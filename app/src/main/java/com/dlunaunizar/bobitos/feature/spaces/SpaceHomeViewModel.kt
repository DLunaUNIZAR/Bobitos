package com.dlunaunizar.bobitos.feature.spaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.data.repository.CalendarRepository
import com.dlunaunizar.bobitos.data.repository.MealRepository
import com.dlunaunizar.bobitos.data.repository.SpaceModuleCounts
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import com.dlunaunizar.bobitos.data.repository.SpaceSummaryRepository
import com.dlunaunizar.bobitos.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * Carga los contadores «de vistazo» del hub (agregación `count()`, una lectura por módulo) y el
 * resumen personal + reparto ([SpaceHomeDigest]) agregado en cliente a partir de los flujos de cada módulo.
 * Se recarga cada vez que «Hoy» se muestra (para no enseñar contadores de antes de editar en otro módulo).
 * El resumen sigue al día actual (cambia solo a medianoche). Si falla una fuente del resumen se emite un resumen
 * `unavailable` (saludo sin frase de resumen ni reparto, sin afirmar que no hay nada); si falla el conteo se
 * conservan los últimos contadores del mismo espacio; al cambiar de espacio se limpian ambos.
 */
@HiltViewModel
class SpaceHomeViewModel @Inject constructor(
    private val summaryRepository: SpaceSummaryRepository,
    private val taskRepository: TaskRepository,
    private val mealRepository: MealRepository,
    private val calendarRepository: CalendarRepository,
    private val spaceRepository: SpaceRepository,
    private val dayProvider: CurrentDayProvider,
) : ViewModel() {
    private val mutableCounts = MutableStateFlow<SpaceModuleCounts?>(null)
    val counts: StateFlow<SpaceModuleCounts?> = mutableCounts.asStateFlow()
    private val mutableDigest = MutableStateFlow<SpaceHomeDigest?>(null)
    val digest: StateFlow<SpaceHomeDigest?> = mutableDigest.asStateFlow()
    private var loadJob: Job? = null
    private var loadedSpaceId: String? = null

    /**
     * Carga contadores y resumen del espacio. Una carga anterior aún en curso se cancela y lo que se
     * mostraba se borra solo si el espacio cambia: «Hoy» espera a los datos del espacio nuevo en vez de enseñar
     * los del anterior, pero al volver al mismo espacio lo que se ve se queda hasta que llegue lo nuevo.
     * El resumen observa los flujos, así que se corrige solo cuando llega el dato del servidor.
     */
    fun load(spaceId: String, userId: String) {
        loadJob?.cancel()
        if (spaceId != loadedSpaceId) {
            mutableCounts.value = null
            mutableDigest.value = null
        }
        loadedSpaceId = spaceId
        loadJob = viewModelScope.launch {
            launch { mutableCounts.value = loadOrNull { summaryRepository.counts(spaceId) } ?: mutableCounts.value }
            launch { observeDigest(spaceId, userId) }
        }
    }

    // El resumen depende del día actual: al cambiar (medianoche) se vuelve a consultar el día nuevo.
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun observeDigest(spaceId: String, userId: String) {
        val zone = ZoneId.systemDefault()
        dayProvider.today(zone)
            .flatMapLatest { today ->
                val dayStart = today.atStartOfDay(zone).toInstant()
                val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant()
                combine(
                    spaceRepository.members(spaceId),
                    taskRepository.tasks(spaceId),
                    mealRepository.meals(spaceId, today, today.plusDays(1)),
                    calendarRepository.events(spaceId, dayStart, dayEnd),
                ) { members, tasks, meals, events ->
                    buildHomeDigest(userId, today, zone, tasks, meals, events, members)
                }
            }
            // Si una fuente falla, «Hoy» no se queda cargando ni afirma que no hay nada: pasa a «no disponible».
            .catch {
                if (it is CancellationException) throw it
                emit(UNAVAILABLE_DIGEST)
            }
            .collect { mutableDigest.value = it }
    }
}

private val UNAVAILABLE_DIGEST =
    SpaceHomeDigest(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), unavailable = true)

/** Fuente del día actual: emite hoy al empezar y de nuevo en cada medianoche (se sustituye en los tests). */
open class CurrentDayProvider @Inject constructor() {
    open fun today(zone: ZoneId): Flow<LocalDate> = flow {
        while (true) {
            val now = ZonedDateTime.now(zone)
            emit(now.toLocalDate())
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(zone)
            delay(Duration.between(now, nextMidnight).toMillis() + 1_000)
        }
    }.distinctUntilChanged()
}

// Null si la lectura falla (sin conexión); una cancelación se propaga para no borrar lo que ya se muestra.
private suspend fun <T> loadOrNull(block: suspend () -> T): T? =
    runCatching { block() }.onFailure { if (it is CancellationException) throw it }.getOrNull()
