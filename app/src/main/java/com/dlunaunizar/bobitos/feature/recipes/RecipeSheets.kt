package com.dlunaunizar.bobitos.feature.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosFormSheet
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosInfoSheet
import com.dlunaunizar.bobitos.core.designsystem.component.rememberEditorItem
import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import com.dlunaunizar.bobitos.feature.common.formatted

private const val MAX_INGREDIENT_ROWS = 50

/**
 * Detalle y editor de receta. Guardan solo ids (y el borrador importado, como lista de textos) para
 * sobrevivir a una rotación; mantienen la receta mientras las listas recargan y, si ya no existe, se cierran.
 */
@Composable
internal fun RecipeSheetsHost(
    state: RecipesUiState,
    canWrite: Boolean,
    canAddToShopping: Boolean,
    detailRecipeId: String?,
    editorOpen: Boolean,
    editorRecipeId: String?,
    importedSaved: List<Any?>?,
    actions: RecipeSheetActions,
) {
    val mine = state.mine as? UiState.Content
    val global = state.global as? UiState.Content
    val loaded = mine != null && global != null
    val all = mine?.value.orEmpty() + global?.value.orEmpty()
    val detail =
        rememberEditorItem(detailRecipeId, detailRecipeId?.let { id -> all.firstOrNull { it.id == id } }, loaded)
    val editing =
        rememberEditorItem(editorRecipeId, editorRecipeId?.let { id -> all.firstOrNull { it.id == id } }, loaded)
    val detailGone = detailRecipeId != null && detail == null
    val editorGone = editorRecipeId != null && editing == null
    LaunchedEffect(detailGone, editorGone, loaded) {
        if (loaded && detailGone) actions.onCloseDetail()
        if (loaded && editorGone) actions.onCloseEditor()
    }
    detail?.let { recipe ->
        RecipeDetailSheet(
            recipe = recipe,
            isMine = state.owns(recipe),
            canWrite = canWrite,
            canAddToShopping = canAddToShopping,
            actions = actions,
        )
    }
    if (editorOpen && !editorGone) {
        RecipeEditor(
            recipe = editing,
            imported = importedSaved?.let(::recipeDraftFromSaved),
            saving = state.isSaving,
            canWrite = canWrite,
            isAdmin = state.isAdmin,
            onDismiss = actions.onCloseEditor,
            onSave = { visibility, title, description, category, ingredients, sourceUrl ->
                actions.onSave(editing, visibility, title, description, category, ingredients, sourceUrl)
                actions.onCloseEditor()
            },
        )
    }
}

/** Acciones que el anfitrión de recetas delega en la pantalla. */
internal class RecipeSheetActions(
    val onCloseDetail: () -> Unit,
    val onCloseEditor: () -> Unit,
    val onAddToShopping: (Recipe) -> Unit,
    val onEdit: (Recipe) -> Unit,
    val onDelete: (Recipe) -> Unit,
    val onFork: (Recipe) -> Unit,
    val onSave: (Recipe?, RecipeVisibility, String, String?, String?, List<Ingredient>, String?) -> Unit,
)

@Composable
private fun RecipeDetailSheet(
    recipe: Recipe,
    isMine: Boolean,
    canWrite: Boolean,
    canAddToShopping: Boolean,
    actions: RecipeSheetActions,
) {
    BobitosInfoSheet(
        title = recipe.title,
        onDismiss = actions.onCloseDetail,
        actions = {
            if (canWrite && canAddToShopping) {
                TextButton(onClick = { actions.onAddToShopping(recipe) }) {
                    Text(stringResource(R.string.recipes_add_to_shopping))
                }
            }
            if (canWrite && isMine) {
                TextButton(onClick = { actions.onEdit(recipe) }) { Text(stringResource(R.string.recipes_edit)) }
                TextButton(onClick = { actions.onDelete(recipe) }) { Text(stringResource(R.string.recipes_delete)) }
            }
            if (canWrite && !isMine) {
                TextButton(onClick = { actions.onFork(recipe) }) { Text(stringResource(R.string.recipes_fork)) }
            }
        },
    ) {
        Text(recipe.description ?: stringResource(R.string.recipes_no_description))
        recipe.category?.let { Text(stringResource(R.string.recipes_category, it)) }
        IngredientsList(recipe.ingredients)
        Text(
            text = stringResource(R.string.recipes_by, recipe.createdByName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecipeEditor(
    recipe: Recipe?,
    imported: RecipeDraft?,
    saving: Boolean,
    canWrite: Boolean,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onSave: (RecipeVisibility, String, String?, String?, List<Ingredient>, String?) -> Unit,
) {
    // Al crear a mano `recipe` e `imported` son null; al importar, los valores iniciales vienen de la web.
    val initial = imported ?: RecipeDraft.of(recipe, null)
    var draft by rememberSaveable(recipe?.id, imported?.sourceUrl, stateSaver = RecipeDraftSaver) {
        mutableStateOf(initial)
    }
    val validation = RecipesValidation.validate(draft.title, draft.description, draft.category)
    // El toggle de catálogo común solo se ofrece al crear (la visibilidad de una receta existente está
    // congelada por las reglas) y solo a quien puede publicar GLOBAL.
    val canChooseGlobal = isAdmin && recipe == null

    BobitosFormSheet(
        title = stringResource(if (recipe == null) R.string.recipes_add_title else R.string.recipes_edit_title),
        confirmLabel = stringResource(R.string.save),
        confirmEnabled = validation == null && canWrite,
        saving = saving,
        dirty = draft != initial,
        onDismiss = onDismiss,
        onConfirm = {
            val visibility = if (draft.global) RecipeVisibility.GLOBAL else RecipeVisibility.PRIVATE
            onSave(
                visibility,
                draft.title,
                draft.description,
                draft.category,
                draft.ingredients.toIngredients(),
                draft.sourceUrl,
            )
        },
    ) {
        RecipeTextFields(draft = draft, validation = validation, onDraft = { draft = it })
        if (canChooseGlobal) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.recipes_publish_global),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(checked = draft.global, onCheckedChange = { draft = draft.copy(global = it) })
            }
        }
        IngredientsEditor(rows = draft.ingredients, onRowsChange = { draft = draft.copy(ingredients = it) })
    }
}

@Composable
private fun RecipeTextFields(draft: RecipeDraft, validation: RecipeUiMessage?, onDraft: (RecipeDraft) -> Unit) {
    OutlinedTextField(
        value = draft.title,
        onValueChange = { onDraft(draft.copy(title = it)) },
        label = { Text(stringResource(R.string.recipes_title_label)) },
        supportingText = {
            if (validation.isTitleError()) Text(stringResource(validation!!.stringResourceId))
        },
        isError = validation.isTitleError(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.description,
        onValueChange = { onDraft(draft.copy(description = it)) },
        label = { Text(stringResource(R.string.recipes_description_label)) },
        supportingText = {
            if (validation == RecipeUiMessage.DescriptionTooLong) {
                Text(stringResource(validation.stringResourceId))
            }
        },
        isError = validation == RecipeUiMessage.DescriptionTooLong,
        minLines = 2,
        maxLines = 4,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.category,
        onValueChange = { onDraft(draft.copy(category = it)) },
        label = { Text(stringResource(R.string.recipes_category_label)) },
        supportingText = {
            if (validation == RecipeUiMessage.CategoryTooLong) {
                Text(stringResource(validation.stringResourceId))
            }
        },
        isError = validation == RecipeUiMessage.CategoryTooLong,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun IngredientsList(ingredients: List<Ingredient>?) {
    if (ingredients.isNullOrEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(R.string.recipes_ingredients_section),
            style = MaterialTheme.typography.titleSmall,
        )
        ingredients.forEach { ingredient ->
            Text(
                text = stringResource(R.string.recipes_ingredient_bullet, ingredient.formatted()),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun IngredientsEditor(rows: List<IngredientRow>, onRowsChange: (List<IngredientRow>) -> Unit) {
    Text(
        text = stringResource(R.string.recipes_ingredients_section),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
    rows.forEachIndexed { index, row ->
        val change: (IngredientRow) -> Unit = { updated ->
            onRowsChange(rows.mapIndexed { i, current -> if (i == index) updated else current })
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = row.name,
                    onValueChange = { change(row.copy(name = it)) },
                    label = { Text(stringResource(R.string.recipes_ingredient_name)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onRowsChange(rows.filterIndexed { i, _ -> i != index }) }) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.recipes_ingredient_remove),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = row.quantity,
                    onValueChange = { change(row.copy(quantity = it)) },
                    label = { Text(stringResource(R.string.recipes_ingredient_quantity)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = row.unit,
                    onValueChange = { change(row.copy(unit = it)) },
                    label = { Text(stringResource(R.string.recipes_ingredient_unit)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    if (rows.size < MAX_INGREDIENT_ROWS) {
        TextButton(onClick = { onRowsChange(rows + IngredientRow()) }) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Text(stringResource(R.string.recipes_ingredient_add))
        }
    }
}

private fun RecipeUiMessage?.isTitleError(): Boolean =
    this == RecipeUiMessage.TitleRequired || this == RecipeUiMessage.TitleTooLong
