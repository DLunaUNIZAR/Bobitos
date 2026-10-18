package com.dlunaunizar.bobitos

import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherIconTest {

    private fun appIcon(): Drawable {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // El recurso del android:icon del manifiesto: getApplicationIcon puede venir envuelto por el fabricante.
        return checkNotNull(ContextCompat.getDrawable(context, R.mipmap.ic_launcher)) { "Falta R.mipmap.ic_launcher" }
    }

    @Test
    fun appIconIsAdaptive() {
        assertTrue(appIcon() is AdaptiveIconDrawable)
    }

    @Test
    fun appIconBackgroundIsCream() {
        val icon = appIcon() as? AdaptiveIconDrawable
        assertNotNull("El icono no es adaptativo", icon)
        val background = icon!!.background as? ColorDrawable
        assertNotNull("El fondo del icono no es un ColorDrawable", background)
        assertEquals(0xFFFBFAF8.toInt(), background!!.color)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    fun appIconHasMonochromeLayer() {
        val icon = appIcon() as? AdaptiveIconDrawable
        assertNotNull("El icono no es adaptativo", icon)
        assertNotNull("Falta la capa monocroma", icon!!.monochrome)
    }
}
