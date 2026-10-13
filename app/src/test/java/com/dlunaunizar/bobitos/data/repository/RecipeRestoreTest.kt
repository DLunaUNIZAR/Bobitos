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
            recipe(RecipeVisibility.GLOBAL, importedAt = imported).restoreAudit("second-admin"),
        )
    }

    @Test
    fun `a common recipe made in the app comes back with its dates and without an import date`() {
        val audit = recipe(RecipeVisibility.GLOBAL).restoreAudit("second-admin")

        assertEquals("catalog-admin", audit.ownerUid)
        assertEquals(created, audit.createdAt)
        assertNull(audit.importedAt)
    }

    @Test
    fun `a private recipe comes back as a new one of whoever undoes, with server dates`() {
        val mine = recipe(RecipeVisibility.PRIVATE, importedAt = imported).copy(ownerUid = "chef", createdBy = "chef")

        assertEquals(RecipeRestoreAudit("chef", "chef", null, "chef", null, null), mine.restoreAudit("chef"))
    }
}
