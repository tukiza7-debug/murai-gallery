package com.murai.gallery.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Sharing helpers that only ever hand out content:// URIs. */
object ShareHelper {

    fun shareOne(context: Context, uri: String, mime: String = "*/*") {
        shareMultiple(context, listOf(uri), listOf(mime))
    }

    fun shareMultiple(context: Context, uris: List<String>) {
        shareMultiple(context, uris, null)
    }

    private fun shareMultiple(context: Context, uris: List<String>, mimes: List<String>?) {
        if (uris.isEmpty()) return
        val parsed = uris.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
        val mime = mimes?.firstOrNull() ?: detectMime(context, parsed.first())
        val grant = Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (parsed.size == 1) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, parsed.first())
                addFlags(grant)
            }
            context.startActivity(Intent.createChooser(intent, null).addFlags(grant))
        } else {
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mime
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(parsed))
                addFlags(grant)
            }
            context.startActivity(Intent.createChooser(intent, null).addFlags(grant))
        }
    }

    private fun detectMime(context: Context, uri: Uri?): String {
        if (uri == null) return "*/*"
        return context.contentResolver.getType(uri) ?: "*/*"
    }

    /** Shares a plain text payload (OCR result, QR text, log excerpts). */
    fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    /** Exposes an app-private file to other apps through FileProvider. */
    fun contentUriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    fun setWallpaper(context: Context, uri: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
                data = Uri.parse(uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, null))
        }
    }
}
