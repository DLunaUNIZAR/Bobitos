package com.dlunaunizar.bobitos.feature.spaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing

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
                DigestLine(
                    stringResource(
                        R.string.home_my_day_events,
                        digest.myEventsToday.joinToString(", ") {
                            it.title
                        },
                    ),
                )
            }
        }
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
                        text = stringResource(R.string.home_workload_counts, member.pending, member.doneThisWeek),
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
