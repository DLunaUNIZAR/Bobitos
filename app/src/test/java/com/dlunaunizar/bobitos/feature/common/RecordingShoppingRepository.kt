package com.dlunaunizar.bobitos.feature.common

import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.data.repository.ShoppingRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingWrite
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientBrandRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientPrefsRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakePersonalIngredientRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/** Doble de [ShoppingRepository] que registra las altas, ediciones y compras en listas. */
internal class RecordingShoppingRepository : ShoppingRepository {
    val addedNames = mutableListOf<String>()
    val addedNotes = mutableListOf<String?>()
    val addedSupermarkets = mutableListOf<Supermarket?>()
    val addedBrands = mutableListOf<String?>()
    val addedQuantities = mutableListOf<String?>()
    val relistedIds = mutableListOf<String>()
    val relistedQuantities = mutableListOf<String?>()
    val relistedSupermarkets = mutableListOf<Supermarket?>()
    val relistedBrands = mutableListOf<String?>()
    val itemsState = MutableStateFlow<List<ShoppingItem>>(emptyList())
    val purchasedIds = mutableListOf<String>()
    val updatedIds = mutableListOf<String>()
    val updatedQuantities = mutableListOf<String?>()
    val updatedSupermarkets = mutableListOf<Supermarket?>()
    val updatedBrands = mutableListOf<String?>()
    val appliedBatches = mutableListOf<List<ShoppingWrite>>()
    var applyFailure: Throwable? = null
    var itemsFailure: Throwable? = null

    // Lo que lee currentItems (el servidor); con null devuelve itemsState. Distinto de itemsState simula una caché vieja.
    var serverItems: List<ShoppingItem>? = null

    override fun items(spaceId: String): Flow<List<ShoppingItem>> =
        itemsFailure?.let { failure -> flow { throw failure } } ?: itemsState

    // Si hay puerta, currentItems espera a que se complete (simula una lectura lenta); cuenta las lecturas.
    var currentItemsGate: CompletableDeferred<Unit>? = null
    var currentItemsCalls = 0

    override suspend fun currentItems(spaceId: String): List<ShoppingItem> {
        currentItemsCalls++
        currentItemsGate?.await()
        itemsFailure?.let { throw it }
        return serverItems ?: itemsState.value
    }

    override suspend fun addItem(
        spaceId: String,
        name: String,
        quantity: String?,
        notes: String?,
        supermarket: Supermarket?,
        brand: String?,
    ) {
        addedNames += name
        addedQuantities += quantity
        addedNotes += notes
        addedSupermarkets += supermarket
        addedBrands += brand
    }

    override suspend fun updateItem(
        spaceId: String,
        itemId: String,
        name: String,
        quantity: String?,
        notes: String?,
        supermarket: Supermarket?,
        brand: String?,
    ) {
        updatedIds += itemId
        updatedQuantities += quantity
        updatedSupermarkets += supermarket
        updatedBrands += brand
    }

    // Cada lote queda en appliedBatches y, además, cada escritura en las listas de altas, ediciones y
    // reapuntados, para que los tests comprueben lo escrito sin desmontar el lote.
    override suspend fun applyWrites(spaceId: String, writes: List<ShoppingWrite>) {
        applyFailure?.let { throw it }
        appliedBatches += writes
        writes.forEach { write ->
            when (write) {
                is ShoppingWrite.Add -> {
                    addedNames += write.name
                    addedQuantities += write.quantity
                    addedNotes += write.notes
                    addedSupermarkets += write.supermarket
                    addedBrands += write.brand
                }
                is ShoppingWrite.Change -> if (write.relist) {
                    relistedIds += write.seen.id
                    relistedQuantities += write.quantity
                    relistedSupermarkets += write.supermarket
                    relistedBrands += write.brand
                } else {
                    updatedIds += write.seen.id
                    updatedQuantities += write.quantity
                    updatedSupermarkets += write.supermarket
                    updatedBrands += write.brand
                }
            }
        }
    }

    override suspend fun setPurchased(spaceId: String, itemId: String, purchased: Boolean) {
        if (purchased) purchasedIds += itemId
    }

    override suspend fun deleteItem(spaceId: String, itemId: String) = Unit
    override suspend fun clearPurchased(spaceId: String): Int = 0
}

/** [IngredientShoppingSource] montado con los dobles de ingredientes. */
internal fun ingredientShoppingSource(
    ingredients: FakeIngredientRepository,
    brands: FakeIngredientBrandRepository,
    prefs: FakeIngredientPrefsRepository,
    personal: FakePersonalIngredientRepository = FakePersonalIngredientRepository(),
) = IngredientShoppingSource(ingredients, personal, brands, prefs)
