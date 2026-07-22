package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.data.sync.RealtimeMetrics
import com.dlunaunizar.bobitos.data.sync.SyncRepository
import com.dlunaunizar.bobitos.data.sync.WriteNotAllowedException
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

    override suspend fun setPref(ingredientId: String, supermarket: Supermarket?, brand: String?) = runOperation {
        val uid = requireVerifiedUid()
        val entry = mapOf(
            FIELD_SUPERMARKET to supermarket?.name,
            FIELD_BRAND to brand?.trim()?.takeIf(String::isNotEmpty)?.take(MAX_BRAND_LENGTH),
        )
        prefsDoc(uid).set(mapOf(FIELD_ENTRIES to mapOf(ingredientId to entry)), SetOptions.merge()).await()
        Unit
    }

    override suspend fun clearPref(ingredientId: String) = runOperation {
        val uid = requireVerifiedUid()
        prefsDoc(uid)
            .set(mapOf(FIELD_ENTRIES to mapOf(ingredientId to FieldValue.delete())), SetOptions.merge())
            .await()
        Unit
    }

    private fun prefsDoc(uid: String) = firestore.collection(INGREDIENT_PREFS).document(uid)

    private fun requireVerifiedUid(): String {
        val user: AuthUser = authRepository.currentUser.value
            ?: throw IngredientPrefsException(IngredientPrefFailure.NotAuthenticated)
        if (!user.isEmailVerified) {
            throw IngredientPrefsException(IngredientPrefFailure.EmailNotVerified)
        }
        return user.id
    }

    private suspend inline fun <T> runOperation(crossinline operation: suspend () -> T): T {
        try {
            syncRepository.requireWritable()
            return operation()
        } catch (error: IngredientPrefsException) {
            if (error.failure == IngredientPrefFailure.Network) {
                syncRepository.reportWriteFailure(error.cause ?: error)
            }
            throw error
        } catch (error: WriteNotAllowedException) {
            throw IngredientPrefsException(IngredientPrefFailure.Network, error)
        } catch (error: Throwable) {
            syncRepository.reportWriteFailure(error)
            throw error.toPrefsException()
        }
    }

    private companion object {
        const val INGREDIENT_PREFS = "ingredientPrefs"
        const val SCOPE = "ingredientPrefs"
        const val FIELD_ENTRIES = "entries"
        const val FIELD_SUPERMARKET = "supermarket"
        const val FIELD_BRAND = "brand"
        const val MAX_BRAND_LENGTH = 60
    }
}

// Convierte el mapa crudo de Firestore (`entries`) en preferencias tipadas; descarta entradas
// malformadas o vacías y los supermercados desconocidos (retro-compatibilidad).
internal fun parseIngredientPrefs(entries: Map<String, Any?>?): Map<String, IngredientPref> {
    if (entries == null) return emptyMap()
    return entries.mapNotNull { (id, raw) ->
        val map = raw as? Map<*, *> ?: return@mapNotNull null
        val supermarket = (map["supermarket"] as? String)
            ?.let { value -> runCatching { Supermarket.valueOf(value) }.getOrNull() }
        val brand = (map["brand"] as? String)?.takeIf(String::isNotBlank)
        if (supermarket == null && brand == null) null else id to IngredientPref(supermarket, brand)
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
