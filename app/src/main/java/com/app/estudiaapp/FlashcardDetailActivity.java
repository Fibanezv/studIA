package com.app.estudiaapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.app.estudiaapp.helper.StorageHelper;
import com.app.estudiaapp.models.Flashcard;
import com.app.estudiaapp.models.StudyPlan;
import com.app.estudiaapp.utils.Constants;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import android.content.Intent;

public class FlashcardDetailActivity extends BaseActivity {

    private String planId;
    private int index = 0;
    private StudyPlan plan;
    private Flashcard flashcard;

    private View cardContainer;
    private androidx.cardview.widget.CardView frontCard;
    private androidx.cardview.widget.CardView backCard;
    private TextView frontText;
    private TextView backText;
    private boolean showingFront = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flashcard_detail);

        planId = getIntent().getStringExtra(Constants.EXTRA_PLAN_ID);
        index = getIntent().getIntExtra("flashcard_index", 0);

        if (planId == null) {
            finish();
            return;
        }

        // Load plan from Firestore with fallback to local
        String uid = null;
        try { uid = getSharedPreferences("auth", MODE_PRIVATE).getString("uid", null); } catch (Exception ignored) {}
        if (uid != null) {
            new UserRepository(this).getPlan(uid, planId, p -> {
                if (p == null || p.flashcards == null || p.flashcards.size() <= index) {
                    Toast.makeText(this, "Flashcard no encontrada", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                plan = p;
                flashcard = plan.flashcards.get(index);
                onPlanLoaded();
            }, e -> {
                plan = StorageHelper.cargarPlan(this, planId);
                if (plan == null || plan.flashcards == null || plan.flashcards.size() <= index) {
                    Toast.makeText(this, "Flashcard no encontrada", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                flashcard = plan.flashcards.get(index);
                onPlanLoaded();
            });
            return;
        } else {
            plan = StorageHelper.cargarPlan(this, planId);
            if (plan == null || plan.flashcards == null || plan.flashcards.size() <= index) {
                Toast.makeText(this, "Flashcard no encontrada", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            flashcard = plan.flashcards.get(index);
        }

        onPlanLoaded();
    }

    private void onPlanLoaded() {
        cardContainer = findViewById(R.id.detail_card);
        frontCard = findViewById(R.id.detail_front_card);
        backCard = findViewById(R.id.detail_back_card);
        frontText = findViewById(R.id.detail_front_text);
        backText = findViewById(R.id.detail_back_text);

        // improve flip perspective
        if (cardContainer != null) {
            float scale = getResources().getDisplayMetrics().density;
            cardContainer.setCameraDistance(8000 * scale);
        }

        frontText.setText(flashcard.pregunta != null ? flashcard.pregunta : "");
        backText.setText(flashcard.respuesta != null ? flashcard.respuesta : "");

        // Attach click listeners to the visible face(s)
        if (frontCard != null) frontCard.setOnClickListener(v -> flipCard());
        if (backCard != null) backCard.setOnClickListener(v -> flipCard());

        setupNavigation();
    }

    private void flipCard() {
        // simple Y-rotation flip
    ObjectAnimator hide = ObjectAnimator.ofFloat(cardContainer, "rotationY", 0f, 90f);
    hide.setDuration(220);
    ObjectAnimator show = ObjectAnimator.ofFloat(cardContainer, "rotationY", -90f, 0f);
    show.setDuration(220);

        hide.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // swap visibility of the whole cards so backgrounds change as desired
                if (showingFront) {
                    if (frontCard != null) frontCard.setVisibility(View.GONE);
                    if (backCard != null) backCard.setVisibility(View.VISIBLE);
                    // mark completed
                    markFlashcardCompleted();
                } else {
                    if (backCard != null) backCard.setVisibility(View.GONE);
                    if (frontCard != null) frontCard.setVisibility(View.VISIBLE);
                }
                showingFront = !showingFront;
                show.start();
            }
        });
        hide.start();
    }

    private void markFlashcardCompleted() {
        if (flashcard == null || plan == null) return;
        if (!flashcard.completada) {
            flashcard.completada = true;
            if (plan.progreso == null) plan.progreso = new com.app.estudiaapp.models.UserProgress();
            int total = (plan.flashcards == null) ? 0 : plan.flashcards.size();
            plan.progreso.registrarFlashcardEstudiada(total);
            // Persist to Firestore if possible
            String uid = null;
            try { uid = getSharedPreferences("auth", MODE_PRIVATE).getString("uid", null); } catch (Exception ignored) {}
            if (uid != null) {
                new UserRepository(this).markFlashcardCompleted(uid, plan.id, index, a -> {
                    // no-op
                }, e -> {
                    // fallback local save
                    StorageHelper.guardarPlan(FlashcardDetailActivity.this, plan);
                });
            } else {
                StorageHelper.guardarPlan(this, plan);
            }
        }
    }

    private void setupNavigation() {
        BottomNavigationView nav = findViewById(R.id.study_nav);
        if (nav == null) return; // defensive: layout may not include bottom nav in some variants
        nav.setSelectedItemId(R.id.nav_flashcard);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Intent intent = null;
            boolean finishThis = false;
            if (id == R.id.nav_details) {
                intent = new Intent(this, PlanDetailsActivity.class);
            } else if (id == R.id.nav_hot_topics) {
                intent = new Intent(this, HotTopicsActivity.class);
            } else if (id == R.id.nav_quiz) {
                intent = new Intent(this, QuizListActivity.class);
            } else if (id == R.id.nav_flashcard) {
                // If user taps the flashcard icon while in the detail screen,
                // open the main FlashcardActivity and finish this detail activity.
                intent = new Intent(this, FlashcardActivity.class);
                finishThis = true;
            }

            if (intent != null) {
                intent.putExtra(Constants.EXTRA_PLAN_ID, planId);
                startActivity(intent);
                overridePendingTransition(0, 0);
                if (finishThis) finish();
                return true;
            }
            return false;
        });
    }

}
