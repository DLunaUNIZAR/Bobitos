package com.dlunaunizar.bobitos.feature.betaupdate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** El aviso al abrir y la hoja de la actualización, encima del resto de la app. */
@Composable
fun BetaUpdateOverlays(state: BetaUpdateUiState, actions: BetaUpdateActions) {
    state.prompt?.let { release ->
        BetaUpdatePrompt(
            release = release,
            onUpdate = actions::acceptPrompt,
            onLater = actions::postponePrompt,
            onDismiss = actions::dismissPrompt,
        )
    }
    state.sheet?.let { sheet ->
        BetaUpdateSheet(
            state = sheet,
            onStartDownload = actions::startDownload,
            onClose = actions::closeSheet,
        )
    }
}

/**
 * Estado para la fila «Versión» de «Perfil»: sin aviso ni hoja, y solo cambia con los campos que la fila lee, para
 * que el progreso de la descarga no reconstruya el grafo de navegación.
 */
@Composable
fun rememberProfileBetaState(state: BetaUpdateUiState): BetaUpdateUiState =
    remember(state.installedVersion, state.available, state.testerSignedIn, state.manualCheck) {
        state.copy(prompt = null, sheet = null)
    }
