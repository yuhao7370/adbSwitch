package com.elleng.adbswitch.ui

import android.content.Context
import android.content.Intent
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.state.ToggleableState
import androidx.core.graphics.drawable.toBitmap
import com.elleng.adbswitch.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.Collator
import java.util.Locale

internal data class InstalledApp(val packageName: String, val label: String)

@Composable
internal fun AppPickerScreen(initialSelection: List<String>, onCancel: () -> Unit, onConfirm: (List<String>) -> Unit) {
    val context = LocalContext.current.applicationContext
    // Pin the opening selection; checking a row must never reorder the list under the finger.
    val pinned = rememberSaveable { ArrayList(initialSelection) }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) {
        loading = true
        try {
            apps = loadInstalledApps(context, pinned.toSet())
            error = apps.isEmpty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = true
        } finally { loading = false }
    }
    AppPickerContent(apps, initialSelection, loading, error, { refresh++ }, onCancel, onConfirm)
}

@Composable
internal fun AppPickerContent(
    apps: List<InstalledApp>, initialSelection: List<String>, loading: Boolean, error: Boolean,
    onRefresh: () -> Unit, onCancel: () -> Unit, onConfirm: (List<String>) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf<List<String>>(initialSelection.toList()) }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, query) {
        val needle = query.trim().lowercase(Locale.ROOT)
        apps.filter { it.label.lowercase(Locale.ROOT).contains(needle) || it.packageName.lowercase(Locale.ROOT).contains(needle) }
    }
    BackHandler(onBack = onCancel)
    Scaffold(
        topBar = {
            SmallTopAppBar(title = stringResource(R.string.linkage_manage_title), navigationIcon = {
                IconButton(onCancel) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back), Modifier.size(24.dp)) }
            })
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(stringResource(android.R.string.cancel), onCancel, Modifier.weight(1f))
                TextButton(stringResource(R.string.picker_save_count, selected.size), { onConfirm(selected.toList()) },
                    Modifier.weight(1f).testTag("picker_save"), enabled = !loading && !error,
                    colors = ButtonDefaults.textButtonColorsPrimary())
            }
        },
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).padding(horizontal = 16.dp)) {
            TextField(value = query, onValueChange = { query = it }, label = stringResource(R.string.picker_hint),
                useLabelAsPlaceholder = true, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("picker_search"))
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.picker_selected_count, selected.size), style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                TextButton(stringResource(R.string.picker_refresh), onRefresh, enabled = !loading, minHeight = 40.dp)
            }
            PullToRefresh(isRefreshing = loading, onRefresh = onRefresh, modifier = Modifier.weight(1f),
                refreshTexts = listOf("下拉刷新", "松开刷新", "刷新中", "刷新完成")) {
                LazyColumn(Modifier.fillMaxSize().testTag("picker_list"), verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                    if (loading && apps.isEmpty()) item { PickerMessage(stringResource(R.string.picker_loading)) }
                    else if (error) item { PickerMessage(stringResource(R.string.picker_need_permission)) }
                    else if (filtered.isEmpty()) item { PickerMessage(stringResource(R.string.picker_no_results)) }
                    items(filtered, key = { it.packageName }) { app ->
                        val checked = app.packageName in selected
                        val toggle = {
                            selected = if (checked) selected - app.packageName else selected + app.packageName
                        }
                        Card {
                            BasicComponent(title = app.label, summary = app.packageName,
                                modifier = Modifier.testTag("picker:${app.packageName}"), onClick = toggle,
                                startAction = { AppIcon(app.packageName) },
                                endActions = { Checkbox(state = if (checked) ToggleableState.On else ToggleableState.Off, onClick = toggle) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerMessage(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(24.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}

@SuppressLint("QueryPermissionsNeeded") // The manifest queries ACTION_MAIN/CATEGORY_LAUNCHER; only launchable apps are offered.
private suspend fun loadInstalledApps(context: Context, pinned: Set<String>): List<InstalledApp> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val launchable = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .mapNotNull { it.activityInfo?.packageName }.toSet()
    val collator = Collator.getInstance(Locale.getDefault())
    pm.getInstalledApplications(0).asSequence()
        .filter { it.packageName != context.packageName }
        .filter { if (launchable.isNotEmpty()) it.packageName in launchable else pm.getLaunchIntentForPackage(it.packageName) != null }
        .map { InstalledApp(it.packageName, it.loadLabel(pm).toString()) }
        .toList().sortedWith { a, b ->
            val priority = (b.packageName in pinned).compareTo(a.packageName in pinned)
            if (priority != 0) priority else collator.compare(a.label, b.label)
        }
}

private val iconCache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
}

@Composable
internal fun AppIcon(packageName: String, size: Int = 40) {
    val context = LocalContext.current.applicationContext
    val pixels = with(LocalDensity.current) { size.dp.roundToPx() }
    val key = "$packageName:$pixels"
    val bitmap by produceState(iconCache.get(key), key) {
        if (value == null) value = withContext(Dispatchers.IO) {
            try {
                context.packageManager.getApplicationIcon(packageName).toBitmap(pixels, pixels)
                    .also { iconCache.put(key, it) }
            } catch (_: Exception) { null }
        }
    }
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize()) }
            ?: Icon(painterResource(R.drawable.ic_app_placeholder), null, Modifier.fillMaxSize(),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions)
    }
}
