package com.murai.gallery.domain.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Vault cryptography:
 * - Files are encrypted with AES/GCM; the master key lives in AndroidKeyStore
 *   and never leaves the device.
 * - PINs are verified with PBKDF2-HMAC-SHA256 over a random 16-byte salt.
 * - The decoy PIN hashes to the same format; a valid decoy opens a separate
 *   decoy space instead of the real vault.
 */
object VaultCrypto {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_ALIAS = "murai_vault_master"
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 120_000

    fun randomSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hashPin(pin: String, saltHex: String): String {
        val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded.joinToString("") { "%02x".format(it) }
    }

    private fun masterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(MASTER_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                MASTER_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val iv = cipher.iv
        val body = cipher.doFinal(plain)
        return ByteArray(1 + iv.size + body.size).apply {
            this[0] = iv.size.toByte()
            iv.copyInto(this, 1)
            body.copyInto(this, 1 + iv.size)
        }
    }

    fun decrypt(blob: ByteArray): ByteArray {
        val ivSize = blob[0].toInt()
        val iv = blob.copyOfRange(1, 1 + ivSize)
        val body = blob.copyOfRange(1 + ivSize, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(body)
    }

    /** Extra wrapper used when a copy is stored outside the vault directory. */
    fun wrapKeyForBackup(raw: ByteArray, pin: String, saltHex: String): ByteArray {
        val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val body = cipher.doFinal(raw)
        return iv + body
    }
}
