package com.juridireader.app.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.juridireader.app.JuridiReaderApp
import com.juridireader.app.data.local.entity.ArticleWithUserData
import com.juridireader.app.data.local.entity.LawMetadataEntity
import com.juridireader.app.ui.model.AppThemeMode
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class LawViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as JuridiReaderApp).repository

    // Configurações visuais do leitor
    private val _themeMode = MutableStateFlow(AppThemeMode.LIGHT)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _fontSizeSp = MutableStateFlow(16f)
    val fontSizeSp: StateFlow<Float> = _fontSizeSp.asStateFlow()

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
    }

    fun setFontSize(sizeSp: Float) {
        _fontSizeSp.value = sizeSp.coerceIn(12f, 28f)
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
}
