package com.dlunaunizar.bobitos.feature.exercises

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.toMutableStateList
import com.dlunaunizar.bobitos.core.model.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseDraftSaverTest {
    private val allSaveable = SaverScope { true }

    private fun roundTrip(drafts: List<ExerciseDraft>) = with(ExerciseDraftListSaver) {
        restore(allSaveable.save(drafts.toMutableStateList())!!)!!
    }

    @Test
    fun laListaVaciaSobreviveAUnaRotacion() {
        assertTrue(roundTrip(emptyList()).isEmpty())
    }

    @Test
    fun laListaVaciaSeGuardaComoValorYNoComoAusencia() {
        val saved = with(ExerciseDraftListSaver) { allSaveable.save(emptyList<ExerciseDraft>().toMutableStateList()) }
        assertTrue("una lista vacía no debe guardarse como null", saved != null)
    }

    @Test
    fun unaSesionDeFuerzaConSeriesSobreviveAUnaRotacion() {
        val original = listOf(
            ExerciseDraft(
                name = "Press banca",
                exerciseId = "e1",
                type = ExerciseType.PESO_LIBRE,
                sets = listOf(SetDraft("10", "60"), SetDraft("8", "62,5")),
                notes = "pausa de 90 s",
            ),
        )
        val restored = roundTrip(original)
        assertEquals(original.toRoutineExercises(), restored.toRoutineExercises())
        assertEquals("62,5", restored.single().sets[1].weight)
        assertEquals("e1", restored.single().exerciseId)
    }

    @Test
    fun unEjercicioDeCardioPersonalizadoSobreviveAUnaRotacion() {
        val original = listOf(
            ExerciseDraft(name = "Cinta", type = ExerciseType.CARDIO, duration = "20", level = "alto"),
            ExerciseDraft(name = "", type = ExerciseType.MAQUINA),
        )
        val restored = roundTrip(original)
        assertEquals(2, restored.size)
        assertEquals("Cinta", restored[0].name)
        assertEquals(null, restored[0].exerciseId)
        assertEquals(ExerciseType.CARDIO, restored[0].type)
        assertEquals("20", restored[0].duration)
        assertEquals("alto", restored[0].level)
        assertEquals(ExerciseType.MAQUINA, restored[1].type)
    }

    @Test
    fun laListaRestauradaEsEditableYNoComparteEstadoConLaOriginal() {
        val original = listOf(ExerciseDraft(name = "Remo", type = ExerciseType.CARDIO))
        val restored = roundTrip(original)
        restored.single().name = "Otro"
        assertEquals("Remo", original.single().name)
    }
}
