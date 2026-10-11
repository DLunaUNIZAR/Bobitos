package com.dlunaunizar.bobitos.feature.exercises

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.model.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExerciseFilterMenusUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var filter by mutableStateOf(ExerciseFilter())

    private fun setMenus(modifier: Modifier = Modifier) {
        compose.setContent {
            BobitosTheme {
                Box(modifier) {
                    ExerciseFilterMenus(
                        groups = listOf("Pecho", "Bíceps"),
                        filter = filter,
                        onTypeChange = { filter = filter.copy(type = it) },
                        onGroupChange = { filter = filter.copy(muscleGroup = it) },
                    )
                }
            }
        }
    }

    @Test fun pickingAnOptionReleasesFocus() {
        setMenus()
        compose.onNodeWithTag(EXERCISE_FILTER_TYPE_TAG).performClick()
        compose.onNodeWithText("Peso libre").performClick()
        compose.waitForIdle()
        assertEquals(ExerciseType.PESO_LIBRE, filter.type)
        compose.onNodeWithTag(EXERCISE_FILTER_TYPE_TAG).assertIsNotFocused()
    }

    @Test fun dismissingTheMenuReleasesFocus() {
        setMenus()
        compose.onNodeWithTag(EXERCISE_FILTER_TYPE_TAG).performClick()
        compose.onNodeWithText("Peso libre").assertExists()
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag(EXERCISE_FILTER_TYPE_TAG).assertIsNotFocused()
    }

    @Test fun bothFieldsHaveTheSameHeightWithLargeFontsInANarrowSpace() {
        compose.setContent {
            BobitosTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f),
                ) {
                    Box(Modifier.width(260.dp)) {
                        ExerciseFilterMenus(
                            groups = listOf("Pecho", "Bíceps"),
                            filter = filter,
                            onTypeChange = { filter = filter.copy(type = it) },
                            onGroupChange = { filter = filter.copy(muscleGroup = it) },
                        )
                    }
                }
            }
        }
        val type = compose.onNodeWithTag(EXERCISE_FILTER_TYPE_TAG).fetchSemanticsNode().size.height
        val group = compose.onNodeWithTag(EXERCISE_FILTER_GROUP_TAG).fetchSemanticsNode().size.height
        assertEquals(type, group)
    }
}
