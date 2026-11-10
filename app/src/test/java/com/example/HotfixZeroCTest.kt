package com.example

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.datastore.AppSettingsRepository
import com.example.data.model.Account
import com.example.data.model.BudgetEntity
import com.example.data.model.Category
import com.example.data.model.Commitment
import com.example.data.model.CustomField
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.data.repository.MutationTracker
import com.example.importbackup.MediaStoreWriter
import com.example.importbackup.UnifiedCsvStorage
import com.example.ui.settings.SettingsViewModel
import com.example.worker.AutoBackupWorker
import kotlinx.coroutines.flow.first
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
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HotfixZeroCTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: FinanceRepository
    private lateinit var settingsRepository: AppSettingsRepository

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun generateCsvWithRows(rowCount: Int, title: String = "Tx"): String {
        val sb = StringBuilder()
        sb.append("#FORMAT=2\n")
        sb.append("Date,Type,Amount,FromAccount,ToAccount,Category,Description,Notes\n")
        for (i in 1..rowCount) {
            val amount = 10.0 + i
            val dateStr = String.format("2026-09-%02d 12:00", (i % 28) + 1)
            sb.append("$dateStr,EXPENSE,$amount,Main Bank,,Groceries,$title $i,Note $i\n")
        }
        return sb.toString()
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(database)
        settingsRepository = AppSettingsRepository(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // T1: VIDEO REPRO — 82 rows in-app, 115-row legacy file on disk. Auto-save must NOT shrink or overwrite 115-row file.
    @Test
    fun testT1_videoRepro_autoSaveDoesNotShrinkLegacy115RowBackup() {
        runBlocking {
            // Seed 82 rows in-app
            val bankId = repository.insertAccount(Account(name = "Main Bank", accountType = "BANK", openingBalance = 50000.0))
            val catId = repository.insertCategory(Category(name = "Groceries", color = "#10B981"))
            val inAppTxs = (1..82).map { i ->
                TransactionEntity(
                    date = 1726660000000L + (i * 3600000L),
                    type = "EXPENSE",
                    amount = 50.0 + i,
                    fromAccountId = bankId,
                    categoryId = catId,
                    description = "InApp Tx $i"
                )
            }
            repository.insertTransactions(inAppTxs)
            assertEquals(82, repository.getAllTransactionsList().size)

            // Place a 115-row fixture at the shared legacy path: Downloads/ExpenseTracker/auto/backup.csv
            val legacy115Content = generateCsvWithRows(115, "Legacy115")
            val legacyBytes = legacy115Content.toByteArray(Charsets.UTF_8)
            val legacyHashBefore = sha256(legacyBytes)

            val legacyValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "backup.csv")
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/ExpenseTracker/auto/")
            }
            val legacyUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, legacyValues)
            assertNotNull("Legacy URI creation must succeed", legacyUri)
            context.contentResolver.openOutputStream(legacyUri!!)!!.use { it.write(legacyBytes) }

            // Verify initial state on disk
            val diskBytesBefore = context.contentResolver.openInputStream(legacyUri)!!.use { it.readBytes() }
            assertEquals(115, MediaStoreWriter.countTransactionRows(String(diskBytesBefore)))
            assertEquals(legacyHashBefore, sha256(diskBytesBefore))

            // Run the auto-save / Save-Sync write path
            val saveResult = UnifiedCsvStorage.saveToDeviceCsv(
                context = context,
                transactions = repository.allTransactions.first(),
                accounts = repository.allAccounts.first(),
                categories = repository.allCategories.first(),
                customFields = emptyList<CustomField>(),
                settings = settingsRepository.settingsFlow.first(),
                commitments = emptyList<Commitment>(),
                budgets = emptyList<BudgetEntity>(),
                settingsRepository = settingsRepository
            )

            // Assert: The 115-row file on disk is NOT shrunk! Hash and row-count unchanged!
            val diskBytesAfter = context.contentResolver.openInputStream(legacyUri)!!.use { it.readBytes() }
            val rowCountAfter = MediaStoreWriter.countTransactionRows(String(diskBytesAfter))
            val hashAfter = sha256(diskBytesAfter)

            assertEquals("115-row legacy file must NOT be shrunk", 115, rowCountAfter)
            assertEquals("115-row legacy file hash must be unchanged", legacyHashBefore, hashAfter)
            assertEquals("File size must be identical", diskBytesBefore.size, diskBytesAfter.size)

            // Verify write was directed to own root (SilentSums) or safely written to own folder
            assertTrue("Save result indicates success or refusal", saveResult.isNotBlank())
        }
    }

    // T2: Restore is read-only — fixture bytes on disk are byte-identical (hash before == hash after) for ALL four doors
    @Test
    fun testT2_restoreIsReadOnly_fixtureBytesIdenticalForFourDoors() {
        runBlocking {
            val fixtureContent = generateCsvWithRows(10, "DoorTx")
            val fixtureBytes = fixtureContent.toByteArray(Charsets.UTF_8)
            val expectedHash = sha256(fixtureBytes)

            // Door 1: Phone CSV (Downloads/SilentSums/expense_tracker.csv)
            val phoneValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "expense_tracker.csv")
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/SilentSums/")
            }
            val phoneUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, phoneValues)!!
            context.contentResolver.openOutputStream(phoneUri)!!.use { it.write(fixtureBytes) }
            val pubPhoneFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SilentSums/expense_tracker.csv")
            pubPhoneFile.parentFile?.mkdirs()
            pubPhoneFile.writeBytes(fixtureBytes)

            val hashDoor1Before = sha256(context.contentResolver.openInputStream(phoneUri)!!.use { it.readBytes() })
            assertEquals(expectedHash, hashDoor1Before)

            UnifiedCsvStorage.restoreFromMobileStorage(context, repository, clearExisting = true, settingsRepository = settingsRepository)

            val hashDoor1After = sha256(context.contentResolver.openInputStream(phoneUri)!!.use { it.readBytes() })
            assertEquals("Door 1 (Phone CSV) must not modify source file", hashDoor1Before, hashDoor1After)

            // Door 2: Auto-save (Downloads/SilentSums/auto/backup.csv)
            val autoValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "backup.csv")
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/SilentSums/auto/")
            }
            val autoUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, autoValues)!!
            context.contentResolver.openOutputStream(autoUri)!!.use { it.write(fixtureBytes) }
            val pubAutoFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SilentSums/auto/backup.csv")
            pubAutoFile.parentFile?.mkdirs()
            pubAutoFile.writeBytes(fixtureBytes)

            val hashDoor2Before = sha256(context.contentResolver.openInputStream(autoUri)!!.use { it.readBytes() })
            assertEquals(expectedHash, hashDoor2Before)

            UnifiedCsvStorage.restoreFromAutoSave(context, repository, clearExisting = true, settingsRepository = settingsRepository)

            val hashDoor2After = sha256(context.contentResolver.openInputStream(autoUri)!!.use { it.readBytes() })
            assertEquals("Door 2 (Auto-save) must not modify source file", hashDoor2Before, hashDoor2After)

            // Door 3: Safety net (filesDir/backups/auto_backup.csv)
            val backupDir = File(context.filesDir, "backups")
            backupDir.mkdirs()
            val safetyNetFile = File(backupDir, "auto_backup.csv")
            safetyNetFile.writeBytes(fixtureBytes)

            val hashDoor3Before = sha256(safetyNetFile.readBytes())
            assertEquals(expectedHash, hashDoor3Before)

            val vm = SettingsViewModel(repository, settingsRepository, database)
            vm.restoreFromInternalSafetyNet(context)
            ShadowLooper.idleMainLooper()

            val hashDoor3After = sha256(safetyNetFile.readBytes())
            assertEquals("Door 3 (Safety net) must not modify source file", hashDoor3Before, hashDoor3After)

            // Door 4: Import (InputStream / Picker door)
            val importFile = File(context.cacheDir, "import_test.csv")
            importFile.writeBytes(fixtureBytes)

            val hashDoor4Before = sha256(importFile.readBytes())
            assertEquals(expectedHash, hashDoor4Before)

            importFile.inputStream().use { stream ->
                UnifiedCsvStorage.restoreFromInputStream(repository, stream, clearExisting = true, settingsRepository = settingsRepository, context = context)
            }

            val hashDoor4After = sha256(importFile.readBytes())
            assertEquals("Door 4 (Import stream) must not modify source file", hashDoor4Before, hashDoor4After)
        }
    }

    // T3: Stamp reset — forcing an auto-save cycle right after a restore writes NOTHING to the source slot
    @Test
    fun testT3_forcingAutoSaveAfterRestoreWritesNothing_stampResetProven() {
        runBlocking {
            // Seed a 5-row fixture file
            val fixtureContent = generateCsvWithRows(5, "StampTx")
            val stream = ByteArrayInputStream(fixtureContent.toByteArray(Charsets.UTF_8))

            // Setup safety-net file with initial timestamp
            val safetyNetFile = File(context.filesDir, "backups/auto_backup.csv")
            safetyNetFile.parentFile?.mkdirs()
            safetyNetFile.writeText(fixtureContent)
            val lastModifiedBefore = safetyNetFile.lastModified()

            // Restore from stream with stamp reset
            UnifiedCsvStorage.restoreFromInputStream(repository, stream, clearExisting = true, settingsRepository = settingsRepository, context = context)

            // Verify settings recorded the stamp
            val settingsAfterRestore = settingsRepository.settingsFlow.first()
            assertTrue("lastSuccessfulAutoSaveTimestamp must be > 0", settingsAfterRestore.lastSuccessfulAutoSaveTimestamp > 0L)
            assertTrue("currentStamp must match lastDataMutationTimestamp", MutationTracker.currentStamp <= settingsAfterRestore.lastDataMutationTimestamp)

            // Instantiate AutoBackupWorker via Unsafe reflection (consistent with suite)
            val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
            unsafeField.isAccessible = true
            val unsafe = unsafeField.get(null) as sun.misc.Unsafe
            val params = unsafe.allocateInstance(WorkerParameters::class.java) as WorkerParameters
            val worker = AutoBackupWorker(context, params)

            val result = worker.doWork()
            assertEquals(ListenableWorker.Result.success(), result)

            // The safety net file was NOT touched because nothing mutated
            assertEquals("Safety net file must not be rewritten on unchanged stamp", lastModifiedBefore, safetyNetFile.lastModified())
        }
    }

    // T4: Five-number report on a 115-row fixture
    @Test
    fun testT4_fiveNumberReportOn115RowFixture() {
        runBlocking {
            val content115 = generateCsvWithRows(115, "Report115")
            val stream = ByteArrayInputStream(content115.toByteArray(Charsets.UTF_8))

            val result = UnifiedCsvStorage.restoreFromInputStream(
                repository = repository,
                inputStream = stream,
                clearExisting = true,
                settingsRepository = settingsRepository,
                context = context
            )

            // Assert 5 numbers verbatim
            assertEquals(115, result.sourceRows)
            assertEquals(115, result.inserted)
            assertEquals(0, result.skippedDup)
            assertEquals(0, result.skippedFailed)
            assertTrue(result.reachedEnd)
            assertTrue(result.failedLines.isEmpty())

            // Second pass without clearExisting: all 115 must be detected as duplicates
            val stream2 = ByteArrayInputStream(content115.toByteArray(Charsets.UTF_8))
            val result2 = UnifiedCsvStorage.restoreFromInputStream(
                repository = repository,
                inputStream = stream2,
                clearExisting = false,
                settingsRepository = settingsRepository,
                context = context
            )

            assertEquals(115, result2.sourceRows)
            assertEquals(0, result2.inserted)
            assertEquals(115, result2.skippedDup)
            assertEquals(0, result2.skippedFailed)
            assertTrue(result2.reachedEnd)

            val summary2 = SettingsViewModel(repository, settingsRepository, database).formatRestoreSummary(result2)
            assertEquals("All 115 records up-to-date (115 duplicates skipped, 0 failed)", summary2)
        }
    }

    // T5: Poisoned-middle-row fixture: N-1 inserted, skippedFailed 1 with line number, reachedEnd true, summary says so
    @Test
    fun testT5_poisonedMiddleRowFixture() {
        runBlocking {
            val poisonedCsv = """
                Date,Type,Amount,FromAccount,ToAccount,Category,Description,Notes
                2026-09-01 10:00,EXPENSE,100.00,Main Bank,,Food,Good Row 1,Note 1
                2026-09-02 10:00,EXPENSE,200.00,Main Bank,,Food,Good Row 2,Note 2
                INVALID_DATE,POISONED_TYPE,NOT_AN_AMOUNT,Main Bank,,Food,Poisoned Row 3,Note 3
                2026-09-04 10:00,EXPENSE,400.00,Main Bank,,Food,Good Row 4,Note 4
                2026-09-05 10:00,EXPENSE,500.00,Main Bank,,Food,Good Row 5,Note 5
            """.trimIndent()

            val stream = ByteArrayInputStream(poisonedCsv.toByteArray(Charsets.UTF_8))
            val result = UnifiedCsvStorage.restoreFromInputStream(
                repository = repository,
                inputStream = stream,
                clearExisting = true,
                settingsRepository = settingsRepository,
                context = context
            )

            assertEquals(5, result.sourceRows)
            assertEquals(4, result.inserted)
            assertEquals(0, result.skippedDup)
            assertEquals(1, result.skippedFailed)
            assertTrue(result.reachedEnd)
            assertEquals(listOf(4), result.failedLines) // Line 4 is the poisoned row

            val vm = SettingsViewModel(repository, settingsRepository, database)
            val summary = vm.formatRestoreSummary(result)
            assertEquals("Imported 4 of 5 rows (0 duplicates skipped, 1 failed)", summary)
            assertFalse("Summary must not contain malformed phrase", summary.contains("0 duplicates added"))
        }
    }

    // T6: Picker preview of a 5-row fixture returns 5 rows; cancelled picker = clean no-op
    @Test
    fun testT6_pickerPreviewReturnsRowsWithoutStreamClosedAndCancelNoOp() {
        runBlocking {
            val content5 = generateCsvWithRows(5, "PickerRow")
            val stream = ByteArrayInputStream(content5.toByteArray(Charsets.UTF_8))

            val vm = SettingsViewModel(repository, settingsRepository, database)

            // Preview suspends and completes synchronously inside stream.use
            stream.use { s ->
                vm.previewCsv(s)
            }

            val preview = vm.csvPreview.value
            assertNotNull("Preview must not be null (no stream closed exception)", preview)
            assertEquals(5, preview?.validRows)

            // Cancelled picker: clear preview, clean no-op
            vm.clearCsvPreview()
            assertEquals(null, vm.csvPreview.value)
        }
    }

    // T7: Summary invariant unit test over every door: "0 duplicates added" phrase gone
    @Test
    fun testT7_summaryInvariantOverEveryDoor_malformedPhraseGone() {
        val vm = SettingsViewModel(repository, settingsRepository, database)

        val resAllDup = com.example.importbackup.RestoreResult(
            inserted = 0,
            skipped = 82,
            total = 82,
            relinked = 0,
            conflicts = 0,
            sourceRows = 82,
            skippedDup = 82,
            skippedFailed = 0,
            reachedEnd = true
        )

        val summaryPhone = vm.formatRestoreSummary(resAllDup, prefix = "Phone CSV")
        val summaryAuto = vm.formatRestoreSummary(resAllDup, prefix = "Auto-save")
        val summarySafety = vm.formatRestoreSummary(resAllDup, prefix = "Safety net")
        val summaryImport = vm.formatRestoreSummary(resAllDup)

        val summaries = listOf(summaryPhone, summaryAuto, summarySafety, summaryImport)
        for (s in summaries) {
            assertFalse("Phrase '0 duplicates added' must NEVER appear in any status message: $s", s.contains("0 duplicates added"))
            assertTrue("Status message must correctly state duplicates skipped: $s", s.contains("duplicates skipped"))
        }

        assertEquals("Phone CSV: All 82 records up-to-date (82 duplicates skipped, 0 failed)", summaryPhone)
        assertEquals("Auto-save: All 82 records up-to-date (82 duplicates skipped, 0 failed)", summaryAuto)
        assertEquals("Safety net: All 82 records up-to-date (82 duplicates skipped, 0 failed)", summarySafety)
        assertEquals("All 82 records up-to-date (82 duplicates skipped, 0 failed)", summaryImport)
    }
}
