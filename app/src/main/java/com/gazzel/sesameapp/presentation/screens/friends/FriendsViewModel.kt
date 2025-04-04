package com.gazzel.sesameapp.presentation.screens.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gazzel.sesameapp.domain.model.Friend
import com.gazzel.sesameapp.domain.usecase.FriendUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val friendUseCases: FriendUseCases
) : ViewModel() {

    private val _uiState = MutableStateFlow<FriendsUiState>(FriendsUiState.Loading)
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    init {
        loadFriends()
    }

    private fun loadFriends() {
        viewModelScope.launch {
            try {
                friendUseCases.getFriends().collect { friends ->
                    _uiState.value = FriendsUiState.Success(friends)
                }
            } catch (e: Exception) {
                _uiState.value = FriendsUiState.Error(e.message ?: "Failed to load friends")
            }
        }
    }

    fun searchFriends(query: String) {
        viewModelScope.launch {
            try {
                _uiState.value = FriendsUiState.Loading
                friendUseCases.searchFriends(query).collect { friends ->
                    _uiState.value = FriendsUiState.Success(friends)
                }
            } catch (e: Exception) {
                _uiState.value = FriendsUiState.Error(e.message ?: "Failed to search friends")
            }
        }
    }
}

sealed class FriendsUiState {
    object Loading : FriendsUiState()
    data class Success(val friends: List<Friend>) : FriendsUiState()
    data class Error(val message: String) : FriendsUiState()
}