package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.data.sync.RealtimeMetrics
import com.dlunaunizar.bobitos.data.sync.SyncRepository
import com.dlunaunizar.bobitos.data.sync.WriteNotAllowedException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreShoppingRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val realtimeMetrics: RealtimeMetrics,
) : ShoppingRepository {
    private val firestore = FirebaseFirestore.getInstance()

    override fun items(spaceId: String): Flow<List<ShoppingItem>> = callbackFlow {
        val metricId = realtimeMetrics.listenerStarted("shopping:active")
        val registration = itemsCollection(spaceId)
            .orderBy(FIELD_CREATED_AT, Query.Direction.ASCENDING)
            .limit(MAX_VISIBLE_ITEMS)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> close(error.toShoppingRepositoryException())
                    snapshot != null -> {
                        realtimeMetrics.snapshotReceived(
                            scope = "shopping:active",
                            changedDocuments = snapshot.documentChanges.size,
                            fromCache = snapshot.metadata.isFromCache,
                        )
                        trySend(
                            snapshot.documents
                                .mapNotNull(DocumentSnapshot::toShoppingItem)
                                .sortedWith(
                                    compareBy<ShoppingItem> { it.purchased }
                                        .thenBy(ShoppingItem::createdAt)
                                        .thenBy(ShoppingItem::id),
                                ),
                        )
                    }
                }
            }
        awaitClose {
            registration.remove()
            realtimeMetrics.listenerStopped(metricId)
        }
    }

    override suspend fun addItem(
        spaceId: String,
        name: String,
        quantity: String?,
        notes: String?,
        supermarket: Supermarket?,
        brand: String?,
    ) = runShoppingOperation {
        val user = requireVerifiedUser()
        val values = validateItem(name, quantity, notes, supermarket, brand)
        val spaceReference = firestore.collection(SPACES).document(spaceId)
        val itemReference = itemsCollection(spaceId).document()

        firestore.runTransaction { transaction ->
            if (!transaction.get(spaceReference).exists()) {
                throw ShoppingRepositoryException(ShoppingFailure.SpaceNotFound)
            }
            transaction.set(
                itemReference,
                mapOf(
                    FIELD_NAME to values.name,
                    FIELD_QUANTITY to values.quantity,
                    FIELD_NOTES to values.notes,
                    FIELD_SUPERMARKET to values.supermarket,
                    FIELD_BRAND to values.brand,
                    FIELD_PURCHASED to false,
                    FIELD_CREATED_BY to user.id,
                    FIELD_CREATED_BY_NAME to user.shoppingDisplayName,
                    FIELD_CREATED_AT to FieldValue.serverTimestamp(),
                    FIELD_UPDATED_BY to user.id,
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                    FIELD_PURCHASED_BY to null,
                    FIELD_PURCHASED_BY_NAME to null,
                    FIELD_PURCHASED_AT to null,
                ),
            )
        }.await()
        Unit
    }

    override suspend fun updateItem(
        spaceId: String,
        itemId: String,
        name: String,
        quantity: String?,
        notes: String?,
        supermarket: Supermarket?,
        brand: String?,
    ) = runShoppingOperation {
        val user = requireVerifiedUser()
        val values = validateItem(name, quantity, notes, supermarket, brand)
        val reference = itemsCollection(spaceId).document(itemId)

        firestore.runTransaction { transaction ->
            requireItem(transaction.get(reference))
            transaction.update(
                reference,
                FIELD_NAME,
                values.name,
                FIELD_QUANTITY,
                values.quantity,
                FIELD_NOTES,
                values.notes,
                FIELD_SUPERMARKET,
                values.supermarket,
                FIELD_BRAND,
                values.brand,
                FIELD_UPDATED_BY,
                user.id,
                FIELD_UPDATED_AT,
                FieldValue.serverTimestamp(),
            )
        }.await()
        Unit
    }

    override suspend fun setPurchased(spaceId: String, itemId: String, purchased: Boolean) = runShoppingOperation {
        val user = requireVerifiedUser()
        val reference = itemsCollection(spaceId).document(itemId)

        firestore.runTransaction { transaction ->
            val item = requireItem(transaction.get(reference))
            if (item.getBoolean(FIELD_PURCHASED) != purchased) {
                transaction.update(
                    reference,
                    FIELD_PURCHASED,
                    purchased,
                    FIELD_PURCHASED_BY,
                    if (purchased) user.id else null,
                    FIELD_PURCHASED_BY_NAME,
                    if (purchased) user.shoppingDisplayName else null,
                    FIELD_PURCHASED_AT,
                    if (purchased) FieldValue.serverTimestamp() else null,
                    FIELD_UPDATED_BY,
                    user.id,
                    FIELD_UPDATED_AT,
                    FieldValue.serverTimestamp(),
                )
            }
        }.await()
        Unit
    }

    override suspend fun deleteItem(spaceId: String, itemId: String) = runShoppingOperation {
        requireVerifiedUser()
        val reference = itemsCollection(spaceId).document(itemId)
        firestore.runTransaction { transaction ->
            requireItem(transaction.get(reference))
            transaction.delete(reference)
        }.await()
        Unit
    }

    override suspend fun clearPurchased(spaceId: String): Int = runShoppingOperation {
        requireVerifiedUser()
        val candidates = itemsCollection(spaceId)
            .whereEqualTo(FIELD_PURCHASED, true)
            .limit(MAX_CLEAR_ITEMS)
            .get(Source.SERVER)
            .await()

        if (candidates.isEmpty) return@runShoppingOperation 0

        firestore.runTransaction { transaction ->
            var deleted = 0
            val currentItems = candidates.documents.map { candidate ->
                transaction.get(candidate.reference)
            }
            currentItems.forEach { current ->
                if (current.exists() && current.getBoolean(FIELD_PURCHASED) == true) {
                    transaction.delete(current.reference)
                    deleted += 1
                }
            }
            deleted
        }.await()
    }

    private fun itemsCollection(spaceId: String) = firestore
        .collection(SPACES)
        .document(spaceId)
        .collection(SHOPPING_ITEMS)

    private fun requireVerifiedUser(): AuthUser {
        val user = authRepository.currentUser.value
            ?: throw ShoppingRepositoryException(ShoppingFailure.NotAuthenticated)
        if (!user.isEmailVerified) {
            throw ShoppingRepositoryException(ShoppingFailure.EmailNotVerified)
        }
        return user
    }

    private fun validateItem(
        name: String,
        quantity: String?,
        notes: String?,
        supermarket: Supermarket?,
        brand: String?,
    ): ItemValues {
        val normalizedName = name.trim()
        val normalizedQuantity = quantity?.trim()?.takeIf(String::isNotEmpty)
        val normalizedNotes = notes?.trim()?.takeIf(String::isNotEmpty)
        val normalizedBrand = brand?.trim()?.takeIf(String::isNotEmpty)
        when {
            normalizedName.isEmpty() -> throw ShoppingRepositoryException(ShoppingFailure.NameRequired)
            normalizedName.length > MAX_NAME_LENGTH -> throw ShoppingRepositoryException(ShoppingFailure.NameTooLong)
            normalizedQuantity != null && normalizedQuantity.length > MAX_QUANTITY_LENGTH -> {
                throw ShoppingRepositoryException(ShoppingFailure.QuantityTooLong)
            }
            normalizedNotes != null && normalizedNotes.length > MAX_NOTES_LENGTH -> {
                throw ShoppingRepositoryException(ShoppingFailure.NotesTooLong)
            }
            normalizedBrand != null && normalizedBrand.length > MAX_BRAND_LENGTH -> {
                throw ShoppingRepositoryException(ShoppingFailure.BrandTooLong)
            }
        }
        return ItemValues(normalizedName, normalizedQuantity, normalizedNotes, supermarket?.name, normalizedBrand)
    }

    private fun requireItem(snapshot: DocumentSnapshot): DocumentSnapshot {
        if (!snapshot.exists()) {
            throw ShoppingRepositoryException(ShoppingFailure.ItemNotFound)
        }
        return snapshot
    }

    private suspend inline fun <T> runShoppingOperation(crossinline operation: suspend () -> T): T {
        try {
            syncRepository.requireWritable()
            return operation()
        } catch (error: ShoppingRepositoryException) {
            if (error.failure == ShoppingFailure.Network) {
                syncRepository.reportWriteFailure(error.cause ?: error)
            }
            throw error
        } catch (error: WriteNotAllowedException) {
            throw ShoppingRepositoryException(ShoppingFailure.Network, error)
        } catch (error: Throwable) {
            syncRepository.reportWriteFailure(error)
            throw error.toShoppingRepositoryException()
        }
    }

    private data class ItemValues(
        val name: String,
        val quantity: String?,
        val notes: String?,
        val supermarket: String?,
        val brand: String?,
    )

    private companion object {
        const val SPACES = "spaces"
        const val SHOPPING_ITEMS = "shoppingItems"
        const val FIELD_NAME = "name"
        const val FIELD_QUANTITY = "quantity"
        const val FIELD_NOTES = "notes"
        const val FIELD_SUPERMARKET = "supermarket"
        const val FIELD_BRAND = "brand"
        const val FIELD_PURCHASED = "purchased"
        const val FIELD_CREATED_BY = "createdBy"
        const val FIELD_CREATED_BY_NAME = "createdByName"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_BY = "updatedBy"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val FIELD_PURCHASED_BY = "purchasedBy"
        const val FIELD_PURCHASED_BY_NAME = "purchasedByName"
        const val FIELD_PURCHASED_AT = "purchasedAt"
        const val MAX_NAME_LENGTH = 120
        const val MAX_QUANTITY_LENGTH = 40
        const val MAX_NOTES_LENGTH = 500
        const val MAX_BRAND_LENGTH = 60
        const val MAX_VISIBLE_ITEMS = 250L
        const val MAX_CLEAR_ITEMS = 100L
    }
}

private val AuthUser.shoppingDisplayName: String
    get() = displayName.ifBlank { email.substringBefore('@') }.take(60)

private fun DocumentSnapshot.toShoppingItem(): ShoppingItem? {
    val createdAt = getTimestamp("createdAt")?.toDate()?.toInstant() ?: return null
    val updatedAt = getTimestamp("updatedAt")?.toDate()?.toInstant() ?: createdAt
    return ShoppingItem(
        id = id,
        name = getString("name") ?: return null,
        quantity = getString("quantity"),
        notes = getString("notes"),
        purchased = getBoolean("purchased") ?: false,
        createdBy = getString("createdBy") ?: return null,
        createdByName = getString("createdByName") ?: getString("createdBy") ?: return null,
        createdAt = createdAt,
        updatedBy = getString("updatedBy") ?: getString("createdBy") ?: return null,
        updatedAt = updatedAt,
        purchasedBy = getString("purchasedBy"),
        purchasedByName = getString("purchasedByName"),
        purchasedAt = getTimestamp("purchasedAt")?.toDate()?.toInstant(),
        supermarket = getString("supermarket")?.let { value ->
            runCatching { Supermarket.valueOf(value) }.getOrNull()
        },
        brand = getString("brand"),
    )
}

private fun Throwable.toShoppingRepositoryException(): ShoppingRepositoryException = ShoppingRepositoryException(
    failure = when ((this as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> ShoppingFailure.PermissionDenied
        FirebaseFirestoreException.Code.NOT_FOUND -> ShoppingFailure.ItemNotFound
        FirebaseFirestoreException.Code.UNAVAILABLE -> ShoppingFailure.Network
        else -> ShoppingFailure.Unknown
    },
    cause = this,
)
