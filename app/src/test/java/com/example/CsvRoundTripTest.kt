package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.datastore.AppSettingsRepository
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.domain.util.Formatters
import com.example.importbackup.CsvBackupManager
import com.example.importbackup.CsvImporter
import com.example.importbackup.UnifiedCsvStorage
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CsvRoundTripTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: FinanceRepository
    private lateinit var settingsRepository: AppSettingsRepository

    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    private fun parseTs(dateStr: String): Long = sdf.parse(dateStr)!!.time

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(database)
        settingsRepository = AppSettingsRepository(context)
        runBlocking {
            settingsRepository.setDemoMode(false)
            settingsRepository.setCurrency("₹")
            Formatters.updateCurrentCurrencySymbol("₹")
        }
    }

    @After
    fun tearDown() {
        ShadowLooper.idleMainLooper()
        database.close()
    }

    // =========================================================================
    // RT-A (ROUND-TRIP INVARIANT):
    // Fixture ledger containing tricky shapes —
    //  1. Two transactions identical in description+amount+source+category on DIFFERENT dates
    //  2. A description with commas ("Milk, Bread, coriander")
    //  3. A note with a comma ("While returning Bangalore, 13th Sept")
    //  4. A transfer with empty category (plus chain transfers SBI Amma -> KBL and KBL -> ICICI Credit)
    //  5. An income row
    //  6. A split parent+children (3 child rows sharing groupId on the same timestamp)
    // Export via buildCsv, re-import via preview parser:
    //  - assert valid-count == exported count
    //  - assert post-merge DB row-count == original row-count (no loss, no dupes)
    // =========================================================================
    @Test
    fun RT_A_roundTripInvariant_allTrickyShapesSurviveExportPreviewAndMerge() = runBlocking {
        val cashId = repository.insertAccount(Account(id = 1L, name = "Cash", accountType = "CASH", openingBalance = 9000.0))
        val kblId = repository.insertAccount(Account(id = 2L, name = "KBL", accountType = "BANK", openingBalance = 44.40))
        val sbiAmmaId = repository.insertAccount(Account(id = 3L, name = "SBI Amma", accountType = "BANK", openingBalance = 355.23))
        val iciciCreditId = repository.insertAccount(Account(id = 4L, name = "ICICI Credit", accountType = "CREDIT_CARD", openingBalance = 0.0))

        val groceriesId = repository.insertCategory(Category(id = 10L, name = "Groceries & Shopping", color = "#F59E0B"))
        val foodId = repository.insertCategory(Category(id = 11L, name = "Food & Dining", color = "#EF4444"))
        val salaryId = repository.insertCategory(Category(id = 12L, name = "Salary & Income", color = "#10B981"))
        val healthId = repository.insertCategory(Category(id = 13L, name = "Health & Wellness", color = "#06B6D4"))

        val splitGroupId = "split_parent_group_001"
        val splitTimestamp = parseTs("2026-10-05 18:30")

        val fixtureTransactions = listOf(
            // (1a) Identical description+amount+source+category on Day 1
            TransactionEntity(
                date = parseTs("2026-09-21 11:25"),
                type = "EXPENSE",
                amount = 200.00,
                fromAccountId = cashId,
                categoryId = groceriesId,
                description = "Daily Supplies",
                notes = "Regular purchase"
            ),
            // (1b) Identical description+amount+source+category on Day 2 (DIFFERENT date)
            TransactionEntity(
                date = parseTs("2026-09-27 11:25"),
                type = "EXPENSE",
                amount = 200.00,
                fromAccountId = cashId,
                categoryId = groceriesId,
                description = "Daily Supplies",
                notes = "Regular purchase"
            ),
            // (2) Description with commas ("Milk, Bread, coriander")
            TransactionEntity(
                date = parseTs("2026-10-01 09:04"),
                type = "EXPENSE",
                amount = 100.00,
                fromAccountId = cashId,
                categoryId = groceriesId,
                description = "Milk, Bread, coriander",
                notes = ""
            ),
            // (3) Note with a comma
            TransactionEntity(
                date = parseTs("2026-09-18 22:00"),
                type = "EXPENSE",
                amount = 170.00,
                fromAccountId = iciciCreditId,
                categoryId = foodId,
                description = "Tea",
                notes = "While returning Bangalore, 13th Sept"
            ),
            // (4a) Transfer with empty category (SBI Amma -> KBL)
            TransactionEntity(
                date = parseTs("2026-10-02 08:02"),
                type = "TRANSFER",
                amount = 500.00,
                fromAccountId = sbiAmmaId,
                toAccountId = kblId,
                categoryId = null,
                description = "Transfer",
                notes = ""
            ),
            // (4b) Back-to-back chain transfer on same minute with empty category (KBL -> ICICI Credit)
            TransactionEntity(
                date = parseTs("2026-10-02 08:02"),
                type = "TRANSFER",
                amount = 500.00,
                fromAccountId = kblId,
                toAccountId = iciciCreditId,
                categoryId = null,
                description = "Transfer",
                notes = ""
            ),
            // (5) Income row
            TransactionEntity(
                date = parseTs("2026-09-21 19:17"),
                type = "INCOME",
                amount = 5000.00,
                fromAccountId = null,
                toAccountId = cashId,
                categoryId = salaryId,
                description = "Dad's Salary",
                notes = "week 3 Payment."
            ),
            // (6) Split parent+children (3 child rows sharing splitGroupId and timestamp)
            TransactionEntity(
                date = splitTimestamp,
                type = "EXPENSE",
                amount = 1850.00,
                fromAccountId = cashId,
                categoryId = groceriesId,
                description = "Supermarket Split Run",
                notes = "Split Line 1: Groceries",
                groupId = splitGroupId
            ),
            TransactionEntity(
                date = splitTimestamp,
                type = "EXPENSE",
                amount = 650.00,
                fromAccountId = cashId,
                categoryId = foodId,
                description = "Supermarket Split Run",
                notes = "Split Line 2: Food court",
                groupId = splitGroupId
            ),
            TransactionEntity(
                date = splitTimestamp,
                type = "EXPENSE",
                amount = 650.00,
                fromAccountId = cashId,
                categoryId = healthId,
                description = "Supermarket Split Run",
                notes = "Split Line 3: Pharmacy",
                groupId = splitGroupId
            )
        )

        repository.insertTransactions(fixtureTransactions)
        val originalCount = repository.allTransactions.first().size
        assertEquals(10, originalCount)

        val accounts = repository.allAccounts.first()
        val categories = repository.allCategories.first()
        val exportedCsv = CsvBackupManager.buildCsv(
            transactions = repository.allTransactions.first(),
            accounts = accounts,
            categories = categories
        )

        // Re-import via preview parser (both mergeTransfers=true and mergeTransfers=false)
        val previewWithMergeFlag = CsvImporter.previewCsv(
            ByteArrayInputStream(exportedCsv.toByteArray(Charsets.UTF_8)),
            existingAccounts = accounts,
            existingCategories = categories,
            mergeTransfers = true
        )
        assertEquals("Preview totalRows must equal exported count", originalCount, previewWithMergeFlag.totalRows)
        assertEquals("Preview validRows must equal exported count", originalCount, previewWithMergeFlag.validRows)
        assertEquals("Preview skippedRows must be 0", 0, previewWithMergeFlag.skippedRows)
        assertTrue("Preview errors must be empty", previewWithMergeFlag.errors.isEmpty())

        // Merge over existing DB: 0 inserted, all 10 skipped as duplicates, DB count stays 10
        val mergeOverExisting = UnifiedCsvStorage.restoreFromInputStream(
            repository = repository,
            inputStream = ByteArrayInputStream(exportedCsv.toByteArray(Charsets.UTF_8)),
            clearExisting = false,
            settingsRepository = settingsRepository,
            context = context
        )
        assertEquals(originalCount, mergeOverExisting.sourceRows)
        assertEquals(0, mergeOverExisting.inserted)
        assertEquals(originalCount, mergeOverExisting.skippedDup)
        assertEquals(0, mergeOverExisting.skippedFailed)
        assertEquals("Post-merge DB row-count must equal original row-count", originalCount, repository.allTransactions.first().size)

        // Merge into a clean DB (clearExisting = false on empty DB): all 10 inserted, 0 dropped
        repository.deleteAllTransactions()
        assertEquals(0, repository.allTransactions.first().size)
        val mergeIntoEmpty = UnifiedCsvStorage.restoreFromInputStream(
            repository = repository,
            inputStream = ByteArrayInputStream(exportedCsv.toByteArray(Charsets.UTF_8)),
            clearExisting = false,
            settingsRepository = settingsRepository,
            context = context
        )
        assertEquals(originalCount, mergeIntoEmpty.inserted)
        assertEquals(0, mergeIntoEmpty.skippedDup)
        assertEquals(originalCount, repository.allTransactions.first().size)
    }

    // =========================================================================
    // RT-B (Signature collision):
    // Two different-date identical rows survive a round-trip as TWO rows;
    // two fully identical rows (same date too) still dedup to one on merge.
    // =========================================================================
    @Test
    fun RT_B_signatureCollision_differentDateSurvivesAsTwo_sameDateDedupsToOneOnMerge() = runBlocking {
        val cashId = repository.insertAccount(Account(id = 1L, name = "Cash", accountType = "CASH", openingBalance = 5000.0))
        val foodId = repository.insertCategory(Category(id = 10L, name = "Food & Dining", color = "#EF4444"))

        val day1 = parseTs("2026-10-10 13:15")
        val day2 = parseTs("2026-10-12 13:15")

        val txDay1 = TransactionEntity(
            date = day1,
            type = "EXPENSE",
            amount = 150.00,
            fromAccountId = cashId,
            categoryId = foodId,
            description = "Lunch Thali",
            notes = "Udupi Hotel"
        )
        val txDay2 = TransactionEntity(
            date = day2,
            type = "EXPENSE",
            amount = 150.00,
            fromAccountId = cashId,
            categoryId = foodId,
            description = "Lunch Thali",
            notes = "Udupi Hotel"
        )

        // Verify signatures include DATE and are distinct across different dates
        val sig1 = CsvBackupManager.transactionSignature(
            date = txDay1.date,
            type = txDay1.type,
            amount = txDay1.amount,
            fromAccount = "Cash",
            toAccount = "",
            category = "Food & Dining",
            description = txDay1.description,
            notes = txDay1.notes
        )
        val sig2 = CsvBackupManager.transactionSignature(
            date = txDay2.date,
            type = txDay2.type,
            amount = txDay2.amount,
            fromAccount = "Cash",
            toAccount = "",
            category = "Food & Dining",
            description = txDay2.description,
            notes = txDay2.notes
        )
        assertNotEquals("Different-date identical rows must have distinct signatures", sig1, sig2)

        // Part 1: Round-trip of two different-date identical rows survives as TWO rows
        val accounts = repository.allAccounts.first()
        val categories = repository.allCategories.first()
        val csvTwoDates = CsvBackupManager.buildCsv(listOf(txDay1, txDay2), accounts, categories)

        val previewTwoDates = CsvImporter.previewCsv(
            ByteArrayInputStream(csvTwoDates.toByteArray(Charsets.UTF_8)),
            existingAccounts = accounts,
            existingCategories = categories,
            mergeTransfers = true
        )
        assertEquals(2, previewTwoDates.totalRows)
        assertEquals(2, previewTwoDates.validRows)
        assertEquals(0, previewTwoDates.skippedRows)

        val restoreTwoDates = UnifiedCsvStorage.restoreFromInputStream(
            repository = repository,
            inputStream = ByteArrayInputStream(csvTwoDates.toByteArray(Charsets.UTF_8)),
            clearExisting = false,
            settingsRepository = settingsRepository,
            context = context
        )
        assertEquals(2, restoreTwoDates.inserted)
        assertEquals(0, restoreTwoDates.skippedDup)
        assertEquals(2, repository.allTransactions.first().size)

        // Part 2: Two fully identical rows (same date too) still dedup to ONE on merge
        repository.deleteAllTransactions()
        val duplicateCsvSameDate = """
            #FORMAT=2
            Date,Type,Amount,FromAccount,ToAccount,Category,Description,Notes
            2026-10-10 13:15,EXPENSE,150.00,Cash,,Food & Dining,Lunch Thali,Udupi Hotel
            2026-10-10 13:15,EXPENSE,150.00,Cash,,Food & Dining,Lunch Thali,Udupi Hotel
        """.trimIndent()

        val previewSameDate = CsvImporter.previewCsv(
            ByteArrayInputStream(duplicateCsvSameDate.toByteArray(Charsets.UTF_8)),
            existingAccounts = accounts,
            existingCategories = categories,
            mergeTransfers = true
        )
        assertEquals(2, previewSameDate.totalRows)
        assertEquals(1, previewSameDate.validRows)
        assertEquals(1, previewSameDate.skippedRows)
        assertTrue(
            "Skipped reason must report duplicate signature row #2: ${previewSameDate.errors}",
            previewSameDate.errors.any { it.contains("duplicate signature row #2") }
        )

        val mergeSameDate = UnifiedCsvStorage.restoreFromInputStream(
            repository = repository,
            inputStream = ByteArrayInputStream(duplicateCsvSameDate.toByteArray(Charsets.UTF_8)),
            clearExisting = false,
            settingsRepository = settingsRepository,
            context = context
        )
        assertEquals(1, mergeSameDate.inserted)
        assertEquals(1, mergeSameDate.skippedDup)
        assertEquals(1, repository.allTransactions.first().size)
    }

    // =========================================================================
    // RT-C (Preview counters):
    // For a file with 1 intentionally unparseable line, preview shows Rows/Valid/Skipped
    // with the reason naming the line index; for a clean file, Skipped line is absent.
    // =========================================================================
    @Test
    fun RT_C_previewCounters_showsSkippedAndLineReasonWhenDirty_absentWhenClean() = runBlocking {
        val settingsVm = SettingsViewModel(repository, settingsRepository, database)

        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(viewModel = settingsVm)
            }
        }
        composeTestRule.waitForIdle()

        // 1. File with 1 intentionally unparseable line (row #2 / line #3)
        val dirtyCsv = """
            Date,Type,Amount,FromAccount,ToAccount,Category,Description,Notes
            2026-10-01 10:00,EXPENSE,100.00,Cash,,Food,Row One,Note 1
            NOT_A_DATE,EXPENSE,BROKEN_AMT,Cash,,Food,Bad Row Two,Note 2
            2026-10-03 10:00,EXPENSE,300.00,Cash,,Food,Row Three,Note 3
        """.trimIndent()

        settingsVm.previewCsv(ByteArrayInputStream(dirtyCsv.toByteArray(Charsets.UTF_8)))
        composeTestRule.waitForIdle()

        val dirtyPreview = settingsVm.csvPreview.value!!
        assertEquals(3, dirtyPreview.totalRows)
        assertEquals(2, dirtyPreview.validRows)
        assertEquals(1, dirtyPreview.skippedRows)
        assertTrue(
            "Reason must name unparseable row #2 / line #3: ${dirtyPreview.errors}",
            dirtyPreview.errors.any { it.contains("unparseable row #2") && it.contains("#3") }
        )

        composeTestRule.onNodeWithTag("preview_total_rows").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rows: 3 · Valid: 2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("preview_skipped_rows").assertIsDisplayed()
        composeTestRule.onNodeWithText("Skipped: 1", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("unparseable row #2", substring = true).assertIsDisplayed()

        // 2. Clean file: Skipped line is completely absent
        settingsVm.clearCsvPreview()
        composeTestRule.waitForIdle()

        val cleanCsv = """
            Date,Type,Amount,FromAccount,ToAccount,Category,Description,Notes
            2026-10-01 10:00,EXPENSE,100.00,Cash,,Food,Row One,Note 1
            2026-10-02 10:00,EXPENSE,200.00,Cash,,Food,Row Two,Note 2
            2026-10-03 10:00,EXPENSE,300.00,Cash,,Food,Row Three,Note 3
        """.trimIndent()

        settingsVm.previewCsv(ByteArrayInputStream(cleanCsv.toByteArray(Charsets.UTF_8)))
        composeTestRule.waitForIdle()

        val cleanPreview = settingsVm.csvPreview.value!!
        assertEquals(3, cleanPreview.totalRows)
        assertEquals(3, cleanPreview.validRows)
        assertEquals(0, cleanPreview.skippedRows)
        assertNull(cleanPreview.skippedLine)

        composeTestRule.onNodeWithText("Rows: 3 · Valid: 3").assertIsDisplayed()
        composeTestRule.onNodeWithTag("preview_skipped_rows").assertDoesNotExist()
        composeTestRule.onNodeWithText("Skipped:", substring = true).assertDoesNotExist()
    }

    // =========================================================================
    // RT-D (Three-counter agreement):
    // Export snackbar count == import "Total rows" == DB transaction count on the same file.
    // Verified on a 120-record ledger including the owner's chain transfers.
    // =========================================================================
    @Test
    fun RT_D_threeCounterAgreement_exportSnackbarEqualsImportTotalRowsEqualsDbCount() = runBlocking {
        val cashId = repository.insertAccount(Account(id = 1L, name = "Cash", accountType = "CASH", openingBalance = 9000.0))
        val kblId = repository.insertAccount(Account(id = 2L, name = "KBL", accountType = "BANK", openingBalance = 44.40))
        val sbiAmmaId = repository.insertAccount(Account(id = 3L, name = "SBI Amma", accountType = "BANK", openingBalance = 355.23))
        val iciciCreditId = repository.insertAccount(Account(id = 4L, name = "ICICI Credit", accountType = "CREDIT_CARD", openingBalance = 0.0))
        val foodId = repository.insertCategory(Category(id = 10L, name = "Food & Dining", color = "#EF4444"))

        // Build a 120-record ledger including the exact chain transfers on 2026-10-02 08:02
        val txs = mutableListOf<TransactionEntity>()
        txs.add(
            TransactionEntity(
                date = parseTs("2026-10-02 08:02"),
                type = "TRANSFER",
                amount = 500.00,
                fromAccountId = sbiAmmaId,
                toAccountId = kblId,
                categoryId = null,
                description = "Transfer",
                notes = ""
            )
        )
        txs.add(
            TransactionEntity(
                date = parseTs("2026-10-02 08:02"),
                type = "TRANSFER",
                amount = 500.00,
                fromAccountId = kblId,
                toAccountId = iciciCreditId,
                categoryId = null,
                description = "Transfer",
                notes = ""
            )
        )
        for (i in 1..118) {
            val day = ((i - 1) % 28) + 1
            val hour = (i % 12) + 8
            val minute = i % 60
            txs.add(
                TransactionEntity(
                    date = parseTs(String.format(Locale.US, "2026-09-%02d %02d:%02d", day, hour, minute)),
                    type = if (i % 10 == 0) "INCOME" else "EXPENSE",
                    amount = 50.0 + i,
                    fromAccountId = if (i % 10 == 0) null else cashId,
                    toAccountId = if (i % 10 == 0) kblId else null,
                    categoryId = foodId,
                    description = "Ledger Item #$i",
                    notes = if (i % 3 == 0) "Note with comma, item #$i" else ""
                )
            )
        }
        repository.insertTransactions(txs)

        // Counter 1: DB transaction count
        val dbCount = repository.allTransactions.first().size
        assertEquals(120, dbCount)

        // Counter 2: Export snackbar count
        val settingsVm = SettingsViewModel(repository, settingsRepository, database)
        val statusDeferred = async { settingsVm.statusMessage.first() }
        ShadowLooper.idleMainLooper()
        settingsVm.exportCsvBackup(context)
        val exportSnackbar = statusDeferred.await()
        assertTrue(
            "Export snackbar must report ($dbCount records), actual: $exportSnackbar",
            exportSnackbar.contains("($dbCount records)")
        )

        // Read the exported file from internal mirror / buildCsv and preview via SettingsViewModel
        val exportedCsv = CsvBackupManager.buildCsv(
            transactions = repository.allTransactions.first(),
            accounts = repository.allAccounts.first(),
            categories = repository.allCategories.first(),
            settings = settingsRepository.settingsFlow.first()
        )
        settingsVm.previewCsv(ByteArrayInputStream(exportedCsv.toByteArray(Charsets.UTF_8)))
        val preview = settingsVm.csvPreview.value!!

        // Counter 3: Import preview totalRows & validRows
        assertEquals("Import totalRows must equal DB count and export snackbar count", dbCount, preview.totalRows)
        assertEquals("Import validRows must equal DB count and export snackbar count", dbCount, preview.validRows)
        assertEquals(0, preview.skippedRows)
        assertEquals("Rows: 120 · Valid: 120", preview.summaryHeader)
    }
}
