package com.dlunaunizar.bobitos.core.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Lo que espera una oferta de «Deshacer» a mostrarse: lo que dura el aviso largo. */
const val UNDO_OFFER_TIMEOUT_MILLIS = 10_000L

/** Lo que se puede deshacer, con el espacio donde pasó (null fuera de un espacio). */
sealed interface Undoable<out T> {
    val spaceId: String?

    data class Created(override val spaceId: String?, val item: CreatedItem) : Undoable<Nothing>

    data class Deleted<out T>(override val spaceId: String?, val item: T) : Undoable<T>

    /** [previous]: como estaba antes de completar; [completed]: como lo dejó el completar. */
    data class Completed<out T>(override val spaceId: String?, val previous: T, val completed: T) : Undoable<T>
}

/** Una oferta pendiente de mostrar; [serial] la distingue de cualquier otra, aunque sea igual. */
data class UndoOffer<out T>(val serial: Long, val undoable: Undoable<T>)

/** Lo que la pantalla le pide al ViewModel al mostrar una oferta. */
interface UndoOfferControls {
    fun consumeOffer(serial: Long)
}

/**
 * Las ofertas de «Deshacer» pendientes de mostrar de un ViewModel. Esperan sin pantalla (un giro, un cambio de módulo)
 * y caducan a los [UNDO_OFFER_TIMEOUT_MILLIS], contados desde que se hizo la acción. Cada una caduca por su cuenta.
 */
class UndoOffers<T>(private val scope: CoroutineScope, private val timeSource: TimeSource = TimeSource.Monotonic) :
    UndoOfferControls {
    private val _offers = MutableStateFlow<List<UndoOffer<T>>>(emptyList())
    val offers: StateFlow<List<UndoOffer<T>>> = _offers.asStateFlow()
    private val lastSerial = AtomicLong(0)

    /** Añade la oferta al final; [since] es cuándo se hizo la acción. Si ya caducó, no añade nada. */
    fun offer(undoable: Undoable<T>, since: TimeMark = timeSource.markNow()) {
        val remaining = UNDO_OFFER_TIMEOUT_MILLIS.milliseconds - since.elapsedNow()
        if (remaining <= 0.milliseconds) return
        val serial = lastSerial.incrementAndGet()
        _offers.update { it + UndoOffer(serial, undoable) }
        scope.launch {
            delay(remaining)
            consumeOffer(serial)
        }
    }

    override fun consumeOffer(serial: Long) {
        _offers.update { list -> list.filterNot { it.serial == serial } }
    }
}
