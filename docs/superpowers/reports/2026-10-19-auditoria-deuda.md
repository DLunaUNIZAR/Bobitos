# Auditoría de deuda: menores internos del backlog (segunda)

Fecha: 2026-10-19 · Base: `main` en `2e417c6` · Fuente: `docs/superpowers/backlog.md`. Parte de la auditoría anterior (`2026-10-18-auditoria-deuda.md`), cuyos G1 (red de seguridad) y G2 (guardado, errores y Deshacer) ya están hechos. Solo he abierto el código para confirmar que siguen vigentes los menores de los grupos recomendados.

Los números «Lnnn» son líneas del backlog en `2e417c6`.

## Resumen

- Hay **126 menores internos pendientes** en 28 secciones (del 2026-10-10 al 2026-10-19). 36 son nuevos desde la auditoría anterior: los dejaron G1, los dos planes de G2 y la revisión de «Añadir a la compra». Los he repartido en 9 grupos y 3 sueltos.
- G1 y G2 cerraron 26 líneas internas del backlog. Lo que dejaron atrás es sobre todo de dos tipos: restos del marco común de guardado y Deshacer (13, grupo nuevo G10) y tests que faltan o no miden lo que dicen (13, grupo R2).
- **Hallazgo fuera del backlog:** la CI no ejecuta ningún test instrumentado: son 52 ficheros y 203 tests de Compose (accesibilidad, letra al 130 %, cabeceras, Deshacer en pantalla…). La puerta de cada plan solo pasa los que nombra una tarea. Hoy pasan todos en el emulador, pero `ProfileScreenTest` estuvo roto tres meses, una semana de ellos ya apuntado en el backlog.
- Recomiendo **R2 (red de seguridad, segunda vuelta)** primero y **G10 (restos del marco de avisos y Deshacer)** después, este junto con los 5 menores con efecto visible del mismo mecanismo.
- No hay internos mal clasificados: el backlog no cambia.

## Hallazgo de la comprobación (fuera del backlog)

- **Qué he comprobado:** he pasado la suite instrumentada entera sobre `main` (`./gradlew :app:connectedDebugAndroidTest` en el emulador `Pixel_10`): **203 tests en 52 ficheros, todos en verde, en 8 min 15 s.**
- **Qué falta:** la CI (`.github/workflows/ci.yml`) ejecuta ktlint, detekt, `testDebugUnitTest`, `lintDebug`, `assembleDebug`, Kover y las tres suites de Node, pero ningún instrumentado. El plan de G1 lo dejó fuera a propósito («No se añaden tests instrumentados a la CI: está fuera de G1»).
- **Lo que ha costado:** `ProfileScreenTest` falló desde `566294d` (2026-07-19) hasta `52eb693` (2026-10-18), tres meses. Se apuntó en el backlog el 2026-10-11, en tres ramas distintas, y siguió roto hasta G1 porque ninguna máquina lo ejecutaba. La puerta de los planes compila los instrumentados (`compileDebugAndroidTestKotlin`), así que un test que no compila sí se ve; uno que compila y falla, no.
- **Por qué importa ahora:** las dos últimas ramas (G2 y la revisión de «Añadir a la compra») apoyan buena parte de su Review Focus en instrumentados (`UndoOfferEffectTest`, `TasksScreenCompletingTest`, `IngredientReviewUiTest`, `SpacesSavingUiTest`). Si una rama posterior los rompe, nada avisa.

## Criterio

El mismo que en la auditoría anterior, el de `engineering:tech-debt`. Cada grupo se puntúa en **Impacto** (cuánto frena trabajar en el código, de 1 a 5), **Riesgo** (qué pasa si no se arregla, de 1 a 5) y **Esfuerzo** (de 1 a 5):

**Prioridad = (Impacto + Riesgo) × (6 − Esfuerzo)**

Señales:

- 🕳 **Red floja:** tests rotos, tests que nunca fallan o partes que la CI no ejecuta.
- 🔁 **Provoca visibles:** el grupo está en el origen de menores con efecto visible, pendientes o ya resueltos.

Los nombres de grupo siguen los de la auditoría anterior cuando el tema es el mismo (G3 a G9). R2, G10 y G11 son nuevos.

## Grupos

| Grupo | Internos | Secciones | I | R | E | Prioridad | Señales |
|---|---:|---:|---:|---:|---:|---:|---|
| **R2** Red de seguridad, segunda vuelta | 13 + hallazgo | 9 | 3 | 4 | 2 | **28** | 🕳🕳 |
| **G10** Restos del marco de avisos y Deshacer | 13 | 2 | 3 | 4 | 3 | **21** | 🕳 🔁 (5 visibles) |
| G4 Documentación y comentarios desfasados | 18 | 13 | 2 | 2 | 1 | 20 | |
| G11 Avisos de beta: estado de la descarga | 5 | 1 | 2 | 3 | 2 | 20 | 🔁🔁 (11 visibles) |
| G3 Constantes, validaciones y helpers duplicados | 22 | 13 | 3 | 3 | 3 | 18 | 🔁 |
| G7 Rendimiento y efectos en la composición | 10 | 7 | 2 | 2 | 2 | 16 | 🔁 (L124) |
| G6 Escuchas y lecturas de Firestore de más | 6 | 6 | 2 | 3 | 3 | 15 | |
| G5 Scripts del catálogo e importadores (admin) | 24 | 6 | 2 | 2 | 3 | 12 | |
| G9 Estilo, avisos del compilador y restos | 12 | 8 | 1 | 1 | 1 | 10 | |
| Sueltos | 3 | 3 | — | — | — | — | |

Cambios respecto a la auditoría anterior: G1 y G2 desaparecen (hechos); G8 (huecos de tests) se funde en R2, porque los tests que faltan y los que no miden lo que dicen se arreglan en las mismas tareas; G5 baja de 15 a 12 porque la CI ya ejecuta `test:scripts` y `test:catalog-import` (G1, tarea 2).

### R2 — Red de seguridad, segunda vuelta (28)

**Por qué puntúa así:**

- **Impacto 3:** todos los planes con interfaz se apoyan en tests instrumentados que solo se ejecutan si una tarea los nombra.
- **Riesgo 4:** hay 203 tests que ninguna máquina ejecuta en cada push (uno estuvo roto tres meses), las suites de Node van en serie (si falla la de reglas, las otras dos no se ejecutan), y quedan huecos sobre lo que G1 y G2 acaban de arreglar. Hoy todo pasa, por eso no es 5.
- **Esfuerzo 2:** un job de emulador en la CI y arreglos pequeños de tests; unas 5 o 6 tareas.

**Qué incluye:**

- **El hallazgo:** los tests instrumentados fuera de la CI.
- **L62** (`red-de-seguridad`): el ancla de «Hoy» solo la protege un instrumentado. Con el hallazgo resuelto, deja de ser un hueco. (L63, mover el ancla a `navigateToWorkspace`, va en «Sueltos»: es un cambio de código, no de la red.)
- **L66** (ídem): la CI ejecuta las tres suites de Node en serie en el mismo job; si falla la de reglas, no se ven las otras dos.
- **Tests que no miden lo que dicen:**
  - L64: el `setDoc` sobre una receta ajena falla por los campos inmutables, no por el dueño (otro test fija el dueño);
  - L65: `labelsAreNotEllipsizedAt130PercentFontScale` ya mide el recorte y repite a `todasLasEtiquetasTienenElMismoTamano`;
  - L67: `BetaUpdateProgressTrackerTest` repite casos de `BetaDistributionMappingTest`.
- **Huecos sobre arreglos recientes:**
  - L15: `badges()` con `pantryIds` vacío (despensa no leída);
  - L32: el organizador que sigue en el evento restaurado sin participar en la actividad;
  - L212: «Cancelar» conserva el aviso de cocinado;
  - L281: `restoredFields` y el redondeo de `Timestamp`;
  - L346: el Review Focus de la compra sin duplicados (fila PENDING larga y mismo producto comprado y pendiente);
- **Huecos antiguos (los de G8):** L312 (fixture singular + singular), L366 (reglas con justo 6 tiendas) y L406 (`BrandInput.toUpdateFields()`).

Los tests de L31 y L51 también son de red, pero los pongo en G10: protegen el marco que ese grupo cambia, y conviene escribirlos antes de tocarlo.

### G10 — Restos del marco de avisos y Deshacer (21)

Es lo que G2 dejó atrás. El código tiene un día, así que se arregla barato ahora y cada vez más caro después: cada pantalla nueva copia el marco tal como está.

**Por qué puntúa así:**

- **Impacto 3:** el marco lo usan 14 ViewModels y 13 pantallas, y tiene trampas que solo salen en ejecución (`checkNotNull` de los textos, `noticeText = { "" }` obligatorio, `notice` que se ignora si se pasa `noticeOf`).
- **Riesgo 4:**
  - 🕳 ningún test falla si se rompe `!restoring` en las altas de tareas, comidas, eventos y actividades (L31): es lo que impide que un Deshacer devuelva a quien ya no es miembro, un visible que G2 acaba de cerrar;
  - 🕳 los efectos centrales (`SaveFeedbackEffect`, `EditorSaveEffect`) no tienen test de Compose (L51), y de ellos dependen las 13 pantallas;
  - 🔁 hay 5 visibles pendientes en el mismo mecanismo (abajo).
- **Esfuerzo 3:** el marco está en `core/common` y `core/designsystem`; tocarlo obliga a repasar las 11 ramas `is Undoable.Completed -> Unit` y unas 8 pantallas. Unas 5 o 6 tareas, y quizá una decisión de diseño (L24).

**Internos (confirmados en el código):**

- **Deshacer (`UndoSnackbar.kt`, `Undoable`):**
  - L28: `UndoOfferEffect` usa `checkNotNull(deletedMessage/completedMessage)` (líneas 68-69): una pantalla que no pase el texto falla en ejecución. Hoy todas lo pasan (Tareas y el host de Hoy, los dos que completan);
  - L29: el conjunto `shown` (línea 60) repite lo que ya hacen `consumeOffer` y la clave por `serial`;
  - L30: `launchUndo`/`showUndo` siguen públicos; hay 11 ramas `is Undoable.Completed -> Unit` que no pueden ocurrir; `EVENT_CHANGED`, del Calendario, está en el enum común `FailureKind`;
  - L31: 🕳 ningún test falla si se rompe `!restoring` (`FirestoreTaskRepository.kt:259`, y las ramas `restoring` de comidas, eventos y actividades); falta el test puro del responsable de una tarea restaurada;
  - L33: `addItem` y `addNote` validan antes de comprobar la sesión;
  - L34: rama inalcanzable en `IngredientDetailViewModel` (personal sin cuenta);
  - L38: `PersonalCalendarScreen.kt:221` pasa `noticeText = { "" }`.
- **Guardado y errores (`SaveRunner`, `SaveState`, `FeedbackSnackbar.kt`):**
  - L51: 🕳 sin test de Compose de `SaveFeedbackEffect` ni de `EditorSaveEffect`;
  - L52: la Despensa traduce `PantryFailure`/`ShoppingFailure` comparando el nombre del enum como texto;
  - L54: restos de API (`ingredientMessageText(it, null, …)`, `SpacesViewModel.clearFeedback()` pública solo para el marco);
  - L55: `SaveRunner.run` recibe `notice` y `noticeOf` (línea 58: `noticeOf` toma `notice` por defecto);
  - L56: `savingOutsideEditor` se usa en 8 ficheros y es `saving && !editorOpen`;
  - L57: `AuthViewModel` conserva su `runAction` propio (línea 161).

**Menores con efecto visible del mismo mecanismo (pendientes):**

- **L24** (`deshacer`): los avisos de Deshacer en cola se consumen al encolarse; al girar con dos o tres en espera se pierden, y si no, el tercero sale pasada su caducidad. Pide una decisión de diseño.
- **L25** (ídem): la oferta de Deshacer puede salir y consumirse detrás de una hoja o un diálogo; `covered` solo se aplica a errores y avisos.
- **L44** (`guardado-y-errores`): «No encontramos el producto…» espera tapado tras la hoja de la marca y sale cuando ya no sirve.
- **L47** (ídem): un aviso que esperó tras una hoja sale al cerrarla sin contexto.
- **L50** (ídem): los avisos salen detrás de hojas y diálogos que no son editores.

Los otros visibles de esas dos secciones (L26, L27, L45, L46, L48, L49) son de pantallas concretas, no del marco: entran mejor en la oferta de menores con efecto visible.

### G4 — Documentación y comentarios desfasados (20)

Sigue siendo barato y de poco riesgo, y ha crecido de 16 a 18. Los que más importan siguen siendo L356 (`ROUTINE_CATALOG.md` no avisa de que borrar una común solo en la app no basta, ni de que guardarla sin cambios la congela) y L283-L285 (las reglas y los documentos de admin describen la app, no la regla). Cabe como una tarea dentro de otro plan.

- **Documentos:**
  - L269: `ARCHITECTURE.md` (módulo activo);
  - L283, L284 y L285: el comentario de `validRecipeShape()`, `DATA_MODEL.md`, `RECIPES_ADMIN.md`, `RECIPE_CATALOG.md` y el KDoc de `RecipeAdmins`;
  - L355: `DATA_MODEL.md`, «Rutinas»;
  - L356: `ROUTINE_CATALOG.md`;
  - L367: menciones a «supermercado»;
  - L383: `INGREDIENT_CATALOG.md` y comentarios de los ViewModels;
  - L432: `exercises-selection.json`;
  - L446: `EXERCISE_CATALOG.md` y comentarios de `normalize.mjs` y `CatalogExercise.kt`.
- **Comentarios y KDoc:**
  - L14: `ReviewRowKind.NEW` y `ReviewHint`;
  - L37: `SportActivityRepository.deleteActivity`;
  - L53: `SaveState.created`, la semántica de `showError` y dos comentarios de más de 120 columnas;
  - L122: `DeclareHeaderBand`, pública y con el KDoc obsoleto;
  - L206: `serverItemsBlocking`;
  - L230: la llamada a `CookedDialog`;
  - L231: `restockFromPantry`;
  - L365: las rutas citadas en `firestore.rules`.

### G11 — Avisos de beta: estado de la descarga (20)

🔁🔁 La sección `avisos-beta` tiene 11 visibles pendientes, la que más después de la fase C. Varios salen del estado de la descarga repartido en campos privados (L84): la fila de «Perfil» que no vuelve a `Idle` (L77), la búsqueda manual que no gasta el día (L78) y «Actualizar» con otra descarga viva (L79).

- L84: el estado de la descarga vive en campos privados (`downloadJob`, `downloadRelease`, `lastProgress`, líneas 86-88) y solo se copia a la UI con la hoja abierta;
- L85: `isTesterSignedIn` se lee en el constructor (línea 80; `SharedPreferences` en el hilo principal);
- L87: tests débiles del ViewModel (no comprueban `signInCalls == 0`, solo miran `testerSignedIn`, nombres en otro estilo, lectura tolerante sin `DataStore` real);
- L88: `releaseNotes?.takeIf { … }` repetido;
- L89: `BETA_DISTRIBUTION.md`, `BETA_TEST_PLAN.md` y la spec.

No lo recomiendo como plan de deuda: lo natural es un plan de los menores visibles de `avisos-beta` (L73-L83) que incluya estos 5.

### G3 — Constantes, validaciones y helpers duplicados (18)

🔁 Los límites de longitud duplicados ya provocaron dos visibles (resueltos). Los nuevos de la revisión de «Añadir a la compra» repiten el patrón: la misma clasificación y los mismos iconos en dos sitios.

- **Límites:** L282 (`MAX_BRAND_LENGTH` en tres sitios, y `feature/common` depende de `feature/shopping` por él), L347 (`MAX_QUANTITY_LENGTH`), L368 (el «6» de `strings.xml`).
- **Clasificación e iconos de la revisión:** L16 (`kind` y `badges()`), L17 (iconos copiados de la navegación), L18 (`chipColors` fuera del design system).
- **Consultas y escrituras repetidas:** L207 (la consulta de la lista visible, en 3 sitios), L234 (`applyCooked` repite el borrado de la despensa), L247 (`loadOrNull`), L248 (el listener de la despensa, en 3 sitios), L35 (`writeNewTask` no reutiliza `resolveAssigneeName`).
- **Recetas:** L329 (`toRecipeCount` usa 0 como marca), L330 (`recipeDetailsFailure` repite `validate`), L331 (los pasos se limpian 4 veces), L332 (los valores por defecto de `updateRecipe` vaciarían la receta en una llamada futura).
- **Varios:** L348 (`relistItem` recibe un `name` que no escribe), L408 (3 `Collator` y `requireVerifiedUid` copiado), L431 (`SetMeasure` en 3 sitios y la regex `WHITESPACE`).
- **Colores e iconos copiados sin enlace:** L95 (geometría monocroma del icono), L96 (colores del icono), L393 (color de la ventana), L364 (`SupermarketContrastTest` duplica los cálculos WCAG).

### G7 — Rendimiento y efectos en la composición (16)

🔁 L124 (`CollapsingZone` escribe `heightOffsetLimit` durante la medición, `BobitosLargeTopBar.kt:224`) está en el mismo fichero que 5 visibles pendientes de la cabecera plegable (L102-L106). Si se planifican esos visibles, L124 debe ir con ellos.

- **Escrituras y lecturas durante la composición:** L124, L125 (`BobitosTheme` lee `Settings.Global` en cada recomposición), L156 (`CompletingItems.retain`).
- **Proveedores fuera de Hilt:** L155 (`rememberToday()` abre un flujo de medianoche por llamada).
- **Cálculos repetidos:** L19 (`badges()`, `hint()` y `chipColors` con cada tecla), L183 (`associateBy` por franja), L298 (`RecipeSections.matching`), L299 (`ShoppingDuplicate.summed`), L349 (el índice de `findShoppingMatch`).
- **Arranque:** L391 (el `DisposableEffect` que se ejecuta dos veces en frío).

### G6 — Escuchas y lecturas de Firestore de más (15)

Sin cambios desde la auditoría anterior: cuesta poco con un grupo de beta pequeño y crecería con los usuarios.

- **Escuchas abiertas de más:** L244 (Comidas escucha la despensa siempre), L382 (Comidas y Recetario, 4 escuchas con hasta 1000 marcas), L270 (`space()` escucha todas las membresías).
- **Lecturas e índices:** L427 (`catalogRequested` suma una lectura por `observe()`), L444 (falta la exención de índice de `exerciseImages.data`).
- **Memoria:** L197 (`createdSpaces` solo crece).

### G5 — Scripts del catálogo e importadores, solo del admin (12)

Baja de 15 a 12: desde G1 la CI ejecuta sus tests. Los riesgos de L415 (lotes de más de 499), L419 (borrado a medias) y L438 (redirecciones fuera de `wger.de`) siguen ahí, pero con unos 350 ingredientes ninguno se alcanza hoy.

- **Recetas:** L314 (el importador lee `recipes` entera), L315 (`runRecipeImport` copia `runRoutineImport`), L316 (`RECIPE_UNITS` duplica el fixture), L317 (`ingredientKey` repetido), L318 (helpers duplicados).
- **Ingredientes:** L412 (texto del importador reutilizado), L413 (el test del borrado no siembra `recipes` ni `exerciseImages`), L414 (tabuladores y «|»), L415 (`batchSize` sin tope), L416 (`updatedBy` fijo), L417 (O(n·m)), L418 (`count()` sin límite de concurrencia), L419 (borrado a medias).
- **Imágenes:** L438 (`fetchBinary`), L439 (`sources.json` corrupto), L440 (problemas redundantes), L441 (decodificación de más), L442 (`--catalog` e `imagesDir`), L443 (límites de `chunkImages` sin test), L445 (la simulación no lista ids), L447 (`ImageRead` y `LoadedImage`).
- **Siembra y utilidades:** L333 (siembra del admin incompleta), L350 (`seed:emulators` no crea el usuario), L358 (`metaPath` y un alias).

### G9 — Estilo, avisos del compilador y restos (10)

La lista de excepciones de detekt no es un problema: ha bajado de 117 entradas (2026-10-01) a 77, aunque G2 añadió dos (L40).

- **Avisos y detekt:** L40 (dos entradas nuevas en el baseline), L158 (el icono obsoleto y entradas de la fase C), L245 (`@StringRes` sin destino), L61 (`createAndroidComposeRule` de `junit4`, obsoleto).
- **Código muerto:** L36 (`FIELD_UPDATED_BY` sin uso).
- **Nombres y estilo:** L20 (`lineCount` y `layoutsOf` en un test), L39 (dos KDoc de 121 caracteres), L68 y L69 (nombres de tests), L300 (nombre de un test), L392 (`parseThemeMode` en vez de `fromName`), L429 (`DataStore` sin calificador).

### Sueltos

- **L63** (`red-de-seguridad`): el ancla de «Hoy» es un caso especial en `openShoppingWithName`; moverla a `navigateToWorkspace` cubriría cualquier entrada futura a un módulo del espacio desde fuera. Encaja con R2 si se quiere, porque L62 es su test.
- **L154** (`evolucion-visual-fase-c`): contrastes de acento contra los tintes de otros módulos; hoy nunca se pintan así.
- **L407** (`marcas-personales-ingredientes`): borrar un ingrediente personal con 499 marcas o más supera el lote de 500.

## Internos que podrían estar mal clasificados

He revisado los que más podían tener efecto visible. Ninguno lo tiene en la práctica, así que el backlog no cambia:

- **L28:** fallaría en ejecución solo si una pantalla no pasa el texto; las dos que ofrecen «completada» (Tareas y Hoy) lo pasan.
- **L33:** solo se ve sin sesión y con datos inválidos, y al corregirlos sale el error de sesión.
- **L38:** `PersonalCalendarScreen` no emite avisos hoy.
- **L332 y L407:** hipotéticos; ningún camino actual los alcanza.
- **L415, L419 y L438:** son del admin y no tocan datos de usuarios.

## Recomendación

1. **R2 — Red de seguridad, segunda vuelta (28).** Hoy los 203 instrumentados pasan, así que es el momento barato de meterlos en la CI: un job con emulador (por ejemplo, `reactivecircus/android-emulator-runner`) que no empieza en rojo. Cierra 13 líneas del backlog más el hallazgo, y deja la red lista para G10, cuyos tests nuevos son de Compose. Es un plan grande, de unas 5 o 6 tareas. Habrá que decidir en el diseño si el job corre en cada push o solo en los PR, porque añade unos 10 a 15 min y los emuladores de la CI pueden dar falsos rojos.
2. **G10 — Restos del marco de avisos y Deshacer (21), junto con sus 5 visibles (L24, L25, L44, L47, L50).** El código tiene un día y lo copiará cada pantalla nueva. Cierra 18 líneas del backlog, dos de ellas huecos de red sobre lo que G2 acaba de arreglar (L31 y L51), que conviene escribir antes de tocar el marco. L24 pide una decisión de diseño (cómo salen varios Deshacer seguidos sin pasarse de su caducidad), así que probablemente necesite spec. Mejor después de R2, con los instrumentados en la CI, porque los tests de estos efectos son de Compose.

Si se quiere algo más barato, G4 (documentación) cabe como una tarea dentro de R2.
