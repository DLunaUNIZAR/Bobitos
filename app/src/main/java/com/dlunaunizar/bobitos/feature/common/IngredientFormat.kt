package com.dlunaunizar.bobitos.feature.common

import com.dlunaunizar.bobitos.core.model.Ingredient

/** «300 g Arroz» o «Sal» (omite cantidad/unidad ausentes). */
internal fun Ingredient.formatted(): String = listOfNotNull(quantity, unit, name).joinToString(" ")
