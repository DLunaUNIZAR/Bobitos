package com.dlunaunizar.bobitos.data.firebase

import android.content.Context
import android.util.Log
import com.dlunaunizar.bobitos.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseInitializer @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    @Volatile
    private var initialized = false
    private lateinit var configuredAuth: FirebaseAuth

    fun initialize() {
        if (initialized) return

        synchronized(this) {
            if (initialized) return
            configureFirebase()
            initialized = true
        }
    }

    fun auth(): FirebaseAuth {
        initialize()
        return configuredAuth
    }

    private fun configureFirebase() {
        if (!BuildConfig.USE_FIREBASE_EMULATORS) {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance(),
            )
            configuredAuth = FirebaseAuth.getInstance()
            return
        }

        val firebaseApp = getOrCreateFirebaseApp()
        configuredAuth = FirebaseAuth.getInstance(firebaseApp).apply {
            useEmulator(
                BuildConfig.FIREBASE_EMULATOR_HOST,
                BuildConfig.FIREBASE_AUTH_EMULATOR_PORT,
            )
        }
        Log.i(
            LOG_TAG,
            "Authentication usa ${BuildConfig.FIREBASE_EMULATOR_HOST}:" +
                BuildConfig.FIREBASE_AUTH_EMULATOR_PORT,
        )

        FirebaseFirestore.getInstance(firebaseApp).apply {
            useEmulator(
                BuildConfig.FIREBASE_EMULATOR_HOST,
                BuildConfig.FIREBASE_FIRESTORE_EMULATOR_PORT,
            )
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(FIRESTORE_CACHE_SIZE_BYTES)
                        .build(),
                )
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
        const val FIRESTORE_CACHE_SIZE_BYTES = 20L * 1024L * 1024L
        const val LOG_TAG = "BobitosFirebase"
    }
}
