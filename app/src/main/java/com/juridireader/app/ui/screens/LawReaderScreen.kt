package com.juridireader.app.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.juridireader.app.data.local.entity.ArticleWithUserData
import com.juridireader.app.ui.model.AppThemeMode
import com.juridireader.app.ui.model.HighlightColor
import com.juridireader.app.ui.util.LegalTextFormatter
import com.juridireader.app.ui.util.SearchTextHighlighter
import com.juridireader.app.ui.viewmodel.LawViewModel
import com.juridireader.app.util.PdfExporter
import kotlinx.coroutines.launch
import java.io.File

private data class InTextMatch(
    val articleIndex: Int,
    val articleId: String,
    val range: IntRange
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LawReaderScreen(
    lawCode: String,
    targetArticleId: String? = null,
    initialSearchQuery: String? = null,
    viewModel: LawViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(lawCode) {
        viewModel.selectLaw(lawCode)
    }

    val articles by viewModel.currentLawArticles.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val fontSizeSp by viewModel.fontSizeSp.collectAsState()
    val justifyText by viewModel.justifyText.collectAsState()

    val listState = rememberLazyListState()
    var hasScrolledToTarget by remember(lawCode, targetArticleId) { mutableStateOf(false) }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var activeNoteArticle by remember { mutableStateOf<ArticleWithUserData?>(null) }
    var activeHighlightArticle by remember { mutableStateOf<ArticleWithUserData?>(null) }

    val startWithQuery = remember(initialSearchQuery) { initialSearchQuery?.trim().orEmpty() }
    var isSearchBarVisible by remember(lawCode, startWithQuery) {
        mutableStateOf(startWithQuery.isNotEmpty())
    }
    var inTextQuery by remember(lawCode, startWithQuery) {
        mutableStateOf(startWithQuery)
    }
    var activeMatchIndex by remember(lawCode, startWithQuery) { mutableStateOf(0) }
    var hasInitializedMatchFromTarget by remember(lawCode, targetArticleId, startWithQuery) {
        mutableStateOf(false)
    }
    var showOnlyRevoked by remember { mutableStateOf(false) }

    val displayedArticles = remember(articles, showOnlyRevoked) {
        if (showOnlyRevoked) {
            articles.filter { !it.revokedContent.isNullOrBlank() }
        } else {
            articles
        }
    }

    // Calcular todas as ocorrências na lei (estilo Ctrl+F de navegador) mantendo contexto de leitura
    val searchResultsByArticle = remember(displayedArticles, inTextQuery) {
        if (inTextQuery.isBlank()) {
            emptyMap()
        } else {
            buildMap {
                displayedArticles.forEach { article ->
                    val body = LegalTextFormatter.stripHeadingMarker(article.content)
                    val ranges = SearchTextHighlighter.findMatchRanges(body, inTextQuery)
                    if (ranges.isNotEmpty()) {
                        put(article.id, ranges)
                    }
                }
            }
        }
    }

    val allMatches = remember(displayedArticles, searchResultsByArticle) {
        if (searchResultsByArticle.isEmpty()) {
            emptyList()
        } else {
            buildList {
                displayedArticles.forEachIndexed { idx, article ->
                    val ranges = searchResultsByArticle[article.id].orEmpty()
                    ranges.forEach { r ->
                        add(InTextMatch(articleIndex = idx, articleId = article.id, range = r))
                    }
                }
            }
        }
    }

    // Reposicionar índice ativo quando query ou lista de matches mudar
    LaunchedEffect(allMatches, targetArticleId) {
        if (allMatches.isEmpty()) {
            activeMatchIndex = 0
        } else if (!hasInitializedMatchFromTarget && !targetArticleId.isNullOrBlank()) {
            val targetMatchIdx = allMatches.indexOfFirst { it.articleId == targetArticleId }
            activeMatchIndex = if (targetMatchIdx >= 0) targetMatchIdx else 0
            hasInitializedMatchFromTarget = true
            listState.animateScrollToItem(allMatches[activeMatchIndex].articleIndex)
        } else if (activeMatchIndex >= allMatches.size) {
            activeMatchIndex = 0
        }
    }

    // Rolar até o artigo alvo mesmo quando aberto sem busca textual (Favoritos / Notas)
    LaunchedEffect(displayedArticles, targetArticleId, startWithQuery) {
        if (!hasScrolledToTarget &&
            startWithQuery.isEmpty() &&
            !targetArticleId.isNullOrBlank() &&
            displayedArticles.isNotEmpty()
        ) {
            val targetIndex = displayedArticles.indexOfFirst { it.id == targetArticleId }
            if (targetIndex >= 0) {
                listState.animateScrollToItem(targetIndex)
                hasScrolledToTarget = true
            }
        }
    }

    fun navigateToMatch(newIndex: Int) {
        if (allMatches.isEmpty()) return
        val wrapped = ((newIndex % allMatches.size) + allMatches.size) % allMatches.size
        activeMatchIndex = wrapped
        val targetItemIdx = allMatches[wrapped].articleIndex
        scope.launch {
            listState.animateScrollToItem(targetItemIdx)
        }
    }

    val activeMatch = allMatches.getOrNull(activeMatchIndex)

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
                    // Abrir / fechar busca na página estilo navegador
                    IconButton(onClick = {
                        isSearchBarVisible = !isSearchBarVisible
                        if (!isSearchBarVisible) {
                            inTextQuery = ""
                        }
                    }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Buscar no texto",
                            tint = if (isSearchBarVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Alternar filtro de artigos com redações anteriores/revogadas
                    val hasAnyRevoked = remember(articles) { articles.any { !it.revokedContent.isNullOrBlank() } }
                    if (hasAnyRevoked) {
                        IconButton(onClick = { showOnlyRevoked = !showOnlyRevoked }) {
                            Icon(
                                Icons.Default.HistoryEdu,
                                contentDescription = if (showOnlyRevoked) "Mostrar todos artigos" else "Filtrar revogados",
                                tint = if (showOnlyRevoked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

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
            // Barra "Buscar no texto" estilo navegador com navegação < > e contador de ocorrências
            AnimatedVisibility(visible = isSearchBarVisible) {
                Surface(
                    tonalElevation = 2.dp,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = inTextQuery,
                            onValueChange = { newVal ->
                                inTextQuery = newVal
                                activeMatchIndex = 0
                                if (newVal.isNotBlank()) {
                                    // Calcular rápido primeiro artigo com ocorrência para rolar de imediato
                                    val firstIdx = displayedArticles.indexOfFirst { art ->
                                        val body = LegalTextFormatter.stripHeadingMarker(art.content)
                                        SearchTextHighlighter.findMatchRanges(body, newVal).isNotEmpty()
                                    }
                                    if (firstIdx >= 0) {
                                        scope.launch { listState.animateScrollToItem(firstIdx) }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Buscar no texto...", fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (inTextQuery.isNotEmpty()) {
                                        IconButton(onClick = {
                                            inTextQuery = ""
                                            activeMatchIndex = 0
                                        }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Limpar")
                                        }
                                    }
                                    IconButton(onClick = {
                                        isSearchBarVisible = false
                                        inTextQuery = ""
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Fechar busca")
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { navigateToMatch(activeMatchIndex + 1) }
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )

                        if (inTextQuery.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedIconButton(
                                    onClick = { navigateToMatch(activeMatchIndex - 1) },
                                    enabled = allMatches.isNotEmpty(),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ChevronLeft,
                                        contentDescription = "Ocorrência anterior"
                                    )
                                }

                                OutlinedIconButton(
                                    onClick = { navigateToMatch(activeMatchIndex + 1) },
                                    enabled = allMatches.isNotEmpty(),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = "Próxima ocorrência"
                                    )
                                }

                                val countLabel = when (allMatches.size) {
                                    0 -> "Nenhum resultado"
                                    1 -> "1 de 1 ocorrência"
                                    else -> "${activeMatchIndex + 1} de ${allMatches.size} ocorrências"
                                }
                                Text(
                                    text = countLabel,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (allMatches.isEmpty()) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Lista de Artigos com layout limpo de Vade Mecum
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 36.dp, top = 8.dp)
            ) {
                itemsIndexed(displayedArticles, key = { _, art -> art.id }) { _, article ->
                    val articleMatches = searchResultsByArticle[article.id].orEmpty()
                    val activeRangeForArticle = if (activeMatch?.articleId == article.id) {
                        activeMatch.range
                    } else {
                        null
                    }
                    val highlightBorder = (article.id == targetArticleId && activeMatch == null) ||
                        (activeRangeForArticle != null)

                    ArticleCard(
                        article = article,
                        isTargeted = highlightBorder,
                        fontSizeSp = fontSizeSp,
                        themeMode = themeMode,
                        justifyText = justifyText,
                        searchMatches = articleMatches,
                        activeSearchMatch = activeRangeForArticle,
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
        val cleanBody = remember(article.content) {
            LegalTextFormatter.stripHeadingMarker(article.content)
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
                            HighlightColor.entries
                                .filter { it != HighlightColor.NONE }
                                .forEach { hlColor ->
                                    HighlightCircle(hlColor, false) {
                                        viewModel.saveTextHighlight(
                                            article.id,
                                            textValue.selection.min,
                                            textValue.selection.max,
                                            selectedSnippet,
                                            hlColor.name
                                        )
                                        Toast.makeText(context, "Trecho grifado!", Toast.LENGTH_SHORT).show()
                                        activeHighlightArticle = null
                                    }
                                }
                        }

                        // Exportar trecho para PDF e salvar grifo
                        OutlinedButton(
                            onClick = {
                                viewModel.saveTextHighlight(
                                    article.id,
                                    textValue.selection.min,
                                    textValue.selection.max,
                                    selectedSnippet,
                                    "YELLOW"
                                )
                                val pdf = PdfExporter.exportSnippetToPdf(
                                    context,
                                    lawTitle,
                                    article.articleNumber,
                                    selectedSnippet
                                )
                                sharePdfFile(pdf)
                                Toast.makeText(context, "Trecho grifado e exportado!", Toast.LENGTH_SHORT).show()
                                activeHighlightArticle = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Grifar e Exportar Trecho para PDF", fontSize = 12.sp)
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

    // Diálogo de Configuração de Leitura (Fonte, Tema e Alinhamento)
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Texto Justificado", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "Alinha ambas as margens do texto",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(
                            checked = justifyText,
                            onCheckedChange = { viewModel.setJustifyText(it) }
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
    themeMode: AppThemeMode = AppThemeMode.LIGHT,
    justifyText: Boolean = false,
    searchMatches: List<IntRange> = emptyList(),
    activeSearchMatch: IntRange? = null,
    onToggleFavorite: () -> Unit,
    onOpenHighlightDialog: () -> Unit,
    onDeleteTextHighlight: (Long) -> Unit,
    onOpenNoteDialog: () -> Unit
) {
    val (headingTitle, cleanBody) = remember(article.content) {
        LegalTextFormatter.extractHeadingAndBody(article.content)
    }

    val isDark = themeMode == AppThemeMode.DARK
    val primaryColor = MaterialTheme.colorScheme.primary
    val headingColor = if (isDark) LegalTextFormatter.HeadingColorDark else LegalTextFormatter.HeadingColor
    val refColor = if (isDark) Color(0xFF94A3B8) else LegalTextFormatter.SubtleRefColor

    val formattedHeading = remember(headingTitle, headingColor) {
        headingTitle?.let { LegalTextFormatter.formatHeadingText(it, headingColor) }
    }

    val formattedText = remember(cleanBody, article.textHighlights, primaryColor, refColor, searchMatches, activeSearchMatch) {
        LegalTextFormatter.formatArticleText(
            content = cleanBody,
            textHighlights = article.textHighlights,
            primaryColor = primaryColor,
            refColor = refColor,
            searchMatches = searchMatches,
            activeSearchMatch = activeSearchMatch
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Cabeçalho Estrutural (LIVRO, TÍTULO, CAPÍTULO, SEÇÃO) centralizado estilo Vade Mecum
        if (formattedHeading != null) {
            Text(
                text = formattedHeading,
                fontSize = (fontSizeSp - 0.5f).coerceAtLeast(13f).sp,
                lineHeight = (fontSizeSp * 1.4f).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 12.dp, start = 8.dp, end = 8.dp)
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = if (isTargeted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                Color.Transparent
            },
            border = if (isTargeted) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
        ) {
            Column(modifier = Modifier.padding(horizontal = if (isTargeted) 12.dp else 2.dp, vertical = 8.dp)) {
                if (article.articleNumber == "Preâmbulo") {
                    Text(
                        text = article.lawTitle.uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    )
                }

                // Conteúdo Formatado do Artigo sem duplicar título "Art. X"
                SelectionContainer {
                    Text(
                        text = formattedText,
                        fontSize = fontSizeSp.sp,
                        lineHeight = (fontSizeSp * 1.55f).sp,
                        textAlign = if (justifyText) TextAlign.Justify else TextAlign.Start,
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
                                        color = Color.Black,
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
                                            tint = Color.Black,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bloco Expansível: Redação Anterior / Revogada (se houver)
                if (!article.revokedContent.isNullOrBlank()) {
                    var isRevokedExpanded by remember { mutableStateOf(false) }
                    val revTint = if (isDark) Color(0xFFFCA5A5) else Color(0xFF991B1B)
                    val revBg = if (isDark) Color(0xFF450A0A) else Color(0xFFFFF1F2)
                    val revBorder = if (isDark) Color(0xFF7F1D1D) else Color(0xFFFECDD3)

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { isRevokedExpanded = !isRevokedExpanded },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = revTint),
                        border = BorderStroke(1.dp, revBorder),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (isRevokedExpanded) Icons.Default.ExpandLess else Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = revTint
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRevokedExpanded) "Ocultar Redação Anterior (Revogada)" else "Ver Redação Anterior (Revogada)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = revTint
                        )
                    }

                    if (isRevokedExpanded) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = revBg,
                            border = BorderStroke(1.dp, revBorder)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Texto Anterior (Revogado / Alterado):",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = revTint
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                SelectionContainer {
                                    Text(
                                        text = article.revokedContent,
                                        fontSize = (fontSizeSp - 1f).sp,
                                        lineHeight = ((fontSizeSp - 1f) * 1.45f).sp,
                                        textAlign = if (justifyText) TextAlign.Justify else TextAlign.Start,
                                        style = TextStyle(
                                            textDecoration = TextDecoration.LineThrough,
                                            color = revTint
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Preview de Anotação Pessoal (se houver)
                if (!article.userNote.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
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

                Spacer(modifier = Modifier.height(4.dp))

                // Barra compacta de ações do artigo (Favoritar, Grifar Trecho, Anotar)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (article.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favoritar",
                            tint = if (article.isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    TextButton(
                        onClick = onOpenHighlightDialog,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BorderColor,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Grifar", fontSize = 12.sp)
                    }

                    TextButton(
                        onClick = onOpenNoteDialog,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NoteAlt,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
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

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 4.dp)
        )
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
