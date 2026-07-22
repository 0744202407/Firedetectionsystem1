package com.example.firedetectionstystem1;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.Date;
import java.util.Locale;

import android.view.View;
import android.widget.ImageButton;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.content.pm.PackageManager;
import android.content.Intent;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.widget.Toast;
import java.util.ArrayList;

public class RealTimeAlertActivity extends AppCompatActivity {

    private ImageView imgStatusIcon;
    private TextView tvFullStatus, tvFullSmokeLevel;
    private Button btnBack;
    private ImageButton btnVoiceAlert;
    private FirebaseFirestore firestore;
    private ListenerRegistration listener;
    private SpeechRecognizer speechRecognizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LocaleHelper.applySavedLocale(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_real_time_alert);

        imgStatusIcon = findViewById(R.id.imgStatusIcon);
        tvFullStatus = findViewById(R.id.tvFullStatus);
        tvFullSmokeLevel = findViewById(R.id.tvFullSmokeLevel);
        btnBack = findViewById(R.id.btnBack);
        btnVoiceAlert = findViewById(R.id.btnVoiceAlert);

        firestore = FirebaseFirestore.getInstance();
        
        btnBack.setOnClickListener(v -> finish());
        btnVoiceAlert.setOnClickListener(v -> startVoiceControl());

        observeStatus();
    }

    private void startVoiceControl() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            return;
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { Toast.makeText(RealTimeAlertActivity.this, R.string.listening, Toast.LENGTH_SHORT).show(); }
                @Override public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) processVoiceCommand(matches.get(0).toLowerCase());
                }
                @Override public void onError(int error) { Toast.makeText(RealTimeAlertActivity.this, R.string.voice_error, Toast.LENGTH_SHORT).show(); }
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
        if (cmd.contains("back") || cmd.contains("rudi") || cmd.contains("close") || cmd.contains("funga")) finish();
        else Toast.makeText(this, getString(R.string.command_not_recognized), Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listener != null) listener.remove();
        if (speechRecognizer != null) speechRecognizer.destroy();
    }

    private void observeStatus() {
        listener = firestore.collection("fire_status").document("current")
                .addSnapshotListener((snapshot, error) -> {
                    if (snapshot != null && snapshot.exists()) {
                        String status = snapshot.getString("status");
                        Long smoke = snapshot.getLong("smokeLevel");
                        int level = (smoke != null) ? smoke.intValue() : 0;

                        boolean isDanger = "FIRE".equalsIgnoreCase(status) || "WARNING".equalsIgnoreCase(status);
                        boolean isFire = "FIRE".equalsIgnoreCase(status);
                        
                        tvFullStatus.setText(isFire ? getString(R.string.fire_detected_caps) : getString(R.string.no_fire_detected_caps));
                        
                        // Always show % and keep layout visible for realism
                        tvFullSmokeLevel.setText(getString(R.string.alert_smoke_level, level) + "%");
                        tvFullSmokeLevel.setVisibility(View.VISIBLE);

                        if (isDanger) {
                            tvFullStatus.setTextColor(isFire ? Color.RED : Color.parseColor("#FFB300"));
                            imgStatusIcon.setColorFilter(isFire ? Color.RED : Color.parseColor("#FFB300"));
                            imgStatusIcon.setImageResource(isFire ? android.R.drawable.stat_notify_error : android.R.drawable.stat_sys_warning);
                        } else {
                            tvFullStatus.setTextColor(Color.GREEN);
                            imgStatusIcon.setColorFilter(Color.GREEN);
                            imgStatusIcon.setImageResource(android.R.drawable.ic_dialog_info);
                        }
                    }
                });
    }

}
