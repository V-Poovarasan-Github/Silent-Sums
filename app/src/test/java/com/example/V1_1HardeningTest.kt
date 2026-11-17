package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.datastore.AppSettingsRepository
import com.example.data.repository.FinanceRepository
import com.example.ui.dashboard.DashboardIntroTracker
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class V1_1HardeningTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: FinanceRepository
    private lateinit var settingsRepository: AppSettingsRepository

    @Before
    fun setUp() = runBlocking {
        com.example.security.PinHasher.dispatcher = kotlinx.coroutines.Dispatchers.Unconfined
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(database)
        settingsRepository = AppSettingsRepository(context)
        settingsRepository.clearSettings()
        DashboardIntroTracker.resetForTests()
    }

    @After
    fun tearDown() = runBlocking {
        ShadowLooper.idleMainLooper()
        database.close()
    }

    // =========================================================================
    // ITEM 1: Auto-save visibility (Settings caption + one-time Dashboard dialog)
    // =========================================================================

    @Test
    fun item1_settingsCaptionShowsRealPath_defaultCustomAndInvalidFallback() {
        runBlocking {
            val defaultSettings = settingsRepository.settingsFlow.first()
            assertTrue("autoBackupEnabled must stay true by default", defaultSettings.autoBackupEnabled)
            assertTrue("autoSaveOnClose must stay true by default", defaultSettings.autoSaveOnClose)

            val vm = SettingsViewModel(repository, settingsRepository, database)
            try {
                composeTestRule.setContent {
                    MyApplicationTheme {
                        SettingsScreen(viewModel = vm)
                    }
                }
                composeTestRule.waitForIdle()

                // 1. Default path: Downloads/SilentSums/auto/backup.csv
                val captionNode = composeTestRule.onNodeWithTag("settings_auto_save_disclosure_caption", useUnmergedTree = true)
                captionNode.performScrollTo()
                captionNode.assertIsDisplayed()
                captionNode.assertTextContains(
                    "Saves a readable CSV of all your transactions to Downloads/SilentSums/auto/backup.csv. Anyone who can open your files can read it."
                )

                // 2. Valid custom folder (writable local directory URI)
                val customDir = File(context.cacheDir, "CustomVaultFolder").apply { mkdirs() }
                settingsRepository.setAutoSaveTreeUri(Uri.fromFile(customDir).toString())
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()

                captionNode.assertTextContains(
                    "Saves a readable CSV of all your transactions to Internal storage/CustomVaultFolder/auto/backup.csv. Anyone who can open your files can read it."
                )

                // 3. Invalid / unresolvable custom folder falls back to Downloads/SilentSums/auto/backup.csv
                settingsRepository.setAutoSaveTreeUri("content://com.android.externalstorage.documents/tree/revoked_nonexistent_folder")
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()

                captionNode.assertTextContains(
                    "Saves a readable CSV of all your transactions to Downloads/SilentSums/auto/backup.csv. Anyone who can open your files can read it."
                )
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    @Test
    fun item1_dashboardOneTimeDialog_showsOnceAfterOnboardingAndPersistsFlag() = runBlocking {
        settingsRepository.setOnboardingCompleted(true)
        assertFalse(settingsRepository.settingsFlow.first().hasShownAutoBackupDisclosure)

        var navigatedToSettings = false
        val vm = DashboardViewModel(repository, settingsRepository)
        try {
            composeTestRule.setContent {
                MyApplicationTheme {
                    DashboardScreen(
                        viewModel = vm,
                        onNavigateToExpenses = { _, _ -> },
                        onNavigateToSettings = { navigatedToSettings = true }
                    )
                }
            }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            // Dialog appears with exact title, body, and 2 buttons
            composeTestRule.onNodeWithTag("auto_backup_disclosure_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithText("Your data is backed up to a CSV").assertIsDisplayed()
            composeTestRule.onNodeWithTag("auto_backup_disclosure_dialog_body").assertTextContains(
                "SilentSums keeps a readable copy of your transactions in Downloads/SilentSums/auto/backup.csv, updated daily and when you leave the app. It is a plain file, so anyone who can open your files can read it. Keep it on, choose another folder, or turn it off in Settings."
            )
            composeTestRule.onNodeWithTag("auto_backup_disclosure_keep_on_btn").assertIsDisplayed()
            composeTestRule.onNodeWithTag("auto_backup_disclosure_settings_btn").assertIsDisplayed()

            // Dismiss via "Keep on" -> dialog disappears and flag persists in DataStore
            composeTestRule.onNodeWithTag("auto_backup_disclosure_keep_on_btn").performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("auto_backup_disclosure_dialog").assertDoesNotExist()
            assertTrue(
                "hasShownAutoBackupDisclosure must persist as true after dismissal",
                settingsRepository.settingsFlow.first().hasShownAutoBackupDisclosure
            )
            assertFalse("Keep on must not navigate to Settings", navigatedToSettings)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun item1_dashboardOneTimeDialog_settingsButtonOpensSettingsAndPersistsFlag() = runBlocking {
        settingsRepository.setOnboardingCompleted(true)
        settingsRepository.setHasShownAutoBackupDisclosure(false)

        var navigatedToSettings = false
        val vm = DashboardViewModel(repository, settingsRepository)
        try {
            composeTestRule.setContent {
                MyApplicationTheme {
                    DashboardScreen(
                        viewModel = vm,
                        onNavigateToExpenses = { _, _ -> },
                        onNavigateToSettings = { navigatedToSettings = true }
                    )
                }
            }
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("auto_backup_disclosure_settings_btn").performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("auto_backup_disclosure_dialog").assertDoesNotExist()
            assertTrue("Tapping Settings button must navigate to Settings", navigatedToSettings)
            assertTrue(
                "hasShownAutoBackupDisclosure must persist as true after tapping Settings",
                settingsRepository.settingsFlow.first().hasShownAutoBackupDisclosure
            )
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    // =========================================================================
    // ITEM 2: Reminder notifications lock-screen privacy & Privacy Mode masking
    // =========================================================================

    @Test
    fun item2_dueReminderNotification_hasPrivateVisibilityAndGenericPublicVersion() = runBlocking {
        val accountId = repository.insertAccount(
            com.example.data.model.Account(name = "HDFC Bank", accountType = "BANK", openingBalance = 5000.0)
        )
        val nowMs = System.currentTimeMillis()
        val tomorrowCal = java.util.Calendar.getInstance().apply {
            timeInMillis = nowMs
            add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        repository.insertCommitment(
            com.example.data.model.Commitment(
                id = 201L,
                type = "RECURRING",
                name = "Car EMI",
                amount = 12500.0,
                direction = "OUTGOING",
                dayOfMonth = tomorrowCal.get(java.util.Calendar.DAY_OF_MONTH),
                accountId = accountId,
                createdAt = nowMs - 86_400_000L * 5,
                active = true
            )
        )

        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notifManager.cancelAll()

        com.example.worker.DueReminderWorker.checkAndNotify(context, database, settingsRepository)

        val channel = notifManager.getNotificationChannel(com.example.worker.DueReminderWorker.CHANNEL_ID)
        org.junit.Assert.assertNotNull(channel)
        assertEquals(android.app.Notification.VISIBILITY_PRIVATE, channel.lockscreenVisibility)

        val notifications = org.robolectric.Shadows.shadowOf(notifManager).allNotifications
        assertEquals(1, notifications.size)
        val notif = notifications.single()
        assertEquals(android.app.Notification.VISIBILITY_PRIVATE, notif.visibility)

        val pub = notif.publicVersion
        org.junit.Assert.assertNotNull("publicVersion must be set on reminder notifications", pub)
        val pubTitle = pub.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
        val pubText = pub.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
        assertEquals("SilentSums", pubTitle)
        assertEquals("You have a payment or IOU due.", pubText)
        assertFalse("Public notification must not leak amount", pubText?.contains("12500") == true)
        assertFalse("Public notification must not leak commitment name", pubText?.contains("Car EMI") == true)
    }

    @Test
    fun item2_dueReminderNotification_masksAmountsAndShortByWhenPrivacyModeOn() = runBlocking {
        settingsRepository.setHideBalances(true)
        val accountId = repository.insertAccount(
            com.example.data.model.Account(name = "HDFC Bank", accountType = "BANK", openingBalance = 2000.0)
        )
        val nowMs = System.currentTimeMillis()
        val tomorrowCal = java.util.Calendar.getInstance().apply {
            timeInMillis = nowMs
            add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        repository.insertCommitment(
            com.example.data.model.Commitment(
                id = 202L,
                type = "RECURRING",
                name = "Car EMI",
                amount = 12500.0,
                direction = "OUTGOING",
                dayOfMonth = tomorrowCal.get(java.util.Calendar.DAY_OF_MONTH),
                accountId = accountId,
                createdAt = nowMs - 86_400_000L * 5,
                active = true
            )
        )

        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notifManager.cancelAll()

        com.example.worker.DueReminderWorker.checkAndNotify(context, database, settingsRepository)

        val notifications = org.robolectric.Shadows.shadowOf(notifManager).allNotifications
        assertEquals(1, notifications.size)
        val notif = notifications.single()
        val privateText = notif.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""

        assertEquals(
            "₹•••• Car EMI due tomorrow from HDFC Bank (Short by ₹••••)",
            privateText
        )
        assertFalse("Private notification must not contain raw amount when hideBalances is true", privateText.contains("12500"))
        assertFalse("Private notification must not contain raw short-by diff when hideBalances is true", privateText.contains("10500"))
    }

    // =========================================================================
    // ITEM 3: Reminder permission and the permission flow
    // =========================================================================

    @Test
    fun item3_firstCommitmentSave_showsRationaleDialogOnceAndPersistsAskedFlag() = runBlocking {
        assertFalse(settingsRepository.settingsFlow.first().hasAskedCommitmentNotificationPermission)
        val accounts = listOf(com.example.data.model.Account(id = 1L, name = "HDFC Bank", accountType = "BANK"))
        var saveCount = 0

        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.commitment.AddEditCommitmentSheet(
                    commitment = null,
                    accounts = accounts,
                    categories = emptyList(),
                    onDismiss = {},
                    onSave = { saveCount++ }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("commitment_name_input").performClick()
        composeTestRule.onNodeWithTag("commitment_name_input").performTextInput("WiFi Bill")
        composeTestRule.onNodeWithTag("commitment_amount_input").performTextInput("999")
        composeTestRule.onNodeWithTag("save_commitment_btn").performClick()
        ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        assertEquals(1, saveCount)

        // Rationale dialog is shown with exact Settings text
        composeTestRule.onNodeWithTag("commitment_notif_rationale_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enable Due Reminders").assertIsDisplayed()
        composeTestRule.onNodeWithTag("commitment_notif_rationale_text").assertTextContains(
            "Allow notifications so SilentSums can alert you when recurring bills or dues are approaching."
        )

        // Tap Skip -> dialog closes and flag is persisted
        composeTestRule.onNodeWithTag("commitment_notif_rationale_skip_btn").performClick()
        ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("commitment_notif_rationale_dialog").assertDoesNotExist()
        assertTrue(
            "hasAskedCommitmentNotificationPermission must be persisted as true after first commitment save",
            settingsRepository.settingsFlow.first().hasAskedCommitmentNotificationPermission
        )
        Unit
    }

    @Test
    fun item3_settingsScreen_showsBlockedNotificationsLineAndOpensAppNotificationSettings() = runBlocking {
        settingsRepository.setDueRemindersEnabled(true)
        val vm = SettingsViewModel(repository, settingsRepository, database)
        try {
            composeTestRule.setContent {
                MyApplicationTheme {
                    SettingsScreen(viewModel = vm)
                }
            }
            composeTestRule.waitForIdle()

            val blockedNode = composeTestRule.onNodeWithTag("due_reminders_blocked_caption", useUnmergedTree = true)
            blockedNode.performScrollTo()
            blockedNode.assertIsDisplayed()
            blockedNode.assertTextContains("Notifications are blocked, so reminders will not appear. Tap to allow.")

            blockedNode.performClick()
            composeTestRule.waitForIdle()

            val app = ApplicationProvider.getApplicationContext<android.app.Application>()
            val startedIntent = org.robolectric.Shadows.shadowOf(app).nextStartedActivity
            org.junit.Assert.assertNotNull("Tapping blocked caption must launch system notification settings intent", startedIntent)
            assertEquals(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS, startedIntent.action)
            assertEquals(context.packageName, startedIntent.getStringExtra(android.provider.Settings.EXTRA_APP_PACKAGE))
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    // =========================================================================
    // ITEM 4: Reminder timing (due today, due tomorrow, overdue, boundary, dedup)
    // =========================================================================

    @Test
    fun item4_reminderTimingAndDeduplication_fakeClockDueTodayTomorrowOverdueAndBoundary() = runBlocking {
        val accountId = repository.insertAccount(
            com.example.data.model.Account(name = "Main Bank", accountType = "BANK", openingBalance = 50000.0)
        )

        // Fake clock: 2026-06-15 at 14:00 (2 PM)
        val fakeNow = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.JUNE, 15, 14, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todayStart = java.util.Calendar.getInstance().apply {
            timeInMillis = fakeNow
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val tomorrowStart = todayStart + 86_400_000L
        val tomorrowEnd = tomorrowStart + 86_400_000L

        // 1. Commitment due earlier today (9 AM today, while fakeNow is 2 PM today) -> "due today"
        val dueToday9Am = todayStart + 9L * 3_600_000L
        repository.insertCommitment(
            com.example.data.model.Commitment(
                id = 401L,
                type = "IOU",
                name = "Rahul",
                amount = 500.0,
                direction = "OUTGOING",
                dueDate = dueToday9Am,
                accountId = accountId,
                createdAt = todayStart - 86_400_000L * 3,
                active = true
            )
        )

        // 2. Commitment due tomorrow (day 16 recurring) -> "due tomorrow"
        repository.insertCommitment(
            com.example.data.model.Commitment(
                id = 402L,
                type = "RECURRING",
                name = "Internet Bill",
                amount = 1000.0,
                direction = "OUTGOING",
                dayOfMonth = 16,
                accountId = accountId,
                createdAt = todayStart - 86_400_000L * 5,
                active = true
            )
        )

        // 3. Commitment overdue (due yesterday) -> "overdue"
        repository.insertCommitment(
            com.example.data.model.Commitment(
                id = 403L,
                type = "IOU",
                name = "Priya",
                amount = 800.0,
                direction = "INCOMING",
                dueDate = todayStart - 3_600_000L,
                accountId = accountId,
                createdAt = todayStart - 86_400_000L * 5,
                active = true
            )
        )

        // 4. Boundary commitment due at exact tomorrowEnd -> NOT "due tomorrow"
        repository.insertCommitment(
            com.example.data.model.Commitment(
                id = 404L,
                type = "IOU",
                name = "BoundaryFuture",
                amount = 1200.0,
                direction = "OUTGOING",
                dueDate = tomorrowEnd,
                accountId = accountId,
                createdAt = todayStart - 86_400_000L * 5,
                active = true
            )
        )

        // Seed an old marker (10 days old) to verify >7-day pruning
        settingsRepository.setReminderLastNotified(setOf("999:2026-06-01:due_today"))

        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notifManager.cancelAll()

        // First call on 2026-06-15 -> fires 3 notifications (401 due today, 402 due tomorrow, 403 overdue; 404 excluded)
        com.example.worker.DueReminderWorker.checkAndNotify(context, database, settingsRepository, now = fakeNow)

        val firstRunNotifs = org.robolectric.Shadows.shadowOf(notifManager).allNotifications
        val firstRunBodies = firstRunNotifs.map {
            it.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
        }
        assertEquals("Must emit 3 notifications (today, tomorrow, overdue) and exclude tomorrowEnd boundary", 3, firstRunNotifs.size)
        assertTrue("401 (9 AM today) must notify as 'due today', got: $firstRunBodies", firstRunBodies.any { it.contains("Rahul") && it.contains("due today") })
        assertTrue("402 must notify as 'due tomorrow', got: $firstRunBodies", firstRunBodies.any { it.contains("Internet Bill") && it.contains("due tomorrow") })
        assertTrue("403 must notify as 'Overdue', got: $firstRunBodies", firstRunBodies.any { it.contains("Priya") && it.contains("Overdue") })
        assertFalse("404 at exact tomorrowEnd must NOT notify", firstRunBodies.any { it.contains("BoundaryFuture") })

        // Verify old marker >7 days was pruned and today's 3 markers were persisted
        val persistedMarkers = settingsRepository.settingsFlow.first().reminderLastNotified
        assertFalse("Marker older than 7 days must be pruned", persistedMarkers.any { it.startsWith("999:") })
        assertTrue(persistedMarkers.contains("401:2026-06-15:due_today"))
        assertTrue(persistedMarkers.contains("402:2026-06-15:due_tomorrow"))
        assertTrue(persistedMarkers.contains("403:2026-06-15:overdue"))

        // Repeat call on the same calendar day -> 0 new notifications
        notifManager.cancelAll()
        com.example.worker.DueReminderWorker.checkAndNotify(
            context,
            database,
            settingsRepository,
            now = fakeNow + 3_600_000L
        )
        assertEquals(
            "Repeat call on the same calendar day must not re-fire notifications",
            0,
            org.robolectric.Shadows.shadowOf(notifManager).allNotifications.size
        )

        // Advance clock to next calendar day (2026-06-16) -> notifies again!
        notifManager.cancelAll()
        val nextDayNow = fakeNow + 86_400_000L
        com.example.worker.DueReminderWorker.checkAndNotify(
            context,
            database,
            settingsRepository,
            now = nextDayNow
        )
        val nextDayNotifs = org.robolectric.Shadows.shadowOf(notifManager).allNotifications
        assertTrue("Next calendar day must notify again", nextDayNotifs.isNotEmpty())
    }

    // =========================================================================
    // ITEM 6: Thread-safe date formatting in CommitmentCalculator
    // =========================================================================

    @Test
    fun item6_commitmentCalculator_threadSafeConcurrentDateFormatting() {
        val now = 1718450400000L // 2024-06-15
        val futureDue = now + 20L * 86_400_000L
        val commitments = (1..20).map { i ->
            com.example.data.model.Commitment(
                id = i.toLong(),
                type = if (i % 2 == 0) "RECURRING" else "IOU",
                name = "Item $i",
                amount = 100.0 * i,
                direction = "OUTGOING",
                dayOfMonth = (i % 28) + 1,
                dueDate = futureDue + i * 86_400_000L,
                createdAt = now - 30L * 86_400_000L
            )
        }
        val expectedCycle = com.example.domain.commitment.CommitmentCalculator.getCurrentCycle(now)
        val expectedLabel = com.example.domain.commitment.CommitmentCalculator.getDueLabel(futureDue, now)
        val expectedGroups = com.example.domain.commitment.CommitmentCalculator.groupCommitments(commitments, now)

        val pool = java.util.concurrent.Executors.newFixedThreadPool(8)
        try {
            val futures = (1..64).map {
                pool.submit<Boolean> {
                    for (iter in 1..25) {
                        val c = com.example.domain.commitment.CommitmentCalculator.getCurrentCycle(now)
                        val l = com.example.domain.commitment.CommitmentCalculator.getDueLabel(futureDue, now)
                        val g = com.example.domain.commitment.CommitmentCalculator.groupCommitments(commitments, now)
                        if (c != expectedCycle || l != expectedLabel || g.later.map { it.first } != expectedGroups.later.map { it.first }) {
                            return@submit false
                        }
                    }
                    true
                }
            }
            for (f in futures) {
                assertTrue("Concurrent CommitmentCalculator date formatting must be thread-safe and byte-identical", f.get())
            }
        } finally {
            pool.shutdownNow()
        }
    }

    // =========================================================================
    // ITEM 7: PIN lockout cannot be shortened by changing the clock
    // =========================================================================

    @Test
    fun item7_pinLockout_clockMovedBackAfterRebootFullDuration_normalRebootRemaining_legacyV100Unchanged() = runBlocking {
        val startElapsed = 100_000L
        val duration = 30_000L
        val endElapsed = startElapsed + duration
        val startWall = 1_700_000_000_000L
        val endWall = startWall + duration

        // 1. Reboot detected (currentElapsed < startElapsed) AND clock moved backward (currentWall < startWall) -> full duration
        val clockMovedBackRemaining = com.example.security.PinHasher.computeRemainingLockoutMs(
            lockoutStartElapsedMs = startElapsed,
            lockoutEndElapsedMs = endElapsed,
            lockoutEndWallMs = endWall,
            currentElapsedMs = 5_000L, // rebooted
            currentWallMs = startWall - 3_600_000L, // moved back 1 hour
            lockoutStartWallMs = startWall
        )
        assertEquals(
            "Moving wall clock backward after reboot must treat full lockout duration as remaining",
            duration,
            clockMovedBackRemaining
        )

        // 2. Normal reboot without clock tampering -> remaining wall-clock time
        val normalRebootRemaining = com.example.security.PinHasher.computeRemainingLockoutMs(
            lockoutStartElapsedMs = startElapsed,
            lockoutEndElapsedMs = endElapsed,
            lockoutEndWallMs = endWall,
            currentElapsedMs = 5_000L, // rebooted
            currentWallMs = startWall + 12_000L, // 12s elapsed since lockout started
            lockoutStartWallMs = startWall
        )
        assertEquals(
            "Normal reboot must return remaining wall-clock lockout time",
            18_000L,
            normalRebootRemaining
        )

        // 3. Existing persisted lockout from v1.0.0 with pinLockoutStartWallMs = 0L behaves exactly as before
        val legacyV100Remaining = com.example.security.PinHasher.computeRemainingLockoutMs(
            lockoutStartElapsedMs = startElapsed,
            lockoutEndElapsedMs = endElapsed,
            lockoutEndWallMs = endWall,
            currentElapsedMs = 5_000L, // rebooted
            currentWallMs = startWall + 10_000L,
            lockoutStartWallMs = 0L
        )
        assertEquals(
            "Legacy v1.0.0 lockout with pinLockoutStartWallMs = 0 must behave identically to before",
            20_000L,
            legacyV100Remaining
        )

        // 4. Verify AppSettingsRepository persists pinLockoutStartWallMs on 5th failed attempt and resets on resetPinLockout
        for (i in 1..5) {
            settingsRepository.recordPinFailure(currentElapsedMs = startElapsed, currentWallMs = startWall)
        }
        val lockedSettings = settingsRepository.settingsFlow.first()
        assertEquals(startWall, lockedSettings.pinLockoutStartWallMs)
        assertEquals(endWall, lockedSettings.pinLockoutEndWallMs)

        settingsRepository.resetPinLockout()
        assertEquals(0L, settingsRepository.settingsFlow.first().pinLockoutStartWallMs)
    }

    // =========================================================================
    // ITEM 10: Money rounding safety net (test only, no schema change)
    // =========================================================================

    @Test
    fun item10_moneyRoundingSafetyNet_1000x0_10_and_10000x1_10() {
        val now = 1_718_450_400_000L
        val account = com.example.data.model.Account(
            id = 1L,
            name = "Bank",
            openingBalance = 0.0,
            accountType = "BANK"
        )

        // 1,000 transactions of 0.10 -> ₹100.00
        val tx1000 = (1..1_000).map { i ->
            com.example.data.model.TransactionEntity(
                id = i.toLong(),
                date = now - (1_000 - i) * 1_000L,
                type = "INCOME",
                amount = 0.10,
                toAccountId = 1L
            )
        }
        val accountBal1000 = com.example.domain.calculator.LedgerCalculator
            .computeAccountBalances(listOf(account), tx1000)
            .single()
            .currentBalance
        val currentBal1000 = com.example.domain.calculator.LedgerCalculator
            .computeCurrentBalance(listOf(account), tx1000, asOf = now)
        val runningBal1000 = com.example.domain.calculator.LedgerCalculator
            .computeRunningBalances(tx1000, listOf(account), emptyList())
            .first()
            .runningBalance

        assertEquals("₹100.00", com.example.domain.util.Formatters.formatMoney(accountBal1000))
        assertEquals("₹100.00", com.example.domain.util.Formatters.formatMoney(currentBal1000))
        assertEquals("₹100.00", com.example.domain.util.Formatters.formatMoney(runningBal1000))

        // 10,000 transactions of 1.10 -> ₹11,000.00
        val tx10000 = (1..10_000).map { i ->
            com.example.data.model.TransactionEntity(
                id = i.toLong(),
                date = now - (10_000 - i) * 100L,
                type = "INCOME",
                amount = 1.10,
                toAccountId = 1L
            )
        }
        val accountBal10000 = com.example.domain.calculator.LedgerCalculator
            .computeAccountBalances(listOf(account), tx10000)
            .single()
            .currentBalance
        val currentBal10000 = com.example.domain.calculator.LedgerCalculator
            .computeCurrentBalance(listOf(account), tx10000, asOf = now)
        val runningBal10000 = com.example.domain.calculator.LedgerCalculator
            .computeRunningBalances(tx10000, listOf(account), emptyList())
            .first()
            .runningBalance

        assertEquals("₹11,000.00", com.example.domain.util.Formatters.formatMoney(accountBal10000))
        assertEquals("₹11,000.00", com.example.domain.util.Formatters.formatMoney(currentBal10000))
        assertEquals("₹11,000.00", com.example.domain.util.Formatters.formatMoney(runningBal10000))
    }

    // =========================================================================
    // FOLLOW-UP 1: Do not burn reminder marker when notifications are disabled
    // =========================================================================

    @Test
    fun followup1_disabledNotificationsDoNotBurnReminderMarker_reEnablingOnSameDayNotifiesAndRecordsMarker() {
        runBlocking {
            val accountId = repository.insertAccount(
                com.example.data.model.Account(name = "Main Bank", accountType = "BANK", openingBalance = 50000.0)
            )
            val fakeNow = java.util.Calendar.getInstance().apply {
                set(2026, java.util.Calendar.JUNE, 15, 10, 0, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            val todayStart = java.util.Calendar.getInstance().apply {
                timeInMillis = fakeNow
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis

            repository.insertCommitment(
                com.example.data.model.Commitment(
                    id = 501L,
                    type = "IOU",
                    name = "Rent IOU",
                    amount = 2500.0,
                    direction = "OUTGOING",
                    dueDate = todayStart + 8L * 3_600_000L,
                    accountId = accountId,
                    createdAt = todayStart - 86_400_000L * 3,
                    active = true
                )
            )

            val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val shadowNotifManager = org.robolectric.Shadows.shadowOf(notifManager)
            notifManager.cancelAll()

            try {
                // 1. Notifications disabled -> 0 notifications and marker is NOT recorded
                shadowNotifManager.setNotificationsEnabled(false)
                com.example.worker.DueReminderWorker.checkAndNotify(
                    context,
                    database,
                    settingsRepository,
                    now = fakeNow
                )
                assertEquals(0, shadowNotifManager.allNotifications.size)
                assertTrue(
                    "Reminder marker must not be burned when notifications are disabled",
                    settingsRepository.settingsFlow.first().reminderLastNotified.isEmpty()
                )

                // 2. Re-enable notifications on the same day -> 1 notification and marker IS recorded
                shadowNotifManager.setNotificationsEnabled(true)
                com.example.worker.DueReminderWorker.checkAndNotify(
                    context,
                    database,
                    settingsRepository,
                    now = fakeNow + 3_600_000L
                )
                assertEquals(1, shadowNotifManager.allNotifications.size)
                assertTrue(
                    "Reminder marker must be recorded after notification is actually shown",
                    settingsRepository.settingsFlow.first().reminderLastNotified.contains("501:2026-06-15:due_today")
                )
            } finally {
                shadowNotifManager.setNotificationsEnabled(true)
            }
        }
    }

    // =========================================================================
    // FOLLOW-UP 2: No runBlocking in UI code; saving commitment shows dialog once
    // =========================================================================

    @Test
    fun followup2_savingCommitmentWithoutRunBlocking_showsRationaleDialogOnce() {
        runBlocking {
            val accounts = listOf(com.example.data.model.Account(id = 1L, name = "HDFC Bank", accountType = "BANK"))
            var saveCount = 0
            val showSheet = androidx.compose.runtime.mutableStateOf(true)

            composeTestRule.setContent {
                MyApplicationTheme {
                    if (showSheet.value) {
                        com.example.ui.commitment.AddEditCommitmentSheet(
                            commitment = null,
                            accounts = accounts,
                            categories = emptyList(),
                            onDismiss = { showSheet.value = false },
                            onSave = { saveCount++ }
                        )
                    }
                }
            }
            composeTestRule.waitForIdle()

            // First save (even with cachedSettings cleared so coroutine fallback runs) -> shows rationale dialog
            composeTestRule.onNodeWithTag("commitment_name_input").performClick()
            composeTestRule.onNodeWithTag("commitment_name_input").performTextInput("Gym Membership")
            composeTestRule.onNodeWithTag("commitment_amount_input").performTextInput("1500")
            composeTestRule.onNodeWithTag("save_commitment_btn").performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            assertEquals(1, saveCount)
            composeTestRule.onNodeWithTag("commitment_notif_rationale_dialog").assertIsDisplayed()

            // Dismiss via Skip -> sheet closes
            composeTestRule.onNodeWithTag("commitment_notif_rationale_skip_btn").performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("commitment_notif_rationale_dialog").assertDoesNotExist()

            // Re-open sheet and save again -> dialog does NOT appear a second time
            showSheet.value = true
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("commitment_name_input").performClick()
            composeTestRule.onNodeWithTag("commitment_name_input").performTextInput("Phone Bill")
            composeTestRule.onNodeWithTag("commitment_amount_input").performTextInput("499")
            composeTestRule.onNodeWithTag("save_commitment_btn").performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            assertEquals(2, saveCount)
            composeTestRule.onNodeWithTag("commitment_notif_rationale_dialog").assertDoesNotExist()
        }
    }

    // =========================================================================
    // FOLLOW-UP 3: Separate auto_backup_dialog_shown flag for Dashboard dialog
    // =========================================================================

    @Test
    fun followup3_upgradedUserWithDisclosureTrueAndDialogFalse_stillShowsAutoBackupDialogOnce() {
        runBlocking {
            settingsRepository.setOnboardingCompleted(true)
            settingsRepository.setHasShownAutoBackupDisclosure(true)
            settingsRepository.setHasShownAutoBackupDialog(false)

            val initial = settingsRepository.settingsFlow.first()
            assertTrue(initial.hasShownAutoBackupDisclosure)
            assertFalse(initial.hasShownAutoBackupDialog)

            val vm = DashboardViewModel(repository, settingsRepository)
            try {
                composeTestRule.setContent {
                    MyApplicationTheme {
                        DashboardScreen(
                            viewModel = vm,
                            onNavigateToExpenses = { _, _ -> },
                            onNavigateToSettings = {}
                        )
                    }
                }
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()

                // Even though hasShownAutoBackupDisclosure is true, dialog appears because hasShownAutoBackupDialog is false
                composeTestRule.onNodeWithTag("auto_backup_disclosure_dialog").assertIsDisplayed()

                composeTestRule.onNodeWithTag("auto_backup_disclosure_keep_on_btn").performClick()
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()

                composeTestRule.onNodeWithTag("auto_backup_disclosure_dialog").assertDoesNotExist()
                assertTrue(
                    "hasShownAutoBackupDialog must be true after dismissing dialog",
                    settingsRepository.settingsFlow.first().hasShownAutoBackupDialog
                )
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    // =========================================================================
    // FOLLOW-UP 4: Settings caption matches Daily Auto-Save switch state
    // =========================================================================

    @Test
    fun followup4_settingsAutoSaveCaption_matchesSwitchOnAndOffStates() {
        runBlocking {
            val vm = SettingsViewModel(repository, settingsRepository, database)
            try {
                composeTestRule.setContent {
                    MyApplicationTheme {
                        SettingsScreen(viewModel = vm)
                    }
                }
                composeTestRule.waitForIdle()

                val captionNode = composeTestRule.onNodeWithTag("settings_auto_save_disclosure_caption", useUnmergedTree = true)
                captionNode.performScrollTo()
                captionNode.assertIsDisplayed()

                // 1. Default ON state
                captionNode.assertTextContains(
                    "Saves a readable CSV of all your transactions to Downloads/SilentSums/auto/backup.csv. Anyone who can open your files can read it."
                )

                // 2. Switched OFF state
                settingsRepository.setAutoBackupEnabled(false)
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()

                captionNode.assertTextContains(
                    "Off. When on, it saves a readable CSV of all your transactions to Downloads/SilentSums/auto/backup.csv. Anyone who can open your files can read it."
                )

                // 3. Switched back ON
                settingsRepository.setAutoBackupEnabled(true)
                ShadowLooper.idleMainLooper()
                composeTestRule.waitForIdle()

                captionNode.assertTextContains(
                    "Saves a readable CSV of all your transactions to Downloads/SilentSums/auto/backup.csv. Anyone who can open your files can read it."
                )
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    // =========================================================================
    // FOLLOW-UP 5: Only ask permission when reminders enabled & when really shown
    // =========================================================================

    @Test
    fun followup5_commitmentSave_skipsDialogWhenRemindersOff_andSheetClosedEarlyLeavesAskedFlagFalse() {
        runBlocking {
            val accounts = listOf(com.example.data.model.Account(id = 1L, name = "HDFC Bank", accountType = "BANK"))
            var saveCount = 0
            val showSheet = androidx.compose.runtime.mutableStateOf(true)

            // 1. dueRemindersEnabled = false -> no rationale dialog shown, flag stays false
            settingsRepository.setDueRemindersEnabled(false)
            composeTestRule.setContent {
                MyApplicationTheme {
                    if (showSheet.value) {
                        com.example.ui.commitment.AddEditCommitmentSheet(
                            commitment = null,
                            accounts = accounts,
                            categories = emptyList(),
                            onDismiss = { showSheet.value = false },
                            onSave = { saveCount++ }
                        )
                    }
                }
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("commitment_name_input").performClick()
            composeTestRule.onNodeWithTag("commitment_name_input").performTextInput("Water Bill")
            composeTestRule.onNodeWithTag("commitment_amount_input").performTextInput("350")
            composeTestRule.onNodeWithTag("save_commitment_btn").performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            assertEquals(1, saveCount)
            composeTestRule.onNodeWithTag("commitment_notif_rationale_dialog").assertDoesNotExist()
            assertFalse(
                "hasAskedCommitmentNotificationPermission must stay false when dueRemindersEnabled is false",
                settingsRepository.settingsFlow.first().hasAskedCommitmentNotificationPermission
            )

            // 2. dueRemindersEnabled = true -> dialog shown; closing sheet early without tapping Continue/Skip leaves flag false
            settingsRepository.setDueRemindersEnabled(true)
            showSheet.value = false
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            showSheet.value = true
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("commitment_name_input").performClick()
            composeTestRule.onNodeWithTag("commitment_name_input").performTextInput("Gas Bill")
            composeTestRule.onNodeWithTag("commitment_amount_input").performTextInput("800")
            composeTestRule.onNodeWithTag("save_commitment_btn").performClick()
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            assertEquals(2, saveCount)
            composeTestRule.onNodeWithTag("commitment_notif_rationale_dialog").assertIsDisplayed()
            assertFalse(
                "hasAskedCommitmentNotificationPermission must remain false while dialog is merely queued/open",
                settingsRepository.settingsFlow.first().hasAskedCommitmentNotificationPermission
            )

            // Simulate sheet closing early before user taps Continue/Skip or dismisses dialog
            showSheet.value = false
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()

            assertFalse(
                "Closing sheet early without interacting with rationale dialog must leave hasAskedCommitmentNotificationPermission false",
                settingsRepository.settingsFlow.first().hasAskedCommitmentNotificationPermission
            )
        }
    }
}
