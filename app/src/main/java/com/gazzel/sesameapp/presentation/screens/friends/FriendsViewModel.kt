// presentation/screens/friends/FriendsViewModel.kt
package com.gazzel.sesameapp.presentation.screens.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gazzel.sesameapp.domain.model.Friend
// Assuming you have FriendRepository injected or FriendUseCases
import com.gazzel.sesameapp.domain.repository.FriendRepository // Or usecases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FriendsViewModel @Inject constructor(
    // Inject FriendRepository directly or FriendUseCases
    private val friendRepository: FriendRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<FriendsUiState>(FriendsUiState.Loading)
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    init {
        loadFriends()
    }

    private fun loadFriends() {
        viewModelScope.launch {
            _uiState.value = FriendsUiState.Loading
            friendRepository.getFriends()
                .catch { e ->
                    _uiState.value = FriendsUiState.Error(e.message ?: "Failed to load friends")
                }
                .collect { friends ->
                    _uiState.value = FriendsUiState.Success(friends)
                }
        }
    }

    fun searchFriends(query: String) {
        viewModelScope.launch {
            // Don't reset to Loading immediately if query is short, maybe just show current friends
            if (query.isBlank()) {
                loadFriends() // Reload all friends if query is cleared
                return@launch
            }
            _uiState.value = FriendsUiState.Loading // Show loading for active search
            friendRepository.searchFriends(query)
                .catch { e ->
                    _uiState.value = FriendsUiState.Error(e.message ?: "Failed to search friends")
                }
                .collect { friends ->
                    _uiState.value = FriendsUiState.Success(friends)
                }
        }
    }

    // --- Add Follow/Unfollow ---
    fun followUser(userId: String) {
        viewModelScope.launch {
            // TODO: Call repository/use case for followUser(userId)
            // Handle Result/Error
            // On success, update the specific friend's isFollowing status in the current state
            // Example (simplified state update):
            /*
            val result = friendRepository.followUser(userId)
            if (result.isSuccess) {
                 _uiState.update { currentState ->
                     if (currentState is FriendsUiState.Success) {
                          currentState.copy(friends = currentState.friends.map {
                              if (it.id == userId) it.copy(isFollowing = true) else it
                          })
                     } else currentState
                 }
            } else { // Handle error }
            */
            println("TODO: Implement followUser API call for $userId")
            // Temporarily update state for UI feedback
            _uiState.update { currentState ->
                if (currentState is FriendsUiState.Success) {
                    currentState.copy(friends = currentState.friends.map {
                        if (it.id == userId) it.copy(isFollowing = true) else it
                    })
                } else currentState
            }
        }
    }

    fun unfollowUser(userId: String) {
        viewModelScope.launch {
            // TODO: Call repository/use case for unfollowUser(userId)
            // Handle Result/Error
            // On success, update the specific friend's isFollowing status in the current state
            println("TODO: Implement unfollowUser API call for $userId")
            // Temporarily update state for UI feedback
            _uiState.update { currentState ->
                if (currentState is FriendsUiState.Success) {
                    currentState.copy(friends = currentState.friends.map {
                        if (it.id == userId) it.copy(isFollowing = false) else it
                    })
                } else currentState
            }
        }
    }
}

sealed class FriendsUiState {
    object Loading : FriendsUiState()
    data class Success(val friends: List<Friend>) : FriendsUiState()
    data class Error(val message: String) : FriendsUiState()
}