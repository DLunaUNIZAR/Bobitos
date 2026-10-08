package com.dlunaunizar.bobitos.feature.shopping

import android.os.Parcelable
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.Supermarket
import kotlinx.parcelize.Parcelize

// Borrador del editor de compra. Parcelable para que sobreviva a una rotación.
@Parcelize
internal data class ShoppingDraft(
    val name: String,
    val quantity: String,
    val notes: String,
    val supermarket: Supermarket?,
    val brand: String,
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
