package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.SpaceRole
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Membresía activa del usuario en un espacio. */
internal data class MembershipSnapshot(val spaceId: String, val role: SpaceRole)

/**
 * El espacio [spaceId], leído con [observeSpace], mientras las membresías activas del usuario ([memberships]) incluyan la
 * suya; null mientras no la incluyan. Si la membresía vuelve, el espacio también.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun activeSpaceOf(
    memberships: Flow<List<MembershipSnapshot>>,
    spaceId: String,
    observeSpace: (MembershipSnapshot) -> Flow<SpaceSummary?>,
): Flow<SpaceSummary?> = memberships
    .map { list -> list.firstOrNull { it.spaceId == spaceId } }
    .distinctUntilChanged()
    .flatMapLatest { membership -> if (membership == null) flowOf(null) else observeSpace(membership) }
