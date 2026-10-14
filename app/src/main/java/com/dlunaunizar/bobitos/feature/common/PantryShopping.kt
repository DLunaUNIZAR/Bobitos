package com.dlunaunizar.bobitos.feature.common

import com.dlunaunizar.bobitos.core.model.PantryEntry
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.findShoppingMatch
import com.dlunaunizar.bobitos.core.model.shoppingMatchKey
import com.dlunaunizar.bobitos.data.repository.ShoppingWrite
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

// Puente entre la Compra y la Despensa (spec 2026-10-13-despensa-design, §2 y §3).

/**
 * La entrada de despensa de [name] si es un ingrediente reconocido (común o personal, por clave tolerante), con su
 * nombre canónico y su categoría; `null` si no lo es (solo los ingredientes entran solos en la despensa).
 */
internal fun pantryEntryFor(name: String, data: IngredientShoppingData): PantryEntry? =
    data.findIngredient(name)?.let { PantryEntry(it.name, it.category) }

/** Lo que hay que escribir en la Compra para apuntar lo que se ha acabado, y lo que ya estaba pendiente (no se escribe). */
internal data class PantryRestock(val writes: List<ShoppingWrite>, val alreadyListed: List<String>)

/**
 * «A la compra» de [names] contra la lista [items] (léase del servidor): pendiente → no se escribe; comprado → se vuelve
 * a apuntar conservando cantidad, notas, tienda y marca; si no está → se añade sin cantidad, con la marca y la tienda
 * por defecto (ver `defaultSelection`). Los nombres en blanco y los repetidos por clave se ignoran.
 */
internal fun pantryRestock(
    names: List<String>,
    items: List<ShoppingItem>,
    data: IngredientShoppingData,
): PantryRestock {
    val writes = mutableListOf<ShoppingWrite>()
    val alreadyListed = mutableListOf<String>()
    names.map(String::trim).filter(String::isNotEmpty).distinctBy(::shoppingMatchKey).forEach { name ->
        val match = findShoppingMatch(items, name)
        when {
            match == null -> writes += newShoppingWrite(name, data)
            match.purchased -> writes += ShoppingWrite.Change(
                seen = match,
                quantity = match.quantity,
                notes = match.notes,
                supermarket = match.supermarket,
                brand = match.brand,
                relist = true,
            )
            else -> alreadyListed += match.name
        }
    }
    return PantryRestock(writes, alreadyListed)
}

private fun newShoppingWrite(name: String, data: IngredientShoppingData): ShoppingWrite.Add {
    val context = data.contextFor(name)
        ?: return ShoppingWrite.Add(name = name, quantity = null, notes = null, supermarket = null, brand = null)
    val selection = context.defaultSelection()
    return ShoppingWrite.Add(
        name = name,
        quantity = null,
        notes = null,
        supermarket = selection.store,
        brand = context.brandName(selection),
    )
}

// Sin catálogo a tiempo, datos vacíos.
private const val CATALOG_TIMEOUT_MILLIS = 5_000L

internal suspend fun IngredientShoppingSource.firstOrEmpty(): IngredientShoppingData =
    withTimeoutOrNull(CATALOG_TIMEOUT_MILLIS) { data().first() } ?: IngredientShoppingData()
