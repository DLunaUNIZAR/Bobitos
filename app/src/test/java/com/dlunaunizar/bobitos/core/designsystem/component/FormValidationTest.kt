package com.dlunaunizar.bobitos.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormValidationTest {
    @Test fun noErrorIsVisibleBeforeTheFirstAttempt() {
        assertNull(FormValidationState().visible("e"))
    }

    @Test fun anInvalidAttemptRevealsTheErrorsAndCallsOnInvalid() {
        val state = FormValidationState()
        var invalid = false
        state.submit(isValid = false, onValid = { error("no debe guardar") }, onInvalid = { invalid = true })
        assertTrue(state.attempted)
        assertTrue(invalid)
        assertEquals("e", state.visible("e"))
    }

    @Test fun aValidAttemptCallsOnValid() {
        val state = FormValidationState()
        var called = false
        state.submit(isValid = true, onValid = { called = true }, onInvalid = { error("no debe avisar") })
        assertTrue(called)
    }

    @Test fun aMissingErrorStaysNullAfterTheAttempt() {
        val state = FormValidationState(attempted = true)
        assertNull(state.visible<String>(null))
        assertFalse(FormValidationState().attempted)
    }
}
