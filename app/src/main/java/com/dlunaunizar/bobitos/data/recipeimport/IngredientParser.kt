package com.dlunaunizar.bobitos.data.recipeimport

import com.dlunaunizar.bobitos.core.model.Ingredient

/**
 * Convierte una línea de ingrediente en texto libre («300 g de arroz») en [Ingredient] separando, de
 * forma best-effort, cantidad y unidad. Si no reconoce una cantidad al principio, deja toda la línea
 * como `name` (nunca inventa). Pensado para español, con algunas abreviaturas/unidades habituales.
 */
object IngredientParser {
    fun parse(raw: String): Ingredient? {
        val line = raw.trim().replace(WHITESPACE, " ")
        if (line.isEmpty()) return null

        val tokens = line.split(' ').toMutableList()
        val quantity = takeQuantity(tokens)
        val unit = if (quantity != null) takeUnit(tokens) else null
        if (unit != null && tokens.firstOrNull()?.lowercase() in CONNECTORS) {
            tokens.removeAt(0)
        }
        val name = tokens.joinToString(" ").trim()

        // Línea degenerada («2 tazas» sin ingrediente): no perdemos la línea, la dejamos íntegra.
        return if (name.isEmpty()) {
            Ingredient(line)
        } else {
            Ingredient(name = name, quantity = quantity, unit = unit)
        }
    }

    // Consume la cantidad inicial (entero/decimal/fracción/rango/fracción unicode) y, opcionalmente,
    // una fracción unicode suelta que la siga («1 ½»).
    private fun takeQuantity(tokens: MutableList<String>): String? {
        val first = tokens.firstOrNull() ?: return null
        if (!QUANTITY.matches(first)) return null
        tokens.removeAt(0)
        val next = tokens.firstOrNull()
        return if (next != null && UNICODE_FRACTION.matches(next)) {
            tokens.removeAt(0)
            "$first $next"
        } else {
            first
        }
    }

    // Si el siguiente token es una unidad conocida (o su plural), la consume.
    private fun takeUnit(tokens: MutableList<String>): String? {
        val candidate = tokens.firstOrNull() ?: return null
        val normalized = candidate.lowercase().trimEnd('.', ',')
        val known = normalized in UNITS || normalized.removeSuffix("s") in UNITS
        return if (known) {
            tokens.removeAt(0)
            candidate
        } else {
            null
        }
    }

    private val WHITESPACE = Regex("\\s+")
    private const val FRACTIONS = "¼½¾⅓⅔⅛⅜⅝⅞"
    private val UNICODE_FRACTION = Regex("^[$FRACTIONS]$")
    private val QUANTITY = Regex(
        "^(\\d+([.,]\\d+)?([-–/]\\d+([.,]\\d+)?)?[$FRACTIONS]?|[$FRACTIONS])$",
    )
    private val CONNECTORS = setOf("de", "del", "of")

    // Formas normalizadas (minúsculas, singular); los plurales se cubren quitando la «s» final.
    private val UNITS = setOf(
        "g", "gr", "gramo", "kg", "kilo", "kilogramo", "mg",
        "ml", "mililitro", "l", "litro", "cl", "dl",
        "cucharada", "cda", "cucharadita", "cdta",
        "taza", "vaso", "diente", "rama", "hoja", "loncha", "rodaja", "rebanada",
        "lata", "sobre", "pizca", "puñado", "trozo", "pieza", "manojo", "chorro",
        "unidad", "ud", "u",
        "tsp", "tbsp", "oz", "lb", "cup",
    )
}
