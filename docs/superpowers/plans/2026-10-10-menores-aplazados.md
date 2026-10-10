# Menores aplazados (backlog 2026-10-10) — Plan de implementación

> **Para agentes:** se ejecuta con `delegating-plan-execution`. Es un **plan de menores**: pasos 1, 2, 3, 5 y 6, sin `simplify`. Cada tarea la hace un subagente `sonnet`, en serie. La revisión final la hace `opus` (si falla, `fable`). Los pasos usan casillas (`- [ ]`).

**Objetivo:** cerrar los 20 menores de `docs/superpowers/backlog.md` con el alcance que decidió el usuario.

**Arquitectura:**
- **Catálogo (Node y reglas):** nuevos campos `measure`, `image` y proveedor `bobitos`, y una versión del catálogo en `catalogMeta/exercises`.
- **App:**
  - un mecanismo común para que los 14 editores se cierren solo cuando el servidor confirma, con un tiempo máximo;
  - series en segundos;
  - imágenes de wger con Coil;
  - filtros y selector accesible;
  - caché del catálogo por versión.

**Stack:** Node (`node --test`), firebase-admin 13.10.0, Kotlin, Compose, Hilt, Firestore, DataStore y Coil 3 (nueva).

**Spec: decisiones del usuario, 2026-10-10.** Entran **todos** los menores, también los internos.
1. **Editores:** los 14, con un mecanismo común. «Guardando…» y el editor sigue abierto hasta que el servidor confirma. Si falla, se conserva el borrador. Hay un tiempo máximo.
2. **Imágenes:** se enlazan las de wger, **sin las generadas por IA** (93 fichas). Miniatura de 400 px, autor y licencia por imagen, sin almacenarlas.
3. **Segundos:** un interruptor «Repeticiones / Segundos» por ejercicio. El catálogo marca los isométricos (1307, 1019, 297, 1852 y 1408) para que empiecen en segundos.
4. **Básicos que faltan:** 6 fichas propias redactadas en el repo (CC BY-SA 4.0, «Catálogo Bobitos»), que el usuario revisa antes de importar.
5. **Caché del catálogo:** se implementa ahora. Lleva una versión que suben el importador y cada escritura de la app.
6. **Despliegue:** todo junto al final (reglas, clave, importación y beta 18), con el catálogo ya mergeado en `main`.

## Contexto

Lo encontrado al explorar:
- **Editores:** todos los de la app se cierran al pulsar Guardar, antes de la confirmación. El `ModalBottomSheet` tapa los snackbars y los banners, así que el error tiene que mostrarse dentro del formulario.
- **Escrituras:**
  - no hay escrituras sin conexión (`requireWritable`);
  - las transacciones fallan sin red;
  - `set().await()` puede no terminar nunca.
- **«Deshacer» del borrado:** reutiliza la función de alta en Tareas, Compra, Recetas, Comidas y Actividad.
- **Imágenes:**
  - vienen en `build/catalog/wger-exerciseinfo.json`;
  - quedan 93 de 208 tras descartar las de IA y las que no tienen miniatura en `https://wger.de/media/`;
  - 7 ejercicios tienen varias imágenes principales, 3 no tienen ninguna y 4 son GIF sin miniatura;
  - `/media/` no pasa por el control antibots.
- **Plan y caché:**
  - `PROJECT_PLAN.md:348,662` dice «sin fotos en el MVP», así que hay que registrar la decisión nueva;
  - Coil y Storage no se usan;
  - `Source.CACHE` no se usa;
  - cualquier usuario verificado puede crear o editar ejercicios.

## Restricciones globales

- **Rutas:**
  - `APP` = `app/src/main/java/com/dlunaunizar/bobitos/`
  - `UT` = `app/src/test/java/com/dlunaunizar/bobitos/`
  - `RES` = `app/src/main/res/values/strings.xml`
- **Comandos de verificación:**
  - **GRADLE:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:assembleDebug :app:ktlintCheck :app:detekt :app:testDebugUnitTest`
  - **NODE:** `npm run test:scripts`
  - **EMU:** `npm run test:emulators` (con JAVA_HOME)
  - **IMP:** `npm run test:catalog-import` (con JAVA_HOME)
- **No volver a descargar wger.** Se usa `build/catalog/wger-exerciseinfo.json`, que está ignorado por git. Una descarga nueva cambiaría `fetchedAt` y los textos. Si se trabaja en un worktree, hay que copiarlo.
- **Valores admitidos:**
  - medidas: `REPS` y `SECONDS`; SECONDS solo con MAQUINA, PESO_LIBRE o PESO_CORPORAL;
  - proveedores: `wger` y `bobitos`. `bobitos` va sin `url`, con licencia CC-BY-SA-4.0 y autor «Catálogo Bobitos»;
  - imagen: `null` o `{url, author, license}`. La URL tiene como mucho 300 caracteres y empieza por `https://wger.de/media/`; el autor tiene como mucho 200, sin correos (si no hay, «colaboradores de wger»); la licencia es una de las 4 admitidas.
- **Tiempo máximo** de guardado del editor: `EDITOR_SAVE_TIMEOUT_MILLIS = 20_000`.
- **Caché del catálogo:** caduca a los 7 días (`CATALOG_MAX_AGE_MILLIS`).
- **Tests JVM puros, con dobles escritos a mano.** Para detekt, extraer código antes que tocar `config/detekt/baseline.xml`, y no añadir `catch (Throwable)` nuevos.
- **Las acciones externas** (desplegar, importar, beta, push) las confirma el usuario una a una.

## Review Focus

1. **Tiempo agotado con un repositorio que convierte la cancelación** en otra excepción (todos hacen `catch (Throwable)`):
   - tiene que acabar en `FAILED` con `SaveTimeout`, nunca en `SAVED`;
   - `isSaving` tiene que volver a `false` y el borrador seguir ahí;
   - riesgo: si la escritura llega tarde y el usuario reintenta, puede duplicarse.
2. **Ciclo de vida del aviso de guardado:**
   - rotación entre `SAVED` y su consumo;
   - que «Deshacer» (`restoreX`) no cierre el editor abierto;
   - que un `FAILED` antiguo no aparezca en el siguiente editor;
   - el aviso de duplicado encima del editor de Compra;
   - que los diálogos de Espacios no se cierren mientras guardan.
3. **Caché por versión:**
   - misma versión con la caché recortada (se cubre con `count`);
   - adoptar una escritura propia solo si `previous` coincide con la versión guardada;
   - importación y app a la vez;
   - `Missing`, `Unreachable` y `PERMISSION_DENIED`;
   - reloj que retrocede;
   - límite de 1000 por id y fichas sin `nameLower`.
4. **Versiones y datos mezclados:**
   - la beta 17 borra `measure` y los segundos al guardar;
   - el estado guardado por la versión anterior se restaura como REPS;
   - SECONDS en un tipo sin series;
   - medida inicial al elegir del selector;
   - «Personalizado…» con lo buscado.
5. **Imágenes y atribución:**
   - solo `https://wger.de/media/` llega a Coil;
   - IA descartada y desempate determinista;
   - autorías sin correos;
   - PNG transparente en tema oscuro (fondo blanco);
   - si la carga falla, se oculta el bloque;
   - fichas `bobitos` sin enlace y editables por el admin;
   - TalkBack en `InfoChip` y `PickerRow`.

---

### Task 1: Scripts internos del catálogo (I6, E7, I1, I2)

**Ficheros:**
- Crear:
  - `scripts/catalog/wger-http.mjs`
  - `tests/catalog-fetch.test.mjs`
- Modificar:
  - `scripts/catalog/fetch-wger.mjs`, `normalize.mjs`, `selection.mjs` e `import-plan.mjs`
  - `tests/catalog-normalize.test.mjs`, `catalog-selection.test.mjs` y `catalog-import-plan.test.mjs`
  - `package.json`: añadir `tests/catalog-fetch.test.mjs` a `test:scripts`.

**Interfaces:**
- `wger-http.mjs`:
  - `assertWgerApiUrl(url)`: exige https, host `wger.de` y ruta `/api/v2/…`; si no, lanza un error.
  - `retryDelayMs(res, attempt)`:
    - `null` con un 4xx distinto de 429;
    - con un 429, `Retry-After` limitado a 60000 ms;
    - con un 5xx, `1000 * attempt`.
  - `fetchJson(url, {fetchImpl = fetch, sleep, attempts = 3, timeoutMs = 30000, headers})`. `fetch-wger.mjs` la usa y valida cada `page.next` con `assertWgerApiUrl`.
- `normalize.mjs`:
  - `ACCENT_FIXES` añade maquinas, bulgaras, bulgaro(s), pajaros, musculo(s), isometrico/a/os/as, estatico/a, eliptica/o y tecnica;
  - regla de sufijo `[csx]ion` final → `[csx]ión` (las excepciones van a `KEEP_CASE`);
  - `export const EQUIPMENT_WORDS`;
  - `export function isNearDuplicate(a, b)`: misma clave, o un conjunto de palabras contiene al otro y lo que sobra son solo palabras de material.
- `selection.mjs` y `import-plan.mjs` usan `isNearDuplicate`.
- `validateEntry` exige que `source.id` sea un entero mayor que 0.

- [ ] **Paso 1: tests en rojo.**
  - `fetchJson no reintenta un 404 permanente` (1 llamada, error `/HTTP 404/`).
  - `fetchJson reintenta un 503 y devuelve el JSON` (2 llamadas).
  - `fetchJson espera Retry-After en un 429 y lo limita a 60 s`.
  - `fetchJson reintenta errores de red y se rinde tras 3 intentos con la URL en el mensaje`.
  - `assertWgerApiUrl acepta /api/v2/ de wger.de y rechaza otro host, http:// u otra ruta`.
  - `normalizeName corrige plurales, derivados y -ción`: «Sentadillas bulgaras en maquinas» → «Sentadillas búlgaras en máquinas»; «Rotacion de tronco» → «Rotación de tronco»; «Elevaciones laterales» no cambia.
  - `isNearDuplicate detecta variantes que solo añaden material`: («Press banca», «Press de banca con barra») da true; («Plancha», «Plancha lateral») da false; («Remo con barra», «Remo con barra con agarre supino») da false.
  - `buildCatalog avisa de «Press banca» frente a «Press de banca con barra»`.
  - `validateEntry exige source.id entero > 0`: rechaza 0, -1, 1.5 y "7".
  - `planImport avisa de un alta casi igual a una existente con material extra`.
- [ ] **Paso 2:** NODE → Esperado: FALLAN los tests nuevos.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** `npm run test:scripts && npm run catalog:build && git diff --exit-code -- data/catalog/exercises.json` → Esperado: todo en verde y `exercises.json` sin cambios. Solo puede cambiar `exercises-review.md`, por los avisos nuevos.
- [ ] **Paso 5:** commit `fix(catalogo): descarga robusta, tildes en plurales y casi duplicados`.

### Task 2: Contrato v2 del catálogo en validación, importador y reglas (`measure`, `image`, `bobitos`)

**Ficheros:**
- `scripts/catalog/normalize.mjs`, `selection.mjs` e `import-plan.mjs`
- `firestore.rules`
- `tests/catalog-selection.test.mjs`, `catalog-import-plan.test.mjs`, `firebase-emulators.test.mjs` y `catalog-import.emulator.test.mjs`

**Interfaces:**
- **Constantes:** `SET_MEASURES`, `STRENGTH_TYPES`, `PROVIDERS`, `BOBITOS_AUTHOR` y `WGER_MEDIA_PREFIX`.
- **`validateEntry`:** valida `measure`, `image` (solo con proveedor `wger`) y la rama `bobitos`.
- **`import-plan.mjs`:**
  - `pickSource` omite las claves ausentes (nunca `undefined`);
  - `managedFields` añade `measure` (por defecto `REPS`) e `image` (por defecto `null`, siempre escrita);
  - `docToExisting` lee `measure ?? "REPS"` e `image ?? null`, para no reescribir las fichas antiguas.
- **Reglas:**
  - `validExerciseShape`: `hasOnly` añade `measure` e `image`; `measure` puede faltar, ser `null` o `REPS`/`SECONDS`; `image` puede faltar o cumplir `validExerciseImage`;
  - `validExerciseSource`: proveedor `wger` o `bobitos`; `bobitos` exige CC-BY-SA-4.0 y prohíbe `url`;
  - `validNewExercise`: prohíbe `source` e `image`;
  - `validExerciseUpdate`: `affectedKeys` añade `measure`. La imagen no la puede cambiar ningún cliente.

- [ ] **Paso 1: tests en rojo.**
  - **NODE:**
    - `validateEntry acepta measure REPS/SECONDS y rechaza otro valor o SECONDS en CARDIO`.
    - `validateEntry acepta imagen de wger media y rechaza URL ajena, licencia no admitida o autor >200`.
    - `validateEntry acepta bobitos sin url con CC-BY-SA-4.0 y rechaza bobitos con url u otra licencia`.
    - `pickSource no deja claves undefined`.
    - `managedFields pone REPS e image null por defecto`.
    - `una ficha importada sin measure ni image no se reescribe si el JSON trae REPS y sin imagen`.
  - **EMU:**
    - `un ejercicio acepta measure REPS o SECONDS y rechaza MINUTES`.
    - `nadie crea un ejercicio con image desde el cliente, ni el admin`.
    - `en una ficha importada con imagen el admin edita la descripción y nadie cambia la imagen`.
    - `una imagen fuera de wger.de/media o con licencia no admitida invalida cualquier edición`.
    - `una ficha bobitos (sin url, CC BY-SA 4.0) es editable por el admin; bobitos con url u otra licencia no`.
  - **IMP:** `importa una ficha propia sin url ni claves undefined`.
- [ ] **Paso 2:** NODE, EMU e IMP → Esperado: FALLAN los tests nuevos.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** NODE, EMU e IMP → Esperado: todo en verde.
- [ ] **Paso 5:** commit `feat(catalogo): medida, imagen y fichas propias en el contrato y las reglas`.

### Task 3: Versión del catálogo (`catalogMeta`) en reglas e importador

**Ficheros:**
- `firestore.rules`
- `scripts/catalog/import-plan.mjs`: `CATALOG_META_PATH = "catalogMeta/exercises"`; `formatPlan` añade una línea sobre la versión.
- `scripts/catalog/import-exercises.mjs`: cada lote con operaciones hace `set(meta, {version: FieldValue.increment(1), updatedAt: serverTimestamp(), updatedBy: CATALOG_ADMIN_UID}, {merge: true})`. Si no hay cambios, no se toca. El resultado incluye `versionBumped`.
- `tests/firebase-emulators.test.mjs`, `catalog-import.emulator.test.mjs` (el `beforeEach` limpia también `catalogMeta`) y `catalog-import-plan.test.mjs`

**Reglas:** `match /catalogMeta/{catalogId}`:
- `get`: cualquier verificado;
- `create`: solo `exercises`, con `version == 1`;
- `update`: solo `version == resource.data.version + 1`;
- siempre `hasOnly([version, updatedAt, updatedBy])`, `updatedAt == request.time` y `updatedBy == request.auth.uid`;
- sin `list` ni `delete`.

No se exige subir la versión en las reglas de `exercises`, para no romper las escrituras de la beta 17. A cambio, la caché caduca a los 7 días.

- [ ] **Paso 1: tests en rojo.**
  - **EMU:**
    - `catalogMeta: un verificado la lee y uno sin verificar no`.
    - `crear solo con version 1, updatedAt == request.time y updatedBy propio`.
    - `actualizar solo sube de uno en uno (ni igual, ni +2, ni bajar, ni campos extra)`.
    - `nadie la borra y otro catalogId se rechaza`.
    - `una transacción de alta de ejercicio + subida de versión pasa`.
  - **IMP:**
    - `la primera importación deja la versión en 1 y una segunda sin cambios no la toca`.
    - `actualizar una ficha sube la versión en 1`.
    - `la simulación no crea catalogMeta`.
  - **NODE:** `formatPlan avisa de si la versión del catálogo sube`.
- [ ] **Paso 2:** NODE, EMU e IMP → Esperado: FALLAN.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** NODE, EMU e IMP → Esperado: verde.
- [ ] **Paso 5:** commit `feat(catalogo): versión del catálogo de ejercicios en reglas e importador`.

### Task 4: Tubería de datos — imágenes, isométricos por tiempo y fichas propias (E1, E8, E9)

**Ficheros:**
- `scripts/catalog/normalize.mjs` y `selection.mjs`
- `tests/fixtures/wger-exerciseinfo.sample.json`: añadir imágenes a 257, 238, 245 y 211, con estos casos:
  - una principal hecha por IA;
  - una no-IA no principal, con un correo en el autor;
  - un GIF sin miniatura;
  - una con licencia 5;
  - dos principales no-IA.
- `tests/catalog-normalize.test.mjs` y `catalog-selection.test.mjs`
- `data/catalog/exercises-selection.json`:
  - `"measure": "SECONDS"` en 1307, 1019, 297, 1852 y 1408;
  - bloque `custom` con las 6 fichas y sus textos;
  - `notes` actualizadas.
- Regenerar `data/catalog/exercises.json` y `exercises-review.md`.
- `data/catalog/LICENSE.md`: fichas propias, e imágenes enlazadas (no redistribuidas y sin las de IA).

**Interfaces:**
- `imageCandidates(info)`:
  - devuelve `[{id, url, author, license, isMain}]`, solo no-IA, con miniatura medium en wger media y licencia admitida;
  - ordena primero las principales y después por id;
  - quita los correos del autor.
- `pickImage(images, override?: false | number)` → `{image|null, problem?}`.
- `buildCatalog`:
  - acepta `measure` e `image` (`false` o un id) en `include`, y `selection.custom = [{bobitosId, name, type, muscleGroup, equipment, description, measure?}]`;
  - cada entrada lleva `measure` e `image` explícitas;
  - son problemas: un `imageId` que no existe, un `bobitosId` que no es entero mayor que 0 o está repetido, y un slug repetido entre wger y propias.
- `renderReview`:
  - columnas `Medida` e `Imagen`;
  - sección «Imágenes» (N de M);
  - sección «Fichas propias (Catálogo Bobitos)» con el texto completo.

**Fichas propias:** textos originales de 400 a 900 caracteres, sin copiar de terceros.

| bobitosId | Nombre | Tipo | Grupo | Material |
|---|---|---|---|---|
| 1 | Remo ergómetro | CARDIO | Cardio | MAQUINA |
| 2 | Dominadas con lastre | PESO_CORPORAL | Espalda | BARRA_DOMINADAS |
| 3 | Elevación de gemelos de pie en máquina | MAQUINA | Gemelos | MAQUINA |
| 4 | Curl femoral de pie | MAQUINA | Isquiotibiales | MAQUINA |
| 5 | Natación | CARDIO | Cardio | — |
| 6 | Caminata | CARDIO | Cardio | — |

- [ ] **Paso 1: tests en rojo.**
  - `imageCandidates descarta IA, sin miniatura y licencia no admitida, y ordena principal primero y luego por id`.
  - `imageCandidates quita correos y usa «colaboradores de wger» si falta autor`.
  - `pickImage elige la primera; false no pone imagen; un id elige esa y un id inexistente es problema`.
  - `buildCatalog copia measure SECONDS y rechaza SECONDS en CARDIO`.
  - `buildCatalog crea fichas propias con source bobitos sin url, measure explícita e image null`.
  - `buildCatalog falla con bobitosId repetido o no entero, o con slug repetido entre wger y propias`.
  - `renderReview muestra medida, imagen con autor y licencia, y el texto completo de las fichas propias`.
  - El test de determinismo que ya existe sigue en verde.
- [ ] **Paso 2:** NODE → Esperado: FALLA.
- [ ] **Paso 3:** implementar y redactar las fichas.
- [ ] **Paso 4:** `npm run test:scripts && npm run catalog:build && npm run catalog:build && git diff --stat -- data/catalog/exercises.json` → Esperado: verde. La segunda ejecución no cambia nada. Totales: 214 ejercicios, 93 con imagen y 5 SECONDS.
- [ ] **Paso 5:** IMP → Esperado: verde.
- [ ] **Paso 6:** commit `feat(catalogo): imágenes de wger, isométricos por tiempo y fichas propias`.

**Parada B (coordinador):** el usuario revisa `data/catalog/exercises-review.md`:
- los textos de las 6 fichas propias;
- las 93 imágenes (se quitan con `image: false` o se cambian con `image: <id>`);
- las 5 marcas «segundos»;
- los avisos nuevos de casi duplicados.

Bloquea solo la importación. Cada cambio lo aplica un `sonnet` en la selección y regenera.

### Task 5: Editores — mecanismo común y piloto en Tareas (T1)

**Ficheros:**
- Crear:
  - `APP/core/common/EditorSave.kt`
  - `APP/core/designsystem/component/EditorSaveEffect.kt`
  - `UT/core/common/EditorSaveTest.kt`
- Modificar:
  - `APP/core/designsystem/component/BobitosFormSheet.kt`
  - `APP/feature/tasks/TasksUiState.kt`, `TasksViewModel.kt` y `TasksScreen.kt` (el host, el «Deshacer» y `TaskEditor`)
  - `RES`
  - `UT/feature/tasks/TasksViewModelTest.kt`: el doble gana `createGate: CompletableDeferred<Unit>?` y `hang` (`awaitCancellation()`).

**Interfaces:**

```kotlin
enum class EditorSaveStatus { IDLE, SAVING, SAVED, FAILED }
fun EditorSaveStatus.started(editor: Boolean): EditorSaveStatus   // también succeeded(editor) y failed(editor); con editor = false no cambian el estado
const val EDITOR_SAVE_TIMEOUT_MILLIS = 20_000L
class SaveTimeoutException : Exception(/* el mismo texto que R.string.write_timeout */)
suspend fun <T> withSaveTimeout(timeoutMillis: Long = EDITOR_SAVE_TIMEOUT_MILLIS, block: suspend () -> T): T {
    val result = withTimeoutOrNull(timeoutMillis) { runCatching { block() } } ?: throw SaveTimeoutException()
    return result.getOrThrow()
}
@Composable fun EditorSaveEffect(status: EditorSaveStatus, editorOpen: Boolean, onClose: () -> Unit, onConsume: () -> Unit)
// LaunchedEffect(status, editorOpen): con SAVED, onClose() y luego onConsume(); con FAILED y el editor cerrado, onConsume()
```

- `BobitosFormSheet(..., errorMessage: String? = null)`: el error se pinta encima de los botones, con color de error y `liveRegion` Polite. Mientras guarda, el botón dice «Guardando…» (`write_saving`, que ya existe).
- Cadena nueva `write_timeout`: «No hay respuesta del servidor. Puede que se guarde al recuperar la conexión: compruébalo antes de repetir».
- `TasksUiState.editorSave` y `TaskUiMessage.SaveTimeout`.
- `TasksViewModel`:
  - `consumeEditorSave()`;
  - `restoreTask(spaceId, task)`, que usa el «Deshacer» con `editor = false`;
  - `runAction(notice, editor: Boolean = false, action)`, envuelto en `withSaveTimeout`.
- El host deja de poner `editorVisible = false` dentro de `onSave` y usa `EditorSaveEffect`. Le pasa `errorMessage` al editor solo cuando el estado es `FAILED`.

- [ ] **Paso 1: tests en rojo.**
  - **`EditorSaveTest`:**
    - `returns the block result in time`
    - `throws SaveTimeoutException when the block never answers`
    - `detects the timeout even if the block turns the cancellation into another exception`
    - `rethrows the block failure unchanged`
    - `outer cancellation is not reported as a timeout`
    - `status only moves for editor saves`
    - `timeout message matches write_timeout in strings.xml` (lee el XML, como hace `AttributionStringsTest`)
  - **`TasksViewModelTest`:**
    - `editor create stays SAVING until the repository answers, then SAVED`
    - `editor save failure leaves FAILED with the error and isSaving false`
    - `editor save without answer times out into FAILED with SaveTimeout`
    - `a validation error from the editor leaves FAILED`
    - `restoreTask does not touch the editor status`
    - `consumeEditorSave returns to IDLE`
- [ ] **Paso 2:** GRADLE → Esperado: FALLA.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** GRADLE → Esperado: BUILD SUCCESSFUL.
- [ ] **Paso 5:** commit `fix(editores): el editor de tareas solo se cierra cuando el servidor confirma`.

### Task 6: Editores — Compra, Notas, Comidas y Actividad

**Ficheros:**
- `APP/feature/shopping/{ShoppingUiState,ShoppingViewModel,ShoppingScreen}.kt`
- `APP/feature/notes/{NotesUiState,NotesViewModel,NotesScreen}.kt`
- `APP/feature/meals/{MealsUiState,MealsViewModel,MealsScreen,MealUiMessage}.kt`
- `APP/feature/sport/{SportUiState,SportViewModel,SportScreen}.kt`
- Sus 4 tests de ViewModel.

**Interfaces:**
- En los 4: `editorSave`, `SaveTimeout` y `consumeEditorSave()`, con el mismo patrón que la Task 5.
- `restoreItem(spaceId, item)`, `restoreMeal(meal)` y `restoreActivity(activity)`, para los «Deshacer».
- **Compra:**
  - el aviso de duplicado se abre encima del editor abierto;
  - sus dos botones guardan con `editor = true`;
  - cerrar el aviso vuelve al borrador.
- Los editores `ShoppingItemEditor`, `NoteEditor`, `MealEditor` y `ActivityEditor` pasan `errorMessage` al formulario.

- [ ] **Paso 1: tests en rojo, por ViewModel:**
  - `editor save is SAVED only after the repository answers`
  - `editor save failure or timeout leaves FAILED`
  - En Compra, Comidas y Actividad: `restoreX does not touch the editor status`
- [ ] **Paso 2:** GRADLE → Esperado: FALLA.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** GRADLE → Esperado: verde.
- [ ] **Paso 5:** commit `fix(editores): compra, notas, comidas y actividad esperan la confirmación`.

### Task 7: Editores — Evento (Calendario y Mi calendario), Receta, Rutina y Ejercicio

**Ficheros:**
- `APP/feature/calendar/{CalendarViewModel,PersonalCalendarViewModel,CalendarScreen,PersonalCalendarScreen}.kt`. `CalendarEditorHost` gana `errorMessage` y deja de llamar a `onClose()` tras `onSave`.
- `APP/feature/recipes/{RecipesUiState,RecipesViewModel,RecipesScreen,RecipeSheets}.kt`
- `APP/feature/routines/{RoutinesUiState,RoutinesViewModel,RoutinesScreen,RoutineEditor}.kt`
- `APP/feature/exercises/{ExercisesUiState,ExercisesViewModel,ExercisesScreen,ExerciseEditorSheet}.kt`. Mientras guarda se oculta el error «ya existe», para que no parpadee tras el alta.
- Tests:
  - `CalendarViewModelTest`, `RecipesViewModelTest`, `RoutinesViewModelTest` y `ExercisesViewModelTest`;
  - crear `UT/feature/calendar/PersonalCalendarViewModelTest.kt`.

**Interfaces:**
- `editorSave` y `consumeEditorSave()` en los 5 ViewModels.
- `RecipesViewModel.restoreRecipe(recipe)`.
- `CalendarViewModel.restore` y `delete` siguen con `editor = false`.

- [ ] **Paso 1: tests en rojo.**
  - Por ViewModel: «SAVED solo tras confirmar» y «FAILED con fallo o tiempo agotado».
  - `restore and delete never touch the editor status` (Calendario).
  - `AlreadyExists from the editor leaves FAILED` (Ejercicios).
- [ ] **Paso 2:** GRADLE → Esperado: FALLA.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** GRADLE → Esperado: verde.
- [ ] **Paso 5:** commit `fix(editores): eventos, recetas, rutinas y ejercicios esperan la confirmación`.

### Task 8: Editores — Ingrediente (alta y escaneo), Ficha, Marca y Espacios

**Ficheros:**
- `APP/feature/ingredients/{IngredientsUiState,IngredientsViewModel,IngredientsScreen,IngredientDetailUiState,IngredientDetailViewModel,IngredientDetailScreen,IngredientUiMessage}.kt`. Se mantiene `finished`, y `EditorSaveEffect` cierra a la vez `showFichaEditor` y `brandInitial`.
- `APP/feature/spaces/{SpacesUiState,SpacesViewModel,SpacesScreen,SpaceSettingsScreen,SpaceFeedback}.kt`:
  - `SpaceNameDialog` e `InvitationCodeDialog` ganan `saving` y `errorMessage`;
  - mientras guardan no se cierran, los botones se desactivan y el principal dice «Guardando…».
- `APP/MainActivity.kt`: `onEditorSaveConsumed = spacesViewModel::consumeEditorSave`.
- Tests: `IngredientsViewModelTest`, `IngredientDetailViewModelTest` y `SpacesViewModelTest` (con `createGate`).

**Interfaces:**
- `SpacesScreen(..., onEditorSaveConsumed)` y `SpaceSettingsScreen(..., onEditorSaveConsumed)`.
- `IngredientUiMessage.SaveTimeout` y `SpaceUiMessage.SaveTimeout`.

- [ ] **Paso 1: tests en rojo.**
  - `create/acceptInvitation/rename are SAVED only after confirm`
  - `acceptInvitation sets acceptedSpaceId and SAVED together`
  - `leave/remove/delete never touch editorSave`
  - Ficha y marca: «SAVED solo tras confirmar» y «FAILED con tiempo agotado».
- [ ] **Paso 2:** GRADLE → Esperado: FALLA.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** GRADLE → Esperado: verde.
- [ ] **Paso 5:** commit `fix(editores): ingredientes, marcas y espacios esperan la confirmación`.

### Task 9: App — modelo v2 del catálogo, atribución por proveedor, chips accesibles (E2) y medida en la ficha

**Ficheros:**
- `APP/core/model/CatalogExercise.kt`
- `APP/data/repository/CatalogExerciseFirestore.kt`
- Crear `APP/core/designsystem/component/InfoChip.kt`
- `APP/feature/exercises/{ExerciseSourceTokens,ExerciseDetailSheet,ExercisesScreen,CatalogExerciseDraft,ExerciseEditorSheet}.kt`
- `APP/feature/auth/CreditsDialog.kt`
- `RES`
- Tests: `UT/data/repository/CatalogExerciseFirestoreTest.kt` y `UT/feature/exercises/{ExerciseSourceTokensTest,CatalogExerciseDraftTest}.kt`

**Interfaces:**

```kotlin
enum class SetMeasure { REPS, SECONDS }
data class ExerciseImage(val url: String, val author: String?, val license: String)
// CatalogExercise: + measure: SetMeasure = REPS, image: ExerciseImage? = null;  ExerciseInput: + measure: SetMeasure = REPS
internal fun parseSetMeasure(raw: Any?): SetMeasure            // si falta o es desconocida → REPS
internal fun parseExerciseImage(raw: Any?): ExerciseImage?    // solo https://wger.de/media/ y con licencia
sealed interface ExerciseAttribution { /* Wger(sourceId, author, license, url), Bobitos(license), Other(provider, license) */ }
fun ExerciseSource.attribution(): ExerciseAttribution
@Composable fun InfoChip(label: String, modifier: Modifier = Modifier, contentColor: Color = /* … */)  // Surface con borde, sin semántica de clic
```

- `toFirestoreFields` escribe `measure`. `validateExerciseInput` fuerza REPS si el tipo no tiene series.
- `InfoChip` sustituye a `AssistChip(enabled = false)` en la ficha y en la fila.
- La ficha muestra el chip «Por tiempo». El editor de la ficha muestra «Se registra en: Repeticiones / Segundos» si el tipo tiene series.
- Atribución de `bobitos`: «Texto original del Catálogo Bobitos · Licencia CC BY-SA 4.0», sin enlace.
- Créditos: bloque «Catálogo Bobitos» y línea de las imágenes de wger.

- [ ] **Paso 1: tests en rojo.**
  - `measure SECONDS is parsed; missing or unknown is REPS`
  - `wger media image is parsed; foreign url, missing licence or non-map is ignored`
  - `bobitos source without url parses`
  - `document without nameLower is kept`
  - `toFirestoreFields writes measure`
  - `editing keeps measure; non-strength types save REPS`
  - `bobitos attribution has own wording and no link`
  - `unknown provider shows provider and licence`
- [ ] **Paso 2:** GRADLE → Esperado: FALLA.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** GRADLE → Esperado: verde.
- [ ] **Paso 5:** commit `feat(ejercicios): medida, imagen y fichas propias en el modelo, y chips accesibles`.

### Task 10: App — series en segundos (E1)

**Ficheros:**
- `APP/core/model/RoutineExercise.kt`
- `APP/data/repository/RoutineExerciseFirestore.kt`
- `APP/feature/exercises/{ExerciseListEditor,ExerciseDraftSaver}.kt`
- `RES`
- Tests: `UT/data/repository/RoutineExerciseFirestoreTest.kt` y `UT/feature/exercises/{ExerciseListEditorTest,ExerciseDraftSaverTest}.kt`

**Interfaces:**
- **Modelo:** `ExerciseSet.seconds: Int? = null` y `RoutineExercise.measure: SetMeasure = REPS`.
- **Serialización:**
  - `"measure": "SECONDS"` solo cuando es SECONDS;
  - `seconds` solo cuando no es nulo;
  - un ejercicio REPS se escribe exactamente igual que hoy.
- **Borrador:** `ExerciseDraft(..., measure)` y `SetDraft(reps, weight, seconds)`.
- `internal fun CatalogExercise.toExerciseDraft(): ExerciseDraft`: el selector arranca con la medida del catálogo.
- `ExerciseDraftSaver`: la medida va en el índice 7 y cada serie es `[reps, weight, seconds]`. Al restaurar usa `getOrNull`, así que el estado antiguo vale.
- `StrengthSets`: `SingleChoiceSegmentedButtonRow` «Repeticiones / Segundos».

- [ ] **Paso 1: tests en rojo.**
  - `SECONDS exercise survives serialize and parse`
  - `REPS exercise serializes without measure or seconds keys`
  - `unknown measure falls back to REPS`
  - `timed sets keep seconds and optional weight`
  - `switching a timed exercise to cardio drops sets and measure`
  - `drafts round-trip measure and seconds`
  - `a timed catalog exercise starts in seconds`
  - `measure and seconds survive rotation`
  - `state saved by the previous version restores as REPS`
- [ ] **Paso 2:** GRADLE → Esperado: FALLA.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** GRADLE → Esperado: verde.
- [ ] **Paso 5:** commit `feat(ejercicios): series en segundos para los isométricos`.

### Task 11: App — imágenes con Coil (E9a)

**Ficheros:**
- `gradle/libs.versions.toml`: `coil` con la última 3.x estable, fijada exacta y compatible con Kotlin 2.3 y el BOM de Compose del repo.
- `app/build.gradle.kts`: `io.coil-kt.coil3:coil-compose` y `coil-network-okhttp`.
- `APP/BobitosApplication.kt`: `SingletonImageLoader.Factory`, con caché de disco de 50 MB en `cacheDir/image_cache`, `crossfade` y `User-Agent: Bobitos/<versionName> (+https://github.com/DLunaUNIZAR/Bobitos)`.
- Crear `APP/feature/exercises/ExerciseImageBlock.kt`.
- `ExerciseDetailSheet.kt` y `CreditsDialog.kt`.
- `RES`: `exercises_image_description` y `exercises_image_credit`.
- Crear `UT/feature/exercises/ExerciseImageCreditTest.kt`.

**Interfaces:**
- `data class ImageCredit(author, licenseLabel, licenseUrl?)` e `internal fun ExerciseImage.credit()`.
- `@Composable internal fun ExerciseImageBlock(image, exerciseName)`:
  - contenedor blanco redondeado;
  - `AsyncImage` con `onError`, que oculta todo el bloque;
  - crédito «Imagen: {autor} · {licencia}», con enlace mediante `rememberSafeLinks`.
- La imagen solo aparece en la ficha, no en las listas.

- [ ] **Paso 1: tests en rojo.**
  - `credit falls back to wger contributors`
  - `credit maps licence label and deed url`
  - `unknown licence has no url`
- [ ] **Paso 2:** GRADLE → Esperado: FALLA.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** GRADLE → Esperado: verde.
- [ ] **Paso 5:** commit `feat(ejercicios): imagen de wger en la ficha con su atribución`.

### Task 12: App — selector y búsqueda (E3, E4, E5, E6, I3, filtros E9b)

**Ficheros:**
- `APP/core/common/TextSearch.kt`
- Crear `APP/feature/exercises/ExerciseFilter.kt`
- `APP/feature/exercises/{ExercisePicker,ExercisesScreen,ExerciseListEditor}.kt`
- `APP/feature/sport/SportViewModel.kt`
- Tests: `UT/core/common/TextSearchTest.kt`, `UT/feature/exercises/ExerciseFilterTest.kt` (sustituye a `ExercisePickerFilterTest`) y `UT/feature/sport/SportViewModelTest.kt`

**Interfaces:**
- `foldForSearch` quita `\p{M}`, y `prepareQuery` trocea por `[\s   ]+`.
- `data class ExerciseFilter(query = "", type: ExerciseType? = null, muscleGroup: String? = null)`.
- `List<CatalogExercise>.filterExercises(filter)`: una sola función para la pantalla y el selector, con `remember(catalog, filter)`.
- `List<CatalogExercise>.muscleGroups()`: plegado, sin repetidos y ordenado con el `Collator` es-ES.
- `customExerciseName(query)`: recorta, une espacios y corta a 120.
- `@Composable ExerciseFilterChips(...)`: chips de grupo y de tipo, en la pantalla y en el selector.
- `ExercisePickerDialog(catalog, onDismiss, onPick, onPickCustom: (String) -> Unit)`: «Personalizado…» rellena el nombre con lo buscado.
- `PickerRow` con `clickable(role = Role.Button)`.
- `SportViewModel`:
  - `catalogRequested`: `observe()` vuelve a lanzar el catálogo si estaba pedido;
  - `stopObserving()` cancela, pero mantiene la marca.

- [ ] **Paso 1: tests en rojo.**
  - `NBSP and carriage return split words`
  - `enclosing marks are folded like slug`
  - `type and group filters combine with the query`
  - `group filter ignores accents and case`
  - `muscleGroups is unique and sorted`
  - `customExerciseName trims, collapses and caps`
  - `catalog resumes after switching space with the session editor open`
- [ ] **Paso 2:** GRADLE → Esperado: FALLA.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** GRADLE → Esperado: verde.
- [ ] **Paso 5:** commit `feat(ejercicios): filtros, «Personalizado…» con lo buscado y selector accesible`.

### Task 13: App — caché del catálogo por versión (I4, I5)

**Ficheros:**
- Crear:
  - `APP/data/repository/VersionedCatalog.kt`
  - `APP/data/repository/CatalogSyncStore.kt` (interfaz y `DataStoreCatalogSyncStore`)
  - `APP/data/repository/FirestoreExerciseCatalogSource.kt`
- Modificar:
  - `APP/data/repository/FirestoreExerciseRepository.kt`
  - `APP/data/di/DataModule.kt`: enlace del almacén y `DataStore<Preferences>` «catalog_sync».
- Tests: `UT/data/repository/VersionedCatalogLoaderTest.kt` y `UT/data/repository/DataStoreCatalogSyncStoreTest.kt` (con `PreferenceDataStoreFactory` y `TemporaryFolder`).

**Interfaces:**
- **Tipos:**
  - `CatalogPage<T>(items, rawCount)`;
  - `sealed interface CatalogMeta { Known(version); Missing; Unreachable }`;
  - `CatalogSyncState(version, count, fetchedAtMillis)`;
  - `enum class CatalogLoad { CACHE, SERVER }`.
- **`decideCatalogLoad(meta, stored, cachedCount, nowMillis)`**, en este orden:
  1. caché vacía → SERVER;
  2. `Unreachable` → CACHE;
  3. `Missing` → SERVER, sin guardar la versión;
  4. versión distinta o sin versión guardada → SERVER;
  5. caché con menos documentos que `count` → SERVER;
  6. más de 7 días o reloj que retrocede → SERVER;
  7. en cualquier otro caso → CACHE.
- **`syncStateAfterOwnWrite(stored, previousVersion, cachedCount)`.**
- **`CatalogSource<T>`:** `readCache()` (`Source.CACHE`), `readServer()` y `readMeta()` (con un máximo de 10 s; `PERMISSION_DENIED` cuenta como `Missing`).
- **`CatalogSyncStore`:** `read(key)` y `write(key, state)`. Se escribe solo tras leer bien del servidor.
- **`VersionedCatalogLoader<T>`:**
  - `catalog(localChanges: Flow<Unit>)`: emite primero la caché, lee la versión y, si toca, el servidor;
  - `afterOwnWrite(previousVersion)`;
  - el colector de `localChanges` se lanza antes de la carga inicial;
  - nunca captura la cancelación.
- **I5:** la consulta pasa a `collection("exercises").limit(1000)`, sin `orderBy`, y se ordena en el cliente con `sortedForCatalog()`.
- **Escrituras de ejercicio:** crear, editar y borrar pasan a `runTransaction`:
  - lee la versión `previous` (0 si no existe);
  - escribe la ficha;
  - sube la versión a `previous + 1`;
  - después llama a `afterOwnWrite(previous)` y emite `localChanges`.
- **Consecuencia (se documenta):** los cambios de otros dispositivos llegan en la siguiente apertura, no en directo.

- [ ] **Paso 1: tests en rojo.**
  - `first launch reads the server and stores version and count`
  - `same version and full cache reads no documents from the server`
  - `newer version refreshes from the server`
  - `offline meta keeps the cache and the stored version`
  - `missing meta reads the server and stores nothing`
  - `cache with fewer documents than stored refreshes`
  - `older than seven days or clock going back refreshes`
  - `server failure with cache keeps the cache; without cache fails`
  - `own write right after the stored version is adopted without refetch`
  - `own write racing another write is not adopted`
  - `local change re-emits from the cache only`
  - `sync state round-trips in DataStore`
- [ ] **Paso 2:** GRADLE → Esperado: FALLA.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** GRADLE → Esperado: verde.
- [ ] **Paso 5:** commit `feat(ejercicios): caché del catálogo por versión y sin perder fichas sin nameLower`.

### Task 14: Documentación y cierre (D1, D2, D3, T2)

**Ficheros:**
- `docs/EXERCISE_CATALOG.md`:
  - OTROS registra tiempo y nivel;
  - se reescribe la frase sobre reimportaciones: guardar una ficha en la app la congela; se recupera borrándola con el admin y reimportando; choques de slug;
  - lista de pasos para añadir un material: JS, Kotlin, reglas (incluido `equipment.size() <= 13`), `strings.xml` y `ExerciseTokens.labelRes`;
  - `measure`, `image`, fichas propias, `catalogMeta` y costes;
  - los cambios de otros dispositivos no llegan en directo.
- `docs/DATA_MODEL.md`:
  - `measure`, `image` y `bobitos`;
  - `measure` y `seconds` en `routines.exercises` y `activities.session`;
  - `catalogMeta/exercises`;
  - subir la versión del documento.
- `PROJECT_PLAN.md`: nueva fila de decisión (2026-10-10), «Imágenes de ejercicios enlazadas desde wger.de, sin almacenarlas», y nota en el apartado 10.1.
- `PRIVACY_POLICY.md`: al abrir una ficha con imagen, wger.de recibe la IP. Actualizar la fecha.
- `docs/superpowers/plans/2026-10-10-quitar-alta-rapida-tareas.md` (T2): «Añadir tarea» pasa a «Nueva tarea», con una nota de que el commit `58387a2` no se puede cambiar.
- `docs/superpowers/backlog.md`: quitar lo resuelto.

- [ ] **Paso 1:** redactar.
- [ ] **Paso 2:** NODE, EMU, IMP y GRADLE → Esperado: todo en verde.
- [ ] **Paso 3:** commit `docs(catalogo): medida, imágenes, fichas propias, versión y correcciones`.

---

## Ejecución (`delegating-plan-execution`, plan de menores)

1. Copiar este plan a `docs/superpowers/plans/2026-10-10-menores-aplazados.md`. Crear la rama `agent/menores-aplazados` desde `main` y hacer commit del plan.
2. Las Tasks 1 a 14 las hace un `sonnet` cada una, en serie. La **Parada B** va tras la Task 4: bloquea solo la importación.
3. Revisión final con `opus` (si falla, `fable`). Los Critical e Important los arregla un `sonnet`. Hay **re-revisión acotada**, porque las Tasks 2 y 3 tocan permisos en las reglas.
4. `code-review` en nivel `high` y arreglos con un `sonnet`. Si algún arreglo toca reglas, se re-revisa también.
5. Cierre: los menores nuevos van al backlog; la decisión de la rama la toma el usuario.

## Despliegue (después; cada acción la confirma el usuario)

- **A (opcional, tras la Task 5):** el usuario prueba el piloto del editor de Tareas en el emulador:
  - con red;
  - sin red;
  - con el emulador parado, para ver el tiempo agotado.
- **C (al final):**
  1. el usuario aprueba la decisión en `PROJECT_PLAN.md` y el texto de `PRIVACY_POLICY.md`;
  2. desplegar las reglas con `--project dev`;
  3. el usuario genera la clave de la cuenta de servicio;
  4. simulación de la importación y luego `--apply` (se esperan 214 altas y la versión 1);
  5. beta 18, cuyas notas piden a todos los miembros que actualicen.

**Compatibilidad:**
- La beta 17 ignora `measure`, `image` y `seconds`. Al guardar rutinas o sesiones, los pierde, igual que PESO_CORPORAL; las notas de la beta lo cubren.
- Las escrituras de la beta 17 no suben la versión. La caché de la app nueva las verá como tarde a los 7 días.
- La beta 18 con las reglas viejas:
  - lee el catálogo, porque `catalogMeta` cuenta como `Missing`;
  - pero falla al guardar ejercicios.
  - Por eso las reglas se despliegan primero.

**Coste en Spark:**
- abrir el catálogo sin cambios: 1 lectura (hoy, de 210 a 300);
- con cambios: 1 + N lecturas;
- cada guardado de ejercicio: +1 lectura y +1 escritura.

## Verificación de punta a punta

- NODE, EMU, IMP y GRADLE en verde.
- En el emulador:
  1. importar con `--project demo-bobitos --apply`;
  2. app debug conectada a los emuladores.
- Comprobar en la app:
  - **Editores:** guardar con red cierra el editor; sin red queda abierto con el error dentro del formulario y el borrador intacto.
  - **Ficha:** imagen con su crédito en tema claro y oscuro; si la imagen no carga, el bloque desaparece.
  - **Plancha:** empieza en segundos al elegirla del selector.
  - **Selector y Ejercicios:** filtros de grupo y tipo; «Personalizado…» con lo buscado.
  - **Caché:** la segunda apertura de Ejercicios cuesta 1 lectura.
  - **Fichas propias:** se ven con su atribución.
