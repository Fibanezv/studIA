package com.app.estudiaapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import com.app.estudiaapp.utils.ErrorDialogHelper;
import com.app.estudiaapp.utils.ErrorHandler;

public class SignUpActivity extends AppCompatActivity {

    EditText userNameInput, emailInput, passwordInput;
    Button registerBtn;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private static final String TAG = "SignUpActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        emailInput = findViewById(R.id.email_input);
        passwordInput = findViewById(R.id.password_input);
        userNameInput = findViewById(R.id.username_input);
        registerBtn = findViewById(R.id.register_button);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        registerBtn.setOnClickListener(v -> registerUser());
    }

    private void registerUser() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        String displayName = userNameInput.getText().toString().trim();

        // Validación básica
        if (!email.contains("@")) {
            ErrorDialogHelper.showErrorDialog(this, com.app.estudiaapp.utils.GeneralErrorType.INVALID_EMAIL, null);
            return;
        }
        
        if (password.length() < 6) {
            ErrorDialogHelper.showErrorDialog(this, com.app.estudiaapp.utils.GeneralErrorType.WEAK_PASSWORD, null);
            return;
        }

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            writeDefaultUserDoc(user.getUid(), user.getEmail(), displayName);
                            // Migrate any local plans to Firestore in background
                            try {
                                new UserRepository(SignUpActivity.this).migrateLocalPlansIfNeeded(user.getUid());
                            } catch (Exception ignored) {
                                Log.w(TAG, "Local migration failed", ignored);
                            }
                        }
                        // Proceed to main
                        startActivity(new Intent(this, MainActivity.class));
                        finish();
                    } else {
                        Exception exception = task.getException();
                        ErrorHandler.logError(exception, "SignUpActivity.registerUser");
                        ErrorDialogHelper.showErrorDialog(
                            SignUpActivity.this, 
                            exception, 
                            () -> registerUser()  // Retry callback
                        );
                    }
                });
    }

    private void writeDefaultUserDoc(String uid, String email, String displayName) {
        Map<String, Object> userDoc = new HashMap<>();
        userDoc.put("uid", uid);
        userDoc.put("email", email);
        userDoc.put("displayName", displayName != null && !displayName.isEmpty() ? displayName : "Usuario");
        // assign a random built-in profile icon name so photoUrl stores the ic_profile resource name
        try {
            int idx = new java.util.Random().nextInt(5) + 1;
            userDoc.put("photoUrl", "ic_profile_" + idx);
        } catch (Exception ignore) {
            userDoc.put("photoUrl", null);
        }
        userDoc.put("provider", "password");
        userDoc.put("emailVerified", false);
        userDoc.put("createdAt", FieldValue.serverTimestamp());
        userDoc.put("lastLoginAt", FieldValue.serverTimestamp());
        userDoc.put("language", "es");
        userDoc.put("level", "A1");

        Map<String, Object> membership = new HashMap<>();
        membership.put("status", "free");
        membership.put("planId", null);
        membership.put("startAt", null);
        membership.put("expiresAt", null);
        userDoc.put("membership", membership);

        Map<String, Object> preferences = new HashMap<>();
        preferences.put("quizLimitDefault", 10);
        preferences.put("flashcardLimitDefault", 20);
        preferences.put("hotTopicLimitDefault", 5);
        preferences.put("notificationsEnabled", true);
        userDoc.put("preferences", preferences);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalFlashcards", 0);
        stats.put("studiedFlashcards", 0);
        stats.put("totalQuizzes", 0);
        stats.put("completedQuizzes", 0);
        stats.put("totalQuestionsAnswered", 0);
        stats.put("correctAnswers", 0);
        userDoc.put("stats", stats);

        userDoc.put("migratedFromLocal", false);

        db.collection("users").document(uid).set(userDoc)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Default user doc written"))
                .addOnFailureListener(e -> Log.w(TAG, "Error writing user doc", e));
    }
}
