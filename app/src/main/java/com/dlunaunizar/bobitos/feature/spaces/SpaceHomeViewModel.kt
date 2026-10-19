package com.dlunaunizar.bobitos.feature.spaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.CurrentDayProvider
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.launchPerIdWrite
import com.dlunaunizar.bobitos.core.common.rethrowIfCancelled
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
    private val completingIds = mutableSetOf<String>()

    // Para qué petición se empezó a observar por última vez: solo una petición nueva vuelve a `Loading`.
    private var lastObserved: Request? = null
    val error: StateFlow<TaskUiMessage?> = mutableError.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val agenda: StateFlow<ScopedAgenda> = request
        .flatMapLatest { it?.let(::observe) ?: emptyFlow() }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            ScopedAgenda(null, UiState.Loading),
        )

    /** Fija el espacio y el usuario de la agenda. Con los mismos de antes no hace nada. */
    fun show(spaceId: String, userId: String) {
        request.update { if (it?.spaceId == spaceId && it.userId == userId) it else Request(spaceId, userId) }
    }

    /** Vuelve a suscribir las fuentes tras un error. */
    fun retry() = request.update { it?.copy(attempt = it.attempt + 1) }

    /**
     * Completa [task] sin salir de Hoy. Si esa tarea ya tiene una escritura en curso no hace nada (un segundo toque no
     * la completa otra vez). [onFinished] recibe cómo acabó la escritura; [onCompleted], la tarea tal como estaba,
     * solo si termina bien.
     */
    fun complete(task: TaskItem, onFinished: (Boolean) -> Unit = {}, onCompleted: (TaskItem) -> Unit): Boolean {
        val spaceId = request.value?.spaceId ?: return false
        if (task.id in completingIds) return false
        mutableError.value = null
        return viewModelScope.launchPerIdWrite(
            completingIds,
            task.id,
            onFinished = { ok ->
                onFinished(ok)
                if (ok) onCompleted(task)
            },
            // Una cancelación lanzada por el repositorio (sin estar cancelado el scope) llega aquí como fallo normal.
            onFailure = { mutableError.value = it.toUiMessage() },
        ) { taskRepository.setCompleted(spaceId, task.id, true) }
    }

    /** «Deshacer» completar: la tarea vuelve a pendiente con las fechas de [previous]. */
    fun undoCompletion(previous: TaskItem) {
        val spaceId = request.value?.spaceId ?: return
        mutableError.value = null
        viewModelScope.launch {
            runCatching { taskRepository.restoreCompletion(spaceId, previous) }.onFailure { showError(it) }
        }
    }

    fun clearError() {
        mutableError.value = null
    }

    private suspend fun showError(error: Throwable) {
        error.rethrowIfCancelled()
        mutableError.value = error.toUiMessage()
    }

    // El día actual manda: a medianoche se vuelve a consultar el día nuevo.
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observe(request: Request): Flow<ScopedAgenda> {
        val zone = ZoneId.systemDefault()
        val spaceId = request.spaceId
        return dayProvider.today(zone)
            .flatMapLatest { today ->
                val tomorrow = today.plusDays(1)
                val dayStart = today.atStartOfDay(zone).toInstant()
                val dayEnd = tomorrow.atStartOfDay(zone).toInstant()
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
                        spaceId, request.userId, today, zone, tasks, meals, events, activities, shopping, members,
                    )
                }
            }
            .map<TodayAgenda, ScopedAgenda> { ScopedAgenda(spaceId, UiState.Content(it)) }
            // Sin datos del espacio anterior ni de antes de un reintento: se empieza en `Loading`. Volver a recoger la
            // misma petición (Hoy -> Tareas -> Hoy) conserva lo último en vez de pasar por «Cargando».
            .onStart {
                if (lastObserved != request) {
                    lastObserved = request
                    emit(ScopedAgenda(spaceId, UiState.Loading))
                }
            }
            .catch {
                it.rethrowIfCancelled()
                emit(ScopedAgenda(spaceId, UiState.Error(it.toFailureKind())))
            }
    }
}
