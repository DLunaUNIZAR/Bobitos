package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.data.sync.RealtimeMetrics
import com.dlunaunizar.bobitos.data.sync.SyncRepository
import com.dlunaunizar.bobitos.data.sync.WriteNotAllowedException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine

/**
 * Envoltorio común de las escrituras: exige poder escribir y traduce cualquier fallo con [writeFailure].
 */
internal suspend inline fun <T, reified E : Throwable> SyncRepository.runWriteOperation(
    crossinline isNetworkFailure: (E) -> Boolean,
    crossinline networkException: (cause: Throwable) -> E,
    crossinline toException: (Throwable) -> E,
    crossinline operation: suspend () -> T,
): T {
    try {
        requireWritable()
        return operation()
    } catch (@Suppress("TooGenericExceptionCaught") error: Throwable) {
        throw writeFailure(error, isNetworkFailure, networkException, toException)
    }
}

/**
 * La excepción [E] del repositorio pasa tal cual (avisando de las de red); `WriteNotAllowedException` pasa a un
 * fallo de red y el resto se avisa y se convierte con [toException].
 */
internal inline fun <reified E : Throwable> SyncRepository.writeFailure(
    error: Throwable,
    isNetworkFailure: (E) -> Boolean,
    networkException: (cause: Throwable) -> E,
    toException: (Throwable) -> E,
): E = when (error) {
    is E -> {
        if (isNetworkFailure(error)) reportWriteFailure(error.cause ?: error)
        error
    }
    is WriteNotAllowedException -> networkException(error)
    else -> {
        reportWriteFailure(error)
        toException(error)
    }
}

/** Escucha [this] en tiempo real: cada snapshot se mide y se emite como [mapper] de sus documentos. */
internal fun <T> Query.documentsFlow(
    metrics: RealtimeMetrics,
    scope: String,
    toException: (Throwable) -> Throwable,
    mapper: (List<DocumentSnapshot>) -> T,
): Flow<T> = callbackFlow {
    val metricId = metrics.listenerStarted(scope)
    val registration = addSnapshotListener { snapshot, error ->
        when {
            error != null -> close(toException(error))
            snapshot != null -> {
                metrics.snapshotReceived(scope, snapshot.documentChanges.size, snapshot.metadata.isFromCache)
                trySend(mapper(snapshot.documents))
            }
        }
    }
    awaitClose {
        registration.remove()
        metrics.listenerStopped(metricId)
    }
}

/**
 * Escucha a la vez la consulta de pendientes y la de hechas recientes y emite su unión ([merge], normalmente
 * [mergePendingAndRecent]). No emite hasta tener las dos primeras respuestas, para que la lista no aparezca primero
 * sin las hechas. Cuando un elemento cambia de estado sale de una consulta y entra en la otra con la misma escritura
 * local; si entre las dos emisiones está en las dos, manda la de pendientes (el merge lo deduplica).
 */
internal fun <T, R> pendingAndRecentFlow(
    pending: Query,
    recent: Query,
    metrics: RealtimeMetrics,
    scope: String,
    toException: (Throwable) -> Throwable,
    mapper: (List<DocumentSnapshot>) -> List<T>,
    merge: (pending: List<T>, recent: List<T>) -> R,
): Flow<R> = combine(
    pending.documentsFlow(metrics, "$scope:pending", toException, mapper),
    recent.documentsFlow(metrics, "$scope:recent", toException, mapper),
    merge,
)
