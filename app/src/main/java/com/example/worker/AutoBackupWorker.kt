package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.AppDatabase
import com.example.data.datastore.AppSettingsRepository
import com.example.data.repository.FinanceRepository
import com.example.data.repository.MutationTracker
import com.example.importbackup.CsvBackupManager
import com.example.importbackup.MediaStoreWriter
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class AutoBackupWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val settingsRepo = AppSettingsRepository(context)
        val settings = settingsRepo.settingsFlow.first()

        // UI greying is courtesy; the service-layer guard is law.
        // 1. Re-read autoBackupEnabled and exit cleanly if off, or in demoMode (4.4 FIREWALL)
        // Rationale: the gate (offer only at 0 real txn) protects the DATABASE;
        // the firewall protects the BACKUP FILES. Belt and suspenders.
        if (!settings.autoBackupEnabled || settings.demoMode) {
            return Result.success()
        }

        // 2. Overwrite-on-change only: compare mutation stamp to LAST SUCCESSFUL save stamp
        val currentStamp = MutationTracker.currentStamp
        if (settings.lastSuccessfulAutoSaveTimestamp > 0L && currentStamp <= settings.lastDataMutationTimestamp) {
            // Nothing changed since the last successful auto-save; skip write
            return Result.success()
        }

        return try {
            val database = AppDatabase.getDatabase(context)
            val repository = FinanceRepository(database)

            val transactions = repository.allTransactions.first()
            if (transactions.isEmpty()) {
                // Do not auto-backup an empty ledger; protects existing backups on phone
                return Result.success()
            }
            val accounts = repository.allAccounts.first()
            val categories = repository.allCategories.first()
            val commitments = repository.allCommitments.first()
            val budgets = repository.allBudgets.first()
            val customFields = repository.allCustomFields.first()

            val csvContent = CsvBackupManager.buildCsv(
                transactions = transactions,
                accounts = accounts,
                categories = categories,
                customFields = customFields,
                settings = settings,
                commitments = commitments,
                budgets = budgets
            )

            // Write exclusively to shared storage via unified MediaStoreWriter
            val (savedUri, usedSaf) = MediaStoreWriter.writeRollingAutoSave(
                context = context,
                content = csvContent,
                customTreeUriString = settings.autoSaveTreeUri.ifBlank { null }
            )

            if (savedUri != null) {
                val now = System.currentTimeMillis()
                val rootPath = MediaStoreWriter.displayPathForRoot(
                    context = context,
                    treeUriString = if (usedSaf) settings.autoSaveTreeUri else null
                )
                val targetPath = "$rootPath/${MediaStoreWriter.AUTO_SUBFOLDER}/${MediaStoreWriter.AUTO_BACKUP_FILE}"
                settingsRepo.recordAutoSaveResult(
                    success = true,
                    statusText = "Saved to $targetPath",
                    timestamp = now
                )
                settingsRepo.recordDataMutation(currentStamp)

                // First-time non-blocking disclosure
                if (!settings.hasShownAutoBackupNotification) {
                    showFirstTimeDisclosure(context, targetPath)
                    settingsRepo.setHasShownAutoBackupNotification(true)
                }

                Result.success()
            } else {
                val now = System.currentTimeMillis()
                settingsRepo.recordAutoSaveResult(
                    success = false,
                    statusText = "Auto-save failing",
                    timestamp = now
                )
                Result.retry()
            }
        } catch (e: com.example.importbackup.OrphanCollisionException) {
            val now = System.currentTimeMillis()
            settingsRepo.recordAutoSaveResult(
                success = false,
                statusText = e.message ?: "Orphan collision",
                timestamp = now
            )
            // Skip cycle without spawning siblings
            Result.success()
        } catch (e: Exception) {
            val now = System.currentTimeMillis()
            settingsRepo.recordAutoSaveResult(
                success = false,
                statusText = "Auto-save failing: ${e.localizedMessage ?: "error"}",
                timestamp = now
            )
            Result.retry()
        }
    }

    private fun showFirstTimeDisclosure(context: Context, path: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "auto_backup_disclosure_channel"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Daily Auto-Backup",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Notifications for automatic background backups"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Daily Auto-Backup Active")
                .setContentText("A rolling backup was saved to $path. You can manage or disable this in Settings.")
                .setStyle(NotificationCompat.BigTextStyle().bigText("A rolling backup was saved to $path. It survives app re-installation. You can configure or turn it off anytime in Settings."))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(78912, notification)
        } catch (_: Exception) {}
    }

    companion object {
        const val WORK_NAME = "auto_backup_daily_work"

        suspend fun runSynchronously(
            context: Context,
            database: AppDatabase,
            repository: FinanceRepository,
            settingsRepo: AppSettingsRepository
        ): String {
            val settings = settingsRepo.settingsFlow.first()
            // UI greying is courtesy; the service-layer guard is law.
            if (settings.demoMode) {
                return "Auto-save paused in demo mode"
            }
            val transactions = repository.allTransactions.first()
            if (transactions.isEmpty()) {
                return "Auto-save skipped: ledger is empty (0 records)"
            }
            val accounts = repository.allAccounts.first()
            val categories = repository.allCategories.first()
            val commitments = repository.allCommitments.first()
            val budgets = repository.allBudgets.first()
            val customFields = repository.allCustomFields.first()

            val csvContent = CsvBackupManager.buildCsv(
                transactions = transactions,
                accounts = accounts,
                categories = categories,
                customFields = customFields,
                settings = settings,
                commitments = commitments,
                budgets = budgets
            )

            val (savedUri, usedSaf) = MediaStoreWriter.writeRollingAutoSave(
                context = context,
                content = csvContent,
                customTreeUriString = settings.autoSaveTreeUri.ifBlank { null }
            )

            if (savedUri != null) {
                val now = System.currentTimeMillis()
                val rootPath = MediaStoreWriter.displayPathForRoot(
                    context = context,
                    treeUriString = if (usedSaf) settings.autoSaveTreeUri else null
                )
                val targetPath = "$rootPath/${MediaStoreWriter.AUTO_SUBFOLDER}/${MediaStoreWriter.AUTO_BACKUP_FILE}"
                settingsRepo.recordAutoSaveResult(
                    success = true,
                    statusText = "Saved to $targetPath",
                    timestamp = now
                )
                settingsRepo.recordDataMutation(MutationTracker.currentStamp)
                return "Auto-save executed: Saved to $targetPath (${transactions.size} records)"
            } else {
                throw Exception("Could not write auto-save file")
            }
        }

        fun enqueueOnClose(context: Context) {
            try {
                val request = androidx.work.OneTimeWorkRequestBuilder<AutoBackupWorker>()
                    .build()
                WorkManager.getInstance(context).enqueue(request)
            } catch (_: Exception) {}
        }

        fun schedule(context: Context) {
            try {
                val constraints = androidx.work.Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
                val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
                    .setConstraints(constraints)
                    .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            } catch (_: Exception) {}
        }
    }
}
