package com.dlunaunizar.bobitos.feature.common

import com.dlunaunizar.bobitos.core.model.PantryEntry
import com.dlunaunizar.bobitos.core.model.PantryItem
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.findShoppingMatch
import com.dlunaunizar.bobitos.core.model.shoppingMatchKey
import com.dlunaunizar.bobitos.data.repository.PantryRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingWrite
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

/**
 * «A la compra» de [names] en la Compra de [spaceId]: lee la lista del servidor (con una caché vieja se duplicaría el
 * artículo) y escribe lo que haga falta ([pantryRestock]) en una sola escritura ([applySeenWrites]). Devuelve el plan:
 * lo que ya estaba pendiente va en `alreadyListed`. Lo usan la Despensa («Se acabó») y Comidas («Has cocinado …»).
 */
internal suspend fun restockFromPantry(
    spaceId: String,
    names: List<String>,
    shoppingRepository: ShoppingRepository,
    data: IngredientShoppingData,
): PantryRestock {
    val plan = pantryRestock(names, shoppingRepository.currentItems(spaceId), data)
    if (plan.writes.isNotEmpty()) shoppingRepository.applySeenWrites(spaceId, plan.writes)
    return plan
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
internal const val CATALOG_TIMEOUT_MILLIS = 5_000L

internal suspend fun IngredientShoppingSource.firstOrEmpty(): IngredientShoppingData =
    withTimeoutOrNull(CATALOG_TIMEOUT_MILLIS) { data().first() } ?: IngredientShoppingData()

/** Los ids de la despensa de [spaceId] leídos del servidor, o `null` si no se puede leer (se sigue sin ella). */
internal suspend fun PantryRepository.currentIdsOrNull(spaceId: String): Set<String>? =
    runCatching { currentIds(spaceId) }
        .onFailure { if (it is CancellationException) throw it }
        .getOrNull()

/**
 * Lee a la vez, del servidor, la lista de la compra ([readItems]) y la despensa de [spaceId] ([currentIdsOrNull]): un
 * viaje de ida y vuelta de espera en lugar de dos. Si [readItems] falla, falla igual y deja de esperar a la despensa.
 */
internal suspend fun <T> PantryRepository.withCurrentIds(
    spaceId: String,
    readItems: suspend () -> T,
): Pair<T, Set<String>?> = coroutineScope {
    val pantryIds = async { currentIdsOrNull(spaceId) }
    readItems() to pantryIds.await()
}

/** Los ids de la despensa de [spaceId], cada vez que cambia. */
internal fun PantryRepository.idsFlow(spaceId: String): Flow<Set<String>> =
    items(spaceId).map { items -> items.mapTo(HashSet(), PantryItem::id) }
