package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import kotlinx.coroutines.flow.Flow

/** Ingredientes personales del usuario (`users/{uid}/ingredients`), visibles solo para él. */
interface PersonalIngredientRepository {
    fun ingredients(): Flow<List<CatalogIngredient>>

    /** Crea la ficha con id `slug(name)` y lo devuelve; `AlreadyExists` si ya hay una personal o común con ese id. */
    suspend fun create(name: String, category: String?, defaultUnit: String?): String

    suspend fun update(id: String, name: String, category: String?, defaultUnit: String?)

    /** Borra la ficha, sus marcas y sus tiendas por defecto. */
    suspend fun delete(id: String)
}
