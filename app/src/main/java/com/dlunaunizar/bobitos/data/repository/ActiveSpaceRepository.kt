package com.dlunaunizar.bobitos.data.repository

import kotlinx.coroutines.flow.Flow

interface ActiveSpaceRepository {
    fun activeSpaceId(userId: String): Flow<String?>

    suspend fun setActiveSpace(userId: String, spaceId: String?)
}
