package com.elleng.adbswitch.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.elleng.adbswitch.R
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun QuickAppsScreen(
    state: MainUiState, actions: UiActions, listState: LazyListState,
    bottomClearance: Dp,
    onOptions: (String) -> Unit, onSettings: () -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("apps_list")
            .scrollEdgeFade(listState, MiuixTheme.colorScheme.surface),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.apps.isEmpty()) item {
            Card {
                BasicComponent(title = stringResource(R.string.linkage_apps_empty),
                    summary = stringResource(R.string.apps_empty_guidance),
                    bottomAction = {
                        TextButton(stringResource(R.string.open_settings_page), onSettings,
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            colors = ButtonDefaults.textButtonColorsPrimary())
                    })
            }
        }
        items(state.apps, key = { it.packageName }) { app ->
            Card {
                BasicComponent(
                    title = app.label,
                    summary = stringResource(if (app.closeRear) R.string.quick_app_summary_rear else R.string.quick_app_summary),
                    modifier = Modifier.testTag("app:" + app.packageName).combinedClickable(
                        enabled = !state.busy,
                        onClick = { actions.launchApp(app.packageName) },
                        onLongClick = { onOptions(app.packageName) },
                        onLongClickLabel = stringResource(R.string.quick_options_title),
                    ),
                    startAction = { AppIcon(app.packageName) },
                    endActions = {
                        IconButton(onClick = { onOptions(app.packageName) }, enabled = !state.busy,
                            modifier = Modifier.testTag("options:" + app.packageName)) {
                            Icon(painterResource(R.drawable.ic_settings),
                                stringResource(R.string.app_options_description, app.label), Modifier.size(22.dp),
                                tint = MiuixTheme.colorScheme.onSurfaceVariantActions)
                        }
                    },
                )
            }
        }
    }
}
