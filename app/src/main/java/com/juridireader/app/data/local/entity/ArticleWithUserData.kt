package com.juridireader.app.data.local.entity

import androidx.room.Ignore

data class ArticleWithUserData(
    val id: String,
    val lawCode: String,
    val lawTitle: String,
    val category: String,
    val articleNumber: String,
    val content: String,
    val orderIndex: Int,
    val isFavorite: Boolean = false,
    val userNote: String? = null,
    val highlightColor: String? = null,
    @Ignore
    val textHighlights: List<UserTextHighlightEntity> = emptyList()
) {
    // Construtor usado pelo Room para mapear diretamente do Cursor
    constructor(
        id: String,
        lawCode: String,
        lawTitle: String,
        category: String,
        articleNumber: String,
        content: String,
        orderIndex: Int,
        isFavorite: Boolean,
        userNote: String?,
        highlightColor: String?
    ) : this(
        id = id,
        lawCode = lawCode,
        lawTitle = lawTitle,
        category = category,
        articleNumber = articleNumber,
        content = content,
        orderIndex = orderIndex,
        isFavorite = isFavorite,
        userNote = userNote,
        highlightColor = highlightColor,
        textHighlights = emptyList()
    )
}
