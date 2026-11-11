package com.example.ui.ledger

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import com.example.ui.common.AppIcons
import com.example.ui.common.CalculatorBottomSheet
import com.example.ui.common.CalculatorMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.domain.model.EnrichedTransaction
import com.example.domain.util.Formatters
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.ui.common.AppTopHeader
import com.example.ui.common.HeaderIconButton
import com.example.ui.common.CategoryEmojiBadge
import com.example.ui.common.FilterChipsRow
import com.example.ui.common.getCategoryGlyphString
import com.example.ui.common.getCategoryIcon
import com.example.ui.common.liquidGlass
import com.example.ui.common.liquidGlassAtmosphere
import com.example.ui.common.parseColorHex
import com.example.data.repository.MediaRepository
import com.example.data.AppDatabase
import com.example.ui.common.SingleFlightNoticeDispatcher
import com.example.ui.common.SingleFlightSnackbarHost
import com.example.ui.media.MediaViewerSheet
import com.example.ui.theme.Amber400
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.Navy800
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TransferGray

object UncategorizedNudgeManager {
    private var pendingTxId: Long? = null
    private var hasPendingNudge: Boolean = false
    var lastNudgeEmitted: Boolean = false

    fun triggerNudge(txId: Long? = null) {
        pendingTxId = txId
        hasPendingNudge = true
        lastNudgeEmitted = true
    }

    fun consumePendingNudge(): Long? {
        if (!hasPendingNudge) return null
        hasPendingNudge = false
        val id = pendingTxId
        pendingTxId = null
        return id ?: -1L
    }

    fun reset() {
        pendingTxId = null
        hasPendingNudge = false
        lastNudgeEmitted = false
    }
}

@Composable
fun LedgerScreen(
    viewModel: LedgerViewModel,
    onAddClick: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    mediaRepository: MediaRepository? = null,
    noticeDispatcher: SingleFlightNoticeDispatcher? = null
) {
    val state by viewModel.uiState.collectAsState()
    val liveFilter by viewModel.filter.collectAsState()
    val swipedRowId by viewModel.swipedRowId.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val ambientDispatcher = com.example.ui.common.LocalNoticeDispatcher.current
    val fallbackDispatcher = com.example.ui.common.rememberSingleFlightNoticeDispatcher()
    val effectiveNoticeDispatcher = noticeDispatcher ?: ambientDispatcher ?: fallbackDispatcher
    val context = androidx.compose.ui.platform.LocalContext.current
    val appSettingsRepo = remember(context) { com.example.data.datastore.AppSettingsRepository(context) }
    val appSettings by appSettingsRepo.settingsFlow.collectAsState(initial = com.example.data.datastore.AppSettings())
    val currencySymbol by appSettingsRepo.currencySymbol.collectAsState()
    val mediaRepo = remember(context, mediaRepository) {
        mediaRepository ?: MediaRepository(context, AppDatabase.getDatabase(context))
    }
    viewModel.attachMediaRepository(mediaRepo)
    val attachmentsByTxnId = state.attachmentsByTxnId
    var viewerTxnId by remember { mutableStateOf<Long?>(null) }
    var viewerInitialTab by remember { mutableStateOf("IMAGES") }
    var showCalculator by remember { mutableStateOf(false) }
    var showHelpSheet by remember { mutableStateOf(false) }
    var expandedGroupIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    val lazyListState = rememberLazyListState()

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (lazyListState.isScrollInProgress && swipedRowId != null) {
            viewModel.clearSwipedRow()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.undoSnackbarEvent.collect { message ->
            effectiveNoticeDispatcher.showNotice(
                key = "UNDO_DELETE",
                message = message,
                actionLabel = "UNDO",
                duration = SnackbarDuration.Short,
                onActionPerformed = {
                    viewModel.undoDelete()
                }
            )
        }
    }

    LaunchedEffect(state.dayGroups) {
        val pending = UncategorizedNudgeManager.consumePendingNudge()
        if (pending != null) {
            effectiveNoticeDispatcher.showNotice(
                key = "UNCATEGORIZED_NUDGE",
                message = "Saved without a category",
                actionLabel = "Add category",
                duration = SnackbarDuration.Short,
                onActionPerformed = {
                    val targetId = if (pending > 0L) pending else {
                        state.dayGroups
                            .flatMap { it.transactions }
                            .firstOrNull { it.category == null && it.type != "TRANSFER" }?.id
                    }
                    targetId?.let { onTransactionClick(it) }
                }
            )
        }
    }

    Scaffold(
        snackbarHost = { if (ambientDispatcher == null) SingleFlightSnackbarHost(effectiveNoticeDispatcher) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier
                    .padding(bottom = 16.dp, end = 16.dp)
                    .testTag("fab_add_transaction")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Transaction",
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding())
                .liquidGlassAtmosphere()
                .pointerInput(swipedRowId) {
                    if (swipedRowId != null) {
                        detectTapGestures {
                            viewModel.clearSwipedRow()
                        }
                    }
                }
        ) {
            AppTopHeader(
                title = "SilentSums",
                subtitle = "Expenses Ledger",
                actions = {
                    if (appSettings.helpVisible) {
                        HeaderIconButton(
                            onClick = { showHelpSheet = true },
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
                    var lastKnownTxCount by rememberSaveable { mutableStateOf<Int?>(null) }
                    if (!state.isLoading) {
                        lastKnownTxCount = state.totalCount
                    }
                    val effectiveCount = if (state.isLoading && lastKnownTxCount != null) lastKnownTxCount!! else state.totalCount
                    val isExportGreyed = effectiveCount == 0

                    HeaderIconButton(
                        icon = AppIcons.FileDownload,
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val result = viewModel.exportFilteredCsv(context)
                                    if (result == "No records to export") {
                                        effectiveNoticeDispatcher.showNotice(
                                            key = "EXPORT_EMPTY",
                                            message = "No records to export"
                                        )
                                    } else if (result.startsWith("Export paused")) {
                                        effectiveNoticeDispatcher.showNotice(
                                            key = "EXPORT_PAUSED",
                                            message = result
                                        )
                                    } else {
                                        effectiveNoticeDispatcher.showNotice(
                                            key = "EXPORT_SUCCESS",
                                            message = "Exported to $result"
                                        )
                                    }
                                } catch (e: Exception) {
                                    effectiveNoticeDispatcher.showNotice(
                                        key = "EXPORT_FAILED",
                                        message = "Export failed: ${e.localizedMessage}"
                                    )
                                }
                            }
                        },
                        testTag = "ledger_export_filtered_btn",
                        contentDescription = "Export Filtered Transactions",
                        isGreyed = isExportGreyed
                    )
                }
            )

            // Search Bar & Calculator scratchpad (next to search/filter)
            val searchTextStyle = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                lineHeight = 16.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = liveFilter.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = {
                        Text(
                            "Search accounts, amounts...",
                            style = searchTextStyle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (liveFilter.searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.updateSearchQuery("") },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    maxLines = 1,
                    textStyle = searchTextStyle,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("ledger_search_field")
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .size(48.dp)
                        .liquidGlass(12.dp)
                        .clickable(role = Role.Button) { showCalculator = true }
                        .testTag("ledger_calculator_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = AppIcons.Calculate,
                            contentDescription = "Open Calculator Scratchpad",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            if (showCalculator) {
                CalculatorBottomSheet(
                    initialExpression = "",
                    mode = CalculatorMode.SCRATCHPAD,
                    currencySymbol = currencySymbol,
                    onDismiss = { showCalculator = false }
                )
            }

            val filterRowScrollState = rememberLazyListState()
            // Filter Chips Row
            FilterChipsRow(
                filterState = liveFilter,
                categories = state.activeCategories,
                accounts = state.activeAccounts,
                onFilterChange = { viewModel.updateFilter(it) },
                availableMonths = state.availableMonths,
                lazyListState = filterRowScrollState,
                onClearAll = { viewModel.clearAllSlicers() }
            )

            // Content: Grouped by date
            if (state.dayGroups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.ReceiptLong,
                            contentDescription = null,
                            tint = TextMuted.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No matching transactions found",
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            fontSize = 15.sp
                        )
                        OutlinedButton(
                            onClick = { viewModel.clearFilters() },
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text("Clear all filters")
                        }
                    }
                }
            } else {
                val dayGroupsWithRowItems = remember(state.dayGroups) {
                    state.dayGroups.map { it to buildLedgerRowItems(it.transactions) }
                }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val density = LocalDensity.current
                    val maxRevealDp = minOf((maxWidth - 32.dp) * 0.30f, 120.dp)
                    val maxRevealPx = remember(density, maxRevealDp) { with(density) { maxRevealDp.toPx() } }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(4.dp)) }

                        dayGroupsWithRowItems.forEach { (dayGroup, rowItems) ->
                            // Day Header
                            item(key = "header_${dayGroup.dateTimestamp}") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = dayGroup.dayLabel,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
                                            .semantics { heading() }
                                    )
                                    val dayTotalStr = Formatters.formatMoney(
                                        amount = dayGroup.dayTotal,
                                        isMasked = state.hideBalances,
                                        currencySymbol = currencySymbol,
                                        showPlusSign = true
                                    )
                                    Text(
                                        text = dayTotalStr,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        color = when {
                                            dayGroup.dayTotal > 0 -> IncomeGreen
                                            dayGroup.dayTotal < 0 -> ExpenseRed
                                            else -> TransferGray
                                        },
                                        modifier = Modifier.semantics {
                                            if (state.hideBalances) {
                                                contentDescription = "hidden"
                                            }
                                        }
                                    )
                                }
                            }

                            // Transaction items in this day
                            items(rowItems, key = { it.key }) { rowItem ->
                                val tx = rowItem.representativeTx
                                val isSwipedOpen = swipedRowId == tx.id

                                val dismissState = rememberSwipeToDismissBoxState(
                                    positionalThreshold = { totalDistance -> totalDistance * 0.25f },
                                    confirmValueChange = { value ->
                                        if (value == SwipeToDismissBoxValue.EndToStart) {
                                            viewModel.setSwipedRowId(tx.id)
                                            true
                                        } else if (value == SwipeToDismissBoxValue.Settled) {
                                            if (swipedRowId == tx.id) {
                                                viewModel.clearSwipedRow()
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                )

                                LaunchedEffect(isSwipedOpen) {
                                    if (isSwipedOpen) {
                                        if (dismissState.currentValue != SwipeToDismissBoxValue.EndToStart) {
                                            dismissState.dismiss(SwipeToDismissBoxValue.EndToStart)
                                        }
                                    } else {
                                        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                                            dismissState.reset()
                                        }
                                    }
                                }

                                SwipeToDismissBox(
                                    state = dismissState,
                                    enableDismissFromStartToEnd = false,
                                    backgroundContent = {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.CenterEnd
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .width(maxRevealDp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(ExpenseRed)
                                                    .testTag("ledger_delete_band")
                                                    .semantics {
                                                        contentDescription = "Delete"
                                                    }
                                                    .clickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = null
                                                    ) {
                                                        viewModel.clearSwipedRow()
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSwipedOpen || dismissState.targetValue == SwipeToDismissBoxValue.EndToStart || dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                                                    IconButton(
                                                        onClick = {
                                                            viewModel.deleteTransaction(tx)
                                                        },
                                                        modifier = Modifier
                                                            .size(48.dp)
                                                            .testTag("ledger_delete_bin")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Delete transaction",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    val rowOffsetModifier = Modifier.offset {
                                        val currentOffset = runCatching { dismissState.requireOffset() }.getOrDefault(0f)
                                        val clampedTranslation = currentOffset.coerceIn(-maxRevealPx, 0f)
                                        val counterOffset = clampedTranslation - currentOffset
                                        IntOffset(counterOffset.roundToInt(), 0)
                                    }

                                    if (rowItem is LedgerRowItem.SplitGroup) {
                                        val isExpanded = rowItem.groupId in expandedGroupIds
                                        val rowAttachments = attachmentsByTxnId[rowItem.representativeTx.id].orEmpty()
                                        SplitGroupCardRow(
                                            splitGroup = rowItem,
                                            isExpanded = isExpanded,
                                            onToggleExpand = {
                                                if (swipedRowId != null) {
                                                    viewModel.clearSwipedRow()
                                                } else {
                                                    expandedGroupIds = if (isExpanded) {
                                                        expandedGroupIds - rowItem.groupId
                                                    } else {
                                                        expandedGroupIds + rowItem.groupId
                                                    }
                                                }
                                            },
                                            onEditGroup = {
                                                if (swipedRowId != null) {
                                                    viewModel.clearSwipedRow()
                                                } else {
                                                    onTransactionClick(rowItem.representativeTx.id)
                                                }
                                            },
                                            onDeleteGroup = {
                                                viewModel.deleteTransaction(rowItem.representativeTx)
                                            },
                                            modifier = rowOffsetModifier,
                                            hideBalances = state.hideBalances,
                                            currencySymbol = currencySymbol,
                                            mediaRepository = mediaRepo,
                                            attachments = rowAttachments,
                                            onOpenMediaViewer = { id, tab ->
                                                if (state.hideBalances) {
                                                    effectiveNoticeDispatcher.showPrivacyNotice()
                                                } else {
                                                    viewerTxnId = id
                                                    viewerInitialTab = tab
                                                }
                                            },
                                            onPrivacyBlocked = {
                                                effectiveNoticeDispatcher.showPrivacyNotice()
                                            }
                                        )
                                    } else if (rowItem is LedgerRowItem.Single) {
                                        val rowAttachments = attachmentsByTxnId[rowItem.transaction.id].orEmpty()
                                        TransactionCardRow(
                                            transaction = rowItem.transaction,
                                            modifier = rowOffsetModifier,
                                            onClick = {
                                                if (swipedRowId != null) {
                                                    viewModel.clearSwipedRow()
                                                } else {
                                                    onTransactionClick(tx.id)
                                                }
                                            },
                                            onDelete = {
                                                viewModel.deleteTransaction(tx)
                                            },
                                            hideBalances = state.hideBalances,
                                            currencySymbol = currencySymbol,
                                            mediaRepository = mediaRepo,
                                            attachments = rowAttachments,
                                            onOpenMediaViewer = { id, tab ->
                                                if (state.hideBalances) {
                                                    effectiveNoticeDispatcher.showPrivacyNotice()
                                                } else {
                                                    viewerTxnId = id
                                                    viewerInitialTab = tab
                                                }
                                            },
                                            onPrivacyBlocked = {
                                                effectiveNoticeDispatcher.showPrivacyNotice()
                                            }
                                        )
                                    }
                                }
                            }
                        }

                    item { Spacer(modifier = Modifier.height(88.dp)) }
                }
            }
        }
    }
    }

    if (showHelpSheet) {
        com.example.ui.dashboard.HelpGesturesSheet(
            section = com.example.ui.dashboard.HelpSection.EXPENSES,
            onDismiss = { showHelpSheet = false }
        )
    }

    if (viewerTxnId != null && !state.hideBalances) {
        val currentTxnId = viewerTxnId!!
        val attachmentsForViewer = attachmentsByTxnId[currentTxnId].orEmpty()
        MediaViewerSheet(
            txnId = currentTxnId,
            attachments = attachmentsForViewer,
            initialTab = viewerInitialTab,
            hideBalances = state.hideBalances,
            autoPlayFirstVoice = false,
            mediaRepository = mediaRepo,
            noticeDispatcher = effectiveNoticeDispatcher,
            onDismiss = {
                MediaRepository.activePlayingTxnId.value = null
                viewerTxnId = null
            },
            onDeleteAttachment = { att ->
                coroutineScope.launch {
                    mediaRepo.deleteAttachment(att)
                }
            }
        )
    }
}

@Composable
fun TransactionCardRow(
    transaction: EnrichedTransaction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    hideBalances: Boolean = false,
    currencySymbol: String = Formatters.currentCurrencySymbol(),
    mediaRepository: MediaRepository? = null,
    attachments: List<com.example.data.model.MediaAttachment> = emptyList(),
    onOpenMediaViewer: ((Long, String) -> Unit)? = null,
    onPrivacyBlocked: (() -> Unit)? = null
) {
    var showInlinePrivacyNotice by remember(hideBalances) { mutableStateOf(false) }
    val titleLineCountHolder = remember(transaction.id) { IntArray(1) { 1 } }

    val primaryColor = MaterialTheme.colorScheme.primary
    val catColor = remember(transaction.category, transaction.type, primaryColor) {
        if (transaction.category != null) {
            parseColorHex(transaction.category.color)
        } else if (transaction.type == "TRANSFER") {
            TransferGray
        } else {
            primaryColor
        }
    }

    val amountColor = remember(transaction.type) {
        when (transaction.type.uppercase()) {
            "INCOME" -> IncomeGreen
            "EXPENSE" -> ExpenseRed
            else -> TransferGray
        }
    }

    val (amountText, balStr) = key(currencySymbol) {
        val amountText = remember(transaction.amount, transaction.type, hideBalances) {
            if (hideBalances) {
                Formatters.MASK_VALUE
            } else {
                val prefix = when (transaction.type.uppercase()) {
                    "INCOME" -> "+"
                    "EXPENSE" -> "-"
                    else -> ""
                }
                val amountStr = Formatters.formatIndianCurrency(
                    amount = transaction.amount,
                    currencySymbol = currencySymbol,
                    showPlusSign = false
                )
                "$prefix$amountStr"
            }
        }

        val balStr = remember(transaction.runningBalance, hideBalances) {
            Formatters.formatMoney(
                amount = transaction.runningBalance,
                isMasked = hideBalances,
                currencySymbol = currencySymbol,
                includeDecimals = true
            )
        }
        amountText to balStr
    }

    val typeLabel = when (transaction.type.uppercase()) {
        "INCOME" -> "Income"
        "EXPENSE" -> "Expense"
        "TRANSFER" -> "Transfer"
        else -> transaction.type
    }
    val catLabel = transaction.category?.name ?: "Uncategorized"
    val descLabel = transaction.description.ifBlank { typeLabel }
    val rowDescription = if (hideBalances) {
        "$descLabel, $catLabel, $typeLabel, amount hidden, balance hidden"
    } else {
        "$descLabel, $catLabel, $typeLabel, amount $amountText, balance $balStr"
    }

    val rowCustomActions = remember(transaction.id, onClick, onDelete) {
        listOfNotNull(
            onDelete?.let { del ->
                CustomAccessibilityAction("Delete transaction") {
                    del()
                    true
                }
            },
            CustomAccessibilityAction("Edit transaction") {
                onClick()
                true
            }
        )
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = rowDescription
                if (rowCustomActions.isNotEmpty()) {
                    customActions = rowCustomActions
                }
            }
            .clickable(onClick = onClick)
            .testTag("transaction_row_${transaction.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val metaContent: @Composable (Modifier) -> Unit = { metaMod ->
                Row(
                    modifier = metaMod,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (transaction.category != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = catColor.copy(alpha = 0.12f),
                            modifier = Modifier.testTag("tx_category_pill_${transaction.id}")
                        ) {
                            Text(
                                text = transaction.category.name,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = catColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (transaction.type != "TRANSFER") {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(onClick = onClick)
                                .testTag("add_category_chip_${transaction.id}")
                                .testTag("tx_category_pill_${transaction.id}")
                        ) {
                            Text(
                                text = "+ Add category",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    val flowText = when (transaction.type.uppercase()) {
                        "TRANSFER" -> {
                            val from = transaction.fromAccount?.name ?: "Source"
                            val to = transaction.toAccount?.name ?: "Dest"
                            "$from → $to"
                        }
                        "EXPENSE" -> transaction.fromAccount?.name ?: ""
                        "INCOME" -> transaction.toAccount?.name ?: ""
                        else -> ""
                    }

                    if (flowText.isNotBlank()) {
                        Text(
                            text = flowText,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }

            Layout(
                modifier = Modifier.fillMaxWidth(),
                content = {
                    Box(modifier = Modifier.testTag("tx_icon_${transaction.id}")) {
                        if (transaction.category != null) {
                            CategoryEmojiBadge(
                                name = transaction.category.name,
                                iconText = transaction.category.iconText,
                                color = catColor,
                                size = 24.dp,
                                fontSize = 13.sp
                            )
                        } else if (transaction.type == "TRANSFER") {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(TransferGray.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AppIcons.Sync,
                                    contentDescription = "Transfer",
                                    tint = TransferGray,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        } else {
                            CategoryEmojiBadge(
                                name = transaction.description.ifBlank { transaction.type },
                                iconText = null,
                                color = MaterialTheme.colorScheme.primary,
                                size = 24.dp,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Text(
                        text = transaction.description.ifBlank { transaction.type },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { textLayoutResult ->
                            titleLineCountHolder[0] = textLayoutResult.lineCount
                        },
                        modifier = Modifier.testTag("tx_title_${transaction.id}")
                    )

                    Text(
                        text = amountText,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        maxLines = 1,
                        softWrap = false,
                        color = amountColor,
                        modifier = Modifier
                            .testTag("tx_amount_${transaction.id}")
                            .semantics {
                                if (hideBalances) {
                                    contentDescription = "hidden"
                                }
                            }
                    )

                    Text(
                        text = "Bal: $balStr",
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                        color = TextMuted,
                        modifier = Modifier
                            .testTag("tx_bal_${transaction.id}")
                            .semantics {
                                if (hideBalances) {
                                    contentDescription = "hidden"
                                }
                            }
                    )

                    metaContent(Modifier.testTag("tx_meta_${transaction.id}"))
                }
            ) { measurables, constraints ->
                val gap8Px = 8.dp.roundToPx()
                val gap6Px = 6.dp.roundToPx()
                val gap2Px = 2.dp.roundToPx()
                val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)

                val iconPlaceable = measurables[0].measure(looseConstraints)
                val amountPlaceable = measurables[2].measure(looseConstraints)
                val balPlaceable = measurables[3].measure(looseConstraints)

                val rightReserveWidth = maxOf(amountPlaceable.width, balPlaceable.width)
                val titleMaxWidth = (constraints.maxWidth - iconPlaceable.width - gap8Px - gap8Px - rightReserveWidth).coerceAtLeast(0)
                val titlePlaceable = measurables[1].measure(
                    Constraints(
                        minWidth = titleMaxWidth,
                        maxWidth = titleMaxWidth,
                        minHeight = 0,
                        maxHeight = constraints.maxHeight
                    )
                )

                val isSingleLine = titleLineCountHolder[0] <= 1
                if (isSingleLine) {
                    val metaMaxWidth = (constraints.maxWidth - balPlaceable.width - gap8Px).coerceAtLeast(0)
                    val metaPlaceable = measurables[4].measure(
                        Constraints(
                            minWidth = 0,
                            maxWidth = metaMaxWidth,
                            minHeight = 0,
                            maxHeight = constraints.maxHeight
                        )
                    )
                    val row1Height = maxOf(iconPlaceable.height, titlePlaceable.height, amountPlaceable.height)
                    val row2Height = maxOf(metaPlaceable.height, balPlaceable.height)
                    val totalHeight = row1Height + gap6Px + row2Height
                    layout(constraints.maxWidth, totalHeight) {
                        iconPlaceable.placeRelative(0, (row1Height - iconPlaceable.height) / 2)
                        titlePlaceable.placeRelative(iconPlaceable.width + gap8Px, (row1Height - titlePlaceable.height) / 2)
                        amountPlaceable.placeRelative(constraints.maxWidth - amountPlaceable.width, (row1Height - amountPlaceable.height) / 2)
                        val row2Y = row1Height + gap6Px
                        metaPlaceable.placeRelative(0, row2Y + (row2Height - metaPlaceable.height) / 2)
                        balPlaceable.placeRelative(constraints.maxWidth - balPlaceable.width, row2Y + (row2Height - balPlaceable.height) / 2)
                    }
                } else {
                    val metaPlaceable = measurables[4].measure(
                        Constraints(
                            minWidth = constraints.maxWidth,
                            maxWidth = constraints.maxWidth,
                            minHeight = 0,
                            maxHeight = constraints.maxHeight
                        )
                    )
                    val rightColHeight = amountPlaceable.height + gap2Px + balPlaceable.height
                    val row1Height = maxOf(iconPlaceable.height, titlePlaceable.height, rightColHeight)
                    val row2Y = row1Height + gap6Px
                    val totalHeight = row2Y + metaPlaceable.height
                    layout(constraints.maxWidth, totalHeight) {
                        iconPlaceable.placeRelative(0, 0)
                        titlePlaceable.placeRelative(iconPlaceable.width + gap8Px, 0)
                        amountPlaceable.placeRelative(constraints.maxWidth - amountPlaceable.width, 0)
                        balPlaceable.placeRelative(constraints.maxWidth - balPlaceable.width, amountPlaceable.height + gap2Px)
                        metaPlaceable.placeRelative(0, row2Y)
                    }
                }
            }

            // Row 3: Notes / Description (full-width maxLines=1 ellipsis)
            if (transaction.notes.isNotBlank()) {
                Text(
                    text = transaction.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tx_notes_${transaction.id}")
                )
            }

            // Row 4: Full-Left-Rail Media Chips + Privacy Gate + Voice Pause Symbol
            val photoCount = attachments.count { it.kind == "PHOTO" }
            val voiceCount = attachments.count { it.kind == "VOICE" }

            if (photoCount > 0 || voiceCount > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                ) {
                    if (photoCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (hideBalances) {
                                        showInlinePrivacyNotice = true
                                        onPrivacyBlocked?.invoke()
                                    } else {
                                        onOpenMediaViewer?.invoke(transaction.id, "IMAGES")
                                    }
                                }
                                .testTag("photo_chip_${transaction.id}")
                        ) {
                            Text(
                                text = "📷 images × $photoCount",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (voiceCount > 0) {
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
                                        showInlinePrivacyNotice = true
                                        onPrivacyBlocked?.invoke()
                                    } else {
                                        onOpenMediaViewer?.invoke(transaction.id, "VOICE")
                                    }
                                }
                                .testTag("voice_chip_${transaction.id}")
                        ) {
                            Text(
                                text = "🎤 voice note × $voiceCount",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                if (showInlinePrivacyNotice && hideBalances) {
                    Text(
                        text = "Turn off Privacy Mode in Settings to view photos and voice notes.",
                        fontSize = 11.sp,
                        color = ExpenseRed,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                            .testTag("privacy_media_gate_notice_${transaction.id}")
                    )
                }
            }
        }
    }
}

sealed class LedgerRowItem {
    abstract val key: String
    abstract val representativeTx: EnrichedTransaction

    data class Single(val transaction: EnrichedTransaction) : LedgerRowItem() {
        override val key: String get() = "tx_${transaction.id}"
        override val representativeTx: EnrichedTransaction get() = transaction
    }

    data class SplitGroup(
        val groupId: String,
        val lines: List<EnrichedTransaction>
    ) : LedgerRowItem() {
        override val key: String get() = "group_$groupId"
        override val representativeTx: EnrichedTransaction get() = lines.first()
        val totalAmount: Double get() = lines.sumOf { it.amount }
        val date: Long get() = lines.first().date
        val type: String get() = lines.first().type
        val description: String get() = lines.first().description.ifBlank { "Split · ${lines.size} lines" }
    }
}

fun buildLedgerRowItems(transactions: List<EnrichedTransaction>): List<LedgerRowItem> {
    val items = mutableListOf<LedgerRowItem>()
    val seenGroupIds = mutableSetOf<String>()
    for (tx in transactions) {
        val gid = tx.entity.groupId
        if (gid == null) {
            items.add(LedgerRowItem.Single(tx))
        } else if (gid !in seenGroupIds) {
            seenGroupIds.add(gid)
            val groupLines = transactions.filter { it.entity.groupId == gid }
            if (groupLines.size > 1) {
                items.add(LedgerRowItem.SplitGroup(gid, groupLines))
            } else {
                items.add(LedgerRowItem.Single(tx))
            }
        }
    }
    return items
}

@Composable
fun SplitGroupCardRow(
    splitGroup: LedgerRowItem.SplitGroup,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onEditGroup: () -> Unit,
    modifier: Modifier = Modifier,
    onDeleteGroup: (() -> Unit)? = null,
    hideBalances: Boolean = false,
    currencySymbol: String = Formatters.currentCurrencySymbol(),
    mediaRepository: MediaRepository? = null,
    attachments: List<com.example.data.model.MediaAttachment> = emptyList(),
    onOpenMediaViewer: ((Long, String) -> Unit)? = null,
    onPrivacyBlocked: (() -> Unit)? = null
) {
    val primaryTx = splitGroup.representativeTx
    var showInlinePrivacyNotice by remember(hideBalances) { mutableStateOf(false) }

    val expandActionLabel = if (isExpanded) "Collapse" else "Expand"
    val deleteActionLabel = "Delete all ${splitGroup.lines.size} split lines"
    val groupTypeLabel = when (splitGroup.type.uppercase()) {
        "INCOME" -> "Income"
        "EXPENSE" -> "Expense"
        else -> splitGroup.type
    }
    val groupAmountFormatted = Formatters.formatMoney(splitGroup.totalAmount, isMasked = hideBalances, currencySymbol = currencySymbol)
    val groupSummary = if (hideBalances) {
        "${splitGroup.description}, Split group, ${splitGroup.lines.size} lines, $groupTypeLabel, amount hidden"
    } else {
        "${splitGroup.description}, Split group, ${splitGroup.lines.size} lines, $groupTypeLabel, amount $groupAmountFormatted"
    }

    val groupCustomActions = remember(splitGroup.groupId, isExpanded, splitGroup.lines.size, onToggleExpand, onEditGroup, onDeleteGroup) {
        listOfNotNull(
            CustomAccessibilityAction(expandActionLabel) {
                onToggleExpand()
                true
            },
            CustomAccessibilityAction("Edit split") {
                onEditGroup()
                true
            },
            onDeleteGroup?.let { del ->
                CustomAccessibilityAction(deleteActionLabel) {
                    del()
                    true
                }
            }
        )
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = groupSummary
                if (groupCustomActions.isNotEmpty()) {
                    customActions = groupCustomActions
                }
            }
            .clickable(onClick = onToggleExpand)
            .testTag("split_group_card_${splitGroup.groupId}")
            .testTag("transaction_row_${splitGroup.representativeTx.id}")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f).padding(end = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .testTag("tx_icon_${primaryTx.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.TableChart,
                            contentDescription = "Split",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Text(
                        text = splitGroup.description,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tx_title_${primaryTx.id}")
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val amountColor = when (splitGroup.type.uppercase()) {
                        "INCOME" -> IncomeGreen
                        "EXPENSE" -> ExpenseRed
                        else -> TransferGray
                    }
                    val prefix = when (splitGroup.type.uppercase()) {
                        "INCOME" -> "+"
                        "EXPENSE" -> "-"
                        else -> ""
                    }
                    val amountText = if (hideBalances) {
                        Formatters.MASK_VALUE
                    } else {
                        val amountStr = Formatters.formatIndianCurrency(
                            amount = splitGroup.totalAmount,
                            currencySymbol = currencySymbol,
                            showPlusSign = false
                        )
                        "$prefix$amountStr"
                    }

                    Text(
                        text = amountText,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        maxLines = 1,
                        softWrap = false,
                        color = amountColor,
                        modifier = Modifier.testTag("tx_amount_${primaryTx.id}")
                    )

                    IconButton(
                        onClick = onEditGroup,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("split_group_edit_${splitGroup.groupId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit split",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Full-Left-Rail Split Badge + Expand Hint Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tx_meta_${primaryTx.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.testTag("split_badge_${splitGroup.groupId}")
                ) {
                    Text(
                        text = "${splitGroup.lines.size}-part split",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = if (isExpanded) "Tap to collapse" else "Tap to expand",
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }

            if (primaryTx.notes.isNotBlank()) {
                Text(
                    text = primaryTx.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tx_notes_${primaryTx.id}")
                )
            }

            val photoCount = attachments.count { it.kind == "PHOTO" }
            val voiceCount = attachments.count { it.kind == "VOICE" }

            if (photoCount > 0 || voiceCount > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                ) {
                    if (photoCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (hideBalances) {
                                        showInlinePrivacyNotice = true
                                        onPrivacyBlocked?.invoke()
                                    } else {
                                        onOpenMediaViewer?.invoke(primaryTx.id, "IMAGES")
                                    }
                                }
                                .testTag("photo_chip_${primaryTx.id}")
                        ) {
                            Text(
                                text = "📷 images × $photoCount",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (voiceCount > 0) {
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
                                        showInlinePrivacyNotice = true
                                        onPrivacyBlocked?.invoke()
                                    } else {
                                        onOpenMediaViewer?.invoke(primaryTx.id, "VOICE")
                                    }
                                }
                                .testTag("voice_chip_${primaryTx.id}")
                        ) {
                            Text(
                                text = "🎤 voice note × $voiceCount",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                if (showInlinePrivacyNotice && hideBalances) {
                    Text(
                        text = "Turn off Privacy Mode in Settings to view photos and voice notes.",
                        fontSize = 11.sp,
                        color = ExpenseRed,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                            .testTag("privacy_media_gate_notice_${primaryTx.id}")
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, start = 16.dp, end = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val seenSplitGlyphs = mutableSetOf<String>()
                    splitGroup.lines.forEach { line ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("split_line_item_${line.id}")
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            ) {
                                val catName = line.category?.name ?: "Uncategorized"
                                val catGlyph = line.category?.let { getCategoryGlyphString(it.name, it.iconText) }.orEmpty()
                                val showGlyph = catGlyph.isNotEmpty() && seenSplitGlyphs.add(catGlyph)
                                val pillText = if (showGlyph) "$catGlyph $catName" else catName
                                val catColor = line.category?.let { parseColorHex(it.color) } ?: MaterialTheme.colorScheme.outline
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = catColor.copy(alpha = 0.12f),
                                    modifier = Modifier.testTag("split_line_pill_${line.id}")
                                ) {
                                    Text(
                                        text = pillText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = catColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                val accName = line.fromAccount?.name ?: line.toAccount?.name ?: ""
                                if (accName.isNotBlank()) {
                                    Text(
                                        text = accName,
                                        fontSize = 11.sp,
                                        color = TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            val lineAmtStr = Formatters.formatMoney(line.amount, isMasked = hideBalances, currencySymbol = currencySymbol)
                            Text(
                                text = lineAmtStr,
                                fontSize = 12.sp,
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

