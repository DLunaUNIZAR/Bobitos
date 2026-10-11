package com.dlunaunizar.bobitos.feature.exercises

import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.model.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseFilterMenusTest {
    @Test
    fun `group options keep the catalog order without folded duplicates`() {
        assertEquals(listOf("Pecho", "Bíceps"), groupFilterOptions(listOf("Pecho", "Bíceps", "biceps"), null))
    }

    @Test
    fun `a selected group missing from the catalog is added at the end`() {
        assertEquals(listOf("Pecho", "Cuello"), groupFilterOptions(listOf("Pecho"), "Cuello"))
    }

    @Test
    fun `a selected group already present with other accents or case is not repeated`() {
        assertEquals(listOf("Bíceps", "Pecho"), groupFilterOptions(listOf("Bíceps", "Pecho"), "BICEPS"))
    }

    @Test
    fun `no groups and nothing selected gives no options`() {
        assertEquals(emptyList<String>(), groupFilterOptions(emptyList(), null))
    }

    @Test
    fun `the selected group is matched ignoring accents and case`() {
        assertTrue(isSelectedGroup("Bíceps", "biceps"))
        assertFalse(isSelectedGroup("Pecho", "biceps"))
        assertFalse(isSelectedGroup("Pecho", null))
    }

    @Test
    fun `the type value is Todos without a type and the type label with one`() {
        assertEquals(R.string.exercises_filter_any, typeFilterValueRes(null))
        assertEquals(R.string.exercise_type_machine, typeFilterValueRes(ExerciseType.MAQUINA))
    }
}
