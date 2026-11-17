package org.openstrap.app.data.ai

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Seals a short secret (the DeepSeek key) so it is stored as text that means nothing without this phone's key store:
 * [seal] gives a Base64 text of a fresh 12-byte nonce and the AES-GCM ciphertext, [open] gives the secret back, or
 * null when the text isn't one of ours, was changed, or its key is gone (a restored phone, a reset key store).
 */
interface AiVault {
    fun seal(plain: String): String

    fun open(sealed: String): String?
}

/** AES-256-GCM over a [key] given to it: the whole algorithm, with no Android in it, so it is tested on the JVM. */
// Catching everything on open is the point: any failure to read the text is "no key", never a crash.
@Suppress("TooGenericExceptionCaught")
class AesGcmVault(private val key: () -> SecretKey) : AiVault {
    override fun seal(plain: String): String {
        // The cipher makes its own random nonce: the Keystore refuses one handed to it, and a fresh one each time is the rule.
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(cipher.iv + body)
    }

    override fun open(sealed: String): String? = try {
        val bytes = Base64.getDecoder().decode(sealed)
        if (bytes.size <= NONCE_BYTES + TAG_BITS / Byte.SIZE_BITS) {
            null
        } else {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes.copyOfRange(0, NONCE_BYTES)))
            String(cipher.doFinal(bytes, NONCE_BYTES, bytes.size - NONCE_BYTES), Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
    }
}

private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val KEYSTORE_KEY_BITS = 256

/** The AES key in the Android Keystore under [KeystoreVault.ALIAS], made on first use. */
@Synchronized
private fun keystoreKey(): SecretKey {
    val store = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    (store.getKey(KeystoreVault.ALIAS, null) as? SecretKey)?.let { return it }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
    generator.init(
        KeyGenParameterSpec.Builder(KeystoreVault.ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEYSTORE_KEY_BITS)
            .build(),
    )
    return generator.generateKey()
}

/**
 * The vault the app uses: [AesGcmVault] with an AES key that lives in the Android Keystore, made on first use. The key
 * never leaves the key store, so the sealed text in DataStore can't be opened anywhere else, and it is not part of any
 * backup. No new dependency: `java.security.KeyStore` and `javax.crypto` only.
 */
class KeystoreVault : AiVault by AesGcmVault(::keystoreKey) {
    companion object {
        const val ALIAS = "openstrap_ai_key"
    }
}
