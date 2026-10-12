# Catálogo común de recetas — Plan de implementación (plan C de 3)

> **Para agentes:** se ejecuta con `delegating-plan-execution`. Es un **plan grande** (4 tareas), así que lleva los pasos 1 a 6, incluido `simplify`. Cada tarea la hace un subagente `sonnet`, en serie y en orden. La revisión final la hace `opus` (si falla, `fable`). No cambia `firestore.rules` (el plan B ya admite `steps`, `servings`, `totalMinutes` y `source`); si un arreglo posterior la tocara, lleva re-revisión acotada (permisos). **Parada del usuario tras la Tarea 2.** Los pasos usan casillas (`- [ ]`).

**Objetivo:** «Recetas comunes» trae 60 recetas de lista propia, en 11 categorías, con ingredientes del catálogo (nombre canónico y unidades de lista cerrada, para que «Añadir a la compra» sume) y pasos; se importan con un importador seguro; y «Deshacer» tras borrar una receta la recrea con su mismo id, así que el importador ya no duplica una común borrada y recuperada.

**Arquitectura:**
- **Datos:** `data/catalog/recipes.json` (lista propia) se valida contra `data/catalog/ingredients.json` con `ingredientKey` y se construye con la forma exacta que escribe la app (`FirestoreRecipeRepository`): ingredientes `{name, quantity, unit}` con `null` si faltan y el nombre canónico del catálogo, `steps` como lista, `servings`/`totalMinutes` enteros. `recipes-review.md` es la revisión del usuario.
- **Importador:** `import-recipes.mjs` sobre `import-core.mjs` (simulación por defecto, nunca borra, respeta lo editado en la app y las recetas de usuarios), sin `catalogMeta` (el Recetario lee en vivo). La parte común con rutinas (comunes GLOBAL del admin en una colección compartida con los usuarios) pasa a `import-core.mjs` y las rutinas la reutilizan sin cambiar su salida.
- **App:** `RecipeRepository.restoreRecipe(recipe)` recrea con `document(recipe.id).set(…)`; `RecipesViewModel.restoreRecipe` la usa en lugar de `createRecipe`.
- **Unidades:** la tabla `tests/fixtures/recipe-units.json` la comparten el validador (Node) y un test de `ShoppingQuantity` (JVM) que comprueba que todas se suman.

**Stack:** Node 22 (`node --test`, firebase-admin), reglas de Firestore (`@firebase/rules-unit-testing`), Kotlin + Hilt (JUnit 4).

**Spec:** `docs/superpowers/specs/2026-10-12-recetario-y-compra-design.md`, «Parte C» (C1–C5) y «Orden y despliegue» (decisiones 1, 3, 4 y 10). Los planes A (8b4eebe) y B (1c9eea1) ya están en `main`. Resuelve además el menor del backlog «Efecto visible (plan C)» de la sección `agent/receta-pasos` (`docs/superpowers/backlog.md`): «Deshacer» de una común del catálogo. Este plan **no despliega**: el despliegue único (reglas → ingredientes → recetas → beta 21) va después, cada paso confirmado por el usuario.

## Rulings de planificación (desviaciones conscientes de la spec)

1. **60 recetas** (la tabla de la spec suma 59): Verduras lleva 5 en lugar de ~4. Coste si mal: el usuario lo corrige en la parada de la Tarea 2.
2. **La lista del plan fija título, categoría, raciones, minutos, entradilla e ingredientes** (ya comprobados contra `ingredients.json`); **los pasos los redacta el implementador de la Tarea 2** con la guía de estilo de esa tarea. El usuario lo revisa todo en la parada.
3. **`description` (entradilla) obligatoria**, de 1 a 1000 caracteres (la spec solo dice «≤1000»): toda receta del catálogo lleva su entradilla.
4. **Concordancia de número en la unidad:** las abreviadas (`g`, `kg`, `ml`, `l`, `ud`) no cambian y no admiten plural («uds» no vale); las demás van en singular si la cantidad vale 1 o menos («1 diente», «1/2 cucharadita») y en plural si pasa de 1 («2 dientes», «1,5 cucharadas»). Es la misma regla que aplica `ShoppingQuantity` al sumar.
5. **Cantidad:** texto (nunca número JSON) con un entero, un decimal con coma o una fracción `a/b` (b ≥ 2), mayor que cero y sin ceros a la izquierda («0,5» sí, «05» y «1.5» no).
6. **El agua se rechaza** con su propio mensaje («El agua no se lista.»), aunque `Agua` existe en `ingredients.json`.
7. **`totalMinutes` no cuenta el remojo ni la nevera**; la entradilla lo avisa («Pon los garbanzos en remojo la noche anterior.», «Necesita tres horas de nevera.»).
8. **Ingredientes nuevos:** «Queso crema» (Lácteos y huevos, g) y «Ternera para guisar» (Carnes, g). Los recuentos «356» de la documentación pasan a «358».
9. **La sugerencia** de un ingrediente desconocido es el del catálogo que comparte más palabras de la clave (en proporción); si ninguno comparte, el más cercano letra a letra (Levenshtein sobre el nombre sin tildes); en empate, el primero de `ingredients.json`.
10. **«Deshacer» recrea con el mismo id** (`restoreRecipe`), y conserva visibilidad (si el usuario puede), contenido, `sourceRecipeId`, `sourceUrl` y el autor que se veía (`createdByName`, p. ej. «Catálogo Bobitos»). No lleva `source` (las reglas no dejan que un cliente lo escriba), así que una común del catálogo deshecha vuelve como «manual»: el importador la omite (no la duplica) y la documentación explica cómo devolverla al catálogo. De paso, las comidas que enlazaban la receta (`Meal.recipeId`) la recuperan.
11. **Núcleo compartido con rutinas:** `planGlobalCatalogImport` y `formatGlobalCatalogPlan` pasan a `import-core.mjs`; `planRoutineImport`/`formatRoutinePlan` delegan en ellas con la misma firma y la misma salida.
12. **La revisión** agrupa por categoría en el orden de `RECIPE_CATEGORIES` y, dentro de cada una, respeta el orden del fichero.

## Restricciones globales

- **Rutas:** `SC` = `scripts/catalog/`; `T` = `tests/`; `DC` = `data/catalog/`; `APP` = `app/src/main/java/com/dlunaunizar/bobitos/`; `UT` = `app/src/test/java/com/dlunaunizar/bobitos/`.
- **Comandos de verificación**, en llamadas separadas y sin `sh -c`:
  - **NODE:** `npm run test:scripts`
  - **IMP:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && npm run test:catalog-import`
  - **EMU** (reglas): `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && npm run test:emulators`
  - **GRADLE:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:assembleDebug :app:ktlintCheck :app:detekt :app:testDebugUnitTest`
  - **JVM (una clase):** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:testDebugUnitTest --tests "<clase completa>"`
  - Si ktlint marca formato: `./gradlew :app:ktlintFormat` (con el mismo `JAVA_HOME`) y revisar el diff antes del commit.
- **Prohibido:** cualquier script contra `bobitos-dev` (`--project dev` o `bobitos-dev`), `npm run catalog:fetch`, desplegar, distribuir betas, push. No se descarga nada. **No se toca `firestore.rules`** (si una tarea cree que hace falta, para y lo dice en el informe).
- **Commits con ficheros con nombre:** nunca `git add -A` ni `git add .`.
- **Ejercicios, ingredientes y rutinas sin cambios de comportamiento:** sus tests no se modifican (solo se añaden tests nuevos) y siguen en verde; `import-plan.mjs`, `ingredient-import-plan.mjs` y `routine-import-plan.mjs` mantienen exportaciones, firmas y salida.
- **Slug:** `slug()` de `SC/normalize.mjs` (idéntico al de la app). El id de cada receta es `slug(title)`.
- **Ingredientes:** se resuelven con `ingredientKey()` de `SC/ingredients.mjs` contra `DC/ingredients.json` y se escriben con el **nombre canónico** del catálogo.
- **Documento común `recipes/{slug}`** (lo escribe el importador): `visibility: "GLOBAL"`, `ownerUid` = `createdBy` = `updatedBy` = `CATALOG_ADMIN_UID`, `createdByName: "Catálogo Bobitos"` (`CATALOG_AUTHOR_NAME`), `title`, `description`, `category`, `servings`, `totalMinutes`, `ingredients`, `steps`, `createdAt` (solo al crear), `updatedAt`, `source: {provider: "bobitos", importedAt}`. Nunca `sourceRecipeId` ni `sourceUrl`.
- **Forma idéntica a la app** (`APP/data/repository/FirestoreRecipeRepository.kt`): cada ingrediente es `{name, quantity, unit}` con las tres claves siempre, `quantity`/`unit` **texto o `null`** (la app lee `quantity as? String`: un número se perdería en silencio); `steps` lista de textos; `servings`/`totalMinutes` enteros.
- **Límites** (los de las reglas y la app): título 1–120, entradilla 1–1000, 1–50 ingredientes, 1–30 pasos de 1–500 caracteres, `servings` 1–50, `totalMinutes` 1–1440.
- **Textos y comentarios en español**, con el estilo de los ficheros vecinos.
- **Tests Kotlin:** JVM puros (JUnit 4 + `kotlinx-coroutines-test`), dobles a mano; sin mockk ni Robolectric. **Orden TDD obligatorio:** test primero, verlo en rojo (copiar el fallo en el informe), después el código.
- **detekt:** `LongMethod` máx. 80 líneas, `TooManyFunctions` 25; extraer código antes que tocar el baseline. **No cambies la firma de `FirestoreRecipeRepository.validate(title, description, category)`** (está en el baseline por su firma). **ktlint:** sin imports sin usar; línea ≤120.

## Review Focus

1. **Cantidades que la app entiende:** una cantidad escrita como número JSON (`300`) se perdería en silencio en la app y en la Compra. El validador exige texto y la construcción deja `{name, quantity, unit}` con texto o `null`. → NODE (Tarea 1: `quantity: 2` se rechaza; las construidas son texto) e IMP (Tarea 3: el documento leído de Firestore tiene `"300"` y `null`).
2. **«Añadir a la compra» dos veces suma con todas las unidades del catálogo** («1 diente» + «2 dientes» = «3 dientes»; «1/2 cucharadita» × 2 = «1 cucharadita»). → tabla compartida `T/fixtures/recipe-units.json`: NODE (Tarea 1) comprueba que es la del validador; JVM (Tarea 4) que `ShoppingQuantity.sum` suma cada una y concuerda en número.
3. **«Deshacer» de una común del catálogo no la duplica:** vuelve con el mismo id y el importador la omite como manual. → JVM (Tarea 4: mismo id, sin `createRecipe`), EMU (Tarea 4: las reglas dejan recrearla con el mismo id sin `source`) e IMP (Tarea 3: no se crea otra).
4. **Reimportar no pisa nada:** sin cambios no escribe; una común editada en la app, una receta de usuario con el mismo id y una común manual se omiten; la simulación no escribe. → IMP (Tarea 3).
5. **El admin puede editar en la app una receta importada** (el importador no escribe claves fuera del contrato de las reglas), pero no su `source`, y cualquier usuario la lee. → EMU (Tarea 4) con el documento real de `recipeToFirestoreDoc`.

---

### Task 1: Validación, construcción y revisión de recetas (Node)

**Ficheros:**
- Crear: `SC/recipes.mjs`, `SC/build-recipes.mjs`, `T/catalog-recipes.test.mjs`, `T/fixtures/recipe-units.json`.
- Modificar: `package.json` (script `catalog:recipes` y el test nuevo en `test:scripts`).

**Interfaces:**
- Consume: `ingredientKey(name)` (`SC/ingredients.mjs`); `foldText`, `groupBy`, `slug` (`SC/normalize.mjs`).
- Produce (lo usan las Tareas 2 y 3):
  - `RECIPE_CATEGORIES: string[]` (11, en el orden de la revisión); `RECIPE_UNITS: { invariable: string[], counted: {singular, plural}[] }`; `RECIPE_LIMITS`.
  - `indexIngredients(ingredientCatalog) → Map<clave, nombreCanónico>` (recibe el contenido de `ingredients.json`).
  - `suggestIngredient(name, index) → string`.
  - `validateRecipeCatalog(catalog, index) → string[]` (problemas; vacío si es válido).
  - `buildRecipes(catalog, index) → [{id, title, description, category, servings, totalMinutes, ingredients: [{name, quantity, unit}], steps}]` (exige catálogo válido).
  - `renderRecipeReview(recipes) → string` (Markdown).
  - `npm run catalog:recipes`: valida `DC/recipes.json` y escribe `DC/recipes-review.md`.

- [ ] **Step 1: Crear la tabla compartida `T/fixtures/recipe-units.json`** (JSON con 2 espacios y salto de línea final):

```json
{
  "invariable": ["g", "kg", "ml", "l", "ud"],
  "counted": [
    { "singular": "cucharada", "plural": "cucharadas" },
    { "singular": "cucharadita", "plural": "cucharaditas" },
    { "singular": "pizca", "plural": "pizcas" },
    { "singular": "diente", "plural": "dientes" },
    { "singular": "rebanada", "plural": "rebanadas" },
    { "singular": "hoja", "plural": "hojas" },
    { "singular": "ramita", "plural": "ramitas" }
  ]
}
```

- [ ] **Step 2: Escribir el test `T/catalog-recipes.test.mjs`:**

```js
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import {
  RECIPE_CATEGORIES, RECIPE_LIMITS, RECIPE_UNITS, buildRecipes, indexIngredients, renderRecipeReview,
  suggestIngredient, validateRecipeCatalog,
} from "../scripts/catalog/recipes.mjs";

const units = JSON.parse(await readFile(new URL("./fixtures/recipe-units.json", import.meta.url), "utf8"));
const index = indexIngredients({
  ingredients: ["Lentejas", "Lentejas pardinas", "Lentejas cocidas", "Tomate", "Ajo", "Sal", "Agua", "Huevos", "Pimentón dulce"]
    .map((name) => ({ name })),
});
const recipe = (over = {}) => ({
  title: "Lentejas estofadas",
  category: "Legumbres",
  description: "Las de toda la vida.",
  servings: 4,
  totalMinutes: 60,
  ingredients: [
    { name: "Lentejas pardinas", quantity: "300", unit: "g" },
    { name: "Ajos", quantity: "2", unit: "dientes" },
    { name: "Sal" },
  ],
  steps: ["Pon las lentejas a cocer.", "Sirve."],
  ...over,
});
const cat = (...recipes) => ({ schemaVersion: 1, recipes });
const problems = (...recipes) => validateRecipeCatalog(cat(...recipes), index);
const withIngredient = (i) => problems(recipe({ ingredients: [i] }));
const ajo = (quantity, unit) => withIngredient({ name: "Ajo", quantity, unit });

test("la tabla de unidades es la compartida con la app", () => {
  assert.deepEqual(RECIPE_UNITS, units);
});

test("una receta correcta no da problemas y se construye con el id y los nombres del catálogo", () => {
  assert.deepEqual(problems(recipe()), []);
  const [built] = buildRecipes(cat(recipe()), index);
  assert.deepEqual(built, {
    id: "lentejas-estofadas",
    title: "Lentejas estofadas",
    description: "Las de toda la vida.",
    category: "Legumbres",
    servings: 4,
    totalMinutes: 60,
    ingredients: [
      { name: "Lentejas pardinas", quantity: "300", unit: "g" },
      { name: "Ajo", quantity: "2", unit: "dientes" },
      { name: "Sal", quantity: null, unit: null },
    ],
    steps: ["Pon las lentejas a cocer.", "Sirve."],
  });
});

test("la cantidad se escribe como texto, nunca como número", () => {
  assert.match(ajo(2, "dientes").join("\n"), /texto/);
  const [built] = buildRecipes(cat(recipe()), index);
  assert.ok(built.ingredients.every((i) => i.quantity === null || typeof i.quantity === "string"));
});

test("rechaza un ingrediente que no está en el catálogo y sugiere el más parecido", () => {
  assert.match(
    withIngredient({ name: "Lentejas rojas", quantity: "300", unit: "g" }).join("\n"),
    /«Lentejas rojas» no está en ingredients\.json \(¿«Lentejas»\?\)/,
  );
  assert.equal(suggestIngredient("Pimentón picante", index), "Pimentón dulce");
  assert.equal(suggestIngredient("Ajoo", index), "Ajo");
});

test("el agua no se lista", () => {
  assert.match(withIngredient({ name: "Agua", quantity: "1", unit: "l" }).join("\n"), /El agua no se lista/);
});

test("rechaza un ingrediente repetido por clave tolerante", () => {
  const twice = recipe({ ingredients: [{ name: "Tomate", quantity: "2", unit: "ud" }, { name: "Tomates", quantity: "1", unit: "ud" }] });
  assert.match(problems(twice).join("\n"), /repetido/);
});

test("la unidad es de la lista cerrada, obligatoria con cantidad y concordada en número", () => {
  for (const ok of [["1", "diente"], ["2", "dientes"], ["1/2", "cucharadita"], ["1,5", "cucharadas"], ["1,5", "kg"], ["2", "ud"], ["1", "pizca"]]) {
    assert.deepEqual(ajo(...ok), [], ok.join(" "));
  }
  for (const bad of [["1", "vaso"], ["1", "dientes"], ["2", "diente"], ["1/2", "cucharaditas"], ["2", "uds"]]) {
    assert.notDeepEqual(ajo(...bad), [], bad.join(" "));
  }
  assert.match(withIngredient({ name: "Ajo", quantity: "300" }).join("\n"), /necesita unidad/);
  assert.match(withIngredient({ name: "Ajo", unit: "g" }).join("\n"), /unidad sin cantidad/);
});

test("la cantidad es un entero, un decimal con coma o una fracción, mayor que cero", () => {
  for (const ok of ["3", "0,5", "3/4", "3/2"]) assert.deepEqual(ajo(ok, "g"), [], ok);
  for (const bad of ["0", "1.5", "1/0", "1/1", "0/2", "dos", "1 1/2", "-1", "05", ""]) assert.notDeepEqual(ajo(bad, "g"), [], bad);
});

test("respeta categorías y límites", () => {
  const L = RECIPE_LIMITS;
  const steps = (n) => Array.from({ length: n }, (_, i) => `Paso ${i + 1}.`);
  const many = Array.from({ length: L.ingredients + 1 }, () => ({ name: "Sal" }));
  for (const over of [
    { category: "Tapas" },
    { title: "x".repeat(L.title + 1) },
    { description: "x".repeat(L.description + 1) },
    { description: "" },
    { servings: 0 }, { servings: L.servings + 1 }, { servings: 2.5 }, { servings: "4" },
    { totalMinutes: 0 }, { totalMinutes: L.minutes + 1 },
    { ingredients: [] }, { ingredients: many },
    { steps: [] }, { steps: steps(L.steps + 1) }, { steps: ["x".repeat(L.step + 1)] },
    { steps: ["Pon  las lentejas."] }, { steps: [" Pon las lentejas."] }, { steps: ["Pon las\nlentejas."] },
  ]) {
    assert.notDeepEqual(problems(recipe(over)), [], JSON.stringify(over).slice(0, 60));
  }
  const { description: _d, ...withoutDescription } = recipe();
  assert.match(problems(withoutDescription).join("\n"), /Faltan claves: description/);
  assert.deepEqual(problems(recipe({ steps: steps(L.steps), servings: L.servings, totalMinutes: L.minutes })), []);
});

test("rechaza claves desconocidas, títulos mal escritos y títulos repetidos", () => {
  assert.notDeepEqual(problems(recipe({ foto: "x" })), []);
  assert.notDeepEqual(withIngredient({ name: "Sal", nota: "x" }), []);
  assert.notDeepEqual(problems(recipe({ title: "lentejas" })), []);
  assert.notDeepEqual(problems(recipe({ title: "Lentejas  estofadas" })), []);
  assert.match(problems(recipe(), recipe({ title: "Léntejas estofadas" })).join("\n"), /id repetido: lentejas-estofadas/);
});

test("rechaza un catálogo sin schemaVersion 1 o sin recetas", () => {
  assert.notDeepEqual(validateRecipeCatalog({ schemaVersion: 2, recipes: [recipe()] }, index), []);
  assert.notDeepEqual(validateRecipeCatalog({ schemaVersion: 1, recipes: [] }, index), []);
});

test("la revisión enseña las recetas por categorías, con raciones, tiempo, ingredientes y pasos", () => {
  const tortilla = recipe({
    title: "Tortilla", category: "Huevos", servings: 1, totalMinutes: 20,
    ingredients: [{ name: "Huevos", quantity: "2", unit: "ud" }], steps: ["Bate.", "Cuaja."],
  });
  const md = renderRecipeReview(buildRecipes(cat(tortilla, recipe()), index));
  assert.match(md, /^# Revisión del catálogo de recetas/);
  assert.match(md, /Total: 2 recetas\./);
  assert.match(md, /\| Legumbres \| 1 \|/);
  assert.match(md, /\| Pasta \| 0 \|/);
  assert.doesNotMatch(md, /## Pasta/);
  assert.ok(md.indexOf("## Legumbres") < md.indexOf("## Huevos"), "orden de RECIPE_CATEGORIES");
  assert.match(md, /### Lentejas estofadas\n\n`lentejas-estofadas` · 4 raciones · 60 min\n\nLas de toda la vida\./);
  assert.match(md, /- Ajo — 2 dientes/);
  assert.match(md, /- Sal — al gusto/);
  assert.match(md, /\*\*Preparación\*\*\n\n1\. Pon las lentejas a cocer\.\n2\. Sirve\./);
  assert.match(md, /`tortilla` · 1 ración · 20 min/);
  assert.equal(RECIPE_CATEGORIES.length, 11);
});
```

- [ ] **Step 3: Añadir el test a `package.json`** (al final de `test:scripts`, separado por un espacio): `tests/catalog-recipes.test.mjs`. Añadir también, después de `"catalog:routines"`, el script `"catalog:recipes": "node scripts/catalog/build-recipes.mjs"`.

- [ ] **Step 4: Ejecutar NODE y verlo en rojo** (falta `SC/recipes.mjs`). Copia el fallo en el informe.

- [ ] **Step 5: Escribir `SC/recipes.mjs`:**

```js
// Catálogo común de recetas de Bobitos (lista propia): validación contra el catálogo de ingredientes,
// construcción de los documentos y revisión. Funciones puras y sin dependencias de red.
import { ingredientKey } from "./ingredients.mjs";
import { foldText, groupBy, slug } from "./normalize.mjs";

// Categorías por tipo de plato, en el orden de la revisión.
export const RECIPE_CATEGORIES = [
  "Legumbres", "Arroces", "Pasta", "Carnes", "Pescados", "Verduras", "Ensaladas", "Huevos", "Sopas y cremas",
  "Desayunos y meriendas", "Postres",
];

// Unidades de la lista cerrada, para que «Añadir a la compra» pueda sumar cantidades (ShoppingQuantity en la
// app). Las abreviadas no cambian; las demás van en singular hasta 1 y en plural por encima, como al sumar.
// Es la misma tabla que `tests/fixtures/recipe-units.json`, que también lee el test de la app.
export const RECIPE_UNITS = {
  invariable: ["g", "kg", "ml", "l", "ud"],
  counted: [
    { singular: "cucharada", plural: "cucharadas" },
    { singular: "cucharadita", plural: "cucharaditas" },
    { singular: "pizca", plural: "pizcas" },
    { singular: "diente", plural: "dientes" },
    { singular: "rebanada", plural: "rebanadas" },
    { singular: "hoja", plural: "hojas" },
    { singular: "ramita", plural: "ramitas" },
  ],
};

// Límites: los de las reglas y la app (RecipeLimits, FirestoreRecipeRepository).
export const RECIPE_LIMITS = { title: 120, description: 1000, ingredients: 50, steps: 30, step: 500, servings: 50, minutes: 1440 };
const L = RECIPE_LIMITS;
const RECIPE_KEYS = ["title", "category", "description", "servings", "totalMinutes", "ingredients", "steps"];
const INGREDIENT_KEYS = ["name", "quantity", "unit"];
const INGREDIENT_NAME_MAX = 120;
const WATER = ingredientKey("Agua");
const UNIT_NAMES = [...RECIPE_UNITS.invariable, ...RECIPE_UNITS.counted.flatMap((u) => [u.singular, u.plural])];

const isObject = (x) => x != null && typeof x === "object" && !Array.isArray(x);
const isIntIn = (x, min, max) => Number.isInteger(x) && x >= min && x <= max;
// Texto de 1 a `max` caracteres, sin espacios en los extremos, sin espacios dobles y sin saltos de línea.
const isTextIn = (x, max) =>
  typeof x === "string" && x.length >= 1 && x.length <= max && x === x.trim() && !/\s{2,}|[\r\n\t]/.test(x);

/** Índice clave tolerante (`ingredientKey`) → nombre canónico de `ingredients.json`. */
export function indexIngredients(ingredientCatalog) {
  return new Map(ingredientCatalog.ingredients.map((i) => [ingredientKey(i.name), i.name]));
}

// Distancia de edición (Levenshtein) entre dos textos.
function editDistance(a, b) {
  let prev = Array.from({ length: b.length + 1 }, (_, j) => j);
  for (let i = 1; i <= a.length; i++) {
    const cur = [i];
    for (let j = 1; j <= b.length; j++) {
      cur[j] = Math.min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
    }
    prev = cur;
  }
  return prev[b.length];
}

/**
 * El nombre del catálogo más parecido a `name`: el que comparte más palabras de la clave tolerante (en
 * proporción); si ninguno comparte palabras, el más cercano letra a letra. En empate, el primero del catálogo.
 */
export function suggestIngredient(name, index) {
  const words = new Set(ingredientKey(name).split(" ").filter(Boolean));
  let best = null;
  let bestScore = 0;
  for (const [key, canonical] of index) {
    const keyWords = key.split(" ");
    const shared = keyWords.filter((w) => words.has(w)).length;
    const score = shared / (words.size + keyWords.length - shared);
    if (score > bestScore) [best, bestScore] = [canonical, score];
  }
  if (best) return best;
  const folded = foldText(name);
  let bestDistance = Infinity;
  for (const canonical of index.values()) {
    const distance = editDistance(folded, foldText(canonical));
    if (distance < bestDistance) [best, bestDistance] = [canonical, distance];
  }
  return best;
}

// Valor de una cantidad del catálogo: entero, decimal con coma o fracción «a/b» (b ≥ 2), mayor que cero y
// sin ceros a la izquierda. null si no se lee.
function quantityValue(text) {
  const fraction = /^([1-9]\d*)\/([1-9]\d*)$/.exec(text);
  if (fraction) return Number(fraction[2]) >= 2 ? Number(fraction[1]) / Number(fraction[2]) : null;
  if (!/^(0|[1-9]\d*)(,\d+)?$/.test(text)) return null;
  const value = Number(text.replace(",", "."));
  return value > 0 ? value : null;
}

// Problema de cantidad y unidad de un ingrediente, o null. Sin cantidad («al gusto») no lleva unidad.
function amountProblem(entry) {
  const { quantity, unit } = entry;
  if (!("quantity" in entry)) return "unit" in entry ? "Una unidad sin cantidad: quita la unidad o pon la cantidad." : null;
  if (typeof quantity !== "string") return `quantity tiene que ser texto («${quantity}» entre comillas).`;
  const value = quantityValue(quantity);
  if (value == null) return `Cantidad «${quantity}» no válida: entero, decimal con coma o fracción, mayor que cero.`;
  if (!("unit" in entry)) return `La cantidad «${quantity}» necesita unidad (${UNIT_NAMES.join(", ")}).`;
  if (RECIPE_UNITS.invariable.includes(unit)) return null;
  const counted = RECIPE_UNITS.counted.find((u) => u.singular === unit || u.plural === unit);
  if (!counted) return `Unidad «${unit}» fuera de la lista (${UNIT_NAMES.join(", ")}).`;
  const expected = value <= 1 ? counted.singular : counted.plural;
  return unit === expected ? null : `Con «${quantity}» la unidad es «${expected}».`;
}

function validateRecipeIngredient(entry, index) {
  if (!isObject(entry)) return ["El ingrediente no es un objeto."];
  const problems = [];
  const extra = Object.keys(entry).filter((k) => !INGREDIENT_KEYS.includes(k));
  if (extra.length) problems.push(`Claves no permitidas: ${extra.join(", ")}.`);
  if (!isTextIn(entry.name, INGREDIENT_NAME_MAX)) return [...problems, "name tiene que ser texto, sin espacios de más."];
  const key = ingredientKey(entry.name);
  if (key === WATER) problems.push("El agua no se lista.");
  else if (!index.has(key)) {
    problems.push(`«${entry.name}» no está en ingredients.json (¿«${suggestIngredient(entry.name, index)}»?).`);
  }
  const amount = amountProblem(entry);
  if (amount) problems.push(amount);
  return problems;
}

function validateIngredientList(ingredients, index) {
  if (!Array.isArray(ingredients) || ingredients.length < 1 || ingredients.length > L.ingredients) {
    return [`ingredients tiene que ser una lista de 1 a ${L.ingredients} ingredientes.`];
  }
  const problems = [];
  const seen = new Set();
  ingredients.forEach((e, i) => {
    const label = `ingrediente ${i + 1} (${e?.name ?? "?"})`;
    for (const p of validateRecipeIngredient(e, index)) problems.push(`${label}: ${p}`);
    if (typeof e?.name !== "string") return;
    const key = ingredientKey(e.name);
    if (seen.has(key)) problems.push(`${label}: repetido.`);
    seen.add(key);
  });
  return problems;
}

function validateSteps(steps) {
  if (!Array.isArray(steps) || steps.length < 1 || steps.length > L.steps) {
    return [`steps tiene que ser una lista de 1 a ${L.steps} pasos.`];
  }
  return steps.flatMap((s, i) =>
    isTextIn(s, L.step) ? [] : [`paso ${i + 1}: entre 1 y ${L.step} caracteres, sin espacios de más ni saltos de línea.`]);
}

function validateRecipe(recipe, index) {
  if (!isObject(recipe)) return ["La receta no es un objeto."];
  const problems = [];
  const extra = Object.keys(recipe).filter((k) => !RECIPE_KEYS.includes(k));
  const missing = RECIPE_KEYS.filter((k) => !(k in recipe));
  if (extra.length) problems.push(`Claves no permitidas: ${extra.join(", ")}.`);
  if (missing.length) problems.push(`Faltan claves: ${missing.join(", ")}.`);
  const { title, category, description, servings, totalMinutes } = recipe;
  if (!isTextIn(title, L.title)) problems.push(`title tiene que tener entre 1 y ${L.title} caracteres, sin espacios de más.`);
  else if (!/\p{Lu}/u.test(title[0])) problems.push("title tiene que empezar por una letra mayúscula.");
  if (!RECIPE_CATEGORIES.includes(category)) problems.push(`category tiene que ser una de: ${RECIPE_CATEGORIES.join(", ")}.`);
  if (!isTextIn(description, L.description)) {
    problems.push(`description tiene que tener entre 1 y ${L.description} caracteres, sin espacios de más.`);
  }
  if (!isIntIn(servings, 1, L.servings)) problems.push(`servings tiene que ser un entero entre 1 y ${L.servings}.`);
  if (!isIntIn(totalMinutes, 1, L.minutes)) problems.push(`totalMinutes tiene que ser un entero entre 1 y ${L.minutes}.`);
  return [...problems, ...validateIngredientList(recipe.ingredients, index), ...validateSteps(recipe.steps)];
}

export function validateRecipeCatalog(catalog, index) {
  const problems = [];
  if (!catalog || catalog.schemaVersion !== 1) problems.push("schemaVersion tiene que ser 1.");
  const list = catalog?.recipes;
  if (!Array.isArray(list) || list.length === 0) {
    problems.push("recipes tiene que ser una lista no vacía.");
    return problems;
  }
  const ids = new Set();
  list.forEach((r, i) => {
    for (const p of validateRecipe(r, index)) problems.push(`${r?.title ?? `#${i + 1}`}: ${p}`);
    if (typeof r?.title !== "string") return;
    const id = slug(r.title);
    if (ids.has(id)) problems.push(`id repetido: ${id}`);
    else ids.add(id);
  });
  return problems;
}

/**
 * Recetas listas para importar, en el orden del fichero, con la forma que escribe la app
 * (FirestoreRecipeRepository): cada ingrediente `{name, quantity, unit}` con el nombre canónico del catálogo y
 * `null` si falta la cantidad; los pasos, como lista. Exige un catálogo ya validado.
 */
export function buildRecipes(catalog, index) {
  return catalog.recipes.map((r) => ({
    id: slug(r.title),
    title: r.title,
    description: r.description,
    category: r.category,
    servings: r.servings,
    totalMinutes: r.totalMinutes,
    ingredients: r.ingredients.map((i) => ({
      name: index.get(ingredientKey(i.name)),
      quantity: i.quantity ?? null,
      unit: i.unit ?? null,
    })),
    steps: [...r.steps],
  }));
}

const servingsText = (n) => (n === 1 ? "1 ración" : `${n} raciones`);
const amountText = (i) => (i.quantity == null ? "al gusto" : `${i.quantity} ${i.unit}`);

export function renderRecipeReview(recipes) {
  const byCategory = groupBy(recipes, (r) => r.category);
  const lines = [
    "# Revisión del catálogo de recetas",
    "",
    "Generado con `npm run catalog:recipes` a partir de `data/catalog/recipes.json`; no se edita a mano.",
    "",
    `Total: ${recipes.length} recetas.`,
    "",
    "| Categoría | Recetas |",
    "|---|---|",
    ...RECIPE_CATEGORIES.map((c) => `| ${c} | ${(byCategory.get(c) ?? []).length} |`),
    "",
  ];
  for (const c of RECIPE_CATEGORIES) {
    const list = byCategory.get(c);
    if (!list) continue;
    lines.push(`## ${c}`, "");
    for (const r of list) {
      lines.push(
        `### ${r.title}`, "",
        `\`${r.id}\` · ${servingsText(r.servings)} · ${r.totalMinutes} min`, "",
        r.description, "",
        "**Ingredientes**", "",
        ...r.ingredients.map((i) => `- ${i.name} — ${amountText(i)}`), "",
        "**Preparación**", "",
        ...r.steps.map((s, n) => `${n + 1}. ${s}`), "",
      );
    }
  }
  return lines.join("\n");
}
```

- [ ] **Step 6: Escribir `SC/build-recipes.mjs`:**

```js
// Valida data/catalog/recipes.json contra ingredients.json y genera data/catalog/recipes-review.md.
import { readFile, writeFile } from "node:fs/promises";
import { buildRecipes, indexIngredients, renderRecipeReview, validateRecipeCatalog } from "./recipes.mjs";

const ROOT = new URL("../../", import.meta.url);
const SOURCE = new URL("data/catalog/recipes.json", ROOT);
const INGREDIENTS = new URL("data/catalog/ingredients.json", ROOT);
const REVIEW = new URL("data/catalog/recipes-review.md", ROOT);

const catalog = JSON.parse(await readFile(SOURCE, "utf8"));
const index = indexIngredients(JSON.parse(await readFile(INGREDIENTS, "utf8")));
const problems = validateRecipeCatalog(catalog, index);
if (problems.length) {
  for (const p of problems) console.error(`- ${p}`);
  console.error(`${problems.length} problemas en data/catalog/recipes.json.`);
  process.exit(1);
}
const recipes = buildRecipes(catalog, index);
await writeFile(REVIEW, renderRecipeReview(recipes));
console.log(`Catálogo de recetas: ${recipes.length} recetas. Revisión en data/catalog/recipes-review.md`);
```

- [ ] **Step 7: Ejecutar NODE y verlo en verde** (todos, también los que ya existían).

- [ ] **Step 8: Commit**

```bash
git add scripts/catalog/recipes.mjs scripts/catalog/build-recipes.mjs tests/catalog-recipes.test.mjs tests/fixtures/recipe-units.json package.json
git commit -m "feat(catalogo): validación, construcción y revisión del catálogo de recetas"
```

---

### Task 2: Contenido: dos ingredientes nuevos y las 60 recetas comunes

**Ficheros:**
- Modificar: `DC/ingredients.json` (2 ingredientes), `DC/ingredients-review.md` (regenerado), `docs/INGREDIENT_CATALOG.md` y `docs/DATA_MODEL.md` (recuento 356 → 358), `T/catalog-recipes.test.mjs` (un test al final).
- Crear: `DC/recipes.json`, `DC/recipes-review.md` (generado).

**Interfaces:**
- Consume: `validateRecipeCatalog`, `buildRecipes`, `indexIngredients`, `RECIPE_CATEGORIES` (Tarea 1); `npm run catalog:recipes` y `npm run catalog:ingredients`.
- Produce: `DC/recipes.json` con `{ "schemaVersion": 1, "recipes": [...] }` en el orden de la lista de abajo (lo usan la Tarea 3 y el despliegue), y `DC/ingredients.json` con «Queso crema» y «Ternera para guisar».

- [ ] **Step 1: Añadir el test** al final de `T/catalog-recipes.test.mjs`:

```js
test("el recipes.json real es válido: 60 recetas en las 11 categorías y cantidades como texto", async () => {
  const real = JSON.parse(await readFile(new URL("../data/catalog/recipes.json", import.meta.url), "utf8"));
  const realIndex = indexIngredients(JSON.parse(await readFile(new URL("../data/catalog/ingredients.json", import.meta.url), "utf8")));
  assert.deepEqual(validateRecipeCatalog(real, realIndex), []);
  const built = buildRecipes(real, realIndex);
  assert.equal(built.length, 60);
  assert.deepEqual(
    Object.fromEntries(RECIPE_CATEGORIES.map((c) => [c, built.filter((r) => r.category === c).length])),
    {
      Legumbres: 5, Arroces: 4, Pasta: 4, Carnes: 6, Pescados: 5, Verduras: 5, Ensaladas: 4, Huevos: 4,
      "Sopas y cremas": 5, "Desayunos y meriendas": 9, Postres: 9,
    },
  );
  assert.equal(built.reduce((n, r) => n + r.ingredients.length, 0), 503);
  assert.ok(built.every((r) => r.ingredients.every((i) => i.quantity === null || typeof i.quantity === "string")));
});
```

- [ ] **Step 2: Ejecutar NODE y verlo en rojo** (falta `recipes.json`). Copia el fallo.

- [ ] **Step 3: Añadir los dos ingredientes** a `DC/ingredients.json`, cada uno en su categoría y en orden alfabético (`Intl.Collator("es")`) entre sus vecinos, con la misma forma que el resto:
  - `{ "id": "queso-crema", "name": "Queso crema", "category": "Lácteos y huevos", "defaultUnit": "g" }` (entre «Queso» y «Queso curado»).
  - `{ "id": "ternera-para-guisar", "name": "Ternera para guisar", "category": "Carnes", "defaultUnit": "g" }` (antes de «Ternera picada»).

  Ejecuta `npm run catalog:ingredients` (regenera `DC/ingredients-review.md`; «Total: 358»). Cambia «356» por «358» en `docs/INGREDIENT_CATALOG.md` (líneas 3, 160, 181 y 183) y en `docs/DATA_MODEL.md` (línea 574).

- [ ] **Step 4: Escribir `DC/recipes.json`** a partir de la lista de abajo, **en ese orden y sin cambiar** títulos, categorías, raciones, minutos, entradillas ni ingredientes. Formato: el de `JSON.stringify(datos, null, 2)` más un salto de línea final; claves de cada receta en este orden: `title`, `category`, `description`, `servings`, `totalMinutes`, `ingredients`, `steps`.

  **Notación de la lista:**
  - `# Título | Categoría | raciones | minutos` → `title`, `category`, `servings` (entero) y `totalMinutes` (entero).
  - La línea siguiente → `description`.
  - La tercera línea son los ingredientes, separados por `; `. `Nombre 300 g` (los dos últimos elementos son cantidad y unidad) → `{ "name": "Nombre", "quantity": "300", "unit": "g" }` (la cantidad **entre comillas**: «1/2», «1,5»); `Nombre` solo → `{ "name": "Nombre" }` (al gusto, sin `quantity` ni `unit`).
  - `steps` lo redactas tú, con la guía de estilo de abajo.

  Escribe el fichero por bloques de categoría y ejecuta `npm run catalog:recipes` después de cada bloque; arregla lo que diga antes de seguir. Si necesitas un script auxiliar para convertir la lista, déjalo fuera del repositorio y no lo incluyas en el commit.

  **Guía de estilo de los pasos:**
  - Entre 3 y 8 pasos por receta (la lasaña, el arroz al horno o el potaje pueden llegar a 10); cada paso, de 1 a 3 frases y no más de 300 caracteres (el límite duro es 500). Sin saltos de línea, sin espacios dobles.
  - Imperativo de tú, español de España, frases completas acabadas en punto: «Pela las patatas y córtalas en dados.» Sin emojis, sin «¡», sin marcas comerciales.
  - Usa **todos** los ingredientes de la lista, con su nombre común en minúsculas («el ajo», «las lentejas»), y **ninguno que no esté** en ella, salvo el agua (que no se lista, rulings 6). En los «al gusto» basta con «sala», «salpimienta», «espolvorea perejil picado».
  - Cantidades en los pasos solo si son una parte («la mitad del azúcar», «dos cucharadas del aceite»); no repitas la lista.
  - Fuego «suave», «medio» o «fuerte»; horno en grados («Precalienta el horno a 180 °C.»); tiempos en minutos («cuece 20 minutos»).
  - Los tiempos de los pasos tienen que cuadrar con `totalMinutes`, que no cuenta el remojo ni la nevera (esos ya los avisa la entradilla; puedes recordarlos en el paso que toque: «Escurre los garbanzos del remojo.»).
  - Cocina española casera y realista: técnica correcta, sin pasos de relleno («Disfruta.»). El último paso es servir o emplatar solo si aporta algo («Sirve templado con canela por encima.»).

```text
# Lentejas estofadas | Legumbres | 4 | 60
Las de toda la vida, con chorizo y verduras. Las pardinas no necesitan remojo.
Lentejas pardinas 300 g; Chorizo 1 ud; Patata 2 ud; Zanahoria 2 ud; Cebolla 1 ud; Pimiento verde 1 ud; Ajo 2 dientes; Tomate triturado 100 g; Pimentón dulce 1 cucharadita; Laurel 1 hoja; Aceite de oliva virgen extra 2 cucharadas; Sal

# Garbanzos con espinacas | Legumbres | 4 | 30
Guiso sevillano con garbanzos cocidos, espinacas, comino y pan frito majado.
Garbanzos cocidos 800 g; Espinacas 300 g; Ajo 3 dientes; Pan 2 rebanadas; Pimentón dulce 1 cucharadita; Comino 1/2 cucharadita; Vinagre de vino 1 cucharada; Aceite de oliva virgen extra 4 cucharadas; Sal

# Alubias blancas con verduras | Legumbres | 4 | 90
Alubias a fuego lento con puerro, zanahoria y pimiento. Pon las alubias en remojo la noche anterior.
Alubias blancas 400 g; Puerro 1 ud; Zanahoria 2 ud; Pimiento rojo 1 ud; Cebolla 1 ud; Ajo 2 dientes; Laurel 1 hoja; Pimentón dulce 1 cucharadita; Aceite de oliva virgen extra 3 cucharadas; Sal

# Potaje de vigilia | Legumbres | 4 | 150
Potaje de Cuaresma con garbanzos, bacalao, espinacas y huevo duro. Pon los garbanzos en remojo la noche anterior.
Garbanzos 300 g; Bacalao desalado 300 g; Espinacas 250 g; Huevos 2 ud; Cebolla 1 ud; Ajo 2 dientes; Tomate triturado 100 g; Pimentón dulce 1 cucharadita; Laurel 1 hoja; Aceite de oliva virgen extra 3 cucharadas; Sal

# Lentejas al curry con leche de coco | Legumbres | 4 | 40
Lentejas especiadas y cremosas, sin carne, que se hacen en una sola olla.
Lentejas 250 g; Leche de coco 400 ml; Tomate triturado 200 g; Cebolla 1 ud; Ajo 2 dientes; Jengibre 10 g; Curry en polvo 2 cucharaditas; Cúrcuma 1/2 cucharadita; Cilantro; Aceite de oliva virgen extra 2 cucharadas; Sal

# Paella de pollo y verduras | Arroces | 4 | 60
Paella de domingo con pollo, judías verdes y pimiento, con su punto de socarrat.
Arroz bomba 320 g; Contramuslos de pollo 500 g; Judías verdes 200 g; Pimiento rojo 1 ud; Tomate triturado 150 g; Ajo 2 dientes; Caldo de pollo 1 l; Azafrán 1 pizca; Pimentón dulce 1 cucharadita; Romero 1 ramita; Aceite de oliva virgen extra 4 cucharadas; Sal

# Arroz a la cubana | Arroces | 4 | 30
Arroz blanco con tomate frito, huevo frito y plátano: rápido y de los que gustan a todos.
Arroz 320 g; Tomate frito 400 g; Huevos 4 ud; Plátano 4 ud; Ajo 2 dientes; Laurel 1 hoja; Aceite de girasol 100 ml; Aceite de oliva virgen extra 1 cucharada; Sal

# Arroz al horno | Arroces | 4 | 75
Arroz valenciano al horno con costillas, morcilla, garbanzos y patata.
Arroz bomba 320 g; Costillas de cerdo 400 g; Morcilla 1 ud; Garbanzos cocidos 200 g; Patata 1 ud; Tomate 2 ud; Ajo 6 dientes; Caldo de pollo 900 ml; Pimentón dulce 1 cucharadita; Azafrán 1 pizca; Aceite de oliva virgen extra 4 cucharadas; Sal

# Arroz tres delicias | Arroces | 4 | 30
Arroz salteado con tortilla, jamón, gambas y guisantes; mejor con arroz cocido del día anterior.
Arroz basmati 300 g; Huevos 2 ud; Jamón cocido 150 g; Gambas 150 g; Guisantes 150 g; Zanahoria 1 ud; Salsa de soja 2 cucharadas; Aceite de girasol 3 cucharadas; Sal

# Macarrones con tomate y chorizo | Pasta | 4 | 30
Los macarrones de casa, con sofrito de tomate, chorizo y queso gratinado.
Macarrones 400 g; Chorizo 150 g; Tomate triturado 400 g; Cebolla 1 ud; Ajo 1 diente; Queso rallado 80 g; Orégano 1 cucharadita; Azúcar 1 cucharadita; Aceite de oliva virgen extra 2 cucharadas; Sal

# Espaguetis a la carbonara | Pasta | 4 | 20
La carbonara sin nata: huevo, queso y panceta bien dorada.
Espaguetis 400 g; Panceta 150 g; Huevos 4 ud; Queso parmesano 80 g; Pimienta negra; Sal

# Lasaña de carne | Pasta | 6 | 90
Lasaña con boloñesa casera y bechamel, gratinada al horno.
Lasaña 12 ud; Carne picada mixta 500 g; Tomate triturado 400 g; Cebolla 1 ud; Zanahoria 1 ud; Ajo 2 dientes; Vino tinto 100 ml; Leche entera 750 ml; Mantequilla 50 g; Harina de trigo 50 g; Nuez moscada 1 pizca; Queso rallado 100 g; Aceite de oliva virgen extra 2 cucharadas; Pimienta negra; Sal

# Pasta con pesto y tomates cherry | Pasta | 4 | 15
Cena en un cuarto de hora: pasta, pesto, tomates cherry y piñones tostados.
Pasta 400 g; Pesto 150 g; Tomate cherry 250 g; Queso parmesano 40 g; Piñones 30 g; Sal

# Pollo asado con patatas | Carnes | 4 | 75
Muslos de pollo al horno sobre una cama de patatas y cebolla, con limón y hierbas.
Muslos de pollo 4 ud; Patata 4 ud; Cebolla 1 ud; Limón 1 ud; Ajo 4 dientes; Vino blanco 100 ml; Tomillo 1 cucharadita; Romero 1 ramita; Aceite de oliva virgen extra 4 cucharadas; Pimienta negra; Sal

# Albóndigas en salsa | Carnes | 4 | 60
Albóndigas tiernas en salsa de verduras, para mojar pan.
Carne picada mixta 500 g; Huevos 1 ud; Pan rallado 50 g; Leche entera 50 ml; Ajo 2 dientes; Perejil; Harina de trigo 50 g; Cebolla 1 ud; Zanahoria 1 ud; Tomate triturado 200 g; Vino blanco 100 ml; Caldo de pollo 300 ml; Aceite de oliva virgen extra 100 ml; Pimienta negra; Sal

# Solomillo de cerdo a la mostaza | Carnes | 4 | 30
Medallones de solomillo dorados en una salsa cremosa de mostaza.
Solomillo de cerdo 600 g; Mostaza 2 cucharadas; Nata para cocinar 200 ml; Cebolla 1 ud; Vino blanco 50 ml; Aceite de oliva virgen extra 2 cucharadas; Pimienta negra; Sal

# Pechugas de pollo al limón | Carnes | 2 | 20
Cena ligera: pechuga a la plancha con ajo, tomillo y limón.
Pechuga de pollo 2 ud; Limón 1 ud; Ajo 1 diente; Tomillo 1 cucharadita; Aceite de oliva virgen extra 2 cucharadas; Pimienta negra; Sal

# Estofado de ternera | Carnes | 4 | 120
Ternera guisada a fuego lento con patatas, zanahoria y vino tinto.
Ternera para guisar 800 g; Patata 4 ud; Zanahoria 2 ud; Cebolla 1 ud; Ajo 2 dientes; Guisantes 100 g; Tomate triturado 150 g; Vino tinto 200 ml; Caldo de verduras 500 ml; Laurel 1 hoja; Harina de trigo 1 cucharada; Aceite de oliva virgen extra 3 cucharadas; Pimienta negra; Sal

# Hamburguesas caseras | Carnes | 4 | 30
Hamburguesas de ternera hechas en casa, con queso, lechuga y tomate.
Ternera picada 600 g; Pan de hamburguesa 4 ud; Queso en lonchas 4 ud; Lechuga 4 hojas; Tomate 1 ud; Cebolla morada 1 ud; Kétchup; Mostaza; Aceite de oliva virgen extra 1 cucharada; Pimienta negra; Sal

# Merluza en salsa verde | Pescados | 4 | 30
Merluza con almejas en salsa de ajo y perejil, al estilo vasco.
Merluza 800 g; Almejas 250 g; Ajo 3 dientes; Perejil; Harina de trigo 1 cucharada; Vino blanco 100 ml; Caldo de pescado 250 ml; Aceite de oliva virgen extra 4 cucharadas; Sal

# Salmón al horno con verduras | Pescados | 4 | 30
Cena ligera en una sola bandeja: salmón, calabacín y pimiento con eneldo y limón.
Salmón 600 g; Calabacín 1 ud; Pimiento rojo 1 ud; Cebolla morada 1 ud; Limón 1 ud; Eneldo 1 cucharadita; Aceite de oliva virgen extra 3 cucharadas; Pimienta negra; Sal

# Bacalao con tomate | Pescados | 4 | 45
Bacalao enharinado y frito, terminado en una salsa de tomate casera.
Bacalao desalado 600 g; Tomate triturado 600 g; Cebolla 1 ud; Pimiento verde 1 ud; Ajo 2 dientes; Harina de trigo 50 g; Azúcar 1 cucharadita; Aceite de oliva virgen extra 100 ml; Sal

# Dorada a la espalda | Pescados | 2 | 30
Dorada al horno abierta en dos, con un refrito de ajo y guindilla.
Dorada 2 ud; Ajo 4 dientes; Guindilla 1 ud; Vinagre de vino 1 cucharada; Limón 1 ud; Perejil; Aceite de oliva virgen extra 4 cucharadas; Sal

# Gambas al ajillo | Pescados | 4 | 15
Gambas en cazuela de barro con ajo y guindilla: tapa o cena rápida.
Gambas 400 g; Ajo 6 dientes; Guindilla 1 ud; Perejil; Aceite de oliva virgen extra 100 ml; Sal

# Pisto manchego | Verduras | 4 | 60
Verduras de huerta pochadas despacio con tomate; también sirve de guarnición.
Calabacín 1 ud; Berenjena 1 ud; Pimiento rojo 1 ud; Pimiento verde 1 ud; Cebolla 1 ud; Ajo 2 dientes; Tomate triturado 400 g; Azúcar 1 cucharadita; Aceite de oliva virgen extra 4 cucharadas; Sal

# Judías verdes con patata | Verduras | 4 | 30
Cena ligera: judías verdes y patata cocidas con un refrito de ajo y pimentón.
Judías verdes 500 g; Patata 2 ud; Ajo 2 dientes; Pimentón dulce 1/2 cucharadita; Aceite de oliva virgen extra 3 cucharadas; Sal

# Berenjenas rellenas | Verduras | 4 | 60
Berenjenas rellenas de carne con tomate y gratinadas con queso.
Berenjena 2 ud; Carne picada mixta 300 g; Cebolla 1 ud; Ajo 1 diente; Tomate frito 200 g; Queso rallado 80 g; Aceite de oliva virgen extra 2 cucharadas; Pimienta negra; Sal

# Menestra de verduras | Verduras | 4 | 45
Verduras de temporada cocidas por separado y rehogadas con jamón.
Alcachofa 4 ud; Judías verdes 200 g; Guisantes 150 g; Zanahoria 2 ud; Espárragos verdes 200 g; Jamón serrano 100 g; Cebolla 1 ud; Ajo 2 dientes; Harina de trigo 1 cucharada; Aceite de oliva virgen extra 3 cucharadas; Sal

# Brócoli salteado con ajo y almendras | Verduras | 2 | 15
Cena ligera: brócoli al dente salteado con ajo, soja y almendra tostada.
Brócoli 500 g; Ajo 3 dientes; Almendras fileteadas 30 g; Salsa de soja 1 cucharada; Aceite de oliva virgen extra 2 cucharadas; Sal

# Ensalada mixta | Ensaladas | 4 | 15
La ensalada de siempre, con atún, huevo duro y aceitunas.
Lechuga 1 ud; Tomate 2 ud; Cebolla 1/2 ud; Zanahoria 1 ud; Maíz dulce 100 g; Atún en lata 160 g; Huevos 2 ud; Aceitunas verdes 50 g; Aceite de oliva virgen extra 4 cucharadas; Vinagre de vino 2 cucharadas; Sal

# Ensaladilla rusa | Ensaladas | 4 | 40
Patata, zanahoria, huevo y atún con mayonesa; mejor fría de la nevera.
Patata 4 ud; Zanahoria 2 ud; Guisantes 100 g; Huevos 3 ud; Atún en lata 160 g; Aceitunas verdes 50 g; Pimiento del piquillo 2 ud; Mayonesa 200 g; Sal

# Ensalada de garbanzos | Ensaladas | 4 | 15
Ensalada fresca de garbanzos cocidos con tomate, pepino y aceitunas.
Garbanzos cocidos 400 g; Tomate 2 ud; Pepino 1 ud; Pimiento verde 1 ud; Cebolla morada 1/2 ud; Aceitunas negras 50 g; Perejil; Comino 1 pizca; Aceite de oliva virgen extra 3 cucharadas; Vinagre de vino 1 cucharada; Sal

# Ensalada de quinoa con aguacate | Ensaladas | 2 | 25
Cena ligera de quinoa, aguacate, tomate cherry y maíz con aliño de lima.
Quinoa 150 g; Aguacate 1 ud; Tomate cherry 150 g; Pepino 1/2 ud; Maíz dulce 80 g; Lima 1 ud; Cilantro; Aceite de oliva virgen extra 2 cucharadas; Sal

# Tortilla de patatas | Huevos | 4 | 45
Con cebolla y jugosa por dentro.
Patata 700 g; Huevos 6 ud; Cebolla 1 ud; Aceite de oliva virgen extra 250 ml; Sal

# Huevos rotos con jamón | Huevos | 2 | 30
Patatas fritas, huevos con la yema líquida y jamón serrano por encima.
Patata 3 ud; Huevos 4 ud; Jamón serrano 80 g; Aceite de oliva suave 300 ml; Sal

# Revuelto de champiñones y gambas | Huevos | 2 | 15
Cena rápida: revuelto cremoso con champiñones, gambas y ajo.
Huevos 4 ud; Champiñones 200 g; Gambas 150 g; Ajo 1 diente; Perejil; Aceite de oliva virgen extra 2 cucharadas; Sal

# Huevos a la flamenca | Huevos | 2 | 30
Huevos cuajados al horno sobre un sofrito de tomate con guisantes, chorizo y jamón.
Huevos 4 ud; Tomate triturado 400 g; Guisantes 100 g; Chorizo 80 g; Jamón serrano 50 g; Cebolla 1 ud; Ajo 1 diente; Pimentón dulce 1/2 cucharadita; Aceite de oliva virgen extra 2 cucharadas; Sal

# Gazpacho andaluz | Sopas y cremas | 4 | 15
Sopa fría de tomate, pimiento y pepino para el verano. Sabe mejor tras una hora en la nevera.
Tomate 1 kg; Pimiento verde 1 ud; Pepino 1/2 ud; Ajo 1 diente; Pan 50 g; Aceite de oliva virgen extra 100 ml; Vinagre de vino 2 cucharadas; Sal

# Salmorejo cordobés | Sopas y cremas | 4 | 15
Crema fría de tomate y pan, con huevo duro y jamón por encima. Sírvelo bien frío.
Tomate 1 kg; Pan 200 g; Ajo 1 diente; Aceite de oliva virgen extra 150 ml; Huevos 2 ud; Jamón serrano 50 g; Sal

# Crema de calabaza | Sopas y cremas | 4 | 40
Crema suave de calabaza y zanahoria con un toque de nuez moscada.
Calabaza 800 g; Zanahoria 2 ud; Puerro 1 ud; Patata 1 ud; Caldo de verduras 750 ml; Nata para cocinar 100 ml; Nuez moscada 1 pizca; Aceite de oliva virgen extra 2 cucharadas; Pimienta negra; Sal

# Sopa de fideos | Sopas y cremas | 4 | 30
Sopa de caldo de pollo con fideos finos y jamón, de las que entonan.
Caldo de pollo 1,5 l; Fideos 120 g; Jamón serrano 50 g; Huevos 2 ud; Sal

# Crema de calabacín | Sopas y cremas | 4 | 30
Cena ligera: crema de calabacín con queso fresco, lista en media hora.
Calabacín 3 ud; Cebolla 1 ud; Patata 1 ud; Queso fresco 80 g; Caldo de verduras 500 ml; Aceite de oliva virgen extra 2 cucharadas; Pimienta negra; Sal

# Tostadas con tomate y aceite | Desayunos y meriendas | 2 | 10
El desayuno mediterráneo: pan tostado, tomate rallado y aceite de oliva.
Pan 4 rebanadas; Tomate 2 ud; Ajo 1 diente; Aceite de oliva virgen extra 2 cucharadas; Sal

# Porridge de avena con plátano | Desayunos y meriendas | 1 | 10
Gachas de avena con leche, plátano, canela y nueces.
Avena 50 g; Leche 250 ml; Plátano 1 ud; Nueces 15 g; Miel 1 cucharadita; Canela en polvo 1 pizca

# Tortitas americanas | Desayunos y meriendas | 4 | 25
Tortitas esponjosas para el desayuno del fin de semana, con sirope de arce.
Harina de trigo 200 g; Leche 250 ml; Huevos 2 ud; Azúcar 2 cucharadas; Levadura química 2 cucharaditas; Mantequilla 30 g; Sirope de arce 4 cucharadas; Sal 1 pizca

# Batido de plátano y fresas | Desayunos y meriendas | 2 | 5
Batido cremoso de fruta con leche y yogur, en cinco minutos.
Plátano 1 ud; Fresas 150 g; Leche 300 ml; Yogur natural 1 ud; Miel 1 cucharada

# Granola casera | Desayunos y meriendas | 8 | 40
Copos de avena tostados con miel y frutos secos; se guarda dos semanas en un bote.
Avena 250 g; Almendras 60 g; Nueces 60 g; Miel 80 g; Aceite de girasol 3 cucharadas; Canela en polvo 1 cucharadita; Pasas 50 g; Sal 1 pizca

# Tostada de aguacate y huevo | Desayunos y meriendas | 2 | 15
Pan integral con aguacate machacado y huevo a la plancha.
Pan integral 2 rebanadas; Aguacate 1 ud; Huevos 2 ud; Limón 1/2 ud; Aceite de oliva virgen extra 1 cucharadita; Pimienta negra; Sal

# Magdalenas caseras | Desayunos y meriendas | 12 | 35
Magdalenas con copete y ralladura de limón. Salen unas doce.
Huevos 3 ud; Azúcar 150 g; Aceite de girasol 150 ml; Leche 100 ml; Harina de repostería 200 g; Levadura química 2 cucharaditas; Limón 1 ud; Sal 1 pizca

# Bizcocho de yogur | Desayunos y meriendas | 8 | 50
El bizcocho más fácil, medido con el vasito del yogur.
Yogur natural 1 ud; Huevos 3 ud; Azúcar 200 g; Aceite de girasol 100 ml; Harina de repostería 250 g; Levadura química 16 g; Limón 1 ud; Mantequilla 10 g

# Pan de plátano | Desayunos y meriendas | 8 | 70
Bizcocho de plátano maduro con nueces y canela.
Plátano 3 ud; Harina de trigo 220 g; Huevos 2 ud; Azúcar moreno 100 g; Mantequilla 80 g; Levadura química 2 cucharaditas; Canela en polvo 1 cucharadita; Nueces 50 g; Sal 1 pizca

# Arroz con leche | Postres | 6 | 60
Cremoso, con canela y piel de limón. Se puede tomar templado o frío.
Arroz 150 g; Leche entera 1 l; Azúcar 120 g; Canela en rama 1 ud; Limón 1 ud; Canela en polvo 1 pizca

# Flan de huevo | Postres | 6 | 60
Flan casero al baño maría con caramelo. Necesita al menos cuatro horas de nevera.
Huevos 4 ud; Leche entera 500 ml; Azúcar 200 g; Esencia de vainilla 1 cucharadita

# Natillas caseras | Postres | 4 | 30
Natillas de la abuela con galleta y canela. Mejor tras dos horas en la nevera.
Leche entera 500 ml; Huevos 4 ud; Azúcar 100 g; Maicena 1 cucharada; Canela en rama 1 ud; Limón 1 ud; Galletas María 4 ud; Canela en polvo 1 pizca

# Torrijas | Postres | 6 | 40
Pan del día anterior empapado en leche aromatizada, frito y rebozado en azúcar y canela.
Barra de pan 1 ud; Leche entera 500 ml; Huevos 2 ud; Azúcar 100 g; Canela en rama 1 ud; Limón 1 ud; Canela en polvo 1 cucharadita; Aceite de girasol 300 ml

# Tarta de queso al horno | Postres | 8 | 60
Tarta de queso cremosa por dentro y tostada por fuera, sin base. Déjala enfriar antes de cortarla.
Queso crema 500 g; Nata para montar 200 ml; Huevos 4 ud; Azúcar 150 g; Harina de trigo 1 cucharada

# Mousse de chocolate | Postres | 4 | 20
Mousse ligera de chocolate negro. Necesita tres horas de nevera.
Chocolate negro 150 g; Huevos 3 ud; Azúcar 30 g; Nata para montar 200 ml

# Manzanas asadas | Postres | 4 | 50
Manzanas al horno con mantequilla, azúcar y canela.
Manzana 4 ud; Azúcar 4 cucharadas; Mantequilla 20 g; Canela en polvo 1 cucharadita; Vino blanco 100 ml

# Macedonia de frutas | Postres | 4 | 15
Fruta fresca troceada con zumo de naranja y hierbabuena.
Naranja 2 ud; Manzana 1 ud; Plátano 1 ud; Kiwi 2 ud; Fresas 150 g; Zumo de naranja 200 ml; Hierbabuena 1 ramita

# Peras al vino tinto | Postres | 4 | 45
Peras cocidas en vino tinto con canela y clavo, con su almíbar.
Pera 4 ud; Vino tinto 500 ml; Azúcar 100 g; Canela en rama 1 ud; Limón 1 ud; Clavo 2 ud
```

- [ ] **Step 5: Generar la revisión:** `npm run catalog:recipes` → «Catálogo de recetas: 60 recetas.» y `DC/recipes-review.md`.

- [ ] **Step 6: Ejecutar NODE y verlo en verde.**

- [ ] **Step 7: Commit**

```bash
git add data/catalog/ingredients.json data/catalog/ingredients-review.md data/catalog/recipes.json data/catalog/recipes-review.md docs/INGREDIENT_CATALOG.md docs/DATA_MODEL.md tests/catalog-recipes.test.mjs
git commit -m "feat(catalogo): 60 recetas comunes y dos ingredientes nuevos"
```

> **PARADA DEL USUARIO (tras la Tarea 2).** El coordinador comprueba que `recipes.json` coincide con la lista de arriba en todo menos los pasos (script de comparación propio, fuera del repo), lee los pasos de unas cuantas recetas, enseña `DC/recipes-review.md` al usuario y **espera su aprobación**. Los cambios que pida los aplica un subagente `sonnet` (editar `recipes.json` y, si hace falta, `ingredients.json`; regenerar las revisiones; ajustar los recuentos del test; NODE en verde; commit). No se sigue con la Tarea 3 hasta tener el visto bueno.

---

### Task 3: Importador de recetas y documentación del catálogo

**Ficheros:**
- Modificar: `SC/import-core.mjs` (dos funciones nuevas al final), `SC/routine-import-plan.mjs` (delega en ellas).
- Crear: `SC/recipe-import-plan.mjs`, `SC/import-recipes.mjs`, `T/catalog-recipe-import-plan.test.mjs`, `T/recipe-import.emulator.test.mjs`, `docs/RECIPE_CATALOG.md`.
- Modificar: `package.json`, `DC/LICENSE.md`, `docs/DATA_MODEL.md`, `docs/RECIPES_ADMIN.md`.

**Interfaces:**
- Consume: `validateRecipeCatalog`, `buildRecipes`, `indexIngredients` (Tarea 1); `planCatalogImport`, `formatImportPlan`, `section`, `baseExisting`, `stampCatalogDoc`, `loadExisting`, `commitCatalogOps`, `CATALOG_ADMIN_UID` (`SC/import-core.mjs`); `CATALOG_AUTHOR_NAME` (`SC/import-plan.mjs`); `connectAdmin`, `parseImportArgs`, `runMain` (`SC/admin-cli.mjs`).
- Produce:
  - `planGlobalCatalogImport({ entries, existing, managedFields, adminUid }) → plan` (con `manualGlobal`) y `formatGlobalCatalogPlan(plan, title) → string` en `SC/import-core.mjs`.
  - `SC/recipe-import-plan.mjs`: `RECIPES_COLLECTION = "recipes"`, `recipeManagedFields(entry)`, `recipeDocToExisting(id, data, times)`, `recipeToFirestoreDoc(entry, { now, create })`, `planRecipeImport({ recipes, existing, adminUid })`, `formatRecipePlan(plan)`. **La Tarea 4 usa `recipeToFirestoreDoc` en los tests de reglas.**
  - `SC/import-recipes.mjs`: `runRecipeImport({ db, catalog, ingredients, apply, log })` y la CLI `npm run catalog:import-recipes -- --project … [--apply] [--catalog ruta]`.

- [ ] **Step 1: Escribir el test del plan `T/catalog-recipe-import-plan.test.mjs`:**

```js
import assert from "node:assert/strict";
import { test } from "node:test";
import { CATALOG_ADMIN_UID } from "../scripts/catalog/import-core.mjs";
import {
  RECIPES_COLLECTION, formatRecipePlan, planRecipeImport, recipeDocToExisting, recipeManagedFields, recipeToFirestoreDoc,
} from "../scripts/catalog/recipe-import-plan.mjs";

const r = (over = {}) => ({
  id: "lentejas", title: "Lentejas", description: "De diario.", category: "Legumbres", servings: 4, totalMinutes: 60,
  ingredients: [{ name: "Lentejas pardinas", quantity: "300", unit: "g" }, { name: "Sal", quantity: null, unit: null }],
  steps: ["Cuece las lentejas."],
  ...over,
});
const imported = (e, over = {}) => ({
  id: e.id, ownerUid: CATALOG_ADMIN_UID, hasSource: true, updatedAtMillis: 1000, importedAtMillis: 1000,
  visibility: "GLOBAL", fields: recipeManagedFields(e), ...over,
});
const plan = (recipes, existing = []) => planRecipeImport({ recipes, existing, adminUid: CATALOG_ADMIN_UID });

test("la colección es recipes", () => assert.equal(RECIPES_COLLECTION, "recipes"));

test("al crear lleva visibilidad, dueño y autor del catálogo; al actualizar, solo lo gestionado", () => {
  const now = { marker: true };
  const e = r();
  assert.deepEqual(recipeToFirestoreDoc(e, { now, create: true }), {
    title: "Lentejas", description: "De diario.", category: "Legumbres", servings: 4, totalMinutes: 60,
    ingredients: e.ingredients, steps: e.steps,
    source: { provider: "bobitos", importedAt: now }, updatedBy: CATALOG_ADMIN_UID, updatedAt: now,
    visibility: "GLOBAL", ownerUid: CATALOG_ADMIN_UID, createdBy: CATALOG_ADMIN_UID, createdByName: "Catálogo Bobitos", createdAt: now,
  });
  assert.deepEqual(Object.keys(recipeToFirestoreDoc(e, { now })).sort(), [
    "category", "description", "ingredients", "servings", "source", "steps", "title", "totalMinutes", "updatedAt", "updatedBy",
  ]);
});

test("un documento importado sin cambios queda igual", () => {
  const e = r();
  const data = { ...recipeToFirestoreDoc(e, { now: "t", create: true }) };
  const existing = [recipeDocToExisting(e.id, data, { updatedAtMillis: 5, importedAtMillis: 5 })];
  assert.deepEqual(plan([e], existing).unchanged, ["lentejas"]);
});

test("cambiar un paso, una cantidad, las raciones o la categoría actualiza", () => {
  const changes = [
    r({ steps: ["Cuece las lentejas 40 minutos."] }),
    r({ ingredients: [{ name: "Lentejas pardinas", quantity: "400", unit: "g" }, { name: "Sal", quantity: null, unit: null }] }),
    r({ servings: 6 }),
    r({ category: "Verduras" }),
  ];
  for (const changed of changes) assert.deepEqual(plan([changed], [imported(r())]).update.map((x) => x.id), ["lentejas"]);
});

test("omite la receta de un usuario, la manual del admin y la editada en la app", () => {
  assert.deepEqual(plan([r()], [imported(r(), { ownerUid: "u1", hasSource: false })]).skippedUserOwned,
    [{ id: "lentejas", ownerUid: "u1" }]);
  assert.deepEqual(plan([r({ servings: 6 })], [imported(r(), { hasSource: false })]).skippedAdminManual, ["lentejas"]);
  assert.deepEqual(plan([r({ servings: 6 })], [imported(r(), { updatedAtMillis: 2000 })]).skippedEditedInApp, ["lentejas"]);
});

test("informa de huérfanas y de comunes creadas a mano, sin contar las privadas del admin", () => {
  const p = plan([r()], [
    imported(r({ id: "vieja" })),
    imported(r({ id: "manual" }), { hasSource: false }),
    imported(r({ id: "privada" }), { hasSource: false, visibility: "PRIVATE" }),
    imported(r({ id: "de-usuario" }), { ownerUid: "u1", hasSource: false }),
  ]);
  assert.deepEqual(p.orphaned, ["vieja"]);
  assert.deepEqual(p.manualGlobal, ["manual"]);
});

test("el texto del plan no menciona versión y lista las comunes manuales", () => {
  const p = plan([r()], [imported(r({ id: "manual" }), { hasSource: false })]);
  const text = formatRecipePlan(p);
  assert.match(text, /^Plan de importación de recetas/);
  assert.match(text, /Crear: 1/);
  assert.doesNotMatch(text, /versión/);
  assert.match(text, /Comunes creadas a mano en la app \(no se tocan\): 1\n  - manual/);
});
```

- [ ] **Step 2: Escribir el test del importador `T/recipe-import.emulator.test.mjs`:**

```js
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { beforeEach, test } from "node:test";
import { Timestamp } from "firebase-admin/firestore";
import { CATALOG_ADMIN_UID } from "../scripts/catalog/import-core.mjs";
import { runRecipeImport } from "../scripts/catalog/import-recipes.mjs";
import { quiet, setupEmulatorDb } from "./helpers/emulator-db.mjs";

const ingredients = JSON.parse(await readFile(new URL("../data/catalog/ingredients.json", import.meta.url), "utf8"));
const full = JSON.parse(await readFile(new URL("../data/catalog/recipes.json", import.meta.url), "utf8"));
const small = { schemaVersion: 1, recipes: [
  { title: "Prueba lentejas", category: "Legumbres", description: "Receta de prueba.", servings: 4, totalMinutes: 60,
    ingredients: [
      { name: "Lentejas pardinas", quantity: "300", unit: "g" },
      { name: "Ajos", quantity: "2", unit: "dientes" },
      { name: "Sal" },
    ],
    steps: ["Cuece las lentejas.", "Sirve."] },
  { title: "Prueba tortilla", category: "Huevos", description: "Receta de prueba.", servings: 2, totalMinutes: 20,
    ingredients: [
      { name: "Huevos", quantity: "4", unit: "ud" },
      { name: "Aceite de oliva virgen extra", quantity: "1/2", unit: "cucharadita" },
    ],
    steps: ["Bate los huevos.", "Cuaja la tortilla."] },
] };

const db = setupEmulatorDb("recipe-import-test");
beforeEach(async () => {
  await db.recursiveDelete(db.collection("recipes"));
  await db.recursiveDelete(db.collection("catalogMeta"));
});
const run = (catalog, apply = true) => runRecipeImport({ db, catalog, ingredients, apply, log: quiet });

test("importa como comunes del catálogo, con la forma de la app, y una segunda pasada no escribe", async () => {
  const first = await run(small);
  assert.equal(first.create.length, 2);
  const d = (await db.doc("recipes/prueba-lentejas").get()).data();
  assert.equal(d.visibility, "GLOBAL");
  assert.equal(d.ownerUid, CATALOG_ADMIN_UID);
  assert.equal(d.createdBy, CATALOG_ADMIN_UID);
  assert.equal(d.createdByName, "Catálogo Bobitos");
  assert.equal(d.source.provider, "bobitos");
  assert.equal(d.updatedAt.toMillis(), d.source.importedAt.toMillis());
  assert.equal(d.category, "Legumbres");
  assert.equal(d.servings, 4);
  assert.ok(Number.isInteger(d.totalMinutes));
  // Cantidades como texto y nombre canónico («Ajos» → «Ajo»); la sal, sin cantidad ni unidad.
  assert.deepEqual(d.ingredients, [
    { name: "Lentejas pardinas", quantity: "300", unit: "g" },
    { name: "Ajo", quantity: "2", unit: "dientes" },
    { name: "Sal", quantity: null, unit: null },
  ]);
  assert.deepEqual(d.steps, ["Cuece las lentejas.", "Sirve."]);
  assert.equal("sourceRecipeId" in d, false);
  const second = await run(small);
  assert.equal(second.create.length + second.update.length, 0);
  assert.equal(second.unchanged.length, 2);
  assert.equal((await db.collection("catalogMeta").get()).size, 0);
});

test("la simulación no escribe nada", async () => {
  const r = await run(small, false);
  assert.equal(r.create.length, 2);
  assert.equal((await db.collection("recipes").get()).size, 0);
});

test("un cambio en el JSON actualiza solo esa receta", async () => {
  await run(small);
  const [a, b] = small.recipes;
  const r = await run({ ...small, recipes: [{ ...a, steps: [...a.steps, "Reposa 5 minutos."] }, b] });
  assert.deepEqual(r.update.map((e) => e.id), ["prueba-lentejas"]);
});

test("no pisa una común editada en la app ni la receta de un usuario con el mismo id", async () => {
  await run(small);
  await db.doc("recipes/prueba-lentejas").update({ title: "Editada", updatedAt: Timestamp.fromMillis(Date.now() + 60_000) });
  await db.doc("recipes/prueba-tortilla").delete();
  await db.doc("recipes/prueba-tortilla").set({ ownerUid: "u1", visibility: "PRIVATE", title: "Mía" });
  const r = await run(small);
  assert.deepEqual(r.skippedEditedInApp, ["prueba-lentejas"]);
  assert.deepEqual(r.skippedUserOwned, [{ id: "prueba-tortilla", ownerUid: "u1" }]);
  assert.equal((await db.doc("recipes/prueba-lentejas").get()).get("title"), "Editada");
  assert.equal((await db.doc("recipes/prueba-tortilla").get()).get("title"), "Mía");
});

test("una común borrada y recreada en la app con el mismo id («Deshacer») se omite y no se duplica", async () => {
  await run(small);
  const ref = db.doc("recipes/prueba-lentejas");
  const { source: _source, ...withoutSource } = (await ref.get()).data();
  await ref.delete();
  await ref.set({ ...withoutSource, createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
  const r = await run(small);
  assert.deepEqual(r.skippedAdminManual, ["prueba-lentejas"]);
  assert.equal(r.create.length, 0);
  assert.equal((await db.collection("recipes").get()).size, 2);
});

test("informa de las comunes creadas a mano y no las toca", async () => {
  await db.doc("recipes/manual1").set({ ownerUid: CATALOG_ADMIN_UID, visibility: "GLOBAL", title: "Cocido" });
  const r = await run(small);
  assert.deepEqual(r.manualGlobal, ["manual1"]);
  assert.equal((await db.doc("recipes/manual1").get()).get("title"), "Cocido");
});

test("rechaza un catálogo inválido sin escribir", async () => {
  const bad = { ...small, recipes: [{ ...small.recipes[0], ingredients: [{ name: "Unicornio", quantity: "1", unit: "ud" }] }] };
  await assert.rejects(run(bad), /Catálogo inválido/);
  assert.equal((await db.collection("recipes").get()).size, 0);
});

test("importa el catálogo completo en un solo pase", async () => {
  const r = await run(full);
  assert.equal(r.create.length, full.recipes.length);
  assert.equal((await db.collection("recipes").get()).size, full.recipes.length);
});
```

- [ ] **Step 3: `package.json`:** añadir `tests/catalog-recipe-import-plan.test.mjs` al final de `test:scripts`; añadir `tests/recipe-import.emulator.test.mjs` al final de la lista de ficheros de `test:catalog-import` (dentro de las comillas escapadas, después de `tests/routine-import.emulator.test.mjs`); añadir, después de `"catalog:import-routines"`, el script `"catalog:import-recipes": "node scripts/catalog/import-recipes.mjs"`.

- [ ] **Step 4: Ejecutar NODE y verlo en rojo** (faltan `recipe-import-plan.mjs` e `import-recipes.mjs`). Copia el fallo.

- [ ] **Step 5: Añadir al final de `SC/import-core.mjs`:**

```js
/**
 * Plan de una colección que comparten las comunes del catálogo y las fichas de los usuarios (rutinas, recetas):
 * las de los usuarios (`ownerUid` ≠ admin) nunca se tocan. Añade `manualGlobal`: comunes del admin sin `source`
 * y con un id que no está en el JSON (creadas a mano en la app); se informan y nunca se tocan. Cada documento de
 * `existing` lleva además su `visibility`.
 */
export function planGlobalCatalogImport({ entries, existing, managedFields, adminUid }) {
  const isForeign = (d) => d.ownerUid !== adminUid;
  const plan = planCatalogImport({ entries, existing, managedFields, isForeign });
  const ids = new Set(entries.map((e) => e.id));
  plan.manualGlobal = existing
    .filter((d) => !isForeign(d) && !d.hasSource && d.visibility === "GLOBAL" && !ids.has(d.id))
    .map((d) => d.id);
  return plan;
}

/** Texto del plan de `planGlobalCatalogImport`: sin versión (estas colecciones se leen en vivo). */
export function formatGlobalCatalogPlan(plan, title) {
  return [
    ...formatImportPlan(plan, { title, metaPath: null }),
    ...section("Comunes creadas a mano en la app (no se tocan)", plan.manualGlobal),
  ].join("\n");
}
```

- [ ] **Step 6: Hacer que `SC/routine-import-plan.mjs` delegue** (misma firma y misma salida). Sustituye `planRoutineImport` y `formatRoutinePlan` por:

```js
/** Plan genérico + `manualGlobal` (ver `planGlobalCatalogImport`). */
export function planRoutineImport({ routines, existing, adminUid }) {
  return planGlobalCatalogImport({ entries: routines, existing, managedFields: routineManagedFields, adminUid });
}

export function formatRoutinePlan(plan) {
  return formatGlobalCatalogPlan(plan, "Plan de importación de rutinas");
}
```

  y deja el import de cabecera así (sin `formatImportPlan`, `planCatalogImport` ni `section`, que ya no usa):

```js
import { CATALOG_ADMIN_UID, baseExisting, formatGlobalCatalogPlan, planGlobalCatalogImport, stampCatalogDoc } from "./import-core.mjs";
```

- [ ] **Step 7: Escribir `SC/recipe-import-plan.mjs`:**

```js
// Plan de importación del catálogo de recetas. Puro: no importa firebase-admin (lo usan los tests de reglas).
// `now` llega ya construido (FieldValue.serverTimestamp() o un Timestamp). Las recetas comunes son GLOBAL y del
// admin del catálogo; las de los usuarios comparten colección y nunca se tocan.
import { CATALOG_ADMIN_UID, baseExisting, formatGlobalCatalogPlan, planGlobalCatalogImport, stampCatalogDoc } from "./import-core.mjs";
import { CATALOG_AUTHOR_NAME } from "./import-plan.mjs";

export const RECIPES_COLLECTION = "recipes";

/** Campos que gestiona el importador, con la forma que escribe la app (FirestoreRecipeRepository). */
export function recipeManagedFields(entry) {
  return {
    title: entry.title,
    description: entry.description,
    category: entry.category,
    servings: entry.servings,
    totalMinutes: entry.totalMinutes,
    ingredients: entry.ingredients,
    steps: entry.steps,
    source: { provider: "bobitos" },
  };
}

/** Documento de Firestore → `ExistingDoc` de `planRecipeImport` (con `visibility`, para las manuales). */
export function recipeDocToExisting(id, data, { updatedAtMillis, importedAtMillis }) {
  const s = data.source;
  return {
    ...baseExisting(id, data, { updatedAtMillis, importedAtMillis }, {
      title: data.title,
      description: data.description ?? null,
      category: data.category ?? null,
      servings: data.servings ?? null,
      totalMinutes: data.totalMinutes ?? null,
      ingredients: data.ingredients ?? [],
      steps: data.steps ?? [],
      source: s ? { provider: s.provider } : undefined,
    }),
    visibility: data.visibility,
  };
}

export function recipeToFirestoreDoc(entry, { now, create = false }) {
  const doc = stampCatalogDoc(recipeManagedFields(entry), { now, adminUid: CATALOG_ADMIN_UID });
  if (!create) return doc;
  return {
    ...doc,
    visibility: "GLOBAL",
    ownerUid: CATALOG_ADMIN_UID,
    createdBy: CATALOG_ADMIN_UID,
    createdByName: CATALOG_AUTHOR_NAME,
    createdAt: now,
  };
}

/** Plan genérico + `manualGlobal` (ver `planGlobalCatalogImport`). */
export function planRecipeImport({ recipes, existing, adminUid }) {
  return planGlobalCatalogImport({ entries: recipes, existing, managedFields: recipeManagedFields, adminUid });
}

export function formatRecipePlan(plan) {
  return formatGlobalCatalogPlan(plan, "Plan de importación de recetas");
}
```

- [ ] **Step 8: Escribir `SC/import-recipes.mjs`:**

```js
// Importa data/catalog/recipes.json a Firestore (colección recipes, comunes GLOBAL) con firebase-admin.
// Idempotente; nunca borra; sin catalogMeta (el Recetario lee las recetas en vivo).
// Uso: node scripts/catalog/import-recipes.mjs --project demo-bobitos|bobitos-dev|dev [--apply] [--catalog ruta]
import { readFileSync } from "node:fs";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { CATALOG_ADMIN_UID, commitCatalogOps, loadExisting } from "./import-core.mjs";
import { connectAdmin, parseImportArgs, runMain } from "./admin-cli.mjs";
import {
  RECIPES_COLLECTION, formatRecipePlan, planRecipeImport, recipeDocToExisting, recipeToFirestoreDoc,
} from "./recipe-import-plan.mjs";
import { buildRecipes, indexIngredients, validateRecipeCatalog } from "./recipes.mjs";

const INGREDIENTS = new URL("../../data/catalog/ingredients.json", import.meta.url);

/** `ingredients`: el contenido de ingredients.json (para validar y escribir el nombre canónico). */
export async function runRecipeImport({ db, catalog, ingredients, apply, log = console.log }) {
  // Antes de leer nada de Firestore.
  const index = indexIngredients(ingredients);
  const problems = validateRecipeCatalog(catalog, index);
  if (problems.length) throw new Error(`Catálogo inválido:\n${problems.join("\n")}`);
  const recipes = buildRecipes(catalog, index);

  const { existing, updateTimes } = await loadExisting(db, RECIPES_COLLECTION, recipeDocToExisting);
  const plan = planRecipeImport({ recipes, existing, adminUid: CATALOG_ADMIN_UID });
  log(formatRecipePlan(plan));
  if (!apply) {
    log("\nSimulación: no se ha escrito nada. Usa --apply para aplicar.");
    return plan;
  }

  // Nunca se borra ninguna receta.
  await commitCatalogOps({
    db, FieldValue, collection: RECIPES_COLLECTION, plan, toDoc: recipeToFirestoreDoc, updateTimes,
    metaPath: null, adminUid: CATALOG_ADMIN_UID, now: FieldValue.serverTimestamp(),
  });
  log(`\nAplicado: ${plan.create.length} creadas, ${plan.update.length} actualizadas.`);
  return plan;
}

await runMain(import.meta.url, async () => {
  const args = parseImportArgs(process.argv.slice(2), { defaultCatalog: "data/catalog/recipes.json" });
  const app = connectAdmin(args.project);
  const catalog = JSON.parse(readFileSync(args.catalog, "utf8"));
  const ingredients = JSON.parse(readFileSync(INGREDIENTS, "utf8"));
  await runRecipeImport({ db: getFirestore(app), catalog, ingredients, apply: args.apply });
});
```

- [ ] **Step 9: Ejecutar NODE y verlo en verde** (incluidos `catalog-routine-import-plan.test.mjs` y `catalog-routines.test.mjs`, sin tocarlos).

- [ ] **Step 10: Ejecutar IMP y verlo en verde** (incluido `routine-import.emulator.test.mjs`, sin tocarlo). Copia el resumen.

- [ ] **Step 11: Documentación.**
  1. **`DC/LICENSE.md`:** en el primer párrafo, «Los ingredientes tienen su propia sección más abajo.» → «Los ingredientes, las rutinas y las recetas tienen su propia sección más abajo.». Añade al final:

```markdown
## Recetas

`recipes.json` es una lista propia de Bobitos: la selección de recetas, las cantidades, los pasos y los textos se han redactado para este catálogo y no derivan de ninguna base de datos ni web de recetas. Cada ingrediente usa el nombre de `ingredients.json`; la licencia CC BY-SA del principio de este fichero se refiere a las fichas de ejercicios, no a las recetas.
```

  2. **Crear `docs/RECIPE_CATALOG.md`** con este contenido:

````markdown
# Catálogo común de recetas

El catálogo común de recetas (`recipes/{slug}`) se siembra con 60 recetas caseras de una lista propia de Bobitos. Este documento explica cómo se edita, se valida y se importa.

## Qué es

- 60 **recetas comunes** (`recipes/{slug}`, `visibility: GLOBAL`, dueño el admin del catálogo, autor «Catálogo Bobitos») que viven en la **misma colección** que las recetas personales de los usuarios. Las crea **solo el admin**, con el importador.
- Once categorías por tipo de plato: Legumbres, Arroces, Pasta, Carnes, Pescados, Verduras, Ensaladas, Huevos, Sopas y cremas, Desayunos y meriendas, y Postres.
- Cada ingrediente usa el nombre de un ingrediente del catálogo común (`ingredients.json`), para que «Añadir a la compra» reconozca la marca y la tienda, y una unidad de una lista cerrada, para que las cantidades se puedan sumar.
- El diseño completo está en [`superpowers/specs/2026-10-12-recetario-y-compra-design.md`](superpowers/specs/2026-10-12-recetario-y-compra-design.md) (parte C).

## Fuente y licencia

- Fuente: lista propia de Bobitos. No hay datos de terceros.
- La licencia está en `data/catalog/LICENSE.md` (sección «Recetas»).

## Estructura de `data/catalog/`

| Fichero | Qué es | ¿Se edita a mano? |
| --- | --- | --- |
| `recipes.json` | La lista, la que se importa a Firestore. | Sí |
| `recipes-review.md` | Informe legible para revisar la lista. | No (`catalog:recipes`) |

## Formato

```json
{
  "schemaVersion": 1,
  "recipes": [
    {
      "title": "Lentejas estofadas",
      "category": "Legumbres",
      "description": "Las de toda la vida, con chorizo y verduras.",
      "servings": 4,
      "totalMinutes": 60,
      "ingredients": [
        { "name": "Lentejas pardinas", "quantity": "300", "unit": "g" },
        { "name": "Ajo", "quantity": "2", "unit": "dientes" },
        { "name": "Sal" }
      ],
      "steps": ["Pon las lentejas en una olla…", "…"]
    }
  ]
}
```

- Todas las claves de la receta son obligatorias. El `id` del documento es el *slug* del `title` (`slug()` de `scripts/catalog/normalize.mjs`, igual que el de la app): cambiar el título crea otra receta y deja huérfana la anterior.
- `totalMinutes` no cuenta el remojo ni el tiempo de nevera; si la receta los necesita, la entradilla (`description`) lo avisa.
- Un ingrediente sin `quantity` es «al gusto» y no lleva `unit`. El agua no se lista.

## Unidades

Si hay cantidad, la unidad es obligatoria y de esta lista: `g`, `kg`, `ml`, `l`, `ud` (no cambian) y cucharada, cucharadita, pizca, diente, rebanada, hoja y ramita, en singular si la cantidad vale 1 o menos («1 diente», «1/2 cucharadita») y en plural si pasa de 1 («2 dientes»). La cantidad es texto: un entero, un decimal con coma («1,5») o una fracción («1/2»), mayor que cero. La tabla está en `tests/fixtures/recipe-units.json` y un test de la app comprueba que la Compra suma todas.

## Validación

`npm run catalog:recipes` valida la lista y escribe `recipes-review.md`. Si hay problemas, los imprime y termina con error sin escribir nada. El importador aplica la misma validación antes de leer Firestore. Reglas:

- cada ingrediente existe en `ingredients.json` (con la clave tolerante `ingredientKey`: «Ajos» vale por «Ajo») y se escribe con el nombre del catálogo; si no existe, el error sugiere el más parecido; una receta no repite un ingrediente;
- categoría de las once; título de 1 a 120 caracteres y con mayúscula inicial; títulos (e ids) únicos;
- límites (`RECIPE_LIMITS` en `scripts/catalog/recipes.mjs`): entradilla 1–1000 caracteres, 1–50 ingredientes, 1–30 pasos de hasta 500 caracteres, `servings` 1–50, `totalMinutes` 1–1440.

Si una receta necesita un ingrediente genérico que no está en el catálogo, se añade a `ingredients.json` (ver [`INGREDIENT_CATALOG.md`](INGREDIENT_CATALOG.md)) y se importan **los ingredientes antes que las recetas**.

## Revisión

`recipes-review.md` agrupa las recetas por categoría, con su id, raciones y tiempo, la entradilla, los ingredientes («Lentejas pardinas — 300 g», «Sal — al gusto») y los pasos numerados. Se revisa antes de cualquier importación.

## Importar

El importador es `scripts/catalog/import-recipes.mjs` (`npm run catalog:import-recipes --`). Usa `firebase-admin`, que se salta las reglas de seguridad. Admite `--project demo-bobitos|bobitos-dev|dev`, `--apply` (sin él solo **simula**) y `--catalog ruta`. Las reglas de `recipes` que admiten `steps`, `servings`, `totalMinutes` y `source` tienen que estar desplegadas antes.

### En el emulador

```bash
npm run emulators            # en otra terminal
FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 npm run catalog:import-recipes -- --project demo-bobitos [--apply]
```

### En `bobitos-dev`

```bash
GOOGLE_APPLICATION_CREDENTIALS=~/.config/bobitos/bobitos-dev-adminsdk.json npm run catalog:import-recipes -- --project dev [--apply]
```

Primero la simulación; si el plan es el esperado, repetir con `--apply`. Las protecciones de proyecto son las de los demás importadores (`scripts/catalog/admin-cli.mjs`).

### Qué hace con cada documento existente

- **Crea** las recetas que no existen y **actualiza** las importadas cuyo contenido ha cambiado; las idénticas quedan **sin cambios**.
- **Omite** las recetas de **usuarios** con el mismo id, las comunes del admin **sin `source`** (creadas a mano) y las comunes **editadas en la app** después de importarse.
- **Nunca borra.** Las **huérfanas** (ya no están en el JSON) y las «**Comunes creadas a mano en la app**» (GLOBAL del admin sin `source` y con otro id) solo se informan, para que el usuario decida.
- **No usa `catalogMeta`**: el Recetario lee las recetas en vivo, así que no hay versión de catálogo que subir.

### Avisos

- Editar una común importada en la app (aunque sea guardarla sin cambios) la **congela**: el importador ya no la actualiza.
- Borrar una común solo en la app no basta: la siguiente importación la vuelve a crear mientras siga en `recipes.json`. Para retirarla, quítala también del JSON.
````

  3. **`docs/DATA_MODEL.md`**, sección «Recetario (colección `recipes`)»: en el bloque de campos, sustituye la línea `ingredients: array<Ingredient>?        # embebido, opcional, ≤50 (Fase 3)` por estas (alineadas con las vecinas):

```text
sourceUrl: string?                     # web de la que se importó (importador web)
ingredients: array<Ingredient>?        # embebido, opcional, ≤50 (Fase 3)
steps: array<string>?                  # pasos de la preparación, ≤30 de ≤500 caracteres
servings: int?                         # raciones, 1–50
totalMinutes: int?                     # tiempo total en minutos, 1–1440
source: map?                           # solo comunes del catálogo: {provider: "bobitos", importedAt}
```

  y, después del párrafo «Cada `Ingredient` es un mapa embebido…», añade:

```markdown
Las recetas antiguas no tienen `steps`, `servings` ni `totalMinutes` (sus pasos están en la descripción) y se leen igual; editarlas añade `steps: []` y los dos números a `null`.

- **Catálogo común importado:** unas 60 comunes de lista propia (`recipes/{slug}`, autor «Catálogo Bobitos», con `source`), que crea el importador con el Admin SDK (ver [`RECIPE_CATALOG.md`](RECIPE_CATALOG.md)). Ningún cliente puede crear ni cambiar `source`; el admin puede editar el resto de campos de una común en la app, y entonces el importador deja de actualizarla. Cada ingrediente del catálogo usa el nombre canónico de `ingredients` y una unidad de lista cerrada.
```

  En la sección «8. Lista de la compra», al final del párrafo «**Artículos repetidos.** …», añade: «Antes de la compra sin duplicados, la revisión de una receta escribía la unidad en `notes`; esos artículos no se migran.».

  4. **`docs/RECIPES_ADMIN.md`:**
     - En el punto 3 de «Quién es admin», «crea y reconoce las fichas» → «crea y reconoce las fichas (y el de los importadores de ingredientes, rutinas y recetas)».
     - En «## Sembrar el catálogo común», añade antes de la lista numerada (que se queda como está) el párrafo: «El catálogo común se siembra con el importador: unas 60 recetas de lista propia, con el autor «Catálogo Bobitos» (ver [`RECIPE_CATALOG.md`](RECIPE_CATALOG.md)). También se puede publicar una común a mano desde la app:».
     - Al final del párrafo «Para **corregir o retirar** una receta común…», añade: «Si es una común importada, editarla en la app la congela (el importador ya no la actualiza) y borrarla solo en la app no basta: la siguiente importación la recrea mientras siga en `recipes.json` (ver los avisos de `RECIPE_CATALOG.md`).».

- [ ] **Step 12: Commit**

```bash
git add scripts/catalog/import-core.mjs scripts/catalog/routine-import-plan.mjs scripts/catalog/recipe-import-plan.mjs scripts/catalog/import-recipes.mjs tests/catalog-recipe-import-plan.test.mjs tests/recipe-import.emulator.test.mjs package.json data/catalog/LICENSE.md docs/RECIPE_CATALOG.md docs/DATA_MODEL.md docs/RECIPES_ADMIN.md
git commit -m "feat(catalogo): importador de recetas comunes y documentación del catálogo"
```

---

### Task 4: «Deshacer» con el mismo id, reglas de las comunes importadas y unidades que suman

**Ficheros:**
- Modificar: `APP/data/repository/RecipeRepository.kt`, `APP/data/repository/FirestoreRecipeRepository.kt`, `APP/feature/recipes/RecipesViewModel.kt` (`restoreRecipe`, ~l.232-245).
- Modificar: `UT/feature/recipes/RecipesViewModelTest.kt` (dos tests existentes y dos nuevos; el doble `FakeRecipeRepository`), `UT/feature/meals/MealsViewModelTest.kt` (su `FakeRecipeRepository`).
- Crear: `UT/core/model/RecipeUnitsFixtureTest.kt`.
- Modificar: `T/firebase-emulators.test.mjs` (import y dos tests nuevos después del test «el admin edita una receta común del catálogo y su fuente se conserva»).
- Modificar: `docs/RECIPE_CATALOG.md` y `docs/DATA_MODEL.md` (una nota sobre «Deshacer»).

**Interfaces:**
- Consume: `recipeToFirestoreDoc(entry, { now, create })` (Tarea 3, `SC/recipe-import-plan.mjs`); `T/fixtures/recipe-units.json` (Tarea 1); `ShoppingQuantity.sum` (`APP/core/model/ShoppingQuantity.kt`); helpers del test de reglas `recipeData`, `verifiedFirestore`, `RECIPE_ADMIN_UID`, `testEnvironment`.
- Produce: `RecipeRepository.restoreRecipe(recipe: Recipe)` (recrea con `recipe.id`); `RecipesViewModel.restoreRecipe(recipe)` la usa.

- [ ] **Step 1: Tests del ViewModel.** En `UT/feature/recipes/RecipesViewModelTest.kt`:
  1. En `FakeRecipeRepository`, añade la propiedad `var restored: Recipe? = null` (junto a `lastUpdatedId`) y, después de `deleteRecipe`:

```kotlin
    override suspend fun restoreRecipe(recipe: Recipe) {
        nextFailure?.let { throw it }
        restored = recipe
    }
```

  2. En el test `restoreRecipe does not touch the editor status`, cambia `assertEquals("Tortilla", repository.lastTitle)` por `assertEquals("Tortilla", repository.restored?.title)`.
  3. Sustituye el test `undoing a delete recreates the steps, servings and minutes` entero por estos dos:

```kotlin
    @Test
    fun `undoing a delete recreates the same recipe under its own id`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val deleted = recipe(
                "lentejas-estofadas",
                RecipeVisibility.PRIVATE,
                "Lentejas",
                listOf(Ingredient("Lentejas", "300", "g")),
            ).copy(
                steps = listOf("Remoja.", "Cuece."),
                servings = 4,
                totalMinutes = 60,
                sourceRecipeId = "lentejas-comunes",
                sourceUrl = "https://example.com/lentejas",
                createdByName = "Catálogo Bobitos",
            )

            viewModel.restoreRecipe(deleted)
            advanceUntilIdle()

            assertEquals(deleted, repository.restored)
            assertEquals(0, repository.createCount)
        }

    @Test
    fun `undoing the delete of a common keeps it common only for the admin`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val common = recipe("torrijas", RecipeVisibility.GLOBAL, "Torrijas")

            viewModel.restoreRecipe(common)
            advanceUntilIdle()
            assertEquals(RecipeVisibility.PRIVATE, repository.restored?.visibility)

            repository.admin = true
            viewModel.observe()
            advanceUntilIdle()
            viewModel.restoreRecipe(common)
            advanceUntilIdle()
            assertEquals(RecipeVisibility.GLOBAL, repository.restored?.visibility)
            assertEquals("torrijas", repository.restored?.id)
        }
```

  4. En `UT/feature/meals/MealsViewModelTest.kt`, en su `FakeRecipeRepository`, después de `deleteRecipe`: `override suspend fun restoreRecipe(recipe: Recipe) = Unit`.

- [ ] **Step 2: Test de unidades `UT/core/model/RecipeUnitsFixtureTest.kt`:**

```kotlin
package com.dlunaunizar.bobitos.core.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RecipeUnitsFixtureTest {
    // La misma tabla que scripts/catalog/recipes.mjs (RECIPE_UNITS): las cantidades de las recetas del catálogo se
    // suman al «Añadir a la compra» dos veces y la unidad concuerda en número con el total.
    private val units = File("../tests/fixtures/recipe-units.json").also {
        assertTrue("falta la tabla compartida en ${it.absolutePath}", it.exists())
    }.readText().let(::JSONObject)

    @Test
    fun `abbreviated units add up and do not change`() {
        val invariable = units.getJSONArray("invariable")
        for (i in 0 until invariable.length()) {
            val unit = invariable.getString(i)
            assertEquals(unit, "3 $unit", ShoppingQuantity.sum("1 $unit", "2 $unit"))
        }
    }

    @Test
    fun `counted units add up and agree in number with the total`() {
        val counted = units.getJSONArray("counted")
        for (i in 0 until counted.length()) {
            val singular = counted.getJSONObject(i).getString("singular")
            val plural = counted.getJSONObject(i).getString("plural")
            assertEquals(singular, "3 $plural", ShoppingQuantity.sum("1 $singular", "2 $plural"))
            assertEquals(singular, "3 $plural", ShoppingQuantity.sum("2 $plural", "1 $singular"))
            assertEquals(singular, "1 $singular", ShoppingQuantity.sum("1/2 $singular", "1/2 $singular"))
        }
    }
}
```

  Este test fija un comportamiento que ya existe (plan A): se espera **en verde** desde el principio. Si sale en rojo, para y cuéntalo en el informe; no cambies `ShoppingQuantity`.

- [ ] **Step 3: Ejecutar JVM de `com.dlunaunizar.bobitos.feature.recipes.RecipesViewModelTest` y verlo en rojo** (no compila: `restoreRecipe` no existe en `RecipeRepository`). Copia el fallo.

- [ ] **Step 4: Repositorio.** En `APP/data/repository/RecipeRepository.kt`, después de `deleteRecipe`:

```kotlin
    /**
     * Recrea con su mismo id una receta recién borrada («Deshacer»), con sus datos y su autor. No lleva `source`:
     * las reglas no dejan que un cliente lo escriba, así que una común del catálogo vuelve como común manual.
     */
    suspend fun restoreRecipe(recipe: Recipe)
```

  En `APP/data/repository/FirestoreRecipeRepository.kt`, sustituye el cuerpo de `createRecipe` y añade `restoreRecipe` y el helper privado `newRecipeFields` (el mapa es el mismo que hoy escribe `createRecipe`, salvo el autor, que llega como parámetro):

```kotlin
    ) = runRecipeOperation {
        val user = requireVerifiedUser()
        val fields = newRecipeFields(
            user = user,
            createdByName = user.recipeDisplayName,
            visibility = visibility,
            title = title,
            description = description,
            category = category,
            sourceRecipeId = sourceRecipeId,
            sourceUrl = sourceUrl,
            ingredients = ingredients,
            steps = steps,
            servings = servings,
            totalMinutes = totalMinutes,
        )
        recipesCollection().document().set(fields).await()
        Unit
    }

    override suspend fun restoreRecipe(recipe: Recipe) = runRecipeOperation {
        val user = requireVerifiedUser()
        // Conserva el autor que se veía («Catálogo Bobitos» en las comunes importadas).
        val author = recipe.createdByName.trim().take(MAX_AUTHOR_NAME_LENGTH).ifEmpty { user.recipeDisplayName }
        val fields = newRecipeFields(
            user = user,
            createdByName = author,
            visibility = recipe.visibility,
            title = recipe.title,
            description = recipe.description,
            category = recipe.category,
            sourceRecipeId = recipe.sourceRecipeId,
            sourceUrl = recipe.sourceUrl,
            ingredients = recipe.ingredients.orEmpty(),
            steps = recipe.steps.orEmpty(),
            servings = recipe.servings,
            totalMinutes = recipe.totalMinutes,
        )
        recipesCollection().document(recipe.id).set(fields).await()
        Unit
    }
```

```kotlin
    // Documento de una receta nueva o recreada: valida y limpia como al crear; dueño y fechas, los del momento.
    private fun newRecipeFields(
        user: AuthUser,
        createdByName: String,
        visibility: RecipeVisibility,
        title: String,
        description: String?,
        category: String?,
        sourceRecipeId: String?,
        sourceUrl: String?,
        ingredients: List<Ingredient>,
        steps: List<String>,
        servings: Int?,
        totalMinutes: Int?,
    ): Map<String, Any?> {
        val values = validate(title, description, category)
        val cleanSteps = checkedSteps(steps, servings, totalMinutes)
        return mapOf(
            FIELD_OWNER_UID to user.id,
            FIELD_VISIBILITY to visibility.name,
            FIELD_TITLE to values.title,
            FIELD_DESCRIPTION to values.description,
            FIELD_CATEGORY to values.category,
            FIELD_SOURCE_RECIPE_ID to sourceRecipeId,
            FIELD_SOURCE_URL to sourceUrl?.trim()?.takeIf(String::isNotEmpty),
            FIELD_INGREDIENTS to ingredients.toFirestore(),
            FIELD_STEPS to cleanSteps,
            FIELD_SERVINGS to servings,
            FIELD_TOTAL_MINUTES to totalMinutes,
            FIELD_CREATED_BY to user.id,
            FIELD_CREATED_BY_NAME to createdByName,
            FIELD_CREATED_AT to FieldValue.serverTimestamp(),
            FIELD_UPDATED_BY to user.id,
            FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
        )
    }
```

  Pon `restoreRecipe` después de `deleteRecipe`, `newRecipeFields` junto a `checkedSteps`, y añade al `companion object` `const val MAX_AUTHOR_NAME_LENGTH = 60` (el máximo de `createdByName` en las reglas).

- [ ] **Step 5: ViewModel.** En `APP/feature/recipes/RecipesViewModel.kt`, sustituye `restoreRecipe` (comentario incluido) por:

```kotlin
    // Recrea la receta borrada («Deshacer») con su mismo id: el importador del catálogo no duplica una común
    // recuperada y las comidas que la enlazaban la vuelven a encontrar. No es un guardado del editor, no mueve
    // su estado.
    fun restoreRecipe(recipe: Recipe) = runAction(RecipeUiMessage.RecipeSaved) {
        repository.restoreRecipe(recipe.copy(visibility = safeVisibility(recipe.visibility)))
    }
```

- [ ] **Step 6: Ejecutar JVM de `RecipesViewModelTest` y de `com.dlunaunizar.bobitos.core.model.RecipeUnitsFixtureTest` y verlos en verde.**

- [ ] **Step 7: Tests de reglas.** En `T/firebase-emulators.test.mjs`, añade el import junto al de `routineToFirestoreDoc`:

```js
import { recipeToFirestoreDoc } from "../scripts/catalog/recipe-import-plan.mjs";
```

  y, después del test «el admin edita una receta común del catálogo y su fuente se conserva»:

```js
const importedRecipe = {
  id: "lentejas-importadas", title: "Lentejas estofadas", description: "Las de toda la vida.", category: "Legumbres",
  servings: 4, totalMinutes: 60,
  ingredients: [{ name: "Lentejas pardinas", quantity: "300", unit: "g" }, { name: "Sal", quantity: null, unit: null }],
  steps: ["Cuece las lentejas.", "Sirve."],
};

async function seedImportedRecipe(id) {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "recipes", id),
      recipeToFirestoreDoc({ ...importedRecipe, id }, { now: Timestamp.now(), create: true }));
  });
}

test("el admin edita en la app una receta del importador, pero no su fuente, y cualquiera la lee", async () => {
  await seedImportedRecipe("lentejas-importadas");
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  const ref = doc(admin, "recipes", "lentejas-importadas");
  await assertSucceeds(updateDoc(ref, {
    title: "Lentejas estofadas (editada)", description: "Otra", category: "Legumbres",
    ingredients: [{ name: "Lentejas pardinas", quantity: "400", unit: "g" }], steps: ["Cuece.", "Sirve."],
    servings: 6, totalMinutes: 75, updatedBy: RECIPE_ADMIN_UID, updatedAt: serverTimestamp(),
  }));
  await assertFails(updateDoc(ref, {
    source: { provider: "bobitos", importedAt: Timestamp.now() }, updatedBy: RECIPE_ADMIN_UID, updatedAt: serverTimestamp(),
  }));
  await assertSucceeds(getDoc(doc(verifiedFirestore("recipe-imported-reader"), "recipes", "lentejas-importadas")));
});

test("«Deshacer»: el admin recrea con el mismo id una común importada que acaba de borrar, sin su fuente", async () => {
  await seedImportedRecipe("lentejas-deshacer");
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  const ref = doc(admin, "recipes", "lentejas-deshacer");
  await assertSucceeds(writeBatch(admin).delete(ref).commit());
  const restored = recipeData(RECIPE_ADMIN_UID, {
    visibility: "GLOBAL", title: importedRecipe.title, description: importedRecipe.description,
    category: importedRecipe.category, ingredients: importedRecipe.ingredients, steps: importedRecipe.steps,
    servings: 4, totalMinutes: 60, sourceRecipeId: null, sourceUrl: null, createdByName: "Catálogo Bobitos",
  });
  await assertFails(setDoc(ref, { ...restored, source: { provider: "bobitos", importedAt: Timestamp.now() } }));
  await assertSucceeds(setDoc(ref, restored));
});
```

  Estos tests fijan reglas que ya existen (plan B): se esperan **en verde**. Si alguno sale en rojo, para y cuéntalo; no toques `firestore.rules`.

- [ ] **Step 8: Ejecutar EMU y verlo en verde.**

- [ ] **Step 9: Documentación de «Deshacer».**
  - En `docs/RECIPE_CATALOG.md`, al final de «### Avisos», añade: «- «Deshacer» tras borrar una común en la app la recrea con el **mismo id**, pero sin `source` (un cliente no puede escribirlo): el importador la omite como «ficha manual del admin» y ya no la actualiza; no se duplica. Para devolverla al catálogo, bórrala en la app sin deshacer y vuelve a importar.»
  - En `docs/DATA_MODEL.md`, al final del punto «**Catálogo común importado:** …» añadido en la Tarea 3, añade: ««Deshacer» tras borrar una receta la recrea con su mismo id (`restoreRecipe`), sin `source`.».

- [ ] **Step 10: Ejecutar GRADLE y verlo en verde.** Copia el resumen (tests JVM, ktlint y detekt).

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/data/repository/RecipeRepository.kt app/src/main/java/com/dlunaunizar/bobitos/data/repository/FirestoreRecipeRepository.kt app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipesViewModel.kt app/src/test/java/com/dlunaunizar/bobitos/feature/recipes/RecipesViewModelTest.kt app/src/test/java/com/dlunaunizar/bobitos/feature/meals/MealsViewModelTest.kt app/src/test/java/com/dlunaunizar/bobitos/core/model/RecipeUnitsFixtureTest.kt tests/firebase-emulators.test.mjs docs/RECIPE_CATALOG.md docs/DATA_MODEL.md
git commit -m "fix(recetas): «Deshacer» recrea la receta con su mismo id y no duplica las comunes del catálogo"
```

---

## Ejecución (`delegating-plan-execution`, plan grande)

1. Rama `agent/catalogo-recetas` (desde `main`, 1c9eea1), con este plan como primer commit.
2. Las tareas 1 a 4, una tras otra, cada una con un `sonnet` que recibe su brief. `task-done` con la suite de cada tarea: NODE en la 1 y la 2; NODE e IMP en la 3; JVM de las clases nombradas, EMU y GRADLE en la 4.
   - **Parada del usuario tras la Tarea 2** (revisión de `recipes-review.md`).
3. **Revisión final** con `opus`, más los arreglos. **Re-revisión acotada** si algún arreglo toca `firestore.rules` (permisos) o el aislamiento de las recetas privadas.
4. **`simplify`:** un revisor `sonnet` con los 4 ángulos y un aplicador `sonnet` de la lista (a).
5. **`code-review`** en nivel `high`, más los arreglos.
6. **Cierre:** menores al backlog; en `docs/superpowers/backlog.md`, sección `agent/receta-pasos`, el menor «**Efecto visible (plan C):** si el admin borra una receta común…» se marca como resuelto en `agent/catalogo-recetas`; decisión del usuario sobre la rama. El despliegue va después (sección final).

## Verificación de punta a punta (coordinador, al final)

- NODE, IMP, EMU y GRADLE en verde.
- **En el emulador:** `npm run emulators` (con `JAVA_HOME` de Android Studio) y `npm run seed:emulators`; crear en el emulador de Auth el usuario normal y el admin (uid `dWWH7eRhHEPopJf5BHPB3Dp6fry1`, correo verificado) con un script del scratchpad (Admin SDK con `FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099`) y ligarlos al espacio sembrado si hace falta (ver los menores de `seed:emulators` en el backlog). Importar: `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 node scripts/catalog/import-ingredients.mjs --project demo-bobitos --apply` y `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 node scripts/catalog/import-recipes.mjs --project demo-bobitos --apply`. Instalar la app debug.
- **Recorrido**, con capturas en tema claro y oscuro:
  1. Usuario normal: «Recetas comunes» muestra las 60, de autor «Catálogo Bobitos»; buscar «lentejas»; abrir «Lentejas estofadas»: «4 raciones · 60 min», la entradilla, los ingredientes con sus cantidades (la sal sin cantidad) y la «Preparación» numerada.
  2. «Guardar como mía» en «Garbanzos con espinacas»: la copia aparece en «Mis recetas» con pasos, raciones y tiempo.
  3. Comidas → nueva comida → «Elegir de una receta»: buscar «garbanzos» y elegir la común; guardar la comida.
  4. Compra: apuntar a mano «Ajo» con «1 diente» (pendiente). Desde la comida o la ficha, «Añadir a la compra»: «Ajo» sale como «Ya en la lista» con «Omitir» marcado; elegir «Sumar» → «4 dientes»; confirmar; en la lista, «Ajo · 4 dientes» y los demás ingredientes nuevos con su cantidad y unidad («Garbanzos cocidos · 800 g»).
  5. Admin: las 60 en «Comunes» (con Editar, Eliminar y «Guardar como mía») y no en «Mis recetas»; borrar «Torrijas» y pulsar «Deshacer»: vuelve a «Comunes», con autor «Catálogo Bobitos»; volver a importar en simulación: «Crear: 0» y «Omitidas (ficha manual del admin, sin fuente): 1 — torrijas».
  6. Admin: editar una común y reimportar en simulación: sale en «Omitidas (editadas en la app tras importarse)».

## Despliegue (tras el cierre; cada paso lo confirma el usuario)

Despliegue único de los planes A, B y C (spec, «Orden y despliegue»; trampas de la beta: `--project dev`, `JAVA_HOME` de Android Studio, `beta:distribute` sube el APK viejo si falla el build):

1. Reglas (las del plan B): `npx firebase deploy --only firestore:rules --project dev`.
2. Ingredientes (dos nuevos): `GOOGLE_APPLICATION_CREDENTIALS=~/.config/bobitos/bobitos-dev-adminsdk.json npm run catalog:import-ingredients -- --project dev` (simulación: «Crear: 2») y después con `--apply`.
3. Recetas: `GOOGLE_APPLICATION_CREDENTIALS=~/.config/bobitos/bobitos-dev-adminsdk.json npm run catalog:import-recipes -- --project dev` (simulación: «Crear: 60»; revisar «Comunes creadas a mano») y después con `--apply`.
4. Beta 21 (`VERSION_CODE=21`, `0.1.0-beta.21`) con notas de A, B y C en `distribution/release-notes.txt`, según `docs/BETA_DISTRIBUTION.md`.
