package com.juridireader.app.data.local.entity

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
    val highlightColor: String? = null
)
