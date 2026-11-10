package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.DatabaseProvider
import com.example.data.StorageConstants
import com.example.data.datastore.AppSettingsRepository
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.MediaAttachment
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.data.repository.MediaRepository
import com.example.data.seed.SampleDataSeeder
import com.example.importbackup.UnifiedCsvStorage
import com.example.ui.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.robolectric.Robolectric
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
class Phase2_13_Stage2A1_Test {

    private lateinit var context: Context
    private lateinit var realDatabase: AppDatabase
    private lateinit var repository: FinanceRepository
    private lateinit var settingsRepository: AppSettingsRepository
    private lateinit var mediaRepository: MediaRepository

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        // Clean any leftovers from past tests
        val marker = File(context.filesDir, StorageConstants.DEMO_MARKER_FILE)
        if (marker.exists()) marker.delete()
        context.deleteDatabase(StorageConstants.DEMO_DATABASE_NAME)
        File(context.filesDir, StorageConstants.DEMO_MEDIA_DIR).deleteRecursively()

        realDatabase = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            StorageConstants.REAL_DATABASE_NAME
        ).allowMainThreadQueries().build()

        repository = FinanceRepository(realDatabase)
        settingsRepository = AppSettingsRepository(context)
        mediaRepository = MediaRepository(context, realDatabase)
    }

    @After
    fun tearDown() {
        ShadowLooper.idleMainLooper()
        realDatabase.close()
        DatabaseProvider.resetInstance()
        val marker = File(context.filesDir, StorageConstants.DEMO_MARKER_FILE)
        if (marker.exists()) marker.delete()
        context.deleteDatabase(StorageConstants.DEMO_DATABASE_NAME)
        File(context.filesDir, StorageConstants.DEMO_MEDIA_DIR).deleteRecursively()
    }

    // 1. StorageConstants verification
    @Test
    fun testStorageConstants_exactValues() {
        assertEquals("silentsums_db", StorageConstants.REAL_DATABASE_NAME)
        assertEquals("demo_database", StorageConstants.DEMO_DATABASE_NAME)
        assertEquals("media", StorageConstants.REAL_MEDIA_DIR)
        assertEquals("demo_media", StorageConstants.DEMO_MEDIA_DIR)
        assertEquals("media_staging", StorageConstants.STAGING_DIR)
        assertEquals("demo_media_staging", StorageConstants.DEMO_STAGING_DIR)
        assertEquals("demo_mode_active.flag", StorageConstants.DEMO_MARKER_FILE)
    }

    // 2. MediaRepository root coupling & pairing guard
    @Test
    fun testMediaRepository_couplingAndPairingGuard() {
        // Real DB couples to REAL_MEDIA_DIR
        assertEquals(StorageConstants.REAL_MEDIA_DIR, mediaRepository.mediaRoot.name)
        assertFalse(mediaRepository.isDemo)

        // Demo DB couples to DEMO_MEDIA_DIR
        val demoDb = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            StorageConstants.DEMO_DATABASE_NAME
        ).allowMainThreadQueries().build()
        val demoMediaRepo = MediaRepository(context, demoDb)
        assertEquals(StorageConstants.DEMO_MEDIA_DIR, demoMediaRepo.mediaRoot.name)
        assertTrue(demoMediaRepo.isDemo)
        demoDb.close()

        // Breach check: Pairing real DB with demo media directory MUST throw IllegalStateException
        val demoDir = File(context.filesDir, StorageConstants.DEMO_MEDIA_DIR)
        try {
            MediaRepository(context, realDatabase, explicitMediaRoot = demoDir)
            fail("Pairing real DB with demo media root must throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Storage pairing breach") == true)
        }
    }

    // 3. Dual-layout file check & missing media filtering in Flow and Sync
    @Test
    fun testDualLayout_andMissingMediaFiltered() = runBlocking {
        val txId = 100L

        // Layout 1: filesDir/media/{txnId}/photo1.jpg
        val dir1 = File(mediaRepository.mediaRoot, txId.toString()).apply { mkdirs() }
        val f1 = File(dir1, "photo1.jpg").apply { writeText("data1") }
        val att1 = MediaAttachment(id = 1L, txnId = txId, kind = "PHOTO", fileName = "photo1.jpg")
        assertNotNull(mediaRepository.getExistingMediaFile(att1))

        // Layout 2: filesDir/media/vault/txn_{txnId}/photo2.jpg
        val dir2 = File(mediaRepository.mediaRoot, "vault/txn_$txId").apply { mkdirs() }
        val f2 = File(dir2, "photo2.jpg").apply { writeText("data2") }
        val att2 = MediaAttachment(id = 2L, txnId = txId, kind = "PHOTO", fileName = "photo2.jpg")
        assertNotNull(mediaRepository.getExistingMediaFile(att2))

        // Missing file on disk
        val attMissing = MediaAttachment(id = 3L, txnId = txId, kind = "PHOTO", fileName = "ghost.jpg")
        assertNull(mediaRepository.getExistingMediaFile(attMissing))

        // Insert rows into DAO
        realDatabase.mediaAttachmentDao().insert(att1)
        realDatabase.mediaAttachmentDao().insert(att2)
        realDatabase.mediaAttachmentDao().insert(attMissing)

        // Flow check: only f1 and f2 are emitted; missing file is omitted
        val flowItems = mediaRepository.getAttachmentsForTxn(txId).first()
        assertEquals(2, flowItems.size)
        assertTrue(flowItems.any { it.fileName == "photo1.jpg" })
        assertTrue(flowItems.any { it.fileName == "photo2.jpg" })
        assertFalse(flowItems.any { it.fileName == "ghost.jpg" })

        // Sync check: filtered identically
        val syncItems = mediaRepository.getAttachmentsForTxnSync(txId)
        assertEquals(2, syncItems.size)
        assertFalse(syncItems.any { it.fileName == "ghost.jpg" })
    }

    // 4. SampleDataSeeder refusal on real DB
    @Test
    fun testSampleDataSeeder_refusesRealDatabase() = runBlocking {
        try {
            SampleDataSeeder.seedSampleData(realDatabase)
            fail("seedSampleData on realDatabase must throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("refused") == true)
        }

        try {
            SampleDataSeeder.seedDemoTransactions(realDatabase)
            fail("seedDemoTransactions on realDatabase must throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("refused") == true)
        }

        try {
            SampleDataSeeder.clearDemoTransactions(realDatabase)
            fail("clearDemoTransactions on realDatabase must throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("refused") == true)
        }
    }

    // 5. DatabaseProvider enter and exit demo mode lifecycle
    @Test
    fun testDatabaseProvider_enterAndExitDemoMode() = runBlocking {
        val marker = File(context.filesDir, StorageConstants.DEMO_MARKER_FILE)
        assertFalse(marker.exists())
        assertFalse(DatabaseProvider.isDemoMode(context))

        // Enter demo mode
        DatabaseProvider.enterDemoMode(context, settingsRepository)
        assertTrue(marker.exists())
        assertTrue(DatabaseProvider.isDemoMode(context))
        assertTrue(settingsRepository.settingsFlow.first().demoMode)

        val bundle = DatabaseProvider.getBundle(context)
        assertTrue(bundle.isDemo)
        assertEquals(StorageConstants.DEMO_DATABASE_NAME, bundle.database.openHelper.databaseName)
        assertEquals(StorageConstants.DEMO_MEDIA_DIR, bundle.mediaRepository.mediaRoot.name)

        // Exit demo mode
        DatabaseProvider.exitDemoMode(context, settingsRepository)
        assertFalse(marker.exists())
        assertFalse(DatabaseProvider.isDemoMode(context))
        assertFalse(settingsRepository.settingsFlow.first().demoMode)

        val realBundle = DatabaseProvider.getBundle(context)
        assertFalse(realBundle.isDemo)
        assertEquals(StorageConstants.REAL_DATABASE_NAME, realBundle.database.openHelper.databaseName)
        assertEquals(StorageConstants.REAL_MEDIA_DIR, realBundle.mediaRepository.mediaRoot.name)
    }

    // 6. Cold start fallback on corrupt demo database
    @Test
    fun testColdStart_corruptDemoDb_fallsBackToReal() = runBlocking {
        val marker = File(context.filesDir, StorageConstants.DEMO_MARKER_FILE)
        marker.writeText("demo")
        // Write corrupt garbage to demo database file
        val demoDbFile = context.getDatabasePath(StorageConstants.DEMO_DATABASE_NAME)
        demoDbFile.parentFile?.mkdirs()
        demoDbFile.writeText("CORRUPT_GARBAGE_NOT_SQLITE")

        DatabaseProvider.resetInstance()
        val bundle = DatabaseProvider.getBundle(context)

        // Must catch corruption and fall back to real DB
        assertFalse(bundle.isDemo)
        assertEquals(StorageConstants.REAL_DATABASE_NAME, bundle.database.openHelper.databaseName)
        assertFalse(marker.exists())
    }

    // 7. Clear-All is blocked in demo mode (Service layer guard)
    @Test
    fun testClearAll_blockedInDemoMode() = runBlocking {
        val marker = File(context.filesDir, StorageConstants.DEMO_MARKER_FILE)
        marker.writeText("demo")
        settingsRepository.setDemoMode(true)

        val vm = SettingsViewModel(repository, settingsRepository, realDatabase)
        vm.clearAllData(context)
        ShadowLooper.idleMainLooper()

        val status = vm.statusMessage.first()
        assertEquals("Unavailable in sample mode", status)
    }

    // 8. Restore CSV preserves media files on disk per Law L2
    @Test
    fun testRestoreCsv_preservesPhysicalMediaFiles() = runBlocking {
        val accId = repository.insertAccount(Account(name = "Bank", accountType = "BANK", openingBalance = 1000.0))
        val catId = repository.insertCategory(Category(name = "Food", color = "#EF4444"))
        val txId = repository.insertTransaction(
            TransactionEntity(date = System.currentTimeMillis(), type = "EXPENSE", amount = 150.0, fromAccountId = accId, categoryId = catId, description = "Lunch")
        )

        val vaultDir = mediaRepository.getVaultDirForTxn(txId)
        val photoFile = File(vaultDir, "receipt.jpg").apply { writeText("binary_photo_data") }
        realDatabase.mediaAttachmentDao().insert(MediaAttachment(txnId = txId, kind = "PHOTO", fileName = photoFile.name))

        val csvContent = ("Date,Type,Amount,FromAccount,ToAccount,Category,Description,Notes\n" +
                "2026-03-01 12:00,EXPENSE,50.00,Bank,,Food,Dinner,\n").toByteArray()

        UnifiedCsvStorage.restoreFromInputStream(
            repository = repository,
            inputStream = ByteArrayInputStream(csvContent),
            clearExisting = true,
            settingsRepository = settingsRepository
        )

        // Real media file must NEVER be deleted during CSV restore
        assertTrue("Physical media file must be preserved on disk across restore per Law L2", photoFile.exists())
    }

    // 9. Reconcile orphans leaves orphan files on disk per Law L2
    @Test
    fun testReconcileOrphans_leavesFilesOnDisk() = runBlocking {
        val orphanTxId = 8888L
        val orphanDir = mediaRepository.getVaultDirForTxn(orphanTxId)
        val orphanFile = File(orphanDir, "lost.jpg").apply { writeText("lost_photo") }
        realDatabase.mediaAttachmentDao().insert(MediaAttachment(txnId = orphanTxId, kind = "PHOTO", fileName = orphanFile.name))

        mediaRepository.reconcileOrphans(realDatabase.transactionDao(), isDemoMode = false)

        // Orphan file is kept on disk per Law L2
        assertTrue("Orphaned media files must be kept in place per Law L2", orphanFile.exists())
    }

    // T1: entry flip with a LIVE flow collector held open on the real DB completes without throwing;
    // recreate shadow called exactly once; bundle is demo afterwards.
    @Test
    fun T1_entryFlip_liveFlowCollector_completesWithoutThrowing(): Unit = runBlocking {
        val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
        val activity = controller.get()
        val realDb = DatabaseProvider.getDatabase(activity)

        val liveCollectorJob = launch(Dispatchers.IO) {
            realDb.transactionDao().getAllTransactions().collect { }
        }

        DatabaseProvider.flipTo(activity, settingsRepository, targetDemoMode = true)
        ShadowLooper.idleMainLooper()

        assertEquals("recreate must be called exactly once", 1, activity.recreateCallCount)
        val demoBundle = DatabaseProvider.getBundle(activity)
        assertTrue("Bundle must be demo after entry flip", demoBundle.isDemo)
        assertEquals(StorageConstants.DEMO_DATABASE_NAME, demoBundle.database.openHelper.databaseName)

        liveCollectorJob.cancel()
        controller.destroy()
        Unit
    }

    // T2: Same for the exit flip.
    @Test
    fun T2_exitFlip_liveFlowCollector_completesWithoutThrowing(): Unit = runBlocking {
        val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
        val activity = controller.get()

        // Put in demo mode first
        DatabaseProvider.flipTo(activity, settingsRepository, targetDemoMode = true)
        ShadowLooper.idleMainLooper()
        activity.recreateCallCount = 0

        val demoDb = DatabaseProvider.getDatabase(activity)
        val liveCollectorJob = launch(Dispatchers.IO) {
            demoDb.transactionDao().getAllTransactions().collect { }
        }

        DatabaseProvider.flipTo(activity, settingsRepository, targetDemoMode = false)
        ShadowLooper.idleMainLooper()

        assertEquals("recreate must be called exactly once on exit flip", 1, activity.recreateCallCount)
        val realBundle = DatabaseProvider.getBundle(activity)
        assertFalse("Bundle must be real after exit flip", realBundle.isDemo)
        assertEquals(StorageConstants.REAL_DATABASE_NAME, realBundle.database.openHelper.databaseName)

        liveCollectorJob.cancel()
        controller.destroy()
        Unit
    }

    // T3: Unit: the flip never closes the outgoing instance (old instance still isOpen until the new onCreate closes it lazily).
    @Test
    fun T3_flipNeverClosesOutgoingInstance_closedLazily(): Unit = runBlocking {
        val initialBundle = DatabaseProvider.getBundle(context)
        val oldDb = initialBundle.database
        // Query database to ensure connection is opened
        oldDb.openHelper.readableDatabase
        assertTrue("Initial database must be open", oldDb.isOpen)

        DatabaseProvider.flipTo(context, settingsRepository, targetDemoMode = true)

        // Outgoing instance is NEVER closed during flipTo
        assertTrue("Outgoing database must remain open across the flip", oldDb.isOpen)

        // Simulating MainActivity.onCreate lazy closure:
        DatabaseProvider.closeAbandonedDatabases()
        assertFalse("Outgoing database must be closed after lazy cleanup", oldDb.isOpen)
    }

    // T4: Unit: an exception thrown inside seeding AFTER the marker write is caught and logged, never rethrown.
    @Test
    fun T4_exceptionInsideSeeding_caughtAndLoggedNeverRethrown(): Unit = runBlocking {
        val vm = SettingsViewModel(repository, settingsRepository, realDatabase)
        val crashLog = File(context.filesDir, "crash.log")
        if (crashLog.exists()) crashLog.delete()

        val throwingContext = object : android.content.ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String): File {
                throw IllegalStateException("Simulated disk error after marker write")
            }
        }

        vm.loadSampleDataWithSafety(throwingContext)

        assertTrue("Failure must be logged to crash.log", crashLog.exists() && crashLog.readText().contains("Simulated disk error"))
    }
}

open class TestActivity : android.app.Activity() {
    var recreateCallCount = 0
    override fun recreate() {
        recreateCallCount++
        super.recreate()
    }
}
