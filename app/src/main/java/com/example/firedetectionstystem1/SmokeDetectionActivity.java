package com.example.firedetectionstystem1;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

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

public class SmokeDetectionActivity extends AppCompatActivity {

    private CircularProgressIndicator smokeProgress;
    private TextView tvSmokePercent, tvSeverity, tvSmokeStatus;
    private Button btnBackSmoke;
    private ImageButton btnVoiceSmoke;
    private FirebaseFirestore firestore;
    private ListenerRegistration listener;
    private SpeechRecognizer speechRecognizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LocaleHelper.applySavedLocale(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smoke_detection);

        smokeProgress = findViewById(R.id.smokeProgress);
        tvSmokePercent = findViewById(R.id.tvSmokePercent);
        tvSeverity = findViewById(R.id.tvSeverity);
        tvSmokeStatus = findViewById(R.id.tvSmokeStatus);
        btnBackSmoke = findViewById(R.id.btnBackSmoke);
        btnVoiceSmoke = findViewById(R.id.btnVoiceSmoke);

        firestore = FirebaseFirestore.getInstance();
        btnBackSmoke.setOnClickListener(v -> finish());
        btnVoiceSmoke.setOnClickListener(v -> startVoiceControl());

        observeSmoke();
    }

    private void observeSmoke() {
        listener = firestore.collection("fire_status").document("current")
                .addSnapshotListener((snapshot, error) -> {
                    if (snapshot != null && snapshot.exists()) {
                        Long smoke = snapshot.getLong("smokeLevel");
                        String status = snapshot.getString("status");
                        int level = (smoke != null) ? smoke.intValue() : 0;
                        boolean isDanger = "FIRE".equalsIgnoreCase(status) || "WARNING".equalsIgnoreCase(status);

                        smokeProgress.setProgress(level);
                        
                        // Always show % for realistic readings
                        tvSmokePercent.setText(level + "%");

                        if (isDanger) {
                            if (level >= 70) {
                                tvSeverity.setText(getString(R.string.alert_severity, getString(R.string.severity_high)));
                                tvSeverity.setTextColor(Color.RED);
                                smokeProgress.setIndicatorColor(Color.RED);
                            } else if (level >= 40) {
                                tvSeverity.setText(getString(R.string.alert_severity, getString(R.string.severity_medium)));
                                tvSeverity.setTextColor(Color.YELLOW);
                                smokeProgress.setIndicatorColor(Color.YELLOW);
                            } else {
                                tvSeverity.setText(getString(R.string.alert_severity, getString(R.string.severity_low)));
                                tvSeverity.setTextColor(Color.GREEN);
                                smokeProgress.setIndicatorColor(Color.GREEN);
                            }
                        } else {
                            tvSeverity.setText(getString(R.string.alert_severity, getString(R.string.severity_low)));
                            tvSeverity.setTextColor(Color.GREEN);
                            smokeProgress.setIndicatorColor(Color.GREEN);
                        }

                        tvSmokeStatus.setText("FIRE".equalsIgnoreCase(status) ? 
                            getString(R.string.investigate_immediately) : getString(R.string.monitoring_normal));
                    }
                });
    }

    private void startVoiceControl() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            return;
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { Toast.makeText(SmokeDetectionActivity.this, R.string.listening, Toast.LENGTH_SHORT).show(); }
                @Override public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) processVoiceCommand(matches.get(0).toLowerCase());
                }
                @Override public void onError(int error) { Toast.makeText(SmokeDetectionActivity.this, R.string.voice_error, Toast.LENGTH_SHORT).show(); }
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
        else if (cmd.contains("refresh") || cmd.contains("update") || cmd.contains("sasisha")) observeSmoke();
        else Toast.makeText(this, getString(R.string.command_not_recognized), Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listener != null) listener.remove();
        if (speechRecognizer != null) speechRecognizer.destroy();
    }
}
