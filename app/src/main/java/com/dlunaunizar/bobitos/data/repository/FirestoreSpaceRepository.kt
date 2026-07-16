package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.SpaceRole
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import javax.inject.Inject
import javax.inject.Singleton
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
) : SpaceRepository {
    private val firestore = FirebaseFirestore.getInstance()

    override val spaces: Flow<List<SpaceSummary>> = authRepository.currentUser
        .flatMapLatest { user ->
            if (user == null || !user.isEmailVerified) {
                flowOf(emptyList())
            } else {
                membershipsForUser(user.id).flatMapLatest { memberships ->
                    val spaceFlows = memberships.map { membership ->
                        observeSpace(membership)
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

    override fun members(spaceId: String): Flow<List<SpaceMember>> = callbackFlow {
        val registration = firestore.collection(MEMBERSHIPS)
            .whereEqualTo(FIELD_SPACE_ID, spaceId)
            .whereEqualTo(FIELD_STATUS, STATUS_ACTIVE)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> close(error.toSpaceRepositoryException())
                    snapshot != null -> {
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
        awaitClose(registration::remove)
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

    private fun membershipsForUser(userId: String): Flow<List<MembershipSnapshot>> = callbackFlow {
        val registration = firestore.collection(MEMBERSHIPS)
            .whereEqualTo(FIELD_USER_ID, userId)
            .whereEqualTo(FIELD_STATUS, STATUS_ACTIVE)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> close(error.toSpaceRepositoryException())
                    snapshot != null -> trySend(
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
        awaitClose(registration::remove)
    }

    private fun observeSpace(membership: MembershipSnapshot): Flow<SpaceSummary?> = callbackFlow {
        val registration = firestore.collection(SPACES)
            .document(membership.spaceId)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> close(error.toSpaceRepositoryException())
                    snapshot == null || !snapshot.exists() -> trySend(null)
                    else -> {
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
        awaitClose(registration::remove)
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

    private data class MembershipSnapshot(
        val spaceId: String,
        val role: SpaceRole,
    )

    private companion object {
        const val MAX_SPACE_NAME_LENGTH = 60
        const val SPACES = "spaces"
        const val MEMBERSHIPS = "memberships"
        const val TASKS = "tasks"
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
        const val FIELD_ASSIGNEE_ID = "assigneeId"
        const val STATUS_ACTIVE = "ACTIVE"
        const val ROLE_OWNER = "OWNER"
        const val ROLE_MEMBER = "MEMBER"
        const val TASK_STATUS_TODO = "TODO"
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

private suspend inline fun <T> runSpaceOperation(
    crossinline operation: suspend () -> T,
): T = try {
    operation()
} catch (error: SpaceRepositoryException) {
    throw error
} catch (error: Throwable) {
    throw error.toSpaceRepositoryException()
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
