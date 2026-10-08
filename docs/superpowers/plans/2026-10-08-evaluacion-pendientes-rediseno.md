# Evaluación de pendientes del rediseño — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Al aprobarlo, copiar este plan a `docs/superpowers/plans/2026-10-08-evaluacion-pendientes-rediseno.md`.

**Goal:** decidir, con datos medidos y un prototipo desechable cuando haga falta, si merece la pena arreglar cada uno de los 13 puntos que quedaron sin aplicar (9 de `/simplify` + 4 bugs A–D de `/code-review`), y dejarlo en un informe con veredicto por punto.

**Architecture:** cada tarea evalúa un punto: (1) mide en el código el alcance real (archivos, líneas, usos), (2) si el veredicto no es obvio, hace un prototipo en una rama `spike/…` desechable y comprueba que compila y pasa tests, (3) puntúa beneficio/coste/riesgo con la rúbrica común y (4) escribe su sección del informe. Nada se fusiona: las ramas `spike/…` se borran al acabar. La última tarea ordena los veredictos.

**Tech Stack:** Kotlin, Jetpack Compose, Material3 1.4.0, Navigation Compose, Firebase (Firestore), detekt, ktlint, JUnit.

**Spec:** decisiones del usuario (2026-10-08): evaluar los 9 hallazgos de `/simplify` no aplicados en `agent/simplify-rediseno-ui` y los bugs A–D de la revisión; resultado = informe con veredicto por punto, sin tocar `main`. Contexto: resúmenes de `/simplify` y `/code-review` de esta sesión.

## Antes de empezar (pedido junto con este plan)
- [x] Push de `agent/simplify-rediseno-ui` y PR «Rediseño UI · simplificación».
- [ ] **Esperar a que el usuario confirme el merge del PR.** Después: `git switch main && git pull` y todas las ramas (`agent/evaluacion-pendientes` y las `spike/…`) salen de ese `main`, que ya incluye la simplificación.

## Global Constraints
- **Sin cambios en `main` ni push de ramas `spike/…`**; cada `spike/…` se borra (`git branch -D`) al cerrar su tarea.
- Sin desplegar `firestore.rules` ni tocar el proyecto Firebase; las reglas solo se prueban con el emulador (`npm run test:emulators`).
- Verificación de cada prototipo: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:ktlintCheck :app:detekt`.
- No hay dispositivo/emulador Android en esta máquina: todo lo que dependa de pantalla, rotación o TalkBack se marca **«sin verificar en dispositivo»** en el informe y nunca cuenta como evidencia.
- Informe en `docs/superpowers/reports/2026-10-08-evaluacion-pendientes-rediseno.md`, en español; commit solo del informe en una rama `agent/evaluacion-pendientes` (sin push hasta que el usuario lo pida).
- Commits con `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Rúbrica común (cada sección del informe la usa tal cual)
| Eje | 1 | 2 | 3 |
|---|---|---|---|
| **Beneficio** | cosmético / ahorro < 50 líneas | evita un fallo raro o ahorra 50–200 líneas o lecturas ocasionales | evita un fallo que verá un usuario normal, o ahorra > 200 líneas / lecturas en cada uso |
| **Coste** | ≤ 3 archivos, < 1 h | 4–10 archivos o toca código fuera del rediseño | > 10 archivos, migración de datos o reglas |
| **Riesgo** | sin cambio de comportamiento y cubierto por tests | cambia comportamiento interno sin test automático | cambia lo que ve el usuario o los datos persistidos, sin test |

**Veredicto:** `Hacer ya` si Beneficio ≥ 2 y Beneficio ≥ Coste y Riesgo ≤ 2; `Más adelante` si Beneficio ≥ 2 pero no cumple lo anterior; `No` si Beneficio = 1 y (Coste ≥ 2 o Riesgo ≥ 2). Para los bugs A–D, Beneficio ≥ 2 por definición. Cada sección del informe: **Qué es · Medido · Prototipo (si lo hubo) · Puntuación · Veredicto · Si se hace: tamaño del PR y cómo verificarlo**.

## Review Focus
1. **Un prototipo que compila no demuestra que funcione:** rotación, back, parpadeo del tema y TalkBack quedan «sin verificar en dispositivo» y bajan la confianza del veredicto; no se dan por buenos.
2. **El ahorro de líneas debe contar los tests:** al medir, se suma `app/src/test` (los Savers tienen tests de ida y vuelta que desaparecerían o habría que reescribir).
3. **Cambios en datos persistidos o reglas** (A–D, D sobre todo) se marcan Riesgo 3 salvo que el emulador lo cubra con un test que falla antes y pasa después.
4. **Coste de Firestore:** cualquier afirmación de «más/menos lecturas» se apoya en el número de consultas del código (no en estimaciones), indicando cuándo se dispara.
5. **Dependencias entre puntos:** si un punto hace innecesario otro (p. ej. arreglar los ViewModels vuelve sobrante `rememberEditorSlot`, o Parcelize cambia cómo se evalúa el helper), la sección lo dice y la Tarea 14 lo usa para ordenar.

---

### Task 0: esqueleto del informe

**Files:** Create: `docs/superpowers/reports/2026-10-08-evaluacion-pendientes-rediseno.md`

- [ ] **Paso 1:** `git switch main && git pull && git switch -c agent/evaluacion-pendientes`.
- [ ] **Paso 2:** crear el informe con: título, fecha, base evaluada (`main` tras fusionar la simplificación), la rúbrica común copiada de este plan, una tabla resumen vacía (Punto · Beneficio · Coste · Riesgo · Veredicto) con las 13 filas A, B, C, D, S1…S9, y una sección vacía por punto con los cinco apartados.
- [ ] **Paso 3:** commit `docs(evaluacion): esqueleto del informe de pendientes del rediseño`.

### Task 1: C — recordatorio a medianoche por cada actividad

**Files (lectura):** `data/reminders/ReminderScheduler.kt:84-93`, `data/repository/FirestoreSportActivityRepository.kt` (`newSportEventData`, `eventParticipants`), `data/reminders/ReminderLeadTime.kt`, tests de recordatorios en `app/src/test`.

- [ ] **Paso 1 — medir:** `grep -rn "allDay" app/src/main/java/com/dlunaunizar/bobitos/data/reminders` (¿se trata `allDay` en algún sitio?), `grep -rln "ReminderScheduler" app/src/test` (¿hay test donde añadir el caso?), y anotar la antelación por defecto (`ReminderLeadTime`).
- [ ] **Paso 2 — prototipo** en `spike/eval-c` con la opción más pequeña: los eventos de todo el día se recuerdan a una hora fija del propio día (p. ej. 9:00) en vez de a medianoche. Test que falla antes y pasa después en el test de `ReminderScheduler` (o en uno nuevo si no hay): un evento `allDay` del día D con antelación 30 min se programa a D 08:30, no a D-1 23:30.
- [ ] **Paso 3 — alternativa a valorar sin prototipo:** no meter al organizador en `participantIds` cuando no participa (afecta a que la actividad aparezca en su calendario personal: es un cambio de producto; anotarlo).
- [ ] **Paso 4:** verificación global; puntuar; escribir la sección C; `git switch agent/evaluacion-pendientes && git branch -D spike/eval-c`; commit del informe.

### Task 2: A — `values-night` reintroduce el parpadeo oscuro al arrancar

**Files (lectura):** `app/src/main/res/values-night/{themes,colors}.xml`, `MainActivity.kt` (`DisposableEffect(darkTheme)`), `DataStoreThemePreferenceRepository.kt:22`, `ThemeViewModel.kt:23`; commits `3cd147e` (PR #57) y `7804ffb` (PR #229).

- [ ] **Paso 1 — medir:** `git show 3cd147e --stat` y su mensaje (qué caso arreglaba y qué coste aceptaba); confirmar el tema por defecto (`LIGHT`).
- [ ] **Paso 2 — prototipo** en `spike/eval-a`: `git rm app/src/main/res/values-night/themes.xml app/src/main/res/values-night/colors.xml` y mantener el `setBackgroundDrawable` de `MainActivity` (cubre a quien elige Oscuro a mano, tras el primer frame). Verificación global (debe compilar sin más cambios).
- [ ] **Paso 3:** comparar los dos costes en la sección (con los datos de `3cd147e`): hoy, destello oscuro para quien usa el valor por defecto con el sistema en oscuro; con el prototipo, destello claro solo para quien elige Oscuro a mano. Anotar «sin verificar en dispositivo».
- [ ] **Paso 4:** puntuar; sección A; borrar `spike/eval-a`; commit.

### Task 3: B — deshacer el borrado del evento de una actividad deja un evento huérfano

**Files (lectura):** `feature/calendar/CalendarScreen.kt:258-264` (`launchUndo` → `save(null, event.toInput())`), `data/repository/FirestoreSportActivityRepository.kt:130-198`, `data/repository/FirestoreCalendarRepository.kt` (`createEvent`/`delete`), `firestore.rules` (create de events con id dado), `tests/firebase-emulators.test.mjs`.

- [ ] **Paso 1 — medir las dos salidas:** (a) el deshacer del calendario restaura el **mismo id** (`set` sobre el id borrado en vez de `add`): ¿lo permiten las reglas de create de `events`? (leer `validNewEvent`); ¿cuántos sitios usan ese deshacer?; (b) el repositorio de deporte recrea el evento si el enlazado no existe (contradice el ruling de 3b de respetar un borrado intencionado).
- [ ] **Paso 2 — prototipo de (a)** en `spike/eval-b`: añadir a `CalendarRepository` un `restoreEvent(spaceId, eventId, input)` que hace `set` sobre `events/{eventId}` y usarlo en el deshacer; test de reglas en el emulador: «restaurar un evento borrado con su id original» `assertSucceeds`. Ejecutar `npm run test:emulators` y la verificación global.
- [ ] **Paso 3:** puntuar (Riesgo 3 si las reglas no lo cubren con test); sección B con la opción recomendada; borrar `spike/eval-b`; commit.

### Task 4: D — editar una actividad reescribe en el evento el nombre de una cuenta borrada

**Files (lectura):** `data/repository/FirestoreSportActivityRepository.kt:141-161` (organizador desde `createdByName`), `data/repository/FirebaseAccountRepository.kt:59-62` (`anonymizeDisplayNames`), `firestore.rules` (`validActivityAnonymization`).

- [ ] **Paso 1 — medir:** ¿existe ya `validActivityAnonymization` en las reglas y qué claves permite? `grep -n "validActivityAnonymization" -A12 firestore.rules`. ¿Qué haría falta: solo añadir `ACTIVITIES` a la lista de colecciones de `anonymizeDisplayNames`?
- [ ] **Paso 2 — prototipo** en `spike/eval-d`: añadir la colección de actividades a `anonymizeDisplayNames` y un test de emulador «al borrar la cuenta se anonimizan `createdByName` y `participantNames` de sus actividades». Ejecutar `npm run test:emulators` y la verificación global.
- [ ] **Paso 3:** puntuar; sección D; borrar `spike/eval-d`; commit.

### Task 5: S1 — borradores con `@Parcelize` en lugar de 12 Savers a mano

**Files (lectura):** los 12 `feature/*/*Draft*.kt` (53 casts posicionales), `app/build.gradle.kts` (bloque `plugins`), `gradle/libs.versions.toml`, los 12 `*DraftTest`.

- [ ] **Paso 1 — medir:** `wc -l app/src/main/java/com/dlunaunizar/bobitos/feature/*/*Draft*.kt app/src/test/java/com/dlunaunizar/bobitos/feature/*/*DraftTest.kt`; contar líneas de Saver y de conversión (`valueOf`, `parse`, `with*`) que desaparecerían.
- [ ] **Paso 2 — prototipo acotado** en `spike/eval-s1`: aplicar el plugin (`id("org.jetbrains.kotlin.plugin.parcelize")` en `plugins`, con la versión de Kotlin del catálogo) y convertir **solo** `NoteDraft` y `TaskDraft` (uno trivial y uno con enums/fechas): `@Parcelize data class … : Parcelable` con tipos reales, `rememberSaveable { mutableStateOf(initial) }` sin `stateSaver`, y adaptar sus tests a un round-trip por `Parcel` o borrarlos si dejan de aportar.
- [ ] **Paso 3:** verificación global; extrapolar a los 12 (líneas netas, archivos); anotar Riesgo (cambia el formato del estado guardado: una actualización con la app en segundo plano restauraría el borrador antiguo una vez); sección S1; borrar la rama; commit.

### Task 6: S2 — que los ViewModels no vuelvan a «cargando» al reobservar (causa raíz de `rememberEditorSlot`)

**Files (lectura):** los 13 ViewModels con `stopObserving`/`stop` (Calendar, PersonalCalendar, Exercises, IngredientDetail, Meals, Notes, Ingredients, Routines, Recipes, Shopping, Spaces, Sport, Tasks), `core/designsystem/component/EditorItem.kt`.

- [ ] **Paso 1 — medir:** para cada ViewModel, si `stop*` borra la clave observada y si `observe` emite `UiState.Loading` aunque la clave no cambie (tabla en la sección). Contar usos de `rememberEditorSlot`/`rememberEditorItem` que sobrarían.
- [ ] **Paso 2 — prototipo** en `spike/eval-s2` en **un** ViewModel (`NotesViewModel`): `stopObserving` cancela el job pero conserva clave y último `Content`; `observe` no emite `Loading` si la clave es la misma. Test unitario: observar, parar, volver a observar el mismo espacio ⇒ el estado no pasa por `Loading`.
- [ ] **Paso 3:** verificación global; extrapolar a los 13; señalar que el helper seguiría haciendo falta para «cerrar si lo borró otra persona» (dependencia con Review Focus 5); sección S2; borrar la rama; commit.

### Task 7: S3 — grafo anidado para «Más»

**Files (lectura):** `core/navigation/BobitosNavHost.kt` (`navigateToTab`, `openFromMore`, rutas Meals/Sport/Notes/SpaceMore), `core/navigation/BobitosDestination.kt` (`workspaceTabFor`), `WorkspaceTabsTest`.

- [ ] **Paso 1 — medir:** ramas especiales que desaparecerían (`navigateToTab` para `SpaceMore`, `openFromMore`, rama de las fichas de «Hoy») y casos de navegación que cambiarían (incluido el minor «atrás desde Comidas abierta desde Hoy»).
- [ ] **Paso 2 — sin prototipo** (el comportamiento de back solo se valida en dispositivo): describir el `navigation(route = "space-more-graph", startDestination = SpaceMore.route) { … }` y qué tests de `WorkspaceTabsTest` cambiarían.
- [ ] **Paso 3:** puntuar (Riesgo 3: cambia la navegación); sección S3; commit.

### Task 8: S4 — metadatos de ruta en el enum

**Files (lectura):** `BobitosDestination.kt`, `RealtimeScopes.kt` (`allSpacesRoutes`, `spacelessRoutes`), `BobitosNavHost.kt:129` (`protectedRoutes`), `workspaceTabFor`, `RealtimeScopeForTest`, `WorkspaceTabsTest`.

- [ ] **Paso 1 — medir:** cuántas listas hay que tocar hoy al añadir una pantalla de espacio (contarlas) y cuántas tras mover `parentTab`, `realtimeScope`, `requiresSpace` al enum.
- [ ] **Paso 2 — prototipo** en `spike/eval-s4`: añadir esas propiedades al enum y derivar `realtimeScopeFor`, `protectedRoutes` y `workspaceTabFor` de ellas; `RealtimeScopeForTest` y `WorkspaceTabsTest` deben pasar **sin cambios** (prueban el mismo comportamiento).
- [ ] **Paso 3:** verificación global; sección S4; borrar la rama; commit.

### Task 9: S5 — el repositorio de deporte reutiliza `EventInput` del calendario

**Files (lectura):** `data/repository/FirestoreSportActivityRepository.kt:316-395` (`newSportEventData`, `sportEventUpdateData`, `dayFields`, `EV_*`), `data/repository/FirestoreCalendarRepository.kt:147-192` (`validated`, `common`, `data`, `updateData`), `data/repository/CalendarRepository.kt:9` (`EventInput`).

- [ ] **Paso 1 — medir:** claves duplicadas entre ambos repositorios (lista de las 16) y diferencias reales (la actualización parcial de deporte que conserva color/descripción/horario; `validated()` que deporte no aplica).
- [ ] **Paso 2 — prototipo** en `spike/eval-s5`: mover `common`/`data` a un `internal object EventDocument` en `data/repository`, usarlo en ambos; los tests de emulador de eventos y actividades deben pasar sin cambios (`npm run test:emulators`) y la verificación global también.
- [ ] **Paso 3:** sección S5 (Riesgo según si los tests de reglas cubren las 16 claves); borrar la rama; commit.

### Task 10: S6 — fuente propia de «todos mis espacios» para el selector

**Files (lectura):** `app/AppViewModel.kt:98-106` (`observedSpaces`, `flatMapLatest`), `core/navigation/RealtimeScopes.kt`, `SpaceSwitcher.kt`, `WorkspaceScaffold.kt`, `BobitosNavHost.kt` (`spacePickerOpen`).

- [ ] **Paso 1 — medir:** listeners que se crean/cancelan al abrir y cerrar el selector hoy (contar por `spaces()` + `space(id)` en `FirestoreSpaceRepository`) frente a un `allSpaces` con `stateIn(WhileSubscribed)` recogido solo por el sheet.
- [ ] **Paso 2 — prototipo** en `spike/eval-s6`: `allSpaces` en `AppViewModel`; `SpacePickerSheet` lo recoge; `pickerOpen` vuelve a `WorkspaceScaffold`; `realtimeScopeFor` pierde el parámetro (ajustar `RealtimeScopeForTest`). Verificación global.
- [ ] **Paso 3:** comprobar que el guard de `selectedSpace == null` (expulsión a «Espacios») no se reactiva al cambiar de espacio: razonarlo con el código del prototipo y marcarlo «sin verificar en dispositivo»; sección S6; borrar la rama; commit.

### Task 11: S7 — validación por campo en los editores

**Files (lectura):** los 8 `*Validation.kt`, editores con mapeo a mano (`RecipeSheets.kt` `isTitleError`, `ShoppingScreen.kt` `nameError`) y editores con `isNotBlank()` ad hoc (`RoutineEditor.kt`, `ExercisesScreen.kt`, `IngredientsScreen.kt`, `IngredientDetailScreen.kt`).

- [ ] **Paso 1 — medir:** en qué editores la regla de la UI difiere de la del ViewModel (p. ej. longitud máxima que la UI no comprueba y el ViewModel sí rechaza) — cada diferencia es un caso «el botón Guardar está activo pero guardar falla».
- [ ] **Paso 2 — sin prototipo** salvo que el Paso 1 encuentre ≥ 1 diferencia con impacto; en ese caso, en `spike/eval-s7`, unificar **solo** ese editor con su `*Validation` y un test unitario de la validación.
- [ ] **Paso 3:** sección S7; borrar la rama si se creó; commit.

### Task 12: S8 — detekt: ignorar `@Composable` en `LongMethod` y `CyclomaticComplexMethod`

**Files (lectura):** `config/detekt/detekt.yml` (ya usa `ignoreAnnotated: ["Composable"]` en `FunctionNaming`), `config/detekt/baseline.xml` (34 entradas de esas dos reglas).

- [ ] **Paso 1 — prototipo** en `spike/eval-s8`: añadir `ignoreAnnotated: ["Composable"]` a ambas reglas, regenerar el baseline (`./gradlew :app:detektBaseline`) y contar cuántas entradas desaparecen; verificación global.
- [ ] **Paso 2:** anotar en la sección el coste: dejar de recibir avisos sobre composables largos (lo que hoy motivó extraer subcomposables útiles) frente a las contorsiones evitadas (`@file:OptIn`, extracciones solo para el límite). Es una decisión de configuración del usuario: el veredicto lo presenta como recomendación.
- [ ] **Paso 3:** sección S8; borrar la rama; commit.

### Task 13: S9 — renombres del borrador en el editor de Compra

**Files (lectura):** `feature/shopping/ShoppingScreen.kt:622-625` (`val name = draft.name`, `quantity`, `notes`, `brand`).

- [ ] **Paso 1 — medir:** usos de las cuatro variables en `ShoppingItemEditor`.
- [ ] **Paso 2:** sección S9 (previsiblemente Beneficio 1, Coste 1, Riesgo 1 ⇒ «Hacer ya» solo si se toca ese archivo por otro motivo; si no, «No»). Commit.

### Task 14: resumen y orden recomendado

**Files:** Modify: el informe.

- [ ] **Paso 1:** rellenar la tabla resumen con las 13 puntuaciones y veredictos.
- [ ] **Paso 2:** ordenar los «Hacer ya» por Beneficio/Coste y agruparlos en PRs propuestos (p. ej. «bugs A–D», «datos/reglas»), aplicando las dependencias detectadas (Review Focus 5).
- [ ] **Paso 3:** comprobar que no queda ninguna rama `spike/…` (`git branch --list 'spike/*'` vacío) y que `main` no ha cambiado.
- [ ] **Paso 4:** commit `docs(evaluacion): veredictos de los pendientes del rediseño` y presentar el resumen al usuario (sin push hasta que lo pida).

## Autorrevisión
- **Cobertura:** 13 puntos → Tasks 1–13 (A, B, C, D, S1–S9) + esqueleto (0) + resumen (14). El push/PR de la simplificación queda en «Antes de empezar».
- **Placeholders:** cada tarea nombra archivos, la medida concreta y, si hay prototipo, el cambio mínimo y su test; los prototipos de navegación (S3) no se hacen a propósito porque solo se validarían en dispositivo.
- **Consistencia:** ramas `spike/eval-<punto>`, informe único en `docs/superpowers/reports/…`, rúbrica única.
- **Review Focus:** los cinco riesgos de la evaluación (prototipo ≠ comportamiento, contar tests, datos/reglas, coste de Firestore con números, dependencias) se aplican en las tareas que los tocan (1–4 reglas/emulador, 5 tests, 6 dependencia, 10 listeners).
