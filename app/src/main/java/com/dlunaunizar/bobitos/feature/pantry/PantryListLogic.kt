package com.dlunaunizar.bobitos.feature.pantry

import com.dlunaunizar.bobitos.core.common.prepareQuery
import com.dlunaunizar.bobitos.core.model.INGREDIENT_CATEGORIES
import com.dlunaunizar.bobitos.core.model.PantryItem
import java.text.Collator
import java.util.Locale

// Compartido entre llamadas: RuleBasedCollator.compare está sincronizado.
private val PANTRY_COLLATOR: Collator =
    Collator.getInstance(Locale.forLanguageTag("es-ES")).apply { strength = Collator.PRIMARY }

/** Agrupa por las categorías del catálogo, en su orden; lo demás (sin categoría o con otra) va a «Otros». */
internal fun <T> groupByIngredientCategory(
    items: List<T>,
    category: (T) -> String?,
    name: (T) -> String,
): List<PantryGroup<T>> {
    val byCategory = items.groupBy { item -> category(item)?.takeIf { it in INGREDIENT_CATEGORIES } }
    val byName = Comparator<T> { a, b -> PANTRY_COLLATOR.compare(name(a), name(b)) }
    return (INGREDIENT_CATEGORIES + null).mapNotNull { key ->
        byCategory[key]?.let { PantryGroup(key, it.sortedWith(byName)) }
    }
}

/** La lista de la Despensa: lo que coincide con [query] (por nombre o categoría), agrupado. */
internal fun pantrySections(items: List<PantryItem>, query: String): List<PantryGroup<PantryItem>> {
    val search = prepareQuery(query)
    return groupByIngredientCategory(
        items.filter { search.matches(it.name, it.category) },
        PantryItem::category,
        PantryItem::name,
    )
}
