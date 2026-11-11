package com.example

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
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
import com.example.ui.dashboard.AUDITED_AFFORDANCES
import com.example.ui.dashboard.COMMON_TASKS_AFFORDANCES
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.dashboard.GestureHelpItem
import com.example.ui.dashboard.HelpGesturesSheet
import com.example.ui.dashboard.HelpSection
import com.example.ui.dashboard.TALKBACK_HELP_NOTE
import com.example.ui.ledger.TransactionCardRow
import com.example.ui.lists.ListsTab
import com.example.ui.theme.MyApplicationTheme
import java.io.File
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
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Phase5HelpAccuracyTest {

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

    @Test
    fun test5_1_dashboardTrendChartHint_saysTapToZoomHoldToOpenLedger() {
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

        composeTestRule.scrollToDashboardTag("trend_card_hint", useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.scrollToDashboardText("Tap to zoom, hold to open ledger", useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Long-press bars for exact dates", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun test5_2_creditCardRowsTypoFixed_andNoDoublePeriodsInAnyHelpEntry() {
        val allItems = AUDITED_AFFORDANCES + COMMON_TASKS_AFFORDANCES
        val cardEntry = allItems.firstOrNull { it.gesture == "Pay Credit Card Due?" }
        assertNotNull("Pay Credit Card Due? entry must exist", cardEntry)
        assertEquals(
            "Credit Card Rows: TAP opens ledger; HOLD opens a pre-filled transfer of the amount due.",
            cardEntry!!.result
        )
        for (item in allItems) {
            assertFalse(
                "Help entry '${item.gesture}' must not contain double period '..': '${item.fullLine}'",
                item.fullLine.contains("..")
            )
        }
    }

    @Test
    fun test5_3_listsTabHelpEntry_matchesRealListsTabsInCode() {
        val listsTabs = ListsTab.entries
        assertEquals("ListsScreen must have exactly 4 tabs", 4, listsTabs.size)
        assertEquals(
            listOf(ListsTab.CATEGORIES, ListsTab.ACCOUNTS, ListsTab.FIELDS, ListsTab.BUDGETS),
            listsTabs
        )
        val tabsHelp = AUDITED_AFFORDANCES.first { it.section == HelpSection.LISTS && it.gesture == "4 Tabs" }
        assertEquals(
            "4 Tabs: Manage Categories, Sources, Fields, and Budgets from the top bar.",
            tabsHelp.result
        )
    }

    @Test
    fun test5_4_talkBackLine_displayedInAllHelpSheetSections() {
        assertEquals(
            "With TalkBack, use the Actions menu on rows and cards instead of swiping or holding.",
            TALKBACK_HELP_NOTE
        )
        val sectionState = mutableStateOf(HelpSection.DASHBOARD)
        composeTestRule.setContent {
            MyApplicationTheme {
                HelpGesturesSheet(
                    section = sectionState.value,
                    onDismiss = {}
                )
            }
        }

        for (section in listOf(HelpSection.DASHBOARD, HelpSection.EXPENSES, HelpSection.LISTS, HelpSection.ALL)) {
            sectionState.value = section
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("help_gestures_talkback_note").assertIsDisplayed()
            composeTestRule.onNodeWithText(TALKBACK_HELP_NOTE).assertIsDisplayed()
        }
    }

    /**
     * 5.6: Fails if any help entry mentions a gesture (swipe, long-press, hold, drag)
     * that has no matching handler or customAction in the screen it describes.
     */
    @Test
    fun test5_6_helpEntriesMentioningGestures_haveMatchingHandlerAndCustomAction() = runBlocking {
        // Seed representative data on Dashboard and Ledger so all interactive rows render
        val bankId = repository.insertAccount(Account(id = 1L, name = "Main Bank", accountType = "BANK", openingBalance = 10000.0))
        val cardId = repository.insertAccount(Account(id = 2L, name = "ICICI Card", accountType = "CREDIT_CARD", openingBalance = -1500.0))
        val catId = repository.insertCategory(Category(id = 1L, name = "Food", color = "#EF4444"))
        val txId = repository.insertTransaction(
            TransactionEntity(
                id = 10L,
                amount = 450.0,
                date = System.currentTimeMillis(),
                type = "EXPENSE",
                fromAccountId = cardId,
                categoryId = catId,
                description = "Lunch"
            )
        )
        val budgetId = repository.insertBudget(
            BudgetEntity(
                id = 1L,
                name = "Food Budget",
                limitAmount = 5000.0,
                scopeType = "CATEGORY",
                targetName = "Food"
            )
        )

        val dashboardVm = DashboardViewModel(repository, settingsRepository)
        val showLedgerRow = mutableStateOf(false)
        val sampleTx = EnrichedTransaction(
            entity = TransactionEntity(
                id = txId,
                amount = 450.0,
                date = System.currentTimeMillis(),
                type = "EXPENSE",
                fromAccountId = cardId,
                categoryId = catId,
                description = "Lunch"
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                if (!showLedgerRow.value) {
                    DashboardScreen(
                        viewModel = dashboardVm,
                        onNavigateToExpenses = { _, _ -> }
                    )
                } else {
                    TransactionCardRow(
                        transaction = sampleTx,
                        onClick = {},
                        onDelete = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // Scroll Dashboard to collect semantics of all gesture-bearing Dashboard components
        val dashboardCustomActionsByKey = mutableMapOf<String, List<String>>()
        val dashboardHasLongClickByKey = mutableMapOf<String, Boolean>()

        fun recordDashboardNode(key: String, tag: String) {
            val node = runCatching {
                composeTestRule.scrollToDashboardTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            }.getOrElse {
                composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            }
            val actions = node.config.getOrNull(SemanticsActions.CustomActions)?.map { it.label }.orEmpty()
            val hasLongClick = SemanticsActions.OnLongClick in node.config
            dashboardCustomActionsByKey[key] = actions
            dashboardHasLongClickByKey[key] = hasLongClick
        }

        recordDashboardNode("Dashboard:Balance Card", "kpi_current_balance")
        recordDashboardNode("Dashboard:Pay Credit Card Due?", "source_row_$cardId")
        recordDashboardNode("Dashboard:Monthly Budgets", "budget_item_$budgetId")

        // Trend bar
        composeTestRule.scrollToDashboardTag("trend_bar_chart", useUnmergedTree = true)
        composeTestRule.waitForIdle()
        val trendBarNode = composeTestRule.onAllNodes(
            SemanticsMatcher("is trend_bar") { node ->
                val tag = node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)
                tag != null && tag.startsWith("trend_bar_") && tag != "trend_bar_chart"
            },
            useUnmergedTree = true
        ).onFirst().fetchSemanticsNode()
        dashboardCustomActionsByKey["Dashboard:Trend Bars"] =
            trendBarNode.config.getOrNull(SemanticsActions.CustomActions)?.map { it.label }.orEmpty()
        dashboardHasLongClickByKey["Dashboard:Trend Bars"] =
            SemanticsActions.OnLongClick in trendBarNode.config

        // Switch to Ledger row to collect Ledger semantics
        showLedgerRow.value = true
        composeTestRule.waitForIdle()
        val ledgerNode = composeTestRule.onNodeWithTag("transaction_row_$txId", useUnmergedTree = true).fetchSemanticsNode()
        val ledgerCustomActions =
            ledgerNode.config.getOrNull(SemanticsActions.CustomActions)?.map { it.label }.orEmpty()

        // Verify all current help items pass the gesture drift check
        val allItems = AUDITED_AFFORDANCES + COMMON_TASKS_AFFORDANCES
        val verifiedCount = verifyHelpGestureItemsAgainstCodeAndSemantics(
            items = allItems,
            dashboardCustomActionsByKey = dashboardCustomActionsByKey,
            dashboardHasLongClickByKey = dashboardHasLongClickByKey,
            ledgerCustomActions = ledgerCustomActions
        )
        assertEquals(
            "Expected exactly 5 gesture-bearing help entries (Balance Card, Pay Credit Card Due?, Monthly Budgets, Trend Bars, Delete & Undo)",
            5,
            verifiedCount
        )

        // Verify that adding an unbacked gesture mention to a help item is immediately caught
        val driftedItem = GestureHelpItem(
            icon = "📑",
            screen = "Lists",
            gesture = "Categories",
            result = "Categories: Hold or swipe category row to duplicate.",
            section = HelpSection.LISTS
        )
        var driftCaught = false
        try {
            verifyHelpGestureItemsAgainstCodeAndSemantics(
                items = listOf(driftedItem),
                dashboardCustomActionsByKey = dashboardCustomActionsByKey,
                dashboardHasLongClickByKey = dashboardHasLongClickByKey,
                ledgerCustomActions = ledgerCustomActions
            )
        } catch (e: AssertionError) {
            driftCaught = true
        }
        assertTrue(
            "Drift detector must fail when a help entry mentions a gesture (swipe/hold/long-press/drag) without a matching handler and customAction",
            driftCaught
        )
    }

    private data class GestureVerificationSpec(
        val screenFile: String,
        val requiredSourceTokens: List<String>,
        val expectedCustomActionLabel: String,
        val requiresLongClick: Boolean
    )

    private fun resolveSourceFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct
        val prefixed = File("app/$relativePath")
        if (prefixed.exists()) return prefixed
        return direct
    }

    private fun verifyHelpGestureItemsAgainstCodeAndSemantics(
        items: List<GestureHelpItem>,
        dashboardCustomActionsByKey: Map<String, List<String>>,
        dashboardHasLongClickByKey: Map<String, Boolean>,
        ledgerCustomActions: List<String>
    ): Int {
        val gestureRegex = Regex("\\b(swipe|swiping|long-press|long press|hold|holding|drag|dragging)\\b", RegexOption.IGNORE_CASE)

        val registeredSpecs = mapOf(
            "Dashboard:Balance Card" to GestureVerificationSpec(
                screenFile = "src/main/java/com/example/ui/dashboard/DashboardScreen.kt",
                requiredSourceTokens = listOf("kpi_current_balance", "combinedClickable", "onLongClick", "View balance breakdown"),
                expectedCustomActionLabel = "View balance breakdown",
                requiresLongClick = true
            ),
            "Dashboard:Pay Credit Card Due?" to GestureVerificationSpec(
                screenFile = "src/main/java/com/example/ui/dashboard/DashboardScreen.kt",
                requiredSourceTokens = listOf("source_row_", "combinedClickable", "onLongClick", "Pay due via transfer"),
                expectedCustomActionLabel = "Pay due via transfer",
                requiresLongClick = true
            ),
            "Dashboard:Monthly Budgets" to GestureVerificationSpec(
                screenFile = "src/main/java/com/example/ui/dashboard/DashboardScreen.kt",
                requiredSourceTokens = listOf("budget_item_", "combinedClickable", "onLongClick", "Edit budget limit"),
                expectedCustomActionLabel = "Edit budget limit",
                requiresLongClick = true
            ),
            "Dashboard:Trend Bars" to GestureVerificationSpec(
                screenFile = "src/main/java/com/example/ui/dashboard/DashboardScreen.kt",
                requiredSourceTokens = listOf("trend_bar_", "combinedClickable", "onLongClick", "Open ledger for this period"),
                expectedCustomActionLabel = "Open ledger for this period",
                requiresLongClick = true
            ),
            "Ledger:Delete & Undo" to GestureVerificationSpec(
                screenFile = "src/main/java/com/example/ui/ledger/LedgerScreen.kt",
                requiredSourceTokens = listOf("transaction_row_", "ledger_delete_band", "Delete transaction"),
                expectedCustomActionLabel = "Delete transaction",
                requiresLongClick = false
            )
        )

        var gestureItemsChecked = 0
        for (item in items) {
            val match = gestureRegex.find(item.fullLine) ?: continue
            gestureItemsChecked++
            val key = "${item.screen}:${item.gesture}"
            val spec = registeredSpecs[key]
                ?: throw AssertionError(
                    "Help entry '$key' mentions gesture '${match.value}' in '${item.fullLine}', " +
                        "but no matching gesture handler or customAction exists on screen '${item.screen}'."
                )

            val file = resolveSourceFile(spec.screenFile)
            assertTrue("Screen source file must exist for $key: ${file.path}", file.exists())
            val source = file.readText()
            for (token in spec.requiredSourceTokens) {
                assertTrue(
                    "Screen '${item.screen}' (${file.name}) is missing required gesture/customAction token '$token' for help entry '$key'",
                    source.contains(token)
                )
            }

            if (item.screen == "Dashboard") {
                val liveActions = dashboardCustomActionsByKey[key].orEmpty()
                assertTrue(
                    "Live Dashboard node for '$key' must expose customAction '${spec.expectedCustomActionLabel}', found: $liveActions",
                    spec.expectedCustomActionLabel in liveActions
                )
                if (spec.requiresLongClick) {
                    assertTrue(
                        "Live Dashboard node for '$key' must have an OnLongClick semantics action backing '${match.value}'",
                        dashboardHasLongClickByKey[key] == true
                    )
                }
            } else if (item.screen == "Ledger") {
                assertTrue(
                    "Live Ledger row for '$key' must expose customAction '${spec.expectedCustomActionLabel}', found: $ledgerCustomActions",
                    spec.expectedCustomActionLabel in ledgerCustomActions
                )
            }
        }
        return gestureItemsChecked
    }
}
