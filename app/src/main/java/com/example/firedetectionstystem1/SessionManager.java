package com.example.firedetectionstystem1;

import android.content.Context;
import android.content.SharedPreferences;

public final class SessionManager {
    private static final String PREFS_NAME = "UserPrefs";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_ROLE = "role";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_REMEMBER_ME = "remember_me";
    private static final String KEY_LOGIN_TIME = "login_time";
    private static final String KEY_LAST_ACTIVE = "last_active";

    // Non-remembered sessions stay valid for 24 hours.
    private static final long SESSION_TIMEOUT_MS = 24 * 60 * 60 * 1000L;
    // Remembered sessions stay valid for 30 days.
    private static final long REMEMBER_ME_TIMEOUT_MS = 30L * 24 * 60 * 60 * 1000;

    private SessionManager() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void onLoginSuccess(Context context, String email, String role, boolean rememberMe) {
        long now = System.currentTimeMillis();
        prefs(context).edit()
                .putString(KEY_EMAIL, email)
                .putString(KEY_ROLE, role)
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .putBoolean(KEY_REMEMBER_ME, rememberMe)
                .putLong(KEY_LOGIN_TIME, now)
                .putLong(KEY_LAST_ACTIVE, now)
                .apply();
    }

    public static boolean isSessionValid(Context context) {
        SharedPreferences preferences = prefs(context);
        if (!preferences.getBoolean(KEY_IS_LOGGED_IN, false)) {
            return false;
        }

        long now = System.currentTimeMillis();
        long loginTime = preferences.getLong(KEY_LOGIN_TIME, 0L);
        long lastActive = preferences.getLong(KEY_LAST_ACTIVE, 0L);
        boolean rememberMe = preferences.getBoolean(KEY_REMEMBER_ME, false);

        if (rememberMe) {
            return loginTime > 0L && (now - loginTime) <= REMEMBER_ME_TIMEOUT_MS;
        }
        return lastActive > 0L && (now - lastActive) <= SESSION_TIMEOUT_MS;
    }

    public static String getRole(Context context) {
        return prefs(context).getString(KEY_ROLE, "user");
    }

    public static void touch(Context context) {
        if (!prefs(context).getBoolean(KEY_IS_LOGGED_IN, false)) {
            return;
        }
        prefs(context).edit()
                .putLong(KEY_LAST_ACTIVE, System.currentTimeMillis())
                .apply();
    }

    public static void logout(Context context) {
        prefs(context).edit()
                .putBoolean(KEY_IS_LOGGED_IN, false)
                .putBoolean(KEY_REMEMBER_ME, false)
                .remove(KEY_ROLE)
                .remove(KEY_LOGIN_TIME)
                .remove(KEY_LAST_ACTIVE)
                .apply();
    }
}
