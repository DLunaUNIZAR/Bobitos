package com.dlunaunizar.bobitos.core.model

/**
 * «Dónde lo compro» de un ingrediente, a nivel de usuario: las tiendas donde lo compra, en orden de
 * preferencia. Vive en el documento de preferencias del propio usuario.
 */
data class IngredientPref(val stores: List<Supermarket> = emptyList())

/** Máximo de tiendas por marca o ingrediente (también lo exigen las reglas de Firestore). */
const val MAX_STORES = 6
