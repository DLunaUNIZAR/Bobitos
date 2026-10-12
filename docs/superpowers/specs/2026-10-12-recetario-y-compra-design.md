# Recetario y compra — Spec de diseño (compra sin duplicados + receta ampliada + catálogo de recetas)

> Resultado de `superpowers:brainstorming` (camino **arquitectónico**), aprobado por el usuario el 2026-10-12. Es el subproyecto 2 del catálogo común (el 1 fue rutinas: `2026-10-11-catalogo-rutinas-design.md`). Sale en **tres planes en serie** (A, B, C), cada uno con `superpowers:writing-plans` y ejecutado con `delegating-plan-execution`.

## Contexto

«Recetas comunes» del Recetario está vacía. Ya existen los catálogos de ejercicios (214), ingredientes (356, lista propia) y rutinas (29). El usuario quiere recetas que **casen con el catálogo de ingredientes**, para que «Añadir a la compra» resuelva marca y tienda. Fuente: **lista propia** (TheMealDB prohíbe publicar apps en el plan gratuito; Wikilibros es CC BY-SA sin estructura; los volcados de Zenodo tienen licencia dudosa).

Durante el diseño el usuario añadió un requisito: al «Añadir a la compra» desde una receta, una comida o un ingrediente, **si ya está en la lista, mostrarlo y preguntar si sumar cantidad u omitirlo**, para no duplicar entradas.

**Hoy (código):**
- `Recipe` (`core/model/Recipe.kt`): `title`, `description` (≤1000; ahí van los pasos), `category` (texto libre ≤60), `sourceRecipeId`, `sourceUrl`, `ingredients: List<Ingredient>?` (`Ingredient(name, quantity?, unit?)`, textos libres, ≤50). Sin raciones, tiempo ni pasos.
- `FirestoreRecipeRepository`: `myRecipes()` filtra por `ownerUid` (incluye las GLOBAL del admin); `updateRecipe` usa `update()` campo a campo; `List<Ingredient>.toFirestore()` escribe `{name, quantity, unit}` con `null` si faltan.
- Recetario: `RecipesUiState.owns()` = «está en `mine`»; el detalle (`RecipeSheets.kt` `RecipeDetailSheet`) muestra Editar/Eliminar si es tuya y «Guardar como mía» si no (`RecipesViewModel.fork()`). Las comunes que importe la cuenta admin saldrían en «Mis recetas» y sin «Guardar como mía» (mismo problema que la decisión 8 de rutinas).
- Importador web `JsonLdRecipeImporter` (`data/recipeimport/RecipeImporter.kt`): une `recipeInstructions` en la descripción; ignora `recipeYield` y los tiempos.
- Selector de receta del planificador (`MealsScreen.kt` `RecipePickerDialog`): lista alfabética de `mine + global` sin buscador.
- Revisión de «Añadir a la compra» (`feature/common/IngredientReview.kt`, compartida por Recetario y Comidas): agrupa y cruza con la lista por **nombre exacto en minúsculas**, cuenta también los **comprados** (les cambia la cantidad y siguen comprados), **sustituye** la cantidad, no permite omitir una fila, y escribe la unidad en `notes`. Con varias aportaciones muestra «200 g + 100 g».
- Ficha de ingrediente → «Añadir a la compra» abre el editor de la Compra con el nombre (`openShoppingWithName`); al guardar, si existe (nombre exacto, incluidos comprados) sale el diálogo «Ya en la lista» con «Actualizar cantidad» (sustituye) o «Añadir aparte» (duplica).
- `MealsViewModel.markCooked` cruza con la compra por `slug(name)`.
- `ShoppingRepository`: `addItem`, `updateItem` (no toca `purchased`), `setPurchased` (transacción), sin escritura que reactive un comprado con cantidad nueva.
- `ingredientKey` (`core/model/CatalogIngredient.kt`, duplicada en `scripts/catalog/ingredients.mjs`) es la clave tolerante del catálogo (sin palabras vacías, singular aproximado, palabras ordenadas).

## Decisiones del usuario

1. **Una spec, tres planes en serie:** A (compra sin duplicados), B (receta con pasos, raciones y tiempo + «Mis recetas»), C (catálogo de recetas). **Despliegue único** al final → **beta 21**.
2. **Modelo ampliado:** `steps`, `servings` y `totalMinutes`.
3. **Contenido:** comidas de diario, cenas rápidas y ligeras, desayunos y meriendas, postres y repostería; **~60 recetas**.
4. **Categorías por tipo de plato** (11 fijas).
5. **Sin escalado** de cantidades por raciones.
6. **Compra sin duplicados** en receta, comida e ingrediente: si ya está pendiente, elegir **Sumar u Omitir**, con **Omitir por defecto**.
7. Un artículo **ya comprado** no cuenta como «en la lista»: se **vuelve a apuntar** (pendiente, con la cantidad nueva) en lugar de crear otro. **«Añadir aparte» desaparece** también del alta manual de la Compra.
8. **Comunes siempre en «Comunes»** (como la decisión 8 de rutinas): «Mis recetas» solo las privadas del usuario; todas las GLOBAL en «Comunes»; «Guardar como mía» en cualquier común, también para el admin.
9. El editor de pasos permite **subir/bajar**; el selector de receta del planificador gana **buscador** y orden «Mías» → «Comunes».
10. En el catálogo, **unidades de lista cerrada** (para que las cantidades se puedan sumar).

## Parte A · Compra sin duplicados (plan A, rama `agent/compra-sin-duplicados`)

### A1. Reconocer un repetido

Función pura compartida (p. ej. `shoppingMatchKey(name) = ingredientKey(name).ifEmpty { name.trim().lowercase() }`) usada en los tres sitios y en `markCooked` (sustituye a `slug`). «Tomates» y «tomate» son el mismo artículo.

Al cruzar con la lista: si hay varios artículos con la misma clave, manda el **primero pendiente**; si no hay pendiente, el primero comprado.

### A2. Sumar cantidades

Objeto puro (p. ej. `ShoppingQuantity`) con `sum(a: String?, b: String?): String?`:
- Lee «número [unidad]». Número: entero, decimal con coma o punto, o fracción `a/b`.
- Unidades equivalentes: g (g, gr, gramo, gramos), kg (kg, kilo, kilos), ml, l (l, litro, litros), ud (ud, uds, u, unidad, unidades); cualquier otra se compara en minúsculas y sin plural (-s/-es), así «cucharadas» = «cucharada». Sin unidad solo suma con sin unidad.
- Si casan → suma con decimal de coma y sin ceros sobrantes («1/2» + «1/2» → «1»; «1,5 kg» + «500 g» no casan). Se escribe la unidad del primer sumando.
- Si no casan → `"$a + $b"`. Si uno es null o vacío → el otro.
- No convierte g↔kg ni ml↔l.

### A3. Diálogo de revisión (receta y comida)

- Agrupa los ingredientes por `shoppingMatchKey` (el nombre mostrado es el de la primera aparición) y suma las aportaciones con `ShoppingQuantity.sum` («200 g» + «300 g» → «500 g»).
- La cantidad de cada aportación ya va con su unidad («200 g»); la unidad **deja de escribirse en `notes`**. Los artículos antiguos con la unidad en notas no se migran.
- Tres tipos de fila:
  - **Nueva:** como hoy (cantidad editable, marca y tienda si el ingrediente está reconocido).
  - **Ya en la lista (pendiente):** pista «Ya en la lista: X · receta: Y» y selector **Omitir / Sumar**, con **Omitir** marcado. Con Omitir no se escribe nada. Con Sumar aparece el campo de cantidad relleno con `sum(existente, receta)`, editable; se actualiza **solo la cantidad** (conserva notas, tienda y marca).
  - **Ya comprado:** pista «Ya comprado: se vuelve a apuntar», campo de cantidad con la de la receta, editable; al confirmar se reactiva ese artículo (pendiente, cantidad nueva, conserva notas, tienda y marca).
- La cantidad se valida en el campo (≤40, como `ShoppingValidation`): error visible y Confirmar deshabilitado, en lugar de fallar al escribir.
- Si al confirmar no hay nada que escribir (todo Omitir), se cierra con el aviso «Ya estaba todo en la lista».

### A4. Alta en la Compra (manual y desde la ficha de ingrediente)

El editor de la Compra (al **crear**; editar no cambia) busca por `shoppingMatchKey`:
- **Pendiente:** la pista en línea se mantiene; al guardar, el diálogo «Ya en la lista: X» ofrece **Sumar cantidad** (cantidad = `sum(existente, escrita)`, conserva notas, tienda y marca del existente) o **No añadir** (cierra sin escribir). **«Añadir aparte» desaparece.**
- **Comprado:** al guardar se reactiva ese artículo con los valores del editor (cantidad, notas, tienda, marca), sin preguntar.

### A5. Repositorio y reglas

- `ShoppingRepository` gana una escritura para **volver a apuntar** un comprado en **una sola operación**: `purchased = false`, `purchasedBy/Name/At = null`, cantidad, notas, tienda, marca, `updatedBy/At`. Implementación y fakes de test.
- Reglas: en principio no cambian (la actualización ya admite esos campos); el EMU lo comprueba y, si la regla de atribución lo impide, se ajusta.

### A6. Pruebas

| Suite | Qué |
|---|---|
| JVM | `shoppingMatchKey` (plurales, mayúsculas, sin clave); `ShoppingQuantity.sum` (enteros, coma, fracciones, sinónimos de unidad, plurales, sin unidad, no casan, null); filas de revisión (pendiente → Omitir por defecto, Sumar, comprado → reactivar, nueva, agrupar plurales); `applyIngredientReview` (omitir no escribe, sumar actualiza cantidad, reactivar, nueva sin unidad en notas, nada que escribir); editor de la Compra (Sumar, No añadir, comprado reactivado); `markCooked` con la clave nueva |
| EMU | reactivar un comprado en una escritura |
| Punta a punta (emulador) | receta y semana del planificador con un ingrediente pendiente y otro comprado; ficha de ingrediente ya en la lista; capturas claro/oscuro |

## Parte B · Receta con pasos, raciones y tiempo + «Mis recetas» (plan B, rama `agent/receta-pasos`)

### B1. Modelo, repositorio y reglas

- `Recipe` gana `steps: List<String>? = null`, `servings: Int? = null`, `totalMinutes: Int? = null`. La descripción sigue (≤1000) como entradilla. **Sin migración:** las recetas antiguas con los pasos en la descripción se ven igual.
- Límites: hasta **30 pasos**, cada uno **≤500** caracteres (se recortan y se descartan los vacíos); `servings` **1–50**; `totalMinutes` **1–1440**. Validación en `RecipesValidation` con mensajes nuevos de `RecipeUiMessage` y cadenas en `strings.xml`.
- `RecipeRepository.createRecipe`/`updateRecipe` reciben `steps`, `servings` y `totalMinutes`. `FirestoreRecipeRepository` escribe **siempre** `steps` como lista (vacía si no hay) y `servings`/`totalMinutes` como entero o `null`; `toRecipe` los lee. Fakes de test actualizados.
- Reglas (`firestore.rules`):
  - `validRecipeShape`: `hasOnly` añade `steps`, `servings`, `totalMinutes` y `source`; `steps` es lista de tamaño ≤30; `servings` null o int 1–50; `totalMinutes` null o int 1–1440; `source` con `validIngredientSource`.
  - `validNewRecipe`: el cliente no puede crear `source`.
  - `validRecipeUpdate`: `affectedKeys` añade `steps`, `servings` y `totalMinutes` (no `source`; `updateRecipe` usa `update()` y lo conserva).

### B2. Ficha y editor

- **Ficha** (`RecipeDetailSheet`): bajo el título, «4 raciones · 40 min» si hay datos (cada parte sale sola si falta la otra); después entradilla, categoría, ingredientes y la sección **«Preparación»** con los pasos numerados.
- **Editor** (`RecipeEditor`, `RecipeDraft` parcelable): raciones y tiempo (min) como dos campos numéricos en una fila; lista de **pasos** al estilo de `IngredientsEditor`: «Paso N», texto de varias líneas, borrar, **subir/bajar** y «Añadir paso» (hasta 30).

### B3. Importador web

`ImportedRecipe` gana `steps`, `servings` y `totalMinutes`. `JsonLdRecipeImporter`:
- `recipeInstructions` → `steps` (recortados a 500 y a 30); la descripción de la web → entradilla.
- `recipeYield` (número, texto como «4 raciones» o array) → primer entero en 1–50.
- `totalTime` (ISO 8601: `PT1H30M`, `PT45M`, `P0DT1H`) → minutos; si falta, `prepTime` + `cookTime`; fuera de 1–1440 → null.

### B4. «Guardar como mía» y secciones

- `RecipesViewModel.fork()` copia también `steps`, `servings` y `totalMinutes`.
- «Mis recetas» = solo las **PRIVATE** del usuario (filtradas en el ViewModel, como `d79fb5a` en rutinas); «Comunes» = todas las GLOBAL.
- Acciones del detalle: privada propia → Editar/Eliminar; común → «Guardar como mía» si `canWrite` (también el admin) y, además, Editar/Eliminar si `isAdmin`.

### B5. Selector de receta del planificador

`RecipePickerDialog` gana un **buscador** (título y categoría) y muestra primero las recetas privadas propias y después las comunes, cada fila con la etiqueta «Mía» o «Común» (cadenas propias, como `sport_routine_origin_*`).

### B6. Pruebas

| Suite | Qué |
|---|---|
| EMU (`tests/firebase-emulators.test.mjs`) | crear/editar con pasos, raciones y tiempo; límites (31 pasos, raciones 0/51, tiempo 0/1441); cliente no crea `source`; admin edita una común con `source` y se conserva |
| JVM | mapeo del repositorio; validación y borrador (subir/bajar, recorte de pasos); importador web (pasos, `recipeYield` en sus formas, ISO 8601, `prepTime` + `cookTime`); `fork()`; secciones y acciones (admin incluido); selector (orden, búsqueda, etiquetas) |
| Punta a punta (emulador) | crear con pasos → ficha → editar → copiar común → planificar con el buscador; capturas claro/oscuro |

## Parte C · Catálogo de recetas (plan C, rama `agent/catalogo-recetas`)

### C1. Contenido y formato

~60 recetas de lista propia, en 11 categorías fijas:

| Categoría | Nº | Categoría | Nº |
|---|---|---|---|
| Legumbres | ~5 | Ensaladas | ~4 |
| Arroces | ~4 | Huevos | ~4 |
| Pasta | ~4 | Sopas y cremas | ~5 |
| Carnes | ~6 | Desayunos y meriendas | ~9 |
| Pescados | ~5 | Postres | ~9 |
| Verduras | ~4 | | |

(≈30 de diario, ≈12 cenas rápidas y ligeras, 9 desayunos y meriendas, 9 postres.)

`data/catalog/recipes.json` (`{schemaVersion: 1, recipes: [...]}`):
```json
{ "title": "Lentejas estofadas", "category": "Legumbres",
  "description": "Las de toda la vida, con chorizo y verduras.",
  "servings": 4, "totalMinutes": 60,
  "ingredients": [
    { "name": "Lentejas pardinas", "quantity": "300", "unit": "g" },
    { "name": "Ajo", "quantity": "2", "unit": "dientes" },
    { "name": "Sal" } ],
  "steps": ["Pon las lentejas en remojo…", "…"] }
```

### C2. Validación y revisión

`scripts/catalog/recipes.mjs` (validación + construcción) y `build-recipes.mjs` (CLI):
- `id = slug(title)` (`normalize.mjs`); títulos e ids únicos; título 1–120; categoría de las 11; descripción ≤1000; `servings` 1–50 y `totalMinutes` 1–1440 obligatorios; 1–50 ingredientes; 1–30 pasos, cada uno ≤500.
- Cada ingrediente se resuelve con `ingredientKey` (`ingredients.mjs`) contra `ingredients.json` y se escribe con el **nombre canónico** del catálogo; si no existe, falla y sugiere el más parecido. Sin ingredientes repetidos (por clave) en una receta. Los genéricos que de verdad falten se **añaden a `ingredients.json`** dentro del plan (y entran en el despliegue). El agua no se lista.
- Cantidad: número (entero, decimal con coma o fracción) o ausente («al gusto», sin unidad). Si hay cantidad, la unidad es obligatoria y de la lista cerrada: g, kg, ml, l, ud, cucharada, cucharadita, pizca, diente, rebanada, hoja, ramita (singular o plural).
- Genera `data/catalog/recipes-review.md` («no se edita a mano»), por categorías: título, «4 raciones · 60 min», entradilla, ingredientes («Lentejas pardinas — 300 g»; «Sal — al gusto») y pasos numerados. **Parada:** el usuario lo revisa antes de cualquier importación.

### C3. Firestore e importador

**Documento** `recipes/{slug}`: `visibility: "GLOBAL"`, `ownerUid`/`createdBy`/`updatedBy` = `CATALOG_ADMIN_UID`, `createdByName: "Catálogo Bobitos"`, `title`, `description`, `category`, `servings`, `totalMinutes`, `steps`, `ingredients`, `createdAt`/`updatedAt`, `source: {provider: "bobitos", importedAt}`. Ingredientes y pasos se escriben **idénticos** a la app (`{name, quantity, unit}` con `null` si faltan; `steps` como lista) para que «sin cambios» se detecte bien.

`scripts/catalog/import-recipes.mjs` + `recipe-import-plan.mjs` sobre `planCatalogImport`/`formatImportPlan`/`commitCatalogOps` (`import-core.mjs`) y `resolveTarget`/`parseImportArgs` (`admin-cli.mjs`), como rutinas:
- Simulación por defecto («Simulación: no se ha escrito nada»); `--apply` escribe.
- Nunca borra (las huérfanas se informan); se salta recetas de usuarios (`ownerUid` ≠ admin), comunes manuales sin `source` y comunes editadas en la app (`updatedAt > source.importedAt`).
- **Sin `catalogMeta`** (`metaPath` nulo): el Recetario usa escucha en vivo.
- npm: `catalog:recipes` (valida + revisión) y `catalog:import-recipes`.

### C4. Documentación

- `data/catalog/LICENSE.md`: recetas = lista propia, sin derivar de ninguna base de datos.
- `docs/RECIPE_CATALOG.md` (nuevo, al estilo de `docs/ROUTINE_CATALOG.md`): formato, unidades, validación, revisión, importación.
- `docs/DATA_MODEL.md`: `steps`, `servings`, `totalMinutes` y `source` en recetas; la unidad ya no va en `notes` de la compra.
- `docs/RECIPES_ADMIN.md`: el catálogo se importa con el script; las comunes a mano siguen siendo posibles.
- Créditos (`CreditsDialog.kt`): sin cambios (el bloque «Catálogo Bobitos» cubre el contenido propio).

### C5. Pruebas

| Suite | Qué |
|---|---|
| NODE (`npm run test:scripts`, nuevos `tests/catalog-recipes.test.mjs` + `catalog-recipe-import-plan.test.mjs`) | ingrediente desconocido (con sugerencia), nombre canónico, repetido por clave, unidad fuera de lista, cantidad sin unidad, categoría inválida, límites, título repetido, forma idéntica a la app, plan de importación; el `recipes.json` real valida |
| IMP (`test:catalog-import`, nuevo `tests/recipe-import.emulator.test.mjs`) | crear, actualizar, sin cambios, saltar editadas en la app, manuales y de usuarios; sin `catalogMeta` |
| Punta a punta (emulador) | importar → «Comunes» → ficha con pasos → «Guardar como mía» → planificar → añadir a la compra con un ingrediente ya en la lista |

## Orden y despliegue

- **Tres planes en serie**, cada uno en su rama y mergeado en main antes del siguiente: A → B → C. Cada plan cierra con revisión final (Opus), simplify, code-review high y punta a punta en emulador.
- **Despliegue único** tras el plan C, cada paso confirmado por el usuario (trampas de beta: `--project dev`, JAVA_HOME de Android Studio, `beta:distribute` sube el APK viejo si falla el build): reglas → (si C añadió ingredientes) simulación + `--apply` de ingredientes → simulación + `--apply` de recetas (clave en `~/.config/bobitos/bobitos-dev-adminsdk.json`) → **beta 21** con notas en `distribution/release-notes.txt`.

## Fuera de alcance

Escalar cantidades por raciones; migrar las recetas antiguas a pasos; etiquetas de momento del día; nutrición por receta; imágenes de recetas; fusionar duplicados que ya existan en la Compra; convertir unidades (g↔kg, ml↔l); sumar cantidades en el diálogo de tachar al cocinar.

## Verificación de esta spec

Cada decisión (1–10) tiene sección: 1→Orden y despliegue; 2→B1; 3→C1; 4→C1–C2; 5→Fuera de alcance; 6→A1–A4; 7→A3–A5; 8→B4; 9→B2, B5; 10→C2 (y A2 para la suma). Piezas reutilizadas: `ingredientKey`, `slug`, `IngredientReviewDialog`/`buildIngredientReviewRows`/`applyIngredientReview`, `ShoppingValidation`, `RecipesViewModel.fork()`, `planCatalogImport`, `commitCatalogOps`, `resolveTarget`, `parseImportArgs`, `validIngredientSource`, `CATALOG_ADMIN_UID`, patrón de etiquetas «Mía/Común» de `SportScreen`.
