# Catálogo común de ejercicios desde wger — Plan de implementación

> **Para agentes:** se ejecuta con el método `delegating-plan-execution`, **plan grande** (8 tareas): pasos 1–6, incluido `simplify`. Cada tarea la hace un subagente `sonnet`, en serie. La revisión final la hace `opus` (si falla, `fable`). Los pasos usan casillas (`- [ ]`).

**Objetivo:** llenar el catálogo común de ejercicios (colección raíz `exercises`, hoy vacía) con una selección revisada de unos 200–300 ejercicios de wger en español, con descripción, material y atribución. Además, que la app sepa mostrarlos, editarlos y buscarlos a esa escala.

**Arquitectura:**
- Una tubería Node reproducible: wger → candidatos normalizados → selección versionada → `data/catalog/exercises.json` → importador `firebase-admin` (idempotente y en simulación por defecto).
- La app amplía `CatalogExercise` con descripción, material y fuente, y añade el tipo `PESO_CORPORAL`.
- Las reglas de Firestore validan los campos nuevos; la fuente (`source`) solo la escribe el Admin SDK.

**Stack:** Node 20–26 (`node --test`), `firebase-admin` 13.x, Kotlin, Jetpack Compose, Hilt, Firestore, ktlint y detekt.

**Spec (decisiones del usuario, 2026-10-10):**
- Fuente **wger** (CC-BY-SA, con atribución).
- Campos: **nombre, tipo, grupo + descripción y material**, sin imágenes.
- **Selección revisada** de unos 200–300 ejercicios; el usuario revisa la lista antes de cargarla.
- Nuevo tipo **«Peso corporal»**: series con repeticiones y peso opcional.

## Contexto

El catálogo común de ejercicios, ingredientes, rutinas y recetas está vacío, y es el principal problema actual de la app. Se empieza por los ejercicios.

Fuentes comparadas el 2026-10-10:
- **wger:** 920 ejercicios, 645 con nombre y descripción en español, licencia por traducción (CC-BY-SA 4.0/3.0, algunas CC0 o CC-BY 4.0).
- **free-exercise-db:** solo en inglés.
- **exercises-dataset:** nombres en inglés y medios de pago.
- **RepDB:** 250 ejercicios y no permite redistribuir.
- **ExerciseDB/ExRx:** de pago.

Hechos de la app que condicionan el plan:
- `CatalogExercise` solo tiene `name`, `type` (4 valores) y `muscleGroup` libre.
- `catalog()` usa `.limit(500)` sin orden.
- El selector de `ExerciseListEditor` no es lazy y no tiene buscador.
- El parser descarta en silencio los documentos con un tipo desconocido.
- Las reglas no admiten campos nuevos.
- No hay `firebase-admin` ni scripts de semilla.

## Restricciones globales

- **Idioma:** UI y datos en español. Las cadenas nuevas van a `app/src/main/res/values/strings.xml` (el único `values*`).
- **Valores de tipo:** `MAQUINA, PESO_LIBRE, PESO_CORPORAL, CARDIO, OTROS`.
- **Valores de material** (mismo orden en Kotlin `ExerciseEquipment` y en JS `EQUIPMENT`): `BARRA, BARRA_Z, MANCUERNAS, KETTLEBELL, DISCO, POLEA, MAQUINA, BANCO, BANCO_INCLINADO, BARRA_DOMINADAS, ESTERILLA, FITBALL, BANDA_ELASTICA`.
- **Licencias admitidas:** `CC-BY-SA-3.0, CC-BY-SA-4.0, CC-BY-4.0, CC0-1.0`. La ODbL (id 5 de wger) y los ids desconocidos se descartan. El conjunto derivado se publica bajo **CC BY-SA 4.0**.
- **Límites:** `name` ≤ 120; `muscleGroup` ≤ 60; `description` ≤ 2000 (la tubería recorta a 1500); `source.author` ≤ 200; `source.url` ≤ 200 y empieza por `https://wger.de/`.
- **Ids y slug:** el id del documento es `slug(name)`. El `slug` JS es una copia exacta del de Kotlin (`core/model/CatalogIngredient.kt:31-38`).
- **Admin:** `CATALOG_ADMIN_UID = "dWWH7eRhHEPopJf5BHPB3Dp6fry1"`. Tiene que coincidir con `firestore.rules` (`recipeAdmins()`) y con `RecipeAdmins.kt`; un test lo vigila.
- **Secretos:** nunca se versionan credenciales. La clave de la cuenta de servicio vive fuera del repo y entra por `GOOGLE_APPLICATION_CREDENTIALS`.
- **Gradle:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:assembleDebug :app:ktlintCheck :app:detekt :app:testDebugUnitTest` (en adelante, **GRADLE**).
- **Tests Node:** `node --test` con los ficheros listados uno a uno, sin globs. Ningún test unitario usa la red.
- **Acciones externas** (desplegar reglas, importar en `bobitos-dev`, beta, push/PR): las confirma el usuario una a una.

## Review Focus

1. **Versiones mezcladas en un mismo espacio.** Un miembro con la app antigua edita una rutina o sesión con ejercicios `PESO_CORPORAL`: los lee como OTROS y pierde las series. Solo se mitiga con las notas de la beta.
2. **Selector con teclado y giro.** El `LazyColumn` con `heightIn(max = 420.dp)` dentro del `AlertDialog` en pantallas pequeñas con el teclado abierto; que la búsqueda se conserve al girar; y que se abra con el catálogo aún vacío (carga diferida) y se rellene solo.
3. **Enlaces de atribución y de créditos.** Que no fallen si no hay navegador (`runCatching`) y que sean accesibles con TalkBack.
4. **Restos de HTML y recortes en las descripciones reales:** entidades raras, viñetas anidadas, frases cortadas, párrafos vacíos.
5. **Choques con fichas que ya existen en `bobitos-dev`:** mismo slug con otro tipo, o casi duplicados con otro slug que acabarían visibles dos veces. También una ficha importada que el admin editó en la app y que la reimportación se salta.

---

### Task 1: Tubería de datos — normalización, selección y build (Node puro)

**Ficheros:**
- Crear:
  - `scripts/catalog/normalize.mjs`
  - `scripts/catalog/selection.mjs`
  - `scripts/catalog/fetch-wger.mjs`
  - `scripts/catalog/build-catalog.mjs`
  - `data/catalog/LICENSE.md`
  - `tests/fixtures/wger-exerciseinfo.sample.json`
  - `tests/catalog-normalize.test.mjs`
  - `tests/catalog-selection.test.mjs`
- Modificar: `package.json`. Scripts nuevos `catalog:fetch`, `catalog:candidates` (`node scripts/catalog/build-catalog.mjs --candidates`) y `catalog:build`; y `test:scripts` pasa a `node --test tests/beta-release-check.test.mjs tests/catalog-normalize.test.mjs tests/catalog-selection.test.mjs`.

**Interfaces que produce:**

`normalize.mjs`, funciones puras y sin dependencias:

- **Constantes:**
  - `SPANISH = 4`, `EXERCISE_TYPES`, `EQUIPMENT`.
  - `WGER_EQUIPMENT` (wger → propio): `{1:"BARRA", 2:"BARRA_Z", 3:"MANCUERNAS", 4:"ESTERILLA", 5:"FITBALL", 6:"BARRA_DOMINADAS", 7:null, 8:"BANCO", 9:"BANCO_INCLINADO", 10:"KETTLEBELL", 11:"BANDA_ELASTICA", 12:"POLEA"}`.
  - `CATEGORY_GROUP`: `{8:"Brazos", 9:"Piernas", 10:"Abdomen", 11:"Pecho", 12:"Espalda", 13:"Hombros", 14:"Gemelos", 15:null}`.
  - `PRIMARY_MUSCLE_GROUP`, que afina solo las categorías 8 y 9: `{1:"Bíceps", 13:"Bíceps", 5:"Tríceps", 10:"Cuádriceps", 11:"Isquiotibiales", 8:"Glúteos", 7:"Gemelos", 15:"Gemelos"}`.
  - `LICENSES`: `{1:"CC-BY-SA-3.0", 2:"CC-BY-SA-4.0", 3:"CC0-1.0", 4:"CC-BY-4.0"}`.
  - `KEEP_CASE` (Scott, Arnold, Pallof, Zottman, Smith, Jefferson…).
  - `ACCENT_FIXES` (jalon→jalón, extension→extensión, triceps→tríceps, biceps→bíceps, elevacion→elevación, flexion→flexión, frances→francés, cuadriceps→cuádriceps, gluteo(s)→glúteo(s), bulgara→búlgara, pajaro→pájaro, maquina→máquina, hiperextension→hiperextensión, traccion→tracción).
  - `wgerExerciseUrl(id)`: el único sitio donde se forma la URL de la ficha. El implementador comprueba el formato real (la web de wger tiene un control antibots); si no puede, usa `https://wger.de/es/exercise/<id>/view/` y lo anota en su informe.
- **`htmlToText(html): string`.** `<p>` y `<br>` pasan a salto de línea; cada `<li>` a «• »; quita etiquetas; decodifica entidades con nombre y numéricas; une espacios; deja como mucho una línea en blanco seguida.
- **`truncateText(text, max = 1500): string`.** Corta en el último fin de frase anterior a `max`; si no hay, en un límite de palabra y añade «…».
- **`normalizeName(raw): string`.** Mayúscula inicial y el resto en minúsculas, salvo `KEEP_CASE` y los acrónimos de 3 letras o menos («SZ», «TRX»). No cuentan como acrónimo las palabras vacías españolas (a, y, o, e, u, al, de…). Aplica `ACCENT_FIXES`.
- **`slug(name)`.** NFD, quita `\p{M}`, pasa a minúsculas, cambia `[^a-z0-9]+` por `-` y recorta los `-` de los extremos.
- **`foldText(s)`** y **`nearDuplicateKey(name)`.** La clave son los tokens del slug sin palabras vacías, en singular (quita la `s` final si el token tiene más de 3 letras y no acaba en `ss`), ordenados.
- **`inferType({categoryId, equipment, name}) → {type, reason: "categoria"|"equipo"|"nombre"|"defecto"}`.** Gana la primera regla que encaje:
  1. Categoría 15 → CARDIO.
  2. Material POLEA o MAQUINA → MAQUINA.
  3. Material BARRA, BARRA_Z, MANCUERNAS, KETTLEBELL o DISCO → PESO_LIBRE. Banco y esterilla se ignoran.
  4. Material BARRA_DOMINADAS → PESO_CORPORAL.
  5. Nombre plegado que encaja con `MACHINE_WORDS` (`maquina|polea|prensa|smith|multipower|contractora|pec ?deck|jalon|hack|extension de (piernas|cuadriceps)|curl femoral|abductor|aductor`) → MAQUINA.
  6. Nombre que encaja con `FREE_WEIGHT_WORDS` (`mancuerna|barra|disco|kettlebell|pesa rusa|landmine`) → PESO_LIBRE.
  7. Material BANDA_ELASTICA o FITBALL → OTROS.
  8. Cualquier otro caso → PESO_CORPORAL.
- **`toCandidate(info) → {candidate?, rejected?}`.**
  - `Candidate = {wgerId, uuid, name, slug, type, typeReason, muscleGroup, description, equipment, source:{provider:"wger", id, author, license, url}, flags}`.
  - Motivos de descarte: `sin-traduccion-es`, `sin-descripcion` (menos de 20 caracteres tras limpiar), `licencia-no-admitida`.
  - Si hay varias traducciones españolas, elige la de descripción más larga.
  - Autor: `author_history` sin repetidos y unido con «, »; si no hay, `license_author` de la traducción; si no, el del ejercicio; si no, «colaboradores de wger».
  - Licencia: la de la traducción; si falta, la del ejercicio.

`selection.mjs`, funciones puras:
- **`validateEntry(entry): string[]`.** Replica las reglas de Firestore y las restricciones globales; además, el id debe ser igual a `slug(name)` y no estar vacío.
- **`buildCatalog({candidates: Map<number, Candidate>, selection, fetchedAt}) → {catalog:{schemaVersion:1, license:"CC-BY-SA-4.0", source, fetchedAt, exercises}, problems, warnings}`.**
  - Aplica las correcciones (`name`, `type`, `muscleGroup`, `equipment`, `description`) y recalcula el slug.
  - Ordena el material según `EQUIPMENT` y las entradas por `id`.
  - Son problemas: id inexistente, sin traducción, slug repetido, enum inválido, y el mismo id seleccionado y excluido.
  - Son avisos: casi duplicados, y motivo «nombre» o «defecto».
- **`scoreCandidate(c)`.** Suma por `COMMON_PATTERNS` (press, sentadilla, peso muerto, remo, dominada, curl, fondos, zancada, plancha, hip thrust, jalón, elevación, aperturas, extensión, prensa, crunch, face pull, encogimiento), por descripción de 200 caracteres o más y por licencia CC-BY-SA-4.0. Resta por `EXCLUDE_PATTERNS` (estiramiento, movilidad, `\d{3,}`, rehabilitación, yoga).
- **`renderCandidates(candidates, rejected)`.** Markdown ordenado por puntuación y agrupado por casi duplicados.
- **`renderReview(catalog, warnings, notes)`.** Markdown con:
  - Totales por grupo, por tipo y por licencia; avisos y notas.
  - Una tabla por grupo muscular: `Nombre | Tipo | Material | wger | Licencia · autor | Descripción (≤160 car.)`, escapando `|`.
  - Un anexo con los excluidos.

CLI:
- **`fetch-wger.mjs`.** Recorre `https://wger.de/api/v2/exerciseinfo/?limit=200` siguiendo `next`, con la cabecera `User-Agent: BobitosCatalogBuilder/1.0 (+https://github.com/DLunaUNIZAR/Bobitos)` y 3 reintentos. Escribe `build/catalog/wger-exerciseinfo.json` = `{fetchedAt, count, results}` y comprueba que `results.length === count`.
- **`build-catalog.mjs`.**
  - Con `--candidates`: genera `build/catalog/candidates.{md,json}`.
  - Sin argumentos: escribe `data/catalog/exercises.json` (`JSON.stringify(…, null, 2) + "\n"`, determinista) y `data/catalog/exercises-review.md`.
  - Sale con código 1 si hay problemas; avisa si el total queda fuera de 200–300.

**`data/catalog/LICENSE.md`** dice:
- que el contenido es CC BY-SA 4.0, derivado de wger.de y de sus autores (con el enlace);
- que cada entrada conserva su licencia y su autor de origen;
- y qué cambios se hicieron: HTML pasado a texto, recortes, nombres normalizados, tipo, grupo y material asignados, y selección.

**Fixture** (unos 8 ejercicios con la estructura real de `exerciseinfo`): 257 (barra), 238 (mancuernas), un par de casi duplicados (245 y 211), uno con disco sin material (254), uno de categoría 15, uno con licencia 5 y uno sin `language: 4`.

- [ ] **Paso 1: tests en rojo** en `tests/catalog-normalize.test.mjs`:
  - `htmlToText convierte párrafos, listas y entidades`: `<p>Hola&nbsp;<strong>mundo</strong></p><ul><li>Uno</li><li>Dos</li></ul>` da `"Hola mundo\n\n• Uno\n• Dos"`; decodifica `&aacute;`, `&#39;`, `&quot;` y `&amp;`.
  - `truncateText respeta el máximo y corta en frase o palabra`.
  - `normalizeName unifica mayúsculas y tildes`:
    - «Curl con Mancuernas en Banco Scott» → «Curl con mancuernas en banco Scott».
    - «  press   BANCA » → «Press banca».
    - «Press Francés con Barra SZ» → «Press francés con barra SZ».
    - «Jalón A La Cara» → «Jalón a la cara».
    - «extension de triceps en polea» → «Extensión de tríceps en polea».
  - `slug coincide con el de Kotlin`: los mismos casos que `CatalogIngredientTest.kt` («Jamón Serrano»→`jamon-serrano`, «Ñoquis»→`noquis`, «50% cacao!»→`50-cacao`, «—··—»→`""`).
  - `nearDuplicateKey agrupa singular/plural y orden`: mancuerna y mancuernas dan la misma clave; «Sentadilla frontal» y «Sentadilla trasera», no.
  - `inferType aplica las reglas por orden`, en tabla:
    - Cardio con kettlebell → CARDIO.
    - POLEA con BARRA → MAQUINA.
    - MANCUERNAS con BANCO → PESO_LIBRE.
    - BARRA_DOMINADAS → PESO_CORPORAL.
    - Sin material y «Prensa de piernas» → MAQUINA/nombre.
    - Sin material y «Elevaciones frontales con disco» → PESO_LIBRE/nombre.
    - ESTERILLA con «Plancha» → PESO_CORPORAL/defecto.
    - Solo BANDA_ELASTICA → OTROS.
    - FITBALL con MANCUERNAS → PESO_LIBRE.
  - `toCandidate descarta sin español, sin descripción u ODbL y conserva la atribución`: autor de `author_history`, licencia de la traducción, y 257 da el grupo «Cuádriceps».
- [ ] **Paso 2: tests en rojo** en `tests/catalog-selection.test.mjs`:
  - `buildCatalog aplica correcciones y recalcula el id`.
  - `buildCatalog falla con id inexistente, slug repetido, enum inválido o seleccionado+excluido`.
  - `buildCatalog avisa de casi duplicados sin fallar`.
  - `buildCatalog es determinista`: dos llamadas dan el mismo JSON y las entradas salen ordenadas.
  - `validateEntry rechaza nombre >120, grupo >60, descripción >2000 y url que no es de wger`.
- [ ] **Paso 3:** `npm run test:scripts` → Esperado: FALLA porque faltan los módulos.
- [ ] **Paso 4:** implementar `normalize.mjs`, `selection.mjs`, los dos CLI, `LICENSE.md` y los scripts de `package.json`.
- [ ] **Paso 5:** `npm run test:scripts` → Esperado: todo en verde, incluido `beta-release-check`.
- [ ] **Paso 6:** commit `feat(catalogo): tubería de datos de ejercicios desde wger`.

---

### Task 2: Selección curada y paquete de revisión (necesita red)

**Ficheros:**
- Crear: `data/catalog/exercises-selection.json`.
- Generados y versionados: `data/catalog/exercises.json` y `data/catalog/exercises-review.md`.

**Interfaces:**
- Consume el CLI de la Task 1.
- Produce `exercises-selection.json` = `{ "notes": [string], "entries": [{ "wgerId": number, "name"?, "type"?, "muscleGroup"?, "equipment"?, "description"? }], "excluded": [{ "wgerId": number, "reason": string }] }`.
- Produce `exercises.json`, que leen la Task 3 y el importador.

- [ ] **Paso 1:** `npm run catalog:fetch && npm run catalog:candidates`.
- [ ] **Paso 2:** curar la selección a partir de `build/catalog/candidates.md`. Objetivo por grupo:

  | Grupo | Ejercicios |
  |---|---|
  | Pecho | 25–30 |
  | Espalda | 30–35 |
  | Hombros | 25–30 |
  | Bíceps, Tríceps y Brazos | 35–40 |
  | Cuádriceps, Isquiotibiales y Glúteos | 45–55 |
  | Gemelos | 6–10 |
  | Abdomen | 25–30 |
  | Cardio | 10–15 |

  - Uno por grupo de casi duplicados.
  - Excluir estiramientos, movilidad, rehabilitación, aparatos de marca y variantes exóticas.
  - Corregir tipo, material y nombre cuando el motivo sea «nombre» o «defecto».
  - Anotar en `notes` los imprescindibles que falten en wger. No se inventan fichas sin fuente.
- [ ] **Paso 3:** `npm run catalog:build` → Esperado: sin problemas y con un total entre 200 y 300. Ejecutarlo otra vez → `git diff --exit-code -- data/catalog/exercises.json` sin cambios.
- [ ] **Paso 4:** `npm run test:scripts` → Esperado: verde.
- [ ] **Paso 5:** commit `feat(catalogo): selección revisable de ejercicios de wger`.

**Parada A (coordinador):** pedir al usuario que revise `data/catalog/exercises-review.md`. Solo bloquea la importación en `bobitos-dev` (paso D del despliegue), no las tareas 3–8. Cada cambio que pida se aplica, a través de un subagente `sonnet`, editando la selección y regenerando.

---

### Task 3: Importador con firebase-admin

**Ficheros:**
- Crear:
  - `scripts/catalog/import-plan.mjs`: puro, **sin importar firebase-admin**, porque lo importan los tests de reglas.
  - `scripts/catalog/import-exercises.mjs`
  - `tests/catalog-import-plan.test.mjs`
  - `tests/catalog-import.emulator.test.mjs`
- Modificar `package.json`:
  - devDependency `firebase-admin` 13.x con versión fija.
  - Scripts `catalog:import` y `test:catalog-import` (`firebase emulators:exec --only firestore --project demo-bobitos "node --test tests/catalog-import.emulator.test.mjs"`).
  - Añadir `tests/catalog-import-plan.test.mjs` a `test:scripts`.
- Modificar `.gitignore`: `*firebase-adminsdk*.json`, `*service-account*.json`.

**Interfaces que produce (`import-plan.mjs`):**
- `CATALOG_ADMIN_UID` y `CATALOG_AUTHOR_NAME = "Catálogo Bobitos"`.
- `managedFields(entry) → {name, nameLower, type, muscleGroup, description, equipment, source:{provider, id, author, license, url}}`.
- `toFirestoreDoc(entry, {now, create})`.
  - Con `create`: el documento completo, con `ownerUid`, `createdBy` y `updatedBy` iguales a `CATALOG_ADMIN_UID`, `createdByName`, `createdAt`, `updatedAt` y `source.importedAt`, todos con `now`.
  - Sin `create`: solo los campos gestionados, `updatedBy`, `updatedAt` y `source.importedAt`.
- `planImport({catalog, existing, adminUid}) → {create, update, unchanged, skippedUserOwned:[{id, ownerUid}], skippedAdminManual, skippedEditedInApp, orphaned, nearDuplicates:[{id, existingId}]}`.
  - `ExistingDoc = {id, ownerUid, fields, hasSource, updatedAtMillis, importedAtMillis|null}`.
  - Una ficha se considera editada en la app si `updatedAt > source.importedAt`.
- `formatPlan(plan): string`: el informe en español.
- `import-exercises.mjs` exporta `runImport({db, catalog, apply, log})` y tiene una guarda para el `main`.
  - Uso: `--project demo-bobitos|bobitos-dev|dev [--apply] [--catalog data/catalog/exercises.json]`.
  - Si el proyecto es `demo-*`, exige `FIRESTORE_EMULATOR_HOST`.
  - Si es `bobitos-dev`, exige que `FIRESTORE_EMULATOR_HOST` no esté definida y que exista `GOOGLE_APPLICATION_CREDENTIALS` con `project_id === "bobitos-dev"`. Se conecta con `applicationDefault()`.
  - Valida cada entrada con `validateEntry`.
  - Solo escribe con `--apply`, en lotes de hasta 400: las altas con `batch.create` y las actualizaciones con `batch.update(ref, data, {lastUpdateTime})`.
  - Nunca borra: solo informa.

- [ ] **Paso 1: tests en rojo** (`catalog-import-plan.test.mjs`):
  - `un catálogo sobre colección vacía lo crea todo`.
  - `reimportar sin cambios no escribe nada`.
  - `una ficha importada con cambios se actualiza sin tocar createdAt/ownerUid`.
  - `una ficha de usuario con el mismo slug no se pisa y se informa`.
  - `una ficha del admin sin source no se pisa`.
  - `una ficha importada editada después en la app no se pisa`.
  - `una ficha que ya no está en el JSON se informa como huérfana`.
  - `un nombre casi igual a una ficha existente se avisa`.
  - `toFirestoreDoc incluye todo lo que exige el parser de la app` (createdAt, name, ownerUid, createdBy, type).
  - `CATALOG_ADMIN_UID coincide con firestore.rules y RecipeAdmins.kt`: lee los dos ficheros.
- [ ] **Paso 2: tests en rojo en el emulador** (`catalog-import.emulator.test.mjs`):
  - `importa y una segunda pasada no escribe`.
  - `no pisa una ficha de usuario`.
  - `no sobrescribe una ficha editada en la app`.
- [ ] **Paso 3:** `npm install && npm run test:scripts` → Esperado: FALLA en los tests nuevos.
- [ ] **Paso 4:** implementar.
- [ ] **Paso 5:** `npm run test:scripts && npm run test:catalog-import` → Esperado: verde.
- [ ] **Paso 6:** commit `feat(catalogo): importador idempotente de ejercicios con firebase-admin`.

---

### Task 4: Reglas de Firestore y sus tests

**Ficheros:**
- Modificar: `firestore.rules`.
- Modificar: `tests/firebase-emulators.test.mjs`. Importa `toFirestoreDoc` de `../scripts/catalog/import-plan.mjs` y amplía `exerciseData` con los campos nuevos.

**Cambios en las reglas:**

```
// validExerciseShape: hasOnly añade "description","equipment","source"
&& d.type in ["MAQUINA","PESO_LIBRE","PESO_CORPORAL","CARDIO","OTROS"]
&& (!d.keys().hasAny(["description"]) || d.description == null
    || (d.description is string && d.description.size() <= 2000))
&& (!d.keys().hasAny(["equipment"]) || (d.equipment is list && d.equipment.size() <= 13
    && d.equipment.toSet().size() == d.equipment.size()
    && d.equipment.hasOnly(["BARRA","BARRA_Z","MANCUERNAS","KETTLEBELL","DISCO","POLEA","MAQUINA",
       "BANCO","BANCO_INCLINADO","BARRA_DOMINADAS","ESTERILLA","FITBALL","BANDA_ELASTICA"])))
&& (!d.keys().hasAny(["source"]) || validExerciseSource(d.source))

function validExerciseSource(s) {
  return s is map && s.keys().hasAll(["provider","id","license"])
    && s.keys().hasOnly(["provider","id","license","author","url","importedAt"])
    && s.provider == "wger" && s.id is int && s.id > 0
    && s.license in ["CC-BY-SA-3.0","CC-BY-SA-4.0","CC-BY-4.0","CC0-1.0"]
    && (!s.keys().hasAny(["author"]) || (s.author is string && s.author.size() <= 200))
    && (!s.keys().hasAny(["url"]) || (s.url is string && s.url.size() <= 200 && s.url.matches('https://wger[.]de/.*')))
    && (!s.keys().hasAny(["importedAt"]) || s.importedAt is timestamp);
}
// validNewExercise: && !request.resource.data.keys().hasAny(["source"])
// validExerciseUpdate: affectedKeys().hasOnly([...actuales, "description","equipment"]) → source inmutable desde cliente
```

- [ ] **Paso 1: tests en rojo:**
  - `un ejercicio acepta PESO_CORPORAL, descripción y material válidos`.
  - `un ejercicio rechaza descripción >2000, material desconocido, repetido o que no es lista`.
  - `nadie crea un ejercicio con source desde el cliente, ni siquiera el admin`.
  - `un documento del importador cumple la forma: el admin edita su descripción, nadie toca su source y otro usuario no lo edita`. Se siembra con `withSecurityRulesDisabled` y `toFirestoreDoc(entry, {now: Timestamp.now(), create: true})`.
  - `un source con proveedor o licencia no admitidos invalida cualquier edición`.
  - `el dueño añade descripción y material a un ejercicio antiguo`.
- [ ] **Paso 2:** `npm run test:emulators` → Esperado: FALLAN los tests nuevos.
- [ ] **Paso 3:** cambiar las reglas.
- [ ] **Paso 4:** `npm run test:emulators` → Esperado: todo en verde, también los tests de ejercicios que ya existían (YOGA, campo ajeno, nombre vacío).
- [ ] **Paso 5:** commit `feat(reglas): descripción, material, fuente y PESO_CORPORAL en ejercicios`.

---

### Task 5: App — modelo, datos y `PESO_CORPORAL` de punta a punta

Rutas bajo `app/src/main/java/com/dlunaunizar/bobitos/` (`APP`) y `app/src/test/java/com/dlunaunizar/bobitos/` (`UT`).

**Ficheros:**
- Modificar:
  - `APP/core/model/CatalogExercise.kt`
  - `APP/data/repository/ExerciseRepository.kt`
  - `APP/data/repository/FirestoreExerciseRepository.kt`
  - `APP/feature/exercises/ExerciseTokens.kt`
  - `APP/feature/exercises/ExerciseListEditor.kt`
  - `APP/feature/exercises/CatalogExerciseDraft.kt`
  - `APP/feature/exercises/ExercisesViewModel.kt`
  - `APP/feature/exercises/ExercisesUiState.kt`
  - `APP/feature/exercises/ExercisesScreen.kt` (solo lo mínimo para que compile)
  - `strings.xml`
  - Los dobles de `ExerciseRepository` en `UT/feature/{exercises,sport,routines}/…ViewModelTest.kt`
- Crear:
  - `APP/data/repository/CatalogExerciseFirestore.kt`
  - `UT/data/repository/CatalogExerciseFirestoreTest.kt`
  - `UT/data/repository/RoutineExerciseFirestoreTest.kt`

**Interfaces que produce:**

```kotlin
enum class ExerciseType { MAQUINA, PESO_LIBRE, PESO_CORPORAL, CARDIO, OTROS }
enum class ExerciseEquipment { BARRA, BARRA_Z, MANCUERNAS, KETTLEBELL, DISCO, POLEA, MAQUINA, BANCO,
    BANCO_INCLINADO, BARRA_DOMINADAS, ESTERILLA, FITBALL, BANDA_ELASTICA }
data class ExerciseSource(val provider: String, val sourceId: Long, val license: String, val author: String?, val url: String?)
// CatalogExercise: nuevos campos tras muscleGroup, con defaults
//   val description: String? = null, val equipment: List<ExerciseEquipment> = emptyList(), val source: ExerciseSource? = null
data class ExerciseInput(val name: String, val type: ExerciseType, val muscleGroup: String?,
    val description: String?, val equipment: List<ExerciseEquipment>)
// ExerciseRepository: createExercise(input: ExerciseInput), updateExercise(id: String, input: ExerciseInput); ExerciseFailure.DescriptionTooLong
// CatalogExerciseFirestore.kt (internal):
fun parseCatalogExercise(id: String, data: Map<String, Any?>, createdAt: Instant?, updatedAt: Instant?): CatalogExercise?
fun parseExerciseType(raw: Any?): ExerciseType            // desconocido → OTROS (antes: descartaba el doc)
fun parseExerciseEquipment(raw: Any?): List<ExerciseEquipment> // ignora desconocidos, sin duplicados, orden del enum
fun parseExerciseSource(raw: Any?): ExerciseSource?        // id vía (as? Number)?.toLong()
fun validateExerciseInput(input: ExerciseInput): ExerciseInput // recorta; descripción en blanco → null; NameRequired/NameTooLong/MuscleGroupTooLong/DescriptionTooLong(>2000)
fun ExerciseInput.toFirestoreFields(): Map<String, Any?>
fun List<CatalogExercise>.sortedForCatalog(): List<CatalogExercise> // Collator es-ES PRIMARY y desempate por id
// ExerciseTokens: PESO_CORPORAL (etiqueta exercise_type_bodyweight, color 0xFF2E7D32); isStrength incluye PESO_CORPORAL;
//   ExerciseType.weightLabelRes (PESO_CORPORAL → routines_ballast_label «Lastre (kg)»); ExerciseEquipment.labelRes
// CatalogExerciseDraft: description = "", equipment = emptyList() (rellenados en of()); fun toInput(): ExerciseInput
```

- `catalog()` pasa a `.orderBy("nameLower").limit(1000)`, con el parser puro y `sortedForCatalog()`.
- `StrengthSets` usa `draft.type.weightLabelRes`.
- Nuevas cadenas: `exercise_type_bodyweight` «Peso corporal», `routines_ballast_label`, `exercises_error_description_too_long` y 13 `exercise_equipment_*`.

- [ ] **Paso 1: tests en rojo.**
  - `CatalogExerciseFirestoreTest`:
    - `legacy document without description, equipment or source parses with defaults`
    - `description, equipment and wger source are parsed`
    - `unknown equipment is dropped and duplicates collapsed`
    - `malformed source yields null source but keeps the exercise`
    - `PESO_CORPORAL is recognised`
    - `unknown type falls back to OTROS`
    - `missing createdAt, name, ownerUid or createdBy drops the document`
    - `validateExerciseInput trims, nulls blank description and orders equipment`
    - `description over 2000 chars fails with DescriptionTooLong`
    - `toFirestoreFields writes nameLower, type and equipment names`
    - `sortedForCatalog sorts accented names with their base letter` («Ángel», «Burpee», «Remo»)
  - `RoutineExerciseFirestoreTest`: `PESO_CORPORAL survives serialize and parse` y `unknown type still falls back to OTROS`.
  - `ExerciseListEditorTest`: `bodyweight sets keep reps and optional ballast` (`SetDraft("12","")` da `ExerciseSet(12,null)`, sin duración ni nivel).
  - `ExerciseDraftSaverTest`: `PESO_CORPORAL survives rotation`.
  - `CatalogExerciseDraftTest`: `editing keeps description and equipment` y `toInput trims and nulls blank description`.
  - `ExercisesViewModelTest`: `create passes the full input to the repository` y `DescriptionTooLong failure shows its message`.
- [ ] **Paso 2:** GRADLE → Esperado: falla la compilación de los tests (aún no existen los símbolos).
- [ ] **Paso 3:** implementar y adaptar los dobles de prueba.
- [ ] **Paso 4:** GRADLE → Esperado: BUILD SUCCESSFUL.
- [ ] **Paso 5:** commit `feat(ejercicios): descripción, material, fuente y tipo Peso corporal`.

---

### Task 6: App — ficha de detalle, editor completo, búsqueda sin tildes y «Créditos y licencias»

**Ficheros:**
- Crear:
  - `APP/core/common/TextSearch.kt`: `String.foldForSearch()` y `matchesQuery(query, vararg fields: String?)`. Cada palabra de la consulta, sin tildes, tiene que aparecer en algún campo.
  - `APP/feature/exercises/ExerciseSourceTokens.kt`: `licenseLabel(code)` (`CC-BY-SA-4.0` → «CC BY-SA 4.0»), `licenseUrl(code)` (las URL `…/deed.es` de Creative Commons) y `ExerciseSource.linkUrl()` (solo si empieza por `https://wger.de/`).
  - `APP/feature/exercises/ExerciseDetailSheet.kt`: `ExerciseDetailSheet(exercise, canEdit, onEdit, onDelete, onDismiss)`, sobre `BobitosInfoSheet` (`core/designsystem/component/BobitosInfoSheet.kt`).
    - Contenido: chip de tipo y grupo; «Material: …»; la descripción, o «Sin descripción.»; y la línea «Texto adaptado de wger.de (ejercicio n.º N) · Autoría: X · Licencia CC BY-SA 4.0».
    - Los enlaces usan `LinkAnnotation.Url` con `runCatching { uriHandler.openUri }`.
  - `APP/feature/exercises/ExerciseEditorSheet.kt`: se saca aquí el editor actual (para no superar los umbrales de detekt) y se le añaden la descripción (`minLines = 3`, contador «n/2000»), una `FlowRow` de `FilterChip` de selección múltiple para el material, y los tipos desde `entries`.
  - `APP/feature/auth/CreditsDialog.kt`: `CreditsDialog(onDismiss)` con desplazamiento y enlaces. Bloques:
    - wger: CC BY-SA 3.0/4.0 (y algunos CC BY 4.0 o CC0), adaptado y compartido bajo CC BY-SA 4.0.
    - Open Food Facts: ODbL y DbCL, que exigen el aviso.
    - Nunito: SIL OFL 1.1 (ya está en `licenses/`).
  - Tests `UT/core/common/TextSearchTest.kt` y `UT/feature/exercises/ExerciseSourceTokensTest.kt`.
- Modificar:
  - `ExercisesScreen.kt`: pulsar la fila abre el detalle (`detailExerciseId` con `rememberSaveable`, resuelto con `rememberEditorSlot` de `core/designsystem/component/EditorItem.kt`); el filtro usa `matchesQuery(query, name, muscleGroup)`; se mantiene el menú contextual.
  - `ProfileScreen.kt`: un `TextButton` «Créditos y licencias» junto a «Política de privacidad».
  - `strings.xml`: cadenas `exercises_description_label`, `exercises_equipment_label`, `exercises_detail_*`, `exercises_attribution_*` y `credits_*`.

- [ ] **Paso 1: tests en rojo.**
  - `TextSearchTest`: `jalon finds Jalón`, `tokens match in any order` («press mancuerna» encuentra «Press de banca con mancuernas»), `blank query matches everything` y `muscle group is searchable`.
  - `ExerciseSourceTokensTest`: `known licence codes map to label and deed URL`, `unknown code shows raw with no URL` y `non-wger url is not linked`.
- [ ] **Paso 2:** GRADLE → Esperado: FALLA.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** GRADLE → Esperado: BUILD SUCCESSFUL.
- [ ] **Paso 5:** commit `feat(ejercicios): ficha con atribución, editor completo y créditos`.

---

### Task 7: App — selector buscable y diferido, y menos lecturas

**Ficheros:**
- Crear:
  - `APP/feature/exercises/ExercisePicker.kt`. Contiene:
    - `ExercisePickerDialog(catalog, onDismiss, onPick: (CatalogExercise?) -> Unit)`: un `AlertDialog` con `SearchField` (o un campo equivalente) con `rememberSaveable`, y un `LazyColumn(Modifier.heightIn(max = 420.dp))`. La primera fila es «Personalizado…» y el resto son `items(key = id)` con el nombre y, debajo, «grupo · tipo». Si no hay resultados, «Ningún ejercicio coincide.».
    - `internal fun filterExercisePicker(catalog, query): List<CatalogExercise>`.
  - `UT/feature/exercises/ExercisePickerFilterTest.kt`.
- Modificar:
  - `ExerciseListEditor.kt`: se borra el diálogo antiguo. El botón siempre abre el selector, aunque el catálogo aún no haya llegado. Nuevo parámetro `onCatalogNeeded: () -> Unit`, que se llama en `LaunchedEffect(Unit)`.
  - `SportViewModel.kt` y `RoutinesViewModel.kt`: `observe()` deja de suscribirse al catálogo. A cambio, `fun observeExerciseCatalog()` (idempotente), que `stopObserving()` cancela.
  - `SportScreen.kt` (`GymSessionSection`) y el editor de `RoutinesScreen.kt`: pasan `onCatalogNeeded = viewModel::observeExerciseCatalog`.
  - Los tests `SportViewModelTest` y `RoutinesViewModelTest`.

- [ ] **Paso 1: tests en rojo.**
  - `ExercisePickerFilterTest`: `empty query returns the whole catalog in order`, `matches folded name and muscle group` y `no match returns empty`.
  - `SportViewModelTest`: `does not subscribe to the exercise catalog until the session editor needs it`. Sustituye al test actual de observación. El doble cuenta las llamadas a `catalog()`: 0 tras `observe`, y 1 tras dos llamadas a `observeExerciseCatalog()`, con `exercises` ya relleno.
  - `RoutinesViewModelTest`: el equivalente.
- [ ] **Paso 2:** GRADLE → Esperado: FALLA.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** GRADLE → Esperado: BUILD SUCCESSFUL.
- [ ] **Paso 5:** commit `feat(ejercicios): selector buscable y carga diferida del catálogo`.

---

### Task 8: Documentación

**Ficheros:**
- Crear: `docs/EXERCISE_CATALOG.md`. Contenido:
  - Fuente y licencia; estructura de `data/catalog/`.
  - Cómo regenerar: `catalog:fetch`, `catalog:candidates`, curar `exercises-selection.json` y `catalog:build`.
  - Cómo revisar (`exercises-review.md`).
  - Cómo importar:
    - En el emulador.
    - En `bobitos-dev`: la clave fuera del repo, `GOOGLE_APPLICATION_CREDENTIALS`, la simulación y luego `--apply`.
    - Qué pasa con las fichas de usuarios, las editadas en la app y las huérfanas.
  - Que para quitar un ejercicio hay que sacarlo de la selección; borrarlo solo en la app no basta, porque se recrea al reimportar.
  - Tabla de tipos y material; coste en Firestore; resolución de problemas.
- Modificar:
  - `docs/DATA_MODEL.md`: sección nueva `exercises/{exerciseId}` con el esquema. Corregir `:35`: las colecciones globales son `recipes`, `routines`, `ingredients`, `exercises` e `ingredientPrefs`. Subir versión y fecha.
  - `docs/RECIPES_ADMIN.md`: añadir el tercer sitio donde vive el uid admin (`scripts/catalog/import-plan.mjs`).

- [ ] **Paso 1:** redactar.
- [ ] **Paso 2:** `npm run test:scripts && npm run test:emulators && npm run test:catalog-import` y GRADLE → Esperado: todo en verde.
- [ ] **Paso 3:** commit `docs(catalogo): catálogo de ejercicios y modelo de datos`.

---

## Ejecución (`delegating-plan-execution`, plan grande)

1. **Preparación.** Copiar este plan a `docs/superpowers/plans/2026-10-10-catalogo-ejercicios-wger.md`. Crear la rama `agent/catalogo-ejercicios-wger` desde `main` y hacer commit del plan.
2. **Tareas.** Las tareas 1 a 8 las hace cada una un subagente `sonnet`, en serie y con su brief. Después de la tarea 2 viene la **Parada A**: el usuario revisa `exercises-review.md` mientras siguen las tareas 3 a 8.
3. **Revisión final.** Un revisor `opus` (si falla, `fable`) con `code-reviewer.md`, el paquete, este Review Focus y los rulings. Los Critical e Important los arregla un `sonnet`. Re-revisión acotada **solo** a los arreglos de **reglas o permisos**: la Task 4 toca permisos.
4. **Simplificar.** Un revisor `sonnet` con los 4 ángulos. Un `sonnet` aplica solo la lista (a); la lista (b) pasa a `code-review`.
5. **Code review.** `code-review` en nivel `high`, junto con la lista (b). Los arreglos los hace un `sonnet`.
6. **Cierre.** Los menores van a `docs/superpowers/backlog.md`. Ya están previstos estos aplazados: imágenes, caché del catálogo por versión, filtros por grupo o tipo en el selector, y fichas sin fuente.

## Despliegue (acciones externas: el coordinador las confirma con el usuario una a una)

- **A.** El usuario aprueba `data/catalog/exercises-review.md`.
- **B.** `npx firebase deploy --only firestore:rules --project dev`. Va antes de la importación y de la beta: con las reglas viejas, la app nueva recibe PERMISSION_DENIED al guardar descripción o material.
- **C.** El usuario genera la clave de la cuenta de servicio de `bobitos-dev` (Consola, Configuración, Cuentas de servicio, Generar clave privada). La guarda fuera del repo (por ejemplo `~/.config/bobitos/`) y la exporta en `GOOGLE_APPLICATION_CREDENTIALS`.
- **D.** Simulación con `node scripts/catalog/import-exercises.mjs --project dev`. Se le enseña el informe al usuario y, si lo aprueba, se repite con `--apply`.
- **E.** Beta (VERSION_CODE 18) justo después de la importación. Las notas piden que **todos los miembros de cada espacio actualicen**, porque las versiones antiguas leen «Peso corporal» como «Otros» y pierden las series al guardar.
- **F.** Opcional: revocar la clave de la cuenta de servicio.
- **G.** Push y PR, o merge, según decida el usuario.

**Compatibilidad:**
- La app antigua ignora los campos nuevos y descarta las fichas `PESO_CORPORAL` (su parser exige un tipo conocido).
- Puede seguir creando y editando, porque los campos nuevos son opcionales.
- La app nueva con las reglas viejas falla al guardar; por eso B va primero.

**Coste en Spark:**
- Importación: unas 300 lecturas y 250 escrituras, una sola vez.
- Uso en la app: cada apertura en frío del catálogo cuesta unas 250–300 lecturas. Con la carga diferida de la Task 7 solo pagan Ejercicios y los editores; con 10 usuarios, menos del 10 % de las 50.000 lecturas diarias.

## Verificación de punta a punta

- `npm run test:scripts`, `npm run test:emulators`, `npm run test:catalog-import` y GRADLE, todo en verde.
- En el emulador: `npm run emulators`, después `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 node scripts/catalog/import-exercises.mjs --project demo-bobitos --apply`, y la app debug conectada a los emuladores (`npm run android:connect-emulators`):
  - **Ejercicios:** buscar «jalon» encuentra «Jalón…»; la ficha muestra descripción, material y atribución con enlace; editar descripción y material y guardar.
  - **Rutinas y gimnasio:** el selector abre al momento, busca, conserva la búsqueda al girar y deja elegir «Personalizado…». Un ejercicio «Peso corporal» registra series con repeticiones y lastre opcional.
  - **Perfil:** «Créditos y licencias» se abre y sus enlaces funcionan.
  - Reimportar sin cambios da «0 escrituras».
