package com.dlunaunizar.bobitos.feature.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.pantryId

// «Con lo que hay» del Recetario y del selector de receta del planificador (spec 2026-10-13-despensa-design, §4c y §4d).

/**
 * Los ingredientes de [recipe] que no hay en la despensa ([pantryIds]), como se escriben en la receta (recortados), sin
 * repetir por clave y en el orden de la receta. Los nombres sin letras ni números ni están ni faltan.
 */
internal fun missingFromPantry(recipe: Recipe, pantryIds: Set<String>): List<String> =
    missing(pantryNames(recipe), pantryIds)

// Los nombres de [names] ((nombre, `pantryId`)) cuyo id no está en la despensa: la regla de «lo que falta», en un sitio.
private fun missing(names: List<Pair<String, String>>, pantryIds: Set<String>): List<String> =
    names.filterNot { it.second in pantryIds }.map { it.first }

// (nombre recortado, `pantryId`) de cada ingrediente con letras o números, sin repetir por id y en el orden de la receta.
private fun pantryNames(recipe: Recipe): List<Pair<String, String>> = recipe.ingredients.orEmpty()
    .map { it.name.trim().let { name -> name to pantryId(name) } }
    .filter { it.second.isNotEmpty() }
    .distinctBy { it.second }

/** La línea de una receta en «Con lo que hay»: su texto y sus argumentos. */
data class PantryLine(@StringRes val res: Int, val args: List<Any> = emptyList())

/** Una receta de la lista y, con «Con lo que hay», su línea. */
data class RecipeRow(val recipe: Recipe, val pantryLine: PantryLine?)

/**
 * Sin [pantryIds], las recetas tal cual y sin línea. Con ellos («Con lo que hay»): solo las que tienen algún ingrediente,
 * de la que menos falta a la que más, después por título y por id, cada una con su línea.
 */
internal fun List<Recipe>.withPantryLines(pantryIds: Set<String>?): List<RecipeRow> {
    if (pantryIds == null) return map { RecipeRow(it, null) }
    return map { it to pantryNames(it) }
        .filter { it.second.isNotEmpty() }
        .map { (recipe, names) -> recipe to missing(names, pantryIds) }
        .sortedWith(compareBy({ it.second.size }, { it.first.title.lowercase() }, { it.first.id }))
        .map { (recipe, missing) -> RecipeRow(recipe, pantryLine(missing)) }
}

/**
 * «Lo tienes todo», «Falta: huevos», «Faltan 2: huevos y leche», «Faltan 3: huevos, leche y harina» o, con más de tres,
 * «Faltan 5: huevos, leche y 3 más». Los nombres van con la inicial en minúscula, salvo las siglas («AOVE»).
 */
internal fun pantryLine(missing: List<String>): PantryLine {
    val names = missing.map(::lowerInitial)
    return when (names.size) {
        0 -> PantryLine(R.string.recipes_pantry_all)
        1 -> PantryLine(R.string.recipes_pantry_missing_one, names)
        2 -> PantryLine(R.string.recipes_pantry_missing_two, names)
        3 -> PantryLine(R.string.recipes_pantry_missing_three, names)
        else -> PantryLine(R.string.recipes_pantry_missing_more, listOf(names.size, names[0], names[1], names.size - 2))
    }
}

// «Huevos» → «huevos»; «AOVE» se queda (dos mayúsculas al principio).
private fun lowerInitial(name: String): String {
    val acronym = name.length > 1 && name[0].isUpperCase() && name[1].isUpperCase()
    return if (acronym) name else name.replaceFirstChar { it.lowercase() }
}

// El spread copia un array de 0-4 argumentos: no importa.
@Suppress("SpreadOperator")
@Composable
internal fun pantryLineText(line: PantryLine): String = stringResource(line.res, *line.args.toTypedArray())
