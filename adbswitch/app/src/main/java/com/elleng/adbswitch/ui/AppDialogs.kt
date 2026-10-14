package com.elleng.adbswitch.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.elleng.adbswitch.BuildConfig
import com.elleng.adbswitch.R
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AppDialogs(dialog: String, state: MainUiState, actions: UiActions, onDismiss: () -> Unit) {
    SuperDialog(show = dialog == "theme", title = stringResource(R.string.theme_mode_title), onDismissRequest = onDismiss) {
        DialogColumn {
            ThemeMode.entries.forEach { mode ->
                SuperRadioButton(title = stringResource(mode.labelResource), selected = state.themeMode == mode,
                    modifier = Modifier.testTag("theme_option_" + mode.preferenceValue),
                    onClick = { actions.setThemeMode(mode); onDismiss() })
            }
            DialogDone(onDismiss, android.R.string.cancel)
        }
    }
    SuperDialog(show = dialog == "auth", title = stringResource(R.string.auth_choose_title), onDismissRequest = onDismiss) {
        DialogColumn {
            SuperRadioButton(title = "root", summary = stringResource(R.string.auth_root_description),
                selected = state.authMode == "root", enabled = !state.busy,
                onClick = { actions.selectAuth("root"); onDismiss() })
            SuperRadioButton(title = "Shizuku", summary = stringResource(R.string.auth_shizuku_description),
                selected = state.authMode == "Shizuku", enabled = !state.busy,
                onClick = { actions.selectAuth("Shizuku"); onDismiss() })
            DialogDone(onDismiss, android.R.string.cancel)
        }
    }
    if (dialog == "default") {
        var selected by rememberSaveable { mutableStateOf(state.defaultPackage) }
        SuperDialog(show = true, title = stringResource(R.string.linkage_default_title), onDismissRequest = onDismiss) {
            DialogColumn {
                state.apps.forEach { app ->
                    SuperRadioButton(title = app.label, selected = selected == app.packageName,
                        onClick = { selected = app.packageName },
                        endActions = { AppIcon(app.packageName, 32) })
                }
                DialogButtons(onDismiss, {
                    actions.setDefaultApp(selected); onDismiss()
                }, confirmEnabled = state.apps.any { it.packageName == selected } && !state.busy)
            }
        }
    }
    SuperDialog(show = dialog == "linkage", title = stringResource(R.string.linkage_hint_title), onDismissRequest = onDismiss) {
        DialogColumn {
            Text(stringResource(R.string.linkage_hint_body), style = MiuixTheme.textStyles.body2)
            DialogDone(onDismiss)
        }
    }
    SuperDialog(show = dialog == "about", title = stringResource(R.string.about_title), onDismissRequest = onDismiss) {
        DialogColumn {
            Column(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.mipmap.ic_launcher), null, Modifier.size(72.dp))
                Text(stringResource(R.string.app_name), Modifier.padding(top = 12.dp), style = MiuixTheme.textStyles.title3)
                Text(BuildConfig.VERSION_NAME, Modifier.padding(top = 4.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AboutField(stringResource(R.string.about_package_label), state.packageName)
                    AboutField(stringResource(R.string.about_build_label), state.buildTime)
                    AboutField(stringResource(R.string.auth_mode_title), authSummary(state))
                }
            }
            Spacer(Modifier.height(20.dp))
            TextButton(stringResource(R.string.about_info_desc), actions::openRepository, Modifier.fillMaxWidth())
            Text(stringResource(R.string.update_disabled_personal), Modifier.padding(vertical = 12.dp).align(Alignment.CenterHorizontally),
                style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            DialogDone(onDismiss)
        }
    }
}

@Composable
internal fun QuickAppDialog(app: QuickApp?, actions: UiActions, enabled: Boolean, onDismiss: () -> Unit) {
    SuperDialog(show = app != null, title = stringResource(R.string.quick_options_title), onDismissRequest = onDismiss) {
        if (app != null) DialogColumn(Modifier.testTag("quick_app_dialog")) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AppIcon(app.packageName, 48)
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MiuixTheme.textStyles.title3)
                    Text(app.packageName, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote2)
                }
            }
            Card {
                SuperSwitch(title = stringResource(R.string.rear_option_title), summary = stringResource(R.string.rear_option_summary),
                    checked = app.closeRear, enabled = enabled, onCheckedChange = { actions.setCloseRear(app.packageName, it) })
            }
            Text(stringResource(R.string.rear_option_recovery_hint), Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Card {
                SuperArrow(title = stringResource(R.string.quick_create_shortcut), summary = stringResource(R.string.quick_shortcut_summary),
                    enabled = enabled, onClick = { actions.createShortcut(app.packageName); onDismiss() })
            }
            DialogDone(onDismiss, R.string.quick_options_done)
        }
    }
}

@Composable
internal fun NotificationDialog(show: Boolean, actions: UiActions, onDismiss: () -> Unit) {
    SuperDialog(show = show, title = stringResource(R.string.live_update_title), onDismissRequest = onDismiss) {
        DialogColumn {
            Text(stringResource(R.string.live_update_test_sent_plain) + "\n\n" + stringResource(R.string.live_update_grant),
                style = MiuixTheme.textStyles.body2)
            DialogButtons(onDismiss, { actions.openNotificationSettings(); onDismiss() })
        }
    }
}

@Composable
private fun AboutField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Text(value, style = MiuixTheme.textStyles.body2)
    }
}

@Composable
private fun DialogColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val availableHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    Column(modifier.fillMaxWidth().heightIn(max = (availableHeight * .66f).coerceAtLeast(120.dp))
        .verticalScroll(rememberScrollState()), content = content)
}

@Composable
private fun DialogDone(onDismiss: () -> Unit, label: Int = android.R.string.ok) {
    TextButton(stringResource(label), onDismiss, Modifier.fillMaxWidth().padding(top = 16.dp),
        colors = ButtonDefaults.textButtonColorsPrimary())
}

@Composable
private fun DialogButtons(onCancel: () -> Unit, onConfirm: () -> Unit, confirmEnabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(stringResource(android.R.string.cancel), onCancel, Modifier.weight(1f))
        TextButton(stringResource(android.R.string.ok), onConfirm, Modifier.weight(1f), enabled = confirmEnabled,
            colors = ButtonDefaults.textButtonColorsPrimary())
    }
}
