package com.example.domain.calculator

import com.example.domain.model.BarItem
import com.example.domain.model.ChartLevel
import com.example.domain.model.PeriodFilter
import com.example.domain.model.TrendBucket
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.TransactionEntity
import com.example.domain.model.AccountWithBalance
import com.example.domain.model.AccountBreakdownRow
import com.example.domain.model.BalanceBreakdownSummary
import com.example.domain.model.CategoryBreakdownItem
import com.example.domain.model.DashboardKpis
import com.example.domain.model.EnrichedTransaction
import com.example.domain.model.FilterState
import com.example.domain.model.MonthlyIncomeSpend
import com.example.domain.model.TimeRange
import com.example.domain.model.TrendPoint
import com.example.domain.model.TrendSummary
import com.example.domain.util.Formatters
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object LedgerCalculator {

    fun computeRunningBalances(
        transactions: List<TransactionEntity>,
        accounts: List<Account>,
        categories: List<Category>
    ): List<EnrichedTransaction> {
        val accountMap = accounts.associateBy { it.id }
        val categoryMap = categories.associateBy { it.id }
        val nonCardAccounts = accounts.filter { it.accountType != "CREDIT_CARD" && !it.archived }
        val targetAccounts = if (nonCardAccounts.isNotEmpty() && accounts.size > 1 && accounts.any { it.accountType == "CREDIT_CARD" }) {
            nonCardAccounts
        } else {
            accounts
        }
        val targetIds = targetAccounts.map { it.id }.toSet()
        val sortedChronological = transactions.sortedWith(
            compareBy<TransactionEntity> { it.date }.thenBy { it.id }
        )
        var running = targetAccounts.sumOf { it.openingBalance }
        val cardBalances = accounts.filter { it.accountType == "CREDIT_CARD" }
            .associate { it.id to it.openingBalance }
            .toMutableMap()

        val mapWithRunning = sortedChronological.map { tx ->
            val fromAcc = tx.fromAccountId?.let { accountMap[it] }
            val toAcc = tx.toAccountId?.let { accountMap[it] }

            val netDelta = when (tx.type.uppercase()) {
                "INCOME" -> if (tx.toAccountId in targetIds || (targetIds.isEmpty() && tx.toAccountId == null)) tx.amount else 0.0
                "EXPENSE" -> if (tx.fromAccountId in targetIds || (targetIds.isEmpty() && tx.fromAccountId == null)) -tx.amount else 0.0
                "TRANSFER" -> {
                    if (tx.fromAccountId in targetIds && tx.toAccountId !in targetIds) -tx.amount
                    else if (tx.toAccountId in targetIds && tx.fromAccountId !in targetIds) tx.amount
                    else 0.0
                }
                else -> 0.0
            }
            running += netDelta

            // Update credit card running balance
            if (tx.fromAccountId != null && cardBalances.containsKey(tx.fromAccountId)) {
                cardBalances[tx.fromAccountId] = (cardBalances[tx.fromAccountId] ?: 0.0) - tx.amount
            }
            if (tx.toAccountId != null && cardBalances.containsKey(tx.toAccountId)) {
                cardBalances[tx.toAccountId] = (cardBalances[tx.toAccountId] ?: 0.0) + tx.amount
            }

            val isCardTx = (fromAcc?.accountType == "CREDIT_CARD") || (toAcc?.accountType == "CREDIT_CARD")
            val effectiveRunningBalance = if (fromAcc?.accountType == "CREDIT_CARD") {
                cardBalances[tx.fromAccountId] ?: running
            } else if (toAcc?.accountType == "CREDIT_CARD") {
                cardBalances[tx.toAccountId] ?: running
            } else {
                running
            }

            EnrichedTransaction(
                entity = tx,
                fromAccount = fromAcc,
                toAccount = toAcc,
                category = tx.categoryId?.let { categoryMap[it] },
                runningBalance = effectiveRunningBalance
            )
        }
        return mapWithRunning.sortedWith(
            compareByDescending<EnrichedTransaction> { it.date }.thenByDescending { it.id }
        )
    }

    fun computeAccountBalances(
        accounts: List<Account>,
        transactions: List<TransactionEntity>
    ): List<AccountWithBalance> {
        return accounts.map { account ->
            var bal = account.openingBalance
            for (tx in transactions) {
                when (tx.type.uppercase()) {
                    "INCOME" -> {
                        if (tx.toAccountId == account.id) bal += tx.amount
                    }
                    "EXPENSE" -> {
                        if (tx.fromAccountId == account.id) bal -= tx.amount
                    }
                    "TRANSFER" -> {
                        if (tx.fromAccountId == account.id) bal -= tx.amount
                        if (tx.toAccountId == account.id) bal += tx.amount
                    }
                }
            }
            AccountWithBalance(account = account, currentBalance = bal)
        }
    }

    fun computeCurrentBalance(
        accounts: List<Account>,
        transactions: List<TransactionEntity>,
        asOf: Long = System.currentTimeMillis()
    ): Double {
        val opening = accounts.sumOf { it.openingBalance }
        val net = transactions.sumOf { tx ->
            if (tx.date > asOf) 0.0
            else when (tx.type.uppercase()) {
                "INCOME" -> tx.amount
                "EXPENSE" -> -tx.amount
                else -> 0.0
            }
        }
        return opening + net
    }

    fun getCycleRange(referenceTime: Long, monthStartDay: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = referenceTime }
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        val startCal = Calendar.getInstance().apply {
            timeInMillis = referenceTime
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (currentDay >= monthStartDay) {
            startCal.set(Calendar.DAY_OF_MONTH, monthStartDay)
        } else {
            startCal.add(Calendar.MONTH, -1)
            startCal.set(Calendar.DAY_OF_MONTH, monthStartDay)
        }
        val endCal = Calendar.getInstance().apply {
            timeInMillis = startCal.timeInMillis
            add(Calendar.MONTH, 1)
            set(Calendar.DAY_OF_MONTH, monthStartDay)
            add(Calendar.MILLISECOND, -1)
        }
        return Pair(startCal.timeInMillis, endCal.timeInMillis)
    }

    fun getPreviousCycleRange(referenceTime: Long, monthStartDay: Int): Pair<Long, Long> {
        val (currentStart, _) = getCycleRange(referenceTime, monthStartDay)
        val cal = Calendar.getInstance().apply {
            timeInMillis = currentStart
            add(Calendar.MILLISECOND, -1000)
        }
        return getCycleRange(cal.timeInMillis, monthStartDay)
    }

    fun computeDashboardKpis(
        accounts: List<Account>,
        transactions: List<TransactionEntity>,
        monthStartDay: Int,
        now: Long = System.currentTimeMillis(),
        allAccountTransactions: List<TransactionEntity> = transactions,
        effYears: Set<Int> = emptySet(),
        effMonths: Set<Int> = emptySet()
    ): DashboardKpis {
        val settledTx = allAccountTransactions.filter { it.date <= now }
        val accountBalances = computeAccountBalances(accounts, settledTx)
        val bankAndCashBalances = accountBalances.filter { !it.account.archived && it.account.accountType != "CREDIT_CARD" }
        val currentBalance = if (bankAndCashBalances.isNotEmpty()) {
            bankAndCashBalances.sumOf { it.currentBalance }
        } else {
            accountBalances.filter { !it.account.archived }.sumOf { it.currentBalance }
        }

        val cardAccounts = accounts.filter { it.accountType.equals("CREDIT_CARD", ignoreCase = true) && !it.archived }
        val cardAccountIds = cardAccounts.map { it.id }.toSet()
        val hasCreditCards = cardAccounts.isNotEmpty()
        val cardsWithDebt = accountBalances.filter {
            it.account.accountType.equals("CREDIT_CARD", ignoreCase = true) && !it.account.archived && it.currentBalance < 0
        }
        val totalOwed = cardsWithDebt.sumOf { -it.currentBalance }
        val cardCount = if (cardsWithDebt.isNotEmpty()) cardsWithDebt.size else cardAccounts.size

        var thisMonthSpend = 0.0
        var lastMonthSpend = 0.0
        var totalCredits = 0.0
        var ccSpendThisMonth = 0.0
        var ccSpendLastMonth = 0.0

        val hasPeriodFilter = effMonths.isNotEmpty()
        val yearScopedTransactions = if (effYears.isNotEmpty()) {
            transactions.filter { tx ->
                Calendar.getInstance().apply { timeInMillis = tx.date }.get(Calendar.YEAR) in effYears
            }
        } else {
            transactions
        }
        val yearScopedAllAccountTransactions = if (effYears.isNotEmpty() && allAccountTransactions.isNotEmpty()) {
            allAccountTransactions.filter { tx ->
                Calendar.getInstance().apply { timeInMillis = tx.date }.get(Calendar.YEAR) in effYears
            }
        } else {
            allAccountTransactions
        }

        if (hasPeriodFilter) {
            val ccCandidateTxs = if (yearScopedAllAccountTransactions.isNotEmpty()) yearScopedAllAccountTransactions else yearScopedTransactions
            if (effMonths.size == 1) {
                val singleMonth = effMonths.first()
                val targetYear = if (effYears.isNotEmpty()) {
                    effYears.max()
                } else {
                    transactions.filter {
                        val c = Calendar.getInstance().apply { timeInMillis = it.date }
                        c.get(Calendar.MONTH) + 1 == singleMonth
                    }.maxOfOrNull {
                        Calendar.getInstance().apply { timeInMillis = it.date }.get(Calendar.YEAR)
                    } ?: Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)
                }
                val matchYears = if (effYears.size > 1) effYears else setOf(targetYear)
                val prevMonth = if (singleMonth == 1) 12 else singleMonth - 1
                val prevYear = if (singleMonth == 1) targetYear - 1 else targetYear

                val periodTxs = transactions.filter { tx ->
                    val cal = Calendar.getInstance().apply { timeInMillis = tx.date }
                    val y = cal.get(Calendar.YEAR)
                    val m = cal.get(Calendar.MONTH) + 1
                    y in matchYears && m == singleMonth
                }

                for (tx in periodTxs) {
                    val isExpense = tx.type.equals("EXPENSE", ignoreCase = true)
                    val isIncome = tx.type.equals("INCOME", ignoreCase = true)
                    if (isExpense) {
                        thisMonthSpend += tx.amount
                        if (tx.fromAccountId != null && tx.fromAccountId in cardAccountIds) {
                            ccSpendThisMonth += tx.amount
                        }
                    } else if (isIncome) {
                        totalCredits += tx.amount
                    }
                }

                if (cardAccountIds.isNotEmpty() && ccSpendThisMonth == 0.0) {
                    ccSpendThisMonth = ccCandidateTxs.filter { tx ->
                        val cal = Calendar.getInstance().apply { timeInMillis = tx.date }
                        val y = cal.get(Calendar.YEAR)
                        val m = cal.get(Calendar.MONTH) + 1
                        tx.type.equals("EXPENSE", ignoreCase = true) &&
                            tx.fromAccountId != null && tx.fromAccountId in cardAccountIds &&
                            y in matchYears && m == singleMonth
                    }.sumOf { it.amount }
                }

                lastMonthSpend = transactions.filter { tx ->
                    val cal = Calendar.getInstance().apply { timeInMillis = tx.date }
                    val y = cal.get(Calendar.YEAR)
                    val m = cal.get(Calendar.MONTH) + 1
                    tx.type.equals("EXPENSE", ignoreCase = true) && y == prevYear && m == prevMonth
                }.sumOf { it.amount }

                if (cardAccountIds.isNotEmpty()) {
                    ccSpendLastMonth = ccCandidateTxs.filter { tx ->
                        val cal = Calendar.getInstance().apply { timeInMillis = tx.date }
                        val y = cal.get(Calendar.YEAR)
                        val m = cal.get(Calendar.MONTH) + 1
                        tx.type.equals("EXPENSE", ignoreCase = true) &&
                            tx.fromAccountId != null && tx.fromAccountId in cardAccountIds &&
                            y == prevYear && m == prevMonth
                    }.sumOf { it.amount }
                }
            } else {
                val periodTxs = transactions.filter { tx ->
                    val cal = Calendar.getInstance().apply { timeInMillis = tx.date }
                    val y = cal.get(Calendar.YEAR)
                    val m = cal.get(Calendar.MONTH) + 1
                    val matchY = effYears.isEmpty() || y in effYears
                    val matchM = effMonths.isEmpty() || m in effMonths
                    matchY && matchM
                }

                for (tx in periodTxs) {
                    val isExpense = tx.type.equals("EXPENSE", ignoreCase = true)
                    val isIncome = tx.type.equals("INCOME", ignoreCase = true)
                    if (isExpense) {
                        thisMonthSpend += tx.amount
                        if (tx.fromAccountId != null && tx.fromAccountId in cardAccountIds) {
                            ccSpendThisMonth += tx.amount
                        }
                    } else if (isIncome) {
                        totalCredits += tx.amount
                    }
                }

                if (cardAccountIds.isNotEmpty() && ccSpendThisMonth == 0.0) {
                    ccSpendThisMonth = ccCandidateTxs.filter { tx ->
                        val cal = Calendar.getInstance().apply { timeInMillis = tx.date }
                        val y = cal.get(Calendar.YEAR)
                        val m = cal.get(Calendar.MONTH) + 1
                        val matchY = effYears.isEmpty() || y in effYears
                        val matchM = effMonths.isEmpty() || m in effMonths
                        tx.type.equals("EXPENSE", ignoreCase = true) &&
                            tx.fromAccountId != null && tx.fromAccountId in cardAccountIds &&
                            matchY && matchM
                    }.sumOf { it.amount }
                }

                val earliestRefTime = when {
                    effYears.isNotEmpty() && effMonths.isNotEmpty() -> {
                        val minYear = effYears.min()
                        val minMonth = effMonths.min()
                        Calendar.getInstance().apply {
                            set(Calendar.YEAR, minYear)
                            set(Calendar.MONTH, minMonth - 1)
                            set(Calendar.DAY_OF_MONTH, 1)
                            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    }
                    effYears.isNotEmpty() -> {
                        val minYear = effYears.min()
                        val minMonth = transactions.filter {
                            val c = Calendar.getInstance().apply { timeInMillis = it.date }
                            c.get(Calendar.YEAR) == minYear
                        }.minOfOrNull {
                            Calendar.getInstance().apply { timeInMillis = it.date }.get(Calendar.MONTH) + 1
                        } ?: 1
                        Calendar.getInstance().apply {
                            set(Calendar.YEAR, minYear)
                            set(Calendar.MONTH, minMonth - 1)
                            set(Calendar.DAY_OF_MONTH, 1)
                            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    }
                    effMonths.isNotEmpty() -> {
                        val minMonth = effMonths.min()
                        val targetYear = transactions.filter {
                            val c = Calendar.getInstance().apply { timeInMillis = it.date }
                            c.get(Calendar.MONTH) + 1 == minMonth
                        }.maxOfOrNull {
                            Calendar.getInstance().apply { timeInMillis = it.date }.get(Calendar.YEAR)
                        } ?: Calendar.getInstance().get(Calendar.YEAR)
                        Calendar.getInstance().apply {
                            set(Calendar.YEAR, targetYear)
                            set(Calendar.MONTH, minMonth - 1)
                            set(Calendar.DAY_OF_MONTH, 1)
                            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    }
                    else -> now
                }

                val (priorStart, priorEnd) = getPreviousCycleRange(earliestRefTime, monthStartDay)
                lastMonthSpend = transactions.filter {
                    it.type.equals("EXPENSE", ignoreCase = true) && it.date in priorStart..priorEnd
                }.sumOf { it.amount }

                if (cardAccountIds.isNotEmpty()) {
                    ccSpendLastMonth = ccCandidateTxs.filter {
                        it.type.equals("EXPENSE", ignoreCase = true) &&
                            it.fromAccountId != null && it.fromAccountId in cardAccountIds &&
                            it.date in priorStart..priorEnd
                    }.sumOf { it.amount }
                }
            }
        } else {
            val (thisStart, thisEnd) = getCycleRange(now, monthStartDay)
            val (lastStart, lastEnd) = getPreviousCycleRange(now, monthStartDay)
            for (tx in yearScopedTransactions) {
                val isExpense = tx.type.equals("EXPENSE", ignoreCase = true)
                val isIncome = tx.type.equals("INCOME", ignoreCase = true)
                if (isExpense) {
                    val fromCard = tx.fromAccountId != null && tx.fromAccountId in cardAccountIds
                    if (tx.date in thisStart..thisEnd) {
                        thisMonthSpend += tx.amount
                        if (fromCard) ccSpendThisMonth += tx.amount
                    }
                    if (tx.date in lastStart..lastEnd) {
                        lastMonthSpend += tx.amount
                        if (fromCard) ccSpendLastMonth += tx.amount
                    }
                } else if (isIncome) {
                    if (tx.date in thisStart..thisEnd) {
                        totalCredits += tx.amount
                    }
                }
            }

            val ccCandidateTxs = if (yearScopedAllAccountTransactions.isNotEmpty()) yearScopedAllAccountTransactions else yearScopedTransactions
            if (cardAccountIds.isNotEmpty()) {
                if (ccSpendThisMonth == 0.0) {
                    ccSpendThisMonth = ccCandidateTxs.filter {
                        it.type.equals("EXPENSE", ignoreCase = true) &&
                        it.fromAccountId != null && it.fromAccountId in cardAccountIds &&
                        it.date in thisStart..thisEnd
                    }.sumOf { it.amount }
                }
                if (ccSpendLastMonth == 0.0) {
                    ccSpendLastMonth = ccCandidateTxs.filter {
                        it.type.equals("EXPENSE", ignoreCase = true) &&
                        it.fromAccountId != null && it.fromAccountId in cardAccountIds &&
                        it.date in lastStart..lastEnd
                    }.sumOf { it.amount }
                }
            }
            if (totalCredits == 0.0) {
                for (tx in yearScopedTransactions) {
                    if (tx.type.equals("INCOME", ignoreCase = true) && tx.date <= now) {
                        totalCredits += tx.amount
                    }
                }
            }
        }

        val deltaPercent = if (lastMonthSpend > 0.0) {
            ((thisMonthSpend - lastMonthSpend) / lastMonthSpend) * 100.0
        } else {
            0.0
        }
        val totalExpenseAll = yearScopedTransactions.filter { it.type.equals("EXPENSE", ignoreCase = true) }.sumOf { it.amount }
        return DashboardKpis(
            currentBalance = currentBalance,
            thisMonthSpend = thisMonthSpend,
            spendDeltaPercent = deltaPercent,
            lastMonthSpend = lastMonthSpend,
            totalCredits = totalCredits,
            totalExpense = totalExpenseAll,
            accountsCount = accounts.count { !it.archived },
            totalOwed = totalOwed,
            ccSpendThisMonth = ccSpendThisMonth,
            ccSpendLastMonth = ccSpendLastMonth,
            hasCreditCards = hasCreditCards,
            cardCount = cardCount
        )
    }

    /**
     * Canonical non-period slicer predicate (Category, Account/Source, TransferRoute, DateRange).
     * Used across all Dashboard KPIs, breakdowns, insights, and trend series so every aggregate
     * honors active slicers in every Type mode.
     */
    fun matchesNonPeriodSlicers(
        tx: TransactionEntity,
        filter: FilterState,
        accounts: List<Account> = emptyList(),
        includeDateRange: Boolean = true
    ): Boolean {
        val effCats = filter.effectiveCategoryIds()
        val matchesCategory = if (effCats.isNotEmpty()) {
            val hasUncat = effCats.contains(-1L)
            val realIds = effCats.filter { it != -1L }.toSet()
            if (tx.type.equals("TRANSFER", ignoreCase = true)) {
                false
            } else {
                (hasUncat && tx.categoryId == null) ||
                    (realIds.isNotEmpty() && tx.categoryId != null && tx.categoryId in realIds)
            }
        } else {
            true
        }

        val effAccs = filter.effectiveAccountIds()
        val matchesAccount = if (effAccs.isNotEmpty()) {
            (tx.fromAccountId != null && tx.fromAccountId in effAccs) ||
                (tx.toAccountId != null && tx.toAccountId in effAccs)
        } else {
            true
        }

        val matchesTransferRoute = when {
            filter.transferRouteFromAccountId != null && filter.transferRouteToAccountId != null -> {
                tx.type.equals("TRANSFER", ignoreCase = true) &&
                    tx.fromAccountId == filter.transferRouteFromAccountId &&
                    tx.toAccountId == filter.transferRouteToAccountId
            }
            !filter.transferRouteLabel.isNullOrBlank() -> {
                val accMap = accounts.associateBy { it.id }
                val route = "${tx.fromAccountId?.let { accMap[it]?.name } ?: "Unknown"} → ${tx.toAccountId?.let { accMap[it]?.name } ?: "Unknown"}"
                tx.type.equals("TRANSFER", ignoreCase = true) && route == filter.transferRouteLabel
            }
            else -> true
        }

        val matchesDateRange = if (!includeDateRange) {
            true
        } else {
            when {
                filter.startDate != null && filter.endDate != null -> tx.date in filter.startDate..filter.endDate
                filter.startDate != null -> tx.date >= filter.startDate
                filter.endDate != null -> tx.date <= filter.endDate
                else -> true
            }
        }

        return matchesCategory && matchesAccount && matchesTransferRoute && matchesDateRange
    }

    fun filterTransactions(
        transactions: List<EnrichedTransaction>,
        filter: FilterState
    ): List<EnrichedTransaction> {
        val hasQuery = filter.searchQuery.isNotBlank()
        val q = if (hasQuery) filter.searchQuery.trim().lowercase() else ""
        val selectedType = filter.selectedType
        val hasTypeFilter = selectedType.uppercase() != "ALL" && selectedType.isNotBlank()
        val hasMultiCats = filter.selectedCategoryIds.isNotEmpty()
        val hasUncat = if (hasMultiCats) filter.selectedCategoryIds.contains(-1L) else false
        val realCatIds = if (hasMultiCats) filter.selectedCategoryIds.filter { it != -1L }.toSet() else emptySet()
        val effYears = if (filter.selectedYears.isNotEmpty()) filter.selectedYears else filter.selectedYear?.let { setOf(it) } ?: emptySet()
        val effMonths = if (filter.selectedMonths.isNotEmpty()) filter.selectedMonths else filter.selectedMonth?.let { setOf(it) } ?: emptySet()
        val cal = if (effYears.isNotEmpty() || effMonths.isNotEmpty()) Calendar.getInstance() else null

        return transactions.filter { tx ->
            val matchesQuery = if (!hasQuery) {
                true
            } else {
                tx.description.lowercase().contains(q) ||
                        tx.notes.lowercase().contains(q) ||
                        (tx.category?.name?.lowercase()?.contains(q) == true) ||
                        (tx.fromAccount?.name?.lowercase()?.contains(q) == true) ||
                        (tx.toAccount?.name?.lowercase()?.contains(q) == true) ||
                        tx.amount.toString().contains(q)
            }
            val matchesType = if (hasTypeFilter) {
                tx.type.equals(selectedType, ignoreCase = true)
            } else {
                true
            }
            val matchesCategory = when {
                hasMultiCats -> {
                    if (tx.type.equals("TRANSFER", ignoreCase = true)) {
                        false
                    } else {
                        (hasUncat && tx.entity.categoryId == null) ||
                                (realCatIds.isNotEmpty() && tx.entity.categoryId != null && tx.entity.categoryId in realCatIds)
                    }
                }
                filter.selectedCategoryId != null -> {
                    if (tx.type.equals("TRANSFER", ignoreCase = true)) {
                        false
                    } else if (filter.selectedCategoryId == -1L) {
                        tx.entity.categoryId == null
                    } else {
                        tx.entity.categoryId == filter.selectedCategoryId
                    }
                }
                else -> true
            }
            val matchesAccount = when {
                filter.selectedAccountIds.isNotEmpty() -> {
                    (tx.entity.fromAccountId != null && tx.entity.fromAccountId in filter.selectedAccountIds) ||
                            (tx.entity.toAccountId != null && tx.entity.toAccountId in filter.selectedAccountIds)
                }
                filter.selectedAccountId != null -> {
                    tx.entity.fromAccountId == filter.selectedAccountId ||
                            tx.entity.toAccountId == filter.selectedAccountId
                }
                else -> true
            }
            val matchesTransferRoute = when {
                filter.transferRouteFromAccountId != null && filter.transferRouteToAccountId != null -> {
                    tx.type.equals("TRANSFER", ignoreCase = true) &&
                            tx.entity.fromAccountId == filter.transferRouteFromAccountId &&
                            tx.entity.toAccountId == filter.transferRouteToAccountId
                }
                !filter.transferRouteLabel.isNullOrBlank() -> {
                    val route = "${tx.fromAccount?.name ?: "Unknown"} → ${tx.toAccount?.name ?: "Unknown"}"
                    tx.type.equals("TRANSFER", ignoreCase = true) && route == filter.transferRouteLabel
                }
                else -> true
            }
            val matchesDate = when {
                filter.startDate != null && filter.endDate != null -> {
                    tx.date in filter.startDate..filter.endDate
                }
                filter.startDate != null -> tx.date >= filter.startDate
                filter.endDate != null -> tx.date <= filter.endDate
                effYears.isNotEmpty() && effMonths.isNotEmpty() -> {
                    val c = cal!!.apply { timeInMillis = tx.date }
                    val txMonth = c.get(Calendar.MONTH) + 1
                    val txYear = c.get(Calendar.YEAR)
                    txYear in effYears && txMonth in effMonths
                }
                effYears.isNotEmpty() -> {
                    val c = cal!!.apply { timeInMillis = tx.date }
                    c.get(Calendar.YEAR) in effYears
                }
                effMonths.isNotEmpty() -> {
                    val c = cal!!.apply { timeInMillis = tx.date }
                    c.get(Calendar.MONTH) + 1 in effMonths
                }
                else -> true
            }
            matchesQuery && matchesType && matchesCategory && matchesAccount && matchesTransferRoute && matchesDate
        }
    }

    fun computeCategoryBreakdown(
        filteredTransactions: List<EnrichedTransaction>,
        allCategories: List<Category>
    ): List<CategoryBreakdownItem> {
        val categoryMap = allCategories.associateBy { it.id }
        val expenseTx = filteredTransactions.filter { it.type.equals("EXPENSE", ignoreCase = true) }
        val totalExpense = expenseTx.sumOf { it.amount }
        if (totalExpense <= 0.0) return emptyList()
        val grouped = expenseTx.groupBy { it.entity.categoryId }
            .map { (catId, txList) ->
                val sum = txList.sumOf { it.amount }
                val category = catId?.let { categoryMap[it] }
                val name = category?.name ?: "Uncategorized"
                val color = category?.color ?: "#64748B"
                CategoryBreakdownItem(
                    categoryId = catId ?: -1L,
                    name = name,
                    colorHex = color,
                    amount = sum,
                    percentage = ((sum / totalExpense) * 100).toFloat()
                )
            }
            .sortedByDescending { it.amount }

        return grouped
    }

    fun computeAvailableMonths(
        transactions: List<TransactionEntity>,
        now: Long = System.currentTimeMillis()
    ): List<Pair<Int, Int>> {
        val earliestTx = transactions.minByOrNull { it.date }?.date
        val topCal = Calendar.getInstance().apply { timeInMillis = now }
        transactions.maxOfOrNull { it.date }?.let { latest ->
            val latestCal = Calendar.getInstance().apply { timeInMillis = latest }
            val a = topCal.get(Calendar.YEAR) * 12 + topCal.get(Calendar.MONTH)
            val b = latestCal.get(Calendar.YEAR) * 12 + latestCal.get(Calendar.MONTH)
            if (b > a) topCal.timeInMillis = latest
        }
        val topMonth = topCal.get(Calendar.MONTH)
        val topYear = topCal.get(Calendar.YEAR)
        val monthsCount = if (earliestTx != null) {
            val startCal = Calendar.getInstance().apply { timeInMillis = earliestTx }
            val startMonthYear = startCal.get(Calendar.YEAR) * 12 + startCal.get(Calendar.MONTH)
            val topMonthYear = topYear * 12 + topMonth
            val diff = (topMonthYear - startMonthYear) + 1
            diff.coerceAtLeast(1)
        } else {
            1
        }
        val list = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until monthsCount) {
            val mCal = Calendar.getInstance().apply {
                timeInMillis = topCal.timeInMillis
                add(Calendar.MONTH, -i)
            }
            list.add(Pair(mCal.get(Calendar.MONTH) + 1, mCal.get(Calendar.YEAR)))
        }
        return list
    }

    fun computeLast6MonthsComparison(
        transactions: List<TransactionEntity>,
        now: Long = System.currentTimeMillis()
    ): List<MonthlyIncomeSpend> {
        val result = mutableListOf<MonthlyIncomeSpend>()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val currentMonth = cal.get(Calendar.MONTH)
        val currentYear = cal.get(Calendar.YEAR)
        val earliestTxDate = transactions.minByOrNull { it.date }?.date
        val monthsCount = if (earliestTxDate != null) {
            val startCal = Calendar.getInstance().apply { timeInMillis = earliestTxDate }
            val startMonthYear = startCal.get(Calendar.YEAR) * 12 + startCal.get(Calendar.MONTH)
            val currentMonthYear = currentYear * 12 + currentMonth
            val diff = (currentMonthYear - startMonthYear) + 1
            diff.coerceIn(1, 6)
        } else {
            1
        }
        for (i in (monthsCount - 1) downTo 0) {
            val monthCal = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.MONTH, -i)
            }
            val m = monthCal.get(Calendar.MONTH)
            val y = monthCal.get(Calendar.YEAR)
            val monthLabel = SimpleDateFormat("MMM", Locale.getDefault()).format(monthCal.time)
            var inc = 0.0
            var sp = 0.0
            for (tx in transactions) {
                val tCal = Calendar.getInstance().apply { timeInMillis = tx.date }
                if (tCal.get(Calendar.MONTH) == m && tCal.get(Calendar.YEAR) == y) {
                    when (tx.type.uppercase()) {
                        "INCOME" -> inc += tx.amount
                        "EXPENSE" -> sp += tx.amount
                    }
                }
            }
            result.add(
                MonthlyIncomeSpend(
                    monthLabel = monthLabel, month = m + 1, year = y,
                    income = inc, spend = sp, isCurrentMonth = (m == currentMonth && y == currentYear)
                )
            )
        }
        return result
    }

    fun computeAccountsBalanceAtTime(
        accounts: List<Account>,
        transactions: List<TransactionEntity>,
        timeInMillis: Long,
        includeOpeningBalance: Boolean = true
    ): Double {
        val targetIds = accounts.map { it.id }.toSet()
        var bal = if (includeOpeningBalance) accounts.sumOf { it.openingBalance } else 0.0
        for (tx in transactions) {
            if (tx.date <= timeInMillis) {
                when (tx.type.uppercase()) {
                    "INCOME" -> {
                        if (tx.toAccountId in targetIds || tx.toAccountId == null) bal += tx.amount
                    }
                    "EXPENSE" -> {
                        if (tx.fromAccountId in targetIds || tx.fromAccountId == null) bal -= tx.amount
                    }
                    "TRANSFER" -> {
                        if (tx.fromAccountId in targetIds && tx.toAccountId !in targetIds) bal -= tx.amount
                        else if (tx.toAccountId in targetIds && tx.fromAccountId !in targetIds) bal += tx.amount
                    }
                }
            }
        }
        return bal
    }

    fun computeBalanceAtTime(
        totalOpeningBalance: Double,
        transactions: List<TransactionEntity>,
        timeInMillis: Long
    ): Double {
        var net = 0.0
        for (tx in transactions) {
            if (tx.date <= timeInMillis) {
                when (tx.type.uppercase()) {
                    "INCOME" -> net += tx.amount
                    "EXPENSE" -> net -= tx.amount
                }
            }
        }
        return totalOpeningBalance + net
    }

    fun yearOf(ts: Long): Int = Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.YEAR)
    fun monthKeyOf(ts: Long): Long = monthStart(ts)
    fun weekIndexOfDate(ts: Long): Int = weekIndexOf(Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.DAY_OF_MONTH))
    fun weekStartOf(monthKey: Long, weekIndex: Int): Long = Calendar.getInstance().apply {
        timeInMillis = monthKey
        set(Calendar.DAY_OF_MONTH, (weekIndex - 1) * 7 + 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    fun weekEndOf(monthKey: Long, weekIndex: Int): Long =
        if (weekIndex >= 5) monthEnd(monthKey) else dayEnd(weekStartOf(monthKey, weekIndex) + 6L * 86400000L)

    fun weekIndexOf(dayOfMonth: Int): Int = when {
        dayOfMonth <= 7 -> 1
        dayOfMonth <= 14 -> 2
        dayOfMonth <= 21 -> 3
        dayOfMonth <= 28 -> 4
        else -> 5
    }

    /**
     * Single source of truth for week pin chip label ("W<w> • <startDay>–<endDay MMM>")
     * using month-bucket weekIndexOf / weekStartOf / weekEndOf (Confirm C).
     */
    fun formatWeekPinLabel(monthKey: Long, weekIndex: Int): String {
        val w = weekIndex.coerceIn(1, 5)
        val wStart = weekStartOf(monthKey, w)
        val wEnd = weekEndOf(monthKey, w)
        val startFmt = SimpleDateFormat("d", Locale.US).format(Date(wStart))
        val endFmt = SimpleDateFormat("d MMM", Locale.US).format(Date(wEnd))
        return "W$w • $startFmt–$endFmt"
    }

    fun formatWeekPinLabelForDate(dateMillis: Long): String {
        val mk = monthStart(dateMillis)
        val w = weekIndexOfDate(dateMillis)
        return formatWeekPinLabel(mk, w)
    }

    internal fun dayStart(ts: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ts
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    internal fun dayEnd(ts: Long): Long = dayStart(ts) + 86400000L - 1

    internal fun monthStart(ts: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ts
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    internal fun monthEnd(ts: Long): Long = Calendar.getInstance().apply {
        timeInMillis = monthStart(ts)
        add(Calendar.MONTH, 1)
        add(Calendar.MILLISECOND, -1)
    }.timeInMillis

    fun computeTrendBuckets(
        transactions: List<TransactionEntity>,
        accounts: List<Account>,
        timeRange: TimeRange,
        now: Long = System.currentTimeMillis()
    ): List<TrendBucket> {
        if (transactions.isEmpty()) return emptyList()
        val weekMode = timeRange == TimeRange.RANGE_WEEKS
        val nominalStart = when (timeRange) {
            TimeRange.RANGE_WEEKS -> monthStart(now)
            TimeRange.RANGE_1M -> now - 30L * 86400000L
            TimeRange.RANGE_3M -> now - 90L * 86400000L
            TimeRange.RANGE_6M -> now - 180L * 86400000L
            TimeRange.RANGE_1Y -> now - 365L * 86400000L
            TimeRange.RANGE_ALL -> Long.MIN_VALUE
        }
        val grouped = if (weekMode) {
            transactions.groupBy { tx ->
                val cal = Calendar.getInstance().apply { timeInMillis = tx.date }
                (cal.get(Calendar.YEAR) * 100 + cal.get(Calendar.MONTH)) * 10 +
                        weekIndexOf(cal.get(Calendar.DAY_OF_MONTH))
            }
        } else {
            transactions.groupBy { tx -> monthStart(tx.date) }
        }
        return grouped.map { (_, txs) ->
            val first = txs.minOf { it.date }
            val cal = Calendar.getInstance().apply { timeInMillis = first }
            val bucketStart: Long
            val bucketEnd: Long
            if (weekMode) {
                val w = weekIndexOf(cal.get(Calendar.DAY_OF_MONTH))
                val startDay = (w - 1) * 7 + 1
                bucketStart = Calendar.getInstance().apply {
                    timeInMillis = first
                    set(Calendar.DAY_OF_MONTH, startDay)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                bucketEnd = if (startDay >= 29) monthEnd(first) else dayEnd(bucketStart + 6 * 86400000L)
            } else {
                bucketStart = monthStart(first)
                bucketEnd = monthEnd(first)
            }
            val net = txs.sumOf { tx ->
                when (tx.type.uppercase()) {
                    "INCOME" -> tx.amount
                    "EXPENSE" -> -tx.amount
                    else -> 0.0
                }
            }
            val days = txs.map { dayEnd(it.date) }.distinct().sorted()
            val bals = days.map { computeAccountsBalanceAtTime(accounts, transactions, it) }
            val startBal = computeAccountsBalanceAtTime(accounts, transactions, bucketStart - 1)
            val all = listOf(startBal) + bals
            val label = if (weekMode) {
                "W" + weekIndexOf(cal.get(Calendar.DAY_OF_MONTH))
            } else {
                SimpleDateFormat("MMM", Locale.getDefault()).format(cal.time)
            }
            val rangeLabel = if (weekMode) {
                val sf = SimpleDateFormat("d", Locale.getDefault())
                val ef = SimpleDateFormat("d MMM", Locale.getDefault())
                "${sf.format(Date(bucketStart))}–${ef.format(Date(bucketEnd))}"
            } else {
                SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(cal.time)
            }
            TrendBucket(
                key = bucketStart, label = label, rangeLabel = rangeLabel, net = net,
                high = all.max(), low = all.min(), endBalance = bals.lastOrNull() ?: startBal,
                isCurrent = now in bucketStart..bucketEnd, hasFuture = txs.any { it.date > now }
            )
        }.filter { it.key + 1 >= nominalStart || weekMode && it.isCurrent }
            .filter { it.key >= nominalStart || it.isCurrent || it.hasFuture }
            .sortedBy { it.key }
    }

    fun defaultBucketKey(buckets: List<TrendBucket>, now: Long): Long? {
        if (buckets.isEmpty()) return null
        buckets.firstOrNull { it.isCurrent }?.let { return it.key }
        buckets.lastOrNull { it.key <= now }?.let { return it.key }
        return buckets.last().key
    }

    fun computeTimelineDayBounds(
        transactions: List<TransactionEntity>,
        now: Long = System.currentTimeMillis()
    ): Pair<Long, Long> {
        val startDay = if (transactions.isNotEmpty()) dayStart(transactions.minOf { it.date }) else dayStart(now)
        val endDay = dayEnd(if (transactions.isNotEmpty()) maxOf(now, transactions.maxOf { it.date }) else now)
        return Pair(startDay, endDay)
    }

    fun computeBalanceBreakdown(
        accounts: List<Account>,
        transactions: List<TransactionEntity>,
        allTransactions: List<TransactionEntity> = transactions,
        asOf: Long = Long.MAX_VALUE
    ): BalanceBreakdownSummary {
        val active = accounts.filter { !it.archived }
        val settledAll = allTransactions.filter { it.date <= asOf }
        val runningBalances = computeAccountBalances(active, settledAll).associate { it.account.id to it.currentBalance }
        val rows = active.map { acc ->
            var totalIn = 0.0
            var totalOut = 0.0
            for (tx in transactions) {
                when (tx.type.uppercase()) {
                    "INCOME" -> {
                        if (tx.toAccountId == acc.id) totalIn += tx.amount
                    }
                    "EXPENSE" -> {
                        if (tx.fromAccountId == acc.id) totalOut += tx.amount
                    }
                    "TRANSFER" -> {
                        if (tx.toAccountId == acc.id) totalIn += tx.amount
                        if (tx.fromAccountId == acc.id) totalOut += tx.amount
                    }
                }
            }
            val currentBalance = runningBalances[acc.id] ?: (acc.openingBalance + totalIn - totalOut)
            val openingAsOfStart = currentBalance - totalIn + totalOut
            val isCreditCard = acc.accountType.equals("CREDIT_CARD", ignoreCase = true)
            AccountBreakdownRow(
                account = acc,
                openingBalance = openingAsOfStart,
                totalIn = totalIn,
                totalOut = totalOut,
                currentBalance = currentBalance,
                isCreditCard = isCreditCard
            )
        }
        val nonCardRows = rows.filter { !it.isCreditCard }
        val liquidTotal = if (nonCardRows.isNotEmpty()) nonCardRows.sumOf { it.currentBalance } else 0.0
        val netWorthInclDebt = rows.sumOf { it.currentBalance }
        return BalanceBreakdownSummary(
            rows = rows,
            liquidTotal = liquidTotal,
            netWorthInclDebt = netWorthInclDebt,
            heroTotal = liquidTotal
        )
    }

    fun computeChartBars(
        accounts: List<Account>,
        transactions: List<TransactionEntity>,
        level: ChartLevel,
        scopeYears: Set<Int> = emptySet(),
        scopeMonths: Set<Long> = emptySet(),
        scopeWeeks: Set<Int> = emptySet(),
        now: Long = System.currentTimeMillis(),
        includeOpeningBalance: Boolean = true
    ): List<BarItem> {
        val nonCard = accounts.filter { it.accountType != "CREDIT_CARD" && !it.archived }
        val target = if (nonCard.isNotEmpty() && accounts.size > 1 && accounts.any { it.accountType == "CREDIT_CARD" }) nonCard else accounts
        val targetIds = target.map { it.id }.toSet()
        val bars = mutableListOf<BarItem>()
        val todayStart = dayStart(now)
        val currentYear = yearOf(now)
        val currentMonthStart = monthStart(now)
        fun balAt(t: Long): Double {
            if (includeOpeningBalance) {
                return computeAccountsBalanceAtTime(target, transactions, t, includeOpeningBalance = true)
            }
            // When filtered by Category / Type / Route (includeOpeningBalance = false),
            // compute cumulative net of the filtered slice within the active chart period window
            // so the Trend detail banner never leaks global opening balance or unrelated months.
            val windowTxs = when {
                scopeMonths.size == 1 -> {
                    val mk = scopeMonths.first()
                    val me = monthEnd(mk)
                    transactions.filter { it.date in mk..me }
                }
                level == ChartLevel.DAYS || level == ChartLevel.WEEKS -> {
                    val mStart = monthStart(t)
                    val mEnd = monthEnd(mStart)
                    val inMonth = transactions.filter { it.date in mStart..mEnd }
                    if (inMonth.isNotEmpty() || mStart == currentMonthStart) inMonth else transactions
                }
                else -> transactions
            }
            return computeAccountsBalanceAtTime(target, windowTxs, t, includeOpeningBalance = false)
        }
        fun flows(txs: List<TransactionEntity>): Triple<Double, Double, Double> {
            var inc = 0.0
            var exp = 0.0
            var tr = 0.0
            for (tx in txs) {
                when (tx.type.uppercase()) {
                    "INCOME" -> {
                        if (tx.toAccountId == null || tx.toAccountId in targetIds) {
                            inc += tx.amount
                        }
                    }
                    "EXPENSE" -> {
                        if (tx.fromAccountId == null || tx.fromAccountId in targetIds) {
                            exp += tx.amount
                        }
                    }
                    "TRANSFER" -> {
                        val fromIn = tx.fromAccountId != null && tx.fromAccountId in targetIds
                        val toIn = tx.toAccountId != null && tx.toAccountId in targetIds
                        if (fromIn && !toIn) {
                            exp += tx.amount
                        } else if (!fromIn && toIn) {
                            inc += tx.amount
                        }
                        if (fromIn || toIn) {
                            tr += tx.amount
                        }
                    }
                }
            }
            return Triple(inc, exp, tr)
        }
        val monFmt = SimpleDateFormat("MMM", Locale.getDefault())
        val yearFmt = SimpleDateFormat("yyyy", Locale.getDefault())
        val monYearFmt = SimpleDateFormat("MMM yyyy", Locale.getDefault())
        val dayNumFmt = SimpleDateFormat("d", Locale.getDefault())
        val dayWeekFmt = SimpleDateFormat("EEE", Locale.getDefault())
        val dayFullFmt = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
        val rangeFmt = SimpleDateFormat("d MMM", Locale.getDefault())

        when (level) {
            ChartLevel.YEARS -> {
                transactions.map { yearOf(it.date) }.distinct().sorted()
                    .filter { scopeYears.isEmpty() || scopeYears.contains(it) }
                    .forEach { yr ->
                        val yStart = Calendar.getInstance().apply {
                            set(Calendar.YEAR, yr); set(Calendar.MONTH, 0); set(Calendar.DAY_OF_MONTH, 1)
                            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        val yEnd = Calendar.getInstance().apply {
                            set(Calendar.YEAR, yr); set(Calendar.MONTH, 11); set(Calendar.DAY_OF_MONTH, 31)
                            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
                        }.timeInMillis
                        val yTxs = transactions.filter { it.date in yStart..yEnd }
                        if (yTxs.isEmpty()) return@forEach
                        val (inc, exp, tr) = flows(yTxs)
                        val endT = if (yr == currentYear) maxOf(now, yTxs.maxOf { it.date }) else yEnd
                        val isCur = yr == currentYear
                        bars.add(BarItem(
                            key = yStart, periodType = PeriodFilter.YEAR, timestamp = endT,
                            dateLabel = yr.toString(), subLabel = yr.toString(), fullRangeLabel = "Year $yr",
                            balance = balAt(endT), income = inc, expense = exp, transfer = tr, netChange = inc - exp,
                            isCurrent = isCur, isToday = isCur, yearValue = yr
                        ))
                    }
            }
            ChartLevel.MONTHS -> {
                transactions.map { monthStart(it.date) }.distinct().sorted()
                    .filter { mk -> scopeMonths.isEmpty() || scopeMonths.contains(mk) }
                    .filter { mk -> scopeYears.isEmpty() || scopeYears.contains(yearOf(mk)) }
                    .forEach { mk ->
                        val me = monthEnd(mk)
                        val mTxs = transactions.filter { it.date in mk..me }
                        if (mTxs.isEmpty()) return@forEach
                        val (inc, exp, tr) = flows(mTxs)
                        val isCur = mk == monthStart(now)
                        val endT = when {
                            isCur -> maxOf(now, mTxs.maxOf { it.date })
                            mk > todayStart -> mTxs.maxOf { it.date }
                            else -> me
                        }
                        bars.add(BarItem(
                            key = mk, periodType = PeriodFilter.MONTH, timestamp = endT,
                            dateLabel = monFmt.format(Date(mk)), subLabel = yearFmt.format(Date(mk)),
                            fullRangeLabel = monYearFmt.format(Date(mk)),
                            balance = balAt(endT), income = inc, expense = exp, transfer = tr, netChange = inc - exp,
                            isCurrent = isCur, isToday = isCur, yearValue = yearOf(mk), monthKey = mk
                        ))
                    }
            }
            ChartLevel.WEEKS -> {
                transactions.groupBy { tx -> monthStart(tx.date) to weekIndexOfDate(tx.date) }
                    .entries.sortedBy { it.key.first }
                    .forEach { (inst, txs) ->
                        val (mk, w) = inst
                        if (scopeMonths.isNotEmpty() && !scopeMonths.contains(mk)) return@forEach
                        if (scopeWeeks.isNotEmpty() && !scopeWeeks.contains(w)) return@forEach
                        if (scopeYears.isNotEmpty() && !scopeYears.contains(yearOf(mk))) return@forEach
                        val wStart = weekStartOf(mk, w)
                        val wEnd = weekEndOf(mk, w)
                        val (inc, exp, tr) = flows(txs)
                        val isCur = now in wStart..wEnd
                        val endT = if (wEnd <= now) wEnd else maxOf(now, txs.maxOf { it.date })
                        bars.add(BarItem(
                            key = wStart, periodType = PeriodFilter.WEEK, timestamp = endT,
                            dateLabel = "W$w", subLabel = monFmt.format(Date(mk)),
                            fullRangeLabel = "W$w • ${monYearFmt.format(Date(mk))} • ${rangeFmt.format(Date(wStart))}–${rangeFmt.format(Date(wEnd))}",
                            balance = balAt(endT), income = inc, expense = exp, transfer = tr, netChange = inc - exp,
                            isCurrent = isCur, isToday = isCur, yearValue = yearOf(mk), monthKey = mk, weekIndex = w
                        ))
                    }
            }
            ChartLevel.DAYS -> {
                if (scopeWeeks.isEmpty() && scopeMonths.isEmpty()) {
                    val yearTxs = if (scopeYears.isNotEmpty()) {
                        transactions.filter { yearOf(it.date) in scopeYears }
                    } else {
                        transactions
                    }
                    val (startDay, endDay) = computeTimelineDayBounds(yearTxs, now)
                    var currentDay = startDay
                    val cal = Calendar.getInstance()
                    while (currentDay <= endDay) {
                        val dStart = currentDay
                        val dEnd = dayEnd(dStart)
                        val dTxs = yearTxs.filter { it.date in dStart..dEnd }
                        val (inc, exp, tr) = flows(dTxs)
                        val isToday = dStart == todayStart
                        val dayBal = balAt(dEnd)
                        cal.timeInMillis = dStart
                        val mk = monthStart(dStart)
                        val w = weekIndexOfDate(dStart)
                        val yr = yearOf(dStart)
                        bars.add(
                            BarItem(
                                key = dStart, periodType = PeriodFilter.WEEK, timestamp = dEnd,
                                dateLabel = dayNumFmt.format(Date(dStart)), subLabel = dayWeekFmt.format(Date(dStart)),
                                fullRangeLabel = dayFullFmt.format(Date(dStart)),
                                balance = dayBal, income = inc, expense = exp, transfer = tr, netChange = inc - exp,
                                isCurrent = isToday, isToday = isToday, yearValue = yr, monthKey = mk, weekIndex = w
                            )
                        )
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                        currentDay = dayStart(cal.timeInMillis)
                    }
                } else {
                    val combos: List<Pair<Long, Int>> = if (scopeWeeks.isNotEmpty()) {
                        val months = if (scopeMonths.isNotEmpty()) scopeMonths.sorted()
                        else transactions.map { monthStart(it.date) }.distinct().sorted()
                            .filter { mk -> scopeYears.isEmpty() || scopeYears.contains(yearOf(mk)) }
                        months.flatMap { mk -> scopeWeeks.sorted().map { w -> mk to w } }
                            .filter { (mk, w) -> transactions.any { monthStart(it.date) == mk && weekIndexOfDate(it.date) == w } }
                    } else if (scopeMonths.isNotEmpty()) {
                        scopeMonths.sorted().flatMap { mk ->
                            val weeksWithTx = (1..5).filter { w -> transactions.any { monthStart(it.date) == mk && weekIndexOfDate(it.date) == w } }
                            if (weeksWithTx.isNotEmpty()) weeksWithTx.map { w -> mk to w }
                            else (1..4).map { w -> mk to w }
                        }
                    } else {
                        val curMk = monthStart(now)
                        val curW = weekIndexOfDate(now)
                        val hasCur = transactions.any { monthStart(it.date) == curMk && weekIndexOfDate(it.date) == curW }
                        if (hasCur) listOf(curMk to curW)
                        else transactions.map { monthStart(it.date) to weekIndexOfDate(it.date) }
                            .distinct().sortedBy { it.first }.lastOrNull()?.let { listOf(it) } ?: listOf(curMk to curW)
                    }
                    combos.forEach { (mk, w) ->
                        val wStart = weekStartOf(mk, w)
                        val wEnd = weekEndOf(mk, w)
                        val wTxs = transactions.filter { it.date in wStart..wEnd }
                        if (wTxs.isEmpty()) {
                            val isCurWeek = now in wStart..wEnd
                            val dStart = if (isCurWeek) todayStart else wStart
                            val dEnd = if (isCurWeek) dayEnd(dStart) else dayEnd(wStart)
                            val dayBal = balAt(dEnd)
                            bars.add(
                                BarItem(
                                    key = dStart, periodType = PeriodFilter.WEEK, timestamp = dEnd,
                                    dateLabel = dayNumFmt.format(Date(dStart)), subLabel = dayWeekFmt.format(Date(dStart)),
                                    fullRangeLabel = dayFullFmt.format(Date(dStart)),
                                    balance = dayBal, income = 0.0, expense = 0.0, transfer = 0.0, netChange = 0.0,
                                    isCurrent = isCurWeek, isToday = isCurWeek, yearValue = yearOf(mk), monthKey = mk, weekIndex = w
                                )
                            )
                        } else {
                            val firstTxDay = wTxs.minOf { dayStart(it.date) }
                            val lastTxDay = wTxs.maxOf { dayStart(it.date) }
                            val startDay = firstTxDay
                            val endDay = minOf(dayStart(wEnd), maxOf(todayStart, lastTxDay))

                            var currentDay = startDay
                            while (currentDay <= endDay) {
                                val dStart = currentDay
                                val dEnd = dayEnd(dStart)
                                val dTxs = transactions.filter { it.date in dStart..dEnd }
                                val (inc, exp, tr) = flows(dTxs)
                                val isToday = dStart == todayStart
                                val dayBal = balAt(dEnd)
                                bars.add(
                                    BarItem(
                                        key = dStart, periodType = PeriodFilter.WEEK, timestamp = dEnd,
                                        dateLabel = dayNumFmt.format(Date(dStart)), subLabel = dayWeekFmt.format(Date(dStart)),
                                        fullRangeLabel = dayFullFmt.format(Date(dStart)),
                                        balance = dayBal, income = inc, expense = exp, transfer = tr, netChange = inc - exp,
                                        isCurrent = isToday, isToday = isToday, yearValue = yearOf(mk), monthKey = mk, weekIndex = w
                                    )
                                )
                                currentDay += 86400000L
                            }
                        }
                    }
                }
            }
        }
        return bars.sortedBy { it.key }
    }

    fun computeTrendPoints(
        accounts: List<Account>,
        transactions: List<TransactionEntity>,
        enrichedTx: List<EnrichedTransaction>,
        timeRange: TimeRange,
        currentBalance: Double,
        selectedBucketKey: Long? = null,
        now: Long = System.currentTimeMillis(),
        includeOpeningBalance: Boolean = true
    ): List<TrendPoint> {
        val nonCard = accounts.filter { it.accountType != "CREDIT_CARD" && !it.archived }
        val targetAccounts = if (nonCard.isNotEmpty() && accounts.size > 1 && accounts.any { it.accountType == "CREDIT_CARD" }) nonCard else accounts
        val dayNum = SimpleDateFormat("d", Locale.getDefault())
        val monShort = SimpleDateFormat("MMM", Locale.getDefault())
        val yearFmt = SimpleDateFormat("yyyy", Locale.getDefault())
        if (transactions.isEmpty()) {
            val totalOpening = if (includeOpeningBalance) targetAccounts.sumOf { it.openingBalance } else 0.0
            val dStart = dayStart(now)
            return listOf(
                TrendPoint(timestamp = dStart, dateLabel = dayNum.format(Date(dStart)), subLabel = monShort.format(Date(dStart)), periodLabel = "Start", balance = totalOpening, isMilestone = false, hasActivity = false),
                TrendPoint(timestamp = now, dateLabel = dayNum.format(Date(now)), subLabel = monShort.format(Date(now)), periodLabel = "Today", balance = totalOpening, isMilestone = true, hasActivity = true, isToday = true)
            )
        }
        val todayStart = dayStart(now)
        fun balAt(t: Long) = computeAccountsBalanceAtTime(targetAccounts, transactions, t, includeOpeningBalance = includeOpeningBalance)
        val raw = mutableListOf<TrendPoint>()
        if (timeRange == TimeRange.RANGE_WEEKS || timeRange == TimeRange.RANGE_1M) {
            val windowStart = if (timeRange == TimeRange.RANGE_1M) now - 30L * 86400000L else Long.MIN_VALUE
            val activityDays = transactions.map { dayStart(it.date) }.distinct().sorted().filter { it >= windowStart }
            if (activityDays.isEmpty()) return emptyList()
            val firstDay = activityDays.first()
            raw.add(TrendPoint(timestamp = firstDay, dateLabel = dayNum.format(Date(firstDay)), subLabel = monShort.format(Date(firstDay)), periodLabel = "Start", balance = balAt(firstDay - 1), isMilestone = false, hasActivity = false))
            for (day in activityDays) {
                val dEnd = dayEnd(day)
                raw.add(TrendPoint(timestamp = dEnd, dateLabel = dayNum.format(Date(day)), subLabel = monShort.format(Date(day)), periodLabel = "", balance = balAt(dEnd), isMilestone = false, isProjected = day > todayStart, hasActivity = true, isToday = day == todayStart))
            }
        } else {
            val windowStart = when (timeRange) {
                TimeRange.RANGE_3M -> now - 90L * 86400000L
                TimeRange.RANGE_6M -> now - 180L * 86400000L
                TimeRange.RANGE_1Y -> now - 365L * 86400000L
                else -> Long.MIN_VALUE
            }
            val months = transactions.map { monthStart(it.date) }.distinct().sorted().filter { it >= windowStart }
            if (months.isEmpty()) return emptyList()
            val filteredTxs = transactions.filter { it.date >= windowStart }
            if (filteredTxs.isEmpty()) return emptyList()
            val earliestTxDate = filteredTxs.minOf { it.date }
            val startOriginTime = dayStart(earliestTxDate)
            val firstMonth = months.first()
            raw.add(TrendPoint(timestamp = startOriginTime, dateLabel = monShort.format(Date(startOriginTime)), subLabel = yearFmt.format(Date(startOriginTime)), periodLabel = "Start", balance = balAt(startOriginTime - 1), isMilestone = true, hasActivity = false))
            for (m in months) {
                val lastTx = transactions.filter { monthStart(it.date) == m }.maxOf { it.date }
                val mIsCurrent = m == monthStart(now)
                val t = when {
                    mIsCurrent -> maxOf(now, lastTx)
                    m > todayStart -> lastTx
                    else -> monthEnd(m)
                }
                raw.add(TrendPoint(timestamp = t, dateLabel = monShort.format(Date(m)), subLabel = yearFmt.format(Date(m)), periodLabel = monShort.format(Date(m)), balance = balAt(t), isMilestone = true, isProjected = t > now, hasActivity = true, isToday = t == now))
            }
        }
        val sorted = raw.sortedBy { it.timestamp }.distinctBy { it.timestamp }
        return sorted.mapIndexed { i, pt ->
            val prev = if (i > 0) sorted[i - 1].balance else pt.balance
            pt.copy(changeFromPrevious = pt.balance - prev)
        }
    }

    fun computeTrendSummary(
        points: List<TrendPoint>,
        timeRange: TimeRange,
        now: Long = System.currentTimeMillis()
    ): TrendSummary {
        if (points.isEmpty()) return TrendSummary()
        val startBal = points.first().balance
        val endBal = points.last().balance
        val delta = endBal - startBal
        val pct = if (kotlin.math.abs(startBal) > 0.001) (delta / kotlin.math.abs(startBal)) * 100.0
        else if (kotlin.math.abs(delta) > 0.001) 100.0 else 0.0
        val high = points.maxOf { it.balance }
        val low = points.minOf { it.balance }
        val startCal = Calendar.getInstance().apply { timeInMillis = points.first().timestamp }
        val endCal = Calendar.getInstance().apply { timeInMillis = points.last().timestamp }
        val startMonth = SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(startCal.time)
        val endMonth = SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(endCal.time)
        val mName = if (startMonth == endMonth) SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(endCal.time) else "$startMonth - $endMonth"
        val isSingleDay = startCal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR) && startCal.get(Calendar.DAY_OF_YEAR) == endCal.get(Calendar.DAY_OF_YEAR)
        val desc = when {
            isSingleDay -> "Today's running balance"
            timeRange == TimeRange.RANGE_WEEKS -> "Weekly balance for $mName"
            else -> "Running balance ($mName)"
        }
        return TrendSummary(startBalance = startBal, endBalance = endBal, netChange = delta, changePercent = pct, highBalance = high, lowBalance = low, monthName = mName, timeRangeDescription = desc)
    }

    fun generateInsights(
        kpis: DashboardKpis,
        breakdown: List<CategoryBreakdownItem>,
        filteredTx: List<EnrichedTransaction>,
        monthLabel: String,
        hideBalances: Boolean = false,
        currencySymbol: String = Formatters.currentCurrencySymbol(),
        hasCategoryFilter: Boolean = false
    ): List<String> {
        val bullets = mutableListOf<String>()
        if (kpis.totalOwed > 0 && !hasCategoryFilter) {
            val owedStr = Formatters.formatMoney(kpis.totalOwed, isMasked = hideBalances, currencySymbol = currencySymbol, includeDecimals = false)
            val addedStr = Formatters.formatMoney(kpis.ccSpendThisMonth, isMasked = hideBalances, currencySymbol = currencySymbol, includeDecimals = false)
            val cardCount = if (kpis.cardCount > 0) kpis.cardCount else 1
            bullets.add("You owe $owedStr across $cardCount card${if (cardCount == 1) "" else "s"}; $addedStr added this month.")
        }
        val topCategory = breakdown.firstOrNull { it.name != "Uncategorized" } ?: breakdown.firstOrNull()
        if (kpis.spendDeltaPercent != 0.0) {
            val dir = if (kpis.spendDeltaPercent > 0) "up" else "down"
            val absPct = String.format(Locale.getDefault(), "%.0f%%", kotlin.math.abs(kpis.spendDeltaPercent))
            val driverText = if (topCategory != null) ", largely driven by ${topCategory.name}" else ""
            bullets.add("Spend is $dir $absPct vs last month$driverText.")
        }
        if (topCategory != null && topCategory.percentage > 0) {
            val pctStr = String.format(Locale.getDefault(), "%.0f%%", topCategory.percentage)
            bullets.add("${topCategory.name} dominates at $pctStr of total monthly outflow.")
        }
        val largestExpense = filteredTx.filter { it.type.equals("EXPENSE", ignoreCase = true) }.maxByOrNull { it.amount }
        if (largestExpense != null && bullets.size < 3) {
            val amountStr = Formatters.formatMoney(largestExpense.amount, isMasked = hideBalances, currencySymbol = currencySymbol, includeDecimals = false)
            val desc = largestExpense.description.ifBlank { "Unlabeled expense" }
            bullets.add("Highest single expense: $desc ($amountStr).")
        }
        if (bullets.isEmpty()) bullets.add("Ready for new transactions in $monthLabel. Add expenses to generate insights.")
        return bullets.take(3)
    }

    /**
     * Builds the mandatory lead insight bullet when exactly one category is active (F19f):
     * "<Cat>: <all-time total> total · This cycle <X> · Prior cycle <Y>"
     */
    fun buildSingleCategoryLeadBullet(
        categoryName: String,
        allTimeTotal: Double,
        thisCycleTotal: Double,
        priorCycleTotal: Double,
        hideBalances: Boolean = false,
        currencySymbol: String = Formatters.currentCurrencySymbol()
    ): String {
        val allStr = Formatters.formatMoney(allTimeTotal, isMasked = hideBalances, currencySymbol = currencySymbol, includeDecimals = false)
        val thisStr = Formatters.formatMoney(thisCycleTotal, isMasked = hideBalances, currencySymbol = currencySymbol, includeDecimals = false)
        val priorStr = Formatters.formatMoney(priorCycleTotal, isMasked = hideBalances, currencySymbol = currencySymbol, includeDecimals = false)
        return "$categoryName: $allStr total · This cycle $thisStr · Prior cycle $priorStr"
    }

    fun sortSources(accounts: List<AccountWithBalance>): List<AccountWithBalance> {
        val yourMoney = accounts.filter { it.account.accountType != "CREDIT_CARD" }
            .sortedWith(
                compareByDescending<AccountWithBalance> { it.currentBalance }
                    .thenBy { it.account.name.lowercase(Locale.getDefault()) }
            )
        val creditCards = accounts.filter { it.account.accountType == "CREDIT_CARD" }
            .sortedWith(
                compareByDescending<AccountWithBalance> {
                    if (it.currentBalance < 0) -it.currentBalance else 0.0
                }.thenBy { it.account.name.lowercase(Locale.getDefault()) }
            )
        return yourMoney + creditCards
    }

    fun formatBudgetDisplayTitle(budgetName: String, targetName: String): String {
        val bTrim = budgetName.trim()
        val tTrim = targetName.trim()
        return if (tTrim.isBlank() || tTrim.equals("All Spend", ignoreCase = true) || tTrim.equals(bTrim, ignoreCase = true)) {
            bTrim
        } else {
            "$bTrim ($tTrim)"
        }
    }

    fun computeIncomeCategoryBreakdown(
        incomeTransactions: List<TransactionEntity>,
        categories: List<Category>,
        accounts: List<Account>
    ): List<IncomeCategoryItem> {
        if (incomeTransactions.isEmpty()) return emptyList()
        val catMap = categories.associateBy { it.id }
        val accMap = accounts.associateBy { it.id }
        val totalIncome = incomeTransactions.sumOf { it.amount }

        return incomeTransactions.groupBy { it.categoryId }
            .map { (catId, txs) ->
                val cat = catId?.let { catMap[it] }
                val catName = cat?.name ?: "Uncategorized"
                val catColor = cat?.color ?: "#10B981"
                val amt = txs.sumOf { it.amount }
                val pct = if (totalIncome > 0) ((amt / totalIncome) * 100.0).toFloat() else 0f

                val topAccId = txs.groupBy { it.toAccountId }
                    .maxByOrNull { entry -> entry.value.sumOf { it.amount } }
                    ?.key
                val topAccName = topAccId?.let { accMap[it]?.name } ?: ""

                IncomeCategoryItem(
                    categoryId = catId ?: -1L,
                    categoryName = catName,
                    amount = amt,
                    percentage = pct,
                    topLandingAccount = topAccName,
                    colorHex = catColor
                )
            }
            .sortedByDescending { it.amount }
    }

    fun computeTransferRoutes(
        transferTransactions: List<TransactionEntity>,
        accounts: List<Account>
    ): List<TransferRouteItem> {
        if (transferTransactions.isEmpty()) return emptyList()
        val accMap = accounts.associateBy { it.id }

        return transferTransactions.groupBy { Pair(it.fromAccountId, it.toAccountId) }
            .map { (pair, txs) ->
                val fromName = pair.first?.let { accMap[it]?.name } ?: "Unknown"
                val toName = pair.second?.let { accMap[it]?.name } ?: "Unknown"
                val routeName = "$fromName → $toName"
                val totalAmt = txs.sumOf { it.amount }
                TransferRouteItem(
                    fromAccountId = pair.first,
                    toAccountId = pair.second,
                    routeName = routeName,
                    amount = totalAmt,
                    count = txs.size
                )
            }
            .sortedByDescending { it.amount }
    }

    fun computeCardPayments(
        transactions: List<TransactionEntity>,
        accounts: List<Account>
    ): Double {
        val cardIds = accounts.filter { it.accountType == "CREDIT_CARD" }.map { it.id }.toSet()
        if (cardIds.isEmpty()) return 0.0
        return transactions.filter { tx ->
            tx.type.equals("TRANSFER", ignoreCase = true) &&
            tx.toAccountId != null && tx.toAccountId in cardIds
        }.sumOf { it.amount }
    }

    fun generateIncomeInsights(
        thisMonthCredits: Double,
        lastMonthCredits: Double,
        creditsDeltaPercent: Double,
        breakdown: List<IncomeCategoryItem>,
        incomeTxs: List<TransactionEntity>,
        monthLabel: String,
        hideBalances: Boolean = false,
        currencySymbol: String = Formatters.currentCurrencySymbol()
    ): List<String> {
        val bullets = mutableListOf<String>()
        if (creditsDeltaPercent != 0.0) {
            val dir = if (creditsDeltaPercent > 0) "up" else "down"
            val absPct = String.format(Locale.getDefault(), "%.0f%%", kotlin.math.abs(creditsDeltaPercent))
            bullets.add("Credits $dir $absPct vs last month.")
        } else if (thisMonthCredits > 0 && lastMonthCredits == 0.0) {
            bullets.add("Credits recorded for the first time this cycle.")
        }
        val topCat = breakdown.firstOrNull()
        if (topCat != null && topCat.percentage > 0) {
            val pctStr = String.format(Locale.getDefault(), "%.0f%%", topCat.percentage)
            val topAccText = if (topCat.topLandingAccount.isNotBlank()) " (mainly landing in ${topCat.topLandingAccount})" else ""
            bullets.add("${topCat.categoryName} is the largest source at $pctStr of total income$topAccText.")
        }
        val largestCredit = incomeTxs.maxByOrNull { it.amount }
        if (largestCredit != null) {
            val amountStr = Formatters.formatMoney(largestCredit.amount, isMasked = hideBalances, currencySymbol = currencySymbol, includeDecimals = false)
            val desc = largestCredit.description.ifBlank { "Income" }
            val lcCal = Calendar.getInstance().apply { timeInMillis = largestCredit.date }
            val dateStr = "${lcCal.get(Calendar.DAY_OF_MONTH)} ${Formatters.getMonthName(lcCal.get(Calendar.MONTH) + 1)}"
            bullets.add("Largest single credit: $desc ($amountStr on $dateStr).")
        }
        if (incomeTxs.isNotEmpty()) {
            val count = incomeTxs.size
            val avg = incomeTxs.sumOf { it.amount } / count.toDouble()
            val avgStr = Formatters.formatMoney(avg, isMasked = hideBalances, currencySymbol = currencySymbol, includeDecimals = false)
            bullets.add("$count income ${if (count == 1) "entry" else "entries"} averaging $avgStr.")
        }
        if (bullets.isEmpty()) bullets.add("Ready for new income in $monthLabel. Add credits to generate insights.")
        return bullets.take(4)
    }

    fun generateTransferInsights(
        movedOut: Double,
        totalMoveCount: Int,
        cardPayments: Double,
        routes: List<TransferRouteItem>,
        transferTxs: List<TransactionEntity>,
        monthLabel: String,
        hideBalances: Boolean = false,
        currencySymbol: String = Formatters.currentCurrencySymbol()
    ): List<String> {
        val bullets = mutableListOf<String>()
        val topRoute = routes.firstOrNull()
        if (topRoute != null) {
            val amtStr = Formatters.formatMoney(topRoute.amount, isMasked = hideBalances, currencySymbol = currencySymbol, includeDecimals = false)
            bullets.add("Top route: ${topRoute.routeName} with $amtStr moved across ${topRoute.count} transfer${if (topRoute.count == 1) "" else "s"}.")
        }
        if (cardPayments > 0) {
            val cpStr = Formatters.formatMoney(cardPayments, isMasked = hideBalances, currencySymbol = currencySymbol, includeDecimals = false)
            bullets.add("Card payments: $cpStr paid towards credit cards this month.")
        }
        val largestMove = transferTxs.maxByOrNull { it.amount }
        if (largestMove != null && bullets.size < 3) {
            val amountStr = Formatters.formatMoney(largestMove.amount, isMasked = hideBalances, currencySymbol = currencySymbol, includeDecimals = false)
            val desc = largestMove.description.ifBlank { "Transfer" }
            bullets.add("Largest single move: $desc ($amountStr).")
        }
        if (bullets.size < 3 && totalMoveCount > 0) {
            val totalMovedStr = Formatters.formatMoney(movedOut, isMasked = hideBalances, currencySymbol = currencySymbol, includeDecimals = false)
            bullets.add("Total moved: $totalMovedStr across $totalMoveCount transfers.")
        }
        if (bullets.isEmpty()) bullets.add("No transfers recorded in $monthLabel.")
        return bullets.take(3)
    }
}

data class IncomeCategoryItem(
    val categoryId: Long? = null,
    val categoryName: String,
    val amount: Double,
    val percentage: Float,
    val topLandingAccount: String,
    val colorHex: String
)

data class TransferRouteItem(
    val fromAccountId: Long? = null,
    val toAccountId: Long? = null,
    val routeName: String,
    val amount: Double,
    val count: Int
)