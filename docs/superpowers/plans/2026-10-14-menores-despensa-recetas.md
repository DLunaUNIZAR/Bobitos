# Menores de la Despensa en recetas y compra — Plan de implementación

> **Para agentes:** se ejecuta con `delegating-plan-execution`. Es un **plan de menores** (6 tareas): pasos 1, 2, 3, 5 y 6, **sin `simplify`**. Cada tarea la hace un subagente `sonnet`, en serie y en orden. La revisión final la hace `opus` (si falla, `fable`). **Ninguna tarea toca `firestore.rules` ni `migrations/`.** Los pasos usan casillas (`- [ ]`).

**Objetivo:** cerrar los 6 menores con efecto visible de la sección «2026-10-14 — `agent/despensa-recetas`» de `docs/superpowers/backlog.md`. Los internos de esa sección se quedan en el backlog.

**Arquitectura:**
- **Comidas, «Has cocinado …» (Tareas 1 y 3):** «Listo» escribe todo en **una sola transacción** nueva, `ShoppingRepository.applyCooked`: tacha, apunta en la despensa lo tachado que es un ingrediente, quita de la despensa lo que se ha acabado y lo apunta a la compra. El diálogo sigue abierto hasta que la escritura termina; si falla, no se ha escrito nada y conserva lo marcado. Lo que se apunta a la compra va con el nombre del catálogo.
- **Recetario y selector (Tarea 2):** la línea de lo que falta usa «e» delante del sonido /i/.
- **Lecturas (Tarea 4):** la lista y la despensa se leen del servidor a la vez, con una función común (`PantryRepository.withCurrentIds`).
- **Pantallas (Tareas 5 y 6):** en la revisión, marca y tienda van una debajo de otra; el selector «Elegir receta» sobrevive al giro.

**Stack:** Kotlin + Jetpack Compose (Material 3) + Hilt + Firestore. Tests JVM (JUnit 4 + `kotlinx-coroutines-test`, dobles a mano) y tests de Compose instrumentados (`app/src/androidTest`, en el emulador).

**Spec:** `docs/superpowers/specs/2026-10-13-despensa-design.md` (§3c para el diálogo de cocinar, §4a para la revisión, §4c y §4d para «Con lo que hay»). Plan anterior: `docs/superpowers/plans/2026-10-14-despensa-recetas.md` (sus rulings 2, 4 y 5 son los que este plan corrige). Backlog: `docs/superpowers/backlog.md`.

## Qué entra

| # | Menor (backlog, sección «2026-10-14 — `agent/despensa-recetas`») | Tarea |
|---|---|---|
| M1 | «Listo» en «Has cocinado …» no es atómico: tacha en serie, y si falla un paso lo de detrás no se hace, sale el error genérico, el diálogo ya se ha cerrado y lo marcado se pierde | 1 |
| M2 | La línea de lo que falta usa siempre «y»: «Faltan 2: sal y higos» | 2 |
| M3 | «Apuntar a la compra lo que se ha acabado» apunta el nombre de la receta, no el del catálogo | 3 |
| M4 | Al abrir la revisión de «Añadir a la compra» y al cocinar, la lista y la despensa se leen una después de otra | 4 |
| M5 | En la fila de una cosa nueva de la revisión, «Marca (opcional)» y «Sin especificar» se parten en dos líneas | 5 |
| M6 | El selector «Elegir receta» de Comidas se cierra al girar el móvil | 6 |

## Qué no entra

- Los 5 menores **internos** de la misma sección (escucha de la despensa en Comidas, `@StringRes` sin destino, los dos tests que faltan, `loadOrNull` repetido, el listener de la despensa repetido). En concreto: **no toques `PantryLine`** (el aviso de `@StringRes`) ni el `runCatching` de `markCooked` más allá de lo que dice la Tarea 4.
- Los menores de las demás secciones del backlog.
- **Cualquier despliegue** (reglas, beta) y cualquier push: aplazados por el usuario.

## Rulings de planificación

1. **«Listo» atómico (M1).** Una sola transacción, `ShoppingRepository.applyCooked(spaceId, crossOff, finishedPantryIds, restock)`:
   - **tacha** cada artículo de `crossOff` y, si trae `pantryEntry`, la apunta en la despensa si no estaba (con `shoppingItemId`, como `setPurchased`);
   - **quita de la despensa** `finishedPantryIds`;
   - **escribe `restock`** («A la compra») sobre la lista tal como se vio, como `applyWrites`.

   La lista para «A la compra» se lee del servidor justo antes y se calcula con `pantryRestock`, como hace hoy `restockFromPantry`. El catálogo se espera como hoy: el que ya escucha Comidas o, si no ha llegado, 5 s como máximo.
2. **Cambios de otro miembro entretanto (M1).**
   - Un artículo a tachar que ya no existe o ya está comprado **se salta**, sin error.
   - Un artículo que se vuelve a apuntar («A la compra» de algo comprado) y ha cambiado o se ha borrado hace fallar todo: `ItemChanged` o `ItemNotFound`. En Comidas, los dos dan «La lista de la compra ha cambiado…» (`MealUiMessage.ShoppingChanged`); hoy `ItemNotFound` da el error genérico.
3. **Diálogo abierto hasta que se escribe (M1).**
   - `confirmCooked` ya no cierra el diálogo al empezar: lo cierra cuando la escritura ha terminado bien.
   - Mientras guarda, «Listo» está deshabilitado.
   - Si falla, el error sale **dentro** del diálogo, encima de las secciones, y lo marcado se conserva: el diálogo sigue con la misma `CookedReview`, y las casillas viven en `remember(review)`.
   - «Cancelar» sigue disponible. Si se cancela mientras guarda y después falla, el error sale en el aviso de la pantalla, como hoy.
4. **Dos artículos tachados con la misma clave** («Tomates» y «tomate» pendientes a la vez): se marcan los dos, y en la despensa entra una sola entrada, la del primero (hoy pasa lo mismo, en dos transacciones).
5. **Comidas ya no escribe en la despensa por su cuenta.** Desaparecen de `MealsViewModel` la rama `is PantryRepositoryException` y `PantryFailure.toUiMessage()`, que se quedan sin uso. Al cerrar, el menor interno «faltan dos tests…» del backlog se marca «Ya no aplica».
6. **«y» o «e» (M2).**
   - «e» delante del sonido /i/: palabras que empiezan por «i», «í», «hi» o «hí», si no van seguidas de vocal («sal e higos», «queso e ibérico», «pan e hígado»).
   - «y» delante de diptongo («agua y hielo», «sal y hierbabuena») y en los demás casos.
   - Solo cambia la conjunción final de las líneas de 2 y 3 nombres. «y N más» no cambia, porque ningún número empieza por /i/.
   - Dos textos nuevos: `recipes_pantry_missing_two_e` y `recipes_pantry_missing_three_e`.
7. **Nombre del catálogo al apuntar (M3).** `pantryEntryFor(name, data)?.name ?: name`, como «Se acabó» en la Despensa. Solo cambia lo que se apunta a la compra desde «Has cocinado …»; el diálogo sigue mostrando los nombres como están en la receta.
8. **Lecturas a la vez (M4).** `PantryRepository.withCurrentIds(spaceId) { … }` lanza la lectura de la despensa (`currentIdsOrNull`) a la vez que el bloque, que lee la lista.
   - Si el bloque falla, se cancela la lectura de la despensa y el error sale enseguida.
   - Ante fallos, el comportamiento es el de hoy: sin lista no hay revisión (Recetario y Comidas) y al cocinar falta su sección; sin despensa no hay filas «Hay en la despensa» ni «¿Se ha acabado algo?».
9. **Revisión estrecha (M5).** En la revisión de «Añadir a la compra», marca y tienda van una debajo de otra, a todo el ancho (`BrandStorePicker(stacked = true)`). El editor de la Compra, que es ancho, las sigue poniendo lado a lado.
10. **Selector y giro (M6).** `pickerOpen` pasa a `rememberSaveable(meal?.id)`. La búsqueda y el chip del selector ya se guardaban.
11. **Tests instrumentados.** Las Tareas 1, 5 y 6 añaden un test de Compose en `app/src/androidTest`.
    - Se ejecutan en el emulador `Pixel_10`, que arranca el coordinador antes de la Tarea 1.
    - Solo se ejecutan las clases nombradas: `ProfileScreenTest` falla desde antes (backlog, sección `agent/menores-compra-membresia-y-comunes`).
    - Para ver estos tests, `MealEditor` y `ReviewRowContent` pasan de `private` a `internal`.

## Restricciones globales

- **Rutas:**
  - `APP` = `app/src/main/java/com/dlunaunizar/bobitos/`
  - `UT` = `app/src/test/java/com/dlunaunizar/bobitos/`
  - `AT` = `app/src/androidTest/java/com/dlunaunizar/bobitos/`
  - `RES` = `app/src/main/res/values/strings.xml`
- **Comandos de verificación**, en llamadas separadas y sin `sh -c`:
  - **GRADLE:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:assembleDebug :app:ktlintCheck :app:detekt :app:testDebugUnitTest`
  - **JVM (una clase):** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:testDebugUnitTest --tests "<clase completa>"`
  - **INSTR (una clase instrumentada):** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<clase completa>`.
    - Antes, comprueba que hay emulador: `~/Library/Android/sdk/platform-tools/adb devices` debe listar un `emulator-…  device`.
    - Si no lo hay, **no arranques ninguno**: para y devuelve BLOCKED.
    - Nunca lances `connectedDebugAndroidTest` sin `-P…class=`: `ProfileScreenTest` falla desde antes.
  - **NODE:** `npm run test:scripts` (solo el coordinador, al final).
  - **EMU (reglas):** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && export PATH="$JAVA_HOME/bin:$PATH" && npm run test:emulators` (solo el coordinador, al final; este plan no cambia reglas).
  - Si ktlint marca formato: `./gradlew :app:ktlintFormat` (con el mismo `JAVA_HOME`) y revisar el diff antes del commit.
- **Prohibido:**
  - cualquier script contra `bobitos-dev` (`--project dev` o `bobitos-dev`);
  - desplegar reglas, importar al proyecto real, distribuir betas o hacer push;
  - descargar nada;
  - tocar `firestore.rules`, `migrations/`, `tests/firebase-emulators.test.mjs` o `config/detekt/baseline.xml`.
- **Commits con ficheros con nombre:** nunca `git add -A` ni `git add .`.
- **Textos y comentarios en español**, con el estilo de los ficheros vecinos. Textos de la app en `RES`.
- **Tests Kotlin:**
  - JVM puros (JUnit 4 + `kotlinx-coroutines-test`), con dobles a mano (`RecordingShoppingRepository`, `FakePantryRepository`, los de `MealsViewModelTest.kt` y los de `IngredientFakes.kt`); sin mockk ni Robolectric.
  - Los instrumentados, con `androidx.compose.ui.test.junit4.v2.createAndroidComposeRule<ComponentActivity>()`, como `AT/feature/exercises/ExerciseFilterMenusUiTest.kt`.
  - **Orden TDD obligatorio:** test primero y verlo en rojo, después el código. Un fallo de compilación por lo que aún no existe también vale como rojo; copia el fallo en el informe.
- **detekt:**
  - Límites: `ReturnCount` máx. 4, `LongMethod` máx. 80 líneas, `CyclomaticComplexMethod` 15 y `TooManyFunctions` 25, que salta **al llegar a 25**. `MagicNumber` está desactivado.
  - **`FirestoreShoppingRepository` tiene hoy 21 funciones.** La Tarea 1 añade exactamente 3 (`applyCooked`, `markPurchased` y `writePlanned`) y la deja en 24: no añadas ninguna más.
  - **No cambies estas firmas, que están en el baseline:** `ShoppingScreen(...)`, `MealsScreen(...)`, `MealCard(...)`, `RecipesScreen(...)`, `BobitosApp(...)`, `BobitosNavHost(...)` y `runSpaceOperation(...)`.
  - No añadas `catch (Throwable)` nuevos.
- **ktlint:** sin imports sin usar; líneas de 120 caracteres como máximo.
- **Tests existentes:** solo se cambian los que la tarea nombra.

## Review Focus

1. **«Listo» sin conexión o con cualquier fallo:** no se escribe nada. El diálogo sigue abierto, con lo marcado y el error dentro, y al reintentar se escribe una sola vez. → `MealsCookedViewModelTest` y `CookedDialogUiTest` (Tarea 1).
2. **Otro miembro borra o tacha un artículo pendiente entre abrir «Has cocinado …» y pulsar «Listo»:** ese artículo se salta y el resto se escribe, sin error. Si cambia uno de los que se vuelven a apuntar, sale «La lista de la compra ha cambiado…» y el diálogo sigue abierto. → `MealsCookedViewModelTest` (Tarea 1, `ItemNotFound`) y el paso 4 del recorrido de punta a punta (artículo borrado con el Admin SDK).
3. **«y» delante de diptongo, mayúscula o tilde:** «agua y hielo», «sal y hierbabuena», «e Ibérico», «e hígado», y «y N más» sin cambios. → `RecipePantryTest` (Tarea 2).
4. **Lo que se apunta y no está en el catálogo** va tal como está en la receta; con otro número u otras mayúsculas («huevo», «aceite de oliva virgen extra»), va con el nombre del catálogo. → `CookedReviewTest` y `MealsCookedViewModelTest` (Tarea 3).
5. **La lista no se puede leer mientras la despensa aún no ha contestado:** el error sale enseguida, sin esperar a la despensa, y no se queda en «Guardando…». → `MealsViewModelTest` y `RecipesViewModelTest` (Tarea 4).

---

### Task 1: «Listo» en «Has cocinado …» escribe todo a la vez y, si falla, el diálogo sigue abierto

**Files:**
- Modify: `APP/data/repository/ShoppingRepository.kt`
- Modify: `APP/data/repository/FirestoreShoppingRepository.kt`
- Modify: `APP/feature/meals/MealsViewModel.kt`
- Modify: `APP/feature/meals/CookedDialog.kt`
- Modify: `APP/feature/meals/MealsScreen.kt` (solo la llamada a `CookedDialog`)
- Modify: `docs/FUNCTIONAL_SPEC.md` (PANTRY-05)
- Modify (test): `UT/feature/common/RecordingShoppingRepository.kt`, `UT/feature/shopping/ShoppingViewModelTest.kt` (`FakeShoppingRepository`), `UT/feature/meals/MealsCookedViewModelTest.kt`
- Create (test): `AT/feature/meals/CookedDialogUiTest.kt`

**Interfaces:**
- Consumes:
  - `pantryEntryFor(name, data): PantryEntry?`, `pantryRestock(names, items, data): PantryRestock` (`APP/feature/common/PantryShopping.kt`);
  - `CookedReview.writesFor(choice): CookedWrites` y `CookedWrites(crossOff, finishedIds, restockNames)` (`APP/feature/meals/CookedReview.kt`).
- Produces:
  - `data class ShoppingCrossOff(val itemId: String, val pantryEntry: PantryEntry?)` (`APP/data/repository/ShoppingRepository.kt`);
  - `suspend fun ShoppingRepository.applyCooked(spaceId: String, crossOff: List<ShoppingCrossOff>, finishedPantryIds: List<String>, restock: List<ShoppingWrite>)`;
  - `CookedDialog(review, saving: Boolean, errorMessage: String?, onConfirm, onDismiss)`;
  - en `RecordingShoppingRepository`: `cookedCalls: MutableList<CookedCall>`, `cookedFailure: Throwable?` y `cookedGate: CompletableDeferred<Unit>?`, con `internal data class CookedCall(crossOff, finishedPantryIds, restock)`. La Tarea 3 usa `confirmCooked` tal como queda aquí.

- [ ] **Step 1: La transacción nueva (sin test JVM: Firestore no se prueba en la JVM; la verifica el recorrido de punta a punta).**

En `APP/data/repository/ShoppingRepository.kt`, añade a la interfaz, después de `setPurchased`:

```kotlin
    /**
     * «Listo» en «Has cocinado …», en una sola transacción (todo o nada): marca como comprados los artículos de
     * [crossOff] y apunta en la despensa su entrada, si la traen y no estaba (como [setPurchased]); quita de la
     * despensa [finishedPantryIds]; y escribe [restock] sobre la lista tal como se vio (como [applyWrites]). Un artículo
     * de [crossOff] que ya no existe o ya está comprado se salta. Si otro miembro ha cambiado o borrado un artículo de
     * [restock], no se escribe nada y falla con [ShoppingFailure.ItemChanged] o [ShoppingFailure.ItemNotFound].
     */
    suspend fun applyCooked(
        spaceId: String,
        crossOff: List<ShoppingCrossOff>,
        finishedPantryIds: List<String>,
        restock: List<ShoppingWrite>,
    )
```

y, después de la interfaz `ShoppingWrite`:

```kotlin
/** Un artículo que se tacha al cocinar y la entrada de la despensa que trae si es un ingrediente reconocido. */
data class ShoppingCrossOff(val itemId: String, val pantryEntry: PantryEntry?)
```

En `APP/data/repository/FirestoreShoppingRepository.kt`:

(a) En `applyWrites`, sustituye el último `planned.forEach { plan -> if (plan.seen == null) { … } else { … } }` por:

```kotlin
            planned.forEach { plan -> writePlanned(transaction, plan, user) }
```

y añade, justo después de `planWrite`:

```kotlin
    // Un alta en su documento nuevo o un cambio del artículo tal como se vio (ver ShoppingWrite).
    private fun writePlanned(transaction: Transaction, plan: PlannedWrite, user: AuthUser) {
        if (plan.seen == null) {
            transaction.set(plan.reference, newItemFields(user, plan.values))
        } else {
            transaction.update(plan.reference, changedItemFields(user, plan.values, rename = false, relist = plan.relist))
        }
    }
```

(b) En `setPurchased`, sustituye el `transaction.update(reference, FIELD_PURCHASED, purchased, …, FIELD_UPDATED_AT, FieldValue.serverTimestamp())` entero por `markPurchased(transaction, reference, purchased, user)`, y añade, justo después de `setPurchased`:

```kotlin
    // Marca o desmarca el artículo: quién lo compró y cuándo (nada, al desmarcar) y quién lo ha cambiado.
    private fun markPurchased(
        transaction: Transaction,
        reference: DocumentReference,
        purchased: Boolean,
        user: AuthUser,
    ) {
        transaction.update(
            reference,
            FIELD_PURCHASED,
            purchased,
            FIELD_PURCHASED_BY,
            if (purchased) user.id else null,
            FIELD_PURCHASED_BY_NAME,
            if (purchased) user.shoppingDisplayName else null,
            FIELD_PURCHASED_AT,
            if (purchased) FieldValue.serverTimestamp() else null,
            FIELD_UPDATED_BY,
            user.id,
            FIELD_UPDATED_AT,
            FieldValue.serverTimestamp(),
        )
    }
```

(c) Añade, justo después de `markPurchased`:

```kotlin
    override suspend fun applyCooked(
        spaceId: String,
        crossOff: List<ShoppingCrossOff>,
        finishedPantryIds: List<String>,
        restock: List<ShoppingWrite>,
    ) = runShoppingOperation {
        val user = requireVerifiedUser()
        val entries = crossOff.map { it.pantryEntry?.let(::validPantryEntryOrNull) }
        val planned = restock.map { planWrite(spaceId, it) }
        val spaceReference = firestore.collection(SPACES).document(spaceId)
        val pantry = firestore.pantryCollection(spaceId)

        firestore.runTransaction { transaction ->
            // Firestore exige hacer todas las lecturas antes de la primera escritura.
            if (!transaction.get(spaceReference).exists()) {
                throw ShoppingRepositoryException(ShoppingFailure.SpaceNotFound)
            }
            // Lo que ya no está en la lista (otro miembro lo ha borrado) o ya está comprado no se tacha.
            val crossed = crossOff.map { transaction.get(itemsCollection(spaceId).document(it.itemId)) }
                .zip(entries)
                .filter { (item, _) -> item.getBoolean(FIELD_PURCHASED) == false }
            // Una entrada por id: si dos artículos traen la misma («Tomates» y «tomate»), la apunta el primero.
            val pantryReads = crossed.mapNotNull { (item, entry) -> entry?.let { item to it } }
                .distinctBy { (_, entry) -> pantryId(entry.name) }
                .map { (item, entry) -> Triple(item, entry, readPantry(transaction, spaceId, entry.name)) }
            planned.forEach { plan -> plan.seen?.let { requireUnchanged(transaction.get(plan.reference), it) } }
            crossed.forEach { (item, _) -> markPurchased(transaction, item.reference, true, user) }
            pantryReads.forEach { (item, entry, snapshot) ->
                snapshot?.let { writePantry(transaction, it, entry, user.id, item) }
            }
            finishedPantryIds.filter(String::isNotEmpty).distinct().forEach { transaction.delete(pantry.document(it)) }
            planned.forEach { plan -> writePlanned(transaction, plan, user) }
        }.await()
        Unit
    }
```

En la clase quedan 24 funciones; no añadas más (ver «Restricciones globales»).

(d) En los dobles:
- En `UT/feature/shopping/ShoppingViewModelTest.kt`, dentro de `FakeShoppingRepository`, añade la implementación vacía, con los imports que falten (`ShoppingCrossOff`, `ShoppingWrite`):

```kotlin
    override suspend fun applyCooked(
        spaceId: String,
        crossOff: List<ShoppingCrossOff>,
        finishedPantryIds: List<String>,
        restock: List<ShoppingWrite>,
    ) = Unit
```

- En `UT/feature/common/RecordingShoppingRepository.kt`:
  - añade los campos (junto a `applyFailure`):

```kotlin
    val cookedCalls = mutableListOf<CookedCall>()
    var cookedFailure: Throwable? = null

    // Si hay puerta, applyCooked espera a que se complete (simula una escritura lenta).
    var cookedGate: CompletableDeferred<Unit>? = null
```

  - sustituye `applyWrites` por esta versión, más `applyCooked` y `record`:

```kotlin
    // Cada lote queda en appliedBatches y, además, cada escritura en las listas de altas, ediciones y
    // reapuntados, para que los tests comprueben lo escrito sin desmontar el lote.
    override suspend fun applyWrites(spaceId: String, writes: List<ShoppingWrite>) {
        applyFailure?.let { throw it }
        appliedBatches += writes
        record(writes)
    }

    // Como el real, todo o nada: con fallo no registra nada. Cada llamada queda en cookedCalls y, además, lo tachado en
    // purchasedIds y purchasedEntries y lo apuntado en las listas de altas y reapuntados (no en appliedBatches).
    override suspend fun applyCooked(
        spaceId: String,
        crossOff: List<ShoppingCrossOff>,
        finishedPantryIds: List<String>,
        restock: List<ShoppingWrite>,
    ) {
        cookedGate?.await()
        cookedFailure?.let { throw it }
        cookedCalls += CookedCall(crossOff, finishedPantryIds, restock)
        crossOff.forEach {
            purchasedIds += it.itemId
            purchasedEntries += it.pantryEntry
        }
        record(restock)
    }

    private fun record(writes: List<ShoppingWrite>) {
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

  - al final del fichero (import `com.dlunaunizar.bobitos.data.repository.ShoppingCrossOff`):

```kotlin
/** Una llamada a [ShoppingRepository.applyCooked]. */
internal data class CookedCall(
    val crossOff: List<ShoppingCrossOff>,
    val finishedPantryIds: List<String>,
    val restock: List<ShoppingWrite>,
)
```

Comprueba que compila: **JVM** de `com.dlunaunizar.bobitos.feature.meals.MealsCookedViewModelTest`. Debe quedar en verde, porque aún no hay tests nuevos.

- [ ] **Step 2: Tests del ViewModel.** En `UT/feature/meals/MealsCookedViewModelTest.kt`, con los imports que falten (`assertSame`, `CompletableDeferred`, `ShoppingCrossOff`, `ShoppingWrite`, `ShoppingFailure`, `ShoppingRepositoryException`):

(a) Cambia los tres tests existentes que miran la despensa del doble:
- en `finishing after cooking removes it from the pantry and puts it on the shopping list`, sustituye `assertEquals(listOf(listOf(pantryId("Leche"))), pantryRepository.removed)` por `assertEquals(listOf(pantryId("Leche")), shoppingRepository.cookedCalls.single().finishedPantryIds)`;
- en `finishing without restock only removes it from the pantry`, la misma sustitución, y añade `assertTrue(shoppingRepository.cookedCalls.single().restock.isEmpty())`;
- en `confirming the cooking dialog with nothing marked writes nothing`, sustituye `assertTrue(pantryRepository.removed.isEmpty())` por `assertTrue(shoppingRepository.cookedCalls.isEmpty())`.

(b) Añade:

```kotlin
    @Test
    fun `done writes what is crossed off, what has finished and the restock in a single write`() =
        runTest(mainDispatcherRule.testDispatcher) {
            ingredientRepository.catalogState.value = listOf(CatalogIngredient("arroz", "Arroz"))
            recipeRepository.mineState.value =
                listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"), Ingredient("Leche", "1", "l"))))
            shoppingRepository.itemsState.value = listOf(shoppingItem("s1", "arroz"))
            pantryRepository.serverIds = setOf(pantryId("Leche"))
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Arroz con leche").copy(recipeId = "r1"))
            advanceUntilIdle()
            val review = viewModel.uiState.value.cooked!!

            viewModel.confirmCooked(review.defaultChoice().toggleFinished(pantryId("Leche")))
            advanceUntilIdle()

            val call = shoppingRepository.cookedCalls.single()
            assertEquals(listOf(ShoppingCrossOff("s1", PantryEntry("Arroz", null))), call.crossOff)
            assertEquals(listOf(pantryId("Leche")), call.finishedPantryIds)
            assertEquals(listOf("Leche"), call.restock.map { (it as ShoppingWrite.Add).name })
            assertTrue(pantryRepository.removed.isEmpty())
            assertTrue(shoppingRepository.appliedBatches.isEmpty())
            assertEquals(MealUiMessage.CookedReviewApplied, viewModel.uiState.value.notice)
            assertNull(viewModel.uiState.value.cooked)
        }

    @Test
    fun `if done fails nothing is written and the dialog stays open with the error until a retry works`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
            shoppingRepository.itemsState.value = listOf(shoppingItem("s1", "Arroz"))
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"))
            advanceUntilIdle()
            val review = viewModel.uiState.value.cooked!!
            shoppingRepository.cookedFailure = ShoppingRepositoryException(ShoppingFailure.Network)

            viewModel.confirmCooked(review.defaultChoice())
            advanceUntilIdle()

            assertSame(review, viewModel.uiState.value.cooked)
            assertEquals(MealUiMessage.NetworkError, viewModel.uiState.value.error)
            assertNull(viewModel.uiState.value.notice)
            assertFalse(viewModel.uiState.value.isSaving)
            assertTrue(shoppingRepository.purchasedIds.isEmpty())

            shoppingRepository.cookedFailure = null
            viewModel.confirmCooked(review.defaultChoice())
            advanceUntilIdle()

            assertEquals(1, shoppingRepository.cookedCalls.size)
            assertNull(viewModel.uiState.value.cooked)
            assertNull(viewModel.uiState.value.error)
            assertEquals(MealUiMessage.CookedReviewApplied, viewModel.uiState.value.notice)
        }

    @Test
    fun `while done is saving the dialog stays open and a second tap does not write twice`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
            shoppingRepository.itemsState.value = listOf(shoppingItem("s1", "Arroz"))
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"))
            advanceUntilIdle()
            val review = viewModel.uiState.value.cooked!!
            shoppingRepository.cookedGate = CompletableDeferred()

            viewModel.confirmCooked(review.defaultChoice())
            viewModel.confirmCooked(review.defaultChoice())
            runCurrent()

            assertSame(review, viewModel.uiState.value.cooked)
            assertTrue(viewModel.uiState.value.isSaving)
            shoppingRepository.cookedGate?.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, shoppingRepository.cookedCalls.size)
            assertNull(viewModel.uiState.value.cooked)
        }

    @Test
    fun `if an item to put back on the list was deleted meanwhile done says the list changed`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
            pantryRepository.serverIds = setOf(pantryId("Leche"))
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
            advanceUntilIdle()
            val review = viewModel.uiState.value.cooked!!
            shoppingRepository.cookedFailure = ShoppingRepositoryException(ShoppingFailure.ItemNotFound)

            viewModel.confirmCooked(review.defaultChoice().toggleFinished(pantryId("Leche")))
            advanceUntilIdle()

            assertEquals(MealUiMessage.ShoppingChanged, viewModel.uiState.value.error)
            assertSame(review, viewModel.uiState.value.cooked)
        }
```

- [ ] **Step 3: Ejecutarlos en rojo.** **JVM** de `com.dlunaunizar.bobitos.feature.meals.MealsCookedViewModelTest`. Esperado: FAIL en los 4 tests nuevos y en 2 de los 3 cambiados. `confirmCooked` aún tacha con `setPurchased` y quita con `pantryRepository.remove`: `cookedCalls` está vacío, el diálogo se cierra al empezar y `ItemNotFound` no llega. El de «nothing marked» sigue en verde, porque no escribe nada. Copia los fallos en el informe.

- [ ] **Step 4: El ViewModel.** En `APP/feature/meals/MealsViewModel.kt`:

(a) Sustituye el comentario y el cuerpo de `confirmCooked` por:

```kotlin
    // «Listo» en «Has cocinado …», en una sola escritura (ShoppingRepository.applyCooked): tacha lo marcado (si es un
    // ingrediente reconocido, entra en la despensa, como al marcar en la Compra), quita de la despensa lo que se ha
    // acabado y, si se pide, lo apunta a la compra. El diálogo sigue abierto hasta que se escribe: si falla, no se ha
    // escrito nada y conserva lo marcado.
    fun confirmCooked(choice: CookedChoice) {
        val spaceId = observedSpaceId ?: return
        val writes = mutableUiState.value.cooked?.writesFor(choice) ?: return
        if (writes.isEmpty) {
            mutableUiState.update { it.copy(cooked = null) }
            return
        }
        runAction(MealUiMessage.CookedReviewApplied) {
            val needsCatalog = writes.crossOff.isNotEmpty() || writes.restockNames.isNotEmpty()
            val data = if (needsCatalog) ingredientData() else IngredientShoppingData()
            // «A la compra» sobre la lista del servidor: con una caché vieja se duplicaría el artículo.
            val restock = if (writes.restockNames.isEmpty()) {
                emptyList()
            } else {
                pantryRestock(writes.restockNames, shoppingRepository.currentItems(spaceId), data).writes
            }
            shoppingRepository.applyCooked(
                spaceId,
                writes.crossOff.map { ShoppingCrossOff(it.id, pantryEntryFor(it.name, data)) },
                writes.finishedIds,
                restock,
            )
            mutableUiState.update { it.copy(cooked = null) }
        }
    }
```

(b) En `ShoppingFailure.toUiMessage()`, `ItemNotFound` pasa del grupo de `UnexpectedError` al de `ShoppingChanged`:

```kotlin
    // En Comidas, ItemNotFound solo llega de «A la compra» al cocinar: un comprado que se vuelve a apuntar y otro miembro
    // ha borrado mientras tanto.
    ShoppingFailure.ItemChanged,
    ShoppingFailure.ItemNotFound,
    -> MealUiMessage.ShoppingChanged
```

(c) Quita lo que se queda sin uso: la rama `is PantryRepositoryException -> failure.toUiMessage()` de `Throwable.toUiMessage()`, la función `PantryFailure.toUiMessage()` entera y los imports `PantryFailure`, `PantryRepositoryException` y `restockFromPantry`. Añade los imports `ShoppingCrossOff` y `pantryRestock`.

- [ ] **Step 5: Verlos en verde.** **JVM** de `…feature.meals.MealsCookedViewModelTest` y de `…feature.meals.MealsViewModelTest` (este último ya usa `confirmCooked` y `purchasedIds`; debe seguir en verde sin cambios). Esperado: PASS.

- [ ] **Step 6: Test del diálogo (instrumentado).** Crea `AT/feature/meals/CookedDialogUiTest.kt`:

```kotlin
package com.dlunaunizar.bobitos.feature.meals

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class CookedDialogUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val review = CookedReview(
        mealName = "Lentejas",
        crossOff = listOf(
            ShoppingItem(
                id = "s1",
                name = "Lentejas",
                quantity = null,
                notes = null,
                purchased = false,
                createdBy = "u1",
                createdByName = "Ana",
                createdAt = Instant.EPOCH,
                updatedBy = "u1",
                updatedAt = Instant.EPOCH,
                purchasedBy = null,
                purchasedByName = null,
                purchasedAt = null,
            ),
        ),
        finished = emptyList(),
    )

    private fun setDialog(saving: Boolean, errorMessage: String?) {
        compose.setContent {
            BobitosTheme {
                CookedDialog(
                    review = review,
                    saving = saving,
                    errorMessage = errorMessage,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
    }

    private fun done() = compose.activity.getString(R.string.meals_cooked_done)

    @Test fun anErrorShowsInsideTheDialogAndDoneCanBeRetried() {
        setDialog(saving = false, errorMessage = "Sin conexión.")
        compose.onNodeWithText("Sin conexión.").assertIsDisplayed()
        compose.onNodeWithText(done()).assertIsEnabled()
    }

    @Test fun doneCannotBePressedWhileSaving() {
        setDialog(saving = true, errorMessage = null)
        compose.onNodeWithText(done()).assertIsNotEnabled()
    }
}
```

**INSTR** de `com.dlunaunizar.bobitos.feature.meals.CookedDialogUiTest`. Esperado: falla al compilar (`CookedDialog` no tiene `saving` ni `errorMessage`). Copia el fallo en el informe.

- [ ] **Step 7: El diálogo y la pantalla.** En `APP/feature/meals/CookedDialog.kt`:
  - la firma pasa a `CookedDialog(review: CookedReview, saving: Boolean, errorMessage: String?, onConfirm: (CookedChoice) -> Unit, onDismiss: () -> Unit)`, con un parámetro por línea;
  - el comentario de cabecera gana una frase: «Mientras se guarda, «Listo» no se puede pulsar; si falla, el error sale dentro y lo marcado se conserva (el diálogo sigue con la misma revisión).»;
  - el `text` envuelve la columna desplazable actual en una `Column` con el error encima, para que se vea aunque la lista sea larga:

```kotlin
        text = {
            Column {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = Spacing.sm),
                    )
                }
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    // … las secciones, sin cambios …
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(choice) }, enabled = !saving) {
                Text(stringResource(R.string.meals_cooked_done))
            }
        },
```

En `APP/feature/meals/MealsScreen.kt`, la llamada (hoy `CookedDialog(review = review, onConfirm = viewModel::confirmCooked, onDismiss = viewModel::dismissCooked)`) pasa a:

```kotlin
    state.cooked?.let { review ->
        CookedDialog(
            review = review,
            saving = state.isSaving,
            errorMessage = state.error?.let { stringResource(it.stringResourceId) },
            onConfirm = viewModel::confirmCooked,
            onDismiss = viewModel::dismissCooked,
        )
    }
```

No cambies la firma de `MealsScreen`.

- [ ] **Step 8: Documentación.** En `docs/FUNCTIONAL_SPEC.md`, «PANTRY-05. Al cocinar», añade al final:

```markdown
- «Listo» lo escribe todo a la vez: si algo falla (por ejemplo, sin conexión), no se escribe nada y el diálogo sigue abierto, con lo marcado y el motivo, para volver a intentarlo. Lo que otro miembro ya ha tachado o ha quitado de la lista se salta.
```

- [ ] **Step 9: Verde completo.**
  - **INSTR** de `…feature.meals.CookedDialogUiTest`. Esperado: PASS (2 tests).
  - **GRADLE.** Esperado: BUILD SUCCESSFUL.
  - Si detekt marca `CyclomaticComplexMethod` o `NestedBlockDepth` en `applyCooked`, no añadas funciones a la clase: pasa el cálculo de `crossed` y `pantryReads` a variables locales fuera de las lambdas anidadas y vuelve a pasar GRADLE.

- [ ] **Step 10: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/data/repository/ShoppingRepository.kt \
  app/src/main/java/com/dlunaunizar/bobitos/data/repository/FirestoreShoppingRepository.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealsViewModel.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/meals/CookedDialog.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealsScreen.kt \
  app/src/test/java/com/dlunaunizar/bobitos/feature/common/RecordingShoppingRepository.kt \
  app/src/test/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingViewModelTest.kt \
  app/src/test/java/com/dlunaunizar/bobitos/feature/meals/MealsCookedViewModelTest.kt \
  app/src/androidTest/java/com/dlunaunizar/bobitos/feature/meals/CookedDialogUiTest.kt \
  docs/FUNCTIONAL_SPEC.md
git commit -m "fix(despensa): «Listo» al cocinar escribe todo a la vez y, si falla, el diálogo conserva lo marcado"
```

---

### Task 2: «e» en vez de «y» delante del sonido /i/ en la línea de lo que falta

**Files:**
- Modify: `APP/feature/common/RecipePantry.kt`
- Modify: `RES`
- Modify (test): `UT/feature/common/RecipePantryTest.kt`
- Create (test): `UT/feature/common/RecipePantryStringsTest.kt`

**Interfaces:**
- Consumes: `pantryLine(missing: List<String>): PantryLine`, `PantryLine(res, args)` (`APP/feature/common/RecipePantry.kt`).
- Produces: `internal fun takesE(word: String): Boolean`; los textos `R.string.recipes_pantry_missing_two_e` y `R.string.recipes_pantry_missing_three_e`.

- [ ] **Step 1: Test de los textos.** Crea `UT/feature/common/RecipePantryStringsTest.kt`:

```kotlin
package com.dlunaunizar.bobitos.feature.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class RecipePantryStringsTest {
    private val xml = File("src/main/res/values/strings.xml").readText()

    private fun text(name: String) = Regex("""<string name="$name">(.*?)</string>""").find(xml)!!.groupValues[1]

    @Test
    fun `the lines with e only change the conjunction`() {
        assertEquals("Faltan 2: %1\$s e %2\$s", text("recipes_pantry_missing_two_e"))
        assertEquals("Faltan 3: %1\$s, %2\$s e %3\$s", text("recipes_pantry_missing_three_e"))
    }
}
```

**JVM** de `com.dlunaunizar.bobitos.feature.common.RecipePantryStringsTest`. Esperado: FAIL (`NullPointerException`: los textos no existen).

- [ ] **Step 2: Los textos.** En `RES`, justo después de `recipes_pantry_missing_three`:

```xml
    <string name="recipes_pantry_missing_two_e">Faltan 2: %1$s e %2$s</string>
    <string name="recipes_pantry_missing_three_e">Faltan 3: %1$s, %2$s e %3$s</string>
```

**JVM** de `…feature.common.RecipePantryStringsTest`. Esperado: PASS.

- [ ] **Step 3: Tests de la línea.** Añade a `UT/feature/common/RecipePantryTest.kt` (con los imports `assertTrue` y `assertFalse` si faltan):

```kotlin
    @Test
    fun `before the sound i the last conjunction is e`() {
        assertEquals(
            PantryLine(R.string.recipes_pantry_missing_two_e, listOf("sal", "higos")),
            pantryLine(listOf("Sal", "Higos")),
        )
        assertEquals(
            PantryLine(R.string.recipes_pantry_missing_three_e, listOf("huevos", "leche", "ibérico")),
            pantryLine(listOf("Huevos", "Leche", "Ibérico")),
        )
    }

    @Test
    fun `before a diphthong or any other sound the conjunction stays y`() {
        assertEquals(
            PantryLine(R.string.recipes_pantry_missing_two, listOf("agua", "hielo")),
            pantryLine(listOf("Agua", "Hielo")),
        )
        assertEquals(
            PantryLine(R.string.recipes_pantry_missing_three, listOf("sal", "pan", "hierbabuena")),
            pantryLine(listOf("Sal", "Pan", "Hierbabuena")),
        )
        assertEquals(
            PantryLine(R.string.recipes_pantry_missing_more, listOf(4, "sal", "pan", 2)),
            pantryLine(listOf("Sal", "Pan", "Higos", "Hinojo")),
        )
    }

    @Test
    fun `takesE follows the sound, not the spelling`() {
        listOf("higos", "Hinojo", "ibérico", "Ídolo", "hígado", "IPA", "hi").forEach { assertTrue(it, takesE(it)) }
        listOf("hielo", "hierbabuena", "hierro", "yogur", "huevos", "leche", "", "  ").forEach {
            assertFalse(it, takesE(it))
        }
    }
```

**JVM** de `com.dlunaunizar.bobitos.feature.common.RecipePantryTest`. Esperado: falla al compilar (`takesE` no existe). Copia el fallo.

- [ ] **Step 4: La regla.** En `APP/feature/common/RecipePantry.kt`, sustituye `pantryLine` (con su KDoc) por:

```kotlin
/**
 * «Lo tienes todo», «Falta: huevos», «Faltan 2: huevos y leche», «Faltan 3: huevos, leche y harina» o, con más de tres,
 * «Faltan 5: huevos, leche y 3 más». Delante del sonido /i/ la conjunción es «e» («Faltan 2: sal e higos»). Los nombres
 * van con la inicial en minúscula, salvo las siglas («AOVE»).
 */
internal fun pantryLine(missing: List<String>): PantryLine {
    val names = missing.map(::lowerInitial)
    val e = names.lastOrNull()?.let(::takesE) == true
    return when (names.size) {
        0 -> PantryLine(R.string.recipes_pantry_all)
        1 -> PantryLine(R.string.recipes_pantry_missing_one, names)
        2 -> PantryLine(if (e) R.string.recipes_pantry_missing_two_e else R.string.recipes_pantry_missing_two, names)
        3 -> PantryLine(if (e) R.string.recipes_pantry_missing_three_e else R.string.recipes_pantry_missing_three, names)
        else -> PantryLine(R.string.recipes_pantry_missing_more, listOf(names.size, names[0], names[1], names.size - 2))
    }
}

/**
 * Si delante de [word] la conjunción es «e» y no «y»: ante el sonido /i/ («sal e higos», «pan e hinojo», «queso e
 * ibérico»), pero no ante un diptongo («agua y hielo», «sal y hierbabuena»).
 */
internal fun takesE(word: String): Boolean {
    val sound = word.trim().lowercase().removePrefix("h")
    return when (sound.firstOrNull()) {
        'í' -> true
        'i' -> sound.getOrNull(1)?.let { it !in "aeiouáéíóú" } ?: true
        else -> false
    }
}
```

No toques `PantryLine` (su `@StringRes` es un menor interno que se queda en el backlog).

- [ ] **Step 5: Verde.** **JVM** de `…feature.common.RecipePantryTest` y de `…feature.common.RecipePantryStringsTest`. Esperado: PASS. Después, **GRADLE**. Esperado: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/common/RecipePantry.kt \
  app/src/main/res/values/strings.xml \
  app/src/test/java/com/dlunaunizar/bobitos/feature/common/RecipePantryTest.kt \
  app/src/test/java/com/dlunaunizar/bobitos/feature/common/RecipePantryStringsTest.kt
git commit -m "fix(despensa): la línea de lo que falta usa «e» delante del sonido /i/ («sal e higos»)"
```

---

### Task 3: Lo que se ha acabado se apunta a la compra con el nombre del catálogo

**Files:**
- Modify: `APP/feature/meals/CookedReview.kt`
- Modify: `APP/feature/meals/MealsViewModel.kt` (una línea de `confirmCooked`)
- Modify: `docs/FUNCTIONAL_SPEC.md` (PANTRY-05)
- Modify (test): `UT/feature/meals/CookedReviewTest.kt`, `UT/feature/meals/MealsCookedViewModelTest.kt`

**Interfaces:**
- Consumes: `confirmCooked` tal como lo deja la Tarea 1; `pantryEntryFor(name, data)` (`APP/feature/common/PantryShopping.kt`); `CookedWrites(crossOff, finishedIds, restockNames)`.
- Produces: `internal fun CookedWrites.restockNamesIn(data: IngredientShoppingData): List<String>`.

- [ ] **Step 1: Test del ViewModel.** Añade a `UT/feature/meals/MealsCookedViewModelTest.kt`:

```kotlin
    @Test
    fun `what has finished goes on the list with the catalog name`() = runTest(mainDispatcherRule.testDispatcher) {
        ingredientRepository.catalogState.value =
            listOf(CatalogIngredient("aceite-de-oliva-virgen-extra", "Aceite de oliva virgen extra"))
        recipeRepository.mineState.value =
            listOf(recipe("r1", listOf(Ingredient("aceite de oliva virgen extra", "2", "cucharadas"))))
        pantryRepository.serverIds = setOf(pantryId("aceite de oliva virgen extra"))
        viewModel.observe("home")
        advanceUntilIdle()
        viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.CENA, "Pisto").copy(recipeId = "r1"))
        advanceUntilIdle()
        val review = viewModel.uiState.value.cooked!!

        viewModel.confirmCooked(review.defaultChoice().toggleFinished(pantryId("aceite de oliva virgen extra")))
        advanceUntilIdle()

        assertEquals(listOf("Aceite de oliva virgen extra"), shoppingRepository.addedNames)
    }
```

- [ ] **Step 2: Ejecutarlo en rojo.** **JVM** de `com.dlunaunizar.bobitos.feature.meals.MealsCookedViewModelTest`. Esperado: FAIL en el test nuevo (`expected:<[Aceite de oliva virgen extra]> but was:<[aceite de oliva virgen extra]>`). Copia el fallo.

- [ ] **Step 3: Test de la función pura.** Añade a `UT/feature/meals/CookedReviewTest.kt` (imports `CatalogIngredient` y `com.dlunaunizar.bobitos.feature.common.IngredientShoppingData`):

```kotlin
    @Test
    fun `what is put on the list takes the catalog name when it is recognized and stays as written if not`() {
        val data = IngredientShoppingData(
            listOf(
                CatalogIngredient("aceite-de-oliva-virgen-extra", "Aceite de oliva virgen extra"),
                CatalogIngredient("huevos", "Huevos"),
            ),
        )
        val writes = CookedWrites(
            crossOff = emptyList(),
            finishedIds = listOf("a", "b", "c"),
            restockNames = listOf("aceite de oliva virgen extra", "huevo", "Sal de Añana"),
        )

        assertEquals(
            listOf("Aceite de oliva virgen extra", "Huevos", "Sal de Añana"),
            writes.restockNamesIn(data),
        )
    }
```

**JVM** de `com.dlunaunizar.bobitos.feature.meals.CookedReviewTest`. Esperado: falla al compilar (`restockNamesIn` no existe).

- [ ] **Step 4: El código.** En `APP/feature/meals/CookedReview.kt` (imports `com.dlunaunizar.bobitos.feature.common.IngredientShoppingData` y `com.dlunaunizar.bobitos.feature.common.pantryEntryFor`), al final:

```kotlin
/**
 * Lo que se apunta a la compra: con el nombre del catálogo si se reconoce («Aceite de oliva virgen extra»), como al
 * decir «Se acabó» en la Despensa; si no, como se escribe en la receta.
 */
internal fun CookedWrites.restockNamesIn(data: IngredientShoppingData): List<String> =
    restockNames.map { pantryEntryFor(it, data)?.name ?: it }
```

En `APP/feature/meals/MealsViewModel.kt`, dentro de `confirmCooked`, la llamada a `pantryRestock` pasa a usar `writes.restockNamesIn(data)` en lugar de `writes.restockNames`.

En `docs/FUNCTIONAL_SPEC.md`, «PANTRY-05. Al cocinar», en la línea «Lo que se marca como acabado sale de la despensa y, con «Apuntar a la compra lo que se ha acabado» (marcada por defecto), se apunta en la Compra como en PANTRY-03.», añade al final: « Si es un ingrediente, se apunta con el nombre del catálogo.»

- [ ] **Step 5: Verde.** **JVM** de `…feature.meals.CookedReviewTest` y de `…feature.meals.MealsCookedViewModelTest`. Esperado: PASS. Después, **GRADLE**. Esperado: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/meals/CookedReview.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealsViewModel.kt \
  app/src/test/java/com/dlunaunizar/bobitos/feature/meals/CookedReviewTest.kt \
  app/src/test/java/com/dlunaunizar/bobitos/feature/meals/MealsCookedViewModelTest.kt \
  docs/FUNCTIONAL_SPEC.md
git commit -m "fix(despensa): lo que se acaba al cocinar se apunta a la compra con el nombre del catálogo"
```

---

### Task 4: La lista y la despensa se leen a la vez al abrir la revisión y al cocinar

**Files:**
- Modify: `APP/feature/common/PantryShopping.kt`
- Modify: `APP/feature/meals/MealsViewModel.kt` (`prepareIngredientReview` y `markCooked`)
- Modify: `APP/feature/recipes/RecipesViewModel.kt` (`addToShopping`)
- Modify (test): `UT/feature/common/FakePantryRepository.kt`, `UT/feature/meals/MealsViewModelTest.kt`, `UT/feature/meals/MealsCookedViewModelTest.kt`, `UT/feature/recipes/RecipesViewModelTest.kt`

**Interfaces:**
- Consumes: `PantryRepository.currentIdsOrNull(spaceId): Set<String>?` (`APP/feature/common/PantryShopping.kt`); `RecordingShoppingRepository.currentItemsGate` y `currentItemsCalls`, que ya existen.
- Produces:
  - `internal suspend fun <T> PantryRepository.withCurrentIds(spaceId: String, readItems: suspend () -> T): Pair<T, Set<String>?>`;
  - en `FakePantryRepository`: `currentIdsGate: CompletableDeferred<Unit>?` y `currentIdsCalls: Int`.

- [ ] **Step 1: El doble.** En `UT/feature/common/FakePantryRepository.kt`, añade los campos y cuenta y espera en `currentIds`:

```kotlin
    // Si hay puerta, currentIds espera a que se complete (simula una lectura lenta); cuenta las lecturas.
    var currentIdsGate: CompletableDeferred<Unit>? = null
    var currentIdsCalls = 0
```

```kotlin
    override suspend fun currentIds(spaceId: String): Set<String> {
        currentIdsCalls++
        currentIdsGate?.await()
        itemsFailure?.let { throw it }
        return serverIds ?: itemsState.value.mapTo(HashSet(), PantryItem::id)
    }
```

- [ ] **Step 2: Tests.** Añade, con los imports que falten (`CompletableDeferred`, `runCurrent`, `ShoppingFailure`, `ShoppingRepositoryException`, `ReviewRowKind`, `pantryId`):

En `UT/feature/meals/MealsViewModelTest.kt`:

```kotlin
    @Test
    fun `the meal review reads the list and the pantry at the same time`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
            pantryRepository.serverIds = setOf(pantryId("Arroz"))
            viewModel.observe("home")
            advanceUntilIdle()
            shoppingRepository.currentItemsGate = CompletableDeferred()

            viewModel.addIngredientsToShopping(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"))
            runCurrent()

            assertEquals(1, shoppingRepository.currentItemsCalls)
            assertEquals(1, pantryRepository.currentIdsCalls)
            shoppingRepository.currentItemsGate?.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf(ReviewRowKind.IN_PANTRY), viewModel.uiState.value.ingredientReview?.map { it.kind })
        }

    // Ya pasa hoy (la lista se lee primero); protege la versión en paralelo de esperar a la despensa.
    @Test
    fun `if the list cannot be read the meal review fails without waiting for the pantry`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
            viewModel.observe("home")
            advanceUntilIdle()
            shoppingRepository.itemsFailure = ShoppingRepositoryException(ShoppingFailure.Network)
            pantryRepository.currentIdsGate = CompletableDeferred()

            viewModel.addIngredientsToShopping(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"))
            runCurrent()

            assertEquals(MealUiMessage.NetworkError, viewModel.uiState.value.error)
            assertNull(viewModel.uiState.value.ingredientReview)
            assertFalse(viewModel.uiState.value.isSaving)
        }
```

En `UT/feature/meals/MealsCookedViewModelTest.kt`:

```kotlin
    @Test
    fun `cooking reads the list and the pantry at the same time`() = runTest(mainDispatcherRule.testDispatcher) {
        recipeRepository.mineState.value =
            listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"), Ingredient("Leche", "1", "l"))))
        shoppingRepository.itemsState.value = listOf(shoppingItem("s1", "Arroz"))
        pantryRepository.serverIds = setOf(pantryId("Leche"))
        viewModel.observe("home")
        advanceUntilIdle()
        shoppingRepository.currentItemsGate = CompletableDeferred()

        viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Arroz con leche").copy(recipeId = "r1"))
        runCurrent()

        assertEquals(1, shoppingRepository.currentItemsCalls)
        assertEquals(1, pantryRepository.currentIdsCalls)
        shoppingRepository.currentItemsGate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("Arroz"), viewModel.uiState.value.cooked?.crossOff?.map(ShoppingItem::name))
        assertEquals(listOf(CookedPantryRow(pantryId("Leche"), "Leche")), viewModel.uiState.value.cooked?.finished)
    }
```

En `UT/feature/recipes/RecipesViewModelTest.kt`:

```kotlin
    @Test
    fun `the review reads the list and the pantry at the same time`() = runTest(mainDispatcherRule.testDispatcher) {
        pantryRepository.serverIds = setOf(pantryId("Arroz"))
        viewModel.observe()
        advanceUntilIdle()
        shoppingRepository.currentItemsGate = CompletableDeferred()
        val recipe = recipe("r1", RecipeVisibility.PRIVATE, "Arroz", listOf(Ingredient("Arroz", "300", "g")))

        viewModel.addToShopping("home", recipe)
        runCurrent()

        assertEquals(1, shoppingRepository.currentItemsCalls)
        assertEquals(1, pantryRepository.currentIdsCalls)
        shoppingRepository.currentItemsGate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(ReviewRowKind.IN_PANTRY), viewModel.uiState.value.ingredientReview?.map { it.kind })
    }

    // Ya pasa hoy (la lista se lee primero); protege la versión en paralelo de esperar a la despensa.
    @Test
    fun `if the list cannot be read the review fails without waiting for the pantry`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.observe()
            advanceUntilIdle()
            shoppingRepository.itemsFailure = ShoppingRepositoryException(ShoppingFailure.Network)
            pantryRepository.currentIdsGate = CompletableDeferred()
            val recipe = recipe("r1", RecipeVisibility.PRIVATE, "Arroz", listOf(Ingredient("Arroz", "300", "g")))

            viewModel.addToShopping("home", recipe)
            runCurrent()

            assertEquals(RecipeUiMessage.NetworkError, viewModel.uiState.value.error)
            assertNull(viewModel.uiState.value.ingredientReview)
            assertFalse(viewModel.uiState.value.isSaving)
        }
```

- [ ] **Step 3: Ejecutarlos en rojo.** **JVM** de `…feature.meals.MealsViewModelTest`, `…feature.meals.MealsCookedViewModelTest` y `…feature.recipes.RecipesViewModelTest`, una clase por llamada. Esperado: FAIL en los tres tests «… at the same time» (`expected:<1> but was:<0>` en `currentIdsCalls`). Los dos «… without waiting for the pantry» ya pasan. Copia los fallos.

- [ ] **Step 4: La función común.** En `APP/feature/common/PantryShopping.kt` (imports `kotlinx.coroutines.async` y `kotlinx.coroutines.coroutineScope`), después de `currentIdsOrNull`:

```kotlin
/**
 * Lee a la vez, del servidor, la lista de la compra ([readItems]) y la despensa de [spaceId] ([currentIdsOrNull]): un
 * viaje de ida y vuelta de espera en lugar de dos. Si [readItems] falla, falla igual y deja de esperar a la despensa.
 */
internal suspend fun <T> PantryRepository.withCurrentIds(
    spaceId: String,
    readItems: suspend () -> T,
): Pair<T, Set<String>?> = coroutineScope {
    val pantryIds = async { currentIdsOrNull(spaceId) }
    readItems() to pantryIds.await()
}
```

- [ ] **Step 5: Los tres sitios.**

En `APP/feature/meals/MealsViewModel.kt`, `prepareIngredientReview`: sustituye desde `// Sin la lista no se pueden buscar repetidos…` hasta `val rows = …` por:

```kotlin
                // La lista y la despensa del servidor, a la vez. Sin la lista no se pueden buscar repetidos: si no se
                // puede leer, no se abre la revisión; si no se puede leer la despensa, la revisión sale sin ella.
                val (current, pantryIds) = runCatching {
                    pantryRepository.withCurrentIds(spaceId) { shoppingRepository.currentItems(spaceId) }
                }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrElse { error ->
                        showError(error.toUiMessage())
                        return@launch
                    }
                val rows = buildIngredientReviewRows(ingredients, current, shoppingData, pantryIds.orEmpty())
```

En el mismo fichero, `markCooked`: sustituye el bloque `else { val items = runCatching { … }.getOrNull(); cookedReview(…) }` por:

```kotlin
                } else {
                    val (items, pantryIds) = pantryRepository.withCurrentIds(spaceId) {
                        runCatching { shoppingRepository.currentItems(spaceId) }
                            .onFailure { if (it is CancellationException) throw it }
                            .getOrNull()
                    }
                    cookedReview(meal.name, ingredients, items, pantryIds)
                }
```

Deja el `catch (error: Throwable)` de `markCooked` como está, porque está en el baseline. Cambia el import `currentIdsOrNull` por `withCurrentIds`.

En `APP/feature/recipes/RecipesViewModel.kt`, `addToShopping`: sustituye desde `// Sin la lista no se pueden buscar repetidos…` hasta `val rows = …` por el mismo bloque que en `prepareIngredientReview` (con `buildIngredientReviewRows(ingredients, current, shoppingData, pantryIds.orEmpty())`). Cambia el import `currentIdsOrNull` por `withCurrentIds`.

- [ ] **Step 6: Verde.** **JVM** de las tres clases del Step 3, más `…feature.common.PantryShoppingTest`. Esperado: PASS. Después, **GRADLE**. Esperado: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/common/PantryShopping.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealsViewModel.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipesViewModel.kt \
  app/src/test/java/com/dlunaunizar/bobitos/feature/common/FakePantryRepository.kt \
  app/src/test/java/com/dlunaunizar/bobitos/feature/meals/MealsViewModelTest.kt \
  app/src/test/java/com/dlunaunizar/bobitos/feature/meals/MealsCookedViewModelTest.kt \
  app/src/test/java/com/dlunaunizar/bobitos/feature/recipes/RecipesViewModelTest.kt
git commit -m "fix(despensa): la lista y la despensa se leen a la vez al abrir la revisión y al cocinar"
```

---

### Task 5: En la revisión, «Marca (opcional)» y «Sin especificar» caben en una línea

**Files:**
- Modify: `APP/feature/common/BrandStorePicker.kt`
- Modify: `APP/feature/common/IngredientReview.kt` (`ReviewRowContent`)
- Create (test): `AT/feature/common/IngredientReviewUiTest.kt`

**Interfaces:**
- Consumes: `IngredientReviewRow(name, recipeQuantity, existing, quantities, context, inPantry)`, `IngredientReviewRow.initialState(): ReviewRowState`, `IngredientShoppingContext(ingredient, brands, stores)`.
- Produces: `BrandStorePicker(…, modifier: Modifier = Modifier, stacked: Boolean = false)`; `ReviewRowContent` pasa a `internal`.

- [ ] **Step 1: Visibilidad.** En `APP/feature/common/IngredientReview.kt`, `private fun ReviewRowContent(` pasa a `internal fun ReviewRowContent(`. Nada más.

- [ ] **Step 2: Test (instrumentado).** Crea `AT/feature/common/IngredientReviewUiTest.kt`:

```kotlin
package com.dlunaunizar.bobitos.feature.common

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class IngredientReviewUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    // Una fila nueva de un ingrediente reconocido sin marcas registradas: marca libre y tienda «Sin especificar».
    private val row = IngredientReviewRow(
        name = "Arroz",
        recipeQuantity = "300 g",
        existing = null,
        quantities = listOf("300 g"),
        context = IngredientShoppingContext(
            ingredient = CatalogIngredient("arroz", "Arroz"),
            brands = emptyList(),
            stores = emptyList(),
        ),
    )

    // Líneas que ocupa [text] en su propio Text (sin fusionar con el campo o el botón que lo contiene).
    private fun lineCount(text: String): Int {
        val node = compose.onAllNodesWithText(text, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .first { SemanticsActions.GetTextLayoutResult in it.config }
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        assertEquals(text, layout.layoutInput.text.text)
        return layout.lineCount
    }

    @Test fun brandAndStoreFitOnOneLineInTheNarrowestReview() {
        compose.setContent {
            BobitosTheme {
                // 280 dp: el ancho mínimo del contenido de un AlertDialog.
                Box(Modifier.width(280.dp)) {
                    ReviewRowContent(row, row.initialState()) {}
                }
            }
        }

        assertEquals(1, lineCount(compose.activity.getString(R.string.shopping_brand_label)))
        assertEquals(1, lineCount(compose.activity.getString(R.string.shopping_supermarket_none)))
    }
}
```

**INSTR** de `com.dlunaunizar.bobitos.feature.common.IngredientReviewUiTest`. Esperado: FAIL (`expected:<1> but was:<2>`: a 280 dp, con dos columnas, los dos textos se parten). Copia el fallo.

- [ ] **Step 3: Marca y tienda, una debajo de otra.** En `APP/feature/common/BrandStorePicker.kt` (import `androidx.compose.foundation.layout.Column`, que ya está), sustituye el KDoc y `BrandStorePicker` por:

```kotlin
/**
 * Marca y tienda del ingrediente reconocido (§5): dos desplegables de igual ancho, uno al lado del otro o, con
 * [stacked], uno debajo del otro (en la revisión de «Añadir a la compra», que es estrecha). Sin marcas registradas,
 * la marca es un campo de texto libre.
 */
@Composable
internal fun BrandStorePicker(
    context: IngredientShoppingContext,
    selection: BrandStoreSelection,
    freeBrand: String,
    onBrandSelected: (String?) -> Unit,
    onStoreSelected: (Supermarket?) -> Unit,
    onFreeBrandChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
) {
    if (stacked) {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BrandField(context, selection, freeBrand, onBrandSelected, onFreeBrandChange, Modifier.fillMaxWidth())
            StoreField(context, selection, onStoreSelected, Modifier.fillMaxWidth())
        }
    } else {
        Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BrandField(context, selection, freeBrand, onBrandSelected, onFreeBrandChange, Modifier.weight(1f))
            StoreField(context, selection, onStoreSelected, Modifier.weight(1f))
        }
    }
}

// La marca: desplegable con las registradas o, si no hay ninguna, texto libre.
@Composable
private fun BrandField(
    context: IngredientShoppingContext,
    selection: BrandStoreSelection,
    freeBrand: String,
    onBrandSelected: (String?) -> Unit,
    onFreeBrandChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (context.brands.isEmpty()) {
            val tooLong = freeBrandTooLong(freeBrand)
            OutlinedTextField(
                value = freeBrand,
                onValueChange = onFreeBrandChange,
                label = { Text(stringResource(R.string.shopping_brand_label)) },
                singleLine = true,
                isError = tooLong,
                supportingText = if (tooLong) {
                    { Text(stringResource(R.string.shopping_error_brand_too_long)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            PickerLabel(R.string.shopping_brand_picker_label)
            BrandDropdown(context.brands, selection.brandId, onBrandSelected)
        }
    }
}

@Composable
private fun StoreField(
    context: IngredientShoppingContext,
    selection: BrandStoreSelection,
    onStoreSelected: (Supermarket?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PickerLabel(R.string.shopping_store_picker_label)
        SupermarketDropdown(
            selected = selection.store,
            onSelect = onStoreSelected,
            options = remember(context, selection.brandId) { context.storeOptions(selection) },
        )
    }
}
```

En `APP/feature/common/IngredientReview.kt`, en `ReviewRowContent`, la llamada a `BrandStorePicker(…)` gana `stacked = true,` como último argumento. El editor de la Compra (`ShoppingScreen.kt`) no cambia.

- [ ] **Step 4: Verde.** **INSTR** de `…feature.common.IngredientReviewUiTest`. Esperado: PASS. Después, **GRADLE**. Esperado: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/common/BrandStorePicker.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/common/IngredientReview.kt \
  app/src/androidTest/java/com/dlunaunizar/bobitos/feature/common/IngredientReviewUiTest.kt
git commit -m "fix(despensa): en la revisión, la marca y la tienda van una debajo de otra y no parten línea"
```

---

### Task 6: El selector «Elegir receta» de Comidas sigue abierto al girar el móvil

**Files:**
- Modify: `APP/feature/meals/MealsScreen.kt` (`MealEditor`)
- Create (test): `AT/feature/meals/MealEditorUiTest.kt`

**Interfaces:**
- Consumes: `MealEditor(meal, initialSlot, members, recipes, pantryIds, saving, errorMessage, canWrite, onDismiss, onSave)`; `RecipePickerDialog`, que ya guarda su búsqueda y su chip con `rememberSaveable`.
- Produces: `MealEditor` pasa a `internal`.

- [ ] **Step 1: Visibilidad.** En `APP/feature/meals/MealsScreen.kt`, `private fun MealEditor(` pasa a `internal fun MealEditor(`. Nada más. No cambies la firma de `MealsScreen` ni la de `MealCard`.

- [ ] **Step 2: Test (instrumentado).** Crea `AT/feature/meals/MealEditorUiTest.kt`:

```kotlin
package com.dlunaunizar.bobitos.feature.meals

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.model.MealSlot
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class MealEditorUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val tortilla = Recipe(
        id = "r1",
        ownerUid = "u1",
        visibility = RecipeVisibility.PRIVATE,
        title = "Tortilla de patatas",
        description = null,
        category = null,
        createdBy = "u1",
        createdByName = "Ana",
        createdAt = Instant.EPOCH,
        updatedBy = "u1",
        updatedAt = Instant.EPOCH,
    )

    private fun text(id: Int) = compose.activity.getString(id)

    @Test fun theRecipePickerStaysOpenWithItsSearchAfterRotating() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            BobitosTheme {
                MealEditor(
                    meal = null,
                    initialSlot = MealSlot.COMIDA,
                    members = emptyList(),
                    recipes = listOf(tortilla),
                    pantryIds = null,
                    saving = false,
                    errorMessage = null,
                    canWrite = true,
                    onDismiss = {},
                    onSave = { _, _, _, _, _ -> },
                )
            }
        }
        compose.onNodeWithText(text(R.string.meals_choose_recipe)).performClick()
        compose.onNodeWithText(text(R.string.meals_recipe_picker_title)).assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.recipes_search_label))).performTextInput("Tort")

        // Lo que pasa al girar el móvil: se guarda el estado, se descarta la composición y se recrea.
        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText(text(R.string.meals_recipe_picker_title)).assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText("Tort")).assertIsDisplayed()
    }
}
```

**INSTR** de `com.dlunaunizar.bobitos.feature.meals.MealEditorUiTest`. Esperado: FAIL después de `emulateSavedInstanceStateRestore()` (no hay ningún nodo «Elegir receta»: el selector se ha cerrado). Copia el fallo.

- [ ] **Step 3: El arreglo.** En `MealEditor`, sustituye `var pickerOpen by remember { mutableStateOf(false) }` por:

```kotlin
    // Como el borrador, sobrevive a una rotación (la búsqueda y el chip del selector ya se guardan en el diálogo).
    var pickerOpen by rememberSaveable(meal?.id) { mutableStateOf(false) }
```

- [ ] **Step 4: Verde.** **INSTR** de `…feature.meals.MealEditorUiTest`. Esperado: PASS. Después, **GRADLE**. Esperado: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealsScreen.kt \
  app/src/androidTest/java/com/dlunaunizar/bobitos/feature/meals/MealEditorUiTest.kt
git commit -m "fix(comidas): el selector «Elegir receta» sigue abierto al girar el móvil"
```

---

## Ejecución (`delegating-plan-execution`, plan de menores)

1. Rama `agent/menores-despensa-recetas`, desde `main` (f889bfc), con este plan como primer commit.
2. **Antes de la Tarea 1**, el coordinador arranca el emulador, en segundo plano, y lo deja encendido hasta el final:
   - `~/Library/Android/sdk/emulator/emulator -avd Pixel_10 -no-snapshot-save`;
   - espera a `~/Library/Android/sdk/platform-tools/adb wait-for-device` y a que `adb shell getprop sys.boot_completed` devuelva `1`.
3. Las tareas 1 a 6, una tras otra, cada una con un `sonnet` que recibe su brief. En el `task-done`: las clases JVM nombradas en su paso de verde, las clases INSTR de la tarea (1, 5 y 6) y GRADLE.
4. **Revisión final** con `opus`, con este plan (Review Focus incluido) y los rulings del ledger.
   - Críticos e Importantes: los arregla un `sonnet`, cada uno con su test RED→GREEN.
   - **Re-revisión acotada** solo si un arreglo toca `migrations/` o alguno de los cuatro temas de seguridad del método. No se espera ninguno: no cambian las reglas.
5. **`code-review`** en nivel `high`, más los arreglos (misma regla de re-revisión).
6. **Cierre:**
   - en `docs/superpowers/backlog.md`, sección «2026-10-14 — `agent/despensa-recetas`», tachar los 6 menores visibles y marcarlos **Resuelto** en `agent/menores-despensa-recetas`;
   - marcar el interno «faltan dos tests…» como **Ya no aplica**: Comidas ya no llama a `remove` y «Listo» es una sola escritura, cuyo contenido comprueba el test de la Tarea 1;
   - añadir los menores aplazados en una sección nueva, con la fecha y la rama;
   - el usuario decide qué hacer con la rama. **Sin push ni despliegue.**

## Verificación de punta a punta (coordinador, al final)

- **Suites:** GRADLE, NODE y EMU en verde (EMU no cambia, pero se pasa entera). Además, INSTR de `CookedDialogUiTest`, `IngredientReviewUiTest` y `MealEditorUiTest`.
- **Preparación:** como en el plan `docs/superpowers/plans/2026-10-14-despensa-recetas.md` («Verificación de punta a punta»):
  - `npm run emulators` con el `JAVA_HOME` y el `PATH` del JBR de Android Studio, y después `npm run seed:emulators`;
  - el script del Admin SDK en el scratchpad que crea el usuario sembrado y lo liga al espacio;
  - los catálogos de ingredientes y recetas importados al emulador;
  - instalar la app debug, porque `connectedDebugAndroidTest` la desinstala al acabar.
- **Datos:**
  - en el Recetario, una receta personal «Prueba de menores» con «huevo» (2 ud), «aceite de oliva virgen extra» (2 cucharadas), «sal» e «higos» (4 ud);
  - en la Despensa, «Huevos» y «Aceite de oliva virgen extra».
- **Recorrido**, con capturas en tema claro y oscuro de los pasos 1, 2 y 5:
  1. **«e» (M2):** Recetario → «Con lo que hay» → «Prueba de menores» dice «Faltan 2: sal e higos».
  2. **Revisión (M5):** «Prueba de menores» → «Añadir a la compra».
     - «sal» e «higos» salen como nuevas. En cada una, «Marca (opcional)» y, debajo, «Tienda» con «Sin especificar», cada texto en una sola línea.
     - «huevo» y «aceite de oliva virgen extra» salen como «Hay en la despensa».
     - Confirmar → en la Compra, «sal» e «higos» pendientes.
  3. **Selector (M6):** Comidas → nueva comida → «Elegir de una receta» → escribir «Prueba».
     - Girar: `adb shell settings put system accelerometer_rotation 0` y `adb shell settings put system user_rotation 1`. El selector sigue abierto, con «Prueba».
     - Volver a vertical (`user_rotation 0`) → elegir «Prueba de menores» → guardar.
  4. **«Listo» atómico y nombre del catálogo (M1 y M3):** marcar esa comida como cocinada.
     - «Has cocinado Prueba de menores» tiene «Tachar de la compra» con «sal» e «higos» marcados, y «¿Se ha acabado algo?» con «huevo» y «aceite de oliva virgen extra».
     - Marcar «aceite de oliva virgen extra» como acabado.
     - Antes de «Listo», borrar «higos» de la Compra con un script del Admin SDK en el scratchpad (hace de otro miembro).
     - «Listo» → «Compra y despensa al día», sin error.
     - En la Compra: «sal» comprada, «higos» ya no está y «Aceite de oliva virgen extra» pendiente, con el nombre del catálogo.
     - En la Despensa: entra «Sal», sale «Aceite de oliva virgen extra» y «Huevos» sigue.
  5. **«Listo» sin conexión (M1):** desmarcar la comida como cocinada y volver a marcarla.
     - En el diálogo, marcar «huevo» como acabado.
     - Cortar la red del emulador: `adb shell svc wifi disable` y `adb shell svc data disable`.
     - «Listo» → el diálogo sigue abierto, con «huevo» marcado y el error dentro (captura).
     - Devolver la red (`enable`) → «Listo» → «Compra y despensa al día». «Huevos» sale de la despensa y entra en la Compra.

## Despliegue (fuera de este plan)

Aplazado por el usuario. Este plan **no cambia `firestore.rules`**, pero la transacción nueva de «Listo» escribe en `pantryItems` como ya hacía `setPurchased`. Por eso necesita las reglas de la Despensa, que siguen sin desplegar: hay que desplegarlas (`npx firebase deploy --only firestore:rules --project dev`) **antes** de distribuir una beta que lleve este trabajo, y desde el mismo commit que se distribuye. Con las reglas antiguas, «Listo» falla entero con permiso denegado en cuanto tacha un ingrediente reconocido o algo se ha acabado.
