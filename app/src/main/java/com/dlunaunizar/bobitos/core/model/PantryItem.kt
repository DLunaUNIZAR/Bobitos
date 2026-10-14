package com.dlunaunizar.bobitos.core.model

/** Algo que hay en la despensa del espacio (`spaces/{spaceId}/pantryItems/{id}`); su `id` es [pantryId] de [name]. */
data class PantryItem(
    val id: String,
    val name: String,
    /** Categoría del catálogo (o la del ingrediente personal); `null` u otra que no es de la lista van a «Otros». */
    val category: String?,
    /** El artículo de la Compra cuya compra lo trajo; `null` si se apuntó a mano. */
    val shoppingItemId: String?,
)

/** Lo que se quiere apuntar en la despensa. */
data class PantryEntry(val name: String, val category: String?)

const val MAX_PANTRY_NAME_LENGTH = 120
const val MAX_PANTRY_CATEGORY_LENGTH = 60

/**
 * Id de la entrada de despensa de [name]: el slug de su clave de la Compra. «Tomates» y «tomate» dan `tomat`;
 * «Aceite de oliva», `aceit-oliva`. Vacío si el nombre no tiene letras ni números: eso no se puede apuntar.
 */
fun pantryId(name: String): String = slug(shoppingMatchKey(name))

/** Si [name] está en la despensa ([pantryIds], ids de [pantryId]); un nombre sin letras ni números nunca lo está. */
fun isInPantry(name: String, pantryIds: Set<String>): Boolean =
    pantryId(name).let { it.isNotEmpty() && it in pantryIds }
