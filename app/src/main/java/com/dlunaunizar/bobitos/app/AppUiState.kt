package com.dlunaunizar.bobitos.app

import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.SpaceSummary

data class AppUiState(
    val spaces: UiState<List<SpaceSummary>> = UiState.Loading,
    val selectedSpace: SpaceSummary? = null,
)

