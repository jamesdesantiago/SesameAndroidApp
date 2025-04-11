// app/src/main/java/com/gazzel/sesameapp/presentation/screens/friends/FriendsViewModel.kt
package com.gazzel.sesameapp.presentation.screens.friends

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map // Keep for potential future UI model mapping
import com.gazzel.sesameapp.domain.model.Friend
// Import Paginated Use Cases
import com.gazzel.sesameapp.domain.usecase.GetFollowingPaginatedUseCase
import com.gazzel.sesameapp.domain.usecase.GetFollowersPaginatedUseCase
import com.gazzel.sesameapp.domain.usecase.SearchFriendsPaginatedUseCase // <<< Import Paginated Search Use Case
// Import Action Use Cases
import com.gazzel.sesameapp.domain.usecase.FollowUserUseCase
import com.gazzel.sesameapp.domain.usecase.UnfollowUserUseCase
// Import Result Utils
import com.gazzel.sesameapp.domain.util.Result
import com.gazzel.sesameapp.domain.util.onError
import com.gazzel.sesameapp.domain.util.onSuccess
// Hilt and Coroutines
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// State for follow/unfollow actions and errors (Keep as is)
sealed class FriendActionState {
    object Idle : FriendActionState()
    data class Error(val message: String, val failedActionUserId: String? = null) : FriendActionState()
}

// Enum for tabs (Keep as is)
enum class FriendTab { FOLLOWING, FOLLOWERS }

@OptIn(ExperimentalCoroutinesApi::class) // For flatMapLatest
@HiltViewModel
class FriendsViewModel @Inject constructor(
    // Inject Paginated Use Cases
    private val getFollowingPaginatedUseCase: GetFollowingPaginatedUseCase,
    private val getFollowersPaginatedUseCase: GetFollowersPaginatedUseCase,
    private val searchFriendsPaginatedUseCase: SearchFriendsPaginatedUseCase, // <<< Inject Paginated Search
    // Inject Action Use Cases
    private val followUserUseCase: FollowUserUseCase,
    private val unfollowUserUseCase: UnfollowUserUseCase
) : ViewModel() {

    // --- State for Search Query ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // --- State for Selected Tab ---
    private val _selectedTab = MutableStateFlow(FriendTab.FOLLOWING)
    val selectedTab: StateFlow<FriendTab> = _selectedTab.asStateFlow()

    // --- Combined Flow for Display ---
    // Switches between following, followers, or paginated search based on query and tab
    val displayDataFlow: Flow<PagingData<Friend>> =
        combine(_searchQuery, _selectedTab) { query, tab -> query to tab }
            // Debounce searches slightly to avoid spamming API/DB during typing
            .debounce(300L) // Adjust debounce time as needed (e.g., 300-500ms)
            .flatMapLatest { (query, tab) ->
                if (query.isBlank()) {
                    // Show paginated data based on the selected tab
                    when (tab) {
                        FriendTab.FOLLOWING -> {
                            Log.d("FriendsViewModel", "Query blank, Tab Following: using getFollowingPaginatedUseCase")
                            getFollowingPaginatedUseCase()
                        }
                        FriendTab.FOLLOWERS -> {
                            Log.d("FriendsViewModel", "Query blank, Tab Followers: using getFollowersPaginatedUseCase")
                            getFollowersPaginatedUseCase()
                        }
                    }
                } else {
                    // --- Use the Paginated Search Use Case ---
                    Log.d("FriendsViewModel", "Query present ('$query'), using SearchFriendsPaginatedUseCase")
                    searchFriendsPaginatedUseCase(query) // <<< CALL Paginated Search Use Case
                }
            }
            .cachedIn(viewModelScope) // Cache the final PagingData flow

    // --- State for Follow/Unfollow Actions ---
    private val _actionState = MutableStateFlow<FriendActionState>(FriendActionState.Idle)
    val actionState: StateFlow<FriendActionState> = _actionState.asStateFlow()
    private val _pendingActions = MutableStateFlow<Set<String>>(emptySet())

    // --- Functions ---

    fun setSearchQuery(query: String) {
        // Update the query state. The combine/flatMapLatest logic will automatically
        // trigger the appropriate use case (following/followers or search).
        _searchQuery.value = query.trim()
    }

    fun selectTab(tab: FriendTab) {
        if (_selectedTab.value != tab) {
            Log.d("FriendsViewModel", "Switching tab to: $tab")
            _selectedTab.value = tab
            // Clear search when switching tabs to show the full list for the new tab
            if (_searchQuery.value.isNotBlank()) {
                _searchQuery.value = ""
                Log.d("FriendsViewModel", "Search query cleared due to tab switch.")
            }
        }
    }

    fun followUser(userId: String) {
        performFollowAction(userId) { followUserUseCase(userId) }
    }

    fun unfollowUser(userId: String) {
        performFollowAction(userId) { unfollowUserUseCase(userId) }
    }

    private fun performFollowAction(userId: String, action: suspend (String) -> Result<Unit>) {
        if (_pendingActions.value.contains(userId)) {
            Log.w("FriendsViewModel", "Action already pending for user $userId")
            return
        }
        _pendingActions.update { it + userId }
        _actionState.value = FriendActionState.Idle // Reset previous errors

        viewModelScope.launch {
            Log.i("FriendsViewModel", "Performing follow/unfollow action for user: $userId")
            val result = action(userId)

            result.onSuccess {
                Log.i("FriendsViewModel", "Action success for $userId. Manual UI refresh needed.")
                // TODO: Emit event to trigger lazyPagingItems.refresh() in UI for immediate feedback
            }.onError { exception ->
                Log.e("FriendsViewModel", "Action failed for $userId: ${exception.message}")
                _actionState.value = FriendActionState.Error(
                    message = exception.message ?: "Action failed",
                    failedActionUserId = userId
                )
            }
            _pendingActions.update { it - userId } // Ensure cleared on success or error
        }
    }

    fun clearError() {
        if (_actionState.value is FriendActionState.Error) {
            _actionState.value = FriendActionState.Idle
        }
    }
}