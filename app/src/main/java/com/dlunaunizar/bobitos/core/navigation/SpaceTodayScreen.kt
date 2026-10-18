package com.dlunaunizar.bobitos.core.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.BobitosDates
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.messageRes
import com.dlunaunizar.bobitos.core.designsystem.component.ErrorState
import com.dlunaunizar.bobitos.core.designsystem.component.LoadingState
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.feature.spaces.DayPeriod
import com.dlunaunizar.bobitos.feature.spaces.TodayAgenda
import com.dlunaunizar.bobitos.feature.spaces.WorkloadSection
import com.dlunaunizar.bobitos.feature.spaces.dayPeriodFor
import com.dlunaunizar.bobitos.feature.spaces.firstName
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * «Hoy», de momento mínimo: saludo y reparto. La agenda de toda la casa la pinta la tarea 16.
 */
@Composable
internal fun SpaceTodayScreen(displayName: String, agenda: UiState<TodayAgenda>, onRetry: () -> Unit) {
    when (agenda) {
        UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Error -> ErrorState(
            modifier = Modifier.fillMaxSize(),
            message = stringResource(agenda.failure.messageRes()),
            onRetry = onRetry,
        )
        is UiState.Content -> Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            TodayHeader(displayName = displayName)
            WorkloadSection(agenda.value.workload)
        }
    }
}

@Composable
private fun TodayHeader(displayName: String) {
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
        BobitosDates.longDay(LocalDate.now())
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
                    stringResource(R.string.space_today_greeting_named, greeting, name)
                },
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(text = date, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
