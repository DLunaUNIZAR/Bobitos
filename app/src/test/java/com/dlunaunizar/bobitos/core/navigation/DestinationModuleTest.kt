package com.dlunaunizar.bobitos.core.navigation

import com.dlunaunizar.bobitos.core.designsystem.theme.AppModule
import org.junit.Assert.assertEquals
import org.junit.Test

class DestinationModuleTest {
    @Test
    fun cadaDestinoUsaElColorDeSuModulo() {
        val esperado = mapOf(
            BobitosDestination.Shopping to AppModule.SHOPPING,
            BobitosDestination.Tasks to AppModule.TASKS,
            BobitosDestination.Calendar to AppModule.CALENDAR,
            BobitosDestination.MyCalendar to AppModule.CALENDAR,
            BobitosDestination.Meals to AppModule.MEALS,
            BobitosDestination.Recipes to AppModule.MEALS,
            BobitosDestination.Ingredients to AppModule.MEALS,
            BobitosDestination.Sport to AppModule.SPORT,
            BobitosDestination.Exercises to AppModule.SPORT,
            BobitosDestination.Routines to AppModule.SPORT,
            BobitosDestination.Notes to AppModule.NOTES,
            BobitosDestination.Pantry to AppModule.PANTRY,
            BobitosDestination.Home to null,
            BobitosDestination.Spaces to null,
            BobitosDestination.SpaceHome to null,
            BobitosDestination.SpaceMore to null,
            BobitosDestination.Profile to null,
            BobitosDestination.SpaceSettings to null,
        )
        assertEquals(BobitosDestination.entries.toSet(), esperado.keys)
        esperado.forEach { (destino, modulo) -> assertEquals(destino.name, modulo, destino.appModule()) }
    }
}
