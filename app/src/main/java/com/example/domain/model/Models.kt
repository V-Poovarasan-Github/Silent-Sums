package com.example.domain.model

import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.TransactionEntity

data class EnrichedTransaction(
    val entity: TransactionEntity,
    val fromAccount: Account? = null,
    val toAccount: Account? = null,
    val category: Category? = null,
    val runningBalance: Double = 0.0
) {
    val id: Long get() = entity.id
    val date: Long get() = entity.date
    val type: String get() = entity.type
    val amount: Double get() = entity.amount
    val description: String get() = entity.description
    val notes: String get() = entity.notes
    val customValues: String get() = entity.customValues
}

data class AccountWithBalance(
    val account: Account,
    val currentBalance: Double
)

data class FilterState(
    val selectedYear: Int? = null,
    val selectedYears: Set<Int> = emptySet(),
    val selectedMonth: Int? = null,
    val selectedMonths: Set<Int> = emptySet(),
    val selectedType: String = "ALL",
    val selectedCategoryId: Long? = null,
    val selectedCategoryIds: Set<Long> = emptySet(),
    val selectedAccountId: Long? = null,
    val selectedAccountIds: Set<Long> = emptySet(),
    val transferRouteFromAccountId: Long? = null,
    val transferRouteToAccountId: Long? = null,
    val transferRouteLabel: String? = null,
    val searchQuery: String = "",
    val startDate: Long? = null,
    val endDate: Long? = null
) {
    fun effectiveYears(): Set<Int> = when {
        selectedYears.isNotEmpty() -> selectedYears
        selectedYear != null -> setOf(selectedYear)
        else -> emptySet()
    }

    fun effectiveMonths(): Set<Int> = when {
        selectedMonths.isNotEmpty() -> selectedMonths
        selectedMonth != null -> setOf(selectedMonth)
        else -> emptySet()
    }

    fun effectiveCategoryIds(): Set<Long> = when {
        selectedCategoryIds.isNotEmpty() -> selectedCategoryIds
        selectedCategoryId != null -> setOf(selectedCategoryId)
        else -> emptySet()
    }

    fun effectiveAccountIds(): Set<Long> = when {
        selectedAccountIds.isNotEmpty() -> selectedAccountIds
        selectedAccountId != null -> setOf(selectedAccountId)
        else -> emptySet()
    }

    fun hasAnyActiveSlicer(): Boolean {
        val typeUpper = selectedType.uppercase().ifBlank { "ALL" }
        val hasTransferRoute = transferRouteFromAccountId != null ||
            transferRouteToAccountId != null ||
            !transferRouteLabel.isNullOrBlank()
        return effectiveYears().isNotEmpty() ||
            effectiveMonths().isNotEmpty() ||
            typeUpper != "ALL" ||
            effectiveCategoryIds().isNotEmpty() ||
            effectiveAccountIds().isNotEmpty() ||
            hasTransferRoute ||
            startDate != null ||
            endDate != null
    }
}

data class FilterHandoffPatch(
    val selectedYear: Int? = null,
    val selectedYears: Set<Int>? = null,
    val selectedMonth: Int? = null,
    val selectedMonths: Set<Int>? = null,
    val selectedType: String? = null,
    val selectedCategoryId: Long? = null,
    val selectedCategoryIds: Set<Long>? = null,
    val selectedAccountId: Long? = null,
    val selectedAccountIds: Set<Long>? = null,
    val transferRouteFromAccountId: Long? = null,
    val transferRouteToAccountId: Long? = null,
    val transferRouteLabel: String? = null,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val clearDateRange: Boolean = false,
    val clearTransferRoute: Boolean = false
)

enum class FilterDimension {
    YEARS,
    MONTHS,
    TYPE,
    CATEGORIES,
    ACCOUNTS,
    TRANSFER_ROUTE,
    DATE_RANGE
}

data class DrillOverlay(
    val activeDimensions: Set<FilterDimension> = emptySet(),
    val values: FilterState = FilterState()
) {
    val isEmpty: Boolean get() = activeDimensions.isEmpty()
}

class SharedFilterStateHolder(
    initialFilter: FilterState = FilterState()
) {
    private val _manualFilter = kotlinx.coroutines.flow.MutableStateFlow(initialFilter.copy(searchQuery = ""))
    val manualFilter: kotlinx.coroutines.flow.StateFlow<FilterState> = _manualFilter
    // Dashboard observes `filter` (manual-only, always)
    val filter: kotlinx.coroutines.flow.StateFlow<FilterState> = _manualFilter

    private val _drillOverlay = kotlinx.coroutines.flow.MutableStateFlow(DrillOverlay())
    val drillOverlay: kotlinx.coroutines.flow.StateFlow<DrillOverlay> = _drillOverlay

    private val _effectiveLedgerFilter = kotlinx.coroutines.flow.MutableStateFlow(initialFilter.copy(searchQuery = ""))
    val effectiveLedgerFilter: kotlinx.coroutines.flow.StateFlow<FilterState> = _effectiveLedgerFilter

    private val clearListeners = mutableListOf<() -> Unit>()
    private val changeListeners = mutableListOf<(FilterState) -> Unit>()
    private val manualChangeListeners = mutableListOf<(FilterState) -> Unit>()

    fun registerOnClearListener(listener: () -> Unit) {
        clearListeners.add(listener)
    }

    fun registerOnChangeListener(listener: (FilterState) -> Unit) {
        changeListeners.add(listener)
    }

    fun registerOnManualChangeListener(listener: (FilterState) -> Unit) {
        manualChangeListeners.add(listener)
    }

    fun updateManualFilter(newFilter: FilterState) {
        // Never allow Ledger-local searchQuery to leak into shared filter state
        val clean = newFilter.copy(searchQuery = "")
        _manualFilter.value = clean
        val nextEffective = computeEffectiveFilter(clean, _drillOverlay.value)
        _effectiveLedgerFilter.value = nextEffective
        manualChangeListeners.forEach { it.invoke(clean) }
        changeListeners.forEach { it.invoke(nextEffective) }
    }

    fun updateFilter(newFilter: FilterState) {
        updateManualFilter(newFilter)
    }

    fun applyHandoff(patch: FilterHandoffPatch): FilterState {
        val nextOverlay = buildOverlayFromPatch(patch)
        _drillOverlay.value = nextOverlay
        val nextEffective = computeEffectiveFilter(_manualFilter.value, nextOverlay)
        _effectiveLedgerFilter.value = nextEffective
        changeListeners.forEach { it.invoke(nextEffective) }
        return nextEffective
    }

    fun applyDrillFilter(drill: FilterState): FilterState {
        return applyHandoff(patchFromDrillFilterState(drill))
    }

    fun mergeIncomingFilter(incoming: FilterState): FilterState {
        return applyDrillFilter(incoming)
    }

    /**
     * F26d + G1: Diff incoming Ledger chip edit against current effectiveLedgerFilter across the 7
     * filter dimensions (ignoring searchQuery completely). Any changed dimension is promoted to
     * manualFilter and removed from drillOverlay; unedited drill dimensions remain in drillOverlay.
     */
    fun promoteLedgerEdit(newLedgerFilter: FilterState): FilterState {
        val cleanIncoming = newLedgerFilter.copy(searchQuery = "")
        val prevEffective = _effectiveLedgerFilter.value
        val changedDims = diffDimensions(prevEffective, cleanIncoming)
        if (changedDims.isEmpty()) {
            return prevEffective
        }
        val nextManual = applyDimensionsToFilter(
            base = _manualFilter.value,
            source = cleanIncoming,
            dims = changedDims
        )
        val remainingOverlayDims = _drillOverlay.value.activeDimensions - changedDims
        val nextOverlay = if (remainingOverlayDims.isEmpty()) {
            DrillOverlay()
        } else {
            _drillOverlay.value.copy(activeDimensions = remainingOverlayDims)
        }
        _manualFilter.value = nextManual
        _drillOverlay.value = nextOverlay
        val nextEffective = computeEffectiveFilter(nextManual, nextOverlay)
        _effectiveLedgerFilter.value = nextEffective
        manualChangeListeners.forEach { it.invoke(nextManual) }
        changeListeners.forEach { it.invoke(nextEffective) }
        return nextEffective
    }

    /**
     * F26c + G2: Discards transient drillOverlay when leaving Expenses for Dashboard, Lists, or Settings.
     */
    fun clearDrillOverlay() {
        if (!_drillOverlay.value.isEmpty) {
            _drillOverlay.value = DrillOverlay()
            val nextEffective = _manualFilter.value
            _effectiveLedgerFilter.value = nextEffective
            changeListeners.forEach { it.invoke(nextEffective) }
        }
    }

    /**
     * F26e + G1 (CLEAR-ALL BYPASS): Resets both manual and drillOverlay directly without running
     * through the per-dimension promotion diff.
     */
    fun clearAllSlicers() {
        val empty = FilterState()
        _drillOverlay.value = DrillOverlay()
        _manualFilter.value = empty
        _effectiveLedgerFilter.value = empty
        manualChangeListeners.forEach { it.invoke(empty) }
        changeListeners.forEach { it.invoke(empty) }
        clearListeners.forEach { it.invoke() }
    }

    companion object {
        fun buildOverlayFromPatch(patch: FilterHandoffPatch): DrillOverlay {
            val dims = mutableSetOf<FilterDimension>()
            if (patch.selectedYears != null || patch.selectedYear != null) {
                dims.add(FilterDimension.YEARS)
            }
            if (patch.selectedMonths != null || patch.selectedMonth != null) {
                dims.add(FilterDimension.MONTHS)
            }
            if (patch.selectedType != null && !patch.selectedType.equals("ALL", ignoreCase = true) && patch.selectedType.isNotBlank()) {
                dims.add(FilterDimension.TYPE)
            }
            if (patch.selectedCategoryIds != null || patch.selectedCategoryId != null) {
                dims.add(FilterDimension.CATEGORIES)
            }
            if (patch.selectedAccountIds != null || patch.selectedAccountId != null) {
                dims.add(FilterDimension.ACCOUNTS)
            }
            val hasPatchRoute = patch.transferRouteFromAccountId != null ||
                patch.transferRouteToAccountId != null ||
                !patch.transferRouteLabel.isNullOrBlank()
            if (patch.clearTransferRoute || hasPatchRoute) {
                dims.add(FilterDimension.TRANSFER_ROUTE)
            }
            val hasPatchDateRange = patch.startDate != null || patch.endDate != null
            if (patch.clearDateRange || hasPatchDateRange) {
                dims.add(FilterDimension.DATE_RANGE)
            }
            if (dims.isEmpty()) return DrillOverlay()
            val values = mergeHandoff(FilterState(), patch)
            return DrillOverlay(activeDimensions = dims, values = values)
        }

        fun patchFromDrillFilterState(incoming: FilterState): FilterHandoffPatch {
            if (incoming == FilterState()) return FilterHandoffPatch()
            val effYears = incoming.effectiveYears()
            val effMonths = incoming.effectiveMonths()
            val effCats = incoming.effectiveCategoryIds()
            val effAccs = incoming.effectiveAccountIds()
            val hasRoute = incoming.transferRouteFromAccountId != null ||
                incoming.transferRouteToAccountId != null ||
                !incoming.transferRouteLabel.isNullOrBlank()
            return FilterHandoffPatch(
                selectedYear = incoming.selectedYear ?: if (effYears.size == 1) effYears.first() else null,
                selectedYears = effYears.takeIf { it.isNotEmpty() },
                selectedMonth = incoming.selectedMonth ?: if (effMonths.size == 1) effMonths.first() else null,
                selectedMonths = effMonths.takeIf { it.isNotEmpty() },
                selectedType = incoming.selectedType.takeIf { !it.equals("ALL", ignoreCase = true) && it.isNotBlank() },
                selectedCategoryId = incoming.selectedCategoryId ?: if (effCats.size == 1) effCats.first() else null,
                selectedCategoryIds = effCats.takeIf { it.isNotEmpty() },
                selectedAccountId = incoming.selectedAccountId ?: if (effAccs.size == 1) effAccs.first() else null,
                selectedAccountIds = effAccs.takeIf { it.isNotEmpty() },
                transferRouteFromAccountId = if (hasRoute) incoming.transferRouteFromAccountId else null,
                transferRouteToAccountId = if (hasRoute) incoming.transferRouteToAccountId else null,
                transferRouteLabel = if (hasRoute) incoming.transferRouteLabel else null,
                startDate = incoming.startDate,
                endDate = incoming.endDate
            )
        }

        fun computeEffectiveFilter(manual: FilterState, overlay: DrillOverlay): FilterState {
            if (overlay.isEmpty) return manual.copy(searchQuery = "")
            return applyDimensionsToFilter(
                base = manual.copy(searchQuery = ""),
                source = overlay.values,
                dims = overlay.activeDimensions
            )
        }

        fun diffDimensions(prev: FilterState, next: FilterState): Set<FilterDimension> {
            val changed = mutableSetOf<FilterDimension>()
            if (prev.effectiveYears() != next.effectiveYears()) {
                changed.add(FilterDimension.YEARS)
            }
            if (prev.effectiveMonths() != next.effectiveMonths()) {
                changed.add(FilterDimension.MONTHS)
            }
            val prevType = prev.selectedType.uppercase().ifBlank { "ALL" }
            val nextType = next.selectedType.uppercase().ifBlank { "ALL" }
            if (prevType != nextType) {
                changed.add(FilterDimension.TYPE)
            }
            if (prev.effectiveCategoryIds() != next.effectiveCategoryIds()) {
                changed.add(FilterDimension.CATEGORIES)
            }
            if (prev.effectiveAccountIds() != next.effectiveAccountIds()) {
                changed.add(FilterDimension.ACCOUNTS)
            }
            val prevRoute = Triple(prev.transferRouteFromAccountId, prev.transferRouteToAccountId, prev.transferRouteLabel ?: "")
            val nextRoute = Triple(next.transferRouteFromAccountId, next.transferRouteToAccountId, next.transferRouteLabel ?: "")
            if (prevRoute != nextRoute) {
                changed.add(FilterDimension.TRANSFER_ROUTE)
            }
            if (prev.startDate != next.startDate || prev.endDate != next.endDate) {
                changed.add(FilterDimension.DATE_RANGE)
            }
            return changed
        }

        fun applyDimensionsToFilter(
            base: FilterState,
            source: FilterState,
            dims: Set<FilterDimension>
        ): FilterState {
            var res = base.copy(searchQuery = "")
            if (FilterDimension.YEARS in dims) {
                val yrs = source.effectiveYears()
                res = res.copy(
                    selectedYears = yrs,
                    selectedYear = source.selectedYear ?: if (yrs.size == 1) yrs.first() else null
                )
            }
            if (FilterDimension.MONTHS in dims) {
                val mos = source.effectiveMonths()
                res = res.copy(
                    selectedMonths = mos,
                    selectedMonth = source.selectedMonth ?: if (mos.size == 1) mos.first() else null
                )
            }
            if (FilterDimension.TYPE in dims) {
                res = res.copy(selectedType = source.selectedType.uppercase().ifBlank { "ALL" })
            }
            if (FilterDimension.CATEGORIES in dims) {
                val cats = source.effectiveCategoryIds()
                res = res.copy(
                    selectedCategoryIds = cats,
                    selectedCategoryId = source.selectedCategoryId ?: if (cats.size == 1) cats.first() else null
                )
            }
            if (FilterDimension.ACCOUNTS in dims) {
                val accs = source.effectiveAccountIds()
                res = res.copy(
                    selectedAccountIds = accs,
                    selectedAccountId = source.selectedAccountId ?: if (accs.size == 1) accs.first() else null
                )
            }
            if (FilterDimension.TRANSFER_ROUTE in dims) {
                res = res.copy(
                    transferRouteFromAccountId = source.transferRouteFromAccountId,
                    transferRouteToAccountId = source.transferRouteToAccountId,
                    transferRouteLabel = source.transferRouteLabel
                )
            }
            if (FilterDimension.DATE_RANGE in dims) {
                res = res.copy(
                    startDate = source.startDate,
                    endDate = source.endDate
                )
            }
            return res
        }

        fun mergeHandoff(current: FilterState, patch: FilterHandoffPatch): FilterState {
            val nextYears = when {
                patch.selectedYears != null -> patch.selectedYears
                patch.selectedYear != null -> setOf(patch.selectedYear)
                else -> current.selectedYears
            }
            val nextYear = when {
                patch.selectedYear != null -> patch.selectedYear
                patch.selectedYears != null -> patch.selectedYears.firstOrNull()
                else -> current.selectedYear
            }

            val nextMonths = when {
                patch.selectedMonths != null -> patch.selectedMonths
                patch.selectedMonth != null -> setOf(patch.selectedMonth)
                else -> current.selectedMonths
            }
            val nextMonth = when {
                patch.selectedMonth != null -> patch.selectedMonth
                patch.selectedMonths != null -> patch.selectedMonths.firstOrNull()
                else -> current.selectedMonth
            }

            val nextType = patch.selectedType?.uppercase() ?: current.selectedType

            val nextCatIds = when {
                patch.selectedCategoryIds != null -> patch.selectedCategoryIds
                patch.selectedCategoryId != null -> setOf(patch.selectedCategoryId)
                else -> current.selectedCategoryIds
            }
            val nextCatId = when {
                patch.selectedCategoryId != null -> patch.selectedCategoryId
                patch.selectedCategoryIds != null -> if (patch.selectedCategoryIds.size == 1) patch.selectedCategoryIds.first() else null
                else -> current.selectedCategoryId
            }

            val nextAccIds = when {
                patch.selectedAccountIds != null -> patch.selectedAccountIds
                patch.selectedAccountId != null -> setOf(patch.selectedAccountId)
                else -> current.selectedAccountIds
            }
            val nextAccId = when {
                patch.selectedAccountId != null -> patch.selectedAccountId
                patch.selectedAccountIds != null -> if (patch.selectedAccountIds.size == 1) patch.selectedAccountIds.first() else null
                else -> current.selectedAccountId
            }

            val hasPatchRoute = patch.transferRouteFromAccountId != null ||
                patch.transferRouteToAccountId != null ||
                patch.transferRouteLabel != null
            val nextRouteFrom = when {
                patch.clearTransferRoute -> null
                hasPatchRoute -> patch.transferRouteFromAccountId
                else -> current.transferRouteFromAccountId
            }
            val nextRouteTo = when {
                patch.clearTransferRoute -> null
                hasPatchRoute -> patch.transferRouteToAccountId
                else -> current.transferRouteToAccountId
            }
            val nextRouteLabel = when {
                patch.clearTransferRoute -> null
                hasPatchRoute -> patch.transferRouteLabel
                else -> current.transferRouteLabel
            }

            val hasPatchDateRange = patch.startDate != null || patch.endDate != null
            val nextStartDate = when {
                patch.clearDateRange -> null
                hasPatchDateRange -> patch.startDate
                else -> current.startDate
            }
            val nextEndDate = when {
                patch.clearDateRange -> null
                hasPatchDateRange -> patch.endDate
                else -> current.endDate
            }

            return current.copy(
                selectedYear = nextYear,
                selectedYears = nextYears,
                selectedMonth = nextMonth,
                selectedMonths = nextMonths,
                selectedType = nextType,
                selectedCategoryId = nextCatId,
                selectedCategoryIds = nextCatIds,
                selectedAccountId = nextAccId,
                selectedAccountIds = nextAccIds,
                transferRouteFromAccountId = nextRouteFrom,
                transferRouteToAccountId = nextRouteTo,
                transferRouteLabel = nextRouteLabel,
                startDate = nextStartDate,
                endDate = nextEndDate,
                searchQuery = ""
            )
        }

        fun mergeFromFilterState(current: FilterState, incoming: FilterState): FilterState {
            if (incoming == FilterState()) return current.copy(searchQuery = "")
            val patch = patchFromDrillFilterState(incoming)
            return mergeHandoff(current, patch)
        }
    }
}

data class DashboardKpis(
    val currentBalance: Double = 0.0,
    val thisMonthSpend: Double = 0.0,
    val spendDeltaPercent: Double = 0.0,
    val lastMonthSpend: Double = 0.0,
    val totalCredits: Double = 0.0,
    val totalExpense: Double = 0.0,
    val accountsCount: Int = 0,
    val totalOwed: Double = 0.0,
    val ccSpendThisMonth: Double = 0.0,
    val ccSpendLastMonth: Double = 0.0,
    val hasCreditCards: Boolean = false,
    val cardCount: Int = 0
)

data class CategoryBreakdownItem(
    val categoryId: Long?,
    val name: String,
    val colorHex: String,
    val amount: Double,
    val percentage: Float
)

data class MonthlyIncomeSpend(
    val monthLabel: String,
    val month: Int,
    val year: Int,
    val income: Double,
    val spend: Double,
    val isCurrentMonth: Boolean
)

data class TrendPoint(
    val timestamp: Long,
    val dateLabel: String,
    val subLabel: String = "",
    val periodLabel: String = "",
    val balance: Double,
    val changeFromPrevious: Double = 0.0,
    val isMilestone: Boolean = true,
    val isProjected: Boolean = false,
    val hasActivity: Boolean = false,
    val isToday: Boolean = false
)

enum class PeriodFilter(val id: String, val label: String) {
    WEEK("WEEK", "Week"),
    MONTH("MONTH", "Month"),
    YEAR("YEAR", "Year")
}

enum class ChartLevel { YEARS, MONTHS, WEEKS, DAYS }

data class BarItem(
    val key: Long,
    val periodType: PeriodFilter,
    val timestamp: Long,
    val dateLabel: String,
    val subLabel: String = "",
    val fullRangeLabel: String = "",
    val balance: Double = 0.0,
    val income: Double = 0.0,
    val expense: Double = 0.0,
    val transfer: Double = 0.0,
    val netChange: Double = 0.0,
    val isCurrent: Boolean = false,
    val isToday: Boolean = false,
    val yearValue: Int = 0,
    val monthKey: Long = 0L,
    val weekIndex: Int = 0
)

data class TrendBucket(
    val key: Long,
    val label: String,
    val rangeLabel: String,
    val net: Double,
    val high: Double,
    val low: Double,
    val endBalance: Double,
    val isCurrent: Boolean,
    val hasFuture: Boolean
)

data class TrendSummary(
    val startBalance: Double = 0.0,
    val endBalance: Double = 0.0,
    val netChange: Double = 0.0,
    val changePercent: Double = 0.0,
    val highBalance: Double = 0.0,
    val lowBalance: Double = 0.0,
    val monthName: String = "",
    val timeRangeDescription: String = "",
    val nowTimestamp: Long = 0L
)

enum class TimeRange(val label: String, val title: String) {
    RANGE_WEEKS("Weeks", "This Month (by Weeks)"),
    RANGE_1M("1M", "1 Month (30 Days)"),
    RANGE_3M("3M", "Past 3 Months"),
    RANGE_6M("6M", "Past 6 Months"),
    RANGE_1Y("1Y", "Past 1 Year"),
    RANGE_ALL("All", "All Time")
}

data class AccountBreakdownRow(
    val account: Account,
    val openingBalance: Double,
    val totalIn: Double,
    val totalOut: Double,
    val currentBalance: Double,
    val isCreditCard: Boolean = account.accountType == "CREDIT_CARD"
) {
    val name: String get() = account.name
    val type: String get() = account.accountType
}

data class BalanceBreakdownSummary(
    val rows: List<AccountBreakdownRow>,
    val liquidTotal: Double,
    val netWorthInclDebt: Double,
    val heroTotal: Double = liquidTotal
)

data class DayGroup(
    val dateTimestamp: Long,
    val dayLabel: String,
    val dayTotal: Double,
    val transactions: List<EnrichedTransaction>
)

data class LedgerUiState(
    val filter: FilterState = FilterState(),
    val dayGroups: List<DayGroup> = emptyList(),
    val totalCount: Int = 0,
    val activeAccounts: List<Account> = emptyList(),
    val activeCategories: List<Category> = emptyList(),
    val availableMonths: List<Pair<Int, Int>> = emptyList(),
    val isLoading: Boolean = false,
    val hideBalances: Boolean = false,
    val attachmentsByTxnId: Map<Long, List<com.example.data.model.MediaAttachment>> = emptyMap()
)

