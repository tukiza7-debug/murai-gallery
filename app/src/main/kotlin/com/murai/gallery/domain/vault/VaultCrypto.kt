package com.murai.gallery.domain.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.InputStream
import java.io.OutputStream
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
 *
 * v2.0.1: whole-file byte arrays are gone. New files are written in the
 * chunked v2 container ("MURAI2" magic + per-chunk GCM), so a 2 GB video
 * encrypts and decrypts through constant-memory streams. Old v1 files
 * ([ivLen][iv][whole GCM body], no magic) keep decrypting via the legacy
 * path, and they never need re-encryption to stay readable.
 */
object VaultCrypto {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_ALIAS = "murai_vault_master"
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 120_000

    /** v2 container: magic(6) + version(1) + chunkSize(4 BE), then chunks. */
    val MAGIC: ByteArray = "MURAI2".toByteArray(Charsets.US_ASCII)
    private const val VERSION_V2 = 2
    const val DEFAULT_CHUNK_SIZE = 64 * 1024
    private const val MAX_CHUNK = 4 * 1024 * 1024

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

    // ------------------------------------------------------------------
    // Streaming v2 container
    // ------------------------------------------------------------------

    /** Streams [input] into [out] as a v2 chunked GCM container. */
    fun encryptStream(input: InputStream, out: OutputStream, key: SecretKey) {
        encryptStream(input, out, key, DEFAULT_CHUNK_SIZE)
    }

    fun encryptStream(input: InputStream, out: OutputStream, key: SecretKey, chunkSize: Int) {
        val size = chunkSize.coerceIn(1024, MAX_CHUNK)
        out.write(MAGIC)
        out.write(VERSION_V2)
        writeIntBE(out, size)
        val buf = ByteArray(size)
        while (true) {
            val read = readFully(input, buf, 0, size)
            if (read == 0) break
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val body = if (read == size) cipher.doFinal(buf) else {
                cipher.doFinal(buf.copyOf(read))
            }
            out.write(iv.size)
            out.write(iv)
            out.write(body)
        }
        out.flush()
    }

    /**
     * Streams a vault file (v2 container or legacy v1 blob) into [out].
     * Legacy bodies are decrypted through an in-memory pass because the old
     * format authenticates the whole ciphertext in one GCM tag; new files are
     * processed chunk by chunk with constant memory.
     */
    fun decryptStream(input: InputStream, out: OutputStream, key: SecretKey) {
        if (!isV2(input)) {
            decryptLegacyStream(input, out, key)
            return
        }
        // Stream is positioned at the container start; skip magic + version.
        val header = ByteArray(MAGIC.size + 1)
        if (readFully(input, header, 0, header.size) != header.size) {
            throw java.io.IOException("truncated header")
        }
        val chunkSize = readIntBE(input).coerceIn(1024, MAX_CHUNK)
        val ct = ByteArray(chunkSize + 16)
        while (true) {
            val ivLen = input.read()
            if (ivLen <= 0 || ivLen > 32) break
            val iv = ByteArray(ivLen)
            if (readFully(input, iv, 0, ivLen) != ivLen) throw java.io.IOException("truncated iv")
            val read = readFully(input, ct, 0, chunkSize + 16)
            if (read <= 16) throw java.io.IOException("truncated chunk")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            val plain = cipher.doFinal(ct.copyOf(read))
            out.write(plain)
        }
        out.flush()
    }

    /** True when [input] starts with the v2 magic. Does not consume the stream. */
    fun isV2(input: InputStream): Boolean {
        input.mark(MAGIC.size + 1)
        val head = ByteArray(MAGIC.size)
        val read = readFully(input, head, 0, MAGIC.size)
        input.reset()
        return read == MAGIC.size && head.contentEquals(MAGIC)
    }

    // ------------------------------------------------------------------
    // Legacy v1 (whole-buffer) — kept only so old vault files stay readable
    // ------------------------------------------------------------------

    fun encrypt(plain: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val body = cipher.doFinal(plain)
        return ByteArray(1 + iv.size + body.size).apply {
            this[0] = iv.size.toByte()
            iv.copyInto(this, 1)
            body.copyInto(this, 1 + iv.size)
        }
    }

    fun decrypt(blob: ByteArray, key: SecretKey): ByteArray {
        val ivSize = blob[0].toInt()
        val iv = blob.copyOfRange(1, 1 + ivSize)
        val body = blob.copyOfRange(1 + ivSize, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(body)
    }

    private fun decryptLegacyStream(input: InputStream, out: OutputStream, key: SecretKey) {
        // v1 layout: [ivLen:1][iv][GCM body]. GCM authenticates the entire
        // body with one tag, so the final tag can only be checked after the
        // last byte — a full pass is required by the format itself.
        val ivLen = input.read()
        if (ivLen <= 0 || ivLen > 32) throw java.io.IOException("bad legacy header")
        val iv = ByteArray(ivLen)
        if (readFully(input, iv, 0, ivLen) != ivLen) throw java.io.IOException("truncated legacy iv")
        val body = input.readBytes()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        out.write(cipher.doFinal(body))
        out.flush()
    }

    // ------------------------------------------------------------------
    // Keystore-backed entry points used by VaultRepository
    // ------------------------------------------------------------------

    fun encryptStreamKeystore(input: InputStream, out: OutputStream) =
        encryptStream(input, out, masterKey())

    fun decryptStreamKeystore(input: InputStream, out: OutputStream) =
        decryptStream(input, out, masterKey())

    fun encryptKeystore(plain: ByteArray): ByteArray = encrypt(plain, masterKey())

    fun decryptKeystore(blob: ByteArray): ByteArray = decrypt(blob, masterKey())

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

    // ------------------------------------------------------------------
    // Stream utils (no java 9+ APIs: minSdk 26)
    // ------------------------------------------------------------------

    private fun readFully(input: InputStream, buf: ByteArray, off: Int, len: Int): Int {
        var total = 0
        while (total < len) {
            val r = input.read(buf, off + total, len - total)
            if (r < 0) break
            total += r
        }
        return total
    }

    private fun writeIntBE(out: OutputStream, v: Int) {
        out.write((v ushr 24) and 0xFF)
        out.write((v ushr 16) and 0xFF)
        out.write((v ushr 8) and 0xFF)
        out.write(v and 0xFF)
    }

    private fun readIntBE(input: InputStream): Int {
        val b = ByteArray(4)
        if (readFully(input, b, 0, 4) != 4) throw java.io.IOException("truncated header")
        return ((b[0].toInt() and 0xFF) shl 24) or ((b[1].toInt() and 0xFF) shl 16) or
            ((b[2].toInt() and 0xFF) shl 8) or (b[3].toInt() and 0xFF)
    }
}
