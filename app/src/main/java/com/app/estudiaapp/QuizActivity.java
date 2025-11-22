package com.app.estudiaapp;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.RadioButton;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;
import android.content.res.ColorStateList;
import androidx.core.content.ContextCompat;

 

import com.app.estudiaapp.helper.StorageHelper;
import com.app.estudiaapp.models.Question;
import com.app.estudiaapp.models.StudyPlan;
import com.app.estudiaapp.models.Quiz;
import com.app.estudiaapp.utils.Constants;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import android.animation.ObjectAnimator;
import android.view.animation.AccelerateDecelerateInterpolator;

import java.util.List;

public class QuizActivity extends BaseActivity {

    private List<Question> questionList;
    private int current = 0;
    private String planId;
    private String quizId;
    private Quiz currentQuiz;
    // UI progress fields
    private android.view.View progressContainer;
    private android.view.View progressFill;

    // start con planId + quizId
    public static void start(Context context, String planId, String quizId) {
        Intent intent = new Intent(context, QuizActivity.class);
        intent.putExtra(Constants.EXTRA_PLAN_ID, planId);
        intent.putExtra("quiz_id", quizId);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);

        planId = getIntent().getStringExtra(Constants.EXTRA_PLAN_ID);
        quizId = getIntent().getStringExtra("quiz_id");
        if (planId == null) {
            Toast.makeText(this, "Error: plan_id no recibido", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        // Load plan from Firestore (preferred) or fallback to local StorageHelper
        String uid = null;
        try { uid = getSharedPreferences("auth", MODE_PRIVATE).getString("uid", null); } catch (Exception ignored) {}
        if (uid != null) {
            new UserRepository(this).getPlan(uid, planId, plan -> {
                if (plan == null || plan.quizzes == null || plan.quizzes.isEmpty()) {
                    Toast.makeText(this, "No hay quizzes disponibles.", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                initializeWithPlan(plan);
            }, e -> {
                StudyPlan plan = StorageHelper.cargarPlan(this, planId);
                if (plan == null || plan.quizzes == null || plan.quizzes.isEmpty()) {
                    Toast.makeText(this, "No hay quizzes disponibles.", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                initializeWithPlan(plan);
            });
            return;
        } else {
            StudyPlan plan = StorageHelper.cargarPlan(this, planId);
            if (plan == null || plan.quizzes == null || plan.quizzes.isEmpty()) {
                Toast.makeText(this, "No hay quizzes disponibles.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            initializeWithPlan(plan);
        }

        // UI will be initialized inside initializeWithPlan(StudyPlan)
    }

    private void initializeWithPlan(StudyPlan plan) {
        // Seleccionar quiz por id (o fallback al primero)
        currentQuiz = null;
        if (plan.quizzes != null) {
            for (Quiz q : plan.quizzes) {
                if (q != null && q.id != null && q.id.equals(quizId)) {
                    currentQuiz = q;
                    break;
                }
            }
        }
        if (currentQuiz == null && plan.quizzes != null && !plan.quizzes.isEmpty()) currentQuiz = plan.quizzes.get(0);

        questionList = currentQuiz != null ? currentQuiz.questions : null;
        // reanudar desde progreso (si existe)
        if (currentQuiz != null && currentQuiz.progreso != null) {
            int idx = currentQuiz.progreso.currentIndex;
            if (idx < 0) idx = 0;
            if (questionList != null && idx <= questionList.size()) {
                current = idx;
            }
        }

        // Initialize UI
        showQuestion();
        setupNavigation();
        // find progress UI
        progressContainer = findViewById(R.id.quiz_progress_container);
        progressFill = findViewById(R.id.quiz_progress_fill);
        refreshProgressUI();
    }

    private void showQuestion() {
        if (questionList == null) return;
        if (current >= questionList.size()) {
            // Quiz completado: mostrar pantalla de resultados (final summary)
            showResultsDialog();
            return;
        }

    // actualizar contador en UI
    refreshProgressUI();

        Question q = questionList.get(current);
        ((TextView) findViewById(R.id.question_text)).setText(q.question);
        RadioGroup group = findViewById(R.id.options_group);
        group.removeAllViews();

        for (String opt : q.options) {
            RadioButton rb = new RadioButton(this);
            rb.setText(opt);
            // ensure each RadioButton has a unique id so getCheckedRadioButtonId() resolves correctly
            rb.setId(View.generateViewId());

            // tint the radio button circle to the app's primary navy color and a muted color when unchecked
            int checkedColor = ContextCompat.getColor(this, R.color.navi_700_theme);
            int uncheckedColor = ContextCompat.getColor(this, R.color.navi_500_theme);
            int[][] states = new int[][]{
                    new int[]{android.R.attr.state_checked},
                    new int[]{}
            };
            int[] colors = new int[]{checkedColor, uncheckedColor};
            ColorStateList csl = new ColorStateList(states, colors);
            rb.setButtonTintList(csl);

            // ensure text color contrasts with the background
            rb.setTextColor(ContextCompat.getColor(this, R.color.black));

            group.addView(rb);
        }

        ((Button) findViewById(R.id.submit_button)).setOnClickListener(v -> {
            int selected = group.getCheckedRadioButtonId();
            if (selected != -1) {
                RadioButton selectedBtn = findViewById(selected);
                boolean correcto = selectedBtn.getText().toString().equals(q.answer);

                // actualizar progreso del quiz y reanudar posición
                if (currentQuiz != null && currentQuiz.progreso != null) {
                    int total = (questionList == null) ? 0 : questionList.size();
                    currentQuiz.progreso.actualizar(correcto, total);
                }

                // avanzar al siguiente índice y persistir currentIndex (cap al total)
                current++;
                int totalNow = (questionList == null) ? 0 : questionList.size();
                if (current > totalNow) current = totalNow;
                if (currentQuiz != null && currentQuiz.progreso != null) {
                    currentQuiz.progreso.currentIndex = Math.min(current, totalNow);
                }

                // Guardar plan actualizado
                // Persist progress: prefer updating Firestore
                try {
                    String uid = getSharedPreferences("auth", MODE_PRIVATE).getString("uid", null);
                    if (uid != null) {
                        // update only the quiz progress using repository method
                        new UserRepository(QuizActivity.this).updateQuizProgress(uid, planId, currentQuiz.id, currentQuiz.progreso, a -> {
                            // no-op success
                        }, e -> {
                            // fallback: save whole plan locally
                            StudyPlan plan = StorageHelper.cargarPlan(QuizActivity.this, planId);
                            if (plan != null) {
                                for (int i = 0; i < plan.quizzes.size(); i++) {
                                    if (plan.quizzes.get(i).id.equals(currentQuiz.id)) {
                                        plan.quizzes.set(i, currentQuiz);
                                        break;
                                    }
                                }
                                StorageHelper.guardarPlan(QuizActivity.this, plan);
                            }
                        });
                    } else {
                        StudyPlan plan = StorageHelper.cargarPlan(QuizActivity.this, planId);
                        if (plan != null) {
                            for (int i = 0; i < plan.quizzes.size(); i++) {
                                if (plan.quizzes.get(i).id.equals(currentQuiz.id)) {
                                    plan.quizzes.set(i, currentQuiz);
                                    break;
                                }
                            }
                            StorageHelper.guardarPlan(QuizActivity.this, plan);
                        }
                    }
                } catch (Exception ex) {
                    StudyPlan plan = StorageHelper.cargarPlan(QuizActivity.this, planId);
                    if (plan != null) {
                        for (int i = 0; i < plan.quizzes.size(); i++) {
                            if (plan.quizzes.get(i).id.equals(currentQuiz.id)) {
                                plan.quizzes.set(i, currentQuiz);
                                break;
                            }
                        }
                        StorageHelper.guardarPlan(QuizActivity.this, plan);
                    }
                }

                // actualizar UI inmediatamente
                refreshProgressUI();
                showQuestion();
            }
        });
    }

    private void showResultsDialog() {
        if (currentQuiz == null) {
            finish();
            return;
        }
        int total = (questionList == null) ? 0 : questionList.size();
        int correct = (currentQuiz.progreso == null) ? 0 : currentQuiz.progreso.preguntasCorrectas;
        int incorrect = (currentQuiz.progreso == null) ? 0 : currentQuiz.progreso.preguntasIncorrectas;
        int percent = 0;
        if (total > 0) percent = (int) ((correct * 100.0f) / total);
        if (percent > 100) percent = 100;

        // Save final score (nota) into the quiz progreso so it can be shown in lists
        if (currentQuiz.progreso != null) {
            currentQuiz.progreso.nota = percent;
        }
        // persist the plan with updated quiz progreso
        StudyPlan plan = StorageHelper.cargarPlan(this, planId);
        if (plan != null && plan.quizzes != null) {
            for (int i = 0; i < plan.quizzes.size(); i++) {
                Quiz pq = plan.quizzes.get(i);
                if (pq != null && pq.id != null && pq.id.equals(currentQuiz.id)) {
                    plan.quizzes.set(i, currentQuiz);
                    break;
                }
            }
            StorageHelper.guardarPlan(this, plan);
        }

        // Launch a dedicated results activity instead of a dialog to improve UX
        android.content.Intent intent = new android.content.Intent(this, QuizResultsActivity.class);
        intent.putExtra(QuizResultsActivity.EXTRA_CORRECT, correct);
        intent.putExtra(QuizResultsActivity.EXTRA_INCORRECT, incorrect);
        intent.putExtra(QuizResultsActivity.EXTRA_PERCENT, percent);
        startActivity(intent);
        // remove transition animation when opening results
        try {
            overridePendingTransition(0, 0);
        } catch (Exception ex) {
            // ignore
        }
        finish();
    }

    private void refreshProgressUI() {
        if (progressContainer == null || progressFill == null || currentQuiz == null) return;
        int total = (questionList == null) ? 0 : questionList.size();
        int answered = (currentQuiz.progreso == null) ? current : currentQuiz.progreso.preguntasRespondidas;
        int percent = 0;
        if (total > 0) percent = (int) ((answered * 100.0f) / total);
        if (percent > 100) percent = 100;

        // Calcular ancho objetivo en píxeles y animar el ancho del progressFill
        int containerWidth = progressContainer.getWidth();
        if (containerWidth <= 0) {
            // Si no está medido aún, postear para calcular después
            progressContainer.post(() -> refreshProgressUI());
            return;
        }

        int targetWidth = (int) (containerWidth * (percent / 100.0f));
        int currentWidth = progressFill.getWidth();
        if (currentWidth == targetWidth) return;

        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofInt(currentWidth, targetWidth);
        va.setDuration(600);
        va.setInterpolator(new AccelerateDecelerateInterpolator());
        va.addUpdateListener(animation -> {
            int val = (int) animation.getAnimatedValue();
            android.view.ViewGroup.LayoutParams lp = progressFill.getLayoutParams();
            lp.width = val;
            progressFill.setLayoutParams(lp);
        });
        va.start();
    }

    private void setupNavigation() {
        BottomNavigationView nav = findViewById(R.id.study_nav);
        nav.setSelectedItemId(R.id.nav_quiz);

            nav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                Intent intent = null;

                if (id == R.id.nav_details) {
                    intent = new Intent(this, PlanDetailsActivity.class);
                } else if (id == R.id.nav_hot_topics) {
                    intent = new Intent(this, HotTopicsActivity.class);
                } else if (id == R.id.nav_flashcard) {
                    intent = new Intent(this, FlashcardActivity.class);
                } else if (id == R.id.nav_quiz) {
                    // abrir la lista de quizzes (punto central de quizzes)
                    intent = new Intent(this, QuizListActivity.class);
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
