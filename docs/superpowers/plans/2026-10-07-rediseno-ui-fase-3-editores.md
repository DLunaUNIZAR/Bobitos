# Rediseño UI · Fase 3: editores en bottom sheet y acciones descubribles — plan

> **Para quien ejecute:** superpowers:executing-plans (nativo) o superpowers:subagent-driven-development. La Fase 3 se entrega en **tres PRs** (3a, 3b, 3c); este documento detalla 3a y deja 3b y 3c con alcance cerrado, para detallarlas al llegar (aprenderemos de 3a cómo se comporta el sheet con el teclado).

**Goal:** que crear y editar cosas sea cómodo y no se pierda lo escrito: los formularios largos pasan de `AlertDialog` a un bottom sheet con el botón de guardar siempre visible, el borrador sobrevive a rotaciones y hay aviso antes de descartar cambios; las acciones por gesto tienen alternativa visible y accesible.

**Architecture:** dos componentes nuevos en el design system —`BobitosFormSheet` (formulario en `ModalBottomSheet`) y `BobitosDialog` (confirmaciones cortas con estilo destructivo)— que sustituyen a los `AlertDialog` por pantalla. El estado de los formularios pasa de `remember` a `rememberSaveable`. La lógica de decisión (¿hay cambios sin guardar?) es una función pura testeada.

**Tech Stack:** Compose Material 3 (`ModalBottomSheet`, ya disponible en la versión del BOM actual), JUnit. Sin dependencias nuevas.

**Spec:** plan maestro `docs/superpowers/plans/2026-10-07-rediseno-ui.md` (Fase 3) y decisiones del usuario: editores en bottom sheet, acciones descubribles, accesibilidad. Inventario de diálogos hecho el 2026-10-07 (resumen en «Hallazgos»).

## Hallazgos que condicionan el diseño
- **Ningún editor sobrevive a una rotación:** todo el estado es `remember` (Tareas, Compra, Evento, Comida, Actividad, Nota…). Solo `ImportUrlDialog`, `InvitationCodeDialog`, `SpaceNameDialog` y Perfil usan `rememberSaveable`.
- **Sin scroll ni IME:** `TaskEditor` (8 campos), `EventEditor`, `MealEditor`, `NoteEditor` no tienen `verticalScroll`; con teclado el contenido se corta. Solo `ActivityEditor`, `RecipeEditor`, `RoutineEditor` y `BrandEditorDialog` hacen scroll.
- **Guardado optimista:** todos los editores se cierran al pulsar guardar, antes de saber el resultado (`isSaving` solo deshabilita el botón). Se conserva ese comportamiento en la Fase 3 (cambiarlo es otro trabajo).
- **Swipe sin alternativa accesible:** `SwipeActionsBox` dibuja sus iconos con `contentDescription = null` y no expone acciones a TalkBack. Sí existen alternativas visibles: checkbox y menú ⋮ en Tareas y Compra. Editar solo se puede desde el menú ⋮.
- **Confirmaciones duplicadas:** 9 confirmaciones destructivas con el mismo patrón (receta, nota, ejercicio, rutina, ingrediente, marca, borrar espacio, quitar miembro, salir del espacio) + eliminar cuenta con campo extra. Hay 5 copias inline, un `ConfirmDialog` privado (`IngredientDetailScreen.kt:403`) y `ConfirmMemberActionDialog`.
- **Selector de color de eventos de 32 dp** y solo se distingue por el borde (`CalendarScreen.kt:~915`).
- **Comidas no tiene FAB** (añadir va por un `TextButton` por franja); `PersonalCalendarScreen` solo crea por pulsación larga.

## Global Constraints
- Los del plan maestro: `JAVA_HOME` del JBR de Android Studio; verificación `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:ktlintCheck :app:detekt :app:assembleDebug`; textos en `strings.xml` en español, sentence case, y la acción conserva su nombre en todo el flujo («Guardar» en el botón y «Guardado» en el aviso); táctil ≥ 48 dp; `rememberReduceMotion()`; commits `tipo(área): …` con `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`; ramas `agent/<tema>`.
- Sin cambios en repositorios, ViewModels (salvo lo indicado), `firestore.rules` ni modelos. Los formularios mantienen sus validaciones (`TaskValidation`, `ShoppingValidation`, etc.) tal cual.
- Los nombres de enums persistidos (`TaskType`, `Supermarket`, `EventColor`…) no cambian: el borrador guarda `name` y se reconstruye con `valueOf`.
- Sin dependencias nuevas.

## Review Focus
1. **Rotación con el sheet abierto** conserva lo escrito (título, fechas, responsable, prioridad, tipo y recurrencia en Tareas; nombre, cantidad, notas, súper y marca en Compra).
2. **Teclado:** el botón «Guardar» sigue visible con el teclado abierto y el campo enfocado no queda tapado (descripción de tarea, notas de compra).
3. **Descartar:** deslizar hacia abajo o tocar fuera con cambios sin guardar pide confirmación («Descartar cambios»); sin cambios, cierra directamente. Pulsar «Cancelar» sigue la misma regla.
4. **Guardando:** con `isSaving` el botón de guardar está deshabilitado y el sheet no se cierra por gesto.
5. **Menús dentro del sheet:** los `DropdownMenu` de responsable, tipo, recurrencia, unidad y supermercado se ven completos, sin recortar, con el sheet a máxima altura.
6. **Selector de fecha** (diálogo sobre el sheet) devuelve el valor y no cierra el sheet.
7. **Duplicado en Compra:** al guardar un producto repetido sigue apareciendo el aviso de duplicado tras cerrar el sheet.
8. **TalkBack:** el sheet anuncia su título; las acciones de deslizar (completar, eliminar) aparecen como acciones personalizadas de la fila; el foco no escapa del sheet.
9. **Solo lectura** (`canWrite = false`): no se puede abrir ningún editor, igual que hoy.
10. **Editar con tarea reabierta tras girar:** el sheet de edición vuelve a mostrar la tarea correcta (se guarda su id, no el objeto).

## Reparto en PRs
| PR | Contenido |
|---|---|
| **3a** | Componentes `BobitosFormSheet` y `BobitosDialog`; Tareas y Compra (editores a sheet, 4 confirmaciones, borrador guardable, tarjeta que abre el editor al tocarla, acciones accesibles del swipe) |
| **3b** | Calendario, Calendario personal, Comidas y Deporte: `EventEditor`, `MealEditor`, `ActivityEditor` a sheet; selector de color de 48 dp con marca de seleccionado; FAB en Comidas (la franja se elige dentro del editor) y FAB en el calendario personal; pista visible para la pulsación larga del calendario |
| **3c** | Recetas, Rutinas, Ejercicios, Notas, Ingredientes y Ajustes: `RecipeEditor`, `RoutineEditor`, `BrandEditorDialog`, `NoteEditor` a sheet; `RecipeDetailDialog` y `RoutineDetailDialog` a sheet de solo lectura; las 9 confirmaciones restantes a `BobitosDialog`; selector de espacio en sheet |

Fuera de alcance de toda la Fase 3: cambiar el guardado optimista, pantallas de detalle con ruta, ilustraciones.

---

# 3a — plan detallado

Rutas bajo `app/src/main/java/com/dlunaunizar/bobitos/` (`DS` = `core/designsystem`). Tests en `app/src/test/java/com/dlunaunizar/bobitos/`. Rama: `agent/ui-fase3a-sheets-tareas-compra`.

**Mapa de archivos**
- Crear `DS/component/BobitosDialog.kt` y `DS/component/BobitosFormSheet.kt`; lógica pura en `DS/component/DiscardGuard.kt`.
- Modificar `feature/tasks/TasksScreen.kt` (editor, borrado, tarjeta, swipe) y `feature/shopping/ShoppingScreen.kt` (editor, 2 confirmaciones, swipe).
- Modificar `DS/component/SwipeActionsBox.kt` (acciones accesibles).
- Modificar `res/values/strings.xml`.
- Tests: `core/designsystem/component/DiscardGuardTest.kt`.

### Task 1: regla de «descartar cambios» (pura y testeada)
**Files:** Create `DS/component/DiscardGuard.kt`; Test `core/designsystem/component/DiscardGuardTest.kt`.
**Interfaces — Produces:** `enum class DismissAction { CLOSE, ASK_DISCARD, BLOCK }`; `fun dismissActionFor(dirty: Boolean, saving: Boolean): DismissAction`.

- [ ] **Paso 1: test que falla**
```kotlin
package com.dlunaunizar.bobitos.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscardGuardTest {
    @Test
    fun sinCambiosCierraDirectamente() {
        assertEquals(DismissAction.CLOSE, dismissActionFor(dirty = false, saving = false))
    }

    @Test
    fun conCambiosPideConfirmacion() {
        assertEquals(DismissAction.ASK_DISCARD, dismissActionFor(dirty = true, saving = false))
    }

    @Test
    fun guardandoNoSeCierraPorGesto() {
        assertEquals(DismissAction.BLOCK, dismissActionFor(dirty = true, saving = true))
        assertEquals(DismissAction.BLOCK, dismissActionFor(dirty = false, saving = true))
    }
}
```
- [ ] **Paso 2:** `./gradlew :app:testDebugUnitTest --tests '*DiscardGuardTest'` → FALLA (no compila).
- [ ] **Paso 3: implementación**
```kotlin
package com.dlunaunizar.bobitos.core.designsystem.component

enum class DismissAction { CLOSE, ASK_DISCARD, BLOCK }

/** Qué hacer cuando la persona intenta cerrar un formulario (gesto, tocar fuera o «Cancelar»). */
fun dismissActionFor(dirty: Boolean, saving: Boolean): DismissAction = when {
    saving -> DismissAction.BLOCK
    dirty -> DismissAction.ASK_DISCARD
    else -> DismissAction.CLOSE
}
```
- [ ] **Paso 4:** test → PASA. **Paso 5: commit** `feat(diseño): regla de descartar cambios de un formulario`.

### Task 2: `BobitosDialog` y `BobitosFormSheet`
**Files:** Create `DS/component/BobitosDialog.kt`, `DS/component/BobitosFormSheet.kt`; Modify `res/values/strings.xml`.
**Interfaces — Consumes:** `dismissActionFor`, `Spacing`. **Produces:**
`@Composable fun BobitosDialog(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit, destructive: Boolean = false, confirmEnabled: Boolean = true, content: (@Composable () -> Unit)? = null)`;
`@Composable fun BobitosFormSheet(title: String, confirmLabel: String, confirmEnabled: Boolean, saving: Boolean, dirty: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit)`.

- [ ] **Paso 1: strings**
```xml
<string name="discard_changes_title">¿Descartar los cambios?</string>
<string name="discard_changes_message">Lo que has escrito no se guardará.</string>
<string name="discard_changes_confirm">Descartar</string>
<string name="discard_changes_keep_editing">Seguir editando</string>
```
  (`cancel` y `confirm` ya existen en `strings.xml`.)
- [ ] **Paso 2: `BobitosDialog`.** `AlertDialog` con título, mensaje, `content` opcional bajo el mensaje, botón de confirmar (`Button` relleno; si `destructive`, `ButtonDefaults.buttonColors(containerColor = colorScheme.error, contentColor = colorScheme.onError)`) y `TextButton` con `R.string.cancel`. Cada botón mide ≥ 48 dp (valor por defecto de Material).
- [ ] **Paso 3: `BobitosFormSheet`.**
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BobitosFormSheet(
    title: String,
    confirmLabel: String,
    confirmEnabled: Boolean,
    saving: Boolean,
    dirty: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var askDiscard by rememberSaveable { mutableStateOf(false) }
    val currentDirty by rememberUpdatedState(dirty)
    val currentSaving by rememberUpdatedState(saving)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            if (target != SheetValue.Hidden) {
                true
            } else {
                when (dismissActionFor(currentDirty, currentSaving)) {
                    DismissAction.CLOSE -> true
                    DismissAction.ASK_DISCARD -> { askDiscard = true; false }
                    DismissAction.BLOCK -> false
                }
            }
        },
    )
    val requestClose = {
        when (dismissActionFor(dirty, saving)) {
            DismissAction.CLOSE -> onDismiss()
            DismissAction.ASK_DISCARD -> askDiscard = true
            DismissAction.BLOCK -> Unit
        }
    }
    ModalBottomSheet(onDismissRequest = requestClose, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .padding(horizontal = Spacing.lg)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                content = content,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = requestClose, enabled = !saving) { Text(stringResource(R.string.cancel)) }
                Button(onClick = onConfirm, enabled = confirmEnabled && !saving) { Text(confirmLabel) }
            }
        }
    }
    if (askDiscard) {
        BobitosDialog(
            title = stringResource(R.string.discard_changes_title),
            message = stringResource(R.string.discard_changes_message),
            confirmLabel = stringResource(R.string.discard_changes_confirm),
            destructive = true,
            onConfirm = { askDiscard = false; onDismiss() },
            onDismiss = { askDiscard = false },
        )
    }
}
```
  Ajustes obligatorios al compilar: importar `SheetValue`, `rememberUpdatedState`, `heading`/`semantics`, `navigationBarsPadding`, `imePadding`; si `ModalBottomSheet` aplica ya los insets del teclado en esta versión del BOM, quitar `imePadding()` para no duplicar el hueco (comprobar en dispositivo con el teclado abierto).
- [ ] **Paso 4:** `./gradlew :app:compileDebugKotlin :app:ktlintCheck :app:detekt` → verde. **Paso 5: commit** `feat(diseño): BobitosDialog y BobitosFormSheet`.

### Task 3: Tareas — editor en sheet con borrador guardable
**Files:** Modify `feature/tasks/TasksScreen.kt` (`TaskEditor` ~677-781; estado del editor 155-157; guardado 316-328; `TaskDateFields` 843, `TaskTypePicker` 890, `RecurrencePicker` 970, `CustomRecurrenceFields` 997).
**Interfaces — Consumes:** `BobitosFormSheet`, el `TaskValidation.validate` y `parseDueDate` existentes.

- [ ] **Paso 1:** sustituir el `AlertDialog` de `TaskEditor` por `BobitosFormSheet`; mover el contenido del `Column` actual a su `content`. `confirmLabel = stringResource(R.string.save)` (reutilizar la cadena de guardar existente en `TasksScreen`; comprobar su nombre con `grep -n "R.string" TasksScreen.kt` antes de crear una nueva). `confirmEnabled = validation == null`; `saving` = el que ya se usa para deshabilitar el botón.
- [ ] **Paso 2: borrador guardable.** Cambiar cada `remember(task?.id, template)` por `rememberSaveable(task?.id, template?.id)`. Tipos guardables en Bundle: `String` (título, descripción, fechas ISO, id de responsable), `Boolean`, `Int`. Los enums (prioridad, tipo, modo de recurrencia, unidad) se guardan por `name` y se reconstruyen con `valueOf`. Valores iniciales: los de la tarea o plantilla, como hoy.
- [ ] **Paso 3: `dirty`.** Calcular `dirty = título != inicial.título || descripción != … || …` comparando cada campo con su valor inicial, sin tocar el modelo.
- [ ] **Paso 4: estado del editor por id.** `editorTask: TaskItem?` pasa a `editorTaskId: String?` guardado con `rememberSaveable`, y la tarea se resuelve desde la lista actual (si ya no existe, se cierra el sheet). `editorVisible` y `editorTemplate` (id de plantilla) también `rememberSaveable`.
- [ ] **Paso 5:** el selector de plantillas (`TaskTemplatePicker`, hoy `AlertDialog` con lista) pasa a `BobitosDialog` sin confirmar o se mantiene como está (decidir al ver el resultado); no bloquea la tarea.
- [ ] **Paso 6:** compilar + `ktlintCheck` + `detekt`. **Paso 7: commit** `feat(tareas): editor de tarea en bottom sheet con borrador guardable`.

### Task 4: Compra — editor en sheet y confirmaciones
**Files:** Modify `feature/shopping/ShoppingScreen.kt` (`ShoppingItemEditor` ~593-699; confirmaciones en 351 y 373; duplicado en 308).

- [ ] **Paso 1:** `ShoppingItemEditor` → `BobitosFormSheet`. Los 5 `remember(item?.id)` (S:602-606) pasan a `rememberSaveable(item?.id)`. `dirty` = algún campo distinto del inicial. Los chips de sugerencias (`LazyRow`) se quedan donde están, dentro del contenido.
- [ ] **Paso 2:** estado del editor por id (`editedItemId`), igual que en Tareas.
- [ ] **Paso 3:** las confirmaciones de borrar ítem (S:351) y vaciar comprados (S:373) pasan a `BobitosDialog(destructive = true)`. El aviso de duplicado (S:308, dos opciones: actualizar cantidad / añadir igualmente) se mantiene como `AlertDialog` propio; no es una confirmación sí/no.
- [ ] **Paso 4:** comprobar que tras guardar un duplicado el aviso sigue apareciendo (Review Focus 7). **Paso 5: commit** `feat(compra): editor de compra en bottom sheet y confirmaciones comunes`.

### Task 5: borrar tarea con `BobitosDialog` y acciones accesibles del swipe
**Files:** Modify `feature/tasks/TasksScreen.kt:332`, `DS/component/SwipeActionsBox.kt` (acción en línea 59, icono en 70), `feature/tasks/TasksScreen.kt` (~234, ~556-610), `feature/shopping/ShoppingScreen.kt` (~189, ~237).

- [ ] **Paso 1:** el borrado de tarea (T:332) pasa a `BobitosDialog(destructive = true)`.
- [ ] **Paso 2: `SwipeActionsBox` accesible.** Añadir a `SwipeAction` un campo `label: String` (obligatorio) y, en el contenedor, `Modifier.semantics { customActions = listOfNotNull(startAction?.let { CustomAccessibilityAction(it.label) { it.onSwipe(); true } }, endAction?.let { … }) }`. El icono del fondo sigue siendo decorativo (`contentDescription = null`) porque la acción ya se anuncia por `customActions`. Actualizar los 4 usos (T:234; S:189 y S:237) con etiquetas: «Completar» / «Reabrir» / «Marcar como comprado» / «Eliminar», todas en `strings.xml`.
- [ ] **Paso 3: alternativa visible para editar.** La tarjeta de tarea y la de compra abren el editor al tocarlas (`onClick` de la `Card`), además de la entrada «Editar» del menú ⋮, que se mantiene. Solo si `canWrite`.
- [ ] **Paso 4:** `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:ktlintCheck :app:detekt :app:assembleDebug` → verde. **Paso 5: commit** `feat(a11y): acciones de deslizar expuestas a TalkBack y tarjetas que abren el editor`.

### Cierre de 3a
- [ ] Recorrido manual en dispositivo (no hay emulador en esta máquina): abrir editor de tarea y de compra; girar con texto escrito; teclado abierto con descripción/notas; deslizar hacia abajo con y sin cambios; guardar; menús del editor completos; selector de fecha; duplicado en Compra; TalkBack (título del sheet y acciones de la fila); usuario solo lectura; claro/oscuro y fuente 200 %.
- [ ] PR «Rediseño UI · Fase 3a: sheets y diálogos comunes (Tareas y Compra)».

## Autorrevisión
- **Cobertura:** editores a sheet (3a/3b/3c), borrador y descarte (T1–T4), acciones descubribles (T5, 3b), FAB en Comidas y selector de color (3b), 9 confirmaciones (T4/T5 y 3c), accesibilidad (T5, 3b).
- **Sin placeholders en 3a:** componentes con código; las migraciones indican líneas y transformación exacta. El paso 5 de la Tarea 3 deja una decisión explícita y acotada (plantillas).
- **Tipos:** `DismissAction`, `dismissActionFor`, `BobitosDialog`, `BobitosFormSheet`, `SwipeAction.label` se usan con los mismos nombres en todas las tareas.
- **Decisiones recogidas y recortadas:** se descarta la pista de «asomar» el swipe (necesita persistencia y no aporta frente a tarjeta clicable + menú ⋮ + acciones de TalkBack); el selector de espacio en sheet pasa a 3c; el guardado optimista no se toca.
