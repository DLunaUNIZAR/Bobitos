package com.dlunaunizar.bobitos.feature.pantry

import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.MAX_PANTRY_NAME_LENGTH
import com.dlunaunizar.bobitos.core.model.PantryEntry
import com.dlunaunizar.bobitos.core.model.findByKey
import com.dlunaunizar.bobitos.core.model.pantryId
import com.dlunaunizar.bobitos.feature.ingredients.matching

/** Una fila del selector: lo que se apuntaría, su id y si ya está en la despensa (sale marcada y deshabilitada). */
internal data class PantryPickRow(val entry: PantryEntry, val id: String, val inPantry: Boolean)

/** El selector: los ingredientes agrupados y, si lo escrito no es ninguno, la fila para apuntarlo tal cual. */
internal data class PantryPickerContent(val groups: List<PantryGroup<PantryPickRow>>, val freeText: PantryPickRow?)

internal fun pantryPickerContent(
    ingredients: List<CatalogIngredient>,
    pantryIds: Set<String>,
    query: String,
): PantryPickerContent {
    val rows = ingredients.matching(query)
        .map { PantryPickRow(PantryEntry(it.name, it.category), pantryId(it.name), pantryId(it.name) in pantryIds) }
        .filter { it.id.isNotEmpty() }
        .distinctBy(PantryPickRow::id)
    return PantryPickerContent(
        groups = groupByIngredientCategory(rows, { it.entry.category }, { it.entry.name }),
        freeText = freeTextRow(ingredients, pantryIds, query),
    )
}

// Lo escrito, si no es un ingrediente (por clave tolerante) ni algo que ya está, y se puede apuntar.
private fun freeTextRow(ingredients: List<CatalogIngredient>, pantryIds: Set<String>, query: String): PantryPickRow? {
    val text = query.trim()
    val id = pantryId(text)
    val known = id.isEmpty() || id in pantryIds || findByKey(ingredients, text) != null
    if (known || text.length > MAX_PANTRY_NAME_LENGTH) return null
    return PantryPickRow(PantryEntry(text, null), id, false)
}
