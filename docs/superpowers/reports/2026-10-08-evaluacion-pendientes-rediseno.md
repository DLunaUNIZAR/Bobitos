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
| S1 | Borradores con `@Parcelize` | | | | |
| S2 | ViewModels sin volver a «cargando» al reobservar | | | | |
| S3 | Grafo anidado para «Más» | | | | |
| S4 | Metadatos de ruta en el enum | | | | |
| S5 | Deporte reutiliza el documento de evento del calendario | | | | |
| S6 | Fuente propia de espacios para el selector | | | | |
| S7 | Validación por campo en los editores | | | | |
| S8 | detekt: ignorar `@Composable` en métodos largos | | | | |
| S9 | Renombres del borrador en el editor de Compra | | | | |

## Orden recomendado

_(se rellena al final)_

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
**Qué es** ·  **Medido** ·  **Prototipo** ·  **Puntuación** ·  **Veredicto** ·  **Si se hace**

## S2 — ViewModels sin volver a «cargando» al reobservar
**Qué es** ·  **Medido** ·  **Prototipo** ·  **Puntuación** ·  **Veredicto** ·  **Si se hace**

## S3 — Grafo anidado para «Más»
**Qué es** ·  **Medido** ·  **Prototipo** ·  **Puntuación** ·  **Veredicto** ·  **Si se hace**

## S4 — Metadatos de ruta en el enum
**Qué es** ·  **Medido** ·  **Prototipo** ·  **Puntuación** ·  **Veredicto** ·  **Si se hace**

## S5 — Deporte reutiliza el documento de evento del calendario
**Qué es** ·  **Medido** ·  **Prototipo** ·  **Puntuación** ·  **Veredicto** ·  **Si se hace**

## S6 — Fuente propia de espacios para el selector
**Qué es** ·  **Medido** ·  **Prototipo** ·  **Puntuación** ·  **Veredicto** ·  **Si se hace**

## S7 — Validación por campo en los editores
**Qué es** ·  **Medido** ·  **Prototipo** ·  **Puntuación** ·  **Veredicto** ·  **Si se hace**

## S8 — detekt: ignorar `@Composable` en métodos largos
**Qué es** ·  **Medido** ·  **Prototipo** ·  **Puntuación** ·  **Veredicto** ·  **Si se hace**

## S9 — Renombres del borrador en el editor de Compra
**Qué es** ·  **Medido** ·  **Prototipo** ·  **Puntuación** ·  **Veredicto** ·  **Si se hace**
