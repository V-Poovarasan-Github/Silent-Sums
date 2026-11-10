package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.data.datastore.AppSettingsRepository
import com.example.data.repository.FinanceRepository
import com.example.ui.PaisaTrackApp
import com.example.worker.DueReminderWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {

    private lateinit var database: AppDatabase
    private lateinit var repository: FinanceRepository
    private lateinit var settingsRepository: AppSettingsRepository
    private val openDuePopupState = mutableStateOf(false)
    private val shortcutActionState = mutableStateOf<String?>(null)

    // Memory-only unlocked state: cold start always begins locked (false)
    private val isUnlockedState = mutableStateOf(false)
    private var leftForegroundAt = 0L
    private var inBackground = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (consumePendingRecreationUnlock()) {
            isUnlockedState.value = true
        }
        enableEdgeToEdge()

        val bundle = com.example.data.DatabaseProvider.getBundle(applicationContext)
        database = bundle.database
        repository = FinanceRepository(database)
        settingsRepository = AppSettingsRepository(applicationContext)

        // Lazily close any abandoned databases from prior mode flips after assigning new instance
        try {
            com.example.data.DatabaseProvider.closeAbandonedDatabases()
        } catch (e: Throwable) {
            Log.w("MainActivity", "Failed to close abandoned databases", e)
        }

        // Reconcile DataStore := marker in BOTH directions on cold start
        lifecycleScope.launch(Dispatchers.IO) {
            val markerExists = com.example.data.DatabaseProvider.isDemoMode(applicationContext)
            val settings = settingsRepository.settingsFlow.first()
            if (markerExists && !settings.demoMode) {
                settingsRepository.setDemoMode(true)
            } else if (!markerExists && settings.demoMode) {
                settingsRepository.setDemoMode(false)
            }
            if (!markerExists) {
                com.example.data.DatabaseProvider.purgeDemoLeftovers(applicationContext)
            }
        }

        // 1.4 Recents and screenshot privacy: set FLAG_SECURE while app lock or hideBalances is on
        lifecycleScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                updateWindowSecureFlag(window, settings.lockEnabled, settings.hideBalances)
            }
        }

        // Ensure notification channel is created early before any reminder or worker runs
        DueReminderWorker.createNotificationChannel(applicationContext)

        // Schedule periodic reminders and check on launch
        DueReminderWorker.schedule(applicationContext)
        com.example.worker.AutoBackupWorker.schedule(applicationContext)
        lifecycleScope.launch(Dispatchers.IO) {
            DueReminderWorker.checkAndNotify(applicationContext, database, settingsRepository)
        }

        if (intent?.getBooleanExtra(DueReminderWorker.EXTRA_OPEN_DUE_POPUP, false) == true) {
            openDuePopupState.value = true
        }
        if (intent?.getStringExtra("shortcut_action") != null) {
            shortcutActionState.value = intent?.getStringExtra("shortcut_action")
        }

        setContent {
            val openDuePopup by openDuePopupState
            val shortcutAction by shortcutActionState
            val isUnlocked by isUnlockedState
            PaisaTrackApp(
                activity = this,
                database = database,
                repository = repository,
                settingsRepository = settingsRepository,
                initialOpenDuePopup = openDuePopup,
                initialShortcutAction = shortcutAction,
                isUnlocked = isUnlocked,
                onUnlocked = {
                    isUnlockedState.value = true
                }
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
    }

    companion object {
        @Volatile var pendingRecreationUnlock: Boolean = false

        fun updateWindowSecureFlag(
            window: android.view.Window?,
            lockEnabled: Boolean,
            hideBalances: Boolean
        ) {
            if (window == null) return
            if (lockEnabled || hideBalances) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
        }

        fun markSessionUnlocked(context: android.content.Context) {
            pendingRecreationUnlock = true
            (com.example.data.DatabaseProvider.findActivity(context) as? MainActivity)?.let { act ->
                act.isUnlockedState.value = true
            }
        }

        fun consumePendingRecreationUnlock(): Boolean {
            val was = pendingRecreationUnlock
            pendingRecreationUnlock = false
            return was
        }
    }

    override fun onStop() {
        super.onStop()
        leftForegroundAt = SystemClock.elapsedRealtime()
        inBackground = true

        // Auto-save on close if enabled and there are unsaved mutations
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val settings = settingsRepository.settingsFlow.first()
                // UI greying is courtesy; the service-layer guard is law.
                // 4.4 FIREWALL: while KEY_DEMO_MODE == TRUE, suppress auto-save on close so fake numbers never reach backups.
                // Rationale: the gate (offer only at 0 real txn) protects the DATABASE; the firewall protects the BACKUP FILES.
                if (settings.autoSaveOnClose && settings.autoBackupEnabled && !settings.demoMode) {
                    val currentStamp = com.example.data.repository.MutationTracker.currentStamp
                    if (currentStamp > settings.lastDataMutationTimestamp) {
                        try {
                            com.example.worker.AutoBackupWorker.runSynchronously(
                                applicationContext,
                                database,
                                repository,
                                settingsRepository
                            )
                        } catch (_: Exception) {
                            com.example.worker.AutoBackupWorker.enqueueOnClose(applicationContext)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    override fun onStart() {
        super.onStart()
        if (inBackground) {
            val away = SystemClock.elapsedRealtime() - leftForegroundAt
            lifecycleScope.launch(Dispatchers.IO) {
                val settings = settingsRepository.settingsFlow.first()
                if (settings.lockEnabled) {
                    val timeoutMs = settings.lockTimeoutMs
                    if (away >= timeoutMs) {
                        withContext(Dispatchers.Main) {
                            isUnlockedState.value = false
                        }
                    }
                }
            }
            inBackground = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(DueReminderWorker.EXTRA_OPEN_DUE_POPUP, false)) {
            openDuePopupState.value = true
        }
        if (intent.hasExtra("shortcut_action")) {
            shortcutActionState.value = intent.getStringExtra("shortcut_action")
        }
    }
}
