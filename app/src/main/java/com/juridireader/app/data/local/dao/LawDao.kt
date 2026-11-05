package com.juridireader.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.SkipQueryVerification
import com.juridireader.app.data.local.entity.ArticleEntity
import com.juridireader.app.data.local.entity.ArticleWithUserData
import com.juridireader.app.data.local.entity.LawMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LawDao {

    @Query("SELECT * FROM laws_metadata ORDER BY code ASC")
    fun getAllLaws(): Flow<List<LawMetadataEntity>>

    @Query("SELECT * FROM laws_metadata WHERE code = :code LIMIT 1")
    suspend fun getLawByCode(code: String): LawMetadataEntity?

    @Query("SELECT DISTINCT category FROM laws_metadata ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>

    @Query("""
        SELECT 
            a.id, 
            a.law_code AS lawCode, 
            a.law_title AS lawTitle, 
            a.category, 
            a.article_number AS articleNumber, 
            a.content, 
            a.order_index AS orderIndex,
            CASE WHEN uf.article_id IS NOT NULL THEN 1 ELSE 0 END AS isFavorite,
            un.note_text AS userNote,
            uh.color AS highlightColor
        FROM articles a
        LEFT JOIN user_favorites uf ON a.id = uf.article_id
        LEFT JOIN user_notes un ON a.id = un.article_id
        LEFT JOIN user_highlights uh ON a.id = uh.article_id
        WHERE a.law_code = :lawCode
        ORDER BY a.order_index ASC
    """)
    fun getArticlesWithUserDataForLaw(lawCode: String): Flow<List<ArticleWithUserData>>

    @SkipQueryVerification
    @Query("""
        SELECT 
            a.id, 
            a.law_code AS lawCode, 
            a.law_title AS lawTitle, 
            a.category, 
            a.article_number AS articleNumber, 
            a.content, 
            a.order_index AS orderIndex,
            CASE WHEN uf.article_id IS NOT NULL THEN 1 ELSE 0 END AS isFavorite,
            un.note_text AS userNote,
            uh.color AS highlightColor
        FROM articles_fts fts
        JOIN articles a ON fts.id = a.id
        LEFT JOIN user_favorites uf ON a.id = uf.article_id
        LEFT JOIN user_notes un ON a.id = un.article_id
        LEFT JOIN user_highlights uh ON a.id = uh.article_id
        WHERE articles_fts MATCH :ftsQuery
        ORDER BY 
            CASE 
                WHEN a.article_number LIKE '%' || :rawQuery || '%' THEN 0
                WHEN a.id LIKE '%' || :rawQuery || '%' THEN 1
                ELSE 2 
            END ASC,
            a.order_index ASC
        LIMIT 100
    """)
    fun searchArticlesFts(ftsQuery: String, rawQuery: String): Flow<List<ArticleWithUserData>>

    @SkipQueryVerification
    @Query("""
        SELECT 
            a.id, 
            a.law_code AS lawCode, 
            a.law_title AS lawTitle, 
            a.category, 
            a.article_number AS articleNumber, 
            a.content, 
            a.order_index AS orderIndex,
            CASE WHEN uf.article_id IS NOT NULL THEN 1 ELSE 0 END AS isFavorite,
            un.note_text AS userNote,
            uh.color AS highlightColor
        FROM articles_fts fts
        JOIN articles a ON fts.id = a.id
        LEFT JOIN user_favorites uf ON a.id = uf.article_id
        LEFT JOIN user_notes un ON a.id = un.article_id
        LEFT JOIN user_highlights uh ON a.id = uh.article_id
        WHERE articles_fts MATCH :ftsQuery AND a.category = :category
        ORDER BY 
            CASE 
                WHEN a.article_number LIKE '%' || :rawQuery || '%' THEN 0
                WHEN a.id LIKE '%' || :rawQuery || '%' THEN 1
                ELSE 2 
            END ASC,
            a.order_index ASC
        LIMIT 100
    """)
    fun searchArticlesFtsByCategory(ftsQuery: String, category: String, rawQuery: String): Flow<List<ArticleWithUserData>>

    @Query("""
        SELECT 
            a.id, 
            a.law_code AS lawCode, 
            a.law_title AS lawTitle, 
            a.category, 
            a.article_number AS articleNumber, 
            a.content, 
            a.order_index AS orderIndex,
            CASE WHEN uf.article_id IS NOT NULL THEN 1 ELSE 0 END AS isFavorite,
            un.note_text AS userNote,
            uh.color AS highlightColor
        FROM articles a
        LEFT JOIN user_favorites uf ON a.id = uf.article_id
        LEFT JOIN user_notes un ON a.id = un.article_id
        LEFT JOIN user_highlights uh ON a.id = uh.article_id
        WHERE a.article_number LIKE '%' || :query || '%' OR a.content LIKE '%' || :query || '%'
        ORDER BY 
            CASE 
                WHEN a.article_number LIKE '%' || :query || '%' THEN 0 
                ELSE 1 
            END ASC,
            a.order_index ASC
        LIMIT 100
    """)
    fun searchArticlesLike(query: String): Flow<List<ArticleWithUserData>>

    @Query("""
        SELECT 
            a.id, 
            a.law_code AS lawCode, 
            a.law_title AS lawTitle, 
            a.category, 
            a.article_number AS articleNumber, 
            a.content, 
            a.order_index AS orderIndex,
            CASE WHEN uf.article_id IS NOT NULL THEN 1 ELSE 0 END AS isFavorite,
            un.note_text AS userNote,
            uh.color AS highlightColor
        FROM articles a
        LEFT JOIN user_favorites uf ON a.id = uf.article_id
        LEFT JOIN user_notes un ON a.id = un.article_id
        LEFT JOIN user_highlights uh ON a.id = uh.article_id
        WHERE (a.article_number LIKE '%' || :query || '%' OR a.content LIKE '%' || :query || '%') AND a.category = :category
        ORDER BY 
            CASE 
                WHEN a.article_number LIKE '%' || :query || '%' THEN 0 
                ELSE 1 
            END ASC,
            a.order_index ASC
        LIMIT 100
    """)
    fun searchArticlesLikeByCategory(query: String, category: String): Flow<List<ArticleWithUserData>>

    @Query("""
        SELECT 
            a.id, 
            a.law_code AS lawCode, 
            a.law_title AS lawTitle, 
            a.category, 
            a.article_number AS articleNumber, 
            a.content, 
            a.order_index AS orderIndex,
            1 AS isFavorite,
            un.note_text AS userNote,
            uh.color AS highlightColor
        FROM user_favorites uf
        JOIN articles a ON uf.article_id = a.id
        LEFT JOIN user_notes un ON a.id = un.article_id
        LEFT JOIN user_highlights uh ON a.id = uh.article_id
        ORDER BY uf.created_at DESC
    """)
    fun getFavoriteArticles(): Flow<List<ArticleWithUserData>>

    @Query("""
        SELECT 
            a.id, 
            a.law_code AS lawCode, 
            a.law_title AS lawTitle, 
            a.category, 
            a.article_number AS articleNumber, 
            a.content, 
            a.order_index AS orderIndex,
            CASE WHEN uf.article_id IS NOT NULL THEN 1 ELSE 0 END AS isFavorite,
            un.note_text AS userNote,
            uh.color AS highlightColor
        FROM articles a
        LEFT JOIN user_favorites uf ON a.id = uf.article_id
        LEFT JOIN user_notes un ON a.id = un.article_id
        LEFT JOIN user_highlights uh ON a.id = uh.article_id
        WHERE un.note_text IS NOT NULL OR uh.color IS NOT NULL OR a.id IN (SELECT DISTINCT article_id FROM user_text_highlights)
        ORDER BY COALESCE(un.updated_at, uh.updated_at, 0) DESC
    """)
    fun getCustomizedArticles(): Flow<List<ArticleWithUserData>>

    @Query("SELECT * FROM articles WHERE id = :id LIMIT 1")
    suspend fun getArticleById(id: String): ArticleEntity?
}
