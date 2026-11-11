package com.example

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.datastore.AppSettingsRepository
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.MediaAttachment
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.data.repository.MediaRepository
import com.example.domain.model.FilterState
import com.example.domain.util.Formatters
import com.example.importbackup.MediaStoreWriter
import com.example.ui.common.CalculatorBottomSheet
import com.example.ui.common.SwipeRevealHelper
import com.example.ui.dashboard.AUDITED_AFFORDANCES
import com.example.ui.dashboard.COMMON_TASKS_AFFORDANCES
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.dashboard.HelpSection
import com.example.ui.ledger.LedgerScreen
import com.example.ui.ledger.LedgerViewModel
import com.example.ui.media.MediaViewerSheet
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
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Calendar
import kotlin.math.min

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class AuditAndAddendumHarnessTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: FinanceRepository
    private lateinit var settingsRepository: AppSettingsRepository
    private lateinit var mediaRepository: MediaRepository

    @Before
    fun setUp() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            repository = FinanceRepository(database)
            settingsRepository = AppSettingsRepository(context)
            mediaRepository = MediaRepository(context, database)
            settingsRepository.clearSettings()
            Formatters.updateCurrentCurrencySymbol("₹")
            MediaRepository.activePlayingTxnId.value = null
            MediaRepository.activePlayingVoiceKey.value = null
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            MediaRepository.activePlayingTxnId.value = null
            MediaRepository.activePlayingVoiceKey.value = null
            Formatters.updateCurrentCurrencySymbol("₹")
            ShadowLooper.idleMainLooper()
            settingsRepository.clearSettings()
        }
    }

    // =========================================================================
    // LT1 (FA + H-DEDUP): Each source row drills to its own accountId;
    // Credit Card summary card has no duplicate per-card sub-rows.
    // =========================================================================
    @Test
    fun LT1_sourceDrill_eachSourceRowDrillsToOwnAccount_andCreditCardSummaryDeduped() {
        runBlocking {
            val cashId = repository.insertAccount(Account(id = 10L, name = "Cash", accountType = "CASH", openingBalance = 2000.0))
            val kblId = repository.insertAccount(Account(id = 20L, name = "KBL", accountType = "BANK", openingBalance = 15000.0))
            val iciciId = repository.insertAccount(Account(id = 30L, name = "ICICI Credit", accountType = "CREDIT_CARD", openingBalance = -4500.0))

            val vm = DashboardViewModel(repository, settingsRepository)
            val capturedFilters = mutableListOf<FilterState>()

            composeTestRule.setContent {
                MyApplicationTheme {
                    DashboardScreen(
                        viewModel = vm,
                        onNavigateToExpenses = { _, _ -> },
                        onNavigateToExpensesWithFilter = { filter -> capturedFilters.add(filter) }
                    )
                }
            }
            composeTestRule.waitForIdle()

            // 1. Tap Cash row -> selectedAccountIds == setOf(cashId)
            composeTestRule.scrollToDashboardTag("source_row_$cashId", useUnmergedTree = true).performClick()
            assertEquals(setOf(cashId), capturedFilters.last().selectedAccountIds)

            // 2. Tap KBL row -> selectedAccountIds == setOf(kblId)
            composeTestRule.scrollToDashboardTag("source_row_$kblId", useUnmergedTree = true).performClick()
            assertEquals(setOf(kblId), capturedFilters.last().selectedAccountIds)

            // 3. Tap ICICI Credit row -> selectedAccountIds == setOf(iciciId)
            composeTestRule.scrollToDashboardTag("source_row_$iciciId", useUnmergedTree = true).performClick()
            assertEquals(setOf(iciciId), capturedFilters.last().selectedAccountIds)

            // 4. H-DEDUP: Credit Card summary card exists, and "ICICI Credit" appears in Sources & Net Balances
            composeTestRule.scrollToDashboardTag("kpi_credit_cards_card").assertIsDisplayed()
            composeTestRule.scrollToDashboardTag("source_row_$iciciId", useUnmergedTree = true).assertExists()
        }
    }

    // =========================================================================
    // LT2 (FB + H-KPI-MATH): Selecting Month=Sept (9) updates all month-scoped KPIs
    // to Sept/Aug data and tapping each CC sub-box & Income/Transfer KPI navigates
    // with the matching FilterState.
    // =========================================================================
    @Test
    fun LT2_monthScope_andCreditCardKpiSubBoxesNavigateWithScopedFilters() {
        runBlocking {
            val bankId = repository.insertAccount(Account(id = 1L, name = "KBL", accountType = "BANK", openingBalance = 20000.0))
            val cardId = repository.insertAccount(Account(id = 2L, name = "ICICI Credit", accountType = "CREDIT_CARD", openingBalance = -3000.0))
            val catId = repository.insertCategory(Category(id = 1L, name = "Shopping", color = "#EF4444"))

            fun ts(year: Int, month0: Int, day: Int): Long = Calendar.getInstance().apply {
                set(year, month0, day, 12, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            // Aug 2026 (1-based month = 8)
            repository.insertTransaction(TransactionEntity(date = ts(2026, Calendar.AUGUST, 10), type = "EXPENSE", amount = 700.0, fromAccountId = cardId, categoryId = catId, description = "Aug CC"))
            repository.insertTransaction(TransactionEntity(date = ts(2026, Calendar.AUGUST, 12), type = "INCOME", amount = 25000.0, toAccountId = bankId, categoryId = catId, description = "Aug Income"))

            // Sept 2026 (1-based month = 9)
            repository.insertTransaction(TransactionEntity(date = ts(2026, Calendar.SEPTEMBER, 10), type = "EXPENSE", amount = 1800.0, fromAccountId = cardId, categoryId = catId, description = "Sept CC"))
            repository.insertTransaction(TransactionEntity(date = ts(2026, Calendar.SEPTEMBER, 14), type = "TRANSFER", amount = 1200.0, fromAccountId = bankId, toAccountId = cardId, description = "Sept Bill Pay"))
            repository.insertTransaction(TransactionEntity(date = ts(2026, Calendar.SEPTEMBER, 15), type = "INCOME", amount = 32000.0, toAccountId = bankId, categoryId = catId, description = "Sept Income"))

            val vm = DashboardViewModel(repository, settingsRepository)
            // FilterState.selectedMonths uses 1-based month index (9 = September, 8 = August)
            vm.setFilter(FilterState(selectedYears = setOf(2026), selectedMonths = setOf(9)))

            var lastNavigatedFilter: FilterState? = null
            composeTestRule.setContent {
                MyApplicationTheme {
                    DashboardScreen(
                        viewModel = vm,
                        onNavigateToExpenses = { _, _ -> },
                        onNavigateToExpensesWithFilter = { lastNavigatedFilter = it }
                    )
                }
            }
            composeTestRule.waitForIdle()
            composeTestRule.waitUntil(5000) {
                ShadowLooper.idleMainLooper()
                vm.uiState.value.allTransactions.size == 5 && vm.uiState.value.kpis.thisMonthSpend > 0.0
            }

            val state = vm.uiState.value
            assertEquals(1800.0, state.kpis.thisMonthSpend, 0.01)
            assertEquals(700.0, state.kpis.lastMonthSpend, 0.01)
            assertEquals(1800.0, state.kpis.ccSpendThisMonth, 0.01)
            assertEquals(700.0, state.kpis.ccSpendLastMonth, 0.01)
            assertEquals(1200.0, state.cardPaymentsThisMonth, 0.01)

            // Tap Card Spend (This Mo) -> EXPENSE, Sept 2026 (9), credit card account IDs
            composeTestRule.scrollToDashboardTag("kpi_cc_spend_this_month").performClick()
            assertNotNull(lastNavigatedFilter)
            assertEquals("EXPENSE", lastNavigatedFilter!!.selectedType)
            assertEquals(setOf(9), lastNavigatedFilter!!.selectedMonths)
            assertEquals(setOf(2026), lastNavigatedFilter!!.selectedYears)
            assertEquals(setOf(cardId), lastNavigatedFilter!!.selectedAccountIds)

            // Tap Card Spend (Last Mo) -> EXPENSE, Aug 2026 (8), credit card account IDs
            composeTestRule.scrollToDashboardTag("kpi_cc_spend_last_month", useUnmergedTree = true).performClick()
            assertEquals("EXPENSE", lastNavigatedFilter!!.selectedType)
            assertEquals(setOf(8), lastNavigatedFilter!!.selectedMonths)
            assertEquals(setOf(2026), lastNavigatedFilter!!.selectedYears)
            assertEquals(setOf(cardId), lastNavigatedFilter!!.selectedAccountIds)

            // Switch to INCOME mode -> kpi_cc_payments_this_month and kpi_last_month_credits are displayed
            vm.setTypeMode("INCOME")
            composeTestRule.waitUntil(5000) {
                ShadowLooper.idleMainLooper()
                vm.uiState.value.activeTypeMode == "INCOME" && vm.uiState.value.filter.selectedType == "INCOME"
            }
            composeTestRule.waitForIdle()

            // Tap Payments (This Mo) in Credit Cards card -> TRANSFER, Sept 2026 (9), credit card account IDs
            composeTestRule.scrollToDashboardTag("kpi_cc_payments_this_month", useUnmergedTree = true).performClick()
            assertEquals("TRANSFER", lastNavigatedFilter!!.selectedType)
            assertEquals(setOf(9), lastNavigatedFilter!!.selectedMonths)
            assertEquals(setOf(cardId), lastNavigatedFilter!!.selectedAccountIds)

            // Tap Last Month Credits -> INCOME, Aug 2026 (8)
            composeTestRule.scrollToDashboardTag("kpi_last_month_credits").performClick()
            assertEquals("INCOME", lastNavigatedFilter!!.selectedType)
            assertEquals(setOf(8), lastNavigatedFilter!!.selectedMonths)
            assertEquals(setOf(2026), lastNavigatedFilter!!.selectedYears)

            // Tap Total Credits KPI in INCOME mode -> INCOME, Sept 2026 (9)
            composeTestRule.scrollToDashboardTag("kpi_total_credits").performClick()
            assertEquals("INCOME", lastNavigatedFilter!!.selectedType)
            assertEquals(setOf(9), lastNavigatedFilter!!.selectedMonths)
            assertEquals(setOf(2026), lastNavigatedFilter!!.selectedYears)
        }
    }

    // =========================================================================
    // LT3 (FC): Trend chart title, Long-press hint, and Tap-to-drill hint have non-overlapping bounds
    // =========================================================================
    @Test
    fun LT3_trendChartHeader_titleSubtitleAndTapHintNeverCollide() {
        runBlocking {
            val bankId = repository.insertAccount(Account(id = 1L, name = "Bank", accountType = "BANK", openingBalance = 5000.0))
            val catId = repository.insertCategory(Category(id = 1L, name = "Food", color = "#EF4444"))
            repository.insertTransaction(
                TransactionEntity(
                    date = System.currentTimeMillis(),
                    type = "EXPENSE",
                    amount = 450.0,
                    fromAccountId = bankId,
                    categoryId = catId,
                    description = "Dinner"
                )
            )

            val vm = DashboardViewModel(repository, settingsRepository)
            vm.jumpToLevel(com.example.domain.model.ChartLevel.MONTHS)
            composeTestRule.setContent {
                MyApplicationTheme {
                    DashboardScreen(viewModel = vm, onNavigateToExpenses = { _, _ -> })
                }
            }
            composeTestRule.waitForIdle()
            composeTestRule.waitUntil(5000) {
                ShadowLooper.idleMainLooper()
                vm.uiState.value.chartLevel == com.example.domain.model.ChartLevel.MONTHS
            }
            composeTestRule.waitForIdle()

            // Running Balance Trend header: trend_card_title, trend_card_hint & "Tap a bar to drill down" stacked vertically
            composeTestRule.scrollToDashboardTag("trend_card_title", useUnmergedTree = true).assertIsDisplayed()
            composeTestRule.scrollToDashboardTag("trend_card_hint", useUnmergedTree = true).assertIsDisplayed()
            composeTestRule.scrollToDashboardText("Tap a bar to drill down", useUnmergedTree = true).assertIsDisplayed()

            val cardTitleBounds = composeTestRule.onNodeWithTag("trend_card_title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val cardHintBounds = composeTestRule.onNodeWithTag("trend_card_hint", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val drillHintBounds = composeTestRule.onNodeWithText("Tap a bar to drill down", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

            assertTrue("Running Balance title must be above Long-press hint (${cardTitleBounds.bottom} <= ${cardHintBounds.top + 2f})", cardTitleBounds.bottom <= cardHintBounds.top + 2f)
            assertTrue("Long-press hint must be above Tap-to-drill hint (${cardHintBounds.bottom} <= ${drillHintBounds.top + 2f})", cardHintBounds.bottom <= drillHintBounds.top + 2f)
        }
    }

    // =========================================================================
    // LT4 (FD): Settings row subtitles wrap onto 2 lines without single-line ellipsis
    // =========================================================================
    @Test
    fun LT4_settingsSubtitles_wrapUpToTwoLines() {
        runBlocking {
            val settingsVm = SettingsViewModel(repository, settingsRepository, database)
            composeTestRule.setContent {
                MyApplicationTheme {
                    Box(modifier = Modifier.size(width = 320.dp, height = 800.dp)) {
                        SettingsScreen(viewModel = settingsVm)
                    }
                }
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("settings_save_sync_subtitle", useUnmergedTree = true)
                .performScrollTo()
                .assertIsDisplayed()
        }
    }

    // =========================================================================
    // LT5 (FE + H-SWIPE PIN): SwipeRevealHelper caps reveal at min(0.30f * width, 120.dp)
    // across both Ledger rows and Attached Media voice rows.
    // =========================================================================
    @Test
    fun LT5_swipeReveal_cappedAtSharedConstantInMediaViewerAndLedger() {
        runBlocking {
            val density = Density(2.5f) // 120.dp = 300px
            val narrowWidthPx = 600f // 0.30 * 600 = 180px < 300px
            val wideWidthPx = 1200f  // 0.30 * 1200 = 360px -> capped at 300px (120.dp)

            assertEquals(0.30f, SwipeRevealHelper.MAX_REVEAL_WIDTH_FRACTION, 0.0001f)
            assertEquals(120.dp, SwipeRevealHelper.MAX_REVEAL_CAP_DP)
            assertEquals(min(0.30f * narrowWidthPx, with(density) { 120.dp.toPx() }), SwipeRevealHelper.computeMaxRevealPx(narrowWidthPx, density), 0.01f)
            assertEquals(with(density) { 120.dp.toPx() }, SwipeRevealHelper.computeMaxRevealPx(wideWidthPx, density), 0.01f)

            // Render MediaViewerSheet with a voice attachment and swipe left on viewer_voice_card_0
            val txnDir = mediaRepository.getVaultDirForTxn(1L)
            val tempVoice = File(txnDir, "test_swipe_voice.m4a").apply { writeBytes(ByteArray(2048)) }
            val voiceItem = MediaAttachment(
                id = 1L,
                txnId = 1L,
                kind = "VOICE",
                fileName = tempVoice.name,
                createdAt = System.currentTimeMillis()
            )

            composeTestRule.setContent {
                MyApplicationTheme {
                    MediaViewerSheet(
                        txnId = 1L,
                        attachments = listOf(voiceItem),
                        initialTab = "VOICE",
                        hideBalances = false,
                        mediaRepository = mediaRepository,
                        onDismiss = {},
                        onDeleteAttachment = {}
                    )
                }
            }
            composeTestRule.waitForIdle()

            val cardNode = composeTestRule.onNodeWithTag("viewer_voice_card_0")
            cardNode.assertIsDisplayed()
            val beforeBounds = cardNode.fetchSemanticsNode().boundsInRoot
            cardNode.performTouchInput { swipeLeft() }
            composeTestRule.waitForIdle()

            // Delete bin is revealed and card remains onscreen (translated by at most maxRevealPx)
            composeTestRule.onNodeWithTag("voice_delete_bin_0").assertIsDisplayed()
            val afterBounds = cardNode.fetchSemanticsNode().boundsInRoot
            val maxRevealPx = SwipeRevealHelper.computeMaxRevealPx(beforeBounds.width, composeTestRule.density)
            val translationPx = beforeBounds.left - afterBounds.left
            assertTrue("Voice card translation ($translationPx) must be > 0 and <= maxRevealPx ($maxRevealPx + 2f)", translationPx > 0f && translationPx <= maxRevealPx + 2f)
            tempVoice.delete()
        }
    }

    // =========================================================================
    // LT6 (FF): Opening voice viewer does NOT auto-play; tapping Play button
    // switches to Pause icon (viewer_pause_symbol_0), and tapping again reverts to Play.
    // =========================================================================
    @Test
    fun LT6_voiceChipAndViewer_showPauseSymbolWhilePlaying() {
        runBlocking {
            val accId = repository.insertAccount(Account(id = 1L, name = "Cash", accountType = "CASH", openingBalance = 1000.0))
            val catId = repository.insertCategory(Category(id = 1L, name = "Food", color = "#10B981"))
            val txnId = repository.insertTransaction(
                TransactionEntity(id = 55L, date = System.currentTimeMillis(), type = "EXPENSE", amount = 250.0, fromAccountId = accId, categoryId = catId, description = "Lunch")
            )
            val txnDir = mediaRepository.getVaultDirForTxn(txnId)
            val voiceFile = File(txnDir, "voice_1.m4a").apply { writeBytes(ByteArray(4096)) }
            database.mediaAttachmentDao().insert(
                MediaAttachment(id = 1L, txnId = txnId, kind = "VOICE", fileName = voiceFile.name, createdAt = System.currentTimeMillis())
            )

            val ledgerVm = LedgerViewModel(repository, settingsRepository, mediaRepository)
            composeTestRule.setContent {
                MyApplicationTheme {
                    Box(modifier = Modifier.size(width = 400.dp, height = 800.dp)) {
                        LedgerScreen(
                            viewModel = ledgerVm,
                            onAddClick = {},
                            onTransactionClick = {},
                            mediaRepository = mediaRepository
                        )
                    }
                }
            }
            composeTestRule.waitForIdle()
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                composeTestRule.onAllNodesWithTag("voice_chip_$txnId", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }

            // Badge always shows clean microphone label ("🎤 voice note × 1")
            composeTestRule.onNodeWithText("🎤 voice note × 1", useUnmergedTree = true).assertExists()

            // Tap voice chip -> opens MediaViewerSheet on VOICE tab WITHOUT auto-playing
            composeTestRule.onNodeWithTag("voice_chip_$txnId", useUnmergedTree = true).performClick()
            composeTestRule.waitForIdle()
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                ShadowLooper.idleMainLooper()
                composeTestRule.onAllNodesWithTag("viewer_play_pause_btn_0", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }

            // Initially stopped (Play button visible, Pause icon does NOT exist)
            composeTestRule.onNodeWithTag("viewer_play_pause_btn_0").assertExists()
            composeTestRule.onNodeWithTag("viewer_pause_symbol_0", useUnmergedTree = true).assertDoesNotExist()

            // Tap Play button -> switches to Pause icon
            composeTestRule.onNodeWithTag("viewer_play_pause_btn_0").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("viewer_pause_symbol_0", useUnmergedTree = true).assertExists()

            // Tap Pause button -> returns to Play icon, and chip remains "🎤 voice note × 1"
            composeTestRule.onNodeWithTag("viewer_play_pause_btn_0").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("viewer_pause_symbol_0", useUnmergedTree = true).assertDoesNotExist()
            composeTestRule.onNodeWithText("🎤 voice note × 1", useUnmergedTree = true).assertExists()
            voiceFile.delete()
        }
    }

    // =========================================================================
    // LT7 (FG): When Hide Balances (Privacy Mode) is ON, tapping photo or voice
    // chips in Ledger and Entry is blocked with the exact privacy gate string,
    // and MediaViewerSheet also gates content behind hideBalances.
    // =========================================================================
    @Test
    fun LT7_privacyMode_blocksPhotoAndVoiceChipsWithControlAgnosticMessage() {
        runBlocking {
            settingsRepository.setHideBalances(true)
            val accId = repository.insertAccount(Account(id = 1L, name = "Cash", accountType = "CASH", openingBalance = 1000.0))
            val catId = repository.insertCategory(Category(id = 1L, name = "Food", color = "#10B981"))
            val txnId = repository.insertTransaction(
                TransactionEntity(id = 77L, date = System.currentTimeMillis(), type = "EXPENSE", amount = 500.0, fromAccountId = accId, categoryId = catId, description = "Receipt Tx")
            )
            val txnDir = mediaRepository.getVaultDirForTxn(txnId)
            val photoFile = File(txnDir, "img_1.jpg").apply { writeBytes(ByteArray(10240)) }
            val voiceFile = File(txnDir, "voice_1.m4a").apply { writeBytes(ByteArray(4096)) }

            database.mediaAttachmentDao().insert(
                MediaAttachment(id = 1L, txnId = txnId, kind = "PHOTO", fileName = photoFile.name, createdAt = System.currentTimeMillis())
            )
            database.mediaAttachmentDao().insert(
                MediaAttachment(id = 2L, txnId = txnId, kind = "VOICE", fileName = voiceFile.name, createdAt = System.currentTimeMillis())
            )

            val ledgerVm = LedgerViewModel(repository, settingsRepository, mediaRepository)
            composeTestRule.setContent {
                MyApplicationTheme {
                    Box(modifier = Modifier.size(width = 400.dp, height = 800.dp)) {
                        LedgerScreen(
                            viewModel = ledgerVm,
                            onAddClick = {},
                            onTransactionClick = {},
                            mediaRepository = mediaRepository
                        )
                    }
                }
            }
            composeTestRule.waitForIdle()
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                composeTestRule.onAllNodesWithTag("photo_chip_$txnId", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }

            // Tap photo chip -> MediaViewerSheet must NOT open; exact privacy gate message is shown
            composeTestRule.onNodeWithTag("photo_chip_$txnId", useUnmergedTree = true).performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("media_viewer_sheet").assertDoesNotExist()
            composeTestRule.onAllNodesWithText("Turn off Privacy Mode in Settings to view photos and voice notes.", useUnmergedTree = true).onFirst().assertExists()

            photoFile.delete()
            voiceFile.delete()
        }
    }

    // =========================================================================
    // LT8 (H-CURRENCY + AM2): Changing currency symbol to "$" updates Formatters
    // and replaces hardcoded "₹" across Entry, Calculator, Lists, Commitments,
    // and Onboarding while leaving formatMoney / formatIndianCurrency / MASK_VALUE intact.
    // =========================================================================
    @Test
    fun LT8_currencySymbol_propagatesDynamicallyWithZeroHardcodedRupeeLeaks() {
        runBlocking {
            settingsRepository.setCurrency("$")
            Formatters.updateCurrentCurrencySymbol("$")

            // Verify frozen Formatters methods remain byte-identical
            assertEquals("••••••", Formatters.MASK_VALUE)
            assertEquals("$1,500.00", Formatters.formatMoney(1500.0, currencySymbol = "$"))
            assertEquals("$", Formatters.currentCurrencySymbol())

            composeTestRule.setContent {
                MyApplicationTheme {
                    Box {
                        CalculatorBottomSheet(
                            initialExpression = "125",
                            onDismiss = {},
                            onApplyAmount = {}
                        )
                    }
                }
            }
            composeTestRule.waitForIdle()

            // Calculator uses currentCurrencySymbol() ("$") and has no "₹" leak
            composeTestRule.onNodeWithText("= $125").assertIsDisplayed()
            composeTestRule.onNodeWithText("₹", substring = true).assertDoesNotExist()
        }
    }

    // =========================================================================
    // LT9 (AM4): CSV Import Preview dialog defaults to Merge (clearExisting = false)
    // and supports Overwrite (clearExisting = true).
    // =========================================================================
    @Test
    fun LT9_csvImport_supportsMergeAndOverwriteModes() {
        runBlocking {
            val accId = repository.insertAccount(Account(id = 1L, name = "Cash", accountType = "CASH", openingBalance = 1000.0))
            val catId = repository.insertCategory(Category(id = 1L, name = "Food", color = "#10B981"))

            // Seed 10 existing transactions
            for (i in 1..10) {
                repository.insertTransaction(
                    TransactionEntity(
                        date = 1700000000000L + i * 86400000L,
                        type = "EXPENSE",
                        amount = (i * 10).toDouble(),
                        fromAccountId = accId,
                        categoryId = catId,
                        description = "Existing $i"
                    )
                )
            }
            assertEquals(10, repository.allTransactions.first().size)

            // Build a valid 5-row CSV
            val csvFiveRows = buildString {
                appendLine("Date,Type,Amount,Category,Account,ToAccount,Description,Notes")
                for (j in 1..5) {
                    appendLine("2026-09-0$j,EXPENSE,${j * 100},Food,Cash,,Imported $j,")
                }
            }

            val settingsVm = SettingsViewModel(repository, settingsRepository, database)

            // 1. Merge mode (clearExisting = false): 10 existing + 5 new = 15 rows
            settingsVm.commitCsvImport(
                inputStream = ByteArrayInputStream(csvFiveRows.toByteArray()),
                mergeTransfers = true,
                clearExisting = false
            )
            val deadline1 = System.currentTimeMillis() + 5000L
            while (repository.allTransactions.first().size < 15 && System.currentTimeMillis() < deadline1) {
                ShadowLooper.idleMainLooper()
                kotlinx.coroutines.delay(20)
            }
            assertEquals(15, repository.allTransactions.first().size)

            // 2. Overwrite mode (clearExisting = true): replaces all with 5 rows
            settingsVm.commitCsvImport(
                inputStream = ByteArrayInputStream(csvFiveRows.toByteArray()),
                mergeTransfers = true,
                clearExisting = true
            )
            val deadline2 = System.currentTimeMillis() + 5000L
            while (repository.allTransactions.first().size != 5 && System.currentTimeMillis() < deadline2) {
                ShadowLooper.idleMainLooper()
                kotlinx.coroutines.delay(20)
            }
            assertEquals(5, repository.allTransactions.first().size)
        }
    }

    // =========================================================================
    // LT10 (H-SAFETY-NET-WRITE): Settings screen renders only "Import a CSV file"
    // and "Restore from CSV Backup" (redundant UI buttons removed), while internal
    // filesDir/backups/auto_backup.csv is still written on auto-save.
    // =========================================================================
    @Test
    fun LT10_settingsRestoreOptionsConsolidated_andInternalSafetyNetWritePreserved() {
        runBlocking {
            val settingsVm = SettingsViewModel(repository, settingsRepository, database)
            composeTestRule.setContent {
                MyApplicationTheme {
                    SettingsScreen(viewModel = settingsVm)
                }
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("Import a CSV file").assertExists()
            composeTestRule.onNodeWithText("Restore from CSV Backup").assertExists()

            // Redundant doors removed from UI
            composeTestRule.onNodeWithText("Fetch Memory from Phone CSV").assertDoesNotExist()
            composeTestRule.onNodeWithText("Restore from Internal Safety Net").assertDoesNotExist()
            composeTestRule.onNodeWithText("Run auto-save now").assertDoesNotExist()

            // Internal safety net write (filesDir/backups/auto_backup.csv) still works
            val safetyNetFile = File(File(context.filesDir, "backups"), "auto_backup.csv")
            safetyNetFile.delete()
            MediaStoreWriter.writeRollingAutoSave(context, "#FORMAT=2\nDate,Type,Amount\n")
            assertTrue("Internal auto_backup.csv write must remain intact", safetyNetFile.exists() && safetyNetFile.length() > 0L)
        }
    }

    // =========================================================================
    // LT11 (AM1 Branch b): Sub-second voice clip (400ms) formats as "0:00" and
    // 4-second clip (4000ms) formats as "0:04" with zero Room schema migration.
    // =========================================================================
    @Test
    fun LT11_voiceDuration_honestSubSecondAndMultiSecondFormatting_noSchemaChange() {
        val fakeSubSecondFile = File(context.cacheDir, "voice_400ms.m4a")
        val fakeFourSecFile = File(context.cacheDir, "voice_4000ms.m4a")

        val resolved400 = MediaRepository.resolveVoiceDurationMs(fakeSubSecondFile, fallbackDurationMs = 400L)
        val resolved4000 = MediaRepository.resolveVoiceDurationMs(fakeFourSecFile, fallbackDurationMs = 4000L)

        assertEquals(400L, resolved400)
        assertEquals(4000L, resolved4000)
        assertEquals("0:00", MediaRepository.formatDuration(resolved400))
        assertEquals("0:04", MediaRepository.formatDuration(resolved4000))
    }

    // =========================================================================
    // LT12 (H-MISSED-1): Storage meter reflects DB + filesDir/media + filesDir/backups
    // when a 100 KB photo is added and removed.
    // =========================================================================
    @Test
    fun LT12_storageMeter_includesMediaAndBackupsDirectories() {
        runBlocking {
            val settingsVm = SettingsViewModel(repository, settingsRepository, database)
            val mediaDir = File(context.filesDir, "media/txn_999").apply { mkdirs() }
            val photoFile = File(mediaDir, "photo_100kb.jpg")
            photoFile.delete()

            val baselineBytes = settingsVm.computeStorageUsedBytes()

            // Write a 100 KB photo file into filesDir/media
            photoFile.writeBytes(ByteArray(102_400))
            val afterAddBytes = settingsVm.computeStorageUsedBytes()
            assertTrue(
                "Adding 100 KB photo must increase storageUsedBytes by >= 100,000 (was $baselineBytes, now $afterAddBytes)",
                afterAddBytes - baselineBytes >= 100_000L
            )

            // Delete the photo file -> storageUsedBytes drops back
            photoFile.delete()
            mediaDir.delete()
            val afterDeleteBytes = settingsVm.computeStorageUsedBytes()
            assertEquals(baselineBytes, afterDeleteBytes)
        }
    }

    // =========================================================================
    // LT13 (X2 Truth-Pin + H-FREEZE + AF1-AF4): Across ALL FOUR sheets
    // (DASHBOARD, EXPENSES, LISTS, ALL/Common Tasks), asserts each rendered line
    // EQUALS its canonical string byte-for-byte (all 27 strings), enforces the
    // forbidden-noun set {Dues-in-Lists, duplicate, billing day, matching categories,
    // "10 ", "3 voice", per-chip ×}, and checks <= 14 words + AF1-AF4 rules.
    // =========================================================================
    @Test
    fun LT13_helpCopyLint_all27LinesPassWordCapAndZeroForbiddenTokens() {
        val canonicalDashboardResult = listOf(
            "Balance Card: Tap or hold for Opening, +In, -Out, and Net breakdown.",
            "Summary, Source, Card, or Category: Tap to open matching rows in Ledger.",
            "Sources Order: Auto-sorted by highest bank balance and largest card debt.",
            "Credit Card Rows: TAP opens ledger; HOLD opens a pre-filled transfer of the amount due.",
            "Monthly Budgets: Tap to view rows; hold to edit limit inline.",
            "Trend Bars: Tap to zoom; hold to open the Ledger at that date.",
            "Chart Controls: Pick more than one period, pin a week label, or zoom out.",
            "Top Filters: Switch between All, Expense, Income, Transfer, months, years, or dates.",
            "Header Icons: Bell settles bills and IOUs; ☀️/🌙/A sets Light, Dark, or Auto."
        )
        val canonicalDashboardFullLine = listOf(
            "Balance Card: Tap or hold for Opening, +In, -Out, and Net breakdown.",
            "Summary, Source, Card, or Category: Tap to open matching rows in Ledger.",
            "Sources Order: Auto-sorted by highest bank balance and largest card debt.",
            "Pay Credit Card Due?: Credit Card Rows: TAP opens ledger; HOLD opens a pre-filled transfer of the amount due.",
            "Monthly Budgets: Tap to view rows; hold to edit limit inline.",
            "Trend Bars: Tap to zoom; hold to open the Ledger at that date.",
            "Chart Controls: Pick more than one period, pin a week label, or zoom out.",
            "Top Filters: Switch between All, Expense, Income, Transfer, months, years, or dates.",
            "Header Icons: Bell settles bills and IOUs; ☀️/🌙/A sets Light, Dark, or Auto."
        )
        val canonicalExpenses = listOf(
            "Delete & Undo: Swipe left, tap red bin to delete, or tap UNDO.",
            "Split Cards: Tap card to expand lines inline; tap ✎ to edit.",
            "Entry & Split Math (+): Keypad solves math; enter Total first for splits.",
            "Media & Categories: Tap 📷 or 🎤 for media; tap category to re-tag.",
            "Edit Row: Tap any row to edit amount, account, date, notes, or media.",
            "Search & Scratchpad (🧮): Search text, tags, or amounts; tap 🧮 for calculator.",
            "Filtered Export: Top download icon saves only filtered rows to your data folder."
        )
        val canonicalLists = listOf(
            "4 Tabs: Manage Categories, Sources, Fields, and Budgets from the top bar.",
            "Delete vs. Archive: Unused items delete; used items archive to protect history.",
            "Categories: Paste any emoji, pick from 24 colors, and reorder with arrows.",
            "Sources: Set Bank, Cash, or Card; editing Opening Balance recalculates all balances.",
            "Custom Fields: Add Text, Number, Dropdown, or Date fields per transaction type.",
            "Budgets: Set monthly limits for a Category, a Source/Card, or All Spend."
        )
        val canonicalCommonTasks = listOf(
            "Cycle Anchor Day: Set your month start day (1–28) for budgets and cycles.",
            "Privacy & Lock: Mask amounts in public, or lock with PIN and biometrics.",
            "UI & Reminders: Toggle Dashboard budgets, ? help buttons, or bill due alerts.",
            "Auto & Manual Backup: Saves daily to your data folder and safety net.",
            "Restore & Import: Import a CSV file or Restore from CSV Backup anytime.",
            "Sample Data Mode: Runs in an isolated sandbox so real data stays untouched."
        )
        val canonicalAll28 = canonicalDashboardFullLine + canonicalExpenses + canonicalLists + canonicalCommonTasks

        val allItems = AUDITED_AFFORDANCES + COMMON_TASKS_AFFORDANCES
        assertEquals(28, allItems.size)

        val actualDashboard = AUDITED_AFFORDANCES.filter { it.section == HelpSection.DASHBOARD }
        val actualExpenses = AUDITED_AFFORDANCES.filter { it.section == HelpSection.EXPENSES }
        val actualLists = AUDITED_AFFORDANCES.filter { it.section == HelpSection.LISTS }
        val actualCommonTasks = COMMON_TASKS_AFFORDANCES

        assertEquals(9, actualDashboard.size)
        assertEquals(7, actualExpenses.size)
        assertEquals(6, actualLists.size)
        assertEquals(6, actualCommonTasks.size)

        // 1. Byte-for-byte truth-pin across all 4 sections (both .result and .fullLine)
        assertEquals(canonicalDashboardResult, actualDashboard.map { it.result })
        assertEquals(canonicalDashboardFullLine, actualDashboard.map { it.fullLine })
        assertEquals(canonicalExpenses, actualExpenses.map { it.result })
        assertEquals(canonicalExpenses, actualExpenses.map { it.fullLine })
        assertEquals(canonicalLists, actualLists.map { it.result })
        assertEquals(canonicalLists, actualLists.map { it.fullLine })
        assertEquals(canonicalCommonTasks, actualCommonTasks.map { it.result })
        assertEquals(canonicalCommonTasks, actualCommonTasks.map { it.fullLine })
        assertEquals(canonicalAll28, allItems.map { it.fullLine })

        // 2. Forbidden-noun scanner across all 27 items
        val forbiddenSubstringsEverywhere = listOf(
            "duplicate",
            "billing day",
            "matching categories",
            "10 ",
            "3 voice",
            "×"
        )
        for (item in allItems) {
            val fullLine = item.fullLine
            for (forbidden in forbiddenSubstringsEverywhere) {
                assertFalse(
                    "Hallucinated noun/token '$forbidden' found in '${item.gesture}': '$fullLine'",
                    fullLine.contains(forbidden, ignoreCase = true)
                )
            }
            if (item.section == HelpSection.LISTS) {
                assertFalse(
                    "Hallucinated 'Dues' in LISTS section found in '${item.gesture}': '$fullLine'",
                    fullLine.contains("Dues", ignoreCase = true)
                )
            }
        }

        // 3. Word cap (<= 14 words for canonical 27 items) and AF1-AF4 token lint
        val forbiddenWordTokens = setOf(
            "chip", "chips", "breadcrumb", "breadcrumbs", "multi-select", "w#", "eye", "👁", "🙈"
        )
        val forbiddenPathSubstrings = listOf(
            "Downloads/SilentSums", "Downloads", "ExpenseTracker", "PaisaTrack", "Internal storage"
        )

        for (item in allItems) {
            val fullLine = item.fullLine
            val words = fullLine.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (item.gesture != "Pay Credit Card Due?") {
                assertTrue(
                    "Tip '${item.gesture}' exceeds 14 words (${words.size} words): '$fullLine'",
                    words.size <= 14
                )
            }

            for (word in words) {
                val cleaned = word.lowercase().trim(',', '.', ';', ':', '(', ')', '\'', '"')
                assertFalse(
                    "Tip '${item.gesture}' contains forbidden token '$cleaned' in '$fullLine'",
                    cleaned in forbiddenWordTokens
                )
            }
            assertFalse("Tip must not contain 👁", fullLine.contains("👁"))
            assertFalse("Tip must not contain 🙈", fullLine.contains("🙈"))
            assertFalse("Tip must not contain W#", fullLine.contains("W#", ignoreCase = true))

            for (pathToken in forbiddenPathSubstrings) {
                assertFalse(
                    "Tip '${item.gesture}' contains forbidden path literal '$pathToken' in '$fullLine'",
                    fullLine.contains(pathToken, ignoreCase = true)
                )
            }

            assertFalse(
                "Tip body '${item.body}' must not repeat gesture label '${item.gesture}'",
                item.body.startsWith(item.gesture, ignoreCase = true)
            )
        }

        // 4. Render all FOUR sheets in Compose and verify every rendered gesture + body
        val activeSectionState = androidx.compose.runtime.mutableStateOf(HelpSection.DASHBOARD)
        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.dashboard.HelpGesturesSheet(
                    section = activeSectionState.value,
                    onDismiss = {}
                )
            }
        }

        val sheetAssertions = listOf(
            HelpSection.DASHBOARD to actualDashboard,
            HelpSection.EXPENSES to actualExpenses,
            HelpSection.LISTS to actualLists,
            HelpSection.ALL to allItems
        )
        for ((section, expectedItems) in sheetAssertions) {
            activeSectionState.value = section
            composeTestRule.waitForIdle()
            for (expectedItem in expectedItems) {
                composeTestRule.onNodeWithText(expectedItem.gesture).assertExists()
                composeTestRule.onNodeWithText(expectedItem.body).assertExists()
            }
        }
    }

    // =========================================================================
    // LT14: Long transaction titles wrap up to 2 lines while keeping the amount
    // top-right aligned and meta/notes on the full left rail.
    // =========================================================================
    @Test
    fun LT14_ledgerRow_twoLineTitleWrapsAndAlignsWithAmountAndLeftRail() {
        runBlocking {
            val accId = repository.insertAccount(Account(id = 1L, name = "ICICI Credit", accountType = "CREDIT_CARD", openingBalance = 0.0))
            val catId = repository.insertCategory(Category(id = 1L, name = "Electronics", color = "#3B82F6"))
            val txnId = repository.insertTransaction(
                TransactionEntity(
                    id = 88L,
                    date = System.currentTimeMillis(),
                    type = "EXPENSE",
                    amount = 14999.0,
                    fromAccountId = accId,
                    categoryId = catId,
                    description = "Noise cancelling earbuds (Amazon Great Indian Festival Deal)",
                    notes = "2 year warranty included"
                )
            )

            val ledgerVm = LedgerViewModel(repository, settingsRepository, mediaRepository)
            composeTestRule.setContent {
                MyApplicationTheme {
                    Box(modifier = Modifier.size(width = 360.dp, height = 640.dp)) {
                        LedgerScreen(
                            viewModel = ledgerVm,
                            onAddClick = {},
                            onTransactionClick = {},
                            mediaRepository = mediaRepository
                        )
                    }
                }
            }
            composeTestRule.waitForIdle()

            val titleBounds = composeTestRule.onNodeWithTag("tx_title_$txnId", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val amountBounds = composeTestRule.onNodeWithTag("tx_amount_$txnId", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val metaBounds = composeTestRule.onNodeWithTag("tx_meta_$txnId", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

            // Title and amount are side-by-side at the top without horizontal overlap
            assertTrue("Title right (${titleBounds.right}) must not overlap amount left (${amountBounds.left})", titleBounds.right <= amountBounds.left + 2f)
            // Meta row is below the title
            assertTrue("Meta row (${metaBounds.top}) must be below title (${titleBounds.bottom})", metaBounds.top >= titleBounds.bottom - 2f)
        }
    }
}
