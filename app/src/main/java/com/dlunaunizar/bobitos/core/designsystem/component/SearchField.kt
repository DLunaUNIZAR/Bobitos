package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dlunaunizar.bobitos.R

/** Campo de búsqueda de texto reutilizable; no se pinta si [visible] es false. */
@Composable
fun SearchField(query: String, onQueryChange: (String) -> Unit, visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text(stringResource(R.string.search_label)) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}
