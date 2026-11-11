package com.example.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import java.text.SimpleDateFormat
import java.util.Date
import android.net.Uri
import android.os.Build
import com.example.BuildConfig
import com.example.data.AppDatabase
import com.example.data.datastore.AppSettingsRepository
import com.example.data.repository.MediaRepository
import com.example.importbackup.MediaStoreWriter
import com.example.ui.dashboard.HelpGesturesSheet
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import com.example.ui.common.AppIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import com.example.ui.common.LocalNoticeDispatcher
import com.example.ui.common.SingleFlightNoticeDispatcher
import com.example.ui.common.SingleFlightSnackbarHost
import com.example.ui.common.rememberSingleFlightNoticeDispatcher
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.common.AppTopHeader
import com.example.ui.common.liquidGlass
import com.example.ui.common.liquidGlassAtmosphere
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber500
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.Navy800
import com.example.ui.theme.Navy900
import com.example.ui.theme.SoftBlue
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    noticeDispatcher: SingleFlightNoticeDispatcher? = null
) {
    val state by viewModel.uiState.collectAsState()
    val showExitDemoAuthGate by viewModel.showExitDemoAuthGate.collectAsState()
    val context = LocalContext.current
    val appSettingsRepo = remember(context) { AppSettingsRepository(context) }
    val appSettings by appSettingsRepo.settingsFlow.collectAsState(initial = state.settings)
    val isDemoMode = state.settings.demoMode || appSettings.demoMode
    val coroutineScope = rememberCoroutineScope()
    val ambientDispatcher = LocalNoticeDispatcher.current
    val fallbackDispatcher = rememberSingleFlightNoticeDispatcher()
    val effectiveNoticeDispatcher = noticeDispatcher ?: ambientDispatcher ?: fallbackDispatcher

    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showAnchorDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showLockTimeoutDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showEmptySaveConfirmDialog by remember { mutableStateOf(false) }
    var showEmptyClearConfirmDialog by remember { mutableStateOf(false) }
    var showSampleDataDialog by remember { mutableStateOf(false) }
    var showRestoreSafetyNetDialog by remember { mutableStateOf(false) }
    var showRestoreDownloadsDialog by remember { mutableStateOf(false) }
    var showHelpGesturesSheet by remember { mutableStateOf(false) }
    var showDueReminderPermissionExplanation by remember { mutableStateOf(false) }
    var showAutoBackupPermissionExplanation by remember { mutableStateOf(false) }

    var mergeTransfersChecked by remember { mutableStateOf(true) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(effectiveNoticeDispatcher) {
        viewModel.statusMessage.collect { msg ->
            effectiveNoticeDispatcher.showNotice(
                key = "SETTINGS_STATUS:$msg",
                message = msg,
                duration = SnackbarDuration.Short
            )
        }
    }

    // 1. CSV Picker for existing Import Preview (Safe OpenDocument)
    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingImportUri = uri
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        viewModel.previewCsv(stream, mergeTransfersChecked)
                    }
                } catch (e: Exception) {
                    viewModel.reportFailure("Failed to read CSV: ${e.localizedMessage}")
                }
            }
        }
    }

    // 2. JSON Backup Export
    val backupExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        viewModel.exportBackup(stream)
                    } ?: viewModel.reportFailure("Could not open file for writing")
                } catch (e: Exception) {
                    viewModel.reportFailure("Export failed: ${e.localizedMessage}")
                }
            }
        }
    }

    // 3. JSON Backup Restore (Safe OpenDocument)
    val backupRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        viewModel.restoreBackup(stream)
                    }
                } catch (e: Exception) {
                    viewModel.reportFailure("Restore failed: ${e.localizedMessage}")
                }
            }
        }
    }

    // 4. Power BI CSV Report Export
    val reportExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        viewModel.exportCsvReport(stream)
                    }
                } catch (e: Exception) {
                    viewModel.reportFailure("Report export failed: ${e.localizedMessage}")
                }
            }
        }
    }

    // 5. CSV Import Picker (Safe OpenDocument)
    val importCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                viewModel.restoreFromUri(context, uri)
            }
        }
    }

    // 6. Storage Permission Launcher (for older Android APIs)
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            coroutineScope.launch(Dispatchers.IO) { viewModel.exportCsvBackup(context) }
        } else {
            effectiveNoticeDispatcher.showNotice(
                key = "STORAGE_PERMISSION_DENIED",
                message = "Storage permission denied",
                duration = SnackbarDuration.Short
            )
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* preference is already saved; worker gracefully checks permissions */ }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            } catch (_: Exception) {}
            viewModel.setAutoSaveTreeUri(treeUri.toString())
        }
    }

    fun safeLaunchCsvPicker() {
        if (isDemoMode) {
            viewModel.reportFailure("Unavailable in sample mode")
            return
        }
        try {
            csvPickerLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/*", "*/*"))
        } catch (e: Exception) {
            viewModel.reportFailure("Could not launch file picker: ${e.localizedMessage}")
        }
    }

    fun safeLaunchCsvImport() {
        if (isDemoMode) {
            viewModel.reportFailure("Unavailable in sample mode")
            return
        }
        try {
            importCsvLauncher.launch(arrayOf("text/csv", "*/*"))
        } catch (e: Exception) {
            viewModel.reportFailure("Could not launch file picker: ${e.localizedMessage}")
        }
    }

    fun safeLaunchJsonRestore() {
        if (isDemoMode) {
            viewModel.reportFailure("Unavailable in sample mode")
            return
        }
        try {
            backupRestoreLauncher.launch(arrayOf("*/*", "application/json", "text/plain"))
        } catch (e: Exception) {
            viewModel.reportFailure("Could not launch file picker: ${e.localizedMessage}")
        }
    }

    if (showExitDemoAuthGate) {
        DemoExitLockGate(
            correctPin = state.settings.pinHash.ifBlank { appSettings.pinHash },
            context = context,
            settingsRepository = viewModel.settingsRepository,
            onUnlocked = { viewModel.confirmExitDemoAfterAuth(context) },
            onDismiss = { viewModel.dismissExitDemoAuthGate() }
        )
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .liquidGlassAtmosphere()
        ) {
            AppTopHeader(
                title = "SilentSums",
                subtitle = "Settings & Vault",
                actions = {
                    if (state.settings.helpVisible) {
                        com.example.ui.common.HeaderIconButton(
                            onClick = { showHelpGesturesSheet = true },
                            testTag = "help_gestures_btn",
                            contentDescription = "Help and gestures"
                        ) {
                            Text(
                                text = "?",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Vault Header Banner
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(16.dp, tint = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Ledger Control",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                            .testTag("settings_version_badge")
                                    )
                                }
                            }
                            Text(
                                text = "Local offline vault · Private mode · 100% On-device",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                // 2. Preferences Section
                Text("Preferences", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.semantics { heading() })
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(14.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                        // Currency
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCurrencyDialog = true }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Ledger Currency", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Primary display unit", fontSize = 11.sp, color = TextMuted, maxLines = 2)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = when (state.settings.currencySymbol) {
                                        "₹" -> "Indian Rupee (₹)"
                                        "$" -> "US Dollar ($)"
                                        "€" -> "Euro (€)"
                                        "£" -> "British Pound (£)"
                                        else -> state.settings.currencySymbol
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1
                                )
                                Icon(AppIcons.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                        // Month Cycle Anchor
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAnchorDialog = true }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Month Cycle Anchor", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Starting cutoff day for budgeting", fontSize = 11.sp, color = TextMuted, maxLines = 2)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Day ${state.settings.monthStartDay}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1
                                )
                                Icon(AppIcons.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                        // Auto theme (A) follows
                        val isAutoSourceDevice = appSettings.autoThemeSource != AppSettingsRepository.AUTO_THEME_SOURCE_DAYLIGHT
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .testTag("settings_auto_theme_source_row"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Auto theme (A) follows",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isAutoSourceDevice) {
                                        "Follows your device theme."
                                    } else {
                                        "Day mode 6 AM–6 PM · Night mode 6 PM–6 AM."
                                    },
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    maxLines = 2,
                                    modifier = Modifier.testTag("settings_auto_source_caption")
                                )
                            }
                            Switch(
                                checked = isAutoSourceDevice,
                                onCheckedChange = { isDevice ->
                                    viewModel.setAutoThemeSource(
                                        if (isDevice) {
                                            AppSettingsRepository.AUTO_THEME_SOURCE_DEVICE
                                        } else {
                                            AppSettingsRepository.AUTO_THEME_SOURCE_DAYLIGHT
                                        }
                                    )
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .testTag("settings_auto_source_toggle")
                                    .semantics { stateDescription = if (isAutoSourceDevice) "On" else "Off" }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                        // Sound & Tactile Haptics
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Tactile Feedback", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Haptic response on keypad & actions", fontSize = 11.sp, color = TextMuted, maxLines = 2)
                            }
                            Switch(
                                checked = state.settings.soundHapticsEnabled,
                                onCheckedChange = { viewModel.setSoundHaptics(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.semantics {
                                    stateDescription = if (state.settings.soundHapticsEnabled) "On" else "Off"
                                }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                        // Dashboard Budgets Card
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("Dashboard Budgets Card", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Show budget card, insights line & source chips on Dashboard", fontSize = 11.sp, color = TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Switch(
                                checked = state.settings.budgetsCardVisible,
                                onCheckedChange = { viewModel.setBudgetsCardVisible(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .testTag("budgets_card_switch")
                                    .semantics {
                                        stateDescription = if (state.settings.budgetsCardVisible) "On" else "Off"
                                    }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                        // Help & Gestures Visibility Switch (1.3)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("Show help & gestures tips", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Display shortcuts help and gesture hints across the app", fontSize = 11.sp, color = TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Switch(
                                checked = state.settings.helpVisible,
                                onCheckedChange = { visible ->
                                    coroutineScope.launch {
                                        appSettingsRepo.setHelpVisible(visible)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .testTag("help_visible_switch")
                                    .semantics {
                                        stateDescription = if (state.settings.helpVisible) "On" else "Off"
                                    }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                        // Due Reminders
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.RequestQuote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Due Reminders", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("Notifications for upcoming & overdue dues", fontSize = 11.sp, color = TextMuted, maxLines = 2)
                                }
                            }
                            Switch(
                                checked = state.settings.dueRemindersEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            showDueReminderPermissionExplanation = true
                                        } else {
                                            viewModel.setDueReminders(true)
                                        }
                                    } else {
                                        viewModel.setDueReminders(false)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .testTag("due_reminders_switch")
                                    .semantics {
                                        stateDescription = if (state.settings.dueRemindersEnabled) "On" else "Off"
                                    }
                            )
                        }

                        // Help & gestures row (gated on helpVisible)
                        if (state.settings.helpVisible) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showHelpGesturesSheet = true }
                                    .padding(vertical = 12.dp)
                                    .testTag("help_gestures_row"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Lightbulb,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Help & gestures", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("Shortcuts and hidden gestures across the app", fontSize = 11.sp, color = TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Icon(
                                    imageVector = AppIcons.ChevronRight,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // 3. Security & Privacy
                Text("Security & Privacy", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.semantics { heading() })
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(14.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                        // App Lock Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Biometric / PIN Lock", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Require unlock on launch", fontSize = 11.sp, color = TextMuted, maxLines = 2)
                            }
                            Switch(
                                checked = state.settings.lockEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled && state.settings.pinHash.isBlank()) {
                                        showPinDialog = true
                                    } else {
                                        viewModel.setLockEnabled(enabled)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.semantics {
                                    stateDescription = if (state.settings.lockEnabled) "On" else "Off"
                                }
                            )
                        }

                        if (state.settings.lockEnabled || state.settings.pinHash.isNotBlank()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showPinDialog = true }
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (state.settings.pinHash.isBlank()) "Set App PIN" else "Change App PIN",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                )
                                Icon(AppIcons.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            val currentTimeoutLabel = when (state.settings.lockTimeoutMs) {
                                0L -> "Immediately"
                                60_000L -> "After 1 minute"
                                300_000L -> "After 5 minutes"
                                900_000L -> "After 15 minutes"
                                1800_000L -> "After 30 minutes"
                                else -> "After ${state.settings.lockTimeoutMs / 60000} minutes"
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showLockTimeoutDialog = true }
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text("Require Lock", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(currentTimeoutLabel, fontSize = 11.sp, color = TextMuted, maxLines = 2)
                                }
                                Icon(AppIcons.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                        // Hide Balances on Launch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Privacy Mode", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Mask balances with ••••••", fontSize = 11.sp, color = TextMuted, maxLines = 2)
                            }
                            Switch(
                                checked = state.settings.hideBalances,
                                onCheckedChange = { viewModel.setHideBalances(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.semantics {
                                    stateDescription = if (state.settings.hideBalances) "On" else "Off"
                                }
                            )
                        }
                    }
                }

                // 4. Data & Backups
                Text("Data & Backups", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.semantics { heading() })
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth().testTag("media_vault_backup_disclosure")
                ) {
                    Text(
                        text = "Photos and voice notes stay on this device and are not included in CSV backups.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                // Data Folder Card (F2: Top of Data & Backups section, always visible; F5: Demo-locked)
                val configuredTree = state.settings.autoSaveTreeUri
                val folderDisplayName = MediaStoreWriter.displayPathForRoot(context, configuredTree)
                val isResetEnabled = !isDemoMode && configuredTree.isNotBlank()

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    "Data folder",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isDemoMode) TextMuted else TextPrimary
                                )
                                Text(
                                    folderDisplayName,
                                    fontSize = 12.sp,
                                    color = if (isDemoMode) TextMuted else MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.testTag("backup_location_current_text")
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(
                                    onClick = { if (!isDemoMode) folderPickerLauncher.launch(null) },
                                    enabled = !isDemoMode,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("backup_location_change_button")
                                ) {
                                    Text(
                                        "Change",
                                        fontSize = 12.sp,
                                        color = if (!isDemoMode) MaterialTheme.colorScheme.primary else TextMuted.copy(alpha = 0.5f)
                                    )
                                }
                                TextButton(
                                    onClick = { if (isResetEnabled) viewModel.resetAutoSaveTreeUri(context) },
                                    enabled = isResetEnabled,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("backup_location_reset_button")
                                ) {
                                    Text(
                                        "Reset",
                                        fontSize = 12.sp,
                                        color = if (isResetEnabled) TextPrimary else TextMuted.copy(alpha = 0.45f)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Files in shared folders can be readable by other apps with storage permission. Your hidden safety-net backup always stays inside the app.",
                            fontSize = 11.sp,
                            color = TextMuted,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .testTag("settings_backup_disclosure")
                        )
                    }
                }

                // Storage Meter Card
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(AppIcons.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Storage Used", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${state.transactionCount} Transactions · ${state.accountCount} Accounts · Optimized",
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    maxLines = 2
                                )
                            }
                        }

                        Text(
                            text = String.format(Locale.getDefault(), "%.2f MB", state.storageEstimateMb),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("storage_used_value_text")
                        )
                    }
                }

                // Daily Auto-Save Card
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(14.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        val autoSaveDayFmt = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }
                        val autoSaveTimeFmt = remember { SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()) }
                        val autoSaveStatusText = remember(
                            state.settings.autoBackupEnabled,
                            state.settings.autoSaveFailingSince,
                            state.settings.lastAutoSaveSuccess,
                            state.settings.lastSuccessfulAutoSaveTimestamp
                        ) {
                            when {
                                !state.settings.autoBackupEnabled -> "Auto-backup is off"
                                state.settings.autoSaveFailingSince > 0L || !state.settings.lastAutoSaveSuccess -> {
                                    val whenText = if (state.settings.autoSaveFailingSince > 0L) {
                                        autoSaveDayFmt.format(Date(state.settings.autoSaveFailingSince))
                                    } else {
                                        "recently"
                                    }
                                    "⚠ auto-save failing since $whenText"
                                }
                                state.settings.lastSuccessfulAutoSaveTimestamp > 0L -> {
                                    val whenText = autoSaveTimeFmt.format(Date(state.settings.lastSuccessfulAutoSaveTimestamp))
                                    "Last auto-save: $whenText ✓"
                                }
                                else -> "Last auto-save: pending first cycle ✓"
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Daily Auto-Save", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                Text(
                                    autoSaveStatusText,
                                    fontSize = 11.sp,
                                    color = if (!state.settings.autoBackupEnabled) TextMuted else if (!state.settings.lastAutoSaveSuccess || state.settings.autoSaveFailingSince > 0L) ExpenseRed else IncomeGreen
                                )
                            }
                            Switch(
                                checked = state.settings.autoBackupEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            showAutoBackupPermissionExplanation = true
                                        } else {
                                            viewModel.setAutoBackupEnabled(true)
                                        }
                                    } else {
                                        viewModel.setAutoBackupEnabled(false)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.semantics {
                                    stateDescription = if (state.settings.autoBackupEnabled) "On" else "Off"
                                }
                            )
                        }

                        if (state.settings.autoBackupEnabled) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text("Auto-Save on App Close", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                    Text("Save changes whenever you exit or switch away", fontSize = 11.sp, color = TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Switch(
                                    checked = state.settings.autoSaveOnClose,
                                    onCheckedChange = { viewModel.setAutoSaveOnClose(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.semantics {
                                        stateDescription = if (state.settings.autoSaveOnClose) "On" else "Off"
                                    }
                                )
                            }
                        }
                    }
                }

                if (isDemoMode) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Amber500.copy(alpha = 0.12f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("demo_firewall_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(AppIcons.Tune, contentDescription = null, tint = Amber500, modifier = Modifier.size(20.dp))
                                Text(
                                    text = "Sample mode active — backups and data resets are paused to protect your files.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                            }
                            OutlinedButton(
                                onClick = { viewModel.requestExitDemo(context) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("settings_demo_cancel")
                            ) {
                                Text("Cancel", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Actions List Card
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(14.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                        // Load Sample Data / Exit Sample Mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isDemoMode) {
                                        viewModel.requestExitDemo(context)
                                    } else {
                                        showSampleDataDialog = true
                                    }
                                }
                                .padding(vertical = 12.dp)
                                .testTag("settings_sample_data_row"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            ) {
                                Icon(AppIcons.Tune, contentDescription = null, tint = Amber500, modifier = Modifier.size(20.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Load Sample Data", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (isDemoMode) "Sample mode is currently active — tap to exit" else "Preload realistic transactions & accounts for preview", fontSize = 11.sp, color = TextMuted, maxLines = 2)
                                }
                            }
                            Icon(AppIcons.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                        // 1. One-Tap Save / Sync to Phone CSV (Downloads/SilentSums/expense_tracker.csv)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (isDemoMode) 0.38f else 1.0f)
                                .clickable(enabled = !isDemoMode) {
                                    if (state.transactionCount == 0) {
                                        showEmptySaveConfirmDialog = true
                                    } else {
                                        if (Build.VERSION.SDK_INT < 29 &&
                                            ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                                            ) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                        } else {
                                            coroutineScope.launch(Dispatchers.IO) { viewModel.exportCsvBackup(context) }
                                        }
                                    }
                                }
                                .padding(vertical = 12.dp)
                                .testTag("save_sync_csv_button"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            ) {
                                Icon(AppIcons.FileDownload, contentDescription = null, tint = IncomeGreen, modifier = Modifier.size(20.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Save / Sync to Phone CSV", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (isDemoMode) {
                                        Text("Unavailable in sample mode", fontSize = 11.sp, color = Amber500, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    } else {
                                        val activeRootDisplayPath = MediaStoreWriter.displayPathForRoot(context, state.settings.autoSaveTreeUri)
                                        Text(
                                            "Saves single unified file to $activeRootDisplayPath/${com.example.importbackup.UnifiedCsvStorage.CSV_FILE_NAME}",
                                            fontSize = 11.sp,
                                            color = TextMuted,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.testTag("settings_save_sync_subtitle")
                                        )
                                    }
                                }
                            }
                            Icon(AppIcons.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                        // 2. Share Backup File via Action Send + FileProvider (B5)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (isDemoMode) 0.38f else 1.0f)
                                .clickable(enabled = !isDemoMode) { viewModel.shareBackupFile(context) }
                                .padding(vertical = 12.dp)
                                .testTag("share_backup_button"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            ) {
                                Icon(AppIcons.Sync, contentDescription = null, tint = SoftBlue, modifier = Modifier.size(20.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Share Backup File", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (isDemoMode) {
                                        Text("Unavailable in sample mode", fontSize = 11.sp, color = Amber500, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    } else {
                                        Text("Share CSV directly via Android Share Sheet", fontSize = 11.sp, color = TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            Icon(AppIcons.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                        // 3. Import a CSV file (foreign / external / orphan file picker)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (isDemoMode) 0.38f else 1.0f)
                                .clickable(enabled = !isDemoMode) { safeLaunchCsvPicker() }
                                .padding(vertical = 12.dp)
                                .testTag("import_csv_button")
                                .testTag("import_backup_button"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            ) {
                                Icon(AppIcons.UploadFile, contentDescription = null, tint = SoftBlue, modifier = Modifier.size(20.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Import a CSV file", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (isDemoMode) {
                                        Text("Unavailable in sample mode", fontSize = 11.sp, color = Amber500, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    } else {
                                        Text("Choose a backup or external CSV to merge or overwrite", fontSize = 11.sp, color = TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            Icon(AppIcons.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                        // 4. Restore from CSV Backup
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (isDemoMode) 0.38f else 1.0f)
                                .clickable(enabled = !isDemoMode) { showRestoreDownloadsDialog = true }
                                .padding(vertical = 12.dp)
                                .testTag("restore_downloads_button"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            ) {
                                Icon(AppIcons.History, contentDescription = null, tint = Amber500, modifier = Modifier.size(20.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Restore from CSV Backup", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (isDemoMode) {
                                        Text("Unavailable in sample mode", fontSize = 11.sp, color = Amber500, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    } else {
                                        val activeRootDisplayPath = MediaStoreWriter.displayPathForRoot(context, state.settings.autoSaveTreeUri)
                                        Text(
                                            "Scans $activeRootDisplayPath and internal auto backups for the newest snapshot",
                                            fontSize = 11.sp,
                                            color = TextMuted,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.testTag("settings_restore_csv_subtitle")
                                        )
                                    }
                                }
                            }
                            Icon(AppIcons.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                        // Clear All Data
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (isDemoMode) 0.38f else 1.0f)
                                .clickable(enabled = !isDemoMode) {
                                    if (state.transactionCount == 0) {
                                        showEmptyClearConfirmDialog = true
                                     } else {
                                        showClearConfirmDialog = true
                                     }
                                }
                                .padding(vertical = 12.dp)
                                .testTag("clear_data_button"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            ) {
                                Icon(AppIcons.DeleteForever, contentDescription = null, tint = ExpenseRed, modifier = Modifier.size(20.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Clear All Ledger Data", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (isDemoMode) TextMuted else ExpenseRed, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (isDemoMode) {
                                        Text("Unavailable in sample mode", fontSize = 11.sp, color = Amber500, maxLines = 2)
                                    } else {
                                        Text("Reset all local accounts and records", fontSize = 11.sp, color = TextMuted, maxLines = 2)
                                    }
                                }
                            }
                            Icon(AppIcons.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        if (ambientDispatcher == null) {
            SingleFlightSnackbarHost(
                dispatcher = effectiveNoticeDispatcher,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 72.dp)
            )
        }
    }

    // Currency Selector Dialog
    if (showCurrencyDialog) {
        val currencies = listOf("₹" to "Indian Rupee (₹)", "$" to "US Dollar ($)", "€" to "Euro (€)", "£" to "British Pound (£)")
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text("Select Primary Currency") },
            text = {
                Column {
                    currencies.forEach { (sym, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setCurrency(sym)
                                    showCurrencyDialog = false
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(name, fontSize = 14.sp)
                            if (state.settings.currencySymbol == sym) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCurrencyDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Month Anchor Day Dialog
    if (showAnchorDialog) {
        var dayText by remember { mutableStateOf(state.settings.monthStartDay.toString()) }
        AlertDialog(
            onDismissRequest = { showAnchorDialog = false },
            title = { Text("Month Cycle Anchor Day") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select which day of the month your personal budget and cycle resets (1 - 28):", fontSize = 12.sp, color = TextSecondary)
                    OutlinedTextField(
                        value = dayText,
                        onValueChange = { dayText = it },
                        label = { Text("Day of Month") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val d = dayText.toIntOrNull() ?: 1
                        viewModel.setMonthStartDay(d.coerceIn(1, 28))
                        showAnchorDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) { Text("Save", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showAnchorDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Set / Change PIN Dialog (Two-step flow: Enter PIN -> Continue -> Confirm PIN -> Save)
    if (showPinDialog) {
        var setupStep by remember { mutableStateOf(1) }
        var firstPinInput by remember { mutableStateOf("") }
        var confirmPinInput by remember { mutableStateOf("") }
        var pinError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = {
                showPinDialog = false
                setupStep = 1
                firstPinInput = ""
                confirmPinInput = ""
                pinError = null
            },
            title = {
                Text(
                    text = if (setupStep == 1) {
                        if (state.settings.pinHash.isBlank()) "Set App PIN" else "Change App PIN"
                    } else {
                        "Confirm App PIN"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (setupStep == 1) {
                            "Enter a 4 to 20 digit numeric PIN to protect your offline ledger:"
                        } else {
                            "Re-enter your PIN to confirm:"
                        },
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = if (setupStep == 1) firstPinInput else confirmPinInput,
                        onValueChange = { input ->
                            if (input.length <= 20 && input.all { c -> c.isDigit() }) {
                                if (setupStep == 1) {
                                    firstPinInput = input
                                } else {
                                    confirmPinInput = input
                                }
                                pinError = null
                            }
                        },
                        label = {
                            Text(if (setupStep == 1) "Enter PIN (4-20 digits)" else "Confirm PIN")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinError != null) {
                        Text(pinError ?: "", color = ExpenseRed, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (setupStep == 1) {
                            if (firstPinInput.length >= 4) {
                                setupStep = 2
                                pinError = null
                            } else {
                                pinError = "PIN must be at least 4 digits"
                            }
                        } else {
                            if (confirmPinInput == firstPinInput) {
                                viewModel.setPin(firstPinInput)
                                showPinDialog = false
                                setupStep = 1
                                firstPinInput = ""
                                confirmPinInput = ""
                                pinError = null
                            } else {
                                pinError = "PINs don't match. Please try again."
                                firstPinInput = ""
                                confirmPinInput = ""
                                setupStep = 1
                            }
                        }
                    },
                    enabled = if (setupStep == 1) firstPinInput.length >= 4 else confirmPinInput.length >= 4,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(if (setupStep == 1) "Continue" else "Save PIN", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPinDialog = false
                        setupStep = 1
                        firstPinInput = ""
                        confirmPinInput = ""
                        pinError = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Require Lock Timeout Dialog
    if (showLockTimeoutDialog) {
        val timeoutOptions = listOf(
            0L to "Immediately",
            60_000L to "After 1 minute",
            300_000L to "After 5 minutes",
            900_000L to "After 15 minutes",
            1_800_000L to "After 30 minutes"
        )
        AlertDialog(
            onDismissRequest = { showLockTimeoutDialog = false },
            title = { Text("Require Lock") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    timeoutOptions.forEach { (timeoutMs, label) ->
                        val isSelected = state.settings.lockTimeoutMs == timeoutMs
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setLockTimeoutMs(timeoutMs)
                                    showLockTimeoutDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setLockTimeoutMs(timeoutMs)
                                    showLockTimeoutDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLockTimeoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear Confirmation Dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear All Ledger Data?") },
            text = { Text("This will permanently remove all accounts, categories, and transactions on this device. Attached photos and voice notes will also be deleted. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog = false
                        viewModel.clearAllData(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ExpenseRed,
                        contentColor = Color.White
                    )
                ) { Text("Clear Everything", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    // 0-Row Clobber Guard for Save / Sync
    if (showEmptySaveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEmptySaveConfirmDialog = false },
            title = { Text("Empty Ledger Warning") },
            text = { Text("Your ledger is empty. Saving/clearing now would replace your existing backup with nothing. Continue?") },
            confirmButton = {
                Button(
                    onClick = {
                        showEmptySaveConfirmDialog = false
                        coroutineScope.launch(Dispatchers.IO) { viewModel.exportCsvBackup(context) }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ExpenseRed,
                        contentColor = Color.White
                    )
                ) { Text("Continue", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showEmptySaveConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    // 0-Row Clobber Guard for Clear All Ledger Data
    if (showEmptyClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyClearConfirmDialog = false },
            title = { Text("Empty Ledger Warning") },
            text = { Text("Your ledger is empty. Attached photos and voice notes will also be deleted. Continue?") },
            confirmButton = {
                Button(
                    onClick = {
                        showEmptyClearConfirmDialog = false
                        viewModel.clearAllData(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ExpenseRed,
                        contentColor = Color.White
                    )
                ) { Text("Continue", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyClearConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Sample Data Confirmation Dialog
    if (showSampleDataDialog) {
        AlertDialog(
            onDismissRequest = { showSampleDataDialog = false },
            title = { Text("Load Sample Data?") },
            text = { Text("Explore the app with realistic sample transactions, accounts, and budgets. Sample mode uses separate temporary data; your real data, photos, and voice notes will not be touched.") },
            confirmButton = {
                Button(
                    onClick = {
                        showSampleDataDialog = false
                        viewModel.loadSampleData(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("confirm_load_sample_data_btn")
                ) { Text("Load Sample Data", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showSampleDataDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Restore from Internal Safety Net Dialog
    if (showRestoreSafetyNetDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreSafetyNetDialog = false },
            title = { Text("Restore from Safety Net?") },
            text = { Text("This replaces your current transactions with the file's. Photos and voice notes stay on this phone, but they are not part of CSV backups and may not appear on the restored transactions.") },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreSafetyNetDialog = false
                        coroutineScope.launch(Dispatchers.IO) {
                            viewModel.restoreFromInternalSafetyNet(context)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("restore_safety_net_confirm_btn")
                ) { Text("Restore", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreSafetyNetDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Restore from CSV Backup Dialog
    if (showRestoreDownloadsDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDownloadsDialog = false },
            title = { Text("Restore from CSV Backup?") },
            text = { Text("Your current transactions and their photos and voice notes are kept. New transactions added from the file will not have photos or voice notes, because CSV backups do not include them.") },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreDownloadsDialog = false
                        coroutineScope.launch(Dispatchers.IO) {
                            viewModel.restoreFromAutoSave(context, clearExisting = false)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("restore_downloads_confirm_btn")
                ) { Text("Restore", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDownloadsDialog = false }) { Text("Cancel") }
            }
        )
    }

    // CSV Import Preview Dialog (AM4: Merge vs Overwrite Choice)
    state.csvPreview?.let { preview ->
        var importOverwriteMode by remember(preview) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { viewModel.clearCsvPreview() },
            title = { Text("CSV Import Preview", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Rows: ${preview.totalRows} · Valid: ${preview.validRows}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.testTag("preview_total_rows")
                    )
                    Text(
                        "Valid transactions found: ${preview.validRows}",
                        color = IncomeGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.testTag("preview_valid_rows")
                    )

                    val skippedCount = (preview.totalRows - preview.validRows).coerceAtLeast(0)
                    if (skippedCount > 0) {
                        val reasonsSuffix = if (preview.errors.isNotEmpty()) {
                            " — ${preview.errors.joinToString("; ")}"
                        } else ""
                        Text(
                            "Skipped: $skippedCount$reasonsSuffix",
                            color = ExpenseRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.testTag("preview_skipped_rows")
                        )
                    }

                    if (preview.dateRange.isNotBlank()) {
                        Text(
                            "Date range: ${preview.dateRange}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("preview_date_range")
                        )
                    }

                    if (preview.newAccountsToCreate.isNotEmpty()) {
                        Text("New Accounts to create (${preview.newAccountsToCreate.size}):", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text(preview.newAccountsToCreate.joinToString(", "), fontSize = 11.sp, color = TextSecondary)
                    }

                    if (preview.newCategoriesToCreate.isNotEmpty()) {
                        Text("New Categories to create (${preview.newCategoriesToCreate.size}):", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text(preview.newCategoriesToCreate.joinToString(", "), fontSize = 11.sp, color = TextSecondary)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Text("Import Mode", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimary)

                    // Option 1: Merge (Default)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { importOverwriteMode = false }
                            .padding(vertical = 4.dp)
                            .testTag("import_mode_merge"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !importOverwriteMode,
                            onClick = { importOverwriteMode = false },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Merge with existing records",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                "Adds up to ${preview.validRows} rows to your ${state.transactionCount} existing records (duplicates skipped)",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                modifier = Modifier.testTag("preview_merge_row_count")
                            )
                        }
                    }

                    // Option 2: Overwrite
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { importOverwriteMode = true }
                            .padding(vertical = 4.dp)
                            .testTag("import_mode_overwrite"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = importOverwriteMode,
                            onClick = { importOverwriteMode = true },
                            colors = RadioButtonDefaults.colors(selectedColor = ExpenseRed)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Overwrite existing records",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ExpenseRed
                            )
                            Text(
                                "Replaces all ${state.transactionCount} existing records with ${preview.validRows} rows from file",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                modifier = Modifier.testTag("preview_overwrite_row_count")
                            )
                        }
                    }

                    if (importOverwriteMode) {
                        Text(
                            "Note: This replaces your current transactions with the file's. Photos and voice notes stay on this phone, but they are not part of CSV backups and may not appear on the restored transactions.",
                            fontSize = 11.sp,
                            color = ExpenseRed,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingImportUri
                        if (uri != null) {
                            val clearAll = importOverwriteMode
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    context.contentResolver.openInputStream(uri)?.use { stream ->
                                        viewModel.commitCsvImport(
                                            stream,
                                            mergeTransfers = false,
                                            clearExisting = clearAll
                                        )
                                    }
                                } catch (e: Exception) {
                                    viewModel.reportFailure("Import failed: ${e.localizedMessage}")
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (importOverwriteMode) ExpenseRed else MaterialTheme.colorScheme.primary,
                        contentColor = if (importOverwriteMode) Color.White else MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("preview_confirm_overwrite_button")
                ) {
                    Text(
                        if (importOverwriteMode) "Overwrite & Restore" else "Merge & Import",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearCsvPreview() }) { Text("Cancel") }
            }
        )
    }

    if (showDueReminderPermissionExplanation) {
        AlertDialog(
            onDismissRequest = {
                showDueReminderPermissionExplanation = false
                viewModel.setDueReminders(true)
            },
            title = { Text("Enable Due Reminders") },
            text = {
                Text("Allow notifications so SilentSums can alert you when recurring bills or dues are approaching.", fontSize = 13.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDueReminderPermissionExplanation = false
                        viewModel.setDueReminders(true)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDueReminderPermissionExplanation = false
                        viewModel.setDueReminders(true)
                    }
                ) {
                    Text("Skip")
                }
            }
        )
    }

    if (showAutoBackupPermissionExplanation) {
        AlertDialog(
            onDismissRequest = {
                showAutoBackupPermissionExplanation = false
                viewModel.setAutoBackupEnabled(true)
            },
            title = { Text("Enable Auto-Backup Notifications") },
            text = {
                Text("Allow notifications so SilentSums can notify you when daily rolling backups finish or require attention.", fontSize = 13.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAutoBackupPermissionExplanation = false
                        viewModel.setAutoBackupEnabled(true)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAutoBackupPermissionExplanation = false
                        viewModel.setAutoBackupEnabled(true)
                    }
                ) {
                    Text("Skip")
                }
            }
        )
    }

    if (showHelpGesturesSheet) {
        HelpGesturesSheet(
            onDismiss = { showHelpGesturesSheet = false }
        )
    }
}

@Composable
fun DemoExitLockGate(
    correctPin: String,
    context: android.content.Context,
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit,
    settingsRepository: com.example.data.datastore.AppSettingsRepository? = null
) {
    val scope = rememberCoroutineScope()
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    com.example.ui.lock.LockScreen(
        correctPin = correctPin,
        settingsRepository = settingsRepository,
        onUnlocked = onUnlocked,
        onBiometricClick = {
            (com.example.data.DatabaseProvider.findActivity(context) as? androidx.fragment.app.FragmentActivity)?.let { act ->
                com.example.ui.showBiometricPrompt(
                    activity = act,
                    onSuccess = {
                        scope.launch {
                            (settingsRepository ?: com.example.data.datastore.AppSettingsRepository(context.applicationContext)).resetPinLockout()
                        }
                        onUnlocked()
                    }
                )
            }
        }
    )
}