# Quitar el alta rápida de Tareas — Plan de implementación

> **Para agentes:** se ejecuta con el método `delegating-plan-execution` (plan pequeño: pasos 1, 2, 3, 5 y 6). Los pasos usan casillas (`- [ ]`).

**Objetivo:** que en la pestaña Tareas la única forma de crear una tarea sea el botón «Añadir tarea» (FAB y botón del estado vacío), que abre el formulario completo.

**Arquitectura:** cambio solo de UI. Se borra la fila de alta rápida (campo de texto + botón «+») de la cabecera de `TasksScreen`, su estado `quickTitle` y sus dos textos. No cambian el ViewModel, el repositorio ni las reglas de Firestore.

**Stack:** Kotlin, Jetpack Compose (Material 3), ktlint, detekt.

**Spec:** la petición del usuario (2026-10-10): «la única forma para el usuario de añadir una tarea sea mediante el botón de añadir tarea, que no se pueda usar solo el cuadro de texto y el botón de más».

## Contexto

El alta rápida entró con B6 (commit `6179276`, #191): crea la tarea solo con el título (prioridad media, sin responsable, tipo, fecha ni periodicidad). Desde que existe el FAB «Añadir tarea» (B9), esa fila sobra y permite crear tareas incompletas. El usuario quiere quitarla.

## Restricciones globales

- No tocar `TasksViewModel`, `TaskRepository` ni `firestore.rules`: `createTask` lo siguen usando el editor y el «Deshacer» de borrar.
- Se mantienen el FAB «Añadir tarea» (`R.string.tasks_add`), el botón «Añadir tarea» del estado vacío y el botón «Plantillas».
- Sin imports sin usar: ktlint (`no-unused-imports`) falla si quedan.
- Para Gradle hay que anteponer `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

## Review Focus

1. Lista vacía: el botón «Añadir tarea» del estado vacío y el FAB siguen abriendo el editor en blanco.
2. Usuario sin permiso de escritura (`canWrite = false`): no ve ninguna vía de creación (antes el campo aparecía deshabilitado; ahora desaparece).
3. Crear una tarea desde el editor sigue funcionando y lo cierra, aunque se haya quitado el `quickTitle = ""` del `onSave`.
4. La cabecera no queda descuadrada: `TaskFilterBar` ya trae `padding(top = Spacing.sm)`.
5. No queda ninguna referencia a `tasks_quick_add` ni a `tasks_quick_add_hint` (la compilación fallaría si quedara alguna).

---

### Task 1: Quitar la fila de alta rápida de `TasksScreen`

**Ficheros:**
- Modificar: `app/src/main/java/com/dlunaunizar/bobitos/feature/tasks/TasksScreen.kt`
- Modificar: `app/src/main/res/values/strings.xml:237-238`

**Interfaces:** ninguna. `TasksScreen(spaceId, canWrite, modifier, viewModel)` mantiene su firma, así que las entradas de `config/detekt/baseline.xml` siguen valiendo.

**Sobre RED/GREEN:** es un borrado de UI sin lógica. La única forma de probarlo sería un test instrumentado de Compose sobre `TasksScreen`, que necesita emulador y montar Hilt (el proyecto solo tiene `ProfileScreenTest` instrumentado). Por eso aquí la comprobación es: compilar, pasar ktlint, detekt y los tests unitarios, buscar con grep que no quede rastro y probarlo a mano en la app.

- [ ] **Paso 1: Borrar el estado `quickTitle`** (línea ~173):

```kotlin
    var quickTitle by rememberSaveable { mutableStateOf("") }
```

- [ ] **Paso 2: Borrar la llamada a `TaskQuickAdd`** de la columna principal (líneas ~202-212). `TaskFeedback(...)` debe quedar seguido directamente de `TaskFilterBar(...)`:

```kotlin
            TaskFeedback(state, viewModel::clearFeedback)
            TaskFilterBar(state.filters, members, viewModel::setFilters)
```

- [ ] **Paso 3: Quitar `quickTitle = ""` del `onSave` del editor** (línea ~352). La rama de creación queda así:

```kotlin
                } ?: run {
                    viewModel.createTask(
                        spaceId, title, description, assignee, due, priority, type, recurrence, start,
                    )
                }
```

- [ ] **Paso 4: Borrar el composable `TaskQuickAdd`** y sus dos comentarios de cabecera («Alta rápida…», líneas ~374-397).

- [ ] **Paso 5: Borrar los imports que quedan sin usar:**
  - `androidx.compose.foundation.text.KeyboardActions`
  - `androidx.compose.ui.text.input.ImeAction`

  Se **mantienen**, porque se usan en otras partes del fichero: `KeyboardOptions` (campo numérico, ~l.1050), `OutlinedTextField`, `IconButton`, `Icons.Rounded.Add` (FAB) y `rememberSaveable`. Antes de borrar cada import, compruébalo con grep.

- [ ] **Paso 6: Borrar los textos** de `app/src/main/res/values/strings.xml` (es el único `values*`):

```xml
    <string name="tasks_quick_add">Añadir tarea</string>
    <string name="tasks_quick_add_hint">Apunta una tarea…</string>
```

- [ ] **Paso 7: Comprobar que no queda rastro**

Run: `grep -rn "quickTitle\|TaskQuickAdd\|tasks_quick_add" app/src`
Esperado: ninguna línea.

- [ ] **Paso 8: Compilar, lint y tests**

Run: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:assembleDebug :app:ktlintCheck :app:detekt :app:testDebugUnitTest`
Esperado: BUILD SUCCESSFUL.

- [ ] **Paso 9: Commit**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/tasks/TasksScreen.kt app/src/main/res/values/strings.xml
git commit -m "feat(tareas): crear tareas solo desde «Añadir tarea» (fuera el alta rápida)"
```

---

## Ejecución (según `delegating-plan-execution`, plan pequeño)

1. Al aprobar el plan, copiarlo a `docs/superpowers/plans/2026-10-10-quitar-alta-rapida-tareas.md`, crear la rama `agent/tareas-sin-alta-rapida` desde `main` y hacer commit del plan.
2. Tarea 1 → un subagente `model: sonnet` con su brief.
3. Revisión final de la rama → un único revisor `model: opus`, con la plantilla `code-reviewer.md`, el paquete de revisión, este Review Focus y los rulings del ledger. Si `opus` da error, se repite el encargo con `model: fable` y se anota en el ledger. Los Críticos e Importantes los arregla un subagente `sonnet`. Los menores van al ledger. Sin re-revisión: el cambio no toca migraciones ni seguridad.
4. `code-review` nivel `high` sobre la rama. Los arreglos, a un subagente `sonnet`.
5. Cierre con `finishing-a-development-branch`: los menores van a `docs/superpowers/backlog.md`. El PR o el merge los decide el usuario.
6. Memoria: la nota `review-model-fable` (2026-10-07) es anterior a la skill (modificada el 2026-10-09). Se reescribe para que diga «revisión final con Opus; Fable solo si Opus falla, según `delegating-plan-execution`», y se actualiza su línea en `MEMORY.md`.

## Verificación de punta a punta

- Gradle en verde (paso 8) y grep limpio (paso 7).
- A mano en emulador o dispositivo (`./gradlew :app:installDebug`), en la pestaña Tareas:
  - La cabecera muestra el título, el contador y «Plantillas», y debajo directamente los filtros. No hay campo de texto ni «+».
  - El FAB «Añadir tarea» abre el formulario completo, y al guardar la tarea aparece en la lista.
  - Con la lista vacía, el botón del estado vacío abre el mismo formulario.
  - «Plantillas» sigue abriendo el editor prerrellenado.
