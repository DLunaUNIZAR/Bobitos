package com.dlunaunizar.bobitos.feature.spaces

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpaceValidationTest {
    @Test
    fun `space name is required`() {
        assertEquals(SpaceUiMessage.NameRequired, SpaceValidation.validateName("   "))
    }

    @Test
    fun `space name cannot exceed the limit`() {
        assertEquals(
            SpaceUiMessage.NameTooLong,
            SpaceValidation.validateName("a".repeat(SpaceValidation.MAX_NAME_LENGTH + 1)),
        )
    }

    @Test
    fun `valid space name is accepted`() {
        assertNull(SpaceValidation.validateName("Piso compartido"))
    }
}
