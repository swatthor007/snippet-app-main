package com.sockjaw.clipsy.data.vault

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** AES-GCM with a random IV, packed as base64(iv + ciphertext). Works with software and Keystore keys. */
object Crypto {
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128
    private const val IV_BYTES = 12
    private val random = SecureRandom()

    fun newKeyBytes(): ByteArray = ByteArray(32).also(random::nextBytes)

    fun newSalt(): ByteArray = ByteArray(16).also(random::nextBytes)

    fun key(bytes: ByteArray): SecretKey = SecretKeySpec(bytes, "AES")

    fun encrypt(key: SecretKey, plain: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return pack(cipher.iv, cipher.doFinal(plain))
    }

    /** Throws [java.security.GeneralSecurityException] when the key is wrong or the data was changed. */
    fun decrypt(key: SecretKey, packed: String): ByteArray {
        val raw = Base64.getDecoder().decode(packed)
        require(raw.size > IV_BYTES) { "Sealed data is too short" }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES))
        return cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES)
    }

    fun encryptText(key: SecretKey, text: String): String = encrypt(key, text.toByteArray(Charsets.UTF_8))

    fun decryptText(key: SecretKey, packed: String): String = decrypt(key, packed).toString(Charsets.UTF_8)

    private fun pack(iv: ByteArray, ct: ByteArray): String = Base64.getEncoder().encodeToString(iv + ct)

    fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun unb64(text: String): ByteArray = Base64.getDecoder().decode(text)
}

/** Turns a short PIN into an AES key. The PIN is never stored: a wrong one simply fails to unwrap the data key. */
object PinKdf {
    const val DEFAULT_ITERATIONS = 150_000
    const val PIN_LENGTH = 6

    fun derive(pin: String, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): SecretKey {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return Crypto.key(bytes)
        } finally {
            spec.clearPassword()
        }
    }

    fun isValid(pin: String): Boolean = pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }
}

/** How long to refuse PIN tries after [failures] wrong ones in a row. Free for the first 4, then 30 s doubling to a 15 min cap. */
fun pinLockoutMs(failures: Int): Long {
    if (failures < 5) return 0
    val steps = (failures - 5).coerceAtMost(5)
    return (30_000L shl steps).coerceAtMost(15 * 60_000L)
}
