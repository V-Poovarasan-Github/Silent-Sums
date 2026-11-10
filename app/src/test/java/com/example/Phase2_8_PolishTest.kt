package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.datastore.AppSettingsRepository
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.Commitment
import com.example.data.repository.FinanceRepository
import com.example.data.seed.SampleDataSeeder
import com.example.importbackup.CsvBackupManager
import com.example.importbackup.UnifiedCsvStorage
import com.example.ui.commitment.CommitmentViewModel
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class Phase2_8_PolishTest {

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
    }

    @After
    fun tearDown() {
        composeTestRule.waitForIdle()
        ShadowLooper.idleMainLooper()
        database.close()
    }

    // =========================================================================
    // ITEM 1: Help-visibility toggle
    // =========================================================================

    @Test
    fun testHelpVisibilityToggle_dashboardGating() {
        runBlocking {
            settingsRepository.setHelpVisible(true)
            val dashboardVm = DashboardViewModel(repository, settingsRepository)

            composeTestRule.setContent {
                MyApplicationTheme {
                    DashboardScreen(viewModel = dashboardVm, onNavigateToExpenses = { _, _ -> })
                }
            }
            composeTestRule.waitForIdle()
            // Dashboard ? icon button is displayed when true
            composeTestRule.onNodeWithTag("help_gestures_btn").assertIsDisplayed()

            // When false -> disappears
            settingsRepository.setHelpVisible(false)
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("help_gestures_btn").assertDoesNotExist()
        }
    }

    @Test
    fun testHelpVisibilityToggle_settingsGating() {
        runBlocking {
            settingsRepository.setHelpVisible(true)
            val settingsVm = SettingsViewModel(repository, settingsRepository, database)

            composeTestRule.setContent {
                MyApplicationTheme {
                    SettingsScreen(viewModel = settingsVm)
                }
            }
            composeTestRule.waitForIdle()
            // Settings help row is displayed
            composeTestRule.onNodeWithTag("help_gestures_row").assertIsDisplayed()
            composeTestRule.onNodeWithTag("help_visible_switch").assertIsDisplayed()

            // When false -> row disappears
            settingsRepository.setHelpVisible(false)
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("help_gestures_row").assertDoesNotExist()
        }
    }

    // =========================================================================
    // ITEM 3: Commitment description (CONTRACT EDIT)
    // =========================================================================

    @Test
    fun testCommitmentDescription_roundTrip_andSettleToLedger() {
        runBlocking {
            // Create an account & category
            val accId = repository.insertAccount(Account(name = "Salary Bank", accountType = "BANK", openingBalance = 10000.0))
            val catId = repository.insertCategory(Category(name = "Utilities", color = "#8B5CF6"))

            // Insert commitment with description
            val commitment = Commitment(
                type = "RECURRING",
                name = "Broadband",
                amount = 999.0,
                direction = "OUTGOING",
                dayOfMonth = 15,
                accountId = accId,
                categoryId = catId,
                description = "High-speed 300Mbps fiber"
            )
            val commitmentId = repository.insertCommitment(commitment)

            // Verify DB survival
            val loaded = repository.getCommitmentById(commitmentId)
            assertNotNull(loaded)
            assertEquals("High-speed 300Mbps fiber", loaded?.description)

            // Settle recurring -> verify transaction carries the description into ledger
            val commitmentVm = CommitmentViewModel(repository)
            commitmentVm.recordAndMarkPaid(loaded!!).join()

            val transactions = repository.allTransactions.first()
            assertEquals(1, transactions.size)
            assertEquals("High-speed 300Mbps fiber", transactions[0].description)
            assertEquals(999.0, transactions[0].amount, 0.001)

            // Test IOU settle copies description
            val iou = Commitment(
                type = "IOU",
                name = "Rohan",
                amount = 1200.0,
                direction = "OUTGOING",
                accountId = accId,
                categoryId = catId,
                description = "Dinner bill split"
            )
            val iouId = repository.insertCommitment(iou)
            val loadedIou = repository.getCommitmentById(iouId)
            assertNotNull(loadedIou)

            commitmentVm.settleIou(loadedIou!!, recordTransaction = true, chosenAccountId = accId).join()
            val allTx = repository.allTransactions.first()
            assertEquals(2, allTx.size)
            val iouTx = allTx.first { it.amount == 1200.0 }
            assertEquals("Dinner bill split", iouTx.description)
            commitmentVm.viewModelScope.coroutineContext[Job]?.cancelChildren()
        }
    }

    @Test
    fun testCommitmentCsv_10thColumn_writeAndLegacyTolerance() {
        val commitment = Commitment(
            id = 1L,
            type = "RECURRING",
            name = "Gym Membership",
            amount = 1500.0,
            direction = "OUTGOING",
            dayOfMonth = 5,
            description = "Annual pass installment"
        )

        // 1. Writer emits 10 columns
        val csv = CsvBackupManager.buildCsv(
            transactions = emptyList(),
            accounts = emptyList(),
            categories = emptyList(),
            commitments = listOf(commitment)
        )
        assertTrue(csv.contains("type,name,amount,direction,dayOfMonth,dueDate,accountName,categoryName,active,description"))
        assertTrue(csv.contains("RECURRING,Gym Membership,1500.00,OUTGOING,5,,,,true,Annual pass installment"))

        // 2. Legacy 9-column CSV tolerance: no index crash, description defaults to empty
        val legacy9ColCsv = """
            #FORMAT=1
            Date,Type,Amount,FromAccount,ToAccount,Category,Description,Notes
            2026-09-27 13:44,EXPENSE,100.00,Cash,,Food,Snack,

            #COMMITMENTS (Dues & Recurring)
            type,name,amount,direction,dayOfMonth,dueDate,accountName,categoryName,active
            RECURRING,Old Wifi,799.00,OUTGOING,10,,,true
        """.trimIndent()

        val parsed = CsvBackupManager.parseCsvFull(ByteArrayInputStream(legacy9ColCsv.toByteArray(Charsets.UTF_8)))
        assertEquals(1, parsed.dues.size)
        val parsedDue = parsed.dues[0]
        assertEquals("Old Wifi", parsedDue.name)
        assertEquals(799.00, parsedDue.amount, 0.001)
        assertEquals("", parsedDue.description) // Tolerates missing trailing column without crash
    }

    // =========================================================================
    // ITEM 4: Sample-data offer + banner + firewall (DEMO FIREWALL)
    // =========================================================================

    @Test
    fun testDemoModeGate_welcomeCard_andBannerHandoff() {
        runBlocking {
            val dashboardVm = DashboardViewModel(repository, settingsRepository)

            // At 0 transactions, not demo mode: welcome card with demo offer button is visible
            composeTestRule.setContent {
                MyApplicationTheme {
                    DashboardScreen(viewModel = dashboardVm, onNavigateToExpenses = { _, _ -> })
                }
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("welcome_card").assertIsDisplayed()
            composeTestRule.onNodeWithTag("welcome_card_demo").assertIsDisplayed()
            composeTestRule.onNodeWithTag("demo_banner").assertDoesNotExist()

            // Seed demo transactions and set demoMode = true
            SampleDataSeeder.seedDemoTransactions(database)
            settingsRepository.setDemoMode(true)

            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            // Welcome card auto-hides; demo banner appears
            composeTestRule.onNodeWithTag("welcome_card").assertDoesNotExist()
            composeTestRule.onNodeWithTag("demo_banner").assertIsDisplayed()
            composeTestRule.onNodeWithTag("demo_cancel").assertIsDisplayed()

            // Tapping cancel re-runs clean defaults reset and clears demo mode
            SampleDataSeeder.clearDemoTransactions(database)
            settingsRepository.setDemoMode(false)

            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            // Demo banner is gone, transactions are 0, welcome card reappears
            composeTestRule.onNodeWithTag("demo_banner").assertDoesNotExist()
            composeTestRule.onNodeWithTag("welcome_card").assertIsDisplayed()
            composeTestRule.onNodeWithTag("welcome_card_demo").assertIsDisplayed()
        }
    }

    @Test
    fun testDemoFirewall_suppressesBackupsAndRestore() {
        runBlocking {
            // Set demoMode = true
            settingsRepository.setDemoMode(true)
            val settings = settingsRepository.settingsFlow.first()
            assertTrue(settings.demoMode)

            // 1. Manual export to device is paused
            val exportResult = UnifiedCsvStorage.saveToDeviceCsv(
                context = context,
                transactions = emptyList(),
                accounts = emptyList(),
                categories = emptyList(),
                settings = settings,
                settingsRepository = settingsRepository
            )
            assertEquals("Export paused: Demo mode active", exportResult)

            // 2. Filtered ledger export is blocked
            val exportUri = UnifiedCsvStorage.exportFilteredLedgerCsv(
                context = context,
                transactions = emptyList(),
                accounts = emptyList(),
                categories = emptyList(),
                settingsRepository = settingsRepository
            )
            assertNull(exportUri)

            // 3. Restore from input stream is blocked by firewall
            val dummyCsv = "Date,Type,Amount\n2026-09-01,EXPENSE,100\n"
            var blockedByFirewall = false
            try {
                UnifiedCsvStorage.restoreFromInputStream(
                    repository = repository,
                    inputStream = ByteArrayInputStream(dummyCsv.toByteArray(Charsets.UTF_8)),
                    settingsRepository = settingsRepository
                )
            } catch (e: IllegalStateException) {
                blockedByFirewall = e.message?.contains("Restore blocked: Demo mode active") == true
            }
            assertTrue("Restore must be blocked when demo mode is active", blockedByFirewall)
        }
    }
}
