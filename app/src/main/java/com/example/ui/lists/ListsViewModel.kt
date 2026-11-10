package com.example.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.datastore.AppSettingsRepository
import com.example.data.model.Account
import com.example.data.model.BudgetEntity
import com.example.data.model.Category
import com.example.data.model.CustomField
import com.example.data.repository.FinanceRepository
import com.example.domain.calculator.LedgerCalculator
import com.example.domain.model.AccountWithBalance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ListsTab {
    CATEGORIES,
    ACCOUNTS,
    FIELDS,
    BUDGETS
}

data class ListsUiState(
    val selectedTab: ListsTab = ListsTab.CATEGORIES,
    val activeCategories: List<Category> = emptyList(),
    val archivedCategories: List<Category> = emptyList(),
    val accountsWithBalances: List<AccountWithBalance> = emptyList(),
    val customFields: List<CustomField> = emptyList(),
    val activeBudgets: List<BudgetEntity> = emptyList(),
    val categoryTransactionCounts: Map<Long, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val hideBalances: Boolean = false
)

class ListsViewModel(
    private val repository: FinanceRepository,
    initialTab: ListsTab = ListsTab.CATEGORIES,
    private val settingsRepository: AppSettingsRepository? = null,
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(initialTab)
    private val hideBalancesFlow = settingsRepository?.settingsFlow?.map { it.hideBalances } ?: flowOf(false)

    private val listsDataFlow = combine(
        repository.activeCategories,
        repository.archivedCategories,
        repository.allAccounts,
        repository.allTransactions,
        combine(repository.allCustomFields, repository.activeBudgets) { fields, budgets -> fields to budgets }
    ) { activeCats, archCats, accounts, transactions, (fields, budgets) ->
        val accountsWithBal = LedgerCalculator.sortSources(LedgerCalculator.computeAccountBalances(accounts, transactions))
        val catCounts = transactions.groupBy { it.categoryId }
            .mapNotNull { (catId, list) -> catId?.let { it to list.size } }
            .toMap()
        object {
            val activeCats = activeCats
            val archCats = archCats
            val accountsWithBal = accountsWithBal
            val fields = fields
            val budgets = budgets
            val catCounts = catCounts
        }
    }

    val uiState: StateFlow<ListsUiState> = combine(
        _selectedTab,
        listsDataFlow,
        hideBalancesFlow
    ) { tab, data, hideBalances ->
        ListsUiState(
            selectedTab = tab,
            activeCategories = data.activeCats,
            archivedCategories = data.archCats,
            accountsWithBalances = data.accountsWithBal,
            customFields = data.fields,
            activeBudgets = data.budgets,
            categoryTransactionCounts = data.catCounts,
            isLoading = false,
            hideBalances = hideBalances
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ListsUiState(selectedTab = initialTab, isLoading = true)
    )

    fun selectTab(tab: ListsTab) {
        _selectedTab.value = tab
    }

    // Category CRUD & Archive
    fun addCategory(name: String, colorHex: String) {
        viewModelScope.launch(dispatcher) {
            val maxOrder = uiState.value.activeCategories.maxOfOrNull { it.sortOrder } ?: 0
            repository.insertCategory(
                Category(name = name.trim(), color = colorHex, sortOrder = maxOrder + 1)
            )
        }
    }

    fun updateCategory(category: Category, newName: String, newColorHex: String) {
        viewModelScope.launch(dispatcher) {
            repository.updateCategory(
                category.copy(name = newName.trim(), color = newColorHex)
            )
        }
    }

    fun archiveCategory(category: Category) {
        viewModelScope.launch(dispatcher) {
            repository.updateCategory(category.copy(archived = true))
        }
    }

    fun restoreCategory(category: Category) {
        viewModelScope.launch(dispatcher) {
            repository.updateCategory(category.copy(archived = false))
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch(dispatcher) {
            repository.deleteCategory(category)
        }
    }

    fun moveCategoryUp(category: Category) {
        val list = uiState.value.activeCategories.toMutableList()
        val index = list.indexOfFirst { it.id == category.id }
        if (index > 0) {
            val prev = list[index - 1]
            list[index - 1] = category.copy(sortOrder = prev.sortOrder)
            list[index] = prev.copy(sortOrder = category.sortOrder)
            viewModelScope.launch(dispatcher) {
                repository.updateCategories(list)
            }
        }
    }

    fun moveCategoryDown(category: Category) {
        val list = uiState.value.activeCategories.toMutableList()
        val index = list.indexOfFirst { it.id == category.id }
        if (index >= 0 && index < list.size - 1) {
            val next = list[index + 1]
            list[index + 1] = category.copy(sortOrder = next.sortOrder)
            list[index] = next.copy(sortOrder = category.sortOrder)
            viewModelScope.launch(dispatcher) {
                repository.updateCategories(list)
            }
        }
    }

    // Account CRUD & Archive
    fun addAccount(name: String, openingBalance: Double, accountType: String = "BANK") {
        viewModelScope.launch(dispatcher) {
            val maxOrder = uiState.value.accountsWithBalances.maxOfOrNull { it.account.sortOrder } ?: 0
            repository.insertAccount(
                Account(name = name.trim(), openingBalance = openingBalance, sortOrder = maxOrder + 1, accountType = accountType)
            )
        }
    }

    fun updateAccount(account: Account, newName: String, newOpeningBalance: Double, accountType: String = account.accountType) {
        viewModelScope.launch(dispatcher) {
            repository.updateAccount(
                account.copy(name = newName.trim(), openingBalance = newOpeningBalance, accountType = accountType)
            )
        }
    }

    fun archiveAccount(account: Account) {
        viewModelScope.launch(dispatcher) {
            repository.updateAccount(account.copy(archived = true))
        }
    }

    fun restoreAccount(account: Account) {
        viewModelScope.launch(dispatcher) {
            repository.updateAccount(account.copy(archived = false))
        }
    }

    fun deleteAccount(account: Account, onBlocked: ((count: Int) -> Unit)? = null) {
        kotlinx.coroutines.runBlocking {
            val count = repository.countTransactionsForAccount(account.id)
            if (count > 0) {
                onBlocked?.invoke(count)
            } else {
                repository.deleteAccount(account)
            }
        }
    }

    suspend fun getAccountTransactionCount(accountId: Long): Int {
        return repository.countTransactionsForAccount(accountId)
    }

    // Custom Fields CRUD
    fun addCustomField(name: String, type: String, options: String, appliesTo: String?) {
        viewModelScope.launch(dispatcher) {
            val maxOrder = uiState.value.customFields.maxOfOrNull { it.sortOrder } ?: 0
            repository.insertCustomField(
                CustomField(
                    name = name.trim(),
                    type = type,
                    options = options.trim(),
                    appliesToType = appliesTo,
                    sortOrder = maxOrder + 1
                )
            )
        }
    }

    fun updateCustomField(field: CustomField, name: String, type: String, options: String, appliesTo: String?) {
        viewModelScope.launch(dispatcher) {
            repository.updateCustomField(
                field.copy(
                    name = name.trim(),
                    type = type,
                    options = options.trim(),
                    appliesToType = appliesTo
                )
            )
        }
    }

    fun deleteCustomField(field: CustomField) {
        viewModelScope.launch(dispatcher) {
            repository.deleteCustomField(field)
        }
    }

    // Budgets CRUD
    fun upsertBudget(name: String, scopeType: String, targetName: String, limitAmount: Double) {
        viewModelScope.launch(dispatcher) {
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
        viewModelScope.launch(dispatcher) {
            repository.softDeleteBudget(id)
        }
    }
}
