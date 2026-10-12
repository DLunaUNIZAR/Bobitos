package com.dlunaunizar.bobitos.feature.common

import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.data.repository.ShoppingRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientBrandRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientPrefsRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakePersonalIngredientRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

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

    override fun items(spaceId: String): Flow<List<ShoppingItem>> = itemsState
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

    override suspend fun relistItem(
        spaceId: String,
        itemId: String,
        name: String,
        quantity: String?,
        notes: String?,
        supermarket: Supermarket?,
        brand: String?,
    ) {
        relistedIds += itemId
        relistedQuantities += quantity
        relistedSupermarkets += supermarket
        relistedBrands += brand
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
