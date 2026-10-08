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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Carga los contadores «de vistazo» del hub (agregación `count()`, una lectura por módulo) y el
 * resumen personal + reparto ([SpaceHomeDigest]) mediante lecturas puntuales agregadas en cliente.
 * Se recarga cada vez que «Hoy» se muestra (para no enseñar contadores de antes de editar en otro módulo)
 * y degrada a null si falla (sin conexión), dejando el hub sin adornos.
 */
@HiltViewModel
class SpaceHomeViewModel @Inject constructor(
    private val summaryRepository: SpaceSummaryRepository,
    private val taskRepository: TaskRepository,
    private val mealRepository: MealRepository,
    private val calendarRepository: CalendarRepository,
    private val spaceRepository: SpaceRepository,
) : ViewModel() {
    private val mutableCounts = MutableStateFlow<SpaceModuleCounts?>(null)
    val counts: StateFlow<SpaceModuleCounts?> = mutableCounts.asStateFlow()
    private val mutableDigest = MutableStateFlow<SpaceHomeDigest?>(null)
    val digest: StateFlow<SpaceHomeDigest?> = mutableDigest.asStateFlow()
    private var loadJob: Job? = null

    /** Carga contadores y resumen del espacio. Una carga anterior aún en curso se cancela. */
    fun load(spaceId: String, userId: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            launch { mutableCounts.value = loadOrNull { summaryRepository.counts(spaceId) } }
            launch { mutableDigest.value = loadOrNull { loadDigest(spaceId, userId) } }
        }
    }

    private suspend fun loadDigest(spaceId: String, userId: String): SpaceHomeDigest = coroutineScope {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val dayStart = today.atStartOfDay(zone).toInstant()
        val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant()
        // Las cuatro lecturas son independientes: en paralelo.
        val members = async { spaceRepository.members(spaceId).first() }
        val tasks = async { taskRepository.tasks(spaceId).first() }
        val meals = async { mealRepository.meals(spaceId, today, today.plusDays(1)).first() }
        val events = async { calendarRepository.events(spaceId, dayStart, dayEnd).first() }
        buildHomeDigest(userId, today, zone, tasks.await(), meals.await(), events.await(), members.await())
    }
}

// Null si la lectura falla (sin conexión); una cancelación se propaga para no borrar lo que ya se muestra.
private suspend fun <T> loadOrNull(block: suspend () -> T): T? =
    runCatching { block() }.onFailure { if (it is CancellationException) throw it }.getOrNull()
