package com.gazzel.sesameapp.presentation.screens.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gazzel.sesameapp.domain.model.PrivacySettings
import com.gazzel.sesameapp.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrivacySettingsViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PrivacySettingsUiState>(PrivacySettingsUiState.Loading)
    val uiState: StateFlow<PrivacySettingsUiState> = _uiState.asStateFlow()

    init {
        loadPrivacySettings()
    }

    private fun loadPrivacySettings() {
        viewModelScope.launch {
            try {
                val settings = userRepository.getPrivacySettings()
                _uiState.value = PrivacySettingsUiState.Success(settings)
            } catch (e: Exception) {
                _uiState.value = PrivacySettingsUiState.Error(e.message ?: "Failed to load privacy settings")
            }
        }
    }

    fun updateProfileVisibility(isPublic: Boolean) {
        viewModelScope.launch {
            try {
                userRepository.updateProfileVisibility(isPublic)
                loadPrivacySettings() // Reload to update UI
            } catch (e: Exception) {
                // Handle error silently as this is a background operation
            }
        }
    }

    fun updateListVisibility(arePublic: Boolean) {
        viewModelScope.launch {
            try {
                userRepository.updateListVisibility(arePublic)
                loadPrivacySettings() // Reload to update UI
            } catch (e: Exception) {
                // Handle error silently as this is a background operation
            }
        }
    }

    fun updateAnalytics(enabled: Boolean) {
        viewModelScope.launch {
            try {
                userRepository.updateAnalytics(enabled)
                loadPrivacySettings() // Reload to update UI
            } catch (e: Exception) {
                // Handle error silently as this is a background operation
            }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            try {
                userRepository.deleteAccount()
                _uiState.value = PrivacySettingsUiState.AccountDeleted
            } catch (e: Exception) {
                _uiState.value = PrivacySettingsUiState.Error(e.message ?: "Failed to delete account")
            }
        }
    }
}

sealed class PrivacySettingsUiState {
    object Loading : PrivacySettingsUiState()
    data class Success(val settings: PrivacySettings) : PrivacySettingsUiState()
    object AccountDeleted : PrivacySettingsUiState()
    data class Error(val message: String) : PrivacySettingsUiState()
} 