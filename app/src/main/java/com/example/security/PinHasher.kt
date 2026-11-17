package com.example.security

import android.util.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinHasher {
    const val HASH_PREFIX = "v1\$"
    const val DEFAULT_ITERATIONS = 120_000
    const val SALT_BYTES = 16
    const val KEY_LENGTH_BITS = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val BASE64_ENCODE_FLAGS = Base64.NO_WRAP or Base64.NO_PADDING
    private const val BASE64_DECODE_FLAGS = Base64.NO_WRAP

    const val ATTEMPTS_PER_LOCKOUT_ROUND = 5
    const val BASE_LOCKOUT_DURATION_MS = 30_000L
    const val MAX_LOCKOUT_DURATION_MS = 15 * 60 * 1000L // 15 minutes

    @Volatile
    var dispatcher: CoroutineDispatcher = Dispatchers.Default

    private val secureRandom = SecureRandom()

    fun isHashed(stored: String): Boolean = stored.startsWith(HASH_PREFIX)

    fun hash(pin: String, iterations: Int = DEFAULT_ITERATIONS): String {
        val effectiveIterations = iterations.coerceAtLeast(DEFAULT_ITERATIONS)
        val salt = ByteArray(SALT_BYTES)
        secureRandom.nextBytes(salt)
        val derived = deriveKey(pin, salt, effectiveIterations, KEY_LENGTH_BITS)
        val saltB64 = Base64.encodeToString(salt, BASE64_ENCODE_FLAGS)
        val hashB64 = Base64.encodeToString(derived, BASE64_ENCODE_FLAGS)
        return "${HASH_PREFIX}${effectiveIterations}\$${saltB64}\$${hashB64}"
    }

    fun verify(pin: String, stored: String): Boolean {
        if (pin.isEmpty() || stored.isEmpty()) return false
        if (!isHashed(stored)) {
            val pinBytes = pin.toByteArray(Charsets.UTF_8)
            val storedBytes = stored.toByteArray(Charsets.UTF_8)
            return MessageDigest.isEqual(pinBytes, storedBytes)
        }
        return try {
            val withoutPrefix = stored.removePrefix(HASH_PREFIX)
            val parts = withoutPrefix.split('$')
            if (parts.size != 3) return false
            val iterations = parts[0].toIntOrNull() ?: return false
            if (iterations <= 0) return false
            val salt = Base64.decode(parts[1], BASE64_DECODE_FLAGS)
            val expectedHash = Base64.decode(parts[2], BASE64_DECODE_FLAGS)
            if (salt.isEmpty() || expectedHash.isEmpty()) return false
            val actualHash = deriveKey(pin, salt, iterations, expectedHash.size * 8)
            MessageDigest.isEqual(actualHash, expectedHash)
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    suspend fun hashOffMain(
        pin: String,
        iterations: Int = DEFAULT_ITERATIONS,
        workDispatcher: CoroutineDispatcher = dispatcher
    ): String = runOffMainThread(workDispatcher) { hash(pin, iterations) }

    suspend fun verifyOffMain(
        pin: String,
        stored: String,
        workDispatcher: CoroutineDispatcher = dispatcher
    ): Boolean = runOffMainThread(workDispatcher) { verify(pin, stored) }

    suspend fun <T> runOffMainThread(
        workDispatcher: CoroutineDispatcher = dispatcher,
        block: () -> T
    ): T = withContext(workDispatcher) { block() }

    private fun deriveKey(pin: String, salt: ByteArray, iterations: Int, keyLengthBits: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, keyLengthBits)
        return try {
            val factory = SecretKeyFactory.getInstance(ALGORITHM)
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * Lockout schedule (1.3):
     * - After 5 wrong PINs: 30s
     * - Doubles on each further round of 5 failures (10 -> 60s, 15 -> 120s, 20 -> 240s, 25 -> 480s, 30+ -> 900s)
     * - Returns 0L when failureCount is not a positive multiple of 5.
     */
    fun lockoutDurationMsForFailures(failureCount: Int): Long {
        if (failureCount < ATTEMPTS_PER_LOCKOUT_ROUND || failureCount % ATTEMPTS_PER_LOCKOUT_ROUND != 0) {
            return 0L
        }
        val roundIndex = (failureCount / ATTEMPTS_PER_LOCKOUT_ROUND) - 1
        val shift = roundIndex.coerceAtMost(10)
        val multiplier = 1L shl shift
        return (BASE_LOCKOUT_DURATION_MS * multiplier).coerceAtMost(MAX_LOCKOUT_DURATION_MS)
    }

    /**
     * Computes remaining lockout time in ms using elapsedRealtime when in the same boot session,
     * falling back to wall-clock end time if a reboot occurred (currentElapsedMs < lockoutStartElapsedMs).
     * If a reboot occurred and the wall clock was set earlier than lockoutStartWallMs, the full
     * lockout duration is treated as remaining so moving the clock back cannot shorten the lockout.
     */
    fun computeRemainingLockoutMs(
        lockoutStartElapsedMs: Long,
        lockoutEndElapsedMs: Long,
        lockoutEndWallMs: Long,
        currentElapsedMs: Long,
        currentWallMs: Long,
        lockoutStartWallMs: Long = 0L
    ): Long {
        if (lockoutEndElapsedMs <= 0L && lockoutEndWallMs <= 0L) return 0L
        val rebooted = lockoutStartElapsedMs > 0L && currentElapsedMs < lockoutStartElapsedMs
        return if (!rebooted && lockoutEndElapsedMs > 0L) {
            (lockoutEndElapsedMs - currentElapsedMs).coerceAtLeast(0L)
        } else if (rebooted && lockoutStartWallMs > 0L && currentWallMs < lockoutStartWallMs) {
            val fullDuration = if (lockoutEndElapsedMs > lockoutStartElapsedMs) {
                lockoutEndElapsedMs - lockoutStartElapsedMs
            } else {
                (lockoutEndWallMs - lockoutStartWallMs).coerceAtLeast(0L)
            }
            fullDuration.coerceAtLeast(0L)
        } else {
            (lockoutEndWallMs - currentWallMs).coerceAtLeast(0L)
        }
    }

    /**
     * Formats remaining lockout milliseconds as "Try again in M:SS" (e.g. "Try again in 0:27").
     */
    fun formatLockoutCountdown(remainingMs: Long): String {
        val totalSeconds = ((remainingMs + 999L) / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "Try again in %d:%02d", minutes, seconds)
    }
}
