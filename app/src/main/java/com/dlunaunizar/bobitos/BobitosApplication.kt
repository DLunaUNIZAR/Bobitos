package com.dlunaunizar.bobitos

import android.app.Application
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.dlunaunizar.bobitos.data.firebase.FirebaseInitializer
import com.dlunaunizar.bobitos.data.reminders.ReminderWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BobitosApplication : Application() {
    @Inject
    lateinit var firebaseInitializer: FirebaseInitializer

    override fun onCreate() {
        super.onCreate()
        firebaseInitializer.initialize()
        createReminderChannel()
    }

    private fun createReminderChannel() {
        val channel = NotificationChannelCompat.Builder(
            ReminderWorker.CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_DEFAULT,
        ).setName(getString(R.string.reminder_channel_name)).build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }
}
