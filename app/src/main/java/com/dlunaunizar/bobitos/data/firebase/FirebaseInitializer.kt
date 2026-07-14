package com.dlunaunizar.bobitos.data.firebase

import android.content.Context
import com.dlunaunizar.bobitos.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseInitializer @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun initialize() {
        if (!BuildConfig.USE_FIREBASE_EMULATORS) return

        val firebaseApp = getOrCreateFirebaseApp()
        FirebaseAuth.getInstance(firebaseApp).useEmulator(
            BuildConfig.FIREBASE_EMULATOR_HOST,
            BuildConfig.FIREBASE_AUTH_EMULATOR_PORT,
        )

        FirebaseFirestore.getInstance(firebaseApp).apply {
            useEmulator(
                BuildConfig.FIREBASE_EMULATOR_HOST,
                BuildConfig.FIREBASE_FIRESTORE_EMULATOR_PORT,
            )
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
    }

    private fun getOrCreateFirebaseApp(): FirebaseApp {
        val existingApp = FirebaseApp.getApps(context)
            .firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }

        if (existingApp != null) {
            check(existingApp.options.projectId == BuildConfig.FIREBASE_PROJECT_ID) {
                "La configuración Firebase debug no coincide con el proyecto demo."
            }
            return existingApp
        }

        val options = FirebaseOptions.Builder()
            .setApplicationId(DEMO_APPLICATION_ID)
            .setApiKey(DEMO_API_KEY)
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .build()

        return FirebaseApp.initializeApp(context, options)
    }

    private companion object {
        const val DEMO_APPLICATION_ID = "1:000000000000:android:demo-bobitos"
        const val DEMO_API_KEY = "demo-api-key"
    }
}
