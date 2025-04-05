package com.gazzel.sesameapp.presentation.screens.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gazzel.sesameapp.domain.model.SesameList
import com.gazzel.sesameapp.domain.repository.ListRepository
import com.gazzel.sesameapp.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.gazzel.sesameapp.domain.util.Result // Import Result
import com.gazzel.sesameapp.domain.util.onSuccess // Import extensions
import com.gazzel.sesameapp.domain.util.onError // Import extensions

@HiltViewModel
class ListsViewModel @Inject constructor(
    private val listRepository: ListRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ListsUiState>(ListsUiState.Loading)
    val uiState: StateFlow<ListsUiState> = _uiState.asStateFlow()

    init {
        loadLists()
    }

    private fun loadLists() {
        _uiState.value = ListsUiState.Loading
        viewModelScope.launch {
            try {
                // Safely get the current user
                val user = userRepository.getCurrentUser().firstOrNull()
                if (user == null) {
                    _uiState.value = ListsUiState.Error("Could not load user information.")
                    return@launch
                }

                // Call the suspend function which returns Result
                val userListsResult: Result<List<SesameList>> = listRepository.getUserLists(user.id)

                // Handle the Result
                userListsResult.onSuccess { lists ->
                    // Success case: update state with the actual list data
                    _uiState.value = ListsUiState.Success(lists)
                }.onError { exception ->
                    // Error case: update state with the error message
                    _uiState.value = ListsUiState.Error(exception.message ?: "Failed to load lists")
                }

            } catch (e: Exception) {
                // Catch other exceptions (e.g., during user fetching)
                _uiState.value = ListsUiState.Error(e.message ?: "An unexpected error occurred")
            }
        }
    }

    fun deleteList(listId: String) {
        viewModelScope.launch {
            try {
                // Call the suspend fun returning Result
                val deleteResult = listRepository.deleteList(listId)
                deleteResult.onSuccess {
                    // Reload lists after successful deletion
                    loadLists()
                }.onError { exception ->
                    // Optionally update UI state with a temporary deletion error message
                    // For now, just print log or ignore for simplicity
                    println("Failed to delete list $listId: ${exception.message}")
                    // You might want to emit a temporary error state or event here
                }
            } catch (e: Exception) {
                // Handle unexpected deletion errors
                println("Exception deleting list $listId: ${e.message}")
                // Maybe emit an error state
            }
        }
    }

    fun refresh() {
        loadLists()
    }
}