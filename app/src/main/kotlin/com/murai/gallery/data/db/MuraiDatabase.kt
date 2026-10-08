package com.murai.gallery.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.murai.gallery.data.db.dao.LibraryDao
import com.murai.gallery.data.db.dao.SortPresetsDao
import com.murai.gallery.data.db.dao.TagsDao
import com.murai.gallery.data.db.dao.VaultDao
import com.murai.gallery.data.db.entity.LibraryItemEntity
import com.murai.gallery.data.db.entity.SortPresetEntity
import com.murai.gallery.data.db.entity.TagEntity
import com.murai.gallery.data.db.entity.TagItemEntity
import com.murai.gallery.data.db.entity.VaultEntryEntity

@Database(
    entities = [
        LibraryItemEntity::class,
        TagEntity::class,
        TagItemEntity::class,
        SortPresetEntity::class,
        VaultEntryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MuraiDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun tagsDao(): TagsDao
    abstract fun sortPresetsDao(): SortPresetsDao
    abstract fun vaultDao(): VaultDao

    companion object {
        @Volatile
        private var instance: MuraiDatabase? = null

        fun get(context: Context): MuraiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MuraiDatabase::class.java,
                    "murai-library.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
