package com.dlunaunizar.bobitos.data.repository

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** Deshacer un alta solo borra si quien deshace es el último que escribió el elemento. */
internal fun isLastWriter(updatedBy: String?, userId: String): Boolean = updatedBy == userId

/**
 * Deshace un alta en una transacción: lanza [notFound] si [reference] ya no existe, [changed] si quien lo escribió
 * por última vez no es [userId], y si no lo borra.
 */
internal suspend fun FirebaseFirestore.deleteIfLastWriter(
    reference: DocumentReference,
    userId: String,
    notFound: () -> Throwable,
    changed: () -> Throwable,
) {
    runTransaction { transaction ->
        val snapshot = transaction.get(reference)
        if (!snapshot.exists()) throw notFound()
        if (!isLastWriter(snapshot.getString("updatedBy"), userId)) throw changed()
        transaction.delete(reference)
    }.await()
}
