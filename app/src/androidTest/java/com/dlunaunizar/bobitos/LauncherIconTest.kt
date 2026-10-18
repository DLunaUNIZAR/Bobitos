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
        // El recurso del android:icon del manifiesto (applicationInfo.icon): getApplicationIcon puede venir envuelto por el fabricante.
        val icon = ContextCompat.getDrawable(context, context.applicationInfo.icon)
        return checkNotNull(icon) { "Falta el icono del manifiesto" }
    }

    private fun adaptiveIcon(): AdaptiveIconDrawable {
        val icon = appIcon() as? AdaptiveIconDrawable
        assertNotNull("El icono no es adaptativo", icon)
        return icon as AdaptiveIconDrawable
    }

    @Test
    fun appIconIsAdaptive() {
        assertTrue(appIcon() is AdaptiveIconDrawable)
    }

    @Test
    fun appIconBackgroundIsCream() {
        val background = adaptiveIcon().background as? ColorDrawable
        assertNotNull("El fondo del icono no es un ColorDrawable", background)
        assertEquals(0xFFFBFAF8.toInt(), background!!.color)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    fun appIconHasMonochromeLayer() {
        assertNotNull("Falta la capa monocroma", adaptiveIcon().monochrome)
    }
}
