# Guardado y errores (G2, plan 1): plan de implementación

> **Para agentes:** se ejecuta con la skill `delegating-plan-execution` del usuario. El marco es superpowers:executing-plans.
> - Es un **plan grande** (10 tareas): pasos 1 a 6, con `simplify`.
> - Cada tarea la hace un subagente `sonnet`, en serie y en el orden del plan. La revisión final la hace `opus` (si falla, `fable`).
> - Los commits, el push y el PR los hace el coordinador.
> - **Interfaz:** las tareas 2 a 10 tocan pantallas, pero no su aspecto. Todas llevan `Frontend: sin skill de diseño (el plan fija el diseño)`. La revisión de diseño (después del paso 3) se limita a las pantallas donde cambia lo que se ve: Tareas, Espacios, Ajustes del espacio, Ingredientes y Despensa. Lo que encuentre y sea anterior al plan va al backlog como menor.
> - **Ninguna tarea cambia `firestore.rules`, `migrations/`, `strings.xml` ni los scripts de Node.**
> - **Cierre (paso 6):** en el commit del backlog se marcan como resueltas en `agent/guardado-y-errores` las 11 líneas de la tabla «Plan 1» de la spec, y como ya resueltas en `agent/evolucion-visual-fase-a` (`a21ce42`) las dos de «Ya resueltas». Se buscan por su texto. Después, el trabajo siguiente que se propone es escribir el plan 2 (Deshacer) desde la misma spec, en una sesión limpia.
> - Los pasos usan casillas (`- [ ]`).

**Objetivo:** que los 14 ViewModels con guardado usen una sola pieza común para escrituras en curso, editor, errores, avisos y altas, de forma que ningún error se vea dos veces, ninguna acción apague «Guardando…» o borre el aviso de otra, y una cancelación lanzada por un repositorio no deje nada colgado.

**Arquitectura:**
- `SaveState<E, N>` (estado de guardado embebido en el `UiState` de cada área) y `SaveRunner<S, E, N>` (dueño de sus transiciones) en `core/common`. Cada ViewModel crea su `SaveRunner` y lo expone a la pantalla como `SaveFeedbackControls`.
- Helpers de pantalla en `core/designsystem/component/SaveFeedback.kt`: qué sale en el aviso (`screenFeedback`), el efecto que lo muestra y lo consume (`SaveFeedbackEffect`) y el que cierra el editor (`EditorSaveEffect` nuevo).
- La tarea 1 hace las piezas; las tareas 2 a 10 migran las áreas, de dos en dos o de tres en tres; la 10 borra lo viejo.

**Tecnología:** Kotlin 2.3.10, Jetpack Compose (BOM 2026.06.00), Hilt, coroutines. Tests JUnit 4 con `kotlinx-coroutines-test` y `MainDispatcherRule`; un test instrumentado de Compose (`createAndroidComposeRule<ComponentActivity>()`).

**Spec:** `docs/superpowers/specs/2026-10-19-guardado-errores-deshacer-design.md`, parte 1. Las líneas Lnnn son de `docs/superpowers/backlog.md` en `746d516`.

## Restricciones globales

**Rutas**
- `J/` = `app/src/main/java/com/dlunaunizar/bobitos/`.
- `T/` = `app/src/test/java/com/dlunaunizar/bobitos/` (JVM).
- `A/` = `app/src/androidTest/java/com/dlunaunizar/bobitos/` (instrumentados).

**Entorno y comandos**
- **Java:** antes de cada `./gradlew`, ejecuta `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- **Test JVM suelto:** `./gradlew :app:testDebugUnitTest --tests "<FQCN>"`.
- **Test instrumentado suelto:** `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`. Si `adb devices` no lista el emulador: `export PATH="$HOME/Library/Android/sdk/emulator:$HOME/Library/Android/sdk/platform-tools:$PATH"; nohup emulator -avd Pixel_10 -no-boot-anim >/dev/null 2>&1 & adb wait-for-device`.
- **Puerta de cada tarea (`gate.sh`):** `./gradlew ktlintCheck detekt testDebugUnitTest assembleDebug lintDebug :app:compileDebugAndroidTestKotlin`, más el instrumentado que nombre la tarea. Las suites de Node no cambian en este plan: el coordinador las pasa en la comprobación previa y antes del cierre.
- Si un cambio rompe `config/detekt/baseline.xml`, regenera el baseline con `./gradlew detektBaseline` e inclúyelo en la tarea. Su diff solo puede cambiar entradas de las funciones que la tarea mueve o borra.

**Reglas de la migración (tareas 2 a 10)**
- **Comportamiento:** el de la parte 1 de la spec, punto por punto. Lo que la spec no cambia se queda como hoy: mismos avisos, mismos textos y misma puerta.
- **«En curso»:** pasa por `SaveRunner.run`/`create` exactamente lo que hoy enciende `isSaving` (o `saving`/`isLoading`), también las lecturas que lo encienden a propósito. Lo que hoy no lo enciende (`launchPerIdWrite`, `writeOptimistically`, `restock`) sigue sin encenderlo y usa `showError`/`showNotice` y `rethrowIfCancelled`.
- **Estado:** el `UiState` del área cambia `isSaving`, `editorSave`, `error`, `notice` y `created` (y `saving`, `isLoading` o `writeStatus` donde los haya) por un solo campo `val save: SaveState<E, N> = SaveState()`. El resto de campos se quedan.
- **ViewModel:**
  - crea `private val saver = SaveRunner(viewModelScope, mutableUiState, <UiState>::save, { copy(save = it) }, Throwable::toUiMessage)` (o la traducción del área);
  - expone `val feedback: SaveFeedbackControls get() = saver`;
  - borra su `runAction`/`action`, su `showError` y sus `consumeCreated`, `consumeEditorSave` y `clearFeedback`, salvo que `clearFeedback` haga algo más (en ese caso se queda solo eso).
- **Pantalla:**
  - el aviso con `SaveFeedbackEffect(state.save, viewModel.feedback, covered = <editor u otra cosa abierta>, …)`;
  - el editor con el `EditorSaveEffect` nuevo;
  - el error del formulario con `state.save.editorError`;
  - «Guardando…» con `state.save.isSaving`;
  - cerrar el editor llama a `feedback.resetEditor()`.
- **Diálogos que no son editores** («Has cocinado», el selector de la Despensa, importar receta): mientras están abiertos, cuentan en `covered` y muestran dentro `state.save.error`, como hoy. Al cerrarse llaman a `feedback.consumeError()`.
- **Tests:**
  - se conservan todos los casos de los tests existentes, adaptados a `state.save.*`. Un error del editor se comprueba ahora en `save.editorError`, no en `save.error`;
  - un test solo se borra si prueba una API que desaparece, y su caso pasa a otro test;
  - los nombres siguen el estilo de su fichero: en los que mezclan estilos, frases en inglés entre comillas invertidas.
- **Comentarios:** en español y con la densidad del fichero. Los KDoc que describan el `runAction` borrado se quitan o se corrigen.

## Review Focus

1. **Guardar desde el editor y que salga su aviso:** al terminar bien, la hoja se cierra y el aviso de éxito («Tarea guardada» y equivalentes) sale después, con la hoja ya cerrada; no se pierde mientras `covered` pasa de true a false. Lo prueba `screenFeedback` en la tarea 1; el cableado lo mira el revisor en cada pantalla.
2. **Marcar varias muy seguidas (completar o comprar) no enciende «Guardando…» ni bloquea otras acciones.** Test en las tareas 2 y 3.
3. **Un guardado del editor sin respuesta** termina a los 20 s con el error de tiempo en el formulario y apaga «Guardando…». Test en la tarea 1 y los de tiempo máximo que ya existen en cada área.
4. **Deshacer el borrado de un ingrediente desde la lista** sigue funcionando. El relevo se publica después de la actualización del estado (`afterSuccess`), y el detalle se cierra (`finished`). Tests existentes en la tarea 7.
5. **Espacios y Ajustes del espacio comparten un ViewModel:** un error mostrado en una no vuelve a salir en la otra, y los botones de los ajustes se desactivan con `save.isSaving`. Lo mira el revisor; los tests existentes de la tarea 9.

---

### Task 1: Piezas comunes de guardado y aviso

L376 (pieza común y `launchPerIdWrite`) y la base de L378, L146, L143, L116, L374 y L202.

`Frontend: sin skill de diseño (el plan fija el diseño)`.

**Ficheros:**
- Crear: `J/core/common/SaveState.kt`, `J/core/common/SaveRunner.kt`, `J/core/common/Cancellation.kt`.
- Modificar: `J/core/common/PerIdWrite.kt`.
- Crear: `J/core/designsystem/component/SaveFeedback.kt`.
- Tests: crear `T/core/common/SaveRunnerTest.kt`, `T/core/common/PerIdWriteTest.kt` y `T/core/designsystem/component/ScreenFeedbackTest.kt`.

**Interfaces (las usan las tareas 2 a 10):**

```kotlin
// J/core/common/SaveState.kt
data class SaveState<E, N>(
    val running: Int = 0,
    val editor: EditorSaveStatus = EditorSaveStatus.IDLE,
    val editorError: E? = null,
    val error: E? = null,
    val notice: N? = null,
    val created: CreatedItem? = null,
) { val isSaving: Boolean get() = running > 0 }

// J/core/common/Cancellation.kt
/** Relanza la cancelación solo si quien la recibe está cancelado; si la lanzó otro (un repositorio), vuelve: es un fallo normal. */
suspend fun Throwable.rethrowIfCancelled() {
    if (this is CancellationException) currentCoroutineContext().ensureActive()
}

// J/core/common/SaveRunner.kt
interface SaveFeedbackControls {
    fun consumeError()
    fun consumeNotice()
    fun consumeCreated()
    fun resetEditor()        // editor a IDLE y editorError a null
    fun releaseEditorError() // si el editor está FAILED: su error pasa a `error` y el editor vuelve a IDLE
}

class SaveRunner<S, E, N>(
    scope: CoroutineScope,
    state: MutableStateFlow<S>,
    read: (S) -> SaveState<E, N>,
    write: S.(SaveState<E, N>) -> S,
    toError: (Throwable) -> E,
) : SaveFeedbackControls {
    fun <T> run(
        notice: N? = null,
        editor: Boolean = false,
        force: Boolean = false,
        noticeOf: (T) -> N? = { notice },
        onSuccess: S.(T) -> S = { this },
        onFailure: S.(Throwable) -> S = { this },
        errorOf: (Throwable) -> E = toError,
        afterSuccess: (T) -> Unit = {},
        action: suspend () -> T,
    ): Boolean
    fun create(
        editor: Boolean = false,
        force: Boolean = false,
        onSuccess: S.(CreatedItem) -> S = { this },
        afterSuccess: (CreatedItem) -> Unit = {},
        action: suspend () -> CreatedItem,
    ): Boolean
    fun showEditorError(error: E)
    fun showError(error: E)
    fun showNotice(notice: N)
    fun clearFeedback()
}

// J/core/designsystem/component/SaveFeedback.kt
sealed interface ScreenFeedback<out E, out N> {
    data class Error<E>(val error: E) : ScreenFeedback<E, Nothing>
    data class Notice<N>(val notice: N) : ScreenFeedback<Nothing, N>
}
fun <E, N> SaveState<E, N>.screenFeedback(covered: Boolean): ScreenFeedback<E, N>?
@Composable fun <E, N> SaveFeedbackEffect(
    save: SaveState<E, N>,
    controls: SaveFeedbackControls,
    covered: Boolean,
    errorText: @Composable (E) -> String,
    noticeText: @Composable (N) -> String,
)
@Composable fun EditorSaveEffect(editor: EditorSaveStatus, editorOpen: Boolean, controls: SaveFeedbackControls, onClose: () -> Unit)
```

**Semántica que fijan los tests:**
- **`run` y `create`:**
  - devuelven false sin hacer nada si `running > 0` y no es `force`;
  - al empezar: `running + 1`, `error = null` y `notice = null`; si es `editor`, también `editor = SAVING` y `editorError = null`;
  - la acción va dentro de `withEditorSaveTimeout(editor)`.
- **Al terminar bien**, en una sola actualización:
  - `running - 1` y `notice = noticeOf(resultado)`;
  - si es `editor`, `editor = SAVED`;
  - `create` pone `created = resultado`; `run` nunca toca `created`;
  - después, `onSuccess`.
  - `afterSuccess` va después de la actualización.
- **Al fallar**, en una sola actualización:
  - `running - 1` y `notice = null`;
  - si es `editor`: `editor = FAILED` y `editorError = errorOf(e)`; si no: `error = errorOf(e)`;
  - después, `onFailure`.
  - Antes de nada, `e.rethrowIfCancelled()`. Si se relanza (se cancela el propio scope), igualmente se descuenta `running`, sin error.
- **`showEditorError`:** `editor = FAILED` y `editorError`. No toca `running`, `error` ni `notice`.
- **`showError`:** pone `error` y `notice = null`. **`showNotice`:** pone `notice`. Ninguno de los dos toca `running`.
- **`consumeError` y `consumeNotice`:** cada uno borra solo su campo.
- **`screenFeedback`:**
  - con `covered` devuelve null;
  - si no, devuelve `Error` si hay `error`, si no `Notice` si hay `notice`, y si no null;
  - nunca devuelve `editorError`.
- **`SaveFeedbackEffect`:** muestra lo que diga `screenFeedback` con el `FeedbackSnackbarEffect(feedback, onShown)` que ya existe (el error en largo y el aviso en corto). Su `onShown` llama a `consumeError` o a `consumeNotice`, según lo que haya mostrado.
- **`EditorSaveEffect`:** con `SAVED`, llama a `onClose()` y a `controls.resetEditor()`. Con `FAILED` y el editor cerrado, llama a `controls.releaseEditorError()`.

- [ ] **Paso 1: tests de `SaveRunnerTest`.**
  - Usa un `data class TestState(val save: SaveState<String, String> = SaveState(), val extra: String = "")`, un `TestScope` y acciones que esperan un `CompletableDeferred` (puerta) o lanzan.
  - `toError = { it::class.simpleName ?: "?" }`, para que el error se lea en las aserciones.
  - Tests (con sus aserciones sobre `state.value.save`):
    - `run keeps saving until the action ends and then shows its notice`: `isSaving` true con la puerta cerrada; al abrirla, `running == 0` y `notice == "ok"`.
    - `a second action does not start while another is in flight`: `run` devuelve false y la acción no se ejecuta.
    - `a forced action starts while another is in flight and saving stays on until both end`: la forzada termina antes y `isSaving` sigue true; al terminar la otra, false.
    - `a forced action ending does not clear a pending created`: termina un `create` (`created == CreatedItem("1", "Pan")`) y después una `run` forzada; `created` no cambia.
    - `an editor failure goes to the editor error and not to the screen error`: `editor == FAILED`, `editorError != null` y `error == null`.
    - `a screen failure goes to the screen error even with an editor failure pending`.
    - `showEditorError does not touch saving nor the screen feedback`: con otra acción en curso y un `notice` pendiente, `isSaving` sigue true y `notice` sigue igual.
    - `consumeError keeps a pending notice and consumeNotice keeps a pending error`.
    - `resetEditor discards only the editor error`.
    - `releaseEditorError moves a failed editor error to the screen error`.
    - `an editor action that never answers fails with the timeout after EDITOR_SAVE_TIMEOUT_MILLIS`: `editorError == "SaveTimeoutException"` y `running == 0`.
    - `a CancellationException thrown by the action is a normal failure`: `running == 0` y `editorError == "CancellationException"` en una acción de editor.
    - `cancelling the scope ends the action without an error`: `running == 0` y `error == null`.
    - `onSuccess and onFailure change the screen state with the result`: `extra` cambia junto con el resultado.
    - `afterSuccess sees the state already updated`: dentro de `afterSuccess`, `running == 0` y `notice` puesto.
- [ ] **Paso 2: tests de `PerIdWriteTest`.**
  - `a CancellationException thrown by the block calls onFailure and frees the id`: `onFailure` recibe la excepción, `onFinished(false)` y el id ya no está en el conjunto.
  - `cancelling the scope frees the id without onFailure`.
- [ ] **Paso 3: tests de `ScreenFeedbackTest`** (funciones puras):
  - `nothing shows while something covers the screen feedback`;
  - `a notice that waited while covered shows once uncovered` (Review Focus 1);
  - `the error goes before the notice`;
  - `the notice shows once the error is consumed`;
  - `the editor error never shows in the screen feedback`.
- [ ] **Paso 4: RED.** `./gradlew :app:testDebugUnitTest --tests "com.dlunaunizar.bobitos.core.*"` no compila: las clases no existen.
- [ ] **Paso 5: implementa** las piezas de «Interfaces» en sus ficheros, y en `PerIdWrite.kt` usa `rethrowIfCancelled` (el id se libera antes de relanzar). `FeedbackSnackbar.kt`, `EditorSaveEffect.kt` y `EditorSave.kt` no se tocan en esta tarea: lo viejo se borra en la tarea 10.
- [ ] **Paso 6: GREEN** de los tres ficheros de test y después la puerta entera.

### Task 2: Tareas y Notas (la migración de referencia)

L143 y L116 (RED en Tareas), L148 (Tareas y Notas) y L146/L378 para estas dos áreas.

`Frontend: sin skill de diseño (el plan fija el diseño)`.

**Ficheros:**
- Modificar: `J/feature/tasks/TasksViewModel.kt`, `J/feature/tasks/TasksUiState.kt`, `J/feature/tasks/TasksScreen.kt`.
- Modificar: `J/feature/notes/NotesViewModel.kt`, `J/feature/notes/NotesUiState.kt`, `J/feature/notes/NotesScreen.kt`.
- Tests: `T/feature/tasks/TasksViewModelTest.kt`, `T/feature/notes/NotesViewModelTest.kt`.

**Interfaces:** consume las de la tarea 1. Produce el patrón que siguen las tareas 3 a 10: `TasksUiState.save: SaveState<TaskUiMessage, TaskUiMessage>` y `TasksViewModel.feedback: SaveFeedbackControls`.

- [ ] **Paso 1: tests nuevos en `TasksViewModelTest`.** Usa el fake con `createGate`/`nextFailure` que ya existe; si el fake no puede bloquear `restoreTask`, añádele una puerta.
  - `a validation error while another write is in flight keeps saving and that write's error reaches the screen` (L143):
    - un `restoreTask` forzado espera su puerta y después `showInvalidDate()`: `save.isSaving == true`, `save.editorError == InvalidDate` y `save.error == null`;
    - `feedback.resetEditor()` (se cierra la hoja) y el `restoreTask` falla con `TaskNotFound`: `save.error == TaskNotFound`.
  - `a forced undo that ends first keeps saving on and keeps the pending created` (L116):
    - un `createTask` desde el editor espera su puerta y un `undoCompletion` termina antes: `save.isSaving == true`;
    - se abre la puerta: `save.created == CreatedItem(<id>, "Fregar")`;
    - otro `restoreTask` termina después: `save.created` no cambia.
  - `marking a task done does not turn saving on`: un `setCompleted` bloqueado deja `save.isSaving == false`.
  - Amplía `undoWhileAnotherActionIsSavingStillDeletes` para que compruebe `save.isSaving == true` hasta que acaban las dos (L148).
- [ ] **Paso 2: test nuevo en `NotesViewModelTest`** (L148): `undo runs while another write is in flight and saving stays on until both end`, con `restoreNote` forzado y otra escritura bloqueada.
- [ ] **Paso 3: RED.** `./gradlew :app:testDebugUnitTest --tests "com.dlunaunizar.bobitos.feature.tasks.TasksViewModelTest" --tests "com.dlunaunizar.bobitos.feature.notes.NotesViewModelTest"` no compila: `save` no existe.
- [ ] **Paso 4: migra Tareas y Notas** con las «Reglas de la migración».
  - `showInvalidDate` y la validación usan `saver.showEditorError`.
  - `setCompleted` sigue con `launchPerIdWrite`: su fallo va por `saver.showError`, «Reabierta» por `saver.showNotice`, y el borrado inicial del aviso por `saver.clearFeedback()`.
  - `createTask` y `createNote` usan `saver.create`.
  - En la pantalla, `covered` es `editorVisible`.
- [ ] **Paso 5: adapta** el resto de tests de los dos ficheros a `state.save.*`.
- [ ] **Paso 6: GREEN** de los dos ficheros y después la puerta entera.

### Task 3: Compra

L148 (Compra) y L146/L378/L376 para esta área.

`Frontend: sin skill de diseño (el plan fija el diseño)`.

**Ficheros:**
- Modificar: `J/feature/shopping/ShoppingViewModel.kt`, `J/feature/shopping/ShoppingUiState.kt`, `J/feature/shopping/ShoppingScreen.kt`.
- Tests: `T/feature/shopping/ShoppingViewModelTest.kt`, `T/feature/shopping/ShoppingViewModelPantryTest.kt`.

**Interfaces:** consume las de la tarea 1 y el patrón de la tarea 2.

**Casos de esta área:**
- `writeStatus` y `ShoppingWriteStatus` desaparecen: ninguna pantalla los lee. Lo que comprueben los tests pasa a `save.isSaving`, `save.error` y `save.notice`.
- Un ingrediente duplicado (`isIngredientDuplicate()`) deja hoy el aviso `IngredientExists` en lugar del error. Se resuelve dentro de la acción: devuelve un resultado que, con `noticeOf`, da `IngredientExists`. No hay un camino de fallo especial.
- `clearPurchased` pasa por `saver.run`; su recuento llega a `lastClearedCount` con `onSuccess`.
- `setPurchased` sigue con `launchPerIdWrite`, como en Tareas.
- `loadIngredientsOnDemand` usa `rethrowIfCancelled`.
- La lambda que hoy acaba en `startPantryLoad(...)` (un `Deferred`) acaba en `Unit`.

- [ ] **Paso 1: tests nuevos.**
  - `restoring an item while another write is in flight keeps saving until both end` (L148);
  - `marking an item bought does not turn saving on`;
  - `a duplicate ingredient ends with the IngredientExists notice and no error`. Si ya existe un test equivalente, adáptalo en lugar de duplicarlo.
- [ ] **Paso 2: RED.** Los dos ficheros no compilan: `save` no existe.
- [ ] **Paso 3: migra Compra** con las «Reglas de la migración» y los casos de arriba.
- [ ] **Paso 4: adapta** el resto de tests a `state.save.*`.
- [ ] **Paso 5: GREEN** de los dos ficheros y después la puerta entera.

### Task 4: Deporte, Rutinas y Ejercicios

L148 (las tres áreas) y L146/L378 para ellas.

`Frontend: sin skill de diseño (el plan fija el diseño)`.

**Ficheros:**
- Modificar: `J/feature/sport/SportViewModel.kt`, `J/feature/sport/SportUiState.kt`, `J/feature/sport/SportScreen.kt`.
- Modificar: `J/feature/routines/RoutinesViewModel.kt`, `J/feature/routines/RoutinesUiState.kt`, `J/feature/routines/RoutinesScreen.kt`.
- Modificar: `J/feature/exercises/ExercisesViewModel.kt`, `J/feature/exercises/ExercisesUiState.kt`, `J/feature/exercises/ExercisesScreen.kt`.
- Tests: `T/feature/sport/SportViewModelTest.kt`, `T/feature/routines/RoutinesViewModelTest.kt`, `T/feature/exercises/ExercisesViewModelTest.kt`.

**Casos de estas áreas:**
- **Deporte:** `deleteActivity` pasa a `saver.run<CalendarEvent?>` y entrega el evento a su `onDeleted` con `afterSuccess`. Desaparece `onSuccess: (Any?) -> Unit`.
- **Rutinas:** `fork` usa `saver.create` sin `force`.
- **Ejercicios:**
  - `AlreadyExists` y `NameRequired` usan `saver.showEditorError`;
  - en la pantalla, `saved = state.editorSave == SAVED` pasa a `state.save.editor == SAVED`;
  - el estado auxiliar `undoable` se queda: es del plan 2.

- [ ] **Paso 1: un test de L148 por área**, con su restauración forzada (`restoreActivity`, `restoreRoutine`, `restoreExercise`) y otra escritura bloqueada: «Guardando…» sigue encendido hasta que acaban las dos.
- [ ] **Paso 2: RED.** Los tres ficheros no compilan: `save` no existe.
- [ ] **Paso 3: migra las tres áreas.**
- [ ] **Paso 4: adapta** el resto de tests.
- [ ] **Paso 5: GREEN** de los tres ficheros y después la puerta entera.

### Task 5: Recetas

L148 (Recetas) y L146/L378/L376 para esta área.

`Frontend: sin skill de diseño (el plan fija el diseño)`.

**Ficheros:**
- Modificar: `J/feature/recipes/RecipesViewModel.kt`, `J/feature/recipes/RecipesUiState.kt`, `J/feature/recipes/RecipesScreen.kt`, `J/feature/recipes/RecipeSheets.kt`.
- Tests: `T/feature/recipes/RecipesViewModelTest.kt`.

**Casos de esta área:**
- `deleteRecipe` deja `deletedRecipe` con `onSuccess`.
- `addToShopping` pasa por `saver.run`: lee del servidor dentro de la acción y deja la revisión con `onSuccess`. Desaparecen su `try/finally` y su `if (it is CancellationException) throw it`.
- El diálogo de importar es un diálogo que no es editor (ver «Reglas de la migración»), y `visibleError(importDialogOpen)` desaparece.

- [ ] **Paso 1: tests.**
  - Amplía `undo runs even while another write is in flight, without a saved notice` para que compruebe `save.isSaving == true` hasta que acaban las dos (L148).
  - Nuevo: `adding to shopping keeps saving on while it reads and blocks another action`.
- [ ] **Paso 2: RED.** No compila: `save` no existe.
- [ ] **Paso 3: migra Recetas.**
- [ ] **Paso 4: adapta** el resto de tests.
- [ ] **Paso 5: GREEN** y después la puerta entera.

### Task 6: Comidas

L161, L168 (RED en Comidas), L148 (Comidas) y L146/L378/L376 para esta área.

`Frontend: sin skill de diseño (el plan fija el diseño)`.

**Ficheros:**
- Modificar: `J/feature/meals/MealsViewModel.kt`, `J/feature/meals/MealsUiState.kt`, `J/feature/meals/MealsScreen.kt`.
- Tests: `T/feature/meals/MealsViewModelTest.kt`, `T/feature/meals/MealsCookedViewModelTest.kt`, `T/feature/meals/MealsUndoViewModelTest.kt`, `T/feature/meals/MealsSnackbarErrorTest.kt`.

**Casos de esta área:**
- **`confirmCooked`:**
  - su traducción del error pasa por `errorOf` y es pura (`toCookedUiMessage(dialogOpen)`);
  - `changedItemName` se escribe con `onFailure`, en la misma actualización: el nombre del `ShoppingRepositoryException`, o null si no lo trae;
  - `dismissCooked` lo pone a null;
  - desaparece el parámetro `toMessage` (L161).
- **L168:** en `ShoppingFailure.toUiMessage()`, `ItemNotFound` pasa a `MealUiMessage.ShoppingChanged`. Se corrige el comentario de al lado.
- `markCooked` y `prepareIngredientReview` pasan por `saver.run`. `markCooked` deja de convertir en `UnexpectedError` una cancelación del propio ViewModel.
- «Has cocinado» es un diálogo que no es editor. `snackbarError()` desaparece, y `cookedErrorItem` lee `save.error`.

- [ ] **Paso 1: tests.**
  - `an item not found in the shopping list reads as the list has changed`: `ShoppingRepositoryException(ShoppingFailure.ItemNotFound).toUiMessage() == MealUiMessage.ShoppingChanged` (L168).
  - `dismissing the cooked dialog forgets the changed item name`: un `confirmCooked` falla con `ItemChanged` y nombre «Leche», y `changedItemName == "Leche"`; tras `dismissCooked()`, es null (L161).
  - `a cooked error without a name clears a previous name`.
  - L148: `restoring a meal while another write is in flight keeps saving until both end`.
- [ ] **Paso 2: RED.** Los ficheros no compilan (`save` no existe). Antes de migrar, ejecuta solo el test de L168 contra el código de hoy (por ejemplo, en un fichero aparte o con los demás tests nuevos aún sin escribir) y anota en el informe que da `UnexpectedError`.
- [ ] **Paso 3: migra Comidas.**
- [ ] **Paso 4: adapta** el resto de tests de los cuatro ficheros.
- [ ] **Paso 5: GREEN** y después la puerta entera.

### Task 7: Ingredientes y Detalle de ingrediente

L374 (RED en Ingredientes), L148 (las dos áreas) y L146/L378/L376 para ellas.

`Frontend: sin skill de diseño (el plan fija el diseño)`.

**Ficheros:**
- Modificar: `J/feature/ingredients/IngredientsViewModel.kt`, `J/feature/ingredients/IngredientsUiState.kt`, `J/feature/ingredients/IngredientsScreen.kt`.
- Modificar: `J/feature/ingredients/IngredientDetailViewModel.kt`, `J/feature/ingredients/IngredientDetailUiState.kt`, `J/feature/ingredients/IngredientDetailScreen.kt`.
- Tests: `T/feature/ingredients/IngredientsViewModelTest.kt`, `T/feature/ingredients/IngredientDetailViewModelTest.kt`.

**Casos de estas áreas:**
- **Alta duplicada:** usa `saver.showEditorError(AlreadyExists)` y, en otra actualización, `openIngredientId` y `existingName`. El aviso se ve una sola vez, en el formulario.
- `runAction` ya no pone `existingName = null` al empezar: lo hace la función que lanza el alta.
- `finishOnSuccess` pasa a `onSuccess = { copy(finished = true) }`.
- La publicación en `DeletedIngredientRelay` va en `afterSuccess`.
- `writeOptimistically` usa `rethrowIfCancelled` y `saver.showError`, sin encender «Guardando…».
- `cancelScan` borra el error del editor con `saver.resetEditor()`.
- `IngredientsMessageEffect` y `editorErrorMessage` pasan a usar `SaveFeedbackEffect` y `state.save.editorError`.

- [ ] **Paso 1: tests.**
  - `a duplicate name shows the error only in the form`: `save.editorError == AlreadyExists`, `save.error == null` y `openIngredientId == <id>` (L374).
  - L148 en las dos: `restoreIngredient` y `restoreBrand` con otra escritura bloqueada.
- [ ] **Paso 2: RED.** No compilan.
- [ ] **Paso 3: migra las dos áreas.**
- [ ] **Paso 4: adapta** el resto de tests.
- [ ] **Paso 5: GREEN** y después la puerta entera.

### Task 8: Calendario del espacio y Mi calendario

L148 (las dos áreas) y L146/L378 para ellas.

`Frontend: sin skill de diseño (el plan fija el diseño)`.

**Ficheros:**
- Modificar: `J/feature/calendar/CalendarViewModel.kt` (con su `UiState`) y `J/feature/calendar/CalendarScreen.kt`.
- Modificar: `J/feature/calendar/PersonalCalendarViewModel.kt` (con su `UiState`) y `J/feature/calendar/PersonalCalendarScreen.kt`.
- Tests: `T/feature/calendar/CalendarViewModelTest.kt`, `T/feature/calendar/PersonalCalendarViewModelTest.kt`.

**Casos de estas áreas:**
- El Calendario del espacio usa `SaveState<FailureKind, CalendarNotice>`, y su `notice` por defecto `SAVED` pasa al `notice` de cada llamada que hoy lo usa. Mi calendario usa `SaveState<FailureKind, Nothing>`.
- `toError` es `Throwable::toFailureKind`.
- `action`/`block` y `saving` desaparecen.
- `restore` del Calendario del espacio deja de devolver el `String` de `createEvent`: la acción acaba en `Unit`.
- Lo que hoy hace `return@action null` sin espacio sigue sin escribir nada; con `run<Unit>`, acaba en `Unit`.

- [ ] **Paso 1: tests.** Amplía `undo runs even while another write is in flight` (Calendario del espacio) para que compruebe `save.isSaving` hasta el final. Añade el mismo caso en Mi calendario (L148).
- [ ] **Paso 2: RED.** No compilan.
- [ ] **Paso 3: migra las dos áreas.**
- [ ] **Paso 4: adapta** el resto de tests.
- [ ] **Paso 5: GREEN** y después la puerta entera.

### Task 9: Espacios y Ajustes del espacio

L372 y L374 (RED en Espacios) y L378 para esta área.

`Frontend: sin skill de diseño (el plan fija el diseño)`.

**Ficheros:**
- Modificar: `J/feature/spaces/SpacesViewModel.kt`, `J/feature/spaces/SpacesUiState.kt`, `J/feature/spaces/SpacesScreen.kt`, `J/feature/spaces/SpaceSettingsScreen.kt`.
- Modificar el cableado de `onClearSpaceFeedback`/`onClearFeedback` y `onEditorSaveConsumed`: `J/MainActivity.kt`, `J/app/BobitosApp.kt` y `J/core/navigation/BobitosNavHost.kt`.
- Tests: `T/feature/spaces/SpacesViewModelTest.kt`.
- Crear: `A/feature/spaces/SpacesSavingUiTest.kt`.

**Casos de esta área:**
- `isLoading` y `writeStatus` (con `WriteStatus`) desaparecen en favor de `save`.
- **Diálogos de Crear, Unirse y Renombrar:** reciben `saving = state.save.isSaving` y `serverError = state.save.editorError`.
- **Botones de los ajustes:** `enabled = !state.save.isSaving && canWrite`.
- `showValidationError` pasa a `saver.showEditorError`, y `acceptInvitation` deja `acceptedSpaceId` con `onSuccess`.
- `SpacesScreen` y `SpaceSettingsScreen` cambian `onClearFeedback` y `onEditorSaveConsumed` por `feedback: SaveFeedbackControls`.
- `covered` es el diálogo de editor abierto en cada pantalla.

- [ ] **Paso 1: test instrumentado `SpacesSavingUiTest.createIsDisabledWhileAnotherActionIsSaving`** (L372).
  - Compón `SpacesScreen` con `UiState.Content(emptyList())`, un `SpaceManagementUiState` con `save = SaveState(running = 1)` y un `SaveFeedbackControls` falso.
  - Abre «Crear espacio» como lo hace `SpaceDialogsUiTest` y escribe un nombre.
  - El botón de confirmar `assertIsNotEnabled()`.
- [ ] **Paso 2: tests JVM.**
  - `a failed create shows the error only in the dialog`: `save.editorError != null` y `save.error == null` (L374).
  - `a validation error while another action is in flight keeps saving`.
- [ ] **Paso 3: RED.**
  - Los JVM no compilan.
  - El instrumentado tampoco compila (`save` no existe). Antes de migrar, comprueba que su aserción falla con el código de hoy: compón el diálogo con `editorSave = IDLE` e `isLoading = true` y anota en el informe que el botón sale activo. Después pásalo a la forma nueva.
- [ ] **Paso 4: migra Espacios y Ajustes del espacio** y su cableado.
- [ ] **Paso 5: adapta** el resto de tests.
- [ ] **Paso 6: GREEN.** Los JVM, el instrumentado con su comando suelto y después la puerta entera.

### Task 10: Despensa y limpieza

L202 (RED en la Despensa) y el cierre de L378, L146 y L376 en todo el alcance.

`Frontend: sin skill de diseño (el plan fija el diseño)`.

**Ficheros:**
- Modificar: `J/feature/pantry/PantryViewModel.kt`, `J/feature/pantry/PantryUiState.kt`, `J/feature/pantry/PantryScreen.kt`.
- Modificar: `J/feature/spaces/SpaceHomeViewModel.kt` y `J/feature/common/PantryShopping.kt` (solo el patrón de cancelación).
- Modificar o borrar lo que quede sin uso:
  - de `J/core/designsystem/component/FeedbackSnackbar.kt`: `feedbackFor`, `savingOutsideEditor`, `discardFailure` y la sobrecarga con `editorSave`;
  - el `EditorSaveEffect` viejo de `J/core/designsystem/component/EditorSaveEffect.kt`;
  - `started`/`succeeded`/`failed` de `J/core/common/EditorSave.kt`.
- Tests: `T/feature/pantry/PantryViewModelTest.kt`. `T/core/designsystem/component/FeedbackForTest.kt` y las partes de `T/core/common/EditorSaveTest.kt` que prueben lo borrado se borran o pasan a `ScreenFeedbackTest`.

**Casos de esta área:**
- La Despensa usa `SaveState<PantryUiMessage, PantryUiMessage>`, sin editor ni tiempo máximo (como hoy). El aviso que hoy devuelve la acción pasa a `noticeOf`.
- `restock` usa `rethrowIfCancelled` y `saver.showError`, sin encender «Guardando…».
- El selector es un diálogo que no es editor.
- Los tres `when` (`Throwable.toUiMessage`, `PantryFailure.toUiMessage` y `ShoppingFailure.toUiMessage`) se quedan en una sola traducción, sin ramas repetidas. El resultado de cada fallo no cambia.

- [ ] **Paso 1: tests.**
  - `a restock failure keeps a pending notice after the error is shown` (L202): una acción deja `ItemsAdded`, falla un `restock`, y tras `feedback.consumeError()` sigue `save.notice == ItemsAdded`.
  - `every pantry and shopping failure keeps its message`: tabla de cada `PantryFailure` y `ShoppingFailure` con el mensaje de hoy.
- [ ] **Paso 2: RED.** No compila.
- [ ] **Paso 3: migra la Despensa** y cambia el patrón de cancelación de `SpaceHomeViewModel` y `PantryShopping.currentIdsOrNull` por `rethrowIfCancelled`.
- [ ] **Paso 4: borra lo viejo** que ya nadie use y adapta sus tests.
- [ ] **Paso 5: comprobaciones del alcance.** Cada comando debe devolver vacío, y su salida va al informe:
  - `grep -rn "as? CreatedItem\|suspend () -> Any?" app/src/main/java` (L146);
  - `grep -rn "is CancellationException) throw" app/src/main/java/com/dlunaunizar/bobitos/feature app/src/main/java/com/dlunaunizar/bobitos/core/common` (L376);
  - `grep -rn "private fun runAction\|private fun action(\|editorSave.started\|editorSave.failed\|discardFailure" app/src/main/java` (L378).
- [ ] **Paso 6: GREEN** de `PantryViewModelTest` y después la puerta entera.
