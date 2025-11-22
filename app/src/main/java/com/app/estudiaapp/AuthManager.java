package com.app.estudiaapp;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;

/**
 * Small helper to centralize logout behavior across activities.
 */
public class AuthManager {

    public static void logoutAndGoToSignIn(Activity from) {
        try {
            // Sign out from Firebase
            FirebaseAuth.getInstance().signOut();
        } catch (Exception ignored) {}

        try {
            // Clear local stored auth prefs
            SharedPreferences prefs = from.getSharedPreferences("auth", Activity.MODE_PRIVATE);
            prefs.edit().clear().apply();
        } catch (Exception ignored) {}

        // Start SignInActivity clearing task
        Intent i = new Intent(from, SignInActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        from.startActivity(i);

        // Finish all activities in the current task to avoid back navigation
        try {
            from.finishAffinity();
        } catch (Exception ignored) {}
    }
}
