package com.dlunaunizar.bobitos.feature.sport

import androidx.compose.runtime.saveable.listSaver
import com.dlunaunizar.bobitos.core.model.SportActivity
import com.dlunaunizar.bobitos.core.model.SportType

// Campos simples del editor de actividad, para que sobrevivan a una rotación (`typeName` es el `name` del
// enum). La sesión de gimnasio se guarda aparte (ExerciseDraftListSaver).
internal data class ActivityDraft(
    val typeName: String,
    val name: String,
    val selectedIds: List<String>,
    val routineId: String?,
) {
    val type: SportType get() = SportType.valueOf(typeName)

    fun withParticipant(userId: String, selected: Boolean) = copy(
        selectedIds = if (selected) (selectedIds + userId).distinct().sorted() else selectedIds - userId,
    )

    companion object {
        fun of(activity: SportActivity?) = ActivityDraft(
            typeName = (activity?.type ?: SportType.PADEL).name,
            name = activity?.name.orEmpty(),
            selectedIds = activity?.participantIds.orEmpty().distinct().sorted(),
            routineId = activity?.routineId,
        )
    }
}

internal val ActivityDraftSaver = listSaver<ActivityDraft, Any?>(
    save = { listOf(it.typeName, it.name, ArrayList(it.selectedIds), it.routineId) },
    restore = {
        @Suppress("UNCHECKED_CAST")
        ActivityDraft(
            typeName = it[0] as String,
            name = it[1] as String,
            selectedIds = (it[2] as List<String>).toList(),
            routineId = it[3] as String?,
        )
    },
)
