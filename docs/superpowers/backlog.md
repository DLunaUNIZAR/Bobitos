# Backlog de menores aplazados

Menores que las revisiones de cada rama dejaron sin arreglar. Cada línea dice si tiene **efecto visible** para un usuario o es **interno**.

Los 20 menores anteriores (ramas `agent/tareas-sin-alta-rapida` y `agent/catalogo-ejercicios-wger`) se cerraron en la rama `agent/menores-aplazados` (plan `docs/superpowers/plans/2026-10-10-menores-aplazados.md`).

## 2026-10-11 — `agent/catalogo-ingredientes`

- **Interno:** el borrado inicial reutiliza el texto del importador: dice «no se importa a bobitos-dev» (`admin-cli.mjs` y la tabla de problemas de `INGREDIENT_CATALOG.md`).
- **Interno:** `INGREDIENT_CATALOG.md` dice que la beta 18 dejará de poder crear preferencias (sí podrá) y no avisa de que verá Ingredientes vacío tras importar, porque descarta los documentos sin `ownerUid` ni `createdBy`.
- **Interno:** el test del borrado no siembra `recipes` ni `exerciseImages` para comprobar que no se tocan.
- **Interno:** `validateIngredient` admite tabuladores y «|» en el nombre; una «|» rompería la tabla de `ingredients-review.md`.
- **Interno:** `commitCatalogOps` no pone tope a `batchSize`: con más de 499, la escritura de la versión supera el límite de 500 por lote.
- **Interno:** `updatedBy` usa siempre `CATALOG_ADMIN_UID`, aunque se pase otro `adminUid` al plan.
- **Interno:** el plan de importación recalcula `ingredientKey` por cada pareja nuevo-existente (O(n·m); despreciable con unos 350).
- **Interno:** `countIngredientData` lanza un `count()` por ingrediente sin límite de concurrencia.
- **Interno:** si falla el segundo `recursiveDelete` (preferencias), el borrado queda a medias; hay que repetir el script.
- **Interno:** la CI solo ejecuta `test:emulators`; ni `test:scripts` ni `test:catalog-import` corren en CI.

## 2026-10-10 — `agent/menores-aplazados`

- **Efecto visible:** los diálogos de Espacios sacan «guardando» de un `editorSave` que comparten varias pantallas. Si hay en curso una acción que no es del editor, el botón no hace nada (`SpacesScreen.kt`, `SpaceSettingsScreen.kt`).
- **Efecto visible:** el filtro por grupo compara el `muscleGroup` sin recortar con el chip, que sí está recortado: «Pecho » no coincide con el chip «Pecho» (`ExerciseFilter.kt`).
- **Efecto visible:** en Espacios e Ingredientes, el error se ve dos veces: en el banner y dentro del formulario.
- **Interno:** la marca `catalogRequested` de Deporte no se borra al cerrar el editor de sesión. Cada `observe()` suma una lectura de la versión (`SportViewModel.kt`).
- **Interno:** si un repositorio lanzara su propia `CancellationException`, el editor se quedaría en «Guardando…». Afecta al patrón `if (error is CancellationException) throw` de los ViewModels.
- **Interno:** `DataStore<Preferences>` está enlazado en Hilt sin calificador (`DataModule.kt`).
- **Interno:** el mecanismo `editorSave` está copiado en 13 ViewModels, y la derivación de `errorMessage` en unas 10 pantallas. Falta un helper común.
- **Interno:** el parseo de `SetMeasure` está en 3 sitios, la etiqueta de medida está duplicada y la regex `WHITESPACE` repite la de `TextSearch`.
- **Interno:** `exercises-selection.json:3` tiene una frase contradictoria.

## 2026-10-10 — `agent/imagenes-en-firestore`

- **Efecto visible:** sin red, y con un hash distinto tras una reimportación, la imagen se oculta aunque haya bytes en caché (falta `cached?.bytes` como reserva en `ExerciseImageRepository.kt`).
- **Efecto visible:** el crédito de la imagen no enlaza a su procedencia (`sourceUrl`). CC BY-SA pide la URI «en la medida razonable» (`ExerciseImageBlock.kt`).
- **Interno:** `fetchBinary` sigue redirecciones fuera de `wger.de/media` (falta `redirect: 'error'`) y no limita el tamaño antes de `arrayBuffer()`.
- **Interno:** un `sources.json` corrupto se trata como si no existiera y fuerza una redescarga silenciosa. Solo `ENOENT` debería caer en la reserva.
- **Interno:** una WebP que falta genera 2 o 3 problemas redundantes. `hashOptional` e `imageSources` sin definir son modos que solo usan los tests: habría que separar `selectSources` de `attachImages`.
- **Interno:** el importador vuelve a decodificar cada WebP con `sharp` para sacar `width` y `height`, campos que la app no lee.
- **Interno:** `--catalog` con un catálogo de otra carpeta no cambia `imagesDir`.
- **Interno:** `chunkImages` no tiene test de los límites de 50 documentos y 5 MiB.
- **Interno:** falta una exención de índice para `exerciseImages.data` en `firestore.indexes.json`.
- **Interno:** la simulación del importador no lista los ids de las imágenes que subiría.
- **Interno:** hay comentarios desactualizados (`normalize.mjs:293`, «enlazables»; `CatalogExercise.kt:43`, `sourceUrl` «página de origen»), y `EXERCISE_CATALOG.md` pide `catalog:build` después de `catalog:images`.
- **Interno:** `ImageRead`/`decideImageRead` envuelven una comparación de una línea, y `LoadedImage` es una data class con un `ByteArray` (su `equals` compara por referencia).

