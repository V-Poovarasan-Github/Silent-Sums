package com.example

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.datastore.AppSettingsRepository
import com.example.data.model.Account
import com.example.data.model.BudgetEntity
import com.example.data.model.Category
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.domain.model.EnrichedTransaction
import com.example.domain.model.FilterState
import com.example.ui.common.LocalNoticeDispatcher
import com.example.ui.common.rememberSingleFlightNoticeDispatcher
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.entry.EntryScreen
import com.example.ui.entry.EntryViewModel
import com.example.ui.ledger.TransactionCardRow
import com.example.ui.theme.MyApplicationTheme
import java.util.Calendar
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Phase4BAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var repository: FinanceRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testRoleOnNonStandardClickable_TypePillHasRoleTab() {
        val entryViewModel = EntryViewModel(repository)
        composeTestRule.setContent {
            MyApplicationTheme {
                EntryScreen(
                    viewModel = entryViewModel,
                    onNavigateBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // 4B.2: Verify Role.Tab on non-standard clickable pill
        composeTestRule.onNodeWithTag("type_selector_expense")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Selected"))

        composeTestRule.onNodeWithTag("type_selector_income")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not selected"))
    }

    @Test
    fun testMergeDescendantsOnTransactionCardRow() {
        val testCategory = Category(id = 11L, name = "Groceries", color = "#10B981")
        val testAccount = Account(id = 21L, name = "HDFC Checking", openingBalance = 10000.0)
        val testTx = EnrichedTransaction(
            entity = TransactionEntity(
                id = 101L,
                amount = 1450.0,
                type = "EXPENSE",
                categoryId = 11L,
                fromAccountId = 21L,
                date = System.currentTimeMillis(),
                description = "Weekly Vegetables"
            ),
            category = testCategory,
            fromAccount = testAccount,
            toAccount = null,
            runningBalance = 8550.0
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                TransactionCardRow(
                    transaction = testTx,
                    onClick = {},
                    hideBalances = false,
                    currencySymbol = "₹"
                )
            }
        }
        composeTestRule.waitForIdle()

        // 4B.2: Verify mergeDescendants = true card readout contains unified sentence
        val node = composeTestRule.onNodeWithTag("transaction_row_${testTx.id}")
            .assertIsDisplayed()
            .fetchSemanticsNode()

        val contentDesc = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
        assertNotNull("Expected contentDescription on merged card", contentDesc)
        assertTrue("Expected description to contain title", contentDesc!!.contains("Weekly Vegetables"))
        assertTrue("Expected description to contain category", contentDesc.contains("Groceries"))
        assertTrue("Expected description to contain type", contentDesc.contains("Expense"))
        assertTrue("Expected description to contain amount", contentDesc.contains("1,450"))
    }

    @Test
    fun testHiddenSemanticsUnderPrivacyMode() {
        val testCategory = Category(id = 12L, name = "Dining", color = "#F59E0B")
        val testAccount = Account(id = 22L, name = "Cash", openingBalance = 5000.0)
        val testTx = EnrichedTransaction(
            entity = TransactionEntity(
                id = 102L,
                amount = 800.0,
                type = "EXPENSE",
                categoryId = 12L,
                fromAccountId = 22L,
                date = System.currentTimeMillis(),
                description = "Dinner"
            ),
            category = testCategory,
            fromAccount = testAccount,
            toAccount = null,
            runningBalance = 4200.0
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                TransactionCardRow(
                    transaction = testTx,
                    onClick = {},
                    hideBalances = true,
                    currencySymbol = "₹"
                )
            }
        }
        composeTestRule.waitForIdle()

        // 4B.3: Privacy mode (hideBalances) on: masked amounts have semantics text "hidden"
        val cardNode = composeTestRule.onNodeWithTag("transaction_row_${testTx.id}")
            .assertIsDisplayed()
            .fetchSemanticsNode()

        val cardDesc = cardNode.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
        assertNotNull("Expected contentDescription on card under privacy mode", cardDesc)
        assertTrue("Card description must announce amount hidden", cardDesc!!.contains("amount hidden"))
        assertTrue("Card description must announce balance hidden", cardDesc.contains("balance hidden"))

        val amountNode = composeTestRule.onNodeWithTag("tx_amount_${testTx.id}", useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode()
        val amountDesc = amountNode.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
        assertEquals("hidden", amountDesc)
    }

    @Test
    fun testStateDescriptionOnBudgetCard() = runBlocking {
        val appSettingsRepo = AppSettingsRepository(context)
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH) + 1
        val currentYear = calendar.get(Calendar.YEAR)

        database.accountDao().insert(Account(id = 31L, name = "Bank", accountType = "BANK", openingBalance = 50000.0))
        database.categoryDao().insert(Category(id = 32L, name = "Dining", color = "#F59E0B"))
        database.budgetDao().insert(
            BudgetEntity(
                id = 55L,
                name = "Dining Out",
                limitAmount = 5000.0,
                scopeType = "CATEGORY",
                targetName = "Dining"
            )
        )

        // Add 2000 spent -> 40% -> "under limit"
        database.transactionDao().insert(
            TransactionEntity(
                id = 505L,
                amount = 2000.0,
                type = "EXPENSE",
                categoryId = 32L,
                fromAccountId = 31L,
                date = calendar.timeInMillis,
                description = "Restaurant"
            )
        )
        val budgetId = 55L

        val dashboardViewModel = DashboardViewModel(repository, appSettingsRepo)

        composeTestRule.setContent {
            val testDispatcher = rememberSingleFlightNoticeDispatcher()
            CompositionLocalProvider(LocalNoticeDispatcher provides testDispatcher) {
                MyApplicationTheme {
                    DashboardScreen(
                        viewModel = dashboardViewModel,
                        onNavigateToExpenses = { _, _ -> },
                        noticeDispatcher = testDispatcher
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // 4B.3: Assert stateDescription conveys "under limit"
        composeTestRule.onNodeWithTag("budget_item_$budgetId")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "under limit"))

        val budgetNode = composeTestRule.onNodeWithTag("budget_item_$budgetId")
            .fetchSemanticsNode()
        val budgetDesc = budgetNode.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
        assertNotNull(budgetDesc)
        assertTrue(budgetDesc!!.contains("Dining Out"))
        assertTrue(budgetDesc.contains("under limit"))
    }

    @Test
    fun testFontScale200PercentNonClipping() {
        val entryViewModel = EntryViewModel(repository)

        // 4B.8: Test with 200% font scale override
        composeTestRule.setContent {
            val currentDensity = LocalDensity.current
            val scaledDensity = Density(
                density = currentDensity.density,
                fontScale = 2.0f
            )
            CompositionLocalProvider(LocalDensity provides scaledDensity) {
                MyApplicationTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        EntryScreen(
                            viewModel = entryViewModel,
                            onNavigateBack = {}
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        // Assert primary components are laid out and displayed without clipping or errors
        composeTestRule.onNodeWithTag("type_selector_expense").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_transaction_button").assertIsDisplayed()
    }
}
