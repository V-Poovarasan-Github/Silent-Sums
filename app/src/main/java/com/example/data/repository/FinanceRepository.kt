package com.example.data.repository

import com.example.data.AppDatabase
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.CustomField
import com.example.data.model.TransactionEntity
import com.example.data.seed.SampleDataSeeder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class FinanceRepository(val database: AppDatabase) {

    private val accountDao = database.accountDao()
    private val categoryDao = database.categoryDao()
    private val customFieldDao = database.customFieldDao()
    private val transactionDao = database.transactionDao()
    private val commitmentDao = database.commitmentDao()
    private val budgetDao = database.budgetDao()

    // Budgets
    val activeBudgets: Flow<List<com.example.data.model.BudgetEntity>> = budgetDao.getActiveBudgets()
    val allBudgets: Flow<List<com.example.data.model.BudgetEntity>> = budgetDao.getAllBudgets()

    suspend fun getActiveBudgetsList(): List<com.example.data.model.BudgetEntity> = budgetDao.getActiveBudgetsList()
    suspend fun getAllBudgetsList(): List<com.example.data.model.BudgetEntity> = budgetDao.getAllBudgetsList()
    suspend fun getBudgetById(id: Long): com.example.data.model.BudgetEntity? = budgetDao.getBudgetById(id)
    suspend fun getBudgetByIdentity(scopeType: String, targetName: String): com.example.data.model.BudgetEntity? =
        budgetDao.getBudgetByIdentity(scopeType, targetName)
    suspend fun insertBudget(budget: com.example.data.model.BudgetEntity): Long {
        MutationTracker.bump()
        return budgetDao.insert(budget)
    }
    suspend fun updateBudget(budget: com.example.data.model.BudgetEntity) {
        MutationTracker.bump()
        budgetDao.update(budget)
    }
    suspend fun softDeleteBudget(id: Long) {
        MutationTracker.bump()
        budgetDao.softDeleteById(id)
    }
    suspend fun deleteBudget(budget: com.example.data.model.BudgetEntity) {
        MutationTracker.bump()
        budgetDao.delete(budget)
    }
    suspend fun clearAllBudgets() {
        MutationTracker.bump()
        budgetDao.clearAll()
    }
    suspend fun upsertBudget(budget: com.example.data.model.BudgetEntity): Long {
        MutationTracker.bump()
        val existing = budgetDao.getBudgetByIdentity(budget.scopeType, budget.targetName)
        return if (existing != null) {
            val updated = existing.copy(
                name = budget.name,
                limitAmount = budget.limitAmount,
                active = budget.active
            )
            budgetDao.update(updated)
            existing.id
        } else {
            budgetDao.insert(budget)
        }
    }

    // Commitments (Dues & Recurring)
    val activeCommitments: Flow<List<com.example.data.model.Commitment>> = commitmentDao.getActiveCommitments()
    val allCommitments: Flow<List<com.example.data.model.Commitment>> = commitmentDao.getAllCommitments()

    suspend fun getCommitmentById(id: Long): com.example.data.model.Commitment? = commitmentDao.getCommitmentById(id)
    suspend fun getAllCommitmentsList(): List<com.example.data.model.Commitment> = commitmentDao.getAllCommitmentsList()
    suspend fun getActiveCommitmentsList(): List<com.example.data.model.Commitment> = commitmentDao.getActiveCommitmentsList()
    suspend fun insertCommitment(commitment: com.example.data.model.Commitment): Long {
        MutationTracker.bump()
        return commitmentDao.insert(commitment)
    }
    suspend fun insertCommitments(commitments: List<com.example.data.model.Commitment>) {
        MutationTracker.bump()
        commitmentDao.insertAll(commitments)
    }
    suspend fun updateCommitment(commitment: com.example.data.model.Commitment) {
        MutationTracker.bump()
        commitmentDao.update(commitment)
    }
    suspend fun deleteCommitment(commitment: com.example.data.model.Commitment) {
        MutationTracker.bump()
        commitmentDao.delete(commitment)
    }
    suspend fun deleteCommitmentById(id: Long) {
        MutationTracker.bump()
        commitmentDao.deleteById(id)
    }
    suspend fun clearAllCommitments() {
        MutationTracker.bump()
        commitmentDao.clearAll()
    }

    // Accounts
    val allAccounts: Flow<List<Account>> = accountDao.getAllAccounts()
    val activeAccounts: Flow<List<Account>> = accountDao.getActiveAccounts()

    suspend fun getActiveAccountsList(): List<Account> = accountDao.getActiveAccountsList()
    suspend fun getAccountById(id: Long): Account? = accountDao.getAccountById(id)
    suspend fun getAccountByName(name: String): Account? = accountDao.getAccountByName(name)
    suspend fun insertAccount(account: Account): Long {
        MutationTracker.bump()
        return accountDao.insert(account)
    }
    suspend fun updateAccount(account: Account) {
        MutationTracker.bump()
        accountDao.update(account)
    }
    suspend fun countTransactionsForAccount(accountId: Long): Int = transactionDao.countTransactionsForAccount(accountId)
    suspend fun deleteAccount(account: Account): Boolean {
        val count = transactionDao.countTransactionsForAccount(account.id)
        if (count > 0) return false
        MutationTracker.bump()
        accountDao.delete(account)
        return true
    }

    // Categories
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()
    val activeCategories: Flow<List<Category>> = categoryDao.getActiveCategories()
    val archivedCategories: Flow<List<Category>> = categoryDao.getArchivedCategories()

    suspend fun getActiveCategoriesList(): List<Category> = categoryDao.getActiveCategoriesList()
    suspend fun getCategoryById(id: Long): Category? = categoryDao.getCategoryById(id)
    suspend fun getCategoryByName(name: String): Category? = categoryDao.getCategoryByName(name)
    suspend fun insertCategory(category: Category): Long {
        MutationTracker.bump()
        return categoryDao.insert(category)
    }
    suspend fun updateCategory(category: Category) {
        MutationTracker.bump()
        categoryDao.update(category)
    }
    suspend fun updateCategories(categories: List<Category>) {
        MutationTracker.bump()
        categoryDao.updateAll(categories)
    }
    suspend fun deleteCategory(category: Category) {
        MutationTracker.bump()
        categoryDao.delete(category)
    }

    // Custom Fields
    val allCustomFields: Flow<List<CustomField>> = customFieldDao.getAllCustomFields()

    suspend fun getCustomFieldById(id: Long): CustomField? = customFieldDao.getCustomFieldById(id)
    suspend fun insertCustomField(field: CustomField): Long {
        MutationTracker.bump()
        return customFieldDao.insert(field)
    }
    suspend fun updateCustomField(field: CustomField) {
        MutationTracker.bump()
        customFieldDao.update(field)
    }
    suspend fun deleteCustomField(field: CustomField) {
        MutationTracker.bump()
        customFieldDao.delete(field)
    }

    // Transactions
    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
    val allTransactionsChronological: Flow<List<TransactionEntity>> = transactionDao.getAllTransactionsChronological()

    suspend fun getAllTransactionsList(): List<TransactionEntity> = transactionDao.getAllTransactionsList()
    suspend fun getTransactionById(id: Long): TransactionEntity? = transactionDao.getTransactionById(id)
    suspend fun insertTransaction(transaction: TransactionEntity): Long {
        MutationTracker.bump()
        return transactionDao.insert(transaction)
    }
    suspend fun insertTransactions(transactions: List<TransactionEntity>) {
        MutationTracker.bump()
        transactions.forEach { transactionDao.insert(it) }
    }
    suspend fun updateTransaction(transaction: TransactionEntity) {
        MutationTracker.bump()
        transactionDao.update(transaction)
    }
    suspend fun deleteTransaction(transaction: TransactionEntity) {
        MutationTracker.bump()
        database.mediaAttachmentDao().deleteByTxnId(transaction.id)
        transactionDao.delete(transaction)
    }
    suspend fun deleteTransactionById(id: Long) {
        MutationTracker.bump()
        database.mediaAttachmentDao().deleteByTxnId(id)
        transactionDao.deleteById(id)
    }
    suspend fun deleteAllTransactions() {
        MutationTracker.bump()
        transactionDao.clearAll()
    }

    suspend fun seedSampleData() = SampleDataSeeder.seedSampleData(database)

    suspend fun seedCleanDefaults() = SampleDataSeeder.seedCleanDefaults(database)

    suspend fun checkAndSeedIfEmpty() {
        val accounts = accountDao.getAllAccounts().first()
        if (accounts.isEmpty()) {
            seedCleanDefaults()
        }
    }

    suspend fun checkAndRestoreOrSeed(
        context: android.content.Context,
        settingsRepository: com.example.data.datastore.AppSettingsRepository? = null
    ): Boolean {
        val result = com.example.importbackup.UnifiedCsvStorage.checkAndRestoreOrSeed(context, this, settingsRepository)
        val isDemo = settingsRepository?.settingsFlow?.first()?.demoMode ?: false
        if (!isDemo) {
            val mediaRepo = MediaRepository(context, database)
            mediaRepo.reconcileOrphans(transactionDao, isDemoMode = false)
            mediaRepo.wipeStaging()
        }
        return result
    }

    suspend fun hasExistingUserData(): Boolean {
        val txs = transactionDao.getAllTransactions().first()
        val accs = accountDao.getAllAccounts().first()
        return txs.isNotEmpty() || accs.isNotEmpty()
    }
    
    suspend fun clearAllData() {
        MutationTracker.bump()
        transactionDao.clearAll()
        accountDao.clearAll()
        categoryDao.clearAll()
        customFieldDao.clearAll()
        budgetDao.clearAll()
        commitmentDao.clearAll()
    }
}
