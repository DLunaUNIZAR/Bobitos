package com.dlunaunizar.bobitos.feature.meals

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.prepareQuery
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import com.dlunaunizar.bobitos.feature.common.RecipeRow
import com.dlunaunizar.bobitos.feature.common.pantryLineText
import com.dlunaunizar.bobitos.feature.common.withPantryLines

// Recetas del selector del planificador: primero las privadas (mías) y después las comunes, cada grupo por
// título. La búsqueda mira el título y la categoría, por palabras y sin tildes; en blanco, todas.
internal fun List<Recipe>.forPicker(query: String): List<Recipe> {
    val search = prepareQuery(query)
    return filter { search.matches(it.title, it.category) }
        .sortedWith(compareBy({ it.visibility != RecipeVisibility.PRIVATE }, { it.title.lowercase() }, Recipe::id))
}

// Etiqueta del origen de una receta en el selector: las privadas son del usuario; las GLOBAL, comunes
// (también las que publica el admin).
@StringRes
internal fun recipeOriginRes(recipe: Recipe): Int = when (recipe.visibility) {
    RecipeVisibility.PRIVATE -> R.string.meals_recipe_origin_mine
    RecipeVisibility.GLOBAL -> R.string.meals_recipe_origin_common
}

// Filas del selector: sin [pantryIds], como [forPicker] y sin línea; con ellos, «Con lo que hay» (ver withPantryLines).
internal fun List<Recipe>.pickerRows(query: String, pantryIds: Set<String>?): List<RecipeRow> =
    forPicker(query).withPantryLines(pantryIds)

// Selector de receta del editor de comidas, con buscador y, si se puede leer la despensa, el chip «Con lo que hay».
// Cada fila: título, «Mía»/«Común» (· categoría) y, con el chip, lo que falta.
@Composable
internal fun RecipePickerDialog(
    recipes: List<Recipe>,
    pantryIds: Set<String>?,
    onPick: (Recipe) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var pantryOnly by rememberSaveable { mutableStateOf(false) }
    val shown = remember(recipes, query, pantryOnly, pantryIds) {
        recipes.pickerRows(query, pantryIds?.takeIf { pantryOnly })
    }
    // Al buscar o activar el chip la lista se filtra y se reordena; con claves se quedaría anclada en la receta
    // que estaba arriba, ahora lejos, así que vuelve al primer elemento.
    val listState = rememberLazyListState()
    LaunchedEffect(query, pantryOnly) { listState.scrollToItem(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.meals_recipe_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.recipes_search_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (pantryIds != null) {
                    FilterChip(
                        selected = pantryOnly,
                        onClick = { pantryOnly = !pantryOnly },
                        label = { Text(stringResource(R.string.recipes_filter_pantry)) },
                    )
                }
                if (shown.isEmpty()) {
                    Text(
                        text = stringResource(R.string.recipes_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(state = listState, modifier = Modifier.heightIn(max = 320.dp)) {
                        items(shown, key = { it.recipe.id }) { row ->
                            RecipePickerRow(row = row, onClick = { onPick(row.recipe) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun RecipePickerRow(row: RecipeRow, onClick: () -> Unit) {
    val recipe = row.recipe
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(text = recipe.title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = listOfNotNull(stringResource(recipeOriginRes(recipe)), recipe.category).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        row.pantryLine?.let { line ->
            Text(
                text = pantryLineText(line),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
