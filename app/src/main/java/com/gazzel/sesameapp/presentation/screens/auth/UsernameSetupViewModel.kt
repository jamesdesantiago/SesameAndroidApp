package com.gazzel.sesameapp.presentation.screens.auth

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

// Define the UI State for this specific screen
sealed class UsernameSetupState {
    object Idle : UsernameSetupState() // Initial state
    object Loading : UsernameSetupState()
    object Success : UsernameSetupState() // Username set successfully
    data class Error(val message: String) : UsernameSetupState()
}

@HiltViewModel
class UsernameSetupViewModel @Inject constructor(
    private val userUseCases: UserUseCases
) : ViewModel() {

    private val _uiState = MutableStateFlow<UsernameSetupState>(UsernameSetupState.Idle)
    val uiState: StateFlow<UsernameSetupState> = _uiState.asStateFlow()

    fun updateUsername(username: String) {
        if (username.isBlank() || username.length < 3) {
            _uiState.value = UsernameSetupState.Error("Username must be at least 3 characters long.")
            return
        }

        viewModelScope.launch {
            _uiState.value = UsernameSetupState.Loading
            try {
                // Call the use case/repository function
                val result : Result<Unit> = userUseCases.updateUsername(username) // Get the Result

                // --- Use 'when' to handle the result ---
                when (result) {
                    is Result.Success -> {
                        // No data to extract for Result<Unit>, just set success state
                        _uiState.value = UsernameSetupState.Success
                    }
                    is Result.Error -> {
                        // Access the exception directly from the result object
                        _uiState.value = UsernameSetupState.Error(result.exception.message ?: "Failed to update username")
                    }
                }
                // --- End 'when' block ---

            } catch (e: Exception) {
                // Catch unexpected errors during the coroutine/use case call itself
                _uiState.value = UsernameSetupState.Error(e.message ?: "An unexpected error occurred")
            }
        }
    }
}