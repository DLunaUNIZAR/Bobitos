@file:Suppress("MatchingDeclarationName") // DayTimelineDefaults solo son las medidas de la línea de tiempo.

package com.dlunaunizar.bobitos.feature.calendar

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.BobitosDates
import com.dlunaunizar.bobitos.core.designsystem.component.MoreOptionsButton
import com.dlunaunizar.bobitos.core.designsystem.pressScale
import com.dlunaunizar.bobitos.core.designsystem.rememberReduceMotion
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Medidas de la línea de tiempo del día. */
internal object DayTimelineDefaults {
    /** Alto de una hora con la letra normal; con la letra grande se multiplica por el factor de escala. */
    val HourHeight: Dp = 64.dp

    /** Ancho de la columna con las etiquetas «HH:00». */
    val LabelWidth: Dp = 52.dp
}

/** `testTag` de la tarjeta de un evento en la línea de tiempo. */
internal fun timelineEventTag(id: String): String = "timeline-event-$id"

/** Alto de una hora con la letra del sistema aplicada: crece con la letra para que el texto de las tarjetas quepa. */
@Composable
internal fun rememberTimelineHourHeight(): Dp {
    val fontScale = LocalDensity.current.fontScale
    return DayTimelineDefaults.HourHeight * maxOf(1f, fontScale)
}

/**
 * Una franja de una hora: la etiqueta «HH:00» a la izquierda y una línea fina arriba. Con [canWrite], mantenerla
 * pulsada llama a [onLongPress] (con vibración) y TalkBack ofrece «Crear evento a las HH:00». No tiene `onClick`.
 */
@Composable
internal fun HourSlot(
    hour: Int,
    height: Dp,
    canWrite: Boolean,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val createLabel = stringResource(R.string.calendar_create_at, BobitosDates.time(LocalTime.of(hour, 0)))
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val haptics = LocalHapticFeedback.current
    val pressSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(
                if (canWrite) {
                    // Al pulsar la franja se ilumina y, al completar el mantenido, vibra: así se nota que va a crear.
                    Modifier
                        .indication(pressSource, LocalIndication.current)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = { offset ->
                                    val press = PressInteraction.Press(offset)
                                    pressSource.emit(press)
                                    val released = tryAwaitRelease()
                                    pressSource.emit(
                                        if (released) {
                                            PressInteraction.Release(press)
                                        } else {
                                            PressInteraction.Cancel(press)
                                        },
                                    )
                                },
                                onLongPress = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    currentOnLongPress()
                                },
                            )
                        }
                        .semantics(mergeDescendants = true) {
                            customActions = listOf(
                                CustomAccessibilityAction(createLabel) {
                                    onLongPress()
                                    true
                                },
                            )
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        Text(
            text = BobitosDates.time(LocalTime.of(hour, 0)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(DayTimelineDefaults.LabelWidth)
                .padding(top = 4.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * El día como línea de tiempo: 24 franjas de [hourHeight][rememberTimelineHourHeight] y, encima, una tarjeta por
 * evento con hora, tan alta como dura y repartida en columnas con los que se solapan (ver [layoutDayEvents]).
 */
@Composable
internal fun DayTimeline(
    day: LocalDate,
    events: List<CalendarEvent>,
    canWrite: Boolean,
    onEdit: (CalendarEvent) -> Unit,
    onDelete: (String) -> Unit,
    onCreateAt: (LocalTime) -> Unit,
    deleteEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val hourHeight = rememberTimelineHourHeight()
    val placed = remember(events, day) { layoutDayEvents(events, day, ZoneId.systemDefault()) }
    BoxWithConstraints(modifier.fillMaxWidth().height(hourHeight * 24)) {
        val labelWidth = DayTimelineDefaults.LabelWidth
        val columnsWidth = maxWidth - labelWidth
        Column {
            repeat(24) { hour ->
                HourSlot(hour, hourHeight, canWrite, onLongPress = { onCreateAt(LocalTime.of(hour, 0)) })
            }
        }
        placed.forEach { item ->
            val slotWidth = columnsWidth / item.columns
            // La clave ata el estado de la tarjeta (su menú abierto) a su evento, no a su posición en la lista.
            key(item.event.id) {
                Box(
                    Modifier
                        .offset(
                            x = labelWidth + slotWidth * item.column,
                            y = hourHeight * item.topMinute / 60,
                        )
                        .size(slotWidth, hourHeight * (item.bottomMinute - item.topMinute) / 60),
                ) {
                    TimelineEventCard(
                        item = item,
                        canWrite = canWrite,
                        onEdit = { onEdit(item.event) },
                        onDelete = { onDelete(item.event.id) },
                        deleteEnabled = deleteEnabled,
                        modifier = Modifier.fillMaxSize().padding(1.dp),
                    )
                }
            }
        }
    }
}

private val CardPadding = 6.dp

/** Alto de una línea de [style] con la letra del sistema (usa 1,25 × el cuerpo si el estilo no fija interlineado). */
@Composable
private fun lineHeightOf(style: TextStyle): Dp {
    val density = LocalDensity.current
    return with(density) {
        (if (style.lineHeight.isSpecified) style.lineHeight else style.fontSize * 1.25f).toDp()
    }
}

/**
 * La tarjeta de un evento: barra de color, título y rango (en una línea con puntos suspensivos si no cabe); el título
 * gana líneas y aparecen los participantes solo si la altura y el ancho sobran. Con permiso de escritura, tocarla abre
 * el editor y mantenerla pulsada (o las acciones de TalkBack) ofrece «Editar» y «Eliminar». Si comparte columna y el
 * texto quedaría por debajo de [CompactTextWidth] con un ⋮ al lado, la tarjeta es compacta: sin ⋮, márgenes finos,
 * título en una línea y solo la hora de inicio. El contenido va arriba para que el título se vea al abrir el día.
 */
@Composable
internal fun TimelineEventCard(
    item: TimelineEvent,
    canWrite: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    deleteEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val event = item.event
    var menuExpanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val actions = cardActions(canWrite, deleteEnabled, onEdit, onDelete)
    Card(
        modifier = modifier
            .pressScale(interactionSource, rememberReduceMotion())
            .semantics(mergeDescendants = true) { if (actions.isNotEmpty()) customActions = actions }
            .testTag(timelineEventTag(event.id)),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .then(
                    if (canWrite) {
                        Modifier.combinedClickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            onClick = onEdit,
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuExpanded = true
                            },
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            val layout = cardLayout(item.columns, canWrite, maxWidth, maxHeight)
            Row(Modifier.fillMaxSize().padding(start = layout.pad), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .padding(vertical = layout.pad)
                        .width(4.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(event.color.accent()),
                )
                CardTexts(event, layout, Modifier.weight(1f))
                if (layout.showMenuButton) {
                    MoreOptionsButton(
                        itemName = event.title,
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(layout.menuWidth),
                    )
                }
            }
            // El menú se ancla arriba a la derecha (junto al ⋮) o, sin ⋮, arriba a la izquierda.
            Box(Modifier.align(if (layout.showMenuButton) Alignment.TopEnd else Alignment.TopStart)) {
                TimelineCardMenu(menuExpanded, { menuExpanded = false }, onEdit, onDelete, deleteEnabled)
            }
        }
    }
}

/** Acciones de TalkBack de una tarjeta editable: las mismas que el menú (vacías sin permiso de escritura). */
@Composable
private fun cardActions(
    canWrite: Boolean,
    deleteEnabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
): List<CustomAccessibilityAction> {
    val editLabel = stringResource(R.string.calendar_edit)
    val deleteLabel = stringResource(R.string.calendar_delete)
    if (!canWrite) return emptyList()
    return listOf(
        CustomAccessibilityAction(editLabel) {
            onEdit()
            true
        },
        CustomAccessibilityAction(deleteLabel) {
            if (deleteEnabled) onDelete()
            deleteEnabled
        },
    )
}

/** Cómo se dibuja una tarjeta según el sitio que tiene (ver [TimelineEventCard]). */
private class CardLayout(
    val compact: Boolean,
    val pad: Dp,
    val menuWidth: Dp,
    val showMenuButton: Boolean,
    val showParticipants: Boolean,
    val titleLines: Int,
)

@Composable
private fun cardLayout(columns: Int, canWrite: Boolean, width: Dp, height: Dp): CardLayout {
    val titleLine = lineHeightOf(MaterialTheme.typography.titleSmall)
    val rangeLine = lineHeightOf(MaterialTheme.typography.labelMedium)
    val participantsLine = lineHeightOf(MaterialTheme.typography.bodySmall)
    val menuWidth = if (columns > 1) 36.dp else 48.dp
    val compact = columns > 1 && width - FullChrome - menuWidth < CompactTextWidth
    val pad = if (compact) CompactPadding else CardPadding
    val available = height - pad * 2
    val showParticipants = !compact && width >= 140.dp && available >= titleLine + rangeLine + participantsLine
    val titleLines = if (compact) {
        1
    } else {
        ((available - rangeLine - if (showParticipants) participantsLine else 0.dp) / titleLine).toInt().coerceIn(1, 3)
    }
    return CardLayout(compact, pad, menuWidth, canWrite && !compact, showParticipants, titleLines)
}

/** Título, rango (o solo la hora de inicio si la tarjeta es compacta) y participantes de una tarjeta. */
@Composable
private fun CardTexts(event: CalendarEvent, layout: CardLayout, modifier: Modifier = Modifier) {
    val zone = ZoneId.systemDefault()
    val start = event.startAt.atZone(zone).toLocalDateTime()
    val range = if (layout.compact) {
        BobitosDates.time(start.toLocalTime())
    } else {
        BobitosDates.timeRange(start, event.endAt.atZone(zone).toLocalDateTime())
    }
    Column(
        modifier.padding(
            start = if (layout.compact) CompactPadding else 8.dp,
            top = layout.pad,
            end = if (layout.showMenuButton) 0.dp else layout.pad,
        ),
    ) {
        Text(
            event.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = layout.titleLines,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = range,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (layout.showParticipants) {
            Text(
                text = event.participantNames.takeIf { it.isNotEmpty() }?.joinToString()
                    ?: stringResource(R.string.calendar_general_event),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Ancho que debe quedar al texto, con el ⋮ al lado, para que la tarjeta no sea compacta. */
private val CompactTextWidth = 72.dp

/** Márgenes de la tarjeta compacta (los de la normal son [CardPadding]). */
private val CompactPadding = 4.dp

/** Márgenes fijos de la tarjeta normal sin contar el ⋮: 6 de la izquierda, 4 de la barra y 8 hasta el texto. */
private val FullChrome = 18.dp

/** El menú «Editar» / «Eliminar» de una tarjeta; se abre con su ⋮ o manteniéndola pulsada. */
@Composable
private fun TimelineCardMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    deleteEnabled: Boolean,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.calendar_edit)) },
            onClick = {
                onDismiss()
                onEdit()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.calendar_delete)) },
            enabled = deleteEnabled,
            onClick = {
                onDismiss()
                onDelete()
            },
        )
    }
}
