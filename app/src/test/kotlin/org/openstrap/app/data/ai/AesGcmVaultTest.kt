package org.openstrap.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** The sealing of the key, with a software AES key standing in for the Keystore's. */
class AesGcmVaultTest {
    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val secret = "sk-1234567890abcdef1234567890abcdef"

    @Test fun `a sealed key opens again`() {
        val vault = AesGcmVault(newKey().let { k -> { k } })
        assertEquals(secret, vault.open(vault.seal(secret)))
    }

    @Test fun `the sealed text does not show the key and differs each time`() {
        val vault = AesGcmVault(newKey().let { k -> { k } })
        val a = vault.seal(secret)
        val b = vault.seal(secret)
        assertFalse(a.contains("sk-"))
        assertFalse(a.contains("1234567890abcdef"))
        assertNotEquals(a, b)
        assertEquals(secret, vault.open(b))
    }

    @Test fun `a changed text opens as nothing`() {
        val vault = AesGcmVault(newKey().let { k -> { k } })
        val bytes = Base64.getDecoder().decode(vault.seal(secret))
        bytes[bytes.size - 1] = (bytes.last().toInt() xor 1).toByte()
        assertNull(vault.open(Base64.getEncoder().encodeToString(bytes)))
    }

    @Test fun `another key can't open it`() {
        val sealed = AesGcmVault(newKey().let { k -> { k } }).seal(secret)
        assertNull(AesGcmVault(newKey().let { k -> { k } }).open(sealed))
    }

    @Test fun `text that was never sealed opens as nothing`() {
        val vault = AesGcmVault(newKey().let { k -> { k } })
        assertNull(vault.open(""))
        assertNull(vault.open("not base64 !!"))
        assertNull(vault.open(Base64.getEncoder().encodeToString(ByteArray(5))))
        assertNull(vault.open(secret))
    }

    @Test fun `a key that can't be had is no key, not a crash`() {
        val sealed = AesGcmVault(newKey().let { k -> { k } }).seal(secret)
        assertNull(AesGcmVault { error("key store reset") }.open(sealed))
    }
}
