package com.elleng.adbswitch.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MiuixScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val actions = RecordingActions()
    private fun ready() = MainUiState().apply {
        authorized = true; confirmed = true; known = true; checkingAuth = false
        authMode = "Shizuku"; buildTime = "2026-10-14 12:00:00"
        packageName = "com.yuhao7370.adbswitch"
        apps = listOf(
            QuickApp("cn.com.hsbc.hsbcchina", "汇丰银行", false),
            QuickApp("hk.com.hsbc.hsbchkmobile", "香港汇丰流动理财", true))
        defaultPackage = apps.first().packageName
    }
    private fun show(state: MainUiState = ready(), dark: Boolean = false, settings: Boolean = true) {
        compose.setContent { AdbSwitchTheme(dark) { AdbSwitchApp(state, actions) } }
        if (settings) compose.onNodeWithTag("tab_settings").performClick()
    }
    @Test fun unconfirmedStateCannotWrite() {
        show(ready().apply { confirmed = false })
        compose.onNodeWithTag("master").performClick()
        assertNull(actions.lastToggle)
    }
    @Test fun masterDispatchesOnceAndWaitsForControllerState() {
        show()
        compose.onNodeWithTag("master").performClick()
        assertEquals(UiActions.Toggle.MASTER to true, actions.lastToggle)
        assertEquals(1, actions.toggleCount)
        compose.onNodeWithText("已关闭 · 可通过检测").assertExists()
    }
    @Test fun authorizationRemainsAccessibleWithoutPrivilege() {
        show(MainUiState().apply { checkingAuth = false })
        compose.onNodeWithTag("auth").performClick()
        compose.onNodeWithText("Shizuku", useUnmergedTree = true).performClick()
        assertEquals("Shizuku", actions.authMode)
    }
    @Test fun quickOptionsDoesNotLaunchAndChangesOnlyChosenApp() {
        show(settings = false)
        compose.onNodeWithTag("options:hk.com.hsbc.hsbchkmobile").performScrollTo().performClick()
        assertNull(actions.launched)
        compose.onNodeWithText("临时关闭背屏").performClick()
        assertEquals("hk.com.hsbc.hsbchkmobile" to false, actions.rear)
        capture("quick-options-dialog-light")
    }
    @Test fun aboutHasNoCreditsAndUpdatesStayDisabled() {
        show()
        compose.onNodeWithTag("settings_list").performScrollToKey("about")
        compose.onNodeWithTag("about").performClick()
        compose.onNodeWithText("@伦Ellen").assertDoesNotExist()
        compose.onNodeWithText("检查更新已停用").assertExists()
        capture("about-light")
    }
    @Test fun renderLightHome() { show(settings = false); capture("apps-light") }
    @Test fun renderDarkApps() { show(dark = true, settings = false); capture("apps-dark") }
    @Test fun renderLightSettings() { show(); capture("settings-light") }
    @Test fun renderAuthorizationDark() {
        show(MainUiState().apply { checkingAuth = false; authMode = "Shizuku"; authNotice = "请先在 Shizuku 应用中启动服务" }, dark = true)
        compose.onNodeWithTag("settings_list").performScrollToKey("debug")
        compose.onNodeWithTag("auth").performClick()
        capture("authorization-dark")
    }
    @Test fun defaultPickerDoesNotSaveUntilConfirmed() {
        show()
        compose.onNodeWithTag("settings_list").performScrollToKey("quick_settings")
        compose.onNodeWithText("默认快捷应用").performClick()
        compose.onNodeWithText("取消").performClick()
        assertNull(actions.chosenDefault)
    }
    @Test @Config(qualifiers = "zh-rCN-w891dp-h411dp-land-xhdpi")
    fun landscapeAboutCanScrollToDone() {
        show(dark = true)
        compose.onNodeWithTag("settings_list").performScrollToKey("about")
        compose.onNodeWithTag("about").performClick()
        compose.onNodeWithText("确定").performScrollTo().assertIsDisplayed()
        capture("about-landscape-dark")
    }
    @Test fun renderDarkPartialAndRecovery() {
        show(ready().apply { usb = true; rearPending = true }, dark = true)
        compose.onNodeWithText("部分开启 · adbd 仍在运行").assertExists()
        capture("settings-dark-recovery")
    }
    @Test @Config(qualifiers = "zh-rCN-w320dp-h700dp-xhdpi")
    fun narrowLargeTextStillReachesAbout() {
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(2f, 1.5f)
            ) { AdbSwitchTheme(false) { AdbSwitchApp(ready(), actions) } }
        }
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("settings_list").performScrollToKey("about")
        compose.onNodeWithTag("about").assertIsDisplayed()
        capture("settings-large-text")
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        // PixelCopy waits for a real hardware frame. Use Android's native Canvas on Robolectric.
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        val file = File("build/outputs/miuix-screenshots/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

internal class RecordingActions : UiActions {
    var lastToggle: Pair<UiActions.Toggle, Boolean>? = null
    var toggleCount = 0
    var authMode: String? = null
    var launched: String? = null
    var rear: Pair<String, Boolean>? = null
    var saved: List<String>? = null
    var chosenDefault: String? = null
    override fun setThemeMode(mode: ThemeMode) {}
    override fun toggle(toggle: UiActions.Toggle, enabled: Boolean) { lastToggle = toggle to enabled; toggleCount++ }
    override fun selectAuth(mode: String) { authMode = mode }
    override fun retryAuth() {}
    override fun saveApps(packages: List<String>) { saved = packages }
    override fun setDefaultApp(packageName: String) { chosenDefault = packageName }
    override fun setCloseRear(packageName: String, enabled: Boolean) { rear = packageName to enabled }
    override fun launchApp(packageName: String) { launched = packageName }
    override fun createShortcut(packageName: String) {}
    override fun restoreRearDisplay() {}
    override fun testNotification() {}
    override fun openNotificationSettings() {}
    override fun openRepository() {}
}
