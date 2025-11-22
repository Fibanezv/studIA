package com.app.estudiaapp;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.app.estudiaapp.helper.StorageHelper;
import com.app.estudiaapp.models.StudyPlan;
import com.google.gson.Gson;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;

/**
 * UserActivity: muestra la información de la cuenta (email, nombre), estadísticas de uso
 * (nº de planes, quizzes completados, flashcards estudiadas) y ofrece acciones de configuración
 * (editar nombre/email, toggles, exportar datos, restablecer y cerrar sesión).
 */
public class UserActivity extends BaseActivity {

    private TextView userNameTv, userEmailTv, tvPlansCount, tvQuizzesCount, tvFlashcardsCount, tvHotTopicsCount;
    private ImageButton btnEditProfile;
    private Switch switchNotifications, switchDarkMode;
    private Button btnExport, btnSignOut;

    private SharedPreferences authPrefs;
    // When updating switches programmatically we suppress their listeners to avoid loops/recreates
    private boolean suppressSwitchCallbacks = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Initialize prefs and apply persisted theme BEFORE inflating UI so the theme is applied immediately
        authPrefs = getSharedPreferences("auth", Context.MODE_PRIVATE);
        boolean darkPref = authPrefs.getBoolean("dark_mode", false);
        int currentMode = androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode();
        if (darkPref && currentMode != androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
        } else if (!darkPref && currentMode != androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user);

        userNameTv = findViewById(R.id.user_name);
        userEmailTv = findViewById(R.id.user_email);
    tvPlansCount = findViewById(R.id.tv_plans_count);
    tvQuizzesCount = findViewById(R.id.tv_quizzes_count);
    tvFlashcardsCount = findViewById(R.id.tv_flashcards_count);
    tvHotTopicsCount = findViewById(R.id.tv_hot_topics_count);
        btnEditProfile = findViewById(R.id.btn_edit_profile);
        switchNotifications = findViewById(R.id.switch_notifications);
        switchDarkMode = findViewById(R.id.switch_dark_mode);
        btnExport = findViewById(R.id.btn_export_data);
    btnSignOut = findViewById(R.id.btn_sign_out);

        // Wire premium card (if included in this layout) to open MembershipActivity
        View premiumCard = findViewById(R.id.premium_card);
        if (premiumCard != null) {
            premiumCard.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(UserActivity.this, MembershipActivity.class));
                } catch (Exception ignore) {}
            });

            // Start shimmer animation for the premium shimmer overlay if present
            View shimmer = findViewById(R.id.premium_shimmer);
            if (shimmer != null) {
                premiumCard.post(() -> {
                    int cardW = premiumCard.getWidth();
                    int shimmerW = shimmer.getWidth();
                    if (cardW <= 0) cardW = premiumCard.getMeasuredWidth();
                    if (shimmerW <= 0) shimmerW = 64;
                    float start = -shimmerW - 20f;
                    float end = cardW + shimmerW + 20f;
                    android.animation.ObjectAnimator anim = android.animation.ObjectAnimator.ofFloat(shimmer, "translationX", start, end);
                    anim.setDuration(1400);
                    anim.setRepeatMode(android.animation.ValueAnimator.RESTART);
                    anim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                    anim.setInterpolator(new android.view.animation.LinearInterpolator());
                    anim.start();
                });
            }
        }

        loadUserInfo();
        computeStats();
        setupListeners();
        populateHistory();
    }

    private void loadUserInfo() {
        // Prefer reading from Firestore users/{uid} and cache into SharedPreferences
        String uid = authPrefs.getString("uid", null);
        if (uid != null) {
            try {
                new UserRepository(UserActivity.this).fetchUserDoc(uid, doc -> {
                    if (doc != null && doc.exists()) {
                        String email = doc.contains("email") ? doc.getString("email") : authPrefs.getString("email", "");
                        String display = doc.contains("displayName") ? doc.getString("displayName") : authPrefs.getString("displayName", "");
                        if (display == null || display.isEmpty()) {
                            if (email != null && email.contains("@")) display = email.split("@")[0];
                            else display = "Usuario";
                        }
                        // update UI
                        userNameTv.setText(display);
                        userEmailTv.setText(email != null && !email.isEmpty() ? email : "(no registrado)");
                        // cache preferences
                        if (doc.contains("preferences")) {
                            Object prefsObj = doc.get("preferences");
                            if (prefsObj instanceof java.util.Map) {
                                java.util.Map map = (java.util.Map) prefsObj;
                                Object notify = map.get("notificationsEnabled");
                                if (notify instanceof Boolean) authPrefs.edit().putBoolean("notify_enabled", (Boolean) notify).apply();
                                Object darkMode = map.get("darkModeEnabled");
                                // Only set local dark_mode from server if user hasn't already set a local preference.
                                if (darkMode instanceof Boolean) {
                                    if (!authPrefs.contains("dark_mode")) {
                                        authPrefs.edit().putBoolean("dark_mode", (Boolean) darkMode).apply();
                                    }
                                }
                            }
                        }

                        // load photoUrl (ic_profile_x) from user doc if present and apply to avatar + cache
                        try {
                            if (doc.contains("photoUrl")) {
                                Object pu = doc.get("photoUrl");
                                if (pu != null) {
                                    String iconName = pu.toString();
                                    authPrefs.edit().putString("profile_icon", iconName).apply();
                                    try {
                                        int resid = getResources().getIdentifier(iconName, "drawable", getPackageName());
                                        android.widget.ImageView avatar = findViewById(R.id.user_avatar);
                                        if (avatar != null && resid != 0) avatar.setImageResource(resid);
                                    } catch (Exception ignore) {}
                                }
                            }
                        } catch (Exception ignore) {}

                        // dark mode could be stored in preferences too
                        boolean notif = authPrefs.getBoolean("notify_enabled", true);
                        boolean dark = authPrefs.getBoolean("dark_mode", false);
                        // set switches without triggering their listeners
                        try {
                            suppressSwitchCallbacks = true;
                            switchNotifications.setChecked(notif);
                            switchNotifications.jumpDrawablesToCurrentState();
                            switchDarkMode.setChecked(dark);
                            switchDarkMode.jumpDrawablesToCurrentState();
                        } finally {
                            suppressSwitchCallbacks = false;
                        }
                        return;
                    }
                    // fallback to prefs if doc not present
                    fallbackLoadFromPrefs();
                }, e -> {
                    // on failure fallback
                    fallbackLoadFromPrefs();
                });
            } catch (Exception ex) {
                fallbackLoadFromPrefs();
            }
        } else {
            fallbackLoadFromPrefs();
        }
    }

    private void fallbackLoadFromPrefs() {
        String email = authPrefs.getString("email", "");
        String display = authPrefs.getString("displayName", "");
        if (display == null || display.isEmpty()) {
            if (email != null && email.contains("@")) display = email.split("@")[0];
            else display = "Usuario";
        }

        userNameTv.setText(display);
        userEmailTv.setText(email != null && !email.isEmpty() ? email : "(no registrado)");

        boolean notif = authPrefs.getBoolean("notify_enabled", true);
        boolean dark = authPrefs.getBoolean("dark_mode", false);
        try {
            suppressSwitchCallbacks = true;
            switchNotifications.setChecked(notif);
            switchNotifications.jumpDrawablesToCurrentState();
            switchDarkMode.setChecked(dark);
            switchDarkMode.jumpDrawablesToCurrentState();
        } finally {
            suppressSwitchCallbacks = false;
        }
    }

    private void computeStats() {
        // Prefer reading plans from Firestore when user is authenticated so UI reflects server state.
        String uid = authPrefs.getString("uid", null);
        if (uid != null) {
            try {
                new UserRepository(this).getPlans(uid, plans -> {
                    int plansCount = plans != null ? plans.size() : 0;
                    int totalQuizzes = 0;
                    int totalFlashcards = 0;
                    int totalHotTopics = 0;
                    if (plans != null) {
                        for (StudyPlan p : plans) {
                            if (p == null) continue;
                            if (p.quizzes != null) totalQuizzes += p.quizzes.size();
                            if (p.flashcards != null) totalFlashcards += p.flashcards.size();
                            if (p.hotTopics != null) totalHotTopics += p.hotTopics.size();
                        }
                    }
                    final int fPlans = plansCount;
                    final int fTotal = totalQuizzes;
                    final int fFlash = totalFlashcards;
                    final int fHot = totalHotTopics;
                    runOnUiThread(() -> {
                        tvPlansCount.setText(String.valueOf(fPlans));
                        tvQuizzesCount.setText(String.valueOf(fTotal));
                        tvFlashcardsCount.setText(String.valueOf(fFlash));
                        if (tvHotTopicsCount != null) tvHotTopicsCount.setText(String.valueOf(fHot));
                    });
                }, e -> {
                    // fallback to local storage on error
                    computeStatsFromLocal();
                });
                return;
            } catch (Exception ex) {
                // fall through to local compute
            }
        }

        // Fallback to local storage when not signed in or Firestore fails
        computeStatsFromLocal();
    }

    private void computeStatsFromLocal() {
        java.util.concurrent.ExecutorService ex = java.util.concurrent.Executors.newSingleThreadExecutor();
        ex.submit(() -> {
            ArrayList<StudyPlan> planes = StorageHelper.listarPlanes(this);
            int plansCount = planes != null ? planes.size() : 0;
            int totalQuizzes = 0;
            int totalFlashcards = 0;
            int totalHotTopics = 0;

            if (planes != null) {
                for (StudyPlan p : planes) {
                    if (p.quizzes != null) totalQuizzes += p.quizzes.size();
                    if (p.flashcards != null) totalFlashcards += p.flashcards.size();
                    if (p.hotTopics != null) totalHotTopics += p.hotTopics.size();
                }
            }

            final int fPlans = plansCount;
            final int fTotal = totalQuizzes;
            final int fFlash = totalFlashcards;
            final int fHot = totalHotTopics;
            runOnUiThread(() -> {
                tvPlansCount.setText(String.valueOf(fPlans));
                tvQuizzesCount.setText(String.valueOf(fTotal));
                tvFlashcardsCount.setText(String.valueOf(fFlash));
                if (tvHotTopicsCount != null) tvHotTopicsCount.setText(String.valueOf(fHot));
            });
        });
    }

    private void setupListeners() {
        btnEditProfile.setOnClickListener(v -> showEditProfileDialog());

        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSwitchCallbacks) return;
            authPrefs.edit().putBoolean("notify_enabled", isChecked).apply();
            // Persist notifications preference to Firestore as preferences.notificationsEnabled
            try {
                String uid = authPrefs.getString("uid", null);
                if (uid != null) {
                    java.util.Map<String, Object> prefUpdate = new java.util.HashMap<>();
                    prefUpdate.put("preferences.notificationsEnabled", isChecked);
                    com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(uid)
                            .update(prefUpdate)
                            .addOnFailureListener(e -> {
                                // ignore failures for now
                            });
                }
            } catch (Exception ignore) {}
        });

        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSwitchCallbacks) return;
            // Debounce: disable the switch briefly to avoid rapid toggles that may leave the UI in an inconsistent state
            try {
                switchDarkMode.setEnabled(false);
            } catch (Exception ignore) {}

            // Save local preference immediately (authoritative)
            authPrefs.edit().putBoolean("dark_mode", isChecked).apply();

            // Only change night mode if it differs from current global mode
            int currentMode = androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode();
            int targetMode = isChecked ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
            if (currentMode != targetMode) {
                try {
                    // mark that a theme transition is about to happen so activities can animate on recreate
                    authPrefs.edit().putBoolean("theme_just_changed", true).apply();
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(targetMode);
                } catch (Exception ignore) {
                    // fallback: still persist pref locally
                }
            }

            // Persist dark mode choice to Firestore (best-effort, non-blocking)
            try {
                String uid = authPrefs.getString("uid", null);
                if (uid != null) {
                    java.util.Map<String, Object> prefUpdate = new java.util.HashMap<>();
                    prefUpdate.put("preferences.darkModeEnabled", isChecked);
                    com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(uid)
                            .update(prefUpdate)
                            .addOnFailureListener(e -> {
                                // ignore failures for now
                            });
                }
            } catch (Exception ignore) {}

            // Re-enable the switch after a short delay; if the activity is recreated due to mode change, this code may not run, which is fine.
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                try {
                    suppressSwitchCallbacks = true;
                    switchDarkMode.setChecked(authPrefs.getBoolean("dark_mode", false));
                } catch (Exception ignore) {}
                finally {
                    suppressSwitchCallbacks = false;
                }
                try { switchDarkMode.setEnabled(true); } catch (Exception ignore) {}
            }, 800);
        });

    btnExport.setOnClickListener(v -> exportAllData());

        btnSignOut.setOnClickListener(v -> {
            authPrefs.edit().remove("email").remove("displayName").apply();
            startActivity(new Intent(this, SignInActivity.class));
            finish();
        });

    // upgrade action not present in layout; keep for future
    }

    private void showEditProfileDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Editar perfil");
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile, null);
        EditText nameInput = view.findViewById(R.id.edit_name);
        nameInput.setText(userNameTv.getText());

        // profile choices
        ImageButton c1 = view.findViewById(R.id.profile_choice_1);
        ImageButton c2 = view.findViewById(R.id.profile_choice_2);
        ImageButton c3 = view.findViewById(R.id.profile_choice_3);
        ImageButton c4 = view.findViewById(R.id.profile_choice_4);
        ImageButton c5 = view.findViewById(R.id.profile_choice_5);

        final String[] selectedIcon = {authPrefs.getString("profile_icon", "ic_profile_1")};

        View.OnClickListener pick = v -> {
            Object tag = v.getTag();
            if (tag != null) selectedIcon[0] = tag.toString();
            // visual feedback: simple alpha change
            try { c1.setAlpha(1f); c2.setAlpha(1f); c3.setAlpha(1f); c4.setAlpha(1f); c5.setAlpha(1f);} catch (Exception ignore) {}
            v.setAlpha(0.6f);
        };

        if (c1 != null) c1.setOnClickListener(pick);
        if (c2 != null) c2.setOnClickListener(pick);
        if (c3 != null) c3.setOnClickListener(pick);
        if (c4 != null) c4.setOnClickListener(pick);
        if (c5 != null) c5.setOnClickListener(pick);

        b.setView(view);
        b.setPositiveButton("Guardar", (dialog, which) -> {
            String newName = nameInput.getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(this, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show();
                return;
            }
            authPrefs.edit().putString("displayName", newName).apply();
            // save selected icon to prefs and Firestore photoUrl
            authPrefs.edit().putString("profile_icon", selectedIcon[0]).apply();

            String uid = authPrefs.getString("uid", null);
            if (uid != null) {
                try {
                    com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(uid)
                            .update("photoUrl", selectedIcon[0], "displayName", newName)
                            .addOnSuccessListener(a -> {
                                // update UI
                            }).addOnFailureListener(e -> {
                                // ignore
                            });
                } catch (Exception ignore) {}
            }

            // update UI in this activity
            try {
                int resid = getResources().getIdentifier(selectedIcon[0], "drawable", getPackageName());
                android.widget.ImageView avatar = findViewById(R.id.user_avatar);
                if (avatar != null && resid != 0) avatar.setImageResource(resid);
            } catch (Exception ignore) {}

            loadUserInfo();

            // update drawer header image & name if present
            try {
                com.google.android.material.navigation.NavigationView nav = findViewById(R.id.nav_view);
                if (nav != null) {
                    View header = nav.getHeaderView(0);
                    if (header != null) {
                        android.widget.ImageView drawerImg = header.findViewById(R.id.drawer_profile_img);
                        TextView drawerName = header.findViewById(R.id.drawer_user_name);
                        if (drawerName != null) drawerName.setText(newName);
                        if (drawerImg != null) {
                            int resid = getResources().getIdentifier(selectedIcon[0], "drawable", getPackageName());
                            if (resid == 0) resid = R.drawable.ic_profile_1;
                            drawerImg.setImageResource(resid);
                        }
                    }
                }
            } catch (Exception ignore) {}

            Toast.makeText(this, "Perfil actualizado", Toast.LENGTH_SHORT).show();
        });
        b.setNegativeButton("Cancelar", null);
        b.show();
    }

    private void exportAllData() {
        try {
            ArrayList<StudyPlan> planes = StorageHelper.listarPlanes(this);
            Gson gson = new Gson();
            String json = gson.toJson(planes != null ? planes : new ArrayList<>());
            String fname = "exported_data.json";
            try (FileOutputStream fos = openFileOutput(fname, Context.MODE_PRIVATE);
                 OutputStreamWriter w = new OutputStreamWriter(fos)) {
                w.write(json);
                w.flush();
            }
            String path = getFilesDir().getAbsolutePath() + "/" + fname;
            Toast.makeText(this, "Datos exportados: " + path, Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            ex.printStackTrace();
            Toast.makeText(this, "Error exportando datos", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void populateHistory() {
        // Prepare UI container and clear
        android.widget.LinearLayout historyList = findViewById(R.id.history_list);
        historyList.removeAllViews();

        // Background thread to collect history strings
        java.util.concurrent.ExecutorService ex = java.util.concurrent.Executors.newSingleThreadExecutor();
        ex.submit(() -> {
            ArrayList<StudyPlan> planes = StorageHelper.listarPlanes(this);
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
            java.util.List<String> items = new java.util.ArrayList<>();
            if (planes != null) {
                for (StudyPlan p : planes) {
                    if (p.quizzes != null) {
                        for (com.app.estudiaapp.models.Quiz q : p.quizzes) {
                            if (q != null && q.progreso != null && q.progreso.lastUpdatedMillis > 0) {
                                java.util.Date d = new java.util.Date(q.progreso.lastUpdatedMillis);
                                items.add(p.nombre + " • " + q.titulo + " — " + sdf.format(d));
                            }
                        }
                    }
                }
            }

            // Post UI updates
            runOnUiThread(() -> {
                if (items.isEmpty()) {
                    TextView tv = new TextView(this);
                    tv.setText("No hay historial disponible");
                    tv.setPadding(8,8,8,8);
                    tv.setTextColor(getResources().getColor(R.color.black));
                    historyList.addView(tv);
                } else {
                    int pad = (int) (4 * getResources().getDisplayMetrics().density);
                    for (String s : items) {
                        TextView tv = new TextView(this);
                        tv.setText(s);
                        tv.setTextAppearance(android.R.style.TextAppearance_Material_Body1);
                        tv.setTextColor(getResources().getColor(R.color.black));
                        tv.setPadding(pad, pad, pad, pad);
                        historyList.addView(tv);
                    }
                }
            });
        });
    }

}
