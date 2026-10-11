package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.data.sync.RealtimeMetrics
import com.dlunaunizar.bobitos.data.sync.SyncRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreIngredientPrefsRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val realtimeMetrics: RealtimeMetrics,
) : IngredientPrefsRepository {
    private val firestore = FirebaseFirestore.getInstance()

    override fun prefs(): Flow<Map<String, IngredientPref>> {
        val uid = authRepository.currentUser.value?.id ?: return flowOf(emptyMap())
        return callbackFlow {
            val metricId = realtimeMetrics.listenerStarted(SCOPE)
            val registration = prefsDoc(uid).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error.toPrefsException())
                } else {
                    realtimeMetrics.snapshotReceived(
                        SCOPE,
                        if (snapshot?.exists() == true) 1 else 0,
                        snapshot?.metadata?.isFromCache == true,
                    )
                    @Suppress("UNCHECKED_CAST")
                    val entries = snapshot?.get(FIELD_ENTRIES) as? Map<String, Any?>
                    trySend(parseIngredientPrefs(entries))
                }
            }
            awaitClose {
                registration.remove()
                realtimeMetrics.listenerStopped(metricId)
            }
        }
    }

    override suspend fun setStores(ingredientId: String, stores: List<Supermarket>) = runOperation {
        val uid = requireVerifiedUid()
        val normalized = normalizeStores(stores)
        val entry: Any = if (normalized.isEmpty()) {
            FieldValue.delete()
        } else {
            mapOf(FIELD_STORES to normalized.map(Supermarket::name))
        }
        prefsDoc(uid).set(mapOf(FIELD_ENTRIES to mapOf(ingredientId to entry)), SetOptions.merge()).await()
        Unit
    }

    private fun prefsDoc(uid: String) = firestore.collection(INGREDIENT_PREFS_COLLECTION).document(uid)

    private fun requireVerifiedUid(): String {
        val user: AuthUser = authRepository.currentUser.value
            ?: throw IngredientPrefsException(IngredientPrefFailure.NotAuthenticated)
        if (!user.isEmailVerified) {
            throw IngredientPrefsException(IngredientPrefFailure.EmailNotVerified)
        }
        return user.id
    }

    private suspend inline fun <T> runOperation(crossinline operation: suspend () -> T): T =
        syncRepository.runWriteOperation(
            isNetworkFailure = { it.failure == IngredientPrefFailure.Network },
            networkException = { IngredientPrefsException(IngredientPrefFailure.Network, it) },
            toException = Throwable::toPrefsException,
            operation = operation,
        )

    private companion object {
        const val SCOPE = "ingredientPrefs"
        const val FIELD_ENTRIES = "entries"
        const val FIELD_STORES = "stores"
    }
}

// Convierte el mapa crudo de Firestore (`entries`) en tiendas tipadas; ignora las entradas con la forma
// antigua (supermercado/marca), las malformadas y las que se quedan sin tiendas.
internal fun parseIngredientPrefs(entries: Map<String, Any?>?): Map<String, IngredientPref> {
    if (entries == null) return emptyMap()
    return entries.mapNotNull { (id, raw) ->
        val map = raw as? Map<*, *> ?: return@mapNotNull null
        val stores = parseStores(map["stores"])
        if (stores.isEmpty()) null else id to IngredientPref(stores)
    }.toMap()
}

private fun Throwable.toPrefsException(): IngredientPrefsException = IngredientPrefsException(
    failure = when ((this as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> IngredientPrefFailure.PermissionDenied
        FirebaseFirestoreException.Code.UNAVAILABLE -> IngredientPrefFailure.Network
        else -> IngredientPrefFailure.Unknown
    },
    cause = this,
)
