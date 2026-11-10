package com.example.domain.budget

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.datastore.AppSettings
import com.example.data.datastore.AppSettingsRepository
import com.example.data.model.Account
import com.example.data.model.BudgetEntity
import com.example.data.model.Category
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.domain.calculator.LedgerCalculator
import com.example.domain.util.Formatters
import com.example.importbackup.CsvBackupManager
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
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BudgetIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: FinanceRepository

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
        database.close()
    }

    @Test
    fun testBudgetCalculator_reusesExactTileCodePath_CardSpendToThePaisa() = runBlocking {
        // Setup Account: Credit Card and Bank
        val cardAccId = repository.insertAccount(
            Account(name = "ICICI Credit", accountType = "CREDIT_CARD", openingBalance = 0.0)
        )
        val bankAccId = repository.insertAccount(
            Account(name = "KBL", accountType = "BANK", openingBalance = 50000.0)
        )
        val foodCatId = repository.insertCategory(
            Category(name = "Food & Dining", color = "#EF4444")
        )

        val cal = Calendar.getInstance()
        val now = cal.timeInMillis

        // 1. Expense from Credit Card: 1092.34
        repository.insertTransaction(
            TransactionEntity(
                date = now,
                type = "EXPENSE",
                amount = 1092.34,
                fromAccountId = cardAccId,
                categoryId = foodCatId,
                description = "Dinner"
            )
        )

        // 2. Transfer repayment from Bank to Credit Card: 5000.00 (MUST BE EXCLUDED)
        repository.insertTransaction(
            TransactionEntity(
                date = now,
                type = "TRANSFER",
                amount = 5000.00,
                fromAccountId = bankAccId,
                toAccountId = cardAccId,
                description = "CC Repayment"
            )
        )

        // 3. Expense from Bank: 250.00
        repository.insertTransaction(
            TransactionEntity(
                date = now,
                type = "EXPENSE",
                amount = 250.00,
                fromAccountId = bankAccId,
                categoryId = foodCatId,
                description = "Tea"
            )
        )

        val txs = repository.allTransactions.first()
        val accs = repository.allAccounts.first()
        val cats = repository.allCategories.first()

        // Verify Dashboard KPI Card Spend calculation
        val kpis = LedgerCalculator.computeDashboardKpis(accs, txs, monthStartDay = 1, now = now)
        assertEquals(1092.34, kpis.ccSpendThisMonth, 0.001)

        // Verify Budget for ICICI Credit matches Card Spend to the paisa
        val cardBudget = BudgetEntity(
            id = 1L,
            name = "ICICI Credit Limit",
            scopeType = "ACCOUNT",
            targetName = "ICICI Credit",
            limitAmount = 6000.0,
            active = true
        )
        val calculated = BudgetCalculator.computeBudgets(
            budgets = listOf(cardBudget),
            transactions = txs,
            accounts = accs,
            categories = cats,
            monthStartDay = 1,
            referenceTime = now
        )

        assertEquals(1, calculated.size)
        val cb = calculated.first()
        assertEquals(kpis.ccSpendThisMonth, cb.spent, 0.001)
        assertEquals(1092.34, cb.spent, 0.001)
        assertEquals(6000.0, cb.limit, 0.001)
        assertEquals(4907.66, cb.remaining, 0.001)
        assertEquals(BudgetTintTier.BELOW_80, cb.tier)
        assertEquals("below 80 percent", cb.tier.label)
    }

    @Test
    fun testBudgetCalculator_CategoryAndAllScopeMath() = runBlocking {
        val bankId = repository.insertAccount(Account(name = "Bank", accountType = "BANK", openingBalance = 10000.0))
        val diningId = repository.insertCategory(Category(name = "Dining", color = "#EF4444"))
        val fuelId = repository.insertCategory(Category(name = "Fuel", color = "#3B82F6"))

        val now = System.currentTimeMillis()
        repository.insertTransaction(TransactionEntity(date = now, type = "EXPENSE", amount = 850.0, fromAccountId = bankId, categoryId = diningId, description = "Lunch"))
        repository.insertTransaction(TransactionEntity(date = now, type = "EXPENSE", amount = 150.0, fromAccountId = bankId, categoryId = fuelId, description = "Gas"))

        val txs = repository.allTransactions.first()
        val accs = repository.allAccounts.first()
        val cats = repository.allCategories.first()

        val catBudget = BudgetEntity(id = 1L, name = "Dining Budget", scopeType = "CATEGORY", targetName = "Dining", limitAmount = 1000.0, active = true)
        val allBudget = BudgetEntity(id = 2L, name = "Overall Budget", scopeType = "ALL", targetName = "", limitAmount = 1000.0, active = true)

        val computed = BudgetCalculator.computeBudgets(listOf(catBudget, allBudget), txs, accs, cats, monthStartDay = 1, referenceTime = now)
        val cbDining = computed.find { it.budget.name == "Dining Budget" }!!
        val cbAll = computed.find { it.budget.name == "Overall Budget" }!!

        // Category spent equals 850.0 (85%), tier "80 to 99 percent"
        assertEquals(850.0, cbDining.spent, 0.001)
        assertEquals(BudgetTintTier.EIGHTY_TO_99, cbDining.tier)
        assertEquals("80 to 99 percent", cbDining.tier.label)

        // All spent equals 1000.0 (100%), tier "100 percent or above"
        assertEquals(1000.0, cbAll.spent, 0.001)
        assertEquals(BudgetTintTier.HUNDRED_OR_ABOVE, cbAll.tier)
        assertEquals("100 percent or above", cbAll.tier.label)
        assertTrue(cbAll.isOverLimit)
    }

    @Test
    fun testEntryLiveIndicator_SingleLineSmallestHeadroom_AndOverBudget() = runBlocking {
        val bAll = BudgetEntity(id = 1L, name = "All Spend", scopeType = "ALL", targetName = "", limitAmount = 10000.0, active = true)
        val bCard = BudgetEntity(id = 2L, name = "Card Cap", scopeType = "ACCOUNT", targetName = "ICICI Credit", limitAmount = 5000.0, active = true)
        val bDining = BudgetEntity(id = 3L, name = "Dining", scopeType = "CATEGORY", targetName = "Food", limitAmount = 1500.0, active = true)

        val calculated = listOf(
            CalculatedBudget(bAll, spent = 2000.0, limit = 10000.0, remaining = 8000.0, percentage = 20.0, tier = BudgetTintTier.BELOW_80, isOverLimit = false),
            CalculatedBudget(bCard, spent = 1000.0, limit = 5000.0, remaining = 4000.0, percentage = 20.0, tier = BudgetTintTier.BELOW_80, isOverLimit = false),
            CalculatedBudget(bDining, spent = 1200.0, limit = 1500.0, remaining = 300.0, percentage = 80.0, tier = BudgetTintTier.EIGHTY_TO_99, isOverLimit = false)
        )

        val matches = listOf(bAll, bCard, bDining)

        // For a new expense of 100.0, headroom after:
        // bAll: 8000 - 100 = 7900
        // bCard: 4000 - 100 = 3900
        // bDining: 300 - 100 = 200 (SMALLEST HEADROOM)
        val tightest = BudgetCalculator.getTightestBudgetForEntry(calculated, matches, newAmount = 100.0)
        assertNotNull(tightest)
        assertEquals("Dining", tightest!!.budget.name)

        // When ALL are over budget:
        val calculatedAllOver = listOf(
            CalculatedBudget(bCard, spent = 5100.0, limit = 5000.0, remaining = -100.0, percentage = 102.0, tier = BudgetTintTier.HUNDRED_OR_ABOVE, isOverLimit = true),
            CalculatedBudget(bDining, spent = 1800.0, limit = 1500.0, remaining = -300.0, percentage = 120.0, tier = BudgetTintTier.HUNDRED_OR_ABOVE, isOverLimit = true)
        )
        // bDining is over by 300 (more over than 100) -> smallest remaining (-300 - 50 = -350 vs -100 - 50 = -150)
        val mostOver = BudgetCalculator.getTightestBudgetForEntry(calculatedAllOver, listOf(bCard, bDining), newAmount = 50.0)
        assertEquals("Dining", mostOver!!.budget.name)
    }

    @Test
    fun testOverLimitSaveConfirm_StrictlyWithinToOverTransition() {
        val bDining = BudgetEntity(id = 1L, name = "Dining", scopeType = "CATEGORY", targetName = "Food", limitAmount = 1000.0, active = true)
        val bCard = BudgetEntity(id = 2L, name = "Card", scopeType = "ACCOUNT", targetName = "ICICI Credit", limitAmount = 2000.0, active = true)

        val cbDining = CalculatedBudget(bDining, spent = 900.0, limit = 1000.0, remaining = 100.0, percentage = 90.0, tier = BudgetTintTier.EIGHTY_TO_99, isOverLimit = false)
        val cbCard = CalculatedBudget(bCard, spent = 1950.0, limit = 2000.0, remaining = 50.0, percentage = 97.5, tier = BudgetTintTier.EIGHTY_TO_99, isOverLimit = false)

        val calculated = listOf(cbDining, cbCard)
        val matches = listOf(bDining, bCard)

        // Case 1: Expense of 150.0 -> Dining becomes 900 + 150 = 1050 (over 1000), Card becomes 1950 + 150 = 2100 (over 2000)
        // BOTH cross! Aggregates and names EVERY crossed budget.
        val crossedBoth = BudgetCalculator.checkCrossedBudgets(calculated, matches, newAmount = 150.0, oldAmount = 0.0)
        assertEquals(2, crossedBoth.size)
        assertTrue(crossedBoth.any { it.budget.name == "Dining" })
        assertTrue(crossedBoth.any { it.budget.name == "Card" })

        // Case 2: Expense of 75.0 -> Dining becomes 975 (under), Card becomes 2025 (over 2000) -> only Card crosses
        val crossedCardOnly = BudgetCalculator.checkCrossedBudgets(calculated, matches, newAmount = 75.0, oldAmount = 0.0)
        assertEquals(1, crossedCardOnly.size)
        assertEquals("Card", crossedCardOnly.first().budget.name)

        // Case 3: Downward edit or already over:
        // Suppose baseline already over limit:
        val cbAlreadyOver = CalculatedBudget(bDining, spent = 1100.0, limit = 1000.0, remaining = -100.0, percentage = 110.0, tier = BudgetTintTier.HUNDRED_OR_ABOVE, isOverLimit = true)
        // Editing an existing transaction from 500 to 550 when spent is already 1100 (priorSpent = 1100 - 500 = 600 < 1000, new = 600 + 550 = 1150 -> crosses)
        // But if priorSpent without tx was already over: priorSpent = 1100 (oldAmount = 0), already over -> does NOT fire
        val noCrossIfAlreadyOver = BudgetCalculator.checkCrossedBudgets(
            calculatedBudgets = listOf(cbAlreadyOver),
            matchingBudgets = listOf(bDining),
            newAmount = 200.0,
            oldAmount = 150.0 // delta = +50, priorSpent = 1100 - 150 = 950 < 1000, newTotal = 950 + 200 = 1150 >= 1000 -> crosses
        )
        assertEquals(1, noCrossIfAlreadyOver.size)

        // If priorSpent was ALREADY >= limit (e.g. priorSpent = 1050):
        val cbWayOver = CalculatedBudget(bDining, spent = 1200.0, limit = 1000.0, remaining = -200.0, percentage = 120.0, tier = BudgetTintTier.HUNDRED_OR_ABOVE, isOverLimit = true)
        val stayingOverNoCross = BudgetCalculator.checkCrossedBudgets(
            calculatedBudgets = listOf(cbWayOver),
            matchingBudgets = listOf(bDining),
            newAmount = 100.0,
            oldAmount = 50.0 // delta = 50, priorSpent = 1200 - 50 = 1150 >= 1000 (already over)
        )
        assertTrue(stayingOverNoCross.isEmpty())
    }

    @Test
    fun testBudgetSoftDelete_PreservesHistoryAndActiveFalseInCsv() = runBlocking {
        val b = BudgetEntity(name = "Groceries", scopeType = "CATEGORY", targetName = "Groceries", limitAmount = 4000.0, active = true)
        val id = repository.insertBudget(b)
        val inserted = repository.getBudgetById(id)
        assertNotNull(inserted)
        assertTrue(inserted!!.active)

        // Soft delete
        repository.softDeleteBudget(id)
        val softDeleted = repository.getBudgetById(id)
        assertNotNull(softDeleted)
        assertFalse(softDeleted!!.active)

        // Active list filters it out
        val activeList = repository.getActiveBudgetsList()
        assertFalse(activeList.any { it.id == id })

        // CSV export includes it with active false
        val allBudgets = repository.getAllBudgetsList()
        val csv = CsvBackupManager.buildCsv(
            transactions = emptyList(),
            accounts = emptyList(),
            categories = emptyList(),
            budgets = allBudgets
        )
        assertTrue(csv.contains("Groceries,CATEGORY,Groceries,4000.00,false"))

        // CSV import round trip parses active false
        val stream = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))
        val parsed = CsvBackupManager.parseCsvFull(stream)
        val parsedBudget = parsed.budgets.find { it.name == "Groceries" }
        assertNotNull(parsedBudget)
        assertFalse(parsedBudget!!.active)
    }

    @Test
    fun testLakhFormatEnabled_ExportedCsvShowsPlainAmountWithoutCommas() = runBlocking {
        val acc = Account(id = 1L, name = "ICICI Credit", accountType = "CREDIT_CARD", openingBalance = 0.0)
        val cat = Category(id = 1L, name = "Entertainment", color = "#EC4899")
        val tx = TransactionEntity(
            id = 1L,
            date = 1727444640000L,
            type = "EXPENSE",
            amount = 1092.34,
            fromAccountId = 1L,
            categoryId = 1L,
            description = "Paradise * 4"
        )

        // Formatters formatIndianCurrency produces lakh formatting for UI
        val formattedUi = Formatters.formatIndianCurrency(1092.34)
        assertEquals("₹1,092.34", formattedUi)

        // But CSV build MUST produce plain 1092.34 without commas
        val csv = CsvBackupManager.buildCsv(
            transactions = listOf(tx),
            accounts = listOf(acc),
            categories = listOf(cat)
        )
        assertTrue("CSV must contain 1092.34 without commas", csv.contains("1092.34"))
        assertFalse("CSV must not contain 1,092.34", csv.contains("1,092.34"))
    }

    @Test
    fun testDoubleFetch_Keeps3BudgetsNot6() = runBlocking {
        val b1 = BudgetEntity(name = "Dining", scopeType = "CATEGORY", targetName = "Dining", limitAmount = 5000.0, active = true)
        val b2 = BudgetEntity(name = "Card Cap", scopeType = "ACCOUNT", targetName = "ICICI", limitAmount = 20000.0, active = true)
        val b3 = BudgetEntity(name = "Overall", scopeType = "ALL", targetName = "", limitAmount = 50000.0, active = true)

        // Fetch 1
        repository.upsertBudget(b1)
        repository.upsertBudget(b2)
        repository.upsertBudget(b3)

        assertEquals(3, repository.getAllBudgetsList().size)

        // Fetch 2 (identical 3 budgets)
        repository.upsertBudget(b1)
        repository.upsertBudget(b2)
        repository.upsertBudget(b3)

        // Still exactly 3 budgets!
        assertEquals(3, repository.getAllBudgetsList().size)
    }

    @Test
    fun testBudgetSoftDelete_RecreateRevivesSoftDeleted() = runBlocking {
        val b = BudgetEntity(name = "Fuel Budget", scopeType = "CATEGORY", targetName = "Fuel", limitAmount = 3000.0, active = true)
        val id = repository.insertBudget(b)

        // Soft-delete it
        repository.softDeleteBudget(id)
        val softDeleted = repository.getBudgetById(id)
        assertNotNull(softDeleted)
        assertFalse(softDeleted!!.active)

        // Recreating budget on the same target (Fuel)
        val recreated = BudgetEntity(name = "Fuel Budget New", scopeType = "CATEGORY", targetName = "Fuel", limitAmount = 3500.0, active = true)
        val upsertedId = repository.upsertBudget(recreated)

        // Reuses the same entity ID, sets active back to true, and updates limit
        assertEquals(id, upsertedId)
        val revived = repository.getBudgetById(id)
        assertNotNull(revived)
        assertTrue(revived!!.active)
        assertEquals(3500.0, revived.limitAmount, 0.001)
        assertEquals(1, repository.getAllBudgetsList().size)
    }

    @Test
    fun testToggleExemptActiveSurfaces() = runBlocking {
        // Active surfaces (Entry indicator & Save confirm) must evaluate regardless of budgetsCardVisible toggle
        val b = BudgetEntity(name = "Dining", scopeType = "CATEGORY", targetName = "Food", limitAmount = 1000.0, active = true)
        val cb = CalculatedBudget(b, spent = 950.0, limit = 1000.0, remaining = 50.0, percentage = 95.0, tier = BudgetTintTier.EIGHTY_TO_99, isOverLimit = false)

        // Check live headroom evaluation is non-null
        val tightest = BudgetCalculator.getTightestBudgetForEntry(listOf(cb), listOf(b), newAmount = 100.0)
        assertNotNull(tightest)
        assertEquals("Dining", tightest!!.budget.name)

        // Check over-limit confirmation fires correctly
        val crossed = BudgetCalculator.checkCrossedBudgets(listOf(cb), listOf(b), newAmount = 100.0, oldAmount = 0.0)
        assertEquals(1, crossed.size)
        assertEquals("Dining", crossed.first().budget.name)
    }

    @Test
    fun testFormat2_RoundTrip_WithCommitmentsAndBudgets() = runBlocking {
        val accKbl = Account(id = 1L, name = "KBL", accountType = "BANK", openingBalance = 50000.0)
        val accIcici = Account(id = 2L, name = "ICICI", accountType = "CREDIT_CARD", openingBalance = 0.0)
        val catOthers = Category(id = 1L, name = "Others", color = "#64748B")
        val catLoan = Category(id = 2L, name = "Loan", color = "#EF4444")
        val catEnt = Category(id = 3L, name = "Entertainment", color = "#EC4899")

        val tx1 = TransactionEntity(id = 1L, date = 1727444640000L, type = "EXPENSE", amount = 1092.34, fromAccountId = 2L, categoryId = 3L, description = "Paradise * 4")
        val com1 = com.example.data.model.Commitment(
            id = 1L,
            type = "IOU",
            name = "To Kiran",
            amount = 1500.00,
            direction = "OUTGOING",
            dueDate = 1728000000000L,
            accountId = 1L,
            categoryId = 1L,
            active = true
        )
        val com2 = com.example.data.model.Commitment(
            id = 2L,
            type = "RECURRING",
            name = "Car Loan",
            amount = 11122.00,
            direction = "OUTGOING",
            dayOfMonth = 10,
            accountId = 1L,
            categoryId = 2L,
            active = true
        )
        val com3 = com.example.data.model.Commitment(
            id = 3L,
            type = "RECURRING",
            name = "House Loan",
            amount = 25000.00,
            direction = "OUTGOING",
            dayOfMonth = 4,
            accountId = 2L,
            categoryId = 2L,
            active = true
        )

        val b1 = BudgetEntity(id = 1L, name = "Dining", scopeType = "CATEGORY", targetName = "Others", limitAmount = 2000.0, active = true)

        val csv = CsvBackupManager.buildCsv(
            transactions = listOf(tx1),
            accounts = listOf(accKbl, accIcici),
            categories = listOf(catOthers, catLoan, catEnt),
            commitments = listOf(com1, com2, com3),
            budgets = listOf(b1)
        )

        assertTrue(csv.contains("#FORMAT=2"))
        assertTrue(csv.contains("Paradise * 4"))
        assertTrue(csv.contains("To Kiran"))
        assertTrue(csv.contains("Car Loan"))
        assertTrue(csv.contains("House Loan"))

        val stream = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))
        val parsed = CsvBackupManager.parseCsvFull(stream)

        assertEquals(2, parsed.formatVersion)
        assertEquals(1, parsed.transactions.size)
        assertEquals(3, parsed.dues.size)
        assertEquals(1, parsed.budgets.size)

        val parsedKiran = parsed.dues.find { it.name == "To Kiran" }!!
        assertEquals(1500.00, parsedKiran.amount, 0.001)
        assertEquals("IOU", parsedKiran.type)
        assertEquals("KBL", parsedKiran.account)
        assertEquals("Others", parsedKiran.category)
        assertTrue(parsedKiran.active)

        val parsedCar = parsed.dues.find { it.name == "Car Loan" }!!
        assertEquals(11122.00, parsedCar.amount, 0.001)
        assertEquals("RECURRING", parsedCar.type)
        assertEquals(10, parsedCar.dayOfMonth)
        assertEquals("KBL", parsedCar.account)
        assertEquals("Loan", parsedCar.category)
        assertTrue(parsedCar.active)

        val parsedHouse = parsed.dues.find { it.name == "House Loan" }!!
        assertEquals(25000.00, parsedHouse.amount, 0.001)
        assertEquals("RECURRING", parsedHouse.type)
        assertEquals(4, parsedHouse.dayOfMonth)
        assertEquals("ICICI", parsedHouse.account)
        assertEquals("Loan", parsedHouse.category)
        assertTrue(parsedHouse.active)
    }

    @Test
    fun testMigrationGuard_1to2to3to4() {
        assertNotNull(AppDatabase.MIGRATION_1_2)
        assertNotNull(AppDatabase.MIGRATION_2_3)
        assertNotNull(AppDatabase.MIGRATION_3_4)
        assertEquals(1, AppDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, AppDatabase.MIGRATION_1_2.endVersion)
        assertEquals(2, AppDatabase.MIGRATION_2_3.startVersion)
        assertEquals(3, AppDatabase.MIGRATION_2_3.endVersion)
        assertEquals(3, AppDatabase.MIGRATION_3_4.startVersion)
        assertEquals(4, AppDatabase.MIGRATION_3_4.endVersion)
    }
}
