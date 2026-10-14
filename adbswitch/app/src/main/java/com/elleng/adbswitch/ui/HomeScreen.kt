package com.elleng.adbswitch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import top.yukonga.miuix.kmp.basic.Scaffold

@Composable
fun AdbSwitchApp(
    state: MainUiState,
    actions: UiActions,
    notificationPrompt: Boolean = false,
    dismissNotificationPrompt: () -> Unit = {},
    navigationRequest: HomeNavigationRequest? = null,
) {
    var selectedTab by rememberSaveable { mutableStateOf(navigationRequest?.tab ?: HomeTab.Apps) }
    val pageTransition = updateTransition(selectedTab, label = "home_navigation")
    val pagePosition = pageTransition.animateFloat(
        transitionSpec = { tween(300, easing = CubicBezierEasing(.2f, 0f, .2f, 1f)) },
        label = "page_position",
    ) { it.ordinal.toFloat() }
    val visibleTabs = if (pageTransition.isRunning || pageTransition.currentState != pageTransition.targetState) {
        HomeTab.entries.toList()
    } else listOf(selectedTab)
    var handledRequest by rememberSaveable { mutableStateOf<String?>(null) }
    var picker by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf("") }
    var optionsPackage by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable(state.anyOn) { mutableStateOf(state.anyOn) }
    // Keep these alive while another page, selector or popup is on screen.
    val appsList = rememberLazyListState()
    val settingsList = rememberLazyListState()
    val hazeState = rememberHazeState()
    val density = LocalDensity.current
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1f else -1f
    var navigationHeight by remember { mutableStateOf(76.dp) }
    val selectedApp = state.apps.firstOrNull { it.packageName == optionsPackage }

    LaunchedEffect(navigationRequest?.id) {
        if (navigationRequest != null && navigationRequest.id != handledRequest) {
            handledRequest = navigationRequest.id
            selectedTab = navigationRequest.tab
            picker = false
            dialog = ""
            optionsPackage = ""
            dismissNotificationPrompt()
        }
    }
    LaunchedEffect(optionsPackage, selectedApp) {
        if (optionsPackage.isNotEmpty() && selectedApp == null) optionsPackage = ""
    }
    if (picker) {
        AppPickerScreen(
            initialSelection = state.apps.map { it.packageName },
            onCancel = { picker = false },
            onConfirm = { actions.saveApps(it); picker = false },
        )
        return
    }
    BackHandler(selectedTab == HomeTab.Settings && dialog.isEmpty() && selectedApp == null && !notificationPrompt) {
        selectedTab = HomeTab.Apps
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("home"),
        topBar = {
            HomeTopBar(visibleTabs, pagePosition)
        },
        floatingToolbar = {
            GlassNavigationBar(selectedTab, { selectedTab = it }, pagePosition, hazeState,
                Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                    .onSizeChanged { navigationHeight = with(density) { it.height.toDp() } })
        },
    ) { insets ->
        // The source excludes navigation and the popup host, avoiding recursive blur capture.
        Box(Modifier.fillMaxSize().hazeSource(hazeState).padding(insets).consumeWindowInsets(insets),
            contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 640.dp).fillMaxSize().clipToBounds()) {
                visibleTabs.forEach { tab ->
                    key(tab) {
                        Box(Modifier.fillMaxSize().graphicsLayer {
                            translationX = (tab.ordinal - pagePosition.value) * size.width * direction
                        }) {
                            if (tab == HomeTab.Apps) {
                                QuickAppsScreen(state, actions, appsList, navigationHeight + 28.dp,
                                    onOptions = { optionsPackage = it }, onSettings = { selectedTab = HomeTab.Settings })
                            } else {
                                SettingsScreen(state, actions, settingsList, navigationHeight + 28.dp,
                                    expanded, onExpandedChange = { expanded = !expanded },
                                    onDialog = { dialog = it }, onPicker = { picker = true })
                            }
                        }
                    }
                }
            }
        }
        AppDialogs(dialog, state, actions, onDismiss = { dialog = "" })
        QuickAppDialog(selectedApp, actions, enabled = !state.busy) { optionsPackage = "" }
        NotificationDialog(notificationPrompt, actions, dismissNotificationPrompt)
    }
}
