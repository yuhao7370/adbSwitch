package com.elleng.adbswitch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-notnight-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeModeTest {
    init {
        RuntimeEnvironment.getApplication().getSharedPreferences("cfg", Context.MODE_PRIVATE)
            .edit().putString("auth_mode", "shizuku").remove("theme_mode").commit()
    }
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun darkChoiceAppliesImmediatelyAndSurvivesRecreation() {
        val originalActivity = compose.activity
        compose.onNodeWithTag("tab_settings").performClick()
        chooseTheme("dark")
        assertSame("Theme changes must not recreate an active controller screen", originalActivity, compose.activity)
        assertTheme(dark = true, name = "theme-explicit-dark")
        assertEquals("dark", savedTheme())
        compose.onNodeWithTag("tab_settings").assertIsSelected()
        compose.activityRule.scenario.recreate()
        assertTheme(dark = true, name = "theme-dark-restored")
        compose.onNodeWithTag("tab_settings").assertIsSelected()
        compose.onNodeWithTag("theme_mode").assertTextContains("深色")
    }

    @Test @Config(qualifiers = "zh-rCN-w411dp-h891dp-night-xhdpi")
    fun lightOverrideAndFollowSystemAreDistinctModes() {
        assertTheme(dark = true, name = "theme-system-night")
        compose.onNodeWithTag("tab_settings").performClick()
        chooseTheme("light")
        assertTheme(dark = false, name = "theme-explicit-light")
        assertEquals("light", savedTheme())
        chooseTheme("system")
        assertTheme(dark = true, name = "theme-follow-system-night")
        assertEquals("system", savedTheme())

        RuntimeEnvironment.setQualifiers("+notnight")
        compose.activityRule.scenario.recreate()
        assertTheme(dark = false, name = "theme-follow-system-day")
        compose.onNodeWithTag("theme_mode").assertTextContains("跟随系统")
    }

    private fun chooseTheme(mode: String) {
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("theme_mode"))
        compose.onNodeWithTag("theme_mode").performClick()
        compose.onNodeWithTag("theme_option_" + mode).performClick()
        compose.waitForIdle()
    }

    private fun savedTheme() = compose.activity.getSharedPreferences("cfg", Context.MODE_PRIVATE)
        .getString("theme_mode", null)

    private fun assertTheme(dark: Boolean, name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        val surfaceBrightness = Color.red(bitmap.getPixel(2, bitmap.height / 2))
        assertTrue("The rendered page must use the selected theme: brightness=" + surfaceBrightness,
            if (dark) surfaceBrightness < 64 else surfaceBrightness > 220)
        val bars = WindowCompat.getInsetsController(compose.activity.window, view)
        assertEquals("Status-bar icons must contrast with the app theme", !dark, bars.isAppearanceLightStatusBars)
        assertEquals("Navigation-bar icons must contrast with the app theme", !dark, bars.isAppearanceLightNavigationBars)
        val output = File("build/outputs/miuix-screenshots/" + name + ".png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
