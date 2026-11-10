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
import com.example.data.repository.StagedMedia
import com.example.ui.entry.EntryViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase2_12_3_EditModeWiringTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: FinanceRepository
    private lateinit var settingsRepository: AppSettingsRepository
    private lateinit var mediaRepository: MediaRepository
    private var testTxnId: Long = 0L

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(database)
        settingsRepository = AppSettingsRepository(context)
        mediaRepository = MediaRepository(context, database.mediaAttachmentDao())

        val accId = repository.insertAccount(Account(name = "Bank", accountType = "BANK", openingBalance = 1000.0))
        val catId = repository.insertCategory(Category(name = "Food", color = "#10B981"))
        testTxnId = repository.insertTransaction(
            TransactionEntity(
                date = System.currentTimeMillis(),
                type = "EXPENSE",
                amount = 250.0,
                fromAccountId = accId,
                categoryId = catId,
                description = "Dinner with vault media"
            )
        )

        // Seed 2 photos and 3 voice notes into DB and vault directory
        val vaultDir = mediaRepository.getVaultDirForTxn(testTxnId)
        for (i in 1..2) {
            val f = File(vaultDir, "photo_$i.jpg").apply { writeText("photo_content_$i") }
            database.mediaAttachmentDao().insert(
                MediaAttachment(txnId = testTxnId, kind = "PHOTO", fileName = f.name)
            )
        }
        for (i in 1..3) {
            val f = File(vaultDir, "voice_$i.m4a").apply { writeText("voice_content_$i") }
            database.mediaAttachmentDao().insert(
                MediaAttachment(txnId = testTxnId, kind = "VOICE", fileName = f.name)
            )
        }
        assertEquals(5, mediaRepository.getAttachmentsForTxnSync(testTxnId).size)
    }

    @After
    fun tearDown() {
        ShadowLooper.idleMainLooper()
        database.close()
    }

    /**
     * T1: EntryViewModel(editTransactionId = X) built the way PaisaTrackApp builds it
     * (passing mediaRepository = mediaRepository).
     * Must load all 5 vault attachments (2 photos + 3 voice notes).
     */
    @Test
    fun T1_editReopen_builtViaPaisaTrackAppPattern_loadsAllVaultAttachments() = runBlocking {
        val vm = EntryViewModel(
            repository = repository,
            editTransactionId = testTxnId,
            settingsRepository = settingsRepository,
            mediaRepository = mediaRepository
        )

        // Let async coroutines in init settle
        var attempts = 0
        while (vm.existingAttachments.value.isEmpty() && attempts < 50) {
            kotlinx.coroutines.delay(20)
            ShadowLooper.idleMainLooper()
            attempts++
        }

        assertEquals("Existing attachments must contain 5 items", 5, vm.existingAttachments.value.size)
        assertEquals("Total photo count must be 2", 2, vm.totalPhotoCount.value)
        assertEquals("Total voice count must be 3", 3, vm.totalVoiceCount.value)
    }

    /**
     * T2: In edit mode built the way PaisaTrackApp builds it,
     * adding a new photo via camera/staging starts from existing 2 and increments count to 3.
     */
    @Test
    fun T2_editMode_cameraResult_addStagedMedia_incrementsFromExistingVaultCount() = runBlocking {
        val vm = EntryViewModel(
            repository = repository,
            editTransactionId = testTxnId,
            settingsRepository = settingsRepository,
            mediaRepository = mediaRepository
        )
        var attempts = 0
        while (vm.existingAttachments.value.isEmpty() && attempts < 50) {
            kotlinx.coroutines.delay(20)
            ShadowLooper.idleMainLooper()
            attempts++
        }

        // Existing photo count must be 2 prior to camera staging
        assertEquals("Initial total photo count must reflect vault", 2, vm.totalPhotoCount.value)

        // Simulate camera capture result staging
        val newPhotoFile = File(mediaRepository.stagingDir, "cam_${UUID.randomUUID()}.jpg").apply {
            parentFile?.mkdirs()
            writeText("new_cam_pic")
        }
        val added = vm.addStagedMedia(StagedMedia(kind = "PHOTO", file = newPhotoFile, byteSize = newPhotoFile.length()))
        assertTrue("Photo should be accepted", added)
        ShadowLooper.idleMainLooper()

        // Count must increment from 2 to 3
        assertEquals("Total photo count must increment to 3", 3, vm.totalPhotoCount.value)
    }

    /**
     * T3: Save in edit mode applies diff (persists staged, deletes removed from disk & DB);
     * Cancel leaves the vault untouched and purges staging.
     */
    @Test
    fun T3_editMode_saveAppliesDiff_andCancelLeavesVaultUntouched() = runBlocking {
        // --- 1. Test SAVE applies diff ---
        val vmSave = EntryViewModel(
            repository = repository,
            editTransactionId = testTxnId,
            settingsRepository = settingsRepository,
            mediaRepository = mediaRepository
        )
        var attemptsSave = 0
        while (vmSave.existingAttachments.value.isEmpty() && attemptsSave < 50) {
            kotlinx.coroutines.delay(20)
            ShadowLooper.idleMainLooper()
            attemptsSave++
        }

        // Remove photo_1.jpg from existing
        val loadedAtts = vmSave.existingAttachments.value
        val toRemove = loadedAtts.firstOrNull { it.fileName == "photo_1.jpg" }
        if (toRemove != null) {
            vmSave.removeExistingAttachment(toRemove)
        }

        // Stage photo_3.jpg
        val newPhoto = File(mediaRepository.stagingDir, "photo_3.jpg").apply {
            parentFile?.mkdirs()
            writeText("photo_3_content")
        }
        vmSave.addStagedMedia(StagedMedia(kind = "PHOTO", file = newPhoto, byteSize = newPhoto.length()))

        // Save
        vmSave.saveTransaction()
        var attemptsPostSave = 0
        while (!mediaRepository.getAttachmentsForTxnSync(testTxnId).any { it.fileName == "photo_3.jpg" } && attemptsPostSave < 50) {
            kotlinx.coroutines.delay(20)
            ShadowLooper.idleMainLooper()
            attemptsPostSave++
        }

        // Verify vault in DB: photo_1.jpg deleted, photo_3.jpg committed, photo_2.jpg and 3 voice notes retained
        val finalAtts = mediaRepository.getAttachmentsForTxnSync(testTxnId)
        val finalFileNames = finalAtts.map { it.fileName }
        assertFalse("photo_1.jpg must be deleted from DB", finalFileNames.contains("photo_1.jpg"))
        assertTrue("photo_3.jpg must committed to DB", finalFileNames.contains("photo_3.jpg"))
        assertTrue("photo_2.jpg must remain in DB", finalFileNames.contains("photo_2.jpg"))

        // Verify disk files
        val vaultDir = mediaRepository.getVaultDirForTxn(testTxnId)
        assertFalse("photo_1.jpg must be deleted from disk", File(vaultDir, "photo_1.jpg").exists())
        assertTrue("photo_3.jpg must exist on disk in vault", File(vaultDir, "photo_3.jpg").exists())
        assertTrue("photo_2.jpg must exist on disk in vault", File(vaultDir, "photo_2.jpg").exists())

        // --- 2. Test CANCEL leaves vault untouched and purges staging ---
        val vmCancel = EntryViewModel(
            repository = repository,
            editTransactionId = testTxnId,
            settingsRepository = settingsRepository,
            mediaRepository = mediaRepository
        )
        var attemptsCancel = 0
        while (vmCancel.existingAttachments.value.isEmpty() && attemptsCancel < 50) {
            kotlinx.coroutines.delay(20)
            ShadowLooper.idleMainLooper()
            attemptsCancel++
        }

        // In draft, stage photo_4.jpg and mark photo_2.jpg for deletion
        val uncommittedPhoto = File(mediaRepository.stagingDir, "photo_4.jpg").apply {
            parentFile?.mkdirs()
            writeText("photo_4_uncommitted")
        }
        vmCancel.addStagedMedia(StagedMedia(kind = "PHOTO", file = uncommittedPhoto, byteSize = uncommittedPhoto.length()))
        val photo2 = vmCancel.existingAttachments.value.firstOrNull { it.fileName == "photo_2.jpg" }
        if (photo2 != null) {
            vmCancel.removeExistingAttachment(photo2)
        }

        // User cancels / dismisses
        vmCancel.onCancelOrDismiss()
        ShadowLooper.idleMainLooper()

        // DB must remain untouched (photo_2.jpg still present)
        val postCancelAtts = mediaRepository.getAttachmentsForTxnSync(testTxnId)
        assertTrue("photo_2.jpg must still be in DB after cancel", postCancelAtts.any { it.fileName == "photo_2.jpg" })
        assertFalse("photo_4.jpg must NOT be in DB after cancel", postCancelAtts.any { it.fileName == "photo_4.jpg" })

        // Staging must be wiped
        assertFalse("Staged photo must be purged from disk on cancel", uncommittedPhoto.exists())
    }
}
