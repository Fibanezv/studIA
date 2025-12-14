# EstudiaApp

> **Aplicación Android de Estudio Inteligente con IA**
> 
> EstudiaApp es una aplicación móvil que permite a los estudiantes generar planes de estudio personalizados, quizzes y flashcards a partir de documentos usando inteligencia artificial.

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![Firebase](https://img.shields.io/badge/Backend-Firebase-orange.svg)](https://firebase.google.com/)
[![OpenAI](https://img.shields.io/badge/AI-OpenAI-blue.svg)](https://openai.com/)

**Última actualización:** 14 de diciembre de 2025  
**Versión:** 1.0 (beta/0.0.1)  
**Repositorio:** [Fibanezv/studIA](https://github.com/Fibanezv/studIA)  
**Últimas funcionalidades:** Resultados detallados de quizzes con layout fijo y validación de respuesta, y overlay de procesamiento mejorado

---

## 📋 Tabla de Contenidos

1. [Características Principales](#-características-principales)
2. [Arquitectura y Tecnologías](#-arquitectura-y-tecnologías)
3. [Estructura del Proyecto](#-estructura-del-proyecto)
4. [Configuración e Instalación](#-configuración-e-instalación)
5. [Uso de la Aplicación](#-uso-de-la-aplicación)
6. [Modelos de Datos](#-modelos-de-datos)
7. [Seguridad y Buenas Prácticas](#-seguridad-y-buenas-prácticas)
8. [Estado Actual del Proyecto](#-estado-actual-del-proyecto)
9. [Roadmap y Tareas Pendientes](#-roadmap-y-tareas-pendientes)
10. [Contribución](#-contribución)
11. [Licencia](#-licencia)

---

## 🚀 Características Principales

### Generación Inteligente de Contenido
- **Creación automática de planes de estudio** a partir de documentos PDF subidos por el usuario
- **Generación de quizzes** con 5 tipologías diferentes:
  - **A, B, C, D**: Opción múltiple con 4 opciones
  - **A, B, C**: Opción múltiple con 3 opciones
  - **True/False**: Verdadero o Falso
  - **Palabras hueccas**: Completar espacios en blanco con 4 opciones de palabras
  - **Combinado**: Mezcla aleatoria de todas las tipologías
- **Flashcards inteligentes** para repaso rápido y eficiente con respuestas detalladas
 - Vista de flashcards en cuadrícula (2 por fila) con controles superiores: Presentación, Aleatorio, Favoritas
- **Hot Topics**: identificación de temas clave del material de estudio (50-75 caracteres)
- **Títulos y descripciones generados con GPT-4o**: Contenido orgánico sin URLs ni metadatos
- **Sistema de reintentos automático**: Hasta 2 intentos si no se genera el contenido completo
- **Validación estricta**: Garantiza consistencia entre tipologías y formatos
- **Personalización avanzada**: control sobre cantidad de quizzes, preguntas, flashcards y temas
- **Orquestación directa en cliente**: `UploadActivity` sube el PDF a Storage y llama la Cloud Function sin WorkManager; asigna planId canónico `U0#####` via transacción en Firestore

### Gestión de Aprendizaje
- **Sistema de progreso y estadísticas** con seguimiento detallado de:
  - Planes de estudio completados
  - Quizzes realizados y puntuaciones
  - Flashcards estudiadas
  - Historial con timestamps
- **Sistema de badges** para gamificación y motivación
- **Biblioteca de planes** con acceso rápido a todo el contenido

### Experiencia de Usuario
- **Interfaz moderna** con Material Design 3
- **Modo oscuro/claro** con transición suave
 - **Aplicación inmediata de tema** (oscuro/claro) al inicio y al cambiar desde perfil, usando Splash para evitar flicker
- **Drawer navigation** centralizado para navegación consistente
- **Autenticación Firebase** con soporte para email/password y Google Sign-In
- **Almacenamiento offline** con sincronización en la nube
- **Exportación de datos** para respaldo personal
- **PlanDetailsActivity mejorado**:
  - Overview Grid con totales de Flashcards, Hot Topics y Quizzes
  - Sección de Progreso con 4 barras (Flashcards, Quizzes Completados, Preguntas Respondidas, Preguntas Acertadas)
  - Progreso oculto automáticamente si todos los valores están al 0%
  - Progress bars con bordes redondeados y porcentaje visible
  - Descripción y Logros como secciones fijas (sin colapsar)
- **QuizActivity mejorado**:
  - Título "Pregunta X" centrado sobre cada pregunta
  - Imagen decorativa (ic_profile_1) entre respuestas y botón
  - Barra de progreso visual con animación
  - Botón "Responder" fijado fuera de la tarjeta de la pregunta
  - Validación: bloquea avance si no se selecciona opción (muestra toast)
- **Quiz items rediseñados**:
  - Botones "Resultados" y "Reiniciar" (90dp, altura automática)
  - Visible solo cuando el quiz está completado al 100%
  - Botón "Resultados" muestra lista detallada de preguntas/respuestas con ✔️/❌
  - Sistema de almacenamiento de respuestas del usuario (userAnswers en QuizProgress)
- **QuizResultsActivity mejorado**:
  - Encabezado simplificado: "Nota" + icono 🎓 y % grande
  - CardView con lista completa scrollable dentro del card (sin corte a 4 ítems)
  - Ítems de preguntas con fondo beige fijo (modo claro/oscuro), mostrando pregunta, respuesta del usuario y correcta (si falló) con ✔️/❌
  - Compatible con quizzes antiguos (solo muestra resumen sin detalles)

## Flashcards: funcionalidades actuales

- **Presentación**: abre la primera flashcard del plan y permite avanzar con controles circulares en la parte trasera (anterior/siguiente).
- **Aleatorio**: abre una flashcard aleatoria; en este modo se ocultan anterior/siguiente y se muestra un botón de recarga (↻) para otra aleatoria sin repetir la actual.
- **Favoritas**:
  - Marca/desmarca una flashcard como favorita desde la parte trasera (botón ⭐ para añadir; botón ❌ para quitar).
  - Navegación por lista de favoritas con anterior/siguiente; al quitar una favorita en este modo se salta a la siguiente favorita.
  - Al terminar la última flashcard en cualquier modo (presentación, aleatorio, favoritas) el botón siguiente vuelve a la lista principal.

### UI de flashcards
- Lista en **grid 2 columnas** con cards compactas estilo baraja.
- En cada item:
  - Título centrado.
  - Icono de estado `checked` en esquina inferior derecha cuando está completada.
  - Icono de favorita en esquina inferior izquierda cuando está marcada como favorita.
- En la vista detalle (parte trasera): **4 FABs circulares** centrados debajo de la respuesta:
  - Anterior (◄) — oculto en la primera.
  - ❌ (quitar de favoritas) — visible solo si es favorita.
  - ⭐ (añadir a favoritas) — visible solo si no es favorita.
  - Siguiente (►) — oculta en modo aleatorio.
  - En modo aleatorio se añade **↻ recargar** para pedir otra flashcard aleatoria.

### Persistencia y sincronización
- **Local**: cambios (completada y favorita) se guardan inmediatamente con `StorageHelper.guardarPlan()`; al reabrir el plan se cargan con `StorageHelper.cargarPlan()`.
- **Firestore**:
  - `markFlashcardCompletedWithTransaction(uid, planId, index)` actualiza el estado de completada y contadores de progreso.
  - `markFlashcardFavoriteWithTransaction(uid, planId, index, favorita)` guarda la marca de favorita de forma atómica e invalida la caché.
  - La actividad de detalle intenta persistir en Firestore cuando hay sesión (`uid`) y siempre hace fallback al guardado local.

## Estado actual de la app
- Build `assembleDebug` exitoso.
- UI de flashcards integrada: controles superiores, grid 2 columnas, detalle con FABs, indicadores en items.
- Navegación completa en modos Presentación, Aleatorio y Favoritas con retorno a lista al finalizar.
- Persistencia local y sincronización a Firestore para completadas y favoritas.
- **Overlay de procesamiento mejorado**:
  - CardView fijo (320x320dp) sin elevación
  - Sistema de estados con cola FIFO y duración mínima de 10s por estado
  - Tres imágenes dinámicas: ic_load_plan_1 (procesando) → ic_load_plan_2 (subiendo) → ic_load_plan_3 (generando)
  - Mensajes de estado centrados debajo de cada imagen
  - Sin progress bar circular, diseño más limpio
- **Formulario de creación mejorado**: 
  - Spinners con texto alineado a la derecha
  - Labels "Total de" en lugar de "Límite de"
  - Botón para remover documento seleccionado (X)
  - Vista previa del nombre del archivo
- **Transiciones Material Motion**:
  - FadeThrough para autenticación (Splash→SignIn, SignIn→Main) y navegación drawer
  - SharedAxis X para flujos de lista→detalle (QuizList→Quiz)
  - MaterialContainerTransform con shared elements para cards→detalles (Main→PlanDetails)
  - Respeto de preferencias de accesibilidad (reduce motion)
  - Helpers centralizados en `BaseActivity` para consistencia
  - Recursos de animación personalizados (fade_in/out, slide_in/out)

---

## 🏗️ Arquitectura y Tecnologías

### Stack Tecnológico

#### Frontend (Android)
- **Lenguaje:** Java (JDK 8+)
- **SDK mínimo:** Android 7.0 (API 24)
- **SDK objetivo:** Android 14 (API 34)
- **Build system:** Gradle 8.3.1
- **UI:** Material Design Components 1.12.0
 - **Estado de datos (quizzes):** Fuente única a través de `UserRepository` con caché in-memory ligero para `StudyPlan` y acceso directo a `Quiz` (`getQuiz`)

#### Backend y Servicios
- **Firebase Authentication** - Gestión de usuarios y autenticación
- **Cloud Firestore** - Base de datos NoSQL en tiempo real
- **Firebase Storage** - Almacenamiento de archivos (PDFs originales)
- **Firebase Cloud Functions** - Procesamiento serverless de generación de planes
  - `generateStudyPlan`: Endpoint principal (respuesta inmediata con planId)
  - `processStudyPlanGeneration`: Trigger de Firestore para generación en background
  - Runtime: Node.js 20, Memory: 8GB, Timeout: 540s
- **Llamada directa desde UploadActivity** - Cloud Functions orchestration sin WorkManager

#### Inteligencia Artificial
- **OpenAI GPT-4o** - Generación de títulos (40 tokens, temp 0.3, max 60 chars)
- **OpenAI GPT-4o** - Generación de descripciones (250 tokens, temp 0.6, max 35 palabras)
- **OpenAI GPT-4o-mini** - Generación de contenido educativo (6000 tokens, temp 0.7)
  - Quizzes con múltiples tipologías
  - Flashcards con respuestas detalladas (120-250 caracteres)
  - Hot Topics con descripciones concisas (50-75 caracteres)
- **Sistema de validación inteligente**: Rechaza contenido inválido y reintenta hasta 2 veces
- **Recuperación de JSON robusta**: Extrae JSON válido de respuestas malformadas
- **JSON Schema Validation** - Respuestas estructuradas y validadas

#### Otras Dependencias Clave
- **Retrofit 2.9.0** - Cliente HTTP
- **Gson 2.10.1** - Serialización JSON
- **OkHttp 4.11.0** - Cliente HTTP avanzado
- **Security Crypto 1.1.0** - Almacenamiento encriptado de API keys
- **MultiDex 2.0.1** - Soporte para aplicaciones grandes

### Arquitectura de Generación de Planes

```
Usuario → UploadActivity → Cloud Function (generateStudyPlan)
                                  ↓
                        [Respuesta inmediata con planId]
                                  ↓
                     Firestore Trigger (onCreate)
                                  ↓
              processStudyPlanGeneration Function
                                  ↓
                    [Procesamiento en background]
                                  ↓
         ┌─────────────────┬──────────────────┬─────────────────┐
         ↓                 ↓                  ↓                 ↓
    GPT-4o           GPT-4o            GPT-4o-mini      Firebase Storage
  (Título)       (Descripción)    (Quizzes/Flashcards)    (PDF original)
         ↓                 ↓                  ↓                 ↓
         └─────────────────┴──────────────────┴─────────────────┘
                                  ↓
                          Cloud Firestore
                    (Plan completo actualizado)
                                  ↓
                          MainActivity
                      (Lista de planes actualizada)
```

**Flujo detallado:**

1. Usuario selecciona un documento PDF en `UploadActivity`
2. Configura límites (quizzes, preguntas, flashcards, hot topics)
3. Configurar tipología de quiz (A/B/C/D, A/B/C, True/False, Palabras hueccas, Combinado)
 4. `UploadActivity` asigna planId canónico (`U0#####`) mediante transacción en `users/{uid}.nextPlanNumber`
 5. Llamada a Cloud Function `generateStudyPlan` con metadata y planId propuesto
 6. Función responde inmediatamente con planId y crea documento "placeholder" en Firestore
 7. Trigger de Firestore activa `processStudyPlanGeneration` en background
 8. Procesamiento en 3 fases paralelas:
   - **Fase 1**: Generación de título (GPT-4o, ~2s)
   - **Fase 2**: Generación de descripción (GPT-4o, ~3s)
   - **Fase 3**: Generación de contenido en lotes (GPT-4o-mini, ~15-30s por lote)
     - Validación estricta por tipología
     - Sistema de reintentos (hasta 2 intentos si faltan items)
     - Subida de PDF a Storage en paralelo
9. Actualización progresiva en Firestore (generationProgress: 0-100%)
10. Usuario ve el plan completado en `MainActivity` con acceso a todo el contenido

---

## 📁 Estructura del Proyecto

```
EstudiaApp/
├── app/                                    # Módulo principal de la aplicación
│   ├── build.gradle                        # Configuración Gradle del módulo
│   ├── google-services.json                # Configuración Firebase
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/app/estudiaapp/
│   │   │   │   ├── activities/             # Activities principales
│   │   │   │   │   ├── MainActivity.java          # Pantalla principal con lista de planes
│   │   │   │   │   ├── BaseActivity.java          # Activity base con drawer navigation
│   │   │   │   │   ├── UploadActivity.java        # Subir documentos y crear planes
│   │   │   │   │   ├── FlashcardActivity.java     # Vista de flashcards
│   │   │   │   │   ├── QuizActivity.java          # Realización de quizzes
│   │   │   │   │   ├── QuizListActivity.java      # Lista de quizzes de un plan
│   │   │   │   │   ├── UserActivity.java          # Perfil y estadísticas de usuario
│   │   │   │   │   ├── PlanDetailsActivity.java   # Detalles con cards plegables
│   │   │   │   │   ├── HotTopicsActivity.java     # Vista de temas importantes
│   │   │   │   │   ├── SignInActivity.java        # Inicio de sesión
│   │   │   │   │   ├── SignUpActivity.java        # Registro de usuario
│   │   │   │   │   ├── SplashActivity.java        # Pantalla de inicio
│   │   │   │   │   ├── MembershipActivity.java    # Información de membresías
│   │   │   │   │   ├── BibliotecaActivity.java    # Biblioteca de planes
│   │   │   │   │   └── ...
│   │   │   │   ├── models/                # Modelos de datos
│   │   │   │   │   ├── StudyPlan.java            # Plan de estudio con quizzes/flashcards
│   │   │   │   │   ├── Quiz.java                 # Quiz con tipología
│   │   │   │   │   ├── Question.java             # Pregunta con validación
│   │   │   │   │   ├── Flashcard.java            # Tarjeta de estudio
│   │   │   │   │   ├── HotTopic.java             # Tema importante
│   │   │   │   │   ├── UserProgress.java         # Progreso del usuario
│   │   │   │   │   ├── QuizProgress.java         # Progreso en quizzes
│   │   │   │   │   ├── Badge.java                # Insignias/logros
│   │   │   │   │   └── BadgesRepository.java     # Repositorio de badges
│   │   │   │   ├── adapters/              # Adaptadores RecyclerView
│   │   │   │   │   ├── PlansAdapter.java         # Adaptador de lista de planes
│   │   │   │   │   ├── QuizzesAdapter.java       # Adaptador de quizzes
│   │   │   │   │   ├── FlashcardsAdapter.java    # Adaptador de flashcards
│   │   │   │   │   └── HotTopicsAdapter.java     # Adaptador de hot topics
│   │   │   │   ├── helper/                # Helpers y utilidades
│   │   │   │   │   └── StorageHelper.java        # Persistencia local JSON
│   │   │   │   ├── utils/                 # Utilidades generales
│   │   │   │   │   ├── OpenAiKeyStore.java       # Gestión segura de API key
│   │   │   │   │   ├── DocumentExtractor.java    # Stub: extracción removida, lanza excepción
│   │   │   │   │   ├── FileValidator.java        # Validación MIME, tamaño y legibilidad de archivos
│   │   │   │   │   ├── UploadErrorType.java      # Enum de tipos de error con mensajes user-friendly
│   │   │   │   │   └── UploadErrorHandler.java   # Clasificador de errores Firebase a mensajes UX
│   │   │   │   ├── ui/                    # Componentes UI personalizados
│   │   │   │   │   └── OverlayManager.java       # Gestión de overlays de progreso
│   │   │   │   ├── api/                   # Clientes API
│   │   │   │   ├── App.java               # Application class (inicialización)
│   │   │   │   ├── AuthManager.java       # Gestión centralizada de autenticación
│   │   │   │   └── UserRepository.java    # Repositorio de usuarios/Firestore
│   │   │   ├── res/                       # Recursos de la aplicación
│   │   │   │   ├── layout/                       # Layouts XML
│   │   │   │   ├── drawable/                     # Imágenes y vectores
│   │   │   │   ├── values/                       # Strings, colores, estilos
│   │   │   │   ├── menu/                         # Menús (drawer_menu.xml)
│   │   │   │   └── ...
│   │   │   └── AndroidManifest.xml        # Manifest de la aplicación
│   │   ├── test/                          # Tests unitarios
│   │   └── androidTest/                   # Tests instrumentados
├── functions/                              # Cloud Functions (Node.js 20)
│   ├── index.js                            # Funciones principales (1289 líneas)
│   ├── package.json                        # Dependencias Node.js
│   └── ...
├── gradle/                                 # Configuración Gradle
│   ├── libs.versions.toml                 # Catálogo de versiones
│   └── wrapper/
├── server/                                 # Servidor de pruebas (legacy)
├── build.gradle                            # Configuración Gradle raíz
├── settings.gradle                         # Configuración del proyecto
├── gradlew / gradlew.bat                   # Wrappers de Gradle
├── README.md                               # Este archivo
├── README_BBDD.md                          # Documentación de base de datos
└── firebase.json                           # Configuración Firebase
```

---

## ⚙️ Configuración e Instalación

### Requisitos Previos

- **Android Studio** Arctic Fox o superior
- **JDK 8** o superior
- **Cuenta Firebase** con proyecto configurado
- **API Key de OpenAI** (para generación de contenido)

### Pasos de Instalación

#### 1. Clonar el Repositorio

```bash
git clone https://github.com/Fibanezv/studIA.git
cd studIA
```

#### 2. Configurar Firebase

1. Crear un proyecto en [Firebase Console](https://console.firebase.google.com/)
2. Habilitar **Authentication** (Email/Password y opcionalmente Google)
3. Crear una base de datos **Cloud Firestore**
4. Configurar **Firebase Storage**
5. Descargar `google-services.json` y colocarlo en `app/`

**Reglas de seguridad recomendadas (Firestore):**

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
      
      match /plans/{planId} {
        allow read, write: if request.auth != null && request.auth.uid == userId;
        
        match /{document=**} {
          allow read, write: if request.auth != null && request.auth.uid == userId;
        }
      }
    }
  }
}
```

#### 3. Configurar OpenAI API Key

**Opción A - Remote Config (Recomendado para producción):**

1. En Firebase Console, ir a Remote Config
2. Crear parámetro `OPENAI_API_KEY` con tu API key
3. La app lo recuperará automáticamente al iniciar

**Opción B - Local (Solo para desarrollo):**

Crear `app/src/main/res/values/secrets.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="openai_api_key">sk-proj-...</string>
</resources>
```

⚠️ **IMPORTANTE:** Nunca commitear `secrets.xml` al repositorio. Agregarlo a `.gitignore`.

#### 4. Compilar y Ejecutar

```bash
# Desde línea de comandos
./gradlew assembleDebug

# O abrir en Android Studio y presionar Run
```

---

## 📱 Uso de la Aplicación

### Flujo Principal

#### 1. **Registro e Inicio de Sesión**
- Abrir app → `SplashActivity` → `SignInActivity`
- Crear cuenta o iniciar sesión con email/password
- Opcionalmente: Google Sign-In

#### 2. **Crear un Plan de Estudio**
- En `MainActivity`, presionar botón "Crear Plan de Estudio"
- Seleccionar un documento PDF
- Configurar opciones:
  - Número de quizzes (1-10)
  - Preguntas por quiz (5-20)
  - Número de flashcards (10-50)
  - Hot topics (3-10)
- Presionar "Generar Plan"
- Esperar procesamiento (30-60 segundos)

#### 3. **Estudiar con el Plan**
- Ver plan generado en `MainActivity`
- Acceder a **Quizzes**: responder preguntas de opción múltiple
- Acceder a **Flashcards**: estudiar conceptos clave
- Ver **Hot Topics**: temas importantes identificados
- Revisar **Badges**: logros desbloqueados

#### 4. **Seguimiento de Progreso**
- Ir a perfil de usuario (`UserActivity`)
- Ver estadísticas:
  - Total de planes
  - Quizzes completados
  - Flashcards estudiadas
  - Historial con timestamps
- Exportar datos personales

### Características Avanzadas

- **Modo Oscuro**: Toggle en perfil de usuario
- **Notificaciones**: Configurar en preferencias
- **Exportación de Datos**: Backup completo en JSON
- **Drawer Navigation**: Acceso rápido desde cualquier pantalla

---

## 📊 Modelos de Datos

### StudyPlan

```java
public class StudyPlan {
    public String id;                    // ID único del plan (ej: "U00001")
    public String nombre;                // Título del plan
    public List<Quiz> quizzes;           // Lista de quizzes
    public List<Flashcard> flashcards;   // Lista de flashcards
    public List<HotTopic> hotTopics;     // Temas importantes
    public String sourceUrl;             // URL del PDF en Storage
    public UserProgress progreso;        // Progreso del usuario
}
```

### Quiz

```java
public class Quiz {
    public String id;                    // ID del quiz
    public String titulo;                // Título del quiz
    public String typology;              // Tipología: "A, B, C, D" | "A, B, C" | "True/False" | "Palabras hueccas" | "Combinado"
    public List<Question> questions;     // Lista de preguntas
    public QuizProgress progreso;        // Progreso en este quiz
}
```

### Question

```java
public class Question {
    public String question;              // Enunciado de la pregunta
    public String[] options;             // Array de opciones
                                         // - [A, B, C, D] para "A, B, C, D"
                                         // - [A, B, C] para "A, B, C"
                                         // - [Verdadero, Falso] para "True/False"
                                         // - [palabra1, palabra2, palabra3, palabra4] para "Palabras hueccas"
    public String answer;                // Respuesta correcta (debe estar en options)
    public String difficulty;            // Nivel de dificultad
    public boolean inferred;             // Si fue inferida o extraída del documento
    public String evidence;              // Fragmento del PDF que soporta la pregunta
}
```

### Flashcard

```java
public class Flashcard {
    public String pregunta;              // Pregunta/concepto (max 150 chars)
    public String respuesta;             // Respuesta/definición detallada (120-250 chars)
    public boolean completada;           // Marcada como estudiada
    public boolean inferred;             // Si fue inferida o extraída
    public String evidence;              // Fragmento del PDF que soporta el concepto
}
```

### UserProgress

```java
public class UserProgress {
    public int preguntasRespondidas;     // Total de preguntas respondidas
    public int preguntasCorrectas;       // Total de respuestas correctas
    public int flashcardsEstudiadas;     // Flashcards completadas
    public int nivel;                    // Nivel del usuario (gamificación)
    public long lastUpdatedMillis;       // Timestamp de última actualización
}
```

### Estructura en Firestore

```
users/
  {uid}/
    ├── displayName: string
    ├── email: string
    ├── membership: { status: "free|pro|premium", ... }
    ├── preferences: { notificationsEnabled: bool, ... }
    ├── stats: { totalPlans, totalQuizzes, ... }
    ├── createdAt: timestamp
    └── plans/
        {planId}/
          ├── id: string
          ├── nombre: string
          ├── quizzes: array
          ├── flashcards: array
          ├── hotTopics: array
          ├── sourceUrl: string
          ├── progreso: object
          └── createdAt: timestamp
```

---

## 🔒 Seguridad y Buenas Prácticas

### Gestión de API Keys

### Seguridad en Caché

- **TTL (Time To Live)**: Cache automáticamente expira después de 5 minutos (configurable via Remote Config en futuro)
- **Thread-Safe**: `ConcurrentHashMap` para evitar race conditions en acceso concurrente
- **Snapshot Listeners**: Real-time sync en Activities críticas (PlanDetailsActivity) para cambios desde otros dispositivos
- **Offline Resilience**: Fallback a cache stale si hay errores de red durante refresh
- **Memory Management**: Limpieza automática de entradas expiradas en acceso; métodos `clearCache()` e `invalidatePlanCache()` para manual cleanup
- **Logging**: Detallado de cache HIT/MISS/EXPIRED con edad en segundos para debugging

### Arquitectura de Caché

**UserRepository PLAN_CACHE:**
```java
ConcurrentHashMap<String, CachedPlan> PLAN_CACHE = new ConcurrentHashMap<>();

class CachedPlan {
    StudyPlan plan;
    long cachedAtMillis;
    long ttlMillis = 5 * 60 * 1000;  // 5 minutes
    boolean isExpired();
    long getAgeSeconds();
}
```

**Métodos principales:**
1. `getPlan(uid, planId, onSuccess, onFailure)`: Check cache TTL, serve if fresh, fetch if expired
2. `refreshPlanIfNeeded(uid, planId, onSuccess, onFailure)`: Conditional refresh basado en TTL con fallback
3. `registerPlanListener(uid, planId, onUpdate)`: Real-time listener retorna `ListenerRegistration`
4. `clearCache()` / `invalidatePlanCache(uid, planId)`: Manual cleanup

**Integración con Activities:**
- `PlanDetailsActivity`: Register listener en `onCreate()`, cleanup en `onDestroy()`, refresh en `onResume()`
- `QuizActivity` / `FlashcardActivity`: `refreshPlanIfNeeded()` en `onCreate()` y `onResume()`

### Nunca hardcodear API keys en el código fuente

### Autenticación

- Firebase Authentication con reglas de seguridad estrictas
- Validación de tokens en cada solicitud
- Sesiones persistentes con `AuthStateListener`
- Logout centralizado mediante `AuthManager`

### Firestore Security Rules

```javascript
// Solo el usuario puede acceder a sus propios datos
match /users/{userId} {
  allow read, write: if request.auth != null 
                     && request.auth.uid == userId;
}

// Validación de tipos en escritura
match /users/{userId}/plans/{planId} {
  allow create: if request.auth.uid == userId
                && request.resource.data.nombre is string
                && request.resource.data.nombre.size() > 0;
}
```

### Privacidad de Datos

- **GDPR Compliance**: Exportación y eliminación de datos de usuario
- Almacenamiento local encriptado para datos sensibles
- No se comparten datos con terceros sin consentimiento
- Logs anónimos para debugging

---

## 📈 Estado Actual del Proyecto

### ✅ Funcionalidades Completadas

- [x] Sistema de autenticación (Firebase Auth)
- [x] Generación de planes con Cloud Functions (Node.js 20)
- [x] Generación inteligente de títulos y descripciones con GPT-4o
- [x] Quizzes interactivos con 5 tipologías diferentes
- [x] Sistema de validación y reintentos automáticos (hasta 2 intentos)
- [x] Flashcards con respuestas detalladas
- [x] Hot Topics con descripciones concisas (50-75 chars)
- [x] Sistema de progreso y estadísticas con generationProgress
- [x] Badges y gamificación básica con card plegable
- [x] Drawer navigation centralizado
- [x] Modo oscuro/claro
- [x] Almacenamiento offline (Firestore + local JSON)
- [x] Exportación de datos personales
- [x] Cards plegables en PlanDetailsActivity (Descripción y Logros)
- [x] Formulario de creación mejorado (spinners alineados, botón remover documento)
- [x] Overlay de progreso para tareas largas
- [x] Orquestación directa de generación sin WorkManager (UploadActivity → Cloud Function)
 - [x] Aplicación temprana del tema: `App` y `SplashActivity` aplican modo oscuro/claro al inicio para evitar flicker
 - [x] Flujo de cambio de tema: al alternar en `UserActivity` se lanza `SplashActivity` para precargar preferencias y re-aplicar recursos sin parpadeos
 - [x] Corrección de toast en ajustes: eliminado el mensaje genérico de error en éxito al actualizar notificaciones
- [x] **UploadActivity robusto**: 
  - Validación MIME real con `ContentResolver` (whitelist: PDF, TXT)
  - Validación de tamaño de archivo (máximo 50MB) con formato legible
  - Verificación de legibilidad con prueba de InputStream
  - Manejo de errores clasificado con 15 tipos (`UploadErrorType`)
  - Mensajes user-friendly diferenciados por categoría (red, auth, storage, functions, validación)
  - Lógica retriable vs no-retriable con botón "Reintentar" condicional
  - Dialogs con "Ver detalles" en modo debug (BuildConfig.DEBUG)
  - Logging estructurado por fases (validation, storage, function_call) con contexto
- [x] Unificación de acceso a quizzes: `QuizListActivity` carga el plan; `QuizActivity` usa `UserRepository.getQuiz(...)` sin reconsultar el plan; `QuizzesAdapter` desacoplado del refresh
- [x] Tests unitarios básicos: `StudyPlanTest`, `QuizTest` (modelos y serialización Gson)
- [x] Tests instrumentados básicos: `SignInFlowTest`, `QuizActivityUiTest`, `StorageHelperInstrumentedTest`
- [x] Configuración JaCoCo para cobertura de tests unitarios
- [x] CI/CD inicial: GitHub Actions workflow para unit tests automáticos en PRs y pushs
- [x] **Sistema unificado de error handling**:
  - `ErrorHandler.java`: Clasificador universal de excepciones (Auth, Firestore, Storage, Network)
  - `GeneralErrorType.java`: Enum con 11 tipos de error y mensajes user-friendly
  - `ErrorDialogHelper.java`: Diálogos consistentes con retry logic, debug details (BuildConfig.DEBUG), Toast para errores no-críticos
  - Refactorizadas 7 Activities: SignIn, SignUp, MainActivity, UserActivity, PlanDetails, Quiz, Flashcard
  - Diferenciación de errores específicos: USER_COLLISION, WEAK_PASSWORD, INVALID_CREDENTIALS, INVALID_EMAIL
  - Logging estructurado con contexto (`ErrorHandler.logError(exception, "Activity.method")`)
  - Feedback visual cuando se usan datos offline/locales
  - Botón "Reintentar" condicional solo para errores recuperables (network, timeout, firestore)
- [x] **Política de refresco de caché con TTL y snapshot listeners**:
  - `CachedPlan` class: Wrapper con plan, timestamp, TTL (5 minutos por defecto)
  - Cache thread-safe con `ConcurrentHashMap` en lugar de `HashMap`
  - Logging detallado: cache HIT/MISS/EXPIRED con edad en segundos
  - `refreshPlanIfNeeded()`: Conditional refresh basado en TTL con fallback a cache stale en errores de red
  - `registerPlanListener()`: Real-time updates via Firestore snapshot listeners (retorna `ListenerRegistration`)
  - `clearCache()` y `invalidatePlanCache()`: Manual cache invalidation
  - PlanDetailsActivity: Snapshot listener en `onCreate()`, cleanup en `onDestroy()`, refresh en `onResume()` con TTL
  - QuizActivity: `refreshPlanIfNeeded()` en `onCreate()` y `onResume()` para respuestas actualizadas
  - FlashcardActivity: `refreshPlanIfNeeded()` en `onCreate()` y `onResume()` para progreso fresh
  - Escalabilidad mejorada: evita refetches innecesarios (cache <5min), real-time collaboration (PlanDetails), resilencia offline (stale cache fallback)
- [x] **Transacciones atómicas en progreso de quiz y flashcards**:
  - `updateQuizProgressWithTransaction()`: Lectura-modificación-escritura atómica con `db.runTransaction()`
  - `markFlashcardCompletedWithTransaction()`: Marca completitud + counters atómicos
  - `resetQuizProgress()`: Reinicio atómico de quizzes (QuizzesAdapter con confirmación, invalidación de caché, recompute de stats)
  - Métodos existentes refactorizados para delegar a versiones transaccionales
  - Cache invalidation automática post-transacción
  - Retry automático en conflictos (Firestore conflict resolution)
  - Recompute de stats agregadas post-éxito de transacción
  - Eliminación de race conditions: 30+ usuarios concurrentes = garantía ACID
  - Latencia: +100ms (aceptable trade-off para consistencia)
  - Documentación en [docs/TRANSACTIONAL_QUIZ_PROGRESS_ANALYSIS.md](docs/TRANSACTIONAL_QUIZ_PROGRESS_ANALYSIS.md)
- [x] **Transiciones Material Motion entre pantallas**:
  - FadeThrough: autenticación (Splash→SignIn/Main), navegación drawer, cambio de secciones
  - SharedAxis X: navegación lista→detalle (QuizList→Quiz) con slide forward
  - MaterialContainerTransform: card→detalles (Main→PlanDetails) con shared elements (`plan_card`, `plan_title`)
  - Helpers centralizados: `startActivityFadeThrough()`, `startActivitySharedAxisX()`, `startActivityWithContainerTransform()`
  - Animaciones XML de fallback: fade_in/out, slide_in_right/out_left
  - Detección de accesibilidad: respeta `reduce motion` deshabilitando animaciones
  - Window transitions habilitadas en tema con MaterialFadeThrough por defecto

### 🔄 En Progreso

- [ ] Sincronización bidireccional Firestore ↔ Local
- [ ] Mejoras en UI/UX de flashcards (navegación entre tarjetas)
- [ ] Sistema de notificaciones inteligente
- [ ] Soporte para más formatos de documento (DOCX, TXT)
- [ ] Optimización de prompts para reducir tokens consumidos
- [ ] Dashboard de estadísticas con gráficos

### ⚠️ Problemas Conocidos

1. **Concurrencia en progreso de quizzes**: `updateQuizProgress()` usa lectura-modificación-escritura sin transacción; riesgo de sobrescrituras concurrentes.
2. **Divergencia local vs nube**: fallback con `StorageHelper.cargarPlan(...)` puede desalinear datos si se escriben localmente y no se sincronizan adecuadamente.
3. **Validación de esquema en reglas**: las reglas de Firestore no validan estructura anidada (`quizzes`, `questions`, `progreso`); riesgo de datos malformados.
4. **Inconsistencias en constantes**: uso de literales vs `Constants.EXTRA_PLAN_ID`; requiere armonización.
5. **Cobertura de tests mejorada**: `UserRepositoryTest` con mocks de Firestore cubre actualización transaccional, reinicio de progreso y fallback de caché; pendiente ampliar casos borde y pruebas con emuladores.
6. **Tests instrumentados parciales**: `SignInFlowTest` y `QuizActivityUiTest` son básicos; falta navegación completa entre Activities, tests de adaptadores (visibilidad de botón "Reiniciar", progreso), modo oscuro/claro.
7. **Sin integración Firebase en tests**: falta configuración de emuladores de Auth/Firestore/Storage para tests de integración y validación de transacción de `planId`.
8. **Telemetría/Crash**: sin Analytics/Crashlytics para observar fallos de generación o de UI.
9. **Accesibilidad**: pendiente revisión completa de `contentDescription` y flujo con TalkBack.
10. **TTL configurable**: Actualmente fijo en 5 minutos; pendiente integración con Firebase Remote Config para ajustar dinámicamente (rango 1-30min).

---

## 🗺️ Roadmap y Tareas Pendientes

### Alta Prioridad 🔴

- [ ] **Expandir tests instrumentados**: navegación completa Main→Upload→PlanDetails→QuizList→Quiz, validación de transacciones concurrentes, tests de estrés (30+ usuarios simultáneos)
- [ ] **Tests de transacciones**: Unit tests para `updateQuizProgressWithTransaction()` y `markFlashcardCompletedWithTransaction()` con emulador Firestore
- [ ] **Unificar fallback local**: encapsular lecturas/escrituras locales en `UserRepository` y evitar escrituras directas desde Activities.
- [ ] **Reglas de seguridad**: validar tipos/longitudes y estructura de `quizzes`, `questions`, `progreso` en Firestore Rules.
- [ ] **Tests de integración Firebase**: configurar emuladores Auth/Firestore/Storage y validar transacción `nextPlanNumber`, lectura de planes, Cloud Functions contract

### Prioridad Media 🟡

- [ ] **Navegación en flashcards**: botones avanzar/retroceder, marcar conocido
- [ ] **Migración a Room**: reemplazar StorageHelper por Room Database
- [ ] **Mejorar ProgressActivity**: visualizaciones gráficas (charts)
- [ ] **Sistema de recomendaciones**: sugerir planes similares
- [ ] **Compartir planes**: export/import entre usuarios
- [ ] **Expandir CI/CD**: añadir job de instrumented tests con emulador Android, emuladores Firebase, lint, reportes de cobertura JaCoCo
- [ ] **Telemetría**: integrar Firebase Analytics y Crashlytics básicos
- [ ] **Tests de reglas de seguridad**: validar Firestore rules con `@firebase/rules-unit-testing` (owner access, esquema nested)
- [ ] **Benchmarks de rendimiento**: parsing JSON, operaciones de caché, stress de `updateQuizProgress` concurrente

### Monetización y Negocio 💰

- [ ] **A/B Testing de Precios**: Experimentar con pricing ($6.99 vs $7.99 vs $9.99 mensual), descuentos anuales (15-20%), monitorear conversion rates y revenue impact
- [ ] **Estrategia de Ads (AdMob)**: Implementar interstitials en eventos (crear plan, reiniciar quiz/plan, eliminar plan), banners persistentes (MainActivity, PlanDetails, QuizActivity, Flashcards), rewarded ads opcionales; target CPM $2-3
- [ ] **Plan de Marketing y Crecimiento**: ASO (App Store Optimization), campaña de lanzamiento, estrategia de influencers educativos, comunidades de estudiantes (Reddit, Discord); métricas: CAC, DAU/MAU, retention day 7
- [ ] **Integración Google Play Billing**: Implementar suscripción PRO (monthly + annual), manejo de renovaciones automáticas, cancelaciones, refunds
- [ ] **Analytics y Monetización Tracking**: Firebase Analytics eventos para conversion funnel (onboarding → paywall → subscription), cohort analysis, LTV tracking

### Prioridad Baja 🟢

- [ ] **Localización**: soporte multi-idioma (inglés, francés)
- [ ] **Accesibilidad**: contentDescription completo, TalkBack testing
- [ ] **Widget de home screen**: acceso rápido a planes recientes
- [ ] **Limpieza de `server/`**: documentar o eliminar artefactos legacy (logs de emulador/WorkManager) para evitar confusión

### Mejoras Técnicas 🔧

- [ ] **Migrar a Kotlin**: conversión gradual de Java a Kotlin
- [ ] **Compose UI**: reescribir UI con Jetpack Compose
- [ ] **Modularización**: dividir app en módulos feature-based
- [ ] **Dependency Injection**: implementar Hilt/Dagger
- [ ] **Clean Architecture**: separar capas domain/data/presentation

---

## 🤝 Contribución

Las contribuciones son bienvenidas. Para contribuir:

1. **Fork** el repositorio
2. Crear una rama feature: `git checkout -b feature/nueva-funcionalidad`
3. Commit cambios: `git commit -m 'Add: nueva funcionalidad'`
4. Push a la rama: `git push origin feature/nueva-funcionalidad`
5. Abrir un **Pull Request**

### Guías de Contribución

- Seguir el estilo de código existente
- Añadir tests para nuevas funcionalidades
- Actualizar documentación según sea necesario
- Commit messages descriptivos (Conventional Commits)

---

## 📄 Licencia

Este proyecto es privado y no tiene licencia pública en este momento.

---

## 📞 Contacto y Soporte

- **GitHub Issues**: [Reportar bug o sugerir feature](https://github.com/Fibanezv/studIA/issues)
- **Email**: contacto@estudiaapp.com (placeholder)
- **Documentación de BD**: Ver [docs/README_BBDD.md](docs/README_BBDD.md) para detalles de base de datos
- **Política de Caché**: Ver [docs/CACHE_POLICY.md](docs/CACHE_POLICY.md) para arquitectura y debugging
- **Resumen de Implementación**: Ver [docs/CACHE_IMPLEMENTATION_SUMMARY.md](docs/CACHE_IMPLEMENTATION_SUMMARY.md) para impacto de escalabilidad
- **Checklist Completo**: Ver [docs/IMPLEMENTATION_COMPLETE.txt](docs/IMPLEMENTATION_COMPLETE.txt) para overview visual

---

## 🙏 Agradecimientos

- **Firebase** por la infraestructura backend
- **OpenAI** por las capacidades de IA
- **Android Community** por recursos y guías
- **Material Design** por las guías de UI/UX

---

**EstudiaApp** - Aprende más inteligentemente 🎓✨
