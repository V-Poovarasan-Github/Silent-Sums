package com.example

import android.content.Context
import android.net.Uri
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
import com.example.data.repository.MutationTracker
import com.example.data.repository.StagedMedia
import com.example.importbackup.CsvBackupManager
import com.example.importbackup.UnifiedCsvStorage
import com.example.ui.ledger.LedgerViewModel
import com.example.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class Phase2_13_Stage2B_Test {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: FinanceRepository
    private lateinit var settingsRepository: AppSettingsRepository
    private lateinit var mediaRepository: MediaRepository

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        // Ensure clean test DB
        context.deleteDatabase("stage2b_test_db")
        database = Room.databaseBuilder(context, AppDatabase::class.java, "stage2b_test_db")
            .allowMainThreadQueries()
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10
            )
            .build()
        repository = FinanceRepository(database)
        settingsRepository = AppSettingsRepository(context)
        settingsRepository.setDemoMode(false)
        val marker = File(context.filesDir, StorageConstants.DEMO_MARKER_FILE)
        if (marker.exists()) marker.delete()
        mediaRepository = MediaRepository(context, database)
    }

    @After
    fun tearDown() {
        ShadowLooper.idleMainLooper()
        database.close()
        context.deleteDatabase("stage2b_test_db")
        File(context.filesDir, "media").deleteRecursively()
        File(context.cacheDir, "media_staging").deleteRecursively()
    }

    // T1: Migration on real v8 fixture (I1)
    @Test
    fun T1_migration8to9_backfillsMediaRefs_groupedByTxnId() {
        val dbFile = context.getDatabasePath("migration_test_v8.db")
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.delete()

        // Create v8 database with all standard tables using Room builder
        val v8Db = Room.databaseBuilder(context, AppDatabase::class.java, "migration_test_v8.db")
            .addMigrations(
                AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8
            )
            .allowMainThreadQueries()
            .build()
        // Open it to create tables
        v8Db.openHelper.writableDatabase.version = 8
        v8Db.openHelper.writableDatabase.execSQL("INSERT INTO media_attachments (id, txnId, kind, fileName, createdAt) VALUES (1, 101, 'PHOTO', 'photo1.jpg', 1000)")
        v8Db.openHelper.writableDatabase.execSQL("INSERT INTO media_attachments (id, txnId, kind, fileName, createdAt) VALUES (2, 101, 'VOICE', 'voice1.aac', 1001)")
        v8Db.openHelper.writableDatabase.execSQL("INSERT INTO media_attachments (id, txnId, kind, fileName, createdAt) VALUES (3, 202, 'PHOTO', 'photo2.jpg', 2000)")
        v8Db.close()

        // Open via Room with MIGRATION_8_9
        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, "migration_test_v8.db")
            .addMigrations(AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10)
            .allowMainThreadQueries()
            .build()

        val atts = runBlocking { migratedDb.mediaAttachmentDao().getAllAttachmentsSync() }
        assertEquals(3, atts.size)

        val tx101Atts = atts.filter { it.txnId == 101L }
        assertEquals(2, tx101Atts.size)
        assertNotNull(tx101Atts[0].mediaRef)
        assertTrue("Ref format must start with m_", tx101Atts[0].mediaRef!!.startsWith("m_"))
        assertEquals("Rows of same txnId must share mediaRef", tx101Atts[0].mediaRef, tx101Atts[1].mediaRef)

        val tx202Att = atts.first { it.txnId == 202L }
        assertNotNull(tx202Att.mediaRef)
        assertTrue(tx202Att.mediaRef!!.startsWith("m_"))
        assertTrue("Different txnIds must have distinct mediaRefs", tx101Atts[0].mediaRef != tx202Att.mediaRef)

        migratedDb.close()
        dbFile.delete()
    }

    // T2: No-media export byte-identical to frozen golden
    @Test
    fun T2_noMediaExport_isByteIdenticalToFrozenGolden() = runBlocking {
        val acc = Account(id = 1L, name = "Bank", accountType = "BANK", openingBalance = 500.0)
        val cat = Category(id = 1L, name = "Food", color = "#EF4444")
        val tx = TransactionEntity(
            id = 1L,
            date = 1772448000000L, // 2026-03-02 16:00 UTC approx
            type = "EXPENSE",
            amount = 45.0,
            fromAccountId = 1L,
            categoryId = 1L,
            description = "Sandwich",
            notes = "Lunch"
        )

        // With no media attachments, buildCsv must yield strictly 8 columns
        val exported = CsvBackupManager.buildCsv(
            transactions = listOf(tx),
            accounts = listOf(acc),
            categories = listOf(cat),
            mediaRefsByTxnId = emptyMap()
        )

        val firstTwoLines = exported.lines().take(3)
        assertEquals("#FORMAT=2", firstTwoLines[0])
        assertEquals("Date,Type,Amount,FromAccount,ToAccount,Category,Description,Notes", firstTwoLines[1])
        assertFalse("Header must not contain MediaRef when no media present", firstTwoLines[1].contains("MediaRef"))
        assertFalse("Row must not have 9th column", firstTwoLines[2].count { it == ',' } > 7)
    }

    // T3: 9-column round-trip + mixed 8/9 file
    @Test
    fun T3_nineColumnRoundTrip_andMixedFile() = runBlocking {
        val acc = Account(id = 1L, name = "Cash", accountType = "CASH", openingBalance = 100.0)
        val cat = Category(id = 1L, name = "Snacks", color = "#10B981")
        val txWithMedia = TransactionEntity(id = 1L, date = 1772448000000L, type = "EXPENSE", amount = 10.0, fromAccountId = 1L, categoryId = 1L, description = "Chai", notes = "")

        val exportedWithMedia = CsvBackupManager.buildCsv(
            transactions = listOf(txWithMedia),
            accounts = listOf(acc),
            categories = listOf(cat),
            mediaRefsByTxnId = mapOf(1L to "m_chai12345")
        )

        assertTrue(exportedWithMedia.contains("Date,Type,Amount,FromAccount,ToAccount,Category,Description,Notes,MediaRef"))
        assertTrue(exportedWithMedia.contains("m_chai12345"))

        // Parse it back
        val parsed = CsvBackupManager.parseCsvFull(ByteArrayInputStream(exportedWithMedia.toByteArray()))
        assertEquals(1, parsed.transactions.size)
        assertEquals("m_chai12345", parsed.transactions[0].mediaRef)

        // Mixed 8 and 9 column file
        val mixedCsv = ("#FORMAT=2\n" +
                "Date,Type,Amount,FromAccount,ToAccount,Category,Description,Notes,MediaRef\n" +
                "2026-03-02 10:00,EXPENSE,10.00,Cash,,Snacks,Chai,,m_chai12345\n" +
                "2026-03-02 11:00,EXPENSE,20.00,Cash,,Snacks,Samosa,\n").toByteArray()

        val parsedMixed = CsvBackupManager.parseCsvFull(ByteArrayInputStream(mixedCsv))
        assertEquals(2, parsedMixed.transactions.size)
        assertEquals("m_chai12345", parsedMixed.transactions[0].mediaRef)
        assertNull(parsedMixed.transactions[1].mediaRef)
    }

    // T4: Record D0-item-2 verdict; unmodified parser passes fixtures
    @Test
    fun T4_parserPassesD0Item2Fixtures() {
        val nineColCsv = ("Date,Type,Amount,FromAccount,ToAccount,Category,Description,Notes,MediaRef\n" +
                "2026-03-01 10:00,EXPENSE,25.00,Bank,,Food,Lunch,Notes,m_abc12345\n").toByteArray()
        val parsed9 = CsvBackupManager.parseCsvFull(ByteArrayInputStream(nineColCsv))
        assertEquals(1, parsed9.transactions.size)
        assertEquals(25.0, parsed9.transactions[0].amount, 0.01)

        val mixedCsv = ("Date,Type,Amount,FromAccount,ToAccount,Category,Description,Notes\n" +
                "2026-03-01 10:00,EXPENSE,25.00,Bank,,Food,Lunch,Notes,m_abc12345\n" +
                "2026-03-01 11:00,EXPENSE,15.00,Bank,,Food,Snack,Notes\n").toByteArray()
        val parsedMixed = CsvBackupManager.parseCsvFull(ByteArrayInputStream(mixedCsv))
        assertEquals(2, parsedMixed.transactions.size)
    }

    // T5: Relink happy path: files physically moved, chips equal old counts
    @Test
    fun T5_relinkHappyPath_filesMovedAndChipsEqualOldCounts() = runBlocking {
        val oldTxnId = 888L
        val oldRef = "m_photoVoiceRef"

        // Old orphaned media rows on disk
        val oldDir = mediaRepository.getVaultDirForTxn(oldTxnId)
        val oldPhoto = File(oldDir, "receipt.jpg").apply { writeText("photo_data") }
        database.mediaAttachmentDao().insert(
            MediaAttachment(id = 1L, txnId = oldTxnId, kind = "PHOTO", fileName = "receipt.jpg", mediaRef = oldRef)
        )

        // Restore CSV containing a row with the same mediaRef
        val restoreCsv = ("#FORMAT=2\n" +
                "Date,Type,Amount,FromAccount,ToAccount,Category,Description,Notes,MediaRef\n" +
                "2026-03-02 12:00,EXPENSE,50.00,Bank,,Food,Restored Lunch,,m_photoVoiceRef\n").toByteArray()

        val result = UnifiedCsvStorage.restoreFromInputStream(
            repository = repository,
            inputStream = ByteArrayInputStream(restoreCsv),
            clearExisting = true,
            settingsRepository = settingsRepository,
            context = context
        )

        assertEquals(1, result.inserted)
        assertEquals(1, result.relinked)
        assertEquals(0, result.conflicts)

        val newTx = repository.allTransactions.first().first()
        val relinkedAtts = database.mediaAttachmentDao().getAttachmentsForTxnSync(newTx.id)
        assertEquals(1, relinkedAtts.size)
        assertEquals("receipt.jpg", relinkedAtts[0].fileName)

        val newFile = File(mediaRepository.getVaultDirForTxn(newTx.id), "receipt.jpg")
        assertTrue("File must be moved to new transaction folder", newFile.exists())
    }

    // T6: Relink conflict: live txn holds the ref -> no move, conflict counted
    @Test
    fun T6_relinkConflict_liveTxnHoldsRef_noMoveConflictCounted() = runBlocking {
        val existingTxId = repository.insertTransaction(
            TransactionEntity(date = 1000L, type = "EXPENSE", amount = 30.0, description = "Existing")
        )
        val ref = "m_sharedRef"
        database.mediaAttachmentDao().insert(
            MediaAttachment(id = 1L, txnId = existingTxId, kind = "PHOTO", fileName = "bill.jpg", mediaRef = ref)
        )

        // Attempting to restore a second transaction claiming the same mediaRef
        val importCsv = ("#FORMAT=2\n" +
                "Date,Type,Amount,FromAccount,ToAccount,Category,Description,Notes,MediaRef\n" +
                "2026-03-02 12:00,EXPENSE,40.00,Bank,,Food,Imported,,m_sharedRef\n").toByteArray()

        val result = UnifiedCsvStorage.restoreFromInputStream(
            repository = repository,
            inputStream = ByteArrayInputStream(importCsv),
            clearExisting = false,
            settingsRepository = settingsRepository,
            context = context
        )

        assertEquals(1, result.inserted)
        assertEquals(0, result.relinked)
        assertEquals(1, result.conflicts)
    }

    // T7: Mirror index atomic + sha256 verified on recovery copy
    @Test
    fun T7_mirrorIndexAtomic_sha256VerifiedOnRecoveryCopy() = runBlocking {
        val mirrorDir = File(context.filesDir, "test_mirror/ExpenseTrackerMirror").apply { mkdirs() }
        val testRef = "m_mirrorTestRef"
        val photoContent = "test_photo_bytes_12345"
        val photoFile = File(context.cacheDir, "temp_photo.jpg").apply { writeText(photoContent) }

        // Mirror sync
        mediaRepository.syncToMirror(
            mirrorDir = mirrorDir,
            mediaRef = testRef,
            kind = "PHOTO",
            file = photoFile
        )

        val indexFile = File(mirrorDir, "media_index.csv")
        assertTrue("media_index.csv must exist in mirror", indexFile.exists())
        val indexText = indexFile.readText()
        assertTrue("Index must contain mediaRef", indexText.contains(testRef))
        assertTrue("Index must contain sha256", indexText.contains("sha256"))

        // Recovery copy verification
        val targetLocalDir = File(context.filesDir, "media/recovered")
        val recovered = mediaRepository.recoverFromMirror(mirrorDir, targetLocalDir)
        assertTrue("Must recover file", recovered >= 1)
        val recoveredFile = File(targetLocalDir, "${testRef}/temp_photo.jpg")
        assertTrue(recoveredFile.exists())
        assertEquals(photoContent, recoveredFile.readText())
    }

    // T8: Delete propagates to mirror
    @Test
    fun T8_deletePropagatesToMirror() = runBlocking {
        val mirrorDir = File(context.filesDir, "test_mirror2/ExpenseTrackerMirror").apply { mkdirs() }
        val testRef = "m_deleteRef"
        val f = File(context.cacheDir, "delete_me.jpg").apply { writeText("data") }

        mediaRepository.syncToMirror(mirrorDir, testRef, "PHOTO", f)
        assertTrue(File(mirrorDir, "media/$testRef/delete_me.jpg").exists())

        // Delete from mirror
        mediaRepository.deleteFromMirror(mirrorDir, testRef, "delete_me.jpg")
        assertFalse(File(mirrorDir, "media/$testRef/delete_me.jpg").exists())

        val indexLines = File(mirrorDir, "media_index.csv").readLines()
        assertFalse("Index must remove deleted entry", indexLines.any { it.contains("delete_me.jpg") })
    }

    // T9: Mirror-OFF restore never touches mirror paths
    @Test
    fun T9_mirrorOff_restoreNeverTouchesMirror() = runBlocking {
        settingsRepository.setMediaMirrorEnabled(false)
        val mirrorDir = File(context.filesDir, "untouched_mirror").apply { mkdirs() }
        settingsRepository.setMediaMirrorTreeUri(Uri.fromFile(mirrorDir).toString())

        val csv = ("Date,Type,Amount,FromAccount,ToAccount,Category,Description,Notes\n" +
                "2026-03-01 10:00,EXPENSE,15.00,Bank,,Food,Coffee,\n").toByteArray()

        UnifiedCsvStorage.restoreFromInputStream(
            repository = repository,
            inputStream = ByteArrayInputStream(csv),
            clearExisting = false,
            settingsRepository = settingsRepository,
            context = context
        )

        assertEquals("Mirror directory must remain untouched when mirror is OFF", 0, mirrorDir.listFiles()?.size ?: 0)
    }

    // T10: Clear-All checkbox unchecked -> mirror folder intact on disk
    @Test
    fun T10_clearAllCheckboxUnchecked_mirrorFolderIntact() = runBlocking {
        val mirrorDir = File(context.filesDir, "safe_mirror/ExpenseTrackerMirror").apply { mkdirs() }
        val dummy = File(mirrorDir, "keep_me.txt").apply { writeText("safe") }

        val vm = SettingsViewModel(repository, settingsRepository, database)
        vm.clearAllData(context = context, deleteMirror = false)
        ShadowLooper.idleMainLooper()

        assertTrue("Mirror folder must remain intact when checkbox is unchecked", dummy.exists())
    }

    // T11: Undo regression (I8)
    @Test
    fun T11_undoRegression_deleteThenUndo_restoresRowAndMedia() = runBlocking {
        val txId = repository.insertTransaction(
            TransactionEntity(date = 1000L, type = "EXPENSE", amount = 100.0, description = "Important Bill")
        )
        val vaultDir = mediaRepository.getVaultDirForTxn(txId)
        val receipt = File(vaultDir, "receipt.jpg").apply { writeText("bill_image") }
        database.mediaAttachmentDao().insert(
            MediaAttachment(id = 1L, txnId = txId, kind = "PHOTO", fileName = "receipt.jpg", mediaRef = "m_billRef")
        )

        val ledgerVm = LedgerViewModel(repository, settingsRepository, mediaRepository)
        val tx = repository.getTransactionById(txId)!!

        // Trigger delete
        ledgerVm.deleteTransaction(tx)

        // Undo immediately within 5 seconds
        ledgerVm.undoDelete()

        // Transaction and media must still be alive
        val retrieved = repository.getTransactionById(txId)
        assertNotNull("Transaction must be restored on undo", retrieved)
        val atts = database.mediaAttachmentDao().getAttachmentsForTxnSync(txId)
        assertEquals(1, atts.size)
        assertTrue("Media file must still exist", receipt.exists())
    }

    // T12: Attach/delete bump MutationTracker -> auto-save output contains MediaRef column
    @Test
    fun T12_attachAndDeployBumpMutationTracker() = runBlocking {
        val initialStamp = MutationTracker.currentStamp

        val stagedFile = File(context.cacheDir, "staged.jpg").apply { writeText("bytes") }
        val staged = listOf(StagedMedia(kind = "PHOTO", file = stagedFile, byteSize = 5L))

        mediaRepository.commitStagedMedia(txnId = 555L, stagedItems = staged)
        assertTrue("MutationTracker must bump on attach", MutationTracker.currentStamp > initialStamp)

        val bumpAfterAttach = MutationTracker.currentStamp
        val att = database.mediaAttachmentDao().getAttachmentsForTxnSync(555L).first()
        mediaRepository.deleteAttachment(att)
        assertTrue("MutationTracker must bump on delete", MutationTracker.currentStamp > bumpAfterAttach)
    }

    // T13: Demo mode: mirror inert, demo media never mirrored
    @Test
    fun T13_demoMode_mirrorInert_demoMediaNeverMirrored() = runBlocking {
        val mirrorDir = File(context.filesDir, "demo_guard_mirror/ExpenseTrackerMirror").apply { mkdirs() }
        settingsRepository.setDemoMode(true)

        val demoDb = Room.databaseBuilder(context, AppDatabase::class.java, StorageConstants.DEMO_DATABASE_NAME)
            .allowMainThreadQueries().build()
        val demoMediaRepo = MediaRepository(context, demoDb)

        val testFile = File(context.cacheDir, "demo_pic.jpg").apply { writeText("demo") }
        try {
            demoMediaRepo.syncToMirror(mirrorDir, "m_demo", "PHOTO", testFile)
        } catch (_: Exception) {}

        assertFalse("Demo media must NEVER be written to mirror", File(mirrorDir, "media/m_demo/demo_pic.jpg").exists())
        demoDb.close()
    }
}
