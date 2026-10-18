package com.dlunaunizar.bobitos.feature.spaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.CurrentDayProvider
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.toFailureKind
import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.data.repository.CalendarRepository
import com.dlunaunizar.bobitos.data.repository.MealRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingRepository
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import com.dlunaunizar.bobitos.data.repository.SportActivityRepository
import com.dlunaunizar.bobitos.data.repository.TaskRepository
import com.dlunaunizar.bobitos.feature.tasks.TaskUiMessage
import com.dlunaunizar.bobitos.feature.tasks.toUiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * La agenda de «Hoy» ([TodayAgenda]): tareas, eventos, deporte, comidas, compra y miembros del espacio, agregados en
 * cliente con [buildTodayAgenda]. Las seis escuchas solo están abiertas mientras alguien recoge [agenda] (la pantalla
 * a la vista); al cambiar de espacio empieza en `Loading`, sin datos del anterior. Sigue al día actual (cambia solo a
 * medianoche). Si una fuente falla, [agenda] pasa a `Error` hasta [retry].
 */
@HiltViewModel
class SpaceHomeViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val mealRepository: MealRepository,
    private val calendarRepository: CalendarRepository,
    private val sportRepository: SportActivityRepository,
    private val shoppingRepository: ShoppingRepository,
    private val spaceRepository: SpaceRepository,
    private val dayProvider: CurrentDayProvider,
) : ViewModel() {
    // Qué agenda se pide; `attempt` sube con cada «Reintentar» para volver a suscribir.
    private data class Request(val spaceId: String, val userId: String, val attempt: Int = 0)

    private val request = MutableStateFlow<Request?>(null)
    private val mutableError = MutableStateFlow<TaskUiMessage?>(null)
    val error: StateFlow<TaskUiMessage?> = mutableError.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val agenda: StateFlow<UiState<TodayAgenda>> = request
        .flatMapLatest { it?.let(::observe) ?: emptyFlow() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    /** Fija el espacio y el usuario de la agenda. Con los mismos de antes no hace nada. */
    fun show(spaceId: String, userId: String) {
        request.update { if (it?.spaceId == spaceId && it.userId == userId) it else Request(spaceId, userId) }
    }

    /** Vuelve a suscribir las fuentes tras un error. */
    fun retry() = request.update { it?.copy(attempt = it.attempt + 1) }

    /** Completa [task] sin salir de Hoy; [onCompleted] recibe la tarea tal como estaba solo si termina bien. */
    fun complete(task: TaskItem, onCompleted: (TaskItem) -> Unit) {
        val spaceId = request.value?.spaceId ?: return
        mutableError.value = null
        viewModelScope.launch {
            runCatching { taskRepository.setCompleted(spaceId, task.id, true) }
                .onSuccess { onCompleted(task) }
                .onFailure(::showError)
        }
    }

    /** «Deshacer» completar: la tarea vuelve a pendiente con las fechas de [previous]. */
    fun undoCompletion(previous: TaskItem) {
        val spaceId = request.value?.spaceId ?: return
        mutableError.value = null
        viewModelScope.launch {
            runCatching { taskRepository.restoreCompletion(spaceId, previous) }.onFailure(::showError)
        }
    }

    fun clearError() {
        mutableError.value = null
    }

    private fun showError(error: Throwable) {
        if (error is CancellationException) throw error
        mutableError.value = error.toUiMessage()
    }

    // El día actual manda: a medianoche se vuelve a consultar el día nuevo.
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observe(request: Request): Flow<UiState<TodayAgenda>> {
        val zone = ZoneId.systemDefault()
        val spaceId = request.spaceId
        return dayProvider.today(zone)
            .flatMapLatest { today ->
                val dayStart = today.atStartOfDay(zone).toInstant()
                val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant()
                val tomorrow = today.plusDays(1)
                combine(
                    combine(
                        taskRepository.tasks(spaceId),
                        mealRepository.meals(spaceId, today, tomorrow),
                        calendarRepository.events(spaceId, dayStart, dayEnd),
                    ) { tasks, meals, events -> Triple(tasks, meals, events) },
                    combine(
                        sportRepository.activities(spaceId, today, tomorrow),
                        shoppingRepository.items(spaceId),
                        spaceRepository.members(spaceId),
                    ) { activities, shopping, members -> Triple(activities, shopping, members) },
                ) { (tasks, meals, events), (activities, shopping, members) ->
                    buildTodayAgenda(
                        request.userId, today, zone, tasks, meals, events, activities, shopping, members,
                    )
                }
            }
            .map<TodayAgenda, UiState<TodayAgenda>> { UiState.Content(it) }
            // Sin datos del espacio anterior ni de antes de un reintento: se empieza en `Loading`.
            .onStart { emit(UiState.Loading) }
            .catch {
                if (it is CancellationException) throw it
                emit(UiState.Error(it.toFailureKind()))
            }
    }
}
