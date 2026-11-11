package com.example.ui.entry

import android.app.DatePickerDialog
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import com.example.ui.common.AppIcons
import com.example.ui.common.CalculatorBottomSheet
import com.example.ui.common.CalculatorMode
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.core.content.FileProvider
import com.example.data.repository.MediaRepository
import com.example.data.repository.StagedMedia
import com.example.data.model.MediaAttachment
import android.media.MediaRecorder
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.alpha
import java.io.File
import java.util.UUID
import com.example.ui.ledger.UncategorizedNudgeManager
import com.example.domain.budget.BudgetCalculator
import com.example.domain.budget.BudgetTintTier
import com.example.ui.theme.Amber500
import java.util.Locale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.domain.util.Formatters
import com.example.ui.common.CategoryEmojiBadge
import com.example.ui.common.CompactThemeToggle
import com.example.ui.common.liquidGlass
import com.example.ui.common.liquidGlassAtmosphere
import com.example.ui.common.liquidGlassPill
import com.example.ui.common.parseColorHex
import com.example.ui.theme.Amber400
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.ExpenseRedBg
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.IncomeGreenBg
import com.example.ui.theme.Navy800
import com.example.ui.theme.Navy900
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TransferGray
import com.example.ui.theme.TransferGrayBg
import com.example.ui.common.SingleFlightNoticeDispatcher
import com.example.ui.common.SingleFlightSnackbarHost
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    viewModel: EntryViewModel,
    onNavigateBack: () -> Unit,
    onAddSource: () -> Unit = {},
    hapticsEnabled: Boolean = true,
    hideBalances: Boolean = false,
    modifier: Modifier = Modifier,
    noticeDispatcher: SingleFlightNoticeDispatcher? = null
) {
    val state by viewModel.formState.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val customFields by viewModel.customFields.collectAsState()
    val liveBudget by viewModel.liveTightestBudget.collectAsState()
    val crossedBudgets by viewModel.crossedBudgetsWarning.collectAsState()
    val quickLogEnabled by viewModel.quickLogChipsEnabled.collectAsState()
    val frequentTxs by viewModel.recentFrequentTransactions.collectAsState()

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val ambientDispatcher = com.example.ui.common.LocalNoticeDispatcher.current
    val fallbackDispatcher = com.example.ui.common.rememberSingleFlightNoticeDispatcher()
    val effectiveNoticeDispatcher = noticeDispatcher ?: ambientDispatcher ?: fallbackDispatcher
    val currencySymbol by com.example.data.datastore.AppSettingsRepository.sharedCurrencySymbolFlow.collectAsState()
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showKeypad by remember { mutableStateOf(true) }
    var showCalculatorSheet by remember { mutableStateOf(false) }

    var isSavedSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.saveSuccessEvent.collect {
            isSavedSuccess = true
            if (state.categoryId == null && state.type != "TRANSFER") {
                UncategorizedNudgeManager.triggerNudge(state.transactionId)
                effectiveNoticeDispatcher.showNotice(
                    key = "UNCATEGORIZED_NUDGE",
                    message = "Saved without a category",
                    actionLabel = "Add category",
                    duration = SnackbarDuration.Short,
                    onActionPerformed = {
                        showAddCategoryDialog = true
                    }
                )
            }
            kotlinx.coroutines.delay(180L)
            onNavigateBack()
        }
    }

    val stagedMedia by viewModel.stagedMedia.collectAsState()
    val existingAttachments by viewModel.existingAttachments.collectAsState()
    val mediaWarning by viewModel.mediaWarning.collectAsState()
    val totalPhotos by viewModel.totalPhotoCount.collectAsState()
    val totalVoice by viewModel.totalVoiceCount.collectAsState()
    val isDemoMode by viewModel.isDemoMode.collectAsState()

    val effectiveMediaRepo = remember(context, viewModel.effectiveMediaRepository) {
        viewModel.effectiveMediaRepository ?: MediaRepository(context, com.example.data.AppDatabase.getDatabase(context))
    }
    LaunchedEffect(effectiveMediaRepo) {
        viewModel.bindMediaRepository(effectiveMediaRepo)
    }

    var showDraftViewer by remember { mutableStateOf(false) }
    var draftViewerInitialTab by remember { mutableStateOf("IMAGES") }

    var tempCameraFile by remember { mutableStateOf<File?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordingSeconds by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var activeRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var currentVoiceFile by remember { mutableStateOf<File?>(null) }
    var voicePermissionDenied by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "voice_recording_pulse")
    val recPulseAlpha by if (isRecordingVoice) {
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "rec_pulse_alpha"
        )
    } else {
        remember { mutableStateOf(1f) }
    }
    val recPulseScale by if (isRecordingVoice) {
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "rec_pulse_scale"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempCameraFile != null) {
            coroutineScope.launch {
                try {
                    val staged = effectiveMediaRepo.compressAndStageImage(tempCameraFile!!)
                    viewModel.addStagedMedia(staged)
                } catch (_: Exception) {}
            }
        }
    }

    val pickMediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = (10 - totalPhotos).coerceAtLeast(1))
    ) { uris ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                for (uri in uris) {
                    if (viewModel.totalPhotoCount.value >= 10) {
                        viewModel.addStagedMedia(StagedMedia(kind = "PHOTO", file = File(""), byteSize = 0L))
                        break
                    }
                    try {
                        val staged = effectiveMediaRepo.compressAndStageUri(uri)
                        viewModel.addStagedMedia(staged)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    var recordingStartMs by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var privacyGateNotice by remember { mutableStateOf<String?>(null) }
    val activePlayingVoiceKey by MediaRepository.activePlayingVoiceKey.collectAsState()

    fun stopAndSaveVoice() {
        try {
            activeRecorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {}
        activeRecorder = null
        isRecordingVoice = false
        val file = currentVoiceFile
        if (file != null && file.exists() && file.length() > 0) {
            val elapsedMs = if (recordingStartMs > 0L) {
                (System.currentTimeMillis() - recordingStartMs).coerceAtLeast(0L)
            } else {
                recordingSeconds * 1000L
            }
            val durationMs = MediaRepository.resolveVoiceDurationMs(file, elapsedMs)
            viewModel.addStagedMedia(
                StagedMedia(
                    kind = "VOICE",
                    file = file,
                    byteSize = file.length(),
                    durationMs = durationMs
                )
            )
        }
        recordingStartMs = 0L
        currentVoiceFile = null
        recordingSeconds = 0
    }

    fun startVoiceRecording() {
        if (isDemoMode || totalVoice >= 3) {
            if (totalVoice >= 3) {
                viewModel.addStagedMedia(StagedMedia(kind = "VOICE", file = File(""), byteSize = 0L))
            }
            return
        }
        val voiceDir = File(context.cacheDir, "media_staging").apply { mkdirs() }
        val voiceFile = File(voiceDir, "VOICE_${UUID.randomUUID()}.m4a")
        currentVoiceFile = voiceFile
        try {
            val mr = MediaRecorder(context).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64_000)
                setAudioSamplingRate(44_100)
                setAudioChannels(1)
                setMaxDuration(120_000)
                setOutputFile(voiceFile.absolutePath)
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        stopAndSaveVoice()
                    }
                }
                prepare()
                start()
            }
            activeRecorder = mr
            recordingStartMs = System.currentTimeMillis()
            isRecordingVoice = true
            recordingSeconds = 0
        } catch (e: Exception) {
            isRecordingVoice = false
            recordingStartMs = 0L
            currentVoiceFile = null
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voicePermissionDenied = false
            startVoiceRecording()
        } else {
            voicePermissionDenied = true
        }
    }

    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            while (isRecordingVoice) {
                kotlinx.coroutines.delay(1000L)
                recordingSeconds++
                if (recordingSeconds >= 120) {
                    stopAndSaveVoice()
                    break
                }
            }
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            try {
                if (activeRecorder != null) {
                    activeRecorder?.stop()
                    activeRecorder?.release()
                }
            } catch (_: Exception) {}
        }
    }

    BackHandler {
        if (isRecordingVoice) {
            stopAndSaveVoice()
        }
        viewModel.onCancelOrDismiss()
        onNavigateBack()
    }

    Scaffold(
        snackbarHost = { if (ambientDispatcher == null) SingleFlightSnackbarHost(effectiveNoticeDispatcher) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.transactionId != null) "Edit Transaction #${state.transactionId}" else "New Transaction",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isRecordingVoice) stopAndSaveVoice()
                            viewModel.onCancelOrDismiss()
                            onNavigateBack()
                        },
                        modifier = Modifier.testTag("entry_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    CompactThemeToggle(
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    if (state.transactionId != null) {
                        IconButton(
                            onClick = { showDeleteConfirmDialog = true },
                            modifier = Modifier.testTag("entry_delete_button")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ExpenseRed)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    if (state.errorMessage != null) {
                        Text(
                            text = state.errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    Button(
                        onClick = { viewModel.saveTransaction() },
                        enabled = state.isSplitValidToSave,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSavedSuccess) IncomeGreen else MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("save_transaction_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            AnimatedVisibility(
                                visible = isSavedSuccess,
                                enter = fadeIn() + scaleIn(initialScale = 0.5f),
                                exit = fadeOut()
                            ) {
                                Row {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                            }
                            Text(
                                text = if (isSavedSuccess) "Saved!" else if (state.transactionId != null) "Update Transaction" else "Save Transaction",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .liquidGlassAtmosphere()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Type Segmented Control (Expense / Income / Transfer)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("EXPENSE" to "Expense", "INCOME" to "Income", "TRANSFER" to "Transfer").forEach { (type, label) ->
                    val isSelected = state.type == type
                    val selColor = when (type) {
                        "EXPENSE" -> ExpenseRed
                        "INCOME" -> IncomeGreen
                        else -> MaterialTheme.colorScheme.primary
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 42.dp)
                            .liquidGlassPill(
                                selected = isSelected,
                                selectedColor = if (isSelected) selColor else null,
                                cornerRadius = 12.dp
                            )
                            .clickable(
                                role = androidx.compose.ui.semantics.Role.Tab,
                                onClickLabel = label
                            ) { viewModel.setType(type) }
                            .semantics {
                                role = androidx.compose.ui.semantics.Role.Tab
                                selected = isSelected
                                stateDescription = if (isSelected) "Selected" else "Not selected"
                            }
                            .testTag("type_selector_${type.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Amount Display Box
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Amount",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val cur = currencySymbol
                        val prefix = when (state.type) {
                            "EXPENSE" -> "-$cur "
                            "INCOME" -> "+$cur "
                            else -> "$cur "
                        }
                        val displayText = if (state.amountText.isBlank()) "0.00" else state.amountText
                        Text(
                            text = "$prefix$displayText",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            color = when (state.type) {
                                "EXPENSE" -> ExpenseRed
                                "INCOME" -> IncomeGreen
                                else -> MaterialTheme.colorScheme.primary
                            },
                            letterSpacing = (-0.5).sp
                        )

                        val selectedFromAccount = accounts.find { it.id == state.fromAccountId }
                        if (state.type == "EXPENSE" && selectedFromAccount?.accountType == "CREDIT_CARD") {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ExpenseRed.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "Adds to card debt",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ExpenseRed,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        if (liveBudget != null) {
                            val b = liveBudget!!
                            val raw = state.amountText.replace(",", "").trim()
                            val num = if (raw.contains('+') || raw.contains('-') || raw.contains('*') || raw.contains('/')) {
                                com.example.domain.calculator.ExpressionEvaluator.evaluate(raw) ?: 0.0
                            } else {
                                raw.toDoubleOrNull() ?: 0.0
                            }
                            val totalAfter = b.spent + num
                            val isOver = totalAfter >= b.limit
                            val pct = if (b.limit > 0) (totalAfter / b.limit) * 100.0 else 0.0
                            val tier = BudgetCalculator.determineTier(pct)
                            val tintColor = when (tier) {
                                BudgetTintTier.BELOW_80 -> IncomeGreen
                                BudgetTintTier.EIGHTY_TO_99 -> Amber500
                                BudgetTintTier.HUNDRED_OR_ABOVE -> ExpenseRed
                            }
                            val indicatorText = if (isOver) {
                                val overAmt = totalAfter - b.limit
                                "${b.budget.name} over by ${com.example.domain.util.Formatters.formatMoney(overAmt, isMasked = hideBalances, currencySymbol = currencySymbol)}"
                            } else {
                                val remaining = b.limit - totalAfter
                                "${b.budget.name}: ${com.example.domain.util.Formatters.formatMoney(remaining, isMasked = hideBalances, currencySymbol = currencySymbol)} left of ${com.example.domain.util.Formatters.formatMoney(b.limit, isMasked = hideBalances, currencySymbol = currencySymbol)}"
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = tintColor.copy(alpha = 0.12f),
                                modifier = Modifier.testTag("entry_budget_indicator")
                            ) {
                                Text(
                                    text = indicatorText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tintColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { showCalculatorSheet = true },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .testTag("entry_calculator_btn")
                    ) {
                        Icon(
                            imageVector = AppIcons.Calculate,
                            contentDescription = "Open Calculator",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // "Log again" chips: recent frequent transactions that PREFILL the form and NEVER auto-commit
            if (quickLogEnabled && frequentTxs.isNotEmpty() && state.type == "EXPENSE") {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    Text("Log again", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(frequentTxs) { tx ->
                            val catName = categories.find { it.id == tx.categoryId }?.name ?: "Expense"
                            val amtStr = Formatters.formatMoney(tx.amount, isMasked = hideBalances, currencySymbol = currencySymbol)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .clickable { viewModel.prefillFromFrequent(tx) }
                                    .testTag("log_again_chip_${tx.id}")
                            ) {
                                Text(
                                    text = "$amtStr · $catName",
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (showCalculatorSheet) {
                CalculatorBottomSheet(
                    initialExpression = state.amountText,
                    mode = CalculatorMode.USE_AMOUNT,
                    currencySymbol = currencySymbol,
                    onDismiss = { showCalculatorSheet = false },
                    onApplyAmount = { evaluated ->
                        val formatted = if (evaluated % 1.0 == 0.0) {
                            evaluated.toLong().toString()
                        } else {
                            java.text.DecimalFormat("#,##0.##").format(evaluated).replace(",", "")
                        }
                        viewModel.setAmountDirect(formatted)
                    }
                )
            }

            // Custom Keypad (collapsible)
            AnimatedVisibility(visible = showKeypad) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf(".", "0", "BACKSPACE")
                    )

                    rows.forEach { rowKeys ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowKeys.forEach { key ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .semantics {
                                            role = androidx.compose.ui.semantics.Role.Button
                                            contentDescription = when (key) {
                                                "BACKSPACE" -> "Backspace"
                                                "+" -> "Plus"
                                                "-" -> "Minus"
                                                "*" -> "Multiply"
                                                "/" -> "Divide"
                                                "." -> "Decimal point"
                                                else -> key
                                            }
                                        }
                                        .clickable {
                                            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.onKeypadPress(key)
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (key == "BACKSPACE") {
                                            Icon(
                                                imageVector = AppIcons.Backspace,
                                                contentDescription = "Backspace",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else {
                                            Text(
                                                text = key,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Date Row
            val cal = remember(state.date) { Calendar.getInstance().apply { timeInMillis = state.date } }
            val dateLabel = remember(state.date) { Formatters.formatDayHeader(state.date) }

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(12.dp)
                    .clickable {
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val selectedCal = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, y)
                                    set(Calendar.MONTH, m)
                                    set(Calendar.DAY_OF_MONTH, d)
                                }
                                viewModel.setDate(selectedCal.timeInMillis)
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.CalendarToday,
                            contentDescription = null,
                            tint = Amber400,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Transaction Date", fontSize = 11.sp, color = TextMuted, maxLines = 1)
                            Text(dateLabel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text("Change", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }

            // Split Toggle Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Split Transaction",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Divide total amount across multiple categories or sources",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.isSplit,
                        onCheckedChange = { viewModel.toggleSplit(it) },
                        modifier = Modifier.testTag("entry_split_toggle")
                    )
                }
            }

            if (state.isSplit) {
                // Prominent "Total" field at the TOP of the split block (above Line 1)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("split_total_container")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Total",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (state.totalAmount > 0.0) {
                                Text(
                                    text = Formatters.formatMoney(state.totalAmount, isMasked = hideBalances, currencySymbol = currencySymbol),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedTextField(
                            value = state.amountText,
                            onValueChange = { viewModel.onAmountTextChange(it) },
                            label = { Text("Total to Split") },
                            placeholder = { Text("0.00") },
                            leadingIcon = {
                                Text(
                                    text = currencySymbol,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("split_total_input")
                        )
                    }
                }

                val isTotalUnset = state.totalAmount <= 0.0
                val left = state.leftToAssign
                val leftColor = when {
                    isTotalUnset -> Amber500
                    Math.abs(left) < 0.01 -> IncomeGreen
                    left > 0 -> Amber500
                    else -> ExpenseRed
                }
                val curSym = currencySymbol
                val leftText = when {
                    isTotalUnset -> "Enter a total first"
                    Math.abs(left) < 0.01 -> "All funds assigned (${curSym}0.00 left)"
                    left > 0 -> "${Formatters.formatMoney(left, isMasked = false, currencySymbol = curSym)} left to assign"
                    else -> "Over-assigned by ${Formatters.formatMoney(-left, isMasked = false, currencySymbol = curSym)}"
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = leftColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, leftColor.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("entry_left_to_assign")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = leftText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = leftColor
                        )
                        Text(
                            text = "${state.splitLines.size} of 5 lines",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val linesEnabled = state.totalAmount > 0.0

                state.splitLines.forEachIndexed { index, line ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (linesEnabled) 0.25f else 0.12f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (linesEnabled) 0.5f else 0.25f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("split_line_${index}")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Line ${index + 1}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (linesEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                if (state.splitLines.size > 2 || (state.transactionId != null && state.splitLines.size > 1)) {
                                    IconButton(
                                        onClick = { viewModel.removeSplitLine(line.id) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .size(32.dp)
                                            .testTag("split_remove_line_${index}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove line ${index + 1}",
                                            tint = ExpenseRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = line.amountText,
                                onValueChange = { newAmt ->
                                    if (linesEnabled) {
                                        viewModel.updateSplitLine(line.id, line.categoryId, line.accountId, newAmt)
                                    }
                                },
                                enabled = linesEnabled,
                                label = { Text("Line Amount") },
                                placeholder = { Text("0.00") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(if (linesEnabled) 1.0f else 0.45f)
                                    .semantics {
                                        contentDescription = "Split line ${index + 1} of ${state.splitLines.size} amount"
                                    }
                                    .testTag("split_amount_${index}")
                            )

                            CategoryDropdown(
                                selectedCategoryId = line.categoryId,
                                categories = categories,
                                onSelect = { catId ->
                                    viewModel.updateSplitLine(line.id, catId, line.accountId, line.amountText)
                                },
                                onAddNew = { showAddCategoryDialog = true }
                            )

                            AccountSelector(
                                label = if (state.type == "INCOME") "To Source" else "From Source",
                                selectedAccountId = line.accountId ?: if (state.type == "INCOME") state.toAccountId else state.fromAccountId,
                                accounts = accounts,
                                onSelect = { accId ->
                                    viewModel.updateSplitLine(line.id, line.categoryId, accId, line.amountText)
                                },
                                onAddSource = onAddSource
                            )
                        }
                    }
                }

                if (state.splitLines.size < 5) {
                    OutlinedButton(
                        onClick = { viewModel.addSplitLine() },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("split_add_line_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Allocation Line")
                    }
                }
            } else {
                // Type-adaptive Account & Category fields
                when (state.type) {
                    "EXPENSE" -> {
                        AccountSelector(
                            label = "From Source",
                            selectedAccountId = state.fromAccountId,
                            accounts = accounts,
                            onSelect = { viewModel.setFromAccount(it) },
                            onAddSource = onAddSource
                        )

                        CategoryDropdown(
                            selectedCategoryId = state.categoryId,
                            categories = categories,
                            onSelect = { viewModel.setCategory(it) },
                            onAddNew = { showAddCategoryDialog = true }
                        )
                    }

                    "INCOME" -> {
                        AccountSelector(
                            label = "To Source",
                            selectedAccountId = state.toAccountId,
                            accounts = accounts,
                            onSelect = { viewModel.setToAccount(it) },
                            onAddSource = onAddSource
                        )

                        CategoryDropdown(
                            selectedCategoryId = state.categoryId,
                            categories = categories,
                            onSelect = { viewModel.setCategory(it) },
                            onAddNew = { showAddCategoryDialog = true }
                        )
                    }

                    "TRANSFER" -> {
                        AccountSelector(
                            label = "From Source",
                            selectedAccountId = state.fromAccountId,
                            accounts = accounts,
                            onSelect = { viewModel.setFromAccount(it) },
                            onAddSource = onAddSource
                        )

                        AccountSelector(
                            label = "To Source",
                            selectedAccountId = state.toAccountId,
                            accounts = accounts,
                            onSelect = { viewModel.setToAccount(it) },
                            onAddSource = onAddSource
                        )
                    }
                }
            }

            // Description input
            OutlinedTextField(
                value = state.description,
                onValueChange = { viewModel.setDescription(it) },
                label = { Text("Description") },
                placeholder = { Text("e.g. Swiggy Gourmet Dinner, Metro Recharge") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("entry_description_field")
            )

            // Notes input
            OutlinedTextField(
                value = state.notes,
                onValueChange = { viewModel.setNotes(it) },
                label = { Text("Notes (Optional)") },
                placeholder = { Text("Additional context, bill memo, or invoice ref") },
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("entry_notes_field")
            )

            // --- Media Attachments (Photos & Voice Memos) ---
            if (!isDemoMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Camera Button: PLAIN
                        OutlinedButton(
                            onClick = {
                                if (totalPhotos >= 10) {
                                    viewModel.addStagedMedia(StagedMedia(kind = "PHOTO", file = File(""), byteSize = 0L))
                                } else {
                                    val tempFile = File(context.cacheDir, "media_staging/cam_${UUID.randomUUID()}.jpg")
                                    tempFile.parentFile?.mkdirs()
                                    tempCameraFile = tempFile
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
                                    tempCameraUri = uri
                                    takePictureLauncher.launch(uri)
                                }
                            },
                            enabled = !isRecordingVoice,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("entry_camera_btn")
                        ) {
                            Text("📷 Camera", fontSize = 12.sp, maxLines = 1)
                        }

                        // Gallery Button: PLAIN
                        OutlinedButton(
                            onClick = {
                                if (totalPhotos >= 10) {
                                    viewModel.addStagedMedia(StagedMedia(kind = "PHOTO", file = File(""), byteSize = 0L))
                                } else {
                                    pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            },
                            enabled = !isRecordingVoice,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("entry_gallery_btn")
                        ) {
                            Text("🖼 Gallery", fontSize = 12.sp, maxLines = 1)
                        }

                        // Voice Button: PLAIN
                        OutlinedButton(
                            onClick = {
                                if (totalVoice >= 3) {
                                    viewModel.addStagedMedia(StagedMedia(kind = "VOICE", file = File(""), byteSize = 0L))
                                } else if (isRecordingVoice) {
                                    stopAndSaveVoice()
                                } else {
                                    val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.RECORD_AUDIO
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    if (hasMic) {
                                        startVoiceRecording()
                                    } else {
                                        audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = if (isRecordingVoice) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer) else ButtonDefaults.outlinedButtonColors(),
                            modifier = Modifier
                                .weight(1f)
                                .semantics {
                                    stateDescription = if (isRecordingVoice) "Recording" else "Stopped"
                                }
                                .testTag("entry_voice_btn")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isRecordingVoice) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .graphicsLayer {
                                                scaleX = recPulseScale
                                                scaleY = recPulseScale
                                                alpha = recPulseAlpha
                                            }
                                            .background(ExpenseRed, CircleShape)
                                            .testTag("voice_rec_pulse_dot")
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = if (isRecordingVoice) "⏹ Stop (${MediaRepository.formatDuration(recordingSeconds * 1000L)})"
                                    else "🎤 Voice",
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Helper microcopy
                    Text(
                        text = "Capture a receipt or voice-note the description.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 2.dp).testTag("media_helper_text")
                    )

                    if (voicePermissionDenied) {
                        Text(
                            text = "Microphone permission needed for voice notes",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    if (mediaWarning != null) {
                        Text(
                            text = mediaWarning ?: "",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    if (privacyGateNotice != null && hideBalances) {
                        Text(
                            text = privacyGateNotice ?: "",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .testTag("entry_privacy_gate_notice")
                        )
                    }

                    // Draft count chips ONLY
                    if (totalPhotos > 0 || totalVoice > 0) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            if (totalPhotos > 0) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Transparent,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (hideBalances) {
                                                val msg = SingleFlightNoticeDispatcher.PRIVACY_NOTICE_MESSAGE
                                                privacyGateNotice = msg
                                                effectiveNoticeDispatcher.showPrivacyNotice()
                                            } else {
                                                privacyGateNotice = null
                                                draftViewerInitialTab = "IMAGES"
                                                showDraftViewer = true
                                            }
                                        }
                                        .testTag("draft_photo_chip")
                                ) {
                                    Text(
                                        text = "📷 images × $totalPhotos",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            if (totalVoice > 0) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Transparent,
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (hideBalances) {
                                                val msg = SingleFlightNoticeDispatcher.PRIVACY_NOTICE_MESSAGE
                                                privacyGateNotice = msg
                                                effectiveNoticeDispatcher.showPrivacyNotice()
                                            } else {
                                                privacyGateNotice = null
                                                draftViewerInitialTab = "VOICE"
                                                showDraftViewer = true
                                            }
                                        }
                                        .testTag("draft_voice_chip")
                                ) {
                                    Text(
                                        text = "🎤 voice note × $totalVoice",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Collapsible More Fields section
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleMoreFields() },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "More Fields & Custom Tags",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        Icon(
                            imageVector = if (state.isMoreFieldsExpanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                            contentDescription = null,
                            tint = TextMuted
                        )
                    }

                    AnimatedVisibility(visible = state.isMoreFieldsExpanded) {
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            customFields.forEach { field ->
                                val currentValue = state.customFieldValues[field.name] ?: ""

                                if (field.type == "DROPDOWN" && field.options.isNotBlank()) {
                                    val opts = field.options.split(",").map { it.trim() }
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(field.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            opts.forEach { opt ->
                                                val isSelected = currentValue == opt
                                                Surface(
                                                    shape = RoundedCornerShape(16.dp),
                                                    color = if (isSelected) Navy800 else MaterialTheme.colorScheme.surface,
                                                    border = androidx.compose.foundation.BorderStroke(
                                                        width = 1.dp,
                                                        color = if (isSelected) Navy800 else MaterialTheme.colorScheme.outlineVariant
                                                    ),
                                                    modifier = Modifier.clickable {
                                                        viewModel.setCustomFieldValue(field.name, if (isSelected) "" else opt)
                                                    }
                                                ) {
                                                    Text(
                                                        text = opt,
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) Color.White else TextPrimary,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    OutlinedTextField(
                                        value = currentValue,
                                        onValueChange = { viewModel.setCustomFieldValue(field.name, it) },
                                        label = { Text(field.name) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Inline Add Category Dialog
    if (showAddCategoryDialog) {
        var newCatName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("Add Category Inline") },
            text = {
                OutlinedTextField(
                    value = newCatName,
                    onValueChange = { newCatName = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCatName.isNotBlank()) {
                            viewModel.addNewCategoryInline(newCatName, "#F59E0B")
                            showAddCategoryDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Transaction?") },
            text = { Text("This will permanently remove this record from the ledger. Running balances will be automatically recalculated.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteTransaction()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ExpenseRed,
                        contentColor = Color.White
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Over-limit Save Confirmation Dialog
    if (crossedBudgets.isNotEmpty()) {
        val raw = state.amountText.replace(",", "").trim()
        val num = if (raw.contains('+') || raw.contains('-') || raw.contains('*') || raw.contains('/')) {
            com.example.domain.calculator.ExpressionEvaluator.evaluate(raw) ?: 0.0
        } else {
            raw.toDoubleOrNull() ?: 0.0
        }
        AlertDialog(
            onDismissRequest = { viewModel.dismissCrossedBudgetsWarning() },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.testTag("over_budget_confirm_dialog"),
            title = { Text("Budget Limit Exceeded", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Saving this expense will exceed the following budget(s):", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    crossedBudgets.forEach { cb ->
                        val target = if (cb.budget.targetName.isNotBlank()) cb.budget.targetName else "All Spend"
                        val displayTitle = com.example.domain.calculator.LedgerCalculator.formatBudgetDisplayTitle(cb.budget.name, target)
                        val limitStr = Formatters.formatMoney(cb.limit, isMasked = hideBalances, currencySymbol = currencySymbol)
                        val newTotalStr = Formatters.formatMoney(cb.spent + num, isMasked = hideBalances, currencySymbol = currencySymbol)
                        Text(
                            "• $displayTitle: Limit $limitStr, New Total $newTotalStr",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = ExpenseRed
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Do you want to proceed?", fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissCrossedBudgetsWarning()
                        viewModel.saveTransaction(forceSave = true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed),
                    modifier = Modifier.testTag("proceed_over_budget_button")
                ) {
                    Text("Proceed", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { viewModel.dismissCrossedBudgetsWarning() },
                    modifier = Modifier.testTag("cancel_over_budget_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDraftViewer) {
        val draftItems = remember(stagedMedia, existingAttachments) {
            val fromStaged = stagedMedia.map {
                com.example.ui.media.MediaViewerItem(
                    id = it.id,
                    kind = it.kind,
                    file = it.file,
                    durationMs = it.durationMs
                )
            }
            val fromExisting = existingAttachments.map { att ->
                val vaultFile = File(effectiveMediaRepo.getVaultDirForTxn(att.txnId), att.fileName)
                com.example.ui.media.MediaViewerItem(
                    id = att.id.toString(),
                    kind = att.kind,
                    file = vaultFile,
                    rawAttachment = att
                )
            }
            fromExisting + fromStaged
        }

        com.example.ui.media.MediaViewerSheet(
            draftItems = draftItems,
            initialTab = draftViewerInitialTab,
            mediaRepository = effectiveMediaRepo,
            hideBalances = hideBalances,
            autoPlayFirstVoice = false,
            noticeDispatcher = effectiveNoticeDispatcher,
            onDismiss = { showDraftViewer = false },
            onDeleteDraftItem = { item ->
                if (item.rawAttachment != null) {
                    viewModel.removeExistingAttachment(item.rawAttachment)
                } else {
                    viewModel.removeStagedMediaById(item.id)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSelector(
    label: String,
    selectedAccountId: Long?,
    accounts: List<Account>,
    onSelect: (Long?) -> Unit,
    onAddSource: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedAcc = accounts.firstOrNull { it.id == selectedAccountId }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedAcc?.name ?: "",
                onValueChange = {},
                readOnly = true,
                placeholder = { Text("Choose Source") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                accounts.forEach { acc ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = acc.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                val badgeLabel = when (acc.accountType) {
                                    "CREDIT_CARD" -> "Card"
                                    "CASH" -> "Cash"
                                    else -> "Bank"
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (acc.accountType == "CREDIT_CARD") ExpenseRed.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = badgeLabel,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (acc.accountType == "CREDIT_CARD") ExpenseRed else MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSelect(acc.id)
                            expanded = false
                        },
                        trailingIcon = {
                            if (acc.id == selectedAccountId) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
                }
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text("+ Add new source", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    },
                    onClick = {
                        expanded = false
                        onAddSource()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    selectedCategoryId: Long?,
    categories: List<Category>,
    onSelect: (Long?) -> Unit,
    onAddNew: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedCat = categories.firstOrNull { it.id == selectedCategoryId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedCat?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
            placeholder = { Text("Select Category") },
            leadingIcon = selectedCat?.let {
                {
                    CategoryEmojiBadge(
                        name = it.name,
                        iconText = it.iconText,
                        color = parseColorHex(it.color),
                        size = 22.dp,
                        fontSize = 12.sp
                    )
                }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            supportingText = if (selectedCat == null) {
                {
                    Text(
                        text = "Optional — uncategorized money won't show in your charts.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("category_helper_text")
                    )
                }
            } else null,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            categories.forEach { cat ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CategoryEmojiBadge(
                                name = cat.name,
                                iconText = cat.iconText,
                                color = parseColorHex(cat.color),
                                size = 20.dp,
                                fontSize = 12.sp
                            )
                            Text(
                                text = cat.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    },
                    onClick = {
                        onSelect(cat.id)
                        expanded = false
                    },
                    trailingIcon = {
                        if (cat.id == selectedCategoryId) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
            }
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text("+ Add new category", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                },
                onClick = {
                    expanded = false
                    onAddNew()
                }
            )
        }
    }
}