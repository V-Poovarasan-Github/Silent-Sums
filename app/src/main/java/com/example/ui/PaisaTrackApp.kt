package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import com.example.CrashLogger
import com.example.ui.common.CrashRecoveryBanner
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.AppDatabase
import com.example.data.datastore.AppSettingsRepository
import com.example.data.repository.FinanceRepository
import com.example.data.repository.MediaRepository
import com.example.domain.model.FilterState
import com.example.ui.commitment.CommitmentViewModel
import com.example.ui.common.AppBottomNav
import com.example.ui.common.LocalNoticeDispatcher
import com.example.ui.common.NavTab
import com.example.ui.common.SingleFlightSnackbarHost
import com.example.ui.common.rememberSingleFlightNoticeDispatcher
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.entry.EntryScreen
import com.example.ui.entry.EntryViewModel
import com.example.ui.ledger.LedgerScreen
import com.example.ui.ledger.LedgerViewModel
import com.example.ui.lists.ListsScreen
import com.example.ui.lists.ListsViewModel
import com.example.ui.lock.LockScreen
import com.example.ui.onboarding.OnboardingSourceScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.LocalOnThemeChange
import com.example.ui.theme.LocalThemeSetting
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.isAutoDarkTheme
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Pure resolution table for dark theme (Task RELEASE-2 / F14'):
 * - manual LIGHT -> Light (false)
 * - manual DARK -> Dark (true)
 * - AUTO + DEVICE -> isSystemDark
 * - AUTO + DAYLIGHT -> local hour in [6, 18) -> Light (false) else Dark (true)
 */
fun resolveDarkTheme(
    themeMode: String,
    autoThemeSource: String,
    isSystemDark: Boolean,
    currentHourOfDay: Int
): Boolean {
    return when (themeMode.uppercase()) {
        "LIGHT" -> false
        "DARK" -> true
        else -> when (autoThemeSource.uppercase()) {
            AppSettingsRepository.AUTO_THEME_SOURCE_DAYLIGHT -> currentHourOfDay !in 6 until 18
            else -> isSystemDark
        }
    }
}

@Composable
fun rememberResolvedDarkTheme(
    themeMode: String,
    autoThemeSource: String,
    isSystemDark: Boolean = isSystemInDarkTheme(),
    hourProvider: () -> Int = { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) },
    tickerIntervalMs: Long = 30_000L
): Boolean {
    var currentHour by remember { mutableStateOf(hourProvider()) }

    // Re-read local hour immediately on mode or source change
    remember(themeMode, autoThemeSource) {
        currentHour = hourProvider()
    }

    // Re-read local hour on app resume (ON_RESUME)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, hourProvider) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentHour = hourProvider()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // While AUTO + DAYLIGHT is active, run a <=60s ticker (30s) so crossing 06:00 / 18:00 flips without restart
    val isAutoMode = themeMode.isBlank() || themeMode.equals("AUTO", ignoreCase = true)
    val isDaylightSource = autoThemeSource.equals(AppSettingsRepository.AUTO_THEME_SOURCE_DAYLIGHT, ignoreCase = true)
    LaunchedEffect(isAutoMode, isDaylightSource, tickerIntervalMs, hourProvider) {
        if (isAutoMode && isDaylightSource) {
            currentHour = hourProvider()
            while (true) {
                kotlinx.coroutines.delay(tickerIntervalMs)
                currentHour = hourProvider()
            }
        }
    }

    return resolveDarkTheme(
        themeMode = themeMode,
        autoThemeSource = autoThemeSource,
        isSystemDark = isSystemDark,
        currentHourOfDay = currentHour
    )
}

sealed class AppDestination {
    data object Dashboard : AppDestination()
    data object Expenses : AppDestination()
    data class Lists(val initialTab: com.example.ui.lists.ListsTab = com.example.ui.lists.ListsTab.CATEGORIES) : AppDestination()
    data object Settings : AppDestination()
    data class Entry(
        val transactionId: Long? = null,
        val initialSourceId: Long? = null,
        val initialCategoryId: Long? = null,
        val initialType: String? = null,
        val initialToAccountId: Long? = null,
        val initialAmountText: String? = null
    ) : AppDestination()
}

@Composable
fun PaisaTrackApp(
    activity: FragmentActivity,
    database: AppDatabase,
    repository: FinanceRepository,
    settingsRepository: AppSettingsRepository,
    initialOpenDuePopup: Boolean = false,
    initialShortcutAction: String? = null,
    isUnlocked: Boolean = false,
    onUnlocked: () -> Unit = {},
    onShowBiometricPrompt: ((activity: FragmentActivity, onSuccess: () -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val settings by settingsRepository.settingsFlow.collectAsState(initial = com.example.data.datastore.AppSettings())
    var pendingShortcutAction by rememberSaveable { mutableStateOf(initialShortcutAction) }
    var pendingOpenDuePopup by rememberSaveable { mutableStateOf(initialOpenDuePopup) }

    LaunchedEffect(initialShortcutAction) {
        if (initialShortcutAction != null) {
            pendingShortcutAction = initialShortcutAction
        }
    }
    LaunchedEffect(initialOpenDuePopup) {
        if (initialOpenDuePopup) {
            pendingOpenDuePopup = true
        }
    }

    // First launch initial check: safely auto-restores from device CSV/auto-save if present, preserving user data
    LaunchedEffect(Unit) {
        repository.checkAndRestoreOrSeed(activity.applicationContext, settingsRepository)
    }

    val darkTheme = rememberResolvedDarkTheme(
        themeMode = settings.theme,
        autoThemeSource = settings.autoThemeSource
    )

    val scope = rememberCoroutineScope()
    val appNoticeDispatcher = rememberSingleFlightNoticeDispatcher()
    val onThemeChange: (String) -> Unit = remember(settingsRepository) {
        { newTheme: String ->
            scope.launch {
                settingsRepository.setTheme(newTheme)
            }
        }
    }

    // 1.4 Recents and screenshot privacy: keep window FLAG_SECURE synced with lockEnabled || hideBalances
    androidx.compose.runtime.SideEffect {
        com.example.MainActivity.updateWindowSecureFlag(
            window = activity.window,
            lockEnabled = settings.lockEnabled,
            hideBalances = settings.hideBalances
        )
    }

    CompositionLocalProvider(
        LocalThemeSetting provides (if (settings.theme.isBlank()) "AUTO" else settings.theme),
        LocalOnThemeChange provides onThemeChange,
        LocalNoticeDispatcher provides appNoticeDispatcher
    ) {
        MyApplicationTheme(darkTheme = darkTheme) {
        // App Lock Screen check: never gate demo/sample mode entry; only gate real ledger when locked
        if (settings.lockEnabled && !settings.demoMode && !isUnlocked) {
            val handleBiometricUnlocked: () -> Unit = {
                scope.launch {
                    settingsRepository.resetPinLockout()
                }
                onUnlocked()
            }
            LockScreen(
                correctPin = settings.pinHash,
                settingsRepository = settingsRepository,
                onUnlocked = onUnlocked,
                onBiometricClick = {
                    if (onShowBiometricPrompt != null) {
                        onShowBiometricPrompt(activity, handleBiometricUnlocked)
                    } else {
                        showBiometricPrompt(
                            activity = activity,
                            onSuccess = handleBiometricUnlocked
                        )
                    }
                }
            )
            return@MyApplicationTheme
        }

        // Onboarding gate (R1): Gate after lock check, before dashboard
        if (!settings.onboardingCompleted) {
            var isCheckingData by remember { mutableStateOf(true) }
            var existingDataFound by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                val hasData = repository.hasExistingUserData()
                if (hasData) {
                    settingsRepository.setOnboardingCompleted(true)
                    existingDataFound = true
                }
                isCheckingData = false
            }

            if (isCheckingData) {
                return@MyApplicationTheme
            }

            if (!existingDataFound && !settings.onboardingCompleted) {
                OnboardingSourceScreen(
                    repository = repository,
                    settingsRepository = settingsRepository,
                    onOnboardingComplete = {
                        scope.launch {
                            settingsRepository.setOnboardingCompleted(true)
                        }
                    }
                )
                return@MyApplicationTheme
            }
        }

        // Main App State
        val initialDest = remember(initialShortcutAction) {
            when (initialShortcutAction) {
                "add_expense" -> AppDestination.Entry(initialType = "EXPENSE")
                "add_income" -> AppDestination.Entry(initialType = "INCOME")
                else -> AppDestination.Dashboard
            }
        }
        var currentDestination by remember { mutableStateOf<AppDestination>(initialDest) }
        var openDuePopup by rememberSaveable { mutableStateOf(initialOpenDuePopup) }

        LaunchedEffect(isUnlocked, pendingShortcutAction, pendingOpenDuePopup, settings.lockEnabled) {
            if (!settings.lockEnabled || isUnlocked) {
                if (pendingShortcutAction != null) {
                    when (pendingShortcutAction) {
                        "add_expense" -> {
                            currentDestination = AppDestination.Entry(initialType = "EXPENSE")
                        }
                        "add_income" -> {
                            currentDestination = AppDestination.Entry(initialType = "INCOME")
                        }
                        "view_dues" -> {
                            openDuePopup = true
                            currentDestination = AppDestination.Dashboard
                        }
                    }
                    pendingShortcutAction = null
                } else if (pendingOpenDuePopup) {
                    openDuePopup = true
                    currentDestination = AppDestination.Dashboard
                    pendingOpenDuePopup = false
                }
            }
        }

        // Shared Filter State Holder (single owner across Dashboard & Ledger)
        val sharedFilterStateHolder = remember { com.example.domain.model.SharedFilterStateHolder() }

        // ViewModels
        val mediaRepository = remember(database) { MediaRepository(activity.applicationContext, database) }
        val dashboardViewModel = remember { DashboardViewModel(repository, settingsRepository, sharedFilterStateHolder) }
        val commitmentViewModel = remember { CommitmentViewModel(repository) }
        val ledgerViewModel = remember { LedgerViewModel(repository, settingsRepository, mediaRepository = mediaRepository, sharedFilterStateHolder = sharedFilterStateHolder) }
        val listsViewModel = remember { ListsViewModel(repository, settingsRepository = settingsRepository) }
        val settingsViewModel = remember { SettingsViewModel(repository, settingsRepository, database) }

        // R1: Check for crash logs on app start
        val crashLogText by remember { mutableStateOf(CrashLogger.checkCrashLog(activity.applicationContext)) }

        // R4: Check SAF grant access
        LaunchedEffect(Unit) {
            settingsViewModel.checkSafGrantAsync(activity.applicationContext)
        }

        val currentNavTab = when (currentDestination) {
            AppDestination.Dashboard -> NavTab.DASHBOARD
            AppDestination.Expenses -> NavTab.EXPENSES
            is AppDestination.Lists -> NavTab.LISTS
            AppDestination.Settings -> NavTab.SETTINGS
            is AppDestination.Entry -> null
        }

        LaunchedEffect(currentDestination) {
            when (currentDestination) {
                AppDestination.Dashboard,
                is AppDestination.Lists,
                AppDestination.Settings -> {
                    sharedFilterStateHolder.clearDrillOverlay()
                }
                AppDestination.Expenses,
                is AppDestination.Entry -> {
                    // G2: drillOverlay survives while on Expenses and across Expenses <-> Entry round-trips
                }
            }
        }

        BackHandler(enabled = currentDestination !is AppDestination.Dashboard) {
            if (currentDestination is AppDestination.Entry) {
                currentDestination = AppDestination.Expenses
            } else {
                sharedFilterStateHolder.clearDrillOverlay()
                currentDestination = AppDestination.Dashboard
            }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            snackbarHost = { SingleFlightSnackbarHost(appNoticeDispatcher) },
            topBar = {
                if (crashLogText != null) {
                    CrashRecoveryBanner(
                        crashLog = crashLogText!!,
                        onShareLog = { log ->
                            CrashLogger.shareCrashLog(activity, log)
                        },
                        modifier = Modifier.statusBarsPadding()
                    )
                }
            },
            bottomBar = {
                if (currentNavTab != null) {
                    AppBottomNav(
                        currentTab = currentNavTab,
                        onTabSelected = { tab ->
                            if (tab != NavTab.EXPENSES) {
                                sharedFilterStateHolder.clearDrillOverlay()
                            }
                            currentDestination = when (tab) {
                                NavTab.DASHBOARD -> AppDestination.Dashboard
                                NavTab.EXPENSES -> AppDestination.Expenses
                                NavTab.LISTS -> AppDestination.Lists()
                                NavTab.SETTINGS -> AppDestination.Settings
                            }
                        }
                    )
                }
            },
            modifier = modifier.fillMaxSize()
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                AnimatedContent(
                    targetState = currentDestination,
                    transitionSpec = {
                        if (targetState is AppDestination.Entry) {
                            (slideInHorizontally(initialOffsetX = { it / 3 }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)))
                                .togetherWith(slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = tween(250)) + fadeOut(animationSpec = tween(200)))
                        } else if (initialState is AppDestination.Entry) {
                            (slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = tween(250)) + fadeIn(animationSpec = tween(250)))
                                .togetherWith(slideOutHorizontally(targetOffsetX = { it / 3 }, animationSpec = tween(250)) + fadeOut(animationSpec = tween(200)))
                        } else {
                            (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.98f, animationSpec = tween(220)))
                                .togetherWith(fadeOut(animationSpec = tween(180)))
                        }
                    },
                    label = "app_destination_transition"
                ) { dest ->
                    when (dest) {
                        AppDestination.Dashboard -> {
                            DashboardScreen(
                                viewModel = dashboardViewModel,
                                commitmentViewModel = commitmentViewModel,
                                showCommitmentsPopupOnStart = openDuePopup,
                                onCommitmentsPopupDismissed = { openDuePopup = false },
                                onNavigateToExpenses = { catId, accId ->
                                    dashboardViewModel.applyHandoff(
                                        com.example.domain.model.FilterHandoffPatch(
                                            selectedCategoryId = catId,
                                            selectedCategoryIds = catId?.let { setOf(it) },
                                            selectedAccountId = accId,
                                            selectedAccountIds = accId?.let { setOf(it) }
                                        )
                                    )
                                    currentDestination = AppDestination.Expenses
                                },
                                onNavigateToExpensesWithFilter = { filterState ->
                                    dashboardViewModel.applyDrillFilter(filterState)
                                    currentDestination = AppDestination.Expenses
                                },
                                onNavigateToEntry = { type ->
                                    currentDestination = AppDestination.Entry(initialType = type)
                                },
                                onNavigateToTransferPrefill = { fromId, toId, amountText ->
                                    currentDestination = AppDestination.Entry(
                                        initialSourceId = fromId,
                                        initialToAccountId = toId,
                                        initialType = "TRANSFER",
                                        initialAmountText = amountText
                                    )
                                },
                                onNavigateToSettings = {
                                    currentDestination = AppDestination.Settings
                                }
                            )
                        }

                        AppDestination.Expenses -> {
                            LedgerScreen(
                                viewModel = ledgerViewModel,
                                onAddClick = {
                                    val filter = ledgerViewModel.filter.value
                                    currentDestination = AppDestination.Entry(
                                        transactionId = null,
                                        initialSourceId = filter.selectedAccountId,
                                        initialCategoryId = filter.selectedCategoryId
                                    )
                                },
                                onTransactionClick = { txId ->
                                    currentDestination = AppDestination.Entry(txId)
                                },
                                noticeDispatcher = appNoticeDispatcher
                            )
                        }

                        is AppDestination.Lists -> {
                            ListsScreen(
                                viewModel = listsViewModel,
                                initialTab = dest.initialTab
                            )
                        }

                        AppDestination.Settings -> {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                noticeDispatcher = appNoticeDispatcher
                            )
                        }

                        is AppDestination.Entry -> {
                            val entryViewModel = remember(
                                dest.transactionId,
                                dest.initialSourceId,
                                dest.initialCategoryId,
                                dest.initialType,
                                dest.initialToAccountId,
                                dest.initialAmountText
                            ) {
                                EntryViewModel(
                                    repository = repository,
                                    editTransactionId = dest.transactionId,
                                    initialSourceId = dest.initialSourceId,
                                    initialCategoryId = dest.initialCategoryId,
                                    initialType = dest.initialType,
                                    settingsRepository = settingsRepository,
                                    mediaRepository = mediaRepository,
                                    initialToAccountId = dest.initialToAccountId,
                                    initialAmountText = dest.initialAmountText
                                )
                            }
                            EntryScreen(
                                viewModel = entryViewModel,
                                onNavigateBack = {
                                    currentDestination = AppDestination.Expenses
                                },
                                onAddSource = {
                                    sharedFilterStateHolder.clearDrillOverlay()
                                    currentDestination = AppDestination.Lists(com.example.ui.lists.ListsTab.ACCOUNTS)
                                },
                                hapticsEnabled = settings.soundHapticsEnabled,
                                hideBalances = settings.hideBalances,
                                noticeDispatcher = appNoticeDispatcher
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

internal fun showBiometricPrompt(
    activity: FragmentActivity,
    onSuccess: () -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val biometricPrompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }
        }
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock SilentSums")
        .setSubtitle("Authenticate using your fingerprint")
        .setNegativeButtonText("Use PIN")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .build()

    try {
        biometricPrompt.authenticate(promptInfo)
    } catch (_: Exception) {}
}
