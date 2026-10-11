package com.dlunaunizar.bobitos.feature.common

import com.dlunaunizar.bobitos.data.repository.IngredientBrandRepository
import com.dlunaunizar.bobitos.data.repository.IngredientPrefsRepository
import com.dlunaunizar.bobitos.data.repository.IngredientRepository
import com.dlunaunizar.bobitos.data.repository.PersonalIngredientRepository
import com.dlunaunizar.bobitos.feature.ingredients.mergeIngredients
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * Fuente compartida de los datos para reconocer ingredientes y proponer marca y tienda al añadir a la
 * compra (spec 2026-10-11-catalogo-ingredientes-design, §5). Un fallo de una fuente la deja vacía sin
 * quitar los datos de las demás.
 */
class IngredientShoppingSource @Inject constructor(
    private val ingredientRepository: IngredientRepository,
    private val personalIngredients: PersonalIngredientRepository,
    private val brandRepository: IngredientBrandRepository,
    private val prefsRepository: IngredientPrefsRepository,
) {
    fun data(): Flow<IngredientShoppingData> = combine(
        ingredientRepository.catalog().catch { emit(emptyList()) },
        personalIngredients.ingredients().catch { emit(emptyList()) },
        brandRepository.brands().catch { emit(emptyList()) },
        prefsRepository.prefs().catch { emit(emptyMap()) },
    ) { common, personal, brands, prefs ->
        IngredientShoppingData(mergeIngredients(common, personal), brands, prefs)
    }
}
