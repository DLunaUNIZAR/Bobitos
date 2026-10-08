package com.dlunaunizar.bobitos.feature.recipes

import android.os.Parcelable
import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import com.dlunaunizar.bobitos.data.recipeimport.ImportedRecipe
import kotlinx.parcelize.Parcelize

/** Fila editable de ingrediente: tres textos. Inmutable; el editor sustituye la fila al cambiarla. */
@Parcelize
internal data class IngredientRow(val name: String = "", val quantity: String = "", val unit: String = "") : Parcelable

// Borrador del editor de receta. Parcelable para que sobreviva a una rotación (también el borrador de
// una receta importada, que guarda el anfitrión). `sourceUrl` es el enlace de una receta importada.
@Parcelize
internal data class RecipeDraft(
    val title: String,
    val description: String,
    val category: String,
    val global: Boolean,
    val ingredients: List<IngredientRow>,
    val sourceUrl: String?,
) : Parcelable {
    companion object {
        /** Valores iniciales: los de la receta existente o, al importar, los del borrador de la web. */
        fun of(recipe: Recipe?, imported: ImportedRecipe?) = RecipeDraft(
            title = recipe?.title ?: imported?.title.orEmpty(),
            description = recipe?.description ?: imported?.description.orEmpty(),
            category = recipe?.category ?: imported?.category.orEmpty(),
            global = recipe?.visibility == RecipeVisibility.GLOBAL,
            ingredients = (recipe?.ingredients ?: imported?.ingredients).orEmpty()
                .map { IngredientRow(it.name, it.quantity.orEmpty(), it.unit.orEmpty()) },
            sourceUrl = imported?.sourceUrl,
        )
    }
}

// Descarta filas sin nombre y normaliza cantidad/unidad vacías a null.
internal fun List<IngredientRow>.toIngredients(): List<Ingredient> = mapNotNull { row ->
    row.name.trim().takeIf(String::isNotEmpty)?.let { name ->
        Ingredient(name, row.quantity.trim().ifBlank { null }, row.unit.trim().ifBlank { null })
    }
}
