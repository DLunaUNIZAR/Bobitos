package com.dlunaunizar.bobitos.core.model

/**
 * Clave para reconocer un artículo repetido en la lista de la compra: la tolerante del catálogo
 * ([ingredientKey]: «Tomates» = «tomate») o, si queda vacía (solo palabras vacías o símbolos), el
 * nombre recortado en minúsculas.
 */
fun shoppingMatchKey(name: String): String = ingredientKey(name).ifEmpty { name.trim().lowercase() }

/**
 * El artículo de [items] que corresponde a [name]: el primero pendiente o, si todos están comprados,
 * el primero comprado. `null` si no hay ninguno o si [name] está en blanco.
 */
fun findShoppingMatch(items: List<ShoppingItem>, name: String): ShoppingItem? {
    if (name.isBlank()) return null
    val key = shoppingMatchKey(name)
    val matches = items.filter { shoppingMatchKey(it.name) == key }
    return matches.firstOrNull { !it.purchased } ?: matches.firstOrNull()
}
