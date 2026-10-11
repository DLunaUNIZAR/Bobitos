package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.Supermarket
import kotlinx.coroutines.flow.Flow

/**
 * Preferencias de ingredientes del usuario activo (tiendas donde compra cada ingrediente),
 * guardadas en `ingredientPrefs/{uid}`. Se usan para prerrellenar el supermercado en la lista de la compra al volcar los
 * ingredientes de una comida. Cada usuario solo ve y escribe las suyas.
 */
interface IngredientPrefsRepository {
    /** Mapa `ingredientId (slug)` → preferencia del usuario activo. Vacío si no hay sesión. */
    fun prefs(): Flow<Map<String, IngredientPref>>

    /** Fija las tiendas del ingrediente (normalizadas); con la lista vacía borra la entrada. */
    suspend fun setStores(ingredientId: String, stores: List<Supermarket>)
}

enum class IngredientPrefFailure {
    NotAuthenticated,
    EmailNotVerified,
    Network,
    PermissionDenied,
    Unknown,
}

class IngredientPrefsException(val failure: IngredientPrefFailure, cause: Throwable? = null) : Exception(cause)
