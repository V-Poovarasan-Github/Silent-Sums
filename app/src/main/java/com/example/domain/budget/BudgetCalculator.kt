package com.example.domain.budget

import com.example.data.model.Account
import com.example.data.model.BudgetEntity
import com.example.data.model.Category
import com.example.data.model.TransactionEntity
import com.example.domain.calculator.LedgerCalculator
import java.util.Calendar
import java.util.Locale

enum class BudgetTintTier(val label: String) {
    BELOW_80("below 80 percent"),
    EIGHTY_TO_99("80 to 99 percent"),
    HUNDRED_OR_ABOVE("100 percent or above")
}

data class CalculatedBudget(
    val budget: BudgetEntity,
    val spent: Double,
    val limit: Double,
    val remaining: Double,
    val percentage: Double,
    val tier: BudgetTintTier,
    val isOverLimit: Boolean
)

object BudgetCalculator {

    /**
     * Progress tints:
     * "below 80 percent" -> green
     * "80 to 99 percent" -> amber
     * "100 percent or above" -> red
     */
    fun determineTier(percentage: Double): BudgetTintTier {
        return if (percentage < 80.0) {
            BudgetTintTier.BELOW_80
        } else if (percentage < 100.0) {
            BudgetTintTier.EIGHTY_TO_99
        } else {
            BudgetTintTier.HUNDRED_OR_ABOVE
        }
    }

    /**
     * Calculates all active budgets reusing the exact tile code paths:
     * - ACCOUNT scope on CREDIT_CARD: matches Card Spend (This Mo) tile to the paisa (EXPENSE-only from card).
     * - CATEGORY scope: matches Where-money-went figure for that category.
     * - ALL scope: ledger total expense in cycle.
     */
    fun computeBudgets(
        budgets: List<BudgetEntity>,
        transactions: List<TransactionEntity>,
        accounts: List<Account>,
        categories: List<Category>,
        monthStartDay: Int,
        referenceTime: Long = System.currentTimeMillis(),
        effYears: Set<Int> = emptySet(),
        effMonths: Set<Int> = emptySet()
    ): List<CalculatedBudget> {
        val activeBudgets = budgets.filter { it.active }
        if (activeBudgets.isEmpty()) return emptyList()

        val hasPeriodFilter = effYears.isNotEmpty() || effMonths.isNotEmpty()
        val cycleExpenses = if (hasPeriodFilter) {
            transactions.filter { tx ->
                val isExp = tx.type.equals("EXPENSE", ignoreCase = true)
                val cal = Calendar.getInstance().apply { timeInMillis = tx.date }
                val y = cal.get(Calendar.YEAR)
                val m = cal.get(Calendar.MONTH) + 1
                val matchY = effYears.isEmpty() || y in effYears
                val matchM = effMonths.isEmpty() || m in effMonths
                isExp && matchY && matchM
            }
        } else {
            val (thisStart, thisEnd) = LedgerCalculator.getCycleRange(referenceTime, monthStartDay)
            transactions.filter {
                it.type.equals("EXPENSE", ignoreCase = true) && it.date in thisStart..thisEnd
            }
        }

        val accountByName = accounts.associateBy { it.name.trim().lowercase(Locale.ROOT) }
        val categoryByName = categories.associateBy { it.name.trim().lowercase(Locale.ROOT) }

        val multiplier = if (effMonths.isNotEmpty()) {
            effMonths.size
        } else if (effYears.isNotEmpty()) {
            effYears.size * 12
        } else {
            1
        }

        return activeBudgets.map { b ->
            val spent = when (b.scopeType.uppercase(Locale.ROOT)) {
                "ACCOUNT" -> {
                    val acc = accountByName[b.targetName.trim().lowercase(Locale.ROOT)]
                        ?: accounts.find {
                            it.name.trim().equals(b.targetName.trim(), ignoreCase = true) ||
                            it.name.trim().lowercase(Locale.ROOT).contains(b.targetName.trim().lowercase(Locale.ROOT)) ||
                            b.targetName.trim().lowercase(Locale.ROOT).contains(it.name.trim().lowercase(Locale.ROOT))
                        }
                    if (acc != null) {
                        cycleExpenses.filter { it.fromAccountId == acc.id }.sumOf { it.amount }
                    } else 0.0
                }
                "CATEGORY" -> {
                    val cat = categoryByName[b.targetName.trim().lowercase(Locale.ROOT)]
                        ?: categories.find {
                            it.name.trim().equals(b.targetName.trim(), ignoreCase = true) ||
                            it.name.trim().lowercase(Locale.ROOT).contains(b.targetName.trim().lowercase(Locale.ROOT)) ||
                            b.targetName.trim().lowercase(Locale.ROOT).contains(it.name.trim().lowercase(Locale.ROOT))
                        }
                    if (cat != null) {
                        cycleExpenses.filter { it.categoryId == cat.id }.sumOf { it.amount }
                    } else 0.0
                }
                else -> {
                    // ALL scope: total cycle expense
                    cycleExpenses.sumOf { it.amount }
                }
            }

            val limit = b.limitAmount * multiplier
            val remaining = limit - spent
            val pct = if (limit > 0.0) (spent / limit) * 100.0 else 0.0
            val tier = determineTier(pct)

            CalculatedBudget(
                budget = b,
                spent = spent,
                limit = limit,
                remaining = remaining,
                percentage = pct,
                tier = tier,
                isOverLimit = spent >= limit
            )
        }
    }

    /**
     * Finds active budgets matching an expense target:
     * - Scope ALL matches all expenses
     * - Scope ACCOUNT matches if fromAccount name matches targetName
     * - Scope CATEGORY matches if category name matches targetName
     */
    fun findMatchingBudgets(
        budgets: List<BudgetEntity>,
        fromAccountName: String?,
        categoryName: String?
    ): List<BudgetEntity> {
        val normAcc = fromAccountName?.trim()?.lowercase(Locale.ROOT) ?: ""
        val normCat = categoryName?.trim()?.lowercase(Locale.ROOT) ?: ""

        return budgets.filter { b ->
            if (!b.active) return@filter false
            when (b.scopeType.uppercase(Locale.ROOT)) {
                "ALL" -> true
                "ACCOUNT" -> normAcc.isNotEmpty() && b.targetName.trim().lowercase(Locale.ROOT) == normAcc
                "CATEGORY" -> normCat.isNotEmpty() && b.targetName.trim().lowercase(Locale.ROOT) == normCat
                else -> false
            }
        }
    }

    /**
     * Entry live indicator:
     * Returns ONE line only: the matching budget with the SMALLEST remaining headroom.
     * Evaluated on the resolved numeric amount.
     */
    fun getTightestBudgetForEntry(
        calculatedBudgets: List<CalculatedBudget>,
        matchingBudgets: List<BudgetEntity>,
        newAmount: Double
    ): CalculatedBudget? {
        val calculatedMap = calculatedBudgets.associateBy { it.budget.id }
        val matchingCalculated = matchingBudgets.mapNotNull { calculatedMap[it.id] }
        if (matchingCalculated.isEmpty()) return null

        // Return matching budget with SMALLEST remaining headroom
        return matchingCalculated.minByOrNull { it.remaining - newAmount }
    }

    /**
     * Over-limit Save confirm:
     * Fires STRICTLY on a within-to-over transition (never when already over, never on a downward edit).
     * If multiple budgets cross, it aggregates and names every crossed budget.
     */
    fun checkCrossedBudgets(
        calculatedBudgets: List<CalculatedBudget>,
        matchingBudgets: List<BudgetEntity>,
        newAmount: Double,
        oldAmount: Double = 0.0
    ): List<CalculatedBudget> {
        val delta = newAmount - oldAmount
        if (delta <= 0.0) return emptyList() // downward or neutral edit never triggers

        val calculatedMap = calculatedBudgets.associateBy { it.budget.id }
        val matchingCalculated = matchingBudgets.mapNotNull { calculatedMap[it.id] }

        // Strictly within-to-over transition:
        // prior state was spent < limit, but with delta: (spent + delta) >= limit
        return matchingCalculated.filter { cb ->
            val priorSpent = cb.spent - oldAmount // baseline spent without this edit/new tx
            val newTotal = priorSpent + newAmount
            priorSpent < cb.limit && newTotal >= cb.limit
        }
    }

    /**
     * Spending Insights budget line:
     * EXACTLY ONE line, citing the single tightest budget (smallest headroom or highest percent over).
     */
    fun getTightestInsightBudget(calculatedBudgets: List<CalculatedBudget>): CalculatedBudget? {
        if (calculatedBudgets.isEmpty()) return null
        // First look for budgets that are over limit (highest percent over)
        val over = calculatedBudgets.filter { it.isOverLimit }
        if (over.isNotEmpty()) {
            return over.maxByOrNull { it.percentage }
        }
        // Otherwise budget with smallest remaining headroom
        return calculatedBudgets.minByOrNull { it.remaining }
    }
}
