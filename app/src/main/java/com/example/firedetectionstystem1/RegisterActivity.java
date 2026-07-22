package com.example.firedetectionstystem1;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Patterns;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

import android.widget.Spinner;
import android.widget.ArrayAdapter;

import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import java.util.ArrayList;

public class RegisterActivity extends AppCompatActivity {

    private EditText etUsername, etPassword, etPhone;
    private Button btnRegister;
    private ImageButton toggleRegisterPasswordBtn, btnVoiceRegister;
    private boolean isPasswordVisible = false;
    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;

    SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "UserPrefs";
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LocaleHelper.applySavedLocale(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        etPhone = findViewById(R.id.etPhone);
        btnRegister = findViewById(R.id.btnRegister);
        toggleRegisterPasswordBtn = findViewById(R.id.toggleRegisterPasswordBtn);
        btnVoiceRegister = findViewById(R.id.btnVoiceRegister);

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        toggleRegisterPasswordBtn.setOnClickListener(v -> togglePasswordVisibility());

        btnRegister.setOnClickListener(v -> registerUser());
        btnVoiceRegister.setOnClickListener(v -> startVoiceControl());
    }

    private void startVoiceControl() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            return;
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { Toast.makeText(RegisterActivity.this, R.string.listening, Toast.LENGTH_SHORT).show(); }
                @Override public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) processVoiceCommand(matches.get(0).toLowerCase());
                }
                @Override public void onError(int error) { Toast.makeText(RegisterActivity.this, R.string.voice_error, Toast.LENGTH_SHORT).show(); }
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
        if (cmd.contains("register") || cmd.contains("sajili")) registerUser();
        else if (cmd.contains("login") || cmd.contains("ingia")) finish();
        else Toast.makeText(this, getString(R.string.command_not_recognized), Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) speechRecognizer.destroy();
    }

    private void registerUser() {
        String username = etUsername.getText().toString().trim().toLowerCase();
        String password = etPassword.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();

        // Automatically determine role: specifically 'admin' for the admin email, otherwise 'user'
        final String selectedRole = "admin@app.com".equalsIgnoreCase(username) ? "admin" : "user";

        if (username.isEmpty()) {
            etUsername.setError(getString(R.string.enter_username_email));
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(username).matches()) {
            etUsername.setError(getString(R.string.invalid_email));
            return;
        }
        if (phone.isEmpty()) {
            etPhone.setError("Enter phone number");
            return;
        }
        if (password.isEmpty()) {
            etPassword.setError(getString(R.string.enter_password));
            return;
        }

        firebaseAuth.createUserWithEmailAndPassword(username, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser() != null ? authResult.getUser().getUid() : "";
                    Map<String, Object> user = new HashMap<>();
                    user.put("email", username);
                    user.put("phone", phone);
                    user.put("role", selectedRole); 
                    user.put("createdAt", System.currentTimeMillis());

                    firestore.collection("users")
                            .document(uid)
                            .set(user)
                            .addOnSuccessListener(unused -> {
                                // Save with unique keys to allow multiple accounts on one phone
                                saveLocalCredentials(username, password, selectedRole, phone);
                                Toast.makeText(this, getString(R.string.registered_successfully, username), Toast.LENGTH_SHORT).show();
                                goToLogin(username);
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, getString(R.string.registration_failed, e.getMessage()), Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e -> {
                    String msg = e.getMessage() != null ? e.getMessage() : "";
                    if (msg.toUpperCase().contains("CONFIGURATION_NOT_FOUND")) {
                        saveLocalCredentials(username, password, selectedRole, phone);
                        Toast.makeText(this, getString(R.string.registered_successfully, username), Toast.LENGTH_SHORT).show();
                        goToLogin(username);
                        return;
                    }
                    Toast.makeText(this, getString(R.string.registration_failed, msg), Toast.LENGTH_SHORT).show();
                });
    }

    private void saveLocalCredentials(String email, String password, String role, String phone) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        // Use keys tied to email to avoid overwriting other users
        editor.putString("pass_" + email, password);
        editor.putString("role_" + email, role);
        editor.putString("phone_" + email, phone);
        // Also keep track of the 'last' registered for quick access
        editor.putString("last_email", email);
        editor.apply();
    }

    private void goToLogin(String email) {
        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
        intent.putExtra("prefill_email", email);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;
        if (isPasswordVisible) {
            etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            toggleRegisterPasswordBtn.setImageResource(R.drawable.ic_visibility);
        } else {
            etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            toggleRegisterPasswordBtn.setImageResource(R.drawable.ic_visibility_off);
        }
        etPassword.setSelection(etPassword.length());
    }
}
