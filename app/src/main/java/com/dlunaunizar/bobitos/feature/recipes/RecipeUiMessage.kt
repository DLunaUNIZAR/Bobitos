package com.dlunaunizar.bobitos.feature.recipes

// Mensajes de UI del recetario: errores de validación, fallos del repositorio y avisos de éxito
// (mismo patrón que ShoppingUiMessage). La pantalla los mapea a recursos de string.
enum class RecipeUiMessage {
    TitleRequired,
    TitleTooLong,
    DescriptionTooLong,
    CategoryTooLong,
    TooManySteps,
    StepTooLong,
    ServingsInvalid,
    TotalMinutesInvalid,
    NotAuthenticated,
    EmailNotVerified,
    RecipeNotFound,
    PermissionDenied,
    NetworkError,
    UnexpectedError,
    ShoppingChanged,
    SaveTimeout,
    RecipeSaved,
    RecipeDeleted,
    ImportInvalidUrl,
    ImportNetwork,
    ImportNotHtml,
    ImportNoRecipe,
    ImportTooLarge,
    AddedToShopping,
    NothingToAddToShopping,
    NoIngredients,
}

// El diálogo de importar solo muestra y consume sus propios errores.
internal val RecipeUiMessage.isImport: Boolean get() = this in IMPORT_MESSAGES

private val IMPORT_MESSAGES = setOf(
    RecipeUiMessage.ImportInvalidUrl,
    RecipeUiMessage.ImportNetwork,
    RecipeUiMessage.ImportNotHtml,
    RecipeUiMessage.ImportNoRecipe,
    RecipeUiMessage.ImportTooLarge,
)

/** El diálogo de importar tapa la pantalla salvo que haya un error pendiente que no es de importación. */
internal fun RecipeUiMessage?.coveredByImportDialog(): Boolean = this == null || isImport

internal fun importDialogCovers(showImport: Boolean, error: RecipeUiMessage?): Boolean =
    showImport && error.coveredByImportDialog()
