package com.dlunaunizar.bobitos.feature.ingredients

import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.data.repository.DeletedIngredient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeletedIngredientRelayTest {
    private val auth = FakeIngredientAuthRepository()

    private fun deleted(uid: String = "me", id: String = "canela") =
        DeletedIngredient(CatalogIngredient(id, id, personal = true), emptyList(), emptyList(), uid)

    @Test
    fun `a post is kept with its mark until consumed`() = runTest {
        val relay = DeletedIngredientRelay(auth, backgroundScope, testScheduler.timeSource)
        runCurrent()

        val post = deleted()
        relay.post(post)

        val relayed = relay.deleted.value
        assertEquals(post, relayed?.deleted)
        assertNotNull(relayed?.since)

        relay.consume(relayed!!)
        assertNull(relay.deleted.value)
    }

    @Test
    fun `consume only clears the same post`() = runTest {
        val relay = DeletedIngredientRelay(auth, backgroundScope, testScheduler.timeSource)
        runCurrent()
        relay.post(deleted(id = "canela"))
        val first = relay.deleted.value!!
        relay.post(deleted(id = "tomillo"))

        relay.consume(first)

        assertEquals("tomillo", relay.deleted.value?.deleted?.ingredient?.id)
    }

    @Test
    fun `signing out clears it`() = runTest {
        val relay = DeletedIngredientRelay(auth, backgroundScope, testScheduler.timeSource)
        runCurrent()
        relay.post(deleted())

        auth.user.value = null
        runCurrent()

        assertNull(relay.deleted.value)
    }

    @Test
    fun `signing in with another account clears it`() = runTest {
        val relay = DeletedIngredientRelay(auth, backgroundScope, testScheduler.timeSource)
        runCurrent()
        relay.post(deleted())

        auth.user.value = ingredientTestUser("ana")
        runCurrent()

        assertNull(relay.deleted.value)
    }

    @Test
    fun `a refresh of the same account keeps it`() = runTest {
        val relay = DeletedIngredientRelay(auth, backgroundScope, testScheduler.timeSource)
        runCurrent()
        relay.post(deleted())

        auth.user.value = ingredientTestUser("me").copy(displayName = "Otro nombre")
        runCurrent()

        assertNotNull(relay.deleted.value)
    }
}
