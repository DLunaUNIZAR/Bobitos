package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import com.google.firebase.firestore.FieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteFieldsTest {
    private val me = AuthUser(id = "me", displayName = "Yo", email = "yo@example.com", isEmailVerified = true)
    private val values = NoteValues(title = "Wifi", body = "clave: 1234")

    @Test
    fun `a restored note keeps its pin`() {
        assertEquals(true, noteFields(values, pinned = true, user = me)["pinned"])
        assertEquals(false, noteFields(values, pinned = false, user = me)["pinned"])
    }

    @Test
    fun `a new note is written by whoever adds it`() {
        val data = noteFields(values, pinned = false, user = me)

        assertEquals("Wifi", data["title"])
        assertEquals("me", data["createdBy"])
        assertEquals("Yo", data["createdByName"])
        assertEquals("me", data["updatedBy"])
        assertEquals(FieldValue.serverTimestamp(), data["createdAt"])
        assertEquals(FieldValue.serverTimestamp(), data["updatedAt"])
    }
}
