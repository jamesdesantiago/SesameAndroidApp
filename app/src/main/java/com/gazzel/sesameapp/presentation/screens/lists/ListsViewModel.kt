package com.gazzel.sesameapp.presentation.screens.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gazzel.sesameapp.domain.repository.ListRepository
import com.gazzel.sesameapp.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

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
        viewModelScope.launch {
            try {
                val userFlow = userRepository.getCurrentUser()
                val user = userFlow.first() // <<< user is a Flow<User>
                // ERROR IS HERE: Trying to access user.id directly on the Flow
                val userLists = listRepository.getUserLists(user.id).collect { lists ->
                    _uiState.value = ListsUiState.Success(lists)
                }
            } catch (e: Exception) {
                _uiState.value = ListsUiState.Error(e.message ?: "Failed to load lists")
            }
        }
    }

    fun deleteList(listId: String) {
        viewModelScope.launch {
            try {
                listRepository.deleteList(listId)
                loadLists() // Reload lists after deletion
            } catch (e: Exception) {
                // Handle error silently as this is a background operation
            }
        }
    }

    fun refresh() {
        loadLists()
    }
} 