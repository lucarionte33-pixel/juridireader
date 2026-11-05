package com.juridireader.app.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.juridireader.app.data.local.entity.ArticleWithUserData
import com.juridireader.app.ui.model.AppThemeMode
import com.juridireader.app.ui.model.HighlightColor
import com.juridireader.app.ui.viewmodel.LawViewModel
import com.juridireader.app.util.PdfExporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LawReaderScreen(
    lawCode: String,
    targetArticleId: String? = null,
    viewModel: LawViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(lawCode) {
        viewModel.selectLaw(lawCode)
    }

    val articles by viewModel.currentLawArticles.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val fontSizeSp by viewModel.fontSizeSp.collectAsState()

    val listState = rememberLazyListState()
    var hasScrolledToTarget by remember(lawCode, targetArticleId) { mutableStateOf(false) }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var activeNoteArticle by remember { mutableStateOf<ArticleWithUserData?>(null) }
    var internalFilter by remember { mutableStateOf("") }

    val displayedArticles = remember(articles, internalFilter) {
        if (internalFilter.isBlank()) articles
        else articles.filter {
            it.articleNumber.contains(internalFilter, ignoreCase = true) ||
                    it.content.contains(internalFilter, ignoreCase = true)
        }
    }

    LaunchedEffect(displayedArticles, targetArticleId) {
        if (!hasScrolledToTarget && !targetArticleId.isNullOrBlank() && displayedArticles.isNotEmpty()) {
            val targetIndex = displayedArticles.indexOfFirst { it.id == targetArticleId }
            if (targetIndex >= 0) {
                listState.animateScrollToItem(targetIndex)
                hasScrolledToTarget = true
            }
        }
    }

    val lawTitle = remember(articles) {
        articles.firstOrNull()?.lawTitle ?: lawCode
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = lawCode,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "${articles.size} artigos",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    // Exportar PDF
                    IconButton(onClick = {
                        val pdfFile = PdfExporter.exportLawToPdf(context, lawTitle, articles)
                        if (pdfFile != null && pdfFile.exists()) {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                pdfFile
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Compartilhar PDF"))
                        } else {
                            Toast.makeText(context, "Erro ao gerar PDF", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Exportar PDF")
                    }

                    // Configurações de Leitura
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.FormatSize, contentDescription = "Configurar exibição")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Barra de busca rápida dentro da lei
            OutlinedTextField(
                value = internalFilter,
                onValueChange = { internalFilter = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text("Filtrar artigos nesta lei...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (internalFilter.isNotEmpty()) {
                        IconButton(onClick = { internalFilter = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpar")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            // Lista de Artigos
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp)
            ) {
                items(displayedArticles, key = { it.id }) { article ->
                    ArticleCard(
                        article = article,
                        isTargeted = article.id == targetArticleId,
                        fontSizeSp = fontSizeSp,
                        onToggleFavorite = { viewModel.toggleFavorite(article.id, article.isFavorite) },
                        onHighlightSelected = { color -> viewModel.setHighlight(article.id, color) },
                        onOpenNoteDialog = { activeNoteArticle = article }
                    )
                }
            }
        }
    }

    // Diálogo de Configuração de Leitura (Fonte e Modo Noturno / Sépia)
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Aparência do Leitor") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Tamanho da Fonte: ${fontSizeSp.toInt()} sp", fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = fontSizeSp,
                        onValueChange = { viewModel.setFontSize(it) },
                        valueRange = 12f..28f,
                        steps = 7
                    )

                    Text("Tema de Leitura", fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ThemeOptionChip(
                            label = "Claro",
                            isSelected = themeMode == AppThemeMode.LIGHT,
                            onClick = { viewModel.setThemeMode(AppThemeMode.LIGHT) }
                        )
                        ThemeOptionChip(
                            label = "Sépia",
                            isSelected = themeMode == AppThemeMode.SEPIA,
                            onClick = { viewModel.setThemeMode(AppThemeMode.SEPIA) }
                        )
                        ThemeOptionChip(
                            label = "Escuro",
                            isSelected = themeMode == AppThemeMode.DARK,
                            onClick = { viewModel.setThemeMode(AppThemeMode.DARK) }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Fechar")
                }
            }
        )
    }

    // Diálogo de Edição de Anotação do Artigo
    activeNoteArticle?.let { article ->
        var noteDraft by remember { mutableStateOf(article.userNote ?: "") }
        AlertDialog(
            onDismissRequest = { activeNoteArticle = null },
            title = { Text("Anotação • ${article.articleNumber}") },
            text = {
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    placeholder = { Text("Escreva suas observações ou jurisprudências aqui...") },
                    maxLines = 6
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveNote(article.id, noteDraft)
                    activeNoteArticle = null
                }) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                if (!article.userNote.isNullOrBlank()) {
                    TextButton(onClick = {
                        viewModel.saveNote(article.id, "")
                        activeNoteArticle = null
                    }) {
                        Text("Excluir", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    TextButton(onClick = { activeNoteArticle = null }) {
                        Text("Cancelar")
                    }
                }
            }
        )
    }
}

@Composable
fun ThemeOptionChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(label) }
    )
}

@Composable
fun ArticleCard(
    article: ArticleWithUserData,
    isTargeted: Boolean = false,
    fontSizeSp: Float,
    onToggleFavorite: () -> Unit,
    onHighlightSelected: (String?) -> Unit,
    onOpenNoteDialog: () -> Unit
) {
    val highlightColor = remember(article.highlightColor) {
        HighlightColor.fromName(article.highlightColor)
    }

    val cardBg = if (highlightColor != HighlightColor.NONE) {
        highlightColor.composeColor.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isTargeted) 4.dp else 1.dp),
        border = if (isTargeted) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Linha do Artigo com Botão de Favorito
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = article.articleNumber,
                    fontWeight = FontWeight.Bold,
                    fontSize = (fontSizeSp + 1f).sp,
                    color = MaterialTheme.colorScheme.primary
                )

                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (article.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favoritar",
                        tint = if (article.isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Conteúdo do Artigo
            Text(
                text = article.content,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * 1.45f).sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Preview de Anotação (se houver)
            if (!article.userNote.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFEF3C7)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(18.dp)
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

            Spacer(modifier = Modifier.height(10.dp))

            // Barra de Ações do Artigo: 4 Cores de Grifo + Anotação
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cores de grifo
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HighlightCircle(color = HighlightColor.YELLOW, isSelected = highlightColor == HighlightColor.YELLOW) {
                        onHighlightSelected(if (highlightColor == HighlightColor.YELLOW) null else HighlightColor.YELLOW.name)
                    }
                    HighlightCircle(color = HighlightColor.GREEN, isSelected = highlightColor == HighlightColor.GREEN) {
                        onHighlightSelected(if (highlightColor == HighlightColor.GREEN) null else HighlightColor.GREEN.name)
                    }
                    HighlightCircle(color = HighlightColor.BLUE, isSelected = highlightColor == HighlightColor.BLUE) {
                        onHighlightSelected(if (highlightColor == HighlightColor.BLUE) null else HighlightColor.BLUE.name)
                    }
                    HighlightCircle(color = HighlightColor.PINK, isSelected = highlightColor == HighlightColor.PINK) {
                        onHighlightSelected(if (highlightColor == HighlightColor.PINK) null else HighlightColor.PINK.name)
                    }
                    if (highlightColor != HighlightColor.NONE) {
                        IconButton(onClick = { onHighlightSelected(null) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.FormatColorReset, contentDescription = "Remover grifo", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Botão de Anotação
                TextButton(onClick = onOpenNoteDialog) {
                    Icon(
                        imageVector = Icons.Default.NoteAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (article.userNote.isNullOrBlank()) "Anotar" else "Editar Nota",
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun HighlightCircle(
    color: HighlightColor,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(color.composeColor)
            .border(
                width = if (isSelected) 2.dp else 0.5.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}
