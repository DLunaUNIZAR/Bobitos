package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.SpaceInvitation
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import kotlinx.coroutines.flow.Flow

interface SpaceRepository {
    fun spaces(): Flow<List<SpaceSummary>>

    fun space(spaceId: String): Flow<SpaceSummary?>

    fun members(spaceId: String): Flow<List<SpaceMember>>

    fun invitations(spaceId: String): Flow<List<SpaceInvitation>>

    suspend fun createSpace(name: String): String

    suspend fun renameSpace(spaceId: String, name: String)

    suspend fun leaveSpace(spaceId: String)

    suspend fun removeMember(spaceId: String, userId: String)

    suspend fun transferOwnership(spaceId: String, newOwnerId: String)

    suspend fun deleteSpace(spaceId: String)

    suspend fun createInvitation(spaceId: String): SpaceInvitation

    suspend fun revokeInvitation(invitationId: String)

    suspend fun acceptInvitation(code: String): String
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
    OnlyOwnerCanDelete,
    InvalidInvitationCode,
    InvitationNotFound,
    InvitationAlreadyUsed,
    InvitationRevoked,
    InvitationExpired,
    SpaceFull,
    PermissionDenied,
    Network,
    Unknown,
}

class SpaceRepositoryException(val failure: SpaceFailure, cause: Throwable? = null) : Exception(cause)
