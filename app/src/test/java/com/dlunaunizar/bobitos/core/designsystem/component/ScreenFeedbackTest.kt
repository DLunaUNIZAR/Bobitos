package com.dlunaunizar.bobitos.core.designsystem.component

import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.SaveState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenFeedbackTest {
    @Test
    fun `nothing shows while something covers the screen feedback`() {
        assertNull(SaveState<String, String>(error = "e", notice = "n").screenFeedback(covered = true))
    }

    @Test
    fun `a notice that waited while covered shows once uncovered`() {
        val save = SaveState<String, String>(notice = "Tarea guardada")

        assertNull(save.screenFeedback(covered = true))
        assertEquals(ScreenFeedback.Notice("Tarea guardada"), save.screenFeedback(covered = false))
    }

    @Test
    fun `the error goes before the notice`() {
        val save = SaveState<String, String>(error = "e", notice = "n")

        assertEquals(ScreenFeedback.Error("e"), save.screenFeedback(covered = false))
    }

    @Test
    fun `the notice shows once the error is consumed`() {
        val save = SaveState<String, String>(error = null, notice = "n")

        assertEquals(ScreenFeedback.Notice("n"), save.screenFeedback(covered = false))
    }

    @Test
    fun `the editor error never shows in the screen feedback`() {
        val save = SaveState<String, String>(editor = EditorSaveStatus.FAILED, editorError = "dentro")

        assertNull(save.screenFeedback(covered = false))
    }
}
