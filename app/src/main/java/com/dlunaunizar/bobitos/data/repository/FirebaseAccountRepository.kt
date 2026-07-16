package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.data.sync.SyncRepository
import com.dlunaunizar.bobitos.data.sync.WriteNotAllowedException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

@Singleton
class FirebaseAccountRepository @Inject constructor(
    private val spaces: SpaceRepository,
    private val syncRepository: SyncRepository,
) : AccountRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    override suspend fun deleteAccount(password: String) {
        try {
            if (password.isBlank()) {
                throw AccountRepositoryException(AccountFailure.PasswordRequired)
            }
            syncRepository.requireWritable()
            val user = auth.currentUser
                ?: throw AccountRepositoryException(AccountFailure.NotAuthenticated)
            user.reauthenticate(
                EmailAuthProvider.getCredential(user.email.orEmpty(), password),
            ).await()
            val memberships = firestore.collection(MEMBERSHIPS)
                .whereEqualTo(FIELD_USER_ID, user.uid)
                .whereEqualTo(FIELD_STATUS, ACTIVE)
                .get(Source.SERVER).await().documents
            if (memberships.any { it.getString(FIELD_ROLE) == OWNER }) {
                throw AccountRepositoryException(AccountFailure.OwnerSpacesRemaining)
            }
            memberships.forEach { membership ->
                val spaceId = membership.getString(FIELD_SPACE_ID)
                    ?: throw AccountRepositoryException(AccountFailure.Unknown)
                anonymizeDisplayNames(spaceId, user.uid)
                spaces.leaveSpace(spaceId)
            }
            user.delete().await()
        } catch (error: AccountRepositoryException) {
            throw error
        } catch (error: Throwable) {
            throw AccountRepositoryException(error.failure(), error)
        }
    }

    private suspend fun anonymizeDisplayNames(spaceId: String, userId: String) {
        val space = firestore.collection(SPACES).document(spaceId)
        val documents = listOf(SHOPPING_ITEMS, TASKS, EVENTS).flatMap { name ->
            space.collection(name).get(Source.SERVER).await().documents
        }
        documents.chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { document ->
                val updates = document.anonymousNameUpdates(userId)
                if (updates.isNotEmpty()) batch.update(document.reference, updates)
            }
            batch.commit().await()
        }
    }

    private companion object {
        const val MEMBERSHIPS = "memberships"
        const val SPACES = "spaces"
        const val SHOPPING_ITEMS = "shoppingItems"
        const val TASKS = "tasks"
        const val EVENTS = "events"
        const val FIELD_USER_ID = "userId"
        const val FIELD_SPACE_ID = "spaceId"
        const val FIELD_STATUS = "status"
        const val FIELD_ROLE = "role"
        const val ACTIVE = "ACTIVE"
        const val OWNER = "OWNER"
    }
}

private fun DocumentSnapshot.anonymousNameUpdates(userId: String): Map<String, Any> {
    val updates = mutableMapOf<String, Any>()
    listOf(
        "createdBy" to "createdByName",
        "purchasedBy" to "purchasedByName",
        "assigneeId" to "assigneeName",
        "completedBy" to "completedByName",
    ).forEach { (id, name) ->
        if (getString(id) == userId && getString(name) != ANONYMOUS_NAME) {
            updates[name] = ANONYMOUS_NAME
        }
    }
    val participantIds = (get("participantIds") as? List<*>)?.filterIsInstance<String>().orEmpty()
    val participantNames = (get("participantNames") as? List<*>)?.filterIsInstance<String>().orEmpty()
    if (userId in participantIds && participantNames.size == participantIds.size) {
        updates["participantNames"] = participantNames.mapIndexed { index, name ->
            if (participantIds[index] == userId) ANONYMOUS_NAME else name
        }
    }
    return updates
}

private fun Throwable.failure() = when (this) {
    is WriteNotAllowedException -> AccountFailure.Network
    is FirebaseAuthInvalidCredentialsException -> AccountFailure.InvalidCredentials
    is FirebaseNetworkException -> AccountFailure.Network
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> AccountFailure.PermissionDenied
        FirebaseFirestoreException.Code.UNAVAILABLE -> AccountFailure.Network
        else -> AccountFailure.Unknown
    }
    else -> AccountFailure.Unknown
}

private const val ANONYMOUS_NAME = "Usuario eliminado"
