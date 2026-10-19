package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.google.firebase.firestore.FieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskFieldsTest {
    private val me = AuthUser(id = "me", displayName = "Yo", email = "yo@example.com", isEmailVerified = true)
    private val values = TaskValues(title = "Regar", description = null, assigneeId = null)

    private fun fields(completed: Boolean) = taskData(
        values = values,
        assigneeName = null,
        dueAt = null,
        priority = TaskPriority.MEDIUM,
        type = null,
        recurrence = null,
        startAt = null,
        user = me,
        completed = completed,
    )

    @Test
    fun `a restored completed task is written done by whoever undoes`() {
        val data = fields(completed = true)

        assertEquals("DONE", data["status"])
        assertEquals("me", data["completedBy"])
        assertEquals("Yo", data["completedByName"])
        assertEquals(FieldValue.serverTimestamp(), data["completedAt"])
    }

    @Test
    fun `a new task is written pending`() {
        val data = fields(completed = false)

        assertEquals("TODO", data["status"])
        assertNull(data["completedBy"])
        assertNull(data["completedByName"])
        assertNull(data["completedAt"])
    }
}
