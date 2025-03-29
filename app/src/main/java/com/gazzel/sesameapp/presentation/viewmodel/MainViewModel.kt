package com.gazzel.sesameapp.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gazzel.sesameapp.domain.usecase.UserUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userUseCases: UserUseCases
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Initial)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        checkUsernameStatus()
    }

    private fun checkUsernameStatus() {
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            userUseCases.checkUsername()
                .collect { needsUsername ->
                    _uiState.value = if (needsUsername) {
                        MainUiState.NeedsUsername
                    } else {
                        MainUiState.Ready
                    }
                }
        }
    }

    fun updateUsername(username: String) {
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            userUseCases.updateUsername(username)
                .onSuccess {
                    _uiState.value = MainUiState.Ready
                }
                .onFailure { error ->
                    _uiState.value = MainUiState.Error(error.message ?: "Unknown error")
                }
        }
    }
}

sealed class MainUiState {
    object Initial : MainUiState()
    object Loading : MainUiState()
    object NeedsUsername : MainUiState()
    object Ready : MainUiState()
    data class Error(val message: String) : MainUiState()
} 