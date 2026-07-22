package com.dlunaunizar.bobitos.core.model

import java.time.Instant

/**
 * Valores nutricionales de una marca, **por 100 g/ml**. Todos opcionales (una marca puede tener solo
 * algunos). Se guardan planos en el documento de la marca para poder validarlos en las reglas.
 */
data class Nutrition(
    val energyKcal: Double? = null,
    val fat: Double? = null,
    val carbohydrates: Double? = null,
    val sugars: Double? = null,
    val protein: Double? = null,
    val salt: Double? = null,
) {
    /** Si no hay ningún valor, no merece la pena guardar/mostrar la nutrición. */
    fun isEmpty(): Boolean = energyKcal == null &&
        fat == null &&
        carbohydrates == null &&
        sugars == null &&
        protein == null &&
        salt == null
}

/**
 * Marca concreta de un ingrediente del catálogo (subcolección `ingredients/{ingredientId}/brands`),
 * con sus valores nutricionales. Colaborativa: la crea cualquier usuario; la edita/borra su autor o
 * un admin del catálogo.
 */
data class IngredientBrand(
    val id: String,
    val ingredientId: String,
    val name: String,
    val barcode: String? = null,
    val nutrition: Nutrition? = null,
    val ownerUid: String,
    val createdBy: String,
    val createdByName: String,
    val createdAt: Instant,
    val updatedBy: String,
    val updatedAt: Instant,
)
