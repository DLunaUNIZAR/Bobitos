package com.dlunaunizar.bobitos.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceTabsTest {
    @Test
    fun laBarraTieneLasCincoPestanasEnOrden() {
        assertEquals(
            listOf(
                BobitosDestination.SpaceHome,
                BobitosDestination.Calendar,
                BobitosDestination.Tasks,
                BobitosDestination.Shopping,
                BobitosDestination.SpaceMore,
            ),
            BobitosDestination.workspaceTabs,
        )
    }

    @Test
    fun cadaRutaDelEspacioResaltaSuPestana() {
        assertEquals(BobitosDestination.SpaceHome, workspaceTabFor("space-home"))
        assertEquals(BobitosDestination.Calendar, workspaceTabFor("calendar"))
        assertEquals(BobitosDestination.Calendar, workspaceTabFor(CALENDAR_EVENT_ROUTE))
        assertEquals(BobitosDestination.Tasks, workspaceTabFor("tasks"))
        assertEquals(BobitosDestination.Shopping, workspaceTabFor("shopping"))
    }

    @Test
    fun comidasYDeporteResaltanMas() {
        assertEquals(BobitosDestination.SpaceMore, workspaceTabFor("space-more"))
        assertEquals(BobitosDestination.SpaceMore, workspaceTabFor("meals"))
        assertEquals(BobitosDestination.SpaceMore, workspaceTabFor("sport"))
    }

    @Test
    fun lasRutasFueraDelEspacioNoTienenPestana() {
        assertNull(workspaceTabFor(null))
        assertNull(workspaceTabFor("home"))
        assertNull(workspaceTabFor("recipes"))
        assertNull(workspaceTabFor("profile"))
    }
}
