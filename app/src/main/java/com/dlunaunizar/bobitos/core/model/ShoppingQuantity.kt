package com.dlunaunizar.bobitos.core.model

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Cantidades de la lista de la compra, que son texto libre («200 g», «1/2», «2 dientes»). [sum] suma
 * dos cantidades agrupando por unidad los términos «número [unidad]» (cada cantidad puede llevar varios
 * unidos con « + »); lo que no se lee se conserva tal cual y los grupos se unen con « + ». El número es
 * entero, decimal (coma o punto) o fracción «a/b»; la unidad, si la hay, son palabras de letras con un punto
 * final opcional («g.», «cdas.»). Cualquier otro resto («2-3», «1 ½», «2 x 200 g») no se lee. Las fracciones
 * se suman exactas y solo se redondea a dos decimales el total. Un número con punto y exactamente tres
 * decimales («1.500») es ambiguo (en España el punto separa miles) y tampoco se lee.
 */
object ShoppingQuantity {
    /** Máximo de caracteres de una cantidad (también lo exigen las reglas de Firestore). */
    const val MAX_LENGTH = 40

    private const val SEPARATOR = " + "
    private const val FRACTION_SCALE = 2
    private val QUANTITY = Regex("""^(\d+/\d+|\d+(?:[.,]\d+)?)\s*(\p{L}+(?:\s+\p{L}+)*)?\.?$""")
    private val THOUSANDS_LIKE = Regex("""\d+\.\d{3}""")
    private const val VOWELS = "aeiouáéíóú"
    private const val ES_PLURAL_CONSONANTS = "lrndj"
    private val PLAIN_VOWELS = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u')

    // Unidades básicas: todas sus grafías cuentan como la misma y se escriben en su forma corta.
    private val BASIC_UNITS = mapOf(
        "g" to "g", "gr" to "g", "gramo" to "g", "gramos" to "g",
        "kg" to "kg", "kilo" to "kg", "kilos" to "kg",
        "ml" to "ml",
        "l" to "l", "litro" to "l", "litros" to "l",
        "ud" to "ud", "uds" to "ud", "u" to "ud", "unidad" to "ud", "unidades" to "ud",
    )

    /**
     * Suma [a] y [b]; si una falta o está en blanco, devuelve la otra (recortada); `null` si faltan las dos.
     * Cada una se parte en términos por « + »: los de la misma unidad se suman en un grupo, que ocupa el sitio
     * de su primer término, y los que no se leen se conservan tal cual. Un grupo de un solo término se escribe
     * con su texto original.
     */
    fun sum(a: String?, b: String?): String? {
        val first = a?.trim().orEmpty()
        val second = b?.trim().orEmpty()
        if (first.isEmpty()) return second.ifEmpty { null }
        if (second.isEmpty()) return first
        val groups = mutableListOf<Group>()
        (termsOf(first) + termsOf(second)).forEach { term ->
            val parsed = parse(term)
            val group = parsed?.let { p -> groups.firstOrNull { it.key == p.unitKey } }
            if (parsed == null || group == null) groups += Group(parsed?.unitKey, term, parsed) else group.add(parsed)
        }
        return groups.joinToString(SEPARATOR) { it.write() }
    }

    private fun termsOf(text: String): List<String> = text.split(SEPARATOR).map(String::trim).filter(String::isNotEmpty)

    // Términos de una misma unidad (clave nula = término sin leer, que va solo y literal).
    private class Group(val key: String?, private val original: String, first: Parsed?) {
        private val members = listOfNotNull(first).toMutableList()

        fun add(term: Parsed) {
            members += term
        }

        fun write(): String {
            if (members.size < 2) return original
            val total = members.fold(BigDecimal.ZERO) { acc, term -> acc + term.amount }
                .setScale(FRACTION_SCALE, RoundingMode.HALF_UP)
            val unit = unitFor(total, members)
            val amount = total.stripTrailingZeros().toPlainString().replace('.', ',')
            return if (unit.isEmpty()) amount else "$amount $unit"
        }
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
        return numerator.divide(denominator, MathContext.DECIMAL64)
    }

    // Básicas en forma corta; las demás, en singular si el total es 1 o menos y en plural si pasa de 1. Sin grafía en
    // singular, se saca de la del plural.
    private fun unitFor(total: BigDecimal, terms: List<Parsed>): String {
        val head = terms.first().unit
        if (head.isEmpty()) return ""
        BASIC_UNITS[head.lowercase()]?.let { return it }
        val spellings = terms.map { it.unit }
        if (total <= BigDecimal.ONE) return spellings.firstOrNull { !it.endsWith("s") } ?: singularOf(head)
        return spellings.firstOrNull { it.endsWith("s") } ?: pluralOf(head)
    }

    // Plural aproximado del español: tras vocal, «-s»; «-z» → «-ces»; tras l, r, n, d o j, «-es», y la aguda pierde la
    // tilde («limón» → «limones»); tras otra consonante (palabras de fuera, como «pack»), «-s».
    private fun pluralOf(unit: String): String {
        val last = unit.last().lowercaseChar()
        return when {
            last in VOWELS -> "${unit}s"
            last == 'z' -> "${unit.dropLast(1)}ces"
            last in ES_PLURAL_CONSONANTS -> "${unit.withoutFinalAccent()}es"
            else -> "${unit}s"
        }
    }

    // Singular aproximado de una grafía en plural: «-ces» → «-z»; «-ones» → «-ón»; si no, sin la «-s» final. Falla en
    // palabras raras como unidad («flores», «panes»).
    private fun singularOf(unit: String): String {
        val lower = unit.lowercase()
        return when {
            lower.endsWith("ces") -> "${unit.dropLast(3)}z"
            lower.endsWith("ones") -> "${unit.dropLast(4)}ón"
            lower.endsWith("s") -> unit.dropLast(1)
            else -> unit
        }
    }

    // Quita la tilde de la vocal que va justo antes de la consonante final («limón» → «limon»).
    private fun String.withoutFinalAccent(): String {
        if (length < 2) return this
        val plain = PLAIN_VOWELS[this[length - 2]] ?: return this
        return substring(0, length - 2) + plain + last()
    }
}
