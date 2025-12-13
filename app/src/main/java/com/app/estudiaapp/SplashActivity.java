package com.app.estudiaapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.concurrent.atomic.AtomicBoolean;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Apply persisted theme immediately to avoid flicker
        try {
            SharedPreferences earlyPrefs = getSharedPreferences("auth", MODE_PRIVATE);
            boolean darkPref = earlyPrefs.getBoolean("dark_mode", false);
            int target = darkPref ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
            AppCompatDelegate.setDefaultNightMode(target);
        } catch (Exception ignored) {}

        setContentView(R.layout.activity_splash);

        SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
        String uid = null;
        try { uid = prefs.getString("uid", null); } catch (Exception ignored) {}

        boolean themeChange = false;
        try { themeChange = getIntent().getBooleanExtra("themeChange", false); } catch (Exception ignored) {}

        if (uid == null && !themeChange) {
            // Not signed in → go to SignIn
            try { startActivity(new Intent(this, SignInActivity.class)); } catch (Exception ignore) {}
            finish();
            return;
        }

        // Preload user preferences (dark mode, notify) from Firestore with a short timeout
        AtomicBoolean finished = new AtomicBoolean(false);
        Handler h = new Handler(Looper.getMainLooper());
        Runnable timeout = () -> {
            if (finished.get()) return;
            finished.set(true);
            // Proceed to MainActivity even if remote load didn't finish
            try { startActivity(new Intent(SplashActivity.this, MainActivity.class)); } catch (Exception ignore) {}
            finish();
        };
        h.postDelayed(timeout, 1800); // max splash wait 1.8s to reduce premature exit

        try {
            new UserRepository(this).fetchUserDoc(uid, (DocumentSnapshot doc) -> {
                try {
                    if (doc != null && doc.exists()) {
                        // cache preferences
                        if (doc.contains("preferences")) {
                            Object prefsObj = doc.get("preferences");
                            if (prefsObj instanceof java.util.Map) {
                                java.util.Map map = (java.util.Map) prefsObj;
                                Object notify = map.get("notificationsEnabled");
                                if (notify instanceof Boolean) prefs.edit().putBoolean("notify_enabled", (Boolean) notify).apply();
                                Object darkMode = map.get("darkModeEnabled");
                                if (darkMode instanceof Boolean) {
                                    prefs.edit().putBoolean("dark_mode", (Boolean) darkMode).apply();
                                    int current = AppCompatDelegate.getDefaultNightMode();
                                    int target = ((Boolean) darkMode) ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
                                    if (current != target) AppCompatDelegate.setDefaultNightMode(target);
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
                finally {
                    finished.set(true);
                    h.removeCallbacks(timeout);
                    try { startActivity(new Intent(SplashActivity.this, MainActivity.class)); } catch (Exception ignore) {}
                    finish();
                }
            }, e -> {
                // On error, proceed; local prefs are already applied
                finished.set(true);
                h.removeCallbacks(timeout);
                try { startActivity(new Intent(SplashActivity.this, MainActivity.class)); } catch (Exception ignore) {}
                finish();
            });
        } catch (Exception ex) {
            // Fallback
            finished.set(true);
            h.removeCallbacks(timeout);
            try { startActivity(new Intent(SplashActivity.this, MainActivity.class)); } catch (Exception ignore) {}
            finish();
        }
    }
}
