package com.app.estudiaapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.navigation.NavigationView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.estudiaapp.adapters.PlansAdapter;
import com.app.estudiaapp.helper.StorageHelper;
import com.app.estudiaapp.models.StudyPlan;
import com.app.estudiaapp.utils.ErrorDialogHelper;
import com.app.estudiaapp.utils.ErrorHandler;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import android.content.SharedPreferences;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.gson.Gson;

import java.util.ArrayList;

public class MainActivity extends BaseActivity implements PlansAdapter.OnPlanClickListener {

    private BottomNavigationView bottomNav;
    // upload button replaced by create-plan include
    private RecyclerView recyclerView;
    private PlansAdapter adapter;
    private ArrayList<StudyPlan> planList;
    // Drawer is handled by BaseActivity when present in the layout
    
    // NEW: Firestore listener for real-time plan updates
    private ListenerRegistration plansListener;
    private String currentUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

        // Sample-plan creation disabled - do not auto-create example plans on startup.

    recyclerView = findViewById(R.id.recycler_plans);
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        // No drawer here; keep original main activity behavior

        // Acción al pulsar la tarjeta "Crear Plan de Estudio" (incluida)
        View createPlanCard = findViewById(R.id.create_plan_card);
        if (createPlanCard != null) {
            createPlanCard.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, UploadActivity.class)));
        }

        // Header menu button: abrir drawer si existe
        View headerMenu = findViewById(R.id.header_menu_button);
        if (headerMenu != null) {
            headerMenu.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.START);
            });
        }

        // Navigation drawer item selection
        if (navigationView != null) {
            // Optional: set user info in header
            View header = navigationView.getHeaderView(0);
            if (header != null) {
                TextView userName = header.findViewById(R.id.drawer_user_name);
                TextView userType = header.findViewById(R.id.drawer_user_type);
                FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
                if (current != null) {
                    // Prefer reading displayName/membership from users/{uid} (firestore)
                    try {
                        new UserRepository(MainActivity.this).fetchUserDoc(current.getUid(), doc -> {
                            if (doc != null && doc.exists()) {
                                String display = doc.contains("displayName") ? doc.getString("displayName") : (current.getDisplayName() != null ? current.getDisplayName() : current.getEmail());
                                String membership = "Free";
                                if (doc.contains("membership")) {
                                    Object mem = doc.get("membership");
                                    if (mem instanceof java.util.Map) {
                                        Object status = ((java.util.Map) mem).get("status");
                                        if (status != null) membership = status.toString();
                                    }
                                }
                                if (userName != null) userName.setText(display != null ? display : "Usuario");
                                if (userType != null) userType.setText(membership != null ? membership : "Free");
                            } else {
                                if (userName != null) userName.setText(current.getDisplayName() != null ? current.getDisplayName() : current.getEmail());
                                if (userType != null) userType.setText("Free");
                            }
                        }, e -> {
                            // on failure fallback - log but don't show dialog, just use default values
                            ErrorHandler.logError(e, "MainActivity.drawerUserInfo");
                            if (userName != null) userName.setText(current.getDisplayName() != null ? current.getDisplayName() : current.getEmail());
                            if (userType != null) userType.setText("Free");
                        });
                    } catch (Exception ex) {
                        if (userName != null) userName.setText(current.getDisplayName() != null ? current.getDisplayName() : current.getEmail());
                        if (userType != null) userType.setText("Free");
                    }
                } else {
                    if (userName != null) userName.setText("Invitado");
                    if (userType != null) userType.setText("");
                }
            }

            navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
                @Override
                public boolean onNavigationItemSelected(MenuItem menuItem) {
                    int id = menuItem.getItemId();
                    if (id == R.id.nav_drawer_plans) {
                        // Already in MainActivity; just close drawer
                        drawerLayout.closeDrawer(GravityCompat.START);
                        return true;
                    } else if (id == R.id.nav_drawer_user) {
                        drawerLayout.closeDrawer(GravityCompat.START);
                        startActivity(new Intent(MainActivity.this, UserActivity.class));
                        return true;
                    } else if (id == R.id.nav_drawer_logout) {
                        // Sign out logic: close drawer, sign out, clear prefs, start SignInActivity as new task
                        drawerLayout.closeDrawer(GravityCompat.START);
                        FirebaseAuth.getInstance().signOut();
                        SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
                        prefs.edit().clear().apply();
                        Intent i = new Intent(MainActivity.this, SignInActivity.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                        // Ensure all existing activities are finished so the app effectively returns to the SignIn screen
                        finishAffinity();
                        return true;
                    }
                    drawerLayout.closeDrawer(GravityCompat.START);
                    return false;
                }
            });
        }


        // Configurar lista de planes
        setupPlanList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Always refresh plan list on resume. If the user is signed-in this will
        // fetch the latest plans from Firestore; otherwise it will load local plans.
        setupPlanList();
    }

    private void setupPlanList() {
        planList = new ArrayList<>();
        // Prefer Firestore plans for signed-in users
        String uid = null;
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
            uid = prefs.getString("uid", null);
        } catch (Exception ignored) {}

        if (uid != null) {
            currentUid = uid;
            // Setup real-time Firestore listener for plan updates
            setupPlansListener(uid);
        } else {
            ArrayList<StudyPlan> lstPlanesPruebas = StorageHelper.listarPlanes(this);
            if (lstPlanesPruebas != null) planList = lstPlanesPruebas;
            adapter = new PlansAdapter(planList, this);
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.setAdapter(adapter);
        }
    }
    
    // NEW: Setup real-time Firestore listener for plan updates
    private void setupPlansListener(String uid) {
        // Remove previous listener if exists
        if (plansListener != null) {
            plansListener.remove();
            plansListener = null;
        }
        
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Gson gson = new Gson();
        
        // Listen to all plans in real-time
        plansListener = db.collection("users").document(uid).collection("plans")
            .addSnapshotListener((querySnapshot, error) -> {
                if (error != null) {
                    ErrorHandler.logError(error, "MainActivity.setupPlansListener");
                    
                    // Show user a dialog with retry option
                    ErrorDialogHelper.showErrorDialog(
                        MainActivity.this,
                        error,
                        () -> setupPlansListener(uid)  // Retry callback
                    );
                    
                    // Fallback to one-time fetch
                    new UserRepository(this).getPlans(uid, plans -> {
                        planList = (plans != null) ? new ArrayList<>(plans) : new ArrayList<>();
                        updateAdapter();
                    }, e -> {
                        ErrorHandler.logError(e, "MainActivity.fallbackPlansLoad");
                        ArrayList<StudyPlan> lstPlanesPruebas = StorageHelper.listarPlanes(this);
                        if (lstPlanesPruebas != null) planList = lstPlanesPruebas;
                        updateAdapter();
                    });
                    return;
                }
                
                if (querySnapshot != null) {
                    planList.clear();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        try {
                            String json = gson.toJson(doc.getData());
                            StudyPlan plan = gson.fromJson(json, StudyPlan.class);
                            if (plan != null) {
                                planList.add(plan);
                                
                                // Log generation progress for debugging
                                if (plan.isGenerating()) {
                                    android.util.Log.d("MainActivity", "Plan " + plan.id + " is generating: " + 
                                        plan.getGenerationPercentage() + "% complete");
                                }
                            }
                        } catch (Exception ex) {
                            ErrorHandler.logError(ex, "MainActivity.planParsing");
                        }
                    }
                    
                    updateAdapter();
                }
            });
    }
    
    // Helper to update or create adapter
    private void updateAdapter() {
        if (adapter == null) {
            adapter = new PlansAdapter(planList, this);
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.setAdapter(adapter);
        } else {
            adapter.notifyDataSetChanged();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove Firestore listener when activity is destroyed
        if (plansListener != null) {
            plansListener.remove();
            plansListener = null;
        }
    }

    // Cuando se hace clic en un plan
    @Override
    public void onPlanClick(StudyPlan plan) {
        // Abrir lista de quizzes del plan
    PlanDetailsActivity.start(this, plan.id);
    }
}
