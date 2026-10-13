package com.dlunaunizar.bobitos.feature.shopping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShoppingValidationTest {
    @Test
    fun `name is required`() {
        assertEquals(
            ShoppingUiMessage.NameRequired,
            ShoppingValidation.validate("   ", null, null),
        )
    }

    @Test
    fun `all field limits are validated`() {
        assertEquals(
            ShoppingUiMessage.NameTooLong,
            ShoppingValidation.validate("a".repeat(121), null, null),
        )
        assertEquals(
            ShoppingUiMessage.QuantityTooLong,
            ShoppingValidation.validate("Pan", "a".repeat(41), null),
        )
        assertEquals(
            ShoppingUiMessage.NotesTooLong,
            ShoppingValidation.validate("Pan", null, "a".repeat(501)),
        )
    }

    @Test
    fun `valid optional values pass validation`() {
        assertNull(ShoppingValidation.validate("Pan", "2 barras", "Integral"))
    }

    @Test
    fun `the brand is limited to 60 characters once trimmed`() {
        assertNull(ShoppingValidation.validate("Pan", null, null, "  " + "M".repeat(60) + "  "))
        assertEquals(
            ShoppingUiMessage.BrandTooLong,
            ShoppingValidation.validate("Pan", null, null, "M".repeat(61)),
        )
        assertNull(ShoppingValidation.validate("Pan", null, null, null))
    }
}
