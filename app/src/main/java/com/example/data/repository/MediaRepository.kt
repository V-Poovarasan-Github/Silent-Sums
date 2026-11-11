package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import com.example.data.AppDatabase
import com.example.data.StorageConstants
import com.example.data.dao.MediaAttachmentDao
import com.example.data.model.MediaAttachment
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class StagedMedia(
    val id: String = UUID.randomUUID().toString(),
    val kind: String, // "PHOTO" or "VOICE"
    val file: File,
    val byteSize: Long,
    val durationMs: Long = 0L
)

class MediaRepository(
    private val context: Context,
    val mediaAttachmentDao: MediaAttachmentDao,
    explicitMediaRoot: File? = null,
    explicitStagingDir: File? = null,
    val database: AppDatabase? = null,
    isDemoMode: Boolean? = null
) {
    // Secondary constructor coupling to AppDatabase
    constructor(
        context: Context,
        database: AppDatabase,
        explicitMediaRoot: File? = null,
        explicitStagingDir: File? = null
    ) : this(
        context = context,
        mediaAttachmentDao = database.mediaAttachmentDao(),
        explicitMediaRoot = explicitMediaRoot,
        explicitStagingDir = explicitStagingDir,
        database = database,
        isDemoMode = (database.openHelper.databaseName == StorageConstants.DEMO_DATABASE_NAME)
    )

    val isDemo: Boolean = isDemoMode ?: (database?.openHelper?.databaseName == StorageConstants.DEMO_DATABASE_NAME)

    val mediaRoot: File = explicitMediaRoot ?: if (isDemo) {
        File(context.filesDir, StorageConstants.DEMO_MEDIA_DIR).apply { if (!exists()) mkdirs() }
    } else {
        File(context.filesDir, StorageConstants.REAL_MEDIA_DIR).apply { if (!exists()) mkdirs() }
    }

    val stagingDir: File = explicitStagingDir ?: if (isDemo) {
        File(context.cacheDir, StorageConstants.DEMO_STAGING_DIR).apply { if (!exists()) mkdirs() }
    } else {
        File(context.cacheDir, StorageConstants.STAGING_DIR).apply { if (!exists()) mkdirs() }
    }

    init {
        val dbName = database?.openHelper?.databaseName
        if (dbName != null) {
            val isDemoDb = (dbName == StorageConstants.DEMO_DATABASE_NAME)
            val isDemoFolder = (mediaRoot.name == StorageConstants.DEMO_MEDIA_DIR)
            if (isDemoDb != isDemoFolder) {
                throw IllegalStateException("Storage pairing breach: dbName=$dbName cannot pair with mediaRoot=${mediaRoot.name}")
            }
        }
    }

    fun getVaultDirForTxn(txnId: Long): File {
        val dir = File(mediaRoot, txnId.toString())
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getExistingMediaFile(attachment: MediaAttachment): File? {
        val file1 = File(File(mediaRoot, attachment.txnId.toString()), attachment.fileName)
        if (file1.exists() && file1.length() > 0L) return file1
        val file2 = File(File(mediaRoot, "vault/txn_${attachment.txnId}"), attachment.fileName)
        if (file2.exists() && file2.length() > 0L) return file2
        return null
    }

    private fun isValidAttachment(att: MediaAttachment): Boolean {
        return getExistingMediaFile(att) != null
    }

    fun getAttachmentsForTxn(txnId: Long): Flow<List<MediaAttachment>> {
        return mediaAttachmentDao.getAttachmentsForTxn(txnId)
            .map { list ->
                withContext(Dispatchers.IO) {
                    list.filter { isValidAttachment(it) }
                }
            }
            .flowOn(Dispatchers.IO)
    }

    fun getAllAttachmentsByTxnFlow(): Flow<Map<Long, List<MediaAttachment>>> {
        return mediaAttachmentDao.getAllAttachments()
            .map { list ->
                withContext(Dispatchers.IO) {
                    list.filter { isValidAttachment(it) }.groupBy { it.txnId }
                }
            }
            .flowOn(Dispatchers.IO)
    }

    suspend fun getAttachmentsForTxnSync(txnId: Long): List<MediaAttachment> = withContext(Dispatchers.IO) {
        mediaAttachmentDao.getAttachmentsForTxnSync(txnId).filter { isValidAttachment(it) }
    }

    suspend fun getAllAttachmentsSync(): List<MediaAttachment> = withContext(Dispatchers.IO) {
        mediaAttachmentDao.getAllAttachmentsSync().filter { isValidAttachment(it) }
    }

    suspend fun wipeStaging() = withContext(Dispatchers.IO) {
        try {
            stagingDir.listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {}
    }

    suspend fun deleteAttachment(attachment: MediaAttachment) = withContext(Dispatchers.IO) {
        val file = getExistingMediaFile(attachment)
        if (file != null && file.exists()) file.delete()
        mediaAttachmentDao.deleteById(attachment.id)
        MutationTracker.bump()
    }

    suspend fun deleteMediaForTxn(txnId: Long) = withContext(Dispatchers.IO) {
        val vaultDir = getVaultDirForTxn(txnId)
        if (vaultDir.exists()) {
            vaultDir.listFiles()?.forEach { it.delete() }
            vaultDir.delete()
        }
        val legacyDir = File(File(mediaRoot, "vault"), "txn_$txnId")
        if (legacyDir.exists()) {
            legacyDir.listFiles()?.forEach { it.delete() }
            legacyDir.delete()
        }
        mediaAttachmentDao.deleteByTxnId(txnId)
        MutationTracker.bump()
    }

    var isSaveOrRestoreInProgress: Boolean = false

    suspend fun reconcileOrphans(
        transactionDao: com.example.data.dao.TransactionDao,
        isDemoMode: Boolean = this.isDemo
    ): Int = withContext(Dispatchers.IO) {
        if (isDemoMode || isDemo) {
            return@withContext 0
        }
        if (isSaveOrRestoreInProgress) {
            return@withContext 0
        }
        if (!isDemo && mediaRoot.name == StorageConstants.DEMO_MEDIA_DIR) {
            throw SecurityException("Runtime Guard Breach: Refusing to reconcile demo root in real mode")
        }
        if (isDemo && mediaRoot.name != StorageConstants.DEMO_MEDIA_DIR) {
            throw SecurityException("Runtime Guard Breach: Refusing to reconcile real root in demo mode")
        }

        val allTxns = transactionDao.getAllTransactionsList()
        val validTxnIds = allTxns.map { it.id }.toSet()

        val vaultRoots = listOf(mediaRoot, File(mediaRoot, "vault"))
        for (root in vaultRoots) {
            if (root.exists() && root.isDirectory) {
                root.listFiles()?.forEach { dir ->
                    if (dir.isDirectory) {
                        val txnId = dir.name.removePrefix("txn_").toLongOrNull() ?: dir.name.toLongOrNull()
                        if (txnId != null && !validTxnIds.contains(txnId)) {
                            // Stage 2A-1: Log and LEAVE IN PLACE. No move, no delete, no manifest.
                            if (com.example.BuildConfig.DEBUG) {
                                android.util.Log.i("MediaVault", "Orphan vault directory found at ${dir.absolutePath} for txnId $txnId - left in place.")
                            }
                        }
                    }
                }
            }
        }
        // Purged count is 0 because files are left in place
        0
    }

    suspend fun wipeAllMedia() = withContext(Dispatchers.IO) {
        if (!isDemo && mediaRoot.name == StorageConstants.DEMO_MEDIA_DIR) {
            throw SecurityException("Runtime Guard Breach: Refusing to wipe demo root in real mode")
        }
        if (isDemo && mediaRoot.name != StorageConstants.DEMO_MEDIA_DIR) {
            throw SecurityException("Runtime Guard Breach: Refusing to wipe real root in demo mode")
        }
        if (mediaRoot.exists()) {
            mediaRoot.deleteRecursively()
        }
        wipeStaging()
        mediaAttachmentDao.clearAll()
        MutationTracker.bump()
    }

    suspend fun commitStagedMedia(txnId: Long, stagedItems: List<StagedMedia>): List<MediaAttachment> = withContext(Dispatchers.IO) {
        val vaultDir = getVaultDirForTxn(txnId)
        val committedEntities = mutableListOf<MediaAttachment>()

        val existingRef = mediaAttachmentDao.getMediaRefForTxn(txnId)
        val refToUse = existingRef ?: ("m_" + java.util.UUID.randomUUID().toString().replace("-", "").take(10))

        for (staged in stagedItems) {
            val destFile = File(vaultDir, staged.file.name)
            if (staged.file.exists()) {
                staged.file.copyTo(destFile, overwrite = true)
                staged.file.delete()
            }
            if (staged.kind == "VOICE" && staged.durationMs > 0L) {
                voiceDurationCache[destFile.name] = staged.durationMs
            }
            val attachment = MediaAttachment(
                txnId = txnId,
                kind = staged.kind,
                fileName = destFile.name,
                createdAt = System.currentTimeMillis(),
                mediaRef = refToUse
            )
            val newId = mediaAttachmentDao.insert(attachment)
            committedEntities.add(attachment.copy(id = newId))
        }
        MutationTracker.bump()
        committedEntities
    }

    fun computeSha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buf).also { bytesRead = it } != -1) {
                digest.update(buf, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun syncToMirror(mirrorDir: File, mediaRef: String, kind: String, file: File) = withContext(Dispatchers.IO) {
        if (isDemo || mediaRoot.name == StorageConstants.DEMO_MEDIA_DIR) {
            if (com.example.BuildConfig.DEBUG) {
                android.util.Log.w("MediaMirror", "Refusing to mirror in demo mode")
            }
            return@withContext
        }
        if (!file.exists()) return@withContext

        val targetDir = File(File(mirrorDir, "media"), mediaRef).apply { if (!exists()) mkdirs() }
        val targetFile = File(targetDir, file.name)
        val tempFile = File(targetDir, "${file.name}.tmp")
        file.copyTo(tempFile, overwrite = true)
        tempFile.renameTo(targetFile)

        val sha256 = computeSha256(targetFile)
        val byteSize = targetFile.length()
        val createdAt = System.currentTimeMillis()

        updateMirrorIndex(mirrorDir) { rows ->
            val existingIdx = rows.indexOfFirst { it.mediaRef == mediaRef && it.fileName == file.name }
            val newRow = MirrorIndexRow(mediaRef, kind, file.name, byteSize, sha256, createdAt)
            if (existingIdx >= 0) {
                rows[existingIdx] = newRow
            } else {
                rows.add(newRow)
            }
        }
    }

    suspend fun deleteFromMirror(mirrorDir: File, mediaRef: String, fileName: String) = withContext(Dispatchers.IO) {
        if (isDemo || mediaRoot.name == StorageConstants.DEMO_MEDIA_DIR) return@withContext

        val targetFile = File(File(File(mirrorDir, "media"), mediaRef), fileName)
        if (targetFile.exists()) targetFile.delete()

        updateMirrorIndex(mirrorDir) { rows ->
            rows.removeAll { it.mediaRef == mediaRef && it.fileName == fileName }
        }
    }

    suspend fun recoverFromMirror(mirrorDir: File, targetLocalDir: File): Int = withContext(Dispatchers.IO) {
        val indexFile = File(mirrorDir, "media_index.csv")
        if (!indexFile.exists()) return@withContext 0

        var recoveredCount = 0
        val lines = indexFile.readLines()
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            val parts = line.split(",")
            if (parts.size >= 5) {
                val mediaRef = parts[0].trim()
                val kind = parts[1].trim()
                val fileName = parts[2].trim()
                val expectedSha = parts[4].trim()

                val srcFile = File(File(File(mirrorDir, "media"), mediaRef), fileName)
                if (srcFile.exists()) {
                    val destFolder = File(targetLocalDir, mediaRef).apply { if (!exists()) mkdirs() }
                    val destFile = File(destFolder, fileName)
                    if (!destFile.exists()) {
                        srcFile.copyTo(destFile, overwrite = true)
                        val actualSha = computeSha256(destFile)
                        if (expectedSha.isBlank() || actualSha.equals(expectedSha, ignoreCase = true)) {
                            recoveredCount++
                        } else {
                            destFile.delete()
                        }
                    }
                }
            }
        }
        recoveredCount
    }

    data class MirrorIndexRow(
        val mediaRef: String,
        val kind: String,
        val fileName: String,
        val byteSize: Long,
        val sha256: String,
        val createdAt: Long
    )

    private fun updateMirrorIndex(mirrorDir: File, mutator: (MutableList<MirrorIndexRow>) -> Unit) {
        val indexFile = File(mirrorDir, "media_index.csv")
        val rows = mutableListOf<MirrorIndexRow>()
        if (indexFile.exists()) {
            val lines = indexFile.readLines()
            for (i in 1 until lines.size) {
                val line = lines[i].trim()
                if (line.isEmpty()) continue
                val parts = line.split(",")
                if (parts.size >= 6) {
                    rows.add(
                        MirrorIndexRow(
                            mediaRef = parts[0].trim(),
                            kind = parts[1].trim(),
                            fileName = parts[2].trim(),
                            byteSize = parts[3].trim().toLongOrNull() ?: 0L,
                            sha256 = parts[4].trim(),
                            createdAt = parts[5].trim().toLongOrNull() ?: System.currentTimeMillis()
                        )
                    )
                }
            }
        }
        mutator(rows)

        val sb = StringBuilder()
        sb.append("mediaRef,kind,fileName,byteSize,sha256,createdAt\n")
        for (r in rows) {
            sb.append("${r.mediaRef},${r.kind},${r.fileName},${r.byteSize},${r.sha256},${r.createdAt}\n")
        }
        val tempFile = File(mirrorDir, "media_index.csv.tmp")
        tempFile.writeText(sb.toString())
        tempFile.renameTo(indexFile)
    }

    // --- Compression & Processing Pipeline ---

    suspend fun compressAndStageImage(sourceFile: File): StagedMedia = withContext(Dispatchers.IO) {
        val compressedBytes = compressToBudget(sourceFile)
        val targetFile = File(stagingDir, "IMG_${UUID.randomUUID()}.jpg")
        FileOutputStream(targetFile).use { it.write(compressedBytes) }
        StagedMedia(
            kind = "PHOTO",
            file = targetFile,
            byteSize = targetFile.length()
        )
    }

    suspend fun compressAndStageUri(uri: Uri): StagedMedia = withContext(Dispatchers.IO) {
        val tempRaw = File(stagingDir, "raw_${UUID.randomUUID()}.tmp")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempRaw).use { output ->
                input.copyTo(output)
            }
        }
        try {
            compressAndStageImage(tempRaw)
        } finally {
            if (tempRaw.exists()) tempRaw.delete()
        }
    }

    fun compressToBudget(sourceFile: File): ByteArray {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourceFile.absolutePath, boundsOptions)
        val origW = boundsOptions.outWidth
        val origH = boundsOptions.outHeight

        var sampleSize = 1
        val maxDim = Math.max(origW, origH)
        while (maxDim / (sampleSize * 2) >= 1280) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val baseBitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOptions)
            ?: throw IllegalStateException("Could not decode image at ${sourceFile.absolutePath}")

        val orientedBitmap = applyExifOrientation(sourceFile.absolutePath, baseBitmap)

        val ladder = COMPRESSION_LADDER
        val targetSizeBytes = PHOTO_BUDGET_BYTES
        var finalBytes: ByteArray? = null

        for (rung in ladder) {
            val targetDim = rung.maxDimension
            val quality = rung.quality

            val scaled = scaleDown(orientedBitmap, targetDim)
            val stream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            val bytes = stream.toByteArray()

            if (scaled != orientedBitmap) {
                scaled.recycle()
            }

            finalBytes = bytes
            if (bytes.size <= targetSizeBytes) {
                break
            }
        }

        if (orientedBitmap != baseBitmap) {
            orientedBitmap.recycle()
        }
        baseBitmap.recycle()

        return finalBytes ?: throw IllegalStateException("Compression ladder failed completely")
    }

    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxCurrent = Math.max(width, height)
        if (maxCurrent <= maxDimension) return bitmap

        val scale = maxDimension.toFloat() / maxCurrent
        val newW = (width * scale).toInt()
        val newH = (height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun applyExifOrientation(filePath: String, bitmap: Bitmap): Bitmap {
        return try {
            val exif = android.media.ExifInterface(filePath)
            val orientation = exif.getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = Matrix()
            when (orientation) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> return bitmap
            }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) {
                bitmap.recycle()
            }
            rotated
        } catch (_: Exception) {
            bitmap
        }
    }

    // --- Voice Recording Pipeline ---

    private var activeRecorder: MediaRecorder? = null
    private var activeVoiceFile: File? = null
    private var recordingStartMs: Long = 0L

    fun startVoiceRecording(): File {
        val targetFile = File(stagingDir, "VOICE_${UUID.randomUUID()}.m4a")
        activeVoiceFile = targetFile
        recordingStartMs = System.currentTimeMillis()

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        recorder.setAudioEncodingBitRate(64000)
        recorder.setAudioSamplingRate(44100)
        recorder.setAudioChannels(1)
        recorder.setMaxDuration(120000)
        recorder.setOutputFile(targetFile.absolutePath)
        recorder.prepare()
        recorder.start()

        activeRecorder = recorder
        return targetFile
    }

    fun stopVoiceRecording(): StagedMedia? {
        val recorder = activeRecorder ?: return null
        val file = activeVoiceFile ?: return null
        val elapsedMs = if (recordingStartMs > 0L) {
            (System.currentTimeMillis() - recordingStartMs).coerceAtLeast(0L)
        } else {
            0L
        }
        recordingStartMs = 0L

        try {
            recorder.stop()
        } catch (_: Exception) {
        } finally {
            recorder.release()
            activeRecorder = null
            activeVoiceFile = null
        }

        if (file.exists() && file.length() > 0L) {
            val resolvedDurationMs = resolveVoiceDurationMs(file, elapsedMs)
            return StagedMedia(
                kind = "VOICE",
                file = file,
                byteSize = file.length(),
                durationMs = resolvedDurationMs
            )
        }
        return null
    }

    fun cancelVoiceRecording() {
        val recorder = activeRecorder
        val file = activeVoiceFile
        recordingStartMs = 0L
        try {
            recorder?.stop()
        } catch (_: Exception) {
        } finally {
            recorder?.release()
            activeRecorder = null
            activeVoiceFile = null
        }
        if (file != null && file.exists()) {
            file.delete()
        }
    }

    data class CompressionRung(val maxDimension: Int, val quality: Int)

    companion object {
        const val PHOTO_BUDGET_BYTES: Long = 100 * 1024L
        val COMPRESSION_LADDER = listOf(
            CompressionRung(1280, 75),
            CompressionRung(1024, 70),
            CompressionRung(896, 62),
            CompressionRung(768, 55),
            CompressionRung(640, 50)
        )

        val voiceDurationCache = java.util.concurrent.ConcurrentHashMap<String, Long>()
        val activePlayingTxnId = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)
        val activePlayingVoiceKey = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

        fun resolveVoiceDurationMs(file: File, fallbackDurationMs: Long = 0L): Long {
            if (fallbackDurationMs > 0L) {
                voiceDurationCache[file.name] = fallbackDurationMs
            }
            var retrieverDuration = 0L
            if (file.exists() && file.length() > 0L) {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    retrieverDuration = retriever
                        .extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                } catch (_: Exception) {
                    retrieverDuration = 0L
                } finally {
                    try {
                        retriever.release()
                    } catch (_: Exception) {
                    }
                }
            }
            if (retrieverDuration > 0L) {
                voiceDurationCache[file.name] = retrieverDuration
                return retrieverDuration
            }
            val cached = voiceDurationCache[file.name]
            if (cached != null && cached > 0L) {
                return cached
            }
            return fallbackDurationMs.coerceAtLeast(0L)
        }

        fun formatDuration(ms: Long): String {
            val totalSeconds = (ms / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
        }

        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0L) return "0 KB"
            val kb = (bytes + 1023) / 1024
            return "$kb KB"
        }
    }
}
