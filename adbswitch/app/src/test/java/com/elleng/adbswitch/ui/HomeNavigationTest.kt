package com.elleng.adbswitch.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.graphics.Insets
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val actions = RecordingActions()
    private fun ready(count: Int = 2) = MainUiState().apply {
        authorized = true; confirmed = true; known = true; checkingAuth = false
        apps = (0 until count).map { QuickApp("com.example.app$it", "应用 $it", it == 1) }
        defaultPackage = apps.first().packageName
    }

    @Test fun firstPageContainsOnlySelectedApplications() {
        compose.setContent { AdbSwitchTheme { AdbSwitchApp(ready(), actions) } }
        compose.onNodeWithText("应用 0").assertIsDisplayed()
        compose.onNodeWithText("应用 1").assertIsDisplayed()
        compose.onNodeWithTag("master").assertDoesNotExist()
        compose.onNodeWithTag("manage_apps").assertDoesNotExist()
        compose.onNodeWithTag("tab_apps").assertIsSelected()
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("master").assertExists()
        compose.onNodeWithTag("app:com.example.app0").assertDoesNotExist()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("manage_apps"))
        compose.onNodeWithTag("manage_apps").assertIsDisplayed()
    }

    @Test fun tabsAndBothScrollPositionsSurviveRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { AdbSwitchTheme { AdbSwitchApp(ready(24), actions) } }
        compose.onNodeWithTag("apps_list").performScrollToNode(hasText("应用 18"))
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("settings_list").performScrollToKey("about")
        compose.onNodeWithTag("tab_apps").performClick()
        compose.onNodeWithText("应用 18").assertIsDisplayed()
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("about").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("tab_settings").assertIsSelected()
        compose.onNodeWithTag("about").assertIsDisplayed()
        compose.onNodeWithTag("tab_apps").performClick()
        compose.onNodeWithText("应用 18").assertIsDisplayed()
    }

    @Test fun appOptionsFloatAndDismissWithoutLaunching() {
        compose.setContent { AdbSwitchTheme { AdbSwitchApp(ready(), actions) } }
        compose.onNodeWithTag("options:com.example.app1").performClick()
        compose.onNodeWithTag("quick_app_dialog").assertIsDisplayed()
        assertNull(actions.launched)
        compose.onNodeWithText("临时关闭背屏").performClick()
        assertEquals("com.example.app1" to false, actions.rear)
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithTag("quick_app_dialog").assertDoesNotExist()
        compose.onNodeWithTag("tab_apps").assertIsSelected()
    }

    @Test fun pendingRecoveryOnlyAppearsAtTopOfSettings() {
        compose.setContent { AdbSwitchTheme { AdbSwitchApp(ready().apply { rearPending = true }, actions) } }
        compose.onNodeWithText("仅恢复背屏").assertDoesNotExist()
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithText("仅恢复背屏").assertIsDisplayed()
    }

    @Test fun navigationAvoidsSystemInsetAndLastRowCanScrollAboveIt() {
        lateinit var view: android.view.View
        compose.setContent {
            view = LocalView.current
            AdbSwitchTheme { AdbSwitchApp(ready(), actions) }
        }
        compose.runOnIdle {
            ViewCompat.dispatchApplyWindowInsets(view, WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 96))
                .setVisible(WindowInsetsCompat.Type.navigationBars(), true).build())
        }
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("settings_list").performScrollToKey("about")
        val root = compose.onNodeWithTag("home").getUnclippedBoundsInRoot()
        val navigation = compose.onNodeWithTag("glass_navigation").getUnclippedBoundsInRoot()
        val about = compose.onNodeWithTag("about").getUnclippedBoundsInRoot()
        assertTrue(navigation.bottom.value <= root.bottom.value - 48f)
        assertTrue("The last row must be accessible above the capsule", about.bottom <= navigation.top)
    }

    @Test fun backClosesDialogThenPickerThenReturnsToApps() {
        compose.setContent { AdbSwitchTheme { AdbSwitchApp(ready(), actions) } }
        compose.onNodeWithTag("options:com.example.app1").performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("quick_app_dialog").assertDoesNotExist()
        compose.onNodeWithTag("tab_apps").assertIsSelected()
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("settings_list").performScrollToKey("quick_settings")
        compose.onNodeWithTag("manage_apps").performClick()
        compose.onNodeWithTag("glass_navigation").assertDoesNotExist()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("tab_settings").assertIsSelected()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("tab_apps").assertIsSelected()
    }

    @Test @Config(sdk = [27])
    fun oldestSupportedAndroidStillHasUsableNavigation() {
        compose.setContent { AdbSwitchTheme { AdbSwitchApp(ready(), actions) } }
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("master").assertIsDisplayed()
        compose.onNodeWithTag("tab_apps").performClick()
        compose.onNodeWithText("应用 0").assertIsDisplayed()
    }
}
