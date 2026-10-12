package com.dlunaunizar.bobitos.core.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Cantidades de la lista de la compra, que son texto libre («200 g», «1/2», «2 dientes»). [sum] suma
 * dos cantidades si ambas son «número [unidad]» con la misma unidad; si no, las une con « + ». El número es
 * entero, decimal (coma o punto) o fracción «a/b»; la unidad, si la hay, son palabras de letras con un punto
 * final opcional («g.», «cdas.»). Cualquier otro resto («2-3», «1 ½», «2 x 200 g») no se lee. Un número con
 * punto y exactamente tres decimales («1.500») es ambiguo (en España el punto separa miles) y tampoco se lee.
 */
object ShoppingQuantity {
    /** Máximo de caracteres de una cantidad (también lo exigen las reglas de Firestore). */
    const val MAX_LENGTH = 40

    private const val FRACTION_SCALE = 2
    private val QUANTITY = Regex("""^(\d+/\d+|\d+(?:[.,]\d+)?)\s*(\p{L}+(?:\s+\p{L}+)*)?\.?$""")
    private val THOUSANDS_LIKE = Regex("""\d+\.\d{3}""")
    private const val VOWELS = "aeiouáéíóú"

    // Unidades básicas: todas sus grafías cuentan como la misma y se escriben en su forma corta.
    private val BASIC_UNITS = mapOf(
        "g" to "g", "gr" to "g", "gramo" to "g", "gramos" to "g",
        "kg" to "kg", "kilo" to "kg", "kilos" to "kg",
        "ml" to "ml",
        "l" to "l", "litro" to "l", "litros" to "l",
        "ud" to "ud", "uds" to "ud", "u" to "ud", "unidad" to "ud", "unidades" to "ud",
    )

    /** Suma [a] y [b]; si una falta o está en blanco, devuelve la otra (recortada); `null` si faltan las dos. */
    fun sum(a: String?, b: String?): String? {
        val first = a?.trim().orEmpty()
        val second = b?.trim().orEmpty()
        if (first.isEmpty()) return second.ifEmpty { null }
        if (second.isEmpty()) return first
        val x = parse(first)
        val y = parse(second)
        if (x == null || y == null || x.unitKey != y.unitKey) return "$first + $second"
        val total = x.amount + y.amount
        val unit = unitFor(total, x, y)
        val amount = total.stripTrailingZeros().toPlainString().replace('.', ',')
        return if (unit.isEmpty()) amount else "$amount $unit"
    }

    private data class Parsed(val amount: BigDecimal, val unit: String, val unitKey: String)

    private fun parse(text: String): Parsed? {
        val match = QUANTITY.matchEntire(text) ?: return null
        val (number, rawUnit) = match.destructured
        if (THOUSANDS_LIKE.matches(number)) return null
        val amount = amountOf(number) ?: return null
        val unit = rawUnit.trim()
        val lower = unit.lowercase()
        // Si la clave queda vacía («de»), cae al texto para no casar con «sin unidad».
        return Parsed(amount, unit, BASIC_UNITS[lower] ?: ingredientKey(lower).ifEmpty { lower })
    }

    private fun amountOf(number: String): BigDecimal? {
        if ('/' !in number) return number.replace(',', '.').toBigDecimal()
        val (numerator, denominator) = number.split('/').map(String::toBigDecimal)
        if (denominator.signum() == 0) return null
        return numerator.divide(denominator, FRACTION_SCALE, RoundingMode.HALF_UP)
    }

    // Básicas en forma corta; las demás, en singular si el total es 1 o menos y en plural si pasa de 1.
    private fun unitFor(total: BigDecimal, x: Parsed, y: Parsed): String {
        if (x.unit.isEmpty()) return ""
        BASIC_UNITS[x.unit.lowercase()]?.let { return it }
        val spellings = listOf(x.unit, y.unit)
        if (total <= BigDecimal.ONE) return spellings.firstOrNull { !it.endsWith("s") } ?: x.unit
        return spellings.firstOrNull { it.endsWith("s") } ?: pluralOf(x.unit)
    }

    private fun pluralOf(unit: String): String = if (unit.last().lowercaseChar() in VOWELS) "${unit}s" else "${unit}es"
}
