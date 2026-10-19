package com.dlunaunizar.bobitos.core.common

import androidx.annotation.StringRes
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.data.repository.CalendarFailure
import com.dlunaunizar.bobitos.data.repository.CalendarRepositoryException
import com.dlunaunizar.bobitos.data.sync.WriteNotAllowedException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException

/** Tipo de fallo que se le cuenta al usuario; el texto de la excepción nunca llega a la pantalla. */
enum class FailureKind { PERMISSION, OFFLINE, NOT_FOUND, TIMEOUT, EVENT_CHANGED, UNKNOWN }

/** Recorre la cadena `cause` y devuelve el primer fallo que sabe traducir. */
fun Throwable.toFailureKind(): FailureKind {
    val seen = mutableSetOf<Throwable>()
    var current: Throwable? = this
    while (current != null && seen.add(current)) {
        current.directFailureKind()?.let { return it }
        current = current.cause
    }
    return FailureKind.UNKNOWN
}

private fun Throwable.directFailureKind(): FailureKind? = when (this) {
    is SaveTimeoutException -> FailureKind.TIMEOUT
    is WriteNotAllowedException -> FailureKind.OFFLINE
    is CalendarRepositoryException -> failure.failureKind()
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> FailureKind.PERMISSION
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
        -> FailureKind.OFFLINE
        FirebaseFirestoreException.Code.NOT_FOUND -> FailureKind.NOT_FOUND
        else -> null
    }
    is FirebaseNetworkException, is IOException -> FailureKind.OFFLINE
    else -> null
}

private fun CalendarFailure.failureKind(): FailureKind? = when (this) {
    CalendarFailure.Network -> FailureKind.OFFLINE
    CalendarFailure.EventNotFound, CalendarFailure.SpaceNotFound -> FailureKind.NOT_FOUND
    CalendarFailure.PermissionDenied -> FailureKind.PERMISSION
    CalendarFailure.EventChanged -> FailureKind.EVENT_CHANGED
    else -> null
}

@StringRes
fun FailureKind.messageRes(): Int = when (this) {
    FailureKind.PERMISSION -> R.string.failure_permission
    FailureKind.OFFLINE -> R.string.failure_offline
    FailureKind.NOT_FOUND -> R.string.failure_not_found
    FailureKind.TIMEOUT -> R.string.failure_timeout
    FailureKind.EVENT_CHANGED -> R.string.calendar_error_changed
    FailureKind.UNKNOWN -> R.string.failure_unknown
}
