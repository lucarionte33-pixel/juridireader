package com.juridireader.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.juridireader.app.data.local.dao.LawDao
import com.juridireader.app.data.local.dao.UserCustomizationDao
import com.juridireader.app.data.local.entity.ArticleEntity
import com.juridireader.app.data.local.entity.LawMetadataEntity
import com.juridireader.app.data.local.entity.UserFavoriteEntity
import com.juridireader.app.data.local.entity.UserHighlightEntity
import com.juridireader.app.data.local.entity.UserNoteEntity

@Database(
    entities = [
        ArticleEntity::class,
        LawMetadataEntity::class,
        UserFavoriteEntity::class,
        UserNoteEntity::class,
        UserHighlightEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun lawDao(): LawDao
    abstract fun userCustomizationDao(): UserCustomizationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "juridireader.db"
                )
                    .createFromAsset("juridireader.db")
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
