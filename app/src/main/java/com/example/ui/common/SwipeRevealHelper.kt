package com.example.ui.common

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Shared swipe-to-delete reveal cap helper used across Ledger rows and Attached Media rows.
 * Caps the red delete reveal band at min(30% of row width, 120.dp) so at least 70%–80% of the
 * row content stays visible when revealed.
 */
object SwipeRevealHelper {
    const val MAX_REVEAL_WIDTH_FRACTION: Float = 0.30f
    val MAX_REVEAL_CAP_DP: Dp = 120.dp

    fun computeMaxRevealPx(rowWidthPx: Float, density: Density): Float {
        if (rowWidthPx <= 0f) return with(density) { MAX_REVEAL_CAP_DP.toPx() }
        return min(MAX_REVEAL_WIDTH_FRACTION * rowWidthPx, with(density) { MAX_REVEAL_CAP_DP.toPx() })
    }
}
