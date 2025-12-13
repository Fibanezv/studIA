package com.app.estudiaapp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.app.estudiaapp.helper.StorageHelper;
import com.app.estudiaapp.models.StudyPlan;
import com.app.estudiaapp.models.Quiz;
import com.app.estudiaapp.utils.Constants;
import com.app.estudiaapp.utils.ErrorDialogHelper;
import com.app.estudiaapp.utils.ErrorHandler;
import com.google.firebase.firestore.ListenerRegistration;

public class PlanDetailsActivity extends BaseActivity {
    private String planIdField;
    private ListenerRegistration planListener;
    
    public static void start(Context context, String planId) {
        Intent intent = new Intent(context, PlanDetailsActivity.class);
        intent.putExtra(Constants.EXTRA_PLAN_ID, planId);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Configure shared element container transform for enter/return
        try {
            com.google.android.material.transition.platform.MaterialContainerTransform enter =
                new com.google.android.material.transition.platform.MaterialContainerTransform();
            enter.setDuration(280);
            enter.setScrimColor(0x00000000);
            getWindow().setSharedElementEnterTransition(enter);

            com.google.android.material.transition.platform.MaterialContainerTransform returnT =
                new com.google.android.material.transition.platform.MaterialContainerTransform();
            returnT.setDuration(240);
            returnT.setScrimColor(0x00000000);
            getWindow().setSharedElementReturnTransition(returnT);
        } catch (Exception ignore) {}
        setContentView(R.layout.activity_plan_details);
        String planId = getIntent().getStringExtra(Constants.EXTRA_PLAN_ID);
        this.planIdField = planId;
        if (planId == null) {
            finish();
            return;
        }

        // initial UI population
        refreshUI();
        
        // Register snapshot listener for real-time updates
        String uid = null;
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
            uid = prefs.getString("uid", null);
        } catch (Exception ignored) {}
        
        if (uid != null) {
            String finalUid = uid;
            planListener = new UserRepository(this).registerPlanListener(uid, planId, plan -> {
                // Real-time update received - refresh UI
                if (plan != null) {
                    populateUIWithPlan(plan);
                }
            });
        }
        
        // setup bottom navigation so user can move between sections
        setupNavigation();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister snapshot listener to prevent memory leaks
        if (planListener != null) {
            planListener.remove();
            planListener = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh UI when returning - checks cache TTL and refetches if needed
        refreshUI();
    }

    /**
     * Load the plan from storage and refresh all UI sections.
     * Uses refreshPlanIfNeeded() to respect cache TTL and avoid unnecessary network calls.
     * This method is safe to call multiple times (e.g. onResume) and will update
     * visibility of sections and adapters accordingly.
     */
    private void refreshUI() {
        if (this.planIdField == null) return;
        
        String uid = null;
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
            uid = prefs.getString("uid", null);
        } catch (Exception ignored) {}

        if (uid != null) {
            // Use refreshPlanIfNeeded to leverage TTL cache - only fetches if expired
            new UserRepository(this).refreshPlanIfNeeded(uid, this.planIdField, p -> {
                if (p == null) {
                    // fallback to local
                    StudyPlan planLocal = StorageHelper.cargarPlan(this, this.planIdField);
                    if (planLocal == null) { finish(); return; }
                    populateUIWithPlan(planLocal);
                } else {
                    populateUIWithPlan(p);
                }
            }, e -> {
                ErrorHandler.logError(e, "PlanDetailsActivity.refreshPlanIfNeeded");
                // Show feedback about using offline data and try to fallback
                ErrorDialogHelper.showErrorToast(PlanDetailsActivity.this, 
                    com.app.estudiaapp.utils.GeneralErrorType.FIRESTORE_READ_FAILED);
                
                StudyPlan planLocal = StorageHelper.cargarPlan(this, this.planIdField);
                if (planLocal == null) { finish(); return; }
                populateUIWithPlan(planLocal);
            });
            return;
        } else {
            StudyPlan plan = StorageHelper.cargarPlan(this, this.planIdField);
            if (plan == null) { finish(); return; }
            populateUIWithPlan(plan);
            return;
        }
    }

    private void populateUIWithPlan(StudyPlan plan) {
        // UI refs and top-level stats
        TextView planTitle = findViewById(R.id.user_text);
        planTitle.setText(plan.nombre != null ? plan.nombre : "Plan");
        
        // Persist last viewed plan id so other activities can fallback to it
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("app", MODE_PRIVATE);
            prefs.edit().putString("last_plan_id", plan.id != null ? plan.id : "").apply();
        } catch (Exception ignored) {}

        // Setup description section (fixed, no toggle)
        LinearLayout descriptionSection = findViewById(R.id.description_section);
        TextView tvDescription = findViewById(R.id.tv_plan_description);
        View separatorDescription = findViewById(R.id.separator_description);
        
        if (plan.description != null && !plan.description.trim().isEmpty()) {
            descriptionSection.setVisibility(View.VISIBLE);
            separatorDescription.setVisibility(View.VISIBLE);
            tvDescription.setText(plan.description);
        } else {
            descriptionSection.setVisibility(View.GONE);
            separatorDescription.setVisibility(View.GONE);
        }

        // Calculate all stats
        int totalFlash = (plan.flashcards == null) ? 0 : plan.flashcards.size();
        int studiedFlash = (plan.progreso == null) ? 0 : plan.progreso.flashcardsEstudiadas;
        int flashPct = totalFlash > 0 ? (int) ((studiedFlash * 100.0f) / totalFlash) : 0;

        int totalQuizzes = (plan.quizzes == null) ? 0 : plan.quizzes.size();
        int completedQuizzes = 0;
        int totalQuestions = 0;
        int totalAnswered = 0;
        int totalCorrect = 0;
        boolean anyPerfect = false;
        if (plan.quizzes != null) {
            for (Quiz q : plan.quizzes) {
                int tq = (q.questions == null) ? 0 : q.questions.size();
                totalQuestions += tq;
                int answered = (q.progreso == null) ? 0 : q.progreso.preguntasRespondidas;
                int correct = (q.progreso == null) ? 0 : q.progreso.preguntasCorrectas;
                totalAnswered += answered;
                totalCorrect += correct;
                if (tq > 0 && answered >= tq) completedQuizzes++;
                if (tq > 0 && correct >= tq) anyPerfect = true;
            }
        }

        int quizPct = totalQuestions > 0 ? (int) ((totalAnswered * 100.0f) / totalQuestions) : 0;
        int correctPct = totalAnswered > 0 ? (int) ((totalCorrect * 100.0f) / totalAnswered) : 0;
        int quizCompletedPct = totalQuizzes > 0 ? (int) ((completedQuizzes * 100.0f) / totalQuizzes) : 0;
        int totalHotTopics = (plan.hotTopics == null) ? 0 : plan.hotTopics.size();

        // Populate Overview Grid
        TextView tvOverviewFlashcardsTotal = findViewById(R.id.tv_overview_flashcards_total);
        TextView tvOverviewHotTopicsTotal = findViewById(R.id.tv_overview_hot_topics_total);
        TextView tvOverviewQuizzesTotal = findViewById(R.id.tv_overview_quizzes_total);
        if (tvOverviewFlashcardsTotal != null) tvOverviewFlashcardsTotal.setText(String.valueOf(totalFlash));
        if (tvOverviewHotTopicsTotal != null) tvOverviewHotTopicsTotal.setText(String.valueOf(totalHotTopics));
        if (tvOverviewQuizzesTotal != null) tvOverviewQuizzesTotal.setText(String.valueOf(totalQuizzes));

        // Populate Flashcards progress
        android.widget.ProgressBar progressFlashcards = findViewById(R.id.progress_flashcards);
        TextView tvFlashPct = findViewById(R.id.tv_flashcards_pct);
        TextView tvFlashLabel = findViewById(R.id.tv_flashcards_label);
        if (progressFlashcards != null) progressFlashcards.setProgress(flashPct);
        if (tvFlashPct != null) tvFlashPct.setText(flashPct + "%");
        if (tvFlashLabel != null) tvFlashLabel.setText("Flashcards progreso (" + studiedFlash + "/" + totalFlash + ")");

        // Populate Quizzes progress
        android.widget.ProgressBar progressQuizzesCompleted = findViewById(R.id.progress_quizzes_completed);
        TextView tvQuizzesCompletedPct = findViewById(R.id.tv_quizzes_completed_pct);
        TextView tvQuizzesCompletedLabel = findViewById(R.id.tv_quizzes_completed_label);
        if (progressQuizzesCompleted != null) progressQuizzesCompleted.setProgress(quizCompletedPct);
        if (tvQuizzesCompletedPct != null) tvQuizzesCompletedPct.setText(quizCompletedPct + "%");
        if (tvQuizzesCompletedLabel != null) tvQuizzesCompletedLabel.setText("Quizzes completados (" + completedQuizzes + "/" + totalQuizzes + ")");

        android.widget.ProgressBar progressQuestionsAnswered = findViewById(R.id.progress_questions_answered);
        TextView tvQuestionsAnsweredPct = findViewById(R.id.tv_questions_answered_pct);
        TextView tvQuestionsAnsweredLabel = findViewById(R.id.tv_questions_answered_label);
        if (progressQuestionsAnswered != null) progressQuestionsAnswered.setProgress(quizPct);
        if (tvQuestionsAnsweredPct != null) tvQuestionsAnsweredPct.setText(quizPct + "%");
        if (tvQuestionsAnsweredLabel != null) tvQuestionsAnsweredLabel.setText("Preguntas respondidas (" + totalAnswered + "/" + totalQuestions + ")");

        android.widget.ProgressBar progressQuestionsCorrect = findViewById(R.id.progress_questions_correct);
        TextView tvQuestionsCorrectPct = findViewById(R.id.tv_questions_correct_pct);
        TextView tvQuestionsCorrectLabel = findViewById(R.id.tv_questions_correct_label);
        if (progressQuestionsCorrect != null) progressQuestionsCorrect.setProgress(correctPct);
        if (tvQuestionsCorrectPct != null) tvQuestionsCorrectPct.setText(correctPct + "%");
        if (tvQuestionsCorrectLabel != null) tvQuestionsCorrectLabel.setText("Preguntas acertadas (" + totalCorrect + "/" + totalAnswered + ")");

        // Hide progress section if all progress is 0%
        LinearLayout progressSection = findViewById(R.id.progress_section);
        boolean hasProgress = flashPct > 0 || quizCompletedPct > 0 || quizPct > 0 || correctPct > 0;
        if (progressSection != null) {
            progressSection.setVisibility(hasProgress ? View.VISIBLE : View.GONE);
        }

        // Badges section (fixed, no toggle)
        LinearLayout badgesSection = findViewById(R.id.badges_section);
        android.widget.GridLayout badgesContainer = findViewById(R.id.badges_container);
        View separatorBadges = findViewById(R.id.separator_badges);
        badgesContainer.removeAllViews();
        java.util.List<com.app.estudiaapp.models.Badge> badges = com.app.estudiaapp.models.BadgesRepository.getBadgesForPlan(plan);
        int achievedCount = 0;
        if (badges != null) {
            for (com.app.estudiaapp.models.Badge b : badges) {
                if (b != null && b.achieved) achievedCount++;
            }
        }
        if (achievedCount <= 0) {
            badgesSection.setVisibility(View.GONE);
            separatorBadges.setVisibility(View.GONE);
        } else {
            badgesSection.setVisibility(View.VISIBLE);
            separatorBadges.setVisibility(View.VISIBLE);
            for (com.app.estudiaapp.models.Badge b : badges) {
                if (b == null || !b.achieved) continue;
                android.view.View v = getLayoutInflater().inflate(R.layout.item_badge_plan, badgesContainer, false);
                android.widget.ImageView badgeImg = v.findViewById(R.id.badge_image);
                try { badgeImg.setImageResource(b.iconRes); } catch (Exception ex) {}
                badgeImg.setContentDescription(b.title);
                v.setOnClickListener(view -> android.widget.Toast.makeText(PlanDetailsActivity.this, b.title, android.widget.Toast.LENGTH_SHORT).show());
                badgesContainer.addView(v);
            }
        }

        // Reset/Delete behavior
        findViewById(R.id.user_reset_plan).setOnClickListener(view -> {
            if (plan.quizzes != null) {
                for (Quiz q : plan.quizzes) {
                    if (q.progreso == null) q.progreso = new com.app.estudiaapp.models.QuizProgress();
                    q.progreso.preguntasRespondidas = 0;
                    q.progreso.preguntasCorrectas = 0;
                    q.progreso.preguntasIncorrectas = 0;
                    q.progreso.currentIndex = 0;
                    q.progreso.nota = -1;
                    q.progreso.lastUpdatedMillis = System.currentTimeMillis();
                }
            }
            if (plan.progreso != null) {
                plan.progreso.preguntasRespondidas = 0;
                plan.progreso.preguntasCorrectas = 0;
                plan.progreso.flashcardsEstudiadas = 0;
            }
            // save to Firestore
            String uid = null;
            try { uid = getSharedPreferences("auth", MODE_PRIVATE).getString("uid", null); } catch (Exception ignored) {}
            if (uid != null) {
                new UserRepository(this).savePlan(uid, plan, a -> {
                    Toast.makeText(this, "Plan reiniciado correctamente", Toast.LENGTH_SHORT).show();
                    refreshUI();
                }, e -> {
                    Toast.makeText(this, "Error al reiniciar plan: " + e, Toast.LENGTH_SHORT).show();
                });
            } else {
                Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.user_delete_plan).setOnClickListener(view -> {
            // delete from Firestore
            String uid = null;
            try { uid = getSharedPreferences("auth", MODE_PRIVATE).getString("uid", null); } catch (Exception ignored) {}
            if (uid != null) {
                com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(uid).collection("plans").document(plan.id)
                        .delete()
                        .addOnSuccessListener(a -> {
                            Toast.makeText(this, "Plan eliminado correctamente", Toast.LENGTH_SHORT).show();
                            // Redirigir explícitamente al MainActivity
                            android.content.Intent intent = new android.content.Intent(PlanDetailsActivity.this, MainActivity.class);
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            finish();
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "Error al eliminar plan: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
            } else {
                Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show();
            }
        });

    }

    private void setupNavigation() {
        com.google.android.material.bottomnavigation.BottomNavigationView nav = findViewById(R.id.study_nav);
        nav.setSelectedItemId(R.id.nav_details);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            android.content.Intent intent = null;

            if (id == R.id.nav_flashcard) {
                intent = new android.content.Intent(this, FlashcardActivity.class);
            } else if (id == R.id.nav_hot_topics) {
                intent = new android.content.Intent(this, HotTopicsActivity.class);
            } else if (id == R.id.nav_quiz) {
                intent = new android.content.Intent(this, QuizListActivity.class);
            } else if (id == R.id.nav_details) {
                // already here
                return true;
            }

            if (intent != null) {
                intent.putExtra(com.app.estudiaapp.utils.Constants.EXTRA_PLAN_ID, this.planIdField);
                // Fade-through between sections to minimize content jank
                startActivityFadeThrough(intent);
                return true;
            }
            return false;
        });
    }
}
