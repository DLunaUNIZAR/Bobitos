package com.dlunaunizar.bobitos.feature.auth

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.designsystem.theme.ThemeMode
import com.dlunaunizar.bobitos.data.repository.ThemePreferenceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeThemePreferenceRepository()
    private val viewModel by lazy { ThemeViewModel(repository) }

    @Test
    fun `before the preference loads the theme is dark`() {
        assertEquals(ThemeMode.DARK, viewModel.themeMode.value)
    }

    @Test
    fun `a stored light choice is reflected`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.themeMode.value
        repository.emitted.emit(ThemeMode.LIGHT)

        assertEquals(ThemeMode.LIGHT, viewModel.themeMode.value)
    }

    private class FakeThemePreferenceRepository : ThemePreferenceRepository {
        val emitted = MutableSharedFlow<ThemeMode>()
        override val themeMode: Flow<ThemeMode> = emitted

        override suspend fun setThemeMode(mode: ThemeMode) = emitted.emit(mode)
    }
}
