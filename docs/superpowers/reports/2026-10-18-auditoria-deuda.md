# Auditoría de deuda: menores internos del backlog

Fecha: 2026-10-18 · Base: `main` en `28dc784` · Fuente: `docs/superpowers/backlog.md`. Solo he abierto el código para confirmar que siguen vigentes los menores de los grupos recomendados.

Los números «Lnnn» son líneas del backlog en `28dc784`.

## Resumen

- Hay **117 menores internos pendientes** en 25 secciones (del 2026-10-10 al 2026-10-18). Los he repartido en 9 grupos y 4 sueltos.
- **Al comprobarlos he encontrado algo que no está en el backlog y es más grave que cualquier menor.** El job «Android quality» de la CI lleva **21 ejecuciones seguidas en rojo en `main`**, desde el 2026-10-07. Cae siempre en «Set up Android SDK», así que hace 11 días que la CI no pasa ktlint, detekt, los tests unitarios, `lintDebug`, `assembleDebug` ni Kover. Solo se ejecuta el job de reglas, que sí pasa.
- Además, `lintDebug` falla en local con 1 error. En cuanto la CI vuelva a ejecutarse, se quedará en rojo en ese punto.
- Recomiendo **G1 (red de seguridad)** primero y **G2 (guardado, errores y Deshacer de los ViewModels)** después, este junto con los 14 menores con efecto visible que provoca.
- No hay internos mal clasificados: el backlog no cambia.

## Hallazgos de la comprobación (fuera del backlog)

1. **CI en rojo desde el 2026-10-07.**
   - Desde `bc14235` hasta `28dc784`, las 21 ejecuciones de `main` que han fallado lo han hecho en el mismo paso: `android-actions/setup-android@v3` avisa «Failed to find package 'tools'» y `sdkmanager` termina con código 1.
   - La última ejecución en verde es `0f74ba0`, del 2026-07-26.
   - Lo que sí ha comprobado cada plan es su `gate.sh` local. Lo que la CI debía comprobar en cada push no se ha comprobado en ninguno.
2. **`lintDebug` falla en local con 1 error, 24 avisos y 1 sugerencia.**
   - Es `RestrictedApi`: `NavController.getCurrentBackStack` en `BobitosNavHost.kt:739` (`openShoppingWithName`).
   - Está ahí desde el 2026-10-11 y ha ido en las betas 19 a 24 porque `assembleRelease` no ejecuta el lint.
   - Constaba en la memoria del proyecto desde el 2026-10-14, pero no en el backlog.

## Criterio

Uso el de `engineering:tech-debt`. Cada grupo se puntúa en **Impacto** (cuánto frena trabajar en el código, de 1 a 5), **Riesgo** (qué pasa si no se arregla, de 1 a 5) y **Esfuerzo** (de 1 a 5). La fórmula es:

**Prioridad = (Impacto + Riesgo) × (6 − Esfuerzo)**

Además, cada grupo lleva estas señales:

- 🕳 **Red floja:** tests rotos, tests que nunca fallan o partes que la CI no ejecuta. La red de seguridad protege menos de lo que parece.
- 🔁 **Provoca visibles:** el grupo está en el origen de menores con efecto visible, pendientes o ya resueltos.

## Grupos

| Grupo | Internos | Secciones | I | R | E | Prioridad | Señales |
|---|---:|---:|---:|---:|---:|---:|---|
| **G1** Red de seguridad: CI, lint y tests que nunca fallan | 13 + 2 hallazgos | 10 | 4 | 5 | 2 | **36** | 🕳 |
| G4 Documentación y comentarios desfasados | 16 | 11 | 2 | 2 | 1 | 20 | |
| G8 Huecos de tests (sin falso verde) | 7 | 7 | 2 | 3 | 2 | 20 | 🕳 (leve) |
| G3 Constantes, validaciones y helpers duplicados | 18 | 11 | 3 | 3 | 3 | 18 | 🔁 |
| **G2** Guardado, errores y Deshacer de los ViewModels | 14 | 7 | 4 | 4 | 4 | **16** | 🔁🔁 (14 visibles pendientes) |
| G7 Rendimiento y efectos en la composición | 10 | 7 | 2 | 2 | 2 | 16 | |
| G5 Scripts del catálogo e importadores (admin) | 24 | 6 | 2 | 3 | 3 | 15 | 🕳 (sin CI) |
| G6 Escuchas y lecturas de Firestore de más | 6 | 6 | 2 | 3 | 3 | 15 | |
| G9 Estilo, avisos del compilador y patrones | 5 | 5 | 1 | 1 | 1 | 10 | |
| Sueltos | 4 | 4 | — | — | — | — | |

### G1 — Red de seguridad: CI, lint y tests que nunca fallan (36)

**Por qué puntúa así:**

- **Impacto 4:** todos los planes dan por hecho que la CI los respalda, y no es así.
- **Riesgo 5:**
  - un error de lint ya ha llegado a seis betas;
  - hay tests que pasan pase lo que pase;
  - los scripts que escriben el catálogo común en Firestore no se prueban en la CI.
- **Esfuerzo 2:** son arreglos pequeños e independientes, de unas 5 a 7 tareas.

**Qué incluye:**

- **Hallazgo 1:** el paso «Set up Android SDK» de `.github/workflows/ci.yml`.
- **Hallazgo 2:** el error de `lintDebug` en `BobitosNavHost.kt:739`. Hay que arreglarlo antes o a la vez que la CI.
- **L356** (`catalogo-ingredientes`): la CI solo ejecuta `test:emulators`. Ni `test:scripts` (13 ficheros) ni `test:catalog-import` (5) se ejecutan en la CI.
- **L135, L295 y L326** (`evolucion-visual-fase-a`, `catalogo-rutinas` y `tema-oscuro-por-defecto`): `ProfileScreenTest` (instrumentado) falla por Hilt. **Es el mismo menor, apuntado en 3 secciones.** Sigue vigente: el test usa `createAndroidComposeRule<ComponentActivity>()` y `ProfileScreen.kt:79-80` crea sus `hiltViewModel()` por defecto.
- **L59** (`menores-evolucion-visual-fase-c`): «ninguna se corta» de `WorkspaceNavigationBarTest` nunca falla. Sigue vigente: la etiqueta lleva `maxLines = 1` sin `overflow` (es decir, `Clip`) en `WorkspaceScaffold.kt:210`, y el test mira `isLineEllipsized`.
- **L293** (`catalogo-rutinas`): los tests de la tabla compartida de rutinas pasarían con el fixture vacío.
- **L222** (`menores-compra-y-deshacer-comunes`): una aserción de `ShoppingQuantityTest` ya pasaba antes del arreglo.
- **L223** (ídem):
  - en el test de reglas de «Deshacer», el caso de `importedAt` futuro no prueba nada aparte;
  - un test depende del reloj;
  - a `RecipeRestoreTest` le falta el caso de dueño y editor retirados a la vez.
- **L168** (`menores-despensa-recetas`): `IngredientReviewUiTest` mide a 280 dp, pero el contenido real ronda los 232 dp. Es más laxo de lo que dice.
- **L33 y L34** (`logo`): `LauncherIconTest` fuerza los casts y protege `.monochrome` con un `assumeTrue` que lint no reconoce. Sigue vigente (líneas 31-38).
- **L22** (`avisos-beta`): si se quitara `afterDownload` del listener, volvería el fallo de «cancelar sale como error» con la suite en verde.
- **L249** (`catalogo-recetas`): ningún test de reglas fija que un `setDoc` de cliente no pise una receta ajena. Es un test de seguridad.

### G2 — Guardado, errores y Deshacer de los ViewModels (16)

**Por qué puntúa así:**

- **Impacto 4:** el mismo mecanismo está copiado en 12 o 13 ViewModels. Cada arreglo hay que repetirlo en 5, 11 o 12 sitios.
- **Riesgo 4:** es el tema que más menores visibles ha provocado en las fases A, B y C.
- **Esfuerzo 4:** es un refactor transversal; probablemente necesite spec, y quizá dos planes.

La fórmula lo baja por el esfuerzo. Pero ese esfuerzo habrá que pagarlo de todos modos para cerrar los 14 visibles que provoca.

**Internos — guardado y errores:**

- **L366** (`menores-aplazados`): `editorSave` está copiado en 13 ViewModels (confirmado: 13), y la derivación de `errorMessage`, en unas 10 pantallas.
- **L134** (`evolucion-visual-fase-a`): `runAction`/`action` devuelven `Any?` y se convierten con `as? CreatedItem` (confirmado: 12 usos).
- **L364** (`menores-aplazados`): el patrón `if (error is CancellationException) throw` (17 ficheros). Si un repositorio lanzara su propia `CancellationException`, el editor se quedaría en «Guardando…».
- **L149** (`menores-listo-has-cocinado`): la lambda `toMessage` de `confirmCooked` escribe estado, y ese contrato no está comentado.
- **L156** (`menores-has-cocinado`): `ItemNotFound` cae en `UnexpectedError` en el mapeo general de Comidas.
- **L207** (`menores-compra-membresia-y-comunes`): `AppViewModel` copia `error.message` a `UiState.Error`.

**Internos — Deshacer:**

- **L120** (`evolucion-visual-fase-b`):
  - `addX`/`restoreX` de Tareas, Compra, Notas y Comidas repiten la validación, la transacción y el mapa de campos;
  - `toLinkedEvent` y `restoredEventData` duplican `toCalendarEvent` y `EventInput.data`.
- **L136** (`evolucion-visual-fase-a`): el Deshacer forzado (`force`, en 12 ViewModels) solo tiene test propio en Tareas y Compra.
- **L116** (`evolucion-visual-fase-b`):
  - no hay tests de repositorio para la escritura directa del estado, `toLinkedEvent` ni `restoredEventData`;
  - `WeekDayStripTest` no comprueba el rol.
- **L117 y L122** (ídem): tests y detalles del relay de ingredientes borrados.
- **L118** (ídem): el Calendario del espacio no captura el `spaceId` en `restore`.
- **L121** (ídem):
  - Ejercicios y Rutinas usan un estado auxiliar para el snackbar de Deshacer;
  - `EventsLoadState` está en el fichero de una sola de las dos pantallas que lo usan.
- **L93** (`evolucion-visual-fase-c`): «marcar varias muy seguidas» solo se prueba en los ViewModels y en `CompletingItemsTest`, no en las pantallas.

**Menores con efecto visible que provoca (pendientes):**

- **L131** (`evolucion-visual-fase-a`): `showError(editor = true)` pone `isSaving = false` con otra acción en curso. Pasa en 5 áreas.
- **L130** (ídem): Deshacer al añadir borra aunque otro miembro haya cambiado el elemento.
- **L98** (`evolucion-visual-fase-b`, **seguridad**): `restore()` de los ingredientes personales no compara el dueño.
- **L99, L100, L101, L102, L103 y L104** (ídem): el relay que sobrevive al cierre de sesión, el `onDeleted` atado a la composición (11 áreas), Deshacer en dos escrituras, `restoreMeal`/`restoreActivity` sin revalidar participantes, el evento enlazado mal formado y `force = true` que pisa `isSaving`.
- **L56** (`menores-evolucion-visual-fase-c`): Deshacer al completar no compara `dueAt`/`startAt`.
- **L190** (`despensa`): `PantryUiMessage` mezcla errores y avisos.
- **L199** (`menores-compra-membresia-y-comunes`): el texto crudo de la excepción aparece en 7 áreas.
- **L360 y L362** (`menores-aplazados`): el `editorSave` compartido de Espacios y el error que se ve dos veces.

### G3 — Constantes, validaciones y helpers duplicados (18)

🔁 **Provoca visibles:** los límites de longitud duplicados ya provocaron dos menores visibles, hoy resueltos: la marca de 50 frente a 60 en la revisión y la marca larga que no bloqueaba «Guardar».

- **Límites:**
  - L218: `MAX_BRAND_LENGTH` está en tres sitios, y `feature/common` depende de `feature/shopping` por él;
  - L283: `MAX_QUANTITY_LENGTH`;
  - L304: el «6» de `strings.xml`.
- **Consultas y escrituras repetidas:**
  - L143: la consulta de la lista visible está escrita en 3 sitios;
  - L170: `applyCooked` repite el borrado de la despensa;
  - L183: `loadOrNull`;
  - L184: el listener de la despensa, en 3 sitios.
- **Recetas:**
  - L265: `toRecipeCount` usa 0 como marca;
  - L266: `recipeDetailsFailure` repite `validate`;
  - L267: los pasos se limpian 4 veces;
  - L268: los valores por defecto de `updateRecipe` vaciarían la receta en una llamada futura.
- **Varios:**
  - L284: `relistItem` recibe un `name` que no escribe;
  - L344: hay 3 `Collator` y `requireVerifiedUid` está copiado;
  - L367: el parseo de `SetMeasure` está en 3 sitios, y la regex `WHITESPACE` repite la de `TextSearch`.
- **Colores e iconos copiados sin enlace:**
  - L31: la geometría monocroma del icono;
  - L32: los colores del icono;
  - L329: el color de la ventana;
  - L300: `SupermarketContrastTest` duplica los cálculos WCAG.

### G4 — Documentación y comentarios desfasados (20)

Es barato y de poco riesgo. Los dos que más importan son L292 (`ROUTINE_CATALOG.md` no avisa de que borrar una común solo en la app no basta, ni de que guardarla sin cambios la congela) y L219-L221 (las reglas y los documentos de admin describen la app, no la regla). Encaja como una tarea dentro de otro plan.

- **Documentos:**
  - L25: `BETA_DISTRIBUTION.md`, `BETA_TEST_PLAN.md` y la spec de avisos;
  - L205: `ARCHITECTURE.md`;
  - L219, L220 y L221: las reglas, `DATA_MODEL.md`, `RECIPES_ADMIN.md`, `RECIPE_CATALOG.md` y el KDoc de `RecipeAdmins`;
  - L291: `DATA_MODEL.md`, «Rutinas»;
  - L292: `ROUTINE_CATALOG.md`;
  - L303: menciones a «supermercado»;
  - L319: `INGREDIENT_CATALOG.md` y comentarios de los ViewModels;
  - L382: `EXERCISE_CATALOG.md` y comentarios.
- **Comentarios y KDoc:**
  - L58: `DeclareHeaderBand`, pública y con el KDoc obsoleto;
  - L142: el comentario de `serverItemsBlocking`;
  - L166: la llamada a `CookedDialog`;
  - L167: el KDoc de `restockFromPantry`;
  - L301: las rutas citadas en `firestore.rules`;
  - L368: `exercises-selection.json`.

### G8 — Huecos de tests, sin falso verde (20)

Son tests que faltan, no tests que engañan. Si se quiere un plan de red de seguridad más completo, G1 puede absorberlos.

- L23: los tests del ViewModel de avisos de beta;
- L148: «Cancelar» conserva el aviso de cocinado;
- L217: `restoredFields` y el redondeo de `Timestamp`;
- L248: el fixture de unidades, singular + singular;
- L282: el Review Focus de compra sin duplicados;
- L302: un test de reglas con justo 6 tiendas;
- L342: `BrandInput.toUpdateFields()`.

### G7 — Rendimiento y efectos en la composición (16)

- **Escrituras y lecturas durante la composición:**
  - L60: `CollapsingZone` escribe durante la medición;
  - L61: `BobitosTheme` lee `Settings.Global` en cada recomposición;
  - L92: `CompletingItems.retain`.
- **Proveedores fuera de Hilt:** L91: `rememberToday()` crea su propio `CurrentDayProvider` y abre un flujo de medianoche por llamada.
- **Cálculos repetidos:**
  - L119: `associateBy` por franja;
  - L234: `RecipeSections.matching`;
  - L235: `ShoppingDuplicate.summed`;
  - L285: el índice de `findShoppingMatch`.
- **Arranque:**
  - L21: `SharedPreferences` en el hilo principal;
  - L327: el `DisposableEffect` que se ejecuta dos veces en frío.

### G5 — Scripts del catálogo e importadores, solo del admin (15)

Son de riesgo medio: L351 (lotes de más de 499), L355 (un borrado que queda a medias) y L374 (redirecciones fuera de `wger.de` y sin límite de tamaño) pueden dejar el catálogo común a medias. Hoy ni siquiera los prueba la CI (G1).

- **Recetas:**
  - L250: el importador lee `recipes` entera;
  - L251: `runRecipeImport` copia `runRoutineImport`;
  - L252: `RECIPE_UNITS` duplica el fixture;
  - L253: `ingredientKey` se repite;
  - L254: helpers duplicados.
- **Ingredientes:**
  - L348: texto del importador reutilizado;
  - L349: el test del borrado no siembra `recipes` ni `exerciseImages`;
  - L350: tabuladores y «|» en el nombre;
  - L351: `batchSize` sin tope;
  - L352: `updatedBy` fijo;
  - L353: O(n·m);
  - L354: `count()` sin límite de concurrencia;
  - L355: borrado a medias.
- **Imágenes:**
  - L374: `fetchBinary`;
  - L375: un `sources.json` corrupto;
  - L376: problemas redundantes;
  - L377: decodificación de más;
  - L378: `--catalog` e `imagesDir`;
  - L379: los límites de `chunkImages` sin test;
  - L381: la simulación no lista ids;
  - L383: `ImageRead` y `LoadedImage`.
- **Siembra y utilidades:**
  - L269: siembra del admin incompleta;
  - L286: `seed:emulators` no crea el usuario;
  - L294: `metaPath` y un alias.

### G6 — Escuchas y lecturas de Firestore de más (15)

- **Escuchas abiertas de más:**
  - L180: Comidas escucha la despensa siempre;
  - L318: Comidas y Recetario mantienen 4 escuchas, con hasta 1000 marcas;
  - L206: `space()` escucha todas las membresías.
- **Lecturas e índices:**
  - L363: `catalogRequested` suma una lectura por `observe()`;
  - L380: falta la exención de índice para `exerciseImages.data`.
- **Memoria:** L133: `createdSpaces` solo crece.

Hoy, con un grupo de beta pequeño, cuesta poco; crecería con los usuarios.

### G9 — Estilo, avisos del compilador y patrones (10)

- L94: el icono obsoleto y entradas nuevas en el baseline de detekt;
- L181: `@StringRes` sin destino;
- L236: el nombre de un test;
- L328: `parseThemeMode`, en lugar de `fromName`;
- L365: `DataStore` sin calificador.

### Sueltos

- **L20** (`avisos-beta`): un `DownloadState` en `BetaUpdateUiState`. Lo natural es hacerlo con los menores visibles de esa sección (L13 a L15), no por separado.
- **L24** (ídem): `releaseNotes` repetido.
- **L90** (`evolucion-visual-fase-c`): contrastes de acento contra los tintes de otros módulos. Hoy nunca se pintan así.
- **L343** (`marcas-personales-ingredientes`): borrar un ingrediente personal con 499 marcas o más supera el lote de 500.

## Internos que podrían estar mal clasificados

He revisado los que más podían tener efecto visible. Ninguno lo tiene en la práctica, así que el backlog no cambia:

- **L343:** el lote falla entero, sin perder datos, y hacen falta 499 marcas en un mismo ingrediente.
- **L374:** es una herramienta del admin y no toca datos de usuarios.
- **L269:** solo se vio en la siembra del emulador.
- **L220:** era una ventana de transición, ya pasada.
- **L364 y L268:** son hipotéticos; ningún camino actual los alcanza.

## Recomendación

1. **G1 — Red de seguridad (36).** Duplica la puntuación del siguiente y su esfuerzo es bajo. Sin él, cualquier otro plan, también G2, se apoya en una CI que no ejecuta nada de Android desde hace 11 días. Es un plan grande, de unas 5 a 7 tareas.
2. **G2 — Guardado, errores y Deshacer de los ViewModels, junto con sus 14 visibles.** La fórmula lo pone en 16 por el esfuerzo, pero es el grupo que sigue provocando menores visibles, y uno de ellos es de seguridad (L98). Arreglar esos visibles uno a uno obliga a repetir cada cambio en 5 a 12 ViewModels; hacerlo una vez en una pieza común cierra 28 líneas del backlog. Mejor después de G1, con la CI en verde, porque es un refactor transversal. Probablemente necesite spec y quizá dos planes: primero guardado y errores, después Deshacer.

Si se quiere algo más barato en lugar de G2, G4 (documentación) cabe como una tarea dentro de G1.
