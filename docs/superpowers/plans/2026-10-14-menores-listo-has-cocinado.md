# Menores de «Listo» en «Has cocinado …» — Plan de implementación

> **Para agentes:** se ejecuta con `delegating-plan-execution`. Es un **plan de menores** (3 tareas): pasos 1, 2, 3, 5 y 6, **sin `simplify`**. Cada tarea la hace un subagente `sonnet`, en serie y en orden. La revisión final la hace `opus` (si falla, `fable`). **Ninguna tarea toca `firestore.rules` ni `migrations/`.** Los pasos usan casillas (`- [ ]`).

**Objetivo:** cerrar los 3 menores con efecto visible de la sección «2026-10-14 — `agent/menores-has-cocinado`» de `docs/superpowers/backlog.md`. Los 2 internos de esa sección se quedan en el backlog; al cerrar se anotan (ver «Ejecución», paso 6).

**Arquitectura:** todo pasa en «Listo» de «Has cocinado …» (`MealsViewModel.confirmCooked`):
- **Tarea 1:** «Listo» espera primero al catálogo y después lee la lista. Si el catálogo ya ha llegado, no hay espera. Así «A la compra» trabaja siempre sobre la lista recién leída.
- **Tarea 2:** «Cancelar» se lleva el error del diálogo. Si se cancela mientras guarda y después falla por la lista, el aviso de la pantalla dice que la compra y la despensa se han quedado como estaban, sin mandar a pulsar «Listo».
- **Tarea 3:** el repositorio de la Compra dice qué artículo ha cambiado o se ha borrado (`ShoppingRepositoryException.itemName`), y el diálogo lo nombra.

**Stack:** Kotlin + Jetpack Compose (Material 3) + Hilt + Firestore. Tests JVM (JUnit 4 + `kotlinx-coroutines-test`, dobles a mano).

**Spec:** `docs/superpowers/specs/2026-10-13-despensa-design.md` (§3c, el diálogo de cocinar). Plan anterior: `docs/superpowers/plans/2026-10-14-menores-has-cocinado.md`. Su ruling 2 creó el mensaje «…vuelve a pulsar «Listo».» y dejó sin tratar el caso de cancelar mientras guarda; su ruling 3 hizo que el mensaje de «Listo» se elija en la llamada (`toMessage`); su ruling 6 puso a la vez la lectura de la lista y la espera del catálogo. Backlog: `docs/superpowers/backlog.md`.

## Qué entra

| # | Menor (backlog, sección «2026-10-14 — `agent/menores-has-cocinado`») | Tarea |
|---|---|---|
| M1 | Si «Listo» falla porque la lista ha cambiado y se pulsa «Cancelar» (después del error o mientras guarda), el aviso de Comidas sigue diciendo «…vuelve a pulsar «Listo».», aunque ya no hay diálogo | 2 |
| M2 | Si el catálogo aún no ha llegado, «A la compra» usa una lista de hasta 5 s antes: puede duplicar un artículo que otro miembro apunte entretanto, o fallar más a menudo por la lista cambiada | 1 |
| M3 | Si otro miembro sigue tocando el artículo, «vuelve a pulsar «Listo»» se repite sin decir qué artículo ha cambiado | 3 |

## Qué no entra

- Los **internos** de la misma sección:
  - `ItemNotFound` en el mapeo general de Comidas;
  - el patrón `coroutineScope` + `async` de `confirmCooked`. Este desaparece con la Tarea 1 sin trabajo aparte; al cerrar se marca «Ya no aplica».
- **No toques** `PantryShopping.kt`, `IngredientReview.kt` (`applySeenWrites`), `CookedDialog.kt`, `CookedReview.kt`, el comentario de `CookedReviewDialog` en `MealsScreen.kt` (otro menor interno) ni el aviso de la pantalla (`MealsFeedback`).
- La Compra, la Despensa y la revisión de «Añadir a la compra» también recibirán `itemName` en sus fallos (comparten `requireUnchanged`), pero no lo usan: sus mensajes no cambian.
- Los menores de las demás secciones del backlog.
- **Cualquier despliegue** (reglas, beta) y cualquier push: aplazados por el usuario.

## Rulings de planificación

1. **Primero el catálogo y después la lista (M2).** En `confirmCooked`, la lista se lee después de `ingredientData()`, como antes del ruling 6 del plan anterior.
   - Si el catálogo ya ha llegado (lo normal: la pantalla lo escucha desde que se abre), `ingredientData()` vuelve al momento: no hay espera y da igual el orden.
   - Leer a la vez solo cambiaba algo cuando el catálogo aún no había llegado, y justo entonces dejaba la lista hasta 5 s vieja. `ShoppingWrite.Add` no lleva precondición, así que lo que otro miembro apuntaba en ese tiempo salía duplicado.
   - Lo que se pierde, solo en ese caso: si la lista no se puede leer, el error sale al acabar la espera del catálogo (5 s como máximo), no enseguida. La espera total no cambia apenas: el catálogo más una lectura.
   - Se descarta precargar el catálogo al abrir el diálogo: la pantalla ya lo escucha desde `observe()`.
   - Al cerrar, la línea de M5 del plan anterior (sección `agent/menores-despensa-recetas`) se anota como revertida a propósito.
2. **«Cancelar» se lleva el error del diálogo (M1).** `dismissCooked()` pone `cooked = null` y también `error = null`, sea cual sea el error.
   - Mientras el diálogo está abierto, el único error posible es el de «Listo»: `markCooked` limpia el error al abrirlo y el diálogo es modal.
   - El aviso «Comida marcada como cocinada» (`notice`) no se toca.
3. **Cancelar mientras guarda (M1).** `toCookedUiMessage` recibe si el diálogo sigue abierto (`cooked != null` en el momento del fallo).
   - Ningún otro diálogo puede abrirse mientras se guarda: `markCooked` no hace nada con `isSaving`.
   - **Abierto:** `ItemChanged` o `ItemNotFound` → `CookedShoppingChanged` («…vuelve a pulsar «Listo».»), como hasta ahora.
   - **Cancelado:** `ItemChanged` o `ItemNotFound` → valor nuevo `MealUiMessage.CookedNotApplied`. Su texto, `meals_error_cooked_not_applied`, es «La lista de la compra ha cambiado mientras guardabas. La compra y la despensa se han quedado como estaban.». Es la otra cara de «Compra y despensa al día» y no manda a pulsar nada. No dice «No se ha guardado nada», porque la comida sí ha quedado marcada como cocinada.
   - Los demás errores, abierto o no, siguen con su mensaje de siempre («No hay conexión…»), que no menciona «Listo».
   - Si cancelar mientras guarda acaba bien, sale «Compra y despensa al día», como hoy.
4. **El repositorio dice qué artículo (M3).** `ShoppingRepositoryException` gana el parámetro `itemName: String? = null`.
   - Lo rellena la función pura nueva `staleItemFailure(current, seen)`, de nivel superior en `FirestoreShoppingRepository.kt`, con el nombre del artículo tal como se vio. Se prueba en la JVM, como `validatePantryEntry` en `PantryFirestoreTest`.
   - `requireUnchanged` la usa: mismos fallos que hoy (`ItemNotFound` si no existe o no se puede leer, `ItemChanged` si ha cambiado), ahora con nombre.
   - `ItemNotFound` por el código `NOT_FOUND` de Firestore (`toShoppingRepositoryException`) sigue sin nombre.
   - **No se añaden funciones a la clase `FirestoreShoppingRepository`:** tiene 24 y con 25 salta `TooManyFunctions`.
5. **Dónde vive el nombre (M3).** El campo nuevo `MealsUiState.changedItemName` se escribe en **cada** fallo de «Listo»: el nombre si el fallo lo trae, `null` si no.
   - Solo se lee a través de la propiedad nueva `cookedErrorItem`, que lo devuelve únicamente con `error == CookedShoppingChanged`. Así nunca se nombra un artículo de un fallo anterior, ni con otro error.
   - Se descarta convertir `MealUiMessage` en `sealed interface` con datos: todos los `*UiMessage` de la app son `enum`.
6. **El texto con nombre (M3).** `meals_error_cooked_item_changed`: «Mientras guardabas, alguien ha cambiado «%1$s» en la lista de la compra. No se ha guardado nada: vuelve a pulsar «Listo».»
   - «Alguien» cubre a otro miembro y al mismo usuario desde otro móvil. Si el artículo se ha borrado, el texto también vale: ha cambiado.
   - Sin nombre, sale el texto de siempre, `meals_error_cooked_shopping_changed`.
   - Solo el diálogo nombra el artículo. El aviso de la pantalla, que queda detrás del diálogo, sigue con el texto sin nombre, y con «Cancelar» se va (ruling 2).
7. **Sin tests instrumentados en las tareas.** `CookedDialog` no cambia. La elección del texto en `CookedReviewDialog` es una línea sobre `cookedErrorItem`, que sí se prueba en la JVM.
   - El coordinador pasa `CookedDialogUiTest` al final, como regresión, y hace el recorrido en la app real (ver «Verificación de punta a punta»).

## Restricciones globales

- **Rutas:**
  - `APP` = `app/src/main/java/com/dlunaunizar/bobitos/`
  - `UT` = `app/src/test/java/com/dlunaunizar/bobitos/`
  - `AT` = `app/src/androidTest/java/com/dlunaunizar/bobitos/`
  - `RES` = `app/src/main/res/values/strings.xml`
  - `ADB` = `~/Library/Android/sdk/platform-tools/adb`
  - `SCRATCH` = el directorio scratchpad de la sesión de ejecución (el coordinador lo pone en cada brief).
- **Comandos de verificación**, en llamadas separadas y sin `sh -c`:
  - **GRADLE:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:assembleDebug :app:ktlintCheck :app:detekt :app:testDebugUnitTest`
  - **JVM (una clase):** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:testDebugUnitTest --tests "<clase completa>"`
  - **INSTR (una clase instrumentada; solo el coordinador, al final):** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<clase completa>`.
    - Antes, `ADB devices` debe listar un `emulator-…  device`.
    - Nunca sin `-P…class=`: `ProfileScreenTest` falla desde antes (backlog, sección `agent/menores-compra-membresia-y-comunes`).
    - `connectedDebugAndroidTest` desinstala la app al acabar.
  - **NODE:** `npm run test:scripts` (solo el coordinador, al final).
  - **EMU (reglas):** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && export PATH="$JAVA_HOME/bin:$PATH" && npm run test:emulators` (solo el coordinador, al final; este plan no cambia reglas). Usa los puertos 8080 y 9099: no puede haber otros emuladores de Firebase arrancados.
  - Si ktlint marca formato: `./gradlew :app:ktlintFormat` (con el mismo `JAVA_HOME`) y revisar el diff antes del commit.
- **Prohibido:**
  - cualquier script contra `bobitos-dev` (`--project dev` o `bobitos-dev`);
  - desplegar reglas, importar al proyecto real, distribuir betas o hacer push;
  - descargar nada;
  - tocar `firestore.rules`, `migrations/`, `tests/firebase-emulators.test.mjs` o `config/detekt/baseline.xml`;
  - arrancar, parar o reiniciar emuladores (Android o Firebase): las tareas no los necesitan.
- **Commits con ficheros con nombre:** nunca `git add -A` ni `git add .`.
- **Textos y comentarios en español**, con el estilo de los ficheros vecinos. Textos de la app en `RES`.
- **Tests Kotlin:**
  - JVM puros (JUnit 4 + `kotlinx-coroutines-test`), con dobles a mano (`RecordingShoppingRepository`, `FakePantryRepository`, los de `MealsViewModelTest.kt` y los de `IngredientFakes.kt`); sin mockk ni Robolectric.
  - **Orden TDD obligatorio:** test primero y verlo en rojo, después el código. Un fallo de compilación por lo que aún no existe también vale como rojo; copia el fallo en el informe.
- **detekt:**
  - Límites: `ReturnCount` máx. 4, `LongMethod` máx. 80 líneas, `CyclomaticComplexMethod` 15 y `TooManyFunctions` 25 (por clase y por fichero). `MagicNumber` está desactivado.
  - **No añadas funciones a la clase `MealsViewModel`** (está en el baseline de `TooManyFunctions`) **ni a la clase `FirestoreShoppingRepository`** (tiene 24). Las únicas funciones nuevas del plan son de nivel superior: `staleItemFailure` (Tarea 3).
  - **No cambies estas firmas, que están en el baseline:** `MealsScreen(...)` y `MealCard(...)`.
  - No añadas `catch (Throwable)` nuevos.
- **ktlint:** sin imports sin usar; líneas de 120 caracteres como máximo. Si un comentario o KDoc del plan pasa del límite, reparte sus líneas sin cambiar el texto.
- **Tests existentes:** solo se cambian los que la tarea nombra.

## Review Focus

1. **El catálogo aún no ha llegado y otro miembro apunta lo mismo mientras «Listo» espera:** no se duplica. Si el catálogo llega antes de los 5 s, «Listo» no espera más. → `MealsCookedViewModelTest` (Tarea 1).
2. **«Cancelar» después de un error de «Listo»:** el aviso de la pantalla queda vacío y el diálogo cerrado. → `MealsCookedViewModelTest` (Tarea 2); con un error de conexión, en el recorrido final.
3. **«Cancelar» mientras «Listo» guarda:**
   - si falla por la lista, la pantalla dice que la compra y la despensa se han quedado como estaban, nunca «vuelve a pulsar «Listo»»;
   - si acaba bien, dice «Compra y despensa al día».

   → `MealsCookedViewModelTest` (Tarea 2).
4. **Dos fallos seguidos de «Listo», el primero con nombre y el segundo sin él:** el diálogo no repite el nombre del primero. → `MealsCookedViewModelTest` (Tarea 3).
5. **Un nombre guardado y otro error** (sin conexión, o el aviso de después de cancelar): no se nombra ningún artículo. → `MealsCookedViewModelTest`, test de `cookedErrorItem` (Tarea 3).

---

## Ejecución (`delegating-plan-execution`, plan de menores)

1. Rama `agent/menores-listo-has-cocinado`, desde `main` (f282f25), con este plan como primer commit.
2. Las tareas 1 a 3, una tras otra, cada una con un `sonnet` que recibe su brief.
   - **`task-done`:** las clases JVM nombradas en su paso de verde y GRADLE.
   - Las tareas no necesitan emuladores.
3. **Revisión final** con `opus`, con este plan (Review Focus incluido) y los rulings del ledger.
   - Críticos e Importantes: los arregla un `sonnet`, cada uno con su test RED→GREEN.
   - **Re-revisión acotada** solo si un arreglo toca `migrations/` o alguno de los cuatro temas de seguridad del método. No se espera ninguno: no cambian las reglas.
4. **`code-review`** en nivel `high`, más los arreglos (misma regla de re-revisión).
5. **Verificación de punta a punta** (sección propia, abajo).
6. **Cierre**, en `docs/superpowers/backlog.md`:
   - sección «2026-10-14 — `agent/menores-has-cocinado`»:
     - tachar M1, M2 y M3 y marcarlos **Resuelto** en `agent/menores-listo-has-cocinado`;
     - tachar el interno «`confirmCooked` repite en línea el patrón `coroutineScope` + `async`…» y marcarlo **Ya no aplica** tras `agent/menores-listo-has-cocinado`: «Listo» ya no lee nada a la vez (ruling 1);
     - en el interno «en el mapeo general de Comidas, `ItemNotFound` cae ahora en `UnexpectedError`…», añadir: «Tras `agent/menores-listo-has-cocinado`, `toCookedUiMessage` también distingue si el diálogo sigue abierto (ruling 3): ya no se puede quitar sin más.»;
   - sección «2026-10-14 — `agent/menores-despensa-recetas`», en la línea tachada «"Listo" espera al catálogo… podrían ir a la vez», añadir: «Revertido a propósito en `agent/menores-listo-has-cocinado` (ruling 1): leer a la vez dejaba la lista hasta 5 s vieja y podía duplicar artículos.»;
   - añadir los menores aplazados en una sección nueva, con la fecha y la rama;
   - el usuario decide qué hacer con la rama. **Sin push ni despliegue.**

---

### Task 1: «Listo» lee la lista después de esperar al catálogo

**Files:**
- Modify: `APP/feature/meals/MealsViewModel.kt` (solo el bloque de `runAction` de `confirmCooked` y dos imports)
- Modify (test): `UT/feature/meals/MealsCookedViewModelTest.kt`

**Interfaces:**
- Consumes:
  - `ingredientData()` (privada de `MealsViewModel`): al momento si el catálogo ya ha llegado; si no, 5 s como máximo (`CATALOG_TIMEOUT_MILLIS`);
  - `pantryRestock(names, items, data).writes`, `CookedWrites.restockNamesIn(data)` y `pantryEntryFor(name, data)`;
  - en los tests:
    - `ingredientRepository.catalogOverride`: `flow { awaitCancellation() }` simula el catálogo que no llega, y un `flow` con `delay` uno que llega tarde;
    - `shoppingRepository.itemsState`: `currentItems` devuelve su valor en el momento de la llamada;
    - `shoppingRepository.itemsFailure`, que hace fallar `currentItems`;
    - `shoppingRepository.cookedCalls`.
- Produces: nada nuevo; misma firma de `confirmCooked(choice: CookedChoice)`. Las Tareas 2 y 3 cambian solo la llamada a `runAction` (sus argumentos), no el bloque que escribe esta tarea.

- [ ] **Step 1: Tests.** En `UT/feature/meals/MealsCookedViewModelTest.kt`:

  1. Añade el import `kotlinx.coroutines.delay`, en orden alfabético con los demás.
  2. Sustituye el test completo `` `done reads the list while it waits for the catalog` `` (desde su `@Test` hasta su `}` de cierre) por estos dos:

  ```kotlin
      @Test
      fun `without the catalog in time done reads the list after waiting and sees what was listed meanwhile`() =
          runTest(mainDispatcherRule.testDispatcher) {
              ingredientRepository.catalogOverride = flow { awaitCancellation() }
              recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
              pantryRepository.serverIds = setOf(pantryId("Leche"))
              viewModel.observe("home")
              runCurrent()
              viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
              runCurrent()
              val review = viewModel.uiState.value.cooked!!

              viewModel.confirmCooked(review.defaultChoice().toggleFinished(pantryId("Leche")))
              runCurrent()
              // Mientras «Listo» espera al catálogo, otro miembro apunta «Leche».
              shoppingRepository.itemsState.value = listOf(shoppingItem("s9", "Leche"))
              advanceTimeBy(5_001)
              runCurrent()

              assertTrue(shoppingRepository.cookedCalls.single().restock.isEmpty())
              assertEquals(MealUiMessage.CookedReviewApplied, viewModel.uiState.value.notice)
          }

      @Test
      fun `done waits only until the catalog arrives and then reads the list`() =
          runTest(mainDispatcherRule.testDispatcher) {
              ingredientRepository.catalogOverride = flow {
                  delay(2_000)
                  emit(listOf(CatalogIngredient("leche", "Leche")))
              }
              recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
              pantryRepository.serverIds = setOf(pantryId("Leche"))
              viewModel.observe("home")
              runCurrent()
              viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
              runCurrent()
              val review = viewModel.uiState.value.cooked!!

              viewModel.confirmCooked(review.defaultChoice().toggleFinished(pantryId("Leche")))
              advanceTimeBy(1_000)
              runCurrent()
              assertTrue(shoppingRepository.cookedCalls.isEmpty())
              // Mientras espera, otro miembro apunta «Leche».
              shoppingRepository.itemsState.value = listOf(shoppingItem("s9", "Leche"))

              advanceTimeBy(1_001)
              runCurrent()
              assertTrue(shoppingRepository.cookedCalls.single().restock.isEmpty())
          }
  ```

  3. Sustituye el test completo `` `if the list cannot be read done fails at once without waiting for the catalog` `` por este (el comportamiento cambia a propósito: ruling 1):

  ```kotlin
      @Test
      fun `if the list cannot be read after waiting for the catalog done writes nothing and keeps the dialog`() =
          runTest(mainDispatcherRule.testDispatcher) {
              ingredientRepository.catalogOverride = flow { awaitCancellation() }
              recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
              pantryRepository.serverIds = setOf(pantryId("Leche"))
              viewModel.observe("home")
              runCurrent()
              viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
              runCurrent()
              val review = viewModel.uiState.value.cooked!!
              shoppingRepository.itemsFailure = ShoppingRepositoryException(ShoppingFailure.Network)

              viewModel.confirmCooked(review.defaultChoice().toggleFinished(pantryId("Leche")))
              advanceTimeBy(5_001)
              runCurrent()

              assertEquals(MealUiMessage.NetworkError, viewModel.uiState.value.error)
              assertFalse(viewModel.uiState.value.isSaving)
              assertSame(review, viewModel.uiState.value.cooked)
              assertTrue(shoppingRepository.cookedCalls.isEmpty())
          }
  ```

  No toques `` `done with nothing to put on the list does not read the list` `` ni `` `crossing off without the catalog in time waits at most 5 seconds and marks without a pantry entry` ``. Si alguna línea pasa de 120 caracteres, pártela como las vecinas.

- [ ] **Step 2: Rojo.** **JVM** de `com.dlunaunizar.bobitos.feature.meals.MealsCookedViewModelTest`. Esperado:
  - FAIL en `without the catalog in time done reads the list after waiting and sees what was listed meanwhile` y en `done waits only until the catalog arrives and then reads the list`: `restock` lleva un `Add` de «Leche», porque la lista se leyó al pulsar «Listo», antes de que otro miembro la apuntara;
  - PASS en `if the list cannot be read after waiting for the catalog done writes nothing and keeps the dialog`, que protege lo que no cambia.

  Copia los fallos.

- [ ] **Step 3: Primero el catálogo.** En `APP/feature/meals/MealsViewModel.kt`, dentro de `confirmCooked`, sustituye el cuerpo del bloque de `runAction` (lo que va entre `runAction(MealUiMessage.CookedReviewApplied, toMessage = Throwable::toCookedUiMessage) {` y su `}` de cierre) por:

```kotlin
            val needsCatalog = writes.crossOff.isNotEmpty() || writes.restockNames.isNotEmpty()
            // Primero el catálogo (al momento si ya ha llegado; si no, hasta 5 s) y después la lista: «A la compra»
            // trabaja sobre la lista recién leída y no duplica lo que otro miembro apunte mientras se espera.
            val data = if (needsCatalog) ingredientData() else IngredientShoppingData()
            val restock = if (writes.restockNames.isEmpty()) {
                emptyList()
            } else {
                pantryRestock(writes.restockNamesIn(data), shoppingRepository.currentItems(spaceId), data).writes
            }
            shoppingRepository.applyCooked(
                spaceId,
                writes.crossOff.map { ShoppingCrossOff(it.id, pantryEntryFor(it.name, data)) },
                writes.finishedIds,
                restock,
            )
            mutableUiState.update { it.copy(cooked = null) }
```

  - Quita los imports `kotlinx.coroutines.async` y `kotlinx.coroutines.coroutineScope`: solo los usaba este bloque.
  - No cambies nada más de `confirmCooked`: ni la línea de `runAction`, ni la comprobación de `writes.isEmpty`, ni el comentario de encima.

- [ ] **Step 4: Verde.** **JVM** de `com.dlunaunizar.bobitos.feature.meals.MealsCookedViewModelTest` y de `com.dlunaunizar.bobitos.feature.meals.MealsViewModelTest`. Esperado: PASS los dos. Después, **GRADLE**. Esperado: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealsViewModel.kt \
  app/src/test/java/com/dlunaunizar/bobitos/feature/meals/MealsCookedViewModelTest.kt
git commit -m "fix(comidas): «Listo» lee la lista después del catálogo y no duplica lo apuntado entretanto"
```

---

### Task 2: «Cancelar» se lleva el error de «Listo»; si se cancela mientras guarda, el aviso no manda a pulsar «Listo»

**Files:**
- Modify: `APP/feature/meals/MealUiMessage.kt`
- Modify: `APP/feature/meals/MealsViewModel.kt` (`confirmCooked`: solo la llamada a `runAction`; `dismissCooked`; `Throwable.toCookedUiMessage`)
- Modify: `APP/feature/meals/MealsScreen.kt` (solo `MealUiMessage.stringResourceId`)
- Modify: `RES`
- Modify: `docs/FUNCTIONAL_SPEC.md` (PANTRY-05)
- Modify (test): `UT/feature/meals/MealsCookedViewModelTest.kt`

**Interfaces:**
- Consumes:
  - el bloque de `confirmCooked` que deja la Tarea 1 (no se toca);
  - `runAction(successNotice, editor = false, toMessage: (Throwable) -> MealUiMessage, action)`;
  - en los tests: `shoppingRepository.cookedGate`, un `CompletableDeferred<Unit>` (simula una escritura lenta), y `shoppingRepository.cookedFailure`. Un fallo de `applyCooked` no se apunta en `cookedCalls`.
- Produces:
  - `MealUiMessage.CookedNotApplied` y el texto `meals_error_cooked_not_applied`;
  - `private fun Throwable.toCookedUiMessage(dialogOpen: Boolean): MealUiMessage`;
  - la llamada `runAction(MealUiMessage.CookedReviewApplied, toMessage = { error -> … }) { … }` en `confirmCooked`. La Tarea 3 amplía esta lambda.

- [ ] **Step 1: Tests.** En `UT/feature/meals/MealsCookedViewModelTest.kt`, añade justo después del test `` `any other failure of done keeps its own message` ``:

  ```kotlin
      @Test
      fun `cancelling after done fails takes the error away with the dialog`() =
          runTest(mainDispatcherRule.testDispatcher) {
              recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
              pantryRepository.serverIds = setOf(pantryId("Leche"))
              viewModel.observe("home")
              advanceUntilIdle()
              viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
              advanceUntilIdle()
              val review = viewModel.uiState.value.cooked!!
              shoppingRepository.cookedFailure = ShoppingRepositoryException(ShoppingFailure.ItemChanged)
              viewModel.confirmCooked(review.defaultChoice().toggleFinished(pantryId("Leche")))
              advanceUntilIdle()
              assertEquals(MealUiMessage.CookedShoppingChanged, viewModel.uiState.value.error)

              viewModel.dismissCooked()

              assertNull(viewModel.uiState.value.cooked)
              assertNull(viewModel.uiState.value.error)
          }

      @Test
      fun `if the list changes after cancelling while done saves the notice does not ask to tap done again`() =
          runTest(mainDispatcherRule.testDispatcher) {
              recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
              pantryRepository.serverIds = setOf(pantryId("Leche"))
              viewModel.observe("home")
              advanceUntilIdle()
              viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
              advanceUntilIdle()
              val review = viewModel.uiState.value.cooked!!
              shoppingRepository.cookedGate = CompletableDeferred()
              shoppingRepository.cookedFailure = ShoppingRepositoryException(ShoppingFailure.ItemChanged)

              viewModel.confirmCooked(review.defaultChoice().toggleFinished(pantryId("Leche")))
              runCurrent()
              viewModel.dismissCooked()
              shoppingRepository.cookedGate?.complete(Unit)
              advanceUntilIdle()

              assertNull(viewModel.uiState.value.cooked)
              assertEquals(MealUiMessage.CookedNotApplied, viewModel.uiState.value.error)
              assertFalse(viewModel.uiState.value.isSaving)
              assertTrue(shoppingRepository.cookedCalls.isEmpty())
          }

      @Test
      fun `if done works after cancelling while it saves the notice says it is saved`() =
          runTest(mainDispatcherRule.testDispatcher) {
              recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
              pantryRepository.serverIds = setOf(pantryId("Leche"))
              viewModel.observe("home")
              advanceUntilIdle()
              viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
              advanceUntilIdle()
              val review = viewModel.uiState.value.cooked!!
              shoppingRepository.cookedGate = CompletableDeferred()

              viewModel.confirmCooked(review.defaultChoice().toggleFinished(pantryId("Leche")))
              runCurrent()
              viewModel.dismissCooked()
              shoppingRepository.cookedGate?.complete(Unit)
              advanceUntilIdle()

              assertNull(viewModel.uiState.value.cooked)
              assertNull(viewModel.uiState.value.error)
              assertEquals(MealUiMessage.CookedReviewApplied, viewModel.uiState.value.notice)
              assertEquals(1, shoppingRepository.cookedCalls.size)
          }
  ```

  Si alguna línea pasa de 120 caracteres, pártela como las vecinas.

- [ ] **Step 2: Rojo.** **JVM** de `com.dlunaunizar.bobitos.feature.meals.MealsCookedViewModelTest`. Esperado: fallo de compilación, `Unresolved reference 'CookedNotApplied'`. Copia el fallo.

- [ ] **Step 3: El texto y el valor nuevos.**
  - En `RES`, justo después de `meals_error_cooked_shopping_changed`:

    ```xml
        <string name="meals_error_cooked_not_applied">La lista de la compra ha cambiado mientras guardabas. La compra y la despensa se han quedado como estaban.</string>
    ```

  - En `APP/feature/meals/MealUiMessage.kt`, añade `CookedNotApplied,` justo después de `CookedShoppingChanged,`.
  - En `APP/feature/meals/MealsScreen.kt`, en `MealUiMessage.stringResourceId`, justo después de la rama de `CookedShoppingChanged`:

    ```kotlin
            MealUiMessage.CookedNotApplied -> R.string.meals_error_cooked_not_applied
    ```

- [ ] **Step 4: El ViewModel.** En `APP/feature/meals/MealsViewModel.kt`:

  1. En `confirmCooked`, sustituye solo la línea `runAction(MealUiMessage.CookedReviewApplied, toMessage = Throwable::toCookedUiMessage) {` por:

     ```kotlin
             runAction(
                 MealUiMessage.CookedReviewApplied,
                 // Si se ha pulsado «Cancelar» mientras guardaba, ya no hay «Listo» que volver a pulsar.
                 toMessage = { error -> error.toCookedUiMessage(dialogOpen = mutableUiState.value.cooked != null) },
             ) {
     ```

     El cuerpo del bloque (Tarea 1) no cambia.
  2. Sustituye `fun dismissCooked() = mutableUiState.update { it.copy(cooked = null) }` por:

     ```kotlin
         // «Cancelar»: el error de «Listo» es del diálogo y se va con él.
         fun dismissCooked() = mutableUiState.update { it.copy(cooked = null, error = null) }
     ```

  3. Sustituye `Throwable.toCookedUiMessage()` y su comentario por:

     ```kotlin
     // «Listo» en «Has cocinado …»: si un artículo que se vuelve a apuntar ha cambiado o se ha borrado, no se ha escrito
     // nada. Con el diálogo abierto basta con volver a pulsar «Listo», que vuelve a leer la lista; si se ha cancelado
     // mientras guardaba, solo se avisa de que no se ha escrito nada. Los demás errores, como en el resto de Comidas.
     private fun Throwable.toCookedUiMessage(dialogOpen: Boolean): MealUiMessage {
         val failure = (this as? ShoppingRepositoryException)?.failure
         return when {
             failure != ShoppingFailure.ItemChanged && failure != ShoppingFailure.ItemNotFound -> toUiMessage()
             dialogOpen -> MealUiMessage.CookedShoppingChanged
             else -> MealUiMessage.CookedNotApplied
         }
     }
     ```

- [ ] **Step 5: La documentación funcional.** En `docs/FUNCTIONAL_SPEC.md`, PANTRY-05, la línea que empieza por «- «Listo» lo escribe todo a la vez» acaba en «…y basta con volver a pulsar «Listo».». Añade al final de esa misma línea, tras un espacio:

  «Cancelar» cierra el diálogo y su aviso. Si se cancela mientras guarda y la lista ha cambiado, la pantalla avisa de que la compra y la despensa se han quedado como estaban.

- [ ] **Step 6: Verde.** **JVM** de `com.dlunaunizar.bobitos.feature.meals.MealsCookedViewModelTest` y de `com.dlunaunizar.bobitos.feature.meals.MealsViewModelTest`. Esperado: PASS los dos, incluidos sin tocarlos `` `if the list changes while done saves a retry reads it again and writes once` `` y `` `any other failure of done keeps its own message` ``. Después, **GRADLE**. Esperado: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealUiMessage.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealsViewModel.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealsScreen.kt \
  app/src/main/res/values/strings.xml \
  docs/FUNCTIONAL_SPEC.md \
  app/src/test/java/com/dlunaunizar/bobitos/feature/meals/MealsCookedViewModelTest.kt
git commit -m "fix(comidas): «Cancelar» en «Has cocinado …» se lleva el error y no deja «vuelve a pulsar «Listo»»"
```

---

### Task 3: el error de «Listo» dice qué artículo ha cambiado

**Files:**
- Modify: `APP/data/repository/ShoppingRepository.kt` (solo `ShoppingRepositoryException`)
- Modify: `APP/data/repository/FirestoreShoppingRepository.kt` (`requireUnchanged` y la función nueva `staleItemFailure`, de nivel superior)
- Modify: `APP/feature/meals/MealsUiState.kt`
- Modify: `APP/feature/meals/MealsViewModel.kt` (solo la lambda `toMessage` de `confirmCooked`)
- Modify: `APP/feature/meals/MealsScreen.kt` (solo `CookedReviewDialog`)
- Modify: `RES`
- Modify: `docs/FUNCTIONAL_SPEC.md` (PANTRY-05)
- Create (test): `UT/data/repository/ShoppingFirestoreTest.kt`
- Modify (test): `UT/feature/meals/MealsCookedViewModelTest.kt`

**Interfaces:**
- Consumes:
  - la llamada `runAction(..., toMessage = { error -> error.toCookedUiMessage(dialogOpen = …) })` de la Tarea 2;
  - `MealUiMessage.CookedNotApplied` (Tarea 2);
  - `ShoppingItem.sameContentAs(other)` (`APP/core/model/ShoppingItem.kt`);
  - `DocumentSnapshot.toShoppingItem()`, privada de nivel superior en `FirestoreShoppingRepository.kt`.
- Produces:
  - `class ShoppingRepositoryException(val failure: ShoppingFailure, cause: Throwable? = null, val itemName: String? = null)`;
  - `internal fun staleItemFailure(current: ShoppingItem?, seen: ShoppingItem): ShoppingRepositoryException?`;
  - `MealsUiState.changedItemName: String?` y la propiedad `MealsUiState.cookedErrorItem: String?`;
  - el texto `meals_error_cooked_item_changed` (con `%1$s`).

- [ ] **Step 1: Test del repositorio.** Crea `UT/data/repository/ShoppingFirestoreTest.kt`:

```kotlin
package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.ShoppingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ShoppingFirestoreTest {
    private val seen = ShoppingItem(
        id = "s1",
        name = "Leche",
        quantity = "1 l",
        notes = null,
        purchased = true,
        createdBy = "u",
        createdByName = "U",
        createdAt = Instant.EPOCH,
        updatedBy = "u",
        updatedAt = Instant.EPOCH,
        purchasedBy = "u",
        purchasedByName = "U",
        purchasedAt = Instant.EPOCH,
        supermarket = null,
        brand = null,
    )

    @Test
    fun `an item still as it was seen is not stale`() {
        assertNull(staleItemFailure(seen.copy(updatedBy = "otro", updatedAt = Instant.now()), seen))
    }

    @Test
    fun `a changed item fails as changed and says which one, as it was seen`() {
        val failure = staleItemFailure(seen.copy(name = "Leche entera", quantity = "2 l"), seen)!!

        assertEquals(ShoppingFailure.ItemChanged, failure.failure)
        assertEquals("Leche", failure.itemName)
    }

    @Test
    fun `a deleted item fails as not found and says which one`() {
        val failure = staleItemFailure(null, seen)!!

        assertEquals(ShoppingFailure.ItemNotFound, failure.failure)
        assertEquals("Leche", failure.itemName)
    }
}
```

- [ ] **Step 2: Tests de Comidas.** En `UT/feature/meals/MealsCookedViewModelTest.kt`, añade justo después del test `` `if done works after cancelling while it saves the notice says it is saved` `` (Tarea 2):

  ```kotlin
      @Test
      fun `if done fails because someone changed an item the dialog names it, and only that time`() =
          runTest(mainDispatcherRule.testDispatcher) {
              recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
              pantryRepository.serverIds = setOf(pantryId("Leche"))
              viewModel.observe("home")
              advanceUntilIdle()
              viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
              advanceUntilIdle()
              val choice = viewModel.uiState.value.cooked!!.defaultChoice().toggleFinished(pantryId("Leche"))
              shoppingRepository.cookedFailure =
                  ShoppingRepositoryException(ShoppingFailure.ItemChanged, itemName = "Leche")

              viewModel.confirmCooked(choice)
              advanceUntilIdle()

              assertEquals(MealUiMessage.CookedShoppingChanged, viewModel.uiState.value.error)
              assertEquals("Leche", viewModel.uiState.value.cookedErrorItem)

              // Si el siguiente fallo no dice qué artículo, no se repite el nombre de antes.
              shoppingRepository.cookedFailure = ShoppingRepositoryException(ShoppingFailure.ItemNotFound)
              viewModel.confirmCooked(choice)
              advanceUntilIdle()

              assertEquals(MealUiMessage.CookedShoppingChanged, viewModel.uiState.value.error)
              assertNull(viewModel.uiState.value.cookedErrorItem)
          }

      @Test
      fun `the changed item is only named with the error that asks to tap done again`() {
          val state = MealsUiState(error = MealUiMessage.CookedShoppingChanged, changedItemName = "Leche")

          assertEquals("Leche", state.cookedErrorItem)
          assertNull(state.copy(error = MealUiMessage.NetworkError).cookedErrorItem)
          assertNull(state.copy(error = MealUiMessage.CookedNotApplied).cookedErrorItem)
          assertNull(state.copy(error = null).cookedErrorItem)
      }
  ```

  Si alguna línea pasa de 120 caracteres, pártela como las vecinas.

- [ ] **Step 3: Rojo.** **JVM** de `com.dlunaunizar.bobitos.data.repository.ShoppingFirestoreTest` y de `com.dlunaunizar.bobitos.feature.meals.MealsCookedViewModelTest`. Esperado: fallos de compilación (`Unresolved reference 'staleItemFailure'`, `'itemName'`, `'changedItemName'` y `'cookedErrorItem'`). Copia los fallos.

- [ ] **Step 4: El repositorio dice qué artículo.**

  1. En `APP/data/repository/ShoppingRepository.kt`, sustituye la línea `class ShoppingRepositoryException(val failure: ShoppingFailure, cause: Throwable? = null) : Exception(cause)` por:

     ```kotlin
     /**
      * Fallo de [ShoppingRepository]. Si una escritura sobre un artículo tal como se vio falla porque otro miembro lo ha
      * cambiado o borrado ([ShoppingFailure.ItemChanged] o [ShoppingFailure.ItemNotFound]), [itemName] es su nombre tal
      * como se vio; si no, `null`.
      */
     class ShoppingRepositoryException(
         val failure: ShoppingFailure,
         cause: Throwable? = null,
         val itemName: String? = null,
     ) : Exception(cause)
     ```

  2. En `APP/data/repository/FirestoreShoppingRepository.kt`, sustituye `requireUnchanged` y su comentario por:

     ```kotlin
         // El artículo sigue como se vio; si otro miembro lo ha cambiado o borrado, no se escribe nada de la transacción.
         private fun requireUnchanged(snapshot: DocumentSnapshot, seen: ShoppingItem) {
             staleItemFailure(snapshot.takeIf { it.exists() }?.toShoppingItem(), seen)?.let { throw it }
         }
     ```

     Y añade al final del fichero, después de `toShoppingRepositoryException`:

     ```kotlin
     /**
      * El fallo de una escritura sobre [seen] si su artículo ya no está como se vio: [ShoppingFailure.ItemNotFound] si
      * se ha borrado o no se puede leer ([current] es `null`) y [ShoppingFailure.ItemChanged] si ha cambiado; `null` si
      * sigue igual. Lleva el nombre de [seen], para poder decir qué artículo ha sido.
      */
     internal fun staleItemFailure(current: ShoppingItem?, seen: ShoppingItem): ShoppingRepositoryException? = when {
         current == null -> ShoppingRepositoryException(ShoppingFailure.ItemNotFound, itemName = seen.name)
         !current.sameContentAs(seen) -> ShoppingRepositoryException(ShoppingFailure.ItemChanged, itemName = seen.name)
         else -> null
     }
     ```

     - `requireItem` se sigue usando en otras funciones: no la toques.
     - No añadas ninguna función a la clase.

- [ ] **Step 5: El nombre en el estado.** En `APP/feature/meals/MealsUiState.kt`:

  1. Añade, justo después de `val cooked: CookedReview? = null,`:

     ```kotlin
         // El artículo de la lista que alguien ha cambiado o borrado la última vez que falló «Listo» (null si no se sabe).
         // Solo cuenta con error = CookedShoppingChanged: se lee a través de cookedErrorItem.
         val changedItemName: String? = null,
     ```

  2. Añade, justo después de la propiedad `weekDays`:

     ```kotlin
         // El artículo que nombra el error de «Has cocinado …»; null si el error es otro o no se sabe cuál.
         val cookedErrorItem: String?
             get() = changedItemName.takeIf { error == MealUiMessage.CookedShoppingChanged }
     ```

- [ ] **Step 6: El ViewModel guarda el nombre.** En `APP/feature/meals/MealsViewModel.kt`, en `confirmCooked`, sustituye estas líneas de la Tarea 2:

  ```kotlin
              // Si se ha pulsado «Cancelar» mientras guardaba, ya no hay «Listo» que volver a pulsar.
              toMessage = { error -> error.toCookedUiMessage(dialogOpen = mutableUiState.value.cooked != null) },
  ```

  por:

  ```kotlin
              toMessage = { error ->
                  // Qué artículo ha cambiado, para nombrarlo en el diálogo (MealsUiState.cookedErrorItem).
                  val itemName = (error as? ShoppingRepositoryException)?.itemName
                  mutableUiState.update { it.copy(changedItemName = itemName) }
                  // Si se ha pulsado «Cancelar» mientras guardaba, ya no hay «Listo» que volver a pulsar.
                  error.toCookedUiMessage(dialogOpen = mutableUiState.value.cooked != null)
              },
  ```

  Nada más de `confirmCooked` cambia.

- [ ] **Step 7: El texto con nombre.**
  - En `RES`, justo después de `meals_error_cooked_shopping_changed`:

    ```xml
        <string name="meals_error_cooked_item_changed">Mientras guardabas, alguien ha cambiado «%1$s» en la lista de la compra. No se ha guardado nada: vuelve a pulsar «Listo».</string>
    ```

  - En `APP/feature/meals/MealsScreen.kt`, en `CookedReviewDialog`, sustituye la línea `errorMessage = state.error?.let { stringResource(it.stringResourceId) },` por:

    ```kotlin
            // Si «Listo» ha fallado porque alguien ha cambiado un artículo de la lista, lo nombra.
            errorMessage = state.cookedErrorItem?.let { stringResource(R.string.meals_error_cooked_item_changed, it) }
                ?: state.error?.let { stringResource(it.stringResourceId) },
    ```

    No cambies el comentario de encima de `CookedReviewDialog` (es otro menor, interno).

- [ ] **Step 8: La documentación funcional.** En `docs/FUNCTIONAL_SPEC.md`, PANTRY-05, en la línea que empieza por «- «Listo» lo escribe todo a la vez», sustituye «el diálogo avisa de que la lista ha cambiado y basta con volver a pulsar «Listo».» por «el diálogo avisa de que la lista ha cambiado, nombrando el artículo si se sabe, y basta con volver a pulsar «Listo».».

- [ ] **Step 9: Verde.** **JVM** de `com.dlunaunizar.bobitos.data.repository.ShoppingFirestoreTest`, de `com.dlunaunizar.bobitos.feature.meals.MealsCookedViewModelTest` y de `com.dlunaunizar.bobitos.feature.meals.MealsViewModelTest`. Esperado: PASS los tres. Después, **GRADLE**. Esperado: BUILD SUCCESSFUL. Si detekt marca `TooManyFunctions` en `FirestoreShoppingRepository`, has añadido una función a la clase: muévela a nivel superior.

- [ ] **Step 10: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/data/repository/ShoppingRepository.kt \
  app/src/main/java/com/dlunaunizar/bobitos/data/repository/FirestoreShoppingRepository.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealsUiState.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealsViewModel.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealsScreen.kt \
  app/src/main/res/values/strings.xml \
  docs/FUNCTIONAL_SPEC.md \
  app/src/test/java/com/dlunaunizar/bobitos/data/repository/ShoppingFirestoreTest.kt \
  app/src/test/java/com/dlunaunizar/bobitos/feature/meals/MealsCookedViewModelTest.kt
git commit -m "fix(comidas): si «Listo» falla porque alguien ha cambiado un artículo, el diálogo lo nombra"
```

---

## Verificación de punta a punta (coordinador, al final)

- **Suites, en este orden:** GRADLE, NODE y EMU en verde (EMU no cambia, pero se pasa entera, sin otros emuladores de Firebase arrancados).
- **Preparación** (cada proceso largo, en segundo plano):
  1. Emulador Android: `~/Library/Android/sdk/emulator/emulator -avd Pixel_10 -no-snapshot-save`. Espera a `ADB wait-for-device` y a que `ADB shell getprop sys.boot_completed` devuelva `1`.
  2. **INSTR** de `com.dlunaunizar.bobitos.feature.meals.CookedDialogUiTest` (regresión; desinstala la app al acabar).
  3. Emuladores de Firebase: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && export PATH="$JAVA_HOME/bin:$PATH" && npm run emulators`. Espera a que respondan los puertos 8080 y 9099.
  4. `npm run android:connect-emulators` y después `npm run seed:emulators`. Esto deja el usuario `demo-owner`, el espacio «Casa de prueba» (`demo-home`) y «Leche» pendiente en la Compra.
  5. Escribe `SCRATCH/setup.mjs` con este contenido y ejecútalo con `node SCRATCH/setup.mjs`:

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
     // pantryId("Arroz") = "arroz"
     await db.collection("spaces").doc("demo-home").collection("pantryItems").doc("arroz").set({
       name: "Arroz", category: null, createdBy: "demo-owner", createdAt: FieldValue.serverTimestamp(), shoppingItemId: null,
     });
     console.log("usuario demo-owner, receta «Arroz con leche» y «Arroz» en la despensa listos");
     ```

  6. Instala la app debug: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:installDebug`.
- **Recorrido en la app real.** Inicia sesión con `david@bobitos.invalid` / `bobitos123` y haz capturas de cada paso en `SCRATCH/e2e/`. Si en el paso 1 «¿Se ha acabado algo?» no ofrece «Arroz», el id de la despensa no es `arroz`: sácalo leyendo `pantryId`, `slug` y `shoppingMatchKey` (`APP/core/model/`), corrige `setup.mjs` y repite.
  1. **«Cancelar» se lleva el error (M1, con un error de conexión):**
     - Casa de prueba → Comidas → «Nueva comida» → «Elegir de una receta» → «Arroz con leche» → «Guardar».
     - En la tarjeta, «Marcar como cocinada». Sale «Has cocinado Arroz con leche», con «Leche» en «Tachar de la compra» y «Arroz» en «¿Se ha acabado algo?».
     - Marca «Arroz».
     - Activa el modo avión (`ADB shell cmd connectivity airplane-mode enable`) y pulsa «Listo».
     - Esperado: el error sale dentro del diálogo («No hay conexión…») (captura).
     - Pulsa «Cancelar». Esperado: el diálogo se cierra y el aviso de Comidas no muestra ningún error (captura).
     - Desactiva el modo avión (`… airplane-mode disable`) y espera 10 s.
     - Las escrituras de la Compra fallan al momento sin conexión de Android (`FirestoreSyncRepository.requireWritable`), pero el emulador puede conservar red: cortar solo wifi y datos no bastó en un recorrido anterior, porque la app va por `adb reverse`. Si a los 20 s de pulsar «Listo» no ha salido ningún error, anótalo como «no comprobado», pulsa «Cancelar», desactiva el modo avión y sigue.
  2. **«Listo» de punta a punta (Tarea 1, orden nuevo):**
     - En la tarjeta, «Marcar como no cocinada» y otra vez «Marcar como cocinada».
     - Marca «Arroz» y pulsa «Listo». Esperado: el aviso «Compra y despensa al día» (captura).
     - En la Compra: «Leche» comprada y «Arroz» pendiente, una sola vez (captura).
     - En la Despensa: «Arroz» ya no está (captura).
     - Si el paso 1 quedó «no comprobado» y su escritura llegó a hacerse al volver la conexión, «Arroz» ya no saldrá en el diálogo: anótalo y comprueba solo la Compra.
- Después, para los emuladores de Firebase y el emulador Android.

## Despliegue (fuera de este plan)

Aplazado por el usuario. Este plan **no cambia `firestore.rules`**. Lo que ya estaba pendiente sigue igual: las reglas de la Despensa están sin desplegar. Hay que desplegarlas (`npx firebase deploy --only firestore:rules --project dev`) **antes** de distribuir una beta que lleve este trabajo, y desde el mismo commit que se distribuye.
