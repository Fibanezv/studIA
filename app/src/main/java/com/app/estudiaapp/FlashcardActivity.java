package com.app.estudiaapp;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

 
import androidx.core.content.ContextCompat;

import com.app.estudiaapp.helper.StorageHelper;
import com.app.estudiaapp.models.StudyPlan;
import com.app.estudiaapp.utils.Constants;
import com.app.estudiaapp.utils.ErrorDialogHelper;
import com.app.estudiaapp.utils.ErrorHandler;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.app.estudiaapp.UserRepository;

public class FlashcardActivity extends BaseActivity {

    private String planId;
    private StudyPlan plan;
    private TextView title, info;
    private com.app.estudiaapp.adapters.FlashcardsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flashcard);
        // Try to get planId passed via intent; if missing, fallback to first available plan
        planId = getIntent().getStringExtra(Constants.EXTRA_PLAN_ID);
        if (planId != null) {
            plan = StorageHelper.cargarPlan(this, planId);
        }

        if (plan == null) {
            // fallback: use last viewed plan id saved by PlanDetailsActivity
            try {
                android.content.SharedPreferences prefs = getSharedPreferences("app", MODE_PRIVATE);
                planId = prefs.getString("last_plan_id", null);
            } catch (Exception ignored) { planId = null; }
        }

        // If still null, create an empty plan placeholder so the Activity can open
        if (plan == null) {
            plan = new com.app.estudiaapp.models.StudyPlan(planId != null ? planId : "", "");
        }

        // Ensure flashcards list is non-null so adapters work with empty states
        if (plan.flashcards == null) plan.flashcards = new java.util.ArrayList<>();

        // Setup RecyclerView listing all flashcards for this plan (empty list allowed)
        androidx.recyclerview.widget.RecyclerView rv = findViewById(R.id.rv_flashcards_list);
        rv.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 2));
        adapter = new com.app.estudiaapp.adapters.FlashcardsAdapter(plan.flashcards, planId);
        rv.setAdapter(adapter);

        // If user is signed-in, prefer loading plan from Firestore so freshly generated plans appear
        String uid = null;
        try { uid = getSharedPreferences("auth", MODE_PRIVATE).getString("uid", null); } catch (Exception ignored) {}
        if (uid != null) {
            new UserRepository(this).refreshPlanIfNeeded(uid, planId, p -> {
                if (p != null && p.flashcards != null) {
                    this.plan = p;
                    if (adapter != null) adapter.updateItems(p.flashcards);
                }
            }, e -> {
                ErrorHandler.logError(e, "FlashcardActivity.refreshPlanIfNeeded");
                // fallback: no-op (we already have local plan) - show offline message
                ErrorDialogHelper.showErrorToast(FlashcardActivity.this, 
                    com.app.estudiaapp.utils.GeneralErrorType.FIRESTORE_READ_FAILED);
            });
        }

        // Setup navigation buttons
        com.google.android.material.button.MaterialButton btnPresentation = findViewById(R.id.btn_flashcards_presentation);
        if (btnPresentation != null) {
            btnPresentation.setOnClickListener(v -> {
                if (plan != null && plan.flashcards != null && !plan.flashcards.isEmpty()) {
                    // Abrir la primera flashcard
                    Intent intent = new Intent(this, FlashcardDetailActivity.class);
                    intent.putExtra(Constants.EXTRA_PLAN_ID, planId);
                    intent.putExtra(Constants.EXTRA_FLASHCARD_INDEX, 0);
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "No hay flashcards disponibles", Toast.LENGTH_SHORT).show();
                }
            });
        }

        com.google.android.material.button.MaterialButton btnShuffle = findViewById(R.id.btn_flashcards_shuffle);
        if (btnShuffle != null) {
            btnShuffle.setOnClickListener(v -> {
                if (plan != null && plan.flashcards != null && !plan.flashcards.isEmpty()) {
                    // Abrir una flashcard aleatoria
                    int randomIndex = new java.util.Random().nextInt(plan.flashcards.size());
                    Intent intent = new Intent(this, FlashcardDetailActivity.class);
                    intent.putExtra(Constants.EXTRA_PLAN_ID, planId);
                    intent.putExtra(Constants.EXTRA_FLASHCARD_INDEX, randomIndex);
                    intent.putExtra("random_mode", true);
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "No hay flashcards disponibles", Toast.LENGTH_SHORT).show();
                }
            });
        }

        com.google.android.material.button.MaterialButton btnFavorites = findViewById(R.id.btn_flashcards_favorites);
        if (btnFavorites != null) {
            btnFavorites.setOnClickListener(v -> {
                if (plan != null && plan.flashcards != null && !plan.flashcards.isEmpty()) {
                    // Buscar la primera flashcard favorita
                    int firstFavoriteIndex = -1;
                    for (int i = 0; i < plan.flashcards.size(); i++) {
                        if (plan.flashcards.get(i).favorita) {
                            firstFavoriteIndex = i;
                            break;
                        }
                    }

                    if (firstFavoriteIndex >= 0) {
                        Intent intent = new Intent(this, FlashcardDetailActivity.class);
                        intent.putExtra(Constants.EXTRA_PLAN_ID, planId);
                        intent.putExtra(Constants.EXTRA_FLASHCARD_INDEX, firstFavoriteIndex);
                        intent.putExtra("favorites_mode", true);
                        startActivity(intent);
                    } else {
                        Toast.makeText(this, "No tienes flashcards favoritas aún", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "No hay flashcards disponibles", Toast.LENGTH_SHORT).show();
                }
            });
        }

        setupNavigation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh the plan and adapter so completed state (background) updates immediately
        if (planId == null || adapter == null) return;
        String uid = null;
        try { uid = getSharedPreferences("auth", MODE_PRIVATE).getString("uid", null); } catch (Exception ignored) {}
        if (uid != null) {
            new UserRepository(this).refreshPlanIfNeeded(uid, planId, p -> {
                if (p != null && p.flashcards != null) {
                    this.plan = p;
                    adapter.updateItems(p.flashcards);
                }
            }, e -> {
                ErrorHandler.logError(e, "FlashcardActivity.onResume.refreshPlanIfNeeded");
                // Fallback to local storage silently - already have loaded plan
                StudyPlan updated = StorageHelper.cargarPlan(this, planId);
                if (updated != null) {
                    this.plan = updated;
                    adapter.updateItems(updated.flashcards);
                }
            });
        } else {
            StudyPlan updated = StorageHelper.cargarPlan(this, planId);
            if (updated != null) {
                this.plan = updated;
                adapter.updateItems(updated.flashcards);
            }
        }
    }

    

    private void setupNavigation() {
        BottomNavigationView nav = findViewById(R.id.study_nav);
        nav.setSelectedItemId(R.id.nav_flashcard);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Intent intent = null;
            if (id == R.id.nav_details) {
                intent = new Intent(this, PlanDetailsActivity.class);
            } else if (id == R.id.nav_hot_topics) {
                intent = new Intent(this, HotTopicsActivity.class);
            } else if (id == R.id.nav_quiz) {
                // abrir la lista de quizzes del plan
                intent = new Intent(this, QuizListActivity.class);
            } else if (id == R.id.nav_flashcard) {
                // already here
                return true;
            }

            if (intent != null) {
                intent.putExtra(Constants.EXTRA_PLAN_ID, planId);
                startActivity(intent);
                overridePendingTransition(0, 0);
                return true;
            }

            return false;
        });
    }
}
