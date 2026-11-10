package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.datastore.AppSettingsRepository
import com.example.data.model.Account
import com.example.data.model.BudgetEntity
import com.example.data.model.Category
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.domain.calculator.LedgerCalculator
import com.example.domain.model.AccountWithBalance
import com.example.domain.model.FilterState
import com.example.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase3B1_DashboardThreeModeTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: FinanceRepository
    private lateinit var settingsRepository: AppSettingsRepository

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(database)
        settingsRepository = AppSettingsRepository(context)
        settingsRepository.setCurrency("₹")
        com.example.domain.util.Formatters.updateCurrentCurrencySymbol("₹")
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testSourceSorting_yourMoneyAndCreditCards() {
        val bankA = Account(id = 1L, name = "Bank A", accountType = "BANK", openingBalance = 5000.0)
        val cash = Account(id = 2L, name = "Cash", accountType = "CASH", openingBalance = 5000.0)
        val bankB = Account(id = 3L, name = "Bank B", accountType = "BANK", openingBalance = 10000.0)
        val overdrawn = Account(id = 4L, name = "Overdrawn Bank", accountType = "BANK", openingBalance = -1000.0)

        val card1 = Account(id = 5L, name = "Card 1", accountType = "CREDIT_CARD", openingBalance = -15000.0)
        val card2 = Account(id = 6L, name = "Card 2", accountType = "CREDIT_CARD", openingBalance = -5000.0)
        val card3 = Account(id = 7L, name = "Card 3", accountType = "CREDIT_CARD", openingBalance = 0.0)

        val list = listOf(
            AccountWithBalance(bankA, 5000.0),
            AccountWithBalance(cash, 5000.0),
            AccountWithBalance(bankB, 10000.0),
            AccountWithBalance(overdrawn, -1000.0),
            AccountWithBalance(card1, -15000.0),
            AccountWithBalance(card2, -5000.0),
            AccountWithBalance(card3, 0.0)
        )

        val sorted = LedgerCalculator.sortSources(list)

        // Your Money: Bank B (10000) -> Bank A (5000) -> Cash (5000) -> Overdrawn (-1000)
        assertEquals("Bank B", sorted[0].account.name)
        assertEquals("Bank A", sorted[1].account.name)
        assertEquals("Cash", sorted[2].account.name)
        assertEquals("Overdrawn Bank", sorted[3].account.name)

        // Credit Cards: Card 1 (owed 15000) -> Card 2 (owed 5000) -> Card 3 (owed 0)
        assertEquals("Card 1", sorted[4].account.name)
        assertEquals("Card 2", sorted[5].account.name)
        assertEquals("Card 3", sorted[6].account.name)
    }

    @Test
    fun testIncomeMode_kpisAndBudgetsHidden() = runBlocking {
        val bankId = repository.insertAccount(Account(id = 1L, name = "Bank", accountType = "BANK", openingBalance = 0.0))
        val cardId = repository.insertAccount(Account(id = 2L, name = "Card", accountType = "CREDIT_CARD", openingBalance = -5000.0))
        val salaryCatId = repository.insertCategory(Category(id = 1L, name = "Salary", color = "#10B981"))
        val freelanceCatId = repository.insertCategory(Category(id = 2L, name = "Freelance", color = "#3B82F6"))

        val now = Calendar.getInstance()
        val thisMonthTime = now.timeInMillis
        val lastMonth = (now.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
        val lastMonthTime = lastMonth.timeInMillis

        // Income this month: 60,000 Salary, 20,000 Freelance
        repository.insertTransaction(TransactionEntity(date = thisMonthTime, type = "INCOME", amount = 60000.0, toAccountId = bankId, categoryId = salaryCatId, description = "Job"))
        repository.insertTransaction(TransactionEntity(date = thisMonthTime, type = "INCOME", amount = 20000.0, toAccountId = bankId, categoryId = freelanceCatId, description = "Side Gig"))

        // Income last month: 50,000
        repository.insertTransaction(TransactionEntity(date = lastMonthTime, type = "INCOME", amount = 50000.0, toAccountId = bankId, categoryId = salaryCatId, description = "Job"))

        // Bank -> Card transfer (Card Payment): 5,000
        repository.insertTransaction(TransactionEntity(date = thisMonthTime, type = "TRANSFER", amount = 5000.0, fromAccountId = bankId, toAccountId = cardId, description = "Card Payment"))

        // Budget that should be hidden in income mode
        repository.insertBudget(BudgetEntity(id = 1L, name = "Shopping", scopeType = "CATEGORY", targetName = "Shopping", limitAmount = 5000.0, active = true))

        val vm = DashboardViewModel(repository, settingsRepository)
        vm.setTypeMode("INCOME")

        val state = vm.uiState.awaitState { it.activeTypeMode == "INCOME" && it.incomeCategoryBreakdown.isNotEmpty() }

        // Budgets card must be hidden in Income mode
        assertFalse("Budgets card must be hidden in Income mode", state.isBudgetsCardVisibleForMode)

        // Flow KPIs
        assertEquals(80000.0, state.thisMonthCredits, 0.01)
        assertEquals(50000.0, state.lastMonthCredits, 0.01)
        assertEquals(60.0, state.creditsDeltaPercent, 0.01) // (80000 - 50000) / 50000 * 100 = +60%
        assertEquals(5000.0, state.cardPaymentsThisMonth, 0.01)

        // Income category breakdown sorted desc over active scope (All Time = 60,000 + 50,000 = 110,000 Salary; 20,000 Freelance)
        assertTrue(state.incomeCategoryBreakdown.isNotEmpty())
        assertEquals("Salary", state.incomeCategoryBreakdown[0].categoryName)
        assertEquals(110000.0, state.incomeCategoryBreakdown[0].amount, 0.01)
        assertEquals("Freelance", state.incomeCategoryBreakdown[1].categoryName)
        assertEquals(20000.0, state.incomeCategoryBreakdown[1].amount, 0.01)
        assertEquals("Bank", state.incomeCategoryBreakdown[0].topLandingAccount)
    }

    @Test
    fun testTransferMode_kpisAndRoutes() = runBlocking {
        val sbiId = repository.insertAccount(Account(id = 1L, name = "SBI Amma", accountType = "BANK", openingBalance = 20000.0))
        val iciciId = repository.insertAccount(Account(id = 2L, name = "ICICI Credit", accountType = "CREDIT_CARD", openingBalance = -10000.0))
        val cashId = repository.insertAccount(Account(id = 3L, name = "Cash Wallet", accountType = "CASH", openingBalance = 5000.0))

        val now = System.currentTimeMillis()

        // Route 1: SBI Amma -> ICICI Credit (5,000) (Card Payment)
        repository.insertTransaction(TransactionEntity(date = now, type = "TRANSFER", amount = 5000.0, fromAccountId = sbiId, toAccountId = iciciId, description = "CC Bill"))
        // Route 2: SBI Amma -> Cash Wallet (3,000)
        repository.insertTransaction(TransactionEntity(date = now, type = "TRANSFER", amount = 3000.0, fromAccountId = sbiId, toAccountId = cashId, description = "ATM"))

        val vm = DashboardViewModel(repository, settingsRepository)
        vm.setTypeMode("TRANSFER")

        val state = vm.uiState.awaitState { it.activeTypeMode == "TRANSFER" && it.transferRoutes.isNotEmpty() }

        // Budgets card hidden in Transfer mode
        assertFalse("Budgets card must be hidden in Transfer mode", state.isBudgetsCardVisibleForMode)

        // Flow KPIs
        assertEquals(8000.0, state.transfersThisMonth, 0.01)
        assertEquals(0.0, state.transfersLastMonth, 0.01)
        assertEquals(2, state.totalMoveCountThisMonth)
        assertEquals(0, state.totalMoveCountLastMonth)
        assertEquals(8000.0, state.movedOutThisMonth, 0.01)
        assertEquals(8000.0, state.movedInThisMonth, 0.01)
        assertEquals(5000.0, state.cardPaymentsThisMonth, 0.01)

        // Routes sorted desc
        assertTrue(state.transferRoutes.isNotEmpty())
        assertEquals("SBI Amma → ICICI Credit", state.transferRoutes[0].routeName)
        assertEquals(5000.0, state.transferRoutes[0].amount, 0.01)
        assertEquals("SBI Amma → Cash Wallet", state.transferRoutes[1].routeName)
        assertEquals(3000.0, state.transferRoutes[1].amount, 0.01)
    }

    @Test
    fun testCopyNits_cleanBudgetName() {
        val cleanName = LedgerCalculator.formatBudgetDisplayTitle("ICICI Credit", "ICICI Credit")
        assertEquals("ICICI Credit", cleanName)

        val withDistinctTarget = LedgerCalculator.formatBudgetDisplayTitle("Dining Budget", "Dining")
        assertEquals("Dining Budget (Dining)", withDistinctTarget)

        val allSpend = LedgerCalculator.formatBudgetDisplayTitle("Monthly Limit", "All Spend")
        assertEquals("Monthly Limit", allSpend)
    }

    @Test
    fun testSingleSelectType_derivesThreeModeMatrixDeterministically() = runBlocking {
        val vm = DashboardViewModel(repository, settingsRepository)

        vm.setFilter(FilterState(selectedType = "INCOME"))
        assertEquals("INCOME", vm.uiState.awaitState { it.filter.selectedType == "INCOME" }.activeTypeMode)

        vm.setFilter(FilterState(selectedType = "TRANSFER"))
        assertEquals("TRANSFER", vm.uiState.awaitState { it.filter.selectedType == "TRANSFER" }.activeTypeMode)

        vm.setFilter(FilterState(selectedType = "EXPENSE"))
        assertEquals("EXPENSE", vm.uiState.awaitState { it.filter.selectedType == "EXPENSE" }.activeTypeMode)

        vm.setFilter(FilterState(selectedType = "ALL"))
        assertEquals("EXPENSE", vm.uiState.awaitState { it.filter.selectedType == "ALL" }.activeTypeMode)
    }

    @Test
    fun testMonthChipSelection_scopesAllDashboardKpisToSelectedMonthAndPriorMonth() = runBlocking {
        val bankId = repository.insertAccount(Account(id = 1L, name = "Bank", accountType = "BANK", openingBalance = 10000.0))
        val cardId = repository.insertAccount(Account(id = 2L, name = "ICICI Credit", accountType = "CREDIT_CARD", openingBalance = -2000.0))
        val foodCatId = repository.insertCategory(Category(id = 1L, name = "Food", color = "#EF4444"))
        val salaryCatId = repository.insertCategory(Category(id = 2L, name = "Salary", color = "#10B981"))

        fun dateMillis(year: Int, month0Based: Int, day: Int): Long {
            return Calendar.getInstance().apply {
                set(year, month0Based, day, 12, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        val augTime = dateMillis(2026, Calendar.AUGUST, 15)
        val sepTime = dateMillis(2026, Calendar.SEPTEMBER, 15)
        val octTime = dateMillis(2026, Calendar.OCTOBER, 15)

        // Aug (month 7): Bank spend 1,200, CC spend 800, Income 40,000
        repository.insertTransaction(TransactionEntity(date = augTime, type = "EXPENSE", amount = 1200.0, fromAccountId = bankId, categoryId = foodCatId, description = "Aug Bank"))
        repository.insertTransaction(TransactionEntity(date = augTime, type = "EXPENSE", amount = 800.0, fromAccountId = cardId, categoryId = foodCatId, description = "Aug Card"))
        repository.insertTransaction(TransactionEntity(date = augTime, type = "INCOME", amount = 40000.0, toAccountId = bankId, categoryId = salaryCatId, description = "Aug Salary"))

        // Sept (month 8): Bank spend 2,500, CC spend 1,500, Income 50,000, Card payment 1,000
        repository.insertTransaction(TransactionEntity(date = sepTime, type = "EXPENSE", amount = 2500.0, fromAccountId = bankId, categoryId = foodCatId, description = "Sept Bank"))
        repository.insertTransaction(TransactionEntity(date = sepTime, type = "EXPENSE", amount = 1500.0, fromAccountId = cardId, categoryId = foodCatId, description = "Sept Card"))
        repository.insertTransaction(TransactionEntity(date = sepTime, type = "INCOME", amount = 50000.0, toAccountId = bankId, categoryId = salaryCatId, description = "Sept Salary"))
        repository.insertTransaction(TransactionEntity(date = sepTime, type = "TRANSFER", amount = 1000.0, fromAccountId = bankId, toAccountId = cardId, description = "Sept CC Bill"))

        // Oct (month 9): Bank spend 9,000, CC spend 3,000
        repository.insertTransaction(TransactionEntity(date = octTime, type = "EXPENSE", amount = 9000.0, fromAccountId = bankId, categoryId = foodCatId, description = "Oct Bank"))
        repository.insertTransaction(TransactionEntity(date = octTime, type = "EXPENSE", amount = 3000.0, fromAccountId = cardId, categoryId = foodCatId, description = "Oct Card"))

        val vm = DashboardViewModel(repository, settingsRepository)
        vm.setFilter(FilterState(selectedYears = setOf(2026), selectedMonths = setOf(9)))

        val state = vm.uiState.awaitState {
            it.filter.selectedMonths == setOf(9) && it.kpis.thisMonthSpend == 4000.0
        }

        // Sept total spend = 2500 + 1500 = 4000; Aug total spend = 1200 + 800 = 2000
        assertEquals(4000.0, state.kpis.thisMonthSpend, 0.01)
        assertEquals(2000.0, state.kpis.lastMonthSpend, 0.01)
        assertEquals(1500.0, state.kpis.ccSpendThisMonth, 0.01)
        assertEquals(800.0, state.kpis.ccSpendLastMonth, 0.01)
        assertEquals(50000.0, state.thisMonthCredits, 0.01)
        assertEquals(40000.0, state.lastMonthCredits, 0.01)
        assertEquals(1000.0, state.transfersThisMonth, 0.01)
        assertEquals(0.0, state.transfersLastMonth, 0.01)
        assertEquals(1000.0, state.cardPaymentsThisMonth, 0.01)

        // F19f: When Month slicer is set (Sept 2026), Total X scopes to that month ("Sept 2026")
        assertEquals(4000.0, state.totalExpenseInScope, 0.01)
        assertEquals(50000.0, state.totalCreditsInScope, 0.01)
        assertEquals(1000.0, state.totalTransfersInScope, 0.01)
        assertEquals("Sept 2026", state.totalScopeSubLabel)

        // F19f: When Month slicer is All (Year = 2026), Total X sums across all months in 2026 ("All months · 2026")
        vm.setFilter(FilterState(selectedYears = setOf(2026), selectedMonths = emptySet()))
        val yearState = vm.uiState.awaitState {
            it.filter.selectedMonths.isEmpty() && it.totalExpenseInScope == 18000.0
        }
        assertEquals(18000.0, yearState.totalExpenseInScope, 0.01) // 2000 + 4000 + 12000
        assertEquals(90000.0, yearState.totalCreditsInScope, 0.01) // 40000 + 50000
        assertEquals(1000.0, yearState.totalTransfersInScope, 0.01)
        assertEquals("All months · 2026", yearState.totalScopeSubLabel)
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
}
