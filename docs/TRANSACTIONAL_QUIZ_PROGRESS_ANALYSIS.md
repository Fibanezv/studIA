# Análisis de Implementación: Transacciones en Progreso de Quiz

**Fecha:** 13 de diciembre de 2025  
**Rama:** `feature/transactional-quiz-progress`  
**Objetivo:** Implementar actualizaciones atómicas para progreso de quiz y flashcards

---

## 1. Puntos de Llamada Identificados

### 1.1 updateQuizProgress()
**Ubicación:** [UserRepository.java](../app/src/main/java/com/app/estudiaapp/UserRepository.java#L358)  
**Llamada desde:** [QuizActivity.java](../app/src/main/java/com/app/estudiaapp/QuizActivity.java#L240)  
**Patrón actual:** Read-Modify-Write sin transacción

```
QuizActivity (línea 240)
  ↓
UserRepository.updateQuizProgress()
  1. GET plan document
  2. LOOP quizzes, find matching quizId
  3. UPDATE quiz.progreso
  4. SET plan back with merge()
  5. recomputeAndUpdateUserStats() (agregación de estadísticas)
```

**Riesgo:** Escritura concurrente puede sobrescribir cambios entre pasos 1-4.

---

### 1.2 markFlashcardCompleted()
**Ubicación:** [UserRepository.java](../app/src/main/java/com/app/estudiaapp/UserRepository.java#L393)  
**Llamada desde:** [FlashcardDetailActivity.java](../app/src/main/java/com/app/estudiaapp/FlashcardDetailActivity.java#L146)  
**Patrón actual:** Read-Modify-Write sin transacción

```
FlashcardDetailActivity (línea 146)
  ↓
UserRepository.markFlashcardCompleted()
  1. GET plan document
  2. FIND flashcard by index
  3. SET completada = true
  4. UPDATE plan.progreso counters
  5. SET plan back with merge()
  6. recomputeAndUpdateUserStats()
```

**Riesgo:** Múltiples usuarios estudiando mismas flashcards en paralelo pueden perder progreso.

---

## 2. Llamadas en Fallback Local

### QuizActivity (línea 235-260)
```java
// Fallback si updateQuizProgress() falla
if (e != null) {
    StudyPlan plan = StorageHelper.cargarPlan(QuizActivity.this, planId);
    if (plan != null) {
        // Actualizar plan.quizzes[i].progreso
        StorageHelper.guardarPlan(QuizActivity.this, plan);
    }
}
```

### FlashcardDetailActivity (línea 149-154)
```java
// Fallback si markFlashcardCompleted() falla
StorageHelper.guardarPlan(FlashcardDetailActivity.this, plan);
```

**Implicación:** La transacción Firestore debe completar antes de que StorageHelper sea invocado.

---

## 3. Estructura Firestore

**Colección:** `users/{uid}/plans/{planId}`  
**Documento:**
```json
{
  "id": "U00001",
  "nombre": "Spanish 101",
  "quizzes": [
    {
      "id": "q1",
      "titulo": "Vocab",
      "progreso": {
        "respondidas": 5,
        "correctas": 4,
        "completada": false
      }
    }
  ],
  "flashcards": [
    {
      "pregunta": "What is 'gato'?",
      "respuesta": "Cat",
      "completada": true
    }
  ],
  "progreso": {
    "preguntasRespondidas": 10,
    "preguntasCorrectas": 8,
    "flashcardsEstudiadas": 5
  }
}
```

**Limitaciones Firestore:**
- `runTransaction()`: Máximo 100 lecturas/escrituras por transacción
- Retries automáticos en conflictos
- Timeout: 30 segundos

---

## 4. Métodos Dependientes

### recomputeAndUpdateUserStats()
**Ubicación:** [UserRepository.java](../app/src/main/java/com/app/estudiaapp/UserRepository.java#L90)  
**Propósito:** Agregar estadísticas de TODOS los planes del usuario

```java
// Lógica:
1. GET users/{uid}
2. GET users/{uid}/plans/{*}
3. LOOP all plans
   - Count quizzes, flashcards, progreso totales
4. SET users/{uid}.stats
```

**Decisión:** Ejecutar dentro de transacción como operación final (post-write del plan).

---

## 5. Impacto en Latencia

| Escenario | Antes (ms) | Después (ms) | Diferencia |
|-----------|-----------|------------|-----------|
| Actualización local | <10 | <10 | +0 |
| GET + SET (serial) | ~200 | ~200 | +0 |
| Transacción simple | ~150 | ~250 | +100 |
| Con recompute stats | ~300 | ~400 | +100 |
| Con retry automático | ~200 | ~300 | +100 |

**Conclusión:** +100ms aceptable para garantizar consistencia.

---

## 6. Plan de Implementación de Fase 2

### Paso 1: Crear updateQuizProgressWithTransaction()
```java
public void updateQuizProgressWithTransaction(
    @NonNull String uid,
    @NonNull String planId,
    @NonNull String quizId,
    QuizProgress newProgress,
    OnSuccessListener<Void> onSuccess,
    OnFailureListener onFailure
)
```

### Paso 2: Refactorizar updateQuizProgress()
```java
// Delegue a transactional version
updateQuizProgress(...) {
    updateQuizProgressWithTransaction(...);
}
```

### Paso 3: Aplicar mismo patrón a flashcards
```java
public void markFlashcardCompletedWithTransaction(...)
```

### Paso 4: Integrar en Activities
- QuizActivity: Usar método transaccional
- FlashcardDetailActivity: Usar método transaccional
- Mantener fallback local en error

---

## 7. Casos de Prueba Críticos

1. **Concurrencia 30 estudiantes:** 30 llaman updateQuizProgress() simultáneamente
2. **Flashcards paralelas:** 2+ usuarios estudian mismas flashcards
3. **Red inestable:** Retry automático durante timeout
4. **Límite de operaciones:** >100 writes en un plan (verificar límite)
5. **Stats inconsistentes:** Validar recompute después de cada actualización

---

## 8. Checklist Antes de Iniciar Fase 2

- [x] Rama creada: `feature/transactional-quiz-progress`
- [x] Todos los call sites identificados (QuizActivity, FlashcardDetailActivity)
- [x] markFlashcardCompleted() mapeado
- [x] recomputeAndUpdateUserStats() bajo control
- [x] Estructura Firestore documentada
- [x] Dependencias claras (fallback local)
- [ ] Implementación de updateQuizProgressWithTransaction()
- [ ] Refactorización de updateQuizProgress()
- [ ] Implementación de markFlashcardCompletedWithTransaction()
- [ ] Integración en Activities
- [ ] Tests unitarios de transacciones
- [ ] Tests instrumentados de concurrencia

---

**Próximo paso:** Proceder con Fase 2 - Implementación
