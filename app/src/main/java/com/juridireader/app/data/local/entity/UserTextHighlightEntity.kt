package com.juridireader.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_text_highlights",
    indices = [
        Index(value = ["article_id"], name = "idx_user_text_highlights_art")
    ]
)
data class UserTextHighlightEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "article_id")
    val articleId: String,
    @ColumnInfo(name = "start_offset")
    val startOffset: Int,
    @ColumnInfo(name = "end_offset")
    val endOffset: Int,
    @ColumnInfo(name = "selected_text")
    val selectedText: String,
    val color: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
