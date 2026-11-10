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
            settingsRepository: AppSettingsRepository
        ) {
            val settings = settingsRepository.settingsFlow.first()
            if (!settings.dueRemindersEnabled) return

            createNotificationChannel(context)

            val now = System.currentTimeMillis()
            val commitments = database.commitmentDao().getActiveCommitmentsList()
            if (commitments.isEmpty()) return

            val accounts = database.accountDao().getAllAccounts().first()
            val transactions = database.transactionDao().getAllTransactions().first()
            val balanceMap = LedgerCalculator.computeAccountBalances(accounts, transactions)
                .associate { it.account.id to it.currentBalance }
            val accountMap = accounts.associateBy { it.id }

            val todayStart = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val tomorrowStart = todayStart + 86_400_000L
            val tomorrowEnd = tomorrowStart + 86_400_000L

            for (c in commitments) {
                if (!c.active || c.settled) continue
                val due = CommitmentCalculator.computeNextDueDate(c, now) ?: continue

                val isOverdue = due < todayStart
                val isDueTomorrow = due in tomorrowStart until tomorrowEnd

                if (!isOverdue && !isDueTomorrow) continue

                val formattedAmount = String.format(Locale.US, "%.0f", c.amount)

                val (title, body) = when (c.type) {
                    "RECURRING" -> {
                        val accName = c.accountId?.let { accountMap[it]?.name } ?: "Account"
                        if (c.direction == "INCOMING") {
                            if (isDueTomorrow) {
                                "Upcoming Income" to "₹$formattedAmount ${c.name} expected in 1 day into $accName"
                            } else {
                                "Income Overdue" to "₹$formattedAmount ${c.name} expected in $accName is overdue"
                            }
                        } else {
                            val balance = c.accountId?.let { balanceMap[it] } ?: 0.0
                            val shortFunds = if (balance < c.amount) {
                                val diff = String.format(Locale.US, "%.0f", c.amount - balance)
                                " (Short by ₹$diff)"
                            } else ""

                            if (isDueTomorrow) {
                                "Upcoming Payment" to "₹$formattedAmount ${c.name} due tomorrow from $accName$shortFunds"
                            } else {
                                "Payment Overdue" to "₹$formattedAmount ${c.name} is overdue"
                            }
                        }
                    }
                    else -> { // IOU
                        val directionText = if (c.direction == "OUTGOING") {
                            "you owe ₹$formattedAmount to ${c.name}"
                        } else {
                            "${c.name} owes you ₹$formattedAmount"
                        }

                        if (isDueTomorrow) {
                            "Upcoming IOU" to "$directionText due tomorrow"
                        } else {
                            "IOU Overdue" to "$directionText (Overdue)"
                        }
                    }
                }

                showNotification(context, c.id.toInt(), title, body)
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
        ) {
            try {
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

                val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)

                val manager = NotificationManagerCompat.from(context)
                manager.notify(notificationId, builder.build())
            } catch (_: SecurityException) {
                // Permission not granted; handled gracefully
            } catch (_: Exception) {}
        }
    }
}
