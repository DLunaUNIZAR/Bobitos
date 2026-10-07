package com.dlunaunizar.bobitos.feature.ingredients

import androidx.compose.runtime.saveable.listSaver
import com.dlunaunizar.bobitos.core.model.CatalogIngredient

// Borrador del editor de ingrediente del catálogo: tres textos, para que sobreviva a una rotación.
internal data class CatalogIngredientDraft(val name: String, val category: String, val unit: String) {
    companion object {
        /** [initialName] solo se usa al crear (p. ej. el nombre sugerido tras escanear un producto). */
        fun of(ingredient: CatalogIngredient?, initialName: String) = CatalogIngredientDraft(
            name = ingredient?.name ?: initialName,
            category = ingredient?.category.orEmpty(),
            unit = ingredient?.defaultUnit.orEmpty(),
        )
    }
}

internal val CatalogIngredientDraftSaver = listSaver<CatalogIngredientDraft, Any?>(
    save = { listOf(it.name, it.category, it.unit) },
    restore = {
        CatalogIngredientDraft(name = it[0] as String, category = it[1] as String, unit = it[2] as String)
    },
)
