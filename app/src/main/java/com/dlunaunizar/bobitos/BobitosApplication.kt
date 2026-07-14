package com.dlunaunizar.bobitos

import android.app.Application
import com.dlunaunizar.bobitos.data.firebase.FirebaseInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BobitosApplication : Application() {
    @Inject
    lateinit var firebaseInitializer: FirebaseInitializer

    override fun onCreate() {
        super.onCreate()
        firebaseInitializer.initialize()
    }
}
