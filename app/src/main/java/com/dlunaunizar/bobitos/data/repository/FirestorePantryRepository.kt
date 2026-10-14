package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.PantryEntry
import com.dlunaunizar.bobitos.core.model.PantryItem
import com.dlunaunizar.bobitos.core.model.pantryId
import com.dlunaunizar.bobitos.data.sync.RealtimeMetrics
import com.dlunaunizar.bobitos.data.sync.SyncRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestorePantryRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val realtimeMetrics: RealtimeMetrics,
) : PantryRepository {
    private val firestore = FirebaseFirestore.getInstance()

    override fun items(spaceId: String): Flow<List<PantryItem>> =
        pantryCollection(spaceId).limit(MAX_PANTRY_ITEMS).documentsFlow(
            metrics = realtimeMetrics,
            scope = "pantry:active",
            toException = Throwable::toPantryRepositoryException,
        ) { documents -> documents.mapNotNull(DocumentSnapshot::toPantryItem) }

    override suspend fun currentIds(spaceId: String): Set<String> = runPantryOperation {
        pantryCollection(spaceId)
            .limit(MAX_PANTRY_ITEMS)
            .get(Source.SERVER)
            .await()
            .documents
            .mapTo(HashSet(), DocumentSnapshot::getId)
    }

    override suspend fun add(spaceId: String, entries: List<PantryEntry>) = runPantryOperation {
        val user = requireVerifiedUser()
        val valid = entries.map(::validatePantryEntry).distinctBy { pantryId(it.name) }
        val spaceReference = firestore.collection(SPACES).document(spaceId)
        valid.chunked(MAX_ADDS_PER_TRANSACTION).forEach { chunk ->
            firestore.runTransaction { transaction ->
                if (!transaction.get(spaceReference).exists()) {
                    throw PantryRepositoryException(PantryFailure.SpaceNotFound)
                }
                val references = chunk.map { pantryCollection(spaceId).document(pantryId(it.name)) }
                // Firestore exige hacer todas las lecturas antes de la primera escritura.
                val missing = references.map { !transaction.get(it).exists() }
                chunk.forEachIndexed { index, entry ->
                    if (missing[index]) transaction.set(references[index], pantryItemFields(entry, user.id, null))
                }
            }.await()
        }
    }

    override suspend fun remove(spaceId: String, ids: List<String>) = runPantryOperation {
        requireVerifiedUser()
        ids.filter(String::isNotEmpty).distinct().chunked(MAX_BATCH_DELETES).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.delete(pantryCollection(spaceId).document(it)) }
            batch.commit().await()
        }
    }

    private fun pantryCollection(spaceId: String) = firestore.collection(SPACES)
        .document(spaceId).collection(PANTRY_ITEMS)

    private fun requireVerifiedUser(): AuthUser {
        val user = authRepository.currentUser.value
            ?: throw PantryRepositoryException(PantryFailure.NotAuthenticated)
        if (!user.isEmailVerified) throw PantryRepositoryException(PantryFailure.EmailNotVerified)
        return user
    }

    private suspend inline fun <T> runPantryOperation(crossinline operation: suspend () -> T): T =
        syncRepository.runWriteOperation(
            isNetworkFailure = { it.failure == PantryFailure.Network },
            networkException = { PantryRepositoryException(PantryFailure.Network, it) },
            toException = Throwable::toPantryRepositoryException,
            operation = operation,
        )

    private companion object {
        const val SPACES = "spaces"
        const val MAX_PANTRY_ITEMS = 1000L
        const val MAX_ADDS_PER_TRANSACTION = 100
        const val MAX_BATCH_DELETES = 400
    }
}

private fun Throwable.toPantryRepositoryException() = this as? PantryRepositoryException ?: PantryRepositoryException(
    failure = when ((this as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> PantryFailure.PermissionDenied
        FirebaseFirestoreException.Code.UNAVAILABLE -> PantryFailure.Network
        else -> PantryFailure.Unknown
    },
    cause = this,
)
