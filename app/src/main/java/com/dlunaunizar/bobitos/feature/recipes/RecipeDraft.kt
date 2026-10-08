package com.dlunaunizar.bobitos.feature.recipes

import androidx.compose.runtime.saveable.listSaver
import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import com.dlunaunizar.bobitos.data.recipeimport.ImportedRecipe

/** Fila editable de ingrediente: tres textos. Inmutable; el editor sustituye la fila al cambiarla. */
internal data class IngredientRow(val name: String = "", val quantity: String = "", val unit: String = "")

// Borrador del editor de receta: solo tipos que caben en un Bundle (textos, un booleano y una lista de
// filas), para que sobreviva a una rotación. `sourceUrl` es el enlace de una receta importada.
internal data class RecipeDraft(
    val title: String,
    val description: String,
    val category: String,
    val global: Boolean,
    val ingredients: List<IngredientRow>,
    val sourceUrl: String?,
) {
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

/** Convierte el borrador en una lista de textos guardable (cabe en un Bundle). */
internal fun RecipeDraft.toSaved(): ArrayList<Any?> = arrayListOf(
    title,
    description,
    category,
    global,
    ArrayList(ingredients.map { arrayListOf(it.name, it.quantity, it.unit) }),
    sourceUrl,
)

internal fun recipeDraftFromSaved(saved: List<Any?>): RecipeDraft {
    @Suppress("UNCHECKED_CAST")
    val rows = (saved[4] as List<List<String>>).map { IngredientRow(it[0], it[1], it[2]) }
    return RecipeDraft(
        title = saved[0] as String,
        description = saved[1] as String,
        category = saved[2] as String,
        global = saved[3] as Boolean,
        ingredients = rows,
        sourceUrl = saved[5] as String?,
    )
}

internal val RecipeDraftSaver = listSaver<RecipeDraft, Any?>(
    save = { it.toSaved() },
    restore = { recipeDraftFromSaved(it) },
)
