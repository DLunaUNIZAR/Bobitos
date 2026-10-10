package com.dlunaunizar.bobitos

import android.app.Application
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.dlunaunizar.bobitos.data.firebase.FirebaseInitializer
import com.dlunaunizar.bobitos.data.reminders.ReminderWorker
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import javax.inject.Inject

@HiltAndroidApp
class BobitosApplication :
    Application(),
    SingletonImageLoader.Factory {
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

    // Cargador de imágenes único: caché de disco acotada y User-Agent identificable para wger.de.
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val userAgent = "Bobitos/${BuildConfig.VERSION_NAME} (+https://github.com/DLunaUNIZAR/Bobitos)"
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
            }
            .build()
        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(IMAGE_CACHE_BYTES)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    private companion object {
        const val IMAGE_CACHE_BYTES = 50L * 1024 * 1024
    }
}
