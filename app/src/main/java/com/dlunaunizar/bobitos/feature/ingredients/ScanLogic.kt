package com.dlunaunizar.bobitos.feature.ingredients

import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.ingredientKey

private const val MAX_SUGGESTIONS = 8

private fun digitsOf(barcode: String): String = barcode.filter(Char::isDigit)

/** La marca cuyo código coincide con [barcode] comparando solo los dígitos. */
internal fun findBrandByBarcode(brands: List<IngredientBrand>, barcode: String): IngredientBrand? {
    val digits = digitsOf(barcode)
    if (digits.isEmpty()) return null
    return brands.firstOrNull { it.barcode?.let(::digitsOf) == digits }
}

private fun keyWords(name: String): List<String> = ingredientKey(name).split(' ').filter(String::isNotEmpty)

/**
 * Ingredientes cuyas palabras clave están todas en el nombre del producto, el más específico (más palabras)
 * primero y, a igualdad, por nombre.
 */
internal fun suggestIngredients(
    ingredients: List<CatalogIngredient>,
    productName: String,
    max: Int = MAX_SUGGESTIONS,
): List<CatalogIngredient> {
    val productWords = keyWords(productName).toSet()
    if (productWords.isEmpty()) return emptyList()
    return ingredients
        .map { it to keyWords(it.name) }
        .filter { (_, words) -> words.isNotEmpty() && productWords.containsAll(words) }
        .sortedWith(compareBy({ -it.second.size }, { it.first.name }))
        .map { it.first }
        .take(max)
}
