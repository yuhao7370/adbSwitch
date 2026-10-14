package com.elleng.adbswitch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import com.elleng.adbswitch.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Fixed title row; only horizontal page navigation moves it. */
@Composable
internal fun HomeTopBar(
    visibleTabs: List<HomeTab>, pagePosition: State<Float>,
) {
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1f else -1f
    Box(
        Modifier.fillMaxWidth().background(MiuixTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .clipToBounds().testTag("home_header"),
    ) {
        visibleTabs.forEach { tab ->
            key(tab) {
                FixedTitle(
                    title = stringResource(if (tab == HomeTab.Apps) R.string.quick_apps_page else R.string.settings_page),
                    modifier = Modifier.fillMaxWidth().graphicsLayer {
                        translationX = (tab.ordinal - pagePosition.value) * size.width * direction
                    },
                )
            }
        }
    }
}

@Composable
private fun FixedTitle(title: String, modifier: Modifier) {
    Box(
        modifier = modifier.heightIn(min = 56.dp).padding(horizontal = 28.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            title,
            modifier = Modifier.testTag("home_title").semantics { heading() },
            fontSize = 26.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            overflow = TextOverflow.Ellipsis, softWrap = false,
        )
    }
}
