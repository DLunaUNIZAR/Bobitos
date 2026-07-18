package com.dlunaunizar.bobitos.feature.shopping

import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.ShoppingItem

data class ShoppingUiState(
    val items: UiState<List<ShoppingItem>> = UiState.Loading,
    val isSaving: Boolean = false,
    val writeStatus: ShoppingWriteStatus = ShoppingWriteStatus.IDLE,
    val error: ShoppingUiMessage? = null,
    val notice: ShoppingUiMessage? = null,
    val lastClearedCount: Int = 0,
)

enum class ShoppingWriteStatus {
    IDLE,
    SAVING,
    SAVED,
    ERROR,
}
enum class ShoppingUiMessage {
    NameRequired,
    NameTooLong,
    QuantityTooLong,
    NotesTooLong,
    BrandTooLong,
    NotAuthenticated,
    EmailNotVerified,
    SpaceNotFound,
    ItemNotFound,
    PermissionDenied,
    NetworkError,
    UnexpectedError,
    ItemAdded,
    ItemUpdated,
    ItemMarked,
    ItemUnmarked,
    ItemDeleted,
    PurchasedCleared,
}
