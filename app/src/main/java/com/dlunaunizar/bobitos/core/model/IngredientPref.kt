package com.dlunaunizar.bobitos.core.model

/**
 * Personalización de un ingrediente del catálogo **a nivel de usuario**: el supermercado y la marca
 * por defecto con los que ese usuario quiere que aparezca en la lista de la compra. No forma parte de
 * la ficha global del ingrediente; vive en el documento de preferencias del propio usuario.
 */
data class IngredientPref(val supermarket: Supermarket? = null, val brand: String? = null)
