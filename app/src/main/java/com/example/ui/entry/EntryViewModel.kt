package com.example.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.datastore.AppSettingsRepository
import com.example.data.model.Account
import com.example.data.model.BudgetEntity
import com.example.data.model.Category
import com.example.data.model.CustomField
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.domain.budget.BudgetCalculator
import com.example.domain.budget.CalculatedBudget
import com.example.domain.calculator.ExpressionEvaluator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.data.model.MediaAttachment
import com.example.data.repository.MediaRepository
import com.example.data.repository.StagedMedia
import com.example.ui.ledger.UncategorizedNudgeManager
import org.json.JSONObject
import java.util.Locale

data class SplitAllocationLine(
    val id: String = java.util.UUID.randomUUID().toString(),
    val transactionId: Long? = null,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val amountText: String = ""
)

data class EntryFormState(
    val transactionId: Long? = null, // null = new, non-null = edit
    val type: String = "EXPENSE", // EXPENSE, INCOME, TRANSFER
    val amountText: String = "",
    val date: Long = System.currentTimeMillis(),
    val fromAccountId: Long? = null,
    val fromAccountManualText: String = "",
    val isFromAccountManual: Boolean = false,
    val toAccountId: Long? = null,
    val toAccountManualText: String = "",
    val isToAccountManual: Boolean = false,
    val categoryId: Long? = null,
    val description: String = "",
    val notes: String = "",
    val customFieldValues: Map<String, String> = emptyMap(),
    val isMoreFieldsExpanded: Boolean = false,
    val isSplit: Boolean = false,
    val splitGroupId: String? = null,
    val splitLines: List<SplitAllocationLine> = emptyList(),
    val errorMessage: String? = null
) {
    val totalAmount: Double
        get() {
            val raw = amountText.replace(",", "").trim()
            return if (raw.contains('+') || raw.contains('-') || raw.contains('*') || raw.contains('/')) {
                ExpressionEvaluator.evaluate(raw) ?: 0.0
            } else {
                raw.toDoubleOrNull() ?: 0.0
            }
        }

    val assignedAmount: Double
        get() = splitLines.sumOf { it.amountText.toDoubleOrNull() ?: 0.0 }

    val leftToAssign: Double
        get() = totalAmount - assignedAmount

    val isSplitValidToSave: Boolean
        get() = !isSplit || (totalAmount > 0.0 && Math.abs(leftToAssign) < 0.01 && splitLines.size >= 2 && splitLines.all { (it.amountText.toDoubleOrNull() ?: 0.0) > 0.0 })
}

class EntryViewModel(
    private val repository: FinanceRepository,
    private val editTransactionId: Long? = null,
    private val initialSourceId: Long? = null,
    private val initialCategoryId: Long? = null,
    private val initialType: String? = null,
    private val settingsRepository: AppSettingsRepository? = null,
    val mediaRepository: MediaRepository? = null,
    private val initialToAccountId: Long? = null,
    private val initialAmountText: String? = null
) : ViewModel() {

    private val _formState = MutableStateFlow(
        EntryFormState(
            transactionId = editTransactionId,
            type = initialType ?: "EXPENSE",
            amountText = initialAmountText ?: "",
            fromAccountId = initialSourceId,
            toAccountId = initialToAccountId ?: initialSourceId,
            categoryId = initialCategoryId
        )
    )
    val formState: StateFlow<EntryFormState> = _formState

    private val _saveSuccessEvent = MutableSharedFlow<Unit>()
    val saveSuccessEvent: SharedFlow<Unit> = _saveSuccessEvent.asSharedFlow()

    private val _crossedBudgetsWarning = MutableStateFlow<List<CalculatedBudget>>(emptyList())
    val crossedBudgetsWarning: StateFlow<List<CalculatedBudget>> = _crossedBudgetsWarning

    private var originalAmount: Double = 0.0

    val isDemoMode: StateFlow<Boolean> = (settingsRepository?.settingsFlow?.map { it.demoMode } ?: flowOf(false))
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _stagedMedia = MutableStateFlow<List<StagedMedia>>(emptyList())
    val stagedMedia: StateFlow<List<StagedMedia>> = _stagedMedia

    private val _existingAttachments = MutableStateFlow<List<MediaAttachment>>(emptyList())
    val existingAttachments: StateFlow<List<MediaAttachment>> = _existingAttachments

    private val _deletedExistingAttachmentIds = mutableSetOf<Long>()
    private var _initialLoadedAttachments = listOf<MediaAttachment>()

    private var _mediaRepo: MediaRepository? = mediaRepository

    val effectiveMediaRepository: MediaRepository?
        get() = _mediaRepo ?: mediaRepository

    fun bindMediaRepository(repo: MediaRepository) {
        _mediaRepo = repo
        val currentTxId = _formState.value.transactionId ?: editTransactionId
        if (currentTxId != null && currentTxId > 0 && _existingAttachments.value.isEmpty()) {
            viewModelScope.launch {
                val atts = repo.getAttachmentsForTxnSync(currentTxId)
                _initialLoadedAttachments = atts
                _existingAttachments.value = atts
            }
        }
    }

    private val _mediaWarning = MutableStateFlow<String?>(null)
    val mediaWarning: StateFlow<String?> = _mediaWarning

    val totalPhotoCount: StateFlow<Int> = combine(_stagedMedia, _existingAttachments) { staged, existing ->
        staged.count { it.kind == "PHOTO" } + existing.count { it.kind == "PHOTO" }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val totalVoiceCount: StateFlow<Int> = combine(_stagedMedia, _existingAttachments) { staged, existing ->
        staged.count { it.kind == "VOICE" } + existing.count { it.kind == "VOICE" }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun addStagedMedia(item: StagedMedia): Boolean {
        val curPhotos = _stagedMedia.value.count { it.kind == "PHOTO" } + _existingAttachments.value.count { it.kind == "PHOTO" }
        val curVoice = _stagedMedia.value.count { it.kind == "VOICE" } + _existingAttachments.value.count { it.kind == "VOICE" }
        if (item.kind == "PHOTO") {
            if (curPhotos >= 10) {
                _mediaWarning.value = "Up to 10 photos per entry"
                try { item.file.delete() } catch (_: Exception) {}
                return false
            }
        } else if (item.kind == "VOICE") {
            if (curVoice >= 3) {
                _mediaWarning.value = "Up to 3 voice notes per entry"
                try { item.file.delete() } catch (_: Exception) {}
                return false
            }
        }
        _mediaWarning.value = null
        _stagedMedia.value = _stagedMedia.value + item
        return true
    }

    fun removeStagedMedia(item: StagedMedia) {
        try { item.file.delete() } catch (_: Exception) {}
        _stagedMedia.value = _stagedMedia.value.filter { it.id != item.id }
        _mediaWarning.value = null
    }

    fun removeStagedMediaById(id: String) {
        val item = _stagedMedia.value.find { it.id == id }
        if (item != null) {
            removeStagedMedia(item)
        }
    }

    fun removeExistingAttachment(attachment: MediaAttachment) {
        _deletedExistingAttachmentIds.add(attachment.id)
        _existingAttachments.value = _existingAttachments.value.filter { it.id != attachment.id }
        _mediaWarning.value = null
    }

    fun clearMediaWarning() {
        _mediaWarning.value = null
    }

    fun wipeStagingOnCancel() {
        viewModelScope.launch {
            effectiveMediaRepository?.wipeStaging()
        }
    }

    fun onCancelOrDismiss() {
        viewModelScope.launch {
            effectiveMediaRepository?.wipeStaging()
        }
    }

    val accounts: StateFlow<List<Account>> = repository.activeAccounts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val categories: StateFlow<List<Category>> = repository.activeCategories.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val customFields: StateFlow<List<CustomField>> = repository.allCustomFields.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val recentFrequentTransactions: StateFlow<List<TransactionEntity>> = repository.allTransactions.map { txList ->
        txList.filter { it.type.equals("EXPENSE", ignoreCase = true) }
            .distinctBy { Triple(it.amount, it.categoryId, it.fromAccountId) }
            .take(6)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val quickLogChipsEnabled: StateFlow<Boolean> = (settingsRepository?.settingsFlow?.map { it.quickLogChipsEnabled }
        ?: flowOf(true))
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true
        )

    private val monthStartDayFlow = (settingsRepository?.settingsFlow?.map { it.monthStartDay }
        ?: flowOf(1))
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 1
        )

    private var lastResolvedAmount: Double = 0.0

    private data class EntryBudgetData(
        val budgets: List<BudgetEntity>,
        val transactions: List<TransactionEntity>,
        val accounts: List<Account>,
        val categories: List<Category>,
        val monthStartDay: Int
    )

    private val entryBudgetDataFlow = combine(
        combine(repository.activeBudgets, repository.allTransactions) { b, t -> b to t },
        combine(repository.activeAccounts, repository.activeCategories) { a, c -> a to c },
        monthStartDayFlow
    ) { (budgets, transactions), (accounts, categories), mStartDay ->
        EntryBudgetData(budgets, transactions, accounts, categories, mStartDay)
    }

    val liveTightestBudget: StateFlow<CalculatedBudget?> = combine(
        entryBudgetDataFlow,
        _formState
    ) { data, form ->
        if (!form.type.equals("EXPENSE", ignoreCase = true)) {
            return@combine null
        }

        val raw = form.amountText.replace(",", "").trim()
        val numericAmount = if (raw.contains('+') || raw.contains('-') || raw.contains('*') || raw.contains('/')) {
            val eval = ExpressionEvaluator.evaluate(raw)
            if (eval != null && eval >= 0.0) {
                lastResolvedAmount = eval
                eval
            } else {
                lastResolvedAmount
            }
        } else {
            val parsed = raw.toDoubleOrNull()
            if (parsed != null && parsed >= 0.0) {
                lastResolvedAmount = parsed
                parsed
            } else {
                lastResolvedAmount
            }
        }

        val fromAcc = data.accounts.firstOrNull { it.id == form.fromAccountId }
        val cat = data.categories.firstOrNull { it.id == form.categoryId }

        val matches = BudgetCalculator.findMatchingBudgets(data.budgets, fromAcc?.name, cat?.name)
        val calculated = BudgetCalculator.computeBudgets(data.budgets, data.transactions, data.accounts, data.categories, data.monthStartDay)

        BudgetCalculator.getTightestBudgetForEntry(calculated, matches, numericAmount)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    init {
        if (editTransactionId != null && editTransactionId > 0) {
            viewModelScope.launch {
                val tx = repository.getTransactionById(editTransactionId)
                if (tx != null) {
                    val gId = tx.groupId
                    if (gId != null) {
                        val groupTxns = repository.database.transactionDao().getTransactionsByGroupId(gId)
                        if (groupTxns.size > 1) {
                            val totalSum = groupTxns.sumOf { it.amount }
                            originalAmount = totalSum
                            lastResolvedAmount = totalSum
                            val map = mutableMapOf<String, String>()
                            try {
                                val json = JSONObject(tx.customValues)
                                val keys = json.keys()
                                while (keys.hasNext()) {
                                    val k = keys.next()
                                    map[k] = json.optString(k, "")
                                }
                            } catch (_: Exception) {}

                            val lines = groupTxns.map { gTx ->
                                SplitAllocationLine(
                                    id = java.util.UUID.randomUUID().toString(),
                                    transactionId = gTx.id,
                                    categoryId = gTx.categoryId,
                                    accountId = if (gTx.type == "INCOME") gTx.toAccountId else gTx.fromAccountId,
                                    amountText = if (gTx.amount % 1.0 == 0.0) gTx.amount.toLong().toString() else gTx.amount.toString()
                                )
                            }
                            _formState.value = EntryFormState(
                                transactionId = tx.id,
                                type = tx.type,
                                amountText = if (totalSum % 1.0 == 0.0) totalSum.toLong().toString() else totalSum.toString(),
                                date = tx.date,
                                fromAccountId = tx.fromAccountId,
                                toAccountId = tx.toAccountId,
                                categoryId = tx.categoryId,
                                description = tx.description,
                                notes = tx.notes,
                                customFieldValues = map,
                                isMoreFieldsExpanded = map.isNotEmpty(),
                                isSplit = true,
                                splitGroupId = gId,
                                splitLines = lines
                            )
                            val firstId = groupTxns.first().id
                            if (mediaRepository != null) {
                                val atts = mediaRepository.getAttachmentsForTxnSync(firstId)
                                _initialLoadedAttachments = atts
                                _existingAttachments.value = atts
                            }
                            return@launch
                        }
                    }

                    originalAmount = tx.amount
                    lastResolvedAmount = tx.amount
                    val map = mutableMapOf<String, String>()
                    try {
                        val json = JSONObject(tx.customValues)
                        val keys = json.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            map[k] = json.optString(k, "")
                        }
                    } catch (_: Exception) {}

                    _formState.value = EntryFormState(
                        transactionId = tx.id,
                        type = tx.type,
                        amountText = if (tx.amount % 1.0 == 0.0) tx.amount.toLong().toString() else tx.amount.toString(),
                        date = tx.date,
                        fromAccountId = tx.fromAccountId,
                        toAccountId = tx.toAccountId,
                        categoryId = tx.categoryId,
                        description = tx.description,
                        notes = tx.notes,
                        customFieldValues = map,
                        isMoreFieldsExpanded = map.isNotEmpty()
                    )
                    if (mediaRepository != null) {
                        val atts = mediaRepository.getAttachmentsForTxnSync(tx.id)
                        _initialLoadedAttachments = atts
                        _existingAttachments.value = atts
                    }
                }
            }
        } else if (initialType == "TRANSFER" && initialToAccountId != null && (initialSourceId == null || initialAmountText == null)) {
            viewModelScope.launch {
                val (accs, txs) = combine(repository.allAccounts, repository.allTransactions) { a, t -> a to t }
                    .first { (a, _) -> a.isNotEmpty() }
                val resolvedFromId = initialSourceId ?: txs.asSequence()
                    .filter {
                        it.type.equals("TRANSFER", ignoreCase = true) &&
                            it.toAccountId == initialToAccountId &&
                            it.fromAccountId != null
                    }
                    .maxWithOrNull(compareBy<TransactionEntity> { it.date }.thenBy { it.id })
                    ?.fromAccountId
                val resolvedAmount = initialAmountText ?: run {
                    val cardBal = com.example.domain.calculator.LedgerCalculator
                        .computeAccountBalances(accs, txs)
                        .firstOrNull { it.account.id == initialToAccountId }
                        ?.currentBalance ?: 0.0
                    val debt = if (cardBal < 0.0) -cardBal else 0.0
                    if (debt > 0.0) String.format(Locale.US, "%.2f", debt) else ""
                }
                _formState.value = _formState.value.copy(
                    type = "TRANSFER",
                    toAccountId = initialToAccountId,
                    fromAccountId = _formState.value.fromAccountId ?: resolvedFromId,
                    amountText = if (_formState.value.amountText.isEmpty()) resolvedAmount else _formState.value.amountText
                )
            }
        }
    }

    fun setType(type: String) {
        _formState.value = _formState.value.copy(type = type, errorMessage = null)
    }

    fun onKeypadPress(key: String) {
        val current = _formState.value.amountText
        val next = when (key) {
            "BACKSPACE" -> if (current.isNotEmpty()) current.dropLast(1) else ""
            "." -> if (current.contains(".")) current else if (current.isEmpty()) "0." else "$current."
            "=" -> {
                val eval = ExpressionEvaluator.evaluate(current)
                if (eval != null) {
                    if (eval % 1.0 == 0.0) eval.toLong().toString() else String.format(Locale.US, "%.2f", eval)
                } else current
            }
            else -> current + key
        }
        _formState.value = _formState.value.copy(amountText = next, errorMessage = null)
    }

    fun onAmountTextChange(text: String) {
        _formState.value = _formState.value.copy(amountText = text, errorMessage = null)
    }

    fun setDate(timestamp: Long) {
        _formState.value = _formState.value.copy(date = timestamp)
    }

    fun selectFromAccount(id: Long?) {
        _formState.value = _formState.value.copy(fromAccountId = id, isFromAccountManual = false, errorMessage = null)
    }

    fun setFromAccountManual(name: String) {
        _formState.value = _formState.value.copy(fromAccountManualText = name, isFromAccountManual = true, fromAccountId = null, errorMessage = null)
    }

    fun selectToAccount(id: Long?) {
        _formState.value = _formState.value.copy(toAccountId = id, isToAccountManual = false, errorMessage = null)
    }

    fun setToAccountManual(name: String) {
        _formState.value = _formState.value.copy(toAccountManualText = name, isToAccountManual = true, toAccountId = null, errorMessage = null)
    }

    fun selectCategory(id: Long?) {
        _formState.value = _formState.value.copy(categoryId = id)
    }

    fun setAmountDirect(amount: String) = onAmountTextChange(amount)
    fun setFromAccount(id: Long?) = selectFromAccount(id)
    fun setToAccount(id: Long?) = selectToAccount(id)
    fun setCategory(id: Long?) = selectCategory(id)
    fun addNewCategoryInline(name: String, colorHex: String = "#F59E0B") {
        viewModelScope.launch {
            val newId = repository.insertCategory(Category(name = name.trim(), color = colorHex))
            selectCategory(newId)
        }
    }

    fun setDescription(desc: String) {
        _formState.value = _formState.value.copy(description = desc)
    }

    fun setNotes(notes: String) {
        _formState.value = _formState.value.copy(notes = notes)
    }

    fun setCustomFieldValue(key: String, value: String) {
        val next = _formState.value.customFieldValues.toMutableMap()
        next[key] = value
        _formState.value = _formState.value.copy(customFieldValues = next)
    }

    fun toggleMoreFields() {
        _formState.value = _formState.value.copy(isMoreFieldsExpanded = !_formState.value.isMoreFieldsExpanded)
    }

    fun prefillFromFrequent(tx: TransactionEntity) {
        val current = _formState.value
        _formState.value = current.copy(
            amountText = if (tx.amount % 1.0 == 0.0) tx.amount.toLong().toString() else String.format(Locale.US, "%.2f", tx.amount),
            categoryId = tx.categoryId,
            fromAccountId = tx.fromAccountId,
            description = tx.description,
            errorMessage = null
        )
    }

    fun dismissCrossedBudgetsWarning() {
        _crossedBudgetsWarning.value = emptyList()
    }

    fun toggleSplit(enable: Boolean) {
        val current = _formState.value
        if (enable) {
            val defaultAcc = current.fromAccountId ?: current.toAccountId
            val lines = if (current.splitLines.size >= 2) {
                current.splitLines
            } else {
                listOf(
                    SplitAllocationLine(categoryId = current.categoryId, accountId = defaultAcc, amountText = ""),
                    SplitAllocationLine(categoryId = null, accountId = defaultAcc, amountText = "")
                )
            }
            _formState.value = current.copy(isSplit = true, splitLines = lines, errorMessage = null)
        } else {
            _formState.value = current.copy(isSplit = false, splitGroupId = null, errorMessage = null)
        }
    }

    fun addSplitLine() {
        val current = _formState.value
        if (current.splitLines.size < 5) {
            val defaultAcc = current.fromAccountId ?: current.toAccountId
            val newLine = SplitAllocationLine(accountId = defaultAcc, amountText = "")
            _formState.value = current.copy(splitLines = current.splitLines + newLine, errorMessage = null)
        }
    }

    fun removeSplitLine(lineId: String) {
        val current = _formState.value
        val nextLines = current.splitLines.filter { it.id != lineId }
        if (nextLines.size == 1) {
            val single = nextLines.first()
            _formState.value = current.copy(
                isSplit = false,
                splitGroupId = null,
                splitLines = emptyList(),
                categoryId = single.categoryId ?: current.categoryId,
                fromAccountId = if (current.type != "INCOME") single.accountId ?: current.fromAccountId else current.fromAccountId,
                toAccountId = if (current.type == "INCOME") single.accountId ?: current.toAccountId else current.toAccountId,
                amountText = if (single.amountText.isNotBlank()) single.amountText else current.amountText,
                errorMessage = null
            )
        } else {
            _formState.value = current.copy(splitLines = nextLines, errorMessage = null)
        }
    }

    fun updateSplitLine(lineId: String, categoryId: Long?, accountId: Long?, amountText: String) {
        val current = _formState.value
        // If total is unset (<= 0), lines are disabled from editing amounts
        if (current.isSplit && current.totalAmount <= 0.0 && amountText.isNotBlank()) {
            return
        }
        val updated = current.splitLines.map { line ->
            if (line.id == lineId) {
                line.copy(categoryId = categoryId, accountId = accountId, amountText = amountText)
            } else line
        }
        _formState.value = current.copy(splitLines = updated, errorMessage = null)
    }

    fun saveTransaction(forceSave: Boolean = false) {
        val current = _formState.value
        val raw = current.amountText.replace(",", "").trim()
        val amount = if (raw.contains('+') || raw.contains('-') || raw.contains('*') || raw.contains('/')) {
            ExpressionEvaluator.evaluate(raw) ?: 0.0
        } else {
            raw.toDoubleOrNull() ?: 0.0
        }

        if (amount <= 0.0) {
            _formState.value = current.copy(errorMessage = "Amount must be greater than zero")
            return
        }

        if (current.isSplit) {
            val lineAmounts = current.splitLines.map { it.amountText.toDoubleOrNull() ?: 0.0 }
            val sumLines = lineAmounts.sum()
            val leftToAssign = amount - sumLines
            if (Math.abs(leftToAssign) > 0.01) {
                _formState.value = current.copy(errorMessage = "All funds must be assigned before saving")
                return
            }
            if (lineAmounts.any { it <= 0.0 }) {
                _formState.value = current.copy(errorMessage = "Every split line must have an amount greater than zero")
                return
            }
        }

        viewModelScope.launch {
            if (current.isSplit) {
                // Build custom fields JSON
                val json = JSONObject()
                current.customFieldValues.forEach { (k, v) ->
                    if (v.isNotBlank()) json.put(k, v)
                }

                val gId = current.splitGroupId ?: java.util.UUID.randomUUID().toString()

                // If editing existing group, clean up removed lines
                if (current.splitGroupId != null) {
                    val oldTxns = repository.database.transactionDao().getTransactionsByGroupId(current.splitGroupId)
                    val remainingTxIds = current.splitLines.mapNotNull { it.transactionId }.toSet()
                    for (oldTx in oldTxns) {
                        if (oldTx.id !in remainingTxIds) {
                            effectiveMediaRepository?.deleteMediaForTxn(oldTx.id)
                            repository.deleteTransaction(oldTx)
                        }
                    }
                }

                var firstCreatedTxId: Long? = null
                current.splitLines.forEachIndexed { index, line ->
                    val lineAmt = line.amountText.toDoubleOrNull() ?: 0.0
                    val lineEntity = TransactionEntity(
                        id = line.transactionId ?: 0L,
                        date = current.date,
                        type = current.type,
                        amount = lineAmt,
                        fromAccountId = if (current.type == "INCOME") null else (line.accountId ?: current.fromAccountId),
                        toAccountId = if (current.type == "EXPENSE") null else (line.accountId ?: current.toAccountId),
                        categoryId = if (current.type == "TRANSFER") null else line.categoryId,
                        description = current.description.trim(),
                        notes = current.notes.trim(),
                        customValues = json.toString(),
                        updatedAt = System.currentTimeMillis(),
                        groupId = gId
                    )
                    val savedId = if (lineEntity.id > 0) {
                        repository.updateTransaction(lineEntity)
                        lineEntity.id
                    } else {
                        repository.insertTransaction(lineEntity)
                    }
                    if (index == 0) {
                        firstCreatedTxId = savedId
                    }
                }

                val repoToUse = effectiveMediaRepository
                if (repoToUse != null && firstCreatedTxId != null) {
                    for (delId in _deletedExistingAttachmentIds) {
                        val toDel = _initialLoadedAttachments.find { it.id == delId }
                        if (toDel != null) {
                            repoToUse.deleteAttachment(toDel)
                        }
                    }
                    repoToUse.commitStagedMedia(firstCreatedTxId!!, _stagedMedia.value)
                }

                if (current.splitLines.any { it.categoryId == null } && current.type != "TRANSFER") {
                    UncategorizedNudgeManager.triggerNudge(firstCreatedTxId)
                }

                _saveSuccessEvent.emit(Unit)
                return@launch
            }

            // Normal Non-Split Save:
            // If was previously a group and downgraded to 1 row, delete sibling transactions
            if (current.transactionId != null && current.transactionId > 0) {
                val existing = repository.getTransactionById(current.transactionId)
                if (existing?.groupId != null && current.splitGroupId == null) {
                    val groupTxns = repository.database.transactionDao().getTransactionsByGroupId(existing.groupId)
                    for (other in groupTxns) {
                        if (other.id != current.transactionId) {
                            effectiveMediaRepository?.deleteMediaForTxn(other.id)
                            repository.deleteTransaction(other)
                        }
                    }
                }
            }

            // Over-limit Save confirm check: STRICTLY on within-to-over transition
            if (!forceSave && current.type.equals("EXPENSE", ignoreCase = true)) {
                val accList = if (accounts.value.isNotEmpty()) accounts.value else repository.getActiveAccountsList()
                val catList = if (categories.value.isNotEmpty()) categories.value else repository.getActiveCategoriesList()
                val fromAcc = accList.firstOrNull { it.id == current.fromAccountId }
                val cat = catList.firstOrNull { it.id == current.categoryId }
                val activeBudgets = repository.getActiveBudgetsList()
                val matches = BudgetCalculator.findMatchingBudgets(activeBudgets, fromAcc?.name, cat?.name)

                if (matches.isNotEmpty()) {
                    val txList = repository.getAllTransactionsList()
                    val mStartDay = monthStartDayFlow.value
                    val calculated = BudgetCalculator.computeBudgets(activeBudgets, txList, accList, catList, mStartDay)
                    val crossed = BudgetCalculator.checkCrossedBudgets(
                        calculatedBudgets = calculated,
                        matchingBudgets = matches,
                        newAmount = amount,
                        oldAmount = originalAmount
                    )

                    if (crossed.isNotEmpty()) {
                        _crossedBudgetsWarning.value = crossed
                        return@launch
                    }
                }
            }

            // Resolve From Account
            var resolvedFromAccountId: Long? = current.fromAccountId
            if (current.isFromAccountManual) {
                val typed = current.fromAccountManualText.trim()
                if (typed.isBlank()) {
                    _formState.value = current.copy(errorMessage = "Please enter a source name")
                    return@launch
                }
                val existing = repository.getAccountByName(typed)
                resolvedFromAccountId = existing?.id ?: repository.insertAccount(Account(name = typed, openingBalance = 0.0))
            }

            // Resolve To Account
            var resolvedToAccountId: Long? = current.toAccountId
            if (current.isToAccountManual) {
                val typed = current.toAccountManualText.trim()
                if (typed.isBlank()) {
                    _formState.value = current.copy(errorMessage = "Please enter a source name")
                    return@launch
                }
                val existing = repository.getAccountByName(typed)
                resolvedToAccountId = existing?.id ?: repository.insertAccount(Account(name = typed, openingBalance = 0.0))
            }

            when (current.type.uppercase()) {
                "EXPENSE" -> {
                    if (resolvedFromAccountId == null) {
                        _formState.value = current.copy(errorMessage = "Please select a source for expense")
                        return@launch
                    }
                }
                "INCOME" -> {
                    if (resolvedToAccountId == null) {
                        _formState.value = current.copy(errorMessage = "Please select a source for income")
                        return@launch
                    }
                }
                "TRANSFER" -> {
                    if (resolvedFromAccountId == null || resolvedToAccountId == null) {
                        _formState.value = current.copy(errorMessage = "Transfer requires both From and To sources")
                        return@launch
                    }
                    if (resolvedFromAccountId == resolvedToAccountId) {
                        _formState.value = current.copy(errorMessage = "From and To sources must be different")
                        return@launch
                    }
                }
            }

            // Build custom fields JSON
            val json = JSONObject()
            current.customFieldValues.forEach { (k, v) ->
                if (v.isNotBlank()) json.put(k, v)
            }

            val entity = TransactionEntity(
                id = current.transactionId ?: 0L,
                date = current.date,
                type = current.type,
                amount = amount,
                fromAccountId = if (current.type == "INCOME") null else resolvedFromAccountId,
                toAccountId = if (current.type == "EXPENSE") null else resolvedToAccountId,
                categoryId = if (current.type == "TRANSFER") null else current.categoryId,
                description = current.description.trim(),
                notes = current.notes.trim(),
                customValues = json.toString(),
                updatedAt = System.currentTimeMillis(),
                groupId = null
            )

            val txnId = if (current.transactionId != null && current.transactionId > 0) {
                repository.updateTransaction(entity)
                current.transactionId
            } else {
                repository.insertTransaction(entity)
            }

            val repoToUse = effectiveMediaRepository
            if (repoToUse != null) {
                for (delId in _deletedExistingAttachmentIds) {
                    val toDel = _initialLoadedAttachments.find { it.id == delId }
                    if (toDel != null) {
                        repoToUse.deleteAttachment(toDel)
                    }
                }
                repoToUse.commitStagedMedia(txnId, _stagedMedia.value)
            }

            _saveSuccessEvent.emit(Unit)
        }
    }

    fun deleteTransaction() {
        val id = _formState.value.transactionId ?: return
        viewModelScope.launch {
            effectiveMediaRepository?.deleteMediaForTxn(id)
            repository.deleteTransactionById(id)
            _saveSuccessEvent.emit(Unit)
        }
    }
}
