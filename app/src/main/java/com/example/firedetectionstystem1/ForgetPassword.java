package com.example.firedetectionstystem1;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import android.widget.ImageButton;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import java.util.ArrayList;

public class ForgetPassword extends AppCompatActivity {

    private EditText resetEmailInput;
    private EditText newPasswordInput;
    private EditText confirmPasswordInput;
    private Button resetPasswordBtn;
    private Button backToLoginBtn;
    private ImageButton btnVoiceForgot;
    private SpeechRecognizer speechRecognizer;

    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "UserPrefs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LocaleHelper.applySavedLocale(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgetpassword);
        
        resetEmailInput = findViewById(R.id.resetEmailInput);
        newPasswordInput = findViewById(R.id.newPasswordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);
        resetPasswordBtn = findViewById(R.id.resetPasswordBtn);
        backToLoginBtn = findViewById(R.id.backToLoginBtn);
        btnVoiceForgot = findViewById(R.id.btnVoiceForgot);

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        resetPasswordBtn.setOnClickListener(v -> resetPassword());
        btnVoiceForgot.setOnClickListener(v -> startVoiceControl());
        backToLoginBtn.setOnClickListener(v -> {
            Intent intent = new Intent(ForgetPassword.this, LoginActivity.class);
            startActivity(intent);
            finish();
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
                @Override public void onReadyForSpeech(Bundle params) { Toast.makeText(ForgetPassword.this, R.string.listening, Toast.LENGTH_SHORT).show(); }
                @Override public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) processVoiceCommand(matches.get(0).toLowerCase());
                }
                @Override public void onError(int error) { Toast.makeText(ForgetPassword.this, R.string.voice_error, Toast.LENGTH_SHORT).show(); }
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
        if (cmd.contains("reset") || cmd.contains("upya")) resetPassword();
        else if (cmd.contains("back") || cmd.contains("rudi")) finish();
        else Toast.makeText(this, getString(R.string.command_not_recognized), Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) speechRecognizer.destroy();
    }

    private void resetPassword() {
        String email = resetEmailInput.getText().toString().trim().toLowerCase();
        String newPassword = newPasswordInput.getText().toString().trim();
        String confirmPassword = confirmPasswordInput.getText().toString().trim();

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            resetEmailInput.setError(getString(R.string.invalid_email));
            return;
        }
        if (!isValidPassword(newPassword)) {
            newPasswordInput.setError(getString(R.string.password_rules));
            return;
        }
        if (!newPassword.equals(confirmPassword)) {
            confirmPasswordInput.setError(getString(R.string.passwords_not_match));
            return;
        }
        
        String savedEmail = sharedPreferences.getString("email", "").toLowerCase();
        if (!email.equals(savedEmail)) {
            resetEmailInput.setError(getString(R.string.email_not_found));
            return;
        }
        
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("password", newPassword);
        editor.apply();
        
        Toast.makeText(this, getString(R.string.password_updated), Toast.LENGTH_SHORT).show();
        
        // Return to LoginActivity
        Intent intent = new Intent(ForgetPassword.this, LoginActivity.class);
        intent.putExtra("prefill_email", email);
        startActivity(intent);
        finish();
    }

    private boolean isValidPassword(String password) {
        // Requires: 6+ chars, 1 uppercase, 1 digit, 1 special char
        return password.matches("^(?=.*[0-9])(?=.*[A-Z])(?=.*[@#$%^&+=!]).{6,}$");
    }
}
