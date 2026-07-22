package com.dlunaunizar.bobitos.feature.ingredients

import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.Nutrition

/**
 * Producto escaneado desde la lista, pendiente de convertir en ingrediente + marca. `suggestedName`
 * es editable por el usuario (nombre genérico del ingrediente); `brandName` es la marca concreta.
 */
data class ScannedProduct(
    val suggestedName: String,
    val brandName: String,
    val barcode: String,
    val nutrition: Nutrition?,
)

data class IngredientsUiState(
    val query: String = "",
    val catalog: UiState<List<CatalogIngredient>> = UiState.Loading,
    // Preferencias del usuario activo (ingredientId → super/marca por defecto).
    val prefs: Map<String, IngredientPref> = emptyMap(),
    // Cuenta activa: puede curar (editar/borrar) fichas ajenas.
    val isAdmin: Boolean = false,
    val currentUid: String? = null,
    val isSaving: Boolean = false,
    // Consulta a Open Food Facts en curso tras escanear desde la lista.
    val isLookingUp: Boolean = false,
    // Producto escaneado pendiente de abrir en el editor de «nuevo ingrediente».
    val scannedProduct: ScannedProduct? = null,
    val error: IngredientUiMessage? = null,
    val notice: IngredientUiMessage? = null,
) {
    fun canEdit(ingredient: CatalogIngredient): Boolean = isAdmin || ingredient.ownerUid == currentUid
}
