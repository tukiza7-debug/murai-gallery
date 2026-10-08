package com.murai.gallery.domain.telegram

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal Telegram Bot API client: sendPhoto / sendVideo / sendDocument with
 * multipart upload, progress callback and bounded retries with backoff.
 */
class TelegramClient(
    private val context: Context,
    private val botToken: String,
    private val chatId: String
) {

    data class SendProgress(val sentBytes: Long, val totalBytes: Long)
    data class SendResult(val ok: Boolean, val message: String?)

    sealed interface ProgressListener {
        fun onProgress(sent: Long, total: Long)
    }

    suspend fun send(
        itemUri: Uri,
        fileName: String,
        mime: String,
        asDocument: Boolean,
        progress: (Long, Long) -> Unit
    ): SendResult = withContext(Dispatchers.IO) {
        if (botToken.isBlank() || chatId.isBlank()) {
            return@withContext SendResult(false, "missing-credentials")
        }
        var attempt = 0
        var lastError: String? = null
        while (attempt < 3) {
            try {
                sendOnce(itemUri, fileName, mime, asDocument, progress)?.let { return@withContext it }
                lastError = "http"
            } catch (t: Throwable) {
                lastError = t.message
            }
            attempt++
            Thread.sleep(1500L * attempt)
        }
        SendResult(false, lastError)
    }

    private fun sendOnce(
        uri: Uri,
        fileName: String,
        mime: String,
        asDocument: Boolean,
        progress: (Long, Long) -> Unit
    ): SendResult? {
        val isVideo = mime.startsWith("video/")
        val method = when {
            asDocument -> "sendDocument"
            isVideo -> "sendVideo"
            else -> "sendPhoto"
        }
        val field = when (method) {
            "sendPhoto" -> "photo"
            "sendVideo" -> "video"
            else -> "document"
        }
        val boundary = "murai" + System.currentTimeMillis()
        val resolver = context.contentResolver
        val size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
        val connection = (URL("https://api.telegram.org/bot$botToken/$method").openConnection() as HttpURLConnection).apply {
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 120_000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setChunkedStreamingMode(256 * 1024)
        }
        try {
            connection.outputStream.use { out ->
                fun part(name: String, value: String) {
                    out.write("--$boundary\r\n".toByteArray())
                    out.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
                    out.write(value.toByteArray())
                    out.write("\r\n".toByteArray())
                }
                part("chat_id", chatId)
                // file part
                out.write("--$boundary\r\n".toByteArray())
                val safeMime = if (mime.contains("*")) "application/octet-stream" else mime
                out.write(
                    "Content-Disposition: form-data; name=\"$field\"; filename=\"${fileName.replace("\"", "")}\"\r\n".toByteArray()
                )
                out.write("Content-Type: $safeMime\r\n\r\n".toByteArray())
                resolver.openInputStream(uri)?.use { input ->
                    val buf = ByteArray(64 * 1024)
                    var sent = 0L
                    while (true) {
                        val r = input.read(buf)
                        if (r <= 0) break
                        out.write(buf, 0, r)
                        sent += r
                        progress(sent, size)
                    }
                }
                out.write("\r\n".toByteArray())
                out.write("--$boundary--\r\n".toByteArray())
            }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }
            if (code in 200..299) {
                val ok = body?.contains("\"ok\":true") == true
                return SendResult(ok, if (ok) null else body?.take(300))
            }
            return SendResult(false, "HTTP $code ${body?.take(200)}")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        suspend fun validate(token: String): Boolean = withContext(Dispatchers.IO) {
            runCatching {
                val encoded = URLEncoder.encode(token, "UTF-8")
                val conn = (URL("https://api.telegram.org/bot$encoded/getMe").openConnection() as HttpURLConnection)
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val ok = conn.responseCode == 200
                conn.disconnect()
                ok
            }.getOrDefault(false)
        }
    }
}
