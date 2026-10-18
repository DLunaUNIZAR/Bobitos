package com.dlunaunizar.bobitos.feature.spaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing

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
