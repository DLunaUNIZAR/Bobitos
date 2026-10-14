package com.dlunaunizar.bobitos.feature.pantry

/** Un grupo de la Despensa: [category] es una de las 14 del catálogo, o `null` para «Otros» (siempre al final). */
internal data class PantryGroup<T>(val category: String?, val items: List<T>)
