package com.elleng.adbswitch.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
class HomeMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun state() = MainUiState().apply {
        authorized = true; known = true; confirmed = true; checkingAuth = false
        apps = listOf(QuickApp("com.example.app0", "应用 0", false))
    }

    @Test fun fixedTitleDoesNotReserveAnEmptyToolbarAboveIt() {
        lateinit var view: android.view.View
        compose.setContent {
            view = LocalView.current
            AdbSwitchTheme { AdbSwitchApp(state(), RecordingActions()) }
        }
        compose.runOnIdle {
            ViewCompat.dispatchApplyWindowInsets(view, WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 48, 0, 0))
                .setVisible(WindowInsetsCompat.Type.statusBars(), true).build())
        }
        val firstRow = compose.onNodeWithTag("app:com.example.app0").getUnclippedBoundsInRoot()
        assertTrue("First row must follow one compact header, not two stacked title rows: " + firstRow.top,
            firstRow.top.value <= 96.5f)
    }

    @Test fun pressingDestinationDoesNotFlashASecondSelection() {
        compose.setContent { AdbSwitchTheme { AdbSwitchApp(state(), RecordingActions()) } }
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag("tab_settings").getUnclippedBoundsInRoot()
        val before = snapshot("nav-before-press")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("tab_settings").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(100)
        val pressed = snapshot("nav-during-press")
        compose.onNodeWithTag("tab_settings").performTouchInput { cancel() }
        var different = 0
        for (y in (bounds.top.value * 2).toInt() until (bounds.bottom.value * 2).toInt()) {
            for (x in (bounds.left.value * 2).toInt() until (bounds.right.value * 2).toInt()) {
                if (before.getPixel(x, y) != pressed.getPixel(x, y)) different++
            }
        }
        assertEquals("Only the moving capsule should indicate selection; no early pressed pill", 0, different)
    }

    @Test fun titleSizeAndStartPositionStayFixedWhileScrolling() {
        compose.setContent { AdbSwitchTheme { AdbSwitchApp(state(), RecordingActions()) } }
        compose.onNodeWithTag("tab_settings").performClick()
        val before = compose.onNodeWithTag("home_title").fetchSemanticsNode().boundsInRoot
        val viewportBefore = compose.onNodeWithTag("settings_list").getUnclippedBoundsInRoot()
        snapshot("fixed-title-before-scroll")
        compose.onNodeWithTag("settings_list").performTouchInput { swipeUp(durationMillis = 300) }
        val after = compose.onNodeWithTag("home_title").fetchSemanticsNode().boundsInRoot
        val viewportAfter = compose.onNodeWithTag("settings_list").getUnclippedBoundsInRoot()
        snapshot("fixed-title-after-scroll")
        assertEquals(before.center.y, after.center.y, 2f)
        assertEquals(viewportBefore.top.value, viewportAfter.top.value, .5f)
        assertEquals("Scrolling must not shrink the fixed title", before.width, after.width, .5f)
        assertEquals("Scrolling must not move the title to the center", before.left, after.left, .5f)
    }

    @Test fun shortDragMovesTheListInsteadOfBeingConsumedByTheHeader() {
        val longList = state().apply {
            apps = (0 until 24).map { QuickApp("com.example.app" + it, "应用 " + it, false) }
        }
        compose.setContent { AdbSwitchTheme { AdbSwitchApp(longList, RecordingActions()) } }
        val before = compose.onNodeWithTag("app:com.example.app0").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("apps_list").performTouchInput {
            swipe(
                start = androidx.compose.ui.geometry.Offset(center.x, center.y + 60f),
                end = center,
                durationMillis = 300,
            )
        }
        val after = compose.onNodeWithTag("app:com.example.app0").getUnclippedBoundsInRoot()
        assertTrue("A short scroll should move list content immediately", after.top.value < before.top.value - 8f)
    }

    @Test fun pageAndIndicatorMoveTogetherAndRapidReversalHasNoBlankFrame() {
        compose.setContent { AdbSwitchTheme { AdbSwitchApp(state(), RecordingActions()) } }
        compose.waitForIdle()
        val homeWidth = compose.onNodeWithTag("home").getUnclippedBoundsInRoot().right.value
        val start = compose.onNodeWithTag("navigation_indicator").getUnclippedBoundsInRoot()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("tab_settings").performSemanticsAction(SemanticsActions.OnClick) { it() }
        // First frame applies recomposition, second frame establishes the animation start time.
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.onNodeWithTag("tab_settings").assertIsSelected()
        compose.mainClock.advanceTimeBy(80)
        compose.waitForIdle()
        val middle = compose.onNodeWithTag("navigation_indicator").getUnclippedBoundsInRoot()
        val indicatorWidth = start.right.value - start.left.value
        val progress = (middle.left.value - start.left.value) / indicatorWidth
        snapshot("navigation-middle")
        assertTrue("The capsule must slide through intermediate positions: start=" + start + ", middle=" + middle, progress in .03f.. .97f)
        val settings = compose.onNodeWithTag("settings_list").getUnclippedBoundsInRoot()
        assertEquals("Page translation and capsule use the same progress", (1f - progress) * homeWidth,
            settings.left.value, 2f)
        snapshot("navigation-middle")
        compose.onNodeWithTag("tab_apps").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(32)
        // Both layers must remain during reversal even when target == transition's original state.
        compose.onNodeWithTag("settings_list").assertExists()
        compose.onNodeWithTag("apps_list").assertExists()
        snapshot("navigation-reversed")
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("settings_list").assertDoesNotExist()
        val end = compose.onNodeWithTag("navigation_indicator").getUnclippedBoundsInRoot()
        assertEquals(start.left.value, end.left.value, .5f)
        compose.onNodeWithText("应用 0").assertIsDisplayed()
    }

    @Test fun renderRedrawnVectorIconsAtActualAndInspectionSize() {
        compose.setContent {
            AdbSwitchTheme {
                androidx.compose.foundation.layout.Row(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize()
                        .background(top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surface),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    listOf(com.elleng.adbswitch.R.drawable.ic_apps, com.elleng.adbswitch.R.drawable.ic_settings).forEach { icon ->
                        androidx.compose.foundation.layout.Column(
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(32.dp),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        ) {
                            top.yukonga.miuix.kmp.basic.Icon(androidx.compose.ui.res.painterResource(icon), null,
                                androidx.compose.ui.Modifier.size(96.dp))
                            top.yukonga.miuix.kmp.basic.Icon(androidx.compose.ui.res.painterResource(icon), null,
                                androidx.compose.ui.Modifier.size(23.dp))
                        }
                    }
                }
            }
        }
        snapshot("redrawn-icons")
    }

    @Test fun rightToLeftSelectionStaysWithinCapsule() {
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl,
            ) { AdbSwitchTheme { AdbSwitchApp(state(), RecordingActions()) } }
        }
        compose.onNodeWithTag("tab_settings").performClick()
        val bar = compose.onNodeWithTag("glass_navigation").getUnclippedBoundsInRoot()
        val indicator = compose.onNodeWithTag("navigation_indicator").getUnclippedBoundsInRoot()
        assertTrue("RTL selected capsule must remain inside the bar", indicator.left >= bar.left && indicator.right <= bar.right)
    }

    @Test fun rightToLeftFixedTitleRemainsStartAlignedAfterScroll() {
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl,
            ) { AdbSwitchTheme { AdbSwitchApp(state(), RecordingActions()) } }
        }
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("settings_list").performTouchInput { swipeUp(durationMillis = 300) }
        val root = compose.onNodeWithTag("home").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithTag("home_title").fetchSemanticsNode().boundsInRoot
        assertEquals("Fixed RTL title must keep its 28dp start margin", 56f, root.right - title.right, 2f)
    }

    @Test fun sideNavigationInsetIsExcludedFromFixedTitleStartMargin() {
        lateinit var view: android.view.View
        compose.setContent {
            view = LocalView.current
            AdbSwitchTheme { AdbSwitchApp(state(), RecordingActions()) }
        }
        compose.runOnIdle {
            ViewCompat.dispatchApplyWindowInsets(view, WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(96, 0, 0, 0))
                .setVisible(WindowInsetsCompat.Type.navigationBars(), true).build())
        }
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("settings_list").performTouchInput { swipeUp(durationMillis = 300) }
        val content = compose.onNodeWithTag("settings_list").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithTag("home_title").fetchSemanticsNode().boundsInRoot
        assertEquals("Fixed title must start inside the same safe area as content", 56f, title.left - content.left, 2f)
    }

    private fun snapshot(name: String): Bitmap {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        val path = File("build/outputs/miuix-screenshots/" + name + ".png")
        path.parentFile!!.mkdirs()
        path.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }
}
