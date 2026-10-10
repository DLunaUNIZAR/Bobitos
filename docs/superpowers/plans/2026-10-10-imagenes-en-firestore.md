# Imágenes de ejercicios almacenadas en Firestore — Plan de implementación

> **Para agentes:** se ejecuta con `delegating-plan-execution`. Es un **plan grande** (4 tareas): pasos 1–6, incluido `simplify`. Cada tarea la hace un subagente `sonnet`, en serie. La revisión final la hace `opus` (si falla, `fable`). La re-revisión es acotada si hay arreglos en reglas. Los pasos usan casillas (`- [ ]`).

**Objetivo:** dejar de enlazar las imágenes de wger. Las 93 miniaturas pasan a almacenarse en Firestore (colección `exerciseImages`, en WebP) y las sube el importador. La app las lee desde Firestore con su caché, sin pedir nada a terceros.

**Arquitectura:**
- **Tubería:** descarga **una sola vez** las miniaturas ya elegidas, las convierte a WebP con `sharp` y las versiona en `data/catalog/images/`.
- **Importador:**
  - sube las imágenes a `exerciseImages/{exerciseId}`;
  - compara el hash para no reescribirlas sin cambios;
  - escribe las imágenes antes que las fichas.
- **Ficha:** guarda solo los metadatos `image: {hash, author, license, sourceUrl}`.
- **App:** lee los bytes primero de la caché (`Source.CACHE`) y, si no están, del servidor. Los decodifica con `BitmapFactory` y retira Coil y OkHttp, que ya no hacen falta.

**Stack:** Node (`sharp`, devDependency nueva), firebase-admin, reglas de Firestore, Kotlin y Compose.

**Spec, decisiones del usuario (2026-10-10):**
1. Las imágenes de los ejercicios **se almacenan en Firestore**, en una colección propia y en WebP pequeño, subidas por el importador. Se lee 1 vez por móvil y después se sirven desde la caché, para no hacer consultas continuas.
2. Se añade **Open Food Facts** a la política de privacidad.
3. Los textos y el catálogo (Paradas B y C) quedan aprobados. Los textos de imágenes se reescriben por este cambio.

## Contexto

- Hoy `image` vale `{url, author, license}`, con una URL de `https://wger.de/media/…400x400_q85.jpg|png`: 53 jpg y 32 png entre las 93 fichas con imagen.
- La app la carga con Coil 3.6.3, con OkHttp, User-Agent y caché de disco.
- Las reglas validan la URL de wger media.
- Las imágenes salen en `PRIVACY_POLICY` (wger recibe la IP), en `PROJECT_PLAN` («se enlazan, no se almacenan»), en `LICENSE.md` («no se redistribuyen»), en créditos y en la documentación.
- Nada de esto está desplegado todavía (beta 18 pendiente), así que el formato se puede cambiar sin migración.
- **Open Food Facts:**
  - la app hace `GET https://world.openfoodfacts.org/api/v2/product/{código}.json?fields=product_name,brands,nutriments`, con `User-Agent: Bobitos/1.0 (Android; recipe app; …)` fijo, solo al escanear;
  - solo se guarda en `ingredients/{id}/brands/*` lo que el usuario confirma: nombre, código de barras y nutrición;
  - la política no lo menciona.
- **Menores aplazados que este cambio deja sin objeto:**
  - el crédito de la imagen se veía antes de que cargara;
  - los textos legales sobre wger.

## Restricciones globales

- **Rutas:** `APP` = `app/src/main/java/com/dlunaunizar/bobitos/` y `UT` = `app/src/test/java/com/dlunaunizar/bobitos/`.
- **Comandos de verificación**, en llamadas separadas y sin `sh -c`:
  - **GRADLE:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:assembleDebug :app:ktlintCheck :app:detekt :app:testDebugUnitTest`
  - **NODE:** `npm run test:scripts`
  - **EMU:** `npm run test:emulators` (con JAVA_HOME)
  - **IMP:** `npm run test:catalog-import` (con JAVA_HOME)
- **No volver a descargar `exerciseinfo`.** Está prohibido `npm run catalog:fetch`. La única descarga permitida es la de las **93 miniaturas** ya elegidas, desde `https://wger.de/media/`, en la Task 1.
- **Formato de imagen:**
  - WebP, como mucho 400 px de lado, calidad 80 y sin metadatos;
  - `hash` = sha256 en hexadecimal (64 caracteres) de los bytes WebP;
  - cada documento de `exerciseImages` ocupa como mucho 200 KB (comprobado por el script; muy por debajo del límite de 1 MiB de Firestore).
- **`image` en la ficha:** `null` o `{hash, author, license, sourceUrl}`:
  - `hash`: 64 caracteres hexadecimales en minúscula;
  - `author`: como mucho 200 caracteres, sin correos (si no hay, «colaboradores de wger»);
  - `license`: una de las 4 admitidas;
  - `sourceUrl`: como mucho 300 caracteres y empieza por `https://wger.de/media/`; es la procedencia, y la app nunca la carga.
- **`exerciseImages/{exerciseId}`:** `{data: bytes, contentType: "image/webp", hash, width, height, author, license, sourceUrl, updatedAt}`.
  - Lectura con `get` para usuarios verificados; sin `list`.
  - Ningún cliente escribe; lo hace el Admin SDK.
- **Las acciones externas** (desplegar, importar, beta, push) las confirma el usuario una a una.
- **Tests:** JVM puros con dobles a mano. Para detekt, extraer código antes que tocar el baseline.

## Review Focus

1. **Orden y coherencia de la importación:**
   - las imágenes se escriben antes que las fichas que apuntan a su hash;
   - una reimportación sin cambios no escribe nada;
   - un cambio solo de imagen cambia el `hash` de la ficha y sube la versión;
   - el tamaño de los lotes con datos binarios;
   - las imágenes huérfanas se informan, no se borran.
2. **Caché en la app:**
   - si la caché tiene la imagen con el mismo hash, no se lee nada del servidor;
   - si el hash es distinto o no hay caché, se lee del servidor, con un tiempo máximo;
   - sin red y sin caché, el bloque se oculta sin error;
   - la caché de Firestore es de 20 MiB y compartida: con unos 3 MB de imágenes cabe, y si no, simplemente se vuelven a leer.
3. **Decodificación:**
   - nunca en el hilo principal;
   - bytes corruptos o un hash que no coincide no rompen nada y no se muestran;
   - fondo blanco en tema oscuro;
   - el crédito solo aparece cuando la imagen se ve.
4. **Reglas:**
   - `exerciseImages` admite solo `get` de verificados, sin `list` ni escritura;
   - el nuevo `image` de la ficha sigue siendo inmutable desde el cliente;
   - una ficha antigua con `image.url` (no desplegada) no existe en producción.
5. **Licencias y privacidad:**
   - las copias son adaptaciones (redimensionadas y en WebP) de obras CC BY-SA, con autor, licencia y cambios indicados;
   - la política refleja que no hay peticiones a wger y explica Open Food Facts con exactitud: código de barras e IP; solo se guarda lo que se confirma.

---

### Task 1: Tubería — descargar, convertir y versionar las miniaturas

**Ficheros:**
- Crear:
  - `scripts/catalog/images.mjs`: funciones puras más un orquestador con E/S inyectable.
  - `scripts/catalog/build-images.mjs`: CLI.
  - `tests/catalog-images.test.mjs`
  - `tests/fixtures/tiny.png`: unos píxeles, para probar la conversión real con `sharp`.
- Modificar:
  - `scripts/catalog/wger-http.mjs`: `fetchBinary(url, opts)` con los mismos reintentos y tiempo máximo que `fetchJson`, pero `arrayBuffer()`, y `assertWgerMediaUrl(url)`.
  - `scripts/catalog/normalize.mjs`: `pickImage` y `toImage` producen `{sourceUrl, author, license}` y aún sin `hash`.
  - `scripts/catalog/selection.mjs`: `buildCatalog` completa `image.hash` leyendo `data/catalog/images/<id>.webp`; `validateEntry` con la forma nueva; `renderReview` con la columna Imagen enlazando `sourceUrl` y el hash corto.
  - `scripts/catalog/build-catalog.mjs`
  - `package.json`:
    - `sharp` con versión exacta y estable;
    - script `catalog:images`;
    - añadir el test a `test:scripts`.
  - Los tests existentes de imagen en `tests/catalog-{normalize,selection,fetch}.test.mjs`.
  - Generados y versionados: `data/catalog/images/*.webp` (93), `data/catalog/exercises.json` y `data/catalog/exercises-review.md`.

**Interfaces:**
- **`images.mjs`:**
  - `IMAGE_MAX_SIDE = 400`, `IMAGE_QUALITY = 80` y `IMAGE_MAX_BYTES = 200 * 1024`;
  - `sha256Hex(buf)`;
  - `async toWebp(buf, {sharpImpl})` → `{data, width, height}`, con `rotate()`, `resize({width: 400, height: 400, fit: "inside", withoutEnlargement: true})`, `webp({quality: 80})` y sin metadatos;
  - `async buildImages({entries, outDir, fetchBinary, sharpImpl, readExisting})`:
    - por cada entrada con `image.sourceUrl`: si ya existe `outDir/<id>.webp`, **no descarga**;
    - si no, descarga, convierte, comprueba que no supere `IMAGE_MAX_BYTES` y escribe;
    - devuelve `{written, skipped, problems}`;
    - los ficheros que ya no se usan se informan, no se borran.
- **`build-images.mjs`:** lee `data/catalog/exercises.json` (con `sourceUrl`), ejecuta `buildImages` y después regenera el catálogo, que ya lleva `hash`.

- [ ] **Paso 1: tests en rojo.**
  - `sha256Hex es estable`
  - `toWebp convierte tiny.png a WebP de ≤400 px` (con `sharp` de verdad)
  - `buildImages no descarga si el fichero ya existe`
  - `buildImages descarga, convierte y escribe con un fetch falso`
  - `buildImages informa de un tamaño > 200 KB y de ficheros sin usar`
  - `assertWgerMediaUrl acepta /media/ de wger.de y rechaza otro host o ruta`
  - `fetchBinary no reintenta un 404`
  - `validateEntry acepta image {hash, author, license, sourceUrl} y rechaza url, hash no hexadecimal o sourceUrl ajena`
  - `buildCatalog completa el hash desde data/catalog/images y falla si falta el fichero`
  - Ajustar los tests de imagen existentes a la forma nueva.
- [ ] **Paso 2:** NODE → Esperado: FALLA.
- [ ] **Paso 3:** implementar y añadir `sharp` (`npm install --save-dev --save-exact`).
- [ ] **Paso 4:** `npm run catalog:images`. **Única descarga permitida:** las 93 miniaturas. Después, `npm run catalog:build` dos veces, y la segunda no cambia nada.
  - Esperado: 93 ficheros WebP, todos de ≤200 KB; anotar el tamaño total.
  - `exercises.json` con `image.hash` y sin `url`.
- [ ] **Paso 5:** NODE → Esperado: verde.
- [ ] **Paso 6:** commit `feat(catalogo): miniaturas de los ejercicios en WebP versionadas en el repo`.

### Task 2: Reglas e importador — colección `exerciseImages`

**Ficheros:**
- `firestore.rules`:
  - `validExerciseImage(i)` con la forma nueva: `hasAll([hash, license, sourceUrl])`, `hasOnly([hash, author, license, sourceUrl])`, `hash.matches('^[0-9a-f]{64}$')`, la licencia admitida, `author` de ≤200 y `sourceUrl` de ≤300 que encaje con `https://wger[.]de/media/.*`;
  - `match /exerciseImages/{exerciseId} { allow get: if verifiedUser(); }`, antes del catch-all y sin `list` ni escrituras;
  - todo lo demás, igual.
- `scripts/catalog/import-plan.mjs`:
  - `pickImage` copia `{hash, author, license, sourceUrl}`;
  - `planImageImport({catalog, existingImages: [{id, hash}]})` → `{upload: [id], unchanged: [id], orphaned: [id]}`;
  - `formatPlan` añade las líneas de imágenes.
- `scripts/catalog/import-exercises.mjs`:
  - lee `exerciseImages` con `select("hash")` para no traer los bytes;
  - sube cada imagen desde `data/catalog/images/<id>.webp` como `{data: Buffer, contentType: "image/webp", hash, width, height, author, license, sourceUrl, updatedAt: serverTimestamp()}`;
  - lotes de imágenes de como mucho 50 documentos y 5 MB, **antes** de los lotes de fichas;
  - en simulación, solo informa;
  - nunca borra.
- `tests/firebase-emulators.test.mjs`, `tests/catalog-import-plan.test.mjs` y `tests/catalog-import.emulator.test.mjs`

- [ ] **Paso 1: tests en rojo.**
  - **EMU:**
    - `exerciseImages: un verificado la lee (get) y uno sin verificar no; nadie la lista ni la escribe`.
    - `una ficha con image {hash, license, sourceUrl} es válida; image con url, hash no hexadecimal o sourceUrl ajena no`.
    - `el admin edita la descripción de una ficha con imagen y nadie cambia la imagen`.
  - **NODE:**
    - `planImageImport sube las nuevas o con hash distinto, deja las iguales e informa de huérfanas`.
    - `formatPlan muestra las imágenes a subir`.
  - **IMP:**
    - `importa imágenes antes que fichas, con los bytes y el hash correctos`.
    - `una segunda importación no sube ninguna imagen`.
    - `cambiar una imagen sube solo esa, actualiza el hash de la ficha y sube la versión`.
    - `la simulación no escribe exerciseImages`.
- [ ] **Paso 2:** NODE, EMU e IMP → Esperado: FALLAN.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** NODE, EMU e IMP → Esperado: verde.
- [ ] **Paso 5:** commit `feat(catalogo): imágenes de ejercicios almacenadas en Firestore por el importador`.

### Task 3: App — leer la imagen de Firestore y retirar Coil

**Ficheros:**
- `APP/core/model/CatalogExercise.kt`: `ExerciseImage(hash: String, author: String?, license: String, sourceUrl: String?)`, con el comentario actualizado (almacenada en Firestore).
- `APP/data/repository/CatalogExerciseFirestore.kt`: `parseExerciseImage` exige `hash` de 64 caracteres hexadecimales y `license`, y tolera que falte `sourceUrl`. Se retira el prefijo de wger como requisito para mostrar la imagen.
- Crear:
  - `APP/data/repository/ExerciseImageRepository.kt`: interfaz `suspend fun imageBytes(exerciseId: String, hash: String): ByteArray?`, la función pura `decideImageRead(cachedHash: String?, wantedHash: String): ImageRead { CACHE, SERVER }` y `parseImageDoc(map): StoredImage?` (`hash` más `data` como `ByteArray`).
  - `APP/data/repository/FirestoreExerciseImageRepository.kt`:
    - `get(Source.CACHE)`; si el hash coincide, devuelve los bytes;
    - si no, `get(Source.SERVER)` con `withTimeoutOrNull(10_000)`;
    - un error o un tiempo agotado devuelven `null`, sin propagar;
    - la cancelación se relanza;
    - `Blob.toBytes()`.
- `APP/data/di/DataModule.kt`: `@Binds` del repositorio.
- `APP/feature/exercises/ExercisesViewModel.kt`: `suspend fun loadImage(exercise: CatalogExercise): ByteArray?`, que delega en el repositorio.
- `APP/feature/exercises/ExerciseImageBlock.kt`:
  - recibe `load: suspend () -> ByteArray?`;
  - `produceState` decodifica con `BitmapFactory.decodeByteArray` en `Dispatchers.Default`;
  - muestra el bloque, con fondo blanco y crédito, **solo** si hay bitmap; si no, nada.
  - El crédito es «Imagen: {autor} · {licencia enlazada} · adaptada de wger.de».
- `APP/feature/exercises/ExerciseDetailSheet.kt` y `ExercisesScreen.kt`: pasan `viewModel::loadImage`.
- `APP/BobitosApplication.kt`: se retira `SingletonImageLoader.Factory`, junto con OkHttp, el User-Agent y la caché de disco.
- `gradle/libs.versions.toml` y `app/build.gradle.kts`: se retiran `coil-compose` y `coil-network-okhttp`, si nada más los usa (comprobar con grep).
- `RES`: actualizar `exercises_image_credit` y `credits_images_body` («copiadas de wger.de, adaptadas —redimensionadas y en WebP— y almacenadas en Bobitos, con su autoría y licencia»).
- Tests:
  - `UT/data/repository/CatalogExerciseFirestoreTest.kt`: imagen con la forma nueva.
  - `UT/data/repository/ExerciseImageRepositoryTest.kt`: `decideImageRead` y `parseImageDoc`, más el flujo con un origen falso. Si la clase está acoplada a Firestore, extraer un `ImageSource` inyectable.
  - `UT/feature/exercises/ExerciseImageCreditTest.kt` (actualizar) y `ExercisesViewModelTest` (`loadImage` delega).

- [ ] **Paso 1: tests en rojo.**
  - `image with hash and licence is parsed; missing or non-hex hash is ignored; url-only legacy image is ignored`
  - `same hash in cache reads no server`
  - `different or missing cached hash reads the server`
  - `server timeout or failure returns null`
  - `cancellation is rethrown`
  - `parseImageDoc reads bytes and hash and rejects missing data`
  - `credit mentions adaptation and falls back to wger contributors`
  - `loadImage delegates to the repository`
- [ ] **Paso 2:** GRADLE → Esperado: FALLA.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** GRADLE → Esperado: verde. Anotar el tamaño del APK debug antes y después.
- [ ] **Paso 5:** commit `feat(ejercicios): imagen leída de Firestore con caché y sin dependencias de red`.

### Task 4: Textos legales, licencias y documentación (incluye Open Food Facts)

**Ficheros:**
- **`PRIVACY_POLICY.md`:**
  - sustituir la viñeta de wger por: «Imágenes de ejercicios: se almacenan en la infraestructura de Bobitos (Firebase), copiadas de wger.de con su autoría y licencia; la aplicación no hace peticiones a wger.de».
  - añadir la viñeta de **Open Food Facts**: «Escaneo de códigos de barras: al escanear un producto, la aplicación envía el número del código de barras a Open Food Facts (world.openfoodfacts.org), una base de datos abierta de terceros, para obtener su nombre, marca e información nutricional; como en cualquier petición web, Open Food Facts recibe la dirección IP del dispositivo. Bobitos solo guarda los datos que el usuario confirma al crear el ingrediente o la marca».
  - actualizar la fecha.
- **`PROJECT_PLAN.md`:**
  - reescribir la fila de decisión del 10/10/2026: «Imágenes de ejercicios almacenadas en Firestore (colección `exerciseImages`)». El porqué:
    - las copia el importador desde wger.de, en WebP de 400 px como máximo y unos 3 MB en total;
    - 1 lectura por imagen y dispositivo, con caché;
    - sin Storage ni Blaze;
    - sin las generadas por IA;
    - autor y licencia en cada imagen;
    - la prohibición de fotos de usuarios sigue vigente.
  - ajustar la nota del apartado 10.1 en el mismo sentido.
- **`data/catalog/LICENSE.md`:** la sección «Imágenes» pasa a decir:
  - que las copias se redistribuyen como **adaptaciones** (redimensionadas y convertidas a WebP) de obras CC BY-SA 3.0/4.0;
  - que cada una lleva su autor, licencia y procedencia (`sourceUrl`);
  - que se publican bajo la misma licencia;
  - que se versionan en `data/catalog/images/`.
- **`docs/EXERCISE_CATALOG.md`:**
  - sección «Imágenes»: `catalog:images`, el formato, el hash, la subida antes que las fichas y las huérfanas;
  - costes: 1 lectura por imagen y dispositivo, y lo que pesa la colección;
  - quitar lo de «se enlazan».
- **`docs/DATA_MODEL.md`:** el nuevo `image` y la colección `exerciseImages/{exerciseId}`. Subir la versión.
- **`docs/superpowers/backlog.md`:** quitar los menores que este cambio resuelve, si queda alguno sobre el crédito o los textos legales de imágenes.

- [ ] **Paso 1:** redactar.
- [ ] **Paso 2:** NODE, EMU, IMP y GRADLE → Esperado: todo en verde.
- [ ] **Paso 3:** commit `docs: imágenes almacenadas en Firestore y Open Food Facts en la política de privacidad`.

---

## Ejecución (`delegating-plan-execution`, plan grande)

1. Copiar este plan a `docs/superpowers/plans/2026-10-10-imagenes-en-firestore.md`, crear la rama `agent/imagenes-en-firestore` desde `main` y hacer commit del plan.
2. Las Tasks 1 a 4 las hace un `sonnet` cada una, en serie.
3. Revisión final con `opus`, más arreglos. Re-revisión acotada si algún arreglo toca reglas.
4. `simplify`: un revisor `sonnet` de 4 ángulos y un aplicador `sonnet` de la lista (a).
5. `code-review` en nivel `high`, más arreglos.
6. Cierre: backlog y decisión de la rama.

## Despliegue (después; cada acción la confirma el usuario)

1. Reglas: `npx firebase deploy --only firestore:rules --project dev`.
2. El usuario genera la clave de la cuenta de servicio (fuera del repo).
3. Simulación de la importación y luego `--apply`. Se esperan 214 fichas, 93 imágenes y la versión 1.
4. Beta 18, con notas que pidan actualizar a todos los miembros.

## Verificación de punta a punta

- NODE, EMU, IMP y GRADLE en verde.
- En el emulador:
  1. `npm run emulators`;
  2. `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 node scripts/catalog/import-exercises.mjs --project demo-bobitos --apply`;
  3. app debug conectada.
- **La ficha con imagen** se ve en tema claro y oscuro, con su crédito. **Con el modo avión**, tras haberla abierto una vez, sigue viéndose porque viene de la caché. Una ficha sin imagen no muestra nada.
- **Reimportar sin cambios:** 0 escrituras y 0 imágenes.
- **Peticiones:** el registro de red del emulador no muestra ninguna petición a wger.de.
