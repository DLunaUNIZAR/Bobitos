package com.dlunaunizar.bobitos.feature.ingredients

import com.dlunaunizar.bobitos.data.repository.DeletedIngredient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lleva el ingrediente personal recién borrado del detalle (que se cierra al borrar) a la lista, que ofrece el
 * «Deshacer». Lo publica el detalle solo tras un borrado terminado y la lista lo consume al mostrar el aviso.
 */
@Singleton
class DeletedIngredientRelay @Inject constructor() {
    private val state = MutableStateFlow<DeletedIngredient?>(null)
    val deleted: StateFlow<DeletedIngredient?> = state.asStateFlow()

    fun post(deleted: DeletedIngredient) {
        state.value = deleted
    }

    fun consume() {
        state.value = null
    }
}
