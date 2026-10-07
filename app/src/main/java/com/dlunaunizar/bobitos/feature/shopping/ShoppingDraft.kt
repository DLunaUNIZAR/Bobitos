package com.dlunaunizar.bobitos.feature.shopping

import androidx.compose.runtime.saveable.listSaver
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.Supermarket

// Borrador del editor de compra: solo tipos que caben en un Bundle, para que sobreviva a una rotación.
// `supermarketName` es el `name` del enum.
internal data class ShoppingDraft(
    val name: String,
    val quantity: String,
    val notes: String,
    val supermarketName: String?,
    val brand: String,
) {
    val supermarket: Supermarket? get() = supermarketName?.let(Supermarket::valueOf)

    companion object {
        fun of(item: ShoppingItem?) = ShoppingDraft(
            name = item?.name.orEmpty(),
            quantity = item?.quantity.orEmpty(),
            notes = item?.notes.orEmpty(),
            supermarketName = item?.supermarket?.name,
            brand = item?.brand.orEmpty(),
        )
    }
}

internal val ShoppingDraftSaver = listSaver<ShoppingDraft, Any?>(
    save = { listOf(it.name, it.quantity, it.notes, it.supermarketName, it.brand) },
    restore = {
        ShoppingDraft(
            name = it[0] as String,
            quantity = it[1] as String,
            notes = it[2] as String,
            supermarketName = it[3] as String?,
            brand = it[4] as String,
        )
    },
)
