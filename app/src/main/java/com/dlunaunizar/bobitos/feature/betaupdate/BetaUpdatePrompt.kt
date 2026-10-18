package com.dlunaunizar.bobitos.feature.betaupdate

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosDialog
import com.dlunaunizar.bobitos.data.betaupdate.BetaRelease

/**
 * Aviso de una beta nueva. «Más tarde» pospone esa versión; Atrás o tocar fuera solo cierra el aviso
 * ([onDismiss]), que es distinto.
 */
@Composable
fun BetaUpdatePrompt(release: BetaRelease, onUpdate: () -> Unit, onLater: () -> Unit, onDismiss: () -> Unit) {
    BobitosDialog(
        title = stringResource(R.string.beta_update_prompt_title),
        message = stringResource(R.string.beta_update_prompt_message, release.displayVersion),
        confirmLabel = stringResource(R.string.beta_update_prompt_update),
        onConfirm = onUpdate,
        onDismiss = onLater,
        dismissLabel = stringResource(R.string.beta_update_prompt_later),
        onDismissRequest = onDismiss,
    )
}
