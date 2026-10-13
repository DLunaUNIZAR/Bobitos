package com.dlunaunizar.bobitos.core.model

import java.time.Instant

enum class RecipeVisibility {
    GLOBAL,
    PRIVATE,
}

data class Recipe(
    val id: String,
    val ownerUid: String,
    val visibility: RecipeVisibility,
    val title: String,
    val description: String?,
    val category: String?,
    val sourceRecipeId: String? = null,
    // Enlace de la web de la que se importó (schema.org/Recipe). Null en recetas creadas a mano o forks.
    val sourceUrl: String? = null,
    val ingredients: List<Ingredient>? = null,
    // Preparación paso a paso; null en las recetas antiguas, que llevan los pasos en la descripción.
    val steps: List<String>? = null,
    // Raciones (1–50) y tiempo total en minutos (1–1440); null si no se indican.
    val servings: Int? = null,
    val totalMinutes: Int? = null,
    val createdBy: String,
    val createdByName: String,
    val createdAt: Instant,
    val updatedBy: String,
    val updatedAt: Instant,
    // Recetas del catálogo: cuándo la escribió el importador (`source.importedAt`); null en las demás.
    val importedAt: Instant? = null,
    // La última edición aún no ha llegado al servidor (`updatedAt` pendiente): «Deshacer» usa la hora del servidor.
    val updatePending: Boolean = false,
)

// Límites de pasos, raciones y tiempo: los mismos en las reglas, el editor, el repositorio y el importador web.
object RecipeLimits {
    const val MAX_STEPS = 30
    const val MAX_STEP_LENGTH = 500
    val SERVINGS = 1..50
    val TOTAL_MINUTES = 1..1440
}

// Pasos tal como se guardan: sin espacios en los extremos y sin los vacíos.
fun cleanRecipeSteps(steps: List<String>): List<String> = steps.map(String::trim).filter(String::isNotEmpty)

// Un Long como Int solo si cae dentro de [range] (raciones y minutos); si no, null.
fun Long.toIntIn(range: IntRange): Int? = takeIf { it >= range.first && it <= range.last }?.toInt()
