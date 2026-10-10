package com.dlunaunizar.bobitos.feature.exercises

import com.dlunaunizar.bobitos.core.model.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseFilterChipKeysTest {
    @Test
    fun `group chip keys stay unique even if a group is called all`() {
        val keys = groupChipKeys(listOf(":all", " :ALL ", "Pecho"))

        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `the all chip never collides with a group or a type`() {
        val groupKeys = groupChipKeys(listOf(":all", "Pecho"))
        val typeKeys = typeChipKeys(ExerciseType.entries)

        assertEquals(groupKeys.size, groupKeys.toSet().size)
        assertEquals(typeKeys.size, typeKeys.toSet().size)
    }
}
