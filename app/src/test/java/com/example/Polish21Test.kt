package com.example

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.DatabaseProvider
import com.example.data.datastore.AppSettingsRepository
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.data.repository.MutationTracker
import com.example.importbackup.CsvBackupManager
import com.example.importbackup.MediaStoreWriter
import com.example.importbackup.UnifiedCsvStorage
import com.example.ui.onboarding.OnboardingSourceScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class Polish21Test {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: FinanceRepository
    private lateinit var settingsRepository: AppSettingsRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(database)
        settingsRepository = AppSettingsRepository(context)
        runBlocking {
            File(context.filesDir, com.example.data.StorageConstants.DEMO_MARKER_FILE).delete()
            settingsRepository.clearSettings()
            settingsRepository.setDemoMode(false)
            settingsRepository.setAutoSaveTreeUri("")
            settingsRepository.setAutoBackupEnabled(true)
        }
    }

    @After
    fun tearDown() {
        ShadowLooper.idleMainLooper()
        runBlocking {
            File(context.filesDir, com.example.data.StorageConstants.DEMO_MARKER_FILE).delete()
            settingsRepository.clearSettings()
        }
    }

    private fun buildSeedCsv(rowCount: Int, descriptionPrefix: String): String {
        val acc = Account(id = 1L, name = "Main Bank", accountType = "BANK", openingBalance = 10000.0)
        val cat = Category(id = 1L, name = "General", color = "#10B981")
        val baseTime = 1729700000000L
        val txs = (1..rowCount).map { idx ->
            TransactionEntity(
                id = idx.toLong(),
                date = baseTime + idx * 60_000L,
                type = "EXPENSE",
                amount = 10.0 + idx,
                fromAccountId = 1L,
                categoryId = 1L,
                description = "${descriptionPrefix}_row_$idx"
            )
        }
        return CsvBackupManager.buildCsv(
            transactions = txs,
            accounts = listOf(acc),
            categories = listOf(cat)
        )
    }

    // =========================================================================
    // T1 (F1): Seed a 120-row file in SAF tree + a 50-row file in
    // Downloads/SilentSums; restore picks the 120-row file (not the 50-row shadow).
    // =========================================================================
    @Test
    fun t1_safTree120RowsBeatsDownloads50RowsAcrossAllReadDoors() {
        runBlocking {
            val pubDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            pubDownloads.deleteRecursively()
            File(context.filesDir, "backups").deleteRecursively()

            val safDir = File(context.cacheDir, "saf_t1_root/Internal storage/SilentSums").apply {
                deleteRecursively()
                mkdirs()
            }
            val safUriStr = Uri.fromFile(safDir).toString()
            settingsRepository.setAutoSaveTreeUri(safUriStr)

            val csv120 = buildSeedCsv(120, "SAF_120")
            val csv50 = buildSeedCsv(50, "DOWNLOADS_50")

            // Seed 50-row file in Downloads/SilentSums/expense_tracker.csv AND Downloads/SilentSums/auto/backup.csv
            File(pubDownloads, "SilentSums/expense_tracker.csv").apply {
                parentFile?.mkdirs()
                writeText(csv50)
            }
            File(pubDownloads, "SilentSums/auto/backup.csv").apply {
                parentFile?.mkdirs()
                writeText(csv50)
            }

            // Seed 120-row file in SAF tree auto/backup.csv (while Downloads has 50-row master + auto)
            File(safDir, "auto/backup.csv").apply {
                parentFile?.mkdirs()
                writeText(csv120)
            }

            // Door 1: UnifiedCsvStorage.restoreFromMobileStorage (via openStoredCsvInputStream)
            val res1 = UnifiedCsvStorage.restoreFromMobileStorage(
                context = context,
                repository = repository,
                clearExisting = true,
                settingsRepository = settingsRepository
            )
            assertEquals("restoreFromMobileStorage must pick 120-row SAF file over 50-row Downloads shadow", 120, res1.inserted)
            assertEquals(120, repository.allTransactions.first().size)

            // Door 2: MediaStoreWriter.readAutoSave
            val autoReadText = MediaStoreWriter.readAutoSave(context)!!.use {
                String(it.readBytes(), Charsets.UTF_8)
            }
            assertTrue("MediaStoreWriter.readAutoSave must return 120-row SAF file", autoReadText.contains("SAF_120_row_120"))
            assertFalse("MediaStoreWriter.readAutoSave must NOT return 50-row Downloads shadow", autoReadText.contains("DOWNLOADS_50_row_1"))

            // Door 3: UnifiedCsvStorage.restoreFromAutoSave (via openAutoSaveInputStream)
            val res2 = UnifiedCsvStorage.restoreFromAutoSave(
                context = context,
                repository = repository,
                clearExisting = true,
                settingsRepository = settingsRepository
            )
            assertEquals("restoreFromAutoSave must pick 120-row SAF file", 120, res2.inserted)
        }
    }

    // =========================================================================
    // T2 (F1): Seed only a file in Downloads/SilentSums (no SAF tree);
    // restore picks it (fallback works).
    // =========================================================================
    @Test
    fun t2_noSafTree_restorePicksDownloadsSilentSumsFallback() {
        runBlocking {
            val pubDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            pubDownloads.deleteRecursively()
            File(context.filesDir, "backups").deleteRecursively()
            settingsRepository.setAutoSaveTreeUri("")

            val csv50 = buildSeedCsv(50, "DOWNLOADS_ONLY_50")
            File(pubDownloads, "SilentSums/expense_tracker.csv").apply {
                parentFile?.mkdirs()
                writeText(csv50)
            }

            val res = UnifiedCsvStorage.restoreFromMobileStorage(
                context = context,
                repository = repository,
                clearExisting = true,
                settingsRepository = settingsRepository
            )
            assertEquals("Fallback to Downloads/SilentSums must restore 50 rows when no SAF tree set", 50, res.inserted)
            assertEquals(50, repository.allTransactions.first().size)
        }
    }

    // =========================================================================
    // T3 (F2): Settings "Data folder" row visible when autoBackupEnabled=false;
    // Reset present always, enabled only when custom tree set and demo off.
    // =========================================================================
    @Test
    fun t3_settingsDataFolderRow_visibleWhenAutoBackupOff_andResetAlwaysPresentWithCorrectEnabledState() {
        runBlocking {
            settingsRepository.setAutoBackupEnabled(false)
            settingsRepository.setAutoSaveTreeUri("")
            settingsRepository.setDemoMode(false)

            val settingsVm = SettingsViewModel(repository, settingsRepository, database)
            composeTestRule.setContent {
                MyApplicationTheme {
                    SettingsScreen(viewModel = settingsVm)
                }
            }
            composeTestRule.waitForIdle()

            // 1. Even with autoBackupEnabled = false, "Data folder" row is visible
            composeTestRule.onNodeWithText("Data folder").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("backup_location_current_text").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("backup_location_change_button").performScrollTo().assertIsDisplayed().assertIsEnabled()

            // 2. Reset button is ALWAYS present, but disabled when custom tree is blank
            composeTestRule.onNodeWithTag("backup_location_reset_button").performScrollTo().assertIsDisplayed().assertIsNotEnabled()

            // 3. Set a custom tree URI -> Reset becomes enabled (while demoMode is false)
            val safDir = File(context.cacheDir, "saf_t3_root/Internal storage/SilentSums").apply { mkdirs() }
            settingsRepository.setAutoSaveTreeUri(Uri.fromFile(safDir).toString())
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("backup_location_reset_button").performScrollTo().assertIsDisplayed().assertIsEnabled()
        }
    }

    // =========================================================================
    // T4 (F3): Manual save with Daily Auto-Save OFF leaves
    // filesDir/backups/auto_backup.csv current, byte-identical to shared copy,
    // and does NOT bump MutationTracker stamp.
    // =========================================================================
    @Test
    fun t4_internalSafetyNet_populatedOnManualSaveWithAutoBackupOff() {
        runBlocking {
            val pubDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            pubDownloads.deleteRecursively()
            val internalSafetyFile = File(context.filesDir, "backups/auto_backup.csv")
            internalSafetyFile.parentFile?.deleteRecursively()

            settingsRepository.setAutoBackupEnabled(false)
            settingsRepository.setAutoSaveTreeUri("")

            val accId = repository.insertAccount(Account(id = 1L, name = "HDFC", accountType = "BANK", openingBalance = 5000.0))
            val catId = repository.insertCategory(Category(id = 1L, name = "Food", color = "#EF4444"))
            val tx = TransactionEntity(
                id = 1L,
                date = System.currentTimeMillis(),
                type = "EXPENSE",
                amount = 325.0,
                fromAccountId = accId,
                categoryId = catId,
                description = "MANUAL_SAVE_SAFETY_NET_ROW"
            )
            repository.insertTransaction(tx)

            val stampBeforeManualSave = MutationTracker.currentStamp

            // Manual Save/Sync while Daily Auto-Save is OFF
            val settings = settingsRepository.settingsFlow.first()
            UnifiedCsvStorage.saveToDeviceCsv(
                context = context,
                transactions = listOf(tx),
                accounts = repository.allAccounts.first(),
                categories = repository.allCategories.first(),
                settings = settings,
                settingsRepository = settingsRepository
            )

            val stampAfterManualSave = MutationTracker.currentStamp
            assertEquals("Manual Save/Sync must NOT bump MutationTracker stamp", stampBeforeManualSave, stampAfterManualSave)

            assertTrue("filesDir/backups/auto_backup.csv must exist after manual Save/Sync even with Daily Auto-Save OFF", internalSafetyFile.exists())
            val sharedFile = File(pubDownloads, "SilentSums/expense_tracker.csv")
            val internalText = internalSafetyFile.readText()
            val sharedText = sharedFile.readText()
            assertTrue("Internal safety net must contain real transaction rows", UnifiedCsvStorage.hasRealTransactionRows(internalText))
            assertTrue("Internal safety net must contain the saved row", internalText.contains("MANUAL_SAVE_SAFETY_NET_ROW"))
            assertEquals("Internal safety net must be byte-identical to shared copy", sharedText, internalText)
        }
    }

    // =========================================================================
    // T5 (F4): Onboarding strings match the spec byte-for-byte;
    // "Skip for now" is absent.
    // =========================================================================
    @Test
    fun t5_onboardingBackupLocationCopy_matchesSpecByteForByteAndSkipAbsent() {
        composeTestRule.setContent {
            MyApplicationTheme {
                OnboardingSourceScreen(
                    repository = repository,
                    settingsRepository = settingsRepository,
                    onOnboardingComplete = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Navigate: Start Fresh -> Use defaults & continue -> Folder step
        composeTestRule.onNodeWithTag("onboarding_start_fresh").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("use_defaults_button").performScrollTo().performClick()
        ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(5000) {
            ShadowLooper.idleMainLooper()
            try {
                composeTestRule.onNodeWithTag("onboarding_backup_location_title").assertIsDisplayed()
                true
            } catch (_: Throwable) {
                false
            }
        }

        // Title byte-for-byte
        composeTestRule.onNodeWithText("Where should your data files live?").performScrollTo().assertIsDisplayed()

        // Subtitle byte-for-byte
        composeTestRule.onNodeWithText(
            "Your ledger file, daily auto-backups, and filtered exports all save to this folder so you can open them on a computer or back them up to the cloud."
        ).performScrollTo().assertIsDisplayed()

        // Info card byte-for-byte: existing privacy warning FIRST + convenience line
        composeTestRule.onNodeWithText(
            "Files in shared folders can be readable by other apps with storage permission. Your live ledger stays safely inside the app. You can open these files in any spreadsheet app."
        ).performScrollTo().assertIsDisplayed()

        // Buttons & hint note
        composeTestRule.onNodeWithText("Keep Default (Downloads)").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Pick My Own Folder").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("You can change this anytime in Settings.").performScrollTo().assertIsDisplayed()

        // "Skip for now" must be absent
        composeTestRule.onNodeWithTag("onboarding_location_skip").assertDoesNotExist()
        composeTestRule.onNodeWithText("Skip for now").assertDoesNotExist()
    }

    // =========================================================================
    // T6a (F5 Test 1): Enter demo, assert Change & Reset buttons are disabled,
    // exit demo, assert they re-enable.
    // =========================================================================
    @Test
    fun t6a_demoMode_disablesChangeAndReset_andReEnablesOnDemoExit() {
        runBlocking {
            val safDir = File(context.cacheDir, "saf_t6a_root/Internal storage/SilentSums").apply { mkdirs() }
            val customUriStr = Uri.fromFile(safDir).toString()
            settingsRepository.setAutoSaveTreeUri(customUriStr)
            settingsRepository.setAutoBackupEnabled(false)
            settingsRepository.setDemoMode(false)

            val settingsVm = SettingsViewModel(repository, settingsRepository, database)

            composeTestRule.setContent {
                MyApplicationTheme {
                    SettingsScreen(viewModel = settingsVm)
                }
            }
            composeTestRule.waitForIdle()

            // Pre-demo: both Change and Reset are enabled
            composeTestRule.onNodeWithTag("backup_location_change_button").performScrollTo().assertIsEnabled()
            composeTestRule.onNodeWithTag("backup_location_reset_button").performScrollTo().assertIsEnabled()

            // Enter Demo Mode
            settingsRepository.setDemoMode(true)
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            // In Demo Mode: both Change and Reset are disabled
            composeTestRule.onNodeWithTag("backup_location_change_button").performScrollTo().assertIsNotEnabled()
            composeTestRule.onNodeWithTag("backup_location_reset_button").performScrollTo().assertIsNotEnabled()

            // Exit Demo Mode
            settingsRepository.setDemoMode(false)
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            // Post-demo: both Change and Reset re-enable
            composeTestRule.onNodeWithTag("backup_location_change_button").performScrollTo().assertIsEnabled()
            composeTestRule.onNodeWithTag("backup_location_reset_button").performScrollTo().assertIsEnabled()
        }
    }

    // =========================================================================
    // T6b (F5 Test 2): Enter demo, exit demo, and assert autoSaveTreeUri
    // survived unchanged (and service-layer guards block mutations during demo).
    // =========================================================================
    @Test
    fun t6b_demoMode_preservesAutoSaveTreeUriAcrossDemoExitAndBlocksMutationsInDemo() {
        runBlocking {
            val safDir = File(context.cacheDir, "saf_t6b_root/Internal storage/SilentSums").apply { mkdirs() }
            val customUriStr = Uri.fromFile(safDir).toString()
            settingsRepository.setAutoSaveTreeUri(customUriStr)
            settingsRepository.setDemoMode(false)

            val settingsVm = SettingsViewModel(repository, settingsRepository, database)

            // Enter Demo Mode
            File(context.filesDir, com.example.data.StorageConstants.DEMO_MARKER_FILE).writeText("demo")
            settingsRepository.setDemoMode(true)
            assertTrue(settingsRepository.settingsFlow.first().demoMode)

            // Attempt mutations via SettingsViewModel while in Demo Mode -> must be blocked by service-layer guard
            settingsVm.setAutoSaveTreeUri("content://should_be_ignored")
            settingsVm.resetAutoSaveTreeUri(context)
            ShadowLooper.idleMainLooper()
            assertEquals("Service-layer guard must protect autoSaveTreeUri in demo mode", customUriStr, settingsRepository.settingsFlow.first().autoSaveTreeUri)

            // Exit Demo Mode (mirroring DatabaseProvider.flipTo(..., targetDemoMode = false) steps)
            File(context.filesDir, com.example.data.StorageConstants.DEMO_MARKER_FILE).delete()
            settingsRepository.setDemoMode(false)
            DatabaseProvider.purgeDemoLeftovers(context)
            assertFalse(settingsRepository.settingsFlow.first().demoMode)

            // Assert autoSaveTreeUri survived unchanged
            assertEquals("autoSaveTreeUri must survive demo exit unchanged", customUriStr, settingsRepository.settingsFlow.first().autoSaveTreeUri)
        }
    }
}
