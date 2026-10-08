package com.dlunaunizar.bobitos.feature.sport

import android.os.Parcelable
import com.dlunaunizar.bobitos.core.model.SportActivity
import com.dlunaunizar.bobitos.core.model.SportType
import kotlinx.parcelize.Parcelize

// Campos simples del editor de actividad, Parcelable para que sobrevivan a una rotación. La sesión de
// gimnasio se guarda aparte (ExerciseDraftListSaver).
@Parcelize
internal data class ActivityDraft(
    val type: SportType,
    val name: String,
    val selectedIds: List<String>,
    val routineId: String?,
) : Parcelable {
    fun withParticipant(userId: String, selected: Boolean) = copy(
        selectedIds = if (selected) (selectedIds + userId).distinct().sorted() else selectedIds - userId,
    )

    companion object {
        fun of(activity: SportActivity?) = ActivityDraft(
            type = activity?.type ?: SportType.PADEL,
            name = activity?.name.orEmpty(),
            selectedIds = activity?.participantIds.orEmpty().distinct().sorted(),
            routineId = activity?.routineId,
        )
    }
}
