// presentation/screens/lists/CreateListViewModel.kt
package com.gazzel.sesameapp.presentation.screens.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gazzel.sesameapp.domain.model.SesameList
import com.gazzel.sesameapp.domain.repository.ListRepository // Inject Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.gazzel.sesameapp.domain.util.Result // Import Result
import com.gazzel.sesameapp.domain.util.onSuccess // Import extensions
import com.gazzel.sesameapp.domain.util.onError // Import extensions


@HiltViewModel
class CreateListViewModel @Inject constructor(
    private val listRepository: ListRepository // <<< Inject Repository
) : ViewModel() {

    private val _uiState = MutableStateFlow<CreateListUiState>(CreateListUiState.Initial)
    val uiState: StateFlow<CreateListUiState> = _uiState.asStateFlow()

    fun createList(title: String, description: String, isPublic: Boolean) {
        viewModelScope.launch {
            try {
                _uiState.value = CreateListUiState.Loading
                // Create domain model instance
                val newListDomain = SesameList(
                    // id will be assigned by backend/repo, don't set here unless needed locally first
                    title = title,
                    description = description,
                    isPublic = isPublic,
                    // Timestamps might be set by backend/repo, defaults are fine here
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                    // userId might be added by repo/API based on token
                )

                // Call the repository method which returns Result<SesameList>
                val result: Result<SesameList> = listRepository.createList(newListDomain)

                // Handle the result
                result.onSuccess { createdList ->
                    // Success: createdList is the domain SesameList returned by the repo
                    _uiState.value = CreateListUiState.Success(createdList.id) // Pass the ID
                }.onError { exception ->
                    // Error: exception is the AppException from the repo
                    _uiState.value = CreateListUiState.Error(exception.message ?: "Failed to create list")
                }
            } catch (e: Exception) {
                _uiState.value = CreateListUiState.Error(e.message ?: "Failed to create list")
            }
        }
    }

    // Function to reset state after navigation if needed
    fun resetState() {
        _uiState.value = CreateListUiState.Initial
    }
}

// --- State definition remains the same ---
sealed class CreateListUiState {
    object Initial : CreateListUiState()
    object Loading : CreateListUiState()
    data class Success(val newListId: String?) : CreateListUiState() // Can be null if ID fetch fails post-creation
    data class Error(val message: String) : CreateListUiState()
}