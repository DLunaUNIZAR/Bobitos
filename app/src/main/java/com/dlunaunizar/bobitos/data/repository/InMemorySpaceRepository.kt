package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.SpaceSummary
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class InMemorySpaceRepository @Inject constructor() : SpaceRepository {
    override val spaces: Flow<List<SpaceSummary>> = flowOf(
        listOf(
            SpaceSummary(
                id = "demo-home",
                name = "Casa",
                memberCount = 4,
            ),
        ),
    )
}

