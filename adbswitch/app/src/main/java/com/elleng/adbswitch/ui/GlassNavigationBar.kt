package com.elleng.adbswitch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import com.elleng.adbswitch.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class HomeTab { Apps, Settings }
data class HomeNavigationRequest(val id: String, val tab: HomeTab)

/** The Scaffold owns system insets. This capsule only styles the two navigation targets. */
@Composable
internal fun GlassNavigationBar(
    selected: HomeTab, onSelect: (HomeTab) -> Unit, pagePosition: State<Float>,
    hazeState: HazeState, modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val dark = colors.surface.luminance() < .5f
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1f else -1f
    val glassStyle = HazeStyle(
        backgroundColor = colors.surface,
        tint = HazeTint(colors.surfaceContainer.copy(alpha = .66f)),
        blurRadius = 24.dp,
        noiseFactor = .015f,
        fallbackTint = HazeTint(colors.surfaceContainer.copy(alpha = .92f)),
    )
    BoxWithConstraints(
        modifier.widthIn(max = 300.dp).fillMaxWidth()
            .testTag("glass_navigation")
            .shadow(12.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .hazeEffect(state = hazeState, style = glassStyle)
            .border(1.dp, colors.onSurface.copy(alpha = if (dark) .12f else .06f), CircleShape)
            .padding(7.dp),
    ) {
        val half = maxWidth / 2
        Box(Modifier.matchParentSize()) {
            Box(Modifier.width(half).fillMaxHeight()
                .graphicsLayer { translationX = pagePosition.value * size.width * direction }
                .testTag("navigation_indicator")
                .background(colors.onSurface.copy(alpha = if (dark) .1f else .065f), CircleShape))
        }
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            NavigationItem(HomeTab.Apps, selected, pagePosition, R.drawable.ic_apps, R.string.tab_apps, onSelect)
            NavigationItem(HomeTab.Settings, selected, pagePosition, R.drawable.ic_settings, R.string.tab_settings, onSelect)
        }
    }
}

@Composable
private fun RowScope.NavigationItem(
    tab: HomeTab, selected: HomeTab, pagePosition: State<Float>, icon: Int, label: Int, onSelect: (HomeTab) -> Unit,
) {
    val selectedFraction = if (tab == HomeTab.Settings) pagePosition.value else 1f - pagePosition.value
    val color = lerp(MiuixTheme.colorScheme.onSurfaceVariantSummary, MiuixTheme.colorScheme.primary, selectedFraction)
    Column(
        Modifier.weight(1f).heightIn(min = 58.dp).clip(CircleShape)
            .testTag(if (tab == HomeTab.Apps) "tab_apps" else "tab_settings")
            .selectable(selected == tab, role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() }, indication = null,
                onClick = { onSelect(tab) })
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(icon), null, Modifier.size(23.dp), tint = color)
        Spacer(Modifier.height(3.dp))
        Text(stringResource(label), style = MiuixTheme.textStyles.footnote2, color = color)
    }
}
