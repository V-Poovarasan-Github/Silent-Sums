package com.example.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "silentsums_settings")

data class AppSettings(
    val currencySymbol: String = "₹",
    val monthStartDay: Int = 1,
    val theme: String = "AUTO", // AUTO, LIGHT, DARK
    val autoThemeSource: String = "DEVICE", // DEVICE, DAYLIGHT
    val lockEnabled: Boolean = false,
    val pinHash: String = "",
    val hideBalances: Boolean = false,
    val soundHapticsEnabled: Boolean = true,
    val dueRemindersEnabled: Boolean = true,
    val autoBackupEnabled: Boolean = true,
    val lastSuccessfulAutoSaveTimestamp: Long = 0L,
    val lastAutoSaveStatusText: String = "",
    val lastAutoSaveSuccess: Boolean = true,
    val autoSaveFailingSince: Long = 0L,
    val lastDataMutationTimestamp: Long = 0L,
    val autoSaveTreeUri: String = "",
    val hasShownAutoBackupDisclosure: Boolean = false,
    val hasShownAutoBackupDialog: Boolean = false,
    val hasShownAutoBackupNotification: Boolean = false,
    val hasAskedCommitmentNotificationPermission: Boolean = false,
    val budgetsCardVisible: Boolean = true,
    val quickLogChipsEnabled: Boolean = true,
    val lakhFormatEnabled: Boolean = false,
    val autoSaveOnClose: Boolean = true,
    val lockTimeoutMs: Long = 60_000L, // Default 1 minute grace period
    val onboardingCompleted: Boolean = false,
    val helpVisible: Boolean = true,
    val demoMode: Boolean = false,
    val mediaMirrorEnabled: Boolean = false,
    val mediaMirrorTreeUri: String = "",
    val pinFailedAttempts: Int = 0,
    val pinLockoutStartElapsedMs: Long = 0L,
    val pinLockoutEndElapsedMs: Long = 0L,
    val pinLockoutStartWallMs: Long = 0L,
    val pinLockoutEndWallMs: Long = 0L,
    val reminderLastNotified: Set<String> = emptySet()
)

class AppSettingsRepository(val context: Context) {
    companion object {
        const val AUTO_THEME_SOURCE_DEVICE = "DEVICE"
        const val AUTO_THEME_SOURCE_DAYLIGHT = "DAYLIGHT"

        private val _currencySymbolFlow = MutableStateFlow("₹")
        val sharedCurrencySymbolFlow: MutableStateFlow<String> = _currencySymbolFlow

        val KEY_CURRENCY = stringPreferencesKey("currency_symbol")
        val KEY_MONTH_START_DAY = intPreferencesKey("month_start_day")
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_AUTO_THEME_SOURCE = stringPreferencesKey("auto_theme_source")
        val KEY_LOCK_ENABLED = booleanPreferencesKey("lock_enabled")
        val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
        val KEY_LOCK_TIMEOUT_MS = longPreferencesKey("lock_timeout_ms")
        val KEY_HIDE_BALANCES = booleanPreferencesKey("hide_balances")
        val KEY_HAPTICS = booleanPreferencesKey("sound_haptics")
        val KEY_DUE_REMINDERS = booleanPreferencesKey("due_reminders_enabled")
        val KEY_AUTO_BACKUP = booleanPreferencesKey("auto_backup_enabled")
        val KEY_AUTO_SAVE_ON_CLOSE = booleanPreferencesKey("auto_save_on_close")
        val KEY_LAST_SUCCESSFUL_AUTO_SAVE = longPreferencesKey("last_successful_auto_save")
        val KEY_LAST_AUTO_SAVE_STATUS = stringPreferencesKey("last_auto_save_status")
        val KEY_LAST_AUTO_SAVE_SUCCESS = booleanPreferencesKey("last_auto_save_success")
        val KEY_AUTO_SAVE_FAILING_SINCE = longPreferencesKey("auto_save_failing_since")
        val KEY_LAST_DATA_MUTATION = longPreferencesKey("last_data_mutation")
        val KEY_AUTO_SAVE_TREE_URI = stringPreferencesKey("auto_save_tree_uri")
        val KEY_DISCLOSURE_SHOWN = booleanPreferencesKey("auto_backup_disclosure_shown")
        val KEY_AUTO_BACKUP_DIALOG_SHOWN = booleanPreferencesKey("auto_backup_dialog_shown")
        val KEY_AUTO_BACKUP_NOTIFICATION_SHOWN = booleanPreferencesKey("auto_backup_notification_shown")
        val KEY_ASKED_COMMITMENT_NOTIF_PERMISSION = booleanPreferencesKey("asked_commitment_notif_permission")
        val KEY_BUDGETS_CARD_VISIBLE = booleanPreferencesKey("budgets_card_visible")
        val KEY_QUICK_LOG_CHIPS = booleanPreferencesKey("quick_log_chips_enabled")
        val KEY_LAKH_FORMAT = booleanPreferencesKey("lakh_format_enabled")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_HELP_VISIBLE = booleanPreferencesKey("help_visible")
        val KEY_DEMO_MODE = booleanPreferencesKey("demo_mode")
        val KEY_MEDIA_MIRROR_ENABLED = booleanPreferencesKey("media_mirror_enabled")
        val KEY_MEDIA_MIRROR_TREE_URI = stringPreferencesKey("media_mirror_tree_uri")
        val KEY_PIN_FAILED_ATTEMPTS = intPreferencesKey("pin_failed_attempts")
        val KEY_PIN_LOCKOUT_START_ELAPSED_MS = longPreferencesKey("pin_lockout_start_elapsed_ms")
        val KEY_PIN_LOCKOUT_END_ELAPSED_MS = longPreferencesKey("pin_lockout_end_elapsed_ms")
        val KEY_PIN_LOCKOUT_START_WALL_MS = longPreferencesKey("pin_lockout_start_wall_ms")
        val KEY_PIN_LOCKOUT_END_WALL_MS = longPreferencesKey("pin_lockout_end_wall_ms")
        val KEY_REMINDER_LAST_NOTIFIED = stringSetPreferencesKey("reminder_last_notified")

        @Volatile
        private var sharedCachedSettings: AppSettings? = null
    }

    val currencySymbolFlow: StateFlow<String>
        get() = _currencySymbolFlow

    val currencySymbol: MutableStateFlow<String>
        get() = _currencySymbolFlow

    var cachedSettings: AppSettings?
        get() = sharedCachedSettings
        private set(value) {
            sharedCachedSettings = value
        }

    fun clearLoadedCacheForTesting() {
        sharedCachedSettings = null
    }

    private fun mapPreferencesToSettings(preferences: Preferences): AppSettings {
        val currency = preferences[KEY_CURRENCY] ?: "₹"
        _currencySymbolFlow.value = currency
        com.example.domain.util.Formatters.updateCurrentCurrencySymbol(currency)
        val rawAutoThemeSource = preferences[KEY_AUTO_THEME_SOURCE] ?: AUTO_THEME_SOURCE_DEVICE
        val normalizedAutoThemeSource = if (rawAutoThemeSource == AUTO_THEME_SOURCE_DAYLIGHT) {
            AUTO_THEME_SOURCE_DAYLIGHT
        } else {
            AUTO_THEME_SOURCE_DEVICE
        }
        return AppSettings(
            currencySymbol = currency,
            monthStartDay = preferences[KEY_MONTH_START_DAY] ?: 1,
            theme = preferences[KEY_THEME] ?: "AUTO",
            autoThemeSource = normalizedAutoThemeSource,
            lockEnabled = preferences[KEY_LOCK_ENABLED] ?: false,
            pinHash = preferences[KEY_PIN_HASH] ?: "",
            hideBalances = preferences[KEY_HIDE_BALANCES] ?: false,
            soundHapticsEnabled = preferences[KEY_HAPTICS] ?: true,
            dueRemindersEnabled = preferences[KEY_DUE_REMINDERS] ?: true,
            autoBackupEnabled = preferences[KEY_AUTO_BACKUP] ?: true,
            lastSuccessfulAutoSaveTimestamp = preferences[KEY_LAST_SUCCESSFUL_AUTO_SAVE] ?: 0L,
            lastAutoSaveStatusText = preferences[KEY_LAST_AUTO_SAVE_STATUS] ?: "",
            lastAutoSaveSuccess = preferences[KEY_LAST_AUTO_SAVE_SUCCESS] ?: true,
            autoSaveFailingSince = preferences[KEY_AUTO_SAVE_FAILING_SINCE] ?: 0L,
            lastDataMutationTimestamp = preferences[KEY_LAST_DATA_MUTATION] ?: 0L,
            autoSaveTreeUri = preferences[KEY_AUTO_SAVE_TREE_URI] ?: "",
            hasShownAutoBackupDisclosure = preferences[KEY_DISCLOSURE_SHOWN] ?: false,
            hasShownAutoBackupDialog = preferences[KEY_AUTO_BACKUP_DIALOG_SHOWN] ?: false,
            hasShownAutoBackupNotification = preferences[KEY_AUTO_BACKUP_NOTIFICATION_SHOWN] ?: false,
            hasAskedCommitmentNotificationPermission = preferences[KEY_ASKED_COMMITMENT_NOTIF_PERMISSION] ?: false,
            budgetsCardVisible = preferences[KEY_BUDGETS_CARD_VISIBLE] ?: true,
            quickLogChipsEnabled = preferences[KEY_QUICK_LOG_CHIPS] ?: true,
            lakhFormatEnabled = preferences[KEY_LAKH_FORMAT] ?: false,
            autoSaveOnClose = preferences[KEY_AUTO_SAVE_ON_CLOSE] ?: true,
            lockTimeoutMs = preferences[KEY_LOCK_TIMEOUT_MS] ?: 60_000L,
            onboardingCompleted = preferences[KEY_ONBOARDING_COMPLETED] ?: false,
            helpVisible = preferences[KEY_HELP_VISIBLE] ?: true,
            demoMode = preferences[KEY_DEMO_MODE] ?: false,
            mediaMirrorEnabled = preferences[KEY_MEDIA_MIRROR_ENABLED] ?: false,
            mediaMirrorTreeUri = preferences[KEY_MEDIA_MIRROR_TREE_URI] ?: "",
            pinFailedAttempts = preferences[KEY_PIN_FAILED_ATTEMPTS] ?: 0,
            pinLockoutStartElapsedMs = preferences[KEY_PIN_LOCKOUT_START_ELAPSED_MS] ?: 0L,
            pinLockoutEndElapsedMs = preferences[KEY_PIN_LOCKOUT_END_ELAPSED_MS] ?: 0L,
            pinLockoutStartWallMs = preferences[KEY_PIN_LOCKOUT_START_WALL_MS] ?: 0L,
            pinLockoutEndWallMs = preferences[KEY_PIN_LOCKOUT_END_WALL_MS] ?: 0L,
            reminderLastNotified = preferences[KEY_REMINDER_LAST_NOTIFIED] ?: emptySet()
        ).also { cachedSettings = it }
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        mapPreferencesToSettings(preferences)
    }

    suspend fun setCurrency(symbol: String) {
        _currencySymbolFlow.value = symbol
        com.example.domain.util.Formatters.updateCurrentCurrencySymbol(symbol)
        context.dataStore.edit {
            it[KEY_CURRENCY] = symbol
            mapPreferencesToSettings(it)
        }
    }

    suspend fun setMonthStartDay(day: Int) {
        context.dataStore.edit {
            it[KEY_MONTH_START_DAY] = day.coerceIn(1, 28)
            mapPreferencesToSettings(it)
        }
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit {
            it[KEY_THEME] = theme
            mapPreferencesToSettings(it)
        }
    }

    suspend fun setAutoThemeSource(source: String) {
        val normalized = if (source.uppercase() == AUTO_THEME_SOURCE_DAYLIGHT) {
            AUTO_THEME_SOURCE_DAYLIGHT
        } else {
            AUTO_THEME_SOURCE_DEVICE
        }
        context.dataStore.edit {
            it[KEY_AUTO_THEME_SOURCE] = normalized
            mapPreferencesToSettings(it)
        }
    }

    suspend fun setLockEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[KEY_LOCK_ENABLED] = enabled
            mapPreferencesToSettings(it)
        }
    }

    suspend fun setLockTimeoutMs(timeoutMs: Long) {
        context.dataStore.edit {
            it[KEY_LOCK_TIMEOUT_MS] = timeoutMs
            mapPreferencesToSettings(it)
        }
    }

    suspend fun setPinHash(hash: String) {
        context.dataStore.edit {
            it[KEY_PIN_HASH] = hash
            mapPreferencesToSettings(it)
        }
    }

    suspend fun hashAndSavePin(rawPin: String): String {
        val hashed = com.example.security.PinHasher.hashOffMain(rawPin)
        context.dataStore.edit {
            it[KEY_PIN_HASH] = hashed
            it[KEY_PIN_FAILED_ATTEMPTS] = 0
            it[KEY_PIN_LOCKOUT_START_ELAPSED_MS] = 0L
            it[KEY_PIN_LOCKOUT_END_ELAPSED_MS] = 0L
            it[KEY_PIN_LOCKOUT_START_WALL_MS] = 0L
            it[KEY_PIN_LOCKOUT_END_WALL_MS] = 0L
            mapPreferencesToSettings(it)
        }
        return hashed
    }

    suspend fun verifyAndMigratePin(enteredPin: String, storedOverride: String? = null): Boolean {
        val storedToVerify = if (!storedOverride.isNullOrEmpty()) {
            storedOverride
        } else {
            settingsFlow.first().pinHash
        }
        val matched = com.example.security.PinHasher.verifyOffMain(enteredPin, storedToVerify)
        if (matched) {
            val migratedHash = if (!com.example.security.PinHasher.isHashed(storedToVerify)) {
                com.example.security.PinHasher.hashOffMain(enteredPin)
            } else {
                null
            }
            context.dataStore.edit { prefs ->
                if (migratedHash != null) {
                    prefs[KEY_PIN_HASH] = migratedHash
                }
                prefs[KEY_PIN_FAILED_ATTEMPTS] = 0
                prefs[KEY_PIN_LOCKOUT_START_ELAPSED_MS] = 0L
                prefs[KEY_PIN_LOCKOUT_END_ELAPSED_MS] = 0L
                prefs[KEY_PIN_LOCKOUT_START_WALL_MS] = 0L
                prefs[KEY_PIN_LOCKOUT_END_WALL_MS] = 0L
                mapPreferencesToSettings(prefs)
            }
        }
        return matched
    }

    suspend fun recordPinFailure(
        currentElapsedMs: Long = android.os.SystemClock.elapsedRealtime(),
        currentWallMs: Long = System.currentTimeMillis()
    ): Int {
        var newFailures = 0
        context.dataStore.edit { prefs ->
            newFailures = (prefs[KEY_PIN_FAILED_ATTEMPTS] ?: 0) + 1
            prefs[KEY_PIN_FAILED_ATTEMPTS] = newFailures
            val delayMs = com.example.security.PinHasher.lockoutDurationMsForFailures(newFailures)
            if (delayMs > 0L) {
                prefs[KEY_PIN_LOCKOUT_START_ELAPSED_MS] = currentElapsedMs
                prefs[KEY_PIN_LOCKOUT_END_ELAPSED_MS] = currentElapsedMs + delayMs
                prefs[KEY_PIN_LOCKOUT_START_WALL_MS] = currentWallMs
                prefs[KEY_PIN_LOCKOUT_END_WALL_MS] = currentWallMs + delayMs
            }
            mapPreferencesToSettings(prefs)
        }
        return newFailures
    }

    suspend fun resetPinLockout() {
        context.dataStore.edit { prefs ->
            prefs[KEY_PIN_FAILED_ATTEMPTS] = 0
            prefs[KEY_PIN_LOCKOUT_START_ELAPSED_MS] = 0L
            prefs[KEY_PIN_LOCKOUT_END_ELAPSED_MS] = 0L
            prefs[KEY_PIN_LOCKOUT_START_WALL_MS] = 0L
            prefs[KEY_PIN_LOCKOUT_END_WALL_MS] = 0L
            mapPreferencesToSettings(prefs)
        }
    }

    suspend fun setHideBalances(hide: Boolean) {
        context.dataStore.edit {
            it[KEY_HIDE_BALANCES] = hide
            mapPreferencesToSettings(it)
        }
    }

    suspend fun setSoundHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HAPTICS] = enabled }
    }

    suspend fun setDueRemindersEnabled(enabled: Boolean) {
        cachedSettings = cachedSettings?.copy(dueRemindersEnabled = enabled)
        context.dataStore.edit {
            it[KEY_DUE_REMINDERS] = enabled
            mapPreferencesToSettings(it)
        }
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_BACKUP] = enabled }
    }

    suspend fun setBudgetsCardVisible(visible: Boolean) {
        context.dataStore.edit { it[KEY_BUDGETS_CARD_VISIBLE] = visible }
    }

    suspend fun setQuickLogChipsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_QUICK_LOG_CHIPS] = enabled }
    }

    suspend fun setLakhFormatEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LAKH_FORMAT] = enabled }
    }

    suspend fun setHelpVisible(visible: Boolean) {
        context.dataStore.edit { it[KEY_HELP_VISIBLE] = visible }
    }

    suspend fun setDemoMode(demo: Boolean) {
        context.dataStore.edit { it[KEY_DEMO_MODE] = demo }
    }

    suspend fun recordAutoSaveResult(success: Boolean, statusText: String, timestamp: Long = System.currentTimeMillis()) {
        context.dataStore.edit {
            if (success) {
                it[KEY_LAST_SUCCESSFUL_AUTO_SAVE] = timestamp
                it[KEY_AUTO_SAVE_FAILING_SINCE] = 0L
            } else {
                val currentFailing = it[KEY_AUTO_SAVE_FAILING_SINCE] ?: 0L
                if (currentFailing == 0L) {
                    it[KEY_AUTO_SAVE_FAILING_SINCE] = timestamp
                }
            }
            it[KEY_LAST_AUTO_SAVE_STATUS] = statusText
            it[KEY_LAST_AUTO_SAVE_SUCCESS] = success
        }
    }

    suspend fun recordDataMutation(timestamp: Long = System.currentTimeMillis()) {
        context.dataStore.edit { it[KEY_LAST_DATA_MUTATION] = timestamp }
    }

    suspend fun setAutoSaveTreeUri(uriString: String) {
        context.dataStore.edit { it[KEY_AUTO_SAVE_TREE_URI] = uriString }
    }

    suspend fun setAutoSaveOnClose(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_SAVE_ON_CLOSE] = enabled }
    }

    suspend fun setHasShownAutoBackupDisclosure(shown: Boolean) {
        cachedSettings = cachedSettings?.copy(hasShownAutoBackupDisclosure = shown)
        context.dataStore.edit {
            it[KEY_DISCLOSURE_SHOWN] = shown
            mapPreferencesToSettings(it)
        }
    }

    suspend fun setHasShownAutoBackupDialog(shown: Boolean) {
        cachedSettings = cachedSettings?.copy(
            hasShownAutoBackupDialog = shown,
            hasShownAutoBackupDisclosure = if (shown) true else (cachedSettings?.hasShownAutoBackupDisclosure ?: false)
        )
        context.dataStore.edit {
            it[KEY_AUTO_BACKUP_DIALOG_SHOWN] = shown
            if (shown) {
                it[KEY_DISCLOSURE_SHOWN] = true
            }
            mapPreferencesToSettings(it)
        }
    }

    suspend fun setHasShownAutoBackupNotification(shown: Boolean) {
        context.dataStore.edit {
            it[KEY_AUTO_BACKUP_NOTIFICATION_SHOWN] = shown
            mapPreferencesToSettings(it)
        }
    }

    suspend fun setHasAskedCommitmentNotificationPermission(asked: Boolean) {
        cachedSettings = cachedSettings?.copy(hasAskedCommitmentNotificationPermission = asked)
        context.dataStore.edit {
            it[KEY_ASKED_COMMITMENT_NOTIF_PERMISSION] = asked
            mapPreferencesToSettings(it)
        }
    }

    suspend fun setReminderLastNotified(markers: Set<String>) {
        cachedSettings = cachedSettings?.copy(reminderLastNotified = markers)
        context.dataStore.edit {
            it[KEY_REMINDER_LAST_NOTIFIED] = markers
            mapPreferencesToSettings(it)
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setMediaMirrorEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MEDIA_MIRROR_ENABLED] = enabled }
    }

    suspend fun setMediaMirrorTreeUri(uri: String) {
        context.dataStore.edit { it[KEY_MEDIA_MIRROR_TREE_URI] = uri }
    }

    suspend fun clearSettings() {
        _currencySymbolFlow.value = "₹"
        com.example.domain.util.Formatters.updateCurrentCurrencySymbol("₹")
        context.dataStore.edit {
            it.clear()
            mapPreferencesToSettings(it)
        }
    }
}
