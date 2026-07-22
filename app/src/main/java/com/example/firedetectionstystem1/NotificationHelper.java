package com.example.firedetectionstystem1;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.telephony.SmsManager;
import androidx.core.app.NotificationCompat;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.Date;

public class NotificationHelper {

    private Context context;
    private static final String CHANNEL_ID = "FIRE_ALARM_CHANNEL";

    public NotificationHelper(Context context) {
        this.context = context;
        createNotificationChannel();
    }

    // 1. Kutuma SMS
    public void sendFireSMS(String phoneNumber, String message) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 2. Kuonyesha App Notification
    public void showAppNotification(String title, String body) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        notificationManager.notify(1, builder.build());
    }

    // 3. Kuhifadhi kwenye Database (Firestore)
    public void saveFireToDatabase(String location, String intensity) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FireRecord record = new FireRecord(location, intensity, new Date(), "ACTIVE");
        
        db.collection("fire_alerts").add(record);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Fire Alarm", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }
}
