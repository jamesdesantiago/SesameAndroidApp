// presentation/screens/friends/FriendsViewModel.kt
package com.gazzel.sesameapp.presentation.screens.friends

import android.util.Log // Add Log import
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gazzel.sesameapp.domain.model.Friend
// Import Use Cases
import com.gazzel.sesameapp.domain.usecase.GetFriendsUseCase // <<< ADD
import com.gazzel.sesameapp.domain.usecase.SearchFriendsUseCase // <<< ADD
import com.gazzel.sesameapp.domain.usecase.FollowUserUseCase // <<< ADD
import com.gazzel.sesameapp.domain.usecase.UnfollowUserUseCase // <<< ADD
// Import Result and extensions
import com.gazzel.sesameapp.domain.util.Result
import com.gazzel.sesameapp.domain.util.onError
import com.gazzel.sesameapp.domain.util.onSuccess
// Other imports
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FriendsViewModel @Inject constructor(
    // Inject Use Cases instead of Repository
    private val getFriendsUseCase: GetFriendsUseCase,         // <<< CHANGE
    private val searchFriendsUseCase: SearchFriendsUseCase,   // <<< CHANGE
    private val followUserUseCase: FollowUserUseCase,       // <<< CHANGE
    private val unfollowUserUseCase: UnfollowUserUseCase     // <<< CHANGE
) : ViewModel() {

    private val _uiState = MutableStateFlow<FriendsUiState>(FriendsUiState.Loading)
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    // Keep track of the search job to cancel previous searches
    private var searchJob: Job? = null
    // Keep track of ongoing follow/unfollow operations to prevent rapid clicks
    private val _pendingActions = MutableStateFlow<Set<String>>(emptySet()) // Set of user IDs being acted upon

    init {
        loadFriends()
    }

    private fun loadFriends() {
        // Don't reload if already loading to avoid redundant calls
        if (_uiState.value is FriendsUiState.Loading) return

        _uiState.value = FriendsUiState.Loading
        viewModelScope.launch {
            Log.d("FriendsViewModel", "Loading initial friends list...")
            getFriendsUseCase() // Use Case returns Flow
                .catch { e ->
                    Log.e("FriendsViewModel", "Error loading friends", e)
                    _uiState.value = FriendsUiState.Error(e.message ?: "Failed to load friends")
                }
                .collect { friends ->
                    Log.d("FriendsViewModel", "Received ${friends.size} friends.")
                    _uiState.value = FriendsUiState.Success(friends)
                }
        }
    }

    fun searchFriends(query: String) {
        searchJob?.cancel() // Cancel previous search if any

        if (query.isBlank()) {
            // If query is cleared, reload the full friend list instead of showing empty search results
            if (_uiState.value !is FriendsUiState.Loading) { // Avoid triggering reload if already loading friends
                loadFriends()
            }
            return
        }

        // Only set loading state for actual search, not for blank query
        _uiState.value = FriendsUiState.Loading(isSearch = true) // Indicate search loading

        searchJob = viewModelScope.launch {
            Log.d("FriendsViewModel", "Searching friends with query: $query")
            searchFriendsUseCase(query) // Use Case returns Flow
                .catch { e ->
                    Log.e("FriendsViewModel", "Error searching friends", e)
                    _uiState.value = FriendsUiState.Error(e.message ?: "Failed to search friends")
                }
                .collect { searchResults ->
                    Log.d("FriendsViewModel", "Search returned ${searchResults.size} results.")
                    // Update state with search results
                    _uiState.value = FriendsUiState.Success(searchResults)
                }
        }
    }

    fun followUser(userId: String) {
        // Prevent action if already pending for this user
        if (_pendingActions.value.contains(userId)) return
        _pendingActions.update { it + userId } // Mark as pending

        viewModelScope.launch {
            Log.d("FriendsViewModel", "Attempting to follow user: $userId")
            val result = followUserUseCase(userId) // Call Use Case

            result.onSuccess {
                Log.d("FriendsViewModel", "Successfully followed $userId. Updating UI state.")
                // Optimistically update the UI state
                _uiState.update { currentState ->
                    if (currentState is FriendsUiState.Success) {
                        currentState.copy(friends = currentState.friends.map {
                            if (it.id == userId) it.copy(isFollowing = true) else it
                        })
                    } else currentState // Should ideally not happen if action was possible
                }
            }.onError { exception ->
                Log.e("FriendsViewModel", "Failed to follow $userId: ${exception.message}")
                // TODO: Show temporary error message to user (e.g., via Snackbar/Event)
            }
            _pendingActions.update { it - userId } // Unmark as pending
        }
    }

    fun unfollowUser(userId: String) {
        // Prevent action if already pending for this user
        if (_pendingActions.value.contains(userId)) return
        _pendingActions.update { it + userId } // Mark as pending

        viewModelScope.launch {
            Log.d("FriendsViewModel", "Attempting to unfollow user: $userId")
            val result = unfollowUserUseCase(userId) // Call Use Case

            result.onSuccess {
                Log.d("FriendsViewModel", "Successfully unfollowed $userId. Updating UI state.")
                // Optimistically update the UI state
                _uiState.update { currentState ->
                    if (currentState is FriendsUiState.Success) {
                        currentState.copy(friends = currentState.friends.map {
                            if (it.id == userId) it.copy(isFollowing = false) else it
                        })
                    } else currentState
                }
            }.onError { exception ->
                Log.e("FriendsViewModel", "Failed to unfollow $userId: ${exception.message}")
                // TODO: Show temporary error message to user (e.g., via Snackbar/Event)
            }
            _pendingActions.update { it - userId } // Unmark as pending
        }
    }
}

// Update UiState to differentiate search loading if needed
sealed class FriendsUiState {
    data class Loading(val isSearch: Boolean = false) : FriendsUiState() // Differentiate initial vs search load
    data class Success(val friends: List<Friend>) : FriendsUiState()
    data class Error(val message: String) : FriendsUiState()
}