package com.dlunaunizar.bobitos.feature.meals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.designsystem.component.ErrorState
import com.dlunaunizar.bobitos.core.designsystem.component.LoadingState
import com.dlunaunizar.bobitos.core.designsystem.component.LocalSnackbarHostState
import com.dlunaunizar.bobitos.core.designsystem.component.SearchField
import com.dlunaunizar.bobitos.core.designsystem.component.launchUndo
import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.Meal
import com.dlunaunizar.bobitos.core.model.MealSlot
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.SpaceMember
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MealsScreen(
    spaceId: String,
    canWrite: Boolean,
    onOpenRecipes: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MealsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(spaceId) {
        viewModel.observe(spaceId)
        onDispose { viewModel.stopObserving() }
    }

    var editor by remember { mutableStateOf<MealEditorRequest?>(null) }
    var mealToDelete by remember { mutableStateOf<Meal?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val members = (state.members as? UiState.Content)?.value.orEmpty()
    val actionsEnabled = canWrite && !state.isSaving
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.meals_undo_deleted)
    val undoLabel = stringResource(R.string.undo)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        WeekSelector(
            weekDays = state.weekDays,
            focusedDate = state.focusedDate,
            onPrevious = viewModel::previousWeek,
            onNext = viewModel::nextWeek,
            onSelectDay = viewModel::selectDay,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.focusedDate.formatHeader(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenRecipes) {
                Icon(Icons.Rounded.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.recipes_open))
            }
        }
        MealsFeedback(state, viewModel::clearFeedback)
        Spacer(Modifier.height(8.dp))

        when (val mealsState = state.meals) {
            UiState.Loading -> LoadingState(Modifier.weight(1f))
            is UiState.Error -> ErrorState(Modifier.weight(1f), message = mealsState.message)
            is UiState.Content -> {
                val dayMeals = mealsState.value.filter { it.date == state.focusedDate }
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    visible = dayMeals.isNotEmpty(),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                val queriedMeals = dayMeals.filter { it.matchesQuery(query) }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                ) {
                    MealSlot.entries.forEach { slot ->
                        item(key = slot.name) {
                            MealSlotSection(
                                slot = slot,
                                meals = queriedMeals.filter { it.slot == slot },
                                recipes = state.recipes,
                                canWrite = canWrite,
                                actionsEnabled = actionsEnabled,
                                onAdd = { editor = MealEditorRequest(slot = slot, meal = null) },
                                onEdit = { meal -> editor = MealEditorRequest(slot = meal.slot, meal = meal) },
                                onDelete = { mealToDelete = it },
                                onAddToShopping = { viewModel.addIngredientsToShopping(it) },
                            )
                        }
                    }
                }
            }
        }
    }

    editor?.let { request ->
        MealEditor(
            request = request,
            members = members,
            recipes = state.recipes,
            saving = state.isSaving,
            canWrite = canWrite,
            onDismiss = { editor = null },
            onSave = { name, participantIds, recipeId ->
                val meal = request.meal
                if (meal == null) {
                    viewModel.addMeal(state.focusedDate, request.slot, name, participantIds, recipeId)
                } else {
                    viewModel.updateMeal(meal.id, meal.date, meal.slot, name, participantIds, recipeId)
                }
                editor = null
            },
        )
    }

    mealToDelete?.let { meal ->
        AlertDialog(
            onDismissRequest = { mealToDelete = null },
            title = { Text(stringResource(R.string.meals_delete_title)) },
            text = { Text(stringResource(R.string.meals_delete_body, meal.name)) },
            confirmButton = {
                TextButton(
                    enabled = actionsEnabled,
                    onClick = {
                        viewModel.deleteMeal(meal.id)
                        mealToDelete = null
                        scope.launchUndo(snackbar, deletedMessage, undoLabel) {
                            viewModel.addMeal(meal.date, meal.slot, meal.name, meal.participantIds, meal.recipeId)
                        }
                    },
                ) { Text(stringResource(R.string.meals_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { mealToDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun WeekSelector(
    weekDays: List<LocalDate>,
    focusedDate: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectDay: (LocalDate) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.meals_week_previous))
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            weekDays.forEach { day ->
                DayChip(
                    date = day,
                    selected = day == focusedDate,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectDay(day) },
                )
            }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.meals_week_next))
        }
    }
}

@Composable
private fun DayChip(date: LocalDate, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(container)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = date.dayInitial(), style = MaterialTheme.typography.labelSmall, color = content)
        Text(text = date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, color = content)
    }
}

@Composable
private fun MealSlotSection(
    slot: MealSlot,
    meals: List<Meal>,
    recipes: List<Recipe>,
    canWrite: Boolean,
    actionsEnabled: Boolean,
    onAdd: () -> Unit,
    onEdit: (Meal) -> Unit,
    onDelete: (Meal) -> Unit,
    onAddToShopping: (Meal) -> Unit,
) {
    val ingredientsByRecipe = recipes.associate { it.id to it.ingredients }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(slot.icon, contentDescription = null, tint = slot.accent())
            Text(
                text = stringResource(slot.labelRes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (canWrite) {
                TextButton(enabled = actionsEnabled, onClick = onAdd) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.meals_add))
                }
            }
        }
        if (meals.isEmpty()) {
            Text(
                text = stringResource(R.string.meals_slot_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            meals.forEach { meal ->
                MealCard(
                    meal = meal,
                    ingredients = meal.recipeId?.let { ingredientsByRecipe[it] },
                    enabled = actionsEnabled,
                    canWrite = canWrite,
                    onEdit = { onEdit(meal) },
                    onDelete = { onDelete(meal) },
                    onAddToShopping = { onAddToShopping(meal) },
                )
            }
        }
    }
}

@Composable
private fun MealCard(
    meal: Meal,
    ingredients: List<Ingredient>?,
    enabled: Boolean,
    canWrite: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddToShopping: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meal.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meal.participantNames.takeIf(List<String>::isNotEmpty)?.joinToString(", ")
                        ?: stringResource(R.string.meals_no_participants),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!ingredients.isNullOrEmpty()) {
                    Text(
                        text = ingredients.joinToString(", ") { it.formatted() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (canWrite) {
                Box {
                    IconButton(enabled = enabled, onClick = { menuExpanded = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (!ingredients.isNullOrEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.meals_add_ingredients_to_shopping)) },
                                onClick = {
                                    menuExpanded = false
                                    onAddToShopping()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.meals_edit)) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.meals_delete)) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MealEditor(
    request: MealEditorRequest,
    members: List<SpaceMember>,
    recipes: List<Recipe>,
    saving: Boolean,
    canWrite: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, List<String>, String?) -> Unit,
) {
    val meal = request.meal
    var name by remember(meal?.id) { mutableStateOf(meal?.name.orEmpty()) }
    var recipeId by remember(meal?.id) { mutableStateOf(meal?.recipeId) }
    var selected by remember(meal?.id) { mutableStateOf(meal?.participantIds?.toSet().orEmpty()) }
    var pickerOpen by remember { mutableStateOf(false) }
    val validation = MealsValidation.validate(name)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(if (meal == null) R.string.meals_add_title else R.string.meals_edit_title),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(request.slot.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        recipeId = null
                    },
                    label = { Text(stringResource(R.string.meals_name_label)) },
                    supportingText = {
                        if (validation != null) Text(stringResource(validation.stringResourceId))
                    },
                    isError = validation != null,
                    singleLine = true,
                )
                if (recipes.isNotEmpty()) {
                    TextButton(onClick = { pickerOpen = true }) {
                        Icon(Icons.Rounded.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.meals_choose_recipe))
                    }
                }
                if (members.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.meals_participants_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    members.forEach { member ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = member.userId in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + member.userId else selected - member.userId
                                },
                            )
                            Text(member.displayName)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = validation == null && canWrite && !saving,
                onClick = { onSave(name, selected.toList(), recipeId) },
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )

    if (pickerOpen) {
        RecipePickerDialog(
            recipes = recipes,
            onPick = { recipe ->
                name = recipe.title
                recipeId = recipe.id
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

@Composable
private fun RecipePickerDialog(recipes: List<Recipe>, onPick: (Recipe) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.meals_recipe_picker_title)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(recipes, key = Recipe::id) { recipe ->
                    Text(
                        text = recipe.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(recipe) }
                            .padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun MealsFeedback(state: MealsUiState, onDismiss: () -> Unit) {
    val message = state.error ?: state.notice
    if (message == null && !state.isSaving) return
    val isError = state.error != null
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (state.isSaving) {
                    stringResource(R.string.write_saving)
                } else {
                    stringResource(message!!.stringResourceId)
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!state.isSaving) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
            }
        }
    }
}

private data class MealEditorRequest(val slot: MealSlot, val meal: Meal?)

private val HEADER_FORMAT = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es"))

// «300 g Arroz» o «Sal» (omite cantidad/unidad ausentes).
private fun Ingredient.formatted(): String = listOfNotNull(quantity, unit, name).joinToString(" ")

// Coincidencia por texto (nombre de la comida) para el buscador; en blanco no filtra.
private fun Meal.matchesQuery(query: String): Boolean =
    query.isBlank() || name.contains(query.trim(), ignoreCase = true)

private fun LocalDate.formatHeader(): String =
    format(HEADER_FORMAT).replaceFirstChar { it.uppercase(Locale.forLanguageTag("es")) }

private fun LocalDate.dayInitial(): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> "L"
    DayOfWeek.TUESDAY -> "M"
    DayOfWeek.WEDNESDAY -> "X"
    DayOfWeek.THURSDAY -> "J"
    DayOfWeek.FRIDAY -> "V"
    DayOfWeek.SATURDAY -> "S"
    DayOfWeek.SUNDAY -> "D"
}

private val MealUiMessage.stringResourceId: Int
    get() = when (this) {
        MealUiMessage.NameRequired -> R.string.meals_error_name_required
        MealUiMessage.NameTooLong -> R.string.meals_error_name_too_long
        MealUiMessage.InvalidParticipants -> R.string.meals_error_invalid_participants
        MealUiMessage.NotAuthenticated -> R.string.space_error_not_authenticated
        MealUiMessage.EmailNotVerified -> R.string.space_error_email_not_verified
        MealUiMessage.SpaceNotFound -> R.string.space_error_not_found
        MealUiMessage.MealNotFound -> R.string.meals_error_not_found
        MealUiMessage.PermissionDenied -> R.string.space_error_permission_denied
        MealUiMessage.NetworkError -> R.string.space_error_network
        MealUiMessage.UnexpectedError -> R.string.space_error_unexpected
        MealUiMessage.MealAdded -> R.string.meals_notice_added
        MealUiMessage.MealUpdated -> R.string.meals_notice_updated
        MealUiMessage.MealDeleted -> R.string.meals_notice_deleted
        MealUiMessage.IngredientsAddedToShopping -> R.string.meals_notice_ingredients_added
    }
