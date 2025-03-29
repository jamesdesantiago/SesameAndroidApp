package com.gazzel.sesameapp.presentation.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gazzel.sesameapp.domain.model.User
import com.gazzel.sesameapp.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        viewModelScope.launch {
            try {
                val user = userRepository.getCurrentUser()
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

data class User(
    val id: String,
    val username: String,
    val displayName: String?,
    val profilePicture: String?,
    val email: String,
    val listCount: Int,
    val followerCount: Int,
    val followingCount: Int
) 