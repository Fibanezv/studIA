package com.app.estudiaapp;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.estudiaapp.adapters.PlansAdapter;
import com.app.estudiaapp.helper.StorageHelper;
import com.app.estudiaapp.models.StudyPlan;

import java.util.ArrayList;

public class PlansListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private PlansAdapter adapter;
    private ArrayList<StudyPlan> plansList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plans_list);
        setTitle("Mis Planes de Estudio");

        recyclerView = findViewById(R.id.recycler_plans);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        // Try loading plans from Firestore; fallback to local StorageHelper
        String uid = null;
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
            uid = prefs.getString("uid", null);
        } catch (Exception ignored) {}

        if (uid != null) {
            new UserRepository(this).getPlans(uid, plans -> {
                plansList = (plans != null) ? new ArrayList<>(plans) : new ArrayList<>();
                adapter = new PlansAdapter(plansList, plan -> PlanDetailsActivity.start(this, plan.id));
                recyclerView.setAdapter(adapter);
            }, e -> {
                // fallback to local
                plansList = StorageHelper.listarPlanes(this);
                if (plansList == null) plansList = new ArrayList<>();
                adapter = new PlansAdapter(plansList, plan -> PlanDetailsActivity.start(this, plan.id));
                recyclerView.setAdapter(adapter);
            });
        } else {
            plansList = StorageHelper.listarPlanes(this);
            if (plansList == null) plansList = new ArrayList<>();
            adapter = new PlansAdapter(plansList, plan -> PlanDetailsActivity.start(this, plan.id));
            recyclerView.setAdapter(adapter);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload plans from Firestore if possible, otherwise refresh local
        String uid = null;
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
            uid = prefs.getString("uid", null);
        } catch (Exception ignored) {}

        if (uid != null) {
            new UserRepository(this).getPlans(uid, plans -> {
                plansList = (plans != null) ? new ArrayList<>(plans) : new ArrayList<>();
                if (adapter != null) adapter.setPlans(plansList);
            }, e -> {
                ArrayList<StudyPlan> refreshed = StorageHelper.listarPlanes(this);
                if (refreshed == null) refreshed = new ArrayList<>();
                plansList = refreshed;
                if (adapter != null) adapter.setPlans(plansList);
            });
        } else {
            ArrayList<StudyPlan> refreshed = StorageHelper.listarPlanes(this);
            if (refreshed == null) refreshed = new ArrayList<>();
            plansList = refreshed;
            if (adapter != null) adapter.setPlans(plansList);
        }
    }
}
