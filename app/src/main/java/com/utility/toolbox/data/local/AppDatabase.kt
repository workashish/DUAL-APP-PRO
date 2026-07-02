package com.utility.toolbox.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.utility.toolbox.data.local.dao.ClonedAppDao
import com.utility.toolbox.data.local.dao.LogDao
import com.utility.toolbox.data.local.dao.WorkspaceDao
import com.utility.toolbox.data.local.entity.ClonedAppEntity
import com.utility.toolbox.data.local.entity.LogEntry
import com.utility.toolbox.data.local.entity.WorkspaceEntity

@Database(
    entities = [
        WorkspaceEntity::class,
        ClonedAppEntity::class,
        LogEntry::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun workspaceDao(): WorkspaceDao
    abstract fun clonedAppDao(): ClonedAppDao
    abstract fun logDao(): LogDao

    companion object {
        private const val DATABASE_NAME = "dualspace_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = Migration(1, 2) { db ->
            db.execSQL("ALTER TABLE cloned_apps ADD COLUMN custom_name TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE cloned_apps ADD COLUMN custom_icon_color INTEGER")
            db.execSQL("ALTER TABLE cloned_apps ADD COLUMN has_shortcut INTEGER NOT NULL DEFAULT 0")
        }

        val MIGRATION_2_3 = Migration(2, 3) { db ->
            db.execSQL("ALTER TABLE cloned_apps ADD COLUMN is_hidden INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE cloned_apps ADD COLUMN fake_icon_path TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE cloned_apps ADD COLUMN device_info_reset_count INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE cloned_apps ADD COLUMN gsf_reset_count INTEGER NOT NULL DEFAULT 0")
        }

        val MIGRATION_3_4 = Migration(3, 4) { db ->
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `logs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `level` TEXT NOT NULL,
                    `tag` TEXT NOT NULL,
                    `message` TEXT NOT NULL,
                    `stack_trace` TEXT,
                    `timestamp` INTEGER NOT NULL,
                    `source` TEXT NOT NULL DEFAULT 'host'
                )"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_logs_level` ON `logs` (`level`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_logs_timestamp` ON `logs` (`timestamp`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_logs_tag` ON `logs` (`tag`)")
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
