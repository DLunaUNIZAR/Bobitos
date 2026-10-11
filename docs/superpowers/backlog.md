# Backlog de menores aplazados

Menores que las revisiones de cada rama dejaron sin arreglar. Cada línea dice si tiene **efecto visible** para un usuario o es **interno**.

Los 20 menores anteriores (ramas `agent/tareas-sin-alta-rapida` y `agent/catalogo-ejercicios-wger`) se cerraron en la rama `agent/menores-aplazados` (plan `docs/superpowers/plans/2026-10-10-menores-aplazados.md`).

## 2026-10-11 — `agent/tiendas-de-barrio`

- **Efecto visible:** contraste de los colores de tienda: Carrefour en oscuro (2,25 sobre el fondo, 2,12 sobre su tarjeta); Eroski, Frutería e Indiferente sobre su tarjeta en claro (2,7-2,9); y los iconos sobre las tarjetas por defecto de Ingredientes y sobre los chips seleccionados en oscuro quedan por debajo de 3:1 (llevan etiqueta de texto). DIA y Carnicería ya se arreglaron (plan `2026-10-11-contraste-dia-carniceria.md`); si se aborda, ampliar `SupermarketContrastTest` a `Supermarket.entries`.
- **Interno:** `SupermarketContrastTest` copia el alfa del tinte (0.12f) en vez de usar `CATEGORY_TINT_ALPHA`, duplica los cálculos WCAG de `ModuleColorsTest`, mezcla el tinte a mano en vez de `compositeOver` y usa el umbral 0.03928 en vez de 0.04045.
- **Interno:** los comentarios de `firestore.rules` junto a las listas de tiendas citan `APP/core/model/ShoppingItem.kt` (abreviatura del plan); deberían citar la ruta real y `SupermarketRulesSyncTest`.
- **Interno:** falta un test de reglas que acepte justo 6 tiendas incluyendo las nuevas.
- **Interno:** quedan menciones a «supermercado(s)» en `docs/DATA_MODEL.md:574` y en comentarios (`ShoppingScreen`, `SupermarketTokens`, `IngredientPrefsRepository`).
- **Interno:** el «6» de las pistas está escrito a mano en `strings.xml` (no sale de `MAX_STORES`).

## 2026-10-11 — `agent/marca-al-anadir-a-la-compra`

- **Efecto visible:** en el diálogo de revisión, la tienda «Mercadona» se parte en dos líneas («Mercado/na») por el ancho del desplegable; en `SupermarketDropdown` falta `maxLines = 1` con elipsis, y la marca se corta sin elipsis (visto en el emulador).
- **Efecto visible:** reconocer un ingrediente pisa la tienda (y la marca escrita) que el usuario ya había puesto; salir y volver al ingrediente devuelve los valores por defecto; al reabrir el editor el selector tarda un instante y una tienda elegida en ese instante se pisa.
- **Efecto visible:** una elección manual igual al valor por defecto se pisa si llegan datos nuevos con el formulario abierto; si el ingrediente pasa a tener marcas con el formulario abierto, la marca escrita a mano no se guarda.
- **Efecto visible:** `selectBrand` trata como «de la marca anterior» una tienda elegida a mano que esté entre las de esa marca, y la cambia al pasar a una marca sin tiendas.
- **Efecto visible:** si se pide la revisión de una receta o comida antes de que lleguen los datos de ingredientes, las filas salen sin selector ni tienda (antes las tiendas se leían al confirmar).
- **Efecto visible:** un ingrediente personal con la misma clave que uno común (p. ej. «Tomates» y «tomate») queda tapado en el selector; desde su ficha salen las marcas del otro.
- **Efecto visible:** accesibilidad del selector: las dos columnas no son grupos de recorrido (TalkBack lee «Marca», «Tienda», valor, valor), «Toca para cambiarla» sobra y «★» se lee «estrella negra».
- **Efecto visible:** sin conexión, la ficha muestra «Añadir a la compra»; ahora el formulario espera a poder escribir, pero no se avisa de por qué no se abre.
- **Efecto visible:** el diálogo de revisión pierde al girar la cantidad, la marca y la tienda elegidas; y si el proceso muere, el borrador de la Compra vuelve a los valores por defecto.
- **Efecto visible (ya pasaba antes):** la revisión lee los artículos de la Compra de la caché: uno borrado en otro dispositivo sigue saliendo como «Ya en la lista» hasta abrir la Compra.
- **Interno:** Comidas y Recetario mantienen 4 escuchas (hasta 1000 marcas) mientras están abiertas; se podría cargar al pedir la revisión.
- **Interno:** `INGREDIENT_CATALOG.md` dice que el selector sale al crear «(y editar)» y omite el caso de dos favoritas; los comentarios de `MealsViewModel` y `RecipesViewModel` hablan solo de «Dónde lo compro».

## 2026-10-11 — `agent/tema-oscuro-por-defecto`

- **Efecto visible:** quien elige Claro (o Sistema con el móvil en claro) ve un destello oscuro breve al abrir la app (aceptado). Se podría quitar para todos con la pantalla de arranque esperando a leer la preferencia (`installSplashScreen().setKeepOnScreenCondition`).
- **Efecto visible:** el selector de tema de Perfil marca «Oscuro» uno o dos fotogramas antes de pasar al valor guardado (`ProfileScreen.kt`: el `ThemeViewModel` de la pantalla es otra instancia y arranca en el valor por defecto).
- **Efecto visible:** con Claro elegido, la barra flotante de copiar y pegar sale oscura, porque toma el tema XML (`themes.xml`); antes pasaba al revés con Oscuro.
- **Interno:** `ProfileScreenTest` (instrumentado) falla desde 566294d: `ProfileScreen` usa `hiltViewModel()` y el test lo monta en una `ComponentActivity` sin Hilt. La CI no ejecuta los instrumentados.
- **Interno:** en frío, el `DisposableEffect` de `MainActivity` se ejecuta dos veces para quien tiene Claro o Sistema (primero con el oscuro provisional).
- **Interno:** `parseThemeMode` vive en `designsystem` como función suelta; el patrón del proyecto es un `fromName` en el companion del enum (`ReminderLeadTime`).
- **Interno:** el color de la ventana (`colors.xml`) duplica `backgroundDark` de `Color.kt` sin avisarlo en el comentario.

## 2026-10-11 — `agent/marcas-personales-ingredientes`

- **Efecto visible:** mientras el catálogo común carga, o si dio error, «Añadir ingrediente» se salta la comprobación por clave y puede crear un duplicado («Tomates» junto a «Tomate») (`IngredientsViewModel.kt`).
- **Efecto visible:** tras un alta duplicada se navega a la ficha existente, pero el formulario sigue abierto con el error y reaparece al volver a la lista, con el aviso rojo arriba (`IngredientsScreen.kt`; visto en el emulador).
- **Efecto visible:** el interruptor «Común (catálogo)» del admin se queda encendido para la siguiente alta (`IngredientsScreen.kt`, `rememberSaveable`).
- **Efecto visible:** al escanear desde la ficha, si Open Food Facts falla solo sale un error y no se abre el editor de marca con el código ya puesto, como sí hace la lista (`IngredientDetailViewModel.kt`).
- **Efecto visible:** el escaneo desde la ficha no ignora las marcas huérfanas (común borrado por el admin): avisa «ya está en tu marca X de <slug>» y no deja archivar el código en este ingrediente.
- **Efecto visible:** si la escritura optimista de tiendas o favorita falla antes de llegar a Firestore (sin red, sin sesión), el chip o la estrella se quedan en el estado nuevo junto al aviso hasta volver a entrar (`IngredientDetailViewModel.kt`).
- **Efecto visible:** renombrar un ingrediente a un nombre que coincide por clave con otro no se comprueba (la spec solo lo exige al crear): pueden quedar dos fichas con la misma clave.
- **Efecto visible:** mientras «Crear ficha de ingrediente» carga el catálogo en la Compra (hasta 30 s, normalmente al instante desde la caché), `isSaving` bloquea las demás acciones de la Compra.
- **Efecto visible:** el botón de editar de cada tarjeta de marca dice «Editar ficha» (`ingredients_edit`), igual que el del ingrediente; ya pasaba antes de esta rama.
- **Interno:** `BrandInput.toUpdateFields()` (no escribe `ingredientId`, borra código y nutrientes ausentes) no tiene test JVM.
- **Interno:** borrar un ingrediente personal con 499 marcas o más supera las 500 operaciones del lote (`FirestorePersonalIngredientRepository.delete` no trocea como `deletePersonalData`).
- **Interno:** hay tres `Collator` es-ES iguales (marcas, ingredientes, ejercicios) y `requireVerifiedUid` está copiado en los repositorios de personales, marcas y preferencias.

## 2026-10-11 — `agent/catalogo-ingredientes`

- **Interno:** el borrado inicial reutiliza el texto del importador: dice «no se importa a bobitos-dev» (`admin-cli.mjs` y la tabla de problemas de `INGREDIENT_CATALOG.md`).
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

