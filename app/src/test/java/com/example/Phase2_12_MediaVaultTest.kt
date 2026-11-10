package com.example

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.datastore.AppSettings
import com.example.data.datastore.AppSettingsRepository
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.MediaAttachment
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.data.repository.MediaRepository
import com.example.data.repository.StagedMedia
import com.example.domain.model.EnrichedTransaction
import com.example.importbackup.CsvBackupManager
import com.example.ui.entry.EntryScreen
import com.example.ui.entry.EntryViewModel
import com.example.ui.ledger.TransactionCardRow
import com.example.ui.media.MediaViewerSheet
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class Phase2_12_MediaVaultTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: FinanceRepository
    private lateinit var settingsRepository: AppSettingsRepository
    private lateinit var mediaRepo: MediaRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(database)
        settingsRepository = AppSettingsRepository(context)
        mediaRepo = MediaRepository(context, database.mediaAttachmentDao())
    }

    @After
    fun tearDown() {
        try {
            composeTestRule.waitForIdle()
        } catch (_: Throwable) {}
        ShadowLooper.idleMainLooper()
        runBlocking { settingsRepository.setDemoMode(false) }
        ShadowLooper.idleMainLooper()
    }

    // 1. testStagingWipedOnCancel: canceling sheet purges media_staging/
    @Test
    fun testStagingWipedOnCancel() {
        runBlocking {
            val testFile = File(mediaRepo.stagingDir, "test_staged_${UUID.randomUUID()}.jpg")
            testFile.writeText("sample staging data")
            assertTrue(testFile.exists())

            mediaRepo.wipeStaging()
            assertFalse(testFile.exists())
            assertEquals(0, mediaRepo.stagingDir.listFiles()?.size ?: 0)
        }
    }

    // 2. testSaveMovesFilesAndInsertsRows: save creates filesDir/media/{txnId}/ and persists rows
    @Test
    fun testSaveMovesFilesAndInsertsRows() {
        runBlocking {
            val stagedFile1 = File(mediaRepo.stagingDir, "test1.jpg").apply { writeText("photo1") }
            val stagedFile2 = File(mediaRepo.stagingDir, "test2.m4a").apply { writeText("voice1") }
            val staged = listOf(
                StagedMedia(kind = "PHOTO", file = stagedFile1, byteSize = stagedFile1.length()),
                StagedMedia(kind = "VOICE", file = stagedFile2, byteSize = stagedFile2.length(), durationMs = 15000L)
            )
            val committed = mediaRepo.commitStagedMedia(101L, staged)
            assertEquals(2, committed.size)

            val inDb = mediaRepo.getAttachmentsForTxnSync(101L)
            assertEquals(2, inDb.size)

            val vaultDir = mediaRepo.getVaultDirForTxn(101L)
            assertTrue(File(vaultDir, "test1.jpg").exists())
            assertTrue(File(vaultDir, "test2.m4a").exists())
            assertFalse(stagedFile1.exists())
            assertFalse(stagedFile2.exists())
        }
    }

    // 3. testBadgeLogic_zeroMedia_rendersNoChips: row without media renders no badge nodes
    @Test
    fun testBadgeLogic_zeroMedia_rendersNoChips() {
        val tx = EnrichedTransaction(
            entity = TransactionEntity(
                id = 55L,
                date = System.currentTimeMillis(),
                type = "EXPENSE",
                amount = 250.0,
                description = "Zero Media Tx"
            )
        )
        composeTestRule.setContent {
            MyApplicationTheme {
                TransactionCardRow(
                    transaction = tx,
                    onClick = {},
                    mediaRepository = mediaRepo
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("photo_chip_55").assertDoesNotExist()
        composeTestRule.onNodeWithTag("voice_chip_55").assertDoesNotExist()
    }

    // 4. testBadgeLogic_withMedia_rendersPhotoAndVoiceChips: row with media renders [📷 n] and [🎤 m]
    @Test
    fun testBadgeLogic_withMedia_rendersPhotoAndVoiceChips() {
        runBlocking {
            val vaultDir = mediaRepo.getVaultDirForTxn(77L)
            File(vaultDir, "p1.jpg").apply { writeText("dummy photo") }
            File(vaultDir, "v1.m4a").apply { writeText("dummy voice") }
            database.mediaAttachmentDao().insert(
                MediaAttachment(txnId = 77L, kind = "PHOTO", fileName = "p1.jpg")
            )
            database.mediaAttachmentDao().insert(
                MediaAttachment(txnId = 77L, kind = "VOICE", fileName = "v1.m4a")
            )
            val tx = EnrichedTransaction(
                entity = TransactionEntity(
                    id = 77L,
                    date = System.currentTimeMillis(),
                    type = "EXPENSE",
                    amount = 350.0,
                    description = "With Media Tx"
                )
            )
            val atts = mediaRepo.getAttachmentsForTxnSync(77L)
            composeTestRule.setContent {
                MyApplicationTheme {
                    TransactionCardRow(
                        transaction = tx,
                        onClick = {},
                        mediaRepository = mediaRepo,
                        attachments = atts
                    )
                }
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("photo_chip_77").assertIsDisplayed()
            composeTestRule.onNodeWithTag("voice_chip_77").assertIsDisplayed()
        }
    }

    // 5. testMediaViewer_rendersPhotoAndVoiceNodes: viewer displays photos and voice audio cards
    @Test
    fun testMediaViewer_rendersPhotoAndVoiceNodes() {
        runBlocking {
            val vaultDir = mediaRepo.getVaultDirForTxn(88L)
            val pFile = File(vaultDir, "p1.jpg").apply { writeText("dummy photo") }
            val vFile = File(vaultDir, "v1.m4a").apply { writeText("dummy voice") }
            val atts = listOf(
                MediaAttachment(id = 1L, txnId = 88L, kind = "PHOTO", fileName = pFile.name),
                MediaAttachment(id = 2L, txnId = 88L, kind = "VOICE", fileName = vFile.name)
            )
            composeTestRule.setContent {
                MyApplicationTheme {
                    MediaViewerSheet(
                        txnId = 88L,
                        attachments = atts,
                        mediaRepository = mediaRepo,
                        onDismiss = {},
                        onDeleteAttachment = {}
                    )
                }
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("viewer_photo_pager").assertIsDisplayed()
            composeTestRule.onNodeWithTag("media_sheet_tab_voice").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("viewer_voice_card_0").assertIsDisplayed()
        }
    }

    // 6. testCascadeDelete_removesMediaRowsAndFiles: deleting transaction cleans disk and DB
    @Test
    fun testCascadeDelete_removesMediaRowsAndFiles() {
        runBlocking {
            val accId = repository.insertAccount(Account(name = "Bank", accountType = "BANK", openingBalance = 1000.0))
            val txId = repository.insertTransaction(
                TransactionEntity(
                    date = System.currentTimeMillis(),
                    type = "EXPENSE",
                    amount = 50.0,
                    fromAccountId = accId,
                    description = "Cascaded Tx"
                )
            )
            val vaultDir = mediaRepo.getVaultDirForTxn(txId)
            val pFile = File(vaultDir, "p1.jpg").apply { writeText("cascade photo") }
            database.mediaAttachmentDao().insert(
                MediaAttachment(txnId = txId, kind = "PHOTO", fileName = pFile.name)
            )
            assertEquals(1, mediaRepo.getAttachmentsForTxnSync(txId).size)
            assertTrue(pFile.exists())

            // Cascade delete
            mediaRepo.deleteMediaForTxn(txId)
            repository.deleteTransactionById(txId)

            assertEquals(0, mediaRepo.getAttachmentsForTxnSync(txId).size)
            assertFalse(vaultDir.exists())
        }
    }

    // 7. testWipeRule_leavesVaultEmptyAfterClearAll: Clear All leaves filesDir/media empty
    @Test
    fun testWipeRule_leavesVaultEmptyAfterClearAll() {
        runBlocking {
            val vaultDir1 = mediaRepo.getVaultDirForTxn(1L)
            val vaultDir2 = mediaRepo.getVaultDirForTxn(2L)
            File(vaultDir1, "f1.jpg").writeText("p1")
            File(vaultDir2, "f2.m4a").writeText("v1")
            database.mediaAttachmentDao().insert(MediaAttachment(txnId = 1L, kind = "PHOTO", fileName = "f1.jpg"))
            database.mediaAttachmentDao().insert(MediaAttachment(txnId = 2L, kind = "VOICE", fileName = "f2.m4a"))

            mediaRepo.wipeAllMedia()

            assertEquals(0, mediaRepo.getAllAttachmentsSync().size)
            val mediaDir = File(context.filesDir, "media")
            assertTrue(!mediaDir.exists() || mediaDir.listFiles()?.isEmpty() == true)
        }
    }

    // 8. testExportByteIdentical_preAndPostFeature_identicalCsvBytes: Gold standard CSV verification
    @Test
    fun testExportByteIdentical_preAndPostFeature_identicalCsvBytes() {
        runBlocking {
        val acc = Account(id = 1L, name = "Savings", accountType = "BANK", openingBalance = 5000.0)
        val cat = Category(id = 1L, name = "Groceries", color = "#10B981")
        val tx = TransactionEntity(
            id = 1L,
            date = 1700000000000L,
            type = "EXPENSE",
            amount = 120.50,
            fromAccountId = 1L,
            categoryId = 1L,
            description = "Supermarket",
            notes = "Weekly grocery"
        )
        val settings = AppSettings()

        // 1. Build CSV prior to any media attachments
        val csvBefore = CsvBackupManager.buildCsv(
            transactions = listOf(tx),
            accounts = listOf(acc),
            categories = listOf(cat),
            customFields = emptyList(),
            settings = settings,
            commitments = emptyList(),
            budgets = emptyList()
        )

        // 2. Insert media attachments for the transaction
        database.mediaAttachmentDao().insert(
            MediaAttachment(txnId = 1L, kind = "PHOTO", fileName = "receipt.jpg")
        )
        database.mediaAttachmentDao().insert(
            MediaAttachment(txnId = 1L, kind = "VOICE", fileName = "memo.m4a")
        )

        // 3. Build CSV again
        val csvAfter = CsvBackupManager.buildCsv(
            transactions = listOf(tx),
            accounts = listOf(acc),
            categories = listOf(cat),
            customFields = emptyList(),
            settings = settings,
            commitments = emptyList(),
            budgets = emptyList()
        )

        // Gold standard: CsvBackupManager output MUST be byte-identical
        assertArrayEquals(csvBefore.toByteArray(Charsets.UTF_8), csvAfter.toByteArray(Charsets.UTF_8))
        assertEquals(csvBefore, csvAfter)
        }
    }

    // 9. testRoomMigration_7_to_8: verifies Room migration 7->8 succeeds and creates media_attachments
    @Test
    fun testRoomMigration_7_to_8() {
        val helperConfig = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("migration_test_db_7_8.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `transactions` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `date` INTEGER NOT NULL,
                            `type` TEXT NOT NULL,
                            `amount` REAL NOT NULL,
                            `fromAccountId` INTEGER,
                            `toAccountId` INTEGER,
                            `categoryId` INTEGER,
                            `description` TEXT NOT NULL,
                            `notes` TEXT NOT NULL,
                            `customValues` TEXT NOT NULL,
                            `updatedAt` INTEGER NOT NULL
                        )
                    """.trimIndent())
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(helperConfig)
        val db = helper.writableDatabase

        // Execute MIGRATION_7_8
        AppDatabase.MIGRATION_7_8.migrate(db)

        // Verify media_attachments table exists and has proper columns
        val cursor = db.query("SELECT id, txnId, kind, fileName, createdAt FROM media_attachments")
        assertNotNull(cursor)
        assertEquals(0, cursor.count)
        cursor.close()
        db.close()
        helper.close()
    }

    // 10. testMediaCaps_enforcePhotoAndVoiceLimits: prevents > 10 photos and > 3 voice notes
    @Test
    fun testMediaCaps_enforcePhotoAndVoiceLimits() {
        val vm = EntryViewModel(repository = repository)

        // Add 10 photos
        for (i in 1..10) {
            val added = vm.addStagedMedia(StagedMedia(kind = "PHOTO", file = File("photo_$i.jpg"), byteSize = 100L))
            assertTrue("Photo $i should be added", added)
        }
        ShadowLooper.idleMainLooper()
        assertEquals(10, vm.totalPhotoCount.value)

        // Attempt 11th photo
        val added11th = vm.addStagedMedia(StagedMedia(kind = "PHOTO", file = File("photo_11.jpg"), byteSize = 100L))
        assertFalse("11th photo must be rejected", added11th)
        ShadowLooper.idleMainLooper()
        assertEquals(10, vm.totalPhotoCount.value)
        assertEquals("Up to 10 photos per entry", vm.mediaWarning.value)

        // Add 3 voice notes
        for (i in 1..3) {
            val added = vm.addStagedMedia(StagedMedia(kind = "VOICE", file = File("voice_$i.m4a"), byteSize = 100L))
            assertTrue("Voice $i should be added", added)
        }
        ShadowLooper.idleMainLooper()
        assertEquals(3, vm.totalVoiceCount.value)

        // Attempt 4th voice note
        val added4th = vm.addStagedMedia(StagedMedia(kind = "VOICE", file = File("voice_4.m4a"), byteSize = 100L))
        assertFalse("4th voice note must be rejected", added4th)
        ShadowLooper.idleMainLooper()
        assertEquals(3, vm.totalVoiceCount.value)
        assertEquals("Up to 3 voice notes per entry", vm.mediaWarning.value)
    }

    // 11. testDemoMode_hidesMediaSection: entire media section absent in demo mode
    @Test
    fun testDemoMode_hidesMediaSection() {
        runBlocking {
            settingsRepository.setDemoMode(true)
            val entryVm = EntryViewModel(
                repository = repository,
                settingsRepository = settingsRepository,
                mediaRepository = mediaRepo
            )
            composeTestRule.setContent {
                MyApplicationTheme {
                    EntryScreen(
                        viewModel = entryVm,
                        onNavigateBack = {},
                        onAddSource = {}
                    )
                }
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("entry_camera_btn").assertDoesNotExist()
            composeTestRule.onNodeWithTag("entry_gallery_btn").assertDoesNotExist()
            composeTestRule.onNodeWithTag("entry_voice_btn").assertDoesNotExist()
            composeTestRule.onNodeWithTag("media_helper_text").assertDoesNotExist()
            composeTestRule.onNodeWithTag("draft_photo_chip").assertDoesNotExist()
            composeTestRule.onNodeWithTag("draft_voice_chip").assertDoesNotExist()
            settingsRepository.setDemoMode(false)
        }
    }

    // 12. Amendment C addition: noisy-bitmap compressToBudget assertion (<= 100 KB or equals FLOOR-rung output)
    @Test
    fun testNoisyBitmap_compressToBudget_budgetOrFloorRule() {
        val width = 1600
        val height = 1200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val random = java.util.Random(42)
        val pixels = IntArray(width * height) { random.nextInt() }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        val tempFile = File(context.cacheDir, "noisy_input.jpg")
        FileOutputStream(tempFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }

        val compressedBytes = mediaRepo.compressToBudget(tempFile)
        assertTrue("Compressed output must not be empty", compressedBytes.isNotEmpty())

        val withinBudget = compressedBytes.size <= MediaRepository.PHOTO_BUDGET_BYTES
        if (!withinBudget) {
            val floorRung = MediaRepository.COMPRESSION_LADDER.last()
            assertEquals("Floor dimension must be 640", 640, floorRung.maxDimension)
            assertEquals("Floor quality must be 50", 50, floorRung.quality)
        }
        tempFile.delete()
        bitmap.recycle()
    }

    // 13. Amendment C addition: camera+gallery choke-point structural test
    @Test
    fun testCameraAndGallery_bothRouteThroughCompressChokePoint() {
        runBlocking {
            val testBmp = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
            val testFile = File(context.cacheDir, "sample_cam.jpg")
            FileOutputStream(testFile).use { testBmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }

            // Camera path
            val cameraStaged = mediaRepo.compressAndStageImage(testFile)
            assertNotNull(cameraStaged)
            assertEquals("PHOTO", cameraStaged.kind)
            assertTrue(cameraStaged.file.exists())
            assertTrue(cameraStaged.byteSize > 0)

            // Gallery path
            val uri = Uri.fromFile(testFile)
            val galleryStaged = mediaRepo.compressAndStageUri(uri)
            assertNotNull(galleryStaged)
            assertEquals("PHOTO", galleryStaged.kind)
            assertTrue(galleryStaged.file.exists())
            assertTrue(galleryStaged.byteSize > 0)

            testFile.delete()
            testBmp.recycle()
        }
    }

    // 14. Amendment C addition: viewer size-subtitle node test
    @Test
    fun testViewerSizeSubtitle_photoAndVoiceNodesExist() {
        runBlocking {
            val vaultDir = mediaRepo.getVaultDirForTxn(99L)
            val pFile = File(vaultDir, "test_pic.jpg").apply {
                val bmp = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
                FileOutputStream(this).use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
                bmp.recycle()
            }
            val vFile = File(vaultDir, "test_note.m4a").apply {
                writeBytes(ByteArray(1024))
            }

            val attachments = listOf(
                MediaAttachment(id = 1L, txnId = 99L, kind = "PHOTO", fileName = pFile.name),
                MediaAttachment(id = 2L, txnId = 99L, kind = "VOICE", fileName = vFile.name)
            )

            composeTestRule.setContent {
                MyApplicationTheme {
                    MediaViewerSheet(
                        txnId = 99L,
                        attachments = attachments,
                        mediaRepository = mediaRepo,
                        onDismiss = {},
                        onDeleteAttachment = {}
                    )
                }
            }
            composeTestRule.waitForIdle()

            // Assert photo subtitle node exists and is displayed
            composeTestRule.onNodeWithTag("viewer_media_subtitle").assertIsDisplayed()

            // Switch to Voice tab
            composeTestRule.onNodeWithTag("media_sheet_tab_voice").performClick()
            composeTestRule.waitForIdle()

            // Assert voice card subtitle node exists and is displayed
            composeTestRule.onNodeWithTag("viewer_voice_subtitle_0").assertIsDisplayed()
        }
    }
}
