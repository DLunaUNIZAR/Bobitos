package com.dlunaunizar.bobitos.feature.shopping

import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.Supermarket

private val LOCAL_SHOPS = setOf(
    Supermarket.CARNICERIA,
    Supermarket.PESCADERIA,
    Supermarket.FRUTERIA,
    Supermarket.PANADERIA,
)

/** Tiendas de barrio: al filtrar por una de ellas solo se ven sus artículos. */
internal val Supermarket.isLocalShop: Boolean
    get() = this in LOCAL_SHOPS

/** Tiendas presentes en los artículos, en el orden del enum (el de los chips del filtro). */
internal fun List<ShoppingItem>.presentSupermarkets(): List<Supermarket> =
    mapNotNull(ShoppingItem::supermarket).distinct().sortedBy(Supermarket::ordinal)

/**
 * Artículos que se ven al filtrar por [supermarket]. Con un supermercado u «Otros» también salen los
 * artículos sin tienda y los «Indiferente» (valen para cualquiera); con una tienda de barrio, solo los suyos.
 */
internal fun List<ShoppingItem>.forSupermarket(supermarket: Supermarket?): List<ShoppingItem> =
    if (supermarket == null) {
        this
    } else if (supermarket.isLocalShop) {
        filter { it.supermarket == supermarket }
    } else {
        filter {
            it.supermarket == supermarket ||
                it.supermarket == null ||
                it.supermarket == Supermarket.INDIFERENTE
        }
    }
