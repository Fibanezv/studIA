# EstudiaApp — Análisis actualizado

Este README ha sido actualizado punto por punto tras un análisis del estado actual del repositorio (noviembre 2025). Incluye: descripción, cambios recientes, estructura de ficheros relevada, estado de las Activities, modelo de datos, cómo se almacena la información, lista de tareas pendientes (prioritizadas) y una lista separada de mejoras recomendables.

Resumen rápido (últimos cambios relevantes)
- Se añadió una clase `BaseActivity` que centraliza el comportamiento del header: fija el icono del menú lateral (drawer) y abre el `DrawerLayout` si existe; si no, muestra un `PopupMenu` como fallback.
- El layout `include_app_header.xml` contiene el botón del menú izquierdo (`@id/header_menu_button`) y el icono de la app centrado. `BaseActivity` fuerza el drawable `ic_menu_24` y aplica tint para mantener consistencia visual.
- Muchos layouts se adaptaron para incluir un `DrawerLayout` + `NavigationView` y la navegación se centralizó para que el drawer sea accesible desde las pantallas que incluyen el header.
- Se añadieron protecciones en `FlashcardActivity` para evitar crash cuando falta `plan_id` o no hay flashcards (se elige plan por defecto o se muestra un Toast y se termina la Activity).
- `UserActivity` se rediseñó: ahora muestra perfil, estadísticas (planes/quizzes/flashcards), toggles, exportación de datos, historial por plan/quiz y edición del nombre (email no editable). Se eliminó la opción de restablecer datos en la UI.
- Se agregó `HotTopicsActivity` y recursos asociados (badges, etc.). Badges y PlanDetails fueron pulidos para mostrar solo badges alcanzados con icono `badge_perfect_plan`.

Índice
- Descripción general
- Cambios recientes y estado actual
- Estructura principal del proyecto
- Resumen por Activity (estado actualizado)
- Modelos y almacenamiento
- Problemas conocidos / limitaciones actuales
- Tareas pendientes (priorizadas) — lista puntual
- Mejoras recomendables (no críticas)
- Cómo ejecutar y validar localmente
- Notas finales

---

Descripción general
-------------------
EstudiaApp es una app Android para crear y usar planes de estudio compuestos por quizzes y flashcards. Los planes pueden ser generados a partir de ficheros subidos (UploadActivity) o creados en memoria (datos de ejemplo con StorageHelper). El almacenamiento principal actual es JSON por plan en internal storage.

Cambios recientes y estado actual
--------------------------------
- Centralizado el header y drawer: `BaseActivity` controla la apertura del drawer o el fallback del popup.
- Header visual: `include_app_header.xml` ahora tiene un icono consistente y un botón de menú en la izquierda.
- Drawer: layouts como `activity_quiz_list.xml` o `activity_flashcard.xml` están envueltos en `DrawerLayout` y usan `NavigationView` con `drawer_menu` y `nav_header_drawer`.
- `FlashcardActivity` se defendió contra NPEs (selección de plan por defecto, mensajes al usuario si no hay flashcards).
- `UserActivity` fue ampliado: perfil (nombre, email), stats por planes/quizzes/flashcards, toggles, exportar datos, historial de quizzes (timestamps), edición del nombre (no del email), y cierre de sesión.
- HotTopics y badges: se añadieron recursos y lógica para mostrar solo badges logrados.

Checkpoint actual (2025-11-09)
-----------------------------
Estos puntos resumen los cambios manuales más recientes y el estado del proyecto en este checkpoint. No se han aplicado cambios de código durante la actualización del README (sólo documentación).

- Archivos modificados (manualmente antes de este README):
  - `app/src/main/res/layout/activity_user.xml`
    - Perfil, estadísticas y toggles se consolidaron en un único `CardView` con estilo destacado (fondo `colorPrimary`, texto beige `beige_300_theme`).
    - Se añadió la inclusión del bloque promocional premium arriba del card: `<include layout="@layout/nav_drawer_footer_premium" />`.
    - IDs relevantes añadidos/empleados en la UI: `tv_plans_count`, `tv_membership_type`, `tv_flashcards_count`, `tv_hot_topics_count`, `tv_quizzes_count`, `switch_notifications`, `switch_dark_mode`, `premium_card`, `premium_shimmer`.

  - `app/src/main/res/layout/nav_drawer_footer_premium.xml`
    - Layout reutilizable para la tarjeta promocional (gradient + shimmer). Contiene `premium_card` y `premium_shimmer` para animación.

  - `app/src/main/res/layout/activity_plan_details.xml`
    - Estilos y visuales de la pantalla de detalles de plan actualizados (mejor consistencia con badges y colores highlight).

  - `app/src/main/java/com/app/estudiaapp/UserActivity.java`
    - `onCreate()` y wiring actualizados para detectar la inclusión del `premium_card` en el layout, iniciar la animación shimmer (`premium_shimmer`) y manejar el clic para abrir `MembershipActivity`.
    - `computeStats()` mantiene la lógica para poblar: `tv_plans_count`, `tv_quizzes_count`, `tv_flashcards_count`.

- Recursos y utilidades añadidas/ajustadas:
  - Selectores de color para switches: `res/color/switch_thumb_selector.xml` y `res/color/switch_track_selector.xml` — switches tintados con `colorPrimary` cuando están activados.

- Estado de compilación (local):
  - `.\gradlew.bat assembleDebug` ejecutado tras los cambios y devolvió: BUILD SUCCESSFUL.

- Pendientes detectados (a documentar/implementar posteriormente):
  1. Poblar `tv_membership_type` desde preferencias/`authPrefs` (ahora por defecto muestra "FREE").
  2. Calcular y poblar `tv_hot_topics_count` (dependiente de la definición de "hot topics" en la lógica del dominio).
  3. Actualizar `FlashcardsAdapter` para enlazar la nueva ImageView de completado (si procede) — binding pendiente en el adaptador.
  4. Revisión visual en dispositivo/emulador para ajustar paddings/márgenes y comprobar accesibilidad (contentDescription y tamaños).

- Notas sobre coherencia y seguridad:
  - El include del promo fue normalizado a `nav_drawer_footer_premium` para evitar discrepancias de nombre de recurso (se verificó la referencia en `activity_user.xml`).
  - No se tocaron los archivos Java/XML originales durante la creación de este checkpoint; el README refleja el estado actual tal como está en el árbol de trabajo.

Si quieres, puedo transformar estos pendientes en issues/PRs y preparar parches concretos para cada uno (p. ej. extraer `membershipType` desde `authPrefs` y añadir tests pequeños). Indica si quieres que lo haga ahora o que sólo documente más en detalle cada pendiente.

Estructura principal del proyecto (relevante)
------------------------------------------
- `app/src/main/java/com/app/estudiaapp/`
  - Activities principales: `MainActivity`, `FlashcardActivity`, `QuizActivity`, `QuizListActivity`, `UploadActivity`, `SignInActivity`, `SignUpActivity`, `SplashActivity`, `UserActivity`, `HotTopicsActivity`, `QuizResultsActivity`, etc.
  - `BaseActivity.java` — comportamiento central del header/drawer.
  - `helper/StorageHelper.java` — persistencia (JSON por plan) y utilidades de datos de prueba.
  - `models/` — `StudyPlan`, `Quiz`, `Question`, `Flashcard`, `UserProgress`, `QuizProgress`, `HotTopic`.
  - `adapters/` — adaptadores RecyclerView (planes, quizzes).
  - `api/` — cliente Retrofit para subida de ficheros.

- Recursos UI: `res/layout/` (include_app_header, activity_*), `res/menu/drawer_menu.xml`, `res/layout/nav_header_drawer.xml`, drawables para iconos y badges.

Resumen por Activity (estado actualizado)
---------------------------------------
Nota: solo se listan las Activities con estado relevante tras los cambios.

- MainActivity
  - Lista planes. Crea datos de ejemplo si no existen (StorageHelper.crearPlanSimuladoSiNoExiste).
  - Abre UploadActivity y Plan/Quiz/Flashcard según selección.

- FlashcardActivity
  - Carga plan por `Constants.EXTRA_PLAN_ID` o elige el primero si falta.
  - Evita crash si no hay flashcards (muestra Toast y termina).
  - Está envuelta en layout con DrawerLayout en la mayoría de casos; el header izquierdo abre el drawer (gracias a BaseActivity).

- QuizListActivity / QuizActivity
  - Muestra lista de quizzes y/o quiz en sí. Todavía existe cierta duplicación entre ambas; se recomienda unificar.

- UserActivity
  - Rediseñada: muestra perfil (nombre y email), estadísticas, toggles, exportación de datos, historial de quizzes (timestamps), permite editar sólo el nombre y cerrar sesión.

- UploadActivity
  - Permite seleccionar un fichero y subirlo. Asegúrate de que el endpoint acepte el tipo MIME subido; hoy `UploadActivity` hace un RequestBody y usa Retrofit.

- HotTopicsActivity
  - Actividad añadida para mostrar temas calientes del plan.

Modelos y almacenamiento
------------------------
- Modelos principales:
  - `StudyPlan` { id, nombre, List<Quiz>, List<Flashcard>, UserProgress }
  - `Quiz` { id, titulo, List<Question>, QuizProgress }
  - `Question` { question, options[], answer }
  - `Flashcard` { pregunta, respuesta }
  - `UserProgress` y `QuizProgress` guardan métricas y `lastUpdatedMillis` para timestamps.

- Almacenamiento:
  - Cada `StudyPlan` se guarda como `{planId}.json` en internal storage (StorageHelper.guardarPlan / cargarPlan / listarPlanes).
  - Exportación: `UserActivity` permite serializar todas las planes a `exported_data.json` en internal storage.

Problemas conocidos / limitaciones actuales
-----------------------------------------
1. Duplicación de lógica entre `QuizActivity` y `QuizListActivity`.
2. Algunos lugares usan literales para extras (`"plan_id"`) en vez de `Constants.EXTRA_PLAN_ID` — inconsistencias posibles.
3. `UploadActivity` debe detectar el MIME real y no asumir `application/pdf` en todos los casos.
4. Historial mostrado solo cuando `QuizProgress.lastUpdatedMillis` > 0 — requiere que la actualización de progreso lo pueble correctamente.
5. No hay entidad `User` persistente (se usa `SharedPreferences` con `email` y `displayName`).
6. Falta una cobertura automatizada de tests (unitarios/instrumentados).

Lista de tareas pendientes (puntual, priorizada)
---------------------------------------------
Tareas críticas (ALTO)
- Unificar la lógica de quizzes (eliminar duplicación entre `QuizActivity` y `QuizListActivity`).
- Robustecer `UploadActivity`: detectar MIME real (ContentResolver.getType), manejar errores, mostrar progress.
- Revisar y armonizar el uso de extras (usar `Constants.EXTRA_PLAN_ID` en todo el código).

Tareas importantes (MEDIO)
- Implementar navegación entre flashcards (avanzar/retroceder, marcar conocido/no conocido).
- Asociar `UserProgress` a un `User` persistente (Room o backend) si se necesita multiusuario.
- Mejorar `ProgressActivity` y vistas estadísticas.

Tareas de mantenimiento (BAJO)
- Añadir tests unitarios para `StorageHelper`, lógica de quizzes y serialización.
- Añadir CI (GitHub Actions) para build y pruebas.
- Revisar accesibilidad y localización (contentDescription, strings.xml).

Lista de mejoras recomendables (no urgentes)
-----------------------------------------
- Migrar a Room para escalabilidad y consultas eficientes.
- Hacer export/import compartible (share intent) y/o export a almacenamiento externo.
- Agregar un backend real: autenticación (Firebase/Auth), sincronización de planes y progreso.
- UI polish: animaciones al avanzar preguntas/flashcards, uso de Material components avanzados.
- Telemetría básica (eventos) para analizar uso y priorizar features.

Cómo ejecutar la app (rápido)
---------------------------
1. Abrir el proyecto en Android Studio.
2. Conectar un emulador o dispositivo.
3. Ejecutar: "Run app" o:

```powershell
cd C:\Users\Fran\Desktop\EstudiaApp
.\gradlew.bat assembleDebug
```

Notas:
- El endpoint de prueba para upload suele estar en `http://10.0.2.2:8000/` (emulador). Si pruebas en dispositivo real, actualiza `ApiClient.BASE_URL`.
- La exportación escribe en internal storage (`exported_data.json`). Para compartir el fichero hay que implementar share intent.

Archivo README: estado final y seguimiento
----------------------------------------
- Este README resume el estado actual del código y las decisiones importantes ya aplicadas.
- Pendientes: la sección "Lista de tareas pendientes" arriba y el `todo` del repositorio resumen los cambios por hacer.

Si quieres, puedo ahora:
- Generar un checklist con PRs sugeridos (archivos a cambiar y extracto de patch) para cada tarea priorizada.
- Empezar la tarea 1 (unificar QuizActivity) o la tarea 2 (robustecer UploadActivity). Indica cuál prefieres y la implemento.

---

Última revisión: noviembre 2025 — actualizado tras cambios en `BaseActivity`, `UserActivity`, `FlashcardActivity`, y recursos de drawer/badges.
