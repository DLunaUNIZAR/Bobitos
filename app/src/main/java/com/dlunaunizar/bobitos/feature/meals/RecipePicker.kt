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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

// Selector de receta del editor de comidas, con buscador. Cada fila: título y «Mía»/«Común» (· categoría).
@Composable
internal fun RecipePickerDialog(recipes: List<Recipe>, onPick: (Recipe) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(recipes, query) { recipes.forPicker(query) }
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
                if (shown.isEmpty()) {
                    Text(
                        text = stringResource(R.string.recipes_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(shown, key = Recipe::id) { recipe ->
                            RecipePickerRow(recipe = recipe, onClick = { onPick(recipe) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun RecipePickerRow(recipe: Recipe, onClick: () -> Unit) {
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
    }
}
