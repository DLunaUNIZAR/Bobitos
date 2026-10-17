package com.dlunaunizar.bobitos.feature.ingredients

import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.core.model.slug
import com.dlunaunizar.bobitos.data.openfoodfacts.OffException
import com.dlunaunizar.bobitos.data.openfoodfacts.OffProduct
import com.dlunaunizar.bobitos.data.openfoodfacts.OpenFoodFactsClient
import com.dlunaunizar.bobitos.data.repository.BrandInput
import com.dlunaunizar.bobitos.data.repository.DeletedIngredient
import com.dlunaunizar.bobitos.data.repository.IngredientBrandRepository
import com.dlunaunizar.bobitos.data.repository.IngredientPrefsRepository
import com.dlunaunizar.bobitos.data.repository.IngredientRepository
import com.dlunaunizar.bobitos.data.repository.PersonalIngredientRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

// Fakes compartidos por los tests de Ingredientes, Detalle y Compra; cada test usa solo las puertas y
// contadores que necesita.

// Nunca responde; como los repositorios reales, convierte la cancelación en otra excepción.
private suspend fun hangUntilCancelled() {
    try {
        awaitCancellation()
    } catch (_: CancellationException) {
        error("cancelled")
    }
}

internal class FakeIngredientRepository : IngredientRepository {
    val catalogState = MutableStateFlow<List<CatalogIngredient>>(emptyList())
    var admin = false
    var uid: String? = "me"
    var createCount = 0
    var lastCreatedName: String? = null
    var createGate: CompletableDeferred<Unit>? = null
    var updateGate: CompletableDeferred<Unit>? = null
    var deletedId: String? = null
    var updatedId: String? = null
    var hang = false
    var failCatalog = false
    var catalogOverride: Flow<List<CatalogIngredient>>? = null

    override fun catalog(): Flow<List<CatalogIngredient>> = catalogOverride
        ?: if (failCatalog) flow { error("offline") } else catalogState

    override fun isCurrentUserCatalogAdmin(): Boolean = admin
    override fun currentUserId(): String? = uid
    override suspend fun createIngredient(name: String, category: String?, defaultUnit: String?) {
        if (hang) hangUntilCancelled()
        createGate?.await()
        createCount++
        lastCreatedName = name
    }

    override suspend fun updateIngredient(id: String, name: String, category: String?, defaultUnit: String?) {
        updatedId = id
        if (hang) hangUntilCancelled()
        updateGate?.await()
    }

    override suspend fun deleteIngredient(id: String) {
        deletedId = id
    }
}

internal class FakePersonalIngredientRepository : PersonalIngredientRepository {
    val state = MutableStateFlow<List<CatalogIngredient>>(emptyList())
    var createCount = 0
    var lastCreatedName: String? = null
    var createGate: CompletableDeferred<Unit>? = null
    var createError: Throwable? = null
    var deletedId: String? = null
    var deleteError: Throwable? = null
    var restored: DeletedIngredient? = null
    var restoreError: Throwable? = null
    var updatedId: String? = null
    var hang = false
    var personalOverride: Flow<List<CatalogIngredient>>? = null

    override fun ingredients(): Flow<List<CatalogIngredient>> = personalOverride ?: state
    override suspend fun create(name: String, category: String?, defaultUnit: String?): String {
        if (hang) hangUntilCancelled()
        createGate?.await()
        createError?.let { throw it }
        createCount++
        lastCreatedName = name
        // Como el real: lo creado aparece luego en la lista de personales.
        state.value += CatalogIngredient(id = slug(name), name = name, category = category)
        return slug(name)
    }

    override suspend fun update(id: String, name: String, category: String?, defaultUnit: String?) {
        updatedId = id
    }

    override suspend fun delete(id: String) {
        deleteError?.let { throw it }
        deletedId = id
    }

    override suspend fun restore(deleted: DeletedIngredient) {
        restoreError?.let { throw it }
        restored = deleted
    }
}

internal class FakeIngredientPrefsRepository : IngredientPrefsRepository {
    val state = MutableStateFlow<Map<String, IngredientPref>>(emptyMap())
    var lastSet: Pair<String, List<Supermarket>>? = null
    var gate: CompletableDeferred<Unit>? = null
    var error: Throwable? = null
    val calls = mutableListOf<List<Supermarket>>()

    override fun prefs(): Flow<Map<String, IngredientPref>> = state
    override suspend fun setStores(ingredientId: String, stores: List<Supermarket>) {
        calls += stores
        gate?.await()
        error?.let { throw it }
        lastSet = ingredientId to stores
        state.value = state.value + (ingredientId to IngredientPref(stores))
    }
}

internal class FakeIngredientBrandRepository : IngredientBrandRepository {
    val brandsState = MutableStateFlow<List<IngredientBrand>>(emptyList())
    var addCount = 0
    var addGate: CompletableDeferred<Unit>? = null
    var updateGate: CompletableDeferred<Unit>? = null
    var favoriteGate: CompletableDeferred<Unit>? = null
    var addError: Throwable? = null
    var lastAdd: BrandInput? = null
    var lastUpdate: BrandInput? = null
    var lastUpdateId: String? = null
    var lastFavorite: Pair<String, Boolean>? = null

    val lastAddedIngredientId: String? get() = lastAdd?.ingredientId
    val lastAddedName: String? get() = lastAdd?.name
    val lastAddedBarcode: String? get() = lastAdd?.barcode
    val lastAddedNutrition get() = lastAdd?.nutrition
    val lastAddedFavorite: Boolean get() = lastAdd?.favorite ?: false

    override fun brands(): Flow<List<IngredientBrand>> = brandsState
    override suspend fun addBrand(input: BrandInput): String {
        addGate?.await()
        addError?.let { throw it }
        addCount++
        lastAdd = input
        return "new"
    }

    override suspend fun updateBrand(brandId: String, input: BrandInput) {
        updateGate?.await()
        lastUpdateId = brandId
        lastUpdate = input
    }

    override suspend fun setFavorite(brandId: String, favorite: Boolean) {
        favoriteGate?.await()
        lastFavorite = brandId to favorite
    }

    val deleted = mutableListOf<String>()
    var deleteError: Throwable? = null
    var restoredBrand: IngredientBrand? = null
    var restoreError: Throwable? = null

    override suspend fun deleteBrand(brandId: String) {
        deleteError?.let { throw it }
        deleted += brandId
    }

    override suspend fun restoreBrand(brand: IngredientBrand) {
        restoreError?.let { throw it }
        restoredBrand = brand
    }
}

internal class FakeOffClient : OpenFoodFactsClient {
    var result: OffProduct? = null
    var failure: OffException? = null
    var calls = 0

    override suspend fun lookup(barcode: String): OffProduct? {
        calls++
        failure?.let { throw it }
        return result
    }
}
