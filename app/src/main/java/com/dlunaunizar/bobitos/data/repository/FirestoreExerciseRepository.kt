package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.ExerciseInput
import com.dlunaunizar.bobitos.core.model.slug
import com.dlunaunizar.bobitos.data.sync.SyncRepository
import com.dlunaunizar.bobitos.data.sync.WriteNotAllowedException
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreExerciseRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    syncStore: CatalogSyncStore,
) : ExerciseRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val localChanges = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    // Lo posterior a un guardado confirmado vive aquí (el repositorio es un singleton), no en la corrutina del guardado.
    private val postCommitScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loader = VersionedCatalogLoader(
        source = FirestoreExerciseCatalogSource(firestore),
        store = syncStore,
        key = EXERCISES,
        now = System::currentTimeMillis,
    )

    // Los cambios de otros dispositivos llegan en la siguiente apertura, no en directo.
    override fun catalog(): Flow<List<CatalogExercise>> = loader.catalog(localChanges)

    override fun isCurrentUserCatalogAdmin(): Boolean =
        authRepository.currentUser.value?.id?.let(RecipeAdmins.uids::contains) == true

    override fun currentUserId(): String? = authRepository.currentUser.value?.id

    override suspend fun exerciseById(id: String): CatalogExercise? =
        runCatching { exercisesCollection().document(id).get().await().toCatalogExercise() }.getOrNull()

    override suspend fun createExercise(input: ExerciseInput) = runOperation {
        val user = requireVerifiedUser()
        val values = validateExerciseInput(input)
        val id = slug(values.name).ifEmpty { throw ExerciseRepositoryException(ExerciseFailure.NameRequired) }
        val fields = values.toFirestoreFields() + mapOf(
            FIELD_OWNER_UID to user.id,
            FIELD_CREATED_BY to user.id,
            FIELD_CREATED_BY_NAME to user.catalogDisplayName,
            FIELD_CREATED_AT to FieldValue.serverTimestamp(),
            FIELD_UPDATED_BY to user.id,
            FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
        )
        writeWithVersion(user.id, id, isCreate = true) { transaction, ref -> transaction.set(ref, fields) }
    }

    override suspend fun updateExercise(id: String, input: ExerciseInput) = runOperation {
        val user = requireVerifiedUser()
        val values = validateExerciseInput(input)
        val fields = values.toFirestoreFields() + mapOf(
            FIELD_UPDATED_BY to user.id,
            FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
        )
        writeWithVersion(user.id, id) { transaction, ref -> transaction.update(ref, fields) }
    }

    override suspend fun deleteExercise(id: String) = runOperation {
        val user = requireVerifiedUser()
        writeWithVersion(user.id, id) { transaction, ref -> transaction.delete(ref) }
    }

    private fun exercisesCollection() = firestore.collection(EXERCISES)

    /**
     * Escribe la ficha y sube `catalogMeta/exercises` en la misma transacción (las reglas exigen
     * `version == anterior + 1`, o 1 si no existe). Después refresca la ficha en la caché local y
     * adopta la nueva versión solo si nadie se interpuso, para no releer el catálogo.
     */
    private suspend fun writeWithVersion(
        userId: String,
        id: String,
        isCreate: Boolean = false,
        write: (Transaction, DocumentReference) -> Unit,
    ) {
        val exerciseRef = exercisesCollection().document(id)
        val metaRef = firestore.collection(CATALOG_META).document(EXERCISES)
        // La transacción ya se confirmó: relectura, adopción de la versión y aviso se hacen fuera del
        // guardado (y de su tiempo máximo) para que no puedan convertirlo en error.
        commitThenRefresh(
            scope = postCommitScope,
            timeoutMillis = REFRESH_TIMEOUT_MILLIS,
            commit = {
                try {
                    commitVersioned(metaRef, exerciseRef, userId, write)
                } catch (error: FirebaseFirestoreException) {
                    val denied = error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                    if (isCreate && denied && exerciseExists(exerciseRef)) {
                        throw ExerciseRepositoryException(ExerciseFailure.AlreadyExists, error)
                    }
                    throw error
                }
            },
            refresh = {
                exerciseRef.get(Source.SERVER).await()
                true
            },
            adopt = { previous -> loader.afterOwnWrite(previous) },
            onDone = { localChanges.tryEmit(Unit) },
        )
    }

    // Un alta rechazada por las reglas porque la ficha ya existe (otro la creó antes) no es falta de permiso.
    private suspend fun exerciseExists(ref: DocumentReference): Boolean = try {
        withTimeoutOrNull(REFRESH_TIMEOUT_MILLIS) { ref.get(Source.SERVER).await().exists() } ?: false
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
        false
    }

    private suspend fun commitVersioned(
        metaRef: DocumentReference,
        exerciseRef: DocumentReference,
        userId: String,
        write: (Transaction, DocumentReference) -> Unit,
    ): Long = firestore.runTransaction { transaction ->
        val meta = transaction.get(metaRef)
        val previousVersion = if (meta.exists()) meta.getLong(FIELD_VERSION) ?: 0L else 0L
        write(transaction, exerciseRef)
        val fields = mapOf(
            FIELD_VERSION to previousVersion + 1,
            FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
            FIELD_UPDATED_BY to userId,
        )
        if (meta.exists()) transaction.update(metaRef, fields) else transaction.set(metaRef, fields)
        previousVersion
    }.await()

    private fun requireVerifiedUser(): AuthUser {
        val user = authRepository.currentUser.value
            ?: throw ExerciseRepositoryException(ExerciseFailure.NotAuthenticated)
        if (!user.isEmailVerified) {
            throw ExerciseRepositoryException(ExerciseFailure.EmailNotVerified)
        }
        return user
    }

    private suspend inline fun <T> runOperation(crossinline operation: suspend () -> T): T {
        try {
            syncRepository.requireWritable()
            return operation()
        } catch (error: ExerciseRepositoryException) {
            if (error.failure == ExerciseFailure.Network) {
                syncRepository.reportWriteFailure(error.cause ?: error)
            }
            throw error
        } catch (error: WriteNotAllowedException) {
            throw ExerciseRepositoryException(ExerciseFailure.Network, error)
        } catch (error: Throwable) {
            syncRepository.reportWriteFailure(error)
            throw error.toExerciseRepositoryException()
        }
    }

    private companion object {
        const val REFRESH_TIMEOUT_MILLIS = 5_000L
        const val EXERCISES = "exercises"
        const val CATALOG_META = "catalogMeta"
        const val FIELD_VERSION = "version"
        const val FIELD_OWNER_UID = "ownerUid"
        const val FIELD_CREATED_BY = "createdBy"
        const val FIELD_CREATED_BY_NAME = "createdByName"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_BY = "updatedBy"
        const val FIELD_UPDATED_AT = "updatedAt"
    }
}

private val AuthUser.catalogDisplayName: String
    get() = displayName.ifBlank { email.substringBefore('@') }.take(60)

internal fun DocumentSnapshot.toCatalogExercise(): CatalogExercise? = parseCatalogExercise(
    id = id,
    data = data.orEmpty(),
    createdAt = getTimestamp("createdAt")?.toDate()?.toInstant(),
    updatedAt = getTimestamp("updatedAt")?.toDate()?.toInstant(),
)

private fun Throwable.toExerciseRepositoryException(): ExerciseRepositoryException = ExerciseRepositoryException(
    failure = when ((this as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> ExerciseFailure.PermissionDenied
        FirebaseFirestoreException.Code.NOT_FOUND -> ExerciseFailure.ExerciseNotFound
        FirebaseFirestoreException.Code.UNAVAILABLE -> ExerciseFailure.Network
        else -> ExerciseFailure.Unknown
    },
    cause = this,
)
