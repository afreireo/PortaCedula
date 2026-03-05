package com.example.portacedula

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CardPart { FRONT, BACK }

data class HomeUiState(
    val cards: List<IdCard> = emptyList(),
    val favoriteCard: IdCard? = null,
    val showZoom: String? = null,
    val isDarkMode: Boolean = false,
    
    // Estados para edición/selección en Home
    val selectedPart: CardPart? = null,
    
    // Estados para selección en pestaña Tarjetas
    val selectedCards: Set<String> = emptySet(),
    
    // Estado para "Agregar Nueva Tarjeta" (la pantalla/modo blanco)
    val isAddingNewCard: Boolean = false,
    val newCardDraft: IdCard? = null
)

class HomeViewModel(private val repo: IdCardRepository) : ViewModel() {
    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            repo.cardsFlow.collect { cardsList ->
                _ui.update { it.copy(
                    cards = cardsList,
                    favoriteCard = (cardsList.find { card -> card.isFavorite } ?: cardsList.firstOrNull())
                ) }
            }
        }
    }

    // --- Gestión de Imágenes ---
    fun onFrontCaptured(cardId: String, uri: String) = viewModelScope.launch {
        if (_ui.value.isAddingNewCard) {
            _ui.update { it.copy(newCardDraft = it.newCardDraft?.copy(frontUri = uri)) }
        } else {
            val updatedCards = _ui.value.cards.map {
                if (it.id == cardId) it.copy(frontUri = uri) else it
            }
            repo.saveCards(updatedCards)
        }
    }

    fun onBackCaptured(cardId: String, uri: String) = viewModelScope.launch {
        if (_ui.value.isAddingNewCard) {
            _ui.update { it.copy(newCardDraft = it.newCardDraft?.copy(backUri = uri)) }
        } else {
            val updatedCards = _ui.value.cards.map {
                if (it.id == cardId) it.copy(backUri = uri) else it
            }
            repo.saveCards(updatedCards)
        }
    }

    fun deletePart(cardId: String, part: CardPart) = viewModelScope.launch {
        val updatedCards = _ui.value.cards.map {
            if (it.id == cardId) {
                if (part == CardPart.FRONT) it.copy(frontUri = null) else it.copy(backUri = null)
            } else it
        }
        repo.saveCards(updatedCards)
        clearPartSelection()
    }

    // --- Gestión de Tarjetas ---
    fun setFavorite(cardId: String) = viewModelScope.launch {
        val updatedCards = _ui.value.cards.map {
            it.copy(isFavorite = it.id == cardId)
        }
        repo.saveCards(updatedCards)
    }

    fun startAddingCard() {
        _ui.update { it.copy(isAddingNewCard = true, newCardDraft = IdCard(name = "Nueva Tarjeta")) }
    }

    fun cancelAddingCard() {
        _ui.update { it.copy(isAddingNewCard = false, newCardDraft = null) }
    }

    fun finishAddingCard() = viewModelScope.launch {
        _ui.value.newCardDraft?.let { draft ->
            if (draft.frontUri != null && draft.backUri != null) {
                repo.addCard(draft)
                _ui.update { it.copy(isAddingNewCard = false, newCardDraft = null) }
            }
        }
    }

    fun updateDraftName(name: String) {
        _ui.update { it.copy(newCardDraft = it.newCardDraft?.copy(name = name)) }
    }

    fun deleteSelectedCards() = viewModelScope.launch {
        val updatedCards = _ui.value.cards.filter { it.id !in _ui.value.selectedCards }
        repo.saveCards(updatedCards)
        _ui.update { it.copy(selectedCards = emptySet()) }
    }

    // --- UI State Toggles ---
    fun selectPart(part: CardPart) = _ui.update { it.copy(selectedPart = part) }
    fun clearPartSelection() = _ui.update { it.copy(selectedPart = null) }
    
    fun toggleCardSelection(cardId: String) {
        _ui.update { state ->
            val newSelection = if (cardId in state.selectedCards) {
                state.selectedCards - cardId
            } else {
                state.selectedCards + cardId
            }
            state.copy(selectedCards = newSelection)
        }
    }
    fun clearCardSelection() = _ui.update { it.copy(selectedCards = emptySet()) }

    fun onZoom(uri: String?) = _ui.update { it.copy(showZoom = uri) }
    fun toggleDarkMode(enabled: Boolean) = _ui.update { it.copy(isDarkMode = enabled) }
}
