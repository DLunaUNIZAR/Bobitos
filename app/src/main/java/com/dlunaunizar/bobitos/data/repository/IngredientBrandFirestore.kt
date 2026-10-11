package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.MAX_STORES
import com.dlunaunizar.bobitos.core.model.Nutrition
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.google.firebase.firestore.FieldValue
import java.text.Collator
import java.util.Locale

private const val MAX_NAME_LENGTH = 60
private const val MAX_BARCODE_LENGTH = 32

internal val NUTRITION_FIELDS = listOf("energyKcal", "fat", "carbohydrates", "sugars", "protein", "salt")

/** Tiendas sin `INDIFERENTE` ni repetidas, en el mismo orden y con 6 como máximo. */
internal fun normalizeStores(stores: List<Supermarket>): List<Supermarket> =
    stores.filter { it != Supermarket.INDIFERENTE }.distinct().take(MAX_STORES)

/** Datos editables de una marca personal. */
data class BrandInput(
    val ingredientId: String,
    val name: String,
    val barcode: String?,
    val nutrition: Nutrition?,
    val favorite: Boolean,
    val stores: List<Supermarket>,
)

internal fun validateBrandInput(input: BrandInput): BrandInput {
    val name = input.name.trim()
    val barcode = input.barcode?.trim()?.takeIf(String::isNotEmpty)
    val failure = when {
        name.isEmpty() -> BrandFailure.NameRequired
        name.length > MAX_NAME_LENGTH -> BrandFailure.NameTooLong
        barcode != null && barcode.length > MAX_BARCODE_LENGTH -> BrandFailure.BarcodeTooLong
        else -> null
    }
    if (failure != null) throw BrandRepositoryException(failure)
    return input.copy(
        ingredientId = input.ingredientId.trim(),
        name = name,
        barcode = barcode,
        stores = normalizeStores(input.stores),
    )
}

internal fun BrandInput.toFirestoreFields(): Map<String, Any?> {
    val fields = mutableMapOf<String, Any?>(
        "ingredientId" to ingredientId,
        "name" to name,
        "favorite" to favorite,
        "stores" to stores.map(Supermarket::name),
    )
    barcode?.let { fields["barcode"] = it }
    fields.putAll(nutrition.toNonNullFields())
    return fields
}

/** Campos que cambian al editar: sin `ingredientId`; el código y los nutrientes ausentes se borran. */
internal fun BrandInput.toUpdateFields(): Map<String, Any?> {
    val fields = toFirestoreFields().toMutableMap()
    fields.remove("ingredientId")
    if (barcode == null) fields["barcode"] = FieldValue.delete()
    for (key in NUTRITION_FIELDS) if (!fields.containsKey(key)) fields[key] = FieldValue.delete()
    return fields
}

internal fun Nutrition?.toNonNullFields(): Map<String, Any> = mapOf(
    "energyKcal" to this?.energyKcal,
    "fat" to this?.fat,
    "carbohydrates" to this?.carbohydrates,
    "sugars" to this?.sugars,
    "protein" to this?.protein,
    "salt" to this?.salt,
).filterValues { it != null }.mapValues { it.value as Any }

/** Marca a partir de los campos de Firestore; `null` sin `ingredientId` o `name`. */
internal fun parseIngredientBrand(id: String, data: Map<String, Any?>): IngredientBrand? {
    val ingredientId = (data["ingredientId"] as? String)?.takeIf(String::isNotBlank) ?: return null
    val name = (data["name"] as? String)?.takeIf(String::isNotBlank) ?: return null
    val nutrition = Nutrition(
        energyKcal = (data["energyKcal"] as? Number)?.toDouble(),
        fat = (data["fat"] as? Number)?.toDouble(),
        carbohydrates = (data["carbohydrates"] as? Number)?.toDouble(),
        sugars = (data["sugars"] as? Number)?.toDouble(),
        protein = (data["protein"] as? Number)?.toDouble(),
        salt = (data["salt"] as? Number)?.toDouble(),
    )
    return IngredientBrand(
        id = id,
        ingredientId = ingredientId,
        name = name,
        barcode = (data["barcode"] as? String)?.takeIf(String::isNotBlank),
        nutrition = nutrition.takeUnless(Nutrition::isEmpty),
        favorite = data["favorite"] as? Boolean ?: false,
        stores = parseStores(data["stores"]),
    )
}

/** Lista cruda de nombres de tienda → tiendas válidas: ignora desconocidas, `INDIFERENTE` y repetidas. */
internal fun parseStores(raw: Any?): List<Supermarket> {
    val parsed = (raw as? List<*>).orEmpty().mapNotNull { value ->
        (value as? String)?.let { name -> runCatching { Supermarket.valueOf(name) }.getOrNull() }
    }
    return normalizeStores(parsed)
}

// Compartido entre llamadas: RuleBasedCollator.compare está sincronizado.
private val BRAND_COLLATOR: Collator =
    Collator.getInstance(Locale.forLanguageTag("es-ES")).apply { strength = Collator.PRIMARY }

/** Orden de las marcas: favoritas primero y, después, por nombre sin acentos ni mayúsculas e id. */
internal fun List<IngredientBrand>.sortedForBrands(): List<IngredientBrand> = sortedWith { a, b ->
    when {
        a.favorite != b.favorite -> if (a.favorite) -1 else 1
        else -> BRAND_COLLATOR.compare(a.name, b.name).takeIf { it != 0 } ?: a.id.compareTo(b.id)
    }
}
