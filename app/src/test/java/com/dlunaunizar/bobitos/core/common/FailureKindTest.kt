package com.dlunaunizar.bobitos.core.common

import com.dlunaunizar.bobitos.data.repository.CalendarFailure
import com.dlunaunizar.bobitos.data.repository.CalendarRepositoryException
import com.dlunaunizar.bobitos.data.repository.TaskFailure
import com.dlunaunizar.bobitos.data.repository.TaskRepositoryException
import com.dlunaunizar.bobitos.data.sync.WriteNotAllowedException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.IOException

class FailureKindTest {
    @Test
    fun `a wrapped permission denied is PERMISSION`() {
        val error =
            TaskRepositoryException(TaskFailure.Unknown, FirebaseFirestoreException("x", Code.PERMISSION_DENIED))

        assertEquals(FailureKind.PERMISSION, error.toFailureKind())
    }

    @Test
    fun `a save timeout is TIMEOUT`() {
        assertEquals(FailureKind.TIMEOUT, SaveTimeoutException().toFailureKind())
    }

    @Test
    fun `an IOException is OFFLINE`() {
        assertEquals(FailureKind.OFFLINE, IOException().toFailureKind())
    }

    @Test
    fun `unavailable and deadline exceeded are OFFLINE`() {
        assertEquals(FailureKind.OFFLINE, FirebaseFirestoreException("x", Code.UNAVAILABLE).toFailureKind())
        assertEquals(FailureKind.OFFLINE, FirebaseFirestoreException("x", Code.DEADLINE_EXCEEDED).toFailureKind())
    }

    @Test
    fun `not found is NOT_FOUND`() {
        assertEquals(FailureKind.NOT_FOUND, FirebaseFirestoreException("x", Code.NOT_FOUND).toFailureKind())
    }

    @Test
    fun `anything else is UNKNOWN`() {
        assertEquals(FailureKind.UNKNOWN, IllegalStateException("boom").toFailureKind())
        assertEquals(FailureKind.UNKNOWN, FirebaseFirestoreException("x", Code.ABORTED).toFailureKind())
    }

    @Test
    fun writeNotAllowedIsOffline() {
        assertEquals(FailureKind.OFFLINE, WriteNotAllowedException().toFailureKind())
    }

    @Test
    fun calendarEventNotFoundIsNotFound() {
        val error = CalendarRepositoryException(CalendarFailure.EventNotFound)

        assertEquals(FailureKind.NOT_FOUND, error.toFailureKind())
        assertEquals(FailureKind.NOT_FOUND, CalendarRepositoryException(CalendarFailure.SpaceNotFound).toFailureKind())
    }

    @Test
    fun `an event that changed meanwhile is EVENT_CHANGED`() {
        assertEquals(
            FailureKind.EVENT_CHANGED,
            CalendarRepositoryException(CalendarFailure.EventChanged).toFailureKind(),
        )
    }

    @Test
    fun calendarNetworkIsOffline() {
        assertEquals(FailureKind.OFFLINE, CalendarRepositoryException(CalendarFailure.Network).toFailureKind())
    }

    @Test
    fun `every kind has a message`() {
        FailureKind.entries.forEach { assertNotEquals(0, it.messageRes()) }
    }
}
