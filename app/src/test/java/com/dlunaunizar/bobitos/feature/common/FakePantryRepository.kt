package com.dlunaunizar.bobitos.feature.common

import com.dlunaunizar.bobitos.core.model.PantryEntry
import com.dlunaunizar.bobitos.core.model.PantryItem
import com.dlunaunizar.bobitos.core.model.pantryId
import com.dlunaunizar.bobitos.data.repository.PantryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/** Doble de [PantryRepository]: guarda la despensa en [itemsState] y registra altas y bajas. */
internal class FakePantryRepository : PantryRepository {
    val itemsState = MutableStateFlow<List<PantryItem>>(emptyList())
    var itemsFailure: Throwable? = null

    // Lo que lee currentIds (el servidor); con null, los ids de itemsState.
    var serverIds: Set<String>? = null
    val added = mutableListOf<List<PantryEntry>>()
    val removed = mutableListOf<List<String>>()
    var addFailure: Throwable? = null
    var removeFailure: Throwable? = null
    var observedSpaceId: String? = null

    override fun items(spaceId: String): Flow<List<PantryItem>> {
        observedSpaceId = spaceId
        return itemsFailure?.let { failure -> flow { throw failure } } ?: itemsState
    }

    override suspend fun currentIds(spaceId: String): Set<String> {
        itemsFailure?.let { throw it }
        return serverIds ?: itemsState.value.mapTo(HashSet(), PantryItem::id)
    }

    // Como el real: lo que ya está no se toca.
    override suspend fun add(spaceId: String, entries: List<PantryEntry>) {
        addFailure?.let { throw it }
        added.add(entries)
        val present = itemsState.value.mapTo(HashSet(), PantryItem::id)
        val new = entries.filter { present.add(pantryId(it.name)) }
            .map { PantryItem(pantryId(it.name), it.name, it.category, null) }
        itemsState.value = itemsState.value + new
    }

    override suspend fun remove(spaceId: String, ids: List<String>) {
        removeFailure?.let { throw it }
        removed.add(ids)
        itemsState.value = itemsState.value.filterNot { it.id in ids }
    }
}
