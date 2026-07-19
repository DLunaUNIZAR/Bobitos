package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dlunaunizar.bobitos.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Diálogo reutilizable de selección de fecha (día/mes/año) de Material 3. Entra y sale un
 * [LocalDate]; la conversión con los millis del DatePicker se hace en UTC solo internamente,
 * así que es independiente de la zona horaria del dispositivo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppDatePickerDialog(initialDate: LocalDate, onConfirm: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
            ) { Text(stringResource(R.string.accept)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DatePicker(state = state)
    }
}
