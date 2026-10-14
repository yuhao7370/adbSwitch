package com.elleng.adbswitch

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import android.content.pm.PackageManager
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import com.elleng.adbswitch.ui.UiActions
import rikka.shizuku.Shizuku

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityTest {
    @Test fun pausingKeepsAnObservedExternalChangeScheduled() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("cfg", Context.MODE_PRIVATE).edit().putString("auth_mode", "shizuku").commit()
        val controller = Robolectric.buildActivity(MainActivity::class.java,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_SETTINGS, true)).setup()
        val activity = controller.get()
        fun field(name: String) = MainActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
        val observer = field("settingsObserver").get(activity) as android.database.ContentObserver
        val handler = field("mainHandler").get(activity) as android.os.Handler
        val resync = field("resyncRunnable").get(activity) as Runnable
        observer.onChange(false)
        controller.pause()
        try { assertTrue("A pending external state change must still be calibrated", handler.hasCallbacks(resync)) }
        finally { controller.stop().destroy() }
    }

    @Test fun successfulCloseStillStartsRecoveryNotificationAfterActivityDestroyed() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("cfg", Context.MODE_PRIVATE).edit().putString("auth_mode", "shizuku").commit()
        val controller = Robolectric.buildActivity(MainActivity::class.java,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_SETTINGS, true)).setup()
        val activity = controller.get()
        controller.pause().stop().destroy()
        activity.onDebugWriteCompleted(UiActions.Toggle.MASTER, false, true, DebugController.State(false, false, false))
        val service = shadowOf(context).nextStartedService
        assertEquals("A completed close must retain its recovery notification", AdbToggleService::class.java.name, service?.component?.className)
    }

    @Test fun permissionEventRestartsAnOlderInFlightProbe() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("cfg", Context.MODE_PRIVATE).edit().putString("auth_mode", "shizuku").commit()
        val controller = Robolectric.buildActivity(MainActivity::class.java,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_SETTINGS, true)).setup()
        val activity = controller.get()
        try {
            fun field(name: String) = MainActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
            field("checkingAuth").setBoolean(activity, true)
            field("authCheckedThisSession").setBoolean(activity, false)
            val generation = field("authGeneration").getInt(activity)
            val listener = field("shizukuPermListener").get(activity) as Shizuku.OnRequestPermissionResultListener
            listener.onRequestPermissionResult(AuthManager.SHIZUKU_PERM_REQUEST, PackageManager.PERMISSION_GRANTED)
            assertTrue("A newer permission event must not be dropped behind an old failed probe",
                field("authGeneration").getInt(activity) > generation)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun firstHomeOpenPublishesLauncherShortcutsWithoutChangingDebug() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("cfg", Context.MODE_PRIVATE).edit().putString("auth_mode", "shizuku").commit()
        val intent = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_SETTINGS, true)
        val controller = Robolectric.buildActivity(MainActivity::class.java, intent).setup()
        try {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            val ids = context.getSystemService(ShortcutManager::class.java).dynamicShortcuts.map { it.id }
            assertTrue("Opening the home screen must publish the launcher menu", ids.contains("toggle_master"))
            assertTrue(ids.contains("toggle_usb"))
            assertTrue(ids.contains("toggle_wireless"))
        } finally { controller.pause().stop().destroy() }
    }
}
