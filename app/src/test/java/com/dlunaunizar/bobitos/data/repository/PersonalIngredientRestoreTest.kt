package com.dlunaunizar.bobitos.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersonalIngredientRestoreTest {
    @Test
    fun `only the account that deleted it can restore it`() {
        assertNull(restoreOwnerFailure(ownerUid = "me", currentUid = "me"))
        assertEquals(IngredientFailure.PermissionDenied, restoreOwnerFailure(ownerUid = "ana", currentUid = "me"))
    }
}
