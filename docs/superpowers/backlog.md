# Backlog de menores aplazados

Menores que las revisiones de cada rama dejaron sin arreglar. Cada línea dice si tiene **efecto visible** para un usuario o es **interno**.

Los 20 menores anteriores (ramas `agent/tareas-sin-alta-rapida` y `agent/catalogo-ejercicios-wger`) se cerraron en la rama `agent/menores-aplazados` (plan `docs/superpowers/plans/2026-10-10-menores-aplazados.md`).

## 2026-10-13 — `agent/menores-compra-membresia-y-comunes`

- **Efecto visible (previo):** Tareas, Notas, Comidas, Deporte, Recetario, Rutinas y los ajustes del espacio (miembros, invitaciones) muestran el texto crudo de la excepción (`UiState.Error(error.message)`), en inglés; al perder la membresía pueden enseñarlo un instante antes de volver a «Espacios». Arreglo de raíz: que `UiState.Error`/`ErrorState` no lleven el texto de la excepción.
- **Efecto visible (previo):** `AppLoadError` no tiene «Reintentar» ni salida: si falla la consulta de membresías (p. ej. un token aún sin `email_verified`), la app se queda en «No se ha podido iniciar Bobitos.» hasta cerrarla (`BobitosApp.kt`, `AppViewModel`).
- **Efecto visible (raro):** con `PERMISSION_DENIED`, `observeSpace` emite `null` y deja el listener muerto (y `RealtimeMetrics` lo cuenta activo): si la membresía no cambia (token sin `email_verified`, despliegue de reglas, re-invitación entre dos snapshots), el espacio queda oculto en «Espacios» hasta reiniciar la app. Antes, en ese caso, salía `AppLoadError` (`FirestoreSpaceRepository.observeSpace`).
- **Efecto visible:** las mayúsculas no se conservan en «-ces» → «-z» («1/2 NUECES» × 2 → «1 NUEz») ni en el plural («1 PAN ALEMÁN» × 2 → «2 PANes ALEMÁNes»: el sufijo va en minúscula y `withoutFinalAccent` no reconoce la vocal con tilde en mayúscula). Lo del plural viene de antes (`ShoppingQuantity.kt`).
- **Efecto visible (datos, previo):** el borrado de cuenta no trata las rutinas: las privadas quedan huérfanas y las comunes conservan el nombre real del autor (`FirebaseAccountRepository.anonymizeOrDeleteOwnedRecipes` solo mira `recipes`). `docs/RECIPES_ADMIN.md` dice ya que un admin retirado «solo puede anonimizar su autor» en recetas y rutinas, que es lo que permiten las reglas, no lo que hace la app.
- **Efecto visible (sin confirmar, previo; visto en el recorrido del emulador):** en «Tus espacios», la cabecera va pegada a la barra de estado, sin el margen superior de otras pantallas, y un toque en su icono «Perfil» no hizo nada (desde la Compra y el hub sí funciona). Puede ser de insets o de la herramienta de toques.
- **Interno:** `docs/ARCHITECTURE.md` (líneas 55 y 78) describe el módulo activo con el documento de la membresía; ahora es la consulta de las membresías activas del usuario.
- **Interno:** `space()` escucha todas las membresías activas del usuario (N lecturas por suscripción y cambios de otros espacios); bastaría la consulta `userId` + `spaceId` + `status` + `limit(1)` de `FirestoreSyncRepository.refresh`.
- **Interno:** `AppViewModel` sigue copiando `error.message` a `UiState.Error` (líneas 69 y 202), que ya no se muestra.

## 2026-10-13 — `agent/menores-compra-y-deshacer-comunes`

- ~~**Efecto visible (casi inalcanzable):** con una edición de una común aún sin confirmar (`updatedAt` pendiente: `toRecipe` usa `createdAt`), borrarla y deshacer la recrea con un `updatedAt` antiguo. Si se reimportó con cambios, la regla la rechaza (`importedAt > updatedAt`) y la común se pierde; si no, el importador pisaría la edición. Arreglo barato: en `restoreAudit`, si `importedAt > updatedAt`, `updatedAt = null` (hora del servidor) (`RecipeRestore.kt`, `FirestoreRecipeRepository.toRecipe`).~~ **Resuelto** en `agent/menores-compra-membresia-y-comunes`.
- ~~**Efecto visible:** en el editor de la Compra, la marca libre de más de 60 caracteres se marca en rojo pero «Guardar» sigue: `ShoppingValidation.validate` no mira la marca y el error llega después, del repositorio o tras el aviso «Ya en la lista».~~ **Resuelto** en `agent/menores-compra-membresia-y-comunes`.
- **Efecto visible (limitación aceptada):** el singular aproximado falla en palabras raras como unidad: «vales» → «val», «trajes» → «traj», «meses» → «mese», «cines» → «cin», «regímenes» → «regimen» (ruling 1 de `agent/menores-compra-membresia-y-comunes`; `ShoppingQuantity.singularOf`). «dulces» y la tilde de «comunes» y «alemanes» se resolvieron en esa rama.
- ~~**Efecto visible:** la lista de la Compra con un error de lectura no se reintenta sola; solo vuelve a escuchar al pulsar «Guardar» en el editor (`ShoppingViewModel.observe`). Viene de antes de la rama.~~ **Resuelto** en `agent/menores-compra-membresia-y-comunes`.
- ~~**Efecto visible (previo, visto en el recorrido del emulador):** si el usuario pierde la membresía del espacio con la Compra abierta, la app muestra a pantalla completa el texto crudo «com.google.firebase.firestore.FirebaseFirestoreException: PERMISSION_DENIED: evaluation error at L818…», sin navegación; al devolverle la membresía no se recupera y «Atrás» sale de la app. Por eso no se pudo probar el paso 4 (lectura fallida en el editor) en el emulador.~~ **Resuelto** en `agent/menores-compra-membresia-y-comunes`.
- ~~**Efecto visible (seguridad, previo):** un admin retirado de `recipeAdmins()` aún puede borrar sus comunes por la rama de dueño de `ownsOrCuratesRecipe` (`firestore.rules`), aunque ya no puede deshacer ese borrado.~~ **Resuelto** en `agent/menores-compra-membresia-y-comunes`.
- **Interno:** ningún test ejercita `restoredFields` en Kotlin ni el redondeo `Timestamp` → `Instant` → `Timestamp` a milisegundos frente al importador; lo cubre a mano el recorrido de punta a punta (paso 6).
- **Interno:** `catalogImportedAt()` repite los literales de `source` del companion privado, e `Instant.toTimestamp()` duplica el de `FirestoreTaskRepository.kt`. `MAX_BRAND_LENGTH` está en tres sitios (reglas, `FirestoreShoppingRepository`, `ShoppingValidation`), y `feature/common` depende de `feature/shopping` por él.
- **Interno:** el comentario de `firestore.rules` en `validRecipeShape()` y `docs/DATA_MODEL.md` («un admin solo lo conserva al deshacer») describen la app, no la regla: `validRecipeRestore` deja a un admin crear cualquier común nueva con `source` y fechas pasadas.
- **Interno:** `RecipeAdmins.uids` ya decide el dueño al deshacer, no es solo un filtro de interfaz como dicen `docs/RECIPES_ADMIN.md` y el KDoc de `RecipeAdmins.kt`. Entre desplegar las reglas y publicar la app, una app desfasada sigue fallando al deshacer una común de un admin retirado.
- **Interno:** `docs/RECIPES_ADMIN.md` y `docs/RECIPE_CATALOG.md` no cuentan que una común deshecha a nombre de otro admin pasa a «omitida (de usuario)» en el importador, y «a nombre de quien deshace» es ambiguo (`createdByName` se conserva).
- **Interno:** en `ShoppingQuantityTest`, la aserción en singular de `only the words before any preposition…` ya pasaba antes del arreglo (mejor «1/2 bolsas para galletas»), y `every word of the unit agrees in number up to the first de` debería decir «preposition».
- **Interno:** a `RecipeRestoreTest` le falta el caso de dueño y editor retirados a la vez; en el test de reglas de «Deshacer», el caso de `importedAt` futuro no prueba nada aparte; el test «solo un admin crea una receta con la fuente del catálogo» podría usar `Date.now() - 60 s` para no depender del reloj.

## 2026-10-13 — `agent/menores-recetas-compra`

- ~~**Efecto visible:** `ShoppingQuantity.singularOf` no es el inverso de `pluralOf`: solo quita la «s» de la última palabra («1/2 cucharadas soperas» × 2 → «1 cucharadas sopera»), y las palabras en «-es» tras l, r, n, d o j pierden solo la «s» («boles» → «bole», «panes» → «pane»).~~ **Resuelto** en `agent/menores-compra-y-deshacer-comunes`.
- ~~**Efecto visible:** las abreviaturas salen mal en plural, como antes: «cl» → «cles», «oz» → «oces», «mg» → «mgs», «cc» → «ccs». Arreglo barato: no cambiar una unidad sin vocales, o añadir cl, dl y mg a `BASIC_UNITS` (`ShoppingQuantity.kt`).~~ **Resuelto** en `agent/menores-compra-y-deshacer-comunes`.
- ~~**Efecto visible:** en el aviso «Ya en la lista», una cantidad existente larga, que va entera con espacios que no parten línea, se corta a mitad de palabra («… del supe / r»). Idea: U+00A0 solo entre el número y la unidad, o solo si la cantidad es corta (`ShoppingDuplicate.existingQuantityLabel`; visto en el emulador).~~ **Resuelto** en `agent/menores-compra-y-deshacer-comunes`.
- ~~**Efecto visible:** en el editor de la Compra, si la lectura de la lista falla (no solo si está cargando), «Guardar» dice «Aún no se ha cargado la lista: espera un momento…» y no se recupera hasta reabrir la pantalla. Un listener de Firestore solo falla por causas permanentes (permiso, índice), así que es raro (`ShoppingViewModel.addItem`).~~ **Resuelto** en `agent/menores-compra-y-deshacer-comunes`.
- ~~**Efecto visible:** «Sumar cantidad» rellena la tienda y la marca vacías por separado, y puede juntar una tienda y una marca que no van juntas (artículo con LIDL y sin marca + «Hacendado» de Mercadona → LIDL + Hacendado). Es la decisión del usuario de rellenar cada hueco; posible ajuste: rellenarlas como pareja (`ShoppingViewModel.sumDuplicate`).~~ **Resuelto** en `agent/menores-compra-y-deshacer-comunes`.
- ~~**Efecto visible:** en la revisión de «Añadir a la compra», una marca libre de más de 50 caracteres en una fila hace fallar toda la revisión con «No se ha podido completar la operación.», sin decir qué fila; el campo no limita la longitud. El mensaje genérico viene de antes de la rama (`FirestoreShoppingRepository.applyWrites`, `BrandStorePicker`).~~ **Resuelto** en `agent/menores-compra-y-deshacer-comunes` (el límite es 60).
- ~~**Efecto visible:** `MealsViewModel.markCooked` sigue leyendo la Compra con `items().first()`: con la caché vieja puede ofrecer tachar algo ya tachado o no ofrecer lo recién apuntado (tachar es idempotente).~~ **Resuelto** en `agent/menores-compra-y-deshacer-comunes`.
- **Interno:** `RecipeSections.matching` normaliza título, categoría e ingredientes de cada receta en cada recomposición; se podría plegar una vez por lista.
- **Interno:** `ShoppingDuplicate.summed` es una propiedad calculada y `ShoppingQuantity.sum` se repite unas cuatro veces por aviso; bastaría calcularla en el constructor.
- **Interno:** `ShoppingStringsTest.elAvisoDeRepetidoNoPermiteParTrasCantidad` no sigue el estilo de nombres con comillas invertidas del resto de tests.

## 2026-10-12 — `agent/catalogo-recetas`

- ~~**Efecto visible:** en «Lasaña de carne», el paso 2 dice «Sala y pimienta.»; debería ser «Salpimienta.» (`data/catalog/recipes.json`; el usuario aprobó el catálogo tal cual).~~ **Resuelto** en `agent/menores-recetas-compra` (llega a la app al reimportar las recetas en `bobitos-dev`).
- ~~**Efecto visible:** pulsar «Deshacer» mientras el borrado sigue en curso no hace nada: `runAction` retorna si `isSaving`, y con red lenta la receta se queda borrada sin aviso (`RecipesViewModel.restoreRecipe`). Viene de antes de esta rama.~~ **Resuelto** en `agent/menores-recetas-compra`: «Deshacer» solo se ofrece cuando el borrado se ha confirmado.
- ~~**Efecto visible:** «Deshacer» se ofrece aunque el borrado falle (`RecipesScreen.kt`, `launchUndo` tras `deleteRecipe`). Con `restoreRecipe`, pulsarlo da un error de permiso sobre una receta que nunca se borró (antes creaba un duplicado).~~ **Resuelto** en `agent/menores-recetas-compra`.
- ~~**Efecto visible:** al deshacer el borrado de una receta antigua sin `createdByName`, se guarda el uid como autor. La app ya lo mostraba así, porque `toRecipe` cae en `createdBy` (`FirestoreRecipeRepository.restoreRecipe`).~~ **Resuelto** en `agent/menores-recetas-compra`: el autor queda vacío en vez del uid.
- ~~**Efecto visible:** si un admin deshace el borrado de una común, `ownerUid` y `createdBy` pasan a ser los suyos: con un segundo admin, el importador la vería como «ficha de usuario». Y si `isAdmin` fuese falso al restaurar, `safeVisibility` la guardaría como PRIVATE con el slug del catálogo, y la común desaparecería hasta borrarla y reimportar. Hoy hay un solo admin.~~ **Resuelto en parte** en `agent/menores-recetas-compra`: «Deshacer» recrea la receta tal como era, también su visibilidad, sin `safeVisibility`; si quien deshace no puede publicar comunes, las reglas lo rechazan. La parte del segundo admin sigue abierta en la línea siguiente.
- ~~**Efecto visible (solo con un segundo admin):** si un admin deshace el borrado de una común, `ownerUid` y `createdBy` pasan a ser los suyos, y el importador la vería como «ficha de usuario». Arreglarlo exige cambiar permisos en `firestore.rules`.~~ **Resuelto** en `agent/menores-compra-y-deshacer-comunes`: «Deshacer» recrea la común tal como era (dueño, autor, fechas y `source`), con la regla `validRecipeRestore`.
- ~~**Efecto visible (admin, CLI del catálogo):** con `"quantity": null`, el validador dice «tiene que ser texto («null» entre comillas)» en vez de pedir que se quite la clave (`scripts/catalog/recipes.mjs`, `amountProblem`).~~ **Resuelto** en `agent/menores-recetas-compra`: pide quitar la clave.
- ~~**Efecto visible (sin confirmar):** en el recorrido de punta a punta del emulador, el Snackbar «Receta eliminada / Deshacer» no apareció nunca, ni en el Recetario ni al borrar un producto en Compra (`launchUndo`, `UndoSnackbar.kt`). Este código no lo toca la rama, y puede ser del emulador, que iba lento. Hay que comprobarlo en un móvil con la beta 20 y, si se reproduce, depurarlo.~~ **Descartado** el 2026-10-12: en un móvil con la beta 21, «Deshacer» sale y funciona al borrar un producto de la Compra. Era el emulador.
- **Interno:** `RecipeUnitsFixtureTest` no cubre singular + singular («1 diente» + «1 diente» = «2 dientes», que sale de `pluralOf`).
- **Interno:** ningún test de reglas fija que un `setDoc` de cliente no pise una receta existente, propia o ajena. Hoy lo impide `validRecipeUpdate` (`createdAt`, `ownerUid` y `createdBy` iguales).
- **Interno:** el importador de recetas lee la colección `recipes` entera, privadas incluidas. Bastaría `where("ownerUid", "==", CATALOG_ADMIN_UID)` más `getAll` de los slugs del catálogo (`import-recipes.mjs`).
- **Interno:** `runRecipeImport` copia línea a línea `runRoutineImport`. Falta un `runGlobalCatalogImport` en `import-core.mjs`.
- **Interno:** `RECIPE_UNITS` (`recipes.mjs`) duplica `tests/fixtures/recipe-units.json`, y un test comprueba que coinciden.
- **Interno:** `ingredientKey` se calcula dos o tres veces por ingrediente en `recipes.mjs` (validación y construcción).
- **Interno:** `isObject` e `isIntIn` están duplicados entre `recipes.mjs` y `routines.mjs`.

## 2026-10-12 — `agent/receta-pasos`

- ~~**Efecto visible:** el importador no parte en pasos las instrucciones con HTML escapado (`&lt;br&gt;`): queda «<br>» visible. Tampoco parte un array de textos o un `HowToStep` con `<br>` o `\n`, y sus pasos salen unidos (`RecipeImporter.kt`).~~ **Resuelto** en `agent/menores-recetas-compra`.
- ~~**Efecto visible:** si el texto único de instrucciones es HTML y lleva un `\n` del código fuente dentro de un `<p>`, el párrafo se parte en dos pasos. No se pierde texto (`RecipeImporter.kt`).~~ **Resuelto** en `agent/menores-recetas-compra`.
- ~~**Efecto visible:** el error de raciones o tiempo solo se marca en su campo si es el primer fallo de la validación. Sin título, o con los dos campos mal, alguno no se marca, aunque Guardar sí queda deshabilitado (`RecipeStepsEditor.kt`).~~ **Resuelto** en `agent/menores-recetas-compra` (también el paso demasiado largo).
- ~~**Efecto visible:** «Subir paso», «Bajar paso» y «Quitar paso» no dicen de qué paso son, y TalkBack los repite hasta 30 veces (`RecipeStepsEditor.kt`).~~ **Resuelto** en `agent/menores-recetas-compra`: «Subir el paso 2», «Bajar el paso 2», «Quitar el paso 2».
- ~~**Efecto visible:** el buscador del Recetario (`Recipe.matches`: subcadena, distingue tildes, mira también los ingredientes) no se comporta como el del selector de Comidas (`forPicker`: por palabras, sin tildes, solo título y categoría). Por ejemplo, «limon» no encuentra «Pollo al limón» en el Recetario.~~ **Resuelto** en `agent/menores-recetas-compra`: por palabras y sin tildes, mirando también los ingredientes.
- ~~**Efecto visible:** en `IngredientsEditor`, quitar un ingrediente con el teclado abierto deja el foco en esa posición, igual que les pasaba a los pasos antes de `dc15e36`.~~ **Resuelto** en `agent/menores-recetas-compra`.
- ~~**Efecto visible (plan C):** si el admin borra una receta común del catálogo y pulsa «Deshacer», se recrea con otro id y sin `source`, y el importador del plan C la duplicaría en «Comunes» (`RecipesViewModel.restoreRecipe`).~~ **Resuelto** en `agent/catalogo-recetas`: «Deshacer» recrea la receta con su mismo id (`RecipeRepository.restoreRecipe`) y el importador la omite como común manual.
- **Interno:** `toRecipeCount` usa 0 como marca de «no es un entero». Funciona porque 0 queda fuera de los dos rangos (`RecipeDraft.kt`).
- **Interno:** `recipeDetailsFailure` repite las comprobaciones de pasos, raciones y tiempo de `RecipesValidation.validate`, y `detailsMessage` existe solo para no superar `CyclomaticComplexMethod`.
- **Interno:** los pasos se limpian hasta 4 veces en cada guardado (`RecipeDraft.validate`, `RecipesViewModel`, el repositorio y `RecipeMetaParser`).
- **Interno:** `updateRecipe` (repositorio y ViewModel) tiene `steps`, `servings` y `totalMinutes` con valores por defecto. Una llamada futura que no los pase vaciaría la receta.
- **Interno:** en el recorrido de punta a punta hubo que sembrar a mano el admin de recetas (uid de `RecipeAdmins`) y una receta antigua. Con ese admin sembrado como miembro de `demo-home`, la app dio `PERMISSION_DENIED` en `spaces/{id}`: parece una siembra incompleta y no se investigó.

## 2026-10-12 — `agent/compra-sin-duplicados`

- ~~**Efecto visible:** plural heurístico de las unidades no básicas al sumar: «pack» → «packes», «nuez» → «nuezes», «limón» → «limónes»; y «1/2 cucharadas» × 2 → «1 cucharadas», porque no hay grafía singular que elegir (`ShoppingQuantity.kt`).~~ **Resuelto** en `agent/menores-recetas-compra`, con plural y singular aproximados del español (ver los menores nuevos de esa rama).
- ~~**Efecto visible:** «Sumar» y «volver a apuntar» escriben los valores que tenía el artículo al abrir el diálogo. Si otro miembro lo marca como comprado mientras tanto, queda comprado con la cantidad nueva, y se pisan sus cambios de notas, tienda o marca. Posible arreglo: usar `relistItem` también para «Sumar», o comprobar el estado dentro de la transacción (`IngredientReview.kt`, `ShoppingViewModel.kt`).~~ **Resuelto** en `agent/menores-recetas-compra`: la revisión y el editor escriben en una transacción que comprueba que el artículo sigue como se vio.
- ~~**Efecto visible:** si la lista aún no se ha cargado o su lectura falla, no se buscan repetidos y se duplica. Pasa en el editor de la Compra antes de `UiState.Content` y en el `runCatching { items().first() }.getOrDefault(emptyList())` de Comidas y Recetario.~~ **Resuelto** en `agent/menores-recetas-compra`: Comidas y Recetario leen la lista del servidor y, si no pueden, no abren la revisión; el editor no escribe con la lista sin cargar.
- ~~**Efecto visible:** si la suma pasa de 40 caracteres en el editor, el error «cantidad demasiado larga» sale sobre un campo que muestra una cantidad corta (`ShoppingViewModel.sumDuplicate`).~~ **Resuelto** en `agent/menores-recetas-compra`: el aviso lo explica y deshabilita «Sumar cantidad».
- ~~**Efecto visible:** `applyIngredientReview` no es atómica. Si una escritura falla a medias y el usuario vuelve a elegir «Sumar», esa cantidad se suma dos veces.~~ **Resuelto** en `agent/menores-recetas-compra`.
- ~~**Efecto visible:** «Sumar cantidad» en el editor descarta las notas, la tienda y la marca que se han tecleado. Lo fija la spec A4; queda por decidir si al menos se rellenan las que el artículo existente tiene vacías. **Decidido** el 2026-10-12 por el usuario: sí, «Sumar cantidad» rellena con lo tecleado las notas, la tienda y la marca que el artículo existente tenga vacías, sin pisar las que ya tiene.~~ **Aplicado** en `agent/menores-recetas-compra`.
- ~~**Efecto visible:** la fila «Ya comprado» de la revisión no tiene «Omitir». Si se vuelve a añadir la semana después de comprar, todo lo comprado vuelve a quedar pendiente. Es decisión de producto (spec A3, decisión 7). **Decidido** el 2026-10-12 por el usuario: la fila «Ya comprado» gana «Omitir», marcado por defecto como en «Ya en la lista»; volver a apuntar pasa a ser la opción que hay que elegir.~~ **Aplicado** en `agent/menores-recetas-compra`.
- ~~**Efecto visible:** el aviso «Ya en la lista» del editor parte «(cantidad: 5 / ud)» en dos líneas (visto en el emulador).~~ **Resuelto** en `agent/menores-recetas-compra`.
- ~~**Efecto visible:** si un ingrediente sin cantidad ya está pendiente, la revisión muestra «Ya en la lista: — · receta: —».~~ **Resuelto** en `agent/menores-recetas-compra`.
- **Interno:** huecos de test del Review Focus. El punto 1 solo prueba el predicado sobre un `ReviewRowState` sintético y no recorre una fila PENDING larga → `withInclude(true)`. El punto 3 no tiene test del ViewModel del editor con el mismo producto comprado y pendiente.
- **Interno:** `FirestoreShoppingRepository.MAX_QUANTITY_LENGTH = 40` no reutiliza `ShoppingQuantity.MAX_LENGTH`.
- **Interno:** `relistItem` recibe un `name` que valida pero no escribe.
- **Interno:** `findShoppingMatch` recalcula `shoppingMatchKey` de toda la lista en cada búsqueda: una vez por grupo de la revisión y otra por cada tecla del editor. Falta un índice clave → artículo.
- **Interno:** `seed:emulators` no crea el usuario de Authentication ni lo liga al espacio sembrado; en el recorrido de punta a punta hubo que crear las dos cosas a mano.

## 2026-10-11 — `agent/catalogo-rutinas`

- **Efecto visible:** en el selector de ejercicios (diálogo estrecho), el valor «Peso corporal» del filtro de tipo se corta en seco («Peso corp»), sin puntos suspensivos; en el menú se lee entero (visto en el emulador; el corte ya se aceptó en el plan de filtros, la elipsis no).
- **Interno:** `docs/DATA_MODEL.md` («Rutinas») dice que cualquier verificado lee `routines`; las PRIVATE solo las lee su dueño, y el admin solo borra las GLOBAL ajenas.
- **Interno:** a `docs/ROUTINE_CATALOG.md` le faltan avisos: borrar una común solo en la app no basta (la reimportación la recrea mientras siga en `routines.json`); guardarla en el editor sin cambios la congela para el importador; qué hacer si una rutina de usuario ocupa el mismo slug; las reglas se despliegan antes de importar.
- **Interno:** los tests de la tabla compartida (`RoutineExerciseFixtureTest`, «expande cada caso…» en `catalog-routines.test.mjs`) pasarían con el fixture vacío; falta comprobar los 4 casos y que coincida con `exercises.json`.
- **Interno:** `formatImportPlan` comprueba `metaPath == null` y `commitCatalogOps` `if (metaPath)` (divergen con `""`); `val visible = routines.value` en `routinesSection` es un alias de más.
- **Interno (ya pasaba en main):** `ProfileScreenTest` (androidTest) falla: usa `ComponentActivity` y `ProfileScreen` crea un `hiltViewModel`; necesita `HiltAndroidRule`/`HiltTestActivity` o no crear el ViewModel en el test.

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

