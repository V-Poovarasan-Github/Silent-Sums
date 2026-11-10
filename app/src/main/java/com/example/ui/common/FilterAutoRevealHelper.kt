package com.example.ui.common

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.unit.IntOffset

/**
 * Helper for O2''' Activation-Order Head Cluster, Position-Only Glide, and Auto-Reveal.
 */
object FilterAutoRevealHelper {

    // Test override for reduced motion testing
    var forceReducedMotionForTests: Boolean? = null

    val CANONICAL_FILTER_KEYS = listOf(
        "date_range",
        "year",
        "month",
        "transfer_route",
        "type",
        "category",
        "account"
    )

    fun isReducedMotion(context: Context): Boolean {
        forceReducedMotionForTests?.let { return it }
        return try {
            val dScale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            val tScale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1f
            )
            dScale == 0f || tScale == 0f
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Reconciles the activation order list against the set of currently active keys.
     * (a) Deactivation removes the key; re-activation appends it at the end.
     */
    fun reconcileActivationOrder(
        existingOrder: List<String>,
        currentlyActiveKeys: Set<String>,
        allVisibleKeys: List<String>
    ): List<String> {
        val preserved = existingOrder.filter { it in currentlyActiveKeys }.toMutableList()
        for (key in allVisibleKeys) {
            if (key in currentlyActiveKeys && key !in preserved) {
                preserved.add(key)
            }
        }
        return preserved
    }

    /**
     * Computes the row order on commit:
     * [Clear All (in-flow leading, rendered only when >=1 active)] +
     * [ACTIVE chips in ACTIVATION order (first-activated first)] +
     * [INACTIVE chips in canonical order]
     */
    fun computeCommittedOrder(
        activationOrder: List<String>,
        currentlyActiveKeys: Set<String>,
        allVisibleKeys: List<String>
    ): List<String> {
        val list = mutableListOf<String>()
        if (currentlyActiveKeys.isNotEmpty()) {
            list.add("clear_all")
        }
        // Active chips in activation order
        for (key in activationOrder) {
            if (key in currentlyActiveKeys && key in allVisibleKeys) {
                list.add(key)
            }
        }
        // Inactive chips in canonical order
        for (key in allVisibleKeys) {
            if (key !in currentlyActiveKeys) {
                list.add(key)
            }
        }
        return list
    }

    /**
     * Resolves the placement animation spec.
     * Position-only, ~200-240ms easeInOut. Null when reduced-motion is active.
     */
    fun getPlacementSpec(isReducedMotion: Boolean): FiniteAnimationSpec<IntOffset>? {
        return if (isReducedMotion) {
            null
        } else {
            tween(durationMillis = 220, easing = FastOutSlowInEasing)
        }
    }

    /**
     * Executes auto-reveal on the LazyRow scroll state:
     * - Animates or snaps to item 0 so the leading cluster is fully within viewport with zero user drag.
     * - When zero active, returns to baseline (start) offset.
     */
    suspend fun autoRevealLeadingCluster(
        lazyListState: LazyListState,
        isReducedMotion: Boolean
    ) {
        if (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0) {
            if (isReducedMotion) {
                lazyListState.scrollToItem(0, 0)
            } else {
                lazyListState.animateScrollToItem(0, 0)
            }
        }
    }
}
