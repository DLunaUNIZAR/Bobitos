package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.MAX_PANTRY_CATEGORY_LENGTH
import com.dlunaunizar.bobitos.core.model.MAX_PANTRY_NAME_LENGTH
import com.dlunaunizar.bobitos.core.model.PantryEntry
import com.dlunaunizar.bobitos.core.model.PantryItem
import com.dlunaunizar.bobitos.core.model.pantryId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue

// Compartido por FirestorePantryRepository y FirestoreShoppingRepository (comprado → despensa).
internal const val PANTRY_ITEMS = "pantryItems"
internal const val FIELD_PANTRY_SHOPPING_ITEM_ID = "shoppingItemId"

/**
 * Entrada lista para escribir: nombre recortado (con letras o números, 120 caracteres como máximo) y categoría
 * recortada. Una categoría vacía o de más de 60 caracteres se descarta, no falla.
 */
internal fun validatePantryEntry(entry: PantryEntry): PantryEntry {
    val name = entry.name.trim()
    val failure = when {
        pantryId(name).isEmpty() -> PantryFailure.NameRequired
        name.length > MAX_PANTRY_NAME_LENGTH -> PantryFailure.NameTooLong
        else -> null
    }
    if (failure != null) throw PantryRepositoryException(failure)
    val category = entry.category?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_PANTRY_CATEGORY_LENGTH }
    return PantryEntry(name, category)
}

/** Campos de una entrada nueva; [shoppingItemId] es el artículo de la Compra que la trajo, o `null`. */
internal fun pantryItemFields(entry: PantryEntry, userId: String, shoppingItemId: String?): Map<String, Any?> = mapOf(
    "name" to entry.name,
    "category" to entry.category,
    "createdBy" to userId,
    "createdAt" to FieldValue.serverTimestamp(),
    FIELD_PANTRY_SHOPPING_ITEM_ID to shoppingItemId,
)

internal fun DocumentSnapshot.toPantryItem(): PantryItem? = PantryItem(
    id = id,
    name = getString("name") ?: return null,
    category = getString("category"),
    shoppingItemId = getString(FIELD_PANTRY_SHOPPING_ITEM_ID),
)
