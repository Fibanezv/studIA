package com.app.estudiaapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.content.SharedPreferences;
import android.util.TypedValue;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.content.res.ColorStateList;
import androidx.core.content.ContextCompat;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

/**
 * BaseActivity: when an activity's layout contains a DrawerLayout with id `drawer_layout`
 * and a NavigationView with id `nav_view`, this class wires the header menu button
 * (`header_menu_button`) to open the drawer. If the drawer is not present, it falls back
 * to showing a PopupMenu with the same items so the header button remains useful.
 */
public class BaseActivity extends AppCompatActivity {

    protected DrawerLayout drawerLayout;
    protected NavigationView navigationView;
    private boolean transitionsInitialized = false;

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Try direct id lookup first
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        // If not found (some layouts may not expose the ids directly), search the view tree
        if (drawerLayout == null || navigationView == null) {
            View root = getWindow() != null ? getWindow().getDecorView() : null;
            if (root != null) {
                if (drawerLayout == null) drawerLayout = findDrawerLayoutInView(root);
                if (navigationView == null) navigationView = findNavigationViewInView(root);
            }
        }

        View headerMenu = findViewById(R.id.header_menu_button);
        if (headerMenu != null) {
            try {
                if (headerMenu instanceof android.widget.ImageButton) {
                    android.widget.ImageButton ib = (android.widget.ImageButton) headerMenu;
                    ib.setImageResource(R.drawable.ic_menu_24);
                    try { ib.setImageTintList(ColorStateList.valueOf(resolveThemeColor(R.attr.headerIconColor))); } catch (Exception ignore) {}
                } else if (headerMenu instanceof android.widget.ImageView) {
                    android.widget.ImageView iv = (android.widget.ImageView) headerMenu;
                    iv.setImageResource(R.drawable.ic_menu_24);
                    try { iv.setImageTintList(ColorStateList.valueOf(resolveThemeColor(R.attr.headerIconColor))); } catch (Exception ignore) {}
                }

                if (drawerLayout != null) {
                    headerMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
                } else {
                    headerMenu.setOnClickListener(v -> {
                        PopupMenu popup = new PopupMenu(this, headerMenu);
                        popup.getMenuInflater().inflate(R.menu.drawer_menu, popup.getMenu());
                        popup.setOnMenuItemClickListener(this::handleNavMenuItem);
                        popup.show();
                    });
                }
            } catch (Exception ex) {
                if (drawerLayout != null) headerMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
                else headerMenu.setOnClickListener(v -> {
                    PopupMenu popup = new PopupMenu(this, headerMenu);
                    popup.getMenuInflater().inflate(R.menu.drawer_menu, popup.getMenu());
                    popup.setOnMenuItemClickListener(this::handleNavMenuItem);
                    popup.show();
                });
            }
            // Also try to tint the centered app icon in the header if present
            try {
                View appIcon = findViewById(R.id.app_header_icon);
                if (appIcon instanceof android.widget.ImageView) {
                    ((android.widget.ImageView) appIcon).setImageTintList(ColorStateList.valueOf(resolveThemeColor(R.attr.headerIconColor)));
                }
            } catch (Exception ignore) {}
        }

        if (navigationView != null) {
            // populate header info if available
            View header = navigationView.getHeaderView(0);
            if (header != null) {
                TextView userName = header.findViewById(R.id.drawer_user_name);
                TextView userType = header.findViewById(R.id.drawer_user_type);
                android.widget.ImageView drawerImg = header.findViewById(R.id.drawer_profile_img);
                try {
                    SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
                    String display = prefs.getString("displayName", "Usuario Demo");
                    String mem = prefs.getString("membership_status", "Free");
                    String profileIcon = prefs.getString("profile_icon", "ic_profile_1");
                    if (userName != null) userName.setText(display != null && !display.isEmpty() ? display : "Usuario Demo");
                    if (userType != null) userType.setText(mem != null ? mem : "Free");
                    if (drawerImg != null) {
                        try {
                            int resid = getResources().getIdentifier(profileIcon, "drawable", getPackageName());
                            if (resid == 0) resid = R.drawable.ic_profile_1;
                            drawerImg.setImageResource(resid);
                        } catch (Exception ignore) { try { drawerImg.setImageResource(R.drawable.ic_profile_1); } catch (Exception e) {}}
                    }
                } catch (Exception ignore) {
                    if (userName != null) userName.setText("Usuario Demo");
                    if (userType != null) userType.setText("Free");
                }

                // premium promo included in header white strip: wire click to open MembershipActivity
                View premiumCardHeader = header.findViewById(R.id.premium_card);
                if (premiumCardHeader != null) {
                    premiumCardHeader.setOnClickListener(v -> {
                        try { startActivity(new Intent(BaseActivity.this, MembershipActivity.class)); } catch (Exception ignore) {}
                    });
                }

                // create plan card: open UploadActivity when clicked
                View createPlanCard = header.findViewById(R.id.create_plan_card);
                if (createPlanCard != null) {
                    createPlanCard.setOnClickListener(v -> {
                        try { startActivity(new Intent(BaseActivity.this, UploadActivity.class)); } catch (Exception ignore) {}
                    });
                }
            }

            // Set navigation view background and menu text/icon colors based on theme attributes
            try {
                int navBg = resolveThemeColor(R.attr.navBackgroundColor);
                int navText = resolveThemeColor(R.attr.navTextColor);
                int navIcon = resolveThemeColor(R.attr.navIconColor);
                navigationView.setBackgroundColor(navBg);
                navigationView.setItemTextColor(ColorStateList.valueOf(navText));
                navigationView.setItemIconTintList(ColorStateList.valueOf(navIcon));
            } catch (Exception ignore) {}

            // If the header contains the premium promo, start the shimmer animation
            try {
                if (header != null) {
                    View shimmer = header.findViewById(R.id.premium_shimmer);
                    View premiumCard = header.findViewById(R.id.premium_card);
                    if (shimmer != null && premiumCard != null) {
                        premiumCard.post(() -> {
                            int cardW = premiumCard.getWidth();
                            int shimmerW = shimmer.getWidth();
                            if (cardW <= 0) cardW = premiumCard.getMeasuredWidth();
                            if (shimmerW <= 0) shimmerW = 64;
                            float start = -shimmerW - 20f;
                            float end = cardW + shimmerW + 20f;
                            android.animation.ObjectAnimator anim = android.animation.ObjectAnimator.ofFloat(shimmer, "translationX", start, end);
                            anim.setDuration(1400);
                            anim.setRepeatMode(android.animation.ValueAnimator.RESTART);
                            anim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                            anim.setInterpolator(new android.view.animation.LinearInterpolator());
                            anim.start();
                        });
                    }
                }
            } catch (Exception ignore) {}

            navigationView.setNavigationItemSelectedListener(item -> {
                boolean handled = handleNavMenuItem(item);
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                return handled;
            });
        }

        // If a theme change just happened, play a fade animation so the transition feels smooth
        try {
            SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
            boolean themeChanged = prefs.getBoolean("theme_just_changed", false);
            if (themeChanged) {
                prefs.edit().putBoolean("theme_just_changed", false).apply();
                final View decor = getWindow().getDecorView();
                decor.setAlpha(0f);
                decor.animate().alpha(1f).setDuration(350).start();
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Set default window transitions once per activity instance
        initDefaultTransitionsIfNeeded();
    }

    private void initDefaultTransitionsIfNeeded() {
        if (transitionsInitialized) return;
        try {
            boolean reduceMotion = !android.animation.ValueAnimator.areAnimatorsEnabled();
            if (!reduceMotion) {
                // Use a gentle fade-through-like effect for default enter/return
                com.google.android.material.transition.platform.MaterialFadeThrough fadeThrough =
                        new com.google.android.material.transition.platform.MaterialFadeThrough();
                fadeThrough.setDuration(220);
                getWindow().setEnterTransition(fadeThrough);
                getWindow().setReturnTransition(fadeThrough);
            }
            transitionsInitialized = true;
        } catch (Exception ignore) {}
    }

    protected void startActivityFadeThrough(Intent intent) {
        try {
            startActivity(intent);
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        } catch (Exception e) {
            startActivity(intent);
        }
    }

    protected void startActivitySharedAxisX(Intent intent, boolean forward) {
        try {
            startActivity(intent);
            if (forward) {
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            } else {
                // Could add left-in/right-out for back navigation if needed
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            }
        } catch (Exception e) {
            startActivity(intent);
        }
    }

    protected void startActivityWithContainerTransform(View sharedView, Intent intent, String transitionName) {
        try {
            androidx.core.app.ActivityOptionsCompat options =
                    androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(this, sharedView, transitionName);
            androidx.core.content.ContextCompat.startActivity(this, intent, options.toBundle());
        } catch (Exception e) {
            startActivityFadeThrough(intent);
        }
    }

    protected boolean handleNavMenuItem(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_drawer_plans) {
            // navigate to MainActivity if not already there
            if (!(this instanceof MainActivity)) {
                startActivityFadeThrough(new Intent(this, MainActivity.class));
            }
            return true;
        } else if (id == R.id.nav_drawer_user) {
            if (!(this instanceof UserActivity)) {
                startActivityFadeThrough(new Intent(this, UserActivity.class));
            }
            return true;
        } else if (id == R.id.nav_drawer_library) {
            if (!(this instanceof BibliotecaActivity)) {
                startActivityFadeThrough(new Intent(this, BibliotecaActivity.class));
            }
            return true;
        } else if (id == R.id.nav_drawer_info) {
            if (!(this instanceof InfoActivity)) {
                startActivityFadeThrough(new Intent(this, InfoActivity.class));
            }
            return true;
        
        } else if (id == R.id.nav_drawer_logout) {
            // Centralized logout behavior for all activities
            AuthManager.logoutAndGoToSignIn(this);
            return true;
        }
        return false;
    }

    // walk view hierarchy to find DrawerLayout if id lookup fails
    private DrawerLayout findDrawerLayoutInView(View root) {
        if (root == null) return null;
        if (root instanceof DrawerLayout) return (DrawerLayout) root;
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                DrawerLayout dl = findDrawerLayoutInView(vg.getChildAt(i));
                if (dl != null) return dl;
            }
        }
        return null;
    }

    private NavigationView findNavigationViewInView(View root) {
        if (root == null) return null;
        if (root instanceof NavigationView) return (NavigationView) root;
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                NavigationView nv = findNavigationViewInView(vg.getChildAt(i));
                if (nv != null) return nv;
            }
        }
        return null;
    }

    // Resolve a color value from the current theme attributes.
    private int resolveThemeColor(int attrRes) {
        try {
            TypedValue tv = new TypedValue();
            boolean found = getTheme().resolveAttribute(attrRes, tv, true);
            if (!found) return ContextCompat.getColor(this, R.color.navi_700_theme);
            if (tv.resourceId != 0) return ContextCompat.getColor(this, tv.resourceId);
            return tv.data;
        } catch (Exception e) {
            try { return ContextCompat.getColor(this, R.color.navi_700_theme); } catch (Exception ex) { return 0xFF000000; }
        }
    }
}
