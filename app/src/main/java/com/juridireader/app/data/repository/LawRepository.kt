package com.juridireader.app.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.juridireader.app.data.local.AppDatabase
import com.juridireader.app.data.local.entity.ArticleWithUserData
import com.juridireader.app.data.local.entity.LawMetadataEntity
import com.juridireader.app.data.local.entity.UserFavoriteEntity
import com.juridireader.app.data.local.entity.UserHighlightEntity
import com.juridireader.app.data.local.entity.UserNoteEntity
import com.juridireader.app.data.local.entity.UserTextHighlightEntity
import com.juridireader.app.util.ImportMode
import com.juridireader.app.util.ImportSummary
import com.juridireader.app.util.UserBackupPayload
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

    fun getLawCodesWithRevokedContent(): Flow<List<String>> = lawDao.getLawCodesWithRevokedContent()

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

    suspend fun exportBackupPayload(): UserBackupPayload {
        return UserBackupPayload(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            favorites = customizationDao.getAllFavoritesSync(),
            notes = customizationDao.getAllNotesSync(),
            highlights = customizationDao.getAllHighlightsSync(),
            textHighlights = customizationDao.getAllTextHighlightsSync()
        )
    }

    suspend fun importBackupPayload(payload: UserBackupPayload, mode: ImportMode): ImportSummary {
        return database.withTransaction {
            when (mode) {
                ImportMode.REPLACE -> {
                    customizationDao.deleteAllFavorites()
                    customizationDao.deleteAllNotes()
                    customizationDao.deleteAllHighlights()
                    customizationDao.deleteAllTextHighlights()

                    if (payload.favorites.isNotEmpty()) {
                        customizationDao.insertFavorites(payload.favorites)
                    }
                    if (payload.notes.isNotEmpty()) {
                        customizationDao.insertNotes(payload.notes)
                    }
                    if (payload.highlights.isNotEmpty()) {
                        customizationDao.insertHighlights(payload.highlights)
                    }
                    if (payload.textHighlights.isNotEmpty()) {
                        // Reseta o id para auto-geração limpa no novo dispositivo
                        val cleanTextHighlights = payload.textHighlights.map { it.copy(id = 0) }
                        customizationDao.insertTextHighlights(cleanTextHighlights)
                    }

                    ImportSummary(
                        addedFavorites = payload.favorites.size,
                        addedOrUpdatedNotes = payload.notes.size,
                        addedHighlights = payload.highlights.size,
                        addedTextHighlights = payload.textHighlights.size,
                        mode = ImportMode.REPLACE
                    )
                }

                ImportMode.MERGE -> {
                    val existingFavIds = customizationDao.getAllFavoritesSync().map { it.articleId }.toSet()
                    val newFavorites = payload.favorites.filter { it.articleId !in existingFavIds }
                    if (newFavorites.isNotEmpty()) {
                        customizationDao.insertFavorites(newFavorites)
                    }

                    val existingNotesMap = customizationDao.getAllNotesSync().associateBy { it.articleId }
                    val notesToSave = mutableListOf<UserNoteEntity>()
                    for (importedNote in payload.notes) {
                        val existing = existingNotesMap[importedNote.articleId]
                        if (existing == null) {
                            notesToSave.add(importedNote)
                        } else {
                            val existingTrimmed = existing.noteText.trim()
                            val importedTrimmed = importedNote.noteText.trim()
                            if (existingTrimmed != importedTrimmed && !existingTrimmed.contains(importedTrimmed)) {
                                notesToSave.add(
                                    existing.copy(
                                        noteText = "$existingTrimmed\n\n[Importado]: $importedTrimmed",
                                        updatedAt = maxOf(existing.updatedAt, importedNote.updatedAt)
                                    )
                                )
                            }
                        }
                    }
                    if (notesToSave.isNotEmpty()) {
                        customizationDao.insertNotes(notesToSave)
                    }

                    val existingHlIds = customizationDao.getAllHighlightsSync().map { it.articleId }.toSet()
                    val newHighlights = payload.highlights.filter { it.articleId !in existingHlIds }
                    if (newHighlights.isNotEmpty()) {
                        customizationDao.insertHighlights(newHighlights)
                    }

                    val existingTextHlKeys = customizationDao.getAllTextHighlightsSync().map {
                        "${it.articleId}|${it.startOffset}|${it.endOffset}|${it.selectedText}|${it.color}"
                    }.toSet()
                    val newTextHighlights = payload.textHighlights.filter {
                        "${it.articleId}|${it.startOffset}|${it.endOffset}|${it.selectedText}|${it.color}" !in existingTextHlKeys
                    }.map { it.copy(id = 0) }
                    if (newTextHighlights.isNotEmpty()) {
                        customizationDao.insertTextHighlights(newTextHighlights)
                    }

                    ImportSummary(
                        addedFavorites = newFavorites.size,
                        addedOrUpdatedNotes = notesToSave.size,
                        addedHighlights = newHighlights.size,
                        addedTextHighlights = newTextHighlights.size,
                        mode = ImportMode.MERGE
                    )
                }
            }
        }
    }
}
