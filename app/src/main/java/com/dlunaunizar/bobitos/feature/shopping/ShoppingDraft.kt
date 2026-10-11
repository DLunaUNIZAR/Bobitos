package com.dlunaunizar.bobitos.feature.shopping

import android.os.Parcelable
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.feature.common.BrandStoreSelection
import com.dlunaunizar.bobitos.feature.common.IngredientShoppingContext
import com.dlunaunizar.bobitos.feature.common.brandName
import com.dlunaunizar.bobitos.feature.common.brandToSave
import com.dlunaunizar.bobitos.feature.common.defaultSelection
import com.dlunaunizar.bobitos.feature.common.selectBrand
import kotlinx.parcelize.Parcelize

// Borrador del editor de compra. Parcelable para que sobreviva a una rotación.
@Parcelize
internal data class ShoppingDraft(
    val name: String,
    val quantity: String,
    val notes: String,
    val supermarket: Supermarket?,
    val brand: String,
    // Marca elegida en el selector (null = «Indiferente» o sin selector).
    val brandId: String? = null,
    // Id del ingrediente cuyos valores por defecto ya se aplicaron.
    val pickerFor: String? = null,
    // Selección por defecto aplicada; si el borrador sigue igual, se puede refrescar con datos nuevos.
    val appliedDefault: BrandStoreSelection? = null,
) : Parcelable {
    companion object {
        fun of(item: ShoppingItem?) = ShoppingDraft(
            name = item?.name.orEmpty(),
            quantity = item?.quantity.orEmpty(),
            notes = item?.notes.orEmpty(),
            supermarket = item?.supermarket,
            brand = item?.brand.orEmpty(),
        )

        /** Borrador de alta con solo el nombre puesto (p. ej. desde la ficha de un ingrediente). */
        fun prefilled(name: String) = of(null).copy(name = name)
    }
}

/**
 * Aplica el reconocimiento de ingrediente (§5): los valores por defecto se ponen una sola vez por
 * ingrediente y sin ingrediente se conservan marca y tienda como texto y tienda normales.
 */
internal fun ShoppingDraft.withContext(context: IngredientShoppingContext?): ShoppingDraft = when {
    context == null -> if (pickerFor != null) {
        // La tienda que puso el valor por defecto se va con el ingrediente; la que eligió el usuario se queda.
        copy(
            pickerFor = null,
            brandId = null,
            brand = pickerBrand(),
            supermarket = if (supermarket == appliedDefault?.store) null else supermarket,
            appliedDefault = null,
        )
    } else {
        this
    }
    context.ingredient.id == pickerFor -> refreshed(context)
    else -> withDefault(context)
}

private fun ShoppingDraft.withDefault(context: IngredientShoppingContext): ShoppingDraft {
    val selection = context.defaultSelection()
    return copy(
        pickerFor = context.ingredient.id,
        brandId = selection.brandId,
        supermarket = selection.store,
        brand = if (context.brands.isEmpty()) pickerBrand() else context.brandName(selection).orEmpty(),
        appliedDefault = selection,
    )
}

// Mismo ingrediente con datos nuevos: lo que el usuario no tocó se recalcula; lo que eligió se respeta.
private fun ShoppingDraft.refreshed(context: IngredientShoppingContext): ShoppingDraft {
    val current = BrandStoreSelection(brandId, supermarket)
    return when {
        current == appliedDefault -> withDefault(context)
        brandId != null && context.brands.none { it.id == brandId } -> copy(brandId = null, brand = "")
        brandId != null -> copy(brand = context.brandName(current).orEmpty())
        else -> this
    }
}

// El texto de marca puesto por el selector no pasa a otro ingrediente; el escrito a mano se conserva.
private fun ShoppingDraft.pickerBrand(): String = if (brandId != null) "" else brand

/** Cambia de marca en el selector: ajusta la tienda y escribe el nombre como texto de marca. */
internal fun ShoppingDraft.withBrandChoice(context: IngredientShoppingContext, brandId: String?): ShoppingDraft {
    val selection = context.selectBrand(BrandStoreSelection(this.brandId, supermarket), brandId)
    return copy(
        brandId = selection.brandId,
        supermarket = selection.store,
        brand = context.brandName(selection).orEmpty(),
    )
}

/** Hay cambios sin guardar: distinto del borrador inicial tras aplicar los valores por defecto. */
internal fun ShoppingDraft.isDirty(initial: ShoppingDraft, context: IngredientShoppingContext?): Boolean =
    this != initial.withContext(context)

/** Marca a guardar: con selector, el nombre vigente de la marca elegida; si no, el texto escrito. */
internal fun ShoppingDraft.savedBrand(context: IngredientShoppingContext?): String? = when (context) {
    null -> brand.trim().ifEmpty { null }
    else -> context.brandToSave(BrandStoreSelection(brandId, supermarket), brand)
}

/** Qué hacer con el nombre traído desde la ficha de un ingrediente. */
internal sealed interface PrefillAction {
    data object None : PrefillAction

    /** Aún no se puede escribir: la petición queda pendiente hasta que se pueda. */
    data object Wait : PrefillAction

    data class Open(val name: String) : PrefillAction
}

internal fun prefillAction(prefillName: String?, canWrite: Boolean): PrefillAction = when {
    prefillName == null -> PrefillAction.None
    !canWrite -> PrefillAction.Wait
    else -> PrefillAction.Open(prefillName)
}
