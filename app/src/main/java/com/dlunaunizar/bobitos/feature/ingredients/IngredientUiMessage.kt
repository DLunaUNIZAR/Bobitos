package com.dlunaunizar.bobitos.feature.ingredients

import androidx.annotation.StringRes
import com.dlunaunizar.bobitos.R

// Mensajes de UI del catálogo de ingredientes y sus marcas (mismo patrón que RecipeUiMessage).
enum class IngredientUiMessage {
    NameRequired,
    NameTooLong,
    CategoryTooLong,
    UnitTooLong,
    AlreadyExists,
    BrandNameRequired,
    BrandNameTooLong,
    BarcodeTooLong,
    BrandNotFound,
    NotAuthenticated,
    EmailNotVerified,
    NotFound,
    PermissionDenied,
    NetworkError,
    UnexpectedError,
    Saved,
    Deleted,
    PrefSaved,
    PrefCleared,
    BrandSaved,
    BrandDeleted,
}

@get:StringRes
internal val IngredientUiMessage.stringResourceId: Int
    get() = when (this) {
        IngredientUiMessage.NameRequired -> R.string.ingredients_error_name_required
        IngredientUiMessage.NameTooLong -> R.string.ingredients_error_name_too_long
        IngredientUiMessage.CategoryTooLong -> R.string.ingredients_error_category_too_long
        IngredientUiMessage.UnitTooLong -> R.string.ingredients_error_unit_too_long
        IngredientUiMessage.AlreadyExists -> R.string.ingredients_error_already_exists
        IngredientUiMessage.BrandNameRequired -> R.string.ingredients_brand_error_name_required
        IngredientUiMessage.BrandNameTooLong -> R.string.ingredients_brand_error_name_too_long
        IngredientUiMessage.BarcodeTooLong -> R.string.ingredients_brand_error_barcode_too_long
        IngredientUiMessage.BrandNotFound -> R.string.ingredients_brand_error_not_found
        IngredientUiMessage.NotFound -> R.string.ingredients_error_not_found
        IngredientUiMessage.NotAuthenticated -> R.string.space_error_not_authenticated
        IngredientUiMessage.EmailNotVerified -> R.string.space_error_email_not_verified
        IngredientUiMessage.PermissionDenied -> R.string.space_error_permission_denied
        IngredientUiMessage.NetworkError -> R.string.space_error_network
        IngredientUiMessage.UnexpectedError -> R.string.space_error_unexpected
        IngredientUiMessage.Saved -> R.string.ingredients_notice_saved
        IngredientUiMessage.Deleted -> R.string.ingredients_notice_deleted
        IngredientUiMessage.PrefSaved -> R.string.ingredients_notice_pref_saved
        IngredientUiMessage.PrefCleared -> R.string.ingredients_notice_pref_cleared
        IngredientUiMessage.BrandSaved -> R.string.ingredients_brand_notice_saved
        IngredientUiMessage.BrandDeleted -> R.string.ingredients_brand_notice_deleted
    }
