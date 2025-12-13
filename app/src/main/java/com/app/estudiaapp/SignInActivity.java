package com.app.estudiaapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.app.estudiaapp.utils.ErrorDialogHelper;
import com.app.estudiaapp.utils.ErrorHandler;
import java.util.Map;
public class SignInActivity extends AppCompatActivity {

    EditText emailInput, passwordInput;
    Button loginBtn, registerRedirect;
    Button googleSignInBtn;

    private static final String TAG = "SignInActivity";
    private static final int RC_SIGN_IN = 9001;
    private FirebaseAuth auth;
    private GoogleSignInClient googleSignInClient;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signin);

        emailInput = findViewById(R.id.email_input);
        passwordInput = findViewById(R.id.password_input);
        loginBtn = findViewById(R.id.login_button);
        registerRedirect = findViewById(R.id.register_redirect);
    googleSignInBtn = findViewById(R.id.google_signin_button);

    auth = FirebaseAuth.getInstance();
    db = FirebaseFirestore.getInstance();

    GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(getString(R.string.default_web_client_id))
        .requestEmail()
        .build();
    googleSignInClient = GoogleSignIn.getClient(this, gso);

        loginBtn.setOnClickListener(v -> signInWithEmail());

        googleSignInBtn.setOnClickListener(v -> signInWithGoogle());

        registerRedirect.setOnClickListener(v -> startActivity(new Intent(this, SignUpActivity.class)));
    }

    private void signInWithEmail() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        
        // Validación básica
        if (!email.contains("@")) {
            ErrorDialogHelper.showErrorDialog(this, com.app.estudiaapp.utils.GeneralErrorType.INVALID_EMAIL, null);
            return;
        }
        
        if (password.length() < 4) {
            ErrorDialogHelper.showErrorDialog(this, com.app.estudiaapp.utils.GeneralErrorType.WEAK_PASSWORD, null);
            return;
        }
        
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        // update lastLoginAt in Firestore
                        if (user != null) {
                            final String uid = user.getUid();
                            final String userEmail = user.getEmail();
                            db.collection("users").document(uid)
                                    .get()
                                    .addOnSuccessListener(doc -> {
                                        if (!doc.exists()) {
                                            writeDefaultUserDoc(uid, userEmail, "Usuario");
                                        } else {
                                            db.collection("users").document(uid)
                                                    .update("lastLoginAt", FieldValue.serverTimestamp())
                                                    .addOnCompleteListener(u -> Log.d(TAG, "lastLoginAt updated"));
                                        }
                                    });
                            SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
                            prefs.edit().putString("email", userEmail).putString("uid", user.getUid()).apply();

                            // Migrate any local plans to Firestore in background (idempotent)
                            try {
                                new UserRepository(SignInActivity.this).migrateLocalPlansIfNeeded(uid);
                            } catch (Exception ignored) {
                                Log.w(TAG, "Local migration failed", ignored);
                            }
                        }
                        startActivity(new Intent(this, MainActivity.class));
                        finish();
                    } else {
                        Exception exception = task.getException();
                        ErrorHandler.logError(exception, "SignInActivity.signInWithEmail");
                        ErrorDialogHelper.showErrorDialog(
                            SignInActivity.this, 
                            exception, 
                            () -> signInWithEmail()  // Retry callback
                        );
                    }
                });
    }

    private void signInWithGoogle() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                // Google Sign In was successful, authenticate with Firebase
                AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
                auth.signInWithCredential(credential).addOnCompleteListener(this, task1 -> {
                    if (task1.isSuccessful()) {
                            FirebaseUser user = auth.getCurrentUser();
                            if (user != null) {
                                db.collection("users").document(user.getUid())
                                        .get()
                                        .addOnSuccessListener(doc -> {
                                            if (!doc.exists()) {
                                                writeDefaultUserDoc(user.getUid(), user.getEmail(), user.getDisplayName() != null ? user.getDisplayName() : "Usuario");
                                            } else {
                                                db.collection("users").document(user.getUid())
                                                        .update("lastLoginAt", FieldValue.serverTimestamp())
                                                        .addOnCompleteListener(u -> Log.d(TAG, "lastLoginAt updated"));
                                            }
                                        });
                                SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
                                prefs.edit().putString("email", user.getEmail()).putString("uid", user.getUid()).apply();

                                // Start migration of local plans to Firestore asynchronously
                                try {
                                    new UserRepository(SignInActivity.this).migrateLocalPlansIfNeeded(user.getUid());
                                } catch (Exception ignored) {
                                    Log.w(TAG, "Local migration failed", ignored);
                                }
                            }
                        startActivity(new Intent(this, MainActivity.class));
                        finish();
                    } else {
                        Exception exception = task1.getException();
                        ErrorHandler.logError(exception, "SignInActivity.signInWithGoogle");
                        ErrorDialogHelper.showErrorDialog(
                            SignInActivity.this, 
                            exception, 
                            () -> signInWithGoogle()  // Retry callback
                        );
                    }
                });
            } catch (ApiException e) {
                ErrorHandler.logError(e, "SignInActivity.googleSignInResult");
                ErrorDialogHelper.showErrorDialog(
                    this, 
                    e, 
                    () -> signInWithGoogle()  // Retry callback
                );
            }
        }
    }

    private void writeDefaultUserDoc(String uid, String email, String displayName) {
        Map<String, Object> userDoc = new java.util.HashMap<>();
        userDoc.put("uid", uid);
        userDoc.put("email", email);
        userDoc.put("displayName", displayName != null && !displayName.isEmpty() ? displayName : "Usuario");
        userDoc.put("photoUrl", null);
        userDoc.put("provider", "password");
        userDoc.put("emailVerified", false);
        userDoc.put("createdAt", FieldValue.serverTimestamp());
        userDoc.put("lastLoginAt", FieldValue.serverTimestamp());
        userDoc.put("language", "es");
        userDoc.put("level", "A1");

        Map<String, Object> membership = new java.util.HashMap<>();
        membership.put("status", "free");
        membership.put("planId", null);
        membership.put("startAt", null);
        membership.put("expiresAt", null);
        userDoc.put("membership", membership);

        Map<String, Object> preferences = new java.util.HashMap<>();
        preferences.put("quizLimitDefault", 10);
        preferences.put("flashcardLimitDefault", 20);
        preferences.put("hotTopicLimitDefault", 5);
        preferences.put("notificationsEnabled", true);
        userDoc.put("preferences", preferences);

        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("totalFlashcards", 0);
        stats.put("studiedFlashcards", 0);
        stats.put("totalQuizzes", 0);
        stats.put("completedQuizzes", 0);
        stats.put("totalQuestionsAnswered", 0);
        stats.put("correctAnswers", 0);
        userDoc.put("stats", stats);

        userDoc.put("migratedFromLocal", false);

        db.collection("users").document(uid).set(userDoc)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Default user doc written (signin)"))
                .addOnFailureListener(e -> Log.w(TAG, "Error writing user doc", e));
    }
}
