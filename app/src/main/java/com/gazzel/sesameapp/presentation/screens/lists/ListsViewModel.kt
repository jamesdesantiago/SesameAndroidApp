// presentation/screens/lists/ListsViewModel.kt
package com.gazzel.sesameapp.presentation.screens.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// Import Use Case instead of repositories directly (keep repo for delete)
import com.gazzel.sesameapp.domain.usecase.GetUserListsUseCase // <<< ADD Use Case
import com.gazzel.sesameapp.domain.repository.ListRepository // Keep for deleteList
// Import necessary domain/util classes
import com.gazzel.sesameapp.domain.model.SesameList
import com.gazzel.sesameapp.domain.util.Result
import com.gazzel.sesameapp.domain.util.onError
import com.gazzel.sesameapp.domain.util.onSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log // Optional logging

@HiltViewModel
class ListsViewModel @Inject constructor(
    private val getUserListsUseCase: GetUserListsUseCase, // <<< INJECT Use Case
    private val listRepository: ListRepository // <<< Keep Repository for actions like delete
) : ViewModel() {

    private val _uiState = MutableStateFlow<ListsUiState>(ListsUiState.Loading)
    val uiState: StateFlow<ListsUiState> = _uiState.asStateFlow()

    init {
        loadLists()
    }

    private fun loadLists() {
        _uiState.value = ListsUiState.Loading
        viewModelScope.launch {
            Log.d("ListsViewModel", "Calling GetUserListsUseCase...")
            // Call the Use Case
            val result = getUserListsUseCase() // Simplified call

            // Handle the Result from the Use Case
            result.onSuccess { lists ->
                Log.d("ListsViewModel", "GetUserListsUseCase successful, received ${lists.size} lists.")
                _uiState.value = ListsUiState.Success(lists)
            }.onError { exception ->
                Log.e("ListsViewModel", "GetUserListsUseCase failed: ${exception.message}")
                _uiState.value = ListsUiState.Error(exception.message ?: "Failed to load lists")
            }
        }
    }

    fun deleteList(listId: String) {
        // Keep delete logic here, calling the repository directly is fine for simple actions
        viewModelScope.launch {
            Log.d("ListsViewModel", "Attempting to delete list: $listId")
            val deleteResult = listRepository.deleteList(listId) // Assume repo handles IO etc.
            deleteResult.onSuccess {
                Log.d("ListsViewModel", "Delete successful for $listId, reloading lists.")
                loadLists() // Reload lists after successful deletion
            }.onError { exception ->
                Log.e("ListsViewModel", "Failed to delete list $listId: ${exception.message}")
                // Optionally update UI state with a temporary deletion error message
                // For now, just logging. Could emit a separate event/state.
                // _uiState.value = ListsUiState.Error("Failed to delete: ${exception.message}") // Or handle differently
            }
        }
    }

    fun refresh() {
        loadLists()
    }
}

// ListsUiState remains the same
// sealed class ListsUiState { ... }