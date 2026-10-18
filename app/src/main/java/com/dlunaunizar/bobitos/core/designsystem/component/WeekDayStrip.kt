package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.BobitosDates
import com.dlunaunizar.bobitos.core.designsystem.BouncySpring
import com.dlunaunizar.bobitos.core.designsystem.motionSpec
import com.dlunaunizar.bobitos.core.designsystem.rememberReduceMotion
import java.time.LocalDate

private val DayGap = 4.dp

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
 * Tira de la semana: flecha anterior, siete días y flecha siguiente en la misma fila.
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
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = previousLabel)
        }
        val selectedIndex = days.indexOf(selected)
        val pillPosition by animateFloatAsState(
            targetValue = selectedIndex.toFloat(),
            animationSpec = motionSpec(rememberReduceMotion(), BouncySpring),
            label = "weekDayPill",
        )
        val pillShape = MaterialTheme.shapes.medium
        Row(
            modifier = Modifier
                .weight(1f)
                // La pastilla del día elegido se dibuja una vez y se desliza hasta el día tocado.
                .drawBehind {
                    if (selectedIndex < 0) return@drawBehind
                    val gap = DayGap.toPx()
                    val cell = (size.width - gap * (days.size - 1)) / days.size
                    val outline = pillShape.createOutline(Size(cell, size.height), layoutDirection, this)
                    translate(left = pillPosition * (cell + gap)) { drawOutline(outline, colors.selectedContainer) }
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
        IconButton(onClick = onNext) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = nextLabel)
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
