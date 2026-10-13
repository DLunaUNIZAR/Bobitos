package com.dlunaunizar.bobitos.feature.common

import android.os.Parcelable
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.core.model.ingredientKey
import com.dlunaunizar.bobitos.data.repository.sortedForBrands
import com.dlunaunizar.bobitos.feature.shopping.ShoppingValidation
import kotlinx.parcelize.Parcelize

// Lógica pura del selector de marca y tienda al añadir un ingrediente a la compra
// (spec 2026-10-11-catalogo-ingredientes-design, §5).

/** Marca elegida (`null` = «Indiferente») y tienda elegida (`null` = sin especificar). */
@Parcelize
internal data class BrandStoreSelection(val brandId: String?, val store: Supermarket?) : Parcelable {
    companion object {
        /** «Indiferente» y sin tienda. */
        val None = BrandStoreSelection(null, null)
    }
}

/** Todo lo necesario para reconocer ingredientes y proponer marca y tienda. */
data class IngredientShoppingData(
    /** Comunes y personales, fusionados con `mergeIngredients`. */
    val ingredients: List<CatalogIngredient> = emptyList(),
    /** Todas las marcas del usuario. */
    val brands: List<IngredientBrand> = emptyList(),
    /** «Dónde lo compro» por id de ingrediente. */
    val prefs: Map<String, IngredientPref> = emptyMap(),
) {
    // Índice por clave tolerante: gana el primero de la lista, como `findByKey`; la clave vacía no entra.
    private val byKey: Map<String, CatalogIngredient> by lazy {
        val index = HashMap<String, CatalogIngredient>()
        for (ingredient in ingredients) {
            val key = ingredientKey(ingredient.name)
            if (key.isNotEmpty()) index.putIfAbsent(key, ingredient)
        }
        index
    }

    internal fun findIngredient(name: String): CatalogIngredient? = byKey[ingredientKey(name)]
}

/** El ingrediente reconocido y sus opciones. Público porque viaja en `IngredientReviewRow` (pública). */
data class IngredientShoppingContext(
    val ingredient: CatalogIngredient,
    /** Solo las marcas de este ingrediente: favoritas primero, después por nombre. */
    val brands: List<IngredientBrand>,
    /** Tiendas donde se compra el ingrediente, en orden de preferencia. */
    val stores: List<Supermarket>,
)

/** Reconoce [name] por clave tolerante (§5); `null` si no coincide con ningún ingrediente. */
internal fun IngredientShoppingData.contextFor(name: String): IngredientShoppingContext? {
    val ingredient = findIngredient(name) ?: return null
    return IngredientShoppingContext(
        ingredient = ingredient,
        brands = brands.filter { it.ingredientId == ingredient.id }.sortedForBrands(),
        stores = prefs[ingredient.id]?.stores.orEmpty(),
    )
}

/**
 * Selección inicial (§5): con exactamente una marca favorita, esa marca y su primera tienda (o la
 * primera del ingrediente si no tiene). Con dos favoritas o ninguna, «Indiferente» y la primera
 * tienda del ingrediente.
 */
internal fun IngredientShoppingContext.defaultSelection(): BrandStoreSelection {
    val favorite = brands.filter { it.favorite }.singleOrNull()
    return BrandStoreSelection(
        brandId = favorite?.id,
        store = favorite?.stores?.firstOrNull() ?: stores.firstOrNull(),
    )
}

/**
 * Cambia de marca (§5). La tienda pasa a la primera de la nueva marca; si esta no tiene tiendas, una
 * tienda que venía de la marca anterior se sustituye por la primera del ingrediente y una elegida a
 * mano se mantiene.
 */
internal fun IngredientShoppingContext.selectBrand(
    current: BrandStoreSelection,
    brandId: String?,
): BrandStoreSelection {
    val newBrand = brands.firstOrNull { it.id == brandId }
    val previous = brands.firstOrNull { it.id == current.brandId }
    val store = newBrand?.stores?.firstOrNull()
        ?: if (current.store != null && previous != null && current.store in previous.stores) {
            stores.firstOrNull()
        } else {
            current.store
        }
    return BrandStoreSelection(newBrand?.id, store)
}

/** Tiendas ofrecidas: las de la marca elegida, las del ingrediente y el resto, sin repetidos. */
internal fun IngredientShoppingContext.storeOptions(selection: BrandStoreSelection): List<Supermarket> {
    val brandStores = brands.firstOrNull { it.id == selection.brandId }?.stores.orEmpty()
    return (brandStores + stores + Supermarket.entries).distinct()
}

/** Nombre de la marca elegida; `null` si es «Indiferente» o el id ya no existe. */
internal fun IngredientShoppingContext.brandName(selection: BrandStoreSelection): String? =
    brands.firstOrNull { it.id == selection.brandId }?.name

/**
 * Marca a guardar: con marcas registradas, el nombre de la elegida; sin ellas, el texto libre
 * recortado (`null` si queda vacío).
 */
internal fun IngredientShoppingContext.brandToSave(selection: BrandStoreSelection, freeText: String): String? {
    val text = if (brands.isEmpty()) freeText else brandName(selection).orEmpty()
    return text.trim().ifEmpty { null }
}

/** El texto libre de la marca no cabe: el campo lo marca y la revisión no deja confirmar. */
internal fun freeBrandTooLong(text: String): Boolean = text.trim().length > ShoppingValidation.MAX_BRAND_LENGTH
