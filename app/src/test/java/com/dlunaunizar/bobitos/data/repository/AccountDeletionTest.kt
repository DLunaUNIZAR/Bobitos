package com.dlunaunizar.bobitos.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AccountDeletionTest {
    private class FakeSteps(
        private val memberships: List<ActiveMembership>,
        private val personalDataError: Throwable? = null,
    ) : AccountDeletionSteps {
        val calls = mutableListOf<String>()

        override suspend fun requireWritable() {
            calls += "requireWritable"
        }

        override suspend fun reauthenticate(password: String) {
            calls += "reauthenticate"
        }

        override suspend fun activeMemberships(): List<ActiveMembership> {
            calls += "memberships"
            return memberships
        }

        override suspend fun deletePersonalData() {
            calls += "deletePersonalData"
            personalDataError?.let { throw it }
        }

        override suspend fun leaveSpace(spaceId: String) {
            calls += "leaveSpace($spaceId)"
        }

        override suspend fun anonymizeOrDeleteOwnedRecipes() {
            calls += "recipes"
        }

        override suspend fun deleteUser() {
            calls += "deleteUser"
        }
    }

    @Test
    fun `an owner of a space gets OwnerSpacesRemaining and nothing is deleted`() = runTest {
        val steps = FakeSteps(listOf(ActiveMembership("s1", isOwner = false), ActiveMembership("s2", isOwner = true)))
        val error = runCatching { deleteAccountWith(steps, "secret") }.exceptionOrNull()
        assertTrue(error is AccountRepositoryException)
        assertEquals(AccountFailure.OwnerSpacesRemaining, (error as AccountRepositoryException).failure)
        assertEquals(listOf("requireWritable", "reauthenticate", "memberships"), steps.calls)
    }

    @Test
    fun `personal data is deleted before leaving spaces and the user is deleted last`() = runTest {
        val steps = FakeSteps(listOf(ActiveMembership("s1", isOwner = false), ActiveMembership("s2", isOwner = false)))
        deleteAccountWith(steps, "secret")
        assertEquals(
            listOf(
                "requireWritable",
                "reauthenticate",
                "memberships",
                "deletePersonalData",
                "leaveSpace(s1)",
                "leaveSpace(s2)",
                "recipes",
                "deleteUser",
            ),
            steps.calls,
        )
    }

    @Test
    fun `a failure deleting personal data leaves spaces and the account untouched`() = runTest {
        val steps = FakeSteps(
            listOf(ActiveMembership("s1", isOwner = false)),
            personalDataError = IllegalStateException("boom"),
        )
        try {
            deleteAccountWith(steps, "secret")
            fail("debía lanzar")
        } catch (error: AccountRepositoryException) {
            assertEquals(AccountFailure.Unknown, error.failure)
            assertNotNull(error.cause)
        }
        assertEquals(listOf("requireWritable", "reauthenticate", "memberships", "deletePersonalData"), steps.calls)
    }
}
