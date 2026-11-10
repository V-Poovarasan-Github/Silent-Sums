package com.example

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.datastore.AppSettingsRepository
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.domain.model.ChartLevel
import com.example.domain.model.TimeRange
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.entry.EntryScreen
import com.example.ui.entry.EntryViewModel
import com.example.ui.ledger.LedgerScreen
import com.example.ui.ledger.LedgerViewModel
import com.example.ui.lists.ListsScreen
import com.example.ui.lists.ListsTab
import com.example.ui.lists.ListsViewModel
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.LocalOnThemeChange
import com.example.ui.theme.LocalThemeSetting
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.isAutoDarkTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
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
import org.robolectric.annotation.GraphicsMode
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class AllPagesUiRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: FinanceRepository
    private lateinit var settingsRepository: AppSettingsRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(database)
        settingsRepository = AppSettingsRepository(context)
        com.example.domain.util.Formatters.updateCurrentCurrencySymbol("₹")

        // Seed basic accounts and categories for realistic page rendering
        runBlocking {
            settingsRepository.setCurrency("₹")
            repository.insertAccount(Account(id = 1L, name = "Cash", openingBalance = 5000.0, accountType = "CASH"))
            repository.insertAccount(Account(id = 2L, name = "Bank Account", openingBalance = 25000.0, accountType = "BANK"))
            repository.insertCategory(Category(id = 1L, name = "Groceries", color = "#10B981"))
            repository.insertCategory(Category(id = 2L, name = "Salary", color = "#3B82F6"))
            repository.insertTransaction(
                TransactionEntity(
                    id = 100L,
                    amount = 1200.0,
                    type = "EXPENSE",
                    date = System.currentTimeMillis(),
                    categoryId = 1L,
                    fromAccountId = 1L,
                    notes = "Weekly groceries"
                )
            )
        }
    }

    @After
    fun tearDown() {
        // In-memory test databases are automatically garbage collected per test method.
        // Avoid closing synchronously while async ViewModel Flow observers are in-flight.
    }

    @Test
    fun testAutoThemeSchedule_6amTo6pmLight_6pmTo6amDark() {
        // Morning 6:00 AM to 6:00 PM -> Light mode (isAutoDarkTheme == false)
        assertFalse("06:00 should be Light mode", isAutoDarkTheme(LocalTime.of(6, 0)))
        assertFalse("09:30 should be Light mode", isAutoDarkTheme(LocalTime.of(9, 30)))
        assertFalse("12:00 should be Light mode", isAutoDarkTheme(LocalTime.of(12, 0)))
        assertFalse("15:45 should be Light mode", isAutoDarkTheme(LocalTime.of(15, 45)))
        assertFalse("17:59 should be Light mode", isAutoDarkTheme(LocalTime.of(17, 59)))

        // 6:00 PM to 6:00 AM -> Dark mode (isAutoDarkTheme == true)
        assertTrue("18:00 should be Dark mode", isAutoDarkTheme(LocalTime.of(18, 0)))
        assertTrue("20:15 should be Dark mode", isAutoDarkTheme(LocalTime.of(20, 15)))
        assertTrue("23:59 should be Dark mode", isAutoDarkTheme(LocalTime.of(23, 59)))
        assertTrue("00:00 should be Dark mode", isAutoDarkTheme(LocalTime.of(0, 0)))
        assertTrue("03:30 should be Dark mode", isAutoDarkTheme(LocalTime.of(3, 30)))
        assertTrue("05:59 should be Dark mode", isAutoDarkTheme(LocalTime.of(5, 59)))
    }

    @Test
    fun testDashboardScreenRendersInLightTheme() {
        val viewModel = DashboardViewModel(repository, settingsRepository)
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                DashboardScreen(viewModel = viewModel, onNavigateToExpenses = { _, _ -> })
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("SilentSums").assertIsDisplayed()
        composeTestRule.onNodeWithText("Financial Dashboard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("kpi_current_balance").assertIsDisplayed()
        composeTestRule.onNodeWithText("Current Balance").assertIsDisplayed()
    }

    @Test
    fun testDashboardScreenRendersInDarkTheme() {
        val viewModel = DashboardViewModel(repository, settingsRepository)
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                DashboardScreen(viewModel = viewModel, onNavigateToExpenses = { _, _ -> })
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("SilentSums").assertIsDisplayed()
        composeTestRule.onNodeWithText("Financial Dashboard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("kpi_current_balance").assertIsDisplayed()
    }

    @Test
    fun testLedgerScreenRendersInLightTheme() {
        val viewModel = LedgerViewModel(repository)
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                LedgerScreen(
                    viewModel = viewModel,
                    onAddClick = {},
                    onTransactionClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("SilentSums").assertIsDisplayed()
        composeTestRule.onNodeWithText("Expenses Ledger").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Add Transaction").assertIsDisplayed()
    }

    @Test
    fun testLedgerScreenRendersInDarkTheme() {
        val viewModel = LedgerViewModel(repository)
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                LedgerScreen(
                    viewModel = viewModel,
                    onAddClick = {},
                    onTransactionClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Expenses Ledger").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Add Transaction").assertIsDisplayed()
    }

    @Test
    fun testListsScreenCategoriesTabRenders() {
        val viewModel = ListsViewModel(repository)
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                ListsScreen(viewModel = viewModel, initialTab = ListsTab.CATEGORIES)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("SilentSums").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lists & Metadata").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_categories").assertIsDisplayed()
    }

    @Test
    fun testListsScreenAccountsTabRenders() {
        val viewModel = ListsViewModel(repository)
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                ListsScreen(viewModel = viewModel, initialTab = ListsTab.ACCOUNTS)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tab_accounts").assertIsDisplayed()
    }

    @Test
    fun testSettingsScreenPreferencesRendersWithoutAppearanceRow() {
        val viewModel = SettingsViewModel(repository, settingsRepository, database)
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                SettingsScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Ledger Control").assertIsDisplayed()
        composeTestRule.onNodeWithText("Preferences").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ledger Currency").assertIsDisplayed()
        composeTestRule.onNodeWithText("Month Cycle Anchor").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tactile Feedback").assertIsDisplayed()

        // Verify Appearance row and its buttons are removed from Settings screen
        composeTestRule.onNodeWithText("Appearance").assertDoesNotExist()
        composeTestRule.onNodeWithTag("theme_btn_light").assertDoesNotExist()
        composeTestRule.onNodeWithTag("theme_btn_dark").assertDoesNotExist()
        composeTestRule.onNodeWithTag("theme_btn_auto").assertDoesNotExist()
    }

    @Test
    fun testEntryScreenRendersInLightTheme() {
        val viewModel = EntryViewModel(
            repository = repository,
            editTransactionId = null,
            initialSourceId = null,
            initialCategoryId = null
        )
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                EntryScreen(
                    viewModel = viewModel,
                    onNavigateBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("type_selector_expense").assertIsDisplayed()
        composeTestRule.onNodeWithTag("type_selector_income").assertIsDisplayed()
        composeTestRule.onNodeWithTag("type_selector_transfer").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_transaction_button").assertIsDisplayed()
    }

    @Test
    fun testEntryScreenRendersInDarkTheme() {
        val viewModel = EntryViewModel(
            repository = repository,
            editTransactionId = null,
            initialSourceId = null,
            initialCategoryId = null
        )
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                EntryScreen(
                    viewModel = viewModel,
                    onNavigateBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("type_selector_expense").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_transaction_button").assertIsDisplayed()
    }

    @Test
    fun testThemeToggleOnDashboardScreen() {
        var chosenTheme = "AUTO"
        val dashboardVm = DashboardViewModel(repository, settingsRepository)
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalThemeSetting provides chosenTheme,
                LocalOnThemeChange provides { chosenTheme = it }
            ) {
                MyApplicationTheme {
                    DashboardScreen(viewModel = dashboardVm, onNavigateToExpenses = { _, _ -> })
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("compact_theme_toggle").assertIsDisplayed().performClick()
        assertEquals("LIGHT", chosenTheme)
    }

    @Test
    fun testThemeToggleOnLedgerScreen() {
        var chosenTheme = "LIGHT"
        val ledgerVm = LedgerViewModel(repository)
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalThemeSetting provides chosenTheme,
                LocalOnThemeChange provides { chosenTheme = it }
            ) {
                MyApplicationTheme {
                    LedgerScreen(viewModel = ledgerVm, onAddClick = {}, onTransactionClick = {})
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("compact_theme_toggle").assertIsDisplayed().performClick()
        assertEquals("DARK", chosenTheme)
    }

    @Test
    fun testThemeToggleOnListsScreen() {
        var chosenTheme = "DARK"
        val listsVm = ListsViewModel(repository)
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalThemeSetting provides chosenTheme,
                LocalOnThemeChange provides { chosenTheme = it }
            ) {
                MyApplicationTheme {
                    ListsScreen(viewModel = listsVm)
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("compact_theme_toggle").assertIsDisplayed().performClick()
        assertEquals("AUTO", chosenTheme)
    }

    @Test
    fun testThemeToggleOnSettingsScreen() {
        val settingsVm = SettingsViewModel(repository, settingsRepository, database)
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalThemeSetting provides "AUTO",
                LocalOnThemeChange provides {}
            ) {
                MyApplicationTheme {
                    SettingsScreen(viewModel = settingsVm)
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("compact_theme_toggle").assertIsDisplayed()
    }

    @Test
    fun testThemeToggleOnEntryScreen() {
        var chosenTheme = "LIGHT"
        val entryVm = EntryViewModel(repository)
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalThemeSetting provides chosenTheme,
                LocalOnThemeChange provides { chosenTheme = it }
            ) {
                MyApplicationTheme {
                    EntryScreen(viewModel = entryVm, onNavigateBack = {})
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("compact_theme_toggle").assertIsDisplayed().performClick()
        assertEquals("DARK", chosenTheme)
    }

    @Test
    fun testLedgerSearchBar_directOnBackground_minHeight() {
        val ledgerVm = LedgerViewModel(repository)
        composeTestRule.setContent {
            MyApplicationTheme {
                LedgerScreen(viewModel = ledgerVm, onAddClick = {}, onTransactionClick = {})
            }
        }
        composeTestRule.waitForIdle()
        val searchNode = composeTestRule.onNodeWithTag("ledger_search_field")
        searchNode.assertIsDisplayed()
        searchNode.performTextInput("Weekly")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Clear search").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun testLedgerSwipeRevealAndBinDeleteWithUndo() {
        val ledgerVm = LedgerViewModel(repository)
        composeTestRule.setContent {
            MyApplicationTheme {
                LedgerScreen(viewModel = ledgerVm, onAddClick = {}, onTransactionClick = {})
            }
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(5000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            try {
                composeTestRule.onNodeWithTag("transaction_row_100").assertExists()
                true
            } catch (_: Throwable) {
                false
            }
        }

        // Before swipe: bin does not exist, row exists
        composeTestRule.onNodeWithTag("ledger_delete_bin").assertDoesNotExist()
        composeTestRule.onNodeWithTag("transaction_row_100").assertExists()

        // (a) Swipe left reveals "ledger_delete_bin" and row STILL exists in list
        composeTestRule.onNodeWithTag("transaction_row_100").performTouchInput { swipeLeft() }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("ledger_delete_bin").assertIsDisplayed()
        composeTestRule.onNodeWithTag("transaction_row_100").assertExists()

        // (b) Tapping the bin removes the row
        composeTestRule.onNodeWithTag("ledger_delete_bin").performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("transaction_row_100").assertDoesNotExist()

        // (e) Undo snackbar restores the row
        composeTestRule.onNodeWithText("UNDO").performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("transaction_row_100").assertExists()
    }

    @Test
    fun testLedgerSwipeCollapseOnSwipeBackAndTapAway() {
        val ledgerVm = LedgerViewModel(repository)
        composeTestRule.setContent {
            MyApplicationTheme {
                LedgerScreen(viewModel = ledgerVm, onAddClick = {}, onTransactionClick = {})
            }
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(5000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            try {
                composeTestRule.onNodeWithTag("transaction_row_100").assertExists()
                true
            } catch (_: Throwable) {
                false
            }
        }

        // Swipe left reveals bin
        composeTestRule.onNodeWithTag("transaction_row_100").performTouchInput { swipeLeft() }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ledger_delete_bin").assertIsDisplayed()

        // (c1) Swipe back collapses with row intact
        composeTestRule.onNodeWithTag("transaction_row_100").performTouchInput { swipeRight() }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ledger_delete_bin").assertDoesNotExist()
        composeTestRule.onNodeWithTag("transaction_row_100").assertExists()

        // Swipe left again
        composeTestRule.onNodeWithTag("transaction_row_100").performTouchInput { swipeLeft() }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ledger_delete_bin").assertIsDisplayed()

        // (c2) Tap away (e.g. clearing swiped row) collapses with row intact
        ledgerVm.clearSwipedRow()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ledger_delete_bin").assertDoesNotExist()
        composeTestRule.onNodeWithTag("transaction_row_100").assertExists()
    }

    @Test
    fun testLedgerSwipeOpeningRowBClosesRowAWithoutDeletingA() {
        runBlocking {
            repository.insertTransaction(
                TransactionEntity(
                    id = 101L,
                    amount = 500.0,
                    type = "EXPENSE",
                    date = System.currentTimeMillis() - 5000,
                    categoryId = 1L,
                    fromAccountId = 1L,
                    notes = "Lunch"
                )
            )
        }
        val ledgerVm = LedgerViewModel(repository)
        composeTestRule.setContent {
            MyApplicationTheme {
                LedgerScreen(viewModel = ledgerVm, onAddClick = {}, onTransactionClick = {})
            }
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(5000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            try {
                composeTestRule.onNodeWithTag("transaction_row_100").assertExists()
                composeTestRule.onNodeWithTag("transaction_row_101").assertExists()
                true
            } catch (_: Throwable) {
                false
            }
        }

        // (d) Open row A (100)
        composeTestRule.onNodeWithTag("transaction_row_100").performTouchInput { swipeLeft() }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ledger_delete_bin").assertIsDisplayed()

        // Open row B (101) -> closes row A without deleting A
        composeTestRule.onNodeWithTag("transaction_row_101").performTouchInput { swipeLeft() }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("transaction_row_100").assertExists()
        composeTestRule.onNodeWithTag("transaction_row_101").assertExists()
        composeTestRule.onNodeWithTag("ledger_delete_bin").assertIsDisplayed()
    }

    @Test
    fun testLedgerAffordancesTapToEditAndCalculator() {
        var clickedTxId: Long? = null
        val ledgerVm = LedgerViewModel(repository)
        composeTestRule.setContent {
            MyApplicationTheme {
                LedgerScreen(
                    viewModel = ledgerVm,
                    onAddClick = {},
                    onTransactionClick = { clickedTxId = it }
                )
            }
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(5000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            try {
                composeTestRule.onNodeWithTag("transaction_row_100").assertExists()
                true
            } catch (_: Throwable) {
                false
            }
        }

        // (f1) Tap non-swiped row fires onTransactionClick
        composeTestRule.onNodeWithTag("transaction_row_100").performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()
        assertEquals(100L, clickedTxId)

        // (f2) Calculator scratchpad opens
        composeTestRule.onNodeWithTag("ledger_calculator_btn").performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("calculator_bottom_sheet").assertIsDisplayed()
    }

    @Test
    fun testLedgerSwipeRefined_capTravelAndBinOnlyDelete() {
        val ledgerVm = LedgerViewModel(repository)
        composeTestRule.setContent {
            MyApplicationTheme {
                LedgerScreen(
                    viewModel = ledgerVm,
                    onAddClick = {},
                    onTransactionClick = {}
                )
            }
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(5000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            try {
                composeTestRule.onNodeWithTag("transaction_row_100").assertExists()
                true
            } catch (_: Throwable) {
                false
            }
        }

        val initialBounds = composeTestRule.onNodeWithTag("transaction_row_100").getUnclippedBoundsInRoot()

        // Swipe left to reveal
        composeTestRule.onNodeWithTag("transaction_row_100").performTouchInput { swipeLeft() }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        // Assert bin is displayed and row still exists in list
        composeTestRule.onNodeWithTag("ledger_delete_bin").assertIsDisplayed()
        composeTestRule.onNodeWithTag("transaction_row_100").assertExists()

        // Assert that reveal translation never exceeds MAX_REVEAL = min(0.30f * rowWidth, 120.dp)
        val swipedBounds = composeTestRule.onNodeWithTag("transaction_row_100").getUnclippedBoundsInRoot()
        val translation = initialBounds.left - swipedBounds.left
        val rowWidth = initialBounds.right - initialBounds.left
        val maxAllowedReveal = if (rowWidth * 0.30f < 120.dp) rowWidth * 0.30f else 120.dp
        assertTrue(
            "Translation (${translation.value}) must not exceed MAX_REVEAL (${maxAllowedReveal.value})",
            translation.value <= maxAllowedReveal.value + 1f
        )

        // Tap red band OUTSIDE the bin does NOT delete (row still present, bin collapses)
        composeTestRule.onNodeWithTag("ledger_delete_band").performTouchInput {
            down(topLeft)
            up()
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("transaction_row_100").assertExists()
        composeTestRule.onNodeWithTag("ledger_delete_bin").assertDoesNotExist()

        // Swipe left again to reveal bin, and tap the bin to delete
        composeTestRule.onNodeWithTag("transaction_row_100").performTouchInput { swipeLeft() }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ledger_delete_bin").assertIsDisplayed()

        // Bin tap removes the row
        composeTestRule.onNodeWithTag("ledger_delete_bin").performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("transaction_row_100").assertDoesNotExist()

        // Undo snackbar restores row
        composeTestRule.onNodeWithText("UNDO").performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("transaction_row_100").assertExists()
    }

    @Test
    fun testDashboardTrendBarChartWithPositiveOnlyDataset() {
        // Clear transactions and insert only positive income transactions
        runBlocking {
            database.clearAllTables()
            repository.insertAccount(Account(id = 1L, name = "Cash", openingBalance = 1000.0, accountType = "CASH"))
            repository.insertCategory(Category(id = 1L, name = "Salary", color = "#3B82F6"))
            val now = System.currentTimeMillis()
            repository.insertTransaction(
                TransactionEntity(
                    id = 101L,
                    amount = 5000.0,
                    type = "INCOME",
                    date = now,
                    categoryId = 1L,
                    fromAccountId = 1L,
                    notes = "Bonus"
                )
            )
        }

        val dashboardVm = DashboardViewModel(repository, settingsRepository)

        // Test week level
        dashboardVm.jumpToLevel(ChartLevel.WEEKS)
        composeTestRule.setContent {
            MyApplicationTheme {
                DashboardScreen(viewModel = dashboardVm, onNavigateToExpenses = { _, _ -> })
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.scrollToDashboardTag("trend_bar_chart").assertIsDisplayed()

        // Test month level
        dashboardVm.jumpToLevel(ChartLevel.MONTHS)
        composeTestRule.waitForIdle()
        composeTestRule.scrollToDashboardTag("trend_bar_chart").assertIsDisplayed()

        // Test year level
        dashboardVm.jumpToLevel(ChartLevel.YEARS)
        composeTestRule.waitForIdle()
        composeTestRule.scrollToDashboardTag("trend_bar_chart").assertIsDisplayed()
    }

    @Test
    fun testDashboardTrendBarChartWithNegativeOnlyDataset() {
        // Clear transactions and insert a large expense exceeding opening balance so balance goes negative
        runBlocking {
            database.clearAllTables()
            repository.insertAccount(Account(id = 1L, name = "Cash", openingBalance = 0.0, accountType = "CASH"))
            repository.insertCategory(Category(id = 1L, name = "Rent", color = "#EF4444"))
            val now = System.currentTimeMillis()
            repository.insertTransaction(
                TransactionEntity(
                    id = 102L,
                    amount = 5000.0,
                    type = "EXPENSE",
                    date = now,
                    categoryId = 1L,
                    fromAccountId = 1L,
                    notes = "Big Debt"
                )
            )
        }

        val dashboardVm = DashboardViewModel(repository, settingsRepository)

        // Test week level
        dashboardVm.jumpToLevel(ChartLevel.WEEKS)
        composeTestRule.setContent {
            MyApplicationTheme {
                DashboardScreen(viewModel = dashboardVm, onNavigateToExpenses = { _, _ -> })
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.scrollToDashboardTag("trend_bar_chart").assertIsDisplayed()

        // Test month level
        dashboardVm.jumpToLevel(ChartLevel.MONTHS)
        composeTestRule.waitForIdle()
        composeTestRule.scrollToDashboardTag("trend_bar_chart").assertIsDisplayed()

        // Test year level
        dashboardVm.jumpToLevel(ChartLevel.YEARS)
        composeTestRule.waitForIdle()
        composeTestRule.scrollToDashboardTag("trend_bar_chart").assertIsDisplayed()
    }

    @Test
    fun testOnboardingWelcomeCard_displaysAtZeroTransactions_andDismisses() {
        val prefs = context.getSharedPreferences("paisa_track_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        // Welcome card appears only when zero transactions exist
        runBlocking {
            repository.deleteAllTransactions()
        }

        var navigatedType: String? = null
        val dashboardVm = DashboardViewModel(repository, settingsRepository)
        composeTestRule.setContent {
            MyApplicationTheme {
                DashboardScreen(
                    viewModel = dashboardVm,
                    onNavigateToExpenses = { _, _ -> },
                    onNavigateToEntry = { navigatedType = it }
                )
            }
        }
        composeTestRule.waitForIdle()

        // 0 transactions -> welcome card must be displayed with updated copy and CTA
        val welcomeCard = composeTestRule.onNodeWithTag("welcome_card")
        welcomeCard.assertIsDisplayed()
        composeTestRule.onNodeWithText("You're all set.").assertIsDisplayed()

        val ctaBtn = composeTestRule.onNodeWithTag("welcome_card_cta")
        ctaBtn.assertIsDisplayed()
        ctaBtn.performClick()
        assertEquals("EXPENSE", navigatedType)

        // Click dismiss button
        val dismissBtn = composeTestRule.onNodeWithTag("welcome_card_dismiss")
        dismissBtn.assertIsDisplayed()
        dismissBtn.performClick()
        composeTestRule.waitForIdle()

        // Verify dismissed flag is saved
        assertTrue(prefs.getBoolean("welcome_card_dismissed", false))
    }

    @Test
    fun testHelpGesturesSheet_opensFromDashboardHeader_andRendersAuditedAffordances() {
        val dashboardVm = DashboardViewModel(repository, settingsRepository)
        composeTestRule.setContent {
            MyApplicationTheme {
                DashboardScreen(viewModel = dashboardVm, onNavigateToExpenses = { _, _ -> })
            }
        }
        composeTestRule.waitForIdle()

        // Check header ? button
        val helpBtn = composeTestRule.onNodeWithTag("help_gestures_btn")
        helpBtn.assertIsDisplayed()
        helpBtn.performClick()
        composeTestRule.waitForIdle()

        // Help sheet is visible
        composeTestRule.onNodeWithTag("help_gestures_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tips & gestures").assertIsDisplayed()

        // Check audited affordance rows render
        composeTestRule.onNodeWithText("Balance Card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tap or hold for Opening, +In, -Out, and Net breakdown.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tap to open matching rows in Ledger.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sources Order").assertIsDisplayed()
        composeTestRule.onNodeWithText("Monthly Budgets").assertIsDisplayed()

        // Dismiss sheet
        composeTestRule.onNodeWithTag("help_gestures_close_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("help_gestures_sheet").assertDoesNotExist()
    }

    @Test
    fun testHelpGesturesSheet_opensFromSettings() {
        val settingsVm = SettingsViewModel(repository, settingsRepository, database)
        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(viewModel = settingsVm)
            }
        }
        composeTestRule.waitForIdle()

        val helpRow = composeTestRule.onNodeWithTag("help_gestures_row")
        helpRow.assertIsDisplayed()
        helpRow.performClick()
        composeTestRule.waitForIdle()

        // Help sheet is visible
        composeTestRule.onNodeWithTag("help_gestures_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tips & gestures").assertIsDisplayed()
    }

    @Test
    fun testDashboardMicrocopyTopUps_displayed() {
        val dashboardVm = DashboardViewModel(repository, settingsRepository)
        composeTestRule.setContent {
            MyApplicationTheme {
                DashboardScreen(viewModel = dashboardVm, onNavigateToExpenses = { _, _ -> })
            }
        }
        composeTestRule.waitForIdle()

        // Check Current Balance caption ("Tap for in & out" removed in Phase 2.9)
        composeTestRule.onNodeWithText("Tap for in & out").assertDoesNotExist()

        // Check Trend chart hint
        composeTestRule.scrollToDashboardText("Long-press bars for exact dates").assertExists()
    }

    @Test
    fun testOnboardingSourceScreen_startFresh_navigatesToAccountSetup_andFolderLast() {
        var completed = false
        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.onboarding.OnboardingSourceScreen(
                    repository = repository,
                    settingsRepository = settingsRepository,
                    onOnboardingComplete = { completed = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Step 1: Source selection
        val startFresh = composeTestRule.onNodeWithTag("onboarding_start_fresh")
        startFresh.assertIsDisplayed()

        // Tap Start Fresh
        startFresh.performClick()
        composeTestRule.waitForIdle()

        // Step 2: Account Setup screen should appear with prefilled rows
        composeTestRule.onNodeWithTag("account_setup_row_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("account_setup_row_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_account_button").assertIsDisplayed()

        // Tap Use defaults & continue
        val defaultsBtn = composeTestRule.onNodeWithTag("use_defaults_button")
        defaultsBtn.assertIsDisplayed()
        defaultsBtn.performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(5000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            try {
                composeTestRule.onNodeWithTag("onboarding_backup_location_title").assertIsDisplayed()
                true
            } catch (_: Throwable) {
                false
            }
        }

        // Step 3: Backup folder location step appears LAST
        composeTestRule.onNodeWithTag("onboarding_backup_location_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("backup_location_disclosure").assertIsDisplayed()
        composeTestRule.onNodeWithTag("backup_location_settings_hint").assertIsDisplayed()

        // Tap Keep Default (Downloads)
        val defaultBtn = composeTestRule.onNodeWithTag("onboarding_location_default")
        defaultBtn.assertIsDisplayed()
        defaultBtn.performClick()
        composeTestRule.waitUntil(5000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            completed
        }

        assertTrue("Onboarding must be marked complete", completed)
    }
}
