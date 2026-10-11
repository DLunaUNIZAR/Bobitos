package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.slug
import com.dlunaunizar.bobitos.data.repository.FirestoreIngredientCatalogSource.Companion.CATALOG_META
import com.dlunaunizar.bobitos.data.repository.FirestoreIngredientCatalogSource.Companion.FIELD_VERSION
import com.dlunaunizar.bobitos.data.repository.FirestoreIngredientCatalogSource.Companion.INGREDIENTS
import com.dlunaunizar.bobitos.data.sync.SyncRepository
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreIngredientRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    syncStore: CatalogSyncStore,
) : IngredientRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val localChanges = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    // Lo posterior a un guardado confirmado vive aquí (el repositorio es un singleton), no en la corrutina del guardado.
    private val postCommitScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loader = VersionedCatalogLoader(
        source = FirestoreIngredientCatalogSource(firestore),
        store = syncStore,
        key = INGREDIENTS,
        now = System::currentTimeMillis,
    )

    // Los cambios de otros dispositivos llegan en la siguiente apertura, no en directo.
    override fun catalog(): Flow<List<CatalogIngredient>> = loader.catalog(localChanges)

    override fun isCurrentUserCatalogAdmin(): Boolean =
        authRepository.currentUser.value?.id?.let(RecipeAdmins.uids::contains) == true

    override suspend fun createIngredient(name: String, category: String?, defaultUnit: String?) = runOperation {
        val user = requireAdmin()
        val values = validateIngredientInput(name, category, defaultUnit)
        val id = slug(values.name)
        val fields = values.toFirestoreFields() + mapOf(
            FIELD_CREATED_AT to FieldValue.serverTimestamp(),
            FIELD_UPDATED_BY to user.id,
            FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
        )
        writeWithVersion(user.id, id) { transaction, ref ->
            if (transaction.get(ref).exists()) throw IngredientRepositoryException(IngredientFailure.AlreadyExists)
            transaction.set(ref, fields)
        }
    }

    override suspend fun updateIngredient(id: String, name: String, category: String?, defaultUnit: String?) =
        runOperation {
            val user = requireAdmin()
            val values = validateIngredientInput(name, category, defaultUnit)
            val fields = values.toFirestoreFields() + mapOf(
                FIELD_UPDATED_BY to user.id,
                FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
            )
            writeWithVersion(user.id, id) { transaction, ref -> transaction.update(ref, fields) }
        }

    override suspend fun deleteIngredient(id: String) = runOperation {
        val user = requireAdmin()
        writeWithVersion(user.id, id) { transaction, ref -> transaction.delete(ref) }
    }

    /**
     * Escribe la ficha y sube `catalogMeta/ingredients` en la misma transacción (las reglas exigen
     * `version == anterior + 1`, o 1 si no existe). Después refresca la ficha en la caché local y
     * adopta la nueva versión solo si nadie se interpuso, para no releer el catálogo.
     */
    private suspend fun writeWithVersion(userId: String, id: String, write: (Transaction, DocumentReference) -> Unit) {
        val ingredientRef = firestore.collection(INGREDIENTS).document(id)
        val metaRef = firestore.collection(CATALOG_META).document(INGREDIENTS)
        // La transacción ya se confirmó: relectura, adopción de la versión y aviso se hacen fuera del
        // guardado (y de su tiempo máximo) para que no puedan convertirlo en error.
        commitThenRefresh(
            scope = postCommitScope,
            timeoutMillis = REFRESH_TIMEOUT_MILLIS,
            commit = { commitVersioned(metaRef, ingredientRef, userId, write) },
            refresh = {
                ingredientRef.get(Source.SERVER).await()
                true
            },
            adopt = { previous -> loader.afterOwnWrite(previous) },
            onDone = { localChanges.tryEmit(Unit) },
        )
    }

    private suspend fun commitVersioned(
        metaRef: DocumentReference,
        ingredientRef: DocumentReference,
        userId: String,
        write: (Transaction, DocumentReference) -> Unit,
    ): Long = firestore.runTransaction { transaction ->
        val meta = transaction.get(metaRef)
        val previousVersion = if (meta.exists()) meta.getLong(FIELD_VERSION) ?: 0L else 0L
        write(transaction, ingredientRef)
        val fields = mapOf(
            FIELD_VERSION to previousVersion + 1,
            FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
            FIELD_UPDATED_BY to userId,
        )
        if (meta.exists()) transaction.update(metaRef, fields) else transaction.set(metaRef, fields)
        previousVersion
    }.await()

    // El catálogo común solo lo escribe el admin; las reglas lo vuelven a exigir.
    private fun requireAdmin(): AuthUser {
        val user = authRepository.currentUser.value
            ?: throw IngredientRepositoryException(IngredientFailure.NotAuthenticated)
        val failure = when {
            !user.isEmailVerified -> IngredientFailure.EmailNotVerified
            user.id !in RecipeAdmins.uids -> IngredientFailure.PermissionDenied
            else -> null
        }
        if (failure != null) throw IngredientRepositoryException(failure)
        return user
    }

    private suspend inline fun <T> runOperation(crossinline operation: suspend () -> T): T =
        syncRepository.runWriteOperation(
            isNetworkFailure = { it.failure == IngredientFailure.Network },
            networkException = { IngredientRepositoryException(IngredientFailure.Network, it) },
            toException = Throwable::toIngredientRepositoryException,
            operation = operation,
        )

    private companion object {
        const val REFRESH_TIMEOUT_MILLIS = 5_000L
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_BY = "updatedBy"
        const val FIELD_UPDATED_AT = "updatedAt"
    }
}

internal fun Throwable.toIngredientRepositoryException(): IngredientRepositoryException = IngredientRepositoryException(
    failure = when ((this as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> IngredientFailure.PermissionDenied
        FirebaseFirestoreException.Code.NOT_FOUND -> IngredientFailure.IngredientNotFound
        FirebaseFirestoreException.Code.UNAVAILABLE -> IngredientFailure.Network
        else -> IngredientFailure.Unknown
    },
    cause = this,
)
