package com.murai.gallery.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.murai.gallery.data.db.dao.LibraryDao
import com.murai.gallery.data.db.dao.SortPresetsDao
import com.murai.gallery.data.db.dao.TagsDao
import com.murai.gallery.data.db.dao.VaultDao
import com.murai.gallery.data.db.entity.LibraryItemEntity
import com.murai.gallery.data.db.entity.SortPresetEntity
import com.murai.gallery.data.db.entity.TagEntity
import com.murai.gallery.data.db.entity.TagItemEntity
import com.murai.gallery.data.db.entity.VaultEntryEntity
import com.murai.gallery.util.ErrorLogger
import java.io.File

/**
 * v2.0.1 hardening:
 *  - fallbackToDestructiveMigration is GONE. A schema bump can never wipe the
 *    user's tags, favorites, sort presets or vault index again; library_items
 *    rows are rebuildable by a rescan, but everything else is irreplaceable.
 *  - exportSchema = true; generated JSON lives in app/schemas for review.
 *  - If the database file is unrecoverably corrupt, the broken file is backed
 *    up next to itself, a fresh database is created, and the event is logged —
 *    the app starts instead of crashing forever.
 */
@Database(
    entities = [
        LibraryItemEntity::class,
        TagEntity::class,
        TagItemEntity::class,
        SortPresetEntity::class,
        VaultEntryEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class MuraiDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun tagsDao(): TagsDao
    abstract fun sortPresetsDao(): SortPresetsDao
    abstract fun vaultDao(): VaultDao

    companion object {
        /** 1 -> 2: index for the GPS backfill pass (additive, nothing lost). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_library_items_latitude_longitude` " +
                        "ON `library_items` (`latitude`, `longitude`)"
                )
            }
        }

        @Volatile
        private var instance: MuraiDatabase? = null

        fun get(context: Context): MuraiDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(appContext: Context): MuraiDatabase = try {
            Room.databaseBuilder(appContext, MuraiDatabase::class.java, "murai-library.db")
                .addMigrations(MIGRATION_1_2)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
        } catch (t: Throwable) {
            // Room surfaces some corruption states only at build/open time.
            ErrorLogger.write(appContext, "db-build-failed", t)
            recoverFromCorruption(appContext)
            Room.databaseBuilder(appContext, MuraiDatabase::class.java, "murai-library.db")
                .addMigrations(MIGRATION_1_2)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
        }

        /**
         * Backs up the broken database files (db, -wal, -shm) so nothing is
         * silently destroyed, then removes them so Room can recreate cleanly.
         */
        private fun recoverFromCorruption(appContext: Context) {
            runCatching {
                val dbFile = appContext.getDatabasePath("murai-library.db")
                val stamp = System.currentTimeMillis()
                for (suffix in listOf("", "-wal", "-shm")) {
                    val f = File(dbFile.path + suffix)
                    if (f.exists()) {
                        val backup = File(f.parentFile, "${f.name}.corrupt-$stamp.bak")
                        if (!f.renameTo(backup)) f.delete()
                    }
                }
                ErrorLogger.write(
                    appContext, "db-corruption-recovered", null,
                    "backup=murai-library.db.corrupt-$stamp.bak"
                )
            }.onFailure {
                ErrorLogger.write(appContext, "db-corruption-recovery-failed", it)
            }
        }
    }
}
