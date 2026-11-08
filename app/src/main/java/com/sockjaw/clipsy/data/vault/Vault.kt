package com.sockjaw.clipsy.data.vault

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** How locked snippets are opened: with the phone's own lock, or with a Clipsy PIN (and optionally a fingerprint). */
enum class LockMode(val label: String) { SYSTEM("Phone lock"), PIN("Clipsy PIN") }

data class VaultState(
    val setUp: Boolean = false,
    val mode: LockMode = LockMode.SYSTEM,
    val unlocked: Boolean = false,
    /** The phone's lock screen was removed or reset, so the phone-lock key is gone. Only a PIN can still open things. */
    val deviceKeyLost: Boolean = false,
    /** A fingerprint can open the vault in PIN mode. */
    val biometricWrap: Boolean = false,
    /** Wrong PINs are refused until this time (epoch ms), or 0. */
    val lockedOutUntil: Long = 0,
)

sealed interface PinResult {
    data object Ok : PinResult
    data class Wrong(val triesBeforeLockout: Int) : PinResult
    data class LockedOut(val until: Long) : PinResult
}

/**
 * Holds the key that opens locked snippets. One random data key encrypts every snippet's text;
 * that key is stored only wrapped: by an Android Keystore key that needs the phone's lock or a
 * fingerprint, and/or by a key derived from the Clipsy PIN. The data key sits in memory only
 * while unlocked.
 *
 * A third Keystore key needs no sign-in. It seals the text of snippets the user allowed to
 * expand without unlocking, so the typing service can read them.
 */
class Vault(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("clipsy_vault", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Volatile private var dek: SecretKey? = null

    private val _state = MutableStateFlow(read())
    val state: StateFlow<VaultState> = _state.asStateFlow()

    val isUnlocked: Boolean get() = dek != null

    /** True when the phone has a PIN, pattern or password; the phone-lock mode can't work without one. */
    fun deviceIsSecure(): Boolean =
        (appContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure

    // ── Setup ─────────────────────────────────────────────────────────────────

    /** Starts the vault on the phone's lock. Call right after the user signed in: the key is only usable for a few seconds after that. */
    fun setUpSystem(): Boolean = guarded {
        val data = dek ?: Crypto.key(Crypto.newKeyBytes())
        val wrapped = Crypto.encrypt(deviceKey(ALIAS_SYSTEM, strongOnly = false), data.encoded)
        prefs.edit().putString("wrap_system", wrapped).putString("mode", LockMode.SYSTEM.name).apply()
        dek = data
        publish()
    }

    /** Starts the vault on a Clipsy PIN. */
    fun setUpPin(pin: String): Boolean = guarded {
        require(PinKdf.isValid(pin))
        val data = dek ?: Crypto.key(Crypto.newKeyBytes())
        writePinWrap(data, pin)
        prefs.edit().putString("mode", LockMode.PIN.name).remove("wrap_system").apply()
        deleteKey(ALIAS_SYSTEM)
        dek = data
        publish()
    }

    /** Moves an open vault from the PIN to the phone lock. Call right after the user signed in. */
    fun switchToSystem(): Boolean {
        if (dek == null) return false
        return setUpSystem().also { ok ->
            if (ok) {
                prefs.edit().remove("wrap_pin").remove("pin_salt").remove("wrap_bio").remove("fails").remove("lock_until").apply()
                deleteKey(ALIAS_BIO)
                publish()
            }
        }
    }

    /** Changes the PIN of an open vault. */
    fun changePin(newPin: String): Boolean = guarded {
        require(PinKdf.isValid(newPin))
        writePinWrap(dek ?: error("Vault is locked"), newPin)
        publish()
    }

    /** Lets a fingerprint open a PIN-mode vault. Call right after a fingerprint sign-in. */
    fun enableBiometricWrap(): Boolean = guarded {
        val data = dek ?: error("Vault is locked")
        prefs.edit().putString("wrap_bio", Crypto.encrypt(deviceKey(ALIAS_BIO, strongOnly = true), data.encoded)).apply()
        publish()
    }

    fun disableBiometricWrap() {
        prefs.edit().remove("wrap_bio").apply()
        deleteKey(ALIAS_BIO)
        publish()
    }

    // ── Unlocking ─────────────────────────────────────────────────────────────

    /** Opens the vault after a successful phone-lock or fingerprint sign-in. */
    fun unlockWithDevice(): Boolean {
        val mode = _state.value.mode
        val wrapped = prefs.getString(if (mode == LockMode.SYSTEM) "wrap_system" else "wrap_bio", null) ?: return false
        val alias = if (mode == LockMode.SYSTEM) ALIAS_SYSTEM else ALIAS_BIO
        return try {
            val key = keyStore.getKey(alias, null) as? SecretKey
            if (key == null) { markKeyLost(); return false }
            dek = Crypto.key(Crypto.decrypt(key, wrapped))
            publish()
            true
        } catch (e: KeyPermanentlyInvalidatedException) {
            markKeyLost(); false
        } catch (e: GeneralSecurityException) {
            false
        }
    }

    fun unlockWithPin(pin: String): PinResult {
        val now = System.currentTimeMillis()
        val until = prefs.getLong("lock_until", 0)
        if (until > now) return PinResult.LockedOut(until)
        val salt = prefs.getString("pin_salt", null)?.let(Crypto::unb64)
        val wrapped = prefs.getString("wrap_pin", null)
        if (salt == null || wrapped == null || !PinKdf.isValid(pin)) return PinResult.Wrong(0)
        return try {
            dek = Crypto.key(Crypto.decrypt(PinKdf.derive(pin, salt, prefs.getInt("pin_iter", PinKdf.DEFAULT_ITERATIONS)), wrapped))
            prefs.edit().remove("fails").remove("lock_until").apply()
            publish()
            PinResult.Ok
        } catch (e: GeneralSecurityException) {
            val fails = prefs.getInt("fails", 0) + 1
            val wait = pinLockoutMs(fails)
            prefs.edit().putInt("fails", fails).putLong("lock_until", if (wait > 0) now + wait else 0).apply()
            publish()
            if (wait > 0) PinResult.LockedOut(now + wait) else PinResult.Wrong(4 - fails)
        }
    }

    fun lock() {
        if (dek == null) return
        dek = null
        publish()
    }

    // ── Sealing text ──────────────────────────────────────────────────────────

    /** Encrypts with the data key; the vault must be unlocked. */
    fun seal(text: String): String = Crypto.encryptText(dek ?: error("Vault is locked"), text)

    /** The text for a sealed value, or null when the vault is locked or the data doesn't open. */
    fun open(sealed: String): String? {
        val key = dek ?: return null
        return try { Crypto.decryptText(key, sealed) } catch (e: GeneralSecurityException) { null } catch (e: IllegalArgumentException) { null }
    }

    /** Seals with the sign-in-free key, for snippets allowed to expand without unlocking. */
    fun sealOpen(text: String): String = Crypto.encryptText(openKey(), text)

    fun openOpen(sealed: String): String? =
        try { Crypto.decryptText(openKey(), sealed) } catch (e: GeneralSecurityException) { null } catch (e: IllegalArgumentException) { null }

    /** Forgets everything. Only call once no snippet is locked any more (or the user chose to lose them). */
    fun reset() {
        dek = null
        prefs.edit().clear().apply()
        listOf(ALIAS_SYSTEM, ALIAS_BIO, ALIAS_OPEN).forEach(::deleteKey)
        publish()
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private inline fun guarded(block: () -> Unit): Boolean = try {
        block(); true
    } catch (e: GeneralSecurityException) {
        false
    }

    private fun writePinWrap(data: SecretKey, pin: String) {
        val salt = Crypto.newSalt()
        val wrapped = Crypto.encrypt(PinKdf.derive(pin, salt), data.encoded)
        prefs.edit()
            .putString("pin_salt", Crypto.b64(salt))
            .putInt("pin_iter", PinKdf.DEFAULT_ITERATIONS)
            .putString("wrap_pin", wrapped)
            .remove("fails").remove("lock_until")
            .apply()
    }

    private fun markKeyLost() {
        // In PIN mode only the fingerprint shortcut is gone; the PIN still opens everything.
        if (_state.value.mode == LockMode.PIN) disableBiometricWrap() else prefs.edit().putBoolean("key_lost", true).apply()
        publish()
    }

    private fun deleteKey(alias: String) {
        runCatching { keyStore.deleteEntry(alias) }
    }

    private fun deviceKey(alias: String, strongOnly: Boolean): SecretKey {
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val types = if (strongOnly) KeyProperties.AUTH_BIOMETRIC_STRONG
        else KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(false)
            .apply {
                if (Build.VERSION.SDK_INT >= 30) setUserAuthenticationParameters(AUTH_WINDOW_SECONDS, types)
                else @Suppress("DEPRECATION") setUserAuthenticationValidityDurationSeconds(AUTH_WINDOW_SECONDS)
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run { init(spec); generateKey() }
    }

    private fun openKey(): SecretKey {
        (keyStore.getKey(ALIAS_OPEN, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS_OPEN, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run { init(spec); generateKey() }
    }

    private fun read() = VaultState(
        setUp = prefs.contains("wrap_system") || prefs.contains("wrap_pin"),
        mode = runCatching { LockMode.valueOf(prefs.getString("mode", null) ?: "") }.getOrDefault(LockMode.SYSTEM),
        unlocked = dek != null,
        deviceKeyLost = prefs.getBoolean("key_lost", false),
        biometricWrap = prefs.contains("wrap_bio"),
        lockedOutUntil = prefs.getLong("lock_until", 0),
    )

    private fun publish() { _state.value = read() }

    private companion object {
        const val ALIAS_SYSTEM = "clipsy_dek_system"
        const val ALIAS_BIO = "clipsy_dek_bio"
        const val ALIAS_OPEN = "clipsy_open"
        /** How long after signing in the Keystore key can still be used. */
        const val AUTH_WINDOW_SECONDS = 15
    }
}
