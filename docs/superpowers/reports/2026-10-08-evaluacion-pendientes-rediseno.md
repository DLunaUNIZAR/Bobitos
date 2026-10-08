# Evaluación de pendientes del rediseño

**Fecha:** 2026-10-08 · **Base evaluada:** `main` en `f5e77ec` (tras fusionar la simplificación, PR #235) · **Plan:** `docs/superpowers/plans/2026-10-08-evaluacion-pendientes-rediseno.md`

Se evalúan 13 puntos que quedaron sin aplicar: los 4 problemas reales de `/code-review` (A–D) y los 9 hallazgos de `/simplify` no aplicados (S1–S9). Cada uno se mide en el código y, si el veredicto no es obvio, se prototipa en una rama `spike/…` desechable (borrada al acabar). No hay dispositivo Android en la máquina: lo que dependa de pantalla, rotación, back o TalkBack figura como **«sin verificar en dispositivo»** y no cuenta como evidencia.

## Rúbrica

| Eje | 1 | 2 | 3 |
|---|---|---|---|
| **Beneficio** | cosmético / ahorro < 50 líneas | evita un fallo raro o ahorra 50–200 líneas o lecturas ocasionales | evita un fallo que verá un usuario normal, o ahorra > 200 líneas / lecturas en cada uso |
| **Coste** | ≤ 3 archivos, < 1 h | 4–10 archivos o toca código fuera del rediseño | > 10 archivos, migración de datos o reglas |
| **Riesgo** | sin cambio de comportamiento y cubierto por tests | cambia comportamiento interno sin test automático | cambia lo que ve el usuario o los datos persistidos, sin test |

**Veredicto:** `Hacer ya` si Beneficio ≥ 2, Beneficio ≥ Coste y Riesgo ≤ 2 · `Más adelante` si Beneficio ≥ 2 pero no cumple lo anterior · `No` si Beneficio = 1 y (Coste ≥ 2 o Riesgo ≥ 2). En A–D, Beneficio ≥ 2 por definición.

## Resumen

| Punto | Qué | Beneficio | Coste | Riesgo | Veredicto |
|---|---|---|---|---|---|
| A | `values-night` y parpadeo oscuro al arrancar | 2 | 1 | 2 | Hacer ya |
| B | Evento huérfano tras deshacer su borrado | 2 | 2 | 2 | Hacer ya |
| C | Recordatorio a medianoche por cada actividad | 2 | 1 | 2 | Hacer ya |
| D | Nombre de cuenta borrada reescrito en el evento (y actividades nunca anonimizadas) | 2 | 1 | 2 | Hacer ya |
| S1 | Borradores con `@Parcelize` | 3 | 3 | 2 | Hacer ya (refactor propio) |
| S2 | ViewModels sin volver a «cargando» al reobservar | 2 | 3 | 2 | Más adelante |
| S3 | Grafo anidado para «Más» | 1 | 2 | 3 | No |
| S4 | Metadatos de ruta en el enum | 1 | 2 | 2 | No (como mucho, un test de coherencia) |
| S5 | Deporte reutiliza el documento de evento del calendario | 1 | 2 | 2 | No |
| S6 | Fuente propia de espacios para el selector | 1 | 2 | 2 | No |
| S7 | Validación por campo en los editores | 3 (Calendario) / 1 (resto) | 1 / 2 | 1 | Hacer ya (solo Calendario) |
| S8 | detekt: ignorar `@Composable` en métodos largos | 1 | 1 | 1 | Decisión del usuario (recomendado: no) |
| S9 | Renombres del borrador en el editor de Compra | 1 | 1 | 1 | No (solo de paso) |

## Orden recomendado

**6 «Hacer ya»** (A, B, C, D, S7-Calendario y S1), agrupados en 3 PRs por archivos compartidos. Los dos primeros son correcciones que ve el usuario; el tercero es solo refactor.

1. **PR «Calendario: deshacer y título obligatorio»** (B + S7). Ambos tocan `CalendarScreen` y `CalendarViewModel`, así que se hacen juntos para no pisarse. Unos 9 archivos, +80. Tests: `CalendarValidationTest` y el test de emulador de restaurar con el mismo id. Primero porque S7 es el fallo más fácil de provocar (guardar un evento sin título pierde lo escrito).
2. **PR «Recordatorios, anonimización y tema al arrancar»** (C + D + A). Tres arreglos independientes y pequeños: 2 archivos borrados, 3 archivos de recordatorios con test y 2 líneas en `FirebaseAccountRepository`. A es el único que solo se valida en dispositivo (arranque en frío con el móvil en oscuro).
3. **PR «Borradores con `@Parcelize`»** (S1), en dos lotes: borradores simples, y luego los que tienen enums o fechas. Va después de los dos anteriores porque toca los mismos editores (Calendario incluido). Si algún día se hace S2, conviene antes que S1; como S2 queda en «Más adelante», no se espera por él.

**Más adelante:** S2 (ViewModels sin «cargando» al reobservar, por lotes de 2–3) y la parte extra de D (anonimizar `cookName` de comidas, que necesita cambiar reglas).

**No:** S3, S4, S5, S6, S9. S4 tiene una alternativa barata (test de coherencia de rutas) por si se añaden pantallas de espacio. S5 compensa solo si cambia el contrato de eventos. S9, solo de paso.

**A decidir:** S8 (recomendado no tocar la configuración de detekt).

Las ramas `spike/…` se han borrado todas y `main` no se ha tocado.

**Estado (2026-10-08):** los tres PRs propuestos están hechos y fusionados, y han salido en la beta 0.1.0-beta.16.
- #236: B + S7.
- #237: C + D + A. En D se añadió también Notas, que tenía el mismo hueco.
- #238: S1.

---

## A — `values-night` y parpadeo oscuro al arrancar
- **Qué es:** la Fase 1 (commit `7804ffb`, PR #229) volvió a añadir `res/values-night/{themes,colors}.xml` (tema padre oscuro, fondo de ventana `#141513`). El commit `3cd147e` (PR #57) los había borrado a propósito: «Al crear una cuenta con el móvil en oscuro, la app se veía oscura… Así el tema es claro por defecto y solo pasa a oscuro/sistema si el usuario lo elige».
- **Medido:**
  - El tema por defecto de la app es `LIGHT` (`DataStoreThemePreferenceRepository.kt:22`, `ThemeViewModel.kt:23`).
  - Con `values-night`, la ventana previa a Compose y el splash siguen al **sistema**; `MainActivity` corrige el fondo con `setBackgroundDrawable` dentro de `DisposableEffect(darkTheme)`, es decir, después de `setContent`.
  - Por tanto, hoy: móvil en oscuro + app en Claro (el caso por defecto) ⇒ destello oscuro en cada arranque en frío. Sin `values-night`: el destello pasa a ser claro y solo para quien elige Oscuro a mano.
- **Prototipo (`spike/eval-a`, borrado):** borrar los dos archivos (2 archivos, −14 líneas) y dejar el `setBackgroundDrawable` de la Fase 1, que sigue cubriendo rotaciones y cambios de tema. Compila, pasan tests, ktlint, detekt y `assembleDebug` sin tocar nada más.
- **Puntuación:** Beneficio 2 (destello visible en cada arranque para el caso por defecto con el móvil en oscuro) · Coste 1 · Riesgo 2 (vuelve al comportamiento ya validado en el PR #57; el destello en sí queda sin verificar en dispositivo).
- **Veredicto:** **Hacer ya.**
- **Si se hace:** PR de 2 archivos borrados. Verificar en dispositivo: móvil en oscuro con la app en Claro, arranque en frío sin destello oscuro; con la app en Oscuro, aceptar el destello claro breve que ya se aceptó en el PR #57.

## B — Evento huérfano tras deshacer su borrado
- **Qué es:** cada actividad guarda el id de su evento (PR #228). Si en Calendario se borra ese evento y se pulsa «Deshacer», el deshacer lo recrea con un **id nuevo** (`viewModel.save(null, event.toInput())`). La actividad sigue apuntando al id borrado: editarla ya no actualiza el evento restaurado y borrarla lo deja huérfano en el calendario del espacio y en los personales.
- **Medido:**
  - Salida (a), restaurar con el mismo id: las reglas de creación (`validNewEvent`) no restringen el id del documento, así que basta con que el cliente haga `set` sobre el id original. Solo hay un deshacer de eventos (`CalendarScreen`); el calendario personal no tiene.
  - Salida (b), que Deporte recree el evento si no existe: contradice la decisión de la 3b de respetar un borrado hecho a propósito. Descartada.
  - No hay ningún test ni fake de `CalendarRepository`/`CalendarViewModel`: un test de ViewModel del deshacer necesitaría fakes de tres repositorios (calendario, espacios y tareas).
- **Prototipo (`spike/eval-b`, borrado):** `createEvent(spaceId, input, eventId: String? = null)` en `CalendarRepository`/`FirestoreCalendarRepository`, `CalendarViewModel.restore(eventId, input)` y el deshacer de `CalendarScreen` llamándolo. Test de emulador «deshacer el borrado de un evento lo restaura con su id original» (`setDoc` → `deleteDoc` → `setDoc` con el mismo id): pasa (95/95). **5 archivos, +21/−4**; compila y pasan tests, ktlint y detekt. (La prueba de reglas pasaría también antes del cambio: lo que se arregla es el cliente; el test documenta que las reglas lo permiten.)
- **Puntuación:** Beneficio 2 (fallo raro: borrar desde Calendario el evento de una actividad y deshacer) · Coste 2 (toca el repositorio de calendario, fuera del rediseño) · Riesgo 2 (comportamiento interno: el evento restaurado conserva su id; sin test de cliente).
- **Veredicto:** **Hacer ya.**
- **Si se hace:** PR con el prototipo y, si se quiere test de cliente, un `FakeCalendarRepository` mínimo más los fakes de espacios y tareas que ya existen en `TasksViewModelTest` (moverlos a un archivo de test compartido). Verificar en dispositivo: borrar desde Calendario el evento de una actividad, deshacer, editar la actividad en Deporte y comprobar que el evento cambia; borrar la actividad y comprobar que el evento desaparece.

## C — Recordatorio a medianoche por cada actividad
- **Qué es:** desde el PR #228 cada actividad crea un evento de todo el día que empieza a las 00:00 e incluye al organizador. `ReminderScheduler` recuerda cualquier evento a su `startAt`, sin tratar `allDay`, así que quien tenga los recordatorios activados recibe «Evento: …» a medianoche (o a las 23:30 de la víspera con 30 min de antelación) por cada actividad, también el organizador aunque no participe.
- **Medido:**
  - `data/reminders` no menciona `allDay` en ningún sitio: el problema afecta también a los eventos de todo el día creados a mano, no solo a Deporte.
  - Ya existe el patrón a seguir: las comidas, que tampoco tienen hora, se recuerdan a una hora fija por franja (`mealReminderInstant`), cubierto por `ReminderTimingTest`.
  - La antelación por defecto es `AT_TIME`; los recordatorios son opcionales (se activan en Perfil).
- **Prototipo (`spike/eval-c`, borrado):** `allDayEventReminderInstant(date, zone)` = 9:00 del propio día, usado en `ReminderScheduler` para eventos `allDay` (el filtro del horizonte pasa a usar esa hora). Test nuevo en `ReminderTimingTest` (falla antes, pasa después): un evento de todo el día del día D con 30 min de antelación se programa a D 08:30. **3 archivos, +31/−2**; compila y pasan tests, ktlint y detekt.
- **Alternativa no prototipada:** no meter al organizador en el evento cuando no participa. Es un cambio de producto (la actividad dejaría de verse en su calendario personal), no un arreglo.
- **Puntuación:** Beneficio 2 (fallo visible para quien usa recordatorios, con cada actividad y cada evento de todo el día) · Coste 1 · Riesgo 2 (cambia la hora de un aviso, cubierto por test unitario; el aviso real queda sin verificar en dispositivo).
- **Veredicto:** **Hacer ya.**
- **Si se hace:** PR pequeño con el prototipo tal cual. Verificar en dispositivo con recordatorios activados: una actividad de mañana avisa a las 9:00 (menos la antelación), no a medianoche.

## D — Nombre de cuenta borrada reescrito en el evento
- **Qué es:** al editar una actividad, el repositorio de Deporte (PR #228) copia al evento enlazado el nombre del organizador desde `activity.createdByName`. Si esa persona borró su cuenta, el evento ya estaba anonimizado («Usuario eliminado») pero la actividad no, así que la edición vuelve a escribir su nombre real en el evento.
- **Medido — la causa es más amplia de lo que parecía:**
  - `FirebaseAccountRepository.anonymizeDisplayNames` recorre `shoppingItems`, `tasks`, `events` y `meals`, **pero nunca `activities`**. La regla `validActivityAnonymization` y su prueba de emulador («un usuario puede anonimizar su nombre en sus actividades…») existen desde Deporte F1 (`3e40346`), pero el cliente no las usa: **desde entonces, borrar la cuenta deja el nombre real en todas sus actividades**, no solo en el evento.
  - El arreglo es añadir la colección a la lista; `anonymousNameUpdates` ya trata `createdBy`/`createdByName` y `participantIds`/`participantNames`, los campos que tienen las actividades.
- **Prototipo (`spike/eval-d`, borrado):** constante `ACTIVITIES` y su inclusión en la lista. **1 archivo, +2/−1**; compila y pasan tests, ktlint y detekt. La regla no cambia (ya está cubierta por su prueba de emulador).
- **Hallazgo extra (fuera del alcance, anterior al rediseño):** en las comidas, `cookName` (cocinero, PR #203) tampoco se anonimiza, y `validMealAnonymization` solo permite cambiar `createdByName` y `participantNames`, así que arreglarlo exige tocar regla y cliente.
- **Puntuación:** Beneficio 2 (privacidad: afecta a toda cuenta borrada que tuviera actividades; borrar cuenta es poco frecuente) · Coste 1 · Riesgo 2 (modifica datos persistidos al borrar cuenta, con la regla ya probada en emulador; el flujo de cliente no tiene test).
- **Veredicto:** **Hacer ya.** El hallazgo extra de `cookName`: **Más adelante** (Beneficio 2 · Coste 3 por la regla · Riesgo 2).
- **Si se hace:** PR de 2 líneas. Verificación con el emulador: borrar una cuenta que creó y participa en actividades y comprobar que `createdByName` y su entrada de `participantNames` pasan a «Usuario eliminado».

## S1 — Borradores con `@Parcelize`
- **Qué es:** los 12 borradores de los editores (`*Draft`) se guardan con `listSaver` escritos a mano: índices posicionales y casts (`it[3] as String`), y los campos que no caben en un Bundle se guardan como texto (`priorityName`, `colorName`, `startDateIso`…) con getters y `with*` para convertirlos. Con `@Parcelize`, `rememberSaveable` los guardaría sin Saver y con sus tipos reales.
- **Medido:**
  - 12 archivos `*Draft*.kt` con **572 líneas** y **24 Savers**; 25 conversiones `valueOf`/`parse`/`with*` dentro de los borradores y 18 usos de esos campos-texto o `with*` en las pantallas.
  - Tests de borradores: **678 líneas**; la mitad, aproximadamente, son de ida y vuelta del Saver.
  - El plugin de Parcelize ya está en el classpath (viene con el plugin de Android/Kotlin del proyecto): basta `id("org.jetbrains.kotlin.plugin.parcelize")` en `app/build.gradle.kts`; **no** se puede declarar con versión en el catálogo (Gradle lo rechaza).
  - `RecipeDraft` y `BrandDraft` dejarían además de necesitar `toSaved()`/`…FromSaved` en los anfitriones. `ExerciseDraft` (mutable, con `SnapshotStateList`) no se beneficia sin hacerlo inmutable antes.
  - Los tests de ida y vuelta no se pueden mantener en JVM (no hay Robolectric para `Parcel`): se perderían, confiando en el código generado por Parcelize.
- **Prototipo (`spike/eval-s1`, borrado):** plugin aplicado y `NoteDraft` convertido (`@Parcelize data class … : Parcelable`, `rememberSaveable(note?.id) { mutableStateOf(initial) }` sin `stateSaver`, test de ida y vuelta eliminado). **4 archivos, +7/−19**; compila y pasan tests, ktlint, detekt y `assembleDebug`.
- **Extrapolación:** del orden de −200 líneas en `main` y −300 en tests, ~12 borradores + ~10 pantallas tocadas; los campos vuelven a tipos reales (`TaskPriority`, `EventColor`, `LocalDate`…).
- **Puntuación:** Beneficio 3 (> 200 líneas y desaparece el código posicional frágil) · Coste 3 (> 10 archivos) · Riesgo 2 (no cambia lo que ve el usuario; cambia el formato del estado guardado, y un borrador guardado con la versión anterior no se restauraría tras actualizar, como mucho una vez; se pierden los tests de ida y vuelta).
- **Veredicto:** **Hacer ya**, como refactor propio y mecánico, en dos lotes (borradores simples; borradores con enums/fechas). Mejor después de S2 si se decide hacerlo, porque toca los mismos editores.
- **Si se hace:** verificación con compilación y tests; en dispositivo, girar con cada editor abierto (el mismo recorrido de las fases 3a–3c).

## S2 — ViewModels sin volver a «cargando» al reobservar
- **Qué es:** las pantallas hacen `observe()` al entrar y `stopObserving()` al salir; al girar el móvil se repiten ambas. Varios ViewModels vuelven a emitir `UiState.Loading` aunque el espacio sea el mismo, y eso es lo que obligó a añadir `rememberEditorSlot` (mantener el elemento del editor durante la recarga). Arreglarlo en los ViewModels quita también el parpadeo de «cargando» que las propias **listas** muestran al girar.
- **Medido:**
  - 13 ViewModels tienen `stopObserving`/`stop`; todos borran su clave observada al parar. **8** vuelven a emitir `Loading` al reobservar (Calendario, Calendario personal, Comidas, Notas, Compra, Espacios, Deporte, Tareas); 5 no (Ejercicios, Detalle de ingrediente, Ingredientes, Recetas, Rutinas).
  - La clave borrada en `stopObserving` cumple una función (bloquea escrituras sin espacio activo, comentario en `NotesScreen`): el arreglo no debe tocarla, sino recordar aparte el último espacio cargado.
  - `rememberEditorSlot` (10 pantallas) **seguiría haciendo falta** para cerrar el editor si otra persona borra el elemento: S2 no lo elimina, solo lo deja como red de seguridad.
- **Prototipo (`spike/eval-s2`, borrado) en `NotesViewModel`:** `lastLoadedSpaceId`; `observe` solo emite `Loading` si cambia de espacio. Test nuevo en `NotesViewModelTest` con un fake que retiene la segunda suscripción (como Firestore, que no emite al instante): sin el arreglo falla, con él pasa. **2 archivos, +32/−2.** Ojo: con el fake actual, que emite de forma síncrona, el test pasaba sin arreglo; hace falta retener la suscripción para que pruebe algo.
- **Extrapolación:** 8 ViewModels × ~4 líneas + 8 tests con fake retenido ⇒ ~16 archivos. En Calendario el `Loading` al cambiar de rango es legítimo y debe conservarse.
- **Puntuación:** Beneficio 2 (quita el parpadeo de «cargando» al girar en 8 pantallas y robustece los editores en la raíz) · Coste 3 (~16 archivos) · Riesgo 2 (comportamiento interno, cubierto por un test por ViewModel; el parpadeo visible queda sin verificar en dispositivo).
- **Veredicto:** **Más adelante.** Si se hace, conviene antes que S1 (tocan pantallas vecinas) y por lotes de 2–3 ViewModels.
- **Si se hace:** el patrón del prototipo en cada ViewModel con su test; verificación en dispositivo girando cada lista.

## S3 — Grafo anidado para «Más»
- **Qué es:** la jerarquía «Más → Comidas / Deporte / Notas» se codifica apilando rutas sobre la pestaña. De ahí las ramas especiales de `navigateToTab` (para «Hoy» y «Más»), `openFromMore`, y la decisión en las fichas de «Hoy» entre las dos. Con un `navigation(route = "space-more-graph", startDestination = SpaceMore.route) { Meals; Sport; Notes }`, todas las pestañas usarían el patrón estándar (`popUpTo(SpaceHome) { saveState }` + `restoreState`).
- **Medido:**
  - Desaparecerían unas 20 líneas de ramas especiales en `BobitosNavHost` (`navigateToTab` para «Más», `openFromMore`, la rama de las fichas de «Hoy»).
  - Arreglaría de paso el minor de la revisión: atrás desde Comidas abierta desde «Hoy» vuelve a «Hoy» aunque la barra resalta «Más».
  - Cambiarían las rutas anidadas, `workspaceTabFor` y `WorkspaceTabsTest` (4 tests). Las rutas visibles (`meals`, `sport`, `notes`) y los deep links (invitación, receta compartida, evento) no dependen de «Más».
  - **Riesgo clave:** con el patrón estándar, volver a «Más» restauraría la pila guardada (aterrizaría en Comidas): es exactamente lo que se corrigió en la revisión de la Fase 2, que pidió que «Más» abra siempre su pantalla. Habría que mantener una excepción, con lo que el ahorro es aún menor.
- **Prototipo:** no se hace a propósito: el comportamiento de atrás y de las pestañas solo se puede validar en un dispositivo.
- **Puntuación:** Beneficio 1 (~20 líneas y un minor de navegación) · Coste 2 · Riesgo 3 (cambia la navegación que ve el usuario, sin test automático).
- **Veredicto:** **No.**

## S4 — Metadatos de ruta en el enum
- **Qué es:** cada pantalla de espacio aparece en varias listas sueltas: `protectedRoutes` (`BobitosNavHost`), `allSpacesRoutes`/`spacelessRoutes` (`RealtimeScopes`), `workspaceTabFor`, `workspaceDestinations`/`moreDestinations` y `moduleColor()` (`BobitosDestination`). La propuesta era moverlo a propiedades del enum (`parentTab`, `realtimeScope`, `requiresSpace`).
- **Medido:**
  - Al añadir una pantalla de espacio hoy hay que acordarse de **4–5 sitios**; olvidar uno falla en silencio (pestaña sin resaltar, alcance de tiempo real equivocado o sin redirección cuando no hay espacio).
  - Dos de ellos ya están cubiertos por tests (`RealtimeScopeForTest`, `WorkspaceTabsTest`), que fallarían si una ruta de espacio nueva cae en la rama equivocada solo si alguien añade el caso al test.
  - El enum tiene 20 entradas con constructor de 3 parámetros: añadir 3 más es largo y repetitivo para entradas que no son de espacio.
  - Ritmo de cambio: la última pantalla de espacio nueva fue «Más» (Fase 2); no hay más previstas en el plan maestro.
- **Prototipo:** no se hace: el veredicto no depende de él.
- **Puntuación:** Beneficio 1 (previene un error futuro poco probable; ahorro de líneas casi nulo) · Coste 2 (enum, `RealtimeScopes`, `BobitosNavHost` y tests) · Riesgo 2.
- **Veredicto:** **No.** Alternativa barata si se quiere protección: un test que recorra `BobitosDestination.entries` y compruebe que toda ruta de `workspaceDestinations`/`moreDestinations` tiene pestaña, alcance `ACTIVE_SPACE` y está en `protectedRoutes`.

## S5 — Deporte reutiliza el documento de evento del calendario
- **Qué es:** `FirestoreSportActivityRepository` construye a mano el evento de calendario de cada actividad (`newSportEventData`, `sportEventUpdateData`, `dayFields` y 9 constantes `EV_*`), duplicando las 16 claves que `FirestoreCalendarRepository` escribe con `EventInput.common/data`.
- **Medido:**
  - Solo la **creación** es compartible: las 16 claves coinciden y podría construirse un `EventInput` de todo el día y reutilizar `data()`. La **actualización** de deporte es parcial a propósito (conserva descripción, color y horario puestos desde el calendario) y no encaja con `updateData()`, que reescribe todo.
  - Ahorro realista: unas 15–20 líneas (el `mapOf` de creación y parte de las constantes); `dayFields` y la actualización parcial se quedan.
  - Diferencia que habría que conservar: el nombre del creador (`sportDisplayName` frente a `displayName.ifBlank { email }.take(60)`).
  - **Ningún test cubre estos mapas:** no hay tests unitarios de ninguno de los dos repositorios y los tests del emulador escriben sus propios mapas en JS (ninguno menciona deporte). Un error en las claves solo se vería en ejecución (las reglas con `hasOnly` rechazarían el guardado).
- **Prototipo:** no se hace. Con los tests actuales, «compila y pasa» no demostraría nada sobre las claves escritas.
- **Puntuación:** Beneficio 1 (< 50 líneas) · Coste 2 (dos repositorios y habría que añadir tests) · Riesgo 2 (cambia los datos que se escriben, sin test automático).
- **Veredicto:** **No.** Si alguna vez cambia el contrato de eventos (se añade una clave), entonces sí compensa extraer un `EventDocument` compartido junto con un test que compruebe que ambos repositorios escriben el mismo conjunto de claves.

## S6 — Fuente propia de espacios para el selector
- **Qué es:** para que el selector de espacio (sheet del chip) liste todos los espacios, `BobitosNavHost` guarda `spacePickerOpen` y lo pasa a `realtimeScopeFor`, que cambia el alcance de tiempo real global a `ALL_SPACES` mientras está abierto. La propuesta: un `allSpaces` aparte en `AppViewModel` (`stateIn(WhileSubscribed)`) que solo recoge el sheet, con lo que `realtimeScopeFor` vuelve a depender solo de la ruta.
- **Medido (listeners de `FirestoreSpaceRepository`):**
  - Hoy, dentro de un espacio hay 2 listeners (`space(id)`: membresía + documento del espacio). Al abrir el selector se cancelan y se crean 1 + N (`spaces()`: consulta de membresías + un documento por espacio). Al cerrarlo se vuelve a 2, que se releen.
  - Con `allSpaces` aparte: al abrir se crean los mismos 1 + N, sin cancelar los 2 del espacio activo. Al cerrar se mantienen 5 s más (`WhileSubscribed`). **Diferencia: unas 2 lecturas menos por cada apertura del selector**; las N lecturas de la lista se pagan igual.
  - Guard de expulsión (`selectedSpace == null` → «Espacios»): hoy **no** se dispara al cambiar de espacio. `flatMapLatest` no emite `Loading` al cambiar de alcance y la lista anterior (que ya contiene el espacio nuevo) sigue hasta que llega la nueva. No hay fallo que arreglar. Razonado leyendo el código, sin verificar en dispositivo.
  - Archivos: `AppViewModel`, `RealtimeScopes`, `BobitosNavHost`, `SpaceSwitcher`, `WorkspaceScaffold` y `RealtimeScopeForTest` (6).
- **Prototipo:** no se hace. El ahorro medido (2 lecturas por apertura, ~10 líneas) ya decide el veredicto.
- **Puntuación:** Beneficio 1 (más limpio, ahorro de lecturas marginal) · Coste 2 (6 archivos) · Riesgo 2 (cambia el ciclo de vida de listeners sin test de integración).
- **Veredicto:** **No.**

## S7 — Validación por campo en los editores
- **Qué es:** cada editor decide por su cuenta cuándo se activa «Guardar». Unos usan su `*Validation` (Notas, Compra, Tareas, Recetas, Comidas). Otros solo miran `isNotBlank()` (Rutinas, Ejercicios, Ingredientes ×2). Calendario y Deporte no comprueban nada.
- **Medido (diferencias UI ↔ lo que se rechaza al guardar):** ninguna pantalla limita la longitud de los campos. Todos los editores cierran el sheet al pulsar «Guardar», antes de saber si se guardó, así que cualquier rechazo pierde lo escrito.

  | Editor | La UI deja guardar… | …y luego se rechaza | Impacto |
  |---|---|---|---|
  | **Calendario** | con el **título vacío**, título > 120 o descripción > 1000 | el repositorio (`EventInput.validated`) | **Alto:** dejar el título vacío es un descuido habitual. El sheet se cierra, el borrador se pierde y el aviso es genérico («No se pudo completar la operación»), porque `CalendarViewModel` usa `error.message` y la pantalla lo borra en cuanto se pinta. |
  | Rutinas | título > 120, descripción > 1000 | `RoutinesUiState.validate` | Bajo (textos muy largos) |
  | Ejercicios, Ingredientes, Marcas | nombres/categorías/unidades largos | el repositorio (`*Failure.*TooLong`) | Bajo |
  | Deporte | nombre > 120 | `SportValidation` | Bajo (vacío se sustituye por el tipo) |
- **Prototipo** (`spike/eval-s7`, borrada): `CalendarValidation` (mismas reglas que el repositorio, con mensajes propios) usado en `EventEditor`. «Guardar» se desactiva y se muestra el motivo en el sheet. Test `CalendarValidationTest` (4 casos), visto en rojo antes de implementar. Verificación global en verde. **4 archivos, +59/−2** (incluye 3 textos nuevos). Que el botón se desactive y el aviso se vea está sin verificar en dispositivo.
- **Puntuación:**
  - **Calendario:** Beneficio 3 (fallo que verá un usuario normal) · Coste 1 · Riesgo 1 (desactivar el botón con un dato inválido; validación cubierta por test) ⇒ **Hacer ya**.
  - **Resto de editores (unificar):** Beneficio 1 (casos de textos muy largos) · Coste 2 (6–7 archivos) · Riesgo 1 ⇒ **No**, salvo que se toquen esos editores por otro motivo.
- **Veredicto:** **Hacer ya, solo Calendario.**
- **Si se hace:** PR pequeño con el prototipo. Si se quiere rematar, el `CalendarViewModel` podría traducir `CalendarFailure` a textos en lugar de `error.message`, pero con la validación en el editor ese camino ya no se alcanza desde la UI. Verificar en dispositivo: nuevo evento sin título ⇒ «Guardar» desactivado con «Escribe un título».

## S8 — detekt: ignorar `@Composable` en métodos largos
- **Qué es:** añadir `ignoreAnnotated: ['Composable']` a `LongMethod` y `CyclomaticComplexMethod`, como ya se hace en `FunctionNaming`. Las pantallas Compose son largas y ramificadas por naturaleza.
- **Medido / prototipo** (`spike/eval-s8`, borrada): con las dos líneas y el baseline regenerado (`:app:detektBaseline`), **el baseline pasa de 109 a 79 entradas**. Desaparecen 30 de las 34 de esas dos reglas, todas composables. Las 4 que quedan no son composables (`EventInput.validated`, dos `stringRes()` y un `toUiMessage`). Verificación global en verde. 2 archivos, +4/−30.
- **Lo que se gana:** el baseline deja de crecer con cada pantalla nueva. Se acaban las extracciones hechas solo para quedar por debajo del límite y los ajustes de firma del baseline al tocar un composable (en el rediseño: `@file:OptIn` en `ShoppingScreen`, entradas editadas a mano, varias extracciones por `LongMethod`).
- **Lo que se pierde:** detekt deja de avisar de composables gigantes en **55 archivos** con `@Composable`. Algunas de las extracciones que forzó el límite (`EventPickers`, `CalendarEditorHost`, `MealParticipants`…) mejoraron de verdad la legibilidad, y sin la regla dependerían solo de la revisión.
- **Puntuación:** Beneficio 1 (fricción de herramienta, sin efecto en el usuario) · Coste 1 · Riesgo 1.
- **Veredicto:** la rúbrica no lo clasifica (Beneficio 1 sin coste ni riesgo), así que es una **decisión de estilo del usuario**. Recomendación: **dejarlo como está** y seguir absorbiendo en el baseline lo que no merezca extraerse. detekt no permite un umbral distinto solo para composables, y quitar la regla del todo pierde un aviso que en este proyecto ha sido útil.

## S9 — Renombres del borrador en el editor de Compra
- **Qué es:** `ShoppingItemEditor` (`ShoppingScreen.kt:622-625`) copia `draft.name/quantity/notes/brand` en cuatro `val` locales. La propuesta era usar `draft.*` directamente.
- **Medido:** 10 usos de esas variables en el editor (validación, duplicados, `onSave`, los tres `value =` y las sugerencias). El cambio quitaría 4 líneas y tocaría 10. Sin efecto en el comportamiento: el borrador es inmutable y se relee en cada recomposición igual que los `val`.
- **Prototipo:** no hace falta.
- **Puntuación:** Beneficio 1 (cosmético; los alias incluso acortan las líneas largas) · Coste 1 · Riesgo 1.
- **Veredicto:** **No** como cambio aislado. Solo si se toca ese editor por otro motivo.
