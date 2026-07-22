package com.dlunaunizar.bobitos.feature.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import com.dlunaunizar.bobitos.data.reminders.ReminderScheduler
import com.dlunaunizar.bobitos.data.repository.ReminderPreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val scheduler: ReminderScheduler,
    private val preferences: ReminderPreferenceRepository,
) : ViewModel() {
    val enabled: StateFlow<Boolean> =
        preferences.enabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    fun setEnabled(value: Boolean) {
        viewModelScope.launch {
            preferences.setEnabled(value)
            if (!value) scheduler.cancelAll()
        }
    }

    /** Reprograma (o cancela) al entrar en la app, según la preferencia. */
    fun sync(userId: String, spaces: List<SpaceSummary>, isEnabled: Boolean) {
        viewModelScope.launch {
            if (isEnabled) scheduler.reschedule(userId, spaces) else scheduler.cancelAll()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
    }
}
