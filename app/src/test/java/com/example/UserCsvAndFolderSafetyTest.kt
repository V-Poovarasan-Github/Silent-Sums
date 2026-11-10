package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.datastore.AppSettingsRepository
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.importbackup.CsvBackupManager
import com.example.importbackup.MediaStoreWriter
import com.example.importbackup.UnifiedCsvStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import androidx.core.content.FileProvider
import com.example.importbackup.CsvImporter
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserCsvAndFolderSafetyTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: FinanceRepository
    private lateinit var settingsRepository: AppSettingsRepository

    // The exact CSV shared by the user in the prompt
    private val currentUploadedCsv = """
#FORMAT=2
Date,Type,Amount,FromAccount,ToAccount,Category,Description,Notes
2026-09-18 19:25,EXPENSE,269.36,ICICI Credit,,Loan,"Washing Machine, 17th Sept",additional chargers
2026-09-18 21:58,EXPENSE,605.80,ICICI Credit,,Health & Wellness,Tablets for Mom,At JayaDeva Hospital [+11.80 Card charges]
2026-09-18 21:59,EXPENSE,960.41,ICICI Credit,,Transport & Fuel,FastTag for Car,13 Sept
2026-09-18 22:00,EXPENSE,170.00,ICICI Credit,,Food & Dining,Tea,"While returning Bangalore, 13th Sept"
2026-09-18 22:00,EXPENSE,1674.53,ICICI Credit,,Transport & Fuel,Petrol for Car,13 Sept
2026-09-18 22:04,EXPENSE,247.49,ICICI Credit,,Food & Dining,"BlinkIt Snacks Order, 16th Sept","199 + 48.49, Unibic Cake and Stuff"
2026-09-18 22:06,EXPENSE,960.82,ICICI Credit,,Bills & Utilities,"TV Bill, 17th Sept","TV Bill, 17th Sept"
2026-09-19 10:34,EXPENSE,150.00,ICICI,,Food & Dining,2 * Idle Parcel,2 * Idle Parcel
2026-09-19 11:23,EXPENSE,1400.00,Cash,,Others,Shravana Shanivara,Includes Pooja related items and Groceries.
2026-09-19 18:58,EXPENSE,200.00,SBI Amma,,Beverages/Snacks,5 * Grape Juice,At Gopi Ice Cream
2026-09-19 20:49,EXPENSE,12.00,KBL,,Others,Xerox,Xerox
2026-09-21 11:16,EXPENSE,37.00,ICICI,,Health & Wellness,Clobetasol propionate Ointment,Clobetasol propionate Ointment
2026-09-21 11:25,EXPENSE,200.00,Cash,,Transport & Fuel,Petrol for Dad's Vehicle.,Petrol for Dad's Vehicle.
2026-09-21 12:17,EXPENSE,60.00,UBI,,Beverages/Snacks,SugarCane Juice * 2,SugarCane Juice * 2
2026-09-21 19:17,INCOME,5000.00,,Cash,Salary & Income,Dad's Salary,week 3 Payment.
2026-09-22 18:07,INCOME,11200.00,,Cash,Salary & Income,Dad's Salary,kept inside Pooja Room.
2026-09-22 19:16,EXPENSE,598.00,ICICI Credit,,Groceries & Shopping,Bought Shirt & Pant,From Flipkart
2026-09-22 20:46,INCOME,1500.00,,KBL,Salary & Income,From Kiran,To return Wonderla money to friends.
2026-09-22 20:47,EXPENSE,1345.00,KBL,,Entertainment,Wonderla Expenses,sent to Suprith
2026-09-22 21:53,INCOME,268.00,,KBL,Salary & Income,Flipkart Refund,Necklace
2026-09-23 20:48,EXPENSE,189.00,KBL,,Bills & Utilities,Mom's Mobile Recharge,
2026-09-23 21:11,EXPENSE,100.00,Cash,,Dad's Expense,,
2026-09-23 21:58,EXPENSE,100.00,Cash,,Beverages/Snacks,Cream bun + Orange Juice,
2026-09-24 18:59,EXPENSE,220.00,Cash,,Groceries & Shopping,Cooking Oil + Milk,
2026-09-26 19:05,EXPENSE,100.00,Cash,,Transport & Fuel,Jupiter 125CC,
2026-09-26 19:05,EXPENSE,100.00,Cash,,Beverages/Snacks,Ice Cream,
2026-09-26 19:06,EXPENSE,100.00,Cash,,Dad's Expense,Dad's Expenses,
2026-09-26 19:06,EXPENSE,29.00,KBL,,Bills & Utilities,Data AddOn 2GB,M51
2026-09-26 19:07,EXPENSE,100.00,ICICI Credit,,Others,Xerox + File,At PES College
2026-09-26 19:08,INCOME,7000.00,,Cash,Salary & Income,Dad's Salary,
2026-09-26 19:17,EXPENSE,80.00,Cash,,Dad's Expense,Dad's Expenses,80 was missing in total So adding now.
2026-09-27 13:47,EXPENSE,1092.34,ICICI Credit,,Entertainment,Paradise Movie * 4,
2026-09-27 15:07,EXPENSE,200.00,Cash,,Transport & Fuel,Dad's Vehicle,
2026-09-27 15:08,EXPENSE,300.00,Cash,,Others,Jyothishya For me,
2026-09-27 15:23,EXPENSE,296.02,ICICI Credit,,Entertainment,Paradise Movie * 1,
2026-09-27 20:01,EXPENSE,50.00,Cash,,Others,Parking fees At YGR Mall,
2026-09-27 21:12,EXPENSE,300.00,Cash,,Food & Dining,Gobi and Egg Rice (2+2),
2026-09-28 07:22,EXPENSE,200.00,Cash,,Groceries & Shopping,Milk and Groceries,
2026-09-28 20:32,EXPENSE,100.00,Cash,,Groceries & Shopping,Vegetables Shopping,
2026-09-28 20:33,EXPENSE,500.00,Cash,,Others,For Permanent Residential Certificate,At BSK Nadakacheri
2026-09-28 20:45,INCOME,600.00,,KBL,Entertainment,Cinema Money for PARADISE,from Sister in law
2026-09-29 09:01,EXPENSE,200.00,Cash,,Dad's Expense,,
2026-09-30 09:15,EXPENSE,2500.00,Cash,,Groceries & Shopping,"Rice, Dals and Household expenses",
2026-10-01 07:44,INCOME,15.00,,ICICI,Salary & Income,Bank Interest,
2026-10-01 09:04,EXPENSE,100.00,Cash,,Groceries & Shopping,"Milk, Bread, coriander",
2026-10-01 10:14,EXPENSE,189.00,KBL,,Bills & Utilities,Mobile Recharge,For me
2026-10-01 19:12,TRANSFER,600.00,KBL,ICICI Credit,,Transfer,
2026-10-02 07:45,INCOME,41061.00,,ICICI,Salary & Income,Salary,
2026-10-02 07:59,INCOME,508.00,,SBI Amma,Others,Suzuki Booking Refund,
2026-10-02 08:02,TRANSFER,500.00,SBI Amma,KBL,,Transfer,
2026-10-02 08:02,TRANSFER,500.00,KBL,ICICI Credit,,Transfer,
2026-10-02 08:08,TRANSFER,11122.00,ICICI,KBL,,Transfer,For Car Loan

#ACCOUNTS
Name,Type,Opening,Archived
Cash,CASH,9000.00,false
KBL,BANK,44.40,false
ICICI,BANK,258.47,false
ICICI Credit,CREDIT_CARD,0.00,false
SBI Appa,BANK,219.05,false
SBI Amma,BANK,355.23,false
UBI,BANK,1074.29,false

#CATEGORIES
Name,Color,Archived
Beverages/Snacks,#6366F1,false
Food & Dining,#EF4444,false
Loan,#EF4444,false
Groceries & Shopping,#F59E0B,false
Transport & Fuel,#3B82F6,false
Bills & Utilities,#8B5CF6,false
Entertainment,#EC4899,false
Health & Wellness,#06B6D4,false
Salary & Income,#10B981,false
Dad's Expense,#8B5CF6,false
Others,#64748B,false
Investments,#059669,false

#SETTINGS
currencySymbol=₹
monthStartDay=1
theme=AUTO
hideBalances=false
soundHapticsEnabled=false

#COMMITMENTS (Dues & Recurring)
type,name,amount,direction,dayOfMonth,dueDate,accountName,categoryName,active
RECURRING,House Loan,25000.00,OUTGOING,4,,ICICI,Loan,true
RECURRING,Car Loan,11122.00,OUTGOING,10,,KBL,Loan,true
IOU,To Kiran,1500.00,OUTGOING,,1793503800121,KBL,Others,true
    """.trimIndent()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(database)
        settingsRepository = AppSettingsRepository(context)
        runBlocking {
            settingsRepository.setDemoMode(false)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            settingsRepository.setDemoMode(false)
        }
        database.close()
    }

    @Test
    fun testUserSharedCsv_all52TransactionsRestoredAccurately() {
        runBlocking {
            val stream = ByteArrayInputStream(currentUploadedCsv.toByteArray(Charsets.UTF_8))
            val result = UnifiedCsvStorage.restoreFromInputStream(
                repository = repository,
                inputStream = stream,
                clearExisting = true,
                settingsRepository = settingsRepository
            )

            // 1. Assert exactly 52 transactions were restored
            assertEquals(52, result.inserted)
            assertEquals(0, result.skipped)
            assertEquals(52, result.total)

            val txList = repository.allTransactions.first()
            assertEquals(52, txList.size)

            // 2. Assert all 7 accounts were restored with correct types and opening balances
            val accounts = repository.allAccounts.first()
            assertEquals(7, accounts.size)

            val cash = accounts.firstOrNull { it.name == "Cash" }
            assertNotNull(cash)
            assertEquals("CASH", cash!!.accountType)
            assertEquals(9000.00, cash.openingBalance, 0.001)

            val kbl = accounts.firstOrNull { it.name == "KBL" }
            assertNotNull(kbl)
            assertEquals("BANK", kbl!!.accountType)
            assertEquals(44.40, kbl.openingBalance, 0.001)

            val iciciCredit = accounts.firstOrNull { it.name == "ICICI Credit" }
            assertNotNull(iciciCredit)
            assertEquals("CREDIT_CARD", iciciCredit!!.accountType)
            assertEquals(0.00, iciciCredit.openingBalance, 0.001)

            val sbiAmma = accounts.firstOrNull { it.name == "SBI Amma" }
            assertNotNull(sbiAmma)
            assertEquals(355.23, sbiAmma!!.openingBalance, 0.001)

            // 3. Assert all 12 categories were restored
            val categories = repository.allCategories.first()
            assertEquals(12, categories.size)
            assertTrue(categories.any { it.name == "Beverages/Snacks" })
            assertTrue(categories.any { it.name == "Dad's Expense" })
            assertTrue(categories.any { it.name == "Salary & Income" })

            // 4. Assert all 3 commitments restored
            val commitments = repository.allCommitments.first()
            assertEquals(3, commitments.size)
            val houseLoan = commitments.firstOrNull { it.name == "House Loan" }
            assertNotNull(houseLoan)
            assertEquals(25000.00, houseLoan!!.amount, 0.001)
            assertEquals(4, houseLoan.dayOfMonth)

            val carLoan = commitments.firstOrNull { it.name == "Car Loan" }
            assertNotNull(carLoan)
            assertEquals(11122.00, carLoan!!.amount, 0.001)
            assertEquals(10, carLoan.dayOfMonth)

            val toKiran = commitments.firstOrNull { it.name == "To Kiran" }
            assertNotNull(toKiran)
            assertEquals(1500.00, toKiran!!.amount, 0.001)
            assertEquals("IOU", toKiran.type)

            // 5. Assert specific critical transactions
            // Latest transfer: ICICI -> KBL, 11122.00, For Car Loan
            val carLoanTransfer = txList.firstOrNull { it.amount == 11122.00 && it.type == "TRANSFER" }
            assertNotNull(carLoanTransfer)
            assertEquals("For Car Loan", carLoanTransfer!!.notes)
            val icici = accounts.first { it.name == "ICICI" }
            assertEquals(icici.id, carLoanTransfer.fromAccountId)
            assertEquals(kbl.id, carLoanTransfer.toAccountId)

            // Salary income: 41061.00 into ICICI
            val salaryTx = txList.firstOrNull { it.amount == 41061.00 && it.type == "INCOME" }
            assertNotNull(salaryTx)
            assertEquals(icici.id, salaryTx!!.toAccountId)

            // Suzuki booking refund: 508.00 into SBI Amma
            val refundTx = txList.firstOrNull { it.amount == 508.00 && it.type == "INCOME" }
            assertNotNull(refundTx)
            assertEquals(sbiAmma.id, refundTx!!.toAccountId)
        }
    }

    @Test
    fun testFolderSafety_autoSaveSubfolderRoundTrip() {
        runBlocking {
            // Write rolling auto-save into auto subfolder
            val (savedUri, usedSaf) = MediaStoreWriter.writeRollingAutoSave(context, currentUploadedCsv)
            assertNotNull(savedUri)

            // Verify auto-save file presence
            val hasAuto = UnifiedCsvStorage.hasAutoSave(context)
            assertTrue("Auto-save file must be detected", hasAuto)

            // Restore from auto-save
            val result = UnifiedCsvStorage.restoreFromAutoSave(context, repository, clearExisting = true, settingsRepository = settingsRepository)
            assertEquals(52, result.inserted)
            assertEquals(52, repository.allTransactions.first().size)
        }
    }

    @Test
    fun testFolderSafety_filteredExportAccumulatesWithoutLoss() {
        runBlocking {
            // Preload data
            val stream = ByteArrayInputStream(currentUploadedCsv.toByteArray(Charsets.UTF_8))
            UnifiedCsvStorage.restoreFromInputStream(repository, stream, clearExisting = true)

            val txs = repository.allTransactions.first()
            val accounts = repository.allAccounts.first()
            val categories = repository.allCategories.first()

            // Export filtered transactions
            val uri1 = UnifiedCsvStorage.exportFilteredLedgerCsv(context, txs.take(10), accounts, categories)
            assertNotNull(uri1)

            val path2 = UnifiedCsvStorage.exportFilteredTransactions(context, txs.takeLast(10), accounts, categories)
            assertTrue(path2.contains("downloads/filtered_export_"))

            // Verify unified storage detects exports
            assertTrue(UnifiedCsvStorage.hasStoredCsv(context))
        }
    }

    @Test
    fun testEmptyDatabaseAutoRecoveryOnStartup() {
        runBlocking {
            // Write auto-save to storage
            MediaStoreWriter.writeRollingAutoSave(context, currentUploadedCsv)

            // Simulate empty app startup
            val emptyDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
            val emptyRepo = FinanceRepository(emptyDb)
            assertEquals(0, emptyRepo.allAccounts.first().size)

            // Launch startup check
            val restored = emptyRepo.checkAndRestoreOrSeed(context, settingsRepository)
            assertTrue("Should auto-recover user data when device backup exists", restored)
            assertEquals(52, emptyRepo.allTransactions.first().size)
            assertEquals(7, emptyRepo.allAccounts.first().size)

            emptyDb.close()
        }
    }

    @Test
    fun testDeduplication_idempotentSecondRestore() {
        runBlocking {
            val stream1 = ByteArrayInputStream(currentUploadedCsv.toByteArray(Charsets.UTF_8))
            val res1 = UnifiedCsvStorage.restoreFromInputStream(repository, stream1, clearExisting = true)
            assertEquals(52, res1.inserted)

            // Second pass over existing data
            val stream2 = ByteArrayInputStream(currentUploadedCsv.toByteArray(Charsets.UTF_8))
            val res2 = UnifiedCsvStorage.restoreFromInputStream(repository, stream2, clearExisting = false)
            assertEquals(0, res2.inserted)
            assertEquals(52, res2.skipped)
            assertEquals(52, repository.allTransactions.first().size)
        }
    }

    @Test
    fun testExistingDataSafety_whenFileInPaisaTrackOrAlternateFolderNames() {
        runBlocking {
            // Simulate an existing installation that placed backups under "PaisaTrack/auto_save/backup.csv"
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val paisaAutoDir = File(downloadsDir, "PaisaTrack/auto_save")
            paisaAutoDir.mkdirs()
            val backupFile = File(paisaAutoDir, "backup.csv")
            backupFile.writeText(currentUploadedCsv)

            assertTrue("Should detect auto-save in alternate folder PaisaTrack/auto_save", UnifiedCsvStorage.hasAutoSave(context))
            assertTrue("Should detect stored CSV in alternate folder PaisaTrack", UnifiedCsvStorage.hasStoredCsv(context))

            // Test auto-recovery on empty DB
            val emptyDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
            val emptyRepo = FinanceRepository(emptyDb)
            val recovered = emptyRepo.checkAndRestoreOrSeed(context, settingsRepository)
            assertTrue("Auto-recovery must restore data from PaisaTrack folder", recovered)
            assertEquals(52, emptyRepo.allTransactions.first().size)

            emptyDb.close()
            backupFile.delete()
            paisaAutoDir.delete()
        }
    }

    @Test
    fun testExistingDataSafety_whenFileInFilteredExportsFolder() {
        runBlocking {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val exportDir = File(downloadsDir, "PaisaTrack/filtered_exports")
            exportDir.mkdirs()
            val exportFile = File(exportDir, "filtered_export_20261002_080200.csv")
            exportFile.writeText(currentUploadedCsv)

            val foundFile = UnifiedCsvStorage.findAnyStoredExportFile(context)
            assertNotNull("Should find export in PaisaTrack/filtered_exports", foundFile)
            assertEquals("filtered_export_20261002_080200.csv", foundFile!!.name)

            exportFile.delete()
            exportDir.delete()
        }
    }

    @Test
    fun testPreservePreExistingDbData_whenUpgradingOrInstallingOverExistingApp() {
        runBlocking {
            // Restore initial 52 transactions into repository
            val stream = ByteArrayInputStream(currentUploadedCsv.toByteArray(Charsets.UTF_8))
            UnifiedCsvStorage.restoreFromInputStream(repository, stream, clearExisting = true)
            assertEquals(52, repository.allTransactions.first().size)
            val initialAccounts = repository.allAccounts.first()
            assertEquals(7, initialAccounts.size)

            // When app boots up after upgrading (database already populated)
            val restored = repository.checkAndRestoreOrSeed(context, settingsRepository)
            // checkAndRestoreOrSeed should return false because DB is not empty, preserving existing data completely
            assertEquals(false, restored)
            assertEquals(52, repository.allTransactions.first().size)
            assertEquals(7, repository.allAccounts.first().size)
        }
    }

    @Test
    fun testSkipEmptyOrphanAndRestoreFromRealBackup() {
        runBlocking {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val autoDir = File(downloadsDir, "ExpenseTracker/auto")
            autoDir.mkdirs()

            // 1. Real backup with 5.44 KB of data (52 transactions)
            val realBackup = File(autoDir, "backup.csv")
            realBackup.writeText(currentUploadedCsv)
            realBackup.setLastModified(1000000L)

            // 2. Empty orphan backup (1).csv with only 177 bytes and NO transactions, newer timestamp
            val emptyOrphan = File(autoDir, "backup (1).csv")
            emptyOrphan.writeText("#FORMAT=2\nDate,Type,Amount,FromAccount,ToAccount,Category,Description,Notes\n#ACCOUNTS\n#CATEGORIES\n")
            emptyOrphan.setLastModified(2000000L)

            // Verify openStoredCsvInputStream skips emptyOrphan and opens realBackup
            val stream = UnifiedCsvStorage.openStoredCsvInputStream(context)
            assertNotNull(stream)

            val result = UnifiedCsvStorage.restoreFromInputStream(repository, stream!!, clearExisting = true)
            assertEquals("Must restore 52 transactions from real backup, not 0 from empty orphan", 52, result.inserted)

            realBackup.delete()
            emptyOrphan.delete()
            autoDir.delete()
        }
    }

    @Test
    fun testOnboardingBypass_whenUserDataExists() {
        runBlocking {
            val emptyDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
            val repo = FinanceRepository(emptyDb)
            assertFalse("Fresh DB should report no existing user data", repo.hasExistingUserData())

            // Seed clean defaults
            repo.seedCleanDefaults()
            assertTrue("Seeded DB should report existing user data", repo.hasExistingUserData())

            // Once data exists, onboarding completed can be set or auto-bypassed
            settingsRepository.setOnboardingCompleted(true)
            val settings = settingsRepository.settingsFlow.first()
            assertTrue("Onboarding must be marked completed", settings.onboardingCompleted)

            emptyDb.close()
        }
    }

    @Test
    fun testCorruptCsvImport_inlineError_dbUntouched() {
        runBlocking {
            val emptyDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
            val repo = FinanceRepository(emptyDb)
            val initialTxCount = repo.allTransactions.first().size
            assertEquals(0, initialTxCount)

            // Garbage CSV with no valid rows or header
            val garbageCsv = "Random garbage text\nAnother line without headers\n123,456"
            val stream = ByteArrayInputStream(garbageCsv.toByteArray(Charsets.UTF_8))

            var caughtException: Exception? = null
            try {
                UnifiedCsvStorage.restoreFromInputStream(repo, stream, clearExisting = true, settingsRepository = settingsRepository)
            } catch (e: Exception) {
                caughtException = e
            }

            assertNotNull("Corrupt CSV must throw an exception to surface inline error", caughtException)
            assertEquals("DB must remain completely untouched after corrupt CSV attempt", 0, repo.allTransactions.first().size)
            assertEquals("Accounts must remain untouched", 0, repo.allAccounts.first().size)

            emptyDb.close()
        }
    }

    @Test
    fun testTier1ChecksumAndHeaderValidation_rejectsCorruptOrPartialBackups() {
        val validContent = "#FORMAT=2\nDate,Type,Amount,FromAccount,ToAccount,Category,Description,Notes\n2026-09-18 19:25,EXPENSE,269.36,Cash,,Others,Test,Notes\n"
        assertTrue("Valid content with #FORMAT= and columns must pass validation", MediaStoreWriter.isValidInternalBackup(validContent))

        val missingFormat = "Date,Type,Amount,FromAccount,ToAccount,Category,Description,Notes\n2026-09-18 19:25,EXPENSE,269.36,Cash,,Others,Test,Notes\n"
        assertFalse("Missing #FORMAT= must be rejected", MediaStoreWriter.isValidInternalBackup(missingFormat))

        val missingColumns = "#FORMAT=2\nColA,ColB,ColC\n1,2,3\n"
        assertFalse("Missing required columns must be rejected", MediaStoreWriter.isValidInternalBackup(missingColumns))

        val blankContent = ""
        assertFalse("Blank content must be rejected", MediaStoreWriter.isValidInternalBackup(blankContent))
    }

    @Test
    fun testSafFallback_securityException_fallsBackToDownloadsAndUpdatesStatusKeys() {
        runBlocking {
            val invalidTreeUri = "content://com.android.externalstorage.documents/tree/deleted_nonexistent_folder"
            val settings = settingsRepository.settingsFlow.first().copy(autoSaveTreeUri = invalidTreeUri)

            val acc = Account(id = 1L, name = "ICICI", accountType = "BANK", openingBalance = 1000.0)
            val cat = Category(id = 1L, name = "Food", color = "#3B82F6")
            val tx = TransactionEntity(id = 1L, date = System.currentTimeMillis(), type = "EXPENSE", amount = 150.0, fromAccountId = 1L, categoryId = 1L, description = "Lunch", notes = "")

            // Calling saveToDeviceCsv with inaccessible SAF tree
            val resultPath = UnifiedCsvStorage.saveToDeviceCsv(
                context = context,
                transactions = listOf(tx),
                accounts = listOf(acc),
                categories = listOf(cat),
                settings = settings,
                settingsRepository = settingsRepository
            )

            // Verify fallback to Downloads
            assertTrue("Must fall back to Downloads on SecurityException", resultPath.contains("Downloads"))

            // Verify settings keys surfaced failure
            val updatedSettings = settingsRepository.settingsFlow.first()
            assertFalse("lastAutoSaveSuccess must be false on failure", updatedSettings.lastAutoSaveSuccess)
            assertTrue("lastAutoSaveStatusText must report fallback or permission lost", updatedSettings.lastAutoSaveStatusText.contains("fell back to Downloads") || updatedSettings.lastAutoSaveStatusText.contains("inaccessible"))
            assertTrue("autoSaveFailingSince must be non-zero", updatedSettings.autoSaveFailingSince > 0L)
        }
    }

    @Test
    fun testRestoreFromInternalSafetyNet_recoversDeletedTransactions() {
        runBlocking {
            // Write valid auto-backup to internal storage safety net
            val (uri, _) = MediaStoreWriter.writeRollingAutoSave(context, currentUploadedCsv)
            assertNotNull(uri)

            // Restore into repo
            val stream = ByteArrayInputStream(currentUploadedCsv.toByteArray(Charsets.UTF_8))
            UnifiedCsvStorage.restoreFromInputStream(repository, stream, clearExisting = true, settingsRepository = settingsRepository)
            assertEquals(52, repository.allTransactions.first().size)

            // Simulate user deleting transactions in-app
            repository.deleteAllTransactions()
            assertEquals(0, repository.allTransactions.first().size)

            // Read internal safety-net file and restore
            val internalFile = File(context.filesDir, "backups/auto_backup.csv")
            assertTrue("Internal safety-net backup file must exist", internalFile.exists() && internalFile.length() > 0)
            val internalStream = ByteArrayInputStream(internalFile.readText().toByteArray(Charsets.UTF_8))
            val restoreResult = UnifiedCsvStorage.restoreFromInputStream(repository, internalStream, clearExisting = true, settingsRepository = settingsRepository)

            assertEquals("Internal safety-net must restore all 52 transactions", 52, restoreResult.inserted)
            assertEquals("Repository must have all 52 transactions recovered", 52, repository.allTransactions.first().size)
        }
    }

    @Test
    fun testCsvPreview_showsCorrectRowCountAndDateRange() {
        val stream = ByteArrayInputStream(currentUploadedCsv.toByteArray(Charsets.UTF_8))
        val preview = CsvImporter.previewCsv(stream, emptyList(), emptyList())

        assertEquals(52, preview.validRows)
        assertTrue("Date range must be non-empty", preview.dateRange.isNotBlank())
        assertTrue("Date range must include starting year 2026", preview.dateRange.contains("2026"))
        assertTrue("Date range must include separator 'to'", preview.dateRange.contains(" to "))
    }

    @Test
    fun testShareCsvViaFileProvider_generatesValidContentUri() {
        try {
            val field = FileProvider::class.java.getDeclaredField("sCache")
            field.isAccessible = true
            (field.get(null) as? MutableMap<*, *>)?.clear()
        } catch (_: Exception) {}

        val shareDir = File(context.cacheDir, "share").apply { mkdirs() }
        val cacheFile = File(shareDir, "test_share.csv")
        cacheFile.writeText(currentUploadedCsv)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
        assertNotNull(uri)
        assertTrue("FileProvider URI must start with content://", uri.toString().startsWith("content://"))
        assertTrue("FileProvider URI must have fileprovider authority", uri.authority?.contains("fileprovider") == true)

        cacheFile.delete()
    }

    @Test
    fun testOnboardingCreatedAccounts_persistWithCustomNamesTypesAndOpenings() = runBlocking {
        val emptyDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val repo = FinanceRepository(emptyDb)

        // Simulate OnboardingAccountsScreen action:
        // 1. Seed clean starter categories
        repo.seedCleanDefaults()
        // 2. Clear starter accounts
        val initialAccounts = repo.getActiveAccountsList()
        initialAccounts.forEach { repo.deleteAccount(it) }

        // 3. Insert user customized accounts
        val myBankId = repo.insertAccount(
            Account(name = "My Bank", accountType = "BANK", openingBalance = 500.0, sortOrder = 0)
        )
        val cashId = repo.insertAccount(
            Account(name = "Cash", accountType = "CASH", openingBalance = 150.0, sortOrder = 1)
        )
        val investmentId = repo.insertAccount(
            Account(name = "Mutual Funds", accountType = "INVESTMENT", openingBalance = 2500.0, sortOrder = 2)
        )

        assertTrue(myBankId > 0)
        assertTrue(cashId > 0)
        assertTrue(investmentId > 0)

        // Read back via repository
        val loadedAccounts = repo.allAccounts.first()
        assertEquals(3, loadedAccounts.size)

        val myBank = loadedAccounts.first { it.name == "My Bank" }
        assertEquals("BANK", myBank.accountType)
        assertEquals(500.0, myBank.openingBalance, 0.001)

        val cash = loadedAccounts.first { it.name == "Cash" }
        assertEquals("CASH", cash.accountType)
        assertEquals(150.0, cash.openingBalance, 0.001)

        val inv = loadedAccounts.first { it.name == "Mutual Funds" }
        assertEquals("INVESTMENT", inv.accountType)
        assertEquals(2500.0, inv.openingBalance, 0.001)

        // Verify ledger math accounts with balances
        val accountsWithBal = com.example.domain.calculator.LedgerCalculator.computeAccountBalances(loadedAccounts, emptyList())
        assertEquals(3, accountsWithBal.size)
        val totalNetOpening = accountsWithBal.sumOf { it.currentBalance }
        assertEquals(3150.0, totalNetOpening, 0.001)

        emptyDb.close()
    }

    @Test
    fun testWelcomeCardKeyLogic_showsAtZeroTransactions_hidesAfterTransactionOrDismiss() {
        val prefs = context.getSharedPreferences("paisa_track_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        // 1. Initial fresh state: 0 transactions, not dismissed
        var welcomeDismissed = prefs.getBoolean("welcome_card_dismissed", false)
        val zeroTransactions = emptyList<TransactionEntity>()
        val shouldShowWelcome = !welcomeDismissed && zeroTransactions.isEmpty()
        assertTrue("Welcome card must show on fresh state with 0 transactions", shouldShowWelcome)

        // 2. User dismisses welcome card
        prefs.edit().putBoolean("welcome_card_dismissed", true).apply()
        welcomeDismissed = prefs.getBoolean("welcome_card_dismissed", false)
        assertTrue("Welcome card dismissed flag must be true in SharedPreferences", welcomeDismissed)
        val shouldShowAfterDismiss = !welcomeDismissed && zeroTransactions.isEmpty()
        assertFalse("Welcome card must not show once dismissed", shouldShowAfterDismiss)

        // 3. User with existing transactions (e.g. from restore)
        prefs.edit().clear().apply()
        val tx = TransactionEntity(id = 1L, date = System.currentTimeMillis(), type = "EXPENSE", amount = 50.0, fromAccountId = 1L, categoryId = 1L)
        val existingTransactions = listOf(tx)
        val shouldShowWithExistingTx = !prefs.getBoolean("welcome_card_dismissed", false) && existingTransactions.isEmpty()
        assertFalse("Welcome card must never show when transactions exist", shouldShowWithExistingTx)
    }
}
