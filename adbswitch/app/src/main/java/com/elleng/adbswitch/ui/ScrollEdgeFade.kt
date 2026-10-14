package com.elleng.adbswitch.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Blend only the list edges into the page surface, leaving its overlays and gestures intact. */
internal fun Modifier.scrollEdgeFade(listState: LazyListState, surface: Color): Modifier = drawWithCache {
    val topHeight = 24.dp.toPx().coerceAtMost(size.height / 2f)
    val bottomHeight = 48.dp.toPx().coerceAtMost(size.height / 2f)
    val bottomStart = size.height - bottomHeight
    val transparent = surface.copy(alpha = 0f)
    val topBrush = Brush.verticalGradient(listOf(surface, transparent), endY = topHeight)
    val bottomBrush = Brush.verticalGradient(
        listOf(transparent, surface), startY = bottomStart, endY = size.height,
    )
    onDrawWithContent {
        drawContent()
        if (topHeight > 0f) {
            // Read scroll state during drawing, without recomposing the list on every scroll frame.
            val topAlpha = when {
                !listState.canScrollBackward -> 0f
                listState.firstVisibleItemIndex > 0 -> 1f
                else -> (listState.firstVisibleItemScrollOffset / topHeight).coerceIn(0f, 1f)
            }
            if (topAlpha > 0f) drawRect(topBrush, size = Size(size.width, topHeight), alpha = topAlpha)
        }
        if (bottomHeight > 0f) {
            // Existing bottom padding lets the final row scroll completely clear of this fade.
            drawRect(bottomBrush, topLeft = Offset(0f, bottomStart), size = Size(size.width, bottomHeight))
        }
    }
}
