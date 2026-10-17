package com.dlunaunizar.bobitos.feature.spaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.model.CalendarEvent

// D1 — «Mi día»: lo del usuario para hoy (tareas que vencen, comidas que cocina/come, sus eventos).
@Composable
fun MyDayCard(digest: SpaceHomeDigest, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(stringResource(R.string.home_my_day_title), style = MaterialTheme.typography.titleMedium)
            if (digest.myDayEmpty) {
                DigestLine(stringResource(R.string.home_my_day_empty))
                return@Column
            }
            if (digest.myTasksToday.isNotEmpty()) {
                DigestLine(
                    stringResource(
                        R.string.home_my_day_tasks,
                        digest.myTasksToday.joinToString(", ") {
                            it.title
                        },
                    ),
                )
            }
            digest.myCookingToday.forEach { meal ->
                DigestLine(stringResource(R.string.home_my_day_cooking, meal.name))
            }
            if (digest.myEatingToday.isNotEmpty()) {
                DigestLine(
                    stringResource(
                        R.string.home_my_day_eating,
                        digest.myEatingToday.joinToString(", ") {
                            it.name
                        },
                    ),
                )
            }
            if (digest.myEventsToday.isNotEmpty()) {
                EventsLine(digest.myEventsToday)
            }
        }
    }
}

@Composable
private fun EventsLine(events: List<CalendarEvent>) {
    val titles = groupTitles(events).map { (title, count) ->
        if (count > 1) stringResource(R.string.home_my_day_event_repeated, title, count) else title
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Icon(
            imageVector = Icons.Rounded.Event,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DigestLine(stringResource(R.string.home_my_day_events, titles.joinToString(", ")))
    }
}

@Composable
private fun DigestLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// D2 — Reparto: pendientes y hechas esta semana por miembro, con barra de equilibrio.
@Composable
fun WorkloadSection(workload: List<MemberWorkload>, modifier: Modifier = Modifier) {
    if (workload.isEmpty()) return
    val maxPending = workload.maxOf { it.pending }.coerceAtLeast(1)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(stringResource(R.string.home_workload_title), style = MaterialTheme.typography.titleMedium)
        workload.forEach { member ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(member.displayName, modifier = Modifier.weight(1f))
                    Text(
                        text = pluralStringResource(R.plurals.home_workload_pending, member.pending, member.pending) +
                            " · " +
                            pluralStringResource(
                                R.plurals.home_workload_done,
                                member.doneThisWeek,
                                member.doneThisWeek,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LinearProgressIndicator(
                    progress = { member.pending.toFloat() / maxPending },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
