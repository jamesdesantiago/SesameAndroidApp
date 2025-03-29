package com.gazzel.sesameapp.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gazzel.sesameapp.domain.model.SesameList
import com.gazzel.sesameapp.domain.model.User
import com.gazzel.sesameapp.domain.repository.ListRepository
import com.gazzel.sesameapp.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val listRepository: ListRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _recentLists = MutableStateFlow<List<SesameList>>(emptyList())
    val recentLists: StateFlow<List<SesameList>> = _recentLists.asStateFlow()

    init {
        loadUserAndLists()
    }

    private fun loadUserAndLists() {
        viewModelScope.launch {
            try {
                val user = userRepository.getCurrentUser()
                val lists = listRepository.getRecentLists(limit = 5)
                _recentLists.value = lists
                _uiState.value = HomeUiState.Success(user, lists)
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "Failed to load data")
            }
        }
    }

    fun searchLists(query: String) {
        viewModelScope.launch {
            try {
                val lists = if (query.isBlank()) {
                    listRepository.getRecentLists(limit = 5)
                } else {
                    listRepository.searchLists(query)
                }
                _recentLists.value = lists
            } catch (e: Exception) {
                // Handle error silently as this is a search operation
            }
        }
    }

    fun refresh() {
        loadUserAndLists()
    }
}

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(val user: User, val recentLists: List<SesameList>) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
} 