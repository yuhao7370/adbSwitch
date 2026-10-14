package com.elleng.adbswitch.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.graphics.Insets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppPickerTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val apps = listOf(InstalledApp("com.bank.first", "银行应用"), InstalledApp("com.example.notes", "备忘录"))
    private var saved: List<String>? = null
    private var cancelled = false

    @Test fun keyboardLeavesSearchResultsVisible() {
        lateinit var view: android.view.View
        var imeBottom = 0
        compose.setContent {
            view = LocalView.current
            imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
            AdbSwitchTheme { AppPickerContent(apps, emptyList(), false, false, {}, {}, {}) }
        }
        compose.runOnIdle {
            ViewCompat.dispatchApplyWindowInsets(view, WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 700))
                .setVisible(WindowInsetsCompat.Type.ime(), true).build())
        }
        compose.waitForIdle()
        assertEquals(700, imeBottom)
        val bounds = compose.onNodeWithTag("picker_list").getUnclippedBoundsInRoot()
        assertTrue("The keyboard must only be avoided once", (bounds.bottom - bounds.top).value > 180f)
        compose.onNodeWithTag("picker:com.bank.first").assertIsDisplayed().performClick()
    }

    @Test fun searchAndRefreshDoNotLoseSelectionAndSaveKeepsOrder() {
        val restore = StateRestorationTester(compose)
        restore.setContent {
            AdbSwitchTheme {
                AppPickerContent(apps, listOf("com.bank.first"), false, false, {},
                    { cancelled = true }, { saved = it })
            }
        }
        compose.onNodeWithTag("picker_search").performTextInput("notes")
        compose.onNodeWithTag("picker:com.example.notes").performClick()
        compose.onNodeWithText("已选择 2 个应用").assertExists()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("picker_search").assertTextContains("notes")
        compose.onNodeWithText("已选择 2 个应用").assertExists()
        compose.onNodeWithTag("picker_save").performClick()
        assertEquals(listOf("com.bank.first", "com.example.notes"), saved)
        assertFalse(cancelled)
    }

    @Test fun cancellingSelectionDoesNotSave() {
        compose.setContent {
            AdbSwitchTheme {
                AppPickerContent(apps, listOf("com.bank.first"), false, false, {},
                    { cancelled = true }, { saved = it })
            }
        }
        compose.onNodeWithTag("picker:com.bank.first").performClick()
        compose.onNodeWithText("取消").performClick()
        assertTrue(cancelled)
        assertNull(saved)
    }

    @Test fun loadingFailureCannotOverwriteSavedApps() {
        compose.setContent {
            AdbSwitchTheme {
                AppPickerContent(emptyList(), listOf("com.bank.first"), false, true, {}, {}, { saved = it })
            }
        }
        compose.onNodeWithTag("picker_save").assertIsNotEnabled()
        assertNull(saved)
    }

    @Test fun renderPickerDark() {
        compose.setContent {
            AdbSwitchTheme(true) { AppPickerContent(apps, listOf("com.bank.first"), false, false, {}, {}, {}) }
        }
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        val file = File("build/outputs/miuix-screenshots/picker-dark.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
