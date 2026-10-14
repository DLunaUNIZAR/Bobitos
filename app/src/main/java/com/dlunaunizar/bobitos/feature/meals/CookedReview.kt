package com.dlunaunizar.bobitos.feature.meals

import android.os.Parcelable
import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.pantryId
import com.dlunaunizar.bobitos.core.model.shoppingMatchKey
import com.dlunaunizar.bobitos.feature.common.IngredientShoppingData
import com.dlunaunizar.bobitos.feature.common.pantryEntryFor
import kotlinx.parcelize.Parcelize

// Diálogo «Has cocinado …» (spec 2026-10-13-despensa-design, §3c).

/** Algo de la receta que hay en la despensa: [id] es su `pantryId`; [name], como se escribe en la receta. */
data class CookedPantryRow(val id: String, val name: String)

/**
 * Lo que ofrece «Has cocinado [mealName]»: [crossOff], los artículos pendientes de la receta en la lista (para tacharlos);
 * [finished], lo de la receta que hay en la despensa y no está pendiente («¿Se ha acabado algo?»).
 */
data class CookedReview(val mealName: String, val crossOff: List<ShoppingItem>, val finished: List<CookedPantryRow>)

/**
 * Lo marcado en el diálogo: los artículos a tachar, lo que se ha acabado y si se apunta a la compra. Parcelable para
 * que sobreviva a una rotación.
 */
@Parcelize
data class CookedChoice(val crossOffIds: Set<String>, val finishedIds: Set<String>, val restock: Boolean) : Parcelable

/** Lo que hay que escribir al pulsar «Listo». */
internal data class CookedWrites(
    val crossOff: List<ShoppingItem>,
    val finishedIds: List<String>,
    /** Lo que se apunta en la compra; vacío si no se pide. */
    val restockNames: List<String>,
) {
    val isEmpty: Boolean get() = crossOff.isEmpty() && finishedIds.isEmpty()
}

/**
 * Las secciones del diálogo, o `null` si las dos quedan vacías. [items] es la lista y [pantryIds] la despensa, leídas
 * del servidor; `null` si no se han podido leer (entonces su sección no sale). Un ingrediente pendiente en la lista solo
 * sale en «Tachar de la compra». Los repetidos por clave salen una vez; los nombres sin letras ni números, nunca.
 */
internal fun cookedReview(
    mealName: String,
    ingredients: List<Ingredient>,
    items: List<ShoppingItem>?,
    pantryIds: Set<String>?,
): CookedReview? {
    val names = ingredients.map { it.name.trim() }.filter(String::isNotEmpty)
    val keys = names.mapTo(HashSet(), ::shoppingMatchKey)
    val crossOff = items.orEmpty().filter { !it.purchased && shoppingMatchKey(it.name) in keys }
    val pendingKeys = crossOff.mapTo(HashSet()) { shoppingMatchKey(it.name) }
    val finished = if (pantryIds == null) {
        emptyList()
    } else {
        names.filter { shoppingMatchKey(it) !in pendingKeys }
            .map { it to pantryId(it) }
            .filter { it.second.isNotEmpty() && it.second in pantryIds }
            .distinctBy { it.second }
            .map { (name, id) -> CookedPantryRow(id, name) }
    }
    if (crossOff.isEmpty() && finished.isEmpty()) return null
    return CookedReview(mealName, crossOff, finished)
}

/** Por defecto: tachar todo lo pendiente, nada se ha acabado y, si algo se acaba, apuntarlo a la compra. */
internal fun CookedReview.defaultChoice(): CookedChoice =
    CookedChoice(crossOff.mapTo(HashSet(), ShoppingItem::id), emptySet(), restock = true)

internal fun CookedChoice.toggleCrossOff(id: String): CookedChoice = copy(crossOffIds = crossOffIds.toggled(id))

internal fun CookedChoice.toggleFinished(id: String): CookedChoice = copy(finishedIds = finishedIds.toggled(id))

private fun Set<String>.toggled(id: String): Set<String> = if (id in this) this - id else this + id

/** Lo que escribe [choice], en el orden del diálogo. */
internal fun CookedReview.writesFor(choice: CookedChoice): CookedWrites {
    val finishedRows = finished.filter { it.id in choice.finishedIds }
    return CookedWrites(
        crossOff = crossOff.filter { it.id in choice.crossOffIds },
        finishedIds = finishedRows.map(CookedPantryRow::id),
        restockNames = if (choice.restock) finishedRows.map(CookedPantryRow::name) else emptyList(),
    )
}

/**
 * Lo que se apunta a la compra: con el nombre del catálogo si se reconoce («Aceite de oliva virgen extra»), como al
 * decir «Se acabó» en la Despensa; si no, como se escribe en la receta.
 */
internal fun CookedWrites.restockNamesIn(data: IngredientShoppingData): List<String> =
    restockNames.map { pantryEntryFor(it, data)?.name ?: it }
