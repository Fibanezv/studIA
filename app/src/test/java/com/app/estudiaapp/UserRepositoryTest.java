package com.app.estudiaapp;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;

import android.content.Context;
import android.util.Log;

import com.app.estudiaapp.models.Quiz;
import com.app.estudiaapp.models.QuizProgress;
import com.app.estudiaapp.models.StudyPlan;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;
import com.google.android.gms.tasks.OnFailureListener;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockedStatic;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class UserRepositoryTest {

    private AutoCloseable mocks;
    private Context context;
    private MockedStatic<Log> logMock;

    @Before
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        context = mock(Context.class);
        when(context.getApplicationContext()).thenReturn(context);
        logMock = Mockito.mockStatic(Log.class);
        logMock.when(() -> Log.d(anyString(), anyString())).thenReturn(0);
        logMock.when(() -> Log.w(anyString(), anyString())).thenReturn(0);
        logMock.when(() -> Log.w(anyString(), anyString(), any())).thenReturn(0);
        logMock.when(() -> Log.e(anyString(), anyString())).thenReturn(0);
        logMock.when(() -> Log.e(anyString(), anyString(), any())).thenReturn(0);
    }

    @After
    public void tearDown() throws Exception {
        new UserRepository(context, mock(FirebaseFirestore.class)).clearCache();
        if (logMock != null) {
            logMock.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    public void updateQuizProgressWithTransaction_updatesProgress_andCallsOnSuccess() throws Exception {
        String uid = "u1";
        String planId = "p1";
        String quizId = "q1";

        FirebaseFirestore db = mock(FirebaseFirestore.class);
        CollectionReference users = mock(CollectionReference.class);
        DocumentReference userDoc = mock(DocumentReference.class);
        CollectionReference plans = mock(CollectionReference.class);
        DocumentReference planRef = mock(DocumentReference.class);
        DocumentSnapshot planSnapshot = mock(DocumentSnapshot.class);
        Transaction transaction = mock(Transaction.class);

        when(db.collection("users")).thenReturn(users);
        when(users.document(uid)).thenReturn(userDoc);
        when(userDoc.collection("plans")).thenReturn(plans);
        when(plans.document(planId)).thenReturn(planRef);
        when(transaction.get(planRef)).thenReturn(planSnapshot);

        StudyPlan plan = new StudyPlan(planId, "Plan");
        Quiz quiz = new Quiz();
        quiz.id = quizId;
        quiz.progreso = new QuizProgress();
        plan.quizzes.add(quiz);

        Map<String, Object> planData = toMap(plan);
        when(planSnapshot.exists()).thenReturn(true);
        when(planSnapshot.getData()).thenReturn(planData);

        java.util.concurrent.atomic.AtomicReference<Map<String, Object>> planCapture = new java.util.concurrent.atomic.AtomicReference<>();
        doAnswer(inv -> {
            planCapture.set(inv.getArgument(1));
            return null;
        }).when(transaction).set(eq(planRef), any(Map.class), eq(SetOptions.merge()));

        Task<Void> successTask = mock(Task.class);
        when(successTask.addOnSuccessListener(any())).thenAnswer(inv -> {
            OnSuccessListener<Void> listener = inv.getArgument(0);
            listener.onSuccess(null);
            return successTask;
        });
        when(successTask.addOnFailureListener(any())).thenReturn(successTask);

        when(db.runTransaction(any())).thenAnswer(invocation -> {
            Transaction.Function<Void> fn = invocation.getArgument(0);
            fn.apply(transaction);
            return successTask;
        });

        UserRepository repo = Mockito.spy(new UserRepository(context, db));
        doAnswer(inv -> {
            Runnable r = inv.getArgument(1);
            r.run();
            return null;
        }).when(repo).recomputeAndUpdateUserStats(eq(uid), any(Runnable.class), any());

        QuizProgress newProgress = new QuizProgress();
        newProgress.preguntasRespondidas = 5;
        newProgress.preguntasCorrectas = 4;
        newProgress.preguntasIncorrectas = 1;
        newProgress.currentIndex = 5;
        newProgress.nota = 80;
        newProgress.lastUpdatedMillis = 123L;

        AtomicBoolean success = new AtomicBoolean(false);
        repo.updateQuizProgressWithTransaction(uid, planId, quizId, newProgress, v -> success.set(true), e -> fail("Should not fail: " + e));

        assertThat(success.get()).isTrue();
        Map<String, Object> updatedPlan = planCapture.get();
        List<Map<String, Object>> quizzes = (List<Map<String, Object>>) updatedPlan.get("quizzes");
        Map<String, Object> quizMap = quizzes.get(0);
        Map<String, Object> progressMap = (Map<String, Object>) quizMap.get("progreso");

        assertThat(progressMap.get("preguntasRespondidas")).isEqualTo(5.0);
        assertThat(progressMap.get("preguntasCorrectas")).isEqualTo(4.0);
        assertThat(progressMap.get("preguntasIncorrectas")).isEqualTo(1.0);
        assertThat(progressMap.get("currentIndex")).isEqualTo(5.0);
        assertThat(progressMap.get("nota")).isEqualTo(80.0);
        assertThat((Double) progressMap.get("lastUpdatedMillis")).isGreaterThan(0.0);
    }

    @Test
    public void refreshPlanIfNeeded_fallsBackToStaleCacheOnFailure() throws Exception {
        FirebaseFirestore db = mock(FirebaseFirestore.class);
        UserRepository repo = Mockito.spy(new UserRepository(context, db));

        StudyPlan cachedPlan = new StudyPlan("p2", "cached");
        repo.putCachedPlanForTest("u1", "p2", cachedPlan, System.currentTimeMillis() - (6 * 60 * 1000));

        doAnswer(inv -> {
            OnFailureListener onFailure = inv.getArgument(3);
            onFailure.onFailure(new RuntimeException("network"));
            return null;
        }).when(repo).getPlan(eq("u1"), eq("p2"), any(), any());

        AtomicInteger calls = new AtomicInteger();
        repo.refreshPlanIfNeeded("u1", "p2", plan -> {
            calls.incrementAndGet();
            assertThat(plan).isNotNull();
            assertThat(plan.nombre).isEqualTo("cached");
        }, e -> fail("Should have fallen back to stale cache"));

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    public void resetQuizProgress_setsDefaults_andCallsOnSuccess() throws Exception {
        String uid = "u3";
        String planId = "plan3";
        String quizId = "quiz3";

        FirebaseFirestore db = mock(FirebaseFirestore.class);
        CollectionReference users = mock(CollectionReference.class);
        DocumentReference userDoc = mock(DocumentReference.class);
        CollectionReference plans = mock(CollectionReference.class);
        DocumentReference planRef = mock(DocumentReference.class);
        DocumentSnapshot planSnapshot = mock(DocumentSnapshot.class);
        Transaction transaction = mock(Transaction.class);

        when(db.collection("users")).thenReturn(users);
        when(users.document(uid)).thenReturn(userDoc);
        when(userDoc.collection("plans")).thenReturn(plans);
        when(plans.document(planId)).thenReturn(planRef);
        when(transaction.get(planRef)).thenReturn(planSnapshot);

        StudyPlan plan = new StudyPlan(planId, "Plan 3");
        Quiz quiz = new Quiz();
        quiz.id = quizId;
        QuizProgress progress = new QuizProgress();
        progress.preguntasRespondidas = 10;
        progress.preguntasCorrectas = 7;
        progress.preguntasIncorrectas = 3;
        progress.currentIndex = 10;
        progress.nota = 90;
        progress.lastUpdatedMillis = 999L;
        quiz.progreso = progress;
        plan.quizzes.add(quiz);

        Map<String, Object> planData = toMap(plan);
        when(planSnapshot.exists()).thenReturn(true);
        when(planSnapshot.getData()).thenReturn(planData);

        java.util.concurrent.atomic.AtomicReference<Map<String, Object>> planCapture = new java.util.concurrent.atomic.AtomicReference<>();
        doAnswer(inv -> {
            planCapture.set(inv.getArgument(1));
            return null;
        }).when(transaction).set(eq(planRef), any(Map.class), eq(SetOptions.merge()));

        Task<Void> successTask = mock(Task.class);
        when(successTask.addOnSuccessListener(any())).thenAnswer(inv -> {
            OnSuccessListener<Void> listener = inv.getArgument(0);
            listener.onSuccess(null);
            return successTask;
        });
        when(successTask.addOnFailureListener(any())).thenReturn(successTask);

        when(db.runTransaction(any())).thenAnswer(invocation -> {
            Transaction.Function<Void> fn = invocation.getArgument(0);
            fn.apply(transaction);
            return successTask;
        });

        UserRepository repo = Mockito.spy(new UserRepository(context, db));
        doAnswer(inv -> {
            Runnable r = inv.getArgument(1);
            r.run();
            return null;
        }).when(repo).recomputeAndUpdateUserStats(eq(uid), any(Runnable.class), any());

        AtomicBoolean success = new AtomicBoolean(false);
        repo.resetQuizProgress(uid, planId, quizId, v -> success.set(true), e -> fail("Should not fail"));

        assertThat(success.get()).isTrue();
        Map<String, Object> updatedPlan = planCapture.get();
        List<Map<String, Object>> quizzes = (List<Map<String, Object>>) updatedPlan.get("quizzes");
        Map<String, Object> quizMap = quizzes.get(0);
        Map<String, Object> progressMap = (Map<String, Object>) quizMap.get("progreso");

        assertThat(progressMap.get("preguntasRespondidas")).isEqualTo(0.0);
        assertThat(progressMap.get("preguntasCorrectas")).isEqualTo(0.0);
        assertThat(progressMap.get("preguntasIncorrectas")).isEqualTo(0.0);
        assertThat(progressMap.get("currentIndex")).isEqualTo(0.0);
        assertThat(progressMap.get("nota")).isEqualTo(-1.0);
        assertThat((Double) progressMap.get("lastUpdatedMillis")).isGreaterThan(0.0);
    }

    private Map<String, Object> toMap(StudyPlan plan) {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        Type mapType = new com.google.gson.reflect.TypeToken<Map<String, Object>>() {}.getType();
        return gson.fromJson(gson.toJson(plan), mapType);
    }

}
