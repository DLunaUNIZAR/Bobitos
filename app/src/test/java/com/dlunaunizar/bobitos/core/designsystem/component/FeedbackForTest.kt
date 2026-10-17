package com.dlunaunizar.bobitos.core.designsystem.component

import com.dlunaunizar.bobitos.core.common.EditorSaveStatus.FAILED
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus.IDLE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedbackForTest {
    @Test
    fun anErrorAlreadyShownInTheOpenSheetIsNotRepeated() {
        assertNull(feedbackFor("e", null, FAILED, editorOpen = true))
    }

    @Test
    fun theErrorWinsOverTheNotice() {
        assertEquals(Feedback("e", true), feedbackFor("e", "n", IDLE, editorOpen = false))
    }

    @Test
    fun aNoticeAloneIsInformative() {
        assertEquals(Feedback("n", false), feedbackFor(null, "n", IDLE, false))
    }

    @Test
    fun aFailedSaveWithTheSheetClosedStillShowsTheError() {
        assertEquals(Feedback("e", true), feedbackFor("e", null, FAILED, editorOpen = false))
    }
}
