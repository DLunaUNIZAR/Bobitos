package com.dlunaunizar.bobitos.core.navigation

import com.dlunaunizar.bobitos.app.RealtimeScope
import org.junit.Assert.assertEquals
import org.junit.Test

class RealtimeScopeForTest {
    @Test
    fun sinRutaTodavia() {
        assertEquals(RealtimeScope.AUTOMATIC, realtimeScopeFor(null, spacePickerOpen = false))
    }

    @Test
    fun lasPantallasDeVariosEspaciosEscuchanTodos() {
        listOf("home", "spaces", "my-calendar").forEach {
            assertEquals(it, RealtimeScope.ALL_SPACES, realtimeScopeFor(it, spacePickerOpen = false))
        }
    }

    @Test
    fun lasPantallasSinEspacioPausanLaEscucha() {
        listOf("profile", "recipes", "ingredients", "exercises", "routines", INGREDIENT_DETAIL_ROUTE).forEach {
            assertEquals(it, RealtimeScope.PAUSED, realtimeScopeFor(it, spacePickerOpen = false))
        }
    }

    @Test
    fun lasPantallasDeUnEspacioEscuchanSoloElActivo() {
        listOf(
            "space-home", "space-more", "tasks", "shopping", "calendar", CALENDAR_EVENT_ROUTE,
            "meals", "sport", "notes", "space-settings",
        ).forEach {
            assertEquals(it, RealtimeScope.ACTIVE_SPACE, realtimeScopeFor(it, spacePickerOpen = false))
        }
    }

    @Test
    fun conElSelectorDeEspacioAbiertoSeEscuchanTodosLosEspacios() {
        assertEquals(RealtimeScope.ALL_SPACES, realtimeScopeFor("space-home", spacePickerOpen = true))
        assertEquals(RealtimeScope.ALL_SPACES, realtimeScopeFor("tasks", spacePickerOpen = true))
    }
}
