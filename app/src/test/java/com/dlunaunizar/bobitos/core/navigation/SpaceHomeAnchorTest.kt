package com.dlunaunizar.bobitos.core.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceHomeAnchorTest {
    private val home = BobitosDestination.Home.route
    private val spaceHome = BobitosDestination.SpaceHome.route

    @Test
    fun `from the main menu ingredients the space hub must be anchored first`() {
        val stack = listOf(home, BobitosDestination.Ingredients.route, "ingredient/{ingredientId}")

        assertTrue(needsSpaceHomeAnchor(stack))
    }

    @Test
    fun `from meals the space hub is already in the stack`() {
        assertFalse(needsSpaceHomeAnchor(listOf(home, spaceHome, BobitosDestination.Meals.route)))
    }

    @Test
    fun `an empty or unknown stack needs the anchor`() {
        assertTrue(needsSpaceHomeAnchor(emptyList()))
        assertTrue(needsSpaceHomeAnchor(listOf(null)))
    }
}
