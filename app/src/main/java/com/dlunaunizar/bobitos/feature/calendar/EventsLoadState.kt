package com.dlunaunizar.bobitos.feature.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.messageRes
import com.dlunaunizar.bobitos.core.designsystem.component.ErrorState
import com.dlunaunizar.bobitos.core.designsystem.component.LoadingState

// Mientras los eventos cargan o fallan, el área bajo la cabecera muestra la carga o el error con «Reintentar»
// en vez de un periodo vacío fingido. Al cambiar de periodo con eventos ya a la vista (`refreshing`), se conserva
// el contenido de antes y una barra fina arriba avisa de que llega el nuevo.
@Composable
internal fun <T> EventsLoadState(
    events: UiState<T>,
    refreshing: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    when (events) {
        UiState.Loading -> LoadingState(modifier)
        is UiState.Error -> ErrorState(
            modifier,
            message = stringResource(events.failure.messageRes()),
            onRetry = onRetry,
        )
        is UiState.Content -> Box(modifier) {
            content(events.value)
            if (refreshing) {
                val loading = stringResource(R.string.generic_loading)
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .semantics { contentDescription = loading },
                )
            }
        }
    }
}
