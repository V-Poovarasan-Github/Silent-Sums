package com.example.domain.calculator

import com.example.data.model.Account
import com.example.data.model.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceBreakdownTest {

    /**
     * Case A (mixed with card debt):
     * accounts = one BANK, one CASH, one CREDIT_CARD;
     * transactions include bank income, bank expense, cash expense, card expenses (creating debt),
     * and one bank-to-card transfer (card payment).
     * Assert:
     * (1) the card row appears with isCreditCard = true and its current balance equals -(unpaid debt);
     * (2) liquidTotal == sum of the BANK and CASH row currents == LedgerCalculator.computeDashboardKpis(...).currentBalance;
     * (3) netWorthInclDebt == liquidTotal + cardBalance == sum of all 3 rows.
     */
    @Test
    fun testCaseA_MixedAccountsWithCreditCardDebtAndPayment() {
        val bank = Account(id = 1L, name = "HDFC Bank", openingBalance = 10000.0, accountType = "BANK")
        val cash = Account(id = 2L, name = "Cash Wallet", openingBalance = 2000.0, accountType = "CASH")
        val card = Account(id = 3L, name = "ICICI Credit Card", openingBalance = 0.0, accountType = "CREDIT_CARD")
        val accounts = listOf(bank, cash, card)

        val txs = listOf(
            // Bank income: +5000 -> Bank bal: 15000
            TransactionEntity(id = 1L, amount = 5000.0, type = "INCOME", toAccountId = 1L, date = 1000L),
            // Bank expense: -1500 -> Bank bal: 13500
            TransactionEntity(id = 2L, amount = 1500.0, type = "EXPENSE", fromAccountId = 1L, date = 2000L),
            // Cash expense: -400 -> Cash bal: 1600
            TransactionEntity(id = 3L, amount = 400.0, type = "EXPENSE", fromAccountId = 2L, date = 3000L),
            // Card expense 1: -2500 -> Card debt: 2500
            TransactionEntity(id = 4L, amount = 2500.0, type = "EXPENSE", fromAccountId = 3L, date = 4000L),
            // Card expense 2: -1500 -> Card debt: 4000
            TransactionEntity(id = 5L, amount = 1500.0, type = "EXPENSE", fromAccountId = 3L, date = 5000L),
            // Bank-to-card transfer (card payment of 1000): Bank bal: 12500, Card unpaid debt: 3000 -> Card bal: -3000
            TransactionEntity(id = 6L, amount = 1000.0, type = "TRANSFER", fromAccountId = 1L, toAccountId = 3L, date = 6000L)
        )

        val breakdown = LedgerCalculator.computeBalanceBreakdown(accounts, txs)
        val kpis = LedgerCalculator.computeDashboardKpis(accounts, txs, monthStartDay = 1, now = 10000L)

        // Find individual rows
        val bankRow = breakdown.rows.first { it.account.id == 1L }
        val cashRow = breakdown.rows.first { it.account.id == 2L }
        val cardRow = breakdown.rows.first { it.account.id == 3L }

        // (1) Assert card row properties and unpaid debt
        assertTrue(cardRow.isCreditCard)
        assertEquals("CREDIT_CARD", cardRow.type)
        assertEquals("ICICI Credit Card", cardRow.name)
        assertEquals(0.0, cardRow.openingBalance, 0.001)
        assertEquals(1000.0, cardRow.totalIn, 0.001) // 1000 payment received
        assertEquals(4000.0, cardRow.totalOut, 0.001) // 4000 total spend
        val unpaidDebt = 3000.0
        assertEquals(-unpaidDebt, cardRow.currentBalance, 0.001)

        // Non-card row assertions
        assertFalse(bankRow.isCreditCard)
        assertEquals(10000.0, bankRow.openingBalance, 0.001)
        assertEquals(5000.0, bankRow.totalIn, 0.001)
        assertEquals(2500.0, bankRow.totalOut, 0.001) // 1500 expense + 1000 transfer out
        assertEquals(12500.0, bankRow.currentBalance, 0.001)

        assertFalse(cashRow.isCreditCard)
        assertEquals(2000.0, cashRow.openingBalance, 0.001)
        assertEquals(0.0, cashRow.totalIn, 0.001)
        assertEquals(400.0, cashRow.totalOut, 0.001)
        assertEquals(1600.0, cashRow.currentBalance, 0.001)

        // (2) Assert liquidTotal == sum of BANK and CASH row currents == kpis.currentBalance
        val expectedLiquid = bankRow.currentBalance + cashRow.currentBalance // 12500 + 1600 = 14100
        assertEquals(14100.0, breakdown.liquidTotal, 0.001)
        assertEquals(expectedLiquid, breakdown.liquidTotal, 0.001)
        assertEquals(kpis.currentBalance, breakdown.liquidTotal, 0.001)
        assertEquals(breakdown.heroTotal, breakdown.liquidTotal, 0.001)

        // (3) Assert netWorthInclDebt == liquidTotal + cardBalance == sum of all 3 rows
        val expectedNetWorth = breakdown.liquidTotal + cardRow.currentBalance // 14100 + (-3000) = 11100
        assertEquals(11100.0, breakdown.netWorthInclDebt, 0.001)
        assertEquals(expectedNetWorth, breakdown.netWorthInclDebt, 0.001)
        assertEquals(breakdown.rows.sumOf { it.currentBalance }, breakdown.netWorthInclDebt, 0.001)
    }

    /**
     * Case B (no credit cards):
     * accounts = BANK + CASH only.
     * Assert: card rows empty, liquidTotal == netWorthInclDebt == LedgerCalculator.computeDashboardKpis(...).currentBalance.
     */
    @Test
    fun testCaseB_NoCreditCards() {
        val bank = Account(id = 1L, name = "Bank", openingBalance = 5000.0, accountType = "BANK")
        val cash = Account(id = 2L, name = "Cash", openingBalance = 1500.0, accountType = "CASH")
        val accounts = listOf(bank, cash)

        val txs = listOf(
            TransactionEntity(id = 1L, amount = 1200.0, type = "INCOME", toAccountId = 1L, date = 1000L),
            TransactionEntity(id = 2L, amount = 300.0, type = "EXPENSE", fromAccountId = 2L, date = 2000L)
        )

        val breakdown = LedgerCalculator.computeBalanceBreakdown(accounts, txs)
        val kpis = LedgerCalculator.computeDashboardKpis(accounts, txs, monthStartDay = 1, now = 10000L)

        // Assert card rows empty
        val cardRows = breakdown.rows.filter { it.isCreditCard }
        assertTrue(cardRows.isEmpty())

        // Assert liquidTotal == netWorthInclDebt == kpis.currentBalance
        val expectedTotal = (5000.0 + 1200.0) + (1500.0 - 300.0) // 6200 + 1200 = 7400
        assertEquals(expectedTotal, breakdown.liquidTotal, 0.001)
        assertEquals(expectedTotal, breakdown.netWorthInclDebt, 0.001)
        assertEquals(kpis.currentBalance, breakdown.liquidTotal, 0.001)
        assertEquals(kpis.currentBalance, breakdown.netWorthInclDebt, 0.001)
    }

    /**
     * Case C (only credit cards):
     * accounts = CREDIT_CARD only.
     * liquidTotal == 0.0, netWorthInclDebt equals card balance sum.
     */
    @Test
    fun testCaseC_OnlyCreditCards() {
        val card1 = Account(id = 1L, name = "Card A", openingBalance = 0.0, accountType = "CREDIT_CARD")
        val card2 = Account(id = 2L, name = "Card B", openingBalance = 0.0, accountType = "CREDIT_CARD")
        val accounts = listOf(card1, card2)

        val txs = listOf(
            TransactionEntity(id = 1L, amount = 500.0, type = "EXPENSE", fromAccountId = 1L, date = 1000L),
            TransactionEntity(id = 2L, amount = 1200.0, type = "EXPENSE", fromAccountId = 2L, date = 2000L)
        )

        val breakdown = LedgerCalculator.computeBalanceBreakdown(accounts, txs)

        assertEquals(0.0, breakdown.liquidTotal, 0.001)
        assertEquals(-1700.0, breakdown.netWorthInclDebt, 0.001)
        assertEquals(breakdown.rows.sumOf { it.currentBalance }, breakdown.netWorthInclDebt, 0.001)
        assertTrue(breakdown.rows.all { it.isCreditCard })
    }

    @Test
    fun testMonthYearFilteredBalanceBreakdown() {
        val bank = Account(id = 1L, name = "Bank", openingBalance = 10000.0, accountType = "BANK")
        val card = Account(id = 2L, name = "Card", openingBalance = 0.0, accountType = "CREDIT_CARD")
        val accounts = listOf(bank, card)

        val cal = java.util.Calendar.getInstance()
        // Sept 2026 txs
        cal.set(2026, java.util.Calendar.SEPTEMBER, 15, 10, 0, 0)
        val septTx1 = TransactionEntity(id = 1L, amount = 2000.0, type = "INCOME", toAccountId = 1L, date = cal.timeInMillis)
        cal.set(2026, java.util.Calendar.SEPTEMBER, 20, 10, 0, 0)
        val septTx2 = TransactionEntity(id = 2L, amount = 500.0, type = "EXPENSE", fromAccountId = 1L, date = cal.timeInMillis)
        cal.set(2026, java.util.Calendar.SEPTEMBER, 25, 10, 0, 0)
        val septTx3 = TransactionEntity(id = 3L, amount = 1500.0, type = "EXPENSE", fromAccountId = 2L, date = cal.timeInMillis)

        // Oct 2026 txs
        cal.set(2026, java.util.Calendar.OCTOBER, 5, 10, 0, 0)
        val octTx1 = TransactionEntity(id = 4L, amount = 8000.0, type = "INCOME", toAccountId = 1L, date = cal.timeInMillis)

        val allTxs = listOf(septTx1, septTx2, septTx3, octTx1)

        // Filter for September 2026 (Month 9, Year 2026)
        val septTxs = allTxs.filter { tx ->
            val c = java.util.Calendar.getInstance().apply { timeInMillis = tx.date }
            c.get(java.util.Calendar.YEAR) == 2026 && (c.get(java.util.Calendar.MONTH) + 1) == 9
        }

        val septBreakdown = LedgerCalculator.computeBalanceBreakdown(accounts, septTxs)
        val bankRow = septBreakdown.rows.first { it.account.id == 1L }
        val cardRow = septBreakdown.rows.first { it.account.id == 2L }

        // In September: Bank In = 2000, Out = 500
        assertEquals(2000.0, bankRow.totalIn, 0.001)
        assertEquals(500.0, bankRow.totalOut, 0.001)
        assertEquals(11500.0, bankRow.currentBalance, 0.001) // 10000 opening + 2000 - 500

        // In September: Card In = 0, Out = 1500
        assertEquals(0.0, cardRow.totalIn, 0.001)
        assertEquals(1500.0, cardRow.totalOut, 0.001)
        assertEquals(-1500.0, cardRow.currentBalance, 0.001)
    }
}
