package com.juridireader.app.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.juridireader.app.JuridiReaderApp
import com.juridireader.app.data.local.entity.ArticleWithUserData
import com.juridireader.app.data.local.entity.LawMetadataEntity
import com.juridireader.app.ui.model.AppThemeMode
import com.juridireader.app.util.BackupCodeCodec
import com.juridireader.app.util.ImportMode
import com.juridireader.app.util.ImportSummary
import com.juridireader.app.util.UserBackupPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class LawViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as JuridiReaderApp).repository
    private val prefs = application.getSharedPreferences("juridireader_prefs", Context.MODE_PRIVATE)

    // Configurações visuais do leitor (persistidas localmente)
    private val _themeMode = MutableStateFlow(
        runCatching {
            AppThemeMode.valueOf(prefs.getString("theme_mode", AppThemeMode.LIGHT.name) ?: AppThemeMode.LIGHT.name)
        }.getOrDefault(AppThemeMode.LIGHT)
    )
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _fontSizeSp = MutableStateFlow(
        prefs.getFloat("font_size_sp", 16f).coerceIn(12f, 28f)
    )
    val fontSizeSp: StateFlow<Float> = _fontSizeSp.asStateFlow()

    private val _justifyText = MutableStateFlow(
        prefs.getBoolean("justify_text", false)
    )
    val justifyText: StateFlow<Boolean> = _justifyText.asStateFlow()

    // Leis e Categorias
    val allLaws: StateFlow<List<LawMetadataEntity>> = repository.getAllLaws()
        .catch { e ->
            Log.e("LawViewModel", "Erro ao carregar lista de leis", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<String>> = repository.getAllCategories()
        .catch { e ->
            Log.e("LawViewModel", "Erro ao carregar categorias", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lawCodesWithRevoked: StateFlow<Set<String>> = repository.getLawCodesWithRevokedContent()
        .map { it.toSet() }
        .catch { e ->
            Log.e("LawViewModel", "Erro ao carregar leis com revogações", e)
            emit(emptySet())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // Lei atualmente aberta no leitor
    private val _selectedLawCode = MutableStateFlow<String?>(null)
    val selectedLawCode: StateFlow<String?> = _selectedLawCode.asStateFlow()

    val currentLawArticles: StateFlow<List<ArticleWithUserData>> = _selectedLawCode
        .flatMapLatest { code ->
            if (code != null) repository.getArticlesForLaw(code) else flowOf(emptyList())
        }
        .catch { e ->
            Log.e("LawViewModel", "Erro ao carregar artigos da lei", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Busca FTS5
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedSearchCategory = MutableStateFlow("Todas")
    val selectedSearchCategory: StateFlow<String> = _selectedSearchCategory.asStateFlow()

    val searchResults: StateFlow<List<ArticleWithUserData>> = combine(
        _searchQuery.debounce(250),
        _selectedSearchCategory
    ) { query, cat -> Pair(query, cat) }
        .flatMapLatest { (query, cat) ->
            if (query.isBlank()) {
                flowOf(emptyList())
            } else {
                repository.searchArticles(query, cat)
            }
        }
        .catch { e ->
            Log.e("LawViewModel", "Erro na busca FTS5", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Favoritos e Personalizados
    val favoriteArticles: StateFlow<List<ArticleWithUserData>> = repository.getFavorites()
        .catch { e ->
            Log.e("LawViewModel", "Erro ao carregar favoritos", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customizedArticles: StateFlow<List<ArticleWithUserData>> = repository.getCustomizedArticles()
        .catch { e ->
            Log.e("LawViewModel", "Erro ao carregar customizações", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectLaw(code: String) {
        _selectedLawCode.value = code
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSearchCategory(cat: String) {
        _selectedSearchCategory.value = cat
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun setFontSize(sizeSp: Float) {
        val clamped = sizeSp.coerceIn(12f, 28f)
        _fontSizeSp.value = clamped
        prefs.edit().putFloat("font_size_sp", clamped).apply()
    }

    fun setJustifyText(justify: Boolean) {
        _justifyText.value = justify
        prefs.edit().putBoolean("justify_text", justify).apply()
    }

    fun toggleFavorite(articleId: String, currentStatus: Boolean) {
        viewModelScope.launch {
            try {
                repository.toggleFavorite(articleId, currentStatus)
            } catch (e: Exception) {
                Log.e("LawViewModel", "Erro ao alternar favorito", e)
            }
        }
    }

    fun saveNote(articleId: String, noteText: String) {
        viewModelScope.launch {
            try {
                repository.saveNote(articleId, noteText)
            } catch (e: Exception) {
                Log.e("LawViewModel", "Erro ao salvar anotação", e)
            }
        }
    }

    fun setHighlight(articleId: String, color: String?) {
        viewModelScope.launch {
            try {
                repository.setHighlight(articleId, color)
            } catch (e: Exception) {
                Log.e("LawViewModel", "Erro ao definir grifo", e)
            }
        }
    }

    fun saveTextHighlight(
        articleId: String,
        startOffset: Int,
        endOffset: Int,
        selectedText: String,
        color: String
    ) {
        viewModelScope.launch {
            try {
                repository.saveTextHighlight(articleId, startOffset, endOffset, selectedText, color)
            } catch (e: Exception) {
                Log.e("LawViewModel", "Erro ao salvar grifo de texto", e)
            }
        }
    }

    fun deleteTextHighlight(id: Long) {
        viewModelScope.launch {
            try {
                repository.deleteTextHighlight(id)
            } catch (e: Exception) {
                Log.e("LawViewModel", "Erro ao excluir grifo de texto", e)
            }
        }
    }

    fun clearTextHighlightsForArticle(articleId: String) {
        viewModelScope.launch {
            try {
                repository.clearTextHighlightsForArticle(articleId)
            } catch (e: Exception) {
                Log.e("LawViewModel", "Erro ao limpar grifos do artigo", e)
            }
        }
    }

    // Exportação e Importação Offline (Códigos Longos)
    fun generateBackupCode(onResult: (Result<Pair<String, UserBackupPayload>>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val payload = repository.exportBackupPayload()
                    val code = BackupCodeCodec.encode(payload)
                    Pair(code, payload)
                }
            }
            onResult(result)
        }
    }

    fun previewBackupCode(rawCode: String): Result<UserBackupPayload> {
        return BackupCodeCodec.decode(rawCode)
    }

    fun importBackupCode(
        rawCode: String,
        mode: ImportMode,
        onResult: (Result<ImportSummary>) -> Unit
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                BackupCodeCodec.decode(rawCode).mapCatching { payload ->
                    repository.importBackupPayload(payload, mode)
                }
            }
            onResult(result)
        }
    }
}
