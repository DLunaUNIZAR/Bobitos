package com.dlunaunizar.bobitos.feature.ingredients

import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientBrand
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
    // Comunes y personales mezclados.
    val ingredients: UiState<List<CatalogIngredient>> = UiState.Loading,
    val brands: List<IngredientBrand> = emptyList(),
    // Tiendas por ingrediente del usuario activo (ingredientId → tiendas).
    val prefs: Map<String, IngredientPref> = emptyMap(),
    val catalogError: IngredientUiMessage? = null,
    // La cuenta activa puede crear ingredientes comunes.
    val isAdmin: Boolean = false,
    val isSaving: Boolean = false,
    val editorSave: EditorSaveStatus = EditorSaveStatus.IDLE,
    // Consulta a Open Food Facts en curso tras escanear desde la lista.
    val isLookingUp: Boolean = false,
    // Producto escaneado pendiente de abrir en el editor de «nuevo ingrediente».
    val scannedProduct: ScannedProduct? = null,
    val error: IngredientUiMessage? = null,
    val notice: IngredientUiMessage? = null,
    // Evento: ficha existente a la que navegar tras un alta duplicada, y su nombre para el aviso.
    val openIngredientId: String? = null,
    val existingName: String? = null,
)
