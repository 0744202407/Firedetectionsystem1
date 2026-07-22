package com.example.firedetectionstystem1;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Patterns;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.CheckBox;
import android.widget.Spinner;
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
import android.os.Build;
import java.util.ArrayList;
import java.util.Locale;

import android.widget.PopupMenu;

public class LoginActivity extends AppCompatActivity {

    EditText emailInput, passwordInput;
    View loginBtn, btnMenu;
    TextView registerBtn, forgotBtn;
    ImageButton togglePasswordBtn, btnVoiceLogin;
    CheckBox rememberMeCheckbox;
    Spinner languageSpinner;
    private boolean isInitializingLanguage = true;
    private boolean isPasswordVisible = false;

    SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "UserPrefs";
    private FirebaseAuth firebaseAuth;
    private SpeechRecognizer speechRecognizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LocaleHelper.applySavedLocale(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginBtn = findViewById(R.id.loginBtn);
        registerBtn = findViewById(R.id.registerBtn);
        forgotBtn = findViewById(R.id.forgotBtn);
        languageSpinner = findViewById(R.id.languageSpinner);
        togglePasswordBtn = findViewById(R.id.togglePasswordBtn);
        rememberMeCheckbox = findViewById(R.id.rememberMeCheckbox);
        btnVoiceLogin = findViewById(R.id.btnVoiceLogin);
        btnMenu = findViewById(R.id.btnMenu);

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        firebaseAuth = FirebaseAuth.getInstance();
        setupLanguageChooser();

        String prefillEmail = getIntent().getStringExtra("prefill_email");
        if (prefillEmail != null) emailInput.setText(prefillEmail);

        togglePasswordBtn.setOnClickListener(v -> togglePasswordVisibility());
        loginBtn.setOnClickListener(v -> validateLogin());
        btnMenu.setOnClickListener(v -> showLoginMenu());
        registerBtn.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
        forgotBtn.setOnClickListener(v -> startActivity(new Intent(this, ForgetPassword.class)));
        btnVoiceLogin.setOnClickListener(v -> startVoiceControl());
    }

    private void showLoginMenu() {
        PopupMenu popupMenu = new PopupMenu(this, btnMenu);
        popupMenu.getMenuInflater().inflate(R.menu.login_menu, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.menu_help_support) {
                startActivity(new Intent(this, HelpSupportActivity.class));
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void validateLogin() {
        String email = emailInput.getText().toString().trim().toLowerCase();
        String pass = passwordInput.getText().toString().trim();

        if(!Patterns.EMAIL_ADDRESS.matcher(email).matches()){
            emailInput.setError(getString(R.string.invalid_email));
            return;
        }
        if(pass.isEmpty()){
            passwordInput.setError(getString(R.string.enter_password));
            return;
        }

        // Special Check for Fixed Admin Credentials
        if ("admin@app.com".equals(email) && "Admin@123".equals(pass)) {
            resetFailedAttempts();
            // Sign in anonymously to give this hardcoded admin a valid Firebase Session
            firebaseAuth.signInAnonymously().addOnCompleteListener(task -> {
                completeLogin(email, "admin");
            });
            return;
        }

        firebaseAuth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser() != null ? authResult.getUser().getUid() : "";
                    com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(uid).get()
                            .addOnSuccessListener(doc -> {
                                String role = "user";
                                if (doc.exists() && doc.getString("role") != null) {
                                    role = doc.getString("role");
                                }
                                completeLogin(email, role);
                            })
                            .addOnFailureListener(e -> completeLogin(email, "user"));
                })
                .addOnFailureListener(e -> {
                    if (attemptLocalLogin(email, pass)) return;
                    Toast.makeText(this, getString(R.string.invalid_credentials), Toast.LENGTH_SHORT).show();
                });
    }

    private void resetFailedAttempts() {
        sharedPreferences.edit()
                .putInt("failed_attempts", 0)
                .putLong("lockout_time", 0)
                .apply();
    }


    private boolean attemptLocalLogin(String email, String pass) {
        String savedPass = sharedPreferences.getString("pass_" + email, "");
        String savedRole = sharedPreferences.getString("role_" + email, "user");

        if (!savedPass.isEmpty() && pass.equals(savedPass)) {
            completeLogin(email, savedRole);
            return true;
        }
        return false;
    }

    private void completeLogin(String email, String role) {
        // Hardcoded Admin Check
        if ("admin@app.com".equalsIgnoreCase(email)) role = "admin";
        SessionManager.onLoginSuccess(this, email, role, rememberMeCheckbox.isChecked());
        
        // Subscribe to fire alerts
        com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic("fire_alerts");

        Intent serviceIntent = new Intent(this, FireMonitoringService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        Intent intent = "admin".equalsIgnoreCase(role) ? 
                new Intent(this, AdminDashboardActivity.class) : 
                new Intent(this, DashboardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
        finish();
    }

    private void togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;
        passwordInput.setInputType(isPasswordVisible ? 
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : 
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        togglePasswordBtn.setImageResource(isPasswordVisible ? R.drawable.ic_visibility : R.drawable.ic_visibility_off);
        passwordInput.setSelection(passwordInput.length());
    }

    private void setupLanguageChooser() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.language_names, R.layout.spinner_item);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        languageSpinner.setAdapter(adapter);
        String selectedCode = LocaleHelper.getSavedLanguage(this);
        String[] codes = getResources().getStringArray(R.array.language_codes);
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equals(selectedCode)) { languageSpinner.setSelection(i, false); break; }
        }
        isInitializingLanguage = false;
        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (!isInitializingLanguage) {
                    LocaleHelper.setLocale(LoginActivity.this, codes[pos]);
                    recreate();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
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
                @Override public void onReadyForSpeech(Bundle p) { Toast.makeText(LoginActivity.this, R.string.listening, Toast.LENGTH_SHORT).show(); }
                @Override public void onResults(Bundle r) {
                    ArrayList<String> matches = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) processVoiceCommand(matches.get(0).toLowerCase());
                }
                @Override public void onError(int e) { Toast.makeText(LoginActivity.this, R.string.voice_error, Toast.LENGTH_SHORT).show(); }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float r) {}
                @Override public void onBufferReceived(byte[] b) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onPartialResults(Bundle p) {}
                @Override public void onEvent(int t, Bundle p) {}
            });
        }
        String lang = LocaleHelper.getSavedLanguage(this);
        Intent si = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        si.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        si.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        speechRecognizer.startListening(si);
    }

    private void processVoiceCommand(String cmd) {
        if (cmd.contains("login") || cmd.contains("ingia")) validateLogin();
        else if (cmd.contains("register") || cmd.contains("sajili")) registerBtn.performClick();
        else if (cmd.contains("forgot") || cmd.contains("sahau")) forgotBtn.performClick();
        else if (cmd.contains("help") || cmd.contains("msaada")) startActivity(new Intent(this, HelpSupportActivity.class));
        else Toast.makeText(this, getString(R.string.command_not_recognized), Toast.LENGTH_SHORT).show();
    }

    @Override protected void onDestroy() { super.onDestroy(); if (speechRecognizer != null) speechRecognizer.destroy(); }
}
