package com.juridireader.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_favorites")
data class UserFavoriteEntity(
    @PrimaryKey
    @ColumnInfo(name = "article_id")
    val articleId: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
