# Evolución visual · Fase C (identidad) — plan de implementación

> **Para agentes:** se ejecuta con la skill `delegating-plan-execution` del usuario. El marco es superpowers:executing-plans, con un implementador por tarea, en serie y en el orden del plan. Los pasos usan casillas (`- [ ]`). Los commits los hace el coordinador.

**Objetivo:**
- Cada módulo es dueño de su color: cabecera teñida, FAB, selección y enlaces.
- Hoy es una agenda de toda la casa.
- Los catálogos se recorren en filas agrupadas por categoría.
- Hay un solo tipo de filtro, el desplegable con su nombre.
- El título grande es la pantalla.
- El movimiento da vida sin quitar velocidad.

**Arquitectura:**
- **El color del módulo entra como tema.** `ModuleTheme(module)` sustituye los roles de acento de Material por los del módulo en la pantalla y en sus hojas. Los componentes de Material lo heredan sin pasar colores a mano.
- **Componentes comunes nuevos** en `core/designsystem/component`:
  - la barra grande que se encoge, con la franja teñida;
  - el desplegable de filtro, simple y múltiple;
  - las filas agrupadas;
  - la celebración.
- **Lógica pura con test JVM** para lo que decide qué se pinta:
  - la agenda de Hoy;
  - la agrupación de los catálogos;
  - el estado vacío de Tareas;
  - la condición de la celebración;
  - el tipo de transición de navegación;
  - el FAB que se recoge;
  - el submuestreo de las miniaturas.
- **Deshacer al completar:** un método nuevo del repositorio de tareas devuelve en una sola escritura el estado y las fechas que la tarea tenía antes de completarla.

**Tecnología:** Kotlin 2.3.10, Jetpack Compose (BOM 2026.06.00: material3 1.4.0, foundation 1.11.3), Hilt y Firestore.
- Tests con JUnit 4 (JVM) y Compose instrumentados (`createAndroidComposeRule<ComponentActivity>()`).
- Tests de reglas en el emulador (`tests/firebase-emulators.test.mjs`).

**Spec:**
- `docs/superpowers/specs/2026-10-18-evolucion-visual-fase-c-design.md` (apartados C1–C12).
- Programa: `docs/superpowers/specs/2026-10-17-evolucion-visual-design.md`.
- Maquetas: `docs/superpowers/specs/2026-10-18-evolucion-visual-fase-c-maquetas/` (una por fichero, con la opción elegida anotada).
- Contexto de producto: `PRODUCT.md`.

## Restricciones globales

**Rutas**
- `J/` = `app/src/main/java/com/dlunaunizar/bobitos/`.
- `T/` = `app/src/test/java/com/dlunaunizar/bobitos/` (JVM).
- `A/` = `app/src/androidTest/java/com/dlunaunizar/bobitos/` (instrumentados).
- `S` = `app/src/main/res/values/strings.xml`.

**Entorno y comandos**
- **Java:** antes de cada `./gradlew` o `npm run test:emulators`, ejecutar `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- **Test JVM suelto:** `./gradlew :app:testDebugUnitTest --tests "<FQCN>"`.
- **Test instrumentado suelto:** `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`.
  - Necesita un emulador encendido: `export PATH="$HOME/Library/Android/sdk/emulator:$HOME/Library/Android/sdk/platform-tools:$PATH"; nohup emulator -avd Pixel_10 -no-boot-anim >/dev/null 2>&1 & adb wait-for-device`.
  - El emulador está en inglés (`en-US`).
- **Tests de reglas:** `npm run test:emulators`.
- **Puerta de cada tarea:** `./gradlew ktlintCheck detekt testDebugUnitTest assembleDebug lintDebug`, más los tests instrumentados de la propia tarea. En la tarea 14, también `npm run test:emulators`.
  - `lintDebug` ya falla en `main` por `RestrictedApi currentBackStack` en `BobitosNavHost`. Ese error, y solo ese, se tolera.
  - Si un cambio de firma rompe `config/detekt/baseline.xml`, se regenera con `./gradlew detektBaseline` y se incluye en la tarea.
  - Los tests instrumentados que ya existen y tocan un fichero de la tarea se actualizan en esa tarea y deben pasar.

**Textos**
- Los nuevos van en `S`, en español, con tuteo y punto final en las frases completas.
- Los conteos, con `<plurals>`.
- Las cadenas que se quedan sin uso se borran.

**Componentes y color**
- Material 3 reales, sin controles hechos a mano que imiten uno de Material.
- El color de una pantalla de módulo sale de `ModuleTheme` (tarea 2): `MaterialTheme.colorScheme.primary`, `primaryContainer`, `secondaryContainer` y sus «on». Ningún componente nuevo lee `ModulePalette` directamente, salvo el tinte (`moduleTint`) y la celebración.
- Las pantallas globales (inicio, Tus espacios, Perfil, Ajustes del espacio y acceso) no llevan `ModuleTheme` ni franja.
- **Tema:** el oscuro sigue siendo el de por defecto. Todo cambio visual se comprueba en claro y en oscuro.

**Tipografía (spec C9)**
- Título de pantalla: `headlineMedium`.
- Cabecera de sección: `SectionHeader` (`titleSmall`).
- Título de una fila: `bodyLarge`.
- Línea secundaria: `bodyMedium` en `onSurfaceVariant`.
- Sin `fontSize` literales.

**Tamaño:** destinos táctiles de 48 dp. Con letra al 130 %, nada se corta.

**Movimiento:** toda animación nueva pasa por `motionSpec` (tarea 19), o comprueba `rememberReduceMotion()`. Con «Quitar animaciones» es instantánea. Ninguna escritura se retrasa por una animación.

**Verificación visual (spec, «Restricciones»):** la revisión de diseño que va después del paso 3 del método incluye capturas en el emulador Pixel 10 de las pantallas de esta fase, en oscuro, en claro y con la letra al 130 % (`adb shell settings put system font_scale 1.3`, y volver a `1.0` al terminar). Las compara con las maquetas.

**Alcance**
- Sin dependencias nuevas y sin cambios en `firestore.rules`.
- No se toca la fase D: adaptividad, rendimiento salvo las miniaturas, `Spacing` y atrás predictivo.

**Frontend**
- **Las skills de diseño** las invoca el implementador, con el subcomando de la línea `Frontend:` de su tarea. No hay nadie a quien preguntar: si la skill pide elegir o confirmar algo, decide con la «Dirección visual» de la spec, las maquetas y `PRODUCT.md`, y anota en el informe lo que decidiste.
- **Límite de ficheros:** la skill solo cambia los ficheros de la tarea.
- **Este plan no hace trabajo nuevo de impeccable** (R1): no se crea `DESIGN.md`.
- Las carpetas de trabajo de las skills ya están en `.gitignore`.

## Rulings de la planificación

- **R1. Subcomandos concretos de impeccable, sin trabajo nuevo.** El trabajo nuevo abre el descubrimiento del mundo visual (`WORLD_DISCOVERY_REQUIRED`), que pregunta al usuario y se degrada dentro de un subagente. La dirección ya está decidida con maquetas, así que cada tarea usa `layout`, `colorize`, `typeset`, `extract`, `harden` o `animate`.
- **R2. El calendario no se desplaza con la lista.** En Calendario y Mi calendario, la cabecera del periodo, el selector Día/Semana/Mes y el filtro de Personas o de Espacios se quedan fijos en la franja, como la tira de días de Comidas y Deporte. El calendario es navegación, no una lista.
- **R3. Un evento que empezó antes de hoy y sigue** va en la agenda con la etiqueta «Sigue hoy». Va después de «Sin hora» y antes de lo que tiene hora. La spec no le daba etiqueta.
- **R4. El valor del desplegable múltiple** es «Todas» (o «Todos»), el nombre si hay uno elegido, o «N personas» («N espacios»). No hay opción «Ninguna»: elegir «Todas» vuelve a todas.
- **R5. Desempates de la agenda:**
  - a la misma hora, primero la comida, después el evento y después la actividad, y dentro de cada uno por título;
  - las tareas de hoy, por hora de vencimiento y después por título.
- **R6. Un solo «hoy» que cambia a medianoche.** `CurrentDayProvider` pasa de `feature/spaces` a `core/common` (tarea 15). Las pantallas lo leen con `rememberToday()` (tarea 17).
- **R7. La pastilla del día que se desliza** va en la tarea de movimiento (19). La tarea 17 deja en la tira de días solo los cambios de accesibilidad y contraste.

## Review Focus

1. **Una tarea asignada a alguien que ya no está en el espacio** sale en Hoy con el nombre guardado en la tarea (`assigneeName`), sin romper ni decir «Sin asignar». Lo prueba la tarea 15.
2. **Cambiar de espacio con Hoy abierto** no mezcla datos: la agenda del espacio anterior no se ve ni un instante bajo el nuevo nombre. Lo prueba la tarea 15.
3. **Deshacer una tarea completada que otro miembro ha borrado entretanto** avisa con un snackbar de texto humano y no se queda en silencio. Lo prueba la tarea 14.
4. **Marcar varias tareas o productos muy seguidos:** cada toque se escribe al momento, y la animación de salida no se come ninguno. Lo prueba la tarea 20.
5. **Un nombre de espacio largo con letra al 130 %** («Piso de estudiantes de Zaragoza») no tapa ni corta el título grande de la pantalla. Lo prueba la tarea 3.

---

### Tarea 1: paleta de siete módulos y tinte (C1)

**Frontend:** `impeccable:impeccable colorize J/core/designsystem/theme/ModuleColors.kt`

**Ficheros:**
- Modificar `J/core/designsystem/theme/ModuleColors.kt` y `J/core/designsystem/theme/CategoryTint.kt`.
- Modificar `J/core/navigation/BobitosDestination.kt` (`appModule()`, ~:147).
- Tests:
  - modificar `T/core/designsystem/theme/ModuleColorsTest.kt`;
  - crear `T/core/navigation/DestinationModuleTest.kt`.

**Interfaces:**
- Produce:
  - `enum class AppModule { SHOPPING, TASKS, CALENDAR, MEALS, SPORT, NOTES, PANTRY }`.
  - `data class ModuleColors(val accent: Color, val onAccent: Color, val container: Color, val onContainer: Color)`. `onAccent` es el color del texto o del icono sobre el acento usado como relleno.
  - `fun ModulePalette.tint(module: AppModule, dark: Boolean): Color`. Es opaco: el acento con alfa 0,16 en oscuro y 0,12 en claro, `compositeOver` del fondo (`backgroundDark` o `backgroundLight`).
  - `@Composable fun moduleTint(module: AppModule): Color`.
  - `internal fun BobitosDestination.appModule(): AppModule?`:
    - Shopping → SHOPPING; Tasks → TASKS;
    - Calendar y MyCalendar → CALENDAR;
    - Meals, Recipes e Ingredients → MEALS;
    - Sport, Exercises y Routines → SPORT;
    - Notes → NOTES; Pantry → PANTRY;
    - el resto → null.
- Consume: nada nuevo.

- [ ] **Paso 1: Tests que fallan.**
  - En `ModuleColorsTest`, para los dos temas y los siete módulos:
    - `losSieteModulosTienenColor`: `AppModule.entries` contiene NOTES y PANTRY y todos dan colores.
    - `onContainerSeLeeSobreContainer`: ≥ 4,5. Ya existe: se amplía.
    - `elAcentoSeLeeComoTextoSobreElFondoYSobreSuTinte`: ≥ 4,5 contra el fondo y contra `ModulePalette.tint`.
    - `loQueVaSobreElAcentoSeLee`: `onAccent` sobre `accent` ≥ 4,5.
    - `elTextoSeLeeSobreElTinte`: `onSurface` y `onSurfaceVariant` del tema sobre el tinte ≥ 4,5.
    - `elAcentoComoContornoSeVe`: el acento sobre el fondo y sobre el tinte ≥ 3.
    - Usa `contrast` de `T/core/designsystem/theme/ContrastMath.kt`.
  - En `DestinationModuleTest`, `cadaDestinoUsaElColorDeSuModulo`: la tabla de `appModule()` de arriba, incluidos los null de Home, Spaces, SpaceHome, SpaceMore, Profile y SpaceSettings.
- [ ] **Paso 2: Comprobar que fallan.** `./gradlew :app:testDebugUnitTest --tests "*.ModuleColorsTest" --tests "*.DestinationModuleTest"` falla al compilar: no existen NOTES, PANTRY ni `onAccent`.
- [ ] **Paso 3: Implementar.**
  - Añadir Notas y Despensa con los valores orientativos de la spec (C1).
  - Darle a cada módulo su `onAccent`.
  - Oscurecer los acentos claros que no lleguen a 4,5 dentro de su familia de tono. El Calendario claro puede pasar a `#0F766E`.
  - Ajustar la tabla de `appModule()`.
  - `WorkspaceNavigationBar` sigue funcionando sin cambios, porque solo usa `appModule()` en las cinco pestañas.
- [ ] **Paso 4: Comprobar que pasan** los dos tests y la puerta.
- [ ] **Paso 5: Informe** con los valores finales de los siete módulos en claro y en oscuro.

---

### Tarea 2: tema de módulo (C3)

**Frontend:** `impeccable:impeccable colorize J/core/designsystem/theme/ModuleTheme.kt J/core/designsystem/component/ScreenScaffold.kt J/core/navigation/WorkspaceScaffold.kt`

**Ficheros:**
- Crear `J/core/designsystem/theme/ModuleTheme.kt`.
- Modificar:
  - `J/core/designsystem/component/ScreenScaffold.kt` (`ScreenScaffold`, :28);
  - `J/core/navigation/WorkspaceScaffold.kt` (:55);
  - `J/core/navigation/BobitosNavHost.kt` (`SpaceScaffold`, :603, y sus llamadas en :268-:421);
  - las llamadas a `ScreenScaffold` de los módulos: `J/feature/recipes/RecipesScreen.kt:143`, `J/feature/ingredients/IngredientsScreen.kt:100`, `J/feature/ingredients/IngredientDetailScreen.kt:108`, `J/feature/exercises/ExercisesScreen.kt:99`, `J/feature/routines/RoutinesScreen.kt:98`, `J/feature/notes/NotesScreen.kt:91`, `J/feature/pantry/PantryScreen.kt:70` y `J/feature/calendar/PersonalCalendarScreen.kt:128`.
- Tests:
  - crear `T/core/designsystem/theme/ModuleThemeTest.kt` (JVM);
  - crear `A/core/designsystem/theme/ModuleThemeUiTest.kt`.

**Interfaces:**
- Consume: `ModuleColors`, `ModulePalette` y `LocalDarkTheme` (tarea 1).
- Produce:
  - `fun moduleColorScheme(base: ColorScheme, colors: ModuleColors): ColorScheme`. Es pura y copia `base` con:
    - `primary = accent` y `onPrimary = onAccent`;
    - `primaryContainer = container` y `onPrimaryContainer = onContainer`;
    - `secondaryContainer = container` y `onSecondaryContainer = onContainer`;
    - `surfaceTint = accent`.
  - `val LocalAppModule = compositionLocalOf<AppModule?> { null }`.
  - `@Composable fun ModuleTheme(module: AppModule?, content: @Composable () -> Unit)`. Con null no cambia nada. Con un módulo, provee `LocalAppModule` y un `MaterialTheme` con `moduleColorScheme`, y conserva la tipografía y las formas.
  - `ScreenScaffold(..., module: AppModule? = null, ...)` envuelve todo el `Scaffold` en `ModuleTheme(module)`.
  - `WorkspaceScaffold(..., module: AppModule?, ...)` envuelve la barra superior y el contenido, **no** la barra inferior, en `ModuleTheme(module)`.
  - `SpaceScaffold` pasa: Shopping → SHOPPING, Tasks → TASKS, Calendar y la ruta de evento → CALENDAR, Meals → MEALS, Sport → SPORT, y SpaceHome y SpaceMore → null.

- [ ] **Paso 1: Tests que fallan.**
  - `ModuleThemeTest.mapeaLosRolesDeAcentoAlModulo`: con un `ColorScheme` de prueba y los colores de MEALS, los seis roles de arriba toman los del módulo y `background`, `surface` y `error` no cambian.
  - `ModuleThemeUiTest`:
    - `unaPantallaDeModuloUsaSuColor`: con `ScreenScaffold(module = MEALS)` y con `module = TASKS`, el contenido guarda `MaterialTheme.colorScheme.primary` y `primaryContainer`. Son el `accent` y el `container` de cada módulo: el `primaryContainer` es el color del FAB.
    - `unaPantallaGlobalConservaLaMarca`: con `module = null`, `primary` es el de la marca.
    - `unaHojaAbiertaDesdeElModuloHeredaSuColor`: un `ModalBottomSheet` dentro del contenido ve el mismo `primary`.
- [ ] **Paso 2: Comprobar que fallan.** El test JVM no compila. El instrumentado falla sin el parámetro `module`.
- [ ] **Paso 3: Implementar** `ModuleTheme` y el parámetro en los dos marcos. Pasar el módulo en las ocho llamadas a `ScreenScaffold`:
  - Recetario e Ingredientes (y su detalle): MEALS;
  - Ejercicios y Rutinas: SPORT;
  - Notas: NOTES;
  - Despensa: PANTRY;
  - Mi calendario: CALENDAR.

  El FAB (`primaryContainer`), las casillas (`primary`), el botón relleno de las hojas (`primary`/`onPrimary`), el selector Día/Semana/Mes y la tira de días (`secondaryContainer`) toman el color sin más cambios.
- [ ] **Paso 4: Comprobar que pasan** los tests y la puerta.

---

### Tarea 3: cabecera de módulo — título grande, franja teñida y selector de espacio (C2)

**Frontend:** `impeccable:impeccable layout J/core/designsystem/component/BobitosLargeTopBar.kt J/core/navigation/WorkspaceScaffold.kt J/core/designsystem/component/ScreenScaffold.kt`

**Ficheros:**
- Crear:
  - `J/core/designsystem/component/BobitosLargeTopBar.kt`;
  - `J/core/designsystem/component/ModuleHeaderBand.kt`;
  - `J/core/designsystem/component/TopBarActions.kt`.
- Modificar:
  - `J/core/navigation/WorkspaceScaffold.kt`: quitar `SpaceTitle`, :169-202;
  - `J/core/designsystem/component/ScreenScaffold.kt`;
  - `J/core/designsystem/component/BobitosTopBar.kt`, si se queda sin uso en pantallas con franja;
  - `J/core/navigation/BobitosNavHost.kt` (títulos que se pasan a `SpaceScaffold`);
  - `J/feature/tasks/TasksScreen.kt`: «Plantillas», `templatesAction` en :195-212 y :291-301;
  - `S`.
- Tests:
  - crear `A/core/navigation/WorkspaceScaffoldTest.kt`;
  - actualizar `A/core/designsystem/component/ScreenScaffoldTest.kt`.

**Interfaces:**
- Consume: `LocalAppModule` y `moduleTint` (tareas 1 y 2).
- Produce:
  - `@Composable fun BobitosLargeTopBar(title: String, scrollBehavior: TopAppBarScrollBehavior, modifier: Modifier = Modifier, overline: (@Composable () -> Unit)? = null, supportingText: String? = null, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {})`.
    - Es una barra de Material 3 que se encoge; el implementador elige la variante mediana o grande.
    - Abierta, de arriba abajo: `overline` (el selector del espacio), `supportingText` (el saludo de Hoy) y el título en `headlineMedium`.
    - Encogida, el título va en `titleLarge`, en una línea.
    - El color del contenedor, abierta y encogida, es `moduleTint(LocalAppModule)` si hay módulo; si no, `surface`.
    - El título es `heading()`.
  - `@Composable fun ModuleHeaderBand(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)`: el mismo tinte, con las esquinas de abajo redondeadas (unos 20 dp). Sin módulo, no tiene fondo.
  - En `TopBarActions.kt`:
    - `class TopBarActionsState { var actions: (@Composable RowScope.() -> Unit)? }`, respaldado por `mutableStateOf`;
    - `val LocalTopBarActions = staticCompositionLocalOf<TopBarActionsState?> { null }`;
    - `@Composable fun TopBarActions(content: @Composable RowScope.() -> Unit)`, que registra `content` mientras está en composición (`DisposableEffect`).
  - `WorkspaceScaffold(selectedTab: BobitosDestination, title: String, module: AppModule?, spaceName: String, onTabSelected: (BobitosDestination) -> Unit, switcher: SpaceSwitcher, onManageSpaces: () -> Unit, onSpaceSettings: () -> Unit, onProfile: () -> Unit, syncStatus: SyncStatus, supportingText: String? = null, content: @Composable () -> Unit)`.
    - **El selector del espacio** va en `overline`: el nombre en `labelLarge` y una flecha. Es un botón con la descripción `change_space_named` («Cambiar de espacio, %1$s»), abre el selector como hoy y ocupa como mucho dos líneas.
    - **Las acciones** son, por orden, las registradas con `TopBarActions`, después ajustes y después perfil.
    - **El `Scaffold`** lleva `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)`.
  - `ScreenScaffold` usa `BobitosLargeTopBar` con `onBack` y el mismo `nestedScroll`.
- **Títulos que pasa `SpaceScaffold`:**
  - el de cada pestaña o módulo («Calendario», «Tareas», «Compra», «Comidas», «Deporte»);
  - Más: «Más» (`more_title`);
  - Hoy: «Hoy», de momento. La tarea 16 lo cambia por la fecha y el saludo.

- [ ] **Paso 1: Tests que fallan.**
  - En `WorkspaceScaffoldTest`, montando `WorkspaceScaffold` con `title = "Tareas"`, `spaceName = "Casa Bobitos"` y `module = TASKS`:
    - `elTituloEsLaPantallaYEsEncabezado`: un nodo con el texto «Tareas» y `heading()`.
    - `elEspacioEsUnSelector`: un nodo con la descripción «Cambiar de espacio, Casa Bobitos», `Role.Button` y acción de clic. Al pulsarlo se abre el selector (`switcher.onPickerOpenChange(true)`).
    - `lasAccionesDeLaPantallaVanEnLaBarra`: el contenido registra `TopBarActions { IconButton(contentDescription = "Plantillas de tareas") }`, y el botón aparece en la barra.
    - `conLetraGrandeYUnEspacioLargoNadaSeCorta` (Review Focus 5): escala 1,3, ancho 360 dp, «Piso de estudiantes de Zaragoza» y el título «Calendario». El título no tiene desbordamiento visual (`TextLayoutResult.hasVisualOverflow == false`) y sus límites no se solapan con los del selector.
  - En `ScreenScaffoldTest`, `conFlechaAtrasYTituloGrande`: el título es encabezado y la flecha «Atrás» llama a `onBack`.
- [ ] **Paso 2: Comprobar que fallan.**
- [ ] **Paso 3: Implementar** los componentes y los dos marcos.
  - **La franja termina siempre con las esquinas de abajo redondeadas**, como en las maquetas 3 y 7. Si la pantalla tiene filtros o tira de días, el redondeo es el de su `ModuleHeaderBand`. Si no los tiene (Notas, Despensa, Mi calendario y, de momento, las demás), el marco lo dibuja bajo la barra. Cómo se dibuja lo decide el implementador.
  - Las tareas 6, 7, 11 y 17 ponen los filtros, la búsqueda y la tira de días de sus pantallas dentro de la franja. Las búsquedas de Notas y Despensa se quedan donde están, debajo de la franja.
  - En `TasksScreen`, quitar «Plantillas» de la primera cabecera y de la barra de filtros. Registrar con `TopBarActions` un `IconButton` (icono `Bolt`, descripción `tasks_templates_open`, «Plantillas de tareas»), solo con `canWrite`, que abre las plantillas como hoy.
  - Las cadenas `tasks_templates` que se queden sin uso se borran.
- [ ] **Paso 4: Comprobar que pasan** los tests de la tarea y la puerta.

---

### Tarea 4: escala tipográfica y hoja de detalle con letra grande (C9, C12)

**Frontend:** `impeccable:impeccable typeset J/core/designsystem/theme/Type.kt J/core/designsystem/component/SectionHeader.kt J/core/designsystem/component/BobitosInfoSheet.kt`

**Ficheros:**
- Modificar:
  - `J/core/designsystem/theme/Type.kt` (:31);
  - `J/core/designsystem/component/SectionHeader.kt` (:32);
  - `J/core/designsystem/component/BobitosInfoSheet.kt` (fila de botones, :80-88).
- Test: modificar `A/core/designsystem/component/BobitosInfoSheetTest.kt`.

**Interfaces:**
- Produce la escala de «Tipografía» de las restricciones globales:
  - `headlineMedium` en Nunito ExtraBold;
  - `titleSmall` en Bold;
  - `SectionHeader` pinta su título en `titleSmall`.
- Consume: nada nuevo.

- [ ] **Paso 1: Test que falla.** `conLetraGrandeLosBotonesSeApilanSinCortarse`:
  - escala 1,3, ancho 360 dp, principal «Añadir a la compra» y «Cerrar»;
  - el texto del principal tiene una línea y no desborda (`TextLayoutResult` de su nodo);
  - el principal queda encima de «Cerrar» (su `bottom` ≤ el `top` de «Cerrar»).
- [ ] **Paso 2: Comprobar que falla.**
- [ ] **Paso 3: Implementar.**
  - **Hoja:** si los dos botones no caben en una fila, se apilan: el principal arriba, a todo el ancho, y «Cerrar» debajo. Si caben, siguen como hoy. Se mide con `SubcomposeLayout` o `FlowRow`; lo elige el implementador.
  - **Escala:** ajustar `Type.kt` y `SectionHeader`.
- [ ] **Paso 4: Comprobar que pasan** el test, los demás de `BobitosInfoSheetTest` y `SectionHeaderTest`, y la puerta.

---

### Tarea 5: desplegable de filtro común (C4)

**Frontend:** `impeccable:impeccable extract J/core/designsystem/component/FilterDropdown.kt J/feature/exercises/ExerciseFilterMenus.kt`

**Ficheros:**
- Crear `J/core/designsystem/component/FilterDropdown.kt`.
- Modificar `J/feature/exercises/ExerciseFilterMenus.kt`: se quitan el `FilterDropdown` y el `FilterOption` privados, :79-136, y pasa a usar los comunes.
- Tests:
  - crear `A/core/designsystem/component/FilterDropdownTest.kt`;
  - actualizar `A/feature/exercises/ExerciseFilterMenusUiTest.kt` si cambian las etiquetas.

**Interfaces:**
- Produce:
  - `class FilterOption(val text: String, val selected: Boolean, val onSelect: () -> Unit)`.
  - `@Composable fun FilterDropdown(label: String, value: String, options: List<FilterOption>, modifier: Modifier = Modifier, active: Boolean = false, enabled: Boolean = true, tag: String? = null)`.
    - Es un `ExposedDropdownMenuBox` con un `OutlinedTextField` de solo lectura, como el de Ejercicios.
    - **La etiqueta siempre se ve.**
    - Con `active`, el contorno y la etiqueta van en `MaterialTheme.colorScheme.primary` (el acento del módulo).
    - En el menú, la opción elegida lleva una marca y `selected` en semántica.
    - Elegir una opción cierra el menú.
    - **Ni la etiqueta ni el valor se cortan con puntos suspensivos:** el valor puede ir en dos líneas.
  - `@Composable fun MultiSelectFilterDropdown(label: String, value: String, options: List<FilterOption>, modifier: Modifier = Modifier, active: Boolean = false, enabled: Boolean = true, tag: String? = null)`. Igual, pero cada opción lleva una `Checkbox` y **tocarla no cierra el menú**.
  - `@Composable fun FilterDropdownRow(modifier: Modifier = Modifier, content: @Composable FlowRowScope.() -> Unit)`.
    - Es un `FlowRow` con dos por fila y el mismo ancho (`Modifier.weight(1f)` y un ancho mínimo).
    - Si un desplegable no cabe, pasa a ocupar su fila.
- Consume: nada nuevo.

- [ ] **Paso 1: Tests que fallan** (`FilterDropdownTest`):
  - `muestraNombreYValor`: el nodo del campo contiene «Estado» y «Pendientes» y tiene `Role.DropdownList`.
  - `elMenuMarcaLaOpcionElegida`: al abrir, «Pendientes» tiene `selected = true` y «Completadas» no.
  - `elegirUnaOpcionAvisaYCierra`: pulsar «Completadas» llama a su `onSelect` y el menú desaparece.
  - `laSeleccionMultipleNoCierraElMenu`: en el múltiple, pulsar dos opciones las marca y el menú sigue abierto.
  - `conLetraGrandeNoSeCorta`: escala 1,3, ancho 360 dp, `FilterDropdownRow` con «Categoría: Desayunos y meriendas» y «Supermercado: Todos». Ningún texto desborda.
- [ ] **Paso 2: Comprobar que fallan** (no existe el componente).
- [ ] **Paso 3: Implementar** el componente y migrar `ExerciseFilterMenus` a él, con sus etiquetas de test (`EXERCISE_FILTER_TYPE_TAG` y `EXERCISE_FILTER_GROUP_TAG`).
- [ ] **Paso 4: Comprobar que pasan** `FilterDropdownTest`, `ExerciseFilterMenusUiTest` y la puerta.

---

### Tarea 6: filtros de Tareas en desplegables y estado vacío (C4, menores 1 y 2)

**Frontend:** `impeccable:impeccable layout J/feature/tasks/TasksScreen.kt`

**Ficheros:**
- Crear `J/feature/tasks/TasksEmptyState.kt`.
- Modificar:
  - `J/feature/tasks/TasksScreen.kt`:
    - `TaskFilterBar` (:373), los cuatro chips (:417-560) y `CurrentOptionCheck`/`CheckedMenuItem` si se quedan sin uso;
    - el estado vacío (:225-242);
    - el filtro y la búsqueda pasan a ser el primer elemento del `LazyColumn` (:284), dentro de `ModuleHeaderBand`;
  - `S`.
- Tests:
  - crear `T/feature/tasks/TasksEmptyStateTest.kt`;
  - actualizar `A/feature/tasks/TaskFilterBarUiTest.kt`.

**Interfaces:**
- Consume: `FilterDropdown`, `FilterDropdownRow` (tarea 5) y `ModuleHeaderBand` (tarea 3).
- Produce:
  - `enum class TasksEmptyKind { FIRST_RUN, ALL_DONE, NO_RESULTS }`.
  - `data class TasksEmpty(val kind: TasksEmptyKind, val canClearFilters: Boolean)`.
  - `fun tasksEmptyState(allTasks: List<TaskItem>, filters: TaskFilters, query: String): TasksEmpty`. Se llama solo cuando la lista visible está vacía. Reglas:
    - `allTasks` vacía → FIRST_RUN y false;
    - ninguna TODO, filtros iguales a `TaskFilters()` y búsqueda en blanco → ALL_DONE y false;
    - en otro caso → NO_RESULTS, con `canClearFilters = filters != TaskFilters() || query.isNotBlank()`.

- [ ] **Paso 1: Tests que fallan.**
  - `TasksEmptyStateTest`:
    - `sinTareasEsLaPrimeraVez`;
    - `todoHechoConFiltrosPorDefectoEsAllDone`;
    - `filtrosSinResultadosPermitenQuitarlos` (prioridad Alta sin ninguna Alta);
    - `sinFiltrosNiBusquedaNoOfreceQuitarFiltros` (hay TODO, pero la búsqueda está vacía y los filtros son los de por defecto: NO_RESULTS y false; no se da en la práctica, pero fija la regla).
  - `TaskFilterBarUiTest`: elegir Estado → Completadas y Prioridad → Alta desde sus desplegables cambia sus valores y el filtro que recibe `onChange`. Responsable ofrece «Sin asignar».
- [ ] **Paso 2: Comprobar que fallan.**
- [ ] **Paso 3: Implementar.**
  - **`TaskFilterBar`:** cuatro `FilterDropdown` en `FilterDropdownRow` (dos filas), en el orden Estado, Prioridad, Tipo y Responsable. Cada uno con `active` cuando su valor no es el de por defecto.
  - **Estado vacío:**
    - ALL_DONE pinta un título «Lo tienes todo hecho» (`tasks_all_done`) con la acción **«Ver completadas»** (`tasks_show_completed`), que pone `status = DONE`;
    - NO_RESULTS pinta «Quitar filtros» solo si `canClearFilters`.
  - **El filtro y la búsqueda** se desplazan con la lista.
- [ ] **Paso 4: Comprobar que pasan** los tests y la puerta.

---

### Tarea 7: filtros de Compra, Recetario e Ingredientes en desplegables (C4)

**Frontend:** `impeccable:impeccable layout J/feature/shopping/ShoppingScreen.kt J/feature/recipes/RecipesScreen.kt J/feature/ingredients/IngredientsScreen.kt`

**Ficheros:**
- Modificar:
  - `J/feature/shopping/ShoppingScreen.kt`: `SupermarketFilterRow`, :847, y su uso en :214;
  - `J/feature/recipes/RecipesScreen.kt`: `RecipeFilters`, :382-430;
  - `J/feature/ingredients/IngredientsScreen.kt`: `IngredientCatalog`, :242;
  - `J/feature/ingredients/IngredientListLogic.kt`;
  - `S`.
- Test: modificar `T/feature/ingredients/IngredientListLogicTest.kt` (crearlo si no existe).

**Interfaces:**
- Consume: `FilterDropdown` y `FilterDropdownRow` (tarea 5); `ModuleHeaderBand` (tarea 3).
- Produce:
  - `internal fun List<CatalogIngredient>.categories(): List<String>`: sin repetir, sin vacías, ordenadas con el `Collator` es-ES. Las que solo difieren en mayúsculas se juntan.
  - `internal fun List<CatalogIngredient>.inCategory(category: String?): List<CatalogIngredient>`. Con null devuelve todo; si no, compara sin mayúsculas y sin espacios en los extremos.

- [ ] **Paso 1: Tests que fallan** (`IngredientListLogicTest`):
  - `lasCategoriasSalenOrdenadasYSinRepetir`, con «Lácteos», «lácteos », «Frutas» y null;
  - `filtraPorCategoria`;
  - `sinCategoriaElegidaDevuelveTodo`.
- [ ] **Paso 2: Comprobar que fallan.**
- [ ] **Paso 3: Implementar.**
  - **Compra:** «Supermercado» en un `FilterDropdown` (Todos y los presentes), dentro de `ModuleHeaderBand` como primer elemento de la lista. Conserva `selectedSupermarket` local.
  - **Recetario:**
    - «Categoría» en un `FilterDropdown` (Todas y las categorías);
    - al lado, en la misma fila, «Con lo que hay» como `FilterChip` de activar y desactivar, solo si hay despensa (`state.pantryIds != null`), como hoy;
    - los dos van, junto a la búsqueda, en el primer elemento de la lista.
  - **Ingredientes:**
    - un `FilterDropdown` «Categoría» nuevo, con su estado local (`rememberSaveable`), igual que el Recetario;
    - la lista visible es `matching(query).inCategory(categoria)`.
  - Las cadenas de los chips que se quedan sin uso se borran.
- [ ] **Paso 4: Comprobar que pasan** el test y la puerta.

---

### Tarea 8: filtros de Personas y Espacios del Calendario (C4)

**Frontend:** `impeccable:impeccable layout J/feature/calendar/CalendarScreen.kt J/feature/calendar/PersonalCalendarScreen.kt`

**Ficheros:**
- Modificar:
  - `J/feature/calendar/CalendarScreen.kt`: `MemberFilters`, :364-383, que pasa a `internal` como `PeopleFilter`;
  - `J/feature/calendar/PersonalCalendarScreen.kt`: `SpaceFilters`, :287-307, que pasa a `internal` como `SpacesFilter`;
  - `S`.
- Crear `J/feature/calendar/MultiFilterValue.kt`.
- Tests:
  - crear `T/feature/calendar/MultiFilterValueTest.kt`;
  - crear `A/feature/calendar/PeopleFilterUiTest.kt`.

**Interfaces:**
- Consume: `MultiSelectFilterDropdown` (tarea 5). Las funciones de los ViewModel no cambian: `toggleMember`, `selectAllMembers`, `toggleSpace` y `selectAllSpaces`.
- Produce:
  - `sealed interface MultiFilterValue { data object All; data class One(val name: String); data class Some(val count: Int) }`;
  - `fun multiFilterValue(selectedIds: Set<String>, all: List<Pair<String, String>>): MultiFilterValue`. Recibe pares de id y nombre. Vacío o todos → All; uno → One; si no → Some (R4).

- [ ] **Paso 1: Tests que fallan.**
  - `MultiFilterValueTest`:
    - `vacioOTodosEsTodas`;
    - `unoEsSuNombre`;
    - `variosEsElNumero`.
  - `PeopleFilterUiTest`:
    - `elegirDosPersonasDejaElMenuAbiertoYDiceDosPersonas`: valor «2 personas»;
    - `todasVuelveATodas`.
- [ ] **Paso 2: Comprobar que fallan.**
- [ ] **Paso 3: Implementar.**
  - **Calendario:** «Personas» con «Todas» (que llama a `selectAllMembers`) y una opción por miembro. Valor: «Todas», el nombre o «N personas» (`calendar_people_count`, plural).
  - **Mi calendario:** «Espacios», igual, con «Todos» y «N espacios» (`my_calendar_spaces_count`, plural).
  - Los dos se quedan fijos en la franja (R2).
  - Quitar `calendar_none`, `my_calendar_none_spaces` y las demás cadenas que se queden sin uso.
- [ ] **Paso 4: Comprobar que pasan** los tests, `CalendarSemanticsTest` y la puerta. Un `grep -rn "FilterChip(" J/feature/tasks J/feature/shopping J/feature/recipes J/feature/ingredients J/feature/exercises J/feature/calendar` solo encuentra el interruptor «Con lo que hay» del Recetario y los chips de los formularios, ningún filtro de lista.

---

### Tarea 9: filas agrupadas, cabecera fija y agrupación de catálogos (C5)

**Frontend:** `impeccable:impeccable extract J/core/designsystem/component/GroupedRow.kt J/core/designsystem/component/SectionHeader.kt`

**Ficheros:**
- Crear:
  - `J/core/designsystem/component/GroupedRow.kt`;
  - `J/feature/common/CatalogGroups.kt`.
- Modificar `J/core/designsystem/component/SectionHeader.kt`.
- Tests:
  - crear `T/feature/common/CatalogGroupsTest.kt`;
  - crear `A/core/designsystem/component/GroupedRowTest.kt`.

**Interfaces:**
- Produce:
  - `enum class RowPosition { SINGLE, FIRST, MIDDLE, LAST }`.
  - `fun rowPosition(index: Int, count: Int): RowPosition`.
  - `@Composable fun GroupedRow(position: RowPosition, modifier: Modifier = Modifier, tint: Color? = null, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit)`.
    - El fondo es `tint ?: MaterialTheme.colorScheme.surfaceContainer`.
    - Esquinas de 16 dp en los extremos del grupo y de 4 dp por dentro, según `position`.
    - 2 dp de hueco debajo, salvo en `LAST` y `SINGLE`.
    - Al menos 48 dp de alto.
    - Con `onClick`, es un `Surface(onClick)`.
    - Sin líneas divisorias.
  - `SectionHeader(..., sticky: Boolean = false, icon: ImageVector? = null)`.
    - Con `sticky`, lleva fondo opaco (`MaterialTheme.colorScheme.surface`) para usarse en `stickyHeader`.
    - `icon` es decorativo y va delante del título.
  - `sealed interface CatalogHeader { data object Mine; data class Category(val name: String); data object Other }`.
  - `data class CatalogGroup<T>(val header: CatalogHeader?, val items: List<T>)`.
  - `enum class CatalogLayout { BY_CATEGORY, FLAT }`.
  - `fun <T> groupCatalog(items: List<T>, isMine: (T) -> Boolean, categoryOf: (T) -> String?, layout: CatalogLayout, selectedCategory: String? = null): List<CatalogGroup<T>>`. Recibe los elementos ya filtrados por la búsqueda y ordenados. Reglas:
    - **FLAT:** un grupo sin cabecera, con los tuyos primero y sin cambiar el orden relativo.
    - **BY_CATEGORY con `selectedCategory`:** un grupo `Category(selectedCategory)` con solo esa categoría (sin mayúsculas ni espacios en los extremos) y los tuyos primero.
    - **BY_CATEGORY sin selección:**
      - primero `Mine`, con los tuyos, si los hay;
      - después un grupo por categoría con lo que no es tuyo, ordenadas con el `Collator` es-ES y juntando las que solo difieren en mayúsculas;
      - al final `Other`, con lo que no es tuyo y no tiene categoría.
    - Nunca devuelve grupos vacíos.

- [ ] **Paso 1: Tests que fallan.**
  - `CatalogGroupsTest`:
    - `porCategoriaVanPrimeroLosTuyosYLuegoCadaCategoriaOrdenada` (con «Ñoquis» entre «Arroces» y «Pescados»);
    - `sinCategoriaVaAlFinalEnOtras`;
    - `conUnaCategoriaElegidaSoloSaleEseGrupoConLosTuyosPrimero`;
    - `planaPoneLosTuyosPrimeroSinCabecera`;
    - `categoriasQueSoloDifierenEnMayusculasSeJuntan`;
    - `nuncaHayGruposVacios`.
  - `GroupedRowTest`:
    - `laCabeceraFijaEsEncabezadoYLeeTituloYNumero`: `SectionHeader("Arroces", count = 8, sticky = true)` es `heading` y lee «Arroces · 8»;
    - `unaFilaPulsableTieneClicYAlMenos48dp`;
    - `rowPositionDaLosExtremos`: puede ir en JVM.
- [ ] **Paso 2: Comprobar que fallan.**
- [ ] **Paso 3: Implementar.**
- [ ] **Paso 4: Comprobar que pasan** los tests y la puerta.

---

### Tarea 10: Recetario e Ingredientes en filas agrupadas por categoría (C5)

**Frontend:** `impeccable:impeccable layout J/feature/recipes/RecipesScreen.kt J/feature/ingredients/IngredientsScreen.kt`

**Ficheros:**
- Modificar:
  - `J/feature/recipes/RecipesScreen.kt`: `recipesSection`, :316-361, `RecipeCard`, :433-460, y `rememberRecipeRows`, :301;
  - `J/feature/ingredients/IngredientsScreen.kt`: `IngredientCatalog`, :242-289, e `IngredientRow`, :292-324;
  - `S`.
- Crear `J/feature/recipes/RecipeSupporting.kt`.
- Test: crear `T/feature/recipes/RecipeSupportingTest.kt`.

**Interfaces:**
- Consume: `groupCatalog`, `CatalogLayout`, `CatalogHeader`, `GroupedRow`, `rowPosition` y `SectionHeader(sticky)` (tarea 9); `withPantryLines` (`J/feature/common/RecipePantry.kt`).
- Produce:
  - `sealed interface RecipeSupporting { data class Pantry(val line: PantryLine); data class Meta(val minutes: Int?, val servings: Int?); data class Category(val name: String); data object None }`.
  - `fun recipeSupporting(recipe: Recipe, pantryLine: PantryLine?, showCategory: Boolean): RecipeSupporting`. Con `pantryLine` → Pantry. Con `showCategory` y categoría → Category. Con minutos o raciones → Meta. Si no → None.

- [ ] **Paso 1: Tests que fallan** (`RecipeSupportingTest`):
  - `conDespensaSaleLaLineaDeLaDespensa`;
  - `alBuscarSaleLaCategoria`;
  - `sinBuscarSalenMinutosYRaciones`;
  - `sinDatosNoHayLinea`.
- [ ] **Paso 2: Comprobar que falla.**
- [ ] **Paso 3: Implementar.**
  - **Recetario:**
    - Las dos secciones actuales («Mis recetas» y «Recetas comunes») pasan a una sola lista: `groupCatalog(mine + global, isMine = { it in mine }, categoryOf = Recipe::category, ...)`.
    - `layout = FLAT` si hay búsqueda o «Con lo que hay» está activo; si no, `BY_CATEGORY`, con la categoría elegida.
    - Cabeceras en `stickyHeader` con `SectionHeader(sticky = true)`:
      - `Mine`: «Mis recetas»;
      - `Category`: su nombre;
      - `Other`: «Otras» (`recipes_group_other`);
      - cada una con su número.
    - Cada receta es un `GroupedRow` pulsable con el título en `bodyLarge` y `recipeSupporting(…, showCategory = layout == FLAT && sin despensa)` en `bodyMedium`. Meta es «25 min · 4 raciones» (`recipes_meta`, con plurales).
    - Se conservan los estados de carga, error y vacío de cada fuente.
  - **Ingredientes:**
    - `groupCatalog(ingredients, isMine = CatalogIngredient::personal, categoryOf = CatalogIngredient::category, ...)`, con `Mine` como «Mis ingredientes» y `Other` como «Otros»;
    - `FLAT` al buscar;
    - la fila conserva la línea de marca o tienda (`IngredientLine`) y pierde la categoría y el chip «Personal», salvo en la búsqueda, donde la categoría vuelve a la línea secundaria.
- [ ] **Paso 4: Comprobar que pasan** el test y la puerta. Un `grep -n "Card(" J/feature/recipes/RecipesScreen.kt J/feature/ingredients/IngredientsScreen.kt` no encuentra tarjetas por elemento.

---

### Tarea 11: Ejercicios con miniatura y Rutinas en filas (C5)

**Frontend:** `impeccable:impeccable layout J/feature/exercises/ExercisesScreen.kt J/feature/exercises/ExerciseThumbnail.kt J/feature/routines/RoutinesScreen.kt`

**Ficheros:**
- Crear `J/feature/exercises/ExerciseThumbnail.kt`.
- Modificar:
  - `J/feature/exercises/ExercisesScreen.kt`: lista, :262-286, y `ExerciseRow`, :290-345;
  - `J/feature/exercises/ExerciseTokens.kt`;
  - `J/feature/routines/RoutinesScreen.kt`: `routinesSection`, :237-288, y `RoutineCard`, :291;
  - `S`.
- Tests:
  - crear `T/feature/exercises/ExerciseThumbnailTest.kt`;
  - actualizar `A/feature/routines/RoutineCardTest.kt`.

**Interfaces:**
- Consume: `groupCatalog`, `GroupedRow` y `SectionHeader(sticky)` (tarea 9); `ExercisesViewModel.loadImage(exercise)` (:44).
- Produce:
  - `fun sampleSizeFor(width: Int, height: Int, targetPx: Int): Int`: la mayor potencia de 2 que deja el lado menor por encima o igual que `targetPx`, y como mínimo 1.
  - `@Composable fun ExerciseThumbnail(exercise: CatalogExercise, load: suspend () -> LoadedImage?, modifier: Modifier = Modifier)`.
    - Es un cuadrado de 40 dp con esquinas de 10 dp y fondo de tono.
    - Primero lee los límites (`inJustDecodeBounds`) y decodifica con `inSampleSize = sampleSizeFor(…, tamaño en px)`, fuera del hilo principal.
    - Sin imagen o mientras carga, pinta el icono del tipo.
    - Es decorativo (`contentDescription = null`).
  - `val ExerciseType.icon: ImageVector`, en `ExerciseTokens.kt`.
  - `fun exerciseSupportingRes(exercise: CatalogExercise): List<Int>`: el `labelRes` del tipo, seguido de los `labelRes` de su material.

- [ ] **Paso 1: Tests que fallan** (`ExerciseThumbnailTest`):
  - `unaImagenGrandeSeSubmuestrea`: 1024×768 con objetivo 120 da 4, porque 768/4 = 192 ≥ 120 y 768/8 = 96 < 120;
  - `unaImagenPequenaNoSeSubmuestrea`: da 1;
  - `laLineaSecundariaLlevaTipoYMaterial`.
- [ ] **Paso 2: Comprobar que falla.**
- [ ] **Paso 3: Implementar.**
  - **Ejercicios:**
    - `groupCatalog(filtered, isMine = { false }, categoryOf = CatalogExercise::muscleGroup, layout = BY_CATEGORY o FLAT al buscar, selectedCategory = filtro de grupo muscular)`;
    - `Other` es «Sin grupo» (`exercises_group_other`);
    - cada fila es un `GroupedRow` con `ExerciseThumbnail`, el nombre en `bodyLarge` (hasta dos líneas) y «Peso libre · Mancuernas» en `bodyMedium`;
    - el `InfoChip` de tipo desaparece;
    - se conservan el menú y el borrado;
    - la búsqueda y los desplegables de Tipo y Grupo muscular pasan al primer elemento de la lista, dentro de `ModuleHeaderBand`, y se desplazan con ella.
  - **Rutinas:**
    - las dos secciones siguen, y cada rutina es un `GroupedRow` con su menú;
    - su búsqueda pasa también al primer elemento de la lista, dentro de `ModuleHeaderBand`.
- [ ] **Paso 4: Comprobar que pasan** el test, `RoutineCardTest` y la puerta.

---

### Tarea 12: Tareas y Compra en filas agrupadas (C5)

Solo cambia el aspecto: **sin test RED**. Su `task-done` es la suite y el lint en verde, los tests instrumentados de Tareas y Compra que ya existen en verde, y el diff dentro de sus ficheros.

**Frontend:** `impeccable:impeccable layout J/feature/tasks/TasksScreen.kt J/feature/shopping/ShoppingScreen.kt`

**Ficheros:**
- Modificar `J/feature/tasks/TasksScreen.kt`: `TaskCard`, :569-624, y `taskRow`, :244-282.
- Modificar `J/feature/shopping/ShoppingScreen.kt`: `ShoppingItemCard`, :452-520, y las filas de :246-340.

**Interfaces:**
- Consume: `GroupedRow` y `rowPosition` (tarea 9).
- Produce: nada.

- [ ] **Paso 1: Implementar.**
  - Cada tarea y cada producto pasa a un `GroupedRow` dentro de su sección, con la posición según su índice.
  - **El tinte** sigue el de su categoría: el tipo de tarea o el supermercado, con el `accent` de su categoría al 12 % sobre `surfaceContainer`, como `categoryCardColors`.
  - Se conservan la casilla, el título tachado al completar, la línea de metadatos, `SwipeActionsBox` y «Más opciones».
  - **Tipografía:** el título en `bodyLarge` y los metadatos en `bodyMedium`.
- [ ] **Paso 2: Comprobar** la puerta, `TaskMetaRowUiTest` y `TaskEditorUiTest`. Un `grep -n "Card(" J/feature/tasks/TasksScreen.kt J/feature/shopping/ShoppingScreen.kt` no encuentra tarjetas por elemento.

---

### Tarea 13: Más, el inicio y los catálogos en un solo sitio (C7)

**Frontend:** `impeccable:impeccable layout J/core/navigation/SpaceMoreScreen.kt J/core/navigation/MainMenuScreen.kt`

**Ficheros:**
- Crear `J/core/navigation/MainMenuScreen.kt`, que se saca de `BobitosNavHost.kt:562`.
- Modificar:
  - `J/core/navigation/SpaceMoreScreen.kt`;
  - `J/core/navigation/BobitosNavHost.kt`: el cableado del inicio, :198-207, de Meals, :370-391, y de Sport, :392-413;
  - `J/core/navigation/BobitosDestination.kt`: iconos y listas del companion, :121-131;
  - `J/feature/meals/MealsScreen.kt`: cabecera, :158-209, y los parámetros `onOpenIngredients`/`onOpenRecipes`, :92;
  - `J/feature/sport/SportScreen.kt`: cabecera, :163-177, y los parámetros `onOpenRoutines`/`onOpenExercises`;
  - `S`.
- Borrar `J/core/navigation/ModuleCard.kt`.
- Tests:
  - crear `A/core/navigation/MainMenuScreenTest.kt`;
  - crear `A/core/navigation/SpaceMoreScreenTest.kt`;
  - crear `T/core/navigation/DestinationIconsTest.kt`.

**Interfaces:**
- Consume: `GroupedRow` y `SectionHeader` (tarea 9); `moduleTint` y `appModule()` (tarea 1).
- Produce:
  - En `BobitosDestination`:
    - `Sport.icon = Icons.Rounded.DirectionsRun`;
    - companion `catalogDestinations = listOf(Recipes, Ingredients, Exercises, Routines)`;
    - `moreDestinations` no cambia;
    - `mainMenuDestinations` se borra.
  - `internal fun MainMenuScreen(spaces: UiState<List<SpaceSummary>>, syncStatus: SyncStatus, onEnterSpace: (SpaceSummary) -> Unit, onManageSpaces: () -> Unit, onOpen: (BobitosDestination) -> Unit, onProfile: () -> Unit)`.
  - `internal fun SpaceMoreScreen(onOpen: (BobitosDestination) -> Unit)`, con la misma firma.

- [ ] **Paso 1: Tests que fallan.**
  - `MainMenuScreenTest`:
    - `tocarUnEspacioEntraEnEl`: «Casa Bobitos · 3 miembros» → `onEnterSpace`;
    - `gestionarLlevaATusEspacios`;
    - `sinEspaciosOfreceCrearOUnirse`: la fila llama a `onManageSpaces`;
    - `tieneTusEspaciosLoTuyoYCatalogosComoEncabezados`.
  - `SpaceMoreScreenTest.tieneSusDosGruposConEncabezado`: «Del espacio» y «Catálogos» son `heading`, y tocar «Recetario» llama a `onOpen(Recipes)`.
  - `DestinationIconsTest`:
    - `deporteYEjerciciosNoCompartenIcono`;
    - `enMasYEnElInicioNingunaFilaRepiteIcono`: `moreDestinations + catalogDestinations + MyCalendar` sin iconos repetidos.
- [ ] **Paso 2: Comprobar que fallan.**
- [ ] **Paso 3: Implementar** (maqueta 4).
  - **Más:**
    - «Del espacio»: Comidas, Deporte, Notas y Despensa, como `GroupedRow` teñidas con `moduleTint`, con su icono en el acento y una flecha;
    - «Catálogos»: los cuatro, como `GroupedRow` neutras, con el icono dentro de una pastilla del contenedor de su módulo.
  - **Inicio:**
    - «Tus espacios», con las iniciales, el nombre y «N miembros» (plural), y la acción «Gestionar» en su cabecera;
    - sin espacios, la fila «Crear o unirse a un espacio»;
    - «Lo tuyo»: Mi calendario, teñido de Calendario, con «Los eventos de todos tus espacios»;
    - «Catálogos», como en Más.
  - **Cableado en `BobitosNavHost`:**
    - `onEnterSpace = { onSpaceSelected(it.id); navController.navigate(SpaceHome.route) { launchSingleTop = true } }`;
    - `onManageSpaces = navController::navigateToSpaces`.
  - **Comidas y Deporte** pierden sus enlaces a catálogos: Comidas se queda con la fecha y el ⋮, y Deporte con la fecha.
  - «¿Qué cocino?» de la Despensa no cambia.
- [ ] **Paso 4: Comprobar que pasan** los tests y la puerta.

---

### Tarea 14: Deshacer al completar una tarea (C6)

**Frontend:** ninguno (lógica y snackbar).

**Ficheros:**
- Modificar:
  - `J/data/repository/TaskRepository.kt`;
  - `J/data/repository/FirestoreTaskRepository.kt`, junto a `setCompleted`, :135-180;
  - `J/feature/tasks/TasksViewModel.kt`: `setCompleted`, :136;
  - `J/feature/tasks/TasksScreen.kt`: las llamadas en :259 y :273;
  - `S`.
- Tests:
  - modificar `T/feature/tasks/TasksViewModelTest.kt` (y su `FakeTaskRepository`, :350);
  - modificar `T/feature/spaces/SpaceHomeViewModelTest.kt` (solo el falso `NoTasks`, :187);
  - modificar `tests/firebase-emulators.test.mjs`.

**Interfaces:**
- Produce:
  - `TaskRepository.restoreCompletion(spaceId: String, previous: TaskItem)`.
    - Exige `previous.status == TaskStatus.TODO`.
    - En una transacción: si el documento no existe, lanza `TaskRepositoryException(TaskFailure.TaskNotFound)`.
    - Si existe, hace un solo `update` con `status = "TODO"`, `completedBy`, `completedByName` y `completedAt` a null, `startAt = previous.startAt`, `dueAt = previous.dueAt`, `updatedBy` y `updatedAt = serverTimestamp()`.
    - Vale para una tarea que se repite, que sigue en TODO con las fechas adelantadas, y para una normal, que está en DONE.
  - `TasksViewModel.setCompleted(spaceId: String, task: TaskItem, completed: Boolean, onCompleted: (TaskItem) -> Unit = {})`.
    - Al completar, no emite el aviso `TaskCompleted`: el snackbar lo lanza la pantalla con Deshacer.
    - Si termina bien, llama a `onCompleted(task)` con la foto de antes.
    - Al reabrir, sigue como hoy, con el aviso `TaskReopened`.
  - `TasksViewModel.undoCompletion(spaceId: String, previous: TaskItem)`: `runAction(null, force = true) { taskRepository.restoreCompletion(spaceId, previous) }`. Los errores salen por `showError` con el texto humano.
  - En `TasksScreen`, completar lanza `scope.launchUndo(snackbar, tasks_undo_completed («Tarea completada»), undo) { viewModel.undoCompletion(spaceId, it) }`, igual que el borrado (:184-190).

- [ ] **Paso 1: Tests que fallan.**
  - `TasksViewModelTest`:
    - `completarOfreceDeshacerConLaFotoDeAntes`: `onCompleted` recibe la tarea con su `startAt` y su `dueAt` originales, y no hay aviso `TaskCompleted`;
    - `deshacerDevuelveLaTareaConSuEstadoYSusFechas`: el falso registra `restoreCompletion(spaceId, previous)` con esas fechas;
    - `deshacerSiLaTareaYaNoExisteAvisa` (Review Focus 3): el falso lanza `TaskNotFound` y el estado tiene ese error.
  - `tests/firebase-emulators.test.mjs`, con el test `deshacer completar devuelve estado y fechas en una escritura`:
    - un miembro reabre una tarea DONE poniendo los `completed*` a null y otra `startAt`/`dueAt` en el mismo `updateDoc`;
    - en una tarea TODO cambia solo `startAt`, `dueAt`, `updatedBy` y `updatedAt`.

    Las reglas actuales lo permiten (`validTaskUpdate`, `firestore.rules:373-396`), así que este test **no es RED**: protege que siga así sin tocar las reglas.
- [ ] **Paso 2: Comprobar que fallan** los JVM. No compilan sin `restoreCompletion` ni la firma nueva.
- [ ] **Paso 3: Implementar.** Añadir `restoreCompletion` también a los falsos `FakeTaskRepository` y `NoTasks`.
- [ ] **Paso 4: Comprobar que pasan** los tests, `npm run test:emulators` y la puerta.

---

### Tarea 15: la agenda de Hoy — lógica y ViewModel (C6)

**Frontend:** ninguno (lógica).

**Ficheros:**
- Crear:
  - `J/feature/spaces/TodayAgenda.kt`;
  - `J/core/common/CurrentDayProvider.kt`, que se mueve desde `J/feature/spaces/SpaceHomeViewModel.kt:105`.
- Modificar:
  - `J/feature/spaces/SpaceHomeViewModel.kt`;
  - `J/core/navigation/BobitosNavHost.kt`, en :258-290, solo lo necesario para que compile con la API nueva;
  - `J/core/navigation/SpaceTodayScreen.kt`, solo para que compile mientras llega la tarea 16;
  - `J/data/di/DataModule.kt`, para quitar el binding de `SpaceSummaryRepository`, :155.
- Borrar:
  - `J/feature/spaces/SpaceHomeDigest.kt`;
  - `J/data/repository/SpaceSummaryRepository.kt`;
  - `J/data/repository/FirestoreSpaceSummaryRepository.kt`.
- Tests:
  - crear `T/feature/spaces/TodayAgendaTest.kt`;
  - crear `T/core/common/CurrentDayProviderTest.kt`;
  - reescribir `T/feature/spaces/SpaceHomeViewModelTest.kt`;
  - borrar `T/feature/spaces/SpaceHomeDigestTest.kt`.

**Interfaces:**
- Consume:
  - `TaskRepository.tasks`, `MealRepository.meals(spaceId, today, today+1)`, `CalendarRepository.events(spaceId, dayStart, dayEnd)`, `SportActivityRepository.activities(spaceId, today, today+1)`, `ShoppingRepository.items(spaceId)` y `SpaceRepository.members(spaceId)`;
  - `restoreCompletion` (tarea 14).
- Produce:
  - En `TodayAgenda.kt`:
    ```kotlin
    const val AGENDA_MAX_TASKS = 5
    data class TodayAgenda(
        val tasks: List<AgendaTask>, val hiddenTasks: Int, val entries: List<AgendaEntry>,
        val shopping: ShoppingGlance, val workload: List<MemberWorkload>,
    )
    data class AgendaTask(val task: TaskItem, val overdueSince: LocalDate?, val owner: AgendaPerson)
    sealed interface AgendaPerson { data object You; data class Member(val name: String); data object Nobody }
    sealed interface AgendaTime {
        data object AllDay; data object NoTime; data object Continues
        data class At(val time: LocalTime); data class Slot(val slot: MealSlot)
    }
    sealed interface AgendaEntry {
        val time: AgendaTime
        data class Event(val event: CalendarEvent, override val time: AgendaTime, val people: List<AgendaPerson>) : AgendaEntry
        data class Activity(val activity: SportActivity, override val time: AgendaTime, val people: List<AgendaPerson>) : AgendaEntry
        data class Meal(val slot: MealSlot, val meal: com.dlunaunizar.bobitos.core.model.Meal?, val cook: AgendaPerson?, val eaters: Int) : AgendaEntry
    }
    data class ShoppingGlance(val pending: Int, val firstNames: List<String>)
    data class MemberWorkload(val userId: String, val displayName: String, val pending: Int, val doneThisWeek: Int)
    fun buildTodayAgenda(
        userId: String, today: LocalDate, zone: ZoneId, tasks: List<TaskItem>, meals: List<Meal>,
        events: List<CalendarEvent>, activities: List<SportActivity>, shopping: List<ShoppingItem>,
        members: List<SpaceMember>,
    ): TodayAgenda
    ```
    - `Meal.time` es siempre `Slot(slot)`.
    - `meal == null` es una franja sin planificar.
    - `MemberWorkload` se mueve tal cual desde `SpaceHomeDigest.kt`, con el mismo cálculo.
  - **Reglas de `buildTodayAgenda`** (spec C6, R3 y R5):
    - **Tareas:**
      - entran las TODO con `dueAt` (en `zone`) hasta hoy, de cualquiera;
      - van primero las vencidas, de la más antigua a la más reciente, con `overdueSince` = la fecha de vencimiento; después las de hoy, por hora y luego por título;
      - `owner`:
        - `You` si `assigneeId == userId`;
        - `Member(assigneeName)` si tiene responsable, aunque ya no esté en `members` (Review Focus 1);
        - `Nobody` si no tiene;
      - salen como mucho `AGENDA_MAX_TASKS`, y `hiddenTasks` es el resto.
    - **Eventos:**
      - todo el día → `AllDay`;
      - con hora que empezó antes de hoy y sigue → `Continues`;
      - con hora de hoy → `At(hora local)`.
    - **Actividades:**
      - con evento enlazado (`eventId` presente en `events`) con hora → `At(hora del evento)`;
      - con evento de todo el día o sin evento → `NoTime`;
      - **el evento enlazado de una actividad de hoy no sale** como evento.
    - **Comidas:**
      - cada comida de hoy es una entrada;
      - cada franja sin comidas es una entrada con `meal = null`;
      - `cook` es `You`, `Member(cookName)` o null;
      - `eaters = participantIds.size`.
    - **Orden de `entries`:**
      - primero `AllDay`, después `NoTime` y después `Continues`;
      - después, por hora, con las franjas a las 08:00, las 14:00 y las 21:00;
      - en un empate, Meal, Event y Activity, y luego por título.
    - **Personas:** `participantIds` con `participantNames` en el mismo índice, y el usuario actual como `You`.
    - **Compra:** los no comprados en el orden de entrada, `pending` y los tres primeros nombres.
  - En `CurrentDayProvider.kt`:
    - `open class CurrentDayProvider @Inject constructor() { open fun today(zone: ZoneId): Flow<LocalDate> }`, con el mismo comportamiento que hoy;
    - `internal fun millisUntilNextDay(now: ZonedDateTime): Long`.
  - `SpaceHomeViewModel`:
    - **Dependencias:** se quita `SpaceSummaryRepository` y se añaden `SportActivityRepository` y `ShoppingRepository`.
    - `fun show(spaceId: String, userId: String)` fija la clave, como hacía `load()`.
    - `val agenda: StateFlow<UiState<TodayAgenda>>`:
      - es `clave.flatMapLatest { … combine … }`;
      - al cambiar de clave empieza con `UiState.Loading`, sin datos del espacio anterior (Review Focus 2);
      - lleva `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)`;
      - **solo escucha mientras alguien recoge**;
      - un fallo da `UiState.Error` con el `FailureKind` humano.
    - `fun retry()` vuelve a suscribir.
    - `fun complete(task: TaskItem, onCompleted: (TaskItem) -> Unit)` llama a `taskRepository.setCompleted(spaceId, task.id, true)`.
    - `fun undoCompletion(previous: TaskItem)`.
    - `val error: StateFlow<TaskUiMessage?>` y `fun clearError()`.
  - `SpaceTodayScreen` recibe de momento lo mínimo para compilar. La tarea 16 la rehace.

- [ ] **Paso 1: Tests que fallan.**
  - `TodayAgendaTest`:
    - `tareasVencidasPrimeroYLuegoLasDeHoy`;
    - `tareasDeCualquieraIncluidasSinAsignarYLasTuyasComoTu`;
    - `unaTareaDeUnExMiembroMuestraSuNombreGuardado`;
    - `masDeCincoTareasDejaLasDemasOcultas`;
    - `eventoDeTodoElDiaVaPrimero`;
    - `eventoEmpezadoAyerSigueHoy`;
    - `eventoConHora`;
    - `actividadConEventoEnlazadoNoSeDuplica`;
    - `actividadConEventoDeTodoElDiaVaSinHora`;
    - `actividadConEventoConHoraVaASuHora`;
    - `actividadSinEventoVaSinHora`;
    - `franjasPlanificadasYSinPlanificarOrdenadasPorHora`;
    - `compraConCeroDosYCincoPendientes`;
    - `diaVacioTieneSoloLasTresFranjasSinPlanificar`;
    - `elRepartoSeCalculaComoAntes`.
  - `CurrentDayProviderTest.faltaParaMedianoche`: a las 23:59:30 quedan 30 000 ms.
  - `SpaceHomeViewModelTest`:
    - `soloEscuchaMientrasSeRecoge`: los falsos cuentan las suscripciones activas; con un colector dentro hay 6; al cancelarlo y avanzar 5 s, hay 0;
    - `cambiarDeEspacioNoMezclaDatos`: tras `show("b", …)`, el primer valor es `Loading` y después solo llegan datos de «b»;
    - `completarDesdeHoyOfreceDeshacerYDeshacerLaDevuelve`;
    - `unFalloDaErrorYReintentarVuelveASuscribir`.
- [ ] **Paso 2: Comprobar que fallan.**
- [ ] **Paso 3: Implementar.** Borrar los ficheros de la lista y el `buildHomeDigest`. Ajustar `BobitosNavHost` para recoger `agenda` con `collectAsStateWithLifecycle` y llamar a `show(…)` desde el `LaunchedEffect` de :263.
- [ ] **Paso 4: Comprobar que pasan** los tests y la puerta. Un `grep -rn "SpaceSummaryRepository\|SpaceModuleCounts\|buildHomeDigest" app/src` sale vacío.

---

### Tarea 16: la pantalla de Hoy (C6)

**Frontend:** `impeccable:impeccable layout J/core/navigation/SpaceTodayScreen.kt`

**Ficheros:**
- Reescribir `J/core/navigation/SpaceTodayScreen.kt`.
- Modificar:
  - `J/feature/spaces/SpaceHomeSections.kt`: se queda solo `WorkloadSection`, con `MyDayCard` y lo suyo borrados;
  - `J/core/navigation/BobitosNavHost.kt`: SpaceHome, :258-290;
  - `S`: borrar las `home_my_day_*` y las de las fichas de contadores; añadir las de la agenda.
- Test: crear `A/core/navigation/SpaceTodayScreenTest.kt`.

**Interfaces:**
- Consume:
  - `TodayAgenda` y los demás tipos, y `SpaceHomeViewModel.complete`, `undoCompletion`, `retry` y `error` (tarea 15);
  - `GroupedRow`, `SectionHeader` y `moduleTint` (tareas 1 y 9);
  - `WorkspaceScaffold(title, supportingText)` (tarea 3);
  - `launchUndo` (`J/core/designsystem/component/UndoSnackbar.kt`).
- Produce: `internal fun SpaceTodayScreen(agenda: UiState<TodayAgenda>, canWrite: Boolean, onToggleTask: (TaskItem) -> Unit, onRetry: () -> Unit, onOpen: (BobitosDestination) -> Unit)`.

- [ ] **Paso 1: Tests que fallan** (`SpaceTodayScreenTest`, con una `TodayAgenda` de prueba):
  - `lasCabecerasSonEncabezados`: «Tareas para hoy · 3», «Agenda» y «Reparto de tareas»;
  - `laCasillaDeLaTareaTieneNombreYEstado`: la casilla tiene `Role.Checkbox`, el título de la tarea como nombre y `ToggleableState.Off`. Pulsarla llama a `onToggleTask`;
  - `tocarLaFilaFueraDeLaCasillaLlevaATareas`: pulsar el texto de la tarea llama a `onOpen(Tasks)` y no a `onToggleTask`;
  - `sinConexionLaCasillaNoSePuedeMarcar`: con `canWrite = false`, `assertIsNotEnabled`;
  - `laFilaDeAgendaSeLeeEntera`: la descripción «Comida: Albóndigas en salsa, cocinas tú, comen 3»;
  - `masDeCincoTareasOfreceVerlasEnTareas`: «Ver las 7 en Tareas» → `onOpen(Tasks)`;
  - `laCompraACeroDiceQueEstaAlDia`;
  - `laFranjaSinPlanificarLlevaAComidas`: «Cena · Sin planificar» → `onOpen(Meals)`.
- [ ] **Paso 2: Comprobar que fallan.**
- [ ] **Paso 3: Implementar** (maquetas 1 y 2, opción A con filas teñidas).
  - **Secciones:**
    - «Tareas para hoy · N», con filas `GroupedRow` teñidas de TASKS. Cada una lleva una `Checkbox` con su propio destino de 48 dp, con nombre (el título) y estado, que completa la tarea. El resto de la fila lleva a la pestaña Tareas (spec C6). La fila tiene el título y la línea secundaria:
      - en las vencidas, «Venció ayer» o «Venció el 12 de octubre», en `error`;
      - la bandera de prioridad con su nombre;
      - «Tú», el nombre o «Sin asignar».
    - «Ver las N en Tareas» si `hiddenTasks > 0`.
    - «Agenda», con filas teñidas del módulo de cada entrada:
      - la etiqueta de hora en una columna: «Todo el día», «Sin hora», «Sigue hoy», `HH:mm` o el nombre de la franja;
      - el icono del módulo, el título y quién: «Cocinas tú», «Cocina Ana», «comen 3», los participantes, «Hecha»;
      - tocar una entrada abre Calendario, Comidas o Deporte;
      - la franja sin planificar va atenuada.
    - La fila de Compra: «Compra · 5» con «Leche, pan, huevos y 2 más», o «La compra está al día».
    - «Reparto de tareas», como ahora.
  - **Estados:**
    - `Loading` → `LoadingState`;
    - `Error` → `ErrorState` con «Reintentar» (`onRetry`);
    - agenda vacía → la línea «Hoy no hay nada en la agenda».
  - **En `BobitosNavHost`:**
    - el título es `BobitosDates.longDay(hoy)`, con mayúscula inicial, y `supportingText` es el saludo («Buenas tardes, David», con `dayPeriodFor` y `firstName`);
    - `onToggleTask` llama a `complete(task) { scope.launchUndo(snackbar, tasks_undo_completed, undo) { undoCompletion(it) } }`, con `LocalSnackbarHostState`;
    - los errores salen por el snackbar.
  - **Descripciones accesibles:** cada fila de la agenda fusiona su contenido en una descripción completa.
- [ ] **Paso 4: Comprobar que pasan** el test y la puerta. Un `grep -rn "CounterTile\|MyDayCard\|home_my_day" app/src` sale vacío.

---

### Tarea 17: Comidas y Deporte — franja vacía, cabeceras, tira de días y «hoy» a medianoche (C8, C11 menores 3, 4 y 5)

**Frontend:** `impeccable:impeccable harden J/feature/meals/MealsScreen.kt J/core/designsystem/component/WeekDayStrip.kt`

**Ficheros:**
- Crear `J/core/designsystem/Today.kt`.
- Modificar:
  - `J/feature/meals/MealsScreen.kt`: `MealSlotSection`, :336-382, que pasa a `internal`, y la tira, :147-156;
  - `J/feature/sport/SportScreen.kt`: la tira, :152-161;
  - `J/core/designsystem/component/WeekDayStrip.kt`: `WeekDay`, :90-123, y `WeekDayStripDefaults`, :39-46;
  - `S`.
- Tests:
  - crear `A/feature/meals/MealSlotSectionUiTest.kt`;
  - actualizar `A/core/designsystem/component/WeekDayStripTest.kt`.

**Interfaces:**
- Consume: `CurrentDayProvider` (tarea 15), `SectionHeader(icon)` y `GroupedRow` (tarea 9).
- Produce:
  - `@Composable fun rememberToday(zone: ZoneId = ZoneId.systemDefault()): LocalDate`. Recoge `CurrentDayProvider().today(zone)` con `collectAsStateWithLifecycle`, así que vuelve a leer al volver a la app y cambia a medianoche.
  - `internal fun MealSlotSection(slot: MealSlot, meals: List<Meal>, recipes: …, canWrite: Boolean, actionsEnabled: Boolean, onAdd: (MealSlot) -> Unit, onEdit: …, onDelete: …, onAddToShopping: …, onToggleCooked: …)`. Los demás parámetros son los de hoy.
  - En `WeekDayStripDefaults.colors()`, `todayOutline = MaterialTheme.colorScheme.primary` (el acento del módulo, con 3:1 comprobado en la tarea 1).

- [ ] **Paso 1: Tests que fallan.**
  - `MealSlotSectionUiTest`:
    - `laFranjaVaciaPermiteAnadirEnElla`: con `canWrite`, «Añadir cena» (`meals_add_to_slot`, con el nombre de la franja en minúscula) llama a `onAdd(CENA)`;
    - `sinConexionLaFranjaVaciaSoloDiceSinPlanificar`: sin `canWrite`, no hay acción;
    - `laCabeceraDeFranjaEsEncabezado`.
  - `WeekDayStripTest`:
    - `cadaDiaEsUnaOpcionUnica`: `Role.RadioButton` en vez de `Role.Tab`;
    - siguen en verde las demás de :1.
- [ ] **Paso 2: Comprobar que fallan.**
- [ ] **Paso 3: Implementar.**
  - **Franja vacía:**
    - con `canWrite`, es un `GroupedRow` pulsable con un icono de añadir y «Añadir desayuno», «Añadir comida» o «Añadir cena», que llama a `onAdd(slot)`;
    - en la pantalla, `onAdd = { openEditor(null, it) }`, la lambda que ya existe en :107, con el día elegido (`focusedDate`);
    - sin `canWrite`, sigue «Sin planificar».
  - **La cabecera de franja** pasa a `SectionHeader(title, icon = slot.icon)`.
  - **Tira de días:**
    - `selectable(role = Role.RadioButton)`;
    - el contorno de «hoy» en `todayOutline`, de 2 dp.
  - Comidas y Deporte pasan `today = rememberToday()`.
  - **En Comidas y Deporte, la tira de días va dentro de `ModuleHeaderBand`,** fija bajo la barra: no se desplaza con la lista (spec C2). La fecha y el ⋮ de Comidas van debajo, fuera de la franja.
- [ ] **Paso 4: Comprobar que pasan** los tests, `MealCardUiTest` y la puerta.

---

### Tarea 18: Calendario — carga sin parpadeo, semana plegada, crear manteniendo pulsado y roles (C11 menores 3, 5, 6, 7 y 8)

**Frontend:** `impeccable:impeccable harden J/feature/calendar/CalendarScreen.kt J/feature/calendar/PersonalCalendarScreen.kt J/feature/calendar/FoldedDaysRow.kt`

**Ficheros:**
- Modificar:
  - `J/feature/calendar/CalendarViewModel.kt` (`observeRange`, :205);
  - `J/feature/calendar/PersonalCalendarViewModel.kt` (:228);
  - `J/feature/calendar/CalendarScreen.kt`: `MonthGrid`, :384-426, `DayCell`, :429, `WeekEventList`, :498-527, y `HourRow`, :638;
  - `J/feature/calendar/PersonalCalendarScreen.kt`: `EventsLoadState`, :405-423, y `PersonalWeekEventList`, :309-329;
  - `J/feature/calendar/WeekRows.kt` (`rememberWeekRows`, :55-60);
  - `J/feature/calendar/FoldedDaysRow.kt`;
  - `J/core/common/BobitosDates.kt`;
  - `S` (:337-338 y cadenas nuevas).
- Tests:
  - modificar `T/feature/calendar/CalendarViewModelTest.kt` y `T/feature/calendar/PersonalCalendarViewModelTest.kt` (crearlos si no existen);
  - modificar `T/core/common/BobitosDatesTest.kt`;
  - modificar `A/feature/calendar/FoldedDaysRowTest.kt` y `A/feature/calendar/CalendarSemanticsTest.kt`.

**Interfaces:**
- Consume: `rememberToday()` (tarea 17).
- Produce:
  - `refreshing: Boolean` en el estado de los dos ViewModel. Al cambiar de periodo o de vista con `events` ya en `Content`, no vuelve a `Loading`: pone `refreshing = true` hasta el primer valor nuevo.
  - `EventsLoadState(state, refreshing, onRetry, modifier, content)`: con `refreshing`, un `LinearProgressIndicator` fino arriba y el contenido de antes.
  - `rememberWeekRows(monday, events, today: LocalDate, hasEvents)`: `today` entra como clave.
  - `fun BobitosDates.accessibleRange(start: LocalDate, end: LocalDate): String`:
    - «del martes 13 al viernes 16 de octubre», o «del jueves 29 de octubre al lunes 2 de noviembre» si cambia el mes;
    - siempre en español y en minúscula.

- [ ] **Paso 1: Tests que fallan.**
  - `CalendarViewModelTest.cambiarDePeriodoConContenidoNoVuelveACargando`: con `Content`, `nextPeriod()` deja `events` en `Content` y `refreshing = true`; al llegar los nuevos, `refreshing = false`. Lo mismo en `PersonalCalendarViewModelTest`.
  - `BobitosDatesTest`, con el locale por defecto en inglés:
    - `rangoAccesibleEnElMismoMes`;
    - `rangoAccesibleEntreMeses`.
  - `FoldedDaysRowTest`:
    - `unSoloDiaNoRepiteLaFecha`: «domingo, 18 de octubre, sin eventos»;
    - `unRangoVaEnMinuscula`: «del martes 13 al viernes 16 de octubre, sin eventos».
  - `CalendarSemanticsTest`:
    - `laCeldaDelMesEsUnaOpcionUnicaConHoy`: `Role.RadioButton`, `selected` y «hoy» en la descripción;
    - `lasCabecerasDeDiaDeLaSemanaSonEncabezados`.
- [ ] **Paso 2: Comprobar que fallan.**
- [ ] **Paso 3: Implementar.**
  - **`DayCell`:** pasa a `selectable(role = Role.RadioButton)` dentro de un `selectableGroup` en la rejilla.
  - **«Hoy»:** `MonthGrid` y las dos semanas leen `rememberToday()`.
  - **Cabeceras de día** de las dos semanas: `heading()`.
  - **`FoldedDaysRow`:**
    - con `start == end`, la cadena `calendar_empty_day_description` («%1$s, sin eventos», con `accessibleDate`);
    - si no, `calendar_empty_run_description` («%1$s, sin eventos», con `accessibleRange`).
  - **`HourRow`:**
    - al mantener pulsado, `LocalHapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)` y una indicación de pulsación en la fila (`indication` con `MutableInteractionSource` en el `PressInteraction`);
    - en la vista Día con `canWrite`, bajo la cabecera del periodo, la línea `calendar_hold_hint` («Mantén pulsada una hora para crear un evento.»), en `bodyMedium` y `onSurfaceVariant`.
- [ ] **Paso 4: Comprobar que pasan** los tests y la puerta.

---

### Tarea 19: movimiento base — transiciones, pulsar, FAB que se recoge y tira de días (C10)

**Frontend:** `impeccable:impeccable animate J/core/designsystem/Motion.kt J/core/navigation/BobitosNavHost.kt J/core/designsystem/component/WeekDayStrip.kt`

**Ficheros:**
- Modificar:
  - `J/core/designsystem/Motion.kt`;
  - `J/core/navigation/BobitosNavHost.kt` (:189-197 y `NAV_ANIM_MS`, :707);
  - `J/core/designsystem/component/WeekDayStrip.kt`;
  - `J/core/designsystem/component/GroupedRow.kt`;
  - `J/core/designsystem/component/ClickableCard.kt`.
- Modificar los 13 FAB, que pasan `expanded`:
  - `J/feature/pantry/PantryScreen.kt:79`;
  - `J/feature/tasks/TasksScreen.kt:160`;
  - `J/feature/calendar/PersonalCalendarScreen.kt:381`;
  - `J/feature/shopping/ShoppingScreen.kt:167`;
  - `J/feature/calendar/CalendarScreen.kt:177`;
  - `J/feature/spaces/SpacesScreen.kt:165`;
  - `J/feature/sport/SportScreen.kt:116`;
  - `J/feature/routines/RoutinesScreen.kt:104`;
  - `J/feature/notes/NotesScreen.kt:97`;
  - `J/feature/exercises/ExercisesScreen.kt:104`;
  - `J/feature/recipes/RecipesScreen.kt:160`;
  - `J/feature/meals/MealsScreen.kt:700`;
  - `J/feature/ingredients/IngredientsScreen.kt:107`.
- Test: crear `T/core/designsystem/MotionTest.kt`.

**Interfaces:**
- Produce, en `Motion.kt`:
  - `fun <T> motionSpec(reduceMotion: Boolean, spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T>`: con `reduceMotion` devuelve `snap()`.
  - `val BouncySpring`: un `spring` con `Spring.DampingRatioMediumBouncy` y `Spring.StiffnessMedium`.
  - `fun Modifier.pressScale(interactionSource: InteractionSource, reduceMotion: Boolean): Modifier`: escala 0,96 al pulsar, con `BouncySpring`.
  - `enum class NavMotion { FADE_THROUGH, SLIDE }`.
  - `fun navMotionFor(fromRoute: String?, toRoute: String?): NavMotion`: FADE_THROUGH si las dos rutas son de `BobitosDestination.workspaceTabs`; SLIDE en otro caso.
  - `fun fabExpandedAfterScroll(wasExpanded: Boolean, previousIndex: Int, previousOffset: Int, index: Int, offset: Int): Boolean`:
    - arriba del todo (0, 0) → true;
    - bajando → false;
    - subiendo → true;
    - quieto → `wasExpanded`.
  - `@Composable fun rememberFabExpanded(listState: LazyListState): Boolean`, con `fabExpandedAfterScroll`.

- [ ] **Paso 1: Tests que fallan** (`MotionTest`):
  - `conQuitarAnimacionesLaEspecificacionEsInstantanea`: `motionSpec(true, spring<Float>()) is SnapSpec`;
  - `sinQuitarAnimacionesSeRespeta`;
  - `entrePestanasEsFundidoYAlEntrarEnUnaPantallaEsDeslizar`: tasks → shopping es FADE_THROUGH; space-more → recipes es SLIDE; recipes → space-more es SLIDE;
  - `elFabSeRecogeAlBajarYVuelveAlSubir`, con los cuatro casos.
- [ ] **Paso 2: Comprobar que fallan.**
- [ ] **Paso 3: Implementar.**
  - **`NavHost`:**
    - con FADE_THROUGH, `fadeIn(90 ms)` + `scaleIn(0.92)` al entrar y `fadeOut(90 ms)` al salir;
    - con SLIDE, `slideInHorizontally { it }` + `fadeIn` al entrar, y el inverso con `popEnter` y `popExit` (deslizando hacia la derecha);
    - con `rememberReduceMotion()`, `None`, como hoy.
  - **`GroupedRow` y `ClickableCard`:** `pressScale`.
  - **Tira de días:** la pastilla del día elegido se dibuja una vez y se desliza hasta el día tocado con `animateDpAsState(motionSpec(reduce, BouncySpring))` (R7).
  - **Los 13 FAB:** `expanded = rememberFabExpanded(listState)`, con el `LazyListState` de la lista de su pantalla. Se crea con `rememberLazyListState()` donde no exista.
- [ ] **Paso 4: Comprobar que pasan** el test, `WeekDayStripTest`, `FabClearanceTest` y la puerta.

---

### Tarea 20: completar con vida, celebración y entrada en cascada (C10)

**Frontend:** `impeccable:impeccable animate J/core/designsystem/component/Celebration.kt J/feature/tasks/TasksScreen.kt J/feature/shopping/ShoppingScreen.kt J/core/navigation/SpaceTodayScreen.kt`

**Ficheros:**
- Crear:
  - `J/core/designsystem/component/Celebration.kt`;
  - `J/core/designsystem/component/CompletingItems.kt`.
- Modificar:
  - `J/feature/tasks/TasksScreen.kt`;
  - `J/feature/shopping/ShoppingScreen.kt`;
  - `J/core/navigation/SpaceTodayScreen.kt`;
  - `J/feature/meals/MealsScreen.kt` y `J/feature/sport/SportScreen.kt` (cascada al cambiar de día);
  - `J/feature/spaces/TodayAgenda.kt`;
  - `S`.
- Tests:
  - crear `T/core/designsystem/CelebrationTest.kt`;
  - crear `A/core/designsystem/component/CompletingItemsTest.kt`;
  - modificar `T/feature/spaces/TodayAgendaTest.kt`.

**Interfaces:**
- Consume: `motionSpec`, `BouncySpring` y `rememberReduceMotion` (tarea 19); `LocalAppModule` y `ModulePalette` (tareas 1 y 2).
- Produce:
  - `fun shouldCelebrate(completedByUserId: String?, pendingBefore: Set<String>, pendingAfter: Set<String>): Boolean`: `completedByUserId in pendingBefore && pendingAfter.isEmpty()`. Una carga inicial o un cambio de otro miembro llegan con `completedByUserId = null`.
  - `fun pendingDueTodayOrEarlier(tasks: List<TaskItem>, today: LocalDate, zone: ZoneId): Set<String>` (en `TodayAgenda.kt`): los ids TODO con `dueAt` hasta hoy. Una tarea que se repite y se ha adelantado más allá de hoy sale del conjunto.
  - `@Composable fun Celebration(visible: Boolean, message: String, onFinished: () -> Unit, modifier: Modifier = Modifier)`:
    - un texto centrado con `liveRegion = Polite`;
    - unas 18 partículas pequeñas con los acentos de los siete módulos (`ModulePalette`), que salen del centro y se desvanecen en menos de 1 s;
    - no intercepta toques;
    - con «Quitar animaciones», solo el texto, que se va solo.
  - `@Composable fun rememberCompletingItems(): CompletingItems`, con:
    - `class CompletingItems { fun start(id: String); fun isCompleting(id: String): Boolean }`;
    - un id «completando» se mantiene visible y marcado unos 450 ms con un estado local, aunque Firestore ya lo haya quitado, y después sale con desplazamiento a la derecha y un giro de 4°.
    - **La escritura se lanza al momento**, antes de animar.

- [ ] **Paso 1: Tests que fallan.**
  - `CelebrationTest`:
    - `laUltimaPendienteCompletadaPorElUsuarioCelebra`;
    - `laCargaInicialNoCelebra`;
    - `unCambioRemotoNoCelebra`;
    - `unaListaQueYaEstabaVaciaNoCelebra`.
  - `TodayAgendaTest.unaRecurrenteAdelantadaNoCuentaComoPendienteDeHoy`.
  - `CompletingItemsTest.marcarTresMuySeguidosEscribeLasTresAlMomento` (Review Focus 4): una lista de prueba con `rememberCompletingItems`. Pulsar tres casillas en menos de 200 ms llama tres veces a `onComplete` antes de avanzar el reloj, y las tres filas salen.
- [ ] **Paso 2: Comprobar que fallan.**
- [ ] **Paso 3: Implementar.**
  - **Tareas (filas pendientes), Compra (pendientes) y las tareas de Hoy:**
    - al marcar, se lanza la escritura y `completing.start(id)`;
    - la casilla rebota (`BouncySpring`) y el texto se tacha;
    - una vibración corta (`HapticFeedbackType.ToggleOn`).
  - **Celebración:**
    - en Hoy y en Tareas, con `pendingDueTodayOrEarlier` antes y después del toque del usuario: «Todo hecho por hoy» (`celebration_today`);
    - en Compra, con los pendientes de toda la lista: «Compra hecha» (`celebration_shopping`);
    - el id del usuario se guarda al tocar y se limpia al recibir la lista nueva.
  - **Cascada:**
    - en Hoy, las filas entran con un desfase de unos 50 ms (fundido y desplazamiento de 14 dp, `BouncySpring`), solo la primera vez que la agenda pasa a `Content`;
    - en Comidas y Deporte, lo mismo al cambiar de día;
    - con «Quitar animaciones», no hay cascada.
- [ ] **Paso 4: Comprobar que pasan** los tests y la puerta.
