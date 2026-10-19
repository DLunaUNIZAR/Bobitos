package com.dlunaunizar.bobitos.core.common

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class UndoOffersTest {
    private val deleted = Undoable.Deleted("s", "Pan")

    @Test
    fun `an offer waits in the list until it is consumed`() = runTest {
        val offers = UndoOffers<String>(backgroundScope, testScheduler.timeSource)

        offers.offer(deleted)

        assertEquals(listOf(UndoOffer(1L, deleted)), offers.offers.value)
        offers.consumeOffer(1L)
        assertTrue(offers.offers.value.isEmpty())
    }

    @Test
    fun `an offer expires UNDO_OFFER_TIMEOUT_MILLIS after it was made`() = runTest {
        val offers = UndoOffers<String>(backgroundScope, testScheduler.timeSource)
        offers.offer(deleted)

        advanceTimeBy(9_999)
        runCurrent()
        assertEquals(1, offers.offers.value.size)

        advanceTimeBy(2)
        runCurrent()
        assertTrue(offers.offers.value.isEmpty())
    }

    @Test
    fun `an offer made from an older mark expires counting from that mark`() = runTest {
        val offers = UndoOffers<String>(backgroundScope, testScheduler.timeSource)
        val since = testScheduler.timeSource.markNow()
        advanceTimeBy(6_000)
        offers.offer(deleted, since)

        advanceTimeBy(3_999)
        runCurrent()
        assertEquals(1, offers.offers.value.size)

        advanceTimeBy(2)
        runCurrent()
        assertTrue(offers.offers.value.isEmpty())
    }

    @Test
    fun `an offer whose mark already expired is not added`() = runTest {
        val offers = UndoOffers<String>(backgroundScope, testScheduler.timeSource)
        val since = testScheduler.timeSource.markNow()
        advanceTimeBy(UNDO_OFFER_TIMEOUT_MILLIS)

        offers.offer(deleted, since)

        assertTrue(offers.offers.value.isEmpty())
    }

    @Test
    fun `consuming one offer keeps the others and their own expiry`() = runTest {
        val offers = UndoOffers<String>(backgroundScope, testScheduler.timeSource)
        offers.offer(Undoable.Deleted("s", "A"))
        advanceTimeBy(5_000)
        offers.offer(Undoable.Deleted("s", "B"))

        offers.consumeOffer(1L)
        assertEquals(listOf(2L), offers.offers.value.map { it.serial })

        advanceTimeBy(9_999)
        runCurrent()
        assertEquals(1, offers.offers.value.size)
        advanceTimeBy(2.milliseconds.inWholeMilliseconds)
        runCurrent()
        assertTrue(offers.offers.value.isEmpty())
    }

    @Test
    fun `the same undoable offered twice gives two offers with different serials`() = runTest {
        val offers = UndoOffers<String>(backgroundScope, testScheduler.timeSource)

        offers.offer(deleted)
        offers.offer(deleted)

        val serials = offers.offers.value.map { it.serial }
        assertEquals(2, serials.size)
        assertNotEquals(serials[0], serials[1])
    }
}
