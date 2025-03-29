package com.gazzel.sesameapp.presentation.screens.help

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gazzel.sesameapp.domain.model.FAQ
import com.gazzel.sesameapp.domain.repository.HelpRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HelpViewModel @Inject constructor(
    private val helpRepository: HelpRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HelpUiState>(HelpUiState.Loading)
    val uiState: StateFlow<HelpUiState> = _uiState.asStateFlow()

    init {
        loadFAQs()
    }

    private fun loadFAQs() {
        viewModelScope.launch {
            try {
                val faqs = helpRepository.getFAQs()
                _uiState.value = HelpUiState.Success(faqs)
            } catch (e: Exception) {
                _uiState.value = HelpUiState.Error(e.message ?: "Failed to load FAQs")
            }
        }
    }

    fun sendSupportEmail(subject: String, message: String) {
        viewModelScope.launch {
            try {
                helpRepository.sendSupportEmail(subject, message)
            } catch (e: Exception) {
                // Handle error silently as this is a background operation
            }
        }
    }

    fun sendFeedback(feedback: String) {
        viewModelScope.launch {
            try {
                helpRepository.sendFeedback(feedback)
            } catch (e: Exception) {
                // Handle error silently as this is a background operation
            }
        }
    }
}

sealed class HelpUiState {
    object Loading : HelpUiState()
    data class Success(val faqs: List<FAQ>) : HelpUiState()
    data class Error(val message: String) : HelpUiState()
} 