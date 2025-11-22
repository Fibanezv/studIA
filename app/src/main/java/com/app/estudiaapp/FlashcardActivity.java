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
import com.google.android.material.bottomnavigation.BottomNavigationView;

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
            // try to pick the first available plan
            java.util.ArrayList<com.app.estudiaapp.models.StudyPlan> all = StorageHelper.listarPlanes(this);
            if (all != null && !all.isEmpty()) {
                plan = all.get(0);
                planId = plan.id;
            }
        }

        if (plan == null) {
            Toast.makeText(this, "No hay planes disponibles para mostrar flashcards.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Ensure flashcards exist
        if (plan.flashcards == null || plan.flashcards.isEmpty()) {
            Toast.makeText(this, "Este plan no tiene flashcards.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Setup RecyclerView listing all flashcards for this plan
        androidx.recyclerview.widget.RecyclerView rv = findViewById(R.id.rv_flashcards_list);
        rv.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        adapter = new com.app.estudiaapp.adapters.FlashcardsAdapter(plan.flashcards, planId);
        rv.setAdapter(adapter);

        setupNavigation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh the plan and adapter so completed state (background) updates immediately
        if (planId != null && adapter != null) {
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
