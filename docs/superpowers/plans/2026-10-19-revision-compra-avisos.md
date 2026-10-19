# Revisión de «Añadir a la compra»: avisos y «No añadir». Plan de implementación

> **Para agentes:** se ejecuta con la skill `delegating-plan-execution` del usuario. El marco es superpowers:executing-plans.
> - Es un **plan pequeño** (2 tareas): pasos 1, 2, 3, 5 y 6, sin `simplify`.
> - Cada tarea la hace un subagente `sonnet`, en serie. La revisión final la hace `opus` (si falla, `fable`).
> - Los commits, el push y el PR los hace el coordinador.
> - **Interfaz:** las dos tareas tocan la revisión de «Añadir a la compra». Llevan `Frontend: sin skill de diseño (el plan
>   fija el diseño)`. La revisión de diseño (después del paso 3) mira esa revisión, abierta desde Recetario y desde
>   Comidas, en tema claro y oscuro y con la letra al 130 %.
> - **Ninguna tarea cambia `firestore.rules`, `migrations/`, los repositorios ni los scripts de Node.**
> - Los pasos usan casillas (`- [ ]`).

Paralelo: no (la Task 2 depende de la Task 1, y su test instrumentado usa el único emulador)

**Objetivo:** que cada ingrediente de la revisión de «Añadir a la compra» muestre a la vez sus avisos de despensa y de
lista como etiquetas con icono, y que todos, también los nuevos, tengan la opción «No añadir».

**Arquitectura:**
- La lógica sigue en funciones puras de `IngredientReview.kt`.
- La Task 1 añade los avisos de cada fila (`badges()`) y deja la pista (`hint()`) solo con la cantidad de la receta.
- La Task 2 los pinta con `InfoChip`, que pasa a aceptar el color de fondo, y pone el selector en todas las filas.
- `buildIngredientReviewRows`, `ingredientReviewWrites`, `applyIngredientReview` y los ViewModels no cambian.

**Tecnología:** Kotlin, Jetpack Compose y Material 3. Tests JUnit 4 en JVM y un test instrumentado de Compose
(`createAndroidComposeRule<ComponentActivity>()`).

**Spec:** `docs/superpowers/specs/2026-10-19-revision-compra-avisos-design.md`.

## Restricciones globales

**Rutas**
- `J/` = `app/src/main/java/com/dlunaunizar/bobitos/`.
- `T/` = `app/src/test/java/com/dlunaunizar/bobitos/` (JVM).
- `A/` = `app/src/androidTest/java/com/dlunaunizar/bobitos/` (instrumentados).
- `strings.xml` = `app/src/main/res/values/strings.xml`.

**Entorno y comandos**
- **Java:** antes de cada `./gradlew`, ejecuta `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- **Test JVM suelto:** `./gradlew :app:testDebugUnitTest --tests "<FQCN>"`.
- **Test instrumentado suelto:** `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`.
  Si `adb devices` no lista el emulador: `export PATH="$HOME/Library/Android/sdk/emulator:$HOME/Library/Android/sdk/platform-tools:$PATH"; nohup emulator -avd Pixel_10 -no-boot-anim >/dev/null 2>&1 & adb wait-for-device`.
- **Puerta de cada tarea (`gate.sh`):** `./gradlew ktlintCheck detekt testDebugUnitTest assembleDebug lintDebug :app:compileDebugAndroidTestKotlin`,
  más el instrumentado que nombre la tarea.
- Si un cambio rompe `config/detekt/baseline.xml`, regenera el baseline con `./gradlew detektBaseline` e inclúyelo en la
  tarea. Su diff solo puede cambiar entradas de las funciones que la tarea toca.

**Textos (los fija la spec, literales)**
- `meals_review_badge_pantry`: «En la despensa».
- `meals_review_badge_listed`: «En la lista: %1$s».
- `meals_review_badge_listed_plain`: «En la lista».
- `meals_review_badge_purchased`: «Ya comprado».
- `meals_review_skip`: pasa de «Omitir» a «No añadir».
- `meals_review_skip_description`: «No añadir a la lista» (nombre para TalkBack).
- `meals_review_recipe` («Receta: %1$s»), `meals_review_multi` («De varias comidas: %1$s»), `meals_review_add`,
  `meals_review_sum` y `meals_review_relist` no cambian.

**Comportamiento que no cambia:**
- qué sale marcado de entrada (`initialState`);
- la otra opción del selector (`includeLabel`);
- lo que se escribe (`ingredientReviewWrites`);
- el aviso «No hace falta añadir nada» si todo queda en «No añadir».

**Estilo:**
- comentarios y KDoc en español, con la densidad del fichero;
- nombres de test en inglés, entre comillas invertidas en JVM y en camelCase en los instrumentados, como en sus
  ficheros.

## Review Focus

1. **Alternar «No añadir» y volver a «Añadir»** en una fila nueva con marca y tienda elegidas y cantidad editada: la
   fila recupera todo lo que tenía. Lo prueba la Task 1 (`a new row starts in Añadir and can be left out`).
2. **Las etiquetas no se pulsan:** TalkBack lee su texto y no las anuncia como botones. Lo prueba la Task 2
   (`aRowInThePantryAndInTheListShowsBothBadges`, `assertHasNoClickAction`).
3. **Una cantidad larga en la lista** («En la lista: 500 g + 2 ud + 1 kg») en la revisión más estrecha (232 dp) y
   con la letra al 130 %: la etiqueta salta de línea y no se corta. Lo prueba la Task 2
   (`badgesAndPickerAreNotCutAtLargeFontInTheNarrowestReview`).
4. **Si no se pudo leer la despensa** (`pantryIds` vacío), no sale la etiqueta de la despensa, y la de la lista sí,
   si toca. Lo prueba la Task 1 (`every row shows the pantry and the list badges it has, pantry first`, fila
   «Pimienta»).
5. **Todo en «No añadir»**, también las filas nuevas: no se escribe nada (`writesNothing`). Lo prueba la Task 1
   (`a new row starts in Añadir and can be left out`).

---

### Task 1: Avisos de despensa y lista por fila, y pista solo con la receta

`Depende de: ninguna`

`Frontend: sin skill de diseño (el plan fija el diseño)`

**Files:**
- Modify: `J/feature/common/IngredientReview.kt`: los tipos y funciones de abajo; `hint()` nuevo; borra `pendingHint()`.
- Modify: `strings.xml`. Añade `meals_review_badge_pantry`, `meals_review_badge_listed`,
  `meals_review_badge_listed_plain` y `meals_review_badge_purchased`, y cambia `meals_review_skip` a «No añadir». Borra
  los que quedan sin uso: `meals_review_existing`, `meals_review_existing_listed`, `meals_review_existing_recipe`,
  `meals_review_existing_plain`, `meals_review_purchased`, `meals_review_purchased_recipe`, `meals_review_in_pantry` y
  `meals_review_in_pantry_recipe`.
- Test: `T/feature/common/IngredientReviewTest.kt`, `T/feature/common/IngredientReviewStringsTest.kt`.

**Interfaces:**
- Consumes: `IngredientReviewRow` (`existing`, `inPantry`, `recipeQuantity`, `quantities`), `ReviewHint`, `ReviewRowState`,
  `initialState()`, `withInclude()`, `toChoice()`, `writesNothing()`, `ingredientReviewWrites()`, todo en
  `IngredientReview.kt`.
- Produces (en `J/feature/common/IngredientReview.kt`):
  - `internal enum class ReviewBadgeSource { PANTRY, SHOPPING }`.
  - `internal data class ReviewBadge(val source: ReviewBadgeSource, @StringRes val res: Int, val args: List<String> = emptyList())`.
  - `internal fun IngredientReviewRow.badges(): List<ReviewBadge>`, con este orden y estas reglas:
    1. si `inPantry`: `ReviewBadge(PANTRY, R.string.meals_review_badge_pantry)`;
    2. si `existing` no es null:
       - comprado: `ReviewBadge(SHOPPING, R.string.meals_review_badge_purchased)`;
       - pendiente con cantidad (recortada, no vacía): `ReviewBadge(SHOPPING, R.string.meals_review_badge_listed, listOf(cantidad))`;
       - pendiente sin cantidad: `ReviewBadge(SHOPPING, R.string.meals_review_badge_listed_plain)`.
  - `internal fun IngredientReviewRow.hint(): ReviewHint?`, igual para todos los `kind`:
    - con 2 o más `quantities`: `ReviewHint(R.string.meals_review_multi, listOf(quantities.joinToString(" + ")))`;
    - si no, con `recipeQuantity`: `ReviewHint(R.string.meals_review_recipe, listOf(recipeQuantity))`;
    - si no, `null`.

- [ ] **Paso 1: tests nuevos en `IngredientReviewTest`.**
  - Sustituyen a `pending hints name only the quantities there are`, `purchased and new hints` y `pantry rows hint the
    pantry and offer Añadir`.
  - Las aserciones de `includeLabel` de ese último pasan al test 3.
  - Usa los helpers del fichero (`item`, `data`, `pantryId`).
  1. `every row shows the pantry and the list badges it has, pantry first`. Una sola llamada a
     `buildIngredientReviewRows`:
     - ingredientes: `Sal "200" g`, `Leche "1" l`, `Arroz`, `Pimienta`, `Tomate`;
     - lista: `item("s1", "Sal", quantity = "1 kg")`, `item("s2", "Leche", purchased = true)` y `item("s3", "Pimienta")`;
     - despensa: `setOf(pantryId("Sal"), pantryId("Leche"), pantryId("Arroz"))`.

     Comprueba `rows.map { it.badges() }`:
     - Sal: `[PANTRY badge_pantry, SHOPPING badge_listed ["1 kg"]]`;
     - Leche: `[PANTRY badge_pantry, SHOPPING badge_purchased]`;
     - Arroz: `[PANTRY badge_pantry]`;
     - Pimienta: `[SHOPPING badge_listed_plain]`;
     - Tomate: `[]`.
  2. `the hint only carries what the recipe asks for`:
     - la fila Sal del test 1 da `ReviewHint(meals_review_recipe, ["200 g"])`;
     - Leche comprada con `Ingredient("Leche")` da `null`;
     - Sal solo en la despensa con `Sal "1" kg` da `ReviewHint(meals_review_recipe, ["1 kg"])`;
     - `Sal "200" g` + `Sal "1" ud`, pendiente en la lista, da `ReviewHint(meals_review_multi, ["200 g + 1 ud"])`;
     - la misma pareja sin lista da lo mismo.
  3. `a new row starts in Añadir and can be left out`. Para la fila nueva de `Arroz "300" g`, con el contexto de `data`:
     - `initialState().include` es `true` y `includeLabel()` es `meals_review_add`;
     - `withInclude(false).toChoice(row).skip` es `true`, `listOf(esa elección).writesNothing()` es `true` y
       `ingredientReviewWrites` devuelve `[]`;
     - un estado con la cantidad cambiada a `"1 kg"` y otra tienda (`withStore`), al pasar por `withInclude(false)` y
       luego `withInclude(true)`, vuelve igual (`assertEquals`);
     - una fila solo en la despensa ofrece `meals_review_add` y una pendiente, `meals_review_sum`.
- [ ] **Paso 2: tests nuevos en `IngredientReviewStringsTest`.**
  - `badge and skip texts are the ones the spec fixes`: los cinco textos de «Textos» de las restricciones globales, sin
    `meals_review_skip_description` (lo añade la Task 2). En el XML, `%1$s` se escribe tal cual.
  - `the old combined hints are gone`: ninguno de los 8 nombres borrados aparece como `<string name="…">` en el XML.
- [ ] **Paso 3: RED.** `./gradlew :app:testDebugUnitTest --tests "com.dlunaunizar.bobitos.feature.common.IngredientReview*"`
  no compila: `badges` y `ReviewBadge` no existen.
- [ ] **Paso 4:** implementa en `IngredientReview.kt` lo de «Produces» y cambia `strings.xml`.
  - `ReviewRowContent` sigue pintando `row.hint()`: no lo toques en esta tarea.
  - Actualiza los KDoc de `IngredientReviewRow` y `ReviewHint` si nombran lo borrado.
- [ ] **Paso 5: GREEN.** El mismo comando pasa. Después pasa la puerta.

### Task 2: Etiquetas con icono y «No añadir» en todas las filas

`Depende de: Task 1`

`Frontend: sin skill de diseño (el plan fija el diseño)`

**Files:**
- Modify: `J/core/designsystem/component/InfoChip.kt`: parámetro nuevo `containerColor: Color = contentColor.copy(alpha = TONAL_ALPHA)`,
  después de `contentColor`, que va al `color` del `Surface`. Los usos de hoy no cambian.
- Modify: `J/feature/common/IngredientReview.kt`: `ReviewBadgeSource.chipColors`, `ReviewRowContent` y `SkipOrIncludePicker`.
- Modify: `strings.xml`: añade `meals_review_skip_description` («No añadir a la lista»).
- Create: `T/feature/common/ReviewBadgeColorsTest.kt`.
- Test: `T/feature/common/IngredientReviewStringsTest.kt`, `A/feature/common/IngredientReviewUiTest.kt`.

**Interfaces:**
- Consumes: `ReviewBadgeSource`, `ReviewBadge`, `IngredientReviewRow.badges()` (Task 1); `ModulePalette.colors(AppModule, dark)`
  y `LocalDarkTheme` (`J/core/designsystem/theme/ModuleColors.kt`); `contrast()` (`T/core/designsystem/theme/ContrastMath.kt`,
  `internal`); `surfaceContainerHighLight` y `surfaceContainerHighDark` (`J/core/designsystem/theme/Color.kt`, `internal`).
- Produces: `internal fun ReviewBadgeSource.chipColors(dark: Boolean): Pair<Color, Color>` (fondo, texto). Devuelve
  `container` y `onContainer` de `ModulePalette.colors(AppModule.PANTRY, dark)` o de `ModulePalette.colors(AppModule.SHOPPING, dark)`.
  La spec explica por qué no es el acento: no llega a 4,5:1 en claro.

- [ ] **Paso 1: `ReviewBadgeColorsTest`.**
  - `badge text reaches 4_5 to 1 on its chip in both themes`: para cada `ReviewBadgeSource` y `dark` en `false` y
    `true`, `contrast(texto, fondo) >= 4.5`, con el par de `chipColors(dark)`.
  - Comprueba también que el fondo no es transparente (`alpha == 1f`): el chip ya no lleva el tinte del 12 %.
- [ ] **Paso 2:** en `IngredientReviewStringsTest`, `the skip option is read as No añadir a la lista`:
  `meals_review_skip_description` es «No añadir a la lista».
- [ ] **Paso 3: tests nuevos en `IngredientReviewUiTest`.**
  - Para que el estado cambie al pulsar, pinta `ReviewRowContent` con un estado propio:
    `var state by remember { mutableStateOf(row.initialState()) }` y `update = { change -> state = change(state) }`.
  - Construye los `ShoppingItem` con todos sus campos, como el helper `item` de `IngredientReviewTest`.
  - Los textos se leen con `compose.activity.getString(...)`.
  1. `aNewRowOffersNoAnadirAndHidesItsFieldsWhenChosen`, con la fila `row` de hoy:
     - el nodo con descripción «No añadir a la lista» (`onNodeWithContentDescription`) existe y no está seleccionado;
     - el segmento «Añadir» está seleccionado y el campo «Cantidad» (`R.string.recipes_ingredient_quantity`) se ve;
     - al pulsar «No añadir a la lista», ese nodo pasa a seleccionado (`assertIsSelected`) y el campo «Cantidad» ya no
       existe (`assertDoesNotExist`).
  2. `aRowInThePantryAndInTheListShowsBothBadges`, con una fila pendiente con cantidad `"1 kg"` e `inPantry = true`:
     - «En la despensa» y «En la lista: 1 kg» se ven;
     - las dos etiquetas no tienen acción de clic (`assertHasNoClickAction()`);
     - «No añadir a la lista» está seleccionado y existe el segmento «Sumar».
  3. `badgesAndPickerAreNotCutAtLargeFontInTheNarrowestReview`:
     - en la `Box` de 232 dp, envuelve la fila en
       `CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f))`;
     - la fila: comprada, en la despensa y con `quantities = listOf("500 g", "2 ud", "1 kg")`;
     - en una segunda fila pendiente con cantidad `"500 g + 2 ud + 1 kg"`, la etiqueta «En la lista: 500 g + 2 ud + 1 kg»;
     - para «En la despensa», «Ya comprado», «No añadir», «Volver a apuntar» y la etiqueta larga, el `TextLayoutResult`
       (helper como `lineCount`, devolviendo el layout) tiene `hasVisualOverflow == false`.
  - El test de hoy (`brandAndStoreFitOnOneLineInTheNarrowestReview`) se queda tal cual.
- [ ] **Paso 4: RED.**
  - `./gradlew :app:testDebugUnitTest --tests "com.dlunaunizar.bobitos.feature.common.*"` no compila: `chipColors` no
    existe.
  - `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.dlunaunizar.bobitos.feature.common.IngredientReviewUiTest`
    falla en los tests 1 y 2.
- [ ] **Paso 5: implementa.**
  - **Orden de cada fila** (`ReviewRowContent`): nombre → etiquetas → `row.hint()` → selector → cantidad, marca y tienda
    (estos tres, solo con `state.include`, como hoy).
  - **Etiquetas:**
    - van en un `FlowRow` con `horizontalArrangement = Arrangement.spacedBy(Spacing.sm)` y
      `verticalArrangement = Arrangement.spacedBy(Spacing.xs)`, y solo si `badges()` no está vacío;
    - cada una es un `InfoChip` con `chipColors(LocalDarkTheme.current)` como `containerColor` y `contentColor`;
    - su icono es decorativo (`contentDescription = null`): `Icons.Rounded.Inventory2` en PANTRY y
      `Icons.Rounded.ShoppingCart` en SHOPPING, de 18 dp;
    - el texto, con `stringResource(badge.res, *badge.args.toTypedArray())`.
  - **Selector:** `SkipOrIncludePicker` en todas las filas: quita la condición `row.kind != ReviewRowKind.NEW`.
  - **TalkBack:** el segmento de «No añadir» lleva
    `Modifier.semantics { contentDescription = <meals_review_skip_description> }`. El texto visible sigue siendo
    `meals_review_skip`.
  - **Comentarios:** actualiza los que hablen de «Omitir» o de que las filas nuevas no tienen selector.
- [ ] **Paso 6: GREEN.** Los dos comandos del paso 4 pasan. Después pasa la puerta, junto con el instrumentado de
  `IngredientReviewUiTest`.
