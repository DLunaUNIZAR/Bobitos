package com.dlunaunizar.bobitos.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val EXERCISE_IMAGES = "exerciseImages"

// Lee exerciseImages/{id}. Un CACHE sin documento lanza FirebaseFirestoreException: lo absorbe CachedExerciseImageRepository.
internal class FirestoreImageSource(private val firestore: FirebaseFirestore) : ImageSource {
    override suspend fun readCache(exerciseId: String): StoredImage? = read(exerciseId, Source.CACHE)

    override suspend fun readServer(exerciseId: String): StoredImage? = read(exerciseId, Source.SERVER)

    private suspend fun read(exerciseId: String, source: Source): StoredImage? =
        firestore.document("$EXERCISE_IMAGES/$exerciseId").get(source).await().toStoredImage()
}

private fun DocumentSnapshot.toStoredImage(): StoredImage? {
    if (!exists()) return null
    return parseImageDoc(mapOf("data" to getBlob("data")?.toBytes(), "hash" to getString("hash")))
}

@Singleton
class FirestoreExerciseImageRepository @Inject constructor() :
    ExerciseImageRepository by CachedExerciseImageRepository(FirestoreImageSource(FirebaseFirestore.getInstance()))
