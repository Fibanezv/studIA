# EstudiaApp

> **Aplicación Android de Estudio Inteligente con IA**
> 
> EstudiaApp es una aplicación móvil que permite a los estudiantes generar planes de estudio personalizados, quizzes y flashcards a partir de documentos usando inteligencia artificial.

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![Firebase](https://img.shields.io/badge/Backend-Firebase-orange.svg)](https://firebase.google.com/)
[![OpenAI](https://img.shields.io/badge/AI-OpenAI-blue.svg)](https://openai.com/)

**Última actualización:** 13 de diciembre de 2025  
**Versión:** 1.0 (beta/0.0.1)  
**Repositorio:** [Fibanezv/studIA](https://github.com/Fibanezv/studIA)

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
- **Quiz items rediseñados**:
  - Botón "Reiniciar" debajo de progreso/nota (120dp, alineado a la izquierda)
  - Visible solo cuando el quiz está completado al 100%
- **Formulario de creación mejorado**: 
  - Spinners con texto alineado a la derecha
  - Labels "Total de" en lugar de "Límite de"
  - Botón para remover documento seleccionado (X)
  - Vista previa del nombre del archivo

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
│   │   │   │   │   └── DocumentExtractor.java    # Stub: extracción removida, lanza excepción
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

1. **Nunca hardcodear API keys** en el código fuente
2. Usar **Firebase Remote Config** para distribución segura
3. Almacenamiento local con **EncryptedSharedPreferences**
4. Implementar rate limiting y monitoreo de uso

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
- [x] Unificación de acceso a quizzes: `QuizListActivity` carga el plan; `QuizActivity` usa `UserRepository.getQuiz(...)` sin reconsultar el plan; `QuizzesAdapter` desacoplado del refresh

### 🔄 En Progreso

- [ ] Sincronización bidireccional Firestore ↔ Local
- [ ] Mejoras en UI/UX de flashcards (navegación entre tarjetas)
- [ ] Sistema de notificaciones inteligente
- [ ] Soporte para más formatos de documento (DOCX, TXT)
- [ ] Optimización de prompts para reducir tokens consumidos
- [ ] Dashboard de estadísticas con gráficos

### ⚠️ Problemas Conocidos

1. (Resuelto) **Duplicación de lógica** entre `QuizActivity` y `QuizListActivity`: ahora centralizado en `UserRepository`
2. **Inconsistencias en constantes**: uso de literales vs `Constants.EXTRA_PLAN_ID`
3. **Detección de MIME type**: actualmente asume PDF, necesita ContentResolver
4. **Tests**: cobertura insuficiente (unitarios e instrumentados)
5. **Historial**: solo se muestra cuando `lastUpdatedMillis > 0`

---

## 🗺️ Roadmap y Tareas Pendientes

### Alta Prioridad 🔴

- [ ] **Unificar lógica de quizzes**: eliminar duplicación entre Activities
- [ ] **Robustecer UploadActivity**: detección MIME real, manejo de errores mejorado
- [ ] **Armonizar uso de extras**: migrar a `Constants` en todo el código
- [ ] **Implementar tests unitarios**: StorageHelper, modelos, serialización
- [ ] **Mejorar manejo de errores**: feedback claro al usuario en fallos de red/API

### Prioridad Media 🟡

- [ ] **Navegación en flashcards**: botones avanzar/retroceder, marcar conocido
- [ ] **Migración a Room**: reemplazar StorageHelper por Room Database
- [ ] **Mejorar ProgressActivity**: visualizaciones gráficas (charts)
- [ ] **Sistema de recomendaciones**: sugerir planes similares
- [ ] **Compartir planes**: export/import entre usuarios

### Prioridad Baja 🟢

- [ ] **CI/CD**: GitHub Actions para build y tests automáticos
- [ ] **Localización**: soporte multi-idioma (inglés, francés)
- [ ] **Accesibilidad**: contentDescription completo, TalkBack testing
- [ ] **Telemetría**: Firebase Analytics para métricas de uso
- [ ] **Animaciones**: transiciones suaves entre pantallas
- [ ] **Widget de home screen**: acceso rápido a planes recientes

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
- **Documentación adicional**: Ver `README_BBDD.md` para detalles de base de datos

---

## 🙏 Agradecimientos

- **Firebase** por la infraestructura backend
- **OpenAI** por las capacidades de IA
- **Android Community** por recursos y guías
- **Material Design** por las guías de UI/UX

---

**EstudiaApp** - Aprende más inteligentemente 🎓✨
