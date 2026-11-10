package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.domain.model.EnrichedTransaction
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.domain.calculator.LedgerCalculator
import com.example.domain.model.ChartLevel
import com.example.domain.model.FilterState
import com.example.domain.model.PeriodFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IssuesAndEnhancementsTest {

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
        com.example.domain.util.Formatters.updateCurrentCurrencySymbol("₹")
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testHistoricalBalanceSept30() = runBlocking {
        // Accounts with user's exact opening balances:
        // Cash 9000, KBL 44.40, ICICI 258.47, SBI Appa 219.05, SBI Amma 355.23, UBI 1074.29
        // ICICI Credit (credit card) opening = 0.00
        val cash = Account(id = 1, name = "Cash", accountType = "CASH", openingBalance = 9000.0)
        val kbl = Account(id = 2, name = "KBL", accountType = "BANK", openingBalance = 44.40)
        val icici = Account(id = 3, name = "ICICI", accountType = "BANK", openingBalance = 258.47)
        val card = Account(id = 4, name = "ICICI Credit", accountType = "CREDIT_CARD", openingBalance = 0.0)
        val sbiAppa = Account(id = 5, name = "SBI Appa", accountType = "BANK", openingBalance = 219.05)
        val sbiAmma = Account(id = 6, name = "SBI Amma", accountType = "BANK", openingBalance = 355.23)
        val ubi = Account(id = 7, name = "UBI", accountType = "BANK", openingBalance = 1074.29)
        val allAccounts = listOf(cash, kbl, icici, card, sbiAppa, sbiAmma, ubi)

        // Opening total for bank + cash = 9000 + 44.40 + 258.47 + 219.05 + 355.23 + 1074.29 = 10,951.44
        val openingBankAndCash = allAccounts.filter { it.accountType != "CREDIT_CARD" }.sumOf { it.openingBalance }
        assertEquals(10951.44, openingBankAndCash, 0.001)

        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 22, 10, 0, 0)
        }
        val septDate = cal.timeInMillis

        // Sept transactions:
        // Income = 24,968.00 (Cash 5000 + 11200 + 7000, KBL 1500 + 268)
        // Bank/Cash Expenses = 8,157.00
        // Net change in bank/cash = +24,968.00 - 8,157.00 = +16,811.00
        // Expected cumulative balance on Sept 30 = 10,951.44 + 16,811.00 = 27,762.44
        val txs = listOf(
            TransactionEntity(id = 1, amount = 24968.0, type = "INCOME", toAccountId = 1, date = septDate),
            TransactionEntity(id = 2, amount = 8157.0, type = "EXPENSE", fromAccountId = 1, date = septDate),
            TransactionEntity(id = 3, amount = 1092.34, type = "EXPENSE", fromAccountId = 4, date = septDate) // Credit card expense (not bank)
        )

        val endSeptCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.SEPTEMBER)
            set(Calendar.DAY_OF_MONTH, 30)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }
        val refTimeSept = endSeptCal.timeInMillis

        val kpis = LedgerCalculator.computeDashboardKpis(
            accounts = allAccounts,
            transactions = txs,
            monthStartDay = 1,
            now = refTimeSept,
            allAccountTransactions = txs
        )

        // Current balance should be ₹27,762.44 as of Sept 30, NOT ₹10,951.44
        assertEquals(27762.44, kpis.currentBalance, 0.01)
    }

    @Test
    fun testCurrentBalanceDoesNotDropOnExpenseFilter() {
        val cash = Account(id = 1, name = "Cash", accountType = "CASH", openingBalance = 9000.0)
        val allAccounts = listOf(cash)

        val txs = listOf(
            TransactionEntity(id = 1, amount = 20000.0, type = "INCOME", toAccountId = 1, date = 1000L),
            TransactionEntity(id = 2, amount = 5000.0, type = "EXPENSE", fromAccountId = 1, date = 2000L)
        )

        // Real balance = 9000 + 20000 - 5000 = 24000
        val kpis = LedgerCalculator.computeDashboardKpis(
            accounts = allAccounts,
            transactions = txs,
            monthStartDay = 1,
            now = 3000L,
            allAccountTransactions = txs
        )

        assertEquals(24000.0, kpis.currentBalance, 0.01)
    }

    @Test
    fun testOctoberCredits15() {
        val cash = Account(id = 1, name = "Cash", accountType = "CASH", openingBalance = 1000.0)
        val allAccounts = listOf(cash)

        val oct1Cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 1, 12, 0, 0)
        }
        val oct1Date = oct1Cal.timeInMillis

        val septCal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 15, 12, 0, 0)
        }
        val septDate = septCal.timeInMillis

        val txs = listOf(
            TransactionEntity(id = 1, amount = 5000.0, type = "INCOME", toAccountId = 1, date = septDate),
            TransactionEntity(id = 2, amount = 15.0, type = "INCOME", toAccountId = 1, date = oct1Date)
        )

        val kpisOct = LedgerCalculator.computeDashboardKpis(
            accounts = allAccounts,
            transactions = txs,
            monthStartDay = 1,
            now = oct1Date,
            allAccountTransactions = txs
        )

        // For October cycle (starting Oct 1), totalCredits must be ₹15
        assertEquals(15.0, kpisOct.totalCredits, 0.01)
    }

    @Test
    fun testMonthlyComparisonFiltersToSelectedMonth() {
        val calSept = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 15, 12, 0) }
        val calOct = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 1, 12, 0) }

        val txs = listOf(
            TransactionEntity(id = 1, amount = 500.0, type = "EXPENSE", fromAccountId = 1, date = calSept.timeInMillis),
            TransactionEntity(id = 2, amount = 15.0, type = "INCOME", toAccountId = 1, date = calOct.timeInMillis)
        )

        val allMonthly = LedgerCalculator.computeLast6MonthsComparison(txs, calOct.timeInMillis)

        // When Oct (month 10) and 2026 is filtered:
        val effMonths = setOf(10)
        val effYears = setOf(2026)
        val filteredMonthly = allMonthly.filter { it.month in effMonths && it.year in effYears }

        assertEquals(1, filteredMonthly.size)
        assertEquals(10, filteredMonthly.first().month)
        assertEquals(2026, filteredMonthly.first().year)
        assertEquals(15.0, filteredMonthly.first().income, 0.01)
    }

    @Test
    fun testMultiSelectFilters() {
        val calSept = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 15, 12, 0) }
        val calOct = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 15, 12, 0) }
        val calNov = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 15, 12, 0) }

        val tx1 = EnrichedTransaction(
            entity = TransactionEntity(id = 1, amount = 100.0, type = "EXPENSE", fromAccountId = 1, categoryId = 10, date = calSept.timeInMillis),
            fromAccount = Account(id = 1, name = "Cash", accountType = "CASH"),
            toAccount = null,
            category = Category(id = 10, name = "Food", color = "#EF4444")
        )
        val tx2 = EnrichedTransaction(
            entity = TransactionEntity(id = 2, amount = 200.0, type = "EXPENSE", fromAccountId = 2, categoryId = 20, date = calOct.timeInMillis),
            fromAccount = Account(id = 2, name = "Bank", accountType = "BANK"),
            toAccount = null,
            category = Category(id = 20, name = "Fuel", color = "#3B82F6")
        )
        val tx3 = EnrichedTransaction(
            entity = TransactionEntity(id = 3, amount = 300.0, type = "INCOME", toAccountId = 2, categoryId = 30, date = calNov.timeInMillis),
            fromAccount = null,
            toAccount = Account(id = 2, name = "Bank", accountType = "BANK"),
            category = Category(id = 30, name = "Salary", color = "#10B981")
        )

        val allTx = listOf(tx1, tx2, tx3)

        // 1. Multi-select months (Sept + Oct)
        val filterMonths = FilterState(selectedMonths = setOf(9, 10))
        val resMonths = LedgerCalculator.filterTransactions(allTx, filterMonths)
        assertEquals(2, resMonths.size)

        // 2. Single-select type (EXPENSE)
        val filterTypes = FilterState(selectedType = "EXPENSE")
        val resTypes = LedgerCalculator.filterTransactions(allTx, filterTypes)
        assertEquals(2, resTypes.size)

        // 3. Multi-select accounts (Cash only)
        val filterAccounts = FilterState(selectedAccountIds = setOf(1L))
        val resAccounts = LedgerCalculator.filterTransactions(allTx, filterAccounts)
        assertEquals(1, resAccounts.size)
        assertEquals(1L, resAccounts.first().id)
    }

    @Test
    fun testDedicatedTrendBarsHaveIncomeExpenseTransfer() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 10, 12, 0) }
        val d = cal.timeInMillis

        val txs = listOf(
            TransactionEntity(id = 1, amount = 1000.0, type = "INCOME", toAccountId = 1, date = d),
            TransactionEntity(id = 2, amount = 250.0, type = "EXPENSE", fromAccountId = 1, date = d),
            TransactionEntity(id = 3, amount = 150.0, type = "TRANSFER", fromAccountId = 1, toAccountId = 2, date = d)
        )
        val accs = listOf(
            Account(id = 1, name = "Acc1", accountType = "BANK", openingBalance = 5000.0),
            Account(id = 2, name = "Acc2", accountType = "BANK", openingBalance = 1000.0)
        )

        val bars = LedgerCalculator.computeChartBars(
            accounts = accs,
            transactions = txs,
            level = ChartLevel.DAYS,
            scopeYears = emptySet(),
            scopeMonths = emptySet(),
            scopeWeeks = emptySet(),
            now = d
        )

        val activeDayBar = bars.firstOrNull { it.key == LedgerCalculator.dayStart(d) }
        assertNotNull(activeDayBar)
        assertEquals(1000.0, activeDayBar!!.income, 0.01)
        assertEquals(250.0, activeDayBar.expense, 0.01)
        assertEquals(150.0, activeDayBar.transfer, 0.01)
    }
}
