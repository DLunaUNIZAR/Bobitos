package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `a private recipe comes back as a new one of whoever undoes, with server dates`() {
        val mine = recipe(RecipeVisibility.PRIVATE, importedAt = imported).copy(ownerUid = "chef", createdBy = "chef")

        assertEquals(RecipeRestoreAudit("chef", "chef", null, "chef", null, null), mine.restoreAudit("chef", admins))
    }
}
