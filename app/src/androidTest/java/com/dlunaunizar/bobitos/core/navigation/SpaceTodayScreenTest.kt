package com.dlunaunizar.bobitos.core.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.model.Meal
import com.dlunaunizar.bobitos.core.model.MealSlot
import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskStatus
import com.dlunaunizar.bobitos.feature.spaces.AgendaEntry
import com.dlunaunizar.bobitos.feature.spaces.AgendaPerson
import com.dlunaunizar.bobitos.feature.spaces.AgendaTask
import com.dlunaunizar.bobitos.feature.spaces.MemberWorkload
import com.dlunaunizar.bobitos.feature.spaces.ShoppingGlance
import com.dlunaunizar.bobitos.feature.spaces.TodayAgenda
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SpaceTodayScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val esEncabezado = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
    private val abiertos = mutableListOf<BobitosDestination>()
    private val marcadas = mutableListOf<TaskItem>()
    private val terminadas = mutableListOf<(Boolean) -> Unit>()

    private fun tarea(id: String, titulo: String) = TaskItem(
        id = id,
        title = titulo,
        description = null,
        assigneeId = null,
        assigneeName = null,
        dueAt = Instant.now(),
        priority = TaskPriority.HIGH,
        status = TaskStatus.TODO,
        createdBy = "u1",
        createdByName = "Ana",
        createdAt = Instant.EPOCH,
        updatedBy = "u1",
        updatedAt = Instant.EPOCH,
        completedBy = null,
        completedByName = null,
        completedAt = null,
    )

    private fun albondigas() = Meal(
        id = "m1",
        date = LocalDate.now(),
        slot = MealSlot.COMIDA,
        name = "Albóndigas en salsa",
        participantIds = listOf("u1", "u2", "u3"),
        participantNames = listOf("Ana", "Luis", "Eva"),
        createdBy = "u1",
        createdByName = "Ana",
        createdAt = Instant.EPOCH,
        updatedBy = "u1",
        updatedAt = Instant.EPOCH,
    )

    private fun agenda(
        tareas: List<TaskItem> = listOf(tarea("t1", "Fregar"), tarea("t2", "Sacar la basura"), tarea("t3", "Regar")),
        ocultas: Int = 0,
        compra: ShoppingGlance = ShoppingGlance(5, listOf("Leche", "pan", "huevos")),
        entradas: List<AgendaEntry> = listOf(
            AgendaEntry.Meal(MealSlot.COMIDA, albondigas(), AgendaPerson.You, eaters = 3),
            AgendaEntry.Meal(MealSlot.CENA, meal = null, cook = null, eaters = 0),
        ),
    ) = TodayAgenda(
        tasks = tareas.map { AgendaTask(it, overdueSince = null, owner = AgendaPerson.Nobody) },
        hiddenTasks = ocultas,
        entries = entradas,
        shopping = compra,
        workload = listOf(MemberWorkload("u1", "Ana", pending = 2, doneThisWeek = 1)),
        spaceId = "casa",
    )

    private fun mostrar(agenda: TodayAgenda = agenda(), canWrite: Boolean = true) {
        compose.setContent {
            BobitosTheme {
                SpaceTodayScreen(
                    agenda = UiState.Content(agenda),
                    canWrite = canWrite,
                    onToggleTask = { tarea, alTerminar ->
                        marcadas += tarea
                        terminadas += alTerminar
                    },
                    onRetry = {},
                    onOpen = { abiertos += it },
                )
            }
        }
    }

    @Test
    fun lasCabecerasSonEncabezados() {
        mostrar()

        compose.onNodeWithText("Tareas para hoy · 3").assert(esEncabezado)
        compose.onNodeWithText("Agenda").assert(esEncabezado)
        compose.onNodeWithText("Reparto de tareas").performScrollTo().assert(esEncabezado)
    }

    @Test
    fun laCasillaDeLaTareaTieneNombreYEstado() {
        mostrar()

        val casilla = compose.onNode(hasContentDescription("Fregar"))
        casilla.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        casilla.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        casilla.performClick()

        assertEquals(listOf("t1"), marcadas.map { it.id })
        assertEquals(emptyList<BobitosDestination>(), abiertos)
    }

    @Test
    fun volverATocarLaCasillaMientrasSaleNoLaCompletaOtraVez() {
        mostrar()

        val casilla = compose.onNode(hasContentDescription("Fregar"))
        casilla.performClick()
        casilla.performClick()

        assertEquals(listOf("t1"), marcadas.map { it.id })
    }

    @Test
    fun siLaEscrituraFallaLaCasillaVuelveADesmarcadaYSePuedeReintentar() {
        mostrar()

        val casilla = compose.onNode(hasContentDescription("Fregar"))
        casilla.performClick()
        casilla.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        compose.runOnIdle { terminadas.single()(false) }

        casilla.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        casilla.performClick()
        assertEquals(listOf("t1", "t1"), marcadas.map { it.id })
    }

    @Test
    fun conLetraGrandeLaHoraDeLaFilaNoSeParteEnMitadDePalabra() {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 1.3f)) {
                BobitosTheme {
                    Box(Modifier.width(360.dp)) {
                        SpaceTodayScreen(
                            agenda = UiState.Content(
                                agenda(
                                    entradas = listOf(
                                        AgendaEntry.Meal(MealSlot.DESAYUNO, meal = null, cook = null, eaters = 0),
                                        AgendaEntry.Meal(MealSlot.CENA, meal = null, cook = null, eaters = 0),
                                    ),
                                ),
                            ),
                            canWrite = true,
                            onToggleTask = { _, _ -> },
                            onRetry = {},
                            onOpen = {},
                        )
                    }
                }
            }
        }

        // Una sola línea: la columna de la hora crece hasta la etiqueta más larga en vez de partirla.
        val resultados = mutableListOf<TextLayoutResult>()
        val etiqueta = compose.onNode(hasText("Desayuno"), useUnmergedTree = true).performScrollTo()
        etiqueta.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        assertEquals(1, resultados.single().lineCount)
    }

    @Test
    fun tocarLaFilaFueraDeLaCasillaLlevaATareas() {
        mostrar()

        compose.onNodeWithText("Fregar").performClick()

        assertEquals(listOf(BobitosDestination.Tasks), abiertos)
        assertEquals(emptyList<TaskItem>(), marcadas)
    }

    @Test
    fun sinConexionLaCasillaNoSePuedeMarcar() {
        mostrar(canWrite = false)

        compose.onNode(hasContentDescription("Fregar")).assertIsNotEnabled()
    }

    @Test
    fun laFilaDeAgendaSeLeeEntera() {
        mostrar()

        compose.onNodeWithContentDescription("Comida: Albóndigas en salsa, cocinas tú, comen 3").assertIsDisplayed()
    }

    @Test
    fun masDeCincoTareasOfreceVerlasEnTareas() {
        mostrar(agenda(tareas = (1..5).map { tarea("t$it", "Tarea $it") }, ocultas = 2))

        compose.onNodeWithText("Tareas para hoy · 7").assert(esEncabezado)
        compose.onNodeWithText("Ver las 7 en Tareas").performClick()

        assertEquals(listOf(BobitosDestination.Tasks), abiertos)
    }

    @Test
    fun laCompraACeroDiceQueEstaAlDia() {
        mostrar(agenda(compra = ShoppingGlance(0, emptyList())))

        compose.onNodeWithText("La compra está al día").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun laCompraListaLosPrimerosProductos() {
        mostrar()

        compose.onNodeWithText("Compra · 5").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Leche, pan, huevos y 2 más")).assertIsDisplayed()
    }

    @Test
    fun laFranjaSinPlanificarLlevaAComidas() {
        mostrar()

        compose.onNodeWithContentDescription("Cena: Sin planificar").performClick()

        assertEquals(listOf(BobitosDestination.Meals), abiertos)
    }
}
