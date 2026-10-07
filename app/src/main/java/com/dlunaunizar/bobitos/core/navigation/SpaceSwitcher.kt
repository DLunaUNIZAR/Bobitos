package com.dlunaunizar.bobitos.core.navigation

import com.dlunaunizar.bobitos.core.model.SpaceSummary

/** Lo que la barra superior necesita para cambiar de espacio sin salir de la pantalla. */
internal data class SpaceSwitcher(
    val spaces: List<SpaceSummary>,
    val selectedSpaceId: String?,
    val pickerOpen: Boolean,
    val onPickerOpenChange: (Boolean) -> Unit,
    val onSelect: (SpaceSummary) -> Unit,
)
