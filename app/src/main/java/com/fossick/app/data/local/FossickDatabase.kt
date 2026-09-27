package com.fossick.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ScreenshotEntity::class], version = 3, exportSchema = false)
abstract class FossickDatabase : RoomDatabase() {
    abstract fun screenshotDao(): ScreenshotDao

    companion object {
        @Volatile
        private var instance: FossickDatabase? = null

        fun get(context: Context): FossickDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                FossickDatabase::class.java,
                "fossick.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE screenshots ADD COLUMN mimeType TEXT NOT NULL DEFAULT 'image/*'")
                db.execSQL("ALTER TABLE screenshots ADD COLUMN category TEXT NOT NULL DEFAULT 'SCREENSHOT'")
                db.execSQL("ALTER TABLE screenshots ADD COLUMN size INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE screenshots ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'MEDIASTORE'")
                db.execSQL("ALTER TABLE screenshots ADD COLUMN normalizedText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE screenshots ADD COLUMN structuredEntities TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE screenshots ADD COLUMN contentIndexVersion INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE screenshots ADD COLUMN lastIndexedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE screenshots ADD COLUMN errorState TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE screenshots SET mediaStoreId = -(mediaStoreId * 2 + 1) " +
                        "WHERE category = 'VIDEO' AND mediaStoreId >= 0"
                )
                db.execSQL(
                    "UPDATE screenshots SET mediaStoreId = -(mediaStoreId * 2 + 2) " +
                        "WHERE category IN ('PDF', 'DOCUMENT', 'PRESENTATION', 'SPREADSHEET') " +
                        "AND mediaStoreId >= 0"
                )
            }
        }
    }
}
