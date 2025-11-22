package com.app.estudiaapp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

 
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.estudiaapp.adapters.HotTopicsAdapter;
import com.app.estudiaapp.helper.StorageHelper;
import com.app.estudiaapp.models.HotTopic;
import com.app.estudiaapp.models.StudyPlan;
import com.app.estudiaapp.utils.Constants;

import java.util.ArrayList;

public class HotTopicsActivity extends BaseActivity {

    private String planId;
    private ArrayList<HotTopic> topics = new ArrayList<>();

    public static void start(Context ctx, String planId) {
        Intent i = new Intent(ctx, HotTopicsActivity.class);
        i.putExtra(Constants.EXTRA_PLAN_ID, planId);
        ctx.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hot_topics);

        planId = getIntent().getStringExtra(Constants.EXTRA_PLAN_ID);
        if (planId == null) {
            Toast.makeText(this, "Error: plan_id no recibido", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        StudyPlan plan = StorageHelper.cargarPlan(this, planId);
        if (plan == null) {
            Toast.makeText(this, "No se pudo cargar el plan", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (plan.hotTopics != null) topics.addAll(plan.hotTopics);

        RecyclerView rv = findViewById(R.id.recycler_hot_topics);
        rv.setLayoutManager(new LinearLayoutManager(this));
        HotTopicsAdapter adapter = new HotTopicsAdapter(topics);
        rv.setAdapter(adapter);

        setupNavigation();
    }

    private void setupNavigation() {
    com.google.android.material.bottomnavigation.BottomNavigationView nav = findViewById(R.id.study_nav);
    // Mark Hot Topics as selected in the bottom navigation so the correct item is highlighted
    nav.setSelectedItemId(R.id.nav_hot_topics);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Intent intent = null;
            if (id == R.id.nav_details) {
                intent = new Intent(this, PlanDetailsActivity.class);
            } else if (id == R.id.nav_quiz) {
                intent = new Intent(this, QuizListActivity.class);
            } else if (id == R.id.nav_flashcard) {
                intent = new Intent(this, FlashcardActivity.class);
            } else if (id == R.id.nav_hot_topics) {
                // already here
                return true;
            }

            if (intent != null) {
                intent.putExtra(Constants.EXTRA_PLAN_ID, planId);
                startActivity(intent);
                overridePendingTransition(0,0);
                return true;
            }
            return false;
        });
    }
    
}
