package com.example

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.datastore.AppSettingsRepository
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.domain.calculator.LedgerCalculator
import com.example.domain.model.FilterState
import com.example.importbackup.MediaStoreWriter
import com.example.importbackup.UnifiedCsvStorage
import com.example.ui.common.FilterChipsRow
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.ledger.LedgerViewModel
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.worker.AutoBackupWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class Polish2Test {

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
            settingsRepository.clearSettings()
            settingsRepository.setDemoMode(false)
            settingsRepository.setAutoSaveTreeUri("")
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            settingsRepository.clearSettings()
            database.close()
        }
    }

    // =========================================================================
    // T1: With valid SAF tree set, Save/Sync, auto-save, and filtered export
    // ALL write into that tree (expense_tracker.csv, auto/backup.csv,
    // downloads/filtered_export_*.csv) and NOT MediaStore.
    // =========================================================================
    @Test
    fun t1_validSafTree_routesAllThreeWritersIntoSafTreeAndNotMediaStore() = runBlocking {
        val pubDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        File(pubDownloads, MediaStoreWriter.ROOT_FOLDER).deleteRecursively()

        val safTempParent = File(context.cacheDir, "saf_test_root/Internal storage/SilentSums").apply {
            deleteRecursively()
            mkdirs()
        }
        val safTreeUriStr = Uri.fromFile(safTempParent).toString()
        settingsRepository.setAutoSaveTreeUri(safTreeUriStr)
        val settings = settingsRepository.settingsFlow.first()

        val accId = repository.insertAccount(Account(id = 1L, name = "ICICI", accountType = "BANK", openingBalance = 10000.0))
        val catId = repository.insertCategory(Category(id = 1L, name = "Groceries", color = "#10B981"))
        val tx = TransactionEntity(
            id = 1L,
            date = System.currentTimeMillis(),
            type = "EXPENSE",
            amount = 450.0,
            fromAccountId = accId,
            categoryId = catId,
            description = "Market"
        )
        repository.insertTransaction(tx)

        val resolvedRoot = MediaStoreWriter.resolveWriteRoot(context, safTreeUriStr)
        assertTrue("resolveWriteRoot must resolve to WriteRoot.Saf", resolvedRoot is MediaStoreWriter.WriteRoot.Saf)
        assertEquals("Internal storage/SilentSums", resolvedRoot.displayPath)

        // 1. Master Save/Sync
        val savePath = UnifiedCsvStorage.saveToDeviceCsv(
            context = context,
            transactions = listOf(tx),
            accounts = repository.allAccounts.first(),
            categories = repository.allCategories.first(),
            settings = settings,
            settingsRepository = settingsRepository
        )
        assertEquals("Internal storage/SilentSums/expense_tracker.csv", savePath)
        assertTrue("Master expense_tracker.csv must exist in SAF tree", File(safTempParent, "expense_tracker.csv").exists())
        assertFalse(
            "Master expense_tracker.csv must NOT be written to MediaStore Downloads",
            File(pubDownloads, "${MediaStoreWriter.ROOT_FOLDER}/expense_tracker.csv").exists()
        )

        // 2. Auto-save (auto/backup.csv)
        val autoMsg = AutoBackupWorker.runSynchronously(context, database, repository, settingsRepository)
        assertTrue(
            "Auto-save message must name Internal storage/SilentSums/auto/backup.csv, got: $autoMsg",
            autoMsg.contains("Internal storage/SilentSums/auto/backup.csv")
        )
        assertTrue("auto/backup.csv must exist in SAF tree", File(safTempParent, "auto/backup.csv").exists())
        assertFalse(
            "auto/backup.csv must NOT be written to MediaStore Downloads",
            File(pubDownloads, "${MediaStoreWriter.ROOT_FOLDER}/auto/backup.csv").exists()
        )

        // 3. Filtered export (downloads/filtered_export_*.csv)
        val ledgerVm = LedgerViewModel(repository, settingsRepository)
        ledgerVm.uiState.awaitState { !it.isLoading && it.dayGroups.isNotEmpty() }
        val exportPath = ledgerVm.exportFilteredCsv(context)
        assertTrue(
            "Filtered export path must start with Internal storage/SilentSums/downloads/filtered_export_, got: $exportPath",
            exportPath.startsWith("Internal storage/SilentSums/downloads/filtered_export_")
        )
        val safExportFiles = File(safTempParent, "downloads").listFiles { f ->
            f.isFile && f.name.startsWith("filtered_export_") && f.name.endsWith(".csv")
        }
        assertTrue("Filtered export file must exist in SAF tree downloads/ subfolder", !safExportFiles.isNullOrEmpty())
        val mediaStoreExportDir = File(pubDownloads, "${MediaStoreWriter.ROOT_FOLDER}/downloads")
        val mediaStoreExports = mediaStoreExportDir.listFiles { f -> f.isFile && f.name.startsWith("filtered_export_") }
        assertTrue("Filtered export must NOT write to MediaStore Downloads when SAF tree is active", mediaStoreExports.isNullOrEmpty())
    }

    // =========================================================================
    // T2: With no tree or revoked grant, all three write to MediaStore
    // Downloads/SilentSums/...
    // =========================================================================
    @Test
    fun t2_noTreeOrRevokedGrant_routesAllThreeWritersToMediaStoreDefault() = runBlocking {
        val pubDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val ownRootDir = File(pubDownloads, MediaStoreWriter.ROOT_FOLDER).apply { deleteRecursively() }

        val accId = repository.insertAccount(Account(id = 1L, name = "SBI", accountType = "BANK", openingBalance = 5000.0))
        val catId = repository.insertCategory(Category(id = 1L, name = "Dining", color = "#3B82F6"))
        val tx = TransactionEntity(
            id = 1L,
            date = System.currentTimeMillis(),
            type = "EXPENSE",
            amount = 250.0,
            fromAccountId = accId,
            categoryId = catId,
            description = "Dinner"
        )
        repository.insertTransaction(tx)

        // Case A: No SAF tree set ("")
        val rootWhenBlank = MediaStoreWriter.resolveWriteRoot(context, "")
        assertTrue(rootWhenBlank is MediaStoreWriter.WriteRoot.MediaStoreDefault)
        assertEquals("Downloads/SilentSums", rootWhenBlank.displayPath)

        // Case B: Revoked/inaccessible SAF grant
        val revokedUri = "content://com.android.externalstorage.documents/tree/primary%3ARevokedFolder"
        val rootWhenRevoked = MediaStoreWriter.resolveWriteRoot(context, revokedUri)
        assertTrue(rootWhenRevoked is MediaStoreWriter.WriteRoot.MediaStoreDefault)
        assertEquals("Downloads/SilentSums", rootWhenRevoked.displayPath)

        settingsRepository.setAutoSaveTreeUri("")
        val settings = settingsRepository.settingsFlow.first()

        // 1. Master Save/Sync
        val savePath = UnifiedCsvStorage.saveToDeviceCsv(
            context = context,
            transactions = listOf(tx),
            accounts = repository.allAccounts.first(),
            categories = repository.allCategories.first(),
            settings = settings,
            settingsRepository = settingsRepository
        )
        assertEquals("Downloads/SilentSums/expense_tracker.csv", savePath)
        assertTrue(File(ownRootDir, "expense_tracker.csv").exists())

        // 2. Auto-save
        val autoMsg = AutoBackupWorker.runSynchronously(context, database, repository, settingsRepository)
        assertTrue(
            "Auto-save message must name Downloads/SilentSums/auto/backup.csv, got: $autoMsg",
            autoMsg.contains("Downloads/SilentSums/auto/backup.csv")
        )
        assertTrue(File(ownRootDir, "auto/backup.csv").exists())

        // 3. Filtered export
        val ledgerVm = LedgerViewModel(repository, settingsRepository)
        ledgerVm.uiState.awaitState { !it.isLoading && it.dayGroups.isNotEmpty() }
        val exportPath = ledgerVm.exportFilteredCsv(context)
        assertTrue(
            "Filtered export path must start with Downloads/SilentSums/downloads/filtered_export_, got: $exportPath",
            exportPath.startsWith("Downloads/SilentSums/downloads/filtered_export_")
        )
        val exportedFiles = File(ownRootDir, "downloads").listFiles { f ->
            f.isFile && f.name.startsWith("filtered_export_") && f.name.endsWith(".csv")
        }
        assertTrue("Filtered export must exist in Downloads/SilentSums/downloads", !exportedFiles.isNullOrEmpty())
    }

    // =========================================================================
    // T3: Subtitles and snackbars render the active root's real path;
    // "Custom Folder"/"Selected Folder" grep-empty; displayPathForRoot unit test.
    // =========================================================================
    @Test
    fun t3_displayPathForRoot_subtitlesAndForbiddenLiteralsGrepEmpty() {
        // 1. Unit-test displayPathForRoot() for both root kinds & max 3 segments
        assertEquals("Downloads/SilentSums", MediaStoreWriter.displayPathForRoot(context, null))
        assertEquals("Downloads/SilentSums", MediaStoreWriter.displayPathForRoot(context, ""))
        assertEquals(
            "Internal storage/SilentSums",
            MediaStoreWriter.displayPathForRoot(
                context,
                "content://com.android.externalstorage.documents/tree/primary%3ASilentSums"
            )
        )
        assertEquals(
            "A/B/SilentSums",
            MediaStoreWriter.displayPathForRoot(
                context,
                "content://com.android.externalstorage.documents/tree/primary%3ARoot%2FA%2FB%2FSilentSums"
            )
        )

        // 2. Verify SettingsScreen subtitles update dynamically between MediaStore default and SAF root
        runBlocking {
            settingsRepository.setAutoBackupEnabled(true)
            settingsRepository.setAutoSaveTreeUri("content://com.android.externalstorage.documents/tree/primary%3ASilentSums")
        }
        val settingsVm = SettingsViewModel(repository, settingsRepository, database)

        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(viewModel = settingsVm)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Internal storage/SilentSums", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Saves single unified file to Internal storage/SilentSums/${com.example.importbackup.UnifiedCsvStorage.CSV_FILE_NAME}", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Scans Internal storage/SilentSums and internal auto backups for the newest snapshot", useUnmergedTree = true).assertExists()

        // 3. Source-code check: "Custom Folder" and "Selected Folder" must not appear anywhere in app/src/main/
        val mainSrcDir = File("src/main/java").let { if (it.exists()) it else File("app/src/main/java") }
        assertTrue("Main source directory must exist", mainSrcDir.exists())
        val offenders = mutableListOf<String>()
        mainSrcDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { idx, line ->
                    if (line.contains("Custom Folder") || line.contains("Selected Folder")) {
                        offenders.add("${file.name}:${idx + 1}: $line")
                    }
                }
            }
        assertTrue("Forbidden phrases 'Custom Folder' / 'Selected Folder' found: $offenders", offenders.isEmpty())
    }

    // =========================================================================
    // T4: Type dropdown is single-select (4 radio options; selecting one
    // replaces prior; no "Types (N)" chip possible; Dashboard mode matches selection).
    // =========================================================================
    @Test
    fun t4_typeDropdown_isSingleSelectRadio_andDashboardModeMatchesSelection() {
        val dashboardVm = DashboardViewModel(repository, settingsRepository)
        var currentFilter by androidx.compose.runtime.mutableStateOf(FilterState())

        composeTestRule.setContent {
            MyApplicationTheme {
                FilterChipsRow(
                    filterState = currentFilter,
                    categories = emptyList(),
                    accounts = emptyList(),
                    onFilterChange = {
                        currentFilter = it
                        dashboardVm.setFilter(it)
                    }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Open Type dropdown -> verify 4 radio options exist
        composeTestRule.onNodeWithTag("filter_chip_type").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("type_option_all").assertIsDisplayed()
        composeTestRule.onNodeWithTag("type_option_expense").assertIsDisplayed()
        composeTestRule.onNodeWithTag("type_option_income").assertIsDisplayed()
        composeTestRule.onNodeWithTag("type_option_transfer").assertIsDisplayed()

        // Select Income -> replaces prior selection, sets Type: Income and Dashboard mode = INCOME
        composeTestRule.onNodeWithTag("type_option_income").performClick()
        composeTestRule.waitForIdle()

        assertEquals("INCOME", currentFilter.selectedType)
        runBlocking {
            assertEquals("INCOME", dashboardVm.uiState.awaitState { it.filter.selectedType == "INCOME" }.activeTypeMode)
        }
        composeTestRule.onNodeWithText("Type: Income").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithText("Types (", substring = true).fetchSemanticsNodes().isEmpty())

        // Open Type dropdown and select Transfer -> replaces Income with Transfer (never "Types (2)")
        composeTestRule.onNodeWithTag("filter_chip_type").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("type_option_transfer").performClick()
        composeTestRule.waitForIdle()

        assertEquals("TRANSFER", currentFilter.selectedType)
        runBlocking {
            assertEquals("TRANSFER", dashboardVm.uiState.awaitState { it.filter.selectedType == "TRANSFER" }.activeTypeMode)
        }
        composeTestRule.onNodeWithText("Type: Transfer").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithText("Types (", substring = true).fetchSemanticsNodes().isEmpty())
    }

    // =========================================================================
    // T5: Tapping an Income "Where money came from" bar navigates to Ledger
    // with Type=Income + Category=<that bar> and filters rows.
    // =========================================================================
    @Test
    fun t5_incomeBarTap_navigatesToLedgerWithTypeIncomeAndCategory_andFiltersRows() {
        var salaryCatId = 10L
        runBlocking {
            val bankId = repository.insertAccount(Account(id = 1L, name = "ICICI", accountType = "BANK", openingBalance = 10000.0))
            salaryCatId = repository.insertCategory(Category(id = 10L, name = "Dad's Salary", color = "#10B981"))
            val refundCatId = repository.insertCategory(Category(id = 20L, name = "Online Shopping", color = "#3B82F6"))

            val now = System.currentTimeMillis()
            repository.insertTransaction(
                TransactionEntity(id = 1L, date = now, type = "INCOME", amount = 60000.0, toAccountId = bankId, categoryId = salaryCatId, description = "Salary Credit")
            )
            repository.insertTransaction(
                TransactionEntity(id = 2L, date = now, type = "INCOME", amount = 1500.0, toAccountId = bankId, categoryId = refundCatId, description = "Refund")
            )
        }

        val dashboardVm = DashboardViewModel(repository, settingsRepository)
        val ledgerVm = LedgerViewModel(repository, settingsRepository)
        dashboardVm.setTypeMode("INCOME")
        runBlocking {
            dashboardVm.uiState.awaitState { it.activeTypeMode == "INCOME" && it.incomeCategoryBreakdown.isNotEmpty() }
        }

        var capturedFilter: FilterState? = null
        composeTestRule.setContent {
            MyApplicationTheme {
                DashboardScreen(
                    viewModel = dashboardVm,
                    onNavigateToExpenses = { _, _ -> },
                    onNavigateToExpensesWithFilter = { filter ->
                        capturedFilter = filter
                        ledgerVm.updateFilter(filter)
                    }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Tap the "Dad's Salary" bar under "Where money came from"
        composeTestRule.scrollToDashboardTag("income_category_bar_Dad's Salary")
            .performClick()
        composeTestRule.waitForIdle()

        assertNotNull("Tapping income category bar must invoke onNavigateToExpensesWithFilter", capturedFilter)
        assertEquals("INCOME", capturedFilter!!.selectedType)
        assertEquals(salaryCatId, capturedFilter!!.selectedCategoryId)
        assertEquals(setOf(salaryCatId), capturedFilter!!.selectedCategoryIds)

        runBlocking {
            val ledgerState = ledgerVm.uiState.awaitState { !it.isLoading && it.filter.selectedCategoryId == salaryCatId }
            val filteredRows = ledgerState.dayGroups.flatMap { it.transactions }
            assertEquals("Ledger must show only the 1 matching income category row", 1, filteredRows.size)
            assertEquals("Salary Credit", filteredRows.first().description)
        }
    }

    // =========================================================================
    // T6: Tapping a Transfer "Where money moved" route navigates to Ledger
    // with Type=Transfer + route chip "From → To" showing only transfers
    // between those two accounts; clearing the chip shows all transfers.
    // =========================================================================
    @Test
    fun t6_transferRouteTap_navigatesWithRouteChip_andClearingChipShowsAllTransfers() {
        var iciciId = 1L
        var kblId = 2L
        runBlocking {
            iciciId = repository.insertAccount(Account(id = 1L, name = "ICICI", accountType = "BANK", openingBalance = 50000.0))
            kblId = repository.insertAccount(Account(id = 2L, name = "KBL", accountType = "BANK", openingBalance = 10000.0))
            val creditId = repository.insertAccount(Account(id = 3L, name = "ICICI Credit", accountType = "CREDIT_CARD", openingBalance = -5000.0))

            val now = System.currentTimeMillis()
            // Route 1: ICICI -> KBL (2 transfers)
            repository.insertTransaction(
                TransactionEntity(id = 1L, date = now, type = "TRANSFER", amount = 10000.0, fromAccountId = iciciId, toAccountId = kblId, description = "Move 1")
            )
            repository.insertTransaction(
                TransactionEntity(id = 2L, date = now - 1000L, type = "TRANSFER", amount = 5000.0, fromAccountId = iciciId, toAccountId = kblId, description = "Move 2")
            )
            // Route 2: KBL -> ICICI Credit (1 transfer)
            repository.insertTransaction(
                TransactionEntity(id = 3L, date = now - 2000L, type = "TRANSFER", amount = 3000.0, fromAccountId = kblId, toAccountId = creditId, description = "Card Bill")
            )
        }

        val dashboardVm = DashboardViewModel(repository, settingsRepository)
        val ledgerVm = LedgerViewModel(repository, settingsRepository)
        dashboardVm.setTypeMode("TRANSFER")
        runBlocking {
            dashboardVm.uiState.awaitState { it.activeTypeMode == "TRANSFER" && it.transferRoutes.size == 2 }
        }

        var capturedFilter: FilterState? = null
        composeTestRule.setContent {
            MyApplicationTheme {
                DashboardScreen(
                    viewModel = dashboardVm,
                    onNavigateToExpenses = { _, _ -> },
                    onNavigateToExpensesWithFilter = { filter ->
                        capturedFilter = filter
                        ledgerVm.updateFilter(filter)
                    }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Tap "ICICI → KBL" route bar
        composeTestRule.scrollToDashboardTag("transfer_route_bar_ICICI → KBL")
            .performClick()
        composeTestRule.waitForIdle()

        assertNotNull("Tapping transfer route bar must invoke onNavigateToExpensesWithFilter", capturedFilter)
        assertEquals("TRANSFER", capturedFilter!!.selectedType)
        assertEquals(iciciId, capturedFilter!!.transferRouteFromAccountId)
        assertEquals(kblId, capturedFilter!!.transferRouteToAccountId)
        assertEquals("ICICI → KBL", capturedFilter!!.transferRouteLabel)

        runBlocking {
            val routeFilteredState = ledgerVm.uiState.awaitState { !it.isLoading && it.filter.transferRouteFromAccountId == iciciId }
            val routeRows = routeFilteredState.dayGroups.flatMap { it.transactions }
            assertEquals("Only the 2 transfers on ICICI → KBL must be shown", 2, routeRows.size)

            // Clear the route chip and verify all 3 transfers appear
            ledgerVm.updateFilter(
                routeFilteredState.filter.copy(
                    transferRouteFromAccountId = null,
                    transferRouteToAccountId = null,
                    transferRouteLabel = null
                )
            )
            val allTransfersState = ledgerVm.uiState.awaitState { !it.isLoading && it.filter.transferRouteFromAccountId == null }
            assertEquals("Clearing route chip must show all 3 transfers", 3, allTransfersState.dayGroups.flatMap { it.transactions }.size)
        }
    }

    private suspend fun <T> kotlinx.coroutines.flow.StateFlow<T>.awaitState(predicate: (T) -> Boolean): T {
        val deadline = System.currentTimeMillis() + 5000L
        while (!predicate(value) && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(10)
        }
        ShadowLooper.idleMainLooper()
        return value
    }

    // =========================================================================
    // T7: Category chip is hidden when Type=Transfer and visible otherwise.
    // =========================================================================
    @Test
    fun t7_categoryChip_hiddenWhenTypeIsTransfer_andVisibleOtherwise() {
        var filterState by androidx.compose.runtime.mutableStateOf(FilterState(selectedType = "ALL"))
        val categories = listOf(Category(id = 1L, name = "Food", color = "#10B981"))

        composeTestRule.setContent {
            MyApplicationTheme {
                FilterChipsRow(
                    filterState = filterState,
                    categories = categories,
                    accounts = emptyList(),
                    onFilterChange = { filterState = it }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Visible on ALL
        composeTestRule.onNodeWithTag("filter_chip_category").assertIsDisplayed()

        // Visible on EXPENSE
        filterState = FilterState(selectedType = "EXPENSE")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("filter_chip_category").assertIsDisplayed()

        // Visible on INCOME
        filterState = FilterState(selectedType = "INCOME")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("filter_chip_category").assertIsDisplayed()

        // Visible on TRANSFER (Confirm D: Category chip stays visible in Transfer mode and preserves selection)
        filterState = FilterState(selectedType = "TRANSFER", selectedCategoryId = 1L, selectedCategoryIds = setOf(1L))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("filter_chip_category").assertIsDisplayed()
        assertEquals(setOf(1L), filterState.effectiveCategoryIds())
    }

    // =========================================================================
    // T8: Category popover with 16 categories is height-capped, scrollable,
    // and search-filters live.
    // =========================================================================
    @Test
    fun t8_categoryPopover_16Categories_isHeightCappedScrollableAndSearchFiltersLive() {
        val sixteenCategories = (1..16).map { idx ->
            Category(id = idx.toLong(), name = if (idx == 15) "Groceries & Mart" else "CategoryItem_$idx", color = "#3B82F6")
        }
        var filterState by androidx.compose.runtime.mutableStateOf(FilterState())

        composeTestRule.setContent {
            MyApplicationTheme {
                FilterChipsRow(
                    filterState = filterState,
                    categories = sixteenCategories,
                    accounts = emptyList(),
                    onFilterChange = { filterState = it }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Open Category popover
        composeTestRule.onNodeWithTag("filter_chip_category").performClick()
        composeTestRule.waitForIdle()

        // Verify height is capped (~45% of screen height, well under 400.dp)
        val menuNode = composeTestRule.onNodeWithTag("category_dropdown_menu").assertIsDisplayed()
        val menuHeight = menuNode.getUnclippedBoundsInRoot().height
        assertTrue("Expected category menu height <= 400.dp, got $menuHeight", menuHeight <= 400.dp)

        // Verify search field and "All Categories" row exist
        composeTestRule.onNodeWithTag("category_search_input").assertIsDisplayed()
        composeTestRule.onNodeWithText("All Categories").assertIsDisplayed()

        // Type search query "Groceries" -> filters live
        composeTestRule.onNodeWithTag("category_search_input").performTextInput("Groceries")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Groceries & Mart").assertIsDisplayed()
        composeTestRule.onNodeWithText("CategoryItem_1").assertDoesNotExist()
    }

    // =========================================================================
    // T9: Crash logger writes only under internal filesDir; no crash.log*
    // created in MediaStore/Downloads/SAF.
    // =========================================================================
    @Test
    fun t9_crashLogger_writesOnlyUnderInternalFilesDir_andNeverSharedStorage() {
        val pubDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        pubDownloads.mkdirs()

        val uri = MediaStoreWriter.writeCrashLog(context, "TEST CRASH CONTENT")
        assertNotNull(uri)

        val internalCrashFile = File(context.filesDir, "logs/last_crash.log")
        assertTrue("Crash log must exist inside internal context.filesDir/logs/last_crash.log", internalCrashFile.exists())
        assertEquals("TEST CRASH CONTENT", internalCrashFile.readText())
        assertTrue("Returned URI must point inside context.filesDir", uri.toString().contains(context.filesDir.absolutePath))

        // Verify no crash.log* exists anywhere under shared Downloads
        val sharedCrashFiles = pubDownloads.walkTopDown().filter {
            it.isFile && it.name.startsWith("crash.log", ignoreCase = true)
        }.toList()
        assertTrue("No crash.log* file may exist in shared Downloads, found: $sharedCrashFiles", sharedCrashFiles.isEmpty())
    }
}
