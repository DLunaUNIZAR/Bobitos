package com.dlunaunizar.bobitos.feature.betaupdate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing

/**
 * Fila «Versión» de «Perfil». Si las actualizaciones están disponibles añade «Buscar actualizaciones»;
 * el estado de la búsqueda y la ayuda sin sesión van debajo, dentro de la misma fila.
 */
@Composable
fun AppVersionRow(state: BetaUpdateUiState, onCheck: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.beta_update_version_label), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = state.installedVersion,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.available) {
            OutlinedButton(
                onClick = onCheck,
                enabled = state.manualCheck != ManualCheckState.Checking,
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
            ) { Text(stringResource(R.string.beta_update_check)) }
            val status = statusText(state)
            if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.manualCheck is ManualCheckState.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
private fun statusText(state: BetaUpdateUiState): String? = when (val check = state.manualCheck) {
    ManualCheckState.Checking -> stringResource(R.string.beta_update_checking)
    ManualCheckState.UpToDate -> stringResource(R.string.beta_update_up_to_date)
    is ManualCheckState.Failed -> stringResource(check.error.messageRes())
    ManualCheckState.Idle ->
        if (state.testerSignedIn) null else stringResource(R.string.beta_update_sign_in_hint)
}
