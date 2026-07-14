package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.SpaceSummary
import kotlinx.coroutines.flow.Flow

interface SpaceRepository {
    val spaces: Flow<List<SpaceSummary>>
}

