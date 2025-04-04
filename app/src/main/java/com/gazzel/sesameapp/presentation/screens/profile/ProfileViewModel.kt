package com.gazzel.sesameapp.presentation.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gazzel.sesameapp.domain.model.User
import com.gazzel.sesameapp.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadUserProfile()
    }

    private fun loadUserProfile() {
        _uiState.value = ProfileUiState.Loading
        viewModelScope.launch {
            try {
                // Use the actual imported class name 'User'
                val user : User = userRepository.getCurrentUser().first() // <<< FIX: Use 'User'
                _uiState.value = ProfileUiState.Success(user)
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(e.message ?: "Failed to load profile")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                userRepository.signOut()
                _uiState.value = ProfileUiState.SignedOut
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(e.message ?: "Failed to sign out")
            }
        }
    }

    fun updateProfile(displayName: String?, profilePicture: String?) {
        viewModelScope.launch {
            try {
                _uiState.value = ProfileUiState.Loading
                userRepository.updateProfile(displayName, profilePicture)
                loadUserProfile()
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(e.message ?: "Failed to update profile")
            }
        }
    }
}

sealed class ProfileUiState {
    object Loading : ProfileUiState()
    data class Success(val user: User) : ProfileUiState()
    object SignedOut : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}