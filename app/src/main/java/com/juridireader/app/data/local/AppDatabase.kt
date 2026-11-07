package com.juridireader.app.data.local

import android.content.Context
import android.util.Log
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
import java.io.File
import java.io.FileOutputStream

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

        const val EXPECTED_IDENTITY_HASH = "bfb01156b90d43da6b26ba19337ebd54"

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

        private fun preparePrepackagedDatabase(context: Context) {
            try {
                val dbFile = context.getDatabasePath("juridireader.db")
                if (dbFile.exists()) {
                    var needsFreshCopy = false
                    var lawCount = 0
                    var hasRevoked = false

                    try {
                        android.database.sqlite.SQLiteDatabase.openDatabase(
                            dbFile.path,
                            null,
                            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
                        ).use { db ->
                            try {
                                db.rawQuery("SELECT count(*) FROM laws_metadata", null).use { cursor ->
                                    if (cursor.moveToFirst()) {
                                        lawCount = cursor.getInt(0)
                                    }
                                }
                            } catch (_: Exception) {
                                lawCount = 0
                            }

                            try {
                                db.rawQuery("PRAGMA table_info(articles)", null).use { cursor ->
                                    while (cursor.moveToNext()) {
                                        if (cursor.getString(1) == "revoked_content") {
                                            hasRevoked = true
                                            break
                                        }
                                    }
                                }
                            } catch (_: Exception) {
                                hasRevoked = false
                            }

                            if (lawCount == 0 || !hasRevoked) {
                                needsFreshCopy = true
                            } else {
                                try {
                                    db.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '$EXPECTED_IDENTITY_HASH')")
                                    db.version = 2
                                } catch (e: Exception) {
                                    Log.e("AppDatabase", "Erro ao ajustar identity_hash", e)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("AppDatabase", "Banco corrompido ou inacessível no disco", e)
                        needsFreshCopy = true
                    }

                    if (needsFreshCopy) {
                        Log.w("AppDatabase", "Restaurando banco completo de assets (leis=$lawCount, hasRevoked=$hasRevoked)")

                        val favorites = mutableListOf<Pair<String, Long>>()
                        val notes = mutableListOf<Triple<String, String, Long>>()
                        val highlights = mutableListOf<Triple<String, String, Long>>()
                        val textHighlights = mutableListOf<List<Any>>()

                        try {
                            android.database.sqlite.SQLiteDatabase.openDatabase(
                                dbFile.path,
                                null,
                                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                            ).use { db ->
                                try {
                                    db.rawQuery("SELECT article_id, created_at FROM user_favorites", null).use { c ->
                                        while (c.moveToNext()) favorites.add(Pair(c.getString(0), c.getLong(1)))
                                    }
                                } catch (_: Exception) {}

                                try {
                                    db.rawQuery("SELECT article_id, note_text, updated_at FROM user_notes", null).use { c ->
                                        while (c.moveToNext()) notes.add(Triple(c.getString(0), c.getString(1), c.getLong(2)))
                                    }
                                } catch (_: Exception) {}

                                try {
                                    db.rawQuery("SELECT article_id, color, updated_at FROM user_highlights", null).use { c ->
                                        while (c.moveToNext()) highlights.add(Triple(c.getString(0), c.getString(1), c.getLong(2)))
                                    }
                                } catch (_: Exception) {}

                                try {
                                    db.rawQuery("SELECT article_id, start_offset, end_offset, selected_text, color, created_at FROM user_text_highlights", null).use { c ->
                                        while (c.moveToNext()) {
                                            textHighlights.add(listOf(c.getString(0), c.getInt(1), c.getInt(2), c.getString(3), c.getString(4), c.getLong(5)))
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        } catch (_: Exception) {}

                        dbFile.delete()
                        File("${dbFile.path}-wal").delete()
                        File("${dbFile.path}-shm").delete()
                        dbFile.parentFile?.mkdirs()

                        context.assets.open("juridireader.db").use { input ->
                            FileOutputStream(dbFile).use { output ->
                                input.copyTo(output)
                            }
                        }

                        if (favorites.isNotEmpty() || notes.isNotEmpty() || highlights.isNotEmpty() || textHighlights.isNotEmpty()) {
                            try {
                                android.database.sqlite.SQLiteDatabase.openDatabase(
                                    dbFile.path,
                                    null,
                                    android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
                                ).use { newDb ->
                                    for ((artId, createdAt) in favorites) {
                                        newDb.execSQL("INSERT OR REPLACE INTO user_favorites (article_id, created_at) VALUES (?, ?)", arrayOf(artId, createdAt))
                                    }
                                    for ((artId, text, updatedAt) in notes) {
                                        newDb.execSQL("INSERT OR REPLACE INTO user_notes (article_id, note_text, updated_at) VALUES (?, ?, ?)", arrayOf(artId, text, updatedAt))
                                    }
                                    for ((artId, color, updatedAt) in highlights) {
                                        newDb.execSQL("INSERT OR REPLACE INTO user_highlights (article_id, color, updated_at) VALUES (?, ?, ?)", arrayOf(artId, color, updatedAt))
                                    }
                                    for (row in textHighlights) {
                                        newDb.execSQL("INSERT OR REPLACE INTO user_text_highlights (article_id, start_offset, end_offset, selected_text, color, created_at) VALUES (?, ?, ?, ?, ?, ?)", row.toTypedArray())
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("AppDatabase", "Erro ao restaurar personalizações", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AppDatabase", "Erro geral em preparePrepackagedDatabase", e)
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                preparePrepackagedDatabase(context.applicationContext)
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
