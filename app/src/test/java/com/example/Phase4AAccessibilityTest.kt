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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import com.example.ui.common.SingleFlightSnackbarHost
import com.example.ui.common.rememberSingleFlightNoticeDispatcher
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.ledger.LedgerRowItem
import com.example.ui.ledger.SplitGroupCardRow
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
@Config(sdk = [33], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Phase4AAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: FinanceRepository
    private lateinit var settingsRepository: AppSettingsRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(db)
        settingsRepository = AppSettingsRepository(context)

        runBlocking {
            settingsRepository.setDemoMode(false)
            settingsRepository.setLockEnabled(false)
            settingsRepository.setHideBalances(false)
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun getCustomActions(testTag: String): List<CustomAccessibilityAction> {
        val node = runCatching {
            composeTestRule.scrollToDashboardTag(testTag, useUnmergedTree = true).fetchSemanticsNode()
        }.getOrElse {
            composeTestRule.onNodeWithTag(testTag, useUnmergedTree = true).fetchSemanticsNode()
        }
        return node.config.getOrNull(SemanticsActions.CustomActions).orEmpty()
    }

    private fun performCustomAction(testTag: String, label: String): Boolean {
        val actions = getCustomActions(testTag)
        val action = actions.find { it.label == label }
            ?: error("Custom action '$label' not found among ${actions.map { it.label }} on tag $testTag")
        return action.action.invoke()
    }

    @Test
    fun ledgerSingleRow_hasDeleteAndEditCustomActions_andBothTriggerCorrectHandlers() {
        var clicked = false
        var deleted = false

        val dummyTx = EnrichedTransaction(
            entity = TransactionEntity(
                id = 42L,
                date = System.currentTimeMillis(),
                amount = 250.0,
                type = "EXPENSE",
                description = "Grocery run"
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                TransactionCardRow(
                    transaction = dummyTx,
                    onClick = { clicked = true },
                    onDelete = { deleted = true }
                )
            }
        }

        val actions = getCustomActions("transaction_row_42")
        val labels = actions.map { it.label }
        assertTrue("Expected 'Delete transaction' in $labels", labels.contains("Delete transaction"))
        assertTrue("Expected 'Edit transaction' in $labels", labels.contains("Edit transaction"))

        composeTestRule.runOnIdle {
            performCustomAction("transaction_row_42", "Edit transaction")
        }
        assertTrue("Click handler should be triggered", clicked)

        composeTestRule.runOnIdle {
            performCustomAction("transaction_row_42", "Delete transaction")
        }
        assertTrue("Delete handler should be triggered", deleted)
    }

    @Test
    fun ledgerSplitGroupRow_hasExpandEditAndDeleteAllCustomActions() {
        var expanded = false
        var edited = false
        var deletedGroup = false

        val line1 = EnrichedTransaction(
            entity = TransactionEntity(id = 101L, date = 1000L, amount = 100.0, type = "EXPENSE", groupId = "grp_1", description = "Split 1")
        )
        val line2 = EnrichedTransaction(
            entity = TransactionEntity(id = 102L, date = 1000L, amount = 150.0, type = "EXPENSE", groupId = "grp_1", description = "Split 2")
        )
        val line3 = EnrichedTransaction(
            entity = TransactionEntity(id = 103L, date = 1000L, amount = 50.0, type = "EXPENSE", groupId = "grp_1", description = "Split 3")
        )

        val splitGroup = LedgerRowItem.SplitGroup(
            groupId = "grp_1",
            lines = listOf(line1, line2, line3)
        )

        composeTestRule.setContent {
            var isExp by remember { mutableStateOf(false) }
            MyApplicationTheme {
                SplitGroupCardRow(
                    splitGroup = splitGroup,
                    isExpanded = isExp,
                    onToggleExpand = {
                        isExp = !isExp
                        expanded = isExp
                    },
                    onEditGroup = { edited = true },
                    onDeleteGroup = { deletedGroup = true }
                )
            }
        }

        val actionsInitial = getCustomActions("split_group_card_grp_1")
        val labelsInitial = actionsInitial.map { it.label }
        assertTrue("Expected 'Expand' in $labelsInitial", labelsInitial.contains("Expand"))
        assertTrue("Expected 'Edit split' in $labelsInitial", labelsInitial.contains("Edit split"))
        assertTrue("Expected 'Delete all 3 split lines' in $labelsInitial", labelsInitial.contains("Delete all 3 split lines"))

        composeTestRule.runOnIdle {
            performCustomAction("split_group_card_grp_1", "Expand")
        }
        composeTestRule.waitForIdle()
        assertTrue("Group should be expanded", expanded)

        // After expanding, action should become 'Collapse'
        val actionsAfter = getCustomActions("split_group_card_grp_1")
        val labelsAfter = actionsAfter.map { it.label }
        assertTrue("Expected 'Collapse' in $labelsAfter", labelsAfter.contains("Collapse"))

        composeTestRule.runOnIdle {
            performCustomAction("split_group_card_grp_1", "Edit split")
        }
        assertTrue("Edit split handler should be triggered", edited)

        composeTestRule.runOnIdle {
            performCustomAction("split_group_card_grp_1", "Delete all 3 split lines")
        }
        assertTrue("Delete group handler should be triggered", deletedGroup)
    }

    @Test
    fun dashboardCreditCard_hasPayDueAndLedgerCustomActions_andSurfacesNoticeWhenNoPaymentNeeded() {
        runBlocking {
            db.accountDao().insert(
                Account(id = 10L, name = "HDFC Credit Card", accountType = "CREDIT_CARD", openingBalance = 0.0)
            )
        }

        val dashboardVm = DashboardViewModel(repository, settingsRepository)
        var filteredToLedger: FilterState? = null

        composeTestRule.setContent {
            val dispatcher = rememberSingleFlightNoticeDispatcher()
            CompositionLocalProvider(LocalNoticeDispatcher provides dispatcher) {
                MyApplicationTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        DashboardScreen(
                            viewModel = dashboardVm,
                            onNavigateToExpenses = { _, _ -> },
                            onNavigateToExpensesWithFilter = { filteredToLedger = it },
                            onNavigateToTransferPrefill = { _, _, _ -> },
                            noticeDispatcher = dispatcher
                        )
                        SingleFlightSnackbarHost(dispatcher = dispatcher)
                    }
                }
            }
        }

        composeTestRule.waitForIdle()

        val cardTag = "source_row_10"
        val actions = getCustomActions(cardTag)
        val labels = actions.map { it.label }
        assertTrue("Expected 'Pay due via transfer' in $labels", labels.contains("Pay due via transfer"))
        assertTrue("Expected 'View in ledger' in $labels", labels.contains("View in ledger"))

        // Net debt is 0, so pay due should surface "No payment needed"
        composeTestRule.runOnIdle {
            performCustomAction(cardTag, "Pay due via transfer")
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("No payment needed").assertIsDisplayed()

        // View in ledger action
        composeTestRule.runOnIdle {
            performCustomAction(cardTag, "View in ledger")
        }
        assertNotNull(filteredToLedger)
        assertEquals(10L, filteredToLedger?.selectedAccountId)
    }

    @Test
    fun dashboardCreditCard_whenNetDebtPositive_payDueTriggersPrefill() {
        runBlocking {
            db.accountDao().insert(
                Account(id = 20L, name = "ICICI Card", accountType = "CREDIT_CARD", openingBalance = 0.0)
            )
            db.transactionDao().insert(
                TransactionEntity(
                    id = 201L,
                    date = System.currentTimeMillis(),
                    amount = 5000.0,
                    type = "EXPENSE",
                    fromAccountId = 20L,
                    description = "Flight booking"
                )
            )
        }

        val dashboardVm = DashboardViewModel(repository, settingsRepository)
        var prefilledCardId: Long? = null
        var prefilledAmount: String? = null

        composeTestRule.setContent {
            val dispatcher = rememberSingleFlightNoticeDispatcher()
            CompositionLocalProvider(LocalNoticeDispatcher provides dispatcher) {
                MyApplicationTheme {
                    DashboardScreen(
                        viewModel = dashboardVm,
                        onNavigateToExpenses = { _, _ -> },
                        onNavigateToTransferPrefill = { _, toCardId, amt ->
                            prefilledCardId = toCardId
                            prefilledAmount = amt
                        },
                        noticeDispatcher = dispatcher
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        val cardTag = "source_row_20"
        composeTestRule.runOnIdle {
            performCustomAction(cardTag, "Pay due via transfer")
        }
        assertEquals(20L, prefilledCardId)
        assertEquals("5000.00", prefilledAmount)
    }

    @Test
    fun dashboardBudgetItem_hasViewTransactionsAndEditBudgetLimitCustomActions() {
        runBlocking {
            db.accountDao().insert(Account(id = 1L, name = "Checking", accountType = "BANK", openingBalance = 5000.0))
            val cat = Category(id = 5L, name = "Dining", iconText = "🍔", color = "#FF5722")
            db.categoryDao().insert(cat)
            db.transactionDao().insert(
                TransactionEntity(
                    id = 501L,
                    date = System.currentTimeMillis(),
                    amount = 400.0,
                    type = "EXPENSE",
                    fromAccountId = 1L,
                    categoryId = 5L,
                    description = "Pizza"
                )
            )
            db.budgetDao().insert(
                BudgetEntity(id = 50L, name = "Dining Out", targetName = "Dining", limitAmount = 4000.0, scopeType = "CATEGORY")
            )
        }

        val dashboardVm = DashboardViewModel(repository, settingsRepository)
        var filterNavigated: FilterState? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                DashboardScreen(
                    viewModel = dashboardVm,
                    onNavigateToExpenses = { _, _ -> },
                    onNavigateToExpensesWithFilter = { filterNavigated = it }
                )
            }
        }

        composeTestRule.waitForIdle()

        val budgetTag = "budget_item_50"
        val actions = getCustomActions(budgetTag)
        val labels = actions.map { it.label }
        assertTrue("Expected 'View transactions' in $labels", labels.contains("View transactions"))
        assertTrue("Expected 'Edit budget limit' in $labels", labels.contains("Edit budget limit"))

        // Invoking "View transactions"
        composeTestRule.runOnIdle {
            performCustomAction(budgetTag, "View transactions")
        }
        assertNotNull(filterNavigated)
        assertEquals(5L, filterNavigated?.selectedCategoryId)

        // Invoking "Edit budget limit" should open the edit dialog
        composeTestRule.runOnIdle {
            performCustomAction(budgetTag, "Edit budget limit")
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Edit Budget: Dining Out").assertIsDisplayed()
        composeTestRule.onNodeWithTag("edit_budget_limit_input").assertIsDisplayed()
    }

    @Test
    fun dashboardTrendBar_hasZoomInOpenLedgerAndReadExactValueCustomActions() {
        val cal = Calendar.getInstance()
        runBlocking {
            db.accountDao().insert(Account(id = 1L, name = "Checking", accountType = "BANK", openingBalance = 10000.0))
            db.transactionDao().insert(
                TransactionEntity(
                    id = 1L,
                    date = cal.timeInMillis,
                    amount = 250.0,
                    type = "EXPENSE",
                    fromAccountId = 1L,
                    description = "Lunch"
                )
            )
        }

        val dashboardVm = DashboardViewModel(repository, settingsRepository)
        var filterNavigated: FilterState? = null

        composeTestRule.setContent {
            val dispatcher = rememberSingleFlightNoticeDispatcher()
            CompositionLocalProvider(LocalNoticeDispatcher provides dispatcher) {
                MyApplicationTheme {
                    DashboardScreen(
                        viewModel = dashboardVm,
                        onNavigateToExpenses = { _, _ -> },
                        onNavigateToExpensesWithFilter = { filterNavigated = it },
                        noticeDispatcher = dispatcher
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.scrollToDashboardTag("trend_bar_chart", useUnmergedTree = true)

        val barMatcher = SemanticsMatcher("has trend_bar_ prefix") { node: SemanticsNode ->
            val tag = node.config.getOrNull(SemanticsProperties.TestTag)
            tag != null && tag.startsWith("trend_bar_") && tag != "trend_bar_chart"
        }

        val barNodes = composeTestRule.onAllNodes(barMatcher, useUnmergedTree = true)
        val barCount = barNodes.fetchSemanticsNodes().size
        assertTrue("At least one trend bar should be displayed", barCount > 0)

        val barNode = barNodes.onFirst()
        val semanticsNode = barNode.fetchSemanticsNode()
        val actions: List<CustomAccessibilityAction> =
            semanticsNode.config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        val labels = actions.map { action -> action.label }

        assertTrue("Expected 'Zoom in' in $labels", labels.contains("Zoom in"))
        assertTrue("Expected 'Open ledger for this period' in $labels", labels.contains("Open ledger for this period"))
        assertTrue("Expected 'Read exact value' in $labels", labels.contains("Read exact value"))

        val openLedgerAction = actions.find { action -> action.label == "Open ledger for this period" }
        assertNotNull(openLedgerAction)
        composeTestRule.runOnIdle {
            openLedgerAction?.action?.invoke()
        }
        assertNotNull("Filter state should have been passed to onNavigateToExpensesWithFilter", filterNavigated)

        val readExactAction = actions.find { action -> action.label == "Read exact value" }
        assertNotNull(readExactAction)
        var handled = false
        composeTestRule.runOnIdle {
            handled = readExactAction?.action?.invoke() == true
        }
        assertTrue(handled)
    }

    @Test
    fun dashboardKpiBalance_hasViewBalanceBreakdownCustomAction() {
        val dashboardVm = DashboardViewModel(repository, settingsRepository)

        composeTestRule.setContent {
            MyApplicationTheme {
                DashboardScreen(
                    viewModel = dashboardVm,
                    onNavigateToExpenses = { _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        val kpiTag = "kpi_current_balance"
        val actions = getCustomActions(kpiTag)
        val labels = actions.map { it.label }
        assertTrue("Expected 'View balance breakdown' in $labels", labels.contains("View balance breakdown"))

        composeTestRule.runOnIdle {
            performCustomAction(kpiTag, "View balance breakdown")
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("balance_breakdown_sheet").assertIsDisplayed()
    }
}
