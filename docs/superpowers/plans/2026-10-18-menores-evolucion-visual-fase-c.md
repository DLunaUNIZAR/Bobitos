# Menores visibles de la fase C — plan de implementación

> **Para agentes:** se ejecuta con la skill `delegating-plan-execution` del usuario. Es un **plan de menores** (13 tareas): pasos 1, 2, 3, 5 y 6, **sin `simplify`**, más la revisión de diseño después del paso 3 porque toca la interfaz. El marco es superpowers:executing-plans. Cada tarea la hace un subagente `sonnet`, en serie y en el orden del plan. La revisión final la hace `opus` (si falla, `fable`). Los commits los hace el coordinador. **Ninguna tarea toca `firestore.rules` ni `migrations/`.** Los pasos usan casillas (`- [ ]`).

**Objetivo:** cerrar los 25 menores con efecto visible de la sección «2026-10-18 — `agent/evolucion-visual-fase-c`» de `docs/superpowers/backlog.md`. Los 5 internos de esa sección se quedan en el backlog.

**Arquitectura:**
- **Cabecera (tareas 1 y 2):**
  - la barra grande se compone de un `TopAppBar` de Material (la fila de iconos) y una zona abierta propia que se encoge con `TopAppBarState`;
  - el selector del espacio va en la fila de iconos;
  - la franja de filtros y búsqueda entra en la zona que se encoge, con el remate redondeado siempre al final.
- **Color y sistema visual (tareas 3 a 7):** etiquetas de la barra inferior con un solo tamaño, tinte claro más suave, Notas, Despensa y Más dentro del sistema, tipografía de metadatos, iconos de color en los menús de filtro y desplegable activo con una señal que no es solo el color.
- **Lógica pura con test JVM (tareas 8 a 13):** búsquedas y categorías, la cascada por día, la escala de animación, la fecha de «Venció el …», los nombres vacíos, la agenda con su espacio, el bloqueo de Deshacer y el reintento de la lista de espacios.

**Tecnología:** Kotlin 2.3.10, Jetpack Compose (BOM 2026.06.00: material3 1.4.0, foundation 1.11.3), Hilt y Firestore.
- Tests JVM con JUnit 4 y `kotlinx-coroutines-test`, con dobles a mano. No hay Robolectric.
- Tests Compose instrumentados con `createAndroidComposeRule<ComponentActivity>()` y `BobitosTheme`.
- La letra grande se prueba con `CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 1.3f))`, dentro de un `Box(Modifier.width(…))`.

**Spec:**
- El backlog (sección de arriba) hace de spec de este plan.
- Spec de referencia: `docs/superpowers/specs/2026-10-18-evolucion-visual-fase-c-design.md` (C1–C12, «Dirección visual», «Restricciones»).
- Maquetas: `docs/superpowers/specs/2026-10-18-evolucion-visual-fase-c-maquetas/`. La 7 fija la cabecera.
- Plan y ledger de la fase C: `docs/superpowers/plans/2026-10-18-evolucion-visual-fase-c.md` y `.superpowers/sdd/2026-10-18-evolucion-visual-fase-c/progress.md` (rulings R1–R18).
- Contexto de producto: `PRODUCT.md`. Fija **WCAG AA y TalkBack usable**.

## Qué entra

| # | Menor (backlog, sección «2026-10-18 — `agent/evolucion-visual-fase-c`») | Tarea |
|---|---|---|
| M1 | La barra grande cambia de golpe al 50 %, recompone en cada fotograma, y el selector invisible de la fila superior responde a toques | 1 |
| M2 | La cabecera abierta gasta ~130 dp antes del título (selector en fila propia); en Tareas, la cabecera ocupa más de media pantalla | 1 y 2 |
| M3 | El título abierto reserva dos líneas por conteo de caracteres | 1 |
| M4 | Al desplazar, la búsqueda queda partida bajo la barra encogida y el remate solo existe abierta; en los catálogos asoma una tira de fila bajo la barra | 2 |
| M5 | El saludo de Hoy va debajo de la fecha | 1 |
| M6 | Hoy vacío dice «Hoy no hay nada en la agenda» y debajo salen tres franjas | 11 |
| M7 | «Venció el …» con «de» fijo y sin año; un nombre vacío deja «Alta · » o «Ana, » | 11 |
| M8 | Un error de otro espacio se pinta un fotograma bajo el nombre nuevo | 11 |
| M9 | Deshacer al completar revierte sin aviso los cambios de otro miembro | 12 |
| M10 | La píldora de la celebración bloquea toques 1,5 s | 10 |
| M11 | La cascada de Comidas y Deporte anima la primera carga, al volver a ver una fila y con cada alta | 10 |
| M12 | La pastilla de la tira de días rebota y se dibuja fuera de la fila | 9 |
| M13 | Cada día de la tira mide ~37 dp de ancho | 9 |
| M14 | Con letra al 130 %, «Calendario» sale más pequeño que las otras etiquetas de la barra inferior | 3 |
| M15 | En claro, Compra, Tareas, Más y Hoy parecen baldosas pastel | 4 |
| M16 | Notas, Despensa y Rutinas se salen del sistema visual | 1, 2 y 5 |
| M17 | Metadatos de fila en `bodySmall` y título del inicio en `headlineSmall` | 6 |
| M18 | Los menús de Prioridad y Tipo perdieron su icono de color, y la cabecera de franja de Comidas el color de su icono | 7 |
| M19 | El desplegable activo solo se distingue por el color | 7 |
| M20 | Al buscar en Ejercicios no vuelve el grupo muscular a la línea secundaria | 8 |
| M21 | Las categorías del Recetario: orden, mayúsculas, espacios y comentario obsoleto | 8 |
| M22 | Al buscar en Comidas, una franja sin coincidencias ofrece «Añadir cena» | 8 |
| M23 | Con la lista de espacios en error, el inicio no ofrece «Reintentar» | 13 |
| M24 | «Quitar animaciones» se lee una sola vez | 10 |
| M25 | Las filas de «Del espacio» en Más miden menos que las de «Catálogos», y `RecipePicker` tiene un `clickable` sin rol | 5 y 6 |

## Qué no entra

- Los 5 menores **internos** de la sección: contraste de acentos sobre tintes ajenos, `rememberToday()` sin Hilt, `CompletingItems.retain`, el Review Focus 4 en pantallas reales, y `DirectionsRun` obsoleto con el baseline de detekt.
- Los menores de las demás secciones del backlog.
- Cualquier despliegue (reglas, beta) y cualquier push.

## Decisiones del usuario (chat del 2026-10-18)

- **M15, tinte en claro:** más suave. En claro, el tinte de filas y cabeceras baja del 12 % a un 6–7 %. En oscuro no cambia nada.
- **M13, tira de días:** sin flechas. La tira ocupa toda la fila.
  - Se cambia de semana deslizando la tira, y la tira se desliza con la semana.
  - Tocar la fecha de debajo abre el selector de fecha, como alternativa de un toque.
  - TalkBack tiene «Semana anterior» y «Semana siguiente» como acciones.
- **M6, Hoy vacío:** la línea pasa a decir «Sin eventos ni deporte hoy» y las tres franjas siguen debajo.

## Restricciones globales

**Rutas**
- `J/` = `app/src/main/java/com/dlunaunizar/bobitos/`.
- `T/` = `app/src/test/java/com/dlunaunizar/bobitos/` (JVM).
- `A/` = `app/src/androidTest/java/com/dlunaunizar/bobitos/` (instrumentados).
- `S` = `app/src/main/res/values/strings.xml`. Es la única carpeta de textos.

**Entorno y comandos**
- **Java:** antes de cada `./gradlew`, ejecutar `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- **Test JVM suelto:** `./gradlew :app:testDebugUnitTest --tests "<FQCN>"`.
- **Test instrumentado suelto:** `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`.
  - Necesita un emulador encendido: `export PATH="$HOME/Library/Android/sdk/emulator:$HOME/Library/Android/sdk/platform-tools:$PATH"; nohup emulator -avd Pixel_10 -no-boot-anim >/dev/null 2>&1 & adb wait-for-device`.
  - El emulador está en inglés (`en-US`). La app fija el español en los textos que lo necesitan (`AppLocale`).
- **Puerta de cada tarea:** `./gradlew ktlintCheck detekt testDebugUnitTest assembleDebug lintDebug`, más los tests instrumentados de la propia tarea.
  - `lintDebug` ya falla en `main` por `RestrictedApi currentBackStack` en `BobitosNavHost`. Ese error, y solo ese, se tolera.
  - Si un cambio de firma rompe `config/detekt/baseline.xml`, se regenera con `./gradlew detektBaseline` y se incluye en la tarea.
  - Los tests instrumentados que ya existen y usan un fichero de la tarea se actualizan en esa tarea y deben pasar.

**Textos**
- Los nuevos van en `S`, en español, con tuteo y punto final en las frases completas.
- Los conteos llevan `<plurals>`.
- Las cadenas que se quedan sin uso se borran.

**Componentes y color**
- Componentes de Material 3. Nada que imite un control de Material.
- Sin dependencias nuevas ni cambio de versiones: material3 sigue en 1.4.0.
- El color de una pantalla de módulo sale de `ModuleTheme` (`MaterialTheme.colorScheme.primary`, `primaryContainer`, `secondaryContainer` y sus «on») o de `moduleTint`.
- WCAG AA en claro y en oscuro. Todo cambio visual se comprueba en los dos temas.

**Tipografía (spec C9):**
- título de pantalla en `headlineMedium` y barra encogida en `titleLarge`;
- cabecera de sección con `SectionHeader` (`titleSmall`);
- título de fila en `bodyLarge`;
- línea secundaria en `bodyMedium` con `onSurfaceVariant`;
- sin `fontSize` literales.

**Tamaño:** destinos táctiles de 48 dp. Con letra al 130 % y 360 dp de ancho, nada se corta ni se solapa.

**Movimiento:** toda animación pasa por `motionSpec` o comprueba `rememberReduceMotion()`. Con «Quitar animaciones» es instantánea. Ninguna escritura se retrasa por una animación.

**Frontend**
- **Las skills de diseño** las invoca el implementador, con el subcomando de la línea `Frontend:` de su tarea.
- **No hay nadie a quien preguntar.** Si la skill pide elegir o confirmar algo, decide con la «Dirección visual» de la spec de la fase C, la maqueta 7 y `PRODUCT.md`, y anota en el informe lo que decidiste.
- **Límite de ficheros:** la skill solo cambia los ficheros de la tarea.
- **Este plan no hace trabajo nuevo de impeccable** (R1 de la fase C): no se crea `DESIGN.md`.
- Las carpetas de trabajo de las skills ya están en `.gitignore`.

**Verificación visual:** la revisión de diseño, después del paso 3, incluye capturas en el emulador Pixel 10 de las pantallas que cambian:
- en oscuro, en claro y con la letra al 130 % (`adb shell settings put system font_scale 1.3`, y vuelta a `1.0` al terminar);
- la cabecera abierta, a medio encoger y encogida en Tareas y en un catálogo;
- Hoy vacío y con una tarea vencida;
- la tira de días;
- Más, Notas y Despensa.

## Rulings de la planificación

- **R1. Barra compuesta, no `MediumTopAppBar`.**
  - En material3 1.4.0, `MediumFlexibleTopAppBar`, `LargeFlexibleTopAppBar` y el `TwoRowsTopAppBar` con títulos separados son `internal`.
  - `MediumTopAppBar` pinta el mismo `title` en las dos filas: de ahí el selector invisible que responde (M1).
  - La spec prohíbe dependencias nuevas, así que `BobitosLargeTopBar` se compone así:
    - un `TopAppBar` de Material para la fila de iconos;
    - debajo, una zona abierta propia;
    - un `exitUntilCollapsedScrollBehavior` que encoge esa zona: la zona fija `heightOffsetLimit` con su alto medido y se encoge con `heightOffset`.
  - El encaje al soltar a medias es el de Material: la barra queda abierta o encogida.
- **R2. El selector en la fila de iconos (maqueta 7).**
  - Abierta, la fila de iconos lleva a la izquierda el selector del espacio o la flecha atrás, y a la derecha las acciones.
  - Al encoger, el selector se funde y el título pequeño (`titleLarge`) aparece en su sitio. Las alfas se leen en `graphicsLayer`, no en la composición.
  - Pasada la mitad, el selector deja de recibir toques y sale del árbol de TalkBack: un `derivedStateOf` booleano, una sola recomposición al cruzar.
  - El título se lee **una sola vez**: es encabezado el grande mientras la barra está abierta y el pequeño cuando está encogida.
- **R3. La franja de filtros entra en la cabecera.**
  - Las pantallas con búsqueda o filtros no ponen su franja como primer elemento de la lista: la registran con `CollapsibleHeaderBand { … }`, como las acciones de `TopBarActions`. El marco la pinta en la zona abierta, bajo el título, y se encoge con ella.
  - Son Tareas, Compra, Recetario, Ingredientes, Ejercicios, Rutinas y Despensa.
  - La cabecera acaba siempre en el remate redondeado (`ModuleHeaderCap`), abierta y encogida.
  - Las franjas fijas no cambian: la tira de días de Comidas y Deporte, y el Calendario y Mi calendario (R2 de la fase C). Siguen con `ModuleHeaderBand`, que ya pone su propio remate.
  - Así se cumple lo de la spec C2: los filtros «se desplazan con la lista y dejan sitio a las filas». Con `exitUntilCollapsed`, bajan con la cabecera antes de que se mueva la lista y vuelven cuando la lista llega arriba.
- **R4. Tinte claro al 7 %.**
  - `ModulePalette.tint` (cabecera, franja y filas de Hoy y de Más) y `categoryRowTint` y `categoryCardColors` (filas de tipo y de supermercado) usan 0,07 en claro.
  - En oscuro siguen igual: 0,16 el del módulo y 0,12 el de categoría.
  - El fondo de `InfoChip` no es una fila y se queda en 0,12.
- **R5. Tira de días:**
  - **Deslizar:** cambia de semana si el arrastre horizontal pasa de 56 dp o termina con una velocidad clara. Un arrastre más corto, o más vertical que horizontal, no cambia nada.
  - **Movimiento:** la semana nueva entra deslizando desde el lado del gesto, con `motionSpec`.
  - **La pastilla del día elegido** no pasa de los extremos (posición acotada a `0..6`) y se recorta a la fila.
  - **La fecha de debajo** es un botón: abre `AppDatePickerDialog` y el día elegido va a `selectDay`.
  - **TalkBack:** «Semana anterior» y «Semana siguiente» son acciones de la tira.
- **R6. Hoy:**
  - **Hoy vacío:** cambia el texto, no la condición. La línea sale cuando no hay eventos, ni deporte, ni comidas planificadas.
  - **Nombres vacíos:**
    - un participante sin nombre se busca en los miembros por su id y, si no está, no sale;
    - un responsable sin nombre dice «Otro miembro» (`AgendaPerson.Unknown`);
    - un cocinero sin nombre no sale.
  - **«Venció el …»:** día y mes, más «de AAAA» si el año no es el de hoy. «Venció ayer» sigue mandando, también al cambiar de año.
- **R7. Deshacer al completar se bloquea si la tarea ya no está como la dejó tu completado.** Cuenta como cambiada si:
  - otro la tocó (`updatedBy` no es el usuario);
  - una normal ya no está hecha;
  - una que se repite ya no está pendiente;
  - cambió el título, la descripción, el responsable, la prioridad, el tipo o la repetición.

  Entonces no se escribe nada y sale «La tarea ha cambiado entretanto y no se ha deshecho.».
- **R8. Desplegable activo:** además del contorno y la etiqueta en el acento, el campo se rellena con `secondaryContainer` (el contenedor del módulo) y el valor va en `onSecondaryContainer` y en `FontWeight.SemiBold`. Es la señal de Material para un filtro elegido.
- **R9. Comidas al buscar:**
  - salen solo las franjas con coincidencias, sin «Añadir …»;
  - si no coincide ninguna, «Ninguna comida coincide con la búsqueda.»;
  - con el día sin comidas, la búsqueda no se aplica: el buscador no se ve, así que no se podría borrar.
- **R10. Barra inferior:** las cinco etiquetas usan el mismo tamaño, el mayor que deja caber todas en su pestaña, entre `labelMedium` y 10 sp.
- **R11. «Quitar animaciones»** se observa una vez para toda la app.
  - `BobitosTheme` provee `LocalReduceMotion`, alimentado por un `ContentObserver` de `Settings.Global.ANIMATOR_DURATION_SCALE`.
  - `rememberReduceMotion()` lee ese local.
- **R12. Cascada de Comidas y Deporte:**
  - suena solo cuando se muestra el contenido de un día distinto del anterior, durante 1,2 s;
  - no suena en la primera carga, ni al volver a ver una fila, ni con un alta en tiempo real;
  - Hoy conserva su cascada de primera carga.
- **R13. Más:** las filas de «Del espacio» llevan su icono en la misma pastilla de 36 dp que «Catálogos», con el contenedor de su módulo. Así miden lo mismo.
- **R14. Notas:**
  - siguen siendo tarjetas, porque la nota es un objeto (spec C5);
  - van con los mismos márgenes que las filas del sistema;
  - la fijada lleva el tinte de Notas.

## Review Focus

1. **Con una búsqueda escrita en Comidas, cambiar a un día sin comidas:** salen las tres franjas para añadir. Nunca queda una pantalla vacía sin buscador con el que borrar. Lo prueba la tarea 8.
2. **Un nombre de espacio largo con letra al 130 % en 360 dp** («Piso de estudiantes de Zaragoza»), en Tareas con sus tres acciones: el selector de la fila de iconos no tapa las acciones ni se solapa con el título. Lo prueba la tarea 1.
3. **TalkBack lee el título de la pantalla una sola vez,** con la barra abierta y encogida. Lo prueba la tarea 1.
4. **Un arrastre corto o en diagonal sobre la tira** (al tocar un día con el dedo algo movido) no cambia de semana. Lo prueba la tarea 9.
5. **Una tarea vencida el 31 de diciembre, vista el 1 de enero,** dice «Venció ayer»; una del 30 de diciembre dice «Venció el 30 de diciembre de 2025». Lo prueba la tarea 11.

---

### Tarea 1: barra grande que se encoge siguiendo el dedo (M1, M2, M3, M5)

**Frontend:** `impeccable:impeccable layout J/core/designsystem/component/BobitosLargeTopBar.kt J/core/navigation/WorkspaceScaffold.kt`

**Ficheros:**
- Modificar:
  - `J/core/designsystem/component/BobitosLargeTopBar.kt`;
  - `J/core/navigation/WorkspaceScaffold.kt` (`SpaceSelector`, :202, y el parámetro `supportingText`);
  - `J/core/designsystem/component/ScreenScaffold.kt`, si su llamada cambia;
  - `J/core/navigation/BobitosNavHost.kt` (:280-294, el saludo de Hoy).
- Tests:
  - crear `A/core/designsystem/component/BobitosLargeTopBarTest.kt`;
  - modificar `A/core/navigation/WorkspaceScaffoldTest.kt` y `A/core/designsystem/component/ScreenScaffoldTest.kt`.

**Interfaces:**
- Produce:
  ```kotlin
  @Composable
  fun BobitosLargeTopBar(
      title: String,
      scrollBehavior: TopAppBarScrollBehavior,
      modifier: Modifier = Modifier,
      spaceSelector: (@Composable () -> Unit)? = null, // fila de iconos, a la izquierda; solo abierta
      eyebrow: String? = null, // saludo de Hoy, encima del título
      onBack: (() -> Unit)? = null,
      actions: @Composable RowScope.() -> Unit = {},
  )
  ```
  - `WorkspaceScaffold(…, eyebrow: String? = null, …)` sustituye a `supportingText`.
  - Hoy pasa el saludo como `eyebrow`.
- Consume: `LocalAppModule` y `moduleTint`, como hoy.

- [ ] **Paso 1: Tests que fallan.** En `BobitosLargeTopBarTest`, con un `TopAppBarState` propio (`rememberTopAppBarState()` y `exitUntilCollapsedScrollBehavior(state)`), el estado se mueve en `runOnIdle { state.heightOffset = state.heightOffsetLimit * f }`.
  - `elSelectorVaEnLaFilaDeLosIconos`: con selector y dos acciones, el centro vertical del selector está a menos de 4 dp del de las acciones, y su borde inferior queda encima del título.
  - `elSaludoVaEncimaDelTitulo`: el borde inferior de «Buenas tardes, David» queda encima del borde superior de «Sábado, 17 de octubre».
  - `unTituloQueCabeEnUnaLineaNoReservaDos`: a 411 dp, la barra abierta de «Ajustes del espacio» mide lo mismo (±1 dp) que la de «Notas».
  - `unTituloQueNoCabeVaEnDosLineasSinCortarse`: a 360 dp y con letra al 130 %, «Pimientos del piquillo» da `lineCount == 2`, sin elipsis, y la barra mide más que con «Notas».
  - `aMedioEncogerElTituloSigueGrandeYSube`: con f = 0,6, el título grande sigue en `headlineMedium`, comprobado con el `fontSize` de `GetTextLayoutResult`, y su borde superior está más arriba que con f = 0.
  - `encogidaQuedaElTituloPequenoYElSelectorNoSeToca`: con f = 1, el título visible está en `titleLarge`, y no hay ningún nodo con clic y la descripción «Cambiar de espacio, Casa Bobitos».
  - `elTituloSeLeeUnaSolaVez`: con f = 0 y con f = 1, hay exactamente un nodo encabezado con el texto del título.
  - En `WorkspaceScaffoldTest`, `conLetraGrandeYUnEspacioLargoNadaSeCorta` pasa a 360 dp y 130 %, con «Piso de estudiantes de Zaragoza» y tres acciones (Review Focus 2):
    - el selector no se solapa con ninguna acción ni con el título;
    - el título no se corta.
- [ ] **Paso 2: Comprobar que fallan.** Las firmas nuevas no compilan. Con ellas, fallan los cuatro primeros y `aMedioEncoger…`.
- [ ] **Paso 3: Implementar** según R1 y R2.
  - **El alto de la zona abierta se mide** (`Layout` o `SubcomposeLayout`) con el saludo, el título y el aire de debajo. El título va en `headlineMedium`, como mucho en dos líneas, con elipsis a partir de la tercera. Desaparecen `LONG_TITLE_CHARS` y `EXPANDED_UNTIL`.
  - **Al encoger,** la zona se recorta y el título grande sube con ella. No cambia de estilo.
  - **La fila de iconos mide 64 dp** (`TopAppBarDefaults.TopAppBarExpandedHeight`). El título grande va justo debajo, sin el aire de más que hoy deja un hueco entre la flecha atrás y el título en Notas (M16).
  - **El contenedor,** abierto y encogido, es `moduleTint` o la superficie, como hoy.
  - **El selector** admite dos líneas y después elipsis, sin pasar de la fila.
  - **Nada lee `collapsedFraction` en la composición,** salvo el `derivedStateOf` booleano de R2.
- [ ] **Paso 4: Comprobar que pasan** los tests de la tarea, `WorkspaceScaffoldTest`, `ScreenScaffoldTest` y la puerta.
- [ ] **Paso 5: Informe:** el alto de la cabecera abierta de Tareas antes y después, a 411 dp y letra al 100 %.

---

### Tarea 2: la franja de filtros dentro de la cabecera (M2, M4 y parte de M16)

**Frontend:** `impeccable:impeccable layout J/core/designsystem/component/ModuleHeaderBand.kt J/core/designsystem/component/BobitosLargeTopBar.kt J/feature/tasks/TasksScreen.kt J/feature/pantry/PantryScreen.kt`

**Ficheros:**
- Modificar:
  - el marco: `J/core/designsystem/component/ModuleHeaderBand.kt`, `BobitosLargeTopBar.kt`, `ScreenScaffold.kt`, `SectionHeader.kt` y `J/core/navigation/WorkspaceScaffold.kt`;
  - las pantallas con franja en la lista: `J/feature/tasks/TasksScreen.kt` (:241-321), `J/feature/shopping/ShoppingScreen.kt`, `J/feature/recipes/RecipesScreen.kt` (:214-246, con su comentario obsoleto de :214), `J/feature/ingredients/IngredientsScreen.kt` (:305, :380), `J/feature/exercises/ExercisesScreen.kt` (:289, :370) y `J/feature/routines/RoutinesScreen.kt` (:215-266);
  - Despensa, que hoy tiene la búsqueda fuera de la lista y sin franja: `J/feature/pantry/PantryScreen.kt` (:162-169).
- Tests:
  - modificar `A/core/designsystem/component/ModuleHeaderBandTest.kt` y `A/core/navigation/WorkspaceScaffoldTest.kt`;
  - actualizar los instrumentados de esas pantallas que se rompan (`TaskFilterBarUiTest` y `GroupedRowTest`).

**Interfaces:**
- Consume: `BobitosLargeTopBar` (tarea 1).
- Produce:
  - `BobitosLargeTopBar(…, band: (@Composable ColumnScope.() -> Unit)? = null)`. La franja va en la zona abierta, bajo el título, y se encoge con ella.
  - `@Composable fun CollapsibleHeaderBand(content: @Composable ColumnScope.() -> Unit)`, en `ModuleHeaderBand.kt`. Registra `content` en un `CompositionLocal` que proveen `ScreenScaffold` y `WorkspaceScaffold`, igual que `TopBarActions`. El marco lo pasa como `band`.
  - `ModuleHeaderBand` y `DeclareHeaderBand` quedan solo para las franjas fijas (R3).
  - `SectionHeader(…, horizontalPadding: Dp = 0.dp)`. El relleno va después del fondo, para que la cabecera fija sea opaca a todo el ancho.

- [ ] **Paso 1: Tests que fallan.**
  - En `ModuleHeaderBandTest`, con `ScreenScaffold(module = TASKS)`, una `CollapsibleHeaderBand` con un `SearchField` y una lista de 60 filas:
    - `alDesplazarLaFranjaSeVaConLaCabeceraYQuedaElRemate`: tras `swipeUp()` en la lista, el buscador no se ve, y el remate (`testTag("moduleHeaderCap")`) mide 20 dp y queda bajo la barra encogida.
    - `soltarAMedioCaminoNoDejaLaBusquedaPartida`: tras un `swipeUp` de 80 dp y `waitForIdle()`, el buscador o se ve entero (alto visible igual a su alto) o no se ve.
    - `ningunaFilaAsomaEntreLaCabeceraYLaCabeceraFija`: con grupos con `stickyHeader { SectionHeader(sticky = true, …) }`, desplazada a mitad de un grupo:
      - ninguna fila (`testTag("row-…")`) cruza la franja entre el borde inferior de la cabecera y el superior de la cabecera fija;
      - la cabecera fija ocupa todo el ancho de la lista.
  - En `WorkspaceScaffoldTest`, `conCuatroDesplegablesYBusquedaLaCabeceraNoLlegaALaMitad`: dentro de un `Box` de 411 × 914 dp, con letra al 100 %, `TaskFilterBar` y un `SearchField` en la franja, el borde inferior de la cabecera abierta queda por encima de 457 dp (M2).
- [ ] **Paso 2: Comprobar que fallan.** `CollapsibleHeaderBand` no existe.
- [ ] **Paso 3: Implementar** según R3.
  - Cada pantalla de la lista de ficheros cambia su `item(key = "filters") { ModuleHeaderBand { … } }` y su `DeclareHeaderBand()` por `CollapsibleHeaderBand { … }`, con el mismo contenido.
    - En Cargando y Error, Tareas ya no necesita poner la franja fija encima del estado.
    - Compra sigue sin franja cuando no tiene artículos.
    - Despensa mete su búsqueda en la franja.
  - Tras la cabecera, la lista empieza con `Spacing.sm` de aire. Así «Mis rutinas» deja de ir pegada (M16).
  - Las cabeceras fijas de los catálogos usan `horizontalPadding = Spacing.lg` en vez de `Modifier.padding(horizontal = …)`.
  - Si la tira que asoma bajo la barra tiene otra causa, se busca en el emulador con capturas antes de arreglarla, y se dice en el informe.
- [ ] **Paso 4: Comprobar que pasan** los tests de la tarea, los instrumentados de las pantallas tocadas y la puerta.
- [ ] **Paso 5: Informe:** la causa de la tira que asomaba, y el alto de la cabecera abierta de Tareas.

---

### Tarea 3: etiquetas de la barra inferior con un solo tamaño (M14)

**Frontend:** `impeccable:impeccable typeset J/core/navigation/WorkspaceScaffold.kt`

**Ficheros:**
- Modificar `J/core/navigation/WorkspaceScaffold.kt` (:164-200).
- Tests: modificar `A/core/navigation/WorkspaceNavigationBarTest.kt`.

**Interfaces:** ninguna nueva fuera del fichero.

- [ ] **Paso 1: Test que falla.** `todasLasEtiquetasTienenElMismoTamano`:
  - a 411 dp y letra al 130 %, las cinco etiquetas tienen el mismo `fontSize` en `GetTextLayoutResult`, y ninguna lleva elipsis;
  - a letra al 100 %, las cinco van en `labelMedium`.
- [ ] **Paso 2: Comprobar que falla.** Con letra al 130 %, «Calendario» va más pequeña.
- [ ] **Paso 3: Implementar** según R10. Se mide con `rememberTextMeasurer`, una vez por ancho de pestaña y escala de letra, no en cada fotograma. Desaparece el `autoSize` por etiqueta.
- [ ] **Paso 4: Comprobar que pasan** los tests de `WorkspaceNavigationBarTest` y la puerta.

---

### Tarea 4: tinte más suave en claro (M15)

**Frontend:** `impeccable:impeccable colorize J/core/designsystem/theme/ModuleColors.kt J/core/designsystem/theme/CategoryTint.kt`

**Ficheros:**
- Modificar `J/core/designsystem/theme/ModuleColors.kt` (:25-31) y `J/core/designsystem/theme/CategoryTint.kt`.
- Tests: modificar `T/core/designsystem/theme/ModuleColorsTest.kt`, `T/core/designsystem/theme/ContrastMath.kt` y `T/core/designsystem/theme/CategoryContrastTest.kt`.

**Interfaces:**
- Produce `internal fun categoryTint(accent: Color, under: Color, dark: Boolean): Color`, pura, en `CategoryTint.kt`. `categoryRowTint` y `categoryCardColors` la usan con el tema actual.

- [ ] **Paso 1: Tests que fallan.**
  - En `ModuleColorsTest`:
    - `enClaroElTinteEsSuave`: para los siete módulos, `contrast(ModulePalette.tint(m, dark = false), backgroundLight) <= 1.15`.
    - `enOscuroElTinteNoCambia`: `ModulePalette.tint(m, true)` es el acento con alfa 0,16 sobre `backgroundDark`.
  - En `CategoryContrastTest`:
    - `enClaroElTinteDeCategoriaEsSuave`: para cada tipo de tarea y cada supermercado, `contrast(categoryTint(accent, surfaceContainerLight, false), surfaceContainerLight) <= 1.15`.
    - `ContrastMath.tinted` recibe el tema: 0,07 en claro y 0,12 en oscuro. El fondo de `InfoChip` se modela con su propia constante de 0,12.
  - Los tests de contraste que ya existen siguen pasando con el tinte nuevo.
- [ ] **Paso 2: Comprobar que fallan.** Con 0,12 en claro, el contraste contra el fondo pasa de 1,15.
- [ ] **Paso 3: Implementar** según R4.
- [ ] **Paso 4: Comprobar que pasan** los tests de contraste y la puerta.
- [ ] **Paso 5: Informe** con el contraste de cada tinte claro contra su fondo.

---

### Tarea 5: Notas, Despensa y las filas de Más en el sistema (M16, M25)

**Frontend:** `impeccable:impeccable layout J/feature/notes/NotesScreen.kt J/feature/pantry/PantryScreen.kt J/core/navigation/SpaceMoreScreen.kt`

**Ficheros:**
- Modificar:
  - `J/feature/notes/NotesScreen.kt` (:141-150 y `NoteCard`, :210-270);
  - `J/feature/pantry/PantryScreen.kt` (:177-211);
  - `J/core/navigation/SpaceMoreScreen.kt` (`ModuleRows`, :53-89, y `IconPill`, :114).
- Tests: modificar `A/core/navigation/SpaceMoreScreenTest.kt` y `A/feature/notes/NoteCardUiTest.kt`, si se rompe.

**Interfaces:** ninguna nueva.

- [ ] **Paso 1: Test que falla.** `lasFilasDeLosDosGruposMidenLoMismo`: en Más, la fila «Comidas» mide lo mismo (±1 dp) que la fila «Recetario».
- [ ] **Paso 2: Comprobar que falla.** Hoy miden 48 y 52 dp.
- [ ] **Paso 3: Implementar.**
  - **Más,** según R13.
  - **Notas,** según R14: tarjetas con los mismos márgenes que las filas, y la fijada con `moduleTint(NOTES)`.
  - **Despensa:**
    - filas en `GroupedRow`, agrupadas por su sección;
    - cabeceras con `SectionHeader` (que ya es encabezado) en vez del `Text` suelto;
    - «Se acabó» sigue en la fila.
  - El resto es aspecto, sin test RED.
- [ ] **Paso 4: Comprobar que pasan** los tests de Más y Notas y la puerta.

---

### Tarea 6: tipografía de los metadatos y rol del selector de recetas (M17, M25)

**Frontend:** `impeccable:impeccable typeset J/feature/tasks/TasksScreen.kt J/feature/shopping/ShoppingScreen.kt J/feature/sport/SportScreen.kt J/feature/spaces/SpaceHomeSections.kt J/core/navigation/MainMenuScreen.kt`

**Ficheros:**
- Modificar:
  - `J/feature/tasks/TasksScreen.kt` (`MetaChip`, :855-861; recurrencia de plantillas, :642);
  - `J/feature/shopping/ShoppingScreen.kt` (`SupermarketBrandLine`, :871-891);
  - `J/feature/sport/SportScreen.kt` (:269, :279);
  - `J/feature/spaces/SpaceHomeSections.kt` (:41);
  - `J/core/navigation/MainMenuScreen.kt` (:66-71);
  - `J/feature/meals/RecipePicker.kt` (`RecipePickerRow`, :113-131).
- Tests:
  - crear `A/feature/meals/RecipePickerRowTest.kt`;
  - actualizar `A/feature/tasks/TaskMetaRowUiTest.kt`, si se rompe.

**Interfaces:** `RecipePickerRow` pasa de `private` a `internal` para el test.

- [ ] **Paso 1: Test que falla.** `cadaRecetaEsUnBoton`: la fila de `RecipePickerRow` tiene `Role.Button` y acción de clic.
- [ ] **Paso 2: Comprobar que falla.** Hoy no tiene rol.
- [ ] **Paso 3: Implementar.**
  - `.clickable(role = Role.Button, onClick = onClick)`, como `ExercisePicker.kt:103`.
  - **Metadatos de fila** en `bodyMedium`:
    - `MetaChip` (su icono crece a 16 dp);
    - la línea de supermercado de Compra;
    - los participantes y los ejercicios de la tarjeta de Deporte;
    - el reparto de Hoy;
    - la recurrencia de las plantillas;
    - las dos líneas de `RecipePicker`.
  - **El título del inicio** en `headlineMedium`.
  - Con letra al 130 %, ninguna fila se corta. Si alguna no cabe, se dice en el informe.
- [ ] **Paso 4: Comprobar que pasan** `RecipePickerRowTest`, `TaskMetaRowUiTest` y la puerta.

---

### Tarea 7: iconos de color en los filtros y desplegable activo (M18, M19)

**Frontend:** `impeccable:impeccable colorize J/core/designsystem/component/FilterDropdown.kt J/feature/tasks/TasksScreen.kt J/feature/meals/MealsScreen.kt`

**Ficheros:**
- Modificar:
  - `J/core/designsystem/component/FilterDropdown.kt` (`FilterOption`, :38; colores, :111-120; `FilterMenuItem`, :150-170);
  - `J/core/designsystem/component/SectionHeader.kt` (:70);
  - `J/feature/tasks/TasksScreen.kt` (`TaskFilterBar`, :490-512);
  - `J/feature/meals/MealsScreen.kt` (:358);
  - `J/feature/meals/MealTokens.kt`.
- Tests: modificar `A/core/designsystem/component/FilterDropdownTest.kt` y `A/feature/tasks/TaskFilterBarUiTest.kt`.

**Interfaces:**
- Produce:
  - `class FilterOption(val text: String, val selected: Boolean, val icon: ImageVector? = null, val iconTint: Color? = null, val onSelect: () -> Unit)`. Las llamadas con lambda final siguen compilando.
  - `const val FilterOptionIconTag = "filterOptionIcon"`, en el icono de cada opción que lo lleve.
  - `SectionHeader(…, icon: ImageVector? = null, iconTint: Color? = null)`.
  - `@Composable fun MealSlot.accent(): Color = accent(LocalDarkTheme.current)`, que se borró en la simplificación de la fase C.

- [ ] **Paso 1: Tests que fallan.**
  - En `FilterDropdownTest`:
    - `unaOpcionConIconoLoMuestra`: con dos opciones, una con icono y otra sin él, el menú abierto tiene un solo nodo `FilterOptionIconTag`.
    - `conValorElegidoElValorVaEnNegrita`: con `active = true`, el valor tiene `FontWeight.SemiBold` en `GetTextLayoutResult`; con `active = false`, no.
  - En `TaskFilterBarUiTest`, `prioridadYTipoLlevanSuIcono`:
    - el menú de Prioridad tiene un `FilterOptionIconTag` por prioridad y ninguno en «Todas»;
    - el de Tipo, uno por tipo.
- [ ] **Paso 2: Comprobar que fallan.** `FilterOption` no tiene icono.
- [ ] **Paso 3: Implementar.**
  - **El icono** va como `leadingIcon` del `DropdownMenuItem`, en las opciones simples. En las múltiples, el `leadingIcon` sigue siendo la casilla.
  - **Tareas, como antes de la fase C (commit `2e08c89^`):**
    - Prioridad lleva `Icons.Rounded.Flag` en `priority.accent()`;
    - Tipo lleva `taskType.icon` en `taskType.accent()`.
  - **Comidas:** la cabecera de franja lleva `iconTint = slot.accent()`.
  - **El desplegable activo,** según R8.
- [ ] **Paso 4: Comprobar que pasan** los tests de la tarea, `ExerciseFilterMenusUiTest`, `PeopleFilterUiTest`, `MealSlotSectionUiTest` y la puerta.

---

### Tarea 8: búsquedas y categorías de los catálogos y de Comidas (M20, M21, M22)

**Frontend:** `impeccable:impeccable harden J/feature/meals/MealsScreen.kt`

**Ficheros:**
- Modificar:
  - Ejercicios: `J/feature/exercises/ExerciseTokens.kt` (:79-81) y `J/feature/exercises/ExercisesScreen.kt` (:277-287, :406-423);
  - Recetario: `J/feature/recipes/RecipesScreen.kt` (:138-141, :338, :465);
  - categorías comunes: `J/feature/common/CategoryKeys.kt` y `J/feature/ingredients/IngredientListLogic.kt` (:58-70);
  - Comidas: `J/feature/meals/MealsScreen.kt` (:222-265, :359-377, :720-722) y `S`.
- Crear `J/feature/meals/MealSearch.kt` y `J/feature/recipes/RecipeCategories.kt`.
- Tests:
  - crear `T/feature/meals/MealSearchTest.kt`, `T/feature/recipes/RecipeCategoriesTest.kt` y `T/feature/common/CategoryKeysTest.kt`;
  - modificar `T/feature/exercises/ExerciseThumbnailTest.kt`.

**Interfaces:**
- Produce:
  - `internal fun exerciseSupportingLine(muscleGroup: String?, labels: List<String>, showMuscleGroup: Boolean): String`, en `ExerciseTokens.kt`. Une con « · ».
  - `fun <T> Iterable<T>.categoryOptions(categoryOf: (T) -> String?): List<String>`, en `CategoryKeys.kt`:
    - quita espacios y vacíos;
    - junta las que solo difieren en mayúsculas (`distinctBy { it.categoryKey() }`);
    - ordena con `CategoryCollator`.
  - `fun String?.sameCategoryAs(other: String?): Boolean`, en `CategoryKeys.kt`. Compara `categoryKey()`; null solo es igual a null.
  - `List<CatalogIngredient>.categories()` delega en `categoryOptions`.
  - `internal fun List<Recipe>.recipeCategories(): List<String>` e `internal fun Recipe.inCategory(category: String?): Boolean`, en `RecipeCategories.kt`. Con null pasan todas.
  - `internal fun visibleMealSlots(dayMeals: List<Meal>, query: String): List<Pair<MealSlot, List<Meal>>>`, en `MealSearch.kt`, junto con `Meal.matchesQuery`, que se mueve allí.

- [ ] **Paso 1: Tests que fallan.**
  - En `ExerciseThumbnailTest`, `alBuscarLaLineaSecundariaLlevaElGrupoMuscular`:
    - `("Pecho", ["Peso libre", "Mancuernas"], true)` da «Pecho · Peso libre · Mancuernas»;
    - con `false` da «Peso libre · Mancuernas»;
    - `(null, ["Cardio"], true)` y `("  ", ["Cardio"], true)` dan «Cardio».
  - En `CategoryKeysTest`, `lasOpcionesJuntanMayusculasYEspaciosYOrdenanConAcentos`: `["Postres", " postres ", "Ácidos", "Bebidas", ""]` da `["Ácidos", "Bebidas", "Postres"]`.
  - En `RecipeCategoriesTest`:
    - `filtrarPorCategoriaIgnoraMayusculasYEspacios`: una receta con `" Postres "` está en «postres».
    - `sinCategoriaElegidaPasanTodas`.
  - En `MealSearchTest`:
    - `sinBusquedaSalenLasTresFranjas`, en orden y con su lista, aunque esté vacía;
    - `alBuscarSoloSalenLasFranjasConCoincidencias`;
    - `alBuscarSinCoincidenciasNoSaleNinguna`;
    - `conElDiaSinComidasLaBusquedaNoSeAplica`: las tres franjas, vacías (Review Focus 1).
- [ ] **Paso 2: Comprobar que fallan.** Las funciones no existen.
- [ ] **Paso 3: Implementar.**
  - **Ejercicios:** la fila usa `exerciseSupportingLine` con `showMuscleGroup = layout == CatalogLayout.FLAT`, y el comentario de :277 dice lo que hace.
  - **Recetario:**
    - el desplegable usa `recipeCategories()` y el filtro usa `inCategory`;
    - la opción elegida se marca con `sameCategoryAs`;
    - el comentario de :214 se corrige si la tarea 2 no lo hizo.
  - **Comidas,** según R9:
    - la lista pinta `visibleMealSlots(dayMeals, query)`;
    - con una búsqueda sin coincidencias sale `meals_no_results`, «Ninguna comida coincide con la búsqueda.»;
    - `MealSlotSection` no cambia.
- [ ] **Paso 4: Comprobar que pasan** los tests de la tarea, `CatalogGroupsTest`, `IngredientListLogicTest`, `MealSlotSectionUiTest` y la puerta.

---

### Tarea 9: tira de días sin flechas (M12, M13)

**Frontend:** `impeccable:impeccable layout J/core/designsystem/component/WeekDayStrip.kt`

**Ficheros:**
- Modificar:
  - `J/core/designsystem/component/WeekDayStrip.kt`;
  - `J/feature/meals/MealsScreen.kt` (:153-212);
  - `J/feature/sport/SportScreen.kt` (:156-177);
  - `S`.
- Crear `J/core/designsystem/component/DayTitleButton.kt`.
- Tests:
  - modificar `A/core/designsystem/component/WeekDayStripTest.kt`;
  - crear `A/core/designsystem/component/DayTitleButtonTest.kt`.

**Interfaces:**
- Produce:
  - `WeekDayStrip` conserva su firma. `onPrevious` y `onNext` se llaman al deslizar y desde las acciones de TalkBack, y `previousLabel` y `nextLabel` son los nombres de esas acciones.
  - `@Composable fun DayTitleButton(date: LocalDate, onPick: (LocalDate) -> Unit, modifier: Modifier = Modifier)`:
    - pinta `BobitosDates.longDay(date)` en `titleMedium`, con un icono de desplegar;
    - es un botón de al menos 48 dp, con `onClickLabel` `pick_day` («Elegir día»);
    - abre `AppDatePickerDialog(initialDate = date, …)` y entrega el día elegido a `onPick`.

- [ ] **Paso 1: Tests que fallan.**
  - En `WeekDayStripTest`:
    - `cadaDiaMideAlMenos48dpDeAncho`, a 360 dp;
    - `noHayFlechas`: ningún nodo con clic y la descripción «Semana anterior» o «Semana siguiente»;
    - `deslizarALaIzquierdaPasaALaSemanaSiguiente` (`swipeLeft()`, que llama a `onNext`);
    - `deslizarALaDerechaVuelveALaAnterior`;
    - `unArrastreCortoNoCambiaDeSemana`: un arrastre de 24 dp no llama a ninguno (Review Focus 4);
    - `TalkBackTieneSemanaAnteriorYSiguiente`: la tira tiene las dos `customActions` con esos nombres, y lanzarlas llama a `onPrevious` y `onNext`;
    - el `show()` del test deja de depender de las flechas.
  - En `DayTitleButtonTest`, `laFechaEsUnBotonQueAbreElCalendario`: tiene `Role.Button` y, al tocarla, se ve el diálogo de fecha.
- [ ] **Paso 2: Comprobar que fallan.** Las flechas siguen y no existe `DayTitleButton`.
- [ ] **Paso 3: Implementar** según R5.
  - Comidas y Deporte cambian el `Text` de la fecha por `DayTitleButton(state.focusedDate, viewModel::selectDay)`. En Comidas, «Más opciones» sigue al lado.
  - Los tests que ya existen de la tira siguen pasando: rol de opción única, «hoy», nombre completo y 48 dp de alto.
- [ ] **Paso 4: Comprobar que pasan** los tests de la tarea, `MealsViewModelTest` y la puerta.

---

### Tarea 10: celebración sin bloquear, «Quitar animaciones» en vivo y cascada por día (M10, M11, M24)

**Frontend:** `impeccable:impeccable animate J/core/designsystem/component/Celebration.kt J/core/designsystem/component/CompletingItems.kt`

**Ficheros:**
- Modificar:
  - `J/core/designsystem/component/Celebration.kt` (:166-185);
  - `J/core/designsystem/Motion.kt` (:30-36);
  - `J/core/designsystem/theme/Theme.kt` (`BobitosTheme`);
  - `J/core/designsystem/component/CompletingItems.kt` (`cascadeIn`, :178-192);
  - `J/feature/meals/MealsScreen.kt` (:236-239);
  - `J/feature/sport/SportScreen.kt` (:200-202).
- Tests:
  - crear `A/core/designsystem/component/CelebrationUiTest.kt`, `T/core/designsystem/ReduceMotionTest.kt` y `T/core/designsystem/DayCascadeTest.kt`;
  - mantener `T/core/designsystem/MotionTest.kt`.

**Interfaces:**
- Produce:
  - `val LocalReduceMotion = compositionLocalOf { false }`, en `Motion.kt`. Lo provee `BobitosTheme`.
  - `@Composable fun rememberReduceMotion(): Boolean = LocalReduceMotion.current`. Sus ocho llamadas no cambian.
  - `internal fun reduceMotionUpdates(readScale: () -> Float, changes: Flow<Unit>): Flow<Boolean>`:
    - emite `readScale() == 0f` al empezar y después de cada cambio;
    - lleva `distinctUntilChanged`.
  - `internal class DayCascade { fun onContentShown(day: LocalDate): Boolean }`, en `CompletingItems.kt`. Da true solo si `day` es distinto del último día mostrado, y nunca la primera vez.
  - `@Composable fun rememberDayCascade(day: LocalDate, ready: Boolean): Boolean`. Es true durante `CASCADE_WINDOW_MS` (1.200 ms) desde que se muestra el contenido de un día nuevo. La ventana se mueve a `CompletingItems.kt`.

- [ ] **Paso 1: Tests que fallan.**
  - `CelebrationUiTest.laCelebracionNoTapaLosToques`: un botón centrado bajo `Celebration(visible = true, …)` recibe el toque mientras la píldora se ve.
  - `ReduceMotionTest.cambiarLaEscalaConLaAppAbiertaSeNota`: con una escala que pasa de 1f a 0f y un `MutableSharedFlow<Unit>` que emite el cambio, el flujo da `[false, true]`. Con una emisión sin cambio de escala, no repite.
  - `DayCascadeTest`:
    - `laPrimeraCargaNoCascada`;
    - `elMismoDiaOtraVezNoCascada` (alta en tiempo real o fila que vuelve a la vista);
    - `otroDiaCascada`;
    - `volverAlDiaAnteriorCascada`.
- [ ] **Paso 2: Comprobar que fallan.** La píldora se traga el toque y las funciones no existen.
- [ ] **Paso 3: Implementar** según R11 y R12.
  - **La píldora** deja de ser un `Surface` sin clic, que intercepta los toques. Pasa a un contenedor que no consume eventos, con la misma forma, color y sombra. TalkBack la sigue anunciando con cortesía.
  - **El `ContentObserver`** se registra y se quita con `DisposableEffect`, en `BobitosTheme`.
  - **Comidas y Deporte** pasan `play = rememberDayCascade(state.focusedDate, ready = <contenido del día cargado>)` a `cascadeIn`.
- [ ] **Paso 4: Comprobar que pasan** los tests de la tarea, `CelebrationTest`, `MotionTest`, `CompletingItemsTest` y la puerta.

---

### Tarea 11: Hoy, textos de fecha, nombres vacíos y error de otro espacio (M6, M7, M8)

**Frontend:** `impeccable:impeccable clarify J/core/navigation/SpaceTodayScreen.kt`

**Ficheros:**
- Modificar:
  - `J/core/common/BobitosDates.kt`;
  - `J/feature/spaces/TodayAgenda.kt` (:37-38, :132-142, :186-190);
  - `J/core/navigation/SpaceTodayScreen.kt` (:228-282, :420-428);
  - `J/feature/spaces/SpaceHomeViewModel.kt` (:68-70, :148-157);
  - `J/core/navigation/BobitosNavHost.kt` (:304);
  - `S` (:68).
- Tests: modificar `T/core/common/BobitosDatesTest.kt`, `T/feature/spaces/TodayAgendaTest.kt`, `T/feature/spaces/SpaceHomeViewModelTest.kt` y `A/core/navigation/SpaceTodayScreenTest.kt`.

**Interfaces:**
- Produce:
  - `fun BobitosDates.dayMonth(date: LocalDate, today: LocalDate): String`, con las tablas españolas de `BobitosDates`: «12 de octubre», y «30 de diciembre de 2025» si el año no es el de `today`.
  - `AgendaPerson.Unknown` (`data object`), que se lee «Otro miembro» (`agenda_unknown_member`).
  - `data class ScopedAgenda(val spaceId: String?, val state: UiState<TodayAgenda>)` y `fun ScopedAgenda.forSpace(spaceId: String?): UiState<TodayAgenda>`. Da `Loading` si el espacio no es el pedido, sea cual sea el estado. Sustituye al `forSpace` de `UiState<TodayAgenda>`.
  - `SpaceHomeViewModel.agenda: StateFlow<ScopedAgenda>`, con el `spaceId` de su `Request`, también en `Loading` y en `Error`.
  - `agenda_empty` pasa a «Sin eventos ni deporte hoy», sin punto, como las demás líneas de Hoy (R14 de la fase C).

- [ ] **Paso 1: Tests que fallan.**
  - En `BobitosDatesTest`:
    - `dayMonthSinAnioSiEsEsteAnio`: 12-10-2026, visto el 18-10-2026, da «12 de octubre».
    - `dayMonthConAnioSiEsDeOtroAnio`: 30-12-2025, visto el 01-01-2026, da «30 de diciembre de 2025» (Review Focus 5).
  - En `TodayAgendaTest`, `unNombreVacioNoDejaHuecos`:
    - una tarea de otro miembro con `assigneeName = ""` y un id que no está en los miembros da `owner == AgendaPerson.Unknown`;
    - con ese id en los miembros, da su nombre;
    - un evento con nombres `["Ana", ""]` da `[Member("Ana")]`;
    - una comida con `cookName = ""` no lleva cocinero.
  - En `SpaceHomeViewModelTest`:
    - `unErrorDeOtroEspacioSeMuestraComoCargando`: un `Error` del espacio A, pedido para B, da `Loading`.
    - Se adaptan `unaAgendaDeOtroEspacioSeMuestraComoCargando` y `unFalloDaErrorYReintentarVuelveASuscribir`.
  - En `SpaceTodayScreenTest`:
    - `diaVacioDiceSinEventosNiDeporte`;
    - `unaTareaVencidaDiceCuandoVencio`: con `overdueSince` hace 6 días, se lee «Venció el …» con `dayMonth`;
    - `ayerDiceVencioAyer`.
- [ ] **Paso 2: Comprobar que fallan.** Las funciones y el tipo no existen, y el texto es el viejo.
- [ ] **Paso 3: Implementar** según R6.
  - `overdueText` usa `dayMonth`. Sale el `getDisplayName` con «de» fijo.
  - La línea de la tarea une con « · » solo las partes no vacías.
- [ ] **Paso 4: Comprobar que pasan** los tests de la tarea, `TodayGreetingTest` y la puerta.

---

### Tarea 12: Deshacer al completar avisa si otro cambió la tarea (M9)

**Frontend:** ninguno. Solo cambia un mensaje.

**Ficheros:**
- Modificar:
  - `J/data/repository/TaskRepository.kt` (`TaskFailure`, :59-71);
  - `J/data/repository/FirestoreTaskRepository.kt` (`restoreCompletion`, :182-201);
  - `J/feature/tasks/TasksViewModel.kt` (:256) y `J/feature/tasks/TasksUiState.kt`;
  - `J/feature/tasks/TasksScreen.kt` (`stringRes`, :1073);
  - `S`.
- Crear `J/data/repository/TaskCompletionUndo.kt`.
- Tests:
  - crear `T/data/repository/TaskCompletionUndoTest.kt`;
  - modificar `T/feature/tasks/TasksViewModelTest.kt` y `T/feature/spaces/SpaceHomeViewModelTest.kt`, con un fallo programable para `restoreCompletion` en su doble.

**Interfaces:**
- Produce:
  - `TaskFailure.TaskChanged`.
  - `internal fun completionUndoFailure(current: TaskItem, previous: TaskItem, userId: String): TaskFailure?`. Devuelve null si se puede deshacer y `TaskChanged` en los casos de R7.
  - `TaskUiMessage.TaskChanged` → `tasks_error_changed`, «La tarea ha cambiado entretanto y no se ha deshecho.».
- Consume: `restoreCompletion(spaceId, previous)`, con la misma firma.

- [ ] **Paso 1: Tests que fallan.**
  - En `TaskCompletionUndoTest`:
    - `sinTocarSePuedeDeshacer`: una normal hecha por el usuario, y una que se repite y sigue pendiente con fechas adelantadas por el usuario, dan null.
    - `otroMiembroLaTocoNoSeDeshace`: `updatedBy` de otro da `TaskChanged`.
    - `unaNormalReabiertaNoSeDeshace`.
    - `unCambioDeTituloNoSeDeshace`, y lo mismo con la prioridad y la repetición.
  - En `TasksViewModelTest` y en `SpaceHomeViewModelTest`, `deshacerUnaTareaQueOtroCambioAvisa`: si `restoreCompletion` lanza `TaskRepositoryException(TaskChanged)`, el error es `TaskUiMessage.TaskChanged`.
- [ ] **Paso 2: Comprobar que fallan.** `TaskChanged` no existe.
- [ ] **Paso 3: Implementar.**
  - En la transacción de `restoreCompletion`, el documento leído pasa por `toTaskItem` y por `completionUndoFailure`.
  - Con un fallo, se lanza `TaskRepositoryException` y no se escribe nada.
  - Si no hay fallo, la escritura es la misma de hoy, así que `firestore.rules` no cambia.
- [ ] **Paso 4: Comprobar que pasan** los tests de la tarea y la puerta.

---

### Tarea 13: «Reintentar» en la lista de espacios del inicio (M23)

**Frontend:** `impeccable:impeccable harden J/core/navigation/MainMenuScreen.kt`

**Ficheros:**
- Modificar:
  - `J/app/AppViewModel.kt` (:99-107, :170-189);
  - `J/core/navigation/MainMenuScreen.kt` (:55-62, :128-131);
  - `J/core/navigation/BobitosNavHost.kt` (:81, :207-221);
  - `J/app/BobitosApp.kt` (:31);
  - `J/MainActivity.kt` (~:72-77).
- Tests: modificar `T/app/AppViewModelTest.kt` (con un `FakeAppSpaceRepository` que falla y después responde) y `A/core/navigation/MainMenuScreenTest.kt`.

**Interfaces:**
- Produce:
  - `fun AppViewModel.retrySpaces()`. Suma un intento a la petición observada (un `attempt` en `ObservationRequest`, como `SpaceHomeViewModel.Request`), y la lista se vuelve a suscribir.
  - `MainMenuScreen(…, onRetrySpaces: () -> Unit, …)`.
  - `BobitosNavHost(…, onRetrySpaces: () -> Unit, …)`.
  - `BobitosApp(…, onRetrySpaces, …)`.
  - `MainActivity` pasa `viewModel::retrySpaces`.

- [ ] **Paso 1: Tests que fallan.**
  - `AppViewModelTest.reintentarTrasUnFalloVuelveACargarLosEspacios`: la primera suscripción falla (`Error`) y, tras `retrySpaces()`, la lista llega (`Content`).
  - `MainMenuScreenTest.siLaListaFallaOfreceReintentar`: con `UiState.Error`, «Reintentar» es un botón y tocarlo llama a `onRetrySpaces`. «Gestionar» sigue al alcance.
- [ ] **Paso 2: Comprobar que fallan.** `retrySpaces` no existe.
- [ ] **Paso 3: Implementar.** `ErrorState(…, onRetry = onRetrySpaces)` en `SpacesRows`.
- [ ] **Paso 4: Comprobar que pasan** los tests de la tarea, los demás de `AppViewModelTest` y `MainMenuScreenTest`, y la puerta.

---

## Cierre (paso 6 del método)

- En `docs/superpowers/backlog.md`, sección «2026-10-18 — `agent/evolucion-visual-fase-c`», los 25 menores visibles se marcan resueltos con la rama `agent/menores-evolucion-visual-fase-c`, en el commit del cierre.
- Los menores nuevos de la revisión final, de la revisión de diseño y del `code-review` van en una sección nueva con la fecha y esta rama.
