package com.dlunaunizar.bobitos.feature.ingredients

import com.dlunaunizar.bobitos.data.repository.AuthRepository
import com.dlunaunizar.bobitos.data.repository.DeletedIngredient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Un borrado que espera en el relevo, con cuándo ocurrió. */
data class RelayedDeletion(val deleted: DeletedIngredient, val since: TimeMark)

/**
 * Lleva el ingrediente personal recién borrado del detalle (que se cierra al borrar) a la lista, que ofrece el
 * «Deshacer». Lo publica el detalle solo tras un borrado terminado y la lista lo consume al ofrecerlo.
 * Se vacía cada vez que cambia la cuenta (cerrar sesión o entrar con otra): lo borrado es de datos privados.
 */
@Singleton
class DeletedIngredientRelay internal constructor(
    authRepository: AuthRepository,
    scope: CoroutineScope,
    private val timeSource: TimeSource,
) {
    @Inject
    constructor(authRepository: AuthRepository) :
        this(authRepository, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate), TimeSource.Monotonic)

    private val state = MutableStateFlow<RelayedDeletion?>(null)
    val deleted: StateFlow<RelayedDeletion?> = state.asStateFlow()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            authRepository.currentUser.map { it?.id }.distinctUntilChanged().drop(1).collect { state.value = null }
        }
    }

    /** Guarda [deleted] con la marca de ahora (sustituye al anterior). */
    fun post(deleted: DeletedIngredient) {
        state.value = RelayedDeletion(deleted, timeSource.markNow())
    }

    /** Lo vacía solo si sigue guardando [relayed]. */
    fun consume(relayed: RelayedDeletion) {
        state.compareAndSet(relayed, null)
    }
}
