package com.dlunaunizar.bobitos.feature.ingredients

import com.dlunaunizar.bobitos.core.common.matchesQuery
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.core.model.ingredientKey
import com.dlunaunizar.bobitos.data.repository.sortedForBrands
import com.dlunaunizar.bobitos.data.repository.sortedForIngredientCatalog

/** Comunes y personales en una lista: el personal cuyo id ya es común se oculta. */
internal fun mergeIngredients(
    common: List<CatalogIngredient>,
    personal: List<CatalogIngredient>,
): List<CatalogIngredient> {
    val commonIds = common.mapTo(HashSet(), CatalogIngredient::id)
    return (common + personal.filterNot { it.id in commonIds }).sortedForIngredientCatalog()
}

/** El primer ingrediente con la misma clave tolerante que [name]; `null` si la clave es vacía. */
internal fun findByKey(ingredients: List<CatalogIngredient>, name: String): CatalogIngredient? {
    val key = ingredientKey(name)
    if (key.isEmpty()) return null
    return ingredients.firstOrNull { ingredientKey(it.name) == key }
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

internal fun List<CatalogIngredient>.matching(query: String): List<CatalogIngredient> =
    filter { matchesQuery(query, it.name, it.category) }
