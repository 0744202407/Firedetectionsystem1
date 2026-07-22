package com.example.firedetectionstystem1;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;

import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.os.Build;
import java.util.ArrayList;

import android.graphics.Color;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class AdminDashboardActivity extends AppCompatActivity {

    private View cardManageUsers, cardMonitorIncidents, cardGenerateReports, cardSystemSettings, btnAdminLogout, layoutAdminVoice;
    private TextView tvAdminStatus;
    private ImageView imgAdminStatus;
    private SpeechRecognizer speechRecognizer;
    private FirebaseFirestore firestore;
    private ListenerRegistration statusListener;

    @Override
    protected void onStart() {
        super.onStart();
        if (!SessionManager.isSessionValid(this) || !"admin".equalsIgnoreCase(SessionManager.getRole(this))) {
            SessionManager.logout(this);
            Toast.makeText(this, "Session expired or access denied.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } else {
            observeStatus();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (statusListener != null) {
            statusListener.remove();
        }
    }

    private void observeStatus() {
        if (firestore == null) firestore = FirebaseFirestore.getInstance();
        statusListener = firestore.collection("fire_status").document("current")
                .addSnapshotListener((snapshot, error) -> {
                    if (snapshot != null && snapshot.exists() && tvAdminStatus != null) {
                        String status = snapshot.getString("status");
                        boolean isFire = "FIRE".equalsIgnoreCase(status) || "WARNING".equalsIgnoreCase(status);
                        boolean isFromCache = snapshot.getMetadata().isFromCache();
                        
                        String baseStatusText = isFire ? "!!! FIRE DETECTED !!!" : "SYSTEM ONLINE - SAFE";
                        String finalStatusText = isFromCache ? baseStatusText + " (Offline)" : baseStatusText;
                        
                        tvAdminStatus.setText(finalStatusText);
                        tvAdminStatus.setTextColor(isFire ? Color.RED : Color.GREEN);
                        if (imgAdminStatus != null) {
                            imgAdminStatus.setColorFilter(isFire ? Color.RED : Color.GREEN);
                            imgAdminStatus.setImageResource(isFire ? android.R.drawable.stat_notify_error : android.R.drawable.presence_online);
                        }
                    } else if (error != null) {
                        if (tvAdminStatus != null) tvAdminStatus.setText("SYSTEM OFFLINE");
                    }
                });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LocaleHelper.applySavedLocale(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        // Request Notification Permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        firestore = FirebaseFirestore.getInstance();
        tvAdminStatus = findViewById(R.id.tvAdminStatus);
        imgAdminStatus = findViewById(R.id.imgAdminStatus);

        // Initialize Views using the correct layout IDs
        cardManageUsers = findViewById(R.id.cardManageUsers);
        cardMonitorIncidents = findViewById(R.id.cardMonitorIncidents);
        cardGenerateReports = findViewById(R.id.cardGenerateReports);
        cardSystemSettings = findViewById(R.id.cardSystemSettings);
        btnAdminLogout = findViewById(R.id.btnAdminLogout);
        layoutAdminVoice = findViewById(R.id.layoutAdminVoice);

        if (layoutAdminVoice != null) {
            layoutAdminVoice.setOnClickListener(v -> startVoiceControl());
        }

        if (cardManageUsers != null) {
            cardManageUsers.setOnClickListener(v -> {
                Intent intent = new Intent(this, ManageUsersActivity.class);
                startActivity(intent);
            });
        }

        if (cardMonitorIncidents != null) {
            cardMonitorIncidents.setOnClickListener(v -> {
                Intent intent = new Intent(this, RealTimeAlertActivity.class);
                startActivity(intent);
            });
        }

        if (cardGenerateReports != null) {
            cardGenerateReports.setOnClickListener(v -> {
                Intent intent = new Intent(this, ReportsActivity.class);
                startActivity(intent);
                Toast.makeText(this, "Select 'Download PDF' to generate the report", Toast.LENGTH_LONG).show();
            });
        }

        if (cardSystemSettings != null) {
            cardSystemSettings.setOnClickListener(v -> {
                Intent intent = new Intent(this, SystemSettingsActivity.class);
                startActivity(intent);
            });
        }

        if (btnAdminLogout != null) {
            btnAdminLogout.setOnClickListener(v -> performLogout());
        }
    }

    private void performLogout() {
        FirebaseAuth.getInstance().signOut();
        SessionManager.logout(this);
        
        // Stop background monitoring service
        stopService(new Intent(this, FireMonitoringService.class));

        Intent intent = new Intent(AdminDashboardActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void startVoiceControl() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            return;
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { Toast.makeText(AdminDashboardActivity.this, R.string.listening, Toast.LENGTH_SHORT).show(); }
                @Override public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) processVoiceCommand(matches.get(0).toLowerCase());
                }
                @Override public void onError(int error) { Toast.makeText(AdminDashboardActivity.this, R.string.voice_error, Toast.LENGTH_SHORT).show(); }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });
        }

        String lang = LocaleHelper.getSavedLanguage(this);
        Intent si = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        si.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        si.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        speechRecognizer.startListening(si);
    }

    private void processVoiceCommand(String cmd) {
        if (cmd.contains("user") || cmd.contains("mtumiaji")) cardManageUsers.performClick();
        else if (cmd.contains("incident") || cmd.contains("tukio")) cardMonitorIncidents.performClick();
        else if (cmd.contains("report") || cmd.contains("ripoti")) cardGenerateReports.performClick();
        else if (cmd.contains("setting") || cmd.contains("mipangilio")) cardSystemSettings.performClick();
        else if (cmd.contains("logout") || cmd.contains("toka") || cmd.contains("funga") || cmd.contains("close") || cmd.contains("ondoka")) performLogout();
        else Toast.makeText(this, getString(R.string.command_not_recognized), Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) speechRecognizer.destroy();
    }
}
