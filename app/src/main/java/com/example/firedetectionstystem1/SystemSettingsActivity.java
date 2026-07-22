package com.example.firedetectionstystem1;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SystemSettingsActivity extends AppCompatActivity {

    private EditText etEmergencyNumber, etSensitivity;
    private SwitchCompat switchSMS, switchNotifications;
    private SharedPreferences sharedPreferences;
    private SpeechRecognizer speechRecognizer;
    private static final String PREFS_NAME = "SystemSettings";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LocaleHelper.applySavedLocale(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_system_settings);

        etEmergencyNumber = findViewById(R.id.etEmergencyNumber);
        etSensitivity = findViewById(R.id.etSensitivity);
        switchSMS = findViewById(R.id.switchSMS);
        switchNotifications = findViewById(R.id.switchNotifications);
        Button btnSaveSettings = findViewById(R.id.btnSaveSettings);
        Button btnBackSettings = findViewById(R.id.btnBackSettings);
        ImageButton btnVoiceSettings = findViewById(R.id.btnVoiceSettings);

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadSettings();

        btnSaveSettings.setOnClickListener(v -> saveSettings());
        btnBackSettings.setOnClickListener(v -> finish());
        btnVoiceSettings.setOnClickListener(v -> startVoiceControl());
    }

    private void loadSettings() {
        etEmergencyNumber.setText(sharedPreferences.getString("emergency_number", "114"));
        etSensitivity.setText(String.valueOf(sharedPreferences.getInt("smoke_threshold", 1200)));
        switchSMS.setChecked(sharedPreferences.getBoolean("sms_enabled", true));
        switchNotifications.setChecked(sharedPreferences.getBoolean("notifications_enabled", true));
    }

    private void saveSettings() {
        String num = etEmergencyNumber.getText().toString().trim();
        String sensStr = etSensitivity.getText().toString().trim();

        if (num.isEmpty() || sensStr.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            int threshold = Integer.parseInt(sensStr);
            
            // 1. SAVE LOCALLY
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("emergency_number", num);
            editor.putInt("smoke_threshold", threshold);
            editor.putBoolean("sms_enabled", switchSMS.isChecked());
            editor.putBoolean("notifications_enabled", switchNotifications.isChecked());
            editor.apply();

            // 2. SYNC TO FIREBASE (For Hardware)
            Map<String, Object> config = new HashMap<>();
            config.put("smokeThreshold", threshold);
            config.put("smsEnabled", switchSMS.isChecked());
            config.put("emergencyNumber", num);

            // Using a simple set to force override and avoid permission bugs
            FirebaseFirestore.getInstance().collection("system_settings").document("config")
                    .set(config)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Settings Saved and Synced! ✅", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        // Silent error if local save was ok, just notify user
                        Toast.makeText(this, "Settings saved locally.", Toast.LENGTH_SHORT).show();
                        finish();
                    });

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid sensitivity value", Toast.LENGTH_SHORT).show();
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
                @Override public void onReadyForSpeech(Bundle params) { Toast.makeText(SystemSettingsActivity.this, R.string.listening, Toast.LENGTH_SHORT).show(); }
                @Override public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) processVoiceCommand(matches.get(0).toLowerCase());
                }
                @Override public void onError(int error) { Toast.makeText(SystemSettingsActivity.this, R.string.voice_error, Toast.LENGTH_SHORT).show(); }
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
        if (cmd.contains("save") || cmd.contains("hifadhi")) saveSettings();
        else if (cmd.contains("back") || cmd.contains("rudi") || cmd.contains("cancel") || cmd.contains("ghairi") || cmd.contains("funga") || cmd.contains("close")) finish();
        else if (cmd.contains("sms off") || cmd.contains("zima sms")) switchSMS.setChecked(false);
        else if (cmd.contains("sms on") || cmd.contains("washa sms")) switchSMS.setChecked(true);
        else Toast.makeText(this, getString(R.string.command_not_recognized), Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) speechRecognizer.destroy();
    }
}
