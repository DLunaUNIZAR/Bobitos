package com.dlunaunizar.bobitos.data.openfoodfacts

import com.dlunaunizar.bobitos.core.model.Nutrition
import org.json.JSONObject

/** Producto de Open Food Facts asociado a un código de barras (lo mínimo para prerrellenar una marca). */
data class OffProduct(val productName: String?, val brand: String?, val nutrition: Nutrition)

/** Motivo de fallo al consultar Open Food Facts. */
enum class OffFailure { Network, Unknown }

class OffException(val failure: OffFailure, cause: Throwable? = null) : Exception(cause)

/** Consulta el catálogo público (y gratuito) de Open Food Facts por código de barras. */
interface OpenFoodFactsClient {
    /**
     * @return el producto, o null si no existe en Open Food Facts.
     * @throws OffException si no se pudo consultar (sin conexión, etc.).
     */
    suspend fun lookup(barcode: String): OffProduct?
}

/**
 * Convierte la respuesta JSON de la API v2 (`{status, product:{product_name, brands, nutriments}}`) en
 * [OffProduct]. Devuelve null si el producto no existe (`status != 1`). Los nutrientes usan los campos
 * `*_100g`. Extraído aparte para poder testear el parseo sin red.
 */
internal fun parseOffProduct(json: String): OffProduct? {
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
    if (root.optInt("status", 0) != 1) return null
    val product = root.optJSONObject("product") ?: return null
    val nutriments = product.optJSONObject("nutriments")
    return OffProduct(
        productName = product.optString("product_name").trim().takeIf(String::isNotEmpty),
        brand = product.optString("brands").split(',').firstOrNull()?.trim()?.takeIf(String::isNotEmpty),
        nutrition = Nutrition(
            energyKcal = nutriments.number("energy-kcal_100g"),
            fat = nutriments.number("fat_100g"),
            carbohydrates = nutriments.number("carbohydrates_100g"),
            sugars = nutriments.number("sugars_100g"),
            protein = nutriments.number("proteins_100g"),
            salt = nutriments.number("salt_100g"),
        ),
    )
}

private fun JSONObject?.number(key: String): Double? {
    if (this == null || !has(key) || isNull(key)) return null
    return optDouble(key).takeUnless(Double::isNaN)
}
