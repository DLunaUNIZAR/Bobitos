package com.dlunaunizar.bobitos.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.data.repository.OnboardingPreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(private val onboardingPreferences: OnboardingPreferenceRepository) :
    ViewModel() {
    // null mientras se lee la preferencia; true/false una vez conocida.
    private val source: Flow<Boolean?> = onboardingPreferences.welcomeSeen
    val seen: StateFlow<Boolean?> = source.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun markSeen() {
        viewModelScope.launch { onboardingPreferences.markWelcomeSeen() }
    }
}
