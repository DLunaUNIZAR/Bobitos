# Vista Día del Calendario a escala de la duración. Plan de implementación

> **Para agentes:** se ejecuta con la skill `delegating-plan-execution` del usuario. El marco es superpowers:executing-plans.
> - Es un **plan pequeño** (2 tareas): pasos 1, 2, 3, 5 y 6, sin `simplify`.
> - Cada tarea la hace un subagente `sonnet`, en serie. La revisión final la hace `opus` (si falla, `fable`).
> - Los commits, el push y el PR los hace el coordinador.
> - **Interfaz:** la Task 2 rehace la vista Día (`Frontend: impeccable:impeccable layout`). La revisión de diseño (después
>   del paso 3) mira la vista Día del calendario de un espacio y del calendario personal, en tema claro y oscuro, con la
>   letra al 130 % y con 3 eventos solapados.
> - **Ninguna tarea cambia `firestore.rules`, `migrations/`, los repositorios, los ViewModels ni los scripts de Node.**
> - El usuario pidió saltar la lluvia de ideas: no hay spec. Las decisiones de diseño están en «Decisiones» y las aprobó
>   con el plan.
> - Los pasos usan casillas (`- [ ]`).

Paralelo: no (la Task 2 depende de la Task 1, y su test instrumentado usa el único emulador)

**Objetivo:** que en la vista Día cada evento con hora ocupe en vertical lo que dura (de su inicio a su fin) y que los
eventos que se solapan se pinten uno al lado del otro, en el calendario de cada espacio y en el personal.

**Causa del fallo:** `DayHourGrid` (`CalendarScreen.kt`) agrupa los eventos por la hora de inicio
(`groupBy { startAt…hour }`) y pinta cada uno como una tarjeta de altura fija dentro de la fila de esa hora (`HourRow`).
Nunca lee `endAt`: un evento de 16:30 a 18:30 se ve igual que uno de 16:30 a 17:00, y dos eventos solapados en horas
distintas no se ven solapados. Además, un evento que empieza el día anterior y cruza la medianoche sale en la fila de su
hora de inicio (por ejemplo, a las 22:00 del día siguiente).

**Arquitectura:**
- La Task 1 añade la geometría como funciones puras en `DayTimelineLayout.kt` (minutos de arriba y abajo de cada
  evento en el día, y su columna entre los solapados) y dos formateadores de rango en `BobitosDates`.
- La Task 2 cambia la lista de 24 filas por una línea de tiempo: 24 franjas de altura fija con la hora y el gesto de
  crear, y encima las tarjetas de los eventos colocadas con `DayTimelineLayout`. Va en un fichero nuevo,
  `DayTimeline.kt`; `DayHourGrid` sigue en `CalendarScreen.kt` y la usa. `EventRow` (lista de la semana y eventos de
  todo el día) pasa a enseñar el rango.

**Tecnología:** Kotlin, Jetpack Compose y Material 3, `java.time`. Tests JUnit 4 en JVM y tests instrumentados de Compose
(`createAndroidComposeRule<ComponentActivity>()`).

**Spec:** no hay (el usuario pidió saltar la lluvia de ideas). Hace de spec la sección «Decisiones».

## Decisiones

1. **Escala:** cada hora mide `DayTimelineDefaults.HourHeight` = `64.dp` multiplicado por `max(1f, fontScale)`, para que
   el texto de las tarjetas crezca con la letra.
2. **Posición:** la tarjeta empieza en el minuto de inicio y acaba en el de fin, dentro del día mostrado. Lo que cae
   fuera del día se recorta: un evento de 22:00 a 02:00 se ve de 22:00 a 24:00 un día y de 00:00 a 02:00 el siguiente.
3. **Mínimo:** una tarjeta mide como mínimo `MIN_VISUAL_MINUTES` = 45 minutos (48 dp con la letra normal, el mínimo de
   toque de Android). Esa altura mínima cuenta también para decidir qué se solapa, para que no se tapen. Si no cabe
   antes de las 24:00, la tarjeta sube hasta acabar a las 24:00.
4. **Solapados:** los eventos que se solapan (aunque sea en cadena) forman un grupo y se reparten el ancho a partes
   iguales, en columnas. Cada evento va a la primera columna libre, en orden de inicio (y, a igual inicio, el más largo
   primero). El número de columnas es el del grupo entero.
5. **Texto de la tarjeta:** título y rango («16:30 – 18:30»); los participantes, si caben. Si el evento cruza de día, el
   rango lleva las fechas («21 oct, 22:00 – 22 oct, 02:00»). Un evento que acaba justo a las 00:00 del día siguiente
   cuenta como del mismo día («22:00 – 00:00»).
6. **Acciones:** tocar la tarjeta abre el editor y su ⋮ ofrece «Editar» y «Eliminar», como ahora (el editor no tiene
   «Eliminar»). Mantener pulsado un hueco crea un evento a la hora en punto de esa franja, como ahora; cada franja sigue
   teniendo la acción de TalkBack «Crear evento a las HH:00».
7. **Desplazamiento inicial:** al abrir un día, la vista se coloca en la hora en punto del primer evento con hora, o en
   las 08:00 si no hay ninguno. Solo una vez por día mostrado: no salta al rotar ni cuando llega un evento nuevo.
8. **Sin cambios:** la cabecera «Mantén pulsada una hora…», el bloque «Todo el día» con las tareas, la vista Semana (salvo
   el rango de `EventRow`) y la vista Mes.

## Restricciones globales

**Rutas**
- `J/` = `app/src/main/java/com/dlunaunizar/bobitos/`.
- `T/` = `app/src/test/java/com/dlunaunizar/bobitos/` (JVM).
- `A/` = `app/src/androidTest/java/com/dlunaunizar/bobitos/` (instrumentados).

**Entorno y comandos**
- **Java:** antes de cada `./gradlew`, ejecuta `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- **Test JVM suelto:** `./gradlew :app:testDebugUnitTest --tests "<FQCN>"`.
- **Test instrumentado suelto:** `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`.
  Si `adb devices` no lista el emulador: `export PATH="$HOME/Library/Android/sdk/emulator:$HOME/Library/Android/sdk/platform-tools:$PATH"; nohup emulator -avd Pixel_10 -no-boot-anim >/dev/null 2>&1 & adb wait-for-device`.
- **Puerta de cada tarea (`gate.sh`):** `./gradlew ktlintCheck detekt testDebugUnitTest assembleDebug lintDebug :app:compileDebugAndroidTestKotlin`,
  más el instrumentado que nombre la tarea.
- Si un cambio rompe `config/detekt/baseline.xml`, regenera el baseline con `./gradlew detektBaseline` e inclúyelo en la
  tarea. Su diff solo puede cambiar entradas de las funciones que la tarea toca.

**Zona horaria en los tests**
- JVM: pasa siempre `ZoneId.of("Europe/Madrid")` explícita.
- Instrumentados: la vista usa `ZoneId.systemDefault()`. Crea los instantes con
  `LocalDateTime.of(…).atZone(ZoneId.systemDefault()).toInstant()`, nunca con `Instant.parse("…Z")`.

**Textos**
- Separador de rangos: « – » (raya corta con un espacio a cada lado), el de `BobitosDates.weekRange`.
- No hay `strings.xml` nuevos: la franja usa `calendar_create_at`, y el ⋮, `more_options_for`.

**Estilo:**
- comentarios y KDoc en español, con la densidad del fichero;
- nombres de test en inglés, entre comillas invertidas en JVM y en camelCase en los instrumentados, como en sus
  ficheros.

## Review Focus

1. **Tres o cuatro eventos solapados en un móvil de 360 dp con la letra al 130 %:** cada tarjeta deja leer el principio
   del título y el rango (con puntos suspensivos, sin cortar a media línea) y su ⋮ se puede pulsar. Lo mira la revisión
   de diseño; la Task 2 prueba el reparto del ancho (`overlappingEventsSitSideBySide`).
2. **Evento corto (15 min) seguido de otro a los 30 min:** no se tapan y los dos se pueden pulsar. Lo prueba la Task 1
   (`short events take the minimum height and push overlaps aside`).
3. **Cambiar de día con las flechas, o llegar un evento nuevo de otro miembro:** al cambiar de día la vista se coloca en
   el primer evento del nuevo día; si solo llega un evento, no salta. Lo prueba la Task 2
   (`theDayOpensAtTheFirstEventOnlyOnce`).
4. **Mantener pulsado un hueco entre dos eventos:** crea a la hora de esa franja; las tarjetas no tapan el gesto de las
   franjas libres. Lo prueba la Task 2 (`holdingAnEmptySpotCreatesAtThatHour`).
5. **Evento que cruza la medianoche, visto el día en que acaba:** empieza arriba, en las 00:00, no en la hora en que
   empezó el día anterior. Lo prueba la Task 1 (`an event crossing midnight is clipped to each day`).

---

### Task 1: Geometría de la línea de tiempo y rangos de hora

`Depende de: ninguna`

`Frontend: no toca la interfaz (funciones puras)`

**Files:**
- Create: `J/feature/calendar/DayTimelineLayout.kt`.
- Modify: `J/core/common/BobitosDates.kt`: añade `timeRange` y `dateTimeRange`.
- Test: `T/feature/calendar/DayTimelineLayoutTest.kt` (nuevo), `T/core/common/BobitosDatesTest.kt`.

**Interfaces:**
- Consumes: `CalendarEvent` (`allDay`, `startAt`, `endAt`, `overlaps`), `visibleInterval(CalendarDisplayMode.DAY, zone)`
  de `CalendarInterval.kt`, `BobitosDates.dateTime` y `BobitosDates.time`.
- Produces (en `J/feature/calendar/DayTimelineLayout.kt`):
  - `internal const val MINUTES_IN_DAY = 1440`.
  - `internal const val MIN_VISUAL_MINUTES = 45`.
  - `internal data class TimelineEvent(val event: CalendarEvent, val topMinute: Int, val bottomMinute: Int, val column: Int, val columns: Int)`.
    `topMinute` y `bottomMinute` son los minutos desde las 00:00 del día ya recortados y con el mínimo aplicado
    (`0 ≤ topMinute < bottomMinute ≤ 1440`); `column` va de `0` a `columns - 1`.
  - `internal fun layoutDayEvents(events: List<CalendarEvent>, day: LocalDate, zone: ZoneId): List<TimelineEvent>`:
    descarta los `allDay` y los que no solapan el día; devuelve en orden de `topMinute` y, a igual valor, del más
    largo al más corto.
  - `internal fun firstTimedHour(events: List<CalendarEvent>, day: LocalDate, zone: ZoneId): Int?`: la hora en punto
    (`topMinute / 60`) del primer evento de `layoutDayEvents`, o `null` si no hay ninguno.
- Produces (en `J/core/common/BobitosDates.kt`):
  - `fun timeRange(start: LocalDateTime, end: LocalDateTime): String`: mismo día → «16:30 – 18:30»; si no,
    `dateTimeRange(start, end)`.
  - `fun dateTimeRange(start: LocalDateTime, end: LocalDateTime): String`: mismo día → «22 oct, 16:30 – 18:30»; si no,
    «21 oct, 22:00 – 22 oct, 02:00».
  - En las dos, «mismo día» incluye un `end` que es justo las 00:00 del día siguiente a `start`, que se escribe «00:00».

- [ ] **Paso 1: tests nuevos en `DayTimelineLayoutTest`.** Usa un helper `event(id, start: LocalDateTime, end: LocalDateTime, allDay = false)`
  como el de `CalendarFilteringTest` y la zona `Europe/Madrid`. El día es el 22 de octubre de 2026 salvo donde se dice.
  Cada test compara la lista de `(id, topMinute, bottomMinute, column, columns)`.
  1. `an event spans its real duration`: «Inglés» 16:30–18:30 → `("ingles", 990, 1110, 0, 1)`.
  2. `overlapping events share the width and reuse free columns`: «Inglés» 16:30–18:00, «Cine» 18:30–21:30 y «Pádel»
     20:00–21:00 → `[("ingles", 990, 1080, 0, 1), ("cine", 1110, 1290, 0, 2), ("padel", 1200, 1260, 1, 2)]`.
  3. `a chain of overlaps forms one group`: A 09:00–11:00, B 09:30–10:00, C 10:30–11:30 →
     `[("a", 540, 660, 0, 2), ("b", 570, 615, 1, 2), ("c", 630, 690, 1, 2)]` (B mide 45 min y C cabe en su columna).
  4. `equal starts put the longer event first`: X 09:00–10:00 e Y 09:00–12:00, pasados en ese orden →
     `[("y", 540, 720, 0, 2), ("x", 540, 600, 1, 2)]`.
  5. `short events take the minimum height and push overlaps aside`: P 10:00–10:15 y Q 10:30–11:00 →
     `[("p", 600, 645, 0, 2), ("q", 630, 675, 1, 2)]`.
  6. `a short event at the end of the day moves up to fit`: 23:50–23:59 → `(…, 1395, 1440, 0, 1)`.
  7. `an event crossing midnight is clipped to each day`: 21 oct 22:00 → 22 oct 02:00. El 21: `(…, 1320, 1440, 0, 1)`;
     el 22: `(…, 0, 120, 0, 1)`.
  8. `all-day events and events of other days are left out`: uno `allDay` del 22 y otro del 23 a las 10:00 → `[]`.
  9. `firstTimedHour is the hour of the first event or null`: con los eventos del test 2, `16`; con la lista del test 8,
     `null`.
- [ ] **Paso 2: tests nuevos en `BobitosDatesTest`.**
  - `timeRange inside a day, ending at midnight and across days`: 22 oct 16:30–18:30 → «16:30 – 18:30»; 22 oct 22:00 –
    23 oct 00:00 → «22:00 – 00:00»; 21 oct 22:00 – 22 oct 02:00 → «21 oct, 22:00 – 22 oct, 02:00».
  - `dateTimeRange inside a day and across days`: 22 oct 16:30–18:30 → «22 oct, 16:30 – 18:30»; 22 oct 22:00 – 23 oct
    00:00 → «22 oct, 22:00 – 00:00»; 21 oct 22:00 – 22 oct 02:00 → «21 oct, 22:00 – 22 oct, 02:00».
- [ ] **Paso 3: RED.** `./gradlew :app:testDebugUnitTest --tests "com.dlunaunizar.bobitos.feature.calendar.DayTimelineLayoutTest" --tests "com.dlunaunizar.bobitos.core.common.BobitosDatesTest"`
  no compila: `layoutDayEvents`, `timeRange` y `dateTimeRange` no existen.
- [ ] **Paso 4:** implementa lo de «Produces».
  - Minutos de un instante en el día: `0` si es anterior al inicio del día, `1440` si es igual o posterior al inicio del
    día siguiente y, si no, la hora local del instante en minutos (así las tarjetas cuadran con las etiquetas de hora
    también en los días de cambio de hora).
  - Mínimo: `bottom = max(bottom, top + MIN_VISUAL_MINUTES)`; si pasa de 1440, `bottom = 1440` y
    `top = 1440 - MIN_VISUAL_MINUTES`.
  - Columnas (algoritmo):
    ```
    ordena por (topMinute, -bottomMinute, id)
    grupo actual = []; fin del grupo = 0
    para cada evento:
        si el grupo no está vacío y topMinute >= fin del grupo: cierra el grupo (columns = nº de columnas usadas)
        columna = la primera cuyo último bottomMinute <= topMinute; si no hay, una nueva
        añádelo al grupo; fin del grupo = max(fin del grupo, bottomMinute)
    cierra el último grupo
    ```
- [ ] **Paso 5: GREEN.** El mismo comando pasa. Después pasa la puerta.

### Task 2: La vista Día como línea de tiempo

`Depende de: Task 1`

`Frontend: impeccable:impeccable layout J/feature/calendar/DayTimeline.kt`

**Files:**
- Create: `J/feature/calendar/DayTimeline.kt`: `DayTimelineDefaults`, `HourSlot`, `TimelineEventCard` y `DayTimeline`.
- Modify: `J/feature/calendar/CalendarScreen.kt`: `DayHourGrid` (nuevo parámetro `day` y usa `DayTimeline`), borra
  `HourRow`, y `EventRow` enseña el rango; el `DayHourGrid(…)` de `CalendarScreen` pasa `day = state.focusedDate`.
- Modify: `J/feature/calendar/PersonalCalendarScreen.kt`: su `DayHourGrid(…)` pasa `day = state.focusedDate`.
- Test: `A/feature/calendar/DayTimelineTest.kt` (nuevo), `A/feature/calendar/CalendarSemanticsTest.kt`.

**Interfaces:**
- Consumes: `layoutDayEvents`, `firstTimedHour`, `TimelineEvent`, `MINUTES_IN_DAY` (Task 1);
  `BobitosDates.timeRange`, `BobitosDates.dateTimeRange` (Task 1); `ClickableCard`, `MoreOptionsButton`,
  `EventColor.accent()`, `R.string.calendar_create_at`, `calendar_edit`, `calendar_delete`, `calendar_general_event`.
- Produces (en `J/feature/calendar/DayTimeline.kt`):
  - `internal object DayTimelineDefaults { val HourHeight: Dp = 64.dp; val LabelWidth: Dp = 52.dp }`.
  - `internal fun timelineEventTag(id: String): String = "timeline-event-$id"`: `testTag` de cada tarjeta.
  - `@Composable internal fun HourSlot(hour: Int, height: Dp, canWrite: Boolean, onLongPress: () -> Unit, modifier: Modifier = Modifier)`:
    la etiqueta «HH:00» a la izquierda (ancho `LabelWidth`), una línea fina arriba y, si `canWrite`, el mantenido con
    vibración, la indicación al pulsar y la acción de TalkBack de `calendar_create_at`, como el `HourRow` de ahora. Sin
    `onClick`.
  - `@Composable internal fun DayTimeline(day: LocalDate, events: List<CalendarEvent>, canWrite: Boolean, onEdit: (CalendarEvent) -> Unit, onDelete: (String) -> Unit, onCreateAt: (LocalTime) -> Unit, deleteEnabled: Boolean, modifier: Modifier = Modifier)`:
    un bloque de `24 × hourHeight` de alto con las 24 `HourSlot` y, encima, una `TimelineEventCard` por cada
    `TimelineEvent`, en `x = LabelWidth + column × ancho / columns`, `y = topMinute × hourHeight / 60`, ancho
    `ancho / columns` y alto `(bottomMinute − topMinute) × hourHeight / 60` (`ancho` = el del bloque menos `LabelWidth`).
    `hourHeight = HourHeight × max(1f, LocalDensity.current.fontScale)`.
  - `DayHourGrid(events, tasks, canWrite, onEdit, onDelete, onCreateAt, day: LocalDate, modifier, deleteEnabled, listState)`:
    los eventos con hora ya no van en filas por hora: un único `item(key = "timeline")` con `DayTimeline` sustituye a
    los 24 `item` de `HourRow`. Lo demás de la lista no cambia.

- [ ] **Paso 1: tests nuevos en `DayTimelineTest`** (`createAndroidComposeRule<ComponentActivity>()`, `BobitosTheme`,
  helper `event(id, title, start: LocalDateTime, end: LocalDateTime)` como el de `CalendarSemanticsTest`, día 22 oct
  2026, zona del sistema como dicen las restricciones globales). Los que miden usan `DayHourGrid` en un
  `Box(Modifier.size(width = 360.dp, height = 640.dp))` y leen `onNodeWithTag(timelineEventTag(id)).getUnclippedBoundsInRoot()`.
  1. `aTimedEventIsAsTallAsItsDuration`: «Inglés» 16:30–18:30 mide `128.dp` de alto (tolerancia ±4 dp) y «Corto»
     09:00–09:45, `48.dp` (±4 dp).
  2. `overlappingEventsSitSideBySide`: «Cine» 18:30–21:30 y «Pádel» 20:00–21:00. Los dos anchos son iguales (±1 dp),
     cada uno mide al menos `120.dp` (la mitad de `360.dp − LabelWidth` son 154 dp) y `cine.right <= padel.left + 1.dp`.
  3. `theCardShowsTheTimeRange`: la tarjeta de «Inglés» contiene el texto «16:30 – 18:30».
  4. `holdingAnEmptySpotCreatesAtThatHour`: con `canWrite = true` y «Inglés» 16:30–18:30, un
     `performTouchInput { longClick(center) }` sobre el nodo `hasChildText("10:00")` llama a `onCreateAt(LocalTime.of(10, 0))`;
     tocar la tarjeta de «Inglés» llama a `onEdit` con ese evento y no a `onCreateAt`.
  5. `theDayOpensAtTheFirstEventOnlyOnce`: con un estado `day` y una lista mutables:
     - día 22 con «Inglés» 16:30: `onNodeWithText("16:00").assertIsDisplayed()` y `"00:00"` no se ve
       (`assertIsNotDisplayed`);
     - se añade a la lista otro evento a las 07:00 del 22: `"16:00"` sigue visible;
     - `day` pasa al 23, sin eventos: `"08:00"` visible y `"07:00"` no.
- [ ] **Paso 2: tests en `CalendarSemanticsTest`.**
  - `anHourRowIsNotAnEmptyButton` pasa a `anHourSlotIsNotAnEmptyButton`, con
    `HourSlot(hour = 10, height = 64.dp, canWrite = writable, onLongPress = { created++ })` y las mismas aserciones.
  - Nuevo `aTimedEventRowShowsItsRange`: `EventRow` con una copia de `event` cuyo `startAt`/`endAt` son el 17 oct de
    19:00 a 20:00 en la zona del sistema enseña «17 oct, 19:00 – 20:00».
- [ ] **Paso 3: RED.** `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.dlunaunizar.bobitos.feature.calendar.DayTimelineTest`
  no compila: `DayTimeline.kt` no existe y `DayHourGrid` no tiene `day`.
- [ ] **Paso 4:** antes de escribir la interfaz, invoca la skill `impeccable:impeccable` con los argumentos
  `layout J/feature/calendar/DayTimeline.kt` (rutas completas) y sigue sus criterios, incluida su preparación. Decide
  con ella el aspecto de `TimelineEventCard` dentro de estas reglas:
  - la barra de color del evento (`event.color.accent()`), el título y el rango de `BobitosDates.timeRange` siempre;
    los participantes (o `calendar_general_event`) solo si caben;
  - con poca altura o poco ancho, título y rango en una línea y con puntos suspensivos, nunca cortados a media línea;
  - un pequeño hueco entre columnas y entre tarjetas seguidas (una que acaba a las 17:00 y otra que empieza a esa hora
    no se tocan), sin romper las tolerancias del Paso 1;
  - toque, ⋮ con «Editar»/«Eliminar» y `deleteEnabled`, `mergeDescendants` y `testTag(timelineEventTag(id))` como en
    `EventRow`.
- [ ] **Paso 5:** implementa `DayTimeline.kt` y los cambios de `CalendarScreen.kt` y `PersonalCalendarScreen.kt`.
  - Desplazamiento inicial (decisión 7), en `DayHourGrid`: un `LaunchedEffect(day)` que, si no se hizo ya para ese día
    (marca en `rememberSaveable(day)`), lleva `listState` al índice del `item` `"timeline"` con el desplazamiento de
    `(firstTimedHour(events, day, zone) ?: 8) × hourHeight` en píxeles.
  - `EventRow`: con hora, `BobitosDates.dateTimeRange(inicio, fin)` en la zona del sistema; «Todo el día» no cambia.
- [ ] **Paso 6: GREEN.** El comando del Paso 3 pasa, y también
  `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.dlunaunizar.bobitos.feature.calendar.CalendarSemanticsTest`.
  Después pasa la puerta.
