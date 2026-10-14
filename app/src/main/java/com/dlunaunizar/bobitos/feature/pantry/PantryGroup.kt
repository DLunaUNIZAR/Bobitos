package com.dlunaunizar.bobitos.feature.pantry

/** Un grupo de la Despensa: [category] es una de las 14 del catálogo, o `null` para «Otros» (siempre al final). */
internal data class PantryGroup<T>(val category: String?, val items: List<T>)

// Claves de LazyColumn: un id de entrada es un slug y nunca lleva «:», así que ningún prefijo choca con él.
internal fun pantryHeaderKey(category: String?): String = "h:${category.orEmpty()}"

internal fun pantryItemKey(id: String): String = "i:$id"

internal fun pantryFreeTextKey(id: String): String = "free:$id"
