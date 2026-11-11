package com.example.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.datastore.AppSettingsRepository
import com.example.data.repository.FinanceRepository
import com.example.importbackup.BackupManager
import com.example.importbackup.CsvImporter
import com.example.importbackup.CsvPreviewResult
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import android.os.Environment
import com.example.importbackup.CsvBackupManager
import com.example.importbackup.RestoreResult
import com.example.importbackup.UnifiedCsvStorage
import com.example.data.model.Account
import android.provider.MediaStore
import com.example.data.model.Category
import com.example.data.model.TransactionEntity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val MIRROR_OFF_DISCLOSURE = "Photos and voice notes live on this phone only. Restores re-link media still on this phone; a reinstall or new phone loses them unless Mirror is on."

data class SettingsUiState(
    val settings: com.example.data.datastore.AppSettings = com.example.data.datastore.AppSettings(),
    val transactionCount: Int = 0,
    val accountCount: Int = 0,
    val categoryCount: Int = 0,
    val storageEstimateMb: Double = 4.2,
    val storageUsedBytes: Long = 0L,
    val csvPreview: CsvPreviewResult? = null,
    val isImporting: Boolean = false,
    val statusMessage: String? = null,
    val mirrorOffDisclosure: String = MIRROR_OFF_DISCLOSURE,
    val safGrantNotice: String? = null
)

class SettingsViewModel(
    private val repository: FinanceRepository,
    val settingsRepository: AppSettingsRepository,
    private val database: AppDatabase
) : ViewModel() {

    private val _statusChannel = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val statusMessage: kotlinx.coroutines.flow.Flow<String> = _statusChannel.receiveAsFlow()

    private val _statusMessage = object {
        suspend fun emit(msg: String) {
            _statusChannel.send(msg)
        }
    }

    private val _csvPreview = MutableStateFlow<CsvPreviewResult?>(null)
    val csvPreview: StateFlow<CsvPreviewResult?> = _csvPreview

    private val _safGrantNotice = MutableStateFlow<String?>(null)
    val safGrantNotice: StateFlow<String?> = _safGrantNotice

    private val _showExitDemoAuthGate = MutableStateFlow(false)
    val showExitDemoAuthGate: StateFlow<Boolean> = _showExitDemoAuthGate

    private val _storageRefreshTick = MutableStateFlow(0L)

    private var hasCheckedSafGrant = false

    fun refreshStorageEstimate() {
        _storageRefreshTick.value = System.currentTimeMillis()
    }

    fun computeStorageUsedBytes(): Long {
        val dbPath = try {
            database.openHelper.readableDatabase.path
        } catch (_: Exception) {
            null
        }
        val dbBytes = if (dbPath != null) {
            val base = File(dbPath)
            val wal = File("$dbPath-wal")
            val shm = File("$dbPath-shm")
            (if (base.exists()) base.length() else 0L) +
                (if (wal.exists()) wal.length() else 0L) +
                (if (shm.exists()) shm.length() else 0L)
        } else {
            0L
        }
        val mediaRoot = File(settingsRepository.context.filesDir, "media")
        val mediaBytes = if (mediaRoot.exists()) {
            mediaRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else {
            0L
        }
        val backupRoot = File(settingsRepository.context.filesDir, "backups")
        val backupBytes = if (backupRoot.exists()) {
            backupRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else {
            0L
        }
        return dbBytes + mediaBytes + backupBytes
    }

    /**
     * R4: If autoSaveTreeUri is set but inaccessible/revoked, emit one-time notice.
     */
    suspend fun checkSafGrant(context: Context): Boolean {
        val settings = settingsRepository.settingsFlow.first()
        val treeUriStr = settings.autoSaveTreeUri
        if (treeUriStr.isNotBlank()) {
            val isInaccessible = try {
                val uri = Uri.parse(treeUriStr)
                val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                doc == null || !doc.exists() || !doc.canWrite()
            } catch (_: Exception) {
                true
            }
            if (isInaccessible) {
                val notice = "Your backup folder needs re-connecting. Tap here to pick it again."
                _safGrantNotice.value = notice
                _statusMessage.emit(notice)
                return true
            }
        }
        return false
    }

    fun checkSafGrantAsync(context: Context) {
        if (hasCheckedSafGrant) return
        hasCheckedSafGrant = true
        viewModelScope.launch {
            checkSafGrant(context)
        }
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settingsFlow,
        repository.allTransactions,
        repository.allAccounts,
        repository.allCategories,
        combine(
            _csvPreview,
            _safGrantNotice,
            database.mediaAttachmentDao().getAllAttachments(),
            _storageRefreshTick
        ) { preview, notice, attachments, tick -> Triple(preview, notice, attachments.size to tick) }
    ) { settings, transactions, accounts, categories, (preview, safNotice, _) ->
        com.example.domain.util.Formatters.updateCurrentCurrencySymbol(settings.currencySymbol)
        val txCount = transactions.size
        val totalBytes = computeStorageUsedBytes()
        val sizeMb = totalBytes / 1048576.0

        SettingsUiState(
            settings = settings,
            transactionCount = txCount,
            accountCount = accounts.size,
            categoryCount = categories.size,
            storageEstimateMb = sizeMb,
            storageUsedBytes = totalBytes,
            csvPreview = preview,
            safGrantNotice = safNotice
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setCurrency(symbol: String) {
        com.example.domain.util.Formatters.updateCurrentCurrencySymbol(symbol)
        viewModelScope.launch {
            settingsRepository.setCurrency(symbol)
            _statusMessage.emit("Currency updated to $symbol")
        }
    }

    fun setMonthStartDay(day: Int) {
        viewModelScope.launch {
            settingsRepository.setMonthStartDay(day)
            _statusMessage.emit("Month cycle anchor updated to Day $day")
        }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch {
            settingsRepository.setTheme(theme)
        }
    }

    fun setAutoThemeSource(source: String) {
        viewModelScope.launch {
            settingsRepository.setAutoThemeSource(source)
        }
    }

    fun setLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setLockEnabled(enabled)
            _statusMessage.emit(if (enabled) "Vault Lock enabled" else "Vault Lock disabled")
        }
    }

    fun setPin(pin: String) {
        viewModelScope.launch {
            settingsRepository.hashAndSavePin(pin)
            settingsRepository.setLockEnabled(true)
            _statusMessage.emit("PIN successfully saved")
        }
    }

    fun setLockTimeoutMs(timeoutMs: Long) {
        viewModelScope.launch {
            settingsRepository.setLockTimeoutMs(timeoutMs)
            val label = when (timeoutMs) {
                0L -> "Immediately"
                60_000L -> "1 minute"
                900_000L -> "15 minutes"
                3_600_000L -> "1 hour"
                else -> "${timeoutMs / 60_000L} minutes"
            }
            _statusMessage.emit("Auto-lock timeout set to $label")
        }
    }

    fun setHideBalances(hide: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHideBalances(hide)
        }
    }

    fun setSoundHaptics(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSoundHapticsEnabled(enabled)
        }
    }

    fun setDueReminders(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDueRemindersEnabled(enabled)
        }
    }

    fun setBudgetsCardVisible(visible: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBudgetsCardVisible(visible)
        }
    }

    fun loadSampleData(context: Context = settingsRepository.context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                com.example.MainActivity.markSessionUnlocked(context)
                com.example.data.DatabaseProvider.flipTo(context, settingsRepository, targetDemoMode = true)
                _statusMessage.emit("Sample ledger data loaded successfully!")
            } catch (t: Throwable) {
                if (com.example.BuildConfig.DEBUG) {
                    Log.e("SettingsViewModel", "Failed to load sample data", t)
                }
                val crashReport = "CRASH: ${t.javaClass.name}: ${t.message}\n" + t.stackTraceToString()
                try {
                    File(context.filesDir, "crash.log").appendText(crashReport)
                } catch (_: Throwable) {}
                _statusMessage.emit("${t.javaClass.simpleName}: ${t.message?.lines()?.firstOrNull() ?: "Failed to enter sample mode"}")
            }
        }
    }

    suspend fun loadSampleDataWithSafety(context: Context = settingsRepository.context) {
        try {
            com.example.MainActivity.markSessionUnlocked(context)
            com.example.data.DatabaseProvider.flipTo(context, settingsRepository, targetDemoMode = true)
            _statusMessage.emit("Sample ledger data loaded successfully!")
        } catch (t: Throwable) {
            if (com.example.BuildConfig.DEBUG) {
                Log.e("SettingsViewModel", "Failed to load sample data with safety", t)
            }
            val crashReport = "CRASH: ${t.javaClass.name}: ${t.message}\n" + t.stackTraceToString()
            try {
                File(context.filesDir, "crash.log").appendText(crashReport)
            } catch (_: Throwable) {}
            _statusMessage.emit("${t.javaClass.simpleName}: ${t.message?.lines()?.firstOrNull() ?: "Failed to enter sample mode"}")
        }
    }

    fun dismissExitDemoAuthGate() {
        _showExitDemoAuthGate.value = false
    }

    fun requestExitDemo(context: Context = settingsRepository.context) {
        viewModelScope.launch {
            requestExitDemoGate(
                context = context,
                settingsRepository = settingsRepository,
                onRequireAuth = { _showExitDemoAuthGate.value = true },
                onError = { msg -> _statusMessage.emit(msg) }
            )
        }
    }

    fun confirmExitDemoAfterAuth(context: Context = settingsRepository.context) {
        _showExitDemoAuthGate.value = false
        viewModelScope.launch {
            executeExitDemoFlip(
                context = context,
                settingsRepository = settingsRepository,
                onError = { msg -> _statusMessage.emit(msg) }
            )
        }
    }

    companion object {
        fun isLockEnabledForDemoExit(settings: com.example.data.datastore.AppSettings): Boolean {
            return settings.lockEnabled
        }

        suspend fun requestExitDemoGate(
            context: Context,
            settingsRepository: AppSettingsRepository,
            onRequireAuth: () -> Unit,
            onError: (suspend (String) -> Unit)? = null
        ) {
            val settings = settingsRepository.settingsFlow.first()
            if (isLockEnabledForDemoExit(settings)) {
                onRequireAuth()
            } else {
                executeExitDemoFlip(context, settingsRepository, onError)
            }
        }

        suspend fun executeExitDemoFlip(
            context: Context,
            settingsRepository: AppSettingsRepository,
            onError: (suspend (String) -> Unit)? = null
        ) {
            try {
                com.example.MainActivity.markSessionUnlocked(context)
                com.example.data.DatabaseProvider.flipTo(context, settingsRepository, targetDemoMode = false)
            } catch (t: Throwable) {
                if (com.example.BuildConfig.DEBUG) {
                    Log.e("SettingsViewModel", "Failed to exit demo mode", t)
                }
                val crashReport = "CRASH: ${t.javaClass.name}: ${t.message}\n" + t.stackTraceToString()
                try {
                    File(context.filesDir, "crash.log").appendText(crashReport)
                } catch (_: Throwable) {}
                onError?.invoke("${t.javaClass.simpleName}: ${t.message?.lines()?.firstOrNull() ?: "Failed to exit sample mode"}")
            }
        }
    }

    suspend fun reportStatus(msg: String) {
        _statusMessage.emit(msg)
    }

    fun clearAllData(context: Context = settingsRepository.context, deleteMirror: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            // UI greying is courtesy; the service-layer guard is law.
            val settings = settingsRepository.settingsFlow.first()
            val isDemo = settings.demoMode || com.example.data.DatabaseProvider.isDemoMode(context)
            if (isDemo) {
                _statusMessage.emit("Unavailable in sample mode")
                return@launch
            }
            com.example.data.DatabaseProvider.getMediaRepository(context).wipeAllMedia()
            if (deleteMirror) {
                try {
                    val mirrorTree = settings.mediaMirrorTreeUri.ifBlank { settings.autoSaveTreeUri }
                    if (mirrorTree.isNotBlank()) {
                        val treeUri = android.net.Uri.parse(mirrorTree)
                        if (treeUri.scheme == "file") {
                            val mirrorDir = java.io.File(treeUri.path ?: "").let {
                                if (it.name == "ExpenseTrackerMirror") it else java.io.File(it, "ExpenseTrackerMirror")
                            }
                            if (mirrorDir.exists()) mirrorDir.deleteRecursively()
                        } else {
                            val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
                            doc?.findFile("ExpenseTrackerMirror")?.delete()
                        }
                    }
                } catch (e: Throwable) {
                    if (com.example.BuildConfig.DEBUG) {
                        android.util.Log.e("SettingsViewModel", "Failed to clear mirror folder", e)
                    }
                }
            }
            repository.clearAllData()
            _statusMessage.emit("All ledger transactions cleared.")
        }
    }

    fun formatRestoreSummary(result: RestoreResult, prefix: String = ""): String {
        val x = result.inserted
        val n = result.sourceRows
        val y = result.skippedDup
        val z = result.skippedFailed
        val pre = if (prefix.isNotBlank()) "$prefix: " else ""
        return if (x == 0 && z == 0 && y == n && n > 0) {
            "${pre}All $n records up-to-date ($y duplicates skipped, 0 failed)"
        } else {
            "${pre}Imported $x of $n rows ($y duplicates skipped, $z failed)"
        }
    }

    suspend fun previewCsv(inputStream: InputStream, mergeTransfers: Boolean = true) {
        val settings = settingsRepository.settingsFlow.first()
        if (settings.demoMode) {
            _statusMessage.emit("Unavailable in sample mode")
            return
        }
        try {
            val accounts = repository.allAccounts.first()
            val categories = repository.allCategories.first()
            val result = CsvImporter.previewCsv(inputStream, accounts, categories, mergeTransfers)
            _csvPreview.value = result
        } catch (e: Exception) {
            _statusMessage.emit("Preview failed: ${e.localizedMessage}")
        }
    }

    fun reportFailure(msg: String) {
        viewModelScope.launch { _statusMessage.emit(msg) }
    }

    fun clearCsvPreview() {
        _csvPreview.value = null
    }

    suspend fun commitCsvImport(
        inputStream: InputStream,
        mergeTransfers: Boolean = false,
        clearExisting: Boolean = false
    ) {
        val settings = settingsRepository.settingsFlow.first()
        if (settings.demoMode) {
            _statusMessage.emit("Unavailable in sample mode")
            return
        }
        try {
            val result = UnifiedCsvStorage.restoreFromInputStream(
                repository = repository,
                inputStream = inputStream,
                clearExisting = clearExisting,
                settingsRepository = settingsRepository,
                context = settingsRepository.context
            )
            _csvPreview.value = null
            _statusMessage.emit(formatRestoreSummary(result))
        } catch (e: Exception) {
            _statusMessage.emit("Import failed: ${e.localizedMessage}")
        }
    }

    fun exportBackup(outputStream: OutputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            // UI greying is courtesy; the service-layer guard is law.
            val settings = settingsRepository.settingsFlow.first()
            if (settings.demoMode) {
                _statusMessage.emit("Unavailable in sample mode")
                return@launch
            }
            try {
                BackupManager.exportBackup(database, settingsRepository, outputStream)
                _statusMessage.emit("Backup exported successfully!")
            } catch (e: Exception) {
                _statusMessage.emit("Export failed: ${e.localizedMessage}")
            }
        }
    }

    fun restoreBackup(inputStream: InputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            // UI greying is courtesy; the service-layer guard is law.
            val settings = settingsRepository.settingsFlow.first()
            if (settings.demoMode) {
                _statusMessage.emit("Unavailable in sample mode")
                return@launch
            }
            try {
                val ok = BackupManager.restoreBackup(database, settingsRepository, inputStream)
                if (ok) {
                    _statusMessage.emit("Snapshot restored successfully!")
                } else {
                    _statusMessage.emit("Restore failed: Invalid file format.")
                }
            } catch (e: Exception) {
                _statusMessage.emit("Restore failed: ${e.localizedMessage}")
            }
        }
    }

    fun exportCsvReport(outputStream: OutputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            // UI greying is courtesy; the service-layer guard is law.
            val settings = settingsRepository.settingsFlow.first()
            if (settings.demoMode) {
                _statusMessage.emit("Unavailable in sample mode")
                return@launch
            }
            try {
                val csvData = generateCsvReport()
                outputStream.bufferedWriter().use { it.write(csvData) }
                _statusMessage.emit("Report exported successfully!")
            } catch (e: Exception) {
                _statusMessage.emit("Report export failed: ${e.localizedMessage}")
            }
        }
    }

    suspend fun exportCsvBackup(context: Context) {
        // UI greying is courtesy; the service-layer guard is law.
        val settings = settingsRepository.settingsFlow.first()
        if (settings.demoMode) {
            _statusMessage.emit("Unavailable in sample mode")
            return
        }
        try {
            val transactions = repository.allTransactions.first()
            val accounts = repository.allAccounts.first()
            val categories = repository.allCategories.first()
            val customFields = repository.allCustomFields.first()
            val commitments = repository.allCommitments.first()
            val budgets = repository.allBudgets.first()
            val result = UnifiedCsvStorage.saveToDeviceCsv(
                context, transactions, accounts, categories, customFields, settings, commitments, budgets, settingsRepository
            )
            if (result.contains("not overwriting it")) {
                _statusMessage.emit(result)
            } else {
                _statusMessage.emit("Unified CSV saved: $result (${transactions.size} records)")
            }
        } catch (lbe: com.example.importbackup.MediaStoreWriter.LargerBackupExistsException) {
            _statusMessage.emit(lbe.message)
        } catch (e: Exception) {
            _statusMessage.emit("CSV backup failed: ${e.localizedMessage}")
        }
    }

    fun restoreFromInternalSafetyNet(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            // UI greying is courtesy; the service-layer guard is law.
            val settings = settingsRepository.settingsFlow.first()
            if (settings.demoMode) {
                _statusMessage.emit("Unavailable in sample mode")
                return@launch
            }
            try {
                val internalFile = File(context.filesDir, "backups/auto_backup.csv")
                if (!internalFile.exists() || internalFile.length() == 0L) {
                    _statusMessage.emit("Internal safety-net backup not found")
                    return@launch
                }
                val content = internalFile.readText()
                if (!com.example.importbackup.MediaStoreWriter.isValidInternalBackup(content) ||
                    !UnifiedCsvStorage.hasRealTransactionRows(content)
                ) {
                    _statusMessage.emit("Internal safety-net backup is empty or corrupt")
                    return@launch
                }
                val stream = java.io.ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))
                val result = UnifiedCsvStorage.restoreFromInputStream(
                    repository = repository,
                    inputStream = stream,
                    clearExisting = true,
                    settingsRepository = settingsRepository,
                    context = context
                )
                _statusMessage.emit(formatRestoreSummary(result, prefix = "Safety net"))
            } catch (e: Exception) {
                _statusMessage.emit("Safety net restore failed: ${e.localizedMessage}")
            }
        }
    }

    fun shareBackupFile(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            // UI greying is courtesy; the service-layer guard is law.
            val settings = settingsRepository.settingsFlow.first()
            if (settings.demoMode) {
                _statusMessage.emit("Unavailable in sample mode")
                return@launch
            }
            try {
                val transactions = repository.allTransactions.first()
                val accounts = repository.allAccounts.first()
                val categories = repository.allCategories.first()
                val customFields = repository.allCustomFields.first()
                val commitments = repository.allCommitments.first()
                val budgets = repository.allBudgets.first()
                val csvContent = CsvBackupManager.buildCsv(transactions, accounts, categories, customFields, settings, commitments, budgets)

                val shareDir = File(context.cacheDir, "share").apply { mkdirs() }
                val shareFile = File(shareDir, "expense_tracker_backup.csv")
                shareFile.writeText(csvContent)

                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    shareFile
                )

                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "SilentSums Backup")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                val chooser = android.content.Intent.createChooser(intent, "Share Backup CSV").apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (e: Exception) {
                _statusMessage.emit("Share failed: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun resetAutoSaveTreeUri(context: Context) {
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            if (settings.demoMode) return@launch
            val currentUri = settings.autoSaveTreeUri
            if (currentUri.isNotBlank()) {
                try {
                    val uri = Uri.parse(currentUri)
                    val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.releasePersistableUriPermission(uri, flags)
                } catch (_: Exception) {}
            }
            settingsRepository.setAutoSaveTreeUri("")
            _statusMessage.emit("Data folder reset to ${com.example.importbackup.MediaStoreWriter.displayPathForRoot(context, null)}")
        }
    }

    suspend fun restoreFromStoredCsv(context: Context, clearExisting: Boolean = false) {
        // UI greying is courtesy; the service-layer guard is law.
        val settings = settingsRepository.settingsFlow.first()
        if (settings.demoMode) {
            _statusMessage.emit("Unavailable in sample mode")
            return
        }
        try {
            val result = UnifiedCsvStorage.restoreFromMobileStorage(context, repository, clearExisting, settingsRepository)
            _statusMessage.emit(formatRestoreSummary(result, prefix = "Phone CSV"))
        } catch (e: Exception) {
            _statusMessage.emit("Fetch failed: ${e.localizedMessage}")
        }
    }

    suspend fun restoreUnifiedCsvBackup(context: Context, clearExisting: Boolean = false) {
        restoreFromStoredCsv(context, clearExisting)
    }

    suspend fun restoreFromAutoSave(context: Context, clearExisting: Boolean = false) {
        // UI greying is courtesy; the service-layer guard is law.
        val settings = settingsRepository.settingsFlow.first()
        if (settings.demoMode) {
            _statusMessage.emit("Unavailable in sample mode")
            return
        }
        try {
            val result = UnifiedCsvStorage.restoreFromAutoSave(context, repository, clearExisting, settingsRepository)
            _statusMessage.emit(formatRestoreSummary(result, prefix = "Auto-save"))
        } catch (e: Exception) {
            _statusMessage.emit("Auto-save restore failed: ${e.localizedMessage}")
        }
    }

    fun runAutoSaveNow(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            // UI greying is courtesy; the service-layer guard is law.
            val settings = settingsRepository.settingsFlow.first()
            if (settings.demoMode) {
                _statusMessage.emit("Unavailable in sample mode")
                return@launch
            }
            try {
                val msg = com.example.worker.AutoBackupWorker.runSynchronously(context, database, repository, settingsRepository)
                _statusMessage.emit(msg)
            } catch (lbe: com.example.importbackup.MediaStoreWriter.LargerBackupExistsException) {
                _statusMessage.emit(lbe.message)
            } catch (e: Exception) {
                _statusMessage.emit("Auto-save failed: ${e.localizedMessage}")
            }
        }
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoBackupEnabled(enabled)
            _statusMessage.emit(if (enabled) "Daily auto-backup enabled" else "Daily auto-backup turned off")
        }
    }

    fun setAutoSaveOnClose(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoSaveOnClose(enabled)
            _statusMessage.emit(if (enabled) "Auto-save on app close enabled" else "Auto-save on app close turned off")
        }
    }

    fun setAutoSaveTreeUri(uriString: String) {
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            if (settings.demoMode) return@launch
            settingsRepository.setAutoSaveTreeUri(uriString)
            val pathDesc = com.example.importbackup.MediaStoreWriter.displayPathForRoot(settingsRepository.context, uriString)
            _statusMessage.emit(if (uriString.isBlank()) "Data folder reset to $pathDesc" else "Data folder set to $pathDesc")
        }
    }

    suspend fun saveNamedSnapshotToUri(context: Context, uri: Uri) {
        // UI greying is courtesy; the service-layer guard is law.
        val settings = settingsRepository.settingsFlow.first()
        if (settings.demoMode) {
            _statusMessage.emit("Unavailable in sample mode")
            return
        }
        try {
            val transactions = repository.allTransactions.first()
            val accounts = repository.allAccounts.first()
            val categories = repository.allCategories.first()
            val customFields = repository.allCustomFields.first()
            val commitments = repository.allCommitments.first()
            val budgets = repository.allBudgets.first()
            val csvContent = CsvBackupManager.buildCsv(transactions, accounts, categories, customFields, settings, commitments, budgets)

            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.bufferedWriter().use { it.write(csvContent) }
            } ?: throw Exception("Could not open destination stream")
            _statusMessage.emit("Named snapshot saved successfully!")
        } catch (e: Exception) {
            _statusMessage.emit("Save failed: ${e.localizedMessage}")
        }
    }

    suspend fun restoreFromUri(context: Context, uri: Uri) {
        // UI greying is courtesy; the service-layer guard is law.
        val settings = settingsRepository.settingsFlow.first()
        if (settings.demoMode) {
            _statusMessage.emit("Unavailable in sample mode")
            return
        }
        try {
            val result = context.contentResolver.openInputStream(uri)?.use { stream ->
                UnifiedCsvStorage.restoreFromInputStream(repository, stream, clearExisting = false, settingsRepository = settingsRepository, context = context)
            } ?: throw Exception("Could not open file stream")
            _statusMessage.emit(formatRestoreSummary(result))
        } catch (e: Exception) {
            _statusMessage.emit("Import failed: ${e.localizedMessage}")
        }
    }

    suspend fun restoreCsvBackup(context: Context, uri: Uri, clearAll: Boolean) {
        // UI greying is courtesy; the service-layer guard is law.
        val settings = settingsRepository.settingsFlow.first()
        if (settings.demoMode) {
            _statusMessage.emit("Unavailable in sample mode")
            return
        }
        try {
            val result = context.contentResolver.openInputStream(uri)?.use { stream ->
                UnifiedCsvStorage.restoreFromInputStream(repository, stream, clearAll, settingsRepository, context = context)
            } ?: throw Exception("Could not open CSV file stream")
            _statusMessage.emit(formatRestoreSummary(result, prefix = "Phone CSV"))
        } catch (e: Exception) {
            _statusMessage.emit("Restore failed: ${e.localizedMessage}")
        }
    }

    private suspend fun generateCsvReport(): String {
        val transactions = repository.allTransactionsChronological.first()
        val accounts = repository.allAccounts.first()
        val categories = repository.allCategories.first()
        val accountMap = accounts.associateBy { it.id }
        val categoryMap = categories.associateBy { it.id }

        val dynamicAccCols = accounts.map { it.name }
        val headerCols = mutableListOf("ID", "Date", "Type", "Amount", "From Account", "To Account")
        headerCols.addAll(dynamicAccCols)
        headerCols.addAll(listOf("Category", "Description", "Notes"))

        val sb = java.lang.StringBuilder()
        sb.append(headerCols.joinToString(",")).append("\n")

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        transactions.forEach { tx ->
            val fromName = tx.fromAccountId?.let { accountMap[it]?.name } ?: ""
            val toName = tx.toAccountId?.let { accountMap[it]?.name } ?: ""
            val catName = tx.categoryId?.let { categoryMap[it]?.name } ?: ""

            val row = mutableListOf<String>()
            row.add(tx.id.toString())
            row.add(sdf.format(Date(tx.date)))
            row.add(tx.type)
            row.add(String.format(Locale.US, "%.2f", tx.amount))
            row.add("\"$fromName\"")
            row.add("\"$toName\"")

            accounts.forEach { acc ->
                val cellVal = when (tx.type.uppercase()) {
                    "EXPENSE" -> if (tx.fromAccountId == acc.id) String.format(Locale.US, "-%.2f", tx.amount) else ""
                    "INCOME" -> if (tx.toAccountId == acc.id) String.format(Locale.US, "+%.2f", tx.amount) else ""
                    "TRANSFER" -> when {
                        tx.fromAccountId == acc.id -> String.format(Locale.US, "-%.2f", tx.amount)
                        tx.toAccountId == acc.id -> String.format(Locale.US, "+%.2f", tx.amount)
                        else -> ""
                    }
                    else -> ""
                }
                row.add(cellVal)
            }

            row.add("\"$catName\"")
            row.add("\"${tx.description.replace("\"", "\"\"")}\"")
            row.add("\"${tx.notes.replace("\"", "\"\"")}\"")

            sb.append(row.joinToString(",")).append("\n")
        }

        return sb.toString()
    }
}