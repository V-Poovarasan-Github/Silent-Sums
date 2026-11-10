package com.example.ui.commitment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.Commitment
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.domain.calculator.LedgerCalculator
import com.example.domain.commitment.CommitmentCalculator
import com.example.domain.commitment.CommitmentGroups
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CommitmentViewModel(
    private val repository: FinanceRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Unconfined
) : ViewModel() {

    val accounts: StateFlow<List<Account>> = repository.allAccounts.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val categories: StateFlow<List<Category>> = repository.allCategories.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    private val allTransactions = repository.allTransactions.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val activeCommitments: StateFlow<List<Commitment>> = repository.activeCommitments.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val accountBalances: StateFlow<Map<Long, Double>> = combine(accounts, allTransactions) { accs, txs ->
        LedgerCalculator.computeAccountBalances(accs, txs).associate { it.account.id to it.currentBalance }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyMap()
    )

    val groupedCommitments: StateFlow<CommitmentGroups> = activeCommitments.combine(accountBalances) { commitments, _ ->
        CommitmentCalculator.groupCommitments(commitments)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        CommitmentGroups(emptyList(), emptyList(), emptyList(), emptyList(), 0)
    )

    fun recordAndMarkPaid(commitment: Commitment) = viewModelScope.launch(dispatcher) {
        val amountToSettle = commitment.remaining
        val isOutgoing = commitment.direction == "OUTGOING"
        // 1. Post real EXPENSE (outgoing) or INCOME (incoming) transaction for accountId
        val tx = if (isOutgoing) {
            TransactionEntity(
                date = System.currentTimeMillis(),
                type = "EXPENSE",
                amount = amountToSettle,
                fromAccountId = commitment.accountId,
                toAccountId = null,
                categoryId = commitment.categoryId,
                description = commitment.description?.takeIf { it.isNotBlank() } ?: commitment.name,
                notes = if (commitment.paidSoFar > 0) "Settled remaining recurring payment" else "Auto-recorded recurring payment"
            )
        } else {
            TransactionEntity(
                date = System.currentTimeMillis(),
                type = "INCOME",
                amount = amountToSettle,
                fromAccountId = null,
                toAccountId = commitment.accountId,
                categoryId = commitment.categoryId,
                description = commitment.description?.takeIf { it.isNotBlank() } ?: commitment.name,
                notes = if (commitment.paidSoFar > 0) "Settled remaining recurring income" else "Auto-recorded recurring income"
            )
        }
        repository.insertTransaction(tx)

        // 2. Advance cycle and reset paidSoFar
        val rolledCycle = CommitmentCalculator.rollCycleOnPaid(commitment)
        val updated = commitment.copy(
            lastPostedCycle = rolledCycle,
            paidSoFar = 0.0
        )
        repository.updateCommitment(updated)
    }

    fun skipCycle(commitment: Commitment) = viewModelScope.launch(dispatcher) {
        val nextCycle = CommitmentCalculator.skipCycle(commitment)
        val updated = commitment.copy(
            lastPostedCycle = nextCycle,
            paidSoFar = 0.0
        )
        repository.updateCommitment(updated)
    }

    fun settleIou(commitment: Commitment, recordTransaction: Boolean, chosenAccountId: Long?) = viewModelScope.launch(dispatcher) {
        val amountToSettle = commitment.remaining
        if (recordTransaction && chosenAccountId != null && amountToSettle > 0.0) {
            val isOutgoing = commitment.direction == "OUTGOING"
            val defaultDesc = if (isOutgoing) "IOU Settled: ${commitment.name}" else "IOU Received: ${commitment.name}"
            val tx = TransactionEntity(
                date = System.currentTimeMillis(),
                type = if (isOutgoing) "EXPENSE" else "INCOME",
                amount = amountToSettle,
                fromAccountId = if (isOutgoing) chosenAccountId else null,
                toAccountId = if (!isOutgoing) chosenAccountId else null,
                categoryId = commitment.categoryId,
                description = commitment.description?.takeIf { it.isNotBlank() } ?: defaultDesc,
                notes = if (commitment.paidSoFar > 0) "Settled remaining commitment" else "Settled commitment"
            )
            repository.insertTransaction(tx)
        }
        val updated = commitment.copy(
            settled = true,
            settledAt = System.currentTimeMillis(),
            paidSoFar = commitment.amount
        )
        repository.updateCommitment(updated)
    }

    fun recordPartialPayment(
        commitment: Commitment,
        partialAmount: Double,
        txDate: Long = System.currentTimeMillis(),
        description: String? = null,
        newDueDate: Long? = null,
        chosenAccountId: Long? = null
    ) = viewModelScope.launch(dispatcher) {
            if (partialAmount <= 0.0 || partialAmount > commitment.remaining) return@launch

            val isRecurring = commitment.type == "RECURRING"
            val isOutgoing = commitment.direction == "OUTGOING"
            val accountIdToUse = chosenAccountId ?: commitment.accountId

            val defaultDesc = when {
                isRecurring && isOutgoing -> "Partial payment: ${commitment.name}"
                isRecurring -> "Partial income: ${commitment.name}"
                isOutgoing -> "IOU Partial Paid: ${commitment.name}"
                else -> "IOU Partial Received: ${commitment.name}"
            }
            val txDesc = description?.takeIf { it.isNotBlank() } ?: (commitment.description?.takeIf { it.isNotBlank() } ?: defaultDesc)

            val tx = TransactionEntity(
                date = txDate,
                type = if (isOutgoing) "EXPENSE" else "INCOME",
                amount = partialAmount,
                fromAccountId = if (isOutgoing) accountIdToUse else null,
                toAccountId = if (!isOutgoing) accountIdToUse else null,
                categoryId = commitment.categoryId,
                description = txDesc,
                notes = when {
                    isRecurring && isOutgoing -> "Partial recurring payment"
                    isRecurring -> "Partial recurring income"
                    else -> "Partial commitment settlement"
                }
            )
            repository.insertTransaction(tx)

            val updatedPaidSoFar = commitment.paidSoFar + partialAmount
            val isFullySettled = updatedPaidSoFar >= (commitment.amount - 0.001)

            val updated = if (isFullySettled) {
                if (isRecurring) {
                    val rolledCycle = CommitmentCalculator.rollCycleOnPaid(commitment)
                    commitment.copy(
                        lastPostedCycle = rolledCycle,
                        paidSoFar = 0.0,
                        dueDate = newDueDate ?: commitment.dueDate
                    )
                } else {
                    commitment.copy(
                        settled = true,
                        settledAt = txDate,
                        paidSoFar = commitment.amount,
                        dueDate = newDueDate ?: commitment.dueDate
                    )
                }
            } else {
                commitment.copy(
                    paidSoFar = updatedPaidSoFar,
                    dueDate = newDueDate ?: commitment.dueDate
                )
            }
            repository.updateCommitment(updated)
        }

    companion object {
        var sessionDraft: CommitmentDraft = CommitmentDraft()

        fun clearSessionDraft() {
            sessionDraft = CommitmentDraft()
        }
    }

    var draft: CommitmentDraft
        get() = sessionDraft
        set(value) { sessionDraft = value }

    fun updateDraft(update: (CommitmentDraft) -> CommitmentDraft) {
        sessionDraft = update(sessionDraft)
    }

    fun clearDraft() {
        sessionDraft = CommitmentDraft()
    }

    fun saveCommitment(commitment: Commitment) = viewModelScope.launch(dispatcher) {
        if (commitment.id == 0L) {
            repository.insertCommitment(commitment)
            clearDraft()
        } else {
            repository.updateCommitment(commitment)
        }
    }

    fun deleteCommitment(commitment: Commitment) = viewModelScope.launch(dispatcher) {
        repository.deleteCommitment(commitment)
    }
}

data class CommitmentDraft(
    val type: String = "RECURRING",
    val name: String = "",
    val amountText: String = "",
    val direction: String = "OUTGOING",
    val dayOfMonth: Int = 1,
    val dueDate: Long? = null,
    val selectedAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val descriptionText: String = ""
)
