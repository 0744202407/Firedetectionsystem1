package com.example.firedetectionstystem1;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int SPLASH_TIME = 500; // Reduced to 0.5 seconds for speed
    private static final int PERMISSION_REQUEST_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LocaleHelper.applySavedLocale(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST_CODE);
            } else {
                proceed();
            }
        } else {
            proceed();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            proceed();
        }
    }

    private void proceed() {
        new Handler().postDelayed(() -> {
            if (SessionManager.isSessionValid(MainActivity.this)) {
                // Start background monitoring
                Intent serviceIntent = new Intent(MainActivity.this, FireMonitoringService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }

                String role = SessionManager.getRole(MainActivity.this);
                
                // Subscribe to fire alerts topic
                com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic("fire_alerts");

                Intent next = "admin".equalsIgnoreCase(role) ? 
                        new Intent(MainActivity.this, AdminDashboardActivity.class) : 
                        new Intent(MainActivity.this, DashboardActivity.class);
                startActivity(next);
            } else {
                startActivity(new Intent(MainActivity.this, LoginActivity.class));
            }
            finish();
        }, SPLASH_TIME);
    }
}
