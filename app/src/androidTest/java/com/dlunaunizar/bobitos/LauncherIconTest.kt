package com.dlunaunizar.bobitos

import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherIconTest {

    private fun appIcon(): Drawable {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.packageManager.getApplicationIcon(context.packageName)
    }

    @Test
    fun appIconIsAdaptive() {
        assertTrue(appIcon() is AdaptiveIconDrawable)
    }

    @Test
    fun appIconBackgroundIsCream() {
        val background = (appIcon() as AdaptiveIconDrawable).background
        assertEquals(0xFFFBFAF8.toInt(), (background as ColorDrawable).color)
    }

    @Test
    fun appIconHasMonochromeLayer() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        assertNotNull((appIcon() as AdaptiveIconDrawable).monochrome)
    }
}
