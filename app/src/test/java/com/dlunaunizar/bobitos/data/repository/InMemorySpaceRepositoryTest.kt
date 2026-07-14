package com.dlunaunizar.bobitos.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemorySpaceRepositoryTest {
    private val repository = InMemorySpaceRepository()

    @Test
    fun `exposes the development space`() = runTest {
        val spaces = repository.spaces.first()

        assertEquals(1, spaces.size)
        assertEquals("Casa", spaces.single().name)
        assertEquals(4, spaces.single().memberCount)
    }
}

