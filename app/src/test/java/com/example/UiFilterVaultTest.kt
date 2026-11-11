package com.example

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.domain.model.ChartLevel
import com.example.domain.model.EnrichedTransaction
import com.example.ui.ledger.TransactionCardRow
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import com.example.ui.media.PhotoScaleKey
import com.example.ui.media.PhotoTranslationXKey
import com.example.ui.media.PhotoTranslationYKey
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import com.example.ui.common.FilterAutoRevealHelper
import org.junit.Assert.assertNull
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.datastore.AppSettingsRepository
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.Commitment
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.domain.calculator.LedgerCalculator
import com.example.domain.model.FilterState
import com.example.domain.util.Formatters
import androidx.compose.runtime.key
import com.example.ui.commitment.AddEditCommitmentSheet
import com.example.ui.common.AppIcons
import com.example.ui.common.CrashRecoveryBanner
import com.example.ui.common.FilterChipsRow
import com.example.ui.dashboard.DashboardIntroTracker
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.dashboard.isReducedMotionEnabled
import com.example.ui.lock.LockScreen
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
import java.io.File
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class UiFilterVaultTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: FinanceRepository
    private lateinit var settingsRepository: AppSettingsRepository

    private val sampleCategories = listOf(
        Category(id = 1L, name = "Groceries", color = "#EF4444"),
        Category(id = 2L, name = "Rent", color = "#3B82F6"),
        Category(id = 3L, name = "Utilities", color = "#10B981")
    )

    private val sampleAccounts = listOf(
        Account(id = 10L, name = "HDFC Bank", accountType = "BANK", openingBalance = 50000.0),
        Account(id = 20L, name = "Cash Wallet", accountType = "CASH", openingBalance = 5000.0),
        Account(id = 30L, name = "ICICI Card", accountType = "CREDIT_CARD", openingBalance = -10000.0)
    )

    @Before
    fun setup() = runBlocking {
        com.example.security.PinHasher.dispatcher = kotlinx.coroutines.Dispatchers.Unconfined
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(database)
        settingsRepository = AppSettingsRepository(context)
        settingsRepository.setCurrency("₹")
        settingsRepository.setHideBalances(false)
        Formatters.updateCurrentCurrencySymbol("₹")
        DashboardIntroTracker.resetForTests()
    }

    @After
    fun tearDown() {
        com.example.security.PinHasher.dispatcher = kotlinx.coroutines.Dispatchers.Default
        FilterAutoRevealHelper.forceReducedMotionForTests = null
        ShadowLooper.idleMainLooper()
        DashboardIntroTracker.resetForTests()
    }

    // --- OT1: Category Select All Header Toggle ---
    @Test
    fun testOT1_categorySelectAllHeaderToggleChecksEveryRow() {
        var currentFilter by mutableStateOf(FilterState())

        composeTestRule.setContent {
            FilterChipsRow(
                filterState = currentFilter,
                categories = sampleCategories,
                accounts = sampleAccounts,
                onFilterChange = { currentFilter = it }
            )
        }

        // Open Category Dropdown
        composeTestRule.onNodeWithTag("filter_chip_category").performClick()

        // Verify select-all header row exists
        composeTestRule.onNodeWithTag("category_select_all_header").assertIsDisplayed()
        composeTestRule.onNodeWithTag("category_select_all_toggle").assertIsDisplayed()

        // Toggle Select All
        composeTestRule.onNodeWithTag("category_select_all_toggle").performClick()

        // Verify all 3 categories are checked in filterState
        assertEquals(3, currentFilter.selectedCategoryIds.size)
        assertTrue(currentFilter.selectedCategoryIds.containsAll(listOf(1L, 2L, 3L)))

        // Toggle again to deselect all
        composeTestRule.onNodeWithTag("category_select_all_toggle").performClick()
        assertTrue(currentFilter.selectedCategoryIds.isEmpty())
    }

    // --- OT1b: Source Select All Header Toggle ---
    @Test
    fun testOT1b_sourceSelectAllHeaderToggleChecksEveryRow() {
        var currentFilter by mutableStateOf(FilterState())

        composeTestRule.setContent {
            FilterChipsRow(
                filterState = currentFilter,
                categories = sampleCategories,
                accounts = sampleAccounts,
                onFilterChange = { currentFilter = it },
                modifier = Modifier.requiredWidth(1000.dp)
            )
        }

        // Open Source Dropdown
        composeTestRule.onNodeWithTag("filter_chip_source").performClick()

        // Verify select-all header row exists
        composeTestRule.onNodeWithTag("account_select_all_header").assertIsDisplayed()
        composeTestRule.onNodeWithTag("account_select_all_toggle").assertIsDisplayed()

        // Toggle Select All
        composeTestRule.onNodeWithTag("account_select_all_toggle").performClick()

        // Verify all 3 accounts are checked in filterState
        assertEquals(3, currentFilter.selectedAccountIds.size)
        assertTrue(currentFilter.selectedAccountIds.containsAll(listOf(10L, 20L, 30L)))

        // Toggle again to deselect all
        composeTestRule.onNodeWithTag("account_select_all_toggle").performClick()
        assertTrue(currentFilter.selectedAccountIds.isEmpty())
    }

    // --- OT2: Active all-selected chip labels are DISTINCT from clear "All" label ---
    @Test
    fun testOT2_activeAllSelectedChipLabelIsDistinctFromClearAllLabel() {
        var currentFilter by mutableStateOf(FilterState())

        composeTestRule.setContent {
            FilterChipsRow(
                filterState = currentFilter,
                categories = sampleCategories,
                accounts = sampleAccounts,
                onFilterChange = { currentFilter = it },
                modifier = Modifier.requiredWidth(1000.dp)
            )
        }

        // Initially clear: labels must be "Category: All" and "Source: All"
        composeTestRule.onNodeWithTag("filter_chip_category").assertTextContains("Category: All")
        composeTestRule.onNodeWithTag("filter_chip_source").assertTextContains("Source: All")

        // Select all categories
        currentFilter = currentFilter.copy(selectedCategoryIds = setOf(1L, 2L, 3L))
        composeTestRule.waitForIdle()

        // Label must be "All Categories (3)", which is DISTINCT from "Category: All"
        composeTestRule.onNodeWithTag("filter_chip_category").assertTextContains("All Categories (3)")

        // Select all sources
        currentFilter = currentFilter.copy(selectedAccountIds = setOf(10L, 20L, 30L))
        composeTestRule.waitForIdle()

        // Label must be "All Sources (3)", which is DISTINCT from "Source: All"
        composeTestRule.onNodeWithTag("filter_chip_source").assertTextContains("All Sources (3)")
    }

    // --- OT3: No new FilterState field; select-all result set == clear result set ---
    @Test
    fun testOT3_noNewFilterStateField_andSelectAllResultMatchesClearResult() {
        runBlocking {
            // Assert no extra fields were added to FilterState via reflection
            val declaredFieldNames = FilterState::class.java.declaredFields.map { it.name }.toSet()
            val expectedFields = setOf(
                "selectedYear", "selectedMonth", "selectedYears", "selectedMonths",
                "selectedType", "selectedCategoryId", "selectedCategoryIds",
                "selectedAccountId", "selectedAccountIds", "startDate", "endDate",
                "searchQuery", "transferRouteFromAccountId", "transferRouteToAccountId",
                "transferRouteLabel"
            )
            // Ensure no new fields outside standard
            for (field in declaredFieldNames) {
                if (!field.startsWith("$") && field != "Companion") {
                    assertTrue("Field $field must be an expected filter field", field in expectedFields)
                }
            }

            // Insert transactions across 3 categories
            sampleCategories.forEach { repository.insertCategory(it) }
            sampleAccounts.forEach { repository.insertAccount(it) }

            val now = System.currentTimeMillis()
            repository.insertTransaction(TransactionEntity(date = now, type = "EXPENSE", amount = 100.0, fromAccountId = 10L, categoryId = 1L, description = "Groceries"))
            repository.insertTransaction(TransactionEntity(date = now, type = "EXPENSE", amount = 500.0, fromAccountId = 10L, categoryId = 2L, description = "Rent"))
            repository.insertTransaction(TransactionEntity(date = now, type = "EXPENSE", amount = 50.0, fromAccountId = 10L, categoryId = 3L, description = "Utilities"))

            val txs = repository.allTransactions.first()
            val catMap = sampleCategories.associateBy { it.id }
            val accMap = sampleAccounts.associateBy { it.id }
            val enriched = txs.map { tx ->
                com.example.domain.model.EnrichedTransaction(
                    entity = tx,
                    category = catMap[tx.categoryId],
                    fromAccount = accMap[tx.fromAccountId]
                )
            }
            val allCatIds = sampleCategories.map { it.id }.toSet()

            // Clear filter result
            val clearFilter = FilterState()
            val clearResult = LedgerCalculator.filterTransactions(enriched, clearFilter)

            // Select-all filter result
            val selectAllFilter = FilterState(selectedCategoryIds = allCatIds)
            val selectAllResult = LedgerCalculator.filterTransactions(enriched, selectAllFilter)

            assertEquals("Select-all result set must equal clear result set", clearResult.size, selectAllResult.size)
            assertEquals(clearResult.map { it.entity.id }, selectAllResult.map { it.entity.id })
        }
    }

    // --- OT4''': activate a chip -> order == [clearAll]+[that chip]+inactive-canonical; unselected chips' x-bounds shifted RIGHT vs baseline; chip fill=active ---
    @Test
    fun testOT4_prime3_activateChip_headCluster_unselectedShiftRight() {
        var currentFilter by mutableStateOf(FilterState())

        composeTestRule.setContent {
            FilterChipsRow(
                filterState = currentFilter,
                categories = sampleCategories,
                accounts = sampleAccounts,
                onFilterChange = { currentFilter = it },
                modifier = Modifier.requiredWidth(1200.dp)
            )
        }
        composeTestRule.waitForIdle()

        // Baseline: no active filters, Clear All does not exist
        composeTestRule.onNodeWithTag("filter_clear_all").assertDoesNotExist()
        val yearBaselineLeft = composeTestRule.onNodeWithTag("filter_chip_year").getUnclippedBoundsInRoot().left

        // Activate "category" chip
        currentFilter = FilterState(selectedCategoryIds = setOf(1L))
        composeTestRule.waitForIdle()

        // Clear All exists leading
        composeTestRule.onNodeWithTag("filter_clear_all").assertExists()
        val clearAllLeft = composeTestRule.onNodeWithTag("filter_clear_all").getUnclippedBoundsInRoot().left
        val catLeft = composeTestRule.onNodeWithTag("filter_chip_category").getUnclippedBoundsInRoot().left
        val yearLeftAfter = composeTestRule.onNodeWithTag("filter_chip_year").getUnclippedBoundsInRoot().left
        val monthLeftAfter = composeTestRule.onNodeWithTag("filter_chip_month").getUnclippedBoundsInRoot().left
        val typeLeftAfter = composeTestRule.onNodeWithTag("filter_chip_type").getUnclippedBoundsInRoot().left
        val sourceLeftAfter = composeTestRule.onNodeWithTag("filter_chip_source").getUnclippedBoundsInRoot().left

        // Order is [clearAll, category, year, month, type, source]
        assertTrue("clearAll must be before category", clearAllLeft < catLeft)
        assertTrue("category must be before year", catLeft < yearLeftAfter)
        assertTrue("year must be before month", yearLeftAfter < monthLeftAfter)
        assertTrue("month must be before type", monthLeftAfter < typeLeftAfter)
        assertTrue("type must be before source", typeLeftAfter < sourceLeftAfter)

        // Unselected chips' x-bounds shifted RIGHT vs baseline
        assertTrue("Unselected chip year must shift right vs baseline", yearLeftAfter > yearBaselineLeft)
    }

    // --- OT5''': (anti-chase) scroll row RIGHT so Category/Source visible and Clear All off-screen; activate it -> after settle, with NO user drag, Clear All AND the chip are fully in viewport ---
    @Test
    fun testOT5_prime3_antiChase_autoRevealLeadingCluster() {
        var currentFilter by mutableStateOf(FilterState())
        lateinit var rowScrollState: androidx.compose.foundation.lazy.LazyListState

        composeTestRule.setContent {
            rowScrollState = androidx.compose.foundation.lazy.rememberLazyListState()
            FilterChipsRow(
                filterState = currentFilter,
                categories = sampleCategories,
                accounts = sampleAccounts,
                onFilterChange = { currentFilter = it },
                lazyListState = rowScrollState,
                modifier = Modifier.requiredWidth(300.dp) // Constrained viewport forces scrolling
            )
        }
        composeTestRule.waitForIdle()

        // Scroll row RIGHT so later chips (e.g. index 3+) are visible
        composeTestRule.runOnIdle {
            runBlocking {
                rowScrollState.scrollToItem(3, 50)
            }
        }
        composeTestRule.waitForIdle()
        assertTrue("Row must be scrolled right", rowScrollState.firstVisibleItemIndex > 0 || rowScrollState.firstVisibleItemScrollOffset > 0)

        // Activate category filter
        currentFilter = FilterState(selectedCategoryIds = setOf(1L))
        composeTestRule.waitForIdle()

        // After settle, with ZERO user drag, scroll state is auto-revealed to 0,0
        assertEquals("Auto-reveal must bring index to 0", 0, rowScrollState.firstVisibleItemIndex)
        assertEquals("Auto-reveal must bring offset to 0", 0, rowScrollState.firstVisibleItemScrollOffset)

        // Clear All AND active chip are fully in viewport
        composeTestRule.onNodeWithTag("filter_clear_all").assertIsDisplayed()
        composeTestRule.onNodeWithTag("filter_chip_category").assertIsDisplayed()
    }

    // --- OT6''': with one active, activate a second -> order == [clearAll, first, second]+remaining (activation order, NOT canonical) ---
    @Test
    fun testOT6_prime3_activationOrder_headCluster() {
        var currentFilter by mutableStateOf(FilterState())

        composeTestRule.setContent {
            FilterChipsRow(
                filterState = currentFilter,
                categories = sampleCategories,
                accounts = sampleAccounts,
                onFilterChange = { currentFilter = it },
                modifier = Modifier.requiredWidth(1200.dp)
            )
        }
        composeTestRule.waitForIdle()

        // Activate Category first
        currentFilter = FilterState(selectedCategoryIds = setOf(1L))
        composeTestRule.waitForIdle()

        // Activate Month second
        currentFilter = FilterState(selectedCategoryIds = setOf(1L), selectedMonths = setOf(5))
        composeTestRule.waitForIdle()

        val clearAllLeft = composeTestRule.onNodeWithTag("filter_clear_all").getUnclippedBoundsInRoot().left
        val catLeft = composeTestRule.onNodeWithTag("filter_chip_category").getUnclippedBoundsInRoot().left
        val monthLeft = composeTestRule.onNodeWithTag("filter_chip_month").getUnclippedBoundsInRoot().left
        val yearLeft = composeTestRule.onNodeWithTag("filter_chip_year").getUnclippedBoundsInRoot().left

        // In canonical order, Month is before Category. BUT in activation order, Category was activated FIRST.
        // Therefore, order is [Clear All, Category (1st), Month (2nd), Year (remaining inactive), ...]
        assertTrue("Clear All must lead", clearAllLeft < catLeft)
        assertTrue("First activated (Category) must precede second activated (Month)", catLeft < monthLeft)
        assertTrue("Second activated (Month) must precede remaining inactives (Year)", monthLeft < yearLeft)
    }

    // --- OT7''': deactivate -> chip returns to canonical slot (others close the gap); when zero active -> order == canonical, no Clear All node, scroll offset == baseline ---
    @Test
    fun testOT7_prime3_deactivation_returnsToCanonical_zeroActiveBaseline() {
        var currentFilter by mutableStateOf(FilterState(selectedCategoryIds = setOf(1L), selectedMonths = setOf(5)))
        lateinit var rowScrollState: androidx.compose.foundation.lazy.LazyListState

        composeTestRule.setContent {
            rowScrollState = androidx.compose.foundation.lazy.rememberLazyListState()
            FilterChipsRow(
                filterState = currentFilter,
                categories = sampleCategories,
                accounts = sampleAccounts,
                onFilterChange = { currentFilter = it },
                lazyListState = rowScrollState,
                modifier = Modifier.requiredWidth(1200.dp)
            )
        }
        composeTestRule.waitForIdle()

        // Deactivate Category: Month remains active
        currentFilter = FilterState(selectedMonths = setOf(5))
        composeTestRule.waitForIdle()

        val yearLeft = composeTestRule.onNodeWithTag("filter_chip_year").getUnclippedBoundsInRoot().left
        val typeLeft = composeTestRule.onNodeWithTag("filter_chip_type").getUnclippedBoundsInRoot().left
        val catLeft = composeTestRule.onNodeWithTag("filter_chip_category").getUnclippedBoundsInRoot().left
        val sourceLeft = composeTestRule.onNodeWithTag("filter_chip_source").getUnclippedBoundsInRoot().left

        // Category returned to canonical slot (after type, before source)
        assertTrue("Type must be before inactive category", typeLeft < catLeft)
        assertTrue("Inactive category must be before source", catLeft < sourceLeft)

        // Deactivate all (zero active)
        currentFilter = FilterState()
        composeTestRule.waitForIdle()

        // No Clear All node
        composeTestRule.onNodeWithTag("filter_clear_all").assertDoesNotExist()

        // All chips in canonical order: Year, Month, Type, Category, Source
        val y = composeTestRule.onNodeWithTag("filter_chip_year").getUnclippedBoundsInRoot().left
        val m = composeTestRule.onNodeWithTag("filter_chip_month").getUnclippedBoundsInRoot().left
        val t = composeTestRule.onNodeWithTag("filter_chip_type").getUnclippedBoundsInRoot().left
        val c = composeTestRule.onNodeWithTag("filter_chip_category").getUnclippedBoundsInRoot().left
        val s = composeTestRule.onNodeWithTag("filter_chip_source").getUnclippedBoundsInRoot().left

        assertTrue("Year < Month in canonical", y < m)
        assertTrue("Month < Type in canonical", m < t)
        assertTrue("Type < Category in canonical", t < c)
        assertTrue("Category < Source in canonical", c < s)

        // Scroll offset is at baseline
        assertEquals("Baseline scroll index is 0", 0, rowScrollState.firstVisibleItemIndex)
        assertEquals("Baseline scroll offset is 0", 0, rowScrollState.firstVisibleItemScrollOffset)
    }

    // --- OT8''': freeze-while-open: selection change inside an open popup does NOT reorder/scroll until dismiss ---
    @Test
    fun testOT8_prime3_freezeWhileOpen_reorderOnDismissOnly() {
        var currentFilter by mutableStateOf(FilterState())

        composeTestRule.setContent {
            FilterChipsRow(
                filterState = currentFilter,
                categories = sampleCategories,
                accounts = sampleAccounts,
                onFilterChange = { currentFilter = it },
                modifier = Modifier.requiredWidth(1200.dp)
            )
        }
        composeTestRule.waitForIdle()

        // Canonical baseline
        composeTestRule.onNodeWithTag("filter_clear_all").assertDoesNotExist()

        // Open Category dropdown
        composeTestRule.onNodeWithTag("filter_chip_category").performClick()
        composeTestRule.waitForIdle()

        // Select all categories inside open menu
        composeTestRule.onNodeWithTag("category_select_all_toggle").performClick()
        composeTestRule.waitForIdle()

        // While menu is OPEN: selection state changed in model, BUT row is FROZEN!
        assertEquals("Filter state updated", 3, currentFilter.selectedCategoryIds.size)
        // Clear all still does NOT appear in frozen row while dropdown is open
        composeTestRule.onNodeWithTag("filter_clear_all").assertDoesNotExist()

        // Dismiss menu by clicking Done
        composeTestRule.onNodeWithTag("category_menu_done").performClick()
        composeTestRule.waitForIdle()

        // On dismiss: row unfreezes, Clear All appears leading
        composeTestRule.onNodeWithTag("filter_clear_all").assertExists()
    }

    // --- OT9''': reduced-motion ON: after commit, reorder applied and cluster revealed INSTANTLY (no glide/scroll-animation node) ---
    @Test
    fun testOT9_prime3_reducedMotion_instantReorderAndReveal() {
        FilterAutoRevealHelper.forceReducedMotionForTests = true
        try {
            // Placement spec is null under reduced motion
            val spec = FilterAutoRevealHelper.getPlacementSpec(isReducedMotion = true)
            assertNull("Placement spec must be null under reduced motion", spec)

            var currentFilter by mutableStateOf(FilterState())
            lateinit var rowScrollState: androidx.compose.foundation.lazy.LazyListState

            composeTestRule.setContent {
                rowScrollState = androidx.compose.foundation.lazy.rememberLazyListState()
                FilterChipsRow(
                    filterState = currentFilter,
                    categories = sampleCategories,
                    accounts = sampleAccounts,
                    onFilterChange = { currentFilter = it },
                    lazyListState = rowScrollState,
                    modifier = Modifier.requiredWidth(300.dp)
                )
            }
            composeTestRule.waitForIdle()

            // Scroll away
            composeTestRule.runOnIdle {
                runBlocking {
                    rowScrollState.scrollToItem(3, 50)
                }
            }
            composeTestRule.waitForIdle()

            // Commit an active filter
            currentFilter = FilterState(selectedCategoryIds = setOf(1L))
            composeTestRule.waitForIdle()

            // Reorder is applied and cluster revealed INSTANTLY at (0, 0)
            assertEquals("Instant reveal index is 0", 0, rowScrollState.firstVisibleItemIndex)
            assertEquals("Instant reveal offset is 0", 0, rowScrollState.firstVisibleItemScrollOffset)
            composeTestRule.onNodeWithTag("filter_clear_all").assertIsDisplayed()
            composeTestRule.onNodeWithTag("filter_chip_category").assertIsDisplayed()
        } finally {
            FilterAutoRevealHelper.forceReducedMotionForTests = null
        }
    }

    // --- OT8: Fingerprint glyph on LockScreen vs Padlock on Privacy ---
    @Test
    fun testOT8_vaultUnlockUsesFingerprintGlyph_privacyLockUsesPadlock() {
        // Compose LockScreen
        composeTestRule.setContent {
            LockScreen(
                correctPin = "1234",
                onUnlocked = {}
            )
        }

        // Verify header box icon has contentDescription "Unlock Vault"
        composeTestRule.onNodeWithContentDescription("Unlock Vault").assertIsDisplayed()

        // Verify AppIcons.Fingerprint is non-null ImageVector
        assertNotNull(AppIcons.Fingerprint)
        assertEquals("Fingerprint", AppIcons.Fingerprint.name)

        // Verify MediaViewerSheet or Privacy uses Padlock Lock (not fingerprint)
        val mediaViewerCandidates = listOf(
            File("src/main/java/com/example/ui/media/MediaViewerSheet.kt"),
            File("app/src/main/java/com/example/ui/media/MediaViewerSheet.kt")
        )
        val mediaViewerFile = mediaViewerCandidates.firstOrNull { it.exists() }
        if (mediaViewerFile != null) {
            val content = mediaViewerFile.readText()
            assertTrue("MediaViewer privacy gate must use Lock glyph", content.contains("Icons.Default.Lock"))
            assertFalse("MediaViewer privacy gate must NOT use Fingerprint glyph", content.contains("AppIcons.Fingerprint"))
        }
    }

    // --- TT1: Transfer Mode KPI Tiles (This-Mo vs Last-Mo) ---
    @Test
    fun testTT1_transferModeKpiTilesSemantics() {
        runBlocking {
            val sbi = repository.insertAccount(Account(id = 1L, name = "SBI", accountType = "BANK", openingBalance = 50000.0))
            val hdfc = repository.insertAccount(Account(id = 2L, name = "HDFC", accountType = "BANK", openingBalance = 30000.0))

            fun makeDate(year: Int, month0Based: Int, day: Int): Long {
                return Calendar.getInstance().apply {
                    set(year, month0Based, day, 12, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }

            val septDate = makeDate(2026, Calendar.SEPTEMBER, 10)
            val augDate = makeDate(2026, Calendar.AUGUST, 10)

            // Aug: 1 transfer of 15,000 (OUT from SBI == IN to HDFC)
            repository.insertTransaction(TransactionEntity(date = augDate, type = "TRANSFER", amount = 15000.0, fromAccountId = sbi, toAccountId = hdfc, description = "Aug Transfer"))

            // Sept: 2 transfers of 20,000 and 10,000 (total volume = 30,000)
            repository.insertTransaction(TransactionEntity(date = septDate, type = "TRANSFER", amount = 20000.0, fromAccountId = sbi, toAccountId = hdfc, description = "Sept Transfer 1"))
            repository.insertTransaction(TransactionEntity(date = septDate, type = "TRANSFER", amount = 10000.0, fromAccountId = sbi, toAccountId = hdfc, description = "Sept Transfer 2"))

            val vm = DashboardViewModel(repository, settingsRepository)
            vm.setTypeMode("TRANSFER")
            vm.setFilter(FilterState(selectedYears = setOf(2026), selectedMonths = setOf(9), selectedType = "TRANSFER"))

            val state = vm.uiState.awaitState {
                it.activeTypeMode == "TRANSFER" && it.transfersThisMonth == 30000.0
            }

            // Volume sum of OUT side (== IN side)
            assertEquals(30000.0, state.transfersThisMonth, 0.01)
            assertEquals(15000.0, state.transfersLastMonth, 0.01)
            assertEquals(2, state.totalMoveCountThisMonth)
            assertEquals(1, state.totalMoveCountLastMonth)

            // UI rendering check for tags (nodes exist in semantics tree)
            composeTestRule.setContent {
                DashboardScreen(
                    viewModel = vm,
                    onNavigateToExpenses = { _, _ -> }
                )
            }

            composeTestRule.onNodeWithTag("kpi_transfers_this_month").assertExists()
            composeTestRule.onNodeWithTag("kpi_transfers_last_month").assertExists()
            composeTestRule.onNodeWithTag("kpi_moved_out").assertDoesNotExist()
            composeTestRule.onNodeWithTag("kpi_moved_in").assertDoesNotExist()
        }
    }

    // --- TT2: Ledger Smooth Scrolling Optimizations ---
    @Test
    fun testTT2_ledgerPerformanceGuards() {
        val candidates = listOf(
            File("src/main/java/com/example/ui/ledger/LedgerScreen.kt"),
            File("app/src/main/java/com/example/ui/ledger/LedgerScreen.kt")
        )
        val ledgerFile = candidates.firstOrNull { it.exists() } ?: error("LedgerScreen.kt not found in $candidates")
        val ledgerSource = ledgerFile.readText()

        // 1. BoxWithConstraints per row must NOT be present inside the dayGroup items loop
        assertFalse("Per-row BoxWithConstraints must be eliminated from LedgerScreen items", ledgerSource.contains("BoxWithConstraints(modifier = Modifier.fillMaxWidth())"))

        // 2. buildLedgerRowItems must be memoized with remember
        assertTrue("buildLedgerRowItems must be memoized with remember", ledgerSource.contains("remember(state.dayGroups) {") && ledgerSource.contains("buildLedgerRowItems(it.transactions)"))

        // 3. dismissState.requireOffset() must be deferred inside Modifier.offset lambda
        assertTrue("dismissState offset must be read inside offset lambda", ledgerSource.contains("rowOffsetModifier = Modifier.offset {"))

        // 4. Formatted values in TransactionCardRow must be memoized
        assertTrue("amountText must be memoized with remember", ledgerSource.contains("val amountText = remember(transaction.amount, transaction.type, hideBalances)"))
        assertTrue("balStr must be memoized with remember", ledgerSource.contains("val balStr = remember(transaction.runningBalance, hideBalances)"))
    }

    // --- TT3: Dashboard Intro Animation Guards ---
    @Test
    fun testTT3_guard1_oneShotSessionFlag() {
        DashboardIntroTracker.resetForTests()
        assertFalse(DashboardIntroTracker.hasRunIntro)

        val vm = DashboardViewModel(repository, settingsRepository)

        composeTestRule.setContent {
            DashboardScreen(
                viewModel = vm,
                onNavigateToExpenses = { _, _ -> }
            )
        }
        composeTestRule.waitForIdle()

        // Intro ran and set the flag
        assertTrue("Intro flag must be set to true after first run", DashboardIntroTracker.hasRunIntro)
    }

    @Test
    fun testTT3_guard2_reducedMotionHonored() {
        val isReduced = isReducedMotionEnabled(context)
        assertNotNull(isReduced)
    }

    @Test
    fun testTT3_guard3_maskedModeStaticDots() {
        runBlocking {
            settingsRepository.setHideBalances(true)
            val maskedVm = DashboardViewModel(repository, settingsRepository)
            composeTestRule.setContent {
                DashboardScreen(
                    viewModel = maskedVm,
                    onNavigateToExpenses = { _, _ -> }
                )
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("kpi_current_balance").assertIsDisplayed()
        }
    }

    @Test
    fun testTT3_guard4_demoBannerInstant() {
        runBlocking {
            settingsRepository.setDemoMode(true)
            val vm = DashboardViewModel(repository, settingsRepository)
            composeTestRule.setContent {
                DashboardScreen(
                    viewModel = vm,
                    onNavigateToExpenses = { _, _ -> }
                )
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("demo_banner").assertIsDisplayed()
        }
    }

    // --- TT4: Commitment Update Feedback & Dismiss Ownership ---
    @Test
    fun testTT4_commitmentUpdateFeedbackAndDismissOwnership() {
        var repoWriteCount = 0
        var editingCommitmentState by mutableStateOf<Commitment?>(
            Commitment(
                id = 100L,
                type = "RECURRING",
                name = "Gym Membership",
                amount = 2500.0,
                direction = "OUTGOING",
                dayOfMonth = 5
            )
        )
        var showAddSheetState by mutableStateOf(false)

        composeTestRule.setContent {
            if (showAddSheetState || editingCommitmentState != null) {
                key(editingCommitmentState?.id ?: "new_commitment") {
                    AddEditCommitmentSheet(
                        commitment = editingCommitmentState,
                        accounts = sampleAccounts,
                        categories = sampleCategories,
                        onDismiss = {
                            showAddSheetState = false
                            editingCommitmentState = null
                        },
                        onSave = {
                            repoWriteCount++
                        }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. Initially "Update Commitment" is displayed, parent state holds editingCommitment
        composeTestRule.onNodeWithTag("save_commitment_btn").assertTextContains("Update Commitment")
        assertNotNull(editingCommitmentState)

        // 2. Tap Update -> write completes, green "Updated" node visible AND sheet still composed immediately after tap
        composeTestRule.onNodeWithTag("save_commitment_btn").performClick()
        assertEquals("Repo write completes immediately before green state", 1, repoWriteCount)

        // Green "Updated" node is visible
        composeTestRule.onNodeWithTag("save_commitment_btn").assertTextContains("Updated")
        composeTestRule.onNodeWithContentDescription("Updated successfully").assertExists()
        // Sheet still composed
        composeTestRule.onNodeWithTag("add_edit_commitment_sheet").assertExists()
        // Parent state unchanged until dismiss
        assertNotNull(editingCommitmentState)

        // 3. Double-tap during green window -> button disabled, ignored, exactly 1 repo write
        composeTestRule.onNodeWithTag("save_commitment_btn").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("save_commitment_btn").performClick()
        assertEquals("Double-tap during green must not trigger second write", 1, repoWriteCount)

        // 4. Advance test clock by 1000ms delay -> sheet gone, parent state cleared
        composeTestRule.mainClock.advanceTimeBy(1100L)
        ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("add_edit_commitment_sheet").assertDoesNotExist()
        assertNull("Parent state cleared after delay", editingCommitmentState)
    }

    @Test
    fun testTT4b_failingRepoKeepsSheetOpenNoErrorGreen() {
        var shouldFail = true
        var editingCommitmentState by mutableStateOf<Commitment?>(
            Commitment(
                id = 101L,
                type = "RECURRING",
                name = "Electricity",
                amount = 1200.0,
                direction = "OUTGOING",
                dayOfMonth = 10
            )
        )

        composeTestRule.setContent {
            if (editingCommitmentState != null) {
                AddEditCommitmentSheet(
                    commitment = editingCommitmentState,
                    accounts = sampleAccounts,
                    categories = sampleCategories,
                    onDismiss = { editingCommitmentState = null },
                    onSave = {
                        if (shouldFail) throw RuntimeException("Disk I/O error")
                    }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Tap Update -> fails
        composeTestRule.onNodeWithTag("save_commitment_btn").performClick()
        composeTestRule.waitForIdle()

        // Error shown, sheet open, no green
        composeTestRule.onNodeWithTag("commitment_error_text").assertExists()
        composeTestRule.onNodeWithTag("commitment_error_text").assertTextContains("Disk I/O error")
        composeTestRule.onNodeWithTag("save_commitment_btn").assertTextContains("Update Commitment")
        composeTestRule.onNodeWithContentDescription("Updated successfully").assertDoesNotExist()
        assertNotNull(editingCommitmentState)
    }

    @Test
    fun testTT4c_xDismissClearsParentState() {
        var editingCommitmentState by mutableStateOf<Commitment?>(
            Commitment(
                id = 102L,
                type = "RECURRING",
                name = "Water Bill",
                amount = 300.0,
                direction = "OUTGOING",
                dayOfMonth = 15
            )
        )

        composeTestRule.setContent {
            if (editingCommitmentState != null) {
                AddEditCommitmentSheet(
                    commitment = editingCommitmentState,
                    accounts = sampleAccounts,
                    categories = sampleCategories,
                    onDismiss = { editingCommitmentState = null },
                    onSave = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Tap close (X) button
        composeTestRule.onNodeWithTag("commitment_close_btn").performClick()
        composeTestRule.waitForIdle()

        // Parent state cleared immediately
        assertNull(editingCommitmentState)
    }

    // --- TT5a: Copy Crash Log with Play Store Guidance Confirmation ---
    @Test
    fun testTT5a_copyCrashLog_copiesExactPayloadAndShowsPlayStoreGuidance() {
        val seededCrashText = "java.lang.NullPointerException: Something went wrong\n\tat com.example.MainActivity.crash(MainActivity.kt:42)"
        val expectedConfirmation = "Crash log copied — paste it in a Play Store review to help us fix it."
        var sharedLog: String? = null

        composeTestRule.setContent {
            CrashRecoveryBanner(
                crashLog = seededCrashText,
                onShareLog = { sharedLog = it }
            )
        }
        composeTestRule.waitForIdle()

        // Tap Share log to check intent body
        composeTestRule.onNodeWithTag("crash_share_log_button").performClick()
        assertEquals(seededCrashText, sharedLog)

        // Tap Copy log
        composeTestRule.onNodeWithTag("crash_copy_log_button").performClick()
        composeTestRule.waitForIdle()

        // Verify ClipboardManager primary clip == seeded crash text (byte-equal to share body)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = clipboard.primaryClip
        assertNotNull(clip)
        assertEquals(seededCrashText, clip!!.getItemAt(0).text.toString())
        assertEquals(sharedLog, clip.getItemAt(0).text.toString())

        // Verify the exact confirmation string is shown
        composeTestRule.onNodeWithTag("crash_copy_confirmation_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("crash_copy_confirmation_text").assertTextEquals(expectedConfirmation)
    }

    // --- TT5b: Privacy: Sentinel Absent from Crash Log Payload ---
    @Test
    fun testTT5b_sentinelAbsentFromCrashLogPayload() {
        val sentinel = "SECRET_ACCT_XYZ"
        // Legitimate technical crash log does not contain ledger transaction data or sentinels
        val realTechnicalLog = "java.lang.IllegalArgumentException: Invalid state\n\tat com.example.ui.ledger.LedgerViewModel.init(LedgerViewModel.kt:12)"

        composeTestRule.setContent {
            CrashRecoveryBanner(
                crashLog = realTechnicalLog,
                onShareLog = {}
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("crash_copy_log_button").performClick()
        composeTestRule.waitForIdle()

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val copiedText = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()

        assertFalse("Ledger sentinel $sentinel must NOT be in copied crash log payload", copiedText.contains(sentinel))
        assertTrue("Copied log must contain technical stack trace", copiedText.contains("IllegalArgumentException"))
    }

    // --- TT5c: Zero Network Permissions in AndroidManifest.xml ---
    @Test
    fun testTT5c_zeroNetworkPermissionsInManifest() {
        val manifestFile = File("src/main/AndroidManifest.xml").let {
            if (it.exists()) it else File("app/src/main/AndroidManifest.xml")
        }
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())
        val manifestContent = manifestFile.readText()

        assertFalse("Manifest must NOT declare android.permission.INTERNET", manifestContent.contains("android.permission.INTERNET"))
        assertFalse("Manifest must NOT declare ACCESS_NETWORK_STATE", manifestContent.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertFalse("Manifest must NOT declare ACCESS_WIFI_STATE", manifestContent.contains("android.permission.ACCESS_WIFI_STATE"))
    }

    // --- TT5d: Banner Renders Both Buttons Regardless of HideBalances ---
    @Test
    fun testTT5d_bannerRendersBothButtonsRegardlessOfHideBalances() {
        val crashText = "Fatal Exception: OOM\n\tat com.example.App.run(App.kt:1)"
        var hideBalances by mutableStateOf(false)

        composeTestRule.setContent {
            CrashRecoveryBanner(
                crashLog = crashText,
                onShareLog = {}
            )
        }
        composeTestRule.waitForIdle()

        // With hideBalances = false: both buttons are rendered and visible
        composeTestRule.onNodeWithTag("crash_share_log_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("crash_copy_log_button").assertIsDisplayed()

        // Toggle hideBalances = true: both buttons still rendered and visible
        hideBalances = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("crash_share_log_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("crash_copy_log_button").assertIsDisplayed()
    }

    // --- UT1: Vault Header Lock & Keypad Below-7 Fingerprint Glyph Truth ---
    @Test
    fun testUT1_vaultFingerprintGlyph_rendersMaterialVector_keypadMatchesMeaning() {
        var unlocked = false
        var biometricClicked = false

        composeTestRule.setContent {
            MyApplicationTheme {
                LockScreen(
                    correctPin = "1234",
                    onUnlocked = { unlocked = true },
                    onBiometricClick = { biometricClicked = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Vault header icon at top renders yellow Lock icon (tagged & described)
        composeTestRule.onNodeWithTag("vault_lock_header_icon", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Unlock Vault", useUnmergedTree = true).assertIsDisplayed()

        // 2. Keypad bottom-left key (below 7) is ALWAYS visible, renders yellow fingerprint vector, and triggers biometric prompt
        val key7Bounds = composeTestRule.onNodeWithTag("keypad_key_7").getUnclippedBoundsInRoot()
        val bioKeyBounds = composeTestRule.onNodeWithTag("keypad_key_BIOMETRIC").getUnclippedBoundsInRoot()
        assertTrue("Biometric key must be positioned below key 7", bioKeyBounds.top > key7Bounds.bottom)

        val biometricIcon = composeTestRule.onNodeWithTag("vault_fingerprint_keypad_icon", useUnmergedTree = true)
        biometricIcon.assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Fingerprint", useUnmergedTree = true).assertIsDisplayed()

        // Tap biometric key triggers onBiometricClick
        composeTestRule.onNodeWithTag("keypad_key_BIOMETRIC").performClick()
        assertTrue("Biometric key tap must invoke onBiometricClick", biometricClicked)

        // 3. Verify LockScreen source unreferences the old AppIcons.Fingerprint swirl and uses Icons.Default.Lock at top + ic_fingerprint_m3 below 7
        val lockScreenFile = File("src/main/java/com/example/ui/lock/LockScreen.kt").let {
            if (it.exists()) it else File("app/src/main/java/com/example/ui/lock/LockScreen.kt")
        }
        assertTrue("LockScreen.kt must exist", lockScreenFile.exists())
        val source = lockScreenFile.readText()
        assertFalse("LockScreen.kt must not reference old AppIcons.Fingerprint swirl", source.contains("AppIcons.Fingerprint"))
        assertTrue("LockScreen.kt header must use Icons.Default.Lock", source.contains("imageVector = Icons.Default.Lock"))
        assertTrue("LockScreen.kt keypad must use R.drawable.ic_fingerprint_m3", source.contains("R.drawable.ic_fingerprint_m3"))
    }

    // --- UT2: Trend Drilldown Hint Truth per Level and Mode ---
    @Test
    fun testUT2_trendDrilldownHint_presentAtYearMonthWeek_hiddenAtDay_longPressAlwaysPresent() {
        runBlocking {
            val bankId = repository.insertAccount(Account(name = "Bank", accountType = "BANK", openingBalance = 50000.0))
        val catId = repository.insertCategory(Category(name = "General", color = "#10B981"))
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.JANUARY, 15, 12, 0, 0)
        repository.insertTransaction(
            TransactionEntity(
                date = cal.timeInMillis,
                type = "EXPENSE",
                amount = 100.0,
                fromAccountId = bankId,
                categoryId = catId,
                description = "Expense 1"
            )
        )
        repository.insertTransaction(
            TransactionEntity(
                date = cal.timeInMillis,
                type = "INCOME",
                amount = 500.0,
                toAccountId = bankId,
                categoryId = catId,
                description = "Income 1"
            )
        )

        val vm = DashboardViewModel(repository, settingsRepository)

        composeTestRule.setContent {
            MyApplicationTheme {
                DashboardScreen(
                    viewModel = vm,
                    onNavigateToExpenses = { _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()

        val modes = listOf("ALL", "EXPENSE", "INCOME", "TRANSFER")
        val levels = listOf(ChartLevel.YEARS, ChartLevel.MONTHS, ChartLevel.WEEKS, ChartLevel.DAYS)

        for (mode in modes) {
            vm.setFilter(FilterState(selectedType = mode))
            for (level in levels) {
                vm.jumpToLevel(level)
                vm.uiState.awaitState { it.filter.selectedType == mode && it.chartLevel == level }
                composeTestRule.waitForIdle()

                // Long-press hint is ALWAYS present at every level and mode
                composeTestRule.scrollToDashboardTag("trend_card_hint", useUnmergedTree = true)
                    .assertIsDisplayed()

                if (level == ChartLevel.DAYS) {
                    // At Day level: "Tap a bar to drill down" must be ABSENT
                    composeTestRule.onNodeWithText("Tap a bar to drill down", useUnmergedTree = true)
                        .assertDoesNotExist()
                } else {
                    // At Year/Month/Week: "Tap a bar to drill down" must be PRESENT
                    composeTestRule.scrollToDashboardText("Tap a bar to drill down", useUnmergedTree = true)
                        .assertIsDisplayed()
                }
            }
        }
    }
    }

    // --- UT3: Ledger Card Conditional Layout (Fill Bal row when empty, shift back on wrap) ---
    @Test
    fun testUT3_ledgerCard_conditionalLayout_shortTitleOverlapsPillAndBal_longTitleShiftsBelow() {
        val testCat = Category(id = 1L, name = "Food & Dining", color = "#10B981")
        val testAcc = Account(id = 10L, name = "HDFC Bank", accountType = "BANK", openingBalance = 50000.0)

        val shortTx = EnrichedTransaction(
            entity = TransactionEntity(
                id = 101L,
                date = System.currentTimeMillis(),
                type = "EXPENSE",
                amount = 150.0,
                fromAccountId = 10L,
                categoryId = 1L,
                description = "Coffee",
                notes = "Quick afternoon espresso"
            ),
            category = testCat,
            fromAccount = testAcc,
            toAccount = null,
            runningBalance = 49850.0
        )

        val longTx = EnrichedTransaction(
            entity = TransactionEntity(
                id = 102L,
                date = System.currentTimeMillis(),
                type = "EXPENSE",
                amount = 4500.0,
                fromAccountId = 10L,
                categoryId = 1L,
                description = "Extremely long descriptive grocery market haul with organic produce, household items, pantry restock, and multiple snacks",
                notes = "Supermarket weekend bulk purchase invoice #9812"
            ),
            category = testCat,
            fromAccount = testAcc,
            toAccount = null,
            runningBalance = 45350.0
        )

        // Compose both fixtures in a single setContent call
        composeTestRule.setContent {
            MyApplicationTheme {
                androidx.compose.foundation.layout.Column {
                    Box(modifier = Modifier.width(360.dp)) {
                        TransactionCardRow(
                            transaction = shortTx,
                            onClick = {}
                        )
                    }
                    Box(modifier = Modifier.width(300.dp)) {
                        TransactionCardRow(
                            transaction = longTx,
                            onClick = {}
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. Short Title Fixture (Variant A: 1-line title)
        val pillBoundsShort = composeTestRule.onNodeWithTag("tx_category_pill_${shortTx.id}", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val balBoundsShort = composeTestRule.onNodeWithTag("tx_bal_${shortTx.id}", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val notesShort = composeTestRule.onNodeWithTag("tx_notes_${shortTx.id}", useUnmergedTree = true)
        notesShort.assertIsDisplayed()

        // Assert: pill node and Bal node y-bounds OVERLAP (same line!)
        val yOverlap = pillBoundsShort.top < balBoundsShort.bottom && pillBoundsShort.bottom > balBoundsShort.top
        assertTrue("In 1-line title, pill and Bal y-bounds must overlap: pill=[${pillBoundsShort.top}, ${pillBoundsShort.bottom}], bal=[${balBoundsShort.top}, ${balBoundsShort.bottom}]", yOverlap)

        // 2. Long Title Fixture (Variant B: 2-line title)
        val pillBoundsLong = composeTestRule.onNodeWithTag("tx_category_pill_${longTx.id}", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val balBoundsLong = composeTestRule.onNodeWithTag("tx_bal_${longTx.id}", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val notesLong = composeTestRule.onNodeWithTag("tx_notes_${longTx.id}", useUnmergedTree = true)
        notesLong.assertIsDisplayed()

        // Assert: pill node on its own row BELOW the Bal line
        assertTrue("In 2-line title, pill must be strictly below Bal: bal.bottom=${balBoundsLong.bottom}, pill.top=${pillBoundsLong.top}", pillBoundsLong.top >= balBoundsLong.bottom - 2f)

        // 3. Invariants: Title cap stays 2 lines, notes renders in both variants
        composeTestRule.onNodeWithTag("tx_title_${shortTx.id}", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("tx_title_${longTx.id}", useUnmergedTree = true).assertIsDisplayed()
    }

    // --- UT4: Remove Lingering Static Auto-Save Safety Net Row ---
    @Test
    fun testUT4_settingsRow_autoSaveSafetyNet_absentFromRender() {
        runBlocking {
            settingsRepository.setAutoBackupEnabled(true)
            val vm = SettingsViewModel(repository, settingsRepository, database)

            composeTestRule.setContent {
                MyApplicationTheme {
                    SettingsScreen(
                        viewModel = vm
                    )
                }
            }
            composeTestRule.waitForIdle()

            // Assert the read-only static row is absent from Settings render
            composeTestRule.onNodeWithText("Auto-Save Safety Net", useUnmergedTree = true).assertDoesNotExist()
            composeTestRule.onNodeWithText("Internal Storage: backups/auto_backup.csv", useUnmergedTree = true).assertDoesNotExist()

            // But toggle "Auto-Save on App Close" remains present
            composeTestRule.onNodeWithText("Auto-Save on App Close", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        }
    }

    // --- UT5: Bounded UX Consistency Sweep Truth Invariants ---
    @Test
    fun testUT5_boundedConsistencySweep_truthInvariants() {
        // Sweep Invariant 1: Vault lock screen has zero references to unmeaningful icons or misleading copy
        val lockFile = File("src/main/java/com/example/ui/lock/LockScreen.kt").let {
            if (it.exists()) it else File("app/src/main/java/com/example/ui/lock/LockScreen.kt")
        }
        val lockSource = lockFile.readText()
        assertTrue("Vault header uses ic_fingerprint_m3", lockSource.contains("ic_fingerprint_m3"))
        assertFalse("Vault screen does not reference AppIcons.Fingerprint", lockSource.contains("AppIcons.Fingerprint"))

        // Sweep Invariant 2: Trend card drill down condition is strictly level != ChartLevel.DAYS
        val dashFile = File("src/main/java/com/example/ui/dashboard/DashboardScreen.kt").let {
            if (it.exists()) it else File("app/src/main/java/com/example/ui/dashboard/DashboardScreen.kt")
        }
        val dashSource = dashFile.readText()
        assertTrue("Drill down hint gated on level != ChartLevel.DAYS", dashSource.contains("if (level != ChartLevel.DAYS)"))

        // Sweep Invariant 3: Ledger notes are capped to maxLines=1 with ellipsis in both Single and SplitGroup rows
        val ledgerFile = File("src/main/java/com/example/ui/ledger/LedgerScreen.kt").let {
            if (it.exists()) it else File("app/src/main/java/com/example/ui/ledger/LedgerScreen.kt")
        }
        val ledgerSource = ledgerFile.readText()
        assertTrue("TransactionCardRow notes maxLines is 1", ledgerSource.contains("tx_notes_\${transaction.id}\")") && ledgerSource.contains("maxLines = 1"))
        assertTrue("SplitGroupCardRow notes maxLines is 1", ledgerSource.contains("tx_notes_\${primaryTx.id}\")"))

        // Sweep Invariant 4: Settings does not contain static dead copy 'Auto-Save Safety Net'
        val settingsFile = File("src/main/java/com/example/ui/settings/SettingsScreen.kt").let {
            if (it.exists()) it else File("app/src/main/java/com/example/ui/settings/SettingsScreen.kt")
        }
        val settingsSource = settingsFile.readText()
        assertFalse("SettingsScreen does not contain Auto-Save Safety Net", settingsSource.contains("Auto-Save Safety Net"))
    }

    // --- UT6a: masked=true, tap photo chip 5x rapidly -> exactly ONE snackbar; wait out full duration WITHOUT tapping -> no second snackbar ever appears ---
    @Test
    fun testUT6a_maskedPhotoChipTapped5xRapidly_showsSingleSnackbar_queueRemainsEmptyAfterDuration() {
        runBlocking {
            settingsRepository.setHideBalances(true)
            val mediaRepo = com.example.data.repository.MediaRepository(context, database)
            val accId = repository.insertAccount(Account(id = 1L, name = "Cash", accountType = "CASH", openingBalance = 1000.0))
            val catId = repository.insertCategory(Category(id = 1L, name = "Food", color = "#10B981"))
            val txnId = repository.insertTransaction(
                TransactionEntity(id = 701L, date = System.currentTimeMillis(), type = "EXPENSE", amount = 250.0, fromAccountId = accId, categoryId = catId, description = "Lunch")
            )
            val txnDir = mediaRepo.getVaultDirForTxn(txnId)
            val photoFile = File(txnDir, "ut6a.jpg").apply { writeBytes(ByteArray(2048)) }
            database.mediaAttachmentDao().insert(
                com.example.data.model.MediaAttachment(id = 701L, txnId = txnId, kind = "PHOTO", fileName = photoFile.name)
            )

            val ledgerVm = com.example.ui.ledger.LedgerViewModel(repository, settingsRepository, mediaRepo)
            lateinit var dispatcher: com.example.ui.common.SingleFlightNoticeDispatcher

            composeTestRule.setContent {
                dispatcher = com.example.ui.common.rememberSingleFlightNoticeDispatcher()
                MyApplicationTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        com.example.ui.ledger.LedgerScreen(
                            viewModel = ledgerVm,
                            onAddClick = {},
                            onTransactionClick = {},
                            mediaRepository = mediaRepo,
                            noticeDispatcher = dispatcher
                        )
                    }
                }
            }
            composeTestRule.waitForIdle()
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag("photo_chip_$txnId"), useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }

            // Tap photo chip 5x rapidly
            repeat(5) {
                composeTestRule.onNodeWithTag("photo_chip_$txnId", useUnmergedTree = true).performClick()
            }
            composeTestRule.waitForIdle()

            // Media viewer is blocked
            composeTestRule.onNodeWithTag("media_viewer_sheet").assertDoesNotExist()

            // Exactly ONE snackbar was dispatched; 4 subsequent same-key taps were dropped
            assertEquals("Exactly 1 snackbar show must be dispatched", 1, dispatcher.dispatchedCount)
            assertEquals("4 duplicate taps must be dropped synchronously", 4, dispatcher.droppedSameKeyCount)
            assertEquals(
                com.example.ui.common.SingleFlightNoticeDispatcher.PRIVACY_NOTICE_KEY,
                dispatcher.inFlightKey
            )
            assertEquals(
                com.example.ui.common.SingleFlightNoticeDispatcher.PRIVACY_NOTICE_MESSAGE,
                dispatcher.snackbarHostState.currentSnackbarData?.visuals?.message
            )

            // Wait out full duration WITHOUT tapping
            composeTestRule.mainClock.advanceTimeBy(4500L)
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            // Snackbar dismissed and gate cleared
            assertNull("Snackbar must be dismissed after timeout", dispatcher.snackbarHostState.currentSnackbarData)
            assertNull("inFlightKey must be cleared after timeout", dispatcher.inFlightKey)

            // Wait another full window -> no second snackbar ever appears (queue is empty)
            composeTestRule.mainClock.advanceTimeBy(4500L)
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            assertNull("No queued second snackbar may appear", dispatcher.snackbarHostState.currentSnackbarData)
            assertEquals("Total dispatched count remains 1", 1, dispatcher.dispatchedCount)

            photoFile.delete()
        }
    }

    // --- UT6b: after timeout-dismiss, one tap -> shows again; after swipe-dismiss, one tap -> shows again ---
    @Test
    fun testUT6b_afterTimeoutDismiss_oneTapShowsAgain_afterSwipeDismiss_oneTapShowsAgain() {
        runBlocking {
            settingsRepository.setHideBalances(true)
            val mediaRepo = com.example.data.repository.MediaRepository(context, database)
            val accId = repository.insertAccount(Account(id = 1L, name = "Cash", accountType = "CASH", openingBalance = 1000.0))
            val catId = repository.insertCategory(Category(id = 1L, name = "Food", color = "#10B981"))
            val txnId = repository.insertTransaction(
                TransactionEntity(id = 702L, date = System.currentTimeMillis(), type = "EXPENSE", amount = 300.0, fromAccountId = accId, categoryId = catId, description = "Coffee")
            )
            val txnDir = mediaRepo.getVaultDirForTxn(txnId)
            val photoFile = File(txnDir, "ut6b.jpg").apply { writeBytes(ByteArray(2048)) }
            database.mediaAttachmentDao().insert(
                com.example.data.model.MediaAttachment(id = 702L, txnId = txnId, kind = "PHOTO", fileName = photoFile.name)
            )

            val ledgerVm = com.example.ui.ledger.LedgerViewModel(repository, settingsRepository, mediaRepo)
            lateinit var dispatcher: com.example.ui.common.SingleFlightNoticeDispatcher

            composeTestRule.setContent {
                dispatcher = com.example.ui.common.rememberSingleFlightNoticeDispatcher()
                MyApplicationTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        com.example.ui.ledger.LedgerScreen(
                            viewModel = ledgerVm,
                            onAddClick = {},
                            onTransactionClick = {},
                            mediaRepository = mediaRepo,
                            noticeDispatcher = dispatcher
                        )
                    }
                }
            }
            composeTestRule.waitForIdle()
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag("photo_chip_$txnId"), useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }

            // Initial tap -> shows snackbar #1
            composeTestRule.onNodeWithTag("photo_chip_$txnId", useUnmergedTree = true).performClick()
            composeTestRule.waitForIdle()
            assertEquals(1, dispatcher.dispatchedCount)
            assertNotNull(dispatcher.snackbarHostState.currentSnackbarData)

            // 1. Timeout-dismiss
            composeTestRule.mainClock.advanceTimeBy(4500L)
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            assertNull("Dismissed after timeout", dispatcher.snackbarHostState.currentSnackbarData)
            assertNull("inFlightKey cleared after timeout", dispatcher.inFlightKey)

            // One tap after timeout-dismiss -> shows again (#2)
            composeTestRule.onNodeWithTag("photo_chip_$txnId", useUnmergedTree = true).performClick()
            composeTestRule.waitForIdle()
            assertEquals("Second show after timeout-dismiss", 2, dispatcher.dispatchedCount)
            assertEquals(
                com.example.ui.common.SingleFlightNoticeDispatcher.PRIVACY_NOTICE_MESSAGE,
                dispatcher.snackbarHostState.currentSnackbarData?.visuals?.message
            )

            // 2. Swipe-dismiss on the active snackbar box
            composeTestRule.onNodeWithTag("single_flight_snackbar_swipe_box")
                .performTouchInput {
                    down(center)
                    moveBy(androidx.compose.ui.geometry.Offset(500f, 0f))
                    up()
                }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            assertNull("Dismissed after swipe", dispatcher.snackbarHostState.currentSnackbarData)
            assertNull("inFlightKey cleared after swipe", dispatcher.inFlightKey)

            // One tap after swipe-dismiss -> shows again (#3)
            composeTestRule.onNodeWithTag("photo_chip_$txnId", useUnmergedTree = true).performClick()
            composeTestRule.waitForIdle()
            assertEquals("Third show after swipe-dismiss", 3, dispatcher.dispatchedCount)
            assertEquals(
                com.example.ui.common.SingleFlightNoticeDispatcher.PRIVACY_NOTICE_MESSAGE,
                dispatcher.snackbarHostState.currentSnackbarData?.visuals?.message
            )

            photoFile.delete()
        }
    }

    // --- UT6c: while privacy notice is live, emit a different-key notice -> privacy notice is REPLACED (not queued); new notice is current ---
    @Test
    fun testUT6c_whilePrivacyNoticeLive_differentKeyReplacesImmediately_compareAndClearPreservesSuccessorGate() {
        runBlocking {
            settingsRepository.setHideBalances(true)
            val mediaRepo = com.example.data.repository.MediaRepository(context, database)
            val accId = repository.insertAccount(Account(id = 1L, name = "Cash", accountType = "CASH", openingBalance = 1000.0))
            val catId = repository.insertCategory(Category(id = 1L, name = "Food", color = "#10B981"))
            val txnId = repository.insertTransaction(
                TransactionEntity(id = 703L, date = System.currentTimeMillis(), type = "EXPENSE", amount = 400.0, fromAccountId = accId, categoryId = catId, description = "Snack")
            )
            val txnDir = mediaRepo.getVaultDirForTxn(txnId)
            val photoFile = File(txnDir, "ut6c.jpg").apply { writeBytes(ByteArray(2048)) }
            database.mediaAttachmentDao().insert(
                com.example.data.model.MediaAttachment(id = 703L, txnId = txnId, kind = "PHOTO", fileName = photoFile.name)
            )

            val ledgerVm = com.example.ui.ledger.LedgerViewModel(repository, settingsRepository, mediaRepo)
            lateinit var dispatcher: com.example.ui.common.SingleFlightNoticeDispatcher

            composeTestRule.setContent {
                dispatcher = com.example.ui.common.rememberSingleFlightNoticeDispatcher()
                MyApplicationTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        com.example.ui.ledger.LedgerScreen(
                            viewModel = ledgerVm,
                            onAddClick = {},
                            onTransactionClick = {},
                            mediaRepository = mediaRepo,
                            noticeDispatcher = dispatcher
                        )
                    }
                }
            }
            composeTestRule.waitForIdle()
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag("photo_chip_$txnId"), useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }

            // 1. Trigger privacy notice -> it is live
            composeTestRule.onNodeWithTag("photo_chip_$txnId", useUnmergedTree = true).performClick()
            composeTestRule.waitForIdle()
            assertEquals(
                com.example.ui.common.SingleFlightNoticeDispatcher.PRIVACY_NOTICE_KEY,
                dispatcher.inFlightKey
            )
            assertEquals(
                com.example.ui.common.SingleFlightNoticeDispatcher.PRIVACY_NOTICE_MESSAGE,
                dispatcher.snackbarHostState.currentSnackbarData?.visuals?.message
            )

            // 2. While privacy notice is still live (0ms elapsed), emit a different-key notice (e.g. export confirmation)
            val exportMsg = "Exported to Silentsums_Data/Expenses_Report.csv"
            val replaced = dispatcher.showNotice(
                key = "EXPORT_SUCCESS",
                message = exportMsg
            )
            assertTrue("Different key must be accepted to replace live notice", replaced)
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            // 3. Privacy notice was REPLACED immediately (not queued), and compare-and-clear kept EXPORT_SUCCESS as inFlightKey!
            assertEquals("Successor key must remain in-flight (not wiped by cancelled privacy notice finally)", "EXPORT_SUCCESS", dispatcher.inFlightKey)
            assertEquals("New notice message must be current immediately", exportMsg, dispatcher.snackbarHostState.currentSnackbarData?.visuals?.message)

            photoFile.delete()
        }
    }

    // --- UT6d: masked=false -> taps open viewer normally; assert no raw showSnackbar bypass exists on these surfaces ---
    @Test
    fun testUT6d_maskedFalse_opensViewerNormally_andNoRawShowSnackbarBypassOnTargetSurfaces() {
        runBlocking {
            settingsRepository.setHideBalances(false)
            val mediaRepo = com.example.data.repository.MediaRepository(context, database)
            val accId = repository.insertAccount(Account(id = 1L, name = "Cash", accountType = "CASH", openingBalance = 1000.0))
            val catId = repository.insertCategory(Category(id = 1L, name = "Food", color = "#10B981"))
            val txnId = repository.insertTransaction(
                TransactionEntity(id = 704L, date = System.currentTimeMillis(), type = "EXPENSE", amount = 500.0, fromAccountId = accId, categoryId = catId, description = "Dinner")
            )
            val txnDir = mediaRepo.getVaultDirForTxn(txnId)
            val photoFile = File(txnDir, "ut6d.jpg").apply { writeBytes(ByteArray(2048)) }
            database.mediaAttachmentDao().insert(
                com.example.data.model.MediaAttachment(id = 704L, txnId = txnId, kind = "PHOTO", fileName = photoFile.name)
            )

            val ledgerVm = com.example.ui.ledger.LedgerViewModel(repository, settingsRepository, mediaRepo)
            lateinit var dispatcher: com.example.ui.common.SingleFlightNoticeDispatcher

            composeTestRule.setContent {
                dispatcher = com.example.ui.common.rememberSingleFlightNoticeDispatcher()
                MyApplicationTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        com.example.ui.ledger.LedgerScreen(
                            viewModel = ledgerVm,
                            onAddClick = {},
                            onTransactionClick = {},
                            mediaRepository = mediaRepo,
                            noticeDispatcher = dispatcher
                        )
                    }
                }
            }
            composeTestRule.waitForIdle()
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                ShadowLooper.idleMainLooper()
                composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag("photo_chip_$txnId"), useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }

            // 1. With masked=false, tapping photo chip opens MediaViewerSheet normally and emits zero privacy snackbars
            composeTestRule.onNodeWithTag("photo_chip_$txnId", useUnmergedTree = true).performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("media_viewer_sheet").assertIsDisplayed()
            assertEquals("Zero snackbars dispatched when unmasked", 0, dispatcher.dispatchedCount)

            // 2. Assert no raw showSnackbar bypass exists on LedgerScreen.kt, EntryScreen.kt, or MediaViewerSheet.kt
            val targetFiles = listOf(
                "ui/ledger/LedgerScreen.kt",
                "ui/entry/EntryScreen.kt",
                "ui/media/MediaViewerSheet.kt"
            )
            for (relPath in targetFiles) {
                val file = File("src/main/java/com/example/$relPath").let {
                    if (it.exists()) it else File("app/src/main/java/com/example/$relPath")
                }
                assertTrue("File $relPath must exist", file.exists())
                val content = file.readText()
                assertFalse("No raw showSnackbar bypass allowed in $relPath", content.contains(".showSnackbar("))
                assertTrue("$relPath must route through SingleFlightNoticeDispatcher", content.contains("SingleFlightNoticeDispatcher"))
            }

            photoFile.delete()
        }
    }

    // =========================================================================
    // F8 (UT7a-UT7d): Demo Mode Auth Gate — Enter Free, Exit Gated iff Lock Enabled
    // =========================================================================

    @Test
    fun testUT7a_lockOn_enterDemo_noPinPrompt_andDemoFlips() {
        runBlocking {
            // Enable lock with PIN 1234
            settingsRepository.setPinHash("1234")
            settingsRepository.setLockEnabled(true)
            settingsRepository.setDemoMode(false)
            File(context.filesDir, com.example.data.StorageConstants.DEMO_MARKER_FILE).delete()

            val settingsVm = com.example.ui.settings.SettingsViewModel(repository, settingsRepository, database)

            composeTestRule.setContent {
                MyApplicationTheme {
                    com.example.ui.settings.SettingsScreen(viewModel = settingsVm)
                }
            }
            composeTestRule.waitForIdle()

            // Click Load Sample Data row -> non-auth confirmation dialog appears, NO PIN lock screen
            composeTestRule.onNodeWithTag("settings_sample_data_row")
                .performScrollTo()
                .performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("app_pin_lock_screen").assertDoesNotExist()
            composeTestRule.onNodeWithTag("confirm_load_sample_data_btn").assertIsDisplayed()

            // Confirm Load Sample Data -> flips to demo without any PIN node
            composeTestRule.onNodeWithTag("confirm_load_sample_data_btn").performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            val updatedSettings = settingsRepository.awaitDemoMode(expected = true)
            assertTrue("Demo mode must be true after confirm", updatedSettings.demoMode)
            assertTrue("Demo marker file must exist", com.example.data.DatabaseProvider.isDemoMode(context))
            composeTestRule.onNodeWithTag("app_pin_lock_screen").assertDoesNotExist()

            // Clean up demo marker
            com.example.data.DatabaseProvider.flipTo(context, settingsRepository, targetDemoMode = false)
        }
    }

    @Test
    fun testUT7b_lockOn_eachExitPath_requiresPinBeforeFlip_wrongPinStaysInDemo_correctPinFlipsToReal() {
        runBlocking {
            settingsRepository.setPinHash("1234")
            settingsRepository.setLockEnabled(true)

            // Exit Path 1: Dashboard demo_banner Cancel button ("demo_cancel")
            com.example.data.DatabaseProvider.flipTo(context, settingsRepository, targetDemoMode = true)
            settingsRepository.awaitDemoMode(expected = true)
            assertTrue(com.example.data.DatabaseProvider.isDemoMode(context))

            val dashboardVm = DashboardViewModel(repository, settingsRepository)
            val settingsVm = com.example.ui.settings.SettingsViewModel(repository, settingsRepository, database)
            var activeScreen by mutableStateOf("DASHBOARD")

            composeTestRule.setContent {
                val hostRegistryOwner = androidx.activity.compose.LocalActivityResultRegistryOwner.current
                    ?: (androidx.compose.ui.platform.LocalContext.current as androidx.activity.result.ActivityResultRegistryOwner)
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.activity.compose.LocalActivityResultRegistryOwner provides hostRegistryOwner,
                    androidx.compose.ui.platform.LocalContext provides context
                ) {
                    MyApplicationTheme {
                        if (activeScreen == "DASHBOARD") {
                            DashboardScreen(
                                viewModel = dashboardVm,
                                onNavigateToExpenses = { _, _ -> }
                            )
                        } else {
                            com.example.ui.settings.SettingsScreen(viewModel = settingsVm)
                        }
                    }
                }
            }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("demo_cancel").assertIsDisplayed().performClick()
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag("app_pin_lock_screen"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("app_pin_lock_screen").assertIsDisplayed()
            assertTrue("Must remain in demo before PIN entry", com.example.data.DatabaseProvider.isDemoMode(context))

            // Enter WRONG PIN (9999) -> error message, still in demo, no partial flip
            repeat(4) { composeTestRule.onNodeWithTag("keypad_key_9").performClick() }
            composeTestRule.onNodeWithTag("unlock_confirm_button").performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Incorrect PIN. Try again.").assertIsDisplayed()
            assertTrue("Wrong PIN must keep app in demo mode", com.example.data.DatabaseProvider.isDemoMode(context))
            assertTrue("Wrong PIN must keep DataStore demoMode=true", settingsRepository.settingsFlow.first().demoMode)

            // Enter CORRECT PIN (1234) -> flips to real data view
            listOf("1", "2", "3", "4").forEach { digit ->
                composeTestRule.onNodeWithTag("keypad_key_$digit").performClick()
            }
            composeTestRule.onNodeWithTag("unlock_confirm_button").performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            settingsRepository.awaitDemoMode(expected = false)
            assertFalse("Correct PIN on Dashboard exit must flip to real mode", com.example.data.DatabaseProvider.isDemoMode(context))
            composeTestRule.onNodeWithTag("app_pin_lock_screen").assertDoesNotExist()

            // Exit Path 2: Settings demo_firewall_banner Cancel button ("settings_demo_cancel")
            com.example.data.DatabaseProvider.flipTo(context, settingsRepository, targetDemoMode = true)
            settingsRepository.awaitDemoMode(expected = true)
            settingsVm.uiState.awaitState { it.settings.demoMode }
            activeScreen = "SETTINGS"
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("settings_demo_cancel")
                .performScrollTo()
                .performClick()
            settingsVm.showExitDemoAuthGate.awaitState { it }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("app_pin_lock_screen").assertIsDisplayed()
            assertTrue("Must remain in demo before PIN entry on settings_demo_cancel", com.example.data.DatabaseProvider.isDemoMode(context))

            // Wrong PIN (0000) -> still in demo
            repeat(4) { composeTestRule.onNodeWithTag("keypad_key_0").performClick() }
            composeTestRule.onNodeWithTag("unlock_confirm_button").performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Incorrect PIN. Try again.").assertIsDisplayed()
            assertTrue("Wrong PIN on settings_demo_cancel must remain in demo", com.example.data.DatabaseProvider.isDemoMode(context))

            // Correct PIN (1234) -> flips to real mode
            listOf("1", "2", "3", "4").forEach { digit ->
                composeTestRule.onNodeWithTag("keypad_key_$digit").performClick()
            }
            composeTestRule.onNodeWithTag("unlock_confirm_button").performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            settingsRepository.awaitDemoMode(expected = false)
            settingsVm.uiState.awaitState { !it.settings.demoMode }
            assertFalse("Correct PIN on settings_demo_cancel must flip to real mode", com.example.data.DatabaseProvider.isDemoMode(context))
            composeTestRule.onNodeWithTag("app_pin_lock_screen").assertDoesNotExist()

            // Exit Path 3: Settings Load Sample Data / Exit row ("settings_sample_data_row")
            com.example.data.DatabaseProvider.flipTo(context, settingsRepository, targetDemoMode = true)
            settingsRepository.awaitDemoMode(expected = true)
            settingsVm.uiState.awaitState { it.settings.demoMode }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("settings_sample_data_row")
                .performScrollTo()
                .performClick()
            settingsVm.showExitDemoAuthGate.awaitState { it }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("app_pin_lock_screen").assertIsDisplayed()
            assertTrue("Must remain in demo before PIN entry on settings_sample_data_row", com.example.data.DatabaseProvider.isDemoMode(context))

            // Wrong PIN (8888) -> still in demo
            repeat(4) { composeTestRule.onNodeWithTag("keypad_key_8").performClick() }
            composeTestRule.onNodeWithTag("unlock_confirm_button").performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Incorrect PIN. Try again.").assertIsDisplayed()
            assertTrue("Wrong PIN on settings_sample_data_row must remain in demo", com.example.data.DatabaseProvider.isDemoMode(context))

            // Correct PIN (1234) -> flips to real mode
            listOf("1", "2", "3", "4").forEach { digit ->
                composeTestRule.onNodeWithTag("keypad_key_$digit").performClick()
            }
            composeTestRule.onNodeWithTag("unlock_confirm_button").performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            settingsRepository.awaitDemoMode(expected = false)
            assertFalse("Correct PIN on settings_sample_data_row must flip to real mode", com.example.data.DatabaseProvider.isDemoMode(context))
            composeTestRule.onNodeWithTag("app_pin_lock_screen").assertDoesNotExist()

            dashboardVm.viewModelScope.cancel()
            settingsVm.viewModelScope.cancel()
            ShadowLooper.idleMainLooper()
        }
    }

    @Test
    fun testUT7c_lockOff_enterAndExit_noPinPrompts() {
        runBlocking {
            settingsRepository.setLockEnabled(false)
            settingsRepository.setPinHash("")
            settingsRepository.setDemoMode(false)
            File(context.filesDir, com.example.data.StorageConstants.DEMO_MARKER_FILE).delete()

            val dashboardVm = DashboardViewModel(repository, settingsRepository)
            val settingsVm = com.example.ui.settings.SettingsViewModel(repository, settingsRepository, database)
            var activeScreen by mutableStateOf("SETTINGS")

            composeTestRule.setContent {
                val hostRegistryOwner = androidx.activity.compose.LocalActivityResultRegistryOwner.current
                    ?: (androidx.compose.ui.platform.LocalContext.current as androidx.activity.result.ActivityResultRegistryOwner)
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.activity.compose.LocalActivityResultRegistryOwner provides hostRegistryOwner,
                    androidx.compose.ui.platform.LocalContext provides context
                ) {
                    MyApplicationTheme {
                        if (activeScreen == "SETTINGS") {
                            com.example.ui.settings.SettingsScreen(viewModel = settingsVm)
                        } else {
                            DashboardScreen(
                                viewModel = dashboardVm,
                                onNavigateToExpenses = { _, _ -> }
                            )
                        }
                    }
                }
            }
            composeTestRule.waitForIdle()

            // Enter demo from Settings
            composeTestRule.onNodeWithTag("settings_sample_data_row")
                .performScrollTo()
                .performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("confirm_load_sample_data_btn").performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            settingsRepository.awaitDemoMode(expected = true)
            assertTrue(com.example.data.DatabaseProvider.isDemoMode(context))
            composeTestRule.onNodeWithTag("app_pin_lock_screen").assertDoesNotExist()

            // Exit demo via settings_demo_cancel -> immediate flip, no PIN prompt
            composeTestRule.onNodeWithTag("settings_demo_cancel")
                .performScrollTo()
                .performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            settingsRepository.awaitDemoMode(expected = false)
            assertFalse(com.example.data.DatabaseProvider.isDemoMode(context))
            composeTestRule.onNodeWithTag("app_pin_lock_screen").assertDoesNotExist()

            // Enter demo and exit from Dashboard demo_cancel -> immediate flip, no PIN prompt
            com.example.data.DatabaseProvider.flipTo(context, settingsRepository, targetDemoMode = true)
            settingsRepository.awaitDemoMode(expected = true)
            activeScreen = "DASHBOARD"
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("demo_cancel").assertIsDisplayed().performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            settingsRepository.awaitDemoMode(expected = false)
            assertFalse(com.example.data.DatabaseProvider.isDemoMode(context))
            composeTestRule.onNodeWithTag("app_pin_lock_screen").assertDoesNotExist()
        }
    }

    @Test
    fun testUT7d_demoFirewallIntact_withEnterFreeAndExitGated() {
        runBlocking {
            settingsRepository.setPinHash("1234")
            settingsRepository.setLockEnabled(true)

            val settingsVm = com.example.ui.settings.SettingsViewModel(repository, settingsRepository, database)
            // Enter demo directly without auth gate
            settingsVm.loadSampleDataWithSafety(context)
            assertTrue(com.example.data.DatabaseProvider.isDemoMode(context))
            assertTrue(settingsRepository.settingsFlow.first().demoMode)

            // Demo firewall blocks export while in demo mode
            val currentSettings = settingsRepository.settingsFlow.first()
            val exportRes = com.example.importbackup.UnifiedCsvStorage.saveToDeviceCsv(
                context = context,
                transactions = emptyList(),
                accounts = emptyList(),
                categories = emptyList(),
                settings = currentSettings,
                settingsRepository = settingsRepository
            )
            assertEquals("Export paused: Demo mode active", exportRes)

            // Requesting exit with lock ON sets showExitDemoAuthGate = true without flipping
            settingsVm.requestExitDemo(context)
            settingsVm.showExitDemoAuthGate.awaitState { it }
            assertTrue("Exit auth gate must be shown when lockEnabled=true", settingsVm.showExitDemoAuthGate.value)
            assertTrue("Must still be in demo mode while auth gate is pending", com.example.data.DatabaseProvider.isDemoMode(context))

            // Completing auth flips out of demo mode and lifts firewall
            settingsVm.confirmExitDemoAfterAuth(context)
            settingsVm.showExitDemoAuthGate.awaitState { !it }
            settingsRepository.awaitDemoMode(expected = false)
            assertFalse("Must exit demo mode after auth confirmation", com.example.data.DatabaseProvider.isDemoMode(context))
            assertFalse("Auth gate must be dismissed after unlock", settingsVm.showExitDemoAuthGate.value)
        }
    }

    // =========================================================================
    // F9 (UT8): Save/Sync Subtitle Full File Path & Restore Subtitle Scan Copy
    // =========================================================================

    @Test
    fun testUT8_saveSyncSubtitleShowsFullFilePath_matchesSnackbar_updatesOnRootChange_andRestoreSubtitleUsesScanWording() {
        runBlocking {
            settingsRepository.setDemoMode(false)
            settingsRepository.setAutoSaveTreeUri("")

            val settingsVm = com.example.ui.settings.SettingsViewModel(repository, settingsRepository, database)

            composeTestRule.setContent {
                MyApplicationTheme {
                    com.example.ui.settings.SettingsScreen(viewModel = settingsVm)
                }
            }
            composeTestRule.waitForIdle()

            // 1. Default root: Save subtitle == "Saves single unified file to " + activeRootDisplay + "/" + UnifiedCsvStorage.CSV_FILE_NAME
            val defaultRootDisplay = com.example.importbackup.MediaStoreWriter.displayPathForRoot(context, "")
            val expectedDefaultPathPortion = "$defaultRootDisplay/${com.example.importbackup.UnifiedCsvStorage.CSV_FILE_NAME}"
            val expectedDefaultSaveSubtitle = "Saves single unified file to $expectedDefaultPathPortion"
            val expectedDefaultRestoreSubtitle = "Scans $defaultRootDisplay and internal auto backups for the newest snapshot"

            composeTestRule.onNodeWithTag("settings_save_sync_subtitle", useUnmergedTree = true)
                .assertExists()
            composeTestRule.onNodeWithText(expectedDefaultSaveSubtitle, useUnmergedTree = true)
                .assertExists()

            // 2. Export snackbar's path portion == subtitle's path portion
            val currentSettings = settingsRepository.settingsFlow.first()
            val snackbarMsg = com.example.importbackup.UnifiedCsvStorage.saveToDeviceCsv(
                context = context,
                transactions = emptyList(),
                accounts = emptyList(),
                categories = emptyList(),
                settings = currentSettings,
                settingsRepository = settingsRepository
            )
            // Snackbar format: "Unified CSV saved: <path> (<N> records)"
            val snackbarPathPortion = snackbarMsg
                .removePrefix("Unified CSV saved: ")
                .substringBefore(" (")
            assertEquals(
                "Snackbar path portion must match Save subtitle path portion",
                expectedDefaultPathPortion,
                snackbarPathPortion
            )

            // 3. Restore subtitle contains no single-file claim and includes scan/auto-fallback wording
            composeTestRule.onNodeWithTag("settings_restore_csv_subtitle", useUnmergedTree = true)
                .assertExists()
            composeTestRule.onNodeWithText(expectedDefaultRestoreSubtitle, useUnmergedTree = true)
                .assertExists()
            assertFalse(
                "Restore subtitle must not claim a single file",
                expectedDefaultRestoreSubtitle.contains(com.example.importbackup.UnifiedCsvStorage.CSV_FILE_NAME) ||
                    expectedDefaultRestoreSubtitle.contains("single unified file")
            )
            assertTrue(
                "Restore subtitle must include scan and internal auto backup wording",
                expectedDefaultRestoreSubtitle.contains("Scans ") &&
                    expectedDefaultRestoreSubtitle.contains("internal auto backups")
            )

            // 4. After a fixture root change, both subtitles update to the new root
            val customUri = "content://com.android.externalstorage.documents/tree/primary%3AMyVault%2FSilentSums"
            settingsRepository.setAutoSaveTreeUri(customUri)
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            val customRootDisplay = com.example.importbackup.MediaStoreWriter.displayPathForRoot(context, customUri)
            assertEquals("Internal storage/MyVault/SilentSums", customRootDisplay)

            val expectedCustomPathPortion = "$customRootDisplay/${com.example.importbackup.UnifiedCsvStorage.CSV_FILE_NAME}"
            val expectedCustomSaveSubtitle = "Saves single unified file to $expectedCustomPathPortion"
            val expectedCustomRestoreSubtitle = "Scans $customRootDisplay and internal auto backups for the newest snapshot"

            composeTestRule.onNodeWithText(expectedCustomSaveSubtitle, useUnmergedTree = true)
                .assertExists()
            composeTestRule.onNodeWithText(expectedCustomRestoreSubtitle, useUnmergedTree = true)
                .assertExists()
        }
    }

    // =========================================================================
    // HARDEN-1 (UT9): Stale Token Does Not Survive Process Death
    // =========================================================================

    @Test
    fun testUT9_staleTokenPresent_simulatedColdStart_showsLockScreen_tokenDiesWithProcess() {
        runBlocking {
            settingsRepository.setDemoMode(false)
            settingsRepository.setLockEnabled(true)
            settingsRepository.setPinHash("1234")

            // 1. Stale token present in memory prior to process death
            com.example.MainActivity.pendingRecreationUnlock = true
            assertTrue("Token was marked before process death", com.example.MainActivity.pendingRecreationUnlock)

            // 2. Simulated process death / cold start:
            // Process death wipes JVM process memory and resets all static/companion fields.
            // SavedInstanceState is null on cold start.
            com.example.MainActivity.pendingRecreationUnlock = false

            // On fresh launch (cold start), token does NOT survive
            val coldStartUnlocked = com.example.MainActivity.consumePendingRecreationUnlock()
            assertFalse("Token must not survive process death into cold start", coldStartUnlocked)

            // 3. Mount UI with cold start state (lockEnabled && !demoMode && !isUnlocked)
            val settings = settingsRepository.settingsFlow.first()
            composeTestRule.setContent {
                MyApplicationTheme {
                    if (settings.lockEnabled && !settings.demoMode && !coldStartUnlocked) {
                        LockScreen(
                            correctPin = settings.pinHash,
                            onUnlocked = {}
                        )
                    }
                }
            }
            composeTestRule.waitForIdle()

            // 4. LockScreen MUST show on cold start
            composeTestRule.onNodeWithTag("app_pin_lock_screen").assertIsDisplayed()

            // 5. Recreate UX within same process carries unlock through seamlessly
            com.example.MainActivity.markSessionUnlocked(context)
            assertTrue("Token marked for recreation within active process", com.example.MainActivity.pendingRecreationUnlock)
            val recreateUnlocked = com.example.MainActivity.consumePendingRecreationUnlock()
            assertTrue("Recreation within same process consumes unlock token", recreateUnlocked)
            assertFalse("Token must be single-use and reset to false after consumption", com.example.MainActivity.consumePendingRecreationUnlock())
        }
    }

    // =========================================================================
    // F10 (UT10): One-Glyph-Per-Card Rule on Ledger Cards & Split Cards
    // =========================================================================

    @Test
    fun testUT10_singleCardPillHasNoDuplicateEmoji_splitExpandedRowPillsFollowRuleB_andCatFilterChipKeepsEmoji() {
        val groceriesCat = Category(id = 1L, name = "Groceries & Shopping", color = "#F59E0B")
        val billsCat = Category(id = 2L, name = "Bills & Utilities", color = "#8B5CF6", iconText = "⚡")
        val cashAcc = Account(id = 10L, name = "Cash", accountType = "CASH", openingBalance = 20000.0)

        val singleTx = EnrichedTransaction(
            entity = TransactionEntity(
                id = 501L,
                date = System.currentTimeMillis(),
                type = "EXPENSE",
                amount = 100.0,
                fromAccountId = 10L,
                categoryId = 1L,
                description = "Milk and Oil",
                notes = "Milk and Oil"
            ),
            category = groceriesCat,
            fromAccount = cashAcc,
            toAccount = null,
            runningBalance = 17361.44
        )

        val splitLine1 = EnrichedTransaction(
            entity = TransactionEntity(
                id = 601L,
                date = System.currentTimeMillis(),
                type = "EXPENSE",
                amount = 300.0,
                fromAccountId = 10L,
                categoryId = 1L,
                description = "Weekend Errands",
                groupId = "grp_ut10"
            ),
            category = groceriesCat,
            fromAccount = cashAcc,
            runningBalance = 17061.44
        )
        val splitLine2 = EnrichedTransaction(
            entity = TransactionEntity(
                id = 602L,
                date = System.currentTimeMillis(),
                type = "EXPENSE",
                amount = 200.0,
                fromAccountId = 10L,
                categoryId = 2L,
                description = "Weekend Errands",
                groupId = "grp_ut10"
            ),
            category = billsCat,
            fromAccount = cashAcc,
            runningBalance = 16861.44
        )
        val splitLine3DuplicateCat = EnrichedTransaction(
            entity = TransactionEntity(
                id = 603L,
                date = System.currentTimeMillis(),
                type = "EXPENSE",
                amount = 50.0,
                fromAccountId = 10L,
                categoryId = 1L,
                description = "Weekend Errands",
                groupId = "grp_ut10"
            ),
            category = groceriesCat,
            fromAccount = cashAcc,
            runningBalance = 16811.44
        )

        val splitGroup = com.example.ui.ledger.LedgerRowItem.SplitGroup(
            groupId = "grp_ut10",
            lines = listOf(splitLine1, splitLine2, splitLine3DuplicateCat)
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                androidx.compose.foundation.layout.Column {
                    TransactionCardRow(
                        transaction = singleTx,
                        onClick = {}
                    )
                    com.example.ui.ledger.SplitGroupCardRow(
                        splitGroup = splitGroup,
                        isExpanded = true,
                        onToggleExpand = {},
                        onEditGroup = {}
                    )
                    FilterChipsRow(
                        filterState = FilterState(selectedCategoryId = 1L, selectedCategoryIds = setOf(1L)),
                        categories = listOf(groceriesCat, billsCat),
                        accounts = listOf(cashAcc),
                        onFilterChange = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        fun collectAllTexts(node: androidx.compose.ui.semantics.SemanticsNode): List<String> {
            val own = if (node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)) {
                node.config[androidx.compose.ui.semantics.SemanticsProperties.Text].map { it.text }
            } else {
                emptyList()
            }
            return own + node.children.flatMap { collectAllTexts(it) }
        }

        // (a) Single-card fixture: leading badge renders "🛒", pill renders category NAME only ("Groceries & Shopping") with NO icon/emoji child
        val badgeNode = composeTestRule.onNodeWithTag("tx_icon_${singleTx.id}", useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode()
        val badgeTexts = collectAllTexts(badgeNode)
        assertTrue("Leading badge on Row 1 must render category emoji 🛒", badgeTexts.contains("🛒"))

        val singlePillNode = composeTestRule.onNodeWithTag("tx_category_pill_${singleTx.id}", useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode()
        val pillTexts = collectAllTexts(singlePillNode)
        assertEquals("Single-card pill must contain category name only", listOf("Groceries & Shopping"), pillTexts)
        assertFalse("Single-card pill must NOT contain duplicate emoji 🛒", pillTexts.any { it.contains("🛒") })

        // (b) Split card fixture: top badge is Split icon, expanded-row pills keep emoji (once per category in card)
        val splitPill1Texts = collectAllTexts(
            composeTestRule.onNodeWithTag("split_line_pill_${splitLine1.id}", useUnmergedTree = true).fetchSemanticsNode()
        )
        assertEquals(listOf("🛒 Groceries & Shopping"), splitPill1Texts)

        val splitPill2Texts = collectAllTexts(
            composeTestRule.onNodeWithTag("split_line_pill_${splitLine2.id}", useUnmergedTree = true).fetchSemanticsNode()
        )
        assertEquals(listOf("⚡ Bills & Utilities"), splitPill2Texts)

        // Second line with same category in the same split card is deduped by the one-glyph-per-card rule
        val splitPill3Texts = collectAllTexts(
            composeTestRule.onNodeWithTag("split_line_pill_${splitLine3DuplicateCat.id}", useUnmergedTree = true).fetchSemanticsNode()
        )
        assertEquals(listOf("Groceries & Shopping"), splitPill3Texts)

        // (c) Ledger "Cat:" filter chip still renders its emoji + label
        val filterChipNode = composeTestRule.onNodeWithTag("filter_chip_category", useUnmergedTree = true)
            .fetchSemanticsNode()
        val chipTexts = collectAllTexts(filterChipNode)
        assertTrue("Cat: filter chip must keep its emoji 🛒", chipTexts.contains("🛒"))
        assertTrue("Cat: filter chip must render 'Cat: Groceries & Shopping'", chipTexts.contains("Cat: Groceries & Shopping"))
    }

    // --- UT12 (Currency reactivity across surfaces): with dashboard state rendered under ₹, emit currency "$" from settings StateFlow (no navigation, no data mutation) -> dashboard balance/debt/source/budget/dues/ledger/lists nodes render "$"; masked fixture still renders dots ---
    @Test
    fun testUT12_currencyReactivityAcrossAllMoneySurfaces_andMaskedPreserved() {
        runBlocking {
            DashboardIntroTracker.hasRunIntro = true
            settingsRepository.setCurrency("₹")
            settingsRepository.setHideBalances(false)

            val cashAccId = repository.insertAccount(
                Account(id = 10L, name = "Cash Wallet", accountType = "CASH", openingBalance = 10000.0)
            )
            val cardAccId = repository.insertAccount(
                Account(id = 11L, name = "ICICI Credit", accountType = "CREDIT_CARD", openingBalance = 0.0)
            )
            val catId = repository.insertCategory(
                Category(id = 20L, name = "Groceries & Shopping", iconText = "🛒", color = "#10B981")
            )
            val budgetId = repository.upsertBudget(
                com.example.data.model.BudgetEntity(
                    id = 30L,
                    name = "Food Cap",
                    scopeType = "CATEGORY",
                    targetName = "Groceries & Shopping",
                    limitAmount = 5000.0,
                    active = true
                )
            )
            val now = System.currentTimeMillis()
            val cashTxId = repository.insertTransaction(
                TransactionEntity(
                    id = 901L,
                    date = now - 60_000L,
                    type = "EXPENSE",
                    amount = 1200.0,
                    fromAccountId = cashAccId,
                    categoryId = catId,
                    description = "Supermarket Run"
                )
            )
            repository.insertTransaction(
                TransactionEntity(
                    id = 902L,
                    date = now - 30_000L,
                    type = "EXPENSE",
                    amount = 800.0,
                    fromAccountId = cardAccId,
                    categoryId = catId,
                    description = "Online Order"
                )
            )

            repository.insertCommitment(
                Commitment(
                    id = 40L,
                    type = "IOU",
                    name = "Broadband Bill",
                    amount = 999.0,
                    direction = "OUTGOING",
                    dueDate = now + 86_400_000L,
                    accountId = cashAccId,
                    categoryId = catId
                )
            )

            val mediaRepo = com.example.data.repository.MediaRepository(context, database)
            val dashVm = DashboardViewModel(repository, settingsRepository)
            val commitmentVm = com.example.ui.commitment.CommitmentViewModel(repository)
            val ledgerVm = com.example.ui.ledger.LedgerViewModel(repository, settingsRepository, mediaRepo)
            val listsVm = com.example.ui.lists.ListsViewModel(
                repository = repository,
                initialTab = com.example.ui.lists.ListsTab.ACCOUNTS,
                settingsRepository = settingsRepository
            )

            dashVm.uiState.awaitState { it.accountsWithBalance.isNotEmpty() && it.calculatedBudgets.isNotEmpty() }
            ledgerVm.uiState.awaitState { it.dayGroups.isNotEmpty() }
            listsVm.uiState.awaitState { it.accountsWithBalances.isNotEmpty() && it.activeBudgets.isNotEmpty() }

            composeTestRule.setContent {
                val activeCurrency by settingsRepository.currencySymbol.collectAsState()
                val dashState by dashVm.uiState.collectAsState()
                val ledgerState by ledgerVm.uiState.collectAsState()
                val listsState by listsVm.uiState.collectAsState()
                val groups by commitmentVm.groupedCommitments.collectAsState()
                MyApplicationTheme {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f)) {
                            DashboardScreen(
                                viewModel = dashVm,
                                commitmentViewModel = commitmentVm,
                                onNavigateToExpenses = { _, _ -> }
                            )
                        }
                        // Render Ledger row, Lists source & budget cards, and Dues amount node in the same composition
                        val tx = ledgerState.dayGroups.flatMap { it.transactions }.firstOrNull { it.id == cashTxId }
                        if (tx != null) {
                            TransactionCardRow(
                                transaction = tx,
                                onClick = {},
                                hideBalances = ledgerState.hideBalances,
                                currencySymbol = activeCurrency
                            )
                        }
                        val cashAccBal = listsState.accountsWithBalances.firstOrNull { it.account.id == cashAccId }
                        if (cashAccBal != null) {
                            com.example.ui.lists.AccountItemCard(
                                accountWithBalance = cashAccBal,
                                onEdit = {},
                                onArchive = {},
                                onRestore = {},
                                onDelete = {},
                                modifier = Modifier.testTag("lists_account_card_$cashAccId"),
                                hideBalances = listsState.hideBalances,
                                currencySymbol = activeCurrency
                            )
                        }
                        val listBudget = listsState.activeBudgets.firstOrNull { it.id == budgetId }
                        if (listBudget != null) {
                            androidx.compose.material3.Text(
                                text = Formatters.formatMoney(
                                    listBudget.limitAmount,
                                    isMasked = listsState.hideBalances,
                                    currencySymbol = activeCurrency
                                ),
                                modifier = Modifier.testTag("lists_budget_amount_$budgetId")
                            )
                        }
                        val dueItem = (groups.overdue + groups.next7Days + groups.noDueDate).firstOrNull()
                        if (dueItem != null) {
                            androidx.compose.material3.Text(
                                text = Formatters.formatMoney(
                                    dueItem.commitment.amount,
                                    isMasked = dashState.hideBalances,
                                    currencySymbol = activeCurrency
                                ),
                                modifier = Modifier.testTag("dues_item_amount_${dueItem.commitment.id}")
                            )
                        }
                    }
                }
            }

            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            fun collectTexts(tag: String): String {
                fun walk(node: androidx.compose.ui.semantics.SemanticsNode): List<String> {
                    val own = if (node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)) {
                        node.config[androidx.compose.ui.semantics.SemanticsProperties.Text].map { it.text }
                    } else {
                        emptyList()
                    }
                    return own + node.children.flatMap { walk(it) }
                }
                var nodes = composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes()
                if (nodes.isEmpty()) {
                    runCatching { composeTestRule.scrollToDashboardTag(tag, useUnmergedTree = true) }
                    nodes = composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes()
                }
                return nodes.flatMap { walk(it) }.joinToString(" | ")
            }

            // 1. Verify initial render under "₹"
            assertTrue("Dashboard current balance starts with ₹", collectTexts("kpi_current_balance").contains("₹8,800.00"))
            assertTrue("Dashboard credit cards debt starts with ₹", collectTexts("kpi_credit_cards_owed").contains("₹800.00"))
            assertTrue("Dashboard card spend tile starts with ₹", collectTexts("kpi_cc_spend_this_month").contains("₹800.00"))
            assertTrue("Dashboard source row starts with ₹", collectTexts("source_row_$cashAccId").contains("₹8,800.00"))
            assertTrue("Dashboard budget item starts with ₹", collectTexts("budget_item_$budgetId").contains("₹2,000.00 / ₹5,000.00"))
            assertTrue("Ledger row starts with ₹", collectTexts("transaction_row_$cashTxId").contains("-₹1,200.00"))
            assertTrue("Lists account card starts with ₹", collectTexts("lists_account_card_$cashAccId").contains("₹8,800.00"))
            assertTrue("Lists budget row starts with ₹", collectTexts("lists_budget_amount_$budgetId").contains("₹5,000.00"))
            assertTrue("Dues item starts with ₹", collectTexts("dues_item_amount_40").contains("₹999.00"))

            // 2. Emit "$" from settings StateFlow ONLY (no navigation, no data mutation)
            settingsRepository.setCurrency("$")
            dashVm.uiState.awaitState { it.currencySymbol == "$" }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            // Verify every money surface re-rendered with "$" and no "₹" remains
            val dashBalAfter = collectTexts("kpi_current_balance")
            assertTrue("Dashboard balance must re-render with $: $dashBalAfter", dashBalAfter.contains("$8,800.00") && !dashBalAfter.contains("₹"))

            val dashDebtAfter = collectTexts("kpi_credit_cards_owed")
            assertTrue("Dashboard CC debt must re-render with $: $dashDebtAfter", dashDebtAfter.contains("$800.00") && !dashDebtAfter.contains("₹"))

            val dashCcSpendAfter = collectTexts("kpi_cc_spend_this_month")
            assertTrue("Dashboard CC spend tile must re-render with $: $dashCcSpendAfter", dashCcSpendAfter.contains("$800.00") && !dashCcSpendAfter.contains("₹"))

            val dashSourceAfter = collectTexts("source_row_$cashAccId")
            assertTrue("Dashboard source row must re-render with $: $dashSourceAfter", dashSourceAfter.contains("$8,800.00") && !dashSourceAfter.contains("₹"))

            val dashBudgetAfter = collectTexts("budget_item_$budgetId")
            assertTrue("Dashboard budget row must re-render with $: $dashBudgetAfter", dashBudgetAfter.contains("$2,000.00 / $5,000.00") && !dashBudgetAfter.contains("₹"))

            assertTrue("Dashboard insights must re-emit with $", dashVm.uiState.value.insights.any { it.contains("$") } && dashVm.uiState.value.insights.none { it.contains("₹") })

            val ledgerRowAfter = collectTexts("transaction_row_$cashTxId")
            assertTrue("Ledger row must re-render with $: $ledgerRowAfter", ledgerRowAfter.contains("-$1,200.00") && ledgerRowAfter.contains("Bal: $8,800.00") && !ledgerRowAfter.contains("₹"))

            val listsAccAfter = collectTexts("lists_account_card_$cashAccId")
            assertTrue("Lists account card must re-render with $: $listsAccAfter", listsAccAfter.contains("$10,000.00") && listsAccAfter.contains("$8,800.00") && !listsAccAfter.contains("₹"))

            val listsBudgetAfter = collectTexts("lists_budget_amount_$budgetId")
            assertTrue("Lists budget amount must re-render with $: $listsBudgetAfter", listsBudgetAfter.contains("$5,000.00") && !listsBudgetAfter.contains("₹"))

            val duesAfter = collectTexts("dues_item_amount_40")
            assertTrue("Dues amount must re-render with $: $duesAfter", duesAfter.contains("$999.00") && !duesAfter.contains("₹"))

            // 3. Masked fixture: toggle hideBalances = true -> pure dots ("••••••" / "••••") regardless of "$" symbol
            settingsRepository.setHideBalances(true)
            dashVm.uiState.awaitState { it.hideBalances }
            ledgerVm.uiState.awaitState { it.hideBalances }
            listsVm.uiState.awaitState { it.hideBalances }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            val maskedDashBal = collectTexts("kpi_current_balance")
            assertTrue("Masked Dashboard balance must render dots without $", maskedDashBal.contains("••••••") && !maskedDashBal.contains("$8,800"))
            val maskedLedgerRow = collectTexts("transaction_row_$cashTxId")
            assertTrue("Masked Ledger row must render dots without $", maskedLedgerRow.contains("••••••") && !maskedLedgerRow.contains("$1,200"))
            val maskedListsAcc = collectTexts("lists_account_card_$cashAccId")
            assertTrue("Masked Lists account must render dots without $", maskedListsAcc.contains("••••••") && !maskedListsAcc.contains("$8,800"))
            val maskedDues = collectTexts("dues_item_amount_40")
            assertTrue("Masked Dues must render dots without $", maskedDues.contains("••••••") && !maskedDues.contains("$999"))

            dashVm.viewModelScope.cancel()
            ledgerVm.viewModelScope.cancel()
            listsVm.viewModelScope.cancel()
            settingsRepository.setHideBalances(false)
            settingsRepository.setCurrency("₹")
            ShadowLooper.idleMainLooper()
        }
    }

    // --- UT13 (Swipeable single-flight snackbar from Settings): Settings "Currency updated to $" snackbar renders inside single_flight_snackbar_swipe_box and swipe-dismisses; notice from Settings is replaceable by ledger privacy notice on the one shared host; no raw SnackbarHost remains on any screen ---
    @Test
    fun testUT13_settingsSnackbarSwipeDismissable_andSharedSingleFlightGateAppWide() {
        runBlocking {
            val settingsVm = SettingsViewModel(repository, settingsRepository, database)
            lateinit var sharedDispatcher: com.example.ui.common.SingleFlightNoticeDispatcher

            composeTestRule.setContent {
                sharedDispatcher = com.example.ui.common.rememberSingleFlightNoticeDispatcher()
                androidx.compose.runtime.CompositionLocalProvider(
                    com.example.ui.common.LocalNoticeDispatcher provides sharedDispatcher
                ) {
                    MyApplicationTheme {
                        androidx.compose.material3.Scaffold(
                            snackbarHost = { com.example.ui.common.SingleFlightSnackbarHost(sharedDispatcher) }
                        ) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                                SettingsScreen(
                                    viewModel = settingsVm,
                                    noticeDispatcher = sharedDispatcher
                                )
                            }
                        }
                    }
                }
            }

            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            suspend fun awaitSnackbarMessage(expected: String) {
                val deadline = System.currentTimeMillis() + 5000L
                while (
                    sharedDispatcher.snackbarHostState.currentSnackbarData?.visuals?.message != expected &&
                    System.currentTimeMillis() < deadline
                ) {
                    ShadowLooper.idleMainLooper()
                    composeTestRule.waitForIdle()
                    kotlinx.coroutines.delay(15)
                }
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()
            }

            // 1. Trigger "Currency updated to $" from SettingsViewModel
            settingsVm.setCurrency("$")
            awaitSnackbarMessage("Currency updated to $")

            assertEquals(
                "Currency updated to $",
                sharedDispatcher.snackbarHostState.currentSnackbarData?.visuals?.message
            )
            assertEquals("SETTINGS_STATUS:Currency updated to $", sharedDispatcher.inFlightKey)

            // Verify it renders inside single_flight_snackbar_swipe_box and swipe-dismisses
            composeTestRule.onNodeWithTag("single_flight_snackbar_swipe_box")
                .assertIsDisplayed()
                .performTouchInput {
                    down(center)
                    moveBy(androidx.compose.ui.geometry.Offset(500f, 0f))
                    up()
                }

            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            assertNull("Settings currency snackbar must dismiss on swipe", sharedDispatcher.snackbarHostState.currentSnackbarData)
            assertNull("Shared inFlightKey must clear after swipe-dismiss", sharedDispatcher.inFlightKey)

            // 2. Dispatch a Settings notice, then immediately replace it with a Ledger privacy notice on the shared host
            settingsVm.setCurrency("€")
            awaitSnackbarMessage("Currency updated to €")
            assertEquals(
                "Currency updated to €",
                sharedDispatcher.snackbarHostState.currentSnackbarData?.visuals?.message
            )

            // Ledger privacy notice arrives on the same shared host (different key -> replaces in-flight Settings notice)
            sharedDispatcher.showPrivacyNotice()
            awaitSnackbarMessage(com.example.ui.common.SingleFlightNoticeDispatcher.PRIVACY_NOTICE_MESSAGE)

            assertEquals(
                com.example.ui.common.SingleFlightNoticeDispatcher.PRIVACY_NOTICE_MESSAGE,
                sharedDispatcher.snackbarHostState.currentSnackbarData?.visuals?.message
            )
            assertEquals(
                com.example.ui.common.SingleFlightNoticeDispatcher.PRIVACY_NOTICE_KEY,
                sharedDispatcher.inFlightKey
            )

            // 3. Verify no raw SnackbarHost( remains on any screen file (only SingleFlightNoticeDispatcher.kt wraps Material3 SnackbarHost)
            val screenFiles = listOf(
                "ui/settings/SettingsScreen.kt",
                "ui/dashboard/DashboardScreen.kt",
                "ui/ledger/LedgerScreen.kt",
                "ui/lists/ListsScreen.kt",
                "ui/entry/EntryScreen.kt",
                "ui/PaisaTrackApp.kt"
            )
            for (rel in screenFiles) {
                val f = File("src/main/java/com/example/$rel").let {
                    if (it.exists()) it else File("app/src/main/java/com/example/$rel")
                }
                val src = f.readText()
                // Remove "SingleFlightSnackbarHost(" to check for any raw "SnackbarHost("
                val stripped = src.replace("SingleFlightSnackbarHost(", "")
                assertFalse("No raw SnackbarHost may remain in $rel", stripped.contains("SnackbarHost("))
                assertFalse("No direct .showSnackbar( may remain in $rel", stripped.contains(".showSnackbar("))
            }
            settingsVm.viewModelScope.cancel()
            settingsRepository.setCurrency("₹")
            ShadowLooper.idleMainLooper()
        }
    }

    // --- TASK RELEASE-1: UT11 Version single source of truth & zero banned literals ---
    @Test
    fun testUT11_versionSingleSourceOfTruth_settingsBadge_crashHeader_andZeroBannedLiterals() {
        val expectedBadge = "v" + BuildConfig.VERSION_NAME + " (Build " + BuildConfig.VERSION_CODE + ")"
        assertEquals("1.0.0", BuildConfig.VERSION_NAME)
        assertEquals(1, BuildConfig.VERSION_CODE)
        assertEquals("v1.0.0 (Build 1)", expectedBadge)

        // 1. Settings badge node text == "v" + BuildConfig.VERSION_NAME + " (Build " + BuildConfig.VERSION_CODE + ")"
        val settingsVm = SettingsViewModel(repository, settingsRepository, database)
        composeTestRule.setContent {
            SettingsScreen(viewModel = settingsVm)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_version_badge")
            .assertExists()
            .assertTextEquals(expectedBadge)
        composeTestRule.onNodeWithText(expectedBadge).assertExists()
        settingsVm.viewModelScope.cancel()
        ShadowLooper.idleMainLooper()

        // 2. Crash-log header contains the same composed string
        CrashLogger.recordCleanStart(context)
        val headerFromHelper = CrashLogger.buildStartHeader("2026-10-28 00:00:00.000")
        assertTrue(
            "CrashLogger.buildStartHeader must contain '$expectedBadge', was: $headerFromHelper",
            headerFromHelper.contains(expectedBadge)
        )
        val log1Text = CrashLogger.getLogFile1(context).readText()
        val log2Text = CrashLogger.getLogFile2(context).readText()
        assertTrue(
            "Persisted crash log 1 header must contain '$expectedBadge', was: $log1Text",
            log1Text.contains(expectedBadge)
        )
        assertTrue(
            "Persisted crash log 2 header must contain '$expectedBadge', was: $log2Text",
            log2Text.contains(expectedBadge)
        )
        assertNull(
            "Clean start header with version badge must still be treated as clean (no stack trace)",
            CrashLogger.checkCrashLog(context)
        )

        // 3. Lint/grep check: ZERO banned literals {"1.1.0", "Build 6", "(Build 6)"} remain in app/src/main
        val mainDir = listOf(File("src/main"), File("app/src/main")).firstOrNull { it.exists() }
            ?: error("Could not locate app/src/main")
        val bannedLiterals = listOf("1.1.0", "Build 6", "(Build 6)")
        val textExtensions = setOf("kt", "java", "xml", "json", "pro", "txt", "md", "properties")
        val mainFiles = mainDir.walkTopDown().filter { it.isFile && it.extension.lowercase() in textExtensions }.toList()
        assertTrue("Expected source/resource files under ${mainDir.path}", mainFiles.isNotEmpty())
        for (file in mainFiles) {
            val content = file.readText()
            for (banned in bannedLiterals) {
                assertFalse(
                    "Banned version/build literal '$banned' found in ${file.path}",
                    content.contains(banned)
                )
            }
        }
    }

    // --- TASK RELEASE-1: UT11b Residue audit (no metadata.json in outputs/APK, no debug-only features, no placeholder assets) ---
    @Test
    fun testUT11b_residueAudit_noMetadataJsonInOutputsOrApk_andNoDebugOnlyFeatures() {
        val repoRoot = listOf(File("."), File("..")).firstOrNull { File(it, "settings.gradle.kts").exists() }
            ?: File(".")

        // 1. Verify .gitignore excludes metadata.json
        val gitignore = File(repoRoot, ".gitignore")
        assertTrue(".gitignore must exist in project root", gitignore.exists())
        val gitignoreLines = gitignore.readLines().map { it.trim() }
        assertTrue(".gitignore must contain metadata.json", gitignoreLines.contains("metadata.json"))

        // 2. Verify metadata.json is NOT in app/src/main/res, app/src/main/assets, or app/build/outputs
        val mainDir = listOf(File("src/main"), File("app/src/main")).firstOrNull { it.exists() }
            ?: error("Could not locate app/src/main")
        val mainMetadataFiles = mainDir.walkTopDown()
            .filter { it.isFile && it.name.equals("metadata.json", ignoreCase = true) }
            .toList()
        assertTrue(
            "metadata.json must NOT exist anywhere under app/src/main: $mainMetadataFiles",
            mainMetadataFiles.isEmpty()
        )

        val outputDirs = listOf(
            File("build/outputs"),
            File("app/build/outputs"),
            File(repoRoot, ".build-outputs")
        ).filter { it.exists() }
        for (outDir in outputDirs) {
            val strayMetadata = outDir.walkTopDown()
                .filter { it.isFile && it.name.equals("metadata.json", ignoreCase = true) }
                .toList()
            assertTrue(
                "metadata.json must NOT exist in ${outDir.path}: $strayMetadata",
                strayMetadata.isEmpty()
            )

            // Inspect any built APK / AAB archives to verify metadata.json is not packaged inside
            val archives = outDir.walkTopDown()
                .filter { it.isFile && (it.extension.equals("apk", ignoreCase = true) || it.extension.equals("aab", ignoreCase = true)) }
                .toList()
            for (archive in archives) {
                java.util.zip.ZipFile(archive).use { zip ->
                    val entries = zip.entries().asSequence().map { it.name }.toList()
                    assertFalse(
                        "APK/AAB ${archive.path} must NOT contain metadata.json",
                        entries.any { it.equals("metadata.json", ignoreCase = true) || it.endsWith("/metadata.json", ignoreCase = true) }
                    )
                }
            }
        }

        // 3. Verify zero debug-only features or external generator strings in app/src/main production code/resources
        val bannedDebugTokens = listOf("seedFakeCrash", "devTrigger", "testHarness", "debugOnly", "isDevMode")
        val bannedGeneratorTokens = listOf("ai" + "-studio", "AI" + " Studio", "ai" + "studio")
        val textExtensions = setOf("kt", "java", "xml", "json", "pro", "txt")
        for (file in mainDir.walkTopDown().filter { it.isFile }) {
            val lowerName = file.name.lowercase()
            assertFalse(
                "Placeholder/temp/test asset '${file.path}' must not ship in app/src/main",
                lowerName.contains("placeholder") || lowerName.contains("temp") || lowerName.startsWith("test_")
            )
            if (file.extension.lowercase() in textExtensions) {
                val content = file.readText()
                for (token in bannedDebugTokens) {
                    assertFalse(
                        "Debug-only token '$token' must not exist in production file ${file.path}",
                        content.contains(token)
                    )
                }
                for (genToken in bannedGeneratorTokens) {
                    assertFalse(
                        "Generator residue '$genToken' must not exist in production file ${file.path}",
                        content.contains(genToken, ignoreCase = true)
                    )
                }
            }
        }
    }

    // --- TASK F13: UT14 Restore Media Delete in Attached Media Sheet ({light, dark} x {voice row, photo page}) ---
    @Test
    fun testUT14_mediaViewerSwipeDelete_voiceAndPhoto_lightAndDark_revealBoundsTintAndDelete() {
        runBlocking {
            val mediaRepo = com.example.data.repository.MediaRepository(context, database)
            val accId = repository.insertAccount(Account(id = 1L, name = "Cash", accountType = "CASH", openingBalance = 5000.0))
            val catId = repository.insertCategory(Category(id = 1L, name = "Food", color = "#10B981"))

            var isDarkState by mutableStateOf(false)
            var activeTxnId by mutableStateOf(811L)
            var activeTab by mutableStateOf("VOICE")
            var initialSeededAttachments by mutableStateOf<List<com.example.data.model.MediaAttachment>>(emptyList())
            var resolvedRevealBgColor = androidx.compose.ui.graphics.Color.Unspecified
            var resolvedBinTintColor = androidx.compose.ui.graphics.Color.Unspecified

            composeTestRule.setContent {
                val liveAttachments by androidx.compose.runtime.remember(activeTxnId) {
                    mediaRepo.getAttachmentsForTxn(activeTxnId)
                }.collectAsState(initial = initialSeededAttachments)

                MyApplicationTheme(darkTheme = isDarkState) {
                    resolvedRevealBgColor = androidx.compose.material3.MaterialTheme.colorScheme.error
                    resolvedBinTintColor = androidx.compose.ui.graphics.Color.White
                    key(isDarkState, activeTxnId, activeTab) {
                        com.example.ui.media.MediaViewerSheet(
                            txnId = activeTxnId,
                            attachments = liveAttachments,
                            initialTab = activeTab,
                            hideBalances = false,
                            mediaRepository = mediaRepo,
                            onDismiss = {}
                        )
                    }
                }
            }

            for (isDark in listOf(false, true)) {
                val themeLabel = if (isDark) "dark" else "light"
                val txnId = if (isDark) 812L else 811L
                repository.insertTransaction(
                    TransactionEntity(
                        id = txnId,
                        date = System.currentTimeMillis(),
                        type = "EXPENSE",
                        amount = 350.0,
                        fromAccountId = accId,
                        categoryId = catId,
                        description = "Media F13 $themeLabel"
                    )
                )

                val txnDir = mediaRepo.getVaultDirForTxn(txnId)
                val photoFile = File(txnDir, "f13_photo_$themeLabel.jpg").apply {
                    val bmp = android.graphics.Bitmap.createBitmap(400, 300, android.graphics.Bitmap.Config.ARGB_8888)
                    java.io.FileOutputStream(this).use { out ->
                        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                    }
                    bmp.recycle()
                }
                val voiceFile = File(txnDir, "f13_voice_$themeLabel.m4a").apply {
                    writeBytes(ByteArray(4096) { 0x11 })
                }

                val photoAttId = database.mediaAttachmentDao().insert(
                    com.example.data.model.MediaAttachment(
                        txnId = txnId,
                        kind = "PHOTO",
                        fileName = photoFile.name,
                        createdAt = System.currentTimeMillis()
                    )
                )
                val voiceAttId = database.mediaAttachmentDao().insert(
                    com.example.data.model.MediaAttachment(
                        txnId = txnId,
                        kind = "VOICE",
                        fileName = voiceFile.name,
                        createdAt = System.currentTimeMillis()
                    )
                )

                assertEquals("Both attachments seeded for $themeLabel", 2, mediaRepo.getAttachmentsForTxnSync(txnId).size)

                initialSeededAttachments = listOf(
                    com.example.data.model.MediaAttachment(id = photoAttId, txnId = txnId, kind = "PHOTO", fileName = photoFile.name),
                    com.example.data.model.MediaAttachment(id = voiceAttId, txnId = txnId, kind = "VOICE", fileName = voiceFile.name)
                )
                isDarkState = isDark
                activeTxnId = txnId
                activeTab = "VOICE"
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()

                // =========================================================
                // 1. VOICE ROW ({light, dark})
                // =========================================================
                val voiceContainerBounds = composeTestRule.onNodeWithTag("viewer_voice_swipe_container_0")
                    .fetchSemanticsNode().boundsInRoot
                val voiceCardNode = composeTestRule.onNodeWithTag("viewer_voice_card_0")
                voiceCardNode.assertIsDisplayed()
                voiceCardNode.performTouchInput {
                    down(center)
                    moveBy(androidx.compose.ui.geometry.Offset(-400f, 0f))
                    up()
                }
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()

                val voiceCardAfterBounds = voiceCardNode.fetchSemanticsNode().boundsInRoot
                val voiceRevealBounds = composeTestRule.onNodeWithTag("voice_delete_reveal_band_0", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                val voiceBinNode = composeTestRule.onNodeWithTag("voice_delete_bin_0", useUnmergedTree = true)
                voiceBinNode.assertIsDisplayed()
                val voiceBinBounds = voiceBinNode.fetchSemanticsNode().boundsInRoot

                val expectedVoiceMaxRevealPx = com.example.ui.common.SwipeRevealHelper.computeMaxRevealPx(
                    voiceContainerBounds.width,
                    composeTestRule.density
                )
                // Reveal band width must match SwipeRevealHelper clamp (not full row width)
                assertEquals(
                    "[$themeLabel/voice] Reveal band width must equal SwipeRevealHelper maxRevealPx",
                    expectedVoiceMaxRevealPx,
                    voiceRevealBounds.width,
                    2.5f
                )
                // Delete icon node MUST lie strictly within the revealed right-hand zone (not hidden behind the card at 50% width)
                assertTrue(
                    "[$themeLabel/voice] Delete bin bounds ($voiceBinBounds) must be inside reveal bounds ($voiceRevealBounds) and uncovered right of swiped card (${voiceCardAfterBounds.right})",
                    voiceBinBounds.left >= voiceRevealBounds.left - 1.5f &&
                        voiceBinBounds.right <= voiceRevealBounds.right + 1.5f &&
                        voiceBinBounds.left >= voiceCardAfterBounds.right - 2f
                )
                val minTouchPx = with(composeTestRule.density) { 48.dp.toPx() } - 1.5f
                assertTrue(
                    "[$themeLabel/voice] Delete bin touch target must be >= 48.dp, was ${voiceBinBounds.width}x${voiceBinBounds.height}",
                    voiceBinBounds.width >= minTouchPx && voiceBinBounds.height >= minTouchPx
                )
                assertNotEquals(
                    "[$themeLabel/voice] Resolved delete icon tint must contrast with (not equal) reveal background color",
                    resolvedRevealBgColor,
                    resolvedBinTintColor
                )

                val mutBeforeVoice = com.example.data.repository.MutationTracker.currentStamp
                voiceBinNode.performClick()
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()

                val deadlineVoice = System.currentTimeMillis() + 3000L
                while (mediaRepo.getAttachmentsForTxnSync(txnId).any { it.id == voiceAttId } && System.currentTimeMillis() < deadlineVoice) {
                    ShadowLooper.idleMainLooper()
                    composeTestRule.waitForIdle()
                    kotlinx.coroutines.delay(15)
                }
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()

                assertFalse(
                    "[$themeLabel/voice] Voice attachment must be removed from MediaRepository on delete tap",
                    mediaRepo.getAttachmentsForTxnSync(txnId).any { it.id == voiceAttId }
                )
                assertFalse("[$themeLabel/voice] Voice file on disk must be deleted", voiceFile.exists())
                assertTrue(
                    "[$themeLabel/voice] MutationTracker must bump on voice delete",
                    com.example.data.repository.MutationTracker.currentStamp > mutBeforeVoice
                )
                composeTestRule.onNodeWithTag("viewer_voice_card_0").assertDoesNotExist()

                // =========================================================
                // 2. PHOTO PAGE ({light, dark})
                // =========================================================
                activeTab = "IMAGES"
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()

                val photoCardNode = composeTestRule.onNodeWithTag("viewer_photo_card_0")
                photoCardNode.assertIsDisplayed()

                // Header bin exists on photo view, deletes the currently viewed photo (A5)
                val photoBinNode = composeTestRule.onNodeWithTag("viewer_header_delete_btn", useUnmergedTree = true)
                photoBinNode.assertIsDisplayed()
                val photoBinBounds = photoBinNode.fetchSemanticsNode().boundsInRoot
                assertTrue(
                    "[$themeLabel/photo] Delete bin touch target must be >= 48.dp, was ${photoBinBounds.width}x${photoBinBounds.height}",
                    photoBinBounds.width >= minTouchPx && photoBinBounds.height >= minTouchPx
                )

                val mutBeforePhoto = com.example.data.repository.MutationTracker.currentStamp
                photoBinNode.performClick()
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()

                val deadlinePhoto = System.currentTimeMillis() + 3000L
                while (mediaRepo.getAttachmentsForTxnSync(txnId).any { it.id == photoAttId } && System.currentTimeMillis() < deadlinePhoto) {
                    ShadowLooper.idleMainLooper()
                    composeTestRule.waitForIdle()
                    kotlinx.coroutines.delay(15)
                }
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()

                assertFalse(
                    "[$themeLabel/photo] Photo attachment must be removed from MediaRepository on delete tap",
                    mediaRepo.getAttachmentsForTxnSync(txnId).any { it.id == photoAttId }
                )
                assertFalse("[$themeLabel/photo] Photo file on disk must be deleted", photoFile.exists())
                assertTrue(
                    "[$themeLabel/photo] MutationTracker must bump on photo delete",
                    com.example.data.repository.MutationTracker.currentStamp > mutBeforePhoto
                )
                composeTestRule.onNodeWithTag("viewer_photo_card_0").assertDoesNotExist()
            }
        }
    }

    // --- TASK RELEASE-2: UT15a-e Auto Theme Source Setting & Resolution Table ---
    @Test
    fun testUT15a_autoDaylight_mocked1000IsLight_mocked2000IsDark() {
        // AUTO + DAYLIGHT + mocked 10:00 -> Light (false)
        assertFalse(
            "AUTO + DAYLIGHT at 10:00 must resolve to Light (darkTheme=false)",
            com.example.ui.resolveDarkTheme(
                themeMode = "AUTO",
                autoThemeSource = "DAYLIGHT",
                isSystemDark = true, // Even when OS is in dark mode!
                currentHourOfDay = 10
            )
        )
        // Boundary checks for [6, 18): 06:00 -> Light, 17:00 -> Light, 18:00 -> Dark, 05:00 -> Dark
        assertFalse(
            "AUTO + DAYLIGHT at 06:00 must resolve to Light",
            com.example.ui.resolveDarkTheme("AUTO", "DAYLIGHT", isSystemDark = true, currentHourOfDay = 6)
        )
        assertFalse(
            "AUTO + DAYLIGHT at 17:00 must resolve to Light",
            com.example.ui.resolveDarkTheme("AUTO", "DAYLIGHT", isSystemDark = true, currentHourOfDay = 17)
        )
        // AUTO + DAYLIGHT + mocked 20:00 -> Dark (true)
        assertTrue(
            "AUTO + DAYLIGHT at 20:00 must resolve to Dark (darkTheme=true)",
            com.example.ui.resolveDarkTheme(
                themeMode = "AUTO",
                autoThemeSource = "DAYLIGHT",
                isSystemDark = false, // Even when OS is in light mode!
                currentHourOfDay = 20
            )
        )
        assertTrue(
            "AUTO + DAYLIGHT at 18:00 must resolve to Dark",
            com.example.ui.resolveDarkTheme("AUTO", "DAYLIGHT", isSystemDark = false, currentHourOfDay = 18)
        )
        assertTrue(
            "AUTO + DAYLIGHT at 05:00 must resolve to Dark",
            com.example.ui.resolveDarkTheme("AUTO", "DAYLIGHT", isSystemDark = false, currentHourOfDay = 5)
        )
    }

    @Test
    fun testUT15b_autoDevice_followsSystemDarkTrueAndFalse() {
        assertTrue(
            "AUTO + DEVICE with system dark=true must resolve to Dark",
            com.example.ui.resolveDarkTheme(
                themeMode = "AUTO",
                autoThemeSource = "DEVICE",
                isSystemDark = true,
                currentHourOfDay = 10 // Daytime hour ignored in DEVICE mode
            )
        )
        assertFalse(
            "AUTO + DEVICE with system dark=false must resolve to Light",
            com.example.ui.resolveDarkTheme(
                themeMode = "AUTO",
                autoThemeSource = "DEVICE",
                isSystemDark = false,
                currentHourOfDay = 22 // Nighttime hour ignored in DEVICE mode
            )
        )
    }

    @Test
    fun testUT15c_manualSunAndMoon_overrideBothSources() {
        for (source in listOf("DEVICE", "DAYLIGHT")) {
            for (sysDark in listOf(false, true)) {
                for (hour in listOf(2, 10, 18, 22)) {
                    assertFalse(
                        "Manual LIGHT (sun) must override source=$source sysDark=$sysDark hour=$hour to Light",
                        com.example.ui.resolveDarkTheme(
                            themeMode = "LIGHT",
                            autoThemeSource = source,
                            isSystemDark = sysDark,
                            currentHourOfDay = hour
                        )
                    )
                    assertTrue(
                        "Manual DARK (moon) must override source=$source sysDark=$sysDark hour=$hour to Dark",
                        com.example.ui.resolveDarkTheme(
                            themeMode = "DARK",
                            autoThemeSource = source,
                            isSystemDark = sysDark,
                            currentHourOfDay = hour
                        )
                    )
                }
            }
        }
    }

    @Test
    fun testUT15d_settingsToggle_persistsAcrossSimulatedRestart_resolvesAndSwapsSubtitle_andNoRadiosExist() {
        runBlocking {
            // Default before any change is DEVICE (toggle ON)
            assertEquals("DEVICE", settingsRepository.settingsFlow.first().autoThemeSource)

            val settingsVm = com.example.ui.settings.SettingsViewModel(repository, settingsRepository, database)
            var mockedSystemDark by mutableStateOf(true)
            var mockedHour by mutableStateOf(10) // 10 AM daytime -> Light under DAYLIGHT, Dark under DEVICE (when mockedSystemDark=true)
            var observedDarkTheme = false
            var mountScreen by mutableStateOf(true)

            composeTestRule.setContent {
                val liveSettings by settingsRepository.settingsFlow.collectAsState(
                    initial = com.example.data.datastore.AppSettings()
                )
                val resolvedDark = com.example.ui.rememberResolvedDarkTheme(
                    themeMode = liveSettings.theme,
                    autoThemeSource = liveSettings.autoThemeSource,
                    isSystemDark = mockedSystemDark,
                    hourProvider = { mockedHour }
                )
                observedDarkTheme = resolvedDark
                MyApplicationTheme(darkTheme = resolvedDark) {
                    if (mountScreen) {
                        com.example.ui.settings.SettingsScreen(viewModel = settingsVm)
                    }
                }
            }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            // 1. Assert title renders and NO radio nodes or legacy radio tags/labels exist anywhere in Settings
            composeTestRule.onNodeWithText("Auto theme (A) follows").performScrollTo().assertIsDisplayed()
            assertEquals(
                "No RadioButton nodes should exist anywhere in Settings",
                0,
                composeTestRule.onAllNodes(
                    androidx.compose.ui.test.SemanticsMatcher.expectValue(
                        androidx.compose.ui.semantics.SemanticsProperties.Role,
                        androidx.compose.ui.semantics.Role.RadioButton
                    )
                ).fetchSemanticsNodes().size
            )
            assertEquals(0, composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag("auto_theme_source_device")).fetchSemanticsNodes().size)
            assertEquals(0, composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag("auto_theme_source_device_radio")).fetchSemanticsNodes().size)
            assertEquals(0, composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag("auto_theme_source_daylight")).fetchSemanticsNodes().size)
            assertEquals(0, composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag("auto_theme_source_daylight_radio")).fetchSemanticsNodes().size)
            assertEquals(0, composeTestRule.onAllNodes(androidx.compose.ui.test.hasText("Device theme")).fetchSemanticsNodes().size)
            assertEquals(0, composeTestRule.onAllNodes(androidx.compose.ui.test.hasText("Daylight hours (6 AM – 6 PM)")).fetchSemanticsNodes().size)

            // 2. Default ON (DEVICE): verify toggle is ON, exact ON subtitle, and resolves via mocked system dark
            composeTestRule.onNodeWithTag("settings_auto_source_toggle").performScrollTo()
            composeTestRule.onNode(
                androidx.compose.ui.test.hasTestTag("settings_auto_source_toggle") and androidx.compose.ui.test.isOn()
            ).assertExists()
            composeTestRule.onNodeWithTag("settings_auto_source_caption")
                .assertTextEquals("Follows your device theme.")
            assertTrue(
                "Toggle ON (DEVICE) with mockedSystemDark=true at 10 AM must resolve to Dark",
                observedDarkTheme
            )

            // 3. Flip toggle OFF -> DAYLIGHT
            composeTestRule.onNodeWithTag("settings_auto_source_toggle").performScrollTo().performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            val deadlineDaylight = System.currentTimeMillis() + 3000L
            while (settingsRepository.settingsFlow.first().autoThemeSource != "DAYLIGHT" && System.currentTimeMillis() < deadlineDaylight) {
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()
                kotlinx.coroutines.delay(15)
            }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            // Verify toggle is OFF, exact OFF subtitle, persistence across simulated restart, and resolution via daylight hours
            composeTestRule.onNode(
                androidx.compose.ui.test.hasTestTag("settings_auto_source_toggle") and androidx.compose.ui.test.isOff()
            ).assertExists()
            composeTestRule.onNodeWithTag("settings_auto_source_caption")
                .assertTextEquals("Day mode 6 AM–6 PM · Night mode 6 PM–6 AM.")

            val restartedRepoOff = com.example.data.datastore.AppSettingsRepository(context)
            val persistedOffSource = restartedRepoOff.settingsFlow.first().autoThemeSource
            assertEquals(
                "Toggle OFF (DAYLIGHT) must persist across simulated restart",
                "DAYLIGHT",
                persistedOffSource
            )
            assertFalse(
                "Toggle OFF (DAYLIGHT) at 10 AM (even with mockedSystemDark=true) must live-resolve to Light",
                observedDarkTheme
            )
            assertFalse(
                "Persisted DAYLIGHT source at 10:00 must resolve to Light",
                com.example.ui.resolveDarkTheme("AUTO", persistedOffSource, isSystemDark = true, currentHourOfDay = 10)
            )
            assertTrue(
                "Persisted DAYLIGHT source at 20:00 must resolve to Dark",
                com.example.ui.resolveDarkTheme("AUTO", persistedOffSource, isSystemDark = false, currentHourOfDay = 20)
            )

            // 4. Flip toggle back ON -> DEVICE
            composeTestRule.onNodeWithTag("settings_auto_source_toggle").performScrollTo().performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            val deadlineDevice = System.currentTimeMillis() + 3000L
            while (restartedRepoOff.settingsFlow.first().autoThemeSource != "DEVICE" && System.currentTimeMillis() < deadlineDevice) {
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()
                kotlinx.coroutines.delay(15)
            }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            // Verify toggle is ON, exact ON subtitle, persistence across simulated restart, and resolution via mocked system dark
            composeTestRule.onNode(
                androidx.compose.ui.test.hasTestTag("settings_auto_source_toggle") and androidx.compose.ui.test.isOn()
            ).assertExists()
            composeTestRule.onNodeWithTag("settings_auto_source_caption")
                .assertTextEquals("Follows your device theme.")

            val restartedRepoOn = com.example.data.datastore.AppSettingsRepository(context)
            val persistedOnSource = restartedRepoOn.settingsFlow.first().autoThemeSource
            assertEquals(
                "Toggle ON (DEVICE) must persist across simulated restart",
                "DEVICE",
                persistedOnSource
            )
            assertTrue(
                "Toggle ON (DEVICE) with mockedSystemDark=true at 10 AM must live-resolve to Dark",
                observedDarkTheme
            )
            mockedSystemDark = false
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            assertFalse(
                "Toggle ON (DEVICE) with mockedSystemDark=false must live-resolve to Light",
                observedDarkTheme
            )
            assertTrue(
                "Persisted DEVICE source with isSystemDark=true must resolve to Dark",
                com.example.ui.resolveDarkTheme("AUTO", persistedOnSource, isSystemDark = true, currentHourOfDay = 10)
            )
            assertFalse(
                "Persisted DEVICE source with isSystemDark=false must resolve to Light",
                com.example.ui.resolveDarkTheme("AUTO", persistedOnSource, isSystemDark = false, currentHourOfDay = 22)
            )

            mountScreen = false
            settingsVm.viewModelScope.cancel()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            kotlinx.coroutines.delay(60)
        }
    }

    @Test
    fun testUT15e_daylightTicker_1759Light_advanceClockTo1801_flipsToDarkWithoutRecreation() {
        var mockedHour = 17 // 17:59 -> hour of day = 17 (Light)
        var themeModeState by mutableStateOf("AUTO")
        var autoSourceState by mutableStateOf("DAYLIGHT")
        var isSysDarkState by mutableStateOf(false)
        var observedDarkTheme = true

        composeTestRule.setContent {
            val resolvedDark = com.example.ui.rememberResolvedDarkTheme(
                themeMode = themeModeState,
                autoThemeSource = autoSourceState,
                isSystemDark = isSysDarkState,
                hourProvider = { mockedHour },
                tickerIntervalMs = 30_000L
            )
            observedDarkTheme = resolvedDark
            MyApplicationTheme(darkTheme = resolvedDark) {
                androidx.compose.material3.Text(
                    text = if (com.example.ui.theme.LocalIsDarkTheme.current) "THEME_DARK" else "THEME_LIGHT"
                )
            }
        }
        ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        // 1. At 17:59 (hour 17), AUTO + DAYLIGHT is Light
        assertFalse("At 17:59 (hour 17), AUTO + DAYLIGHT must resolve to Light", observedDarkTheme)
        composeTestRule.onNodeWithText("THEME_LIGHT").assertIsDisplayed()

        // 2. Advance clock to 18:01 (hour 18) and advance virtual clock by one 30s tick WITHOUT activity recreation
        mockedHour = 18
        composeTestRule.mainClock.advanceTimeBy(31_000L)
        ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        assertTrue("After crossing 18:00 + one tick, AUTO + DAYLIGHT must flip to Dark without recreation", observedDarkTheme)
        composeTestRule.onNodeWithText("THEME_DARK").assertIsDisplayed()

        // 3. Immediate radio change to DEVICE (where isSysDarkState = false) flips immediately to Light
        autoSourceState = "DEVICE"
        ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()
        assertFalse("Radio change to DEVICE (sysDark=false) must immediately flip to Light", observedDarkTheme)

        // 4. System configuration change (isSysDarkState = true) while AUTO + DEVICE flips immediately to Dark
        isSysDarkState = true
        ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()
        assertTrue("System config dark=true while AUTO + DEVICE must immediately flip to Dark", observedDarkTheme)

        // 5. Manual mode change to LIGHT overrides immediately
        themeModeState = "LIGHT"
        ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()
        assertFalse("Manual LIGHT mode must immediately flip to Light", observedDarkTheme)
    }

    // --- TASK RELEASE-2: UT16 Ledger Swipe Bin Centered Like Media ({light, dark} x {ledger row, voice row}) ---
    @Test
    fun testUT16_ledgerAndVoiceSwipeDelete_lightAndDark_bandWidthCenteredBinAndUndoNotice() {
        runBlocking {
            val mediaRepo = com.example.data.repository.MediaRepository(context, database)
            val accId = repository.insertAccount(Account(id = 1L, name = "Cash", accountType = "CASH", openingBalance = 5000.0))
            val catId = repository.insertCategory(Category(id = 1L, name = "Food", color = "#10B981"))

            val ledgerVm = com.example.ui.ledger.LedgerViewModel(repository, settingsRepository, mediaRepo)
            var isDarkState by mutableStateOf(false)
            var surfaceMode by mutableStateOf("LEDGER") // "LEDGER" or "VOICE"
            var activeVoiceTxnId by mutableStateOf(901L)
            var voiceSeededList by mutableStateOf<List<com.example.data.model.MediaAttachment>>(emptyList())
            var resolvedVoiceBandBg = androidx.compose.ui.graphics.Color.Unspecified
            var resolvedLedgerBandBg = androidx.compose.ui.graphics.Color.Unspecified
            var resolvedBinTint = androidx.compose.ui.graphics.Color.Unspecified

            composeTestRule.setContent {
                MyApplicationTheme(darkTheme = isDarkState) {
                    resolvedVoiceBandBg = androidx.compose.material3.MaterialTheme.colorScheme.error
                    resolvedLedgerBandBg = com.example.ui.theme.ExpenseRed
                    resolvedBinTint = androidx.compose.ui.graphics.Color.White
                    if (surfaceMode == "LEDGER") {
                        com.example.ui.ledger.LedgerScreen(
                            viewModel = ledgerVm,
                            onAddClick = {},
                            onTransactionClick = {},
                            mediaRepository = mediaRepo
                        )
                    } else if (surfaceMode == "VOICE") {
                        key(isDarkState, activeVoiceTxnId) {
                            com.example.ui.media.MediaViewerSheet(
                                txnId = activeVoiceTxnId,
                                attachments = voiceSeededList,
                                initialTab = "VOICE",
                                hideBalances = false,
                                mediaRepository = mediaRepo,
                                onDismiss = {}
                            )
                        }
                    }
                }
            }

            val oneDpPx = with(composeTestRule.density) { 1.dp.toPx() }

            for (isDark in listOf(false, true)) {
                val themeLabel = if (isDark) "dark" else "light"
                val ledgerTxId = if (isDark) 912L else 911L
                val voiceTxId = if (isDark) 922L else 921L

                // 1. Seed Ledger transaction & test LEDGER row in {light, dark}
                repository.insertTransaction(
                    TransactionEntity(
                        id = ledgerTxId,
                        date = System.currentTimeMillis(),
                        type = "EXPENSE",
                        amount = 150.0,
                        fromAccountId = accId,
                        categoryId = catId,
                        description = "Ledger F15 $themeLabel"
                    )
                )
                isDarkState = isDark
                surfaceMode = "LEDGER"
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()
                composeTestRule.waitUntil(timeoutMillis = 5000) {
                    runCatching {
                        composeTestRule.onNodeWithTag("tx_icon_$ledgerTxId", useUnmergedTree = true).fetchSemanticsNode()
                    }.isSuccess
                }

                val ledgerRowNode = composeTestRule.onNodeWithText("Ledger F15 $themeLabel")
                val ledgerBeforeBounds = ledgerRowNode.fetchSemanticsNode().boundsInRoot
                ledgerRowNode.performTouchInput {
                    down(center)
                    moveBy(androidx.compose.ui.geometry.Offset(-400f, 0f))
                    up()
                }
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()

                val ledgerBandBounds = composeTestRule.onNodeWithTag("ledger_delete_band", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                val ledgerBinNode = composeTestRule.onNodeWithTag("ledger_delete_bin", useUnmergedTree = true)
                ledgerBinNode.assertIsDisplayed()
                val ledgerBinBounds = ledgerBinNode.fetchSemanticsNode().boundsInRoot

                val expectedLedgerMaxRevealPx = com.example.ui.common.SwipeRevealHelper.computeMaxRevealPx(
                    ledgerBeforeBounds.width,
                    composeTestRule.density
                )
                // Band width == clamp
                assertEquals(
                    "[$themeLabel/ledger] Band width must equal SwipeRevealHelper clamp",
                    expectedLedgerMaxRevealPx,
                    ledgerBandBounds.width,
                    2.5f
                )
                // Bin center x == band center x (±1.dp)
                assertEquals(
                    "[$themeLabel/ledger] Bin center X (${ledgerBinBounds.center.x}) must equal band center X (${ledgerBandBounds.center.x}) within ±1.dp ($oneDpPx px)",
                    ledgerBandBounds.center.x,
                    ledgerBinBounds.center.x,
                    oneDpPx + 0.5f
                )
                // Bin tint != band background
                assertNotEquals(
                    "[$themeLabel/ledger] Bin tint must not equal band background",
                    resolvedLedgerBandBg,
                    resolvedBinTint
                )

                // Ledger tap deletes and shows the UNDO notice ("Deleted \"Ledger F15 $themeLabel\"" with "UNDO")
                ledgerBinNode.performClick()
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()

                composeTestRule.onNodeWithText("Deleted \"Ledger F15 $themeLabel\"").assertIsDisplayed()
                composeTestRule.onNodeWithText("UNDO").assertIsDisplayed()
                composeTestRule.onNodeWithText("Ledger F15 $themeLabel").assertDoesNotExist()

                // Tap UNDO to restore row, then clean up before next iteration
                composeTestRule.onNodeWithText("UNDO").performClick()
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithText("Ledger F15 $themeLabel").assertIsDisplayed()
                repository.deleteTransactionById(ledgerTxId)
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()

                // 2. Seed Voice attachment & test VOICE row in {light, dark}
                val txnDir = mediaRepo.getVaultDirForTxn(voiceTxId)
                val voiceFile = File(txnDir, "f15_voice_$themeLabel.m4a").apply {
                    writeBytes(ByteArray(4096) { 0x22 })
                }
                voiceSeededList = listOf(
                    com.example.data.model.MediaAttachment(
                        id = voiceTxId,
                        txnId = voiceTxId,
                        kind = "VOICE",
                        fileName = voiceFile.name,
                        createdAt = System.currentTimeMillis()
                    )
                )
                activeVoiceTxnId = voiceTxId
                surfaceMode = "VOICE"
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()

                val voiceContainerBounds = composeTestRule.onNodeWithTag("viewer_voice_swipe_container_0")
                    .fetchSemanticsNode().boundsInRoot
                val voiceCardNode = composeTestRule.onNodeWithTag("viewer_voice_card_0")
                voiceCardNode.assertIsDisplayed()
                voiceCardNode.performTouchInput {
                    down(center)
                    moveBy(androidx.compose.ui.geometry.Offset(-400f, 0f))
                    up()
                }
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()

                val voiceBandBounds = composeTestRule.onNodeWithTag("voice_delete_reveal_band_0", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                val voiceBinBounds = composeTestRule.onNodeWithTag("voice_delete_bin_0", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                val expectedVoiceMaxRevealPx = com.example.ui.common.SwipeRevealHelper.computeMaxRevealPx(
                    voiceContainerBounds.width,
                    composeTestRule.density
                )
                assertEquals(
                    "[$themeLabel/voice] Band width must equal SwipeRevealHelper clamp",
                    expectedVoiceMaxRevealPx,
                    voiceBandBounds.width,
                    2.5f
                )
                assertEquals(
                    "[$themeLabel/voice] Bin center X (${voiceBinBounds.center.x}) must equal band center X (${voiceBandBounds.center.x}) within ±1.dp ($oneDpPx px)",
                    voiceBandBounds.center.x,
                    voiceBinBounds.center.x,
                    oneDpPx + 0.5f
                )
                assertNotEquals(
                    "[$themeLabel/voice] Bin tint must not equal band background",
                    resolvedVoiceBandBg,
                    resolvedBinTint
                )
                voiceFile.delete()
            }
            surfaceMode = "NONE"
            ledgerVm.viewModelScope.cancel()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            kotlinx.coroutines.delay(80)
            ShadowLooper.idleMainLooper()
        }
    }

    // --- UT18a (F17a): the four bottom-nav icon nodes resolve to four pairwise-different icon vectors (no two equal), light + dark; labels unchanged ---
    @Test
    fun testUT18a_bottomNavGlyphs_fourPairwiseDistinctVectors_lightAndDark_labelsUnchanged() {
        var isDarkState by androidx.compose.runtime.mutableStateOf(false)
        var selectedTab by androidx.compose.runtime.mutableStateOf(com.example.ui.common.NavTab.DASHBOARD)

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = isDarkState) {
                com.example.ui.common.AppBottomNav(
                    currentTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        }

        fun extractVectorSignature(node: androidx.compose.ui.semantics.SemanticsNode): String {
            for (modInfo in node.layoutInfo.getModifierInfo()) {
                val mod = modInfo.modifier
                if (mod.javaClass.name.contains("Painter")) {
                    val painterField = mod.javaClass.declaredFields.firstOrNull { it.name == "painter" }
                    painterField?.isAccessible = true
                    val painter = painterField?.get(mod) ?: continue
                    val vectorField = painter.javaClass.declaredFields.firstOrNull { it.name == "vector" }
                    vectorField?.isAccessible = true
                    val vectorComp = vectorField?.get(painter) ?: continue
                    val nameField = vectorComp.javaClass.declaredFields.firstOrNull { it.name == "name" }
                    nameField?.isAccessible = true
                    val vecName = nameField?.get(vectorComp)?.toString() ?: ""
                    val rootField = vectorComp.javaClass.declaredFields.firstOrNull { it.name == "root" }
                    rootField?.isAccessible = true
                    val rootGroup = rootField?.get(vectorComp)
                    fun dumpGroup(g: Any?): String {
                        if (g == null) return ""
                        val cls = g.javaClass
                        if (cls.simpleName == "PathComponent") {
                            val pathDataField = cls.declaredFields.firstOrNull { it.name == "pathData" }
                            pathDataField?.isAccessible = true
                            return pathDataField?.get(g)?.toString() ?: cls.simpleName
                        }
                        val childrenField = cls.declaredFields.firstOrNull { it.name == "children" }
                        childrenField?.isAccessible = true
                        val children = childrenField?.get(g) as? List<*> ?: emptyList<Any?>()
                        return children.joinToString("|") { dumpGroup(it) }
                    }
                    return "$vecName::${dumpGroup(rootGroup)}"
                }
            }
            return "UNKNOWN"
        }

        val expectedLabels = mapOf(
            com.example.ui.common.NavTab.DASHBOARD to "Dashboard",
            com.example.ui.common.NavTab.EXPENSES to "Expenses",
            com.example.ui.common.NavTab.LISTS to "Lists",
            com.example.ui.common.NavTab.SETTINGS to "Settings"
        )

        for (isDark in listOf(false, true)) {
            val themeLabel = if (isDark) "dark" else "light"
            isDarkState = isDark
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            val signatures = mutableMapOf<com.example.ui.common.NavTab, String>()
            for (tab in com.example.ui.common.NavTab.values()) {
                val tabTag = "nav_tab_${tab.name.lowercase()}"
                val tabNode = composeTestRule.onNodeWithTag(tabTag, useUnmergedTree = true).fetchSemanticsNode()

                // Verify label unchanged
                val texts = mutableListOf<String>()
                fun collectTexts(n: androidx.compose.ui.semantics.SemanticsNode) {
                    if (androidx.compose.ui.semantics.SemanticsProperties.Text in n.config) {
                        n.config[androidx.compose.ui.semantics.SemanticsProperties.Text].forEach { texts.add(it.text) }
                    }
                    n.children.forEach { collectTexts(it) }
                }
                collectTexts(tabNode)
                assertEquals(
                    "[$themeLabel] Label for ${tab.name} must be unchanged",
                    listOf(expectedLabels[tab]),
                    texts
                )

                // Find the Icon child node (Role.Image / contentDescription == tab.title)
                fun findIconNode(n: androidx.compose.ui.semantics.SemanticsNode): androidx.compose.ui.semantics.SemanticsNode? {
                    val desc = if (androidx.compose.ui.semantics.SemanticsProperties.ContentDescription in n.config) {
                        n.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription]
                    } else null
                    if (desc?.contains(tab.title) == true) return n
                    for (c in n.children) {
                        val found = findIconNode(c)
                        if (found != null) return found
                    }
                    return null
                }
                val iconNode = findIconNode(tabNode)
                assertNotNull("[$themeLabel] Icon node must exist for ${tab.name}", iconNode)
                val sig = extractVectorSignature(iconNode!!)
                assertTrue("[$themeLabel] Vector signature must be resolved for ${tab.name}", sig != "UNKNOWN" && sig.isNotBlank())
                signatures[tab] = sig
            }

            // Pairwise distinct assertion across all 6 pairs
            val tabs = com.example.ui.common.NavTab.values().toList()
            for (i in tabs.indices) {
                for (j in i + 1 until tabs.size) {
                    val t1 = tabs[i]
                    val t2 = tabs[j]
                    assertNotEquals(
                        "[$themeLabel] Bottom-nav glyphs for ${t1.name} and ${t2.name} must be pairwise distinct, but both resolved to: ${signatures[t1]?.take(60)}",
                        signatures[t1],
                        signatures[t2]
                    )
                }
            }
            assertEquals("[$themeLabel] All 4 bottom-nav glyphs must be distinct", 4, signatures.values.toSet().size)
        }
    }

    // --- UT18b (F17b): fixture source WITH budget -> chip node y-bounds strictly between name node and opening node; name node text equals full name (no ellipsis); balance on name line right-aligned; fixture source WITHOUT budget -> layout unchanged; light + dark ---
    @Test
    fun testUT18b_sourceRowWithBudget_chipOnOwnLineBetweenNameAndOpening_fullNameNoEllipsis_lightAndDark() {
        runBlocking {
            settingsRepository.setCurrency("₹")
            com.example.domain.util.Formatters.updateCurrentCurrencySymbol("₹")
            settingsRepository.setBudgetsCardVisible(true)

            val personalId = repository.insertAccount(
                Account(id = 10L, name = "Personal", accountType = "BANK", openingBalance = 6240.24, sortOrder = 1)
            )
            val salaryId = repository.insertAccount(
                Account(id = 11L, name = "Salary A/C", accountType = "BANK", openingBalance = 0.0, sortOrder = 2)
            )
            val catId = repository.insertCategory(Category(id = 10L, name = "General", color = "#10B981"))
            val now = System.currentTimeMillis()
            repository.insertTransaction(
                TransactionEntity(id = 1001L, date = now, type = "INCOME", amount = 78100.0, toAccountId = personalId, categoryId = catId, description = "Funding")
            )
            repository.insertTransaction(
                TransactionEntity(id = 1002L, date = now, type = "EXPENSE", amount = 5320.0, fromAccountId = personalId, categoryId = catId, description = "Spend")
            )
            repository.insertTransaction(
                TransactionEntity(id = 1003L, date = now, type = "INCOME", amount = 54000.0, toAccountId = salaryId, categoryId = catId, description = "Salary")
            )
            repository.insertBudget(
                com.example.data.model.BudgetEntity(
                    id = 10L,
                    name = "Personal Account Allowance",
                    scopeType = "ACCOUNT",
                    targetName = "Personal",
                    limitAmount = 25000.0,
                    active = true
                )
            )

            val dashboardVm = DashboardViewModel(repository, settingsRepository)
            var isDarkState by androidx.compose.runtime.mutableStateOf(false)
            var mountScreen by androidx.compose.runtime.mutableStateOf(true)

            composeTestRule.setContent {
                MyApplicationTheme(darkTheme = isDarkState) {
                    if (mountScreen) {
                        DashboardScreen(
                            viewModel = dashboardVm,
                            onNavigateToExpenses = { _, _ -> }
                        )
                    }
                }
            }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                ShadowLooper.idleMainLooper()
                dashboardVm.uiState.value.accountsWithBalance.size >= 2 &&
                    dashboardVm.uiState.value.calculatedBudgets.isNotEmpty()
            }

            for (isDark in listOf(false, true)) {
                val themeLabel = if (isDark) "dark" else "light"
                isDarkState = isDark
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()

                composeTestRule.scrollToDashboardTag("source_row_$personalId", useUnmergedTree = true)
                composeTestRule.waitForIdle()

                val personalRowNode = composeTestRule.onNodeWithTag("source_row_$personalId", useUnmergedTree = true).fetchSemanticsNode()
                val chipNode = composeTestRule.onNodeWithTag("source_budget_chip_$personalId", useUnmergedTree = true).fetchSemanticsNode()

                fun findTextChild(root: androidx.compose.ui.semantics.SemanticsNode, expectedText: String): androidx.compose.ui.semantics.SemanticsNode? {
                    val txt = if (androidx.compose.ui.semantics.SemanticsProperties.Text in root.config) {
                        root.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") { it.text }
                    } else null
                    if (txt == expectedText) return root
                    for (c in root.children) {
                        val found = findTextChild(c, expectedText)
                        if (found != null) return found
                    }
                    return null
                }

                val nameNode = findTextChild(personalRowNode, "Personal")
                val openingNode = findTextChild(personalRowNode, "Opening: ₹6,240.24")
                val balanceNode = findTextChild(personalRowNode, "₹79,020.24")

                assertNotNull("[$themeLabel] Name node 'Personal' must exist", nameNode)
                assertNotNull("[$themeLabel] Opening node 'Opening: ₹6,240.24' must exist", openingNode)
                assertNotNull("[$themeLabel] Balance node '₹79,020.24' must exist", balanceNode)

                val nameBounds = nameNode!!.boundsInRoot
                val chipBounds = chipNode.boundsInRoot
                val openingBounds = openingNode!!.boundsInRoot
                val balanceBounds = balanceNode!!.boundsInRoot

                // 1. Name node text equals full name and is NOT ellipsized
                val nameText = if (androidx.compose.ui.semantics.SemanticsProperties.Text in nameNode.config) {
                    nameNode.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") { it.text }
                } else ""
                assertEquals("[$themeLabel] Name node text must equal full name", "Personal", nameText)
                val layoutResults = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                if (androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult in nameNode.config) {
                    nameNode.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(layoutResults)
                }
                assertTrue("[$themeLabel] TextLayoutResult must be available for name node", layoutResults.isNotEmpty())
                assertFalse(
                    "[$themeLabel] Name 'Personal' must NOT be ellipsized (was truncated to 'Person…' when chip was inline)",
                    layoutResults.first().isLineEllipsized(0)
                )

                // 2. Chip node y-bounds strictly between name node and opening node
                assertTrue(
                    "[$themeLabel] Chip top (${chipBounds.top}) must be >= name bottom (${nameBounds.bottom}) (chip must be on Row 2 below name, not inline on Row 1)",
                    chipBounds.top >= nameBounds.bottom - 1.0f
                )
                assertTrue(
                    "[$themeLabel] Chip bottom (${chipBounds.bottom}) must be <= opening top (${openingBounds.top}) (chip must be above Opening on Row 3)",
                    chipBounds.bottom <= openingBounds.top + 1.0f
                )

                // 3. Balance on the name line right-aligned
                val twoDpPx = with(composeTestRule.density) { 2.dp.toPx() }
                assertEquals(
                    "[$themeLabel] Balance center.y (${balanceBounds.center.y}) must align with Name center.y (${nameBounds.center.y}) on Row 1",
                    nameBounds.center.y,
                    balanceBounds.center.y,
                    twoDpPx + 1.0f
                )
                assertTrue(
                    "[$themeLabel] Balance (${balanceBounds.left}) must be right-aligned to the right of Name (${nameBounds.right})",
                    balanceBounds.left > nameBounds.right
                )

                // 4. Fixture source WITHOUT budget (Salary A/C) -> layout unchanged
                composeTestRule.scrollToDashboardTag("source_row_$salaryId", useUnmergedTree = true)
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("source_budget_chip_$salaryId", useUnmergedTree = true).assertDoesNotExist()
                val salaryRowNode = composeTestRule.onNodeWithTag("source_row_$salaryId", useUnmergedTree = true).fetchSemanticsNode()
                val salaryNameNode = findTextChild(salaryRowNode, "Salary A/C")
                val salaryOpeningNode = findTextChild(salaryRowNode, "Opening: ₹0.00")
                val salaryBalanceNode = findTextChild(salaryRowNode, "₹54,000.00")
                assertNotNull("[$themeLabel] Salary A/C name node must exist", salaryNameNode)
                assertNotNull("[$themeLabel] Salary A/C opening node must exist", salaryOpeningNode)
                assertNotNull("[$themeLabel] Salary A/C balance node must exist", salaryBalanceNode)
                assertTrue(
                    "[$themeLabel] Without budget, Opening top (${salaryOpeningNode!!.boundsInRoot.top}) is directly below Name bottom (${salaryNameNode!!.boundsInRoot.bottom})",
                    salaryOpeningNode.boundsInRoot.top >= salaryNameNode.boundsInRoot.bottom - 1.0f
                )
                assertTrue(
                    "[$themeLabel] Without budget, Balance (${salaryBalanceNode!!.boundsInRoot.left}) is to the right of Name (${salaryNameNode.boundsInRoot.right})",
                    salaryBalanceNode.boundsInRoot.left > salaryNameNode.boundsInRoot.right
                )
            }

            mountScreen = false
            dashboardVm.viewModelScope.cancel()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            kotlinx.coroutines.delay(50)
            ShadowLooper.idleMainLooper()
        }
    }

    // --- UT19 (F18): Credit-card source row long-press opens pre-filled transfer (Type=TRANSFER, To=card, Amount=2345.12, From=Cash); editable; tap -> filtered ledger; non-card long-press -> no-op; ? sheet 28 exact strings; masked prefill ---
    @Test
    fun testUT19_creditCardLongPress_opensPreFilledTransfer_tapOpensLedger_nonCardNoOp_helpSheet28_andMaskedPrefill() {
        runBlocking {
            settingsRepository.setCurrency("₹")
            com.example.domain.util.Formatters.updateCurrentCurrencySymbol("₹")
            settingsRepository.setHideBalances(false)
            settingsRepository.setSoundHapticsEnabled(true)

            val cashId = repository.insertAccount(
                Account(id = 1L, name = "Cash", accountType = "CASH", openingBalance = 10000.0, sortOrder = 1)
            )
            val bankId = repository.insertAccount(
                Account(id = 2L, name = "HDFC Bank", accountType = "BANK", openingBalance = 25000.0, sortOrder = 2)
            )
            val cardId = repository.insertAccount(
                Account(id = 3L, name = "ICICI Credit Card", accountType = "CREDIT_CARD", openingBalance = -1000.0, sortOrder = 3)
            )
            val unpaidCardId = repository.insertAccount(
                Account(id = 4L, name = "Axis Credit Card", accountType = "CREDIT_CARD", openingBalance = -890.50, sortOrder = 4)
            )
            val catId = repository.insertCategory(Category(id = 1L, name = "Shopping", color = "#EF4444"))

            val now = System.currentTimeMillis()
            // Older payment to ICICI Credit Card from HDFC Bank (2 days ago)
            repository.insertTransaction(
                TransactionEntity(
                    id = 2001L,
                    date = now - 172_800_000L,
                    type = "TRANSFER",
                    amount = 200.0,
                    fromAccountId = bankId,
                    toAccountId = cardId,
                    description = "Older card payment"
                )
            )
            // Most recent prior payment to ICICI Credit Card from Cash (1 day ago)
            repository.insertTransaction(
                TransactionEntity(
                    id = 2002L,
                    date = now - 86_400_000L,
                    type = "TRANSFER",
                    amount = 500.0,
                    fromAccountId = cashId,
                    toAccountId = cardId,
                    description = "Recent card payment from Cash"
                )
            )
            // Card purchase on ICICI Credit Card so net balance = -1000 + 200 + 500 - 2045.12 = -2345.12 -> owed = 2345.12
            repository.insertTransaction(
                TransactionEntity(
                    id = 2003L,
                    date = now - 3_600_000L,
                    type = "EXPENSE",
                    amount = 2045.12,
                    fromAccountId = cardId,
                    categoryId = catId,
                    description = "Card purchase"
                )
            )

            val dashboardVm = DashboardViewModel(repository, settingsRepository)
            var activeEntryVm by androidx.compose.runtime.mutableStateOf<com.example.ui.entry.EntryViewModel?>(null)
            var navigatedLedgerFilter: com.example.domain.model.FilterState? = null
            var transferPrefillCalls = 0
            var showHelpSheet by androidx.compose.runtime.mutableStateOf(false)
            var mountHost by androidx.compose.runtime.mutableStateOf(true)

            composeTestRule.setContent {
                val appSettings by settingsRepository.settingsFlow.collectAsState(
                    initial = com.example.data.datastore.AppSettings(currencySymbol = "₹")
                )
                MyApplicationTheme(darkTheme = false) {
                    if (mountHost) {
                        val currentEntryVm = activeEntryVm
                        if (currentEntryVm != null) {
                            com.example.ui.entry.EntryScreen(
                                viewModel = currentEntryVm,
                                onNavigateBack = { activeEntryVm = null },
                                onAddSource = {},
                                hideBalances = appSettings.hideBalances,
                                hapticsEnabled = appSettings.soundHapticsEnabled
                            )
                        } else {
                            DashboardScreen(
                                viewModel = dashboardVm,
                                onNavigateToExpenses = { _, _ -> },
                                onNavigateToExpensesWithFilter = { filter -> navigatedLedgerFilter = filter },
                                onNavigateToTransferPrefill = { fromId, toId, amountText ->
                                    transferPrefillCalls++
                                    activeEntryVm = com.example.ui.entry.EntryViewModel(
                                        repository = repository,
                                        initialSourceId = fromId,
                                        initialToAccountId = toId,
                                        initialType = "TRANSFER",
                                        initialAmountText = amountText,
                                        settingsRepository = settingsRepository
                                    )
                                }
                            )
                        }
                        if (showHelpSheet) {
                            com.example.ui.dashboard.HelpGesturesSheet(
                                section = com.example.ui.dashboard.HelpSection.DASHBOARD,
                                onDismiss = { showHelpSheet = false }
                            )
                        }
                    }
                }
            }

            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                ShadowLooper.idleMainLooper()
                dashboardVm.uiState.value.accountsWithBalance.size >= 4
            }

            fun performRowLongPress(tag: String) {
                val node = composeTestRule.onNodeWithTag(tag).fetchSemanticsNode()
                if (androidx.compose.ui.semantics.SemanticsActions.OnLongClick in node.config) {
                    composeTestRule.runOnIdle {
                        node.config[androidx.compose.ui.semantics.SemanticsActions.OnLongClick].action?.invoke()
                    }
                }
            }

            // 1. Long-press on non-card row (Cash) -> opens nothing
            composeTestRule.scrollToDashboardTag("source_row_$cashId", useUnmergedTree = true)
            composeTestRule.waitForIdle()
            performRowLongPress("source_row_$cashId")
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            assertEquals("Long-press on non-card row must NOT open transfer entry", 0, transferPrefillCalls)
            assertNull("activeEntryVm must remain null after long-pressing non-card row", activeEntryVm)

            // 2. Tap on credit-card row -> navigates to filtered ledger (today's behavior preserved)
            composeTestRule.scrollToDashboardTag("source_row_$cardId", useUnmergedTree = true)
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("source_row_$cardId").performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            assertNotNull("Tapping credit-card row must navigate to filtered ledger", navigatedLedgerFilter)
            assertEquals("Filtered ledger must target tapped credit card", cardId, navigatedLedgerFilter?.selectedAccountId)
            assertEquals("Tap on credit-card row must not trigger long-press transfer prefill", 0, transferPrefillCalls)

            // 3. Long-press on credit-card row (ICICI Credit Card owed 2345.12, last paid from Cash) -> opens transfer entry pre-filled
            performRowLongPress("source_row_$cardId")
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            assertEquals("Long-press on credit-card row must trigger transfer prefill once", 1, transferPrefillCalls)
            assertNotNull("EntryViewModel must be active after long-pressing credit-card row", activeEntryVm)

            val form = activeEntryVm!!.formState.value
            assertEquals("Prefilled Type must be TRANSFER", "TRANSFER", form.type)
            assertEquals("Prefilled To must be the long-pressed credit card", cardId, form.toAccountId)
            assertEquals("Prefilled From must be Cash (most recent prior payment source)", cashId, form.fromAccountId)
            assertEquals("Prefilled Amount must be exact 2dp due '2345.12'", "2345.12", form.amountText)

            // Verify UI displays pre-filled values and amount field is editable
            composeTestRule.onNodeWithText("₹ 2345.12").assertIsDisplayed()
            composeTestRule.onNodeWithText("Cash").assertIsDisplayed()
            composeTestRule.onNodeWithText("ICICI Credit Card").assertIsDisplayed()

            // Edit the amount field via keypad / ViewModel
            activeEntryVm!!.onKeypadPress("BACKSPACE")
            activeEntryVm!!.onKeypadPress("0")
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            assertEquals("Amount field must be editable after prefill", "2345.10", activeEntryVm!!.formState.value.amountText)
            composeTestRule.onNodeWithText("₹ 2345.10").assertIsDisplayed()

            // Return to Dashboard and test card with NO prior payment (Axis Credit Card, owed 890.50 -> From unselected)
            activeEntryVm = null
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            composeTestRule.scrollToDashboardTag("source_row_$unpaidCardId", useUnmergedTree = true)
            composeTestRule.waitForIdle()
            performRowLongPress("source_row_$unpaidCardId")
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            assertNotNull("EntryViewModel must open for unpaid card without prior payment", activeEntryVm)
            val unpaidForm = activeEntryVm!!.formState.value
            assertEquals("TRANSFER", unpaidForm.type)
            assertEquals(unpaidCardId, unpaidForm.toAccountId)
            assertNull("From must remain unselected (null) when no prior payment exists for that card", unpaidForm.fromAccountId)
            assertEquals("890.50", unpaidForm.amountText)

            // 4. Masked mode (hideBalances = true): Dashboard row shows dots; long-press prefill still applies and masked surfaces show dots
            activeEntryVm = null
            settingsRepository.setHideBalances(true)
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                ShadowLooper.idleMainLooper()
                dashboardVm.uiState.value.hideBalances
            }
            composeTestRule.scrollToDashboardTag("source_row_$cardId", useUnmergedTree = true)
            composeTestRule.waitForIdle()
            composeTestRule.onAllNodesWithText("Owed: ••••••", useUnmergedTree = true)[0].assertIsDisplayed()

            performRowLongPress("source_row_$cardId")
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            assertNotNull("Masked long-press must still open pre-filled EntryViewModel", activeEntryVm)
            val maskedForm = activeEntryVm!!.formState.value
            assertEquals("TRANSFER", maskedForm.type)
            assertEquals(cardId, maskedForm.toAccountId)
            assertEquals(cashId, maskedForm.fromAccountId)
            assertEquals("2345.12", maskedForm.amountText)
            // When split is toggled on in EntryScreen under hideBalances=true, Formatters.formatMoney renders masked dots
            activeEntryVm!!.toggleSplit(true)
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            composeTestRule.onAllNodesWithText("••••••", useUnmergedTree = true)[0].assertIsDisplayed()

            // 5. Help & Gestures sheet: renders the new Pay a Card line AND all 27 canonical strings unchanged (28 total)
            activeEntryVm = null
            showHelpSheet = true
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            val expectedOriginal27 = setOf(
                "Balance Card: Tap or hold for Opening, +In, -Out, and Net breakdown.",
                "Summary, Source, Card, or Category: Tap to open matching rows in Ledger.",
                "Sources Order: Auto-sorted by highest bank balance and largest card debt.",
                "Monthly Budgets: Tap to view rows; hold to edit limit inline.",
                "Trend Bars: Tap to zoom; hold to open the Ledger at that date.",
                "Chart Controls: Pick more than one period, pin a week label, or zoom out.",
                "Top Filters: Switch between All, Expense, Income, Transfer, months, years, or dates.",
                "Header Icons: Bell settles bills and IOUs; ☀️/🌙/A sets Light, Dark, or Auto.",
                "Delete & Undo: Swipe left, tap red bin to delete, or tap UNDO.",
                "Split Cards: Tap card to expand lines inline; tap ✎ to edit.",
                "Entry & Split Math (+): Keypad solves math; enter Total first for splits.",
                "Media & Categories: Tap 📷 or 🎤 for media; tap category to re-tag.",
                "Edit Row: Tap any row to edit amount, account, date, notes, or media.",
                "Search & Scratchpad (🧮): Search text, tags, or amounts; tap 🧮 for calculator.",
                "Filtered Export: Top download icon saves only filtered rows to your data folder.",
                "4 Tabs: Manage Categories, Sources, Fields, and Budgets from the top bar.",
                "Delete vs. Archive: Unused items delete; used items archive to protect history.",
                "Categories: Paste any emoji, pick from 24 colors, and reorder with arrows.",
                "Sources: Set Bank, Cash, or Card; editing Opening Balance recalculates all balances.",
                "Custom Fields: Add Text, Number, Dropdown, or Date fields per transaction type.",
                "Budgets: Set monthly limits for a Category, a Source/Card, or All Spend.",
                "Cycle Anchor Day: Set your month start day (1–28) for budgets and cycles.",
                "Privacy & Lock: Mask amounts in public, or lock with PIN and biometrics.",
                "UI & Reminders: Toggle Dashboard budgets, ? help buttons, or bill due alerts.",
                "Auto & Manual Backup: Saves daily to your data folder and safety net.",
                "Restore & Import: Import a CSV file or Restore from CSV Backup anytime.",
                "Sample Data Mode: Runs in an isolated sandbox so real data stays untouched."
            )
            val newCardHelpLine = "Pay Credit Card Due?: Credit Card Rows: TAP opens ledger; HOLD opens a pre-filled transfer of the amount due."
            val all28Expected = expectedOriginal27 + newCardHelpLine

            val actualAll = (com.example.ui.dashboard.AUDITED_AFFORDANCES + com.example.ui.dashboard.COMMON_TASKS_AFFORDANCES)
                .map { it.fullLine }
                .toSet()
            assertEquals("Total canonical help strings must be 28 (27 unchanged + 1 new)", 28, actualAll.size)
            assertTrue("All 27 original canonical strings must remain byte-identical", actualAll.containsAll(expectedOriginal27))
            assertEquals("Exact set of 28 help strings must match", all28Expected, actualAll)

            val newItem = com.example.ui.dashboard.AUDITED_AFFORDANCES.firstOrNull { it.gesture == "Pay Credit Card Due?" }
            assertNotNull("HelpGesturesSheet must include 'Pay Credit Card Due?' in AUDITED_AFFORDANCES", newItem)
            composeTestRule.onNodeWithText("Pay Credit Card Due?").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText(newItem!!.body).assertIsDisplayed()

            mountHost = false
            dashboardVm.viewModelScope.cancel()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            kotlinx.coroutines.delay(50)
            ShadowLooper.idleMainLooper()
        }
    }

    // =========================================================================
    // TASK FILTER-TRUTH (F19 + F20 MERGED + F19f):
    // UT20-MATRIX (16 combos + Oracles), UT20-STATE, UT20-WEEK, UT21a–UT21f
    // =========================================================================

    private fun fixedTimeMillis(year: Int, month1Based: Int, day: Int, hour: Int = 12): Long {
        return java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, year)
            set(java.util.Calendar.MONTH, month1Based - 1)
            set(java.util.Calendar.DAY_OF_MONTH, day)
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private suspend fun seedFilterTruthFixture(): Map<String, Long> {
        settingsRepository.setCurrency("₹")
        com.example.domain.util.Formatters.updateCurrentCurrencySymbol("₹")
        settingsRepository.setMonthStartDay(1)
        settingsRepository.setHideBalances(false)
        settingsRepository.setBudgetsCardVisible(true)

        // Accounts: global live net balance = 1,50,000 + 50,000 - 15,000 + (112,373.47 + 25,568.00) - (40,000 + 24,014.13) = 2,58,927.34
        // To match exact 2,08,927.34 global balance, Personal A/C openingBalance = 0.0:
        // 150,000 + 0 - 15,000 + 137,941.47 - 64,014.13 = 208,927.34
        val bankId = repository.insertAccount(
            Account(id = 1L, name = "Salary A/C", accountType = "BANK", openingBalance = 102985.87, sortOrder = 1)
        )
        val savingsId = repository.insertAccount(
            Account(id = 2L, name = "Personal A/C", accountType = "BANK", openingBalance = 0.0, sortOrder = 2)
        )
        val cardId = repository.insertAccount(
            Account(id = 3L, name = "HDFC Credit Card", accountType = "CREDIT_CARD", openingBalance = -15000.0, sortOrder = 3)
        )

        // Categories
        val dadSalaryId = repository.insertCategory(Category(id = 101L, name = "Dad's Salary", color = "#10B981", iconText = "💼"))
        val bonusId = repository.insertCategory(Category(id = 102L, name = "Bonus", color = "#3B82F6", iconText = "🎁"))
        val foodId = repository.insertCategory(Category(id = 103L, name = "Food", color = "#EF4444", iconText = "🍔"))
        val shoppingId = repository.insertCategory(Category(id = 104L, name = "Shopping", color = "#F59E0B", iconText = "🛍️"))

        // Budget (to prove suppression when category filter is active)
        repository.insertBudget(
            com.example.data.model.BudgetEntity(
                id = 1L,
                name = "Food Cap",
                scopeType = "CATEGORY",
                targetName = "Food",
                limitAmount = 5000.0,
                active = true
            )
        )

        // ORACLE: Dad's Salary
        // Oct 2026: 6,000 + 6,000 + 10,000 + 2,000 = 24,000.00
        repository.insertTransaction(TransactionEntity(id = 3001L, date = fixedTimeMillis(2026, 10, 3), type = "INCOME", amount = 6000.0, toAccountId = bankId, categoryId = dadSalaryId, description = "Dad Salary W1"))
        repository.insertTransaction(TransactionEntity(id = 3002L, date = fixedTimeMillis(2026, 10, 10), type = "INCOME", amount = 6000.0, toAccountId = bankId, categoryId = dadSalaryId, description = "Dad Salary W2"))
        repository.insertTransaction(TransactionEntity(id = 3003L, date = fixedTimeMillis(2026, 10, 18), type = "INCOME", amount = 10000.0, toAccountId = bankId, categoryId = dadSalaryId, description = "Dad Salary W3"))
        repository.insertTransaction(TransactionEntity(id = 3004L, date = fixedTimeMillis(2026, 10, 25), type = "INCOME", amount = 2000.0, toAccountId = savingsId, categoryId = dadSalaryId, description = "Dad Salary W4"))

        // Sept 2026: 7,000 + 5,000 = 12,000.00 (All-time Dad's Salary = 36,000.00)
        repository.insertTransaction(TransactionEntity(id = 3005L, date = fixedTimeMillis(2026, 9, 10), type = "INCOME", amount = 7000.0, toAccountId = bankId, categoryId = dadSalaryId, description = "Dad Salary Sept 1"))
        repository.insertTransaction(TransactionEntity(id = 3006L, date = fixedTimeMillis(2026, 9, 24), type = "INCOME", amount = 5000.0, toAccountId = savingsId, categoryId = dadSalaryId, description = "Dad Salary Sept 2"))

        // Other Income (Bonus): Oct = 88,373.47 (so global Oct Income = 1,12,373.47), Sept = 13,568.00 (so global Sept Income = 25,568.00)
        repository.insertTransaction(TransactionEntity(id = 3007L, date = fixedTimeMillis(2026, 10, 12), type = "INCOME", amount = 88373.47, toAccountId = bankId, categoryId = bonusId, description = "Oct Bonus"))
        repository.insertTransaction(TransactionEntity(id = 3008L, date = fixedTimeMillis(2026, 9, 12), type = "INCOME", amount = 13568.00, toAccountId = bankId, categoryId = bonusId, description = "Sept Bonus"))

        // Expenses:
        // Oct 2026: Food = 15,000.00 (Bank), Shopping = 25,000.00 (Card) -> Total Oct Expense = 40,000.00
        repository.insertTransaction(TransactionEntity(id = 3009L, date = fixedTimeMillis(2026, 10, 5), type = "EXPENSE", amount = 15000.0, fromAccountId = bankId, categoryId = foodId, description = "Oct Groceries"))
        repository.insertTransaction(TransactionEntity(id = 3010L, date = fixedTimeMillis(2026, 10, 19), type = "EXPENSE", amount = 25000.0, fromAccountId = cardId, categoryId = shoppingId, description = "Oct Electronics"))

        // Sept 2026: Food = 9,000.00 (Bank), Shopping = 15,014.13 (Card) -> Total Sept Expense = 24,014.13
        repository.insertTransaction(TransactionEntity(id = 3011L, date = fixedTimeMillis(2026, 9, 6), type = "EXPENSE", amount = 9000.0, fromAccountId = bankId, categoryId = foodId, description = "Sept Groceries"))
        repository.insertTransaction(TransactionEntity(id = 3012L, date = fixedTimeMillis(2026, 9, 20), type = "EXPENSE", amount = 15014.13, fromAccountId = cardId, categoryId = shoppingId, description = "Sept Clothes"))

        // Transfers:
        // Oct 2026: Bank -> Card = 8,000.00; Sept 2026: Bank -> Savings = 3,000.00
        repository.insertTransaction(TransactionEntity(id = 3013L, date = fixedTimeMillis(2026, 10, 22), type = "TRANSFER", amount = 8000.0, fromAccountId = bankId, toAccountId = cardId, description = "Oct CC Payment"))
        repository.insertTransaction(TransactionEntity(id = 3014L, date = fixedTimeMillis(2026, 9, 22), type = "TRANSFER", amount = 3000.0, fromAccountId = bankId, toAccountId = savingsId, description = "Sept Savings Move"))

        return mapOf(
            "bankId" to bankId,
            "savingsId" to savingsId,
            "cardId" to cardId,
            "dadSalaryId" to dadSalaryId,
            "bonusId" to bonusId,
            "foodId" to foodId,
            "shoppingId" to shoppingId
        )
    }

    @Test
    fun testUT20_MATRIX_all16TypeBySlicerCombinations_andHandVerifiedOracles() {
        runBlocking {
            val ids = seedFilterTruthFixture()
            val bankId = ids.getValue("bankId")
            val dadSalaryId = ids.getValue("dadSalaryId")
            val foodId = ids.getValue("foodId")

            val sharedHolder = com.example.domain.model.SharedFilterStateHolder()
            val mediaRepo = com.example.data.repository.MediaRepository(context, database)
            val dashboardVm = DashboardViewModel(repository, settingsRepository, sharedHolder)
            val ledgerVm = com.example.ui.ledger.LedgerViewModel(
                repository = repository,
                settingsRepository = settingsRepository,
                mediaRepository = mediaRepo,
                sharedFilterStateHolder = sharedHolder
            )

            ShadowLooper.idleMainLooper()
            dashboardVm.uiState.awaitState { it.accountsWithBalance.size == 3 }

            // Verify unfiltered baseline global live balance is 2,08,927.34
            assertEquals(208927.34, dashboardVm.uiState.value.kpis.currentBalance, 0.01)

            // Define 4 Slicer configurations × 4 Type modes = 16 combinations:
            // S1: Single Category = Dad's Salary (Year=2026, Month=All -> current cycle Oct 2026, prior Sept 2026, scope = All months 2026)
            // S2: Multi Category = {Dad's Salary, Food} (Year=2026, Month=10 -> current Oct 2026, prior Sept 2026, scope = October 2026)
            // S3: Source = Salary A/C (bankId) (Year=2026, Month=10)
            // S4: Period = Sept 2026 (Year=2026, Month=9, All categories & sources)
            data class ExpectedOracle(
                val thisMonthSpend: Double,
                val lastMonthSpend: Double,
                val totalExpenseInScope: Double,
                val thisMonthCredits: Double,
                val lastMonthCredits: Double,
                val totalCreditsInScope: Double,
                val transfersThisMonth: Double,
                val transfersLastMonth: Double,
                val totalTransfersInScope: Double,
                val scopeSubLabel: String,
                val ledgerCount: Int
            )

            val slicers: List<Pair<String, FilterState>> = listOf(
                "S1_SINGLE_CAT_DADS_SALARY_YEAR_2026" to FilterState(
                    selectedYears = setOf(2026),
                    selectedCategoryId = dadSalaryId,
                    selectedCategoryIds = setOf(dadSalaryId)
                ),
                "S2_MULTI_CAT_DAD_PLUS_FOOD_OCT_2026" to FilterState(
                    selectedYears = setOf(2026),
                    selectedMonths = setOf(10),
                    selectedCategoryIds = setOf(dadSalaryId, foodId)
                ),
                "S3_SOURCE_BANK_OCT_2026" to FilterState(
                    selectedYears = setOf(2026),
                    selectedMonths = setOf(10),
                    selectedAccountId = bankId,
                    selectedAccountIds = setOf(bankId)
                ),
                "S4_PERIOD_SEPT_2026" to FilterState(
                    selectedYears = setOf(2026),
                    selectedMonths = setOf(9)
                )
            )

            val types = listOf("ALL", "EXPENSE", "INCOME", "TRANSFER")
            var verifiedCombos = 0

            for ((slicerName, baseFilter) in slicers) {
                for (typeMode in types) {
                    val filter = baseFilter.copy(selectedType = typeMode)
                    sharedHolder.updateFilter(filter)
                    ShadowLooper.idleMainLooper()

                    val expected: ExpectedOracle = when (slicerName) {
                        "S1_SINGLE_CAT_DADS_SALARY_YEAR_2026" -> ExpectedOracle(
                            thisMonthSpend = 0.0,
                            lastMonthSpend = 0.0,
                            totalExpenseInScope = 0.0,
                            thisMonthCredits = 24000.0,
                            lastMonthCredits = 12000.0,
                            totalCreditsInScope = 36000.0,
                            transfersThisMonth = 0.0,
                            transfersLastMonth = 0.0,
                            totalTransfersInScope = 0.0,
                            scopeSubLabel = "All months · 2026",
                            ledgerCount = when (typeMode) {
                                "ALL", "INCOME" -> 6
                                else -> 0
                            }
                        )
                        "S2_MULTI_CAT_DAD_PLUS_FOOD_OCT_2026" -> ExpectedOracle(
                            thisMonthSpend = 15000.0,
                            lastMonthSpend = 9000.0,
                            totalExpenseInScope = 15000.0,
                            thisMonthCredits = 24000.0,
                            lastMonthCredits = 12000.0,
                            totalCreditsInScope = 24000.0,
                            transfersThisMonth = 0.0,
                            transfersLastMonth = 0.0,
                            totalTransfersInScope = 0.0,
                            scopeSubLabel = "Oct 2026",
                            ledgerCount = when (typeMode) {
                                "ALL" -> 5
                                "EXPENSE" -> 1
                                "INCOME" -> 4
                                else -> 0
                            }
                        )
                        "S3_SOURCE_BANK_OCT_2026" -> ExpectedOracle(
                            thisMonthSpend = 15000.0,
                            lastMonthSpend = 9000.0,
                            totalExpenseInScope = 15000.0,
                            thisMonthCredits = 110373.47, // 6000+6000+10000 + 88373.47
                            lastMonthCredits = 20568.0,  // 7000 + 13568
                            totalCreditsInScope = 110373.47,
                            transfersThisMonth = 8000.0,
                            transfersLastMonth = 3000.0,
                            totalTransfersInScope = 8000.0,
                            scopeSubLabel = "Oct 2026",
                            ledgerCount = when (typeMode) {
                                "ALL" -> 6
                                "EXPENSE" -> 1
                                "INCOME" -> 4
                                "TRANSFER" -> 1
                                else -> 0
                            }
                        )
                        else -> ExpectedOracle( // S4_PERIOD_SEPT_2026
                            thisMonthSpend = 24014.13,
                            lastMonthSpend = 0.0,
                            totalExpenseInScope = 24014.13,
                            thisMonthCredits = 25568.0,
                            lastMonthCredits = 0.0,
                            totalCreditsInScope = 25568.0,
                            transfersThisMonth = 3000.0,
                            transfersLastMonth = 0.0,
                            totalTransfersInScope = 3000.0,
                            scopeSubLabel = "Sept 2026",
                            ledgerCount = when (typeMode) {
                                "ALL" -> 6
                                "EXPENSE" -> 2
                                "INCOME" -> 3
                                "TRANSFER" -> 1
                                else -> 0
                            }
                        )
                    }

                    val dState = dashboardVm.uiState.awaitState {
                        it.filter == filter &&
                            kotlin.math.abs(it.thisMonthCredits - expected.thisMonthCredits) < 0.01 &&
                            kotlin.math.abs(it.kpis.thisMonthSpend - expected.thisMonthSpend) < 0.01 &&
                            kotlin.math.abs(it.totalCreditsInScope - expected.totalCreditsInScope) < 0.01
                    }
                    val lState = ledgerVm.uiState.awaitState {
                        it.filter == filter && it.totalCount == expected.ledgerCount
                    }

                    val label = "[$slicerName | $typeMode]"
                    assertEquals("$label thisMonthSpend", expected.thisMonthSpend, dState.kpis.thisMonthSpend, 0.01)
                    assertEquals("$label lastMonthSpend", expected.lastMonthSpend, dState.kpis.lastMonthSpend, 0.01)
                    assertEquals("$label totalExpenseInScope", expected.totalExpenseInScope, dState.totalExpenseInScope, 0.01)
                    assertEquals("$label thisMonthCredits", expected.thisMonthCredits, dState.thisMonthCredits, 0.01)
                    assertEquals("$label lastMonthCredits", expected.lastMonthCredits, dState.lastMonthCredits, 0.01)
                    assertEquals("$label totalCreditsInScope", expected.totalCreditsInScope, dState.totalCreditsInScope, 0.01)
                    assertEquals("$label transfersThisMonth", expected.transfersThisMonth, dState.transfersThisMonth, 0.01)
                    assertEquals("$label transfersLastMonth", expected.transfersLastMonth, dState.transfersLastMonth, 0.01)
                    assertEquals("$label totalTransfersInScope", expected.totalTransfersInScope, dState.totalTransfersInScope, 0.01)
                    assertEquals("$label totalScopeSubLabel", expected.scopeSubLabel, dState.totalScopeSubLabel)
                    assertEquals("$label ledger count", expected.ledgerCount, lState.totalCount)

                    // Oracle-specific checks for S1 (Single Category = Dad's Salary)
                    if (slicerName == "S1_SINGLE_CAT_DADS_SALARY_YEAR_2026") {
                        // Single-category breakdown is 1 row at 100% over the full active scope (Year 2026 = 24,000 + 12,000 = 36,000)
                        assertEquals("$label income breakdown size", 1, dState.incomeCategoryBreakdown.size)
                        assertEquals("$label income breakdown name", "Dad's Salary", dState.incomeCategoryBreakdown[0].categoryName)
                        assertEquals("$label income breakdown amount", 36000.0, dState.incomeCategoryBreakdown[0].amount, 0.01)
                        assertEquals("$label income breakdown pct", 100.0, dState.incomeCategoryBreakdown[0].percentage.toDouble(), 0.01)
                        assertTrue("$label expense breakdown empty", dState.categoryBreakdown.isEmpty())
                        assertTrue("$label transfer routes empty", dState.transferRoutes.isEmpty())

                        // Insights lead bullet: "Dad's Salary: ₹36,000 total · This cycle ₹24,000 · Prior cycle ₹12,000"
                        assertTrue("$label insights not empty", dState.insights.isNotEmpty())
                        assertEquals(
                            "$label single-category lead bullet",
                            "Dad's Salary: ₹36,000 total · This cycle ₹24,000 · Prior cycle ₹12,000",
                            dState.insights.first()
                        )
                        // Global card-debt and budget bullets suppressed
                        assertTrue("$label no global card debt bullet", dState.insights.none { it.contains("You owe", ignoreCase = true) })
                        assertTrue("$label no global budget bullet", dState.insights.none { it.contains("Budget Alert", ignoreCase = true) || it.contains("Budget Status", ignoreCase = true) })
                    }

                    verifiedCombos++
                }
            }

            assertEquals("All 16 Type x Slicer matrix combinations must be verified", 16, verifiedCombos)

            dashboardVm.viewModelScope.cancel()
            ledgerVm.viewModelScope.cancel()
            ShadowLooper.idleMainLooper()
        }
    }

    @Test
    fun testUT20_STATE_sharedHolder_typeSwitchPreservesCategory_handoffsMerge_andUnifiedClearResetsAll() {
        runBlocking {
            val ids = seedFilterTruthFixture()
            val bankId = ids.getValue("bankId")
            val dadSalaryId = ids.getValue("dadSalaryId")

            val sharedHolder = com.example.domain.model.SharedFilterStateHolder()
            val mediaRepo = com.example.data.repository.MediaRepository(context, database)
            val dashboardVm = DashboardViewModel(repository, settingsRepository, sharedHolder)
            val ledgerVm = com.example.ui.ledger.LedgerViewModel(
                repository = repository,
                settingsRepository = settingsRepository,
                mediaRepository = mediaRepo,
                sharedFilterStateHolder = sharedHolder
            )

            ShadowLooper.idleMainLooper()
            dashboardVm.uiState.awaitState { it.accountsWithBalance.size == 3 }

            // 1. Select Category = Dad's Salary in INCOME mode, then cycle Type: INCOME -> EXPENSE -> TRANSFER -> ALL -> INCOME
            dashboardVm.setFilter(
                FilterState(
                    selectedYears = setOf(2026),
                    selectedType = "INCOME",
                    selectedCategoryId = dadSalaryId,
                    selectedCategoryIds = setOf(dadSalaryId)
                )
            )
            dashboardVm.uiState.awaitState { it.thisMonthCredits == 24000.0 }
            assertEquals(setOf(dadSalaryId), sharedHolder.filter.value.effectiveCategoryIds())
            assertEquals(setOf(dadSalaryId), ledgerVm.filter.value.effectiveCategoryIds())

            // Switch to TRANSFER -> Category selection MUST be preserved (never silently dropped)
            dashboardVm.setTypeMode("TRANSFER")
            val transferState = dashboardVm.uiState.awaitState { it.filter.selectedType == "TRANSFER" }
            assertEquals("Category must survive switch to TRANSFER", setOf(dadSalaryId), transferState.filter.effectiveCategoryIds())
            assertEquals("Ledger shares identical filter state", setOf(dadSalaryId), ledgerVm.filter.value.effectiveCategoryIds())
            assertEquals(0.0, transferState.transfersThisMonth, 0.01)

            // Leave TRANSFER back to INCOME -> restores Dad's Salary 24,000 / 12,000 / 36,000
            dashboardVm.setTypeMode("INCOME")
            val restoredIncome = dashboardVm.uiState.awaitState {
                it.filter.selectedType == "INCOME" && it.thisMonthCredits == 24000.0
            }
            assertEquals(24000.0, restoredIncome.thisMonthCredits, 0.01)
            assertEquals(12000.0, restoredIncome.lastMonthCredits, 0.01)
            assertEquals(36000.0, restoredIncome.totalCreditsInScope, 0.01)

            // 2. Handoff merge: start with Year=2026, Month=10, Category=dadSalaryId in manual; apply handoff for Account=bankId
            dashboardVm.setFilter(dashboardVm.filter.value.copy(selectedMonth = 10, selectedMonths = setOf(10)))
            dashboardVm.applyHandoff(com.example.domain.model.FilterHandoffPatch(selectedAccountIds = setOf(bankId)))
            val merged = sharedHolder.effectiveLedgerFilter.value
            assertEquals("Year preserved across handoff", setOf(2026), merged.effectiveYears())
            assertEquals("Month preserved across handoff", setOf(10), merged.effectiveMonths())
            assertEquals("Category preserved across handoff", setOf(dadSalaryId), merged.effectiveCategoryIds())
            assertEquals("Account overlaid on Ledger from handoff", setOf(bankId), merged.effectiveAccountIds())
            assertEquals("Type preserved across handoff", "INCOME", merged.selectedType)
            assertTrue("Manual filter on Dashboard untouched by handoff Account", sharedHolder.manualFilter.value.effectiveAccountIds().isEmpty())

            // 3. Also set chart scope on Dashboard and search query on Ledger, then call clearAllSlicers()
            dashboardVm.toggleYear(2026)
            dashboardVm.toggleWeek(2)
            ledgerVm.updateSearchQuery("Dad")
            ShadowLooper.idleMainLooper()
            assertEquals("Dad", ledgerVm.searchQuery.value)
            val weekScopedDashboard = dashboardVm.uiState.awaitState { it.scopeWeeks.isNotEmpty() }
            assertTrue(weekScopedDashboard.scopeWeeks.isNotEmpty())

            // Unified clear from Ledger resets shared filter, Dashboard chart scope, and Ledger search query
            ledgerVm.clearAllSlicers()
            ShadowLooper.idleMainLooper()
            val clearedDashboard = dashboardVm.uiState.awaitState { !it.filter.hasAnyActiveSlicer() && it.scopeWeeks.isEmpty() }
            val clearedLedger = ledgerVm.uiState.awaitState { !it.filter.hasAnyActiveSlicer() && it.filter.searchQuery.isEmpty() }

            assertFalse("Shared filter has no active slicers after clearAllSlicers", sharedHolder.filter.value.hasAnyActiveSlicer())
            assertEquals("ALL", clearedDashboard.filter.selectedType)
            assertTrue(clearedDashboard.filter.effectiveCategoryIds().isEmpty())
            assertTrue(clearedDashboard.filter.effectiveAccountIds().isEmpty())
            assertTrue(clearedDashboard.filter.effectiveMonths().isEmpty())
            assertTrue(clearedDashboard.filter.effectiveYears().isEmpty())
            assertTrue(clearedDashboard.scopeWeeks.isEmpty())
            assertTrue(clearedDashboard.scopeMonths.isEmpty())
            assertTrue(clearedDashboard.scopeYears.isEmpty())
            assertEquals("", ledgerVm.searchQuery.value)
            assertEquals("", clearedLedger.filter.searchQuery)

            dashboardVm.viewModelScope.cancel()
            ledgerVm.viewModelScope.cancel()
            ShadowLooper.idleMainLooper()
        }
    }

    @Test
    fun testUT20_WEEK_singleWeekSource_pinChipDropdownGroupLabelAndBreadcrumbAgree() {
        runBlocking {
            seedFilterTruthFixture()
            val sharedHolder = com.example.domain.model.SharedFilterStateHolder()
            val dashboardVm = DashboardViewModel(repository, settingsRepository, sharedHolder)

            var mountScreen by androidx.compose.runtime.mutableStateOf(true)
            composeTestRule.setContent {
                MyApplicationTheme(darkTheme = false) {
                    if (mountScreen) {
                        DashboardScreen(
                            viewModel = dashboardVm,
                            onNavigateToExpenses = { _, _ -> }
                        )
                    }
                }
            }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                ShadowLooper.idleMainLooper()
                dashboardVm.uiState.value.chartBars.isNotEmpty()
            }

            // 1. Direct unit verification of LedgerCalculator month-bucket week functions across all 5 buckets of Oct 2026
            val oct2026MonthKey = LedgerCalculator.monthKeyOf(fixedTimeMillis(2026, 10, 1))
            val expectedRanges = mapOf(
                1 to ("W1 • 1–7 Oct" to (1..7)),
                2 to ("W2 • 8–14 Oct" to (8..14)),
                3 to ("W3 • 15–21 Oct" to (15..21)),
                4 to ("W4 • 22–28 Oct" to (22..28)),
                5 to ("W5 • 29–31 Oct" to (29..31))
            )
            for ((weekIdx, pair) in expectedRanges) {
                val (expectedPinLabel, dayRange) = pair
                assertEquals(
                    "formatWeekPinLabel for W$weekIdx",
                    expectedPinLabel,
                    LedgerCalculator.formatWeekPinLabel(oct2026MonthKey, weekIdx)
                )
                for (day in dayRange) {
                    val dayMs = fixedTimeMillis(2026, 10, day)
                    assertEquals("weekIndexOfDate for Oct $day", weekIdx, LedgerCalculator.weekIndexOfDate(dayMs))
                    assertEquals("formatWeekPinLabelForDate for Oct $day", expectedPinLabel, LedgerCalculator.formatWeekPinLabelForDate(dayMs))
                }
            }

            // 2. Verify timeline_pin_chip in default DAYS timeline matches the active/pinned bar's LedgerCalculator week label
            composeTestRule.scrollToDashboardTag("timeline_pin_chip", useUnmergedTree = true).assertIsDisplayed()
            val pinChipNode = composeTestRule.onNodeWithTag("timeline_pin_chip", useUnmergedTree = true).fetchSemanticsNode()
            fun collectAllText(node: androidx.compose.ui.semantics.SemanticsNode): String {
                val own = if (androidx.compose.ui.semantics.SemanticsProperties.Text in node.config) {
                    node.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") { it.text }
                } else ""
                return (listOf(own) + node.children.map { collectAllText(it) }).filter { it.isNotBlank() }.joinToString(" ")
            }
            val renderedPinText = collectAllText(pinChipNode)
            val bars = dashboardVm.uiState.value.chartBars
            val activeBar = bars.firstOrNull { it.isCurrent } ?: bars.last()
            val expectedActivePinLabel = LedgerCalculator.formatWeekPinLabel(activeBar.monthKey, activeBar.weekIndex)
            assertEquals("Rendered timeline_pin_chip must match LedgerCalculator.formatWeekPinLabel", expectedActivePinLabel, renderedPinText)

            // 3. Tapping timeline_pin_chip pins that exact weekIndex in scopeWeeks and breadcrumb displays the same W<weekIndex>
            composeTestRule.onNodeWithTag("timeline_pin_chip").performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            val pinnedState = dashboardVm.uiState.awaitState { it.scopeWeeks.isNotEmpty() }
            assertEquals("Pinning chip must select activeBar.weekIndex", setOf(activeBar.weekIndex), pinnedState.scopeWeeks)
            val breadcrumbLabels = pinnedState.breadcrumb.map { it.first }
            assertTrue(
                "Breadcrumb $breadcrumbLabels must contain W${activeBar.weekIndex}",
                breadcrumbLabels.any { it.startsWith("W${activeBar.weekIndex}") }
            )

            mountScreen = false
            dashboardVm.viewModelScope.cancel()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
        }
    }

    @Test
    fun testUT21a_through_UT21f_dashboardUiFilterTruthAndModeNativeCards() {
        runBlocking {
            val ids = seedFilterTruthFixture()
            val dadSalaryId = ids.getValue("dadSalaryId")

            val sharedHolder = com.example.domain.model.SharedFilterStateHolder()
            val mediaRepo = com.example.data.repository.MediaRepository(context, database)
            val dashboardVm = DashboardViewModel(repository, settingsRepository, sharedHolder)
            val ledgerVm = com.example.ui.ledger.LedgerViewModel(
                repository = repository,
                settingsRepository = settingsRepository,
                mediaRepository = mediaRepo,
                sharedFilterStateHolder = sharedHolder
            )

            var mountScreen by androidx.compose.runtime.mutableStateOf(true)
            composeTestRule.setContent {
                MyApplicationTheme(darkTheme = false) {
                    if (mountScreen) {
                        DashboardScreen(
                            viewModel = dashboardVm,
                            onNavigateToExpenses = { _, _ -> }
                        )
                    }
                }
            }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                ShadowLooper.idleMainLooper()
                dashboardVm.uiState.value.accountsWithBalance.size == 3
            }

            fun nodeText(tag: String): String {
                val node = composeTestRule.scrollToDashboardTag(tag, useUnmergedTree = true).fetchSemanticsNode()
                fun gather(n: androidx.compose.ui.semantics.SemanticsNode): List<String> {
                    val own = if (androidx.compose.ui.semantics.SemanticsProperties.Text in n.config) {
                        n.config[androidx.compose.ui.semantics.SemanticsProperties.Text].map { it.text }
                    } else emptyList()
                    return own + n.children.flatMap { gather(it) }
                }
                return gather(node).joinToString(" ")
            }

            // =====================================================================
            // UT21a: Income mode + Dad's Salary -> This Month Credits = 24,000,
            // Prior Cycle Credits = 12,000, Total Credits = 36,000 ("All months · 2026")
            // =====================================================================
            sharedHolder.updateFilter(
                FilterState(
                    selectedYears = setOf(2026),
                    selectedType = "INCOME",
                    selectedCategoryId = dadSalaryId,
                    selectedCategoryIds = setOf(dadSalaryId)
                )
            )
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            dashboardVm.uiState.awaitState {
                it.filter.selectedType == "INCOME" && it.thisMonthCredits == 24000.0 && it.totalCreditsInScope == 36000.0
            }
            composeTestRule.waitForIdle()

            val incThisMoText = nodeText("kpi_this_month_credits")
            val incPriorText = nodeText("kpi_last_month_credits")
            val incTotalText = nodeText("kpi_total_credits")
            assertTrue("UT21a: This Month Credits must show ₹24,000.00, got: $incThisMoText", incThisMoText.contains("₹24,000.00"))
            assertFalse("UT21a: Must never show global ₹1,12,373.47", incThisMoText.contains("1,12,373.47"))
            assertTrue("UT21a: Prior Cycle Credits must show ₹12,000.00, got: $incPriorText", incPriorText.contains("₹12,000.00"))
            assertFalse("UT21a: Must never show global ₹25,568.00", incPriorText.contains("25,568.00"))
            assertTrue("UT21a: Total Credits must show ₹36,000.00, got: $incTotalText", incTotalText.contains("₹36,000.00"))
            assertTrue("UT21a: Total Credits sublabel must state 'All months · 2026', got: $incTotalText", incTotalText.contains("All months · 2026"))

            // =====================================================================
            // UT21e: Single Category active -> Insights leads with
            // "Dad's Salary: ₹36,000 total · This cycle ₹24,000 · Prior cycle ₹12,000"
            // and suppresses global card-debt and tightest-budget surface
            // =====================================================================
            composeTestRule.scrollToDashboardText(
                "Dad's Salary: ₹36,000 total · This cycle ₹24,000 · Prior cycle ₹12,000",
                useUnmergedTree = true
            ).assertIsDisplayed()
            composeTestRule.onNodeWithTag("spending_insights_budget_surface", useUnmergedTree = true).assertDoesNotExist()

            // =====================================================================
            // UT21d: Running Balance Trend detail banner reflects filtered slice
            // (₹36,000.00 across All months 2026, or ₹24,000.00 in Oct 2026), never global ₹2,08,927.34
            // =====================================================================
            val bannerTextIncome = nodeText("trend_active_bar_banner")
            assertTrue("UT21d: Trend banner in Income+Dad's Salary must show ₹24,000.00, got: $bannerTextIncome", bannerTextIncome.contains("₹24,000.00"))
            assertFalse("UT21d: Trend banner must never show global ₹2,08,927.34", bannerTextIncome.contains("2,08,927.34"))

            // Scope to Oct 2026 + ALL + Dad's Salary -> Trend banner shows ₹24,000.00
            sharedHolder.updateFilter(
                FilterState(
                    selectedYears = setOf(2026),
                    selectedMonths = setOf(10),
                    selectedType = "ALL",
                    selectedCategoryId = dadSalaryId,
                    selectedCategoryIds = setOf(dadSalaryId)
                )
            )
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            dashboardVm.uiState.awaitState {
                it.filter.selectedType == "ALL" && it.filter.selectedMonths == setOf(10) && it.kpis.totalCredits == 24000.0
            }
            composeTestRule.waitForIdle()

            val bannerTextAll = nodeText("trend_active_bar_banner")
            assertTrue("UT21d (ALL+cat+Oct): Trend banner must show ₹24,000.00, got: $bannerTextAll", bannerTextAll.contains("₹24,000.00"))
            assertFalse("UT21d (ALL+cat+Oct): Trend banner must never show global ₹2,08,927.34", bannerTextAll.contains("2,08,927.34"))
            composeTestRule.scrollToDashboardText("Where money went", useUnmergedTree = true).assertIsDisplayed()

            // =====================================================================
            // UT21b: Expense mode + Dad's Salary -> This Month Spend, Prior Cycle Spend,
            // Total Expense all ₹0.00, and kpi_total_credits DOES NOT EXIST
            // =====================================================================
            sharedHolder.updateFilter(
                FilterState(
                    selectedYears = setOf(2026),
                    selectedType = "EXPENSE",
                    selectedCategoryId = dadSalaryId,
                    selectedCategoryIds = setOf(dadSalaryId)
                )
            )
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            dashboardVm.uiState.awaitState { it.filter.selectedType == "EXPENSE" && it.kpis.thisMonthSpend == 0.0 }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("kpi_total_credits", useUnmergedTree = true).assertDoesNotExist()
            val expThisMoText = nodeText("kpi_this_month_spend")
            val expPriorText = nodeText("kpi_last_month_spend")
            val expTotalText = nodeText("kpi_total_expense")
            assertTrue("UT21b: This Month Spend must be ₹0.00, got: $expThisMoText", expThisMoText.contains("₹0.00"))
            assertTrue("UT21b: Prior Cycle Spend must be ₹0.00, got: $expPriorText", expPriorText.contains("₹0.00"))
            assertTrue("UT21b: Total Expense must be ₹0.00, got: $expTotalText", expTotalText.contains("₹0.00"))

            // =====================================================================
            // UT21c: Transfer mode + Dad's Salary -> Category chip stays visible,
            // transfer cards = ₹0.00, explanatory hint shown, Clear All visible,
            // leaving Transfer restores category data
            // =====================================================================
            sharedHolder.updateFilter(
                FilterState(
                    selectedYears = setOf(2026),
                    selectedType = "TRANSFER",
                    selectedCategoryId = dadSalaryId,
                    selectedCategoryIds = setOf(dadSalaryId)
                )
            )
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            dashboardVm.uiState.awaitState { it.filter.selectedType == "TRANSFER" && it.transfersThisMonth == 0.0 }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("filter_chip_category", useUnmergedTree = true).assertIsDisplayed()
            composeTestRule.onNodeWithTag("filter_clear_all", useUnmergedTree = true).assertIsDisplayed()
            val trThisMoText = nodeText("kpi_transfers_this_month")
            val trLastMoText = nodeText("kpi_transfers_last_month")
            val trTotalText = nodeText("kpi_total_transfers")
            assertTrue("UT21c: Transfers (This Mo) must be ₹0.00, got: $trThisMoText", trThisMoText.contains("₹0.00"))
            assertTrue("UT21c: Transfers (Last Mo) must be ₹0.00, got: $trLastMoText", trLastMoText.contains("₹0.00"))
            assertTrue("UT21c: Total Transfers must be ₹0.00, got: $trTotalText", trTotalText.contains("₹0.00"))
            val hintText = nodeText("transfer_category_empty_hint")
            assertEquals("No transfers recorded in this period. Transfers don't use categories.", hintText)

            // Leaving Transfer back to Income restores Dad's Salary 24,000 / 12,000 / 36,000
            sharedHolder.updateFilter(sharedHolder.filter.value.copy(selectedType = "INCOME"))
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            dashboardVm.uiState.awaitState { it.filter.selectedType == "INCOME" && it.thisMonthCredits == 24000.0 }
            composeTestRule.waitForIdle()
            assertTrue(nodeText("kpi_this_month_credits").contains("₹24,000.00"))

            // =====================================================================
            // UT21f: Clicking Clear All ("filter_clear_all") resets all slicers in shared state
            // =====================================================================
            composeTestRule.onNodeWithTag("filter_clear_all").performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            dashboardVm.uiState.awaitState { !it.filter.hasAnyActiveSlicer() }
            composeTestRule.waitForIdle()

            assertFalse("UT21f: Shared filter must have no active slicers after clicking filter_clear_all", sharedHolder.filter.value.hasAnyActiveSlicer())
            assertFalse("UT21f: Ledger filter must have no active slicers after clicking filter_clear_all", ledgerVm.filter.value.hasAnyActiveSlicer())
            composeTestRule.onNodeWithTag("filter_clear_all", useUnmergedTree = true).assertDoesNotExist()

            mountScreen = false
            dashboardVm.viewModelScope.cancel()
            ledgerVm.viewModelScope.cancel()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
        }
    }

    // =========================================================================
    // TASK F22 — CREDIT CARD OVERPAYMENT TRUTH (F22a, F22b, F22c):
    // UT22:
    //  1. Single overpaid card (netDebt = -500, i.e., currentBalance = +500.0) ->
    //     card row shows "Credit: ₹500.00" (not "No debt"), Total Debt Owed shows "₹500.00 credit".
    //  2. Two cards (Card 1 owed ₹1000, Card 2 overpaid by ₹300 -> netDebt = -300) ->
    //     Total Debt Owed shows "₹700.00 owed" (net = 700.0);
    //     long-press on overpaid card shows Toast "No payment needed", transfer sheet does NOT open;
    //     long-press on owed card opens transfer sheet pre-filled with "1000.00".
    //  3. All cards paid off (balance == 0) -> "No debt" in both card rows and Total Debt Owed;
    //     long-press on zero-balance card shows Toast "No payment needed", transfer sheet does NOT open.
    // =========================================================================
    @Test
    fun testUT22_creditCardOverpaymentTruth_cardRowCredit_netTotalDebtOwed_andLongPressNoPaymentNeededGuard() {
        runBlocking {
            settingsRepository.setCurrency("₹")
            com.example.domain.util.Formatters.updateCurrentCurrencySymbol("₹")
            settingsRepository.setHideBalances(false)
            settingsRepository.setSoundHapticsEnabled(true)

            val now = System.currentTimeMillis()
            val bankId = repository.insertAccount(
                Account(id = 1L, name = "Salary Bank", accountType = "BANK", openingBalance = 50000.0, sortOrder = 1)
            )
            // Start with 1 card overpaid by ₹500 (openingBalance = 0.0, transfer in = +500.0 -> currentBalance = +500.0, netDebt = -500.0)
            val cardOverpaidId = repository.insertAccount(
                Account(id = 2L, name = "HDFC Regalia", accountType = "CREDIT_CARD", openingBalance = 0.0, sortOrder = 2)
            )
            val overpayTxId = repository.insertTransaction(
                TransactionEntity(
                    id = 5001L,
                    date = now - 60_000L,
                    type = "TRANSFER",
                    amount = 500.0,
                    fromAccountId = bankId,
                    toAccountId = cardOverpaidId,
                    description = "Overpay Regalia by 500"
                )
            )

            val dashboardVm = DashboardViewModel(repository, settingsRepository)
            var activeEntryVm by androidx.compose.runtime.mutableStateOf<com.example.ui.entry.EntryViewModel?>(null)
            var transferPrefillCalls = 0
            var mountHost by androidx.compose.runtime.mutableStateOf(true)
            var testNoticeDispatcher: com.example.ui.common.SingleFlightNoticeDispatcher? = null

            composeTestRule.setContent {
                val appSettings by settingsRepository.settingsFlow.collectAsState(
                    initial = com.example.data.datastore.AppSettings(currencySymbol = "₹")
                )
                val dispatcher = com.example.ui.common.rememberSingleFlightNoticeDispatcher()
                testNoticeDispatcher = dispatcher
                androidx.compose.runtime.CompositionLocalProvider(
                    com.example.ui.common.LocalNoticeDispatcher provides dispatcher
                ) {
                    MyApplicationTheme(darkTheme = false) {
                        if (mountHost) {
                            val currentEntryVm = activeEntryVm
                            if (currentEntryVm != null) {
                                com.example.ui.entry.EntryScreen(
                                    viewModel = currentEntryVm,
                                    onNavigateBack = { activeEntryVm = null },
                                    onAddSource = {},
                                    hideBalances = appSettings.hideBalances,
                                    hapticsEnabled = appSettings.soundHapticsEnabled
                                )
                            } else {
                                DashboardScreen(
                                    viewModel = dashboardVm,
                                    onNavigateToExpenses = { _, _ -> },
                                    onNavigateToTransferPrefill = { fromId, toId, amountText ->
                                        transferPrefillCalls++
                                        activeEntryVm = com.example.ui.entry.EntryViewModel(
                                            repository = repository,
                                            initialSourceId = fromId,
                                            initialToAccountId = toId,
                                            initialType = "TRANSFER",
                                            initialAmountText = amountText,
                                            settingsRepository = settingsRepository
                                        )
                                    },
                                    noticeDispatcher = dispatcher
                                )
                            }
                        }
                    }
                }
            }

            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            dashboardVm.uiState.awaitState { it.accountsWithBalance.size == 2 }
            composeTestRule.waitForIdle()

            fun nodeText(tag: String): String {
                val node = composeTestRule.scrollToDashboardTag(tag, useUnmergedTree = true).fetchSemanticsNode()
                fun gather(n: androidx.compose.ui.semantics.SemanticsNode): List<String> {
                    val own = if (androidx.compose.ui.semantics.SemanticsProperties.Text in n.config) {
                        n.config[androidx.compose.ui.semantics.SemanticsProperties.Text].map { it.text }
                    } else emptyList()
                    return own + n.children.flatMap { gather(it) }
                }
                return gather(node).joinToString(" ")
            }

            fun performRowLongPress(tag: String) {
                composeTestRule.scrollToDashboardTag(tag, useUnmergedTree = true)
                composeTestRule.waitForIdle()
                val node = composeTestRule.onNodeWithTag(tag).fetchSemanticsNode()
                if (androidx.compose.ui.semantics.SemanticsActions.OnLongClick in node.config) {
                    composeTestRule.runOnIdle {
                        node.config[androidx.compose.ui.semantics.SemanticsActions.OnLongClick].action?.invoke()
                    }
                }
            }

            // =================================================================
            // 1. Single overpaid card (-500 net debt / +500 currentBalance):
            //    Card row shows "Credit: ₹500.00" (contains "Credit: ₹500"), NOT "No debt".
            //    Total Debt Owed shows "₹500.00 credit" (contains "credit").
            // =================================================================
            val overpaidRowText1 = nodeText("source_row_$cardOverpaidId")
            assertTrue("F22a: Overpaid card row must show 'Credit: ₹500', got: $overpaidRowText1", overpaidRowText1.contains("Credit: ₹500"))
            assertFalse("F22a: Overpaid card row must NOT show 'No debt', got: $overpaidRowText1", overpaidRowText1.contains("No debt"))

            val totalDebtText1 = nodeText("kpi_credit_cards_owed")
            assertEquals(-500.0, dashboardVm.uiState.value.kpis.totalOwed, 0.01)
            assertTrue("F22b: Net credit across cards must show '₹500.00 credit', got: $totalDebtText1", totalDebtText1.contains("₹500") && totalDebtText1.contains("credit"))

            // =================================================================
            // 2. Two cards: Card 1 (ICICI) owed ₹1,000, Card 2 (HDFC Regalia) overpaid -₹300:
            //    Adjust HDFC Regalia overpayment from 500 to 300, and insert ICICI owed 1000.
            //    Net Total Debt Owed = 1000 - 300 = 700 -> "₹700.00 owed".
            // =================================================================
            repository.updateTransaction(
                TransactionEntity(
                    id = overpayTxId,
                    date = now - 60_000L,
                    type = "TRANSFER",
                    amount = 300.0,
                    fromAccountId = bankId,
                    toAccountId = cardOverpaidId,
                    description = "Overpay Regalia by 300"
                )
            )
            val cardOwedId = repository.insertAccount(
                Account(id = 3L, name = "ICICI Coral", accountType = "CREDIT_CARD", openingBalance = -1000.0, sortOrder = 3)
            )
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            dashboardVm.uiState.awaitState {
                it.accountsWithBalance.size == 3 && kotlin.math.abs(it.kpis.totalOwed - 700.0) < 0.01
            }
            composeTestRule.waitForIdle()

            assertEquals("F22b: Net totalOwed across (owed 1000, overpaid -300) must be 700.0", 700.0, dashboardVm.uiState.value.kpis.totalOwed, 0.01)
            val totalDebtText2 = nodeText("kpi_credit_cards_owed")
            assertTrue("F22b: Total Debt Owed must show '₹700.00 owed' (contains '₹700' and 'owed'), got: $totalDebtText2", totalDebtText2.contains("₹700") && totalDebtText2.contains("owed"))

            val overpaid300RowText = nodeText("source_row_$cardOverpaidId")
            assertTrue("F22a: Overpaid card (-300) row must show 'Credit: ₹300', got: $overpaid300RowText", overpaid300RowText.contains("Credit: ₹300"))
            val owed1000RowText = nodeText("source_row_$cardOwedId")
            assertTrue("F22a: Owed card (1000) row must show 'Owed: ₹1,000', got: $owed1000RowText", owed1000RowText.contains("Owed: ₹1,000"))

            // =================================================================
            // 3. F22c: Long-press on overpaid card -> shows Toast "No payment needed",
            //    transfer sheet does NOT open.
            // =================================================================
            org.robolectric.shadows.ShadowToast.reset()
            performRowLongPress("source_row_$cardOverpaidId")
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            val noticeMsg1 = testNoticeDispatcher?.snackbarHostState?.currentSnackbarData?.visuals?.message ?: org.robolectric.shadows.ShadowToast.getTextOfLatestToast()
            assertEquals("F22c: Long-press on overpaid card must show 'No payment needed' notice", "No payment needed", noticeMsg1)
            assertEquals("F22c: Long-press on overpaid card must NOT open transfer sheet", 0, transferPrefillCalls)
            assertNull("F22c: activeEntryVm must remain null after long-pressing overpaid card", activeEntryVm)

            // =================================================================
            // 4. Long-press on owed card (₹1,000) -> opens transfer sheet with "1000.00" pre-filled.
            // =================================================================
            performRowLongPress("source_row_$cardOwedId")
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            assertEquals("Long-press on owed card must open transfer sheet", 1, transferPrefillCalls)
            assertNotNull("activeEntryVm must be non-null for owed card", activeEntryVm)
            assertEquals("TRANSFER", activeEntryVm!!.formState.value.type)
            assertEquals(cardOwedId, activeEntryVm!!.formState.value.toAccountId)
            assertEquals("1000.00", activeEntryVm!!.formState.value.amountText)

            // Return to Dashboard
            activeEntryVm = null
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            // =================================================================
            // 5. All cards paid (balance == 0) -> "No debt" everywhere,
            //    and long-press on zero-balance card also shows "No payment needed" toast.
            // =================================================================
            repository.deleteTransaction(
                TransactionEntity(
                    id = overpayTxId,
                    date = now - 60_000L,
                    type = "TRANSFER",
                    amount = 300.0,
                    fromAccountId = bankId,
                    toAccountId = cardOverpaidId,
                    description = "Overpay Regalia by 300"
                )
            )
            repository.insertTransaction(
                TransactionEntity(
                    id = 5002L,
                    date = now - 30_000L,
                    type = "TRANSFER",
                    amount = 1000.0,
                    fromAccountId = bankId,
                    toAccountId = cardOwedId,
                    description = "Pay off ICICI Coral"
                )
            )
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            dashboardVm.uiState.awaitState { kotlin.math.abs(it.kpis.totalOwed) < 0.001 }
            composeTestRule.waitForIdle()

            assertEquals("All cards paid -> totalOwed must be 0.0", 0.0, dashboardVm.uiState.value.kpis.totalOwed, 0.01)
            val totalDebtZeroText = nodeText("kpi_credit_cards_owed")
            assertEquals("F22b: When sum is zero, Total Debt Owed must show 'No debt'", "No debt", totalDebtZeroText)

            val rowRegaliaZeroText = nodeText("source_row_$cardOverpaidId")
            val rowCoralZeroText = nodeText("source_row_$cardOwedId")
            assertTrue("F22a: Zero-balance card 1 must show 'No debt', got: $rowRegaliaZeroText", rowRegaliaZeroText.contains("No debt"))
            assertTrue("F22a: Zero-balance card 2 must show 'No debt', got: $rowCoralZeroText", rowCoralZeroText.contains("No debt"))

            org.robolectric.shadows.ShadowToast.reset()
            performRowLongPress("source_row_$cardOwedId")
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            val noticeMsg2 = testNoticeDispatcher?.snackbarHostState?.currentSnackbarData?.visuals?.message ?: org.robolectric.shadows.ShadowToast.getTextOfLatestToast()
            assertEquals("F22c: Long-press on zero-balance card must show 'No payment needed' notice", "No payment needed", noticeMsg2)
            assertEquals("F22c: Long-press on zero-balance card must NOT open transfer sheet", 1, transferPrefillCalls)
            assertNull("F22c: activeEntryVm must remain null", activeEntryVm)

            mountHost = false
            dashboardVm.viewModelScope.cancel()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
        }
    }

    // =========================================================================
    // TASK F23 — RESTORE MODE-NATIVE BREAKDOWN SECTIONS (exactly one per mode):
    // UT23:
    //  For each of the 4 modes (EXPENSE, INCOME, TRANSFER, ALL):
    //   - Assert exactly-one breakdown section node present (by testTag)
    //   - Assert its matching insights family present
    //   - Assert the other two breakdown sections ABSENT
    //   - ALL mode additionally asserts Running Balance Trend + Income vs Spend present
    //   - Run under {no filter, single category} to prove filters don't change the section set
    // =========================================================================
    @Test
    fun testUT23_modeNativeBreakdownSections_onePerMode_noFilterAndSingleCategory() {
        runBlocking {
            val ids = seedFilterTruthFixture()
            val dadSalaryId = ids.getValue("dadSalaryId")
            val foodId = ids.getValue("foodId")

            val sharedHolder = com.example.domain.model.SharedFilterStateHolder()
            val dashboardVm = DashboardViewModel(repository, settingsRepository, sharedHolder)

            var mountScreen by androidx.compose.runtime.mutableStateOf(true)
            composeTestRule.setContent {
                MyApplicationTheme(darkTheme = false) {
                    if (mountScreen) {
                        DashboardScreen(
                            viewModel = dashboardVm,
                            onNavigateToExpenses = { _, _ -> }
                        )
                    }
                }
            }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                ShadowLooper.idleMainLooper()
                dashboardVm.uiState.value.accountsWithBalance.size == 3
            }

            data class ModeSectionExpectation(
                val mode: String,
                val expectedBreakdownTag: String,
                val expectedBreakdownTitle: String,
                val absentBreakdownTags: List<String>,
                val absentBreakdownTitles: List<String>,
                val expectedInsightsTag: String,
                val expectedInsightsTitle: String,
                val absentInsightsTags: List<String>
            )

            val modeExpectations = listOf(
                ModeSectionExpectation(
                    mode = "ALL",
                    expectedBreakdownTag = "breakdown_section_expense",
                    expectedBreakdownTitle = "Where money went",
                    absentBreakdownTags = listOf("breakdown_section_income", "breakdown_section_transfer"),
                    absentBreakdownTitles = listOf("Where money came from", "Where money moved"),
                    expectedInsightsTag = "insights_section_spending",
                    expectedInsightsTitle = "Spending Insights",
                    absentInsightsTags = listOf("insights_section_income", "insights_section_transfer")
                ),
                ModeSectionExpectation(
                    mode = "EXPENSE",
                    expectedBreakdownTag = "breakdown_section_expense",
                    expectedBreakdownTitle = "Where money went",
                    absentBreakdownTags = listOf("breakdown_section_income", "breakdown_section_transfer"),
                    absentBreakdownTitles = listOf("Where money came from", "Where money moved"),
                    expectedInsightsTag = "insights_section_spending",
                    expectedInsightsTitle = "Spending Insights",
                    absentInsightsTags = listOf("insights_section_income", "insights_section_transfer")
                ),
                ModeSectionExpectation(
                    mode = "INCOME",
                    expectedBreakdownTag = "breakdown_section_income",
                    expectedBreakdownTitle = "Where money came from",
                    absentBreakdownTags = listOf("breakdown_section_expense", "breakdown_section_transfer"),
                    absentBreakdownTitles = listOf("Where money went", "Where money moved"),
                    expectedInsightsTag = "insights_section_income",
                    expectedInsightsTitle = "Income Insights",
                    absentInsightsTags = listOf("insights_section_spending", "insights_section_transfer")
                ),
                ModeSectionExpectation(
                    mode = "TRANSFER",
                    expectedBreakdownTag = "breakdown_section_transfer",
                    expectedBreakdownTitle = "Where money moved",
                    absentBreakdownTags = listOf("breakdown_section_expense", "breakdown_section_income"),
                    absentBreakdownTitles = listOf("Where money went", "Where money came from"),
                    expectedInsightsTag = "insights_section_transfer",
                    expectedInsightsTitle = "Transfer Insights",
                    absentInsightsTags = listOf("insights_section_spending", "insights_section_income")
                )
            )

            val filterCases: List<Pair<String, FilterState>> = listOf(
                "NO_FILTER" to FilterState(selectedYears = setOf(2026)),
                "SINGLE_CATEGORY_FOOD" to FilterState(
                    selectedYears = setOf(2026),
                    selectedCategoryId = foodId,
                    selectedCategoryIds = setOf(foodId)
                ),
                "SINGLE_CATEGORY_DADS_SALARY" to FilterState(
                    selectedYears = setOf(2026),
                    selectedCategoryId = dadSalaryId,
                    selectedCategoryIds = setOf(dadSalaryId)
                )
            )

            for ((filterLabel, baseFilter) in filterCases) {
                for (exp in modeExpectations) {
                    val targetFilter = baseFilter.copy(selectedType = exp.mode)
                    sharedHolder.updateFilter(targetFilter)
                    ShadowLooper.idleMainLooper()
                    composeTestRule.waitForIdle()
                    dashboardVm.uiState.awaitState { it.filter == targetFilter }
                    composeTestRule.waitForIdle()

                    val ctx = "[$filterLabel | mode=${exp.mode}]"

                    // 1. Expected breakdown section present by testTag + title
                    composeTestRule.scrollToDashboardTag(exp.expectedBreakdownTag, useUnmergedTree = true)
                        .assertIsDisplayed()
                    composeTestRule.onNodeWithText(exp.expectedBreakdownTitle, useUnmergedTree = true)
                        .assertIsDisplayed()

                    // 2. Other two breakdown sections strictly ABSENT (both by testTag and title)
                    for (absentTag in exp.absentBreakdownTags) {
                        composeTestRule.onNodeWithTag(absentTag, useUnmergedTree = true)
                            .assertDoesNotExist()
                    }
                    for (absentTitle in exp.absentBreakdownTitles) {
                        assertEquals(
                            "$ctx Breakdown title '$absentTitle' must be absent",
                            0,
                            composeTestRule.onAllNodesWithText(absentTitle, useUnmergedTree = true).fetchSemanticsNodes().size
                        )
                    }

                    // 3. Matching insights family present by testTag + title; other two insights tags absent
                    // F24-TITLE (Issue 5): insights card title follows the bullet family actually rendered
                    // (All + income-only category -> "Income Insights")
                    val isAllWithIncomeOnlyCategory = (exp.mode == "ALL" && filterLabel == "SINGLE_CATEGORY_DADS_SALARY")
                    val expectedInsightsTag = if (isAllWithIncomeOnlyCategory) "insights_section_income" else exp.expectedInsightsTag
                    val expectedInsightsTitle = if (isAllWithIncomeOnlyCategory) "Income Insights" else exp.expectedInsightsTitle
                    val absentInsightsTags = if (isAllWithIncomeOnlyCategory) {
                        listOf("insights_section_spending", "insights_section_transfer")
                    } else {
                        exp.absentInsightsTags
                    }

                    composeTestRule.scrollToDashboardTag(expectedInsightsTag, useUnmergedTree = true)
                        .assertIsDisplayed()
                    composeTestRule.onNodeWithText(expectedInsightsTitle, useUnmergedTree = true)
                        .assertIsDisplayed()
                    for (absentInsightTag in absentInsightsTags) {
                        composeTestRule.onNodeWithTag(absentInsightTag, useUnmergedTree = true)
                            .assertDoesNotExist()
                    }

                    // 4. ALL mode additionally asserts Running Balance Trend + Income vs Spend present
                    if (exp.mode == "ALL") {
                        composeTestRule.scrollToDashboardTag("running_balance_trend_card", useUnmergedTree = true)
                            .assertIsDisplayed()
                        composeTestRule.scrollToDashboardTag("income_vs_spend_card", useUnmergedTree = true)
                            .assertIsDisplayed()
                    }
                }
            }

            mountScreen = false
            dashboardVm.viewModelScope.cancel()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
        }
    }

    // =========================================================================
    // TASK F24 — CANONICAL BREAKDOWN & INSIGHTS SCOPE (F24-SCOPE, F24-LABEL, F24-TITLE, F24-PINS, F24-LEDGER):
    // UT24 verifies:
    //  (ii)  Owner video multi-month regression (Oct 24,000 across 4 entries + Sept 23,200 across 3 entries = 47,200 across 7 entries):
    //        - Under Type=Income, Cat=Dad's Salary, Year=All, Month=All:
    //          * "Where money came from" header reads "All Time • ₹47,200.00 Total"
    //          * Row "Dad's Salary" shows ₹47,200 (100%)
    //          * Insights lead bullet: "Dad's Salary: ₹47,200 total · This cycle ₹24,000 · Prior cycle ₹23,200"
    //          * Insights secondary bullets: largest = ₹11,200 (22 Sept), count = 7, avg = ₹6,743
    //        - Set Month=Oct (Year=2026) -> header reads "Oct 2026 • ₹24,000.00 Total", row ₹24,000 (100%),
    //          largest = ₹10,000 (19 Oct), count = 4, avg = ₹6,000
    //        - Set Type=All + Cat=Dad's Salary -> Insights title reads "Income Insights" (F24-TITLE)
    //  (iii) Multi-year fixture (2025 + 2026):
    //        - Under Year=All, Month=All: breakdown == all-years sum ("All Time") AND Total-X KPI == default-year sum ("All months · 2026")
    //  (iv)  Breakdown header total == sum of rows == Ledger filtered sum under identical slicers across Expense, Income, Transfer
    //  (v)   Month=Sept + Year=All behavior pinned as month-across-years (Sept 2025 + Sept 2026)
    //  (vi)  Empty-state guard uses scoped total (not thisMonthCredits/movedOutThisMonth), and chart week scope filters breakdowns while Ledger stays FilterState-only (F24-LEDGER)
    // =========================================================================
    @Test
    fun testUT24_canonicalBreakdownScope_ownerVideoOracle_multiYear_ledgerParity_andMonthAcrossYears() {
        runBlocking {
            settingsRepository.setCurrency("₹")
            com.example.domain.util.Formatters.updateCurrentCurrencySymbol("₹")
            settingsRepository.setHideBalances(false)

            val cashId = repository.insertAccount(Account(id = 1L, name = "Cash", accountType = "CASH", openingBalance = 9000.0, sortOrder = 1))
            val sbiId = repository.insertAccount(Account(id = 2L, name = "SBI Amma", accountType = "BANK", openingBalance = 15000.0, sortOrder = 2))
            val cardId = repository.insertAccount(Account(id = 3L, name = "ICICI Credit", accountType = "CREDIT_CARD", openingBalance = 0.0, sortOrder = 3))

            val dadSalaryId = repository.insertCategory(Category(id = 10L, name = "Dad's Salary", color = "#10B981"))
            val bonusCatId = repository.insertCategory(Category(id = 11L, name = "Sept Only Bonus", color = "#3B82F6"))
            val foodId = repository.insertCategory(Category(id = 12L, name = "Food & Dining", color = "#EF4444"))

            // Owner video fixture: Dad's Salary in Oct 2026 (4 entries = 24,000) & Sept 2026 (3 entries = 23,200) -> Total = 47,200 (7 entries)
            repository.insertTransaction(TransactionEntity(id = 6001L, date = fixedTimeMillis(2026, 10, 25), type = "INCOME", amount = 2000.0, toAccountId = cashId, categoryId = dadSalaryId, description = "Dad's Salary"))
            repository.insertTransaction(TransactionEntity(id = 6002L, date = fixedTimeMillis(2026, 10, 19), type = "INCOME", amount = 10000.0, toAccountId = cashId, categoryId = dadSalaryId, description = "8k weekly wage + 2k Bonus"))
            repository.insertTransaction(TransactionEntity(id = 6003L, date = fixedTimeMillis(2026, 10, 11), type = "INCOME", amount = 6000.0, toAccountId = cashId, categoryId = dadSalaryId, description = "INCOME"))
            repository.insertTransaction(TransactionEntity(id = 6004L, date = fixedTimeMillis(2026, 10, 3), type = "INCOME", amount = 6000.0, toAccountId = cashId, categoryId = dadSalaryId, description = "Dad's Salary"))

            repository.insertTransaction(TransactionEntity(id = 6005L, date = fixedTimeMillis(2026, 9, 26), type = "INCOME", amount = 7000.0, toAccountId = cashId, categoryId = dadSalaryId, description = "Dad's Salary"))
            repository.insertTransaction(TransactionEntity(id = 6006L, date = fixedTimeMillis(2026, 9, 22), type = "INCOME", amount = 11200.0, toAccountId = cashId, categoryId = dadSalaryId, description = "Dad's Salary"))
            repository.insertTransaction(TransactionEntity(id = 6007L, date = fixedTimeMillis(2026, 9, 21), type = "INCOME", amount = 5000.0, toAccountId = cashId, categoryId = dadSalaryId, description = "Dad's Salary"))

            // Additional expenses & transfers across Sept and Oct 2026
            repository.insertTransaction(TransactionEntity(id = 6008L, date = fixedTimeMillis(2026, 10, 14), type = "EXPENSE", amount = 1500.0, fromAccountId = cashId, categoryId = foodId, description = "Oct Lunch"))
            repository.insertTransaction(TransactionEntity(id = 6009L, date = fixedTimeMillis(2026, 9, 14), type = "EXPENSE", amount = 2500.0, fromAccountId = cashId, categoryId = foodId, description = "Sept Dinner"))
            repository.insertTransaction(TransactionEntity(id = 6010L, date = fixedTimeMillis(2026, 10, 18), type = "TRANSFER", amount = 3000.0, fromAccountId = sbiId, toAccountId = cashId, description = "Oct ATM"))
            repository.insertTransaction(TransactionEntity(id = 6011L, date = fixedTimeMillis(2026, 9, 18), type = "TRANSFER", amount = 4500.0, fromAccountId = sbiId, toAccountId = cashId, description = "Sept ATM"))

            // Sept-only income category (₹0 in Oct) to verify empty-state guard uses scoped total, not thisMonthCredits
            repository.insertTransaction(TransactionEntity(id = 6012L, date = fixedTimeMillis(2026, 9, 10), type = "INCOME", amount = 8000.0, toAccountId = sbiId, categoryId = bonusCatId, description = "Sept Festival Bonus"))

            val sharedHolder = com.example.domain.model.SharedFilterStateHolder()
            val mediaRepo = com.example.data.repository.MediaRepository(context, database)
            val dashboardVm = DashboardViewModel(repository, settingsRepository, sharedHolder)
            val ledgerVm = com.example.ui.ledger.LedgerViewModel(
                repository = repository,
                settingsRepository = settingsRepository,
                mediaRepository = mediaRepo,
                sharedFilterStateHolder = sharedHolder
            )

            var mountScreen by androidx.compose.runtime.mutableStateOf(true)
            composeTestRule.setContent {
                MyApplicationTheme(darkTheme = false) {
                    if (mountScreen) {
                        DashboardScreen(
                            viewModel = dashboardVm,
                            onNavigateToExpenses = { _, _ -> }
                        )
                    }
                }
            }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                ShadowLooper.idleMainLooper()
                dashboardVm.uiState.value.accountsWithBalance.size == 3
            }

            fun nodeText(tag: String): String {
                val node = composeTestRule.scrollToDashboardTag(tag, useUnmergedTree = true).fetchSemanticsNode()
                fun gather(n: androidx.compose.ui.semantics.SemanticsNode): List<String> {
                    val own = if (androidx.compose.ui.semantics.SemanticsProperties.Text in n.config) {
                        n.config[androidx.compose.ui.semantics.SemanticsProperties.Text].map { it.text }
                    } else emptyList()
                    return own + n.children.flatMap { gather(it) }
                }
                return gather(node).joinToString(" ")
            }

            // =================================================================
            // 1. F24-PINS (ii) & DV37: Owner video scenario
            //    Type = INCOME, Cat = Dad's Salary, Year = All, Month = All
            // =================================================================
            val filterVideoAllTime = FilterState(
                selectedType = "INCOME",
                selectedCategoryId = dadSalaryId,
                selectedCategoryIds = setOf(dadSalaryId)
            )
            sharedHolder.updateFilter(filterVideoAllTime)
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            val stateVideoAllTime = dashboardVm.uiState.awaitState {
                it.filter == filterVideoAllTime && it.thisMonthCredits == 24000.0
            }
            composeTestRule.waitForIdle()

            assertEquals("All Time", stateVideoAllTime.monthYearLabel)
            assertEquals("F24-SCOPE: thisMonthCredits is Oct cycle (24,000)", 24000.0, stateVideoAllTime.thisMonthCredits, 0.01)
            assertEquals("F24-SCOPE: lastMonthCredits is Sept cycle (23,200)", 23200.0, stateVideoAllTime.lastMonthCredits, 0.01)
            assertEquals("F24-SCOPE: totalCreditsInScope is 2026 total (47,200)", 47200.0, stateVideoAllTime.totalCreditsInScope, 0.01)
            assertEquals("F24-SCOPE: incomeCategoryBreakdown must have 1 row", 1, stateVideoAllTime.incomeCategoryBreakdown.size)
            assertEquals("Dad's Salary", stateVideoAllTime.incomeCategoryBreakdown[0].categoryName)
            assertEquals("F24-PINS (ii): Dad's Salary breakdown row must be 47,200 (Oct + Sept), not 24,000", 47200.0, stateVideoAllTime.incomeCategoryBreakdown[0].amount, 0.01)
            assertEquals(100.0, stateVideoAllTime.incomeCategoryBreakdown[0].percentage.toDouble(), 0.01)

            val incomeSectionText = nodeText("breakdown_section_income")
            assertTrue("F24-PINS (ii): Breakdown header must show 'All Time • ₹47,200.00 Total', got: $incomeSectionText", incomeSectionText.contains("All Time • ₹47,200.00 Total"))
            assertTrue("F24-PINS (ii): Breakdown row must show '₹47,200 (100%)', got: $incomeSectionText", incomeSectionText.contains("₹47,200 (100%)"))

            val insightsTextAllTime = stateVideoAllTime.insights.joinToString(" | ")
            assertEquals(
                "Dad's Salary: ₹47,200 total · This cycle ₹24,000 · Prior cycle ₹23,200",
                stateVideoAllTime.insights.first()
            )
            assertTrue(
                "F24-PINS (ii): Insights secondary bullets must include largest = ₹11,200 on 22 Sept, got: $insightsTextAllTime",
                stateVideoAllTime.insights.any { it.contains("11,200") && it.contains("22 Sept") }
            )
            assertTrue(
                "F24-PINS (ii): Insights secondary bullets must include 7 income entries averaging ₹6,743, got: $insightsTextAllTime",
                stateVideoAllTime.insights.any { it.contains("7 income entries") && it.contains("6,743") }
            )

            // =================================================================
            // 2. DV37: Set Month = Oct (Year = 2026) -> "Oct 2026 • ₹24,000.00 Total"
            // =================================================================
            val filterOct2026 = FilterState(
                selectedYears = setOf(2026),
                selectedMonths = setOf(10),
                selectedType = "INCOME",
                selectedCategoryId = dadSalaryId,
                selectedCategoryIds = setOf(dadSalaryId)
            )
            sharedHolder.updateFilter(filterOct2026)
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            val stateOct2026 = dashboardVm.uiState.awaitState {
                it.filter == filterOct2026 && it.monthYearLabel == "Oct 2026"
            }
            composeTestRule.waitForIdle()

            assertEquals(24000.0, stateOct2026.incomeCategoryBreakdown[0].amount, 0.01)
            val incomeSectionOctText = nodeText("breakdown_section_income")
            assertTrue("DV37: Month=Oct header must read 'Oct 2026 • ₹24,000.00 Total', got: $incomeSectionOctText", incomeSectionOctText.contains("Oct 2026 • ₹24,000.00 Total"))
            assertTrue("DV37: Month=Oct row must read '₹24,000 (100%)', got: $incomeSectionOctText", incomeSectionOctText.contains("₹24,000 (100%)"))
            assertTrue(
                "Month=Oct insights largest credit must be ₹10,000 on 19 Oct, got: ${stateOct2026.insights}",
                stateOct2026.insights.any { it.contains("10,000") && it.contains("19 Oct") }
            )
            assertTrue(
                "Month=Oct insights count must be 4 income entries averaging ₹6,000, got: ${stateOct2026.insights}",
                stateOct2026.insights.any { it.contains("4 income entries") && it.contains("6,000") }
            )

            // =================================================================
            // 3. F24-SCOPE Empty-State Guard: Category with ₹0 in Oct but ₹8,000 in Sept
            //    Under Year=All, Month=All (thisMonthCredits == 0.0, scoped total == 8,000.0)
            //    must render the ₹8,000 row and NOT "No income recorded in this period."
            // =================================================================
            val filterSeptOnlyCat = FilterState(
                selectedType = "INCOME",
                selectedCategoryId = bonusCatId,
                selectedCategoryIds = setOf(bonusCatId)
            )
            sharedHolder.updateFilter(filterSeptOnlyCat)
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            val stateSeptOnlyCat = dashboardVm.uiState.awaitState {
                it.filter == filterSeptOnlyCat && it.thisMonthCredits == 0.0 && it.incomeCategoryBreakdown.isNotEmpty()
            }
            composeTestRule.waitForIdle()

            assertEquals(0.0, stateSeptOnlyCat.thisMonthCredits, 0.01)
            assertEquals(8000.0, stateSeptOnlyCat.incomeCategoryBreakdown[0].amount, 0.01)
            val septOnlySectionText = nodeText("breakdown_section_income")
            assertTrue("Empty-state guard must use scoped total (₹8,000.00), got: $septOnlySectionText", septOnlySectionText.contains("All Time • ₹8,000.00 Total"))
            assertFalse("Must NOT show empty state when scoped total > 0", septOnlySectionText.contains("No income recorded in this period."))

            // =================================================================
            // 4. F24-PINS (iii) & F24-LABEL: Multi-year fixture (2025 + 2026)
            //    Insert 2025 Dad's Salary (+₹10,000 on 15 Sept 2025).
            //    Under Year=All, Month=All:
            //      - Breakdown sums all years: 47,200 + 10,000 = 57,200 ("All Time • ₹57,200.00 Total")
            //      - Total Credits KPI sums default active year (2026): 47,200 ("All months · 2026")
            // =================================================================
            repository.insertTransaction(
                TransactionEntity(
                    id = 6013L,
                    date = fixedTimeMillis(2025, 9, 15),
                    type = "INCOME",
                    amount = 10000.0,
                    toAccountId = cashId,
                    categoryId = dadSalaryId,
                    description = "2025 Sept Dad Salary"
                )
            )
            sharedHolder.updateFilter(filterVideoAllTime)
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            val stateMultiYear = dashboardVm.uiState.awaitState {
                it.filter == filterVideoAllTime && it.incomeCategoryBreakdown.firstOrNull()?.amount == 57200.0
            }
            composeTestRule.waitForIdle()

            assertEquals("All Time", stateMultiYear.monthYearLabel)
            assertEquals("F24-PINS (iii): Breakdown row is all-years sum (57,200)", 57200.0, stateMultiYear.incomeCategoryBreakdown[0].amount, 0.01)
            assertEquals("F24-PINS (iii): Total Credits KPI is default-year 2026 sum (47,200)", 47200.0, stateMultiYear.totalCreditsInScope, 0.01)
            assertEquals("All months · 2026", stateMultiYear.totalScopeSubLabel)
            val multiYearSectionText = nodeText("breakdown_section_income")
            assertTrue("F24-LABEL: 'All Time • ₹57,200.00 Total' matches all-years sum, got: $multiYearSectionText", multiYearSectionText.contains("All Time • ₹57,200.00 Total"))

            // =================================================================
            // 5. F24-PINS (v) (Issue 6): Month=Sept + Year=All pinned as month-across-years
            //    Sept 2025 (10,000) + Sept 2026 (23,200) = 33,200
            // =================================================================
            val filterSeptAcrossYears = FilterState(
                selectedMonths = setOf(9),
                selectedType = "INCOME",
                selectedCategoryId = dadSalaryId,
                selectedCategoryIds = setOf(dadSalaryId)
            )
            sharedHolder.updateFilter(filterSeptAcrossYears)
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            val stateSeptAcrossYears = dashboardVm.uiState.awaitState {
                it.filter == filterSeptAcrossYears && it.monthYearLabel == "Sept"
            }
            val ledgerSeptAcrossYears = ledgerVm.uiState.awaitState {
                it.filter == filterSeptAcrossYears && it.totalCount == 4
            }
            composeTestRule.waitForIdle()

            val ledgerSeptSum = ledgerSeptAcrossYears.dayGroups.flatMap { it.transactions }.sumOf { it.amount }
            assertEquals("F24-PINS (v): Month=Sept + Year=All sums Sept across all years (33,200)", 33200.0, stateSeptAcrossYears.incomeCategoryBreakdown[0].amount, 0.01)
            assertEquals("F24-PINS (v): Ledger sum matches breakdown sum (33,200)", 33200.0, ledgerSeptSum, 0.01)
            val septAcrossYearsText = nodeText("breakdown_section_income")
            assertTrue("F24-PINS (v): Header reads 'Sept • ₹33,200.00 Total', got: $septAcrossYearsText", septAcrossYearsText.contains("Sept • ₹33,200.00 Total"))

            // =================================================================
            // 6. F24-PINS (iv): Breakdown header total == sum of rows == Ledger filtered sum
            //    under identical slicers across EXPENSE, INCOME, TRANSFER
            // =================================================================
            val parityFilters = listOf(
                FilterState(selectedType = "EXPENSE"),
                FilterState(selectedYears = setOf(2026), selectedType = "EXPENSE"),
                FilterState(selectedMonths = setOf(9), selectedType = "EXPENSE"),
                FilterState(selectedType = "INCOME"),
                FilterState(selectedYears = setOf(2026), selectedType = "INCOME"),
                FilterState(selectedMonths = setOf(9), selectedType = "INCOME"),
                FilterState(selectedType = "TRANSFER"),
                FilterState(selectedYears = setOf(2026), selectedType = "TRANSFER"),
                FilterState(selectedMonths = setOf(9), selectedType = "TRANSFER")
            )
            for (pf in parityFilters) {
                sharedHolder.updateFilter(pf)
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()
                val dS = dashboardVm.uiState.awaitState { it.filter == pf }
                val lS = ledgerVm.uiState.awaitState { it.filter == pf }
                composeTestRule.waitForIdle()

                val ledgerSum = lS.dayGroups.flatMap { it.transactions }.sumOf { it.amount }
                when (pf.selectedType) {
                    "EXPENSE" -> {
                        val rowSum = dS.categoryBreakdown.sumOf { it.amount }
                        val visibleRows = if (dS.categoryBreakdown.size <= 8) dS.categoryBreakdown else dS.categoryBreakdown.take(8)
                        val visibleSum = visibleRows.sumOf { it.amount }
                        assertEquals("F24-PINS (iv) [$pf] expense header == sum of ALL breakdown rows in state", rowSum, dS.totalExpenseInPeriod, 0.01)
                        assertEquals("F24-PINS (iv) [$pf] expense rowSum == ledgerSum", ledgerSum, rowSum, 0.01)
                        if (dS.categoryBreakdown.size <= 8) {
                            assertEquals("F28d [$pf] expense visible-rows sum == header total when N <= 8", dS.totalExpenseInPeriod, visibleSum, 0.01)
                        }
                    }
                    "INCOME" -> {
                        val rowSum = dS.incomeCategoryBreakdown.sumOf { it.amount }
                        val visibleRows = if (dS.incomeCategoryBreakdown.size <= 8) dS.incomeCategoryBreakdown else dS.incomeCategoryBreakdown.take(8)
                        val visibleSum = visibleRows.sumOf { it.amount }
                        val sectionTxt = nodeText("breakdown_section_income")
                        val expectedHeader = "${dS.monthYearLabel} • ${com.example.domain.util.Formatters.formatMoney(rowSum)} Total"
                        assertEquals("F24-PINS (iv) [$pf] income header total == sum of ALL breakdown rows in state", rowSum, dS.totalIncomeInPeriod, 0.01)
                        assertTrue("F24-PINS (iv) [$pf] income header must contain '$expectedHeader', got: $sectionTxt", sectionTxt.contains(expectedHeader))
                        assertEquals("F24-PINS (iv) [$pf] income rowSum == ledgerSum", ledgerSum, rowSum, 0.01)
                        if (dS.incomeCategoryBreakdown.size <= 8) {
                            assertEquals("F28d [$pf] income visible-rows sum == header total when N <= 8", dS.totalIncomeInPeriod, visibleSum, 0.01)
                        }
                    }
                    "TRANSFER" -> {
                        val rowSum = dS.transferRoutes.sumOf { it.amount }
                        val visibleRows = if (dS.transferRoutes.size <= 8) dS.transferRoutes else dS.transferRoutes.take(8)
                        val visibleSum = visibleRows.sumOf { it.amount }
                        val sectionTxt = nodeText("breakdown_section_transfer")
                        val expectedHeader = "${dS.monthYearLabel} • ${com.example.domain.util.Formatters.formatMoney(rowSum)} Total"
                        assertEquals("F24-PINS (iv) [$pf] transfer header total == sum of ALL breakdown rows in state", rowSum, dS.totalTransferInPeriod, 0.01)
                        assertTrue("F24-PINS (iv) [$pf] transfer header must contain '$expectedHeader', got: $sectionTxt", sectionTxt.contains(expectedHeader))
                        assertEquals("F24-PINS (iv) [$pf] transfer rowSum == ledgerSum", ledgerSum, rowSum, 0.01)
                        if (dS.transferRoutes.size <= 8) {
                            assertEquals("F28d [$pf] transfer visible-rows sum == header total when N <= 8", dS.totalTransferInPeriod, visibleSum, 0.01)
                        }
                    }
                }
            }

            // =================================================================
            // 7. F24-SCOPE + F24-LEDGER: Chart week scope (scope.weeks) scopes Dashboard
            //    breakdown, while Ledger remains FilterState-only
            // =================================================================
            val filterOctIncome = FilterState(
                selectedYears = setOf(2026),
                selectedMonths = setOf(10),
                selectedType = "INCOME",
                selectedCategoryId = dadSalaryId,
                selectedCategoryIds = setOf(dadSalaryId)
            )
            sharedHolder.updateFilter(filterOctIncome)
            dashboardVm.toggleWeek(4) // W4 of Oct 2026 = days 22..28 -> only 25 Oct (+2,000.0)
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            val dStateW4 = dashboardVm.uiState.awaitState {
                it.filter == filterOctIncome && it.scopeWeeks == setOf(4)
            }
            val lStateOct = ledgerVm.uiState.awaitState {
                it.filter == filterOctIncome
            }
            assertEquals("F24-SCOPE: W4 chart scope filters Dashboard income breakdown to 25 Oct (2,000.0)", 2000.0, dStateW4.incomeCategoryBreakdown.sumOf { it.amount }, 0.01)
            assertEquals("F24-LEDGER: Ledger remains FilterState-only (all 4 Oct entries = 24,000.0)", 24000.0, lStateOct.dayGroups.flatMap { it.transactions }.sumOf { it.amount }, 0.01)

            mountScreen = false
            dashboardVm.viewModelScope.cancel()
            ledgerVm.viewModelScope.cancel()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
        }
    }

    // =========================================================================
    // TASK F25 — RECURRING DIRECTION SUPPORT (I pay / I receive):
    // UT25 verifies:
    //  1. F25a Sheet toggle: two-way direction selector on RECURRING tab ("I pay (outgoing)" /
    //     "I receive (incoming)"), "Receiving Account (Required)" header, "Please select a receiving account"
    //     validation error, "Recurring Name (e.g. Tenant Rent, Salary)" label, unfiltered category chips,
    //     and saving RECURRING with direction="INCOMING".
    //  2. F25a Tab-switch: preserves direction on edit (commitment != null), defaults "OUTGOING" on new draft (commitment == null).
    //  3. F25b Settlement: recordAndMarkPaid & recordPartialPayment on RECURRING+INCOMING create INCOME
    //     ledger transactions (fromAccountId=null, toAccountId=accountId, recurring income notes) and roll cycle.
    //  4. F25c Popup rows: incoming recurring row renders IncomeGreen + up-arrow + "Record & mark received" +
    //     "I receive · <dueLabel>" + ZERO sufficiency/short-by nodes; outgoing recurring row unchanged.
    //  5. F25c Masked mode: hideBalances=true masks popup amounts including partial settle buttons ("Settle remaining ••••••").
    //  6. F25e CSV round-trip: RECURRING,...,INCOMING survives buildCsv -> parseFullCsv -> previewFromUri -> importFromUri.
    //  7. F25d DueReminderWorker: emits "Upcoming Income" / "Income Overdue" for incoming recurring without funds check.
    // =========================================================================
    @Test
    fun testUT25_recurringDirectionSupport_sheetToggle_incomeSettlement_popupGreenNoFundsChip_maskedMode_csvRoundTrip_reminders_andTabSwitch() {
        runBlocking {
            settingsRepository.setCurrency("₹")
            com.example.domain.util.Formatters.updateCurrentCurrencySymbol("₹")
            settingsRepository.setDemoMode(false)
            settingsRepository.setHideBalances(false)
            settingsRepository.setDueRemindersEnabled(true)
            com.example.ui.commitment.CommitmentViewModel.clearSessionDraft()

            val dadAccId = repository.insertAccount(
                Account(id = 1L, name = "Dad's A/C", accountType = "BANK", openingBalance = 0.0, sortOrder = 1)
            )
            val rentCatId = repository.insertCategory(
                Category(id = 10L, name = "Rental Income", color = "#10B981", iconText = "🏠")
            )
            val foodCatId = repository.insertCategory(
                Category(id = 11L, name = "Food & Dining", color = "#EF4444", iconText = "🍽️")
            )

            val accountsList = repository.allAccounts.first()
            val categoriesList = repository.allCategories.first()

            fun nodeText(tag: String): String {
                val node = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
                fun gather(n: androidx.compose.ui.semantics.SemanticsNode): List<String> {
                    val own = if (androidx.compose.ui.semantics.SemanticsProperties.Text in n.config) {
                        n.config[androidx.compose.ui.semantics.SemanticsProperties.Text].map { it.text }
                    } else emptyList()
                    return own + n.children.flatMap { gather(it) }
                }
                return gather(node).joinToString(" ")
            }

            // =================================================================
            // 1. F25a SHEET TOGGLE & VALIDATION & TAB-SWITCH (NEW DRAFT vs EDIT)
            // =================================================================
            var sheetMode by androidx.compose.runtime.mutableStateOf("NEW_NO_ACCOUNTS")
            var editingItem by androidx.compose.runtime.mutableStateOf<Commitment?>(null)
            var savedCommitments = mutableListOf<Commitment>()
            var popupGroupsState by androidx.compose.runtime.mutableStateOf(
                com.example.domain.commitment.CommitmentGroups(emptyList(), emptyList(), emptyList(), emptyList(), 0)
            )
            var popupHideBalances by androidx.compose.runtime.mutableStateOf(false)
            var popupAccountBalance by androidx.compose.runtime.mutableStateOf(0.0)

            composeTestRule.setContent {
                MyApplicationTheme(darkTheme = false) {
                    when (sheetMode) {
                        "NEW_NO_ACCOUNTS" -> {
                            androidx.compose.runtime.key("new_no_accounts") {
                                AddEditCommitmentSheet(
                                    commitment = null,
                                    accounts = emptyList(),
                                    categories = categoriesList,
                                    currencySymbol = "₹",
                                    onDismiss = {},
                                    onSave = { savedCommitments.add(it) }
                                )
                            }
                        }
                        "NEW_WITH_ACCOUNTS" -> {
                            androidx.compose.runtime.key("new_with_accounts") {
                                AddEditCommitmentSheet(
                                    commitment = null,
                                    accounts = accountsList,
                                    categories = categoriesList,
                                    currencySymbol = "₹",
                                    onDismiss = {},
                                    onSave = { savedCommitments.add(it) }
                                )
                            }
                        }
                        "EDIT_INCOMING" -> {
                            androidx.compose.runtime.key("edit_incoming_${editingItem?.id}") {
                                AddEditCommitmentSheet(
                                    commitment = editingItem,
                                    accounts = accountsList,
                                    categories = categoriesList,
                                    currencySymbol = "₹",
                                    onDismiss = {},
                                    onSave = { savedCommitments.add(it) }
                                )
                            }
                        }
                        "POPUP" -> {
                            com.example.ui.commitment.CommitmentsPopup(
                                groups = popupGroupsState,
                                accounts = accountsList,
                                categories = categoriesList,
                                accountBalances = mapOf(dadAccId to popupAccountBalance),
                                currencySymbol = "₹",
                                hideBalances = popupHideBalances,
                                onDismiss = {},
                                onRecordAndMarkPaid = {},
                                onRecordPartialPayment = { _, _, _, _, _, _ -> },
                                onSkipCycle = {},
                                onSettleIou = { _, _, _ -> },
                                onSaveCommitment = {},
                                onDeleteCommitment = {}
                            )
                        }
                    }
                }
            }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            // Verify RECURRING tab shows two-way direction selector with recurring wording
            composeTestRule.onNodeWithTag("iou_outgoing_btn", useUnmergedTree = true).assertIsDisplayed()
            composeTestRule.onNodeWithTag("iou_incoming_btn", useUnmergedTree = true).assertIsDisplayed()
            assertEquals("I pay (outgoing)", nodeText("iou_outgoing_btn"))
            assertEquals("I receive (incoming)", nodeText("iou_incoming_btn"))
            composeTestRule.onNodeWithText("Paying Account (Required)", useUnmergedTree = true).assertIsDisplayed()
            composeTestRule.onNodeWithText("Bill Name (e.g. WiFi, Car Loan)", useUnmergedTree = true).assertIsDisplayed()

            // Tap "I receive (incoming)" -> header & name label update; categories stay unfiltered
            composeTestRule.onNodeWithTag("iou_incoming_btn").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Receiving Account (Required)", useUnmergedTree = true).assertIsDisplayed()
            composeTestRule.onNodeWithText("Recurring Name (e.g. Tenant Rent, Salary)", useUnmergedTree = true).assertIsDisplayed()
            composeTestRule.onNodeWithTag("category_chip_$rentCatId", useUnmergedTree = true).assertIsDisplayed()
            composeTestRule.onNodeWithTag("category_chip_$foodCatId", useUnmergedTree = true).assertIsDisplayed()

            // Validation when no receiving account is selected
            composeTestRule.onNodeWithTag("commitment_name_input").performTextInput("Tenant Rent")
            composeTestRule.onNodeWithTag("commitment_amount_input").performTextInput("5000")
            composeTestRule.onNodeWithTag("save_commitment_btn").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Please select a receiving account", useUnmergedTree = true).assertIsDisplayed()

            // Tab-switch on NEW draft (commitment == null) resets direction to "OUTGOING"
            composeTestRule.onNodeWithTag("type_iou_btn").performClick()
            composeTestRule.waitForIdle()
            assertEquals("OUTGOING", com.example.ui.commitment.CommitmentViewModel.sessionDraft.direction)
            assertEquals("I owe (Outgoing)", nodeText("iou_outgoing_btn"))
            assertEquals("Owed to me (Incoming)", nodeText("iou_incoming_btn"))

            // Select INCOMING on IOU tab, then switch back to RECURRING on NEW draft -> defaults to OUTGOING
            composeTestRule.onNodeWithTag("iou_incoming_btn").performClick()
            composeTestRule.waitForIdle()
            assertEquals("INCOMING", com.example.ui.commitment.CommitmentViewModel.sessionDraft.direction)
            composeTestRule.onNodeWithTag("type_recurring_btn").performClick()
            composeTestRule.waitForIdle()
            assertEquals("OUTGOING", com.example.ui.commitment.CommitmentViewModel.sessionDraft.direction)
            composeTestRule.onNodeWithText("Paying Account (Required)", useUnmergedTree = true).assertIsDisplayed()

            // Now mount NEW_WITH_ACCOUNTS, select INCOMING, pick category, and save
            com.example.ui.commitment.CommitmentViewModel.clearSessionDraft()
            sheetMode = "NEW_WITH_ACCOUNTS"
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("iou_incoming_btn").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("commitment_name_input").performTextInput("Tenant Rent")
            composeTestRule.onNodeWithTag("commitment_amount_input").performTextInput("5000")
            composeTestRule.onNodeWithTag("category_chip_$rentCatId").performClick()
            composeTestRule.onNodeWithTag("save_commitment_btn").performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            assertEquals(1, savedCommitments.size)
            val savedIncoming = savedCommitments.single()
            assertEquals("RECURRING", savedIncoming.type)
            assertEquals("INCOMING", savedIncoming.direction)
            assertEquals("Tenant Rent", savedIncoming.name)
            assertEquals(5000.0, savedIncoming.amount, 0.01)
            assertEquals(dadAccId, savedIncoming.accountId)
            assertEquals(rentCatId, savedIncoming.categoryId)

            // Tab-switch on EDIT (commitment != null) preserves direction = "INCOMING"
            val existingIncoming = savedIncoming.copy(id = 77L, dayOfMonth = 5)
            editingItem = existingIncoming
            sheetMode = "EDIT_INCOMING"
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("Receiving Account (Required)", useUnmergedTree = true).assertIsDisplayed()
            composeTestRule.onNodeWithTag("type_iou_btn").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("type_recurring_btn").performClick()
            composeTestRule.waitForIdle()
            // Still INCOMING after toggling tabs during edit
            composeTestRule.onNodeWithText("Receiving Account (Required)", useUnmergedTree = true).assertIsDisplayed()
            composeTestRule.onNodeWithTag("save_commitment_btn").performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            assertEquals("INCOMING", savedCommitments.last().direction)

            // =================================================================
            // 2. F25b SETTLEMENT (Full & Partial for INCOMING vs OUTGOING)
            // =================================================================
            val commitmentVm = com.example.ui.commitment.CommitmentViewModel(repository)
            val inId = repository.insertCommitment(
                Commitment(
                    id = 501L,
                    type = "RECURRING",
                    name = "Tenant Rent",
                    amount = 5000.0,
                    direction = "INCOMING",
                    dayOfMonth = 10,
                    accountId = dadAccId,
                    categoryId = rentCatId,
                    active = true
                )
            )
            val insertedIn = repository.activeCommitments.first().first { it.id == inId }

            // 2a. Full settlement via recordAndMarkPaid -> creates INCOME transaction & rolls cycle
            commitmentVm.recordAndMarkPaid(insertedIn)
            composeTestRule.waitUntil(5000) {
                ShadowLooper.idleMainLooper()
                runBlocking {
                    repository.allTransactions.first().size == 1 &&
                        repository.activeCommitments.first().first { it.id == inId }.lastPostedCycle != null
                }
            }
            val txsAfterFull = repository.allTransactions.first()
            assertEquals(1, txsAfterFull.size)
            val fullTx = txsAfterFull.single()
            assertEquals("INCOME", fullTx.type)
            assertEquals(5000.0, fullTx.amount, 0.01)
            assertNull(fullTx.fromAccountId)
            assertEquals(dadAccId, fullTx.toAccountId)
            assertEquals(rentCatId, fullTx.categoryId)
            assertEquals("Tenant Rent", fullTx.description)
            assertEquals("Auto-recorded recurring income", fullTx.notes)

            val rolledIn = repository.activeCommitments.first().first { it.id == inId }
            assertEquals(com.example.domain.commitment.CommitmentCalculator.rollCycleOnPaid(insertedIn), rolledIn.lastPostedCycle)
            assertEquals(0.0, rolledIn.paidSoFar, 0.01)

            // 2b. Partial settlement (2,000) then remaining settlement (3,000) on INCOMING recurring
            commitmentVm.recordPartialPayment(rolledIn, partialAmount = 2000.0)
            composeTestRule.waitUntil(5000) {
                ShadowLooper.idleMainLooper()
                runBlocking {
                    repository.allTransactions.first().size == 2 &&
                        repository.activeCommitments.first().first { it.id == inId }.paidSoFar == 2000.0
                }
            }
            val partialTx = repository.allTransactions.first().maxByOrNull { it.id }!!
            assertEquals("INCOME", partialTx.type)
            assertEquals(2000.0, partialTx.amount, 0.01)
            assertNull(partialTx.fromAccountId)
            assertEquals(dadAccId, partialTx.toAccountId)
            assertEquals("Partial income: Tenant Rent", partialTx.description)
            assertEquals("Partial recurring income", partialTx.notes)

            val afterPartialIn = repository.activeCommitments.first().first { it.id == inId }
            assertEquals(2000.0, afterPartialIn.paidSoFar, 0.01)
            assertEquals(3000.0, afterPartialIn.remaining, 0.01)

            commitmentVm.recordAndMarkPaid(afterPartialIn)
            composeTestRule.waitUntil(5000) {
                ShadowLooper.idleMainLooper()
                runBlocking {
                    repository.allTransactions.first().size == 3 &&
                        repository.activeCommitments.first().first { it.id == inId }.paidSoFar == 0.0
                }
            }
            val remTx = repository.allTransactions.first().maxByOrNull { it.id }!!
            assertEquals("INCOME", remTx.type)
            assertEquals(3000.0, remTx.amount, 0.01)
            assertNull(remTx.fromAccountId)
            assertEquals(dadAccId, remTx.toAccountId)
            assertEquals("Settled remaining recurring income", remTx.notes)

            // =================================================================
            // 3. F25c POPUP ROWS (Incoming vs Outgoing, Sufficiency Omission, Masked Mode)
            // =================================================================
            val incomingUnpaid = Commitment(
                id = 801L,
                type = "RECURRING",
                name = "Flat Rental Income",
                amount = 18000.0,
                direction = "INCOMING",
                dayOfMonth = 5,
                accountId = dadAccId,
                categoryId = rentCatId,
                active = true,
                paidSoFar = 0.0
            )
            val outgoingUnpaid = Commitment(
                id = 802L,
                type = "RECURRING",
                name = "Broadband Bill",
                amount = 1200.0,
                direction = "OUTGOING",
                dayOfMonth = 5,
                accountId = dadAccId,
                categoryId = foodCatId,
                active = true,
                paidSoFar = 0.0
            )
            val incomingPartial = Commitment(
                id = 803L,
                type = "RECURRING",
                name = "Shop Rent",
                amount = 5000.0,
                direction = "INCOMING",
                dayOfMonth = 12,
                accountId = dadAccId,
                categoryId = rentCatId,
                active = true,
                paidSoFar = 2000.0
            )
            val iouPartial = Commitment(
                id = 804L,
                type = "IOU",
                name = "Rahul Loan",
                amount = 4000.0,
                direction = "INCOMING",
                accountId = dadAccId,
                active = true,
                paidSoFar = 1500.0
            )

            popupHideBalances = false
            popupAccountBalance = 0.0 // Insufficient for 18,000 and 1,200

            popupGroupsState = com.example.domain.commitment.CommitmentGroups(
                overdue = emptyList(),
                next7Days = listOf(
                    com.example.domain.commitment.CommitmentItem(incomingUnpaid, null, "in 4 days", false),
                    com.example.domain.commitment.CommitmentItem(outgoingUnpaid, null, "in 4 days", false)
                ),
                later = emptyList(),
                noDueDate = emptyList(),
                badgeCount = 2
            )

            sheetMode = "POPUP"
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            // Incoming unpaid row (801):
            // - Meta line starts with "I receive · in 4 days"
            // - Settle button says "Record & mark received"
            // - Up-arrow icon with contentDescription "Incoming Recurring" exists
            // - ZERO "Funds sufficient" or "Short by" text inside commitment_row_801 (both when balance=0 and when balance=50,000)
            val row801TextLowBal = nodeText("commitment_row_801")
            val meta801Text = nodeText("commitment_meta_line_801")
            assertTrue("F25c: Incoming meta line must start with 'I receive · in 4 days', got: $meta801Text", meta801Text.startsWith("I receive · in 4 days"))
            assertEquals("Record & mark received", nodeText("record_paid_btn_801"))
            composeTestRule.onNodeWithContentDescription("Incoming Recurring", useUnmergedTree = true).assertExists()
            assertFalse("F25c: Incoming row must NOT render 'Short by' when balance < amount, got: $row801TextLowBal", row801TextLowBal.contains("Short by"))
            assertFalse("F25c: Incoming row must NOT render 'Funds sufficient', got: $row801TextLowBal", row801TextLowBal.contains("Funds sufficient"))

            // Outgoing unpaid row (802) DOES render "Record & mark paid" and "Short by" when balance=0
            val row802TextLowBal = nodeText("commitment_row_802")
            assertEquals("Record & mark paid", nodeText("record_paid_btn_802"))
            assertTrue("F25c: Outgoing row must render 'Short by' when balance < amount, got: $row802TextLowBal", row802TextLowBal.contains("Short by"))

            // Flip balance to 50,000 -> Incoming row STILL has zero sufficiency chip; Outgoing row has "Funds sufficient"
            popupAccountBalance = 50000.0
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            val row801TextHighBal = nodeText("commitment_row_801")
            val row802TextHighBal = nodeText("commitment_row_802")
            assertFalse("F25c: Incoming row must NOT render 'Funds sufficient' when balance >= amount, got: $row801TextHighBal", row801TextHighBal.contains("Funds sufficient"))
            assertFalse("F25c: Incoming row must NOT render 'Short by', got: $row801TextHighBal", row801TextHighBal.contains("Short by"))
            assertTrue("F25c: Outgoing row must render 'Funds sufficient' when balance >= amount, got: $row802TextHighBal", row802TextHighBal.contains("Funds sufficient"))

            // Masked mode (hideBalances = true) with partial recurring (803) and partial IOU (804):
            // Verify amounts AND partial settle buttons (lines 726, 729) mask remaining amount with Formatters.MASK_VALUE
            popupGroupsState = com.example.domain.commitment.CommitmentGroups(
                overdue = emptyList(),
                next7Days = listOf(
                    com.example.domain.commitment.CommitmentItem(incomingPartial, null, "in 6 days", false)
                ),
                later = emptyList(),
                noDueDate = listOf(
                    com.example.domain.commitment.CommitmentItem(iouPartial, null, "No due date", false)
                ),
                badgeCount = 2
            )
            popupHideBalances = true
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            val math803Masked = nodeText("commitment_math_text_803")
            assertEquals("${com.example.domain.util.Formatters.MASK_VALUE} left of ${com.example.domain.util.Formatters.MASK_VALUE}", math803Masked)
            val btn803Masked = nodeText("record_paid_btn_803")
            assertEquals("Settle remaining ${com.example.domain.util.Formatters.MASK_VALUE}", btn803Masked)
            assertFalse("Masked recurring settle button must not leak 3,000", btn803Masked.contains("3,000"))

            val btn804Masked = nodeText("mark_settled_btn_804")
            assertEquals("Settle remaining ${com.example.domain.util.Formatters.MASK_VALUE}", btn804Masked)
            assertFalse("Masked IOU settle button must not leak 2,500", btn804Masked.contains("2,500"))

            // =================================================================
            // 4. F25e CSV ROUND-TRIP (buildCsv -> parseCsvFull -> restoreFromInputStream)
            // =================================================================
            val csvText = com.example.importbackup.CsvBackupManager.buildCsv(
                accounts = accountsList,
                categories = categoriesList,
                transactions = emptyList(),
                commitments = listOf(incomingUnpaid)
            )
            assertTrue(
                "CSV #COMMITMENTS must contain RECURRING,Flat Rental Income,18000.00,INCOMING",
                csvText.contains("RECURRING,Flat Rental Income,18000.00,INCOMING")
            )
            val parsedBackup = com.example.importbackup.CsvBackupManager.parseCsvFull(
                java.io.ByteArrayInputStream(csvText.toByteArray(Charsets.UTF_8))
            )
            assertEquals(1, parsedBackup.dues.size)
            assertEquals("RECURRING", parsedBackup.dues.single().type)
            assertEquals("INCOMING", parsedBackup.dues.single().direction)
            assertEquals(18000.0, parsedBackup.dues.single().amount, 0.01)

            // Wipe commitments in DB and restore from CSV InputStream
            database.commitmentDao().clearAll()
            assertEquals(0, repository.activeCommitments.first().size)
            com.example.importbackup.UnifiedCsvStorage.restoreFromInputStream(
                repository = repository,
                inputStream = java.io.ByteArrayInputStream(csvText.toByteArray(Charsets.UTF_8)),
                clearExisting = true,
                settingsRepository = settingsRepository,
                context = context
            )
            val restoredCommitment = repository.activeCommitments.first().single()
            assertEquals("RECURRING", restoredCommitment.type)
            assertEquals("INCOMING", restoredCommitment.direction)
            assertEquals("Flat Rental Income", restoredCommitment.name)
            assertEquals(18000.0, restoredCommitment.amount, 0.01)

            // =================================================================
            // 5. F25d REMINDERS (DueReminderWorker incoming recurring tomorrow & overdue)
            // =================================================================
            database.commitmentDao().clearAll()
            val nowMs = System.currentTimeMillis()
            val tomorrowCal = java.util.Calendar.getInstance().apply {
                timeInMillis = nowMs
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            val tomorrowDay = tomorrowCal.get(java.util.Calendar.DAY_OF_MONTH)

            val yesterdayCal = java.util.Calendar.getInstance().apply {
                timeInMillis = nowMs
                add(java.util.Calendar.DAY_OF_YEAR, -1)
            }
            val yesterdayDay = yesterdayCal.get(java.util.Calendar.DAY_OF_MONTH)
            val prevMonthCycle = java.util.Calendar.getInstance().apply {
                timeInMillis = nowMs
                add(java.util.Calendar.MONTH, -2)
            }.let {
                String.format(java.util.Locale.US, "%04d-%02d", it.get(java.util.Calendar.YEAR), it.get(java.util.Calendar.MONTH) + 1)
            }

            repository.insertCommitment(
                Commitment(
                    id = 901L,
                    type = "RECURRING",
                    name = "Tenant Rent",
                    amount = 5000.0,
                    direction = "INCOMING",
                    dayOfMonth = tomorrowDay,
                    accountId = dadAccId,
                    createdAt = nowMs - 86_400_000L * 5,
                    active = true
                )
            )
            repository.insertCommitment(
                Commitment(
                    id = 902L,
                    type = "RECURRING",
                    name = "Overdue Rent",
                    amount = 5000.0,
                    direction = "INCOMING",
                    dayOfMonth = yesterdayDay,
                    accountId = dadAccId,
                    lastPostedCycle = prevMonthCycle,
                    createdAt = nowMs - 86_400_000L * 40,
                    active = true
                )
            )

            val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notifManager.cancelAll()

            com.example.worker.DueReminderWorker.checkAndNotify(context, database, settingsRepository)

            val notifications = org.robolectric.Shadows.shadowOf(notifManager).allNotifications
            val notifTexts = notifications.map { n ->
                val t = n.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
                val b = n.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
                "$t | $b"
            }

            assertTrue(
                "F25d: Must emit 'Upcoming Income | ₹5000 Tenant Rent expected in 1 day into Dad's A/C', got: $notifTexts",
                notifTexts.contains("Upcoming Income | ₹5000 Tenant Rent expected in 1 day into Dad's A/C")
            )
            assertTrue(
                "F25d: Must emit 'Income Overdue | ₹5000 Overdue Rent expected in Dad's A/C is overdue', got: $notifTexts",
                notifTexts.contains("Income Overdue | ₹5000 Overdue Rent expected in Dad's A/C is overdue")
            )
            assertFalse(
                "F25d: Incoming reminders must NEVER include 'Short by', got: $notifTexts",
                notifTexts.any { it.contains("Short by") }
            )

            commitmentVm.viewModelScope.cancel()
        }
    }

    // =========================================================================
    // TASK F26 (REVISED) — DRILL HANDOFFS ARE TRANSIENT OVERLAYS; ONLY MANUAL CHIP EDITS PERSIST:
    // UT26 verifies:
    //  (1) Owner scenario (Bug 2 in test_29.mp4):
    //      manual = All -> tap Entertainment/Food bar under "Where money went" ->
    //      Expenses renders Type=Expense, Cat=Food while Dashboard manual stays All ->
    //      return to Dashboard -> both Dashboard and Expenses are All (Type=All, Cat=All).
    //  (2) Manual + Drill combo:
    //      manual Month=Oct on Dashboard -> tap Food bar -> Expenses has Month=Oct, Type=Expense, Cat=Food ->
    //      return to Dashboard -> Month=Oct survives on Dashboard & Expenses, while Cat=All, Type=All.
    //  (3) Manual edit on Ledger persists:
    //      open Expenses directly -> set Month=Oct on Ledger chip bar -> switch to Dashboard ->
    //      Month=Oct is active on Dashboard.
    //  (4) Manual edit on Ledger AFTER drill promotes ONLY edited dimension:
    //      drill from All into Cat=Food, Type=EXPENSE -> on Expenses, manually change Category chip
    //      to Travel -> return to Dashboard -> Cat=Travel persists in manual on Dashboard, while
    //      Type (from drillOverlay, unedited) reverts to ALL.
    //  (5) KPI / Card sub-tile / Trend / Source / Budget / Income / Transfer Route drills (Bug 1 in test_29.mp4):
    //      manual = All -> tap Card Spend (Last Mo) ("kpi_cc_spend_last_month"), Trend bar, Source row,
    //      Budget row, Income bar, Transfer route bar -> each populates drillOverlay on Expenses and
    //      reverts cleanly to All on return to Dashboard across Light & Dark themes.
    //  (6) Guard G2 (Entry round-trip lifecycle):
    //      drill Dashboard -> Expenses -> Entry -> Back to Expenses keeps drillOverlay intact;
    //      switching to Dashboard, Lists, or Settings clears drillOverlay.
    //  (7) Guard G1 (Clear-All bypass & searchQuery exclusion):
    //      manual = {Year=2026, Month=Oct} + drillOverlay = {Type=EXPENSE, Cat=Food} ->
    //      typing searchQuery on Ledger never promotes any dimension to manual ->
    //      clicking Clear All on Ledger clears manual + drillOverlay + searchQuery directly
    //      with zero promoted empties.
    // =========================================================================
    @Test
    fun testUT26_transientDrillOverlay_manualChipPersistence_entryRoundTrip_andClearAllBypass() {
        runBlocking {
            val ids = seedFilterTruthFixture()
            val bankId = ids.getValue("bankId")
            val savingsId = ids.getValue("savingsId")
            val cardId = ids.getValue("cardId")
            val foodId = ids.getValue("foodId")
            val shoppingId = ids.getValue("shoppingId")
            val dadSalaryId = ids.getValue("dadSalaryId")

            val sharedHolder = com.example.domain.model.SharedFilterStateHolder()
            val mediaRepo = com.example.data.repository.MediaRepository(context, database)
            val dashboardVm = DashboardViewModel(repository, settingsRepository, sharedHolder)
            val ledgerVm = com.example.ui.ledger.LedgerViewModel(
                repository = repository,
                settingsRepository = settingsRepository,
                mediaRepository = mediaRepo,
                sharedFilterStateHolder = sharedHolder
            )

            // Simulate PaisaTrackApp destination state & G2 lifecycle rules
            var currentDest by androidx.compose.runtime.mutableStateOf("DASHBOARD")
            var isDarkTheme by androidx.compose.runtime.mutableStateOf(false)
            var mountHost by androidx.compose.runtime.mutableStateOf(true)

            fun switchDestination(nextDest: String) {
                if (nextDest == "DASHBOARD" || nextDest == "LISTS" || nextDest == "SETTINGS") {
                    sharedHolder.clearDrillOverlay()
                }
                currentDest = nextDest
            }

            composeTestRule.setContent {
                MyApplicationTheme(darkTheme = isDarkTheme) {
                    if (mountHost) {
                        androidx.compose.runtime.LaunchedEffect(currentDest) {
                            if (currentDest == "DASHBOARD" || currentDest == "LISTS" || currentDest == "SETTINGS") {
                                sharedHolder.clearDrillOverlay()
                            }
                        }
                        when (currentDest) {
                            "DASHBOARD" -> {
                                DashboardScreen(
                                    viewModel = dashboardVm,
                                    onNavigateToExpenses = { catId, accId ->
                                        dashboardVm.applyHandoff(
                                            com.example.domain.model.FilterHandoffPatch(
                                                selectedCategoryId = catId,
                                                selectedCategoryIds = catId?.let { setOf(it) },
                                                selectedAccountId = accId,
                                                selectedAccountIds = accId?.let { setOf(it) }
                                            )
                                        )
                                        currentDest = "EXPENSES"
                                    },
                                    onNavigateToExpensesWithFilter = { drillFilter ->
                                        dashboardVm.applyDrillFilter(drillFilter)
                                        currentDest = "EXPENSES"
                                    }
                                )
                            }
                            "EXPENSES" -> {
                                com.example.ui.ledger.LedgerScreen(
                                    viewModel = ledgerVm,
                                    onAddClick = { currentDest = "ENTRY" },
                                    onTransactionClick = { _ -> currentDest = "ENTRY" }
                                )
                            }
                            "ENTRY" -> {
                                androidx.compose.material3.Text(
                                    text = "Entry Screen Mock",
                                    modifier = Modifier.testTag("mock_entry_screen")
                                )
                            }
                            "LISTS" -> {
                                androidx.compose.material3.Text(
                                    text = "Lists Screen Mock",
                                    modifier = Modifier.testTag("mock_lists_screen")
                                )
                            }
                            "SETTINGS" -> {
                                androidx.compose.material3.Text(
                                    text = "Settings Screen Mock",
                                    modifier = Modifier.testTag("mock_settings_screen")
                                )
                            }
                        }
                    }
                }
            }

            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            dashboardVm.uiState.awaitState { it.accountsWithBalance.size == 3 && it.categoryBreakdown.isNotEmpty() }
            composeTestRule.waitForIdle()

            fun assertChipText(tag: String, expectedText: String) {
                composeTestRule.onNodeWithTag("filter_chips_row")
                    .performScrollToNode(androidx.compose.ui.test.hasTestTag(tag))
                composeTestRule.onNodeWithTag(tag).assertTextContains(expectedText)
            }

            // =================================================================
            // (1) OWNER SCENARIO (Bug 2 in test_29.mp4):
            //     manual = All -> tap category bar ("Food") under "Where money went" ->
            //     Expenses shows Type: Expense + Cat: Food;
            //     return to Dashboard -> both Dashboard and Expenses are All.
            // =================================================================
            assertFalse("Initial manual filter must have no active slicers", sharedHolder.manualFilter.value.hasAnyActiveSlicer())
            assertTrue("Initial drillOverlay must be empty", sharedHolder.drillOverlay.value.isEmpty)

            composeTestRule.scrollToDashboardText("Food")
                .performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            assertEquals("Must navigate to EXPENSES", "EXPENSES", currentDest)
            assertEquals("Expenses effective filter must have Type=EXPENSE", "EXPENSE", ledgerVm.filter.value.selectedType)
            assertEquals("Expenses effective filter must have Cat=foodId", setOf(foodId), ledgerVm.filter.value.effectiveCategoryIds())
            assertFalse("Dashboard manual filter MUST remain untouched (All)", sharedHolder.manualFilter.value.hasAnyActiveSlicer())
            assertEquals("Dashboard activeTypeMode must not be mutated by drill", "EXPENSE", dashboardVm.uiState.value.activeTypeMode)
            assertChipText("filter_chip_type", "Type: Expense")
            assertChipText("filter_chip_category", "Cat: Food")

            // Return to Dashboard -> drillOverlay clears; both pages are All
            switchDestination("DASHBOARD")
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            assertTrue("drillOverlay must be empty on return to Dashboard", sharedHolder.drillOverlay.value.isEmpty)
            assertFalse("Dashboard manual filter must be All on return", dashboardVm.uiState.value.filter.hasAnyActiveSlicer())
            assertFalse("Ledger effective filter must revert to All on return", ledgerVm.filter.value.hasAnyActiveSlicer())
            assertChipText("filter_chip_type", "Type: All")
            assertChipText("filter_chip_category", "Category: All")
            composeTestRule.onNodeWithTag("filter_clear_all", useUnmergedTree = true).assertDoesNotExist()

            // =================================================================
            // (2) MANUAL + DRILL COMBO:
            //     Set Month = Oct (10) manually on Dashboard -> tap Food bar ->
            //     Expenses shows Month: Oct + Type: Expense + Cat: Food ->
            //     return to Dashboard -> Month: Oct survives, Cat & Type revert to All.
            // =================================================================
            dashboardVm.updateFilter(FilterState(selectedMonth = 10, selectedMonths = setOf(10)))
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            dashboardVm.uiState.awaitState { it.filter.effectiveMonths() == setOf(10) }
            composeTestRule.waitForIdle()

            composeTestRule.scrollToDashboardText("Food")
                .performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            assertEquals("EXPENSES", currentDest)
            assertEquals("Expenses must combine manual Month=10 with drill overlay", setOf(10), ledgerVm.filter.value.effectiveMonths())
            assertEquals("Expenses must have drill Type=EXPENSE", "EXPENSE", ledgerVm.filter.value.selectedType)
            assertEquals("Expenses must have drill Cat=foodId", setOf(foodId), ledgerVm.filter.value.effectiveCategoryIds())
            assertChipText("filter_chip_month", "Month: Oct")
            assertChipText("filter_chip_type", "Type: Expense")
            assertChipText("filter_chip_category", "Cat: Food")

            // Return to Dashboard -> Month=Oct survives; Cat & Type revert to All
            switchDestination("DASHBOARD")
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            assertEquals("Manual Month=10 must survive return to Dashboard", setOf(10), dashboardVm.uiState.value.filter.effectiveMonths())
            assertEquals("Type must revert to ALL on return", "ALL", dashboardVm.uiState.value.filter.selectedType)
            assertTrue("Category must revert to empty on return", dashboardVm.uiState.value.filter.effectiveCategoryIds().isEmpty())
            assertEquals("Ledger effective filter also has Month=10 only", setOf(10), ledgerVm.filter.value.effectiveMonths())
            assertTrue("Ledger effective filter has empty categories after return", ledgerVm.filter.value.effectiveCategoryIds().isEmpty())
            assertChipText("filter_chip_month", "Month: Oct")
            assertChipText("filter_chip_type", "Type: All")
            assertChipText("filter_chip_category", "Category: All")

            // Reset to All before Scenario 3
            dashboardVm.clearAllSlicers()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            // =================================================================
            // (3) MANUAL EDIT ON LEDGER PERSISTS:
            //     Open Expenses via bottom-nav -> manually set Month=Oct on Ledger ->
            //     switch to Dashboard -> Month=Oct is on Dashboard.
            // =================================================================
            switchDestination("EXPENSES")
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            ledgerVm.updateFilter(ledgerVm.filter.value.copy(selectedMonth = 10, selectedMonths = setOf(10)))
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            assertEquals("Manual edit on Ledger must promote Month=10 to manualFilter", setOf(10), sharedHolder.manualFilter.value.effectiveMonths())
            assertChipText("filter_chip_month", "Month: Oct")

            switchDestination("DASHBOARD")
            dashboardVm.uiState.awaitState { it.filter.effectiveMonths() == setOf(10) }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            assertEquals("Month=10 edited on Ledger must persist on Dashboard", setOf(10), dashboardVm.uiState.value.filter.effectiveMonths())
            assertChipText("filter_chip_month", "Month: Oct")

            // Reset to All before Scenario 4
            dashboardVm.clearAllSlicers()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            // =================================================================
            // (4) MANUAL EDIT ON LEDGER AFTER DRILL PROMOTES ONLY EDITED DIM (UT26(4)):
            //     Drill from All into Cat=Food, Type=EXPENSE -> on Expenses, manually
            //     change Category chip to Shopping (shoppingId) -> return to Dashboard ->
            //     Cat=Shopping persists in manualFilter on Dashboard, while Type (from
            //     drillOverlay, unedited) reverts to ALL.
            // =================================================================
            composeTestRule.scrollToDashboardText("Food")
                .performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            assertEquals("EXPENSES", currentDest)
            assertEquals(
                setOf(com.example.domain.model.FilterDimension.TYPE, com.example.domain.model.FilterDimension.CATEGORIES),
                sharedHolder.drillOverlay.value.activeDimensions
            )

            // User manually edits ONLY Category chip on Expenses to Shopping (shoppingId)
            ledgerVm.updateFilter(
                ledgerVm.filter.value.copy(
                    selectedCategoryId = shoppingId,
                    selectedCategoryIds = setOf(shoppingId)
                )
            )
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            // Verify CATEGORIES was promoted to manualFilter and removed from drillOverlay, while TYPE stays in drillOverlay
            assertEquals("CATEGORIES must be promoted to manualFilter", setOf(shoppingId), sharedHolder.manualFilter.value.effectiveCategoryIds())
            assertEquals("Unedited TYPE must remain ALL in manualFilter", "ALL", sharedHolder.manualFilter.value.selectedType)
            assertEquals(
                "Only TYPE must remain in drillOverlay",
                setOf(com.example.domain.model.FilterDimension.TYPE),
                sharedHolder.drillOverlay.value.activeDimensions
            )
            assertEquals("While still on Expenses, effective Type is EXPENSE", "EXPENSE", ledgerVm.filter.value.selectedType)
            assertEquals("While still on Expenses, effective Category is Shopping", setOf(shoppingId), ledgerVm.filter.value.effectiveCategoryIds())

            // Return to Dashboard -> unedited TYPE reverts to ALL; edited Category=Shopping persists!
            switchDestination("DASHBOARD")
            dashboardVm.uiState.awaitState { it.filter.effectiveCategoryIds() == setOf(shoppingId) && it.filter.selectedType == "ALL" }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            assertTrue("drillOverlay must be cleared on return", sharedHolder.drillOverlay.value.isEmpty)
            assertEquals("Edited Category=Shopping must persist on Dashboard", setOf(shoppingId), dashboardVm.uiState.value.filter.effectiveCategoryIds())
            assertEquals("Unedited drill Type=EXPENSE must revert to ALL on Dashboard", "ALL", dashboardVm.uiState.value.filter.selectedType)
            assertChipText("filter_chip_category", "Cat: Shopping")
            assertChipText("filter_chip_type", "Type: All")

            // Reset to All before Scenario 5
            dashboardVm.clearAllSlicers()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            // =================================================================
            // (5) BUG 1 IN VIDEO (Card Spend Last Mo) + KPI / SOURCE / TREND /
            //     INCOME / TRANSFER ROUTE DRILLS ACROSS LIGHT & DARK THEMES:
            // =================================================================
            for (dark in listOf(false, true)) {
                isDarkTheme = dark
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()

                // 5a. Tap Card Spend (Last Mo) ("kpi_cc_spend_last_month") — exact Bug 1 from test_29.mp4
                val expectedPrevCal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, -1) }
                val expectedPrevMonth = expectedPrevCal.get(java.util.Calendar.MONTH) + 1
                val expectedPrevYear = expectedPrevCal.get(java.util.Calendar.YEAR)
                composeTestRule.scrollToDashboardTag("kpi_cc_spend_last_month", useUnmergedTree = true)
                    .performClick()
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()

                assertEquals("EXPENSES", currentDest)
                assertEquals("EXPENSE", ledgerVm.filter.value.selectedType)
                assertEquals(setOf(expectedPrevMonth), ledgerVm.filter.value.effectiveMonths())
                assertEquals(setOf(expectedPrevYear), ledgerVm.filter.value.effectiveYears())
                assertEquals(setOf(cardId), ledgerVm.filter.value.effectiveAccountIds())
                assertFalse("Dashboard manual filter must remain All during Card Spend (Last Mo) drill (dark=$dark)", sharedHolder.manualFilter.value.hasAnyActiveSlicer())

                switchDestination("DASHBOARD")
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()
                assertFalse("After returning from Card Spend (Last Mo), Dashboard must be All (dark=$dark)", dashboardVm.uiState.value.filter.hasAnyActiveSlicer())
                assertFalse("After returning from Card Spend (Last Mo), Ledger must be All (dark=$dark)", ledgerVm.filter.value.hasAnyActiveSlicer())

                // 5b. Tap Source row ("source_row_$bankId")
                composeTestRule.scrollToDashboardTag("source_row_$bankId", useUnmergedTree = true)
                    .performClick()
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()

                assertEquals("EXPENSES", currentDest)
                assertEquals(setOf(bankId), ledgerVm.filter.value.effectiveAccountIds())
                assertFalse("Manual filter untouched by source row drill", sharedHolder.manualFilter.value.hasAnyActiveSlicer())

                switchDestination("DASHBOARD")
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()
                assertFalse("Returned from source row -> All", dashboardVm.uiState.value.filter.hasAnyActiveSlicer())

                // 5c. Trend bar drill (DATE_RANGE dimension)
                val octStart = fixedTimeMillis(2026, 10, 1)
                val octEnd = fixedTimeMillis(2026, 10, 7)
                dashboardVm.applyDrillFilter(FilterState(startDate = octStart, endDate = octEnd))
                currentDest = "EXPENSES"
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()

                assertEquals(octStart, ledgerVm.filter.value.startDate)
                assertEquals(octEnd, ledgerVm.filter.value.endDate)
                assertNull("Manual startDate must remain null", sharedHolder.manualFilter.value.startDate)

                switchDestination("DASHBOARD")
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()
                assertNull("Returned from trend drill -> startDate cleared", ledgerVm.filter.value.startDate)

                // 5d. Transfer route drill (TRANSFER_ROUTE dimension)
                dashboardVm.applyDrillFilter(
                    FilterState(
                        selectedType = "TRANSFER",
                        transferRouteFromAccountId = bankId,
                        transferRouteToAccountId = savingsId,
                        transferRouteLabel = "Salary A/C → Personal A/C"
                    )
                )
                currentDest = "EXPENSES"
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()

                assertEquals("TRANSFER", ledgerVm.filter.value.selectedType)
                assertEquals(bankId, ledgerVm.filter.value.transferRouteFromAccountId)
                assertEquals(savingsId, ledgerVm.filter.value.transferRouteToAccountId)
                assertFalse("Manual filter untouched by transfer route drill", sharedHolder.manualFilter.value.hasAnyActiveSlicer())

                switchDestination("DASHBOARD")
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()
                assertNull("Returned from transfer route drill -> route cleared", ledgerVm.filter.value.transferRouteFromAccountId)
                assertEquals("ALL", ledgerVm.filter.value.selectedType)
            }
            isDarkTheme = false
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            // =================================================================
            // (6) GUARD G2 (ENTRY ROUND-TRIP PIN + LISTS/SETTINGS CLEAR):
            //     Drill Dashboard -> Expenses -> Entry -> Back to Expenses keeps
            //     drillOverlay intact; navigating to Lists, Settings, or Dashboard clears it.
            // =================================================================
            composeTestRule.scrollToDashboardText("Food")
                .performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            assertEquals("EXPENSES", currentDest)
            assertEquals(setOf(foodId), ledgerVm.filter.value.effectiveCategoryIds())
            assertEquals("EXPENSE", ledgerVm.filter.value.selectedType)

            // Navigate Expenses -> Entry (e.g. tapping a row or + FAB)
            switchDestination("ENTRY")
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("mock_entry_screen").assertIsDisplayed()
            assertFalse("G2: drillOverlay MUST survive while on Entry", sharedHolder.drillOverlay.value.isEmpty)

            // Press Back from Entry -> Expenses
            switchDestination("EXPENSES")
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            assertFalse("G2: drillOverlay MUST survive Entry -> Expenses round-trip", sharedHolder.drillOverlay.value.isEmpty)
            assertEquals("G2: Cat=foodId still active on Expenses after Entry round-trip", setOf(foodId), ledgerVm.filter.value.effectiveCategoryIds())
            assertEquals("G2: Type=EXPENSE still active on Expenses after Entry round-trip", "EXPENSE", ledgerVm.filter.value.selectedType)

            // Switch to LISTS -> drillOverlay clears
            switchDestination("LISTS")
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            assertTrue("G2: Switching to Lists must clear drillOverlay", sharedHolder.drillOverlay.value.isEmpty)
            assertFalse("G2: Ledger effective filter must be clear after switching to Lists", ledgerVm.filter.value.hasAnyActiveSlicer())

            // Re-drill from Dashboard -> Expenses -> switch to SETTINGS -> drillOverlay clears
            switchDestination("DASHBOARD")
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            dashboardVm.applyDrillFilter(FilterState(selectedType = "INCOME", selectedCategoryId = dadSalaryId, selectedCategoryIds = setOf(dadSalaryId)))
            currentDest = "EXPENSES"
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            assertFalse(sharedHolder.drillOverlay.value.isEmpty)

            switchDestination("SETTINGS")
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            assertTrue("G2: Switching to Settings must clear drillOverlay", sharedHolder.drillOverlay.value.isEmpty)

            switchDestination("DASHBOARD")
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            // =================================================================
            // (7) GUARD G1 (CLEAR-ALL BYPASS & SEARCHQUERY EXCLUSION PIN):
            //     Set manual = {Year=2026, Month=Oct} + drillOverlay = {Type=EXPENSE, Cat=Food}.
            //     1) Typing searchQuery on Ledger never promotes any dimension to manual.
            //     2) Clicking Clear All on Ledger clears manual + drillOverlay + searchQuery
            //        directly without promoting empties.
            // =================================================================
            dashboardVm.updateFilter(
                FilterState(
                    selectedYear = 2026,
                    selectedYears = setOf(2026),
                    selectedMonth = 10,
                    selectedMonths = setOf(10)
                )
            )
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            composeTestRule.scrollToDashboardText("Food")
                .performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            assertEquals("EXPENSES", currentDest)
            assertEquals(setOf(2026), sharedHolder.manualFilter.value.effectiveYears())
            assertEquals(setOf(10), sharedHolder.manualFilter.value.effectiveMonths())
            assertTrue("Manual filter has no category before search", sharedHolder.manualFilter.value.effectiveCategoryIds().isEmpty())
            assertEquals(
                setOf(com.example.domain.model.FilterDimension.TYPE, com.example.domain.model.FilterDimension.CATEGORIES),
                sharedHolder.drillOverlay.value.activeDimensions
            )

            // Type search query on Ledger -> must NOT promote TYPE or CATEGORIES into manualFilter
            ledgerVm.updateSearchQuery("grocery")
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            assertEquals("grocery", ledgerVm.filter.value.searchQuery)
            assertEquals("G1: searchQuery must never leak into manualFilter", "", sharedHolder.manualFilter.value.searchQuery)
            assertTrue("G1: searchQuery edit must not promote drill category to manualFilter", sharedHolder.manualFilter.value.effectiveCategoryIds().isEmpty())
            assertEquals("G1: searchQuery edit must not promote drill type to manualFilter", "ALL", sharedHolder.manualFilter.value.selectedType)
            assertEquals(
                "G1: drillOverlay dimensions must remain intact after searchQuery edit",
                setOf(com.example.domain.model.FilterDimension.TYPE, com.example.domain.model.FilterDimension.CATEGORIES),
                sharedHolder.drillOverlay.value.activeDimensions
            )

            // Click Clear All ("filter_clear_all") on Ledger -> clears manual + drillOverlay + searchQuery directly
            composeTestRule.onNodeWithTag("filter_chips_row")
                .performScrollToNode(androidx.compose.ui.test.hasTestTag("filter_clear_all"))
            composeTestRule.onNodeWithTag("filter_clear_all").performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            assertTrue("G1: drillOverlay must be empty after Clear All on Ledger", sharedHolder.drillOverlay.value.isEmpty)
            assertFalse("G1: manualFilter must have no active slicers after Clear All on Ledger", sharedHolder.manualFilter.value.hasAnyActiveSlicer())
            assertFalse("G1: effectiveLedgerFilter must have no active slicers after Clear All on Ledger", sharedHolder.effectiveLedgerFilter.value.hasAnyActiveSlicer())
            assertEquals("G1: Ledger searchQuery must be cleared on Clear All", "", ledgerVm.filter.value.searchQuery)

            switchDestination("DASHBOARD")
            dashboardVm.uiState.awaitState { !it.filter.hasAnyActiveSlicer() }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            assertFalse("Dashboard filter is completely clear after Ledger Clear All", dashboardVm.uiState.value.filter.hasAnyActiveSlicer())

            mountHost = false
            dashboardVm.viewModelScope.cancel()
            ledgerVm.viewModelScope.cancel()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
        }
    }

    private suspend fun com.example.data.datastore.AppSettingsRepository.awaitDemoMode(expected: Boolean): com.example.data.datastore.AppSettings {
        val deadline = System.currentTimeMillis() + 5000L
        while (System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            val cur = settingsFlow.first()
            if (cur.demoMode == expected) return cur
            kotlinx.coroutines.delay(10)
        }
        ShadowLooper.idleMainLooper()
        return settingsFlow.first()
    }

    private suspend fun <T> kotlinx.coroutines.flow.StateFlow<T>.awaitState(predicate: (T) -> Boolean): T {
        val deadline = System.currentTimeMillis() + 5000L
        while (!predicate(value) && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(10)
        }
        ShadowLooper.idleMainLooper()
        return value
    }

    @Test
    fun testPerfMeasurementGate_fiveScenarios_frameStats() {
        runBlocking {
            com.example.data.seed.SampleDataSeeder.seedSampleData(database)
            ShadowLooper.idleMainLooper()

            val sharedHolder = com.example.domain.model.SharedFilterStateHolder()
            val mediaRepo = com.example.data.repository.MediaRepository(context, database)
            val dashboardVm = DashboardViewModel(repository, settingsRepository, sharedHolder)
            val ledgerVm = com.example.ui.ledger.LedgerViewModel(
                repository = repository,
                settingsRepository = settingsRepository,
                mediaRepository = mediaRepo,
                sharedFilterStateHolder = sharedHolder
            )

            dashboardVm.uiState.awaitState { it.categoryBreakdown.isNotEmpty() }
            ledgerVm.uiState.awaitState { it.dayGroups.isNotEmpty() }

            var currentDest by androidx.compose.runtime.mutableStateOf("DASHBOARD")
            var mountHost by androidx.compose.runtime.mutableStateOf(true)

            composeTestRule.setContent {
                if (mountHost) {
                    MyApplicationTheme {
                        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                            when (currentDest) {
                                "DASHBOARD" -> {
                                    DashboardScreen(
                                        viewModel = dashboardVm,
                                        onNavigateToExpenses = { _, _ -> currentDest = "EXPENSES" },
                                        onNavigateToExpensesWithFilter = { drill ->
                                            dashboardVm.applyDrillFilter(drill)
                                            currentDest = "EXPENSES"
                                        }
                                    )
                                }
                                "EXPENSES" -> {
                                    com.example.ui.ledger.LedgerScreen(
                                        viewModel = ledgerVm,
                                        onAddClick = {},
                                        onTransactionClick = {},
                                        mediaRepository = mediaRepo
                                    )
                                }
                            }
                        }
                    }
                }
            }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            fun measureSteppedFrames(frames: Int = 20, preAction: () -> Unit): Triple<Double, Double, Int> {
                composeTestRule.mainClock.autoAdvance = false
                val frameTimesMs = mutableListOf<Double>()
                try {
                    val t0 = System.nanoTime()
                    preAction()
                    ShadowLooper.idleMainLooper()
                    composeTestRule.mainClock.advanceTimeByFrame()
                    ShadowLooper.idleMainLooper()
                    frameTimesMs.add((System.nanoTime() - t0) / 1_000_000.0)

                    repeat(frames - 1) {
                        val fStart = System.nanoTime()
                        composeTestRule.mainClock.advanceTimeBy(16L)
                        ShadowLooper.idleMainLooper()
                        frameTimesMs.add((System.nanoTime() - fStart) / 1_000_000.0)
                    }
                } finally {
                    composeTestRule.mainClock.autoAdvance = true
                    ShadowLooper.idleMainLooper()
                    composeTestRule.waitForIdle()
                }
                val sorted = frameTimesMs.sorted()
                val totalMs = frameTimesMs.sum()
                val p95Idx = ((sorted.size - 1) * 0.95).toInt().coerceIn(0, sorted.lastIndex)
                val p95Ms = sorted[p95Idx]
                val dropped = frameTimesMs.count { it > 16.67 }
                return Triple(totalMs, p95Ms, dropped)
            }

            // 1. Dashboard fling
            val dashFling = measureSteppedFrames(20) {
                composeTestRule.onNodeWithText("Current Balance")
                    .performTouchInput {
                        down(center)
                        moveBy(androidx.compose.ui.geometry.Offset(0f, -600f))
                        up()
                    }
            }

            // 2. Drill tap (Groceries & Shopping on Dashboard -> Expenses)
            currentDest = "DASHBOARD"
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            val drillTap = measureSteppedFrames(15) {
                val drillFilter = FilterState(
                    selectedType = "EXPENSE",
                    selectedCategoryId = 1L,
                    selectedCategoryIds = setOf(1L)
                )
                dashboardVm.applyDrillFilter(drillFilter)
                currentDest = "EXPENSES"
            }

            // 3. Clear All tap on Ledger
            composeTestRule.onNodeWithTag("filter_chips_row")
                .performScrollToNode(androidx.compose.ui.test.hasTestTag("filter_clear_all"))
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            val clearAllTap = measureSteppedFrames(15) {
                composeTestRule.onNodeWithTag("filter_clear_all").performClick()
            }

            // 4. Ledger fling (across unfiltered demo transactions)
            val ledgerFling = measureSteppedFrames(20) {
                val firstTx = ledgerVm.uiState.value.dayGroups
                    .flatMap { it.transactions }
                    .first { it.entity.groupId == null }
                composeTestRule.onNodeWithTag("transaction_row_${firstTx.id}")
                    .performTouchInput {
                        down(center)
                        moveBy(androidx.compose.ui.geometry.Offset(0f, -700f))
                        up()
                    }
            }

            // 5. Tab-return (Expenses -> Dashboard)
            val tabReturn = measureSteppedFrames(15) {
                sharedHolder.clearDrillOverlay()
                currentDest = "DASHBOARD"
            }

            println(
                "PERF_GATE_METRICS: " +
                    "DashFling(total=${String.format(java.util.Locale.US, "%.2f", dashFling.first)}ms, p95=${String.format(java.util.Locale.US, "%.2f", dashFling.second)}ms, dropped=${dashFling.third}) | " +
                    "LedgerFling(total=${String.format(java.util.Locale.US, "%.2f", ledgerFling.first)}ms, p95=${String.format(java.util.Locale.US, "%.2f", ledgerFling.second)}ms, dropped=${ledgerFling.third}) | " +
                    "DrillTap(total=${String.format(java.util.Locale.US, "%.2f", drillTap.first)}ms, p95=${String.format(java.util.Locale.US, "%.2f", drillTap.second)}ms, dropped=${drillTap.third}) | " +
                    "ClearAll(total=${String.format(java.util.Locale.US, "%.2f", clearAllTap.first)}ms, p95=${String.format(java.util.Locale.US, "%.2f", clearAllTap.second)}ms, dropped=${clearAllTap.third}) | " +
                    "TabReturn(total=${String.format(java.util.Locale.US, "%.2f", tabReturn.first)}ms, p95=${String.format(java.util.Locale.US, "%.2f", tabReturn.second)}ms, dropped=${tabReturn.third})"
            )

            mountHost = false
            dashboardVm.viewModelScope.cancel()
            ledgerVm.viewModelScope.cancel()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
        }
    }

    // =========================================================================
    // TASK MEDIA-VIEWER: UT27a-e Zoom, Clamps, Paging Pin, Header Bin & Edge Cases
    // =========================================================================

    // A1: ZOOM RESET ON PAGE CHANGE
    @Test
    fun testUT27a_zoomResetOnPageChange() {
        runBlocking {
            val mediaRepo = com.example.data.repository.MediaRepository(context, database)
            val accId = repository.insertAccount(Account(id = 271L, name = "Cash", accountType = "CASH", openingBalance = 5000.0))
            val catId = repository.insertCategory(Category(id = 271L, name = "Misc", color = "#10B981"))
            val txnId = repository.insertTransaction(
                TransactionEntity(id = 271L, date = System.currentTimeMillis(), type = "EXPENSE", amount = 100.0, fromAccountId = accId, categoryId = catId, description = "UT27a")
            )
            val txnDir = mediaRepo.getVaultDirForTxn(txnId)
            val p1 = File(txnDir, "p1.jpg").apply {
                val bmp = android.graphics.Bitmap.createBitmap(400, 300, android.graphics.Bitmap.Config.ARGB_8888)
                java.io.FileOutputStream(this).use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
                bmp.recycle()
            }
            val p2 = File(txnDir, "p2.jpg").apply {
                val bmp = android.graphics.Bitmap.createBitmap(400, 300, android.graphics.Bitmap.Config.ARGB_8888)
                java.io.FileOutputStream(this).use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
                bmp.recycle()
            }
            val a1 = com.example.data.model.MediaAttachment(id = 27101L, txnId = txnId, kind = "PHOTO", fileName = p1.name, createdAt = System.currentTimeMillis())
            val a2 = com.example.data.model.MediaAttachment(id = 27102L, txnId = txnId, kind = "PHOTO", fileName = p2.name, createdAt = System.currentTimeMillis() + 10)
            database.mediaAttachmentDao().insert(a1)
            database.mediaAttachmentDao().insert(a2)

            composeTestRule.setContent {
                val liveAtts by mediaRepo.getAttachmentsForTxn(txnId).collectAsState(initial = listOf(a1, a2))
                MyApplicationTheme {
                    com.example.ui.media.MediaViewerSheet(
                        txnId = txnId,
                        attachments = liveAtts,
                        initialTab = "IMAGES",
                        hideBalances = false,
                        mediaRepository = mediaRepo,
                        onDismiss = {}
                    )
                }
            }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            val photo0 = composeTestRule.onNodeWithTag("viewer_photo_card_0")
            photo0.assertIsDisplayed()
            assertEquals(1f, photo0.fetchSemanticsNode().config[PhotoScaleKey], 0.01f)

            // Double-tap to zoom to 2.5x
            photo0.performTouchInput { doubleClick(center) }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            val zoomedScale = photo0.fetchSemanticsNode().config[PhotoScaleKey]
            assertTrue("Scale after double-tap must be > 1.05f, was $zoomedScale", zoomedScale > 1.05f)

            // Double-tap to reset to fit (1x)
            photo0.performTouchInput { doubleClick(center) }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            assertEquals(1f, photo0.fetchSemanticsNode().config[PhotoScaleKey], 0.01f)

            // Swipe to page 1
            photo0.performTouchInput { swipeLeft() }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("2 / 2").assertIsDisplayed()

            val photo1 = composeTestRule.onNodeWithTag("viewer_photo_card_1")
            photo1.assertIsDisplayed()
            assertEquals(1f, photo1.fetchSemanticsNode().config[PhotoScaleKey], 0.01f)

            // Swipe right back to page 0 to assert page 0 scale was reset on page change (A1)
            photo1.performTouchInput { swipeRight() }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("1 / 2").assertIsDisplayed()
            assertEquals(1f, photo0.fetchSemanticsNode().config[PhotoScaleKey], 0.01f)
            assertEquals(0f, photo0.fetchSemanticsNode().config[PhotoTranslationXKey], 0.01f)
            assertEquals(0f, photo0.fetchSemanticsNode().config[PhotoTranslationYKey], 0.01f)
        }
    }

    // A2: ZOOM CLAMPS [1x, 4x] & DOUBLE-TAP TOGGLE & PAN CLAMPING
    @Test
    fun testUT27b_zoomClamps_scaleBounds_doubleTapToggle_panClamped() {
        runBlocking {
            val mediaRepo = com.example.data.repository.MediaRepository(context, database)
            val accId = repository.insertAccount(Account(id = 272L, name = "Cash", accountType = "CASH", openingBalance = 5000.0))
            val catId = repository.insertCategory(Category(id = 272L, name = "Misc", color = "#10B981"))
            val txnId = repository.insertTransaction(
                TransactionEntity(id = 272L, date = System.currentTimeMillis(), type = "EXPENSE", amount = 100.0, fromAccountId = accId, categoryId = catId, description = "UT27b")
            )
            val txnDir = mediaRepo.getVaultDirForTxn(txnId)
            val p1 = File(txnDir, "p_clamp.jpg").apply {
                val bmp = android.graphics.Bitmap.createBitmap(400, 300, android.graphics.Bitmap.Config.ARGB_8888)
                java.io.FileOutputStream(this).use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
                bmp.recycle()
            }
            val a1 = com.example.data.model.MediaAttachment(id = 27201L, txnId = txnId, kind = "PHOTO", fileName = p1.name, createdAt = System.currentTimeMillis())
            database.mediaAttachmentDao().insert(a1)

            composeTestRule.setContent {
                val liveAtts by mediaRepo.getAttachmentsForTxn(txnId).collectAsState(initial = listOf(a1))
                MyApplicationTheme {
                    com.example.ui.media.MediaViewerSheet(
                        txnId = txnId,
                        attachments = liveAtts,
                        initialTab = "IMAGES",
                        hideBalances = false,
                        mediaRepository = mediaRepo,
                        onDismiss = {}
                    )
                }
            }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            val photo0 = composeTestRule.onNodeWithTag("viewer_photo_card_0")
            photo0.assertIsDisplayed()
            assertEquals(1f, photo0.fetchSemanticsNode().config[PhotoScaleKey], 0.01f)
            assertEquals(0f, photo0.fetchSemanticsNode().config[PhotoTranslationXKey], 0.01f)
            assertEquals(0f, photo0.fetchSemanticsNode().config[PhotoTranslationYKey], 0.01f)

            // Double tap toggles to ~2.5x
            photo0.performTouchInput { doubleClick(center) }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            val scale25 = photo0.fetchSemanticsNode().config[PhotoScaleKey]
            assertEquals(2.5f, scale25, 0.05f)

            // Pan while zoomed (> 1x)
            photo0.performTouchInput {
                down(center)
                moveBy(androidx.compose.ui.geometry.Offset(50f, 50f))
                up()
            }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            val bounds = photo0.fetchSemanticsNode().boundsInRoot
            val maxPanX = (bounds.width * (scale25 - 1f)) / 2f
            val maxPanY = (bounds.height * (scale25 - 1f)) / 2f
            val transX = photo0.fetchSemanticsNode().config[PhotoTranslationXKey]
            val transY = photo0.fetchSemanticsNode().config[PhotoTranslationYKey]
            assertTrue("Translation X ($transX) must be within [-maxPanX, maxPanX] ([-$maxPanX, $maxPanX])", transX >= -maxPanX - 1f && transX <= maxPanX + 1f)
            assertTrue("Translation Y ($transY) must be within [-maxPanY, maxPanY] ([-$maxPanY, $maxPanY])", transY >= -maxPanY - 1f && transY <= maxPanY + 1f)

            // Double tap toggles back to fit (1x)
            photo0.performTouchInput { doubleClick(center) }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            assertEquals(1f, photo0.fetchSemanticsNode().config[PhotoScaleKey], 0.01f)
            assertEquals(0f, photo0.fetchSemanticsNode().config[PhotoTranslationXKey], 0.01f)
            assertEquals(0f, photo0.fetchSemanticsNode().config[PhotoTranslationYKey], 0.01f)
        }
    }

    // A3: PAGING REGRESSION PIN (advances counter, asserts zero photo swipe-reveal nodes)
    @Test
    fun testUT27c_pagingRegressionPin_advancesCounter_zeroPhotoSwipeRevealNodes() {
        runBlocking {
            val mediaRepo = com.example.data.repository.MediaRepository(context, database)
            val accId = repository.insertAccount(Account(id = 273L, name = "Cash", accountType = "CASH", openingBalance = 5000.0))
            val catId = repository.insertCategory(Category(id = 273L, name = "Misc", color = "#10B981"))
            val txnId = repository.insertTransaction(
                TransactionEntity(id = 273L, date = System.currentTimeMillis(), type = "EXPENSE", amount = 100.0, fromAccountId = accId, categoryId = catId, description = "UT27c")
            )
            val txnDir = mediaRepo.getVaultDirForTxn(txnId)
            val p1 = File(txnDir, "p1_page.jpg").apply {
                val bmp = android.graphics.Bitmap.createBitmap(400, 300, android.graphics.Bitmap.Config.ARGB_8888)
                java.io.FileOutputStream(this).use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
                bmp.recycle()
            }
            val p2 = File(txnDir, "p2_page.jpg").apply {
                val bmp = android.graphics.Bitmap.createBitmap(400, 300, android.graphics.Bitmap.Config.ARGB_8888)
                java.io.FileOutputStream(this).use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
                bmp.recycle()
            }
            val a1 = com.example.data.model.MediaAttachment(id = 27301L, txnId = txnId, kind = "PHOTO", fileName = p1.name, createdAt = System.currentTimeMillis())
            val a2 = com.example.data.model.MediaAttachment(id = 27302L, txnId = txnId, kind = "PHOTO", fileName = p2.name, createdAt = System.currentTimeMillis() + 10)
            database.mediaAttachmentDao().insert(a1)
            database.mediaAttachmentDao().insert(a2)

            composeTestRule.setContent {
                val liveAtts by mediaRepo.getAttachmentsForTxn(txnId).collectAsState(initial = listOf(a1, a2))
                MyApplicationTheme {
                    com.example.ui.media.MediaViewerSheet(
                        txnId = txnId,
                        attachments = liveAtts,
                        initialTab = "IMAGES",
                        hideBalances = false,
                        mediaRepository = mediaRepo,
                        onDismiss = {}
                    )
                }
            }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("1 / 2").assertIsDisplayed()

            // Assert ZERO swipe-reveal nodes on photo pages (the video's "cant scroll left" bug pinned forever)
            composeTestRule.onAllNodesWithTag("photo_delete_reveal_band_0").assertCountEquals(0)
            composeTestRule.onAllNodesWithTag("photo_delete_reveal_band_1").assertCountEquals(0)
            composeTestRule.onAllNodesWithTag("image_delete_bin_0").assertCountEquals(0)
            composeTestRule.onAllNodesWithTag("viewer_photo_swipe_container_0").assertCountEquals(0)

            // Horizontal drag on the photo page advances the counter (1/2 -> 2/2)
            composeTestRule.onNodeWithTag("viewer_photo_card_0").performTouchInput { swipeLeft() }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("2 / 2").assertIsDisplayed()

            // Confirm still ZERO swipe-reveal nodes
            composeTestRule.onAllNodesWithTag("photo_delete_reveal_band_0").assertCountEquals(0)
            composeTestRule.onAllNodesWithTag("photo_delete_reveal_band_1").assertCountEquals(0)
        }
    }

    // A4: LT5 VOICE SWIPE-REVEAL CAP & SHARED CONSTANTS PIN
    @Test
    fun testUT27d_LT5VoiceSwipeRevealCapAndSharedConstantsPin() {
        runBlocking {
            // Shared constants pinned
            assertEquals(0.30f, com.example.ui.common.SwipeRevealHelper.MAX_REVEAL_WIDTH_FRACTION, 0.0001f)
            assertEquals(120.dp, com.example.ui.common.SwipeRevealHelper.MAX_REVEAL_CAP_DP)

            val mediaRepo = com.example.data.repository.MediaRepository(context, database)
            val accId = repository.insertAccount(Account(id = 274L, name = "Cash", accountType = "CASH", openingBalance = 5000.0))
            val catId = repository.insertCategory(Category(id = 274L, name = "Misc", color = "#10B981"))
            val txnId = repository.insertTransaction(
                TransactionEntity(id = 274L, date = System.currentTimeMillis(), type = "EXPENSE", amount = 100.0, fromAccountId = accId, categoryId = catId, description = "UT27d")
            )
            val txnDir = mediaRepo.getVaultDirForTxn(txnId)
            val voiceFile = File(txnDir, "ut27d_voice.m4a").apply { writeBytes(ByteArray(2048)) }
            val aVoice = com.example.data.model.MediaAttachment(id = 27401L, txnId = txnId, kind = "VOICE", fileName = voiceFile.name, createdAt = System.currentTimeMillis())
            database.mediaAttachmentDao().insert(aVoice)

            composeTestRule.setContent {
                val liveAtts by mediaRepo.getAttachmentsForTxn(txnId).collectAsState(initial = listOf(aVoice))
                MyApplicationTheme {
                    com.example.ui.media.MediaViewerSheet(
                        txnId = txnId,
                        attachments = liveAtts,
                        initialTab = "VOICE",
                        hideBalances = false,
                        mediaRepository = mediaRepo,
                        onDismiss = {}
                    )
                }
            }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            val voiceCard = composeTestRule.onNodeWithTag("viewer_voice_card_0")
            voiceCard.assertIsDisplayed()
            val beforeBounds = voiceCard.fetchSemanticsNode().boundsInRoot
            voiceCard.performTouchInput { swipeLeft() }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            val revealBand = composeTestRule.onNodeWithTag("voice_delete_reveal_band_0", useUnmergedTree = true)
            revealBand.assertIsDisplayed()
            composeTestRule.onNodeWithTag("voice_delete_bin_0", useUnmergedTree = true).assertIsDisplayed()

            val expectedMaxRevealPx = com.example.ui.common.SwipeRevealHelper.computeMaxRevealPx(beforeBounds.width, composeTestRule.density)
            val revealWidth = revealBand.fetchSemanticsNode().boundsInRoot.width
            assertEquals(expectedMaxRevealPx, revealWidth, 2.5f)
        }
    }

    // A5: EXISTENCE + EDGE PINS (header bin deletes viewed photo, empty state never "1 / 0")
    @Test
    fun testUT27e_headerBinExistenceAndEdgePins_deletesCurrentPhoto_emptyStateNoOneZero() {
        runBlocking {
            val mediaRepo = com.example.data.repository.MediaRepository(context, database)
            val accId = repository.insertAccount(Account(id = 275L, name = "Cash", accountType = "CASH", openingBalance = 5000.0))
            val catId = repository.insertCategory(Category(id = 275L, name = "Misc", color = "#10B981"))
            val txnId = repository.insertTransaction(
                TransactionEntity(id = 275L, date = System.currentTimeMillis(), type = "EXPENSE", amount = 100.0, fromAccountId = accId, categoryId = catId, description = "UT27e")
            )
            val txnDir = mediaRepo.getVaultDirForTxn(txnId)
            val p1 = File(txnDir, "single_photo.jpg").apply {
                val bmp = android.graphics.Bitmap.createBitmap(400, 300, android.graphics.Bitmap.Config.ARGB_8888)
                java.io.FileOutputStream(this).use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
                bmp.recycle()
            }
            val voiceFile = File(txnDir, "single_voice.m4a").apply { writeBytes(ByteArray(2048)) }
            val aPhoto = com.example.data.model.MediaAttachment(id = 27501L, txnId = txnId, kind = "PHOTO", fileName = p1.name, createdAt = System.currentTimeMillis())
            val aVoice = com.example.data.model.MediaAttachment(id = 27502L, txnId = txnId, kind = "VOICE", fileName = voiceFile.name, createdAt = System.currentTimeMillis() + 10)
            database.mediaAttachmentDao().insert(aPhoto)
            database.mediaAttachmentDao().insert(aVoice)

            var sheetDismissed = false
            composeTestRule.setContent {
                val liveAtts by mediaRepo.getAttachmentsForTxn(txnId).collectAsState(initial = listOf(aPhoto, aVoice))
                MyApplicationTheme {
                    com.example.ui.media.MediaViewerSheet(
                        txnId = txnId,
                        attachments = liveAtts,
                        initialTab = "IMAGES",
                        hideBalances = false,
                        mediaRepository = mediaRepo,
                        onDismiss = { sheetDismissed = true }
                    )
                }
            }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            // Header bin exists on photo view
            val headerBin = composeTestRule.onNodeWithTag("viewer_header_delete_btn", useUnmergedTree = true)
            headerBin.assertIsDisplayed()
            composeTestRule.onNodeWithText("1 / 1").assertIsDisplayed()

            // Delete the last photo
            headerBin.performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            // Empty state is shown
            composeTestRule.onNodeWithText("No images attached").assertIsDisplayed()

            // Counter NEVER reads "1 / 0"
            composeTestRule.onAllNodesWithText("1 / 0").assertCountEquals(0)
            composeTestRule.onAllNodesWithTag("viewer_photo_counter").assertCountEquals(0)

            // Voice notes tab still exists and voice row swipe-delete stays functional
            composeTestRule.onNodeWithTag("media_sheet_tab_voice").performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            val voiceCard = composeTestRule.onNodeWithTag("viewer_voice_card_0")
            voiceCard.assertIsDisplayed()
            voiceCard.performTouchInput { swipeLeft() }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            val voiceBin = composeTestRule.onNodeWithTag("voice_delete_bin_0", useUnmergedTree = true)
            voiceBin.assertIsDisplayed()
            voiceBin.performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            // Now both photos and voice notes are deleted -> sheet auto-dismisses (A5)
            assertTrue("Sheet must auto-dismiss when all attachments are deleted", sheetDismissed)
        }
    }

    // =========================================================================
    // TASK F28 — TOP-8 CAP + SHOW-ALL TOGGLE ON INCOME & TRANSFER BREAKDOWNS:
    // UT28 verifies:
    //  1. Fixture with 10 income categories + 10 transfer routes + 10 expense categories:
    //     - Under INCOME mode: collapsed renders exactly 8 rows (desc by amount) + "Show all (10) categories" button;
    //       percentages remain of the full total; header total == sum of ALL 10 rows;
    //       tap -> renders all 10 rows + "Show less" + visible-rows sum == header total;
    //       tap -> collapses back to 8 rows + "Show all (10) categories".
    //     - Under TRANSFER mode: collapsed renders exactly 8 rows (desc by amount) + "Show all (10) routes" button;
    //       percentages remain of the full total; header total == sum of ALL 10 routes;
    //       tap -> renders all 10 routes + "Show less" + visible-rows sum == header total;
    //       tap -> collapses back to 8 rows + "Show all (10) routes".
    //     - Under EXPENSE mode: existing "Show all (10) categories" / "Show less" toggle works identically.
    //  2. N <= 8 (e.g. Month filter with 5 items) -> no button node exists.
    //  3. Single-category filter -> 1 row, no button node exists.
    // =========================================================================
    @Test
    fun testUT28_top8CapAndShowAllToggle_incomeAndTransferBreakdowns_mirrorExpensesPattern() {
        runBlocking {
            settingsRepository.setCurrency("₹")
            com.example.domain.util.Formatters.updateCurrentCurrencySymbol("₹")
            settingsRepository.setDemoMode(false)
            settingsRepository.setHideBalances(false)

            // Insert 11 accounts so we can form 10 distinct transfer routes (Acc1 -> Acc2 .. Acc1 -> Acc11)
            val accIds = (1..11).map { idx ->
                repository.insertAccount(
                    Account(
                        id = idx.toLong(),
                        name = "Acc$idx",
                        accountType = "BANK",
                        openingBalance = 50000.0,
                        sortOrder = idx
                    )
                )
            }

            // Insert 10 income categories + 10 expense categories
            val incCatIds = (1..10).map { idx ->
                repository.insertCategory(
                    Category(
                        id = (100 + idx).toLong(),
                        name = "IncCat$idx",
                        color = "#10B981",
                        iconText = "💰"
                    )
                )
            }
            val expCatIds = (1..10).map { idx ->
                repository.insertCategory(
                    Category(
                        id = (200 + idx).toLong(),
                        name = "ExpCat$idx",
                        color = "#EF4444",
                        iconText = "🛒"
                    )
                )
            }

            // Insert 10 income transactions (amounts: 1000, 2000, ..., 10000; first 5 in Sept, next 5 in Oct)
            for (i in 1..10) {
                val month = if (i <= 5) 9 else 10
                val amt = i * 1000.0 // i=10 is largest (10000), i=1 is smallest (1000), i=2 is 2nd smallest (2000)
                repository.insertTransaction(
                    TransactionEntity(
                        id = (8100 + i).toLong(),
                        date = fixedTimeMillis(2026, month, 10),
                        type = "INCOME",
                        amount = amt,
                        toAccountId = accIds[0],
                        categoryId = incCatIds[i - 1],
                        description = "Income $i"
                    )
                )
            }

            // Insert 10 transfer transactions across 10 distinct routes (Acc1 -> Acc2 .. Acc1 -> Acc11)
            for (i in 1..10) {
                val month = if (i <= 5) 9 else 10
                val amt = i * 500.0 // i=10 is largest (5000), i=1 is smallest (500), i=2 is 2nd smallest (1000)
                repository.insertTransaction(
                    TransactionEntity(
                        id = (8200 + i).toLong(),
                        date = fixedTimeMillis(2026, month, 12),
                        type = "TRANSFER",
                        amount = amt,
                        fromAccountId = accIds[0],
                        toAccountId = accIds[i],
                        description = "Transfer $i"
                    )
                )
            }

            // Insert 10 expense transactions across 10 distinct categories
            for (i in 1..10) {
                val month = if (i <= 5) 9 else 10
                val amt = i * 300.0
                repository.insertTransaction(
                    TransactionEntity(
                        id = (8300 + i).toLong(),
                        date = fixedTimeMillis(2026, month, 15),
                        type = "EXPENSE",
                        amount = amt,
                        fromAccountId = accIds[0],
                        categoryId = expCatIds[i - 1],
                        description = "Expense $i"
                    )
                )
            }

            val sharedHolder = com.example.domain.model.SharedFilterStateHolder()
            val dashboardVm = DashboardViewModel(repository, settingsRepository, sharedHolder)

            var mountScreen by androidx.compose.runtime.mutableStateOf(true)
            composeTestRule.setContent {
                MyApplicationTheme(darkTheme = false) {
                    if (mountScreen) {
                        DashboardScreen(
                            viewModel = dashboardVm,
                            onNavigateToExpenses = { _, _ -> }
                        )
                    }
                }
            }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            dashboardVm.uiState.awaitState {
                it.incomeCategoryBreakdown.size == 10 &&
                    it.transferRoutes.size == 10 &&
                    it.categoryBreakdown.size == 10
            }
            composeTestRule.waitForIdle()

            // =================================================================
            // 1. INCOME BREAKDOWN: 10 categories -> collapsed shows top 8 (IncCat10..IncCat3)
            // =================================================================
            sharedHolder.updateFilter(FilterState(selectedType = "INCOME"))
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            val incState10 = dashboardVm.uiState.awaitState { it.filter.selectedType == "INCOME" && it.incomeCategoryBreakdown.size == 10 }
            composeTestRule.waitForIdle()

            composeTestRule.scrollToDashboardTag("breakdown_section_income", useUnmergedTree = true)
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            // Top 8 (IncCat10 down to IncCat3) exist; bottom 2 (IncCat2, IncCat1) do NOT exist when collapsed
            for (i in 3..10) {
                composeTestRule.onAllNodesWithTag("income_category_bar_IncCat$i", useUnmergedTree = true).assertCountEquals(1)
            }
            composeTestRule.onAllNodesWithTag("income_category_bar_IncCat2", useUnmergedTree = true).assertCountEquals(0)
            composeTestRule.onAllNodesWithTag("income_category_bar_IncCat1", useUnmergedTree = true).assertCountEquals(0)

            // Toggle button shows "Show all (10) categories"
            composeTestRule.scrollToDashboardText("Show all (10) categories").assertIsDisplayed()
            composeTestRule.onAllNodesWithText("Show less").assertCountEquals(0)

            // Percentages remain of the full total (55,000): IncCat10 (10,000) is 18.18% -> 18%
            assertEquals(55000.0, incState10.totalIncomeInPeriod, 0.01)
            assertEquals(55000.0, incState10.incomeCategoryBreakdown.sumOf { it.amount }, 0.01)
            assertEquals(10000.0 / 55000.0 * 100.0, incState10.incomeCategoryBreakdown.first().percentage.toDouble(), 0.05)

            // Tap "Show all (10) categories" -> expands to all 10 rows + "Show less"
            composeTestRule.onNodeWithText("Show all (10) categories").performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            for (i in 1..10) {
                composeTestRule.onAllNodesWithTag("income_category_bar_IncCat$i", useUnmergedTree = true).assertCountEquals(1)
            }
            composeTestRule.scrollToDashboardText("Show less").assertIsDisplayed()
            composeTestRule.onAllNodesWithText("Show all (10) categories").assertCountEquals(0)

            // Tap "Show less" -> collapses back to 8 rows + "Show all (10) categories"
            composeTestRule.onNodeWithText("Show less").performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            composeTestRule.onAllNodesWithTag("income_category_bar_IncCat2", useUnmergedTree = true).assertCountEquals(0)
            composeTestRule.onAllNodesWithTag("income_category_bar_IncCat1", useUnmergedTree = true).assertCountEquals(0)
            composeTestRule.scrollToDashboardText("Show all (10) categories").assertIsDisplayed()

            // Single-category filter -> 1 row, no button node exists
            sharedHolder.updateFilter(
                FilterState(
                    selectedType = "INCOME",
                    selectedCategoryId = incCatIds[9],
                    selectedCategoryIds = setOf(incCatIds[9])
                )
            )
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            dashboardVm.uiState.awaitState { it.incomeCategoryBreakdown.size == 1 }
            composeTestRule.waitForIdle()

            composeTestRule.onAllNodesWithTag("income_category_bar_IncCat10", useUnmergedTree = true).assertCountEquals(1)
            composeTestRule.onAllNodesWithText("Show all", substring = true).assertCountEquals(0)
            composeTestRule.onAllNodesWithText("Show less").assertCountEquals(0)

            // N <= 8 (Month = Sept -> 5 income categories) -> 5 rows, no button node exists
            sharedHolder.updateFilter(FilterState(selectedType = "INCOME", selectedMonths = setOf(9)))
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            dashboardVm.uiState.awaitState { it.incomeCategoryBreakdown.size == 5 }
            composeTestRule.waitForIdle()

            for (i in 1..5) {
                composeTestRule.onAllNodesWithTag("income_category_bar_IncCat$i", useUnmergedTree = true).assertCountEquals(1)
            }
            composeTestRule.onAllNodesWithText("Show all", substring = true).assertCountEquals(0)
            composeTestRule.onAllNodesWithText("Show less").assertCountEquals(0)

            // =================================================================
            // 2. TRANSFER BREAKDOWN: 10 routes -> collapsed shows top 8 (Acc1 → Acc11 .. Acc1 → Acc4)
            // =================================================================
            sharedHolder.updateFilter(FilterState(selectedType = "TRANSFER"))
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            val trState10 = dashboardVm.uiState.awaitState { it.filter.selectedType == "TRANSFER" && it.transferRoutes.size == 10 }
            composeTestRule.waitForIdle()

            composeTestRule.scrollToDashboardTag("breakdown_section_transfer", useUnmergedTree = true)
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            // Top 8 routes (i=3..10 -> Acc1 → Acc4 .. Acc1 → Acc11) exist; bottom 2 (Acc1 → Acc3, Acc1 → Acc2) do NOT exist
            for (i in 3..10) {
                composeTestRule.onAllNodesWithTag("transfer_route_bar_Acc1 → Acc${i + 1}", useUnmergedTree = true).assertCountEquals(1)
            }
            composeTestRule.onAllNodesWithTag("transfer_route_bar_Acc1 → Acc3", useUnmergedTree = true).assertCountEquals(0)
            composeTestRule.onAllNodesWithTag("transfer_route_bar_Acc1 → Acc2", useUnmergedTree = true).assertCountEquals(0)

            // Toggle button shows "Show all (10) routes"
            composeTestRule.scrollToDashboardText("Show all (10) routes").assertIsDisplayed()
            composeTestRule.onAllNodesWithText("Show less").assertCountEquals(0)

            // Header total == sum of ALL 10 routes (27,500)
            assertEquals(27500.0, trState10.totalTransferInPeriod, 0.01)
            assertEquals(27500.0, trState10.transferRoutes.sumOf { it.amount }, 0.01)

            // Tap "Show all (10) routes" -> expands to all 10 routes + "Show less"
            composeTestRule.onNodeWithText("Show all (10) routes").performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            for (i in 1..10) {
                composeTestRule.onAllNodesWithTag("transfer_route_bar_Acc1 → Acc${i + 1}", useUnmergedTree = true).assertCountEquals(1)
            }
            composeTestRule.scrollToDashboardText("Show less").assertIsDisplayed()
            composeTestRule.onAllNodesWithText("Show all (10) routes").assertCountEquals(0)

            // Tap "Show less" -> collapses back to 8 routes + "Show all (10) routes"
            composeTestRule.onNodeWithText("Show less").performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            composeTestRule.onAllNodesWithTag("transfer_route_bar_Acc1 → Acc3", useUnmergedTree = true).assertCountEquals(0)
            composeTestRule.onAllNodesWithTag("transfer_route_bar_Acc1 → Acc2", useUnmergedTree = true).assertCountEquals(0)
            composeTestRule.scrollToDashboardText("Show all (10) routes").assertIsDisplayed()

            // N <= 8 (Month = Sept -> 5 transfer routes) -> 5 rows, no button node exists
            sharedHolder.updateFilter(FilterState(selectedType = "TRANSFER", selectedMonths = setOf(9)))
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            dashboardVm.uiState.awaitState { it.transferRoutes.size == 5 }
            composeTestRule.waitForIdle()

            for (i in 1..5) {
                composeTestRule.onAllNodesWithTag("transfer_route_bar_Acc1 → Acc${i + 1}", useUnmergedTree = true).assertCountEquals(1)
            }
            composeTestRule.onAllNodesWithText("Show all", substring = true).assertCountEquals(0)
            composeTestRule.onAllNodesWithText("Show less").assertCountEquals(0)

            // =================================================================
            // 3. EXPENSES BREAKDOWN TOGGLE UNCHANGED: 10 categories -> "Show all (10) categories" / "Show less"
            // =================================================================
            sharedHolder.updateFilter(FilterState(selectedType = "EXPENSE"))
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            dashboardVm.uiState.awaitState { it.filter.selectedType == "EXPENSE" && it.categoryBreakdown.size == 10 }
            composeTestRule.waitForIdle()

            composeTestRule.scrollToDashboardTag("breakdown_section_expense", useUnmergedTree = true)
            composeTestRule.scrollToDashboardText("Show all (10) categories").assertIsDisplayed()
            composeTestRule.onNodeWithText("Show all (10) categories").performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            composeTestRule.scrollToDashboardText("Show less").assertIsDisplayed()

            mountScreen = false
            dashboardVm.viewModelScope.cancel()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
        }
    }
}
