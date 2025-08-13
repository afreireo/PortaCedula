package com.example.portacedula

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val frontUri: String? = null,
    val backUri: String? = null,
    val showZoom: String? = null,
    val showAddSheet: Boolean = false
)

class HomeViewModel(private val repo: IdCardRepository) : ViewModel() {
    val ui = MutableStateFlow(HomeUiState())

    init {
        viewModelScope.launch {
            repo.cardFlow.collect { card ->
                ui.update { it.copy(frontUri = card.frontUri, backUri = card.backUri) }
            }
        }
    }

    fun onFrontCaptured(uri: String) = viewModelScope.launch { repo.setFront(uri) }
    fun onBackCaptured(uri: String)  = viewModelScope.launch { repo.setBack(uri)  }

    fun onZoom(uri: String?) = ui.update { it.copy(showZoom = uri) }
    fun toggleAddSheet(show: Boolean) = ui.update { it.copy(showAddSheet = show) }
}
