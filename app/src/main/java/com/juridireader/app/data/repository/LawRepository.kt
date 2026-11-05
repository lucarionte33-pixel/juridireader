package com.juridireader.app.data.repository

import android.util.Log
import com.juridireader.app.data.local.AppDatabase
import com.juridireader.app.data.local.entity.ArticleWithUserData
import com.juridireader.app.data.local.entity.LawMetadataEntity
import com.juridireader.app.data.local.entity.UserFavoriteEntity
import com.juridireader.app.data.local.entity.UserHighlightEntity
import com.juridireader.app.data.local.entity.UserNoteEntity
import com.juridireader.app.data.local.entity.UserTextHighlightEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOf

class LawRepository(private val database: AppDatabase) {

    private val lawDao = database.lawDao()
    private val customizationDao = database.userCustomizationDao()

    fun getAllLaws(): Flow<List<LawMetadataEntity>> = lawDao.getAllLaws()

    fun getAllCategories(): Flow<List<String>> = lawDao.getAllCategories()

    fun getArticlesForLaw(lawCode: String): Flow<List<ArticleWithUserData>> {
        return combine(
            lawDao.getArticlesWithUserDataForLaw(lawCode),
            customizationDao.getAllTextHighlights()
        ) { articles, allHighlights ->
            val highlightsByArt = allHighlights.groupBy { it.articleId }
            articles.map { art ->
                art.copy(textHighlights = highlightsByArt[art.id] ?: emptyList())
            }
        }
    }

    fun searchArticles(query: String, category: String? = null): Flow<List<ArticleWithUserData>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return flowOf(emptyList())
        }

        val tokenRegex = Regex("[0-9A-Za-zÀ-ÿ]+")
        val tokens = tokenRegex.findAll(trimmed).map { it.value }.toList()

        if (tokens.isEmpty()) {
            return if (category.isNullOrBlank() || category == "Todas") {
                lawDao.searchArticlesLike(trimmed)
            } else {
                lawDao.searchArticlesLikeByCategory(trimmed, category)
            }
        }

        val ftsExpression = tokens.joinToString(" AND ") { "\"$it\"*" }
        val rawNum = tokens.lastOrNull { token -> token.any { c -> c.isDigit() } } ?: trimmed

        val ftsFlow = if (category.isNullOrBlank() || category == "Todas") {
            lawDao.searchArticlesFts(ftsExpression, rawNum)
        } else {
            lawDao.searchArticlesFtsByCategory(ftsExpression, category, rawNum)
        }

        return ftsFlow.catch { e ->
            Log.w("LawRepository", "FTS5 query falhou ($ftsExpression), usando fallback LIKE", e)
            val fallbackFlow = if (category.isNullOrBlank() || category == "Todas") {
                lawDao.searchArticlesLike(trimmed)
            } else {
                lawDao.searchArticlesLikeByCategory(trimmed, category)
            }
            emitAll(fallbackFlow)
        }
    }

    fun getFavorites(): Flow<List<ArticleWithUserData>> = lawDao.getFavoriteArticles()

    fun getCustomizedArticles(): Flow<List<ArticleWithUserData>> {
        return combine(
            lawDao.getCustomizedArticles(),
            customizationDao.getAllTextHighlights()
        ) { articles, allHighlights ->
            val highlightsByArt = allHighlights.groupBy { it.articleId }
            articles.map { art ->
                art.copy(textHighlights = highlightsByArt[art.id] ?: emptyList())
            }
        }
    }

    fun getAllTextHighlights(): Flow<List<UserTextHighlightEntity>> =
        customizationDao.getAllTextHighlights()

    suspend fun saveTextHighlight(
        articleId: String,
        startOffset: Int,
        endOffset: Int,
        selectedText: String,
        color: String
    ): Long {
        return customizationDao.saveTextHighlight(
            UserTextHighlightEntity(
                articleId = articleId,
                startOffset = startOffset,
                endOffset = endOffset,
                selectedText = selectedText,
                color = color
            )
        )
    }

    suspend fun deleteTextHighlight(id: Long) {
        customizationDao.deleteTextHighlight(id)
    }

    suspend fun clearTextHighlightsForArticle(articleId: String) {
        customizationDao.clearTextHighlightsForArticle(articleId)
    }

    suspend fun toggleFavorite(articleId: String, currentStatus: Boolean) {
        if (currentStatus) {
            customizationDao.removeFavorite(articleId)
        } else {
            customizationDao.setFavorite(UserFavoriteEntity(articleId = articleId))
        }
    }

    suspend fun saveNote(articleId: String, text: String) {
        if (text.isBlank()) {
            customizationDao.deleteNote(articleId)
        } else {
            customizationDao.saveNote(UserNoteEntity(articleId = articleId, noteText = text.trim()))
        }
    }

    suspend fun setHighlight(articleId: String, color: String?) {
        if (color == null || color == "NONE") {
            customizationDao.clearHighlight(articleId)
        } else {
            customizationDao.saveHighlight(UserHighlightEntity(articleId = articleId, color = color))
        }
    }
}
