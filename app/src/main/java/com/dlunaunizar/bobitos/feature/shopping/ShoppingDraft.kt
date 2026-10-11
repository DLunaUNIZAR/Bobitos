package com.dlunaunizar.bobitos.feature.shopping

import android.os.Parcelable
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.feature.common.BrandStoreSelection
import com.dlunaunizar.bobitos.feature.common.IngredientShoppingContext
import com.dlunaunizar.bobitos.feature.common.brandName
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
) : Parcelable {
    companion object {
        fun of(item: ShoppingItem?) = ShoppingDraft(
            name = item?.name.orEmpty(),
            quantity = item?.quantity.orEmpty(),
            notes = item?.notes.orEmpty(),
            supermarket = item?.supermarket,
            brand = item?.brand.orEmpty(),
        )
    }
}

/**
 * Aplica el reconocimiento de ingrediente (§5): los valores por defecto se ponen una sola vez por
 * ingrediente y sin ingrediente se conservan marca y tienda como texto y tienda normales.
 */
internal fun ShoppingDraft.withContext(context: IngredientShoppingContext?): ShoppingDraft = when {
    context == null -> if (pickerFor != null) copy(pickerFor = null, brandId = null) else this
    context.ingredient.id == pickerFor -> this
    else -> {
        val selection = context.defaultSelection()
        copy(
            pickerFor = context.ingredient.id,
            brandId = selection.brandId,
            supermarket = selection.store,
            brand = if (context.brands.isEmpty()) brand else context.brandName(selection).orEmpty(),
        )
    }
}

/** Cambia de marca en el selector: ajusta la tienda y escribe el nombre como texto de marca. */
internal fun ShoppingDraft.withBrandChoice(context: IngredientShoppingContext, brandId: String?): ShoppingDraft {
    val selection = context.selectBrand(BrandStoreSelection(this.brandId, supermarket), brandId)
    return copy(
        brandId = selection.brandId,
        supermarket = selection.store,
        brand = context.brandName(selection).orEmpty(),
    )
}

/** Borrador de alta con solo el nombre puesto (p. ej. desde la ficha de un ingrediente). */
internal fun ShoppingDraft.Companion.prefilled(name: String): ShoppingDraft = ShoppingDraft.of(null).copy(name = name)
