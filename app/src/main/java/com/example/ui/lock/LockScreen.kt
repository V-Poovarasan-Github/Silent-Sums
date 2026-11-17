package com.example.ui.lock

import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.example.data.datastore.AppSettings
import com.example.data.datastore.AppSettingsRepository
import com.example.security.PinHasher
import com.example.ui.common.AppIcons
import com.example.ui.theme.Amber400
import com.example.ui.theme.Navy700
import com.example.ui.theme.Navy800
import com.example.ui.theme.Navy900
import com.example.ui.theme.SoftBlue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

private fun triggerFallbackBiometricPrompt(
    context: Context,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val activity = context.findFragmentActivity() ?: return
    val executor = ContextCompat.getMainExecutor(activity)
    val biometricPrompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (
                    errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_CANCELED
                ) {
                    onError(errString.toString())
                }
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

@Composable
fun LockScreen(
    correctPin: String,
    onUnlocked: () -> Unit,
    onBiometricClick: (() -> Unit)? = null,
    settingsRepository: AppSettingsRepository? = null,
    settingsFlowOverride: kotlinx.coroutines.flow.Flow<AppSettings>? = null,
    elapsedRealtimeProvider: () -> Long = { SystemClock.elapsedRealtime() },
    wallClockProvider: () -> Long = { System.currentTimeMillis() },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val effectiveSettingsRepo = remember(context, settingsRepository) {
        settingsRepository ?: AppSettingsRepository(context.applicationContext)
    }
    val activeSettingsFlow = settingsFlowOverride ?: effectiveSettingsRepo.settingsFlow
    val initialLoadedSettings = if (settingsFlowOverride != null) null else effectiveSettingsRepo.cachedSettings
    val loadedSettings by activeSettingsFlow.collectAsState(initial = initialLoadedSettings)
    val isSettingsLoaded = loadedSettings != null
    val persistedSettings = loadedSettings ?: AppSettings()
    val isBiometricAvailable = remember(context) { BiometricHelper.isBiometricAvailable(context) }

    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val shakeOffset = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    var localFailedAttempts by remember { mutableIntStateOf(0) }
    var localStartElapsedMs by remember { mutableLongStateOf(0L) }
    var localEndElapsedMs by remember { mutableLongStateOf(0L) }
    var localStartWallMs by remember { mutableLongStateOf(0L) }
    var localEndWallMs by remember { mutableLongStateOf(0L) }

    val effectiveFailedAttempts = maxOf(localFailedAttempts, persistedSettings.pinFailedAttempts)
    val effectiveStartElapsedMs = maxOf(localStartElapsedMs, persistedSettings.pinLockoutStartElapsedMs)
    val effectiveEndElapsedMs = maxOf(localEndElapsedMs, persistedSettings.pinLockoutEndElapsedMs)
    val effectiveStartWallMs = maxOf(localStartWallMs, persistedSettings.pinLockoutStartWallMs)
    val effectiveEndWallMs = maxOf(localEndWallMs, persistedSettings.pinLockoutEndWallMs)

    LaunchedEffect(
        persistedSettings.pinFailedAttempts,
        persistedSettings.pinLockoutStartElapsedMs,
        persistedSettings.pinLockoutEndElapsedMs,
        persistedSettings.pinLockoutStartWallMs,
        persistedSettings.pinLockoutEndWallMs
    ) {
        if (persistedSettings.pinFailedAttempts > localFailedAttempts ||
            persistedSettings.pinLockoutEndElapsedMs > localEndElapsedMs ||
            persistedSettings.pinLockoutEndWallMs > localEndWallMs
        ) {
            localFailedAttempts = persistedSettings.pinFailedAttempts
            localStartElapsedMs = persistedSettings.pinLockoutStartElapsedMs
            localEndElapsedMs = persistedSettings.pinLockoutEndElapsedMs
            localStartWallMs = persistedSettings.pinLockoutStartWallMs
            localEndWallMs = persistedSettings.pinLockoutEndWallMs
        } else if (persistedSettings.pinFailedAttempts == 0 &&
            persistedSettings.pinLockoutEndElapsedMs == 0L &&
            persistedSettings.pinLockoutEndWallMs == 0L &&
            localFailedAttempts == 0
        ) {
            localStartElapsedMs = 0L
            localEndElapsedMs = 0L
            localStartWallMs = 0L
            localEndWallMs = 0L
        }
    }

    var nowElapsedMs by remember { mutableLongStateOf(elapsedRealtimeProvider()) }
    var nowWallMs by remember { mutableLongStateOf(wallClockProvider()) }

    val remainingLockoutMs = PinHasher.computeRemainingLockoutMs(
        lockoutStartElapsedMs = effectiveStartElapsedMs,
        lockoutEndElapsedMs = effectiveEndElapsedMs,
        lockoutEndWallMs = effectiveEndWallMs,
        currentElapsedMs = nowElapsedMs,
        currentWallMs = nowWallMs,
        lockoutStartWallMs = effectiveStartWallMs
    )
    val isLockedOut = remainingLockoutMs > 0L
    val isKeypadDisabled = !isSettingsLoaded || isLockedOut

    LaunchedEffect(effectiveStartElapsedMs, effectiveEndElapsedMs, effectiveStartWallMs, effectiveEndWallMs) {
        nowElapsedMs = elapsedRealtimeProvider()
        nowWallMs = wallClockProvider()
        while (
            PinHasher.computeRemainingLockoutMs(
                lockoutStartElapsedMs = effectiveStartElapsedMs,
                lockoutEndElapsedMs = effectiveEndElapsedMs,
                lockoutEndWallMs = effectiveEndWallMs,
                currentElapsedMs = nowElapsedMs,
                currentWallMs = nowWallMs,
                lockoutStartWallMs = effectiveStartWallMs
            ) > 0L
        ) {
            delay(200L)
            nowElapsedMs = elapsedRealtimeProvider()
            nowWallMs = wallClockProvider()
        }
    }

    fun triggerShake() {
        coroutineScope.launch {
            shakeOffset.snapTo(0f)
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 400
                    0f at 0
                    (-20f) at 50
                    20f at 100
                    (-15f) at 150
                    15f at 200
                    (-8f) at 250
                    8f at 300
                    (-3f) at 350
                    0f at 400
                }
            )
        }
    }

    fun handleKeyPress(key: String) {
        if (isKeypadDisabled) return
        if (key == "BACKSPACE") {
            if (enteredPin.isNotEmpty()) {
                enteredPin = enteredPin.dropLast(1)
                errorMessage = null
            }
        } else {
            // Variable length PIN: 4 to 20 digits (max 20 digits)
            if (enteredPin.length < 20) {
                enteredPin += key
                errorMessage = null
            }
        }
    }

    fun handleBiometricUnlockSuccess() {
        localFailedAttempts = 0
        localStartElapsedMs = 0L
        localEndElapsedMs = 0L
        localStartWallMs = 0L
        localEndWallMs = 0L
        errorMessage = null
        enteredPin = ""
        coroutineScope.launch {
            effectiveSettingsRepo.resetPinLockout()
        }
        onUnlocked()
    }

    fun submitPin() {
        if (!isSettingsLoaded) return
        nowElapsedMs = elapsedRealtimeProvider()
        nowWallMs = wallClockProvider()
        if (
            PinHasher.computeRemainingLockoutMs(
                lockoutStartElapsedMs = effectiveStartElapsedMs,
                lockoutEndElapsedMs = effectiveEndElapsedMs,
                lockoutEndWallMs = effectiveEndWallMs,
                currentElapsedMs = nowElapsedMs,
                currentWallMs = nowWallMs,
                lockoutStartWallMs = effectiveStartWallMs
            ) > 0L
        ) {
            return
        }
        if (enteredPin.length < 4) {
            errorMessage = "PIN must be at least 4 digits"
            triggerShake()
            return
        }
        val candidatePin = enteredPin
        val storedValue = correctPin.ifEmpty { persistedSettings.pinHash }
        coroutineScope.launch {
            val isValid = PinHasher.verifyOffMain(candidatePin, storedValue)
            if (isValid) {
                localFailedAttempts = 0
                localStartElapsedMs = 0L
                localEndElapsedMs = 0L
                localStartWallMs = 0L
                localEndWallMs = 0L
                errorMessage = null
                enteredPin = ""
                onUnlocked()
                effectiveSettingsRepo.verifyAndMigratePin(
                    enteredPin = candidatePin,
                    storedOverride = storedValue
                )
            } else {
                val curElapsed = elapsedRealtimeProvider()
                val curWall = wallClockProvider()
                val nextFailures = effectiveFailedAttempts + 1
                localFailedAttempts = nextFailures
                val delayMs = PinHasher.lockoutDurationMsForFailures(nextFailures)
                if (delayMs > 0L) {
                    localStartElapsedMs = curElapsed
                    localEndElapsedMs = curElapsed + delayMs
                    localStartWallMs = curWall
                    localEndWallMs = curWall + delayMs
                    nowElapsedMs = curElapsed
                    nowWallMs = curWall
                    errorMessage = null
                } else {
                    errorMessage = "Incorrect PIN. Try again."
                }
                enteredPin = ""
                triggerShake()
                effectiveSettingsRepo.recordPinFailure(
                    currentElapsedMs = curElapsed,
                    currentWallMs = curWall
                )
            }
        }
    }

    Surface(
        color = Navy900,
        modifier = modifier
            .fillMaxSize()
            .testTag("app_pin_lock_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Header Icon & Title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Navy800),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Unlock Vault",
                        tint = Amber400,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("vault_lock_header_icon")
                    )
                }

                Text(
                    text = "SilentSums Vault",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "Enter your PIN to unlock",
                    fontSize = 13.sp,
                    color = SoftBlue,
                    modifier = Modifier.testTag("master_pin_prompt_text")
                )

                // Masked PIN Dots: grows dynamically per digit entered
                val dotCount = if (enteredPin.isEmpty()) 4 else enteredPin.length
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .offset(x = shakeOffset.value.dp)
                ) {
                    for (i in 0 until dotCount) {
                        val isFilled = i < enteredPin.length
                        val dotScale by animateFloatAsState(
                            targetValue = if (isFilled) 1.2f else 1.0f,
                            animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                            label = "dot_scale_$i"
                        )
                        val dotColor by animateColorAsState(
                            targetValue = if (isFilled) Amber400 else Navy700,
                            animationSpec = tween(150),
                            label = "dot_color_$i"
                        )
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .scale(dotScale)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                }

                val statusText = when {
                    isLockedOut -> PinHasher.formatLockoutCountdown(remainingLockoutMs)
                    errorMessage != null -> errorMessage
                    else -> null
                }
                if (statusText != null) {
                    Text(
                        text = statusText,
                        color = Color(0xFFFFB4AB),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .offset(x = shakeOffset.value.dp)
                            .semantics {
                                liveRegion = if (isLockedOut) {
                                    androidx.compose.ui.semantics.LiveRegionMode.Polite
                                } else {
                                    androidx.compose.ui.semantics.LiveRegionMode.Assertive
                                }
                            }
                            .testTag(if (isLockedOut) "lockout_countdown_text" else "lock_error_text")
                    )
                }
            }

            // Keypad & Actions
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_master_pin_pad")
            ) {
                val keypad = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("BIOMETRIC", "0", "BACKSPACE")
                )

                keypad.forEach { rowKeys ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        rowKeys.forEach { key ->
                            val isBiometricKey = key == "BIOMETRIC"
                            val keyEnabled = key.isNotBlank() && (isBiometricKey || !isKeypadDisabled)
                            val keyLabel = when (key) {
                                "BACKSPACE" -> "Delete last digit"
                                "BIOMETRIC" -> "Unlock with fingerprint"
                                else -> key
                            }
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(if (key.isNotBlank()) Navy800 else Color.Transparent)
                                    .alpha(if (key.isNotBlank() && !keyEnabled) 0.4f else 1f)
                                    .clickable(
                                        enabled = keyEnabled,
                                        role = androidx.compose.ui.semantics.Role.Button,
                                        onClickLabel = if (key.isNotBlank()) keyLabel else null
                                    ) {
                                        when (key) {
                                            "BIOMETRIC" -> {
                                                if (!isLockedOut) {
                                                    errorMessage = null
                                                }
                                                onBiometricClick?.invoke()
                                                if (onBiometricClick == null || !isBiometricAvailable) {
                                                    triggerFallbackBiometricPrompt(
                                                        context = context,
                                                        onSuccess = { handleBiometricUnlockSuccess() },
                                                        onError = { msg -> errorMessage = msg }
                                                    )
                                                }
                                            }
                                            else -> handleKeyPress(key)
                                        }
                                    }
                                    .semantics {
                                        if (key.isNotBlank()) {
                                            role = androidx.compose.ui.semantics.Role.Button
                                            contentDescription = keyLabel
                                        }
                                    }
                                    .testTag(if (key.isNotBlank()) "keypad_key_$key" else "keypad_empty"),
                                contentAlignment = Alignment.Center
                            ) {
                                when (key) {
                                    "BIOMETRIC" -> Icon(
                                        painter = painterResource(R.drawable.ic_fingerprint_m3),
                                        contentDescription = "Fingerprint",
                                        tint = Amber400,
                                        modifier = Modifier
                                            .size(26.dp)
                                            .testTag("vault_fingerprint_keypad_icon")
                                    )
                                    "BACKSPACE" -> Icon(
                                        imageVector = AppIcons.Backspace,
                                        contentDescription = "Delete last digit",
                                        tint = SoftBlue,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    "" -> {}
                                    else -> Text(
                                        text = key,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Explicit Unlock Button (No auto-submit)
                Button(
                    onClick = { submitPin() },
                    enabled = !isKeypadDisabled && enteredPin.length >= 4,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Amber400,
                        contentColor = Navy900,
                        disabledContainerColor = Navy800,
                        disabledContentColor = SoftBlue.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(48.dp)
                        .semantics {
                            stateDescription = when {
                                isLockedOut -> "Locked out"
                                isKeypadDisabled -> "Disabled, loading"
                                enteredPin.length < 4 -> "Disabled, enter at least 4 digits"
                                else -> "Ready to unlock"
                            }
                        }
                        .testTag("unlock_confirm_button")
                ) {
                    Text("Unlock", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
