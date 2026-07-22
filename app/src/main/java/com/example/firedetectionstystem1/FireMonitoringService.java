package com.example.firedetectionstystem1;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class FireMonitoringService extends Service {

    private static final String CHANNEL_ID_SERVICE = "FireMonitorService_v2";
    private static final String CHANNEL_ID_ALARM = "FireCriticalAlerts_Final"; 
    private FirebaseFirestore firestore;
    private ListenerRegistration listener;
    private String lastStatus = "SAFE";
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
        
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FireSystem::CriticalLock");
            if (!wakeLock.isHeld()) wakeLock.acquire();
        }

        // Persistent system notification
        startForeground(101, createPersistentNotification());
        
        firestore = FirebaseFirestore.getInstance();
        startMonitoring();
    }

    private Notification createPersistentNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
                .setContentTitle("🔥 Fire Protection Enabled")
                .setContentText("Your device is connected and ready for alerts.")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    private void startMonitoring() {
        if (listener != null) listener.remove();
        
        listener = firestore.collection("fire_status").document("current")
                .addSnapshotListener((snapshot, error) -> {
                    if (snapshot != null && snapshot.exists()) {
                        String status = snapshot.getString("status");
                        if (status != null && !status.equalsIgnoreCase(lastStatus)) {
                            if ("FIRE".equalsIgnoreCase(status) || "WARNING".equalsIgnoreCase(status)) {
                                triggerCriticalAlert(status);
                            }
                            lastStatus = status;
                        }
                    }
                });
    }

    private void triggerCriticalAlert(String status) {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pi = PendingIntent.getActivity(this, (int) System.currentTimeMillis(), intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);

        Notification alert = new NotificationCompat.Builder(this, CHANNEL_ID_ALARM)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("!!! EMERGENCY FIRE ALERT !!!")
                .setContentText("DANGER: " + status + " DETECTED AT YOUR STATION!")
                .setPriority(NotificationCompat.PRIORITY_MAX) // Heads-up
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setSound(sound)
                .setVibrate(new long[]{0, 1000, 500, 1000, 500, 1000})
                .setFullScreenIntent(pi, true) // Force pop-up
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(false)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(202, alert);
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;

            NotificationChannel serviceChannel = new NotificationChannel(CHANNEL_ID_SERVICE, 
                "System Status", NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(serviceChannel);

            NotificationChannel alertChannel = new NotificationChannel(CHANNEL_ID_ALARM, 
                "Critical Fire Alerts", NotificationManager.IMPORTANCE_HIGH);
            alertChannel.enableVibration(true);
            alertChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            
            AudioAttributes att = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            alertChannel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), att);
            
            manager.createNotificationChannel(alertChannel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
        restartServiceIntent.setPackage(getPackageName());
        PendingIntent pi = PendingIntent.getService(getApplicationContext(), 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        alarmService.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000, pi);
        super.onTaskRemoved(rootIntent);
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (listener != null) listener.remove();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }
}
