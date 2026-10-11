package com.dlunaunizar.bobitos.feature.routines

import com.dlunaunizar.bobitos.core.model.Routine
import com.dlunaunizar.bobitos.core.model.RoutineVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RoutineSectionsTest {
    @Test
    fun `my routines only lists private ones, also for the catalog admin`() {
        val mine = listOf(
            routine("p1", RoutineVisibility.PRIVATE),
            routine("torso", RoutineVisibility.GLOBAL),
            routine("p2", RoutineVisibility.PRIVATE),
        )
        assertEquals(listOf("p1", "p2"), mine.personalOnly().map(Routine::id))
    }

    @Test
    fun `any common routine can be saved as mine when writing is allowed`() {
        assertTrue(canForkRoutine(routine("torso", RoutineVisibility.GLOBAL), canWrite = true))
        assertFalse(canForkRoutine(routine("torso", RoutineVisibility.GLOBAL), canWrite = false))
        assertFalse(canForkRoutine(routine("p1", RoutineVisibility.PRIVATE), canWrite = true))
    }

    private fun routine(id: String, visibility: RoutineVisibility) = Routine(
        id = id,
        ownerUid = "admin",
        visibility = visibility,
        title = id,
        createdBy = "admin",
        createdByName = "Catálogo Bobitos",
        createdAt = Instant.EPOCH,
        updatedBy = "admin",
        updatedAt = Instant.EPOCH,
    )
}
