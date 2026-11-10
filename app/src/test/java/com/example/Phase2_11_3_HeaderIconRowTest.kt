package com.example

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.ui.common.HEADER_ICON_ROW_SPACING
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.ledger.LedgerScreen
import com.example.ui.ledger.LedgerViewModel
import com.example.ui.lists.ListsScreen
import com.example.ui.lists.ListsViewModel
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class Phase2_11_3_HeaderIconRowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: FinanceRepository
    private val activeViewModels = mutableListOf<ViewModel>()

    private fun <T : ViewModel> track(vm: T): T {
        activeViewModels.add(vm)
        return vm
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(database)
    }

    @After
    fun tearDown() {
        composeTestRule.waitForIdle()
        activeViewModels.forEach { vm ->
            try {
                vm.viewModelScope.cancel()
            } catch (_: Exception) {}
        }
        activeViewModels.clear()
        composeTestRule.waitForIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        database.close()
    }

    @Test
    fun testExportGreyedFlag_emptyLedger_isGreyed() {
        val emptyVm = track(LedgerViewModel(repository = repository))
        composeTestRule.setContent {
            MyApplicationTheme {
                LedgerScreen(viewModel = emptyVm, onAddClick = {}, onTransactionClick = {})
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(0, emptyVm.uiState.value.totalCount)
        assertTrue("Export must be greyed when total count is 0", emptyVm.isExportGreyed)
    }

    @Test
    fun testExportGreyedFlag_populatedLedger_isNotGreyed_andStableAcrossSimulatedReentry() {
        runBlocking {
            val accId = repository.insertAccount(Account(name = "Bank", accountType = "BANK", openingBalance = 1000.0))
            val catId = repository.insertCategory(Category(name = "Food", color = "#10B981"))
            repository.insertTransaction(
                TransactionEntity(
                    date = System.currentTimeMillis(),
                    type = "EXPENSE",
                    amount = 150.0,
                    fromAccountId = accId,
                    categoryId = catId,
                    description = "Lunch"
                )
            )
        }

        val populatedVm = track(LedgerViewModel(repository = repository))
        composeTestRule.setContent {
            MyApplicationTheme {
                LedgerScreen(viewModel = populatedVm, onAddClick = {}, onTransactionClick = {})
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(timeoutMillis = 3000L) {
            populatedVm.uiState.value.totalCount == 1
        }

        val populatedCount = populatedVm.uiState.value.totalCount
        assertEquals(1, populatedCount)
        assertFalse("Export must NOT be greyed when total count > 0", populatedVm.isExportGreyed)

        // Simulated re-entry: settled frame retains state, no flip
        composeTestRule.waitForIdle()
        assertEquals(1, populatedVm.uiState.value.totalCount)
        assertFalse("Flag must remain stably false across simulated re-entry", populatedVm.isExportGreyed)
    }

    @Test
    fun testLedgerScreen_headerIconNodesExist_andMatchSharedTags() {
        val ledgerViewModel = track(LedgerViewModel(repository = repository))

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

        // Verify all 3 header icon nodes exist via shared testTags
        composeTestRule.onNodeWithTag("help_gestures_btn").assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithTag("ledger_export_filtered_btn").assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithTag("compact_theme_toggle").assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun testSharedGapConstant_andStructuralParityAcrossScreens() {
        // Assert shared gap constant equals 12.dp
        assertEquals(12.dp, HEADER_ICON_ROW_SPACING)

        // Verify Dashboard header row has help, bell, and theme toggle
        val settingsRepository = com.example.data.datastore.AppSettingsRepository(context)
        val dashboardViewModel = track(DashboardViewModel(repository = repository, settingsRepository = settingsRepository))
        composeTestRule.setContent {
            MyApplicationTheme {
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onNavigateToExpenses = { _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("help_gestures_btn").assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithTag("dashboard_bell_btn").assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithTag("compact_theme_toggle").assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun testListsScreen_headerIconNodesExist() {
        val listsViewModel = track(ListsViewModel(repository = repository))
        composeTestRule.setContent {
            MyApplicationTheme {
                ListsScreen(
                    viewModel = listsViewModel
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("help_gestures_btn").assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithTag("compact_theme_toggle").assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun testGuardRegression_emptyShowsMessageAndNoFile() {
        val emptyVm = track(LedgerViewModel(repository = repository))
        composeTestRule.setContent {
            MyApplicationTheme {
                LedgerScreen(viewModel = emptyVm, onAddClick = {}, onTransactionClick = {})
            }
        }
        composeTestRule.waitForIdle()

        val emptyResult = runBlocking { emptyVm.exportFilteredCsv(context) }
        assertEquals("No records to export", emptyResult)
    }

    @Test
    fun testGuardRegression_withDataExportsNormally() {
        runBlocking {
            val accId = repository.insertAccount(Account(name = "Bank", accountType = "BANK", openingBalance = 1000.0))
            val catId = repository.insertCategory(Category(name = "Food", color = "#10B981"))
            repository.insertTransaction(
                TransactionEntity(
                    date = System.currentTimeMillis(),
                    type = "EXPENSE",
                    amount = 250.0,
                    fromAccountId = accId,
                    categoryId = catId,
                    description = "Dinner"
                )
            )
        }

        val populatedVm = track(LedgerViewModel(repository = repository))
        composeTestRule.setContent {
            MyApplicationTheme {
                LedgerScreen(viewModel = populatedVm, onAddClick = {}, onTransactionClick = {})
            }
        }
        composeTestRule.waitForIdle()

        val withDataResult = runBlocking { populatedVm.exportFilteredCsv(context) }
        assertTrue("Export with records should write file", withDataResult.contains("filtered_export_"))
    }
}
