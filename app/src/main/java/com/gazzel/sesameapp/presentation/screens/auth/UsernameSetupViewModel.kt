package com.gazzel.sesameapp.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gazzel.sesameapp.domain.usecase.UserUseCases // Import your use cases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Define the UI State for this specific screen
sealed class UsernameSetupState {
    object Idle : UsernameSetupState() // Initial state
    object Loading : UsernameSetupState()
    object Success : UsernameSetupState() // Username set successfully
    data class Error(val message: String) : UsernameSetupState()
}

@HiltViewModel
class UsernameSetupViewModel @Inject constructor(
    private val userUseCases: UserUseCases // Inject the user use cases
) : ViewModel() {

    // Use the specific state for this screen
    private val _uiState = MutableStateFlow<UsernameSetupState>(UsernameSetupState.Idle)
    val uiState: StateFlow<UsernameSetupState> = _uiState.asStateFlow()

    fun updateUsername(username: String) {
        // Basic validation (you might add more complex checks)
        if (username.isBlank() || username.length < 3) {
            _uiState.value = UsernameSetupState.Error("Username must be at least 3 characters long.")
            return
        }

        viewModelScope.launch {
            _uiState.value = UsernameSetupState.Loading
            try {
                // Call the use case/repository function
                val result = userUseCases.updateUsername(username)

                result.onSuccess {
                    _uiState.value = UsernameSetupState.Success // Navigate on success
                }.onError { exception ->
                    // Handle specific errors from repository if needed
                    _uiState.value = UsernameSetupState.Error(exception.message ?: "Failed to update username")
                }

            } catch (e: Exception) {
                // Catch unexpected errors
                _uiState.value = UsernameSetupState.Error(e.message ?: "An unexpected error occurred")
            }
        }
    }
}