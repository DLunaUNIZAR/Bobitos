# Evolución visual · Fase B (jerarquía, ruido y accesibilidad TalkBack) — diseño

Fecha: 2026-10-17. Programa: `docs/superpowers/specs/2026-10-17-evolucion-visual-design.md`. De ese documento valen aquí el «Por qué», las «Restricciones» y los «Anti-objetivos». La fase A está hecha y mergeada en `main` (ddfa827).

Contexto de producto: `PRODUCT.md`, escrito en esta misma sesión con `/impeccable init`. Fija **WCAG AA y TalkBack usable** como requisito permanente.

Maquetas de las tres decisiones que se tomaron viéndolas: `docs/superpowers/specs/2026-10-17-evolucion-visual-maquetas-fase-b.html`. Son ilustrativas: si difieren de este texto, manda el texto.

Rutas relativas a `app/src/main/java/com/dlunaunizar/bobitos/` salvo que se diga otra cosa.

## Por qué

La crítica del 2026-10-17 detectó tres grupos de problemas que la fase A no cubrió:
- **Estética y minimalismo (2/4):**
  - títulos y acciones principales duplicados;
  - metadatos repetidos en cada fila;
  - la tarjeta de comida vuelca todos los ingredientes;
  - la semana repite «Sin eventos» hasta cinco veces.
- **Consistencia (1/4):**
  - dos tiras de días distintas;
  - dos formas de borrar;
  - unas áreas con Deshacer y otras sin él.
- **Accesibilidad no visual:** para TalkBack hay controles sin rol, sin estado y sin nombre propio. Afecta a los chips de día, las celdas del calendario, la cabecera plegable y los 10 «Más opciones».

## Lo que decidió el usuario

- **Menores del backlog que entran** (sección «2026-10-17 — agent/evolucion-visual-fase-a»):
  - Calendario y Mi calendario sin `ErrorState`;
  - los emoji de «Mi día»;
  - los diálogos de Espacios.

  Quedan fuera:
  - «Calendario 0» en Hoy (fase C);
  - los dos menores «raros» de Deshacer y de `showError`.
- **Comidas:** solo el FAB. Fuera los tres «+ Añadir» de las franjas.
- **Regla de borrado:** lo que se puede deshacer se borra al momento, sin preguntar, y ofrece **Deshacer**, venga del menú ⋮ o de deslizar. Solo piden confirmación los borrados irreversibles.
- **Despensa:** no cambia. «Se acabó» conserva «A la compra» en su snackbar y no gana Deshacer.
- **Espacios:**
  - sin Deshacer;
  - sus acciones irreversibles siguen pidiendo confirmación;
  - «Revocar invitación», que hoy revoca al primer toque, **pasa a pedirla**;
  - no se tocan las reglas de Firestore.
- **Filtros de Tareas:** opción **C** de la maqueta. Todos los filtros van en chips con menú (Estado, Prioridad, Tipo y Responsable), también el estado.
- **Semana:** opción **A**. Los días vacíos seguidos se juntan en una sola línea atenuada, que se despliega al tocarla.
- **Detalle de receta:** opción **A**.
  - Una acción principal en botón relleno, más «Cerrar».
  - Las secundarias (Guardar como mía, Editar, Eliminar) van en un menú ⋮ junto al título.
  - Rutinas y Ejercicios siguen la misma regla.
- El usuario **aprobó en el chat** el reparto de pantallas y TalkBack y la regla de Deshacer que se describen abajo, incluidas las propuestas marcadas como mías.
- Confirmó también el brief de diseño de `/impeccable shape` («Dirección visual»).

## Dirección visual

Brief de `/impeccable shape`, confirmado por el usuario:

1. **Para quién y en qué modo.**
   - Miembros del hogar de la beta privada. Usan el móvil con una mano y con prisa, en casa, en el súper o en el gimnasio.
   - También quien usa TalkBack o letra al 130 %.
   - Modo **Operate**: cuentan más la lectura rápida y las costumbres de Android que la expresión.
2. **Resultado.**
   - Cada pantalla dice cada cosa una sola vez: un título (el de la barra superior), una acción principal (el FAB) y metadatos solo cuando informan.
   - Se consigue cuando una lista se recorre sin texto repetido y TalkBack lee en cada control su nombre, su rol y su estado reales.
3. **Dirección.**
   - Es un **refinamiento del mundo actual**: Material 3, teal, Nunito y oscuro por defecto.
   - La jerarquía sale de **quitar**, no de añadir estilos: ni colores ni tipografías nuevas, solo los roles de Material que ya existen.
   - Un acento fuerte por pantalla: el FAB, o el botón relleno en una hoja.
   - «Evolucionar» (color por módulo, listas en vez de losas, Hoy como agenda) queda para la fase C.
4. **Alcance y límites.**
   - **Pantallas:** las de esta spec.
   - **Intocable:** paleta, tipografía, barra de 5 pestañas y la estructura de tarjetas actual.
   - **Anti-objetivos:**
     - controles hechos a mano que imiten a Material;
     - acciones principales solo con icono;
     - emoji como icono;
     - perder información útil al quitar ruido, como quién lo añadió cuando sois varios.
5. **Estados y rangos.**
   - **Espacios de 1 a 10 miembros:** con 1 no sale «Añadido por».
   - **Comida:** de 0 a 30 ingredientes.
   - **Semana entera vacía:** una sola línea.
   - **Filtros sin resultados:** un estado propio con «Quitar filtros».
   - **Calendario:** carga, error con «Reintentar» y vacío.
   - **Sin conexión:** no hay FAB y los estados vacíos van sin botón.
6. **Interacción.**
   - **Filtros de Tareas:** chips con menú. El chip enseña el valor elegido.
   - **Cabecera de sección:** común, «Título · N», marcada como encabezado.
   - **Semana:** la línea plegada se despliega en su sitio y anuncia su estado.
   - **Hoja de detalle:** botón relleno principal más «Cerrar». El ⋮ junto al título lleva las secundarias.
   - **Tira de días:**
     - una sola, común;
     - el día elegido en `secondaryContainer`, para que el FAB siga siendo el único acento fuerte;
     - hoy, con contorno;
     - la fase C le pondrá el color del módulo.
   - **Tarjetas:** las de comida y nota abren su edición al tocarlas.
   - **Borrar:** el resultado sale en un snackbar con Deshacer.
7. **Restricciones.**
   - Componentes de Material 3.
   - Textos en `strings.xml`, con plurales.
   - Comprobado en claro y en oscuro.
   - Toques de 48 dp.
   - Letra al 130 % sin cortes.
   - Sin dependencias nuevas y sin cambios en las reglas de Firestore.
   - **Queda al implementador:** los iconos de «Mi día» (`Icons.Rounded`) y la redacción de las cadenas nuevas, dentro del tono.

## Restricciones

Las del programa, más estas:
- **Sin cambios en `firestore.rules`.** Si deshacer en un área exigiera cambiarlas, esa área se queda sin Deshacer: el plan lo anota como ruling y el menor va al backlog.
- **Tests:**
  - El comportamiento visible se prueba con tests JVM de ViewModel y de lógica pura.
  - La accesibilidad se prueba con tests Compose instrumentados que comprueban semántica: rol, `selected`, `stateDescription`, `contentDescription` y `heading`. Hoy casi no hay tests de semántica, así que esta fase crea la red.
  - Patrón existente: `createAndroidComposeRule<ComponentActivity>()`, como en `ScreenScaffoldTest` o `MemberCheckboxesUiTest`. El emulador está en inglés.
- **Puerta de cada tarea:** `./gradlew ktlintCheck detekt testDebugUnitTest assembleDebug lintDebug`, más los tests instrumentados de la propia tarea.
  - Antes, `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
  - `lintDebug` ya falla en `main` por `RestrictedApi currentBackStack` en `BobitosNavHost`. Ese error, y solo ese, se tolera.

## B1. Un título por pantalla

**Problema.** La barra del espacio ya dice «Casa Bobitos ▾ / Tareas». Aun así:
- **Tareas** repite «Tareas compartidas» (`feature/tasks/TasksScreen.kt:199`), con «3 de 5 tareas» debajo (`TasksVisibleCount`, :1091).
- **Compra** repite «Lista compartida» con «N productos pendientes» (`feature/shopping/ShoppingScreen.kt:206-221`).
- Las cabeceras de sección ya llevan el número: «Hoy · 2» y «Pendientes · 3».

**Decisión.**
- **Fuera** el título interno de Tareas y Compra, «3 de 5 tareas» y «N productos pendientes». El número queda solo en las cabeceras de sección.
- **«Plantillas»** (Tareas, solo con `canWrite`) pasa a ser la acción final de la primera cabecera de sección visible. Si la lista está vacía, va junto a los filtros (llamada mía).
- **Cabecera de sección común:** un componente nuevo en `core/designsystem/component`.
  - Lleva el título en `titleMedium` y, si hay número, « · N».
  - Admite una acción opcional al final.
  - Va marcado como encabezado (`heading()`).
  - Sustituye a `TaskSectionHeader`, `ShoppingSectionTitle` y las cabeceras ad hoc de Recetario y Rutinas, y la usan las pantallas que toca esta fase. La cabecera de categoría de la Despensa es de otro tipo: solo pasa a ser encabezado.

**Hecho cuando:**
- ni Tareas ni Compra pintan un título propio ni una línea de recuento suelta;
- un test de semántica comprueba que la cabecera común es encabezado y que lee «Título · N».

`TasksVisibleCountUiTest` se retira junto con el componente.

## B2. Un CTA por pantalla

**Problema.** El estado vacío repite la acción del FAB en tres pantallas:
- **Tareas:** «Nueva tarea» (`TasksScreen.kt:228-243`).
- **Despensa:** «Llenar la despensa» (`feature/pantry/PantryScreen.kt:148-156`).
- **Espacios:** «Crear espacio» y «Unirse con invitación» (`feature/spaces/SpacesScreen.kt:258-278`). Además ya hay un botón de unirse fijo arriba.

En Comidas, además del FAB «Nueva comida», hay tres «+ Añadir», uno por franja (`feature/meals/MealsScreen.kt:415-420`).

**Decisión.**
- **Estados vacíos sin botón** donde la pantalla tiene FAB. Tareas, Despensa y Espacios conservan el icono, el título y la descripción.
  - En Espacios, «Unirse con invitación» sigue solo en su sitio fijo de arriba.
- **Comidas:** fuera los tres «+ Añadir». Una franja vacía dice solo «Sin planificar».
  - El FAB sigue abriendo el editor con la franja que toca por la hora (`defaultMealSlot`), y en el editor se puede cambiar.
- **Tareas con filtros sin resultados:** «Ninguna tarea coincide con los filtros.» (`tasks_no_results`) gana la acción **«Quitar filtros»**.
  - Vuelve a los filtros por defecto: Pendientes, sin prioridad, sin tipo y sin responsable.
  - No es un CTA de alta.

**Hecho cuando:**
- ningún `EmptyState` con acción convive con un FAB que hace lo mismo;
- Comidas no tiene botones de alta fuera del FAB;
- un test del ViewModel o de la lógica de filtros demuestra que «Quitar filtros» vuelve a los filtros por defecto.

## B3. Metadatos que aportan

**Decisión.**
- **Compra:** «Añadido por …» y «Marcado por …» (`ShoppingScreen.kt:~545-552`) solo salen si el espacio tiene **más de un miembro**.
  - El dato está en `SpaceSummary.memberCount`, en el `NavHost` (`BobitosNavHost.kt:322`), y se pasa a la pantalla. Sin el dato, se muestran (llamada mía).
- **Recetario:** las filas no dicen «por …» en ninguna sección (`feature/recipes/RecipesScreen.kt:469`).
  - En «Mis recetas» son siempre del usuario, porque las reglas no dejan leer las privadas ajenas.
  - En «Recetas comunes» ya lo dice la sección.
  - El detalle también pierde la línea «por …» (B7).
- **Tarjeta de comida** (`MealCard`, `MealsScreen.kt:446-545`):
  - Sin la lista completa de ingredientes.
  - Debajo del nombre, **una línea de resumen**: «5 ingredientes · Ana, David». Si no hay participantes, «Sin asignar».
    - El número de ingredientes sale de la receta enlazada (`recipe.ingredients.size`), con `<plurals>`.
    - Sin receta o sin ingredientes, la línea solo dice quién come.
  - El cocinero y el «Cocinada» pasan de símbolos de texto (🍳, ✓) a iconos de Material con su texto. El diseño exacto (misma línea o la siguiente) queda al implementador, sin cortes al 130 %.
- **Tocar la tarjeta abre su edición** en Comidas y en Notas, como ya hacen Tareas y Deporte (llamada mía, aprobada).
  - Hoy `MealCard` y `NoteCard` (`feature/notes/NotesScreen.kt:203`) solo se editan desde el menú.
  - Sin `canWrite`, no se pulsan.

**Hecho cuando:**
- un test de lógica decide «mostrar autor» según el número de miembros;
- un test de la línea de resumen cubre: con receta e ingredientes, sin receta y sin participantes;
- un test de UI demuestra que tocar una comida y una nota abre su editor.

## B4. La semana sin ruido (opción A)

**Problema.** `WeekEventList` (`feature/calendar/CalendarScreen.kt:~430-475`) y `PersonalWeekEventList` (`feature/calendar/PersonalCalendarScreen.kt:~320-345`) pintan una cabecera por día, más «Sin eventos» en cada día vacío.

**Decisión.**
- **Días vacíos seguidos** se juntan en **una línea atenuada**: «mar 13 – vie 16 · Sin eventos». Un día vacío suelto dice «dom 18 · Sin eventos», y una semana entera vacía, «lun 12 – dom 18 · Sin eventos».
- **Hoy nunca se pliega** (llamada mía): si está vacío, sale con su cabecera completa y «Sin eventos», partiendo el grupo.
- **Al tocar la línea** se despliegan en su sitio los días del grupo, con su cabecera y «Sin eventos». Se vuelve a plegar desde la misma línea.
  - En el Calendario del espacio, las cabeceras de los días desplegados siguen llevando al día, como hoy. En Mi calendario no llevan a ningún sitio, también como hoy.
  - El estado plegado o desplegado se guarda mientras se está en esa semana. Al cambiar de semana, todo vuelve a plegarse.
- **TalkBack:**
  - la línea es un botón con descripción completa («del martes 13 al viernes 16 de octubre, sin eventos»);
  - su estado se anuncia como plegado o desplegado.
- **Formatos de fecha:**
  - Los nuevos (día corto «mar 13» y rango corto) van en `BobitosDates`, con locale español.
  - La descripción accesible usa el formato largo que ya existe (`BobitosDates.accessibleDate` o el que encaje).
- **La agrupación es una función pura y compartida**, con test JVM. Recibe la semana, los días con eventos y hoy, y devuelve días y grupos.

**Hecho cuando:**
- el test de la agrupación cubre:
  - semana con días vacíos al principio, en medio y al final;
  - toda vacía;
  - ninguna vacía;
  - hoy vacío en medio de un grupo;
- un test del formateador, con el locale por defecto en inglés, devuelve «mar 13» y el rango en español;
- un test de semántica comprueba el botón, la descripción y el estado plegado o desplegado.

## B5. Calendario que no finge estar vacío (menor del backlog)

**Problema.** `CalendarScreen.kt:147` y `PersonalCalendarScreen.kt:~85` toman `(state.events as? UiState.Content)?.value.orEmpty()`. Con un error o mientras carga, la rejilla, la semana y el día salen vacíos («Sin eventos», «0 eventos»).

**Decisión.**
- **Con `UiState.Error`:** el área de contenido (debajo de la cabecera del periodo y del selector Día/Semana/Mes) muestra `ErrorState` con el mensaje humano de `FailureKind` y **«Reintentar»**.
  - Los ViewModels de Calendario y Mi calendario ganan una acción de reintento que vuelve a suscribir los eventos.
- **Mientras llega la primera carga:** `LoadingState`, en vez de «Sin eventos» (llamada mía, coherente con A8).

**Hecho cuando:**
- tests de los dos ViewModels demuestran que un fallo deja `events` en `Error` y que reintentar vuelve a suscribir y puede llegar a `Content`;
- las dos pantallas pintan `ErrorState` con «Reintentar» ante `Error`.

## B6. Filtros de Tareas (opción C)

**Problema.** `TaskFilterBar` (`TasksScreen.kt:413-447`) pone en una sola fila desplazable cinco `FilterChip`: Pendientes, Completadas, Baja, Media y Alta. Detrás van Tipo y Responsable, que ya son chips con menú. «Alta» queda cortada por el borde.

**Decisión.**
- **Cuatro chips con menú**, en este orden: **Estado**, **Prioridad**, **Tipo** y **Responsable**. Siguen el patrón que ya usan Tipo y Responsable (`FilterChip` + `DropdownMenu`, `TasksScreen.kt:450-520`).
  - **Estado:** Pendientes, Completadas o Todas. Por defecto, Pendientes, como hoy.
  - **Prioridad:** Todas, Alta, Media o Baja, en ese orden, con la bandera teñida de la fase A.
  - **Sin filtro,** el chip dice su nombre («Estado», «Prioridad», «Tipo», «Responsable») y no está seleccionado.
  - **Con filtro,** dice el valor elegido («Pendientes», «Alta», el nombre del miembro…) y está seleccionado.
- **En cada menú**, la opción elegida lleva una marca visible y está seleccionada en semántica.
- **El modelo no cambia:** `TaskFilters(status, priority, type, assigneeId, unassignedOnly)`. Estado y prioridad ya son independientes.

- **La fila se desliza** (decisión del usuario, tras medirla). Con Nunito, los cuatro chips con sus nombres completos y su flecha ▾ ocupan unos 455 dp, y el Pixel 10 deja 379 dp.
  - Nombres completos y con flecha. Sin abreviar.
  - La fila se desplaza en horizontal, como las de Compra o Recetario.
  - El último chip asoma por el borde.

**Hecho cuando:**
- ninguna etiqueta de chip se corta con puntos suspensivos, ni al 100 % ni al 130 %;
- un test de UI elige un estado y una prioridad desde sus menús y comprueba la etiqueta y el estado seleccionado del chip.

## B7. Hoja de detalle con una acción principal (opción A)

**Problema.** `BobitosInfoSheet` (`core/designsystem/component/BobitosInfoSheet.kt:33`) pinta todas las acciones como `TextButton` iguales en un `FlowRow`, más «Cerrar».
- La usan el detalle de receta (`feature/recipes/RecipeSheets.kt:114-157`), el de rutina (`feature/routines/RoutinesScreen.kt:397`) y el de ejercicio (`feature/exercises/ExerciseDetailSheet.kt:37`).
- La receta, además, dice «Categoría: Saludables» como frase (:146) y «por Catálogo Bobitos» al final (:149-155).

**Decisión.**
- **`BobitosInfoSheet` separa:**
  - **una acción principal opcional:** botón relleno, abajo a la derecha, con «Cerrar» como `TextButton` a su izquierda;
  - **acciones secundarias:** un menú ⋮ en la fila del título, con «Más opciones de «<título>»» como descripción. Si no hay secundarias, no hay menú.
- **Reparto por hoja:**

  | Hoja | Principal | Secundarias (⋮) |
  |---|---|---|
  | Receta | «Añadir a la compra», si se puede | «Guardar como mía», «Editar», «Eliminar», según `recipeDetailActions` |
  | Receta sin «Añadir a la compra» | la primera disponible de «Guardar como mía» o «Editar» | el resto |
  | Rutina común | «Guardar como mía» | — |
  | Ejercicio editable | «Editar» | «Eliminar» |
  | Sin acciones | — | — (solo «Cerrar») |

- **«Eliminar» desde una hoja** sigue la regla de B10: cierra la hoja, borra y ofrece Deshacer, sin diálogo.
- **Receta:**
  - la categoría pasa a etiqueta (`InfoChip` en el tono de la fase A) en la línea de raciones y minutos (`RecipeSummary`);
  - desaparece la línea «por …».

**Hecho cuando:**
- un test de lógica decide el reparto principal/secundarias para cada caso de la tabla;
- un test de UI de `BobitosInfoSheet` comprueba:
  - el botón principal;
  - «Cerrar»;
  - el menú ⋮ con su nombre;
  - que sin secundarias no hay menú.

## B8. Perfil ordenado

**Problema.** `feature/auth/ProfileScreen.kt:106-177` es una columna centrada de controles sin secciones. «Eliminar mi cuenta» va justo debajo de «Créditos y licencias».

**Decisión.**
- **Todo alineado a la izquierda,** con cabeceras de sección (componente de B1), separadas por espacio y no por líneas:
  - **Cuenta:** avatar, correo y «Verificado», el nombre con «Guardar nombre» y «Cerrar sesión»;
  - **Apariencia:** el selector de tema;
  - **Recordatorios:** el interruptor y la antelación;
  - **Información:** «Política de privacidad» y «Créditos y licencias».
- **«Eliminar mi cuenta»:** al final, separado del resto por más espacio. Sigue en color de error y con su diálogo con contraseña.
- **Sin cambios de comportamiento.** Lo que hoy dice `AuthFeedback` sigue en su sitio, dentro de Cuenta.
- El indicador de «Guardar nombre» mientras guarda pasa a un tamaño acorde al botón. Hoy es un `CircularProgressIndicator` de 48 dp sin tamaño.

**Hecho cuando:**
- no queda `horizontalAlignment = CenterHorizontally` en la columna;
- las cuatro cabeceras son encabezados;
- «Eliminar mi cuenta» va después de «Información», en un bloque propio.

`ProfileScreenTest` ya falla en `main` por Hilt (backlog). Esta fase no lo arregla, y cualquier test nuevo de Perfil evita ese camino.

## B9. «Mi día» sin emoji (menor del backlog)

**Problema.** Las cadenas `home_my_day_tasks`, `_cooking`, `_eating` y `_empty` (`res/values/strings.xml:63-66`) empiezan por emoji (✅ 🍳 🍽 🎉), y TalkBack los lee dentro de la frase. La línea de eventos ya usa un icono de Material (`EventsLine`, `feature/spaces/SpaceHomeSections.kt`).

**Decisión.**
- Las cuatro cadenas pierden el emoji.
- Cada línea de `MyDayCard` va como la de eventos: un icono de Material `Icons.Rounded` decorativo, con `contentDescription = null`, y el texto.
- El texto se entiende sin el icono. La redacción queda al implementador.

**Hecho cuando:**
- no queda ningún emoji en las cadenas `home_my_day_*` ni en `meals_cook_value`;
- un test de recursos lo comprueba.

## B10. Borrar con Deshacer, sin preguntar

**Regla** (decisión del usuario). Lo que se puede deshacer:
- se borra **al momento**, sin diálogo;
- muestra en el snackbar del `Scaffold` el aviso de borrado con **«Deshacer»** (`launchUndo`, `core/designsystem/component/UndoSnackbar.kt`);
- vale igual venga del menú ⋮, de deslizar o de una hoja de detalle.

Si Deshacer ya no puede devolver el elemento, lo avisa con un snackbar, como en la fase A.

**Áreas.**

| Área | Hoy | Después |
|---|---|---|
| Tareas, Compra | deslizar sin diálogo; el menú con diálogo; ambos con Deshacer | **sin diálogo** en el menú: un solo camino |
| Comidas, Deporte, Calendario, Recetario | diálogo y luego Deshacer | **sin diálogo** |
| Mi calendario | diálogo, **sin** Deshacer | sin diálogo, **con Deshacer**, como el Calendario del espacio |
| Notas, Rutinas, Ejercicios | diálogo, sin Deshacer (aviso «… eliminada/o») | sin diálogo, **con Deshacer** (`restore*` nuevo en su ViewModel y repositorio) |
| Ingredientes y marcas personales | diálogo en el detalle, sin Deshacer; el detalle se cierra | sin diálogo; el detalle se cierra y el snackbar con **Deshacer** sale en la lista |

- **Deshacer devuelve el elemento tal como era:**
  - **Su mismo id,** cuando otros documentos lo referencian por id: rutinas a ejercicios, comidas a recetas, recetas o despensa a ingredientes. Así esas referencias siguen funcionando. El implementador comprueba en cada área cómo se referencia.
  - **Su estado.** Una comida vuelve cocinada si lo estaba, y una actividad, hecha. Hoy `restoreMeal` y `restoreActivity` no lo devuelven. Si las reglas obligan a crear sin ese estado, se crea y después se actualiza.
  - **Lo que el borrado se llevó con él.** Un ingrediente personal vuelve con sus marcas y su entrada de `ingredientPrefs`.
- **Si no se puede deshacer, se pregunta.** Un borrado que no se puede devolver tal como era sin cambiar las reglas no se puede deshacer (Restricciones). Conserva su diálogo de confirmación, no lleva Deshacer y el plan lo anota como ruling. Por ejemplo:
  - **Lo que borra el administrador del catálogo común:** rutinas, ejercicios e ingredientes comunes, y ejercicios de otros usuarios. Las reglas de alta prohíben `source` (los ejercicios, también `image`) y obligan a que el autor sea quien deshace. Volverían sin la atribución de wger, sin imagen, y el importador ya no los actualizaría (`skippedAdminManual`). Recetas sí tiene su regla propia (`validRecipeRestore`) y no entra en esta excepción.
  - **Un área que no permita fijar el id.**
- **Lo que haga el implementador:** decidir cuál es el caso en cada área y dejarlo en un test (llamada mía, derivada de la regla del usuario).
- **Sin cambios:**
  - **Despensa:** «Se acabó» con «A la compra».
  - **Altas en lote**.
  - **«Limpiar comprados»:** conserva su diálogo, porque borra muchos a la vez y no se deshace.
  - **Espacios:** borrar el espacio, salir, quitar a un miembro, transferir la propiedad.
  - **Perfil:** eliminar la cuenta.
- **«Revocar invitación»** (`feature/spaces/SpaceSettingsScreen.kt:390`) pasa a pedir confirmación con `BobitosDialog` destructivo.

**Hecho cuando:**
- tests de ViewModel de Notas, Rutinas, Ejercicios, Ingredientes (ingrediente y marca) y Mi calendario demuestran dos cosas: borrar emite el aviso con Deshacer, y Deshacer devuelve el elemento tal como era (id, estado y lo que se llevó el borrado);
- hay tests de que Comidas y Deporte devuelven «Cocinada» y «Hecha»;
- un test demuestra que un borrado del catálogo común sigue pidiendo confirmación;
- en las áreas de la tabla no queda diálogo de confirmación de borrado, salvo en los casos que no se pueden deshacer;
- un test comprueba que revocar pide confirmación.

## B11. Diálogos de Espacios como el resto de formularios (menor del backlog)

**Problema.** `SpaceNameDialog` (crear y renombrar) e `InvitationCodeDialog` (`feature/spaces/SpacesScreen.kt:283-430`) muestran el error que les llega del ViewModel (`showValidationError`, `SpacesViewModel.kt:167`). Ese error tiene dos fallos:
- no se borra al corregir el campo, solo al volver a pulsar;
- no lleva el foco al campo.

**Decisión.** Siguen la regla A5 de la fase A, con `FormValidationState` (`core/designsystem/component/FormValidation.kt`):
- se abren sin errores;
- al pulsar con datos no válidos no se envía, el error sale bajo el campo y el foco va al campo;
- desde ese intento, el error se recalcula al escribir y desaparece al corregirlo.

La validación del campo es la que ya existe: `SpaceValidation.validateName` e `InvitationCode.normalize`.

Los errores del servidor siguen dentro del diálogo, con texto humano y `liveRegion`, y se quitan al editar el campo:
- los que dependen del código (inválido, caducado, revocado o ya usado) van bajo el campo del código;
- los demás (sin permiso, sin conexión, espacio lleno) van como aviso del diálogo, no del campo.

**Hecho cuando** los tests (de ViewModel o de UI) demuestran:
- que no hay error antes del primer intento;
- que sí lo hay tras intentarlo;
- que desaparece al corregir;
- que el foco va al campo.

## B12. TalkBack: nombres, roles y estados

- **Tira de días común.** `DayChip` de Comidas (`MealsScreen.kt:373-386`) y de Deporte (`feature/sport/SportScreen.kt:296-316`) son dos copias privadas distintas. Pasan a **un componente** en `core/designsystem/component`, con el `WeekSelector` que comparten.
  - Grupo seleccionable (`selectableGroup`). Cada día es `selectable` con rol de pestaña o de opción.
  - Descripción completa: «lunes, 12 de octubre», más «hoy» si lo es. La inicial y el número no se leen por separado.
  - Aspecto: el día elegido en `secondaryContainer`/`onSecondaryContainer` y hoy con contorno.
  - Tamaño: **48 dp de alto**, y de ancho lo que dé la fila (unos 37 dp en el Pixel 10, por encima del mínimo de 24 dp de WCAG AA). Las flechas de semana siguen en la misma fila, así que la cabecera de Comidas y de Deporte no cambia de estructura (llamada mía).
  - La fase C le pondrá el color de cada módulo, así que los colores entran como parámetro con este valor por defecto.
- **Celdas del mes** (`DayCell`, `CalendarScreen.kt:~432-505`):
  - rol de botón, `selected` si es el día elegido y «hoy» en la descripción;
  - los días de otro mes siguen siendo pulsables, como hoy, y su descripción dice el mes.
- **Horas de la vista Día** (`HourRow`, `CalendarScreen.kt:~560`):
  - dejan de ser clicables sin efecto (hoy `onClick = {}`);
  - mantener pulsado sigue creando un evento a esa hora;
  - con `canWrite`, TalkBack tiene esa acción con nombre («Crear evento a las 10:00»).
- **Cabecera plegable de Completadas** (`TasksScreen.kt:381-410`):
  - anuncia si está desplegada o plegada (`stateDescription`), con rol de botón;
  - el icono pasa a decorativo. Hoy su texto fijo es «Mostrar u ocultar completadas».
- **«Más opciones» con nombre** en los 10 sitios (`TasksScreen.kt:592`, `ShoppingScreen.kt:569`, `CalendarScreen.kt:703`, `SportScreen.kt:383`, `RoutinesScreen.kt:313`, `NotesScreen.kt:233`, `ExercisesScreen.kt:310`, `MealsScreen.kt:174` y `:502`) y en el ⋮ nuevo de B7.
  - Formato: «Más opciones de «<nombre>»», con el nombre del elemento.
  - En el menú del día de Comidas, el nombre es el día: «Más opciones de «lunes, 12 de octubre»».
- **`Card(onClick)` en vez de `Card(Modifier.clickable)`** en las cinco tarjetas:
  - `PersonalCalendarScreen.kt:334`, `CalendarScreen.kt:662`, `RoutinesScreen.kt:283`, `RecipesScreen.kt:455` e `IngredientsScreen.kt:296`;
  - y en las que B3 hace pulsables.
  - Si una tarjeta no se puede pulsar (sin `canWrite`), sigue siendo una `Card` normal, no una `Card` desactivada: `enabled = false` cambiaría su color.

**Hecho cuando** tests de semántica instrumentados comprueban:
- la tira de días: rol, `selected` y descripción con el día completo y «hoy»;
- una celda del mes: rol, `selected` y «hoy»;
- `HourRow`: sin clic vacío y con la acción con nombre si `canWrite`;
- la cabecera plegable y su estado;
- el nombre de «Más opciones» en al menos una tarjeta por componente de tarjeta;
- que las tarjetas pulsables exponen clic y las no pulsables no.

## Fuera del alcance

- **Fase C:** color por módulo (FAB, selección, chips), listas en vez de losas, Hoy como agenda (también «Calendario 0»), tipografía y movimiento.
- **Fase D:** adaptividad, rendimiento y tokens.
- **Backlog:** los dos menores «raros» de la fase A, el de Deshacer con cambios de otros miembros y el de `showError`/`isSaving`.
- **Reglas de Firestore:** ningún cambio.

## Lo que queda al implementador

- Nombres y ubicación de los componentes nuevos (cabecera de sección, tira de días, línea plegada de la semana), siguiendo `core/designsystem/component`.
- Los iconos `Icons.Rounded` de «Mi día», del cocinero y de «Cocinada», y la redacción de las cadenas nuevas: tuteo, punto final en frases completas, plurales.
- Si el cocinero va en la línea de resumen de la comida o en la siguiente, siempre que no se corte al 130 %.
- El rol exacto de cada día de la tira (pestaña u opción), siempre que TalkBack anuncie el día y si está elegido.

## Llamadas mías (no del usuario)

Se aprobaron en el chat con la descripción, salvo las tres últimas, que salieron (o se ampliaron) de la revisión del documento:
- **«Plantillas»** junto a la primera cabecera de sección.
- **«Quitar filtros»** en el estado de Tareas sin resultados.
- **Mostrar autor** cuando no se conoce el número de miembros.
- **Sin «por …»** en el detalle de receta.
- **Comida y nota** abren su edición al tocarlas.
- **Hoy nunca se pliega** en la semana.
- **`LoadingState`** en Calendario mientras llega la primera carga.
- **Perfil:** sus cuatro secciones y el indicador de guardado a escala.
- **Sin diálogo de borrado** también en Comidas, Deporte, Calendario y Recetario.
- **Colores de la tira de días como parámetro**, pensando en la fase C.
- **Deshacer devuelve el elemento tal como era:** mismo id, «Cocinada» o «Hecha», y las marcas y preferencias del ingrediente.
- **Lo del catálogo común** que borra el administrador sigue pidiendo confirmación y no lleva Deshacer, porque las reglas no dejan devolverlo entero. Esta llamada salió de la revisión del documento y no se ha comentado en el chat.
- **Tira de días:** 48 dp de alto y el ancho que dé la fila, sin sacar las flechas de semana. También salió de la revisión del documento.
