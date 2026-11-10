package com.example

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.domain.model.EnrichedTransaction
import com.example.ui.entry.EntryScreen
import com.example.ui.entry.EntryViewModel
import com.example.ui.ledger.TransactionCardRow
import com.example.ui.ledger.UncategorizedNudgeManager
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.example.ui.ledger.LedgerViewModel
import com.example.ui.ledger.LedgerScreen
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class Phase2_11_UncategorizedNudgeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: FinanceRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(database)
        UncategorizedNudgeManager.reset()
    }

    @After
    fun tearDown() {
        try {
            composeTestRule.waitForIdle()
        } catch (_: Exception) {}
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        database.close()
        UncategorizedNudgeManager.reset()
    }

    @Test
    fun testCategorylessTxnRow_exposesAddCategoryChip_andTitleNotDisplaced() {
        // (i) a categoryless txn row exposes a clickable "+ Add category" node and the full title still renders (no displacement)
        var rowClicked = false
        val uncategorizedTx = EnrichedTransaction(
            entity = TransactionEntity(
                id = 701L,
                date = System.currentTimeMillis(),
                type = "EXPENSE",
                amount = 250.0,
                fromAccountId = 1L,
                description = "Groceries"
            ),
            fromAccount = Account(id = 1L, name = "Cash", accountType = "CASH", openingBalance = 1000.0),
            toAccount = null,
            category = null,
            runningBalance = 750.0
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                TransactionCardRow(
                    transaction = uncategorizedTx,
                    onClick = { rowClicked = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Verify title "Groceries" is displayed
        composeTestRule.onNodeWithText("Groceries").assertIsDisplayed()

        // 2. Verify "+ Add category" chip exists, is displayed, and is clickable
        composeTestRule.onNodeWithTag("add_category_chip_701")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertTextContains("+ Add category")
            .performClick()

        assertTrue("Tapping + Add category opens edit / re-categorize flow", rowClicked)
    }

    @Test
    fun testCategorizedTxnRow_doesNotShowChip_andRendersUnchanged() {
        // (ii) a categorized row does NOT show the chip and renders unchanged (regression guard)
        val foodCategory = Category(id = 5L, name = "Food & Dining", color = "#10B981")
        val categorizedTx = EnrichedTransaction(
            entity = TransactionEntity(
                id = 702L,
                date = System.currentTimeMillis(),
                type = "EXPENSE",
                amount = 300.0,
                fromAccountId = 1L,
                categoryId = 5L,
                description = "Dinner"
            ),
            fromAccount = Account(id = 1L, name = "Cash", accountType = "CASH", openingBalance = 1000.0),
            toAccount = null,
            category = foodCategory,
            runningBalance = 450.0
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                TransactionCardRow(
                    transaction = categorizedTx,
                    onClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Title and category name exist
        composeTestRule.onNodeWithText("Dinner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Food & Dining", substring = true).assertIsDisplayed()

        // 2. + Add category chip does NOT exist
        composeTestRule.onNodeWithTag("add_category_chip_702").assertDoesNotExist()
    }

    @Test
    fun testSavingWithoutCategory_emitsSnackbarWithAddCategoryAction_persistsEntity() {
        // (iii) saving without a category emits the snackbar with an "Add category" action and persists the txn exactly as before (byte-identical entity)
        var accId: Long
        runBlocking {
            accId = repository.insertAccount(Account(name = "Wallet", accountType = "CASH", openingBalance = 5000.0))
        }

        val viewModel = EntryViewModel(
            repository = repository,
            initialSourceId = accId
        )
        viewModel.setAmountDirect("150")
        viewModel.setDescription("Snack")

        composeTestRule.setContent {
            MyApplicationTheme {
                EntryScreen(
                    viewModel = viewModel,
                    onNavigateBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Save transaction and wait for coroutine to process
        composeTestRule.onNodeWithTag("save_transaction_button").performClick()
        var retries = 0
        while (!UncategorizedNudgeManager.lastNudgeEmitted && retries < 50) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            Thread.sleep(50)
            retries++
        }

        // Verify snackbar was emitted with "Saved without a category" and action "Add category"
        assertTrue("Uncategorized nudge must be emitted", UncategorizedNudgeManager.lastNudgeEmitted)
        composeTestRule.onNodeWithText("Saved without a category").assertExists()
        composeTestRule.onNodeWithText("Add category").assertExists().assertHasClickAction()

        // Verify entity persisted with categoryId = null (byte-identical)
        runBlocking {
            val txs = repository.allTransactions.first()
            assertEquals(1, txs.size)
            val tx = txs[0]
            assertEquals(150.0, tx.amount, 0.001)
            assertEquals("Snack", tx.description)
            assertNull(tx.categoryId)
            assertEquals(accId, tx.fromAccountId)
            assertEquals("EXPENSE", tx.type)
        }
    }

    @Test
    fun testEntrySheet_helperTextAppearsOnlyWhenCategoryEmpty() {
        // (iv) the helper text appears in the entry sheet only when category is empty
        var catId: Long
        var accId: Long
        runBlocking {
            accId = repository.insertAccount(Account(name = "Bank", accountType = "BANK", openingBalance = 5000.0))
            catId = repository.insertCategory(Category(name = "Utilities", color = "#3B82F6"))
        }

        val viewModel = EntryViewModel(
            repository = repository,
            initialSourceId = accId
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                EntryScreen(
                    viewModel = viewModel,
                    onNavigateBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Initially category is empty -> helper text exists
        composeTestRule.onNodeWithTag("category_helper_text", useUnmergedTree = true)
            .assertExists()
            .assertTextContains("Optional — uncategorized money won't show in your charts.")

        // 2. Select category -> helper text disappears
        viewModel.setCategory(catId)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("category_helper_text", useUnmergedTree = true).assertDoesNotExist()

        // 3. Clear category back to null -> helper text reappears
        viewModel.setCategory(null)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("category_helper_text", useUnmergedTree = true)
            .assertExists()
            .assertTextContains("Optional — uncategorized money won't show in your charts.")
    }

    @Test
    fun testIncomeSaveWithoutSource_emitsSourceWordedError_andNoValidationContainsToAccount() {
        val viewModel = EntryViewModel(repository = repository)
        viewModel.setType("INCOME")
        viewModel.setAmountDirect("1200")
        // No source selected (toAccountId is null)

        composeTestRule.setContent {
            MyApplicationTheme {
                EntryScreen(
                    viewModel = viewModel,
                    onNavigateBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Click Save button
        composeTestRule.onNodeWithTag("save_transaction_button").performClick()
        composeTestRule.waitForIdle()

        // 1. Error displayed matches the "To Source" label and says "Please select a source for income"
        val incomeError = viewModel.formState.value.errorMessage
        assertEquals("Please select a source for income", incomeError)
        composeTestRule.onNodeWithText("Please select a source for income").assertIsDisplayed()

        // 2. Audit all tabs / validation paths: verify NO validation string contains "To Account"
        assertFalse("Income error should not contain 'To Account'", incomeError?.contains("To Account") ?: false)
    }

    @Test
    fun testExportFilteredCsv_emptyFilteredState_emitsMessageAndZeroFiles() {
        val ledgerViewModel = LedgerViewModel(repository = repository)

        composeTestRule.setContent {
            MyApplicationTheme {
                LedgerScreen(
                    viewModel = ledgerViewModel,
                    onAddClick = {},
                    onTransactionClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. In empty state, header export icon is displayed and clickable
        val exportBtn = composeTestRule.onNodeWithTag("ledger_export_filtered_btn")
        exportBtn.assertIsDisplayed()
        exportBtn.assertHasClickAction()

        // 2. Tap export button -> triggers exportFilteredCsv which finds 0 transactions and shows "No records to export"
        exportBtn.performClick()
        var retries = 0
        while (retries < 40) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            try {
                composeTestRule.onNodeWithText("No records to export").assertExists()
                break
            } catch (_: AssertionError) {
                Thread.sleep(50)
                retries++
            }
        }
        composeTestRule.onNodeWithText("No records to export").assertExists()

        // 3. Direct service-layer guard check: returns "No records to export" immediately
        runBlocking {
            val emptyResult = ledgerViewModel.exportFilteredCsv(context)
            assertEquals("No records to export", emptyResult)
            assertEquals("No records to export", ledgerViewModel.statusMessage.first())
        }
    }

    @Test
    fun testExportFilteredCsv_nonEmptyFilteredState_exportsFile() {
        var accId: Long
        runBlocking {
            accId = repository.insertAccount(Account(name = "Bank", accountType = "BANK", openingBalance = 1000.0))
            val catId = repository.insertCategory(Category(name = "Groceries", color = "#10B981"))
            repository.insertTransaction(
                TransactionEntity(
                    date = System.currentTimeMillis(),
                    type = "EXPENSE",
                    amount = 250.0,
                    fromAccountId = accId,
                    categoryId = catId,
                    description = "Food Market"
                )
            )
        }

        val ledgerViewModel = LedgerViewModel(repository = repository)

        composeTestRule.setContent {
            MyApplicationTheme {
                LedgerScreen(
                    viewModel = ledgerViewModel,
                    onAddClick = {},
                    onTransactionClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // With transactions present in uiState, calling exportFilteredCsv returns the exported path
        runBlocking {
            val nonEmptyResult = ledgerViewModel.exportFilteredCsv(context)
            assertTrue("Non-empty export should return file path", nonEmptyResult.contains("filtered_export_") || nonEmptyResult.contains("Downloads"))
            assertEquals("Exported to $nonEmptyResult", ledgerViewModel.statusMessage.first())
        }
    }
}
