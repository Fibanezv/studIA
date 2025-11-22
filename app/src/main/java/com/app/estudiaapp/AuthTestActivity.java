package com.app.estudiaapp;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class AuthTestActivity extends AppCompatActivity {

    private static final String TAG = "AuthTestActivity";
    private EditText editEmail, editPassword;
    private Button btnSignIn, btnCreateUser, btnWriteDefault;
    private TextView tvStatus;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth_test);

        editEmail = findViewById(R.id.edit_email);
        editPassword = findViewById(R.id.edit_password);
        btnSignIn = findViewById(R.id.btn_sign_in);
        btnCreateUser = findViewById(R.id.btn_create_user);
        btnWriteDefault = findViewById(R.id.btn_write_default_doc);
        tvStatus = findViewById(R.id.tv_status);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Prefill email as requested
        editEmail.setText("ibanezvarelafran@email.com");

        btnSignIn.setOnClickListener(v -> signIn());
        btnCreateUser.setOnClickListener(v -> createUser());
        btnWriteDefault.setOnClickListener(v -> writeDefaultUserDoc());
    }

    private void updateStatus(String s) {
        tvStatus.setText("Status: " + s);
        Log.d(TAG, s);
    }

    private void signIn() {
        String email = editEmail.getText().toString().trim();
        String pwd = editPassword.getText().toString();
        if (email.isEmpty() || pwd.isEmpty()) {
            Toast.makeText(this, "Introduce email y password", Toast.LENGTH_SHORT).show();
            return;
        }
        updateStatus("Signing in...");
        auth.signInWithEmailAndPassword(email, pwd)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        updateStatus("Signed in: " + user.getUid());
                        Toast.makeText(this, "Sign in OK: " + user.getEmail(), Toast.LENGTH_SHORT).show();
                    } else {
                        updateStatus("Sign in failed");
                        Toast.makeText(this, "Sign in error: " + task.getException(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void createUser() {
        String email = editEmail.getText().toString().trim();
        String pwd = editPassword.getText().toString();
        if (email.isEmpty() || pwd.isEmpty()) {
            Toast.makeText(this, "Introduce email y password", Toast.LENGTH_SHORT).show();
            return;
        }
        updateStatus("Creating user...");
        auth.createUserWithEmailAndPassword(email, pwd)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        updateStatus("User created: " + user.getUid());
                        Toast.makeText(this, "User created: " + user.getEmail(), Toast.LENGTH_SHORT).show();
                    } else {
                        updateStatus("Create user failed");
                        Toast.makeText(this, "Create error: " + task.getException(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void writeDefaultUserDoc() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "You must be signed in first", Toast.LENGTH_SHORT).show();
            return;
        }
        String uid = user.getUid();
        String email = user.getEmail();
        updateStatus("Writing default Firestore doc for uid=" + uid);

        Map<String, Object> userDoc = new HashMap<>();
        userDoc.put("uid", uid);
        userDoc.put("email", email);
        userDoc.put("displayName", "Fran Ibáñez");
        userDoc.put("photoUrl", null);
        userDoc.put("provider", "password");
        userDoc.put("emailVerified", user.isEmailVerified());
        userDoc.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        userDoc.put("lastLoginAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        userDoc.put("language", "es");
        userDoc.put("level", "B2");

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
        stats.put("totalFlashcards", 120);
        stats.put("studiedFlashcards", 15);
        stats.put("totalQuizzes", 12);
        stats.put("completedQuizzes", 2);
        stats.put("totalQuestionsAnswered", 48);
        stats.put("correctAnswers", 36);
        userDoc.put("stats", stats);

        userDoc.put("migratedFromLocal", false);

        // Write document users/{uid} with merge to avoid overwriting unexpected fields
        DocumentReference ref = db.collection("users").document(uid);
        ref.set(userDoc, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    updateStatus("Default user doc written for " + uid);
                    Toast.makeText(this, "User doc written", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    updateStatus("Error writing user doc: " + e);
                    Toast.makeText(this, "Write error: " + e, Toast.LENGTH_LONG).show();
                });
    }
}
