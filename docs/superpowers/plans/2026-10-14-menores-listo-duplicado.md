# «Listo» no duplica lo que otro miembro acaba de apuntar — Plan de implementación

> **Para agentes:** se ejecuta con `delegating-plan-execution`. Es un **plan de menores** (1 tarea): pasos 1, 2, 3, 5 y 6, **sin `simplify`**. La tarea la hace un subagente `sonnet`. La revisión final la hace `opus` (si falla, `fable`). **No se toca `firestore.rules` ni `migrations/`.** Los pasos usan casillas (`- [ ]`).

**Objetivo:** cerrar el menor con efecto visible de la sección «2026-10-14 — `agent/menores-listo-has-cocinado`» de `docs/superpowers/backlog.md`. Lo que «Listo» apunta en la compra como artículo nuevo (`ShoppingWrite.Add`) no comprueba la lista antes de escribir. Si otro miembro apunta lo mismo entre la lectura de la lista (`currentItems`, en `MealsViewModel.confirmCooked`) y la transacción de `FirestoreShoppingRepository.applyCooked`, sale duplicado. Los 2 internos de esa sección se quedan en el backlog.

**Arquitectura:** el cambio está solo en el repositorio de la Compra (`FirestoreShoppingRepository.applyCooked`).
- Dentro de la transacción, después de todas sus lecturas y justo antes de escribir, se vuelve a leer la lista del servidor.
- Si ya tiene un artículo que corresponde a algo que se iba a añadir, no se escribe nada y falla con `ShoppingFailure.ItemChanged`, con el nombre de ese artículo.
- Comidas ya trata ese fallo: el diálogo dice «Mientras guardabas, alguien ha cambiado «Leche» en la lista de la compra. No se ha guardado nada: vuelve a pulsar «Listo».». Al volver a pulsar, `confirmCooked` vuelve a leer la lista, ve el artículo y no lo añade.

**Stack:** Kotlin + Jetpack Compose (Material 3) + Hilt + Firestore (`firebase-firestore` 26.4.0, BoM 34.15.0). Tests JVM (JUnit 4 + `kotlinx-coroutines-test`, dobles a mano).

**Spec:** `docs/superpowers/specs/2026-10-13-despensa-design.md` (§3c, el diálogo de cocinar). Plan anterior: `docs/superpowers/plans/2026-10-14-menores-listo-has-cocinado.md`. Su Tarea 1 hizo que «Listo» lea la lista después de esperar al catálogo; su Tarea 3 creó `ShoppingRepositoryException.itemName`, `staleItemFailure` y el texto con nombre. Backlog: `docs/superpowers/backlog.md`.

## Qué entra

| # | Menor (backlog, sección «2026-10-14 — `agent/menores-listo-has-cocinado`») | Tarea |
|---|---|---|
| M1 | Lo que «Listo» vuelve a apuntar a la compra (`ShoppingWrite.Add`) no lleva precondición: si otro miembro apunta el mismo artículo entre `currentItems` y la transacción de `applyCooked`, sale duplicado | 1 |

## Qué no entra

- Los **internos** de la misma sección:
  - ningún test protege que «Cancelar» sin error previo conserve el aviso;
  - el contrato de la lambda `toMessage` de `confirmCooked` no está comentado.
- **`applyWrites`** tiene la misma ventana con sus `ShoppingWrite.Add`. Lo usan la revisión de «Añadir a la compra» (recetas y comidas) y «A la compra» de la Despensa. No entra (ruling 3); al cerrar se anota en el backlog como menor nuevo.
- **No toques** Comidas (`MealsViewModel.kt`, `MealsScreen.kt`, `MealsUiState.kt`, `CookedDialog.kt`, `CookedReview.kt`), `strings.xml`, `PantryShopping.kt`, `IngredientReview.kt`, ni las demás funciones de `FirestoreShoppingRepository` (`applyWrites`, `planWrite`, `requireUnchanged`, `currentItems`…).
- Los menores de las demás secciones del backlog.
- **Cualquier despliegue** (reglas, beta) y cualquier push: aplazados por el usuario.

## Rulings de planificación

1. **La comprobación va dentro de la transacción, lo último antes de escribir.**
   - En Android, Firestore no deja hacer consultas dentro de una transacción: `Transaction` solo tiene `get(DocumentReference)` (comprobado en `firebase-firestore` 26.4.0). Un artículo nuevo tiene un id que nadie conoce de antemano, así que no hay documento que leer.
   - Por eso la lista se lee con una consulta normal al servidor (`Source.SERVER`), desde la función de la transacción. Esa función corre en un hilo de fondo y puede esperar: `Transaction.get` hace lo mismo (`Tasks.await`).
   - La ventana pasa de «la lectura de `confirmCooked`, todas las lecturas de la transacción y el commit» (varios viajes de ida y vuelta) a «la consulta y el commit». **No se puede cerrar del todo sin cambiar el esquema.** Lo que queda se anota al cerrar.
   - Si Firestore repite la transacción (otro miembro ha tocado algo que leyó), la función vuelve a correr y la comprobación se repite con la lista nueva.
   - **Descartado:** un id fijo por clave para cada artículo. El editor de la Compra permite «Añadir aparte» a propósito, y los artículos que ya existen tienen ids automáticos.
   - **Descartado:** un documento con la versión de la lista que toquen todas las escrituras. Supone esquema nuevo, reglas, despliegue y versiones viejas de la app que no lo tocarían. Es demasiado para un menor raro.
2. **Si ya está, falla como un artículo que ha cambiado.** Si la lista tiene un artículo que corresponde a algo que se añade (`findShoppingMatch`, la misma clave tolerante con la que `pantryRestock` decidió añadirlo), no se escribe nada y falla con `ShoppingFailure.ItemChanged`.
   - `itemName` es el nombre de ese artículo tal como está en la lista: es el que ha apuntado el otro miembro.
   - Pasa igual si el artículo está comprado: al volver a pulsar «Listo», `pantryRestock` lo vuelve a apuntar.
   - Comidas no cambia. `ItemChanged` con nombre ya da el texto con nombre en el diálogo (`meals_error_cooked_item_changed`), y sin diálogo (cancelado mientras guardaba) da `CookedNotApplied`. «Alguien ha cambiado «X» en la lista de la compra» vale también para un artículo que otro miembro ha apuntado. No hay textos nuevos.
   - **Descartado:** saltarse en silencio el alta, como se saltan los tachados que ya no están. Con un artículo comprado se perdería el «volver a apuntar», y `applyCooked` dejaría de escribir «sobre la lista tal como se vio».
3. **Solo `applyCooked`.** El usuario ha pedido solo este menor. `applyWrites` se queda como está (ver «Qué no entra»).
4. **La misma lectura que `currentItems`:** por antigüedad y con el mismo límite (`MAX_VISIBLE_ITEMS`, 250). Así la comprobación ve lo mismo que vio `confirmCooked`. Si no hay nada que añadir, no se lee: «Listo» que solo tacha, quita de la despensa o vuelve a apuntar un comprado no hace ningún viaje de más.
5. **Fallos de la lectura sin envolver.** `Tasks.await` envuelve el fallo en `ExecutionException`; se lanza su causa, como hace `Transaction.get`.
   - Así una lectura sin conexión sigue siendo `FirebaseFirestoreException` (`UNAVAILABLE`). Firestore la trata como cualquier fallo de una lectura de la transacción, y `runShoppingOperation` la convierte en `ShoppingFailure.Network` («No hay conexión…»).
   - Sin esto, el fallo llegaría como `ExecutionException` y saldría «error inesperado».
6. **Sin funciones nuevas en la clase.** `FirestoreShoppingRepository` tiene 24; con 25 salta `TooManyFunctions`. Las dos funciones nuevas son de nivel superior en `FirestoreShoppingRepository.kt`, como `staleItemFailure`:
   - `listedMeanwhileFailure`, pura, que se prueba en la JVM (`ShoppingFirestoreTest`);
   - `serverItemsBlocking`, la lectura.
7. **Sin tests instrumentados.** No hay infraestructura para probar repositorios contra el emulador de Firebase desde Android, y la carrera no se puede provocar a mano en la app.
   - La decisión la cubre la función pura.
   - Lo que ve el usuario lo cubren los tests de Comidas: los que ya existen (`ItemChanged` con nombre → diálogo con nombre; cancelado → `CookedNotApplied`) y uno nuevo que protege el reintento.
   - Que la espera dentro de la transacción funciona con el SDK real lo comprueba el recorrido final, con un «Listo» que añade un artículo.

## Restricciones globales

- **Rutas:**
  - `APP` = `app/src/main/java/com/dlunaunizar/bobitos/`
  - `UT` = `app/src/test/java/com/dlunaunizar/bobitos/`
  - `ADB` = `~/Library/Android/sdk/platform-tools/adb`
  - `SCRATCH` = el directorio scratchpad de la sesión de ejecución (el coordinador lo pone en cada brief).
- **Comandos de verificación**, en llamadas separadas y sin `sh -c`:
  - **GRADLE:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:assembleDebug :app:ktlintCheck :app:detekt :app:testDebugUnitTest`
  - **JVM (una clase):** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:testDebugUnitTest --tests "<clase completa>"`
  - Si ktlint marca formato: `./gradlew :app:ktlintFormat` (con el mismo `JAVA_HOME`) y revisar el diff antes del commit.
- **Prohibido:**
  - cualquier script contra `bobitos-dev` (`--project dev` o `bobitos-dev`);
  - desplegar reglas, importar al proyecto real, distribuir betas o hacer push;
  - descargar nada;
  - tocar `firestore.rules`, `migrations/`, `tests/firebase-emulators.test.mjs` o `config/detekt/baseline.xml`;
  - arrancar, parar o reiniciar emuladores (Android o Firebase): la tarea no los necesita.
- **Commits con ficheros con nombre:** nunca `git add -A` ni `git add .`.
- **Textos y comentarios en español**, con el estilo de los ficheros vecinos.
- **Tests Kotlin:**
  - JVM puros (JUnit 4 + `kotlinx-coroutines-test`), con dobles a mano (`RecordingShoppingRepository`, `FakePantryRepository`, los de `MealsViewModelTest.kt` y los de `IngredientFakes.kt`); sin mockk ni Robolectric.
  - **Orden TDD obligatorio:** test primero y verlo en rojo, después el código. Un fallo de compilación por lo que aún no existe también vale como rojo; copia el fallo en el informe.
- **detekt:**
  - Límites: `ReturnCount` máx. 4, `LongMethod` máx. 80 líneas, `CyclomaticComplexMethod` 15 y `TooManyFunctions` 25 (por clase y por fichero). `MagicNumber` está desactivado.
  - **No añadas funciones a la clase `FirestoreShoppingRepository`** (tiene 24). Las funciones nuevas son de nivel superior: `listedMeanwhileFailure` y `serverItemsBlocking`.
  - No añadas `catch (Throwable)` nuevos.
- **ktlint:** sin imports sin usar; líneas de 120 caracteres como máximo. Si un comentario o KDoc del plan pasa del límite, reparte sus líneas sin cambiar el texto.
- **Tests existentes:** no se cambia ninguno; la tarea solo añade tests.

## Review Focus

1. **Otro miembro apunta lo mismo con otra forma** («Tomates» cuando «Listo» iba a añadir «tomate»): cuenta como el mismo artículo y no se duplica. Un artículo distinto («Pan») no bloquea nada. → `ShoppingFirestoreTest` (Tarea 1).
2. **Volver a pulsar «Listo» después de ese fallo:** la lista ya tiene el artículo, no se añade otra vez y sale «Compra y despensa al día». → `MealsCookedViewModelTest` (Tarea 1).
3. **Sin conexión justo en la lectura de la comprobación:** sale «No hay conexión…», no «error inesperado», y el diálogo sigue abierto con lo marcado. No se puede probar en la JVM (`Tasks` y `Query` son de Android y Firestore). El revisor compara el `catch` de `serverItemsBlocking` con `Transaction.get` (ruling 5).
4. **«Listo» con algo que añadir, en la app real:** la lectura dentro de la transacción no se cuelga ni falla por el hilo (`Tasks.await` no se puede llamar en el hilo principal). → recorrido final, paso 1.
5. **«Listo» sin nada que añadir** (solo tachar, quitar de la despensa o volver a apuntar un comprado): no hace la lectura de más. → revisión del `if (added.isNotEmpty())` (ruling 4).

---

## Ejecución (`delegating-plan-execution`, plan de menores)

1. Rama `agent/menores-listo-duplicado`, desde `main` (43e00be), con este plan como primer commit.
2. La Tarea 1, con un `sonnet` que recibe su brief.
   - **`task-done`:** las clases JVM nombradas en su paso de verde y GRADLE.
   - La tarea no necesita emuladores.
3. **Revisión final** con `opus`, con este plan (Review Focus incluido) y los rulings del ledger.
   - Críticos e Importantes: los arregla un `sonnet`, cada uno con su test RED→GREEN.
   - **Re-revisión acotada** solo si un arreglo toca `migrations/` o alguno de los cuatro temas de seguridad del método. No se espera ninguno: no cambian las reglas.
4. **`code-review`** en nivel `high`, más los arreglos (misma regla de re-revisión).
5. **Verificación de punta a punta** (sección propia, abajo).
6. **Cierre**, en `docs/superpowers/backlog.md`:
   - sección «2026-10-14 — `agent/menores-listo-has-cocinado`»: tachar M1 y marcarlo **Resuelto** en `agent/menores-listo-duplicado`, con esta nota: «Justo antes de escribir, «Listo» vuelve a leer la lista dentro de la transacción. Si ya tiene algo de lo que añade, no escribe nada y pide volver a pulsar «Listo» (rulings 1 y 2). Queda la ventana entre esa lectura y el commit: no se puede cerrar sin cambiar el esquema.»;
   - añadir una sección nueva, con la fecha y la rama, con:
     - **Efecto visible (raro):** `applyWrites` tiene la misma ventana con sus `ShoppingWrite.Add`: si otro miembro apunta lo mismo mientras se guarda, sale duplicado. Afecta a la revisión de «Añadir a la compra» (recetas y comidas) y a «A la compra» de la Despensa. El arreglo sería el de `applyCooked` (`FirestoreShoppingRepository.applyWrites`, ruling 3 de `docs/superpowers/plans/2026-10-14-menores-listo-duplicado.md`);
     - los menores aplazados de la revisión y del `code-review`;
   - el usuario decide qué hacer con la rama. **Sin push ni despliegue.**

---

### Task 1: «Listo» vuelve a mirar la lista justo antes de escribir lo que añade

**Files:**
- Modify: `APP/data/repository/FirestoreShoppingRepository.kt` (`applyCooked`, dos funciones nuevas de nivel superior al final del fichero y sus imports)
- Modify: `APP/data/repository/ShoppingRepository.kt` (solo los KDoc de `applyCooked` y de `ShoppingRepositoryException`)
- Modify: `docs/FUNCTIONAL_SPEC.md` (PANTRY-05)
- Modify (test): `UT/data/repository/ShoppingFirestoreTest.kt`
- Modify (test): `UT/feature/meals/MealsCookedViewModelTest.kt`

**Interfaces:**
- Consumes:
  - `findShoppingMatch(items: List<ShoppingItem>, name: String): ShoppingItem?` (`APP/core/model/ShoppingMatch.kt`): por clave tolerante, el primer artículo pendiente o, si todos están comprados, el primero comprado; `null` si no hay ninguno o si `name` está en blanco;
  - `List<DocumentSnapshot>.toSortedShoppingItems()`, privada de nivel superior en `FirestoreShoppingRepository.kt` (la usa `currentItems`);
  - `ShoppingRepositoryException(failure, cause = null, itemName = null)`;
  - en los tests de Comidas: `shoppingRepository.cookedFailure` (un fallo de `applyCooked` no se apunta en `cookedCalls`), `shoppingRepository.itemsState` (lo que devuelve `currentItems` en el momento de la llamada), `shoppingRepository.cookedCalls` y `shoppingItem(id, name)` (pendiente; en `MealsViewModelTest.kt`).
- Produces:
  - `internal fun listedMeanwhileFailure(current: List<ShoppingItem>, added: List<String>): ShoppingRepositoryException?`;
  - `private fun Query.serverItemsBlocking(): List<ShoppingItem>`.
  - La firma de `applyCooked` no cambia.

- [ ] **Step 1: Test del repositorio.** En `UT/data/repository/ShoppingFirestoreTest.kt`, añade al final de la clase (después del test `` `a deleted item fails as not found and says which one` ``):

```kotlin
    private fun listed(name: String, purchased: Boolean = false) =
        seen.copy(id = "id-$name", name = name, purchased = purchased)

    @Test
    fun `adding what is still not on the list is fine`() {
        assertNull(listedMeanwhileFailure(listOf(listed("Pan")), listOf("Leche", "Arroz")))
    }

    @Test
    fun `if someone has listed something being added it fails as changed and names it as listed`() {
        val failure = listedMeanwhileFailure(listOf(listed("Pan"), listed("Tomates")), listOf("Leche", "tomate"))!!

        assertEquals(ShoppingFailure.ItemChanged, failure.failure)
        assertEquals("Tomates", failure.itemName)
    }

    @Test
    fun `an item listed and bought meanwhile also fails, so that tapping done again lists it again`() {
        val failure = listedMeanwhileFailure(listOf(listed("Leche", purchased = true)), listOf("Leche"))!!

        assertEquals(ShoppingFailure.ItemChanged, failure.failure)
        assertEquals("Leche", failure.itemName)
    }
```

- [ ] **Step 2: Test de Comidas (protege el reintento).** En `UT/feature/meals/MealsCookedViewModelTest.kt`, añade justo después del test `` `the changed item is only named with the error that asks to tap done again` ``:

  ```kotlin
      @Test
      fun `if someone lists meanwhile what done adds the dialog names it and a retry does not add it again`() =
          runTest(mainDispatcherRule.testDispatcher) {
              recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
              pantryRepository.serverIds = setOf(pantryId("Leche"))
              viewModel.observe("home")
              advanceUntilIdle()
              viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
              advanceUntilIdle()
              val choice = viewModel.uiState.value.cooked!!.defaultChoice().toggleFinished(pantryId("Leche"))
              // Justo antes de escribir, el repositorio ve que otro miembro acaba de apuntar «Leche».
              shoppingRepository.cookedFailure =
                  ShoppingRepositoryException(ShoppingFailure.ItemChanged, itemName = "Leche")

              viewModel.confirmCooked(choice)
              advanceUntilIdle()

              assertEquals(MealUiMessage.CookedShoppingChanged, viewModel.uiState.value.error)
              assertEquals("Leche", viewModel.uiState.value.cookedErrorItem)

              shoppingRepository.cookedFailure = null
              shoppingRepository.itemsState.value = listOf(shoppingItem("s9", "Leche"))
              viewModel.confirmCooked(choice)
              advanceUntilIdle()

              assertTrue(shoppingRepository.cookedCalls.single().restock.isEmpty())
              assertNull(viewModel.uiState.value.cooked)
              assertEquals(MealUiMessage.CookedReviewApplied, viewModel.uiState.value.notice)
          }
  ```

  Si alguna línea pasa de 120 caracteres, pártela como las vecinas. Este test no depende de código nuevo: pasará en cuanto compilen los tests.

- [ ] **Step 3: Rojo.** **JVM** de `com.dlunaunizar.bobitos.data.repository.ShoppingFirestoreTest`. Esperado: fallo de compilación, `Unresolved reference 'listedMeanwhileFailure'`. Copia el fallo.

- [ ] **Step 4: La comprobación en el repositorio.** En `APP/data/repository/FirestoreShoppingRepository.kt`:

  1. Sustituye la función `applyCooked` completa (desde `override suspend fun applyCooked(` hasta su `}` de cierre, justo antes de `override suspend fun deleteItem`) por:

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
             val added = restock.filterIsInstance<ShoppingWrite.Add>().map(ShoppingWrite.Add::name)
             val spaceReference = firestore.collection(SPACES).document(spaceId)
             val pantry = firestore.pantryCollection(spaceId)
             // La misma lectura que currentItems: con ella se comprueba lo que se añade, justo antes de escribir.
             val list = itemsCollection(spaceId)
                 .orderBy(FIELD_CREATED_AT, Query.Direction.ASCENDING)
                 .limit(MAX_VISIBLE_ITEMS)

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
                 // Lo último antes de escribir: si otro miembro ha apuntado algo de lo que se añade después de que
                 // se leyera la lista, no se escribe nada. Firestore no deja consultar dentro de una transacción,
                 // así que la lista se lee aparte (serverItemsBlocking).
                 if (added.isNotEmpty()) listedMeanwhileFailure(list.serverItemsBlocking(), added)?.let { throw it }
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

     Lo único nuevo son `added`, `list` (con su comentario) y la comprobación (con su comentario). El resto es el código de hoy, sin cambios.

  2. Añade al final del fichero, después de `staleItemFailure`:

     ```kotlin
     /**
      * El fallo de añadir [added] (los nombres de cada [ShoppingWrite.Add]) si la lista [current] ya tiene un artículo
      * que corresponde a alguno ([findShoppingMatch]): otro miembro lo ha apuntado después de que se leyera la lista.
      * Es [ShoppingFailure.ItemChanged], con el nombre de ese artículo tal como está en la lista; `null` si no hay ninguno.
      */
     internal fun listedMeanwhileFailure(current: List<ShoppingItem>, added: List<String>): ShoppingRepositoryException? =
         added.firstNotNullOfOrNull { name -> findShoppingMatch(current, name) }
             ?.let { ShoppingRepositoryException(ShoppingFailure.ItemChanged, itemName = it.name) }

     // La lista del servidor, leída desde la función de una transacción: corre en un hilo de fondo y puede esperar, como
     // Transaction.get. Los fallos salen sin envolver, también como en Transaction.get: sin conexión sigue siendo
     // ShoppingFailure.Network y no «error inesperado».
     private fun Query.serverItemsBlocking(): List<ShoppingItem> = try {
         Tasks.await(get(Source.SERVER)).documents.toSortedShoppingItems()
     } catch (error: ExecutionException) {
         throw error.cause ?: error
     }
     ```

  3. Añade los imports `com.dlunaunizar.bobitos.core.model.findShoppingMatch`, `com.google.android.gms.tasks.Tasks` y `java.util.concurrent.ExecutionException`, en el orden de ktlint (si dudas, `ktlintFormat`).

  - No añadas ninguna función a la clase.
  - No toques `applyWrites`, `planWrite`, `requireUnchanged`, `currentItems` ni `staleItemFailure`.
  - Si detekt marca algo en `serverItemsBlocking` (por ejemplo, `SwallowedException`), cuéntalo en el informe antes de cambiar nada: el `catch` tiene que seguir lanzando la causa (ruling 5).

- [ ] **Step 5: El contrato en la interfaz.** En `APP/data/repository/ShoppingRepository.kt`:

  1. En el KDoc de `applyCooked`, sustituye la última línea, `* [restock], no se escribe nada y falla con [ShoppingFailure.ItemChanged] o [ShoppingFailure.ItemNotFound].`, por:

     ```kotlin
          * [restock], no se escribe nada y falla con [ShoppingFailure.ItemChanged] o [ShoppingFailure.ItemNotFound]. Justo
          * antes de escribir se vuelve a leer la lista: si ya tiene algo de lo que [restock] añade ([ShoppingWrite.Add]),
          * porque otro miembro lo ha apuntado entretanto, tampoco se escribe nada y falla con
          * [ShoppingFailure.ItemChanged].
     ```

  2. Sustituye el KDoc de `ShoppingRepositoryException` por:

     ```kotlin
     /**
      * Fallo de [ShoppingRepository]. Si una escritura sobre un artículo tal como se vio falla porque otro miembro lo ha
      * cambiado o borrado ([ShoppingFailure.ItemChanged] o [ShoppingFailure.ItemNotFound]), [itemName] es su nombre tal
      * como se vio. Si [ShoppingRepository.applyCooked] falla porque otro miembro ya ha apuntado algo que se iba a
      * añadir ([ShoppingFailure.ItemChanged]), es el nombre de ese artículo tal como está en la lista. Si no, `null`.
      */
     ```

- [ ] **Step 6: La documentación funcional.** En `docs/FUNCTIONAL_SPEC.md`, PANTRY-05, en la línea que empieza por «- «Listo» lo escribe todo a la vez», sustituye «Si a la vez otro miembro cambia algo de lo que se vuelve a apuntar, el diálogo avisa» por «Si a la vez otro miembro cambia algo de lo que se vuelve a apuntar, o apunta algo de lo que se va a añadir, el diálogo avisa». El resto de la línea no cambia.

- [ ] **Step 7: Verde.** **JVM** de `com.dlunaunizar.bobitos.data.repository.ShoppingFirestoreTest` y de `com.dlunaunizar.bobitos.feature.meals.MealsCookedViewModelTest`. Esperado: PASS los dos, incluido el test nuevo de Comidas. Después, **GRADLE**. Esperado: BUILD SUCCESSFUL. Si detekt marca `TooManyFunctions` en `FirestoreShoppingRepository`, has añadido una función a la clase: muévela a nivel superior.

- [ ] **Step 8: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/data/repository/FirestoreShoppingRepository.kt \
  app/src/main/java/com/dlunaunizar/bobitos/data/repository/ShoppingRepository.kt \
  docs/FUNCTIONAL_SPEC.md \
  app/src/test/java/com/dlunaunizar/bobitos/data/repository/ShoppingFirestoreTest.kt \
  app/src/test/java/com/dlunaunizar/bobitos/feature/meals/MealsCookedViewModelTest.kt
git commit -m "fix(comidas): «Listo» mira la lista justo antes de escribir y no duplica lo que otro miembro acaba de apuntar"
```

---

## Verificación de punta a punta (coordinador, al final)

- **Suites:** GRADLE en verde. NODE (`npm run test:scripts`) y EMU (las reglas) no se pasan: este plan no toca scripts ni reglas.
- **Preparación** (cada proceso largo, en segundo plano):
  1. Emulador Android: `~/Library/Android/sdk/emulator/emulator -avd Pixel_10 -no-snapshot-save`. Espera a `ADB wait-for-device` y a que `ADB shell getprop sys.boot_completed` devuelva `1`.
  2. Emuladores de Firebase: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && export PATH="$JAVA_HOME/bin:$PATH" && npm run emulators`. Espera a que respondan los puertos 8080 y 9099.
  3. `npm run android:connect-emulators` y después `npm run seed:emulators`. Esto deja el usuario `demo-owner`, el espacio «Casa de prueba» (`demo-home`) y «Leche» pendiente en la Compra.
  4. Escribe `SCRATCH/setup.mjs` con este contenido y ejecútalo con `node SCRATCH/setup.mjs`:

     ```js
     // Prepara los emuladores de Firebase (demo-bobitos): el usuario sembrado, la receta «Arroz con leche» y «Arroz» en la despensa.
     import { createRequire } from "node:module";
     const require = createRequire("/Users/dluna/Developer/Bobitos/package.json");
     process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8080";
     process.env.FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099";
     const { initializeApp } = require("firebase-admin/app");
     const { getAuth } = require("firebase-admin/auth");
     const { getFirestore, FieldValue } = require("firebase-admin/firestore");
     const app = initializeApp({ projectId: "demo-bobitos" });
     try { await getAuth(app).deleteUser("demo-owner"); } catch {}
     await getAuth(app).createUser({
       uid: "demo-owner", email: "david@bobitos.invalid", password: "bobitos123",
       displayName: "David Demo", emailVerified: true,
     });
     const db = getFirestore(app);
     await db.collection("recipes").doc("arroz-con-leche").set({
       ownerUid: "demo-owner", visibility: "PRIVATE", title: "Arroz con leche", description: null, category: null,
       sourceRecipeId: null, sourceUrl: null,
       ingredients: [
         { name: "Arroz", quantity: "200", unit: "g" },
         { name: "Leche", quantity: "1", unit: "l" },
       ],
       steps: [], servings: null, totalMinutes: null,
       createdBy: "demo-owner", createdByName: "David Demo", createdAt: FieldValue.serverTimestamp(),
       updatedBy: "demo-owner", updatedAt: FieldValue.serverTimestamp(),
     });
     // pantryId("Arroz") = "arroc" (ingredientKey cambia la z final por c)
     await db.collection("spaces").doc("demo-home").collection("pantryItems").doc("arroc").set({
       name: "Arroz", category: null, createdBy: "demo-owner", createdAt: FieldValue.serverTimestamp(), shoppingItemId: null,
     });
     console.log("usuario demo-owner, receta «Arroz con leche» y «Arroz» en la despensa listos");
     ```

  5. Instala la app debug: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:installDebug`.
- **Recorrido en la app real.** Inicia sesión con `david@bobitos.invalid` / `bobitos123` y haz capturas de cada paso en `SCRATCH/e2e/`. Si en el paso 1 «¿Se ha acabado algo?» no ofrece «Arroz», el id de la despensa no es `arroc`: sácalo leyendo `pantryId`, `slug` y `shoppingMatchKey` (`APP/core/model/`), corrige `setup.mjs` y repite.
  1. **«Listo» que añade un artículo (la lectura dentro de la transacción, con el SDK real):**
     - Casa de prueba → Comidas → «Nueva comida» → «Elegir de una receta» → «Arroz con leche» → «Guardar».
     - En la tarjeta, «Marcar como cocinada». Sale «Has cocinado Arroz con leche», con «Leche» en «Tachar de la compra» y «Arroz» en «¿Se ha acabado algo?».
     - Marca «Arroz» y pulsa «Listo». «Arroz» no está en la lista, así que «Listo» lo añade y hace la comprobación nueva.
     - Esperado: el diálogo se cierra y sale el aviso «Compra y despensa al día» (captura). Si sale «error inesperado» o el diálogo se queda guardando más de 20 s, la lectura dentro de la transacción falla: guarda `ADB logcat -d` en `SCRATCH/e2e/logcat.txt` y para.
     - En la Compra: «Leche» comprada y «Arroz» pendiente, una sola vez (captura).
     - En la Despensa: «Arroz» ya no está (captura).
  - La carrera no se puede provocar a mano, y «Listo» con lo mismo ya apuntado (no se añade nada) lo cubren los tests JVM (ruling 7).
- Después, para los emuladores de Firebase y el emulador Android.

## Despliegue (fuera de este plan)

Aplazado por el usuario. Este plan **no cambia `firestore.rules`**. Lo que ya estaba pendiente sigue igual: las reglas de la Despensa están sin desplegar. Hay que desplegarlas (`npx firebase deploy --only firestore:rules --project dev`) **antes** de distribuir una beta que lleve este trabajo, y desde el mismo commit que se distribuye.
