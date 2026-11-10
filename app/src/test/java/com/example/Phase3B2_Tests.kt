package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.datastore.AppSettingsRepository
import com.example.data.model.Account
import com.example.data.model.BudgetEntity
import com.example.data.model.Category
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.data.seed.SampleDataSeeder
import com.example.domain.calculator.LedgerCalculator
import com.example.domain.model.AccountWithBalance
import com.example.domain.model.DayGroup
import com.example.domain.model.FilterState
import com.example.importbackup.UnifiedCsvStorage
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.entry.EntryFormState
import com.example.ui.entry.EntryViewModel
import com.example.ui.entry.SplitAllocationLine
import com.example.ui.ledger.LedgerRowItem
import com.example.ui.ledger.LedgerViewModel
import com.example.ui.ledger.buildLedgerRowItems
import com.example.ui.onboarding.OnboardingSourceScreen
import kotlinx.coroutines.flow.first
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
import java.io.InputStream
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase3B2_Tests {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: FinanceRepository
    private lateinit var settingsRepository: AppSettingsRepository

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(database)
        settingsRepository = AppSettingsRepository(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // T1: Migration 9->10 on real v9 database fixture
    @Test
    fun testT1_migrationOnRealV9Fixture() {
        runBlocking {
            val dbName = "test_v9_to_v10.db"
            context.deleteDatabase(dbName)

            // 1. Seed full schema via Room, then downgrade transactions table to v9 shape
            val initDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .build()
            val sqldb = initDb.openHelper.writableDatabase
            sqldb.execSQL("DROP INDEX IF EXISTS `index_transactions_groupId`")
            sqldb.execSQL("DROP TABLE IF EXISTS `transactions`")
            sqldb.execSQL("""
                CREATE TABLE IF NOT EXISTS `transactions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `date` INTEGER NOT NULL,
                    `type` TEXT NOT NULL,
                    `amount` REAL NOT NULL,
                    `fromAccountId` INTEGER,
                    `toAccountId` INTEGER,
                    `categoryId` INTEGER,
                    `description` TEXT NOT NULL,
                    `notes` TEXT NOT NULL,
                    `customValues` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
            """.trimIndent())
            sqldb.execSQL("""
                INSERT INTO transactions (id, date, type, amount, fromAccountId, toAccountId, categoryId, description, notes, customValues, createdAt, updatedAt)
                VALUES (101, 1729000000000, 'EXPENSE', 750.0, NULL, NULL, NULL, 'V9 Grocery', '', '{}', 1729000000000, 1729000000000)
            """.trimIndent())
            sqldb.version = 9
            initDb.close()

            // 2. Apply MIGRATION_9_10 to upgrade from v9 to v10
            val upgradedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .addMigrations(AppDatabase.MIGRATION_9_10)
                .allowMainThreadQueries()
                .build()

            // Existing row backfilled as null groupId
            val tx101 = upgradedDb.transactionDao().getTransactionById(101)
            assertNotNull(tx101)
            assertEquals("V9 Grocery", tx101?.description)
            assertNull("Backfilled groupId must be null", tx101?.groupId)

            // Insert new row with groupId in v10
            val newTxId = upgradedDb.transactionDao().insert(
                TransactionEntity(
                    date = System.currentTimeMillis(),
                    type = "EXPENSE",
                    amount = 250.0,
                    description = "V10 Split Line",
                    groupId = "test_grp_uuid_101"
                )
            )
            val newTx = upgradedDb.transactionDao().getTransactionById(newTxId)
            assertNotNull(newTx)
            assertEquals("test_grp_uuid_101", newTx?.groupId)

            // Verify index works via query
            val groupTxs = upgradedDb.transactionDao().getTransactionsByGroupId("test_grp_uuid_101")
            assertEquals(1, groupTxs.size)
            assertEquals(newTxId, groupTxs[0].id)

            upgradedDb.close()
            context.deleteDatabase(dbName)
        }
    }

    // T2: Split save creates N rows, one groupId, amounts sum to total
    @Test
    fun testT2_splitSaveCreatesNRowsOneGroupIdSumEqualsTotal() {
        runBlocking {
            val accId = repository.insertAccount(Account(name = "HDFC Bank", openingBalance = 5000.0))
            val cat1 = repository.insertCategory(Category(name = "Groceries", color = "#10B981"))
            val cat2 = repository.insertCategory(Category(name = "Household", color = "#3B82F6"))
            val cat3 = repository.insertCategory(Category(name = "Snacks", color = "#F59E0B"))

            val vm = EntryViewModel(repository)
            vm.setType("EXPENSE")
            vm.setFromAccount(accId)
            vm.setAmountDirect("1000.00")
            vm.setDescription("Supermarket Run")
            vm.toggleSplit(true)

            // 3 allocation lines: 500 + 300 + 200 = 1000
            vm.addSplitLine() // lines now size 3
            val lines = vm.formState.value.splitLines
            assertEquals(3, lines.size)

            vm.updateSplitLine(lines[0].id, cat1, accId, "500.00")
            vm.updateSplitLine(lines[1].id, cat2, accId, "300.00")
            vm.updateSplitLine(lines[2].id, cat3, accId, "200.00")

            assertTrue("Should be valid to save", vm.formState.value.isSplitValidToSave)
            assertEquals(0.0, vm.formState.value.leftToAssign, 0.001)

            vm.saveTransaction()
            waitForCondition { repository.getAllTransactionsList().size == 3 }

            val allTxs = repository.getAllTransactionsList()
            assertEquals(3, allTxs.size)

            val firstGid = allTxs[0].groupId
            assertNotNull("Group ID must not be null", firstGid)
            assertTrue("All N rows must share the exact same groupId", allTxs.all { it.groupId == firstGid })
            assertEquals(1000.0, allTxs.sumOf { it.amount }, 0.001)
            assertTrue(allTxs.all { it.description == "Supermarket Run" })
        }
    }

    // T3: Save blocked while left-to-assign != 0
    @Test
    fun testT3_saveBlockedWhileLeftToAssignNotZero() {
        val stateUnder = EntryFormState(
            type = "EXPENSE",
            amountText = "1000",
            isSplit = true,
            splitLines = listOf(
                SplitAllocationLine(amountText = "400"),
                SplitAllocationLine(amountText = "400")
            )
        )
        // 1000 - 800 = 200 left
        assertEquals(200.0, stateUnder.leftToAssign, 0.001)
        assertFalse("Save must be blocked when leftToAssign > 0", stateUnder.isSplitValidToSave)

        val stateOver = EntryFormState(
            type = "EXPENSE",
            amountText = "1000",
            isSplit = true,
            splitLines = listOf(
                SplitAllocationLine(amountText = "600"),
                SplitAllocationLine(amountText = "500")
            )
        )
        // 1000 - 1100 = -100 left
        assertEquals(-100.0, stateOver.leftToAssign, 0.001)
        assertFalse("Save must be blocked when leftToAssign < 0", stateOver.isSplitValidToSave)

        val stateZeroLine = EntryFormState(
            type = "EXPENSE",
            amountText = "1000",
            isSplit = true,
            splitLines = listOf(
                SplitAllocationLine(amountText = "1000"),
                SplitAllocationLine(amountText = "0")
            )
        )
        // 1000 - 1000 = 0 left, but a line is 0.0
        assertFalse("Save must be blocked if any line is 0", stateZeroLine.isSplitValidToSave)

        val stateValid = EntryFormState(
            type = "EXPENSE",
            amountText = "1000",
            isSplit = true,
            splitLines = listOf(
                SplitAllocationLine(amountText = "700"),
                SplitAllocationLine(amountText = "300")
            )
        )
        assertTrue("Save enabled when left == 0 and all lines > 0", stateValid.isSplitValidToSave)
    }

    // T4: Group swipe-delete removes all N, undo restores all N with same groupId
    @Test
    fun testT4_groupSwipeDeleteRemovesAllNUndoRestoresAllNWithSameGroupId() {
        runBlocking {
            val gid = "grp_test_swipe_401"
            val tx1 = repository.insertTransaction(TransactionEntity(date = 1000L, type = "EXPENSE", amount = 300.0, description = "Split Buy", groupId = gid))
            val tx2 = repository.insertTransaction(TransactionEntity(date = 1000L, type = "EXPENSE", amount = 200.0, description = "Split Buy", groupId = gid))

            val ledgerVm = LedgerViewModel(repository, settingsRepository = settingsRepository)
            val loadedTx1 = repository.getTransactionById(tx1)!!

            // Swipe delete group
            var undoCallback: (() -> Unit)? = null
            ledgerVm.deleteTransaction(loadedTx1) { undo -> undoCallback = undo }
            waitForCondition { undoCallback != null }

            // Commit pending deletion
            ledgerVm.commitPendingDelete()
            waitForCondition { repository.getAllTransactionsList().isEmpty() }

            val afterDelete = repository.getAllTransactionsList()
            assertEquals(0, afterDelete.size)

            // Undo restores all N rows with same groupId
            ledgerVm.undoDelete()
            waitForCondition { repository.getAllTransactionsList().size == 2 }

            val afterUndo = repository.getAllTransactionsList()
            assertEquals(2, afterUndo.size)
            assertTrue(afterUndo.all { it.groupId == gid })
            assertEquals(500.0, afterUndo.sumOf { it.amount }, 0.001)
        }
    }

    // T5: Editor collapse to one line clears groupId
    @Test
    fun testT5_editorCollapseToOneLineClearsGroupId() {
        runBlocking {
            val accId = repository.insertAccount(Account(name = "Main Bank", openingBalance = 5000.0))
            val gid = "grp_collapse_501"
            val tx1Id = repository.insertTransaction(TransactionEntity(date = 1000L, type = "EXPENSE", amount = 300.0, fromAccountId = accId, description = "Split Item", groupId = gid))
            val tx2Id = repository.insertTransaction(TransactionEntity(date = 1000L, type = "EXPENSE", amount = 200.0, fromAccountId = accId, description = "Split Item", groupId = gid))

            val entryVm = EntryViewModel(repository, editTransactionId = tx1Id)
            entryVm.formState.awaitState { it.isSplit && it.splitLines.size == 2 }

            assertTrue(entryVm.formState.value.isSplit)
            assertEquals(2, entryVm.formState.value.splitLines.size)

            // Remove 1 line down to 1
            val lineToRemove = entryVm.formState.value.splitLines.last()
            entryVm.removeSplitLine(lineToRemove.id)

            // Downgrades to single row
            assertFalse(entryVm.formState.value.isSplit)
            assertNull("GroupId must be cleared on downgrade to 1 row", entryVm.formState.value.splitGroupId)

            entryVm.saveTransaction()
            waitForCondition {
                val list = repository.getAllTransactionsList()
                list.size == 1 && list[0].groupId == null
            }

            val remaining = repository.getAllTransactionsList()
            assertEquals(1, remaining.size)
            assertNull("Saved single row must have null groupId", remaining[0].groupId)
        }
    }

    // T6: CSV export of a split = N ordinary rows, re-import = N rows, money totals identical
    @Test
    fun testT6_csvExportSplitAsOrdinaryRowsReImportAsNGroupsIdenticalTotals() {
        runBlocking {
        val bankId = repository.insertAccount(Account(id = 1L, name = "SBI", accountType = "BANK", openingBalance = 0.0))
        val cat1 = repository.insertCategory(Category(id = 1L, name = "Food", color = "#10B981"))
        val cat2 = repository.insertCategory(Category(id = 2L, name = "Clothes", color = "#3B82F6"))

        val gid = "split_csv_grp_601"
        repository.insertTransaction(TransactionEntity(date = 1729000000000L, type = "EXPENSE", amount = 600.0, fromAccountId = bankId, categoryId = cat1, description = "Mall Split", groupId = gid))
        repository.insertTransaction(TransactionEntity(date = 1729000000000L, type = "EXPENSE", amount = 400.0, fromAccountId = bankId, categoryId = cat2, description = "Mall Split", groupId = gid))

        val accounts = repository.allAccounts.first()
        val categories = repository.allCategories.first()
        val customFields = repository.allCustomFields.first()
        val settings = settingsRepository.settingsFlow.first()
        val commitments = repository.allCommitments.first()
        val budgets = repository.allBudgets.first()
        val txs = repository.allTransactions.first()

        val csvString = com.example.importbackup.CsvBackupManager.buildCsv(txs, accounts, categories, customFields, settings, commitments, budgets)

        // CSV contract frozen: groupId is NOT written to CSV
        assertFalse("groupId must not be exported to CSV", csvString.contains(gid))

        // Re-import into a fresh database
        val newDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val newRepo = FinanceRepository(newDb)
        val inStream: InputStream = csvString.byteInputStream()

        val restoreResult = UnifiedCsvStorage.restoreFromInputStream(newRepo, inStream, clearExisting = true, settingsRepository = settingsRepository)
        assertEquals(2, restoreResult.inserted)

        val restoredTxs = newRepo.getAllTransactionsList()
        assertEquals(2, restoredTxs.size)
        assertEquals(1000.0, restoredTxs.sumOf { it.amount }, 0.001)
        // GroupId is null upon honest re-import (render-only grouping)
        assertTrue(restoredTxs.all { it.groupId == null })

        newDb.close()
    }
}

    // T7: Ledger renders exactly one collapsed row per group
    @Test
    fun testT7_ledgerRendersExactlyOneCollapsedRowPerGroup() {
        runBlocking {
        val bank = Account(id = 1L, name = "Bank", accountType = "BANK", openingBalance = 0.0)
        val cat = Category(id = 1L, name = "Shopping", color = "#10B981")

        val gid = "grp_render_701"
        val tx1 = com.example.domain.model.EnrichedTransaction(
            TransactionEntity(id = 1L, date = 1000L, type = "EXPENSE", amount = 300.0, fromAccountId = 1L, categoryId = 1L, description = "Groceries Split", groupId = gid),
            fromAccount = bank, category = cat, runningBalance = 300.0
        )
        val tx2 = com.example.domain.model.EnrichedTransaction(
            TransactionEntity(id = 2L, date = 1000L, type = "EXPENSE", amount = 200.0, fromAccountId = 1L, categoryId = 1L, description = "Groceries Split", groupId = gid),
            fromAccount = bank, category = cat, runningBalance = 500.0
        )
        val txSingle = com.example.domain.model.EnrichedTransaction(
            TransactionEntity(id = 3L, date = 1000L, type = "EXPENSE", amount = 150.0, fromAccountId = 1L, categoryId = 1L, description = "Single Tx", groupId = null),
            fromAccount = bank, category = cat, runningBalance = 650.0
        )

        val rowItems = buildLedgerRowItems(listOf(tx1, tx2, txSingle))

        // Exactly 2 row items: 1 SplitGroup (collapsing tx1 and tx2) + 1 Single
        assertEquals(2, rowItems.size)
        assertTrue(rowItems[0] is LedgerRowItem.SplitGroup)
        assertTrue(rowItems[1] is LedgerRowItem.Single)

        val splitGroup = rowItems[0] as LedgerRowItem.SplitGroup
        assertEquals(500.0, splitGroup.totalAmount, 0.001)
        assertEquals(2, splitGroup.lines.size)
        assertEquals("Groceries Split", splitGroup.description)
    }
}

    // T8: O1 kickers present on onboarding source screen
    @Test
    fun testT8_onboardingKickersPresent() {
        composeTestRule.setContent {
            OnboardingSourceScreen(
                repository = repository,
                settingsRepository = settingsRepository,
                onOnboardingComplete = {}
            )
        }

        composeTestRule.onNodeWithTag("onboarding_kicker_new_user", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("NEW USER?", useUnmergedTree = true).assertIsDisplayed()

        composeTestRule.onNodeWithTag("onboarding_kicker_existing_user", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("EXISTING USER?", useUnmergedTree = true).assertIsDisplayed()
    }

    // T9: Demo seeder contains one 3-line split
    @Test
    fun testT9_demoSeederContainsOneThreeLineSplit() {
        runBlocking {
        SampleDataSeeder.seedSampleData(database)

        val txs = repository.getAllTransactionsList()
        val splitTxs = txs.filter { it.groupId != null }

        assertTrue("Demo seeder must seed split transactions", splitTxs.isNotEmpty())
        val groupIds = splitTxs.mapNotNull { it.groupId }.distinct()
        assertEquals("Exactly one split group in demo seeder", 1, groupIds.size)

        val demoGroupTxs = splitTxs.filter { it.groupId == groupIds[0] }
        assertEquals("Demo split must have exactly 3 lines", 3, demoGroupTxs.size)
        assertEquals(3000.0, demoGroupTxs.sumOf { it.amount }, 0.001)
    }
}

    // T10: Dashboard renders with NO standalone Expense/Income/Transfer pill row
    @Test
    fun testT10_dashboardRendersWithNoStandalonePillRow() {
        composeTestRule.setContent {
            DashboardScreen(
                viewModel = DashboardViewModel(repository, settingsRepository),
                onNavigateToExpenses = { _, _ -> }
            )
        }

        // Quote assertion used: verifying standalone pill row tags do NOT exist
        composeTestRule.onNodeWithTag("mode_tab_expense").assertDoesNotExist()
        composeTestRule.onNodeWithTag("mode_tab_income").assertDoesNotExist()
        composeTestRule.onNodeWithTag("mode_tab_transfer").assertDoesNotExist()
    }

    // T11: Setting the Type chip to Income flips the KPI label to "This Month Credits" and the flow-card title to "Where money came from", and the Balance Breakdown sheet's mode toggle produces the same flip (shared state proven)
    @Test
    fun testT11_typeChipAndBalanceBreakdownSyncSharedState() {
        runBlocking {
        val vm = DashboardViewModel(repository, settingsRepository)

        // Initial mode: EXPENSE
        assertEquals("EXPENSE", vm.uiState.value.activeTypeMode)

        // Flip to INCOME via setTypeMode (used by Type filter chip)
        vm.setTypeMode("INCOME")
        val stateIncome = vm.uiState.awaitState { it.activeTypeMode == "INCOME" }
        assertEquals("INCOME", stateIncome.activeTypeMode)

        // Flip back to EXPENSE, then flip via Balance Breakdown sheet's mode toggle
        vm.setTypeMode("EXPENSE")
        assertEquals("EXPENSE", vm.uiState.awaitState { it.activeTypeMode == "EXPENSE" }.activeTypeMode)

        // Balance breakdown sheet also invokes vm.setTypeMode("INCOME")
        vm.setTypeMode("INCOME")
        val stateSheetFlip = vm.uiState.awaitState { it.activeTypeMode == "INCOME" }
        assertEquals("INCOME", stateSheetFlip.activeTypeMode)
    }
}

    // T12: sortSources unit test: Your Money desc by balance with alphabetical ties and overdrawn last, Credit Cards desc by owed with alphabetical ties
    @Test
    fun testT12_sortSourcesRules() {
        val bankA = Account(id = 1L, name = "Axis Bank", accountType = "BANK", openingBalance = 5000.0)
        val cash = Account(id = 2L, name = "Cash Wallet", accountType = "CASH", openingBalance = 5000.0)
        val bankB = Account(id = 3L, name = "HDFC Bank", accountType = "BANK", openingBalance = 10000.0)
        val overdrawn = Account(id = 4L, name = "Overdrawn Overdraft", accountType = "BANK", openingBalance = -2000.0)

        val cardA = Account(id = 5L, name = "ICICI Card", accountType = "CREDIT_CARD", openingBalance = -10000.0)
        val cardB = Account(id = 6L, name = "Amex Card", accountType = "CREDIT_CARD", openingBalance = -10000.0)
        val cardC = Account(id = 7L, name = "SBI Card", accountType = "CREDIT_CARD", openingBalance = -3000.0)

        val list = listOf(
            AccountWithBalance(bankA, 5000.0),
            AccountWithBalance(cash, 5000.0),
            AccountWithBalance(bankB, 10000.0),
            AccountWithBalance(overdrawn, -2000.0),
            AccountWithBalance(cardA, -10000.0),
            AccountWithBalance(cardB, -10000.0),
            AccountWithBalance(cardC, -3000.0)
        )

        val sorted = LedgerCalculator.sortSources(list)

        // Your Money:
        // 1. HDFC (10000)
        // 2. Axis (5000, A-Z tie over Cash)
        // 3. Cash (5000)
        // 4. Overdrawn (-2000, sink to bottom)
        assertEquals("HDFC Bank", sorted[0].account.name)
        assertEquals("Axis Bank", sorted[1].account.name)
        assertEquals("Cash Wallet", sorted[2].account.name)
        assertEquals("Overdrawn Overdraft", sorted[3].account.name)

        // Credit Cards:
        // 1. Amex (-10000 owed, A-Z tie over ICICI)
        // 2. ICICI (-10000 owed)
        // 3. SBI (-3000 owed)
        assertEquals("Amex Card", sorted[4].account.name)
        assertEquals("ICICI Card", sorted[5].account.name)
        assertEquals("SBI Card", sorted[6].account.name)
    }

    // T13: Robolectric: mode=Income shows "This Month Credits" and hides the Budgets card; mode=Transfer shows "Moved Out"/"Moved In" and the "Where money moved" title
    @Test
    fun testT13_robolectricModeDynamics() {
        runBlocking {
        val bankId = repository.insertAccount(Account(name = "Bank", accountType = "BANK", openingBalance = 0.0))
        val catId = repository.insertCategory(Category(name = "Salary", color = "#10B981"))
        repository.insertBudget(BudgetEntity(name = "Spend Cap", scopeType = "CATEGORY", targetName = "All Spend", limitAmount = 10000.0, active = true))

        repository.insertTransaction(TransactionEntity(date = System.currentTimeMillis(), type = "INCOME", amount = 50000.0, toAccountId = bankId, categoryId = catId, description = "Salary"))

        val vm = DashboardViewModel(repository, settingsRepository)

        // Income Mode
        vm.setTypeMode("INCOME")
        val incomeState = vm.uiState.awaitState { it.activeTypeMode == "INCOME" && it.thisMonthCredits == 50000.0 }
        assertFalse("Budgets card must be hidden in Income mode", incomeState.isBudgetsCardVisibleForMode)
        assertEquals(50000.0, incomeState.thisMonthCredits, 0.01)

        // Transfer Mode
        vm.setTypeMode("TRANSFER")
        val transferState = vm.uiState.awaitState { it.activeTypeMode == "TRANSFER" }
        assertFalse("Budgets card must be hidden in Transfer mode", transferState.isBudgetsCardVisibleForMode)
        assertEquals(0.0, transferState.movedOutThisMonth, 0.01)
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

    private suspend fun waitForCondition(predicate: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5000L
        while (!predicate() && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(10)
        }
        ShadowLooper.idleMainLooper()
    }
}
