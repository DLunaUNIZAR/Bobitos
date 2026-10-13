package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.SpaceRole
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveSpaceTest {
    private val home = MembershipSnapshot("home", SpaceRole.MEMBER)
    private val other = MembershipSnapshot("other", SpaceRole.OWNER)

    private fun summaryOf(membership: MembershipSnapshot) = SpaceSummary(membership.spaceId, "Casa", 2, membership.role)

    @Test
    fun `the active space is null while its membership is gone and comes back with it`() = runTest {
        val memberships = MutableStateFlow(listOf(home, other))
        val seen = mutableListOf<SpaceSummary?>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            activeSpaceOf(memberships, "home") { flowOf(summaryOf(it)) }.toList(seen)
        }

        memberships.value = listOf(other)
        runCurrent()
        memberships.value = listOf(home, other)
        runCurrent()
        job.cancel()

        assertEquals(listOf(summaryOf(home), null, summaryOf(home)), seen)
    }

    @Test
    fun `without its membership from the start the active space is null`() = runTest {
        val seen = mutableListOf<SpaceSummary?>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            activeSpaceOf(MutableStateFlow(listOf(other)), "home") { flowOf(summaryOf(it)) }.toList(seen)
        }
        job.cancel()

        assertEquals(listOf<SpaceSummary?>(null), seen)
    }

    @Test
    fun `changes in other memberships do not listen to the active space again`() = runTest {
        val memberships = MutableStateFlow(listOf(home))
        var listened = 0
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            activeSpaceOf(memberships, "home") {
                listened++
                flowOf(summaryOf(it))
            }.collect {}
        }

        memberships.value = listOf(home, other)
        runCurrent()
        job.cancel()

        assertEquals(1, listened)
    }
}
