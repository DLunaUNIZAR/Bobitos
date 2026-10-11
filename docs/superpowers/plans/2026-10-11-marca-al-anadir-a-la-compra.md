# Marca al añadir a la compra (plan 3 de 3) — Plan de implementación

> **Para agentes:** se ejecuta con `delegating-plan-execution`. Es un **plan grande** (4 tareas), así que lleva los pasos 1 a 6, incluido `simplify`. Cada tarea la hace un subagente `sonnet`, en serie. La revisión final la hace `opus` (si falla, `fable`). No toca reglas ni datos privados ajenos: no hay re-revisión salvo que un arreglo los toque. Los pasos usan casillas (`- [ ]`).

**Objetivo:** al añadir un ingrediente a la compra se elige **marca** (tus marcas o «Indiferente») y **tienda**, con los valores por defecto de la spec, en tres sitios:
- «Añadir» en la Compra;
- el diálogo de revisión de recetas y comidas;
- el botón «Añadir a la compra» de la ficha del ingrediente.

**Arquitectura:**
- **Lógica pura** del selector en `feature/common/BrandStoreSelection.kt`: qué ingrediente corresponde a un nombre (por `ingredientKey`), opciones de marca y tienda, valores por defecto y cambio de marca. Es lo que se prueba en la JVM.
- **Fuente de datos compartida** `IngredientShoppingSource`: combina catálogo común, personales, marcas y «Dónde lo compro» en un solo flujo, para la Compra, Comidas y Recetas.
- **Componente** `BrandStorePicker` (dos desplegables en una fila; marca en texto libre si no tienes marcas de ese ingrediente).
- **La ficha** abre la Compra del espacio activo con el nombre relleno; el selector de la Compra pone la marca y la tienda por defecto. Así hay un solo camino para los valores por defecto.

**Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Navigation Compose, Firestore (sin cambios de esquema ni de reglas).

**Spec:** `docs/superpowers/specs/2026-10-11-catalogo-ingredientes-design.md`, sección 5 («Compra») y, de la sección 4, «Añadir a la compra» de la ficha.
- La retirada de las preferencias antiguas de la Compra (fila «Sugerido», «Guardar como preferencia», ajuste automático) **ya se hizo en el plan 2** (ruling aprobado por el usuario). «Crear ficha de ingrediente» ya crea un personal.
- La Compra no cambia de esquema: la marca elegida se guarda como texto en `brand` (60 como máximo) y «Indiferente» se guarda como `null`; la tienda va en `supermarket`.

## Rulings de planificación

1. **La ficha pasa solo el nombre.** «Añadir a la compra» abre el formulario de la Compra con el nombre del ingrediente; la marca y la tienda por defecto las pone el selector de la Compra cuando reconoce el ingrediente (las mismas reglas que pide la spec para la ficha). Así no hay dos cálculos de los valores por defecto. Coste si mal: si la Compra tarda en cargar marcas y tiendas, el formulario se ve un instante sin ellas.
2. **El nombre viaja por el `SavedStateHandle` de la entrada de la Compra**, no por un argumento de ruta: la ruta `"shopping"` se compara como cadena en varios sitios (`workspaceTabFor`, `protectedRoutes`, `RealtimeScopes`) y cambiarla tiene más riesgo.
3. **En el diálogo de revisión, una fila nueva sin ingrediente reconocido** (ningún ingrediente con esa clave) no lleva selector y se añade sin tienda ni marca. Antes usaba «Dónde lo compro» por slug exacto; ahora el ingrediente se reconoce por clave tolerante y sus tiendas salen de su entrada.
4. **El estado del diálogo de revisión** sigue con `remember` (se pierde al girar, como hoy); no entra en este plan.

## Restricciones globales

- **Rutas:** `APP` = `app/src/main/java/com/dlunaunizar/bobitos/`; `UT` = `app/src/test/java/com/dlunaunizar/bobitos/`; `RES` = `app/src/main/res/values/strings.xml`.
- **GRADLE:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:assembleDebug :app:ktlintCheck :app:detekt :app:testDebugUnitTest`
- **Prohibido:** nada contra `bobitos-dev` (`--project dev`), `npm run catalog:fetch`, desplegar, distribuir betas, push.
- **Tests:** JVM puros (JUnit 4 + `kotlinx-coroutines-test`), dobles a mano; sin mockk ni Robolectric. Los dobles compartidos de ingredientes están en `UT/feature/ingredients/IngredientFakes.kt` (`FakeIngredientRepository`, `FakePersonalIngredientRepository`, `FakeIngredientPrefsRepository`, `FakeIngredientBrandRepository`). **Orden TDD obligatorio:** el test primero, verlo en rojo (y copiar el fallo en el informe), después el código.
- **detekt:** extraer código antes que tocar el baseline. **ktlint:** sin imports sin usar.
- **Textos** en español de España, en `RES`.
- **Tiendas:** enum `Supermarket` (`APP/core/model/ShoppingItem.kt`: `DIA, EROSKI, MERCADONA, CARREFOUR, ALCAMPO, OTROS, INDIFERENTE`). Etiqueta e icono: `Supermarket.labelRes` (`APP/feature/shopping/SupermarketTokens.kt`) y `SupermarketIcon` (`APP/feature/shopping/ShoppingScreen.kt`). En las tiendas de marcas y de «Dónde lo compro» nunca hay `INDIFERENTE`; en la tienda de un artículo de la Compra sí puede haberlo (como hoy).
- **Marca:** 60 caracteres como máximo (`FirestoreShoppingRepository.MAX_BRAND_LENGTH`, reglas `firestore.rules`). «Indiferente» es una opción de **marca** que se guarda como `brand = null`; no confundir con la tienda `INDIFERENTE`.
- **Piezas existentes que se reutilizan:** `findByKey`, `slug`, `ingredientKey` (`APP/core/model/CatalogIngredient.kt`); `mergeIngredients` (`APP/feature/ingredients/IngredientListLogic.kt`); `sortedForBrands()` (`APP/data/repository/IngredientBrandFirestore.kt`: favoritas primero y después por nombre con `Collator` español); `SupermarketDropdown` (`APP/feature/shopping/ShoppingScreen.kt:~762`); `BobitosFormSheet`.

## Review Focus

1. **Reconocimiento del nombre:** «Tomates», «tomate» o «TOMATE» en «Añadir» muestran el selector de «Tomate»; un nombre que no es de ningún ingrediente deja el formulario de siempre; pasar de uno a otro mientras se escribe no deja una marca de otro ingrediente guardada.
2. **Valores por defecto:** una sola favorita → esa marca y su primera tienda (si no tiene tiendas, la primera del ingrediente); dos favoritas o ninguna → «Indiferente» y la primera tienda del ingrediente, o ninguna; si las marcas llegan después de abrir el formulario, se aplican sin pisar lo que el usuario ya eligió en el selector.
3. **Diálogo de revisión:** las filas que ya están en la lista conservan su marca y su tienda; las filas nuevas sin ingrediente reconocido no llevan selector y se añaden sin tienda ni marca; cada fila guarda su propia elección.
4. **Ficha → Compra:** el botón no aparece sin espacio activo; el formulario se abre una sola vez (volver a la Compra más tarde no lo reabre); sin permiso de escritura no se abre; cerrar sin tocar nada no pregunta «¿Descartar los cambios?».
5. **Guardado:** la marca elegida se guarda como su nombre (≤60) y «Indiferente» como `null`; el aviso de duplicados y la validación de la Compra siguen funcionando con el selector.

---

### Task 1: Lógica del selector y fuente de datos compartida

**Ficheros:**
- Crear:
  - `APP/feature/common/BrandStoreSelection.kt` (puro);
  - `APP/feature/common/IngredientShoppingSource.kt`;
  - `UT/feature/common/BrandStoreSelectionTest.kt`;
  - `UT/feature/common/IngredientShoppingSourceTest.kt`.

**Interfaces:**
- **Consume:** `findByKey(ingredients, name)`, `mergeIngredients(common, personal)`, `List<IngredientBrand>.sortedForBrands()`, `IngredientRepository.catalog()`, `PersonalIngredientRepository.ingredients()`, `IngredientBrandRepository.brands()`, `IngredientPrefsRepository.prefs()` (mapa `ingredientId → IngredientPref(stores)`).
- **Produce** (paquete `com.dlunaunizar.bobitos.feature.common`):

```kotlin
/** Marca elegida (null = «Indiferente») y tienda elegida (null = sin especificar). */
@Parcelize
internal data class BrandStoreSelection(val brandId: String?, val store: Supermarket?) : Parcelable

/** Todo lo necesario para reconocer ingredientes y proponer marca y tienda. */
data class IngredientShoppingData(
    val ingredients: List<CatalogIngredient> = emptyList(), // comunes + personales, con mergeIngredients
    val brands: List<IngredientBrand> = emptyList(),        // todas las del usuario
    val prefs: Map<String, IngredientPref> = emptyMap(),    // «Dónde lo compro» por id
)

/** El ingrediente reconocido y sus opciones. Público porque viaja en `IngredientReviewRow` (pública). */
data class IngredientShoppingContext(
    val ingredient: CatalogIngredient,
    val brands: List<IngredientBrand>,   // solo las de este ingrediente, con sortedForBrands()
    val stores: List<Supermarket>,       // prefs[ingredient.id]?.stores, en orden
)

internal fun IngredientShoppingData.contextFor(name: String): IngredientShoppingContext?
internal fun IngredientShoppingContext.defaultSelection(): BrandStoreSelection
internal fun IngredientShoppingContext.selectBrand(current: BrandStoreSelection, brandId: String?): BrandStoreSelection
internal fun IngredientShoppingContext.storeOptions(selection: BrandStoreSelection): List<Supermarket>
internal fun IngredientShoppingContext.brandName(selection: BrandStoreSelection): String?

class IngredientShoppingSource @Inject constructor(
    private val ingredientRepository: IngredientRepository,
    private val personalIngredients: PersonalIngredientRepository,
    private val brandRepository: IngredientBrandRepository,
    private val prefsRepository: IngredientPrefsRepository,
) {
    fun data(): Flow<IngredientShoppingData>
}
```

- **Reglas** (spec §5):
  - `contextFor(name)`: `findByKey(ingredients, name)`; si no hay, `null`. Si hay: `brands.filter { it.ingredientId == ingredient.id }.sortedForBrands()` y `prefs[ingredient.id]?.stores.orEmpty()`.
  - `defaultSelection()`: `val favorite = brands.filter { it.favorite }.singleOrNull()`; marca = `favorite?.id`; tienda = `favorite?.stores?.firstOrNull() ?: stores.firstOrNull()`. Con dos favoritas o ninguna, marca `null` («Indiferente»).
  - `selectBrand(current, brandId)`: la nueva marca es la de ese id (o ninguna si `brandId` es `null` o no existe). Tienda = primera de la nueva marca; si la nueva no tiene tiendas: si la tienda actual venía de la marca anterior (está en sus `stores`), pasa a `stores.firstOrNull()`; si no, se mantiene la actual.
  - `storeOptions(selection)`: las tiendas de la marca elegida, después las del ingrediente y después el resto de `Supermarket.entries` (en el orden del enum, con `INDIFERENTE` al final), sin repetidos.
  - `brandName(selection)`: el nombre de la marca elegida, o `null` si es «Indiferente» o el id ya no existe.
  - `IngredientShoppingSource.data()`: `combine` de los cuatro flujos; cada uno con `.catch { emit(vacío) }` para que un fallo de uno no deje al resto sin datos; `ingredients = mergeIngredients(common, personal)`.

- [ ] **Paso 1: tests en rojo.**
  - `BrandStoreSelectionTest` (con datos construidos a mano: ingrediente común «Tomate», `id = "tomate"`, marcas `IngredientBrand(id, "tomate", name, favorite, stores)`):
    - `a name matching by key finds the ingredient and only its brands, favorites first`: «Tomates» → contexto de `tomate`, sin marcas de otros ingredientes, la favorita primero.
    - `a name matching nothing has no context`, y `a blank name has no context`.
    - `a single favorite is the default with its first store`.
    - `a favorite without stores falls back to the ingredient first store`.
    - `two favorites or none default to indifferent and the ingredient first store`.
    - `no brands and no stores default to nothing`.
    - `changing brand takes the new brand first store`.
    - `changing to a brand without stores resets a store that came from the previous brand`.
    - `changing to a brand without stores keeps a store the user chose elsewhere`.
    - `changing to indifferent behaves like a brand without stores`.
    - `store options list brand stores, then ingredient stores, then the rest without repeats`.
    - `brand name is null for indifferent and for an unknown id`.
  - `IngredientShoppingSourceTest` (con los dobles de `IngredientFakes.kt`):
    - `data merges common and personal and carries brands and prefs`.
    - `a failing source still delivers the others` (p. ej. `FakeIngredientRepository.failCatalog = true` → `ingredients` solo con los personales y marcas y prefs presentes).
- [ ] **Paso 2:** GRADLE. Esperado: FALLA (referencias sin resolver).
- [ ] **Paso 3:** implementar según la interfaz, con KDoc en español que cite la spec (§5).
- [ ] **Paso 4:** GRADLE. Esperado: verde.
- [ ] **Paso 5:** commit `feat(compra): lógica del selector de marca y tienda y fuente de datos de ingredientes`.

### Task 2: Componente `BrandStorePicker` y «Añadir» en la Compra

**Ficheros:**
- Crear: `APP/feature/common/BrandStorePicker.kt`.
- Modificar:
  - `APP/feature/shopping/ShoppingDraft.kt` (campos y funciones puras nuevas);
  - `APP/feature/shopping/ShoppingScreen.kt` (editor, `SupermarketDropdown` con opciones, comentario desfasado de `:~99` «Catálogo + preferencias…»);
  - `APP/feature/shopping/ShoppingViewModel.kt` y `ShoppingUiState.kt`;
  - `UT/feature/shopping/ShoppingDraftTest.kt` y `ShoppingViewModelTest.kt`;
  - `RES`.

**Interfaces:**
- **Consume (Task 1):** `IngredientShoppingSource`, `IngredientShoppingData`, `IngredientShoppingContext`, `contextFor`, `defaultSelection`, `selectBrand`, `storeOptions`, `brandName`, `BrandStoreSelection`.
- **Produce:**

```kotlin
// feature/common/BrandStorePicker.kt
@Composable
internal fun BrandStorePicker(
    context: IngredientShoppingContext,
    selection: BrandStoreSelection,
    freeBrand: String,                       // solo se usa si context.brands está vacío
    onBrandSelected: (String?) -> Unit,      // id de marca, o null = «Indiferente»
    onStoreSelected: (Supermarket?) -> Unit,
    onFreeBrandChange: (String) -> Unit,
    modifier: Modifier = Modifier,
)

// feature/shopping/ShoppingScreen.kt (se amplía, mismo comportamiento por defecto)
internal fun SupermarketDropdown(
    selected: Supermarket?,
    onSelect: (Supermarket?) -> Unit,
    modifier: Modifier = Modifier,
    options: List<Supermarket> = Supermarket.entries,
)

// feature/shopping/ShoppingDraft.kt
internal data class ShoppingDraft(
    val name: String, val quantity: String, val notes: String,
    val supermarket: Supermarket?, val brand: String,
    val brandId: String? = null,      // marca elegida en el selector (null = «Indiferente» o sin selector)
    val pickerFor: String? = null,    // id del ingrediente cuyos valores por defecto ya se aplicaron
) : Parcelable
internal fun ShoppingDraft.withContext(context: IngredientShoppingContext?): ShoppingDraft
internal fun ShoppingDraft.withBrandChoice(context: IngredientShoppingContext, brandId: String?): ShoppingDraft
```

- **Reglas de `ShoppingDraft`:**
  - `withContext(null)`: si `pickerFor != null`, `copy(pickerFor = null, brandId = null)` (conserva `brand` y `supermarket` como texto y tienda normales); si no, `this`.
  - `withContext(context)` con `context.ingredient.id == pickerFor`: `this` (no pisa lo elegido).
  - `withContext(context)` con otro ingrediente: aplica `defaultSelection()`: `pickerFor = id`, `brandId = sel.brandId`, `supermarket = sel.store`, `brand = if (context.brands.isEmpty()) brand else context.brandName(sel).orEmpty()`.
  - `withBrandChoice(context, brandId)`: `selectBrand(BrandStoreSelection(this.brandId, supermarket), brandId)` → `brandId`, `supermarket` y `brand = context.brandName(sel).orEmpty()`.
- **`BrandStorePicker`:** una fila (`Row`) con dos desplegables de igual ancho, «Marca» y «Tienda» (patrón del proyecto: `Box` + `OutlinedButton` a todo el ancho + `DropdownMenu`, como `SupermarketDropdown`; no hay `ExposedDropdownMenuBox` en el proyecto).
  - Marca: las de `context.brands` (las favoritas con «★ » delante) y, al final, «Indiferente» (`shopping_brand_indifferent`). Muestra el nombre elegido o «Indiferente».
  - Si `context.brands` está vacío: en lugar del desplegable de marca, un `OutlinedTextField` de una línea para la marca (`freeBrand`), etiqueta «Marca (opcional)» (reutiliza la etiqueta que ya use el campo de marca de la Compra).
  - Tienda: `SupermarketDropdown(selection.store, onStoreSelected, options = context.storeOptions(selection))`.
  - Descripciones accesibles en los botones de los desplegables.
- **ViewModel:** `ShoppingViewModel` recibe además `IngredientShoppingSource`. `startIngredientAssist()` recoge `source.data()` en `ShoppingUiState.ingredientData: IngredientShoppingData` (sustituye a `catalog`; las sugerencias de nombre usan `ingredientData.ingredients`). `stopIngredientAssist()` como hoy. `createIngredientFromItem` y `loadIngredientsOnDemand` no cambian.
- **Editor (`ShoppingItemEditor`), solo al crear (`item == null`):**
  - `val context = remember(draft.name, ingredientData) { ingredientData.contextFor(draft.name) }` y `LaunchedEffect(context?.ingredient?.id) { draft = draft.withContext(context) }`.
  - Con `context != null`: `BrandStorePicker(context, BrandStoreSelection(draft.brandId, draft.supermarket), draft.brand, onBrandSelected = { draft = draft.withBrandChoice(context, it) }, onStoreSelected = { draft = draft.copy(supermarket = it) }, onFreeBrandChange = { draft = draft.copy(brand = it) })` en lugar de `SupermarketAndBrandFields`.
  - Con `context == null`, o al editar un artículo existente: el formulario de hoy.
  - El guardado no cambia: `onSave(name, quantity, notes, draft.supermarket, draft.brand.trim().ifEmpty { null })`.
  - `dirty = { draft != initial.withContext(context) }`, para que los valores por defecto aplicados solos no cuenten como cambios.

- [ ] **Paso 1: tests en rojo.**
  - `ShoppingDraftTest`:
    - `recognizing an ingredient applies its default brand and store once`;
    - `the same ingredient again keeps the user choice` (tras `withBrandChoice`, `withContext` del mismo contexto no la pisa);
    - `leaving the ingredient keeps the text brand and store but drops the picker`;
    - `an ingredient without brands keeps the typed brand and takes its first store`;
    - `choosing indifferent stores no brand text` (`brand == ""`, `brandId == null`);
    - `choosing a brand writes its name as the brand text`.
  - `ShoppingViewModelTest` (construir el ViewModel con `IngredientShoppingSource(ingredientRepository, personalRepository, brandRepository, prefsRepository)` y los dobles de `IngredientFakes.kt`):
    - `ingredient assist exposes ingredients, brands and prefs`;
    - mantener en verde los tests existentes de sugerencias y de «Crear ficha de ingrediente» adaptados a `ingredientData`.
- [ ] **Paso 2:** GRADLE. Esperado: FALLA.
- [ ] **Paso 3:** implementar. Textos nuevos en `RES`: `shopping_brand_indifferent` («Indiferente»), `shopping_brand_label` («Marca», si no existe ya un texto equivalente), `shopping_store_label` («Tienda», ídem), y las descripciones accesibles de los dos desplegables.
- [ ] **Paso 4:** GRADLE. Esperado: verde.
- [ ] **Paso 5:** commit `feat(compra): selector de marca y tienda al añadir un ingrediente`.

### Task 3: Diálogo de revisión de recetas y comidas con selector

**Ficheros:**
- Modificar:
  - `APP/feature/common/IngredientReview.kt`;
  - `APP/feature/meals/MealsViewModel.kt` y `MealsScreen.kt`;
  - `APP/feature/recipes/RecipesViewModel.kt` y `RecipesScreen.kt`;
  - `UT/feature/meals/MealsViewModelTest.kt` y `UT/feature/recipes/RecipesViewModelTest.kt`.
- Crear: `UT/feature/common/IngredientReviewTest.kt`.

**Interfaces:**
- **Consume (Tasks 1 y 2):** `IngredientShoppingSource.data()`, `IngredientShoppingData.contextFor`, `defaultSelection`, `brandName`, `BrandStoreSelection`, `BrandStorePicker`.
- **Produce:**

```kotlin
data class IngredientReviewRow(
    val name: String, val unit: String?, val recipeQuantity: String?,
    val existing: ShoppingItem?, val quantities: List<String> = emptyList(),
    val context: IngredientShoppingContext? = null,   // solo en filas nuevas con ingrediente reconocido
)
fun buildIngredientReviewRows(
    ingredients: List<Ingredient>, currentItems: List<ShoppingItem>,
    data: IngredientShoppingData = IngredientShoppingData(),
): List<IngredientReviewRow>

/** Lo que el usuario confirma en cada fila. */
data class IngredientReviewChoice(val quantity: String?, val supermarket: Supermarket?, val brand: String?)

/** Estado editable de una fila en el diálogo. */
internal data class ReviewRowState(val quantity: String, val selection: BrandStoreSelection, val freeBrand: String)
internal fun IngredientReviewRow.initialState(): ReviewRowState
internal fun ReviewRowState.toChoice(row: IngredientReviewRow): IngredientReviewChoice

suspend fun applyIngredientReview(
    spaceId: String, rows: List<IngredientReviewRow>, choices: List<IngredientReviewChoice>,
    shoppingRepository: ShoppingRepository,
)

@Composable
fun IngredientReviewDialog(rows: List<IngredientReviewRow>, onConfirm: (List<IngredientReviewChoice>) -> Unit, onDismiss: () -> Unit)
```

- **Reglas:**
  - `buildIngredientReviewRows`: como hoy, y `context = if (existing == null) data.contextFor(name) else null`.
  - `initialState()`: cantidad como hoy (`existing?.quantity ?: recipeQuantity`); `selection = context?.defaultSelection() ?: BrandStoreSelection(null, null)`; `freeBrand = ""`.
  - `toChoice(row)`: cantidad recortada o `null`. Fila existente: `supermarket`/`brand` del artículo existente (no se usan al aplicar). Fila nueva con contexto: `supermarket = selection.store`; `brand = if (context.brands.isEmpty()) freeBrand.trim().ifEmpty { null } else context.brandName(selection)`. Fila nueva sin contexto: `supermarket = null`, `brand = null`.
  - `applyIngredientReview`: fila existente → `updateItem` cambiando solo la cantidad (como hoy, conserva notas, súper y marca); fila nueva → `addItem(spaceId, row.name, choice.quantity, row.unit, choice.supermarket, choice.brand)` (la unidad sigue yendo en las notas, como hoy).
  - **Diálogo:** estado `remember(rows) { rows.map { it.initialState() }.toMutableStateList() }`; bajo el campo de cantidad de cada fila con `context != null`, `BrandStorePicker` (al elegir marca: `context.selectBrand(...)`); confirmar con `rows.mapIndexed { i, row -> states[i].toChoice(row) }`.
  - **ViewModels:** `MealsViewModel` y `RecipesViewModel` sustituyen `IngredientPrefsRepository` por `IngredientShoppingSource`; guardan el último `IngredientShoppingData` recibido (como hoy guardan las preferencias: variable privada, observada en `observe`, con `catch` que deja vacío) y lo pasan a `buildIngredientReviewRows`. `confirmIngredientReview(choices: List<IngredientReviewChoice>)` y `confirmShoppingReview(spaceId, choices)` llaman a `applyIngredientReview` con las elecciones.

- [ ] **Paso 1: tests en rojo.**
  - `IngredientReviewTest`:
    - `a new row matching an ingredient by key gets its context; an existing row does not`;
    - `a new row without ingredient has no context and adds without store or brand`;
    - `the initial state uses the single favorite and its first store`;
    - `choices carry the picked brand name, indifferent as null, and a free brand when there are no brands`;
    - `apply keeps brand and store of rows already in the list and adds new rows with their choice` (con un doble de `ShoppingRepository` que registre `addItem` y `updateItem`).
  - `MealsViewModelTest`: sustituir `dumping ingredients uses the ingredient's first store and no brand` por `confirming the review adds each new row with its chosen brand and store` (construir el ViewModel con `IngredientShoppingSource` y los dobles compartidos; quitar el `FakeIngredientPrefsRepository` local, que choca de nombre con el compartido); hacer que el `FakeShoppingRepository` local registre también `updateItem`.
  - `RecipesViewModelTest`: igual (`confirming the review uses the chosen brand and store`); ampliar `RecipesFakeShoppingRepository` para registrar `addedBrands`.
- [ ] **Paso 2:** GRADLE. Esperado: FALLA.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** GRADLE. Esperado: verde.
- [ ] **Paso 5:** commit `feat(compra): marca y tienda por fila en la revisión de recetas y comidas`.

### Task 4: «Añadir a la compra» desde la ficha del ingrediente y documentación

**Ficheros:**
- Modificar:
  - `APP/feature/ingredients/IngredientDetailScreen.kt` (botón);
  - `APP/core/navigation/BobitosNavHost.kt` (navegación y paso del nombre);
  - `APP/feature/shopping/ShoppingScreen.kt` (abrir el formulario relleno una vez);
  - `APP/feature/shopping/ShoppingDraft.kt` (`prefilled`);
  - `UT/feature/shopping/ShoppingDraftTest.kt`;
  - `RES`;
  - `docs/INGREDIENT_CATALOG.md` («En la app»: el selector de marca y tienda en los tres sitios) y `docs/DATA_MODEL.md` («Lista de la compra»: `brand` es el nombre de la marca elegida o texto libre, «Indiferente» = `null`).

**Interfaces:**
- **Consume (Task 2):** `ShoppingDraft` con `withContext` (el editor aplica los valores por defecto al reconocer el nombre).
- **Produce:**

```kotlin
// feature/ingredients/IngredientDetailScreen.kt
fun IngredientDetailScreen(
    ingredientId: String, brandId: String?, onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToShopping: ((String) -> Unit)? = null,   // nombre del ingrediente; null = sin espacio activo → sin botón
    viewModel: IngredientDetailViewModel = hiltViewModel(),
)

// feature/shopping/ShoppingScreen.kt
fun ShoppingScreen(
    spaceId: String, canWrite: Boolean, modifier: Modifier = Modifier,
    prefillName: String? = null, onPrefillConsumed: () -> Unit = {},
    viewModel: ShoppingViewModel = hiltViewModel(),
)

// feature/shopping/ShoppingDraft.kt
internal fun ShoppingDraft.Companion.prefilled(name: String): ShoppingDraft   // nombre puesto; el resto vacío

// core/navigation (constante de clave)
internal const val SHOPPING_PREFILL_KEY = "shoppingPrefillName"
```

- **Reglas:**
  - **Ficha:** un botón «Añadir a la compra» (`ingredients_add_to_shopping`, con icono de carrito) bajo la tarjeta de datos del ingrediente, solo si `onAddToShopping != null` y el ingrediente ya está cargado; llama a `onAddToShopping(ingredient.name)`.
  - **NavHost:** en el destino `INGREDIENT_DETAIL_ROUTE`, `onAddToShopping = uiState.selectedSpace?.let { { name -> navController.openShoppingWithName(name) } }`. `openShoppingWithName` (extensión privada junto a `navigateToTab`/`navigateToWorkspace`) entra en la Compra del espacio activo siguiendo el mismo patrón que ya usa Mi calendario para entrar en un espacio desde una pantalla sin espacio (`BobitosNavHost.kt:~243-252`) y, después de navegar, hace `navController.currentBackStackEntry?.savedStateHandle?.set(SHOPPING_PREFILL_KEY, name)`.
  - En el destino de la Compra: `val prefill by entry.savedStateHandle.getStateFlow<String?>(SHOPPING_PREFILL_KEY, null).collectAsStateWithLifecycle()` y `ShoppingScreen(..., prefillName = prefill, onPrefillConsumed = { entry.savedStateHandle[SHOPPING_PREFILL_KEY] = null })`.
  - **ShoppingScreen:** `LaunchedEffect(prefillName)`: si no es `null`, y si `canWrite`, abre el editor de alta con `ShoppingDraft.prefilled(prefillName)` como borrador inicial (nuevo parámetro `initialDraft` de `ShoppingItemEditor`, guardado con `rememberSaveable` en la pantalla); en todo caso llama a `onPrefillConsumed()`, para que no se reabra al volver más tarde. El selector (Task 2) aplica la marca y la tienda por defecto cuando reconoce el nombre.

- [ ] **Paso 1: tests en rojo.**
  - `ShoppingDraftTest`:
    - `a prefilled draft has only the name`;
    - `a prefilled draft is not dirty once the ingredient defaults apply` (`ShoppingDraft.prefilled("Tomate").withContext(ctx) == initial.withContext(ctx)` con el `initial` prefijado).
- [ ] **Paso 2:** GRADLE. Esperado: FALLA.
- [ ] **Paso 3:** implementar el botón, la navegación, la apertura del formulario y la documentación.
- [ ] **Paso 4:** GRADLE. Esperado: verde.
- [ ] **Paso 5:** commit `feat(ingredientes): añadir a la compra desde la ficha con marca y tienda por defecto`.

---

## Ejecución (`delegating-plan-execution`, plan grande)

1. Rama `agent/marca-al-anadir-a-la-compra` desde `main`; commit de este plan.
2. Tareas 1 a 4, una tras otra, cada una con un `sonnet`; el coordinador hace `task-done` con GRADLE.
3. Revisión final con `opus`, más arreglos (re-revisión solo si un arreglo toca reglas, permisos o aislamiento).
4. `simplify`: un revisor `sonnet` con los 4 ángulos y un aplicador `sonnet` de la lista (a).
5. `code-review` en nivel `high`, más arreglos.
6. Cierre: backlog, rulings y la decisión sobre la rama, que es del usuario. Después, el despliegue único (cada paso confirmado por el usuario): reglas → borrado inicial (simulación → `--apply`) → importación (simulación → `--apply`) → beta 19.

## Verificación de punta a punta (coordinador, al final)

En el emulador Android con los emuladores de Firebase y el catálogo importado (`FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 node scripts/catalog/import-ingredients.mjs --project demo-bobitos --apply`), con un usuario que tenga un espacio:
1. En «Tomate», marcar Mercadona y DIA y crear dos marcas: «Valor» favorita con Mercadona y «Hacendado» sin tiendas.
2. **Compra → Añadir → «Tomates»:** sale el selector con «★ Valor» y Mercadona; cambiar a «Hacendado» → la tienda pasa a la primera del ingrediente (Mercadona); cambiar a «Indiferente» y guardar → el artículo sin marca.
3. **Un nombre que no es ingrediente** («Pilas»): el formulario de siempre.
4. **Receta con «Tomate» → Añadir a la Compra:** la fila nueva lleva el selector con los valores por defecto; una fila que ya está en la lista conserva su marca.
5. **Ficha de «Tomate» → «Añadir a la compra»:** abre la Compra con el formulario relleno («Tomate», «★ Valor», Mercadona); cerrarlo sin tocar nada no pregunta; volver a la Compra más tarde no lo reabre.
6. Marcar una segunda favorita: los valores por defecto pasan a «Indiferente» y Mercadona.
7. Sin espacio activo, la ficha no muestra el botón.
8. Capturas en tema oscuro (por defecto) y claro.
