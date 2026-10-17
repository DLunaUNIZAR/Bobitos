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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.BobitosDates
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.messageRes
import com.dlunaunizar.bobitos.core.designsystem.component.AppDatePickerDialog
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosDialog
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosFormSheet
import com.dlunaunizar.bobitos.core.designsystem.component.CreatedUndoEffect
import com.dlunaunizar.bobitos.core.designsystem.component.EditorSaveEffect
import com.dlunaunizar.bobitos.core.designsystem.component.ErrorState
import com.dlunaunizar.bobitos.core.designsystem.component.FeedbackSnackbarEffect
import com.dlunaunizar.bobitos.core.designsystem.component.LoadingState
import com.dlunaunizar.bobitos.core.designsystem.component.LocalSnackbarHostState
import com.dlunaunizar.bobitos.core.designsystem.component.ModuleScaffold
import com.dlunaunizar.bobitos.core.designsystem.component.SearchField
import com.dlunaunizar.bobitos.core.designsystem.component.discardFailure
import com.dlunaunizar.bobitos.core.designsystem.component.errorSupportingText
import com.dlunaunizar.bobitos.core.designsystem.component.launchUndo
import com.dlunaunizar.bobitos.core.designsystem.component.rememberEditorSlot
import com.dlunaunizar.bobitos.core.designsystem.component.rememberFormValidationState
import com.dlunaunizar.bobitos.core.designsystem.component.savingOutsideEditor
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.Meal
import com.dlunaunizar.bobitos.core.model.MealSlot
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.feature.common.IngredientReviewDialog
import com.dlunaunizar.bobitos.feature.common.MemberCheckboxes
import com.dlunaunizar.bobitos.feature.common.formatted
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun MealsScreen(
    spaceId: String,
    canWrite: Boolean,
    onOpenRecipes: () -> Unit,
    onOpenIngredients: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MealsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(spaceId) {
        viewModel.observe(spaceId)
        onDispose { viewModel.stopObserving() }
    }

    // El editor sobrevive a una rotación: se guarda el id de la comida y la franja, no el objeto.
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editorMealId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorSlotName by rememberSaveable { mutableStateOf(MealSlot.COMIDA.name) }
    val openEditor: (Meal?, MealSlot) -> Unit = { meal, slot ->
        editorMealId = meal?.id
        editorSlotName = slot.name
        editorOpen = true
    }
    var mealToDelete by remember { mutableStateOf<Meal?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var dayMenuExpanded by remember { mutableStateOf(false) }
    var duplicateDayPicker by remember { mutableStateOf(false) }
    val members = (state.members as? UiState.Content)?.value.orEmpty()
    val actionsEnabled = canWrite && !state.isSaving
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.meals_undo_deleted)
    val undoLabel = stringResource(R.string.undo)

    ModuleScaffold(
        modifier = modifier.fillMaxSize(),
        saving = savingOutsideEditor(state.isSaving, editorOpen),
        floatingActionButton = {
            NewMealFab(
                visible = canWrite,
                onClick = { openEditor(null, defaultMealSlot(LocalTime.now().hour)) },
            )
        },
    ) { padding ->
        val snackbar = LocalSnackbarHostState.current
        FeedbackSnackbarEffect(
            error = state.snackbarError(),
            notice = state.notice,
            editorSave = state.editorSave,
            editorOpen = editorOpen,
            onShown = viewModel::clearFeedback,
            text = { stringResource(it.stringResourceId) },
        )
        CreatedUndoEffect(state.created, viewModel::consumeCreated) { viewModel.undoCreate(it.id) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                IconButton(onClick = onOpenIngredients) {
                    Icon(Icons.Rounded.Kitchen, contentDescription = stringResource(R.string.ingredients_open))
                }
                TextButton(onClick = onOpenRecipes) {
                    Icon(Icons.Rounded.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.recipes_open))
                }
                if (canWrite) {
                    Box {
                        IconButton(onClick = { dayMenuExpanded = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = dayMenuExpanded, onDismissRequest = { dayMenuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.meals_duplicate_day)) },
                                onClick = {
                                    dayMenuExpanded = false
                                    duplicateDayPicker = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.meals_duplicate_week)) },
                                onClick = {
                                    dayMenuExpanded = false
                                    viewModel.duplicateWeekToNext()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.meals_add_day_to_shopping)) },
                                onClick = {
                                    dayMenuExpanded = false
                                    viewModel.addDayIngredientsToShopping()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.meals_add_week_to_shopping)) },
                                onClick = {
                                    dayMenuExpanded = false
                                    viewModel.addWeekIngredientsToShopping()
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            when (val mealsState = state.meals) {
                UiState.Loading -> LoadingState(Modifier.weight(1f))
                is UiState.Error -> ErrorState(
                    Modifier.weight(1f),
                    message = stringResource(mealsState.failure.messageRes()),
                    onRetry = { viewModel.observe(spaceId) },
                )
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
                        contentPadding = PaddingValues(top = 8.dp, bottom = Spacing.fabClearance),
                    ) {
                        MealSlot.entries.forEach { slot ->
                            item(key = slot.name) {
                                MealSlotSection(
                                    slot = slot,
                                    meals = queriedMeals.filter { it.slot == slot },
                                    recipes = state.recipes,
                                    canWrite = canWrite,
                                    actionsEnabled = actionsEnabled,
                                    onAdd = { openEditor(null, slot) },
                                    onEdit = { meal -> openEditor(meal, meal.slot) },
                                    onDelete = { mealToDelete = it },
                                    onAddToShopping = { viewModel.addIngredientsToShopping(it) },
                                    onToggleCooked = {
                                        if (it.cooked) viewModel.unmarkCooked(it) else viewModel.markCooked(it)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        mealToDelete?.let { meal ->
            DeleteMealDialog(
                meal = meal,
                enabled = actionsEnabled,
                onConfirm = {
                    viewModel.deleteMeal(meal.id)
                    mealToDelete = null
                    scope.launchUndo(snackbar, deletedMessage, undoLabel) {
                        viewModel.restoreMeal(meal)
                    }
                },
                onDismiss = { mealToDelete = null },
            )
        }
    }

    val closeEditor = {
        // Cerrar tras un guardado fallido descarta ese error (ya se veía en la hoja).
        state.editorSave.discardFailure(viewModel::clearFeedback)
        editorOpen = false
        editorMealId = null
    }
    EditorSaveEffect(
        status = state.editorSave,
        editorOpen = editorOpen,
        onClose = closeEditor,
        onConsume = viewModel::consumeEditorSave,
    )
    MealEditorHost(
        open = editorOpen,
        mealId = editorMealId,
        slotName = editorSlotName,
        meals = (state.meals as? UiState.Content)?.value.orEmpty(),
        mealsLoaded = state.meals is UiState.Content,
        members = members,
        recipes = state.recipes,
        pantryIds = state.pantryIds,
        saving = state.isSaving,
        errorMessage = state.error?.takeIf { state.editorSave == EditorSaveStatus.FAILED }
            ?.let { stringResource(it.stringResourceId) },
        canWrite = canWrite,
        onClose = closeEditor,
        onSave = { meal, slot, name, participantIds, recipeId, cookId ->
            if (meal == null) {
                viewModel.addMeal(state.focusedDate, slot, name, participantIds, recipeId, cookId)
            } else {
                viewModel.updateMeal(meal.id, meal.date, slot, name, participantIds, recipeId, cookId)
            }
        },
    )

    if (duplicateDayPicker) {
        AppDatePickerDialog(
            initialDate = state.focusedDate.plusDays(1),
            onConfirm = { target ->
                viewModel.duplicateDay(target)
                duplicateDayPicker = false
            },
            onDismiss = { duplicateDayPicker = false },
        )
    }

    state.ingredientReview?.let { rows ->
        IngredientReviewDialog(
            rows = rows,
            onConfirm = viewModel::confirmIngredientReview,
            onDismiss = viewModel::dismissIngredientReview,
        )
    }

    state.cooked?.let { review -> CookedReviewDialog(review, state, viewModel) }
}

// «Has cocinado …»; el error de una escritura fallida sale dentro del diálogo, no en el aviso de la pantalla.
@Composable
private fun CookedReviewDialog(review: CookedReview, state: MealsUiState, viewModel: MealsViewModel) {
    CookedDialog(
        review = review,
        saving = state.isSaving,
        // Si «Listo» ha fallado porque alguien ha cambiado un artículo de la lista, lo nombra.
        errorMessage = state.cookedErrorItem?.let { stringResource(R.string.meals_error_cooked_item_changed, it) }
            ?: state.error?.let { stringResource(it.stringResourceId) },
        onConfirm = viewModel::confirmCooked,
        onDismiss = viewModel::dismissCooked,
    )
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
    onToggleCooked: (Meal) -> Unit,
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
                    onToggleCooked = { onToggleCooked(meal) },
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
    onToggleCooked: () -> Unit,
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
                meal.cookName?.let { cook ->
                    Text(
                        text = stringResource(R.string.meals_cook_value, cook),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!ingredients.isNullOrEmpty()) {
                    Text(
                        text = ingredients.joinToString(", ") { it.formatted() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (meal.cooked) {
                    Text(
                        text = stringResource(R.string.meals_cooked_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
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
                            text = {
                                Text(
                                    stringResource(
                                        if (meal.cooked) R.string.meals_mark_uncooked else R.string.meals_mark_cooked,
                                    ),
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onToggleCooked()
                            },
                        )
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

// Muestra el editor de comida (nueva o existente). Si la comida que se editaba ya no existe, lo cierra
// (mientras la lista carga, espera).
@Composable
private fun MealEditorHost(
    open: Boolean,
    mealId: String?,
    slotName: String,
    meals: List<Meal>,
    mealsLoaded: Boolean,
    members: List<SpaceMember>,
    recipes: List<Recipe>,
    pantryIds: Set<String>?,
    saving: Boolean,
    errorMessage: String?,
    canWrite: Boolean,
    onClose: () -> Unit,
    onSave: (Meal?, MealSlot, String, List<String>, String?, String?) -> Unit,
) {
    val slot = rememberEditorSlot(
        open = open,
        id = mealId,
        items = meals.takeIf { mealsLoaded },
        idOf = Meal::id,
        onGone = onClose,
    ) ?: return
    val meal = slot.item
    MealEditor(
        meal = meal,
        initialSlot = MealSlot.valueOf(slotName),
        members = members,
        recipes = recipes,
        pantryIds = pantryIds,
        saving = saving,
        errorMessage = errorMessage,
        canWrite = canWrite,
        onDismiss = onClose,
        onSave = { slot, name, participantIds, recipeId, cookId ->
            onSave(meal, slot, name, participantIds, recipeId, cookId)
        },
    )
}

@Composable
internal fun MealEditor(
    meal: Meal?,
    initialSlot: MealSlot,
    members: List<SpaceMember>,
    recipes: List<Recipe>,
    pantryIds: Set<String>?,
    saving: Boolean,
    errorMessage: String?,
    canWrite: Boolean,
    onDismiss: () -> Unit,
    onSave: (MealSlot, String, List<String>, String?, String?) -> Unit,
) {
    val initial = MealDraft.of(meal, initialSlot)
    var draft by rememberSaveable(meal?.id) { mutableStateOf(initial) }
    // Como el borrador, sobrevive a una rotación (la búsqueda y el chip del selector ya se guardan en el diálogo).
    var pickerOpen by rememberSaveable(meal?.id) { mutableStateOf(false) }
    val validation = MealsValidation.validate(draft.name)
    val form = rememberFormValidationState()
    val nameFocus = remember { FocusRequester() }
    val nameError = form.visible(validation)

    BobitosFormSheet(
        title = stringResource(if (meal == null) R.string.meals_add_title else R.string.meals_edit_title),
        confirmLabel = stringResource(R.string.save),
        canSubmit = canWrite,
        saving = saving,
        dirty = { draft != initial },
        errorMessage = errorMessage,
        onDismiss = onDismiss,
        onConfirm = {
            form.submit(
                isValid = validation == null,
                onValid = {
                    onSave(
                        draft.slot,
                        draft.name,
                        draft.selectedIds,
                        draft.recipeId,
                        draft.cookId?.takeIf { it in draft.selectedIds },
                    )
                },
                onInvalid = { nameFocus.requestFocus() },
            )
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MealSlot.entries.forEach { option ->
                FilterChip(
                    selected = draft.slot == option,
                    onClick = { draft = draft.copy(slot = option) },
                    label = { Text(stringResource(option.labelRes)) },
                )
            }
        }
        OutlinedTextField(
            value = draft.name,
            onValueChange = { draft = draft.withName(it) },
            label = { Text(stringResource(R.string.meals_name_label)) },
            supportingText = errorSupportingText(nameError?.let { stringResource(it.stringResourceId) }),
            isError = nameError != null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
        )
        if (recipes.isNotEmpty()) {
            TextButton(onClick = { pickerOpen = true }) {
                Icon(Icons.Rounded.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.meals_choose_recipe))
            }
        }
        MealParticipants(members = members, draft = draft, onDraft = { draft = it })
    }

    if (pickerOpen) {
        RecipePickerDialog(
            recipes = recipes,
            pantryIds = pantryIds,
            onPick = { recipe ->
                draft = draft.withRecipe(recipe.title, recipe.id)
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

// Participantes (casillas) y, si hay alguno, el cocinero entre ellos.
@Composable
private fun MealParticipants(members: List<SpaceMember>, draft: MealDraft, onDraft: (MealDraft) -> Unit) {
    if (members.isEmpty()) return
    var cookMenu by remember { mutableStateOf(false) }
    MemberCheckboxes(
        label = stringResource(R.string.meals_participants_label),
        members = members,
        selectedIds = draft.selectedIds,
        onToggle = { userId, selected -> onDraft(draft.withParticipant(userId, selected)) },
    )
    if (draft.selectedIds.isEmpty()) return
    Text(
        text = stringResource(R.string.meals_cook_label),
        style = MaterialTheme.typography.labelLarge,
    )
    Box {
        TextButton(onClick = { cookMenu = true }) {
            Text(
                members.firstOrNull { it.userId == draft.cookId }?.displayName
                    ?: stringResource(R.string.meals_no_cook),
            )
        }
        DropdownMenu(expanded = cookMenu, onDismissRequest = { cookMenu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.meals_no_cook)) },
                onClick = {
                    onDraft(draft.copy(cookId = null))
                    cookMenu = false
                },
            )
            members.filter { it.userId in draft.selectedIds }.forEach { member ->
                DropdownMenuItem(
                    text = { Text(member.displayName) },
                    onClick = {
                        onDraft(draft.copy(cookId = member.userId))
                        cookMenu = false
                    },
                )
            }
        }
    }
}

@Composable
private fun NewMealFab(visible: Boolean, onClick: () -> Unit) {
    if (!visible) return
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
        text = { Text(stringResource(R.string.meals_add_title)) },
    )
}

@Composable
private fun DeleteMealDialog(meal: Meal, enabled: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    BobitosDialog(
        title = stringResource(R.string.meals_delete_title),
        message = stringResource(R.string.meals_delete_body, meal.name),
        confirmLabel = stringResource(R.string.meals_delete),
        destructive = true,
        confirmEnabled = enabled,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

// Coincidencia por texto (nombre de la comida) para el buscador; en blanco no filtra.
private fun Meal.matchesQuery(query: String): Boolean =
    query.isBlank() || name.contains(query.trim(), ignoreCase = true)

private fun LocalDate.formatHeader(): String = BobitosDates.longDay(this)

private fun LocalDate.dayInitial(): String = BobitosDates.dayInitial(dayOfWeek)

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
        MealUiMessage.ShoppingChanged -> R.string.meals_error_shopping_changed
        MealUiMessage.CookedShoppingChanged -> R.string.meals_error_cooked_shopping_changed
        MealUiMessage.CookedNotApplied -> R.string.meals_error_cooked_not_applied
        MealUiMessage.SaveTimeout -> R.string.write_timeout
        MealUiMessage.MealAdded -> R.string.meals_notice_added
        MealUiMessage.MealUpdated -> R.string.meals_notice_updated
        MealUiMessage.MealDeleted -> R.string.meals_notice_deleted
        MealUiMessage.MealsDuplicated -> R.string.meals_notice_duplicated
        MealUiMessage.IngredientsAddedToShopping -> R.string.meals_notice_ingredients_added
        MealUiMessage.NothingToAddToShopping -> R.string.meals_notice_nothing_to_add
        MealUiMessage.MealCooked -> R.string.meals_notice_cooked
        MealUiMessage.CookedReviewApplied -> R.string.meals_notice_cooked_review
    }
