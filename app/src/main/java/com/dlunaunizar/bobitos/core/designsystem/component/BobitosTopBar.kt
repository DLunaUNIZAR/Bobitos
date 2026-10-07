package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.dlunaunizar.bobitos.R

/**
 * Barra superior común: título en una línea (con elipsis para que no tape el botón atrás ni las
 * acciones con fuentes grandes), botón atrás opcional con su descripción de accesibilidad y acciones.
 */
@Composable
fun BobitosTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    BobitosTopBar(
        titleContent = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
        onBack = onBack,
        actions = actions,
    )
}

/** Variante con título compuesto (p. ej. nombre del espacio y módulo en dos líneas). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BobitosTopBar(
    titleContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = titleContent,
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.navigate_back),
                    )
                }
            }
        },
        actions = actions,
    )
}
