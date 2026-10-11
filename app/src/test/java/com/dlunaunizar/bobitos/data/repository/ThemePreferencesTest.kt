package com.dlunaunizar.bobitos.data.repository

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import com.dlunaunizar.bobitos.core.designsystem.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class ThemePreferencesTest {
    @Test fun `no stored theme reads as dark`() = runTest {
        val source: Flow<Preferences> = flow { emit(emptyPreferences()) }
        assertEquals(ThemeMode.DARK, source.themeModes().first())
    }

    @Test fun `a stored light theme reads as light`() = runTest {
        val source: Flow<Preferences> = flow { emit(preferencesOf(THEME_MODE_KEY to ThemeMode.LIGHT.name)) }
        assertEquals(ThemeMode.LIGHT, source.themeModes().first())
    }

    @Test fun `an unreadable settings file reads as dark instead of crashing`() = runTest {
        val source: Flow<Preferences> = flow { throw IOException("corrupt") }
        assertEquals(ThemeMode.DARK, source.themeModes().first())
    }

    @Test fun `other errors are not swallowed`() = runTest {
        val source: Flow<Preferences> = flow { throw IllegalStateException("boom") }
        assertThrows(IllegalStateException::class.java) { runTest { source.themeModes().first() } }
    }
}
