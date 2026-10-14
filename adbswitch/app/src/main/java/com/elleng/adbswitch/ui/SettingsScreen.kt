package com.elleng.adbswitch.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.elleng.adbswitch.BuildConfig
import com.elleng.adbswitch.R
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SettingsScreen(
    state: MainUiState, actions: UiActions, listState: LazyListState,
    bottomClearance: Dp, expanded: Boolean, onExpandedChange: () -> Unit,
    onDialog: (String) -> Unit, onPicker: () -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("settings_list")
            .scrollEdgeFade(listState, MiuixTheme.colorScheme.surface),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.rearPending) item(key = "recovery") {
            Card {
                BasicComponent(title = stringResource(R.string.rear_notify_title), summary = stringResource(R.string.rear_restore_summary),
                    bottomAction = {
                        TextButton(stringResource(R.string.rear_restore_only), actions::restoreRearDisplay,
                            enabled = state.authorized && !state.busy, modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            colors = ButtonDefaults.textButtonColorsPrimary())
                    })
            }
        }
        if (!state.authorized) item(key = "authorization_notice") {
            Card(colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)) {
                BasicComponent(
                    title = stringResource(if (state.checkingAuth) R.string.auth_checking else R.string.auth_need_grant),
                    summary = state.authNotice.ifEmpty { stringResource(R.string.auth_setup_summary) },
                    bottomAction = {
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(stringResource(R.string.auth_mode_title), onClick = { onDialog("auth") }, modifier = Modifier.weight(1f))
                            TextButton(stringResource(R.string.auth_retry), onClick = actions::retryAuth,
                                enabled = !state.checkingAuth, modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.textButtonColorsPrimary())
                        }
                    },
                )
            }
        }
        item(key = "debug_heading") { SectionTitle(stringResource(R.string.debug_auth_section)) }
        item(key = "debug") {
            Card {
                SuperSwitch(title = stringResource(R.string.master_title), checked = state.anyOn,
                    summary = masterSummary(state), onCheckedChange = { actions.toggle(UiActions.Toggle.MASTER, it) },
                    enabled = state.canWrite, modifier = Modifier.testTag("master"))
                BasicComponent(title = stringResource(R.string.debug_details), onClick = onExpandedChange,
                    endActions = {
                        Icon(painterResource(if (expanded) R.drawable.ic_collapse else R.drawable.ic_expand),
                            stringResource(if (expanded) R.string.master_collapse_desc else R.string.master_expand_desc),
                            modifier = Modifier.size(20.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantActions)
                    })
                AnimatedVisibility(expanded) {
                    Column {
                        SettingSwitch(R.string.usb_title, if (state.usb) R.string.usb_on else R.string.usb_off,
                            state.usb, state.canWrite) { actions.toggle(UiActions.Toggle.USB, it) }
                        SettingSwitch(R.string.wireless_title, if (state.wireless) R.string.wireless_on else R.string.wireless_off,
                            state.wireless, state.canWrite) { actions.toggle(UiActions.Toggle.WIRELESS, it) }
                    }
                }
                SettingSwitch(R.string.devopts_title, if (state.dev) R.string.devopts_on else R.string.devopts_off,
                    state.dev, state.canWrite) { actions.toggle(UiActions.Toggle.DEVELOPER, it) }
                SuperArrow(title = stringResource(R.string.auth_mode_title), summary = authSummary(state),
                    modifier = Modifier.testTag("auth"), enabled = !state.busy, onClick = { onDialog("auth") })
            }
        }
        item(key = "quick_heading") { SectionTitle(stringResource(R.string.card_linkage_title)) }
        item(key = "quick_settings") {
            Card {
                SuperArrow(title = stringResource(R.string.linkage_manage_title),
                    summary = stringResource(if (state.apps.isEmpty()) R.string.linkage_apps_empty else R.string.linkage_manage_sum),
                    modifier = Modifier.testTag("manage_apps"), enabled = !state.busy, onClick = onPicker)
                SuperArrow(title = stringResource(R.string.linkage_default_title),
                    summary = state.apps.firstOrNull { it.packageName == state.defaultPackage }?.label
                        ?: stringResource(R.string.linkage_default_none),
                    enabled = state.apps.isNotEmpty() && !state.busy, onClick = { onDialog("default") })
                SettingSwitch(R.string.linkage_switch, R.string.linkage_sum, state.linkage, !state.busy) {
                    actions.toggle(UiActions.Toggle.LINKAGE, it)
                    if (it && state.defaultPackage.isNotEmpty()) onDialog("linkage")
                }
                SettingSwitch(R.string.kill_before_open_title, R.string.kill_before_open_sum,
                    state.killBeforeOpen, !state.busy) { actions.toggle(UiActions.Toggle.KILL, it) }
            }
        }
        item(key = "notification_heading") { SectionTitle(stringResource(R.string.card_notify_title)) }
        item(key = "notifications") {
            Card {
                SettingSwitch(R.string.btn_temp_close, R.string.temp_close_sum, state.notify, !state.busy) {
                    actions.toggle(UiActions.Toggle.NOTIFY, it)
                }
                if (state.notify && state.liveUpdatesSupported) {
                    SuperArrow(title = stringResource(R.string.live_update_title), summary = stringResource(R.string.live_update_sum),
                        onClick = actions::testNotification)
                }
            }
        }
        item(key = "appearance_heading") { SectionTitle(stringResource(R.string.appearance_section)) }
        item(key = "theme_mode") {
            Card {
                SuperArrow(title = stringResource(R.string.theme_mode_title),
                    summary = stringResource(state.themeMode.labelResource),
                    modifier = Modifier.testTag("theme_mode"), onClick = { onDialog("theme") })
            }
        }
        item(key = "about") {
            Card {
                SuperArrow(title = stringResource(R.string.about_title), summary = BuildConfig.VERSION_NAME,
                    modifier = Modifier.testTag("about"), onClick = { onDialog("about") })
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, Modifier.padding(start = 12.dp, top = 10.dp, bottom = 2.dp).semantics { heading() },
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1)
}

@Composable
private fun SettingSwitch(title: Int, summary: Int, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    SuperSwitch(title = stringResource(title), summary = stringResource(summary), checked = checked,
        enabled = enabled, onCheckedChange = onChange)
}

@Composable
internal fun authSummary(state: MainUiState): String = if (state.checkingAuth) {
    stringResource(R.string.auth_checking)
} else {
    state.authMode + stringResource(if (state.authorized) R.string.auth_granted_suffix else R.string.auth_denied_suffix)
}

@Composable
private fun masterSummary(state: MainUiState): String = stringResource(when {
    state.busy -> R.string.operation_in_progress
    !state.authorized && !state.checkingAuth -> R.string.auth_no_priv
    !state.known -> R.string.master_checking
    !state.anyOn -> R.string.master_off
    state.usb && state.wireless -> R.string.master_on
    else -> R.string.master_partial
})
