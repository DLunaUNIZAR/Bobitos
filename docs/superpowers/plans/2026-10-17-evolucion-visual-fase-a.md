# Evolución visual · Fase A (defectos visibles) — plan de implementación

> **Para agentes:** se ejecuta con la skill `delegating-plan-execution` del usuario. El marco es superpowers:executing-plans, con un implementador por tarea, en serie y en el orden del plan. Los pasos usan casillas (`- [ ]`).

**Objetivo:** arreglar los defectos visibles de la crítica del 2026-10-17:
- cabeceras e insets;
- FAB que tapa filas;
- contraste de colores de categoría y de la barra inferior;
- avisos incrustados y texto de excepción en pantalla;
- deshacer lo recién añadido;
- formularios que regañan al abrirse;
- fechas, días y plurales;
- y los pequeños defectos de Hoy, Compra, Deporte y las filas con casilla.

**Arquitectura:**
- **Andamios comunes:** dos componentes nuevos en `core/designsystem/component`. `ScreenScaffold` sirve a las pantallas con barra superior propia y `ModuleScaffold` al contenido de un módulo dentro de `WorkspaceScaffold`. Los dos dan insets, el hueco del FAB y su propio `SnackbarHost`, publicado en `LocalSnackbarHostState`.
- **Avisos y errores:** los avisos pasan por efectos comunes de snackbar, y los errores por un único traductor `FailureKind`.
- **Colores de categoría:** cada uno es una función pura `(dark: Boolean) -> Color`, con un accesor `@Composable` que lee `LocalDarkTheme`.
- **Fechas:** se formatean en un único objeto, `BobitosDates`, con locale `es-ES`.

**Tecnología:** Kotlin 2.3.10, Jetpack Compose (BOM 2026.06.00: material3 1.4.0, foundation 1.11.3), Hilt, Firestore, JUnit 4 (JVM) y tests Compose instrumentados (`createAndroidComposeRule<ComponentActivity>()`).

**Spec:** `docs/superpowers/specs/2026-10-17-evolucion-visual-design.md` (fase A). Maquetas aprobadas: `docs/superpowers/specs/2026-10-17-evolucion-visual-maquetas-fase-a.html`.

## Restricciones globales

**Rutas**
- El código vive en `app/src/main/java/com/dlunaunizar/bobitos/` (abreviado `J/`), los tests JVM en `app/src/test/java/com/dlunaunizar/bobitos/` (`T/`) y los instrumentados en `app/src/androidTest/java/com/dlunaunizar/bobitos/` (`A/`).

**Entorno y comandos**
- **Java:** antes de cada `./gradlew`, ejecutar `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- **Test JVM suelto:** `./gradlew :app:testDebugUnitTest --tests "<FQCN>"`.
- **Test instrumentado suelto:** `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`.
  - Necesita un emulador encendido: `export PATH="$HOME/Library/Android/sdk/emulator:$HOME/Library/Android/sdk/platform-tools:$PATH"; nohup emulator -avd Pixel_10 -no-boot-anim >/dev/null 2>&1 & adb wait-for-device`.
  - El emulador está en inglés (`en-US`). Los tests de idioma cuentan con ello.
- **Puerta de cada tarea:** `./gradlew ktlintCheck detekt testDebugUnitTest assembleDebug lintDebug` más los tests instrumentados de la propia tarea.
  - `lintDebug` ya falla en `main` por `RestrictedApi currentBackStack` en `BobitosNavHost`. Ese error, y solo ese, se tolera.
  - Si un cambio de firma rompe `config/detekt/baseline.xml`, se regenera con `./gradlew detektBaseline` y se incluye en el commit de la tarea.

**Textos y estilo**
- Los textos nuevos van en `app/src/main/res/values/strings.xml`. Español, tuteo y punto final en las frases completas. Los conteos van con `<plurals>`.
- Los componentes son Material 3 reales: nada de controles hechos a mano que imiten uno de Material.
- El tema oscuro sigue siendo el de por defecto. Cada color nuevo tiene variante clara y oscura.

**Contraste (umbrales WCAG)**
- Texto ≥ 4,5:1.
- Iconos, puntos y barras ≥ 3:1.
- Se miden contra el fondo real donde se pintan:
  - `background` para la ventana;
  - `background` teñido con el color al 12 % para las tarjetas de `categoryCardColors`;
  - `surfaceContainerHighest`, el contenedor de `Card` por defecto, para chips dentro de tarjetas;
  - `surfaceContainer`, el contenedor de `NavigationBar`, para la barra inferior.

**Alcance**
- Sin dependencias nuevas.
- No se toca la fase B: roles de TalkBack, títulos duplicados, «Más opciones», etc.

## Review Focus

1. **Deshacer al añadir nunca borra algo que ya existía.** En Compra, `addItem` puede reapuntar un comprado (relist) o abrir el diálogo de duplicado. En esos casos no se ofrece Deshacer. Lo prueba la tarea 7.
2. **Snackbar, FAB y barra inferior no se pisan.** Dentro de un módulo, el snackbar sale encima de la barra inferior y el FAB sube por encima del snackbar. Lo prueba la tarea 2.
3. **Un error de guardado con la hoja abierta sale solo en la hoja,** no también en un snackbar detrás de ella. Lo prueba la tarea 6, con `feedbackFor`.
4. **El intento fallido de guardar sobrevive a girar el móvil:** los errores siguen visibles tras recrear. Lo prueba la tarea 9 con `StateRestorationTester`.
5. **En Mi calendario, la flecha de la barra superior hace lo mismo que el Atrás del sistema.** Si se entró a un día desde el mes, vuelve al mes. Si no, sale de la pantalla. Lo prueba la tarea 1.

---

### Tarea 1: `ScreenScaffold`, `ModuleScaffold` y cabeceras con insets (A1)

**Ficheros:**
- Crear: `J/core/designsystem/component/ScreenScaffold.kt`
- Modificar:
  - `J/feature/spaces/SpacesScreen.kt` (raíz en :127, FAB en :199)
  - `J/feature/calendar/PersonalCalendarScreen.kt` (raíz en :114, `NewEventFab` en :345, `BackHandler`)
  - `J/feature/spaces/SpaceSettingsScreen.kt` (raíz en :84; Row del «Atrás» en :89-100)
  - `J/feature/auth/AuthComponents.kt` (`AuthScreenLayout` :28)
  - `J/feature/auth/WelcomeScreen.kt` (:33)
- Test: `A/core/designsystem/component/ScreenScaffoldTest.kt`

**Interfaces:**
- Produce:
  - `@Composable fun ScreenScaffold(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, syncStatus: SyncStatus? = null, actions: @Composable RowScope.() -> Unit = {}, floatingActionButton: @Composable () -> Unit = {}, saving: Boolean = false, content: @Composable (PaddingValues) -> Unit)`
    - Usa `Scaffold`, con `topBar = Column { BobitosTopBar(title, onBack = onBack, actions = actions); syncStatus?.let { SyncStatusBanner(it) } }`.
    - Incluye `snackbarHost` con un `SnackbarHostState` propio, publicado con `CompositionLocalProvider(LocalSnackbarHostState provides …)` alrededor de `content`.
    - Pone el `floatingActionButton` en el hueco del `Scaffold`.
    - Con `saving == true`, superpone un `LinearProgressIndicator` de ancho completo arriba del contenido, sin desplazarlo.
  - `@Composable fun ModuleScaffold(modifier: Modifier = Modifier, floatingActionButton: @Composable () -> Unit = {}, saving: Boolean = false, content: @Composable (PaddingValues) -> Unit)`
    - Igual que el anterior, pero sin barra superior y con `contentWindowInsets = WindowInsets(0)`, porque `WorkspaceScaffold` ya aplica los insets.
- Consume: `BobitosTopBar(title, modifier, onBack, actions)` (`BobitosTopBar.kt:22`), `SyncStatusBanner`, `LocalSnackbarHostState` (`UndoSnackbar.kt:15`).

- [ ] **Paso 1: Escribir los tests que fallan**

```kotlin
class ScreenScaffoldTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun statusBarTopPx(): Int = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!
        .getInsets(WindowInsetsCompat.Type.statusBars()).top

    @Test fun theTitleIsDrawnBelowTheStatusBar() {
        compose.setContent { BobitosTheme { ScreenScaffold(title = "Ajustes del espacio", onBack = {}) { Text("x") } } }
        val top = compose.onNodeWithText("Ajustes del espacio").fetchSemanticsNode().boundsInWindow.top
        assertTrue("title top $top < status bar ${statusBarTopPx()}", top >= statusBarTopPx())
    }

    @Test fun backIsAnIconButtonNotATextButton() {
        compose.setContent { BobitosTheme { ScreenScaffold(title = "T", onBack = {}) { Text("x") } } }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.navigate_back)).assertHasClickAction()
        compose.onAllNodesWithText(compose.activity.getString(R.string.navigate_back)).assertCountEquals(0)
    }

    @Test fun contentCanShowASnackbarThroughTheLocalHost() {
        compose.setContent { BobitosTheme { ScreenScaffold(title = "T", onBack = null) {
            val host = LocalSnackbarHostState.current
            LaunchedEffect(Unit) { host?.showSnackbar("Hola") }
        } } }
        compose.onNodeWithText("Hola").assertIsDisplayed()
    }
}
```

Añade también `PersonalCalendarBackTest` en `A/feature/calendar/` si `PersonalCalendarScreen` puede montarse sin Hilt. Debe comprobar que, con un día abierto desde el mes, pulsar la flecha vuelve al mes. Si exige un ViewModel de Hilt, extrae la decisión a una función pura `internal fun personalCalendarBackAction(drilledFrom: CalendarView?): BackAction`, que devuelve `BackAction.ToPreviousView` o `BackAction.Leave`, y pruébala en `T/feature/calendar/PersonalCalendarBackTest.kt`.

- [ ] **Paso 2: Comprobar que fallan.** Ejecuta `connectedDebugAndroidTest … class=com.dlunaunizar.bobitos.core.designsystem.component.ScreenScaffoldTest`. Resultado esperado: FALLO de compilación, porque `ScreenScaffold` no existe.

- [ ] **Paso 3: Crear `ScreenScaffold` y `ModuleScaffold` con las firmas de «Interfaces».**

- [ ] **Paso 4: Migrar las tres pantallas.**
  - **Tus espacios, Mi calendario y Ajustes del espacio** pasan a `ScreenScaffold`. El título va en la barra (`spaces_title`, `my_calendar_title`, `space_settings_title`) y el FAB en su hueco.
  - **Mi calendario:** su flecha llama a la misma lógica que su `BackHandler`. Con un día abierto, vuelve a la vista anterior; si no, ejecuta `onBack`.
  - **Ajustes del espacio** pierde el `TextButton` «Atrás».
  - El botón de perfil de Tus espacios pasa a `actions`.

- [ ] **Paso 5: Acceso.** En `AuthScreenLayout` y `WelcomeScreen`, sustituye `imePadding()` por `windowInsetsPadding(WindowInsets.safeDrawing)`, que ya incluye el teclado. La composición centrada se queda igual.

- [ ] **Paso 6: Pasar los tests.** Los tests del paso 1 pasan, y la puerta global también.

- [ ] **Paso 7: Commit.** `feat(ui): ScreenScaffold y ModuleScaffold; cabeceras con insets en Espacios, Mi calendario, Ajustes y acceso`

---

### Tarea 2: un único FAB y su hueco al final de la lista (A2)

**Ficheros:**
- Modificar `J/core/designsystem/theme/Spacing.kt`.
- Pantallas con `Scaffold` propio, que pasan a `ScreenScaffold`, con su `floatingActionButton` en el hueco y la lista con el hueco al final:
  - `PantryScreen.kt` (:89, :176)
  - `NotesScreen.kt` (:99, :125, que hoy no tiene hueco)
  - `RecipesScreen.kt` (:163, :209)
  - `RoutinesScreen.kt` (:95, :185)
  - `ExercisesScreen.kt` (:88, :240)
  - `IngredientsScreen.kt` (:95, :257)
  - `IngredientDetailScreen.kt`, solo la cabecera (no tiene FAB)
  - `ProfileScreen.kt`, solo la cabecera
- Módulos de `WorkspaceScaffold`, que envuelven su contenido en `ModuleScaffold` con el FAB en su hueco:
  - `TasksScreen.kt` (:284, :262)
  - `ShoppingScreen.kt` (:320, :212)
  - `CalendarScreen.kt` (:224, :493, :577)
  - `MealsScreen.kt` (`NewMealFab` :689, :201)
  - `SportScreen.kt` (:183, :162)
- Además, `SpacesScreen.kt:189` y `PersonalCalendarScreen.kt:293`, que la tarea 1 ya pasó a `ScreenScaffold`.
- Test: `A/core/designsystem/component/FabClearanceTest.kt`

**Interfaces:**
- Produce: `Spacing.fabClearance = 88.dp`, en `object Spacing`. Es el FAB extendido de 56 dp más los 16 dp de margen y 16 dp de aire.
- Consume: `ScreenScaffold` y `ModuleScaffold` (tarea 1).

- [ ] **Paso 1: Escribir los tests que fallan.** Todos montan `ModuleScaffold` dentro de una `Box(Modifier.size(411.dp, 700.dp))`, con un FAB extendido etiquetado «Nuevo» y una `LazyColumn` de 40 filas (`"Fila $i"`) con `contentPadding = PaddingValues(bottom = Spacing.fabClearance)`.
  - `theLastRowEndsAboveTheFab`: tras `performScrollToIndex(39)`, el borde inferior de «Fila 39» es ≤ el borde superior del nodo «Nuevo».
  - `theFabRisesAboveASnackbar`: tras mostrar un snackbar por `LocalSnackbarHostState`, el borde inferior de «Nuevo» es ≤ el borde superior del texto del snackbar. Es el punto 2 del Review Focus.

- [ ] **Paso 2: Comprobar que fallan.** Resultado esperado: FALLO de compilación, porque `Spacing.fabClearance` no existe.

- [ ] **Paso 3: Añadir `fabClearance` y migrar todas las pantallas listadas.** No queda ningún `Box(...).align(Alignment.BottomEnd).padding(...)` para un FAB, y las listas usan `Spacing.fabClearance`.

- [ ] **Paso 4: Comprobar que no quedan literales.**
  - Ejecuta `grep -rn "88.dp" app/src/main/java | grep -v EmailVerificationScreen`.
  - Resultado esperado: vacío.

- [ ] **Paso 5: Pasar los tests.** `FabClearanceTest` y la puerta global pasan.

- [ ] **Paso 6: Commit.** `fix(ui): un solo FAB por pantalla y hueco común al final de las listas`

---

### Tarea 3: colores de categoría con contraste en claro y oscuro (A3)

**Ficheros:**
- Modificar, poniendo pares claro/oscuro:
  - `J/feature/tasks/TaskTypeTokens.kt` (:37, :47)
  - `J/feature/shopping/SupermarketTokens.kt` (:33)
  - `J/feature/calendar/EventColorTokens.kt` (:11)
  - `J/feature/sport/SportTokens.kt` (:41)
  - `J/feature/exercises/ExerciseTokens.kt` (:47)
  - `J/feature/meals/MealTokens.kt` (:29)
- Modificar `J/core/designsystem/component/InfoChip.kt` (:22).
- Modificar `J/feature/tasks/TasksScreen.kt`:
  - quitar `PriorityDot` (:578, :550);
  - hacer `internal` `TaskMetaRow` (:796) y `MetaChip` (:842);
  - poner la prioridad en la línea de metadatos.
- Modificar `J/feature/exercises/ExerciseImageBlock.kt` (:79).
- Modificar `J/feature/calendar/CalendarScreen.kt` (:949-966, la marca de la muestra de color).
- Tests:
  - `T/core/designsystem/theme/CategoryContrastTest.kt` (nuevo); `SupermarketContrastTest` se integra en él y desaparece.
  - `T/feature/exercises/ExerciseImageBackgroundTest.kt`
  - `A/feature/tasks/TaskMetaRowUiTest.kt`

**Interfaces:**
- **Produce, por cada tabla,** una función pura y su accesor de Compose:
  - `fun TaskType.accent(dark: Boolean): Color` y `@Composable fun TaskType.accent(): Color = accent(LocalDarkTheme.current)`.
  - Lo mismo para `TaskPriority.accent`, `Supermarket.brandColor`, `EventColor.accent`, `SportType.accent`, `ExerciseType.accent` y `MealSlot.accent`.
- **Imagen de ejercicio:** `internal fun exerciseImageBackground(dark: Boolean): Color`. En claro, `Color.White`. En oscuro, un tono claro atenuado, no blanco.
- **`InfoChip`:** misma firma, sin borde. El fondo es `contentColor.copy(alpha = 0.12f)`, con la forma actual.
- **Marca sobre la muestra de color:** `internal fun swatchCheckColor(swatch: Color): Color` devuelve negro o blanco, el que más contraste dé.
- **Consume:** `LocalDarkTheme` (`ModuleColors.kt`) y los `internal val` de `Color.kt` (`backgroundLight/Dark`, `surfaceContainerHighestLight/Dark`).

- [ ] **Paso 1: Escribir el test que falla.** `CategoryContrastTest` reutiliza `luminance`, `contrast` y `tinted` de `SupermarketContrastTest`, con `TINT = 0.12f`. Para cada `dark` en `[false, true]`:
  - **Uso gráfico:** cada valor de `TaskType`, `TaskPriority`, `Supermarket`, `EventColor`, `SportType`, `ExerciseType` y `MealSlot` cumple `contrast(c, bg) >= 3.0` y `contrast(c, tinted(bg, c)) >= 3.0`.
  - **Uso como texto:**
    - Cada `ExerciseType`, que es texto de `InfoChip`, cumple `contrast(c, tinted(cardBg, c)) >= 4.5`, con `cardBg = surfaceContainerHighest(dark)`.
    - Cada `TaskType`, que es texto de `MetaChip`, cumple `contrast(c, tinted(bg, c)) >= 4.5`, porque va en la tarjeta teñida.
  - **Marca sobre la muestra:** cada `EventColor` cumple `contrast(swatchCheckColor(c), c) >= 3.0`.
  - Los mensajes de fallo nombran la categoría y el tema, por ejemplo `"PESO_LIBRE dark: 1.95"`.

  `ExerciseImageBackgroundTest`:
  - `assertNotEquals(Color.White, exerciseImageBackground(dark = true))`
  - `assertTrue(contrast(Color.Black, exerciseImageBackground(true)) >= 10.0)`
  - `assertEquals(Color.White, exerciseImageBackground(false))`

  `TaskMetaRowUiTest`: una tarea con `TaskPriority.MEDIUM` muestra el texto «Media», y una con `HIGH`, «Alta».

- [ ] **Paso 2: Comprobar que fallan.** Resultado esperado: FALLO de compilación, porque no existen las firmas con `dark`.

- [ ] **Paso 3: Implementar.**
  - **Pares de colores:** cada categoría conserva su familia de tono. En oscuro, tonos más claros, como hace `ModulePalette`.
  - **Uso:** todas las llamadas usan el accesor de Compose.
  - **`InfoChip`:** fondo tonal y sin borde.
  - **Prioridad:** `MetaChip(icon = Icons.Rounded.Flag, text = stringResource(priority.labelRes), tint = priority.accent())`. La bandera va teñida y el texto en `onSurfaceVariant`. `PriorityDot` desaparece. Si `TaskPriority` no tiene `labelRes`, reutiliza las cadenas de `PriorityPicker` (:782).

- [ ] **Paso 4: Pasar los tests.** Los tres tests y la puerta global pasan.

- [ ] **Paso 5: Commit.** `fix(ui): colores de categoría con variante clara y oscura que pasan AA; prioridad con bandera y nombre`

---

### Tarea 4: barra inferior legible (A4)

**Ficheros:**
- Modificar `J/core/navigation/WorkspaceScaffold.kt` (:87-110): extraer la barra a `internal fun WorkspaceNavigationBar`.
- Modificar `J/core/navigation/BobitosDestination.kt` (:137).
- Tests:
  - `T/core/navigation/WorkspaceTabColorsTest.kt`
  - `A/core/navigation/WorkspaceNavigationBarTest.kt`

**Interfaces:**
- Produce:
  - `internal fun BobitosDestination.appModule(): AppModule?`
  - `internal data class TabColors(val indicator: Color, val selectedIcon: Color, val selectedLabel: Color)`
  - `internal fun tabColors(module: AppModule, dark: Boolean): TabColors`. Devuelve `(container, onContainer, onSurface)` del tema.
  - `@Composable internal fun WorkspaceNavigationBar(selectedTab: BobitosDestination, onTabSelected: (BobitosDestination) -> Unit)`
- Consume: `ModulePalette.colors(module, dark)`, `onSurfaceLight/Dark` y `surfaceContainerLight/Dark` (`Color.kt`).

- [ ] **Paso 1: Escribir los tests que fallan.**
  - **`WorkspaceTabColorsTest`:** para cada `AppModule` y cada `dark`, `contrast(selectedIcon, indicator) >= 3.0` y `contrast(selectedLabel, navBarBg(dark)) >= 4.5`, con `navBarBg` = `surfaceContainer`.
  - **`WorkspaceNavigationBarTest`:**
    - monta la barra a 411 dp de ancho con `LocalDensity` y `fontScale = 1.3f`;
    - para cada título («Hoy», «Calendario», «Tareas», «Compra», «Más»), obtiene el `TextLayoutResult` con `SemanticsActions.GetTextLayoutResult`;
    - comprueba `assertFalse(layout.isLineEllipsized(0))`.

- [ ] **Paso 2: Comprobar que fallan.** Resultado esperado: FALLO de compilación (no existen `tabColors` ni `WorkspaceNavigationBar`).

- [ ] **Paso 3: Implementar.**
  - **Colores:** indicador opaco `container`, icono `onContainer`, etiqueta seleccionada `onSurface` en SemiBold, y sin selección `onSurfaceVariant`. Hoy y Más usan `NavigationBarItemDefaults.colors()`.
  - **Etiqueta:** `Text(…, maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = <legible>, maxFontSize = MaterialTheme.typography.labelMedium.fontSize))`.

- [ ] **Paso 4: Pasar los tests.** Los dos tests, `WorkspaceTabsTest` y la puerta global pasan.

- [ ] **Paso 5: Commit.** `fix(ui): barra inferior con indicador opaco del módulo y etiquetas que no se cortan`

---

### Tarea 5: errores en lenguaje humano (A6, primera parte)

**Ficheros:**
- Crear `J/core/common/FailureKind.kt`.
- Modificar:
  - `J/core/common/UiState.kt` (:8)
  - `J/core/designsystem/component/StateViews.kt`, si hace falta: `ErrorState` ya acepta `message` y `onRetry`
  - Cada `UiState.Error(error.message)` y su pantalla:
    - `app/AppViewModel.kt:69,202` → `SpacesScreen.kt:78` `ErrorContent`
    - `TasksViewModel.kt:47,54` → `TasksScreen.kt:202`
    - `CalendarViewModel.kt:73,185,213`
    - `PersonalCalendarViewModel.kt:155,193`
    - `SpacesViewModel.kt:41,57` → `SpaceSettingsScreen.kt:149,294`
    - `SportViewModel.kt:61,101`
    - `RoutinesViewModel.kt:49,54`
    - `NotesViewModel.kt:40`
    - `RecipesViewModel.kt:77,82`
    - `MealsViewModel.kt:80,382`
- Tests:
  - `T/core/common/FailureKindTest.kt`
  - Uno nuevo en cada `*ViewModelTest` de esas áreas, incluidos `AppViewModelTest`, `CalendarViewModelTest`, `PersonalCalendarViewModelTest` y `SpacesViewModelTest`

**Interfaces:**
- **Produce:**
  - `enum class FailureKind { PERMISSION, OFFLINE, NOT_FOUND, TIMEOUT, UNKNOWN }`
  - `fun Throwable.toFailureKind(): FailureKind`. Recorre la cadena `cause`:
    - `SaveTimeoutException` → TIMEOUT
    - `FirebaseFirestoreException` con `PERMISSION_DENIED` → PERMISSION
    - `UNAVAILABLE` o `DEADLINE_EXCEEDED` → OFFLINE
    - `NOT_FOUND` → NOT_FOUND
    - `FirebaseNetworkException` o `java.io.IOException` → OFFLINE
    - cualquier otra cosa → UNKNOWN
  - `@StringRes fun FailureKind.messageRes(): Int`
  - `UiState.Error(val failure: FailureKind = FailureKind.UNKNOWN)`, que sustituye a `message: String?`.
  - Calendario:
    - `CalendarUiState.error: FailureKind?` y `notice: CalendarNotice?`, con `enum class CalendarNotice { SAVED, DELETED }`, que sustituyen al `message: String?` de `CalendarViewModel` y de `PersonalCalendarViewModel`.
    - Por ahora siguen pintándose con `CalendarMessage`, ya con `stringResource`. La tarea 6 los pasa al snackbar.
- **Cadenas nuevas:**

| Nombre | Texto |
|---|---|
| `failure_permission` | «No tienes permiso para hacer esto en este espacio.» |
| `failure_offline` | «No hay conexión. Comprueba la red e inténtalo de nuevo.» |
| `failure_not_found` | «Esto ya no existe. Puede que otro miembro lo haya borrado.» |
| `failure_timeout` | «El servidor no responde. Inténtalo de nuevo en un momento.» |
| `failure_unknown` | «Algo ha fallado. Inténtalo de nuevo.» |
| `calendar_notice_saved` | «Cambios guardados.» |

- [ ] **Paso 1: Escribir los tests que fallan.**
  - **`FailureKindTest`:**
    - `TaskRepositoryException(…, cause = FirebaseFirestoreException("x", PERMISSION_DENIED))` → `PERMISSION`
    - `SaveTimeoutException()` → `TIMEOUT`
    - `IOException()` → `OFFLINE`
    - `FirebaseFirestoreException("x", UNAVAILABLE)` → `OFFLINE`
    - `IllegalStateException("boom")` → `UNKNOWN`
    - `FailureKind.entries.forEach { assertNotEquals(0, it.messageRes()) }`
  - **En cada VM:** `aLoadFailureNeverExposesTheExceptionText`. El repositorio falso lanza `RuntimeException("PERMISSION_DENIED: Missing or insufficient permissions.")` y el estado debe ser `UiState.Error(FailureKind.UNKNOWN)`.
  - **Calendario:** el fallo de una acción deja `error == FailureKind.UNKNOWN` y ningún `String`.

- [ ] **Paso 2: Comprobar que fallan.** Resultado esperado: FALLO de compilación (no existen `FailureKind` ni `UiState.Error(failure)`).

- [ ] **Paso 3: Implementar y conectar en las pantallas.**
  - Cada pantalla con error de carga usa `ErrorState(message = stringResource(state.failure.messageRes()), onRetry = …)`.
  - `onRetry` vuelve a llamar al punto de entrada de carga que ya tiene su ViewModel, con los mismos argumentos. Si un ViewModel no tiene ninguno reutilizable, añade `fun retry()` que reinicia su observación.
  - No queda ningún `error.message` ni `throwable.message` en un estado de UI.

- [ ] **Paso 4: Comprobar que no quedan.**
  - Ejecuta `grep -rnE "Error\((error|e|it|throwable)\.message|\.message \?:" app/src/main/java`.
  - Resultado esperado: vacío, salvo usos que no llegan a la UI (por ejemplo, logs). Cada uno de esos se justifica en el informe del implementador.
  - El mapeo de `SaveTimeoutException` a su cadena puede seguir dentro de los `toUiMessage` de cada área.

- [ ] **Paso 5: Pasar los tests.** Los tests nuevos y la puerta global pasan.

- [ ] **Paso 6: Commit.** `fix(ui): los errores se muestran en lenguaje normal (FailureKind), nunca con el texto de la excepción`

---

### Tarea 6: avisos que flotan (A6, segunda parte)

**Ficheros:**
- Modificar `J/core/designsystem/component/UndoSnackbar.kt`.
- Crear `J/core/designsystem/component/FeedbackSnackbar.kt`.
- Quitar los avisos incrustados y conectar el efecto en:
  - `PantryScreen.kt` (`PantryErrorBanner` :224, llamada :98) y `PantryPicker.kt:67`
  - `TasksScreen.kt` (`TaskFeedback` :1045, llamada :190)
  - `ShoppingScreen.kt` (`ShoppingFeedback` :440, llamada :195)
  - `SportScreen.kt` (`SportFeedback` :292, llamada :146)
  - `RoutinesScreen.kt` (:412, llamadas :107-108)
  - `NotesScreen.kt` (`NoteFeedbackBanner` :285, llamada :111)
  - `ExercisesScreen.kt` (:323, llamadas :99-100)
  - `RecipesScreen.kt` (:525, llamada :181)
  - `MealsScreen.kt` (:715, llamada :184)
  - `IngredientsScreen.kt` (`IngredientsFeedback` :409, llamada :108) e `IngredientDetailScreen.kt:234`
  - `SpaceFeedback.kt` (llamadas `SpacesScreen.kt:164`, `SpaceSettingsScreen.kt:119`)
  - `CalendarScreen.kt` (`CalendarMessage` :279) y `PersonalCalendarScreen.kt`
- `AuthFeedback` **no** se toca (spec, A6).
- Tests:
  - `T/core/designsystem/component/FeedbackForTest.kt`
  - `A/core/designsystem/component/FeedbackSnackbarTest.kt`

**Interfaces:**
- **Produce:**
  - `data class Feedback<M>(val message: M, val isError: Boolean)`
  - `fun <M> feedbackFor(error: M?, notice: M?, editorSave: EditorSaveStatus, editorOpen: Boolean): Feedback<M>?`
    - Un error con `editorOpen && editorSave == FAILED` devuelve `null`: ese error ya se muestra en la hoja.
    - Si no, el error tiene prioridad sobre el aviso.
  - `@Composable fun FeedbackSnackbarEffect(feedback: Feedback<String>?, onShown: () -> Unit)`
    - Llama a `onShown()` enseguida.
    - Muestra el mensaje en el `LocalSnackbarHostState` desde un `rememberCoroutineScope()`, para que limpiar el estado no lo cierre.
    - Duración: `Short` si `isError == false`; `Long` si es un error.
  - `showUndo` pasa a `SnackbarDuration.Long`. `launchUndo` mantiene su firma.
- **Consume:**
  - `saving` de `ScreenScaffold` y `ModuleScaffold` (tarea 1). Sustituye a los «Guardando…» de los bloques que desaparecen (`ShoppingFeedback`, `RecipesFeedback`, `MealsFeedback`, `SpaceFeedback`, `IngredientsFeedback`) y se activa cuando se guarda fuera de una hoja.
  - El `clearFeedback()` de cada ViewModel.
  - `CalendarNotice` y `FailureKind` (tarea 5).

- [ ] **Paso 1: Escribir los tests que fallan.**
  - **`FeedbackForTest`:**
    - `assertNull(feedbackFor("e", null, FAILED, editorOpen = true))`
    - `assertEquals(Feedback("e", true), feedbackFor("e", "n", IDLE, editorOpen = false))`
    - `assertEquals(Feedback("n", false), feedbackFor(null, "n", IDLE, false))`
    - `assertEquals(Feedback("e", true), feedbackFor("e", null, FAILED, editorOpen = false))`
  - **`FeedbackSnackbarTest`**, montado en `ScreenScaffold` con una lista de 5 filas:
    - `anInfoMessageFloatsAndGoesAway`: con `Feedback("Producto añadido", false)`, se ve el texto.
    - Además, el borde superior de «Fila 0» no cambia entre antes y después de mostrarse, o sea, no empuja nada.
    - Tras `compose.mainClock.advanceTimeBy(6_000)`, el texto ya no existe.

- [ ] **Paso 2: Comprobar que fallan.** Resultado esperado: FALLO de compilación.

- [ ] **Paso 3: Implementar y migrar las pantallas listadas.**
  - Cada pantalla calcula `feedbackFor(state.error, state.notice, state.editorSave, editorOpen)` y lo pasa al efecto, con el texto ya resuelto mediante `stringResource`. El `onShown` es `viewModel::clearFeedback`.
  - Los avisos que Tareas (:133-138) y Notas (:77-81) ya enseñaban en snackbar pasan al mismo efecto.

- [ ] **Paso 4: Comprobar que no quedan.**
  - Ejecuta `grep -rnE "fun (Pantry|Task|Shopping|Sport|Routines|Exercises|Recipes|Meals|Ingredients|Space)Feedback|PantryErrorBanner|NoteFeedbackBanner|fun CalendarMessage" app/src/main/java`.
  - Resultado esperado: vacío.

- [ ] **Paso 5: Pasar los tests.** Los tests de la tarea y la puerta global pasan.

- [ ] **Paso 6: Commit.** `fix(ui): avisos en snackbar flotante que se cierra solo; fuera los bloques incrustados`

---

### Tarea 7: deshacer al añadir — Tareas, Compra, Notas y Calendario (A6, deshacer)

**Ficheros:**
- Crear `J/core/common/CreatedItem.kt`.
- Modificar `J/core/designsystem/component/UndoSnackbar.kt`.
- Modificar, con el `create` devolviendo el id:
  - `TaskRepository.kt:13` y `FirestoreTaskRepository.kt:62`
  - `ShoppingRepository.kt:17` y `FirestoreShoppingRepository.kt:72`
  - `NoteRepository.kt:9` y `FirestoreNoteRepository.kt:59`
  - `CalendarRepository.kt:27` y `FirestoreCalendarRepository.kt:64`
- Modificar los ViewModels, sus UiState y sus pantallas:
  - `TasksViewModel.kt:70`
  - `ShoppingViewModel.kt:157`
  - `NotesViewModel.kt:51`
  - `CalendarViewModel.kt:131`
  - `PersonalCalendarViewModel.kt:132`
- Modificar los fakes:
  - `FakeTaskRepository` (`TasksViewModelTest.kt:225`)
  - `FakeShoppingRepository` (`ShoppingViewModelTest.kt:721`)
  - `RecordingShoppingRepository`
  - `FakeNoteRepository`
  - `RecordingCalendarRepository`
  - `PersonalRecordingCalendarRepository`
  - y cualquier otro implementador de esas interfaces que no compile
- Tests: en `TasksViewModelTest`, `ShoppingViewModelTest`, `NotesViewModelTest`, `CalendarViewModelTest` y `PersonalCalendarViewModelTest`.

**Interfaces:**
- **Produce:**
  - `data class CreatedItem(val id: String, val name: String)`
  - En cada UiState de estas áreas, `val created: CreatedItem? = null`.
  - En cada ViewModel, `fun consumeCreated()` y `fun undoCreate(id: String)`. Este último borra con el `delete…` que ya existe. Si falla, deja un error que se muestra por el snackbar de la tarea 6.
  - `@Composable fun CreatedUndoEffect(created: CreatedItem?, onConsumed: () -> Unit, onUndo: (CreatedItem) -> Unit)`
    - Llama a `onConsumed()` y luego a `launchUndo` desde un `rememberCoroutineScope()`.
    - Mensaje: `stringResource(R.string.created_undo_message, name)`, nueva cadena con el texto «Has añadido «%1$s»». Acción: `R.string.undo`.
  - Los `create` del repositorio devuelven `String`, el id nuevo:
    - `suspend fun createTask(…): String`
    - `suspend fun addItem(…): String`
    - `suspend fun addNote(spaceId, title, body): String`
    - `suspend fun createEvent(spaceId: String, input: EventInput, eventId: String? = null): String`
- **Regla:** un alta desde la hoja deja `created = CreatedItem(id, nombre)` **en lugar del** aviso de creación (`TaskCreated`, `NoteAdded`, `Saved`…). Las ediciones y los `restore*` no lo hacen.
  - En **Compra** solo se crea cuando `match == null` (`ShoppingViewModel.kt:186`). Reapuntar un comprado (:188-199) y sumar a un duplicado (`sumDuplicate` :210) dejan su aviso de siempre y `created == null`.

- [ ] **Paso 1: Escribir los tests que fallan** (en cada ViewModel).
  - `creatingOneItemOffersUndoWithItsIdAndName`:
    - tras crear «Leche», el fake devuelve `"new-1"` y `state.created == CreatedItem("new-1", "Leche")`;
    - además, `notice == null`.
  - `undoCreateDeletesExactlyThatItem`: `undoCreate("new-1")` hace que el fake registre `deleted == listOf("new-1")`.
  - `editingDoesNotOfferUndo`: tras `update`, `created == null`.
  - Solo en Compra, `relistingAPurchasedItemNeverOffersUndo`, que es el punto 1 del Review Focus:
    - hay un comprado «Leche»;
    - `addItem("Leche", …)` deja `created == null`;
    - y el fake no registra ningún borrado.

- [ ] **Paso 2: Comprobar que fallan.** Resultado esperado: FALLO de compilación (no existen `created` ni `undoCreate`).

- [ ] **Paso 3: Implementar.**
  - Los repositorios devuelven `document.id`. Calendario devuelve `eventId ?: generado`.
  - Cada pantalla usa `CreatedUndoEffect(state.created, viewModel::consumeCreated) { viewModel.undoCreate(it.id) }`.
  - Mi calendario ya tiene snackbar gracias a `ScreenScaffold` (tarea 1).

- [ ] **Paso 4: Pasar los tests.** Los tests de las cinco áreas y la puerta global pasan.

- [ ] **Paso 5: Commit.** `feat(ui): deshacer lo recién añadido en Tareas, Compra, Notas y Calendario`

---

### Tarea 8: deshacer al añadir — Comidas, Deporte, Recetas, Rutinas, Ejercicios, Ingredientes y marcas

**Ficheros:**
- Modificar, con el `create` devolviendo el id:
  - `MealRepository.kt:11` y `FirestoreMealRepository.kt:67`
  - `SportActivityRepository.kt:12` y `FirestoreSportActivityRepository.kt:74` (devuelve el id de la actividad; su borrado ya borra el evento enlazado)
  - `RecipeRepository.kt:16` y `FirestoreRecipeRepository.kt:47`
  - `RoutineRepository.kt:18` y `FirestoreRoutineRepository.kt:43`
  - `ExerciseRepository.kt:20` y `FirestoreExerciseRepository.kt:60` (devuelve `slug(name)`)
- Modificar los ViewModels, sus UiState y sus pantallas:
  - `MealsViewModel.kt:135`
  - `SportViewModel.kt:130`
  - `RecipesViewModel.kt`: `saveDraft` :254, que cubre crear e importar, y `fork` :298, que es «Guardar como mía»
  - `RoutinesViewModel.kt`: `createRoutine` :83 y `fork` :107
  - `ExercisesViewModel.kt:83`
  - `IngredientsViewModel.kt:119`: solo el ingrediente **personal** (`common == false`); el `id` devuelto ya no se descarta
  - `IngredientDetailViewModel.kt:202`: `saveBrand`, cuando crea
- Modificar los fakes correspondientes (`FakeMealRepository`, `FakeSportActivityRepository`, `FakeRecipeRepository`, `FakeRoutineRepository`, `FakeExerciseRepository`, `FakePersonalIngredientRepository`, `FakeIngredientBrandRepository`) para que devuelvan ids y registren los borrados.
- Tests: en los `*ViewModelTest` de esas áreas.

**Interfaces:**
- Consume `CreatedItem`, `CreatedUndoEffect` y la regla «`created` en lugar del aviso de creación» de la tarea 7.
- **Quedan fuera** (no producen `created`):
  - `duplicateDay` y `duplicateWeekToNext`;
  - «Añadir a la compra» de recetas y comidas, y `confirmCooked`;
  - el selector y la reposición de la Despensa;
  - `createPersonalForScan`, `saveScannedBrand` y el ingrediente común de administración;
  - todos los `restore*`.

- [ ] **Paso 1: Escribir los tests que fallan.** Son los mismos tres tests de la tarea 7 en cada área:
  - `creatingOneItemOffersUndoWithItsIdAndName`
  - `undoCreateDeletesExactlyThatItem`
  - `editingDoesNotOfferUndo`

  Y estos específicos:
  - **Recetas:** `forkingOffersUndoForTheCopy` (`created.id` es el id nuevo, no el de la original).
  - **Comidas:** `duplicatingADayNeverOffersUndo`.

- [ ] **Paso 2: Comprobar que fallan.** Resultado esperado: FALLO de compilación.

- [ ] **Paso 3: Implementar,** como en la tarea 7.

- [ ] **Paso 4: Pasar los tests.** Los tests de las siete áreas y la puerta global pasan.

- [ ] **Paso 5: Commit.** `feat(ui): deshacer lo recién añadido en Comidas, Deporte, Recetas, Rutinas, Ejercicios, Ingredientes y marcas`

---

### Tarea 9: formularios que no regañan al abrirse (A5)

**Ficheros:**
- Modificar `J/core/designsystem/component/BobitosFormSheet.kt` (:48, :157-172).
- Crear `J/core/designsystem/component/FormValidation.kt`.
- Modificar los editores:
  - `TasksScreen.kt:680`: error en :734, `showInvalidDate`
  - `ShoppingScreen.kt:662`
  - `CalendarScreen.kt:764`: error de fechas en :820
  - `SportScreen.kt:488`
  - `RoutineEditor.kt:53`
  - `NotesScreen.kt:254`: error en :278
  - `ExerciseEditorSheet.kt:47`: `visibleDraftErrors`
  - `RecipeSheets.kt:176`
  - `MealsScreen.kt:583`
  - `IngredientsScreen.kt:361`
  - `IngredientDetailScreen.kt:486`
- Modificar los diálogos de espacio: `SpaceNameDialog` (`SpacesScreen.kt:386`) e `InvitationCodeDialog` (:275), con `DialogError` en :427.
- Hacer `internal` los editores que haga falta para los tests (por ejemplo, `TaskEditor`).
- Tests:
  - `T/core/designsystem/component/FormValidationTest.kt`
  - `A/feature/tasks/TaskEditorUiTest.kt`
  - `A/feature/calendar/EventEditorUiTest.kt`

**Interfaces:**
- **Produce:**
  - `@Stable class FormValidationState(attempted: Boolean = false)` con:
    - `var attempted: Boolean`;
    - `fun <E> visible(error: E?): E? = error.takeIf { attempted }`;
    - `fun submit(isValid: Boolean, onValid: () -> Unit, onInvalid: () -> Unit)`, que pone `attempted = true` y llama a una de las dos.
  - `@Composable fun rememberFormValidationState(): FormValidationState`, que se guarda con `rememberSaveable`.
  - `BobitosFormSheet(title, confirmLabel, saving, dirty, onConfirm, onDismiss, errorMessage = null, canSubmit: Boolean = true, content)`
    - Desaparece `confirmEnabled`.
    - Guardar queda `enabled = canSubmit && !saving`.
    - `canSubmit` solo es `false` donde hoy se bloquea sin conexión: Calendario, Deporte, Rutinas, Recetas y Comidas.
- **Regla:**
  - Cada editor llama a `validation.submit(isValid = error == null, onValid = save, onInvalid = { firstInvalidFocus.requestFocus() })`.
  - Pinta cada error con `isError = validation.visible(e) != null` y su `supportingText`, **bajo su campo**. El texto rojo suelto al pie desaparece.
  - Los errores se recalculan en cada composición, así que desaparecen al corregir.
  - El error de fechas del evento va junto a los selectores de fecha, con `liveRegion = Polite`.
  - Los diálogos de espacio pasan `DialogError` a `supportingText` del campo.

- [ ] **Paso 1: Escribir los tests que fallan.**
  - **`FormValidationTest`:**
    - `assertNull(FormValidationState().visible("e"))`
    - tras `submit(false, {}, {})`, `assertEquals("e", state.visible("e"))` y `assertTrue(state.attempted)`
    - `submit(true, onValid = { called = true }, {})` hace `called == true`
  - **`TaskEditorUiTest`:**
    - `opensWithoutErrors`: al montar `TaskEditor` vacío, `onAllNodesWithText("Introduce un título.").assertCountEquals(0)` y Guardar `assertIsEnabled()`.
    - `showsTheErrorUnderTheTitleAfterSaving`: al pulsar Guardar se ve «Introduce un título.». Al escribir «Limpiar» en Título, desaparece.
    - `theAttemptSurvivesRotation`: con `StateRestorationTester`, se pulsa Guardar, `emulateSavedInstanceStateRestore()` y el error sigue visible. Es el punto 4 del Review Focus.
  - **`EventEditorUiTest`:** `theDateErrorClearsWhenTheDatesAreFixed`. Fin antes del inicio y Guardar muestran el error de fechas; poner el fin igual al inicio lo quita.

- [ ] **Paso 2: Comprobar que fallan.**
  - El test JVM falla al compilar (no existe `FormValidationState`).
  - Los instrumentados fallan en `opensWithoutErrors`, porque hoy el error sale al abrir.

- [ ] **Paso 3: Implementar** en `BobitosFormSheet`, en los 11 editores y en los dos diálogos.
  - Ajusta los tests de validación existentes si comprobaban que Guardar estaba apagado.
  - Ningún editor pasa ya `confirmEnabled`.

- [ ] **Paso 4: Pasar los tests.** Los tests de la tarea, los de validación existentes y la puerta global pasan.

- [ ] **Paso 5: Commit.** `fix(ui): los formularios se abren sin errores y los muestran bajo su campo al intentar guardar`

---

### Tarea 10: fechas en español, días y plurales (A7)

**Ficheros:**
- Crear `J/core/common/BobitosDates.kt`.
- Crear `J/core/designsystem/component/AppLocale.kt`.
- Sustituir todos los sitios de formato de fecha:
  - `SpaceTodayScreen.kt:76-77`
  - `TasksScreen.kt:875,896,1066`
  - `PersonalCalendarScreen.kt:300,329`
  - `CalendarScreen.kt:293,297-301,385,428,499,647,675,975,976`
  - `SpaceSettingsScreen.kt:329`
  - `SportScreen.kt:617-622`
  - `MealsScreen.kt:750-759`
- Selectores:
  - `AppDatePickerDialog.kt:23`
  - `CalendarScreen.kt:1015` (`EventTimePickerDialog`)
- En `strings.xml`, pasar a `<plurals>`:
  - `routines_exercise_count`
  - `routines_summary_strength`
  - `recipes_pantry_missing_more`
  - `pantry_picker_confirm`
  - `recurrence_every_n_days`, `recurrence_every_n_weeks` y `recurrence_every_n_months`
  - `home_workload_counts`, que se divide en `home_workload_pending` y `home_workload_done`

  Y actualizar sus usos:
  - `SportScreen.kt:349` y `RoutinesScreen.kt:285`
  - `SpaceHomeSections.kt:93`
  - `RecipePantry.kt:64`
  - `TasksScreen.kt:973-975`
- Tests:
  - `T/core/common/BobitosDatesTest.kt`
  - `T/core/common/PluralsTest.kt`
  - `A/core/designsystem/component/AppDatePickerLocaleTest.kt`

**Interfaces:**
- **Produce:** `object BobitosDates`, con `val locale: Locale = Locale.forLanguageTag("es-ES")` y estas funciones:

| Función | Ejemplo |
|---|---|
| `fun longDay(date: LocalDate): String` | «Sábado, 17 de octubre» |
| `fun month(month: YearMonth): String` | «Octubre de 2026» |
| `fun weekRange(start: LocalDate, end: LocalDate): String` | «12–18 oct 2026»; si cruza el mes, «28 sept – 4 oct 2026» |
| `fun dayHeader(date: LocalDate): String` | «Lunes 12» |
| `fun shortDate(date: LocalDate): String` | «17 oct 2026» |
| `fun dateTime(dateTime: LocalDateTime): String` | «17 oct, 10:30» |
| `fun time(time: LocalTime): String` | «10:30» |
| `fun accessibleDate(date: LocalDate): String` | «sábado, 17 de octubre de 2026», para descripciones de accesibilidad |
| `fun dayInitial(day: DayOfWeek): String` | L, M, X, J, V, S, D |

- **Produce también:** `@Composable fun AppLocale(content: @Composable () -> Unit)`. Da a su contenido `LocalConfiguration` y `LocalContext` con locale `es-ES`, para que los selectores de Material salgan en español y con la semana empezando en lunes.

- [ ] **Paso 1: Escribir los tests que fallan.**
  - **`BobitosDatesTest`**, con `@Before { Locale.setDefault(Locale.US) }` y `@After` que lo restaura. Cada caso sale de la tabla de «Interfaces»:
    - `month(YearMonth.of(2026, 10)) == "Octubre de 2026"`
    - `longDay(2026-10-17) == "Sábado, 17 de octubre"`
    - `weekRange(2026-10-12, 2026-10-18) == "12–18 oct 2026"`
    - `weekRange(2026-09-28, 2026-10-04) == "28 sept – 4 oct 2026"`
    - `dayHeader(2026-10-12) == "Lunes 12"`
    - `shortDate(2026-10-17) == "17 oct 2026"`
    - `dateTime(2026-10-17T10:30) == "17 oct, 10:30"`
    - `time(10:30) == "10:30"`
    - `DayOfWeek.entries.map(::dayInitial) == listOf("L","M","X","J","V","S","D")`
  - **`PluralsTest`**: lee `src/main/res/values/strings.xml`, igual que `ShoppingStringsTest`. Para cada nombre de la lista de plurales:
    - existe `<plurals name="…">` con `item quantity="one"` e `item quantity="other"`;
    - no existe `<string name="…">`.
  - **`AppDatePickerLocaleTest`**, en el emulador en inglés: al montar `AppDatePickerDialog(LocalDate.of(2026, 10, 17), {}, {})`:
    - `onAllNodesWithText("octubre", substring = true, ignoreCase = true)` tiene al menos un nodo;
    - `onAllNodesWithText("October", substring = true)` tiene cero.

- [ ] **Paso 2: Comprobar que fallan.**
  - Los JVM fallan al compilar o por los plurales.
  - El instrumentado falla porque hoy sale «October».

- [ ] **Paso 3: Implementar y sustituir todos los sitios.**
  - Envuelve `AppDatePickerDialog` y `EventTimePickerDialog` en `AppLocale`.
  - Las iniciales de Comidas (`MealsScreen.kt:759`), Deporte (`SportScreen.kt:622`) y la cabecera del mes (`CalendarScreen.kt:385`) usan `dayInitial`.

- [ ] **Paso 4: Comprobar que no quedan.**
  - Ejecuta `grep -rnE "ofPattern\(|ofLocalized(Date|DateTime|Time)\(|Locale\(\"es" app/src/main/java/com/dlunaunizar/bobitos/feature app/src/main/java/com/dlunaunizar/bobitos/core/navigation`.
  - Resultado esperado: vacío.
  - No cuentan los `Collator` de ordenación ni los formatos que no se muestran (claves de documento, parseo). Cada uno de esos se justifica en el informe del implementador.

- [ ] **Paso 5: Pasar los tests.** Los tres tests y la puerta global pasan.

- [ ] **Paso 6: Commit.** `fix(ui): fechas en español con un único formateador, X para el miércoles y plurales`

---

### Tarea 11: pequeños defectos visibles (A8)

**Ficheros:**
- Modificar:
  - `J/feature/spaces/SpaceHomeViewModel.kt` (:46)
  - `J/core/navigation/SpaceTodayScreen.kt` (:43, :57-117)
  - `J/feature/spaces/SpaceHomeSections.kt` (:55-63)
  - `J/feature/spaces/SpaceHomeDigest.kt`
  - `J/feature/shopping/ShoppingScreen.kt` (`ShoppingEmptyState` :484, carga en :199)
  - `J/feature/sport/SportScreen.kt` (`ActivityCard` :310)
  - `J/feature/common/MemberCheckboxes.kt` (:13)
  - `J/feature/calendar/CalendarScreen.kt` (:794-797)
  - `res/values/strings.xml` (`home_my_day_events`)
- Tests:
  - `T/feature/spaces/SpaceHomeViewModelTest.kt` (nuevo)
  - `T/feature/spaces/SpaceHomeDigestTest.kt`
  - `A/feature/common/MemberCheckboxesUiTest.kt`
  - `A/feature/sport/ActivityCardUiTest.kt`

**Interfaces:**
- **Produce:**
  - `internal fun groupTitles(events: List<CalendarEvent>): List<Pair<String, Int>>`, en `SpaceHomeDigest.kt`. Agrupa por título, conserva el orden de primera aparición y cuenta las repeticiones.
  - `home_my_day_event_repeated` = «%1$s (%2$d)». `home_my_day_events` pierde el emoji y la fila pasa a llevar `Icon(Icons.Rounded.Event, contentDescription = null)`.
- **Comportamiento:**
  - **Carga de Hoy:**
    - `SpaceHomeViewModel.load` pone `counts` y `digest` a `null` al empezar.
    - El digest observa sus flujos con `combine`, en lugar de `first()`, así que se corrige solo cuando llega el dato del servidor.
    - `SpaceTodayScreen` muestra `LoadingState()` mientras `digest == null`, y nunca «Hoy no tienes nada pendiente» antes de tener datos.
  - **Compra vacía:** `EmptyState(title = shopping_empty, icon = Icons.Rounded.ShoppingCart, description = shopping_empty_description)`, sin botón. La carga usa `LoadingState()`.
  - **Tarjeta de actividad:** `Card(onClick = onEdit)`, que abre la edición igual que una tarea. Guardar sigue desactivado sin conexión (tarea 9).
  - **Filas con casilla:**
    - Cada fila de `MemberCheckboxes` y la de «Todo el día» llevan `Modifier.toggleable(value, role = Role.Checkbox, onValueChange = …)`, con `Checkbox(onCheckedChange = null)`.
    - Es el patrón de `CookedCheckRow` (`CookedDialog.kt:105`).

- [ ] **Paso 1: Escribir los tests que fallan.**
  - **`SpaceHomeDigestTest`:** `groupTitles(listOf(ev("Gimnasio"), ev("Cena"), ev("Gimnasio"))) == listOf("Gimnasio" to 2, "Cena" to 1)`. Añade un constructor `ev(title)`.
  - **`SpaceHomeViewModelTest`**, con fakes de flujos `MutableSharedFlow`:
    - `switchingSpaceClearsTheOldDigest`: tras `load("a")` con datos, `load("b")` deja `digest == null` hasta que emiten los flujos de `"b"`.
    - `aLaterServerSnapshotUpdatesTheDigest`: primera emisión de tareas vacía y luego una tarea mía que vence hoy. `digest.todayItemCount()` pasa de 0 a 1.
  - **`MemberCheckboxesUiTest`:** pulsar el **nombre** «Ana» llama a `onToggle("ana", true)`.
  - **`ActivityCardUiTest`:** pulsar la tarjeta llama a `onEdit`.

- [ ] **Paso 2: Comprobar que fallan.** Resultado esperado:
  - FALLO de compilación (`groupTitles`);
  - aserciones fallidas en el ViewModel;
  - en la UI, el nombre no marca y la tarjeta no responde.

- [ ] **Paso 3: Implementar** lo descrito en «Comportamiento».

- [ ] **Paso 4: Pasar los tests.** Los tests de la tarea y la puerta global pasan.

- [ ] **Paso 5: Commit.** `fix(ui): Hoy espera a sus datos, «Mi día» sin emoji ni repetidos, Compra vacía común, actividad que se abre y filas que marcan enteras`

---

### Tarea 12: verificación completa en el emulador

Esta tarea no toca código del producto. Es la comprobación visual que pide la spec («Llamadas mías», última línea).

**Ficheros:**
- Crear `docs/superpowers/reports/2026-10-17-evolucion-visual-fase-a-verificacion.md`.
- Las capturas van en la carpeta de trabajo del ledger, no en el repositorio.

- [ ] **Paso 1: Pasar la suite instrumentada completa.**
  - Ejecuta `./gradlew :app:connectedDebugAndroidTest`.
  - Resultado esperado: BUILD SUCCESSFUL, con todos los tests en verde, incluidos los de las tareas 1-11 y los que ya existían.

- [ ] **Paso 2: Preparar el entorno de datos.**
  - Instala la variante debug y prepara Firebase local siguiendo `docs/FIREBASE_DEVELOPMENT.md`: `npm run emulators` con el JBR 21 de Android Studio, `npm run android:connect-emulators` y `npm run seed:emulators`.
  - Si el seed no crea un usuario con sesión, regístralo en la app y verifícalo con el `oobLink` del Auth Emulator.

- [ ] **Paso 3: Capturar.** En oscuro, en claro y con `adb shell settings put system font_scale 1.3`, captura:
  - Mi calendario, Ajustes del espacio, Tus espacios y Login;
  - Calendario en vista Semana con eventos, al final de la lista;
  - Recetario al final;
  - Ejercicios, con el chip «Peso libre»;
  - una tarea con prioridad Media;
  - la barra inferior;
  - Crear tarea recién abierta y después de pulsar Guardar en vacío;
  - añadir «Leche» a la Compra, con el snackbar «Has añadido «Leche»»;
  - Comidas y Deporte con su tira de días;
  - Hoy recién abierto.

  Al terminar, restaura `font_scale 1.0`.

- [ ] **Paso 4: Escribir el informe.** El informe tiene una tabla con una fila por cada punto de A1-A8 de la spec: «se ve como en la maqueta», sí o no, con la captura que lo prueba. Si alguna fila sale «no», la tarea se da por fallida y se trata en «Atascos».

- [ ] **Paso 5: Commit.** `docs(ui): verificación visual de la fase A de la evolución visual`
