package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import kotlinx.coroutines.flow.Flow

interface SpaceRepository {
    val spaces: Flow<List<SpaceSummary>>

    fun members(spaceId: String): Flow<List<SpaceMember>>

    suspend fun createSpace(name: String): String

    suspend fun renameSpace(spaceId: String, name: String)

    suspend fun leaveSpace(spaceId: String)

    suspend fun removeMember(spaceId: String, userId: String)

    suspend fun transferOwnership(spaceId: String, newOwnerId: String)
}

enum class SpaceFailure {
    NameRequired,
    NameTooLong,
    NotAuthenticated,
    EmailNotVerified,
    SpaceNotFound,
    MembershipNotFound,
    OwnerMustTransfer,
    CannotRemoveOwner,
    InvalidNewOwner,
    PermissionDenied,
    Network,
    Unknown,
}

class SpaceRepositoryException(
    val failure: SpaceFailure,
    cause: Throwable? = null,
) : Exception(cause)
