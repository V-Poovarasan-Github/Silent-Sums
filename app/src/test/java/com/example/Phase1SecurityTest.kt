package com.example

import android.app.Activity
import android.content.Context
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.datastore.AppSettingsRepository
import com.example.data.repository.FinanceRepository
import com.example.importbackup.BackupManager
import com.example.importbackup.CsvBackupManager
import com.example.security.PinHasher
import com.example.ui.lock.LockScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class Phase1SecurityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: FinanceRepository
    private lateinit var settingsRepository: AppSettingsRepository

    private fun clearFileProviderCache() {
        try {
            val field = FileProvider::class.java.getDeclaredField("sCache")
            field.isAccessible = true
            (field.get(null) as? MutableMap<*, *>)?.clear()
        } catch (_: Exception) {}
    }

    @Before
    fun setup() = runBlocking {
        PinHasher.dispatcher = kotlinx.coroutines.Dispatchers.Unconfined
        clearFileProviderCache()
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(database)
        settingsRepository = AppSettingsRepository(context)
        settingsRepository.clearSettings()
    }

    @After
    fun tearDown() {
        PinHasher.dispatcher = kotlinx.coroutines.Dispatchers.Default
        clearFileProviderCache()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        database.close()
    }

    @Test
    fun testPinHasher_roundTrip_wrongPin_prefixConstant_andSaltRandomness() {
        val pin = "258046"
        val hashed1 = PinHasher.hash(pin)
        val hashed2 = PinHasher.hash(pin)

        assertTrue("Stored hash must start with PinHasher.HASH_PREFIX (v1$)", hashed1.startsWith(PinHasher.HASH_PREFIX))
        assertTrue("PinHasher.isHashed must return true for hashed PIN", PinHasher.isHashed(hashed1))
        assertNotEquals("Two hashes of the same PIN must use distinct random salts", hashed1, hashed2)

        val parts = hashed1.removePrefix(PinHasher.HASH_PREFIX).split('$')
        assertEquals("Format must be v1$<iterations>$<saltB64>$<hashB64>", 3, parts.size)
        val iterations = parts[0].toInt()
        assertTrue("Iterations must be at least 120,000", iterations >= 120_000)
        val saltBytes = android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP)
        val keyBytes = android.util.Base64.decode(parts[2], android.util.Base64.NO_WRAP)
        assertEquals("Salt must be 16 bytes", 16, saltBytes.size)
        assertEquals("Derived key must be 256 bits (32 bytes)", 32, keyBytes.size)

        assertTrue("Correct PIN must verify", PinHasher.verify(pin, hashed1))
        assertTrue("Correct PIN must verify against second salted hash", PinHasher.verify(pin, hashed2))
        assertFalse("Wrong PIN must fail verification", PinHasher.verify("258047", hashed1))
        assertFalse("Short PIN must fail verification", PinHasher.verify("2580", hashed1))
        assertFalse("Empty PIN must fail verification", PinHasher.verify("", hashed1))
    }

    @Test
    fun testPinHasher_oldJavaBase64EncodingCompatibility() {
        val pin = "4826"
        val salt = ByteArray(16) { (it + 7).toByte() }
        val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        val derived = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
        spec.clearPassword()

        // 1. Old java.util.Base64.getEncoder().withoutPadding() format
        val oldEncoderNoPad = Base64.getEncoder().withoutPadding()
        val oldUnpaddedStored = "${PinHasher.HASH_PREFIX}120000\$${oldEncoderNoPad.encodeToString(salt)}\$${oldEncoderNoPad.encodeToString(derived)}"
        assertTrue("Hash created with old java.util.Base64 (withoutPadding) must verify", PinHasher.verify(pin, oldUnpaddedStored))
        assertFalse("Wrong PIN must fail against old java.util.Base64 hash", PinHasher.verify("9999", oldUnpaddedStored))

        // 2. Old java.util.Base64.getEncoder() (padded) format
        val oldEncoderPadded = Base64.getEncoder()
        val oldPaddedStored = "${PinHasher.HASH_PREFIX}120000\$${oldEncoderPadded.encodeToString(salt)}\$${oldEncoderPadded.encodeToString(derived)}"
        assertTrue("Hash created with padded java.util.Base64 must also verify", PinHasher.verify(pin, oldPaddedStored))
    }

    @Test
    fun testLegacyPlainPinMigration_verifiesAndReSavesAsV1HashOnFirstUnlock() = runBlocking {
        // Seed a legacy plain PIN (does not start with "v1$")
        settingsRepository.setPinHash("1234")
        settingsRepository.setLockEnabled(true)
        assertFalse(PinHasher.isHashed(settingsRepository.settingsFlow.first().pinHash))

        // Wrong PIN does not unlock and does not migrate
        val wrongResult = settingsRepository.verifyAndMigratePin("9999")
        assertFalse("Wrong PIN must return false", wrongResult)
        assertEquals("Legacy PIN must remain unchanged on failed attempt", "1234", settingsRepository.settingsFlow.first().pinHash)

        // First successful unlock verifies legacy plain PIN and migrates it in-place to v1$ hash
        val rightResult = settingsRepository.verifyAndMigratePin("1234")
        assertTrue("Correct legacy PIN must verify", rightResult)

        val migratedStored = settingsRepository.settingsFlow.first().pinHash
        assertNotEquals("Migrated PIN must no longer be raw plain text", "1234", migratedStored)
        assertTrue(
            "Migrated PIN must start with v1$ prefix, got: $migratedStored",
            migratedStored.startsWith(PinHasher.HASH_PREFIX)
        )
        assertTrue("Migrated hash must verify original PIN", PinHasher.verify("1234", migratedStored))
        assertFalse("Migrated hash must reject wrong PIN", PinHasher.verify("9999", migratedStored))
    }

    @Test
    fun testSettingsViewModelSetPin_storesSaltedHashWithPrefix() {
        val vm = SettingsViewModel(repository, settingsRepository, database)
        vm.setPin("7890")
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        // Wait for coroutine to persist
        val stored = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            var s = ""
            for (i in 0 until 50) {
                s = settingsRepository.settingsFlow.first().pinHash
                if (s.isNotEmpty()) break
                Thread.sleep(20)
            }
            s
        }
        assertTrue("setPin() must store hash starting with v1$", stored.startsWith(PinHasher.HASH_PREFIX))
        assertFalse("setPin() must never store raw PIN", stored.contains("7890"))
        assertTrue("Stored hash must verify 7890", PinHasher.verify("7890", stored))
    }

    @Test
    fun testBackupExcludesPinMaterial_andRestoreNeverOverwritesCurrentPin() = runBlocking {
        val currentHash = settingsRepository.hashAndSavePin("1357")
        settingsRepository.setLockEnabled(true)

        // 1. Export JSON backup -> must NOT contain pinHash or PIN material
        val out = ByteArrayOutputStream()
        BackupManager.exportBackup(database, settingsRepository, out)
        val exportedJson = out.toString(Charsets.UTF_8.name())
        val root = JSONObject(exportedJson)
        val settingsObj = root.getJSONObject("settings")

        assertFalse("Exported backup JSON settings must NOT contain pinHash key", settingsObj.has("pinHash"))
        assertFalse("Exported backup JSON must NOT contain raw PIN or hash string", exportedJson.contains(currentHash))

        // 2. Export CSV backup -> must NOT contain pinHash or PIN material
        val currentSettings = settingsRepository.settingsFlow.first()
        val csvText = CsvBackupManager.buildCsv(
            transactions = emptyList(),
            accounts = emptyList(),
            categories = emptyList(),
            settings = currentSettings
        )
        assertFalse("Exported CSV must NOT contain pinHash key", csvText.contains("pinHash"))
        assertFalse("Exported CSV must NOT contain hash value", csvText.contains(currentHash))

        // 3. Restore an old backup that contains a legacy "pinHash" key -> must be ignored and never overwrite current PIN
        val maliciousOldBackup = JSONObject().apply {
            put("version", 1)
            put("timestamp", System.currentTimeMillis())
            put("settings", JSONObject().apply {
                put("currencySymbol", "$")
                put("monthStartDay", 5)
                put("theme", "DARK")
                put("lockEnabled", false)
                put("pinHash", "9999")
            })
        }.toString()

        BackupManager.restoreBackup(
            database,
            settingsRepository,
            ByteArrayInputStream(maliciousOldBackup.toByteArray(Charsets.UTF_8))
        )

        val afterRestore = settingsRepository.settingsFlow.first()
        assertEquals("Non-PIN settings should restore", "$", afterRestore.currencySymbol)
        assertEquals("Current PIN hash must NEVER be overwritten by backup restore", currentHash, afterRestore.pinHash)
        assertTrue("Lock enabled state must remain intact", afterRestore.lockEnabled)
    }

    @Test
    fun testLockoutSchedule_andRebootFallbackToWallClock() {
        // 1..4 failures -> 0ms
        for (i in 1..4) {
            assertEquals(0L, PinHasher.lockoutDurationMsForFailures(i))
        }
        // Round 1 (5 failures) -> 30s
        assertEquals(30_000L, PinHasher.lockoutDurationMsForFailures(5))
        // 6..9 failures -> 0ms (no new trigger until 10)
        for (i in 6..9) {
            assertEquals(0L, PinHasher.lockoutDurationMsForFailures(i))
        }
        // Round 2 (10 failures) -> 60s
        assertEquals(60_000L, PinHasher.lockoutDurationMsForFailures(10))
        // Round 3 (15 failures) -> 120s (2m)
        assertEquals(120_000L, PinHasher.lockoutDurationMsForFailures(15))
        // Round 4 (20 failures) -> 240s (4m)
        assertEquals(240_000L, PinHasher.lockoutDurationMsForFailures(20))
        // Round 5 (25 failures) -> 480s (8m)
        assertEquals(480_000L, PinHasher.lockoutDurationMsForFailures(25))
        // Round 6 (30 failures) -> capped at 900s (15m)
        assertEquals(900_000L, PinHasher.lockoutDurationMsForFailures(30))
        // Round 7 (35 failures) -> still capped at 900s (15m)
        assertEquals(900_000L, PinHasher.lockoutDurationMsForFailures(35))

        // Condition 1: Same boot vs Reboot case
        val startElapsed = 500_000L
        val endElapsed = 530_000L // +30s
        val endWall = 1_700_000_030_000L

        // Same boot: currentElapsedMs (503_000L) >= startElapsed (500_000L) -> uses elapsedRealtime (27_000ms left)
        val sameBootRemaining = PinHasher.computeRemainingLockoutMs(
            lockoutStartElapsedMs = startElapsed,
            lockoutEndElapsedMs = endElapsed,
            lockoutEndWallMs = endWall,
            currentElapsedMs = 503_000L,
            currentWallMs = 1_900_000_000_000L // Even if user set wall clock forward in same boot, elapsedRealtime wins
        )
        assertEquals(27_000L, sameBootRemaining)
        assertEquals("Try again in 0:27", PinHasher.formatLockoutCountdown(sameBootRemaining))

        // Reboot case: currentElapsedMs (10_000L) < startElapsed (500_000L) -> reboot happened, uses wall-clock end time!
        val rebootRemaining = PinHasher.computeRemainingLockoutMs(
            lockoutStartElapsedMs = startElapsed,
            lockoutEndElapsedMs = endElapsed,
            lockoutEndWallMs = endWall,
            currentElapsedMs = 10_000L, // Lower than startElapsed (500_000L) -> reboot detected
            currentWallMs = 1_700_000_003_000L // 27s before endWall
        )
        assertEquals("After reboot (currentElapsed < startElapsed), wall-clock end time must be used", 27_000L, rebootRemaining)
        assertEquals("Try again in 0:27", PinHasher.formatLockoutCountdown(rebootRemaining))

        // After wall-clock expiry post-reboot -> 0L
        val rebootExpired = PinHasher.computeRemainingLockoutMs(
            lockoutStartElapsedMs = startElapsed,
            lockoutEndElapsedMs = endElapsed,
            lockoutEndWallMs = endWall,
            currentElapsedMs = 15_000L,
            currentWallMs = 1_700_000_031_000L
        )
        assertEquals(0L, rebootExpired)
    }

    @Test
    fun testLockScreen_fiveWrongPinsTriggersLockout_showsCountdown_disablesKeypad_keepsBiometricActive_andResetsOnUnlock() {
        val hashedPin = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            val h = settingsRepository.hashAndSavePin("1234")
            settingsRepository.setLockEnabled(true)
            h
        }

        var fakeElapsed by mutableLongStateOf(100_000L)
        var fakeWall by mutableLongStateOf(1_700_000_000_000L)
        var unlockedCount = 0
        var biometricClicks = 0

        composeTestRule.setContent {
            MyApplicationTheme {
                LockScreen(
                    correctPin = hashedPin,
                    settingsRepository = settingsRepository,
                    elapsedRealtimeProvider = { fakeElapsed },
                    wallClockProvider = { fakeWall },
                    onUnlocked = { unlockedCount++ },
                    onBiometricClick = { biometricClicks++ }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Enter 5 wrong PINs
        repeat(5) { attempt ->
            repeat(4) { composeTestRule.onNodeWithTag("keypad_key_9").performClick() }
            composeTestRule.onNodeWithTag("unlock_confirm_button").performClick()
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            composeTestRule.waitForIdle()
            if (attempt < 4) {
                composeTestRule.onNodeWithText("Incorrect PIN. Try again.").assertIsDisplayed()
            }
        }

        // On 5th failure, 30s lockout is active
        composeTestRule.onNodeWithTag("lockout_countdown_text").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try again in 0:30").assertIsDisplayed()

        // Advance clock by 3 seconds -> live countdown shows "Try again in 0:27"
        fakeElapsed += 3_000L
        fakeWall += 3_000L
        composeTestRule.mainClock.advanceTimeBy(250L)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Try again in 0:27").assertIsDisplayed()

        // Keypad digits and backspace are disabled during lockout, but biometric key stays enabled!
        composeTestRule.onNodeWithTag("keypad_key_1").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("keypad_key_BACKSPACE").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("unlock_confirm_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("keypad_key_BIOMETRIC").assertIsEnabled()
        composeTestRule.onNodeWithTag("keypad_key_BIOMETRIC").performClick()
        assertEquals("Biometric path must stay available during PIN lockout", 1, biometricClicks)

        // Verify failure count & lockout end times were persisted to DataStore
        val persisted = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            var s = settingsRepository.settingsFlow.first()
            for (i in 0 until 20) {
                if (s.pinFailedAttempts == 5) break
                Thread.sleep(20)
                s = settingsRepository.settingsFlow.first()
            }
            s
        }
        assertEquals(5, persisted.pinFailedAttempts)
        assertEquals(100_000L, persisted.pinLockoutStartElapsedMs)
        assertEquals(130_000L, persisted.pinLockoutEndElapsedMs)

        // Advance past the 30s lockout -> keypad re-enables
        fakeElapsed = 131_000L
        fakeWall = 1_700_000_031_000L
        composeTestRule.mainClock.advanceTimeBy(250L)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("keypad_key_1").assertIsEnabled()

        // Unlock with valid PIN (1234) -> resets failure count to 0
        listOf("1", "2", "3", "4").forEach { d ->
            composeTestRule.onNodeWithTag("keypad_key_$d").performClick()
        }
        composeTestRule.onNodeWithTag("unlock_confirm_button").performClick()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        assertEquals("Valid PIN after lockout expiry must unlock", 1, unlockedCount)
        val afterUnlock = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            var s = settingsRepository.settingsFlow.first()
            for (i in 0 until 20) {
                if (s.pinFailedAttempts == 0) break
                Thread.sleep(20)
                s = settingsRepository.settingsFlow.first()
            }
            s
        }
        assertEquals("Failure counter must reset to 0 on successful unlock", 0, afterUnlock.pinFailedAttempts)
        assertEquals(0L, afterUnlock.pinLockoutEndElapsedMs)
    }

    @Test
    fun testWindowSecureFlag_setWhenLockOrPrivacyOn_clearedOtherwise() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val window = activity.window

        // Both off -> FLAG_SECURE cleared
        MainActivity.updateWindowSecureFlag(window, lockEnabled = false, hideBalances = false)
        assertEquals(0, window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)

        // Lock on -> FLAG_SECURE set
        MainActivity.updateWindowSecureFlag(window, lockEnabled = true, hideBalances = false)
        assertNotEquals(0, window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)

        // Lock off, hideBalances on -> FLAG_SECURE set
        MainActivity.updateWindowSecureFlag(window, lockEnabled = false, hideBalances = true)
        assertNotEquals(0, window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)

        // Both off again -> FLAG_SECURE cleared
        MainActivity.updateWindowSecureFlag(window, lockEnabled = false, hideBalances = false)
        assertEquals(0, window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
    }

    @Test
    fun testCloudBackupDisabled_andFileProviderNarrowedToShareAndMediaStagingOnly() {
        // 1.5 Verify AndroidManifest.xml and XML backup rules
        val manifestFile = File("src/main/AndroidManifest.xml").let {
            if (it.exists()) it else File("app/src/main/AndroidManifest.xml")
        }
        val manifestText = manifestFile.readText()
        assertTrue("Manifest must set android:allowBackup=\"false\"", manifestText.contains("android:allowBackup=\"false\""))

        val backupRulesFile = File("src/main/res/xml/backup_rules.xml").let {
            if (it.exists()) it else File("app/src/main/res/xml/backup_rules.xml")
        }
        val backupRulesText = backupRulesFile.readText()
        assertTrue(backupRulesText.contains("<exclude domain=\"database\" path=\".\"/>"))
        assertTrue(backupRulesText.contains("<exclude domain=\"file\" path=\".\"/>"))
        assertTrue(backupRulesText.contains("<exclude domain=\"sharedpref\" path=\".\"/>"))
        assertFalse(backupRulesText.contains("<include"))

        val extractionRulesFile = File("src/main/res/xml/data_extraction_rules.xml").let {
            if (it.exists()) it else File("app/src/main/res/xml/data_extraction_rules.xml")
        }
        val extractionRulesText = extractionRulesFile.readText()
        assertTrue(extractionRulesText.contains("<exclude domain=\"database\" path=\".\"/>"))
        assertTrue(extractionRulesText.contains("<exclude domain=\"file\" path=\".\"/>"))
        assertTrue(extractionRulesText.contains("<exclude domain=\"sharedpref\" path=\".\"/>"))
        assertFalse(extractionRulesText.contains("<include"))

        // 1.6 Verify FileProvider narrowed paths
        val shareFile = File(File(context.cacheDir, "share").apply { mkdirs() }, "backup.csv").apply { writeText("h1\n") }
        val stagingFile = File(File(context.cacheDir, "media_staging").apply { mkdirs() }, "cam_1.jpg").apply { writeText("img") }
        val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFile)
        val stagingUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", stagingFile)
        assertNotNull(shareUri)
        assertNotNull(stagingUri)

        // Root cacheDir or filesDir must be rejected by FileProvider
        val rootCacheFile = File(context.cacheDir, "outside.csv").apply { writeText("x") }
        var rejectedRootCache = false
        try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", rootCacheFile)
        } catch (_: IllegalArgumentException) {
            rejectedRootCache = true
        }
        assertTrue("FileProvider must reject files outside share/ and media_staging/", rejectedRootCache)

        shareFile.delete()
        stagingFile.delete()
        rootCacheFile.delete()
    }

    @Test
    fun testLockScreen_keypadDisabledUntilLockoutSettingsLoaded_preventsStaleLocalCounterBypass() {
        val hashedPin = PinHasher.hash("1234")
        val delayedSettingsFlow = kotlinx.coroutines.flow.MutableSharedFlow<com.example.data.datastore.AppSettings>(replay = 1)
        var unlockedCount = 0

        composeTestRule.setContent {
            MyApplicationTheme {
                LockScreen(
                    correctPin = hashedPin,
                    settingsRepository = settingsRepository,
                    settingsFlowOverride = delayedSettingsFlow,
                    elapsedRealtimeProvider = { 105_000L },
                    wallClockProvider = { 1_700_000_005_000L },
                    onUnlocked = { unlockedCount++ }
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Before saved lockout settings have loaded, keypad digits, backspace, and unlock button are disabled
        composeTestRule.onNodeWithTag("keypad_key_1").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("keypad_key_BACKSPACE").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("unlock_confirm_button").assertIsNotEnabled()

        // 2. Saved settings emit with an active 30s lockout -> keypad remains disabled and countdown renders immediately
        delayedSettingsFlow.tryEmit(
            com.example.data.datastore.AppSettings(
                lockEnabled = true,
                pinHash = hashedPin,
                pinFailedAttempts = 5,
                pinLockoutStartElapsedMs = 100_000L,
                pinLockoutEndElapsedMs = 130_000L,
                pinLockoutEndWallMs = 1_700_000_030_000L
            )
        )
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("keypad_key_1").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("unlock_confirm_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("lockout_countdown_text").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try again in 0:25").assertIsDisplayed()
        assertEquals(0, unlockedCount)
    }

    @Test
    fun testPhase2_releaseBuildConfig_proguard_themeSilentSums_andRoomSchema10() {
        fun resolveFile(relPath: String): File {
            val f1 = File(relPath)
            if (f1.exists()) return f1
            return File("app/$relPath")
        }

        // 2.1, 2.3, 2.4, 2.6, 2.8, 2.9: app/build.gradle.kts
        val buildGradleText = resolveFile("build.gradle.kts").readText()
        assertTrue("Release must enable isMinifyEnabled = true", buildGradleText.contains("isMinifyEnabled = true"))
        assertTrue("Release must enable isShrinkResources = true", buildGradleText.contains("isShrinkResources = true"))
        assertTrue("Release must use release signingConfig only", buildGradleText.contains("signingConfig = signingConfigs.findByName(\"release\")"))
        assertFalse("Release must not fall back to debugConfig", buildGradleText.contains("signingConfigs.findByName(\"release\") ?: signingConfigs.getByName(\"debugConfig\")"))
        assertTrue("checkReleaseSigning task must exist", buildGradleText.contains("val checkReleaseSigning by tasks.registering"))
        assertTrue("checkReleaseSigning must use providers.environmentVariable", buildGradleText.contains("providers.environmentVariable(\"KEYSTORE_PATH\")"))
        assertFalse("build.gradle.kts must not use System.getenv at configuration time", buildGradleText.contains("System.getenv"))
        assertTrue("checkReleaseSigning must throw GradleException on missing vars", buildGradleText.contains("throw GradleException("))
        assertTrue("preReleaseBuild must depend on checkReleaseSigning", buildGradleText.contains("it.name == \"preReleaseBuild\"") && buildGradleText.contains("dependsOn(checkReleaseSigning)"))
        assertTrue("versionCode must be 1", buildGradleText.contains("versionCode = 1"))
        assertTrue("versionName must be 1.0.0", buildGradleText.contains("versionName = \"1.0.0\""))
        assertTrue("Room schemaLocation must be configured for KSP", buildGradleText.contains("arg(\"room.schemaLocation\""))
        assertTrue("applicationId must remain app.silentsums", buildGradleText.contains("applicationId = \"app.silentsums\""))

        // 2.2: proguard-rules.pro
        val proguardText = resolveFile("proguard-rules.pro").readText()
        assertTrue("ProGuard must keep SourceFile,LineNumberTable", proguardText.contains("-keepattributes SourceFile,LineNumberTable"))
        assertTrue("ProGuard must keep Room @Entity", proguardText.contains("-keep @androidx.room.Entity class *"))
        assertTrue("ProGuard must keep Room @Dao", proguardText.contains("-keep @androidx.room.Dao class *"))
        assertTrue("ProGuard must keep Room @Database", proguardText.contains("-keep @androidx.room.Database class *"))
        assertTrue("ProGuard must keep WorkManager ListenableWorker constructors", proguardText.contains("class * extends androidx.work.ListenableWorker"))
        assertTrue("ProGuard must keep DataStore preferences", proguardText.contains("androidx.datastore.**"))

        // 2.5: Theme.SilentSums in themes.xml, values-night/themes.xml, and AndroidManifest.xml
        val dayThemeText = resolveFile("src/main/res/values/themes.xml").readText()
        val nightThemeText = resolveFile("src/main/res/values-night/themes.xml").readText()
        val manifestText = resolveFile("src/main/AndroidManifest.xml").readText()
        assertTrue("Day theme must define Theme.SilentSums", dayThemeText.contains("name=\"Theme.SilentSums\""))
        assertFalse("Day theme must not contain Theme.MyApplication", dayThemeText.contains("Theme.MyApplication"))
        assertTrue("Night theme must define Theme.SilentSums", nightThemeText.contains("name=\"Theme.SilentSums\""))
        assertFalse("Night theme must not contain Theme.MyApplication", nightThemeText.contains("Theme.MyApplication"))
        assertTrue("Manifest must reference @style/Theme.SilentSums", manifestText.contains("@style/Theme.SilentSums"))
        assertFalse("Manifest must not reference Theme.MyApplication", manifestText.contains("Theme.MyApplication"))

        // 2.8: Room schema 10.json generated and committed
        val schemaFile = resolveFile("schemas/com.example.data.AppDatabase/10.json")
        assertTrue("Room schema 10.json must exist at ${schemaFile.path}", schemaFile.exists())
        val schemaJson = JSONObject(schemaFile.readText())
        val dbObj = schemaJson.getJSONObject("database")
        assertEquals("Room schema version must match AppDatabase version 10", 10, dbObj.getInt("version"))
    }

    @Test
    fun testPhase2_r8SmokeTest_roomOpenMigrate_workManagerReflectiveInit_dataStoreReadWrite_andBackupJsonRoundTrip() = runBlocking {
        // 1. Room open & v1 -> v10 migration chain smoke test on file-backed SQLite DB
        val dbFile = File(context.cacheDir, "smoke_migrate_v1_to_v10.db")
        dbFile.delete()
        val rawSqlite = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        rawSqlite.execSQL("CREATE TABLE IF NOT EXISTS `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `openingBalance` REAL NOT NULL, `archived` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)")
        rawSqlite.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, `archived` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)")
        rawSqlite.execSQL("CREATE TABLE IF NOT EXISTS `custom_fields` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `options` TEXT NOT NULL, `appliesToType` TEXT, `sortOrder` INTEGER NOT NULL)")
        rawSqlite.execSQL("CREATE TABLE IF NOT EXISTS `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` INTEGER NOT NULL, `type` TEXT NOT NULL, `amount` REAL NOT NULL, `fromAccountId` INTEGER, `toAccountId` INTEGER, `categoryId` INTEGER, `description` TEXT NOT NULL, `notes` TEXT NOT NULL, `customValues` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
        rawSqlite.execSQL("INSERT INTO `accounts` (`id`, `name`, `openingBalance`, `archived`, `sortOrder`) VALUES (1, 'Legacy Bank', 5000.0, 0, 0)")
        rawSqlite.version = 1
        rawSqlite.close()

        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10
            )
            .allowMainThreadQueries()
            .build()

        val migratedAccounts = migratedDb.accountDao().getAllAccounts().first()
        assertEquals(1, migratedAccounts.size)
        assertEquals("Legacy Bank", migratedAccounts[0].name)
        assertEquals("BANK", migratedAccounts[0].accountType)

        val catId = migratedDb.categoryDao().insert(
            com.example.data.model.Category(name = "Groceries", color = "#F59E0B", iconText = "🛒")
        )
        migratedDb.transactionDao().insert(
            com.example.data.model.TransactionEntity(
                date = 1_700_000_100_000L,
                type = "EXPENSE",
                amount = 250.0,
                fromAccountId = 1L,
                toAccountId = null,
                categoryId = catId,
                description = "Milk & Bread",
                notes = "Smoke test",
                groupId = "grp_1"
            )
        )
        migratedDb.budgetDao().insert(
            com.example.data.model.BudgetEntity(
                name = "Monthly Groceries",
                scopeType = "CATEGORY",
                targetName = "Groceries",
                limitAmount = 4000.0
            )
        )
        migratedDb.commitmentDao().insert(
            com.example.data.model.Commitment(
                type = "RECURRING",
                name = "Fiber Internet",
                amount = 999.0,
                direction = "OUTGOING",
                dayOfMonth = 10,
                dueDate = null,
                accountId = 1L,
                categoryId = catId
            )
        )
        assertEquals(1, migratedDb.transactionDao().getAllTransactions().first().size)

        // 2. WorkManager workers reflective constructor check + execution smoke test
        val dueWorkerCtor = Class.forName("com.example.worker.DueReminderWorker")
            .getDeclaredConstructor(Context::class.java, androidx.work.WorkerParameters::class.java)
        val autoBackupWorkerCtor = Class.forName("com.example.worker.AutoBackupWorker")
            .getDeclaredConstructor(Context::class.java, androidx.work.WorkerParameters::class.java)
        assertNotNull("DueReminderWorker (Context, WorkerParameters) constructor must exist", dueWorkerCtor)
        assertNotNull("AutoBackupWorker (Context, WorkerParameters) constructor must exist", autoBackupWorkerCtor)

        com.example.worker.DueReminderWorker.checkAndNotify(context, migratedDb, settingsRepository)
        val syncRepo = FinanceRepository(migratedDb)
        val backupResult = com.example.worker.AutoBackupWorker.runSynchronously(
            context,
            migratedDb,
            syncRepo,
            settingsRepository
        )
        assertTrue("AutoBackupWorker must report 1 saved record, got: $backupResult", backupResult.contains("1 records"))

        // 3. DataStore read/write smoke test
        settingsRepository.setCurrency("€")
        settingsRepository.setMonthStartDay(15)
        settingsRepository.setTheme("DARK")
        val savedHash = settingsRepository.hashAndSavePin("4321")
        val dsSnapshot = settingsRepository.settingsFlow.first()
        assertEquals("€", dsSnapshot.currencySymbol)
        assertEquals(15, dsSnapshot.monthStartDay)
        assertEquals("DARK", dsSnapshot.theme)
        assertTrue(savedHash.startsWith(PinHasher.HASH_PREFIX))
        assertTrue(settingsRepository.verifyAndMigratePin("4321"))

        // 4. Backup JSON export/restore smoke test
        val jsonOut = ByteArrayOutputStream()
        BackupManager.exportBackup(migratedDb, settingsRepository, jsonOut)
        val freshDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settingsRepository.setCurrency("₹")
        BackupManager.restoreBackup(
            freshDb,
            settingsRepository,
            ByteArrayInputStream(jsonOut.toByteArray())
        )
        assertEquals(1, freshDb.accountDao().getAllAccounts().first().size)
        assertEquals(1, freshDb.categoryDao().getAllCategories().first().size)
        assertEquals(1, freshDb.transactionDao().getAllTransactions().first().size)
        assertEquals("€", settingsRepository.settingsFlow.first().currencySymbol)

        freshDb.close()
        migratedDb.close()
        dbFile.delete()

        // 5. If R8 seeds.txt is present from release build, verify critical symbols were kept by R8
        val seedsFile = File("build/outputs/mapping/release/seeds.txt").let {
            if (it.exists()) it else File("app/build/outputs/mapping/release/seeds.txt")
        }
        if (seedsFile.exists()) {
            val seedsText = seedsFile.readText()
            assertTrue(seedsText.contains("com.example.data.AppDatabase_Impl"))
            assertTrue(seedsText.contains("com.example.worker.DueReminderWorker: DueReminderWorker(android.content.Context,androidx.work.WorkerParameters)"))
            assertTrue(seedsText.contains("com.example.worker.AutoBackupWorker: AutoBackupWorker(android.content.Context,androidx.work.WorkerParameters)"))
            assertTrue(seedsText.contains("com.example.data.datastore.AppSettingsRepository"))
            assertTrue(seedsText.contains("com.example.importbackup.BackupManager"))
        }
    }

    @Test
    fun testPhase3_permissionsPolicyAndTiming() {
        fun resolveFile(relPath: String): File {
            val f1 = File(relPath)
            if (f1.exists()) return f1
            return File("app/$relPath")
        }

        // 3.1 & 3.3: AndroidManifest.xml permissions audit
        val manifestText = resolveFile("src/main/AndroidManifest.xml").readText()
        assertFalse("AndroidManifest.xml must NOT request INTERNET", manifestText.contains("android.permission.INTERNET"))
        assertFalse("AndroidManifest.xml must NOT request READ_EXTERNAL_STORAGE", manifestText.contains("android.permission.READ_EXTERNAL_STORAGE"))
        assertTrue("AndroidManifest.xml must declare RECORD_AUDIO", manifestText.contains("android.permission.RECORD_AUDIO"))
        assertTrue("AndroidManifest.xml must declare POST_NOTIFICATIONS", manifestText.contains("android.permission.POST_NOTIFICATIONS"))
        assertTrue("AndroidManifest.xml must declare WRITE_EXTERNAL_STORAGE with maxSdkVersion=\"28\"", manifestText.contains("android.permission.WRITE_EXTERNAL_STORAGE\" android:maxSdkVersion=\"28\""))

        // 3.1: MainActivity must NOT request POST_NOTIFICATIONS on launch
        val mainActivityText = resolveFile("src/main/java/com/example/MainActivity.kt").readText()
        assertFalse("MainActivity must not request notification permission on launch", mainActivityText.contains("requestNotificationPermissionIfNeeded"))
        assertFalse("MainActivity must not launch POST_NOTIFICATIONS", mainActivityText.contains("POST_NOTIFICATIONS"))

        // 3.1: SettingsScreen must have contextual explanation for POST_NOTIFICATIONS
        val settingsText = resolveFile("src/main/java/com/example/ui/settings/SettingsScreen.kt").readText()
        assertTrue("SettingsScreen must contain due reminders notification explanation", settingsText.contains("showDueReminderPermissionExplanation"))
        assertTrue("SettingsScreen must contain auto-backup notification explanation", settingsText.contains("showAutoBackupPermissionExplanation"))
        assertTrue("SettingsScreen must explain recurring bills alert", settingsText.contains("alert you when recurring bills or dues are approaching"))
        assertTrue("SettingsScreen must explain rolling backups alert", settingsText.contains("notify you when daily rolling backups finish"))

        // 3.2: EntryScreen microphone denial copy
        val entryText = resolveFile("src/main/java/com/example/ui/entry/EntryScreen.kt").readText()
        assertTrue("EntryScreen must show 'Microphone permission needed for voice notes' when denied", entryText.contains("Microphone permission needed for voice notes"))
    }
}


