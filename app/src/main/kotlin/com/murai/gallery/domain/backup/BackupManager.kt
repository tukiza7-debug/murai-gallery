package com.murai.gallery.domain.backup

import android.content.Context
import com.murai.gallery.data.db.MuraiDatabase
import com.murai.gallery.data.db.entity.SortPresetEntity
import com.murai.gallery.data.db.entity.TagEntity
import com.murai.gallery.data.db.entity.TagItemEntity
import com.murai.gallery.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Exports and restores the app's own data (settings, tags, favorites flags
 * and sort presets) as a single portable zip. Media files themselves stay in
 * the system gallery and are intentionally not duplicated.
 */
class BackupManager(
    private val context: Context,
    private val db: MuraiDatabase,
    private val settings: SettingsRepository
) {

    data class BackupSummary(val items: Int, val tags: Int, val presets: Int, val settings: Int)

    suspend fun exportTo(output: OutputStream): BackupSummary = withContext(Dispatchers.IO) {
        val favIds = db.libraryDao().observeFavorites().first().map { it.id.toInt() }
        val tagList = db.tagsDao().observeTags().first().map { Triple(it.id, it.name, it.colorArgb) }
        val presets = db.sortPresetsDao().observeAll().first()
        val prefs = settings.exportAll()

        val root = JSONObject()
        root.put("format", 1)
        root.put("app", "murai-gallery")
        root.put("favorites", JSONArray(favIds))
        val tagsArr = JSONArray()
        for ((id, name, color) in tagList) {
            tagsArr.put(JSONObject().put("name", name).put("color", color))
        }
        root.put("tags", tagsArr)
        val presetsArr = JSONArray()
        for (p in presets) {
            presetsArr.put(
                JSONObject()
                    .put("name", p.name)
                    .put("sort", p.sortOptionId)
                    .put("asc", p.ascending)
                    .put("group", p.groupId)
            )
        }
        root.put("sort_presets", presetsArr)
        val prefsObj = JSONObject()
        for ((k, v) in prefs) prefsObj.put(k, v)
        root.put("settings", prefsObj)

        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("murai-backup.json"))
            zip.write(root.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        BackupSummary(favIds.size, tagList.size, presets.size, prefs.size)
    }

    suspend fun importFrom(input: InputStream): BackupSummary = withContext(Dispatchers.IO) {
        var items = 0
        var tags = 0
        var presets = 0
        var settingsCount = 0
        ZipInputStream(input.buffered()).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                if (entry.name == "murai-backup.json") {
                    val text = zip.readBytes().toString(Charsets.UTF_8)
                    val root = JSONObject(text)
                    if (root.optString("app") == "murai-gallery") {
                        // favorites flags are restored for items still present
                        val favIds = root.optJSONArray("favorites") ?: JSONArray()
                        val present = db.libraryDao().allIds().toSet()
                        for (i in 0 until favIds.length()) {
                            val id = favIds.optLong(i, -1)
                            if (id in present) {
                                db.libraryDao().setFavorite(id, true)
                                items++
                            }
                        }
                        val tagsArr = root.optJSONArray("tags") ?: JSONArray()
                        for (i in 0 until tagsArr.length()) {
                            val o = tagsArr.optJSONObject(i) ?: continue
                            val id = db.tagsDao().insertTag(
                                TagEntity(name = o.optString("name"), colorArgb = o.optLong("color"))
                            )
                            tags++
                            val members = root.optJSONArray("tag_members_$i")
                            members?.let {
                                for (j in 0 until it.length()) {
                                    db.tagsDao().insertJoin(TagItemEntity(id, it.optLong(j)))
                                }
                            }
                        }
                        val presetsArr = root.optJSONArray("sort_presets") ?: JSONArray()
                        for (i in 0 until presetsArr.length()) {
                            val o = presetsArr.optJSONObject(i) ?: continue
                            db.sortPresetsDao().insert(
                                SortPresetEntity(
                                    name = o.optString("name"),
                                    sortOptionId = o.optString("sort"),
                                    ascending = o.optBoolean("asc"),
                                    groupId = o.optString("group"),
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                            presets++
                        }
                        val prefsObj = root.optJSONObject("settings")
                        if (prefsObj != null) {
                            val map = mutableMapOf<String, String>()
                            for (key in prefsObj.keys()) map[key] = prefsObj.optString(key)
                            settings.importAll(map)
                            settingsCount = map.size
                        }
                    }
                }
                entry = zip.nextEntry
            }
        }
        BackupSummary(items, tags, presets, settingsCount)
    }

    fun suggestFileName(): String =
        "murai-backup-${java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
            .format(java.util.Date())}.zip"

    fun stagedExportFile(): File = File(context.cacheDir, suggestFileName())
}
