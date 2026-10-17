package com.dlunaunizar.bobitos.feature.ingredients

import androidx.annotation.StringRes
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.SaveTimeoutException
import com.dlunaunizar.bobitos.data.repository.BrandFailure
import com.dlunaunizar.bobitos.data.repository.BrandRepositoryException
import com.dlunaunizar.bobitos.data.repository.IngredientFailure
import com.dlunaunizar.bobitos.data.repository.IngredientRepositoryException

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
    SaveTimeout,
    Saved,
    Deleted,
    BrandSaved,
    ScanNotFound,
    ScanFailed,
    BarcodeInOtherIngredient,
    CatalogUnavailable,
}

@get:StringRes
internal val IngredientUiMessage.stringResourceId: Int
    get() = when (this) {
        IngredientUiMessage.NameRequired -> R.string.ingredients_error_name_required
        IngredientUiMessage.NameTooLong -> R.string.ingredients_error_name_too_long
        IngredientUiMessage.CategoryTooLong -> R.string.ingredients_error_category_too_long
        IngredientUiMessage.UnitTooLong -> R.string.ingredients_error_unit_too_long
        IngredientUiMessage.AlreadyExists -> R.string.ingredients_error_already_exists_generic
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
        IngredientUiMessage.SaveTimeout -> R.string.write_timeout
        IngredientUiMessage.Saved -> R.string.ingredients_notice_saved
        IngredientUiMessage.Deleted -> R.string.ingredients_notice_deleted
        IngredientUiMessage.BrandSaved -> R.string.ingredients_brand_notice_saved
        IngredientUiMessage.ScanNotFound -> R.string.ingredients_brand_scan_not_found
        IngredientUiMessage.ScanFailed -> R.string.ingredients_brand_scan_error
        IngredientUiMessage.BarcodeInOtherIngredient -> R.string.ingredients_brand_barcode_elsewhere_generic
        IngredientUiMessage.CatalogUnavailable -> R.string.ingredients_catalog_unavailable
    }

internal fun Throwable.toUiMessage(): IngredientUiMessage = when (this) {
    is IngredientRepositoryException -> failure.toUiMessage()
    is BrandRepositoryException -> failure.toUiMessage()
    is SaveTimeoutException -> IngredientUiMessage.SaveTimeout
    else -> IngredientUiMessage.UnexpectedError
}

internal fun BrandFailure.toUiMessage(): IngredientUiMessage = when (this) {
    BrandFailure.NameRequired -> IngredientUiMessage.BrandNameRequired
    BrandFailure.NameTooLong -> IngredientUiMessage.BrandNameTooLong
    BrandFailure.BarcodeTooLong -> IngredientUiMessage.BarcodeTooLong
    BrandFailure.BrandNotFound -> IngredientUiMessage.BrandNotFound
    BrandFailure.NotAuthenticated -> IngredientUiMessage.NotAuthenticated
    BrandFailure.EmailNotVerified -> IngredientUiMessage.EmailNotVerified
    BrandFailure.PermissionDenied -> IngredientUiMessage.PermissionDenied
    BrandFailure.Network -> IngredientUiMessage.NetworkError
    BrandFailure.Unknown -> IngredientUiMessage.UnexpectedError
}

internal fun IngredientFailure.toUiMessage(): IngredientUiMessage = when (this) {
    IngredientFailure.NameRequired -> IngredientUiMessage.NameRequired
    IngredientFailure.NameTooLong -> IngredientUiMessage.NameTooLong
    IngredientFailure.CategoryTooLong -> IngredientUiMessage.CategoryTooLong
    IngredientFailure.UnitTooLong -> IngredientUiMessage.UnitTooLong
    IngredientFailure.AlreadyExists -> IngredientUiMessage.AlreadyExists
    IngredientFailure.NotAuthenticated -> IngredientUiMessage.NotAuthenticated
    IngredientFailure.EmailNotVerified -> IngredientUiMessage.EmailNotVerified
    IngredientFailure.IngredientNotFound -> IngredientUiMessage.NotFound
    IngredientFailure.PermissionDenied -> IngredientUiMessage.PermissionDenied
    IngredientFailure.Network -> IngredientUiMessage.NetworkError
    IngredientFailure.Unknown -> IngredientUiMessage.UnexpectedError
}
