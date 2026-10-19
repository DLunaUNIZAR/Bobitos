# Guardado, errores y Deshacer de los ViewModels (G2) — diseño

Fecha: 2026-10-19 · Base: `main` en `746d516` · Origen: grupo G2 de `docs/superpowers/reports/2026-10-18-auditoria-deuda.md`.

## Por qué

El mismo mecanismo de guardado (`runAction`, `isSaving`, `editorSave`, `showError`, `error`/`notice`, `created`, `force`) está copiado en 14 ViewModels, y su derivación en la pantalla en 13. Es el tema que más menores visibles ha provocado en las fases A, B y C: cada arreglo hay que repetirlo en 5 a 12 sitios, y cada copia acaba distinta. El Deshacer tiene el mismo problema: cada área ofrece el Deshacer a su manera (lambda atada a la composición, estado auxiliar, relevo, estado en el ViewModel), y cada repositorio restaura con su propia variante del alta.

La auditoría recomienda hacerlo una vez en piezas comunes, en dos planes: primero guardado y errores, después Deshacer.

## Alcance

Las líneas son las de `docs/superpowers/backlog.md` en `746d516` (la auditoría usó las de `28dc784`, 12 líneas más arriba). Se buscan por su texto.

### Ya resueltas (solo se marcan en el backlog)

| Línea | Sección | Motivo |
|---|---|---|
| L211 (audit. L199) | `menores-compra-membresia-y-comunes` | El texto crudo de la excepción ya no llega a la pantalla: `UiState.Error` solo lleva `FailureKind` desde `a21ce42` (`agent/evolucion-visual-fase-a`). |
| L219 (audit. L207) | ídem | `AppViewModel` ya emite `UiState.Error(error.toFailureKind())` (`a21ce42`). |

### Plan 1 — guardado y errores (6 internos, 5 visibles)

| Línea | Tipo | Resumen |
|---|---|---|
| L378 (audit. L366) | Interno | `editorSave` copiado en 13 ViewModels y la derivación de `errorMessage` en ~10 pantallas. |
| L146 (audit. L134) | Interno | `runAction` devuelve `Any?` y se convierte con `as? CreatedItem` (12 usos). |
| L376 (audit. L364) | Interno | `if (error is CancellationException) throw`: una cancelación lanzada por un repositorio deja el editor en «Guardando…». |
| L161 (audit. L149) | Interno | La lambda `toMessage` de `confirmCooked` escribe estado (`changedItemName`). |
| L168 (audit. L156) | Interno | En el mapeo general de Comidas, `ItemNotFound` cae en `UnexpectedError`. |
| L148 (audit. L136) | Interno | El Deshacer forzado (`force`) solo tiene test propio en Tareas y Compra. |
| L143 (audit. L131) | Visible | `showError(editor = true)` pone `isSaving = false` con otra acción en curso; al cerrar la hoja se pierde el aviso de esa acción (Tareas, Compra, Notas, Deporte, Ejercicios). |
| L116 (audit. L104) | Visible | Los Deshacer con `force = true` ponen `isSaving = false` antes de que acabe otra escritura y pueden pisar el `created` de un alta simultánea. |
| L372 (audit. L360) | Visible | Los diálogos de Espacios sacan «guardando» de un `editorSave` compartido: con otra acción en curso, el botón no hace nada. |
| L374 (audit. L362) | Visible | En Espacios e Ingredientes, el error se ve dos veces: en el aviso y dentro del formulario. |
| L202 (audit. L190) | Visible | `PantryUiMessage` mezcla errores y avisos y `clearFeedback` borra los dos; tres `when` repiten el mapeo de fallos. |

### Plan 2 — Deshacer (7 internos, 8 visibles)

| Línea | Tipo | Resumen |
|---|---|---|
| L132 (audit. L120) | Interno | `addX`/`restoreX` de Tareas, Compra, Notas y Comidas repiten validación, transacción y campos; `toLinkedEvent` y `restoredEventData` duplican `toCalendarEvent` y `EventInput.data`. |
| L128 (audit. L116) | Interno | Sin tests de repositorio para la escritura directa del estado, `toLinkedEvent` ni `restoredEventData`; `WeekDayStripTest` no comprueba el rol. |
| L129 (audit. L117) | Interno | Faltan tests del relevo de ingredientes (`ownerUid` null, uid actual null, publicar antes de `observe()`, `stopObserving()` + `observe()` sin escucha doble). |
| L134 (audit. L122) | Interno | Detalles del relevo (`consume()` condicionado al mismo valor, `ownerUid` no nulo) y dos comentarios de más de 120 columnas. |
| L130 (audit. L118) | Interno | El Calendario del espacio no captura el `spaceId` en `restore`. |
| L133 (audit. L121) | Interno | Ejercicios y Rutinas usan un estado auxiliar para el aviso de Deshacer; `EventsLoadState` vive en `PersonalCalendarScreen.kt`. |
| L105 (audit. L93) | Interno | «Marcar varias muy seguidas» solo se prueba en los ViewModels y en `CompletingItemsTest`, no en las pantallas de Tareas y Compra. |
| L142 (audit. L130) | Visible | Deshacer al añadir borra aunque otro miembro haya cambiado el elemento. |
| L110 (audit. L98) | Visible (seguridad) | `FirestorePersonalIngredientRepository.restore()` no compara el dueño. |
| L111 (audit. L99) | Visible | El relevo de ingredientes borrados sobrevive al cierre de sesión y ofrece un Deshacer obsoleto al volver. |
| L112 (audit. L100) | Visible | El `onDeleted` de cada borrado está atado a la composición: al girar o salir antes de que acabe el borrado, el Deshacer no sale (11 áreas). |
| L113 (audit. L101) | Visible | Deshacer el borrado de una tarea completada, un producto comprado o una actividad hecha son dos escrituras; si falla la segunda, reintentar da «sin permiso». |
| L114 (audit. L102) | Visible | `restoreMeal` y `restoreActivity` no revalidan participantes ni cocinero. |
| L115 (audit. L103) | Visible | Si el evento enlazado de una actividad está mal formado, se borra, no vuelve y la actividad queda con un `eventId` colgante. |
| L68 (audit. L56) | Visible | Deshacer al completar no compara `dueAt`/`startAt`. |

Al cerrar cada plan, sus líneas se marcan en el backlog como resueltas, con su rama. Las dos ya resueltas se marcan en el cierre del plan 1.

## Objetivos

- Una sola implementación del ciclo de guardado (en curso, terminado, fallido, editor, avisos, alta recién hecha), con sus tests, que usen los 14 ViewModels.
- Cada error se ve en un solo sitio, y ninguna acción apaga «Guardando…» ni borra el aviso de otra.
- Una sola forma de ofrecer Deshacer, que sobreviva al giro de pantalla, en todas las áreas.
- Restaurar algo borrado pasa por el mismo código y las mismas validaciones que crearlo, y se hace de una vez: o vuelve como estaba, o no cambia nada.

## Fuera del alcance

- El resto de grupos de la auditoría (G3 a G9), aunque se toquen los mismos ficheros. En particular, no se unifica el mapeo de los `*Failure` comunes entre áreas (es G3), salvo los tres `when` de la Despensa que nombra L202.
- `AuthViewModel` y `BetaUpdateViewModel`: no siguen el patrón y no están en G2.
- Añadir Deshacer donde hoy no lo hay (marcar comprado, hecho o cocinado).
- Cambios de aspecto: no hay pantallas ni componentes nuevos.

## Lo que no debe pasar (anti-objetivos)

- Que una pantalla pierda un aviso, un error o un Deshacer que hoy sí muestra.
- Que un mismo error salga a la vez en el formulario y en el aviso de la pantalla.
- Que un Deshacer restaure algo de otra cuenta, o en otro espacio.
- Que un Deshacer deje el elemento a medias (pendiente cuando estaba completado, enlazado a un evento que no existe).
- Un helper común que cada ViewModel acabe envolviendo o saltándose con su propia copia.

## Decisión del usuario

- **Participantes que ya no están (2026-10-19).** Al deshacer el borrado de una tarea, comida, actividad o evento, si alguien que participaba, el responsable o el cocinero ya no es miembro activo del espacio, el elemento **vuelve sin esa persona**: la tarea, sin responsable; la comida, la actividad o el evento, sin ese participante; y si era el cocinero, la comida vuelve sin cocinero. Esto cambia también Tareas y Calendario, que hoy fallan con «participante no válido». Ninguna de esas listas exige un mínimo de participantes, así que el Deshacer no falla por este motivo.

## Parte 1 — Guardado y errores (plan 1)

### Comportamiento

1. **Escrituras en curso.** «Guardando…» (la barra del andamio y el botón del editor) sigue encendido mientras quede **cualquier** escritura del ViewModel en curso, y se apaga solo cuando acaba la última. Un Deshacer forzado o un error de validación nunca lo apagan antes de tiempo (L143, L116).
2. **La puerta.** Mientras haya una escritura en curso, una acción normal no empieza (como hoy). Un Deshacer sí (como hoy con `force`). Un error de validación no la abre ni la cierra.
   - Cuenta como «en curso» exactamente lo que hoy enciende `isSaving`, también las lecturas que hoy lo encienden a propósito para no ir en paralelo (`addToShopping` en Recetas, `prepareIngredientReview` en Comidas).
   - Lo que hoy no lo enciende sigue sin encenderlo: marcar completado o comprado (`launchPerIdWrite`), `writeOptimistically` (Detalle de ingrediente) y `restock` (Despensa). Así «marcar varias muy seguidas» sigue igual.
3. **El editor y la pantalla, por separado.**
   - El error de un guardado del editor (o de su validación) se ve **solo** en el formulario. Los errores y avisos de las demás acciones se ven **solo** en el aviso de la pantalla (L374).
   - Mientras algo abierto tapa el aviso de la pantalla (una hoja o un diálogo de editor, «Has cocinado» en Comidas, el selector de la Despensa, el diálogo de importar receta), el aviso de la pantalla **espera** y sale al cerrarlo. No se da por visto hasta que sale. Si llegan dos errores (o dos avisos) mientras espera, sale el último.
   - «Has cocinado», el selector de la Despensa y el diálogo de importar receta siguen mostrando dentro el error que llegue mientras están abiertos, como hoy, y al cerrarse lo dan por visto, para que no salga otra vez en el aviso. No pasan a ser editores: no heredan el tiempo máximo de 20 s.
   - Si el editor ya no está abierto cuando llega su error (por ejemplo, tras un giro que lo cerró), ese error sale en el aviso de la pantalla, como hoy.
   - Cerrar el editor descarta solo el error del editor; nunca el error o el aviso de otra acción, que sale entonces en el aviso de la pantalla (L143).
4. **El aviso consume solo lo que muestra.** Si hay a la vez un error y un aviso pendientes, sale primero el error y después el aviso: mostrar uno no borra el otro (L202 y el mismo caso en las demás pantallas).
5. **Alta recién hecha.** Solo una acción que crea algo deja el «alta recién hecha» (con su Deshacer). Ninguna otra acción la borra al terminar, sea forzada o no (L116).
6. **Cancelación.** Si la escritura falla con una `CancellationException` y quien la lanzó **no** está cancelado (la lanzó el repositorio), cuenta como un fallo normal: sale el error inesperado de esa área y el guardado termina. Solo se relanza cuando el que se cancela es el propio ViewModel (L376).
7. **Espacios.** Los diálogos de Crear, Unirse y Renombrar calculan «guardando» como las demás áreas, con las escrituras en curso del ViewModel: con otra acción en curso, el botón se ve desactivado con «Guardando…» en lugar de no hacer nada al tocarlo (L372).
8. **Comidas.**
   - `ItemNotFound` en el mapeo general pasa a «La lista ha cambiado» (`ShoppingChanged`), como `ItemChanged` (L168).
   - El nombre del artículo que ha cambiado en «Listo» (`changedItemName`) se calcula sin efectos dentro de la traducción del error y se escribe siempre en la misma actualización que el error (vacío si el error no trae nombre). Se limpia al cerrar el diálogo de «Has cocinado» (L161).
9. **Despensa.** El error y el aviso se consumen por separado (punto 4). La traducción de `PantryFailure` y `ShoppingFailure` a mensaje se escribe una vez en la Despensa, sin los tres `when` que repiten las mismas ramas (L202).

### Diseño técnico

- **Una pieza común en `core/common`** (nombre a elegir por quien implementa, por ejemplo `SaveRunner`) que sustituye a los 14 `runAction`/`action` de Tareas, Compra, Notas, Deporte, Rutinas, Ejercicios, Recetas, Comidas, Ingredientes, Detalle de ingrediente, Calendario del espacio, Mi calendario, Espacios y Despensa. Es dueña de las transiciones del estado de guardado; los ViewModels no las reescriben.
- **Estado de guardado común**, embebido en el `UiState` de cada área. Orientativo:

  ```kotlin
  data class SaveState<M>(
      val running: Int = 0,               // escrituras en curso
      val editor: EditorSaveStatus = EditorSaveStatus.IDLE,
      val editorError: M? = null,         // error del editor: solo en el formulario
      val error: M? = null,               // error de otra acción: solo en el aviso
      val notice: M? = null,
      val created: CreatedItem? = null,
  ) { val isSaving: Boolean get() = running > 0 }
  ```

  - El Calendario usa `FailureKind` para el error y `CalendarNotice` para el aviso: quien implementa decide si el estado admite dos tipos o si el Calendario los unifica.
  - `writeStatus` (Compra y Espacios) mantiene su comportamiento actual, derivado del estado común si es posible.
- **Resultados tipados (L146).** Sin `Any?` ni `as? CreatedItem`. Una entrada para acciones que no devuelven nada, otra para las altas (`CreatedItem`) y, si hace falta, una genérica para las que devuelven un dato que usa el ViewModel (Deporte con el evento borrado, Despensa con su aviso). Ninguna lambda tiene que acabar en `null` para cumplir el tipo.
- **Estado extra en la misma actualización.** Las áreas que hoy cambian su estado al terminar (Recetas con `deletedRecipe`, Detalle de ingrediente con `finished`, Espacios con `acceptedSpaceId`, Despensa) o al fallar (Comidas con `changedItemName`) lo hacen con reductores puros que la pieza común aplica en la misma actualización que su propia transición. Los efectos fuera del estado (publicar en el relevo de ingredientes) van después de la actualización.
- **Cancelación (L376).** La regla del punto 6 vive en la pieza común y en una función común que usan también los demás sitios con el patrón `if (error is CancellationException) throw` de los ViewModels del alcance (`launchPerIdWrite`, `restock`, `writeOptimistically`, `addToShopping`, `prepareIngredientReview`, `markCooked`, `currentIdsOrNull` de `PantryShopping.kt`). `markCooked` deja de convertir una cancelación del propio ViewModel en «error inesperado».
- **Pantallas (L378).** La derivación copiada (`error?.takeIf { editorSave == FAILED }`, `discardFailure`, `FeedbackSnackbarEffect`, `EditorSaveEffect`, `savingOutsideEditor`) pasa a helpers comunes de `core/designsystem/component` que leen el estado común. Cada pantalla solo aporta cómo se escribe su mensaje.
- **Validación.** El error de validación del editor se registra con una función de la pieza común que solo toca `editor` y `editorError`; no toca las escrituras en curso ni el aviso de la pantalla.

### Tests (plan 1)

- La pieza común tiene sus tests unitarios: escrituras en curso con dos acciones solapadas (la forzada termina antes y después), la puerta, `created` que no se pisa, error de validación con otra acción en curso, editor frente a pantalla, consumo por separado de error y aviso, tiempo máximo del editor y cancelación lanzada por el repositorio frente a cancelación del ViewModel.
- Los tests de cada ViewModel se adaptan a la forma nueva del estado sin perder casos.
- **L148:** cada área con Deshacer forzado (Tareas, Compra, Notas, Comidas, Deporte, Rutinas, Ejercicios, Recetas, Ingredientes, Detalle de ingrediente, Calendario del espacio y Mi calendario) tiene un test de ViewModel en el que un Deshacer se ejecuta con otra escritura en curso y «Guardando…» sigue encendido hasta que acaban las dos.
- Un test por cada visible del plan 1 que lo reproduzca antes del arreglo (RED) en al menos un área: L143 en Tareas (un error de validación con otra acción en curso no apaga «Guardando…», y al cerrar la hoja el error de esa otra acción sigue pendiente y sale en el aviso), L116 en Tareas, L372 en Espacios, L374 en Espacios e Ingredientes, L202 en la Despensa.
- Un test del helper común de pantalla: con algo abierto que tapa el aviso, el aviso de la pantalla no se da por visto y sale al cerrarlo.

## Parte 2 — Deshacer (plan 2)

### Comportamiento

1. **La oferta de Deshacer vive en el ViewModel (L112, L133).**
   - Borrar, completar (Tareas y Hoy) y dar de alta dejan en el estado del ViewModel una oferta de Deshacer con todo lo necesario para deshacer: el elemento tal como estaba, su espacio y lo que haga falta (el evento enlazado en Deporte). La pantalla la muestra y la consume, como hoy `created`.
   - Sobrevive al giro de pantalla y a cambiar de módulo y volver mientras la pantalla sigue en la pila. Si no llega a mostrarse en unos 10 s (la duración del aviso largo) desde que terminó la escritura, caduca y no se muestra: al volver a una pantalla no sale un Deshacer antiguo.
   - Salir con «atrás» destruye el ViewModel de la pantalla, y la oferta se pierde, como hoy. Un Deshacer que aparezca en otra pantalla queda fuera del alcance, salvo el relevo de ingredientes que ya existe (borrar en la ficha y deshacer en la lista). L112 se cierra para el giro y para las pantallas que siguen en la pila.
   - El Deshacer usa el espacio guardado en la oferta, nunca el espacio activo en ese momento (L130).
   - Desaparecen el `onDeleted` atado a la composición y el estado auxiliar de Ejercicios. `EventsLoadState` pasa a su propio fichero del Calendario.
2. **Deshacer al añadir (L142).** En las áreas de un espacio (Tareas, Compra, Notas, Comidas, Deporte, Calendario del espacio y Mi calendario), deshacer un alta solo borra si el último que escribió en el elemento es quien deshace (`updatedBy`). Si otro miembro lo ha cambiado, no se borra y sale el aviso «<El elemento> ha cambiado entretanto y no se ha deshecho.», con el mismo molde que el actual `tasks_error_changed`: «La nota…», «El producto…», «La comida…», «La actividad…», «El evento…».
3. **Restaurar es como crear (L132, L114).** El Deshacer de un borrado pasa por el mismo código que el alta, con el id original: las mismas validaciones y los mismos campos. Los participantes, el responsable o el cocinero que ya no son miembros activos se quitan, según la decisión del usuario.
4. **De una sola vez (L113).** El Deshacer del borrado de una tarea completada, un producto comprado o una actividad hecha deja el elemento exactamente como estaba (completado, comprado o hecho) en una sola escritura atómica: o vuelve entero, o no cambia nada, y repetirlo tras un fallo funciona.
5. **Evento enlazado de una actividad (L115, L132).** La foto del evento al borrar se lee con el mismo código que el Calendario. Si el evento existe pero no se puede leer, la actividad vuelve sin enlace (sin `eventId`), nunca con un enlace a un evento que no existe.
6. **Deshacer al completar (L68).** Además de lo que ya compara (R7), deshacer al completar no revierte la tarea si su `dueAt` o su `startAt` han cambiado desde que se completó, aunque los haya cambiado la misma persona: sale el aviso de «La tarea ha cambiado entretanto…». En una tarea que se repite, las fechas que se comparan son las que dejó al completarla.
7. **Ingredientes personales (L110, L111).**
   - `FirestorePersonalIngredientRepository.restore()` comprueba que la cuenta actual es la dueña del ingrediente borrado (`ownerUid`); si no lo es, o si no se sabe, falla sin escribir.
   - El relevo de ingredientes borrados se vacía al cambiar de cuenta (cerrar sesión o entrar con otra), y su oferta caduca como las demás (punto 1).

### Diseño técnico

- **La oferta.** Una pieza común para la oferta de Deshacer (generaliza `CreatedUndoEffect`) que usan las 11 áreas con borrado, el alta y el completar de Tareas y Hoy. Quien implementa decide si va dentro del estado común del plan 1.
- **Repositorios.** `restoreX` reutiliza la función interna del alta con un id explícito en Tareas, Compra, Notas, Comidas y Deporte, y el Calendario restaura con su propia función, que conserva el id y quita los participantes que ya no están. `toLinkedEvent` y `restoredEventData` desaparecen en favor de `toCalendarEvent` y `EventInput.data`.
- **Escritura atómica y reglas (L113).** Las reglas de hoy exigen crear una tarea en `TODO`, un producto sin comprar y una actividad sin hacer; por eso hoy son dos escrituras. El plan 2 elige, con una prueba en el emulador de reglas (`tests/firebase-emulators.test.mjs`), entre:
  - una sola escritura atómica que las reglas actuales ya acepten (por ejemplo, alta y actualización en el mismo lote), si el emulador confirma que funciona;
  - o una regla nueva de restauración que acepte el estado completado, comprado o hecho al crear, con `completedBy`/`purchasedBy` igual a quien deshace y la hora del servidor. En ese caso hay que desplegar las reglas en `bobitos-dev` antes de distribuir la beta: va en la checklist de despliegue del cierre.
- **Deshacer al añadir.** La comprobación de `updatedBy` y el borrado van en la misma transacción.

### Tests (plan 2)

- Tests unitarios de las funciones puras de los repositorios: mapa de campos del alta y del restaurado, lectura del evento enlazado (también mal formado), la comparación de R7 con fechas y la de `updatedBy`.
- Tests de reglas en el emulador para la escritura atómica del Deshacer y, si la hay, la regla nueva.
- Tests de ViewModel: la oferta sobrevive a recrear la pantalla, caduca, usa su espacio y no se pisa con otra.
- Tests del relevo que pide L129, y el vaciado al cambiar de cuenta.
- L128: `WeekDayStripTest` comprueba el rol.
- L105: un test instrumentado sobre las pantallas reales de Tareas y Compra que marca varias muy seguidas. La CI no ejecuta los instrumentados: se pasan en local antes del cierre.

## Dirección visual

Sin cambios de aspecto. Los errores, avisos, botones y ofertas de Deshacer usan los componentes que ya existen (aviso del andamio, `BobitosFormSheet`, diálogos de Espacios, `UndoSnackbar`). Lo único visible nuevo son los textos «… ha cambiado entretanto y no se ha deshecho.» de la parte 2, con el molde de `tasks_error_changed`.

## Restricciones

- Kotlin, Compose, Hilt y Firestore, como el resto de la app. Sin dependencias nuevas.
- La suite unitaria, ktlint, detekt y `lintDebug` en verde en cada tarea (la CI ya los ejecuta desde G1).
- Sin cambios de reglas en el plan 1. En el plan 2, solo si el emulador demuestra que hacen falta.

## Llamadas mías (no del usuario)

- **Dos planes y una spec.** El plan 2 se escribe cuando el plan 1 esté mergeado, porque el plan 1 reescribe los mismos 14 ViewModels y sus pantallas: escrito ahora, sus tareas citarían código que ya no existirá.
- L148 (tests del Deshacer forzado) y L116 (`force` que apaga «Guardando…» y pisa `created`) van en el plan 1, porque los arregla la pieza común.
- En Espacios, el botón desactivado con «Guardando…» mientras corre otra acción, igual que en las otras 12 áreas, en lugar de dejar que el editor guarde en paralelo.
- La oferta de Deshacer caduca a los ~10 s si no llega a mostrarse, también la del alta, que hoy no caduca.
- El completar de Tareas y de Hoy entra en la oferta común aunque L112 solo nombra los borrados: su `onCompleted` está atado a la composición igual.
- La comprobación de Deshacer al añadir usa `updatedBy`, como R7, y no `updatedAt`. Si la misma persona lo ha editado, el Deshacer lo borra: es lo que ha pedido.
- Las fechas de R7 se comparan aunque las haya cambiado la misma persona, porque así lo pide L68.
- No se separa `PantryUiMessage` en dos tipos. El efecto visible de L202 lo arregla el consumo por separado del punto 4.
- Con una hoja o un diálogo abierto, el aviso de la pantalla espera a que se cierre (sale el último de cada tipo), en vez de salir detrás, donde nadie lo ve. Hoy ese error sale dentro del formulario, en el sitio equivocado.
- «Guardando…» y la puerta cuentan lo mismo que hoy cuenta `isSaving`, ni más ni menos.
- El Deshacer no sigue al usuario si sale con «atrás»: haría falta un almacén y un sitio común donde mostrarlo, y eso es una función nueva.

## Lo que queda para quien implementa

- Los nombres y la forma exacta de la API de la pieza común y de la oferta de Deshacer, y si el estado común lleva un tipo o dos.
- El orden de migración de las áreas y cómo se reparten en tareas.
- En el alta duplicada de Ingredientes (que lleva a la ficha existente), dónde se ve el aviso, siempre que se vea una sola vez.
- En el plan 2, cuál de las dos opciones de escritura atómica, según el emulador.
