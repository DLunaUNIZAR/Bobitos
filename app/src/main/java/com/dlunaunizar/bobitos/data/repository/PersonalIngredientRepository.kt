package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.Supermarket
import kotlinx.coroutines.flow.Flow

/** Lo que borrar un ingrediente personal se lleva por delante: la ficha, sus marcas y sus tiendas por defecto. */
data class DeletedIngredient(
    val ingredient: CatalogIngredient,
    val brands: List<IngredientBrand>,
    val stores: List<Supermarket>,
    // Cuenta que lo borró: solo ella puede deshacerlo.
    val ownerUid: String,
)

/** Ingredientes personales del usuario (`users/{uid}/ingredients`), visibles solo para él. */
interface PersonalIngredientRepository {
    fun ingredients(): Flow<List<CatalogIngredient>>

    /** Crea la ficha con id `slug(name)` y lo devuelve; `AlreadyExists` si ya hay una personal o común con ese id. */
    suspend fun create(name: String, category: String?, defaultUnit: String?): String

    suspend fun update(id: String, name: String, category: String?, defaultUnit: String?)

    /** Borra la ficha, sus marcas y sus tiendas por defecto. */
    suspend fun delete(id: String)

    /**
     * «Deshacer» el borrado: un único lote devuelve la ficha con su slug, cada marca con su id y las tiendas. Si un
     * ingrediente común ha ocupado el slug, las reglas rechazan el lote entero (`AlreadyExists`) y no se escribe nada.
     */
    suspend fun restore(deleted: DeletedIngredient)
}
