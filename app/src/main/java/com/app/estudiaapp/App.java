package com.app.estudiaapp;

import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

import androidx.annotation.NonNull;

/**
 * Application class: centraliza AuthStateListener y habilita persistencia de Firestore.
 */
public class App extends Application {

    private static final String TAG = "App";
    private FirebaseAuth.AuthStateListener authListener;

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            androidx.multidex.MultiDex.install(this);
            Log.i(TAG, "MultiDex.install called to ensure secondary dexes are loaded");
        } catch (Exception ignored) {}

        // Initialize PDFBox Android resource loader early so glyphlists/cmaps/fonts
        // are available before any PDFBox classes are referenced elsewhere in the app.
        try {
            // Use reflection so compilation doesn't hard-depend on the PDFBox Android class.
            try {
                // Try multiple known class names (some pdfbox-android versions place the loader
                // under different packages). Use the first that loads successfully.
                String[] candidates = new String[]{
                        "com.tom_roush.pdfbox.util.PDFBoxResourceLoader",
                        "com.tom_roush.pdfbox.android.PDFBoxResourceLoader"
                };
                Class<?> loaded = null;
                for (String cand : candidates) {
                    try {
                        loaded = Class.forName(cand);
                        if (loaded != null) {
                            Log.i(TAG, "Found PDFBoxResourceLoader class: " + cand);
                            break;
                        }
                    } catch (ClassNotFoundException ignored) {}
                }
                if (loaded != null) {
                    java.lang.reflect.Method m = loaded.getMethod("init", android.content.Context.class);
                    m.invoke(null, getApplicationContext());
                    Log.i(TAG, "PDFBoxResourceLoader initialized successfully in Application.onCreate (via reflection)");
                } else {
                    Log.w(TAG, "PDFBoxResourceLoader class not found on classpath; skipping PDFBox init");
                }
            } catch (Throwable t) {
                Log.w(TAG, "PDFBoxResourceLoader init failed in Application.onCreate: " + (t == null ? "<null>" : t.getMessage()));
            }
        } catch (Throwable t) {
            Log.w(TAG, "PDFBoxResourceLoader init failed in Application.onCreate: " + (t == null ? "<null>" : t.getMessage()));
        }

        // Runtime check: list key PDFBox asset directories and log counts so we can verify
        // the expected resource files (glyphlist, cmap) are packaged into the APK.
        try {
            android.content.res.AssetManager am = getAssets();
            String[] glyphs = am.list("com/tom_roush/pdfbox/resources/glyphlist");
            String[] cmaps = am.list("com/tom_roush/pdfbox/resources/cmap");
            int gcount = glyphs != null ? glyphs.length : 0;
            int ccount = cmaps != null ? cmaps.length : 0;
            Log.i(TAG, "PDFBox assets - glyphlist count=" + gcount + ", cmap count=" + ccount);
            if (gcount > 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(6, glyphs.length); i++) { sb.append(glyphs[i]).append(","); }
                Log.i(TAG, "Sample glyphlist files: " + sb.toString());
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not list PDFBox asset files", e);
        }

        // DEBUG: Fetch Remote Config at application start to verify OPENAI_API_KEY delivery
        try {
            com.google.firebase.remoteconfig.FirebaseRemoteConfig rc = com.google.firebase.remoteconfig.FirebaseRemoteConfig.getInstance();
            rc.setConfigSettingsAsync(new com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(0).build());
                rc.setDefaultsAsync(java.util.Collections.singletonMap("OPENAI_API_KEY", "<LOCAL_PLACEHOLDER>"))
                .addOnCompleteListener(defTask -> rc.fetchAndActivate().addOnCompleteListener(fetchTask -> {
                    try {
                        java.util.Map<String, com.google.firebase.remoteconfig.FirebaseRemoteConfigValue> all = rc.getAll();
                        Log.i(TAG, "[RC debug] keys count=" + (all != null ? all.size() : 0));
                        if (all != null) {
                            for (String k : all.keySet()) {
                                try {
                                    com.google.firebase.remoteconfig.FirebaseRemoteConfigValue v = rc.getValue(k);
                                    String src = "?";
                                    try {
                                        // Some versions of the Remote Config SDK return an int constant for getSource().
                                        // Map known constants to readable names to avoid calling .name() on a primitive.
                                        try {
                                            int s = v.getSource();
                                            if (s == com.google.firebase.remoteconfig.FirebaseRemoteConfig.VALUE_SOURCE_STATIC) src = "STATIC";
                                            else if (s == com.google.firebase.remoteconfig.FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT) src = "DEFAULT";
                                            else if (s == com.google.firebase.remoteconfig.FirebaseRemoteConfig.VALUE_SOURCE_REMOTE) src = "REMOTE";
                                            else src = String.valueOf(s);
                                        } catch (NoSuchMethodError | NoSuchFieldError e) {
                                            // Fallback: older/newer SDKs may expose an enum; try calling name() reflectively.
                                            try {
                                                java.lang.Object srcObj = v.getSource();
                                                if (srcObj != null) src = srcObj.toString();
                                            } catch (Throwable ignored2) {
                                            }
                                        }
                                    } catch (Exception ignored) {}
                                    Log.i(TAG, "[RC debug] param=" + k + " source=" + src + " len=" + (v != null ? v.asString().length() : 0));
                                } catch (Exception ignored) {}
                            }
                        }
                        String key = "";
                        try { key = rc.getString("OPENAI_API_KEY"); } catch (Exception ignored) {}
                        boolean present = key != null && !key.isEmpty();
                        String masked = present ? (key.length() <= 8 ? "****" : (key.substring(0,4) + "..." + key.substring(key.length()-4))) : "<missing>";
                        Log.i(TAG, "[RC debug] OPENAI_API_KEY present=" + present + " masked=" + masked + " len=" + (key != null ? key.length() : 0));
                        // Persist the key securely for fallback (but avoid storing placeholder)
                        try {
                            if (present && !"<LOCAL_PLACEHOLDER>".equals(key)) {
                                com.app.estudiaapp.utils.OpenAiKeyStore.saveKey(getApplicationContext(), key);
                                Log.i(TAG, "OPENAI_API_KEY persisted to OpenAiKeyStore (masked=" + masked + ")");
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to persist OPENAI_API_KEY", e);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "[RC debug] fetch failed", e);
                    }
                }));
        } catch (Exception ignored) {}

        // Enable offline persistence for Firestore
        try {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .build();
            db.setFirestoreSettings(settings);
        } catch (Exception e) {
            Log.w(TAG, "Could not enable Firestore persistence", e);
        }

        authListener = firebaseAuth -> {
            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user == null) {
                // Not signed in: clear cached auth prefs and start SignInActivity
                try {
                    SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
                    prefs.edit().clear().apply();
                } catch (Exception ignored) {}
                try {
                    Intent i = new Intent(getApplicationContext(), SignInActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                } catch (Exception e) {
                    Log.d(TAG, "Could not start SignInActivity from Application context: " + e);
                }
            } else {
                final String uid = user.getUid();
                // Fetch user document and cache some common fields in SharedPreferences
                try {
                    new UserRepository(getApplicationContext()).fetchUserDoc(uid, (DocumentSnapshot doc) -> {
                        try {
                            SharedPreferences prefs2 = getSharedPreferences("auth", MODE_PRIVATE);
                            if (doc != null && doc.exists()) {
                                String displayName = doc.contains("displayName") ? doc.getString("displayName") : user.getDisplayName();
                                String email = doc.contains("email") ? doc.getString("email") : user.getEmail();
                                prefs2.edit().putString("displayName", displayName != null ? displayName : "")
                                        .putString("email", email != null ? email : "")
                                        .putString("uid", uid)
                                        .apply();

                                // preferences.notificationsEnabled -> notify_enabled
                                if (doc.contains("preferences")) {
                                    Object prefsObj = doc.get("preferences");
                                    if (prefsObj instanceof java.util.Map) {
                                        java.util.Map map = (java.util.Map) prefsObj;
                            // Apply persisted dark mode at application start so all Activities use correct window background
                            SharedPreferences authPrefs = getSharedPreferences("auth", MODE_PRIVATE);
                            boolean darkMode = authPrefs.getBoolean("dark_mode", false);
                            int mode = darkMode ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                                    : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
                            try {
                                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode);
                            } catch (Exception ignored) {
                            }
                                        Object notify = map.get("notificationsEnabled");
                                        if (notify instanceof Boolean) prefs2.edit().putBoolean("notify_enabled", (Boolean) notify).apply();
                                    }
                                }

                                // photoUrl (used to store built-in ic_profile_x name) -> profile_icon cache
                                if (doc.contains("photoUrl")) {
                                    try {
                                        Object p = doc.get("photoUrl");
                                        if (p != null) prefs2.edit().putString("profile_icon", p.toString()).apply();
                                    } catch (Exception ignore) {}
                                }

                                // membership.status -> membership_status
                                if (doc.contains("membership")) {
                                    Object mem = doc.get("membership");
                                    if (mem instanceof java.util.Map) {
                                        Object status = ((java.util.Map) mem).get("status");
                                        if (status != null) prefs2.edit().putString("membership_status", status.toString()).apply();
                                    }
                                }
                            } else {
                                // fallback to FirebaseUser
                                prefs2.edit().putString("displayName", user.getDisplayName() != null ? user.getDisplayName() : "").putString("email", user.getEmail() != null ? user.getEmail() : "").putString("uid", uid).apply();
                            }
                        } catch (Exception ex) {
                            Log.w(TAG, "Error caching user doc fields", ex);
                        }
                    }, (Exception e) -> {
                        Log.w(TAG, "Failed to fetch user doc for caching", e);
                    });
                } catch (Exception e) {
                    Log.w(TAG, "Error while fetching user doc", e);
                }
            }
        };

        FirebaseAuth.getInstance().addAuthStateListener(authListener);
    }

    @Override
    public void onTerminate() {
        try {
            if (authListener != null) FirebaseAuth.getInstance().removeAuthStateListener(authListener);
        } catch (Exception ignored) {}
        super.onTerminate();
    }
}
