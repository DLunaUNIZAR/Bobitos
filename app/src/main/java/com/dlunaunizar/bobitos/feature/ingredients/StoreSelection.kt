package com.dlunaunizar.bobitos.feature.ingredients

import com.dlunaunizar.bobitos.core.model.Supermarket

/** Tiendas que se pueden elegir: todas salvo `INDIFERENTE`, que no tiene sentido como preferencia. */
internal val SELECTABLE_STORES: List<Supermarket> = Supermarket.entries - Supermarket.INDIFERENTE

/** Quita la tienda si ya está; si no, la añade al final (el orden es la preferencia). Ignora `INDIFERENTE`. */
internal fun toggleStore(stores: List<Supermarket>, store: Supermarket): List<Supermarket> = when {
    store == Supermarket.INDIFERENTE -> stores
    store in stores -> stores - store
    else -> stores + store
}
