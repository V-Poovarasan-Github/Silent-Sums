package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.datastore.AppSettingsRepository
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.data.seed.SampleDataSeeder
import com.example.importbackup.UnifiedCsvStorage
import com.example.ui.ledger.LedgerViewModel
import com.example.ui.settings.SettingsViewModel
import com.example.worker.AutoBackupWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase2_9_2_DemoFirewallCorrectnessTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: FinanceRepository
    private lateinit var settingsRepository: AppSettingsRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(database)
        settingsRepository = AppSettingsRepository(context)
    }

    @After
    fun tearDown() {
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        runBlocking {
            settingsRepository.setDemoMode(false)
        }
        database.close()
    }

    // =========================================================================
    // ITEM 1: Service-Layer Functional Guards (direct invocation bypassing UI)
    // =========================================================================

    @Test
    fun testExportCsvBackup_blockedDirectlyInDemoMode() = runBlocking {
        settingsRepository.setDemoMode(true)
        val viewModel = SettingsViewModel(repository, settingsRepository, database)

        viewModel.exportCsvBackup(context)

        val status = withTimeout(5000) { viewModel.statusMessage.first() }
        assertEquals("Unavailable in sample mode", status)
    }

    @Test
    fun testShareBackupFile_blockedDirectlyInDemoMode() = runBlocking {
        settingsRepository.setDemoMode(true)
        val viewModel = SettingsViewModel(repository, settingsRepository, database)

        viewModel.shareBackupFile(context)

        val status = withTimeout(5000) { viewModel.statusMessage.first() }
        assertEquals("Unavailable in sample mode", status)
    }

    @Test
    fun testRestoreFromInternalSafetyNet_blockedDirectlyInDemoMode() = runBlocking {
        settingsRepository.setDemoMode(true)
        val viewModel = SettingsViewModel(repository, settingsRepository, database)

        // Seed a real txn first
        val accId = repository.insertAccount(Account(name = "Bank", openingBalance = 100.0, accountType = "BANK"))
        val catId = repository.insertCategory(Category(name = "Food", color = "#FF0000"))
        repository.insertTransaction(
            TransactionEntity(date = System.currentTimeMillis(), type = "EXPENSE", amount = 50.0, fromAccountId = accId, categoryId = catId, description = "Test")
        )
        val initialTxCount = repository.allTransactions.first().size

        viewModel.restoreFromInternalSafetyNet(context)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        val status = withTimeout(5000) { viewModel.statusMessage.first() }
        assertEquals("Unavailable in sample mode", status)
        assertEquals("Database must not be mutated when blocked", initialTxCount, repository.allTransactions.first().size)
    }

    @Test
    fun testRestoreFromStoredCsv_blockedDirectlyInDemoMode() = runBlocking {
        settingsRepository.setDemoMode(true)
        val viewModel = SettingsViewModel(repository, settingsRepository, database)

        viewModel.restoreFromStoredCsv(context)

        val status = withTimeout(5000) { viewModel.statusMessage.first() }
        assertEquals("Unavailable in sample mode", status)
    }

    @Test
    fun testRestoreFromAutoSave_blockedDirectlyInDemoMode() = runBlocking {
        settingsRepository.setDemoMode(true)
        val viewModel = SettingsViewModel(repository, settingsRepository, database)

        viewModel.restoreFromAutoSave(context)

        val status = withTimeout(5000) { viewModel.statusMessage.first() }
        assertEquals("Unavailable in sample mode", status)
    }

    @Test
    fun testRunAutoSaveNow_blockedDirectlyInDemoMode() = runBlocking {
        settingsRepository.setDemoMode(true)
        val viewModel = SettingsViewModel(repository, settingsRepository, database)

        viewModel.runAutoSaveNow(context)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        val status = withTimeout(5000) { viewModel.statusMessage.first() }
        assertEquals("Unavailable in sample mode", status)
    }

    @Test
    fun testClearAllData_blockedDirectlyInDemoMode() = runBlocking {
        val accId = repository.insertAccount(Account(name = "Bank", openingBalance = 100.0, accountType = "BANK"))
        val catId = repository.insertCategory(Category(name = "Food", color = "#FF0000"))
        repository.insertTransaction(
            TransactionEntity(date = System.currentTimeMillis(), type = "EXPENSE", amount = 25.0, fromAccountId = accId, categoryId = catId, description = "Lunch")
        )
        assertEquals(1, repository.allTransactions.first().size)

        settingsRepository.setDemoMode(true)
        val viewModel = SettingsViewModel(repository, settingsRepository, database)

        viewModel.clearAllData()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        val status = withTimeout(5000) { viewModel.statusMessage.first() }
        assertEquals("Unavailable in sample mode", status)
        assertEquals("Ledger transactions must NOT be cleared in demo mode", 1, repository.allTransactions.first().size)
    }

    @Test
    fun testImportAndPreviewCsv_blockedDirectlyInDemoMode() = runBlocking {
        settingsRepository.setDemoMode(true)
        val viewModel = SettingsViewModel(repository, settingsRepository, database)

        val dummyCsv = "dummy csv content".byteInputStream()
        viewModel.previewCsv(dummyCsv)

        val status1 = withTimeout(5000) { viewModel.statusMessage.first() }
        assertEquals("Unavailable in sample mode", status1)

        val dummyCsv2 = "dummy csv content".byteInputStream()
        viewModel.commitCsvImport(dummyCsv2)

        val status2 = withTimeout(5000) { viewModel.statusMessage.first() }
        assertEquals("Unavailable in sample mode", status2)
    }

    // =========================================================================
    // ITEM 2: AutoBackupWorker & LifeCycle Guards
    // =========================================================================

    @Test
    fun testAutoBackupWorker_doWorkReturnsSuccessImmediatelyInDemoMode() = runBlocking {
        settingsRepository.setDemoMode(true)
        settingsRepository.setAutoBackupEnabled(true)

        val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null) as sun.misc.Unsafe
        val params = unsafe.allocateInstance(WorkerParameters::class.java) as WorkerParameters
        val worker = AutoBackupWorker(context, params)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        val internalFile = File(context.filesDir, "backups/auto_backup.csv")
        assertFalse("AutoBackupWorker must not write internal safety-net backup in demo mode", internalFile.exists() && internalFile.length() > 0)
    }

    @Test
    fun testAutoBackupWorker_runSynchronouslyPausedInDemoMode() = runBlocking {
        settingsRepository.setDemoMode(true)

        val resultMsg = AutoBackupWorker.runSynchronously(context, database, repository, settingsRepository)
        assertEquals("Auto-save paused in demo mode", resultMsg)
    }

    // =========================================================================
    // ITEM 4: Both doors engage (Settings loadSampleData sets demoMode = true)
    //         and Cancel lifts guards immediately
    // =========================================================================

    @Test
    fun testBothDoorsEngage_SettingsLoadSampleSetsDemoMode() = runBlocking {
        settingsRepository.setDemoMode(false)
        val viewModel = SettingsViewModel(repository, settingsRepository, database)

        viewModel.loadSampleData()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        val msg = withTimeout(5000) { viewModel.statusMessage.first() }
        assertEquals("Sample ledger data loaded successfully!", msg)

        assertTrue("Settings loadSampleData MUST set demoMode to true", settingsRepository.settingsFlow.first().demoMode)
    }

    @Test
    fun testCancelDemo_liftsAllGuardsImmediately() = runBlocking {
        settingsRepository.setDemoMode(true)
        val viewModel = SettingsViewModel(repository, settingsRepository, database)

        // Cancel demo
        settingsRepository.setDemoMode(false)
        assertFalse(settingsRepository.settingsFlow.first().demoMode)

        // Seed 1 transaction
        val accId = repository.insertAccount(Account(name = "Bank", openingBalance = 100.0, accountType = "BANK"))
        val catId = repository.insertCategory(Category(name = "Food", color = "#FF0000"))
        repository.insertTransaction(
            TransactionEntity(date = System.currentTimeMillis(), type = "EXPENSE", amount = 25.0, fromAccountId = accId, categoryId = catId, description = "Coffee")
        )
        assertEquals(1, repository.allTransactions.first().size)

        // Now clearAllData works
        viewModel.clearAllData()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        val status = withTimeout(5000) { viewModel.statusMessage.first() }
        assertEquals("All ledger transactions cleared.", status)
        assertEquals(0, repository.allTransactions.first().size)
    }

    // =========================================================================
    // STORAGE-LAYER NULL FALLBACK & BYPASS TESTS (Amendment 1)
    // =========================================================================

    @Test
    fun testSaveToDeviceCsv_nullSettingsCaller_blockedInDemoMode() = runBlocking {
        settingsRepository.setDemoMode(true)
        val accId = repository.insertAccount(Account(name = "Bank", openingBalance = 100.0, accountType = "BANK"))
        val catId = repository.insertCategory(Category(name = "Food", color = "#FF0000"))
        val tx = TransactionEntity(date = System.currentTimeMillis(), type = "EXPENSE", amount = 10.0, fromAccountId = accId, categoryId = catId, description = "Test")
        val accounts = repository.allAccounts.first()
        val categories = repository.allCategories.first()

        // Caller passes settings == null (resolves internally from AppSettingsRepository(context))
        val result = UnifiedCsvStorage.saveToDeviceCsv(
            context = context,
            transactions = listOf(tx),
            accounts = accounts,
            categories = categories,
            commitments = emptyList()
        )

        assertEquals("Export paused: Demo mode active", result)
    }

    @Test
    fun testLedgerViewModel_exportFilteredCsv_blockedInDemoMode() = runBlocking {
        settingsRepository.setDemoMode(true)
        val accId = repository.insertAccount(Account(name = "Bank", openingBalance = 100.0, accountType = "BANK"))
        val catId = repository.insertCategory(Category(name = "Food", color = "#FF0000"))
        repository.insertTransaction(
            TransactionEntity(date = System.currentTimeMillis(), type = "EXPENSE", amount = 10.0, fromAccountId = accId, categoryId = catId, description = "Test")
        )
        val ledgerVm = LedgerViewModel(repository, settingsRepository)

        val result = ledgerVm.exportFilteredCsv(context)

        assertEquals("Export paused: Demo mode active", result)
    }
}
