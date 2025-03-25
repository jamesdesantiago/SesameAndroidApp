package com.gazzel.sesameapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ListDetailViewModel(
    private val listId: Int,
    initialName: String,
    private val listService: ListService,
    private val getValidToken: suspend () -> String?
) : ViewModel() {

    private val _detail = MutableStateFlow(
        ListDetailCache.cache[listId] ?: ListResponse(
            id = listId,
            name = initialName,
            description = null,
            isPrivate = false,
            collaborators = emptyList(),
            places = emptyList()
        )
    )
    val detail: StateFlow<ListResponse> = _detail.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun setErrorMessage(message: String?) {
        _errorMessage.value = message
    }

    init {
        fetchListDetail()
    }

    private fun fetchListDetail() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val token = getValidToken()
            if (token != null) {
                try {
                    val response = listService.getListDetail(listId, "Bearer $token")
                    if (response.isSuccessful) {
                        val fetchedDetail = response.body()
                        if (fetchedDetail != null) {
                            _detail.value = fetchedDetail
                            ListDetailCache.cache[listId] = fetchedDetail
                        } else {
                            _errorMessage.value = "No data returned from server"
                        }
                    } else {
                        _errorMessage.value = "Failed to fetch details: ${response.message()}"
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "Exception: ${e.message}"
                }
            } else {
                _errorMessage.value = "Missing token"
            }
            _isLoading.value = false
        }
    }

    fun updateListPrivacy() {
        viewModelScope.launch {
            val token = getValidToken()
            if (token != null) {
                try {
                    val newPrivacyStatus = !_detail.value.isPrivate
                    val response = listService.updateList(
                        listId,
                        ListUpdate(isPrivate = newPrivacyStatus),
                        "Bearer $token"
                    )
                    if (response.isSuccessful) {
                        val updatedList = response.body()
                        if (updatedList != null) {
                            _detail.value = updatedList
                            ListDetailCache.cache[listId] = updatedList
                        } else {
                            _errorMessage.value = "No data returned from server"
                        }
                    } else {
                        _errorMessage.value = "Failed to update privacy: ${response.message()}"
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "Exception: ${e.message}"
                }
            } else {
                _errorMessage.value = "Missing token"
            }
        }
    }

    fun updateListName(newName: String) {
        viewModelScope.launch {
            val token = getValidToken()
            if (token != null) {
                try {
                    val response = listService.updateList(
                        listId,
                        ListUpdate(name = newName),
                        "Bearer $token"
                    )
                    if (response.isSuccessful) {
                        val updatedList = response.body()
                        if (updatedList != null) {
                            _detail.value = updatedList
                            ListDetailCache.cache[listId] = updatedList
                        } else {
                            _errorMessage.value = "No data returned from server"
                        }
                    } else {
                        _errorMessage.value = "Failed to update list name: ${response.message()}"
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "Exception: ${e.message}"
                }
            } else {
                _errorMessage.value = "Missing token"
            }
        }
    }

    fun updatePlaceNotes(placeId: Int, note: String) {
        viewModelScope.launch {
            val token = getValidToken()
            if (token != null) {
                try {
                    val response = listService.updatePlace(
                        listId,
                        placeId,
                        PlaceUpdate(notes = note),
                        "Bearer $token"
                    )
                    if (response.isSuccessful) {
                        val refreshResponse = listService.getListDetail(listId, "Bearer $token")
                        if (refreshResponse.isSuccessful) {
                            val refreshedDetail = refreshResponse.body()
                            if (refreshedDetail != null) {
                                _detail.value = refreshedDetail
                                ListDetailCache.cache[listId] = refreshedDetail
                            }
                        }
                    } else {
                        _errorMessage.value = "Failed to update notes: ${response.message()}"
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "Exception: ${e.message}"
                }
            } else {
                _errorMessage.value = "Missing token"
            }
        }
    }

    fun deleteList(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val token = getValidToken()
            if (token != null) {
                try {
                    val response = listService.deleteList(listId, "Bearer $token")
                    if (response.isSuccessful) {
                        ListDetailCache.cache.remove(listId)
                        onSuccess()
                    } else {
                        _errorMessage.value = "Failed to delete list: ${response.message()}"
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "Exception: ${e.message}"
                }
            } else {
                _errorMessage.value = "Missing token"
            }
        }
    }
}

class ListDetailViewModelFactory(
    private val listId: Int,
    private val initialName: String,
    private val listService: ListService,
    private val getValidToken: suspend () -> String?
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ListDetailViewModel::class.java)) {
            return ListDetailViewModel(listId, initialName, listService, getValidToken) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}