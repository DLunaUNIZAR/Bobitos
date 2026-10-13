# Menores de la beta 22: Compra, membresía perdida y comunes — Plan de implementación

> **Para agentes:** se ejecuta con `delegating-plan-execution`. Es un **plan de menores** (6 tareas): pasos 1, 2, 3, 5 y 6, **sin `simplify`**. Cada tarea la hace un subagente `sonnet`, en serie y en orden. La revisión final la hace `opus` (si falla, `fable`). **Solo la Tarea 6 toca `firestore.rules`** (permisos); ninguna toca `migrations/`. Los pasos usan casillas (`- [ ]`).

**Objetivo:** cerrar los 6 menores con efecto visible de la sección «2026-10-13 — `agent/menores-compra-y-deshacer-comunes`» de `docs/superpowers/backlog.md`. Los 6 internos de esa sección se quedan en el backlog.

**Arquitectura:**
- **Compra (Tareas 1 a 3):**
  - `ShoppingQuantity` saca mejor el singular de «-ces» tras consonante, de «-nes» (con la tilde de la aguda) y de los diptongos.
  - El editor valida la marca: con más de 60 caracteres, «Guardar» se deshabilita y el campo libre se marca en rojo también sin ingrediente reconocido.
  - La lista con la lectura fallida dice «No se ha podido leer la lista de la compra.» con «Reintentar», nunca el texto de la excepción.
- **Espacio activo (Tarea 4):** al perder la membresía, la app vuelve a «Espacios» en vez de quedarse en una pantalla de error con el texto crudo de Firestore, y se recupera si se la devuelven.
- **Recetario (Tarea 5):** «Deshacer» una común con una edición aún sin confirmar la recrea con la hora del servidor como última edición.
- **Reglas (Tarea 6):** una receta o rutina común solo la editan o borran los admins actuales, no su dueño si ya no es admin.

**Stack:** Kotlin + Jetpack Compose (Material 3) + Hilt + Firestore; reglas de Firestore con tests en el emulador (`@firebase/rules-unit-testing`, `node --test`).

**Spec:** no hay spec propia: cada menor está descrito en `docs/superpowers/backlog.md` (sección «2026-10-13 — `agent/menores-compra-y-deshacer-comunes`»). Contexto: `docs/superpowers/specs/2026-10-12-recetario-y-compra-design.md` (Compra y catálogo de recetas) y el plan anterior, `docs/superpowers/plans/2026-10-13-menores-compra-y-deshacer-comunes.md` (sus rulings 2, 6, 7 y 9 son los que este plan ajusta).

## Qué entra

| # | Menor (backlog) | Tarea |
|---|---|---|
| B3 | El singular aproximado se aplica a adjetivos y nombres raros: «bolsas dulces» → «bolsa dulz»; «comunes» y «alemanes» pierden la tilde | 1 |
| B2 | Editor de la Compra: la marca libre de más de 60 caracteres se marca en rojo, pero «Guardar» sigue y el error llega después (del repositorio o tras «Ya en la lista») | 2 |
| B4 | La lista con un error de lectura no se reintenta; solo vuelve a escuchar al pulsar «Guardar» en el editor | 3 |
| B5 | Al perder la membresía con la Compra abierta: texto crudo «com.google.firebase.firestore.FirebaseFirestoreException: PERMISSION_DENIED…» a pantalla completa, sin navegación, sin recuperación, y «Atrás» sale de la app | 4 |
| B1 | «Deshacer» una común con una edición sin confirmar: la regla la rechaza (la común se pierde) o el importador pisa la edición | 5 |
| B6 | Seguridad: un admin retirado de `recipeAdmins()` aún puede borrar (y editar) sus comunes por la rama de dueño de `ownsOrCuratesRecipe` | 6 |

## Qué no entra

- Los 6 menores **internos** de la sección.
- **«vales» → «val»:** distinguir «vale» de «bol» o «tamal» pide un diccionario. Se queda como limitación (ruling 1); al cerrar, la línea de B3 del backlog se reescribe con solo esa limitación.
- **El plural de las llanas en «-n»** («examen» → «examenes», sin tilde): no es de este menor y no hay unidades así en la Compra.
- **Otras pantallas con el texto crudo de la excepción** (Tareas, Notas, Comidas, Deporte, Recetario, Rutinas, ajustes del espacio: `ErrorState(message = x.message)`): tras la Tarea 4, perder la membresía ya no las deja a la vista. Al cerrar se apunta en el backlog como menor nuevo con efecto visible.
- **Ejercicios:** `ownsOrCuratesExercise` deja editar y borrar al autor siempre. Es el diseño (los ejercicios no tienen visibilidad `GLOBAL`/`PRIVATE`), no el agujero de B6.

## Rulings de planificación

1. **B3, singular de una palabra en plural.** Ajusta el ruling 2 del plan anterior. Orden nuevo:
   - «-ces»: tras **vocal**, «-z» («nueces» → «nuez»); tras **consonante**, solo pierde la «-s» («dulces» → «dulce»).
   - **Diptongo** (a, e u o + i o u) + l, r, n, d o j + «-es»: solo pierde la «-s» («peines» → «peine», «aires» → «aire»).
   - vocal + «-nes»: pierde «-es» y la tilde se pone así:
     - si la raíz lleva tilde (plural esdrújulo), se quita («exámenes» → «examen»);
     - si no la lleva y tiene **dos sílabas o más**, es aguda: tilde en la vocal antes de la «n» («comunes» → «común», «alemanes» → «alemán», «camiones» → «camión»);
     - monosílaba, sin tilde («panes» → «pan», «trenes» → «tren», «guiones» → «guion»).
     - Sustituye a las reglas «-ones» → «-ón» e «-ines» → «-ín».
   - vocal + l, r, d o j + «-es»: pierde «-es» («boles» → «bol», «yogures» → «yogur»).
   - si no, sin la «-s» final.
   - **Sílabas aproximadas:** cada grupo de vocales seguidas cuenta una sílaba por vocal fuerte (a, e, o o cualquiera con tilde), o una si solo tiene débiles («guion» → 1, «leon» → 2).
   - Cada letra conserva su mayúscula («ALEMANES» → «ALEMÁN»).
   - **Limitación que queda:** «vales» → «val», «trajes», «meses».
2. **B2, marca en el editor.**
   - `ShoppingValidation.validate` gana el parámetro `brand` y devuelve `BrandTooLong` si la marca recortada pasa de 60. El orden de los errores es: nombre, cantidad, notas, marca.
   - El editor valida la marca que se guardaría (`draft.savedBrand(context)`): con ella demasiado larga, «Guardar» se deshabilita.
   - El campo libre de marca sin ingrediente reconocido (`SupermarketAndBrandFields`) se marca en rojo con «La marca no puede superar 60 caracteres.», como el de `BrandStorePicker`.
   - El ViewModel también valida la marca en `addItem`, `updateItem` y `sumDuplicate`, antes de buscar repetidos: con la marca demasiado larga no sale el aviso «Ya en la lista».
3. **B4, lista con la lectura fallida.**
   - La lista muestra `ErrorState` con «No se ha podido leer la lista de la compra.» y el botón «Reintentar», que vuelve a escuchar la lista (`observe(spaceId)`).
   - **No hay reintento automático:** un listener de Firestore solo falla por causas permanentes (permiso, índice), y reintentar solo daría vueltas.
   - El ViewModel guarda `UiState.Error()` sin el texto de la excepción.
4. **B5, membresía perdida.**
   - **Causa:** al quitar a un miembro se **borra** su documento de membresía. Las reglas no pueden evaluar `resource.data` de un documento que no existe, así que el listener de ese documento recibe `PERMISSION_DENIED` («evaluation error at L818»). El error cierra el flujo del espacio activo y `BobitosApp` pinta `AppLoadError(error.message)`.
   - **Arreglo en el cliente** (las reglas no cambian):
     - `space(spaceId)` sigue la membresía con la **consulta de membresías del usuario** (`userId == uid`, `status == ACTIVE`, la de `spaces()`), no con el documento. Al quitarla, la consulta solo deja de incluirla y el espacio pasa a `null`: `BobitosNavHost` ya manda a «Espacios» cuando `selectedSpace` es `null`. Si se la devuelven, la consulta la vuelve a incluir y el espacio vuelve.
     - El listener del documento del espacio, con `PERMISSION_DENIED`, emite `null` en vez de fallar: puede llegar antes que el cambio de la consulta.
     - `FirestoreSyncRepository.refresh` lee la membresía con una consulta (`userId`, `spaceId`, `status`, `limit(1)`) en vez de con el documento. Si no, con la membresía perdida falla cada 3 s y la app se queda «sin conexión».
     - `AppLoadError` muestra siempre «No se ha podido iniciar Bobitos.», nunca el texto de la excepción.
   - No es un cambio de permisos (las reglas no cambian): un arreglo posterior a esta tarea **no** lleva re-revisión, salvo que toque `firestore.rules`.
5. **B1, «Deshacer» con la edición sin confirmar.**
   - `Recipe` gana `updatePending: Boolean = false`. `toRecipe` lo pone a `true` si el documento tiene `updatedAt`, pero aún sin valor (hora del servidor pendiente).
   - Al deshacer una **común**, la última edición es `null` (hora del servidor) si `updatePending`, o si `importedAt` es posterior a `updatedAt`. Así cumple la regla (`importedAt ≤ updatedAt`) y el importador la sigue viendo editada en la app, sin pisar la edición.
   - Una común importada sin editar (`updatedAt == importedAt`) conserva su fecha, y el importador la sigue actualizando. Una privada vuelve como antes.
6. **B6, admin retirado.**
   - `ownsOrCuratesRecipe()` y `ownsOrCuratesRoutine()`: una `GLOBAL` solo la edita o borra `isRecipeAdmin()`; una `PRIVATE`, solo su dueño.
   - **Las rutinas entran** porque tienen el mismo agujero, con la misma regla.
   - La anonimización del autor al borrar la cuenta (`validRecipeAnonymization`, `validRoutineAnonymization`) no cambia: un admin retirado puede seguir anonimizando sus comunes.
   - La app ya no ofrece editar ni borrar una común a quien no es admin (`RecipeSections`, `RoutinesScreen`): no cambia.
   - Toca **permisos**: un arreglo posterior que toque estas reglas lleva re-revisión acotada.

## Restricciones globales

- **Rutas:**
  - `APP` = `app/src/main/java/com/dlunaunizar/bobitos/`
  - `UT` = `app/src/test/java/com/dlunaunizar/bobitos/`
  - `RES` = `app/src/main/res/values/strings.xml`
- **Comandos de verificación**, en llamadas separadas y sin `sh -c`:
  - **GRADLE:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:assembleDebug :app:ktlintCheck :app:detekt :app:testDebugUnitTest`
  - **JVM (una clase):** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:testDebugUnitTest --tests "<clase completa>"`
  - **EMU (reglas):** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && npm run test:emulators`
  - **NODE:** `npm run test:scripts`
  - Si ktlint marca formato: `./gradlew :app:ktlintFormat` (con el mismo `JAVA_HOME`) y revisar el diff antes del commit.
- **Prohibido:**
  - cualquier script contra `bobitos-dev` (`--project dev` o `bobitos-dev`);
  - desplegar reglas, importar, distribuir betas o hacer push;
  - descargar nada;
  - tocar `firestore.rules` fuera de la Tarea 6.
- **Commits con ficheros con nombre:** nunca `git add -A` ni `git add .`.
- **Textos y comentarios en español**, con el estilo de los ficheros vecinos. Textos de la app en `RES`.
- **Tests Kotlin:** JVM puros (JUnit 4 + `kotlinx-coroutines-test`), dobles a mano; sin mockk ni Robolectric. **Orden TDD obligatorio:** test primero, verlo en rojo (un fallo de compilación por lo que aún no existe también vale; copiar el fallo en el informe), después el código. El código de listeners de Firestore no tiene test JVM posible: cada tarea dice qué parte se prueba y cómo.
- **detekt:**
  - Límites: `ReturnCount` máx. 4, `LongMethod` máx. 80 líneas, `TooManyFunctions` 25 (por clase y por fichero). `MagicNumber` está desactivado.
  - Extraer código antes que tocar `config/detekt/baseline.xml`.
  - **No cambies estas firmas, que están en el baseline:** `ShoppingScreen(...)`, `BobitosApp(...)`, `DocumentSnapshot.toRecipe()`, `FirestoreSpaceRepository.removeMembership(...)` y `runSpaceOperation(...)`.
  - `ShoppingItemEditor` **no** está en el baseline (la entrada que hay es de una firma antigua): si pasa de 80 líneas, se extrae código.
  - No añadas `catch (Throwable)` nuevos.
- **ktlint:** sin imports sin usar; línea ≤120.
- **Tests existentes:** solo se cambian los que fijan el comportamiento que este plan sustituye. Cada tarea dice cuáles y cómo.

## Review Focus

1. **Perder la membresía con la Compra (u otra pantalla del espacio) abierta:** la app vuelve a «Espacios», sin texto técnico. Al devolver la membresía, el espacio reaparece en la lista y se puede volver a entrar. La barra no se queda «sin conexión». → `ActiveSpaceTest` y el test de reglas de la Tarea 4; recorrido en el emulador.
2. **La lectura del espacio denegada antes de que la consulta de membresías quite la membresía** (carrera entre los dos listeners): el espacio pasa a `null` y no hay pantalla de error. → Tarea 4, `observeSpace` (sin test JVM: listener de Firestore); recorrido en el emulador.
3. **Marca de exactamente 60 caracteres con espacios en los extremos:** pasa. Con 61 tras recortar, no pasa, y con el artículo ya pendiente no sale el aviso «Ya en la lista». → `ShoppingValidationTest` y `ShoppingViewModelTest` (Tarea 2).
4. **Unidades en mayúsculas o monosílabas:** «1/2 PANES ALEMANES» × 2 → «1 PAN ALEMÁN»; «1/2 trenes» × 2 → «1 tren»; «1/2 guiones» × 2 → «1 guion». → `ShoppingQuantityTest` (Tarea 1).
5. **Deshacer una común importada que nunca se editó:** conserva `updatedAt == importedAt` y el importador la sigue actualizando. No pasa a la hora del servidor por error. → `RecipeRestoreTest` (Tarea 5).

---

### Task 1: Compra: singular de «-ces», «-nes» y diptongos

**Ficheros:**
- Modificar: `APP/core/model/ShoppingQuantity.kt` (solo el objeto privado `UnitNumber`: `singularOf` y funciones privadas nuevas).
- Test: `UT/core/model/ShoppingQuantityTest.kt`.

**Interfaces:**
- Consume: nada nuevo.
- Produce: `ShoppingQuantity.sum` con el singular nuevo; ninguna firma pública cambia.

- [ ] **Step 1: Test.** Añadir al final de `ShoppingQuantityTest`:

```kotlin
    @Test
    fun `the singular of -ces after a consonant only drops the s`() {
        assertEquals("1 bolsa dulce", ShoppingQuantity.sum("1/2 bolsas dulces", "1/2 bolsas dulces"))
        assertEquals("1 nuez", ShoppingQuantity.sum("1/2 nueces", "1/2 nueces"))
    }

    @Test
    fun `the singular of a plural in -nes gets the accent of its stress`() {
        assertEquals("1 bolsa común", ShoppingQuantity.sum("1/2 bolsas comunes", "1/2 bolsas comunes"))
        assertEquals("1 pan alemán", ShoppingQuantity.sum("1/2 panes alemanes", "1/2 panes alemanes"))
        assertEquals("1 camión", ShoppingQuantity.sum("1/2 camiones", "1/2 camiones"))
        assertEquals("1 examen", ShoppingQuantity.sum("1/2 exámenes", "1/2 exámenes"))
        assertEquals("1 tren", ShoppingQuantity.sum("1/2 trenes", "1/2 trenes"))
        assertEquals("1 guion", ShoppingQuantity.sum("1/2 guiones", "1/2 guiones"))
        assertEquals("1 limón", ShoppingQuantity.sum("1/2 limones", "1/2 limones"))
        assertEquals("1 botellín", ShoppingQuantity.sum("1/2 botellines", "1/2 botellines"))
    }

    @Test
    fun `the singular keeps the capital letters and puts the accent in capitals`() {
        assertEquals("1 PAN ALEMÁN", ShoppingQuantity.sum("1/2 PANES ALEMANES", "1/2 PANES ALEMANES"))
    }

    @Test
    fun `the singular of a diphthong before the consonant keeps the e`() {
        assertEquals("1 peine", ShoppingQuantity.sum("1/2 peines", "1/2 peines"))
        assertEquals("1 baile", ShoppingQuantity.sum("1/2 bailes", "1/2 bailes"))
    }
```

- [ ] **Step 2: Rojo.** JVM `com.dlunaunizar.bobitos.core.model.ShoppingQuantityTest`. Esperado: fallan las cuatro (p. ej. `expected:<1 bolsa [dulce]> but was:<1 bolsa [dulz]>`; «común» sale «comun», «peine» sale «peín» y «baile» sale «bail»). El resto de la clase sigue en verde.

- [ ] **Step 3: Código.** En `UnitNumber` (`ShoppingQuantity.kt`):

  a) Junto a las constantes del objeto, añadir:

```kotlin
    private const val STRONG_VOWELS = "aeoáéíóú"
    private val VOWEL_RUN = Regex("[aeiouáéíóúü]+")
    private val ACUTE_VOWELS = mapOf('a' to 'á', 'e' to 'é', 'i' to 'í', 'o' to 'ó', 'u' to 'ú')
```

  b) Sustituir `singularOf` y su comentario por:

```kotlin
    // Singular aproximado de una palabra en plural:
    // - vocal + «-ces» → «-z» («nueces» → «nuez»); consonante + «-ces» → sin la «-s» («dulces» → «dulce»);
    // - diptongo + l, r, n, d o j + «-es» → sin la «-s» («peines» → «peine», «aires» → «aire»);
    // - vocal + «-nes» → sin «-es», con la tilde de la aguda («comunes» → «común»), sin la de la esdrújula
    //   («exámenes» → «examen») y sin tilde en la monosílaba («panes» → «pan»);
    // - vocal + l, r, d o j + «-es» → sin «-es» («boles» → «bol», «yogures» → «yogur»);
    // - si no, sin la «-s» final («sobres» → «sobre»).
    // Falla en palabras raras como unidad («vales» → «val», «trajes», «meses»).
    fun singularOf(word: String): String {
        val lower = word.lowercase()
        return when {
            lower.endsWith("ces") -> if (lower.vowelAt(lower.length - 4)) "${word.dropLast(3)}z" else word.dropLast(1)
            lower.keepsEAfterDiphthong() -> word.dropLast(1)
            lower.dropsNes() -> word.dropLast(2).withStressOfFinalN()
            lower.dropsEs() -> word.dropLast(2)
            lower.endsWith("s") -> word.dropLast(1)
            else -> word
        }
    }

    private fun String.vowelAt(index: Int): Boolean = index >= 0 && this[index] in VOWELS

    // Diptongo + l, r, n, d o j + «-es»: el singular conserva la «e» («peines» → «peine», «aires» → «aire»).
    private fun String.keepsEAfterDiphthong(): Boolean =
        length >= 5 && endsWith("es") && this[length - 3] in ES_PLURAL_CONSONANTS &&
            this[length - 4] in "iu" && this[length - 5] in "aeo"

    // Vocal + «-nes»: el singular acaba en «-n» («panes» → «pan», «comunes» → «común»).
    private fun String.dropsNes(): Boolean = length >= 4 && endsWith("nes") && vowelAt(length - 4)

    // Tilde de un singular en vocal + «n»: si viene de una esdrújula, la pierde («exámen» → «examen»); sin tilde y con dos
    // sílabas o más, es aguda y la lleva en la vocal antes de la «n» («comun» → «común»); la monosílaba, no («pan»).
    private fun String.withStressOfFinalN(): String = when {
        any { it.lowercaseChar() in PLAIN_VOWELS } -> map { it.swapped(PLAIN_VOWELS) }.joinToString("")
        syllables() < 2 -> this
        else -> substring(0, length - 2) + this[length - 2].swapped(ACUTE_VOWELS) + last()
    }

    // Sílabas aproximadas: cada grupo de vocales seguidas cuenta una por vocal fuerte (a, e, o o con tilde), o una si
    // solo tiene débiles («guion» → 1, «leon» → 2, «camion» → 2).
    private fun String.syllables(): Int =
        VOWEL_RUN.findAll(lowercase()).sumOf { run -> run.value.count { it in STRONG_VOWELS }.coerceAtLeast(1) }

    // Cambia la vocal según [vowels] (claves en minúscula) y conserva la mayúscula.
    private fun Char.swapped(vowels: Map<Char, Char>): Char {
        val swapped = vowels[lowercaseChar()] ?: return this
        return if (isUpperCase()) swapped.uppercaseChar() else swapped
    }
```

  c) `dropsEs`, `pluralOf`, `withoutFinalAccent`, `ES_PLURAL_CONSONANTS` y `PLAIN_VOWELS` no cambian. Las ramas de «-n» de `dropsEs` ya no se alcanzan porque `dropsNes` va antes; no hace falta tocarlas.

- [ ] **Step 4: Verde.** JVM `com.dlunaunizar.bobitos.core.model.ShoppingQuantityTest` (toda la clase, incluidos los tests anteriores de «limones», «botellines», «panes» y «nueces»). Después, GRADLE.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/core/model/ShoppingQuantity.kt app/src/test/java/com/dlunaunizar/bobitos/core/model/ShoppingQuantityTest.kt
git commit -m "fix(compra): el singular de «-ces» tras consonante, de «-nes» con su tilde y de los diptongos"
```

---

### Task 2: Compra: la marca demasiado larga no deja guardar en el editor

**Ficheros:**
- Modificar: `APP/feature/shopping/ShoppingValidation.kt` (`validate`).
- Modificar: `APP/feature/shopping/ShoppingViewModel.kt` (`validate` privada, `addItem`, `updateItem` y `sumDuplicate`).
- Modificar: `APP/feature/shopping/ShoppingScreen.kt` (`ShoppingItemEditor` y `SupermarketAndBrandFields`).
- Test: `UT/feature/shopping/ShoppingValidationTest.kt` y `UT/feature/shopping/ShoppingViewModelTest.kt`.

**Interfaces:**
- Consume: `freeBrandTooLong(text: String): Boolean` (`APP/feature/common/BrandStoreSelection.kt`, ya existe) y `ShoppingDraft.savedBrand(context)` (ya existe).
- Produce: `ShoppingValidation.validate(name: String, quantity: String?, notes: String?, brand: String? = null): ShoppingUiMessage?`.

- [ ] **Step 1: Tests.**

  a) Al final de `ShoppingValidationTest`:

```kotlin
    @Test
    fun `the brand is limited to 60 characters once trimmed`() {
        assertNull(ShoppingValidation.validate("Pan", null, null, "  " + "M".repeat(60) + "  "))
        assertEquals(
            ShoppingUiMessage.BrandTooLong,
            ShoppingValidation.validate("Pan", null, null, "M".repeat(61)),
        )
        assertNull(ShoppingValidation.validate("Pan", null, null, null))
    }
```

  b) En `ShoppingViewModelTest`, detrás de `adding while the list keeps failing to load never writes and listens again each time`:

```kotlin
    @Test
    fun `a brand over 60 characters is refused in the editor before looking for repeats or writing`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.observe("home")
            advanceUntilIdle()
            val longBrand = "M".repeat(61)

            viewModel.addItem("home", "Leche", "1", null, null, longBrand)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.duplicate)
            assertNull(repository.applied)
            assertEquals(ShoppingUiMessage.BrandTooLong, viewModel.uiState.value.error)
            assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.editorSave)

            viewModel.addItem("home", "Pan", "1", null, null, longBrand)
            advanceUntilIdle()
            viewModel.updateItem("home", "milk", "Leche", "1", null, null, longBrand)
            advanceUntilIdle()

            assertNull(repository.addedName)
            assertNull(repository.updatedItemId)
            assertEquals(ShoppingUiMessage.BrandTooLong, viewModel.uiState.value.error)
        }
```

  La lista del doble empieza con «Leche» pendiente (`shoppingItem()`, id `milk`): hoy el primer `addItem` deja el aviso «Ya en la lista» en vez del error.

- [ ] **Step 2: Rojo.** JVM `com.dlunaunizar.bobitos.feature.shopping.ShoppingValidationTest` (no compila: `validate` no tiene cuarto parámetro) y JVM `com.dlunaunizar.bobitos.feature.shopping.ShoppingViewModelTest` (falla el test nuevo: `duplicate` no es null). Copiar los dos fallos en el informe.

- [ ] **Step 3: Código.**

  a) `ShoppingValidation.validate`:

```kotlin
    fun validate(name: String, quantity: String?, notes: String?, brand: String? = null): ShoppingUiMessage? = when {
        name.isBlank() -> ShoppingUiMessage.NameRequired
        name.trim().length > MAX_NAME_LENGTH -> ShoppingUiMessage.NameTooLong
        quantity?.trim()?.length?.let { it > MAX_QUANTITY_LENGTH } == true -> {
            ShoppingUiMessage.QuantityTooLong
        }
        notes?.trim()?.length?.let { it > MAX_NOTES_LENGTH } == true -> {
            ShoppingUiMessage.NotesTooLong
        }
        brand?.trim()?.length?.let { it > MAX_BRAND_LENGTH } == true -> ShoppingUiMessage.BrandTooLong
        else -> null
    }
```

  b) `ShoppingViewModel`:
     - La `validate` privada gana `brand: String?` y lo pasa: `ShoppingValidation.validate(name, quantity, notes, brand)`.
     - `addItem`: `if (!validate(name, quantity, notes, brand)) return`. Sigue siendo la primera línea, antes de `itemsForAdd`.
     - `updateItem`: `if (!validate(name, quantity, notes, brand)) return`.
     - `sumDuplicate`: subir `val (store, brand) = duplicate.storeAndBrandAfterSum()` por encima de la validación y validar con `validate(existing.name, duplicate.summed, notes, brand)`.

  c) `ShoppingItemEditor` (`ShoppingScreen.kt`):
     - Mover el bloque de `context` (el comentario «Solo al crear: un ingrediente reconocido propone marca y tienda (§5).», `val context = remember(...)` y su `LaunchedEffect`) justo encima de `val validation`.
     - Validar con la marca que se guardaría:

```kotlin
    val validation = ShoppingValidation.validate(name, quantity, notes, draft.savedBrand(context))
```

  d) `SupermarketAndBrandFields`: el campo de marca se marca como el de `BrandStorePicker`:

```kotlin
    val tooLong = freeBrandTooLong(brand)
    OutlinedTextField(
        value = brand,
        onValueChange = onBrand,
        label = { Text(stringResource(R.string.shopping_brand_label)) },
        singleLine = true,
        isError = tooLong,
        supportingText = if (tooLong) {
            { Text(stringResource(R.string.shopping_error_brand_too_long)) }
        } else {
            null
        },
    )
```

  Con el import `com.dlunaunizar.bobitos.feature.common.freeBrandTooLong`. Si detekt marca `LongMethod` en `ShoppingItemEditor`, extraer a una función privada el `if (context != null) BrandStorePicker(...) else SupermarketAndBrandFields(...)`.

- [ ] **Step 4: Verde.** Las dos clases JVM del Step 2 y después GRADLE.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingValidation.kt app/src/main/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingViewModel.kt app/src/main/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingScreen.kt app/src/test/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingValidationTest.kt app/src/test/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingViewModelTest.kt
git commit -m "fix(compra): una marca de más de 60 caracteres no deja guardar en el editor"
```

---

### Task 3: Compra: la lista con la lectura fallida ofrece «Reintentar»

**Ficheros:**
- Modificar: `APP/feature/shopping/ShoppingViewModel.kt` (`observe`).
- Modificar: `APP/feature/shopping/ShoppingScreen.kt` (rama `is UiState.Error` de `ShoppingScreen`).
- Modificar: `RES` (string nueva).
- Test: `UT/feature/shopping/ShoppingViewModelTest.kt`.

**Interfaces:**
- Consume: `ErrorState(modifier, message, onRetry)` (`APP/core/designsystem/component/StateViews.kt`, ya existe) y `ShoppingViewModel.observe(spaceId)`.
- Produce: `R.string.shopping_error_list_read`. La lista con la lectura fallida queda en `UiState.Error()` (sin mensaje).

- [ ] **Step 1: Test.**

  a) En `FakeShoppingRepository.items` (`ShoppingViewModelTest.kt`), la escucha que falla lleva una causa con texto, como la excepción real de Firestore:

```kotlin
            return flow {
                throw ShoppingRepositoryException(
                    ShoppingFailure.PermissionDenied,
                    IllegalStateException("PERMISSION_DENIED: evaluation error at L818"),
                )
            }
```

  b) Detrás de `adding while the list keeps failing to load never writes and listens again each time`:

```kotlin
    @Test
    fun `a failed read of the list keeps no exception text and observing again loads it`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.itemsFailures = 1
            viewModel.observe("home")
            advanceUntilIdle()

            assertEquals(UiState.Error(), viewModel.uiState.value.items)

            viewModel.observe("home")
            advanceUntilIdle()

            assertEquals(2, repository.itemsCalls)
            assertEquals("milk", (viewModel.uiState.value.items as UiState.Content).value.single().id)
        }
```

- [ ] **Step 2: Rojo.** JVM `com.dlunaunizar.bobitos.feature.shopping.ShoppingViewModelTest`. Esperado: falla el test nuevo (`expected:<Error(message=null)> but was:<Error(message=java.lang.IllegalStateException: PERMISSION_DENIED…)>`). Los demás siguen en verde.

- [ ] **Step 3: Código.**

  a) `ShoppingViewModel.observe`, en el `catch`:

```kotlin
                .catch {
                    // Sin el texto de la excepción (técnico y en inglés): la pantalla dice que no se ha podido leer la
                    // lista y ofrece «Reintentar».
                    mutableUiState.update { state -> state.copy(items = UiState.Error()) }
                }
```

  b) `RES`, junto a `shopping_error_list_unavailable`:

```xml
    <string name="shopping_error_list_read">No se ha podido leer la lista de la compra.</string>
```

  c) `ShoppingScreen`, rama de error de la lista:

```kotlin
                is UiState.Error -> ErrorState(
                    modifier = Modifier.weight(1f),
                    message = stringResource(R.string.shopping_error_list_read),
                    onRetry = { viewModel.observe(spaceId) },
                )
```

  Con el import `com.dlunaunizar.bobitos.core.designsystem.component.ErrorState`. «Reintentar» funciona porque, tras el `catch`, el trabajo de la escucha ha terminado (`itemsJob?.isActive == false`) y `observe` vuelve a escuchar.

- [ ] **Step 4: Verde.** JVM `com.dlunaunizar.bobitos.feature.shopping.ShoppingViewModelTest` y después GRADLE.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingViewModel.kt app/src/main/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingScreen.kt app/src/main/res/values/strings.xml app/src/test/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingViewModelTest.kt
git commit -m "fix(compra): la lista que no se ha podido leer lo dice y ofrece «Reintentar»"
```

---

### Task 4: Espacio activo: perder la membresía vuelve a «Espacios» y se recupera

**Ficheros:**
- Crear: `APP/data/repository/MembershipSnapshot.kt` (`MembershipSnapshot`, que sale de `FirestoreSpaceRepository`, y `activeSpaceOf`).
- Modificar: `APP/data/repository/FirestoreSpaceRepository.kt` (`space`, `observeSpace`; se borran `observeMembership` y la clase anidada `MembershipSnapshot`).
- Modificar: `APP/data/sync/FirestoreSyncRepository.kt` (`refresh`).
- Modificar: `APP/app/BobitosApp.kt` (`AppLoadError` y sus dos llamadas).
- Test: `UT/data/repository/ActiveSpaceTest.kt` (nuevo) y `tests/firebase-emulators.test.mjs` (test nuevo, sin tocar reglas).

**Interfaces:**
- Consume: nada de tareas anteriores.
- Produce:
  - `internal data class MembershipSnapshot(val spaceId: String, val role: SpaceRole)`;
  - `internal fun activeSpaceOf(memberships: Flow<List<MembershipSnapshot>>, spaceId: String, observeSpace: (MembershipSnapshot) -> Flow<SpaceSummary?>): Flow<SpaceSummary?>`.
  - `SpaceRepository` no cambia.

- [ ] **Step 1: Test de reglas** (fija la premisa del arreglo; las reglas no cambian, así que pasa ya). En `tests/firebase-emulators.test.mjs`, detrás de `el propietario puede expulsar a un miembro con la misma operación segura`:

```js
test("al quitar a un miembro, su membresía y el espacio ya no se leen por documento, pero sus consultas de membresías sí, sin ella", async () => {
  await seedSpace("lost-space", "lost-owner", ["lost-member"]);
  const member = verifiedFirestore("lost-member");
  const memberships = collection(member, "memberships");
  const mine = query(memberships, where("userId", "==", "lost-member"), where("status", "==", "ACTIVE"));
  const mineHere = query(
    memberships,
    where("userId", "==", "lost-member"),
    where("spaceId", "==", "lost-space"),
    where("status", "==", "ACTIVE"),
  );
  assert.equal((await getDocs(mine)).size, 1);

  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await deleteDoc(doc(context.firestore(), "memberships", "lost-space_lost-member"));
  });

  await assertFails(getDoc(doc(member, "memberships", "lost-space_lost-member")));
  await assertFails(getDoc(doc(member, "spaces", "lost-space")));
  assert.equal((await assertSucceeds(getDocs(mine))).size, 0);
  assert.equal((await assertSucceeds(getDocs(mineHere))).size, 0);
});
```

- [ ] **Step 2: EMU.** Esperado: el test nuevo **pasa ya** (fija la conducta de las reglas en la que se apoya el arreglo). Si `getDoc` de la membresía borrada **no** falla, se para y se informa: la causa del ruling 4 sería otra.

- [ ] **Step 3: Test JVM.** Crear `UT/data/repository/ActiveSpaceTest.kt`:

```kotlin
package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.SpaceRole
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveSpaceTest {
    private val home = MembershipSnapshot("home", SpaceRole.MEMBER)
    private val other = MembershipSnapshot("other", SpaceRole.OWNER)

    private fun summaryOf(membership: MembershipSnapshot) =
        SpaceSummary(membership.spaceId, "Casa", 2, membership.role)

    @Test
    fun `the active space is null while its membership is gone and comes back with it`() = runTest {
        val memberships = MutableStateFlow(listOf(home, other))
        val seen = mutableListOf<SpaceSummary?>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            activeSpaceOf(memberships, "home") { flowOf(summaryOf(it)) }.toList(seen)
        }

        memberships.value = listOf(other)
        runCurrent()
        memberships.value = listOf(home, other)
        runCurrent()
        job.cancel()

        assertEquals(listOf(summaryOf(home), null, summaryOf(home)), seen)
    }

    @Test
    fun `without its membership from the start the active space is null`() = runTest {
        val seen = mutableListOf<SpaceSummary?>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            activeSpaceOf(MutableStateFlow(listOf(other)), "home") { flowOf(summaryOf(it)) }.toList(seen)
        }
        job.cancel()

        assertEquals(listOf<SpaceSummary?>(null), seen)
    }

    @Test
    fun `changes in other memberships do not listen to the active space again`() = runTest {
        val memberships = MutableStateFlow(listOf(home))
        var listened = 0
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            activeSpaceOf(memberships, "home") {
                listened++
                flowOf(summaryOf(it))
            }.collect {}
        }

        memberships.value = listOf(home, other)
        runCurrent()
        job.cancel()

        assertEquals(1, listened)
    }
}
```

- [ ] **Step 4: Rojo.** JVM `com.dlunaunizar.bobitos.data.repository.ActiveSpaceTest`. Esperado: no compila (`activeSpaceOf` no existe y `MembershipSnapshot` es privada de `FirestoreSpaceRepository`). Copiar el fallo.

- [ ] **Step 5: Código.**

  a) Crear `APP/data/repository/MembershipSnapshot.kt`:

```kotlin
package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.SpaceRole
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Membresía activa del usuario en un espacio. */
internal data class MembershipSnapshot(val spaceId: String, val role: SpaceRole)

/**
 * El espacio [spaceId], leído con [observeSpace], mientras las membresías activas del usuario ([memberships]) incluyan la
 * suya; null mientras no la incluyan. Si la membresía vuelve, el espacio también.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun activeSpaceOf(
    memberships: Flow<List<MembershipSnapshot>>,
    spaceId: String,
    observeSpace: (MembershipSnapshot) -> Flow<SpaceSummary?>,
): Flow<SpaceSummary?> = memberships
    .map { list -> list.firstOrNull { it.spaceId == spaceId } }
    .distinctUntilChanged()
    .flatMapLatest { membership -> if (membership == null) flowOf(null) else observeSpace(membership) }
```

  b) `FirestoreSpaceRepository`:
     - Borrar `private data class MembershipSnapshot(...)` del final de la clase. Los `MembershipSnapshot(spaceId, role)` existentes usan ahora la del fichero nuevo, del mismo paquete.
     - `space(spaceId)`:

```kotlin
    override fun space(spaceId: String): Flow<SpaceSummary?> = authRepository.currentUser.flatMapLatest { user ->
        if (user == null || !user.isEmailVerified) {
            flowOf(null)
        } else {
            // Por la consulta de membresías del usuario y no por el documento de la membresía: al quitarla se borra y
            // las reglas ya no dejan leer ese documento (PERMISSION_DENIED). La consulta solo deja de incluirla, y la
            // vuelve a incluir si se la devuelven.
            activeSpaceOf(membershipsForUser(user.id), spaceId) { membership ->
                observeSpace(membership, metricScope = "space:active")
            }
        }
    }
```

     - Borrar `observeMembership` (ya no se usa). `membershipReference` se queda: la usan `removeMembership` y otras.
     - `observeSpace`, en el `when` del listener, antes de la rama `error != null`:

```kotlin
                    // Sin permiso para leer el espacio es que se ha perdido la membresía (su consulta lo confirmará): el
                    // espacio deja de verse en vez de romper la app, y se vuelve a escuchar si la membresía vuelve.
                    error?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> trySend(null)
                    error != null -> close(error.toSpaceRepositoryException())
```

  c) `FirestoreSyncRepository.refresh`, rama `else` (con `spaceId`). Se lee la membresía por consulta:

```kotlin
                } else {
                    // Por consulta y no por el documento: con la membresía perdida, su documento ya no existe y las
                    // reglas no dejan leerlo (PERMISSION_DENIED); la sincronización se quedaba «sin conexión».
                    val membership = firestore.collection(MEMBERSHIPS)
                        .whereEqualTo(FIELD_USER_ID, user.id)
                        .whereEqualTo(FIELD_SPACE_ID, spaceId)
                        .whereEqualTo(FIELD_STATUS, STATUS_ACTIVE)
                        .limit(1)
                        .get(Source.SERVER)
                        .await()
                    if (!membership.isEmpty) {
                        firestore.collection(SPACES)
                            .document(spaceId)
                            .get(Source.SERVER)
                            .await()
                        recordRefreshReads(2, "space:active")
                    } else {
                        recordRefreshReads(1, "membership:active")
                    }
                }
```

  Con `const val FIELD_SPACE_ID = "spaceId"` en su `companion`. Son tres igualdades, así que Firestore no pide índice compuesto.

  d) `BobitosApp.kt`: `AppLoadError` sin parámetro y sus dos llamadas como `AppLoadError()`:

```kotlin
// Error al arrancar o al leer los espacios: un texto fijo, nunca el de la excepción (técnico y en inglés).
@Composable
private fun AppLoadError() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = stringResource(R.string.app_start_error))
    }
}
```

  e) `grep -rn "membership:active\|observeMembership" app/src` no debe dar nada en `FirestoreSpaceRepository.kt`. En `FirestoreSyncRepository.kt` queda la métrica `"membership:active"`, que sigue siendo válida.

- [ ] **Step 6: Verde.** JVM `com.dlunaunizar.bobitos.data.repository.ActiveSpaceTest`, JVM `com.dlunaunizar.bobitos.app.AppViewModelTest`, GRADLE y EMU.

- [ ] **Step 7: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/data/repository/MembershipSnapshot.kt app/src/main/java/com/dlunaunizar/bobitos/data/repository/FirestoreSpaceRepository.kt app/src/main/java/com/dlunaunizar/bobitos/data/sync/FirestoreSyncRepository.kt app/src/main/java/com/dlunaunizar/bobitos/app/BobitosApp.kt app/src/test/java/com/dlunaunizar/bobitos/data/repository/ActiveSpaceTest.kt tests/firebase-emulators.test.mjs
git commit -m "fix(espacios): perder la membresía vuelve a «Espacios» y se recupera, sin el error crudo de Firestore"
```

---

### Task 5: Recetario: «Deshacer» una común con la edición sin confirmar

**Ficheros:**
- Modificar: `APP/core/model/Recipe.kt` (campo `updatePending`).
- Modificar: `APP/data/repository/FirestoreRecipeRepository.kt` (`DocumentSnapshot.toRecipe()`, sin cambiar su firma).
- Modificar: `APP/data/repository/RecipeRestore.kt` (`restoreAudit` y una función privada nueva).
- Modificar: `docs/DATA_MODEL.md` (párrafo «Catálogo común importado» de recetas).
- Test: `UT/data/repository/RecipeRestoreTest.kt`.

**Interfaces:**
- Consume: nada de tareas anteriores.
- Produce: `Recipe.updatePending: Boolean = false` (último parámetro del constructor, detrás de `importedAt`).

- [ ] **Step 1: Test.** Al final de `RecipeRestoreTest`:

```kotlin
    @Test
    fun `a common recipe whose last edit is still pending comes back edited at the server time`() {
        val pending = recipe(RecipeVisibility.GLOBAL, importedAt = imported)
            .copy(updatedAt = created, updatePending = true)
        val madeInApp = recipe(RecipeVisibility.GLOBAL).copy(updatePending = true)

        val audit = pending.restoreAudit("catalog-admin", admins)

        assertNull(audit.updatedAt)
        assertEquals(created, audit.createdAt)
        assertEquals(imported, audit.importedAt)
        assertNull(madeInApp.restoreAudit("catalog-admin", admins).updatedAt)
    }

    @Test
    fun `a common recipe read with an edit date before its import comes back edited at the server time`() {
        val stale = recipe(RecipeVisibility.GLOBAL, importedAt = imported).copy(updatedAt = created)

        assertNull(stale.restoreAudit("catalog-admin", admins).updatedAt)
    }

    @Test
    fun `an imported common recipe never edited in the app keeps its last edit at the import date`() {
        val audit = recipe(RecipeVisibility.GLOBAL, importedAt = imported).restoreAudit("catalog-admin", admins)

        assertEquals(imported, audit.updatedAt)
        assertEquals(imported, audit.importedAt)
    }
```

- [ ] **Step 2: Rojo.** JVM `com.dlunaunizar.bobitos.data.repository.RecipeRestoreTest`. Esperado: no compila (`updatePending` no existe). Copiar el fallo.

- [ ] **Step 3: Código.**

  a) `Recipe.kt`, detrás de `importedAt`:

```kotlin
    // La última edición aún no ha llegado al servidor (`updatedAt` pendiente): «Deshacer» usa la hora del servidor.
    val updatePending: Boolean = false,
```

  b) `toRecipe()` (`FirestoreRecipeRepository.kt`):

```kotlin
    val updatedStamp = getTimestamp("updatedAt")
    val updatedAt = updatedStamp?.toDate()?.toInstant() ?: createdAt
```

  y en el constructor, detrás de `importedAt = catalogImportedAt(),`:

```kotlin
        // Una edición aún sin confirmar deja `updatedAt` con la hora del servidor pendiente, que se lee null.
        updatePending = updatedStamp == null && contains("updatedAt"),
```

  c) `RecipeRestore.kt`: en la rama `GLOBAL`, `RecipeRestoreAudit(owner, owner, createdAt, editor, lastEditToRestore(), importedAt)`, y al final del fichero:

```kotlin
// Última edición con que vuelve una común: null (hora del servidor) si aún no había llegado al servidor o si la fecha leída
// es anterior a la importación (las reglas exigen importedAt ≤ updatedAt). Así cumple las reglas y el importador la sigue
// viendo editada en la app, sin pisar la edición.
private fun Recipe.lastEditToRestore(): Instant? =
    updatedAt.takeUnless { updatePending || (importedAt != null && importedAt > it) }
```

  En el KDoc de `restoreAudit`, después de «(dueño, autor, fechas y `source.importedAt`)», añadir: «, salvo una edición aún sin confirmar, que pasa a la hora del servidor».

  d) `docs/DATA_MODEL.md`, párrafo «Catálogo común importado» de recetas: «una común, tal como era (dueño, autor, fechas y `source`)» pasa a «una común, tal como era (dueño, autor, fechas y `source`; si su última edición aún no había llegado al servidor, con la hora del servidor)».

- [ ] **Step 4: Verde.** JVM `com.dlunaunizar.bobitos.data.repository.RecipeRestoreTest` y después GRADLE.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/core/model/Recipe.kt app/src/main/java/com/dlunaunizar/bobitos/data/repository/FirestoreRecipeRepository.kt app/src/main/java/com/dlunaunizar/bobitos/data/repository/RecipeRestore.kt app/src/test/java/com/dlunaunizar/bobitos/data/repository/RecipeRestoreTest.kt docs/DATA_MODEL.md
git commit -m "fix(recetas): «Deshacer» una común con la edición sin confirmar la recrea con la hora del servidor"
```

---

### Task 6: Reglas: un admin retirado ya no edita ni borra sus comunes

**Ficheros:**
- Modificar: `firestore.rules` (`ownsOrCuratesRecipe`, `ownsOrCuratesRoutine` y sus comentarios).
- Modificar: `docs/RECIPES_ADMIN.md` («Añadir o rotar un admin» y «corregir o retirar»), `docs/DATA_MODEL.md` (recetas: «Curación y admins»; rutinas: «Edición» y «Borrado»).
- Test: `tests/firebase-emulators.test.mjs`.

**Interfaces:**
- Consume: nada de tareas anteriores.
- Produce: nada que usen otras tareas.

- [ ] **Step 1: Test.** En `tests/firebase-emulators.test.mjs`, detrás de `«Deshacer» de una común de un admin retirado: se recrea a nombre de quien deshace, no con el retirado`:

```js
test("un admin retirado ya no edita ni borra sus recetas y rutinas comunes; sí las personales y anonimizar su autor", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const firestore = context.firestore();
    const timestamp = Timestamp.now();
    const dates = { createdAt: timestamp, updatedAt: timestamp };
    await setDoc(doc(firestore, "recipes", "ex-admin-common"),
      { ...recipeData("ex-admin", { visibility: "GLOBAL" }), ...dates });
    await setDoc(doc(firestore, "recipes", "ex-admin-private"), { ...recipeData("ex-admin"), ...dates });
    await setDoc(doc(firestore, "routines", "ex-admin-common"),
      { ...routineData("ex-admin", { visibility: "GLOBAL" }), ...dates });
    await setDoc(doc(firestore, "routines", "ex-admin-private"), { ...routineData("ex-admin"), ...dates });
  });
  const exAdmin = verifiedFirestore("ex-admin");
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  const edit = { title: "Editada por el retirado", updatedBy: "ex-admin", updatedAt: serverTimestamp() };

  for (const collectionName of ["recipes", "routines"]) {
    const common = doc(exAdmin, collectionName, "ex-admin-common");
    const personal = doc(exAdmin, collectionName, "ex-admin-private");
    await assertFails(updateDoc(common, edit));
    await assertFails(writeBatch(exAdmin).delete(common).commit());
    await assertSucceeds(updateDoc(common, { createdByName: "Usuario eliminado" }));
    await assertSucceeds(updateDoc(personal, edit));
    await assertSucceeds(writeBatch(exAdmin).delete(personal).commit());
    await assertSucceeds(writeBatch(admin).delete(doc(admin, collectionName, "ex-admin-common")).commit());
  }
});
```

- [ ] **Step 2: Rojo.** EMU. Esperado: falla el test nuevo en el primer `assertFails` (hoy el dueño edita su común). Copiar el fallo.

- [ ] **Step 3: Código.**

  a) `firestore.rules`:

```
    // Una común (GLOBAL) solo la curan los admins actuales, aunque sea de otro o de un admin retirado; una personal, solo
    // su dueño. Anonimizar el autor al borrar la cuenta va aparte (validRecipeAnonymization).
    function ownsOrCuratesRecipe() {
      return verifiedUser()
        && ((resource.data.visibility == "GLOBAL" && isRecipeAdmin())
          || (resource.data.visibility != "GLOBAL" && resource.data.ownerUid == request.auth.uid));
    }
```

  y lo mismo en `ownsOrCuratesRoutine()`, con su comentario («Anonimizar… va aparte (`validRoutineAnonymization`)»).

  b) Si un test existente fija que un dueño **que no es admin** edita o borra una `GLOBAL` (receta o rutina), fija la conducta que este plan quita: pasa a `assertFails` y se dice en el informe. Los demás tests no se tocan.

  c) `docs/RECIPES_ADMIN.md`:
     - La línea «Si quitas a un admin, «Deshacer» de una común suya la recrea a nombre de quien deshace.» pasa a: «Si quitas a un admin, deja de poder editar y borrar sus comunes (recetas y rutinas) aunque siga siendo su dueño; solo puede anonimizar su autor al borrar su cuenta. «Deshacer» de una común suya la recrea a nombre de quien deshace.»
     - En «Para **corregir o retirar**…», el paréntesis pasa a: «(las reglas permiten a un admin editar/borrar cualquier `GLOBAL`, aunque la haya creado otra cuenta, y solo a un admin: su dueño, si ya no lo es, no puede)».

  d) `docs/DATA_MODEL.md`:
     - Recetas, al final de la viñeta «Curación y admins»: «Una `GLOBAL` solo la editan o borran los admins actuales; su dueño, si ya no es admin, no.»
     - Rutinas: «**Edición:** el dueño o el admin (en las `GLOBAL`).» pasa a «**Edición:** el dueño en las `PRIVATE`; en las `GLOBAL`, solo un admin actual (aunque el dueño sea otro o un admin retirado).». «**Borrado:** el dueño o el admin.» pasa a «**Borrado:** como la edición.».

- [ ] **Step 4: Verde.** EMU y después GRADLE (no cambia Kotlin; confirma que la rama sigue en verde).

- [ ] **Step 5: Commit.**

```bash
git add firestore.rules tests/firebase-emulators.test.mjs docs/RECIPES_ADMIN.md docs/DATA_MODEL.md
git commit -m "fix(reglas): un admin retirado ya no edita ni borra sus recetas y rutinas comunes"
```

---

## Ejecución (`delegating-plan-execution`, plan de menores)

1. Rama `agent/menores-compra-membresia-y-comunes`, desde `main` (2fdf5c7), con este plan como primer commit.
2. Las tareas 1 a 6, una tras otra, cada una con un `sonnet` que recibe su brief. En el `task-done`:
   - Tareas 1, 2, 3 y 5: JVM de las clases nombradas y GRADLE;
   - Tarea 4: JVM, GRADLE y EMU;
   - Tarea 6: EMU y GRADLE.
3. **Revisión final** con `opus`, más los arreglos (un `sonnet`, cada uno con su test RED→GREEN). **Re-revisión acotada** solo si un arreglo toca `firestore.rules` (permisos) o `migrations/`.
4. **Sin `simplify`** (plan de menores).
5. **`code-review`** en nivel `high`, más los arreglos (misma regla de re-revisión).
6. **Cierre:**
   - En el backlog se tachan B1, B2, B4, B5 y B6 de la sección «2026-10-13 — `agent/menores-compra-y-deshacer-comunes`», con la rama. B3 se reescribe: queda solo la limitación «vales» → «val», «trajes», «meses».
   - Se añaden los menores nuevos, entre ellos el de «Qué no entra»: otras pantallas muestran el texto crudo de la excepción.
   - El usuario decide qué hacer con la rama.

## Verificación de punta a punta (coordinador, al final)

- **Suites:** GRADLE, NODE y EMU en verde.
- **Preparación del emulador:** como en el plan anterior (`npm run emulators` con el `JAVA_HOME` de Android Studio, `npm run seed:emulators`, script del Admin SDK en el scratchpad que crea el usuario sembrado y lo liga al espacio, e instalar la app debug).
- **Recorrido**, con capturas en tema claro y oscuro de los pasos 2 y 3:
  1. **Unidades:** en la Compra, «Galletas» pendiente con «1/2 bolsas dulces»; añadir «galletas» con «1/2 bolsas dulces» y «Sumar cantidad» → «1 bolsa dulce».
  2. **Marca larga:**
     - en el editor de la Compra, un producto sin ingrediente reconocido y 61 caracteres en la marca → el campo en rojo y «Guardar» deshabilitado;
     - lo mismo con un ingrediente reconocido sin marcas registradas.
  3. **Membresía perdida:**
     - con la Compra abierta, borrar la membresía del usuario con el script del Admin SDK → la app vuelve a «Espacios», sin texto técnico, y la barra no se queda «sin conexión»;
     - devolver la membresía → el espacio reaparece en la lista; entrar → la Compra carga.
  4. **Lectura fallida de la Compra:** perder la membresía ya no deja ver este estado, así que no hay forma sencilla de provocarlo en el emulador. Se salta y se dice; lo cubre `ShoppingViewModelTest`.
  5. **«Deshacer» con la edición sin confirmar** (sesión del admin; necesita el admin sembrado y el catálogo de recetas importado al emulador, como en el plan anterior):
     - sin red (modo avión en el emulador), editar «Lasaña de carne», borrarla y pulsar «Deshacer»;
     - volver a tener red → la receta sigue, con la edición;
     - `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 npm run catalog:import-recipes -- --project demo-bobitos` (simulación) → «Lasaña de carne» sale como editada en la app (omitida), no actualizada.
     - Si el emulador no deja provocarlo, se salta y se dice.

## Despliegue (tras el merge, cada paso confirmado por el usuario)

Trampas conocidas: `--project dev`; `JAVA_HOME` de Android Studio; `beta:distribute` sube el APK viejo si falla el build.

1. **Reglas** (la Tarea 6 las cambia): `npx firebase deploy --only firestore:rules --project dev`.
2. **Beta 23** (`0.1.0-beta.23`, `VERSION_CODE=23`):
   - notas en `distribution/release-notes.txt` con los cambios visibles de este plan;
   - build release firmado;
   - `npm run beta:distribute`, después de comprobar que el build ha terminado bien.
