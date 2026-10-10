# Catálogo común de ingredientes (plan 1 de 3) — Plan de implementación

> **Para agentes:** se ejecuta con `delegating-plan-execution`. Es un **plan grande** (5 tareas), así que lleva los pasos 1 a 6, incluido `simplify`. Cada tarea la hace un subagente `sonnet`, en serie. La revisión final la hace `opus` (si falla, `fable`). Los pasos usan casillas (`- [ ]`).

**Objetivo:** tener listos, sin tocar la app, los datos y los scripts del catálogo común de ingredientes:
- unos 350 genéricos validados, con su fichero de revisión;
- un importador idempotente a `ingredients/{slug}` con versión en `catalogMeta/ingredients`;
- un script de un solo uso que borra los ingredientes, las marcas y las preferencias de la beta.

**Arquitectura:**
- El núcleo genérico del importador de ejercicios (plan de importación, formato del plan, escritura por lotes con subida de versión y protecciones de proyecto) se extrae a `import-core.mjs` y `admin-cli.mjs`. El importador de ejercicios lo reutiliza **sin cambiar su comportamiento**.
- Encima se montan:
  - `ingredients.mjs`: categorías, unidades, validación y fichero de revisión;
  - `ingredient-import-plan.mjs` (puro) e `import-ingredients.mjs`, el importador;
  - `reset-ingredients.mjs`, el borrado.

**Stack:** Node ≥20 en ESM, `node:test` con `node:assert/strict`, firebase-admin 13.10.0 y Firebase Emulator Suite. Sin dependencias nuevas.

**Spec:** `docs/superpowers/specs/2026-10-11-catalogo-ingredientes-design.md`. Este plan cubre la sección 3 («Catálogo e importador»), la parte de documentación del catálogo de la sección 6 y el plan 1 de la sección 8. Las reglas, la app y la Compra son de los planes 2 y 3.

## Restricciones globales

- **Rutas:**
  - `SC` = `scripts/catalog/`
  - `T` = `tests/`
  - `DC` = `data/catalog/`
- **Comandos de verificación**, en llamadas separadas y sin `sh -c`:
  - **NODE:** `npm run test:scripts`
  - **IMP:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && npm run test:catalog-import`
- **Proyectos y descargas:**
  - Prohibido ejecutar cualquier script contra `bobitos-dev` (`--project dev` o `bobitos-dev`).
  - Prohibido `npm run catalog:fetch`.
  - En este plan no se descarga nada.
- **Ejercicios sin cambios de comportamiento:**
  - los tests que ya existen en `T/catalog-*.test.mjs` y `T/catalog-import.emulator.test.mjs` **no se modifican** y siguen en verde;
  - `import-plan.mjs` mantiene todas sus exportaciones, con la misma firma y la misma salida.
- **Slug:** se reutiliza `slug()` de `SC/normalize.mjs`, que es idéntico a `slug()` de `app/src/main/java/com/dlunaunizar/bobitos/core/model/CatalogIngredient.kt`. El `id` de cada ingrediente es `slug(name)`.
- **Documento común `ingredients/{slug}`** (lo escribe el importador):
  - `name`, `nameLower` (`name.toLowerCase()`), `category`, `defaultUnit`;
  - `source: {provider: "bobitos", importedAt}`;
  - `createdAt` (solo al crear), `updatedAt` y `updatedBy` (= `CATALOG_ADMIN_UID`).
  - **Sin `ownerUid`, `createdBy` ni `createdByName`** (spec, sección 1).
- **Las 14 categorías**, con este texto y en este orden: «Frutas», «Verduras y hortalizas», «Carnes», «Pescados y mariscos», «Lácteos y huevos», «Legumbres», «Cereales, pasta y arroz», «Panadería», «Aceites, salsas y condimentos», «Especias y hierbas», «Frutos secos», «Dulces y chocolate», «Bebidas», «Congelados y otros».
- **Unidades:** `g`, `ml` o `ud`.
- **Textos y comentarios en español**, con el estilo de los scripts que ya existen.
- **Tests del emulador en serie:** los ficheros de `test:catalog-import` comparten el emulador de Firestore, así que se ejecutan con `node --test --test-concurrency=1`.

## Review Focus

1. **El borrado no se pasa de la raya:**
   - borra **solo** `ingredients` (con sus `brands`, incluidas las de documentos padre que no existen) e `ingredientPrefs`;
   - nunca toca `users/{uid}/…`, `exercises`, `exerciseImages`, `catalogMeta` ni `recipes`;
   - sin `--apply` no escribe nada.
2. **El proyecto equivocado:** el borrado y el importador comparten protecciones:
   - un proyecto `demo-*` exige el emulador;
   - `bobitos-dev` se rechaza si `FIRESTORE_EMULATOR_HOST` está definida, y exige una clave cuyo `project_id` sea `bobitos-dev`.
   - Nunca se puede borrar ni importar en un proyecto que no se ha pedido.
3. **Reimportar:**
   - sin cambios en el JSON no escribe nada y no sube la versión;
   - una ficha editada en la app después de importarla no se pisa;
   - un documento antiguo de usuario con el mismo id (con `ownerUid` de otro) se omite;
   - los ingredientes que ya no están en el JSON se informan y no se borran.
4. **Calidad del catálogo:**
   - nombres naturales de la lista de la compra en España, sin duplicados ni casi duplicados (singular y plural, incluidos los plurales en «-es», tildes y mayúsculas, con `ingredientKey`);
   - cada `id` es `slug(name)`;
   - categoría y unidad, dentro de su lista.
5. **Ejercicios no cambia:** tras extraer el núcleo, el importador de ejercicios produce el mismo plan, el mismo texto, el mismo orden de escritura (imágenes antes que fichas) y la misma subida de `catalogMeta/exercises`.

---

### Task 1: Extraer el núcleo genérico del importador

**Ficheros:**
- Crear:
  - `SC/import-core.mjs`: plan genérico, formato genérico y escritura por lotes;
  - `SC/admin-cli.mjs`: protecciones de proyecto y argumentos;
  - `T/catalog-import-core.test.mjs`.
- Modificar:
  - `SC/import-plan.mjs`: `planImport` y `formatPlan` delegan en el núcleo, con las mismas exportaciones y la misma salida;
  - `SC/import-exercises.mjs`: usa `commitCatalogOps`, `connectAdmin` y `parseImportArgs`;
  - `package.json`: añadir `tests/catalog-import-core.test.mjs` a `test:scripts`.

**Interfaces:**
- **Produce** (`SC/import-core.mjs`):
  - `canonical(value): string`: JSON con las claves ordenadas, igual que el `canonical` actual de `import-plan.mjs`, que se mueve aquí.
  - `planCatalogImport({ entries, existing, managedFields, isForeign = () => false, isNearDuplicate = null })` → `{create, update, unchanged, skippedUserOwned, skippedAdminManual, skippedEditedInApp, orphaned, nearDuplicates}`.
    - `entries` son entradas con `id` y `name`.
    - `existing`: `[{id, ownerUid, hasSource, updatedAtMillis, importedAtMillis, fields}]`.
    - `isForeign(doc)`: cierto si el documento es de un usuario. Va a `skippedUserOwned` como `{id, ownerUid}` y nunca a `orphaned`.
    - `isNearDuplicate(a, b)`: solo se mira al crear. Si es `null`, no se calcula ninguno.
  - `formatImportPlan(plan, { title, metaPath }): string[]`: las líneas comunes del texto de hoy de `formatPlan` (título, contadores, versión y secciones), sin las de imágenes.
  - `async commitCatalogOps({ db, collection, ops, toDoc, updateTimes, metaPath, adminUid, now, batchSize = 400 })`:
    - `ops` es `[{kind: "create"|"update", e}]`;
    - escribe en lotes y cada lote con operaciones sube `version` en `metaPath` (`merge`, con `updatedAt` y `updatedBy`);
    - devuelve el número de operaciones escritas.
- **Produce** (`SC/admin-cli.mjs`):
  - `resolveTarget(projectArg, env, readKeyProject)` → `{ projectId, useCredential }`, o lanza el error con el **mismo texto** que el `connect()` actual. `readKeyProject(path)` devuelve el `project_id` de la clave.
  - `connectAdmin(projectArg)`: usa `resolveTarget` con `process.env` y `readFileSync`, y llama a `initializeApp`.
  - `parseImportArgs(argv, { defaultCatalog })` → `{ apply, catalog, project }`, con el mismo texto de error para argumentos desconocidos.
- **Consume:** `isNearDuplicate` de `SC/normalize.mjs`, solo desde `import-plan.mjs`.

- [ ] **Paso 1: escribir los tests en rojo**, en `T/catalog-import-core.test.mjs`:

```js
import assert from "node:assert/strict";
import { test } from "node:test";
import { canonical, formatImportPlan, planCatalogImport } from "../scripts/catalog/import-core.mjs";
import { parseImportArgs, resolveTarget } from "../scripts/catalog/admin-cli.mjs";

const fields = (e) => ({ name: e.name, n: e.n ?? 1 });
const doc = (e, over = {}) => ({
  id: e.id, ownerUid: undefined, hasSource: true, updatedAtMillis: 1000, importedAtMillis: 1000, fields: fields(e), ...over,
});
const plan = (entries, existing, opts = {}) => planCatalogImport({ entries, existing, managedFields: fields, ...opts });

test("canonical no depende del orden de las claves", () => {
  assert.equal(canonical({ b: 1, a: { d: 2, c: 3 } }), canonical({ a: { c: 3, d: 2 }, b: 1 }));
});

test("planCatalogImport crea, actualiza, deja igual e informa de huérfanas", () => {
  const a = { id: "a", name: "A" };
  const b = { id: "b", name: "B", n: 2 };
  const c = { id: "c", name: "C" };
  const p = plan([a, b, c], [doc(a), doc({ id: "b", name: "B" }), doc({ id: "z", name: "Z" })]);
  assert.deepEqual(p.create.map((e) => e.id), ["c"]);
  assert.deepEqual(p.update.map((e) => e.id), ["b"]);
  assert.deepEqual(p.unchanged, ["a"]);
  assert.deepEqual(p.orphaned, ["z"]);
});

test("planCatalogImport sin isForeign trata como del catálogo un documento sin dueño", () => {
  const a = { id: "a", name: "A" };
  assert.deepEqual(plan([a], [doc(a)]).skippedUserOwned, []);
});

test("planCatalogImport omite los documentos ajenos y no los cuenta como huérfanos", () => {
  const a = { id: "a", name: "A" };
  const isForeign = (d) => d.ownerUid != null && d.ownerUid !== "admin";
  const p = plan([a], [doc(a, { ownerUid: "u1" }), doc({ id: "y", name: "Y" }, { ownerUid: "u2" })], { isForeign });
  assert.deepEqual(p.skippedUserOwned, [{ id: "a", ownerUid: "u1" }]);
  assert.deepEqual(p.orphaned, []);
});

test("planCatalogImport no pisa lo editado en la app ni las fichas manuales", () => {
  const a = { id: "a", name: "A", n: 2 };
  const b = { id: "b", name: "B", n: 2 };
  const p = plan([a, b], [doc({ id: "a", name: "A" }, { updatedAtMillis: 2000 }), doc({ id: "b", name: "B" }, { hasSource: false })]);
  assert.deepEqual(p.skippedEditedInApp, ["a"]);
  assert.deepEqual(p.skippedAdminManual, ["b"]);
});

test("planCatalogImport calcula casi duplicados solo con la función inyectada", () => {
  const nuevo = { id: "chocolates-negros", name: "Chocolates negros" };
  const existing = [doc({ id: "chocolate-negro", name: "Chocolate negro" })];
  assert.deepEqual(plan([nuevo], existing).nearDuplicates, []);
  const same = (x, y) => x.replace(/s\b/g, "") === y.replace(/s\b/g, "");
  assert.deepEqual(plan([nuevo], existing, { isNearDuplicate: same }).nearDuplicates, [
    { id: "chocolates-negros", existingId: "chocolate-negro" },
  ]);
});

test("formatImportPlan usa el título y la ruta de la versión", () => {
  const p = plan([{ id: "a", name: "A" }], []);
  const lines = formatImportPlan(p, { title: "Plan de importación de ingredientes", metaPath: "catalogMeta/ingredients" });
  assert.equal(lines[0], "Plan de importación de ingredientes");
  assert.ok(lines.includes("La versión del catálogo subirá (catalogMeta/ingredients)."));
});

test("resolveTarget exige el emulador para demo-* y lo prohíbe para bobitos-dev", () => {
  const keyOf = () => "bobitos-dev";
  assert.throws(() => resolveTarget("demo-bobitos", {}, keyOf), /FIRESTORE_EMULATOR_HOST/);
  assert.deepEqual(resolveTarget("demo-bobitos", { FIRESTORE_EMULATOR_HOST: "127.0.0.1:8080" }, keyOf), {
    projectId: "demo-bobitos", useCredential: false,
  });
  const env = { GOOGLE_APPLICATION_CREDENTIALS: "/k.json" };
  assert.throws(() => resolveTarget("dev", { ...env, FIRESTORE_EMULATOR_HOST: "x" }, keyOf), /no se importa a bobitos-dev/);
  assert.throws(() => resolveTarget("dev", {}, keyOf), /GOOGLE_APPLICATION_CREDENTIALS/);
  assert.throws(() => resolveTarget("dev", env, () => "otro"), /«otro»/);
  assert.deepEqual(resolveTarget("dev", env, keyOf), { projectId: "bobitos-dev", useCredential: true });
  assert.throws(() => resolveTarget("produccion", {}, keyOf), /Usa --project/);
});

test("parseImportArgs lee --apply, --project y --catalog y rechaza lo desconocido", () => {
  assert.deepEqual(parseImportArgs(["--project", "dev", "--apply"], { defaultCatalog: "x.json" }), {
    apply: true, catalog: "x.json", project: "dev",
  });
  assert.equal(parseImportArgs(["--catalog", "y.json"], { defaultCatalog: "x.json" }).catalog, "y.json");
  assert.throws(() => parseImportArgs(["--borrar"], { defaultCatalog: "x.json" }), /Argumento desconocido: --borrar/);
});
```

- [ ] **Paso 2:** ejecutar NODE. Esperado: FALLA, porque no existen `import-core.mjs` ni `admin-cli.mjs`.

- [ ] **Paso 3: implementar.**
  - **`SC/import-core.mjs`:**
    - mover `canonical` y `section` desde `import-plan.mjs`;
    - `planCatalogImport` es el bucle actual de `planImport` con las tres funciones inyectadas:
      - la condición `cur.ownerUid !== adminUid` pasa a ser `isForeign(cur)`;
      - las huérfanas son `!isForeign(d) && d.hasSource && !ids.has(d.id)`;
      - `nearDuplicates` solo se calcula si `isNearDuplicate` no es `null`;
    - `formatImportPlan` devuelve las líneas actuales de `formatPlan` sin el bloque de imágenes, con `title` en la primera línea y `metaPath` en la de la versión;
    - `commitCatalogOps` es el bucle actual de `runImport`, desde `const ops` hasta antes del `log` final.
  - **`SC/import-plan.mjs`:**
    - `planImport({catalog, existing, adminUid})` llama a `planCatalogImport` con `entries: catalog.exercises`, `managedFields`, `isForeign: (d) => d.ownerUid !== adminUid` e `isNearDuplicate`;
    - `formatPlan(plan)` es `formatImportPlan(plan, {title: "Plan de importación de ejercicios", metaPath: CATALOG_META_PATH})` más las líneas de imágenes de hoy, unido con `\n`.
  - **`SC/admin-cli.mjs`:** `resolveTarget`, `connectAdmin` y `parseImportArgs`, sacados de `connect()` y `parseArgs()` de `import-exercises.mjs` con los mismos textos de error.
  - **`SC/import-exercises.mjs`:**
    - usa `commitCatalogOps({db, collection: "exercises", ops, toDoc: toFirestoreDoc, updateTimes, metaPath: CATALOG_META_PATH, adminUid: CATALOG_ADMIN_UID, now})` **después** de los lotes de imágenes, como hoy;
    - la CLI usa `parseImportArgs(argv, {defaultCatalog: "data/catalog/exercises.json"})` y `connectAdmin`.
  - **`package.json`:** añadir el test nuevo a `test:scripts`.

- [ ] **Paso 4:** ejecutar NODE. Esperado: verde, con los tests nuevos y todos los que ya existían sin tocar.

- [ ] **Paso 5:** ejecutar IMP. Esperado: verde, con `T/catalog-import.emulator.test.mjs` sin cambios.

- [ ] **Paso 6:** commit.

```bash
git add scripts/catalog/import-core.mjs scripts/catalog/admin-cli.mjs scripts/catalog/import-plan.mjs scripts/catalog/import-exercises.mjs tests/catalog-import-core.test.mjs package.json
git commit -m "refactor(catalogo): núcleo genérico del importador y protecciones de proyecto compartidas"
```

### Task 2: Lista de ingredientes genéricos, validación y fichero de revisión

**Ficheros:**
- Crear:
  - `SC/ingredients.mjs`: constantes, validación y generación del fichero de revisión;
  - `SC/build-ingredients.mjs`: CLI que valida y genera la revisión;
  - `DC/ingredients.json`: los datos;
  - `DC/ingredients-review.md`: generado;
  - `T/catalog-ingredients.test.mjs`.
- Modificar `package.json`:
  - script `"catalog:ingredients": "node scripts/catalog/build-ingredients.mjs"`;
  - añadir `tests/catalog-ingredients.test.mjs` a `test:scripts`.

**Interfaces:**
- **Consume:** `slug` y `nearDuplicateKey` de `SC/normalize.mjs`.
- **Produce** (`SC/ingredients.mjs`):
  - `ingredientKey(name): string`: la clave de casi duplicado de ingredientes. Es `nearDuplicateKey(name)` con cada palabra de más de 3 letras que acaba en «e» recortada (`limone` → `limon`, `pane` → `pan`, `tomate` → `tomat`), y las palabras ordenadas y unidas con un espacio. Sirve porque `nearDuplicateKey` solo quita la «s» final y deja «Limones» como `limone`.
  - `INGREDIENT_CATEGORIES`: un array con las 14 categorías de las Restricciones globales, en ese orden.
  - `INGREDIENT_UNITS = ["g", "ml", "ud"]` e `INGREDIENT_NAME_MAX = 120`.
  - `validateIngredient(entry): string[]`, con los problemas de una entrada.
  - `validateIngredientCatalog(catalog): string[]`, con los problemas del fichero entero.
  - `renderIngredientReview(catalog): string`, el markdown de revisión.
- **Formato de `DC/ingredients.json`:** `{ "schemaVersion": 1, "ingredients": [{ "id", "name", "category", "defaultUnit" }] }`, ordenado por categoría (en el orden de `INGREDIENT_CATEGORIES`) y después por nombre según `Intl.Collator("es")`.

- [ ] **Paso 1: escribir los tests en rojo**, en `T/catalog-ingredients.test.mjs`:

```js
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import {
  INGREDIENT_CATEGORIES, INGREDIENT_UNITS, ingredientKey, renderIngredientReview, validateIngredient, validateIngredientCatalog,
} from "../scripts/catalog/ingredients.mjs";
import { slug } from "../scripts/catalog/normalize.mjs";

const ing = (over = {}) => ({ id: "chocolate-negro", name: "Chocolate negro", category: "Dulces y chocolate", defaultUnit: "g", ...over });
const cat = (...ingredients) => ({ schemaVersion: 1, ingredients });

test("hay 14 categorías y tres unidades", () => {
  assert.equal(INGREDIENT_CATEGORIES.length, 14);
  assert.equal(INGREDIENT_CATEGORIES[0], "Frutas");
  assert.equal(INGREDIENT_CATEGORIES[13], "Congelados y otros");
  assert.deepEqual(INGREDIENT_UNITS, ["g", "ml", "ud"]);
});

test("ingredientKey junta singular y plural, tildes y mayúsculas, y separa variantes", () => {
  assert.equal(ingredientKey("Limón"), ingredientKey("Limones"));
  assert.equal(ingredientKey("Tomate"), ingredientKey("tomates"));
  assert.equal(ingredientKey("Pan"), ingredientKey("Panes"));
  assert.equal(ingredientKey("Pimiento rojo"), ingredientKey("Pimientos rojos"));
  assert.notEqual(ingredientKey("Leche entera"), ingredientKey("Leche semidesnatada"));
  assert.notEqual(ingredientKey("Pan de molde"), ingredientKey("Pan"));
});

test("una entrada correcta no tiene problemas", () => {
  assert.deepEqual(validateIngredient(ing()), []);
});

test("el id tiene que ser el slug del nombre", () => {
  assert.equal(validateIngredient(ing({ id: "chocolate" })).length, 1);
});

test("rechaza nombre vacío, largo, con espacios de más o en minúscula inicial", () => {
  assert.ok(validateIngredient(ing({ name: "", id: "" })).length > 0);
  const largo = "A".repeat(121);
  assert.ok(validateIngredient(ing({ name: largo, id: slug(largo) })).length > 0);
  assert.ok(validateIngredient(ing({ name: " Chocolate negro", id: "chocolate-negro" })).length > 0);
  assert.ok(validateIngredient(ing({ name: "Chocolate  negro", id: "chocolate-negro" })).length > 0);
  assert.ok(validateIngredient(ing({ name: "chocolate negro" })).length > 0);
});

test("rechaza categoría o unidad fuera de la lista y claves de más", () => {
  assert.ok(validateIngredient(ing({ category: "Dulces" })).length > 0);
  assert.ok(validateIngredient(ing({ defaultUnit: "kg" })).length > 0);
  assert.ok(validateIngredient(ing({ brand: "Valor" })).length > 0);
});

test("el catálogo rechaza ids repetidos y casi duplicados", () => {
  assert.ok(validateIngredientCatalog(cat(ing(), ing())).some((p) => p.includes("chocolate-negro")));
  const plural = ing({ id: "chocolates-negros", name: "Chocolates negros" });
  assert.ok(validateIngredientCatalog(cat(ing(), plural)).some((p) => p.includes("chocolates-negros")));
  const tilde = ing({ id: "limon", name: "Limón", category: "Frutas", defaultUnit: "ud" });
  const otra = ing({ id: "limones", name: "Limones", category: "Frutas", defaultUnit: "ud" });
  assert.ok(validateIngredientCatalog(cat(tilde, otra)).length > 0);
});

test("el catálogo exige schemaVersion 1 y una lista", () => {
  assert.ok(validateIngredientCatalog({ ingredients: [ing()] }).length > 0);
  assert.ok(validateIngredientCatalog({ schemaVersion: 1 }).length > 0);
});

test("la revisión agrupa por categoría en orden y cuenta cada una", () => {
  const md = renderIngredientReview(cat(
    ing(),
    ing({ id: "manzana", name: "Manzana", category: "Frutas", defaultUnit: "ud" }),
  ));
  assert.ok(md.indexOf("## Frutas") < md.indexOf("## Dulces y chocolate"));
  assert.match(md, /Manzana/);
  assert.match(md, /\| Frutas \| 1 \|/);
  assert.match(md, /Total: 2/);
});

test("data/catalog/ingredients.json es válido, usa las 14 categorías y tiene entre 300 y 400", async () => {
  const data = JSON.parse(await readFile(new URL("../data/catalog/ingredients.json", import.meta.url), "utf8"));
  assert.deepEqual(validateIngredientCatalog(data), []);
  assert.ok(data.ingredients.length >= 300 && data.ingredients.length <= 400, `hay ${data.ingredients.length}`);
  assert.deepEqual(new Set(data.ingredients.map((i) => i.category)), new Set(INGREDIENT_CATEGORIES));
});

test("data/catalog/ingredients-review.md está al día", async () => {
  const data = JSON.parse(await readFile(new URL("../data/catalog/ingredients.json", import.meta.url), "utf8"));
  const md = await readFile(new URL("../data/catalog/ingredients-review.md", import.meta.url), "utf8");
  assert.equal(md, renderIngredientReview(data));
});
```

- [ ] **Paso 2:** ejecutar NODE. Esperado: FALLA, porque no existe `ingredients.mjs`.

- [ ] **Paso 3: implementar `SC/ingredients.mjs`.**
  - **`validateIngredient`:**
    - claves exactamente `id`, `name`, `category` y `defaultUnit`;
    - `name` es string, sin espacios al principio ni al final, sin dobles espacios, de 1 a 120 caracteres, y su primera letra es mayúscula (`name[0] === name[0].toLocaleUpperCase("es")`, siendo una letra);
    - `id === slug(name)`;
    - `category` está en `INGREDIENT_CATEGORIES`;
    - `defaultUnit` está en `INGREDIENT_UNITS`.
    - Cada mensaje es una frase en español que nombra el campo.
  - **`validateIngredientCatalog`:**
    - `schemaVersion === 1` y `ingredients` es un array no vacío;
    - los problemas de cada entrada, con el prefijo `${id ?? name}: `;
    - los ids repetidos («id repetido: <id>»);
    - los casi duplicados, cuando dos entradas comparten `ingredientKey(name)` («casi duplicado: <id> ~ <id>»).
  - **`renderIngredientReview`:**
    - título `# Revisión del catálogo de ingredientes`;
    - una línea que explica que se genera con `npm run catalog:ingredients` y que no se edita a mano;
    - `Total: N`;
    - una tabla `| Categoría | Ingredientes |` con todas las categorías en orden, incluidas las que tienen 0;
    - después, por cada categoría con entradas, `## <Categoría>` y una tabla `| Nombre | Unidad | id |` ordenada con `Intl.Collator("es")`;
    - termina con un salto de línea.
  - **`SC/build-ingredients.mjs`:** lee `DC/ingredients.json` y lo valida. Si hay problemas, los imprime y sale con código 1. Si no, escribe `DC/ingredients-review.md` e imprime `Catálogo de ingredientes: N ingredientes. Revisión en data/catalog/ingredients-review.md`.

- [ ] **Paso 4: escribir `DC/ingredients.json`**, con **entre 320 y 380** ingredientes genéricos de una cocina española y todas las categorías con algún ingrediente. Criterios:
  - **Nombre:** el que se apunta en la lista de la compra en España. Singular para piezas («Tomate», «Cebolla», «Limón», «Pimiento rojo»); plural cuando es lo habitual («Garbanzos», «Lentejas», «Espaguetis», «Huevos», «Guisantes»).
  - **Genérico, sin marcas ni formatos comerciales:** «Chocolate negro», no «Chocolate 85 % Lindt». Las variantes solo si se compran por separado («Leche entera», «Leche semidesnatada», «Pan de molde», «Barra de pan»).
  - **Unidad:** `ud` lo que se compra por piezas (frutas y verduras sueltas, huevos, barra de pan, latas, yogures), `ml` los líquidos (leche, aceites, vinagre, bebidas, caldos) y `g` todo lo demás.
  - **Categoría:**
    - embutidos y fiambres → «Carnes»;
    - conservas de pescado → «Pescados y mariscos»;
    - conservas vegetales y tomate triturado → «Verduras y hortalizas»;
    - harinas y levadura → «Cereales, pasta y arroz»;
    - azúcar, miel, cacao y mermelada → «Dulces y chocolate»;
    - sal, vinagre, caldos, mayonesa y tomate frito → «Aceites, salsas y condimentos»;
    - café, té, infusiones y zumos → «Bebidas»;
    - congelados, tofu, seitán, levadura nutricional y lo que no encaje → «Congelados y otros».
  - **Orden:** el de la interfaz (categoría y después nombre). Ejecutar `npm run catalog:ingredients` y corregir hasta que no haya problemas.

- [ ] **Paso 5:** ejecutar NODE. Esperado: verde, incluidos los dos tests sobre los ficheros reales.

- [ ] **Paso 6:** commit.

```bash
git add scripts/catalog/ingredients.mjs scripts/catalog/build-ingredients.mjs data/catalog/ingredients.json data/catalog/ingredients-review.md tests/catalog-ingredients.test.mjs package.json
git commit -m "feat(catalogo): lista de ingredientes genéricos con validación y revisión"
```

### Task 3: Importador de ingredientes

**Ficheros:**
- Crear:
  - `SC/ingredient-import-plan.mjs` (puro, sin firebase-admin);
  - `SC/import-ingredients.mjs`;
  - `T/catalog-ingredient-import-plan.test.mjs`;
  - `T/ingredient-import.emulator.test.mjs`.
- Modificar `package.json`:
  - `"catalog:import-ingredients": "node scripts/catalog/import-ingredients.mjs"`;
  - `test:catalog-import` pasa a ser `firebase emulators:exec --only firestore --project demo-bobitos "node --test --test-concurrency=1 tests/catalog-import.emulator.test.mjs tests/ingredient-import.emulator.test.mjs"`. `--test-concurrency=1` hace falta porque los ficheros comparten el emulador y vacían colecciones en `beforeEach`; en paralelo se pisarían;
  - añadir `tests/catalog-ingredient-import-plan.test.mjs` a `test:scripts`.

**Interfaces:**
- **Consume:**
  - `planCatalogImport`, `formatImportPlan` y `commitCatalogOps` de `SC/import-core.mjs`;
  - `parseImportArgs` y `connectAdmin` de `SC/admin-cli.mjs`;
  - `CATALOG_ADMIN_UID` de `SC/import-plan.mjs`;
  - `ingredientKey` y `validateIngredientCatalog` de `SC/ingredients.mjs`.
- **Produce** (`SC/ingredient-import-plan.mjs`):
  - `INGREDIENTS_META_PATH = "catalogMeta/ingredients"`;
  - `ingredientManagedFields(entry)` → `{ name, nameLower: name.toLowerCase(), category, defaultUnit, source: { provider: "bobitos" } }`;
  - `ingredientDocToExisting(id, data, { updatedAtMillis, importedAtMillis })` → `{ id, ownerUid: data.ownerUid, hasSource, updatedAtMillis: updatedAtMillis ?? 0, importedAtMillis, fields: { name, nameLower, category, defaultUnit, source: data.source ? { provider: data.source.provider } : undefined } }`;
  - `ingredientToFirestoreDoc(entry, { now, create = false })` → los campos gestionados, más `source.importedAt = now`, `updatedBy = CATALOG_ADMIN_UID` y `updatedAt = now`, y al crear también `createdAt = now`. **Sin `ownerUid`, `createdBy` ni `createdByName`.**
  - `planIngredientImport({ catalog, existing, adminUid })` llama a `planCatalogImport` con:
    - `entries: catalog.ingredients`;
    - `managedFields: ingredientManagedFields`;
    - `isForeign: (d) => d.ownerUid != null && d.ownerUid !== adminUid`;
    - `isNearDuplicate: (a, b) => ingredientKey(a) === ingredientKey(b)`.
  - `formatIngredientPlan(plan)` = `formatImportPlan(plan, { title: "Plan de importación de ingredientes", metaPath: INGREDIENTS_META_PATH }).join("\n")`.
- **Produce** (`SC/import-ingredients.mjs`): `runIngredientImport({ db, catalog, apply, log = console.log })` → `{ ...plan, versionBumped }`.

- [ ] **Paso 1: escribir los tests en rojo.**

`T/catalog-ingredient-import-plan.test.mjs`:

```js
import assert from "node:assert/strict";
import { test } from "node:test";
import { CATALOG_ADMIN_UID } from "../scripts/catalog/import-plan.mjs";
import {
  INGREDIENTS_META_PATH, formatIngredientPlan, ingredientDocToExisting, ingredientManagedFields,
  ingredientToFirestoreDoc, planIngredientImport,
} from "../scripts/catalog/ingredient-import-plan.mjs";

const ing = (over = {}) => ({ id: "chocolate-negro", name: "Chocolate negro", category: "Dulces y chocolate", defaultUnit: "g", ...over });
const cat = (...ingredients) => ({ schemaVersion: 1, ingredients });
const imported = (e, over = {}) => ({
  id: e.id, ownerUid: undefined, hasSource: true, updatedAtMillis: 1000, importedAtMillis: 1000,
  fields: ingredientManagedFields(e), ...over,
});
const plan = (catalog, existing = []) => planIngredientImport({ catalog, existing, adminUid: CATALOG_ADMIN_UID });

test("la versión vive en catalogMeta/ingredients", () => {
  assert.equal(INGREDIENTS_META_PATH, "catalogMeta/ingredients");
});

test("el documento al crear lleva fuente, fechas y autor del catálogo y nada de dueño", () => {
  const now = { marker: true };
  const d = ingredientToFirestoreDoc(ing(), { now, create: true });
  assert.deepEqual(d, {
    name: "Chocolate negro", nameLower: "chocolate negro", category: "Dulces y chocolate", defaultUnit: "g",
    source: { provider: "bobitos", importedAt: now }, updatedBy: CATALOG_ADMIN_UID, updatedAt: now, createdAt: now,
  });
  assert.equal("createdAt" in ingredientToFirestoreDoc(ing(), { now }), false);
});

test("un documento importado sin cambios queda igual", () => {
  const e = ing();
  const data = { ...ingredientManagedFields(e), source: { provider: "bobitos", importedAt: "t" } };
  const existing = [ingredientDocToExisting(e.id, data, { updatedAtMillis: 5, importedAtMillis: 5 })];
  assert.deepEqual(plan(cat(e), existing).unchanged, ["chocolate-negro"]);
});

test("cambiar la categoría o la unidad actualiza", () => {
  const e = ing();
  assert.deepEqual(plan(cat(ing({ defaultUnit: "ud" })), [imported(e)]).update.map((x) => x.id), ["chocolate-negro"]);
});

test("un documento antiguo de usuario con el mismo id se omite", () => {
  const e = ing();
  const p = plan(cat(e), [imported(e, { ownerUid: "u1", hasSource: false })]);
  assert.deepEqual(p.skippedUserOwned, [{ id: "chocolate-negro", ownerUid: "u1" }]);
  assert.deepEqual(p.create, []);
});

test("una ficha manual del admin (sin fuente) y una editada en la app no se pisan", () => {
  const e = ing({ defaultUnit: "ud" });
  assert.deepEqual(plan(cat(e), [imported(ing(), { hasSource: false })]).skippedAdminManual, ["chocolate-negro"]);
  assert.deepEqual(plan(cat(e), [imported(ing(), { updatedAtMillis: 2000 })]).skippedEditedInApp, ["chocolate-negro"]);
});

test("se informa de huérfanas y de casi duplicados", () => {
  const viejo = ing({ id: "cacao", name: "Cacao" });
  const nuevo = ing({ id: "chocolates-negros", name: "Chocolates negros" });
  const p = plan(cat(nuevo), [imported(viejo), imported(ing())]);
  assert.deepEqual(p.orphaned.sort(), ["cacao", "chocolate-negro"]);
  assert.deepEqual(p.nearDuplicates, [{ id: "chocolates-negros", existingId: "chocolate-negro" }]);
});

test("el texto del plan habla de ingredientes y de su versión", () => {
  const text = formatIngredientPlan(plan(cat(ing())));
  assert.match(text, /^Plan de importación de ingredientes/);
  assert.match(text, /catalogMeta\/ingredients/);
});
```

`T/ingredient-import.emulator.test.mjs`:

```js
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { after, before, beforeEach, test } from "node:test";
import { deleteApp, initializeApp } from "firebase-admin/app";
import { Timestamp, getFirestore } from "firebase-admin/firestore";
import { runIngredientImport } from "../scripts/catalog/import-ingredients.mjs";

const full = JSON.parse(await readFile(new URL("../data/catalog/ingredients.json", import.meta.url), "utf8"));
const small = { ...full, ingredients: full.ingredients.slice(0, 5) };
const quiet = () => {};
let app;
let db;

before(() => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, "falta FIRESTORE_EMULATOR_HOST");
  app = initializeApp({ projectId: "demo-bobitos" }, "ingredient-import-test");
  db = getFirestore(app);
});
after(() => deleteApp(app));
beforeEach(async () => {
  await db.recursiveDelete(db.collection("ingredients"));
  await db.doc("catalogMeta/ingredients").delete();
  await db.doc("catalogMeta/exercises").delete();
});

const version = async () => (await db.doc("catalogMeta/ingredients").get()).get("version");

test("importa y una segunda pasada no escribe ni sube la versión", async () => {
  const first = await runIngredientImport({ db, catalog: small, apply: true, log: quiet });
  assert.equal(first.create.length, 5);
  assert.equal((await db.collection("ingredients").get()).size, 5);
  assert.equal(await version(), 1);
  const d = (await db.doc(`ingredients/${small.ingredients[0].id}`).get()).data();
  assert.equal(d.name, small.ingredients[0].name);
  assert.equal(d.source.provider, "bobitos");
  assert.equal(d.updatedAt.toMillis(), d.source.importedAt.toMillis());
  assert.equal(d.ownerUid, undefined);
  const second = await runIngredientImport({ db, catalog: small, apply: true, log: quiet });
  assert.equal(second.create.length + second.update.length, 0);
  assert.equal(second.versionBumped, false);
  assert.equal(await version(), 1);
});

test("la simulación no escribe nada", async () => {
  const r = await runIngredientImport({ db, catalog: small, apply: false, log: quiet });
  assert.equal(r.create.length, 5);
  assert.equal((await db.collection("ingredients").get()).size, 0);
  assert.equal((await db.doc("catalogMeta/ingredients").get()).exists, false);
});

test("un cambio en el JSON actualiza solo esa ficha y sube la versión", async () => {
  await runIngredientImport({ db, catalog: small, apply: true, log: quiet });
  const [first, ...rest] = small.ingredients;
  const changed = { ...small, ingredients: [{ ...first, defaultUnit: first.defaultUnit === "g" ? "ud" : "g" }, ...rest] };
  const r = await runIngredientImport({ db, catalog: changed, apply: true, log: quiet });
  assert.deepEqual(r.update.map((e) => e.id), [first.id]);
  assert.equal(await version(), 2);
});

test("no pisa una ficha editada en la app después de importarla", async () => {
  await runIngredientImport({ db, catalog: small, apply: true, log: quiet });
  const id = small.ingredients[0].id;
  await db.doc(`ingredients/${id}`).update({ category: "Congelados y otros", updatedAt: Timestamp.fromMillis(Date.now() + 60_000) });
  const r = await runIngredientImport({ db, catalog: small, apply: true, log: quiet });
  assert.deepEqual(r.skippedEditedInApp, [id]);
  assert.equal((await db.doc(`ingredients/${id}`).get()).get("category"), "Congelados y otros");
});

test("omite un documento antiguo de usuario con el mismo id", async () => {
  const e = small.ingredients[0];
  await db.doc(`ingredients/${e.id}`).set({ name: e.name, nameLower: e.name.toLowerCase(), ownerUid: "u1" });
  const r = await runIngredientImport({ db, catalog: small, apply: true, log: quiet });
  assert.deepEqual(r.skippedUserOwned, [{ id: e.id, ownerUid: "u1" }]);
  assert.equal((await db.doc(`ingredients/${e.id}`).get()).get("ownerUid"), "u1");
});

test("rechaza un catálogo inválido sin escribir", async () => {
  const bad = { ...small, ingredients: [{ ...small.ingredients[0], defaultUnit: "kg" }] };
  await assert.rejects(runIngredientImport({ db, catalog: bad, apply: true, log: quiet }), /Catálogo inválido/);
  assert.equal((await db.collection("ingredients").get()).size, 0);
});

test("no toca la versión del catálogo de ejercicios", async () => {
  await runIngredientImport({ db, catalog: small, apply: true, log: quiet });
  assert.equal((await db.doc("catalogMeta/exercises").get()).exists, false);
});

test("importa el catálogo completo en un solo pase", async () => {
  const r = await runIngredientImport({ db, catalog: full, apply: true, log: quiet });
  assert.equal(r.create.length, full.ingredients.length);
  assert.equal((await db.collection("ingredients").get()).size, full.ingredients.length);
});
```

- [ ] **Paso 2:** ejecutar NODE e IMP. Esperado: FALLAN, porque no existen los módulos.

- [ ] **Paso 3: implementar.**
  - **`SC/ingredient-import-plan.mjs`:** según la interfaz.
  - **`SC/import-ingredients.mjs`:**
    - `runIngredientImport`:
      1. valida con `validateIngredientCatalog`; si hay problemas, lanza `Catálogo inválido:\n…`, **antes de leer nada**;
      2. lee `db.collection("ingredients").get()`, guarda `updateTime` por id y convierte cada documento con `ingredientDocToExisting`;
      3. calcula el plan y lo imprime con `log(formatIngredientPlan(plan))`;
      4. sin `apply`, imprime «Simulación: no se ha escrito nada. Usa --apply para aplicar.» y devuelve `versionBumped: false`;
      5. con `apply`, usa `now = FieldValue.serverTimestamp()` y `commitCatalogOps({db, collection: "ingredients", ops, toDoc: ingredientToFirestoreDoc, updateTimes, metaPath: INGREDIENTS_META_PATH, adminUid: CATALOG_ADMIN_UID, now})`;
      6. imprime `Aplicado: N creados, M actualizados.`.
    - **La CLI** sigue el modelo de `import-exercises.mjs`: `parseImportArgs(argv, {defaultCatalog: "data/catalog/ingredients.json"})`, `connectAdmin` y `runIngredientImport`, con los errores en `console.error` y `process.exitCode = 1`.
    - **Nunca borra.**
  - **`package.json`:** según la lista de ficheros.

- [ ] **Paso 4:** ejecutar NODE e IMP. Esperado: verde, con los tests de ejercicios incluidos y sin cambios.

- [ ] **Paso 5:** commit.

```bash
git add scripts/catalog/ingredient-import-plan.mjs scripts/catalog/import-ingredients.mjs tests/catalog-ingredient-import-plan.test.mjs tests/ingredient-import.emulator.test.mjs package.json
git commit -m "feat(catalogo): importador de ingredientes a ingredients con versión en catalogMeta"
```

### Task 4: Borrado inicial de ingredientes, marcas y preferencias

**Ficheros:**
- Crear:
  - `SC/reset-ingredients.mjs`;
  - `T/ingredient-reset.emulator.test.mjs`.
- Modificar `package.json`:
  - `"catalog:reset-ingredients": "node scripts/catalog/reset-ingredients.mjs"`;
  - añadir `tests/ingredient-reset.emulator.test.mjs` al comando de `test:catalog-import`, detrás de los otros dos y con `--test-concurrency=1` (lo pone la Task 3).

**Interfaces:**
- **Consume:** `connectAdmin` de `SC/admin-cli.mjs`.
- **Produce** (`SC/reset-ingredients.mjs`):
  - `async countIngredientData(db)` → `{ ingredients, brands, prefs }`:
    - `ingredients` = `(await db.collection("ingredients").listDocuments()).length`, que incluye los padres sin datos que tienen marcas;
    - `brands` = la suma, para cada uno de esos documentos, de `(await ref.collection("brands").count().get()).data().count`;
    - `prefs` = `(await db.collection("ingredientPrefs").count().get()).data().count`.
  - `async runReset({ db, apply, log = console.log })` → `{ ingredients, brands, prefs, deleted }`.
  - `parseResetArgs(argv)` → `{ apply, project }`. Rechaza cualquier otro argumento con `Argumento desconocido: <arg>`.

- [ ] **Paso 1: escribir los tests en rojo**, en `T/ingredient-reset.emulator.test.mjs`:

```js
import assert from "node:assert/strict";
import { after, before, beforeEach, test } from "node:test";
import { deleteApp, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { parseResetArgs, runReset } from "../scripts/catalog/reset-ingredients.mjs";

const quiet = () => {};
let app;
let db;

before(() => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, "falta FIRESTORE_EMULATOR_HOST");
  app = initializeApp({ projectId: "demo-bobitos" }, "ingredient-reset-test");
  db = getFirestore(app);
});
after(() => deleteApp(app));

beforeEach(async () => {
  for (const path of ["ingredients", "ingredientPrefs", "users", "exercises", "catalogMeta"]) {
    await db.recursiveDelete(db.collection(path));
  }
  for (const id of ["arroz", "leche", "tomate"]) {
    await db.doc(`ingredients/${id}`).set({ name: id, ownerUid: "u1" });
    await db.doc(`ingredients/${id}/brands/b1`).set({ name: "Marca 1", ownerUid: "u1" });
    await db.doc(`ingredients/${id}/brands/b2`).set({ name: "Marca 2", ownerUid: "u2" });
  }
  // Padre sin datos: solo tiene una marca colgando.
  await db.doc("ingredients/fantasma/brands/b1").set({ name: "Huérfana", ownerUid: "u1" });
  await db.doc("ingredientPrefs/u1").set({ entries: { arroz: { supermarket: "DIA" } } });
  await db.doc("ingredientPrefs/u2").set({ entries: {} });
  // Lo que no se toca nunca.
  await db.doc("users/u1/brands/p1").set({ name: "Personal", ingredientId: "arroz" });
  await db.doc("users/u1/ingredients/salsa-casera").set({ name: "Salsa casera" });
  await db.doc("exercises/press-de-banca").set({ name: "Press de banca" });
  await db.doc("catalogMeta/exercises").set({ version: 3 });
  await db.doc("catalogMeta/ingredients").set({ version: 1 });
});

test("la simulación cuenta ingredientes, marcas y preferencias sin borrar nada", async () => {
  const r = await runReset({ db, apply: false, log: quiet });
  assert.deepEqual(r, { ingredients: 4, brands: 7, prefs: 2, deleted: false });
  assert.equal((await db.collection("ingredients").listDocuments()).length, 4);
  assert.equal((await db.collection("ingredientPrefs").get()).size, 2);
});

test("con apply borra ingredientes, marcas (también bajo padres sin datos) y preferencias", async () => {
  const r = await runReset({ db, apply: true, log: quiet });
  assert.deepEqual(r, { ingredients: 4, brands: 7, prefs: 2, deleted: true });
  assert.equal((await db.collection("ingredients").listDocuments()).length, 0);
  assert.equal((await db.collection("ingredients/fantasma/brands").get()).size, 0);
  assert.equal((await db.collection("ingredientPrefs").get()).size, 0);
});

test("no toca users, exercises ni catalogMeta", async () => {
  await runReset({ db, apply: true, log: quiet });
  assert.equal((await db.doc("users/u1/brands/p1").get()).exists, true);
  assert.equal((await db.doc("users/u1/ingredients/salsa-casera").get()).exists, true);
  assert.equal((await db.doc("exercises/press-de-banca").get()).exists, true);
  assert.equal((await db.doc("catalogMeta/exercises").get()).get("version"), 3);
  assert.equal((await db.doc("catalogMeta/ingredients").get()).get("version"), 1);
});

test("una segunda pasada no encuentra nada", async () => {
  await runReset({ db, apply: true, log: quiet });
  assert.deepEqual(await runReset({ db, apply: true, log: quiet }), { ingredients: 0, brands: 0, prefs: 0, deleted: true });
});

test("parseResetArgs solo admite --project y --apply", () => {
  assert.deepEqual(parseResetArgs(["--project", "demo-bobitos"]), { apply: false, project: "demo-bobitos" });
  assert.deepEqual(parseResetArgs(["--apply", "--project", "dev"]), { apply: true, project: "dev" });
  assert.throws(() => parseResetArgs(["--catalog", "x"]), /Argumento desconocido: --catalog/);
});
```

- [ ] **Paso 2:** ejecutar IMP. Esperado: FALLA, porque no existe `reset-ingredients.mjs`.

- [ ] **Paso 3: implementar `SC/reset-ingredients.mjs`.**
  - **Cabecera:** un comentario que explica que es un script de un solo uso para empezar de cero (spec, decisión 7). Uso: `node scripts/catalog/reset-ingredients.mjs --project demo-bobitos|bobitos-dev|dev [--apply]`.
  - **`runReset`:**
    1. cuenta con `countIngredientData`;
    2. imprime `Borrado de ingredientes: N ingredientes, M marcas, P preferencias`;
    3. sin `apply`, imprime «Simulación: no se ha borrado nada. Usa --apply para borrar.» y devuelve `deleted: false`;
    4. con `apply`, ejecuta `await db.recursiveDelete(db.collection("ingredients"))` y después `await db.recursiveDelete(db.collection("ingredientPrefs"))`, imprime `Borrado: …` y devuelve `deleted: true`.
    - No toca ninguna otra ruta.
  - **La CLI** solo se ejecuta como programa principal (el mismo `if` que en `import-exercises.mjs`): `parseResetArgs`, `connectAdmin(project)` y `runReset({ db: getFirestore(app), apply })`, con los errores en `console.error` y `process.exitCode = 1`.
  - **`package.json`:** según la lista de ficheros.

- [ ] **Paso 4:** ejecutar IMP y NODE. Esperado: verde.

- [ ] **Paso 5:** commit.

```bash
git add scripts/catalog/reset-ingredients.mjs tests/ingredient-reset.emulator.test.mjs package.json
git commit -m "feat(catalogo): script de borrado inicial de ingredientes, marcas y preferencias"
```

### Task 5: Documentación del catálogo de ingredientes

**Ficheros:**
- Crear: `docs/INGREDIENT_CATALOG.md`.
- Modificar:
  - `data/catalog/LICENSE.md`: una sección `## Ingredientes`;
  - `PROJECT_PLAN.md`: una fila nueva en la tabla de decisiones, después de la del 10/10/2026;
  - `docs/DATA_MODEL.md`: solo un enlace a `INGREDIENT_CATALOG.md` donde ya se enlaza `EXERCISE_CATALOG.md`. El esquema nuevo de ingredientes es del plan 2.

**Interfaces:**
- **Consume:** los nombres de los scripts y comandos de npm de las Tasks 2 a 4:
  - `catalog:ingredients`
  - `catalog:import-ingredients`
  - `catalog:reset-ingredients`
  - `test:catalog-import`
- **Produce:** documentación, sin código.

- [ ] **Paso 1: escribir `docs/INGREDIENT_CATALOG.md`**, siguiendo la estructura de `docs/EXERCISE_CATALOG.md` y sin imágenes ni wger. Secciones:
  1. **Qué es:**
     - genéricos comunes que crea solo el admin;
     - las marcas, las tiendas y los ingredientes personales son de cada usuario (planes 2 y 3, `users/{uid}/…`);
     - enlace a la spec.
  2. **Fuente y licencia:** lista propia de Bobitos, sin datos de terceros ni nutrición. BEDCA se descartó por sus condiciones de uso.
  3. **Estructura de `data/catalog/`:** `ingredients.json` (formato y orden) e `ingredients-review.md` (se genera y no se edita).
  4. **Categorías y unidades:** las 14 categorías, las 3 unidades y los criterios de nombre, unidad y categoría de la Task 2, paso 4. Cómo añadir una categoría: cambiar `INGREDIENT_CATEGORIES` y, en el plan 2, la lista equivalente de la app.
  5. **Editar el catálogo:** editar `ingredients.json`, ejecutar `npm run catalog:ingredients` y revisar el diff de `ingredients-review.md`.
  6. **Importar:**
     - en el emulador: `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 npm run catalog:import-ingredients -- --project demo-bobitos [--apply]`;
     - en `bobitos-dev`: `GOOGLE_APPLICATION_CREDENTIALS=~/.config/bobitos/bobitos-dev-adminsdk.json npm run catalog:import-ingredients -- --project dev`, primero en simulación y después con `--apply`;
     - una tabla con qué hace con cada documento existente: crear, actualizar, sin cambios, omitido de usuario, omitido manual del admin, omitido por editado en la app, huérfano y casi duplicado.
  7. **Empezar de cero (`catalog:reset-ingredients`):** qué borra y qué no, que primero se ejecuta la simulación, y que se ejecuta **una sola vez**, después de desplegar las reglas del plan 2 y justo antes de la primera importación.
  8. **Orden de despliegue:** reglas → borrado (simulación y `--apply`) → importación (simulación y `--apply`) → beta 19. Las acciones contra `bobitos-dev` las confirma el usuario una a una.
  9. **Versión del catálogo y caché:**
     - `catalogMeta/ingredients` sube con cada lote que escribe algo;
     - la app (plan 2) usa `VersionedCatalogLoader` con la clave `"ingredients"`.
  10. **Coste en Firestore (plan Spark):** unas 350 escrituras en la primera importación, 0 al reimportar sin cambios, y una lectura de unos 350 documentos por dispositivo cuando cambia la versión.
- [ ] **Paso 2: `data/catalog/LICENSE.md`.** Añadir la sección `## Ingredientes`: `ingredients.json` es una lista propia de Bobitos, que no deriva de wger ni de ninguna otra base de datos, y la licencia CC BY-SA del principio del fichero se refiere a los ejercicios. Ajustar el título del fichero a «Licencias del catálogo» si hace falta para que se entienda.
- [ ] **Paso 3: `PROJECT_PLAN.md`.** Añadir la fila `| 11/10/2026 | Catálogo común de ingredientes y marcas personales | … |`. En una frase cada cosa:
  - genéricos de lista propia (unos 350, sin nutrición) que solo crea el admin;
  - ingredientes personales, marcas (con favorita y varias tiendas) y «Dónde lo compro», privados de cada usuario en `users/{uid}/…` e `ingredientPrefs/{uid}`;
  - la marca se elige al añadir a la compra, o «Indiferente»;
  - se empieza de cero en `bobitos-dev`;
  - BEDCA descartada por licencia;
  - spec en `docs/superpowers/specs/2026-10-11-catalogo-ingredientes-design.md`.
- [ ] **Paso 4:** ejecutar NODE e IMP. Esperado: verde. No se toca código, así que es una comprobación de que el árbol sigue sano.
- [ ] **Paso 5:** commit.

```bash
git add docs/INGREDIENT_CATALOG.md data/catalog/LICENSE.md PROJECT_PLAN.md docs/DATA_MODEL.md
git commit -m "docs: catálogo común de ingredientes, importación y borrado inicial"
```

---

## Ejecución (`delegating-plan-execution`, plan grande)

1. **Rama:** `agent/catalogo-ingredientes`, que ya existe y tiene la spec (commit `d0aaf30`). Commit de este plan.
2. **Tareas:** de la 1 a la 5, una tras otra, cada una con un `sonnet`. El coordinador hace `task-done` con NODE (y con IMP en las Tasks 1, 3, 4 y 5).
3. **Revisión final** con `opus`, más los arreglos. Este plan no toca reglas, autenticación ni migraciones, así que no hay re-revisión, salvo que un arreglo toque el script de borrado y eso afecte al aislamiento de datos privados.
4. **`simplify`:** un revisor `sonnet` con los 4 ángulos y un aplicador `sonnet` de la lista (a).
5. **`code-review`** en nivel `high`, más los arreglos.
6. **Parada A (el usuario revisa el contenido):**
   - el usuario revisa `data/catalog/ingredients-review.md`;
   - sus cambios los aplica un `sonnet` en `ingredients.json` y regenera la revisión con `npm run catalog:ingredients`;
   - se ejecuta NODE y se hace commit.
7. **Cierre:** el backlog y la decisión sobre la rama, que es del usuario. Después vienen los planes 2 y 3. El despliegue espera a que estén los tres.

## Verificación de punta a punta

- NODE e IMP en verde.
- En el emulador:
  1. `npm run emulators`;
  2. `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 node scripts/catalog/reset-ingredients.mjs --project demo-bobitos`, en simulación;
  3. `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 node scripts/catalog/import-ingredients.mjs --project demo-bobitos --apply`. Se esperan unos 350 creados y `catalogMeta/ingredients.version = 1`;
  4. repetir la importación. Se esperan 0 escrituras y la versión sigue en 1.
- `node scripts/catalog/reset-ingredients.mjs --project dev`, **sin** clave, termina con el error de `GOOGLE_APPLICATION_CREDENTIALS` y no se conecta a nada.
