package com.murai.gallery.domain.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app update check against GitHub Releases. Everything runs on IO, fails
 * soft (offline message) and never crashes the app.
 */
object UpdateChecker {

    const val RELEASES_URL = "https://api.github.com/repos/tukiza7-debug/murai-gallery/releases/latest"

    data class ReleaseInfo(
        val tagName: String,
        val name: String,
        val body: String,
        val apkUrl: String?,
        val publishedAt: String
    )

    sealed interface CheckResult {
        data class Success(val release: ReleaseInfo, val isNewer: Boolean) : CheckResult
        data class Offline(val reason: String) : CheckResult
    }

    suspend fun check(currentVersion: String): CheckResult = withContext(Dispatchers.IO) {
        val connection: HttpURLConnection
        try {
            connection = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "MuraiGallery/$currentVersion")
            }
        } catch (t: Throwable) {
            return@withContext CheckResult.Offline(t.message ?: "network")
        }
        try {
            val code = connection.responseCode
            if (code != 200) return@withContext CheckResult.Offline("HTTP $code")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = a.optString("browser_download_url")
                        break
                    }
                }
            }
            val tagName = json.optString("tag_name", "")
            val release = ReleaseInfo(
                tagName = tagName,
                name = json.optString("name", tagName),
                body = json.optString("body", ""),
                apkUrl = apkUrl,
                publishedAt = json.optString("published_at", "")
            )
            CheckResult.Success(release, isNewer(tagName.removePrefix("v"), currentVersion))
        } catch (t: Throwable) {
            CheckResult.Offline(t.message ?: "offline")
        } finally {
            connection.disconnect()
        }
    }

    /** Compare two dotted versions; 2.0.10 > 2.0.9, missing parts = 0. */
    fun isNewer(candidate: String, current: String): Boolean {
        val c = candidate.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val cur = current.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(c.size, cur.size)) {
            val a = c.getOrElse(i) { 0 }
            val b = cur.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
