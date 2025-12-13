# Política de Refresco de Caché EstudiaApp

## 📋 Descripción General

EstudiaApp implementa una **política de caché de dos capas** para optimizar escalabilidad y experiencia del usuario:

1. **TTL-Based Cache**: Cache en memoria con expiración automática (5 minutos)
2. **Real-Time Listeners**: Snapshot listeners opcionales para sincronización en tiempo real

Este documento describe la arquitectura, uso y mejores prácticas para el sistema de caché.

---

## 🏗️ Arquitectura

### CachedPlan: Wrapper con Metadata

```java
private static class CachedPlan {
    final StudyPlan plan;              // Datos actuales del plan
    final long cachedAtMillis;         // Timestamp de cacheado (System.currentTimeMillis())
    final long ttlMillis;              // Time To Live (5 minutos por defecto)
    
    CachedPlan(StudyPlan plan, long cachedAtMillis) {
        this(plan, cachedAtMillis, DEFAULT_CACHE_TTL_MILLIS);
    }
    
    CachedPlan(StudyPlan plan, long cachedAtMillis, long ttlMillis) {
        this.plan = plan;
        this.cachedAtMillis = cachedAtMillis;
        this.ttlMillis = ttlMillis;
    }
    
    // Verificar si entry ha expirado
    boolean isExpired() {
        long age = System.currentTimeMillis() - cachedAtMillis;
        return age > ttlMillis;
    }
    
    // Edad en segundos (para logging)
    long getAgeSeconds() {
        return (System.currentTimeMillis() - cachedAtMillis) / 1000;
    }
}
```

### PLAN_CACHE: Thread-Safe Storage

```java
// Thread-safe ConcurrentHashMap para acceso concurrente desde múltiples Activities
private static final ConcurrentHashMap<String, CachedPlan> PLAN_CACHE = new ConcurrentHashMap<>();

// Cache key format: "uid::planId" (ejemplo: "user123::U00001")
String cacheKey = uid + "::" + planId;
```

### TTL Configuration

```java
private static final long DEFAULT_CACHE_TTL_MILLIS = 5 * 60 * 1000;  // 5 minutes

// En futuro: integrable con Firebase Remote Config
// public static long getTTL() {
//     return FirebaseRemoteConfig.getInstance().getLong("PLAN_CACHE_TTL_SECONDS") * 1000;
// }
```

---

## 🔄 Métodos Principales

### 1. getPlan() - Fetch con Cache TTL

**Propósito**: Obtener un plan individual respetando caché con expiración

**Lógica**:
1. Check cache por key `uid::planId`
2. Si existe y NO ha expirado → **SERVE IMMEDIATELY** (cache HIT)
3. Si existe pero HA expirado → **REMOVE** y fetch desde Firestore (cache EXPIRED)
4. Si NO existe → **FETCH** desde Firestore (cache MISS)
5. Cache result con timestamp actual

**Ejemplo de uso**:
```java
userRepository.getPlan("user123", "U00001", 
    plan -> {
        // Usar plan
    }, 
    error -> {
        // Manejar error
    });
```

**Log output**:
```
Cache HIT for U00001 (age: 120s)
Cache MISS for U00001
Cache EXPIRED for U00001 (age: 305s)
Cached plan U00001 with TTL=300s
```

### 2. getQuiz() - Quiz Lookup con Cache TTL

**Propósito**: Obtener un quiz específico desde un plan cacheado

**Lógica**:
1. Check cache plan por key `uid::planId`
2. Si plan está en cache y NO expirado → buscar quiz en plan cacheado
3. Si no encontrado en cache → call `getPlan()` con fallback
4. `getPlan()` maneja caching

**Ejemplo de uso**:
```java
userRepository.getQuiz("user123", "U00001", "QUIZ001",
    quiz -> {
        // Usar quiz
    },
    error -> {
        // Manejar error
    });
```

### 3. refreshPlanIfNeeded() - Smart Refresh con Fallback

**Propósito**: Refresh condicional que respeta TTL y fallback offline

**Lógica**:
1. Check cache TTL
2. Si cache fresco (< 5 min) → **SERVE** sin refetch
3. Si cache expirado → **FETCH** desde Firestore
4. Si error de red → **FALLBACK** a cache stale (if available)
5. Si no hay cache disponible → **FAIL**

**Ejemplo de uso** (típico en onResume()):
```java
userRepository.refreshPlanIfNeeded("user123", "U00001",
    plan -> {
        // Plan actualizado o servido del caché
        populateUI(plan);
    },
    error -> {
        // Error crítico: no hay cache fallback
        showErrorDialog(error);
    });
```

**Log output**:
```
Refresh skipped for U00001 (cache fresh, age: 45s)
Refreshing plan U00001 (cache expired or missing)
Network error refreshing U00001, falling back to stale cache (age: 310s)
```

### 4. registerPlanListener() - Real-Time Sync

**Propósito**: Registrar listener para cambios en Firestore y update automático de caché

**Retorna**: `ListenerRegistration` que DEBE ser removido en `onDestroy()`

**Lógica**:
1. Agregar `addSnapshotListener` para `users/{uid}/plans/{planId}`
2. En cada snapshot → parse y update cache con nuevo timestamp
3. Call callback `onUpdate()` para refresh UI
4. Manejar errores silenciosamente

**Ejemplo de uso** (PlanDetailsActivity):
```java
// En onCreate()
planListener = userRepository.registerPlanListener("user123", "U00001", 
    updatedPlan -> {
        // UI reflex con datos actualizados en tiempo real
        populateUIWithPlan(updatedPlan);
    });

// En onDestroy() - IMPORTANTE: cleanup
@Override
protected void onDestroy() {
    super.onDestroy();
    if (planListener != null) {
        planListener.remove();
        planListener = null;
    }
}
```

**Log output**:
```
Snapshot listener updated cache for U00001
Snapshot listener error for U00001: ...
```

### 5. clearCache() - Full Cache Clear

**Propósito**: Limpiar toda la caché (e.g., logout)

**Ejemplo de uso**:
```java
userRepository.clearCache();  // En SignOutActivity o logout
```

**Log output**:
```
Cache cleared manually (5 entries removed)
```

### 6. invalidatePlanCache() - Targeted Invalidation

**Propósito**: Remover un plan específico de caché

**Ejemplo de uso**:
```java
userRepository.invalidatePlanCache("user123", "U00001");  // Plan eliminado
```

**Log output**:
```
Invalidated cache for U00001 (was 120s old)
```

---

## 📱 Integración con Activities

### PlanDetailsActivity: Real-Time + TTL

```java
public class PlanDetailsActivity extends BaseActivity {
    private ListenerRegistration planListener;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plan_details);
        
        String uid = getAuth().getUid();
        String planId = getIntent().getStringExtra(Constants.EXTRA_PLAN_ID);
        
        // Load inicial (respeta cache TTL)
        refreshUI();
        
        // Register listener para real-time updates
        if (uid != null) {
            planListener = userRepository.registerPlanListener(uid, planId, 
                updatedPlan -> populateUIWithPlan(updatedPlan));
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Refresh UI - respeta TTL (no refetch si < 5 min)
        refreshUI();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Cleanup listener
        if (planListener != null) {
            planListener.remove();
        }
    }
    
    private void refreshUI() {
        String uid = getAuth().getUid();
        userRepository.refreshPlanIfNeeded(uid, planId,
            plan -> populateUIWithPlan(plan),
            error -> showErrorDialog(error));
    }
}
```

**Comportamiento esperado**:
- Primera carga: fetch desde Firestore → cache
- Usuario sale y vuelve < 5 min: serve del cache (sin red)
- Usuario deja abierto > 5 min: listener trae cambios en tiempo real
- Cambio en otro dispositivo: listener actualiza instántaneamente

### QuizActivity: Smart Refresh

```java
public class QuizActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadQuizData();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Refresh on return - respeta TTL
        loadQuizData();
    }
    
    private void loadQuizData() {
        String uid = getAuth().getUid();
        userRepository.refreshPlanIfNeeded(uid, planId,
            plan -> {
                Quiz quiz = findQuizInPlan(plan, quizId);
                initializeWithQuiz(quiz);
            },
            error -> showErrorAndLoadLocal());
    }
}
```

**Comportamiento esperado**:
- Inicio: fetch plan si cache expirado, serve quiz
- Usuario regresa < 5 min: usa cache (sin red) para mostrar quiz
- Respuestas guardadas en otro dispositivo: refreshed en próximo refresh

### FlashcardActivity: Same Pattern

```java
private void loadFlashcardData() {
    userRepository.refreshPlanIfNeeded(uid, planId,
        plan -> {
            this.plan = plan;
            adapter.updateItems(plan.flashcards);
        },
        error -> showErrorAndLoadLocal());
}
```

---

## 📊 Comportamiento del Caché

### Timeline Típico

```
T=0s      onCreate()
          ├─ Cache MISS para U00001
          ├─ Fetch desde Firestore
          └─ Store en cache con timestamp 0s

T=45s     Usuario en app
          ├─ onResume()
          ├─ Check TTL: 45s < 300s (OK)
          └─ SERVE del cache sin red

T=305s    (después de 5+ min)
          ├─ onResume()
          ├─ Check TTL: 305s > 300s (EXPIRED)
          ├─ Fetch desde Firestore
          └─ Update cache con nuevo timestamp

T=305s+   Real-time listener activo
          ├─ Cambio en Firestore (otro dispositivo)
          ├─ Snapshot listener triggered
          ├─ Parse y update cache
          └─ onUpdate() callback refresh UI INSTANTÁNEAMENTE
```

### Escenarios Especiales

#### ✅ Cambio en otro dispositivo (30s después de fetch)

```
T=0s      Device A: fetch plan → cache (U00001, age=0s)
T=30s     Device B: actualiza plan.nombre en Firestore
          Device A listener snapshot (< 1s después)
          ├─ Parse nuevo nombre
          ├─ Update cache timestamp
          └─ UI refresh con nombre nuevo
```

#### ✅ Offline entre 2-5 min

```
T=0s      Fetch plan → cache
T=180s    Desconecta red, cierra app
T=200s    Abre app sin red
          ├─ onResume() → refreshPlanIfNeeded()
          ├─ Cache check: 200s < 300s (OK)
          └─ SERVE del cache sin intentar red
```

#### ✅ Offline > 5 min, luego vuelve conexión

```
T=0s      Fetch plan → cache
T=400s    Sin red, cache expirado (400s > 300s)
          ├─ onResume() → refreshPlanIfNeeded()
          ├─ Check TTL: expirado
          ├─ Try fetch de Firestore → FALLA (sin red)
          └─ FALLBACK a cache stale (UI muestra datos de 400s atrás)
          
T=410s    Conexión vuelve
          ├─ Listener se reconecta
          └─ Snapshot trae datos frescos → cache update + UI refresh
```

---

## 🔍 Logging y Debugging

### Log Tags

Todos los logs de caché usan tag `"UserRepository"`:

```java
Log.d("UserRepository", "Cache HIT for U00001 (age: 120s)");
Log.d("UserRepository", "Cache MISS for U00001");
Log.d("UserRepository", "Cache EXPIRED for U00001 (age: 305s)");
Log.w("UserRepository", "Network error refreshing U00001, falling back to stale cache");
```

### Enable Verbose Logging

En `UserRepository`, logs son automáticos para:
- HIT/MISS/EXPIRED states
- Fetch events con TTL
- Listener events (updated, errors)
- Manual invalidation

### Debug Checklist

```bash
# Ver todos los logs de UserRepository
adb logcat | grep "UserRepository"

# Ver específicamente cache events
adb logcat | grep "Cache"

# Ver listener events
adb logcat | grep "Snapshot"
```

---

## 🚀 Optimizaciones y Future Work

### Fase 3: Firebase Remote Config Integration

Hacer TTL configurable dinámicamente:

```java
public static long getTTLMillis() {
    FirebaseRemoteConfig config = FirebaseRemoteConfig.getInstance();
    long ttlSeconds = config.getLong("PLAN_CACHE_TTL_SECONDS");  // default 300
    return ttlSeconds * 1000;
}
```

**Remote Config setup**:
```
Parameter: PLAN_CACHE_TTL_SECONDS
Default: 300 (5 min)
Min: 60 (1 min)
Max: 1800 (30 min)
```

### Fase 3: Cache Prefetch Strategy

Prefetch planes cuando `MainActivity` carga:

```java
// MainActivity.onCreate()
List<StudyPlan> plans = loadPlans();
for (StudyPlan plan : plans) {
    userRepository.getPlan(uid, plan.id, p -> {}, e -> {});  // async preload
}
```

### Fase 4: Analytics Integration

Track cache effectiveness:

```java
private static class CacheStats {
    int hits = 0, misses = 0, expires = 0;
    
    void recordHit() {
        hits++;
        Analytics.log("cache_hit", new Bundle());
    }
}
```

---

## ⚠️ Caveats y Limitaciones

### Caché sincrónico

- Cache checks son sincrónicas (sub-ms)
- Fetch desde Firestore es async
- No hay "wait for fresh data" - serve stale si hay errores

### TTL fijo

- TTL actualmente fijo en 5 minutos (no configurable runtime)
- Future: Remote Config para ajuste dinámico

### No hay cache pre-warming

- Cache llena bajo demanda, no se prewarma al login
- Future: prefetch en `MainActivity` onCreate

### Listeners no automáticos

- Solo PlanDetailsActivity registra listeners
- Quiz/Flashcard confían en TTL refresh
- Future: listeners en todas las Activities con datos críticos

---

## 📚 Referencias

- **Firebase Firestore Offline**: https://firebase.google.com/docs/firestore/manage-data/enable-offline
- **Real-time Updates**: https://firebase.google.com/docs/firestore/query-data/listen
- **Android Concurrency**: https://developer.android.com/guide/components/processes-and-threads
- **Memory Management**: https://developer.android.com/topic/performance/memory

---

## 📞 Contacto

Para preguntas o issues sobre la política de caché:
- Abrir GitHub Issue con tag `[Cache]`
- Documentación actualizada en `CACHE_POLICY.md` (este archivo)
