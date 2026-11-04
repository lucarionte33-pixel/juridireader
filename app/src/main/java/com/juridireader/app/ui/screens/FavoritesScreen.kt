package com.juridireader.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juridireader.app.data.local.entity.ArticleWithUserData
import com.juridireader.app.ui.viewmodel.LawViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: LawViewModel,
    onNavigateToLaw: (String) -> Unit
) {
    val favorites by viewModel.favoriteArticles.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Favoritos", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Nenhum artigo favoritado ainda.\nToque na estrela de qualquer artigo para salvá-lo aqui.",
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(favorites, key = { it.id }) { article ->
                    FavoriteItemCard(
                        article = article,
                        onClick = { onNavigateToLaw(article.lawCode) },
                        onRemoveFavorite = { viewModel.toggleFavorite(article.id, true) }
                    )
                }
            }
        }
    }
}

@Composable
fun FavoriteItemCard(
    article: ArticleWithUserData,
    onClick: () -> Unit,
    onRemoveFavorite: () -> Unit
) {
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
                IconButton(onClick = onRemoveFavorite) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Desfavoritar",
                        tint = Color(0xFFFFB300)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = article.content,
                maxLines = 3,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
