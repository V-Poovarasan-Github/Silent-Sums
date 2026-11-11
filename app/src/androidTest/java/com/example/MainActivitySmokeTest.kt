package com.example

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchMainActivity_reachesDashboardOnboardingOrLockScreenWithoutCrashing() {
        composeRule.waitForIdle()
        val rootTags = setOf(
            "dashboard_screen",
            "kpi_current_balance",
            "custom_master_pin_pad",
            "vault_lock_header_icon",
            "onboarding_start_fresh_card",
            "onboarding_import_backup_card"
        )
        val hasExpectedSurface = composeRule.onAllNodes(
            SemanticsMatcher("is main or lock or onboarding surface") { node ->
                val tag = node.config.getOrNull(SemanticsProperties.TestTag)
                val textList = node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()
                (tag != null && tag in rootTags) ||
                    textList.any { it.contains("SilentSums", ignoreCase = true) || it.contains("Start Fresh", ignoreCase = true) }
            },
            useUnmergedTree = true
        ).fetchSemanticsNodes().isNotEmpty()

        assertTrue(
            "MainActivity must render Dashboard, Onboarding, or LockScreen without crashing",
            hasExpectedSurface
        )
    }
}
