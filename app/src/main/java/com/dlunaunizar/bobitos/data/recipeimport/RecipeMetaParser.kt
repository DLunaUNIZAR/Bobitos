package com.dlunaunizar.bobitos.data.recipeimport

import com.dlunaunizar.bobitos.core.model.RecipeLimits
import com.dlunaunizar.bobitos.core.model.cleanRecipeSteps
import com.dlunaunizar.bobitos.core.model.toIntIn
import org.json.JSONArray

/**
 * Raciones, tiempo y pasos de una receta schema.org, ya dentro de los límites del modelo. Recibe los
 * valores crudos del JSON-LD y nunca lanza: lo que no entiende queda en null.
 */
internal object RecipeMetaParser {
    private const val MINUTES_PER_HOUR = 60
    private const val MINUTES_PER_DAY = 1440
    private const val SECONDS_PER_MINUTE = 60
    private val FIRST_NUMBER = Regex("\\d+")
    private val SENTENCE_END = Regex("(?<=[.!?])\\s+")

    // Años, meses, semanas, días, horas, minutos y segundos (estos, con decimales); cada parte es opcional.
    private val ISO_DURATION = Regex(
        "P(?:(\\d+)Y)?(?:(\\d+)M)?(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+(?:[.,]\\d+)?)S)?)?",
        RegexOption.IGNORE_CASE,
    )

    /** `recipeYield` (número, texto como «4 raciones» o «4-6», o array): su primer entero, si está en 1–50. */
    fun servings(raw: Any?): Int? = when (raw) {
        is Number -> raw.toLong().toIntIn(RecipeLimits.SERVINGS)
        is String -> FIRST_NUMBER.find(raw)?.value?.toLongOrNull()?.toIntIn(RecipeLimits.SERVINGS)
        is JSONArray -> (0 until raw.length()).firstNotNullOfOrNull { servings(raw.opt(it)) }
        else -> null
    }

    /**
     * Minutos de `totalTime`; si falta, no se entiende o vale 0, la suma de `prepTime` y `cookTime` (o el
     * que haya). Fuera de 1–1440 → null.
     */
    fun totalMinutes(total: String?, prep: String?, cook: String?): Int? {
        val minutes = isoMinutes(total)?.takeIf { it > 0 }
            ?: listOfNotNull(isoMinutes(prep), isoMinutes(cook)).takeIf { it.isNotEmpty() }?.sum()
        return minutes?.toIntIn(RecipeLimits.TOTAL_MINUTES)
    }

    /** Duración ISO 8601 («PT1H30M», «P0DT1H») en minutos; null si no lo es o trae años, meses o semanas. */
    fun isoMinutes(raw: String?): Long? {
        val parts = ISO_DURATION.matchEntire(raw?.trim().orEmpty())?.groupValues?.drop(1)
            ?.takeIf { groups -> groups.any(String::isNotEmpty) }
            ?.map { it.replace(',', '.').toDoubleOrNull() ?: 0.0 }
            ?: return null
        if (parts[0] + parts[1] + parts[2] > 0) return null
        val minutes = parts[3] * MINUTES_PER_DAY + parts[4] * MINUTES_PER_HOUR + parts[5] +
            parts[6] / SECONDS_PER_MINUTE
        return minutes.toLong()
    }

    /**
     * Pasos para el editor: limpios, sin perder texto (un paso de más de 500 caracteres se parte por frases
     * y, si una frase sola no cabe, en trozos de 500) y como mucho 30.
     */
    fun steps(raw: List<String>): List<String> =
        cleanRecipeSteps(raw).flatMap(::splitLongStep).take(RecipeLimits.MAX_STEPS)

    private fun splitLongStep(step: String): List<String> {
        if (step.length <= RecipeLimits.MAX_STEP_LENGTH) return listOf(step)
        val pieces = step.split(SENTENCE_END).flatMap { it.chunked(RecipeLimits.MAX_STEP_LENGTH) }
        val chunks = mutableListOf<String>()
        var current = ""
        for (piece in pieces) {
            current = when {
                current.isEmpty() -> piece
                current.length + 1 + piece.length <= RecipeLimits.MAX_STEP_LENGTH -> "$current $piece"
                else -> {
                    chunks += current
                    piece
                }
            }
        }
        chunks += current
        return chunks
    }
}
