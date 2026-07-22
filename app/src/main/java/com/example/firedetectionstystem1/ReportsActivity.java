package com.example.firedetectionstystem1;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReportsActivity extends AppCompatActivity {

    private FirebaseFirestore firestore;
    private final List<AlertModel> alertList = new ArrayList<>();
    private AlertAdapter adapter;
    private SpeechRecognizer speechRecognizer;
    private ListenerRegistration reportListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LocaleHelper.applySavedLocale(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        final RecyclerView rvAlertHistory = findViewById(R.id.rvAlertHistory);
        final View btnViewReports = findViewById(R.id.btnViewReports);
        final View btnDownloadPDF = findViewById(R.id.btnDownloadPDF);
        final View btnBackReports = findViewById(R.id.btnBackReports);
        final View btnVoiceReports = findViewById(R.id.btnVoiceReports);

        firestore = FirebaseFirestore.getInstance();
        adapter = new AlertAdapter(alertList);
        rvAlertHistory.setAdapter(adapter);

        // Clear local persistence on start to ensure fresh data
        firestore.clearPersistence().addOnCompleteListener(task -> startRealTimeListening());

        btnViewReports.setOnClickListener(v -> startRealTimeListening());
        btnDownloadPDF.setOnClickListener(v -> fetchAndDownload());
        btnVoiceReports.setOnClickListener(v -> startVoiceControl());
        btnBackReports.setOnClickListener(v -> finish());
    }

    private void startRealTimeListening() {
        if (reportListener != null) reportListener.remove();

        reportListener = firestore.collection("fire_alerts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Toast.makeText(this, "Listener Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (snapshots != null) {
                        alertList.clear();
                        for (DocumentSnapshot doc : snapshots) {
                            AlertModel alert = doc.toObject(AlertModel.class);
                            if (alert != null && alert.getStatus() != null && alert.getTimestamp() != null) {
                                alertList.add(alert);
                            }
                        }
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    private void fetchAndDownload() {
        if (alertList.isEmpty()) {
            Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show();
            return;
        }
        generatePDF(alertList);
    }

    private void generatePDF(List<AlertModel> list) {
        final PdfDocument document = new PdfDocument();
        final PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        final PdfDocument.Page page = document.startPage(pageInfo);

        final Canvas canvas = page.getCanvas();
        final Paint paint = new Paint();

        paint.setTextSize(20);
        paint.setFakeBoldText(true);
        canvas.drawText("FIRE DETECTION SYSTEM LOGS", 50, 50, paint);

        paint.setFakeBoldText(false);
        paint.setTextSize(12);
        int yCoord = 100;
        for (AlertModel alert : list) {
            if (yCoord > 780) break;
            final String time = formatTime(alert.getTimestamp());
            canvas.drawText(String.format(Locale.getDefault(), "Time: %s | Status: %s", time, alert.getStatus()), 50, yCoord, paint);
            canvas.drawText(String.format(Locale.getDefault(), "Smoke: %d%% | Loc: %s", alert.getSmokeLevel(), alert.getLocation()), 50, yCoord + 20, paint);
            yCoord += 50;
            canvas.drawLine(50, (float)yCoord - 10, 550, (float)yCoord - 10, paint);
        }

        document.finishPage(page);
        final String fileName = "Fire_Report_" + System.currentTimeMillis() + ".pdf";

        try (OutputStream os = createOutputStream(fileName)) {
            if (os != null) {
                document.writeTo(os);
                Toast.makeText(this, "PDF saved to Downloads", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Save error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            document.close();
        }
    }

    private OutputStream createOutputStream(String fileName) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            final ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            final Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            return (uri != null) ? getContentResolver().openOutputStream(uri) : null;
        } else {
            final File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return Files.newOutputStream(file.toPath());
            } else {
                return new FileOutputStream(file);
            }
        }
    }

    private static String formatTime(Object ts) {
        if (ts instanceof com.google.firebase.Timestamp) {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(((com.google.firebase.Timestamp) ts).toDate());
        } else if (ts instanceof String) {
            return (String) ts;
        }
        return "N/A";
    }

    private void startVoiceControl() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            return;
        }
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle p) {}
                @Override public void onResults(Bundle r) {
                    final ArrayList<String> m = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (m != null && !m.isEmpty()) processCmd(m.get(0).toLowerCase());
                }
                @Override public void onError(int e) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float r) {}
                @Override public void onBufferReceived(byte[] b) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onPartialResults(Bundle p) {}
                @Override public void onEvent(int t, Bundle p) {}
            });
        }
        final Intent si = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        si.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizer.startListening(si);
    }

    private void processCmd(String cmd) {
        if (cmd.contains("download") || cmd.contains("save") || cmd.contains("pakua") || cmd.contains("hifadhi")) fetchAndDownload();
        else if (cmd.contains("back") || cmd.contains("close") || cmd.contains("rudi") || cmd.contains("funga")) finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (reportListener != null) reportListener.remove();
        if (speechRecognizer != null) speechRecognizer.destroy();
    }

    private static class AlertAdapter extends RecyclerView.Adapter<AlertAdapter.AlertVH> {
        private final List<AlertModel> list;
        public AlertAdapter(List<AlertModel> list) { this.list = list; }

        @NonNull
        @Override
        public AlertVH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            return new AlertVH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_alert, p, false));
        }

        @Override
        public void onBindViewHolder(@NonNull AlertVH h, int pos) {
            final AlertModel alert = list.get(pos);
            h.status.setText(alert.getStatus());
            h.time.setText(formatTime(alert.getTimestamp()));
            
            final long smoke = alert.getSmokeLevel() != null ? alert.getSmokeLevel() : 0;
            h.details.setText(String.format(Locale.getDefault(), "Station: %s | Smoke: %d%%", 
                    alert.getLocation() != null ? alert.getLocation() : "Station 01", (int)smoke));

            final boolean isFire = "FIRE".equalsIgnoreCase(alert.getStatus());
            h.status.setTextColor(isFire ? Color.RED : Color.parseColor("#FFB300"));
            h.icon.setColorFilter(isFire ? Color.RED : Color.parseColor("#FFB300"));
        }

        @Override
        public int getItemCount() { return list.size(); }

        static class AlertVH extends RecyclerView.ViewHolder {
            final TextView status, time, details;
            final ImageView icon;
            public AlertVH(@NonNull View v) {
                super(v);
                status = v.findViewById(R.id.txtAlertStatus);
                time = v.findViewById(R.id.txtAlertTime);
                details = v.findViewById(R.id.txtAlertDetails);
                icon = v.findViewById(R.id.imgAlertIcon);
            }
        }
    }
}
