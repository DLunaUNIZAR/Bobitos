package com.dlunaunizar.bobitos.data.repository

import kotlinx.coroutines.flow.Flow

/** Persiste si el usuario ya vio la pantalla de bienvenida (onboarding de una sola vez). */
interface OnboardingPreferenceRepository {
    val welcomeSeen: Flow<Boolean>

    suspend fun markWelcomeSeen()
}
