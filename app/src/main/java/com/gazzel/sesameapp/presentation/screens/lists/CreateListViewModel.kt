package com.gazzel.sesameapp.presentation.screens.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gazzel.sesameapp.domain.model.SesameList
import com.gazzel.sesameapp.domain.repository.ListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateListViewModel @Inject constructor(
    private val listRepository: ListRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<CreateListUiState>(CreateListUiState.Initial)
    val uiState: StateFlow<CreateListUiState> = _uiState.asStateFlow()

    fun createList(title: String, description: String, isPublic: Boolean) {
        viewModelScope.launch {
            try {
                _uiState.value = CreateListUiState.Loading
                val newList = SesameList(
                    title = title,
                    description = description,
                    isPublic = isPublic,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                result.onSuccess { createdList ->
                    _uiState.value = CreateListUiState.Success(createdList.id) // Pass the ID
                }.onError { exception ->
                    _uiState.value = CreateListUiState.Error(exception.message ?: "Failed to create list")
                }
            } catch (e: Exception) {
                _uiState.value = CreateListUiState.Error(e.message ?: "Failed to create list")
            }
        }
    }
}

sealed class CreateListUiState {
    object Initial : CreateListUiState()
    object Loading : CreateListUiState()
    data class Success(val newListId: String?) : CreateListUiState()
    data class Error(val message: String) : CreateListUiState()
} 