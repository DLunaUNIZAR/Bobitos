package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.InvitationCode
import com.dlunaunizar.bobitos.core.model.InvitationStatus
import com.dlunaunizar.bobitos.core.model.SpaceInvitation
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.SpaceRole
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import com.dlunaunizar.bobitos.data.sync.RealtimeMetrics
import com.dlunaunizar.bobitos.data.sync.SyncRepository
import com.dlunaunizar.bobitos.data.sync.WriteNotAllowedException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import javax.inject.Inject
import javax.inject.Singleton
import java.time.Duration
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirestoreSpaceRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val realtimeMetrics: RealtimeMetrics,
) : SpaceRepository {
    private val firestore = FirebaseFirestore.getInstance()

    override fun spaces(): Flow<List<SpaceSummary>> =
        authRepository.currentUser.flatMapLatest { user ->
            if (user == null || !user.isEmailVerified) {
                flowOf(emptyList())
            } else {
                membershipsForUser(user.id).flatMapLatest { memberships ->
                    val spaceFlows = memberships.map { membership ->
                        observeSpace(membership, metricScope = "space:list")
                    }
                    if (spaceFlows.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        combine(spaceFlows) { spaces ->
                            spaces
                                .filterNotNull()
                                .sortedBy { it.name.lowercase() }
                        }
                    }
                }
            }
        }

    override fun space(spaceId: String): Flow<SpaceSummary?> =
        authRepository.currentUser.flatMapLatest { user ->
            if (user == null || !user.isEmailVerified) {
                flowOf(null)
            } else {
                observeMembership(spaceId, user.id).flatMapLatest { membership ->
                    if (membership == null) {
                        flowOf(null)
                    } else {
                        observeSpace(membership, metricScope = "space:active")
                    }
                }
            }
        }

    override fun members(spaceId: String): Flow<List<SpaceMember>> = callbackFlow {
        val metricId = realtimeMetrics.listenerStarted("members:settings")
        val registration = firestore.collection(MEMBERSHIPS)
            .whereEqualTo(FIELD_SPACE_ID, spaceId)
            .whereEqualTo(FIELD_STATUS, STATUS_ACTIVE)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> close(error.toSpaceRepositoryException())
                    snapshot != null -> {
                        realtimeMetrics.snapshotReceived(
                            scope = "members:settings",
                            changedDocuments = snapshot.documentChanges.size,
                            fromCache = snapshot.metadata.isFromCache,
                        )
                        val members = snapshot.documents
                            .mapNotNull(DocumentSnapshot::toSpaceMember)
                            .sortedWith(
                                compareBy<SpaceMember> { it.role != SpaceRole.OWNER }
                                    .thenBy { it.displayName.lowercase() },
                            )
                        trySend(members)
                    }
                }
            }
        awaitClose {
            registration.remove()
            realtimeMetrics.listenerStopped(metricId)
        }
    }

    override fun invitations(spaceId: String): Flow<List<SpaceInvitation>> = callbackFlow {
        val metricId = realtimeMetrics.listenerStarted("invitations:settings")
        val registration = firestore.collection(INVITATIONS)
            .whereEqualTo(FIELD_SPACE_ID, spaceId)
            .whereEqualTo(FIELD_STATUS, INVITATION_STATUS_ACTIVE)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> close(error.toSpaceRepositoryException())
                    snapshot != null -> {
                        realtimeMetrics.snapshotReceived(
                            scope = "invitations:settings",
                            changedDocuments = snapshot.documentChanges.size,
                            fromCache = snapshot.metadata.isFromCache,
                        )
                        trySend(
                            snapshot.documents
                                .mapNotNull(DocumentSnapshot::toSpaceInvitation)
                                .filter { invitation ->
                                    invitation.status == InvitationStatus.ACTIVE
                                }
                                .sortedByDescending(SpaceInvitation::expiresAt),
                        )
                    }
                }
            }
        awaitClose {
            registration.remove()
            realtimeMetrics.listenerStopped(metricId)
        }
    }

    override suspend fun createSpace(name: String): String = runSpaceOperation {
        val normalizedName = validateName(name)
        val user = requireVerifiedUser()
        val spaceReference = firestore.collection(SPACES).document()
        val membershipReference = membershipReference(spaceReference.id, user.id)

        firestore.runTransaction { transaction ->
            transaction.set(
                spaceReference,
                mapOf(
                    FIELD_NAME to normalizedName,
                    FIELD_OWNER_ID to user.id,
                    FIELD_CREATED_BY to user.id,
                    FIELD_MEMBER_COUNT to 1,
                    FIELD_LAST_MEMBERSHIP_CHANGE_USER_ID to user.id,
                    FIELD_CREATED_AT to FieldValue.serverTimestamp(),
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                ),
            )
            transaction.set(
                membershipReference,
                mapOf(
                    FIELD_SPACE_ID to spaceReference.id,
                    FIELD_USER_ID to user.id,
                    FIELD_DISPLAY_NAME to user.membershipDisplayName,
                    FIELD_ROLE to ROLE_OWNER,
                    FIELD_STATUS to STATUS_ACTIVE,
                    FIELD_JOINED_AT to FieldValue.serverTimestamp(),
                ),
            )
        }.await()

        spaceReference.id
    }

    override suspend fun renameSpace(spaceId: String, name: String) = runSpaceOperation {
        requireVerifiedUser()
        val normalizedName = validateName(name)
        val spaceReference = firestore.collection(SPACES).document(spaceId)

        firestore.runTransaction { transaction ->
            checkSpaceExists(transaction.get(spaceReference))
            transaction.update(
                spaceReference,
                FIELD_NAME,
                normalizedName,
                FIELD_UPDATED_AT,
                FieldValue.serverTimestamp(),
            )
        }.await()
        Unit
    }

    override suspend fun leaveSpace(spaceId: String) = runSpaceOperation {
        val user = requireVerifiedUser()
        removeMembership(
            spaceId = spaceId,
            targetUserId = user.id,
            currentUserId = user.id,
            leavingVoluntarily = true,
        )
    }

    override suspend fun removeMember(spaceId: String, userId: String) = runSpaceOperation {
        val currentUser = requireVerifiedUser()
        removeMembership(
            spaceId = spaceId,
            targetUserId = userId,
            currentUserId = currentUser.id,
            leavingVoluntarily = false,
        )
    }

    override suspend fun deleteSpace(spaceId: String) = runSpaceOperation {
        val user = requireVerifiedUser()
        val spaceReference = firestore.collection(SPACES).document(spaceId)
        val space = spaceReference.get(Source.SERVER).await()
        checkSpaceExists(space)
        if (space.getString(FIELD_OWNER_ID) != user.id) {
            throw SpaceRepositoryException(SpaceFailure.OnlyOwnerCanDelete)
        }

        // Firestore no elimina subcolecciones al borrar el documento padre. Se vacían
        // explícitamente en lotes acotados y se conserva la membresía del propietario
        // hasta la operación final para que las reglas sigan autorizando el proceso.
        listOf(SHOPPING_ITEMS, TASKS, EVENTS).forEach { childCollection ->
            deleteDocumentsInChunks(spaceReference.collection(childCollection).get(Source.SERVER).await().documents)
        }
        val invitationDocuments = firestore.collection(INVITATIONS)
            .whereEqualTo(FIELD_SPACE_ID, spaceId).get(Source.SERVER).await().documents
        deleteDocumentsInChunks(invitationDocuments)

        val memberships = firestore.collection(MEMBERSHIPS)
            .whereEqualTo(FIELD_SPACE_ID, spaceId).get(Source.SERVER).await().documents
        val finalBatch = firestore.batch()
        memberships.forEach { finalBatch.delete(it.reference) }
        finalBatch.delete(spaceReference)
        finalBatch.commit().await()
        Unit
    }

    private suspend fun deleteDocumentsInChunks(documents: List<DocumentSnapshot>) {
        documents.chunked(MAX_BATCH_DELETES).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    override suspend fun transferOwnership(
        spaceId: String,
        newOwnerId: String,
    ) = runSpaceOperation {
        val currentUser = requireVerifiedUser()
        if (newOwnerId == currentUser.id) {
            throw SpaceRepositoryException(SpaceFailure.InvalidNewOwner)
        }

        val spaceReference = firestore.collection(SPACES).document(spaceId)
        val currentOwnerReference = membershipReference(spaceId, currentUser.id)
        val newOwnerReference = membershipReference(spaceId, newOwnerId)

        firestore.runTransaction { transaction ->
            val space = transaction.get(spaceReference)
            val currentOwner = transaction.get(currentOwnerReference)
            val newOwner = transaction.get(newOwnerReference)
            checkSpaceExists(space)

            if (
                space.getString(FIELD_OWNER_ID) != currentUser.id ||
                currentOwner.membershipRole != SpaceRole.OWNER
            ) {
                throw SpaceRepositoryException(SpaceFailure.PermissionDenied)
            }
            if (
                !newOwner.exists() ||
                newOwner.getString(FIELD_STATUS) != STATUS_ACTIVE ||
                newOwner.membershipRole != SpaceRole.MEMBER
            ) {
                throw SpaceRepositoryException(SpaceFailure.InvalidNewOwner)
            }

            transaction.update(
                spaceReference,
                FIELD_OWNER_ID,
                newOwnerId,
                FIELD_UPDATED_AT,
                FieldValue.serverTimestamp(),
            )
            transaction.update(currentOwnerReference, FIELD_ROLE, ROLE_MEMBER)
            transaction.update(newOwnerReference, FIELD_ROLE, ROLE_OWNER)
        }.await()
        Unit
    }

    override suspend fun createInvitation(spaceId: String): SpaceInvitation =
        runSpaceOperation {
            val user = requireVerifiedUser()
            val invitationId = InvitationCode.generate()
            val invitationReference = firestore.collection(INVITATIONS).document(invitationId)
            val expiresAt = Instant.now().plus(INVITATION_VALIDITY)

            val spaceReference = firestore.collection(SPACES).document(spaceId)
            firestore.runTransaction { transaction ->
                checkSpaceExists(transaction.get(spaceReference))
                transaction.set(
                    invitationReference,
                    mapOf(
                        FIELD_SPACE_ID to spaceId,
                        FIELD_CREATED_BY to user.id,
                        FIELD_CREATED_AT to FieldValue.serverTimestamp(),
                        FIELD_EXPIRES_AT to Timestamp(Date.from(expiresAt)),
                        FIELD_STATUS to INVITATION_STATUS_ACTIVE,
                        FIELD_USED_BY to null,
                        FIELD_USED_AT to null,
                        FIELD_REVOKED_AT to null,
                    ),
                )
            }.await()

            SpaceInvitation(
                id = invitationId,
                spaceId = spaceId,
                expiresAt = expiresAt,
                status = InvitationStatus.ACTIVE,
            )
        }

    override suspend fun revokeInvitation(invitationId: String) = runSpaceOperation {
        requireVerifiedUser()
        val invitationReference = firestore.collection(INVITATIONS).document(invitationId)

        firestore.runTransaction { transaction ->
            val invitation = transaction.get(invitationReference)
            checkInvitationAvailable(invitation, allowExpired = true)
            transaction.update(
                invitationReference,
                FIELD_STATUS,
                INVITATION_STATUS_REVOKED,
                FIELD_REVOKED_AT,
                FieldValue.serverTimestamp(),
            )
        }.await()
        Unit
    }

    override suspend fun acceptInvitation(code: String): String = runSpaceOperation {
        val user = requireVerifiedUser()
        val invitationId = InvitationCode.normalize(code)
            ?: throw SpaceRepositoryException(SpaceFailure.InvalidInvitationCode)
        val invitationReference = firestore.collection(INVITATIONS).document(invitationId)
        val initialInvitation = invitationReference.get(Source.SERVER).await()
        if (!initialInvitation.exists()) {
            throw SpaceRepositoryException(SpaceFailure.InvitationNotFound)
        }
        val initialSpaceId = initialInvitation.getString(FIELD_SPACE_ID)
            ?: throw SpaceRepositoryException(SpaceFailure.InvitationNotFound)
        checkInvitationAvailable(initialInvitation)
        val alreadyMember = firestore.collection(MEMBERSHIPS)
            .whereEqualTo(FIELD_USER_ID, user.id)
            .whereEqualTo(FIELD_STATUS, STATUS_ACTIVE)
            .get(Source.SERVER)
            .await()
            .documents
            .any { membership -> membership.getString(FIELD_SPACE_ID) == initialSpaceId }
        if (alreadyMember) return@runSpaceOperation initialSpaceId

        firestore.runTransaction { transaction ->
            val invitation = transaction.get(invitationReference)
            if (!invitation.exists()) {
                throw SpaceRepositoryException(SpaceFailure.InvitationNotFound)
            }
            val spaceId = invitation.getString(FIELD_SPACE_ID)
                ?: throw SpaceRepositoryException(SpaceFailure.InvitationNotFound)
            val membershipReference = membershipReference(spaceId, user.id)

            checkInvitationAvailable(invitation)
            val spaceReference = firestore.collection(SPACES).document(spaceId)

            transaction.update(
                invitationReference,
                FIELD_STATUS,
                INVITATION_STATUS_USED,
                FIELD_USED_BY,
                user.id,
                FIELD_USED_AT,
                FieldValue.serverTimestamp(),
            )
            transaction.set(
                membershipReference,
                mapOf(
                    FIELD_SPACE_ID to spaceId,
                    FIELD_USER_ID to user.id,
                    FIELD_DISPLAY_NAME to user.membershipDisplayName,
                    FIELD_ROLE to ROLE_MEMBER,
                    FIELD_STATUS to STATUS_ACTIVE,
                    FIELD_JOINED_AT to FieldValue.serverTimestamp(),
                    FIELD_JOINED_VIA_INVITATION_ID to invitationId,
                ),
            )
            transaction.update(
                spaceReference,
                FIELD_MEMBER_COUNT,
                FieldValue.increment(1),
                FIELD_LAST_MEMBERSHIP_CHANGE_USER_ID,
                user.id,
                FIELD_UPDATED_AT,
                FieldValue.serverTimestamp(),
            )
            spaceId
        }.await()
    }

    private fun membershipsForUser(userId: String): Flow<List<MembershipSnapshot>> = callbackFlow {
        val metricId = realtimeMetrics.listenerStarted("memberships:list")
        val registration = firestore.collection(MEMBERSHIPS)
            .whereEqualTo(FIELD_USER_ID, userId)
            .whereEqualTo(FIELD_STATUS, STATUS_ACTIVE)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> close(error.toSpaceRepositoryException())
                    snapshot != null -> {
                        realtimeMetrics.snapshotReceived(
                            scope = "memberships:list",
                            changedDocuments = snapshot.documentChanges.size,
                            fromCache = snapshot.metadata.isFromCache,
                        )
                        trySend(
                            snapshot.documents.mapNotNull { document ->
                                val spaceId = document.getString(FIELD_SPACE_ID)
                                val role = document.membershipRole
                                if (spaceId == null || role == null) {
                                    null
                                } else {
                                    MembershipSnapshot(spaceId, role)
                                }
                            },
                        )
                    }
                }
            }
        awaitClose {
            registration.remove()
            realtimeMetrics.listenerStopped(metricId)
        }
    }

    private fun observeMembership(
        spaceId: String,
        userId: String,
    ): Flow<MembershipSnapshot?> = callbackFlow {
        val metricId = realtimeMetrics.listenerStarted("membership:active")
        val registration = membershipReference(spaceId, userId)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> close(error.toSpaceRepositoryException())
                    snapshot == null ||
                        !snapshot.exists() ||
                        snapshot.getString(FIELD_STATUS) != STATUS_ACTIVE -> trySend(null)
                    else -> {
                        realtimeMetrics.snapshotReceived(
                            scope = "membership:active",
                            changedDocuments = 1,
                            fromCache = snapshot.metadata.isFromCache,
                        )
                        val role = snapshot.membershipRole
                        if (role == null) {
                            close(SpaceRepositoryException(SpaceFailure.Unknown))
                        } else {
                            trySend(MembershipSnapshot(spaceId, role))
                        }
                    }
                }
            }
        awaitClose {
            registration.remove()
            realtimeMetrics.listenerStopped(metricId)
        }
    }

    private fun observeSpace(
        membership: MembershipSnapshot,
        metricScope: String,
    ): Flow<SpaceSummary?> = callbackFlow {
        val metricId = realtimeMetrics.listenerStarted(metricScope)
        val registration = firestore.collection(SPACES)
            .document(membership.spaceId)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> close(error.toSpaceRepositoryException())
                    snapshot == null || !snapshot.exists() -> trySend(null)
                    else -> {
                        realtimeMetrics.snapshotReceived(
                            scope = metricScope,
                            changedDocuments = 1,
                            fromCache = snapshot.metadata.isFromCache,
                        )
                        val name = snapshot.getString(FIELD_NAME)
                        val memberCount = snapshot.getLong(FIELD_MEMBER_COUNT)?.toInt()
                        if (name == null || memberCount == null) {
                            close(SpaceRepositoryException(SpaceFailure.Unknown))
                        } else {
                            trySend(
                                SpaceSummary(
                                    id = snapshot.id,
                                    name = name,
                                    memberCount = memberCount,
                                    role = membership.role,
                                ),
                            )
                        }
                    }
                }
            }
        awaitClose {
            registration.remove()
            realtimeMetrics.listenerStopped(metricId)
        }
    }

    private suspend fun removeMembership(
        spaceId: String,
        targetUserId: String,
        currentUserId: String,
        leavingVoluntarily: Boolean,
    ) {
        val spaceReference = firestore.collection(SPACES).document(spaceId)
        val membershipReference = membershipReference(spaceId, targetUserId)
        val taskDocuments = firestore.collection(SPACES)
            .document(spaceId)
            .collection(TASKS)
            .whereEqualTo(FIELD_STATUS, TASK_STATUS_TODO)
            .whereEqualTo(FIELD_ASSIGNEE_ID, targetUserId)
            .get(Source.SERVER)
            .await()
            .documents

        firestore.runTransaction { transaction ->
            val space = transaction.get(spaceReference)
            val membership = transaction.get(membershipReference)
            val tasks = taskDocuments.map { task -> transaction.get(task.reference) }
            checkSpaceExists(space)
            if (!membership.exists() || membership.getString(FIELD_STATUS) != STATUS_ACTIVE) {
                throw SpaceRepositoryException(SpaceFailure.MembershipNotFound)
            }
            if (
                membership.membershipRole == SpaceRole.OWNER ||
                space.getString(FIELD_OWNER_ID) == targetUserId
            ) {
                throw SpaceRepositoryException(
                    if (leavingVoluntarily) {
                        SpaceFailure.OwnerMustTransfer
                    } else {
                        SpaceFailure.CannotRemoveOwner
                    },
                )
            }
            if (!leavingVoluntarily && space.getString(FIELD_OWNER_ID) != currentUserId) {
                throw SpaceRepositoryException(SpaceFailure.PermissionDenied)
            }

            val currentMemberCount = space.getLong(FIELD_MEMBER_COUNT)?.toInt()
                ?: throw SpaceRepositoryException(SpaceFailure.Unknown)
            if (currentMemberCount <= 1) {
                throw SpaceRepositoryException(SpaceFailure.Unknown)
            }

            tasks
                .filter { task ->
                    task.exists() &&
                        task.getString(FIELD_STATUS) == TASK_STATUS_TODO &&
                        task.getString(FIELD_ASSIGNEE_ID) == targetUserId
                }
                .forEach { task ->
                    transaction.update(
                        task.reference,
                        FIELD_ASSIGNEE_ID,
                        null,
                        FIELD_ASSIGNEE_NAME,
                        null,
                        FIELD_UPDATED_BY,
                        currentUserId,
                        FIELD_UPDATED_AT,
                        FieldValue.serverTimestamp(),
                    )
                }
            transaction.update(
                spaceReference,
                FIELD_MEMBER_COUNT,
                currentMemberCount - 1,
                FIELD_LAST_MEMBERSHIP_CHANGE_USER_ID,
                targetUserId,
                FIELD_UPDATED_AT,
                FieldValue.serverTimestamp(),
            )
            transaction.delete(membershipReference)
        }.await()
    }

    private fun membershipReference(spaceId: String, userId: String) =
        firestore.collection(MEMBERSHIPS).document("${spaceId}_$userId")

    private fun requireVerifiedUser(): AuthUser {
        val user = authRepository.currentUser.value
            ?: throw SpaceRepositoryException(SpaceFailure.NotAuthenticated)
        if (!user.isEmailVerified) {
            throw SpaceRepositoryException(SpaceFailure.EmailNotVerified)
        }
        return user
    }

    private fun validateName(name: String): String {
        val normalizedName = name.trim()
        when {
            normalizedName.isEmpty() -> throw SpaceRepositoryException(SpaceFailure.NameRequired)
            normalizedName.length > MAX_SPACE_NAME_LENGTH -> {
                throw SpaceRepositoryException(SpaceFailure.NameTooLong)
            }
        }
        return normalizedName
    }

    private fun checkSpaceExists(snapshot: DocumentSnapshot) {
        if (!snapshot.exists()) {
            throw SpaceRepositoryException(SpaceFailure.SpaceNotFound)
        }
    }

    private fun checkInvitationAvailable(
        snapshot: DocumentSnapshot,
        allowExpired: Boolean = false,
    ) {
        if (!snapshot.exists()) {
            throw SpaceRepositoryException(SpaceFailure.InvitationNotFound)
        }
        when (snapshot.getString(FIELD_STATUS)) {
            INVITATION_STATUS_USED -> {
                throw SpaceRepositoryException(SpaceFailure.InvitationAlreadyUsed)
            }
            INVITATION_STATUS_REVOKED -> {
                throw SpaceRepositoryException(SpaceFailure.InvitationRevoked)
            }
            INVITATION_STATUS_ACTIVE -> Unit
            else -> throw SpaceRepositoryException(SpaceFailure.InvitationNotFound)
        }
        val expiresAt = snapshot.getTimestamp(FIELD_EXPIRES_AT)?.toDate()?.toInstant()
            ?: throw SpaceRepositoryException(SpaceFailure.InvitationNotFound)
        if (!allowExpired && !expiresAt.isAfter(Instant.now())) {
            throw SpaceRepositoryException(SpaceFailure.InvitationExpired)
        }
    }

    private suspend inline fun <T> runSpaceOperation(
        crossinline operation: suspend () -> T,
    ): T {
        try {
            syncRepository.requireWritable()
            return operation()
        } catch (error: SpaceRepositoryException) {
            if (error.failure == SpaceFailure.Network) {
                syncRepository.reportWriteFailure(error.cause ?: error)
            }
            throw error
        } catch (error: WriteNotAllowedException) {
            throw SpaceRepositoryException(SpaceFailure.Network, error)
        } catch (error: Throwable) {
            syncRepository.reportWriteFailure(error)
            throw error.toSpaceRepositoryException()
        }
    }

    private data class MembershipSnapshot(
        val spaceId: String,
        val role: SpaceRole,
    )

    private companion object {
        const val MAX_SPACE_NAME_LENGTH = 60
        const val SPACES = "spaces"
        const val MEMBERSHIPS = "memberships"
        const val INVITATIONS = "invitations"
        const val TASKS = "tasks"
        const val SHOPPING_ITEMS = "shoppingItems"
        const val EVENTS = "events"
        const val MAX_BATCH_DELETES = 400
        const val FIELD_NAME = "name"
        const val FIELD_OWNER_ID = "ownerId"
        const val FIELD_CREATED_BY = "createdBy"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val FIELD_MEMBER_COUNT = "memberCount"
        const val FIELD_LAST_MEMBERSHIP_CHANGE_USER_ID = "lastMembershipChangeUserId"
        const val FIELD_SPACE_ID = "spaceId"
        const val FIELD_USER_ID = "userId"
        const val FIELD_DISPLAY_NAME = "displayName"
        const val FIELD_ROLE = "role"
        const val FIELD_STATUS = "status"
        const val FIELD_JOINED_AT = "joinedAt"
        const val FIELD_JOINED_VIA_INVITATION_ID = "joinedViaInvitationId"
        const val FIELD_EXPIRES_AT = "expiresAt"
        const val FIELD_USED_BY = "usedBy"
        const val FIELD_USED_AT = "usedAt"
        const val FIELD_REVOKED_AT = "revokedAt"
        const val FIELD_ASSIGNEE_ID = "assigneeId"
        const val FIELD_ASSIGNEE_NAME = "assigneeName"
        const val FIELD_UPDATED_BY = "updatedBy"
        const val STATUS_ACTIVE = "ACTIVE"
        const val ROLE_OWNER = "OWNER"
        const val ROLE_MEMBER = "MEMBER"
        const val TASK_STATUS_TODO = "TODO"
        const val INVITATION_STATUS_ACTIVE = "ACTIVE"
        const val INVITATION_STATUS_USED = "USED"
        const val INVITATION_STATUS_REVOKED = "REVOKED"
        val INVITATION_VALIDITY: Duration = Duration.ofHours(72)
    }
}

private val AuthUser.membershipDisplayName: String
    get() = displayName.ifBlank { email.substringBefore('@') }.take(60)

private val DocumentSnapshot.membershipRole: SpaceRole?
    get() = when (getString("role")) {
        "OWNER" -> SpaceRole.OWNER
        "MEMBER" -> SpaceRole.MEMBER
        else -> null
    }

private fun DocumentSnapshot.toSpaceMember(): SpaceMember? {
    val userId = getString("userId") ?: return null
    val displayName = getString("displayName") ?: return null
    val role = membershipRole ?: return null
    return SpaceMember(userId, displayName, role)
}

private fun DocumentSnapshot.toSpaceInvitation(): SpaceInvitation? {
    val spaceId = getString("spaceId") ?: return null
    val expiresAt = getTimestamp("expiresAt")?.toDate()?.toInstant() ?: return null
    val status = when (getString("status")) {
        "ACTIVE" -> InvitationStatus.ACTIVE
        "USED" -> InvitationStatus.USED
        "REVOKED" -> InvitationStatus.REVOKED
        else -> return null
    }
    return SpaceInvitation(id, spaceId, expiresAt, status)
}

private fun Throwable.toSpaceRepositoryException(): SpaceRepositoryException =
    SpaceRepositoryException(
        failure = when ((this as? FirebaseFirestoreException)?.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> SpaceFailure.PermissionDenied
            FirebaseFirestoreException.Code.UNAVAILABLE -> SpaceFailure.Network
            else -> SpaceFailure.Unknown
        },
        cause = this,
    )
