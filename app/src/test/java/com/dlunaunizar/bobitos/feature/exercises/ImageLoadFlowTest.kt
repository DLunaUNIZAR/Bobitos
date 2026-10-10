package com.dlunaunizar.bobitos.feature.exercises

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImageLoadFlowTest {
    @Test
    fun `al cambiar de clave emite null antes del nuevo valor y nunca el antiguo`() =
        runTest(UnconfinedTestDispatcher()) {
            val gates = mapOf("a" to CompletableDeferred<String?>(), "b" to CompletableDeferred<String?>())
            val key = MutableStateFlow("a")
            val seen = mutableListOf<String?>()
            val job = launch {
                key.flatMapLatest { k -> imageLoadFlow { gates.getValue(k).await() } }.collect { seen += it }
            }
            gates.getValue("a").complete("imagen-a")
            key.value = "b"
            assertEquals(listOf(null, "imagen-a", null), seen)
            gates.getValue("b").complete("imagen-b")
            assertEquals(listOf(null, "imagen-a", null, "imagen-b"), seen)
            job.cancel()
        }
}
