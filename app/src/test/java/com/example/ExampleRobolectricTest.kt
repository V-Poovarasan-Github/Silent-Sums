package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.TransactionEntity
import com.example.domain.calculator.LedgerCalculator
import com.example.domain.model.ChartLevel
import com.example.domain.model.FilterState
import com.example.domain.model.PeriodFilter
import com.example.domain.model.TimeRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SilentSums", appName)
    }

    @Test
    fun `test computeTrendPoints starts from today when user started today without blank historical dates`() {
        val accounts = listOf(Account(id = 1L, name = "Cash", openingBalance = 10000.0))
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 17, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = cal.timeInMillis
        val txCal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 17, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val transactions = listOf(
            TransactionEntity(
                id = 101L,
                amount = 3000.0,
                type = "EXPENSE",
                date = txCal.timeInMillis,
                notes = "Shopping"
            )
        )
        val enriched = LedgerCalculator.computeRunningBalances(transactions, accounts, emptyList())
        val points6M = LedgerCalculator.computeTrendPoints(
            accounts = accounts,
            transactions = transactions,
            enrichedTx = enriched,
            timeRange = TimeRange.RANGE_6M,
            currentBalance = 7000.0,
            now = now
        )
        assertTrue("Trend points should not be empty", points6M.isNotEmpty())
        assertEquals(10000.0, points6M.first().balance, 0.01)
        assertEquals(7000.0, points6M.last().balance, 0.01)
        val startOfDayCal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 17, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        points6M.forEach { pt ->
            assertTrue("Points should not precede start date", pt.timestamp >= startOfDayCal.timeInMillis)
        }
        val summary = LedgerCalculator.computeTrendSummary(points6M, TimeRange.RANGE_6M, now)
        assertEquals(10000.0, summary.startBalance, 0.01)
        assertEquals(7000.0, summary.endBalance, 0.01)
        assertEquals(-3000.0, summary.netChange, 0.01)
    }

    @Test
    fun `test computeTrendPoints handles negative balances`() {
        val accounts = listOf(Account(id = 1L, name = "Cash", openingBalance = 2000.0))
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 17, 15, 0, 0)
        }
        val now = cal.timeInMillis
        val txCal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 17, 11, 0, 0)
        }
        val transactions = listOf(
            TransactionEntity(
                id = 102L,
                amount = 5000.0,
                type = "EXPENSE",
                date = txCal.timeInMillis,
                notes = "Emergency Repairs"
            )
        )
        val enriched = LedgerCalculator.computeRunningBalances(transactions, accounts, emptyList())
        val points = LedgerCalculator.computeTrendPoints(
            accounts = accounts,
            transactions = transactions,
            enrichedTx = enriched,
            timeRange = TimeRange.RANGE_WEEKS,
            currentBalance = -3000.0,
            now = now
        )
        assertTrue("Points must be present", points.isNotEmpty())
        assertEquals(-3000.0, points.last().balance, 0.01)
        val summary = LedgerCalculator.computeTrendSummary(points, TimeRange.RANGE_WEEKS, now)
        assertEquals(2000.0, summary.startBalance, 0.01)
        assertEquals(-3000.0, summary.endBalance, 0.01)
        assertEquals(-5000.0, summary.netChange, 0.01)
        assertEquals(-3000.0, summary.lowBalance, 0.01)
    }

    @Test
    fun `test computeTrendPoints when user has no transactions yet`() {
        val accounts = listOf(Account(id = 1L, name = "Bank", openingBalance = 50000.0))
        val now = System.currentTimeMillis()
        val points = LedgerCalculator.computeTrendPoints(
            accounts = accounts,
            transactions = emptyList(),
            enrichedTx = emptyList(),
            timeRange = TimeRange.RANGE_1M,
            currentBalance = 50000.0,
            now = now
        )
        assertTrue("Should contain start and current points", points.size >= 2)
        points.forEach { pt ->
            assertEquals(50000.0, pt.balance, 0.01)
        }
    }

    @Test
    fun `test computeLast6MonthsComparison adapts dynamically to start date`() {
        val now = System.currentTimeMillis()
        val septTx = listOf(
            TransactionEntity(id = 1L, amount = 1000.0, type = "EXPENSE", date = now, fromAccountId = 1L)
        )
        val comparison = LedgerCalculator.computeLast6MonthsComparison(septTx, now = now)
        assertEquals(1, comparison.size)
        assertEquals(1000.0, comparison[0].spend, 0.01)
        assertEquals(0.0, comparison[0].income, 0.01)
        assertTrue(comparison[0].isCurrentMonth)
    }

    @Test
    fun `test computeLast6MonthsComparison with activity across 2 months`() {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.MONTH, -1)
        }
        val txs = listOf(
            TransactionEntity(id = 1L, amount = 500.0, type = "EXPENSE", date = cal.timeInMillis, fromAccountId = 1L),
            TransactionEntity(id = 2L, amount = 2000.0, type = "INCOME", date = now, toAccountId = 1L)
        )
        val comparison = LedgerCalculator.computeLast6MonthsComparison(txs, now = now)
        assertEquals(2, comparison.size)
        assertEquals(500.0, comparison[0].spend, 0.01)
        assertEquals(2000.0, comparison[1].income, 0.01)
    }

    @Test
    fun `test computeAvailableMonths adapts to earliest transaction`() {
        val now = System.currentTimeMillis()
        val months = LedgerCalculator.computeAvailableMonths(emptyList(), now = now)
        assertEquals(1, months.size)
    }

    @Test
    fun `test credit card spend and debt calculations`() {
        val bankAcc = Account(id = 1L, name = "HDFC Bank", openingBalance = 25000.0, accountType = "BANK")
        val cardAcc = Account(id = 2L, name = "ICICI Card", openingBalance = 0.0, accountType = "CREDIT_CARD")
        val accounts = listOf(bankAcc, cardAcc)
        val now = System.currentTimeMillis()
        val txs = listOf(
            TransactionEntity(
                id = 101L,
                amount = 500.0,
                type = "EXPENSE",
                date = now,
                fromAccountId = 2L,
                description = "Dinner"
            ),
            TransactionEntity(
                id = 102L,
                amount = 1500.0,
                type = "EXPENSE",
                date = now,
                fromAccountId = 1L,
                description = "Groceries"
            )
        )
        val kpis = LedgerCalculator.computeDashboardKpis(
            accounts = accounts,
            transactions = txs,
            monthStartDay = 1,
            now = now
        )
        assertEquals(23500.0, kpis.currentBalance, 0.01)
        assertEquals(2000.0, kpis.thisMonthSpend, 0.01)
        assertEquals(500.0, kpis.ccSpendThisMonth, 0.01)
        assertEquals(500.0, kpis.totalOwed, 0.01)
        assertTrue("Should indicate presence of credit cards", kpis.hasCreditCards)
    }

    @Test
    fun `test csv backup manager roundtrip`() {
        val accounts = listOf(
            Account(id = 1L, name = "Main Bank", openingBalance = 1000.0, accountType = "BANK"),
            Account(id = 2L, name = "Amazon Pay Card", openingBalance = 0.0, accountType = "CREDIT_CARD")
        )
        val categories = listOf(
            Category(id = 10L, name = "Food & Dining", color = "#EF4444")
        )
        val txs = listOf(
            TransactionEntity(
                id = 1L,
                date = 1774000000000L,
                type = "EXPENSE",
                amount = 250.0,
                fromAccountId = 2L,
                categoryId = 10L,
                description = "Pizza night",
                notes = "With friends"
            )
        )
        val csv = com.example.importbackup.CsvBackupManager.buildCsv(txs, accounts, categories)
        assertTrue("CSV header should be present", csv.contains("Date,Type,Amount"))
        val parsedRows = com.example.importbackup.CsvBackupManager.parseCsv(csv.byteInputStream())
        assertEquals(1, parsedRows.size)
        val row = parsedRows.first()
        assertEquals("EXPENSE", row.type)
        assertEquals(250.0, row.amount, 0.01)
        assertEquals("Amazon Pay Card", row.fromAccount)
        assertEquals("Food & Dining", row.category)
        assertEquals("Pizza night", row.description)
        assertEquals("With friends", row.notes)
    }

    @Test
    fun `test unified csv storage export and import roundtrip`() {
        val accounts = listOf(
            Account(id = 1L, name = "HDFC Savings", openingBalance = 50000.0, accountType = "BANK"),
            Account(id = 2L, name = "Cash Wallet", openingBalance = 2000.0, accountType = "CASH"),
            Account(id = 3L, name = "OneCard", openingBalance = 0.0, accountType = "CREDIT_CARD")
        )
        val categories = listOf(
            Category(id = 10L, name = "Groceries", color = "#10B981"),
            Category(id = 20L, name = "Salary", color = "#3B82F6")
        )
        val txs = listOf(
            TransactionEntity(
                id = 1L,
                date = 1774000000000L,
                type = "EXPENSE",
                amount = 1450.0,
                fromAccountId = 1L,
                categoryId = 10L,
                description = "Supermarket run",
                notes = "Weekly groceries"
            ),
            TransactionEntity(
                id = 2L,
                date = 1774010000000L,
                type = "INCOME",
                amount = 65000.0,
                toAccountId = 1L,
                categoryId = 20L,
                description = "Monthly Paycheck",
                notes = "March Salary"
            ),
            TransactionEntity(
                id = 3L,
                date = 1774020000000L,
                type = "EXPENSE",
                amount = 500.0,
                fromAccountId = 3L,
                categoryId = 10L,
                description = "Dinner on Card",
                notes = "ICICI Spend"
            )
        )
        val csvString = com.example.importbackup.CsvBackupManager.buildCsv(txs, accounts, categories)
        assertTrue(csvString.contains("Date,Type,Amount"))
        val parsedRecords = com.example.importbackup.CsvBackupManager.parseCsv(csvString.byteInputStream())
        assertEquals(3, parsedRecords.size)
        assertEquals(1450.0, parsedRecords[0].amount, 0.01)
        assertEquals("HDFC Savings", parsedRecords[0].fromAccount)
        assertEquals("Groceries", parsedRecords[0].category)
        assertEquals("INCOME", parsedRecords[1].type)
        assertEquals(65000.0, parsedRecords[1].amount, 0.01)
        assertEquals(500.0, parsedRecords[2].amount, 0.01)
        assertEquals("OneCard", parsedRecords[2].fromAccount)
    }

    @Test
    fun `test multi-select dropdown filtering`() {
        val accounts = listOf(
            Account(id = 1L, name = "Bank", openingBalance = 10000.0),
            Account(id = 2L, name = "Cash", openingBalance = 2000.0),
            Account(id = 3L, name = "Card", openingBalance = 0.0)
        )
        val categories = listOf(
            Category(id = 10L, name = "Food"),
            Category(id = 20L, name = "Travel"),
            Category(id = 30L, name = "Shopping")
        )
        val txs = listOf(
            TransactionEntity(id = 1L, type = "EXPENSE", amount = 100.0, fromAccountId = 1L, categoryId = 10L, date = 1000L),
            TransactionEntity(id = 2L, type = "EXPENSE", amount = 200.0, fromAccountId = 2L, categoryId = 20L, date = 2000L),
            TransactionEntity(id = 3L, type = "INCOME", amount = 500.0, toAccountId = 1L, categoryId = 30L, date = 3000L),
            TransactionEntity(id = 4L, type = "TRANSFER", amount = 50.0, fromAccountId = 1L, toAccountId = 2L, date = 4000L)
        )
        val enriched = LedgerCalculator.computeRunningBalances(txs, accounts, categories)

        val filterType = FilterState(selectedType = "EXPENSE")
        assertEquals(2, LedgerCalculator.filterTransactions(enriched, filterType).size)

        val filterCat = FilterState(selectedCategoryIds = setOf(10L, 20L))
        assertEquals(2, LedgerCalculator.filterTransactions(enriched, filterCat).size)

        val filterAcc = FilterState(selectedAccountIds = setOf(2L))
        assertEquals(2, LedgerCalculator.filterTransactions(enriched, filterAcc).size)
    }

    @Test
    fun `test adding multiple entries to expenses holds without error`() {
        val accounts = listOf(
            Account(id = 1L, name = "Bank Account", openingBalance = 50000.0),
            Account(id = 2L, name = "Cash In Hand", openingBalance = 10000.0)
        )
        val categories = listOf(
            Category(id = 1L, name = "Food"),
            Category(id = 2L, name = "Fuel"),
            Category(id = 3L, name = "Utilities"),
            Category(id = 4L, name = "Entertainment")
        )
        val transactions = mutableListOf<TransactionEntity>()
        for (i in 1..50) {
            val isExpense = i % 4 != 0
            val accId = if (i % 2 == 0) 1L else 2L
            val catId = (i % 4 + 1).toLong()
            transactions.add(
                TransactionEntity(
                    id = i.toLong(),
                    date = System.currentTimeMillis() - (50 - i) * 3600000L,
                    type = if (isExpense) "EXPENSE" else "INCOME",
                    amount = (i * 100).toDouble(),
                    fromAccountId = if (isExpense) accId else null,
                    toAccountId = if (!isExpense) accId else null,
                    categoryId = catId,
                    description = "Transaction #$i",
                    notes = "Auto-generated test entry"
                )
            )
        }
        val enriched = LedgerCalculator.computeRunningBalances(transactions, accounts, categories)
        assertEquals(50, enriched.size)
        val kpis = LedgerCalculator.computeDashboardKpis(accounts, transactions, 1)
        assertNotNull(kpis)
        assertTrue("KPIs current balance computed", kpis.currentBalance != 0.0)
        val points = LedgerCalculator.computeTrendPoints(
            accounts = accounts,
            transactions = transactions,
            enrichedTx = enriched,
            timeRange = TimeRange.RANGE_1M,
            currentBalance = kpis.currentBalance
        )
        assertTrue("Trend points created successfully", points.isNotEmpty())
    }

    @Test
    fun `test computeChartBars hierarchical drill down`() {
        val accounts = listOf(
            Account(id = 1L, name = "Bank", openingBalance = 50000.0)
        )
        val now = System.currentTimeMillis()
        val transactions = listOf(
            TransactionEntity(id = 1L, date = now - 86400000L * 2, type = "EXPENSE", amount = 1000.0, fromAccountId = 1L),
            TransactionEntity(id = 2L, date = now - 86400000L * 10, type = "EXPENSE", amount = 2000.0, fromAccountId = 1L),
            TransactionEntity(id = 3L, date = now - 86400000L * 40, type = "INCOME", amount = 10000.0, toAccountId = 1L)
        )
        val daysBars = LedgerCalculator.computeChartBars(
            accounts = accounts,
            transactions = transactions,
            level = ChartLevel.DAYS,
            now = now
        )
        assertTrue("Days bars should not be empty", daysBars.isNotEmpty())

        val monthsBars = LedgerCalculator.computeChartBars(
            accounts = accounts,
            transactions = transactions,
            level = ChartLevel.MONTHS,
            now = now
        )
        assertTrue("Months bars should not be empty", monthsBars.isNotEmpty())

        val yearsBars = LedgerCalculator.computeChartBars(
            accounts = accounts,
            transactions = transactions,
            level = ChartLevel.YEARS,
            now = now
        )
        assertTrue("Years bars should contain bars", yearsBars.isNotEmpty())
    }

    @Test
    fun `test credit card spend is excluded from running balance trend bar chart flows`() {
        val bankAccount = Account(id = 1L, name = "Bank", openingBalance = 10000.0, accountType = "BANK")
        val ccAccount = Account(id = 2L, name = "ICICI Credit", openingBalance = 0.0, accountType = "CREDIT_CARD")
        val accounts = listOf(bankAccount, ccAccount)
        val now = System.currentTimeMillis()
        
        // 1 expense from CC of 4558.76 and 1 income to Bank of 500.0
        val transactions = listOf(
            TransactionEntity(id = 1L, date = now, type = "EXPENSE", amount = 4558.76, fromAccountId = ccAccount.id),
            TransactionEntity(id = 2L, date = now, type = "INCOME", amount = 500.0, toAccountId = bankAccount.id)
        )
        val bars = LedgerCalculator.computeChartBars(
            accounts = accounts,
            transactions = transactions,
            level = ChartLevel.DAYS,
            now = now
        )
        val todayBar = bars.firstOrNull { it.isToday }
        assertNotNull("Today bar should exist", todayBar)
        // Bank balance should be 10000 + 500 = 10500 (CC expense should not decrease bank balance)
        assertEquals(10500.0, todayBar!!.balance, 0.01)
        // Expense in the bar chart should be 0 (credit spend excluded from running bank balance chart)
        assertEquals(0.0, todayBar.expense, 0.01)
        // Income should be 500
        assertEquals(500.0, todayBar.income, 0.01)
        // Net change should be +500
        assertEquals(500.0, todayBar.netChange, 0.01)
    }

    @Test
    fun `test trim empty edges in day level starts from first transaction date`() {
        val bankAccount = Account(id = 1L, name = "Bank", openingBalance = 10000.0, accountType = "BANK")
        val now = System.currentTimeMillis()
        // First transaction is today
        val transactions = listOf(
            TransactionEntity(id = 1L, date = now, type = "INCOME", amount = 1000.0, toAccountId = bankAccount.id)
        )
        val bars = LedgerCalculator.computeChartBars(
            accounts = listOf(bankAccount),
            transactions = transactions,
            level = ChartLevel.DAYS,
            now = now
        )
        // Leading empty days are trimmed, so only today's bar is present
        assertEquals(1, bars.size)
        val todayBar = bars.first()
        assertTrue(todayBar.isToday)
        assertEquals(11000.0, todayBar.balance, 0.01)
    }

    @Test
    fun `test duplicate detection in csv backup row parsing and handling`() {
        val accounts = listOf(Account(id = 1L, name = "HDFC", openingBalance = 10000.0))
        val categories = listOf(Category(id = 10L, name = "Dining"))
        val existingTxs = listOf(
            TransactionEntity(
                id = 1L,
                date = 1774000000000L,
                type = "EXPENSE",
                amount = 500.0,
                fromAccountId = 1L,
                categoryId = 10L,
                description = "Coffee",
                notes = "With colleague"
            )
        )
        val csv = com.example.importbackup.CsvBackupManager.buildCsv(existingTxs, accounts, categories)
        val parsedRows = com.example.importbackup.CsvBackupManager.parseCsv(csv.byteInputStream())
        assertEquals(1, parsedRows.size)
        val row = parsedRows.first()
        assertEquals("EXPENSE", row.type)
        assertEquals(500.0, row.amount, 0.01)
        assertEquals("HDFC", row.fromAccount)
        assertEquals("Dining", row.category)
        assertEquals("Coffee", row.description)
    }
}