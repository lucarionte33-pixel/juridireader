package com.juridireader.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "law_code")
    val lawCode: String,

    @ColumnInfo(name = "law_title")
    val lawTitle: String,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "article_number")
    val articleNumber: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "order_index")
    val orderIndex: Int
)
