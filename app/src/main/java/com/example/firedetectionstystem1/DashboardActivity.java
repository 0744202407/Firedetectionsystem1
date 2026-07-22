package com.example.firedetectionstystem1;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayList;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.auth.FirebaseAuth;

import android.view.View;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.os.Build;
import android.widget.LinearLayout;

public class DashboardActivity extends AppCompatActivity {

    private TextView tvStatus, tvResult, tvZoomPercent;
    private View btnLogout;
    private View btnRealTime, btnSmoke, btnEmergency, btnReports, btnVoice;
    private ImageButton btnZoomIn, btnZoomOut;
    private LinearLayout dashboardRoot;
    private float currentScale = 1.0f;
    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private boolean fireDetected = false;
    private String lastUpdated = "-";
    private FirebaseFirestore firestore;
    private ListenerRegistration fireStatusListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LocaleHelper.applySavedLocale(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Request Notification Permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        // Initialize Views
        tvStatus = findViewById(R.id.tvStatus);
        tvResult = findViewById(R.id.tvResult);
        tvZoomPercent = findViewById(R.id.tvZoomPercent);
        dashboardRoot = findViewById(R.id.dashboardRoot);
        
        btnRealTime = findViewById(R.id.layoutAlerts);
        btnSmoke = findViewById(R.id.layoutSmoke);
        btnEmergency = findViewById(R.id.btnEmergencyRow);
        btnReports = findViewById(R.id.layoutReports);
        btnVoice = findViewById(R.id.layoutVoice);
        btnLogout = findViewById(R.id.btnUserLogoutRow);
        
        btnZoomIn = findViewById(R.id.btnZoomIn);
        btnZoomOut = findViewById(R.id.btnZoomOut);

        firestore = FirebaseFirestore.getInstance();
        observeFireStatus();

        // Click listeners
        if (btnRealTime != null) btnRealTime.setOnClickListener(v -> showRealTimeAlert());
        if (btnSmoke != null) btnSmoke.setOnClickListener(v -> showSmokeDetectionDetails());
        if (btnEmergency != null) btnEmergency.setOnClickListener(v -> openEmergencyDialer());
        if (btnReports != null) btnReports.setOnClickListener(v -> showFireReports());
        if (btnVoice != null) btnVoice.setOnClickListener(v -> startVoiceControl());
        if (btnLogout != null) btnLogout.setOnClickListener(v -> performLogout());
        
        if (btnZoomIn != null) btnZoomIn.setOnClickListener(v -> zoomUI(true));
        if (btnZoomOut != null) btnZoomOut.setOnClickListener(v -> zoomUI(false));
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!SessionManager.isSessionValid(this)) {
            SessionManager.logout(this);
            Toast.makeText(this, getString(R.string.session_expired), Toast.LENGTH_LONG).show();
            Intent intent = new Intent(DashboardActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        SessionManager.touch(this);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        SessionManager.touch(this);
    }

    private void updateStatusViews() {
        if (fireDetected) {
            tvStatus.setText(getString(R.string.fire_detected_sms));
            tvStatus.setTextColor(Color.RED);
            tvResult.setText(getString(R.string.result_high_risk, lastUpdated));
        } else {
            tvStatus.setText(getString(R.string.no_fire_detected));
            tvStatus.setTextColor(Color.GREEN);
            tvResult.setText(getString(R.string.result_area_safe, lastUpdated));
        }
    }

    private void observeFireStatus() {
        fireStatusListener = firestore.collection("fire_status")
                .document("current")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        tvResult.setText(String.format("%s (Offline)", getString(R.string.result_monitoring)));
                        return;
                    }
                    if (snapshot == null || !snapshot.exists()) {
                        tvResult.setText(getString(R.string.result_monitoring));
                        return;
                    }
                    
                    boolean isFromCache = snapshot.getMetadata().isFromCache();
                    applySnapshot(snapshot);
                    updateStatusViews();
                    
                    if (isFromCache) {
                        tvResult.append(" (Cached)");
                    }
                });
    }

    private void applySnapshot(DocumentSnapshot snapshot) {
        Boolean flame = snapshot.getBoolean("flameDetected");
        String status = snapshot.getString("status");
        Date updatedAt = snapshot.getDate("updatedAt");

        if (updatedAt != null) {
            lastUpdated = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(updatedAt);
        } else {
            lastUpdated = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        }

        if (status != null) {
            fireDetected = "FIRE".equalsIgnoreCase(status) || "WARNING".equalsIgnoreCase(status);
        } else if (flame != null) {
            fireDetected = flame;
        }
    }

    private void showRealTimeAlert() {
        startActivity(new Intent(DashboardActivity.this, RealTimeAlertActivity.class));
    }

    private void showSmokeDetectionDetails() {
        startActivity(new Intent(DashboardActivity.this, SmokeDetectionActivity.class));
    }

    private void openEmergencyDialer() {
        // Load the actual emergency number from System Settings
        android.content.SharedPreferences settings = getSharedPreferences("SystemSettings", android.content.Context.MODE_PRIVATE);
        String emergencyNumber = settings.getString("emergency_number", "114");

        // Format the URI and use ACTION_DIAL to avoid security block
        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + emergencyNumber.trim()));
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open dialer", Toast.LENGTH_SHORT).show();
        }
    }

    private void showFireReports() {
        startActivity(new Intent(DashboardActivity.this, ReportsActivity.class));
    }

    private void performLogout() {
        if (speechRecognizer != null) speechRecognizer.destroy();
        
        // Stop background monitoring service
        stopService(new Intent(this, FireMonitoringService.class));

        FirebaseAuth.getInstance().signOut();
        SessionManager.logout(this);
        Intent intent = new Intent(DashboardActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void zoomUI(boolean zoomIn) {
        currentScale = zoomIn ? currentScale + 0.1f : Math.max(0.5f, currentScale - 0.1f);
        if (dashboardRoot != null) {
            dashboardRoot.setScaleX(currentScale);
            dashboardRoot.setScaleY(currentScale);
            dashboardRoot.setPivotX(dashboardRoot.getWidth() / 2f);
            dashboardRoot.setPivotY(0);
        }
        if (tvZoomPercent != null) {
            tvZoomPercent.setText(Math.round(currentScale * 100) + "%");
        }
    }

    private void startVoiceControl() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            return;
        }
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { Toast.makeText(DashboardActivity.this, R.string.listening, Toast.LENGTH_SHORT).show(); }
                @Override public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) processVoiceCommand(matches.get(0).toLowerCase());
                }
                @Override public void onError(int error) { Toast.makeText(DashboardActivity.this, R.string.voice_error, Toast.LENGTH_SHORT).show(); }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });
        }
        String lang = LocaleHelper.getSavedLanguage(this);
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        speechRecognizer.startListening(speechIntent);
    }

    private void processVoiceCommand(String cmd) {
        if (cmd.contains("report") || cmd.contains("ripoti")) showFireReports();
        else if (cmd.contains("smoke") || cmd.contains("moshi")) showSmokeDetectionDetails();
        else if (cmd.contains("alert") || cmd.contains("tahadhari")) showRealTimeAlert();
        else if (cmd.contains("emergency") || cmd.contains("dharura")) openEmergencyDialer();
        else if (cmd.contains("logout") || cmd.contains("toka") || cmd.contains("funga") || cmd.contains("close") || cmd.contains("ondoka")) performLogout();
        else if (cmd.contains("zoom in") || cmd.contains("kuza")) zoomUI(true);
        else if (cmd.contains("zoom out") || cmd.contains("punguza")) zoomUI(false);
        else Toast.makeText(this, getString(R.string.command_not_recognized), Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fireStatusListener != null) fireStatusListener.remove();
        if (speechRecognizer != null) speechRecognizer.destroy();
    }
}
