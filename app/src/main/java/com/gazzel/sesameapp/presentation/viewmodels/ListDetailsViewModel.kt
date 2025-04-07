// presentation/viewmodels/ListDetailsViewModel.kt
package com.gazzel.sesameapp.presentation.viewmodels

import androidx.lifecycle.SavedStateHandle // Import SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gazzel.sesameapp.data.service.PlaceUpdate
// Import consolidated ListApiService
import com.gazzel.sesameapp.data.service.ListApiService // Adjust import
// Import TokenProvider (Create this class first)
import com.gazzel.sesameapp.domain.auth.TokenProvider // Adjust import path
import com.gazzel.sesameapp.domain.model.ListResponse
import com.gazzel.sesameapp.domain.model.ListUpdate
import dagger.hilt.android.lifecycle.HiltViewModel // Import HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject // Import Inject
import android.util.Log

@HiltViewModel // Add Hilt annotation
class ListDetailsViewModel @Inject constructor( // Use @Inject constructor
    private val savedStateHandle: SavedStateHandle, // Inject SavedStateHandle
    private val listService: ListApiService, // Inject consolidated service
    private val tokenProvider: TokenProvider // Inject TokenProvider
) : ViewModel() {

    // Get listId from navigation arguments via SavedStateHandle
    private val listId: String = savedStateHandle.get<String>("listId") ?: ""

    private val _detail = MutableStateFlow<ListResponse?>(null) // Start as null
    val detail: StateFlow<ListResponse?> = _detail.asStateFlow()

    // ... (isLoading, errorMessage, deleteSuccess states remain the same) ...
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    private val _deleteSuccess = MutableStateFlow(false)
    val deleteSuccess: StateFlow<Boolean> = _deleteSuccess.asStateFlow()

    init {
        if (listId.isNotEmpty()) {
            fetchListDetail()
        } else {
            _errorMessage.value = "List ID not provided."
            Log.e("ListDetailsVM", "List ID is missing in SavedStateHandle")
        }
    }

    private fun fetchListDetail() {
        if (listId.isEmpty()) return // Don't fetch if ID is invalid
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val token = tokenProvider.getToken() // Use TokenProvider
            if (token != null) {
                try {
                    // Ensure method names match consolidated ListApiService
                    val response = listService.getListDetail(listId = listId, token = "Bearer $token")
                    if (response.isSuccessful) {
                        _detail.value = response.body()
                    } else {
                        // ... (error handling) ...
                        val errorBody = response.errorBody()?.string() ?: "Unknown error"
                        _errorMessage.value = "Failed to fetch details: ${response.code()} ${response.message()}"
                        Log.e("ListDetailsVM", "Fetch error: ${response.code()} - $errorBody")
                    }
                } catch (e: Exception) {
                    // ... (exception handling) ...
                    _errorMessage.value = "Exception fetching details: ${e.localizedMessage}"
                    Log.e("ListDetailsVM", "Fetch exception", e)
                } finally {
                    _isLoading.value = false
                }
            } else {
                // ... (auth error handling) ...
                _errorMessage.value = "Authentication error: Unable to get token"
                Log.w("ListDetailsVM", "TokenProvider returned null")
                _isLoading.value = false // Ensure loading stops
            }
        }
    }

    // --- Refactor other methods (updateListPrivacy, updateListName, etc.) ---
    // Replace `getValidToken()` with `tokenProvider.getToken()`
    // Ensure they use the injected consolidated `listService`
    // Example:
    fun updateListPrivacy() {
        val currentDetail = _detail.value ?: return // Need current state
        viewModelScope.launch {
            val token = tokenProvider.getToken() // Use provider
            if (token != null) {
                _isLoading.value = true
                _errorMessage.value = null
                try {
                    val newPrivacyStatus = !currentDetail.isPrivate
                    val updateData = ListUpdate(isPrivate = newPrivacyStatus)
                    val response = listService.updateList( // Use consolidated service
                        listId = listId,
                        update = updateData,
                        token = "Bearer $token"
                    )
                    if (response.isSuccessful) {
                        _detail.value = response.body() ?: currentDetail.copy(isPrivate = newPrivacyStatus) // Update local state optimistically or use response
                    } else {
                        // ... (error handling) ...
                        val errorBody = response.errorBody()?.string() ?: "Unknown error"
                        _errorMessage.value = "Failed to update privacy: ${response.code()} ${response.message()}"
                        Log.e("ListDetailsVM", "UpdatePrivacy error: ${response.code()} - $errorBody")
                    }
                } catch (e: Exception) {
                    // ... (exception handling) ...
                    _errorMessage.value = "Exception updating privacy: ${e.localizedMessage}"
                    Log.e("ListDetailsVM", "UpdatePrivacy exception", e)
                } finally {
                    _isLoading.value = false
                }
            } else { /* auth error */ }
        }
    }

    // Add updateListName, updatePlaceNotes, deleteList, deletePlace similarly,
    // using tokenProvider and the consolidated listService.

    // Example deleteList
    fun deleteList() {
        if (listId.isEmpty()) return
        viewModelScope.launch {
            val token = tokenProvider.getToken()
            if (token != null) {
                _isLoading.value = true
                _errorMessage.value = null
                _deleteSuccess.value = false // Reset delete success flag
                try {
                    // Use consolidated service
                    val response = listService.deleteList(listId = listId, token = "Bearer $token")
                    if (response.isSuccessful || response.code() == 204) {
                        _deleteSuccess.value = true // Signal success
                        Log.d("ListDetailsVM", "List $listId deleted successfully.")
                    } else {
                        // ... (error handling) ...
                        val errorBody = response.errorBody()?.string() ?: "Unknown error"
                        _errorMessage.value = "Failed to delete list: ${response.code()} ${response.message()}"
                        Log.e("ListDetailsVM", "DeleteList error: ${response.code()} - $errorBody")
                    }
                } catch (e: Exception) {
                    // ... (exception handling) ...
                    _errorMessage.value = "Exception deleting list: ${e.localizedMessage}"
                    Log.e("ListDetailsVM", "DeleteList exception", e)
                } finally {
                    _isLoading.value = false
                }
            } else { /* auth error */ }
        }
    }

    // Add updatePlaceNotes using the consolidated service
    fun updatePlaceNotes(placeId: String, note: String) {
        if (listId.isEmpty() || placeId.isEmpty()) return
        viewModelScope.launch {
            val token = tokenProvider.getToken()
            if (token != null) {
                _isLoading.value = true
                _errorMessage.value = null
                try {
                    // Use data.service.PlaceUpdate or define a DTO
                    val updateDto = PlaceUpdate(notes = note)
                    // Use consolidated service (assuming updatePlace exists there)
                    val response = listService.updatePlace( // Adjust method name if needed
                        listId = listId,
                        placeId = placeId,
                        update = updateDto, // Pass the DTO
                        token = "Bearer $token" // Adjust param name if needed
                    )
                    if (response.isSuccessful || response.code() == 204) {
                        fetchListDetail() // Refetch the whole list to see changes
                    } else {
                        // ... (error handling) ...
                        val errorBody = response.errorBody()?.string() ?: "Unknown error"
                        _errorMessage.value = "Failed to update notes: ${response.code()} ${response.message()}"
                        Log.e("ListDetailsVM", "UpdateNotes error: ${response.code()} - $errorBody")
                    }
                } catch (e: Exception) {
                    // ... (exception handling) ...
                    _errorMessage.value = "Exception updating notes: ${e.localizedMessage}"
                    Log.e("ListDetailsVM", "UpdateNotes exception", e)
                } finally {
                    _isLoading.value = false
                }
            } else { /* auth error */ }
        }
    }

    fun clearErrorMessage() { _errorMessage.value = null }
    fun resetDeleteSuccess() { _deleteSuccess.value = false }


}
// REMOVE ListDetailsViewModelFactory