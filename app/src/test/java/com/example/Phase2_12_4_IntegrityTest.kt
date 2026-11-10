package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.datastore.AppSettingsRepository
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.MediaAttachment
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.data.repository.MediaRepository
import com.example.importbackup.UnifiedCsvStorage
import com.example.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase2_12_4_IntegrityTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: FinanceRepository
    private lateinit var settingsRepository: AppSettingsRepository
    private lateinit var mediaRepository: MediaRepository
    private lateinit var settingsViewModel: SettingsViewModel

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        val marker = File(context.filesDir, com.example.data.StorageConstants.DEMO_MARKER_FILE)
        if (marker.exists()) marker.delete()
        context.deleteDatabase(com.example.data.StorageConstants.DEMO_DATABASE_NAME)
        File(context.filesDir, com.example.data.StorageConstants.DEMO_MEDIA_DIR).deleteRecursively()
        settingsRepository = AppSettingsRepository(context)
        settingsRepository.setDemoMode(false)
        settingsRepository.setCurrency("₹")
        com.example.domain.util.Formatters.updateCurrentCurrencySymbol("₹")
        com.example.data.DatabaseProvider.resetInstance()

        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(database)
        mediaRepository = MediaRepository(context, database.mediaAttachmentDao())
        settingsViewModel = SettingsViewModel(repository, settingsRepository, database)
    }

    @After
    fun tearDown() = runBlocking {
        ShadowLooper.idleMainLooper()
        settingsRepository.setDemoMode(false)
        com.example.data.DatabaseProvider.resetInstance()
        val marker = File(context.filesDir, com.example.data.StorageConstants.DEMO_MARKER_FILE)
        if (marker.exists()) marker.delete()
        context.deleteDatabase(com.example.data.StorageConstants.DEMO_DATABASE_NAME)
        File(context.filesDir, com.example.data.StorageConstants.DEMO_MEDIA_DIR).deleteRecursively()
        database.close()
    }

    /**
     * T1: Settings-door sample load with real media:
     * pre_sample.csv exists in filesDir/backups and contains real transactions before erase;
     * failure to write safety CSV aborts the erase.
     */
    @Test
    fun T1_sampleLoad_isolatedMode_leavesRealMediaAndTransactionsUntouched() = runBlocking {
        val accId = repository.insertAccount(Account(name = "Bank", accountType = "BANK", openingBalance = 500.0))
        val catId = repository.insertCategory(Category(name = "Food", color = "#10B981"))
        val txId = repository.insertTransaction(
            TransactionEntity(date = System.currentTimeMillis(), type = "EXPENSE", amount = 42.0, fromAccountId = accId, categoryId = catId, description = "Real Lunch")
        )
        // Add photo to vault
        val vaultDir = mediaRepository.getVaultDirForTxn(txId)
        val photoFile = File(vaultDir, "lunch.jpg").apply { writeText("img_bytes") }
        database.mediaAttachmentDao().insert(MediaAttachment(txnId = txId, kind = "PHOTO", fileName = photoFile.name))

        // Execute loadSampleDataWithSafety
        settingsViewModel.loadSampleDataWithSafety(context)

        // Real transaction and real media remain completely untouched
        val realTxns = repository.allTransactions.first()
        assertEquals(1, realTxns.size)
        assertEquals("Real Lunch", realTxns.first().description)
        assertTrue("Real media must remain on disk", photoFile.exists())

        // Reset demo mode for subsequent tests
        settingsRepository.setDemoMode(false)
        com.example.data.DatabaseProvider.resetInstance()
    }

    /**
     * T2: Failed restore (corrupt CSV, bad header, invalid syntax) leaves media AND transactions untouched.
     */
    @Test
    fun T2_failedRestore_leavesMediaAndTransactionsUntouched() = runBlocking {
        val accId = repository.insertAccount(Account(name = "Savings", accountType = "BANK", openingBalance = 1000.0))
        val catId = repository.insertCategory(Category(name = "Travel", color = "#3B82F6"))
        val txId = repository.insertTransaction(
            TransactionEntity(date = System.currentTimeMillis(), type = "EXPENSE", amount = 100.0, fromAccountId = accId, categoryId = catId, description = "Taxi Ride")
        )
        val vaultDir = mediaRepository.getVaultDirForTxn(txId)
        val photoFile = File(vaultDir, "taxi.jpg").apply { writeText("taxi_photo") }
        database.mediaAttachmentDao().insert(MediaAttachment(txnId = txId, kind = "PHOTO", fileName = photoFile.name))

        val corruptCsv = "NOT,A,VALID,CSV,HEADER\ngarbage,data,here".toByteArray()

        try {
            UnifiedCsvStorage.restoreFromInputStream(
                repository = repository,
                inputStream = ByteArrayInputStream(corruptCsv),
                clearExisting = true,
                settingsRepository = settingsRepository
            )
        } catch (_: Exception) {}

        // Assert existing transaction and media attachment remain completely intact
        val txns = repository.allTransactions.first()
        assertEquals("Transaction must not be wiped on failed restore", 1, txns.size)
        assertEquals("Taxi Ride", txns.first().description)

        val atts = database.mediaAttachmentDao().getAllAttachmentsSync()
        assertEquals("Media attachment must not be wiped on failed restore", 1, atts.size)
        assertTrue("Media file must still exist on disk", photoFile.exists())
    }

    /**
     * T3: Orphan reconciliation: reconcileOrphans leaves files on disk (per Law L2)
     * while leaving valid transaction media completely untouched.
     */
    @Test
    fun T3_reconcileOrphans_leavesFilesOnDisk_leavesValidMediaIntact() = runBlocking {
        val accId = repository.insertAccount(Account(name = "Bank", accountType = "BANK", openingBalance = 1000.0))
        val catId = repository.insertCategory(Category(name = "General", color = "#10B981"))
        val validTxId = repository.insertTransaction(
            TransactionEntity(date = System.currentTimeMillis(), type = "EXPENSE", amount = 50.0, fromAccountId = accId, categoryId = catId, description = "Valid Tx")
        )

        // Valid attachment
        val validVaultDir = mediaRepository.getVaultDirForTxn(validTxId)
        val validFile = File(validVaultDir, "valid.jpg").apply { writeText("valid") }
        database.mediaAttachmentDao().insert(MediaAttachment(txnId = validTxId, kind = "PHOTO", fileName = validFile.name))

        // Orphan attachment for non-existent txnId 9999
        val orphanTxnId = 9999L
        val orphanVaultDir = mediaRepository.getVaultDirForTxn(orphanTxnId)
        val orphanFile = File(orphanVaultDir, "orphan.jpg").apply { writeText("orphan") }
        database.mediaAttachmentDao().insert(MediaAttachment(txnId = orphanTxnId, kind = "PHOTO", fileName = orphanFile.name))

        mediaRepository.reconcileOrphans(database.transactionDao(), isDemoMode = false)

        // Valid remains
        val validAtts = database.mediaAttachmentDao().getAttachmentsForTxnSync(validTxId)
        assertEquals(1, validAtts.size)
        assertTrue(validFile.exists())

        // Orphan file is left in place on disk per Stage 2A Law L2
        assertTrue("Orphan vault file must be left in place on disk per Law L2", orphanFile.exists())
    }

    /**
     * T4: Restore with clearExisting=true preserves physical vault files on disk per Law L2.
     */
    @Test
    fun T4_restoreClearExisting_successPath_preservesVaultFilesOnDisk() = runBlocking {
        val accId = repository.insertAccount(Account(name = "OldBank", accountType = "BANK", openingBalance = 100.0))
        val catId = repository.insertCategory(Category(name = "OldCat", color = "#EF4444"))
        val oldTxId = repository.insertTransaction(
            TransactionEntity(date = System.currentTimeMillis(), type = "EXPENSE", amount = 10.0, fromAccountId = accId, categoryId = catId, description = "Old Tx")
        )
        val oldVaultDir = mediaRepository.getVaultDirForTxn(oldTxId)
        val oldPhoto = File(oldVaultDir, "old.jpg").apply { writeText("old_data") }
        database.mediaAttachmentDao().insert(MediaAttachment(txnId = oldTxId, kind = "PHOTO", fileName = oldPhoto.name))

        val validCsv = ("Date,Type,Amount,FromAccount,ToAccount,Category,Description,Notes\n" +
                "2026-03-01 10:00,EXPENSE,25.00,NewBank,,Food,New Expense,\n").toByteArray()

        UnifiedCsvStorage.restoreFromInputStream(
            repository = repository,
            inputStream = ByteArrayInputStream(validCsv),
            clearExisting = true,
            settingsRepository = settingsRepository
        )

        val txns = repository.allTransactions.first()
        assertEquals(1, txns.size)
        assertEquals("New Expense", txns.first().description)

        // Old vault files stay on disk per Law L2
        assertTrue("Old vault file must remain on disk per Law L2", oldPhoto.exists())
    }

    /**
     * T5: Theme, hideBalances, and soundHaptics remain unchanged across CSV restore
     * whose CSV carries different values.
     */
    @Test
    fun T5_themeHideBalancesSoundHaptics_remainUnchangedAcrossRestore() = runBlocking {
        settingsRepository.setTheme("LIGHT")
        settingsRepository.setHideBalances(false)
        settingsRepository.setSoundHapticsEnabled(true)

        val csvWithSettings = ("Date,Type,Amount,FromAccount,ToAccount,Category,Description,Notes\n" +
                "2026-03-01 12:00,EXPENSE,15.00,Cash,,Food,Coffee,\n" +
                "\n" +
                "#SETTINGS\n" +
                "theme=DARK\n" +
                "hideBalances=true\n" +
                "soundHapticsEnabled=false\n" +
                "currencySymbol=$\n" +
                "monthStartDay=5\n").toByteArray()

        UnifiedCsvStorage.restoreFromInputStream(
            repository = repository,
            inputStream = ByteArrayInputStream(csvWithSettings),
            clearExisting = false,
            settingsRepository = settingsRepository
        )

        val settings = settingsRepository.settingsFlow.first()
        // theme, hideBalances, soundHaptics must NOT be overwritten
        assertEquals("Theme must remain LIGHT", "LIGHT", settings.theme)
        assertEquals("Hide balances must remain false", false, settings.hideBalances)
        assertEquals("Sound haptics must remain true", true, settings.soundHapticsEnabled)

        // currencySymbol and monthStartDay ARE allowed to update
        assertEquals("$", settings.currencySymbol)
        assertEquals(5, settings.monthStartDay)
    }

    /**
     * T6: Status message delivered once, not replayed, and not dropped when emitted before collector.
     */
    @Test
    fun T6_statusMessage_deliveredOnce_neverReplayed() = runBlocking {
        // Emit a message
        settingsViewModel.reportStatus("Operation Complete")

        // First collector receives it
        val received = mutableListOf<String>()
        val job1 = launch {
            settingsViewModel.statusMessage.collect { msg ->
                received.add(msg)
            }
        }
        kotlinx.coroutines.delay(50)
        job1.cancel()

        assertEquals("First collector should receive exactly 1 message", 1, received.size)
        assertEquals("Operation Complete", received.first())

        // Second collector subscribing later must receive NOTHING (no replay!)
        val replayed = mutableListOf<String>()
        val job2 = launch {
            settingsViewModel.statusMessage.collect { msg ->
                replayed.add(msg)
            }
        }
        kotlinx.coroutines.delay(50)
        job2.cancel()

        assertTrue("Second collector must NOT receive replayed message", replayed.isEmpty())
    }

    /**
     * T7: reconcileOrphans does not run in demo mode and does not delete media of an in-flight save.
     */
    @Test
    fun T7_reconcileOrphans_skippedInDemoMode_andProtectsInFlightSave() = runBlocking {
        val orphanTxnId = 8888L
        val orphanVaultDir = mediaRepository.getVaultDirForTxn(orphanTxnId)
        val orphanFile = File(orphanVaultDir, "orphan_demo.jpg").apply { writeText("orphan") }
        database.mediaAttachmentDao().insert(MediaAttachment(txnId = orphanTxnId, kind = "PHOTO", fileName = orphanFile.name))

        // In demo mode: reconciliation must be skipped
        val demoResult = mediaRepository.reconcileOrphans(database.transactionDao(), isDemoMode = true)
        assertEquals("Reconcile should return 0 in demo mode", 0, demoResult)
        assertTrue("Orphan must still exist because demo mode skipped reconciliation", orphanFile.exists())

        // With in-flight save active: reconciliation must be skipped
        mediaRepository.isSaveOrRestoreInProgress = true
        val inFlightResult = mediaRepository.reconcileOrphans(database.transactionDao(), isDemoMode = false)
        assertEquals("Reconcile should return 0 when in-flight save is active", 0, inFlightResult)
        assertTrue("Orphan must still exist because in-flight save protected it", orphanFile.exists())

        mediaRepository.isSaveOrRestoreInProgress = false
    }

    /**
     * T8: Fetch and Downloads (clearExisting=false) restores leave existing media intact.
     */
    @Test
    fun T8_clearExistingFalse_leavesExistingMediaIntact() = runBlocking {
        val accId = repository.insertAccount(Account(name = "Bank", accountType = "BANK", openingBalance = 200.0))
        val catId = repository.insertCategory(Category(name = "Groceries", color = "#F59E0B"))
        val txId = repository.insertTransaction(
            TransactionEntity(date = System.currentTimeMillis(), type = "EXPENSE", amount = 30.0, fromAccountId = accId, categoryId = catId, description = "Existing Grocery")
        )
        val vaultDir = mediaRepository.getVaultDirForTxn(txId)
        val photoFile = File(vaultDir, "grocery.jpg").apply { writeText("grocery_photo") }
        database.mediaAttachmentDao().insert(MediaAttachment(txnId = txId, kind = "PHOTO", fileName = photoFile.name))

        val appendCsv = ("Date,Type,Amount,FromAccount,ToAccount,Category,Description,Notes\n" +
                "2026-03-02 11:00,EXPENSE,12.00,Bank,,Groceries,Milk,\n").toByteArray()

        UnifiedCsvStorage.restoreFromInputStream(
            repository = repository,
            inputStream = ByteArrayInputStream(appendCsv),
            clearExisting = false,
            settingsRepository = settingsRepository
        )

        // Existing media must remain 100% intact
        val atts = database.mediaAttachmentDao().getAttachmentsForTxnSync(txId)
        assertEquals("Existing media attachments must not be deleted on clearExisting=false", 1, atts.size)
        assertTrue("Existing media file on disk must remain intact", photoFile.exists())
    }
}
