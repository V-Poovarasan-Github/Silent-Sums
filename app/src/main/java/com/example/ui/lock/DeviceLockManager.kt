package com.example.ui.lock

import android.content.Context
import androidx.biometric.BiometricManager

object BiometricHelper {
    /**
     * Checks if biometric (fingerprint/face) hardware is available and enrolled.
     * Uses BIOMETRIC_STRONG ONLY without any DEVICE_CREDENTIAL fallback.
     */
    fun isBiometricAvailable(context: Context): Boolean {
        return try {
            val biometricManager = BiometricManager.from(context)
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Exception) {
            false
        }
    }
}

