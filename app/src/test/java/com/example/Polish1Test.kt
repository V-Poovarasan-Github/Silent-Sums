package com.example

import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.datastore.AppSettingsRepository
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.repository.FinanceRepository
import com.example.ui.common.CompactThemeToggle
import com.example.ui.dashboard.HelpGesturesSheet
import com.example.ui.dashboard.HelpSection
import com.example.ui.entry.EntryFormState
import com.example.ui.entry.EntryScreen
import com.example.ui.entry.EntryViewModel
import com.example.ui.entry.SplitAllocationLine
import com.example.ui.theme.LocalOnThemeChange
import com.example.ui.theme.LocalThemeSetting
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Polish1Test {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: FinanceRepository
    private lateinit var settingsRepository: AppSettingsRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(database)
        settingsRepository = AppSettingsRepository(context)

        runBlocking {
            settingsRepository.setCurrency("₹")
            com.example.domain.util.Formatters.updateCurrentCurrencySymbol("₹")
            repository.insertAccount(Account(id = 1, name = "Bank Account", accountType = "BANK", openingBalance = 1000.0))
            repository.insertCategory(Category(id = 1, name = "Food", color = "#EF4444"))
        }
    }

    // --- PT1: Split Total-First Enforcement ---

    @Test
    fun PT1_splitTotalFirst_stateAndGatingCondition() {
        // (1) Total unset (0.0): leftToAssign is 0.0 - 0.0 = 0.0, but totalAmount <= 0.0
        val stateUnset = EntryFormState(
            isSplit = true,
            amountText = "",
            splitLines = listOf(
                SplitAllocationLine(id = "1", amountText = ""),
                SplitAllocationLine(id = "2", amountText = "")
            )
        )
        assertEquals(0.0, stateUnset.totalAmount, 0.001)
        assertFalse("Save must be disabled when total is unset", stateUnset.isSplitValidToSave)

        // (2) Total set to 500, lines empty: leftToAssign = 500
        val stateWithTotal = stateUnset.copy(amountText = "500")
        assertEquals(500.0, stateWithTotal.totalAmount, 0.001)
        assertEquals(500.0, stateWithTotal.leftToAssign, 0.001)
        assertFalse("Save disabled until all funds assigned", stateWithTotal.isSplitValidToSave)

        // (3) Total set to 500, lines fully assigned (300 + 200 = 500)
        val stateBalanced = stateWithTotal.copy(
            splitLines = listOf(
                SplitAllocationLine(id = "1", amountText = "300"),
                SplitAllocationLine(id = "2", amountText = "200")
            )
        )
        assertEquals(0.0, stateBalanced.leftToAssign, 0.001)
        assertTrue("Save enabled when balanced and lines > 0", stateBalanced.isSplitValidToSave)

        // (4) Over-assigned: lines sum to 600 > 500
        val stateOver = stateWithTotal.copy(
            splitLines = listOf(
                SplitAllocationLine(id = "1", amountText = "350"),
                SplitAllocationLine(id = "2", amountText = "250")
            )
        )
        assertEquals(-100.0, stateOver.leftToAssign, 0.001)
        assertFalse("Save disabled when over-assigned", stateOver.isSplitValidToSave)
    }

    @Test
    fun PT1_splitTotalFirst_uiGatingAndTotalField() {
        val viewModel = EntryViewModel(repository)
        composeTestRule.setContent {
            MyApplicationTheme {
                EntryScreen(viewModel = viewModel, onNavigateBack = {})
            }
        }
        composeTestRule.waitForIdle()

        // Toggle Split ON (scroll to toggle switch first)
        composeTestRule.onNodeWithTag("entry_split_toggle").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // (a) Prominent Total field must appear at the top of split block
        composeTestRule.onNodeWithTag("split_total_input").performScrollTo().assertIsDisplayed()

        // (b) Live counter banner while total is unset MUST show "Enter a total first" (NOT "Over-assigned by")
        composeTestRule.onNodeWithTag("entry_left_to_assign").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Enter a total first").assertIsDisplayed()
        composeTestRule.onNodeWithText("Over-assigned", substring = true).assertDoesNotExist()

        // Lines must be disabled while Total is unset
        composeTestRule.onNodeWithTag("split_amount_0").performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithTag("split_amount_1").performScrollTo().assertIsNotEnabled()

        // Save button must be disabled
        composeTestRule.onNodeWithTag("save_transaction_button").assertIsNotEnabled()

        // Set Total = 2400 into split_total_input
        composeTestRule.onNodeWithTag("split_total_input").performScrollTo().performTextInput("2400")
        composeTestRule.waitForIdle()

        // Lines must now be enabled
        composeTestRule.onNodeWithTag("split_amount_0").performScrollTo().assertIsEnabled()
        composeTestRule.onNodeWithTag("split_amount_1").performScrollTo().assertIsEnabled()

        // Live counter reads "2,400.00 left to assign"
        composeTestRule.onNodeWithText("2,400.00 left to assign", substring = true).assertExists()

        // Fill lines to 2400 (Line 1 = 1200, Line 2 = 1200)
        composeTestRule.onNodeWithTag("split_amount_0").performScrollTo().performTextInput("1200")
        composeTestRule.onNodeWithTag("split_amount_1").performScrollTo().performTextInput("1200")
        composeTestRule.waitForIdle()

        // Live counter reads "All funds assigned (₹0.00 left)"
        composeTestRule.onNodeWithText("All funds assigned (₹0.00 left)").assertExists()

        // Save button is now enabled
        composeTestRule.onNodeWithTag("save_transaction_button").assertIsEnabled()

        // Over-fill to 2600: change Line 2 to 1400 (1200 + 1400 = 2600)
        composeTestRule.onNodeWithTag("split_amount_1").performScrollTo().performTextReplacement("1400")
        composeTestRule.waitForIdle()

        // Over-assigned by ₹200.00 banner displayed, Save disabled
        composeTestRule.onNodeWithText("Over-assigned by ₹200.00").assertExists()
        composeTestRule.onNodeWithTag("save_transaction_button").assertIsNotEnabled()

        // Toggle Split OFF -> returns to normal flow, split_total_input disappears
        composeTestRule.onNodeWithTag("entry_split_toggle").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("split_total_input").assertDoesNotExist()
    }

    // --- PT2: Editing an existing 2-line split pre-fills Total and obeys gating ---

    @Test
    fun PT2_splitEditMode_prefillsTotalAndObeysGating() {
        val groupId = "test_split_group_pt2"
        var tx1Id = 0L
        runBlocking {
            tx1Id = repository.insertTransaction(
                com.example.data.model.TransactionEntity(
                    date = System.currentTimeMillis(),
                    type = "EXPENSE",
                    amount = 1000.0,
                    fromAccountId = 1,
                    categoryId = 1,
                    description = "Split Groceries",
                    groupId = groupId
                )
            )
            repository.insertTransaction(
                com.example.data.model.TransactionEntity(
                    date = System.currentTimeMillis(),
                    type = "EXPENSE",
                    amount = 1400.0,
                    fromAccountId = 1,
                    categoryId = 1,
                    description = "Split Groceries",
                    groupId = groupId
                )
            )
        }

        val viewModel = EntryViewModel(repository, editTransactionId = tx1Id)
        composeTestRule.setContent {
            MyApplicationTheme {
                EntryScreen(viewModel = viewModel, onNavigateBack = {})
            }
        }
        composeTestRule.waitForIdle()

        // Total should be prefilled to 2400
        composeTestRule.onNodeWithTag("split_total_input").performScrollTo().assertIsDisplayed()
        assertEquals(2400.0, viewModel.formState.value.totalAmount, 0.001)

        // Lines are enabled
        composeTestRule.onNodeWithTag("split_amount_0").performScrollTo().assertIsEnabled()
        composeTestRule.onNodeWithTag("split_amount_1").performScrollTo().assertIsEnabled()

        // Fully assigned -> Save enabled
        composeTestRule.onNodeWithText("All funds assigned (₹0.00 left)").assertExists()
        composeTestRule.onNodeWithTag("save_transaction_button").assertIsEnabled()

        // Over-fill lines: set Line 2 to 1600 (1000 + 1600 = 2600 > 2400)
        composeTestRule.onNodeWithTag("split_amount_1").performScrollTo().performTextReplacement("1600")
        composeTestRule.waitForIdle()

        // Over-assigned banner, Save disabled
        composeTestRule.onNodeWithText("Over-assigned by ₹200.00").assertExists()
        composeTestRule.onNodeWithTag("save_transaction_button").assertIsNotEnabled()

        // Unset total: clear split_total_input
        composeTestRule.onNodeWithTag("split_total_input").performScrollTo().performTextReplacement("")
        composeTestRule.waitForIdle()

        // Lines must be disabled and banner shows "Enter a total first"
        composeTestRule.onNodeWithText("Enter a total first").assertExists()
        composeTestRule.onNodeWithTag("split_amount_0").performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithTag("split_amount_1").performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithTag("save_transaction_button").assertIsNotEnabled()
    }

    // --- PT3: Single Circle Theme Toggle (A -> Sun -> Moon -> A) ---

    @Test
    fun PT3_themeControl_singleCircleCyclesAutoLightDark() {
        var currentTheme by mutableStateOf("AUTO")
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalThemeSetting provides currentTheme,
                LocalOnThemeChange provides { currentTheme = it }
            ) {
                MyApplicationTheme {
                    CompactThemeToggle()
                }
            }
        }
        composeTestRule.waitForIdle()

        // Verify the single circle toggle displays "A" in AUTO mode
        composeTestRule.onNodeWithTag("compact_theme_toggle").assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithText("A").assertIsDisplayed()

        // Tap 1: AUTO -> LIGHT (Sun icon)
        composeTestRule.onNodeWithTag("compact_theme_toggle").performClick()
        composeTestRule.waitForIdle()
        assertEquals("LIGHT", currentTheme)
        composeTestRule.onNodeWithContentDescription("Light Theme").assertIsDisplayed()

        // Tap 2: LIGHT -> DARK (Moon icon)
        composeTestRule.onNodeWithTag("compact_theme_toggle").performClick()
        composeTestRule.waitForIdle()
        assertEquals("DARK", currentTheme)
        composeTestRule.onNodeWithContentDescription("Dark Theme").assertIsDisplayed()

        // Tap 3: DARK -> AUTO ("A")
        composeTestRule.onNodeWithTag("compact_theme_toggle").performClick()
        composeTestRule.waitForIdle()
        assertEquals("AUTO", currentTheme)
        composeTestRule.onNodeWithText("A").assertIsDisplayed()
    }

    // --- PT4: Refresh Tips & Gestures Content ---

    @Test
    fun PT4_helpGesturesSheet_expensesContentRefreshed() {
        composeTestRule.setContent {
            MyApplicationTheme {
                HelpGesturesSheet(section = HelpSection.EXPENSES, onDismiss = {})
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Swipe left, tap red bin to delete, or tap UNDO.").assertExists()
        composeTestRule.onNodeWithText("Tap card to expand lines inline; tap ✎ to edit.").assertExists()
        composeTestRule.onNodeWithText("Keypad solves math; enter Total first for splits.").assertExists()
        composeTestRule.onNodeWithText("Tap 📷 or 🎤 for media; tap category to re-tag.").assertExists()
        composeTestRule.onNodeWithText("Tap any row to edit amount, account, date, notes, or media.").assertExists()
        composeTestRule.onNodeWithText("Search text, tags, or amounts; tap 🧮 for calculator.").assertExists()
        composeTestRule.onNodeWithText("Top download icon saves only filtered rows to your data folder.").assertExists()
    }

    @Test
    fun PT4_helpGesturesSheet_dashboardContentRefreshed() {
        composeTestRule.setContent {
            MyApplicationTheme {
                HelpGesturesSheet(section = HelpSection.DASHBOARD, onDismiss = {})
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Tap or hold for Opening, +In, -Out, and Net breakdown.").assertExists()
        composeTestRule.onNodeWithText("Tap to open matching rows in Ledger.").assertExists()
        composeTestRule.onNodeWithText("Auto-sorted by highest bank balance and largest card debt.").assertExists()
        composeTestRule.onNodeWithText("Tap to view rows; hold to edit limit inline.").assertExists()
        composeTestRule.onNodeWithText("Tap to zoom; hold to open the Ledger at that date.").assertExists()
        composeTestRule.onNodeWithText("Pick more than one period, pin a week label, or zoom out.").assertExists()
        composeTestRule.onNodeWithText("Switch between All, Expense, Income, Transfer, months, years, or dates.").assertExists()
        composeTestRule.onNodeWithText("Bell settles bills and IOUs; ☀️/🌙/A sets Light, Dark, or Auto.").assertExists()
    }

    @Test
    fun PT4_helpGesturesSheet_listsContentRefreshed() {
        composeTestRule.setContent {
            MyApplicationTheme {
                HelpGesturesSheet(section = HelpSection.LISTS, onDismiss = {})
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Manage Categories, Sources, Fields, and Budgets from the top bar.").assertExists()
        composeTestRule.onNodeWithText("Unused items delete; used items archive to protect history.").assertExists()
        composeTestRule.onNodeWithText("Paste any emoji, pick from 24 colors, and reorder with arrows.").assertExists()
        composeTestRule.onNodeWithText("Set Bank, Cash, or Card; editing Opening Balance recalculates all balances.").assertExists()
        composeTestRule.onNodeWithText("Add Text, Number, Dropdown, or Date fields per transaction type.").assertExists()
        composeTestRule.onNodeWithText("Set monthly limits for a Category, a Source/Card, or All Spend.").assertExists()
    }

    @Test
    fun PT4_helpGesturesSheet_settingsContentRefreshed() {
        composeTestRule.setContent {
            MyApplicationTheme {
                HelpGesturesSheet(section = HelpSection.ALL, onDismiss = {})
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Set your month start day (1–28) for budgets and cycles.").assertExists()
        composeTestRule.onNodeWithText("Mask amounts in public, or lock with PIN and biometrics.").assertExists()
        composeTestRule.onNodeWithText("Toggle Dashboard budgets, ? help buttons, or bill due alerts.").assertExists()
        composeTestRule.onNodeWithText("Saves daily to your data folder and safety net.").assertExists()
        composeTestRule.onNodeWithText("Import a CSV file or Restore from CSV Backup anytime.").assertExists()
        composeTestRule.onNodeWithText("Runs in an isolated sandbox so real data stays untouched.").assertExists()
    }

    @Test
    fun PT4_staleStringsAbsent() {
        val allResults = (com.example.ui.dashboard.AUDITED_AFFORDANCES + com.example.ui.dashboard.COMMON_TASKS_AFFORDANCES).map { it.result }
        // Verify no removed controls or stale strings are mentioned
        assertFalse(allResults.any { it.contains("pill row", ignoreCase = true) })
        assertFalse(allResults.any { it.contains("deleted restore", ignoreCase = true) })
    }

    // --- POLISH-1.1 / POLISH-1.4 Tests (HT1, HT2, HT3a, HT3b, HT3c, C3, C4) ---

    @Test
    fun HT1_themeSegmentedRow_singleJoinedContainerNoGapsAndSelectsModes() {
        var currentTheme by androidx.compose.runtime.mutableStateOf("AUTO")
        composeTestRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalThemeSetting provides currentTheme,
                LocalOnThemeChange provides { currentTheme = it }
            ) {
                MyApplicationTheme {
                    CompactThemeToggle()
                }
            }
        }
        composeTestRule.waitForIdle()

        // Single circular HeaderIconButton container exists with click action and no legacy segment tags
        composeTestRule.onNodeWithTag("compact_theme_toggle").assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithTag("theme_segment_light").assertDoesNotExist()
        composeTestRule.onNodeWithTag("theme_segment_dark").assertDoesNotExist()
        composeTestRule.onNodeWithTag("theme_segment_auto").assertDoesNotExist()

        val bounds = composeTestRule.onNodeWithTag("compact_theme_toggle").fetchSemanticsNode().boundsInRoot
        assertEquals(bounds.width, bounds.height, 1.0f)

        // Tapping cycles AUTO -> LIGHT -> DARK -> AUTO
        composeTestRule.onNodeWithTag("compact_theme_toggle").performClick()
        assertEquals("LIGHT", currentTheme)

        composeTestRule.onNodeWithTag("compact_theme_toggle").performClick()
        assertEquals("DARK", currentTheme)

        composeTestRule.onNodeWithTag("compact_theme_toggle").performClick()
        assertEquals("AUTO", currentTheme)
    }

    @Test
    fun HT2_settingsSubtitlesAndRestoreDoorReadPath_deriveFromLiveRootAndNoLiteralLegacyLabels() {
        val settingsRepo = com.example.data.datastore.AppSettingsRepository(context)
        val settingsVm = com.example.ui.settings.SettingsViewModel(repository, settingsRepo, database)

        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.settings.SettingsScreen(viewModel = settingsVm)
            }
        }
        composeTestRule.waitForIdle()

        val liveRoot = com.example.importbackup.MediaStoreWriter.ROOT_FOLDER
        val liveAutoSub = com.example.importbackup.MediaStoreWriter.AUTO_SUBFOLDER

        // Assert every path-bearing subtitle in Settings contains the live root constant
        composeTestRule.onNodeWithText("Saves single unified file to Downloads/$liveRoot/${com.example.importbackup.UnifiedCsvStorage.CSV_FILE_NAME}")
            .assertExists()
        composeTestRule.onNodeWithText("Scans Downloads/$liveRoot and internal auto backups for the newest snapshot")
            .assertExists()

        // Assert NO display node in Settings contains literal "ExpenseTracker" or "PaisaTrack"
        composeTestRule.onNodeWithText("ExpenseTracker", substring = true, ignoreCase = true)
            .assertDoesNotExist()
        composeTestRule.onNodeWithText("PaisaTrack", substring = true, ignoreCase = true)
            .assertDoesNotExist()

        // Assert the restore door's primary read path resolves to the live root (SilentSums/auto/backup.csv)
        assertEquals(
            "$liveRoot/$liveAutoSub/${com.example.importbackup.MediaStoreWriter.AUTO_BACKUP_FILE}",
            com.example.importbackup.UnifiedCsvStorage.PRIMARY_AUTO_SAVE_RELATIVE_PATH
        )
        assertEquals(liveRoot, com.example.importbackup.UnifiedCsvStorage.CSV_FOLDER_NAME)
    }

    @Test
    fun HT3a_dashboardHelpList_equalsExactStringsInOrder() {
        val dashboardTips = com.example.ui.dashboard.AUDITED_AFFORDANCES
            .filter { it.section == HelpSection.DASHBOARD }
            .map { it.result }
        val expensesTips = com.example.ui.dashboard.AUDITED_AFFORDANCES
            .filter { it.section == HelpSection.EXPENSES }
            .map { it.result }
        val listsTips = com.example.ui.dashboard.AUDITED_AFFORDANCES
            .filter { it.section == HelpSection.LISTS }
            .map { it.result }
        val commonTasksTips = com.example.ui.dashboard.COMMON_TASKS_AFFORDANCES
            .map { it.result }

        val expectedDashboard = listOf(
            "Balance Card: Tap or hold for Opening, +In, -Out, and Net breakdown.",
            "Summary, Source, Card, or Category: Tap to open matching rows in Ledger.",
            "Sources Order: Auto-sorted by highest bank balance and largest card debt.",
            "Credit Card Rows: TAP opens ledger; HOLD opens a pre-filled transfer of the amount due..",
            "Monthly Budgets: Tap to view rows; hold to edit limit inline.",
            "Trend Bars: Tap to zoom; hold to open the Ledger at that date.",
            "Chart Controls: Pick more than one period, pin a week label, or zoom out.",
            "Top Filters: Switch between All, Expense, Income, Transfer, months, years, or dates.",
            "Header Icons: Bell settles bills and IOUs; ☀️/🌙/A sets Light, Dark, or Auto."
        )
        val expectedExpenses = listOf(
            "Delete & Undo: Swipe left, tap red bin to delete, or tap UNDO.",
            "Split Cards: Tap card to expand lines inline; tap ✎ to edit.",
            "Entry & Split Math (+): Keypad solves math; enter Total first for splits.",
            "Media & Categories: Tap 📷 or 🎤 for media; tap category to re-tag.",
            "Edit Row: Tap any row to edit amount, account, date, notes, or media.",
            "Search & Scratchpad (🧮): Search text, tags, or amounts; tap 🧮 for calculator.",
            "Filtered Export: Top download icon saves only filtered rows to your data folder."
        )
        val expectedLists = listOf(
            "4 Tabs: Manage Categories, Sources, Fields, and Budgets from the top bar.",
            "Delete vs. Archive: Unused items delete; used items archive to protect history.",
            "Categories: Paste any emoji, pick from 24 colors, and reorder with arrows.",
            "Sources: Set Bank, Cash, or Card; editing Opening Balance recalculates all balances.",
            "Custom Fields: Add Text, Number, Dropdown, or Date fields per transaction type.",
            "Budgets: Set monthly limits for a Category, a Source/Card, or All Spend."
        )
        val expectedCommonTasks = listOf(
            "Cycle Anchor Day: Set your month start day (1–28) for budgets and cycles.",
            "Privacy & Lock: Mask amounts in public, or lock with PIN and biometrics.",
            "UI & Reminders: Toggle Dashboard budgets, ? help buttons, or bill due alerts.",
            "Auto & Manual Backup: Saves daily to your data folder and safety net.",
            "Restore & Import: Import a CSV file or Restore from CSV Backup anytime.",
            "Sample Data Mode: Runs in an isolated sandbox so real data stays untouched."
        )

        assertEquals(9, dashboardTips.size)
        assertEquals(expectedDashboard, dashboardTips)

        assertEquals(7, expensesTips.size)
        assertEquals(expectedExpenses, expensesTips)

        assertEquals(6, listsTips.size)
        assertEquals(expectedLists, listsTips)

        assertEquals(6, commonTasksTips.size)
        assertEquals(expectedCommonTasks, commonTasksTips)

        val all28 = dashboardTips + expensesTips + listsTips + commonTasksTips
        assertEquals(28, all28.size)
        assertEquals(expectedDashboard + expectedExpenses + expectedLists + expectedCommonTasks, all28)
    }

    @Test
    fun HT3b_dashboardHelpSheet_scrollsToLastEntryInCompactViewport() {
        composeTestRule.setContent {
            MyApplicationTheme {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.size(width = 360.dp, height = 420.dp)
                ) {
                    HelpGesturesSheet(section = HelpSection.DASHBOARD, onDismiss = {})
                }
            }
        }
        composeTestRule.waitForIdle()

        val lastIndex = com.example.ui.dashboard.AUDITED_AFFORDANCES
            .count { it.section == HelpSection.DASHBOARD } - 1
        val lastBody = com.example.ui.dashboard.AUDITED_AFFORDANCES
            .last { it.section == HelpSection.DASHBOARD }.body

        // Scroll to the last entry and assert it is displayed (not clipped)
        composeTestRule.onNodeWithTag("help_gesture_row_$lastIndex")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(lastBody)
            .assertIsDisplayed()

        // Pinned header remains displayed
        composeTestRule.onNodeWithTag("help_gestures_title").assertIsDisplayed()
    }

    @Test
    fun HT3c_dashboardHelpList_pinsNavigationTeachingStringsVerbatim() {
        val allItems = com.example.ui.dashboard.AUDITED_AFFORDANCES + com.example.ui.dashboard.COMMON_TASKS_AFFORDANCES
        val allTips = allItems.map { it.fullLine }

        val canonical28 = listOf(
            // Dashboard (9)
            "Balance Card: Tap or hold for Opening, +In, -Out, and Net breakdown.",
            "Summary, Source, Card, or Category: Tap to open matching rows in Ledger.",
            "Sources Order: Auto-sorted by highest bank balance and largest card debt.",
            "Pay Credit Card Due?: Credit Card Rows: TAP opens ledger; HOLD opens a pre-filled transfer of the amount due..",
            "Monthly Budgets: Tap to view rows; hold to edit limit inline.",
            "Trend Bars: Tap to zoom; hold to open the Ledger at that date.",
            "Chart Controls: Pick more than one period, pin a week label, or zoom out.",
            "Top Filters: Switch between All, Expense, Income, Transfer, months, years, or dates.",
            "Header Icons: Bell settles bills and IOUs; ☀️/🌙/A sets Light, Dark, or Auto.",
            // Expenses (7)
            "Delete & Undo: Swipe left, tap red bin to delete, or tap UNDO.",
            "Split Cards: Tap card to expand lines inline; tap ✎ to edit.",
            "Entry & Split Math (+): Keypad solves math; enter Total first for splits.",
            "Media & Categories: Tap 📷 or 🎤 for media; tap category to re-tag.",
            "Edit Row: Tap any row to edit amount, account, date, notes, or media.",
            "Search & Scratchpad (🧮): Search text, tags, or amounts; tap 🧮 for calculator.",
            "Filtered Export: Top download icon saves only filtered rows to your data folder.",
            // Lists (6)
            "4 Tabs: Manage Categories, Sources, Fields, and Budgets from the top bar.",
            "Delete vs. Archive: Unused items delete; used items archive to protect history.",
            "Categories: Paste any emoji, pick from 24 colors, and reorder with arrows.",
            "Sources: Set Bank, Cash, or Card; editing Opening Balance recalculates all balances.",
            "Custom Fields: Add Text, Number, Dropdown, or Date fields per transaction type.",
            "Budgets: Set monthly limits for a Category, a Source/Card, or All Spend.",
            // Common Tasks (6)
            "Cycle Anchor Day: Set your month start day (1–28) for budgets and cycles.",
            "Privacy & Lock: Mask amounts in public, or lock with PIN and biometrics.",
            "UI & Reminders: Toggle Dashboard budgets, ? help buttons, or bill due alerts.",
            "Auto & Manual Backup: Saves daily to your data folder and safety net.",
            "Restore & Import: Import a CSV file or Restore from CSV Backup anytime.",
            "Sample Data Mode: Runs in an isolated sandbox so real data stays untouched."
        )

        assertEquals(28, allTips.size)
        assertEquals(canonical28, allTips)

        // Forbidden hallucinated nouns must never appear
        val forbiddenEverywhere = listOf("duplicate", "billing day", "matching categories", "10 ", "3 voice", "×")
        for (item in allItems) {
            for (forbidden in forbiddenEverywhere) {
                assertFalse(
                    "Forbidden token '$forbidden' found in '${item.fullLine}'",
                    item.fullLine.contains(forbidden, ignoreCase = true)
                )
            }
            if (item.section == HelpSection.LISTS) {
                assertFalse(
                    "Forbidden 'Dues' in LISTS found in '${item.fullLine}'",
                    item.fullLine.contains("Dues", ignoreCase = true)
                )
            }
        }
    }

    @Test
    fun C3_orphanExceptionMessage_namesLegacyFolderNotLiveRoot() = runBlocking {
        // Ensure no stored CSV or auto-save exists
        val pubDownloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        pubDownloads.deleteRecursively()
        java.io.File(context.filesDir, "backups").deleteRecursively()

        var thrownMsg = ""
        try {
            com.example.importbackup.UnifiedCsvStorage.restoreFromAutoSave(
                context = context,
                repository = repository,
                clearExisting = false,
                settingsRepository = settingsRepository
            )
            fail("Expected exception when no backup exists")
        } catch (e: Exception) {
            thrownMsg = e.message ?: ""
        }

        assertTrue("Orphan message must reference legacy folder (Downloads/ExpenseTracker or similar)", thrownMsg.contains("Downloads/ExpenseTracker or similar"))
        assertFalse("Orphan message must NOT interpolate live root SilentSums", thrownMsg.contains("Downloads/SilentSums"))
    }

    @Test
    fun C4_readAutoSave_resolvesLiveRootFirstBeforeLegacyAndSafetyNet() {
        val pubDownloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        pubDownloads.deleteRecursively()
        val safetyNetDir = java.io.File(context.filesDir, "backups")
        safetyNetDir.deleteRecursively()

        val liveCsv = "#FORMAT=2\n#TRANSACTIONS\nDate,Type,Amount,FromAccount,ToAccount,Category,Description,Notes\n2026-10-01 10:00:00,EXPENSE,111.00,Bank Account,,Food,LIVE_ROOT_MARKER,\n"
        val legacyCsv = "#FORMAT=2\n#TRANSACTIONS\nDate,Type,Amount,FromAccount,ToAccount,Category,Description,Notes\n2026-10-01 10:00:00,EXPENSE,222.00,Bank Account,,Food,LEGACY_ROOT_MARKER,\n"
        val safetyCsv = "#FORMAT=2\n#TRANSACTIONS\nDate,Type,Amount,FromAccount,ToAccount,Category,Description,Notes\n2026-10-01 10:00:00,EXPENSE,333.00,Bank Account,,Food,SAFETY_NET_MARKER,\n"

        val liveFile = java.io.File(pubDownloads, "SilentSums/auto/backup.csv").apply {
            parentFile?.mkdirs()
            writeText(liveCsv)
        }
        val legacyFile = java.io.File(pubDownloads, "ExpenseTracker/auto/backup.csv").apply {
            parentFile?.mkdirs()
            writeText(legacyCsv)
        }
        val safetyFile = java.io.File(safetyNetDir, "auto_backup.csv").apply {
            parentFile?.mkdirs()
            writeText(safetyCsv)
        }

        // 1. All three exist -> LIVE root ("SilentSums/auto/backup.csv") wins first
        val read1 = com.example.importbackup.MediaStoreWriter.readAutoSave(context)!!.use {
            String(it.readBytes(), Charsets.UTF_8)
        }
        assertTrue("Primary read must resolve LIVE root first", read1.contains("LIVE_ROOT_MARKER"))

        // 2. Remove LIVE root -> Legacy root ("ExpenseTracker/auto/backup.csv") wins next
        liveFile.delete()
        val read2 = com.example.importbackup.MediaStoreWriter.readAutoSave(context)!!.use {
            String(it.readBytes(), Charsets.UTF_8)
        }
        assertTrue("Second priority must resolve legacy root fallback", read2.contains("LEGACY_ROOT_MARKER"))

        // 3. Remove Legacy root -> Internal safety net ("filesDir/backups/auto_backup.csv") wins last
        legacyFile.delete()
        val read3 = com.example.importbackup.MediaStoreWriter.readAutoSave(context)!!.use {
            String(it.readBytes(), Charsets.UTF_8)
        }
        assertTrue("Third priority must resolve internal safety net fallback", read3.contains("SAFETY_NET_MARKER"))

        safetyFile.delete()
    }
}
