package com.juridireader.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.juridireader.app.data.local.entity.UserFavoriteEntity
import com.juridireader.app.data.local.entity.UserHighlightEntity
import com.juridireader.app.data.local.entity.UserNoteEntity

@Dao
interface UserCustomizationDao {

    // Favorites
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setFavorite(favorite: UserFavoriteEntity)

    @Query("DELETE FROM user_favorites WHERE article_id = :articleId")
    suspend fun removeFavorite(articleId: String)

    @Query("SELECT COUNT(*) FROM user_favorites WHERE article_id = :articleId")
    suspend fun isFavorite(articleId: String): Int

    // Notes
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveNote(note: UserNoteEntity)

    @Query("DELETE FROM user_notes WHERE article_id = :articleId")
    suspend fun deleteNote(articleId: String)

    @Query("SELECT note_text FROM user_notes WHERE article_id = :articleId LIMIT 1")
    suspend fun getNote(articleId: String): String?

    // Highlights
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveHighlight(highlight: UserHighlightEntity)

    @Query("DELETE FROM user_highlights WHERE article_id = :articleId")
    suspend fun clearHighlight(articleId: String)

    @Query("SELECT color FROM user_highlights WHERE article_id = :articleId LIMIT 1")
    suspend fun getHighlightColor(articleId: String): String?
}
