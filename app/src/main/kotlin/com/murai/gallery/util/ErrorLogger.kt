package com.murai.gallery.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Rolling on-device error log. Entries live for 30 days; a retention worker
 * purges older files. Users can export the whole log as a zip from Settings.
 */
object ErrorLogger {

    private const val DIR = "error_log"
    private const val RETENTION_DAYS = 30
    private const val MAX_FILE_BYTES = 512 * 1024

    fun logDir(context: Context): File =
        File(context.filesDir, DIR).apply { mkdirs() }

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(context, "uncaught", throwable, thread.name) }
            runCatching { recordCrash(context, thread.name, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    // ------------------------------------------------------------------
    // Crash recovery (v2.0.1): the last fatal crash is kept in a marker
    // file so the next launch can offer a recovery screen instead of a
    // silent crash loop.
    // ------------------------------------------------------------------

    data class CrashRecord(val threadName: String, val whenMs: Long, val trace: String)

    private fun crashFile(context: Context): File = File(context.filesDir, "last_crash.txt")

    fun recordCrash(context: Context, threadName: String, throwable: Throwable) {
        runCatching {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            crashFile(context).writeText(
                buildString {
                    append(System.currentTimeMillis()).appendLine()
                    append(threadName).appendLine()
                    append(sw.toString())
                }
            )
        }
    }

    fun lastCrash(context: Context): CrashRecord? = runCatching {
        val f = crashFile(context)
        if (!f.exists()) return null
        val text = f.readText()
        val lines = text.lineSequence().toList()
        val whenMs = lines.getOrNull(0)?.toLongOrNull() ?: return null
        val thread = lines.getOrNull(1) ?: "main"
        CrashRecord(thread, whenMs, lines.drop(2).joinToString("\n"))
    }.getOrNull()

    /** Called once the recovery screen was shown (or dismissed) by the user. */
    fun clearCrash(context: Context) {
        runCatching { crashFile(context).delete() }
    }

    fun write(context: Context, tag: String, throwable: Throwable?, extra: String? = null, threadName: String? = null) {
        runCatching {
            val dir = logDir(context)
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val file = File(dir, "murai-log-$day.txt")
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val sw = StringWriter()
            throwable?.printStackTrace(PrintWriter(sw))
            val line = buildString {
                append("[$stamp] [$tag]")
                extra?.let { append(" $it") }
                threadName?.let { append(" thread=$it") }
                appendLine()
                throwable?.let { appendLine(sw.toString()) }
                appendLine()
            }
            synchronized(ErrorLogger) {
                if (file.length() < MAX_FILE_BYTES) {
                    file.appendText(line)
                } else {
                    File(dir, "murai-log-$day-2.txt").appendText(line)
                }
            }
        }
    }

    fun purgeOld(context: Context): Int {
        val cutoff = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -RETENTION_DAYS) }.timeInMillis
        var removed = 0
        for (f in logDir(context).listFiles() ?: emptyArray()) {
            if (f.lastModified() < cutoff && f.delete()) removed++
        }
        return removed
    }

    fun exportZip(context: Context, target: File): Boolean = runCatching {
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            for (f in logDir(context).listFiles()?.sortedBy { it.name } ?: emptyList()) {
                zip.putNextEntry(ZipEntry(f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        target.length() > 0
    }.getOrDefault(false)

    fun sdkSummary(): String =
        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MODEL}"
}
