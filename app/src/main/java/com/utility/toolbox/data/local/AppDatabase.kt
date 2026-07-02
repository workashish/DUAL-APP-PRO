package com.utility.toolbox.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.utility.toolbox.data.local.dao.ClonedAppDao
import com.utility.toolbox.data.local.dao.LogDao
import com.utility.toolbox.data.local.entity.ClonedAppEntity
import com.utility.toolbox.data.local.entity.LogEntry

@Database(
    entities = [
        ClonedAppEntity::class,
        LogEntry::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun clonedAppDao(): ClonedAppDao
    abstract fun logDao(): LogDao

    companion object {
        private const val DATABASE_NAME = "dualspace_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_4_5 = Migration(4, 5) { db ->
            // Drop workspace table and foreign key
            db.execSQL("DROP TABLE IF EXISTS workspaces")
            db.execSQL("DROP TABLE IF EXISTS workspace_users")

            // Recreate cloned_apps without workspace_id and with new identity columns
            db.execSQL("DROP TABLE IF EXISTS cloned_apps")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `cloned_apps` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `original_package` TEXT NOT NULL,
                    `clone_package` TEXT NOT NULL,
                    `app_name` TEXT NOT NULL,
                    `custom_name` TEXT NOT NULL DEFAULT '',
                    `custom_icon_color` INTEGER,
                    `version_name` TEXT NOT NULL DEFAULT '',
                    `version_code` INTEGER NOT NULL DEFAULT 0,
                    `apk_path` TEXT NOT NULL DEFAULT '',
                    `data_path` TEXT NOT NULL DEFAULT '',
                    `user_id` INTEGER NOT NULL DEFAULT 0,
                    `android_id` TEXT NOT NULL DEFAULT '',
                    `device_model` TEXT NOT NULL DEFAULT '',
                    `device_brand` TEXT NOT NULL DEFAULT '',
                    `device_fingerprint` TEXT NOT NULL DEFAULT '',
                    `device_serial` TEXT NOT NULL DEFAULT '',
                    `imei` TEXT NOT NULL DEFAULT '',
                    `mac_address` TEXT NOT NULL DEFAULT '',
                    `gsf_id` TEXT NOT NULL DEFAULT '',
                    `gms_installed` INTEGER NOT NULL DEFAULT 0,
                    `gms_install_date` INTEGER NOT NULL DEFAULT 0,
                    `is_installed` INTEGER NOT NULL DEFAULT 0,
                    `is_running` INTEGER NOT NULL DEFAULT 0,
                    `install_date` INTEGER NOT NULL DEFAULT 0,
                    `last_launch` INTEGER NOT NULL DEFAULT 0,
                    `app_size` INTEGER NOT NULL DEFAULT 0,
                    `cache_size` INTEGER NOT NULL DEFAULT 0,
                    `has_shortcut` INTEGER NOT NULL DEFAULT 0,
                    `is_hidden` INTEGER NOT NULL DEFAULT 0,
                    `fake_icon_path` TEXT NOT NULL DEFAULT '',
                    `device_info_reset_count` INTEGER NOT NULL DEFAULT 0,
                    `gsf_reset_count` INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cloned_apps_original_package` ON `cloned_apps` (`original_package`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cloned_apps_clone_package` ON `cloned_apps` (`clone_package`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cloned_apps_user_id` ON `cloned_apps` (`user_id`)")
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
                .addMigrations(MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
