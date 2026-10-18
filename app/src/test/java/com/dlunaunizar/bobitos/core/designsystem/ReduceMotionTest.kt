package com.dlunaunizar.bobitos.core.designsystem

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReduceMotionTest {
    @Test
    fun cambiarLaEscalaConLaAppAbiertaSeNota() = runTest(UnconfinedTestDispatcher()) {
        var escala = 1f
        val cambios = MutableSharedFlow<Unit>()
        val vistos = mutableListOf<Boolean>()
        val job = launch { reduceMotionUpdates({ escala }, cambios).toList(vistos) }
        escala = 0f
        cambios.emit(Unit)
        job.cancel()
        assertEquals(listOf(false, true), vistos)
    }

    @Test
    fun unaEmisionSinCambioDeEscalaNoRepite() = runTest(UnconfinedTestDispatcher()) {
        val cambios = MutableSharedFlow<Unit>()
        val vistos = mutableListOf<Boolean>()
        val job = launch { reduceMotionUpdates({ 1f }, cambios).toList(vistos) }
        cambios.emit(Unit)
        job.cancel()
        assertEquals(listOf(false), vistos)
    }
}
