package com.dlunaunizar.bobitos.app

import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import com.dlunaunizar.bobitos.core.model.SyncStatus

data class AppUiState(
    val authUser: UiState<AuthUser?> = UiState.Loading,
    val spaces: UiState<List<SpaceSummary>> = UiState.Loading,
    val selectedSpace: SpaceSummary? = null,
    val syncStatus: SyncStatus = SyncStatus.OFFLINE,
)
