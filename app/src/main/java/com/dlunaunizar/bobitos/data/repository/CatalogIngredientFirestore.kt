package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.slug
import java.text.Collator
import java.util.Locale

private const val MAX_NAME_LENGTH = 120
private const val MAX_CATEGORY_LENGTH = 60
private const val MAX_UNIT_LENGTH = 24

/** Ficha de ingrediente a partir de los campos de Firestore; `null` si no tiene nombre. */
internal fun parseCatalogIngredient(id: String, data: Map<String, Any?>, personal: Boolean): CatalogIngredient? {
    val name = (data["name"] as? String)?.takeIf(String::isNotBlank) ?: return null
    return CatalogIngredient(
        id = id,
        name = name,
        category = (data["category"] as? String)?.takeIf(String::isNotBlank),
        defaultUnit = (data["defaultUnit"] as? String)?.takeIf(String::isNotBlank),
        personal = personal,
    )
}

// Compartido entre llamadas: RuleBasedCollator.compare está sincronizado.
private val INGREDIENT_COLLATOR: Collator =
    Collator.getInstance(Locale.forLanguageTag("es-ES")).apply { strength = Collator.PRIMARY }

/** Orden del catálogo: por nombre sin distinguir acentos ni mayúsculas y, a igualdad, por id. */
internal fun List<CatalogIngredient>.sortedForIngredientCatalog(): List<CatalogIngredient> =
    sortedWith { a, b -> INGREDIENT_COLLATOR.compare(a.name, b.name).takeIf { it != 0 } ?: a.id.compareTo(b.id) }

internal data class IngredientInput(val name: String, val category: String?, val defaultUnit: String?)

internal fun validateIngredientInput(name: String, category: String?, defaultUnit: String?): IngredientInput {
    val normalizedName = name.trim()
    val normalizedCategory = category?.trim()?.takeIf(String::isNotEmpty)
    val normalizedUnit = defaultUnit?.trim()?.takeIf(String::isNotEmpty)
    val failure = when {
        normalizedName.isEmpty() || slug(normalizedName).isEmpty() -> IngredientFailure.NameRequired
        normalizedName.length > MAX_NAME_LENGTH -> IngredientFailure.NameTooLong
        normalizedCategory != null && normalizedCategory.length > MAX_CATEGORY_LENGTH ->
            IngredientFailure.CategoryTooLong
        normalizedUnit != null && normalizedUnit.length > MAX_UNIT_LENGTH -> IngredientFailure.UnitTooLong
        else -> null
    }
    if (failure != null) throw IngredientRepositoryException(failure)
    return IngredientInput(normalizedName, normalizedCategory, normalizedUnit)
}

internal fun IngredientInput.toFirestoreFields(): Map<String, Any?> = mapOf(
    "name" to name,
    "nameLower" to name.lowercase(),
    "category" to category,
    "defaultUnit" to defaultUnit,
)
