package com.app.estudiaapp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

 
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.estudiaapp.adapters.QuizzesAdapter;
import com.app.estudiaapp.adapters.HotTopicsAdapter;
import com.app.estudiaapp.adapters.FlashcardsAdapter;
import com.app.estudiaapp.helper.StorageHelper;
import com.app.estudiaapp.models.StudyPlan;
import com.app.estudiaapp.models.Quiz;
import com.app.estudiaapp.models.Flashcard;
import com.app.estudiaapp.models.HotTopic;
import com.app.estudiaapp.utils.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

public class PlanDetailsActivity extends BaseActivity {
    private String planIdField;
    public static void start(Context context, String planId) {
        Intent intent = new Intent(context, PlanDetailsActivity.class);
        intent.putExtra(Constants.EXTRA_PLAN_ID, planId);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plan_details);
        String planId = getIntent().getStringExtra(Constants.EXTRA_PLAN_ID);
        this.planIdField = planId;
        if (planId == null) {
            finish();
            return;
        }

        // initial UI population
        refreshUI();
        // setup bottom navigation so user can move between sections
        setupNavigation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh UI when returning to this activity so progress reflects recent changes
        refreshUI();
    }

    /**
     * Load the plan from storage and refresh all UI sections.
     * This method is safe to call multiple times (e.g. onResume) and will update
     * visibility of sections and adapters accordingly.
     */
    private void refreshUI() {
        if (this.planIdField == null) return;
        // Try to load plan from Firestore then fallback to local storage
        StudyPlan[] holder = new StudyPlan[1];
        String uid = null;
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
            uid = prefs.getString("uid", null);
        } catch (Exception ignored) {}

        if (uid != null) {
            new UserRepository(this).getPlan(uid, this.planIdField, p -> {
                if (p == null) {
                    // fallback to local
                    StudyPlan planLocal = StorageHelper.cargarPlan(this, this.planIdField);
                    if (planLocal == null) { finish(); return; }
                    populateUIWithPlan(planLocal);
                } else {
                    populateUIWithPlan(p);
                }
            }, e -> {
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

    int totalFlash = (plan.flashcards == null) ? 0 : plan.flashcards.size();
    int studiedFlash = (plan.progreso == null) ? 0 : plan.progreso.flashcardsEstudiadas;
    int flashPct = totalFlash > 0 ? (int) ((studiedFlash * 100.0f) / totalFlash) : 0;
    TextView tvFlashPct = findViewById(R.id.tv_flashcards_pct);
    if (tvFlashPct != null) tvFlashPct.setText(flashPct + "%");

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
    TextView tvQuizPct = findViewById(R.id.tv_quizzes_pct);
    if (tvQuizPct != null) tvQuizPct.setText(quizPct + "%");

    // Hot topics count - show total number on the stats row
    int totalHotTopics = (plan.hotTopics == null) ? 0 : plan.hotTopics.size();
    TextView hotCountTv = findViewById(R.id.tv_hot_topics_count);
    if (hotCountTv != null) hotCountTv.setText(String.valueOf(totalHotTopics));

        // Badges
        androidx.cardview.widget.CardView userBadgesCard = findViewById(R.id.user_badges_card);
        android.widget.GridLayout badgesContainer = findViewById(R.id.badges_container);
        badgesContainer.removeAllViews();
        java.util.List<com.app.estudiaapp.models.Badge> badges = com.app.estudiaapp.models.BadgesRepository.getBadgesForPlan(plan);
        int achievedCount = 0;
        if (badges != null) {
            for (com.app.estudiaapp.models.Badge b : badges) {
                if (b != null && b.achieved) achievedCount++;
            }
        }
        if (achievedCount <= 0) {
            if (userBadgesCard != null) userBadgesCard.setVisibility(View.GONE);
        } else {
            if (userBadgesCard != null) userBadgesCard.setVisibility(View.VISIBLE);
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

        // RecyclerViews
        androidx.recyclerview.widget.RecyclerView rvQuizzesToStart = findViewById(R.id.rv_quizzes_to_start);
        androidx.recyclerview.widget.RecyclerView rvQuizzesInProgress = findViewById(R.id.rv_quizzes_in_progress);
        androidx.recyclerview.widget.RecyclerView rvFlashcardsPending = findViewById(R.id.rv_flashcards_pending);
        androidx.recyclerview.widget.RecyclerView rvTopicsRelevant = findViewById(R.id.rv_topics_relevant);

        TextView titleQuizzesToStart = findViewById(R.id.section_quizzes_to_start_title);
        TextView titleQuizzesInProgress = findViewById(R.id.section_quizzes_in_progress_title);
        TextView titleFlashcardsPending = findViewById(R.id.section_flashcards_pending_title);
        TextView titleTopicsRelevant = findViewById(R.id.section_topics_relevant_title);

        rvQuizzesToStart.setLayoutManager(new LinearLayoutManager(this));
        rvQuizzesInProgress.setLayoutManager(new LinearLayoutManager(this));
        rvFlashcardsPending.setLayoutManager(new LinearLayoutManager(this));
        rvTopicsRelevant.setLayoutManager(new LinearLayoutManager(this));

        // Prepare filtered lists
        ArrayList<Quiz> quizzesToStart = new ArrayList<>();
        ArrayList<Quiz> quizzesInProgress = new ArrayList<>();
        if (plan.quizzes != null) {
            for (Quiz q : plan.quizzes) {
                if (q == null) continue;
                int total = (q.questions == null) ? 0 : q.questions.size();
                int answered = (q.progreso == null) ? 0 : q.progreso.preguntasRespondidas;
                if (answered <= 0) {
                    if (quizzesToStart.size() < 5) quizzesToStart.add(q);
                } else if (total > 0 && answered > 0 && answered < total) {
                    if (quizzesInProgress.size() < 5) quizzesInProgress.add(q);
                }
                if (quizzesToStart.size() >= 5 && quizzesInProgress.size() >= 5) break;
            }
        }

        ArrayList<Flashcard> flashcardsPending = new ArrayList<>();
        ArrayList<Integer> flashcardIndices = new ArrayList<>();
        if (plan.flashcards != null) {
            for (int i = 0; i < plan.flashcards.size() && flashcardsPending.size() < 5; i++) {
                Flashcard f = plan.flashcards.get(i);
                if (f == null) continue;
                if (f.completada) continue;
                flashcardsPending.add(f);
                flashcardIndices.add(i);
            }
        }

        ArrayList<HotTopic> topicsRelevant = new ArrayList<>();
        if (plan.hotTopics != null && !plan.hotTopics.isEmpty()) {
            java.util.List<HotTopic> temp = new ArrayList<>(plan.hotTopics);
            Collections.shuffle(temp, new Random(System.currentTimeMillis()));
            for (int i = 0; i < Math.min(5, temp.size()); i++) topicsRelevant.add(temp.get(i));
        }

        if (quizzesToStart.isEmpty()) {
            rvQuizzesToStart.setVisibility(View.GONE);
            titleQuizzesToStart.setVisibility(View.GONE);
        } else {
            rvQuizzesToStart.setVisibility(View.VISIBLE);
            titleQuizzesToStart.setVisibility(View.VISIBLE);
            String uid = null;
            try { uid = getSharedPreferences("auth", MODE_PRIVATE).getString("uid", null); } catch (Exception ignored) {}
            QuizzesAdapter a = new QuizzesAdapter(quizzesToStart, plan, uid, quiz -> {
                QuizActivity.start(PlanDetailsActivity.this, plan.id, quiz.id);
            }, this::refreshUI);
            rvQuizzesToStart.setAdapter(a);
        }

        if (quizzesInProgress.isEmpty()) {
            rvQuizzesInProgress.setVisibility(View.GONE);
            titleQuizzesInProgress.setVisibility(View.GONE);
        } else {
            rvQuizzesInProgress.setVisibility(View.VISIBLE);
            titleQuizzesInProgress.setVisibility(View.VISIBLE);
            String uid = null;
            try { uid = getSharedPreferences("auth", MODE_PRIVATE).getString("uid", null); } catch (Exception ignored) {}
            QuizzesAdapter a2 = new QuizzesAdapter(quizzesInProgress, plan, uid, quiz -> {
                QuizActivity.start(PlanDetailsActivity.this, plan.id, quiz.id);
            }, this::refreshUI);
            rvQuizzesInProgress.setAdapter(a2);
        }

        if (flashcardsPending.isEmpty()) {
            rvFlashcardsPending.setVisibility(View.GONE);
            titleFlashcardsPending.setVisibility(View.GONE);
        } else {
            rvFlashcardsPending.setVisibility(View.VISIBLE);
            titleFlashcardsPending.setVisibility(View.VISIBLE);
            FlashcardsAdapter fa = new FlashcardsAdapter(flashcardsPending, plan.id, flashcardIndices);
            rvFlashcardsPending.setAdapter(fa);
        }

        if (topicsRelevant.isEmpty()) {
            rvTopicsRelevant.setVisibility(View.GONE);
            titleTopicsRelevant.setVisibility(View.GONE);
        } else {
            rvTopicsRelevant.setVisibility(View.VISIBLE);
            titleTopicsRelevant.setVisibility(View.VISIBLE);
            HotTopicsAdapter ht = new HotTopicsAdapter(topicsRelevant);
            rvTopicsRelevant.setAdapter(ht);
        }

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
                startActivity(intent);
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
    }
}
