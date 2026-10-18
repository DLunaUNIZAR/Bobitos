# Evolución visual · Fase C (identidad) — diseño

Fecha: 2026-10-18. Programa: `docs/superpowers/specs/2026-10-17-evolucion-visual-design.md`. De ese documento valen aquí el «Por qué», las «Restricciones» y los «Anti-objetivos». La fase B está hecha y mergeada en `main` (78a789c); su spec es `docs/superpowers/specs/2026-10-17-evolucion-visual-fase-b-design.md`.

Contexto de producto: `PRODUCT.md`. Fija **WCAG AA y TalkBack usable** como requisito permanente.

Maquetas de las decisiones, una por fichero y cada una con la opción elegida anotada al principio: `docs/superpowers/specs/2026-10-18-evolucion-visual-fase-c-maquetas/`. Son ilustrativas: si difieren de este texto, manda el texto.

Rutas relativas a `app/src/main/java/com/dlunaunizar/bobitos/` salvo que se diga otra cosa.

## Por qué

La crítica del 2026-10-17 dijo que la app parece «una app de ejemplo de Material 3 con teal».
- El color no tiene sistema: `ModuleColors` (`accent`, `container` y `onContainer`, en `core/designsystem/theme/ModuleColors.kt`) casi no se usa fuera de la barra inferior.
- Los catálogos largos son losas idénticas: Recetario tiene unas 160, Ingredientes 367, Ejercicios 214 y Rutinas 29.
- Hoy son cuatro contadores con números grandes, casi siempre a 0. Uno de ellos es «Calendario 0», que sale aunque haya eventos hoy.
- La jerarquía tipográfica está al revés: el nombre del espacio es el título grande y la pantalla en la que estás va en letra pequeña.

La fase B quitó ruido sin tocar el aspecto general. Esta fase le da identidad.

## Lo que decidió el usuario

Todo con maquetas, en el chat del 2026-10-18:
- **Hoy es una agenda de toda la casa**, no lo tuyo. Opción **A**: tareas arriba y una agenda ordenada por hora.
- **El color de Hoy** es la opción **3**: cada fila se tiñe del color de su módulo. El usuario pidió «algo más de color» que la propuesta inicial.
- **Color dentro de cada módulo:** opción **2**. El módulo tiñe su cabecera, y el FAB, la selección y los enlaces van en su color.
- **Paleta, Más e inicio:** aprobados tal como se mostraron («nada, está bien»).
  - Notas pasa a ámbar y Despensa a verde.
  - Los catálogos heredan el color de su módulo.
  - El Calendario conserva el teal, que también es el de la marca.
  - Más pasa a lista, con «Del espacio» y «Catálogos».
  - El inicio lista tus espacios.
  - Los catálogos se abren solo desde «Catálogos» y salen de las cabeceras de Comidas y Deporte.
  - Deporte cambia de icono.
- **Catálogos:** opción **A**, agrupados por categoría con cabeceras fijas.
- **Filtros:** un solo tipo de filtro en toda la app, el desplegable con su nombre siempre a la vista, como el de Ejercicios. Va en los catálogos, en Compra y en Tareas, aunque Tareas ocupe dos filas.
- **Las cuatro decisiones de la fase B que cuestionó la auditoría de diseño:**
  - **Filtros de Tareas en dos toques:** se aceptan, con desplegables que dicen siempre qué filtran.
  - **Comidas sin «Añadir» por franja:** opción **b**. La franja vacía se puede tocar para añadir en ella.
  - **«Plantillas»:** opción **a**, un icono fijo en la barra superior de Tareas.
  - **Recetas sin autor:** se quedan sin autor (propuesta mía, sin objeción). Solo se ven las recetas propias y las del catálogo común (`firestore.rules`, `match /recipes`), así que «por …» no informaría de nada.
- **Tipografía:** el título grande es la pantalla y el espacio pasa a un selector pequeño encima. Al desplazar, el título se encoge. La escala tiene cuatro tamaños.
- **Movimiento:** la versión «con más vida» de la maqueta 8. El usuario la pidió con «algo más de vida» y respondió «sí, mucho mejor».
- **Deshacer al completar** una tarea, en Hoy y en Tareas, también si se repite: vuelve a sus fechas anteriores (opción **a**, tras la revisión de la spec). Ver C6.
- **Menores del backlog que entran:** los nueve de la sección «2026-10-17 — `agent/evolucion-visual-fase-b`» que caen en estas pantallas (C11 y C12), más «Calendario 0» y las escuchas de Hoy de la sección de la fase A (C6).
- **Brief de diseño** de `/impeccable shape`: confirmado («Dirección visual»).

## Dirección visual

Brief de `/impeccable shape`, confirmado por el usuario:

1. **Para quién y en qué modo.**
   - Los miembros del hogar de la beta. Usan el móvil con una mano y con prisa, en casa, en el súper o en el gimnasio.
   - También quien usa TalkBack o la letra al 130 %.
   - Modo **Operate**, pero esta fase sube la expresión: el color y el movimiento dan identidad, sin quitar velocidad.
2. **Resultado.**
   - Sabes en qué módulo estás antes de leer nada.
   - Hoy te dice qué toca a toda la casa sin abrir otra pestaña.
   - Los catálogos largos se pueden recorrer, y hay un solo tipo de filtro.
   - Marcar algo da gusto.
3. **Dirección.**
   - Es una **evolución del mundo actual**: Material 3, Nunito, el teal de la marca y el oscuro por defecto.
   - **Cada módulo es dueño de su color:** su cabecera va teñida, y el FAB, la selección y los enlaces van en ese color.
   - **El tinte de una fila dice de qué tipo es.** En Hoy, de qué módulo; dentro de un módulo, de qué categoría.
   - **Referencia visual:** la app actual y las maquetas aprobadas.
4. **Alcance y límites.**
   - **Pantallas:** Hoy, Calendario, Tareas, Compra, Más, Comidas, Deporte, Notas, Despensa, los cuatro catálogos, el inicio y Mi calendario.
   - **Intocable:** el oscuro por defecto, los componentes de Material y la barra de 5 pestañas.
   - **Anti-objetivos:**
     - un arcoíris sin significado;
     - baldosas pastel en claro;
     - controles que imiten a Material;
     - emoji como icono;
     - movimiento que retrase una acción o ignore «Quitar animaciones»;
     - perder quién hizo qué.
5. **Estados y rangos.**
   - **Hoy:**
     - de 0 a muchas tareas, vencidas incluidas;
     - de 0 a 3 franjas de comida;
     - eventos de todo el día y con hora;
     - deporte;
     - el día vacío, la carga, el error y sin conexión, donde no se puede marcar.
   - **Catálogos:** de 29 a 367 elementos, la búsqueda y el filtro sin resultados.
   - **Inicio:** de 0 a N espacios.
   - Todo con la letra al 130 %.
6. **Interacción.** La de las secciones C1 a C12.
7. **Restricciones.**
   - Componentes de Material 3.
   - Textos en `strings.xml`, con plurales.
   - WCAG AA en claro y en oscuro, también sobre las filas teñidas.
   - Toques de 48 dp.
   - Sin dependencias nuevas ni cambios en las reglas de Firestore.
   - Las miniaturas no decodifican la imagen a tamaño completo.

## Restricciones

Las del programa, más estas:
- **Sin cambios en `firestore.rules`** ni en el modelo de datos de Firestore.
- **Sin dependencias nuevas.** El movimiento usa las APIs de Compose y Material que ya están: `spring`, `animateItem`, `AnimatedContent`, las transiciones de Navigation Compose y `LocalHapticFeedback`.
- **Tests:**
  - El comportamiento se prueba con tests JVM de lógica pura y de ViewModel.
  - El contraste se prueba con tests JVM, ampliando `ModuleColorsTest` y `CategoryContrastTest` (`app/src/test/java/com/dlunaunizar/bobitos/core/designsystem/theme/`).
  - La semántica (rol, estado, encabezado, descripción) se prueba con tests Compose instrumentados, con el patrón `createAndroidComposeRule<ComponentActivity>()`. El emulador está en inglés.
  - Una tarea que solo cambia el aspecto no lleva test RED: su comprobación es la suite y el lint en verde, y el diff dentro de sus ficheros.
- **Puerta de cada tarea:** `./gradlew ktlintCheck detekt testDebugUnitTest assembleDebug lintDebug`, más los tests instrumentados de la propia tarea.
  - Antes, `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
  - `lintDebug` ya falla en `main` por `RestrictedApi currentBackStack` en `BobitosNavHost`. Ese error, y solo ese, se tolera.
  - Si cambian firmas que están en `config/detekt/baseline.xml`, se regenera con `./gradlew detektBaseline`.
- **Verificación visual** al terminar la fase: capturas en el emulador Pixel 10 de las pantallas afectadas, en oscuro, en claro y con la letra al 130 %, como en la fase A.

## C1. Paleta por módulo

**Hoy.** `AppModule` (`core/designsystem/theme/ModuleColors.kt`) tiene cinco módulos: Compra, Tareas, Calendario, Comidas y Deporte. Notas y Despensa no tienen color.

**Decisión.**
- **Siete módulos:** se añaden **Notas** (ámbar) y **Despensa** (verde), con variante clara y oscura, como los demás.
  - Valores orientativos, los de la maqueta 4:

    | Módulo | Acento oscuro | Contenedor oscuro | Acento claro | Contenedor claro |
    |---|---|---|---|---|
    | Notas | `#E8C35A` | `#4D3B00` | `#8A6A00` | `#FFE9A8` |
    | Despensa | `#8FD18A` | `#1F4D1C` | `#2E7D32` | `#CDEFC8` |

  - Cada uno lleva además su `onContainer`.
  - El implementador puede ajustarlos para pasar el test de contraste, siempre que sigan siendo ámbar y verde y se distingan del teal y del naranja de Compra.
- **Los acentos claros que ya existen se pueden oscurecer** dentro de su misma familia de tono para pasar el test (llamada mía). Hoy, como texto sobre el fondo claro, Compra (`#C05621`) da 4,38:1 y Calendario (`#00897B`) 4,14:1, y sobre su tinte dan menos. El Calendario puede pasar al teal oscuro de la marca (`#0F766E`). El usuario aprobó los tonos, no los valores exactos, como ya se hizo con las categorías en la fase A.
- **Herencia:**
  - Recetario e Ingredientes usan el color de Comidas.
  - Ejercicios y Rutinas, el de Deporte.
  - Mi calendario, el de Calendario.
- **El Calendario conserva el teal**, que también es el de la marca. Ya no choca, porque Hoy no lleva franja de marca (C6).
- **Pantallas globales** (inicio, Tus espacios, Perfil, Ajustes del espacio y acceso): cabecera neutra, sin tinte, y el teal de la marca en sus acentos (llamada mía).
- **Un rol más:** si el acento se usa como relleno con texto encima (por ejemplo, una casilla marcada o un botón relleno, ver C3), cada módulo necesita también su color «sobre el acento». El nombre lo elige el implementador.
- **Tinte de fila y de cabecera:** el acento del módulo mezclado con la superficie, como hace ya `categoryCardColors` (`core/designsystem/theme/CategoryTint.kt`).
  - Orientativo: alrededor del 16 % en oscuro y del 12 % en claro.
  - Es el mismo tinte en las filas de Hoy, en la cabecera de módulo y en las filas de Más.

**Hecho cuando** un test JVM recorre los siete módulos en los dos temas y comprueba:
- `onContainer` sobre `container`: al menos 4,5:1. Es el texto del FAB, del día elegido y del filtro elegido.
- El acento sobre el fondo y sobre su propio tinte: al menos 4,5:1, porque se usa como texto de enlace.
- El acento sobre el fondo, como contorno de «hoy» y como icono: al menos 3:1.
- `onSurface` y `onSurfaceVariant` sobre el tinte de fila y de cabecera: al menos 4,5:1.
- Lo que va sobre el acento usado como relleno: al menos 4,5:1.

## C2. Cabecera de módulo: título grande, franja teñida y selector de espacio

**Hoy.** `WorkspaceScaffold` (`core/navigation/WorkspaceScaffold.kt`) pinta «Casa Bobitos ▾» como título y el módulo en letra pequeña debajo (`SpaceTitle`). `ScreenScaffold` (`core/designsystem/component/ScreenScaffold.kt`) pinta una `TopAppBar` normal con flecha atrás.

**Decisión** (maquetas 3 y 7):
- **El título grande es la pantalla:** «Tareas», «Compra», «Comidas», «Recetario»…
  - En las pestañas del espacio, encima del título va un **selector pequeño con el nombre del espacio** («Casa Bobitos ▾»). Abre el cambio de espacio, como hoy, y para TalkBack es un botón con nombre: «Cambiar de espacio, Casa Bobitos».
  - Las acciones (ajustes y perfil, y «Plantillas» en Tareas) siguen arriba a la derecha.
- **Al desplazar la lista**, el título grande se encoge a una barra de una línea con el nombre de la pantalla. Usa el comportamiento de desplazamiento de las barras de Material 3 (barra mediana o grande que se encoge). La barra encogida conserva el tinte.
- **Franja teñida** con el tinte del módulo (C1).
  - Abarca la barra superior y, si la pantalla los tiene, los filtros o la tira de días, con las esquinas inferiores redondeadas, como en la maqueta 3.
  - **Los filtros y la búsqueda se desplazan con la lista** y dejan sitio a las filas (llamada mía).
  - **La tira de días de Comidas y Deporte se queda fija** bajo la barra, porque es la navegación de la pantalla, no un filtro (llamada mía).
- **Quién la lleva:**
  - todas las pantallas de módulo: las pestañas Calendario, Tareas y Compra, y Comidas y Deporte (dentro de `WorkspaceScaffold`);
  - Notas, Despensa, los cuatro catálogos y Mi calendario (dentro de `ScreenScaffold`, con flecha atrás).
- **Sin franja, con título grande y cabecera neutra:**
  - Hoy, cuyo título grande es la fecha (C6);
  - Más;
  - las pantallas globales (C1).
- **«Plantillas» en Tareas** (decisión del usuario, opción a):
  - es un icono fijo en las acciones de la barra superior, solo con `canWrite`, con nombre para TalkBack («Plantillas de tareas»);
  - deja de ir en la primera cabecera de sección y en la barra de filtros (`TasksScreen.kt`, `templatesAction`).

**Hecho cuando:**
- un test de UI comprueba que en una pestaña del espacio el título es la pantalla y que el selector del espacio es un botón con su nombre;
- un test de UI comprueba que «Plantillas» está en la barra superior con `canWrite` y no está sin él.

## C3. Acentos del módulo

**Decisión** (maqueta 3):
- **El color del módulo entra como tema** (llamada mía).
  - Cada pantalla de módulo, y las hojas y diálogos que abre, se envuelven en un tema que sustituye los roles de acento de Material (`primary`, `primaryContainer`, `secondaryContainer` y sus «on») por los del módulo.
  - Así lo heredan los componentes de Material sin pasar colores a mano: el FAB, los botones rellenos de las hojas, las casillas, los interruptores, el selector Día/Semana/Mes y los indicadores de progreso.
  - La barra inferior (que ya usa el color de la pestaña, fase A) y las pantallas globales no cambian.
  - El mapeo exacto de roles queda al implementador, siempre que se cumpla el test de C1.
- **Lo que pasa a llevar el color del módulo:**
  - el **FAB**;
  - el **día elegido** de la tira de días (`WeekDayStrip`, que ya recibe `WeekDayStripColors`) y de la rejilla del mes (`DayCell`);
  - el **contorno de «hoy»** en el acento, con 3:1 contra su fondo (C11);
  - el **filtro con valor elegido** (C4);
  - el interruptor **«Con lo que hay»** del Recetario;
  - las **casillas marcadas**;
  - los **enlaces y botones de texto** («Quitar filtros», «Ver completadas», «Gestionar»);
  - el **botón principal de las hojas** abiertas desde el módulo.
- **Un acento fuerte por pantalla:** el FAB, o el botón relleno en una hoja. Lo demás usa el contenedor o el acento como texto.

**Hecho cuando:**
- un test de UI comprueba, en al menos dos módulos, que el FAB usa el contenedor del módulo y que la tira de días usa sus colores;
- un test de UI comprueba que una pantalla global sigue con los colores de la marca.

## C4. Filtros desplegables en toda la app

**Hoy.**
- **Ejercicios** (y su selector, `ExercisePicker`) usa desplegables con etiqueta. El `FilterDropdown` privado de `feature/exercises/ExerciseFilterMenus.kt` es un `ExposedDropdownMenuBox` con `OutlinedTextField`.
- **Tareas** usa chips con menú: Estado, Prioridad, Tipo y Responsable (`TasksScreen.kt`).
- **El Recetario** usa una fila de chips de categoría, más «Con lo que hay» (`RecipesScreen.kt`).
- **Compra** usa una fila de chips de supermercado (`ShoppingScreen.kt`, alrededor de `:856`).
- **El Calendario y Mi calendario** usan chips de miembros, más «Todos» y «Ninguno».
- **Ingredientes** solo tiene búsqueda.

**Decisión** (decisión del usuario; maqueta 6):
- **Un componente común** de desplegable con etiqueta, en `core/designsystem/component`, que sale del de Ejercicios.
  - **La etiqueta siempre se ve** («Categoría», «Supermercado», «Estado»…) y dentro va el valor elegido.
  - La opción por defecto («Todas», «Todos») quita el filtro.
  - **Con un valor que no es el de por defecto,** el contorno y la etiqueta van en el acento del módulo (C3).
  - **En el menú,** la opción elegida lleva una marca visible y está seleccionada en semántica.
  - **TalkBack** lee el nombre y el valor («Estado: Pendientes») y que es un desplegable.
  - **Reparto en filas:** dos por fila, con el mismo ancho. Uno solo ocupa la fila entera, salvo que comparta fila con un interruptor. Con letra al 130 %, si no caben, cada uno pasa a ocupar su fila. Ni el nombre ni el valor se cortan con puntos suspensivos con los valores reales más largos, como «Desayunos y meriendas» o un nombre de miembro largo.
- **Dónde:**

  | Pantalla | Desplegables | Además |
  |---|---|---|
  | Tareas | Estado (Pendientes, Completadas, Todas), Prioridad, Tipo, Responsable (con «Sin asignar») | Dos filas |
  | Compra | Supermercado | — |
  | Recetario | Categoría | «Con lo que hay» como interruptor al lado, porque no es una categoría (llamada mía) |
  | Ingredientes | Categoría (**nuevo**) | — |
  | Ejercicios y su selector | Tipo, Grupo muscular (como hoy) | — |
  | Calendario | Personas, de **selección múltiple**: menú con casillas que no se cierra al marcar, con «Todas» (llamada mía). Sustituye a los chips de miembros | — |
| Mi calendario | Espacios, de **selección múltiple**, igual que Personas. Sustituye a los chips de espacios (`SpaceFilters`) | — |

- **El modelo de filtros de cada pantalla no cambia**, salvo el filtro de categoría nuevo de Ingredientes.
- **Menor 1 del backlog (Tareas sin resultados).** Hoy, con todo hecho y los filtros por defecto, sale «Ninguna tarea coincide con los filtros», y «Quitar filtros» puede seguir dejando la lista vacía. Pasa a ser así (llamada mía):
  - Si no queda ninguna pendiente y los filtros son los de por defecto, el estado vacío dice que está todo hecho y ofrece **«Ver completadas»**, que pone Estado en Completadas. Si no hay ninguna tarea, es el estado vacío de siempre.
  - **«Quitar filtros»** solo sale cuando hay algún filtro distinto del de por defecto.
- **Menor 2** (el chip de filtro que solo decía el valor) queda resuelto con la etiqueta siempre visible.

**Hecho cuando:**
- un test de UI del componente comprueba la etiqueta, el valor, el estado elegido en el menú y la descripción para TalkBack;
- un test de ViewModel o de lógica comprueba el filtro de categoría de Ingredientes;
- un test de lógica comprueba el estado vacío de Tareas en sus tres casos: todo hecho con filtros por defecto, filtros sin resultados y ninguna tarea;
- un test de UI comprueba la selección múltiple de Personas o de Espacios;
- ya no queda ninguna fila de `FilterChip` usada como filtro de lista en las pantallas de la tabla.

## C5. Listas agrupadas en vez de losas

**Decisión** (maquetas 2, 4, 5 y 6):
- **Componente común de filas agrupadas**, en `core/designsystem/component`:
  - filas con fondo de tono (`surfaceContainer` o el tinte que toque), separadas por 2 dp de hueco y sin líneas;
  - esquinas grandes en los extremos del grupo y pequeñas por dentro;
  - cada fila con un toque de al menos 48 dp.
- **Quién lo usa:**
  - Hoy, Más, el inicio y los cuatro catálogos;
  - **Tareas y Compra:** sus elementos pasan de tarjetas a filas agrupadas por sección. Conservan el tinte de su categoría (tipo de tarea, supermercado), el deslizar para borrar y «Más opciones» (llamada mía, como en las maquetas 6 y 7).
- **Las tarjetas se quedan solo para objetos:**
  - la comida (Comidas);
  - el evento (vistas Semana y Día del Calendario, y Mi calendario);
  - la actividad (Deporte);
  - la nota (Notas).
- **Catálogos agrupados por categoría con cabeceras fijas** (decisión del usuario, opción A):
  - **Recetario:**
    - «Mis recetas» primero y después las comunes, agrupadas por `category`;
    - sin categoría, al final, en «Otras».
    - Cada fila lleva el título y una línea secundaria con los minutos y las raciones o, si está activo «Con lo que hay», la línea de la despensa que ya existe («Faltan 2: …», «Tienes todo»). La categoría ya no va en la fila, porque la dice la cabecera.
  - **Ingredientes:** los personales primero («Mis ingredientes») y después los comunes, agrupados por `category`.
  - **Ejercicios:**
    - agrupados por grupo muscular; sin grupo, al final;
    - cada fila lleva una **miniatura** de la imagen del ejercicio o, si no tiene, el icono de su tipo sobre un fondo de tono;
    - el nombre puede ir en dos líneas;
    - la línea secundaria lleva el tipo y el material («Peso libre · Mancuernas»). El chip de tipo de la derecha desaparece.
  - **Rutinas:** «Mis rutinas» y «Comunes», sin más grupos.
  - **Cabecera fija:**
    - «Categoría · N», marcada como encabezado;
    - se queda arriba mientras se recorre su grupo, con fondo opaco.
  - **Al buscar,** desaparecen las cabeceras de categoría y sale la lista de resultados. Primero los tuyos y la categoría vuelve a la línea secundaria.
  - **Con un filtro de categoría,** solo sale ese grupo, con los tuyos primero dentro de él (llamada mía).
  - **Con «Con lo que hay» activo** en el Recetario, también desaparecen las cabeceras: es una lista plana ordenada de la que menos falta a la que más, como hoy (`RecipePantry.kt`, `withPantryLines`), porque su propósito es ese orden (llamada mía).
- **Miniaturas:**
  - se cargan por fila visible, con la caché por hash que ya existe (`data/repository/ExerciseImageRepository.kt`);
  - se decodifican al tamaño de la miniatura (submuestreo), nunca a tamaño completo;
  - son decorativas para TalkBack.
- **La agrupación es una función pura y compartida** con test JVM: recibe los elementos y la consulta, y devuelve los grupos con su cabecera y número.

**Hecho cuando:**
- el test de la agrupación cubre:
  - sin categoría;
  - con búsqueda;
  - con filtro de categoría;
  - con «Con lo que hay»;
  - los tuyos primero;
- un test de UI comprueba que la cabecera fija es encabezado y lee «Categoría · N»;
- no queda ninguna `Card` por elemento en los catálogos, ni en Tareas ni en Compra.

## C6. Hoy como agenda de toda la casa

**Hoy.**
- `core/navigation/SpaceTodayScreen.kt` y `feature/spaces/SpaceHomeSections.kt` pintan:
  - la tarjeta grande del saludo;
  - cuatro contadores (`FirestoreSpaceSummaryRepository.counts`, consultas de recuento);
  - «Mi día» (`SpaceHomeDigest`: lo tuyo);
  - «Reparto de tareas».
- «Calendario» cuenta los eventos con `startAt >= now`, de modo que un evento de todo el día o uno ya empezado no cuenta (menor de la fase A).
- `SpaceHomeViewModel` mantiene cuatro escuchas de Firestore abiertas aunque Hoy no esté a la vista (menor interno de la fase A).

**Decisión** (decisión del usuario: opción A y filas teñidas; maquetas 1 y 2):
- **Fuera:** los cuatro contadores (y su consulta de recuento, si nadie más la usa), la tarjeta grande del saludo y «Mi día».
- **Cabecera** neutra, sin franja:
  - el selector del espacio, ajustes y perfil;
  - debajo, el saludo en pequeño («Buenas tardes, David», con el `DayPeriod` que ya existe);
  - la **fecha larga como título grande** («Sábado, 17 de octubre»).
- **Todo es de toda la casa.** Donde aparece el usuario actual, dice **«Tú»**: «Tú», «Cocinas tú».
- **Secciones, en este orden:**
  1. **«Tareas para hoy · N».**
     - **Qué tareas:** las pendientes que vencen hoy o que ya vencieron, de cualquier miembro y también sin asignar.
     - **Orden:** primero las vencidas, de la más antigua a la más reciente, y después las de hoy.
     - **Cada fila:**
       - va teñida del color de Tareas;
       - lleva una **casilla que completa la tarea sin salir de Hoy**, por el mismo camino que en Tareas y con el mismo snackbar con **Deshacer** (ver «Deshacer al completar», más abajo);
       - lleva el título;
       - la línea secundaria lleva:
         - en las vencidas, cuándo vencieron, en el color de error («Venció ayer», «Venció el 12 de octubre»);
         - la bandera de prioridad con su nombre;
         - quién la tiene: «Tú», su nombre o «Sin asignar».
     - **Tocar la fila** (fuera de la casilla) lleva a la pestaña Tareas (llamada mía).
     - **Como mucho, 5 filas.** Si hay más, una fila final «Ver las N en Tareas» (llamada mía).
     - **Si no hay ninguna,** la sección no se pinta.
  2. **«Agenda».** Las cosas de hoy de toda la casa, ordenadas así (llamada mía):
     - primero, los eventos de **todo el día**, con la etiqueta «Todo el día»;
     - después, las **actividades de Deporte sin hora**, con «Sin hora». Son casi todas: el evento enlazado de una actividad se crea de todo el día (`FirestoreSportActivityRepository`, `newSportEventData`). Entran aquí las que no tienen evento y las que lo tienen de todo el día;
     - después, todo lo que tiene hora, por hora: los **eventos con hora** (también los que empezaron antes y siguen hoy) y las **actividades cuyo evento enlazado tiene hora** (alguien se la puso desde el Calendario), a la hora del evento;
     - las **comidas** van en su franja, con el nombre de la franja como etiqueta («Desayuno», «Comida», «Cena»). Para ordenarlas cuentan como las 08:00, las 14:00 y las 21:00.
     - Una **actividad con evento enlazado** sale **una sola vez**, como actividad; su evento no se repite.
     - **Las tres franjas de comida salen siempre.** Una sin planificar sale atenuada, «Sin planificar», y al tocarla abre Comidas en hoy.
     - **Cada fila:**
       - va teñida del color de su módulo: Calendario, Comidas o Deporte (decisión del usuario, opción 3);
       - lleva el icono del módulo, el título y una línea secundaria con quién: participantes; en una comida, quién cocina y cuántos comen; en una actividad, «Hecha» si lo está;
       - al tocarla, lleva a su pantalla: el Calendario en ese día, Comidas o Deporte.
     - **Con la agenda vacía,** sale una línea «Hoy no hay nada en la agenda», además de las tres franjas sin planificar.
  3. **Compra.** Una sola fila teñida de Compra:
     - dice «Compra · N» y los tres primeros productos pendientes («Leche, pan, huevos y 2 más»), y lleva a la pestaña Compra;
     - con 0 pendientes dice «La compra está al día» (llamada mía).
  4. **«Reparto de tareas».** Como ahora, al final.
- **Estados:**
  - **Carga:** `LoadingState` hasta la primera carga completa, como en la fase A.
  - **Error:** `ErrorState` con «Reintentar».
  - **Sin conexión:** las casillas se ven pero no se pueden marcar (`canWrite`).
- **TalkBack:**
  - las cabeceras de sección son encabezados;
  - cada fila de la agenda se lee entera («Comida: Albóndigas en salsa, cocinas tú, comen 3»);
  - la fila de tarea es una casilla con nombre y estado.
- **Datos:**
  - **Una función pura** construye la agenda a partir de las tareas, las comidas, los eventos, las actividades, la compra, los miembros, el usuario y el día de hoy, con test JVM. Sustituye a `SpaceHomeDigest`. El reparto se conserva.
  - **Las escuchas solo están abiertas mientras Hoy está a la vista.** Se recogen con el ciclo de vida, no en `viewModelScope` para siempre.
  - **La fila de la compra** usa la escucha que ya existe, `ShoppingRepository.items()`, y filtra los pendientes en local, con el mismo orden que la lista de Compra (llamada mía). Así no hace falta un índice compuesto nuevo (`purchased` + `createdAt`), que tampoco está en `firestore.indexes.json`, y la fila va en tiempo real. Como todas las escuchas de Hoy, solo está abierta mientras Hoy está a la vista.
- **«Calendario 0»** desaparece, y con él el menor de la fase A.
- **Deshacer al completar** (decisión del usuario, opción a). Hoy, `TasksViewModel.setCompleted` solo avisa «Tarea completada.», sin Deshacer.
  - **Dónde:** al completar una tarea, en Hoy y en Tareas. El snackbar de «Tarea completada.» gana **«Deshacer»** (`launchUndo`, `core/designsystem/component/UndoSnackbar.kt`), como los borrados de la fase B.
  - **Qué devuelve:**
    - una tarea normal vuelve a pendiente, tal como estaba;
    - **una tarea que se repite** vuelve a sus fechas anteriores. Completarla no la cierra, sino que adelanta `startAt` y `dueAt` (`FirestoreTaskRepository.kt`, alrededor de `:141-162`), así que `setCompleted(false)` no basta. Deshacer restaura las fechas y el estado que tenía antes de completarla, con un método del repositorio que lo haga en una sola escritura.
    - Si ya no se puede devolver (otro miembro la cambió o la borró), lo avisa con un snackbar, como en la fase A.
  - **Compra no lo necesita:** un producto comprado sigue a la vista en su sección «Comprados» y se desmarca con un toque.
  - **Sin cambios en `firestore.rules`.** Si las reglas no dejaran restaurar las fechas, la tarea que se repite se queda sin Deshacer: el plan lo anota como ruling y el menor va al backlog, como en la fase B.

**Hecho cuando:**
- tests de ViewModel de Tareas y de Hoy comprueban que completar una tarea ofrece Deshacer y que Deshacer la devuelve a pendiente;
- un test comprueba que Deshacer devuelve una tarea que se repite a sus fechas anteriores;
- el test JVM de la agenda cubre:
  - tareas vencidas, de hoy, sin asignar y propias («Tú»), con el orden y el tope de 5;
  - evento de todo el día, evento empezado ayer que sigue hoy y evento con hora;
  - actividad con evento enlazado sin duplicar, con evento de todo el día («Sin hora»), con evento con hora y sin evento;
  - franjas planificadas y sin planificar, con el orden por hora;
  - compra con 0, con 2 y con 5 pendientes;
  - día vacío;
- un test de semántica comprueba los encabezados y la fila de tarea como casilla;
- no queda ninguna referencia a los contadores ni a «Mi día».

## C7. Más, el inicio y los catálogos en un solo sitio

**Decisión** (maqueta 4, aprobada):
- **Más** (`core/navigation/SpaceMoreScreen.kt`) pasa de losas (`ModuleCard`) a dos grupos de filas:
  - **«Del espacio»:** Comidas, Deporte, Notas y Despensa. Cada fila va teñida de su módulo, con su icono y una flecha.
  - **«Catálogos»:** Recetario, Ingredientes, Ejercicios y Rutinas. Filas neutras, con el icono dentro de una pastilla del color heredado.
- **Inicio** (`MainMenuScreen`, `core/navigation/BobitosNavHost.kt:~562`):
  - **«Tus espacios»:**
    - lista los espacios del usuario, con iniciales, nombre y «N miembros». Los datos ya están en `uiState.spaces`.
    - **Tocar uno entra en ese espacio (Hoy) con un toque**, sin pasar por la pantalla Tus espacios.
    - La acción de la cabecera, «Gestionar», lleva a la pantalla Tus espacios, para crear, unirse y gestionar.
    - Sin espacios, una fila «Crear o unirse a un espacio» lleva a esa misma pantalla.
  - **«Lo tuyo»:** Mi calendario, teñido de Calendario, con «Los eventos de todos tus espacios».
  - **«Catálogos»:** las mismas cuatro filas que en Más.
- **Los catálogos se abren solo desde «Catálogos»,** en Más y en el inicio:
  - Comidas pierde el enlace «Recetario» y el icono de Ingredientes de su cabecera;
  - Deporte pierde «Rutinas» y «Ejercicios».

  Los caminos que no son de navegación se quedan:
  - elegir receta en el editor de comida;
  - elegir ejercicio en una rutina o en la sesión;
  - los enlaces de receta compartida;
  - **«¿Qué cocino?»** de la Despensa, que abre el Recetario con «Con lo que hay» (`openRecipesWithPantry`). Es una acción de la Despensa, no un acceso al catálogo.
- **Iconos:** Deporte pasa a un icono de persona en movimiento (por ejemplo, `Icons.Rounded.DirectionsRun`) y Ejercicios conserva la pesa. En Más y en el inicio, ninguna fila comparte icono con otra.
- **La app sigue arrancando en el inicio.** Abrir directamente el último espacio queda fuera.

**Hecho cuando:**
- un test de UI comprueba que tocar un espacio en el inicio entra en su Hoy;
- un test de UI comprueba que Más tiene sus dos grupos con encabezado;
- ni Comidas ni Deporte tienen enlaces a catálogos en su cabecera;
- Deporte y Ejercicios no comparten icono, y en Más y en el inicio ninguna fila repite icono.

## C8. Comidas: añadir en la franja vacía

**Decisión** (decisión del usuario, opción b):
- **Una franja vacía** (`MealSlotSection`, `feature/meals/MealsScreen.kt`) muestra, en vez de «Sin planificar», una fila pulsable «Añadir desayuno», «Añadir comida» o «Añadir cena», con icono de añadir.
  - Abre el editor de comida con el **día elegido** y **esa franja**.
  - Solo con `canWrite`. Sin conexión, la franja sigue diciendo «Sin planificar».
- **Las franjas con comida** no llevan botón.
- **El FAB se queda** como está: día elegido y franja según la hora (`defaultMealSlot`).
- **Menor 3 del backlog:** las cabeceras de franja de Comidas pasan a `SectionHeader` (encabezado). Las cabeceras de día de la semana del Calendario también pasan a encabezado.

**Hecho cuando:**
- un test de UI o de ViewModel comprueba que tocar la franja vacía abre el editor con ese día y esa franja;
- un test de semántica comprueba que las cabeceras de franja son encabezados.

## C9. Tipografía

**Decisión** (maqueta 7):
- **Cuatro tamaños para el contenido**, mapeados a roles de Material en `core/designsystem/theme/Type.kt`:

  | Uso | Rol orientativo | Aspecto |
  |---|---|---|
  | Título de pantalla | `headlineMedium` | Nunito ExtraBold |
  | Cabecera de sección | `titleSmall` | SemiBold o Bold |
  | Fila | `bodyLarge` | — |
  | Dato secundario | `bodyMedium` | `onSurfaceVariant` |

  - Los controles siguen con sus roles de etiqueta (`label*`).
  - La barra encogida usa `titleLarge`.
- Las pantallas de esta fase **solo usan esos roles** para su contenido: sin tamaños sueltos (`fontSize = …sp`) ni estilos copiados a mano.
- Todo en `sp`, y con letra al 130 % nada se corta ni se solapa.

**Hecho cuando** las pantallas de esta fase no tienen tamaños de letra literales. Es una tarea de aspecto: sin test RED.

## C10. Movimiento

**Decisión** (maqueta 8, «con más vida»). Con «Quitar animaciones» (`rememberReduceMotion`, `core/designsystem/Motion.kt`) todo es instantáneo. Nada dura más de unos 500 ms, ni bloquea un toque.
- **Pulsar:** filas, tarjetas y FAB se hunden un poco y vuelven con un muelle con rebote.
- **Completar** una tarea (en Hoy y en Tareas) o un producto (en Compra):
  - la casilla rebota;
  - el texto se tacha;
  - la fila sale de la lista con un desplazamiento y un pequeño giro.
  - Firestore quita la fila de la lista en cuanto se escribe en local, y `animateItem` solo da un fundido al salir. Para el desplazamiento y el giro, la interfaz mantiene la fila un instante, marcada, con un estado local. **La escritura no se retrasa nunca.** Si no se puede sin retrasarla, basta el fundido de `animateItem`.
- **Celebración:**
  - **Cuándo sale** (llamada mía: la misma condición para Hoy y Tareas, independiente de los filtros):
    - en Hoy y en Tareas, cuando una acción del usuario deja a 0 las tareas pendientes que vencen hoy o ya vencieron. Una tarea recurrente cuenta como hecha para hoy cuando su siguiente vencimiento ya no es hoy;
    - en Compra, cuando el usuario marca el último producto pendiente de toda la lista, sea cual sea el filtro de supermercado.
  - **Cómo es:** «Todo hecho por hoy» (o su equivalente en cada pantalla) y un estallido pequeño de partículas en los colores de los módulos, de menos de 1 s.
  - **Solo cuando la causa una acción del propio usuario,** no al cargar ni por un cambio de otro miembro (llamada mía).
  - TalkBack anuncia el texto con cortesía.
  - La condición es lógica pura con test JVM.
- **Entrada en cascada:** las filas de Hoy llegan una tras otra, unos 50 ms de diferencia, en la primera carga. Lo mismo al cambiar de día en Comidas y Deporte. No pasa en cada recomposición ni en cada cambio en tiempo real.
- **Tira de días:** la pastilla del día elegido se desliza con un muelle hasta el día tocado.
- **FAB:** al bajar por una lista se queda en solo el icono; al subir, vuelve con su texto (`ExtendedFloatingActionButton(expanded = …)`). Vale en todas las listas con FAB.
- **Navegación** (`BobitosNavHost`, hoy con fundidos):
  - cambiar de pestaña, con un **fundido cruzado** («fade through»);
  - entrar en una pantalla (catálogo, Notas, Despensa, Mi calendario, Perfil, Ajustes, Tus espacios), **deslizando desde la derecha**, y Atrás la devuelve por el mismo lado («shared axis» horizontal).
- **Cabecera:** el título grande se encoge siguiendo el dedo (C2).
- **Vibración corta** (`LocalHapticFeedback`, sin permisos nuevos) al marcar una tarea o un producto y al mantener pulsado para crear un evento (C11).

**Hecho cuando:**
- un test JVM comprueba la condición de la celebración: última pendiente completada por el usuario sí; carga inicial, cambio remoto y lista que ya estaba vacía no;
- un test comprueba que, con «Quitar animaciones», las especificaciones de animación de la app son instantáneas (por ejemplo, un helper común que devuelve `snap()` o ninguna transición).

El resto es aspecto, sin test RED.

## C11. Calendario y tira de días (menores del backlog)

- **Menor 4, tira de días:**
  - «hoy» sin elegir se marca con un contorno en el acento del módulo, con al menos 3:1 contra su fondo (incluida la franja teñida);
  - **roles:** los días de `WeekDayStrip` y las celdas del mes (`DayCell`) son elementos `selectable` de un grupo, con rol de opción única (llamada mía). TalkBack anuncia el día, si está elegido y si es hoy. Hoy son `Tab` fuera de un `TabRow` y `Button` con `selected`.
- **Menor 5, «hoy» se congela pasada la medianoche** (`weekRows(…, LocalDate.now())` dentro de `remember`, y `LocalDate.now()` en la composición de las tiras de Comidas y Deporte):
  - un único «hoy» que cambia a medianoche y al volver a la app;
  - ya existe uno, `CurrentDayProvider` (`feature/spaces/SpaceHomeViewModel.kt`), que se puede sustituir en los tests: se reutiliza o se lleva a `core`;
  - lo usan la semana del Calendario, las tiras de días y la agenda de Hoy;
  - es testeable con un reloj falso.
- **Menor 6, semana plegada** (`feature/calendar/FoldedDaysRow.kt`, `calendar_empty_run_description`):
  - un solo día se lee «domingo, 18 de octubre, sin eventos», sin rango;
  - un rango se lee «del martes 13 al viernes 16 de octubre, sin eventos», sin mayúsculas a mitad de frase.
- **Menor 7, parpadeo al cambiar de periodo:** Calendario y Mi calendario conservan lo que había mientras llega el periodo nuevo, con un indicador de progreso fino arriba. `LoadingState` solo sale en la primera carga.
- **Menor 8, crear manteniendo pulsado** (`HourRow`, vista Día):
  - al mantener pulsada una hora, vibra y la fila se ilumina como al pulsar;
  - con `canWrite`, bajo la fecha de la vista Día hay una línea de pista: «Mantén pulsada una hora para crear un evento» (llamada mía);
  - la acción con nombre para TalkBack se queda.

**Hecho cuando** hay tests de:
- la tira y la celda, con rol, elegido y «hoy»;
- el cambio de día a medianoche, con reloj falso;
- las dos descripciones de la semana plegada;
- que cambiar de periodo con contenido no vuelve a `LoadingState`.

## C12. Hoja de detalle con letra grande (menor 9)

`BobitosInfoSheet` (`core/designsystem/component/BobitosInfoSheet.kt:~80`) pone «Cerrar» y el botón principal en una fila sin alternativa.
- Si no caben en una línea con letra al 130 % en 360 dp, **se apilan**: el principal arriba, a todo el ancho, y «Cerrar» debajo.
- Ningún texto de botón se corta ni se parte.

**Hecho cuando** un test de UI con escala de letra 1,3 y 360 dp de ancho comprueba que «Añadir a la compra» no se corta.

## Fuera del alcance

- **Fase D:**
  - adaptividad (tablet, rail);
  - rendimiento, salvo las miniaturas;
  - adopción de `Spacing` y textos sueltos;
  - atrás predictivo.
- **Abrir el último espacio al arrancar:** la app sigue empezando en el inicio, que ahora lista los espacios.
- **Despensa:** sin cambios de comportamiento; solo su color, su cabecera y el título.
- **Backlog que no entra:**
  - los menores de Deshacer;
  - el de seguridad del ingrediente personal;
  - el de revocar invitación;
  - los internos que no se han nombrado.
- **Reglas de Firestore:** ningún cambio.

## Lo que queda al implementador

- Los valores exactos de Notas y Despensa, del tinte y del color «sobre el acento», siempre que pasen el test de C1.
- El mapeo de roles de Material en el tema de módulo (C3).
- Nombres y ubicación de los componentes nuevos (filas agrupadas, desplegable, cabecera de módulo, celebración, helper de movimiento, «hoy» que cambia a medianoche), siguiendo `core/designsystem` y `core/navigation`.
- Qué barra de Material 3 usar para el título que se encoge, siempre que se comporte como en la maqueta 7.
- Cómo se obtiene la fila de la compra en Hoy, con el criterio de lecturas de C6.
- Los iconos exactos (`Icons.Rounded`) y la redacción de las cadenas nuevas: tuteo, punto final en frases completas y plurales.
- Las curvas y duraciones de cada animación, dentro de los límites de C10.

## Llamadas mías (no del usuario)

Se mostraron en el chat y el usuario no las objetó:
- el Calendario conserva el teal;
- los colores de Notas (ámbar) y Despensa (verde);
- los catálogos heredan el color;
- las recetas sin autor;
- «Con lo que hay» como interruptor junto al desplegable;
- la franja de las tres comidas en Hoy, con «Sin planificar» atenuado.

Salen de la escritura de esta spec y no se han comentado en el chat:
- **Pantallas globales** con cabecera neutra y acentos teal (C1).
- **El color del módulo entra como tema** y alcanza a sus hojas y diálogos, también al botón «Guardar» (C3).
- **Tareas y Compra** pasan de tarjetas a filas agrupadas con el tinte de su categoría (C5). Así salían en las maquetas 6 y 7, pero no se preguntó.
- **Filtro de Personas** del Calendario como desplegable de selección múltiple (C4).
- **Los filtros y la búsqueda se desplazan con la lista;** la tira de días se queda fija (C2).
- **Hoy** (C6):
  - tope de 5 tareas con «Ver las N en Tareas»;
  - tocar una fila lleva a su pantalla;
  - el orden de la agenda, con las franjas a las 08:00, 14:00 y 21:00;
  - «Sin hora» para las actividades sin evento;
  - la compra a 0 dice «La compra está al día».
- **Tareas sin pendientes:** «Ver completadas», y «Quitar filtros» solo con filtros distintos de los de por defecto (C4).
- **Celebración** también en Tareas y Compra, y solo por una acción propia (C10).
- **Pista** de mantener pulsado en la vista Día (C11).
- **Rol de opción única** para los días de la tira y del mes (C11).

Salen de la revisión del documento:
- **Los acentos claros** de Compra, Calendario y Tareas se pueden oscurecer dentro de su tono para llegar a 4,5:1 como texto (C1).
- **Actividades de Deporte en Hoy:** las que tienen el evento de todo el día van en «Sin hora». Casi todas lo tienen así, y solo van a su hora si alguien se la pone al evento (C6).
- **Mi calendario** filtra por **Espacios**, no por Personas, como hoy (C4).
- **Recetario:** con «Con lo que hay» activo, es una lista plana ordenada por lo que falta; con una categoría elegida, los tuyos van primero dentro de ese grupo (C5).
- **«¿Qué cocino?»** de la Despensa se queda como camino al Recetario (C7).
- **La fila de la compra de Hoy** usa la escucha que ya existe y filtra en local, sin índice nuevo (C6).
- **Animación de completar:** la fila se mantiene un instante en la interfaz y la escritura no se retrasa nunca (C10).
- **La celebración** usa la misma condición en Hoy y en Tareas, sin depender de los filtros. En Compra, cuenta toda la lista (C10).
