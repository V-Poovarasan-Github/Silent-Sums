package com.example

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Phase6CleanupTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun resolveProjectRoot(): File {
        val cwd = File(".").canonicalFile
        return if (File(cwd, "app/src/main").exists()) cwd else cwd.parentFile ?: cwd
    }

    @Test
    fun test6_5_mainActivitySmokeTest_reachesDashboardOnboardingOrLockScreenWithoutCrashing() {
        composeRule.waitForIdle()
        val rootTags = setOf(
            "dashboard_screen",
            "kpi_current_balance",
            "custom_master_pin_pad",
            "vault_lock_header_icon",
            "onboarding_start_fresh_card",
            "onboarding_import_backup_card"
        )
        val nodes = composeRule.onAllNodes(
            SemanticsMatcher("is main or lock or onboarding surface") { node ->
                val tag = node.config.getOrNull(SemanticsProperties.TestTag)
                val textList = node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()
                (tag != null && tag in rootTags) ||
                    textList.any { it.contains("SilentSums", ignoreCase = true) || it.contains("Start Fresh", ignoreCase = true) }
            },
            useUnmergedTree = true
        ).fetchSemanticsNodes()

        assertTrue(
            "MainActivity smoke test must reach Dashboard, Onboarding, or LockScreen without crashing",
            nodes.isNotEmpty()
        )
    }

    @Test
    fun test6_1_to_6_7_deadAssetsClassesDocsShortcutsAndLogsVerified() {
        val root = resolveProjectRoot()
        val drawableDir = File(root, "app/src/main/res/drawable")

        // 6.1 Unused drawables deleted
        val deletedDrawables = listOf(
            "ic_launcher_asset.jpg",
            "ic_chevron_right.xml",
            "ic_calculate.xml",
            "ic_expand_less.xml",
            "ic_expand_more.xml",
            "ic_cloud_download.xml",
            "ic_storage.xml",
            "ic_currency_rupee.xml",
            "ic_sync.xml",
            "ic_file_download.xml",
            "ic_trending_up.xml",
            "ic_account_balance.xml",
            "ic_history.xml",
            "ic_credit_card.xml",
            "ic_dark_mode.xml",
            "ic_backspace.xml",
            "ic_wallet.xml",
            "ic_arrow_downward.xml",
            "ic_upload_file.xml",
            "ic_arrow_upward.xml",
            "ic_light_mode.xml"
        )
        for (name in deletedDrawables) {
            assertFalse("Unused drawable $name must be deleted", File(drawableDir, name).exists())
        }

        // Active drawables retained
        assertTrue(File(drawableDir, "ic_fingerprint_m3.xml").exists())
        assertTrue(File(drawableDir, "ic_launcher_background.xml").exists())
        assertTrue(File(drawableDir, "ic_launcher_foreground.xml").exists())

        // 6.2 Unused TxType and HelpGesturesCatalog removed
        val modelsText = File(root, "app/src/main/java/com/example/domain/model/Models.kt").readText()
        assertFalse("Unused TxType enum must be removed", modelsText.contains("enum class TxType"))

        val helpSheetText = File(root, "app/src/main/java/com/example/ui/dashboard/HelpGesturesSheet.kt").readText()
        assertFalse("Unused HelpGesturesCatalog must be removed", helpSheetText.contains("object HelpGesturesCatalog"))

        // 6.3 & 6.4 Stale app/applet/NOTES.md deleted, docs/DEVLOG.md has Phases 1-6, root NOTES.md has parked features
        assertFalse("Stale app/applet/NOTES.md must be deleted", File(root, "app/applet/NOTES.md").exists())
        val devlogFile = File(root, "docs/DEVLOG.md")
        assertTrue("docs/DEVLOG.md must exist", devlogFile.exists())
        val devlogText = devlogFile.readText()
        for (phaseHeading in listOf("PHASE 1", "PHASE 2", "PHASE 3", "PHASE 4A", "PHASE 4B", "PHASE 5", "PHASE 6")) {
            assertTrue("docs/DEVLOG.md must preserve $phaseHeading", devlogText.contains(phaseHeading))
        }

        val rootNotesFile = File(root, "NOTES.md")
        assertTrue("Root NOTES.md must exist", rootNotesFile.exists())
        val rootNotesText = rootNotesFile.readText()
        assertTrue("Root NOTES.md must contain parked features", rootNotesText.contains("Parked"))
        assertFalse("Root NOTES.md must not duplicate full Phase 1-5 devlog", rootNotesText.contains("TASK RELEASE-3 — PHASE 1"))

        // 6.5 Template tests removed, MainActivitySmokeTest present
        assertFalse(File(root, "app/src/test/java/com/example/ExampleUnitTest.kt").exists())
        assertFalse(File(root, "app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt").exists())
        assertTrue(File(root, "app/src/androidTest/java/com/example/MainActivitySmokeTest.kt").exists())

        // 6.6 Shortcuts use dedicated icons
        val shortcutsXml = File(root, "app/src/main/res/xml/shortcuts.xml").readText()
        assertFalse(
            "shortcuts.xml must not reuse @drawable/ic_launcher_foreground directly",
            shortcutsXml.contains("@drawable/ic_launcher_foreground")
        )
        assertTrue(shortcutsXml.contains("@drawable/ic_shortcut_add_expense"))
        assertTrue(shortcutsXml.contains("@drawable/ic_shortcut_add_income"))
        assertTrue(shortcutsXml.contains("@drawable/ic_shortcut_view_dues"))
        assertTrue(File(drawableDir, "ic_shortcut_add_expense.xml").exists())
        assertTrue(File(drawableDir, "ic_shortcut_add_income.xml").exists())
        assertTrue(File(drawableDir, "ic_shortcut_view_dues.xml").exists())

        // 6.7 No println calls in app/src/main, and non-crash-logger Log.* calls are wrapped in BuildConfig.DEBUG
        val mainJavaDir = File(root, "app/src/main/java")
        for (file in mainJavaDir.walkTopDown().filter { it.isFile && it.extension == "kt" }) {
            val src = file.readText()
            assertFalse("No println( allowed in production file ${file.name}", src.contains("println("))
            if (file.name != "ExpenseTrackerApp.kt" && src.contains(Regex("\\bLog\\.(d|e|i|v|w|wtf)\\("))) {
                assertTrue(
                    "Diagnostic Log calls in ${file.name} must be guarded by BuildConfig.DEBUG",
                    src.contains("BuildConfig.DEBUG")
                )
            }
        }
    }
}
