package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dlunaunizar.bobitos.R

/** Botón ⋮ de «Más opciones» con nombre: TalkBack dice de qué elemento son las opciones. */
@Composable
fun MoreOptionsButton(itemName: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options_for, itemName))
    }
}
