package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Catálogo común de ingredientes en Firestore. Sin `orderBy`: ordenar en el servidor dejaría fuera los
 * documentos sin `nameLower`; se ordena en el cliente.
 */
class FirestoreIngredientCatalogSource(private val firestore: FirebaseFirestore) : CatalogSource<CatalogIngredient> {
    override suspend fun readCache(): CatalogPage<CatalogIngredient> = try {
        query(Source.CACHE)
    } catch (_: FirebaseFirestoreException) {
        CatalogPage(emptyList(), 0)
    }

    override suspend fun readServer(): CatalogPage<CatalogIngredient> = query(Source.SERVER)

    override suspend fun readMeta(): CatalogMeta = try {
        val snapshot = withTimeoutOrNull(META_TIMEOUT_MILLIS) {
            firestore.collection(CATALOG_META).document(INGREDIENTS).get(Source.SERVER).await()
        }
        when {
            snapshot == null -> CatalogMeta.Unreachable
            else -> snapshot.getLong(FIELD_VERSION)?.let(CatalogMeta::Known) ?: CatalogMeta.Missing
        }
    } catch (error: FirebaseFirestoreException) {
        if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            CatalogMeta.Missing
        } else {
            CatalogMeta.Unreachable
        }
    }

    private suspend fun query(source: Source): CatalogPage<CatalogIngredient> {
        val snapshot: QuerySnapshot =
            firestore.collection(INGREDIENTS).limit(MAX_VISIBLE_INGREDIENTS).get(source).await()
        val items = snapshot.documents.mapNotNull {
            parseCatalogIngredient(it.id, it.data.orEmpty(), personal = false)
        }
        return CatalogPage(
            items = items.sortedForIngredientCatalog(),
            rawCount = snapshot.size(),
        )
    }

    companion object {
        const val INGREDIENTS = "ingredients"
        const val CATALOG_META = "catalogMeta"
        const val FIELD_VERSION = "version"
        const val META_TIMEOUT_MILLIS = 10_000L
        const val MAX_VISIBLE_INGREDIENTS = 1000L
    }
}
