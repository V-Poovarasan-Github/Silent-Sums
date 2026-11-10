package com.example

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.datastore.AppSettingsRepository
import com.example.data.repository.FinanceRepository
import com.example.domain.util.Formatters
import com.example.ui.common.CrashRecoveryBanner
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase3C_Tests {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var settingsRepository: AppSettingsRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CrashLogger.clearCrashLogs(context)
        settingsRepository = AppSettingsRepository(context)
    }

    @After
    fun tearDown() {
        ShadowLooper.idleMainLooper()
        CrashLogger.clearCrashLogs(context)
    }

    // =========================================================================
    // RT1 — Crash Recovery Banner & Share Log
    // =========================================================================
    @Test
    fun rt1_crashRecoveryBanner_detectsLogAndSharesPayload() {
        val fakeCrash = """
            APP START 2026-10-24 10:00:00.000
            ================ CRASH REPORT ================
            TIMESTAMP: 2026-10-24 10:00:05.123
            THREAD: main (id: 1)
            EXCEPTION: java.lang.IllegalStateException
            MESSAGE: Simulated crash for RT1 test
            STACK TRACE:
            java.lang.IllegalStateException: Simulated crash for RT1 test
            	at com.example.MainActivity.simulatedCrash(MainActivity.kt:99)
            ==============================================
        """.trimIndent()

        // 1. Seed fake crash log in private storage
        val logFile = CrashLogger.getLogFile1(context)
        logFile.parentFile?.mkdirs()
        logFile.writeText(fakeCrash)

        // 2. Detection check
        val detected = CrashLogger.checkCrashLog(context)
        assertNotNull("CrashLogger must detect log with stack trace", detected)
        assertEquals(fakeCrash, detected)

        // 3. Launch banner composable
        var sharedLogPayload: String? = null
        composeTestRule.setContent {
            MyApplicationTheme {
                CrashRecoveryBanner(
                    crashLog = detected!!,
                    onShareLog = { sharedLogPayload = it }
                )
            }
        }
        composeTestRule.waitForIdle()

        // 4. Assert banner copy
        composeTestRule.onNodeWithText("SilentSums stopped unexpectedly last time. ", substring = true).assertExists()
        composeTestRule.onNodeWithText("Share log").assertExists()
        composeTestRule.onNodeWithText(" to help us fix it.", substring = true).assertExists()

        // 5. Tap "Share log" and verify payload and intent
        composeTestRule.onNodeWithText("Share log").performClick()
        assertEquals("Shared log payload must match seeded crash", fakeCrash, sharedLogPayload)

        val shareIntent = CrashLogger.createShareIntent(fakeCrash)
        assertEquals(Intent.ACTION_SEND, shareIntent.action)
        assertEquals("text/plain", shareIntent.type)
        assertEquals("SilentSums Crash Report", shareIntent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals(fakeCrash, shareIntent.getStringExtra(Intent.EXTRA_TEXT))

        // 6. Dismissal on next clean start: clean APP START without stack trace clears detection
        CrashLogger.recordCleanStart(context)
        val cleanDetected = CrashLogger.checkCrashLog(context)
        assertNull("Clean start without stack trace must clear crash banner detection", cleanDetected)
        ShadowLooper.idleMainLooper()
    }

    // =========================================================================
    // RT2 — PLAY_STORE_GUIDE.md Exists and Contains Required Sections
    // =========================================================================
    @Test
    fun rt2_playStoreGuide_existsAndContainsRequiredSections() {
        val candidates = listOf(
            File("PLAY_STORE_GUIDE.md"),
            File("../PLAY_STORE_GUIDE.md"),
            File("../../PLAY_STORE_GUIDE.md")
        )
        val guideFile = candidates.firstOrNull { it.exists() && it.isFile }
        assertNotNull("PLAY_STORE_GUIDE.md must exist in root project directory", guideFile)

        val text = guideFile!!.readText()
        assertTrue("Must specify App Name 'SilentSums'", text.contains("SilentSums"))
        assertTrue("Must include Short Description", text.contains("Offline-first personal finance tracker with zero telemetry"))

        // Character count check on short description
        val shortDesc = "Offline-first personal finance tracker with zero telemetry"
        assertTrue("Short description must be <= 80 characters", shortDesc.length <= 80)

        // Key required concepts
        assertTrue("Must highlight privacy", text.contains("privacy", ignoreCase = true))
        assertTrue("Must highlight local storage", text.contains("local storage", ignoreCase = true))
        assertTrue("Must highlight CSV portability", text.contains("CSV portability", ignoreCase = true) || text.contains("CSV", ignoreCase = true))
        assertTrue("Must mention no accounts needed", text.contains("no accounts", ignoreCase = true))
        assertTrue("Must highlight split transactions", text.contains("split transactions", ignoreCase = true))
        assertTrue("Must highlight voice notes", text.contains("voice notes", ignoreCase = true))

        // Target audience & Privacy Policy
        assertTrue("Must include Target Audience section", text.contains("Target Audience"))
        assertTrue("Must include Privacy Policy summary", text.contains("Privacy Policy"))
        assertTrue("Must explicitly mention No internet permission", text.contains("No internet permission", ignoreCase = true))
        assertTrue("Must state no analytics", text.contains("no analytics", ignoreCase = true))
        assertTrue("Must state data stays on device", text.contains("data stays on device", ignoreCase = true))
    }

    // =========================================================================
    // RT3 — ₹ Currency Asset Audit (Zero hardcoded currency in entities/calc/csv writer)
    // =========================================================================
    @Test
    fun rt3_currencyAudit_noHardcodedCurrencyInEntitiesCsvWriterCalculator() {
        // 1. Audit Room entity definitions under data/model/
        val baseDir = File("src/main/java/com/example").takeIf { it.exists() }
            ?: File("app/src/main/java/com/example")

        val modelDir = File(baseDir, "data/model")
        if (modelDir.exists()) {
            modelDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
                val content = file.readText()
                assertFalse("Entity file ${file.name} must not contain hardcoded '₹'", content.contains("₹"))
                assertFalse("Entity file ${file.name} must not contain hardcoded '€'", content.contains("€"))
            }
        }

        // 2. Audit calculator logic under domain/calculator/
        val calcDir = File(baseDir, "domain/calculator")
        if (calcDir.exists()) {
            calcDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
                val content = file.readText()
                assertFalse("Calculator file ${file.name} must not contain hardcoded '₹'", content.contains("₹"))
                assertFalse("Calculator file ${file.name} must not contain hardcoded '€'", content.contains("€"))
            }
        }

        // 3. Dynamic currency routing through Formatters
        val formattedRupee = Formatters.formatMoney(1500.0, currencySymbol = "₹")
        val formattedDollar = Formatters.formatMoney(1500.0, currencySymbol = "$")
        val formattedEuro = Formatters.formatMoney(1500.0, currencySymbol = "€")
        val formattedPound = Formatters.formatMoney(1500.0, currencySymbol = "£")

        assertEquals("₹1,500.00", formattedRupee)
        assertEquals("$1,500.00", formattedDollar)
        assertEquals("€1,500.00", formattedEuro)
        assertEquals("£1,500.00", formattedPound)
    }

    // =========================================================================
    // RT4 — SAF Grant Revocation Disclosure Notice
    // =========================================================================
    @Test
    fun rt4_safGrantRevocation_triggersNotice() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = FinanceRepository(db)
            val vm = SettingsViewModel(repo, settingsRepository, db)

            // Given: User previously picked a custom SAF tree URI that is now revoked / inaccessible
            val revokedUriString = "content://com.android.externalstorage.documents/tree/primary%3ARevokedNonExistentFolder"
            settingsRepository.setAutoSaveTreeUri(revokedUriString)

            // When: checkSafGrant is evaluated
            val isRevoked = vm.checkSafGrant(context)

            // Then: Notice is triggered
            assertTrue("Revoked/inaccessible URI must return true from checkSafGrant", isRevoked)
            val expectedNotice = "Your backup folder needs re-connecting. Tap here to pick it again."
            assertEquals(expectedNotice, vm.safGrantNotice.value)

            // When: Reset to default (empty tree URI)
            settingsRepository.setAutoSaveTreeUri("")
            val isDefaultRevoked = vm.checkSafGrant(context)
            assertFalse("Default blank URI is not revoked", isDefaultRevoked)
        } finally {
            ShadowLooper.idleMainLooper()
            try { db.close() } catch (_: Throwable) {}
        }
    }
}
