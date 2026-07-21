package com.dlunaunizar.bobitos.data.repository

/** Contadores «de vistazo» por módulo para el hub del espacio. */
data class SpaceModuleCounts(
    val pendingShopping: Int = 0,
    val pendingTasks: Int = 0,
    val upcomingEvents: Int = 0,
    val todayMeals: Int = 0,
)

interface SpaceSummaryRepository {
    /**
     * Resumen del espacio mediante consultas de agregación `count()` (una lectura por módulo,
     * sin listeners persistentes). Lanza si no hay conexión: la pantalla degrada sin badges.
     */
    suspend fun counts(spaceId: String): SpaceModuleCounts
}
