package com.dlunaunizar.bobitos.core.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SpaceHomeAnchorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var nav: NavHostController

    @Before
    fun setUp() {
        compose.setContent {
            nav = rememberNavController()
            NavHost(navController = nav, startDestination = BobitosDestination.Home.route) {
                composable(BobitosDestination.Home.route) {}
                composable(BobitosDestination.Ingredients.route) {}
                composable(BobitosDestination.SpaceHome.route) {}
                composable(BobitosDestination.Meals.route) {}
            }
        }
    }

    @Test
    fun fromTheMainMenuIngredientsTheSpaceHubMustBeAnchoredFirst() {
        compose.runOnIdle { nav.navigate(BobitosDestination.Ingredients.route) }

        compose.runOnIdle { assertTrue(nav.needsSpaceHomeAnchor()) }
    }

    @Test
    fun fromMealsTheSpaceHubIsAlreadyInTheStack() {
        compose.runOnIdle { nav.navigate(BobitosDestination.SpaceHome.route) }
        compose.runOnIdle { nav.navigate(BobitosDestination.Meals.route) }

        compose.runOnIdle { assertFalse(nav.needsSpaceHomeAnchor()) }
    }

    @Test
    fun onlyTheStartDestinationNeedsTheAnchor() {
        compose.runOnIdle { assertTrue(nav.needsSpaceHomeAnchor()) }
    }
}
