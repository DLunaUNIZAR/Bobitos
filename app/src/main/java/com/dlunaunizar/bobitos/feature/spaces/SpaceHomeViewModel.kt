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
 * Todo se cachea por `spaceId` y degrada a null si falla (sin conexión), dejando el hub sin adornos.
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
    private var loadedSpaceId: String? = null

    fun load(spaceId: String, userId: String) {
        if (spaceId == loadedSpaceId && mutableCounts.value != null && mutableDigest.value != null) return
        loadedSpaceId = spaceId
        viewModelScope.launch {
            mutableCounts.value = runCatching { summaryRepository.counts(spaceId) }.getOrNull()
        }
        viewModelScope.launch {
            mutableDigest.value = runCatching { loadDigest(spaceId, userId) }.getOrNull()
        }
    }

    private suspend fun loadDigest(spaceId: String, userId: String): SpaceHomeDigest {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val dayStart = today.atStartOfDay(zone).toInstant()
        val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant()
        val members = spaceRepository.members(spaceId).first()
        val tasks = taskRepository.tasks(spaceId).first()
        val meals = mealRepository.meals(spaceId, today, today.plusDays(1)).first()
        val events = calendarRepository.events(spaceId, dayStart, dayEnd).first()
        return buildHomeDigest(userId, today, zone, tasks, meals, events, members)
    }
}
