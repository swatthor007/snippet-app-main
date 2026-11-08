package com.sockjaw.clipsy

import com.sockjaw.clipsy.data.Snippet
import com.sockjaw.clipsy.data.vault.Crypto
import com.sockjaw.clipsy.data.vault.PinKdf
import com.sockjaw.clipsy.data.vault.pinLockoutMs
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.GeneralSecurityException

class VaultCryptoTest {
    private val key = Crypto.key(Crypto.newKeyBytes())

    @Test fun textRoundTrips() {
        val sealed = Crypto.encryptText(key, "Best, Ada — 42 Lane ✓")
        assertEquals("Best, Ada — 42 Lane ✓", Crypto.decryptText(key, sealed))
    }

    @Test fun sameTextSealsDifferentlyEachTime() {
        assertNotEquals(Crypto.encryptText(key, "hello"), Crypto.encryptText(key, "hello"))
    }

    @Test fun sealedTextDoesNotContainThePlainText() {
        val sealed = Crypto.encryptText(key, "correct horse battery staple")
        assertFalse(String(Crypto.unb64(sealed), Charsets.ISO_8859_1).contains("horse"))
    }

    @Test fun wrongKeyFails() {
        val sealed = Crypto.encryptText(key, "secret")
        try {
            Crypto.decryptText(Crypto.key(Crypto.newKeyBytes()), sealed)
            fail("opened with the wrong key")
        } catch (_: GeneralSecurityException) {}
    }

    @Test fun tamperedDataFails() {
        val raw = Crypto.unb64(Crypto.encryptText(key, "secret"))
        raw[raw.size - 1] = (raw.last().toInt() xor 1).toByte()
        try {
            Crypto.decryptText(key, Crypto.b64(raw))
            fail("opened changed data")
        } catch (_: GeneralSecurityException) {}
    }

    @Test fun pinWrapsAndUnwrapsTheDataKey() {
        val data = Crypto.newKeyBytes()
        val salt = Crypto.newSalt()
        val wrapped = Crypto.encrypt(PinKdf.derive("123456", salt, 1_000), data)
        assertArrayEquals(data, Crypto.decrypt(PinKdf.derive("123456", salt, 1_000), wrapped))
    }

    @Test fun wrongPinFailsToUnwrap() {
        val salt = Crypto.newSalt()
        val wrapped = Crypto.encrypt(PinKdf.derive("123456", salt, 1_000), Crypto.newKeyBytes())
        try {
            Crypto.decrypt(PinKdf.derive("123457", salt, 1_000), wrapped)
            fail("a wrong PIN opened the key")
        } catch (_: GeneralSecurityException) {}
    }

    @Test fun pinMustBeSixDigits() {
        assertTrue(PinKdf.isValid("012345"))
        assertFalse(PinKdf.isValid("12345"))
        assertFalse(PinKdf.isValid("1234567"))
        assertFalse(PinKdf.isValid("12a456"))
    }

    @Test fun lockoutStartsOnTheFifthWrongPinAndCaps() {
        assertEquals(0, pinLockoutMs(4))
        assertEquals(30_000, pinLockoutMs(5))
        assertEquals(60_000, pinLockoutMs(6))
        assertEquals(15 * 60_000L, pinLockoutMs(40))
    }

    @Test fun lockedSnippetNeedsUnlockUntilTextIsFilledIn() {
        val locked = Snippet(name = "IBAN", locked = true, sealedContent = "x")
        assertTrue(locked.needsUnlock)
        assertFalse(locked.revealed("DE00 1234").needsUnlock)
        assertFalse(Snippet(name = "Plain", content = "hi").needsUnlock)
    }
}
