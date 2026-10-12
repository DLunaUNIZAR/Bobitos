# Compra sin duplicados — Plan de implementación (plan A de 3)

> **Para agentes:** se ejecuta con `delegating-plan-execution`. Es un **plan grande** (4 tareas), así que lleva los pasos 1 a 6, incluido `simplify`. Cada tarea la hace un subagente `sonnet`, en serie y en orden. La revisión final la hace `opus` (si falla, `fable`). No cambia `firestore.rules` (la Tarea 2 solo añade un test que lo comprueba); si un arreglo acabara cambiándolas, lleva re-revisión acotada (permisos). Los pasos usan casillas (`- [ ]`).

**Objetivo:** al «Añadir a la compra» desde una receta, una comida, la ficha de un ingrediente o el alta manual, un artículo que ya está en la lista no se duplica: si está pendiente se pregunta (Omitir / Sumar, con Omitir por defecto en la revisión; «Sumar cantidad» / «No añadir» en el editor) y si está comprado se vuelve a apuntar ese mismo artículo.

**Arquitectura:**
- **Lógica pura en `core/model`:** `shoppingMatchKey`/`findShoppingMatch` (reconocer un repetido con la clave tolerante `ingredientKey`) y `ShoppingQuantity.sum` (sumar «200 g» + «300 g»).
- **Repositorio:** `ShoppingRepository.relistItem` vuelve a apuntar un comprado en una sola escritura (pendiente + cantidad, notas, tienda y marca).
- **Revisión de receta y comida** (`feature/common/IngredientReview.kt`, compartida): filas NEW / PENDING / PURCHASED; PENDING con selector Omitir/Sumar; la unidad va dentro de la cantidad y deja de ir a `notes`; si todo queda en Omitir, aviso «Ya estaba todo en la lista».
- **Editor de la Compra:** la decisión (añadir, preguntar o volver a apuntar) pasa al `ShoppingViewModel` (estado `duplicate`), que la pantalla solo pinta.

**Stack:** Kotlin + Jetpack Compose (Material 3) + Hilt; reglas de Firestore (`@firebase/rules-unit-testing`, Node 22).

**Spec:** `docs/superpowers/specs/2026-10-12-recetario-y-compra-design.md`, «Parte A» (A1–A6) y decisiones 1, 6 y 7. Los planes B (receta con pasos) y C (catálogo de recetas) vienen después; el despliegue es único tras el plan C.

## Rulings de planificación (desviaciones conscientes de la spec)

1. **Unidad del resultado de una suma:** g, kg, ml, l y ud se escriben en su forma corta («200 gramos» + «300 g» → «500 g»); las demás concuerdan en número con el total («1 diente» + «2 dientes» → «3 dientes»; «1 diente» + «1 diente» → «2 dientes»; «1/2 cucharadita» × 2 → «1 cucharadita»). La spec decía «la unidad del primer sumando», que daba «3 diente». Coste si mal: una unidad rara en la lista, editable.
2. **Fracciones con 2 decimales:** «1/3» + «1/3» → «0,66».
3. **La decisión del editor de la Compra se mueve al `ShoppingViewModel`** (estado `duplicate`), en vez de vivir en la pantalla: así se prueba en JVM y sobrevive a una rotación.
4. **«No añadir» cierra el editor; tocar fuera del aviso vuelve al editor** sin escribir (para corregir el nombre).
5. **Al volver a apuntar desde el editor se conserva el nombre del artículo existente** («leches» escrito → vuelve «Leche»); cantidad, notas, tienda y marca son las del editor (spec A4). `relistItem` recibe el nombre (mismo perfil que `updateItem`) para validarlo con `validateItem`.
6. **La pista en línea del editor solo avisa de pendientes**; un comprado se vuelve a apuntar sin aviso (spec A4).
7. **`IngredientReviewRow.unit` desaparece:** la cantidad ya lleva la unidad («300 g»). Los artículos antiguos con la unidad en `notes` no se migran; al sumar «200» (sin unidad) con «100 g» se unen con « + ».

## Restricciones globales

- **Rutas:** `APP` = `app/src/main/java/com/dlunaunizar/bobitos/`; `UT` = `app/src/test/java/com/dlunaunizar/bobitos/`; `RES` = `app/src/main/res/values/strings.xml`; `T` = `tests/`.
- **Comandos de verificación**, en llamadas separadas y sin `sh -c`:
  - **GRADLE:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:assembleDebug :app:ktlintCheck :app:detekt :app:testDebugUnitTest`
  - **JVM (una clase):** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:testDebugUnitTest --tests "<clase completa>"`
  - **EMU** (reglas): `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && npm run test:emulators`
- **Prohibido:** cualquier script contra `bobitos-dev` (`--project dev` o `bobitos-dev`), desplegar, distribuir betas, push. No se descarga nada.
- **Commits con ficheros con nombre:** nunca `git add -A` ni `git add .`.
- **Máximo de una cantidad: 40 caracteres** (reglas y `ShoppingValidation`); a partir de la Tarea 1 la constante vive en `ShoppingQuantity.MAX_LENGTH` y `ShoppingValidation.MAX_QUANTITY_LENGTH` la reutiliza.
- **Omitir por defecto** en una fila ya pendiente de la revisión (decisión 6).
- **Textos y comentarios en español**, con el estilo de los ficheros vecinos. Textos de la app en `RES`.
- **Tests Kotlin:** JVM puros (JUnit 4 + `kotlinx-coroutines-test`), dobles a mano; sin mockk ni Robolectric. **Orden TDD obligatorio:** test primero, verlo en rojo (copiar el fallo en el informe), después el código.
- **detekt:** `ReturnCount` máx. 4, `LongMethod` máx. 80 líneas, `TooManyFunctions` 25 por clase; extraer código antes que tocar el baseline. **ktlint:** sin imports sin usar.
- **Tests existentes:** solo se cambian los que fijan el comportamiento que esta spec sustituye, y cada tarea dice cuáles y cómo.

## Review Focus

1. **Sumar se pasa de 40 caracteres** (dos textos libres largos): en la revisión, el campo marca el error y Confirmar queda deshabilitado; en el editor, sale el error de cantidad y no se escribe nada. → `IngredientReviewTest` (Tarea 3) y `ShoppingViewModelTest` (Tarea 4).
2. **El ingrediente de la receta no trae cantidad** («Sal») y en la lista hay «Sal · 1 kg» pendiente: Sumar parte de «1 kg», no lo borra. → `IngredientReviewTest` (Tarea 3).
3. **En la lista hay el mismo producto comprado y pendiente** («Tomates» comprado, «tomate» pendiente): manda el pendiente, en la revisión y en el editor. → `ShoppingMatchTest` (Tarea 1) e `IngredientReviewTest` (Tarea 3).
4. **Nombres sin clave del catálogo** (solo palabras vacías como «De», o símbolos): se comparan por el nombre en minúsculas, y un nombre en blanco nunca coincide con nada. → `ShoppingMatchTest` (Tarea 1).
5. **Cantidades mal formadas** («1/0», «1,2,3», «½», «un puñado»): la suma nunca falla; las une con « + ». → `ShoppingQuantityTest` (Tarea 1).

---

### Task 1: Reconocer repetidos y sumar cantidades (lógica pura)

**Ficheros:**
- Crear: `APP/core/model/ShoppingMatch.kt`, `APP/core/model/ShoppingQuantity.kt`, `UT/core/model/ShoppingMatchTest.kt`, `UT/core/model/ShoppingQuantityTest.kt`.
- Modificar: `APP/feature/shopping/ShoppingValidation.kt` (la constante de 40).

**Interfaces:**
- Consume: `ingredientKey(name: String): String` de `APP/core/model/CatalogIngredient.kt`; `ShoppingItem` de `APP/core/model/ShoppingItem.kt`.
- Produce (las usan las Tareas 3 y 4):
  - `fun shoppingMatchKey(name: String): String`
  - `fun findShoppingMatch(items: List<ShoppingItem>, name: String): ShoppingItem?`
  - `object ShoppingQuantity { const val MAX_LENGTH = 40; fun sum(a: String?, b: String?): String? }`

- [ ] **Step 1: Test de `ShoppingMatch` (rojo)**

Crear `UT/core/model/ShoppingMatchTest.kt`:

```kotlin
package com.dlunaunizar.bobitos.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ShoppingMatchTest {
    @Test
    fun `the key ignores plurals, accents, case and empty words`() {
        assertEquals(shoppingMatchKey("Tomate"), shoppingMatchKey("tomates"))
        assertEquals(shoppingMatchKey("Limón"), shoppingMatchKey("LIMONES"))
        assertEquals(shoppingMatchKey("Pan de molde"), shoppingMatchKey("pan molde"))
    }

    @Test
    fun `different products keep different keys`() {
        assertNotEquals(shoppingMatchKey("Leche entera"), shoppingMatchKey("Leche"))
        assertNotEquals(shoppingMatchKey("Tomate frito"), shoppingMatchKey("Tomate"))
    }

    @Test
    fun `a name without catalog key falls back to the trimmed lowercase name`() {
        assertEquals("de", shoppingMatchKey(" De "))
        assertEquals("¡¡!!", shoppingMatchKey("¡¡!!"))
    }

    @Test
    fun `the match prefers a pending item over a purchased one`() {
        val items = listOf(item("a", "Tomates", purchased = true), item("b", "tomate"), item("c", "Tomate"))

        assertEquals("b", findShoppingMatch(items, "Tomate")?.id)
    }

    @Test
    fun `with only purchased matches the first purchased one is returned`() {
        val items = listOf(item("a", "Leche", purchased = true), item("b", "leche", purchased = true))

        assertEquals("a", findShoppingMatch(items, "LECHE")?.id)
    }

    @Test
    fun `blank names and other products never match`() {
        val items = listOf(item("a", "Leche"), item("b", "¡¡!!"))

        assertNull(findShoppingMatch(items, "   "))
        assertNull(findShoppingMatch(items, "Pan"))
        assertEquals("b", findShoppingMatch(items, " ¡¡!! ")?.id)
    }

    private fun item(id: String, name: String, purchased: Boolean = false) = ShoppingItem(
        id = id,
        name = name,
        quantity = null,
        notes = null,
        purchased = purchased,
        createdBy = "u",
        createdByName = "U",
        createdAt = Instant.EPOCH,
        updatedBy = "u",
        updatedAt = Instant.EPOCH,
        purchasedBy = null,
        purchasedByName = null,
        purchasedAt = null,
    )
}
```

- [ ] **Step 2: Verlo en rojo**

Run: JVM con `com.dlunaunizar.bobitos.core.model.ShoppingMatchTest`
Expected: FAIL de compilación («Unresolved reference: shoppingMatchKey»).

- [ ] **Step 3: Implementar `ShoppingMatch.kt`**

Crear `APP/core/model/ShoppingMatch.kt`:

```kotlin
package com.dlunaunizar.bobitos.core.model

/**
 * Clave para reconocer un artículo repetido en la lista de la compra: la tolerante del catálogo
 * ([ingredientKey]: «Tomates» = «tomate») o, si queda vacía (solo palabras vacías o símbolos), el
 * nombre recortado en minúsculas.
 */
fun shoppingMatchKey(name: String): String = ingredientKey(name).ifEmpty { name.trim().lowercase() }

/**
 * El artículo de [items] que corresponde a [name]: el primero pendiente o, si todos están comprados,
 * el primero comprado. `null` si no hay ninguno o si [name] está en blanco.
 */
fun findShoppingMatch(items: List<ShoppingItem>, name: String): ShoppingItem? {
    if (name.isBlank()) return null
    val key = shoppingMatchKey(name)
    val matches = items.filter { shoppingMatchKey(it.name) == key }
    return matches.firstOrNull { !it.purchased } ?: matches.firstOrNull()
}
```

- [ ] **Step 4: Verlo en verde**

Run: JVM con `com.dlunaunizar.bobitos.core.model.ShoppingMatchTest`
Expected: PASS (6 tests).

- [ ] **Step 5: Test de `ShoppingQuantity` (rojo)**

Crear `UT/core/model/ShoppingQuantityTest.kt`:

```kotlin
package com.dlunaunizar.bobitos.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShoppingQuantityTest {
    @Test
    fun `numbers with the same unit are added`() {
        assertEquals("500 g", ShoppingQuantity.sum("200 g", "300 g"))
        assertEquals("3", ShoppingQuantity.sum("1", "2"))
        assertEquals("2,5 kg", ShoppingQuantity.sum("1,5 kg", "1 kg"))
        assertEquals("2 l", ShoppingQuantity.sum("1.5 l", "0,5 litros"))
        assertEquals("500 g", ShoppingQuantity.sum("200g", "300 g"))
    }

    @Test
    fun `basic units are written in their short form`() {
        assertEquals("500 g", ShoppingQuantity.sum("200 gramos", "300 g"))
        assertEquals("3 ud", ShoppingQuantity.sum("1 unidad", "2 uds"))
        assertEquals("2 kg", ShoppingQuantity.sum("1 Kg", "1 kilo"))
    }

    @Test
    fun `other units agree in number with the total`() {
        assertEquals("3 dientes", ShoppingQuantity.sum("1 diente", "2 dientes"))
        assertEquals("2 dientes", ShoppingQuantity.sum("1 diente", "1 diente"))
        assertEquals("3 cucharadas", ShoppingQuantity.sum("2 cucharadas", "1 cucharada"))
        assertEquals("1 cucharadita", ShoppingQuantity.sum("1/2 cucharadita", "1/2 cucharadita"))
    }

    @Test
    fun `fractions are added with two decimals`() {
        assertEquals("0,75", ShoppingQuantity.sum("1/2", "1/4"))
        assertEquals("0,66", ShoppingQuantity.sum("1/3", "1/3"))
    }

    @Test
    fun `different units are joined, not converted`() {
        assertEquals("200 g + 1 kg", ShoppingQuantity.sum("200 g", "1 kg"))
        assertEquals("2 + 1 ud", ShoppingQuantity.sum("2", "1 ud"))
        assertEquals("200 + 100 g", ShoppingQuantity.sum("200", "100 g"))
    }

    @Test
    fun `a missing quantity leaves the other one`() {
        assertEquals("200 g", ShoppingQuantity.sum(null, "200 g"))
        assertEquals("1 kg", ShoppingQuantity.sum(" 1 kg ", null))
        assertEquals("1 kg", ShoppingQuantity.sum("1 kg", "  "))
        assertNull(ShoppingQuantity.sum(null, " "))
        assertNull(ShoppingQuantity.sum("", null))
    }

    @Test
    fun `malformed quantities never fail and are joined`() {
        assertEquals("1/0 + 1/0", ShoppingQuantity.sum("1/0", "1/0"))
        assertEquals("1,2,3 + 1", ShoppingQuantity.sum("1,2,3", "1"))
        assertEquals("½ + 1", ShoppingQuantity.sum("½", "1"))
        assertEquals("un puñado + un puñado", ShoppingQuantity.sum("un puñado", "un puñado"))
    }
}
```

- [ ] **Step 6: Verlo en rojo**

Run: JVM con `com.dlunaunizar.bobitos.core.model.ShoppingQuantityTest`
Expected: FAIL de compilación («Unresolved reference: ShoppingQuantity»).

- [ ] **Step 7: Implementar `ShoppingQuantity.kt`**

Crear `APP/core/model/ShoppingQuantity.kt`:

```kotlin
package com.dlunaunizar.bobitos.core.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Cantidades de la lista de la compra, que son texto libre («200 g», «1/2», «2 dientes»). [sum] suma
 * dos cantidades si ambas son «número [unidad]» con la misma unidad; si no, las une con « + ».
 */
object ShoppingQuantity {
    /** Máximo de caracteres de una cantidad (también lo exigen las reglas de Firestore). */
    const val MAX_LENGTH = 40

    private const val FRACTION_SCALE = 2
    private val QUANTITY = Regex("""^(\d+(?:[.,]\d+)?|\d+/\d+)\s*(.*)$""")
    private val VOWELS = "aeiouáéíóú"

    // Unidades básicas: todas sus grafías cuentan como la misma y se escriben en su forma corta.
    private val BASIC_UNITS = mapOf(
        "g" to "g", "gr" to "g", "gramo" to "g", "gramos" to "g",
        "kg" to "kg", "kilo" to "kg", "kilos" to "kg",
        "ml" to "ml",
        "l" to "l", "litro" to "l", "litros" to "l",
        "ud" to "ud", "uds" to "ud", "u" to "ud", "unidad" to "ud", "unidades" to "ud",
    )

    /** Suma [a] y [b]; si una falta o está en blanco, devuelve la otra (recortada); `null` si faltan las dos. */
    fun sum(a: String?, b: String?): String? {
        val first = a?.trim().orEmpty()
        val second = b?.trim().orEmpty()
        if (first.isEmpty()) return second.ifEmpty { null }
        if (second.isEmpty()) return first
        val x = parse(first)
        val y = parse(second)
        if (x == null || y == null || x.unitKey != y.unitKey) return "$first + $second"
        val total = x.amount + y.amount
        val unit = unitFor(total, x, y)
        val amount = total.stripTrailingZeros().toPlainString().replace('.', ',')
        return if (unit.isEmpty()) amount else "$amount $unit"
    }

    private data class Parsed(val amount: BigDecimal, val unit: String, val unitKey: String)

    private fun parse(text: String): Parsed? {
        val match = QUANTITY.matchEntire(text) ?: return null
        val (number, rawUnit) = match.destructured
        val amount = amountOf(number) ?: return null
        val unit = rawUnit.trim()
        val lower = unit.lowercase()
        return Parsed(amount, unit, BASIC_UNITS[lower] ?: ingredientKey(lower))
    }

    private fun amountOf(number: String): BigDecimal? {
        if ('/' !in number) return number.replace(',', '.').toBigDecimal()
        val (numerator, denominator) = number.split('/').map(String::toBigDecimal)
        if (denominator.signum() == 0) return null
        return numerator.divide(denominator, FRACTION_SCALE, RoundingMode.HALF_UP)
    }

    // Básicas en forma corta; las demás, en singular si el total es 1 o menos y en plural si pasa de 1.
    private fun unitFor(total: BigDecimal, x: Parsed, y: Parsed): String {
        if (x.unit.isEmpty()) return ""
        BASIC_UNITS[x.unit.lowercase()]?.let { return it }
        val spellings = listOf(x.unit, y.unit)
        if (total <= BigDecimal.ONE) return spellings.firstOrNull { !it.endsWith("s") } ?: x.unit
        return spellings.firstOrNull { it.endsWith("s") } ?: pluralOf(x.unit)
    }

    private fun pluralOf(unit: String): String =
        if (unit.last().lowercaseChar() in VOWELS) "${unit}s" else "${unit}es"
}
```

Nota: `unitFor` tiene 4 `return` (incluido el de `let`), el máximo de detekt. Si ktlint pide otro formato para `BASIC_UNITS`, aplica `./gradlew :app:ktlintFormat` y revisa el diff.

- [ ] **Step 8: Verlo en verde**

Run: JVM con `com.dlunaunizar.bobitos.core.model.ShoppingQuantityTest`
Expected: PASS (7 tests).

- [ ] **Step 9: `ShoppingValidation` reutiliza la constante**

En `APP/feature/shopping/ShoppingValidation.kt`, sustituir `const val MAX_QUANTITY_LENGTH = 40` por:

```kotlin
    const val MAX_QUANTITY_LENGTH = ShoppingQuantity.MAX_LENGTH
```

con `import com.dlunaunizar.bobitos.core.model.ShoppingQuantity`.

- [ ] **Step 10: Suite completa**

Run: GRADLE
Expected: BUILD SUCCESSFUL.

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/core/model/ShoppingMatch.kt app/src/main/java/com/dlunaunizar/bobitos/core/model/ShoppingQuantity.kt app/src/main/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingValidation.kt app/src/test/java/com/dlunaunizar/bobitos/core/model/ShoppingMatchTest.kt app/src/test/java/com/dlunaunizar/bobitos/core/model/ShoppingQuantityTest.kt
git commit -m "feat(compra): reconocer artículos repetidos y sumar cantidades"
```

---

### Task 2: Volver a apuntar un artículo comprado (`relistItem`)

**Ficheros:**
- Modificar: `APP/data/repository/ShoppingRepository.kt`, `APP/data/repository/FirestoreShoppingRepository.kt`, `UT/feature/common/RecordingShoppingRepository.kt`, `UT/feature/shopping/ShoppingViewModelTest.kt` (solo el doble `FakeShoppingRepository`), `T/firebase-emulators.test.mjs`.

**Interfaces:**
- Consume: nada de tareas anteriores.
- Produce (las usan las Tareas 3 y 4):
  - `ShoppingRepository.relistItem(spaceId: String, itemId: String, name: String, quantity: String?, notes: String?, supermarket: Supermarket?, brand: String?)` (suspend): deja el artículo pendiente (`purchased = false`, `purchasedBy`/`purchasedByName`/`purchasedAt` = `null`) y le pone cantidad, notas, tienda y marca, en **una sola escritura**. `name` es el del artículo (se valida, no se cambia).
  - En `RecordingShoppingRepository`: `addedQuantities: MutableList<String?>`, `relistedIds: MutableList<String>`, `relistedQuantities: MutableList<String?>`, `relistedSupermarkets: MutableList<Supermarket?>`, `relistedBrands: MutableList<String?>`.
  - En `FakeShoppingRepository` (`ShoppingViewModelTest.kt`): `val itemState` (deja de ser `private`), `updatedItemId: String?`, `updatedQuantity: String?` y `relisted: RelistCall?`, con `private data class RelistCall(val itemId: String, val name: String, val quantity: String?, val notes: String?, val supermarket: Supermarket?, val brand: String?)`.

- [ ] **Step 1: Test EMU (guarda)**

En `T/firebase-emulators.test.mjs`, justo después del test «marcar y desmarcar conserva una atribución coherente», añadir:

```js
test("volver a apuntar un comprado lo deja pendiente con la cantidad nueva en una sola escritura", async () => {
  await seedSpace("shopping-relist", "relist-owner", ["relist-member"]);
  await seedShoppingItem("shopping-relist", "rice", "relist-owner", { purchased: true, quantity: "1 kg" });
  const member = verifiedFirestore("relist-member");
  const itemReference = doc(member, "spaces", "shopping-relist", "shoppingItems", "rice");

  await assertSucceeds(
    updateDoc(itemReference, {
      name: "Producto",
      quantity: "500 g",
      notes: null,
      supermarket: "DIA",
      brand: "Brillante",
      purchased: false,
      purchasedBy: null,
      purchasedByName: null,
      purchasedAt: null,
      updatedBy: "relist-member",
      updatedAt: serverTimestamp(),
    }),
  );

  const item = await getDoc(itemReference);
  assert.equal(item.data().purchased, false);
  assert.equal(item.data().quantity, "500 g");
  assert.equal(item.data().purchasedBy, null);
  assert.equal(item.data().purchasedAt, null);
  assert.equal(item.data().brand, "Brillante");
});
```

- [ ] **Step 2: Ejecutarlo**

Run: EMU
Expected: PASS. Las reglas ya lo permiten (`validShoppingItemUpdate` acepta estos campos y `validShoppingPurchaseAttribution` exige justo `null` al desmarcar); el test fija ese contrato para `relistItem`. Si fallara, **para** y repórtalo al coordinador: no toques `firestore.rules` sin ruling.

- [ ] **Step 3: Interfaz**

En `APP/data/repository/ShoppingRepository.kt`, después de `updateItem`, añadir:

```kotlin
    /**
     * Vuelve a apuntar un artículo comprado: lo deja pendiente (sin atribución de compra) y le pone
     * cantidad, notas, tienda y marca, en una sola escritura. [name] es el del artículo (no cambia).
     */
    suspend fun relistItem(
        spaceId: String,
        itemId: String,
        name: String,
        quantity: String?,
        notes: String?,
        supermarket: Supermarket?,
        brand: String?,
    )
```

- [ ] **Step 4: Implementación Firestore**

En `APP/data/repository/FirestoreShoppingRepository.kt`, después de `updateItem`, añadir:

```kotlin
    override suspend fun relistItem(
        spaceId: String,
        itemId: String,
        name: String,
        quantity: String?,
        notes: String?,
        supermarket: Supermarket?,
        brand: String?,
    ) = runShoppingOperation {
        val user = requireVerifiedUser()
        val values = validateItem(name, quantity, notes, supermarket, brand)
        val reference = itemsCollection(spaceId).document(itemId)

        firestore.runTransaction { transaction ->
            requireItem(transaction.get(reference))
            transaction.update(
                reference,
                FIELD_QUANTITY,
                values.quantity,
                FIELD_NOTES,
                values.notes,
                FIELD_SUPERMARKET,
                values.supermarket,
                FIELD_BRAND,
                values.brand,
                FIELD_PURCHASED,
                false,
                FIELD_PURCHASED_BY,
                null,
                FIELD_PURCHASED_BY_NAME,
                null,
                FIELD_PURCHASED_AT,
                null,
                FIELD_UPDATED_BY,
                user.id,
                FIELD_UPDATED_AT,
                FieldValue.serverTimestamp(),
            )
        }.await()
        Unit
    }
```

- [ ] **Step 5: Dobles de test**

En `UT/feature/common/RecordingShoppingRepository.kt`:
- añadir los campos `val addedQuantities = mutableListOf<String?>()`, `val relistedIds = mutableListOf<String>()`, `val relistedQuantities = mutableListOf<String?>()`, `val relistedSupermarkets = mutableListOf<Supermarket?>()`, `val relistedBrands = mutableListOf<String?>()`;
- en `addItem`, añadir `addedQuantities += quantity`;
- añadir:

```kotlin
    override suspend fun relistItem(
        spaceId: String,
        itemId: String,
        name: String,
        quantity: String?,
        notes: String?,
        supermarket: Supermarket?,
        brand: String?,
    ) {
        relistedIds += itemId
        relistedQuantities += quantity
        relistedSupermarkets += supermarket
        relistedBrands += brand
    }
```

En `UT/feature/shopping/ShoppingViewModelTest.kt` (solo el doble, ningún test):
- `private val itemState = MutableStateFlow(listOf(shoppingItem()))` pasa a `val itemState = MutableStateFlow(listOf(shoppingItem()))`;
- añadir `var updatedItemId: String? = null`, `var updatedQuantity: String? = null` y `var relisted: RelistCall? = null`;
- en `updateItem`, después de `throwNextFailure()`, añadir `updatedItemId = itemId` y `updatedQuantity = quantity`;
- añadir:

```kotlin
    override suspend fun relistItem(
        spaceId: String,
        itemId: String,
        name: String,
        quantity: String?,
        notes: String?,
        supermarket: Supermarket?,
        brand: String?,
    ) {
        throwNextFailure()
        relisted = RelistCall(itemId, name, quantity, notes, supermarket, brand)
    }
```

- y, al final del fichero, junto a `shoppingItem()`:

```kotlin
private data class RelistCall(
    val itemId: String,
    val name: String,
    val quantity: String?,
    val notes: String?,
    val supermarket: Supermarket?,
    val brand: String?,
)
```

- [ ] **Step 6: Suite completa**

Run: GRADLE
Expected: BUILD SUCCESSFUL (compila con la interfaz nueva; ningún test cambia de resultado).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/data/repository/ShoppingRepository.kt app/src/main/java/com/dlunaunizar/bobitos/data/repository/FirestoreShoppingRepository.kt app/src/test/java/com/dlunaunizar/bobitos/feature/common/RecordingShoppingRepository.kt app/src/test/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingViewModelTest.kt tests/firebase-emulators.test.mjs
git commit -m "feat(compra): volver a apuntar un artículo comprado en una sola escritura"
```

---

### Task 3: Revisión de «Añadir a la compra» (receta y comida) sin duplicados

**Ficheros:**
- Modificar: `APP/feature/common/IngredientReview.kt`, `APP/feature/recipes/RecipesViewModel.kt`, `APP/feature/recipes/RecipeUiMessage.kt`, `APP/feature/recipes/RecipesScreen.kt` (solo el `when` de mensajes), `APP/feature/meals/MealsViewModel.kt`, `APP/feature/meals/MealUiMessage.kt`, `APP/feature/meals/MealsScreen.kt` (solo el `when` de mensajes), `RES`, `docs/DATA_MODEL.md`.
- Tests: `UT/feature/common/IngredientReviewTest.kt`, `UT/feature/meals/MealsViewModelTest.kt`, `UT/feature/recipes/RecipesViewModelTest.kt`.

**Interfaces:**
- Consume: `shoppingMatchKey`, `findShoppingMatch`, `ShoppingQuantity.sum`, `ShoppingQuantity.MAX_LENGTH` (Tarea 1); `ShoppingRepository.relistItem` y los campos nuevos de `RecordingShoppingRepository` (Tarea 2).
- Produce:
  - `enum class ReviewRowKind { NEW, PENDING, PURCHASED }` y `val IngredientReviewRow.kind: ReviewRowKind`.
  - `IngredientReviewRow(name: String, recipeQuantity: String?, existing: ShoppingItem?, quantities: List<String> = emptyList(), context: IngredientShoppingContext? = null)` — **sin `unit`**; `recipeQuantity` lleva la unidad («500 g»).
  - `IngredientReviewChoice(quantity: String?, supermarket: Supermarket?, brand: String?, skip: Boolean = false)`.
  - `internal ReviewRowState(quantity: String, selection: BrandStoreSelection, freeBrand: String, include: Boolean = true)`; `internal fun ReviewRowState.withInclude(value: Boolean)`; `internal fun ReviewRowState.quantityTooLong(): Boolean`.
  - `fun List<IngredientReviewChoice>.writesNothing(): Boolean`.
  - `RecipeUiMessage.NothingToAddToShopping`, `MealUiMessage.NothingToAddToShopping`.

- [ ] **Step 1: Tests de la revisión (rojo)**

En `UT/feature/common/IngredientReviewTest.kt`:

a) **Ajustar** los tests que fijan el comportamiento sustituido (la unidad pasa a ir dentro de la cantidad):
- `a new row without ingredient has no context and adds without store or brand`: `assertEquals(IngredientReviewChoice("1", null, null), choice)` pasa a `assertEquals(IngredientReviewChoice("1 kg", null, null), choice)`.
- `the initial state uses the single favorite and its first store`: `assertEquals("300", state.quantity)` pasa a `assertEquals("300 g", state.quantity)`.
- `apply keeps brand and store of rows already in the list and adds new rows with their choice`: `assertEquals(listOf("g"), shopping.addedNotes)` pasa a `assertEquals(listOf<String?>(null), shopping.addedNotes)` y se añade `assertEquals(listOf<String?>("300"), shopping.addedQuantities)`.

b) **Añadir** estos tests (antes de `private fun item(...)`), y añadir a `item(...)` un parámetro `quantity: String? = null` y otro `purchased: Boolean = false` que se pasen al `ShoppingItem`; añadir los imports `org.junit.Assert.assertFalse` y `org.junit.Assert.assertTrue`:

```kotlin
    @Test
    fun `a pending item is omitted by default with the sum ready in case of Sumar`() = runTest {
        val rows = buildIngredientReviewRows(
            listOf(Ingredient("Arroz", "300", "g")),
            listOf(item("s1", "arroz", quantity = "200 g")),
            data,
        )
        val row = rows.single()
        val state = row.initialState()

        assertEquals(ReviewRowKind.PENDING, row.kind)
        assertFalse(state.include)
        assertEquals("500 g", state.quantity)
        val choices = listOf(state.toChoice(row))
        assertTrue(choices.writesNothing())

        val shopping = RecordingShoppingRepository()
        applyIngredientReview("home", rows, choices, shopping)
        assertTrue(shopping.updatedIds.isEmpty())
        assertTrue(shopping.addedNames.isEmpty())
        assertTrue(shopping.relistedIds.isEmpty())
    }

    @Test
    fun `choosing Sumar updates only the quantity of the pending item`() = runTest {
        val existing = item("s1", "Arroz", quantity = "200 g", supermarket = Supermarket.DIA, brand = "Sos")
        val rows = buildIngredientReviewRows(listOf(Ingredient("Arroz", "300", "g")), listOf(existing), data)
        val row = rows.single()
        val choice = row.initialState().withInclude(true).toChoice(row)

        assertFalse(choice.skip)
        val shopping = RecordingShoppingRepository()
        applyIngredientReview("home", rows, listOf(choice), shopping)

        assertEquals(listOf("s1"), shopping.updatedIds)
        assertEquals(listOf<String?>("500 g"), shopping.updatedQuantities)
        assertEquals(listOf<Supermarket?>(Supermarket.DIA), shopping.updatedSupermarkets)
        assertEquals(listOf<String?>("Sos"), shopping.updatedBrands)
        assertTrue(shopping.addedNames.isEmpty())
    }

    @Test
    fun `a purchased item is relisted with the recipe quantity keeping its brand and store`() = runTest {
        val bought = item(
            "s2",
            "Leche",
            quantity = "6 l",
            supermarket = Supermarket.DIA,
            brand = "Pascual",
            purchased = true,
        )
        val rows = buildIngredientReviewRows(listOf(Ingredient("Leche", "1", "l")), listOf(bought), data)
        val row = rows.single()
        val state = row.initialState()

        assertEquals(ReviewRowKind.PURCHASED, row.kind)
        assertTrue(state.include)
        assertEquals("1 l", state.quantity)

        val shopping = RecordingShoppingRepository()
        applyIngredientReview("home", rows, listOf(state.toChoice(row)), shopping)
        assertEquals(listOf("s2"), shopping.relistedIds)
        assertEquals(listOf<String?>("1 l"), shopping.relistedQuantities)
        assertEquals(listOf<Supermarket?>(Supermarket.DIA), shopping.relistedSupermarkets)
        assertEquals(listOf<String?>("Pascual"), shopping.relistedBrands)
        assertTrue(shopping.addedNames.isEmpty())
        assertTrue(shopping.updatedIds.isEmpty())
    }

    @Test
    fun `singular and plural spellings share one row with the quantities added`() {
        val rows = buildIngredientReviewRows(
            listOf(Ingredient("Tomate", "2", "ud"), Ingredient("tomates", "3", "ud")),
            emptyList(),
            data,
        )

        assertEquals(1, rows.size)
        assertEquals("Tomate", rows.single().name)
        assertEquals("5 ud", rows.single().recipeQuantity)
        assertEquals(listOf("2 ud", "3 ud"), rows.single().quantities)
    }

    @Test
    fun `a pending item wins over a purchased one with the same name`() {
        val rows = buildIngredientReviewRows(
            listOf(Ingredient("Tomate", "2", "ud")),
            listOf(item("s1", "Tomates", purchased = true), item("s2", "tomate")),
            data,
        )

        assertEquals("s2", rows.single().existing?.id)
        assertEquals(ReviewRowKind.PENDING, rows.single().kind)
    }

    @Test
    fun `an ingredient without quantity keeps the pending quantity when summing`() {
        val row = buildIngredientReviewRows(
            listOf(Ingredient("Sal")),
            listOf(item("s1", "Sal", quantity = "1 kg")),
            data,
        ).single()

        assertEquals("1 kg", row.initialState().quantity)
    }

    @Test
    fun `a quantity over 40 characters blocks confirming only when it will be written`() {
        val long = ReviewRowState("x".repeat(41), BrandStoreSelection.None, "")

        assertTrue(long.quantityTooLong())
        assertFalse(long.withInclude(false).quantityTooLong())
        assertFalse(long.copy(quantity = "x".repeat(40)).quantityTooLong())
    }

    @Test
    fun `new rows are added with the unit inside the quantity and without notes`() = runTest {
        val rows = buildIngredientReviewRows(listOf(Ingredient("Sal", "1", "kg")), emptyList(), data)
        val row = rows.single()
        val shopping = RecordingShoppingRepository()

        applyIngredientReview("home", rows, listOf(row.initialState().toChoice(row)), shopping)

        assertEquals(listOf<String?>("1 kg"), shopping.addedQuantities)
        assertEquals(listOf<String?>(null), shopping.addedNotes)
    }
```

La firma de `item(...)` queda: `private fun item(id: String, name: String, supermarket: Supermarket? = null, brand: String? = null, quantity: String? = null, purchased: Boolean = false)`.

- [ ] **Step 2: Verlo en rojo**

Run: JVM con `com.dlunaunizar.bobitos.feature.common.IngredientReviewTest`
Expected: FAIL de compilación («Unresolved reference: ReviewRowKind», «withInclude», «quantityTooLong», «writesNothing»…).

- [ ] **Step 3: Lógica de la revisión**

En `APP/feature/common/IngredientReview.kt`, sustituir todo lo que va desde el KDoc de `IngredientReviewRow` hasta el final de `formattedQuantity()` (incluido) por:

```kotlin
/** Qué hace una fila de la revisión según la lista actual de la compra. */
enum class ReviewRowKind {
    /** No está en la lista: se añade. */
    NEW,

    /** Ya está pendiente: «Omitir» (por defecto) o «Sumar». */
    PENDING,

    /** Está, pero comprado: se vuelve a apuntar ese artículo (pendiente, con la cantidad nueva). */
    PURCHASED,
}

/**
 * Fila de la revisión de «añadir a la Compra» de los ingredientes de una receta o comida. Compartida
 * por Comidas y Recetario. `recipeQuantity` es la cantidad con su unidad («500 g»), ya sumada si el
 * ingrediente aparece varias veces; `quantities` lleva cada aportación por separado (con 2+ la
 * revisión las muestra todas).
 */
data class IngredientReviewRow(
    val name: String,
    val recipeQuantity: String?,
    val existing: ShoppingItem?,
    val quantities: List<String> = emptyList(),
    /** Solo en filas nuevas con ingrediente reconocido: da pie a elegir marca y tienda. */
    val context: IngredientShoppingContext? = null,
) {
    val kind: ReviewRowKind
        get() = when {
            existing == null -> ReviewRowKind.NEW
            existing.purchased -> ReviewRowKind.PURCHASED
            else -> ReviewRowKind.PENDING
        }
}

/** Lo que el usuario confirma en cada fila; `skip` = «Omitir» (no se escribe nada). */
data class IngredientReviewChoice(
    val quantity: String?,
    val supermarket: Supermarket?,
    val brand: String?,
    val skip: Boolean = false,
)

/** Estado editable de una fila en el diálogo; `include` = false solo en una fila pendiente con «Omitir». */
internal data class ReviewRowState(
    val quantity: String,
    val selection: BrandStoreSelection,
    val freeBrand: String,
    val include: Boolean = true,
)

// Pendiente: «Omitir» marcado y la suma preparada por si elige «Sumar». Nueva o comprada: la de la receta.
internal fun IngredientReviewRow.initialState(): ReviewRowState = when (kind) {
    ReviewRowKind.PENDING -> ReviewRowState(
        quantity = ShoppingQuantity.sum(existing?.quantity, recipeQuantity).orEmpty(),
        selection = BrandStoreSelection.None,
        freeBrand = "",
        include = false,
    )
    else -> ReviewRowState(
        quantity = recipeQuantity.orEmpty(),
        selection = context?.defaultSelection() ?: BrandStoreSelection.None,
        freeBrand = "",
    )
}

// Cada cambio parte del estado vigente de la fila, no de uno capturado en una composición anterior.
internal fun ReviewRowState.withBrand(context: IngredientShoppingContext, brandId: String?): ReviewRowState =
    copy(selection = context.selectBrand(selection, brandId))

internal fun ReviewRowState.withStore(store: Supermarket?): ReviewRowState =
    copy(selection = selection.copy(store = store))

internal fun ReviewRowState.withFreeBrand(text: String): ReviewRowState = copy(freeBrand = text)

internal fun ReviewRowState.withInclude(value: Boolean): ReviewRowState = copy(include = value)

/** La cantidad que se va a escribir supera el máximo: error en el campo y Confirmar deshabilitado. */
internal fun ReviewRowState.quantityTooLong(): Boolean =
    include && quantity.trim().length > ShoppingQuantity.MAX_LENGTH

internal fun ReviewRowState.toChoice(row: IngredientReviewRow): IngredientReviewChoice {
    val trimmed = quantity.trim().ifEmpty { null }
    val existing = row.existing
    val context = row.context
    return when {
        existing != null -> IngredientReviewChoice(trimmed, existing.supermarket, existing.brand, skip = !include)
        context == null -> IngredientReviewChoice(trimmed, null, null)
        else -> IngredientReviewChoice(
            quantity = trimmed,
            supermarket = selection.store,
            brand = context.brandToSave(selection, freeBrand),
        )
    }
}

/** Si al confirmar no se escribe nada (todas las filas en «Omitir»). */
fun List<IngredientReviewChoice>.writesNothing(): Boolean = all(IngredientReviewChoice::skip)

/**
 * Agrupa [ingredients] por [shoppingMatchKey] («Tomate» y «tomates» van juntos), suma sus cantidades y
 * los cruza con la lista de la compra actual ([currentItems]) con [findShoppingMatch] (manda el
 * pendiente). El orden respeta la primera aparición. Función pura (sin efectos), fácil de testear.
 */
fun buildIngredientReviewRows(
    ingredients: List<Ingredient>,
    currentItems: List<ShoppingItem>,
    data: IngredientShoppingData = IngredientShoppingData(),
): List<IngredientReviewRow> = ingredients.groupBy { shoppingMatchKey(it.name) }.values.map { occurrences ->
    val first = occurrences.first()
    val contributions = occurrences.mapNotNull(Ingredient::formattedQuantity)
    val existing = findShoppingMatch(currentItems, first.name)
    IngredientReviewRow(
        name = first.name,
        recipeQuantity = contributions.fold(null as String?) { total, next -> ShoppingQuantity.sum(total, next) },
        existing = existing,
        quantities = contributions,
        context = if (existing == null) data.contextFor(first.name) else null,
    )
}

/**
 * Aplica la revisión: «Omitir» no escribe; un pendiente con «Sumar» cambia solo su cantidad; un
 * comprado se vuelve a apuntar ([ShoppingRepository.relistItem]); los dos conservan notas, tienda y
 * marca. El resto se añade con la cantidad (que ya lleva la unidad) y el súper y la marca elegidos.
 */
suspend fun applyIngredientReview(
    spaceId: String,
    rows: List<IngredientReviewRow>,
    choices: List<IngredientReviewChoice>,
    shoppingRepository: ShoppingRepository,
) {
    rows.forEachIndexed { index, row ->
        val choice = choices.getOrNull(index)
        if (choice?.skip == true) return@forEachIndexed
        val quantity = choice?.quantity?.trim()?.takeIf(String::isNotEmpty)
        val existing = row.existing
        when {
            existing == null ->
                shoppingRepository.addItem(spaceId, row.name, quantity, null, choice?.supermarket, choice?.brand)
            existing.purchased -> shoppingRepository.relistItem(
                spaceId,
                existing.id,
                existing.name,
                quantity,
                existing.notes,
                existing.supermarket,
                existing.brand,
            )
            else -> shoppingRepository.updateItem(
                spaceId,
                existing.id,
                existing.name,
                quantity,
                existing.notes,
                existing.supermarket,
                existing.brand,
            )
        }
    }
}

// «200 g» / «2» / null si el ingrediente no trae cantidad. Une cantidad y unidad.
private fun Ingredient.formattedQuantity(): String? {
    val q = quantity?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return listOfNotNull(q, unit?.trim()?.takeIf(String::isNotEmpty)).joinToString(" ")
}
```

Imports nuevos: `com.dlunaunizar.bobitos.core.model.ShoppingQuantity`, `com.dlunaunizar.bobitos.core.model.findShoppingMatch`, `com.dlunaunizar.bobitos.core.model.shoppingMatchKey`.

- [ ] **Step 4: Verlo en verde**

Run: JVM con `com.dlunaunizar.bobitos.feature.common.IngredientReviewTest`
Expected: PASS. (Puede fallar la compilación del diálogo hasta el Step 5: si es así, haz el Step 5 y vuelve.)

- [ ] **Step 5: Diálogo**

En el mismo fichero, sustituir `IngredientReviewDialog` entero por lo siguiente (la fila se extrae a `ReviewRowContent` para no pasar de 80 líneas):

```kotlin
@Composable
fun IngredientReviewDialog(
    rows: List<IngredientReviewRow>,
    onConfirm: (List<IngredientReviewChoice>) -> Unit,
    onDismiss: () -> Unit,
) {
    val states = remember(rows) { rows.map { it.initialState() }.toMutableStateList() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.meals_review_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rows.forEachIndexed { index, row ->
                    ReviewRowContent(row, states[index]) { change -> states[index] = change(states[index]) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(rows.mapIndexed { i, row -> states[i].toChoice(row) }) },
                enabled = states.none(ReviewRowState::quantityTooLong),
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ReviewRowContent(
    row: IngredientReviewRow,
    state: ReviewRowState,
    update: ((ReviewRowState) -> ReviewRowState) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = row.name, style = MaterialTheme.typography.titleSmall)
        row.hint()?.let { hint ->
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (row.kind == ReviewRowKind.PENDING) {
            SkipOrSumPicker(include = state.include, onChange = { value -> update { it.withInclude(value) } })
        }
        if (state.include) {
            val tooLong = state.quantityTooLong()
            OutlinedTextField(
                value = state.quantity,
                onValueChange = { text -> update { it.copy(quantity = text) } },
                label = { Text(stringResource(R.string.recipes_ingredient_quantity)) },
                isError = tooLong,
                supportingText = if (tooLong) {
                    { Text(stringResource(R.string.shopping_error_quantity_too_long)) }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val context = row.context
        if (context != null) {
            BrandStorePicker(
                context = context,
                selection = state.selection,
                freeBrand = state.freeBrand,
                onBrandSelected = { brandId -> update { it.withBrand(context, brandId) } },
                onStoreSelected = { store -> update { it.withStore(store) } },
                onFreeBrandChange = { text -> update { it.withFreeBrand(text) } },
            )
        }
    }
}

@Composable
private fun IngredientReviewRow.hint(): String? = when {
    kind == ReviewRowKind.PENDING ->
        stringResource(R.string.meals_review_existing, existing?.quantity ?: "—", recipeQuantity ?: "—")
    kind == ReviewRowKind.PURCHASED -> stringResource(R.string.meals_review_purchased)
    quantities.size >= 2 -> stringResource(R.string.meals_review_multi, quantities.joinToString(" + "))
    recipeQuantity != null -> stringResource(R.string.meals_review_recipe, recipeQuantity)
    else -> null
}

// «Omitir» (no se toca el artículo de la lista) o «Sumar» (se edita la suma propuesta).
@Composable
private fun SkipOrSumPicker(include: Boolean, onChange: (Boolean) -> Unit) {
    val options = listOf(false to R.string.meals_review_skip, true to R.string.meals_review_sum)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = include == value,
                onClick = { onChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(stringResource(label))
            }
        }
    }
}
```

Imports nuevos: `androidx.compose.material3.SegmentedButton`, `androidx.compose.material3.SegmentedButtonDefaults`, `androidx.compose.material3.SingleChoiceSegmentedButtonRow` (en `TasksScreen.kt:771` se usan sin `@OptIn`; si el compilador lo pidiera, añade `@OptIn(ExperimentalMaterial3Api::class)` a `SkipOrSumPicker`).

En `RES`, junto a `meals_review_multi`:

```xml
    <string name="meals_review_purchased">Ya comprado: se vuelve a apuntar</string>
    <string name="meals_review_skip">Omitir</string>
    <string name="meals_review_sum">Sumar</string>
```

- [ ] **Step 6: Tests de los ViewModels (rojo)**

En `UT/feature/meals/MealsViewModelTest.kt`:

a) **Ajustar**:
- `reviewing then confirming pushes a recipe's ingredients to the shopping list`: `assertEquals(listOf("g", null), shoppingRepository.addedNotes)` pasa a `assertEquals(listOf<String?>(null, null), shoppingRepository.addedNotes)` y se añade `assertEquals(listOf<String?>("300", null), shoppingRepository.addedQuantities)`.
- `the review aggregates all quantities of a repeated ingredient`: `assertEquals("200 g + 300 g", review[0].recipeQuantity)` pasa a `assertEquals("500 g", review[0].recipeQuantity)`.

b) **Añadir** (cambiar el helper `shoppingItem(id, name)` del final del fichero a `shoppingItem(id: String, name: String, quantity: String? = null)` y pasar `quantity` al `ShoppingItem`; importar `com.dlunaunizar.bobitos.feature.common.initialState` y `com.dlunaunizar.bobitos.feature.common.toChoice`):

```kotlin
    @Test
    fun `confirming with every row omitted writes nothing and says it was all in the list`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
            shoppingRepository.itemsState.value = listOf(shoppingItem("s1", "arroz", quantity = "1 kg"))
            viewModel.observe("home")
            advanceUntilIdle()

            viewModel.addIngredientsToShopping(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"))
            advanceUntilIdle()
            val review = viewModel.uiState.value.ingredientReview!!
            viewModel.confirmIngredientReview(review.map { it.initialState().toChoice(it) })
            advanceUntilIdle()

            assertTrue(shoppingRepository.updatedIds.isEmpty())
            assertTrue(shoppingRepository.addedNames.isEmpty())
            assertEquals(MealUiMessage.NothingToAddToShopping, viewModel.uiState.value.notice)
            assertEquals(null, viewModel.uiState.value.ingredientReview)
        }

    @Test
    fun `marking cooked offers items whose name differs only in plural`() = runTest(mainDispatcherRule.testDispatcher) {
        recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Tomates", "3", "ud"))))
        shoppingRepository.itemsState.value = listOf(shoppingItem("s1", "Tomate"))
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Ensalada").copy(recipeId = "r1"))
        advanceUntilIdle()

        assertEquals(listOf("Tomate"), viewModel.uiState.value.cookedCrossOff?.map(ShoppingItem::name))
    }
```

(Si `assertTrue` no está importado en ese fichero, añade `org.junit.Assert.assertTrue`.)

En `UT/feature/recipes/RecipesViewModelTest.kt`, añadir después de `confirming the review uses the chosen brand and store` (con los imports `com.dlunaunizar.bobitos.core.model.ShoppingItem`, `java.time.Instant` y `org.junit.Assert.assertTrue` si faltan):

```kotlin
    @Test
    fun `confirming with every row omitted writes nothing and says it was all in the list`() =
        runTest(mainDispatcherRule.testDispatcher) {
            shoppingRepository.itemsState.value = listOf(pendingItem("s1", "Huevos"))
            viewModel.observe()
            advanceUntilIdle()
            viewModel.addToShopping(
                "home",
                recipe("r1", RecipeVisibility.PRIVATE, "Tortilla", listOf(Ingredient("Huevo", "4", "uds"))),
            )
            advanceUntilIdle()

            viewModel.confirmShoppingReview("home", listOf(IngredientReviewChoice(null, null, null, skip = true)))
            advanceUntilIdle()

            assertTrue(shoppingRepository.addedNames.isEmpty())
            assertTrue(shoppingRepository.updatedIds.isEmpty())
            assertEquals(RecipeUiMessage.NothingToAddToShopping, viewModel.uiState.value.notice)
            assertEquals(null, viewModel.uiState.value.ingredientReview)
        }
```

y, al final del fichero:

```kotlin
private fun pendingItem(id: String, name: String) = ShoppingItem(
    id = id,
    name = name,
    quantity = null,
    notes = null,
    purchased = false,
    createdBy = "owner",
    createdByName = "David",
    createdAt = Instant.EPOCH,
    updatedBy = "owner",
    updatedAt = Instant.EPOCH,
    purchasedBy = null,
    purchasedByName = null,
    purchasedAt = null,
)
```

- [ ] **Step 7: Verlo en rojo**

Run: JVM con `com.dlunaunizar.bobitos.feature.meals.MealsViewModelTest` y después con `com.dlunaunizar.bobitos.feature.recipes.RecipesViewModelTest`
Expected: FAIL de compilación («Unresolved reference: NothingToAddToShopping»).

- [ ] **Step 8: ViewModels, mensajes y textos**

`APP/feature/recipes/RecipeUiMessage.kt`: añadir `NothingToAddToShopping,` después de `AddedToShopping,`.
`APP/feature/meals/MealUiMessage.kt`: añadir `NothingToAddToShopping,` después de `IngredientsAddedToShopping,`.

`APP/feature/recipes/RecipesViewModel.kt`, sustituir `confirmShoppingReview` por:

```kotlin
    fun confirmShoppingReview(spaceId: String, choices: List<IngredientReviewChoice>) {
        val rows = mutableUiState.value.ingredientReview ?: return
        if (choices.writesNothing()) {
            mutableUiState.update {
                it.copy(ingredientReview = null, error = null, notice = RecipeUiMessage.NothingToAddToShopping)
            }
            return
        }
        mutableUiState.update { it.copy(ingredientReview = null) }
        runAction(RecipeUiMessage.AddedToShopping) {
            applyIngredientReview(spaceId, rows, choices, shoppingRepository)
        }
    }
```

`APP/feature/meals/MealsViewModel.kt`, sustituir `confirmIngredientReview` por:

```kotlin
    // Aplica la revisión: omite, suma, vuelve a apuntar o añade cada fila (ver applyIngredientReview).
    fun confirmIngredientReview(choices: List<IngredientReviewChoice>) {
        val spaceId = observedSpaceId ?: return
        val rows = mutableUiState.value.ingredientReview ?: return
        if (choices.writesNothing()) {
            mutableUiState.update {
                it.copy(ingredientReview = null, error = null, notice = MealUiMessage.NothingToAddToShopping)
            }
            return
        }
        mutableUiState.update { it.copy(ingredientReview = null) }
        runAction(MealUiMessage.IngredientsAddedToShopping) {
            applyIngredientReview(spaceId, rows, choices, shoppingRepository)
        }
    }
```

y en `markCooked` cambiar el cruce por la clave de la compra (y su comentario):

```kotlin
    // Marca la comida como cocinada y, si su receta tiene ingredientes en la compra sin comprar,
    // ofrece tacharlos (setPurchased) en un diálogo. Cruce por shoppingMatchKey («Tomates» = «tomate»).
```

```kotlin
                val keys = recipe?.ingredients.orEmpty().map { shoppingMatchKey(it.name) }.toSet()
                val candidates = if (keys.isEmpty()) {
                    emptyList()
                } else {
                    val current = runCatching { shoppingRepository.items(spaceId).first() }.getOrDefault(emptyList())
                    current.filter { !it.purchased && shoppingMatchKey(it.name) in keys }
                }
```

Imports: `com.dlunaunizar.bobitos.core.model.shoppingMatchKey` y `com.dlunaunizar.bobitos.feature.common.writesNothing` en los dos ViewModels donde se usen; quita `import com.dlunaunizar.bobitos.core.model.slug` de `MealsViewModel.kt` si queda sin uso.

`APP/feature/recipes/RecipesScreen.kt`, en el `when` de mensajes, después de `RecipeUiMessage.AddedToShopping -> …`:

```kotlin
        RecipeUiMessage.NothingToAddToShopping -> R.string.recipes_notice_nothing_to_add
```

`APP/feature/meals/MealsScreen.kt`, en el `when` de mensajes, después de `MealUiMessage.IngredientsAddedToShopping -> …`:

```kotlin
        MealUiMessage.NothingToAddToShopping -> R.string.meals_notice_nothing_to_add
```

`RES`, junto a `meals_notice_ingredients_added` y a `recipes_notice_added_to_shopping` respectivamente:

```xml
    <string name="meals_notice_nothing_to_add">Ya estaba todo en la lista</string>
```

```xml
    <string name="recipes_notice_nothing_to_add">Ya estaba todo en la lista</string>
```

- [ ] **Step 9: Verlo en verde**

Run: JVM con `com.dlunaunizar.bobitos.feature.meals.MealsViewModelTest`, con `com.dlunaunizar.bobitos.feature.recipes.RecipesViewModelTest` y con `com.dlunaunizar.bobitos.feature.common.IngredientReviewTest`
Expected: PASS.

- [ ] **Step 10: Documentación**

En `docs/DATA_MODEL.md`, sustituir el párrafo de «#### Integración con la Compra (Fase 3)» («Desde una comida enlazada a una receta… La acción respeta `canWrite`.») por:

```markdown
Desde la ficha de una receta o desde una comida enlazada a una receta (`Meal.recipeId`) se pueden **volcar sus ingredientes a la lista de la Compra** del espacio activo, tras una revisión (`IngredientReviewDialog`). Los ingredientes se agrupan con `shoppingMatchKey` («Tomate» y «tomates» van juntos) y sus cantidades se suman (`ShoppingQuantity.sum`). Cada fila nueva crea un `ShoppingItem` (`name`=nombre, `quantity`=cantidad con su unidad, p. ej. «200 g»; sin `notes`). Una fila que ya está pendiente en la lista sale con «Omitir» (por defecto) o «Sumar» (solo cambia la cantidad); una que está comprada se vuelve a apuntar (`relistItem`). Si todo queda en «Omitir», no se escribe nada. La acción respeta `canWrite`.
```

- [ ] **Step 11: Suite completa**

Run: GRADLE
Expected: BUILD SUCCESSFUL.

- [ ] **Step 12: Commit**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/common/IngredientReview.kt app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipesViewModel.kt app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipeUiMessage.kt app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipesScreen.kt app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealsViewModel.kt app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealUiMessage.kt app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealsScreen.kt app/src/main/res/values/strings.xml docs/DATA_MODEL.md app/src/test/java/com/dlunaunizar/bobitos/feature/common/IngredientReviewTest.kt app/src/test/java/com/dlunaunizar/bobitos/feature/meals/MealsViewModelTest.kt app/src/test/java/com/dlunaunizar/bobitos/feature/recipes/RecipesViewModelTest.kt
git commit -m "feat(compra): la revisión de receta y comida omite, suma o vuelve a apuntar los repetidos"
```

---

### Task 4: Alta en la Compra (manual y desde la ficha de ingrediente) sin duplicados

**Ficheros:**
- Modificar: `APP/feature/shopping/ShoppingUiState.kt`, `APP/feature/shopping/ShoppingViewModel.kt`, `APP/feature/shopping/ShoppingScreen.kt`, `RES`, `docs/DATA_MODEL.md`.
- Tests: `UT/feature/shopping/ShoppingViewModelTest.kt`.

**Interfaces:**
- Consume: `findShoppingMatch`, `ShoppingQuantity.sum` (Tarea 1); `ShoppingRepository.relistItem` y, en `FakeShoppingRepository`, `itemState`, `updatedItemId`, `updatedQuantity`, `relisted`/`RelistCall` (Tarea 2).
- Produce:
  - `data class ShoppingDuplicate(val existing: ShoppingItem, val quantity: String?)` (en `ShoppingUiState.kt`) y `ShoppingUiState.duplicate: ShoppingDuplicate? = null`.
  - `ShoppingViewModel.addItem(...)` (misma firma) decide: añadir, preguntar (`duplicate`) o volver a apuntar.
  - `ShoppingViewModel.sumDuplicate(spaceId: String)` y `ShoppingViewModel.dismissDuplicate()`.

- [ ] **Step 1: Tests (rojo)**

En `UT/feature/shopping/ShoppingViewModelTest.kt`:

a) **Ajustar** `manual add no longer applies any preference`: añade `"Pan"` en vez de `"Leche"` (con la lista observada, «Leche» ya está pendiente y ahora preguntaría en vez de añadir):

```kotlin
        viewModel.addItem("home", "Pan", null, null, null, null)
```

y añade `assertEquals("Pan", repository.addedName)` antes de los dos `assertNull`.

b) **Añadir** (después de `restoreItem does not touch the editor status`), con un helper privado nuevo al final del fichero:

```kotlin
private fun listItem(quantity: String? = null, purchased: Boolean = false) = shoppingItem().copy(
    quantity = quantity,
    purchased = purchased,
    supermarket = Supermarket.DIA,
    brand = "Pascual",
)
```

```kotlin
    @Test
    fun `adding a product already pending asks instead of writing`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.itemState.value = listOf(listItem(quantity = "1 l"))
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.addItem("home", " leches ", " 2 l ", null, null, null)
        advanceUntilIdle()

        assertEquals(ShoppingDuplicate(listItem(quantity = "1 l"), "2 l"), viewModel.uiState.value.duplicate)
        assertNull(repository.addedName)
        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.editorSave)
    }

    @Test
    fun `summing a duplicate updates only the quantity of the existing item`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.itemState.value = listOf(listItem(quantity = "1 l"))
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.addItem("home", "Leche", "2 l", null, Supermarket.EROSKI, "Otra")
            advanceUntilIdle()

            viewModel.sumDuplicate("home")
            advanceUntilIdle()

            assertEquals("milk", repository.updatedItemId)
            assertEquals("3 l", repository.updatedQuantity)
            assertEquals(Supermarket.DIA, repository.updatedSupermarket)
            assertEquals("Pascual", repository.updatedBrand)
            assertNull(viewModel.uiState.value.duplicate)
            assertEquals(ShoppingUiMessage.ItemUpdated, viewModel.uiState.value.notice)
            assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.editorSave)
        }

    @Test
    fun `dismissing a duplicate writes nothing`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.itemState.value = listOf(listItem(quantity = "1 l"))
        viewModel.observe("home")
        advanceUntilIdle()
        viewModel.addItem("home", "Leche", "2 l", null, null, null)
        advanceUntilIdle()

        viewModel.dismissDuplicate()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.duplicate)
        assertNull(repository.addedName)
        assertNull(repository.updatedItemId)
    }

    @Test
    fun `adding a purchased product relists it with the editor values and keeps its name`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.itemState.value = listOf(listItem(quantity = "6 l", purchased = true))
            viewModel.observe("home")
            advanceUntilIdle()

            viewModel.addItem("home", " leches ", " 2 ", " sin lactosa ", Supermarket.EROSKI, "Puleva")
            advanceUntilIdle()

            assertEquals(
                RelistCall("milk", "Leche", "2", "sin lactosa", Supermarket.EROSKI, "Puleva"),
                repository.relisted,
            )
            assertNull(repository.addedName)
            assertNull(viewModel.uiState.value.duplicate)
            assertEquals(ShoppingUiMessage.ItemAdded, viewModel.uiState.value.notice)
            assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.editorSave)
        }

    @Test
    fun `a sum longer than 40 characters is reported and not written`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.itemState.value = listOf(listItem(quantity = "x".repeat(30)))
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.addItem("home", "Leche", "y".repeat(10), null, null, null)
            advanceUntilIdle()

            viewModel.sumDuplicate("home")
            advanceUntilIdle()

            assertNull(repository.updatedItemId)
            assertEquals(ShoppingUiMessage.QuantityTooLong, viewModel.uiState.value.error)
            assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.editorSave)
        }
```

- [ ] **Step 2: Verlo en rojo**

Run: JVM con `com.dlunaunizar.bobitos.feature.shopping.ShoppingViewModelTest`
Expected: FAIL de compilación («Unresolved reference: ShoppingDuplicate», «duplicate», «sumDuplicate», «dismissDuplicate»).

- [ ] **Step 3: Estado**

En `APP/feature/shopping/ShoppingUiState.kt`, añadir a `ShoppingUiState` (al final de sus campos):

```kotlin
    // Aviso de artículo ya pendiente al crear uno («Sumar cantidad» / «No añadir»); null si no hay.
    val duplicate: ShoppingDuplicate? = null,
```

y, después de la clase:

```kotlin
/** Artículo ya pendiente en la lista ([existing]) y la cantidad que se quería añadir. */
data class ShoppingDuplicate(val existing: ShoppingItem, val quantity: String?)
```

- [ ] **Step 4: ViewModel**

En `APP/feature/shopping/ShoppingViewModel.kt`, sustituir `addItem` por lo siguiente y añadir `sumDuplicate`, `dismissDuplicate` y la propiedad `currentItems` (propiedad, no función, para no sumar funciones a la clase):

```kotlin
    /**
     * Alta desde el editor. Si el producto ya está pendiente en la lista, no escribe: deja el aviso en
     * [ShoppingUiState.duplicate]. Si está comprado, vuelve a apuntar ese artículo (con su nombre y los
     * valores del editor). Si no está, lo añade. Con la lista aún sin cargar, siempre añade.
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
        val match = findShoppingMatch(currentItems, name)
        when {
            match == null -> runAction(ShoppingUiMessage.ItemAdded, editor = true) {
                repository.addItem(
                    spaceId,
                    name.trim(),
                    quantity.normalized(),
                    notes.normalized(),
                    supermarket,
                    brand,
                )
            }
            !match.purchased -> mutableUiState.update {
                it.copy(duplicate = ShoppingDuplicate(match, quantity.normalized()))
            }
            else -> runAction(ShoppingUiMessage.ItemAdded, editor = true) {
                repository.relistItem(
                    spaceId,
                    match.id,
                    match.name,
                    quantity.normalized(),
                    notes.normalized(),
                    supermarket,
                    brand,
                )
            }
        }
    }

    /** «Sumar cantidad» en el aviso de repetido: solo cambia la cantidad del artículo existente. */
    fun sumDuplicate(spaceId: String) {
        val duplicate = mutableUiState.value.duplicate ?: return
        mutableUiState.update { it.copy(duplicate = null) }
        val existing = duplicate.existing
        val quantity = ShoppingQuantity.sum(existing.quantity, duplicate.quantity)
        if (!validate(existing.name, quantity, existing.notes)) return
        runAction(ShoppingUiMessage.ItemUpdated, editor = true) {
            repository.updateItem(
                spaceId,
                existing.id,
                existing.name,
                quantity,
                existing.notes,
                existing.supermarket,
                existing.brand,
            )
        }
    }

    /** «No añadir» o cerrar el aviso de repetido: no se escribe nada. */
    fun dismissDuplicate() = mutableUiState.update { it.copy(duplicate = null) }

    private val currentItems: List<ShoppingItem>
        get() = (mutableUiState.value.items as? UiState.Content)?.value.orEmpty()
```

Imports: `com.dlunaunizar.bobitos.core.model.ShoppingQuantity`, `com.dlunaunizar.bobitos.core.model.findShoppingMatch` (y `UiState` si no estaba).

- [ ] **Step 5: Verlo en verde**

Run: JVM con `com.dlunaunizar.bobitos.feature.shopping.ShoppingViewModelTest`
Expected: PASS (todos, incluidos los anteriores).

- [ ] **Step 6: Pantalla**

En `APP/feature/shopping/ShoppingScreen.kt`:

1. Borrar `var duplicatePrompt by remember { mutableStateOf<ShoppingDuplicate?>(null) }` y la `private data class ShoppingDuplicate(...)` del final del fichero (ahora vive en `ShoppingUiState.kt`).
2. Sustituir `findByName` por una búsqueda que solo devuelve pendientes (la pista en línea del editor; un comprado se vuelve a apuntar sin aviso):

```kotlin
    val pendingMatch: (String) -> ShoppingItem? = { raw ->
        findShoppingMatch(allItems, raw)?.takeUnless(ShoppingItem::purchased)
    }
```

y pasar `resolveDuplicate = pendingMatch` al `ShoppingItemEditor`.
3. Sustituir el `onSave` del `ShoppingItemEditor` por:

```kotlin
            onSave = { name, quantity, notes, supermarket, brand ->
                val item = editedItem
                if (item != null) {
                    viewModel.updateItem(spaceId, item.id, name, quantity, notes, supermarket, brand)
                } else {
                    viewModel.addItem(spaceId, name, quantity, notes, supermarket, brand)
                }
            },
```

4. Sustituir el bloque `duplicatePrompt?.let { prompt -> AlertDialog(...) }` por:

```kotlin
    state.duplicate?.let { duplicate ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicate,
            title = { Text(stringResource(R.string.shopping_duplicate_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.shopping_duplicate_body,
                        duplicate.existing.name,
                        duplicate.existing.quantity ?: "—",
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.sumDuplicate(spaceId) }) {
                    Text(stringResource(R.string.shopping_duplicate_sum))
                }
            },
            dismissButton = {
                // «No añadir» cierra también el editor; tocar fuera del aviso vuelve al editor.
                TextButton(onClick = {
                    viewModel.dismissDuplicate()
                    editorVisible = false
                }) { Text(stringResource(R.string.shopping_duplicate_skip)) }
            },
        )
    }
```

Import: `com.dlunaunizar.bobitos.core.model.findShoppingMatch`. Quita los imports que queden sin uso.

En `RES`, sustituir las líneas de `shopping_duplicate_body`, `shopping_duplicate_update` y `shopping_duplicate_add` por:

```xml
    <string name="shopping_duplicate_body">«%1$s» ya está en la lista (cantidad: %2$s). ¿Sumas la cantidad o no lo añades?</string>
    <string name="shopping_duplicate_sum">Sumar cantidad</string>
    <string name="shopping_duplicate_skip">No añadir</string>
```

(Comprueba con `grep -rn "shopping_duplicate_update\|shopping_duplicate_add" app/src` que ya no se usan.)

- [ ] **Step 7: Documentación**

En `docs/DATA_MODEL.md`, §8 «Lista de la compra», después del párrafo que empieza por «`brand` es el nombre de la marca elegida…», añadir:

```markdown
**Artículos repetidos.** Al crear un artículo (a mano, desde la ficha de un ingrediente o desde la revisión de una receta o comida) se busca uno ya apuntado con `shoppingMatchKey` (la clave tolerante `ingredientKey`: «Tomates» = «tomate»; si queda vacía, el nombre en minúsculas), y manda el pendiente sobre el comprado. Si está **pendiente**, se pregunta: sumar la cantidad (`ShoppingQuantity.sum`: suma si las dos son número con la misma unidad; si no, las une con « + ») o no añadirlo. Si está **comprado**, se vuelve a apuntar ese mismo documento (`relistItem`: `purchased = false` y atribución de compra a `null`, en una sola escritura) con la cantidad nueva. No se crean duplicados.
```

- [ ] **Step 8: Suite completa**

Run: GRADLE
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingUiState.kt app/src/main/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingViewModel.kt app/src/main/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingScreen.kt app/src/main/res/values/strings.xml docs/DATA_MODEL.md app/src/test/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingViewModelTest.kt
git commit -m "feat(compra): el alta pregunta si sumar o no añadir un repetido y vuelve a apuntar los comprados"
```

---

## Verificación de punta a punta (coordinador, al final)

- GRADLE y EMU en verde.
- **En el emulador:** `npm run emulators` (con `JAVA_HOME` de Android Studio) y `npm run seed:emulators`; instalar la app debug y entrar con un usuario del espacio sembrado.
- **Recorrido**, con capturas en tema claro y oscuro:
  1. Compra: añadir «Tomate · 2 ud» y «Leche · 1 l»; marcar «Leche» como comprada.
  2. Recetario: una receta propia con «Tomates 3 ud», «Leche 1 l» y «Sal» → «Añadir a la compra»: «Tomates» sale como «Ya en la lista: 2 ud · receta: 3 ud» con «Omitir» marcado; «Leche» como «Ya comprado: se vuelve a apuntar»; «Sal» como nueva. Elegir «Sumar» en «Tomates» (propone «5 ud») y confirmar: «Tomate · 5 ud», «Leche · 1 l» pendiente y «Sal» nueva, sin duplicados ni unidad en notas.
  3. Repetir y dejar todo en «Omitir» y la leche ya pendiente: aviso «Ya estaba todo en la lista».
  4. Comidas: la semana con dos comidas que usan la misma receta → una sola fila por ingrediente con la cantidad sumada.
  5. Ingredientes → ficha de «Tomate» → «Añadir a la compra» → guardar: aviso «Ya en la lista» con «Sumar cantidad» y «No añadir»; «No añadir» cierra el editor sin escribir; tocar fuera vuelve al editor.
  6. Marcar «Tomate» como comprado y volver a añadirlo a mano: vuelve a salir pendiente, sin aviso y sin duplicar.
  7. Cocinar una comida con «Tomates» en la receta y «Tomate» pendiente: ofrece tacharlo.

## Despliegue

Ninguno en este plan: el despliegue es único tras el plan C (spec, «Orden y despliegue»). Este plan no cambia `firestore.rules`.
