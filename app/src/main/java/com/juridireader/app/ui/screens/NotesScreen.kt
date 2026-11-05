package com.juridireader.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juridireader.app.data.local.entity.ArticleWithUserData
import com.juridireader.app.ui.model.HighlightColor
import com.juridireader.app.ui.viewmodel.LawViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    viewModel: LawViewModel,
    onNavigateToArticle: (String, String) -> Unit
) {
    val customizedArticles by viewModel.customizedArticles.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Grifos & Anotações", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        if (customizedArticles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Nenhum grifo ou anotação registrado.\nAo estudar qualquer artigo, você pode grifar em 4 cores ou adicionar notas pessoais.",
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(customizedArticles, key = { it.id }) { article ->
                    CustomizedItemCard(
                        article = article,
                        onClick = { onNavigateToArticle(article.lawCode, article.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun CustomizedItemCard(
    article: ArticleWithUserData,
    onClick: () -> Unit
) {
    val highlight = HighlightColor.fromName(article.highlightColor)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SuggestionChip(
                        onClick = onClick,
                        label = { Text(article.lawCode, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                    )
                    Text(
                        text = article.articleNumber,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (highlight != HighlightColor.NONE) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = highlight.composeColor
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Highlight,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = Color.Black
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    highlight.label,
                                    fontSize = 10.sp,
                                    color = Color.Black,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = article.content,
                maxLines = 2,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Trechos Grifados no Artigo
            if (article.textHighlights.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    article.textHighlights.forEach { th ->
                        val hlColor = HighlightColor.fromName(th.color)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = hlColor.composeColor.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Highlight,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = Color.Black
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "“${th.selectedText}”",
                                    fontSize = 12.sp,
                                    fontStyle = FontStyle.Italic,
                                    color = Color.Black,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }

            // Bloco de nota do usuário
            if (!article.userNote.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFFEF3C7)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.EditNote,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = article.userNote,
                            fontSize = 13.sp,
                            fontStyle = FontStyle.Italic,
                            color = Color(0xFF78350F)
                        )
                    }
                }
            }
        }
    }
}
