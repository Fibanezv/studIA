package com.app.estudiaapp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;



import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.estudiaapp.adapters.QuizzesAdapter;
import com.app.estudiaapp.helper.StorageHelper;
import com.app.estudiaapp.models.StudyPlan;
import com.app.estudiaapp.models.Quiz;
import com.app.estudiaapp.utils.Constants;
import java.util.ArrayList;

public class QuizListActivity extends BaseActivity {

    private String planIdField;
    private com.app.estudiaapp.adapters.QuizzesAdapter adapter;
    private java.util.ArrayList<com.app.estudiaapp.models.Quiz> quizzesList;

    public static void start(Context context, String planId) {
        Intent intent = new Intent(context, QuizListActivity.class);
        intent.putExtra(Constants.EXTRA_PLAN_ID, planId);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String planId = getIntent().getStringExtra(Constants.EXTRA_PLAN_ID);
        if (planId == null) {
            Toast.makeText(this, "Error: plan_id no recibido", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        StudyPlan plan = StorageHelper.cargarPlan(this, planId);
        if (plan == null || plan.quizzes == null || plan.quizzes.isEmpty()) {
            Toast.makeText(this, "No hay quizzes disponibles.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }


        // Mostrar lista de quizzes con RecyclerView (misma UX que QuizActivity)
        setContentView(R.layout.activity_quiz_list);

        // setup RecyclerView
        androidx.recyclerview.widget.RecyclerView recyclerView = findViewById(R.id.recycler_quizzes);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // guardar planId y preparar lista/adaptador para poder refrescar en onResume
        this.planIdField = planId;
        quizzesList = new java.util.ArrayList<>();
        quizzesList.addAll(plan.quizzes);

        adapter = new com.app.estudiaapp.adapters.QuizzesAdapter(quizzesList, planId, quiz -> {
            // Abrir QuizActivity con planId + quiz.id
            QuizActivity.start(QuizListActivity.this, planId, quiz.id);
        });

        recyclerView.setAdapter(adapter);

        // setup bottom navigation (marcar Quizzes como seleccionado y abrir pantallas desde aquí)
        com.google.android.material.bottomnavigation.BottomNavigationView nav = findViewById(R.id.study_nav);
        nav.setSelectedItemId(R.id.nav_quiz);
            nav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                android.content.Intent intent = null;
                if (id == R.id.nav_details) {
                    intent = new android.content.Intent(this, PlanDetailsActivity.class);
                } else if (id == R.id.nav_hot_topics) {
                    intent = new android.content.Intent(this, HotTopicsActivity.class);
                } else if (id == R.id.nav_flashcard) {
                    intent = new android.content.Intent(this, FlashcardActivity.class);
                } else if (id == R.id.nav_quiz) {
                    // already here
                    return true;
                }

                if (intent != null) {
                    intent.putExtra(com.app.estudiaapp.utils.Constants.EXTRA_PLAN_ID, planId);
                    startActivity(intent);
                    overridePendingTransition(0,0);
                    return true;
                }
                return false;
            });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (this.planIdField == null) return;
        StudyPlan plan = StorageHelper.cargarPlan(this, this.planIdField);
        if (plan == null) return;
        // actualizar lista y notificar adapter
        quizzesList.clear();
        if (plan.quizzes != null) quizzesList.addAll(plan.quizzes);
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    
}
