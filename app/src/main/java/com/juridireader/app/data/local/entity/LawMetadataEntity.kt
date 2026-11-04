package com.juridireader.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "laws_metadata")
data class LawMetadataEntity(
    @PrimaryKey
    val code: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "article_count")
    val articleCount: Int,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String
)
