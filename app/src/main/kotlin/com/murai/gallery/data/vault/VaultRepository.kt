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
 *
 * v2.0.1: every transfer streams through [VaultCrypto] containers — media
 * bytes are never held in a byte array, so moving a 2 GB video in or out no
 * longer risks an OOM kill. Old whole-buffer files keep decrypting thanks to
 * legacy-format detection in the crypto layer.
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
                val encFile = File(vaultDir, "v_${System.nanoTime()}.enc")
                var wrote = false
                context.contentResolver.openInputStream(srcUri)?.use { input ->
                    encFile.outputStream().use { out ->
                        VaultCrypto.encryptStreamKeystore(input, out)
                        wrote = true
                    }
                }
                if (!wrote) {
                    encFile.delete()
                    return@withContext false
                }
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
                val ops = MediaOperationsForVault(context)
                // Stream-decrypt straight into the MediaStore output stream.
                val inserted = ops.insertFromStream(entry.isVideo, folder, entry.origName, entry.mime) { out ->
                    encFile.inputStream().use { input ->
                        VaultCrypto.decryptStreamKeystore(input, out)
                    }
                }
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

    /** Exposes an original decrypted copy for in-app viewing (streamed). */
    fun openDecrypted(entry: VaultEntryEntity, cacheDir: File): File? = runCatching {
        val encFile = File(entry.encPath)
        if (!encFile.exists()) return null
        val ext = entry.origName.substringAfterLast('.', "jpg")
        val out = File(cacheDir, "vault_view_${entry.id}.$ext")
        encFile.inputStream().use { input ->
            out.outputStream().use { output ->
                VaultCrypto.decryptStreamKeystore(input, output)
            }
        }
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

    /**
     * Inserts a new MediaStore row and lets [fill] stream the (decrypted)
     * payload into its output stream, keeping memory flat for big files.
     */
    suspend fun insertFromStream(
        isVideo: Boolean,
        folder: String,
        name: String,
        mime: String,
        fill: (java.io.OutputStream) -> Unit
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
            try {
                resolver.openOutputStream(uri)?.use { fill(it) } ?: run {
                    resolver.delete(uri, null, null)
                    return@withContext null
                }
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    resolver.update(uri, android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    }, null, null)
                }
                uri
            } catch (t: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw t
            }
        } catch (t: Throwable) {
            null
        }
    }
}
