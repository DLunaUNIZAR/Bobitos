package com.dlunaunizar.bobitos.data.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Publica una notificación local a partir de los datos de entrada. No inyecta nada. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: return Result.success()
        val text = inputData.getString(KEY_TEXT).orEmpty()
        val notificationId = inputData.getInt(KEY_NOTIFICATION_ID, title.hashCode())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val launch = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        val pending = launch?.let {
            PendingIntent.getActivity(applicationContext, notificationId, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "bobitos_reminders"
        const val TAG = "bobitos-reminder"
        const val KEY_TITLE = "title"
        const val KEY_TEXT = "text"
        const val KEY_NOTIFICATION_ID = "notificationId"
    }
}
