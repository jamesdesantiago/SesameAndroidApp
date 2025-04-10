package com.gazzel.sesameapp.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gazzel.sesameapp.domain.usecase.UserUseCases
import com.gazzel.sesameapp.domain.util.Result
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
            try { // Add try-catch for flow collection
                userUseCases.checkUsername()
                    .collect { needsUsername ->
                        _uiState.value = if (needsUsername) {
                            MainUiState.NeedsUsername
                        } else {
                            MainUiState.Ready
                        }
                    }
            } catch (e: Exception) {
                // Handle error fetching username status
                _uiState.value = MainUiState.Error(e.message ?: "Failed to check username status")
            }
        }
    }

    fun updateUsername(username: String) {
        // Optional: Add basic validation like in UsernameSetupViewModel
        // if (username.isBlank() || username.length < 3) { ... return }

        viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            try { // Add try-catch around the use case call
                val result: Result<Unit> = userUseCases.updateUsername(username) // Get the Result

                // --- Use 'when' ---
                when (result) {
                    is Result.Success -> {
                        _uiState.value = MainUiState.Ready // Username set, app is ready
                    }
                    is Result.Error -> {
                        _uiState.value = MainUiState.Error(result.exception.message ?: "Failed to update username")
                    }
                }
                // --- End 'when' ---

            } catch (e: Exception) {
                // Catch errors from the launch block/use case call itself
                _uiState.value = MainUiState.Error(e.message ?: "An unexpected error occurred")
            }
        }
    }
}

// --- MainUiState definition remains the same ---
sealed class MainUiState {
    object Initial : MainUiState()
    object Loading : MainUiState()
    object NeedsUsername : MainUiState()
    object Ready : MainUiState()
    data class Error(val message: String) : MainUiState()
}