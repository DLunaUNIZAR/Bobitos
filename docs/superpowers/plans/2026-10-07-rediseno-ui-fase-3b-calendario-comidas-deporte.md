# Rediseño UI · Fase 3b: Calendario, Comidas y Deporte — plan detallado

> **Para quien ejecute:** superpowers:executing-plans (nativo). Continúa `docs/superpowers/plans/2026-10-07-rediseno-ui-fase-3-editores.md` (alcance de 3b ya acordado). Reutiliza `BobitosFormSheet`, `BobitosDialog` y el patrón «borrador inmutable + `Saver` + test» de la 3a (`TaskDraft`, `ShoppingDraft`).

**Goal:** que los editores de evento, comida y actividad deportiva sean sheets cómodos que no pierden lo escrito, y que crear desde Comidas y desde el calendario personal sea evidente (FAB), con un selector de color accesible.

**Global Constraints:** los del plan de la Fase 3. Sin cambios en repositorios, ViewModels (salvo lo indicado), `firestore.rules` ni modelos; enums persistidos (`EventColor`, `MealSlot`, `SportType`) se guardan por `name`.

## Hallazgos que condicionan el diseño (inventario + lectura del código)
- `EventEditor` (`CalendarScreen.kt`, `internal`) lo usan `CalendarScreen` y `PersonalCalendarScreen`; todo su estado es `remember`; la visibilidad depende de `creating`/`editor`/`creatingAt` (Calendario) y `editorRequest` (Personal), también `remember`.
- `PersonalCalendarScreen` solo crea por pulsación larga (`onCreateAt` → `SpacePickerDialog` → editor); no hay FAB.
- `MealEditor` recibe el hueco (`MealSlot`) del botón «Añadir» de cada franja y lo muestra como texto de solo lectura; `MealsViewModel.updateMeal` ya acepta `slot`, así que se puede elegir en el editor. `MealsScreen` no tiene FAB; su raíz es un `Column`.
- `ActivityEditor` (`SportScreen.kt`) incluye `GymSessionSection` con `SnapshotStateList<ExerciseDraft>` (clases con `mutableStateOf`), que no cabe en un `Saver` sencillo: se resuelve junto con `RoutineEditor` en la 3c (ambos comparten `ExerciseListEditor`).
- `ColorPicker`: círculos de 32 dp, `Row` sin salto de línea, el seleccionado solo se distingue por el borde.
- `HourRow` crea eventos con pulsación larga sin pista ni etiqueta de accesibilidad.

## Review Focus
1. **Rotación con el editor abierto** conserva lo escrito en evento y comida (título, fechas/horas, todo el día, color, participantes; nombre, receta, participantes, cocinero, franja). En Deporte el sheet se cierra al girar, igual que hoy (ver ruling esperado).
2. **Editar un evento y girar** vuelve a mostrar el mismo evento (se guarda su id, no el objeto) y, en el calendario personal, con los miembros del espacio correcto.
3. **Descartar:** con cambios sin guardar, deslizar/atrás/Cancelar pide confirmación; sin cambios cierra. Aplica a los tres editores.
4. **FAB de Comidas:** abre el editor con la franja por defecto según la hora (desayuno/comida/cena) y permite cambiarla; el botón «Añadir» por franja sigue funcionando y abre esa franja.
5. **FAB del calendario personal:** con un solo espacio abre el editor directamente; con varios, primero elige espacio.
6. **Selector de color:** objetivo táctil ≥ 48 dp, marca visible (✓) en el seleccionado además del borde, se ve completo con fuente 200 % (salto de línea), cada opción anuncia nombre y «seleccionado».
7. **Solo lectura:** sin conexión no hay FAB ni editores, como hoy.
8. **Confirmaciones de borrar** (evento, evento personal, comida, actividad) usan `BobitosDialog` con el deshacer intacto.
9. **Pulsación larga en la rejilla horaria:** sigue creando el evento; el gesto lleva etiqueta de accesibilidad.
10. **Pickers de fecha/hora** sobre el sheet devuelven el valor sin cerrarlo.

## Tareas
- **T1 — `EventDraft` + `EventEditor` en sheet.** `EventDraft` (inmutable, `listSaver`): `title`, `description`, `allDay`, `startDate`/`endDate` como ISO, `startTime`/`endTime` como `HH:mm`, `colorName`, `selectedIds` ordenados. Test de ida y vuelta del `Saver` y de las propiedades derivadas. `EventEditor` pasa a `BobitosFormSheet` (`dirty = draft != initial`, `saving`), con `activePicker`/`error` transitorios en `remember`.
- **T2 — Anfitriones del calendario.** `CalendarScreen`: `editorEventId`, `creating`, `creatingAtMinutes` en `rememberSaveable`; evento resuelto por id (si desaparece y la lista cargó, se cierra). `PersonalCalendarScreen`: lo mismo con `editorSpaceId` + `editorEventId` + `creatingAtMinutes`; FAB (un espacio → editor directo; varios → `SpacePickerDialog`). Borrar evento (ambas pantallas) con `BobitosDialog`. `HourRow` con `onLongClickLabel`.
- **T3 — Selector de color accesible.** `FlowRow`, 48 dp táctil con círculo de 32 dp dentro, ✓ en el seleccionado, `Role.RadioButton` + `selected`.
- **T4 — Comidas.** `MealDraft` (`name`, `recipeId`, `selectedIds`, `cookId`, `slotName`) con `Saver` + `defaultMealSlot(hour)` pura; tests. `MealEditor` en sheet con `FilterChip` de franja; anfitrión `rememberSaveable` (por id de comida o franja); FAB; borrar comida con `BobitosDialog`.
- **T5 — Deporte.** `ActivityEditor` en `BobitosFormSheet` con `dirty` (campos simples + sesión de gimnasio); el estado del anfitrión sigue en `remember` (el sheet no sobrevive a la rotación, como hoy) porque la lista de ejercicios necesita el `Saver` de la 3c; borrar actividad con `BobitosDialog`.

## Fuera de alcance
Guardado optimista, `ExerciseDraft`/`RoutineEditor` (3c), detalle de receta (3c), selector de espacio en sheet (3c), pantalla de detalle de evento.
