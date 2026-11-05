package com.juridireader.app.ui.screens

import android.content.Context
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.juridireader.app.data.local.entity.ArticleWithUserData
import com.juridireader.app.ui.model.AppThemeMode
import com.juridireader.app.ui.model.HighlightColor
import com.juridireader.app.ui.util.LegalTextFormatter
import com.juridireader.app.ui.viewmodel.LawViewModel
import com.juridireader.app.util.PdfExporter
import java.io.File

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
    var showExportDialog by remember { mutableStateOf(false) }
    var activeNoteArticle by remember { mutableStateOf<ArticleWithUserData?>(null) }
    var activeHighlightArticle by remember { mutableStateOf<ArticleWithUserData?>(null) }
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

    fun sharePdfFile(file: File?) {
        if (file != null && file.exists()) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    // Exportar PDF
                    IconButton(onClick = { showExportDialog = true }) {
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
                        onOpenHighlightDialog = { activeHighlightArticle = article },
                        onDeleteTextHighlight = { highlightId -> viewModel.deleteTextHighlight(highlightId) },
                        onOpenNoteDialog = { activeNoteArticle = article }
                    )
                }
            }
        }
    }

    // Diálogo de Exportação para PDF
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Exportar para PDF", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Selecione o que deseja exportar deste documento:",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Button(
                        onClick = {
                            showExportDialog = false
                            val pdf = PdfExporter.exportLawToPdf(context, lawTitle, articles)
                            sharePdfFile(pdf)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Exportar Lei Completa (${articles.size} artigos)")
                    }

                    OutlinedButton(
                        onClick = {
                            showExportDialog = false
                            val pdf = PdfExporter.exportCustomizedToPdf(context, lawTitle, articles)
                            if (pdf != null) {
                                sharePdfFile(pdf)
                            } else {
                                Toast.makeText(
                                    context,
                                    "Nenhum grifo ou anotação registrado nesta lei ainda.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.BorderColor, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Exportar Apenas Grifos & Anotações")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo de Grifar Trecho Selecionado
    activeHighlightArticle?.let { article ->
        val headingMatch = remember(article.content) {
            Regex("""^###\s*([^#]+?)\s*###\n*""").find(article.content)
        }
        val cleanBody = remember(article.content, headingMatch) {
            if (headingMatch != null) article.content.substring(headingMatch.range.last + 1).trimStart()
            else article.content
        }

        var textValue by remember {
            mutableStateOf(TextFieldValue(cleanBody, selection = TextRange(0, 0)))
        }

        val selectedSnippet = remember(textValue.selection) {
            if (!textValue.selection.collapsed) {
                val start = textValue.selection.min.coerceIn(0, cleanBody.length)
                val end = textValue.selection.max.coerceIn(start, cleanBody.length)
                cleanBody.substring(start, end).trim()
            } else ""
        }

        AlertDialog(
            onDismissRequest = { activeHighlightArticle = null },
            title = { Text("Grifar Trecho • ${article.articleNumber}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Selecione uma palavra ou trecho no texto abaixo:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { textValue = it },
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        textStyle = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
                    )

                    if (selectedSnippet.isNotBlank()) {
                        Text(
                            text = "Trecho selecionado:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "“$selectedSnippet”",
                                fontSize = 12.sp,
                                fontStyle = FontStyle.Italic,
                                maxLines = 3,
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        Text("Escolha a cor do grifo:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HighlightCircle(HighlightColor.YELLOW, false) {
                                viewModel.saveTextHighlight(
                                    article.id,
                                    textValue.selection.min,
                                    textValue.selection.max,
                                    selectedSnippet,
                                    "YELLOW"
                                )
                                activeHighlightArticle = null
                            }
                            HighlightCircle(HighlightColor.GREEN, false) {
                                viewModel.saveTextHighlight(
                                    article.id,
                                    textValue.selection.min,
                                    textValue.selection.max,
                                    selectedSnippet,
                                    "GREEN"
                                )
                                activeHighlightArticle = null
                            }
                            HighlightCircle(HighlightColor.BLUE, false) {
                                viewModel.saveTextHighlight(
                                    article.id,
                                    textValue.selection.min,
                                    textValue.selection.max,
                                    selectedSnippet,
                                    "BLUE"
                                )
                                activeHighlightArticle = null
                            }
                            HighlightCircle(HighlightColor.PINK, false) {
                                viewModel.saveTextHighlight(
                                    article.id,
                                    textValue.selection.min,
                                    textValue.selection.max,
                                    selectedSnippet,
                                    "PINK"
                                )
                                activeHighlightArticle = null
                            }
                        }

                        // Exportar trecho para PDF
                        OutlinedButton(
                            onClick = {
                                val pdf = PdfExporter.exportSnippetToPdf(
                                    context,
                                    lawTitle,
                                    article.articleNumber,
                                    selectedSnippet
                                )
                                sharePdfFile(pdf)
                                activeHighlightArticle = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Exportar Trecho para PDF", fontSize = 12.sp)
                        }
                    } else {
                        Text(
                            "Dica: Toque duas vezes ou mantenha pressionado para selecionar o texto.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeHighlightArticle = null }) {
                    Text("Fechar")
                }
            }
        )
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
    onOpenHighlightDialog: () -> Unit,
    onDeleteTextHighlight: (Long) -> Unit,
    onOpenNoteDialog: () -> Unit
) {
    // Extrair cabeçalho estrutural se houver prefixo "### TÍTULO ... ###"
    val headingMatch = remember(article.content) {
        Regex("""^###\s*([^#]+?)\s*###\n*""").find(article.content)
    }
    val headingTitle = headingMatch?.groups?.get(1)?.value
    val cleanBody = remember(article.content, headingMatch) {
        if (headingMatch != null) article.content.substring(headingMatch.range.last + 1).trimStart()
        else article.content
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val formattedText = remember(cleanBody, article.textHighlights, primaryColor) {
        LegalTextFormatter.formatArticleText(
            content = cleanBody,
            textHighlights = article.textHighlights,
            primaryColor = primaryColor
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Se houver Cabeçalho Estrutural (TÍTULO, CAPÍTULO, SEÇÃO), exibir no topo como em livro jurídico
        if (!headingTitle.isNullOrBlank()) {
            Text(
                text = headingTitle,
                color = LegalTextFormatter.HeadingColor,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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

                // Conteúdo Formatado do Artigo com Justificação
                SelectionContainer {
                    Text(
                        text = formattedText,
                        fontSize = fontSizeSp.sp,
                        lineHeight = (fontSizeSp * 1.55f).sp,
                        textAlign = TextAlign.Justify,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Lista de Trechos Grifados neste Artigo
                if (article.textHighlights.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        article.textHighlights.forEach { th ->
                            val hlColor = HighlightColor.fromName(th.color)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = hlColor.composeColor.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "“${th.selectedText}”",
                                        fontSize = 12.sp,
                                        fontStyle = FontStyle.Italic,
                                        maxLines = 2,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { onDeleteTextHighlight(th.id) },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remover trecho",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Preview de Anotação Pessoal (se houver)
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

                // Barra de Ações: Grifar Trecho Selecionado + Anotar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botão Grifar Trecho
                    OutlinedButton(
                        onClick = onOpenHighlightDialog,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BorderColor,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Grifar Trecho", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Botão Anotar
                    TextButton(
                        onClick = onOpenNoteDialog,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NoteAlt,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
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
}

@Composable
fun HighlightCircle(
    color: HighlightColor,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
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
