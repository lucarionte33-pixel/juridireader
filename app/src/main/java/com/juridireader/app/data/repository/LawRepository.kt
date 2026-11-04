package com.juridireader.app.data.repository

import com.juridireader.app.data.local.AppDatabase
import com.juridireader.app.data.local.entity.ArticleWithUserData
import com.juridireader.app.data.local.entity.LawMetadataEntity
import com.juridireader.app.data.local.entity.UserFavoriteEntity
import com.juridireader.app.data.local.entity.UserHighlightEntity
import com.juridireader.app.data.local.entity.UserNoteEntity
import kotlinx.coroutines.flow.Flow

class LawRepository(private val database: AppDatabase) {

    private val lawDao = database.lawDao()
    private val customizationDao = database.userCustomizationDao()

    fun getAllLaws(): Flow<List<LawMetadataEntity>> = lawDao.getAllLaws()

    fun getAllCategories(): Flow<List<String>> = lawDao.getAllCategories()

    fun getArticlesForLaw(lawCode: String): Flow<List<ArticleWithUserData>> =
        lawDao.getArticlesWithUserDataForLaw(lawCode)

    fun searchArticles(query: String, category: String? = null): Flow<List<ArticleWithUserData>> {
        val sanitizedQuery = query.trim().replace("\"", "").replace("'", "")
        if (sanitizedQuery.isEmpty()) {
            return lawDao.getFavoriteArticles()
        }
        val ftsExpression = "$sanitizedQuery*"
        return if (category.isNullOrBlank() || category == "Todas") {
            lawDao.searchArticlesFts(ftsExpression)
        } else {
            lawDao.searchArticlesFtsByCategory(ftsExpression, category)
        }
    }

    fun getFavorites(): Flow<List<ArticleWithUserData>> = lawDao.getFavoriteArticles()

    fun getCustomizedArticles(): Flow<List<ArticleWithUserData>> = lawDao.getCustomizedArticles()

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
