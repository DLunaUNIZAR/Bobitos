package com.dlunaunizar.bobitos.core.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.designsystem.theme.categoryCardColors
import com.dlunaunizar.bobitos.data.repository.SpaceModuleCounts
import com.dlunaunizar.bobitos.feature.spaces.DayPeriod
import com.dlunaunizar.bobitos.feature.spaces.MyDayCard
import com.dlunaunizar.bobitos.feature.spaces.SpaceHomeDigest
import com.dlunaunizar.bobitos.feature.spaces.WorkloadSection
import com.dlunaunizar.bobitos.feature.spaces.dayPeriodFor
import com.dlunaunizar.bobitos.feature.spaces.firstName
import com.dlunaunizar.bobitos.feature.spaces.todayItemCount
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * «Hoy»: cabecera con saludo y resumen, fichas de contadores de cada módulo, «Mi día» y reparto.
 * Mientras [digest] o [counts] no han cargado (o sin conexión) solo se ve el saludo.
 */
@Composable
internal fun SpaceTodayScreen(
    displayName: String,
    counts: SpaceModuleCounts?,
    digest: SpaceHomeDigest?,
    onOpen: (BobitosDestination) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        TodayHeader(displayName = displayName, digest = digest)
        counts?.let { CounterTiles(counts = it, onOpen = onOpen) }
        digest?.takeUnless { it.myDayEmpty }?.let { MyDayCard(it) }
        digest?.workload?.let { WorkloadSection(it) }
    }
}

@Composable
private fun TodayHeader(displayName: String, digest: SpaceHomeDigest?) {
    val now = remember { LocalDateTime.now() }
    val greeting = stringResource(
        when (dayPeriodFor(now.hour)) {
            DayPeriod.MORNING -> R.string.space_today_greeting_morning
            DayPeriod.AFTERNOON -> R.string.space_today_greeting_afternoon
            DayPeriod.EVENING -> R.string.space_today_greeting_evening
        },
    )
    val name = firstName(displayName)
    val date = remember {
        LocalDate.now()
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()))
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = if (name.isBlank()) {
                    greeting
                } else {
                    stringResource(
                        R.string.space_today_greeting_named,
                        greeting,
                        name,
                    )
                },
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(text = date, style = MaterialTheme.typography.bodyLarge)
            digest?.let {
                val count = it.todayItemCount()
                Text(
                    text = if (count == 0) {
                        stringResource(R.string.space_today_nothing)
                    } else {
                        pluralStringResource(R.plurals.space_today_summary, count, count)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }
    }
}

// Cuadrícula 2 × 2: cada ficha lleva a su módulo y se lee como un solo elemento («3 Tareas»).
@Composable
private fun CounterTiles(counts: SpaceModuleCounts, onOpen: (BobitosDestination) -> Unit) {
    val tiles = listOf(
        BobitosDestination.Shopping to counts.pendingShopping,
        BobitosDestination.Tasks to counts.pendingTasks,
        BobitosDestination.Calendar to counts.upcomingEvents,
        BobitosDestination.Meals to counts.todayMeals,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                row.forEach { (destination, count) ->
                    CounterTile(
                        destination = destination,
                        count = count,
                        onClick = { onOpen(destination) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CounterTile(destination: BobitosDestination, count: Int, onClick: () -> Unit, modifier: Modifier) {
    val accent = destination.moduleColor()
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = accent?.let { categoryCardColors(it) } ?: CardDefaults.cardColors(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = null,
                tint = accent ?: MaterialTheme.colorScheme.onSurface,
            )
            Text(text = count.toString(), style = MaterialTheme.typography.headlineMedium)
            Text(text = stringResource(destination.titleRes), style = MaterialTheme.typography.labelLarge)
        }
    }
}
