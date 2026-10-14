package com.elleng.adbswitch

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeEntryTest {
    init {
        RuntimeEnvironment.getApplication().getSharedPreferences("cfg", Context.MODE_PRIVATE)
            .edit().putString("auth_mode", "shizuku").commit()
    }
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun pageNavigationRemainsAvailableDuringAControllerOperation() {
        val originalIntent = Intent(compose.activity.intent)
        val busy = MainActivity::class.java.getDeclaredField("busy").apply { isAccessible = true }
        try {
            compose.runOnIdle {
                busy.setBoolean(compose.activity, true)
                compose.activity.onNewIntent(Intent(originalIntent).putExtra(MainActivity.EXTRA_SETTINGS, true))
            }
            compose.onNodeWithTag("tab_settings").assertIsSelected()
            compose.runOnIdle {
                compose.activity.onNewIntent(Intent(originalIntent).setAction("com.elleng.adbswitch.action.OPEN_HOME"))
            }
            compose.onNodeWithTag("tab_apps").assertIsSelected()
        } finally {
            compose.runOnIdle {
                busy.setBoolean(compose.activity, false)
                compose.activity.intent = originalIntent
            }
        }
    }

    @Test fun explicitSettingsAndHomeIntentsNavigateAnExistingActivity() {
        val originalIntent = Intent(compose.activity.intent)
        try {
        compose.onNodeWithTag("tab_apps").assertIsSelected()
        compose.runOnIdle {
            compose.activity.onNewIntent(Intent(originalIntent)
                .putExtra(MainActivity.EXTRA_SETTINGS, true))
        }
        compose.onNodeWithTag("tab_settings").assertIsSelected()
        compose.onNodeWithTag("tab_apps").performClick()
        compose.runOnIdle {
            compose.activity.onNewIntent(Intent(originalIntent).putExtra(MainActivity.EXTRA_SETTINGS, true))
        }
        compose.onNodeWithTag("tab_settings").assertIsSelected()
        compose.runOnIdle {
            compose.activity.onNewIntent(Intent(originalIntent)
                .setAction("com.elleng.adbswitch.action.OPEN_HOME"))
        }
        compose.onNodeWithTag("tab_apps").assertIsSelected()
        } finally {
            // ActivityScenario matches lifecycle events against its launch Intent.
            compose.runOnIdle { compose.activity.intent = originalIntent }
        }
    }

    @Test fun recreationDoesNotReplayAnOldSettingsEntry() {
        compose.runOnIdle {
            compose.activity.onNewIntent(Intent(compose.activity.intent)
                .putExtra(MainActivity.EXTRA_SETTINGS, true))
        }
        compose.onNodeWithTag("tab_settings").assertIsSelected()
        compose.onNodeWithTag("tab_apps").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("tab_apps").assertIsSelected()
        compose.runOnIdle {
            compose.activity.onNewIntent(Intent(compose.activity.intent).putExtra(MainActivity.EXTRA_SETTINGS, true))
        }
        compose.onNodeWithTag("tab_settings").assertIsSelected()
    }
}
