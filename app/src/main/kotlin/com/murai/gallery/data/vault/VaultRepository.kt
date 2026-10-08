package com.murai.gallery.data.vault

import android.content.Context
import com.murai.gallery.data.db.MuraiDatabase
import com.murai.gallery.data.db.entity.VaultEntryEntity
import com.murai.gallery.domain.vault.VaultCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The secure vault stores AES/GCM-encrypted copies of chosen media inside
 * app-private storage. Move-out decrypts and re-inserts the item into the
 * public gallery through MediaStore.
 */
class VaultRepository(
    private val context: Context,
    private val db: MuraiDatabase
) {

    private val dao get() = db.vaultDao()
    private val vaultDir: File get() = File(context.filesDir, "vault").apply { mkdirs() }

    fun observeEntries(): Flow<List<VaultEntryEntity>> = dao.observeAll()

    suspend fun moveIn(item: com.murai.gallery.data.db.entity.LibraryItemEntity): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val srcUri = android.net.Uri.parse(item.uri)
                val bytes = context.contentResolver.openInputStream(srcUri)?.use { it.readBytes() }
                    ?: return@withContext false
                val enc = VaultCrypto.encrypt(bytes)
                val encFile = File(vaultDir, "v_${System.nanoTime()}.enc")
                encFile.writeBytes(enc)
                dao.insert(
                    VaultEntryEntity(
                        encPath = encFile.absolutePath,
                        origName = item.name,
                        mime = item.mime,
                        isVideo = item.isVideo,
                        size = item.size,
                        addedAt = System.currentTimeMillis(),
                        origUri = item.uri
                    )
                )
                true
            } catch (t: Throwable) {
                com.murai.gallery.util.ErrorLogger.write(context, "vault-move-in", t)
                false
            }
        }

    suspend fun moveOut(entry: VaultEntryEntity, folder: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val encFile = File(entry.encPath)
                if (!encFile.exists()) return@withContext false
                val plain = VaultCrypto.decrypt(encFile.readBytes())
                val ops = MediaOperationsForVault(context)
                val inserted = ops.insertBytes(
                    plain, folder, entry.origName, entry.isVideo, entry.mime
                )
                if (inserted != null) {
                    encFile.delete()
                    dao.delete(entry.id)
                    true
                } else false
            } catch (t: Throwable) {
                com.murai.gallery.util.ErrorLogger.write(context, "vault-move-out", t)
                false
            }
        }

    suspend fun deletePermanently(entry: VaultEntryEntity) = withContext(Dispatchers.IO) {
        File(entry.encPath).delete()
        dao.delete(entry.id)
    }

    /** Exposes an original decrypted stream for in-app viewing. */
    fun openDecrypted(entry: VaultEntryEntity, cacheDir: File): File? = runCatching {
        val encFile = File(entry.encPath)
        if (!encFile.exists()) return null
        val plain = VaultCrypto.decrypt(encFile.readBytes())
        val ext = entry.origName.substringAfterLast('.', "jpg")
        val out = File(cacheDir, "vault_view_${entry.id}.$ext")
        out.writeBytes(plain)
        out
    }.getOrNull()

    fun pinMatches(pin: String, salt: String, storedHash: String): Boolean =
        storedHash.isNotEmpty() && constantTimeEquals(hashOf(pin, salt), storedHash)

    fun hashOf(pin: String, salt: String): String = VaultCrypto.hashPin(pin, salt)

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}

/** Narrow helper so the vault never depends on the full operations class. */
private class MediaOperationsForVault(private val context: Context) {
    suspend fun insertBytes(
        bytes: ByteArray,
        folder: String,
        name: String,
        isVideo: Boolean,
        mime: String
    ): android.net.Uri? = withContext(Dispatchers.IO) {
        try {
            val collection = if (isVideo)
                android.provider.MediaStore.Video.Media.getContentUri(
                    android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY
                )
            else android.provider.MediaStore.Images.Media.getContentUri(
                android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY
            )
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, folder)
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values) ?: return@withContext null
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return@withContext null
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                resolver.update(uri, android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                }, null, null)
            }
            uri
        } catch (t: Throwable) {
            null
        }
    }
}
