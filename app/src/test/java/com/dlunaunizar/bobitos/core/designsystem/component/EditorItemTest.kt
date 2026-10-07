package com.dlunaunizar.bobitos.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditorItemTest {
    @Test
    fun siElElementoEstaSeUsa() {
        assertEquals("nuevo", stickyEditorItem("nuevo", last = "viejo", loaded = true))
        assertEquals("nuevo", stickyEditorItem("nuevo", last = "viejo", loaded = false))
    }

    @Test
    fun mientrasLaListaRecargaSeMantieneElUltimoElemento() {
        assertEquals("viejo", stickyEditorItem<String>(null, last = "viejo", loaded = false))
    }

    @Test
    fun siLaListaYaCargoYElElementoNoEstaSeDescarta() {
        assertNull(stickyEditorItem<String>(null, last = "viejo", loaded = true))
    }

    @Test
    fun sinElementoNiUltimoNoHayNada() {
        assertNull(stickyEditorItem<String>(null, last = null, loaded = false))
    }
}
