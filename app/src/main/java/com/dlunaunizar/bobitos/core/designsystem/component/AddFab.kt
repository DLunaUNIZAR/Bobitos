package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * Botón «Añadir» de las pantallas: desplegado muestra icono y etiqueta; recogido, solo el icono, con la etiqueta como
 * descripción.
 */
@Composable
fun AddFab(@StringRes label: Int, expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        expanded = expanded,
        onClick = onClick,
        modifier = modifier,
        icon = {
            Icon(
                Icons.Rounded.Add,
                contentDescription = if (expanded) null else stringResource(label),
            )
        },
        text = { Text(stringResource(label)) },
    )
}
