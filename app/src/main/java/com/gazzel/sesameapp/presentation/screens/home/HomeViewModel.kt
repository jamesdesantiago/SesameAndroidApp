package com.gazzel.sesameapp.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gazzel.sesameapp.domain.model.SesameList
import com.gazzel.sesameapp.domain.model.User
import com.gazzel.sesameapp.domain.repository.ListRepository
import com.gazzel.sesameapp.domain.repository.UserRepository
import com.gazzel.sesameapp.domain.util.onError // Ensure these are imported
import com.gazzel.sesameapp.domain.util.onSuccess // Ensure these are imported
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.gazzel.sesameapp.domain.util.Result // Ensure this is imported

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val listRepository: ListRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Still useful if search updates this independently from the main screen state
    private val _recentLists = MutableStateFlow<List<SesameList>>(emptyList())
    val recentLists: StateFlow<List<SesameList>> = _recentLists.asStateFlow()

    init {
        loadUserAndLists()
    }

    private fun loadUserAndLists() {
        _uiState.value = HomeUiState.Loading // Set loading state BEFORE launching coroutine
        viewModelScope.launch {
            try {
                // Safely fetch user, handle null case
                val user = userRepository.getCurrentUser().firstOrNull()
                if (user == null) {
                    _uiState.value = HomeUiState.Error("Failed to load user profile.")
                    return@launch // Stop processing if user is null
                }

                // Call repository which returns Result
                val listsResult: Result<List<SesameList>> = listRepository.getRecentLists(limit = 5)

                // *** HANDLE THE RESULT ***
                listsResult.onSuccess { listsData ->
                    // This block executes ONLY if listRepository call was successful
                    _recentLists.value = listsData // Update the list state with the actual List<SesameList>
                    _uiState.value = HomeUiState.Success(user, listsData) // Update UI state with user and actual List<SesameList>
                }.onError { exception ->
                    // This block executes ONLY if listRepository call failed
                    _uiState.value = HomeUiState.Error(exception.message ?: "Failed to load recent lists")
                }

            } catch (e: Exception) {
                // Catch other exceptions (e.g., during user fetching)
                _uiState.value = HomeUiState.Error(e.message ?: "Failed to load initial data")
            }
        }
    }

    fun searchLists(query: String) {
        viewModelScope.launch {
            try {
                // Call repository which returns Result
                val searchResult: Result<List<SesameList>> = if (query.isBlank()) {
                    listRepository.getRecentLists(limit = 5)
                } else {
                    listRepository.searchLists(query)
                }

                // *** HANDLE THE RESULT ***
                searchResult.onSuccess { listsData ->
                    // This block executes ONLY if the call was successful
                    _recentLists.value = listsData // Update list state with the actual List<SesameList>
                    // Decide if you need to update the main _uiState here as well
                }.onError { exception ->
                    // Handle search error (e.g., log it, maybe show a Snackbar via a different state/event)
                    println("Error searching lists: ${exception.message}")
                    // Avoid setting the main _uiState to Error on just a search failure usually
                    // _recentLists.value = emptyList() // Optionally clear list on search error
                }
            } catch (e: Exception) {
                // Handle other exceptions during search
                println("Exception during searchLists: ${e.message}")
            }
        }
    }

    fun refresh() {
        loadUserAndLists()
    }
}

// Sealed class remains the same
sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(val user: User, val recentLists: List<SesameList>) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}