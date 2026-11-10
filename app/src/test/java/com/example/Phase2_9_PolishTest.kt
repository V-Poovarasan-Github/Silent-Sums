package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.datastore.AppSettingsRepository
import com.example.data.model.Account
import com.example.data.model.Commitment
import com.example.data.repository.FinanceRepository
import com.example.data.seed.SampleDataSeeder
import com.example.ui.commitment.AddEditCommitmentSheet
import com.example.ui.commitment.CommitmentDraft
import com.example.ui.commitment.CommitmentViewModel
import com.example.ui.dashboard.AUDITED_AFFORDANCES
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.dashboard.HelpGesturesSheet
import com.example.ui.dashboard.HelpSection
import com.example.ui.ledger.LedgerScreen
import com.example.ui.ledger.LedgerViewModel
import com.example.ui.lists.ListsScreen
import com.example.ui.lists.ListsViewModel
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
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
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class Phase2_9_PolishTest {

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
        runBlocking { settingsRepository.setCurrency("₹") }
        CommitmentViewModel.clearSessionDraft()
    }

    @After
    fun tearDown() {
        CommitmentViewModel.clearSessionDraft()
        ShadowLooper.idleMainLooper()
    }

    // =========================================================================
    // ITEM 1: Demo parity — ONE unified rich dataset in replace-mode
    // =========================================================================
    @Test
    fun testDemoParity_bothSeedersUseRichDataset() {
        runBlocking {
            // Seed via seedDemoTransactions (the welcome card path)
            SampleDataSeeder.seedDemoTransactions(database)
            val accounts = repository.allAccounts.first()
            val commitments = repository.activeCommitments.first()
            val txs = repository.allTransactions.first()

            // Verify rich dataset properties: credit-card account, commitments, transactions
            val ccAccount = accounts.find { it.accountType == "CREDIT_CARD" }
            assertNotNull("Rich dataset must include a CREDIT_CARD account", ccAccount)
            assertTrue("Rich dataset must include commitments/dues", commitments.isNotEmpty())
            assertTrue("Rich dataset must include sample transactions", txs.size >= 10)
            assertTrue(txs.any { it.description.contains("Salary") })

            // Now run clean defaults to reset
            SampleDataSeeder.seedCleanDefaults(database)
            assertEquals(0, repository.allTransactions.first().size)

            // Seed via seedSampleData (Settings path)
            SampleDataSeeder.seedSampleData(database)
            val accounts2 = repository.allAccounts.first()
            val commitments2 = repository.activeCommitments.first()
            val txs2 = repository.allTransactions.first()

            assertEquals("Both entry points must seed the exact same number of accounts", accounts.size, accounts2.size)
            assertEquals("Both entry points must seed the exact same number of commitments", commitments.size, commitments2.size)
            assertEquals("Both entry points must seed the exact same number of transactions", txs.size, txs2.size)
        }
    }

    // =========================================================================
    // ITEM 2 & AMENDMENT A: Visible Demo Firewall
    // =========================================================================
    @Test
    fun testVisibleDemoFirewall_controlsGreyingAndCaptions() {
        runBlocking {
            settingsRepository.setDemoMode(true)
            val settingsVm = SettingsViewModel(repository, settingsRepository, database)

            composeTestRule.setContent {
                MyApplicationTheme {
                    SettingsScreen(viewModel = settingsVm)
                }
            }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            // Verify firewall banner exists
            composeTestRule.onNodeWithTag("demo_firewall_banner", useUnmergedTree = true).assertExists()

            // Verify captions show "Unavailable in sample mode"
            val unavailableNodes = composeTestRule.onAllNodesWithText("Unavailable in sample mode", useUnmergedTree = true)
            assertTrue("Multiple controls must render 'Unavailable in sample mode' caption", unavailableNodes.fetchSemanticsNodes().size >= 4)
        }
    }

    // =========================================================================
    // ITEM 3 & AMENDMENT B & F1: Dropped "Tap for in & out" microcopy
    // =========================================================================
    @Test
    fun testMicrocopy_currentBalanceDropsTapCaption() {
        val dashboardVm = DashboardViewModel(repository, settingsRepository)
        composeTestRule.setContent {
            MyApplicationTheme {
                DashboardScreen(viewModel = dashboardVm, onNavigateToExpenses = { _, _ -> })
            }
        }
        composeTestRule.waitForIdle()

        // "Tap for in & out" must no longer exist
        composeTestRule.onNodeWithText("Tap for in & out").assertDoesNotExist()
    }

    // =========================================================================
    // ITEM 4 & AMENDMENT F3: Distinct Restore Button Labels
    // =========================================================================
    @Test
    fun testRestoreButtons_distinctLabelsRendered() {
        runBlocking {
            settingsRepository.setDemoMode(false)
            val settingsVm = SettingsViewModel(repository, settingsRepository, database)

            composeTestRule.setContent {
                MyApplicationTheme {
                    SettingsScreen(viewModel = settingsVm)
                }
            }
            composeTestRule.waitForIdle()

            // 1. Only two CSV restore/import doors exist (LT10)
            composeTestRule.onNodeWithText("Import a CSV file").assertExists()
            composeTestRule.onNodeWithText("Restore from CSV Backup").assertExists()
            composeTestRule.onNodeWithText("Restore from Internal Safety Net").assertDoesNotExist()
        }
    }

    // =========================================================================
    // ITEM 6 & AMENDMENTS C & F2: Exact 3-case validation strings
    // =========================================================================
    @Test
    fun testCommitmentValidation_threeExactCases_recurringTab() {
        val accounts = listOf(Account(id = 1, name = "Bank", accountType = "BANK"))
        composeTestRule.setContent {
            MyApplicationTheme {
                AddEditCommitmentSheet(
                    commitment = null,
                    accounts = accounts,
                    categories = emptyList(),
                    onDismiss = {},
                    onSave = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        val saveBtn = composeTestRule.onNodeWithTag("save_commitment_btn")

        // Case 3: Both missing -> "Enter a bill name and amount"
        saveBtn.performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Enter a bill name and amount").assertIsDisplayed()

        // Case 1: Name missing, amount entered -> "Enter a bill name"
        composeTestRule.onNodeWithTag("commitment_amount_input").performTextInput("500")
        saveBtn.performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Enter a bill name").assertIsDisplayed()
    }

    @Test
    fun testCommitmentValidation_namePresentAmountMissing_recurringTab() {
        val accounts = listOf(Account(id = 1, name = "Bank", accountType = "BANK"))
        composeTestRule.setContent {
            MyApplicationTheme {
                AddEditCommitmentSheet(
                    commitment = null,
                    accounts = accounts,
                    categories = emptyList(),
                    onDismiss = {},
                    onSave = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Case 2: Name present, amount missing -> "Enter an amount"
        composeTestRule.onNodeWithTag("commitment_name_input").performTextInput("WiFi Bill")
        composeTestRule.onNodeWithTag("save_commitment_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Enter an amount").assertIsDisplayed()
    }

    @Test
    fun testCommitmentValidation_threeExactCases_iouTab() {
        val accounts = listOf(Account(id = 1, name = "Bank", accountType = "BANK"))
        composeTestRule.setContent {
            MyApplicationTheme {
                AddEditCommitmentSheet(
                    commitment = null,
                    accounts = accounts,
                    categories = emptyList(),
                    onDismiss = {},
                    onSave = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Switch to IOU tab
        composeTestRule.onNodeWithTag("type_iou_btn").performClick()
        composeTestRule.waitForIdle()

        val saveBtn = composeTestRule.onNodeWithTag("save_commitment_btn")

        // Both missing -> "Enter a bill name and amount"
        saveBtn.performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Enter a bill name and amount").assertIsDisplayed()

        // Enter name only -> "Enter an amount"
        composeTestRule.onNodeWithTag("commitment_name_input").performTextInput("Friend")
        saveBtn.performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Enter an amount").assertIsDisplayed()
    }

    // =========================================================================
    // ITEM 7 & AMENDMENT D: Draft State in CommitmentViewModel
    // =========================================================================
    @Test
    fun testCommitmentDraft_persistsOnDismissAndClearsOnSave() {
        val accounts = listOf(Account(id = 1, name = "Bank", accountType = "BANK"))
        var savedCommitment: Commitment? = null

        // Render sheet and type data
        composeTestRule.setContent {
            MyApplicationTheme {
                AddEditCommitmentSheet(
                    commitment = null,
                    accounts = accounts,
                    categories = emptyList(),
                    onDismiss = {},
                    onSave = { savedCommitment = it }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("commitment_name_input").performTextInput("Electricity")
        composeTestRule.onNodeWithTag("commitment_amount_input").performTextInput("1200")
        composeTestRule.waitForIdle()

        // Verify draft state in ViewModel is updated
        assertEquals("Electricity", CommitmentViewModel.sessionDraft.name)
        assertEquals("1200", CommitmentViewModel.sessionDraft.amountText)

        // Switching to IOU tab retains name & amount
        composeTestRule.onNodeWithTag("type_iou_btn").performClick()
        composeTestRule.waitForIdle()
        assertEquals("IOU", CommitmentViewModel.sessionDraft.type)
        assertEquals("Electricity", CommitmentViewModel.sessionDraft.name)
        assertEquals("1200", CommitmentViewModel.sessionDraft.amountText)

        // Switching back to RECURRING tab retains name & amount
        composeTestRule.onNodeWithTag("type_recurring_btn").performClick()
        composeTestRule.waitForIdle()
        assertEquals("RECURRING", CommitmentViewModel.sessionDraft.type)
        assertEquals("Electricity", CommitmentViewModel.sessionDraft.name)
        assertEquals("1200", CommitmentViewModel.sessionDraft.amountText)

        // Now save successfully
        composeTestRule.onNodeWithTag("save_commitment_btn").performClick()
        composeTestRule.waitForIdle()
        assertNotNull(savedCommitment)
        assertEquals("Electricity", savedCommitment?.name)

        // Draft state must now be CLEARED
        assertEquals("", CommitmentViewModel.sessionDraft.name)
        assertEquals("", CommitmentViewModel.sessionDraft.amountText)
    }

    // =========================================================================
    // ITEM 8 & AMENDMENT E: Context-Aware Section Scoping & Swipe-Delete Line
    // =========================================================================
    @Test
    fun testContextAwareHelp_expensesSectionContainsSwipeDeleteLine() {
        composeTestRule.setContent {
            MyApplicationTheme {
                HelpGesturesSheet(
                    section = HelpSection.EXPENSES,
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Check required swipe delete line
        val exactSwipeDeleteBody = "Swipe left, tap red bin to delete, or tap UNDO."
        composeTestRule.onNodeWithText(exactSwipeDeleteBody).assertIsDisplayed()

        // Check other expenses items
        composeTestRule.onNodeWithText("Search text, tags, or amounts; tap 🧮 for calculator.").assertExists()
        composeTestRule.onNodeWithText("Tap any row to edit amount, account, date, notes, or media.").assertExists()

        // Dashboard items must NOT be displayed in Expenses section
        composeTestRule.onNodeWithText("Tap or hold for Opening, +In, -Out, and Net breakdown.").assertDoesNotExist()
    }

    @Test
    fun testContextAwareHelp_dashboardSectionContainsOnlyDashboardItems() {
        composeTestRule.setContent {
            MyApplicationTheme {
                HelpGesturesSheet(
                    section = HelpSection.DASHBOARD,
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Tap or hold for Opening, +In, -Out, and Net breakdown.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tap to zoom; hold to open the Ledger at that date.").assertExists()

        // Expenses and Lists must NOT be in Dashboard section
        composeTestRule.onNodeWithText("Swipe left, tap red bin to delete, or tap UNDO.").assertDoesNotExist()
        composeTestRule.onNodeWithText("Manage Categories, Sources, Fields, and Budgets from the top bar.").assertDoesNotExist()
    }

    @Test
    fun testContextAwareHelp_listsSectionContainsOnlyListsItems() {
        composeTestRule.setContent {
            MyApplicationTheme {
                HelpGesturesSheet(
                    section = HelpSection.LISTS,
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Manage Categories, Sources, Fields, and Budgets from the top bar.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Unused items delete; used items archive to protect history.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Set monthly limits for a Category, a Source/Card, or All Spend.").assertExists()

        // Dashboard items must NOT be in Lists section
        composeTestRule.onNodeWithText("Tap or hold for Opening, +In, -Out, and Net breakdown.").assertDoesNotExist()
    }

    @Test
    fun testContextAwareHelp_combinedSectionContainsCommonTasks() {
        composeTestRule.setContent {
            MyApplicationTheme {
                HelpGesturesSheet(
                    section = HelpSection.ALL,
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Verify all sections and Common Tasks are present
        composeTestRule.onNodeWithText("Tap or hold for Opening, +In, -Out, and Net breakdown.").assertExists()
        composeTestRule.onNodeWithText("Swipe left, tap red bin to delete, or tap UNDO.").assertExists()
        composeTestRule.onNodeWithText("Manage Categories, Sources, Fields, and Budgets from the top bar.").assertExists()
        composeTestRule.onNodeWithText("Set your month start day (1–28) for budgets and cycles.").assertExists()
        composeTestRule.onNodeWithText("Import a CSV file or Restore from CSV Backup anytime.").assertExists()
        composeTestRule.onNodeWithText("Mask amounts in public, or lock with PIN and biometrics.").assertExists()
        composeTestRule.onNodeWithText("Runs in an isolated sandbox so real data stays untouched.").assertExists()
    }
}
