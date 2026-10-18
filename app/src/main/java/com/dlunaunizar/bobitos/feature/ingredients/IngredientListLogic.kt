package com.dlunaunizar.bobitos.feature.ingredients

import com.dlunaunizar.bobitos.core.common.prepareQuery
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.data.repository.sortedForBrands
import com.dlunaunizar.bobitos.data.repository.sortedForIngredientCatalog
import com.dlunaunizar.bobitos.feature.common.CategoryCollator
import com.dlunaunizar.bobitos.feature.common.categoryKey

/** Comunes y personales en una lista: el personal cuyo id ya es común se oculta. */
internal fun mergeIngredients(
    common: List<CatalogIngredient>,
    personal: List<CatalogIngredient>,
): List<CatalogIngredient> {
    val commonIds = common.mapTo(HashSet(), CatalogIngredient::id)
    return (common + personal.filterNot { it.id in commonIds }).sortedForIngredientCatalog()
}

/** Línea de marca y tienda bajo el nombre del ingrediente en la lista. */
internal sealed interface IngredientRowLine {
    data class Favorite(val brand: String, val store: Supermarket?) : IngredientRowLine
    data class Brands(val count: Int) : IngredientRowLine
    data class Store(val store: Supermarket) : IngredientRowLine
    data object None : IngredientRowLine
}

/** [brands] ya viene filtrada por ingrediente. */
internal fun ingredientRowLine(brands: List<IngredientBrand>, pref: IngredientPref?): IngredientRowLine {
    val favorite = brands.sortedForBrands().firstOrNull { it.favorite }
    val prefStore = pref?.stores?.firstOrNull()
    return when {
        favorite != null -> IngredientRowLine.Favorite(favorite.name, favorite.stores.firstOrNull() ?: prefStore)
        brands.isNotEmpty() -> IngredientRowLine.Brands(brands.size)
        prefStore != null -> IngredientRowLine.Store(prefStore)
        else -> IngredientRowLine.None
    }
}

internal fun List<CatalogIngredient>.matching(query: String): List<CatalogIngredient> {
    val search = prepareQuery(query)
    return filter { search.matches(it.name, it.category) }
}

/** La línea de cada ingrediente con marcas o tiendas; el resto (sin entrada) es [IngredientRowLine.None]. */
internal fun ingredientRowLines(
    brands: List<IngredientBrand>,
    prefs: Map<String, IngredientPref>,
): Map<String, IngredientRowLine> {
    val brandsByIngredient = brands.groupBy { it.ingredientId }
    return (brandsByIngredient.keys + prefs.keys).associateWith {
        ingredientRowLine(brandsByIngredient[it].orEmpty(), prefs[it])
    }
}

/** Categorías del catálogo sin repetir (las que solo difieren en mayúsculas se juntan) ni vacías, ordenadas. */
internal fun List<CatalogIngredient>.categories(): List<String> = mapNotNull {
    it.category?.trim()?.takeIf(String::isNotEmpty)
}
    .distinctBy { it.categoryKey() }
    .sortedWith(CategoryCollator)

/** Con [category] null devuelve todo; si no, los de esa categoría sin mayúsculas ni espacios en los extremos. */
internal fun List<CatalogIngredient>.inCategory(category: String?): List<CatalogIngredient> {
    if (category == null) return this
    val key = category.categoryKey()
    return filter { it.category?.categoryKey() == key }
}
