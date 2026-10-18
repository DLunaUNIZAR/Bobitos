package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dlunaunizar.bobitos.core.designsystem.theme.AppModule
import com.dlunaunizar.bobitos.core.designsystem.theme.ModuleTheme
import com.dlunaunizar.bobitos.core.model.SyncStatus

/**
 * Andamio común de las pantallas a pantalla completa: barra superior con título (y atrás opcional),
 * estado de sincronización bajo la barra, FAB en su hueco y host de avisos propio, publicado en
 * [LocalSnackbarHostState] para el contenido. Con [saving] superpone una barra de progreso arriba
 * del contenido sin desplazarlo. Con [module] toda la pantalla (FAB, hojas…) toma el color del módulo.
 */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    syncStatus: SyncStatus? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    module: AppModule? = null,
    saving: Boolean = false,
    content: @Composable (PaddingValues) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    ModuleTheme(module) {
        Scaffold(
            modifier = modifier,
            topBar = {
                Column {
                    BobitosTopBar(title = title, onBack = onBack, actions = actions)
                    syncStatus?.let { SyncStatusBanner(status = it) }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = floatingActionButton,
        ) { padding ->
            ScaffoldBody(snackbarHostState, saving, padding, content)
        }
    }
}

/**
 * Andamio de un módulo dentro del espacio: sin barra superior (la pone `WorkspaceScaffold`, que ya
 * aplica los insets) y con su propio host de avisos, que sombrea el del espacio para que el aviso
 * salga encima de la barra inferior y el FAB suba por encima.
 */
@Composable
fun ModuleScaffold(
    modifier: Modifier = Modifier,
    floatingActionButton: @Composable () -> Unit = {},
    saving: Boolean = false,
    content: @Composable (PaddingValues) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = floatingActionButton,
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        ScaffoldBody(snackbarHostState, saving, padding, content)
    }
}

@Composable
private fun ScaffoldBody(
    snackbarHostState: SnackbarHostState,
    saving: Boolean,
    padding: PaddingValues,
    content: @Composable (PaddingValues) -> Unit,
) {
    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Box {
            content(padding)
            if (saving) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = padding.calculateTopPadding())
                        .align(Alignment.TopCenter),
                )
            }
        }
    }
}
