# Menores de la Compra y «Deshacer» de las comunes (beta 22) — Plan de implementación

> **Para agentes:** se ejecuta con `delegating-plan-execution`. Es un **plan de menores** (6 tareas): pasos 1, 2, 3, 5 y 6, **sin `simplify`**. Cada tarea la hace un subagente `sonnet`, en serie y en orden. La revisión final la hace `opus` (si falla, `fable`). **Solo la Tarea 6 toca `firestore.rules`** (permisos); ninguna toca `migrations/`. Los pasos usan casillas (`- [ ]`).

**Objetivo:** cerrar los 7 menores con efecto visible de la sección «2026-10-13 — `agent/menores-recetas-compra`» de `docs/superpowers/backlog.md` y la parte (a) de R4 de la sección `agent/catalogo-recetas` («solo con un segundo admin»). Los internos se quedan en el backlog.

**Arquitectura:**
- **Compra (Tareas 1 a 4):**
  - `ShoppingQuantity` cambia de número cada palabra de la unidad hasta la primera «de», saca bien el singular de «-es» y deja quietas las abreviaturas.
  - El aviso «Ya en la lista» solo une con un espacio duro el número y la palabra que le sigue.
  - «Sumar cantidad» rellena la tienda y la marca como pareja.
  - El editor dice que la lectura de la lista ha fallado y vuelve a escucharla.
  - La revisión de «Añadir a la compra» marca la marca libre demasiado larga en su fila y no deja confirmar.
- **Comidas (Tarea 5):** `markCooked` lee la Compra del servidor.
- **Recetario (Tarea 6):** «Deshacer» recrea una común tal como era (dueño, autor, fechas y `source`), con una regla nueva de creación solo para admins.

**Stack:** Kotlin + Jetpack Compose (Material 3) + Hilt + Firestore; reglas de Firestore con tests en el emulador (`@firebase/rules-unit-testing`, `node --test`).

**Spec:** `docs/superpowers/specs/2026-10-12-recetario-y-compra-design.md` (A3 y A4 para la Compra; C1 y C2 para el catálogo de recetas). Plan anterior: `docs/superpowers/plans/2026-10-12-menores-recetas-compra.md` (rulings 5, 7, 10 y 11, que este plan corrige). Backlog: `docs/superpowers/backlog.md`.

## Qué entra

| # | Menor (backlog) | Tarea |
|---|---|---|
| M1 | `singularOf` no es el inverso de `pluralOf`: solo toca la última palabra («1 cucharadas sopera») y «-es» tras l, r, n, d o j pierde solo la «s» («bole», «pane») | 1 |
| M2 | Abreviaturas en plural: «cles», «oces», «mgs», «ccs» | 1 |
| M3 | Aviso «Ya en la lista»: una cantidad larga se corta a mitad de palabra («… del supe / r») | 2 |
| M5 | «Sumar cantidad» junta una tienda y una marca que no van juntas (DIA sin marca + «Hacendado» de Mercadona → DIA + Hacendado) | 2 |
| M4 | Editor de la Compra con la lectura de la lista fallida: «Aún no se ha cargado la lista…» para siempre | 3 |
| M6 | Revisión: una marca libre demasiado larga hace fallar todo con «No se ha podido completar la operación.», sin decir qué fila | 4 |
| M7 | `markCooked` lee la Compra de la caché (`items().first()`) | 5 |
| R4a | Un segundo admin que deshace el borrado de una común se queda como dueño y autor, y el importador la ve como «ficha de usuario» | 6 |

Los menores **internos** de las dos secciones no entran.

## Rulings de planificación

1. **M1, número de las unidades de varias palabras.** Cambian de número las palabras de la unidad **hasta la primera «de» o «del»** («cucharadas soperas» ↔ «cucharada sopera»; «dientes de ajo» ↔ «diente de ajo»; «vasos de leche» ↔ «vaso de leche»). Una grafía está en plural si su **primera** palabra acaba en «s». Cada palabra conserva sus mayúsculas.
2. **M1, singular de una palabra en plural**, en este orden:
   - «-ces» → «-z»;
   - «-ones» → «-ón»;
   - «-ines» → «-ín» («botellines» → «botellín»);
   - vocal + l, r, n, d o j + «-es» → sin «-es» («panes» → «pan», «boles» → «bol», «yogures» → «yogur»);
   - si no, sin la «-s» final («sobres» → «sobre», «baldes» → «balde», «filetes» → «filete»).
   - **Limitación aceptada:** palabras raras como unidad, como «trajes» o «meses», no salen bien.
3. **M2, abreviaturas.** Una palabra de la unidad **sin vocales o de dos letras o menos** es una abreviatura y no cambia de número («cl», «dl», «mg», «cc», «oz», «lb»). `BASIC_UNITS` no cambia. «cda» sigue pasando a «cdas».
4. **M3.** En el aviso, el espacio duro (U+00A0) va **solo entre el número inicial y la palabra que le sigue** («5 ud», «2 paquetes…»). Los demás espacios de la cantidad son normales: una cantidad larga se parte entre palabras. El espacio duro tras «cantidad:» de `strings.xml` se queda. Una cantidad sin número delante va tal cual.
5. **M5, tienda y marca como pareja** (corrige la decisión D2 del 2026-10-12 solo para tienda y marca; las notas siguen rellenándose solas):
   - artículo sin tienda ni marca → la tienda y la marca tecleadas;
   - artículo con tienda y sin marca → conserva su tienda; la marca tecleada solo entra si se tecleó **esa misma tienda**;
   - artículo con marca y sin tienda → conserva su marca; la tienda tecleada solo entra si se tecleó **esa misma marca** (sin distinguir mayúsculas ni espacios en los extremos);
   - artículo con las dos → las conserva.
   - Una marca solo con espacios cuenta como vacía. «Indiferente» cuenta como tienda elegida.
6. **M4.** Si la lectura de la lista ha fallado, «Guardar» no escribe y dice «No se ha podido leer la lista de la compra…» en el formulario. Además **vuelve a escuchar la lista** (`observe`). Si la lectura se recupera, el siguiente «Guardar» funciona sin reabrir la pantalla; si vuelve a fallar, se repite lo mismo. Mientras la lista carga sigue «Aún no se ha cargado la lista…».
7. **M6.**
   - El límite de la marca es **60** caracteres (reglas y `FirestoreShoppingRepository.MAX_BRAND_LENGTH`). El «50» del backlog es un desliz.
   - El campo de marca libre de `BrandStorePicker` se marca en rojo con «La marca no puede superar 60 caracteres.» si el texto recortado pasa de 60. El picker también sale en el editor de la Compra, que gana el mismo error en el campo.
   - En la revisión, «Confirmar» se deshabilita mientras una fila tenga la marca libre demasiado larga, como ya pasa con la cantidad.
8. **M7.** `markCooked` lee la lista del servidor (`currentItems`). Si no puede, no ofrece tachar nada y la comida queda cocinada, como hasta ahora.
9. **R4a, «Deshacer» de una común: vuelve tal como era.**
   - Una receta **GLOBAL** se recrea con su `ownerUid`, `createdBy`, `createdByName`, `createdAt`, `updatedBy` y `updatedAt` originales, y con su `source` (`provider: "bobitos"`, `importedAt`) si venía del importador.
   - Así el importador la sigue tratando como antes del borrado. Esto arregla también un fallo que hoy pasa **con un solo admin**: la común deshecha perdía `source` y el importador la omitía para siempre como «ficha manual del admin» (`docs/RECIPE_CATALOG.md`, aviso de «Deshacer»).
   - Una receta **PRIVATE** se recrea como ahora (de quien deshace, con fechas del servidor).
   - **Reglas:** nueva `validRecipeRestore()` en `allow create` de `recipes`. Solo la cumple un admin que crea una GLOBAL con:
     - dueño en `recipeAdmins()`;
     - `createdBy == ownerUid`;
     - `updatedBy` en `recipeAdmins()`;
     - `createdAt <= updatedAt <= request.time`.
   - Con un solo admin en `recipeAdmins()`, los tests de reglas prueban la regla con el admin del catálogo como dueño y quien deshace a la vez; la condición es la misma con dos admins.
   - Las comunes que ya se deshicieron antes de la beta 22 siguen sin `source`. No se migran: el aviso de `docs/RECIPE_CATALOG.md` explica cómo devolverlas al catálogo.
   - Toca **permisos**: un arreglo posterior que toque esta regla lleva re-revisión acotada.

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
- **Tests Kotlin:** JVM puros (JUnit 4 + `kotlinx-coroutines-test`), dobles a mano; sin mockk ni Robolectric. **Orden TDD obligatorio:** test primero, verlo en rojo (un fallo de compilación por lo que aún no existe también vale; copiar el fallo en el informe), después el código.
- **detekt:**
  - Límites: `ReturnCount` máx. 4, `LongMethod` máx. 80 líneas, `TooManyFunctions` 25 (por clase y por fichero).
  - Extraer código antes que tocar `config/detekt/baseline.xml`.
  - **No cambies estas firmas, que están en el baseline:** `ShoppingScreen(...)`, `FirestoreShoppingRepository.validateItem(...)`, `runShoppingOperation(...)`, `DocumentSnapshot.toShoppingItem()`, `DocumentSnapshot.toRecipe()`, `FirestoreRecipeRepository.validate(title, description, category)` y `runRecipeOperation(...)`.
  - No añadas `catch (Throwable)` nuevos.
- **ktlint:** sin imports sin usar; línea ≤120.
- **Tests existentes:** solo se cambian los que fijan el comportamiento que este plan sustituye. Cada tarea dice cuáles y cómo.

## Review Focus

1. **Unidades con mayúsculas o punto final** («1/2 Cucharadas Soperas.» + «1/2 cucharadas soperas»): cada palabra cambia de número, conserva sus mayúsculas y pierde el punto → «1 Cucharada Sopera». → `ShoppingQuantityTest` (Tarea 1).
2. **Cantidad del artículo sin número delante o de una sola palabra larga** en el aviso «Ya en la lista»: va tal cual, sin espacios duros añadidos. → `ShoppingDuplicateTest` (Tarea 2).
3. **Marca tecleada que solo difiere en mayúsculas o espacios** de la del artículo sin tienda: cuenta como la misma, y la tienda tecleada rellena la vacía. → `ShoppingDuplicateTest` (Tarea 2).
4. **La lectura de la lista vuelve a fallar al reintentarla** (causa permanente, como un permiso): cada «Guardar» repite el aviso, vuelve a escuchar y no escribe nada. → `ShoppingViewModelTest` (Tarea 3).
5. **Deshacer una común creada a mano en la app** (sin `source`): vuelve con su dueño y sus fechas y sin `source`; una privada vuelve como antes. → `RecipeRestoreTest` (Tarea 6).

---

### Task 1: Compra: número de las unidades de varias palabras, singular de «-es» y abreviaturas

**Ficheros:**
- Modificar: `APP/core/model/ShoppingQuantity.kt` (`unitFor` y `singularOf`; `isPlural`, `inflect`, `isAbbreviation` y `dropsEs` nuevas).
- Test: `UT/core/model/ShoppingQuantityTest.kt`.

**Interfaces:**
- Consume: nada nuevo.
- Produce: `ShoppingQuantity.sum` con la concordancia nueva; la firma no cambia.

- [ ] **Step 1: Test.** Añadir al final de `ShoppingQuantityTest`:

```kotlin
    @Test
    fun `the singular of a plural in -es after a vowel and l, r, n, d or j drops the whole -es`() {
        assertEquals("1 bol", ShoppingQuantity.sum("1/2 boles", "1/2 boles"))
        assertEquals("1 pan", ShoppingQuantity.sum("1/2 panes", "1/2 panes"))
        assertEquals("1 yogur", ShoppingQuantity.sum("1/2 yogures", "1/2 yogures"))
        assertEquals("1 botellín", ShoppingQuantity.sum("1/2 botellines", "1/2 botellines"))
        assertEquals("1 balde", ShoppingQuantity.sum("1/2 baldes", "1/2 baldes"))
        assertEquals("1 filete", ShoppingQuantity.sum("1/2 filetes", "1/2 filetes"))
    }

    @Test
    fun `every word of the unit agrees in number up to the first de`() {
        assertEquals("1 cucharada sopera", ShoppingQuantity.sum("1/2 cucharadas soperas", "1/2 cucharadas soperas"))
        assertEquals("2 cucharadas soperas", ShoppingQuantity.sum("1 cucharada sopera", "1 cucharada sopera"))
        assertEquals("2 dientes de ajo", ShoppingQuantity.sum("1 diente de ajo", "1 diente de ajo"))
        assertEquals("3 dientes de ajo", ShoppingQuantity.sum("1 diente de ajo", "2 dientes de ajo"))
        assertEquals("1 vaso de leche", ShoppingQuantity.sum("1/2 vasos de leche", "1/2 vasos de leche"))
        assertEquals("1 Cucharada Sopera", ShoppingQuantity.sum("1/2 Cucharadas Soperas.", "1/2 cucharadas soperas"))
    }

    @Test
    fun `abbreviations do not change in number`() {
        assertEquals("3 cl", ShoppingQuantity.sum("1 cl", "2 cl"))
        assertEquals("2 oz", ShoppingQuantity.sum("1 oz", "1 oz"))
        assertEquals("3 mg", ShoppingQuantity.sum("1 mg", "2 mg"))
        assertEquals("2 cc", ShoppingQuantity.sum("1 cc", "1 cc"))
        assertEquals("1 dl", ShoppingQuantity.sum("1/2 dl", "1/2 dl"))
        assertEquals("2 cdas", ShoppingQuantity.sum("1 cda", "1 cda"))
    }
```

- [ ] **Step 2: Rojo.** JVM de `com.dlunaunizar.bobitos.core.model.ShoppingQuantityTest`. Lo esperado: «1 bole», «1 pane», «1 yogure», «1 botelline», «1 cucharadas sopera», «2 cucharada soperas», «2 diente de ajos», «3 cl» → «3 cles», «oces», «mgs», «ccs». Copiar el fallo.

- [ ] **Step 3: Implementación** (`ShoppingQuantity.kt`):
  - **Constantes**, junto a `PLAIN_VOWELS`:

```kotlin
    private val WORD_SPACE = Regex("""\s+""")

    // Palabras que cierran la parte de la unidad que cambia de número («dientes de ajo»).
    private val UNIT_LINKS = setOf("de", "del")
```

  - **`unitFor`**, que sustituye a la actual:

```kotlin
    // Básicas en forma corta; las demás, en singular si el total es 1 o menos y en plural si pasa de 1. Sin grafía en
    // ese número, se saca de la otra con [inflect].
    private fun unitFor(total: BigDecimal, terms: List<Parsed>): String {
        val head = terms.first().unit
        if (head.isEmpty()) return ""
        BASIC_UNITS[head.lowercase()]?.let { return it }
        val spellings = terms.map { it.unit }
        if (total <= BigDecimal.ONE) return spellings.firstOrNull { !isPlural(it) } ?: inflect(head, ::singularOf)
        return spellings.firstOrNull(::isPlural) ?: inflect(head, ::pluralOf)
    }

    // Una grafía está en plural si su primera palabra acaba en «s» («dientes de ajo», «cucharadas soperas»).
    private fun isPlural(unit: String): Boolean = unit.split(WORD_SPACE).first().lowercase().endsWith("s")

    // Cambia de número las palabras de la unidad hasta la primera «de» o «del» («cucharadas soperas» → «cucharada
    // sopera», «dientes de ajo» → «diente de ajo»). Las abreviaturas no cambian.
    private fun inflect(unit: String, change: (String) -> String): String {
        val words = unit.split(WORD_SPACE)
        val link = words.indexOfFirst { it.lowercase() in UNIT_LINKS }
        val end = if (link < 0) words.size else link
        return words.mapIndexed { index, word ->
            if (index < end && !isAbbreviation(word)) change(word) else word
        }.joinToString(" ")
    }

    // Abreviatura: sin vocales o de dos letras o menos («cl», «mg», «cc», «oz»).
    private fun isAbbreviation(word: String): Boolean = word.length <= 2 || word.none { it.lowercaseChar() in VOWELS }
```

  - **`pluralOf`** no cambia (ahora recibe una palabra; el comentario sigue valiendo).
  - **`singularOf`**, que sustituye a la actual:

```kotlin
    // Singular aproximado de una palabra en plural: «-ces» → «-z»; «-ones» → «-ón»; «-ines» → «-ín»; vocal + l, r, n,
    // d o j + «-es» → sin «-es» («panes» → «pan», «boles» → «bol»); si no, sin la «-s» final («sobres» → «sobre»).
    // Falla en palabras raras como unidad («trajes», «meses»).
    private fun singularOf(word: String): String {
        val lower = word.lowercase()
        return when {
            lower.endsWith("ces") -> "${word.dropLast(3)}z"
            lower.endsWith("ones") -> "${word.dropLast(4)}ón"
            lower.endsWith("ines") -> "${word.dropLast(4)}ín"
            lower.dropsEs() -> word.dropLast(2)
            lower.endsWith("s") -> word.dropLast(1)
            else -> word
        }
    }

    // «-es» tras l, r, n, d o j que siguen a una vocal: el singular acaba en esa consonante («flores» → «flor»).
    private fun String.dropsEs(): Boolean =
        length >= 4 && endsWith("es") && this[length - 3] in ES_PLURAL_CONSONANTS && this[length - 4] in VOWELS
```

- [ ] **Step 4: Verde.** JVM de `ShoppingQuantityTest`, `RecipeUnitsFixtureTest` e `IngredientReviewTest`, y GRADLE completo.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/core/model/ShoppingQuantity.kt app/src/test/java/com/dlunaunizar/bobitos/core/model/ShoppingQuantityTest.kt
git commit -m "fix(compra): al sumar, cada palabra de la unidad concuerda en número y las abreviaturas no cambian"
```

---

### Task 2: Aviso «Ya en la lista»: la cantidad se parte entre palabras y «Sumar cantidad» rellena tienda y marca como pareja

**Ficheros:**
- Modificar:
  - `APP/feature/shopping/ShoppingUiState.kt` (`ShoppingDuplicate.existingQuantityLabel` y `storeAndBrandAfterSum()` nueva);
  - `APP/feature/shopping/ShoppingViewModel.kt` (`sumDuplicate` y su KDoc).
- Tests: `UT/feature/shopping/ShoppingDuplicateTest.kt` y `UT/feature/shopping/ShoppingViewModelTest.kt`.

**Interfaces:**
- Consume: `ShoppingItem.supermarket`, `ShoppingItem.brand`.
- Produce: `fun ShoppingDuplicate.storeAndBrandAfterSum(): Pair<Supermarket?, String?>` (miembro de `ShoppingDuplicate`).

- [ ] **Step 1: Tests.**
  - **`ShoppingDuplicateTest`:**
    - **sustituir** el test `the existing quantity does not break across lines and is absent when there is none`, que fija el comportamiento antiguo (todos los espacios duros), por el primero de abajo;
    - añadir el segundo;
    - añadir `import com.dlunaunizar.bobitos.core.model.Supermarket`.

```kotlin
    @Test
    fun `only the space after the leading number does not break, so a long quantity breaks between words`() {
        assertEquals("5 ud", ShoppingDuplicate(item(" 5 ud "), "2").existingQuantityLabel)
        assertEquals(
            "2 paquetes grandes del supermercado",
            ShoppingDuplicate(item("2 paquetes grandes del supermercado"), "1").existingQuantityLabel,
        )
        assertEquals(
            "1/2 cucharadas soperas",
            ShoppingDuplicate(item("1/2 cucharadas soperas"), "1").existingQuantityLabel,
        )
        assertEquals("un poco", ShoppingDuplicate(item("un poco"), "1").existingQuantityLabel)
        assertEquals("x".repeat(30), ShoppingDuplicate(item("x".repeat(30)), "1").existingQuantityLabel)
        assertNull(ShoppingDuplicate(item(null), "2").existingQuantityLabel)
        assertNull(ShoppingDuplicate(item("  "), "2").existingQuantityLabel)
    }

    @Test
    fun `summing fills the store and the brand only as a pair that goes together`() {
        fun after(store: Supermarket?, brand: String?, typedStore: Supermarket?, typedBrand: String?) =
            ShoppingDuplicate(item("1 l").copy(supermarket = store, brand = brand), "1 l", null, typedStore, typedBrand)
                .storeAndBrandAfterSum()

        assertEquals(Supermarket.MERCADONA to "Hacendado", after(null, null, Supermarket.MERCADONA, "Hacendado"))
        assertEquals(Supermarket.DIA to null, after(Supermarket.DIA, null, Supermarket.MERCADONA, "Hacendado"))
        assertEquals(Supermarket.DIA to "Dia", after(Supermarket.DIA, "  ", Supermarket.DIA, "Dia"))
        assertEquals(
            Supermarket.MERCADONA to "Hacendado",
            after(null, "Hacendado", Supermarket.MERCADONA, " hacendado "),
        )
        assertEquals(null to "Pascual", after(null, "Pascual", Supermarket.DIA, "Puleva"))
        assertEquals(Supermarket.DIA to "Pascual", after(Supermarket.DIA, "Pascual", Supermarket.MERCADONA, "Hacendado"))
        assertEquals(Supermarket.INDIFERENTE to null, after(Supermarket.INDIFERENTE, null, Supermarket.DIA, "Puleva"))
    }
```

  - **`ShoppingViewModelTest`**, añadir:

```kotlin
    @Test
    fun `summing a duplicate does not join a store and a brand that do not go together`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val pending = shoppingItem().copy(quantity = "1 l", supermarket = Supermarket.DIA)
            repository.itemState.value = listOf(pending)
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.addItem("home", "Leche", "2 l", null, Supermarket.MERCADONA, "Hacendado")
            advanceUntilIdle()

            viewModel.sumDuplicate("home")
            advanceUntilIdle()

            assertEquals(
                listOf(ShoppingWrite.Change(pending, "3 l", null, Supermarket.DIA, null, relist = false)),
                repository.applied,
            )
        }
```

- [ ] **Step 2: Rojo.** JVM de `com.dlunaunizar.bobitos.feature.shopping.ShoppingDuplicateTest` (no compila: falta `storeAndBrandAfterSum`) y de `com.dlunaunizar.bobitos.feature.shopping.ShoppingViewModelTest` (escribe `DIA` + «Hacendado»). Copiar los fallos.

- [ ] **Step 3: Implementación.**
  - **`ShoppingUiState.kt`**, en `ShoppingDuplicate`:
    - el KDoc de la clase cambia «rellena las que el artículo tenga vacías» por «rellena las notas vacías y, como pareja, la tienda y la marca (ver [storeAndBrandAfterSum])»;
    - `existingQuantityLabel` pasa a ser:

```kotlin
    /**
     * La cantidad del artículo para el aviso; null si no tiene. Solo el espacio entre el número inicial y la palabra que
     * le sigue no parte línea («5 ud»): una cantidad larga se parte por sus otros espacios, nunca a mitad de palabra.
     */
    val existingQuantityLabel: String?
        get() = existing.quantity?.trim()?.takeIf(String::isNotEmpty)?.replaceFirst(NUMBER_THEN_SPACE, "\$1 ")

    /**
     * Tienda y marca tras «Sumar cantidad»: lo tecleado rellena las del artículo como pareja, para no juntar una tienda
     * y una marca que no van juntas. Sin tienda ni marca, entran las tecleadas; con solo la tienda, la marca tecleada
     * entra si se tecleó esa misma tienda; con solo la marca, la tienda tecleada entra si se tecleó esa misma marca (sin
     * distinguir mayúsculas); con las dos, se conservan. Una marca solo con espacios cuenta como vacía.
     */
    fun storeAndBrandAfterSum(): Pair<Supermarket?, String?> {
        val store = existing.supermarket
        val listedBrand = existing.brand?.trim()?.takeIf(String::isNotEmpty)
        val typedBrand = brand?.trim()?.takeIf(String::isNotEmpty)
        return when {
            store == null && listedBrand == null -> supermarket to typedBrand
            listedBrand == null -> store to typedBrand.takeIf { supermarket == store }
            store == null -> supermarket.takeIf { listedBrand.equals(typedBrand, ignoreCase = true) } to listedBrand
            else -> store to listedBrand
        }
    }
```

  - y, al final del fichero (fuera de la clase):

```kotlin
// Número inicial («5», «1/2», «1,5») y el espacio que lo separa de la palabra siguiente.
private val NUMBER_THEN_SPACE = Regex("""^(\S*\d\S*)\s+""")
```

  - **`ShoppingViewModel.sumDuplicate`:**
    - antes de `runAction`, `val (store, brand) = duplicate.storeAndBrandAfterSum()`;
    - en el `ShoppingWrite.Change`, `supermarket = store` y `brand = brand`;
    - las notas no cambian: `existing.notes.normalized() ?: duplicate.notes`;
    - el KDoc pasa a ser: «"Sumar cantidad" en el aviso de repetido: suma la cantidad, rellena con las tecleadas las notas que el artículo tenga vacías y rellena la tienda y la marca como pareja ([ShoppingDuplicate.storeAndBrandAfterSum]). Si la suma no cabe, no hace nada (el aviso lo explica y deshabilita el botón).»

- [ ] **Step 4: Verde.** JVM de `ShoppingDuplicateTest` y `ShoppingViewModelTest` (también siguen en verde `summing a duplicate fills the empty notes, store and brand with the typed ones`, `summing a duplicate keeps the notes, store and brand the item already has` y `summing treats an indifferent store as chosen and blank notes as empty`), `ShoppingStringsTest`, y GRADLE completo.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingUiState.kt app/src/main/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingViewModel.kt app/src/test/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingDuplicateTest.kt app/src/test/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingViewModelTest.kt
git commit -m "fix(compra): el aviso de repetido parte la cantidad entre palabras y «Sumar» rellena tienda y marca como pareja"
```

---

### Task 3: Editor de la Compra: si la lectura de la lista ha fallado, lo dice y vuelve a escucharla

**Ficheros:**
- Modificar:
  - `APP/feature/shopping/ShoppingViewModel.kt` (`addItem`; `itemsForAdd` nueva sustituye a la propiedad `loadedItems`);
  - `APP/feature/shopping/ShoppingUiState.kt` (`ShoppingUiMessage.ListUnavailable`);
  - `APP/feature/shopping/ShoppingScreen.kt` (`stringResourceId`);
  - `RES` (`shopping_error_list_unavailable`).
- Test: `UT/feature/shopping/ShoppingViewModelTest.kt` (y su `FakeShoppingRepository`).

**Interfaces:**
- Consume: `ShoppingViewModel.observe(spaceId)`, `UiState`.
- Produce: `ShoppingUiMessage.ListUnavailable`.

- [ ] **Step 1: Tests.**
  - **Doble:** en `FakeShoppingRepository`, añadir `var itemsFailures = 0` y `var itemsCalls = 0`. `items` pasa a ser:

```kotlin
    // Las primeras `itemsFailures` escuchas fallan con un permiso denegado (como un listener que se cierra).
    override fun items(spaceId: String): Flow<List<ShoppingItem>> {
        observedSpaceId = spaceId
        itemsCalls++
        if (itemsFailures > 0) {
            itemsFailures--
            return flow { throw ShoppingRepositoryException(ShoppingFailure.PermissionDenied) }
        }
        return itemState
    }
```

  - **Tests nuevos:**

```kotlin
    @Test
    fun `adding after the list failed to load says so, listens again and works once it loads`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.itemsFailures = 1
            viewModel.observe("home")
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.items is UiState.Error)

            viewModel.addItem("home", "Pan", "1", null, null, null)
            advanceUntilIdle()

            assertNull(repository.addedName)
            assertEquals(ShoppingUiMessage.ListUnavailable, viewModel.uiState.value.error)
            assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.editorSave)
            assertEquals(2, repository.itemsCalls)
            assertTrue(viewModel.uiState.value.items is UiState.Content)

            viewModel.addItem("home", "Pan", "1", null, null, null)
            advanceUntilIdle()

            assertEquals("Pan", repository.addedName)
        }

    @Test
    fun `adding while the list keeps failing to load never writes and listens again each time`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.itemsFailures = 3
            viewModel.observe("home")
            advanceUntilIdle()

            viewModel.addItem("home", "Pan", "1", null, null, null)
            advanceUntilIdle()
            viewModel.addItem("home", "Pan", "1", null, null, null)
            advanceUntilIdle()

            assertNull(repository.addedName)
            assertNull(repository.applied)
            assertEquals(ShoppingUiMessage.ListUnavailable, viewModel.uiState.value.error)
            assertEquals(3, repository.itemsCalls)
            assertTrue(viewModel.uiState.value.items is UiState.Error)
        }
```

- [ ] **Step 2: Rojo.** JVM de `com.dlunaunizar.bobitos.feature.shopping.ShoppingViewModelTest` (no compila: falta `ListUnavailable`). Copiar el fallo.

- [ ] **Step 3: Implementación.**
  - **`ShoppingUiMessage`:** `ListUnavailable` justo después de `ListNotLoaded`.
  - **`RES`**, junto a `shopping_error_list_not_loaded`:

```xml
    <string name="shopping_error_list_unavailable">No se ha podido leer la lista de la compra. Se está volviendo a leer: guarda de nuevo en un momento.</string>
```

  - **`ShoppingScreen.kt`**, en `stringResourceId`: `ShoppingUiMessage.ListUnavailable -> R.string.shopping_error_list_unavailable`.
  - **`ShoppingViewModel`:**
    - quitar la propiedad `loadedItems` (y su comentario);
    - en `addItem`, el bloque `val items = loadedItems ?: run { … }` pasa a ser `val items = itemsForAdd(spaceId) ?: return`;
    - la última frase de su KDoc pasa a ser: «Con la lista aún sin cargar, o con su lectura fallida, no escribe nada: sin ella no se pueden buscar repetidos (ver [itemsForAdd]).»;
    - añadir, después de `dismissDuplicate`:

```kotlin
    // La lista ya leída. Si aún carga, o si su lectura ha fallado (y entonces se vuelve a escuchar), lo dice en el
    // editor y devuelve null: sin ella no se pueden buscar repetidos.
    private fun itemsForAdd(spaceId: String): List<ShoppingItem>? = when (val items = mutableUiState.value.items) {
        is UiState.Content -> items.value
        UiState.Loading -> null.also { showError(ShoppingUiMessage.ListNotLoaded, editor = true) }
        is UiState.Error -> null.also {
            showError(ShoppingUiMessage.ListUnavailable, editor = true)
            observe(spaceId)
        }
    }
```

- [ ] **Step 4: Verde.** JVM de `ShoppingViewModelTest` (también `adding before the list is loaded asks to wait and writes nothing`), y GRADLE completo.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingViewModel.kt app/src/main/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingUiState.kt app/src/main/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingScreen.kt app/src/main/res/values/strings.xml app/src/test/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingViewModelTest.kt
git commit -m "fix(compra): con la lectura de la lista fallida, el editor lo dice y vuelve a escucharla"
```

---

### Task 4: Revisión de «Añadir a la compra»: la marca libre demasiado larga se marca en su fila y no deja confirmar

**Ficheros:**
- Modificar:
  - `APP/feature/shopping/ShoppingValidation.kt` (`MAX_BRAND_LENGTH`);
  - `APP/feature/common/BrandStoreSelection.kt` (`freeBrandTooLong` nueva);
  - `APP/feature/common/BrandStorePicker.kt` (error en el campo de marca libre);
  - `APP/feature/common/IngredientReview.kt` (`brandTooLong` y `canConfirmReview` nuevas; «Confirmar» de `IngredientReviewDialog`).
- Test: `UT/feature/common/IngredientReviewTest.kt`.

**Interfaces:**
- Consume: `IngredientShoppingContext.brands`, `ReviewRowState.freeBrand`, `ReviewRowState.quantityTooLong()`.
- Produce:
  - `ShoppingValidation.MAX_BRAND_LENGTH = 60`;
  - `internal fun freeBrandTooLong(text: String): Boolean` (`BrandStoreSelection.kt`);
  - `internal fun ReviewRowState.brandTooLong(row: IngredientReviewRow): Boolean` y `internal fun canConfirmReview(rows: List<IngredientReviewRow>, states: List<ReviewRowState>): Boolean` (`IngredientReview.kt`).

- [ ] **Step 1: Test.** En `IngredientReviewTest` (usa `arroz` y `data` de la clase):

```kotlin
    @Test
    fun `a free brand longer than the limit marks its row and blocks confirming`() {
        val free = IngredientReviewRow(
            name = "Arroz",
            recipeQuantity = "300 g",
            existing = null,
            context = IngredientShoppingContext(arroz, emptyList(), emptyList()),
        )
        val long = free.initialState().withFreeBrand("x".repeat(61))
        val fits = free.initialState().withFreeBrand("  " + "x".repeat(60) + "  ")

        assertTrue(long.brandTooLong(free))
        assertFalse(fits.brandTooLong(free))
        assertFalse(canConfirmReview(listOf(free), listOf(long)))
        assertTrue(canConfirmReview(listOf(free), listOf(fits)))

        // Con marcas registradas no hay campo libre: su texto no cuenta.
        val withBrands = free.copy(context = IngredientShoppingContext(arroz, data.brands, emptyList()))
        assertFalse(withBrands.initialState().withFreeBrand("x".repeat(61)).brandTooLong(withBrands))
        assertTrue(freeBrandTooLong("x".repeat(61)))
        assertFalse(freeBrandTooLong(" " + "x".repeat(60) + " "))
    }

    @Test
    fun `a quantity that does not fit still blocks confirming`() {
        val row = IngredientReviewRow("Sal", "1 pizca", existing = null)
        val long = row.initialState().copy(quantity = "x".repeat(41))

        assertFalse(canConfirmReview(listOf(row), listOf(long)))
        assertTrue(canConfirmReview(listOf(row), listOf(row.initialState())))
    }
```

- [ ] **Step 2: Rojo.** JVM de `com.dlunaunizar.bobitos.feature.common.IngredientReviewTest` (no compila: faltan `brandTooLong`, `canConfirmReview` y `freeBrandTooLong`). Copiar el fallo.

- [ ] **Step 3: Implementación.**
  - **`ShoppingValidation`:** `const val MAX_BRAND_LENGTH = 60` tras `MAX_NOTES_LENGTH`, con el comentario «// El mismo límite que las reglas y FirestoreShoppingRepository.».
  - **`BrandStoreSelection.kt`**, al final:

```kotlin
/** El texto libre de la marca no cabe: el campo lo marca y la revisión no deja confirmar. */
internal fun freeBrandTooLong(text: String): Boolean = text.trim().length > ShoppingValidation.MAX_BRAND_LENGTH
```

  (import `com.dlunaunizar.bobitos.feature.shopping.ShoppingValidation`).
  - **`BrandStorePicker`**, en el `OutlinedTextField` de la marca libre: antes, `val tooLong = freeBrandTooLong(freeBrand)`. Al campo:

```kotlin
                    isError = tooLong,
                    supportingText = if (tooLong) {
                        { Text(stringResource(R.string.shopping_error_brand_too_long)) }
                    } else {
                        null
                    },
```

  - **`IngredientReview.kt`**, tras `quantityTooLong`:

```kotlin
/** La marca libre de una fila nueva supera el máximo: error en el campo y Confirmar deshabilitado. */
internal fun ReviewRowState.brandTooLong(row: IngredientReviewRow): Boolean =
    row.context?.brands?.isEmpty() == true && freeBrandTooLong(freeBrand)

/** Confirmar se puede pulsar si ninguna fila tiene la cantidad o la marca libre demasiado larga. */
internal fun canConfirmReview(rows: List<IngredientReviewRow>, states: List<ReviewRowState>): Boolean =
    rows.indices.none { index -> states[index].quantityTooLong() || states[index].brandTooLong(rows[index]) }
```

  - En `IngredientReviewDialog`, el `enabled` de «Confirmar» pasa a ser `canConfirmReview(rows, states)`.

- [ ] **Step 4: Verde.** JVM de `IngredientReviewTest` y `BrandStoreSelectionTest`, y GRADLE completo.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingValidation.kt app/src/main/java/com/dlunaunizar/bobitos/feature/common/BrandStoreSelection.kt app/src/main/java/com/dlunaunizar/bobitos/feature/common/BrandStorePicker.kt app/src/main/java/com/dlunaunizar/bobitos/feature/common/IngredientReview.kt app/src/test/java/com/dlunaunizar/bobitos/feature/common/IngredientReviewTest.kt
git commit -m "fix(compra): una marca libre de más de 60 caracteres se marca en su fila y no deja confirmar la revisión"
```

---

### Task 5: Comidas: al marcar cocinada, la lista de la compra se lee del servidor

**Ficheros:**
- Modificar: `APP/feature/meals/MealsViewModel.kt` (`markCooked`).
- Test: `UT/feature/meals/MealsViewModelTest.kt`.

**Interfaces:**
- Consume: `ShoppingRepository.currentItems(spaceId)`; en el doble `RecordingShoppingRepository`, `serverItems` (la lista del servidor, distinta de la caché `itemsState`) e `itemsFailure`.
- Produce: nada nuevo.

- [ ] **Step 1: Tests.** En `MealsViewModelTest` (imports de `ShoppingRepositoryException` y `ShoppingFailure` si faltan):

```kotlin
    @Test
    fun `marking cooked reads the list from the server, not from a stale cached snapshot`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value =
                listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"), Ingredient("Tomate", "2", "ud"))))
            shoppingRepository.itemsState.value = listOf(shoppingItem("s1", "Arroz"))
            shoppingRepository.serverItems =
                listOf(shoppingItem("s1", "Arroz").copy(purchased = true), shoppingItem("s2", "Tomate"))
            viewModel.observe("home")
            advanceUntilIdle()

            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"))
            advanceUntilIdle()

            assertEquals(listOf("Tomate"), viewModel.uiState.value.cookedCrossOff?.map(ShoppingItem::name))
        }

    @Test
    fun `marking cooked when the list cannot be read still marks the meal and offers nothing`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
            shoppingRepository.itemsFailure = ShoppingRepositoryException(ShoppingFailure.Network)
            viewModel.observe("home")
            advanceUntilIdle()

            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"))
            advanceUntilIdle()

            assertEquals(true, mealRepository.cookedValue)
            assertEquals(MealUiMessage.MealCooked, viewModel.uiState.value.notice)
            assertEquals(null, viewModel.uiState.value.cookedCrossOff)
            assertEquals(null, viewModel.uiState.value.error)
        }
```

- [ ] **Step 2: Rojo.** JVM de `com.dlunaunizar.bobitos.feature.meals.MealsViewModelTest`. Lo esperado: el primero ofrece «Arroz» (la caché). El segundo ya pasa hoy y queda de guarda. Copiar el fallo.

- [ ] **Step 3: Implementación** (`markCooked`). La línea `val current = runCatching { shoppingRepository.items(spaceId).first() }.getOrDefault(emptyList())` pasa a ser:

```kotlin
                    // La lista del servidor, no la caché; si no se puede leer, no se ofrece tachar nada.
                    val current = runCatching { shoppingRepository.currentItems(spaceId) }
                        .onFailure { if (it is CancellationException) throw it }
                        .getOrDefault(emptyList())
```

  Si el import de `kotlinx.coroutines.flow.first` queda sin uso, quitarlo.

- [ ] **Step 4: Verde.** JVM de `MealsViewModelTest`, y GRADLE completo.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealsViewModel.kt app/src/test/java/com/dlunaunizar/bobitos/feature/meals/MealsViewModelTest.kt
git commit -m "fix(comidas): al marcar una comida cocinada, la lista de la compra se lee del servidor"
```

---

### Task 6: Recetario: «Deshacer» recrea una común tal como era (reglas: `validRecipeRestore`)

**Ficheros:**
- Crear:
  - `APP/data/repository/RecipeRestore.kt`;
  - `UT/data/repository/RecipeRestoreTest.kt`.
- Modificar:
  - `APP/core/model/Recipe.kt` (`importedAt`);
  - `APP/data/repository/FirestoreRecipeRepository.kt` (`restoreRecipe`, `restoredFields` nueva, constantes de `source`; `toRecipe` lee `importedAt`);
  - `firestore.rules` (`validRecipeRestore` y `allow create` de `recipes`);
  - `tests/firebase-emulators.test.mjs`;
  - `docs/RECIPE_CATALOG.md` y `docs/RECIPES_ADMIN.md`.

**Interfaces:**
- Consume: `Recipe`, `RecipeVisibility`, `newRecipeFields(...)` (privada del repositorio), `recipeToFirestoreDoc` y `seedImportedRecipe` (tests de reglas).
- Produce:
  - `Recipe.importedAt: Instant? = null` (último parámetro de `Recipe`);
  - `internal data class RecipeRestoreAudit(ownerUid: String, createdBy: String, createdAt: Instant?, updatedBy: String, updatedAt: Instant?, importedAt: Instant?)`;
  - `internal fun Recipe.restoreAudit(restorerUid: String): RecipeRestoreAudit`.

- [ ] **Step 1: Tests.**
  - **JVM**, crear `UT/data/repository/RecipeRestoreTest.kt`:

```kotlin
package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class RecipeRestoreTest {
    private val created = Instant.parse("2026-10-01T10:00:00Z")
    private val imported = Instant.parse("2026-10-12T09:30:00.123Z")

    private fun recipe(visibility: RecipeVisibility, importedAt: Instant? = null) = Recipe(
        id = "lasana-de-carne",
        ownerUid = "catalog-admin",
        visibility = visibility,
        title = "Lasaña de carne",
        description = null,
        category = null,
        createdBy = "catalog-admin",
        createdByName = "Catálogo Bobitos",
        createdAt = created,
        updatedBy = "catalog-admin",
        updatedAt = imported,
        importedAt = importedAt,
    )

    @Test
    fun `a common recipe comes back as it was even when another admin undoes`() {
        assertEquals(
            RecipeRestoreAudit("catalog-admin", "catalog-admin", created, "catalog-admin", imported, imported),
            recipe(RecipeVisibility.GLOBAL, importedAt = imported).restoreAudit("second-admin"),
        )
    }

    @Test
    fun `a common recipe made in the app comes back with its dates and without an import date`() {
        val audit = recipe(RecipeVisibility.GLOBAL).restoreAudit("second-admin")

        assertEquals("catalog-admin", audit.ownerUid)
        assertEquals(created, audit.createdAt)
        assertNull(audit.importedAt)
    }

    @Test
    fun `a private recipe comes back as a new one of whoever undoes, with server dates`() {
        val mine = recipe(RecipeVisibility.PRIVATE, importedAt = imported).copy(ownerUid = "chef", createdBy = "chef")

        assertEquals(RecipeRestoreAudit("chef", "chef", null, "chef", null, null), mine.restoreAudit("chef"))
    }
}
```

  - **Reglas**, en `tests/firebase-emulators.test.mjs`:
    - **sustituir** el test ««Deshacer»: el admin recrea con el mismo id una común importada que acaba de borrar, sin su fuente», que fija el comportamiento antiguo, por estos dos:

```js
test("«Deshacer»: el admin recrea una común importada tal como era, con su dueño, sus fechas y su fuente", async () => {
  await seedImportedRecipe("lentejas-deshacer");
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  const ref = doc(admin, "recipes", "lentejas-deshacer");
  const before = (await getDoc(ref)).data();
  await assertSucceeds(writeBatch(admin).delete(ref).commit());

  await assertSucceeds(setDoc(ref, before));

  const after = (await getDoc(ref)).data();
  assert.equal(after.ownerUid, RECIPE_ADMIN_UID);
  assert.equal(after.createdByName, before.createdByName);
  assert.equal(after.source.provider, "bobitos");
  assert.ok(after.source.importedAt.isEqual(before.source.importedAt));
  assert.ok(after.updatedAt.isEqual(before.updatedAt));
});

test("«Deshacer» de una común: solo un admin, con dueño, autor y editor admins y fechas pasadas", async () => {
  await seedImportedRecipe("lentejas-deshacer-mal");
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  const ref = doc(admin, "recipes", "lentejas-deshacer-mal");
  const before = (await getDoc(ref)).data();
  await assertSucceeds(writeBatch(admin).delete(ref).commit());
  const future = Timestamp.fromMillis(Date.now() + 3_600_000);

  await assertFails(setDoc(ref, { ...before, ownerUid: "otro-autor", createdBy: "otro-autor" }));
  await assertFails(setDoc(ref, { ...before, createdBy: "otro-autor" }));
  await assertFails(setDoc(ref, { ...before, updatedBy: "otro-autor" }));
  await assertFails(setDoc(ref, { ...before, updatedAt: future }));
  await assertFails(setDoc(ref, { ...before, createdAt: future }));
  await assertFails(setDoc(ref, { ...before, visibility: "PRIVATE" }));

  const normal = verifiedFirestore("recipe-undo-normal");
  const normalRef = doc(normal, "recipes", "lentejas-deshacer-mal");
  await assertFails(setDoc(normalRef, before));
  await assertFails(setDoc(normalRef, {
    ...before, ownerUid: "recipe-undo-normal", createdBy: "recipe-undo-normal", updatedBy: "recipe-undo-normal",
  }));
});
```

- [ ] **Step 2: Rojo.**
  - JVM de `com.dlunaunizar.bobitos.data.repository.RecipeRestoreTest`: no compila (faltan `importedAt`, `RecipeRestoreAudit` y `restoreAudit`).
  - EMU: falla el primer test de reglas (hoy un cliente no puede crear con `source` ni con fechas pasadas). El segundo puede pasar ya: queda de guarda.
  - Copiar los fallos.

- [ ] **Step 3: Implementación.**
  - **`Recipe.kt`:** último parámetro de `Recipe`:

```kotlin
    // Recetas del catálogo: cuándo la escribió el importador (`source.importedAt`); null en las demás.
    val importedAt: Instant? = null,
```

  - **Crear `RecipeRestore.kt`:**

```kotlin
package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import java.time.Instant

/** Dueño, autor, fechas e importación con que «Deshacer» recrea una receta. Una fecha null es la hora del servidor. */
internal data class RecipeRestoreAudit(
    val ownerUid: String,
    val createdBy: String,
    val createdAt: Instant?,
    val updatedBy: String,
    val updatedAt: Instant?,
    val importedAt: Instant?,
)

/**
 * Una común vuelve tal como era (dueño, autor, fechas y `source.importedAt`), aunque la deshaga otro admin: así el
 * importador la sigue tratando como antes del borrado (reglas: `validRecipeRestore`). Una privada vuelve como receta
 * nueva de quien deshace.
 */
internal fun Recipe.restoreAudit(restorerUid: String): RecipeRestoreAudit = when (visibility) {
    RecipeVisibility.GLOBAL -> RecipeRestoreAudit(ownerUid, createdBy, createdAt, updatedBy, updatedAt, importedAt)
    RecipeVisibility.PRIVATE -> RecipeRestoreAudit(restorerUid, restorerUid, null, restorerUid, null, null)
}
```

  - **`FirestoreRecipeRepository.kt`:**
    - En `restoreRecipe`:
      - `val fields = newRecipeFields(…)` pasa a ser `val fields = newRecipeFields(…) + restoredFields(recipe.restoreAudit(user.id))`; el `+` de mapas sustituye las claves repetidas;
      - el comentario del autor no cambia;
      - encima de la función: «// «Deshacer»: una común vuelve tal como era y una privada, como receta nueva (ver restoreAudit).».
    - Función privada nueva en la clase, tras `newRecipeFields`:

```kotlin
    // Dueño, autor y fechas de la receta recreada; las fechas null, la hora del servidor. `source` solo si venía del
    // importador.
    private fun restoredFields(audit: RecipeRestoreAudit): Map<String, Any?> = buildMap {
        put(FIELD_OWNER_UID, audit.ownerUid)
        put(FIELD_CREATED_BY, audit.createdBy)
        put(FIELD_CREATED_AT, audit.createdAt?.toTimestamp() ?: FieldValue.serverTimestamp())
        put(FIELD_UPDATED_BY, audit.updatedBy)
        put(FIELD_UPDATED_AT, audit.updatedAt?.toTimestamp() ?: FieldValue.serverTimestamp())
        audit.importedAt?.let { importedAt ->
            put(FIELD_SOURCE, mapOf(SOURCE_PROVIDER to CATALOG_PROVIDER, SOURCE_IMPORTED_AT to importedAt.toTimestamp()))
        }
    }
```

    - En el `companion object`, junto a los `FIELD_…`:

```kotlin
        const val FIELD_SOURCE = "source"
        const val SOURCE_PROVIDER = "provider"
        const val SOURCE_IMPORTED_AT = "importedAt"
        const val CATALOG_PROVIDER = "bobitos"
```

    - Funciones privadas de fichero, junto a `toRecipe`:

```kotlin
private fun Instant.toTimestamp(): Timestamp = Timestamp(epochSecond, nano)

// Recetas del importador: `source.importedAt` con proveedor «bobitos»; null en las demás.
private fun DocumentSnapshot.catalogImportedAt(): Instant? {
    val source = get("source") as? Map<*, *> ?: return null
    if (source["provider"] != "bobitos") return null
    return (source["importedAt"] as? Timestamp)?.toDate()?.toInstant()
}
```

    - En `toRecipe`, añadir `importedAt = catalogImportedAt(),` tras `updatedAt = updatedAt,`. La firma de `toRecipe` no cambia.
    - Imports: `com.google.firebase.Timestamp` y `java.time.Instant`.
  - **`firestore.rules`**, tras `validNewRecipe()`:

```
    // «Deshacer» el borrado de una común: un admin la recrea tal como era (dueño, autor, fechas y `source` del
    // importador), aunque sea de otro admin, para que el importador la siga tratando como antes. Dueño, autor y último
    // editor tienen que ser admins y las fechas, pasadas.
    function validRecipeRestore() {
      return isRecipeAdmin() && validRecipeShape()
        && request.resource.data.visibility == "GLOBAL"
        && request.resource.data.ownerUid in recipeAdmins()
        && request.resource.data.createdBy == request.resource.data.ownerUid
        && request.resource.data.updatedBy in recipeAdmins()
        && request.resource.data.createdAt <= request.resource.data.updatedAt
        && request.resource.data.updatedAt <= request.time;
    }
```

    y en `match /recipes/{recipeId}`: `allow create: if validNewRecipe() || validRecipeRestore();`.
  - **`docs/RECIPE_CATALOG.md`:** el aviso de «Deshacer» (hoy: «… pero sin `source` (un cliente no puede escribirlo): el importador la omite …») pasa a ser:
    > «Deshacer» tras borrar una común en la app la recrea **tal como era**: mismo id, dueño, autor, fechas y `source`, aunque la deshaga otro admin. El importador la sigue tratando como antes del borrado. Las comunes que se deshicieron antes de la beta 22 quedaron sin `source` y el importador las omite como «ficha manual del admin»: para devolverlas al catálogo, bórralas en la app sin deshacer y vuelve a importar.
  - **`docs/RECIPES_ADMIN.md`**, en «Notas de seguridad y coste», un punto nuevo tras el de «Publicar `GLOBAL`…»:
    > «Deshacer» el borrado de una `GLOBAL` (`validRecipeRestore`): un admin la recrea con su dueño, autor, fechas pasadas y `source` originales. Dueño, autor y último editor tienen que estar en `recipeAdmins()`; un usuario normal no puede.

- [ ] **Step 4: Verde.** JVM de `RecipeRestoreTest` y `RecipesViewModelTest`; GRADLE completo; EMU (todos los tests de reglas, también los de recetas privadas y el de «un admin puede curar (borrar) una receta GLOBAL ajena»); NODE.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/core/model/Recipe.kt app/src/main/java/com/dlunaunizar/bobitos/data/repository/RecipeRestore.kt app/src/main/java/com/dlunaunizar/bobitos/data/repository/FirestoreRecipeRepository.kt app/src/test/java/com/dlunaunizar/bobitos/data/repository/RecipeRestoreTest.kt firestore.rules tests/firebase-emulators.test.mjs docs/RECIPE_CATALOG.md docs/RECIPES_ADMIN.md
git commit -m "fix(recetas): «Deshacer» recrea una común tal como era, con su dueño, sus fechas y su fuente"
```

---

## Ejecución (`delegating-plan-execution`, plan de menores)

1. Rama `agent/menores-compra-y-deshacer-comunes`, desde `main` (46f0b28), con este plan como primer commit.
2. Las tareas 1 a 6, una tras otra, cada una con un `sonnet` que recibe su brief. En el `task-done`:
   - Tareas 1 a 5: JVM de las clases nombradas y GRADLE completo;
   - Tarea 6: JVM, GRADLE, EMU y NODE.
3. **Revisión final** con `opus`, más los arreglos (un `sonnet`, cada uno con su test RED→GREEN). **Re-revisión acotada** si un arreglo toca `firestore.rules`, la restauración de comunes (permisos) o `migrations/`; si no, no hay re-revisión.
4. **Sin `simplify`** (plan de menores).
5. **`code-review`** en nivel `high`, más los arreglos (misma regla de re-revisión).
6. **Cierre:**
   - Los menores nuevos van al backlog.
   - En el backlog se tachan los 7 menores de la sección «2026-10-13 — `agent/menores-recetas-compra`» y la línea de R4 «solo con un segundo admin», con la rama.
   - El usuario decide qué hacer con la rama.

## Verificación de punta a punta (coordinador, al final)

- **Suites:** GRADLE, NODE y EMU en verde.
- **Preparación del emulador:**
  - `npm run emulators` (con `JAVA_HOME` de Android Studio) y `npm run seed:emulators`.
  - Script en el scratchpad con el Admin SDK y `FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099`, porque `seed:emulators` no crea usuarios:
    - crear el usuario sembrado y ligarlo al espacio;
    - crear un usuario con el **uid del admin** (`dWWH7eRhHEPopJf5BHPB3Dp6fry1`, email verificado) y ligarlo también.
  - Importar ingredientes y recetas al emulador: `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 npm run catalog:import-recipes -- --project demo-bobitos --apply`, y antes el de ingredientes si hace falta.
  - Instalar la app debug.
- **Recorrido**, con capturas en tema claro y oscuro del aviso «Ya en la lista» y de la revisión:
  1. **Unidades:**
     - en la Compra, «Aceite» pendiente con «1/2 cucharadas soperas»; añadir «aceite» con «1/2 cucharadas soperas» y «Sumar cantidad» → «1 cucharada sopera»;
     - «1 cl» + «2 cl» → «3 cl».
  2. **Aviso:** artículo «Harina» pendiente con «2 paquetes grandes del supermercado de abajo»; añadir «harina» → el aviso parte la cantidad entre palabras, nunca a mitad de una.
  3. **Pareja:** «Leche» pendiente con tienda DIA y sin marca; añadir «leche» con Mercadona y «Hacendado» y «Sumar cantidad» → queda DIA y sin marca.
  4. **Lectura fallida:**
     - con la Compra abierta, quitar la membresía del usuario con un script del Admin SDK: la lista muestra el error;
     - devolver la membresía; en el editor, «Guardar» → «No se ha podido leer la lista de la compra…» y la lista vuelve a cargarse;
     - «Guardar» otra vez → se añade.
     - Si el emulador no deja provocarlo, se salta y se dice.
  5. **Marca larga:** desde una receta con un ingrediente reconocido sin marcas registradas, «Añadir a la compra» y teclear 61 caracteres en la marca → el campo se marca y «Confirmar» está deshabilitado.
  6. **Deshacer una común** (sesión del admin):
     - borrar «Lasaña de carne» y pulsar «Deshacer» → vuelve, con autor «Catálogo Bobitos»;
     - después, `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 npm run catalog:import-recipes -- --project demo-bobitos` (simulación) → «Lasaña de carne» sale en «Sin cambios» y no en «Omitidas».

## Despliegue (tras el merge, cada paso confirmado por el usuario)

Trampas conocidas: `--project dev`; `JAVA_HOME` de Android Studio; `beta:distribute` sube el APK viejo si falla el build.

1. **Recetas en `bobitos-dev`** (para «Salpimienta.», del plan anterior):
   - simulación: `GOOGLE_APPLICATION_CREDENTIALS=~/.config/bobitos/bobitos-dev-adminsdk.json npm run catalog:import-recipes -- --project dev`. Lo esperado: 1 receta actualizada («Lasaña de carne») y ninguna creada. Si sale alguna en «Omitidas (ficha manual del admin, sin fuente)», es una común deshecha antes de este plan (ver el aviso de `docs/RECIPE_CATALOG.md`): se le dice al usuario antes de seguir;
   - si el plan es ese, repetir con `--apply`.
2. **Reglas** (este plan las cambia: `validRecipeRestore`): `npx firebase deploy --only firestore:rules --project dev`, con el `JAVA_HOME` de Android Studio si lo pide.
3. **Beta 22** (`0.1.0-beta.22`, `VERSION_CODE=22`):
   - notas en `distribution/release-notes.txt`, con los cambios visibles de este plan y del anterior (`2026-10-12-menores-recetas-compra.md`);
   - build release firmado;
   - `npm run beta:distribute`, después de comprobar que el build ha terminado bien.
