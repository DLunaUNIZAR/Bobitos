package com.dlunaunizar.bobitos.feature.recipes

import android.os.Parcelable
import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeLimits
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import com.dlunaunizar.bobitos.data.recipeimport.ImportedRecipe
import kotlinx.parcelize.Parcelize

/** Fila editable de ingrediente: tres textos. Inmutable; el editor sustituye la fila al cambiarla. */
@Parcelize
internal data class IngredientRow(val name: String = "", val quantity: String = "", val unit: String = "") : Parcelable

// Borrador del editor de receta. Parcelable para que sobreviva a una rotación (también el borrador de
// una receta importada, que guarda el anfitrión). `sourceUrl` es el enlace de una receta importada.
// Los pasos van tal como se escriben (se limpian al guardar); raciones y tiempo, como el texto de su campo.
@Parcelize
internal data class RecipeDraft(
    val title: String,
    val description: String,
    val category: String,
    val global: Boolean,
    val ingredients: List<IngredientRow>,
    val sourceUrl: String?,
    val steps: List<String> = emptyList(),
    val servings: String = "",
    val totalMinutes: String = "",
) : Parcelable {
    companion object {
        /** Valores iniciales: los de la receta existente o, al importar, los del borrador de la web. */
        fun of(recipe: Recipe?, imported: ImportedRecipe?) = RecipeDraft(
            title = recipe?.title ?: imported?.title.orEmpty(),
            description = recipe?.description ?: imported?.description.orEmpty(),
            category = recipe?.category ?: imported?.category.orEmpty(),
            global = recipe?.visibility == RecipeVisibility.GLOBAL,
            ingredients = (recipe?.ingredients ?: imported?.ingredients).orEmpty()
                .map { IngredientRow(it.name, it.quantity.orEmpty(), it.unit.orEmpty()) },
            sourceUrl = imported?.sourceUrl,
            steps = (recipe?.steps ?: imported?.steps).orEmpty(),
            servings = (recipe?.servings ?: imported?.servings)?.toString().orEmpty(),
            totalMinutes = (recipe?.totalMinutes ?: imported?.totalMinutes)?.toString().orEmpty(),
        )
    }
}

// Descarta filas sin nombre y normaliza cantidad/unidad vacías a null.
internal fun List<IngredientRow>.toIngredients(): List<Ingredient> = mapNotNull { row ->
    row.name.trim().takeIf(String::isNotEmpty)?.let { name ->
        Ingredient(name, row.quantity.trim().ifBlank { null }, row.unit.trim().ifBlank { null })
    }
}

// Raciones o minutos escritos: vacío → null; un entero → ese número; cualquier otra cosa («dos», «1,5»)
// → 0, que la validación rechaza por estar fuera de rango.
internal fun String.toRecipeCount(): Int? = trim().takeIf(String::isNotEmpty)?.let { it.toIntOrNull() ?: 0 }

// Validación del borrador tal como se guardaría (pasos limpios; raciones y tiempo como números).
internal fun RecipeDraft.validate(): RecipeUiMessage? = RecipesValidation.validate(
    title = title,
    description = description,
    category = category,
    steps = steps,
    servings = servings.toRecipeCount(),
    totalMinutes = totalMinutes.toRecipeCount(),
)

// Errores de cada campo, aparte de la validación general (que solo da el primero): raciones, tiempo y cada paso largo
// se marcan aunque falle antes otro campo, como el título.
internal fun RecipeDraft.servingsError(): RecipeUiMessage? =
    RecipeUiMessage.ServingsInvalid.takeIf { servings.toRecipeCount().isOutside(RecipeLimits.SERVINGS) }

internal fun RecipeDraft.totalMinutesError(): RecipeUiMessage? =
    RecipeUiMessage.TotalMinutesInvalid.takeIf { totalMinutes.toRecipeCount().isOutside(RecipeLimits.TOTAL_MINUTES) }

// Posiciones de los pasos que pasan de largo, medidos sin espacios en los extremos, como se guardan.
internal fun RecipeDraft.tooLongSteps(): Set<Int> =
    steps.indices.filter { steps[it].trim().length > RecipeLimits.MAX_STEP_LENGTH }.toSet()

private fun Int?.isOutside(range: IntRange): Boolean = this != null && this !in range

// Edición de la lista de pasos: cada cambio devuelve otro borrador.
internal fun RecipeDraft.withStep(index: Int, text: String): RecipeDraft =
    copy(steps = steps.mapIndexed { i, step -> if (i == index) text else step })

internal fun RecipeDraft.withoutStep(index: Int): RecipeDraft = copy(steps = steps.filterIndexed { i, _ -> i != index })

// Añade un paso vacío al final, hasta el máximo.
internal fun RecipeDraft.withNewStep(): RecipeDraft =
    if (steps.size < RecipeLimits.MAX_STEPS) copy(steps = steps + "") else this

// Sube (offset -1) o baja (+1) el paso [index]; en los extremos no cambia nada.
internal fun RecipeDraft.withStepMoved(index: Int, offset: Int): RecipeDraft {
    val target = index + offset
    if (index !in steps.indices || target !in steps.indices) return this
    return copy(steps = steps.toMutableList().apply { add(target, removeAt(index)) })
}
