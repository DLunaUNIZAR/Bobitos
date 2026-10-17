# Evolución visual · Fase B (jerarquía, ruido y accesibilidad TalkBack) — plan de implementación

> **Para agentes:** se ejecuta con la skill `delegating-plan-execution` del usuario. El marco es superpowers:executing-plans, con un implementador por tarea, en serie y en el orden del plan. Los pasos usan casillas (`- [ ]`). Los commits los hace el coordinador.

**Objetivo:** cada pantalla dice cada cosa una sola vez (un título, un CTA, metadatos que aportan); borrar es siempre con Deshacer salvo lo irreversible, y TalkBack lee nombre, rol y estado reales en cada control.

**Arquitectura:**
- **Componentes comunes nuevos** en `core/designsystem/component`:
  - `SectionHeader`, la cabecera de sección, plegable o no;
  - `WeekDayStrip`, la tira de días de Comidas y Deporte;
  - la hoja de detalle `BobitosInfoSheet` pasa a tener una acción principal y un menú de secundarias.
- **Lógica pura con test JVM** para lo que decide qué se pinta:
  - la agrupación de la semana;
  - el resumen de la comida;
  - el reparto de las acciones de la hoja;
  - si se ve el autor;
  - si un borrado se puede deshacer.
- **Deshacer:** cada área gana un `restore…` en su repositorio, que escribe con **el mismo id** y el estado que tenía el elemento. El ViewModel solo ofrece Deshacer cuando el borrado ha terminado bien (patrón `CalendarViewModel.delete(eventId, onDeleted)`).

**Tecnología:** Kotlin 2.3.10, Jetpack Compose (BOM 2026.06.00: material3 1.4.0, foundation 1.11.3), Hilt y Firestore. Tests con JUnit 4 (JVM), Compose instrumentados (`createAndroidComposeRule<ComponentActivity>()`) y tests de reglas en el emulador (`tests/firebase-emulators.test.mjs`).

**Spec:**
- `docs/superpowers/specs/2026-10-17-evolucion-visual-fase-b-design.md` (apartados B1–B12).
- Programa: `docs/superpowers/specs/2026-10-17-evolucion-visual-design.md`.
- Maquetas: `docs/superpowers/specs/2026-10-17-evolucion-visual-maquetas-fase-b.html`.
- Contexto de producto: `PRODUCT.md`.

## Restricciones globales

**Rutas**
- `J/` = `app/src/main/java/com/dlunaunizar/bobitos/`.
- `T/` = `app/src/test/java/com/dlunaunizar/bobitos/` (JVM).
- `A/` = `app/src/androidTest/java/com/dlunaunizar/bobitos/` (instrumentados).
- `S` = `app/src/main/res/values/strings.xml`.

**Entorno y comandos**
- **Java:** antes de cada `./gradlew` o `npm run test:emulators`, ejecutar `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- **Test JVM suelto:** `./gradlew :app:testDebugUnitTest --tests "<FQCN>"`.
- **Test instrumentado suelto:** `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`.
  - Necesita un emulador encendido: `export PATH="$HOME/Library/Android/sdk/emulator:$HOME/Library/Android/sdk/platform-tools:$PATH"; nohup emulator -avd Pixel_10 -no-boot-anim >/dev/null 2>&1 & adb wait-for-device`.
  - El emulador está en inglés (`en-US`).
- **Tests de reglas:** `npm run test:emulators`.
- **Puerta de cada tarea:** `./gradlew ktlintCheck detekt testDebugUnitTest assembleDebug lintDebug`, más los tests instrumentados de la propia tarea. En las tareas 10 a 13, también `npm run test:emulators`.
  - `lintDebug` ya falla en `main` por `RestrictedApi currentBackStack` en `BobitosNavHost`. Ese error, y solo ese, se tolera.
  - Si un cambio de firma rompe `config/detekt/baseline.xml`, se regenera con `./gradlew detektBaseline` y se incluye en la tarea.

**Textos y estilo**
- **Textos:**
  - Los nuevos van en `S`, en español, con tuteo y punto final en las frases completas.
  - Los conteos, con `<plurals>`.
  - Las cadenas que se quedan sin uso se borran.
- **Componentes:**
  - Material 3 reales, sin controles hechos a mano que imiten uno de Material.
  - Ni colores ni tipografías nuevas: solo los roles de `MaterialTheme`.
- **Tema:** el tema oscuro sigue siendo el de por defecto. Todo cambio visual se comprueba en claro y en oscuro.
- **Tamaño:** destinos táctiles de 48 dp. Con letra al 130 %, nada se corta.

**Deshacer (todas las áreas)**
- **Mismo id.** Deshacer escribe el documento con su id original.
- **Mismo contenido y estado:** comida cocinada, actividad hecha, tarea completada, producto comprado o nota fijada.
- **Lo que el borrado arrastró vuelve:** las marcas y preferencias de un ingrediente, o el evento enlazado de una actividad.
- **La autoría y las fechas de creación no vuelven.** Pasan a ser de quien deshace y del momento de deshacer, porque las reglas de alta exigen `createdBy == auth.uid` y `createdAt == request.time`. Es lo que ya hace hoy el Deshacer del Calendario.
  - Ruling R1 del plan: es lo que significa «tal como era» sin cambiar las reglas.
  - Excepción: las recetas comunes conservan su autoría con `validRecipeRestore`, como hoy.
- **Estado en dos pasos.** Si la regla de alta obliga a crear sin ese estado (`done == false`, `status == "TODO"`, `purchased == false`), se crea y después se actualiza.
- **Si no se puede devolver así sin cambiar `firestore.rules`, no se ofrece Deshacer.** Se conserva el diálogo de confirmación (spec B10). Las reglas no se tocan.
- **Si Deshacer falla** (el espacio ya no está, el id ya está ocupado, sin permiso…), el error sale por el snackbar con el texto humano de `FailureKind`. Nunca se queda en silencio.

**Alcance**
- **Sin dependencias nuevas** y sin cambios en `firestore.rules`.
- **No se toca la fase C:**
  - color por módulo;
  - listas en vez de losas;
  - Hoy como agenda y «Calendario 0»;
  - movimiento.

**Frontend**
- **Las skills de diseño** las invoca el implementador, con el subcomando de la línea `Frontend:` de su tarea. No hay nadie a quien preguntar: si la skill pide elegir o confirmar algo, decide con la «Dirección visual» de la spec y `PRODUCT.md`, y anota en el informe lo que decidiste.
- **Límite de ficheros:** la skill solo cambia los ficheros de la tarea.
- **Este plan no hace trabajo nuevo de impeccable:** es un refinamiento sin `DESIGN.md`.

## Rulings de la planificación

- **R1. «Tal como era» sin cambiar las reglas.** La autoría y las fechas de creación no vuelven, como en el Deshacer de hoy del Calendario (ver «Deshacer»). La spec no lo decía porque salió al mirar las reglas.
- **R2. «Más opciones» con nombre.** En vez de un test de UI por cada tarjeta (spec B12), un componente común, `MoreOptionsButton`, con su test (tarea 7), y un grep que exige que todos los ⋮ lo usen (tarea 15).
- **R3. Tareas y Compra también vuelven con su estado y su id.** Es la regla «su estado» de B10 aplicada a completada y comprado. Hoy vuelven pendientes y con un id nuevo.

## Review Focus

1. **Deshacer una tarea completada que se repite no la avanza de fecha** ni crea otra. Devolver «completada» es una escritura directa del estado, no el camino de completar, que con recurrencia avanza las fechas (`FirestoreTaskRepository.kt:140-146`). Lo prueba la tarea 12.
2. **Deshacer escribe en el espacio del elemento, no en el que se está viendo.** Si el usuario cambia de espacio antes de pulsar Deshacer, la nota, la comida o el evento vuelve a su espacio original. Por eso todo `restore…` recibe el `spaceId` capturado al borrar. Lo prueban las tareas 10 y 13.
3. **Un ingrediente personal cuyo slug ha ocupado entretanto un común no se devuelve a medias.** La regla `!exists(/ingredients/…)` rechaza el alta. Las marcas y las preferencias van en el mismo lote, así que no queda nada huérfano, y sale el aviso de error. Lo prueba la tarea 11.
4. **Deshacer una actividad de Deporte devuelve su evento enlazado con el mismo id y los datos que tenía,** también si se había editado desde el Calendario. El borrado devuelve la foto del evento. Lo prueba la tarea 13.
5. **Reintentar en Calendario mientras sigue fallando vuelve a enseñar el error,** sin quedarse cargando para siempre. Lo prueba la tarea 6.

---

### Tarea 1: cabecera de sección común y un título por pantalla (B1, y cabecera plegable de B12)

**Frontend:** `impeccable:impeccable extract J/feature/tasks/TasksScreen.kt J/feature/shopping/ShoppingScreen.kt J/feature/recipes/RecipesScreen.kt J/feature/routines/RoutinesScreen.kt`

**Ficheros:**
- Crear `J/core/designsystem/component/SectionHeader.kt`.
- Modificar:
  - `J/feature/tasks/TasksScreen.kt`: título y `TasksVisibleCount` (:192-209, :1091), `TaskSectionHeader` (:381-410);
  - `J/feature/shopping/ShoppingScreen.kt`: título y recuento (:206-221), `ShoppingSectionTitle` (:483);
  - `J/feature/recipes/RecipesScreen.kt` (`recipesSection`, cabecera en ~:335);
  - `J/feature/routines/RoutinesScreen.kt` (cabeceras en :197, :207 y :229);
  - `J/feature/pantry/PantryScreen.kt` (la cabecera de categoría en ~:185, solo `heading()`);
  - `S` (borrar `tasks_list_title`, `shopping_list_title`, `tasks_visible_count` y `shopping_pending_count` si se quedan sin uso);
  - `.gitignore`.
- Borrar `A/feature/tasks/TasksVisibleCountUiTest.kt`.
- Test: `A/core/designsystem/component/SectionHeaderTest.kt`.

**Interfaces:**
- Produce: `SectionHeader` (la usa también la tarea 8), con esta firma:
  ```kotlin
  @Composable fun SectionHeader(
      title: String,
      modifier: Modifier = Modifier,
      count: Int? = null,
      expanded: Boolean? = null,
      onToggle: (() -> Unit)? = null,
      action: (@Composable () -> Unit)? = null,
  )
  ```
  - Pinta `titleMedium`: «title · count» si hay `count` (cadena `section_header_count` = `%1$s · %2$d`), o solo `title`.
  - El texto es `heading()`.
  - Con `onToggle`, la fila entera es pulsable, con `Role.Button` y `stateDescription` = `state_expanded` («Desplegado») o `state_collapsed` («Plegado»), según `expanded`. Lleva el icono ExpandLess/ExpandMore como decorativo (`contentDescription = null`). La cadena `tasks_completed_toggle` desaparece.
  - `action` va al final de la fila, fuera del área pulsable.
- Consume: nada nuevo.

- [ ] **Paso 1: Añadir al `.gitignore`** las carpetas de trabajo de las skills de diseño, debajo de «Herramientas de diseño locales». `.impeccable/critique/2026-10-17T08-52-25Z__…md` ya está versionado y se queda como está.
  ```
  .taste-skill/
  .impeccable/review/
  .impeccable/critique/
  .impeccable/mocks/
  .impeccable/build/
  .impeccable/config.local.json
  ```

- [ ] **Paso 2: Escribir los tests que fallan** (`SectionHeaderTest`).
  - `aPlainHeaderIsAHeadingWithItsCount`: `SectionHeader("Hoy", count = 2)` tiene un nodo con el texto «Hoy · 2» y `SemanticsProperties.Heading`.
  - `aCollapsibleHeaderAnnouncesItsState`: con `expanded = false` y `onToggle`, el nodo con `Role.Button` tiene `StateDescription` «Plegado» y acción de clic. Tras pulsarlo, `onToggle` se ha llamado una vez. Con `expanded = true`, dice «Desplegado».
  - `theActionIsNotPartOfTheToggle`: con `action = { TextButton(onClick = flag) { Text("Plantillas") } }`, pulsar «Plantillas» no llama a `onToggle`.

- [ ] **Paso 3: Comprobar que fallan.** Resultado esperado: FALLO de compilación, porque `SectionHeader` no existe.

- [ ] **Paso 4: Implementar `SectionHeader` y usarlo.**
  - **Tareas:**
    - fuera el título «Tareas compartidas» y `TasksVisibleCount`;
    - las secciones usan `SectionHeader`; Completadas lo usa plegable;
    - «Plantillas» (solo con `canWrite`) es la `action` de la primera cabecera visible; si la lista está vacía, va al final de la fila de filtros.
  - **Compra:** fuera «Lista compartida» y «N productos pendientes». «Pendientes · N» y «Comprados · N» usan `SectionHeader`, con «Limpiar comprados» como `action`.
  - **Recetario y Rutinas:** sus cabeceras pasan a `SectionHeader`.
  - **Despensa:** su cabecera de categoría solo gana `heading()`.

- [ ] **Paso 5: Comprobar que no quedan títulos ni recuentos sueltos.** `grep -rn "tasks_list_title\|shopping_list_title\|TasksVisibleCount\|shopping_pending_count" app/src` sale vacío.

- [ ] **Paso 6: Pasar los tests.** `SectionHeaderTest` y la puerta global pasan.

- [ ] **Paso 7: Commit.** `feat(ui): cabecera de sección común y un solo título en Tareas y Compra`

---

### Tarea 2: un CTA por pantalla y «Quitar filtros» (B2)

**Frontend:** `impeccable:impeccable distill J/feature/tasks/TasksScreen.kt J/feature/pantry/PantryScreen.kt J/feature/spaces/SpacesScreen.kt J/feature/meals/MealsScreen.kt`

**Ficheros:**
- Modificar:
  - `J/feature/tasks/TasksScreen.kt` (estado vacío :228-243, estado sin resultados);
  - `J/feature/tasks/TasksViewModel.kt`;
  - `J/feature/pantry/PantryScreen.kt` (:148-156);
  - `J/feature/spaces/SpacesScreen.kt` (`SpacesEmptyState`, :258-278);
  - `J/feature/meals/MealsScreen.kt` (`MealSlotSection`, :390-425);
  - `S`.
- Test: `T/feature/tasks/TasksViewModelTest.kt` (añadir).

**Interfaces:**
- Produce: `TasksViewModel.clearFilters()`. Deja `filters = TaskFilters()`.
- Consume: `SectionHeader` (tarea 1), sin cambios.

- [ ] **Paso 1: Escribir el test que falla.** `clearFilters goes back to the default filters`: tras `setFilters(TaskFilters(status = TaskStatus.DONE, priority = TaskPriority.HIGH, type = <cualquier TaskType>, assigneeId = "u2"))` y `clearFilters()`, `uiState.value.filters == TaskFilters()`.

- [ ] **Paso 2: Comprobar que falla.** Resultado esperado: FALLO de compilación, porque `clearFilters` no existe.

- [ ] **Paso 3: Implementar.**
  - **Estados vacíos sin botón:** Tareas, Despensa y Espacios pierden `actionLabel`/`onAction`. En Espacios, «Unirse con invitación» se queda solo en su botón fijo de arriba.
  - **Comidas:** fuera los tres «+ Añadir». Una franja vacía dice solo `meals_slot_empty`.
  - **Tareas sin resultados con filtros:** `tasks_no_results` gana la acción «Quitar filtros» (cadena nueva `tasks_clear_filters`).
    - Llama a `clearFilters()` y vacía también la búsqueda.
    - Solo sale si los filtros no son los de por defecto o hay búsqueda.
  - **Cadenas sin uso:** se borran (`meals_add`, etc.).

- [ ] **Paso 4: Comprobar que no queda ningún CTA duplicado.**
  - Los `EmptyState(` de `TasksScreen.kt`, `PantryScreen.kt` y `SpacesScreen.kt` no pasan `actionLabel`.
  - `MealsScreen.kt` no tiene ningún botón de alta fuera de `NewMealFab`.

- [ ] **Paso 5: Pasar los tests.** `TasksViewModelTest` y la puerta global pasan.

- [ ] **Paso 6: Commit.** `fix(ui): un solo CTA por pantalla y «Quitar filtros» en Tareas`

---

### Tarea 3: filtros de Tareas en chips con menú (B6, opción C)

**Frontend:** `impeccable:impeccable layout J/feature/tasks/TasksScreen.kt`

**Ficheros:**
- Modificar:
  - `J/feature/tasks/TasksScreen.kt`: `TaskFilterBar` (:413-447), `TaskTypeFilterChip` (:450) y `TaskAssigneeFilterChip` (:484);
  - `S`.
- Test: `A/feature/tasks/TaskFilterBarUiTest.kt`.

**Interfaces:**
- Produce: `internal fun TaskFilterBar(filters: TaskFilters, members: List<SpaceMember>, onChange: (TaskFilters) -> Unit)`. Deja de ser `private` para el test.
- Consume: `TaskFilters` (sin cambios).

- [ ] **Paso 1: Escribir los tests que fallan.** Montan `TaskFilterBar` con un `var filters by mutableStateOf(TaskFilters())` y `members = [SpaceMember «Ana»]`.
  - `theStatusChipShowsTheChosenValue`:
    - al principio hay un chip «Pendientes» seleccionado (`SemanticsProperties.Selected` = true);
    - tras abrirlo y elegir «Completadas», dice «Completadas» y `filters.status == DONE`;
    - tras elegir «Todas», dice «Estado», no está seleccionado y `filters.status == null`.
  - `thePriorityChipShowsTheChosenValue`:
    - al principio dice «Prioridad» y no está seleccionado;
    - tras elegir «Alta», dice «Alta», está seleccionado y `filters.priority == HIGH`.
  - `theMenuMarksTheCurrentOption`: con el menú de Prioridad abierto y «Alta» elegida, el elemento «Alta» del menú es `Selected` = true y «Media» es false.
  - `noChipLabelIsEllipsizedAtLargeFont`: con `Density(fontScale = 1.3f)`, el texto de ningún chip tiene desbordamiento visual. Sigue el patrón de `WorkspaceNavigationBarTest`, que ya comprueba etiquetas sin elipsis al 130 %.

- [ ] **Paso 2: Comprobar que fallan.** Resultado esperado: FALLO de compilación (`TaskFilterBar` es privada) o de aserción (no hay chip de estado).

- [ ] **Paso 3: Implementar.**
  - **Cuatro chips** `FilterChip` + `DropdownMenu`, con el patrón de `TaskTypeFilterChip`, en este orden:
    - **Estado:** Pendientes / Completadas / Todas. La cadena nueva `tasks_filter_status` es «Estado».
    - **Prioridad:** Todas / Alta / Media / Baja. Cada una con su bandera teñida (`TaskPriority` de la fase A).
    - **Tipo** y **Responsable**, como hoy.
  - **El chip** dice su nombre y no está seleccionado sin filtro. Con filtro, dice el valor y está seleccionado.
  - **En cada menú,** la opción vigente lleva el icono `Check` como `trailingIcon` y `Modifier.semantics { selected = true }`.
  - **La fila** sigue con `horizontalScroll`. No se abrevia ninguna etiqueta y ninguna lleva `maxLines` con elipsis (decisión del usuario).
  - Fuera los cinco `FilterChip` sueltos.

- [ ] **Paso 4: Pasar los tests.** `TaskFilterBarUiTest` y la puerta global pasan.

- [ ] **Paso 5: Commit.** `feat(ui): filtros de Tareas en cuatro chips con menú`

---

### Tarea 4: metadatos que aportan y tarjetas que se pulsan (B3)

**Frontend:** `impeccable:impeccable distill J/feature/shopping/ShoppingScreen.kt J/feature/recipes/RecipesScreen.kt J/feature/meals/MealsScreen.kt J/feature/notes/NotesScreen.kt`

**Ficheros:**
- Crear:
  - `J/feature/shopping/ShoppingAuthors.kt`;
  - `J/feature/meals/MealSummary.kt`.
- Modificar:
  - `J/feature/shopping/ShoppingScreen.kt` (firma :102; `ShoppingItemCard`, :~545-552);
  - `J/core/navigation/BobitosNavHost.kt` (:322);
  - `J/feature/recipes/RecipesScreen.kt` (:469);
  - `J/feature/meals/MealsScreen.kt` (`MealCard`, :446-545);
  - `J/feature/notes/NotesScreen.kt` (`NoteCard`, :201);
  - `S` (`meals_cook_value` sin 🍳, plurales de ingredientes).
- Tests:
  - `T/feature/shopping/ShoppingAuthorsTest.kt`;
  - `T/feature/meals/MealSummaryTest.kt`;
  - `A/feature/meals/MealCardUiTest.kt`;
  - `A/feature/notes/NoteCardUiTest.kt`.

**Interfaces:**
- Produce:
  - `internal fun showsAuthor(memberCount: Int?): Boolean`. Es `memberCount == null || memberCount > 1`.
  - `ShoppingScreen(..., memberCount: Int? = null, ...)`.
  - `internal data class MealSummary(val ingredientCount: Int?, val participantNames: List<String>)`.
  - `internal fun mealSummary(meal: Meal, recipe: Recipe?): MealSummary`. `ingredientCount` es null sin receta o con 0 ingredientes.
  - `MealCard` y `NoteCard` pasan a `internal`, con `onClick: (() -> Unit)?`. Con null, no se pueden pulsar.
- Consume: nada nuevo.

- [ ] **Paso 1: Escribir los tests que fallan.**
  - `ShoppingAuthorsTest`: `showsAuthor(1) == false`, `showsAuthor(2) == true` y `showsAuthor(null) == true`.
  - `MealSummaryTest`:
    - con receta de 5 ingredientes y participantes «Ana» y «David», da `MealSummary(5, ["Ana", "David"])`;
    - sin `recipeId`, `ingredientCount == null`;
    - con receta sin ingredientes, `ingredientCount == null`;
    - sin participantes, `participantNames == []`.
  - `MealCardUiTest.tappingTheCardOpensItsEditor`: pulsar el nombre de la comida llama a `onClick`. Con `onClick = null`, el nodo de la tarjeta no tiene `SemanticsActions.OnClick`.
  - `MealCardUiTest.theCardSummarisesInsteadOfListingIngredients`: con una receta de 5 ingredientes («400 g garbanzos»…), se ve «5 ingredientes» y no existe el texto «400 g garbanzos».
  - `NoteCardUiTest.tappingTheCardOpensItsEditor`: igual que el de `MealCard`, con una nota.

- [ ] **Paso 2: Comprobar que fallan.** Resultado esperado: FALLO de compilación, porque las funciones no existen y las tarjetas son privadas.

- [ ] **Paso 3: Implementar.**
  - **Compra:**
    - `ShoppingScreen` recibe `memberCount`, y el `NavHost` le pasa `space.memberCount`;
    - «Añadido por» y «Marcado por» solo salen si `showsAuthor(memberCount)`.
  - **Recetario:** las filas dejan de mostrar «por …» en las dos secciones.
  - **`MealCard`:**
    - nombre y una línea de resumen: «5 ingredientes · Ana, David», con `<plurals>` `meals_ingredient_count`. Sin participantes, «Sin asignar» (`meals_no_participants`);
    - el cocinero y «Cocinada» llevan iconos `Icons.Rounded` y texto, sin 🍳 ni ✓ (`meals_cook_value` pierde el emoji);
    - es `Card(onClick)` y abre su editor, como el menú «Editar», solo con `canWrite`.
  - **`NoteCard`:** es `Card(onClick)` y abre su editor, solo con `canWrite`.
  - **Sin `canWrite`,** las dos tarjetas son `Card` normales, sin `onClick`. `enabled = false` cambiaría su color.

- [ ] **Paso 4: Pasar los tests.** Los cuatro tests y la puerta global pasan.

- [ ] **Paso 5: Commit.** `fix(ui): metadatos solo cuando aportan y tarjetas de comida y nota pulsables`

---

### Tarea 5: la semana sin ruido (B4, opción A)

**Frontend:** `impeccable:impeccable distill J/feature/calendar/CalendarScreen.kt J/feature/calendar/PersonalCalendarScreen.kt`

**Ficheros:**
- Crear:
  - `J/feature/calendar/WeekRows.kt` (lógica pura);
  - `J/feature/calendar/FoldedDaysRow.kt` (composable).
- Modificar:
  - `J/core/common/BobitosDates.kt`;
  - `J/feature/calendar/CalendarScreen.kt` (`WeekEventList`, :492-535);
  - `J/feature/calendar/PersonalCalendarScreen.kt` (`PersonalWeekEventList`, :298-332);
  - `S`.
- Tests:
  - `T/feature/calendar/WeekRowsTest.kt`;
  - `T/core/common/BobitosDatesTest.kt` (añadir, o el test del formateador que ya exista);
  - `A/feature/calendar/FoldedDaysRowTest.kt`.

**Interfaces:**
- Produce:
  - `BobitosDates.shortDay(date: LocalDate): String`, «mar 13». Abreviaturas lun, mar, mié, jue, vie, sáb, dom.
  - `BobitosDates.shortDayRange(start: LocalDate, end: LocalDate): String`, «mar 13 – vie 16». Si `start == end`, `shortDay(start)`.
  - `sealed interface WeekRow { data class Day(val date: LocalDate) : WeekRow; data class EmptyRun(val start: LocalDate, val end: LocalDate) : WeekRow }`.
  - `internal fun weekRows(days: List<LocalDate>, daysWithEvents: Set<LocalDate>, today: LocalDate): List<WeekRow>`:
    - cada día con eventos y `today` son `Day`;
    - los días vacíos consecutivos (sin `today`) forman un `EmptyRun`;
    - el orden es el de `days`.
  - `@Composable internal fun FoldedDaysRow(start: LocalDate, end: LocalDate, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier)`.
- Consume: nada nuevo.

- [ ] **Paso 1: Escribir los tests que fallan.** La semana es del lunes 12 al domingo 18 de octubre de 2026, y hoy es el sábado 17.
  - `WeekRowsTest`:
    - `emptyDaysInTheMiddleAreGrouped`: con eventos el 12 y el 17, da `[Day(12), EmptyRun(13,16), Day(17), EmptyRun(18,18)]`;
    - `aWholeEmptyWeekIsOneRunWhenTodayIsOutside`: hoy es el 20 y no hay eventos: `[EmptyRun(12,18)]`;
    - `todayNeverFolds`: hoy es el 15 y hay eventos solo el 12: `[Day(12), EmptyRun(13,14), Day(15), EmptyRun(16,18)]`;
    - `aFullWeekHasNoRuns`: con eventos los 7 días, da 7 `Day`;
    - `emptyDaysAtTheStart`: con eventos solo el 18, `[EmptyRun(12,16), Day(17), Day(18)]`.
  - `BobitosDatesTest`, con `Locale.setDefault(Locale.US)`:
    - `shortDay(2026-10-13) == "mar 13"` y `shortDay(2026-10-14) == "mié 14"`;
    - `shortDayRange(13, 16) == "mar 13 – vie 16"`;
    - `shortDayRange(18, 18) == "dom 18"`.
  - `FoldedDaysRowTest`:
    - `theRowIsAButtonThatAnnouncesItsState`: con `expanded = false`, el nodo tiene `Role.Button`, `StateDescription` «Plegado» (cadena `state_collapsed` de la tarea 1) y una `ContentDescription` que contiene «13 de octubre», «16 de octubre» y «sin eventos». Pulsarlo llama a `onToggle`.
    - `theVisibleTextIsTheShortRange`: con `expanded = true`, «Desplegado», y el texto visible es «mar 13 – vie 16 · Sin eventos».

- [ ] **Paso 2: Comprobar que fallan.** Resultado esperado: FALLO de compilación, porque las funciones no existen.

- [ ] **Paso 3: Implementar.**
  - **`WeekEventList` y `PersonalWeekEventList`** pintan `weekRows(...)`.
    - Un `Day` va como hoy, con su cabecera y sus eventos, o «Sin eventos» si es hoy y está vacío.
    - Un `EmptyRun` es una `FoldedDaysRow`: texto `onSurfaceVariant`, `calendar_empty_run` = «%1$s · Sin eventos».
  - **Al desplegarla,** salen sus días con la cabecera y «Sin eventos», como antes.
    - En el Calendario del espacio, la cabecera de cada día sigue llevando al día.
    - En Mi calendario, no lleva a ningún sitio.
  - **El estado desplegado** es un `rememberSaveable(weekStart) { mutableStateOf(setOf<LocalDate>()) }` con el `start` de cada grupo abierto. Al cambiar de semana se reinicia.
  - **La descripción accesible** (`calendar_empty_run_description`) usa `BobitosDates.longDay` de los dos extremos más «sin eventos». La redacción exacta queda al implementador, dentro de lo que exige el test.

- [ ] **Paso 4: Pasar los tests.** Los tres tests y la puerta global pasan.

- [ ] **Paso 5: Commit.** `feat(ui): la semana junta los días vacíos en una línea plegable`

---

### Tarea 6: el Calendario no finge estar vacío (B5)

**Frontend:** `impeccable:impeccable harden J/feature/calendar/CalendarScreen.kt J/feature/calendar/PersonalCalendarScreen.kt`

**Ficheros:**
- Modificar:
  - `J/feature/calendar/CalendarViewModel.kt` (suscripción de eventos, :~190-205);
  - `J/feature/calendar/PersonalCalendarViewModel.kt` (:~210-222);
  - `J/feature/calendar/CalendarScreen.kt` (:147 y el área de contenido);
  - `J/feature/calendar/PersonalCalendarScreen.kt` (:~85 y el área de contenido).
- Tests:
  - `T/feature/calendar/CalendarViewModelTest.kt` (añadir);
  - `T/feature/calendar/PersonalCalendarViewModelTest.kt` (añadir).

**Interfaces:**
- Produce: `CalendarViewModel.retryEvents()` y `PersonalCalendarViewModel.retryEvents()`. Las dos vuelven a suscribir los eventos del periodo actual.
- Consume: `ErrorState`, `LoadingState` y `FailureKind` (existentes).

- [ ] **Paso 1: Escribir los tests que fallan** (en los dos ViewModels). El repositorio falso falla la primera suscripción de `events` y emite eventos en la segunda.
  - `aFailedLoadLeavesEventsInError`: tras observar, `events` es `UiState.Error`.
  - `retryingResubscribesAndCanSucceed`: tras `retryEvents()`, `events` es `UiState.Content` con los eventos.
  - `retryingWhileItStillFailsShowsTheErrorAgain`: si el repositorio falla otra vez, `events` vuelve a ser `Error`, no `Loading`. Es el punto 5 del Review Focus.

- [ ] **Paso 2: Comprobar que fallan.** Resultado esperado: FALLO de compilación, porque `retryEvents` no existe.

- [ ] **Paso 3: Implementar.**
  - **Los dos ViewModels:**
    - `retryEvents()` relanza la suscripción del periodo actual. Por ejemplo, con un disparador que entre en el `flatMapLatest` que ya exista, o como mejor encaje;
    - en la primera carga, `events` es `Loading`.
  - **Las dos pantallas:**
    - con `UiState.Error`, el área bajo la cabecera del periodo y el selector Día/Semana/Mes muestra `ErrorState(message = <texto de FailureKind>, onRetry = viewModel::retryEvents)`;
    - con `UiState.Loading`, muestra `LoadingState()`;
    - la cabecera y el selector se quedan.

- [ ] **Paso 4: Comprobar las pantallas.** `CalendarScreen.kt` y `PersonalCalendarScreen.kt` usan `ErrorState(` con `onRetry` y `LoadingState(`, y ya no hacen `as? UiState.Content)?.value.orEmpty()` sobre `events`.

- [ ] **Paso 5: Pasar los tests.** Los dos tests y la puerta global pasan.

- [ ] **Paso 6: Commit.** `fix(calendar): Calendario y Mi calendario muestran la carga y el error con Reintentar`

---

### Tarea 7: hoja de detalle con una acción principal (B7, opción A)

**Frontend:** `impeccable:impeccable layout J/core/designsystem/component/BobitosInfoSheet.kt J/feature/recipes/RecipeSheets.kt`

**Ficheros:**
- Crear `J/core/designsystem/component/MoreOptionsButton.kt`.
- Modificar:
  - `J/core/designsystem/component/BobitosInfoSheet.kt`;
  - `J/feature/recipes/RecipeSheets.kt` (`RecipeDetailSheet`, :114-157; `RecipeSummary`, :296);
  - `J/feature/recipes/RecipeSections.kt`;
  - `J/feature/routines/RoutinesScreen.kt` (`RoutineDetailSheet`, :397);
  - `J/feature/exercises/ExerciseDetailSheet.kt` (:37);
  - `S` (`more_options_for`; borrar `recipes_category` y `recipes_by` si se quedan sin uso).
- Tests:
  - `T/feature/recipes/RecipeSectionsTest.kt` (añadir);
  - `A/core/designsystem/component/BobitosInfoSheetTest.kt`;
  - `A/core/designsystem/component/MoreOptionsButtonTest.kt`.

**Interfaces:**
- Produce:
  - `@Composable fun MoreOptionsButton(itemName: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true)`. Es un `IconButton` con `Icons.Rounded.MoreVert` y `contentDescription` = `stringResource(R.string.more_options_for, itemName)` (cadena nueva: «Más opciones de «%1$s»»). La tarea 15 lo usa en los 9 menús ⋮ restantes.
  - `data class InfoSheetAction(val label: String, val onClick: () -> Unit)`, en `BobitosInfoSheet.kt`.
  - La firma nueva de la hoja. Su menú usa `MoreOptionsButton(itemName = title, …)`.
    ```kotlin
    fun BobitosInfoSheet(
        title: String,
        onDismiss: () -> Unit,
        primaryAction: InfoSheetAction? = null,
        secondaryActions: List<InfoSheetAction> = emptyList(),
        content: @Composable ColumnScope.() -> Unit,
    )
    ```
  - `enum class RecipeSheetAction { ADD_TO_SHOPPING, FORK, EDIT, DELETE }`.
  - `internal data class SheetActions<A>(val primary: A?, val secondary: List<A>)`.
  - `internal fun recipeSheetActions(detail: RecipeDetailActions, canAddToShopping: Boolean): SheetActions<RecipeSheetAction>`.
- Consume: `recipeDetailActions` (existente).

- [ ] **Paso 1: Escribir los tests que fallan.**
  - **`RecipeSectionsTest`**, según la tabla de B7:
    - con `canAddToShopping` y una común con `canFork`: `primary = ADD_TO_SHOPPING`, `secondary = [FORK]`;
    - con `canAddToShopping` y una propia (`canEdit`): `primary = ADD_TO_SHOPPING`, `secondary = [EDIT, DELETE]`;
    - sin `canAddToShopping`, una común `canFork`: `primary = FORK`, `secondary = []`;
    - sin `canAddToShopping`, una propia: `primary = EDIT`, `secondary = [DELETE]`;
    - sin nada: `primary = null`, `secondary = []`.
  - **`BobitosInfoSheetTest`:**
    - `thePrimaryActionIsAFilledButtonNextToClose`: con `primaryAction = «Añadir a la compra»`, existen «Añadir a la compra» y «Cerrar», y pulsar la primera llama a su `onClick`;
    - `secondaryActionsLiveInANamedMenu`: con `secondaryActions = [«Editar», «Eliminar»]` y `title = "Ensalada"`, existe un nodo con `ContentDescription` «Más opciones de «Ensalada»». Al pulsarlo aparecen «Editar» y «Eliminar»;
    - `withoutSecondariesThereIsNoMenu`: no existe ningún nodo con descripción que empiece por «Más opciones».
  - **`MoreOptionsButtonTest.itsDescriptionNamesTheItem`:** `MoreOptionsButton("Leche", …)` tiene `ContentDescription` «Más opciones de «Leche»» y acción de clic. Con `enabled = false` está desactivado.

- [ ] **Paso 2: Comprobar que fallan.** Resultado esperado: FALLO de compilación, porque la firma y las funciones nuevas no existen.

- [ ] **Paso 3: Implementar.**
  - **`BobitosInfoSheet`:**
    - el título va en una fila con el `IconButton` ⋮ (`MoreVert`) si hay secundarias, y su `DropdownMenu`;
    - abajo, a la derecha, `TextButton` «Cerrar» y, si hay principal, un `Button` relleno.
  - **Receta:** usa `recipeSheetActions`.
    - «Eliminar» desde el ⋮ sigue haciendo hoy lo mismo que el botón de ahora. La tarea 13 quita su diálogo.
    - La categoría pasa a `InfoChip` en la línea de `RecipeSummary`, junto a raciones y minutos.
    - Fuera la frase «Categoría: …» y la línea «por …».
  - **Rutina:** con `canFork`, la principal es «Guardar como mía».
  - **Ejercicio:** con `canEdit`, la principal es «Editar» y la secundaria «Eliminar».

- [ ] **Paso 4: Pasar los tests.** Los dos tests y la puerta global pasan.

- [ ] **Paso 5: Commit.** `feat(ui): las hojas de detalle tienen una acción principal y el resto en ⋮`

---

### Tarea 8: Perfil ordenado y «Mi día» sin emoji (B8, B9)

**Frontend:**
- Perfil: `impeccable:impeccable layout J/feature/auth/ProfileScreen.kt`.
- «Mi día»: `impeccable:impeccable clarify J/feature/spaces/SpaceHomeSections.kt`.

**Ficheros:**
- Modificar:
  - `J/feature/auth/ProfileScreen.kt` (:106-177, `LegalButtons` :206);
  - `J/feature/spaces/SpaceHomeSections.kt` (`MyDayCard`, :28-60);
  - `S` (`home_my_day_*`, :62-66, y los títulos de las secciones de Perfil).
- Test: `T/feature/spaces/MyDayStringsTest.kt`.

**Interfaces:**
- Consume: `SectionHeader` (tarea 1).
- No produce nada.

- [ ] **Paso 1: Escribir el test que falla** (`MyDayStringsTest`, patrón de `ShoppingStringsTest`: lee `src/main/res/values/strings.xml`). `noMyDayStringStartsWithAnEmoji`: los valores de `home_my_day_title`, `_tasks`, `_cooking`, `_eating`, `_empty` y `meals_cook_value` no contienen ningún carácter de los rangos de emoji. Comprueba los puntos de código ≥ `0x1F000` y los rangos `0x2600–0x27BF`, que incluyen ✅.

- [ ] **Paso 2: Comprobar que falla.** Resultado esperado: FALLO, porque `home_my_day_tasks` empieza por ✅.

- [ ] **Paso 3: Implementar.**
  - **«Mi día»:**
    - las cuatro cadenas pierden el emoji;
    - cada línea de `MyDayCard` es una fila con un icono `Icons.Rounded` decorativo (`contentDescription = null`) y el texto, como `EventsLine`;
    - el texto se entiende sin el icono. La redacción queda al implementador.
  - **Perfil.** Esta parte solo cambia el aspecto: sin test RED.
    - La columna se alinea a la izquierda: fuera `horizontalAlignment = CenterHorizontally`.
    - Lleva cuatro `SectionHeader`, separados por espacio y no por líneas:
      - **Cuenta:** avatar, correo y «Verificado», el nombre con «Guardar nombre», `AuthFeedback` y «Cerrar sesión»;
      - **Apariencia:** el selector de tema;
      - **Recordatorios:** el interruptor y la antelación;
      - **Información:** «Política de privacidad» y «Créditos y licencias».
    - «Eliminar mi cuenta» va al final, en un bloque propio separado por más espacio, con su color de error y su diálogo.
    - El indicador de «Guardar nombre» mientras guarda pasa a un tamaño acorde al botón (unos 20 dp, `strokeWidth` 2 dp).
  - **`ProfileScreenTest`** ya falla en `main` por Hilt (backlog). No se toca ni se añade otro test que monte `ProfileScreen`.

- [ ] **Paso 4: Comprobar Perfil.** `grep -n "CenterHorizontally" app/src/main/java/com/dlunaunizar/bobitos/feature/auth/ProfileScreen.kt` no encuentra la columna principal, y «Eliminar mi cuenta» va después de la sección Información.

- [ ] **Paso 5: Pasar los tests.** `MyDayStringsTest` y la puerta global pasan.

- [ ] **Paso 6: Commit.** `fix(ui): Perfil con secciones alineadas y «Mi día» con iconos en vez de emoji`

---

### Tarea 9: diálogos de Espacios como el resto de formularios y revocar con confirmación (B11, y revocar de B10)

**Frontend:** `impeccable:impeccable harden J/feature/spaces/SpacesScreen.kt J/feature/spaces/SpaceSettingsScreen.kt`

**Ficheros:**
- Modificar:
  - `J/feature/spaces/SpacesScreen.kt`: `InvitationCodeDialog` (:283-330), `SpaceNameDialog` (:395-430) y sus llamadas (:~220-235);
  - `J/feature/spaces/SpaceSettingsScreen.kt` (renombrar en :218; `InvitationCard` :332-400);
  - `J/feature/spaces/SpacesViewModel.kt` (si hace falta limpiar el error al editar);
  - `S`.
- Tests:
  - `A/feature/spaces/SpaceDialogsUiTest.kt`;
  - `A/feature/spaces/InvitationCardUiTest.kt`.

**Interfaces:**
- Produce:
  - `SpaceNameDialog(..., serverError: String?, onEdited: () -> Unit, ...)` e `internal fun InvitationCodeDialog(..., serverError: String?, onEdited: () -> Unit, ...)`.
    - El error de campo lo calculan ellos con `SpaceValidation.validateName` / `InvitationCode.normalize` y `FormValidationState`.
    - `serverError` es el texto humano del ViewModel.
    - `onEdited` se llama al editar el campo y limpia ese error.
  - `internal fun InvitationCard(...)`.
- Consume: `FormValidationState`, `errorSupportingText` y `BobitosDialog` (existentes).

- [ ] **Paso 1: Escribir los tests que fallan.**
  - **`SpaceDialogsUiTest`:**
    - `theNameDialogOpensWithoutErrors`: no hay ningún texto de error;
    - `submittingABlankNameShowsTheErrorAndFocusesTheField`: tras pulsar el botón de confirmar con el campo vacío, se ve el texto de `NameRequired` bajo el campo, el campo está enfocado (`assertIsFocused`) y `onConfirm` no se ha llamado;
    - `theErrorGoesAwayWhenCorrected`: tras escribir «Casa», el error ya no se ve;
    - `aServerErrorClearsWhenEditing`: con `serverError = "Sin permiso."`, al escribir se llama a `onEdited`;
    - los mismos cuatro para `InvitationCodeDialog`, con un código no válido, «abc».
  - **`InvitationCardUiTest`:**
    - `revokingAsksFirst`: pulsar «Revocar» abre un diálogo y no llama a `onRevoke`;
    - confirmar llama a `onRevoke` una vez;
    - «Cancelar» no lo llama.

- [ ] **Paso 2: Comprobar que fallan.** Resultado esperado: FALLO de compilación o de aserción, porque hoy el error viene del ViewModel y revocar no pregunta.

- [ ] **Paso 3: Implementar.**
  - **Los dos diálogos** siguen A5:
    - se abren limpios;
    - Guardar sigue activo salvo mientras guarda o sin conexión, como hoy;
    - al pulsar con datos no válidos no llaman a `onConfirm` y el foco va al campo (`FocusRequester`);
    - desde ese intento, el error se recalcula al escribir.
  - **Errores del servidor:**
    - los de código (`InvalidInvitationCode`, caducado, revocado, ya usado) van bajo el campo;
    - los demás (sin permiso, sin conexión, espacio lleno) van como texto del diálogo con `liveRegion`;
    - todos desaparecen al editar (`onEdited`).
  - **`InvitationCard`:** «Revocar» abre `BobitosDialog(destructive = true)` con título y mensaje nuevos en `S`.
  - **El ViewModel sigue validando** por si acaso. No cambia su contrato.

- [ ] **Paso 4: Pasar los tests.** Los dos tests y la puerta global pasan.

- [ ] **Paso 5: Commit.** `fix(spaces): los diálogos de Espacios validan como el resto y revocar pide confirmación`

---

### Tarea 10: Deshacer al borrar en Notas y Rutinas (B10)

**Frontend:** sin skill de diseño (quita diálogos y añade un snackbar existente).

**Ficheros:**
- Modificar:
  - `J/data/repository/NoteRepository.kt` y `FirestoreNoteRepository.kt` (alta en :59-83, borrado en :122-130);
  - `J/feature/notes/NotesViewModel.kt` (:78);
  - `J/feature/notes/NotesScreen.kt` (diálogo en :184-198);
  - `J/data/repository/RoutineRepository.kt` y `FirestoreRoutineRepository.kt` (alta en :43-70, borrado en :92-96);
  - `J/feature/routines/RoutinesViewModel.kt` (:115);
  - `J/feature/routines/RoutinesScreen.kt` (diálogo en :167-181).
- Tests:
  - `T/feature/notes/NotesViewModelTest.kt` (añadir; `FakeNoteRepository` :177);
  - `T/feature/routines/RoutinesViewModelTest.kt` (añadir; `FakeRoutineRepository` :355);
  - `tests/firebase-emulators.test.mjs` (añadir).

**Interfaces:**
- Produce:
  - `NoteRepository.restoreNote(spaceId: String, note: Note)`. Hace `set` en `spaces/{spaceId}/notes/{note.id}` con title, body y pinned del original, el autor actual y los sellos del servidor.
  - `NotesViewModel.deleteNote(noteId: String, onDeleted: () -> Unit = {})`. `onDeleted` solo se llama si el borrado termina bien; no hay aviso propio.
  - `NotesViewModel.restoreNote(spaceId: String, note: Note)`.
  - `RoutineRepository.restoreRoutine(routine: Routine)`. Hace `set` en `routines/{routine.id}`, siempre PRIVATE, con el dueño actual, `sourceRoutineId` original y sellos del servidor.
  - `RoutinesViewModel.deleteRoutine(routineId: String, onDeleted: () -> Unit = {})` y `RoutinesViewModel.restoreRoutine(routine: Routine)`.
  - `internal fun Routine.canUndoDelete(): Boolean`. Es `visibility == RoutineVisibility.PRIVATE`.
- Consume: `launchUndo` y `LocalSnackbarHostState` (existentes).

- [ ] **Paso 1: Escribir los tests que fallan.**
  - **`NotesViewModelTest`:**
    - `undo is offered only once the delete has finished`: `onDeleted` no se llama si `deleteNote` falla, y sí si termina bien;
    - `undoing a delete brings the note back with its id and pin`: `restoreNote("space-a", note(id = "n1", pinned = true))` llama al repositorio con `"space-a"` y una nota con `id == "n1"` y `pinned == true`;
    - `undo writes to the note's own space`: tras observar `space-b`, `restoreNote("space-a", note)` escribe en `"space-a"`. Es el punto 2 del Review Focus.
  - **`RoutinesViewModelTest`:**
    - los equivalentes de `deleteRoutine`/`restoreRoutine`, con id y `sourceRoutineId` conservados;
    - `canUndoDelete`: true en una PRIVATE y false en una GLOBAL.
  - **Tests de reglas**, en `tests/firebase-emulators.test.mjs`, con el patrón de «deshacer el borrado de un evento lo restaura con su id original» (:984). Son de caracterización: deben pasar sin cambiar las reglas.
    - «deshacer el borrado de una nota la devuelve con su id y fijada»: otro miembro la restaura con `setDoc` y `noteData(uid, { pinned: true })`;
    - «deshacer el borrado de una rutina personal la devuelve con su id».

- [ ] **Paso 2: Comprobar que fallan.**
  - Los tests JVM fallan por compilación, porque las funciones no existen.
  - `npm run test:emulators` pasa ya. Si un test de reglas fallara, el área no se puede deshacer sin cambiar reglas: para y díselo al coordinador.

- [ ] **Paso 3: Implementar.**
  - **Notas:** menú «Eliminar» → `deleteNote(note.id) { scope.launchUndo(snackbar, <«Nota eliminada»>, <«Deshacer»>) { restoreNote(spaceId, note) } }`. Sin `BobitosDialog`. El `spaceId` se captura al borrar.
  - **Rutinas:** si `canUndoDelete()`, igual que Notas. Si no (común, que solo borra el admin), se queda el diálogo actual, sin Deshacer.
  - **Fallo al restaurar:** sale el error de `runAction` en el snackbar.

- [ ] **Paso 4: Pasar los tests.** Los tests JVM, `npm run test:emulators` y la puerta global pasan.

- [ ] **Paso 5: Commit.** `feat(undo): borrar notas y rutinas personales se deshace sin preguntar`

---

### Tarea 11: Deshacer al borrar en Ejercicios e Ingredientes (B10)

**Frontend:** sin skill de diseño.

**Ficheros:**
- Crear `J/feature/ingredients/DeletedIngredientRelay.kt`.
- Modificar:
  - **Ejercicios:**
    - `J/data/repository/ExerciseRepository.kt` y `FirestoreExerciseRepository.kt` (alta en :60-74, `writeWithVersion` en :98-129);
    - `J/feature/exercises/ExercisesViewModel.kt` (:113);
    - `J/feature/exercises/ExercisesScreen.kt` (diálogo en :189-203);
    - `J/feature/exercises/ExerciseDetailSheet.kt`.
  - **Ingredientes:**
    - `J/data/repository/PersonalIngredientRepository.kt` y `FirestorePersonalIngredientRepository.kt` (borrado en :72-88);
    - `J/data/repository/IngredientBrandRepository.kt` y `FirestoreIngredientBrandRepository.kt`;
    - `J/feature/ingredients/IngredientDetailViewModel.kt` (:159, :225);
    - `J/feature/ingredients/IngredientDetailScreen.kt` (`DeleteConfirmations`, :170-205);
    - `J/feature/ingredients/IngredientsViewModel.kt`;
    - `J/feature/ingredients/IngredientsScreen.kt`.
- Tests:
  - `T/feature/exercises/ExercisesViewModelTest.kt` (añadir);
  - `T/feature/ingredients/IngredientDetailViewModelTest.kt` (añadir);
  - `T/feature/ingredients/IngredientsViewModelTest.kt` (añadir o crear);
  - `T/feature/ingredients/IngredientFakes.kt`;
  - `tests/firebase-emulators.test.mjs` (añadir).

**Interfaces:**
- Produce:
  - **Ejercicios:**
    - `ExerciseRepository.restoreExercise(exercise: CatalogExercise)`. Hace `set` en el id original dentro de `writeWithVersion`, que sube `catalogMeta/exercises`. Lleva los campos de `ExerciseInput`, con el dueño actual y sin `source` ni `image`.
    - `internal fun CatalogExercise.canUndoDelete(currentUid: String?): Boolean`. Es `ownerUid == currentUid && source == null && image == null`.
    - `ExercisesViewModel.deleteExercise(id: String, onDeleted: () -> Unit = {})` y `ExercisesViewModel.restoreExercise(exercise: CatalogExercise)`.
  - **Ingredientes:**
    - `data class DeletedIngredient(val ingredient: CatalogIngredient, val brands: List<IngredientBrand>, val stores: List<Supermarket>)`.
    - `PersonalIngredientRepository.restore(deleted: DeletedIngredient)`. Es un único `WriteBatch` con el ingrediente con su slug, cada marca con su id y la entrada de `ingredientPrefs` con `merge`. Si la regla rechaza el alta porque hay un común con ese slug, no se escribe nada.
    - `IngredientBrandRepository.restoreBrand(brand: IngredientBrand)`. Hace `set` en el id original.
    - `@Singleton class DeletedIngredientRelay @Inject constructor()`, con `val deleted: StateFlow<DeletedIngredient?>`, `fun post(deleted: DeletedIngredient)` y `fun consume()`.
    - `IngredientDetailViewModel.deleteIngredient()` publica en el relay solo si el ingrediente es personal y el borrado ha terminado bien.
    - `IngredientDetailViewModel.deleteBrand(brandId: String, onDeleted: () -> Unit = {})` e `IngredientDetailViewModel.restoreBrand(brand: IngredientBrand)`.
    - `IngredientsViewModel.restoreIngredient(deleted: DeletedIngredient)`.
- Consume: `launchUndo` (existente).

- [ ] **Paso 1: Escribir los tests que fallan.**
  - **`ExercisesViewModelTest`:**
    - `canUndoDelete` es true en uno propio sin `source` ni `image`, y false en uno con `source`, en uno con `image` y en uno de otro dueño, aunque borre el admin;
    - `undoing a delete restores the exercise with its id`: `restoreExercise(ex(id = "press-banca-2", name = "Press banca"))` llama al repositorio con ese id, no con `slug(name)`;
    - `onDeleted` solo se llama si el borrado termina bien.
  - **`IngredientDetailViewModelTest`:**
    - `deleting a personal ingredient posts the snapshot`: el relay recibe `DeletedIngredient` con el ingrediente, sus marcas y sus tiendas del estado antes de borrar;
    - `a failed delete posts nothing`;
    - `deleting a common ingredient posts nothing`. El común sigue con su diálogo;
    - `undoing a brand delete restores it with its id`.
  - **`IngredientsViewModelTest.restoreIngredient brings back brands and stores`:** el repositorio falso recibe el `DeletedIngredient` completo.
  - **Tests de reglas** (de caracterización):
    - «deshacer el borrado de un ejercicio propio lo devuelve con su id y sube la versión del catálogo»;
    - «deshacer el borrado de un ingrediente personal devuelve sus marcas y sus tiendas»;
    - «deshacer un ingrediente personal cuyo slug ya es común no escribe nada». El lote falla entero. Es el punto 3 del Review Focus.

- [ ] **Paso 2: Comprobar que fallan.**
  - Los tests JVM fallan por compilación.
  - `npm run test:emulators` pasa ya. Si no, para y díselo al coordinador.

- [ ] **Paso 3: Implementar.**
  - **Ejercicios:**
    - si `canUndoDelete`, el menú y el detalle borran sin diálogo y ofrecen Deshacer;
    - si no, conservan su diálogo actual, sin Deshacer.
  - **Ingrediente personal:**
    - en el detalle, «Eliminar» borra sin diálogo y la pantalla se cierra (`finished`), como hoy;
    - `IngredientsScreen` recoge `relay.deleted`, lo consume y muestra «Ingrediente borrado» con Deshacer → `restoreIngredient(deleted)`.
  - **Ingrediente común** (solo el admin): conserva su diálogo, sin Deshacer.
  - **Marca:** se borra sin diálogo, y el snackbar con Deshacer sale en el mismo detalle.

- [ ] **Paso 4: Pasar los tests.** Los tests JVM, `npm run test:emulators` y la puerta global pasan.

- [ ] **Paso 5: Commit.** `feat(undo): borrar ejercicios propios, ingredientes personales y marcas se deshace sin preguntar`

---

### Tarea 12: un solo camino para borrar en Tareas y Compra, y Deshacer con su estado (B10)

**Frontend:** sin skill de diseño.

**Ficheros:**
- Modificar:
  - `J/data/repository/TaskRepository.kt` y `FirestoreTaskRepository.kt`;
  - `J/feature/tasks/TasksViewModel.kt` (:144);
  - `J/feature/tasks/TasksScreen.kt` (diálogo en :307-319, menú en :603);
  - `J/data/repository/ShoppingRepository.kt` y `FirestoreShoppingRepository.kt`;
  - `J/feature/shopping/ShoppingViewModel.kt` (:255);
  - `J/feature/shopping/ShoppingScreen.kt` (diálogo en :359-372).
- Tests:
  - `T/feature/tasks/TasksViewModelTest.kt` (añadir);
  - `T/feature/shopping/ShoppingViewModelTest.kt` (añadir);
  - `tests/firebase-emulators.test.mjs` (añadir).

**Interfaces:**
- Produce:
  - `TaskRepository.restoreTask(spaceId: String, task: TaskItem)`.
    - Hace un alta con el id original, con `status == "TODO"` como exige la regla.
    - Si `task` estaba completada, después hace una **actualización directa** del estado a completada. No usa el camino de completar, que avanza la recurrencia.
  - `ShoppingRepository.restoreItem(spaceId: String, item: ShoppingItem)`. Alta con el id original y, si estaba comprado, actualización a comprado.
  - Las firmas de `TasksViewModel.restoreTask(spaceId, task)` y `ShoppingViewModel.restoreItem(spaceId, item)` no cambian. Pasan a usar esos métodos.
- Consume: nada nuevo.

- [ ] **Paso 1: Escribir los tests que fallan.**
  - **`TasksViewModelTest`:**
    - `undoing a delete keeps the id and the completed state`: `restoreTask("s1", task(id = "t1", status = DONE))` llama a `restoreTask` del repositorio con `id == "t1"` y `status == DONE`;
    - `undoing a completed recurring task does not advance it`: el repositorio falso registra que no se llamó a completar ni a avanzar la recurrencia. Es el punto 1 del Review Focus. Si la recurrencia vive en el repositorio, ponlo en un test de repositorio o en el de reglas.
  - **`ShoppingViewModelTest.undoing a delete keeps the id and the purchased state`.**
  - **Tests de reglas** (de caracterización): «deshacer el borrado de una tarea completada la devuelve con su id y completada (alta y actualización)», y lo mismo con un producto comprado.

- [ ] **Paso 2: Comprobar que fallan.**
  - Los tests JVM fallan por compilación o por aserción, porque hoy el alta usa un id nuevo y pierde el estado.
  - `npm run test:emulators` pasa ya.

- [ ] **Paso 3: Implementar.**
  - **Tareas y Compra:** el menú «Eliminar» borra sin diálogo y ofrece Deshacer, igual que deslizar. Fuera los dos `BobitosDialog` de borrado.
  - **«Limpiar comprados»** conserva su diálogo.

- [ ] **Paso 4: Pasar los tests.** Los tests JVM, `npm run test:emulators` y la puerta global pasan.

- [ ] **Paso 5: Commit.** `feat(undo): un solo camino para borrar en Tareas y Compra, y Deshacer con su estado`

---

### Tarea 13: Deshacer sin preguntar en Comidas, Deporte, Calendario, Mi calendario y Recetario (B10)

**Frontend:** sin skill de diseño.

**Ficheros:**
- Modificar:
  - **Comidas:** `J/data/repository/MealRepository.kt` y `FirestoreMealRepository.kt`; `J/feature/meals/MealsViewModel.kt` (:163); `J/feature/meals/MealsScreen.kt` (`DeleteMealDialog` :732, uso :254-267).
  - **Deporte:** `J/data/repository/SportActivityRepository.kt` y `FirestoreSportActivityRepository.kt` (alta en :74-110, borrado en :187-199); `J/feature/sport/SportViewModel.kt` (:160); `J/feature/sport/SportScreen.kt` (:142-147, :218-230).
  - **Calendario:** `J/feature/calendar/CalendarScreen.kt` (:244-260, `DeleteEventDialog` :937, `toInput` :1017).
  - **Mi calendario:** `J/feature/calendar/PersonalCalendarViewModel.kt` (:151); `J/feature/calendar/PersonalCalendarScreen.kt` (:236-244, :~285-295).
  - **Recetario:** `J/feature/recipes/RecipesScreen.kt` (:176-180, :291-303).
- Tests:
  - `T/feature/meals/MealsViewModelTest.kt`;
  - `T/feature/sport/SportViewModelTest.kt`;
  - `T/feature/calendar/PersonalCalendarViewModelTest.kt` (a `PersonalRecordingCalendarRepository` :145 se le añade una lista `created`);
  - `tests/firebase-emulators.test.mjs`.

**Interfaces:**
- Produce:
  - **Comidas:** `MealRepository.restoreMeal(spaceId: String, meal: Meal)`. Hace `set` con el id original, con `cooked`, `cookId`, `recipeId` y participantes. La regla de alta admite `cooked`.
  - **Deporte:**
    - `SportActivityRepository.deleteActivity(spaceId, activityId): CalendarEvent?`. Devuelve la foto del evento enlazado que ha borrado.
    - `SportActivityRepository.restoreActivity(spaceId: String, activity: SportActivity, event: CalendarEvent?)`. En una transacción, la actividad con su id y `done = false`, y el evento con su id y sus datos. Después, si `activity.done`, `setDone`.
    - `SportViewModel.deleteActivity(activityId, onDeleted: (CalendarEvent?) -> Unit = {})` y `SportViewModel.restoreActivity(spaceId: String, activity: SportActivity, event: CalendarEvent?)`.
  - **Mi calendario:**
    - `internal fun CalendarEvent.toInput(): EventInput` pasa de privada a un fichero compartido de `feature/calendar`.
    - `PersonalCalendarViewModel.deleteEvent(spaceId, eventId, onDeleted: () -> Unit = {})` y `PersonalCalendarViewModel.restore(spaceId: String, eventId: String, input: EventInput)`, que llama a `repository.createEvent(spaceId, input, eventId)`.
  - **Comidas:** `MealsViewModel.restoreMeal(spaceId: String, meal: Meal)`. El `spaceId` se pasa explícito.
- Consume: `CalendarViewModel.delete`/`restore` (existentes) y `launchUndo`.

- [ ] **Paso 1: Escribir los tests que fallan.**
  - **`MealsViewModelTest`:**
    - `undoing a delete keeps the id and the cooked state`: `restoreMeal("s1", meal(id = "m1", cooked = true))` llama a `restoreMeal` del repositorio con `id == "m1"` y `cooked == true`;
    - `undo writes to the meal's own space`, con otro espacio observado. Es el punto 2 del Review Focus.
  - **`SportViewModelTest`:**
    - `undoing a delete keeps ids and done`: con una actividad `done = true` y un evento enlazado con el título editado «Pierna (editado)», `restoreActivity` recibe la actividad con su id y `done = true`, y el evento con su id y ese título. Es el punto 4 del Review Focus.
  - **`PersonalCalendarViewModelTest`:**
    - `undoing a delete recreates the event with its original id in its space`;
    - `undo is offered only once the delete has finished`.
  - **Tests de reglas** (de caracterización):
    - «deshacer el borrado de una comida cocinada la devuelve con su id y cocinada»;
    - «deshacer el borrado de una actividad hecha devuelve actividad y evento con sus ids, y después se marca hecha».

- [ ] **Paso 2: Comprobar que fallan.**
  - Los tests JVM fallan por compilación o por aserción.
  - `npm run test:emulators` pasa ya. Si no, para y díselo al coordinador.

- [ ] **Paso 3: Implementar.**
  - **Comidas, Deporte, Calendario y Recetario,** también «Eliminar» desde el ⋮ de la hoja de receta:
    - borran sin diálogo;
    - ofrecen Deshacer cuando el borrado termina bien.
  - **Mi calendario:** igual, con `restore`.
  - **Diálogos:** se borran `DeleteMealDialog`, `DeleteEventDialog`, el de Deporte y el de Recetario, y sus cadenas sin uso.
  - **Recetas comunes:** conservan el Deshacer actual (`validRecipeRestore`).

- [ ] **Paso 4: Pasar los tests.** Los tests JVM, `npm run test:emulators` y la puerta global pasan.

- [ ] **Paso 5: Commit.** `feat(undo): Comidas, Deporte, Calendario, Mi calendario y Recetario borran sin preguntar y deshacen con su estado`

---

### Tarea 14: tira de días común y accesible (B12)

**Frontend:** `impeccable:impeccable extract J/feature/meals/MealsScreen.kt J/feature/sport/SportScreen.kt`

**Ficheros:**
- Crear `J/core/designsystem/component/WeekDayStrip.kt`.
- Modificar:
  - `J/feature/meals/MealsScreen.kt` (`WeekSelector` :342, `DayChip` :373);
  - `J/feature/sport/SportScreen.kt` (`WeekSelector` :268, `DayChip` :296);
  - `S`.
- Test: `A/core/designsystem/component/WeekDayStripTest.kt`.

**Interfaces:**
- Produce:
  ```kotlin
  @Composable fun WeekDayStrip(
      days: List<LocalDate>,
      selected: LocalDate,
      today: LocalDate,
      onSelect: (LocalDate) -> Unit,
      onPrevious: () -> Unit,
      onNext: () -> Unit,
      previousLabel: String,
      nextLabel: String,
      modifier: Modifier = Modifier,
      colors: WeekDayStripColors = WeekDayStripDefaults.colors(),
  )
  ```
  - `WeekDayStripColors(selectedContainer, selectedContent, todayOutline)`. Por defecto `secondaryContainer`, `onSecondaryContainer` y `outline`. La fase C pasará los del módulo.
- Consume: `BobitosDates.accessibleDate` y `BobitosDates.dayInitial` (existentes).

- [ ] **Paso 1: Escribir los tests que fallan** (`WeekDayStripTest`). La semana es del 12 al 18 de octubre de 2026, con el elegido el 14 y hoy el 17.
  - `eachDayIsSelectableWithItsFullName`: existe un nodo con `ContentDescription` que contiene «miércoles, 14 de octubre», con `Selected` = true. El del 13 tiene `Selected` = false.
  - `todayIsAnnounced`: la descripción del 17 contiene «hoy» (cadena nueva `today`).
  - `initialAndNumberAreNotReadSeparately`: no hay ningún nodo que sea solo el texto «X» o «14» con semántica propia (`clearAndSetSemantics` en el día).
  - `tappingADaySelectsIt`: pulsar el del 13 llama a `onSelect(2026-10-13)`.
  - `daysAreTallEnough`: la altura de cada día es ≥ 48 dp.
  - `theStripIsASelectableGroup`: el contenedor de los días tiene `SemanticsProperties.SelectableGroup`.

- [ ] **Paso 2: Comprobar que fallan.** Resultado esperado: FALLO de compilación, porque `WeekDayStrip` no existe.

- [ ] **Paso 3: Implementar.**
  - **`WeekDayStrip`:**
    - flecha anterior, siete días y flecha siguiente;
    - cada día es `selectable(role = Role.Tab)` dentro de un `selectableGroup()`, con `clearAndSetSemantics { contentDescription = …; selected = … }`;
    - el elegido va en `selectedContainer`/`selectedContent` y hoy con borde `todayOutline`;
    - de alto, 48 dp; de ancho, lo que dé la fila, con las flechas en la misma fila (spec B12).
  - **Comidas y Deporte** usan `WeekDayStrip` y borran sus copias privadas de `WeekSelector` y `DayChip`.

- [ ] **Paso 4: Pasar los tests.** `WeekDayStripTest` y la puerta global pasan.

- [ ] **Paso 5: Commit.** `feat(a11y): una sola tira de días para Comidas y Deporte, accesible con TalkBack`

---

### Tarea 15: TalkBack en el calendario, «Más opciones» con nombre y `Card(onClick)` (B12)

**Frontend:** `impeccable:impeccable harden J/feature/calendar/CalendarScreen.kt J/feature/calendar/PersonalCalendarScreen.kt J/feature/tasks/TasksScreen.kt J/feature/shopping/ShoppingScreen.kt J/feature/sport/SportScreen.kt J/feature/routines/RoutinesScreen.kt J/feature/notes/NotesScreen.kt J/feature/exercises/ExercisesScreen.kt J/feature/meals/MealsScreen.kt J/feature/recipes/RecipesScreen.kt J/feature/ingredients/IngredientsScreen.kt`

**Ficheros:**
- Modificar:
  - **Calendario:** `CalendarScreen.kt`: `DayCell` (:428-490), `HourRow` (:620-658), `EventRow` (:660-727). `PersonalCalendarScreen.kt` (`PersonalEventRow` :333).
  - **«Más opciones»:** `TasksScreen.kt:592`, `ShoppingScreen.kt:569`, `CalendarScreen.kt:703`, `SportScreen.kt:383`, `RoutinesScreen.kt:313`, `NotesScreen.kt:233`, `ExercisesScreen.kt:310`, `MealsScreen.kt:174` y `MealsScreen.kt:502`.
  - **`Card(onClick)`:** `RoutinesScreen.kt:283`, `RecipesScreen.kt:455` e `IngredientsScreen.kt:296`.
  - `S`.
- Test: `A/feature/calendar/CalendarSemanticsTest.kt`.

**Interfaces:**
- Produce: `DayCell`, `HourRow` y `EventRow` pasan a `internal` para el test.
- Consume: `MoreOptionsButton` (tarea 7) y la cadena `today` (tarea 14).

- [ ] **Paso 1: Escribir los tests que fallan** (`CalendarSemanticsTest`).
  - `aMonthCellIsAButtonWithSelectionAndToday`:
    - la celda del 17 (hoy y elegida) tiene `Role.Button`, `Selected` = true y una descripción que contiene «hoy»;
    - la del 16 tiene `Selected` = false y no contiene «hoy».
  - `anHourRowIsNotAnEmptyButton`:
    - sin `canWrite`, la fila de las 10:00 no tiene `SemanticsActions.OnClick`;
    - con `canWrite`, tiene una `CustomAccessibilityAction` cuya etiqueta contiene «10:00», y ejecutarla llama a la función de crear con las 10:00.
  - `anEventRowNamesItsMenu`: `EventRow` con un evento «Cena con Ana» tiene un nodo con `ContentDescription` «Más opciones de «Cena con Ana»».
  - `anEventRowIsClickableOnlyWhenWritable`:
    - con `canWrite`, el nodo de la tarjeta tiene `OnClick`;
    - sin `canWrite`, no lo tiene.

- [ ] **Paso 2: Comprobar que fallan.** Resultado esperado: FALLO de compilación (`private`) o de aserción.

- [ ] **Paso 3: Implementar.**
  - **`DayCell`:**
    - `semantics { role = Role.Button; selected = … }`;
    - la descripción actual (fecha accesible y número de eventos) gana «hoy» si lo es;
    - los días de otro mes siguen pulsables, y su fecha accesible ya dice el mes.
  - **`HourRow`:**
    - fuera el `onClick = {}`;
    - mantener pulsado sigue creando el evento, por ejemplo con `pointerInput { detectTapGestures(onLongPress = …) }`;
    - con `canWrite`, `semantics { customActions = listOf(CustomAccessibilityAction(<«Crear evento a las HH:00»>) { … ; true }) }`.
  - **«Más opciones»:** los 9 menús ⋮ pasan a `MoreOptionsButton(itemName = <nombre del elemento>, …)`.
    - En el menú del día de Comidas, el nombre es `BobitosDates.longDay(date)`.
    - `more_options` se borra si queda sin uso.
  - **«hoy»** en `DayCell` usa la cadena `today` de la tarea 14.
  - **`Card(onClick)`:** `EventRow`, `PersonalEventRow`, `RoutineCard`, `RecipeCard` e `IngredientRow` pasan de `Card(Modifier.clickable)` a `Card(onClick = …)`. Si no se pueden pulsar, quedan como `Card` sin `onClick`, nunca con `enabled = false`.

- [ ] **Paso 4: Comprobar que no queda ningún ⋮ sin nombre ni `Card` con `clickable`.**
  - `grep -rn "Icons.Rounded.MoreVert" app/src/main/java` solo encuentra `MoreOptionsButton.kt`.
  - `grep -rn "R.string.more_options)" app/src/main/java` sale vacío.
  - Ninguna `Card(` de los ficheros de la tarea lleva `Modifier...clickable`.

- [ ] **Paso 5: Pasar los tests.** `CalendarSemanticsTest` y la puerta global pasan.

- [ ] **Paso 6: Commit.** `feat(a11y): celdas y horas del calendario con rol y estado, «Más opciones» con nombre y tarjetas con Card(onClick)`
