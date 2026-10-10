package com.dlunaunizar.bobitos.feature.exercises

import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.ExerciseEquipment
import com.dlunaunizar.bobitos.core.model.ExerciseInput
import com.dlunaunizar.bobitos.core.model.ExerciseType
import com.dlunaunizar.bobitos.core.model.SetMeasure
import com.dlunaunizar.bobitos.core.model.slug
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class CatalogExerciseDraftTest {
    private fun exercise(muscle: String?) = CatalogExercise(
        id = "e1", name = "Press banca", type = ExerciseType.PESO_LIBRE, muscleGroup = muscle, ownerUid = "u",
        createdBy = "u", createdByName = "U", createdAt = Instant.EPOCH, updatedBy = "u", updatedAt = Instant.EPOCH,
    )

    @Test
    fun unEjercicioNuevoEmpiezaVacioYDeMaquina() {
        val draft = CatalogExerciseDraft.of(null)
        assertEquals(CatalogExerciseDraft("", ExerciseType.MAQUINA, ""), draft)
        assertEquals(ExerciseType.MAQUINA, draft.type)
    }

    @Test
    fun editarCargaSusDatosYUnMusculoNuloEsVacio() {
        assertEquals(
            CatalogExerciseDraft("Press banca", ExerciseType.PESO_LIBRE, "pecho"),
            CatalogExerciseDraft.of(exercise("pecho")),
        )
        assertEquals("", CatalogExerciseDraft.of(exercise(null)).muscle)
    }

    @Test
    fun `editing keeps description and equipment`() {
        val draft = CatalogExerciseDraft.of(
            exercise("pecho").copy(
                description = "Tumbado.",
                equipment = listOf(ExerciseEquipment.BARRA, ExerciseEquipment.BANCO),
            ),
        )
        assertEquals("Tumbado.", draft.description)
        assertEquals(listOf(ExerciseEquipment.BARRA, ExerciseEquipment.BANCO), draft.equipment)
        assertEquals("", CatalogExerciseDraft.of(null).description)
        assertEquals(emptyList<ExerciseEquipment>(), CatalogExerciseDraft.of(null).equipment)
    }

    @Test
    fun `toInput trims and nulls blank description`() {
        val draft = CatalogExerciseDraft(
            name = "  Remo ",
            type = ExerciseType.MAQUINA,
            muscle = " Espalda ",
            description = "   ",
            equipment = listOf(ExerciseEquipment.POLEA),
        )
        assertEquals(
            ExerciseInput("Remo", ExerciseType.MAQUINA, "Espalda", null, listOf(ExerciseEquipment.POLEA)),
            draft.toInput(),
        )
        assertEquals("Texto", draft.copy(description = " Texto ").toInput().description)
    }

    private fun draft(name: String = "Remo", muscle: String = "", description: String = "") =
        CatalogExerciseDraft(name, ExerciseType.PESO_LIBRE, muscle, description)

    @Test
    fun unBorradorCorrectoNoTieneErrores() {
        assertEquals(emptySet<ExerciseDraftError>(), draft(muscle = "espalda").errors(emptySet()))
    }

    @Test
    fun nombreVacioOSoloEspaciosEsError() {
        assertEquals(setOf(ExerciseDraftError.NameRequired), draft(name = "   ").errors(emptySet()))
    }

    @Test
    fun elNombreTieneUnMaximoDe120() {
        assertEquals(emptySet<ExerciseDraftError>(), draft(name = "a".repeat(120)).errors(emptySet()))
        assertEquals(setOf(ExerciseDraftError.NameTooLong), draft(name = "a".repeat(121)).errors(emptySet()))
    }

    @Test
    fun elGrupoTieneUnMaximoDe60() {
        assertEquals(emptySet<ExerciseDraftError>(), draft(muscle = "a".repeat(60)).errors(emptySet()))
        assertEquals(setOf(ExerciseDraftError.MuscleTooLong), draft(muscle = "a".repeat(61)).errors(emptySet()))
    }

    @Test
    fun laDescripcionTieneUnMaximoDe2000() {
        assertEquals(emptySet<ExerciseDraftError>(), draft(description = "a".repeat(2000)).errors(emptySet()))
        assertEquals(
            setOf(ExerciseDraftError.DescriptionTooLong),
            draft(description = "a".repeat(2001)).errors(emptySet()),
        )
    }

    @Test
    fun unNombreConElMismoSlugQueOtroEjercicioEsDuplicado() {
        val others = setOf(slug("Sentadilla búlgara"))
        assertEquals(
            setOf(ExerciseDraftError.NameExists),
            draft(name = "  SENTADILLA bulgara ").errors(others),
        )
    }

    @Test
    fun editarSinCambiarElNombreNoEsDuplicado() {
        // Quien edita pasa los ids de los demás ejercicios, sin el suyo.
        assertEquals(emptySet<ExerciseDraftError>(), draft(name = "Remo").errors(setOf(slug("Press banca"))))
    }

    @Test
    fun `editing keeps measure, non-strength types save REPS`() {
        val seconds = exercise("core").copy(type = ExerciseType.PESO_CORPORAL, measure = SetMeasure.SECONDS)
        val draft = CatalogExerciseDraft.of(seconds)
        assertEquals(SetMeasure.SECONDS, draft.measure)
        assertEquals(SetMeasure.SECONDS, draft.toInput().measure)
        assertEquals(SetMeasure.REPS, CatalogExerciseDraft.of(null).measure)
        assertEquals(SetMeasure.REPS, draft.copy(type = ExerciseType.CARDIO).toInput().measure)
        assertEquals(SetMeasure.REPS, draft.copy(type = ExerciseType.OTROS).toInput().measure)
    }
}
