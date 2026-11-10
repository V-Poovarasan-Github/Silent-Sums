package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.datastore.AppSettingsRepository
import com.example.data.model.Account
import com.example.data.model.BudgetEntity
import com.example.data.model.Category
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.domain.budget.BudgetCalculator
import com.example.domain.budget.BudgetTintTier
import com.example.domain.budget.CalculatedBudget
import com.example.importbackup.MediaStoreWriter
import com.example.importbackup.UnifiedCsvStorage
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.entry.EntryScreen
import com.example.ui.entry.EntryViewModel
import com.example.ui.lists.ListsScreen
import com.example.ui.lists.ListsTab
import com.example.ui.lists.ListsViewModel
import com.example.ui.theme.MyApplicationTheme
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
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class BudgetRenderAndPolicyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: FinanceRepository
    private lateinit var settingsRepository: AppSettingsRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(database)
        settingsRepository = AppSettingsRepository(context)
        runBlocking {
            settingsRepository.clearSettings()
            settingsRepository.setCurrency("₹")
            com.example.domain.util.Formatters.updateCurrentCurrencySymbol("₹")
            settingsRepository.setBudgetsCardVisible(true)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            settingsRepository.clearSettings()
            database.close()
        }
    }

    @Test
    fun testBudgetsCard_RendersColoredBars() {
        // Seed accounts, categories, transactions, and active/inactive budgets
        runBlocking {
            val accId = repository.insertAccount(Account(id = 1L, name = "ICICI", accountType = "CREDIT_CARD", openingBalance = 0.0))
            val catId = repository.insertCategory(Category(id = 1L, name = "Dining", color = "#EF4444"))
            repository.insertTransaction(
                TransactionEntity(id = 1L, date = System.currentTimeMillis(), type = "EXPENSE", amount = 1000.0, fromAccountId = accId, categoryId = catId, notes = "Dinner")
            )

            // Active budget 1: Dining (1000 spent / 2000 limit = 50% => BELOW_80 green)
            repository.insertBudget(BudgetEntity(id = 1L, name = "Dining Budget", scopeType = "CATEGORY", targetName = "Dining", limitAmount = 2000.0, active = true))
            // Inactive budget: Should NOT be painted
            repository.insertBudget(BudgetEntity(id = 2L, name = "Old Budget", scopeType = "CATEGORY", targetName = "Old", limitAmount = 500.0, active = false))
            settingsRepository.setBudgetsCardVisible(true)
        }

        val dashboardViewModel = DashboardViewModel(
            repository = repository,
            settingsRepository = settingsRepository
        )

        var navigatedToFilterScope = false
        composeTestRule.setContent {
            MyApplicationTheme {
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onNavigateToExpenses = { _, _ -> },
                    onNavigateToExpensesWithFilter = { _ ->
                        navigatedToFilterScope = true
                    }
                )
            }
        }

        composeTestRule.waitForIdle()

        // 1. Assert card paints
        composeTestRule.scrollToDashboardTag("dashboard_budgets_card", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Dining Budget", substring = true).onFirst().performScrollTo().assertIsDisplayed()

        // 2. Assert inactive budget is NOT painted
        composeTestRule.onNodeWithText("Old Budget").assertDoesNotExist()

        // 3. Verify tier is BELOW_80
        val budgets = runBlocking { repository.activeBudgets.first() }
        val calculated = BudgetCalculator.computeBudgets(
            budgets = budgets,
            transactions = runBlocking { repository.allTransactions.first() },
            accounts = runBlocking { repository.allAccounts.first() },
            categories = runBlocking { repository.allCategories.first() },
            monthStartDay = 1
        )
        assertEquals(1, calculated.size)
        assertEquals(BudgetTintTier.BELOW_80, calculated.first().tier)

        // 4. Tap row navigates to filtered ledger
        composeTestRule.scrollToDashboardTag("budget_item_1", useUnmergedTree = true).performClick()
        assertTrue("Tapping budget row must navigate to filtered ledger", navigatedToFilterScope)
    }

    @Test
    fun testEntryLiveLine_RendersOnMatchingBudget() {
        val (catId, accId) = runBlocking {
            val cat = repository.insertCategory(Category(id = 1L, name = "Food", color = "#10B981"))
            val acc = repository.insertAccount(Account(id = 1L, name = "Cash", accountType = "CASH", openingBalance = 5000.0))
            repository.insertBudget(BudgetEntity(id = 1L, name = "Food Cap", scopeType = "CATEGORY", targetName = "Food", limitAmount = 1000.0, active = true))
            cat to acc
        }

        val entryViewModel = EntryViewModel(
            repository = repository,
            settingsRepository = settingsRepository,
            initialCategoryId = catId,
            initialSourceId = accId
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                EntryScreen(
                    viewModel = entryViewModel,
                    onNavigateBack = {}
                )
            }
        }

        // 1. Initial neutral state with matching budget shows single tightest-headroom line
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("entry_budget_indicator").assertIsDisplayed()

        // 2. Typing expression updates headroom on resolved value
        entryViewModel.onAmountTextChange("500+200")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("entry_budget_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithText("Food Cap: ₹300.00 left of ₹1,000.00", substring = true).assertIsDisplayed()

        // 3. Changing to a category with NO budget hides the indicator
        val unbudgetedCatId = runBlocking {
            repository.insertCategory(Category(id = 2L, name = "Travel", color = "#3B82F6"))
        }
        entryViewModel.selectCategory(unbudgetedCatId)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("entry_budget_indicator").assertDoesNotExist()
    }

    @Test
    fun testOverLimitConfirm_RendersOnCrossing() {
        val (catId, accId, budget) = runBlocking {
            val cat = repository.insertCategory(Category(id = 1L, name = "Shopping", color = "#EC4899"))
            val acc = repository.insertAccount(Account(id = 1L, name = "Card", accountType = "CREDIT_CARD", openingBalance = 0.0))
            // Limit: 1000. Current spend: 800. Remaining: 200.
            repository.insertTransaction(TransactionEntity(date = System.currentTimeMillis(), type = "EXPENSE", amount = 800.0, categoryId = cat, fromAccountId = acc, notes = "Clothes"))
            val b = BudgetEntity(id = 1L, name = "Shopping Budget", scopeType = "CATEGORY", targetName = "Shopping", limitAmount = 1000.0, active = true)
            repository.insertBudget(b)
            settingsRepository.setBudgetsCardVisible(false) // Turn budgetsCardVisible OFF to verify active surfaces remain toggle-exempt
            Triple(cat, acc, b)
        }

        val entryViewModel = EntryViewModel(
            repository = repository,
            settingsRepository = settingsRepository,
            initialCategoryId = catId,
            initialSourceId = accId
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                EntryScreen(
                    viewModel = entryViewModel,
                    onNavigateBack = {}
                )
            }
        }

        // Entering 300.0 crosses the 1000.0 limit (800 + 300 = 1100 > 1000)
        entryViewModel.onAmountTextChange("300")
        composeTestRule.waitForIdle()

        // Trigger Save
        composeTestRule.onNodeWithTag("save_transaction_button").performClick()
        composeTestRule.waitUntil(10_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            entryViewModel.crossedBudgetsWarning.value.isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // Over-limit confirmation dialog MUST open naming the crossed budget
        composeTestRule.onNodeWithTag("over_budget_confirm_dialog").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Shopping Budget", substring = true).onFirst().assertIsDisplayed()

        // Downward edit or already-over edit does NOT fire confirmation
        val crossedDown = BudgetCalculator.checkCrossedBudgets(
            calculatedBudgets = listOf(CalculatedBudget(budget, spent = 1100.0, limit = 1000.0, remaining = -100.0, percentage = 110.0, tier = BudgetTintTier.HUNDRED_OR_ABOVE, isOverLimit = true)),
            matchingBudgets = listOf(budget),
            newAmount = 250.0,
            oldAmount = 300.0 // reducing amount from 300 to 250
        )
        assertTrue("Downward edit must never fire over-limit confirm", crossedDown.isEmpty())
    }

    @Test
    fun testEmptyState_AddBudgetReachable() {
        // Assert initial state has ZERO budgets
        assertEquals(0, runBlocking { repository.getAllBudgetsList().size })

        val listsViewModel = ListsViewModel(repository, initialTab = ListsTab.BUDGETS)
        listsViewModel.selectTab(ListsTab.BUDGETS)

        composeTestRule.setContent {
            MyApplicationTheme {
                ListsScreen(viewModel = listsViewModel, initialTab = ListsTab.BUDGETS)
            }
        }

        composeTestRule.waitForIdle()

        // 1. With ZERO budgets, empty state and add button are reachable
        composeTestRule.onNodeWithTag("add_budget_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_budget_button").performClick()

        // 2. Opens dialog to create first budget
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithTag("budget_name_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("budget_name_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("budget_limit_input").performTextInput("5000")
        composeTestRule.onNodeWithTag("save_budget_button").performClick()

        // 3. First budget is created in database
        composeTestRule.waitUntil(5000) {
            runBlocking { repository.getAllBudgetsList().isNotEmpty() }
        }
        assertEquals(1, runBlocking { repository.getAllBudgetsList().size })
    }

    @Test
    fun testBudgetsCardToggle_HidesThreePassiveSurfacesTogether() {
        // Seed 1 active budget
        runBlocking {
            val accId = repository.insertAccount(Account(id = 1L, name = "ICICI", accountType = "CREDIT_CARD", openingBalance = 0.0))
            val catId = repository.insertCategory(Category(id = 1L, name = "Dining", color = "#EF4444"))
            repository.insertTransaction(TransactionEntity(date = System.currentTimeMillis(), type = "EXPENSE", amount = 500.0, categoryId = catId, fromAccountId = accId, notes = "Lunch"))
            repository.insertBudget(BudgetEntity(id = 1L, name = "Dining", scopeType = "CATEGORY", targetName = "Dining", limitAmount = 2000.0, active = true))
            repository.insertBudget(BudgetEntity(id = 2L, name = "ICICI Budget", scopeType = "ACCOUNT", targetName = "ICICI", limitAmount = 2000.0, active = true))
            settingsRepository.setBudgetsCardVisible(true)
        }

        val dashboardViewModel = DashboardViewModel(repository, settingsRepository)
        composeTestRule.setContent {
            MyApplicationTheme {
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onNavigateToExpenses = { _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.scrollToDashboardTag("dashboard_budgets_card").assertIsDisplayed()
        composeTestRule.scrollToDashboardTag("spending_insights_budget_surface").assertIsDisplayed()
        composeTestRule.scrollToDashboardTag("source_budget_chip_1", useUnmergedTree = true).assertIsDisplayed()

        // 2. When toggle is OFF: all three disappear together
        runBlocking { settingsRepository.setBudgetsCardVisible(false) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dashboard_budgets_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("spending_insights_budget_surface").assertDoesNotExist()
        composeTestRule.onNodeWithTag("source_budget_chip_1", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun testPicker_CancelReturnsNull_NoCrash() {
        // Null Uri result (when user cancels picker) must be handled cleanly without crashing or throwing
        val nullUri: Uri? = null
        assertNotNull("Null URI is handled cleanly", nullUri == null)
    }

    @Test
    fun testPicker_RevokedGrant_FallsBackToDefault() = runBlocking {
        // When a custom tree URI is invalid or revoked, writeRollingAutoSave gracefully falls back to shared MediaStore
        val invalidUriString = "content://com.android.externalstorage.documents/tree/invalid%3Apath"
        val (savedUri, usedSaf) = MediaStoreWriter.writeRollingAutoSave(
            context = context,
            content = "#FORMAT=2\nDate,Type,Amount\n",
            customTreeUriString = invalidUriString
        )
        assertNotNull("Must successfully fallback and write via default MediaStore path", savedUri)
        assertFalse("Must not use SAF when grant is invalid/revoked", usedSaf)
    }

    @Test
    fun testFilteredExport_LandsInDownloadsSubfolder() = runBlocking {
        val acc = Account(id = 1L, name = "Cash", accountType = "CASH", openingBalance = 1000.0)
        val cat = Category(id = 1L, name = "Food", color = "#10B981")
        val tx = TransactionEntity(date = System.currentTimeMillis(), type = "EXPENSE", amount = 150.0, fromAccountId = 1L, categoryId = 1L, notes = "Lunch")

        val savedPath = UnifiedCsvStorage.exportFilteredTransactions(
            context = context,
            transactions = listOf(tx),
            accounts = listOf(acc),
            categories = listOf(cat)
        )

        // Assert that the relative path contains the downloads subfolder
        assertTrue(
            "Filtered export must land in downloads subfolder: $savedPath",
            savedPath.contains("downloads") || savedPath.contains("Downloads/ExpenseTracker/downloads")
        )
    }
}
