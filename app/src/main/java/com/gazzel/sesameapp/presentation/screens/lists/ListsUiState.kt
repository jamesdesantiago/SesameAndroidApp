package com.gazzel.sesameapp.presentation.screens.lists

import com.gazzel.sesameapp.domain.model.SesameList

sealed class ListsUiState {
    object Loading : ListsUiState()
    data class Success(val userLists: List<SesameList>) : ListsUiState()
    data class Error(val message: String) : ListsUiState()
} 