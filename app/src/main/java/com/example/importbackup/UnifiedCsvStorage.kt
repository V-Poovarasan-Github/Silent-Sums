package com.example.importbackup

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.datastore.AppSettings
import com.example.data.datastore.AppSettingsRepository
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.Commitment
import com.example.data.model.CustomField
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import kotlinx.coroutines.flow.first
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RestoreResult(
    val inserted: Int,
    val skipped: Int = 0,
    val total: Int = 0,
    val relinked: Int = 0,
    val conflicts: Int = 0,
    val sourceRows: Int = total,
    val skippedDup: Int = skipped,
    val skippedFailed: Int = 0,
    val reachedEnd: Boolean = true,
    val failedLines: List<Int> = emptyList()
)

object UnifiedCsvStorage {
    const val CSV_FILE_NAME = "expense_tracker.csv"
    val CSV_FOLDER_NAME: String get() = MediaStoreWriter.ROOT_FOLDER
    val PRIMARY_AUTO_SAVE_RELATIVE_PATH: String
        get() = "${MediaStoreWriter.ROOT_FOLDER}/${MediaStoreWriter.AUTO_SUBFOLDER}/${MediaStoreWriter.AUTO_BACKUP_FILE}"

    val CANDIDATE_ROOT_FOLDERS = MediaStoreWriter.CANDIDATE_ROOT_FOLDERS
    val CANDIDATE_AUTO_FOLDERS = MediaStoreWriter.CANDIDATE_AUTO_FOLDERS
    val CANDIDATE_EXPORT_FOLDERS = MediaStoreWriter.CANDIDATE_EXPORT_FOLDERS
    // Restricted exclusively to the two master backup files
    val CANDIDATE_CSV_NAMES = listOf("expense_tracker.csv", "backup.csv")

    /**
     * Saves all app transactions, sources, and expenses into the single unified CSV file
     * in the user's phone storage (Downloads/SilentSums/expense_tracker.csv) via MediaStore.
     */
    fun saveToDeviceCsv(
        context: Context,
        transactions: List<TransactionEntity>,
        accounts: List<Account>,
        categories: List<Category>,
        commitments: List<Commitment>,
        budgets: List<com.example.data.model.BudgetEntity> = emptyList()
    ): String = saveToDeviceCsv(context, transactions, accounts, categories, emptyList(), null, commitments, budgets)

    fun saveToDeviceCsv(
        context: Context,
        transactions: List<TransactionEntity>,
        accounts: List<Account>,
        categories: List<Category>,
        customFields: List<CustomField> = emptyList(),
        settings: AppSettings? = null,
        commitments: List<Commitment> = emptyList(),
        budgets: List<com.example.data.model.BudgetEntity> = emptyList(),
        settingsRepository: AppSettingsRepository? = null
    ): String {
        // UI greying is courtesy; the service-layer guard is law.
        // 4.4 FIREWALL: while KEY_DEMO_MODE == TRUE, suppress exports so fake numbers can NEVER masquerade as real.
        // Rationale: the gate (offer only at 0 real txn) protects the DATABASE; the firewall protects the BACKUP FILES. Belt and suspenders.
        val isDemo = settings?.demoMode ?: kotlinx.coroutines.runBlocking {
            try {
                (settingsRepository ?: AppSettingsRepository(context)).settingsFlow.first().demoMode
            } catch (_: Throwable) {
                false
            }
        }
        if (isDemo) {
            return "Export paused: Demo mode active"
        }
        val csvContent = CsvBackupManager.buildCsv(transactions, accounts, categories, customFields, settings, commitments, budgets)
        val effectiveSettings = settings ?: kotlinx.coroutines.runBlocking {
            try {
                (settingsRepository ?: AppSettingsRepository(context)).settingsFlow.first()
            } catch (_: Throwable) {
                null
            }
        }
        val treeUriStr = effectiveSettings?.autoSaveTreeUri ?: ""
        when (val root = MediaStoreWriter.resolveWriteRoot(context, treeUriStr)) {
            is MediaStoreWriter.WriteRoot.Saf -> {
                try {
                    val safUri = MediaStoreWriter.writeToSafTree(context, root.treeUri, "", CSV_FILE_NAME, csvContent, root.rootDoc)
                    if (safUri != null) {
                        try {
                            val internalDir = File(context.filesDir, "backups").apply { mkdirs() }
                            File(internalDir, "auto_backup.csv").writeText(csvContent, Charsets.UTF_8)
                        } catch (_: Exception) {}
                        return "${root.displayPath}/$CSV_FILE_NAME"
                    }
                } catch (lbe: MediaStoreWriter.LargerBackupExistsException) {
                    return lbe.message
                } catch (se: SecurityException) {
                    settingsRepository?.let { repo ->
                        kotlinx.coroutines.runBlocking {
                            repo.recordAutoSaveResult(
                                success = false,
                                statusText = "Permission lost or folder inaccessible; fell back to Downloads",
                                timestamp = System.currentTimeMillis()
                            )
                        }
                    }
                } catch (_: Exception) {}
            }
            is MediaStoreWriter.WriteRoot.MediaStoreDefault -> {
                if (root.fellBackFromInvalidSaf) {
                    settingsRepository?.let { repo ->
                        kotlinx.coroutines.runBlocking {
                            repo.recordAutoSaveResult(
                                success = false,
                                statusText = "Permission lost or folder inaccessible; fell back to Downloads",
                                timestamp = System.currentTimeMillis()
                            )
                        }
                    }
                }
            }
        }
        try {
            val defaultRoot = MediaStoreWriter.WriteRoot.MediaStoreDefault()
            MediaStoreWriter.writeToSharedDownloads(context, "", CSV_FILE_NAME, csvContent)
            try {
                val internalDir = File(context.filesDir, "backups").apply { mkdirs() }
                File(internalDir, "auto_backup.csv").writeText(csvContent, Charsets.UTF_8)
            } catch (_: Exception) {}
            return "${defaultRoot.displayPath}/$CSV_FILE_NAME"
        } catch (lbe: MediaStoreWriter.LargerBackupExistsException) {
            return lbe.message
        }
    }

    /**
     * Exports filtered transactions to <activeRoot>/downloads/export_<timestamp>.csv
     * via MediaStoreWriter.resolveWriteRoot().
     */
    fun exportFilteredLedgerCsv(
        context: Context,
        transactions: List<TransactionEntity>,
        accounts: List<Account>,
        categories: List<Category>,
        customTreeUriString: String? = null,
        settingsRepository: AppSettingsRepository? = null
    ): Uri? {
        // UI greying is courtesy; the service-layer guard is law.
        // 4.4 FIREWALL: while KEY_DEMO_MODE == TRUE, suppress exports so fake numbers can NEVER masquerade as real.
        val sRepo = settingsRepository ?: AppSettingsRepository(context)
        val settings = kotlinx.coroutines.runBlocking {
            try {
                sRepo.settingsFlow.first()
            } catch (_: Throwable) {
                null
            }
        }
        if (settings?.demoMode == true) return null
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val fileName = "filtered_export_${sdf.format(Date())}.csv"
        val csvContent = CsvBackupManager.buildCsv(transactions, accounts, categories)
        val treeUriStr = customTreeUriString ?: settings?.autoSaveTreeUri

        when (val root = MediaStoreWriter.resolveWriteRoot(context, treeUriStr)) {
            is MediaStoreWriter.WriteRoot.Saf -> {
                try {
                    val safUri = MediaStoreWriter.writeToSafTree(
                        context,
                        root.treeUri,
                        MediaStoreWriter.DOWNLOADS_SUBFOLDER,
                        fileName,
                        csvContent,
                        root.rootDoc
                    )
                    if (safUri != null) return safUri
                } catch (se: SecurityException) {
                    settingsRepository?.let { repo ->
                        kotlinx.coroutines.runBlocking {
                            repo.recordAutoSaveResult(
                                success = false,
                                statusText = "Permission lost or folder inaccessible; fell back to Downloads",
                                timestamp = System.currentTimeMillis()
                            )
                        }
                    }
                } catch (_: Exception) {}
            }
            is MediaStoreWriter.WriteRoot.MediaStoreDefault -> {
                if (root.fellBackFromInvalidSaf) {
                    settingsRepository?.let { repo ->
                        kotlinx.coroutines.runBlocking {
                            repo.recordAutoSaveResult(
                                success = false,
                                statusText = "Permission lost or folder inaccessible; fell back to Downloads",
                                timestamp = System.currentTimeMillis()
                            )
                        }
                    }
                }
            }
        }
        return MediaStoreWriter.writeToSharedDownloads(context, MediaStoreWriter.DOWNLOADS_SUBFOLDER, fileName, csvContent)
    }

    /**
     * Checks if the unified CSV file or auto-save backup exists anywhere in user storage via MediaStore.
     */
    fun hasStoredCsv(context: Context): Boolean {
        try {
            val resolver = context.contentResolver
            val downloadsUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Files.getContentUri("external")
            }
            resolver.query(
                downloadsUri,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH),
                null,
                null,
                null
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val pathIdx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                while (cursor.moveToNext()) {
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                    val path = if (pathIdx >= 0) cursor.getString(pathIdx) ?: "" else ""
                    if (name.endsWith(".csv", ignoreCase = true) &&
                        (path.isEmpty() || CANDIDATE_ROOT_FOLDERS.any { path.contains(it, ignoreCase = true) })) {
                        return true
                    }
                }
            }
        } catch (_: Exception) { }

        // App-private external storage check
        try {
            val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (extDir != null && extDir.exists()) {
                val found = extDir.walkTopDown().any { it.isFile && it.name.endsWith(".csv", ignoreCase = true) }
                if (found) return true
            }
        } catch (_: Exception) {}

        return findStoredCsvFile(context) != null || findStoredAutoSaveFile(context) != null || findAnyStoredExportFile(context) != null
    }

    fun hasAutoSave(context: Context): Boolean {
        if (MediaStoreWriter.hasAutoSave(context)) return true
        return findStoredAutoSaveFile(context) != null
    }

    fun findAnyStoredExportFile(context: Context): File? {
        try {
            val pubDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            for (rootDir in CANDIDATE_ROOT_FOLDERS) {
                for (exportSub in CANDIDATE_EXPORT_FOLDERS) {
                    val dir = File(pubDownloads, "$rootDir/$exportSub")
                    if (dir.exists()) {
                        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".csv") }
                        if (!files.isNullOrEmpty()) {
                            return files.maxByOrNull { it.lastModified() }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        if (extDir != null) {
            for (exportSub in CANDIDATE_EXPORT_FOLDERS) {
                val dir = File(extDir, exportSub)
                if (dir.exists()) {
                    val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".csv") }
                    if (!files.isNullOrEmpty()) {
                        return files.maxByOrNull { it.lastModified() }
                    }
                }
            }
        }
        return null
    }

    fun findStoredAutoSaveFile(context: Context): File? {
        try {
            val pubDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            for (rootDir in CANDIDATE_ROOT_FOLDERS) {
                for (autoSub in CANDIDATE_AUTO_FOLDERS) {
                    val file = File(pubDownloads, "$rootDir/$autoSub/${MediaStoreWriter.AUTO_BACKUP_FILE}")
                    if (file.exists() && file.length() > 0) return file
                }
            }
        } catch (_: Exception) {}

        val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        if (extDir != null) {
            for (autoSub in CANDIDATE_AUTO_FOLDERS) {
                val file = File(extDir, "$autoSub/${MediaStoreWriter.AUTO_BACKUP_FILE}")
                if (file.exists() && file.length() > 0) return file
            }
        }
        return null
    }

    fun findStoredCsvFile(context: Context): File? {
        try {
            val pubDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            for (rootDir in CANDIDATE_ROOT_FOLDERS) {
                for (csvName in CANDIDATE_CSV_NAMES) {
                    val file = File(pubDownloads, "$rootDir/$csvName")
                    if (file.exists() && file.length() > 0) return file
                }
            }
        } catch (_: Exception) {}

        val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        if (extDir != null) {
            val file = File(extDir, CSV_FILE_NAME)
            if (file.exists() && file.length() > 0) return file
        }
        return null
    }

    fun hasRealTransactionRows(content: String): Boolean {
        for (line in content.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#") || trimmed.startsWith("Date,") || trimmed.startsWith("Name,") || trimmed.startsWith("type,") || trimmed.isBlank()) continue
            val upper = trimmed.uppercase()
            if (upper.contains(",EXPENSE,") || upper.contains(",INCOME,") || upper.contains(",TRANSFER,")) {
                return true
            }
        }
        return false
    }

    /**
     * Opens an InputStream directly from one of the two allowed master CSV files via MediaStore:
     * 1. Downloads/ExpenseTracker/auto/backup.csv
     * 2. Downloads/ExpenseTracker/expense_tracker.csv
     *
     * STANDING RULE: Any future export_*.csv or downloads/-subfolder exports must NEVER
     * enter the auto-restore pool; such files are importable ONLY via the D1 SAF picker.
     * A partial slice can never masquerade as the whole ledger.
     */
    fun openStoredCsvInputStream(context: Context): InputStream? {
        return MediaStoreWriter.openBestBackupCandidateStream(context, includeMasterCsv = true)
    }

    fun openAutoSaveInputStream(context: Context): InputStream? {
        return MediaStoreWriter.openBestBackupCandidateStream(context, includeMasterCsv = false)
    }

    fun openLatestFilteredExportInputStream(context: Context): Pair<InputStream, String>? {
        return MediaStoreWriter.readLatestFilteredExport(context)
    }

    private fun openSpecificCsvInputStream(context: Context, fileName: String, subFolder: String): InputStream? {
        // 0. Check active SAF write root if configured
        try {
            val treeUriStr = kotlinx.coroutines.runBlocking {
                try { AppSettingsRepository(context).settingsFlow.first().autoSaveTreeUri } catch (_: Throwable) { "" }
            }
            val root = MediaStoreWriter.resolveWriteRoot(context, treeUriStr)
            if (root is MediaStoreWriter.WriteRoot.Saf) {
                if (root.treeUri.scheme == "file") {
                    val base = File(root.treeUri.path ?: "")
                    val target = if (subFolder.isBlank()) File(base, fileName) else File(File(base, subFolder), fileName)
                    if (target.exists() && target.length() > 0L) return FileInputStream(target)
                } else {
                    val dirDoc = if (subFolder.isBlank()) root.rootDoc else root.rootDoc.findFile(subFolder)
                    val fileDoc = dirDoc?.findFile(fileName)
                    if (fileDoc != null) {
                        val stream = context.contentResolver.openInputStream(fileDoc.uri)
                        if (stream != null) return stream
                    }
                }
            }
        } catch (_: Throwable) {}

        // 1. Read via MediaStore
        val fromWriter = MediaStoreWriter.readFromSharedDownloads(context, subFolder, fileName)
        if (fromWriter != null) return fromWriter

        // 2. Read from app-private documents directory
        try {
            val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (extDir != null) {
                for (rootDir in CANDIDATE_ROOT_FOLDERS) {
                    val extFile = if (subFolder.isBlank()) File(extDir, "$rootDir/$fileName") else File(extDir, "$rootDir/$subFolder/$fileName")
                    if (extFile.exists() && extFile.length() > 0) return FileInputStream(extFile)
                }
                val simpleExt = if (subFolder.isBlank()) File(extDir, fileName) else File(extDir, "$subFolder/$fileName")
                if (simpleExt.exists() && simpleExt.length() > 0) return FileInputStream(simpleExt)
            }
        } catch (_: Throwable) {}

        // 3. Read from app-private internal files directory
        try {
            val internalFile = File(context.filesDir, fileName)
            if (internalFile.exists() && internalFile.length() > 0) return FileInputStream(internalFile)
        } catch (_: Throwable) {}

        return null
    }

    fun exportFilteredTransactions(
        context: Context,
        transactions: List<TransactionEntity>,
        accounts: List<Account>,
        categories: List<Category>,
        customTreeUriString: String? = null,
        settingsRepository: AppSettingsRepository? = null
    ): String {
        // UI greying is courtesy; the service-layer guard is law.
        // 4.4 FIREWALL: while KEY_DEMO_MODE == TRUE, suppress exports so fake numbers can NEVER masquerade as real.
        val sRepo = settingsRepository ?: AppSettingsRepository(context)
        val settings = kotlinx.coroutines.runBlocking {
            try {
                sRepo.settingsFlow.first()
            } catch (_: Throwable) {
                null
            }
        }
        if (settings?.demoMode == true) {
            return "Export paused: Demo mode active"
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "filtered_export_$timestamp.csv"
        val csvContent = CsvBackupManager.buildCsv(transactions, accounts, categories)
        val treeUriStr = customTreeUriString ?: settings?.autoSaveTreeUri

        when (val root = MediaStoreWriter.resolveWriteRoot(context, treeUriStr)) {
            is MediaStoreWriter.WriteRoot.Saf -> {
                try {
                    val safUri = MediaStoreWriter.writeToSafTree(
                        context,
                        root.treeUri,
                        MediaStoreWriter.DOWNLOADS_SUBFOLDER,
                        fileName,
                        csvContent,
                        root.rootDoc
                    )
                    if (safUri != null) {
                        return "${root.displayPath}/${MediaStoreWriter.DOWNLOADS_SUBFOLDER}/$fileName"
                    }
                } catch (_: Exception) {
                    // Fall back to MediaStore default if SAF write fails mid-flight
                }
            }
            is MediaStoreWriter.WriteRoot.MediaStoreDefault -> {
                // Handled below via MediaStore default
            }
        }

        val defaultRoot = MediaStoreWriter.WriteRoot.MediaStoreDefault()
        MediaStoreWriter.writeToSharedDownloads(context, MediaStoreWriter.DOWNLOADS_SUBFOLDER, fileName, csvContent)
        return "${defaultRoot.displayPath}/${MediaStoreWriter.DOWNLOADS_SUBFOLDER}/$fileName"
    }

    /**
     * Reads from the user's stored CSV file via MediaStore and restores memory directly.
     */
    suspend fun restoreFromMobileStorage(
        context: Context,
        repository: FinanceRepository,
        clearExisting: Boolean = false,
        settingsRepository: AppSettingsRepository? = null
    ): RestoreResult {
        // UI greying is courtesy; the service-layer guard is law.
        // 4.4 FIREWALL: while KEY_DEMO_MODE == TRUE, suppress restore to protect real backups.
        val sRepo = settingsRepository ?: AppSettingsRepository(context)
        if (sRepo.settingsFlow.first().demoMode) {
            throw IllegalStateException("Restore blocked: Demo mode active")
        }
        val stream = openStoredCsvInputStream(context)
            ?: throw Exception("No app-owned backup found. Files from an earlier install (orphaned after a reinstall) are invisible to auto-restore — use 'Import a CSV file' and pick it from your older backup folder (Downloads/ExpenseTracker or similar).")
        return stream.use { s ->
            restoreFromInputStream(repository, s, clearExisting, sRepo, context)
        }
    }

    /**
     * Restores specifically from the rolling auto-save file (Downloads/<ROOT_FOLDER>/auto/backup.csv).
     */
    suspend fun restoreFromAutoSave(
        context: Context,
        repository: FinanceRepository,
        clearExisting: Boolean = false,
        settingsRepository: AppSettingsRepository? = null
    ): RestoreResult {
        // UI greying is courtesy; the service-layer guard is law.
        // 4.4 FIREWALL: while KEY_DEMO_MODE == TRUE, suppress restore to protect real backups.
        val sRepo = settingsRepository ?: AppSettingsRepository(context)
        if (sRepo.settingsFlow.first().demoMode) {
            throw IllegalStateException("Restore blocked: Demo mode active")
        }
        val stream = openAutoSaveInputStream(context)
            ?: throw Exception("No app-owned backup found. Files from an earlier install (orphaned after a reinstall) are invisible to auto-restore — use 'Import a CSV file' and pick it from your older backup folder (Downloads/ExpenseTracker or similar).")
        return stream.use { s ->
            restoreFromInputStream(repository, s, clearExisting, sRepo, context)
        }
    }

    /**
     * Reads from a user-selected SAF URI and restores memory directly.
     */
    suspend fun restoreFromUri(
        context: Context,
        uri: Uri,
        repository: FinanceRepository,
        clearExisting: Boolean = false,
        settingsRepository: AppSettingsRepository? = null
    ): RestoreResult {
        // UI greying is courtesy; the service-layer guard is law.
        // 4.4 FIREWALL: while KEY_DEMO_MODE == TRUE, suppress restore to protect real backups.
        val sRepo = settingsRepository ?: AppSettingsRepository(context)
        if (sRepo.settingsFlow.first().demoMode) {
            throw IllegalStateException("Restore blocked: Demo mode active")
        }
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Could not open selected CSV file")
        return stream.use { s ->
            restoreFromInputStream(repository, s, clearExisting, sRepo, context)
        }
    }

    /**
     * Non-fatal launch restore check:
     * Automatically attempts to restore from existing device storage via MediaStore (e.g. 52-row backup.csv).
     * If restore fails for ANY reason (permission, parse, empty), it safely falls through to clean defaults
     * and NEVER blocks or crashes app launch.
     */
    suspend fun checkAndRestoreOrSeed(
        context: Context,
        repository: FinanceRepository,
        settingsRepository: AppSettingsRepository? = null
    ): Boolean {
        try {
            val accounts = repository.allAccounts.first()
            if (accounts.isEmpty()) {
                // Inward migration check:
                // Check if legacy Downloads/ExpenseTracker/auto/backup.csv exists and migrate inward
                try {
                    val legacyStream = MediaStoreWriter.readFromSharedDownloads(
                        context,
                        MediaStoreWriter.AUTO_SUBFOLDER,
                        MediaStoreWriter.AUTO_BACKUP_FILE
                    ) ?: findStoredAutoSaveFile(context)?.let { if (it.exists() && it.length() > 0) FileInputStream(it) else null }

                    if (legacyStream != null) {
                        val legacyBytes = legacyStream.use { it.readBytes() }
                        val legacyContent = String(legacyBytes, Charsets.UTF_8)
                        if (hasRealTransactionRows(legacyContent)) {
                            val internalFile = File(context.filesDir, "backups/auto_backup.csv")
                            val shouldMigrate = !internalFile.exists() || internalFile.length() == 0L || run {
                                try { !hasRealTransactionRows(internalFile.readText()) } catch (_: Exception) { true }
                            }
                            if (shouldMigrate) {
                                val backupDir = File(context.filesDir, "backups")
                                if (!backupDir.exists()) backupDir.mkdirs()
                                internalFile.writeText(legacyContent)
                                try { File(backupDir, MediaStoreWriter.AUTO_BACKUP_FILE).writeText(legacyContent) } catch (_: Exception) {}
                            }
                        }
                    }
                } catch (_: Throwable) {}

                val stream = try {
                    openStoredCsvInputStream(context)
                } catch (_: Throwable) {
                    null
                }
                if (stream != null) {
                    try {
                        stream.use { s ->
                            restoreFromInputStream(repository, s, clearExisting = false, settingsRepository = settingsRepository)
                        }
                        settingsRepository?.setOnboardingCompleted(true)
                        return true
                    } catch (_: Throwable) {
                        return false
                    }
                } else {
                    return false
                }
            }
        } catch (_: Throwable) {
            return false
        }
        return false
    }

    /**
     * Restores transactions from any input stream (file or SAF URI) with strict duplicate prevention.
     */
    suspend fun restoreFromInputStream(
        repository: FinanceRepository,
        inputStream: InputStream,
        clearExisting: Boolean = false,
        settingsRepository: AppSettingsRepository? = null,
        context: Context? = null
    ): RestoreResult {
        // 4.4 FIREWALL: while KEY_DEMO_MODE == TRUE, suppress restore to protect real backups.
        // Rationale: the gate (offer only at 0 real txn) protects the DATABASE;
        // the firewall protects the BACKUP FILES. Belt and suspenders.
        settingsRepository?.let { repo ->
            val settings = repo.settingsFlow.first()
            // UI greying is courtesy; the service-layer guard is law.
            if (settings.demoMode) {
                throw IllegalStateException("Restore blocked: Demo mode active")
            }
        }

        val fullText = inputStream.bufferedReader().use { it.readText() }
        val parsed = CsvBackupManager.parseCsvFull(ByteArrayInputStream(fullText.toByteArray(Charsets.UTF_8)))
        val rows = parsed.transactions
        if (rows.isEmpty() && parsed.dues.isEmpty() && parsed.accounts.isEmpty()) {
            throw Exception("No valid transaction rows found in CSV")
        }

        // Count source transaction rows and detect poisoned/failed lines
        val rawLines = CsvBackupManager.splitLines(fullText)
        var inTxSection = true
        var headerPassed = false
        var sourceTxCount = 0
        val failedLinesList = mutableListOf<Int>()

        for ((idx, rawLine) in rawLines.withIndex()) {
            val lineNum = idx + 1
            val trimmed = rawLine.trim().removePrefix("\uFEFF")
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("#")) {
                val upper = trimmed.uppercase()
                if (upper.startsWith("#FORMAT=") || upper.startsWith("#TRANSACTIONS")) {
                    inTxSection = true
                    headerPassed = false
                } else {
                    inTxSection = false
                    headerPassed = false
                }
                continue
            }
            if (inTxSection) {
                if (!headerPassed) {
                    if (trimmed.contains("Date", ignoreCase = true) ||
                        trimmed.contains("Amount", ignoreCase = true) ||
                        trimmed.contains("Debit", ignoreCase = true) ||
                        trimmed.contains("Type", ignoreCase = true)
                    ) {
                        headerPassed = true
                        continue
                    }
                } else {
                    sourceTxCount++
                }
            }
        }

        val validParsedTxCount = rows.size
        val effectiveSourceRows = maxOf(sourceTxCount, validParsedTxCount)
        val skippedFailedCount = maxOf(0, effectiveSourceRows - validParsedTxCount)

        if (skippedFailedCount > 0) {
            inTxSection = true
            headerPassed = false
            for ((idx, rawLine) in rawLines.withIndex()) {
                val lineNum = idx + 1
                val trimmed = rawLine.trim().removePrefix("\uFEFF")
                if (trimmed.isEmpty()) continue
                if (trimmed.startsWith("#")) {
                    val upper = trimmed.uppercase()
                    if (upper.startsWith("#FORMAT=") || upper.startsWith("#TRANSACTIONS")) {
                        inTxSection = true
                        headerPassed = false
                    } else {
                        inTxSection = false
                        headerPassed = false
                    }
                    continue
                }
                val isTxLine = if (inTxSection) {
                    if (!headerPassed) {
                        if (trimmed.contains("Date", ignoreCase = true) ||
                            trimmed.contains("Amount", ignoreCase = true) ||
                            trimmed.contains("Debit", ignoreCase = true) ||
                            trimmed.contains("Type", ignoreCase = true)
                        ) {
                            headerPassed = true
                            false
                        } else false
                    } else true
                } else false

                if (isTxLine) {
                    val testCsv = "Date,Type,Amount,Description\n$rawLine"
                    val testParsed = try {
                        CsvBackupManager.parseCsv(ByteArrayInputStream(testCsv.toByteArray(Charsets.UTF_8)))
                    } catch (_: Exception) {
                        emptyList()
                    }
                    if (testParsed.isEmpty()) {
                        failedLinesList.add(lineNum)
                    }
                }
            }
        }

        if (clearExisting) {
            repository.deleteAllTransactions()
            repository.clearAllCommitments()
            repository.clearAllBudgets()
        }
        settingsRepository?.setDemoMode(false)

        val existingAccounts = repository.allAccounts.first().toMutableList()
        val existingCategories = repository.allCategories.first().toMutableList()
        val accountMap = existingAccounts.associateBy { it.name.lowercase().trim() }.toMutableMap()
        val categoryMap = existingCategories.associateBy { it.name.lowercase().trim() }.toMutableMap()

        // 1. Recreate / update Accounts from #ACCOUNTS section BEFORE mapping transactions
        for (accRow in parsed.accounts) {
            val key = accRow.name.lowercase().trim()
            val existing = accountMap[key]
            if (existing != null) {
                val updated = existing.copy(
                    accountType = accRow.type.ifBlank { existing.accountType },
                    openingBalance = accRow.opening,
                    archived = accRow.archived
                )
                repository.updateAccount(updated)
                accountMap[key] = updated
                val idx = existingAccounts.indexOfFirst { it.id == existing.id }
                if (idx >= 0) existingAccounts[idx] = updated
            } else {
                val newAcc = Account(
                    name = accRow.name.trim(),
                    accountType = accRow.type.ifBlank { "BANK" },
                    openingBalance = accRow.opening,
                    archived = accRow.archived,
                    sortOrder = accountMap.size + 1
                )
                val newId = repository.insertAccount(newAcc)
                val created = newAcc.copy(id = newId)
                accountMap[key] = created
                existingAccounts.add(created)
            }
        }

        // 2. Recreate / update Categories from #CATEGORIES section BEFORE mapping transactions
        for (catRow in parsed.categories) {
            val key = catRow.name.lowercase().trim()
            val existing = categoryMap[key]
            if (existing != null) {
                val updated = existing.copy(
                    color = catRow.color.ifBlank { existing.color },
                    archived = catRow.archived,
                    iconText = catRow.iconText ?: existing.iconText
                )
                repository.updateCategory(updated)
                categoryMap[key] = updated
                val idx = existingCategories.indexOfFirst { it.id == existing.id }
                if (idx >= 0) existingCategories[idx] = updated
            } else {
                val newCat = Category(
                    name = catRow.name.trim(),
                    color = catRow.color.ifBlank { "#3B82F6" },
                    archived = catRow.archived,
                    sortOrder = categoryMap.size + 1,
                    iconText = catRow.iconText
                )
                val newId = repository.insertCategory(newCat)
                val created = newCat.copy(id = newId)
                categoryMap[key] = created
                existingCategories.add(created)
            }
        }

        // 3. Recreate Custom Fields from #CUSTOMFIELDS section
        if (parsed.customFields.isNotEmpty()) {
            val existingFields = repository.allCustomFields.first().toMutableList()
            val fieldMap = existingFields.associateBy { it.name.lowercase().trim() }.toMutableMap()
            for (cfRow in parsed.customFields) {
                val key = cfRow.name.lowercase().trim()
                if (!fieldMap.containsKey(key)) {
                    val newField = CustomField(
                        name = cfRow.name.trim(),
                        type = cfRow.type.ifBlank { "TEXT" },
                        options = cfRow.options,
                        sortOrder = fieldMap.size + 1
                    )
                    val newId = repository.insertCustomField(newField)
                    fieldMap[key] = newField.copy(id = newId)
                }
            }
        }

        // 4. Apply Settings keys present (Never include PIN, lock data, theme, hideBalances, or soundHaptics)
        if (settingsRepository != null && parsed.settings.isNotEmpty()) {
            parsed.settings["currencySymbol"]?.let { settingsRepository.setCurrency(it) }
            parsed.settings["monthStartDay"]?.toIntOrNull()?.let { settingsRepository.setMonthStartDay(it) }
        }

        // Auto-create missing accounts
        val colors = listOf("#3B82F6", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899", "#06B6D4")
        var colorIdx = 0

        suspend fun getOrCreateAccount(name: String, forceBank: Boolean = false): Long? {
            val trimmed = name.trim()
            if (trimmed.isBlank()) return null
            val key = trimmed.lowercase()
            accountMap[key]?.let { return it.id }
            val accType = if (forceBank) "BANK" else CsvBackupManager.guessAccountType(trimmed)
            val newAcc = Account(
                name = trimmed,
                openingBalance = 0.0,
                accountType = accType
            )
            val newId = repository.insertAccount(newAcc)
            val created = newAcc.copy(id = newId)
            accountMap[key] = created
            existingAccounts.add(created)
            return newId
        }

        suspend fun getOrCreateCategory(name: String): Long? {
            val trimmed = name.trim()
            if (trimmed.isBlank()) return null
            val key = trimmed.lowercase()
            categoryMap[key]?.let { return it.id }
            val color = colors[colorIdx % colors.size]
            colorIdx++
            val newCat = Category(
                name = trimmed,
                color = color
            )
            val newId = repository.insertCategory(newCat)
            val created = newCat.copy(id = newId)
            categoryMap[key] = created
            existingCategories.add(created)
            return newId
        }

        val existingTx = if (clearExisting) mutableListOf() else repository.allTransactions.first().toMutableList()

        var insertedCount = 0
        var skippedCount = 0
        val toInsert = mutableListOf<TransactionEntity>()

        for (r in rows) {
            var fromId = if (r.fromAccount.isNotBlank()) getOrCreateAccount(r.fromAccount) else null
            var toId = if (r.toAccount.isNotBlank()) getOrCreateAccount(r.toAccount) else null
            val catId = if (r.category.isNotBlank()) getOrCreateCategory(r.category) else null

            if (r.type.uppercase() == "INCOME") {
                if (toId == null && fromId != null) {
                    toId = fromId
                    fromId = null
                }
            }

            val isDupe = !clearExisting && isDuplicate(
                existingList = existingTx,
                rDate = r.date,
                rType = r.type,
                rAmount = r.amount,
                fromId = fromId,
                toId = toId,
                catId = catId,
                desc = r.description,
                notes = r.notes
            )

            if (isDupe) {
                skippedCount++
                continue
            }

            val newEntity = TransactionEntity(
                date = r.date,
                type = r.type,
                amount = r.amount,
                fromAccountId = if (r.type.uppercase() == "INCOME") null else fromId,
                toAccountId = toId,
                categoryId = catId,
                description = r.description,
                notes = r.notes
            )
            toInsert.add(newEntity)
            existingTx.add(newEntity)
            insertedCount++
        }

        if (toInsert.isNotEmpty()) {
            repository.insertTransactions(toInsert)
        }

        // Restore commitments if #DUES was present
        val existingCommitments = if (clearExisting) mutableListOf() else repository.getAllCommitmentsList().toMutableList()
        for (d in parsed.dues) {
            val accId = if (d.account.isNotBlank()) getOrCreateAccount(d.account, forceBank = true) else null
            val catId = if (d.category.isNotBlank()) getOrCreateCategory(d.category) else null
            val existing = existingCommitments.firstOrNull {
                it.type.equals(d.type, ignoreCase = true) &&
                it.name.trim().equals(d.name.trim(), ignoreCase = true) &&
                kotlin.math.abs(it.amount - d.amount) < 0.009
            }
            if (existing != null) {
                val updated = existing.copy(
                    direction = d.direction,
                    dayOfMonth = d.dayOfMonth,
                    dueDate = d.dueDate,
                    accountId = accId ?: existing.accountId,
                    categoryId = catId ?: existing.categoryId,
                    active = d.active,
                    description = d.description.ifBlank { null } ?: existing.description
                )
                repository.updateCommitment(updated)
                val idx = existingCommitments.indexOfFirst { it.id == existing.id }
                if (idx >= 0) existingCommitments[idx] = updated
            } else {
                val commitment = Commitment(
                    type = d.type,
                    name = d.name.trim(),
                    amount = d.amount,
                    direction = d.direction,
                    dayOfMonth = d.dayOfMonth,
                    dueDate = d.dueDate,
                    accountId = accId,
                    categoryId = catId,
                    active = d.active,
                    description = d.description.ifBlank { null }
                )
                val newId = repository.insertCommitment(commitment)
                existingCommitments.add(commitment.copy(id = newId))
            }
        }

        // Restore budgets if #BUDGETS was present
        val existingBudgets = if (clearExisting) mutableListOf() else repository.getAllBudgetsList().toMutableList()
        for (b in parsed.budgets) {
            val existing = existingBudgets.firstOrNull {
                it.scopeType.equals(b.scopeType, ignoreCase = true) &&
                it.targetName.trim().equals(b.targetName.trim(), ignoreCase = true)
            }
            if (existing != null) {
                val updated = existing.copy(
                    name = b.name.ifBlank { existing.name },
                    limitAmount = b.limitAmount,
                    active = b.active
                )
                repository.updateBudget(updated)
                val idx = existingBudgets.indexOfFirst { it.id == existing.id }
                if (idx >= 0) existingBudgets[idx] = updated
            } else {
                val newBudget = com.example.data.model.BudgetEntity(
                    name = b.name,
                    scopeType = b.scopeType,
                    targetName = b.targetName,
                    limitAmount = b.limitAmount,
                    active = b.active
                )
                val newId = repository.insertBudget(newBudget)
                existingBudgets.add(newBudget.copy(id = newId))
            }
        }
        // I4: MediaRef Relinking on restore/import
        var relinkedCount = 0
        var conflictCount = 0

        if (context != null) {
            val allCurrentTx = repository.database.transactionDao().getAllTransactionsList()
            val mediaDao = repository.database.mediaAttachmentDao()
            val mediaRepo = com.example.data.repository.MediaRepository(context, repository.database)

            // For each inserted transaction carrying a mediaRef
            for (r in rows) {
                val ref = r.mediaRef
                if (ref.isNullOrBlank()) continue

                // Find the inserted transaction in the database matching this row
                val matchingTx = allCurrentTx.firstOrNull { tx ->
                    tx.date == r.date &&
                    kotlin.math.abs(tx.amount - r.amount) < 0.009 &&
                    tx.type.equals(r.type, ignoreCase = true) &&
                    tx.description == r.description
                } ?: continue

                // Find existing media attachments carrying this mediaRef
                val existingAtts = mediaDao.getAttachmentsByMediaRef(ref)
                if (existingAtts.isEmpty()) continue

                // Check conflict: is this mediaRef already attached to another live transaction?
                val conflictingTxId = existingAtts.firstOrNull { it.txnId != matchingTx.id }?.txnId
                val isLiveConflict = if (conflictingTxId != null) {
                    allCurrentTx.any { it.id == conflictingTxId }
                } else false

                if (isLiveConflict) {
                    conflictCount++
                } else {
                    // Relink orphaned media rows to the restored transaction
                    val oldTxnId = existingAtts.first().txnId
                    val oldDir = mediaRepo.getVaultDirForTxn(oldTxnId)
                    val newDir = mediaRepo.getVaultDirForTxn(matchingTx.id)

                    for (att in existingAtts) {
                        val srcFile = java.io.File(oldDir, att.fileName).let {
                            if (it.exists()) it else java.io.File(java.io.File(java.io.File(mediaRepo.mediaRoot, "vault"), "txn_$oldTxnId"), att.fileName)
                        }
                        if (srcFile.exists()) {
                            val destFile = java.io.File(newDir, att.fileName)
                            if (srcFile.canonicalPath != destFile.canonicalPath) {
                                srcFile.copyTo(destFile, overwrite = true)
                                srcFile.delete()
                            }
                        }
                    }
                    mediaDao.updateTxnIdForMediaRef(ref, matchingTx.id)
                    relinkedCount++
                }
            }
        }

        // FIX 3: Reset mutation stamp so subsequent auto-save sees nothing new and writes nothing
        val currentStamp = com.example.data.repository.MutationTracker.currentStamp
        settingsRepository?.let { repo ->
            repo.recordDataMutation(currentStamp)
            repo.recordAutoSaveResult(
                success = true,
                statusText = "Imported $insertedCount of $effectiveSourceRows rows ($skippedCount duplicates skipped, $skippedFailedCount failed)",
                timestamp = System.currentTimeMillis()
            )
        }

        return RestoreResult(
            inserted = insertedCount,
            skipped = skippedCount,
            total = effectiveSourceRows,
            relinked = relinkedCount,
            conflicts = conflictCount,
            sourceRows = effectiveSourceRows,
            skippedDup = skippedCount,
            skippedFailed = skippedFailedCount,
            reachedEnd = true,
            failedLines = failedLinesList
        )
    }

    private fun isDuplicate(
        existingList: List<TransactionEntity>,
        rDate: Long,
        rType: String,
        rAmount: Double,
        fromId: Long?,
        toId: Long?,
        catId: Long?,
        desc: String,
        notes: String = ""
    ): Boolean {
        val normType = rType.trim().uppercase()
        val normDesc = desc.trim().lowercase().ifEmpty { notes.trim().lowercase() }
        val normNotes = notes.trim().lowercase().ifEmpty { normDesc }
        val sdfMinute = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val rMinuteKey = sdfMinute.format(Date(rDate))
        val rIsMidnight = isMidnightTimestamp(rDate)

        for (ex in existingList) {
            val typeMatch = ex.type.trim().uppercase() == normType
            val amountMatch = kotlin.math.abs(ex.amount - rAmount) < 0.009
            if (!typeMatch || !amountMatch) continue

            val fromMatch = (ex.fromAccountId == null && fromId == null) || (ex.fromAccountId == fromId)
            val toMatch = (ex.toAccountId == null && toId == null) || (ex.toAccountId == toId)
            if (!fromMatch || !toMatch) continue

            val catMatch = (ex.categoryId == null && catId == null) || (ex.categoryId == catId)
            val exDesc = ex.description.trim().lowercase().ifEmpty { ex.notes.trim().lowercase() }
            val exNotes = ex.notes.trim().lowercase().ifEmpty { exDesc }
            val descMatch = exDesc == normDesc
            val notesMatch = exNotes == normNotes
            if (!catMatch || !descMatch || !notesMatch) continue

            val exMinuteKey = sdfMinute.format(Date(ex.date))
            val diffMs = kotlin.math.abs(ex.date - rDate)
            if (exMinuteKey == rMinuteKey || diffMs <= 60_000L) {
                return true
            }

            if (isSameCalendarDay(ex.date, rDate) && (rIsMidnight || isMidnightTimestamp(ex.date))) {
                return true
            }
        }
        return false
    }

    private fun isMidnightTimestamp(ts: Long): Boolean {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ts }
        return cal.get(java.util.Calendar.HOUR_OF_DAY) == 0 &&
               cal.get(java.util.Calendar.MINUTE) == 0 &&
               cal.get(java.util.Calendar.SECOND) == 0
    }

    private fun isSameCalendarDay(t1: Long, t2: Long): Boolean {
        val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = t1 }
        val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = t2 }
        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
               cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
    }
}
