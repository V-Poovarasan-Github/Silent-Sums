package com.example.ui.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import com.example.ui.common.AppIcons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.IconButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import com.example.data.AppDatabase
import com.example.data.datastore.AppSettings
import com.example.data.datastore.AppSettingsRepository
import com.example.data.repository.MediaRepository
import com.example.data.seed.SampleDataSeeder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.combinedClickable
import com.example.domain.budget.BudgetTintTier
import com.example.domain.budget.CalculatedBudget
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.BarItem
import com.example.domain.model.CategoryBreakdownItem
import com.example.domain.model.ChartLevel
import com.example.domain.model.MonthlyIncomeSpend
import com.example.domain.model.PeriodFilter
import com.example.domain.model.TimeRange
import com.example.domain.model.TrendPoint
import com.example.domain.model.TrendBucket
import com.example.domain.model.TrendSummary
import com.example.domain.util.Formatters
import com.example.ui.common.AppTopHeader
import com.example.ui.common.HeaderIconButton
import com.example.ui.common.FilterChipsRow
import com.example.ui.common.liquidGlass
import com.example.ui.common.liquidGlassAtmosphere
import com.example.ui.common.raisedChildLayer
import com.example.ui.common.parseColorHex
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber500
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.Navy700
import com.example.ui.theme.Navy800
import com.example.ui.theme.Navy900
import com.example.ui.theme.SoftBlue
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import com.example.ui.common.LocalNoticeDispatcher
import com.example.ui.common.rememberSingleFlightNoticeDispatcher
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import com.example.domain.model.FilterState
import androidx.compose.ui.unit.Dp
import com.example.domain.util.RangeSummarizer
import com.example.domain.commitment.CommitmentGroups
import com.example.ui.commitment.CommitmentViewModel
import com.example.ui.commitment.CommitmentsPopup
import com.example.ui.common.CategoryEmojiBadge

object DashboardIntroTracker {
    var hasRunIntro: Boolean = false
    fun resetForTests() {
        hasRunIntro = false
    }
}

fun isReducedMotionEnabled(context: Context): Boolean {
    return try {
        val dScale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val tScale = Settings.Global.getFloat(context.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
        dScale == 0f || tScale == 0f
    } catch (_: Exception) {
        false
    }
}

@Composable
fun Modifier.animatedBar(barId: Any, isVertical: Boolean = false): Modifier {
    val context = LocalContext.current
    val reducedMotion = remember { isReducedMotionEnabled(context) }
    if (DashboardIntroTracker.hasRunIntro || reducedMotion) {
        return this
    }
    val animatable = remember(barId) { Animatable(0f) }
    LaunchedEffect(barId) {
        animatable.animateTo(1f, animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        if (isVertical) {
            scaleY = animatable.value
            transformOrigin = TransformOrigin(0.5f, 1.0f)
        } else {
            scaleX = animatable.value
            transformOrigin = TransformOrigin(0.0f, 0.5f)
        }
    }
}

val LocalHideBalances = compositionLocalOf { false }

@Composable
private fun IntroLeaf(content: @Composable () -> Unit) {
    val hideBalances = LocalHideBalances.current
    if (hideBalances) {
        Box(modifier = Modifier.semantics { contentDescription = "hidden" }) {
            content()
        }
    } else {
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    commitmentViewModel: CommitmentViewModel? = null,
    onNavigateToExpenses: (categoryId: Long?, accountId: Long?) -> Unit,
    showCommitmentsPopupOnStart: Boolean = false,
    onCommitmentsPopupDismissed: (() -> Unit)? = null,
    onNavigateToExpensesWithFilter: ((FilterState) -> Unit)? = null,
    onNavigateToEntry: ((type: String) -> Unit)? = null,
    onNavigateToTransferPrefill: ((fromAccountId: Long?, toAccountId: Long, amountText: String) -> Unit)? = null,
    noticeDispatcher: com.example.ui.common.SingleFlightNoticeDispatcher? = null,
    modifier: Modifier = Modifier
) {
    val ambientDispatcher = LocalNoticeDispatcher.current
    val fallbackDispatcher = rememberSingleFlightNoticeDispatcher()
    val effectiveNoticeDispatcher = noticeDispatcher ?: ambientDispatcher ?: fallbackDispatcher

    val state by viewModel.uiState.collectAsState()
    val liveFilter by viewModel.filter.collectAsState()

    var showHelpSheet by remember { mutableStateOf(false) }
    var showDemoCancelConfirmDialog by remember { mutableStateOf(false) }
    var showDemoExitAuthGate by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val appSettingsRepo = remember(context) { AppSettingsRepository(context) }
    val appSettings by appSettingsRepo.settingsFlow.collectAsState(initial = AppSettings())
    val reactiveCurrency by appSettingsRepo.currencySymbol.collectAsState()
    val activeCurrencySymbol = reactiveCurrency.ifBlank { state.currencySymbol.ifBlank { "₹" } }
    val database = remember(context) { AppDatabase.getDatabase(context) }
    val welcomePrefs = remember(context) {
        context.getSharedPreferences("paisa_track_prefs", Context.MODE_PRIVATE)
    }
    var welcomeCardDismissed by remember {
        mutableStateOf(welcomePrefs.getBoolean("welcome_card_dismissed", false))
    }
    var realTransactionEverLogged by remember {
        mutableStateOf(welcomePrefs.getBoolean("real_transaction_ever_logged", false))
    }

    LaunchedEffect(activeCurrencySymbol) {
        Formatters.updateCurrentCurrencySymbol(activeCurrencySymbol)
    }

    LaunchedEffect(state.allTransactions, appSettings.demoMode) {
        if (state.allTransactions.isNotEmpty() && !appSettings.demoMode && !realTransactionEverLogged) {
            welcomePrefs.edit().putBoolean("real_transaction_ever_logged", true).apply()
            realTransactionEverLogged = true
        }
    }

    val scopeMonthsInt = remember(state.scopeMonths) {
        val cal = Calendar.getInstance()
        state.scopeMonths.map {
            cal.timeInMillis = it
            cal.get(Calendar.MONTH) + 1
        }.toSet()
    }
    val effectiveFilterMonths = when {
        state.filter.selectedMonths.isNotEmpty() -> state.filter.selectedMonths
        state.filter.selectedMonth != null -> setOf(state.filter.selectedMonth!!)
        else -> scopeMonthsInt
    }
    val effectiveFilterYears = when {
        state.filter.selectedYears.isNotEmpty() -> state.filter.selectedYears
        state.filter.selectedYear != null -> setOf(state.filter.selectedYear!!)
        else -> state.scopeYears
    }

    val navigateToFilter: (FilterState) -> Unit = { filter ->
        if (onNavigateToExpensesWithFilter != null) {
            onNavigateToExpensesWithFilter(filter)
        } else {
            val effective = viewModel.applyDrillFilter(filter)
            val resolvedAccountId = effective.selectedAccountId ?: effective.selectedAccountIds.firstOrNull()
            val resolvedCategoryId = effective.selectedCategoryId ?: effective.selectedCategoryIds.firstOrNull()
            onNavigateToExpenses(resolvedCategoryId, resolvedAccountId)
        }
    }

    var showBalanceBreakdown by remember { mutableStateOf(false) }
    var isBudgetsCardExpanded by rememberSaveable { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<CalculatedBudget?>(null) }
    var editBudgetText by remember { mutableStateOf("") }

    var showCommitmentsPopup by remember { mutableStateOf(showCommitmentsPopupOnStart) }
    LaunchedEffect(showCommitmentsPopupOnStart) {
        if (showCommitmentsPopupOnStart) {
            showCommitmentsPopup = true
        }
    }

    val groupedCommitments by commitmentViewModel?.groupedCommitments?.collectAsState()
        ?: remember { mutableStateOf(CommitmentGroups(emptyList(), emptyList(), emptyList(), emptyList(), 0)) }
    val accounts by commitmentViewModel?.accounts?.collectAsState()
        ?: remember { mutableStateOf(emptyList()) }
    val categories by commitmentViewModel?.categories?.collectAsState()
        ?: remember { mutableStateOf(emptyList()) }
    val accountBalances by commitmentViewModel?.accountBalances?.collectAsState()
        ?: remember { mutableStateOf(emptyMap()) }

    val lazyListState = viewModel.lazyListState
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val showScrollToTop by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 1 ||
                (lazyListState.firstVisibleItemIndex == 1 && lazyListState.firstVisibleItemScrollOffset > 200)
        }
    }

    val reducedMotion = remember { isReducedMotionEnabled(context) }
    val introProgress = remember { Animatable(if (DashboardIntroTracker.hasRunIntro || reducedMotion) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (!DashboardIntroTracker.hasRunIntro && !reducedMotion) {
            introProgress.animateTo(1f, animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing))
            DashboardIntroTracker.hasRunIntro = true
        }
    }

    fun readIntroFactor(): Float = if (DashboardIntroTracker.hasRunIntro || reducedMotion) 1f else introProgress.value

    fun formatMoney(amount: Double): String {
        if (state.hideBalances) return "••••••"
        val factor = readIntroFactor()
        val effectiveAmount = if (factor >= 1f) amount else amount * factor
        return Formatters.formatIndianCurrency(effectiveAmount, currencySymbol = activeCurrencySymbol)
    }

    if (showDemoExitAuthGate) {
        com.example.ui.settings.DemoExitLockGate(
            correctPin = appSettings.pinHash,
            context = context,
            settingsRepository = appSettingsRepo,
            onUnlocked = {
                showDemoExitAuthGate = false
                coroutineScope.launch {
                    com.example.ui.settings.SettingsViewModel.executeExitDemoFlip(context, appSettingsRepo)
                }
            },
            onDismiss = { showDemoExitAuthGate = false }
        )
        return
    }

    CompositionLocalProvider(LocalHideBalances provides state.hideBalances) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .liquidGlassAtmosphere()
    ) {
        AppTopHeader(
            title = "SilentSums",
            subtitle = "Financial Dashboard",
            bellBadgeCount = groupedCommitments.badgeCount,
            onBellClick = { showCommitmentsPopup = true },
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
            }
        )

        val filterRowScrollState = rememberLazyListState()
        // Filter chips row
        FilterChipsRow(
            filterState = liveFilter,
            categories = state.activeCategories,
            accounts = state.activeAccounts,
            onFilterChange = { viewModel.updateFilter(it) },
            availableMonths = state.availableMonths,
            lazyListState = filterRowScrollState,
            onClearAll = { viewModel.clearAllSlicers() },
            modifier = Modifier.padding(top = 4.dp)
        )

        val currentTypeMode = state.filter.selectedType.uppercase().ifBlank { "ALL" }

        val creditCards = state.accountsWithBalance.filter { it.account.accountType == "CREDIT_CARD" && !it.account.archived }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("dashboard_lazy_column"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Demo Banner (4.3)
                if (appSettings.demoMode) {
                    item(key = "demo_banner") {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics(mergeDescendants = true) {
                                    contentDescription = "Demo mode: Viewing sample data"
                                }
                                .testTag("demo_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Lightbulb,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Viewing sample data",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            com.example.ui.settings.SettingsViewModel.requestExitDemoGate(
                                                context = context,
                                                settingsRepository = appSettingsRepo,
                                                onRequireAuth = { showDemoExitAuthGate = true }
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("demo_cancel")
                                ) {
                                    Text("Cancel", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // One-time Welcome Card (D1 + 4.1 + Phase 2.10 Split-Flag Rule)
                if (!welcomeCardDismissed && !realTransactionEverLogged && !appSettings.demoMode && state.allTransactions.isEmpty()) {
                    item(key = "welcome_card") {
                        Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("welcome_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.CreditCard,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Text(
                                        text = "You're all set.",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        welcomePrefs.edit().putBoolean("welcome_card_dismissed", true).apply()
                                        welcomeCardDismissed = true
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("welcome_card_dismiss")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    onNavigateToEntry?.invoke("EXPENSE")
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("welcome_card_cta")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Log your first expense",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            // 4.1 Quiet sample data offer line
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        com.example.MainActivity.markSessionUnlocked(context)
                                        com.example.data.DatabaseProvider.enterDemoMode(context, appSettingsRepo)
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("welcome_card_demo")
                            ) {
                                Text(
                                    text = "…or load sample data to explore.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    }
                }

            // 1. Horizontal Scrollable KPI Cards (2.1: vertically centered to eliminate dead space)
            item(key = "kpi_row") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Current Balance Hero Card (Unchanged across all modes)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .width(260.dp)
                        .liquidGlass(cornerRadius = 16.dp, elevation = 8.dp, tint = MaterialTheme.colorScheme.primary, isHero = true)
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction("View balance breakdown") {
                                    showBalanceBreakdown = true
                                    true
                                }
                            )
                        }
                        .combinedClickable(
                            onClick = { showBalanceBreakdown = true },
                            onLongClick = { showBalanceBreakdown = true }
                        )
                        .testTag("kpi_current_balance")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val effYears = effectiveFilterYears
                            val effMonths = effectiveFilterMonths

                            val balancePillText = when {
                                effMonths.size > 1 -> {
                                    val maxMonth = effMonths.max()
                                    val mName = Formatters.getMonthName(maxMonth)
                                    "as of $mName · latest of ${effMonths.size}"
                                }
                                effMonths.size == 1 -> {
                                    val mName = Formatters.getMonthName(effMonths.first())
                                    "as of $mName"
                                }
                                effYears.size > 1 -> "as of ${effYears.max()} · latest of ${effYears.size}"
                                effYears.size == 1 -> "as of ${effYears.first()}"
                                else -> "Live Net"
                            }

                            Text("Current Balance", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = balancePillText,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        AnimatedContent(
                            targetState = state.kpis.currentBalance,
                            transitionSpec = {
                                (slideInVertically { it / 2 } + fadeIn(tween(200)))
                                    .togetherWith(slideOutVertically { -it / 2 } + fadeOut(tween(150)))
                            },
                            label = "current_balance_anim"
                        ) { balance ->
                            IntroLeaf {
                                Text(
                                    text = formatMoney(balance),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-0.5).sp,
                                    modifier = Modifier
                                        .semantics {
                                            if (state.hideBalances) {
                                                contentDescription = "hidden"
                                            }
                                            customActions = listOf(
                                                CustomAccessibilityAction("View balance breakdown") {
                                                    showBalanceBreakdown = true
                                                    true
                                                }
                                            )
                                        }
                                        .combinedClickable(
                                            onClick = { showBalanceBreakdown = true },
                                            onLongClick = { showBalanceBreakdown = true }
                                        )
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${state.kpis.accountsCount} sources linked",
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                    fontSize = 11.sp
                                )
                            }
                            Canvas(modifier = Modifier.size(width = 60.dp, height = 20.dp)) {
                                val p = Path().apply {
                                    moveTo(0f, size.height * 0.8f)
                                    lineTo(size.width * 0.3f, size.height * 0.5f)
                                    lineTo(size.width * 0.6f, size.height * 0.7f)
                                    lineTo(size.width, size.height * 0.2f)
                                }
                                drawPath(p, color = Amber400, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                            }
                        }
                    }
                }

                if (currentTypeMode == "INCOME" || state.activeTypeMode == "INCOME") {
                    val effMonths = effectiveFilterMonths
                    val effYears = effectiveFilterYears
                    // Flow KPI 1: This Month Credits
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .width(220.dp)
                            .liquidGlass(16.dp)
                            .clickable {
                                val now = Calendar.getInstance()
                                val effY = effYears.firstOrNull() ?: now.get(Calendar.YEAR)
                                val effM = effMonths.firstOrNull() ?: (now.get(Calendar.MONTH) + 1)
                                navigateToFilter(
                                    FilterState(
                                        selectedYear = effY,
                                        selectedMonth = effM,
                                        selectedYears = setOf(effY),
                                        selectedMonths = setOf(effM),
                                        selectedType = "INCOME"
                                    )
                                )
                            }
                            .testTag("kpi_this_month_credits")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val creditsTitle = when {
                                effMonths.size == 1 -> "${Formatters.getMonthName(effMonths.first())} Credits"
                                effMonths.size > 1 -> "Period Credits"
                                else -> "This Month Credits"
                            }
                            Text(creditsTitle, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            AnimatedContent(
                                targetState = state.thisMonthCredits,
                                transitionSpec = {
                                    (slideInVertically { it / 2 } + fadeIn(tween(200)))
                                        .togetherWith(slideOutVertically { -it / 2 } + fadeOut(tween(150)))
                                },
                                label = "this_month_credits_anim"
                            ) { credits ->
                                IntroLeaf {
                                    Text(
                                        text = formatMoney(credits),
                                        color = IncomeGreen,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            if (state.lastMonthCredits == 0.0) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "First cycle",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            } else {
                                val isUp = state.creditsDeltaPercent >= 0
                                val deltaText = String.format(Locale.getDefault(), "%.0f%% vs prior", kotlin.math.abs(state.creditsDeltaPercent))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isUp) IncomeGreen.copy(alpha = 0.12f) else ExpenseRed.copy(alpha = 0.12f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isUp) AppIcons.ArrowUpward else AppIcons.ArrowDownward,
                                            contentDescription = if (isUp) "Increased" else "Decreased",
                                            tint = if (isUp) IncomeGreen else ExpenseRed,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = deltaText,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isUp) IncomeGreen else ExpenseRed
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Flow KPI 2: Prior Cycle Credits
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .width(180.dp)
                            .liquidGlass(16.dp)
                            .clickable {
                                if (effMonths.size == 1) {
                                    val singleMonth = effMonths.first()
                                    val prevMonth = if (singleMonth == 1) 12 else singleMonth - 1
                                    val effY = effYears.firstOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
                                    val prevYear = if (singleMonth == 1) effY - 1 else effY
                                    navigateToFilter(
                                        FilterState(
                                            selectedYear = prevYear,
                                            selectedMonth = prevMonth,
                                            selectedYears = setOf(prevYear),
                                            selectedMonths = setOf(prevMonth),
                                            selectedType = "INCOME"
                                        )
                                    )
                                } else {
                                    val prev = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                                    val py = prev.get(Calendar.YEAR)
                                    val pm = prev.get(Calendar.MONTH) + 1
                                    navigateToFilter(
                                        FilterState(
                                            selectedYear = py,
                                            selectedMonth = pm,
                                            selectedYears = setOf(py),
                                            selectedMonths = setOf(pm),
                                            selectedType = "INCOME"
                                        )
                                    )
                                }
                            }
                            .testTag("kpi_last_month_credits")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val priorCreditsTitle = when {
                                effMonths.size == 1 -> {
                                    val prevM = if (effMonths.first() == 1) 12 else effMonths.first() - 1
                                    "${Formatters.getMonthName(prevM)} Credits"
                                }
                                effMonths.size > 1 -> "Prior Period Credits"
                                else -> "Prior Cycle Credits"
                            }
                            Text(priorCreditsTitle, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            AnimatedContent(
                                targetState = state.lastMonthCredits,
                                transitionSpec = {
                                    (slideInVertically { it / 2 } + fadeIn(tween(200)))
                                        .togetherWith(slideOutVertically { -it / 2 } + fadeOut(tween(150)))
                                },
                                label = "last_month_credits_anim"
                            ) { credits ->
                                IntroLeaf {
                                    Text(
                                        text = formatMoney(credits),
                                        color = TextPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            val priorCreditsSub = when {
                                effMonths.size == 1 -> {
                                    val prevM = if (effMonths.first() == 1) 12 else effMonths.first() - 1
                                    "Previous month (${Formatters.getMonthName(prevM)})"
                                }
                                state.lastMonthCredits == 0.0 -> "No earlier cycle recorded"
                                else -> "vs previous cycle"
                            }
                            Text(priorCreditsSub, color = TextMuted, fontSize = 10.sp)
                        }
                    }

                    // Flow KPI 3: Total Credits (Mode-Native F19f)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .width(180.dp)
                            .liquidGlass(16.dp)
                            .clickable {
                                val effY = effYears.firstOrNull()
                                val effM = effMonths.firstOrNull()
                                navigateToFilter(
                                    FilterState(
                                        selectedYear = effY,
                                        selectedMonth = effM,
                                        selectedYears = effYears,
                                        selectedMonths = effMonths,
                                        selectedType = "INCOME"
                                    )
                                )
                            }
                            .testTag("kpi_total_credits")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Total Credits", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            AnimatedContent(
                                targetState = state.totalCreditsInScope,
                                transitionSpec = {
                                    (slideInVertically { it / 2 } + fadeIn(tween(200)))
                                        .togetherWith(slideOutVertically { -it / 2 } + fadeOut(tween(150)))
                                },
                                label = "income_total_credits_anim"
                            ) { totalCred ->
                                IntroLeaf {
                                    Text(
                                        text = formatMoney(totalCred),
                                        color = IncomeGreen,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = state.totalScopeSubLabel.ifBlank { "All months" },
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                } else if (currentTypeMode == "TRANSFER" || state.activeTypeMode == "TRANSFER") {
                    val effYears = effectiveFilterYears
                    val effMonths = effectiveFilterMonths

                    val transfersThisMonthTitle = when {
                        effMonths.size == 1 -> "${Formatters.getMonthName(effMonths.first())} Transfers"
                        effMonths.size > 1 -> "Period Transfers"
                        else -> "Transfers (This Mo)"
                    }
                    val transfersLastMonthTitle = when {
                        effMonths.size == 1 -> {
                            val prevM = if (effMonths.first() == 1) 12 else effMonths.first() - 1
                            "${Formatters.getMonthName(prevM)} Transfers"
                        }
                        effMonths.size > 1 -> "Prior Period Transfers"
                        else -> "Transfers (Last Mo)"
                    }

                    // Flow KPI 1: This-Mo Transfers
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .width(200.dp)
                            .liquidGlass(16.dp)
                            .clickable {
                                val effY = effYears.firstOrNull()
                                val effM = effMonths.firstOrNull()
                                navigateToFilter(
                                    FilterState(
                                        selectedYear = effY,
                                        selectedMonth = effM,
                                        selectedYears = effYears,
                                        selectedMonths = effMonths,
                                        selectedType = "TRANSFER"
                                    )
                                )
                            }
                            .testTag("kpi_transfers_this_month")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(transfersThisMonthTitle, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            AnimatedContent(
                                targetState = state.transfersThisMonth,
                                transitionSpec = {
                                    (slideInVertically { it / 2 } + fadeIn(tween(200)))
                                        .togetherWith(slideOutVertically { -it / 2 } + fadeOut(tween(150)))
                                },
                                label = "transfers_this_month_anim"
                            ) { moved ->
                                IntroLeaf {
                                    Text(
                                        text = formatMoney(moved),
                                        color = Amber500,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            Text("${state.totalMoveCountThisMonth} transfers this month", color = TextMuted, fontSize = 10.sp)
                        }
                    }

                    // Flow KPI 2: Last-Mo Transfers
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .width(180.dp)
                            .liquidGlass(16.dp)
                            .clickable {
                                if (effMonths.size == 1) {
                                    val singleMonth = effMonths.first()
                                    val prevMonth = if (singleMonth == 1) 12 else singleMonth - 1
                                    val effY = effYears.firstOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
                                    val prevYear = if (singleMonth == 1) effY - 1 else effY
                                    navigateToFilter(
                                        FilterState(
                                            selectedYear = prevYear,
                                            selectedMonth = prevMonth,
                                            selectedYears = setOf(prevYear),
                                            selectedMonths = setOf(prevMonth),
                                            selectedType = "TRANSFER"
                                        )
                                    )
                                } else {
                                    val prev = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                                    val py = prev.get(Calendar.YEAR)
                                    val pm = prev.get(Calendar.MONTH) + 1
                                    navigateToFilter(
                                        FilterState(
                                            selectedYear = py,
                                            selectedMonth = pm,
                                            selectedYears = setOf(py),
                                            selectedMonths = setOf(pm),
                                            selectedType = "TRANSFER"
                                        )
                                    )
                                }
                            }
                            .testTag("kpi_transfers_last_month")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(transfersLastMonthTitle, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            AnimatedContent(
                                targetState = state.transfersLastMonth,
                                transitionSpec = {
                                    (slideInVertically { it / 2 } + fadeIn(tween(200)))
                                        .togetherWith(slideOutVertically { -it / 2 } + fadeOut(tween(150)))
                                },
                                label = "transfers_last_month_anim"
                            ) { moved ->
                                IntroLeaf {
                                    Text(
                                        text = formatMoney(moved),
                                        color = TextPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text("${state.totalMoveCountLastMonth} transfers last month", color = TextMuted, fontSize = 10.sp)
                        }
                    }

                    // Flow KPI 3: Total Transfers (Mode-Native F19f)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .width(180.dp)
                            .liquidGlass(16.dp)
                            .clickable {
                                val effY = effYears.firstOrNull()
                                val effM = effMonths.firstOrNull()
                                navigateToFilter(
                                    FilterState(
                                        selectedYear = effY,
                                        selectedMonth = effM,
                                        selectedYears = effYears,
                                        selectedMonths = effMonths,
                                        selectedType = "TRANSFER"
                                    )
                                )
                            }
                            .testTag("kpi_total_transfers")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Total Transfers", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            AnimatedContent(
                                targetState = state.totalTransfersInScope,
                                transitionSpec = {
                                    (slideInVertically { it / 2 } + fadeIn(tween(200)))
                                        .togetherWith(slideOutVertically { -it / 2 } + fadeOut(tween(150)))
                                },
                                label = "total_transfers_anim"
                            ) { totalMoved ->
                                IntroLeaf {
                                    Text(
                                        text = formatMoney(totalMoved),
                                        color = Amber500,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = state.totalScopeSubLabel.ifBlank { "All months" },
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                } else {
                    val effMonths = effectiveFilterMonths
                    val effYears = effectiveFilterYears
                    // This Month Spend Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .width(220.dp)
                            .liquidGlass(16.dp)
                            .clickable {
                                val now = Calendar.getInstance()
                                val effY = effYears.firstOrNull() ?: now.get(Calendar.YEAR)
                                val effM = effMonths.firstOrNull() ?: (now.get(Calendar.MONTH) + 1)
                                navigateToFilter(
                                    FilterState(
                                        selectedYear = effY,
                                        selectedMonth = effM,
                                        selectedYears = setOf(effY),
                                        selectedMonths = setOf(effM),
                                        selectedType = "EXPENSE"
                                    )
                                )
                            }
                            .testTag("kpi_this_month_spend")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val spendTitle = when {
                                effMonths.size == 1 -> "${Formatters.getMonthName(effMonths.first())} Spend"
                                effMonths.size > 1 -> "Period Spend"
                                effYears.isNotEmpty() -> "Spend in ${state.monthYearLabel}"
                                else -> "This Month Spend"
                            }
                            Text(spendTitle, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            AnimatedContent(
                                targetState = state.kpis.thisMonthSpend,
                                transitionSpec = {
                                    (slideInVertically { it / 2 } + fadeIn(tween(200)))
                                        .togetherWith(slideOutVertically { -it / 2 } + fadeOut(tween(150)))
                                },
                                label = "this_month_spend_anim"
                            ) { spend ->
                                IntroLeaf {
                                    Text(
                                        text = formatMoney(spend),
                                        color = ExpenseRed,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            if (state.kpis.lastMonthSpend == 0.0) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "First month",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            } else {
                                val isDown = state.kpis.spendDeltaPercent <= 0
                                val deltaText = String.format(Locale.getDefault(), "%.0f%% vs prior", kotlin.math.abs(state.kpis.spendDeltaPercent))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isDown) IncomeGreen.copy(alpha = 0.12f) else ExpenseRed.copy(alpha = 0.12f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isDown) AppIcons.ArrowDownward else AppIcons.ArrowUpward,
                                            contentDescription = if (isDown) "Decreased" else "Increased",
                                            tint = if (isDown) IncomeGreen else ExpenseRed,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = deltaText,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDown) IncomeGreen else ExpenseRed
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Last Month Spend Card (Prior Cycle Spend)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .width(180.dp)
                            .liquidGlass(16.dp)
                            .clickable {
                                if (effMonths.size == 1) {
                                    val singleMonth = effMonths.first()
                                    val prevMonth = if (singleMonth == 1) 12 else singleMonth - 1
                                    val effY = effYears.firstOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
                                    val prevYear = if (singleMonth == 1) effY - 1 else effY
                                    navigateToFilter(
                                        FilterState(
                                            selectedYear = prevYear,
                                            selectedMonth = prevMonth,
                                            selectedYears = setOf(prevYear),
                                            selectedMonths = setOf(prevMonth),
                                            selectedType = "EXPENSE"
                                        )
                                    )
                                } else if (state.kpis.lastMonthSpend == 0.0) {
                                    navigateToFilter(FilterState())
                                } else {
                                    val prev = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                                    val py = prev.get(Calendar.YEAR)
                                    val pm = prev.get(Calendar.MONTH) + 1
                                    navigateToFilter(
                                        FilterState(
                                            selectedYear = py,
                                            selectedMonth = pm,
                                            selectedYears = setOf(py),
                                            selectedMonths = setOf(pm),
                                            selectedType = "EXPENSE"
                                        )
                                    )
                                }
                            }
                            .testTag("kpi_last_month_spend")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val priorTitle = when {
                                effMonths.size == 1 -> {
                                    val prevM = if (effMonths.first() == 1) 12 else effMonths.first() - 1
                                    "${Formatters.getMonthName(prevM)} Spend"
                                }
                                effMonths.size > 1 -> "Prior Period Spend"
                                else -> "Prior Cycle Spend"
                            }
                            Text(priorTitle, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            AnimatedContent(
                                targetState = state.kpis.lastMonthSpend,
                                transitionSpec = {
                                    (slideInVertically { it / 2 } + fadeIn(tween(200)))
                                        .togetherWith(slideOutVertically { -it / 2 } + fadeOut(tween(150)))
                                },
                                label = "last_month_spend_anim"
                            ) { spend ->
                                IntroLeaf {
                                    Text(
                                        text = formatMoney(spend),
                                        color = TextPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            val priorSub = when {
                                effMonths.size == 1 -> {
                                    val prevM = if (effMonths.first() == 1) 12 else effMonths.first() - 1
                                    "Previous month (${Formatters.getMonthName(prevM)})"
                                }
                                state.kpis.lastMonthSpend == 0.0 -> "No earlier cycle recorded"
                                else -> "vs previous cycle"
                            }
                            Text(priorSub, color = TextMuted, fontSize = 10.sp)
                        }
                    }

                    if (currentTypeMode == "EXPENSE") {
                        // Total Expense Card (Mode-Native F19f — replaces Total Credits in Expense mode)
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier
                                .width(180.dp)
                                .liquidGlass(16.dp)
                                .clickable {
                                    val effY = effYears.firstOrNull()
                                    val effM = effMonths.firstOrNull()
                                    navigateToFilter(
                                        FilterState(
                                            selectedYear = effY,
                                            selectedMonth = effM,
                                            selectedYears = effYears,
                                            selectedMonths = effMonths,
                                            selectedType = "EXPENSE"
                                        )
                                    )
                                }
                                .testTag("kpi_total_expense")
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Total Expense", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                AnimatedContent(
                                    targetState = state.totalExpenseInScope,
                                    transitionSpec = {
                                        (slideInVertically { it / 2 } + fadeIn(tween(200)))
                                            .togetherWith(slideOutVertically { -it / 2 } + fadeOut(tween(150)))
                                    },
                                    label = "total_expense_anim"
                                ) { totalExp ->
                                    IntroLeaf {
                                        Text(
                                            text = formatMoney(totalExp),
                                            color = ExpenseRed,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    text = state.totalScopeSubLabel.ifBlank { "All months" },
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    } else {
                        // Total Credits Card (All mode keeps mixed set, filtered)
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier
                                .width(180.dp)
                                .liquidGlass(16.dp)
                                .clickable {
                                    val effY = effYears.firstOrNull()
                                    val effM = effMonths.firstOrNull()
                                    navigateToFilter(
                                        FilterState(
                                            selectedYear = effY,
                                            selectedMonth = effM,
                                            selectedYears = effYears,
                                            selectedMonths = effMonths,
                                            selectedType = "INCOME"
                                        )
                                    )
                                }
                                .testTag("kpi_total_credits")
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Total Credits", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                AnimatedContent(
                                    targetState = state.kpis.totalCredits,
                                    transitionSpec = {
                                        (slideInVertically { it / 2 } + fadeIn(tween(200)))
                                            .togetherWith(slideOutVertically { -it / 2 } + fadeOut(tween(150)))
                                    },
                                    label = "total_credits_anim"
                                ) { credits ->
                                    IntroLeaf {
                                        Text(
                                            text = formatMoney(credits),
                                            color = IncomeGreen,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                val creditSub = if (effMonths.isNotEmpty() || effYears.isNotEmpty()) "Income in Period" else "All inward accounts"
                                Text(creditSub, color = TextMuted, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
            }

            // Credit Cards KPI Card (Hidden if no CREDIT_CARD sources exist)
            if (creditCards.isNotEmpty()) {
                item(key = "credit_cards_kpi_card") {
                val ccIds = creditCards.map { it.account.id }.toSet()
                val effMonths = effectiveFilterMonths
                val effYears = effectiveFilterYears
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(16.dp)
                        .animateContentSize()
                        .clickable {
                            navigateToFilter(
                                FilterState(
                                    selectedAccountId = ccIds.firstOrNull(),
                                    selectedAccountIds = ccIds,
                                    selectedYear = effYears.firstOrNull(),
                                    selectedMonth = effMonths.firstOrNull(),
                                    selectedYears = effYears,
                                    selectedMonths = effMonths
                                )
                            )
                        }
                        .testTag("kpi_credit_cards_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ExpenseRed.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = AppIcons.CreditCard,
                                        contentDescription = null,
                                        tint = ExpenseRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Credit Cards",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = TextPrimary
                                    )
                                    val debtSubLabel = when {
                                        effMonths.size > 1 -> "Total Debt Owed · as of ${Formatters.getMonthName(effMonths.max())}"
                                        effMonths.size == 1 -> "Total Debt Owed · as of ${Formatters.getMonthName(effMonths.first())}"
                                        effYears.isNotEmpty() -> "Total Debt Owed · as of ${state.monthYearLabel}"
                                        else -> "Total Debt Owed"
                                    }
                                    Text(
                                        text = debtSubLabel,
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                            }

                            // Net owed / credit / no-debt summary
                            AnimatedContent(
                                targetState = state.kpis.totalOwed,
                                transitionSpec = {
                                    (slideInVertically { it / 2 } + fadeIn(tween(200)))
                                        .togetherWith(slideOutVertically { -it / 2 } + fadeOut(tween(150)))
                                },
                                label = "total_owed_anim"
                            ) { owed ->
                                IntroLeaf {
                                    val debtSummaryText = when {
                                        owed > 0.0 -> "${formatMoney(owed)} owed"
                                        owed < 0.0 -> "${formatMoney(-owed)} credit"
                                        else -> "No debt"
                                    }
                                    val debtSummaryColor = if (owed > 0.0) ExpenseRed else IncomeGreen
                                    Text(
                                        text = debtSummaryText,
                                        color = debtSummaryColor,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier
                                            .clickable {
                                                navigateToFilter(
                                                    FilterState(
                                                        selectedAccountId = ccIds.firstOrNull(),
                                                        selectedAccountIds = ccIds,
                                                        selectedYear = effYears.firstOrNull(),
                                                        selectedMonth = effMonths.firstOrNull(),
                                                        selectedYears = effYears,
                                                        selectedMonths = effMonths
                                                    )
                                                )
                                            }
                                            .testTag("kpi_credit_cards_owed")
                                    )
                                }
                            }
                        }

                        // CC sub-metrics
                        val cardThisMoLabel = when {
                            effMonths.size == 1 -> "Card Spend (${Formatters.getMonthName(effMonths.first())})"
                            effMonths.size > 1 -> "Card Spend (Period)"
                            else -> "Card Spend (This Mo)"
                        }
                        val cardLastMoLabel = when {
                            effMonths.size == 1 -> {
                                val prevM = if (effMonths.first() == 1) 12 else effMonths.first() - 1
                                "Card Spend (${Formatters.getMonthName(prevM)})"
                            }
                            effMonths.size > 1 -> "Card Spend (Prior)"
                            else -> "Card Spend (Last Mo)"
                        }
                        val cardPayMoLabel = when {
                            effMonths.size == 1 -> "Card Payments (${Formatters.getMonthName(effMonths.first())})"
                            effMonths.size > 1 -> "Card Payments (Period)"
                            else -> "Card Payments (This Mo)"
                        }

                        val drillThisMonthCardSpend: () -> Unit = {
                            val now = Calendar.getInstance()
                            val effY = effYears.firstOrNull() ?: now.get(Calendar.YEAR)
                            val effM = effMonths.firstOrNull() ?: (now.get(Calendar.MONTH) + 1)
                            navigateToFilter(
                                FilterState(
                                    selectedYear = effY,
                                    selectedMonth = effM,
                                    selectedYears = setOf(effY),
                                    selectedMonths = setOf(effM),
                                    selectedType = "EXPENSE",
                                    selectedAccountId = ccIds.firstOrNull(),
                                    selectedAccountIds = ccIds
                                )
                            )
                        }
                        val drillLastMonthCardSpend: () -> Unit = {
                            if (effMonths.size == 1) {
                                val singleMonth = effMonths.first()
                                val prevMonth = if (singleMonth == 1) 12 else singleMonth - 1
                                val effY = effYears.firstOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
                                val prevYear = if (singleMonth == 1) effY - 1 else effY
                                navigateToFilter(
                                    FilterState(
                                        selectedYear = prevYear,
                                        selectedMonth = prevMonth,
                                        selectedYears = setOf(prevYear),
                                        selectedMonths = setOf(prevMonth),
                                        selectedType = "EXPENSE",
                                        selectedAccountId = ccIds.firstOrNull(),
                                        selectedAccountIds = ccIds
                                    )
                                )
                            } else {
                                val prev = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                                val py = prev.get(Calendar.YEAR)
                                val pm = prev.get(Calendar.MONTH) + 1
                                navigateToFilter(
                                    FilterState(
                                        selectedYear = py,
                                        selectedMonth = pm,
                                        selectedYears = setOf(py),
                                        selectedMonths = setOf(pm),
                                        selectedType = "EXPENSE",
                                        selectedAccountId = ccIds.firstOrNull(),
                                        selectedAccountIds = ccIds
                                    )
                                )
                            }
                        }
                        val drillCardPayments: () -> Unit = {
                            val now = Calendar.getInstance()
                            val effY = effYears.firstOrNull() ?: now.get(Calendar.YEAR)
                            val effM = effMonths.firstOrNull() ?: (now.get(Calendar.MONTH) + 1)
                            navigateToFilter(
                                FilterState(
                                    selectedYear = effY,
                                    selectedMonth = effM,
                                    selectedYears = setOf(effY),
                                    selectedMonths = setOf(effM),
                                    selectedType = "TRANSFER",
                                    selectedAccountId = ccIds.firstOrNull(),
                                    selectedAccountIds = ccIds
                                )
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (state.activeTypeMode == "INCOME" || state.activeTypeMode == "TRANSFER") {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .raisedChildLayer(10.dp, elevation = 1.dp)
                                        .clickable { drillCardPayments() }
                                        .padding(10.dp)
                                        .testTag("kpi_cc_payments_this_month")
                                ) {
                                    Column {
                                        Text(cardPayMoLabel, fontSize = 10.sp, color = TextMuted)
                                        IntroLeaf {
                                            Text(
                                                text = formatMoney(state.cardPaymentsThisMonth),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = IncomeGreen
                                            )
                                        }
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .raisedChildLayer(10.dp, elevation = 1.dp)
                                        .clickable { drillThisMonthCardSpend() }
                                        .padding(10.dp)
                                        .testTag("kpi_cc_spend_this_month")
                                ) {
                                    Column {
                                        Text(cardThisMoLabel, fontSize = 10.sp, color = TextMuted)
                                        IntroLeaf {
                                            Text(
                                                text = formatMoney(state.kpis.ccSpendThisMonth),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .raisedChildLayer(10.dp, elevation = 1.dp)
                                        .clickable { drillThisMonthCardSpend() }
                                        .padding(10.dp)
                                        .testTag("kpi_cc_spend_this_month")
                                ) {
                                    Column {
                                        Text(cardThisMoLabel, fontSize = 10.sp, color = TextMuted)
                                        IntroLeaf {
                                            Text(
                                                text = formatMoney(state.kpis.ccSpendThisMonth),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                        }
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .raisedChildLayer(10.dp, elevation = 1.dp)
                                        .clickable { drillLastMonthCardSpend() }
                                        .padding(10.dp)
                                        .testTag("kpi_cc_spend_last_month")
                                ) {
                                    Column {
                                        Text(cardLastMoLabel, fontSize = 10.sp, color = TextMuted)
                                        IntroLeaf {
                                            Text(
                                                text = formatMoney(state.kpis.ccSpendLastMonth),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
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
            }

            // 6. Sources & Net Balances List
            item(key = "sources_net_balances_card") {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(16.dp)
                    .animateContentSize()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sources & Net Balances",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimary,
                            modifier = Modifier.semantics { heading() }
                        )
                        val effMonths = effectiveFilterMonths
                        val sourcesSubLabel = when {
                            effMonths.size > 1 -> "as of ${Formatters.getMonthName(effMonths.max())} · latest of ${effMonths.size}"
                            effMonths.size == 1 -> "as of ${Formatters.getMonthName(effMonths.first())}"
                            effectiveFilterYears.isNotEmpty() -> "as of ${state.monthYearLabel}"
                            else -> "Tap to view ledger"
                        }
                        Text(sourcesSubLabel, fontSize = 10.sp, color = TextMuted)
                    }

                    val yourMoney = state.accountsWithBalance.filter { !it.account.archived && it.account.accountType != "CREDIT_CARD" }
                    val cardMoney = state.accountsWithBalance.filter { !it.account.archived && it.account.accountType == "CREDIT_CARD" }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (yourMoney.isNotEmpty()) {
                            Text(
                                text = "Your Money",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                modifier = Modifier
                                    .padding(top = 4.dp, bottom = 2.dp)
                                    .semantics { heading() }
                            )
                            yourMoney.forEach { accItem ->
                                val accBudget = state.calculatedBudgets.find {
                                    it.budget.scopeType.equals("ACCOUNT", ignoreCase = true) &&
                                    it.budget.targetName.equals(accItem.account.name, ignoreCase = true)
                                }
                                val sourceSummary = if (state.hideBalances) {
                                    "${accItem.account.name}, balance hidden"
                                } else {
                                    "${accItem.account.name}, balance ${formatMoney(accItem.currentBalance)}"
                                }
                                val hasBudgetChip = state.budgetsCardVisible && accBudget != null
                                if (hasBudgetChip && accBudget != null) {
                                    val viewInLedger = {
                                        navigateToFilter(
                                            FilterState(
                                                selectedAccountId = accItem.account.id,
                                                selectedAccountIds = setOf(accItem.account.id),
                                                selectedYear = effectiveFilterYears.firstOrNull(),
                                                selectedMonth = effectiveFilterMonths.firstOrNull(),
                                                selectedYears = effectiveFilterYears,
                                                selectedMonths = effectiveFilterMonths
                                            )
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .raisedChildLayer(10.dp)
                                            .semantics(mergeDescendants = true) {
                                                contentDescription = sourceSummary
                                                customActions = listOf(
                                                    CustomAccessibilityAction("View in ledger") {
                                                        viewInLedger()
                                                        true
                                                    }
                                                )
                                            }
                                            .clickable { viewInLedger() }
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                            .testTag("source_row_${accItem.account.id}"),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (accItem.account.accountType == "CASH") AppIcons.Wallet else AppIcons.AccountBalance,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = accItem.account.name,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.sp,
                                                    color = TextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                                )
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    IntroLeaf {
                                                        Text(
                                                            text = formatMoney(accItem.currentBalance),
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp,
                                                            maxLines = 1,
                                                            color = if (accItem.currentBalance >= 0) IncomeGreen else ExpenseRed
                                                        )
                                                    }
                                                    Icon(
                                                        imageVector = AppIcons.ChevronRight,
                                                        contentDescription = null,
                                                        tint = TextMuted,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            val chipColor = when (accBudget.tier) {
                                                BudgetTintTier.BELOW_80 -> Color(0xFF10B981)
                                                BudgetTintTier.EIGHTY_TO_99 -> Color(0xFFF59E0B)
                                                BudgetTintTier.HUNDRED_OR_ABOVE -> Color(0xFFEF4444)
                                            }
                                            val chipStatus = when (accBudget.tier) {
                                                BudgetTintTier.BELOW_80 -> "under limit"
                                                BudgetTintTier.EIGHTY_TO_99 -> "near limit"
                                                BudgetTintTier.HUNDRED_OR_ABOVE -> "over limit"
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = chipColor.copy(alpha = 0.14f),
                                                modifier = Modifier
                                                    .testTag("source_budget_chip_${accItem.account.id}")
                                                    .semantics { stateDescription = chipStatus }
                                            ) {
                                                IntroLeaf {
                                                    Text(
                                                        text = "${formatMoney(accBudget.spent)} / ${formatMoney(accBudget.limit)}",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = chipColor,
                                                        maxLines = 1,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            IntroLeaf {
                                                Text(
                                                    text = "Opening: ${formatMoney(accItem.account.openingBalance)}",
                                                    fontSize = 10.sp,
                                                    color = TextMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    val viewInLedger = {
                                        navigateToFilter(
                                            FilterState(
                                                selectedAccountId = accItem.account.id,
                                                selectedAccountIds = setOf(accItem.account.id),
                                                selectedYear = effectiveFilterYears.firstOrNull(),
                                                selectedMonth = effectiveFilterMonths.firstOrNull(),
                                                selectedYears = effectiveFilterYears,
                                                selectedMonths = effectiveFilterMonths
                                            )
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .raisedChildLayer(10.dp)
                                            .semantics(mergeDescendants = true) {
                                                contentDescription = sourceSummary
                                                customActions = listOf(
                                                    CustomAccessibilityAction("View in ledger") {
                                                        viewInLedger()
                                                        true
                                                    }
                                                )
                                            }
                                            .clickable { viewInLedger() }
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                            .testTag("source_row_${accItem.account.id}"),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (accItem.account.accountType == "CASH") AppIcons.Wallet else AppIcons.AccountBalance,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = accItem.account.name,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 13.sp,
                                                        color = TextPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                }
                                                IntroLeaf {
                                                    Text(
                                                        text = "Opening: ${formatMoney(accItem.account.openingBalance)}",
                                                        fontSize = 10.sp,
                                                        color = TextMuted,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IntroLeaf {
                                                Text(
                                                    text = formatMoney(accItem.currentBalance),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    color = if (accItem.currentBalance >= 0) IncomeGreen else ExpenseRed
                                                )
                                            }
                                            Icon(
                                                imageVector = AppIcons.ChevronRight,
                                                contentDescription = null,
                                                tint = TextMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (cardMoney.isNotEmpty()) {
                            Text(
                                text = "Credit Cards",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                modifier = Modifier
                                    .padding(top = 8.dp, bottom = 2.dp)
                                    .semantics { heading() }
                            )
                            val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
                            cardMoney.forEach { cardItem ->
                                val netDebt = -cardItem.currentBalance
                                val debt = if (netDebt > 0.0) netDebt else 0.0
                                val cardSummary = if (state.hideBalances) {
                                    "${cardItem.account.name}, Credit card, balance hidden"
                                } else {
                                    "${cardItem.account.name}, Credit card, net debt ${formatMoney(debt)}"
                                }

                                val payDueAction = {
                                    if (appSettings.soundHapticsEnabled) {
                                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    }
                                    if (netDebt <= 0.0) {
                                        effectiveNoticeDispatcher.showNotice(
                                            key = "NO_PAYMENT_NEEDED_${cardItem.account.id}",
                                            message = "No payment needed"
                                        )
                                    } else {
                                        val dueAmountText = String.format(Locale.US, "%.2f", netDebt)
                                        val lastPayingSourceId = state.allTransactions
                                            .asSequence()
                                            .filter {
                                                it.type.equals("TRANSFER", ignoreCase = true) &&
                                                    it.toAccountId == cardItem.account.id &&
                                                    it.fromAccountId != null
                                            }
                                            .maxWithOrNull(
                                                compareBy<com.example.data.model.TransactionEntity> { it.date }
                                                    .thenBy { it.id }
                                            )
                                            ?.fromAccountId
                                        onNavigateToTransferPrefill?.invoke(
                                            lastPayingSourceId,
                                            cardItem.account.id,
                                            dueAmountText
                                        )
                                    }
                                }

                                val viewInLedgerAction = {
                                    navigateToFilter(
                                        FilterState(
                                            selectedAccountId = cardItem.account.id,
                                            selectedAccountIds = setOf(cardItem.account.id),
                                            selectedYear = effectiveFilterYears.firstOrNull(),
                                            selectedMonth = effectiveFilterMonths.firstOrNull(),
                                            selectedYears = effectiveFilterYears,
                                            selectedMonths = effectiveFilterMonths
                                        )
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .raisedChildLayer(10.dp)
                                        .semantics(mergeDescendants = true) {
                                            contentDescription = cardSummary
                                            customActions = listOf(
                                                CustomAccessibilityAction("Pay due via transfer") {
                                                    payDueAction()
                                                    true
                                                },
                                                CustomAccessibilityAction("View in ledger") {
                                                    viewInLedgerAction()
                                                    true
                                                }
                                            )
                                        }
                                        .combinedClickable(
                                            onClick = { viewInLedgerAction() },
                                            onLongClick = { payDueAction() }
                                        )
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                        .testTag("source_row_${cardItem.account.id}"),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Row 1: Icon + Name + Budget Chip + Chevron
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(ExpenseRed.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = AppIcons.CreditCard,
                                                    contentDescription = null,
                                                    tint = ExpenseRed,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Text(
                                                text = cardItem.account.name,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                color = TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            val cardBudget = state.calculatedBudgets.find {
                                                it.budget.scopeType.equals("ACCOUNT", ignoreCase = true) &&
                                                it.budget.targetName.equals(cardItem.account.name, ignoreCase = true)
                                            }
                                            if (state.budgetsCardVisible && cardBudget != null) {
                                                val chipColor = when (cardBudget.tier) {
                                                    BudgetTintTier.BELOW_80 -> Color(0xFF10B981)
                                                    BudgetTintTier.EIGHTY_TO_99 -> Color(0xFFF59E0B)
                                                    BudgetTintTier.HUNDRED_OR_ABOVE -> Color(0xFFEF4444)
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = chipColor.copy(alpha = 0.14f),
                                                    modifier = Modifier.testTag("source_budget_chip_${cardItem.account.id}")
                                                ) {
                                                    IntroLeaf {
                                                        Text(
                                                            text = "${formatMoney(cardBudget.spent)} / ${formatMoney(cardBudget.limit)}",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = chipColor,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Icon(
                                            imageVector = AppIcons.ChevronRight,
                                            contentDescription = null,
                                            tint = TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Row 2: Separate row for Opening Debt (left) and Owed (right)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                            IntroLeaf {
                                                Text(
                                                    text = "Opening debt: ${formatMoney(if (cardItem.account.openingBalance < 0) -cardItem.account.openingBalance else 0.0)}",
                                                    fontSize = 11.sp,
                                                    color = TextMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        IntroLeaf {
                                            val cardBalanceText = when {
                                                netDebt > 0.0 -> "Owed: ${formatMoney(netDebt)}"
                                                netDebt < 0.0 -> "Credit: ${formatMoney(-netDebt)}"
                                                else -> "No debt"
                                            }
                                            Text(
                                                text = cardBalanceText,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                color = if (netDebt > 0.0) ExpenseRed else IncomeGreen
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }

            // Active Budgets Card (Hidden if no active budgets exist OR if budgetsCardVisible is false OR if mode is Income/Transfer)
            if (state.budgetsCardVisible && state.isBudgetsCardVisibleForMode && state.calculatedBudgets.isNotEmpty()) {
                item(key = "budgets_card") {
                val displayBudgets = if (isBudgetsCardExpanded || state.calculatedBudgets.size <= 3) {
                    state.calculatedBudgets
                } else {
                    state.calculatedBudgets.take(3)
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(16.dp)
                        .animateContentSize()
                        .testTag("dashboard_budgets_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Monthly Budgets",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TextPrimary,
                                    modifier = Modifier.semantics { heading() }
                                )
                            }
                            if (state.calculatedBudgets.size > 3) {
                                TextButton(
                                    onClick = { isBudgetsCardExpanded = !isBudgetsCardExpanded },
                                    modifier = Modifier.testTag("toggle_budgets_expand")
                                ) {
                                    Text(
                                        text = if (isBudgetsCardExpanded) "Show less" else "All (${state.calculatedBudgets.size})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        imageVector = if (isBudgetsCardExpanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            displayBudgets.forEach { cb ->
                                val tintColor = when (cb.tier) {
                                    BudgetTintTier.BELOW_80 -> Color(0xFF10B981)
                                    BudgetTintTier.EIGHTY_TO_99 -> Color(0xFFF59E0B)
                                    BudgetTintTier.HUNDRED_OR_ABOVE -> Color(0xFFEF4444)
                                }
                                val progressFraction = if (cb.limit > 0) (cb.spent / cb.limit).toFloat().coerceIn(0f, 1f) else 0f
                                val animatedProgress by animateFloatAsState(
                                    targetValue = progressFraction,
                                    animationSpec = tween(600, easing = FastOutSlowInEasing),
                                    label = "budget_fill_${cb.budget.id}"
                                )
                                val animatedTintColor by animateColorAsState(
                                    targetValue = tintColor,
                                    animationSpec = tween(400),
                                    label = "budget_tint_${cb.budget.id}"
                                )

                                val currentState by androidx.compose.runtime.rememberUpdatedState(state)
                                val currentNavigateToFilter by androidx.compose.runtime.rememberUpdatedState(navigateToFilter)
                                val currentCb by androidx.compose.runtime.rememberUpdatedState(cb)

                                val viewTransactionsAction = {
                                    val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
                                    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                                    val b = currentCb.budget
                                    val filter = when (b.scopeType.uppercase(Locale.ROOT)) {
                                        "CATEGORY" -> {
                                            val catId = currentState.activeCategories.find { it.name.equals(b.targetName, ignoreCase = true) }?.id
                                            FilterState(selectedCategoryId = catId, selectedMonth = currentMonth, selectedYear = currentYear)
                                        }
                                        "ACCOUNT" -> {
                                            val accId = currentState.activeAccounts.find { it.name.equals(b.targetName, ignoreCase = true) }?.id
                                            FilterState(selectedAccountId = accId, selectedMonth = currentMonth, selectedYear = currentYear)
                                        }
                                        else -> FilterState(selectedMonth = currentMonth, selectedYear = currentYear)
                                    }
                                    currentNavigateToFilter(filter)
                                }

                                val editBudgetLimitAction = {
                                    val targetCb = currentCb
                                    editingBudget = targetCb
                                    editBudgetText = if (targetCb.limit % 1.0 == 0.0) targetCb.limit.toLong().toString() else String.format(Locale.US, "%.2f", targetCb.limit)
                                }

                                val budgetHealthStatus = when {
                                    cb.isOverLimit -> "over limit"
                                    (cb.spent / cb.limit.coerceAtLeast(1.0)) >= 0.8 -> "near limit"
                                    else -> "under limit"
                                }
                                val percentSpent = ((cb.spent / cb.limit.coerceAtLeast(1.0)) * 100).toInt()
                                val budgetSentence = "${cb.budget.name}, budget ${formatMoney(cb.limit)}, spent ${formatMoney(cb.spent)}, $percentSpent percent, $budgetHealthStatus"

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .raisedChildLayer(10.dp)
                                        .semantics(mergeDescendants = true) {
                                            contentDescription = budgetSentence
                                            stateDescription = budgetHealthStatus
                                            customActions = listOf(
                                                CustomAccessibilityAction("View transactions") {
                                                    viewTransactionsAction()
                                                    true
                                                },
                                                CustomAccessibilityAction("Edit budget limit") {
                                                    editBudgetLimitAction()
                                                    true
                                                }
                                            )
                                        }
                                        .combinedClickable(
                                            onClick = { viewTransactionsAction() },
                                            onLongClick = { editBudgetLimitAction() }
                                        )
                                        .padding(14.dp)
                                        .testTag("budget_item_${cb.budget.id}"),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Line 1: Budget NAME on left + small status/scope chip on right
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = cb.budget.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = TextPrimary,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        val targetDesc = if (cb.budget.targetName.isNotBlank()) cb.budget.targetName else "All Spend"
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                        ) {
                                            Text(
                                                text = "${cb.budget.scopeType}: $targetDesc",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = TextMuted,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    // Line 2: Full "spent / limit" amount on its OWN row, full width so never splits
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IntroLeaf {
                                            Text(
                                                text = "${formatMoney(cb.spent)} / ${formatMoney(cb.limit)}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = animatedTintColor
                                            )
                                        }
                                    }

                                    // Line 3: "₹X left" or "Over by ₹X" on its OWN row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IntroLeaf {
                                            val statusDesc = if (cb.isOverLimit) {
                                                "Over by ${formatMoney(cb.spent - cb.limit)}"
                                            } else {
                                                "${formatMoney(cb.remaining)} left"
                                            }
                                            Text(
                                                text = statusDesc,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = animatedTintColor
                                            )
                                        }
                                    }

                                    // Line 4: Progress bar with smooth fill
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(fraction = animatedProgress.coerceIn(0f, 1f))
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(animatedTintColor)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }

            // 4. Horizontal Bar Chart ("Where money went" / "Where money came from" / "Where money moved")
            item(key = "category_breakdown_card") {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val currentBreakdownMode = state.filter.selectedType.uppercase().ifBlank { "ALL" }
                    val hasActiveCategorySlicer = state.filter.effectiveCategoryIds().isNotEmpty()

                    @Composable
                    fun BreakdownHeader(title: String, totalStr: String) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = totalStr,
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                                Text(
                                    text = "Tap bar to filter",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }

                    @Composable
                    fun ExpenseBreakdownSection() {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("breakdown_section_expense"),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IntroLeaf {
                                BreakdownHeader(
                                    title = "Where money went",
                                    totalStr = "${state.monthYearLabel} • ${formatMoney(state.totalExpenseInPeriod)} Total"
                                )
                            }
                            if (state.totalExpenseInPeriod == 0.0 || state.categoryBreakdown.isEmpty()) {
                                Text(
                                    text = "No expenses recorded in this period.",
                                    fontSize = 12.sp,
                                    color = TextMuted,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            } else {
                                var showAllCategories by remember { mutableStateOf(false) }
                                val displayList = if (showAllCategories || state.categoryBreakdown.size <= 8) {
                                    state.categoryBreakdown
                                } else {
                                    state.categoryBreakdown.take(8)
                                }
                                val catIconMap = remember(state.activeCategories) {
                                    state.activeCategories.associate { it.id to it.iconText }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    displayList.forEach { item ->
                                        val icon = item.categoryId?.let { catIconMap[it] }
                                        CategoryHorizontalBarRow(
                                            item = item,
                                            iconText = icon,
                                            hideBalances = state.hideBalances,
                                            currencySymbol = activeCurrencySymbol,
                                            onClick = {
                                                val resolvedCatId = item.categoryId ?: state.activeCategories.firstOrNull {
                                                    it.name.equals(item.name, ignoreCase = true)
                                                }?.id ?: if (item.name.equals("Uncategorized", ignoreCase = true)) -1L else null
                                                navigateToFilter(
                                                    FilterState(
                                                        selectedType = "EXPENSE",
                                                        selectedCategoryId = resolvedCatId,
                                                        selectedCategoryIds = resolvedCatId?.let { setOf(it) } ?: emptySet(),
                                                        selectedAccountId = null,
                                                        selectedAccountIds = emptySet()
                                                    )
                                                )
                                            }
                                        )
                                    }
                                    if (state.categoryBreakdown.size > 8) {
                                        TextButton(
                                            onClick = { showAllCategories = !showAllCategories },
                                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                        ) {
                                            Text(
                                                text = if (showAllCategories) "Show less" else "Show all (${state.categoryBreakdown.size}) categories",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    @Composable
                    fun IncomeBreakdownSection() {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("breakdown_section_income"),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IntroLeaf {
                                BreakdownHeader(
                                    title = "Where money came from",
                                    totalStr = "${state.monthYearLabel} • ${formatMoney(state.totalIncomeInPeriod)} Total"
                                )
                            }
                            if (state.totalIncomeInPeriod == 0.0 || state.incomeCategoryBreakdown.isEmpty()) {
                                Text(
                                    text = "No income recorded in this period.",
                                    fontSize = 12.sp,
                                    color = TextMuted,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            } else {
                                var showAllCategories by remember { mutableStateOf(false) }
                                val displayList = if (showAllCategories || state.incomeCategoryBreakdown.size <= 8) {
                                    state.incomeCategoryBreakdown
                                } else {
                                    state.incomeCategoryBreakdown.take(8)
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    displayList.forEach { item ->
                                        val resolvedCatId = item.categoryId ?: state.activeCategories.firstOrNull {
                                            it.name.equals(item.categoryName, ignoreCase = true)
                                        }?.id ?: if (item.categoryName.equals("Uncategorized", ignoreCase = true)) -1L else null
                                        IncomeCategoryHorizontalBarRow(
                                            item = item,
                                            hideBalances = state.hideBalances,
                                            currencySymbol = activeCurrencySymbol,
                                            onClick = {
                                                navigateToFilter(
                                                    FilterState(
                                                        selectedType = "INCOME",
                                                        selectedCategoryId = resolvedCatId,
                                                        selectedCategoryIds = resolvedCatId?.let { setOf(it) } ?: emptySet()
                                                    )
                                                )
                                            }
                                        )
                                    }
                                    if (state.incomeCategoryBreakdown.size > 8) {
                                        TextButton(
                                            onClick = { showAllCategories = !showAllCategories },
                                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                        ) {
                                            Text(
                                                text = if (showAllCategories) "Show less" else "Show all (${state.incomeCategoryBreakdown.size}) categories",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    @Composable
                    fun TransferBreakdownSection() {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("breakdown_section_transfer"),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IntroLeaf {
                                BreakdownHeader(
                                    title = "Where money moved",
                                    totalStr = "${state.monthYearLabel} • ${formatMoney(state.totalTransferInPeriod)} Total"
                                )
                            }
                            if (state.totalTransferInPeriod == 0.0 || state.transferRoutes.isEmpty()) {
                                val emptyTransferText = if (hasActiveCategorySlicer) {
                                    "No transfers recorded in this period. Transfers don't use categories."
                                } else {
                                    "No transfers recorded in this period."
                                }
                                Text(
                                    text = emptyTransferText,
                                    fontSize = 12.sp,
                                    color = TextMuted,
                                    modifier = Modifier
                                        .padding(vertical = 12.dp)
                                        .testTag("transfer_category_empty_hint")
                                )
                            } else {
                                var showAllRoutes by remember { mutableStateOf(false) }
                                val displayList = if (showAllRoutes || state.transferRoutes.size <= 8) {
                                    state.transferRoutes
                                } else {
                                    state.transferRoutes.take(8)
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    displayList.forEach { item ->
                                        TransferRouteHorizontalBarRow(
                                            item = item,
                                            totalMoved = state.totalTransferInPeriod,
                                            hideBalances = state.hideBalances,
                                            currencySymbol = activeCurrencySymbol,
                                            onClick = {
                                                navigateToFilter(
                                                    FilterState(
                                                        selectedType = "TRANSFER",
                                                        transferRouteFromAccountId = item.fromAccountId,
                                                        transferRouteToAccountId = item.toAccountId,
                                                        transferRouteLabel = item.routeName
                                                    )
                                                )
                                            }
                                        )
                                    }
                                    if (state.transferRoutes.size > 8) {
                                        TextButton(
                                            onClick = { showAllRoutes = !showAllRoutes },
                                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                        ) {
                                            Text(
                                                text = if (showAllRoutes) "Show less" else "Show all (${state.transferRoutes.size}) routes",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    when (currentBreakdownMode) {
                        "INCOME" -> IncomeBreakdownSection()
                        "TRANSFER" -> TransferBreakdownSection()
                        else -> ExpenseBreakdownSection()
                    }
                }
            }
            }

            // 5. Insights Card
            item(key = "insights_card") {
            val insightsSectionTag = when (state.activeInsightsMode) {
                "INCOME" -> "insights_section_income"
                "TRANSFER" -> "insights_section_transfer"
                else -> "insights_section_spending"
            }
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(16.dp)
                    .testTag(insightsSectionTag)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val insightsTitle = when (state.activeInsightsMode) {
                        "INCOME" -> "Income Insights"
                        "TRANSFER" -> "Transfer Insights"
                        else -> "Spending Insights"
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    when (state.activeInsightsMode) {
                                        "INCOME" -> IncomeGreen.copy(alpha = 0.2f)
                                        "TRANSFER" -> Amber400.copy(alpha = 0.2f)
                                        else -> Amber400.copy(alpha = 0.2f)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = AppIcons.Lightbulb,
                                contentDescription = null,
                                tint = if (state.activeInsightsMode == "INCOME") IncomeGreen else Amber500,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = insightsTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.semantics { heading() }
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.insights.forEach { insight ->
                            val bulletFilter: FilterState? = remember(insight, state.topCategoryId, state.largestExpenseDate) {
                                when {
                                    insight.contains("dominates at", ignoreCase = true) || insight.contains("largely driven by", ignoreCase = true) -> {
                                        state.topCategoryId?.let { FilterState(selectedCategoryId = it) }
                                    }
                                    insight.startsWith("Highest single expense:", ignoreCase = true) -> {
                                        state.largestExpenseDate?.let { date ->
                                            val cal = Calendar.getInstance().apply { timeInMillis = date }
                                            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                                            val dayStart = cal.timeInMillis
                                            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
                                            val dayEnd = cal.timeInMillis
                                            FilterState(startDate = dayStart, endDate = dayEnd)
                                        }
                                    }
                                    insight.startsWith("Spend is up", ignoreCase = true) || insight.startsWith("Spend is down", ignoreCase = true) -> {
                                        val now = Calendar.getInstance()
                                        FilterState(selectedYear = now.get(Calendar.YEAR), selectedMonth = now.get(Calendar.MONTH) + 1)
                                    }
                                    else -> null
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (bulletFilter != null) {
                                            Modifier
                                                .raisedChildLayer(8.dp, elevation = 1.dp)
                                                .clickable { navigateToFilter(bulletFilter) }
                                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                        } else {
                                            Modifier
                                                .raisedChildLayer(8.dp, elevation = 1.dp)
                                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                        }
                                    )
                                    .testTag("insight_bullet"),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "•",
                                    fontWeight = FontWeight.Black,
                                    color = Amber500,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(top = 1.dp)
                                )
                                Text(
                                    text = insight,
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                if (bulletFilter != null) {
                                    Icon(
                                        imageVector = AppIcons.ChevronRight,
                                        contentDescription = "View related transactions",
                                        tint = TextMuted.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                    )
                                }
                            }
                        }

                        if (state.budgetsCardVisible && state.isBudgetsCardVisibleForMode && state.filter.effectiveCategoryIds().isEmpty() && state.calculatedBudgets.isNotEmpty()) {
                            val tightestBudget = state.calculatedBudgets.minByOrNull { it.remaining } ?: state.calculatedBudgets.first()
                            val tintColor = when (tightestBudget.tier) {
                                BudgetTintTier.BELOW_80 -> Color(0xFF10B981)
                                BudgetTintTier.EIGHTY_TO_99 -> Color(0xFFF59E0B)
                                BudgetTintTier.HUNDRED_OR_ABOVE -> Color(0xFFEF4444)
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
                                        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                                        val filter = when (tightestBudget.budget.scopeType.uppercase(Locale.ROOT)) {
                                            "CATEGORY" -> {
                                                val catId = state.activeCategories.find { it.name.equals(tightestBudget.budget.targetName, ignoreCase = true) }?.id
                                                FilterState(selectedCategoryId = catId, selectedMonth = currentMonth, selectedYear = currentYear)
                                            }
                                            "ACCOUNT" -> {
                                                val accId = state.activeAccounts.find { it.name.equals(tightestBudget.budget.targetName, ignoreCase = true) }?.id
                                                FilterState(selectedAccountId = accId, selectedMonth = currentMonth, selectedYear = currentYear)
                                            }
                                            else -> FilterState(selectedMonth = currentMonth, selectedYear = currentYear)
                                        }
                                        navigateToFilter(filter)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                                    .testTag("spending_insights_budget_surface"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Tune,
                                        contentDescription = null,
                                        tint = tintColor,
                                        modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                    )
                                    Box(modifier = Modifier.weight(1f, fill = false)) {
                                        IntroLeaf {
                                            val tightestStatus = if (tightestBudget.isOverLimit) {
                                                "${tightestBudget.budget.name} over budget by ${formatMoney(tightestBudget.spent - tightestBudget.limit)}"
                                            } else {
                                                "${tightestBudget.budget.name}: ${formatMoney(tightestBudget.remaining)} left of ${formatMoney(tightestBudget.limit)}"
                                            }
                                            Text(
                                                text = tightestStatus,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = tintColor,
                                                lineHeight = 17.sp
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = AppIcons.ChevronRight,
                                    contentDescription = "View Budget",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
            }

            // 6. Running Balance Trend Bar Chart (Filtered by Week/Month/Year multi-select)
            item(key = "running_balance_trend_card") {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(20.dp)
                    .testTag("running_balance_trend_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val effType = state.filter.selectedType?.uppercase() ?: "ALL"
                    RunningBalanceBarChart(
                        bars = state.chartBars,
                        level = state.chartLevel,
                        scopeYears = state.scopeYears,
                        scopeMonths = state.scopeMonths,
                        scopeWeeks = state.scopeWeeks,
                        yearOptions = state.yearOptions,
                        monthOptions = state.monthOptions,
                        weekOptions = state.weekOptions,
                        activeType = effType,
                        onToggleYear = { viewModel.toggleYear(it) },
                        onToggleMonth = { viewModel.toggleMonth(it) },
                        onToggleWeek = { viewModel.toggleWeek(it) },
                        onClearScope = { viewModel.clearChartScope() },
                        onBarTapped = { viewModel.onBarTapped(it) },
                        onBarLongPressed = { bar ->
                            val filter = when (state.chartLevel) {
                                ChartLevel.DAYS -> {
                                    val cal = Calendar.getInstance().apply { timeInMillis = bar.key }
                                    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                                    val dStart = cal.timeInMillis
                                    cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
                                    val dEnd = cal.timeInMillis
                                    FilterState(startDate = dStart, endDate = dEnd)
                                }
                                ChartLevel.WEEKS -> {
                                    val end = if (bar.timestamp > bar.key) bar.timestamp else bar.key + 7L * 86400000L - 1L
                                    FilterState(startDate = bar.key, endDate = end)
                                }
                                ChartLevel.MONTHS -> {
                                    val cal = Calendar.getInstance().apply { timeInMillis = bar.key }
                                    FilterState(selectedYear = cal.get(Calendar.YEAR), selectedMonth = cal.get(Calendar.MONTH) + 1)
                                }
                                ChartLevel.YEARS -> {
                                    FilterState(selectedYear = bar.yearValue)
                                }
                            }
                            navigateToFilter(filter)
                        },
                        onJumpLevel = { viewModel.jumpToLevel(it) },
                        breadcrumb = state.breadcrumb,
                        unionHint = state.unionHint,
                        selectedBarKey = state.selectedBarKey,
                        hideBalances = state.hideBalances,
                        currencySymbol = activeCurrencySymbol
                    )
                }
            }
            }

            // 7. Income vs Spend Grouped Bars for Last 6 Months
            item(key = "income_vs_spend_card") {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(16.dp)
                    .testTag("income_vs_spend_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val monthsCount = state.monthlyComparison.size
                        val titleText = if (monthsCount > 1) {
                            "Income vs Spend (Last $monthsCount Months)"
                        } else {
                            "Income vs Spend"
                        }
                        Text(
                            text = titleText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(IncomeGreen))
                                Text("Income", fontSize = 10.sp, color = TextMuted)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(ExpenseRed))
                                Text("Spend", fontSize = 10.sp, color = TextMuted)
                            }
                        }
                    }

                    SixMonthsGroupedBarChart(
                        monthlyList = state.monthlyComparison,
                        hideBalances = state.hideBalances
                    )
                }
            }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showScrollToTop,
            enter = fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.8f, animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 0.8f, animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
        ) {
            SmallFloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        lazyListState.animateScrollToItem(0)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 8.dp
                ),
                modifier = Modifier
                    .size(44.dp)
                    .testTag("dashboard_scroll_to_top_button")
            ) {
                Icon(
                    imageVector = AppIcons.ArrowUpward,
                    contentDescription = "Scroll to top",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

    if (showBalanceBreakdown) {
        val effYears = if (state.filter.selectedYears.isNotEmpty()) state.filter.selectedYears
            else state.filter.selectedYear?.let { setOf(it) } ?: emptySet()
        val effMonths = if (state.filter.selectedMonths.isNotEmpty()) state.filter.selectedMonths
            else state.filter.selectedMonth?.let { setOf(it) } ?: emptySet()

        val (breakdownTransactions, refTime) = remember(state.allTransactions, effYears, effMonths) {
            val cal = Calendar.getInstance()
            val txList = if (effYears.isEmpty() && effMonths.isEmpty()) {
                state.allTransactions
            } else {
                state.allTransactions.filter { tx ->
                    cal.timeInMillis = tx.date
                    val y = cal.get(Calendar.YEAR)
                    val m = cal.get(Calendar.MONTH) + 1
                    val matchY = effYears.isEmpty() || y in effYears
                    val matchM = effMonths.isEmpty() || m in effMonths
                    matchY && matchM
                }
            }

            val rTime = when {
                effYears.isNotEmpty() && effMonths.isNotEmpty() -> {
                    val maxYear = effYears.max()
                    val maxMonth = effMonths.max()
                    cal.apply {
                        set(Calendar.YEAR, maxYear)
                        set(Calendar.MONTH, maxMonth - 1)
                        set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }.timeInMillis
                }
                effYears.isNotEmpty() -> {
                    val maxYear = effYears.max()
                    cal.timeInMillis = System.currentTimeMillis()
                    val currentYear = cal.get(Calendar.YEAR)
                    val targetMonth = if (maxYear == currentYear) {
                        cal.get(Calendar.MONTH) + 1
                    } else {
                        state.allTransactions.filter {
                            cal.timeInMillis = it.date
                            cal.get(Calendar.YEAR) == maxYear
                        }.maxOfOrNull {
                            cal.timeInMillis = it.date
                            cal.get(Calendar.MONTH) + 1
                        } ?: 12
                    }
                    cal.apply {
                        set(Calendar.YEAR, maxYear)
                        set(Calendar.MONTH, targetMonth - 1)
                        set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }.timeInMillis
                }
                effMonths.isNotEmpty() -> {
                    val maxMonth = effMonths.max()
                    cal.timeInMillis = System.currentTimeMillis()
                    val curY = cal.get(Calendar.YEAR)
                    val targetYear = state.allTransactions.filter {
                        cal.timeInMillis = it.date
                        cal.get(Calendar.MONTH) + 1 == maxMonth
                    }.maxOfOrNull {
                        cal.timeInMillis = it.date
                        cal.get(Calendar.YEAR)
                    } ?: curY
                    cal.apply {
                        set(Calendar.YEAR, targetYear)
                        set(Calendar.MONTH, maxMonth - 1)
                        set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }.timeInMillis
                }
                else -> maxOf(System.currentTimeMillis(), state.allTransactions.maxOfOrNull { it.date } ?: 0L)
            }
            txList to rTime
        }

        BalanceBreakdownSheet(
            accounts = state.allAccounts,
            transactions = breakdownTransactions,
            allTransactions = state.allTransactions,
            asOf = refTime,
            periodLabel = state.monthYearLabel.takeIf { it != "All Time" },
            hideBalances = state.hideBalances,
            currencySymbol = activeCurrencySymbol,
            onDismiss = { showBalanceBreakdown = false },
            onAccountClick = { accId ->
                val effY = state.filter.selectedYears.firstOrNull() ?: state.filter.selectedYear
                val effM = state.filter.selectedMonths.firstOrNull() ?: state.filter.selectedMonth
                navigateToFilter(
                    FilterState(
                        selectedAccountId = accId,
                        selectedAccountIds = setOf(accId),
                        selectedYear = effY,
                        selectedMonth = effM
                    )
                )
            }
        )
    }

    if (showCommitmentsPopup) {
        CommitmentsPopup(
            groups = groupedCommitments,
            accounts = accounts,
            categories = categories,
            accountBalances = accountBalances,
            currencySymbol = activeCurrencySymbol,
            hideBalances = state.hideBalances,
            onDismiss = {
                showCommitmentsPopup = false
                onCommitmentsPopupDismissed?.invoke()
            },
            onRecordAndMarkPaid = { commitmentViewModel?.recordAndMarkPaid(it) },
            onRecordPartialPayment = { c, amt, date, desc, newDate, accId ->
                commitmentViewModel?.recordPartialPayment(c, amt, date, desc, newDate, accId)
            },
            onSkipCycle = { commitmentViewModel?.skipCycle(it) },
            onSettleIou = { c, record, accId -> commitmentViewModel?.settleIou(c, record, accId) },
            onSaveCommitment = { commitmentViewModel?.saveCommitment(it) },
            onDeleteCommitment = { commitmentViewModel?.deleteCommitment(it) }
        )
    }

    if (showDemoCancelConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDemoCancelConfirmDialog = false },
            title = { Text("Exit Sample Mode?") },
            text = { Text("Exit sample mode and return to your real ledger? Temporary sample data will be removed. Your real transactions, photos, and voice notes remain safe.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDemoCancelConfirmDialog = false
                        coroutineScope.launch {
                            com.example.ui.settings.SettingsViewModel.requestExitDemoGate(
                                context = context,
                                settingsRepository = appSettingsRepo,
                                onRequireAuth = { showDemoExitAuthGate = true }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ExpenseRed,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("demo_cancel_confirm_btn")
                ) {
                    Text("Exit Sample Mode", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDemoCancelConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (editingBudget != null) {
        val b = editingBudget!!
        AlertDialog(
            onDismissRequest = { editingBudget = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            title = { Text("Edit Budget: ${b.budget.name}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val targetDesc = if (b.budget.targetName.isNotBlank()) b.budget.targetName else "All Spend"
                    Text("Scope: ${b.budget.scopeType} ($targetDesc)", fontSize = 12.sp, color = TextMuted)
                    OutlinedTextField(
                        value = editBudgetText,
                        onValueChange = { editBudgetText = it },
                        label = { Text("Budget Limit ($activeCurrencySymbol)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("edit_budget_limit_input")
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            viewModel.softDeleteBudget(b.budget.id)
                            editingBudget = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = ExpenseRed),
                        modifier = Modifier.testTag("delete_budget_button")
                    ) {
                        Text("Delete")
                    }
                    Button(
                        onClick = {
                            val newLim = editBudgetText.toDoubleOrNull()
                            if (newLim != null && newLim > 0) {
                                viewModel.updateBudgetLimit(b.budget.id, newLim)
                            }
                            editingBudget = null
                        },
                        modifier = Modifier.testTag("save_budget_button")
                    ) {
                        Text("Save")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { editingBudget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showHelpSheet) {
        HelpGesturesSheet(
            section = HelpSection.DASHBOARD,
            onDismiss = { showHelpSheet = false }
        )
    }
    }
}

@Composable
private fun CategoryHorizontalBarRow(
    item: CategoryBreakdownItem,
    iconText: String? = null,
    hideBalances: Boolean,
    currencySymbol: String = Formatters.currentCurrencySymbol(),
    onClick: () -> Unit
) {
    val barColor = parseColorHex(item.colorHex)
    val amountText = if (hideBalances) "••••••" else Formatters.formatIndianCurrency(item.amount, includeDecimals = false, currencySymbol = currencySymbol)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                CategoryEmojiBadge(
                    name = item.name,
                    iconText = iconText,
                    color = barColor,
                    size = 20.dp,
                    fontSize = 12.sp
                )
                Text(
                    text = item.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "$amountText (${String.format(Locale.getDefault(), "%.0f%%", item.percentage)})",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        // Progress Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (item.percentage / 100f).coerceIn(0.02f, 1f))
                    .height(8.dp)
                    .animatedBar(item.name, isVertical = false)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor)
            )
        }
    }
}

@Composable
private fun IncomeCategoryHorizontalBarRow(
    item: com.example.domain.calculator.IncomeCategoryItem,
    hideBalances: Boolean,
    currencySymbol: String = Formatters.currentCurrencySymbol(),
    onClick: () -> Unit
) {
    val barColor = parseColorHex(item.colorHex)
    val amountText = if (hideBalances) "••••••" else Formatters.formatIndianCurrency(item.amount, includeDecimals = false, currencySymbol = currencySymbol)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
            .testTag("income_category_bar_${item.categoryName}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(barColor)
                )
                Column {
                    Text(
                        text = item.categoryName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.topLandingAccount.isNotBlank()) {
                        Text(
                            text = "Top landing: ${item.topLandingAccount}",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                }
            }
            Text(
                text = "$amountText (${String.format(Locale.getDefault(), "%.0f%%", item.percentage)})",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = IncomeGreen,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (item.percentage / 100f).coerceIn(0.02f, 1f))
                    .height(8.dp)
                    .animatedBar(item.categoryName, isVertical = false)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor)
            )
        }
    }
}

@Composable
private fun TransferRouteHorizontalBarRow(
    item: com.example.domain.calculator.TransferRouteItem,
    totalMoved: Double,
    hideBalances: Boolean,
    currencySymbol: String = Formatters.currentCurrencySymbol(),
    onClick: () -> Unit
) {
    val amountText = if (hideBalances) "••••••" else Formatters.formatIndianCurrency(item.amount, includeDecimals = false, currencySymbol = currencySymbol)
    val pct = if (totalMoved > 0) ((item.amount / totalMoved) * 100f).coerceIn(0.0, 100.0).toFloat() else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
            .testTag("transfer_route_bar_${item.routeName}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Icon(
                    imageVector = AppIcons.ChevronRight,
                    contentDescription = null,
                    tint = Amber500,
                    modifier = Modifier.size(16.dp)
                )
                Column {
                    Text(
                        text = item.routeName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.count} transfer${if (item.count == 1) "" else "s"}",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }
            Text(
                text = "$amountText (${String.format(Locale.getDefault(), "%.0f%%", pct)})",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Amber500,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (pct / 100f).coerceIn(0.02f, 1f))
                    .height(8.dp)
                    .animatedBar(item.routeName, isVertical = false)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Amber500)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RunningBalanceBarChart(
    bars: List<BarItem>,
    level: ChartLevel,
    scopeYears: Set<Int>,
    scopeMonths: Set<Long>,
    scopeWeeks: Set<Int>,
    yearOptions: List<Int>,
    monthOptions: List<Pair<Long, String>>,
    weekOptions: List<Int>,
    onToggleYear: (Int) -> Unit,
    onToggleMonth: (Long) -> Unit,
    onToggleWeek: (Int) -> Unit,
    onClearScope: () -> Unit,
    onBarTapped: (BarItem) -> Unit,
    onBarLongPressed: ((BarItem) -> Unit)? = null,
    onJumpLevel: (ChartLevel) -> Unit,
    breadcrumb: List<Pair<String, ChartLevel>>,
    unionHint: Boolean,
    selectedBarKey: Long?,
    hideBalances: Boolean,
    currencySymbol: String = Formatters.currentCurrencySymbol(),
    activeType: String = "ALL",
    modifier: Modifier = Modifier
) {
    var yearMenu by remember { mutableStateOf(false) }
    var monthMenu by remember { mutableStateOf(false) }
    var weekMenu by remember { mutableStateOf(false) }

    val isExpenseMode = activeType.equals("EXPENSE", ignoreCase = true)
    val isIncomeMode = activeType.equals("INCOME", ignoreCase = true)
    val isTransferMode = activeType.equals("TRANSFER", ignoreCase = true)
    val isDedicatedType = isExpenseMode || isIncomeMode || isTransferMode

    val chartTitle = when {
        isExpenseMode -> "Expense Trend"
        isIncomeMode -> "Income Trend"
        isTransferMode -> "Transfer Trend"
        else -> "Running Balance Trend"
    }

    Column(modifier = modifier.fillMaxWidth().testTag("trend_bar_chart"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Row 1: Title & microcopy hint (stacked in Column so title never squeezes hint)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = chartTitle,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextPrimary,
                modifier = Modifier.testTag("trend_card_title")
            )
            Text(
                text = "Tap to zoom, hold to open ledger",
                fontSize = 10.sp,
                color = TextMuted,
                modifier = Modifier.testTag("trend_card_hint")
            )
        }

        // Row 2: Dedicated row for Year, Month, Week filter dropdowns
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Year dropdown
            Box {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (scopeYears.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { yearMenu = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (scopeYears.isNotEmpty()) scopeYears.sorted().joinToString(",") else "Year",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (scopeYears.isNotEmpty()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (scopeYears.isNotEmpty()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                DropdownMenu(expanded = yearMenu, onDismissRequest = { yearMenu = false }) {
                    yearOptions.forEach { yr ->
                        DropdownMenuItem(
                            text = { Text(yr.toString(), fontSize = 13.sp) },
                            onClick = { onToggleYear(yr) },
                            trailingIcon = { if (scopeYears.contains(yr)) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        )
                    }
                }
            }
            // Month dropdown
            Box {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (scopeMonths.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { monthMenu = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (scopeMonths.isNotEmpty()) "Month •${scopeMonths.size}" else "Month",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (scopeMonths.isNotEmpty()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (scopeMonths.isNotEmpty()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                DropdownMenu(expanded = monthMenu, onDismissRequest = { monthMenu = false }) {
                    monthOptions.forEach { (mk, label) ->
                        DropdownMenuItem(
                            text = { Text(label, fontSize = 13.sp) },
                            onClick = { onToggleMonth(mk) },
                            trailingIcon = { if (scopeMonths.contains(mk)) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        )
                    }
                }
            }
            // Week dropdown
            Box {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (scopeWeeks.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { weekMenu = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (scopeWeeks.isNotEmpty()) scopeWeeks.sorted().joinToString(",") { "W$it" } else "Week",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (scopeWeeks.isNotEmpty()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (scopeWeeks.isNotEmpty()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                DropdownMenu(expanded = weekMenu, onDismissRequest = { weekMenu = false }) {
                    weekOptions.forEach { w ->
                        DropdownMenuItem(
                            text = { Text("W$w", fontSize = 13.sp) },
                            onClick = { onToggleWeek(w) },
                            trailingIcon = { if (scopeWeeks.contains(w)) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        )
                    }
                }
            }

            // Clear button near year month week filter row
            if (scopeYears.isNotEmpty() || scopeMonths.isNotEmpty() || scopeWeeks.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.clickable { onClearScope() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Clear chart filters", modifier = Modifier.size(13.dp))
                        Text("Clear", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Row 3: Dedicated row for 'Tap a bar to drill down' hint (shown only when deeper level exists: Year/Month/Week; hidden at Day)
        if (level != ChartLevel.DAYS) {
            Text(
                text = "Tap a bar to drill down",
                fontSize = 12.sp,
                color = TextMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("trend_drill_down_hint")
            )
        }

        if (bars.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 130.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(AppIcons.TrendingUp, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(28.dp))
                    Text("No data in selected periods", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("Add transactions or clear a filter", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return
        }

        if (unionHint) {
            Text(
                text = "Showing selected week(s) across all months (union)",
                fontSize = 10.sp,
                color = Amber500,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Active bar details
        val activeBar = if (selectedBarKey != null) {
            bars.firstOrNull { it.key == selectedBarKey }
        } else if (level == ChartLevel.MONTHS && scopeMonths.isEmpty() && scopeWeeks.isEmpty() && bars.size >= 2) {
            null
        } else {
            bars.firstOrNull { it.isCurrent } ?: bars.lastOrNull()
        }
        ActiveBarBanner(
            activeBar = activeBar,
            bars = bars,
            level = level,
            scopeWeeks = scopeWeeks,
            scopeMonths = scopeMonths,
            scopeYears = scopeYears,
            hideBalances = hideBalances,
            currencySymbol = currencySymbol,
            activeType = activeType
        )

        // Bars: centered when <= 7, scrollable beyond (3 vertically stacked rows sharing slot layout)
        val maxVal = if (isExpenseMode) {
            bars.maxOfOrNull { it.expense } ?: 0.0
        } else if (isIncomeMode) {
            bars.maxOfOrNull { it.income } ?: 0.0
        } else if (isTransferMode) {
            bars.maxOfOrNull { it.transfer } ?: 0.0
        } else {
            bars.maxOfOrNull { kotlin.math.abs(it.balance) } ?: 0.0
        }

        val totalSpan = maxVal.coerceAtLeast(0.0001)

        val topGutter = 20.dp
        val plotHeightDp = 120.dp
        val chartAreaHeight = topGutter + plotHeightDp // 140.dp

        val scale = plotHeightDp.value / totalSpan.toFloat()

        val isTimeline = level == ChartLevel.DAYS && scopeWeeks.isEmpty() && scopeMonths.isEmpty() && scopeYears.isEmpty()

        if (isTimeline) {
            val todayIndex = remember(bars) { bars.indexOfFirst { it.isToday } }
            val initialIndex = remember(todayIndex, bars.size) {
                if (todayIndex >= 0) maxOf(0, todayIndex - 6) else maxOf(0, bars.size - 7)
            }
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
            LaunchedEffect(todayIndex, bars.size) {
                val target = if (todayIndex >= 0) maxOf(0, todayIndex - 6) else maxOf(0, bars.size - 7)
                listState.scrollToItem(target, 0)
            }

            // Floating pin chip indicating the week of the currently centered day
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            val centeredItemIndex = if (visibleItems.isNotEmpty()) {
                val centerOffset = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                visibleItems.minByOrNull { kotlin.math.abs((it.offset + it.size / 2) - centerOffset) }?.index ?: 0
            } else (listState.firstVisibleItemIndex + 3).coerceAtMost(maxOf(0, bars.size - 1))
            val centeredBar = bars.getOrNull(centeredItemIndex)
            val activeVisibleBar = activeBar?.takeIf { ab ->
                visibleItems.any { bars.getOrNull(it.index)?.key == ab.key }
            }
            val pinBar = activeVisibleBar ?: centeredBar

            if (pinBar != null) {
                val pinLabel = com.example.domain.calculator.LedgerCalculator.formatWeekPinLabel(pinBar.monthKey, pinBar.weekIndex)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable { onToggleWeek(pinBar.weekIndex) }
                        .testTag("timeline_pin_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(AppIcons.PushPin, contentDescription = "Pin week", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            text = pinLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val slotWidth = maxWidth / 7f
                val snapFling = rememberSnapFlingBehavior(lazyListState = listState)
                val sharedCal = remember { Calendar.getInstance() }
                LazyRow(
                    state = listState,
                    flingBehavior = snapFling,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("day_timeline_lazy_row"),
                    horizontalArrangement = Arrangement.Start
                ) {
                    itemsIndexed(bars, key = { _, bar -> bar.key }) { index, bar ->
                        val isFirstOfWeek = index == 0 || (bar.weekIndex != bars[index - 1].weekIndex || bar.monthKey != bars[index - 1].monthKey)
                        val weekLabel = remember(isFirstOfWeek, bar.key, bar.weekIndex) {
                            if (isFirstOfWeek) {
                                sharedCal.timeInMillis = bar.key
                                "W${bar.weekIndex} • ${Formatters.getMonthName(sharedCal.get(Calendar.MONTH) + 1).take(4)}"
                            } else null
                        }

                        BarColumnView(
                            bar = bar,
                            isSelected = bar.key == activeBar?.key,
                            slotWidth = slotWidth,
                            chartAreaHeight = chartAreaHeight,
                            scale = scale,
                            hideBalances = hideBalances,
                            currencySymbol = currencySymbol,
                            weekGroupLabel = weekLabel,
                            activeType = activeType,
                            onTap = { onBarTapped(bar) },
                            onLongPress = { onBarLongPressed?.invoke(bar) }
                        )
                    }
                }
            }
        } else {
            val isCentered = bars.size <= 7
            val rowModifier = if (isCentered) {
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp)
            }

            if (isCentered) {
                BoxWithConstraints(modifier = rowModifier) {
                    val slotWidth = maxWidth / 7f
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Top
                    ) {
                        bars.forEach { bar ->
                            BarColumnView(
                                bar = bar,
                                isSelected = bar.key == activeBar?.key,
                                slotWidth = slotWidth,
                                chartAreaHeight = chartAreaHeight,
                                scale = scale,
                                hideBalances = hideBalances,
                                currencySymbol = currencySymbol,
                                weekGroupLabel = null,
                                activeType = activeType,
                                onTap = { onBarTapped(bar) },
                                onLongPress = { onBarLongPressed?.invoke(bar) }
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = rowModifier,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    bars.forEach { bar ->
                        BarColumnView(
                            bar = bar,
                            isSelected = bar.key == activeBar?.key,
                            slotWidth = 48.dp,
                            chartAreaHeight = chartAreaHeight,
                            scale = scale,
                            hideBalances = hideBalances,
                            currencySymbol = currencySymbol,
                            weekGroupLabel = null,
                            activeType = activeType,
                            onTap = { onBarTapped(bar) },
                            onLongPress = { onBarLongPressed?.invoke(bar) }
                        )
                    }
                }
            }
        }

        // Breadcrumb pill (tap a segment to jump up a level)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            breadcrumb.forEachIndexed { idx, (label, targetLevel) ->
                val isLast = idx == breadcrumb.lastIndex
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                    color = if (isLast) MaterialTheme.colorScheme.primary else TextMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !isLast) { onJumpLevel(targetLevel) }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
                if (!isLast) Text("›", fontSize = 11.sp, color = TextMuted)
            }
        }
    }
}

@Composable
private fun ActiveBarBanner(
    activeBar: BarItem?,
    bars: List<BarItem>,
    level: ChartLevel,
    scopeWeeks: Set<Int>,
    scopeMonths: Set<Long>,
    scopeYears: Set<Int>,
    hideBalances: Boolean,
    currencySymbol: String = Formatters.currentCurrencySymbol(),
    activeType: String = "ALL"
) {
    val isExpenseMode = activeType.equals("EXPENSE", ignoreCase = true)
    val isIncomeMode = activeType.equals("INCOME", ignoreCase = true)
    val isTransferMode = activeType.equals("TRANSFER", ignoreCase = true)
    val typePrefix = when {
        isExpenseMode -> "Expense"
        isIncomeMode -> "Income"
        isTransferMode -> "Transfer"
        else -> "Balance"
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trend_active_bar_banner")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (activeBar == null) {
                val totalIncome = bars.sumOf { it.income }
                val totalExpense = bars.sumOf { it.expense }
                val spanNet = totalIncome - totalExpense
                val compactRight = if (hideBalances) "••••" else {
                    when {
                        isExpenseMode -> Formatters.formatCompactCurrency(totalExpense, currencySymbol = currencySymbol)
                        isIncomeMode -> Formatters.formatCompactCurrency(totalIncome, currencySymbol = currencySymbol)
                        isTransferMode -> Formatters.formatCompactCurrency(bars.sumOf { it.transfer }, currencySymbol = currencySymbol)
                        else -> Formatters.formatCompactCurrency(spanNet, showPlusSign = true, currencySymbol = currencySymbol)
                    }
                }
                val rightHeaderLabel = when {
                    isExpenseMode || isIncomeMode || isTransferMode -> "Total $compactRight"
                    else -> "Net $compactRight"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Net Change • ${bars.size} Months",
                        fontSize = 12.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = rightHeaderLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (spanNet < 0) ExpenseRed else IncomeGreen,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                val displayBal = if (hideBalances) "••••••" else {
                    when {
                        isExpenseMode -> Formatters.formatIndianCurrency(totalExpense, currencySymbol = currencySymbol)
                        isIncomeMode -> Formatters.formatIndianCurrency(totalIncome, currencySymbol = currencySymbol)
                        isTransferMode -> Formatters.formatIndianCurrency(bars.sumOf { it.transfer }, currencySymbol = currencySymbol)
                        else -> Formatters.formatIndianCurrency(spanNet, showPlusSign = true, currencySymbol = currencySymbol)
                    }
                }
                val displayColor = when {
                    isExpenseMode -> ExpenseRed
                    isIncomeMode -> IncomeGreen
                    isTransferMode -> MaterialTheme.colorScheme.primary
                    else -> if (spanNet < 0.0) ExpenseRed else IncomeGreen
                }

                Text(
                    text = displayBal,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = displayColor,
                    letterSpacing = (-0.5).sp,
                    modifier = Modifier.fillMaxWidth()
                )

                val inStr = if (totalIncome > 0) "+${Formatters.formatMoney(totalIncome, isMasked = hideBalances, currencySymbol = currencySymbol, includeDecimals = false, mask = "••••")}" else ""
                val outStr = if (totalExpense > 0) "-${Formatters.formatMoney(totalExpense, isMasked = hideBalances, currencySymbol = currencySymbol, includeDecimals = false, mask = "••••")}" else ""
                val flowsStr = listOf(inStr, outStr).filter { it.isNotEmpty() }.joinToString("  ")
                val rangeStr = if (bars.isNotEmpty()) "${bars.first().dateLabel} – ${bars.last().dateLabel}" else ""
                val exactNetStr = "Net " + (if (hideBalances) "••••" else Formatters.formatIndianCurrency(spanNet, showPlusSign = true, currencySymbol = currencySymbol))
                val row3Text = listOf(flowsStr, rangeStr, exactNetStr).filter { it.isNotBlank() }.joinToString(" • ")

                Text(
                    text = row3Text,
                    fontSize = 11.sp,
                    color = TextMuted,
                    lineHeight = 15.sp
                )
            } else {
                val monthBarsToDate = if (level == ChartLevel.DAYS) {
                    bars.filter { it.monthKey == activeBar.monthKey && it.key <= activeBar.key }
                } else emptyList()
                val effBarExpense = if (level == ChartLevel.DAYS && activeBar.expense == 0.0 && monthBarsToDate.isNotEmpty()) {
                    monthBarsToDate.sumOf { it.expense }
                } else activeBar.expense
                val effBarIncome = if (level == ChartLevel.DAYS && activeBar.income == 0.0 && monthBarsToDate.isNotEmpty()) {
                    monthBarsToDate.sumOf { it.income }
                } else activeBar.income
                val effBarTransfer = if (level == ChartLevel.DAYS && activeBar.transfer == 0.0 && monthBarsToDate.isNotEmpty()) {
                    monthBarsToDate.sumOf { it.transfer }
                } else activeBar.transfer
                val effBarNet = if (level == ChartLevel.DAYS && activeBar.netChange == 0.0 && monthBarsToDate.isNotEmpty()) {
                    monthBarsToDate.sumOf { it.netChange }
                } else activeBar.netChange

                // Row 1: left = short period label only; right = Period Net or Total in COMPACT form
                val shortPeriodLabel = when (level) {
                    ChartLevel.DAYS -> "$typePrefix • ${activeBar.dateLabel} ${activeBar.subLabel}".trim()
                    ChartLevel.WEEKS -> "$typePrefix • W${activeBar.weekIndex}"
                    ChartLevel.MONTHS -> "$typePrefix • ${activeBar.dateLabel}"
                    ChartLevel.YEARS -> "$typePrefix • ${activeBar.dateLabel}"
                }
                val compactRight = if (hideBalances) "••••" else {
                    when {
                        isExpenseMode -> Formatters.formatCompactCurrency(effBarExpense, currencySymbol = currencySymbol)
                        isIncomeMode -> Formatters.formatCompactCurrency(effBarIncome, currencySymbol = currencySymbol)
                        isTransferMode -> Formatters.formatCompactCurrency(effBarTransfer, currencySymbol = currencySymbol)
                        else -> Formatters.formatCompactCurrency(effBarNet, showPlusSign = true, currencySymbol = currencySymbol)
                    }
                }

                val rightHeaderLabel = when {
                    isExpenseMode || isIncomeMode || isTransferMode -> "Total $compactRight"
                    else -> "Net $compactRight"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = shortPeriodLabel,
                        fontSize = 12.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    BoxWithConstraints(modifier = Modifier.wrapContentWidth()) {
                        val rightWidth = maxWidth
                        val fontSize = if (rightWidth < 90.dp) 11.sp else 13.sp
                        val rightColor = when {
                            isExpenseMode -> ExpenseRed
                            isIncomeMode -> IncomeGreen
                            isTransferMode -> MaterialTheme.colorScheme.primary
                            effBarNet == 0.0 -> TextMuted
                            effBarNet > 0 -> IncomeGreen
                            else -> ExpenseRed
                        }

                        Text(
                            text = rightHeaderLabel,
                            fontSize = fontSize,
                            fontWeight = FontWeight.Bold,
                            color = rightColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Row 2: large balance value at full precision, full width
                val displayBal = if (hideBalances) "••••••" else {
                    when {
                        isExpenseMode -> Formatters.formatIndianCurrency(effBarExpense, currencySymbol = currencySymbol)
                        isIncomeMode -> Formatters.formatIndianCurrency(effBarIncome, currencySymbol = currencySymbol)
                        isTransferMode -> Formatters.formatIndianCurrency(effBarTransfer, currencySymbol = currencySymbol)
                        else -> Formatters.formatIndianCurrency(activeBar.balance, currencySymbol = currencySymbol)
                    }
                }
                val displayColor = when {
                    isExpenseMode -> ExpenseRed
                    isIncomeMode -> IncomeGreen
                    isTransferMode -> MaterialTheme.colorScheme.primary
                    else -> if (activeBar.balance < 0.0) ExpenseRed else MaterialTheme.colorScheme.primary
                }

                Text(
                    text = displayBal,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = displayColor,
                    letterSpacing = (-0.5).sp,
                    modifier = Modifier.fillMaxWidth()
                )

                // Row 3 (muted, wrapping allowed): "+₹12,968 −₹1,545 • <full range> • Net +₹11,423.00"
                val inStr = if (effBarIncome > 0) "+${Formatters.formatMoney(effBarIncome, isMasked = hideBalances, currencySymbol = currencySymbol, includeDecimals = false, mask = "••••")}" else ""
                val outStr = if (effBarExpense > 0) "-${Formatters.formatMoney(effBarExpense, isMasked = hideBalances, currencySymbol = currencySymbol, includeDecimals = false, mask = "••••")}" else ""
                val flowsStr = listOf(inStr, outStr).filter { it.isNotEmpty() }.joinToString("  ")

                val rangeStr = remember(scopeWeeks, scopeMonths, scopeYears, activeBar.fullRangeLabel, activeBar.dateLabel) {
                    when {
                        scopeWeeks.isNotEmpty() -> RangeSummarizer.summarizeWeeks(scopeWeeks.toList(), singleExactDates = activeBar.fullRangeLabel)
                        scopeMonths.isNotEmpty() -> {
                            val cal = Calendar.getInstance()
                            val mIndices = scopeMonths.map {
                                cal.timeInMillis = it
                                cal.get(Calendar.MONTH) + 1
                            }
                            RangeSummarizer.summarizeMonths(mIndices, singleExactDates = activeBar.fullRangeLabel)
                        }
                        scopeYears.isNotEmpty() -> RangeSummarizer.summarizeYears(scopeYears.toList(), singleExactDates = activeBar.fullRangeLabel)
                        else -> activeBar.fullRangeLabel.ifBlank { activeBar.dateLabel }
                    }
                }

                val exactNetStr = "Net " + (if (hideBalances) "••••" else Formatters.formatIndianCurrency(effBarNet, showPlusSign = true, currencySymbol = currencySymbol))

                val row3Text = listOf(flowsStr, rangeStr, exactNetStr).filter { it.isNotBlank() }.joinToString(" • ")
                Text(
                    text = row3Text,
                    fontSize = 11.sp,
                    color = TextMuted,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BarColumnView(
    bar: BarItem,
    isSelected: Boolean,
    slotWidth: Dp,
    chartAreaHeight: Dp,
    scale: Float,
    hideBalances: Boolean,
    currencySymbol: String = Formatters.currentCurrencySymbol(),
    weekGroupLabel: String? = null,
    activeType: String = "ALL",
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val isExpenseMode = activeType.equals("EXPENSE", ignoreCase = true)
    val isIncomeMode = activeType.equals("INCOME", ignoreCase = true)
    val isTransferMode = activeType.equals("TRANSFER", ignoreCase = true)
    val isDedicated = isExpenseMode || isIncomeMode || isTransferMode

    val (displayVal, isZero, barColor) = if (isDedicated) {
        val v = when {
            isExpenseMode -> bar.expense
            isIncomeMode -> bar.income
            isTransferMode -> bar.transfer
            else -> 0.0
        }
        val col = when {
            isExpenseMode -> ExpenseRed
            isIncomeMode -> IncomeGreen
            else -> MaterialTheme.colorScheme.primary
        }
        Triple(v, v < 0.001, col)
    } else {
        val absBal = kotlin.math.abs(bar.balance)
        val col = if (bar.balance >= 0.0) IncomeGreen else ExpenseRed
        Triple(absBal, absBal < 0.001, col)
    }

    val ambientDispatcher = LocalNoticeDispatcher.current
    val fallbackDispatcher = rememberSingleFlightNoticeDispatcher()
    val effectiveNoticeDispatcher = ambientDispatcher ?: fallbackDispatcher

    val exactValueAnnouncement = remember(bar, hideBalances, isDedicated, displayVal, currencySymbol) {
        val dateText = listOf(bar.dateLabel, bar.subLabel).filter { it.isNotBlank() }.joinToString(" ")
        if (hideBalances) {
            "$dateText • ••••"
        } else {
            val amt = if (isDedicated) {
                Formatters.formatIndianCurrency(displayVal, currencySymbol = currencySymbol)
            } else {
                Formatters.formatIndianCurrency(bar.balance, showPlusSign = true, currencySymbol = currencySymbol)
            }
            val net = if (bar.netChange != 0.0) {
                "Net " + Formatters.formatIndianCurrency(bar.netChange, showPlusSign = true, currencySymbol = currencySymbol)
            } else ""
            listOf(dateText, amt, net).filter { it.isNotBlank() }.joinToString(" • ")
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(slotWidth)
            .semantics {
                stateDescription = exactValueAnnouncement
                customActions = listOf(
                    CustomAccessibilityAction("Zoom in") {
                        onTap()
                        true
                    },
                    CustomAccessibilityAction("Open ledger for this period") {
                        onLongPress()
                        true
                    },
                    CustomAccessibilityAction("Read exact value") {
                        effectiveNoticeDispatcher.showNotice(
                            key = "BAR_EXACT_VALUE_${bar.key}",
                            message = exactValueAnnouncement
                        )
                        true
                    }
                )
            }
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
            .testTag("trend_bar_${bar.key}")
    ) {
        // (1) Zone 1: Reserved 14dp week-label zone (always 14dp high)
        Box(
            modifier = Modifier
                .width(slotWidth)
                .height(14.dp),
            contentAlignment = Alignment.Center
        ) {
            if (weekGroupLabel != null) {
                Text(
                    text = weekGroupLabel,
                    fontSize = 8.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center
                )
            }
        }

        // (2) Zone 2: Chart area (all bars rise upward from the single bottom axis)
        Box(
            modifier = Modifier
                .width(slotWidth)
                .height(chartAreaHeight)
        ) {
            if (isZero) {
                val nubHeight = 2.dp
                val nubTop = (chartAreaHeight - nubHeight).coerceAtLeast(0.dp)
                val corner = RoundedCornerShape(1.dp)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = nubTop)
                        .width(18.dp)
                        .height(nubHeight)
                        .clip(corner)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        .then(
                            if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, corner)
                            else if (bar.isCurrent) Modifier.border(1.5.dp, Amber400, corner)
                            else Modifier
                        )
                )
                if (!hideBalances) {
                    val labelTop = (nubTop - 16.dp).coerceAtLeast(0.dp)
                    Text(
                        text = "${currencySymbol}0",
                        fontSize = 9.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else TextMuted.copy(alpha = 0.6f),
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = labelTop)
                    )
                }
            } else {
                val barHeight = (displayVal.toFloat() * scale).dp.coerceAtLeast(2.dp)
                val barTop = (chartAreaHeight - barHeight).coerceAtLeast(0.dp)
                val corner = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = barTop)
                        .width(18.dp)
                        .height(barHeight)
                        .animatedBar(bar.key, isVertical = true)
                        .clip(corner)
                        .background(barColor)
                        .then(
                            if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, corner)
                            else if (bar.isCurrent) Modifier.border(1.5.dp, Amber400, corner)
                            else Modifier
                        )
                )
                if (!hideBalances) {
                    val labelTop = (barTop - 16.dp).coerceAtLeast(2.dp)
                    val labelText = if (isDedicated) {
                        Formatters.formatCompactCurrency(displayVal, currencySymbol = currencySymbol)
                    } else {
                        Formatters.formatCompactCurrency(bar.balance, currencySymbol = currencySymbol)
                    }
                    Text(
                        text = labelText,
                        fontSize = 9.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else TextMuted,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = labelTop)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // (4) Zone 4: Date-number zone (fixed height 16dp)
        Box(
            modifier = Modifier
                .width(slotWidth)
                .height(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = bar.dateLabel,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = if (bar.isCurrent || isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (bar.isCurrent || isSelected) MaterialTheme.colorScheme.primary else if (isZero) TextMuted.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center
            )
        }

        // (5) Zone 5: Weekday zone (fixed height 14dp)
        Box(
            modifier = Modifier
                .width(slotWidth)
                .height(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = bar.subLabel,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Normal,
                color = if (isZero) TextMuted.copy(alpha = 0.5f) else TextMuted,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center
            )
        }
    }
}
@Composable
private fun SixMonthsGroupedBarChart(
    monthlyList: List<MonthlyIncomeSpend>,
    hideBalances: Boolean,
    modifier: Modifier = Modifier
) {
    if (monthlyList.isEmpty() || monthlyList.all { it.income == 0.0 && it.spend == 0.0 }) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp),
            contentAlignment = Alignment.Center
        ) {
            val periodText = if (monthlyList.size > 1) "in the last ${monthlyList.size} months" else "for this month"
            Text(
                text = "No income or spend recorded $periodText",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val maxVal = monthlyList.maxOf { kotlin.math.max(it.income, it.spend) }.coerceAtLeast(100.0)
    val barWidth = when (monthlyList.size) {
        1 -> 24.dp
        2 -> 18.dp
        3 -> 14.dp
        4 -> 11.dp
        else -> 8.dp
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 130.dp)
            .padding(top = 8.dp),
        horizontalArrangement = if (monthlyList.size <= 3) Arrangement.SpaceEvenly else Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        monthlyList.forEach { m ->
            val incFraction = (m.income / maxVal).toFloat().coerceIn(0.04f, 1f)
            val spendFraction = (m.spend / maxVal).toFloat().coerceIn(0.04f, 1f)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = if (monthlyList.size <= 3) Modifier.width(72.dp) else Modifier.weight(1f)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.height(95.dp)
                ) {
                    // Income Bar (Green)
                    Box(
                        modifier = Modifier
                            .width(barWidth)
                            .height((95 * incFraction).dp)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(IncomeGreen)
                    )
                    // Spend Bar (Red)
                    Box(
                        modifier = Modifier
                            .width(barWidth)
                            .height((95 * spendFraction).dp)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(ExpenseRed)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = m.monthLabel,
                    fontSize = 10.sp,
                    fontWeight = if (m.isCurrentMonth) FontWeight.Bold else FontWeight.Normal,
                    color = if (m.isCurrentMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
