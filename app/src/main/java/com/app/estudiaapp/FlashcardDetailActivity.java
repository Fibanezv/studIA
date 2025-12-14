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
    private boolean randomMode = false;
    private boolean favoritesMode = false;

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
        index = getIntent().getIntExtra(Constants.EXTRA_FLASHCARD_INDEX, 0);
        randomMode = getIntent().getBooleanExtra("random_mode", false);
        favoritesMode = getIntent().getBooleanExtra("favorites_mode", false);

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

        // Setup navigation buttons
        setupNavigationButtons();
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

    private void setupNavigationButtons() {
        updateNavigationButtonsVisibility();
        setupNavigationButtonsListeners();
    }

    private void setupNavigationButtonsListeners() {
        com.google.android.material.floatingactionbutton.FloatingActionButton btnPrevious = findViewById(R.id.btn_flashcard_previous);
        com.google.android.material.floatingactionbutton.FloatingActionButton btnNext = findViewById(R.id.btn_flashcard_next);
        com.google.android.material.floatingactionbutton.FloatingActionButton btnDiscard = findViewById(R.id.btn_flashcard_discard);
        com.google.android.material.floatingactionbutton.FloatingActionButton btnFavorite = findViewById(R.id.btn_flashcard_favorite);
        com.google.android.material.floatingactionbutton.FloatingActionButton btnReload = findViewById(R.id.btn_flashcard_reload);

        // Botón anterior
        if (btnPrevious != null) {
            btnPrevious.setOnClickListener(v -> navigateToPrevious());
        }

        // Botón siguiente
        if (btnNext != null) {
            btnNext.setOnClickListener(v -> navigateToNext());
        }

        // Botón X (quitar de favoritos)
        if (btnDiscard != null) {
            btnDiscard.setOnClickListener(v -> toggleFavorite(false));
        }

        // Botón favorito (añadir a favoritos)
        if (btnFavorite != null) {
            btnFavorite.setOnClickListener(v -> toggleFavorite(true));
        }

        // Botón reload (modo aleatorio)
        if (btnReload != null) {
            btnReload.setOnClickListener(v -> loadRandomFlashcard());
        }
    }

    private void updateNavigationButtonsVisibility() {
        com.google.android.material.floatingactionbutton.FloatingActionButton btnPrevious = findViewById(R.id.btn_flashcard_previous);
        com.google.android.material.floatingactionbutton.FloatingActionButton btnNext = findViewById(R.id.btn_flashcard_next);
        com.google.android.material.floatingactionbutton.FloatingActionButton btnReload = findViewById(R.id.btn_flashcard_reload);
        com.google.android.material.floatingactionbutton.FloatingActionButton btnDiscard = findViewById(R.id.btn_flashcard_discard);
        com.google.android.material.floatingactionbutton.FloatingActionButton btnFavorite = findViewById(R.id.btn_flashcard_favorite);

        if (randomMode) {
            // En modo aleatorio: ocultar anterior/siguiente, mostrar reload
            if (btnPrevious != null) btnPrevious.setVisibility(View.GONE);
            if (btnNext != null) btnNext.setVisibility(View.GONE);
            if (btnReload != null) btnReload.setVisibility(View.VISIBLE);
        } else {
            // Modo normal: mostrar anterior/siguiente según posición, ocultar reload
            if (btnPrevious != null) {
                btnPrevious.setVisibility(index <= 0 ? View.GONE : View.VISIBLE);
            }
            if (btnNext != null) btnNext.setVisibility(View.VISIBLE);
            if (btnReload != null) btnReload.setVisibility(View.GONE);
        }

        // Mostrar botón de favorito o quitar favorito según estado
        boolean isFavorite = flashcard != null && flashcard.favorita;
        if (btnDiscard != null) {
            btnDiscard.setVisibility(isFavorite ? View.VISIBLE : View.GONE);
        }
        if (btnFavorite != null) {
            btnFavorite.setVisibility(isFavorite ? View.GONE : View.VISIBLE);
        }
    }

    private void loadRandomFlashcard() {
        if (plan == null || plan.flashcards == null || plan.flashcards.isEmpty()) {
            Toast.makeText(this, "No hay flashcards disponibles", Toast.LENGTH_SHORT).show();
            return;
        }

        if (plan.flashcards.size() == 1) {
            Toast.makeText(this, "Solo hay una flashcard", Toast.LENGTH_SHORT).show();
            return;
        }

        // Elegir un índice aleatorio diferente al actual
        int newIndex;
        java.util.Random random = new java.util.Random();
        do {
            newIndex = random.nextInt(plan.flashcards.size());
        } while (newIndex == index);

        index = newIndex;
        flashcard = plan.flashcards.get(index);
        updateFlashcardView();
    }

    private void toggleFavorite(boolean addToFavorites) {
        if (flashcard == null || plan == null) return;

        flashcard.favorita = addToFavorites;
        
        // Persistir cambio local
        StorageHelper.guardarPlan(this, plan);

        // Persistir en Firestore si el usuario está autenticado
        String uid = null;
        try { uid = getSharedPreferences("auth", MODE_PRIVATE).getString("uid", null); } catch (Exception ignored) {}
        if (uid != null) {
            new UserRepository(this).markFlashcardFavorite(uid, planId, index, addToFavorites, a -> {
                // no-op
            }, e -> {
                // fallback: ya guardado local
            });
        }
        
        // Actualizar visibilidad de botones
        updateNavigationButtonsVisibility();
        
        String message = addToFavorites ? "❤️ Añadida a favoritas" : "Quitada de favoritas";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

        // Si estamos en modo favoritas y quitamos de favoritas, navegar a la siguiente o volver
        if (favoritesMode && !addToFavorites) {
            navigateToNextInFavoritesMode();
        }
    }

    private void navigateToNextInFavoritesMode() {
        if (plan == null || plan.flashcards == null) return;

        // Buscar la siguiente favorita
        for (int i = index + 1; i < plan.flashcards.size(); i++) {
            if (plan.flashcards.get(i).favorita) {
                index = i;
                flashcard = plan.flashcards.get(index);
                updateFlashcardView();
                return;
            }
        }

        // No hay más favoritas, volver a la lista
        Toast.makeText(this, "No hay más favoritas", Toast.LENGTH_SHORT).show();
        returnToFlashcardList();
    }

    private void returnToFlashcardList() {
        Intent intent = new Intent(this, FlashcardActivity.class);
        intent.putExtra(Constants.EXTRA_PLAN_ID, planId);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }

    private void navigateToPrevious() {
        if (plan == null || plan.flashcards == null) return;

        if (favoritesMode) {
            // En modo favoritas, buscar la anterior favorita
            for (int i = index - 1; i >= 0; i--) {
                if (plan.flashcards.get(i).favorita) {
                    index = i;
                    flashcard = plan.flashcards.get(index);
                    updateFlashcardView();
                    return;
                }
            }
            Toast.makeText(this, "No hay favoritas anteriores", Toast.LENGTH_SHORT).show();
        } else if (index > 0) {
            index--;
            flashcard = plan.flashcards.get(index);
            updateFlashcardView();
        }
    }

    private void navigateToNext() {
        if (plan == null || plan.flashcards == null) return;

        if (favoritesMode) {
            // En modo favoritas, buscar la siguiente favorita
            for (int i = index + 1; i < plan.flashcards.size(); i++) {
                if (plan.flashcards.get(i).favorita) {
                    index = i;
                    flashcard = plan.flashcards.get(index);
                    updateFlashcardView();
                    return;
                }
            }
            // No hay más favoritas, volver a la lista
            Toast.makeText(this, "Has terminado de revisar las favoritas", Toast.LENGTH_SHORT).show();
            returnToFlashcardList();
        } else if (index < plan.flashcards.size() - 1) {
            // Modo normal, avanzar a la siguiente
            index++;
            flashcard = plan.flashcards.get(index);
            updateFlashcardView();
        } else {
            // Última flashcard, volver a la lista
            Toast.makeText(this, "Has terminado de revisar todas las flashcards", Toast.LENGTH_SHORT).show();
            returnToFlashcardList();
        }
    }

    private void updateFlashcardView() {
        // Reset to front side
        if (backCard != null) backCard.setVisibility(View.GONE);
        if (frontCard != null) frontCard.setVisibility(View.VISIBLE);
        showingFront = true;
        if (cardContainer != null) cardContainer.setRotationY(0f);

        // Update text
        if (frontText != null) frontText.setText(flashcard.pregunta != null ? flashcard.pregunta : "");
        if (backText != null) backText.setText(flashcard.respuesta != null ? flashcard.respuesta : "");

        // Update button visibility
        updateNavigationButtonsVisibility();
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
