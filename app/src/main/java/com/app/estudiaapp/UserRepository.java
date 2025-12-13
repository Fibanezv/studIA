package com.app.estudiaapp;

import android.content.Context;
import android.util.Log;

import com.app.estudiaapp.helper.StorageHelper;
import com.app.estudiaapp.models.StudyPlan;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UserRepository centraliza operaciones relacionadas con el documento users/{uid}
 * y la migración de planes locales (JSON) a Firestore.
 * 
 * Incluye sistema de caché con TTL (Time To Live) y snapshot listeners opcionales
 * para mantener datos actualizados en tiempo real.
 */
public class UserRepository {

    private static final String TAG = "UserRepository";
    private final FirebaseFirestore db;
    
    // Cache with TTL for plans - thread-safe ConcurrentHashMap
    private static final java.util.Map<String, CachedPlan> PLAN_CACHE = new ConcurrentHashMap<>();
    
    // Default TTL: 5 minutes (configurable via Remote Config in future)
    private static final long DEFAULT_CACHE_TTL_MILLIS = 5 * 60 * 1000;
    
    private final Context ctx;

    /**
     * Wrapper class for cached plans with expiration logic
     */
    private static class CachedPlan {
        final StudyPlan plan;
        final long cachedAtMillis;
        final long ttlMillis;

        CachedPlan(StudyPlan plan, long cachedAtMillis) {
            this(plan, cachedAtMillis, DEFAULT_CACHE_TTL_MILLIS);
        }

        CachedPlan(StudyPlan plan, long cachedAtMillis, long ttlMillis) {
            this.plan = plan;
            this.cachedAtMillis = cachedAtMillis;
            this.ttlMillis = ttlMillis;
        }

        boolean isExpired() {
            long age = System.currentTimeMillis() - cachedAtMillis;
            return age > ttlMillis;
        }

        long getAgeSeconds() {
            return (System.currentTimeMillis() - cachedAtMillis) / 1000;
        }
    }

    public UserRepository(@NonNull Context context) {
        this(context, FirebaseFirestore.getInstance());
    }

    // VisibleForTesting: allows injecting a mocked FirebaseFirestore
    public UserRepository(@NonNull Context context, @NonNull FirebaseFirestore firestore) {
        this.db = firestore;
        this.ctx = context.getApplicationContext();
    }

    // Only for tests: allow injecting a cached entry without using Firestore
    @VisibleForTesting
    void putCachedPlanForTest(@NonNull String uid, @NonNull String planId, @NonNull StudyPlan plan, long cachedAtMillis) {
        PLAN_CACHE.put(uid + "::" + planId, new CachedPlan(plan, cachedAtMillis));
    }

    /**
     * Recompute aggregate stats from all plans under users/{uid}/plans and write them
     * into users/{uid}.stats. onSuccessRunnable runs when the write completes.
     */
    public void recomputeAndUpdateUserStats(@NonNull String uid, @NonNull Runnable onSuccessRunnable, @NonNull OnFailureListener onFailure) {
        if (uid == null || uid.isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("uid required"));
            return;
        }

        db.collection("users").document(uid).collection("plans").get()
                .addOnSuccessListener(qs -> {
                    int totalPlans = 0;
                    int totalFlashcards = 0;
                    int totalHotTopics = 0;
                    int totalQuizzes = 0;
                    int studiedFlashcards = 0;
                    int completedQuizzes = 0;
                    int totalQuestionsAnswered = 0;
                    int correctAnswers = 0;
                    // aggregate user progress fields
                    int aggPreguntasRespondidas = 0;
                    int aggPreguntasCorrectas = 0;
                    int aggFlashcardsEstudiadas = 0;
                    int aggNivel = 1;

                    Gson gson = new Gson();
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        totalPlans++;
                        try {
                            StudyPlan sp = gson.fromJson(gson.toJson(d.getData()), StudyPlan.class);
                            if (sp == null) continue;
                            if (sp.flashcards != null) {
                                totalFlashcards += sp.flashcards.size();
                                for (com.app.estudiaapp.models.Flashcard f : sp.flashcards) {
                                    if (f != null && f.completada) studiedFlashcards++;
                                }
                            }
                            if (sp.hotTopics != null) totalHotTopics += sp.hotTopics.size();
                            if (sp.quizzes != null) {
                                totalQuizzes += sp.quizzes.size();
                                for (com.app.estudiaapp.models.Quiz q : sp.quizzes) {
                                    if (q != null && q.progreso != null) {
                                        totalQuestionsAnswered += q.progreso.preguntasRespondidas;
                                        correctAnswers += q.progreso.preguntasCorrectas;
                                        if (q.progreso.nota >= 0) completedQuizzes++;
                                    }
                                }
                            }
                            if (sp.progreso != null) {
                                aggPreguntasRespondidas += sp.progreso.preguntasRespondidas;
                                aggPreguntasCorrectas += sp.progreso.preguntasCorrectas;
                                aggFlashcardsEstudiadas += sp.progreso.flashcardsEstudiadas;
                                aggNivel = Math.max(aggNivel, sp.progreso.nivel);
                            }
                        } catch (Exception ex) {
                            Log.w(TAG, "Error parsing plan for stats: " + d.getId(), ex);
                        }
                    }

                    Map<String, Object> stats = new HashMap<>();
                    stats.put("totalPlans", totalPlans);
                    stats.put("totalFlashcards", totalFlashcards);
                    stats.put("studiedFlashcards", studiedFlashcards);
                    stats.put("totalHotTopics", totalHotTopics);
                    stats.put("totalQuizzes", totalQuizzes);
                    stats.put("completedQuizzes", completedQuizzes);
                    stats.put("totalQuestionsAnswered", totalQuestionsAnswered);
                    stats.put("correctAnswers", correctAnswers);

                    Map<String, Object> userProgress = new HashMap<>();
                    userProgress.put("preguntasRespondidas", aggPreguntasRespondidas);
                    userProgress.put("preguntasCorrectas", aggPreguntasCorrectas);
                    userProgress.put("flashcardsEstudiadas", aggFlashcardsEstudiadas);
                    userProgress.put("nivel", aggNivel);

                    // Write both stats and userProgress into user doc
                    Map<String, Object> toWrite = new HashMap<>();
                    toWrite.put("stats", stats);
                    toWrite.put("progreso", userProgress);

                    db.collection("users").document(uid).set(toWrite, SetOptions.merge())
                            .addOnSuccessListener(a -> {
                                try { onSuccessRunnable.run(); } catch (Exception ignore) {}
                            }).addOnFailureListener(onFailure);

                }).addOnFailureListener(onFailure);
    }

    /**
     * Comprueba si el usuario ya tiene el flag migratedFromLocal en su doc.
     * Si no, sube los planes locales (StorageHelper) a la colección
     * users/{uid}/plans y marca migratedFromLocal = true.
     * La operación se ejecuta en background y no bloquea el hilo UI.
     */
    public void migrateLocalPlansIfNeeded(@NonNull String uid) {
        if (uid == null || uid.isEmpty()) return;

        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    boolean migrated = false;
                    if (doc != null && doc.exists()) {
                        Object m = doc.get("migratedFromLocal");
                        if (m instanceof Boolean) migrated = (Boolean) m;
                    }

                    if (migrated) {
                        Log.d(TAG, "User already migratedFromLocal=true, skipping migration for " + uid);
                        return;
                    }

                    // Load local plans and upload
                    ArrayList<StudyPlan> plans = StorageHelper.listarPlanes(ctx);
                    if (plans == null || plans.isEmpty()) {
                        // still mark migrated to avoid repeated checks
                        db.collection("users").document(uid).update("migratedFromLocal", true)
                                .addOnSuccessListener(a -> Log.d(TAG, "Marked migratedFromLocal=true (no local plans)"))
                                .addOnFailureListener(e -> Log.w(TAG, "Failed to mark migratedFromLocal", e));
                        return;
                    }

                    Gson gson = new Gson();
                    Type mapType = new TypeToken<Map<String, Object>>() {}.getType();

                    // Upload each plan under users/{uid}/plans/{planId}
                    for (StudyPlan p : plans) {
                        try {
                            if (p == null || p.id == null) continue;
                            String json = gson.toJson(p);
                            Map<String, Object> map = gson.fromJson(json, mapType);
                            if (map == null) map = new HashMap<>();
                            db.collection("users").document(uid).collection("plans").document(p.id)
                                    .set(map)
                                    .addOnSuccessListener(a -> Log.d(TAG, "Uploaded plan " + p.id))
                                    .addOnFailureListener(e -> Log.w(TAG, "Failed upload plan " + p.id, e));
                        } catch (Exception ex) {
                            Log.w(TAG, "Error converting/uploading plan " + (p != null ? p.id : "null"), ex);
                        }
                    }

                    // After requesting uploads, mark migrated flag so we don't repeat
                    db.collection("users").document(uid).update("migratedFromLocal", true)
                            .addOnSuccessListener(a -> Log.d(TAG, "Marked migratedFromLocal=true after upload"))
                            .addOnFailureListener(e -> Log.w(TAG, "Failed to mark migratedFromLocal after upload", e));
                })
                .addOnFailureListener(e -> Log.w(TAG, "Could not read user doc to check migration flag", e));
    }

    /**
     * Helper básico para obtener el documento users/{uid} (async)
     */
    public void fetchUserDoc(@NonNull String uid, OnSuccessListener<DocumentSnapshot> onSuccess, OnFailureListener onFailure) {
        db.collection("users").document(uid).get().addOnSuccessListener(onSuccess).addOnFailureListener(onFailure);
    }

    /**
     * Obtener todos los planes del usuario desde users/{uid}/plans
     */
    public void getPlans(@NonNull String uid, OnSuccessListener<List<StudyPlan>> onSuccess, OnFailureListener onFailure) {
        db.collection("users").document(uid).collection("plans").get()
                .addOnSuccessListener((QuerySnapshot qs) -> {
                    List<StudyPlan> out = new ArrayList<>();
                    Gson gson = new Gson();
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        try {
                            String json = gson.toJson(d.getData());
                            StudyPlan sp = gson.fromJson(json, StudyPlan.class);
                            if (sp != null) out.add(sp);
                        } catch (Exception ex) {
                            Log.w(TAG, "Failed to parse plan doc " + d.getId(), ex);
                        }
                    }
                    onSuccess.onSuccess(out);
                }).addOnFailureListener(onFailure);
    }

    /**
     * Obtener un plan concreto con soporte de cache TTL
     */
    public void getPlan(@NonNull String uid, @NonNull String planId, OnSuccessListener<StudyPlan> onSuccess, OnFailureListener onFailure) {
        String cacheKey = uid + "::" + planId;
        
        // Check cache - serve if not expired
        CachedPlan cached = PLAN_CACHE.get(cacheKey);
        if (cached != null) {
            if (!cached.isExpired()) {
                Log.d(TAG, "Cache HIT for " + planId + " (age: " + cached.getAgeSeconds() + "s)");
                onSuccess.onSuccess(cached.plan);
                return;
            } else {
                Log.d(TAG, "Cache EXPIRED for " + planId + " (age: " + cached.getAgeSeconds() + "s)");
                PLAN_CACHE.remove(cacheKey); // Clean up expired entry
            }
        } else {
            Log.d(TAG, "Cache MISS for " + planId);
        }
        
        // Fetch from Firestore
        db.collection("users").document(uid).collection("plans").document(planId).get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        try {
                            Gson gson = new Gson();
                            String json = gson.toJson(doc.getData());
                            StudyPlan p = gson.fromJson(json, StudyPlan.class);
                            
                            // Cache with current timestamp
                            PLAN_CACHE.put(cacheKey, new CachedPlan(p, System.currentTimeMillis()));
                            Log.d(TAG, "Cached plan " + planId + " with TTL=" + (DEFAULT_CACHE_TTL_MILLIS / 1000) + "s");
                            
                            onSuccess.onSuccess(p);
                        } catch (Exception ex) {
                            Log.w(TAG, "Error parsing plan " + planId, ex);
                            onFailure.onFailure(ex);
                        }
                    } else {
                        onSuccess.onSuccess(null);
                    }
                }).addOnFailureListener(onFailure);
    }

    /**
     * Get a specific quiz by id from cache or Firestore. If plan not in cache, fetch it then resolve quiz.
     * Uses cache TTL logic from getPlan()
     */
    public void getQuiz(@NonNull String uid, @NonNull String planId, @NonNull String quizId,
                        OnSuccessListener<com.app.estudiaapp.models.Quiz> onSuccess,
                        OnFailureListener onFailure) {
        String cacheKey = uid + "::" + planId;
        CachedPlan cached = PLAN_CACHE.get(cacheKey);
        
        // Check cache if not expired
        if (cached != null && !cached.isExpired() && cached.plan.quizzes != null) {
            for (com.app.estudiaapp.models.Quiz q : cached.plan.quizzes) {
                if (q != null && quizId.equals(q.id)) {
                    Log.d(TAG, "Quiz " + quizId + " served from cache (age: " + cached.getAgeSeconds() + "s)");
                    onSuccess.onSuccess(q);
                    return;
                }
            }
        }
        
        // Fallback: fetch plan then find quiz (getPlan handles caching)
        getPlan(uid, planId, plan -> {
            if (plan != null && plan.quizzes != null) {
                for (com.app.estudiaapp.models.Quiz q : plan.quizzes) {
                    if (q != null && quizId.equals(q.id)) { onSuccess.onSuccess(q); return; }
                }
            }
            onSuccess.onSuccess(null);
        }, onFailure);
    }

    /**
     * Guardar o actualizar un plan (set merge)
     */
    public void savePlan(@NonNull String uid, @NonNull StudyPlan plan, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        try {
            Gson gson = new Gson();
            Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> map = gson.fromJson(gson.toJson(plan), mapType);
            db.collection("users").document(uid).collection("plans").document(plan.id).set(map, SetOptions.merge())
                    .addOnSuccessListener(a -> onSuccess.onSuccess(null)).addOnFailureListener(onFailure);
        } catch (Exception ex) {
            onFailure.onFailure(ex);
        }
    }

    /**
     * Actualiza el progreso de un quiz dentro de un plan. Estrategia: leer-el-modificar-escribir.
     * Nota: para alta concurrencia usar transacciones o Cloud Functions.
     */
    public void updateQuizProgress(@NonNull String uid, @NonNull String planId, @NonNull String quizId, com.app.estudiaapp.models.QuizProgress newProgress, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        // Delegate to transactional version for consistency
        updateQuizProgressWithTransaction(uid, planId, quizId, newProgress, onSuccess, onFailure);
    }

    /**
     * TRANSACTIONAL VERSION: Actualiza el progreso de un quiz de forma atómica.
     * 
     * Usa db.runTransaction() para garantizar consistencia bajo concurrencia.
     * Automáticamente reintenta en caso de conflictos (Firestore).
     * 
     * @param uid Usuario ID
     * @param planId Plan ID
     * @param quizId Quiz ID a actualizar
     * @param newProgress Nuevo QuizProgress
     * @param onSuccess Callback de éxito
     * @param onFailure Callback de error
     */
    public void updateQuizProgressWithTransaction(@NonNull String uid, @NonNull String planId, @NonNull String quizId, 
                                                   com.app.estudiaapp.models.QuizProgress newProgress, 
                                                   OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        db.runTransaction(transaction -> {
            DocumentReference planRef = db.collection("users").document(uid).collection("plans").document(planId);
            DocumentSnapshot planDoc = transaction.get(planRef);

            if (!planDoc.exists()) {
                throw new FirebaseFirestoreException("Plan document not found", FirebaseFirestoreException.Code.NOT_FOUND);
            }

            try {
                Gson gson = new Gson();
                StudyPlan plan = gson.fromJson(gson.toJson(planDoc.getData()), StudyPlan.class);

                if (plan == null || plan.quizzes == null) {
                    throw new FirebaseFirestoreException("Plan not found or has no quizzes", FirebaseFirestoreException.Code.INVALID_ARGUMENT);
                }

                // Find and update quiz progress
                boolean found = false;
                for (int i = 0; i < plan.quizzes.size(); i++) {
                    if (plan.quizzes.get(i) != null && quizId.equals(plan.quizzes.get(i).id)) {
                        plan.quizzes.get(i).progreso = newProgress;
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    throw new FirebaseFirestoreException("Quiz not found in plan", FirebaseFirestoreException.Code.NOT_FOUND);
                }

                // Convert plan back to map and update in transaction
                Map<String, Object> planMap = gson.fromJson(gson.toJson(plan), new TypeToken<Map<String, Object>>(){}.getType());
                transaction.set(planRef, planMap, SetOptions.merge());

                // Invalidate cache for this plan
                PLAN_CACHE.remove(uid + "::" + planId);
                Log.d(TAG, "updateQuizProgressWithTransaction: cache invalidated for " + uid + "/" + planId);

                return null;
            } catch (Exception ex) {
                Log.e(TAG, "updateQuizProgressWithTransaction: error parsing plan", ex);
                throw new RuntimeException(ex);
            }
        }).addOnSuccessListener(result -> {
            // After transaction succeeds, recompute aggregate stats
            recomputeAndUpdateUserStats(uid, () -> onSuccess.onSuccess(null), onFailure);
        }).addOnFailureListener(onFailure);
    }

    /**
     * TRANSACTIONAL VERSION: Marca una flashcard como completada de forma atómica.
     * 
     * Usa db.runTransaction() para garantizar que la marca de completitud
     * y los contadores de progreso se actualizan conjuntamente.
     * 
     * @param uid Usuario ID
     * @param planId Plan ID
     * @param index Índice de la flashcard
     * @param onSuccess Callback de éxito
     * @param onFailure Callback de error
     */
    public void markFlashcardCompletedWithTransaction(@NonNull String uid, @NonNull String planId, int index, 
                                                       OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        db.runTransaction(transaction -> {
            DocumentReference planRef = db.collection("users").document(uid).collection("plans").document(planId);
            DocumentSnapshot planDoc = transaction.get(planRef);

            if (!planDoc.exists()) {
                throw new FirebaseFirestoreException("Plan document not found", FirebaseFirestoreException.Code.NOT_FOUND);
            }

            try {
                Gson gson = new Gson();
                StudyPlan plan = gson.fromJson(gson.toJson(planDoc.getData()), StudyPlan.class);

                if (plan == null || plan.flashcards == null || index < 0 || index >= plan.flashcards.size()) {
                    throw new FirebaseFirestoreException("Flashcard index out of bounds or plan missing", 
                            FirebaseFirestoreException.Code.INVALID_ARGUMENT);
                }

                // Mark flashcard as completed
                plan.flashcards.get(index).completada = true;

                // Update progress counters
                if (plan.progreso == null) {
                    plan.progreso = new com.app.estudiaapp.models.UserProgress();
                }
                int total = plan.flashcards.size();
                plan.progreso.registrarFlashcardEstudiada(total);

                // Convert and update in transaction
                Map<String, Object> planMap = gson.fromJson(gson.toJson(plan), new TypeToken<Map<String, Object>>(){}.getType());
                transaction.set(planRef, planMap, SetOptions.merge());

                // Invalidate cache for this plan
                PLAN_CACHE.remove(uid + "::" + planId);
                Log.d(TAG, "markFlashcardCompletedWithTransaction: cache invalidated for " + uid + "/" + planId);

                return null;
            } catch (Exception ex) {
                Log.e(TAG, "markFlashcardCompletedWithTransaction: error parsing plan", ex);
                throw new RuntimeException(ex);
            }
        }).addOnSuccessListener(result -> {
            // After transaction succeeds, recompute aggregate stats
            recomputeAndUpdateUserStats(uid, () -> onSuccess.onSuccess(null), onFailure);
        }).addOnFailureListener(onFailure);
    }

    /**
     * Marca una flashcard como completada dentro de un plan: lee, marca, guarda.
     * DEPRECATED: Now delegates to transactional version for consistency.
     */
    public void markFlashcardCompleted(@NonNull String uid, @NonNull String planId, int index, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        // Delegate to transactional version for consistency
        markFlashcardCompletedWithTransaction(uid, planId, index, onSuccess, onFailure);
    }

    /**
     * TRANSACTIONAL VERSION: Reinicia el progreso de un quiz de forma atómica.
     * 
     * Resetea todos los campos de progreso (preguntasRespondidas, preguntasCorrectas, 
     * preguntasIncorrectas, currentIndex, nota, lastUpdatedMillis) usando db.runTransaction()
     * para garantizar atomicidad. Invalida caché y recomputa stats agregadas.
     * 
     * Casos de uso:
     * - Usuario quiere reintentar un quiz completado
     * - Reiniciar intentos previos y empezar de cero
     * 
     * @param uid Usuario ID
     * @param planId Plan ID
     * @param quizId Quiz ID a resetear
     * @param onSuccess Callback de éxito (recibe null)
     * @param onFailure Callback de error
     */
    public void resetQuizProgress(@NonNull String uid, @NonNull String planId, @NonNull String quizId,
                                   OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        db.runTransaction(transaction -> {
            DocumentReference planRef = db.collection("users").document(uid).collection("plans").document(planId);
            DocumentSnapshot planDoc = transaction.get(planRef);

            if (!planDoc.exists()) {
                throw new FirebaseFirestoreException("Plan document not found", FirebaseFirestoreException.Code.NOT_FOUND);
            }

            try {
                Gson gson = new Gson();
                StudyPlan plan = gson.fromJson(gson.toJson(planDoc.getData()), StudyPlan.class);

                if (plan == null || plan.quizzes == null) {
                    throw new FirebaseFirestoreException("Plan not found or has no quizzes", FirebaseFirestoreException.Code.INVALID_ARGUMENT);
                }

                // Find the quiz to reset
                boolean found = false;
                for (int i = 0; i < plan.quizzes.size(); i++) {
                    if (plan.quizzes.get(i) != null && quizId.equals(plan.quizzes.get(i).id)) {
                        // Reset all progress fields to default state
                        com.app.estudiaapp.models.QuizProgress resetProgress = new com.app.estudiaapp.models.QuizProgress();
                        resetProgress.preguntasRespondidas = 0;
                        resetProgress.preguntasCorrectas = 0;
                        resetProgress.preguntasIncorrectas = 0;
                        resetProgress.currentIndex = 0;
                        resetProgress.nota = -1;
                        resetProgress.lastUpdatedMillis = System.currentTimeMillis();

                        plan.quizzes.get(i).progreso = resetProgress;
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    throw new FirebaseFirestoreException("Quiz not found in plan", FirebaseFirestoreException.Code.NOT_FOUND);
                }

                // Convert plan back to map and update in transaction
                Map<String, Object> planMap = gson.fromJson(gson.toJson(plan), new TypeToken<Map<String, Object>>(){}.getType());
                transaction.set(planRef, planMap, SetOptions.merge());

                // Invalidate cache for this plan
                PLAN_CACHE.remove(uid + "::" + planId);
                Log.d(TAG, "resetQuizProgress: cache invalidated for " + uid + "/" + planId + "/" + quizId);

                return null;
            } catch (Exception ex) {
                Log.e(TAG, "resetQuizProgress: error resetting quiz", ex);
                throw new RuntimeException(ex);
            }
        }).addOnSuccessListener(result -> {
            // After transaction succeeds, recompute aggregate stats
            Log.d(TAG, "resetQuizProgress: success, recomputing user stats");
            recomputeAndUpdateUserStats(uid, () -> onSuccess.onSuccess(null), onFailure);
        }).addOnFailureListener(exception -> {
            Log.e(TAG, "resetQuizProgress: transaction failed", exception);
            onFailure.onFailure(exception);
        });
    }
    
    // ==================== Cache Management Methods ====================
    
    /**
     * Clears the entire plan cache. Useful for logout or manual refresh scenarios.
     */
    public void clearCache() {
        int size = PLAN_CACHE.size();
        PLAN_CACHE.clear();
        Log.d(TAG, "Cache cleared manually (" + size + " entries removed)");
    }
    
    /**
     * Removes a specific plan from cache. Useful when plan is deleted or needs forced refresh.
     */
    public void invalidatePlanCache(@NonNull String uid, @NonNull String planId) {
        String cacheKey = uid + "::" + planId;
        CachedPlan removed = PLAN_CACHE.remove(cacheKey);
        if (removed != null) {
            Log.d(TAG, "Invalidated cache for plan " + planId + " (was " + removed.getAgeSeconds() + "s old)");
        }
    }
    
    /**
     * Registers a real-time snapshot listener for a specific plan.
     * Updates cache automatically when Firestore data changes.
     * 
     * @return ListenerRegistration that MUST be removed in Activity's onDestroy()
     */
    public ListenerRegistration registerPlanListener(@NonNull String uid, @NonNull String planId, 
                                                      @NonNull OnSuccessListener<StudyPlan> onUpdate) {
        String cacheKey = uid + "::" + planId;
        
        return db.collection("users").document(uid).collection("plans").document(planId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.w(TAG, "Snapshot listener error for plan " + planId, error);
                        return;
                    }
                    
                    if (snapshot != null && snapshot.exists()) {
                        try {
                            Gson gson = new Gson();
                            String json = gson.toJson(snapshot.getData());
                            StudyPlan plan = gson.fromJson(json, StudyPlan.class);
                            
                            // Update cache with fresh data
                            PLAN_CACHE.put(cacheKey, new CachedPlan(plan, System.currentTimeMillis()));
                            Log.d(TAG, "Snapshot listener updated cache for plan " + planId);
                            
                            onUpdate.onSuccess(plan);
                        } catch (Exception ex) {
                            Log.w(TAG, "Error parsing plan from snapshot " + planId, ex);
                        }
                    }
                });
    }
    
    /**
     * Refreshes a plan from Firestore if cache is expired.
     * Falls back to stale cache on network errors (offline resilience).
     * 
     * Call this in Activity's onResume() to ensure data freshness.
     */
    public void refreshPlanIfNeeded(@NonNull String uid, @NonNull String planId,
                                     @NonNull OnSuccessListener<StudyPlan> onSuccess,
                                     @NonNull OnFailureListener onFailure) {
        String cacheKey = uid + "::" + planId;
        CachedPlan cached = PLAN_CACHE.get(cacheKey);
        
        // If cache is fresh, serve immediately
        if (cached != null && !cached.isExpired()) {
            Log.d(TAG, "Refresh skipped for " + planId + " (cache fresh, age: " + cached.getAgeSeconds() + "s)");
            onSuccess.onSuccess(cached.plan);
            return;
        }
        
        // Cache expired or missing - fetch from Firestore
        Log.d(TAG, "Refreshing plan " + planId + " (cache expired or missing)");
        
        getPlan(uid, planId, onSuccess, exception -> {
            // On network failure, fallback to stale cache if available
            if (cached != null) {
                Log.w(TAG, "Network error refreshing " + planId + ", falling back to stale cache (age: " + 
                      cached.getAgeSeconds() + "s)", exception);
                onSuccess.onSuccess(cached.plan);
            } else {
                // No cache fallback available
                onFailure.onFailure(exception);
            }
        });
    }
}
