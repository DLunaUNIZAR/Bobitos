package com.dlunaunizar.bobitos.feature.ingredients

import com.dlunaunizar.bobitos.core.common.SaveState
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.Nutrition

/** Producto escaneado en la lista, pendiente de decidir de qué ingrediente es la marca. */
data class ScanPick(val barcode: String, val productName: String, val brandName: String, val nutrition: Nutrition?)

/** Marca ya conocida a la que navegar tras escanear su código. */
data class OpenBrand(val ingredientId: String, val brandId: String)

data class IngredientsUiState(
    // Comunes y personales mezclados.
    val ingredients: UiState<List<CatalogIngredient>> = UiState.Loading,
    val brands: List<IngredientBrand> = emptyList(),
    // Tiendas por ingrediente del usuario activo (ingredientId → tiendas).
    val prefs: Map<String, IngredientPref> = emptyMap(),
    val catalogError: IngredientUiMessage? = null,
    // La cuenta activa puede crear ingredientes comunes.
    val isAdmin: Boolean = false,
    // Guardado, editor, error, aviso y alta recién hecha (la pantalla la avisa con «Deshacer» y la consume).
    val save: SaveState<IngredientUiMessage, IngredientUiMessage> = SaveState(),
    // Consulta a Open Food Facts en curso tras escanear desde la lista.
    val isLookingUp: Boolean = false,
    // Producto escaneado pendiente de elegir ingrediente, y el ingrediente elegido (editor de marca).
    val scanPick: ScanPick? = null,
    val scanBrandTarget: String? = null,
    // Evento: marca conocida a la que navegar.
    val openBrand: OpenBrand? = null,
    // Evento: ficha existente a la que navegar tras un alta duplicada, y su nombre para el aviso.
    val openIngredientId: String? = null,
    val existingName: String? = null,
)
