package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.PantryEntry
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.core.model.pantryId
import com.dlunaunizar.bobitos.core.model.sameContentAs
import com.dlunaunizar.bobitos.data.sync.RealtimeMetrics
import com.dlunaunizar.bobitos.data.sync.SyncRepository
import com.dlunaunizar.bobitos.data.sync.WriteNotAllowedException
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
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
                        trySend(snapshot.documents.toSortedShoppingItems())
                    }
                }
            }
        awaitClose {
            registration.remove()
            realtimeMetrics.listenerStopped(metricId)
        }
    }

    override suspend fun currentItems(spaceId: String): List<ShoppingItem> = runShoppingOperation {
        itemsCollection(spaceId)
            .orderBy(FIELD_CREATED_AT, Query.Direction.ASCENDING)
            .limit(MAX_VISIBLE_ITEMS)
            .get(Source.SERVER)
            .await()
            .documents
            .toSortedShoppingItems()
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
            transaction.set(itemReference, newItemFields(user, values))
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
            transaction.update(reference, changedItemFields(user, values, rename = true, relist = false))
        }.await()
        Unit
    }

    override suspend fun applyWrites(spaceId: String, writes: List<ShoppingWrite>) = runShoppingOperation {
        if (writes.isEmpty()) return@runShoppingOperation
        val user = requireVerifiedUser()
        val planned = writes.map { planWrite(spaceId, it) }
        val spaceReference = firestore.collection(SPACES).document(spaceId)

        firestore.runTransaction { transaction ->
            // Firestore exige hacer todas las lecturas antes de la primera escritura.
            if (!transaction.get(spaceReference).exists()) {
                throw ShoppingRepositoryException(ShoppingFailure.SpaceNotFound)
            }
            planned.forEach { plan -> plan.seen?.let { requireUnchanged(transaction.get(plan.reference), it) } }
            planned.forEach { plan ->
                if (plan.seen == null) {
                    transaction.set(plan.reference, newItemFields(user, plan.values))
                } else {
                    val fields = changedItemFields(user, plan.values, rename = false, relist = plan.relist)
                    transaction.update(plan.reference, fields)
                }
            }
        }.await()
        Unit
    }

    // Valida cada escritura y le da su documento (uno nuevo para las altas) antes de abrir la transacción.
    private fun planWrite(spaceId: String, write: ShoppingWrite): PlannedWrite = when (write) {
        is ShoppingWrite.Add -> PlannedWrite(
            reference = itemsCollection(spaceId).document(),
            values = validateItem(write.name, write.quantity, write.notes, write.supermarket, write.brand),
            seen = null,
            relist = false,
        )
        is ShoppingWrite.Change -> PlannedWrite(
            reference = itemsCollection(spaceId).document(write.seen.id),
            values = validateItem(write.seen.name, write.quantity, write.notes, write.supermarket, write.brand),
            seen = write.seen,
            relist = write.relist,
        )
    }

    // El artículo sigue como se vio; si otro miembro lo ha cambiado, no se escribe nada de la transacción.
    private fun requireUnchanged(snapshot: DocumentSnapshot, seen: ShoppingItem) {
        val current = requireItem(snapshot).toShoppingItem()
            ?: throw ShoppingRepositoryException(ShoppingFailure.ItemNotFound)
        if (!current.sameContentAs(seen)) throw ShoppingRepositoryException(ShoppingFailure.ItemChanged)
    }

    private fun newItemFields(user: AuthUser, values: ItemValues): Map<String, Any?> = mapOf(
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
    )

    // Campos de una edición: con `rename` cambia también el nombre; con `relist` deja el artículo pendiente, sin quién
    // ni cuándo lo compró. Los `null` se escriben como `null`.
    private fun changedItemFields(
        user: AuthUser,
        values: ItemValues,
        rename: Boolean,
        relist: Boolean,
    ): Map<String, Any?> {
        val fields = mutableMapOf<String, Any?>()
        if (rename) fields[FIELD_NAME] = values.name
        fields[FIELD_QUANTITY] = values.quantity
        fields[FIELD_NOTES] = values.notes
        fields[FIELD_SUPERMARKET] = values.supermarket
        fields[FIELD_BRAND] = values.brand
        if (relist) {
            fields[FIELD_PURCHASED] = false
            fields[FIELD_PURCHASED_BY] = null
            fields[FIELD_PURCHASED_BY_NAME] = null
            fields[FIELD_PURCHASED_AT] = null
        }
        fields[FIELD_UPDATED_BY] = user.id
        fields[FIELD_UPDATED_AT] = FieldValue.serverTimestamp()
        return fields
    }

    override suspend fun setPurchased(spaceId: String, itemId: String, purchased: Boolean, pantryEntry: PantryEntry?) =
        runShoppingOperation {
            val user = requireVerifiedUser()
            val reference = itemsCollection(spaceId).document(itemId)
            val entry = pantryEntry?.let(::validPantryEntryOrNull)

            firestore.runTransaction { transaction ->
                val item = requireItem(transaction.get(reference))
                // Firestore exige hacer todas las lecturas antes de la primera escritura.
                val pantry =
                    readPantry(transaction, spaceId, if (purchased) entry?.name else item.getString(FIELD_NAME))
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
                    if (pantry != null) writePantry(transaction, pantry, entry.takeIf { purchased }, user.id, itemId)
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

    // Una entrada que no se puede apuntar (sin letras, demasiado larga) no impide marcar el artículo.
    private fun validPantryEntryOrNull(entry: PantryEntry): PantryEntry? = try {
        validatePantryEntry(entry)
    } catch (_: PantryRepositoryException) {
        null
    }

    private fun readPantry(transaction: Transaction, spaceId: String, name: String?): DocumentSnapshot? {
        val id = name?.let(::pantryId)?.takeIf(String::isNotEmpty) ?: return null
        return transaction.get(pantryCollection(spaceId).document(id))
    }

    // Con [entry] (se marcó): la apunta si no estaba. Sin ella (se desmarcó): la quita si la trajo este artículo.
    private fun writePantry(
        transaction: Transaction,
        pantry: DocumentSnapshot,
        entry: PantryEntry?,
        userId: String,
        itemId: String,
    ) {
        if (entry != null) {
            if (!pantry.exists()) transaction.set(pantry.reference, pantryItemFields(entry, userId, itemId))
        } else if (pantry.getString(FIELD_PANTRY_SHOPPING_ITEM_ID) == itemId) {
            transaction.delete(pantry.reference)
        }
    }

    private fun pantryCollection(spaceId: String) = firestore
        .collection(SPACES)
        .document(spaceId)
        .collection(PANTRY_ITEMS)

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

    private data class PlannedWrite(
        val reference: DocumentReference,
        val values: ItemValues,
        val seen: ShoppingItem?,
        val relist: Boolean,
    )

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

// Mapeo y orden de la lista: pendientes antes que comprados, y por antigüedad.
private fun List<DocumentSnapshot>.toSortedShoppingItems(): List<ShoppingItem> =
    mapNotNull(DocumentSnapshot::toShoppingItem)
        .sortedWith(
            compareBy<ShoppingItem> { it.purchased }
                .thenBy(ShoppingItem::createdAt)
                .thenBy(ShoppingItem::id),
        )

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
