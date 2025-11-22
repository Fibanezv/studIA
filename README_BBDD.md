# README_BBDD — Integración y diseño de la Base de Datos (Firebase)

Fecha: 2025-11-10

Resumen
-------
Este documento recoge el plan técnico y operativo para integrar un sistema realista de signup/login y gestión de usuarios en EstudiaApp usando Firebase. Incluye:

- Servicios recomendados (Firebase Auth, Firestore, Storage)
- Esquema de datos y campos necesarios
- Reglas de seguridad y validaciones
- Flujos de signup/login y manejo de sesión
- Estrategia de migración desde JSON locales (StorageHelper)
- Checklist de tareas concretas para la implementación

Este README pretende ser la referencia para los desarrolladores que vayan a implementar la integración con Firebase.

1) Servicios recomendados
------------------------
- Firebase Authentication — autenticación (email/password, Google Sign-In opcional).
- Cloud Firestore — almacenamiento estructurado de usuarios, planes, progreso, quizzes y flashcards.
- Firebase Storage — almacenamiento de imágenes (foto de perfil) u otros ficheros (opcional).
- Firebase Emulator Suite — pruebas locales de reglas y flows.

2) Esquema de datos recomendado (Firestore)
-----------------------------------------

Colecciones principales:

- `users` (document id = uid de Firebase Auth)
- `plans` (document id = planId)
- `publicPlans` (opcional, para planes compartidos públicamente)

Documento `users/{uid}` (campos mínimos y recomendados):

- `uid`: string
- `email`: string
- `displayName`: string
- `photoUrl`: string | null (URL Storage)
- `provider`: string ("password" | "google" ...)
- `emailVerified`: boolean
- `createdAt`: timestamp
- `lastLoginAt`: timestamp
- `language`: string ("es", "en", ...)
- `level`: string ("A1","B2",...)
- `membership`: map { status, planId, startAt, expiresAt }
- `preferences`: map { quizLimitDefault, flashcardLimitDefault, hotTopicLimitDefault, notificationsEnabled }
- `stats`: map { totalFlashcards, studiedFlashcards, totalQuizzes, completedQuizzes, totalQuestionsAnswered, correctAnswers }
- `plansSummary`: map (opcional) planId -> { progress, lastUpdated }
- `roles`: array<string> e.g., ["user"]
- `migratedFromLocal`: boolean
- `lastDevice`: map { model, osVersion, appVersion } (opcional)

Documento `plans/{planId}` (campos principales):

- `planId`: string
- `ownerUid`: string
- `title`: string
- `description`: string
- `language`: string
- `level`: string
- `isPublic`: boolean
- `createdAt`, `updatedAt`: timestamp
- `counts`: map { quizzes, flashcards, hotTopics }
- `progressSummary`: map { overall, flashcards, quizzes } (opcional)
- `metadata`: map { sourceFileName, tags }

Sub-colecciones recomendadas:

- `plans/{planId}/quizzes/{quizId}`
- `plans/{planId}/flashcards/{flashcardId}`
- `plans/{planId}/hotTopics/{hotTopicId}`

Progresos de usuario (opciones de modelado):

- Opción A (recomendada): guardar progreso por usuario en `users/{uid}/plansProgress/{planId}` — lectura rápida desde la vista de planes del usuario.
- Opción B: `plans/{planId}/progress/{uid}` — útil si los planes son compartidos entre usuarios y el propietario necesita ver progreso de otros.

Ejemplo de documento de progreso (`users/{uid}/plansProgress/{planId}`):

- `lastUpdated`: timestamp
- `quizzesCompleted`: number
- `flashcardsStudied`: number
- `overallPercent`: number

3) Reglas básicas de seguridad (ejemplo)
--------------------------------------

Archivo `firestore.rules` (ejemplo):

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }

    match /plans/{planId} {
      allow read: if resource.data.isPublic == true || (request.auth != null && request.auth.uid == resource.data.ownerUid);
      allow create: if request.auth != null && request.resource.data.ownerUid == request.auth.uid;
      allow update, delete: if request.auth != null && request.auth.uid == resource.data.ownerUid;
    }

    match /plans/{planId}/{subCollection}/{docId} {
      allow read: if get(/databases/$(database)/documents/plans/$(planId)).data.isPublic == true
                  || (request.auth != null && request.auth.uid == get(/databases/$(database)/documents/plans/$(planId)).data.ownerUid);
      allow write: if request.auth != null && request.auth.uid == get(/databases/$(database)/documents/plans/$(planId)).data.ownerUid;
    }

    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```

Notas:
- Validar tipos y longitudes donde sea posible: ej. `request.resource.data.title is string && request.resource.data.title.size() <= 200`.
- Evitar permitir que un cliente fije `ownerUid` distinto al `request.auth.uid`.

4) Flujos (Signup / Login)
---------------------------

Signup (cliente Android - pasos):

1. Recolectar: email, password, displayName (opcional), idioma, nivel, foto (opcional).
2. Validar en cliente: formato email, contraseña (mínimo 8 caracteres, política), displayName (2..80).
3. Llamar `createUserWithEmailAndPassword(email, password)`.
4. En éxito: obtener `uid` y crear `users/{uid}` en Firestore con `createdAt`, `language`, `level`, `migratedFromLocal=false`, `preferences` por defecto.
5. (Opcional) enviar verificación de email.

Login:

- `signInWithEmailAndPassword(email, password)`; actualizar `users/{uid}.lastLoginAt = serverTimestamp()`; usar `AuthStateListener`.
- Para Google Sign-In: obtener `idToken` y llamar `signInWithCredential`.

Errores a manejar (cliente):
- `FirebaseAuthUserCollisionException` (email en uso)
- `FirebaseAuthWeakPasswordException` (contraseña débil)
- `FirebaseAuthInvalidCredentialsException` (email inválido)

5) Persistencia offline y sincronización
--------------------------------------

Habilitar cache offline (Firestore) para mejor UX:

```java
FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
        .setPersistenceEnabled(true)
        .build();
FirebaseFirestore.getInstance().setFirestoreSettings(settings);
```

Estrategia de sincronización:
- Hacer cambios locales y escribir a Firestore; manejar conflictos por timestamp/lastWriterWins o políticas de merge.
- Para migraciones, ejecutar la subida de forma idempotente y marcar `migratedFromLocal=true`.

6) Migración desde JSON locales (StorageHelper)
----------------------------------------------

Proceso recomendado:

1. Detectar primer login del usuario (o botón explícito "Migrar mis datos").
2. Leer JSON locales por `StorageHelper`.
3. Para cada plan JSON:
   - Generar `planId` nuevo o reutilizar si hay mapeo.
   - Crear `plans/{planId}` con `ownerUid = uid`.
   - Poblar subcolecciones `quizzes`, `flashcards`, `hotTopics`.
4. Verificar subida completa; si todo ok, setear `users/{uid}.migratedFromLocal = true`.
5. Mantener copia local de seguridad hasta verificación.

Consideraciones:
- Ejecutar la migración en background y mostrar progreso en UI.
- Ser idempotente: en caso de fallo, no duplicar recursos.

7) Dependencias y configuración en Android
-----------------------------------------

1. Añadir plugin Google Services en `build.gradle` (project):

```gradle
buildscript {
  dependencies {
    classpath 'com.google.gms:google-services:4.3.15'
  }
}
```

2. `app/build.gradle` (ejemplo):

```gradle
apply plugin: 'com.android.application'
apply plugin: 'com.google.gms.google-services' // al final

dependencies {
  implementation platform('com.google.firebase:firebase-bom:32.2.0')
  implementation 'com.google.firebase:firebase-auth'
  implementation 'com.google.firebase:firebase-firestore'
  implementation 'com.google.firebase:firebase-storage' // opcional
  implementation 'com.google.android.gms:play-services-auth:20.5.0' // si Google Sign-In
}
```

3. Añadir `google-services.json` en `app/` (desde Firebase Console).

4. Inicializar Firestore (opcionalmente) y habilitar persistence.

8) Tests y Emulator Suite
-------------------------

- Configurar Firebase Emulator Suite para Auth y Firestore.
- Escribir tests de reglas (con nodejs o firebase-tools) y pruebas de integración para flows de login/signup y accesos a documentos.

9) Checklist de implementación (orden recomendado)
------------------------------------------------

Alta prioridad (MVP):

1. Crear proyecto Firebase y obtener `google-services.json`.
2. Añadir dependencias Firebase y construir proyecto.
3. Habilitar Authentication (email/password).
4. Implementar pantalla signup con validaciones y creación de `users/{uid}`.
5. Implementar pantalla login y `AuthStateListener`.
6. Actualizar `lastLoginAt` tras login y leer `users/{uid}`.

Prioridad media:

7. Diseñar schema Firestore completo y escribir reglas básicas.
8. Implementar CRUD de `plans` en Firestore y sincronización.
9. Habilitar offline persistence y pruebas de sincronización.
10. Implementar migración desde JSON locales.

Prioridad baja / opcional:

11. Integrar Google Sign-In y Firebase Storage para fotos.
12. Implementar roles/claims y administración.
13. Tests automáticos en Emulator Suite.

10) Buenas prácticas y seguridad
--------------------------------

- No almacenar contraseñas en Firestore.
- Validar datos tanto en cliente como en Security Rules.
- Implementar eliminación de datos (GDPR) y exportación si es necesario.
- Monitorizar errores con Crashlytics y métricas con Analytics.

11) Estimación de esfuerzo (orientativa)
--------------------------------------

- Auth + signup/login + users doc: 1–2 días.
- CRUD de plans + migración: 2–4 días.
- Offline, testing y reglas: 1–2 días.
- QA y producción: 1–2 días.

12) Siguientes pasos que puedo ejecutar ahora
-------------------------------------------

Indica cuál quieres que implemente primero y lo hago:

- Opción A: Añadir dependencias Firebase y preparar el proyecto (CREAR COMMIT con cambios gradle + placeholder google-services.json).
- Opción B: Implementar pantallas Signup/Login y código Java para Auth + creación de `users/{uid}`.
- Opción C: Generar utilitario de migración desde `StorageHelper` a Firestore.

---

Este README_BBDD queda guardado en el repositorio para referencia y seguimiento. Si quieres, puedo crear automáticamente el commit con este archivo y empezar a implementar la opción que prefieras.
