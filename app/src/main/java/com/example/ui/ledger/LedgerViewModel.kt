package com.example.ui.ledger

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.datastore.AppSettingsRepository
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.domain.calculator.LedgerCalculator
import com.example.domain.model.DayGroup
import com.example.domain.model.EnrichedTransaction
import com.example.domain.model.FilterHandoffPatch
import com.example.domain.model.FilterState
import com.example.domain.model.LedgerUiState
import com.example.domain.model.SharedFilterStateHolder
import com.example.importbackup.UnifiedCsvStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerViewModel(
    private val repository: FinanceRepository,
    private val settingsRepository: AppSettingsRepository? = null,
    private val mediaRepository: com.example.data.repository.MediaRepository? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    val sharedFilterStateHolder: SharedFilterStateHolder = SharedFilterStateHolder()
) : ViewModel() {
    // Secondary constructor for backward compatibility
    constructor(
        repository: FinanceRepository,
        settingsRepository: AppSettingsRepository?,
        ioDispatcher: CoroutineDispatcher,
        mediaRepository: com.example.data.repository.MediaRepository?
    ) : this(repository, settingsRepository, mediaRepository, ioDispatcher, SharedFilterStateHolder())

    private val _mediaRepositoryFlow = MutableStateFlow(mediaRepository)

    fun attachMediaRepository(repo: com.example.data.repository.MediaRepository?) {
        if (_mediaRepositoryFlow.value == null && repo != null) {
            _mediaRepositoryFlow.value = repo
        }
    }

    val attachmentsByTxnId: StateFlow<Map<Long, List<com.example.data.model.MediaAttachment>>> =
        _mediaRepositoryFlow.flatMapLatest { repo ->
            repo?.getAllAttachmentsByTxnFlow()?.catch { } ?: flowOf(emptyMap())
        }.catch { }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyMap()
        )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filter = MutableStateFlow(sharedFilterStateHolder.effectiveLedgerFilter.value)
    val filter: StateFlow<FilterState> = _filter.asStateFlow()

    init {
        sharedFilterStateHolder.registerOnChangeListener { shared ->
            _filter.value = shared.copy(searchQuery = _searchQuery.value)
        }
        sharedFilterStateHolder.registerOnClearListener {
            _searchQuery.value = ""
            _filter.value = sharedFilterStateHolder.effectiveLedgerFilter.value
        }
    }

    private var pendingDeleteTxs: List<TransactionEntity> = emptyList()
    private var pendingDeleteJob: Job? = null
    private val _pendingDeletedIds = MutableStateFlow<Set<Long>>(emptySet())

    private val _undoSnackbarEvent = MutableSharedFlow<String>()
    val undoSnackbarEvent: SharedFlow<String> = _undoSnackbarEvent.asSharedFlow()

    private val _statusMessage = MutableSharedFlow<String>(replay = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val statusMessage: SharedFlow<String> = _statusMessage.asSharedFlow()

    private val _swipedRowId = MutableStateFlow<Long?>(null)
    val swipedRowId: StateFlow<Long?> = _swipedRowId.asStateFlow()

    fun setSwipedRowId(id: Long?) {
        _swipedRowId.value = id
    }

    fun clearSwipedRow() {
        _swipedRowId.value = null
    }

    private val hideBalancesFlow = settingsRepository?.settingsFlow?.map {
        com.example.domain.util.Formatters.updateCurrentCurrencySymbol(it.currencySymbol)
        it.hideBalances
    } ?: flowOf(false)

    private val enrichedTransactionsFlow = combine(
        repository.allTransactions.catch { },
        repository.allAccounts.catch { },
        repository.allCategories.catch { }
    ) { txList, accounts, categories ->
        val enriched = LedgerCalculator.computeRunningBalances(txList, accounts, categories)
        Triple(enriched, accounts.filter { !it.archived }, categories.filter { !it.archived })
    }.catch { }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<LedgerUiState> = combine(
        _filter,
        enrichedTransactionsFlow,
        _pendingDeletedIds,
        hideBalancesFlow,
        attachmentsByTxnId
    ) { filterState, (enriched, accounts, categories), pendingIds, hideBalances, attachmentsMap ->
        val nonPending = if (pendingIds.isEmpty()) enriched else enriched.filter { it.id !in pendingIds }
        val filtered = LedgerCalculator.filterTransactions(nonPending, filterState)

        // S5: If any line matches search/filter, show the entire group
        val matchedGroupIds = filtered.mapNotNull { it.entity.groupId }.toSet()
        val finalFiltered = if (matchedGroupIds.isEmpty()) {
            filtered
        } else {
            nonPending.filter { it in filtered || (it.entity.groupId != null && it.entity.groupId in matchedGroupIds) }
        }

        // Group by calendar day
        val cal = Calendar.getInstance()
        val groups = finalFiltered.groupBy { tx ->
            cal.timeInMillis = tx.date
            cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
        }.map { (_, list) ->
            val firstDate = list.first().date
            val dayLabel = com.example.domain.util.Formatters.formatDayHeader(firstDate)
            val dayTotal = list.sumOf { tx ->
                when (tx.type.uppercase()) {
                    "INCOME" -> tx.amount
                    "EXPENSE" -> -tx.amount
                    else -> 0.0
                }
            }
            DayGroup(
                dateTimestamp = firstDate,
                dayTotal = dayTotal,
                dayLabel = dayLabel,
                transactions = list
            )
        }.sortedByDescending { it.dateTimestamp }

        val availableMonths = LedgerCalculator.computeAvailableMonths(nonPending.map { it.entity })

        val txEntities = nonPending.map { it.entity }
        val referencedCategoryIds = txEntities.mapNotNull { it.categoryId }.toSet()
        val filterCategories = categories.filter { it.id in referencedCategoryIds }

        val accountsBal = LedgerCalculator.computeAccountBalances(accounts, txEntities)
        val balancesMap = accountsBal.associate { it.account.id to it.currentBalance }
        val filterAccounts = accounts.filter { acc ->
            val hasTx = txEntities.any { it.fromAccountId == acc.id || it.toAccountId == acc.id }
            val hasOpening = acc.openingBalance != 0.0
            val hasCurrent = (balancesMap[acc.id] ?: 0.0) != 0.0
            hasTx || hasOpening || hasCurrent
        }

        LedgerUiState(
            filter = filterState,
            dayGroups = groups,
            totalCount = finalFiltered.size,
            activeAccounts = filterAccounts,
            activeCategories = filterCategories,
            availableMonths = availableMonths,
            isLoading = false,
            hideBalances = hideBalances,
            attachmentsByTxnId = attachmentsMap
        )
    }.catch { }.flowOn(Dispatchers.Default).catch { }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LedgerUiState(isLoading = true)
    )

    val isExportGreyed: Boolean
        get() = uiState.value.totalCount == 0

    fun updateFilter(newFilter: FilterState) {
        clearSwipedRow()
        val normalizedAccounts = when {
            newFilter.selectedAccountIds.isNotEmpty() -> newFilter
            newFilter.selectedAccountId != null -> newFilter.copy(selectedAccountIds = setOf(newFilter.selectedAccountId))
            else -> newFilter
        }
        val normalizedCategories = when {
            normalizedAccounts.selectedCategoryIds.isNotEmpty() -> normalizedAccounts
            normalizedAccounts.selectedCategoryId != null -> normalizedAccounts.copy(selectedCategoryIds = setOf(normalizedAccounts.selectedCategoryId))
            else -> normalizedAccounts
        }
        if (newFilter.searchQuery != _searchQuery.value && newFilter.searchQuery.isNotEmpty()) {
            _searchQuery.value = newFilter.searchQuery
        }
        // F26d + G1: Promote only changed dimensions to manualFilter; searchQuery is ignored
        val effective = sharedFilterStateHolder.promoteLedgerEdit(normalizedCategories)
        _filter.value = effective.copy(searchQuery = _searchQuery.value)
    }

    fun applyHandoff(patch: FilterHandoffPatch): FilterState {
        clearSwipedRow()
        val merged = sharedFilterStateHolder.applyHandoff(patch)
        _filter.value = merged.copy(searchQuery = _searchQuery.value)
        return _filter.value
    }

    fun updateSearchQuery(query: String) {
        clearSwipedRow()
        _searchQuery.value = query
        _filter.value = sharedFilterStateHolder.effectiveLedgerFilter.value.copy(searchQuery = query)
    }

    fun clearAllSlicers() {
        clearSwipedRow()
        // F26e + G1: Clear-All bypasses per-dimension promotion and resets manual + drillOverlay directly
        sharedFilterStateHolder.clearAllSlicers()
        _filter.value = sharedFilterStateHolder.effectiveLedgerFilter.value.copy(searchQuery = _searchQuery.value)
    }

    fun clearFilters() {
        clearAllSlicers()
    }

    fun deleteTransaction(entity: TransactionEntity, onShowUndoSnackbar: ((() -> Unit) -> Unit)? = null) {
        clearSwipedRow()
        commitPendingDelete()
        viewModelScope.launch {
            val targets = if (entity.groupId != null) {
                val group = repository.database.transactionDao().getTransactionsByGroupId(entity.groupId)
                if (group.isNotEmpty()) group else listOf(entity)
            } else {
                listOf(entity)
            }
            pendingDeleteTxs = targets
            _pendingDeletedIds.value = targets.map { it.id }.toSet()
            val undo = { undoDelete() }
            onShowUndoSnackbar?.invoke(undo)
            val msg = if (targets.size > 1) {
                "Deleted split (${targets.size} lines)"
            } else {
                "Deleted \"${entity.description.ifBlank { entity.type }}\""
            }
            _undoSnackbarEvent.emit(msg)
            pendingDeleteJob?.cancel()
            pendingDeleteJob = viewModelScope.launch {
                delay(5000L)
                commitPendingDelete()
            }
        }
    }

    fun deleteTransaction(tx: EnrichedTransaction, onShowUndoSnackbar: ((() -> Unit) -> Unit)? = null) {
        deleteTransaction(tx.entity, onShowUndoSnackbar)
    }

    fun undoDelete() {
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null
        val txs = pendingDeleteTxs.ifEmpty { lastCommittedDeleteTxs }
        pendingDeleteTxs = emptyList()
        lastCommittedDeleteTxs = emptyList()
        _pendingDeletedIds.value = emptySet()
        if (txs.isNotEmpty()) {
            viewModelScope.launch {
                for (tx in txs) {
                    if (repository.getTransactionById(tx.id) == null) {
                        repository.insertTransaction(tx)
                    }
                }
            }
        }
    }

    private var lastCommittedDeleteTxs: List<TransactionEntity> = emptyList()

    fun commitPendingDelete() {
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null
        val toDelete = pendingDeleteTxs
        pendingDeleteTxs = emptyList()
        _pendingDeletedIds.value = emptySet()
        if (toDelete.isNotEmpty()) {
            lastCommittedDeleteTxs = toDelete
            viewModelScope.launch {
                for (tx in toDelete) {
                    mediaRepository?.deleteMediaForTxn(tx.id)
                    repository.deleteTransaction(tx)
                }
            }
        }
    }

    suspend fun exportFilteredCsv(context: Context): String {
        val sRepo = settingsRepository ?: AppSettingsRepository(context)
        val settings = sRepo.settingsFlow.first()
        if (settings.demoMode) {
            _statusMessage.emit("Export paused: Demo mode active")
            return "Export paused: Demo mode active"
        }
        val currentState = uiState.first { !it.isLoading }
        val allTx = currentState.dayGroups.flatMap { it.transactions }.map { it.entity }
        if (allTx.isEmpty()) {
            _statusMessage.emit("No records to export")
            return "No records to export"
        }
        val accounts = repository.allAccounts.first()
        val categories = repository.allCategories.first()
        val path = UnifiedCsvStorage.exportFilteredTransactions(
            context = context,
            transactions = allTx,
            accounts = accounts,
            categories = categories,
            customTreeUriString = settings.autoSaveTreeUri.ifBlank { null },
            settingsRepository = sRepo
        )
        _statusMessage.emit("Exported to $path")
        return path
    }
}
