package com.dlunaunizar.bobitos.feature.spaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.data.repository.SpaceModuleCounts
import com.dlunaunizar.bobitos.data.repository.SpaceSummaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Carga los contadores «de vistazo» del hub con una agregación `count()` por módulo (una lectura
 * cada uno, sin listeners). Si falla (p. ej. sin conexión) deja los contadores en null y el hub se
 * muestra sin badges.
 */
@HiltViewModel
class SpaceHomeViewModel @Inject constructor(private val summaryRepository: SpaceSummaryRepository) : ViewModel() {
    private val mutableCounts = MutableStateFlow<SpaceModuleCounts?>(null)
    val counts: StateFlow<SpaceModuleCounts?> = mutableCounts.asStateFlow()
    private var loadedSpaceId: String? = null

    fun load(spaceId: String) {
        if (spaceId == loadedSpaceId && mutableCounts.value != null) return
        loadedSpaceId = spaceId
        viewModelScope.launch {
            mutableCounts.value = runCatching { summaryRepository.counts(spaceId) }.getOrNull()
        }
    }
}
