package com.juridireader.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_notes")
data class UserNoteEntity(
    @PrimaryKey
    @ColumnInfo(name = "article_id")
    val articleId: String,

    @ColumnInfo(name = "note_text")
    val noteText: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
