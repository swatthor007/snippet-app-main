package com.sockjaw.clipsy.ui.lock

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** What the sign-in prompt accepts. */
enum class AuthKind {
    /** The phone's fingerprint/face, or its PIN, pattern or password. */
    PHONE_LOCK,

    /** A fingerprint/face only, with a "Use PIN" button that hands over to the Clipsy PIN. */
    FINGERPRINT,
}

enum class AuthResult { SUCCESS, CANCELLED, USE_PIN, UNAVAILABLE }

/**
 * Shows the system sign-in prompt. Create it while the activity is being built, because it
 * registers an activity-result launcher (used on Android 10 and older, where the prompt can't
 * offer the phone's PIN itself).
 */
class Authenticator(private val activity: FragmentActivity) {
    private var legacyCallback: ((Boolean) -> Unit)? = null
    private val credentialLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        legacyCallback?.invoke(it.resultCode == FragmentActivity.RESULT_OK)
        legacyCallback = null
    }

    fun canUseFingerprint(): Boolean =
        BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(title: String, subtitle: String?, kind: AuthKind, onResult: (AuthResult) -> Unit) {
        when (kind) {
            AuthKind.FINGERPRINT -> {
                if (!canUseFingerprint()) return onResult(AuthResult.UNAVAILABLE)
                prompt(title, subtitle, BIOMETRIC_STRONG, negative = "Use PIN", onResult = onResult)
            }
            AuthKind.PHONE_LOCK ->
                if (Build.VERSION.SDK_INT >= 30) prompt(title, subtitle, BIOMETRIC_STRONG or DEVICE_CREDENTIAL, negative = null, onResult = onResult)
                else legacyPhoneLock(title, subtitle, onResult)
        }
    }

    /** Android 10 and older can't mix a fingerprint with the phone's PIN in one prompt: try the fingerprint, with a button for the PIN screen. */
    private fun legacyPhoneLock(title: String, subtitle: String?, onResult: (AuthResult) -> Unit) {
        val canFingerprint = BiometricManager.from(activity).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        if (!canFingerprint) return confirmCredential(title, subtitle, onResult)
        prompt(title, subtitle, BIOMETRIC_WEAK, negative = "Use phone PIN") { r ->
            if (r == AuthResult.USE_PIN) confirmCredential(title, subtitle, onResult) else onResult(r)
        }
    }

    @Suppress("DEPRECATION")
    private fun confirmCredential(title: String, subtitle: String?, onResult: (AuthResult) -> Unit) {
        val km = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val intent = km.createConfirmDeviceCredentialIntent(title, subtitle) ?: return onResult(AuthResult.UNAVAILABLE)
        legacyCallback = { ok -> onResult(if (ok) AuthResult.SUCCESS else AuthResult.CANCELLED) }
        credentialLauncher.launch(intent)
    }

    private fun prompt(title: String, subtitle: String?, authenticators: Int, negative: String?, onResult: (AuthResult) -> Unit) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(AuthResult.SUCCESS)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(
                when (errorCode) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> AuthResult.USE_PIN
                    BiometricPrompt.ERROR_NO_BIOMETRICS, BiometricPrompt.ERROR_HW_NOT_PRESENT, BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL, BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED -> AuthResult.UNAVAILABLE
                    else -> AuthResult.CANCELLED
                },
            )
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(authenticators)
            .apply {
                subtitle?.let(::setSubtitle)
                // The system supplies the cancel button when the phone's PIN is allowed.
                if (authenticators and DEVICE_CREDENTIAL == 0) setNegativeButtonText(negative ?: "Cancel")
                setConfirmationRequired(false)
            }
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
    }
}
