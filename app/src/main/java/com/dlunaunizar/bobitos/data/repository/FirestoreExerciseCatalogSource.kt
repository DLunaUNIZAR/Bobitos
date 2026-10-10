package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Catálogo de ejercicios en Firestore. Sin `orderBy`: ordenar en el servidor dejaría fuera las
 * fichas sin `nameLower`; se ordena en el cliente.
 */
class FirestoreExerciseCatalogSource(private val firestore: FirebaseFirestore) : CatalogSource<CatalogExercise> {
    override suspend fun readCache(): CatalogPage<CatalogExercise> = try {
        query(Source.CACHE)
    } catch (_: FirebaseFirestoreException) {
        CatalogPage(emptyList(), 0)
    }

    override suspend fun readServer(): CatalogPage<CatalogExercise> = query(Source.SERVER)

    override suspend fun readMeta(): CatalogMeta = try {
        val snapshot = withTimeoutOrNull(META_TIMEOUT_MILLIS) {
            firestore.collection(CATALOG_META).document(EXERCISES).get(Source.SERVER).await()
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

    private suspend fun query(source: Source): CatalogPage<CatalogExercise> {
        val snapshot: QuerySnapshot = firestore.collection(EXERCISES).limit(MAX_VISIBLE_EXERCISES).get(source).await()
        return CatalogPage(
            items = snapshot.documents.mapNotNull(DocumentSnapshot::toCatalogExercise).sortedForCatalog(),
            rawCount = snapshot.size(),
        )
    }

    companion object {
        const val EXERCISES = "exercises"
        const val CATALOG_META = "catalogMeta"
        const val FIELD_VERSION = "version"
        const val META_TIMEOUT_MILLIS = 10_000L
        const val MAX_VISIBLE_EXERCISES = 1000L
    }
}
