package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.BobitosDates
import com.dlunaunizar.bobitos.core.designsystem.BouncySpring
import com.dlunaunizar.bobitos.core.designsystem.motionSpec
import com.dlunaunizar.bobitos.core.designsystem.rememberReduceMotion
import java.time.LocalDate
import kotlin.math.abs

private val DayGap = 2.dp
private val SwipeDistance = 56.dp
private val SwipeVelocity = 800.dp
private const val WEEK_SLIDE_MILLIS = 300

/** Colores de [WeekDayStrip]. La fase de color por módulo pasará los suyos. */
@Immutable
data class WeekDayStripColors(val selectedContainer: Color, val selectedContent: Color, val todayOutline: Color)

object WeekDayStripDefaults {
    @Composable
    fun colors(): WeekDayStripColors = WeekDayStripColors(
        selectedContainer = MaterialTheme.colorScheme.secondaryContainer,
        selectedContent = MaterialTheme.colorScheme.onSecondaryContainer,
        todayOutline = MaterialTheme.colorScheme.primary,
    )
}

/**
 * Tira de la semana: siete días que ocupan toda la fila. Se cambia de semana deslizando la tira (más de
 * 56 dp o con un gesto rápido, y más horizontal que vertical) o con las acciones de TalkBack
 * [previousLabel] y [nextLabel]; la semana nueva entra por el lado del gesto.
 * Cada día se anuncia entero («miércoles, 14 de octubre de 2026, hoy») y no por su inicial y su número.
 */
@Composable
fun WeekDayStrip(
    days: List<LocalDate>,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    previousLabel: String,
    nextLabel: String,
    modifier: Modifier = Modifier,
    colors: WeekDayStripColors = WeekDayStripDefaults.colors(),
) {
    val reduceMotion = rememberReduceMotion()
    val currentPrevious by rememberUpdatedState(onPrevious)
    val currentNext by rememberUpdatedState(onNext)
    AnimatedContent(
        targetState = days,
        modifier = modifier
            .clipToBounds()
            .weekSwipe(onPrevious = { currentPrevious() }, onNext = { currentNext() })
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(previousLabel) {
                        currentPrevious()
                        true
                    },
                    CustomAccessibilityAction(nextLabel) {
                        currentNext()
                        true
                    },
                )
            },
        transitionSpec = {
            if (reduceMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                // Una semana posterior entra por la derecha; una anterior, por la izquierda.
                val direction = if (targetState.first() > initialState.first()) 1 else -1
                val offset = motionSpec(false, tween<IntOffset>(WEEK_SLIDE_MILLIS))
                (
                    slideInHorizontally(offset) { it * direction } togetherWith
                        slideOutHorizontally(offset) { -it * direction }
                    )
            }
        },
        label = "weekDays",
    ) { week ->
        WeekRow(week, selected, today, colors, onSelect, reduceMotion)
    }
}

@Composable
private fun WeekRow(
    days: List<LocalDate>,
    selected: LocalDate,
    today: LocalDate,
    colors: WeekDayStripColors,
    onSelect: (LocalDate) -> Unit,
    reduceMotion: Boolean,
) {
    val selectedIndex = days.indexOf(selected)
    val pillPosition by animateFloatAsState(
        targetValue = selectedIndex.coerceAtLeast(0).toFloat(),
        animationSpec = motionSpec(reduceMotion, BouncySpring),
        label = "weekDayPill",
    )
    val pillShape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // La pastilla del día elegido se dibuja una vez y se desliza hasta el día tocado, sin salir de la fila.
            .drawBehind {
                if (selectedIndex < 0) return@drawBehind
                val gap = DayGap.toPx()
                val cell = (size.width - gap * (days.size - 1)) / days.size
                val outline = pillShape.createOutline(Size(cell, size.height), layoutDirection, this)
                val position = pillPosition.coerceIn(0f, (days.size - 1).toFloat())
                translate(left = position * (cell + gap)) { drawOutline(outline, colors.selectedContainer) }
            }
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(DayGap),
    ) {
        days.forEach { day ->
            WeekDay(
                date = day,
                isSelected = day == selected,
                isToday = day == today,
                colors = colors,
                onClick = { onSelect(day) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Detecta el deslizamiento horizontal de la tira sin consumir los toques de los días. */
private fun Modifier.weekSwipe(onPrevious: () -> Unit, onNext: () -> Unit): Modifier = pointerInput(Unit) {
    val distanceThreshold = SwipeDistance.toPx()
    val velocityThreshold = SwipeVelocity.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var last = down.position
        var dx = 0f
        var dy = 0f
        do {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            tracker.addPosition(change.uptimeMillis, change.position)
            dx += change.position.x - last.x
            dy += change.position.y - last.y
            last = change.position
        } while (change.pressed)
        val fast = abs(tracker.calculateVelocity().x) >= velocityThreshold && abs(dx) > viewConfiguration.touchSlop
        val horizontal = abs(dx) > abs(dy)
        if (horizontal && (abs(dx) > distanceThreshold || fast)) {
            if (dx < 0) onNext() else onPrevious()
        }
    }
}

@Composable
private fun WeekDay(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    colors: WeekDayStripColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = BobitosDates.accessibleDate(date) +
        if (isToday) ", " + stringResource(R.string.today) else ""
    val content = if (isSelected) colors.selectedContent else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                this.selected = isSelected
            },
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        contentColor = content,
        border = if (isToday) BorderStroke(2.dp, colors.todayOutline) else null,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = BobitosDates.dayInitial(date.dayOfWeek), style = MaterialTheme.typography.labelSmall)
            Text(text = date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
        }
    }
}
