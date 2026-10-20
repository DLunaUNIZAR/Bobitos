package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RecipeRestoreTest {
    private val created = Instant.parse("2026-10-01T10:00:00Z")
    private val imported = Instant.parse("2026-10-12T09:30:00.123Z")
    private val admins = setOf("catalog-admin", "second-admin")

    private fun recipe(visibility: RecipeVisibility, importedAt: Instant? = null) = Recipe(
        id = "lasana-de-carne",
        ownerUid = "catalog-admin",
        visibility = visibility,
        title = "Lasaña de carne",
        description = null,
        category = null,
        createdBy = "catalog-admin",
        createdByName = "Catálogo Bobitos",
        createdAt = created,
        updatedBy = "catalog-admin",
        updatedAt = imported,
        importedAt = importedAt,
    )

    @Test
    fun `a common recipe comes back as it was even when another admin undoes`() {
        assertEquals(
            RecipeRestoreAudit("catalog-admin", "catalog-admin", created, "catalog-admin", imported, imported),
            recipe(RecipeVisibility.GLOBAL, importedAt = imported).restoreAudit("second-admin", admins),
        )
    }

    @Test
    fun `a common recipe made in the app comes back with its dates and without an import date`() {
        val audit = recipe(RecipeVisibility.GLOBAL).restoreAudit("second-admin", admins)

        assertEquals("catalog-admin", audit.ownerUid)
        assertEquals(created, audit.createdAt)
        assertNull(audit.importedAt)
    }

    @Test
    fun `a common recipe whose owner is no longer an admin comes back owned by whoever undoes`() {
        val orphan = recipe(RecipeVisibility.GLOBAL, importedAt = imported).copy(
            ownerUid = "ex-admin",
            createdBy = "ex-admin",
        )

        assertEquals(
            RecipeRestoreAudit("second-admin", "second-admin", created, "catalog-admin", imported, imported),
            orphan.restoreAudit("second-admin", admins),
        )
    }

    @Test
    fun `a common recipe whose last editor is no longer an admin comes back edited by whoever undoes`() {
        val edited = recipe(RecipeVisibility.GLOBAL, importedAt = imported).copy(updatedBy = "ex-admin")

        assertEquals(
            RecipeRestoreAudit("catalog-admin", "catalog-admin", created, "second-admin", imported, imported),
            edited.restoreAudit("second-admin", admins),
        )
    }

    @Test
    @Suppress("ktlint:standard:max-line-length", "MaxLineLength")
    fun `a common recipe whose owner and last editor are no longer admins comes back owned and edited by whoever undoes`() {
        val orphan = recipe(RecipeVisibility.GLOBAL, importedAt = imported).copy(
            ownerUid = "ex-admin",
            createdBy = "ex-admin",
            updatedBy = "ex-admin",
        )

        assertEquals(
            RecipeRestoreAudit("second-admin", "second-admin", created, "second-admin", imported, imported),
            orphan.restoreAudit("second-admin", admins),
        )
    }

    @Test
    fun `a private recipe comes back as a new one of whoever undoes, with server dates`() {
        val mine = recipe(RecipeVisibility.PRIVATE, importedAt = imported).copy(ownerUid = "chef", createdBy = "chef")

        assertEquals(RecipeRestoreAudit("chef", "chef", null, "chef", null, null), mine.restoreAudit("chef", admins))
    }

    @Test
    fun `a common recipe whose last edit is still pending comes back edited at the server time`() {
        val pending = recipe(RecipeVisibility.GLOBAL, importedAt = imported)
            .copy(updatedAt = created, updatePending = true)
        val madeInApp = recipe(RecipeVisibility.GLOBAL).copy(updatePending = true)

        val audit = pending.restoreAudit("catalog-admin", admins)

        assertNull(audit.updatedAt)
        assertEquals(created, audit.createdAt)
        assertEquals(imported, audit.importedAt)
        assertNull(madeInApp.restoreAudit("catalog-admin", admins).updatedAt)
    }

    @Test
    fun `a common recipe read with an edit date before its import comes back edited at the server time`() {
        val stale = recipe(RecipeVisibility.GLOBAL, importedAt = imported).copy(updatedAt = created)

        assertNull(stale.restoreAudit("catalog-admin", admins).updatedAt)
    }

    @Test
    fun `an imported common recipe never edited in the app keeps its last edit at the import date`() {
        val audit = recipe(RecipeVisibility.GLOBAL, importedAt = imported).restoreAudit("catalog-admin", admins)

        assertEquals(imported, audit.updatedAt)
        assertEquals(imported, audit.importedAt)
    }

    @Test
    fun `restored dates keep the millisecond the app read from Firestore`() {
        val stored = Timestamp(1_760_000_000L, 123_456_789)
        val read = stored.toMillisInstant()
        val fields = RecipeRestoreAudit("owner", "author", read, "editor", read, read).toRestoredFields()

        val expected = Timestamp(1_760_000_000L, 123_000_000)
        assertEquals(expected, fields["createdAt"])
        assertEquals(expected, fields["updatedAt"])
        val source = fields["source"] as Map<*, *>
        assertEquals(expected, source["importedAt"])
        assertFalse((source["importedAt"] as Timestamp) > (fields["updatedAt"] as Timestamp))
    }

    @Test
    fun `restored fields use the server time for missing dates and carry the source only for imported recipes`() {
        val fields = RecipeRestoreAudit("owner", "author", null, "editor", null, null).toRestoredFields()

        assertEquals(FieldValue.serverTimestamp(), fields["createdAt"])
        assertEquals(FieldValue.serverTimestamp(), fields["updatedAt"])
        assertFalse(fields.containsKey("source"))
        assertEquals("owner", fields["ownerUid"])
        assertEquals("author", fields["createdBy"])
        assertEquals("editor", fields["updatedBy"])
        assertTrue(fields.keys.containsAll(listOf("ownerUid", "createdBy", "updatedBy")))
    }
}
