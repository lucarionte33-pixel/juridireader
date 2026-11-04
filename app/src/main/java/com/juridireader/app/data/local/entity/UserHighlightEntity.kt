package com.juridireader.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_highlights")
data class UserHighlightEntity(
    @PrimaryKey
    @ColumnInfo(name = "article_id")
    val articleId: String,

    @ColumnInfo(name = "color")
    val color: String, // YELLOW, GREEN, BLUE, PINK

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
