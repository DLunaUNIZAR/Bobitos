# Receta con pasos, raciones y tiempo + «Mis recetas» — Plan de implementación (plan B de 3)

> **Para agentes:** se ejecuta con `delegating-plan-execution`. Es un **plan grande** (6 tareas), así que lleva los pasos 1 a 6, incluido `simplify`. Cada tarea la hace un subagente `sonnet`, en serie y en orden. La revisión final la hace `opus` (si falla, `fable`). La Tarea 1 cambia `firestore.rules`: si un arreglo posterior vuelve a tocarlas, lleva re-revisión acotada (permisos). Los pasos usan casillas (`- [ ]`).

**Objetivo:** una receta guarda sus pasos (ordenables), sus raciones y su tiempo total; la ficha los muestra; el importador web los trae; «Mis recetas» solo enseña las privadas del usuario y cualquier común se puede «Guardar como mía» (copiando también pasos, raciones y tiempo); el selector de receta de Comidas tiene buscador y separa «Mía» de «Común».

**Arquitectura:**
- **Modelo y límites (`core/model/Recipe.kt`):** `Recipe.steps`/`servings`/`totalMinutes` y `RecipeLimits` + `cleanRecipeSteps`, compartidos por validación, repositorio, importador y editor.
- **Repositorio:** `createRecipe`/`updateRecipe` reciben los tres campos; `FirestoreRecipeRepository` escribe siempre `steps` (lista) y `servings`/`totalMinutes` (entero o `null`); el parseo y los límites son funciones puras en `data/repository/RecipeFirestore.kt` (probadas en JVM).
- **Reglas:** `recipes` admite `steps` (≤30), `servings` (1–50), `totalMinutes` (1–1440) y `source` (solo el importador del plan C).
- **Importador web:** `RecipeMetaParser` (puro) convierte `recipeYield`, duraciones ISO 8601 y pasos; `JsonLdRecipeImporter` deja la descripción de la web como entradilla.
- **Editor:** la hoja envía el `RecipeDraft` entero y `RecipesViewModel.saveDraft` lo convierte.
- **Secciones:** `personalOnly()` y `recipeDetailActions()` (`feature/recipes/RecipeSections.kt`), como en rutinas.
- **Selector de Comidas:** `forPicker()`, `recipeOriginRes()` y el diálogo pasan a `feature/meals/RecipePicker.kt`.

**Stack:** Kotlin + Jetpack Compose (Material 3) + Hilt; `org.json`/Jsoup en el importador; reglas de Firestore (`@firebase/rules-unit-testing`, Node 22).

**Spec:** `docs/superpowers/specs/2026-10-12-recetario-y-compra-design.md`, «Parte B» (B1–B6) y decisiones 2, 5, 8 y 9. El plan A (compra sin duplicados) ya está en `main` (8b4eebe); el plan C (catálogo de recetas) viene después y el despliegue es único tras el C.

## Rulings de planificación (desviaciones conscientes de la spec)

1. **El editor envía el borrador entero.** `RecipeEditor.onSave` pasa a `(RecipeDraft) -> Unit` y `RecipeSheetActions.onSave` a `(Recipe?, RecipeDraft) -> Unit`; el nuevo `RecipesViewModel.saveDraft(recipeId, draft)` lo convierte y llama a `createRecipe`/`updateRecipe` (que ganan `steps`, `servings` y `totalMinutes`). Así la conversión se prueba en JVM y la hoja no pasa 10 valores sueltos.
2. **`RecipeLimits` y `cleanRecipeSteps` viven en `core/model/Recipe.kt`** y los usan validación, repositorio, importador y editor (una sola fuente para 30 / 500 / 1–50 / 1–1440).
3. **Raciones o tiempo con texto no entero** («dos», «1,5») cuentan como fuera de rango: error en el campo y Guardar deshabilitado; nunca se ignoran en silencio. Los espacios alrededor se aceptan.
4. **El importador no corta texto.** Un único texto de instrucciones se parte por saltos de línea (`\n`, `<br>`, `</p>`, `</li>`), y un paso de más de 500 caracteres se parte por frases (y, si una frase sola no cabe, en trozos de 500). La spec decía «recortados a 500»: así no se pierde texto. Siguen siendo como mucho 30 pasos.
5. **`recipeYield`:** el primer entero del texto (o del primer elemento de un array que lo tenga); si no está en 1–50, `null`. Un número decimal se trunca.
6. **`totalTime` que no se entiende o vale 0 cuenta como ausente**, y entonces se usa `prepTime` + `cookTime` (o el que haya). Una duración ISO con años, meses o semanas → `null`; los segundos se suman truncando a minutos.
7. **La ficha da el tiempo siempre en minutos** («90 min»), como las rutinas (`routines_summary_minutes`).
8. **«Sin descripción.» solo si no hay ni entradilla ni pasos**: con pasos, la «Preparación» ya explica la receta.
9. **Acciones del detalle por visibilidad:** una receta PRIVATE visible siempre es del usuario (las reglas no dejan leer privadas ajenas), así que `RecipesUiState.owns()` desaparece.
10. **Selector de Comidas:** cada fila muestra «Mía»/«Común» y, si la hay, la categoría («Común · Legumbres»); la búsqueda ignora tildes y mayúsculas (`prepareQuery`), por palabras, en título y categoría; sin resultados, «Sin resultados para tu búsqueda.» (cadenas `recipes_search_label` y `recipes_no_results` reutilizadas).
11. **`toRecipe()` (privada, sobre `DocumentSnapshot`) no tiene test JVM**: se prueban sus piezas puras (`parseRecipeSteps`, `parseRecipeCount`) y lo cubre la punta a punta.
12. **La documentación de datos** (`DATA_MODEL.md`, `RECIPES_ADMIN.md`) queda para el plan C (spec C4).

## Restricciones globales

- **Rutas:** `APP` = `app/src/main/java/com/dlunaunizar/bobitos/`; `UT` = `app/src/test/java/com/dlunaunizar/bobitos/`; `RES` = `app/src/main/res/values/strings.xml`; `T` = `tests/`.
- **Comandos de verificación**, en llamadas separadas y sin `sh -c`:
  - **GRADLE:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:assembleDebug :app:ktlintCheck :app:detekt :app:testDebugUnitTest`
  - **JVM (una clase):** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:testDebugUnitTest --tests "<clase completa>"`
  - **EMU** (reglas): `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && npm run test:emulators`
  - Si ktlint marca formato: `./gradlew :app:ktlintFormat` (con el mismo `JAVA_HOME`) y revisar el diff antes del commit.
- **Prohibido:** cualquier script contra `bobitos-dev` (`--project dev` o `bobitos-dev`), desplegar, distribuir betas, push. No se descarga nada. No se toca `scripts/` ni `data/catalog/` (son del plan C).
- **Commits con ficheros con nombre:** nunca `git add -A` ni `git add .`.
- **Límites (iguales en reglas, editor, repositorio e importador):** hasta **30 pasos**, cada uno **≤500** caracteres (recortados y sin vacíos al guardar); `servings` **1–50**; `totalMinutes` **1–1440**. En Kotlin, siempre vía `RecipeLimits` (Tarea 2).
- **Sin migración:** las recetas antiguas (pasos en la descripción, sin `steps`/`servings`/`totalMinutes`) se leen y se ven igual; editarlas añade `steps: []` y `servings`/`totalMinutes` a `null`.
- **Sin escalado** de cantidades por raciones (decisión 5).
- **Textos y comentarios en español**, con el estilo de los ficheros vecinos. Textos de la app en `RES`.
- **Tests Kotlin:** JVM puros (JUnit 4 + `kotlinx-coroutines-test`), dobles a mano; sin mockk ni Robolectric. **Orden TDD obligatorio:** test primero, verlo en rojo (copiar el fallo en el informe), después el código.
- **detekt:** `ReturnCount` máx. 4, `LongMethod` máx. 80 líneas, `TooManyFunctions` 25, `ThrowsCount` 2; extraer código antes que tocar el baseline. **No cambies la firma de `FirestoreRecipeRepository.validate(title, description, category)`**: está en el baseline (`ThrowsCount`) por su firma. **ktlint:** sin imports sin usar; línea ≤120.
- **Tests existentes:** solo se cambian los que fijan el comportamiento que esta spec sustituye, y cada tarea dice cuáles y cómo.

## Review Focus

1. **Deshacer tras borrar una receta con pasos, raciones y tiempo:** la receta recreada los conserva (hoy `restoreRecipe` solo copia título, descripción, categoría e ingredientes). → `RecipesViewModelTest` (Tarea 5).
2. **Receta antigua** (sin `steps`, `servings` ni `totalMinutes` en Firestore): el editor abre con pasos y números vacíos y sin error, y las reglas aceptan la edición que añade `steps: []` y `servings: null`. → EMU (Tarea 1) y `RecipeDraftTest` (Tarea 4).
3. **Web con las instrucciones en un único texto** (con saltos de línea, o de más de 500 caracteres): se trocea en pasos sin perder texto. → `RecipeMetaParserTest` y `JsonLdRecipeImporterTest` (Tarea 3).
4. **Raciones o tiempo escritos a mano** («dos», «1,5», «0», « 4 »): los no enteros o fuera de rango dan error en su campo y Guardar queda deshabilitado; « 4 » vale 4. → `RecipeDraftTest` y `RecipesViewModelTest` (Tarea 4).
5. **Pasos en blanco entre otros:** se descartan al guardar (la ficha numera 1, 2, 3 seguidos) y no cuentan para el máximo de 30. → `RecipeStepsTest`/`RecipesValidationTest` (Tarea 2) y `RecipesViewModelTest` (Tarea 4).

---

### Task 1: Reglas de `recipes`: pasos, raciones, tiempo y fuente del catálogo

**Ficheros:**
- Modificar: `firestore.rules` (funciones `validRecipeShape`, `validNewRecipe` y `validRecipeUpdate`, ~l.945-1006).
- Modificar: `T/firebase-emulators.test.mjs` (tests nuevos después del test «una receta guarda su lista de ingredientes pero rechaza un ingredients no-lista o demasiado largo», ~l.1296).

**Interfaces:**
- Consume: `validIngredientSource(s)` (`firestore.rules`, ~l.1111); helpers del test `recipeData`, `verifiedFirestore`, `RECIPE_ADMIN_UID`, `testEnvironment`, `assert`.
- Produce: documento `recipes` que admite `steps` (lista ≤30), `servings` (`null` o int 1–50), `totalMinutes` (`null` o int 1–1440) y `source` (`{provider: "bobitos", importedAt}`, nunca creado por un cliente). La edición admite `steps`, `servings` y `totalMinutes` (no `source`). Las Tareas 2–5 escriben estos campos; el plan C escribe `source`.

- [ ] **Step 1: Escribir los tests** en `T/firebase-emulators.test.mjs`, después del test «una receta guarda su lista de ingredientes…» y antes del comentario `// --- Catálogo común de ingredientes …`:

```js
test("una receta guarda pasos, raciones y tiempo, y su dueño los edita o los vacía", async () => {
  const chef = verifiedFirestore("recipe-steps");
  const ref = doc(chef, "recipes", "con-pasos");

  await assertSucceeds(setDoc(ref, recipeData("recipe-steps", {
    steps: ["Pon las lentejas en remojo.", "Sofríe la verdura."], servings: 4, totalMinutes: 60,
  })));
  await assertSucceeds(updateDoc(ref, {
    steps: ["Cuece todo junto."], servings: 2, totalMinutes: 45,
    updatedBy: "recipe-steps", updatedAt: serverTimestamp(),
  }));
  await assertSucceeds(updateDoc(ref, {
    steps: [], servings: null, totalMinutes: null, updatedBy: "recipe-steps", updatedAt: serverTimestamp(),
  }));
});

test("una receta antigua, sin pasos ni raciones, se edita añadiéndolos vacíos", async () => {
  const chef = verifiedFirestore("recipe-legacy");
  const ref = doc(chef, "recipes", "antigua");
  await assertSucceeds(setDoc(ref, recipeData("recipe-legacy")));

  await assertSucceeds(updateDoc(ref, {
    title: "Lentejas de la abuela", steps: [], servings: null, totalMinutes: null,
    updatedBy: "recipe-legacy", updatedAt: serverTimestamp(),
  }));
});

test("la receta rechaza más de 30 pasos y raciones o tiempo fuera de rango", async () => {
  const chef = verifiedFirestore("recipe-limits");
  const create = (id, overrides) => setDoc(doc(chef, "recipes", id), recipeData("recipe-limits", overrides));
  const steps = (count) => Array.from({ length: count }, (_, index) => `Paso ${index + 1}`);

  await assertSucceeds(create("maximos", { steps: steps(30), servings: 50, totalMinutes: 1440 }));
  await assertSucceeds(create("minimos", { steps: [], servings: 1, totalMinutes: 1 }));
  await assertFails(create("31-pasos", { steps: steps(31) }));
  await assertFails(create("pasos-texto", { steps: "Remojar y cocer" }));
  await assertFails(create("cero-raciones", { servings: 0 }));
  await assertFails(create("51-raciones", { servings: 51 }));
  await assertFails(create("raciones-decimales", { servings: 2.5 }));
  await assertFails(create("raciones-texto", { servings: "4" }));
  await assertFails(create("cero-minutos", { totalMinutes: 0 }));
  await assertFails(create("1441-minutos", { totalMinutes: 1441 }));
  await assertFails(updateDoc(doc(chef, "recipes", "minimos"), {
    servings: 51, updatedBy: "recipe-limits", updatedAt: serverTimestamp(),
  }));
  await assertFails(updateDoc(doc(chef, "recipes", "minimos"), {
    steps: steps(31), updatedBy: "recipe-limits", updatedAt: serverTimestamp(),
  }));
});

test("ningún cliente crea una receta con la fuente del catálogo", async () => {
  const source = { provider: "bobitos", importedAt: Timestamp.now() };
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  await assertFails(setDoc(doc(admin, "recipes", "falsa-global"),
    recipeData(RECIPE_ADMIN_UID, { visibility: "GLOBAL", source })));
  const user = verifiedFirestore("recipe-fake-source");
  await assertFails(setDoc(doc(user, "recipes", "falsa"), recipeData("recipe-fake-source", { source })));
});

test("el admin edita una receta común del catálogo y su fuente se conserva", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const timestamp = Timestamp.now();
    await setDoc(doc(context.firestore(), "recipes", "lentejas-catalogo"), {
      ...recipeData(RECIPE_ADMIN_UID, {
        visibility: "GLOBAL", title: "Lentejas estofadas", ingredients: [],
        steps: ["Remoja las lentejas."], servings: 4, totalMinutes: 60,
        source: { provider: "bobitos", importedAt: timestamp },
      }),
      createdByName: "Catálogo Bobitos",
      createdAt: timestamp,
      updatedAt: timestamp,
    });
  });
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  const ref = doc(admin, "recipes", "lentejas-catalogo");

  await assertSucceeds(updateDoc(ref, {
    title: "Lentejas estofadas (editada)", steps: ["Remoja las lentejas.", "Cuécelas."], servings: 6, totalMinutes: 75,
    updatedBy: RECIPE_ADMIN_UID, updatedAt: serverTimestamp(),
  }));
  await assertFails(updateDoc(ref, {
    source: { provider: "bobitos", importedAt: Timestamp.now() }, updatedBy: RECIPE_ADMIN_UID, updatedAt: serverTimestamp(),
  }));
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const stored = (await getDoc(doc(context.firestore(), "recipes", "lentejas-catalogo"))).data();
    assert.equal(stored.source.provider, "bobitos");
    assert.equal(stored.title, "Lentejas estofadas (editada)");
    assert.deepEqual(stored.steps, ["Remoja las lentejas.", "Cuécelas."]);
  });
  const user = verifiedFirestore("recipe-catalog-user");
  await assertFails(updateDoc(doc(user, "recipes", "lentejas-catalogo"), {
    title: "Mía", updatedBy: "recipe-catalog-user", updatedAt: serverTimestamp(),
  }));
});
```

- [ ] **Step 2: Ejecutar EMU y verlo en rojo.** Copia los fallos en el informe. Esperado: fallan el primero, el segundo, el tercero (`maximos`/`minimos`) y el quinto (`hasOnly` y `affectedKeys` no admiten los campos nuevos). «ningún cliente crea…» ya pasa: es una guarda.

- [ ] **Step 3: Cambiar las reglas** en `firestore.rules`:
  - En `validRecipeShape()`, la lista de `hasOnly` pasa a ser:

```
        && request.resource.data.keys().hasOnly([
          "ownerUid", "visibility", "title", "description", "category",
          "createdBy", "createdByName", "createdAt", "updatedBy", "updatedAt", "sourceRecipeId",
          "sourceUrl", "ingredients", "steps", "servings", "totalMinutes", "source"
        ])
```

  - En `validRecipeShape()`, justo después del bloque de `ingredients` (`… && request.resource.data.ingredients.size() <= 50))`), se añade:

```
        // Preparación: hasta 30 pasos (que cada texto mida ≤500 lo garantiza la app: las reglas no recorren listas).
        && (!request.resource.data.keys().hasAny(["steps"])
          || (request.resource.data.steps is list && request.resource.data.steps.size() <= 30))
        && (!request.resource.data.keys().hasAny(["servings"])
          || request.resource.data.servings == null
          || (request.resource.data.servings is int
            && request.resource.data.servings >= 1 && request.resource.data.servings <= 50))
        && (!request.resource.data.keys().hasAny(["totalMinutes"])
          || request.resource.data.totalMinutes == null
          || (request.resource.data.totalMinutes is int
            && request.resource.data.totalMinutes >= 1 && request.resource.data.totalMinutes <= 1440))
        // Recetas del catálogo: `source` lo escribe solo el importador (Admin SDK), con la forma de ingredientes.
        && (!request.resource.data.keys().hasAny(["source"]) || validIngredientSource(request.resource.data.source))
```

  - En `validNewRecipe()`, tras `validRecipeShape()`, añade `&& !request.resource.data.keys().hasAny(["source"])`.
  - En `validRecipeUpdate()`, el `affectedKeys().hasOnly([...])` pasa a `["title", "description", "category", "ingredients", "steps", "servings", "totalMinutes", "updatedBy", "updatedAt"]` (sin `source`: la app edita con `update()` y lo conserva).
  - `validIngredientSource` está definida más abajo en el mismo bloque; las funciones de reglas se resuelven al evaluar (rutinas ya la usa igual), así que no hace falta moverla.

- [ ] **Step 4: Ejecutar EMU y verlo en verde** (toda la suite).

- [ ] **Step 5: Commit**

```bash
git add firestore.rules tests/firebase-emulators.test.mjs
git commit -m "feat(reglas): recetas con pasos, raciones, tiempo y fuente del catálogo"
```

---

### Task 2: Modelo, validación y repositorio de pasos, raciones y tiempo

**Ficheros:**
- Modificar: `APP/core/model/Recipe.kt`, `APP/data/repository/RecipeRepository.kt`, `APP/data/repository/FirestoreRecipeRepository.kt`, `APP/feature/recipes/RecipesValidation.kt`, `APP/feature/recipes/RecipeUiMessage.kt`, `APP/feature/recipes/RecipesViewModel.kt` (solo el mapeo de fallos), `APP/feature/recipes/RecipesScreen.kt` (solo `stringResourceId`), `RES`.
- Crear: `APP/data/repository/RecipeFirestore.kt`, `UT/core/model/RecipeStepsTest.kt`, `UT/data/repository/RecipeFirestoreTest.kt`.
- Tests: `UT/feature/recipes/RecipesValidationTest.kt` (añadir), `UT/feature/recipes/RecipesViewModelTest.kt` (doble y un test), `UT/feature/meals/MealsViewModelTest.kt` (solo la firma del doble).

**Interfaces:**
- Consume: reglas de la Tarea 1.
- Produce (las usan las Tareas 3–6):
  - `Recipe.steps: List<String>? = null`, `Recipe.servings: Int? = null`, `Recipe.totalMinutes: Int? = null`;
  - `object RecipeLimits { const val MAX_STEPS = 30; const val MAX_STEP_LENGTH = 500; val SERVINGS = 1..50; val TOTAL_MINUTES = 1..1440 }` y `fun cleanRecipeSteps(steps: List<String>): List<String>` (`APP/core/model/Recipe.kt`);
  - `RecipeRepository.createRecipe(visibility, title, description, category, sourceRecipeId, ingredients: List<Ingredient> = emptyList(), sourceUrl: String? = null, steps: List<String> = emptyList(), servings: Int? = null, totalMinutes: Int? = null)` y `RecipeRepository.updateRecipe(recipeId, title, description, category, ingredients: List<Ingredient> = emptyList(), steps: List<String> = emptyList(), servings: Int? = null, totalMinutes: Int? = null)`;
  - `RecipeFailure.TooManySteps`, `StepTooLong`, `ServingsOutOfRange`, `TotalMinutesOutOfRange`;
  - `RecipeUiMessage.TooManySteps`, `StepTooLong`, `ServingsInvalid`, `TotalMinutesInvalid` (con cadenas);
  - `RecipesValidation.validate(title, description, category, steps: List<String> = emptyList(), servings: Int? = null, totalMinutes: Int? = null): RecipeUiMessage?`;
  - `internal fun recipeDetailsFailure(steps: List<String>, servings: Int?, totalMinutes: Int?): RecipeFailure?`, `internal fun parseRecipeSteps(raw: Any?): List<String>?`, `internal fun parseRecipeCount(raw: Any?, range: IntRange): Int?` (`APP/data/repository/RecipeFirestore.kt`);
  - en el doble `FakeRecipeRepository` de `RecipesViewModelTest`: `lastSteps`, `lastServings`, `lastTotalMinutes`, `updateCount`, `lastUpdatedId` (las Tareas 4 y 5 los usan).

- [ ] **Step 1: Tests nuevos (rojo).** Crear `UT/core/model/RecipeStepsTest.kt`:

```kotlin
package com.dlunaunizar.bobitos.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeStepsTest {
    @Test
    fun `clean steps are trimmed and blank ones dropped keeping the order`() {
        assertEquals(
            listOf("Remoja las lentejas.", "Sofríe la verdura."),
            cleanRecipeSteps(listOf("  Remoja las lentejas. ", "", "   ", "Sofríe la verdura.")),
        )
    }

    @Test
    fun `the limits match the firestore rules`() {
        assertEquals(30, RecipeLimits.MAX_STEPS)
        assertEquals(500, RecipeLimits.MAX_STEP_LENGTH)
        assertEquals(1..50, RecipeLimits.SERVINGS)
        assertEquals(1..1440, RecipeLimits.TOTAL_MINUTES)
    }
}
```

Crear `UT/data/repository/RecipeFirestoreTest.kt`:

```kotlin
package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.RecipeLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeFirestoreTest {
    @Test
    fun `steps are read as their non blank texts and a missing field stays null`() {
        assertEquals(listOf("Remoja.", "Cuece."), parseRecipeSteps(listOf("Remoja.", "  ", 3, null, "Cuece.")))
        assertEquals(emptyList<String>(), parseRecipeSteps(emptyList<String>()))
        assertNull(parseRecipeSteps(null))
        assertNull(parseRecipeSteps("Remoja y cuece"))
    }

    @Test
    fun `servings and minutes are read only as whole numbers inside their range`() {
        assertEquals(4, parseRecipeCount(4L, RecipeLimits.SERVINGS))
        assertEquals(50, parseRecipeCount(50, RecipeLimits.SERVINGS))
        assertEquals(1440, parseRecipeCount(1440L, RecipeLimits.TOTAL_MINUTES))
        assertNull(parseRecipeCount(0L, RecipeLimits.SERVINGS))
        assertNull(parseRecipeCount(51L, RecipeLimits.SERVINGS))
        assertNull(parseRecipeCount(1441L, RecipeLimits.TOTAL_MINUTES))
        assertNull(parseRecipeCount(Long.MAX_VALUE, RecipeLimits.TOTAL_MINUTES))
        assertNull(parseRecipeCount(4.5, RecipeLimits.SERVINGS))
        assertNull(parseRecipeCount("4", RecipeLimits.SERVINGS))
        assertNull(parseRecipeCount(null, RecipeLimits.SERVINGS))
    }

    @Test
    fun `details inside the limits pass and outside them report the failure`() {
        val longest = "a".repeat(RecipeLimits.MAX_STEP_LENGTH)
        assertNull(recipeDetailsFailure(List(30) { longest }, 50, 1440))
        assertNull(recipeDetailsFailure(emptyList(), null, null))
        assertEquals(RecipeFailure.TooManySteps, recipeDetailsFailure(List(31) { "Paso" }, null, null))
        assertEquals(RecipeFailure.StepTooLong, recipeDetailsFailure(listOf(longest + "a"), null, null))
        assertEquals(RecipeFailure.ServingsOutOfRange, recipeDetailsFailure(emptyList(), 0, null))
        assertEquals(RecipeFailure.ServingsOutOfRange, recipeDetailsFailure(emptyList(), 51, null))
        assertEquals(RecipeFailure.TotalMinutesOutOfRange, recipeDetailsFailure(emptyList(), null, 0))
        assertEquals(RecipeFailure.TotalMinutesOutOfRange, recipeDetailsFailure(emptyList(), null, 1441))
    }
}
```

Añadir a `UT/feature/recipes/RecipesValidationTest.kt`:

```kotlin
    @Test
    fun `steps, servings and minutes are validated after the text fields`() {
        assertEquals(
            RecipeUiMessage.TooManySteps,
            RecipesValidation.validate("Lentejas", null, null, steps = List(31) { "Paso" }),
        )
        assertEquals(
            RecipeUiMessage.StepTooLong,
            RecipesValidation.validate("Lentejas", null, null, steps = listOf("a".repeat(501))),
        )
        assertEquals(RecipeUiMessage.ServingsInvalid, RecipesValidation.validate("Lentejas", null, null, servings = 0))
        assertEquals(RecipeUiMessage.ServingsInvalid, RecipesValidation.validate("Lentejas", null, null, servings = 51))
        assertEquals(
            RecipeUiMessage.TotalMinutesInvalid,
            RecipesValidation.validate("Lentejas", null, null, totalMinutes = 1441),
        )
        assertEquals(RecipeUiMessage.TitleRequired, RecipesValidation.validate(" ", null, null, servings = 0))
    }

    @Test
    fun `blank steps do not count and the limits themselves are valid`() {
        assertNull(
            RecipesValidation.validate(
                title = "Lentejas",
                description = null,
                category = null,
                steps = List(30) { "a".repeat(500) } + listOf("", "   "),
                servings = 50,
                totalMinutes = 1440,
            ),
        )
        assertNull(
            RecipesValidation.validate(
                title = "Lentejas",
                description = null,
                category = null,
                steps = listOf("  ${"a".repeat(500)}  "),
                servings = 1,
                totalMinutes = 1,
            ),
        )
    }
```

Añadir a `UT/feature/recipes/RecipesViewModelTest.kt` (dentro de la clase):

```kotlin
    @Test
    fun `a repository limit failure shows its message`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.nextFailure = RecipeRepositoryException(RecipeFailure.ServingsOutOfRange)

        viewModel.createRecipe(RecipeVisibility.PRIVATE, "Lentejas", null, null)
        advanceUntilIdle()

        assertEquals(RecipeUiMessage.ServingsInvalid, viewModel.uiState.value.error)
    }
```

- [ ] **Step 2: Verlo en rojo.** JVM de `com.dlunaunizar.bobitos.core.model.RecipeStepsTest`. Esperado: FALLO de compilación (`cleanRecipeSteps`, `RecipeLimits`, `parseRecipeSteps`… no existen). Copia el fallo.

- [ ] **Step 3: Modelo y límites.** En `APP/core/model/Recipe.kt`, añade a `Recipe` tras `ingredients`:

```kotlin
    // Preparación paso a paso; null en las recetas antiguas, que llevan los pasos en la descripción.
    val steps: List<String>? = null,
    // Raciones (1–50) y tiempo total en minutos (1–1440); null si no se indican.
    val servings: Int? = null,
    val totalMinutes: Int? = null,
```

y al final del fichero:

```kotlin
// Límites de pasos, raciones y tiempo: los mismos en las reglas, el editor, el repositorio y el importador web.
object RecipeLimits {
    const val MAX_STEPS = 30
    const val MAX_STEP_LENGTH = 500
    val SERVINGS = 1..50
    val TOTAL_MINUTES = 1..1440
}

// Pasos tal como se guardan: sin espacios en los extremos y sin los vacíos.
fun cleanRecipeSteps(steps: List<String>): List<String> = steps.map(String::trim).filter(String::isNotEmpty)
```

- [ ] **Step 4: Funciones puras de Firestore.** Crear `APP/data/repository/RecipeFirestore.kt`:

```kotlin
package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.RecipeLimits

// Pasos, raciones y tiempo de una receta tal como se guardan: `steps` es una lista de textos (vacía si no
// hay) y `servings`/`totalMinutes`, enteros o null. El importador del catálogo (plan C) escribe lo mismo.

// El primer límite que no cumplen pasos (ya limpios), raciones o tiempo; null si todo cabe.
internal fun recipeDetailsFailure(steps: List<String>, servings: Int?, totalMinutes: Int?): RecipeFailure? = when {
    steps.size > RecipeLimits.MAX_STEPS -> RecipeFailure.TooManySteps
    steps.any { it.length > RecipeLimits.MAX_STEP_LENGTH } -> RecipeFailure.StepTooLong
    servings != null && servings !in RecipeLimits.SERVINGS -> RecipeFailure.ServingsOutOfRange
    totalMinutes != null && totalMinutes !in RecipeLimits.TOTAL_MINUTES -> RecipeFailure.TotalMinutesOutOfRange
    else -> null
}

// Retro-compat: campo ausente o que no es lista → null; lista → sus textos no vacíos.
internal fun parseRecipeSteps(raw: Any?): List<String>? =
    (raw as? List<*>)?.mapNotNull { (it as? String)?.takeIf(String::isNotBlank) }

// Raciones o minutos guardados: un entero dentro de [range]; cualquier otra cosa (ausente, decimal, texto,
// fuera de rango) → null. Firestore devuelve los enteros como Long.
internal fun parseRecipeCount(raw: Any?, range: IntRange): Int? {
    val value = when (raw) {
        is Long -> raw
        is Int -> raw.toLong()
        else -> return null
    }
    return value.takeIf { it >= range.first && it <= range.last }?.toInt()
}
```

- [ ] **Step 5: Repositorio.**
  - `APP/data/repository/RecipeRepository.kt`: `createRecipe` gana, tras `sourceUrl: String? = null`, los parámetros `steps: List<String> = emptyList(), servings: Int? = null, totalMinutes: Int? = null`; `updateRecipe` gana los mismos tras `ingredients: List<Ingredient> = emptyList()`. `RecipeFailure` gana, tras `CategoryTooLong`, `TooManySteps, StepTooLong, ServingsOutOfRange, TotalMinutesOutOfRange`.
  - `APP/data/repository/FirestoreRecipeRepository.kt`:
    - `createRecipe` y `updateRecipe` reciben `steps: List<String>, servings: Int?, totalMinutes: Int?` (al final, como en la interfaz). En ambos, tras `val values = validate(title, description, category)`, añade `val cleanSteps = checkedSteps(steps, servings, totalMinutes)` y en el mapa, tras `FIELD_INGREDIENTS to ingredients.toFirestore(),`:

```kotlin
                FIELD_STEPS to cleanSteps,
                FIELD_SERVINGS to servings,
                FIELD_TOTAL_MINUTES to totalMinutes,
```

    - Función privada nueva en la clase (no toques `validate`: su firma está en el baseline de detekt):

```kotlin
    // Pasos limpios para guardar; falla si pasos, raciones o tiempo no caben en los límites de las reglas.
    private fun checkedSteps(steps: List<String>, servings: Int?, totalMinutes: Int?): List<String> {
        val clean = cleanRecipeSteps(steps)
        recipeDetailsFailure(clean, servings, totalMinutes)?.let { throw RecipeRepositoryException(it) }
        return clean
    }
```

    - En el `companion object`: `const val FIELD_STEPS = "steps"`, `const val FIELD_SERVINGS = "servings"`, `const val FIELD_TOTAL_MINUTES = "totalMinutes"`.
    - En `DocumentSnapshot.toRecipe()`, tras `ingredients = parseIngredients(),`:

```kotlin
        steps = parseRecipeSteps(get("steps")),
        servings = parseRecipeCount(get("servings"), RecipeLimits.SERVINGS),
        totalMinutes = parseRecipeCount(get("totalMinutes"), RecipeLimits.TOTAL_MINUTES),
```

    - Imports: `com.dlunaunizar.bobitos.core.model.RecipeLimits` y `com.dlunaunizar.bobitos.core.model.cleanRecipeSteps`.

- [ ] **Step 6: Validación y mensajes.**
  - `APP/feature/recipes/RecipeUiMessage.kt`: tras `CategoryTooLong`, añade `TooManySteps, StepTooLong, ServingsInvalid, TotalMinutesInvalid` (uno por línea, como el resto).
  - `APP/feature/recipes/RecipesValidation.kt` queda así:

```kotlin
package com.dlunaunizar.bobitos.feature.recipes

import com.dlunaunizar.bobitos.core.model.RecipeLimits
import com.dlunaunizar.bobitos.core.model.cleanRecipeSteps

object RecipesValidation {
    const val MAX_TITLE_LENGTH = 120
    const val MAX_DESCRIPTION_LENGTH = 1000
    const val MAX_CATEGORY_LENGTH = 60

    // Los pasos se miden ya limpios (sin espacios en los extremos ni vacíos), como se guardan.
    fun validate(
        title: String,
        description: String?,
        category: String?,
        steps: List<String> = emptyList(),
        servings: Int? = null,
        totalMinutes: Int? = null,
    ): RecipeUiMessage? {
        val descriptionLength = description?.trim()?.length ?: 0
        val categoryLength = category?.trim()?.length ?: 0
        val cleanSteps = cleanRecipeSteps(steps)
        return when {
            title.isBlank() -> RecipeUiMessage.TitleRequired
            title.trim().length > MAX_TITLE_LENGTH -> RecipeUiMessage.TitleTooLong
            descriptionLength > MAX_DESCRIPTION_LENGTH -> RecipeUiMessage.DescriptionTooLong
            categoryLength > MAX_CATEGORY_LENGTH -> RecipeUiMessage.CategoryTooLong
            cleanSteps.size > RecipeLimits.MAX_STEPS -> RecipeUiMessage.TooManySteps
            cleanSteps.any { it.length > RecipeLimits.MAX_STEP_LENGTH } -> RecipeUiMessage.StepTooLong
            servings != null && servings !in RecipeLimits.SERVINGS -> RecipeUiMessage.ServingsInvalid
            totalMinutes != null && totalMinutes !in RecipeLimits.TOTAL_MINUTES -> RecipeUiMessage.TotalMinutesInvalid
            else -> null
        }
    }
}
```

  - `APP/feature/recipes/RecipesViewModel.kt`, en `RecipeFailure.toUiMessage()`, tras `RecipeFailure.CategoryTooLong -> …`:

```kotlin
    RecipeFailure.TooManySteps -> RecipeUiMessage.TooManySteps
    RecipeFailure.StepTooLong -> RecipeUiMessage.StepTooLong
    RecipeFailure.ServingsOutOfRange -> RecipeUiMessage.ServingsInvalid
    RecipeFailure.TotalMinutesOutOfRange -> RecipeUiMessage.TotalMinutesInvalid
```

  - `APP/feature/recipes/RecipesScreen.kt`, en `RecipeUiMessage.stringResourceId`, tras `CategoryTooLong`:

```kotlin
        RecipeUiMessage.TooManySteps -> R.string.recipes_error_too_many_steps
        RecipeUiMessage.StepTooLong -> R.string.recipes_error_step_too_long
        RecipeUiMessage.ServingsInvalid -> R.string.recipes_error_servings
        RecipeUiMessage.TotalMinutesInvalid -> R.string.recipes_error_total_minutes
```

  - `RES`, tras `recipes_error_category_too_long`:

```xml
    <string name="recipes_error_too_many_steps">Una receta admite hasta 30 pasos.</string>
    <string name="recipes_error_step_too_long">Cada paso admite hasta 500 caracteres.</string>
    <string name="recipes_error_servings">Las raciones van de 1 a 50.</string>
    <string name="recipes_error_total_minutes">El tiempo va de 1 a 1440 minutos.</string>
```

- [ ] **Step 7: Dobles de test.**
  - `UT/feature/recipes/RecipesViewModelTest.kt`, `FakeRecipeRepository`: añade

```kotlin
    var lastSteps: List<String> = emptyList()
    var lastServings: Int? = null
    var lastTotalMinutes: Int? = null
    var updateCount = 0
    var lastUpdatedId: String? = null
```

    `createRecipe` recibe además `steps: List<String>, servings: Int?, totalMinutes: Int?` y, tras `lastIngredients = ingredients`, guarda `lastSteps = steps`, `lastServings = servings`, `lastTotalMinutes = totalMinutes`. `updateRecipe` recibe además `steps: List<String>, servings: Int?, totalMinutes: Int?` y, tras `addGate?.await()`, hace:

```kotlin
        updateCount++
        lastUpdatedId = recipeId
        lastTitle = title
        lastIngredients = ingredients
        lastSteps = steps
        lastServings = servings
        lastTotalMinutes = totalMinutes
```

  - `UT/feature/meals/MealsViewModelTest.kt`, `FakeRecipeRepository`: solo añade `steps: List<String>, servings: Int?, totalMinutes: Int?` a las firmas de `createRecipe` y `updateRecipe` (siguen devolviendo `Unit`).

- [ ] **Step 8: Verlo en verde.** JVM de `com.dlunaunizar.bobitos.core.model.RecipeStepsTest`, `com.dlunaunizar.bobitos.data.repository.RecipeFirestoreTest`, `com.dlunaunizar.bobitos.feature.recipes.RecipesValidationTest` y `com.dlunaunizar.bobitos.feature.recipes.RecipesViewModelTest`; después GRADLE completo.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/core/model/Recipe.kt app/src/main/java/com/dlunaunizar/bobitos/data/repository/RecipeFirestore.kt app/src/main/java/com/dlunaunizar/bobitos/data/repository/RecipeRepository.kt app/src/main/java/com/dlunaunizar/bobitos/data/repository/FirestoreRecipeRepository.kt app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipesValidation.kt app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipeUiMessage.kt app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipesViewModel.kt app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipesScreen.kt app/src/main/res/values/strings.xml app/src/test/java/com/dlunaunizar/bobitos/core/model/RecipeStepsTest.kt app/src/test/java/com/dlunaunizar/bobitos/data/repository/RecipeFirestoreTest.kt app/src/test/java/com/dlunaunizar/bobitos/feature/recipes/RecipesValidationTest.kt app/src/test/java/com/dlunaunizar/bobitos/feature/recipes/RecipesViewModelTest.kt app/src/test/java/com/dlunaunizar/bobitos/feature/meals/MealsViewModelTest.kt
git commit -m "feat(recetas): pasos, raciones y tiempo en el modelo, la validación y el repositorio"
```

---

### Task 3: Importador web: pasos, raciones y tiempo

**Ficheros:**
- Modificar: `APP/data/recipeimport/ImportedRecipe.kt`, `APP/data/recipeimport/RecipeImporter.kt`.
- Crear: `APP/data/recipeimport/RecipeMetaParser.kt`, `UT/data/recipeimport/RecipeMetaParserTest.kt`.
- Tests: `UT/data/recipeimport/JsonLdRecipeImporterTest.kt` (cambia dos tests que fijaban «pasos en la descripción», amplía uno y añade dos).

**Interfaces:**
- Consume: `RecipeLimits`, `cleanRecipeSteps` (Tarea 2, `APP/core/model/Recipe.kt`).
- Produce (las usa la Tarea 4):
  - `ImportedRecipe(title, description, category, ingredients, sourceUrl, steps: List<String> = emptyList(), servings: Int? = null, totalMinutes: Int? = null)`;
  - `internal object RecipeMetaParser { fun servings(raw: Any?): Int?; fun totalMinutes(total: String?, prep: String?, cook: String?): Int?; fun isoMinutes(raw: String?): Long?; fun steps(raw: List<String>): List<String> }`.

- [ ] **Step 1: Test del parser (rojo).** Crear `UT/data/recipeimport/RecipeMetaParserTest.kt`:

```kotlin
package com.dlunaunizar.bobitos.data.recipeimport

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeMetaParserTest {
    @Test
    fun `recipeYield gives its first whole number between 1 and 50`() {
        assertEquals(4, RecipeMetaParser.servings(4))
        assertEquals(4, RecipeMetaParser.servings(4.0))
        assertEquals(4, RecipeMetaParser.servings("4 raciones"))
        assertEquals(6, RecipeMetaParser.servings("Para 6-8 personas"))
        assertEquals(2, RecipeMetaParser.servings(JSONArray(listOf("2", "2 raciones"))))
        assertEquals(3, RecipeMetaParser.servings(JSONArray(listOf("unas cuantas", "3 personas"))))
        assertNull(RecipeMetaParser.servings("unas cuantas"))
        assertNull(RecipeMetaParser.servings("100 galletas"))
        assertNull(RecipeMetaParser.servings("99999999999999999999 raciones"))
        assertNull(RecipeMetaParser.servings(0))
        assertNull(RecipeMetaParser.servings(null))
    }

    @Test
    fun `iso 8601 durations become minutes`() {
        assertEquals(90L, RecipeMetaParser.isoMinutes("PT1H30M"))
        assertEquals(45L, RecipeMetaParser.isoMinutes("PT45M"))
        assertEquals(60L, RecipeMetaParser.isoMinutes("P0DT1H"))
        assertEquals(1440L, RecipeMetaParser.isoMinutes("P1D"))
        assertEquals(45L, RecipeMetaParser.isoMinutes("P0Y0M0DT0H45M0.000S"))
        assertEquals(20L, RecipeMetaParser.isoMinutes(" pt20m "))
        assertEquals(1L, RecipeMetaParser.isoMinutes("PT90S"))
        assertNull(RecipeMetaParser.isoMinutes("P1Y"))
        assertNull(RecipeMetaParser.isoMinutes("P2W"))
        assertNull(RecipeMetaParser.isoMinutes("PT"))
        assertNull(RecipeMetaParser.isoMinutes("45 min"))
        assertNull(RecipeMetaParser.isoMinutes(""))
        assertNull(RecipeMetaParser.isoMinutes(null))
    }

    @Test
    fun `total time falls back to prep plus cook and stays within a day`() {
        assertEquals(90, RecipeMetaParser.totalMinutes("PT1H30M", "PT10M", "PT20M"))
        assertEquals(45, RecipeMetaParser.totalMinutes(null, "PT15M", "PT30M"))
        assertEquals(30, RecipeMetaParser.totalMinutes("", null, "PT30M"))
        assertEquals(30, RecipeMetaParser.totalMinutes("PT0M", "PT10M", "PT20M"))
        assertEquals(30, RecipeMetaParser.totalMinutes("45 minutos", "PT10M", "PT20M"))
        assertNull(RecipeMetaParser.totalMinutes("P2D", null, null))
        assertNull(RecipeMetaParser.totalMinutes("PT0M", null, null))
        assertNull(RecipeMetaParser.totalMinutes(null, null, null))
    }

    @Test
    fun `a long step is split by sentences without losing text`() {
        val sentence = "Remueve la mezcla con cuidado durante un buen rato hasta que espese."
        val long = List(10) { sentence }.joinToString(" ")

        val steps = RecipeMetaParser.steps(listOf(long))

        assertTrue(steps.size > 1)
        assertTrue(steps.all { it.length <= 500 })
        assertEquals(long, steps.joinToString(" "))
    }

    @Test
    fun `a sentence longer than a step is cut in pieces of 500`() {
        val word = "a".repeat(1200)

        val steps = RecipeMetaParser.steps(listOf(word))

        assertEquals(listOf(500, 500, 200), steps.map(String::length))
        assertEquals(word, steps.joinToString(""))
    }

    @Test
    fun `steps are cleaned and capped at 30`() {
        val steps = RecipeMetaParser.steps(listOf("  Uno ", "", "Dos") + List(40) { "Paso $it" })

        assertEquals(30, steps.size)
        assertEquals(listOf("Uno", "Dos"), steps.take(2))
    }
}
```

- [ ] **Step 2: Tests del importador.** En `UT/data/recipeimport/JsonLdRecipeImporterTest.kt`:
  - En `maps a plain Recipe node`, sustituye `assertEquals("Pelar las patatas\nBatir los huevos\nCuajar la tortilla", recipe.description)` por:

```kotlin
        assertEquals(null, recipe.description)
        assertEquals(listOf("Pelar las patatas", "Batir los huevos", "Cuajar la tortilla"), recipe.steps)
```

  - En `reads HowToStep instructions`, sustituye la aserción final por:

```kotlin
        assertEquals(listOf("Mezclar los secos", "Añadir los huevos"), importer.import("https://example.com/b").steps)
```

  - Sustituye el test `falls back to description when there are no instructions` por:

```kotlin
    @Test
    fun `keeps the description when there are no instructions`() = runTest {
        fetcher.html = pageWith(
            """
            { "@type": "Recipe", "name": "Café", "description": "Un café rápido" }
            """.trimIndent(),
        )

        val recipe = importer.import("https://example.com/cafe")

        assertEquals("Un café rápido", recipe.description)
        assertEquals(emptyList<String>(), recipe.steps)
        assertEquals(null, recipe.servings)
        assertEquals(null, recipe.totalMinutes)
    }
```

  - Añade:

```kotlin
    @Test
    fun `keeps the web description as the intro and reads steps, servings and time`() = runTest {
        fetcher.html = pageWith(
            """
            {
              "@type": "Recipe",
              "name": "Lentejas",
              "description": "Las de toda la vida.",
              "recipeYield": ["4", "4 raciones"],
              "totalTime": "PT1H",
              "recipeInstructions": [
                { "@type": "HowToSection", "name": "Preparación", "itemListElement": [
                  { "@type": "HowToStep", "text": "Remoja las lentejas." },
                  { "@type": "HowToStep", "text": "Cuécelas con la verdura." }
                ] }
              ]
            }
            """.trimIndent(),
        )

        val recipe = importer.import("https://example.com/lentejas")

        assertEquals("Las de toda la vida.", recipe.description)
        assertEquals(listOf("Remoja las lentejas.", "Cuécelas con la verdura."), recipe.steps)
        assertEquals(4, recipe.servings)
        assertEquals(60, recipe.totalMinutes)
    }

    @Test
    fun `a single instructions text is split by its line breaks`() = runTest {
        fetcher.html = pageWith(
            """
            {
              "@type": "Recipe",
              "name": "Gazpacho",
              "recipeInstructions": "Trocea las verduras.\nTritúralo todo.<br>Enfríalo en la nevera.",
              "prepTime": "PT15M",
              "cookTime": "PT0M",
              "recipeYield": "Para 6 personas"
            }
            """.trimIndent(),
        )

        val recipe = importer.import("https://example.com/gazpacho")

        assertEquals(listOf("Trocea las verduras.", "Tritúralo todo.", "Enfríalo en la nevera."), recipe.steps)
        assertEquals(6, recipe.servings)
        assertEquals(15, recipe.totalMinutes)
    }
```

  (En el texto Kotlin `"""…"""`, `\n` llega como `\n` al JSON, que lo convierte en un salto de línea real.)

- [ ] **Step 3: Verlo en rojo.** JVM de `com.dlunaunizar.bobitos.data.recipeimport.RecipeMetaParserTest`. Esperado: FALLO de compilación (`RecipeMetaParser`, `ImportedRecipe.steps` no existen). Copia el fallo.

- [ ] **Step 4: `ImportedRecipe`** gana, al final del constructor:

```kotlin
    // Pasos (≤30, cada uno ≤500), raciones (1–50) y tiempo total en minutos (1–1440), si la web los da.
    val steps: List<String> = emptyList(),
    val servings: Int? = null,
    val totalMinutes: Int? = null,
```

- [ ] **Step 5: Crear `APP/data/recipeimport/RecipeMetaParser.kt`:**

```kotlin
package com.dlunaunizar.bobitos.data.recipeimport

import com.dlunaunizar.bobitos.core.model.RecipeLimits
import com.dlunaunizar.bobitos.core.model.cleanRecipeSteps
import org.json.JSONArray

/**
 * Raciones, tiempo y pasos de una receta schema.org, ya dentro de los límites del modelo. Recibe los
 * valores crudos del JSON-LD y nunca lanza: lo que no entiende queda en null.
 */
internal object RecipeMetaParser {
    private const val MINUTES_PER_HOUR = 60
    private const val MINUTES_PER_DAY = 1440
    private const val SECONDS_PER_MINUTE = 60
    private val FIRST_NUMBER = Regex("\\d+")
    private val SENTENCE_END = Regex("(?<=[.!?])\\s+")

    // Años, meses, semanas, días, horas, minutos y segundos (estos, con decimales); cada parte es opcional.
    private val ISO_DURATION = Regex(
        "P(?:(\\d+)Y)?(?:(\\d+)M)?(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+(?:[.,]\\d+)?)S)?)?",
        RegexOption.IGNORE_CASE,
    )

    /** `recipeYield` (número, texto como «4 raciones» o «4-6», o array): su primer entero, si está en 1–50. */
    fun servings(raw: Any?): Int? = when (raw) {
        is Number -> raw.toLong().inRange(RecipeLimits.SERVINGS)
        is String -> FIRST_NUMBER.find(raw)?.value?.toLongOrNull()?.inRange(RecipeLimits.SERVINGS)
        is JSONArray -> (0 until raw.length()).firstNotNullOfOrNull { servings(raw.opt(it)) }
        else -> null
    }

    /**
     * Minutos de `totalTime`; si falta, no se entiende o vale 0, la suma de `prepTime` y `cookTime` (o el
     * que haya). Fuera de 1–1440 → null.
     */
    fun totalMinutes(total: String?, prep: String?, cook: String?): Int? {
        val minutes = isoMinutes(total)?.takeIf { it > 0 }
            ?: listOfNotNull(isoMinutes(prep), isoMinutes(cook)).takeIf { it.isNotEmpty() }?.sum()
        return minutes?.inRange(RecipeLimits.TOTAL_MINUTES)
    }

    /** Duración ISO 8601 («PT1H30M», «P0DT1H») en minutos; null si no lo es o trae años, meses o semanas. */
    fun isoMinutes(raw: String?): Long? {
        val parts = ISO_DURATION.matchEntire(raw?.trim().orEmpty())?.groupValues?.drop(1)
            ?.takeIf { groups -> groups.any(String::isNotEmpty) }
            ?.map { it.replace(',', '.').toDoubleOrNull() ?: 0.0 }
            ?: return null
        if (parts[0] + parts[1] + parts[2] > 0) return null
        val minutes = parts[3] * MINUTES_PER_DAY + parts[4] * MINUTES_PER_HOUR + parts[5] +
            parts[6] / SECONDS_PER_MINUTE
        return minutes.toLong()
    }

    /**
     * Pasos para el editor: limpios, sin perder texto (un paso de más de 500 caracteres se parte por frases
     * y, si una frase sola no cabe, en trozos de 500) y como mucho 30.
     */
    fun steps(raw: List<String>): List<String> =
        cleanRecipeSteps(raw).flatMap(::splitLongStep).take(RecipeLimits.MAX_STEPS)

    private fun splitLongStep(step: String): List<String> {
        if (step.length <= RecipeLimits.MAX_STEP_LENGTH) return listOf(step)
        val pieces = step.split(SENTENCE_END).flatMap { it.chunked(RecipeLimits.MAX_STEP_LENGTH) }
        val chunks = mutableListOf<String>()
        var current = ""
        for (piece in pieces) {
            current = when {
                current.isEmpty() -> piece
                current.length + 1 + piece.length <= RecipeLimits.MAX_STEP_LENGTH -> "$current $piece"
                else -> {
                    chunks += current
                    piece
                }
            }
        }
        chunks += current
        return chunks
    }

    private fun Long.inRange(range: IntRange): Int? = takeIf { it >= range.first && it <= range.last }?.toInt()
}
```

- [ ] **Step 6: `JsonLdRecipeImporter`** (`APP/data/recipeimport/RecipeImporter.kt`):
  - `toImportedRecipe` construye así (y desaparece `parseDescription`):

```kotlin
        return ImportedRecipe(
            title = title,
            description = cleanText(optString("description")).takeIf(String::isNotBlank)?.take(MAX_DESCRIPTION),
            category = parseCategory()?.take(MAX_CATEGORY),
            ingredients = parseIngredients(),
            sourceUrl = sourceUrl,
            steps = RecipeMetaParser.steps(parseInstructions()),
            servings = RecipeMetaParser.servings(opt("recipeYield")),
            totalMinutes = RecipeMetaParser.totalMinutes(
                optString("totalTime"),
                optString("prepTime"),
                optString("cookTime"),
            ),
        )
```

  - En `parseInstructions`, el caso `is String` pasa a:

```kotlin
        // Un único texto puede traer varios pasos separados por saltos de línea (o <br>, </p>, </li>).
        is String -> raw.replace(HTML_LINE_BREAK, "\n").lines().map { cleanText(it) }.filter(String::isNotBlank)
```

  - En el `companion object`: `val HTML_LINE_BREAK = Regex("<br\\s*/?>|</p>|</li>", RegexOption.IGNORE_CASE)`.
  - Actualiza el comentario de `parseInstructions`/`flattenInstructions` si mencionaba la descripción.

- [ ] **Step 7: Verlo en verde.** JVM de `com.dlunaunizar.bobitos.data.recipeimport.RecipeMetaParserTest` y `com.dlunaunizar.bobitos.data.recipeimport.JsonLdRecipeImporterTest`; después GRADLE completo.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/data/recipeimport/ImportedRecipe.kt app/src/main/java/com/dlunaunizar/bobitos/data/recipeimport/RecipeMetaParser.kt app/src/main/java/com/dlunaunizar/bobitos/data/recipeimport/RecipeImporter.kt app/src/test/java/com/dlunaunizar/bobitos/data/recipeimport/RecipeMetaParserTest.kt app/src/test/java/com/dlunaunizar/bobitos/data/recipeimport/JsonLdRecipeImporterTest.kt
git commit -m "feat(recetas): el importador web trae pasos, raciones y tiempo"
```

---

### Task 4: Ficha y editor con raciones, tiempo y pasos ordenables

**Ficheros:**
- Modificar: `APP/feature/recipes/RecipeDraft.kt`, `APP/feature/recipes/RecipesViewModel.kt`, `APP/feature/recipes/RecipeSheets.kt`, `APP/feature/recipes/RecipesScreen.kt` (solo el `onSave` de `RecipeSheetActions`), `RES`.
- Crear: `APP/feature/recipes/RecipeStepsEditor.kt`.
- Tests: `UT/feature/recipes/RecipeDraftTest.kt` y `UT/feature/recipes/RecipesViewModelTest.kt` (añadir).

**Interfaces:**
- Consume: `Recipe.steps/servings/totalMinutes`, `RecipeLimits`, `cleanRecipeSteps`, `RecipesValidation.validate(…, steps, servings, totalMinutes)`, `RecipeUiMessage.StepTooLong/ServingsInvalid/TotalMinutesInvalid`, `RecipeRepository.createRecipe/updateRecipe(…, steps, servings, totalMinutes)` y el doble con `lastSteps`, `lastServings`, `lastTotalMinutes`, `updateCount`, `lastUpdatedId` (Tarea 2); `ImportedRecipe.steps/servings/totalMinutes` (Tarea 3).
- Produce:
  - `RecipeDraft(title, description, category, global, ingredients, sourceUrl, steps: List<String> = emptyList(), servings: String = "", totalMinutes: String = "")`;
  - `internal fun String.toRecipeCount(): Int?`, `internal fun RecipeDraft.validate(): RecipeUiMessage?`, `RecipeDraft.withStep(index, text)`, `withoutStep(index)`, `withNewStep()`, `withStepMoved(index, offset)` (`RecipeDraft.kt`);
  - `RecipesViewModel.createRecipe(visibility, title, description, category, ingredients = emptyList(), sourceUrl = null, steps = emptyList(), servings = null, totalMinutes = null)`, `RecipesViewModel.updateRecipe(recipeId, title, description, category, ingredients = emptyList(), steps = emptyList(), servings = null, totalMinutes = null)` e `internal fun saveDraft(recipeId: String?, draft: RecipeDraft)`;
  - `RecipeSheetActions.onSave: (Recipe?, RecipeDraft) -> Unit`.

- [ ] **Step 1: Tests del borrador (rojo).** Añadir a `UT/feature/recipes/RecipeDraftTest.kt` (imports nuevos: ninguno más que los existentes):

```kotlin
    @Test
    fun editarCargaPasosRacionesYTiempo() {
        val draft = RecipeDraft.of(
            recipe().copy(steps = listOf("Remoja.", "Cuece."), servings = 4, totalMinutes = 40),
            null,
        )
        assertEquals(listOf("Remoja.", "Cuece."), draft.steps)
        assertEquals("4", draft.servings)
        assertEquals("40", draft.totalMinutes)
    }

    @Test
    fun unaRecetaAntiguaSinPasosAbreConLosCamposVaciosYSinError() {
        val draft = RecipeDraft.of(recipe(), null)
        assertEquals(emptyList<String>(), draft.steps)
        assertEquals("", draft.servings)
        assertEquals("", draft.totalMinutes)
        assertNull(draft.validate())
    }

    @Test
    fun unBorradorImportadoTraePasosRacionesYTiempo() {
        val imported = ImportedRecipe(
            title = "Gazpacho",
            description = null,
            category = null,
            ingredients = emptyList(),
            sourceUrl = "https://web.example/gazpacho",
            steps = listOf("Tritura."),
            servings = 6,
            totalMinutes = 15,
        )
        val draft = RecipeDraft.of(null, imported)
        assertEquals(listOf("Tritura."), draft.steps)
        assertEquals("6", draft.servings)
        assertEquals("15", draft.totalMinutes)
    }

    @Test
    fun losPasosSeSubenBajanCambianQuitanYAnadenHasta30() {
        val draft = RecipeDraft.of(null, null).copy(steps = listOf("A", "B", "C"))
        assertEquals(listOf("B", "A", "C"), draft.withStepMoved(1, -1).steps)
        assertEquals(listOf("A", "C", "B"), draft.withStepMoved(1, 1).steps)
        assertEquals(draft, draft.withStepMoved(0, -1))
        assertEquals(draft, draft.withStepMoved(2, 1))
        assertEquals(listOf("A", "C"), draft.withoutStep(1).steps)
        assertEquals(listOf("A", "Bis", "C"), draft.withStep(1, "Bis").steps)
        assertEquals(listOf("A", "B", "C", ""), draft.withNewStep().steps)
        val full = draft.copy(steps = List(30) { "Paso $it" })
        assertEquals(full, full.withNewStep())
    }

    @Test
    fun racionesYTiempoSoloAceptanEnterosEnRango() {
        assertNull("".toRecipeCount())
        assertNull("   ".toRecipeCount())
        assertEquals(4, " 4 ".toRecipeCount())
        assertEquals(0, "dos".toRecipeCount())
        assertEquals(0, "1,5".toRecipeCount())
        val draft = RecipeDraft.of(null, null).copy(title = "Lentejas")
        assertEquals(RecipeUiMessage.ServingsInvalid, draft.copy(servings = "dos").validate())
        assertEquals(RecipeUiMessage.ServingsInvalid, draft.copy(servings = "0").validate())
        assertEquals(RecipeUiMessage.TotalMinutesInvalid, draft.copy(totalMinutes = "1441").validate())
        assertEquals(RecipeUiMessage.TotalMinutesInvalid, draft.copy(totalMinutes = "1,5").validate())
        assertNull(draft.copy(servings = " 4 ", totalMinutes = "40").validate())
    }

    @Test
    fun losPasosEnBlancoNoCuentanYUnPasoLargoEsError() {
        val draft = RecipeDraft.of(null, null).copy(title = "Lentejas")
        assertNull(draft.copy(steps = listOf("", "  ", "Cuece.")).validate())
        assertEquals(RecipeUiMessage.StepTooLong, draft.copy(steps = listOf("a".repeat(501))).validate())
    }
```

Añadir a `UT/feature/recipes/RecipesViewModelTest.kt` (dentro de la clase):

```kotlin
    @Test
    fun `saving a new draft creates the recipe with clean steps, servings and minutes`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val draft = RecipeDraft.of(null, null).copy(
                title = " Lentejas ",
                ingredients = listOf(IngredientRow("Lentejas", "300", "g"), IngredientRow(" ")),
                steps = listOf("  Remoja. ", "", "Cuece."),
                servings = " 4 ",
                totalMinutes = "60",
            )

            viewModel.saveDraft(null, draft)
            advanceUntilIdle()

            assertEquals(1, repository.createCount)
            assertEquals(RecipeVisibility.PRIVATE, repository.lastVisibility)
            assertEquals("Lentejas", repository.lastTitle)
            assertEquals(listOf(Ingredient("Lentejas", "300", "g")), repository.lastIngredients)
            assertEquals(listOf("Remoja.", "Cuece."), repository.lastSteps)
            assertEquals(4, repository.lastServings)
            assertEquals(60, repository.lastTotalMinutes)
            assertEquals(RecipeUiMessage.RecipeSaved, viewModel.uiState.value.notice)
        }

    @Test
    fun `an admin saving a global draft publishes it`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.admin = true
        viewModel.observe()
        advanceUntilIdle()

        viewModel.saveDraft(null, RecipeDraft.of(null, null).copy(title = "Lentejas", global = true))
        advanceUntilIdle()

        assertEquals(RecipeVisibility.GLOBAL, repository.lastVisibility)
    }

    @Test
    fun `saving the draft of an existing recipe updates it and empty numbers clear the values`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.saveDraft("r1", RecipeDraft.of(null, null).copy(title = "Lentejas", steps = listOf("Cuece.")))
            advanceUntilIdle()

            assertEquals(0, repository.createCount)
            assertEquals(1, repository.updateCount)
            assertEquals("r1", repository.lastUpdatedId)
            assertEquals(listOf("Cuece."), repository.lastSteps)
            assertEquals(null, repository.lastServings)
            assertEquals(null, repository.lastTotalMinutes)
        }

    @Test
    fun `a draft with invalid servings never reaches the repository`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.saveDraft(null, RecipeDraft.of(null, null).copy(title = "Lentejas", servings = "dos"))
        advanceUntilIdle()

        assertEquals(0, repository.createCount)
        assertEquals(RecipeUiMessage.ServingsInvalid, viewModel.uiState.value.error)
        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.editorSave)
    }
```

- [ ] **Step 2: Verlo en rojo.** JVM de `com.dlunaunizar.bobitos.feature.recipes.RecipeDraftTest`. Esperado: FALLO de compilación (`steps`, `withStepMoved`, `toRecipeCount`, `saveDraft`… no existen). Copia el fallo.

- [ ] **Step 3: Borrador.** En `APP/feature/recipes/RecipeDraft.kt`:
  - El comentario de la clase añade la línea `// Los pasos van tal como se escriben (se limpian al guardar); raciones y tiempo, como el texto de su campo.` y el constructor gana, tras `sourceUrl`:

```kotlin
    val steps: List<String> = emptyList(),
    val servings: String = "",
    val totalMinutes: String = "",
```

  - En `of(...)`, tras `sourceUrl = imported?.sourceUrl,`:

```kotlin
            steps = (recipe?.steps ?: imported?.steps).orEmpty(),
            servings = (recipe?.servings ?: imported?.servings)?.toString().orEmpty(),
            totalMinutes = (recipe?.totalMinutes ?: imported?.totalMinutes)?.toString().orEmpty(),
```

  - Al final del fichero (imports nuevos: `com.dlunaunizar.bobitos.core.model.RecipeLimits`):

```kotlin
// Raciones o minutos escritos: vacío → null; un entero → ese número; cualquier otra cosa («dos», «1,5»)
// → 0, que la validación rechaza por estar fuera de rango.
internal fun String.toRecipeCount(): Int? = trim().takeIf(String::isNotEmpty)?.let { it.toIntOrNull() ?: 0 }

// Validación del borrador tal como se guardaría (pasos limpios; raciones y tiempo como números).
internal fun RecipeDraft.validate(): RecipeUiMessage? = RecipesValidation.validate(
    title = title,
    description = description,
    category = category,
    steps = steps,
    servings = servings.toRecipeCount(),
    totalMinutes = totalMinutes.toRecipeCount(),
)

// Edición de la lista de pasos: cada cambio devuelve otro borrador.
internal fun RecipeDraft.withStep(index: Int, text: String): RecipeDraft =
    copy(steps = steps.mapIndexed { i, step -> if (i == index) text else step })

internal fun RecipeDraft.withoutStep(index: Int): RecipeDraft = copy(steps = steps.filterIndexed { i, _ -> i != index })

// Añade un paso vacío al final, hasta el máximo.
internal fun RecipeDraft.withNewStep(): RecipeDraft =
    if (steps.size < RecipeLimits.MAX_STEPS) copy(steps = steps + "") else this

// Sube (offset -1) o baja (+1) el paso [index]; en los extremos no cambia nada.
internal fun RecipeDraft.withStepMoved(index: Int, offset: Int): RecipeDraft {
    val target = index + offset
    if (index !in steps.indices || target !in steps.indices) return this
    return copy(steps = steps.toMutableList().apply { add(target, removeAt(index)) })
}
```

- [ ] **Step 4: ViewModel.** En `APP/feature/recipes/RecipesViewModel.kt`:
  - `createRecipe` gana, tras `sourceUrl: String? = null`, `steps: List<String> = emptyList(), servings: Int? = null, totalMinutes: Int? = null`; valida con `validate(title, description, category, steps, servings, totalMinutes)` y pasa al repositorio, tras `sourceUrl = sourceUrl,`, `steps = cleanRecipeSteps(steps), servings = servings, totalMinutes = totalMinutes`.
  - `updateRecipe` gana, tras `ingredients`, los mismos tres parámetros con los mismos valores por defecto; valida igual y llama al repositorio con argumentos con nombre:

```kotlin
            repository.updateRecipe(
                recipeId = recipeId,
                title = title.trim(),
                description = description.normalized(),
                category = category.normalized(),
                ingredients = ingredients,
                steps = cleanRecipeSteps(steps),
                servings = servings,
                totalMinutes = totalMinutes,
            )
```

  - El `validate` privado pasa a:

```kotlin
    private fun validate(
        title: String,
        description: String?,
        category: String?,
        steps: List<String>,
        servings: Int?,
        totalMinutes: Int?,
    ): Boolean {
        val error = RecipesValidation.validate(title, description, category, steps, servings, totalMinutes) ?: return true
        showError(error, editor = true)
        return false
    }
```

  - Nueva función, tras `updateRecipe`:

```kotlin
    /** Guarda el borrador del editor: crea una receta si [recipeId] es null y, si no, edita esa. */
    internal fun saveDraft(recipeId: String?, draft: RecipeDraft) {
        val ingredients = draft.ingredients.toIngredients()
        val servings = draft.servings.toRecipeCount()
        val totalMinutes = draft.totalMinutes.toRecipeCount()
        if (recipeId == null) {
            createRecipe(
                visibility = if (draft.global) RecipeVisibility.GLOBAL else RecipeVisibility.PRIVATE,
                title = draft.title,
                description = draft.description,
                category = draft.category,
                ingredients = ingredients,
                sourceUrl = draft.sourceUrl,
                steps = draft.steps,
                servings = servings,
                totalMinutes = totalMinutes,
            )
        } else {
            updateRecipe(
                recipeId = recipeId,
                title = draft.title,
                description = draft.description,
                category = draft.category,
                ingredients = ingredients,
                steps = draft.steps,
                servings = servings,
                totalMinutes = totalMinutes,
            )
        }
    }
```

  - Import: `com.dlunaunizar.bobitos.core.model.cleanRecipeSteps`.

- [ ] **Step 5: Verlo en verde (JVM).** JVM de `com.dlunaunizar.bobitos.feature.recipes.RecipeDraftTest` y `com.dlunaunizar.bobitos.feature.recipes.RecipesViewModelTest`.

- [ ] **Step 6: Cadenas.** En `RES`, tras `recipes_ingredient_bullet`:

```xml
    <plurals name="recipes_servings">
        <item quantity="one">%1$d ración</item>
        <item quantity="other">%1$d raciones</item>
    </plurals>
    <string name="recipes_minutes">%1$d min</string>
    <string name="recipes_steps_section">Preparación</string>
    <string name="recipes_step_numbered">%1$d. %2$s</string>
    <string name="recipes_servings_label">Raciones</string>
    <string name="recipes_minutes_label">Tiempo (min)</string>
    <string name="recipes_step_label">Paso %1$d</string>
    <string name="recipes_step_add">Añadir paso</string>
    <string name="recipes_step_remove">Quitar paso</string>
    <string name="recipes_step_up">Subir paso</string>
    <string name="recipes_step_down">Bajar paso</string>
```

- [ ] **Step 7: Campos nuevos del editor.** Crear `APP/feature/recipes/RecipeStepsEditor.kt`:

```kotlin
package com.dlunaunizar.bobitos.feature.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.model.RecipeLimits

// Raciones y tiempo, en una fila, del editor de recetas.
@Composable
internal fun ServingsAndTimeFields(draft: RecipeDraft, validation: RecipeUiMessage?, onDraft: (RecipeDraft) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CountField(
            value = draft.servings,
            label = stringResource(R.string.recipes_servings_label),
            error = RecipeUiMessage.ServingsInvalid.takeIf { validation == it },
            onValueChange = { onDraft(draft.copy(servings = it)) },
            modifier = Modifier.weight(1f),
        )
        CountField(
            value = draft.totalMinutes,
            label = stringResource(R.string.recipes_minutes_label),
            error = RecipeUiMessage.TotalMinutesInvalid.takeIf { validation == it },
            onValueChange = { onDraft(draft.copy(totalMinutes = it)) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CountField(
    value: String,
    label: String,
    error: RecipeUiMessage?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = { error?.let { Text(stringResource(it.stringResourceId)) } },
        isError = error != null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier,
    )
}

// Pasos del editor: «Paso N» con texto de varias líneas, subir, bajar y quitar; «Añadir paso» hasta 30.
@Composable
internal fun StepsEditor(draft: RecipeDraft, validation: RecipeUiMessage?, onDraft: (RecipeDraft) -> Unit) {
    Text(
        text = stringResource(R.string.recipes_steps_section),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
    draft.steps.forEachIndexed { index, step ->
        // Solo se marca el paso que se pasa de largo.
        val tooLong = validation == RecipeUiMessage.StepTooLong && step.trim().length > RecipeLimits.MAX_STEP_LENGTH
        Column {
            OutlinedTextField(
                value = step,
                onValueChange = { onDraft(draft.withStep(index, it)) },
                label = { Text(stringResource(R.string.recipes_step_label, index + 1)) },
                supportingText = { if (tooLong) Text(stringResource(R.string.recipes_error_step_too_long)) },
                isError = tooLong,
                minLines = 2,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { onDraft(draft.withStepMoved(index, -1)) }, enabled = index > 0) {
                    Icon(
                        Icons.Rounded.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.recipes_step_up),
                    )
                }
                IconButton(
                    onClick = { onDraft(draft.withStepMoved(index, 1)) },
                    enabled = index < draft.steps.lastIndex,
                ) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.recipes_step_down),
                    )
                }
                IconButton(onClick = { onDraft(draft.withoutStep(index)) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.recipes_step_remove))
                }
            }
        }
    }
    if (draft.steps.size < RecipeLimits.MAX_STEPS) {
        TextButton(onClick = { onDraft(draft.withNewStep()) }) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Text(stringResource(R.string.recipes_step_add))
        }
    }
}
```

- [ ] **Step 8: Hoja de recetas** (`APP/feature/recipes/RecipeSheets.kt`):
  - `RecipeSheetActions.onSave` pasa a `val onSave: (Recipe?, RecipeDraft) -> Unit`; en `RecipeSheetsHost`, el editor recibe `onSave = { draft -> actions.onSave(editing, draft) }`.
  - `RecipeEditor`: el parámetro pasa a `onSave: (RecipeDraft) -> Unit`; `val validation = draft.validate()` (en lugar de `RecipesValidation.validate(draft.title, …)`); `onConfirm = { onSave(draft) }`. En el cuerpo del formulario, `ServingsAndTimeFields(draft = draft, validation = validation, onDraft = { draft = it })` va justo después de `RecipeTextFields(…)`, y `StepsEditor(draft = draft, validation = validation, onDraft = { draft = it })` justo después de `IngredientsEditor(…)`. El bloque `if (canChooseGlobal) { … }` se queda como está, entre los dos.

  - `RecipeDetailSheet`: el contenido pasa a

```kotlin
        RecipeSummary(recipe)
        // «Sin descripción.» solo si tampoco hay pasos: con pasos, la «Preparación» ya explica la receta.
        val description = recipe.description
        if (description != null) {
            Text(description)
        } else if (recipe.steps.isNullOrEmpty()) {
            Text(stringResource(R.string.recipes_no_description))
        }
        recipe.category?.let { Text(stringResource(R.string.recipes_category, it)) }
        IngredientsList(recipe.ingredients)
        StepsList(recipe.steps)
        Text(
            text = stringResource(R.string.recipes_by, recipe.createdByName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
```

  - Composables privados nuevos (tras `IngredientsList`):

```kotlin
// «4 raciones · 40 min» bajo el título; cada parte sale sola si falta la otra, y nada si faltan las dos.
@Composable
private fun RecipeSummary(recipe: Recipe) {
    val parts = listOfNotNull(
        recipe.servings?.let { pluralStringResource(R.plurals.recipes_servings, it, it) },
        recipe.totalMinutes?.let { stringResource(R.string.recipes_minutes, it) },
    )
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StepsList(steps: List<String>?) {
    if (steps.isNullOrEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.recipes_steps_section),
            style = MaterialTheme.typography.titleSmall,
        )
        steps.forEachIndexed { index, step ->
            Text(
                text = stringResource(R.string.recipes_step_numbered, index + 1, step),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
```

  - Imports: añade `androidx.compose.ui.res.pluralStringResource`; quita `RecipeVisibility` (ya no se usa aquí) y cualquier otro que quede sin usar.

- [ ] **Step 9: Pantalla.** En `APP/feature/recipes/RecipesScreen.kt`, el `onSave` de `RecipeSheetActions` pasa a:

```kotlin
                onSave = { recipe, draft -> viewModel.saveDraft(recipe?.id, draft) },
```

- [ ] **Step 10: Verlo en verde.** GRADLE completo.

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipeDraft.kt app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipesViewModel.kt app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipeSheets.kt app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipeStepsEditor.kt app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipesScreen.kt app/src/main/res/values/strings.xml app/src/test/java/com/dlunaunizar/bobitos/feature/recipes/RecipeDraftTest.kt app/src/test/java/com/dlunaunizar/bobitos/feature/recipes/RecipesViewModelTest.kt
git commit -m "feat(recetas): ficha y editor con raciones, tiempo y pasos ordenables"
```

---

### Task 5: Comunes siempre en «Comunes» y copias completas

**Ficheros:**
- Crear: `APP/feature/recipes/RecipeSections.kt`, `UT/feature/recipes/RecipeSectionsTest.kt`.
- Modificar: `APP/feature/recipes/RecipesViewModel.kt` (`observe`, `fork`, `restoreRecipe`), `APP/feature/recipes/RecipeSheets.kt` (detalle), `APP/feature/recipes/RecipesScreen.kt` (quitar `owns`).
- Tests: `UT/feature/recipes/RecipesViewModelTest.kt` (añadir).

**Interfaces:**
- Consume: `Recipe.steps/servings/totalMinutes`, `RecipeRepository.createRecipe(…, steps, servings, totalMinutes)` y el doble con `lastSteps`, `lastServings`, `lastTotalMinutes` (Tarea 2); `RecipeDetailSheet` y `RecipeSheetsHost` tal como los deja la Tarea 4.
- Produce: `internal fun List<Recipe>.personalOnly(): List<Recipe>`, `internal data class RecipeDetailActions(val canEdit: Boolean, val canFork: Boolean)` e `internal fun recipeDetailActions(recipe: Recipe, canWrite: Boolean, isAdmin: Boolean): RecipeDetailActions` (`RecipeSections.kt`).

- [ ] **Step 1: Tests (rojo).** Crear `UT/feature/recipes/RecipeSectionsTest.kt`:

```kotlin
package com.dlunaunizar.bobitos.feature.recipes

import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class RecipeSectionsTest {
    @Test
    fun `my recipes keep only the private ones`() {
        val mine = listOf(
            recipe("lentejas", RecipeVisibility.GLOBAL),
            recipe("p1", RecipeVisibility.PRIVATE),
            recipe("p2", RecipeVisibility.PRIVATE),
        )
        assertEquals(listOf("p1", "p2"), mine.personalOnly().map(Recipe::id))
    }

    @Test
    fun `a private recipe is edited and deleted but not forked`() {
        assertEquals(
            RecipeDetailActions(canEdit = true, canFork = false),
            recipeDetailActions(recipe("p1", RecipeVisibility.PRIVATE), canWrite = true, isAdmin = false),
        )
    }

    @Test
    fun `a common recipe is forked by anyone who can write and also edited by the admin`() {
        val common = recipe("lentejas", RecipeVisibility.GLOBAL)
        assertEquals(
            RecipeDetailActions(canEdit = false, canFork = true),
            recipeDetailActions(common, canWrite = true, isAdmin = false),
        )
        assertEquals(
            RecipeDetailActions(canEdit = true, canFork = true),
            recipeDetailActions(common, canWrite = true, isAdmin = true),
        )
    }

    @Test
    fun `without write access there are no actions`() {
        val none = RecipeDetailActions(canEdit = false, canFork = false)
        assertEquals(none, recipeDetailActions(recipe("p1", RecipeVisibility.PRIVATE), canWrite = false, isAdmin = true))
        assertEquals(
            none,
            recipeDetailActions(recipe("lentejas", RecipeVisibility.GLOBAL), canWrite = false, isAdmin = true),
        )
    }

    private fun recipe(id: String, visibility: RecipeVisibility) = Recipe(
        id = id,
        ownerUid = "u",
        visibility = visibility,
        title = id,
        description = null,
        category = null,
        createdBy = "u",
        createdByName = "U",
        createdAt = Instant.EPOCH,
        updatedBy = "u",
        updatedAt = Instant.EPOCH,
    )
}
```

Añadir a `UT/feature/recipes/RecipesViewModelTest.kt` (dentro de la clase):

```kotlin
    @Test
    fun `my recipes keep only the private ones while the common ones arrive through global`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.mineState.value = listOf(
                recipe("lentejas", RecipeVisibility.GLOBAL, "Lentejas"),
                recipe("p1", RecipeVisibility.PRIVATE, "Mis lentejas"),
            )
            repository.globalState.value = listOf(recipe("lentejas", RecipeVisibility.GLOBAL, "Lentejas"))

            viewModel.observe()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf("p1"), (state.mine as UiState.Content).value.map(Recipe::id))
            assertEquals(listOf("lentejas"), (state.global as UiState.Content).value.map(Recipe::id))
        }

    @Test
    fun `fork copies steps, servings and minutes`() = runTest(mainDispatcherRule.testDispatcher) {
        val common = recipe("lentejas", RecipeVisibility.GLOBAL, "Lentejas")
            .copy(steps = listOf("Remoja.", "Cuece."), servings = 4, totalMinutes = 60)

        viewModel.fork(common)
        advanceUntilIdle()

        assertEquals("lentejas", repository.lastSourceRecipeId)
        assertEquals(listOf("Remoja.", "Cuece."), repository.lastSteps)
        assertEquals(4, repository.lastServings)
        assertEquals(60, repository.lastTotalMinutes)
    }

    @Test
    fun `undoing a delete recreates the steps, servings and minutes`() = runTest(mainDispatcherRule.testDispatcher) {
        val deleted = recipe("r1", RecipeVisibility.PRIVATE, "Tortilla", listOf(Ingredient("Huevo", "4", "ud")))
            .copy(steps = listOf("Bate.", "Cuaja."), servings = 2, totalMinutes = 20)

        viewModel.restoreRecipe(deleted)
        advanceUntilIdle()

        assertEquals(listOf(Ingredient("Huevo", "4", "ud")), repository.lastIngredients)
        assertEquals(listOf("Bate.", "Cuaja."), repository.lastSteps)
        assertEquals(2, repository.lastServings)
        assertEquals(20, repository.lastTotalMinutes)
    }
```

- [ ] **Step 2: Verlo en rojo.** JVM de `com.dlunaunizar.bobitos.feature.recipes.RecipeSectionsTest`. Esperado: FALLO de compilación (`personalOnly`, `recipeDetailActions` no existen). Copia el fallo.

- [ ] **Step 3: Crear `APP/feature/recipes/RecipeSections.kt`:**

```kotlin
package com.dlunaunizar.bobitos.feature.recipes

import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility

// «Mis recetas» toma solo las privadas del usuario: las GLOBAL (también las que publica el admin del
// catálogo, que es su dueño) llegan siempre por la lista de comunes.
internal fun List<Recipe>.personalOnly(): List<Recipe> = filter { it.visibility == RecipeVisibility.PRIVATE }

// Acciones del detalle. Una privada siempre es del usuario (las reglas no dejan leer las ajenas): se edita y
// se borra. Una común se guarda como mía (también el admin) y el admin, además, la edita y la borra.
internal data class RecipeDetailActions(val canEdit: Boolean, val canFork: Boolean)

internal fun recipeDetailActions(recipe: Recipe, canWrite: Boolean, isAdmin: Boolean): RecipeDetailActions {
    val common = recipe.visibility == RecipeVisibility.GLOBAL
    return RecipeDetailActions(canEdit = canWrite && (!common || isAdmin), canFork = canWrite && common)
}
```

- [ ] **Step 4: ViewModel** (`APP/feature/recipes/RecipesViewModel.kt`):
  - En `observe()`, el `collect` de `myRecipes()` pasa a:

```kotlin
                // «Mis recetas» = solo privadas; las GLOBAL (también las del admin) llegan por `global`.
                .collect { list -> mutableUiState.update { it.copy(mine = UiState.Content(list.personalOnly())) } }
```

  - `fork` y `restoreRecipe` pasan al repositorio, además de lo que ya pasan, `steps = recipe.steps.orEmpty(), servings = recipe.servings, totalMinutes = recipe.totalMinutes` (en `fork`, con `source` en lugar de `recipe`).

- [ ] **Step 5: Detalle** (`APP/feature/recipes/RecipeSheets.kt`):
  - En `RecipeSheetsHost`, el detalle se abre con

```kotlin
        RecipeDetailSheet(
            recipe = recipe,
            detailActions = recipeDetailActions(recipe, canWrite, state.isAdmin),
            canAddToShopping = canWrite && canAddToShopping,
            actions = actions,
        )
```

  - `RecipeDetailSheet(recipe: Recipe, detailActions: RecipeDetailActions, canAddToShopping: Boolean, actions: RecipeSheetActions)` y sus acciones:

```kotlin
        actions = {
            if (canAddToShopping) {
                TextButton(onClick = { actions.onAddToShopping(recipe) }) {
                    Text(stringResource(R.string.recipes_add_to_shopping))
                }
            }
            if (detailActions.canEdit) {
                TextButton(onClick = { actions.onEdit(recipe) }) { Text(stringResource(R.string.recipes_edit)) }
                TextButton(onClick = { actions.onDelete(recipe) }) { Text(stringResource(R.string.recipes_delete)) }
            }
            if (detailActions.canFork) {
                TextButton(onClick = { actions.onFork(recipe) }) { Text(stringResource(R.string.recipes_fork)) }
            }
        },
```

  - En `APP/feature/recipes/RecipesScreen.kt`, borra `internal fun RecipesUiState.owns(...)` (ya no se usa).

- [ ] **Step 6: Verlo en verde.** JVM de `com.dlunaunizar.bobitos.feature.recipes.RecipeSectionsTest` y `com.dlunaunizar.bobitos.feature.recipes.RecipesViewModelTest`; después GRADLE completo.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipeSections.kt app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipesViewModel.kt app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipeSheets.kt app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipesScreen.kt app/src/test/java/com/dlunaunizar/bobitos/feature/recipes/RecipeSectionsTest.kt app/src/test/java/com/dlunaunizar/bobitos/feature/recipes/RecipesViewModelTest.kt
git commit -m "feat(recetas): las comunes siempre en «Comunes» y las copias conservan pasos, raciones y tiempo"
```

---

### Task 6: Selector de receta del planificador con buscador

**Ficheros:**
- Crear: `APP/feature/meals/RecipePicker.kt`, `UT/feature/meals/RecipePickerTest.kt`.
- Modificar: `APP/feature/meals/MealsScreen.kt` (quitar el `RecipePickerDialog` privado, ~l.713-733, y los imports que queden sin usar), `RES`.

**Interfaces:**
- Consume: `Recipe`, `RecipeVisibility` (`core/model`); `prepareQuery(query: String): SearchQuery` y `SearchQuery.matches(vararg fields: String?)` (`APP/core/common/TextSearch.kt`); cadenas `meals_recipe_picker_title`, `recipes_search_label`, `recipes_no_results`, `cancel`.
- Produce: `internal fun List<Recipe>.forPicker(query: String): List<Recipe>`, `@StringRes internal fun recipeOriginRes(recipe: Recipe): Int` e `@Composable internal fun RecipePickerDialog(recipes: List<Recipe>, onPick: (Recipe) -> Unit, onDismiss: () -> Unit)` (misma firma que el privado que sustituye: `MealEditor` lo llama igual).

- [ ] **Step 1: Test (rojo).** Crear `UT/feature/meals/RecipePickerTest.kt`:

```kotlin
package com.dlunaunizar.bobitos.feature.meals

import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class RecipePickerTest {
    private val recipes = listOf(
        recipe("g1", RecipeVisibility.GLOBAL, "Lentejas estofadas", "Legumbres"),
        recipe("p2", RecipeVisibility.PRIVATE, "Tortilla de patatas", "Huevos"),
        recipe("g2", RecipeVisibility.GLOBAL, "Crema de calabacín", "Sopas y cremas"),
        recipe("p1", RecipeVisibility.PRIVATE, "Arroz con verduras", null),
    )

    @Test
    fun `mine come first and then the common ones, each by title`() {
        assertEquals(listOf("p1", "p2", "g2", "g1"), recipes.forPicker("").map(Recipe::id))
        assertEquals(listOf("p1", "p2", "g2", "g1"), recipes.forPicker("   ").map(Recipe::id))
    }

    @Test
    fun `the search looks at title and category ignoring accents and case`() {
        assertEquals(listOf("g2"), recipes.forPicker("calabacin").map(Recipe::id))
        assertEquals(listOf("g1"), recipes.forPicker("LEGUMBRES").map(Recipe::id))
        assertEquals(listOf("p2"), recipes.forPicker("huevos tortilla").map(Recipe::id))
        assertEquals(emptyList<String>(), recipes.forPicker("paella").map(Recipe::id))
    }

    @Test
    fun `each row is labelled as mine or common`() {
        assertEquals(R.string.meals_recipe_origin_mine, recipeOriginRes(recipes[1]))
        assertEquals(R.string.meals_recipe_origin_common, recipeOriginRes(recipes[0]))
    }

    private fun recipe(id: String, visibility: RecipeVisibility, title: String, category: String?) = Recipe(
        id = id,
        ownerUid = "u",
        visibility = visibility,
        title = title,
        description = null,
        category = category,
        createdBy = "u",
        createdByName = "U",
        createdAt = Instant.EPOCH,
        updatedBy = "u",
        updatedAt = Instant.EPOCH,
    )
}
```

- [ ] **Step 2: Verlo en rojo.** JVM de `com.dlunaunizar.bobitos.feature.meals.RecipePickerTest`. Esperado: FALLO de compilación (`forPicker`, `recipeOriginRes`, `meals_recipe_origin_mine` no existen). Copia el fallo.

- [ ] **Step 3: Cadenas.** En `RES`, tras `meals_recipe_picker_title`:

```xml
    <string name="meals_recipe_origin_mine">Mía</string>
    <string name="meals_recipe_origin_common">Común</string>
```

- [ ] **Step 4: Crear `APP/feature/meals/RecipePicker.kt`:**

```kotlin
package com.dlunaunizar.bobitos.feature.meals

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.prepareQuery
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility

// Recetas del selector del planificador: primero las privadas (mías) y después las comunes, cada grupo por
// título. La búsqueda mira el título y la categoría, por palabras y sin tildes; en blanco, todas.
internal fun List<Recipe>.forPicker(query: String): List<Recipe> {
    val search = prepareQuery(query)
    return filter { search.matches(it.title, it.category) }
        .sortedWith(compareBy({ it.visibility != RecipeVisibility.PRIVATE }, { it.title.lowercase() }, Recipe::id))
}

// Etiqueta del origen de una receta en el selector: las privadas son del usuario; las GLOBAL, comunes
// (también las que publica el admin).
@StringRes
internal fun recipeOriginRes(recipe: Recipe): Int = when (recipe.visibility) {
    RecipeVisibility.PRIVATE -> R.string.meals_recipe_origin_mine
    RecipeVisibility.GLOBAL -> R.string.meals_recipe_origin_common
}

// Selector de receta del editor de comidas, con buscador. Cada fila: título y «Mía»/«Común» (· categoría).
@Composable
internal fun RecipePickerDialog(recipes: List<Recipe>, onPick: (Recipe) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(recipes, query) { recipes.forPicker(query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.meals_recipe_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.recipes_search_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (shown.isEmpty()) {
                    Text(
                        text = stringResource(R.string.recipes_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(shown, key = Recipe::id) { recipe ->
                            RecipePickerRow(recipe = recipe, onClick = { onPick(recipe) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun RecipePickerRow(recipe: Recipe, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(text = recipe.title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = listOfNotNull(stringResource(recipeOriginRes(recipe)), recipe.category).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

- [ ] **Step 5: `MealsScreen.kt`:** borra el `private fun RecipePickerDialog(...)` (la llamada de `MealEditor` no cambia: ahora resuelve al `internal` del mismo paquete) y quita los imports que queden sin usar (ktlint los marca; comprueba `AlertDialog`, `LazyColumn`, `items`, `heightIn` y `clickable`, que pueden seguir usándose en otras partes del fichero).

- [ ] **Step 6: Verlo en verde.** JVM de `com.dlunaunizar.bobitos.feature.meals.RecipePickerTest`; después GRADLE completo.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/meals/RecipePicker.kt app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealsScreen.kt app/src/main/res/values/strings.xml app/src/test/java/com/dlunaunizar/bobitos/feature/meals/RecipePickerTest.kt
git commit -m "feat(comidas): el selector de receta busca y separa las mías de las comunes"
```

---

## Ejecución (`delegating-plan-execution`, plan grande)

1. Rama `agent/receta-pasos` (desde `main`, 8b4eebe), con este plan como primer commit.
2. Las tareas 1 a 6, una tras otra, cada una con un `sonnet` que recibe su brief. `task-done` con la suite de cada tarea: EMU en la 1; JVM de las clases nombradas y GRADLE completo en la 2 a la 6.
3. **Revisión final** con `opus`, más los arreglos. **Re-revisión acotada** si algún arreglo toca `firestore.rules` (permisos) o la visibilidad de las recetas privadas.
4. **`simplify`:** un revisor `sonnet` con los 4 ángulos y un aplicador `sonnet` de la lista (a).
5. **`code-review`** en nivel `high`, más los arreglos.
6. **Cierre:** menores al backlog y decisión del usuario sobre la rama.

## Verificación de punta a punta (coordinador, al final)

- GRADLE y EMU en verde.
- **En el emulador:** `npm run emulators` (con `JAVA_HOME` de Android Studio) y `npm run seed:emulators`; sembrar además, con un script en el scratchpad (`withSecurityRulesDisabled`, como `seed-emulators.mjs`), una receta privada antigua del usuario sembrado **sin** `steps`, `servings` ni `totalMinutes` y con los pasos en la descripción. Para el admin: crear en el emulador de Auth un usuario con el uid de `RecipeAdmins` (`dWWH7eRhHEPopJf5BHPB3Dp6fry1`) y el correo verificado (Admin SDK con `FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099`). Instalar la app debug.
- **Recorrido**, con capturas en tema claro y oscuro:
  1. Usuario normal: crear «Lentejas» con 4 raciones, 60 min, dos ingredientes y tres pasos; subir el tercero al primer lugar; añadir un paso y dejarlo vacío; guardar. Ficha: «4 raciones · 60 min», entradilla, ingredientes y «Preparación» numerada 1–2–3 en el orden elegido (el vacío no sale).
  2. Editar: raciones «dos» → error en el campo y Guardar deshabilitado; «0», igual; vaciar raciones y tiempo y guardar → la ficha ya no muestra el resumen.
  3. La receta antigua sembrada se ve como antes (pasos en la descripción, sin resumen ni «Preparación»); editarla y guardar funciona.
  4. Importar desde una web con JSON-LD (si el emulador no tiene red, se salta y se dice): el editor trae entradilla, pasos, raciones y tiempo.
  5. Admin: publicar una común con pasos (interruptor «Publicar en el catálogo común»): sale en «Recetas comunes» y no en «Mis recetas»; su detalle ofrece Editar, Eliminar y «Guardar como mía».
  6. Usuario normal: esa común solo ofrece «Guardar como mía» (y «Añadir a la compra»); copiarla → aparece en «Mis recetas» con sus pasos, raciones y tiempo.
  7. Borrar la copia y «Deshacer»: vuelve con pasos, raciones y tiempo.
  8. Comidas → nueva comida → «Elegir de una receta»: buscador; primero las mías («Mía») y después las comunes («Común · …»); «lentejas» y «LEGUMBRES» filtran; sin resultados, «Sin resultados para tu búsqueda.»; elegir rellena el nombre de la comida.

## Despliegue

Ninguno en este plan: el despliegue es único tras el plan C (spec, «Orden y despliegue»), y en él van las reglas de la Tarea 1 (`firebase deploy --only firestore:rules --project dev`). **Hasta desplegar esas reglas no se distribuye ninguna beta desde esta rama ni desde `main`**: la app de este plan escribe `steps`, `servings` y `totalMinutes`, que las reglas actuales de `bobitos-dev` rechazan (crear o editar recetas fallaría).
