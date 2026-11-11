package com.example.importbackup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.flow.first
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class OrphanCollisionException(message: String) : Exception(message)

object MediaStoreWriter {

    const val ROOT_FOLDER = "SilentSums"
    const val AUTO_SUBFOLDER = "auto"
    const val DOWNLOADS_SUBFOLDER = "downloads"
    const val AUTO_BACKUP_FILE = "backup.csv"
    const val DEFAULT_MANUAL_FILE = "expense_tracker.csv"

    val LEGACY_ROOT_FOLDERS = listOf("ExpenseTracker", "PaisaTrack", "Expense Tracker", "paisatrack")
    val CANDIDATE_ROOT_FOLDERS = listOf("SilentSums", "ExpenseTracker", "PaisaTrack", "Expense Tracker", "paisatrack")
    val CANDIDATE_AUTO_FOLDERS = listOf("auto", "auto_save", "auto save", "autosave", "AutoSave", "Auto Save")
    val CANDIDATE_EXPORT_FOLDERS = listOf("downloads", "filtered_exports", "filtered exports", "exports", "Filtered Exports", "FilteredExports")

    class LargerBackupExistsException(
        override val message: String = "A newer/larger backup exists on disk — not overwriting it. Use Import to merge it in."
    ) : Exception(message)

    sealed class WriteRoot {
        abstract val displayPath: String
        abstract val isSaf: Boolean

        data class Saf(
            val treeUri: Uri,
            val rootDoc: DocumentFile,
            override val displayPath: String
        ) : WriteRoot() {
            override val isSaf: Boolean = true
        }

        data class MediaStoreDefault(
            val rootFolder: String = ROOT_FOLDER,
            override val displayPath: String = "Downloads/$rootFolder",
            val fellBackFromInvalidSaf: Boolean = false
        ) : WriteRoot() {
            override val isSaf: Boolean = false
        }
    }

    /**
     * Deterministic human-readable path for the active write root:
     * - SAF tree -> display-name chain (e.g. "Internal storage/SilentSums"), max 3 segments
     * - MediaStore default -> "Downloads/SilentSums"
     */
    fun displayPathForRoot(
        context: Context? = null,
        treeUriString: String? = null,
        rootDoc: DocumentFile? = null
    ): String {
        if (treeUriString.isNullOrBlank()) {
            return "Downloads/$ROOT_FOLDER"
        }
        val treeUri = try {
            Uri.parse(treeUriString)
        } catch (_: Exception) {
            return "Downloads/$ROOT_FOLDER"
        }
        if (isLegacyFolder(treeUriString)) {
            return "Downloads/$ROOT_FOLDER"
        }

        // 1. Check tree document ID (e.g., "primary:SilentSums" or "primary:Backups/SilentSums")
        val treeDocId: String? = try {
            android.provider.DocumentsContract.getTreeDocumentId(treeUri)
        } catch (_: Exception) {
            val segments = treeUri.pathSegments
            val treeIdx = segments.indexOf("tree")
            if (treeIdx >= 0 && treeIdx + 1 < segments.size) {
                Uri.decode(segments[treeIdx + 1])
            } else {
                null
            }
        }

        if (!treeDocId.isNullOrBlank() && treeDocId.contains(":")) {
            val volume = treeDocId.substringBefore(":")
            val relPath = treeDocId.substringAfter(":").trim('/', '\\')
            val volumeLabel = when {
                volume.equals("primary", ignoreCase = true) || volume.equals("home", ignoreCase = true) -> "Internal storage"
                volume.isNotBlank() -> volume
                else -> "Internal storage"
            }
            val relSegments = relPath.split('/', '\\').map { it.trim() }.filter { it.isNotEmpty() }
            val allSegments = (listOf(volumeLabel) + relSegments).takeLast(3)
            return allSegments.joinToString("/")
        }

        // 2. Check file:// URI (e.g., local test directory or emulated storage path)
        if (treeUri.scheme == "file") {
            val rawPath = treeUri.path ?: ""
            val normalized = rawPath.replace('\\', '/').trim('/')
            val parts = normalized.split('/').filter { it.isNotBlank() }
            val internalIdx = parts.indexOfFirst { it.equals("Internal storage", ignoreCase = true) }
            if (internalIdx >= 0) {
                return parts.subList(internalIdx, parts.size).takeLast(3).joinToString("/")
            }
            val leaf = rootDoc?.name?.takeIf { it.isNotBlank() } ?: parts.lastOrNull() ?: ROOT_FOLDER
            return listOf("Internal storage", leaf).takeLast(3).joinToString("/")
        }

        // 3. Fallback for generic content:// tree URIs using DocumentFile name or last path segment
        val docName = rootDoc?.name?.takeIf { it.isNotBlank() }
            ?: (if (context != null) {
                try {
                    DocumentFile.fromTreeUri(context, treeUri)?.name?.takeIf { it.isNotBlank() }
                } catch (_: Exception) {
                    null
                }
            } else null)
            ?: treeDocId?.trim('/', '\\')?.takeIf { it.isNotBlank() }
            ?: treeUri.lastPathSegment?.let { Uri.decode(it) }?.substringAfterLast(':')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: ROOT_FOLDER

        val cleanSegments = docName.split('/', '\\').map { it.trim() }.filter { it.isNotEmpty() }
        val combined = if (cleanSegments.firstOrNull()?.equals("Internal storage", ignoreCase = true) == true) {
            cleanSegments
        } else {
            listOf("Internal storage") + cleanSegments
        }
        return combined.takeLast(3).joinToString("/")
    }

    /**
     * Resolves the single active write root:
     * - If a SAF backup tree is set AND its grant is valid/writable -> WriteRoot.Saf
     * - Otherwise -> WriteRoot.MediaStoreDefault ("Downloads/SilentSums")
     * Legacy roots are never returned as write targets.
     */
    fun resolveWriteRoot(
        context: Context,
        customTreeUriString: String? = null
    ): WriteRoot {
        if (customTreeUriString.isNullOrBlank()) {
            return WriteRoot.MediaStoreDefault()
        }
        val treeUri = try {
            Uri.parse(customTreeUriString)
        } catch (_: Exception) {
            return WriteRoot.MediaStoreDefault(fellBackFromInvalidSaf = true)
        }

        if (isLegacyFolder(customTreeUriString) || isLegacyFolder(treeUri.toString())) {
            return WriteRoot.MediaStoreDefault(fellBackFromInvalidSaf = true)
        }

        if (treeUri.scheme == "file") {
            val dir = File(treeUri.path ?: "")
            if (!isLegacyFolder(dir.name) && !isLegacyFolder(dir.absolutePath) && (dir.exists() || dir.mkdirs()) && dir.canWrite()) {
                val doc = DocumentFile.fromFile(dir)
                return WriteRoot.Saf(
                    treeUri = treeUri,
                    rootDoc = doc,
                    displayPath = displayPathForRoot(context, customTreeUriString, doc)
                )
            }
            return WriteRoot.MediaStoreDefault(fellBackFromInvalidSaf = true)
        }

        val hasValidGrant = try {
            val perms = context.contentResolver.persistedUriPermissions
            perms.isEmpty() || perms.any { it.uri == treeUri && it.isWritePermission }
        } catch (_: Exception) {
            true
        }
        if (!hasValidGrant) {
            return WriteRoot.MediaStoreDefault(fellBackFromInvalidSaf = true)
        }

        val rootDoc = try {
            DocumentFile.fromTreeUri(context, treeUri)
        } catch (_: Exception) {
            null
        } ?: return WriteRoot.MediaStoreDefault(fellBackFromInvalidSaf = true)

        val docName = rootDoc.name ?: ""
        if (isLegacyFolder(docName) || !rootDoc.canWrite()) {
            return WriteRoot.MediaStoreDefault(fellBackFromInvalidSaf = true)
        }

        return WriteRoot.Saf(
            treeUri = treeUri,
            rootDoc = rootDoc,
            displayPath = displayPathForRoot(context, customTreeUriString, rootDoc)
        )
    }

    fun isLegacyFolder(pathOrName: String): Boolean {
        val clean = pathOrName.trim().lowercase()
        return LEGACY_ROOT_FOLDERS.any { legacy ->
            val leg = legacy.trim().lowercase()
            clean == leg ||
            clean.contains("/$leg/") ||
            clean.contains("/$leg") ||
            clean.contains("\\$leg\\") ||
            clean.contains("\\$leg") ||
            clean.startsWith("$leg/") ||
            clean.startsWith("$leg\\")
        }
    }

    fun countTransactionRows(csvContent: String): Int {
        if (csvContent.isBlank()) return 0
        var inTxSection = true
        var headerPassed = false
        var txCount = 0

        for (rawLine in csvContent.lineSequence()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("#")) {
                val upper = trimmed.uppercase()
                if (upper.startsWith("#FORMAT=")) {
                    continue
                }
                if (upper.startsWith("#TRANSACTIONS")) {
                    inTxSection = true
                    headerPassed = false
                    continue
                }
                // Any subsequent section (#ACCOUNTS, #CATEGORIES, #SETTINGS, etc.)
                inTxSection = false
                continue
            }
            if (inTxSection) {
                if (!headerPassed) {
                    if (trimmed.contains("Date", ignoreCase = true) &&
                        (trimmed.contains("Amount", ignoreCase = true) || trimmed.contains("Debit", ignoreCase = true) || trimmed.contains("Type", ignoreCase = true))) {
                        headerPassed = true
                        continue
                    }
                }
                txCount++
            }
        }
        return txCount
    }

    fun checkRefuseLargerOrForeign(
        diskContent: String,
        newContent: String,
        targetDesc: String = "backup file"
    ) {
        val diskRows = countTransactionRows(diskContent)
        val newRows = countTransactionRows(newContent)
        if (diskRows > newRows && diskRows > 0) {
            if (com.example.BuildConfig.DEBUG) {
                android.util.Log.w(
                    "MediaStoreWriter",
                    "REFUSING WRITE: on-disk $targetDesc has $diskRows rows > in-app state ($newRows rows)."
                )
            }
            throw LargerBackupExistsException()
        }
    }

    /**
     * Writes content to MediaStore.Downloads under SilentSums/[subFolder]/[fileName].
     * Resolves exclusively to own-root (Downloads/SilentSums/). Never writes to legacy roots.
     * Guarded against clobbering larger on-disk files.
     */
    fun writeToSharedDownloads(
        context: Context,
        subFolder: String,
        fileName: String,
        content: String,
        mimeType: String = "text/csv"
    ): Uri? {
        val resolver = context.contentResolver
        val relativePath = if (subFolder.isBlank()) {
            "${Environment.DIRECTORY_DOWNLOADS}/$ROOT_FOLDER/"
        } else {
            "${Environment.DIRECTORY_DOWNLOADS}/$ROOT_FOLDER/$subFolder/"
        }

        // Secondary backup: mirror only to app-private external storage
        try {
            val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (extDir != null) {
                val targetDir = if (subFolder.isBlank()) extDir else File(extDir, subFolder)
                targetDir.mkdirs()
                val targetFile = File(targetDir, fileName)
                if (targetFile.exists() && targetFile.length() > 0) {
                    checkRefuseLargerOrForeign(targetFile.readText(), content, "app-private external backup")
                }
                targetFile.writeText(content)
            }
        } catch (lbe: LargerBackupExistsException) {
            throw lbe
        } catch (_: Exception) {}

        // JVM / test public directory check for refuse-larger guard
        try {
            val pubDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                if (subFolder.isBlank()) ROOT_FOLDER else "$ROOT_FOLDER/$subFolder"
            )
            val pubFile = File(pubDir, fileName)
            if (pubFile.exists() && pubFile.length() > 0) {
                checkRefuseLargerOrForeign(pubFile.readText(), content, "shared download file")
            }
        } catch (lbe: LargerBackupExistsException) {
            throw lbe
        } catch (_: Exception) {}

        val downloadsUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Files.getContentUri("external")
        }

        // Check if file already exists in MediaStore to overwrite cleanly in place (OWN ROOT ONLY)
        var existingUri: Uri? = null
        try {
            resolver.query(downloadsUri, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val pathIdx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                while (cursor.moveToNext()) {
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                    val path = if (pathIdx >= 0) cursor.getString(pathIdx) ?: "" else ""
                    val matchesName = name.equals(fileName, ignoreCase = true)
                    val isLegacy = isLegacyFolder(path)
                    val matchesOwnFolder = if (subFolder.isBlank()) {
                        path.contains(ROOT_FOLDER, ignoreCase = true)
                    } else {
                        path.contains(ROOT_FOLDER, ignoreCase = true) && path.contains(subFolder, ignoreCase = true)
                    }
                    if (matchesName && matchesOwnFolder && !isLegacy) {
                        val id = cursor.getLong(idIdx)
                        existingUri = Uri.withAppendedPath(downloadsUri, id.toString())
                        break
                    }
                }
            }
        } catch (_: Exception) {}

        val targetUri: Uri
        if (existingUri != null) {
            // Guard: check existing MediaStore file before in-place overwrite
            try {
                resolver.openInputStream(existingUri)?.use { s ->
                    val diskContent = s.bufferedReader().readText()
                    checkRefuseLargerOrForeign(diskContent, content, "MediaStore $fileName")
                }
            } catch (lbe: LargerBackupExistsException) {
                throw lbe
            } catch (_: Exception) {}
            targetUri = existingUri
        } else {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }
            val inserted = resolver.insert(downloadsUri, values) ?: return null

            var actualName: String? = null
            try {
                resolver.query(inserted, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val nameIdx = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                        if (nameIdx >= 0) {
                            actualName = c.getString(nameIdx)
                        }
                    }
                }
            } catch (_: Exception) {}

            val isDuplicateNumberedSibling = actualName != null &&
                !actualName.equals(fileName, ignoreCase = true) &&
                !actualName.equals("$fileName.csv", ignoreCase = true) &&
                actualName.contains("(") && actualName.contains(")")

            if (isDuplicateNumberedSibling) {
                try {
                    resolver.delete(inserted, null, null)
                } catch (_: Exception) {}
                throw OrphanCollisionException("An old $fileName from a previous install occupies the auto-save name. Delete it in Files or import it; auto-save will own the name afterwards.")
            }
            targetUri = inserted
        }

        try {
            resolver.openOutputStream(targetUri, "wt")?.use { out ->
                out.bufferedWriter().use { it.write(content) }
            }
            // Also write to JVM / test public directory
            try {
                val pubDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    if (subFolder.isBlank()) ROOT_FOLDER else "$ROOT_FOLDER/$subFolder"
                )
                pubDir.mkdirs()
                File(pubDir, fileName).writeText(content)
            } catch (_: Exception) {}
            return targetUri
        } catch (lbe: LargerBackupExistsException) {
            throw lbe
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Dedicated crash log writer: writes a single stable crash.log file with MIME_TYPE text/plain,
     * overwriting fresh per launch and cleaning up any sibling duplicates.
     */
    fun writeCrashLog(context: Context, content: String): Uri? {
        // Clean up legacy crash.log* (covers crash.log and crash.log.txt) in public Downloads if present
        try {
            val downloadsUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Files.getContentUri("external")
            }
            val resolver = context.contentResolver
            val candidateIds = mutableListOf<Long>()
            resolver.query(
                downloadsUri,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                arrayOf("crash.log%"),
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                    if (name.startsWith("crash.log", ignoreCase = true)) {
                        candidateIds.add(cursor.getLong(idIdx))
                    }
                }
            }
            for (id in candidateIds) {
                try {
                    val rowUri = android.content.ContentUris.withAppendedId(downloadsUri, id)
                    resolver.delete(rowUri, null, null)
                } catch (_: SecurityException) {
                    // Scoped storage permits deleting only own rows; never delete foreign files
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // Write exclusively to app-private internal storage
        return try {
            val logDir = File(context.filesDir, "logs")
            if (!logDir.exists()) logDir.mkdirs()
            val logFile = File(logDir, "last_crash.log")
            logFile.writeText(content)
            Uri.fromFile(logFile)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Validates that an internal backup has proper format version and standard column headers.
     * Rejects corrupt or partial backups cleanly before restore.
     */
    fun isValidInternalBackup(content: String): Boolean {
        if (content.isBlank()) return false
        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return false
        val hasFormat = lines.any { it.startsWith("#FORMAT=") }
        val hasHeader = lines.any {
            it.contains("Date", ignoreCase = true) &&
            it.contains("Type", ignoreCase = true) &&
            it.contains("Amount", ignoreCase = true)
        }
        return hasFormat && hasHeader
    }

    /**
     * Writes content into a persisted SAF tree URI (obtained via OpenDocumentTree).
     * Rethrows SecurityException when permissions are revoked or folder is deleted/inaccessible.
     */
    fun writeToSafTree(
        context: Context,
        treeUri: Uri,
        subFolder: String,
        fileName: String,
        content: String,
        preResolvedDoc: DocumentFile? = null
    ): Uri? {
        if (treeUri.scheme == "file") {
            val rootDir = File(treeUri.path ?: "")
            if (isLegacyFolder(rootDir.name) || isLegacyFolder(rootDir.absolutePath)) {
                throw SecurityException("Legacy backup folders are read-only. Writes must target own folder only.")
            }
            if ((!rootDir.exists() && !rootDir.mkdirs()) || !rootDir.canWrite()) {
                throw SecurityException("Cannot write to selected SAF folder")
            }
            val targetDir = if (subFolder.isNotBlank()) File(rootDir, subFolder).apply { mkdirs() } else rootDir
            val targetFile = File(targetDir, fileName)
            if (targetFile.exists() && targetFile.length() > 0) {
                checkRefuseLargerOrForeign(targetFile.readText(), content, "SAF $fileName")
            }
            targetFile.writeText(content)
            return Uri.fromFile(targetFile)
        }

        val rootDoc = preResolvedDoc ?: try {
            DocumentFile.fromTreeUri(context, treeUri)
        } catch (se: SecurityException) {
            throw se
        } ?: throw SecurityException("Selected SAF tree inaccessible")

        val docName = rootDoc.name ?: ""
        if (isLegacyFolder(docName) || isLegacyFolder(treeUri.toString())) {
            if (com.example.BuildConfig.DEBUG) {
                android.util.Log.w("MediaStoreWriter", "Refusing write to legacy root '$docName' (read-only)")
            }
            throw SecurityException("Legacy backup folders are read-only. Writes must target own folder only.")
        }

        if (!rootDoc.canWrite()) {
            throw SecurityException("Cannot write to selected SAF folder")
        }

        val targetDir = if (subFolder.isNotBlank()) {
            rootDoc.findFile(subFolder) ?: rootDoc.createDirectory(subFolder) ?: rootDoc
        } else {
            rootDoc
        }

        val existingFile = targetDir.findFile(fileName)
        if (existingFile != null) {
            // Guard: check existing file on disk before overwrite
            try {
                context.contentResolver.openInputStream(existingFile.uri)?.use { s ->
                    val diskContent = s.bufferedReader().readText()
                    checkRefuseLargerOrForeign(diskContent, content, "SAF $fileName")
                }
            } catch (lbe: LargerBackupExistsException) {
                throw lbe
            } catch (_: Exception) {}
        }

        val targetFile = existingFile ?: targetDir.createFile("text/csv", fileName)
        val fileUri = targetFile?.uri ?: throw SecurityException("Cannot create file in SAF folder")

        context.contentResolver.openOutputStream(fileUri, "wt")?.use { out ->
            out.bufferedWriter().use { it.write(content) }
        } ?: throw SecurityException("Cannot open output stream for SAF file")
        return fileUri
    }

    /**
     * Writes the single rolling auto-save file (<activeRoot>/auto/backup.csv).
     * Routes through resolveWriteRoot(): if a custom SAF tree URI is set and valid, writes there;
     * otherwise writes to MediaStore Downloads/SilentSums/auto/backup.csv.
     * Guarded: refuses to overwrite newer/larger backups on disk.
     */
    fun writeRollingAutoSave(
        context: Context,
        content: String,
        customTreeUriString: String? = null
    ): Pair<Uri?, Boolean> {
        // Guard check on internal backup file
        try {
            val backupDir = File(context.filesDir, "backups")
            val internalFile = File(backupDir, "auto_backup.csv")
            if (internalFile.exists() && internalFile.length() > 0) {
                checkRefuseLargerOrForeign(internalFile.readText(), content, "internal auto_backup.csv")
            }
        } catch (lbe: LargerBackupExistsException) {
            throw lbe
        } catch (_: Exception) {}

        when (val root = resolveWriteRoot(context, customTreeUriString)) {
            is WriteRoot.Saf -> {
                try {
                    val safUri = writeToSafTree(context, root.treeUri, AUTO_SUBFOLDER, AUTO_BACKUP_FILE, content, root.rootDoc)
                    if (safUri != null) {
                        try {
                            val backupDir = File(context.filesDir, "backups")
                            if (!backupDir.exists()) backupDir.mkdirs()
                            File(backupDir, "auto_backup.csv").writeText(content)
                            File(backupDir, AUTO_BACKUP_FILE).writeText(content)
                        } catch (_: Exception) {}
                        return Pair(safUri, true)
                    }
                } catch (lbe: LargerBackupExistsException) {
                    throw lbe
                } catch (_: Exception) {
                    // Fall through to MediaStore default if SAF write fails mid-flight
                }
            }
            is WriteRoot.MediaStoreDefault -> {
                // Handled below via MediaStore default
            }
        }

        // MediaStore default: Downloads/SilentSums/auto/backup.csv
        try {
            val sharedUri = writeToSharedDownloads(context, AUTO_SUBFOLDER, AUTO_BACKUP_FILE, content)
            if (sharedUri != null) {
                // Also update Tier 1 internal storage
                try {
                    val backupDir = File(context.filesDir, "backups")
                    if (!backupDir.exists()) backupDir.mkdirs()
                    File(backupDir, "auto_backup.csv").writeText(content)
                    File(backupDir, AUTO_BACKUP_FILE).writeText(content)
                } catch (_: Exception) {}
                return Pair(sharedUri, false)
            }
        } catch (lbe: LargerBackupExistsException) {
            throw lbe
        } catch (_: Exception) {}

        // Tier 1: ALWAYS write rolling auto-save to app-private internal storage safety net
        val internalFile = try {
            val backupDir = File(context.filesDir, "backups")
            if (!backupDir.exists()) backupDir.mkdirs()
            val file = File(backupDir, "auto_backup.csv")
            file.writeText(content)
            try { File(backupDir, AUTO_BACKUP_FILE).writeText(content) } catch (_: Exception) {}
            file
        } catch (_: Exception) {
            null
        }

        val internalUri = if (internalFile != null) Uri.fromFile(internalFile) else null
        return Pair(internalUri, false)
    }

    /**
     * Reads a file from shared downloads by subfolder and filename exclusively through MediaStore.
     * Never uses raw java.io.File or FileInputStream on shared storage.
     */
    fun readFromSharedDownloads(
        context: Context,
        subFolder: String,
        fileName: String
    ): InputStream? {
        val resolver = context.contentResolver
        val downloadsUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Files.getContentUri("external")
        }

        // 1. Primary read: LIVE root (Downloads/<ROOT_FOLDER>/...) via MediaStore
        var legacyFallbackUri: Uri? = null
        try {
            resolver.query(downloadsUri, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val pathIdx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                while (cursor.moveToNext()) {
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                    val path = if (pathIdx >= 0) cursor.getString(pathIdx) ?: "" else ""
                    val matchesName = name.equals(fileName, ignoreCase = true)
                    val matchesSubfolder = if (subFolder.isBlank()) {
                        path.isEmpty() || CANDIDATE_ROOT_FOLDERS.any { path.contains(it, ignoreCase = true) }
                    } else {
                        val candidates = if (subFolder == AUTO_SUBFOLDER) CANDIDATE_AUTO_FOLDERS
                        else if (subFolder == DOWNLOADS_SUBFOLDER) CANDIDATE_EXPORT_FOLDERS
                        else listOf(subFolder)
                        candidates.any { path.contains(it, ignoreCase = true) } || path.isEmpty()
                    }
                    if (matchesName && matchesSubfolder) {
                        val id = cursor.getLong(idIdx)
                        val fileUri = Uri.withAppendedPath(downloadsUri, id.toString())
                        val isLiveRoot = !isLegacyFolder(path) && (path.isEmpty() || path.contains(ROOT_FOLDER, ignoreCase = true))
                        if (isLiveRoot) {
                            val stream = resolver.openInputStream(fileUri)
                            if (stream != null) return stream
                        } else if (legacyFallbackUri == null) {
                            legacyFallbackUri = fileUri
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        if (legacyFallbackUri != null) {
            try {
                val stream = resolver.openInputStream(legacyFallbackUri!!)
                if (stream != null) return stream
            } catch (_: Exception) {}
        }

        // Secondary check: app-private external files dir (NOT shared storage, completely permission-safe)
        try {
            val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (extDir != null) {
                val extFile = if (subFolder.isBlank()) File(extDir, fileName) else File(extDir, "$subFolder/$fileName")
                if (extFile.exists() && extFile.length() > 0) return FileInputStream(extFile)
            }
        } catch (_: Exception) {}

        // JVM / test fallback safely wrapped in try-catch (CANDIDATE_ROOT_FOLDERS has ROOT_FOLDER first, then LEGACY_ROOT_FOLDERS)
        try {
            for (rootDir in CANDIDATE_ROOT_FOLDERS) {
                val pubFile = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    if (subFolder.isBlank()) "$rootDir/$fileName" else "$rootDir/$subFolder/$fileName"
                )
                if (pubFile.exists() && pubFile.length() > 0) {
                    try {
                        return FileInputStream(pubFile)
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}

        return null
    }

    data class RankedBackupCandidate(
        val tier: Int,
        val sourceLabel: String,
        val bytes: ByteArray,
        val hasRealRows: Boolean,
        val rowCount: Int,
        val lastModified: Long
    )

    private val candidateWithinTierComparator = Comparator<RankedBackupCandidate> { a, b ->
        when {
            a.hasRealRows != b.hasRealRows -> if (a.hasRealRows) -1 else 1
            a.rowCount != b.rowCount -> b.rowCount.compareTo(a.rowCount)
            a.lastModified != b.lastModified -> b.lastModified.compareTo(a.lastModified)
            else -> b.bytes.size.compareTo(a.bytes.size)
        }
    }

    private fun buildCandidate(
        tier: Int,
        sourceLabel: String,
        bytes: ByteArray,
        lastModified: Long
    ): RankedBackupCandidate? {
        if (bytes.isEmpty()) return null
        val content = try {
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            return null
        }
        val hasReal = UnifiedCsvStorage.hasRealTransactionRows(content)
        val rows = if (hasReal) countTransactionRows(content) else 0
        return RankedBackupCandidate(
            tier = tier,
            sourceLabel = sourceLabel,
            bytes = bytes,
            hasRealRows = hasReal,
            rowCount = rows,
            lastModified = lastModified
        )
    }

    /**
     * Shared 4-tier read-ranking helper:
     * (1) Active SAF tree (user's picked folder): master (expense_tracker.csv) + auto (auto/backup.csv)
     *     ranked newest+largest-with-real-rows first
     * (2) MediaStore default (Downloads/SilentSums)
     * (3) Legacy roots (ExpenseTracker/, PaisaTrack/, "Expense Tracker", paisatrack) read-only
     * (4) Internal safety-net (filesDir/backups/auto_backup.csv)
     */
    fun rankBackupCandidates(
        context: Context,
        includeMasterCsv: Boolean = true
    ): List<RankedBackupCandidate> {
        val tier1Saf = mutableListOf<RankedBackupCandidate>()
        val tier2MediaStoreDefault = mutableListOf<RankedBackupCandidate>()
        val tier3Legacy = mutableListOf<RankedBackupCandidate>()
        val tier4Internal = mutableListOf<RankedBackupCandidate>()

        // Tier 1: Active SAF tree (master + auto)
        try {
            val treeUriStr = kotlinx.coroutines.runBlocking {
                try {
                    com.example.data.datastore.AppSettingsRepository(context).settingsFlow.first().autoSaveTreeUri
                } catch (_: Throwable) {
                    ""
                }
            }
            val root = resolveWriteRoot(context, treeUriStr)
            if (root is WriteRoot.Saf) {
                if (root.treeUri.scheme == "file") {
                    val baseDir = File(root.treeUri.path ?: "")
                    val masterFile = File(baseDir, DEFAULT_MANUAL_FILE)
                    if (masterFile.exists() && masterFile.length() > 0L) {
                        try {
                            buildCandidate(1, "saf:$DEFAULT_MANUAL_FILE", masterFile.readBytes(), masterFile.lastModified())
                                ?.let { tier1Saf.add(it) }
                        } catch (_: Exception) {}
                    }
                    for (autoSub in CANDIDATE_AUTO_FOLDERS) {
                        val autoDir = File(baseDir, autoSub)
                        if (autoDir.exists() && autoDir.isDirectory) {
                            val files = autoDir.listFiles { f ->
                                f.isFile && (f.name.equals(AUTO_BACKUP_FILE, ignoreCase = true) ||
                                    (f.name.startsWith("backup", ignoreCase = true) && f.name.endsWith(".csv", ignoreCase = true)))
                            }
                            files?.forEach { f ->
                                try {
                                    buildCandidate(1, "saf:$autoSub/${f.name}", f.readBytes(), f.lastModified())
                                        ?.let { tier1Saf.add(it) }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                } else {
                    val masterDoc = root.rootDoc.findFile(DEFAULT_MANUAL_FILE)
                    if (masterDoc != null) {
                        try {
                            val bytes = context.contentResolver.openInputStream(masterDoc.uri)?.use { it.readBytes() }
                            if (bytes != null) {
                                buildCandidate(1, "saf:$DEFAULT_MANUAL_FILE", bytes, masterDoc.lastModified())
                                    ?.let { tier1Saf.add(it) }
                            }
                        } catch (_: Exception) {}
                    }
                    for (autoSub in CANDIDATE_AUTO_FOLDERS) {
                        val autoDir = root.rootDoc.findFile(autoSub) ?: continue
                        for (doc in autoDir.listFiles()) {
                            val name = doc.name ?: continue
                            if (name.equals(AUTO_BACKUP_FILE, ignoreCase = true) ||
                                (name.startsWith("backup", ignoreCase = true) && name.endsWith(".csv", ignoreCase = true))
                            ) {
                                try {
                                    val bytes = context.contentResolver.openInputStream(doc.uri)?.use { it.readBytes() }
                                    if (bytes != null) {
                                        buildCandidate(1, "saf:$autoSub/$name", bytes, doc.lastModified())
                                            ?.let { tier1Saf.add(it) }
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        // Tier 2 (MediaStore default Downloads/SilentSums) & Tier 3 (Legacy roots read-only)
        val resolver = context.contentResolver
        val downloadsUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Files.getContentUri("external")
        }
        val liveRelativeDir = "$ROOT_FOLDER/$AUTO_SUBFOLDER"

        try {
            resolver.query(
                downloadsUri,
                arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    MediaStore.MediaColumns.DATE_MODIFIED
                ),
                null,
                null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val pathIdx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                val modIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                while (cursor.moveToNext()) {
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                    val path = if (pathIdx >= 0) cursor.getString(pathIdx) ?: "" else ""
                    val modSec = if (modIdx >= 0) cursor.getLong(modIdx) else 0L
                    val lastModMs = if (modSec in 1..9_999_999_999L) modSec * 1000L else modSec

                    val isAutoFile = (name.startsWith("backup", ignoreCase = true) && name.endsWith(".csv", ignoreCase = true)) &&
                        (CANDIDATE_AUTO_FOLDERS.any { path.contains(it, ignoreCase = true) } || path.isEmpty())
                    val isMasterFile = includeMasterCsv &&
                        name.equals(DEFAULT_MANUAL_FILE, ignoreCase = true) &&
                        !CANDIDATE_EXPORT_FOLDERS.any { path.contains(it, ignoreCase = true) } &&
                        (path.isEmpty() || CANDIDATE_ROOT_FOLDERS.any { path.contains(it, ignoreCase = true) })

                    if (isAutoFile || isMasterFile) {
                        val id = cursor.getLong(idIdx)
                        val fileUri = Uri.withAppendedPath(downloadsUri, id.toString())
                        val isLiveRoot = !isLegacyFolder(path) &&
                            (path.contains(liveRelativeDir, ignoreCase = true) || path.contains(ROOT_FOLDER, ignoreCase = true) || path.isEmpty())
                        val targetTier = if (isLiveRoot) 2 else 3
                        val targetList = if (isLiveRoot) tier2MediaStoreDefault else tier3Legacy
                        try {
                            resolver.openInputStream(fileUri)?.use { s ->
                                val bytes = s.readBytes()
                                buildCandidate(targetTier, "mediastore:$path/$name", bytes, lastModMs)
                                    ?.let { targetList.add(it) }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}

        // Public Downloads & app-private Documents directory scan for Tier 2 (SilentSums) & Tier 3 (Legacy)
        try {
            val pubDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            for (rootDir in CANDIDATE_ROOT_FOLDERS) {
                val isLive = rootDir.equals(ROOT_FOLDER, ignoreCase = true)
                val tier = if (isLive) 2 else 3
                val list = if (isLive) tier2MediaStoreDefault else tier3Legacy

                if (includeMasterCsv) {
                    val masterFile = File(pubDownloads, "$rootDir/$DEFAULT_MANUAL_FILE")
                    if (masterFile.exists() && masterFile.length() > 0L) {
                        try {
                            buildCandidate(tier, "pub:$rootDir/$DEFAULT_MANUAL_FILE", masterFile.readBytes(), masterFile.lastModified())
                                ?.let { list.add(it) }
                        } catch (_: Exception) {}
                    }
                }
                for (autoSub in CANDIDATE_AUTO_FOLDERS) {
                    val dir = File(pubDownloads, "$rootDir/$autoSub")
                    if (dir.exists() && dir.isDirectory) {
                        val files = dir.listFiles { f ->
                            f.isFile && f.name.startsWith("backup", ignoreCase = true) && f.name.endsWith(".csv", ignoreCase = true)
                        }
                        files?.forEach { f ->
                            try {
                                buildCandidate(tier, "pub:$rootDir/$autoSub/${f.name}", f.readBytes(), f.lastModified())
                                    ?.let { list.add(it) }
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (extDir != null) {
                // Live root paths in app-private Documents
                if (includeMasterCsv) {
                    for (rel in listOf("$ROOT_FOLDER/$DEFAULT_MANUAL_FILE", DEFAULT_MANUAL_FILE)) {
                        val f = File(extDir, rel)
                        if (f.exists() && f.length() > 0L) {
                            try {
                                buildCandidate(2, "ext:$rel", f.readBytes(), f.lastModified())
                                    ?.let { tier2MediaStoreDefault.add(it) }
                            } catch (_: Exception) {}
                        }
                    }
                }
                for (liveAutoPath in listOf(liveRelativeDir, AUTO_SUBFOLDER)) {
                    val dir = File(extDir, liveAutoPath)
                    if (dir.exists() && dir.isDirectory) {
                        val files = dir.listFiles { f ->
                            f.isFile && f.name.startsWith("backup", ignoreCase = true) && f.name.endsWith(".csv", ignoreCase = true)
                        }
                        files?.forEach { f ->
                            try {
                                buildCandidate(2, "ext:$liveAutoPath/${f.name}", f.readBytes(), f.lastModified())
                                    ?.let { tier2MediaStoreDefault.add(it) }
                            } catch (_: Exception) {}
                        }
                    }
                }
                // Legacy paths in app-private Documents
                for (legacyRoot in LEGACY_ROOT_FOLDERS) {
                    if (includeMasterCsv) {
                        val mf = File(extDir, "$legacyRoot/$DEFAULT_MANUAL_FILE")
                        if (mf.exists() && mf.length() > 0L) {
                            try {
                                buildCandidate(3, "ext:$legacyRoot/$DEFAULT_MANUAL_FILE", mf.readBytes(), mf.lastModified())
                                    ?.let { tier3Legacy.add(it) }
                            } catch (_: Exception) {}
                        }
                    }
                    for (autoSub in CANDIDATE_AUTO_FOLDERS) {
                        val dir = File(extDir, "$legacyRoot/$autoSub")
                        if (dir.exists() && dir.isDirectory) {
                            val files = dir.listFiles { f ->
                                f.isFile && f.name.startsWith("backup", ignoreCase = true) && f.name.endsWith(".csv", ignoreCase = true)
                            }
                            files?.forEach { f ->
                                try {
                                    buildCandidate(3, "ext:$legacyRoot/$autoSub/${f.name}", f.readBytes(), f.lastModified())
                                        ?.let { tier3Legacy.add(it) }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
                for (autoSub in CANDIDATE_AUTO_FOLDERS) {
                    if (autoSub.equals(AUTO_SUBFOLDER, ignoreCase = true)) continue
                    val dir = File(extDir, autoSub)
                    if (dir.exists() && dir.isDirectory) {
                        val files = dir.listFiles { f ->
                            f.isFile && f.name.startsWith("backup", ignoreCase = true) && f.name.endsWith(".csv", ignoreCase = true)
                        }
                        files?.forEach { f ->
                            try {
                                buildCandidate(3, "ext:$autoSub/${f.name}", f.readBytes(), f.lastModified())
                                    ?.let { tier3Legacy.add(it) }
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // Tier 4: App-private internal storage safety net (filesDir/backups/auto_backup.csv)
        try {
            val candidateFiles = listOf(
                File(context.filesDir, "backups/auto_backup.csv"),
                File(context.filesDir, "backups/$AUTO_BACKUP_FILE")
            )
            for (internalFile in candidateFiles) {
                if (internalFile.exists() && internalFile.length() > 0L) {
                    val bytes = internalFile.readBytes()
                    val content = String(bytes, Charsets.UTF_8)
                    if (bytes.isNotEmpty() && isValidInternalBackup(content)) {
                        buildCandidate(4, "internal:${internalFile.name}", bytes, internalFile.lastModified())
                            ?.let { tier4Internal.add(it) }
                    }
                }
            }
            if (includeMasterCsv) {
                val rootInternal = File(context.filesDir, DEFAULT_MANUAL_FILE)
                if (rootInternal.exists() && rootInternal.length() > 0L) {
                    buildCandidate(4, "internal:$DEFAULT_MANUAL_FILE", rootInternal.readBytes(), rootInternal.lastModified())
                        ?.let { tier4Internal.add(it) }
                }
            }
        } catch (_: Exception) {}

        val sortedT1 = tier1Saf.sortedWith(candidateWithinTierComparator)
        val sortedT2 = tier2MediaStoreDefault.sortedWith(candidateWithinTierComparator)
        val sortedT3 = tier3Legacy.sortedWith(candidateWithinTierComparator)
        val sortedT4 = tier4Internal.sortedWith(candidateWithinTierComparator)

        val realRowCandidates = (sortedT1.filter { it.hasRealRows } +
            sortedT2.filter { it.hasRealRows } +
            sortedT3.filter { it.hasRealRows } +
            sortedT4.filter { it.hasRealRows })
        val fallbackCandidates = (sortedT1.filter { !it.hasRealRows } +
            sortedT2.filter { !it.hasRealRows } +
            sortedT3.filter { !it.hasRealRows } +
            sortedT4.filter { !it.hasRealRows })

        return realRowCandidates + fallbackCandidates
    }

    fun openBestBackupCandidateStream(
        context: Context,
        includeMasterCsv: Boolean = true
    ): InputStream? {
        val best = rankBackupCandidates(context, includeMasterCsv).firstOrNull() ?: return null
        return ByteArrayInputStream(best.bytes)
    }

    /**
     * Reads the rolling auto-save file via the shared 4-tier ranking helper:
     * (1) Active SAF tree -> (2) MediaStore default Downloads/SilentSums -> (3) Legacy roots -> (4) Internal safety-net.
     */
    fun readAutoSave(context: Context): InputStream? {
        return openBestBackupCandidateStream(context, includeMasterCsv = false)
    }

    /**
     * Checks if rolling auto-save file exists.
     */
    fun hasAutoSave(context: Context): Boolean {
        return readAutoSave(context)?.use { true } ?: false
    }

    /**
     * Reads the most recent filtered export file from ExpenseTracker/downloads/ or candidate folders via MediaStore.
     */
    fun readLatestFilteredExport(context: Context): Pair<InputStream, String>? {
        val resolver = context.contentResolver
        val downloadsUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Files.getContentUri("external")
        }

        try {
            resolver.query(
                downloadsUri,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH),
                null,
                null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val pathIdx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                while (cursor.moveToNext()) {
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                    val path = if (pathIdx >= 0) cursor.getString(pathIdx) ?: "" else ""
                    if (name.endsWith(".csv", ignoreCase = true) &&
                        (path.isEmpty() || CANDIDATE_EXPORT_FOLDERS.any { path.contains(it, ignoreCase = true) })) {
                        val id = cursor.getLong(idIdx)
                        val fileUri = Uri.withAppendedPath(downloadsUri, id.toString())
                        val stream = resolver.openInputStream(fileUri)
                        if (stream != null) return Pair(stream, name)
                    }
                }
            }
        } catch (_: Exception) {}

        // Secondary check: app-private external files dir
        try {
            val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (extDir != null) {
                for (exportSub in CANDIDATE_EXPORT_FOLDERS) {
                    val dir = File(extDir, exportSub)
                    if (dir.exists()) {
                        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".csv") }
                        if (!files.isNullOrEmpty()) {
                            val latest = files.maxByOrNull { it.lastModified() }
                            if (latest != null) return Pair(FileInputStream(latest), latest.name)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // JVM / test filesystem fallback safely wrapped in try-catch
        try {
            for (rootDir in CANDIDATE_ROOT_FOLDERS) {
                for (exportSub in CANDIDATE_EXPORT_FOLDERS) {
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "$rootDir/$exportSub"
                    )
                    if (dir.exists()) {
                        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".csv") }
                        if (!files.isNullOrEmpty()) {
                            val latest = files.maxByOrNull { it.lastModified() }
                            if (latest != null) {
                                try {
                                    return Pair(FileInputStream(latest), latest.name)
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return null
    }
}
