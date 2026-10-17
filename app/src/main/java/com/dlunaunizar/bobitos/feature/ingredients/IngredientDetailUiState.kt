package com.dlunaunizar.bobitos.feature.ingredients

import com.dlunaunizar.bobitos.core.common.CreatedItem
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.Nutrition
import com.dlunaunizar.bobitos.core.model.Supermarket

/** Borrador de marca prerrellenado tras escanear un código (nombre/nutrición de Open Food Facts). */
data class ScannedBrand(val name: String, val barcode: String, val nutrition: Nutrition?)

/** Marca de otro ingrediente que ya tiene el código escaneado. */
data class BarcodeConflict(val brandName: String, val ingredientName: String)

data class IngredientDetailUiState(
    val ingredientId: String = "",
    val ingredient: CatalogIngredient? = null,
    // El catálogo ya emitió (para distinguir «cargando» de «no existe»).
    val loaded: Boolean = false,
    val brands: List<IngredientBrand> = emptyList(),
    // Tiendas del ingrediente en orden de preferencia (de las preferencias del usuario).
    val stores: List<Supermarket> = emptyList(),
    val isAdmin: Boolean = false,
    val isSaving: Boolean = false,
    val editorSave: EditorSaveStatus = EditorSaveStatus.IDLE,
    // Consulta a Open Food Facts en curso tras escanear.
    val isLookingUp: Boolean = false,
    // Borrador pendiente de abrir en el editor de marca tras un escaneo.
    val scannedBrand: ScannedBrand? = null,
    // Evento: marca de este ingrediente cuyo código se acaba de escanear, para abrir su editor.
    val openBrandId: String? = null,
    val barcodeConflict: BarcodeConflict? = null,
    val error: IngredientUiMessage? = null,
    val notice: IngredientUiMessage? = null,
    // Alta recién hecha desde la hoja: la pantalla la avisa con «Deshacer» y la consume.
    val created: CreatedItem? = null,
    // El ingrediente se borró: la pantalla debe cerrarse.
    val finished: Boolean = false,
) {
    val canEditIngredient: Boolean
        get() = ingredient != null && (ingredient.personal || isAdmin)
}
