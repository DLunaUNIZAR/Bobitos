# Menores de Recetas y Compra (beta 22) — Plan de implementación

> **Para agentes:** se ejecuta con `delegating-plan-execution`. Es un **plan de menores** (8 tareas): pasos 1, 2, 3, 5 y 6, **sin `simplify`**. Cada tarea la hace un subagente `sonnet`, en serie y en orden. La revisión final la hace `opus` (si falla, `fable`). Ninguna tarea toca `firestore.rules` ni `migrations/`. Los pasos usan casillas (`- [ ]`).

**Objetivo:** aplicar las dos decisiones de producto del plan A y cerrar los menores con efecto visible, sin tachar, de las secciones `agent/catalogo-recetas`, `agent/receta-pasos` y `agent/compra-sin-duplicados` de `docs/superpowers/backlog.md`. Los internos se quedan en el backlog.

**Arquitectura:**
- **Compra (Tareas 1 a 4):**
  - `ShoppingRepository.applyWrites` escribe una lista de `ShoppingWrite` (añadir o cambiar) en **una sola transacción**. Cada cambio comprueba antes que el artículo sigue como se vio (`ShoppingItem.sameContentAs`); si no, no escribe nada y falla con `ShoppingFailure.ItemChanged`. La usan la revisión de «Añadir a la compra» y el editor de la Compra. `relistItem` desaparece.
  - La revisión gana «Omitir / Volver a apuntar» en la fila «Ya comprado» y pistas sin «—».
  - El editor rellena huecos al «Sumar cantidad», avisa de una suma demasiado larga y no escribe con la lista sin cargar.
  - `ShoppingQuantity` concuerda en número las unidades con el plural del español.
- **Recetario (Tareas 5 a 8):**
  - «Deshacer» solo cuando el borrado se ha confirmado, y recrea la receta con su visibilidad.
  - Autor vacío en vez del uid.
  - Buscador por palabras y sin tildes.
  - Errores de campo independientes, TalkBack de los pasos y foco al quitar un ingrediente.
  - Importador web que parte bien el HTML.
  - Catálogo: «Salpimienta.» y un mensaje claro para `null`.

**Stack:** Kotlin + Jetpack Compose (Material 3) + Hilt + Firestore; Jsoup en el importador; Node 22 (`node --test`) en el catálogo.

**Spec:** `docs/superpowers/specs/2026-10-12-recetario-y-compra-design.md` (A3 y A4; B2 y B3 para el editor y el importador; C1 para el catálogo). Backlog: `docs/superpowers/backlog.md`, secciones `agent/catalogo-recetas`, `agent/receta-pasos` y `agent/compra-sin-duplicados`.

## Qué entra

| # | Menor o decisión (backlog) | Tarea |
|---|---|---|
| D1 | Decisión: la fila «Ya comprado» gana «Omitir», marcado por defecto | 2 |
| D2 | Decisión: «Sumar cantidad» rellena las notas, la tienda y la marca vacías del artículo | 3 |
| C1 | «Sumar» y «volver a apuntar» pisan los cambios de otro miembro | 1 |
| C2 | `applyIngredientReview` no es atómica | 1 |
| C3 | Con la lista sin cargar o sin poder leerla, no se buscan repetidos | 2 (Comidas y Recetario) y 3 (editor) |
| C4 | Suma de más de 40 caracteres: el error sale sobre un campo corto | 3 |
| C5 | «(cantidad: 5 / ud)» se parte en dos líneas | 3 |
| C6 | «Ya en la lista: — · receta: —» | 2 |
| C7 | Plural heurístico de unidades («packes», «nuezes», «limónes», «1 cucharadas») | 4 |
| R1 | «Deshacer» durante el borrado no hace nada | 5 |
| R2 | «Deshacer» se ofrece aunque el borrado falle | 5 |
| R3 | Receta antigua sin `createdByName`: el uid como autor | 5 |
| R4 | Deshacer una común sin `isAdmin`: se guarda como PRIVATE con el slug | 5 (parte b; ver ruling 2) |
| R5 | El buscador del Recetario distingue tildes y no va por palabras | 5 |
| R6 | Raciones o tiempo: el error solo se marca si es el primer fallo | 6 |
| R7 | «Subir/Bajar/Quitar paso» no dicen de qué paso son | 6 |
| R8 | Quitar un ingrediente con el teclado abierto deja el foco en esa posición | 6 |
| R9 | El importador no parte HTML escapado, listas de textos ni `HowToStep` con `<br>` o `\n` | 7 |
| R10 | Un `\n` del código fuente dentro de un `<p>` parte el párrafo | 7 |
| K1 | «Sala y pimienta.» en «Lasaña de carne» | 8 |
| K2 | `"quantity": null` da un mensaje confuso en la CLI | 8 |

Los menores **internos** de las tres secciones no entran.

## Rulings de planificación

1. **C1 y C2 se resuelven juntos con `applyWrites`.**
   - Una sola transacción: Firestore la aplica entera o no la aplica. Reintentar tras un fallo ya no suma dos veces.
   - Cada `Change` lleva el artículo tal como se vio (`seen`). Dentro de la transacción se compara su contenido visible (nombre, cantidad, notas, tienda, marca y si está comprado) con el del servidor. Si otro miembro lo ha cambiado o lo ha marcado, no se escribe nada y sale «ha cambiado»; si lo ha borrado, «no encontrado». Volver a intentarlo parte de la lista nueva.
   - Lo usan la revisión (todas sus filas en una transacción), «Sumar cantidad» y «volver a apuntar» del editor. «Editar» un artículo sigue con `updateItem`, sin comprobación (fuera de alcance).
   - `relistItem` deja de tener llamadas y se quita de la interfaz, de la implementación y de los dobles.
2. **R4, solo la parte (b).** «Deshacer» recrea la receta tal como era, también su visibilidad, sin pasar por `safeVisibility`. Si quien deshace no puede publicar comunes, las reglas lo rechazan y sale el error de permiso. Nunca se guarda como privada con el slug del catálogo. La parte (a) (`ownerUid` y `createdBy` del admin que deshace) solo se nota con un **segundo admin**, que hoy no existe, y arreglarla exige cambiar permisos en `firestore.rules`. **Se queda en el backlog** (ver «Cierre»).
3. **R6 se extiende al paso demasiado largo**, que tenía el mismo fallo (solo se marcaba si era el primero). Raciones, tiempo y cada paso largo se marcan siempre, con funciones puras de `RecipeDraft`.
4. **R5:** el Recetario usa la búsqueda del selector de Comidas (`prepareQuery`: por palabras, sin tildes ni mayúsculas) y sigue mirando también los ingredientes, como hasta ahora. El selector de Comidas no cambia (spec B5: título y categoría).
5. **C3:**
   - **Comidas y Recetario:** si no se puede leer la lista, no se abre la revisión y sale el error (de red, de permiso…).
   - **Editor de la Compra:** con la lista aún sin cargar (o con su lectura fallida), «Guardar» no escribe. Sale «Aún no se ha cargado la lista…» en el formulario, y el editor sigue abierto con lo tecleado.
   - `markCooked` conserva su caída a lista vacía: solo ofrece tachar y no duplica nada.
6. **C4:** el aviso «Ya en la lista» calcula la suma al abrirse. Si pasa de 40 caracteres, lo explica en el propio aviso y deshabilita «Sumar cantidad». Nunca sale un error sobre el campo del editor.
7. **C5:** en el aviso, la cantidad del artículo va con espacios que no parten línea (U+00A0), también tras «cantidad:». Si el artículo no tiene cantidad, el texto va sin el paréntesis (sin «—»).
8. **C6:** la pista de una fila pendiente nombra solo las cantidades que hay: «Ya en la lista: X · receta: Y», «Ya en la lista: X», «Ya en la lista · receta: Y» o «Ya en la lista».
9. **D1:** la fila comprada muestra la pista «Ya comprado» (o «Ya comprado · receta: Y») y el selector «Omitir / Volver a apuntar», con **Omitir** marcado. Con «Volver a apuntar» aparece el campo de cantidad con la de la receta (como hoy). Si todo queda en «Omitir», el aviso sigue siendo «Ya estaba todo en la lista».
10. **D2:**
    - «Sumar cantidad» rellena con lo tecleado las notas, la tienda y la marca que el artículo tiene vacías, y conserva las que tiene.
    - Unas notas o una marca solo con espacios cuentan como vacías.
    - La tienda «Indiferente» cuenta como elegida.
11. **C7, plural y singular aproximados del español:**
    - **Plural:**
      - acabada en vocal → «-s»;
      - «-z» → «-ces»;
      - l, r, n, d o j → «-es», y la aguda pierde la tilde («limón» → «limones»);
      - otra consonante (palabras de fuera, como «pack») → «-s».
    - **Singular de una grafía en plural:** «-ces» → «-z», «-ones» → «-ón»; si no, sin la «-s» final.
    - **Limitación aceptada:** palabras raras como unidad, como «flores» o «panes», no salen bien en singular.
12. **K1** solo llega a la app al reimportar las recetas en `bobitos-dev` (ver «Despliegue»).

## Restricciones globales

- **Rutas:**
  - `APP` = `app/src/main/java/com/dlunaunizar/bobitos/`
  - `UT` = `app/src/test/java/com/dlunaunizar/bobitos/`
  - `RES` = `app/src/main/res/values/strings.xml`
  - `T` = `tests/`
- **Comandos de verificación**, en llamadas separadas y sin `sh -c`:
  - **GRADLE:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:assembleDebug :app:ktlintCheck :app:detekt :app:testDebugUnitTest`
  - **JVM (una clase):** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:testDebugUnitTest --tests "<clase completa>"`
  - **NODE:** `npm run test:scripts`
  - Si ktlint marca formato: `./gradlew :app:ktlintFormat` (con el mismo `JAVA_HOME`) y revisar el diff antes del commit.
- **Prohibido:**
  - cualquier script contra `bobitos-dev` (`--project dev` o `bobitos-dev`);
  - desplegar, importar, distribuir betas o hacer push;
  - descargar nada;
  - tocar `firestore.rules`.
- **Commits con ficheros con nombre:** nunca `git add -A` ni `git add .`.
- **Textos y comentarios en español**, con el estilo de los ficheros vecinos. Textos de la app en `RES`.
- **Tests Kotlin:** JVM puros (JUnit 4 + `kotlinx-coroutines-test`), dobles a mano; sin mockk ni Robolectric. **Orden TDD obligatorio:** test primero, verlo en rojo (un fallo de compilación por lo que aún no existe también vale; copiar el fallo en el informe), después el código.
- **detekt:** `ReturnCount` máx. 4, `LongMethod` máx. 80 líneas, `TooManyFunctions` 25 (por clase y por fichero). Extraer código antes que tocar `config/detekt/baseline.xml`. **No cambies estas firmas, que están en el baseline:** `ShoppingScreen(...)`, `RecipesScreen(...)`, `FirestoreShoppingRepository.validateItem(...)`, `runShoppingOperation(...)`, `DocumentSnapshot.toShoppingItem()` y `FirestoreRecipeRepository.validate(title, description, category)`. No añadas `catch (Throwable)` nuevos.
- **ktlint:** sin imports sin usar; línea ≤120.
- **Tests existentes:** solo se cambian los que fijan el comportamiento que este plan sustituye. Cada tarea dice cuáles y cómo.

## Review Focus

1. **Otro miembro cambia, marca o borra el artículo** entre que se abre la revisión (o el aviso «Ya en la lista») y se confirma: no se escribe nada, sale «ha cambiado» (o «no encontrado») y volver a intentarlo parte de la lista nueva. → `ShoppingViewModelTest`, `MealsViewModelTest` y `RecipesViewModelTest` (Tarea 1); recorrido de punta a punta, paso 3.
2. **La revisión de una semana con filas nuevas, pendientes y compradas** va en una sola escritura, en el orden de las filas. Si falla, nada queda a medias, y reintentar no suma dos veces. → `IngredientReviewTest` (Tarea 1).
3. **Filas sin ninguna cantidad** (ingrediente sin cantidad y artículo sin cantidad, pendiente o comprado): la pista no muestra «—», y «Sumar» o «Volver a apuntar» escriben la cantidad vacía (`null`), nunca «—». → `IngredientReviewTest` (Tarea 2).
4. **Una receta cuyos ingredientes ya se compraron todos:** con las opciones por defecto no se escribe nada y sale «Ya estaba todo en la lista». → `MealsViewModelTest` (Tarea 2).
5. **Instrucciones de texto plano con entidades normales** («&amp;», «&quot;») y sin etiquetas: se parten por líneas como antes, sin perder texto ni dejar entidades a la vista. → `JsonLdRecipeImporterTest` (Tarea 7).

---

### Task 1: Compra: escrituras de la revisión y del editor en una sola transacción, con comprobación de cambios

**Ficheros:**
- Modificar:
  - `APP/core/model/ShoppingItem.kt`
  - `APP/data/repository/ShoppingRepository.kt`
  - `APP/data/repository/FirestoreShoppingRepository.kt`
  - `APP/feature/common/IngredientReview.kt` (`applyIngredientReview`, más una función pura nueva)
  - `APP/feature/shopping/ShoppingViewModel.kt` (`addItem`, `sumDuplicate` y el mapeo de fallos)
  - `APP/feature/shopping/ShoppingUiState.kt` (`ShoppingUiMessage.ItemChanged`)
  - `APP/feature/shopping/ShoppingScreen.kt` (`stringResourceId`)
  - `APP/feature/meals/MealsViewModel.kt` y `MealUiMessage.kt` (`ShoppingChanged`), `MealsScreen.kt` (`stringResourceId`)
  - `APP/feature/recipes/RecipesViewModel.kt` y `RecipeUiMessage.kt` (`ShoppingChanged`), `RecipesScreen.kt` (`stringResourceId`)
  - `RES`
- Tests:
  - crear `UT/core/model/ShoppingItemTest.kt`;
  - modificar `UT/feature/common/RecordingShoppingRepository.kt`, `UT/feature/common/IngredientReviewTest.kt`, `UT/feature/shopping/ShoppingViewModelTest.kt`, `UT/feature/meals/MealsViewModelTest.kt` y `UT/feature/recipes/RecipesViewModelTest.kt`.

**Interfaces:**
- Consume: `findShoppingMatch`, `ShoppingQuantity.sum`, `buildIngredientReviewRows`, `IngredientReviewChoice`, `toShoppingItem()` (privada de `FirestoreShoppingRepository.kt`).
- Produce (las Tareas 2 y 3 lo usan):
  - `fun ShoppingItem.sameContentAs(other: ShoppingItem): Boolean` (`core/model/ShoppingItem.kt`).
  - `sealed interface ShoppingWrite` con `data class Add(name: String, quantity: String?, notes: String?, supermarket: Supermarket?, brand: String?)` y `data class Change(seen: ShoppingItem, quantity: String?, notes: String?, supermarket: Supermarket?, brand: String?, relist: Boolean)` (`data/repository/ShoppingRepository.kt`).
  - `suspend fun ShoppingRepository.applyWrites(spaceId: String, writes: List<ShoppingWrite>)`; `relistItem` desaparece.
  - `ShoppingFailure.ItemChanged`; `ShoppingUiMessage.ItemChanged`; `MealUiMessage.ShoppingChanged`; `RecipeUiMessage.ShoppingChanged`.
  - `fun ingredientReviewWrites(rows: List<IngredientReviewRow>, choices: List<IngredientReviewChoice>): List<ShoppingWrite>` (`feature/common/IngredientReview.kt`, pura).
  - Dobles: `RecordingShoppingRepository.appliedBatches: MutableList<List<ShoppingWrite>>` y `applyFailure: Throwable?`; `FakeShoppingRepository.applied: List<ShoppingWrite>?` (en `ShoppingViewModelTest.kt`).

- [ ] **Step 1: Test de `sameContentAs`.** Crear `UT/core/model/ShoppingItemTest.kt`:

```kotlin
package com.dlunaunizar.bobitos.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ShoppingItemTest {
    private val item = ShoppingItem(
        id = "s1",
        name = "Leche",
        quantity = "1 l",
        notes = null,
        purchased = false,
        createdBy = "u",
        createdByName = "U",
        createdAt = Instant.EPOCH,
        updatedBy = "u",
        updatedAt = Instant.EPOCH,
        purchasedBy = null,
        purchasedByName = null,
        purchasedAt = null,
        supermarket = Supermarket.DIA,
        brand = "Pascual",
    )

    @Test
    fun `the same content ignores who and when but not what the list shows`() {
        assertTrue(item.sameContentAs(item.copy(updatedBy = "otro", updatedAt = Instant.now(), createdByName = "Otro")))
        assertFalse(item.sameContentAs(item.copy(name = "Leche sin lactosa")))
        assertFalse(item.sameContentAs(item.copy(quantity = "2 l")))
        assertFalse(item.sameContentAs(item.copy(notes = "entera")))
        assertFalse(item.sameContentAs(item.copy(purchased = true)))
        assertFalse(item.sameContentAs(item.copy(supermarket = Supermarket.EROSKI)))
        assertFalse(item.sameContentAs(item.copy(brand = "Puleva")))
    }
}
```

- [ ] **Step 2: Dobles.** En `UT/feature/common/RecordingShoppingRepository.kt`:
  - quitar el `override suspend fun relistItem(...)`, pero **conservar** las listas `relisted*`;
  - añadir el import de `com.dlunaunizar.bobitos.data.repository.ShoppingWrite`, y estos campos y este override:

```kotlin
    val appliedBatches = mutableListOf<List<ShoppingWrite>>()
    var applyFailure: Throwable? = null

    // Cada lote queda en appliedBatches y, además, cada escritura en las listas de altas, ediciones y
    // reapuntados, para que los tests comprueben lo escrito sin desmontar el lote.
    override suspend fun applyWrites(spaceId: String, writes: List<ShoppingWrite>) {
        applyFailure?.let { throw it }
        appliedBatches += writes
        writes.forEach { write ->
            when (write) {
                is ShoppingWrite.Add -> {
                    addedNames += write.name
                    addedQuantities += write.quantity
                    addedNotes += write.notes
                    addedSupermarkets += write.supermarket
                    addedBrands += write.brand
                }
                is ShoppingWrite.Change -> if (write.relist) {
                    relistedIds += write.seen.id
                    relistedQuantities += write.quantity
                    relistedSupermarkets += write.supermarket
                    relistedBrands += write.brand
                } else {
                    updatedIds += write.seen.id
                    updatedQuantities += write.quantity
                    updatedSupermarkets += write.supermarket
                    updatedBrands += write.brand
                }
            }
        }
    }
```

  En `UT/feature/shopping/ShoppingViewModelTest.kt`, en `FakeShoppingRepository`:
  - quitar `override suspend fun relistItem(...)`;
  - añadir el import de `ShoppingWrite`, y este campo y este override:

```kotlin
    var applied: List<ShoppingWrite>? = null

    override suspend fun applyWrites(spaceId: String, writes: List<ShoppingWrite>) {
        throwNextFailure()
        applied = writes
        writes.forEach { write ->
            when (write) {
                is ShoppingWrite.Add -> {
                    addedName = write.name
                    addedQuantity = write.quantity
                    addedNotes = write.notes
                    addedSupermarket = write.supermarket
                    addedBrand = write.brand
                }
                is ShoppingWrite.Change -> if (write.relist) {
                    relisted = RelistCall(
                        write.seen.id,
                        write.seen.name,
                        write.quantity,
                        write.notes,
                        write.supermarket,
                        write.brand,
                    )
                } else {
                    updatedItemId = write.seen.id
                    updatedQuantity = write.quantity
                    updatedSupermarket = write.supermarket
                    updatedBrand = write.brand
                }
            }
        }
    }
```

- [ ] **Step 3: Tests de la revisión.** En `UT/feature/common/IngredientReviewTest.kt` (import de `com.dlunaunizar.bobitos.data.repository.ShoppingWrite`):

```kotlin
    @Test
    fun `the whole review is written in one batch in row order`() = runTest {
        val pending = item("s1", "Arroz", supermarket = Supermarket.DIA, brand = "Sos", quantity = "200 g")
        val bought = item("s2", "Leche", quantity = "6 l", purchased = true)
        val rows = buildIngredientReviewRows(
            listOf(Ingredient("Sal", "1", "kg"), Ingredient("Arroz", "300", "g"), Ingredient("Leche", "1", "l")),
            listOf(pending, bought),
            data,
        )
        val choices = rows.map { it.initialState().withInclude(true).toChoice(it) }
        val shopping = RecordingShoppingRepository()

        applyIngredientReview("home", rows, choices, shopping)

        assertEquals(
            listOf(
                listOf(
                    ShoppingWrite.Add("Sal", "1 kg", null, null, null),
                    ShoppingWrite.Change(pending, "500 g", null, Supermarket.DIA, "Sos", relist = false),
                    ShoppingWrite.Change(bought, "1 l", null, null, null, relist = true),
                ),
            ),
            shopping.appliedBatches,
        )
    }

    @Test
    fun `a review with every row omitted writes no batch`() = runTest {
        val rows = buildIngredientReviewRows(
            listOf(Ingredient("Arroz", "300", "g")),
            listOf(item("s1", "arroz", quantity = "200 g")),
            data,
        )
        val shopping = RecordingShoppingRepository()

        applyIngredientReview("home", rows, rows.map { it.initialState().toChoice(it) }, shopping)

        assertTrue(shopping.appliedBatches.isEmpty())
    }
```

- [ ] **Step 4: Tests del editor de la Compra.** En `UT/feature/shopping/ShoppingViewModelTest.kt`:

```kotlin
    @Test
    fun `relisting a purchased product sends the item as it was seen`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val bought = listItem(quantity = "6 l", purchased = true)
            repository.itemState.value = listOf(bought)
            viewModel.observe("home")
            advanceUntilIdle()

            viewModel.addItem("home", "Leche", "2", null, Supermarket.EROSKI, "Puleva")
            advanceUntilIdle()

            assertEquals(
                listOf(ShoppingWrite.Change(bought, "2", null, Supermarket.EROSKI, "Puleva", relist = true)),
                repository.applied,
            )
        }

    @Test
    fun `a product changed by another member meanwhile is reported and the editor stays open`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.itemState.value = listOf(listItem(quantity = "6 l", purchased = true))
            viewModel.observe("home")
            advanceUntilIdle()
            repository.nextFailure = ShoppingRepositoryException(ShoppingFailure.ItemChanged)

            viewModel.addItem("home", "Leche", "2", null, null, null)
            advanceUntilIdle()

            assertEquals(ShoppingUiMessage.ItemChanged, viewModel.uiState.value.error)
            assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.editorSave)
        }

    @Test
    fun `summing a duplicate sends the item as it was seen`() = runTest(mainDispatcherRule.testDispatcher) {
        val pending = listItem(quantity = "1 l")
        repository.itemState.value = listOf(pending)
        viewModel.observe("home")
        advanceUntilIdle()
        viewModel.addItem("home", "Leche", "2 l", null, null, null)
        advanceUntilIdle()

        viewModel.sumDuplicate("home")
        advanceUntilIdle()

        assertEquals(
            listOf(ShoppingWrite.Change(pending, "3 l", null, Supermarket.DIA, "Pascual", relist = false)),
            repository.applied,
        )
    }
```

- [ ] **Step 5: Tests de Comidas y Recetario.** En `UT/feature/meals/MealsViewModelTest.kt` (imports de `ShoppingFailure` y `ShoppingRepositoryException` si faltan):

```kotlin
    @Test
    fun `a shopping list changed while reviewing is reported`() = runTest(mainDispatcherRule.testDispatcher) {
        recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
        viewModel.observe("home")
        advanceUntilIdle()
        viewModel.addIngredientsToShopping(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"))
        advanceUntilIdle()
        shoppingRepository.applyFailure = ShoppingRepositoryException(ShoppingFailure.ItemChanged)

        viewModel.confirmIngredientReview(listOf(choice("300 g")))
        advanceUntilIdle()

        assertEquals(MealUiMessage.ShoppingChanged, viewModel.uiState.value.error)
    }
```

  En `UT/feature/recipes/RecipesViewModelTest.kt` (mismos imports si faltan):

```kotlin
    @Test
    fun `a shopping list changed while reviewing is reported`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe()
        advanceUntilIdle()
        viewModel.addToShopping(
            "home",
            recipe("r1", RecipeVisibility.PRIVATE, "Tortilla", listOf(Ingredient("Huevo", "4", "uds"))),
        )
        advanceUntilIdle()
        shoppingRepository.applyFailure = ShoppingRepositoryException(ShoppingFailure.ItemChanged)
        val rows = viewModel.uiState.value.ingredientReview!!

        viewModel.confirmShoppingReview("home", rows.map { it.initialState().toChoice(it) })
        advanceUntilIdle()

        assertEquals(RecipeUiMessage.ShoppingChanged, viewModel.uiState.value.error)
    }
```

- [ ] **Step 6: Verlos en rojo.** JVM de `ShoppingItemTest`, `IngredientReviewTest`, `ShoppingViewModelTest`, `MealsViewModelTest` y `RecipesViewModelTest`. Lo esperado es un fallo de compilación (`sameContentAs`, `ShoppingWrite`, `applyWrites`, `ItemChanged` y `ShoppingChanged` no existen). Copiar el fallo.

- [ ] **Step 7: Modelo e interfaz.**
  - En `APP/core/model/ShoppingItem.kt`, al final:

```kotlin
/**
 * Mismo contenido visible en la lista: nombre, cantidad, notas, si está comprado, tienda y marca. No mira quién
 * ni cuándo lo tocó. Sirve para no pisar el cambio de otro miembro hecho mientras tanto.
 */
fun ShoppingItem.sameContentAs(other: ShoppingItem): Boolean = name == other.name &&
    quantity == other.quantity &&
    notes == other.notes &&
    purchased == other.purchased &&
    supermarket == other.supermarket &&
    brand == other.brand
```

  - En `APP/data/repository/ShoppingRepository.kt`:
    - sustituir `relistItem` (y su KDoc) por:

```kotlin
    /**
     * Escribe [writes] en una sola transacción: o se aplican todas o ninguna. Cada [ShoppingWrite.Change] comprueba
     * antes que su artículo sigue como se vio ([ShoppingItem.sameContentAs]); si otro miembro lo ha cambiado, no se
     * escribe nada y falla con [ShoppingFailure.ItemChanged] (con [ShoppingFailure.ItemNotFound] si lo ha borrado).
     */
    suspend fun applyWrites(spaceId: String, writes: List<ShoppingWrite>)
```

    - añadir `ItemChanged` a `ShoppingFailure` (detrás de `ItemNotFound`);
    - añadir, tras la interfaz:

```kotlin
/** Una escritura de [ShoppingRepository.applyWrites]. */
sealed interface ShoppingWrite {
    /** Añade un artículo pendiente. */
    data class Add(
        val name: String,
        val quantity: String?,
        val notes: String?,
        val supermarket: Supermarket?,
        val brand: String?,
    ) : ShoppingWrite

    /**
     * Cambia cantidad, notas, tienda y marca de [seen], el artículo tal como se vio (su nombre no cambia). Con
     * [relist], además lo deja pendiente, sin quién ni cuándo lo compró.
     */
    data class Change(
        val seen: ShoppingItem,
        val quantity: String?,
        val notes: String?,
        val supermarket: Supermarket?,
        val brand: String?,
        val relist: Boolean,
    ) : ShoppingWrite
}
```

- [ ] **Step 8: Implementación en Firestore** (`APP/data/repository/FirestoreShoppingRepository.kt`):
  - **Campos comunes:** extraer el mapa de `addItem` a `newItemFields(user: AuthUser, values: ItemValues): Map<String, Any?>`, con los mismos campos y valores. El mapa de `writeItemFields` pasa a ser:

```kotlin
    // Campos de una edición: con `rename` cambia también el nombre; con `relist` deja el artículo pendiente, sin quién
    // ni cuándo lo compró. Los `null` se escriben como `null`.
    private fun changedItemFields(user: AuthUser, values: ItemValues, rename: Boolean, relist: Boolean): Map<String, Any?> {
        val fields = mutableMapOf<String, Any?>()
        if (rename) fields[FIELD_NAME] = values.name
        fields[FIELD_QUANTITY] = values.quantity
        fields[FIELD_NOTES] = values.notes
        fields[FIELD_SUPERMARKET] = values.supermarket
        fields[FIELD_BRAND] = values.brand
        if (relist) {
            fields[FIELD_PURCHASED] = false
            fields[FIELD_PURCHASED_BY] = null
            fields[FIELD_PURCHASED_BY_NAME] = null
            fields[FIELD_PURCHASED_AT] = null
        }
        fields[FIELD_UPDATED_BY] = user.id
        fields[FIELD_UPDATED_AT] = FieldValue.serverTimestamp()
        return fields
    }
```

  - **Edición:** `updateItem` hace su transacción directamente: `requireItem(transaction.get(reference))` y `transaction.update(reference, changedItemFields(user, values, rename = true, relist = false))`. Se quitan `relistItem` y `writeItemFields`.
  - **Escritura nueva** (import de `com.google.firebase.firestore.DocumentReference` y de `com.dlunaunizar.bobitos.core.model.sameContentAs`):

```kotlin
    override suspend fun applyWrites(spaceId: String, writes: List<ShoppingWrite>) = runShoppingOperation {
        if (writes.isEmpty()) return@runShoppingOperation
        val user = requireVerifiedUser()
        val planned = writes.map { planWrite(spaceId, it) }
        val spaceReference = firestore.collection(SPACES).document(spaceId)

        firestore.runTransaction { transaction ->
            // Firestore exige hacer todas las lecturas antes de la primera escritura.
            if (!transaction.get(spaceReference).exists()) {
                throw ShoppingRepositoryException(ShoppingFailure.SpaceNotFound)
            }
            planned.forEach { plan -> plan.seen?.let { requireUnchanged(transaction.get(plan.reference), it) } }
            planned.forEach { plan ->
                if (plan.seen == null) {
                    transaction.set(plan.reference, newItemFields(user, plan.values))
                } else {
                    transaction.update(plan.reference, changedItemFields(user, plan.values, rename = false, relist = plan.relist))
                }
            }
        }.await()
        Unit
    }

    // Valida cada escritura y le da su documento (uno nuevo para las altas) antes de abrir la transacción.
    private fun planWrite(spaceId: String, write: ShoppingWrite): PlannedWrite = when (write) {
        is ShoppingWrite.Add -> PlannedWrite(
            reference = itemsCollection(spaceId).document(),
            values = validateItem(write.name, write.quantity, write.notes, write.supermarket, write.brand),
            seen = null,
            relist = false,
        )
        is ShoppingWrite.Change -> PlannedWrite(
            reference = itemsCollection(spaceId).document(write.seen.id),
            values = validateItem(write.seen.name, write.quantity, write.notes, write.supermarket, write.brand),
            seen = write.seen,
            relist = write.relist,
        )
    }

    // El artículo sigue como se vio; si otro miembro lo ha cambiado, no se escribe nada de la transacción.
    private fun requireUnchanged(snapshot: DocumentSnapshot, seen: ShoppingItem) {
        val current = requireItem(snapshot).toShoppingItem()
            ?: throw ShoppingRepositoryException(ShoppingFailure.ItemNotFound)
        if (!current.sameContentAs(seen)) throw ShoppingRepositoryException(ShoppingFailure.ItemChanged)
    }

    private data class PlannedWrite(
        val reference: DocumentReference,
        val values: ItemValues,
        val seen: ShoppingItem?,
        val relist: Boolean,
    )
```

  (`addItem` sigue creando su documento con `newItemFields`; `restoreItem` del ViewModel lo usa.)

- [ ] **Step 9: Revisión.** En `APP/feature/common/IngredientReview.kt`:
  - sustituir el cuerpo de `applyIngredientReview` por:

```kotlin
suspend fun applyIngredientReview(
    spaceId: String,
    rows: List<IngredientReviewRow>,
    choices: List<IngredientReviewChoice>,
    shoppingRepository: ShoppingRepository,
) {
    val writes = ingredientReviewWrites(rows, choices)
    if (writes.isNotEmpty()) shoppingRepository.applyWrites(spaceId, writes)
}

/**
 * Lo que escribe la revisión, en el orden de las filas: «Omitir» no escribe; un pendiente con «Sumar» cambia solo su
 * cantidad; un comprado se vuelve a apuntar; los dos conservan notas, tienda y marca y van con el artículo tal como
 * se vio. El resto se añade con la cantidad (que ya lleva la unidad) y el súper y la marca elegidos.
 */
fun ingredientReviewWrites(rows: List<IngredientReviewRow>, choices: List<IngredientReviewChoice>): List<ShoppingWrite> =
    rows.mapIndexedNotNull { index, row ->
        val choice = choices.getOrNull(index)
        if (choice?.skip == true) return@mapIndexedNotNull null
        val quantity = choice?.quantity?.trim()?.takeIf(String::isNotEmpty)
        val existing = row.existing
        if (existing == null) {
            ShoppingWrite.Add(row.name, quantity, null, choice?.supermarket, choice?.brand)
        } else {
            ShoppingWrite.Change(existing, quantity, existing.notes, existing.supermarket, existing.brand, existing.purchased)
        }
    }
```

  - actualizar el KDoc anterior de `applyIngredientReview`: «Aplica la revisión en una sola escritura ([ShoppingRepository.applyWrites]); ver [ingredientReviewWrites]»;
  - import de `ShoppingWrite`.

- [ ] **Step 10: Editor de la Compra** (`APP/feature/shopping/ShoppingViewModel.kt`):
  - **Comprado, en `addItem`:** el `repository.relistItem(...)` pasa a ser `repository.applyWrites(spaceId, listOf(ShoppingWrite.Change(match, normalizedQuantity, normalizedNotes, supermarket, brand, relist = true)))`.
  - **`sumDuplicate`:** el `repository.updateItem(...)` pasa a ser `repository.applyWrites(spaceId, listOf(ShoppingWrite.Change(existing, quantity, existing.notes, existing.supermarket, existing.brand, relist = false)))`.
  - **Mensajes:**
    - `toShoppingRepositoryUiMessage`: `ShoppingFailure.ItemChanged -> ShoppingUiMessage.ItemChanged`;
    - `ShoppingUiMessage` (en `ShoppingUiState.kt`) gana `ItemChanged`, detrás de `ItemNotFound`;
    - `ShoppingScreen.kt`, en `stringResourceId`: `ShoppingUiMessage.ItemChanged -> R.string.shopping_error_item_changed`.

- [ ] **Step 11: Comidas y Recetario.**
  - **Comidas:** `MealUiMessage` gana `ShoppingChanged`, detrás de `UnexpectedError`. En `MealsViewModel.kt`, `ShoppingFailure.toUiMessage()`: `ShoppingFailure.ItemChanged -> MealUiMessage.ShoppingChanged`. `MealsScreen.kt`: `MealUiMessage.ShoppingChanged -> R.string.meals_error_shopping_changed`.
  - **Recetario:** `RecipeUiMessage` gana `ShoppingChanged`. En `RecipesViewModel.kt`, `ShoppingFailure.toUiMessage()`: `ShoppingFailure.ItemChanged -> RecipeUiMessage.ShoppingChanged`, antes del `else`. `RecipesScreen.kt`: `RecipeUiMessage.ShoppingChanged -> R.string.recipes_error_shopping_changed`.
  - **`RES`:** cada cadena junto a las de su módulo.

```xml
    <string name="shopping_error_item_changed">Ese producto ha cambiado en la lista mientras tanto. Vuelve a intentarlo.</string>
    <string name="meals_error_shopping_changed">La lista de la compra ha cambiado mientras la revisabas. Vuelve a añadir los ingredientes.</string>
    <string name="recipes_error_shopping_changed">La lista de la compra ha cambiado mientras la revisabas. Vuelve a añadir los ingredientes.</string>
```

- [ ] **Step 12: Verde.** JVM de las cinco clases del Step 6, y después GRADLE completo. Los tests existentes de la revisión y del editor siguen pasando sin cambios (los dobles reparten cada escritura en sus listas de siempre).

- [ ] **Step 13: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/core/model/ShoppingItem.kt app/src/main/java/com/dlunaunizar/bobitos/data/repository/ShoppingRepository.kt app/src/main/java/com/dlunaunizar/bobitos/data/repository/FirestoreShoppingRepository.kt app/src/main/java/com/dlunaunizar/bobitos/feature/common/IngredientReview.kt app/src/main/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingViewModel.kt app/src/main/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingUiState.kt app/src/main/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingScreen.kt app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealsViewModel.kt app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealUiMessage.kt app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealsScreen.kt app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipesViewModel.kt app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipeUiMessage.kt app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipesScreen.kt app/src/main/res/values/strings.xml app/src/test/java/com/dlunaunizar/bobitos/core/model/ShoppingItemTest.kt app/src/test/java/com/dlunaunizar/bobitos/feature/common/RecordingShoppingRepository.kt app/src/test/java/com/dlunaunizar/bobitos/feature/common/IngredientReviewTest.kt app/src/test/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingViewModelTest.kt app/src/test/java/com/dlunaunizar/bobitos/feature/meals/MealsViewModelTest.kt app/src/test/java/com/dlunaunizar/bobitos/feature/recipes/RecipesViewModelTest.kt
git commit -m "fix(compra): la revisión y el editor escriben en una sola transacción y no pisan cambios ajenos"
```

---

### Task 2: Revisión de «Añadir a la compra»: «Omitir» en lo comprado, pistas sin rayas y lista que no se puede leer

**Ficheros:**
- Modificar:
  - `APP/feature/common/IngredientReview.kt` (`initialState`, la pista, el selector y `ReviewRowContent`)
  - `APP/feature/meals/MealsViewModel.kt` (`prepareIngredientReview`)
  - `APP/feature/recipes/RecipesViewModel.kt` (`addToShopping`)
  - `RES`
- Tests: `UT/feature/common/RecordingShoppingRepository.kt`, `UT/feature/common/IngredientReviewTest.kt`, `UT/feature/meals/MealsViewModelTest.kt` y `UT/feature/recipes/RecipesViewModelTest.kt`.

**Interfaces:**
- Consume: `ShoppingWrite`, `ingredientReviewWrites`, `RecordingShoppingRepository.appliedBatches` (Tarea 1).
- Produce:
  - `internal data class ReviewHint(@StringRes val res: Int, val args: List<String> = emptyList())`;
  - `internal fun IngredientReviewRow.hint(): ReviewHint?`, pura, que sustituye a la `@Composable` actual;
  - `RecordingShoppingRepository.itemsFailure: Throwable?`.
- Test existente que cambia: **`a purchased item is relisted with the recipe quantity keeping its brand and store`** (`IngredientReviewTest`). Ahora:
  - `assertFalse(state.include)`;
  - con las opciones por defecto, `writesNothing()`;
  - el reapuntado se comprueba con `state.withInclude(true).toChoice(row)`, con las mismas aserciones de `relisted*` que hoy.

- [ ] **Step 1: Doble.** En `RecordingShoppingRepository` (import de `kotlinx.coroutines.flow.flow`):

```kotlin
    var itemsFailure: Throwable? = null

    override fun items(spaceId: String): Flow<List<ShoppingItem>> =
        itemsFailure?.let { failure -> flow { throw failure } } ?: itemsState
```

- [ ] **Step 2: Tests de la revisión** (`IngredientReviewTest`, imports de `com.dlunaunizar.bobitos.R` y `ShoppingWrite`). Cambiar el test existente como dice «Interfaces» y añadir:

```kotlin
    @Test
    fun `a purchased item is omitted by default and relisted only when chosen`() {
        val bought = item("s2", "Leche", quantity = "6 l", purchased = true)
        val row = buildIngredientReviewRows(listOf(Ingredient("Leche", "1", "l")), listOf(bought), data).single()
        val state = row.initialState()

        assertFalse(state.include)
        assertEquals("1 l", state.quantity)
        assertTrue(listOf(state.toChoice(row)).writesNothing())
        assertEquals(
            listOf(ShoppingWrite.Change(bought, "1 l", null, null, null, relist = true)),
            ingredientReviewWrites(listOf(row), listOf(state.withInclude(true).toChoice(row))),
        )
    }

    @Test
    fun `pending hints name only the quantities there are`() {
        fun hintFor(listed: String?, ingredient: Ingredient) =
            buildIngredientReviewRows(listOf(ingredient), listOf(item("s1", "Sal", quantity = listed)), data).single().hint()

        assertEquals(ReviewHint(R.string.meals_review_existing, listOf("1 kg", "200 g")), hintFor("1 kg", Ingredient("Sal", "200", "g")))
        assertEquals(ReviewHint(R.string.meals_review_existing_listed, listOf("1 kg")), hintFor("1 kg", Ingredient("Sal")))
        assertEquals(ReviewHint(R.string.meals_review_existing_recipe, listOf("200 g")), hintFor(null, Ingredient("Sal", "200", "g")))
        assertEquals(ReviewHint(R.string.meals_review_existing_plain), hintFor(null, Ingredient("Sal")))
    }

    @Test
    fun `purchased and new hints`() {
        val bought = listOf(item("s2", "Leche", purchased = true))
        assertEquals(
            ReviewHint(R.string.meals_review_purchased_recipe, listOf("1 l")),
            buildIngredientReviewRows(listOf(Ingredient("Leche", "1", "l")), bought, data).single().hint(),
        )
        assertEquals(
            ReviewHint(R.string.meals_review_purchased),
            buildIngredientReviewRows(listOf(Ingredient("Leche")), bought, data).single().hint(),
        )
        assertEquals(
            ReviewHint(R.string.meals_review_multi, listOf("200 g + 1 ud")),
            buildIngredientReviewRows(listOf(Ingredient("Sal", "200", "g"), Ingredient("Sal", "1", "ud")), emptyList(), data)
                .single().hint(),
        )
        assertEquals(null, buildIngredientReviewRows(listOf(Ingredient("Sal")), emptyList(), data).single().hint())
    }

    @Test
    fun `rows without any quantity write no quantity when summed or relisted`() {
        val pending = item("s1", "Sal")
        val bought = item("s2", "Pimienta", purchased = true)
        val rows = buildIngredientReviewRows(listOf(Ingredient("Sal"), Ingredient("Pimienta")), listOf(pending, bought), data)

        assertEquals(
            listOf(
                ShoppingWrite.Change(pending, null, null, null, null, relist = false),
                ShoppingWrite.Change(bought, null, null, null, null, relist = true),
            ),
            ingredientReviewWrites(rows, rows.map { it.initialState().withInclude(true).toChoice(it) }),
        )
    }
```

  (`ShoppingQuantity.sum("200 g", "1 ud")` da «200 g + 1 ud», porque no casan, y la pista lista las dos aportaciones.)

- [ ] **Step 3: Tests de Comidas y Recetario.** En `MealsViewModelTest`:

```kotlin
    @Test
    fun `if the shopping list cannot be read the review does not open and the error is shown`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
            shoppingRepository.itemsFailure = ShoppingRepositoryException(ShoppingFailure.Network)
            viewModel.observe("home")
            advanceUntilIdle()

            viewModel.addIngredientsToShopping(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"))
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.value.ingredientReview)
            assertEquals(MealUiMessage.NetworkError, viewModel.uiState.value.error)
        }

    @Test
    fun `a recipe whose ingredients were all bought writes nothing by default`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
            shoppingRepository.itemsState.value = listOf(shoppingItem("s1", "arroz", quantity = "1 kg").copy(purchased = true))
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.addIngredientsToShopping(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"))
            advanceUntilIdle()
            val review = viewModel.uiState.value.ingredientReview!!

            viewModel.confirmIngredientReview(review.map { it.initialState().toChoice(it) })
            advanceUntilIdle()

            assertTrue(shoppingRepository.appliedBatches.isEmpty())
            assertEquals(MealUiMessage.NothingToAddToShopping, viewModel.uiState.value.notice)
        }
```

  (Si el `shoppingItem(...)` del test no admite `quantity`, usar `.copy(quantity = "1 kg", purchased = true)`.)

  En `RecipesViewModelTest`:

```kotlin
    @Test
    fun `if the shopping list cannot be read the review does not open and the error is shown`() =
        runTest(mainDispatcherRule.testDispatcher) {
            shoppingRepository.itemsFailure = ShoppingRepositoryException(ShoppingFailure.Network)
            viewModel.observe()
            advanceUntilIdle()

            viewModel.addToShopping(
                "home",
                recipe("r1", RecipeVisibility.PRIVATE, "Tortilla", listOf(Ingredient("Huevo", "4", "uds"))),
            )
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.value.ingredientReview)
            assertEquals(RecipeUiMessage.NetworkError, viewModel.uiState.value.error)
        }
```

- [ ] **Step 4: Rojo.** JVM de `IngredientReviewTest`, `MealsViewModelTest` y `RecipesViewModelTest`: no compila (`ReviewHint`, `hint()` pura, `itemsFailure`) o falla (`include` del comprado y revisión abierta con la lista sin leer). Copiar el fallo.

- [ ] **Step 5: Estado inicial y pistas** (`IngredientReview.kt`, import de `androidx.annotation.StringRes`):
  - **`initialState`:** sustituirla (y su comentario) por:

```kotlin
// Ya en la lista (pendiente o comprado): «Omitir» marcado; con «Sumar», la suma preparada; con «Volver a apuntar»,
// la cantidad de la receta. Nueva: la de la receta, con la marca y la tienda por defecto.
internal fun IngredientReviewRow.initialState(): ReviewRowState = when (kind) {
    ReviewRowKind.PENDING -> ReviewRowState(
        quantity = ShoppingQuantity.sum(existing?.quantity, recipeQuantity).orEmpty(),
        selection = BrandStoreSelection.None,
        freeBrand = "",
        include = false,
    )
    ReviewRowKind.PURCHASED -> ReviewRowState(
        quantity = recipeQuantity.orEmpty(),
        selection = BrandStoreSelection.None,
        freeBrand = "",
        include = false,
    )
    ReviewRowKind.NEW -> ReviewRowState(
        quantity = recipeQuantity.orEmpty(),
        selection = context?.defaultSelection() ?: BrandStoreSelection.None,
        freeBrand = "",
    )
}
```

  - **KDoc:** el de `ReviewRowState` dice ahora «`include` = false en una fila ya en la lista (pendiente o comprada) con «Omitir»». El de `ReviewRowKind.PURCHASED`: «Está, pero comprado: «Omitir» (por defecto) o «Volver a apuntar» ese artículo (pendiente, con la cantidad nueva)».
  - **Pista:** sustituir la `@Composable private fun IngredientReviewRow.hint()` por estas funciones puras:

```kotlin
/** Pista bajo el nombre de una fila: su texto y sus argumentos. Si falta una cantidad, no se nombra (sin «—»). */
internal data class ReviewHint(@StringRes val res: Int, val args: List<String> = emptyList())

internal fun IngredientReviewRow.hint(): ReviewHint? = when (kind) {
    ReviewRowKind.PENDING -> pendingHint()
    ReviewRowKind.PURCHASED -> recipeQuantity?.let { ReviewHint(R.string.meals_review_purchased_recipe, listOf(it)) }
        ?: ReviewHint(R.string.meals_review_purchased)
    ReviewRowKind.NEW -> when {
        quantities.size >= 2 -> ReviewHint(R.string.meals_review_multi, listOf(quantities.joinToString(" + ")))
        recipeQuantity != null -> ReviewHint(R.string.meals_review_recipe, listOf(recipeQuantity))
        else -> null
    }
}

private fun IngredientReviewRow.pendingHint(): ReviewHint {
    val listed = existing?.quantity?.trim()?.takeIf(String::isNotEmpty)
    val recipe = recipeQuantity
    return when {
        listed != null && recipe != null -> ReviewHint(R.string.meals_review_existing, listOf(listed, recipe))
        listed != null -> ReviewHint(R.string.meals_review_existing_listed, listOf(listed))
        recipe != null -> ReviewHint(R.string.meals_review_existing_recipe, listOf(recipe))
        else -> ReviewHint(R.string.meals_review_existing_plain)
    }
}
```

- [ ] **Step 6: Diálogo** (`ReviewRowContent` y el selector):
  - **Pista:** `row.hint()?.let { hint -> Text(text = stringResource(hint.res, *hint.args.toTypedArray()), style = …, color = …) }`, con el mismo estilo que hoy.
  - **Selector:** `SkipOrSumPicker` pasa a llamarse `SkipOrIncludePicker(include: Boolean, @StringRes includeLabel: Int, onChange: (Boolean) -> Unit)`. Las opciones son `listOf(false to R.string.meals_review_skip, true to includeLabel)`, y su comentario: «// «Omitir» (no se toca el artículo de la lista) o «Sumar» / «Volver a apuntar» (se edita la cantidad propuesta).»
  - **Cuándo sale:** en `ReviewRowContent`, el `if (row.kind == ReviewRowKind.PENDING)` pasa a ser `if (row.kind != ReviewRowKind.NEW)`, con `includeLabel = if (row.kind == ReviewRowKind.PENDING) R.string.meals_review_sum else R.string.meals_review_relist`.

- [ ] **Step 7: Lista que no se puede leer.**
  - **Recetario:** en `RecipesViewModel.addToShopping`, la línea `val current = runCatching { … }.getOrDefault(emptyList())` pasa a ser:

```kotlin
            // Sin la lista no se pueden buscar repetidos: si no se puede leer, no se abre la revisión.
            val current = runCatching { shoppingRepository.items(spaceId).first() }
                .onFailure { if (it is CancellationException) throw it }
                .getOrElse { error ->
                    showError(error.toUiMessage())
                    return@launch
                }
```

  - **Comidas:** lo mismo en `MealsViewModel.prepareIngredientReview`. **No** se toca la lectura de `markCooked`.

- [ ] **Step 8: Cadenas** (`RES`, junto a las `meals_review_*`):
  - cambiar `meals_review_purchased` a `Ya comprado`;
  - añadir:

```xml
    <string name="meals_review_existing_listed">Ya en la lista: %1$s</string>
    <string name="meals_review_existing_recipe">Ya en la lista · receta: %1$s</string>
    <string name="meals_review_existing_plain">Ya en la lista</string>
    <string name="meals_review_purchased_recipe">Ya comprado · receta: %1$s</string>
    <string name="meals_review_relist">Volver a apuntar</string>
```

- [ ] **Step 9: Verde.** JVM de las tres clases y GRADLE completo.

- [ ] **Step 10: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/common/IngredientReview.kt app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealsViewModel.kt app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipesViewModel.kt app/src/main/res/values/strings.xml app/src/test/java/com/dlunaunizar/bobitos/feature/common/RecordingShoppingRepository.kt app/src/test/java/com/dlunaunizar/bobitos/feature/common/IngredientReviewTest.kt app/src/test/java/com/dlunaunizar/bobitos/feature/meals/MealsViewModelTest.kt app/src/test/java/com/dlunaunizar/bobitos/feature/recipes/RecipesViewModelTest.kt
git commit -m "feat(compra): lo ya comprado se omite por defecto en la revisión y las pistas no muestran rayas"
```

---

### Task 3: Editor de la Compra: «Sumar cantidad» rellena huecos, suma demasiado larga y lista sin cargar

**Ficheros:**
- Modificar:
  - `APP/feature/shopping/ShoppingUiState.kt` (`ShoppingDuplicate` y `ShoppingUiMessage.ListNotLoaded`)
  - `APP/feature/shopping/ShoppingViewModel.kt` (`addItem`, `sumDuplicate` y `currentItems`)
  - `APP/feature/shopping/ShoppingScreen.kt` (aviso «Ya en la lista» y `stringResourceId`)
  - `RES`
- Tests:
  - crear `UT/feature/shopping/ShoppingDuplicateTest.kt`;
  - modificar `UT/feature/shopping/ShoppingViewModelTest.kt`.

**Interfaces:**
- Consume: `ShoppingWrite.Change`, `applyWrites` y `FakeShoppingRepository.applied` (Tarea 1).
- Produce:
  - `ShoppingDuplicate(existing, quantity, notes = null, supermarket = null, brand = null)`, con las propiedades calculadas `summed: String?`, `sumTooLong: Boolean` y `existingQuantityLabel: String?`;
  - `ShoppingUiMessage.ListNotLoaded`.
- Tests existentes que cambian (`ShoppingViewModelTest`):
  - **Lista vacía observada antes:** en `add trims values and reports success`, `editor save is SAVED only after the repository answers` y `editor save failure or timeout leaves FAILED`, añadir al principio `repository.itemState.value = emptyList()`, `viewModel.observe("home")` y `advanceUntilIdle()`, porque ahora el alta exige la lista cargada.
  - **`a sum longer than 40 characters is reported and not written`** se sustituye por `a sum that does not fit keeps the notice open and writes nothing` (abajo).

- [ ] **Step 1: Tests puros del aviso.** Crear `UT/feature/shopping/ShoppingDuplicateTest.kt`:

```kotlin
package com.dlunaunizar.bobitos.feature.shopping

import com.dlunaunizar.bobitos.core.model.ShoppingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ShoppingDuplicateTest {
    private fun item(quantity: String?) = ShoppingItem(
        id = "milk", name = "Leche", quantity = quantity, notes = null, purchased = false,
        createdBy = "u", createdByName = "U", createdAt = Instant.EPOCH, updatedBy = "u", updatedAt = Instant.EPOCH,
        purchasedBy = null, purchasedByName = null, purchasedAt = null,
    )

    @Test
    fun `the sum is ready when the notice opens and says when it does not fit`() {
        assertEquals("3 l", ShoppingDuplicate(item("1 l"), "2 l").summed)
        assertFalse(ShoppingDuplicate(item("1 l"), "2 l").sumTooLong)
        assertTrue(ShoppingDuplicate(item("x".repeat(30)), "y".repeat(10)).sumTooLong)
    }

    @Test
    fun `the existing quantity does not break across lines and is absent when there is none`() {
        assertEquals("5 / ud", ShoppingDuplicate(item(" 5 / ud "), "2").existingQuantityLabel)
        assertNull(ShoppingDuplicate(item(null), "2").existingQuantityLabel)
        assertNull(ShoppingDuplicate(item("  "), "2").existingQuantityLabel)
    }
}
```

- [ ] **Step 2: Tests del ViewModel** (`ShoppingViewModelTest`, import de `ShoppingWrite`). Aplicar los cambios de «Interfaces» y añadir:

```kotlin
    @Test
    fun `summing a duplicate fills the empty notes, store and brand with the typed ones`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val pending = shoppingItem().copy(quantity = "1 l")
            repository.itemState.value = listOf(pending)
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.addItem("home", "Leche", "2 l", " sin lactosa ", Supermarket.EROSKI, " Puleva ")
            advanceUntilIdle()

            viewModel.sumDuplicate("home")
            advanceUntilIdle()

            assertEquals(
                listOf(ShoppingWrite.Change(pending, "3 l", "sin lactosa", Supermarket.EROSKI, "Puleva", relist = false)),
                repository.applied,
            )
        }

    @Test
    fun `summing a duplicate keeps the notes, store and brand the item already has`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val pending = listItem(quantity = "1 l").copy(notes = "entera")
            repository.itemState.value = listOf(pending)
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.addItem("home", "Leche", "2 l", "sin lactosa", Supermarket.EROSKI, "Otra")
            advanceUntilIdle()

            viewModel.sumDuplicate("home")
            advanceUntilIdle()

            assertEquals(
                listOf(ShoppingWrite.Change(pending, "3 l", "entera", Supermarket.DIA, "Pascual", relist = false)),
                repository.applied,
            )
        }

    @Test
    fun `summing treats an indifferent store as chosen and blank notes as empty`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val pending = shoppingItem().copy(quantity = "1 l", notes = "  ", supermarket = Supermarket.INDIFERENTE)
            repository.itemState.value = listOf(pending)
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.addItem("home", "Leche", "2 l", "sin lactosa", Supermarket.EROSKI, null)
            advanceUntilIdle()

            viewModel.sumDuplicate("home")
            advanceUntilIdle()

            assertEquals(
                listOf(ShoppingWrite.Change(pending, "3 l", "sin lactosa", Supermarket.INDIFERENTE, null, relist = false)),
                repository.applied,
            )
        }

    @Test
    fun `a sum that does not fit keeps the notice open and writes nothing`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.itemState.value = listOf(listItem(quantity = "x".repeat(30)))
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.addItem("home", "Leche", "y".repeat(10), null, null, null)
            advanceUntilIdle()

            viewModel.sumDuplicate("home")
            advanceUntilIdle()

            assertNull(repository.applied)
            assertTrue(viewModel.uiState.value.duplicate?.sumTooLong == true)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `adding before the list is loaded asks to wait and writes nothing`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.addItem("home", "Pan", "1", null, null, null)
            advanceUntilIdle()

            assertNull(repository.addedName)
            assertNull(repository.applied)
            assertEquals(ShoppingUiMessage.ListNotLoaded, viewModel.uiState.value.error)
            assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.editorSave)
        }
```

- [ ] **Step 3: Rojo.** JVM de `ShoppingDuplicateTest` y `ShoppingViewModelTest`. Copiar el fallo.

- [ ] **Step 4: El aviso** (`ShoppingUiState.kt`, imports de `Supermarket` y `ShoppingQuantity`). Sustituir `ShoppingDuplicate` (y su KDoc) por:

```kotlin
/**
 * Artículo ya pendiente en la lista ([existing]) y lo tecleado en el editor: la cantidad que se quería añadir y las
 * notas, la tienda y la marca con que «Sumar cantidad» rellena las que el artículo tenga vacías.
 */
data class ShoppingDuplicate(
    val existing: ShoppingItem,
    val quantity: String?,
    val notes: String? = null,
    val supermarket: Supermarket? = null,
    val brand: String? = null,
) {
    /** La cantidad del artículo tras «Sumar cantidad». */
    val summed: String? get() = ShoppingQuantity.sum(existing.quantity, quantity)

    /** La suma no cabe en una cantidad: el aviso lo dice y deshabilita «Sumar cantidad». */
    val sumTooLong: Boolean get() = (summed?.length ?: 0) > ShoppingQuantity.MAX_LENGTH

    /** La cantidad del artículo para el aviso, con espacios que no parten línea; null si no tiene. */
    val existingQuantityLabel: String?
        get() = existing.quantity?.trim()?.takeIf(String::isNotEmpty)?.replace(' ', ' ')
}
```

  `ShoppingUiMessage` gana `ListNotLoaded`, detrás de `ItemChanged`.

- [ ] **Step 5: ViewModel** (`ShoppingViewModel.kt`):
  - **Lista leída:** sustituir `currentItems` por:

```kotlin
    // La lista ya leída; null mientras carga o si su lectura ha fallado.
    private val loadedItems: List<ShoppingItem>?
        get() = (mutableUiState.value.items as? UiState.Content)?.value
```

  - **`addItem`:** sustituir su KDoc y su cuerpo por:

```kotlin
    /**
     * Alta desde el editor. Si el producto ya está pendiente en la lista, no escribe: deja el aviso en
     * [ShoppingUiState.duplicate] con lo tecleado. Si está comprado, vuelve a apuntar ese artículo (con su nombre y
     * los valores del editor). Si no está, lo añade. Con la lista aún sin cargar no escribe nada: sin ella no se
     * pueden buscar repetidos.
     */
    fun addItem(
        spaceId: String,
        name: String,
        quantity: String?,
        notes: String?,
        supermarket: Supermarket?,
        brand: String?,
    ) {
        if (!validate(name, quantity, notes)) return
        val items = loadedItems ?: run {
            showError(ShoppingUiMessage.ListNotLoaded, editor = true)
            return
        }
        val match = findShoppingMatch(items, name)
        val normalizedQuantity = quantity.normalized()
        val normalizedNotes = notes.normalized()
        if (match != null && !match.purchased) {
            mutableUiState.update {
                it.copy(
                    duplicate = ShoppingDuplicate(match, normalizedQuantity, normalizedNotes, supermarket, brand.normalized()),
                )
            }
            return
        }
        runAction(ShoppingUiMessage.ItemAdded, editor = true) {
            if (match == null) {
                repository.addItem(spaceId, name.trim(), normalizedQuantity, normalizedNotes, supermarket, brand)
            } else {
                repository.applyWrites(
                    spaceId,
                    listOf(ShoppingWrite.Change(match, normalizedQuantity, normalizedNotes, supermarket, brand, relist = true)),
                )
            }
        }
    }
```

  - **`sumDuplicate`:**

```kotlin
    /**
     * «Sumar cantidad» en el aviso de repetido: suma la cantidad y rellena con lo tecleado las notas, la tienda y la
     * marca que el artículo tenga vacías, sin pisar las que ya tiene. Si la suma no cabe, no hace nada (el aviso lo
     * explica y deshabilita el botón).
     */
    fun sumDuplicate(spaceId: String) {
        val duplicate = mutableUiState.value.duplicate ?: return
        if (duplicate.sumTooLong) return
        mutableUiState.update { it.copy(duplicate = null) }
        val existing = duplicate.existing
        val notes = existing.notes.normalized() ?: duplicate.notes
        if (!validate(existing.name, duplicate.summed, notes)) return
        runAction(ShoppingUiMessage.ItemUpdated, editor = true) {
            repository.applyWrites(
                spaceId,
                listOf(
                    ShoppingWrite.Change(
                        seen = existing,
                        quantity = duplicate.summed,
                        notes = notes,
                        supermarket = existing.supermarket ?: duplicate.supermarket,
                        brand = existing.brand.normalized() ?: duplicate.brand,
                        relist = false,
                    ),
                ),
            )
        }
    }
```

- [ ] **Step 6: Pantalla** (`ShoppingScreen.kt`):
  - **Texto del aviso:** el `text` del `AlertDialog` de `state.duplicate` pasa a ser:

```kotlin
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val listed = duplicate.existingQuantityLabel
                    Text(
                        if (listed != null) {
                            stringResource(R.string.shopping_duplicate_body, duplicate.existing.name, listed)
                        } else {
                            stringResource(R.string.shopping_duplicate_body_plain, duplicate.existing.name)
                        },
                    )
                    if (duplicate.sumTooLong) {
                        Text(
                            text = stringResource(R.string.shopping_duplicate_sum_too_long),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
```

  - **Botón:** el `TextButton` de «Sumar cantidad» gana `enabled = !duplicate.sumTooLong`.
  - **Mensaje:** en `stringResourceId`, `ShoppingUiMessage.ListNotLoaded -> R.string.shopping_error_list_not_loaded`.
  - Añadir los imports que falten (`Column`, `Arrangement`, `MaterialTheme`).

- [ ] **Step 7: Cadenas** (`RES`, junto a las `shopping_duplicate_*`):
  - cambiar `shopping_duplicate_body` a `«%1$s» ya está en la lista (cantidad: %2$s). ¿Sumas la cantidad o no lo añades?`;
  - añadir:

```xml
    <string name="shopping_duplicate_body_plain">«%1$s» ya está en la lista. ¿Sumas la cantidad o no lo añades?</string>
    <string name="shopping_duplicate_sum_too_long">Sumadas, las cantidades pasarían de 40 caracteres. Corrige la del producto en la lista o no lo añadas.</string>
    <string name="shopping_error_list_not_loaded">Aún no se ha cargado la lista: espera un momento y vuelve a guardar.</string>
```

- [ ] **Step 8: Verde.** JVM de las dos clases y GRADLE completo.

- [ ] **Step 9: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingUiState.kt app/src/main/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingViewModel.kt app/src/main/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingScreen.kt app/src/main/res/values/strings.xml app/src/test/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingDuplicateTest.kt app/src/test/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingViewModelTest.kt
git commit -m "feat(compra): «Sumar cantidad» rellena lo que falta y avisa si la suma no cabe"
```

---

### Task 4: Compra: plural y singular de las unidades al sumar

**Ficheros:**
- Modificar: `APP/core/model/ShoppingQuantity.kt` (`unitFor` y `pluralOf`, y una `singularOf` nueva).
- Test: `UT/core/model/ShoppingQuantityTest.kt`.

**Interfaces:**
- Consume: nada nuevo.
- Produce: `ShoppingQuantity.sum` con la concordancia nueva; la firma no cambia.

- [ ] **Step 1: Test.** En `ShoppingQuantityTest`:

```kotlin
    @Test
    fun `units outside the basic ones follow the Spanish plural`() {
        assertEquals("2 packs", ShoppingQuantity.sum("1 pack", "1 pack"))
        assertEquals("2 nueces", ShoppingQuantity.sum("1 nuez", "1 nuez"))
        assertEquals("2 limones", ShoppingQuantity.sum("1 limón", "1 limón"))
        assertEquals("2 sobres", ShoppingQuantity.sum("1 sobre", "1 sobre"))
        assertEquals("3 terrones", ShoppingQuantity.sum("1 terrón", "2 terrón"))
    }

    @Test
    fun `a total of one or less goes back to the singular when only the plural was written`() {
        assertEquals("1 cucharada", ShoppingQuantity.sum("1/2 cucharadas", "1/2 cucharadas"))
        assertEquals("1 nuez", ShoppingQuantity.sum("1/2 nueces", "1/2 nueces"))
        assertEquals("1 limón", ShoppingQuantity.sum("1/2 limones", "1/2 limones"))
        assertEquals("1 sobre", ShoppingQuantity.sum("1/2 sobres", "1/2 sobres"))
    }
```

- [ ] **Step 2: Rojo.** JVM de `com.dlunaunizar.bobitos.core.model.ShoppingQuantityTest`. Lo esperado: «packes», «nuezes», «limónes», «terrónes» y «1 cucharadas». Copiar el fallo.

- [ ] **Step 3: Implementación** (`ShoppingQuantity.kt`):
  - **`unitFor`:** en la rama `total <= BigDecimal.ONE`, `?: head` pasa a ser `?: singularOf(head)`. El comentario de `unitFor` gana: «sin grafía en singular, se saca de la del plural».
  - **Plural:** sustituir `pluralOf` por:

```kotlin
    // Plural aproximado del español: tras vocal, «-s»; «-z» → «-ces»; tras l, r, n, d o j, «-es», y la aguda pierde la
    // tilde («limón» → «limones»); tras otra consonante (palabras de fuera, como «pack»), «-s».
    private fun pluralOf(unit: String): String {
        val last = unit.last().lowercaseChar()
        return when {
            last in VOWELS -> "${unit}s"
            last == 'z' -> "${unit.dropLast(1)}ces"
            last in ES_PLURAL_CONSONANTS -> "${unit.withoutFinalAccent()}es"
            else -> "${unit}s"
        }
    }

    // Singular aproximado de una grafía en plural: «-ces» → «-z»; «-ones» → «-ón»; si no, sin la «-s» final. Falla en
    // palabras raras como unidad («flores», «panes»).
    private fun singularOf(unit: String): String {
        val lower = unit.lowercase()
        return when {
            lower.endsWith("ces") -> "${unit.dropLast(3)}z"
            lower.endsWith("ones") -> "${unit.dropLast(4)}ón"
            lower.endsWith("s") -> unit.dropLast(1)
            else -> unit
        }
    }

    // Quita la tilde de la vocal que va justo antes de la consonante final («limón» → «limon»).
    private fun String.withoutFinalAccent(): String {
        if (length < 2) return this
        val plain = PLAIN_VOWELS[this[length - 2]] ?: return this
        return substring(0, length - 2) + plain + last()
    }
```

  - **Constantes**, junto a `VOWELS`:

```kotlin
    private const val ES_PLURAL_CONSONANTS = "lrndj"
    private val PLAIN_VOWELS = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u')
```

- [ ] **Step 4: Verde.** JVM de `ShoppingQuantityTest`, `RecipeUnitsFixtureTest` e `IngredientReviewTest`, y GRADLE completo.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/core/model/ShoppingQuantity.kt app/src/test/java/com/dlunaunizar/bobitos/core/model/ShoppingQuantityTest.kt
git commit -m "fix(compra): al sumar, las unidades concuerdan en número con el plural del español"
```

---

### Task 5: Recetario: «Deshacer» tras el borrado confirmado, autor sin uid y buscador sin tildes

**Ficheros:**
- Modificar:
  - `APP/feature/recipes/RecipesUiState.kt` (`deletedRecipe`)
  - `APP/feature/recipes/RecipesViewModel.kt` (`deleteRecipe`, `consumeDeletedRecipe`, `restoreRecipe` y `runAction`)
  - `APP/feature/recipes/RecipesScreen.kt` (diálogo de borrado, efecto del «Deshacer», filtro y autor de la fila)
  - `APP/feature/recipes/RecipeSheets.kt` (autor del detalle)
  - `APP/feature/recipes/RecipeSections.kt` (`matching`)
  - `APP/data/repository/RecipeFirestore.kt` (`recipeAuthorName`)
  - `APP/data/repository/FirestoreRecipeRepository.kt` (`toRecipe`)
- Tests: `UT/feature/recipes/RecipesViewModelTest.kt`, `UT/feature/recipes/RecipeSectionsTest.kt` y `UT/data/repository/RecipeFirestoreTest.kt`.

**Interfaces:**
- Consume: `prepareQuery`/`SearchQuery.matches` (`core/common/TextSearch.kt`); `launchUndo` (`core/designsystem/component/UndoSnackbar.kt`).
- Produce:
  - `RecipesUiState.deletedRecipe: Recipe?`;
  - `RecipesViewModel.deleteRecipe(recipe: Recipe)` (antes recibía el id) y `consumeDeletedRecipe()`;
  - `internal fun List<Recipe>.matching(query: String): List<Recipe>`;
  - `internal fun recipeAuthorName(raw: String?): String`.
- Test existente que cambia: **`undoing the delete of a common keeps it common only for the admin`** (`RecipesViewModelTest`) se sustituye por `undoing the delete of a common recreates it common, and without admin rights the rules refuse it` (abajo).
- Doble que cambia: en `FakeRecipeRepository`, `deleteRecipe` pasa a ser:

```kotlin
    var deleteGate: CompletableDeferred<Unit>? = null
    val deletedIds = mutableListOf<String>()

    override suspend fun deleteRecipe(recipeId: String) {
        deleteGate?.await()
        nextFailure?.let { throw it }
        deletedIds += recipeId
    }
```

- [ ] **Step 1: Tests del ViewModel** (`RecipesViewModelTest`):

```kotlin
    @Test
    fun `the undo is offered only once the delete is confirmed`() = runTest(mainDispatcherRule.testDispatcher) {
        val tortilla = recipe("r1", RecipeVisibility.PRIVATE, "Tortilla")
        val gate = CompletableDeferred<Unit>()
        repository.deleteGate = gate

        viewModel.deleteRecipe(tortilla)
        runCurrent()
        assertNull(viewModel.uiState.value.deletedRecipe)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("r1"), repository.deletedIds)
        assertEquals(tortilla, viewModel.uiState.value.deletedRecipe)

        viewModel.consumeDeletedRecipe()
        assertNull(viewModel.uiState.value.deletedRecipe)
    }

    @Test
    fun `a failed delete offers no undo and shows the error`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.nextFailure = RecipeRepositoryException(RecipeFailure.Network)

        viewModel.deleteRecipe(recipe("r1", RecipeVisibility.PRIVATE, "Tortilla"))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.deletedRecipe)
        assertEquals(RecipeUiMessage.NetworkError, viewModel.uiState.value.error)
    }

    @Test
    fun `undoing the delete of a common recreates it common, and without admin rights the rules refuse it`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val common = recipe("torrijas", RecipeVisibility.GLOBAL, "Torrijas")

            viewModel.restoreRecipe(common)
            advanceUntilIdle()
            assertEquals(common, repository.restored)

            repository.nextFailure = RecipeRepositoryException(RecipeFailure.PermissionDenied)
            viewModel.restoreRecipe(common)
            advanceUntilIdle()
            assertEquals(RecipeUiMessage.PermissionDenied, viewModel.uiState.value.error)
        }
```

  (Imports: `runCurrent` y `assertNull`, si faltan.)

- [ ] **Step 2: Tests del buscador y del autor.** En `RecipeSectionsTest`, añadir lo siguiente. El helper `recipe(id, visibility)` del fichero no admite título ni ingredientes: usar `.copy(...)`, y añadir el import de `Ingredient`.

```kotlin
    @Test
    fun `the search goes by words without accents or case in title, category and ingredients`() {
        val pollo = recipe("p1", RecipeVisibility.GLOBAL)
            .copy(title = "Pechugas de pollo al limón", category = "Carnes", ingredients = listOf(Ingredient("Pechuga de pollo")))
        val arroz = recipe("a1", RecipeVisibility.PRIVATE)
            .copy(title = "Arroz al horno", category = "Arroces", ingredients = listOf(Ingredient("Garbanzos cocidos")))
        val all = listOf(pollo, arroz)

        assertEquals(listOf("p1"), all.matching("limon").map(Recipe::id))
        assertEquals(listOf("p1"), all.matching("  POLLO   limón ").map(Recipe::id))
        assertEquals(listOf("a1"), all.matching("garbanzos").map(Recipe::id))
        assertEquals(listOf("a1"), all.matching("arroces").map(Recipe::id))
        assertEquals(emptyList<String>(), all.matching("limón arroces").map(Recipe::id))
        assertEquals(listOf("p1", "a1"), all.matching(" ").map(Recipe::id))
    }
```

  En `RecipeFirestoreTest`:

```kotlin
    @Test
    fun `a missing author name stays empty instead of becoming the uid`() {
        assertEquals("", recipeAuthorName(null))
        assertEquals("", recipeAuthorName("   "))
        assertEquals("Ana", recipeAuthorName(" Ana "))
    }
```

- [ ] **Step 3: Rojo.** JVM de las tres clases. Copiar el fallo.

- [ ] **Step 4: ViewModel y estado.**
  - **Estado:** `RecipesUiState` gana, al final:

```kotlin
    // Receta recién borrada, con el borrado ya confirmado: la pantalla ofrece «Deshacer» y la consume.
    val deletedRecipe: Recipe? = null,
```

  - **`runAction`** (`RecipesViewModel.kt`) gana un parámetro antes de `action`: `onSuccess: (RecipesUiState) -> RecipesUiState = { it }`. En `.onSuccess`, el estado nuevo pasa por él:

```kotlin
                    mutableUiState.update {
                        onSuccess(
                            it.copy(isSaving = false, notice = successNotice, editorSave = it.editorSave.succeeded(editor)),
                        )
                    }
```

  - **`deleteRecipe`:** sustituirla por:

```kotlin
    // Sin notice: el «Deshacer» lo ofrece la pantalla cuando el servidor confirma el borrado (deletedRecipe); si
    // falla, sale el error y no se ofrece.
    fun deleteRecipe(recipe: Recipe) {
        runAction(null, onSuccess = { it.copy(deletedRecipe = recipe) }) { repository.deleteRecipe(recipe.id) }
    }

    fun consumeDeletedRecipe() = mutableUiState.update { it.copy(deletedRecipe = null) }
```

  - **`restoreRecipe`** (y su comentario):

```kotlin
    // Recrea la receta borrada («Deshacer») tal como era, con su mismo id y su visibilidad: el importador del catálogo
    // no duplica una común recuperada y las comidas que la enlazaban la vuelven a encontrar. Si quien deshace ya no
    // puede publicar comunes, las reglas lo rechazan y sale el error (nunca se guarda como privada con el id del
    // catálogo). No es un guardado del editor, no mueve su estado.
    fun restoreRecipe(recipe: Recipe) = runAction(RecipeUiMessage.RecipeSaved) { repository.restoreRecipe(recipe) }
```

- [ ] **Step 5: Pantalla** (`RecipesScreen.kt`):
  - **Borrar:** en el `onConfirm` del diálogo de borrado, dejar solo `viewModel.deleteRecipe(recipe)` y `recipeToDelete = null` (fuera el `scope.launchUndo` de ahí).
  - **«Deshacer»:** justo después de `val undoLabel = …`, añadir:

```kotlin
    // «Deshacer» solo cuando el servidor ha confirmado el borrado: si falla, sale el error y no se ofrece.
    LaunchedEffect(state.deletedRecipe) {
        val deleted = state.deletedRecipe ?: return@LaunchedEffect
        viewModel.consumeDeletedRecipe()
        scope.launchUndo(snackbarHostState, deletedMessage, undoLabel) { viewModel.restoreRecipe(deleted) }
    }
```

  - **Filtro:** `recipes.value.filter { it.matches(query) && (category == null || it.category == category) }` pasa a ser `recipes.value.matching(query).filter { category == null || it.category == category }`. Quitar la `private fun Recipe.matches(query: String)`.
  - **Autor de la fila:** el texto de la fila solo nombra el autor si lo hay, y la línea no se pinta si queda vacía:

```kotlin
            listOfNotNull(
                recipe.category,
                recipe.createdByName.takeIf(String::isNotBlank)?.let { stringResource(R.string.recipes_by, it) },
            ).joinToString(" · ").takeIf(String::isNotEmpty)?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
```

- [ ] **Step 6: Autor y buscador.**
  - **Detalle** (`RecipeSheets.kt`): el `Text` de `R.string.recipes_by` va dentro de `if (recipe.createdByName.isNotBlank()) { … }`.
  - **`RecipeFirestore.kt`:**

```kotlin
// Autor que se muestra («por …»): el guardado, sin espacios; vacío si la receta es antigua y no lo tiene. Nunca el uid:
// al deshacer su borrado, el repositorio pone el nombre de quien la recrea.
internal fun recipeAuthorName(raw: String?): String = raw?.trim().orEmpty()
```

  - **`FirestoreRecipeRepository.toRecipe`:** `createdByName = getString("createdByName") ?: getString("createdBy") ?: return null,` pasa a ser `createdByName = recipeAuthorName(getString("createdByName")),`. `restoreRecipe` ya cae en `user.recipeDisplayName` con el autor vacío: no se toca.
  - **`RecipeSections.kt`** (imports de `prepareQuery` e `Ingredient`):

```kotlin
// Buscador del Recetario: como el del selector de Comidas (por palabras, sin tildes ni mayúsculas), pero mirando también
// los nombres de los ingredientes. En blanco, todas.
internal fun List<Recipe>.matching(query: String): List<Recipe> {
    val search = prepareQuery(query)
    return filter { recipe ->
        search.matches(recipe.title, recipe.category, *recipe.ingredients.orEmpty().map(Ingredient::name).toTypedArray())
    }
}
```

- [ ] **Step 7: Verde.** JVM de las tres clases y GRADLE completo.

- [ ] **Step 8: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipesUiState.kt app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipesViewModel.kt app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipesScreen.kt app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipeSheets.kt app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipeSections.kt app/src/main/java/com/dlunaunizar/bobitos/data/repository/RecipeFirestore.kt app/src/main/java/com/dlunaunizar/bobitos/data/repository/FirestoreRecipeRepository.kt app/src/test/java/com/dlunaunizar/bobitos/feature/recipes/RecipesViewModelTest.kt app/src/test/java/com/dlunaunizar/bobitos/feature/recipes/RecipeSectionsTest.kt app/src/test/java/com/dlunaunizar/bobitos/data/repository/RecipeFirestoreTest.kt
git commit -m "fix(recetas): «Deshacer» solo tras el borrado confirmado, sin autor uid y buscador sin tildes"
```

---

### Task 6: Editor de recetas: errores de cada campo, TalkBack de los pasos y foco al quitar un ingrediente

**Ficheros:**
- Modificar:
  - `APP/feature/recipes/RecipeDraft.kt` (`servingsError`, `totalMinutesError` y `tooLongSteps`)
  - `APP/feature/recipes/RecipeStepsEditor.kt`
  - `APP/feature/recipes/RecipeSheets.kt` (llamadas y `IngredientsEditor`)
  - `RES`
- Test: `UT/feature/recipes/RecipeDraftTest.kt`.

**Interfaces:**
- Consume: `toRecipeCount`, `RecipeLimits`.
- Produce:
  - `internal fun RecipeDraft.servingsError(): RecipeUiMessage?`;
  - `internal fun RecipeDraft.totalMinutesError(): RecipeUiMessage?`;
  - `internal fun RecipeDraft.tooLongSteps(): Set<Int>`;
  - `ServingsAndTimeFields(draft, onDraft)` y `StepsEditor(draft, onDraft)` pierden el parámetro `validation`.

- [ ] **Step 1: Test.** En `RecipeDraftTest`:

```kotlin
    @Test
    fun `servings and time errors are marked even when another field fails first`() {
        val draft = RecipeDraft.of(null, null).copy(title = "", servings = "0", totalMinutes = "dos")

        assertEquals(RecipeUiMessage.TitleRequired, draft.validate())
        assertEquals(RecipeUiMessage.ServingsInvalid, draft.servingsError())
        assertEquals(RecipeUiMessage.TotalMinutesInvalid, draft.totalMinutesError())

        val fine = draft.copy(servings = " 4 ", totalMinutes = "")
        assertNull(fine.servingsError())
        assertNull(fine.totalMinutesError())
        assertEquals(RecipeUiMessage.TotalMinutesInvalid, fine.copy(totalMinutes = "1441").totalMinutesError())
    }

    @Test
    fun `a step that is too long is marked whatever fails first`() {
        val draft = RecipeDraft.of(null, null).copy(
            title = "",
            steps = listOf("Corta.", "x".repeat(501), "  ${"y".repeat(500)}  "),
        )

        assertEquals(setOf(1), draft.tooLongSteps())
    }
```

- [ ] **Step 2: Rojo.** JVM de `com.dlunaunizar.bobitos.feature.recipes.RecipeDraftTest`: no compila. Copiar el fallo.

- [ ] **Step 3: Funciones del borrador** (`RecipeDraft.kt`, tras `validate()`):

```kotlin
// Errores de cada campo, aparte de la validación general (que solo da el primero): raciones, tiempo y cada paso largo
// se marcan aunque falle antes otro campo, como el título.
internal fun RecipeDraft.servingsError(): RecipeUiMessage? =
    RecipeUiMessage.ServingsInvalid.takeIf { servings.toRecipeCount().isOutside(RecipeLimits.SERVINGS) }

internal fun RecipeDraft.totalMinutesError(): RecipeUiMessage? =
    RecipeUiMessage.TotalMinutesInvalid.takeIf { totalMinutes.toRecipeCount().isOutside(RecipeLimits.TOTAL_MINUTES) }

// Posiciones de los pasos que pasan de largo, medidos sin espacios en los extremos, como se guardan.
internal fun RecipeDraft.tooLongSteps(): Set<Int> =
    steps.indices.filter { steps[it].trim().length > RecipeLimits.MAX_STEP_LENGTH }.toSet()

private fun Int?.isOutside(range: IntRange): Boolean = this != null && this !in range
```

- [ ] **Step 4: Editor** (`RecipeStepsEditor.kt`):
  - **Raciones y tiempo:** `ServingsAndTimeFields(draft: RecipeDraft, onDraft: (RecipeDraft) -> Unit)`, con `error = draft.servingsError()` y `error = draft.totalMinutesError()`.
  - **Pasos largos:** en `StepsEditor(draft: RecipeDraft, onDraft: (RecipeDraft) -> Unit)`, antes del bucle, `val tooLongSteps = draft.tooLongSteps()`; dentro, `val tooLong = index in tooLongSteps`. El comentario «// Solo se marca el paso que se pasa de largo.» se queda.
  - **TalkBack:** `contentDescription = stringResource(R.string.recipes_step_up, index + 1)`; lo mismo con `recipes_step_down` y `recipes_step_remove`.
  - **Llamadas** (`RecipeSheets.kt`): `ServingsAndTimeFields(draft = draft, onDraft = { draft = it })` y `StepsEditor(draft = draft, onDraft = { draft = it })`.

- [ ] **Step 5: Foco al quitar un ingrediente** (`IngredientsEditor`, `RecipeSheets.kt`, import de `androidx.compose.ui.platform.LocalFocusManager`). Al principio de la función:

```kotlin
    // Las filas se pintan por posición: al quitar una, el foco no puede quedarse en la siguiente.
    val focusManager = LocalFocusManager.current
```

  El `onClick` del botón de quitar pasa a ser:

```kotlin
                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        onRowsChange(rows.filterIndexed { i, _ -> i != index })
                    },
                ) {
```

- [ ] **Step 6: Cadenas** (`RES`): `recipes_step_remove` → `Quitar el paso %1$d`; `recipes_step_up` → `Subir el paso %1$d`; `recipes_step_down` → `Bajar el paso %1$d`.

- [ ] **Step 7: Verde.** JVM de `RecipeDraftTest` y GRADLE completo. TalkBack y el foco se comprueban en la punta a punta.

- [ ] **Step 8: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipeDraft.kt app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipeStepsEditor.kt app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipeSheets.kt app/src/main/res/values/strings.xml app/src/test/java/com/dlunaunizar/bobitos/feature/recipes/RecipeDraftTest.kt
git commit -m "fix(recetas): el editor marca cada campo erróneo, nombra el paso en TalkBack y suelta el foco al quitar"
```

---

### Task 7: Importador web: pasos de HTML escapado, de listas y de párrafos con saltos de línea

**Ficheros:**
- Modificar: `APP/data/recipeimport/RecipeImporter.kt` (`parseInstructions`, `flattenInstructions`, una `splitInstructionText` nueva y las expresiones del `companion`).
- Test: `UT/data/recipeimport/JsonLdRecipeImporterTest.kt`.

**Interfaces:**
- Consume: `cleanText` (privada de la clase) y `RecipeMetaParser.steps`.
- Produce: `ImportedRecipe.steps` mejor partidos; ninguna firma pública cambia.

- [ ] **Step 1: Tests.** En `JsonLdRecipeImporterTest`, junto a `a single instructions text is split by its line breaks`:

```kotlin
    @Test
    fun `escaped html tags in the instructions split the steps`() = runTest {
        fetcher.html = pageWith(
            """
            {
              "@type": "Recipe",
              "name": "Gazpacho",
              "recipeInstructions": "Trocea.&lt;br&gt;Tritura.&lt;br /&gt;Enfría.&lt;p&gt;Sirve.&lt;/p&gt;"
            }
            """.trimIndent(),
        )

        assertEquals(listOf("Trocea.", "Tritura.", "Enfría.", "Sirve."), importer.import("https://example.com/g").steps)
    }

    @Test
    fun `each instruction of a list is split by its own line breaks`() = runTest {
        fetcher.html = pageWith(
            """
            {
              "@type": "Recipe",
              "name": "Gazpacho",
              "recipeInstructions": [
                "Trocea las verduras.<br>Pélalas.",
                { "@type": "HowToStep", "text": "Tritúralo todo.\nCuélalo." },
                {
                  "@type": "HowToSection",
                  "itemListElement": [{ "@type": "HowToStep", "text": "Enfríalo.&lt;br&gt;Sírvelo." }]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(
            listOf("Trocea las verduras.", "Pélalas.", "Tritúralo todo.", "Cuélalo.", "Enfríalo.", "Sírvelo."),
            importer.import("https://example.com/g").steps,
        )
    }

    @Test
    fun `source line breaks inside html paragraphs do not split a step`() = runTest {
        fetcher.html = pageWith(
            """
            {
              "@type": "Recipe",
              "name": "Gazpacho",
              "recipeInstructions": "<p>Trocea las verduras\ny pélalas.</p>\n<p>Tritúralo todo.</p>"
            }
            """.trimIndent(),
        )

        assertEquals(listOf("Trocea las verduras y pélalas.", "Tritúralo todo."), importer.import("https://example.com/g").steps)
    }

    @Test
    fun `plain instructions with ordinary entities keep splitting by lines`() = runTest {
        fetcher.html = pageWith(
            """
            {
              "@type": "Recipe",
              "name": "Bizcocho",
              "recipeInstructions": "Mezcla harina &amp; azúcar.\nAñade &quot;un poco&quot; de leche."
            }
            """.trimIndent(),
        )

        assertEquals(
            listOf("Mezcla harina & azúcar.", "Añade \"un poco\" de leche."),
            importer.import("https://example.com/b").steps,
        )
    }
```

- [ ] **Step 2: Rojo.** JVM de `com.dlunaunizar.bobitos.data.recipeimport.JsonLdRecipeImporterTest`: fallan los tres primeros (el cuarto ya pasa y fija que no se rompe). Copiar el fallo.

- [ ] **Step 3: Implementación** (`RecipeImporter.kt`, import de `org.jsoup.parser.Parser`):
  - **`parseInstructions`:** la rama `is String` pasa a ser `is String -> splitInstructionText(raw)`, y se quita su comentario de una línea.
  - **`flattenInstructions`:** `is String -> steps += splitInstructionText(item)`, y en la rama de `JSONObject` sin sección, `steps += splitInstructionText(item.optText("text").ifBlank { item.optText("name") })`. Su comentario pasa a ser: «// recipeInstructions puede ser texto, lista de strings, de HowToStep {text} o de HowToSection; cada texto puede traer varios pasos.»
  - **`splitInstructionText`**, tras `flattenInstructions`:

```kotlin
    // Parte un texto de instrucciones en pasos. Las etiquetas escapadas («&lt;br&gt;») se leen como etiquetas. Separan
    // pasos <br>, <p>, </p>, <li> y </li>, y también los saltos de línea, salvo en HTML con párrafos o listas, donde
    // solo son el formato del código fuente.
    private fun splitInstructionText(raw: String): List<String> {
        val html = if (ESCAPED_TAG.containsMatchIn(raw)) Parser.unescapeEntities(raw, false) else raw
        val text = if (HTML_BLOCK.containsMatchIn(html)) html.replace(SOURCE_LINE_BREAK, " ") else html
        return text.replace(HTML_LINE_BREAK, "\n").lines().map { cleanText(it) }.filter(String::isNotBlank)
    }
```

  - **`companion object`:** `HTML_LINE_BREAK` pasa a ser `Regex("<br\\s*/?>|</?(?:p|li)\\b[^>]*>", RegexOption.IGNORE_CASE)`, y se añaden:

```kotlin
        val HTML_BLOCK = Regex("</?(?:p|li)\\b", RegexOption.IGNORE_CASE)
        val ESCAPED_TAG = Regex("&lt;\\s*/?\\s*(?:br|p|li)\\b", RegexOption.IGNORE_CASE)
        val SOURCE_LINE_BREAK = Regex("\\r?\\n")
```

- [ ] **Step 4: Verde.** JVM de `JsonLdRecipeImporterTest` y `RecipeMetaParserTest` (siguen en verde todos los anteriores, incluido `a single instructions text is split by its line breaks`), y GRADLE completo.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/data/recipeimport/RecipeImporter.kt app/src/test/java/com/dlunaunizar/bobitos/data/recipeimport/JsonLdRecipeImporterTest.kt
git commit -m "fix(recetas): el importador web parte en pasos el HTML escapado, las listas y los párrafos"
```

---

### Task 8: Catálogo de recetas: «Salpimienta.» y un mensaje claro para `null`

**Ficheros:**
- Modificar:
  - `data/catalog/recipes.json` (l. 798, receta «Lasaña de carne», paso 2)
  - `scripts/catalog/recipes.mjs` (`amountProblem`)
- Test: `T/catalog-recipes.test.mjs`.

**Interfaces:**
- Consume: `validateRecipeCatalog`, los helpers `withIngredient` del test.
- Produce: nada que usen otras tareas.

- [ ] **Step 1: Tests.** En `T/catalog-recipes.test.mjs`, tras el test «la cantidad es un entero, un decimal con coma o una fracción, mayor que cero»:

```js
test("un null en quantity o en unit pide quitar la clave", () => {
  const noQuantity = withIngredient({ name: "Ajo", quantity: null }).join("\n");
  assert.match(noQuantity, /quantity no puede ser null: si va al gusto, quita la clave quantity/);
  assert.doesNotMatch(noQuantity, /entre comillas/);
  assert.match(withIngredient({ name: "Ajo", quantity: "2", unit: null }).join("\n"), /unit no puede ser null/);
  assert.match(withIngredient({ name: "Ajo", unit: null }).join("\n"), /unit no puede ser null/);
});
```

  Y tras el test «el recipes.json real es válido…»:

```js
test("la lasaña del catálogo real salpimienta la carne", async () => {
  const real = JSON.parse(await readFile(new URL("../data/catalog/recipes.json", import.meta.url), "utf8"));
  const lasagna = real.recipes.find((r) => r.title === "Lasaña de carne");
  assert.ok(lasagna.steps[1].endsWith("unos 5 minutos. Salpimienta."));
  assert.ok(!JSON.stringify(real).includes("Sala y pimienta"));
});
```

- [ ] **Step 2: Rojo.** NODE: fallan los dos. Copiar el fallo.

- [ ] **Step 3: Implementación.**
  - **`recipes.json`:** en el paso 2 de «Lasaña de carne», `Sala y pimienta.` → `Salpimienta.`. No se toca nada más del fichero.
  - **`amountProblem`** (`scripts/catalog/recipes.mjs`): justo después de `const { quantity, unit } = entry;`:

```js
  if (quantity === null) return "quantity no puede ser null: si va al gusto, quita la clave quantity (y unit).";
  if (unit === null) return "unit no puede ser null: quita la clave unit o pon una unidad de la lista.";
```

- [ ] **Step 4: Verde.** NODE completo. Además, `npm run catalog:recipes` valida y genera la revisión sin problemas (no escribe en ninguna base de datos).

- [ ] **Step 5: Commit.**

```bash
git add data/catalog/recipes.json scripts/catalog/recipes.mjs tests/catalog-recipes.test.mjs
git commit -m "fix(catalogo): «Salpimienta.» en la lasaña y mensaje claro para un null en cantidad o unidad"
```

---

## Ejecución (`delegating-plan-execution`, plan de menores)

1. Rama `agent/menores-recetas-compra`, desde `main` (41c7a4d), con este plan como primer commit.
2. Las tareas 1 a 8, una tras otra, cada una con un `sonnet` que recibe su brief. En el `task-done`:
   - Tareas 1 a 7: JVM de las clases nombradas y GRADLE completo;
   - Tarea 8: NODE.
3. **Revisión final** con `opus`, más los arreglos (un `sonnet`, cada uno con su test RED→GREEN). No hay re-revisión salvo que un arreglo toque `migrations/` o uno de los cuatro temas de seguridad (SSRF, autenticación, permisos o aislamiento de datos privados).
4. **Sin `simplify`** (plan de menores).
5. **`code-review`** en nivel `high`, más los arreglos.
6. **Cierre:**
   - Los menores nuevos van al backlog.
   - En el backlog se tachan los cerrados, con la rama.
   - La parte (a) de R4 se reescribe como menor propio: «solo con un segundo admin; exige cambiar permisos en las reglas».
   - El usuario decide qué hacer con la rama.

## Verificación de punta a punta (coordinador, al final)

- **Suites:** GRADLE y NODE en verde.
- **Preparación del emulador:**
  - `npm run emulators` (con `JAVA_HOME` de Android Studio) y `npm run seed:emulators`.
  - Crear en el emulador de Auth el usuario sembrado y ligarlo al espacio. `seed:emulators` no lo hace: script en el scratchpad con el Admin SDK y `FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099`.
  - Importar ingredientes y recetas al emulador: `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 npm run catalog:import-recipes -- --project demo-bobitos --apply`, y antes el de ingredientes si hace falta.
  - Instalar la app debug.
- **Recorrido**, con capturas en tema claro y oscuro de la revisión y del aviso «Ya en la lista»:
  1. **Revisión:** en la Compra, «Arroz» pendiente sin cantidad y «Leche» comprada. Desde una receta con arroz (sin cantidad) y leche, «Añadir a la compra»:
     - el arroz muestra «Ya en la lista», sin rayas;
     - la leche muestra «Ya comprado · receta: …» con «Omitir / Volver a apuntar» y Omitir marcado;
     - Confirmar con todo omitido → «Ya estaba todo en la lista», y la Compra no cambia.
  2. **Volver a apuntar:** repetir y elegir «Volver a apuntar» en la leche → queda pendiente con la cantidad de la receta.
  3. **Cambio ajeno:** con la revisión abierta, cambiar la cantidad del arroz desde un script del Admin SDK contra el emulador. Elegir «Sumar» y confirmar:
     - sale «La lista de la compra ha cambiado mientras la revisabas…»;
     - no se ha escrito nada (tampoco las filas nuevas);
     - volver a abrir la revisión muestra la cantidad nueva.
  4. **Sumar cantidad:** en la Compra, «Leche» pendiente con «5 ud» y sin notas, tienda ni marca. Añadir «leche», «2 ud», con notas, tienda y marca:
     - el aviso no parte «(cantidad: 5 ud)»;
     - «Sumar cantidad» deja «7 ud» y rellena notas, tienda y marca.
     Con una cantidad muy larga, el aviso explica que no cabe y «Sumar cantidad» está deshabilitado.
  5. **Unidades:** «1 pack» + «1 pack» → «2 packs». Desde la revisión: «1/2 cucharadas» + «1/2 cucharadas» → «1 cucharada».
  6. **Recetario, borrar:** borrar una receta propia → el Snackbar «Deshacer» sale tras el borrado; «Deshacer» la recupera. Sin red (modo avión del emulador), el borrado da error y no ofrece «Deshacer».
  7. **Recetario, buscar:** «limon» encuentra «Pechugas de pollo al limón».
  8. **Editor:** sin título y con raciones «0» → el campo de raciones se marca.
     - Con TalkBack o con `adb shell uiautomator dump`: los botones dicen «Subir el paso 2», «Bajar el paso 2» y «Quitar el paso 2».
     - Quitar un ingrediente con el teclado abierto no deja el foco en la fila siguiente.
  9. **Catálogo:** la ficha de «Lasaña de carne» dice «… unos 5 minutos. Salpimienta.».
  10. **Importar** una web con instrucciones en HTML. Si el emulador no tiene red, se salta y se dice.

## Despliegue (tras el merge, cada paso confirmado por el usuario)

Trampas conocidas: `--project dev`; `JAVA_HOME` de Android Studio; `beta:distribute` sube el APK viejo si falla el build.

1. **Reglas:** no cambian; no hay que desplegarlas.
2. **Recetas en `bobitos-dev`** (para K1):
   - simulación: `GOOGLE_APPLICATION_CREDENTIALS=~/.config/bobitos/bobitos-dev-adminsdk.json npm run catalog:import-recipes -- --project dev`. Lo esperado: 1 receta actualizada («Lasaña de carne») y ninguna creada;
   - si el plan es ese, repetir con `--apply`.
3. **Beta 22** (`0.1.0-beta.22`, `VERSION_CODE=22`):
   - notas en `distribution/release-notes.txt`;
   - build release firmado;
   - `npm run beta:distribute`, después de comprobar que el build ha terminado bien.
