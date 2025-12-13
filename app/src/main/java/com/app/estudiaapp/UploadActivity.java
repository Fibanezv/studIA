package com.app.estudiaapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.app.estudiaapp.ui.OverlayManager;
import androidx.appcompat.app.AlertDialog;
import android.widget.EditText;
import com.app.estudiaapp.utils.OpenAiKeyStore;
import com.app.estudiaapp.utils.FileValidator;
import com.app.estudiaapp.utils.UploadErrorType;
import com.app.estudiaapp.utils.UploadErrorHandler;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.SetOptions;

public class UploadActivity extends BaseActivity {

    private static final int PICK_FILE = 100;
    private Button uploadFileBtn;
    private Button createPlanBtn;
    private ImageButton removeFileBtn;
    private Uri selectedFileUri;
    private TextView selectedFileNameText;
    private LinearLayout selectedFileContainer;
    private static final String TAG = "UploadActivity";
    private OverlayManager overlayManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        uploadFileBtn = findViewById(R.id.button_upload_file);
        createPlanBtn = findViewById(R.id.button_get_study_plan);
        selectedFileNameText = findViewById(R.id.selected_file_name);
        selectedFileContainer = findViewById(R.id.selected_file_container);
        removeFileBtn = findViewById(R.id.button_remove_file);

        // Initialize overlay manager with ids (use getIdentifier for cancel which may be absent in some layouts)
        int overlayId = getResources().getIdentifier("processing_overlay", "id", getPackageName());
        int msgId = getResources().getIdentifier("processing_message", "id", getPackageName());
        int cancelId = getResources().getIdentifier("processing_cancel_button", "id", getPackageName());
        overlayManager = new OverlayManager(this, overlayId, msgId, cancelId);

        uploadFileBtn.setOnClickListener(v -> openFileChooser());
        
        removeFileBtn.setOnClickListener(v -> {
            // Clear selected file
            selectedFileUri = null;
            selectedFileNameText.setText("");
            selectedFileContainer.setVisibility(View.GONE);
            uploadFileBtn.setVisibility(View.VISIBLE);
            showTemporaryStatus("Documento eliminado", 1500);
        });
        
        createPlanBtn.setOnClickListener(v -> {
            if (selectedFileUri == null) {
                showTemporaryStatus("Seleccione un documento primero", 2000);
                return;
            }
            createPlanInApp(selectedFileUri);
        });

        try {
            debugFetchRemoteConfig();
        } catch (Exception ignored) {}
    }

    private void debugFetchRemoteConfig() {
        FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance();
        remoteConfig.setConfigSettingsAsync(new FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(0).build());
        remoteConfig.setDefaultsAsync(java.util.Collections.singletonMap("OPENAI_API_KEY", "<LOCAL_PLACEHOLDER>"))
                .addOnCompleteListener(defTask -> remoteConfig.fetchAndActivate().addOnCompleteListener(fetchTask -> {
                    try {
                        android.util.Log.i(TAG, "[debugFetch] RemoteConfig fetchAndActivate success=" + fetchTask.isSuccessful());
                        java.util.Map<String, com.google.firebase.remoteconfig.FirebaseRemoteConfigValue> all = remoteConfig.getAll();
                        android.util.Log.i(TAG, "[debugFetch] RemoteConfig keys count=" + (all != null ? all.size() : 0));
                        String key = "";
                        try { key = remoteConfig.getString("OPENAI_API_KEY"); } catch (Exception ignored) {}
                        boolean present = key != null && !key.isEmpty();
                        String masked = present ? (key.length() <= 8 ? "****" : (key.substring(0,4) + "..." + key.substring(key.length()-4))) : "<missing>";
                        android.util.Log.i(TAG, "[debugFetch] OPENAI_API_KEY present=" + present + " masked=" + masked + " len=" + (key != null ? key.length() : 0));
                    } catch (Exception e) {
                        android.util.Log.w(TAG, "[debugFetch] RemoteConfig debug fetch failed", e);
                    }
                }));
    }

    private void createPlanInApp(Uri uri) {
        Log.i(TAG, "=== Starting plan creation process ===");
        Log.i(TAG, "Phase: validation - URI: " + uri);
        showProcessing("Procesando documento...");

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Log.w(TAG, "User not authenticated, starting anonymous sign-in");
            runOnUiThread(() -> showTemporaryStatus("Iniciando sesión anónima...", 1000));
            FirebaseAuth.getInstance().signInAnonymously().addOnCompleteListener(task -> {
                if (task.isSuccessful()) createPlanInApp(uri);
                else runOnUiThread(() -> showTemporaryStatus("No se pudo iniciar sesión.", 2000));
            });
            return;
        }

        // Collect form fields to pass to the Worker so it can enforce requested sizes
        // Title is generated by OpenAI on the server; remove client title input
        int quizLimit = 0;
        int questionsLimit = 0;
        int flashcardLimit = 0;
        int hotTopicsLimit = 0;
        try { android.widget.Spinner sp = findViewById(R.id.spinner_quiz_limit); if (sp != null) quizLimit = Integer.parseInt(String.valueOf(sp.getSelectedItem())); } catch (Exception e) {
            Log.w(TAG, "Failed to parse quizLimit, using default 0", e);
        }
        try { android.widget.Spinner spq = findViewById(R.id.spinner_questions_limit); if (spq != null) questionsLimit = Integer.parseInt(String.valueOf(spq.getSelectedItem())); } catch (Exception e) {
            Log.w(TAG, "Failed to parse questionsLimit, using default 0", e);
        }
        try { android.widget.Spinner sfl = findViewById(R.id.spinner_flashcard_limit); if (sfl != null) flashcardLimit = Integer.parseInt(String.valueOf(sfl.getSelectedItem())); } catch (Exception e) {
            Log.w(TAG, "Failed to parse flashcardLimit, using default 0", e);
        }
        try { android.widget.Spinner sht = findViewById(R.id.spinner_hot_topics_limit); if (sht != null) hotTopicsLimit = Integer.parseInt(String.valueOf(sht.getSelectedItem())); } catch (Exception e) {
            Log.w(TAG, "Failed to parse hotTopicsLimit, using default 0", e);
        }

        Log.i(TAG, "Form inputs: quizLimit=" + quizLimit + ", questionsLimit=" + questionsLimit + ", flashcardLimit=" + flashcardLimit + ", hotTopicsLimit=" + hotTopicsLimit);

        // Read selected language and quiz typology
        String language = "es"; // default Spanish
        try { android.widget.Spinner langSp = findViewById(R.id.spinner_language); if (langSp != null) language = String.valueOf(langSp.getSelectedItem()); } catch (Exception ignored) {}
        String quizTypology = "A,B,C,D";
        try { android.widget.Spinner qt = findViewById(R.id.spinner_quiz_type); if (qt != null) quizTypology = String.valueOf(qt.getSelectedItem()); } catch (Exception ignored) {}
        // Read selected difficulty/level
        String level = "";
        try { android.widget.Spinner lvl = findViewById(R.id.spinner_level); if (lvl != null) level = String.valueOf(lvl.getSelectedItem()); } catch (Exception ignored) {}

        // Read user-provided priority points (comma-separated)
        String priorityPoints = "";
        try { android.widget.EditText pp = findViewById(R.id.input_priority_topics); if (pp != null) priorityPoints = pp.getText().toString().trim(); } catch (Exception ignored) {}

        // derive include flags from whether the selected limit is greater than zero
        boolean includeQuizzes = quizLimit > 0;
        boolean includeFlashcards = flashcardLimit > 0;
        boolean includeHotTopics = hotTopicsLimit > 0;

        // Build limits JSON and membership (membership currently defaulted to "free")
        String membership = "free";
        String limitsJson = null;
        try {
            org.json.JSONObject limitsObj = new org.json.JSONObject();
            limitsObj.put("quizLimit", quizLimit);
            limitsObj.put("questionsLimit", questionsLimit);
            limitsObj.put("flashcardLimit", flashcardLimit);
            limitsObj.put("hotTopicsLimit", hotTopicsLimit);
            limitsJson = limitsObj.toString();
        } catch (Exception ignored) {}

        java.util.Map<String, Object> limitsMap = new java.util.HashMap<>();
        limitsMap.put("quizLimit", quizLimit);
        limitsMap.put("questionsLimit", questionsLimit);
        limitsMap.put("flashcardLimit", flashcardLimit);
        limitsMap.put("hotTopicsLimit", hotTopicsLimit);

        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("membership", membership);
        payload.put("limits", limitsMap);
        payload.put("includeQuizzes", includeQuizzes);
        payload.put("quizLimit", quizLimit);
        payload.put("questionsLimit", questionsLimit);
        payload.put("includeFlashcards", includeFlashcards);
        payload.put("flashcardLimit", flashcardLimit);
        payload.put("includeHotTopics", includeHotTopics);
        if (language != null && !language.trim().isEmpty()) payload.put("language", language.trim());
        if (quizTypology != null && !quizTypology.trim().isEmpty()) payload.put("quizTypology", quizTypology.trim());
        if (priorityPoints != null && !priorityPoints.trim().isEmpty()) payload.put("priorityPoints", priorityPoints.trim());
        if (level != null && !level.trim().isEmpty()) payload.put("difficulty", level.trim());

        String uid = null;
        try { uid = FirebaseAuth.getInstance().getCurrentUser().getUid(); } catch (Exception ignored) {}
        if (uid == null || uid.trim().isEmpty()) {
            showTemporaryStatus("Debes iniciar sesión para generar un plan", 2500);
            return;
        }

        FirebaseStorage storage = FirebaseStorage.getInstance();
        String uploadId = java.util.UUID.randomUUID().toString();
        Log.i(TAG, "Phase: storage_init - uploadId: " + uploadId + ", uid: " + uid);
        com.google.firebase.storage.StorageReference ref = storage.getReference().child("users/" + uid + "/uploads/" + uploadId + "/source.pdf");
        com.google.firebase.storage.UploadTask uploadTask = ref.putFile(uri);

        try { overlayManager.show("Subiendo documento...", () -> { try { uploadTask.cancel(); } catch (Exception ignored) {} }); } catch (Exception ignored) {}

        com.google.android.gms.tasks.Task<String> urlTask = uploadTask
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        Log.e(TAG, "Storage upload failed", task.getException());
                        throw task.getException();
                    }
                    Log.i(TAG, "Storage upload successful, getting download URL");
                    return ref.getDownloadUrl();
                })
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        Log.e(TAG, "Failed to get download URL", task.getException());
                        throw task.getException();
                    }
                    android.net.Uri download = task.getResult();
                    String downloadUrl = download != null ? download.toString() : null;
                    Log.i(TAG, "Download URL obtained: " + (downloadUrl != null ? downloadUrl.substring(0, Math.min(50, downloadUrl.length())) + "..." : "null"));
                    return downloadUrl;
                });

        com.google.android.gms.tasks.Task<String> planIdTask = allocateCanonicalPlanId(uid);

        com.google.android.gms.tasks.Tasks.whenAllSuccess(urlTask, planIdTask)
                .addOnSuccessListener(results -> {
                    String pdfUrl = (String) results.get(0);
                    String clientPlanId = results.size() > 1 ? (String) results.get(1) : null;
                    Log.i(TAG, "Phase: pre_function_call - pdfUrl obtained, clientPlanId: " + clientPlanId);
                    if (pdfUrl == null || pdfUrl.trim().isEmpty()) {
                        Log.e(TAG, "No PDF URL obtained from storage");
                        throw new IllegalStateException("No se obtuvo URL del archivo");
                    }
                    payload.put("pdfUrl", pdfUrl);
                    if (clientPlanId != null && !clientPlanId.isEmpty()) payload.put("clientPlanId", clientPlanId);

                    try { overlayManager.show("Generando plan en la nube...", null); } catch (Exception ignored) {}

                    Log.i(TAG, "Phase: function_call - calling generateStudyPlan with " + payload.size() + " parameters");
                    FirebaseFunctions.getInstance()
                            .getHttpsCallable("generateStudyPlan")
                            .call(payload)
                            .addOnSuccessListener(result -> {
                                hideProcessing();
                                showTemporaryStatus("Plan creado. Cargando contenido...", 1500);
                                Log.i(TAG, "=== Plan creation successful - returning to MainActivity ===");
                                try {
                                    Intent i = new Intent(UploadActivity.this, MainActivity.class);
                                    i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                    startActivity(i);
                                    finish();
                                } catch (Exception ignored) {}
                            })
                            .addOnFailureListener(e -> {
                                hideProcessing();
                                Log.e(TAG, "Phase: function_call_failed - Cloud Function call failed", e);
                                handleUploadError(e, "Error al llamar Cloud Function");
                            });

                })
                .addOnFailureListener(e -> {
                    hideProcessing();
                    Log.e(TAG, "Phase: upload_or_id_allocation_failed", e);
                    handleUploadError(e, "Error durante la subida y asignación de ID");
                });
    }

    /**
     * Muestra un diálogo de error cuando falla la validación del archivo
     */
    private void showValidationError(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Archivo no válido")
                .setMessage(message != null ? message : "El archivo seleccionado no es válido")
                .setPositiveButton("Seleccionar otro", (dialog, which) -> openFileChooser())
                .setNegativeButton("Cerrar", null)
                .show();
        
        Log.w(TAG, "File validation failed: " + message);
    }

    /**
     * Maneja errores del proceso de upload con clasificación inteligente
     */
    private void handleUploadError(Exception exception, String context) {
        UploadErrorType errorType = UploadErrorHandler.classify(exception);
        String userMessage = errorType.getUserMessage();
        String dialogTitle = errorType.getDialogTitle();
        boolean isRetriable = errorType.isRetriable();

        // Logging estructurado
        if (UploadErrorHandler.isCritical(errorType)) {
            Log.e(TAG, context + " - " + errorType.name() + ": " + userMessage, exception);
        } else {
            Log.w(TAG, context + " - " + errorType.name() + ": " + userMessage);
        }

        // Construir diálogo
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(dialogTitle)
                .setMessage(userMessage);

        // Botón "Reintentar" solo si es retriable
        if (isRetriable) {
            builder.setPositiveButton("Reintentar", (dialog, which) -> {
                if (selectedFileUri != null) {
                    createPlanInApp(selectedFileUri);
                }
            });
            builder.setNegativeButton("Cerrar", null);
        } else {
            builder.setPositiveButton("Entendido", null);
        }

        // Botón "Ver detalles" en modo debug
        if (isAppDebug()) {
            builder.setNeutralButton("Ver detalles", (dialog, which) -> {
                String details = UploadErrorHandler.getDetailedMessage(exception, true);
                new AlertDialog.Builder(this)
                        .setTitle("Detalles técnicos")
                        .setMessage(details)
                        .setPositiveButton("Cerrar", null)
                        .show();
            });
        }

        builder.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                // Validar archivo antes de aceptarlo
                FileValidator.ValidationResult validation = FileValidator.validate(this, uri);
                
                if (!validation.isValid) {
                    // Mostrar error de validación al usuario
                    showValidationError(validation.errorMessage);
                    return;
                }
                
                // Archivo válido: tomar permisos persistentes y actualizar UI
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    Log.i(TAG, "Persistable URI permission granted for: " + uri);
                } catch (Exception e) {
                    Log.w(TAG, "Could not take persistable URI permission (may not be needed)", e);
                }
                
                selectedFileUri = uri;
                String name = queryName(uri);
                if (selectedFileNameText != null && name != null) {
                    selectedFileNameText.setText(name);
                    selectedFileContainer.setVisibility(View.VISIBLE);
                    uploadFileBtn.setVisibility(View.GONE);
                    
                    // Mostrar info del archivo validado
                    String sizeInfo = FileValidator.formatFileSize(validation.fileSizeBytes);
                    showTemporaryStatus("Archivo seleccionado: " + sizeInfo, 2000);
                    Log.i(TAG, "File selected: name=" + name + ", mimeType=" + validation.mimeType + ", size=" + sizeInfo);
                }
            }
        }
    }

    private String queryName(Uri uri) {
        String result = null;
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) result = cursor.getString(idx);
            }
        } catch (Exception ignored) {}
        if (result == null) result = uri.getLastPathSegment();
        return result;
    }

    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, PICK_FILE);
        } catch (Exception e) {
            android.util.Log.w(TAG, "openFileChooser failed", e);
        }
    }

    private com.google.android.gms.tasks.Task<String> allocateCanonicalPlanId(String uid) {
        if (uid == null || uid.trim().isEmpty()) return com.google.android.gms.tasks.Tasks.forResult(null);
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        com.google.firebase.firestore.DocumentReference userRef = db.collection("users").document(uid);
        return db.runTransaction((com.google.firebase.firestore.Transaction.Function<String>) transaction -> {
            com.google.firebase.firestore.DocumentSnapshot snap = transaction.get(userRef);
            long current = 0L;
            if (snap != null && snap.exists() && snap.contains("nextPlanNumber")) {
                try {
                    Object v = snap.get("nextPlanNumber");
                    if (v instanceof Number) current = ((Number) v).longValue();
                    else current = Long.parseLong(String.valueOf(v));
                } catch (Exception ignored) {
                    current = 0L;
                }
            }
            long next = current + 1L;
            java.util.Map<String, Object> updates = new java.util.HashMap<>();
            updates.put("nextPlanNumber", next);
            transaction.set(userRef, updates, com.google.firebase.firestore.SetOptions.merge());
            return String.format("U0%05d", next);
        });
    }

    private void showProcessing(String message) {
        try { overlayManager.show(message, null); } catch (Exception ignored) {}
    }

    private void hideProcessing() {
        try { overlayManager.hide(); } catch (Exception ignored) {}
    }

    private void showTemporaryStatus(String message, int millis) {
        try {
            android.widget.Toast t = android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG);
            t.show();
        } catch (Exception ignored) {}
    }

    private boolean isAppDebug() {
        try {
            return (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        } catch (Exception ignored) {
            return false;
        }
    }
}
