package com.elleng.adbswitch.ui

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import java.util.UUID

@Composable
fun AdbSwitchTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val view = LocalView.current
    val colors = if (dark) darkColorScheme() else lightColorScheme()
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(colors.surface.toArgb()))
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MiuixTheme(colors = colors, content = content)
}

/** Small Java/Compose boundary: only snapshots and user actions cross it. */
class MiuixHost(activity: ComponentActivity, actions: UiActions, initialThemeMode: ThemeMode) {
    private var state by mutableStateOf(MainUiState().apply { themeMode = initialThemeMode })
    private var notificationPrompt by mutableStateOf(false)
    private var navigationRequest by mutableStateOf<HomeNavigationRequest?>(null)

    init {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        activity.setContent {
            AdbSwitchTheme(dark = state.themeMode.isDark(isSystemInDarkTheme())) {
                AdbSwitchApp(state, actions, notificationPrompt,
                    dismissNotificationPrompt = { notificationPrompt = false }, navigationRequest = navigationRequest)
            }
        }
    }
    fun update(snapshot: MainUiState) { state = snapshot }
    fun showNotificationPrompt() { notificationPrompt = true }
    fun showSettingsPage() { navigationRequest = HomeNavigationRequest(UUID.randomUUID().toString(), HomeTab.Settings) }
    fun showAppsPage() { navigationRequest = HomeNavigationRequest(UUID.randomUUID().toString(), HomeTab.Apps) }
}
