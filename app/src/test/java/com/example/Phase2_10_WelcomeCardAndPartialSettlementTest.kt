package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.datastore.AppSettingsRepository
import com.example.data.model.Account
import com.example.data.model.Commitment
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.importbackup.CsvBackupManager
import com.example.ui.commitment.CommitmentViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase2_10_WelcomeCardAndPartialSettlementTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: FinanceRepository
    private lateinit var appSettingsRepo: AppSettingsRepository
    private lateinit var commitmentViewModel: CommitmentViewModel

    private var accountId: Long = 0L

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Clear prefs before each test
        context.getSharedPreferences("paisa_track_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(database)
        appSettingsRepo = AppSettingsRepository(context)
        commitmentViewModel = CommitmentViewModel(repository, kotlinx.coroutines.Dispatchers.Unconfined)

        runBlocking {
            accountId = repository.insertAccount(
                Account(name = "Main Bank", accountType = "BANK", openingBalance = 10000.0)
            )
        }
    }

    @After
    fun tearDown() {
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        database.close()
    }

    @Test
    fun testPartialSettlement_iouIncoming_createsLedgerIncomeTxnAndUpdatesRemaining() {
        runBlocking {
            // Given: IOU from Sandesh ₹3,000 due 31 Oct
            val dueDateOct31 = 1730332800000L // 31 Oct
            val comId = repository.insertCommitment(
                Commitment(
                    type = "IOU",
                    name = "Sandesh",
                    amount = 3000.0,
                    direction = "INCOMING",
                    dueDate = dueDateOct31,
                    accountId = accountId,
                    description = "Trip loan"
                )
            )
            val initial = repository.allCommitments.first().first { it.id == comId }
            assertEquals(3000.0, initial.remaining, 0.001)
            assertEquals(0.0, initial.paidSoFar, 0.001)

            // When: Partial settlement of ₹2,000 on 15 Oct with description
            val dateOct15 = 1728950400000L
            commitmentViewModel.recordPartialPayment(
                commitment = initial,
                partialAmount = 2000.0,
                txDate = dateOct15,
                description = "Sandesh Part 1",
                newDueDate = null, // keep current date
                chosenAccountId = accountId
            ).join()

            // Then: One ledger transaction created
            val txList = repository.allTransactions.first()
            assertEquals(1, txList.size)
            val tx = txList[0]
            assertEquals("INCOME", tx.type)
            assertEquals(2000.0, tx.amount, 0.001)
            assertEquals(dateOct15, tx.date)
            assertEquals("Sandesh Part 1", tx.description)
            assertEquals(accountId, tx.toAccountId)

            // Then: Commitment paidSoFar updated and remaining decreases, stays active
            val updated = repository.allCommitments.first().first { it.id == comId }
            assertEquals(2000.0, updated.paidSoFar, 0.001)
            assertEquals(1000.0, updated.remaining, 0.001)
            assertEquals(dueDateOct31, updated.dueDate)
            assertFalse(updated.settled)
            assertTrue(updated.active)
        }
    }

    @Test
    fun testPartialSettlement_newDueDate_movesDueDateCorrectly() {
        runBlocking {
            val dueDateOct31 = 1730332800000L
            val comId = repository.insertCommitment(
                Commitment(
                    type = "IOU",
                    name = "Sandesh",
                    amount = 3000.0,
                    direction = "INCOMING",
                    dueDate = dueDateOct31,
                    accountId = accountId,
                    paidSoFar = 2000.0 // already 2,000 paid, 1,000 remaining
                )
            )
            val current = repository.allCommitments.first().first { it.id == comId }
            assertEquals(1000.0, current.remaining, 0.001)

            // When: Second partial of ₹500 with new due date Nov 15
            val newDueDateNov15 = 1731628800000L
            commitmentViewModel.recordPartialPayment(
                commitment = current,
                partialAmount = 500.0,
                txDate = System.currentTimeMillis(),
                description = "Second partial",
                newDueDate = newDueDateNov15,
                chosenAccountId = accountId
            ).join()

            val updated = repository.allCommitments.first().first { it.id == comId }
            assertEquals(2500.0, updated.paidSoFar, 0.001)
            assertEquals(500.0, updated.remaining, 0.001)
            assertEquals(newDueDateNov15, updated.dueDate)
            assertFalse(updated.settled)
        }
    }

    @Test
    fun testPartialSettlement_overpayRejected_nothingWritten() {
        runBlocking {
            val comId = repository.insertCommitment(
                Commitment(
                    type = "IOU",
                    name = "Sandesh",
                    amount = 3000.0,
                    direction = "INCOMING",
                    paidSoFar = 2000.0 // 1,000 remaining
                )
            )
            val current = repository.allCommitments.first().first { it.id == comId }

            // When: Attempting to pay ₹5,000 when only ₹1,000 is remaining
            commitmentViewModel.recordPartialPayment(
                commitment = current,
                partialAmount = 5000.0,
                txDate = System.currentTimeMillis(),
                chosenAccountId = accountId
            ).join()

            // Then: Nothing written to ledger and commitment unchanged
            val txList = repository.allTransactions.first()
            assertEquals(0, txList.size)
            val unchanged = repository.allCommitments.first().first { it.id == comId }
            assertEquals(2000.0, unchanged.paidSoFar, 0.001)
            assertEquals(1000.0, unchanged.remaining, 0.001)
        }
    }

    @Test
    fun testPartialThenFullSettle_iou_closesAndMarksSettled() {
        runBlocking {
            val comId = repository.insertCommitment(
                Commitment(
                    type = "IOU",
                    name = "Sandesh",
                    amount = 3000.0,
                    direction = "INCOMING",
                    paidSoFar = 2000.0,
                    accountId = accountId
                )
            )
            val current = repository.allCommitments.first().first { it.id == comId }
            assertEquals(1000.0, current.remaining, 0.001)

            // When: Full settle remaining ₹1,000 via settleIou
            commitmentViewModel.settleIou(current, recordTransaction = true, chosenAccountId = accountId)

            // Then: Ledger has 1 transaction for remaining ₹1,000
            val txList = repository.allTransactions.first()
            assertEquals(1, txList.size)
            assertEquals(1000.0, txList[0].amount, 0.001)
            assertEquals("INCOME", txList[0].type)

            // Then: Commitment is marked settled and remaining is 0
            val settled = repository.allCommitments.first().first { it.id == comId }
            assertTrue(settled.settled)
            assertEquals(3000.0, settled.paidSoFar, 0.001)
            assertEquals(0.0, settled.remaining, 0.001)
        }
    }

    @Test
    fun testRecurringPartial_neverAdvancesCycle_onlyFullZeroRemainingSettleAdvances() {
        runBlocking {
            val comId = repository.insertCommitment(
                Commitment(
                    type = "RECURRING",
                    name = "Broadband",
                    amount = 1500.0,
                    direction = "OUTGOING",
                    dayOfMonth = 10,
                    accountId = accountId,
                    lastPostedCycle = null
                )
            )
            val initial = repository.allCommitments.first().first { it.id == comId }

            // 1. Partial payment of ₹500
            commitmentViewModel.recordPartialPayment(
                commitment = initial,
                partialAmount = 500.0,
                txDate = System.currentTimeMillis(),
                chosenAccountId = accountId
            ).join()

            // Assert: cycle is NOT advanced on partial payment
            val afterPartial = repository.allCommitments.first().first { it.id == comId }
            assertEquals(500.0, afterPartial.paidSoFar, 0.001)
            assertEquals(1000.0, afterPartial.remaining, 0.001)
            assertNull(afterPartial.lastPostedCycle) // NEVER advanced!

            // 2. Full settle remaining ₹1,000 via recordAndMarkPaid
            commitmentViewModel.recordAndMarkPaid(afterPartial).join()

            // Assert: ledger recorded ₹1,000 expense
            val txList = repository.allTransactions.first()
            assertEquals(2, txList.size)
            assertTrue("Expected 500.0 transaction", txList.any { Math.abs(it.amount - 500.0) < 0.001 })
            assertTrue("Expected 1000.0 transaction", txList.any { Math.abs(it.amount - 1000.0) < 0.001 })

            // Assert: cycle IS rolled and paidSoFar is reset to 0 for next cycle
            val afterFull = repository.allCommitments.first().first { it.id == comId }
            assertNotNull(afterFull.lastPostedCycle)
            assertEquals(0.0, afterFull.paidSoFar, 0.001)
            assertEquals(1500.0, afterFull.remaining, 0.001)
        }
    }

    @Test
    fun testCsvBackupManager_11ColumnsRoundTrip_andLegacy10ColumnTolerance() {
        // Given commitments with paidSoFar
        val c1 = Commitment(
            id = 1,
            type = "IOU",
            name = "Kiran",
            amount = 5000.0,
            direction = "OUTGOING",
            dueDate = 1730000000000L,
            active = true,
            description = "Trip loan",
            paidSoFar = 1500.0
        )
        val c2 = Commitment(
            id = 2,
            type = "RECURRING",
            name = "Gym",
            amount = 1200.0,
            direction = "OUTGOING",
            dayOfMonth = 5,
            active = true,
            description = "Monthly gym",
            paidSoFar = 0.0
        )

        // When exporting
        val csv = CsvBackupManager.buildCsv(
            transactions = emptyList(),
            accounts = emptyList(),
            categories = emptyList(),
            customFields = emptyList(),
            commitments = listOf(c1, c2)
        )

        // Then: CSV contains 11-column header
        assertTrue(csv.contains("type,name,amount,direction,dayOfMonth,dueDate,accountName,categoryName,active,description,paidSoFar"))
        assertTrue(csv.contains("IOU,Kiran,5000.00,OUTGOING,,1730000000000,,,true,Trip loan,1500.00"))
        assertTrue(csv.contains("RECURRING,Gym,1200.00,OUTGOING,5,,,,true,Monthly gym,0.00"))

        // When restoring 11-column CSV
        val parsed11 = CsvBackupManager.parseCsvFull(
            ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8))
        ).dues
        assertEquals(2, parsed11.size)
        val p1 = parsed11.first { it.name == "Kiran" }
        assertEquals(1500.0, p1.paidSoFar, 0.001)
        val p2 = parsed11.first { it.name == "Gym" }
        assertEquals(0.0, p2.paidSoFar, 0.001)

        // When restoring legacy 10-column CSV (missing 11th paidSoFar column)
        val legacy10ColCsv = """
            #COMMITMENTS (Dues & Recurring)
            type,name,amount,direction,dayOfMonth,dueDate,accountName,categoryName,active,description
            IOU,Ramesh,2500.00,INCOMING,,1730000000000,,,true,Old borrow
            RECURRING,Netflix,499.00,OUTGOING,15,,,,true,Subscription
        """.trimIndent()

        val parsed10 = CsvBackupManager.parseCsvFull(
            ByteArrayInputStream(legacy10ColCsv.toByteArray(StandardCharsets.UTF_8))
        ).dues
        assertEquals(2, parsed10.size)
        val ramesh = parsed10.first { it.name == "Ramesh" }
        assertEquals(0.0, ramesh.paidSoFar, 0.001)
        assertEquals("Old borrow", ramesh.description)
        val netflix = parsed10.first { it.name == "Netflix" }
        assertEquals(0.0, netflix.paidSoFar, 0.001)
    }

    @Test
    fun testWelcomeCardRules_demoEnterAndCancel_returnsWelcomeCard() {
        val prefs = context.getSharedPreferences("paisa_track_prefs", Context.MODE_PRIVATE)

        // Condition helper simulating DashboardScreen visibility logic
        fun isWelcomeCardVisible(
            allTransactionsCount: Int,
            demoMode: Boolean
        ): Boolean {
            val welcomeCardDismissed = prefs.getBoolean("welcome_card_dismissed", false)
            val realTxEverLogged = prefs.getBoolean("real_transaction_ever_logged", false)
            return !welcomeCardDismissed && !realTxEverLogged && !demoMode && allTransactionsCount == 0
        }

        // 1. Fresh install: 0 transactions, not dismissed, demoMode = false
        assertTrue(isWelcomeCardVisible(allTransactionsCount = 0, demoMode = false))

        // 2. Load demo (Atomic launch: setDemoMode(true) first, then insert sample rows)
        val demoMode = true
        val demoTransactionsCount = 10
        // Because demoMode is true, real_transaction_ever_logged is NOT set
        assertFalse(prefs.getBoolean("real_transaction_ever_logged", false))
        // Card is hidden while in demo
        assertFalse(isWelcomeCardVisible(allTransactionsCount = demoTransactionsCount, demoMode = demoMode))

        // 3. Cancel demo: clear demo rows (count = 0), set demoMode = false
        // Card must return!
        assertTrue(isWelcomeCardVisible(allTransactionsCount = 0, demoMode = false))
    }

    @Test
    fun testWelcomeCardRules_realTransaction_burnsCardPermanently_evenIfDeleted() {
        val prefs = context.getSharedPreferences("paisa_track_prefs", Context.MODE_PRIVATE)

        fun isWelcomeCardVisible(
            allTransactionsCount: Int,
            demoMode: Boolean
        ): Boolean {
            val welcomeCardDismissed = prefs.getBoolean("welcome_card_dismissed", false)
            val realTxEverLogged = prefs.getBoolean("real_transaction_ever_logged", false)
            return !welcomeCardDismissed && !realTxEverLogged && !demoMode && allTransactionsCount == 0
        }

        // 1. Fresh install -> visible
        assertTrue(isWelcomeCardVisible(allTransactionsCount = 0, demoMode = false))

        // 2. Log first real transaction (demoMode = false)
        // Dashboard LaunchedEffect sets real_transaction_ever_logged = true
        prefs.edit().putBoolean("real_transaction_ever_logged", true).commit()

        // Card is hidden
        assertFalse(isWelcomeCardVisible(allTransactionsCount = 1, demoMode = false))

        // 3. User deletes that transaction -> count returns to 0
        // Card is still hidden forever!
        assertFalse(isWelcomeCardVisible(allTransactionsCount = 0, demoMode = false))
    }

    @Test
    fun testWelcomeCardRules_xDismiss_isPermanent() {
        val prefs = context.getSharedPreferences("paisa_track_prefs", Context.MODE_PRIVATE)

        fun isWelcomeCardVisible(
            allTransactionsCount: Int,
            demoMode: Boolean
        ): Boolean {
            val welcomeCardDismissed = prefs.getBoolean("welcome_card_dismissed", false)
            val realTxEverLogged = prefs.getBoolean("real_transaction_ever_logged", false)
            return !welcomeCardDismissed && !realTxEverLogged && !demoMode && allTransactionsCount == 0
        }

        // User clicks X dismiss
        prefs.edit().putBoolean("welcome_card_dismissed", true).commit()

        // Card is hidden forever even at 0 transactions
        assertFalse(isWelcomeCardVisible(allTransactionsCount = 0, demoMode = false))
    }
}
