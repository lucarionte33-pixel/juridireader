package com.juridireader.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.juridireader.app.data.local.entity.UserFavoriteEntity
import com.juridireader.app.data.local.entity.UserHighlightEntity
import com.juridireader.app.data.local.entity.UserNoteEntity
import com.juridireader.app.data.local.entity.UserTextHighlightEntity
import kotlinx.coroutines.flow.Flow

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

    // Highlights do Artigo Inteiro (compatibilidade)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveHighlight(highlight: UserHighlightEntity)

    @Query("DELETE FROM user_highlights WHERE article_id = :articleId")
    suspend fun clearHighlight(articleId: String)

    @Query("SELECT color FROM user_highlights WHERE article_id = :articleId LIMIT 1")
    suspend fun getHighlightColor(articleId: String): String?

    // Grifos Selecionáveis por Trecho de Texto
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTextHighlight(highlight: UserTextHighlightEntity): Long

    @Query("DELETE FROM user_text_highlights WHERE id = :id")
    suspend fun deleteTextHighlight(id: Long)

    @Query("DELETE FROM user_text_highlights WHERE article_id = :articleId")
    suspend fun clearTextHighlightsForArticle(articleId: String)

    @Query("SELECT * FROM user_text_highlights WHERE article_id = :articleId ORDER BY start_offset ASC")
    fun getTextHighlightsForArticle(articleId: String): Flow<List<UserTextHighlightEntity>>

    @Query("SELECT * FROM user_text_highlights ORDER BY created_at DESC")
    fun getAllTextHighlights(): Flow<List<UserTextHighlightEntity>>
}
