package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.datastore.AppSettingsRepository
import com.example.domain.calculator.LedgerCalculator
import com.example.domain.commitment.CommitmentCalculator
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class DueReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(context)
        val settingsRepo = AppSettingsRepository(context)
        checkAndNotify(context, database, settingsRepo)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "due_reminders_periodic_work"
        const val CHANNEL_ID = "due_reminders_channel"
        const val EXTRA_OPEN_DUE_POPUP = "EXTRA_OPEN_DUE_POPUP"

        fun schedule(context: Context) {
            try {
                createNotificationChannel(context)
                val constraints = androidx.work.Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
                val request = PeriodicWorkRequestBuilder<DueReminderWorker>(1, TimeUnit.DAYS)
                    .setConstraints(constraints)
                    .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            } catch (_: Exception) {}
        }

        suspend fun checkAndNotify(
            context: Context,
            database: AppDatabase,
            settingsRepository: AppSettingsRepository,
            now: Long = System.currentTimeMillis()
        ) {
            val settings = settingsRepository.settingsFlow.first()
            if (!settings.dueRemindersEnabled) return

            createNotificationChannel(context)
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

            val todayStart = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val tomorrowStart = todayStart + 86_400_000L
            val tomorrowEnd = tomorrowStart + 86_400_000L
            val cutoffStart = todayStart - 7L * 86_400_000L

            val dayFormat = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val todayKey = dayFormat.format(java.util.Date(todayStart))
            val activeMarkers = settings.reminderLastNotified.filterTo(mutableSetOf()) { entry ->
                val parts = entry.split(":")
                if (parts.size < 3) {
                    false
                } else {
                    val entryDateMs = try {
                        dayFormat.parse(parts[1])?.time
                    } catch (_: Exception) {
                        null
                    }
                    entryDateMs != null && entryDateMs >= cutoffStart
                }
            }
            var markersChanged = activeMarkers != settings.reminderLastNotified

            val commitments = database.commitmentDao().getActiveCommitmentsList()
            if (commitments.isEmpty()) {
                if (markersChanged) {
                    settingsRepository.setReminderLastNotified(activeMarkers)
                }
                return
            }

            val accounts = database.accountDao().getAllAccounts().first()
            val transactions = database.transactionDao().getAllTransactions().first()
            val balanceMap = LedgerCalculator.computeAccountBalances(accounts, transactions)
                .associate { it.account.id to it.currentBalance }
            val accountMap = accounts.associateBy { it.id }

            for (c in commitments) {
                if (!c.active || c.settled) continue
                val due = CommitmentCalculator.computeNextDueDate(c, now) ?: continue

                val isOverdue = due < todayStart
                val isDueToday = due in todayStart until tomorrowStart
                val isDueTomorrow = due in tomorrowStart until tomorrowEnd

                if (!isOverdue && !isDueToday && !isDueTomorrow) continue

                val kind = when {
                    isDueToday -> "due_today"
                    isDueTomorrow -> "due_tomorrow"
                    else -> "overdue"
                }
                val marker = "${c.id}:$todayKey:$kind"
                if (marker in activeMarkers) continue

                val hideBalances = settings.hideBalances
                val amountDisplay = if (hideBalances) {
                    "₹••••"
                } else {
                    "₹${String.format(Locale.US, "%.0f", c.amount)}"
                }

                val (title, body) = when (c.type) {
                    "RECURRING" -> {
                        val accName = c.accountId?.let { accountMap[it]?.name } ?: "Account"
                        if (c.direction == "INCOMING") {
                            when {
                                isDueToday -> "Upcoming Income" to "$amountDisplay ${c.name} due today into $accName"
                                isDueTomorrow -> "Upcoming Income" to "$amountDisplay ${c.name} expected in 1 day into $accName"
                                else -> "Income Overdue" to "$amountDisplay ${c.name} expected in $accName is overdue"
                            }
                        } else {
                            val balance = c.accountId?.let { balanceMap[it] } ?: 0.0
                            val shortFunds = if (balance < c.amount) {
                                val shortDisplay = if (hideBalances) {
                                    "₹••••"
                                } else {
                                    "₹${String.format(Locale.US, "%.0f", c.amount - balance)}"
                                }
                                " (Short by $shortDisplay)"
                            } else ""

                            when {
                                isDueToday -> "Upcoming Payment" to "$amountDisplay ${c.name} due today from $accName$shortFunds"
                                isDueTomorrow -> "Upcoming Payment" to "$amountDisplay ${c.name} due tomorrow from $accName$shortFunds"
                                else -> "Payment Overdue" to "$amountDisplay ${c.name} is overdue"
                            }
                        }
                    }
                    else -> { // IOU
                        val directionText = if (c.direction == "OUTGOING") {
                            "you owe $amountDisplay to ${c.name}"
                        } else {
                            "${c.name} owes you $amountDisplay"
                        }

                        when {
                            isDueToday -> "Upcoming IOU" to "$directionText due today"
                            isDueTomorrow -> "Upcoming IOU" to "$directionText due tomorrow"
                            else -> "IOU Overdue" to "$directionText (Overdue)"
                        }
                    }
                }

                if (showNotification(context, c.id.toInt(), title, body)) {
                    activeMarkers.add(marker)
                    markersChanged = true
                }
            }

            if (markersChanged) {
                settingsRepository.setReminderLastNotified(activeMarkers)
            }
        }

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "Due reminders"
                val descriptionText = "Notifications for upcoming and overdue bills and IOUs"
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                    enableVibration(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                }
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        private fun showNotification(
            context: Context,
            notificationId: Int,
            title: String,
            message: String
        ): Boolean {
            return try {
                createNotificationChannel(context)
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_OPEN_DUE_POPUP, true)
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    notificationId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val publicNotification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("SilentSums")
                    .setContentText("You have a payment or IOU due.")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()

                val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(publicNotification)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)

                val manager = NotificationManagerCompat.from(context)
                manager.notify(notificationId, builder.build())
                true
            } catch (_: SecurityException) {
                // Permission not granted; handled gracefully
                false
            } catch (_: Exception) {
                false
            }
        }
    }
}
