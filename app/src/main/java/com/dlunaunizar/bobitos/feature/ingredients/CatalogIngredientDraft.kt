package com.dlunaunizar.bobitos.feature.ingredients

import android.os.Parcelable
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import kotlinx.parcelize.Parcelize

// Borrador del editor de ingrediente del catálogo. Parcelable para que sobreviva a una rotación.
@Parcelize
internal data class CatalogIngredientDraft(val name: String, val category: String, val unit: String) : Parcelable {
    companion object {
        /** [initialName] solo se usa al crear (p. ej. el nombre sugerido tras escanear un producto). */
        fun of(ingredient: CatalogIngredient?, initialName: String) = CatalogIngredientDraft(
            name = ingredient?.name ?: initialName,
            category = ingredient?.category.orEmpty(),
            unit = ingredient?.defaultUnit.orEmpty(),
        )
    }
}
