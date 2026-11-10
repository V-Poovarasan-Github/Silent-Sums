package com.example

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex

/**
 * Scrolls `dashboard_lazy_column` across its keyed items until a node matching [matcher]
 * is composed and displayed in the viewport, invoking `performScrollTo()` on the matched node
 * so nested scroll containers (e.g. horizontal KPI row) or tall cards bring it into view.
 */
fun ComposeTestRule.scrollToDashboardNode(
    matcher: SemanticsMatcher,
    useUnmergedTree: Boolean = false
): SemanticsNodeInteraction {
    fun isTargetDisplayed(): Boolean = runCatching {
        onNode(matcher, useUnmergedTree = useUnmergedTree).assertIsDisplayed()
    }.isSuccess

    if (!isTargetDisplayed()) {
        runCatching {
            onNode(matcher, useUnmergedTree = useUnmergedTree).performScrollTo()
        }
        waitForIdle()
    }

    if (!isTargetDisplayed()) {
        if (onAllNodesWithTag("dashboard_lazy_column", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
            for (idx in 0..10) {
                val scrolled = runCatching {
                    onNodeWithTag("dashboard_lazy_column").performScrollToIndex(idx)
                }.isSuccess
                if (!scrolled) break
                waitForIdle()
                if (onAllNodes(matcher, useUnmergedTree = useUnmergedTree).fetchSemanticsNodes().isNotEmpty()) {
                    runCatching {
                        onNode(matcher, useUnmergedTree = useUnmergedTree).performScrollTo()
                    }
                    waitForIdle()
                    if (isTargetDisplayed()) {
                        break
                    }
                }
            }
        }
    }
    return onNode(matcher, useUnmergedTree = useUnmergedTree)
}

fun ComposeTestRule.scrollToDashboardTag(
    tag: String,
    useUnmergedTree: Boolean = false
): SemanticsNodeInteraction = scrollToDashboardNode(
    matcher = hasTestTag(tag),
    useUnmergedTree = useUnmergedTree
)

fun ComposeTestRule.scrollToDashboardText(
    text: String,
    substring: Boolean = false,
    ignoreCase: Boolean = false,
    useUnmergedTree: Boolean = false
): SemanticsNodeInteraction = scrollToDashboardNode(
    matcher = hasText(text, substring = substring, ignoreCase = ignoreCase),
    useUnmergedTree = useUnmergedTree
)
