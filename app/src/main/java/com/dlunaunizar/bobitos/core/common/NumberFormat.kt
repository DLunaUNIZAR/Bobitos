package com.dlunaunizar.bobitos.core.common

/** Número sin decimales innecesarios para mostrarlo o precargarlo en un campo («120», «1.5»). */
fun formatDecimal(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
