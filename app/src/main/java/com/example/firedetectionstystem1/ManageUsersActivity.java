package com.example.firedetectionstystem1;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;

public class ManageUsersActivity extends AppCompatActivity {

    private RecyclerView rvUserList;
    private Button btnBackUsers;
    private ImageButton btnVoiceManageUsers;
    private FirebaseFirestore firestore;
    private UserAdapter adapter;
    private List<UserModel> userList;
    private SpeechRecognizer speechRecognizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LocaleHelper.applySavedLocale(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_users);

        rvUserList = findViewById(R.id.rvUserList);
        btnBackUsers = findViewById(R.id.btnBackUsers);
        btnVoiceManageUsers = findViewById(R.id.btnVoiceManageUsers);

        firestore = FirebaseFirestore.getInstance();
        userList = new ArrayList<>();
        adapter = new UserAdapter(userList);
        rvUserList.setAdapter(adapter);

        btnBackUsers.setOnClickListener(v -> finish());
        btnVoiceManageUsers.setOnClickListener(v -> startVoiceControl());

        fetchUsers();
    }

    private void fetchUsers() {
        // Use a snapshot listener instead of get() to support offline persistence automatically
        firestore.collection("users")
                .addSnapshotListener((queryDocumentSnapshots, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Offline Mode: Showing cached users", Toast.LENGTH_SHORT).show();
                    }
                    
                    if (queryDocumentSnapshots != null) {
                        userList.clear();
                        if (queryDocumentSnapshots.isEmpty()) {
                            Toast.makeText(this, "No users found in database!", Toast.LENGTH_LONG).show();
                        } else {
                            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                                String email = doc.getString("email");
                                String phone = doc.getString("phone");
                                String role = doc.getString("role");
                                
                                if (email != null) {
                                    userList.add(new UserModel(doc.getId(), email, phone != null ? phone : "N/A", role != null ? role : "user"));
                                }
                            }
                        }
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    private void deleteUser(UserModel user) {
        new AlertDialog.Builder(this)
                .setTitle("Delete User")
                .setMessage("Are you sure you want to delete " + user.getEmail() + "?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    firestore.collection("users").document(user.getUid()).delete()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "User deleted", Toast.LENGTH_SHORT).show();
                                fetchUsers();
                            })
                            .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void startVoiceControl() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            return;
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { Toast.makeText(ManageUsersActivity.this, R.string.listening, Toast.LENGTH_SHORT).show(); }
                @Override public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) processVoiceCommand(matches.get(0).toLowerCase());
                }
                @Override public void onError(int error) { Toast.makeText(ManageUsersActivity.this, R.string.voice_error, Toast.LENGTH_SHORT).show(); }
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
        else if (cmd.contains("refresh") || cmd.contains("update") || cmd.contains("sasisha")) fetchUsers();
        else Toast.makeText(this, getString(R.string.command_not_recognized), Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) speechRecognizer.destroy();
    }

    // --- RECYCLER VIEW ADAPTER ---
    private class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {
        private final List<UserModel> list;

        public UserAdapter(List<UserModel> list) { this.list = list; }

        @NonNull
        @Override
        public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
            return new UserViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
            UserModel user = list.get(position);
            holder.txtEmail.setText(user.getEmail());
            holder.txtPhone.setText(user.getPhone() != null ? user.getPhone() : "No Phone");
            String roleText = "ROLE: " + (user.getRole() != null ? user.getRole().toUpperCase() : "USER");
            holder.txtRole.setText(roleText);
            
            holder.btnDelete.setOnClickListener(v -> deleteUser(user));
        }

        @Override
        public int getItemCount() { return list.size(); }

        class UserViewHolder extends RecyclerView.ViewHolder {
            TextView txtEmail, txtPhone, txtRole;
            ImageButton btnDelete;

            public UserViewHolder(@NonNull View itemView) {
                super(itemView);
                txtEmail = itemView.findViewById(R.id.txtUserEmail);
                txtPhone = itemView.findViewById(R.id.txtUserPhone);
                txtRole = itemView.findViewById(R.id.txtUserRole);
                btnDelete = itemView.findViewById(R.id.btnDeleteUser);
            }
        }
    }
}
