package com.dlunaunizar.bobitos.feature.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosFormSheet
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosInfoSheet
import com.dlunaunizar.bobitos.core.designsystem.component.InfoChip
import com.dlunaunizar.bobitos.core.designsystem.component.InfoSheetAction
import com.dlunaunizar.bobitos.core.designsystem.component.rememberEditorSlot
import com.dlunaunizar.bobitos.core.designsystem.component.rememberFormValidationState
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.Recipe
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
    imported: RecipeDraft?,
    actions: RecipeSheetActions,
) {
    val mine = state.mine as? UiState.Content
    val global = state.global as? UiState.Content
    // Las dos listas juntas; null mientras alguna carga.
    val all = if (mine != null && global != null) mine.value + global.value else null
    val detail = rememberEditorSlot(
        open = detailRecipeId != null,
        id = detailRecipeId,
        items = all,
        idOf = Recipe::id,
        onGone = actions.onCloseDetail,
    )?.item
    val editor = rememberEditorSlot(
        open = editorOpen,
        id = editorRecipeId,
        items = all,
        idOf = Recipe::id,
        onGone = actions.onCloseEditor,
    )
    detail?.let { recipe ->
        RecipeDetailSheet(
            recipe = recipe,
            detailActions = recipeDetailActions(recipe, canWrite, state.isAdmin),
            canAddToShopping = canWrite && canAddToShopping,
            deleteEnabled = !state.save.isSaving,
            actions = actions,
        )
    }
    editor?.let { slot ->
        val editing = slot.item
        RecipeEditor(
            recipe = editing,
            imported = imported,
            saving = state.save.isSaving,
            errorMessage = state.save.editorError?.let { stringResource(it.stringResourceId) },
            canWrite = canWrite,
            isAdmin = state.isAdmin,
            onDismiss = actions.onCloseEditor,
            onSave = { draft -> actions.onSave(editing, draft) },
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
    val onSave: (Recipe?, RecipeDraft) -> Unit,
)

@Composable
private fun RecipeDetailSheet(
    recipe: Recipe,
    detailActions: RecipeDetailActions,
    canAddToShopping: Boolean,
    // Falso mientras otra escritura está en curso: borrar sería un toque que no hace nada y cerraría la hoja.
    deleteEnabled: Boolean,
    actions: RecipeSheetActions,
) {
    val sheetActions = recipeSheetActions(detailActions, canAddToShopping)
    val labels = mapOf(
        RecipeSheetAction.ADD_TO_SHOPPING to stringResource(R.string.recipes_add_to_shopping),
        RecipeSheetAction.FORK to stringResource(R.string.recipes_fork),
        RecipeSheetAction.EDIT to stringResource(R.string.recipes_edit),
        RecipeSheetAction.DELETE to stringResource(R.string.recipes_delete),
    )
    val toInfoAction = { action: RecipeSheetAction ->
        InfoSheetAction(
            label = labels.getValue(action),
            onClick = {
                when (action) {
                    RecipeSheetAction.ADD_TO_SHOPPING -> actions.onAddToShopping(recipe)
                    RecipeSheetAction.FORK -> actions.onFork(recipe)
                    RecipeSheetAction.EDIT -> actions.onEdit(recipe)
                    RecipeSheetAction.DELETE -> actions.onDelete(recipe)
                }
            },
            enabled = action != RecipeSheetAction.DELETE || deleteEnabled,
        )
    }
    BobitosInfoSheet(
        title = recipe.title,
        onDismiss = actions.onCloseDetail,
        primaryAction = sheetActions.primary?.let(toInfoAction),
        secondaryActions = sheetActions.secondary.map(toInfoAction),
    ) {
        RecipeSummary(recipe)
        // «Sin descripción.» solo si tampoco hay pasos: con pasos, la «Preparación» ya explica la receta.
        val description = recipe.description
        if (description != null) {
            Text(description)
        } else if (recipe.steps.isNullOrEmpty()) {
            Text(stringResource(R.string.recipes_no_description))
        }
        IngredientsList(recipe.ingredients)
        StepsList(recipe.steps)
    }
}

@Composable
private fun RecipeEditor(
    recipe: Recipe?,
    imported: RecipeDraft?,
    saving: Boolean,
    errorMessage: String?,
    canWrite: Boolean,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onSave: (RecipeDraft) -> Unit,
) {
    // Al crear a mano `recipe` e `imported` son null; al importar, los valores iniciales vienen de la web.
    val initial = imported ?: RecipeDraft.of(recipe, null)
    var draft by rememberSaveable(recipe?.id, imported?.sourceUrl) {
        mutableStateOf(initial)
    }
    val validation = draft.validate()
    val form = rememberFormValidationState()
    val titleFocus = remember { FocusRequester() }
    val descriptionFocus = remember { FocusRequester() }
    val categoryFocus = remember { FocusRequester() }
    // El toggle de catálogo común solo se ofrece al crear (la visibilidad de una receta existente está
    // congelada por las reglas) y solo a quien puede publicar GLOBAL.
    val canChooseGlobal = isAdmin && recipe == null

    BobitosFormSheet(
        title = stringResource(if (recipe == null) R.string.recipes_add_title else R.string.recipes_edit_title),
        confirmLabel = stringResource(R.string.save),
        canSubmit = canWrite,
        saving = saving,
        dirty = { draft != initial },
        errorMessage = errorMessage,
        onDismiss = onDismiss,
        onConfirm = {
            form.submit(
                isValid = validation == null,
                onValid = { onSave(draft) },
                onInvalid = {
                    when (validation) {
                        RecipeUiMessage.DescriptionTooLong -> descriptionFocus
                        RecipeUiMessage.CategoryTooLong -> categoryFocus
                        else -> titleFocus
                    }.requestFocus()
                },
            )
        },
    ) {
        RecipeTextFields(
            draft = draft,
            validation = form.visible(validation),
            focus = RecipeFocus(titleFocus, descriptionFocus, categoryFocus),
            onDraft = { draft = it },
        )
        ServingsAndTimeFields(draft = draft, onDraft = { draft = it })
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
        StepsEditor(draft = draft, onDraft = { draft = it })
    }
}

private class RecipeFocus(val title: FocusRequester, val description: FocusRequester, val category: FocusRequester)

@Composable
private fun RecipeTextFields(
    draft: RecipeDraft,
    validation: RecipeUiMessage?,
    focus: RecipeFocus,
    onDraft: (RecipeDraft) -> Unit,
) {
    OutlinedTextField(
        value = draft.title,
        onValueChange = { onDraft(draft.copy(title = it)) },
        label = { Text(stringResource(R.string.recipes_title_label)) },
        supportingText = {
            if (validation.isTitleError()) Text(stringResource(validation!!.stringResourceId))
        },
        isError = validation.isTitleError(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().focusRequester(focus.title),
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
        modifier = Modifier.fillMaxWidth().focusRequester(focus.description),
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
        modifier = Modifier.fillMaxWidth().focusRequester(focus.category),
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

// «4 raciones · 40 min» y la categoría, como fichas bajo el título; cada una sale sola si falta y nada si faltan todas.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeSummary(recipe: Recipe) {
    val servings = recipe.servings?.let { pluralStringResource(R.plurals.recipes_servings, it, it) }
    val minutes = recipe.totalMinutes?.let { stringResource(R.string.recipes_minutes, it) }
    val parts = listOfNotNull(servings, minutes, recipe.category?.takeIf(String::isNotBlank))
    if (parts.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) { parts.forEach { InfoChip(it) } }
}

@Composable
private fun StepsList(steps: List<String>?) {
    if (steps.isNullOrEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.recipes_steps_section),
            style = MaterialTheme.typography.titleSmall,
        )
        steps.forEachIndexed { index, step ->
            Text(
                text = stringResource(R.string.recipes_step_numbered, index + 1, step),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun IngredientsEditor(rows: List<IngredientRow>, onRowsChange: (List<IngredientRow>) -> Unit) {
    // Las filas se pintan por posición: al quitar una, el foco no puede quedarse en la siguiente.
    val focusManager = LocalFocusManager.current
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
                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        onRowsChange(rows.filterIndexed { i, _ -> i != index })
                    },
                ) {
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
