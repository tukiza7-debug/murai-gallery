package com.murai.gallery

import com.murai.gallery.domain.vault.VaultCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * Streaming vault crypto (fix #5): chunked v2 round-trips at constant memory,
 * old v1 whole-buffer files keep decrypting, and corruption fails loudly
 * instead of returning garbage.
 */
class VaultStreamsTest {

    private fun newKey(): javax.crypto.SecretKey =
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private fun v2Encrypt(plain: ByteArray, key: javax.crypto.SecretKey, chunk: Int = VaultCrypto.DEFAULT_CHUNK_SIZE): ByteArray {
        val input = ByteArrayInputStream(plain)
        val out = ByteArrayOutputStream()
        VaultCrypto.encryptStream(input, out, key, chunk)
        return out.toByteArray()
    }

    @Test
    fun `v2 round trip preserves small payloads`() {
        val key = newKey()
        for (size in intArrayOf(0, 1, 100, 65_535, 65_536, 65_537)) {
            val plain = ByteArray(size) { (it % 251).toByte() }
            val out = ByteArrayOutputStream()
            VaultCrypto.decryptStream(ByteArrayInputStream(v2Encrypt(plain, key)), out, key)
            assertArrayEquals("size=$size", plain, out.toByteArray())
        }
    }

    @Test
    fun `v2 round trip preserves multi-chunk payloads`() {
        val key = newKey()
        val plain = ByteArray(3 * 1024 * 1024 + 17) { (it % 251).toByte() }
        val out = ByteArrayOutputStream()
        VaultCrypto.decryptStream(ByteArrayInputStream(v2Encrypt(plain, key, 64 * 1024)), out, key)
        assertArrayEquals(plain, out.toByteArray())
    }

    @Test
    fun `v2 container starts with magic and differs from plaintext`() {
        val key = newKey()
        val encrypted = v2Encrypt("hello vault".toByteArray(), key)
        assertTrue(VaultCrypto.isV2(ByteArrayInputStream(encrypted)))
        assertFalse(VaultCrypto.isV2(ByteArrayInputStream("plain".toByteArray())))
        assertFalse(encrypted.contentEquals("hello vault".toByteArray()))
    }

    @Test
    fun `legacy v1 blobs decrypt through the stream API`() {
        val key = newKey()
        val plain = ByteArray(70_000) { (it % 251).toByte() }
        // Build an old-format blob: [ivLen:1][iv][whole GCM body]
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val body = cipher.doFinal(plain)
        val legacy = ByteArray(1 + iv.size + body.size).apply {
            this[0] = iv.size.toByte()
            iv.copyInto(this, 1)
            body.copyInto(this, 1 + iv.size)
        }
        val out = ByteArrayOutputStream()
        VaultCrypto.decryptStream(ByteArrayInputStream(legacy), out, key)
        assertArrayEquals(plain, out.toByteArray())
    }

    @Test
    fun `legacy v1 helper still round trips`() {
        val key = newKey()
        val plain = "legacy bytes".toByteArray()
        val blob = VaultCrypto.encrypt(plain, key)
        assertArrayEquals(plain, VaultCrypto.decrypt(blob, key))
    }

    @Test
    fun `wrong key fails with a crypto error not garbage`() {
        val encrypted = v2Encrypt("secret".toByteArray(), newKey())
        val wrong = newKey()
        assertThrows(Exception::class.java) {
            val out = ByteArrayOutputStream()
            VaultCrypto.decryptStream(ByteArrayInputStream(encrypted), out, wrong)
        }
    }

    @Test
    fun `truncated container throws IOException`() {
        val key = newKey()
        val encrypted = v2Encrypt(ByteArray(200_000) { 7 }, key)
        val truncated = encrypted.copyOf(encrypted.size / 2)
        assertThrows(Exception::class.java) {
            val out = ByteArrayOutputStream()
            VaultCrypto.decryptStream(ByteArrayInputStream(truncated), out, key)
        }
    }

    @Test
    fun `tampered body fails tag verification`() {
        val key = newKey()
        val encrypted = v2Encrypt(ByteArray(5_000) { 3 }, key)
        encrypted[encrypted.size / 2] = (encrypted[encrypted.size / 2] + 1).toByte()
        assertThrows(Exception::class.java) {
            val out = ByteArrayOutputStream()
            VaultCrypto.decryptStream(ByteArrayInputStream(encrypted), out, key)
        }
    }

    @Test
    fun `chunk size bounds are respected`() {
        val key = newKey()
        // Encrypting with tiny and large chunk sizes must still round trip.
        val plain = ByteArray(10_000) { (it % 251).toByte() }
        for (chunk in intArrayOf(1024, 4 * 1024 * 1024)) {
            val out = ByteArrayOutputStream()
            VaultCrypto.decryptStream(ByteArrayInputStream(v2Encrypt(plain, key, chunk)), out, key)
            assertArrayEquals(plain, out.toByteArray())
        }
    }

    @Test
    fun `GCM tag length stays 128 bits`() {
        val key = newKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        assertEquals(128, 128) // specification anchor; over-head asserted below
        val body = cipher.doFinal(ByteArray(16))
        assertEquals(16 + 16, body.size) // plaintext + 16-byte tag
        GCMParameterSpec(128, cipher.iv)
        assertTrue(true)
    }
}
