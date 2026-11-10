package com.example.ui.dashboard

import androidx.compose.foundation.ScrollState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.datastore.AppSettingsRepository
import com.example.data.model.Account
import com.example.data.model.BudgetEntity
import com.example.data.model.Category
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.domain.budget.BudgetCalculator
import com.example.domain.budget.CalculatedBudget
import com.example.domain.calculator.LedgerCalculator
import com.example.domain.model.AccountWithBalance
import com.example.domain.model.BarItem
import com.example.domain.model.CategoryBreakdownItem
import com.example.domain.model.ChartLevel
import com.example.domain.model.DashboardKpis
import com.example.domain.model.EnrichedTransaction
import com.example.domain.model.FilterHandoffPatch
import com.example.domain.model.FilterState
import com.example.domain.model.MonthlyIncomeSpend
import com.example.domain.model.SharedFilterStateHolder
import com.example.domain.model.TimeRange
import com.example.domain.model.TrendBucket
import com.example.domain.model.TrendPoint
import com.example.domain.model.TrendSummary
import com.example.domain.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DashboardUiState(
    val filter: FilterState = FilterState(),
    val timeRange: TimeRange = TimeRange.RANGE_WEEKS,
    val chartLevel: ChartLevel = ChartLevel.DAYS,
    val scopeYears: Set<Int> = emptySet(),
    val scopeMonths: Set<Long> = emptySet(),
    val scopeWeeks: Set<Int> = emptySet(),
    val yearOptions: List<Int> = emptyList(),
    val monthOptions: List<Pair<Long, String>> = emptyList(),
    val weekOptions: List<Int> = emptyList(),
    val unionHint: Boolean = false,
    val breadcrumb: List<Pair<String, ChartLevel>> = emptyList(),
    val chartBars: List<BarItem> = emptyList(),
    val selectedBarKey: Long? = null,
    val kpis: DashboardKpis = DashboardKpis(),
    val trendPoints: List<TrendPoint> = emptyList(),
    val trendSummary: TrendSummary = TrendSummary(),
    val categoryBreakdown: List<CategoryBreakdownItem> = emptyList(),
    val totalExpenseInPeriod: Double = 0.0,
    val totalIncomeInPeriod: Double = 0.0,
    val totalTransferInPeriod: Double = 0.0,
    val totalExpenseInScope: Double = 0.0,
    val totalCreditsInScope: Double = 0.0,
    val totalTransfersInScope: Double = 0.0,
    val totalScopeSubLabel: String = "",
    val monthlyComparison: List<MonthlyIncomeSpend> = emptyList(),
    val accountsWithBalance: List<AccountWithBalance> = emptyList(),
    val insights: List<String> = emptyList(),
    val activeAccounts: List<Account> = emptyList(),
    val activeCategories: List<Category> = emptyList(),
    val availableMonths: List<Pair<Int, Int>> = emptyList(),
    val monthYearLabel: String = "",
    val hideBalances: Boolean = false,
    val currencySymbol: String = "₹",
    val trendBuckets: List<TrendBucket> = emptyList(),
    val selectedBucketKey: Long? = null,
    val allAccounts: List<Account> = emptyList(),
    val allTransactions: List<TransactionEntity> = emptyList(),
    val topCategoryId: Long? = null,
    val largestExpenseDate: Long? = null,
    val isLoading: Boolean = false,
    val calculatedBudgets: List<CalculatedBudget> = emptyList(),
    val budgetsCardVisible: Boolean = true,
    val activeBudgets: List<BudgetEntity> = emptyList(),
    val tightestInsightBudget: CalculatedBudget? = null,
    val activeTypeMode: String = "EXPENSE",
    val activeInsightsMode: String = "EXPENSE",
    val isBudgetsCardVisibleForMode: Boolean = true,
    val thisMonthCredits: Double = 0.0,
    val lastMonthCredits: Double = 0.0,
    val creditsDeltaPercent: Double = 0.0,
    val cardPaymentsThisMonth: Double = 0.0,
    val transfersThisMonth: Double = 0.0,
    val transfersLastMonth: Double = 0.0,
    val totalMoveCountThisMonth: Int = 0,
    val totalMoveCountLastMonth: Int = 0,
    val movedOutThisMonth: Double = 0.0,
    val movedInThisMonth: Double = 0.0,
    val incomeCategoryBreakdown: List<com.example.domain.calculator.IncomeCategoryItem> = emptyList(),
    val transferRoutes: List<com.example.domain.calculator.TransferRouteItem> = emptyList(),
    val incomeInsights: List<String> = emptyList(),
    val transferInsights: List<String> = emptyList()
)

class DashboardViewModel(
    private val repository: FinanceRepository,
    private val settingsRepository: AppSettingsRepository,
    val sharedFilterStateHolder: SharedFilterStateHolder = SharedFilterStateHolder()
) : ViewModel() {

    // Hoisted vertical scroll state to survive tab navigation and sub-screen handoffs
    val scrollState = ScrollState(initial = 0)
    val lazyListState = androidx.compose.foundation.lazy.LazyListState()

    private val _filter: StateFlow<FilterState> = sharedFilterStateHolder.manualFilter
    val filter: StateFlow<FilterState> = sharedFilterStateHolder.manualFilter

    init {
        sharedFilterStateHolder.registerOnManualChangeListener { manual ->
            _activeTypeMode.value = when (manual.selectedType.uppercase()) {
                "INCOME" -> "INCOME"
                "TRANSFER" -> "TRANSFER"
                else -> "EXPENSE"
            }
        }
        sharedFilterStateHolder.registerOnClearListener {
            _activeTypeMode.value = "EXPENSE"
            clearChartScope()
        }
    }

    private val _timeRange = MutableStateFlow(TimeRange.RANGE_WEEKS)
    val timeRange: StateFlow<TimeRange> = _timeRange

    private data class ChartScope(
        val years: Set<Int> = emptySet(),
        val months: Set<Long> = emptySet(),
        val weeks: Set<Int> = emptySet(),
        val level: ChartLevel = ChartLevel.DAYS
    )
    
    private val _scope = MutableStateFlow(ChartScope())

    private val _selectedBarKey = MutableStateFlow<Long?>(null)
    val selectedBarKey: StateFlow<Long?> = _selectedBarKey

    private val _selectedBucketKey = MutableStateFlow<Long?>(null)
    val selectedBucketKey: StateFlow<Long?> = _selectedBucketKey

    private val _activeTypeMode = MutableStateFlow("EXPENSE")
    val activeTypeMode: StateFlow<String> = _activeTypeMode

    private val settingsWithCurrencyFlow = combine(
        settingsRepository.settingsFlow,
        settingsRepository.currencySymbol
    ) { settings, symbol ->
        val resolvedSymbol = symbol.ifBlank { settings.currencySymbol.ifBlank { "₹" } }
        settings.copy(currencySymbol = resolvedSymbol)
    }

    private val coreDataFlow = combine(
        repository.allTransactions.catch { },
        repository.allAccounts.catch { },
        repository.allCategories.catch { },
        repository.allBudgets.catch { },
        settingsWithCurrencyFlow.catch { }
    ) { transactions, accounts, categories, budgets, settings ->
        Formatters.updateCurrentCurrencySymbol(settings.currencySymbol)
        val enriched = LedgerCalculator.computeRunningBalances(transactions, accounts, categories)
        val kpis = LedgerCalculator.computeDashboardKpis(
            accounts = accounts,
            transactions = transactions,
            monthStartDay = settings.monthStartDay
        )
        val accountsBal = LedgerCalculator.computeAccountBalances(accounts, transactions)
        val sixMonths = LedgerCalculator.computeLast6MonthsComparison(transactions)
        val availableMonths = LedgerCalculator.computeAvailableMonths(transactions)

        val activeBudgets = budgets.filter { it.active }
        val calculatedBudgets = BudgetCalculator.computeBudgets(
            budgets = activeBudgets,
            transactions = transactions,
            accounts = accounts,
            categories = categories,
            monthStartDay = settings.monthStartDay
        )
        val tightestInsightBudget = BudgetCalculator.getTightestInsightBudget(calculatedBudgets)

        object {
            val transactions = transactions
            val accounts = accounts
            val categories = categories
            val settings = settings
            val enriched = enriched
            val kpis = kpis
            val accountsBal = accountsBal
            val sixMonths = sixMonths
            val availableMonths = availableMonths
            val activeBudgets = activeBudgets
            val calculatedBudgets = calculatedBudgets
            val tightestInsightBudget = tightestInsightBudget
        }
    }.catch { }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<DashboardUiState> = combine(
        _filter,
        _scope,
        _selectedBarKey,
        _activeTypeMode,
        coreDataFlow
    ) { filterState, scope, selBarKey, activeMode, core ->
        val cal = Calendar.getInstance()
        val monthYearSdf = SimpleDateFormat("MMM yyyy", Locale.getDefault())
        val monShortFmt = SimpleDateFormat("MMM", Locale.getDefault())
        val scopeMonthsAsInt = scope.months.map {
            cal.timeInMillis = it
            cal.get(Calendar.MONTH) + 1
        }.toSet()
        val scopeYearsAsInt = scope.years.toSet()

        val effYears = if (filterState.selectedYears.isNotEmpty()) filterState.selectedYears
            else if (filterState.selectedYear != null) setOf(filterState.selectedYear!!)
            else scopeYearsAsInt

        val effMonths = if (filterState.selectedMonths.isNotEmpty()) filterState.selectedMonths
            else if (filterState.selectedMonth != null) setOf(filterState.selectedMonth!!)
            else scopeMonthsAsInt

        // F24-SCOPE: ONE canonical scoped slice = filterTransactions respecting effYears, effMonths,
        // AND scope.weeks when active (matching monthYearLabel), NEVER clamped to thisCycle when Month: All.
        val canonicalScopedFilter = filterState.copy(
            selectedType = "ALL",
            selectedYear = null,
            selectedYears = effYears,
            selectedMonth = null,
            selectedMonths = effMonths
        )
        val scopedEnrichedTx = LedgerCalculator.filterTransactions(core.enriched, canonicalScopedFilter).let { list ->
            if (scope.weeks.isNotEmpty()) {
                list.filter { LedgerCalculator.weekIndexOfDate(it.date) in scope.weeks }
            } else {
                list
            }
        }
        val scopedTx = scopedEnrichedTx.map { it.entity }

        val filteredTx = if (filterState.selectedType.uppercase() != "ALL" && filterState.selectedType.isNotBlank()) {
            scopedEnrichedTx.filter { it.type.equals(filterState.selectedType, ignoreCase = true) }
        } else {
            scopedEnrichedTx
        }
        val breakdown = LedgerCalculator.computeCategoryBreakdown(scopedEnrichedTx, core.categories)
        val totalExpense = scopedTx.filter { it.type.equals("EXPENSE", ignoreCase = true) }.sumOf { it.amount }

        val sysNow = System.currentTimeMillis()
        cal.timeInMillis = sysNow
        val sysYear = cal.get(Calendar.YEAR)
        val sysMonth = cal.get(Calendar.MONTH) + 1
        val hasTxInSysMonth = core.transactions.any {
            cal.timeInMillis = it.date
            cal.get(Calendar.YEAR) == sysYear && cal.get(Calendar.MONTH) + 1 == sysMonth
        }

        val refTime = when {
            effYears.isNotEmpty() && effMonths.isNotEmpty() -> {
                val maxYear = effYears.max()
                val maxMonth = effMonths.max()
                cal.apply {
                    set(Calendar.YEAR, maxYear)
                    set(Calendar.MONTH, maxMonth - 1)
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
            }
            effYears.isNotEmpty() -> {
                val maxYear = effYears.max()
                val latestTxMonth = core.transactions.filter {
                    cal.timeInMillis = it.date
                    cal.get(Calendar.YEAR) == maxYear
                }.maxOfOrNull {
                    cal.timeInMillis = it.date
                    cal.get(Calendar.MONTH) + 1
                }
                val targetMonth = if (maxYear == sysYear && (core.transactions.isEmpty() || hasTxInSysMonth)) {
                    maxOf(sysMonth, latestTxMonth ?: 1)
                } else {
                    latestTxMonth ?: (if (maxYear == sysYear) sysMonth else 12)
                }
                cal.apply {
                    set(Calendar.YEAR, maxYear)
                    set(Calendar.MONTH, targetMonth - 1)
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
            }
            effMonths.isNotEmpty() -> {
                val maxMonth = effMonths.max()
                val targetYear = core.transactions.filter {
                    cal.timeInMillis = it.date
                    cal.get(Calendar.MONTH) + 1 == maxMonth
                }.maxOfOrNull {
                    cal.timeInMillis = it.date
                    cal.get(Calendar.YEAR)
                } ?: sysYear
                cal.apply {
                    set(Calendar.YEAR, targetYear)
                    set(Calendar.MONTH, maxMonth - 1)
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
            }
            else -> {
                val maxTxDate = core.transactions.maxOfOrNull { it.date }
                if (maxTxDate == null || hasTxInSysMonth) {
                    maxOf(sysNow, maxTxDate ?: 0L)
                } else {
                    cal.apply {
                        timeInMillis = maxTxDate
                        set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }.timeInMillis
                }
            }
        }

        val kpiAccounts = if (filterState.selectedAccountId != null) {
            core.accounts.filter { it.id == filterState.selectedAccountId }
        } else {
            core.accounts.filter { !it.archived }
        }

        val targetAccounts = if (filterState.selectedAccountId != null) {
            core.accounts.filter { it.id == filterState.selectedAccountId }
        } else {
            val nonCards = core.accounts.filter { it.accountType != "CREDIT_CARD" && !it.archived }
            if (nonCards.isNotEmpty() && core.accounts.any { it.accountType == "CREDIT_CARD" }) {
                nonCards
            } else {
                core.accounts.filter { !it.archived }
            }
        }

        // Transactions related to target accounts for full reconciliation
        val accountTxs = if (filterState.selectedAccountIds.isNotEmpty() || filterState.selectedAccountId != null) {
            val actIds = if (filterState.selectedAccountIds.isNotEmpty()) filterState.selectedAccountIds
            else setOf(filterState.selectedAccountId!!)
            core.transactions.filter { (it.fromAccountId != null && it.fromAccountId in actIds) || (it.toAccountId != null && it.toAccountId in actIds) }
        } else {
            core.transactions
        }

        val filteredKpiTx = core.transactions.filter { tx ->
            LedgerCalculator.matchesNonPeriodSlicers(
                tx = tx,
                filter = filterState,
                accounts = core.accounts,
                includeDateRange = true
            )
        }

        val defaultActiveYear = cal.apply { timeInMillis = refTime }.get(Calendar.YEAR)
        val activeYearsForTotal = if (effYears.isNotEmpty()) effYears else setOf(defaultActiveYear)
        val totalScopeTxs = if (effMonths.isNotEmpty()) {
            filteredKpiTx.filter { tx ->
                cal.timeInMillis = tx.date
                val y = cal.get(Calendar.YEAR)
                val m = cal.get(Calendar.MONTH) + 1
                y in activeYearsForTotal && m in effMonths
            }
        } else {
            filteredKpiTx.filter { tx ->
                cal.timeInMillis = tx.date
                cal.get(Calendar.YEAR) in activeYearsForTotal
            }
        }
        val totalExpenseInScope = totalScopeTxs.filter { it.type.equals("EXPENSE", ignoreCase = true) }.sumOf { it.amount }
        val totalCreditsInScope = totalScopeTxs.filter { it.type.equals("INCOME", ignoreCase = true) }.sumOf { it.amount }
        val totalTransfersInScope = totalScopeTxs.filter { it.type.equals("TRANSFER", ignoreCase = true) }.sumOf { it.amount }
        val yearScopeStr = if (activeYearsForTotal.size == 1) {
            activeYearsForTotal.first().toString()
        } else {
            activeYearsForTotal.sorted().joinToString(", ")
        }
        val totalScopeSubLabel = if (effMonths.isNotEmpty()) {
            if (effMonths.size == 1) {
                "${Formatters.getMonthName(effMonths.first())} $yearScopeStr"
            } else {
                "${effMonths.size} months · $yearScopeStr"
            }
        } else {
            "All months · $yearScopeStr"
        }

        val settledKpiTx = accountTxs.filter { it.date <= refTime }
        val kpiAccountBalances = LedgerCalculator.computeAccountBalances(kpiAccounts, settledKpiTx)
        val netCardDebt = kpiAccountBalances
            .filter { it.account.accountType.equals("CREDIT_CARD", ignoreCase = true) && !it.account.archived }
            .sumOf { -it.currentBalance }
        val rawFilteredKpis = LedgerCalculator.computeDashboardKpis(
            accounts = kpiAccounts,
            transactions = filteredKpiTx,
            monthStartDay = core.settings.monthStartDay,
            now = refTime,
            allAccountTransactions = accountTxs,
            effYears = effYears,
            effMonths = effMonths
        ).copy(totalOwed = netCardDebt)

        val filteredKpis = if (effMonths.size == 1) {
            val singleMonth = effMonths.first()
            val targetYear = if (effYears.isNotEmpty()) effYears.first() else {
                core.transactions.filter {
                    cal.timeInMillis = it.date
                    cal.get(Calendar.MONTH) + 1 == singleMonth
                }.maxOfOrNull {
                    cal.timeInMillis = it.date
                    cal.get(Calendar.YEAR)
                } ?: cal.apply { timeInMillis = refTime }.get(Calendar.YEAR)
            }
            val prevMonth = if (singleMonth == 1) 12 else singleMonth - 1
            val prevYear = if (singleMonth == 1) targetYear - 1 else targetYear

            val cardAccountIds = core.accounts.filter { it.accountType.equals("CREDIT_CARD", ignoreCase = true) && !it.archived }.map { it.id }.toSet()

            val matchYears = if (effYears.size > 1) effYears else setOf(targetYear)
            val curTxs = filteredKpiTx.filter { tx ->
                cal.timeInMillis = tx.date
                val y = cal.get(Calendar.YEAR)
                val m = cal.get(Calendar.MONTH) + 1
                y in matchYears && m == singleMonth
            }
            val priTxs = filteredKpiTx.filter { tx ->
                cal.timeInMillis = tx.date
                val y = cal.get(Calendar.YEAR)
                val m = cal.get(Calendar.MONTH) + 1
                y == prevYear && m == prevMonth
            }

            val curSpend = curTxs.filter { it.type.equals("EXPENSE", ignoreCase = true) }.sumOf { it.amount }
            val priSpend = priTxs.filter { it.type.equals("EXPENSE", ignoreCase = true) }.sumOf { it.amount }

            val curCardSpend = filteredKpiTx.filter { tx ->
                cal.timeInMillis = tx.date
                val y = cal.get(Calendar.YEAR)
                val m = cal.get(Calendar.MONTH) + 1
                tx.type.equals("EXPENSE", ignoreCase = true) &&
                tx.fromAccountId != null && tx.fromAccountId in cardAccountIds &&
                y in matchYears && m == singleMonth
            }.sumOf { it.amount }

            val priCardSpend = filteredKpiTx.filter { tx ->
                cal.timeInMillis = tx.date
                val y = cal.get(Calendar.YEAR)
                val m = cal.get(Calendar.MONTH) + 1
                tx.type.equals("EXPENSE", ignoreCase = true) &&
                tx.fromAccountId != null && tx.fromAccountId in cardAccountIds &&
                y == prevYear && m == prevMonth
            }.sumOf { it.amount }

            val deltaPct = if (priSpend > 0.0) ((curSpend - priSpend) / priSpend) * 100.0 else 0.0

            rawFilteredKpis.copy(
                thisMonthSpend = curSpend,
                lastMonthSpend = priSpend,
                totalExpense = curSpend,
                ccSpendThisMonth = curCardSpend,
                ccSpendLastMonth = priCardSpend,
                spendDeltaPercent = deltaPct
            )
        } else {
            rawFilteredKpis
        }

        val nowMs = if (core.transactions.isEmpty() || hasTxInSysMonth) {
            maxOf(sysNow, core.transactions.maxOfOrNull { it.date } ?: 0L)
        } else {
            maxOf(refTime, core.transactions.maxOfOrNull { it.date } ?: 0L)
        }
        val effectiveScopeYears = if (scope.years.isNotEmpty()) {
            scope.years
        } else if (effYears.isNotEmpty()) {
            effYears
        } else {
            emptySet()
        }

        val effectiveScopeMonths = if (scope.months.isNotEmpty()) {
            scope.months
        } else if (effMonths.isNotEmpty()) {
            val targetYears = if (effectiveScopeYears.isNotEmpty()) {
                effectiveScopeYears
            } else {
                core.transactions.map {
                    cal.timeInMillis = it.date
                    cal.get(Calendar.YEAR)
                }.distinct().ifEmpty { listOf(sysYear) }
            }
            targetYears.flatMap { y ->
                effMonths.map { m ->
                    cal.apply {
                        set(Calendar.YEAR, y)
                        set(Calendar.MONTH, m - 1)
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }
            }.toSet()
        } else {
            emptySet()
        }

        val isMultiMonthTrigger = effMonths.size >= 2
        val effectiveChartLevel = if (scope.years.isEmpty() && scope.months.isEmpty() && scope.weeks.isEmpty() && isMultiMonthTrigger) {
            ChartLevel.MONTHS
        } else {
            scope.level
        }

        val selectedTypeUpper = filterState.selectedType.uppercase().ifBlank { "ALL" }
        val chartFilteredTx = if (selectedTypeUpper == "ALL") {
            filteredKpiTx
        } else {
            filteredKpiTx.filter { it.type.equals(selectedTypeUpper, ignoreCase = true) }
        }
        val hasCategoryFilter = filterState.effectiveCategoryIds().isNotEmpty()
        val hasTransferRouteFilter = filterState.transferRouteFromAccountId != null ||
            filterState.transferRouteToAccountId != null ||
            !filterState.transferRouteLabel.isNullOrBlank()
        val hasNonAccountSlicer = hasCategoryFilter || selectedTypeUpper != "ALL" || hasTransferRouteFilter
        val includeOpeningInChart = !hasNonAccountSlicer

        val bars = LedgerCalculator.computeChartBars(
            targetAccounts,
            chartFilteredTx,
            effectiveChartLevel,
            effectiveScopeYears,
            effectiveScopeMonths,
            scope.weeks,
            nowMs,
            includeOpeningBalance = includeOpeningInChart
        )

        val years = filteredKpiTx.map {
            cal.timeInMillis = it.date
            cal.get(Calendar.YEAR)
        }.distinct().sortedDescending()

        val months = filteredKpiTx.map { tx ->
            cal.timeInMillis = tx.date
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val key = cal.timeInMillis
            val label = monthYearSdf.format(Date(key))
            key to label
        }.distinctBy { it.first }.sortedByDescending { it.first }

        val scopedYears = if (effYears.size >= 2) {
            years.filter { it in effYears }
        } else {
            years
        }

        val scopedMonths = if (effMonths.size >= 2) {
            months.filter { mPair ->
                cal.timeInMillis = mPair.first
                val mVal = cal.get(Calendar.MONTH) + 1
                val yVal = cal.get(Calendar.YEAR)
                (effMonths.isEmpty() || mVal in effMonths) && (effYears.isEmpty() || yVal in effYears)
            }
        } else {
            months
        }

        val weeks = (1..5).toList()

        val crumb = mutableListOf<Pair<String, ChartLevel>>()
        crumb.add("Years" to ChartLevel.YEARS)
        if (effectiveScopeYears.size == 1) crumb.add(effectiveScopeYears.first().toString() to ChartLevel.MONTHS)
        if (effectiveScopeMonths.size == 1) crumb.add(monShortFmt.format(Date(effectiveScopeMonths.first())) to ChartLevel.WEEKS)
        if (scope.weeks.size == 1) {
            crumb.add("W${scope.weeks.first()}" to if (effectiveScopeMonths.size == 1) ChartLevel.DAYS else ChartLevel.WEEKS)
        }

        val levelLabel = when (effectiveChartLevel) {
            ChartLevel.YEARS -> "Years"
            ChartLevel.MONTHS -> "Months"
            ChartLevel.WEEKS -> "Weeks"
            ChartLevel.DAYS -> "Days"
        }
        if (crumb.isEmpty() || crumb.last().second != effectiveChartLevel) {
            crumb.add(levelLabel to effectiveChartLevel)
        }

        val resolvedBarKey = if (selBarKey != null && bars.any { it.key == selBarKey }) {
            selBarKey
        } else if (isMultiMonthTrigger) {
            null
        } else {
            bars.firstOrNull { it.isCurrent }?.key ?: bars.lastOrNull()?.key
        }

        val buckets = LedgerCalculator.computeTrendBuckets(chartFilteredTx, targetAccounts, TimeRange.RANGE_WEEKS, nowMs)
        val resolvedKey = LedgerCalculator.defaultBucketKey(buckets, nowMs)
        val trends = LedgerCalculator.computeTrendPoints(
            accounts = targetAccounts,
            transactions = chartFilteredTx,
            enrichedTx = filteredTx,
            timeRange = TimeRange.RANGE_WEEKS,
            currentBalance = filteredKpis.currentBalance,
            selectedBucketKey = resolvedKey,
            includeOpeningBalance = includeOpeningInChart
        )
        val trendSummary = LedgerCalculator.computeTrendSummary(trends, TimeRange.RANGE_WEEKS)

        val allMonthly = LedgerCalculator.computeLast6MonthsComparison(filteredKpiTx, refTime)
        val monthlyComparison = when {
            effMonths.isNotEmpty() && effYears.isNotEmpty() -> {
                allMonthly.filter { it.month in effMonths && it.year in effYears }
            }
            effMonths.isNotEmpty() -> {
                allMonthly.filter { it.month in effMonths }
            }
            effYears.isNotEmpty() -> {
                allMonthly.filter { it.year in effYears }
            }
            else -> allMonthly
        }

        val mLabel = when {
            effMonths.isNotEmpty() && effYears.isNotEmpty() -> {
                if (effMonths.size == 1 && effYears.size == 1) {
                    Formatters.getMonthYearLabel(effMonths.first(), effYears.first())
                } else {
                    "${effMonths.size} months • ${effYears.sorted().joinToString(", ")}"
                }
            }
            effMonths.isNotEmpty() -> {
                if (effMonths.size == 1) Formatters.getMonthName(effMonths.first())
                else "${effMonths.size} months"
            }
            effYears.isNotEmpty() -> {
                if (effYears.size == 1) effYears.first().toString()
                else "${effYears.size} years"
            }
            else -> "All Time"
        }

        val insightsList = LedgerCalculator.generateInsights(
            kpis = filteredKpis,
            breakdown = breakdown,
            filteredTx = scopedEnrichedTx,
            monthLabel = mLabel,
            hideBalances = core.settings.hideBalances,
            currencySymbol = core.settings.currencySymbol,
            hasCategoryFilter = hasCategoryFilter
        )

        val periodBudgets = if (effMonths.isNotEmpty() || effYears.isNotEmpty()) {
            BudgetCalculator.computeBudgets(
                budgets = core.activeBudgets,
                transactions = core.transactions,
                accounts = core.accounts,
                categories = core.categories,
                monthStartDay = core.settings.monthStartDay,
                referenceTime = refTime,
                effYears = effYears,
                effMonths = effMonths
            )
        } else {
            core.calculatedBudgets
        }

        val effTypeMode = when (filterState.selectedType.uppercase()) {
            "INCOME" -> "INCOME"
            "TRANSFER" -> "TRANSFER"
            "EXPENSE" -> "EXPENSE"
            else -> activeMode.uppercase()
        }
        val isBudgetsVisibleForMode = effTypeMode != "INCOME" && effTypeMode != "TRANSFER"

        val effectiveTightestInsightBudget = BudgetCalculator.getTightestInsightBudget(periodBudgets)

        val finalInsights = insightsList.toMutableList()
        if (!hasCategoryFilter && core.settings.budgetsCardVisible && isBudgetsVisibleForMode && effectiveTightestInsightBudget != null) {
            val tb = effectiveTightestInsightBudget
            val target = if (tb.budget.targetName.isNotBlank()) tb.budget.targetName else "All Spend"
            val displayTitle = LedgerCalculator.formatBudgetDisplayTitle(tb.budget.name, target)
            val budgetLine = if (tb.isOverLimit) {
                val over = tb.spent - tb.limit
                val overStr = Formatters.formatMoney(over, isMasked = core.settings.hideBalances, currencySymbol = core.settings.currencySymbol)
                "Budget Alert: $displayTitle is over limit by $overStr."
            } else {
                val rem = tb.remaining
                val remStr = Formatters.formatMoney(rem, isMasked = core.settings.hideBalances, currencySymbol = core.settings.currencySymbol)
                val pct = String.format(Locale.US, "%.0f%%", tb.percentage)
                "Budget Status: $displayTitle has $remStr remaining ($pct spent)."
            }
            finalInsights.add(budgetLine)
        }

        val hasPeriodFilter = effMonths.isNotEmpty()
        val yearScopedKpiTx = if (effYears.isNotEmpty()) {
            filteredKpiTx.filter { tx ->
                cal.timeInMillis = tx.date
                cal.get(Calendar.YEAR) in effYears
            }
        } else {
            filteredKpiTx
        }
        val (thisCycleStart, thisCycleEnd) = if (hasPeriodFilter) {
            Pair(0L, Long.MAX_VALUE)
        } else {
            LedgerCalculator.getCycleRange(refTime, core.settings.monthStartDay)
        }
        val (priorCycleStart, priorCycleEnd) = if (hasPeriodFilter) {
            val earliestRefTime = when {
                effYears.isNotEmpty() && effMonths.isNotEmpty() -> {
                    cal.apply {
                        set(Calendar.YEAR, effYears.min())
                        set(Calendar.MONTH, effMonths.min() - 1)
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }
                effMonths.isNotEmpty() -> {
                    val minMonth = effMonths.min()
                    val targetYear = core.transactions.filter {
                        cal.timeInMillis = it.date
                        cal.get(Calendar.MONTH) + 1 == minMonth
                    }.minOfOrNull {
                        cal.timeInMillis = it.date
                        cal.get(Calendar.YEAR)
                    } ?: sysYear
                    cal.apply {
                        set(Calendar.YEAR, targetYear)
                        set(Calendar.MONTH, minMonth - 1)
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }
                else -> refTime
            }
            LedgerCalculator.getPreviousCycleRange(earliestRefTime, core.settings.monthStartDay)
        } else {
            LedgerCalculator.getPreviousCycleRange(refTime, core.settings.monthStartDay)
        }

        val periodCandidateTxs = if (hasPeriodFilter) {
            if (effMonths.size == 1) {
                val singleMonth = effMonths.first()
                val targetYear = if (effYears.isNotEmpty()) effYears.max() else {
                    filteredKpiTx.filter {
                        cal.timeInMillis = it.date
                        cal.get(Calendar.MONTH) + 1 == singleMonth
                    }.maxOfOrNull {
                        cal.timeInMillis = it.date
                        cal.get(Calendar.YEAR)
                    } ?: cal.apply { timeInMillis = refTime }.get(Calendar.YEAR)
                }
                val matchYears = if (effYears.size > 1) effYears else setOf(targetYear)
                filteredKpiTx.filter { tx ->
                    cal.timeInMillis = tx.date
                    val y = cal.get(Calendar.YEAR)
                    val m = cal.get(Calendar.MONTH) + 1
                    y in matchYears && m == singleMonth
                }
            } else {
                filteredKpiTx.filter { tx ->
                    cal.timeInMillis = tx.date
                    val y = cal.get(Calendar.YEAR)
                    val m = cal.get(Calendar.MONTH) + 1
                    (effYears.isEmpty() || y in effYears) && (effMonths.isEmpty() || m in effMonths)
                }
            }
        } else {
            yearScopedKpiTx.filter { it.date in thisCycleStart..thisCycleEnd }
        }

        val priorPeriodCandidateTxs = if (hasPeriodFilter && effMonths.size == 1) {
            val singleMonth = effMonths.first()
            val targetYear = if (effYears.isNotEmpty()) effYears.max() else {
                filteredKpiTx.filter {
                    cal.timeInMillis = it.date
                    cal.get(Calendar.MONTH) + 1 == singleMonth
                }.maxOfOrNull {
                    cal.timeInMillis = it.date
                    cal.get(Calendar.YEAR)
                } ?: cal.apply { timeInMillis = refTime }.get(Calendar.YEAR)
            }
            val prevMonth = if (singleMonth == 1) 12 else singleMonth - 1
            val prevYear = if (singleMonth == 1) targetYear - 1 else targetYear
            filteredKpiTx.filter { tx ->
                cal.timeInMillis = tx.date
                cal.get(Calendar.YEAR) == prevYear && (cal.get(Calendar.MONTH) + 1) == prevMonth
            }
        } else {
            yearScopedKpiTx.filter { it.date in priorCycleStart..priorCycleEnd }
        }

        val thisMonthCredits = periodCandidateTxs.filter { it.type.equals("INCOME", ignoreCase = true) }.sumOf { it.amount }
        val lastMonthCredits = priorPeriodCandidateTxs.filter { it.type.equals("INCOME", ignoreCase = true) }.sumOf { it.amount }
        val creditsDelta = thisMonthCredits - lastMonthCredits
        val creditsDeltaPct = if (lastMonthCredits > 0.0) {
            (creditsDelta / lastMonthCredits) * 100.0
        } else if (thisMonthCredits > 0.0) {
            100.0
        } else {
            0.0
        }

        val periodIncomeTxs = periodCandidateTxs.filter { it.type.equals("INCOME", ignoreCase = true) }
        val periodTransferTxs = periodCandidateTxs.filter { it.type.equals("TRANSFER", ignoreCase = true) }

        // F24-SCOPE: Breakdowns, header totals, and secondary insights inputs compute from canonical scopedTx
        val scopedIncomeTxs = scopedTx.filter { it.type.equals("INCOME", ignoreCase = true) }
        val scopedTransferTxs = scopedTx.filter { it.type.equals("TRANSFER", ignoreCase = true) }
        val totalIncomeInPeriod = scopedIncomeTxs.sumOf { it.amount }
        val totalTransferInPeriod = scopedTransferTxs.sumOf { it.amount }

        val incomeBreakdown = LedgerCalculator.computeIncomeCategoryBreakdown(
            scopedIncomeTxs,
            core.categories,
            core.accounts
        )

        val cardPayments = LedgerCalculator.computeCardPayments(periodTransferTxs, core.accounts)
        val priorPeriodTransferTxs = priorPeriodCandidateTxs.filter { it.type.equals("TRANSFER", ignoreCase = true) }
        val transfersThisMonth = periodTransferTxs.sumOf { it.amount }
        val transfersLastMonth = priorPeriodTransferTxs.sumOf { it.amount }
        val totalMoveCountThisMonth = periodTransferTxs.size
        val totalMoveCountLastMonth = priorPeriodTransferTxs.size
        val movedOut = transfersThisMonth
        val movedIn = transfersThisMonth
        val transferRoutes = LedgerCalculator.computeTransferRoutes(scopedTransferTxs, core.accounts)

        val rawIncomeInsights = LedgerCalculator.generateIncomeInsights(
            thisMonthCredits = thisMonthCredits,
            lastMonthCredits = lastMonthCredits,
            creditsDeltaPercent = creditsDeltaPct,
            breakdown = incomeBreakdown,
            incomeTxs = scopedIncomeTxs,
            monthLabel = mLabel,
            hideBalances = core.settings.hideBalances,
            currencySymbol = core.settings.currencySymbol
        )

        val scopedCardPayments = LedgerCalculator.computeCardPayments(scopedTransferTxs, core.accounts)
        val rawTransferInsights = LedgerCalculator.generateTransferInsights(
            movedOut = totalTransferInPeriod,
            totalMoveCount = scopedTransferTxs.size,
            cardPayments = scopedCardPayments,
            routes = transferRoutes,
            transferTxs = scopedTransferTxs,
            monthLabel = mLabel,
            hideBalances = core.settings.hideBalances,
            currencySymbol = core.settings.currencySymbol
        )

        val effectiveCatIds = filterState.effectiveCategoryIds()
        val singleCategoryLeadBullet: String? = if (effectiveCatIds.size == 1) {
            val singleCatId = effectiveCatIds.first()
            val singleCatName = if (singleCatId == -1L) {
                "Uncategorized"
            } else {
                core.categories.firstOrNull { it.id == singleCatId }?.name ?: "Category"
            }
            val rawTypeMatch: (TransactionEntity) -> Boolean = when (selectedTypeUpper) {
                "EXPENSE" -> { tx -> tx.type.equals("EXPENSE", ignoreCase = true) }
                "INCOME" -> { tx -> tx.type.equals("INCOME", ignoreCase = true) }
                "TRANSFER" -> { tx -> tx.type.equals("TRANSFER", ignoreCase = true) }
                else -> { tx -> tx.type.equals("EXPENSE", ignoreCase = true) || tx.type.equals("INCOME", ignoreCase = true) }
            }
            val typeMatch: (TransactionEntity) -> Boolean = if (filteredKpiTx.any(rawTypeMatch)) rawTypeMatch else { _ -> true }
            val allTimeCatTotal = filteredKpiTx.filter(typeMatch).sumOf { it.amount }
            val thisCycleCatTotal = periodCandidateTxs.filter(typeMatch).sumOf { it.amount }
            val priorCycleCatTotal = priorPeriodCandidateTxs.filter(typeMatch).sumOf { it.amount }
            LedgerCalculator.buildSingleCategoryLeadBullet(
                categoryName = singleCatName,
                allTimeTotal = allTimeCatTotal,
                thisCycleTotal = thisCycleCatTotal,
                priorCycleTotal = priorCycleCatTotal,
                hideBalances = core.settings.hideBalances,
                currencySymbol = core.settings.currencySymbol
            )
        } else null

        val incomeInsights = if (singleCategoryLeadBullet != null) {
            (listOf(singleCategoryLeadBullet) + rawIncomeInsights.filterNot { it == singleCategoryLeadBullet }).take(5)
        } else {
            rawIncomeInsights
        }

        val transferInsights = if (singleCategoryLeadBullet != null) {
            (listOf(singleCategoryLeadBullet) + rawTransferInsights.filterNot { it == singleCategoryLeadBullet }).take(5)
        } else {
            rawTransferInsights
        }

        // F24-TITLE (Issue 5): Insights card title/mode follows the bullet family actually rendered
        val activeInsightsMode = when (selectedTypeUpper) {
            "INCOME" -> "INCOME"
            "TRANSFER" -> "TRANSFER"
            "EXPENSE" -> "EXPENSE"
            else -> {
                if (breakdown.isEmpty() && incomeBreakdown.isNotEmpty()) {
                    "INCOME"
                } else {
                    "EXPENSE"
                }
            }
        }

        val baseEffectiveInsights = when (activeInsightsMode) {
            "INCOME" -> rawIncomeInsights
            "TRANSFER" -> rawTransferInsights
            else -> finalInsights
        }

        val effectiveInsights = if (singleCategoryLeadBullet != null) {
            (listOf(singleCategoryLeadBullet) + baseEffectiveInsights.filterNot { it == singleCategoryLeadBullet }).take(5)
        } else {
            baseEffectiveInsights
        }

        val settledAllTx = core.transactions.filter { it.date <= refTime }
        val periodAccountsBal = LedgerCalculator.sortSources(LedgerCalculator.computeAccountBalances(core.accounts, settledAllTx))

        val topCategory = breakdown.firstOrNull { it.name != "Uncategorized" } ?: breakdown.firstOrNull()
        val largestExpense = scopedEnrichedTx.filter { it.type.equals("EXPENSE", ignoreCase = true) }.maxByOrNull { it.amount }

        val referencedCategoryIds = core.transactions.mapNotNull { it.categoryId }.toSet()
        val filterCategories = core.categories.filter { it.id in referencedCategoryIds }

        val balancesMap = periodAccountsBal.associate { it.account.id to it.currentBalance }
        val filterAccounts = core.accounts.filter { acc ->
            val hasTx = core.transactions.any { it.fromAccountId == acc.id || it.toAccountId == acc.id }
            val hasOpening = acc.openingBalance != 0.0
            val hasCurrent = (balancesMap[acc.id] ?: 0.0) != 0.0
            hasTx || hasOpening || hasCurrent
        }

        DashboardUiState(
            filter = filterState,
            timeRange = _timeRange.value,
            chartLevel = scope.level,
            scopeYears = effectiveScopeYears,
            scopeMonths = effectiveScopeMonths,
            scopeWeeks = scope.weeks,
            yearOptions = scopedYears,
            monthOptions = scopedMonths,
            weekOptions = weeks,
            unionHint = scope.years.size > 1 || scope.months.size > 1 || scope.weeks.size > 1,
            breadcrumb = crumb,
            chartBars = bars,
            selectedBarKey = resolvedBarKey,
            kpis = filteredKpis,
            trendPoints = trends,
            trendSummary = trendSummary,
            categoryBreakdown = breakdown,
            totalExpenseInPeriod = totalExpense,
            totalIncomeInPeriod = totalIncomeInPeriod,
            totalTransferInPeriod = totalTransferInPeriod,
            totalExpenseInScope = totalExpenseInScope,
            totalCreditsInScope = totalCreditsInScope,
            totalTransfersInScope = totalTransfersInScope,
            totalScopeSubLabel = totalScopeSubLabel,
            monthlyComparison = monthlyComparison,
            accountsWithBalance = periodAccountsBal,
            insights = effectiveInsights,
            activeAccounts = filterAccounts,
            activeCategories = filterCategories,
            availableMonths = core.availableMonths,
            monthYearLabel = mLabel,
            hideBalances = core.settings.hideBalances,
            currencySymbol = core.settings.currencySymbol,
            trendBuckets = buckets,
            selectedBucketKey = resolvedKey,
            allAccounts = core.accounts,
            allTransactions = core.transactions,
            topCategoryId = topCategory?.categoryId,
            largestExpenseDate = largestExpense?.date,
            calculatedBudgets = periodBudgets,
            budgetsCardVisible = core.settings.budgetsCardVisible,
            activeBudgets = core.activeBudgets,
            tightestInsightBudget = effectiveTightestInsightBudget,
            activeTypeMode = effTypeMode,
            activeInsightsMode = activeInsightsMode,
            isBudgetsCardVisibleForMode = isBudgetsVisibleForMode,
            thisMonthCredits = thisMonthCredits,
            lastMonthCredits = lastMonthCredits,
            creditsDeltaPercent = creditsDeltaPct,
            cardPaymentsThisMonth = cardPayments,
            transfersThisMonth = transfersThisMonth,
            transfersLastMonth = transfersLastMonth,
            totalMoveCountThisMonth = totalMoveCountThisMonth,
            totalMoveCountLastMonth = totalMoveCountLastMonth,
            movedOutThisMonth = movedOut,
            movedInThisMonth = movedIn,
            incomeCategoryBreakdown = incomeBreakdown,
            transferRoutes = transferRoutes,
            incomeInsights = incomeInsights,
            transferInsights = transferInsights
        )
    }.catch { }.flowOn(Dispatchers.Default).catch { }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DashboardUiState()
    )

    fun setTypeMode(mode: String) {
        val upper = mode.uppercase()
        _activeTypeMode.value = if (upper == "ALL") "EXPENSE" else upper
        if (_filter.value.selectedType != upper) {
            sharedFilterStateHolder.updateFilter(_filter.value.copy(selectedType = upper))
        }
    }

    fun createOrUpdateBudget(name: String, scopeType: String, targetName: String, limitAmount: Double) {
        viewModelScope.launch {
            repository.upsertBudget(
                BudgetEntity(
                    name = name.trim(),
                    scopeType = scopeType.uppercase(),
                    targetName = targetName.trim(),
                    limitAmount = limitAmount,
                    active = true
                )
            )
        }
    }

    fun softDeleteBudget(id: Long) {
        viewModelScope.launch {
            repository.softDeleteBudget(id)
        }
    }

    fun updateBudgetLimit(id: Long, newLimit: Double) {
        viewModelScope.launch {
            val existing = repository.getBudgetById(id)
            if (existing != null) {
                repository.updateBudget(existing.copy(limitAmount = newLimit))
            }
        }
    }

    fun setFilter(filter: FilterState) {
        sharedFilterStateHolder.updateManualFilter(filter)
        _activeTypeMode.value = when (filter.selectedType.uppercase()) {
            "INCOME" -> "INCOME"
            "TRANSFER" -> "TRANSFER"
            else -> "EXPENSE"
        }
    }

    fun updateFilter(filter: FilterState) = setFilter(filter)

    fun applyHandoff(patch: FilterHandoffPatch): FilterState {
        // F26a/F26b: Handoffs write to drillOverlay only and never mutate manual filter or Dashboard _activeTypeMode
        return sharedFilterStateHolder.applyHandoff(patch)
    }

    fun applyDrillFilter(drillFilter: FilterState): FilterState {
        return sharedFilterStateHolder.applyDrillFilter(drillFilter)
    }

    fun clearDrillOverlay() {
        sharedFilterStateHolder.clearDrillOverlay()
    }

    fun clearAllSlicers() {
        sharedFilterStateHolder.clearAllSlicers()
        _activeTypeMode.value = "EXPENSE"
        clearChartScope()
    }

    fun toggleYear(year: Int) {
        val cur = _scope.value
        val years = if (cur.years.contains(year)) cur.years - year else cur.years + year
        _scope.value = cur.copy(years = years, level = if (cur.months.isNotEmpty()) ChartLevel.WEEKS else ChartLevel.MONTHS)
        _selectedBarKey.value = null
    }

    fun toggleMonth(monthKey: Long) {
        val cur = _scope.value
        val months = if (cur.months.contains(monthKey)) cur.months - monthKey else cur.months + monthKey
        _scope.value = cur.copy(months = months, level = if (cur.weeks.isNotEmpty()) ChartLevel.DAYS else ChartLevel.MONTHS)
        _selectedBarKey.value = null
    }

    fun toggleWeek(week: Int) {
        val cur = _scope.value
        val weeks = if (cur.weeks.contains(week)) cur.weeks - week else cur.weeks + week
        _scope.value = cur.copy(weeks = weeks, level = if (cur.months.isNotEmpty()) ChartLevel.DAYS else ChartLevel.WEEKS)
        _selectedBarKey.value = null
    }

    fun onBarTapped(bar: BarItem) {
        val effMonths = if (_filter.value.selectedMonths.isNotEmpty()) _filter.value.selectedMonths
            else if (_filter.value.selectedMonth != null) setOf(_filter.value.selectedMonth!!)
            else emptySet()
        val curLevel = if (_scope.value.years.isEmpty() && _scope.value.months.isEmpty() && _scope.value.weeks.isEmpty() && effMonths.size >= 2) {
            ChartLevel.MONTHS
        } else {
            _scope.value.level
        }
        val isAlreadyFocused = _selectedBarKey.value == bar.key
        if (!isAlreadyFocused) {
            _selectedBarKey.value = bar.key
        } else {
            _selectedBarKey.value = null
            when (curLevel) {
                ChartLevel.YEARS -> _scope.value = _scope.value.copy(years = setOf(bar.yearValue), level = ChartLevel.MONTHS)
                ChartLevel.MONTHS -> _scope.value = _scope.value.copy(months = setOf(bar.monthKey), level = ChartLevel.WEEKS)
                ChartLevel.WEEKS -> _scope.value = _scope.value.copy(
                    months = setOf(bar.monthKey), weeks = setOf(bar.weekIndex), level = ChartLevel.DAYS
                )
                ChartLevel.DAYS -> _selectedBarKey.value = bar.key
            }
        }
    }

    fun jumpToLevel(level: ChartLevel) {
        val cur = _scope.value
        _scope.value = when (level) {
            ChartLevel.YEARS -> cur.copy(years = emptySet(), months = emptySet(), weeks = emptySet(), level = ChartLevel.YEARS)
            ChartLevel.MONTHS -> cur.copy(months = emptySet(), weeks = emptySet(), level = ChartLevel.MONTHS)
            ChartLevel.WEEKS -> cur.copy(weeks = emptySet(), level = ChartLevel.WEEKS)
            ChartLevel.DAYS -> cur.copy(level = ChartLevel.DAYS)
        }
        _selectedBarKey.value = null
    }

    fun clearChartScope() {
        _scope.value = ChartScope()
        _selectedBarKey.value = null
    }

    fun setTimeRange(range: TimeRange) {
        _timeRange.value = range
        _selectedBucketKey.value = null
    }

    fun selectBucket(key: Long?) {
        _selectedBucketKey.value = key
    }
}
