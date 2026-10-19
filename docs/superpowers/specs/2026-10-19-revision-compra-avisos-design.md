# Revisión de «Añadir a la compra»: avisos de despensa y lista, y «No añadir» en todos

Fecha: 2026-10-19. Diseño aprobado en el chat con el usuario.

## Intención

Al pulsar «Añadir a la compra» en una receta (Recetario) o en una comida (Comidas) se abre la revisión de ingredientes
(`IngredientReviewDialog`, en `app/src/main/java/com/dlunaunizar/bobitos/feature/common/IngredientReview.kt`). El usuario
quiere decidir ahí, de un vistazo, qué ingredientes añadir: ver si cada uno ya hay en la despensa, si ya está en la
lista y quitar los que no quiere, también los que son nuevos.

Hoy la revisión ya cruza los ingredientes con la lista y la despensa, pero tiene tres huecos que el usuario confirmó
(«sobre todo el último»):

1. **Solo sale un aviso por ingrediente.** La fila tiene un único `kind` con precedencia (pendiente → en la despensa →
   comprado → nueva) y una única pista: si un ingrediente está pendiente en la lista y además hay en la despensa, solo
   dice «Ya en la lista» y el de la despensa no aparece.
2. **El aviso es poco visible:** una línea de texto gris pequeña (`bodySmall`, `onSurfaceVariant`) bajo el nombre.
3. **Los ingredientes nuevos no se pueden quitar:** el selector «Omitir / Añadir» solo aparece en las filas que no son
   `NEW`; las nuevas se añaden siempre.

## Objetivos

- Cada ingrediente muestra **dos avisos independientes**, los dos a la vez si se dan:
  - **despensa:** «En la despensa»;
  - **lista:** «En la lista: 2» si el artículo pendiente tiene cantidad, «En la lista» si no la tiene, o «Ya
    comprado» si el artículo de la lista está comprado (tachado).
- Los avisos se ven bien: son etiquetas con icono y el color del módulo (ver «Dirección visual»).
- **Todos** los ingredientes, también los nuevos, tienen el selector de dos partes con la opción «No añadir».
- Vale igual en Recetario y en Comidas: la revisión es compartida y el cambio se hace una vez (decisión del usuario).

## No objetivos

- No cambia lo que se escribe en la lista: «Añadir» crea el artículo, «Sumar» cambia la cantidad del pendiente y
  «Volver a apuntar» reactiva el comprado, como ahora (`ingredientReviewWrites`).
- No cambia cómo se cruzan los ingredientes con la lista y la despensa (`buildIngredientReviewRows`,
  `findShoppingMatch`, `isInPantry`), ni la lectura del servidor en `RecipesViewModel.addToShopping` y en Comidas.
- No cambia el diálogo en sí (título, botones «Confirmar» y «Cancelar», scroll), ni la marca y la tienda.
- No hay botón para marcar o desmarcar todo, ni un contador en «Confirmar».

## Antiobjetivos

- Que la etiqueta de la despensa desaparezca cuando el ingrediente también está en la lista (el fallo de hoy).
- Que las etiquetas parezcan botones: no se pulsan y TalkBack no las anuncia como botones.
- Que un ingrediente nuevo no se pueda dejar fuera.

## Comportamiento

### Lo que sale marcado de entrada (decisión del usuario: «como ahora»)

- Nuevo (no está en la lista y no hay en la despensa): «Añadir».
- Hay en la despensa, o ya está en la lista (pendiente o comprado): «No añadir».

### La opción que no es «No añadir»

- Pendiente en la lista: «Sumar» (como ahora; la cantidad sale con la suma preparada).
- Comprado en la lista: «Volver a apuntar» (como ahora), haya o no en la despensa.
- En cualquier otro caso (nuevo, o solo en la despensa): «Añadir».

Es la regla de `includeLabel()` de hoy; lo único nuevo es que las filas nuevas también tienen el selector.

### El selector

- El selector de dos partes de hoy (`SkipOrIncludePicker`, un `SingleChoiceSegmentedButtonRow`), en todas las filas.
- La opción que hoy dice «Omitir» pasa a decir **«No añadir»**; TalkBack la lee como **«No añadir a la lista»** (el
  nombre accesible contiene el texto visible). El texto completo no va en el botón porque en el ancho del diálogo
  ocuparía tres líneas o más, y más con la letra al 130 %.
- Un texto del selector puede partirse en dos líneas en pantallas estrechas o con la letra grande (ya pasa hoy con
  «Volver a apuntar»): vale, mientras no se corte ni se solape (regla de PRODUCT.md). No hace falta forzar una sola
  línea (decisión del coordinador).
- Con «No añadir» se esconden la cantidad, la marca y la tienda de esa fila, como hoy con «Omitir». Al volver a
  «Añadir», la fila recupera lo que tenía (la cantidad y la marca y la tienda elegidas no se pierden al alternar).
- Si al confirmar todas las filas están en «No añadir», no se escribe nada y sale el aviso de hoy («No hace falta añadir nada», `NothingToAddToShopping`).

### La línea de la cantidad

Debajo de las etiquetas queda una línea de texto, como hoy, con lo que pide la receta, sin repetir el estado de la lista
ni de la despensa (eso ya lo dicen las etiquetas):
- con dos o más aportaciones del mismo ingrediente: «De varias comidas: 1 + 2» (texto de hoy);
- si no, con cantidad: «Receta: 3»;
- sin cantidad, no hay línea.

La cantidad que ya hay en la lista va en la etiqueta («En la lista: 2»), no en esta línea.

### Casos límite

- Si no se pudo leer la despensa, la revisión sale igual, sin etiqueta de despensa (como hoy).
- Un ingrediente no puede estar a la vez pendiente y comprado en la revisión: `findShoppingMatch` devuelve un solo
  artículo y manda el pendiente.
- Comprado y en la despensa a la vez: salen «En la despensa» y «Ya comprado», con «No añadir» marcado y «Volver a
  apuntar» como la otra opción.

## Dirección visual

Brief de `/impeccable shape`, confirmado por el usuario el 2026-10-19.

- **Trabajo y modo:** Operate. Quien la abre está en casa decidiendo la compra a partir de una receta o del plan de
  comidas, con el móvil en una mano. Tiene que saber de un vistazo qué hay ya y quitar lo que sobra con un toque.
- **Mundo visual:** el de la app, sin cambios. Material 3 con los tokens de `core/designsystem` (tema oscuro por
  defecto, colores de módulo en `ModuleColors.kt`, `Spacing`, Nunito). No hay `DESIGN.md`. Es un refinamiento: se
  conserva el diálogo y su estructura.
- **Orden de cada fila:** nombre (`titleSmall`) → etiquetas → línea de la cantidad → selector → cantidad, marca y
  tienda (solo con la opción que añade).
- **Etiquetas:** el `InfoChip` que ya existe (`core/designsystem/component/InfoChip.kt`; no se pulsa y TalkBack solo
  lee su texto), con icono decorativo:
  - despensa: icono `Icons.Rounded.Inventory2` (el de Despensa en la navegación) y los colores de `AppModule.PANTRY`;
  - lista: icono `Icons.Rounded.ShoppingCart` (el de Compra) y los colores de `AppModule.SHOPPING`.

  De cada módulo se usa el par contenedor/texto (`container` de fondo y `onContainer` para el texto y el icono), en
  los dos temas. El acento sobre su tinte del 12 % (lo que hace hoy `InfoChip`) no llega a 4,5:1 en el tema claro sobre
  la superficie del diálogo (`surfaceContainerHigh`): 4,49 en Despensa y 4,05 en Compra. El par contenedor/texto llega
  a 12,4 y 12,7 en claro y a 7,9 y 9,6 en oscuro. Por eso `InfoChip` pasa a aceptar el color de fondo (decisión del
  coordinador, medida el 2026-10-19).

  Van en una fila que salta de línea (`FlowRow`) si no caben, con la despensa primero.
- **Lo que no debe parecer:** botones o filtros pulsables; una alerta de error (no es un problema que esté en la
  despensa); ruido: si no hay avisos, no hay fila de etiquetas.
- **Pantallas afectadas:** la revisión de «Añadir a la compra», que abren Recetario (`RecipesScreen`) y Comidas
  (`MealsScreen`). No cambia nada más.

## Restricciones

- Android nativo, Kotlin, Compose y Material 3. Textos solo en `res/values/strings.xml`, en español y con tuteo.
- **Accesibilidad (PRODUCT.md):**
  - el texto de las etiquetas, a 4,5:1 o más contra su fondo real (el tinte del chip sobre la superficie del diálogo),
    en tema claro y oscuro;
  - el icono decorativo, sin descripción;
  - con la letra al 130 % nada se corta ni se solapa;
  - cada segmento del selector tiene un área táctil de al menos 48 × 48 dp (el `SegmentedButton` de Material 3 ya la da
    aunque se dibuje más bajo; no hace falta agrandar lo que se ve; decisión del coordinador).
- La lógica que decide etiquetas, opción marcada de entrada y textos son funciones puras y testeables, como hoy
  (`initialState`, `hint`, `includeLabel`). Ya hay tests unitarios (`IngredientReviewTest`,
  `IngredientReviewStringsTest`) y de UI (`androidTest/.../IngredientReviewUiTest`) que habrá que actualizar.

## Lo que queda al constructor

- El nombre y la forma de las funciones y textos nuevos (por ejemplo, una lista de avisos por fila en lugar del
  `hint` actual), y si `ReviewRowKind` se queda o se sustituye, mientras el comportamiento sea el descrito.
- Qué textos sin uso se borran de `strings.xml`.
- Cómo se da a TalkBack el nombre «No añadir a la lista» del segmento.
