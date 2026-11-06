package com.juridireader.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.juridireader.app.data.local.dao.LawDao
import com.juridireader.app.data.local.dao.UserCustomizationDao
import com.juridireader.app.data.local.entity.ArticleEntity
import com.juridireader.app.data.local.entity.LawMetadataEntity
import com.juridireader.app.data.local.entity.UserFavoriteEntity
import com.juridireader.app.data.local.entity.UserHighlightEntity
import com.juridireader.app.data.local.entity.UserNoteEntity
import com.juridireader.app.data.local.entity.UserTextHighlightEntity

@Database(
    entities = [
        ArticleEntity::class,
        LawMetadataEntity::class,
        UserFavoriteEntity::class,
        UserNoteEntity::class,
        UserHighlightEntity::class,
        UserTextHighlightEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun lawDao(): LawDao
    abstract fun userCustomizationDao(): UserCustomizationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS user_text_highlights (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        article_id TEXT NOT NULL,
                        start_offset INTEGER NOT NULL,
                        end_offset INTEGER NOT NULL,
                        selected_text TEXT NOT NULL,
                        color TEXT NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_user_text_highlights_art ON user_text_highlights(article_id)")
                try {
                    db.execSQL("ALTER TABLE articles ADD COLUMN revoked_content TEXT")
                } catch (_: Exception) {
                }
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "juridireader.db"
                )
                    .createFromAsset("juridireader.db")
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
