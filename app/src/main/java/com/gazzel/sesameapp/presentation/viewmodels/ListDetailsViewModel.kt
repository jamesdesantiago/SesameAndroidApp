package com.gazzel.sesameapp.presentation.viewmodels

import android.util.Log // <-- Add Log import
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gazzel.sesameapp.data.service.UserListService
import com.gazzel.sesameapp.domain.model.ListResponse // Domain ListResponse
import com.gazzel.sesameapp.data.service.PlaceUpdate // <-- IMPORT Service DTO
// Remove domain PlaceUpdate import if present
import com.gazzel.sesameapp.domain.model.ListUpdate // Assuming service uses this one
// REMOVE: import com.gazzel.sesameapp.presentation.activities.ListDetailCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// TODO: Define this state class properly if needed, potentially holding PlaceItems
// data class ListDetailState(...)

class ListDetailsViewModel(
    private val listId: String,
    initialName: String,
    private val listService: UserListService,
    private val getValidToken: suspend () -> String?
) : ViewModel() {

    // --- State ---
    private val _detail = MutableStateFlow(
        // Correct initial state using ListResponse definition
        ListResponse(
            id = listId,
            name = initialName,
            description = null,
            isPrivate = false, // Use isPrivate
            collaborators = emptyList(),
            places = null // Default to null or emptyList() based on ListResponse definition
        )
    )
    val detail: StateFlow<ListResponse> = _detail.asStateFlow() // Correct type

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _deleteSuccess = MutableStateFlow(false)
    val deleteSuccess: StateFlow<Boolean> = _deleteSuccess.asStateFlow()
    // --- End State ---

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
    fun resetDeleteSuccess() {
        _deleteSuccess.value = false
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
                    // Ensure service method name and params match UserListService.kt
                    val response = listService.getListDetail(id = listId, token = "Bearer $token")
                    if (response.isSuccessful) {
                        val fetchedDetail = response.body()
                        if (fetchedDetail != null) {
                            // CRITICAL: If fetchedDetail.places is List<PlaceDto>, map to List<PlaceItem> here!
                            // Example:
                            // val mappedPlaces = fetchedDetail.places?.map { dto -> dto.toPlaceItem(listId) }
                            // _detail.value = fetchedDetail.copy(places = mappedPlaces)
                            // For now, assuming ListResponse ALREADY contains List<PlaceItem>? based on domain model
                            _detail.value = fetchedDetail
                            // REMOVE ListDetailCache
                        } else {
                            _errorMessage.value = "No data returned from server"
                        }
                    } else {
                        val errorBody = response.errorBody()?.string() ?: "Unknown error"
                        _errorMessage.value = "Failed to fetch details: ${response.code()} ${response.message()}"
                        Log.e("ListDetailsVM", "Fetch error: ${response.code()} - $errorBody")
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "Exception fetching details: ${e.localizedMessage}"
                    Log.e("ListDetailsVM", "Fetch exception", e)
                }
            } else {
                _errorMessage.value = "Authentication error: Unable to get token"
                Log.w("ListDetailsVM", "getValidToken returned null")
            }
            _isLoading.value = false
        }
    }

    fun updateListPrivacy() {
        viewModelScope.launch {
            val token = getValidToken()
            if (token != null) {
                _isLoading.value = true
                _errorMessage.value = null
                try {
                    val newPrivacyStatus = !_detail.value.isPrivate // Use isPrivate
                    val updateData = ListUpdate(isPrivate = newPrivacyStatus)
                    // Ensure service method name and params match UserListService.kt
                    val response = listService.updateList(
                        id = listId, // Assuming service path param is 'id'
                        update = updateData,
                        token = "Bearer $token" // Assuming service header param is 'token'
                    )
                    if (response.isSuccessful) {
                        response.body()?.let { updatedList ->
                            // TODO: Map DTO->Item if needed
                            _detail.value = updatedList
                            // REMOVE ListDetailCache
                        } ?: fetchListDetail() // Refetch if body is null
                    } else {
                        val errorBody = response.errorBody()?.string() ?: "Unknown error"
                        _errorMessage.value = "Failed to update privacy: ${response.code()} ${response.message()}"
                        Log.e("ListDetailsVM", "UpdatePrivacy error: ${response.code()} - $errorBody")
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "Exception updating privacy: ${e.localizedMessage}"
                    Log.e("ListDetailsVM", "UpdatePrivacy exception", e)
                } finally {
                    _isLoading.value = false
                }
            } else { _errorMessage.value = "Authentication error: Unable to get token" }
        }
    }

    fun updateListName(newName: String) {
        if (newName.isBlank()) {
            _errorMessage.value = "List name cannot be empty"
            return
        }
        viewModelScope.launch {
            val token = getValidToken()
            if (token != null) {
                _isLoading.value = true
                _errorMessage.value = null
                try {
                    val updateData = ListUpdate(name = newName)
                    // Ensure service method name and params match UserListService.kt
                    val response = listService.updateList(
                        id = listId,
                        update = updateData,
                        token = "Bearer $token"
                    )
                    if (response.isSuccessful) {
                        response.body()?.let { updatedList ->
                            // TODO: Map DTO->Item if needed
                            _detail.value = updatedList
                            // REMOVE ListDetailCache
                        } ?: fetchListDetail() // Refetch if body is null
                    } else {
                        val errorBody = response.errorBody()?.string() ?: "Unknown error"
                        _errorMessage.value = "Failed to update list name: ${response.code()} ${response.message()}"
                        Log.e("ListDetailsVM", "UpdateName error: ${response.code()} - $errorBody")
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "Exception updating name: ${e.localizedMessage}"
                    Log.e("ListDetailsVM", "UpdateName exception", e)
                } finally {
                    _isLoading.value = false
                }
            } else { _errorMessage.value = "Authentication error: Unable to get token" }
        }
    }

    fun updatePlaceNotes(placeId: String, note: String) {
        viewModelScope.launch {
            val token = getValidToken()
            if (token != null) {
                _isLoading.value = true
                _errorMessage.value = null
                try {
                    // Use data.service.PlaceUpdate
                    val updateDto = PlaceUpdate(notes = note)
                    // Ensure service method name and params match UserListService.kt
                    val response = listService.updatePlace(
                        listId = listId, // Assuming service path param is 'listId'
                        placeId = placeId, // Assuming service path param is 'placeId'
                        update = updateDto,
                        authHeader = "Bearer $token" // Assuming service header param is 'authHeader'
                    )
                    if (response.isSuccessful || response.code() == 204) { // Allow 204 No Content
                        fetchListDetail() // Refetch the whole list
                    } else {
                        val errorBody = response.errorBody()?.string() ?: "Unknown error"
                        _errorMessage.value = "Failed to update notes: ${response.code()} ${response.message()}"
                        Log.e("ListDetailsVM", "UpdateNotes error: ${response.code()} - $errorBody")
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "Exception updating notes: ${e.localizedMessage}"
                    Log.e("ListDetailsVM", "UpdateNotes exception", e)
                } finally {
                    _isLoading.value = false
                }
            } else { _errorMessage.value = "Authentication error: Unable to get token" }
        }
    }

    // REMOVED onSuccess parameter
    fun deleteList() {
        viewModelScope.launch {
            val token = getValidToken()
            if (token != null) {
                _isLoading.value = true
                _errorMessage.value = null
                _deleteSuccess.value = false
                try {
                    // Ensure service method name and params match UserListService.kt
                    val response = listService.deleteList(
                        id = listId, // Assuming service path param is 'id'
                        token = "Bearer $token" // Assuming service header param is 'token'
                    )
                    if (response.isSuccessful || response.code() == 204) {
                        // REMOVE ListDetailCache reference
                        _deleteSuccess.value = true // Set state to trigger UI reaction (e.g., navigation)
                        Log.d("ListDetailsVM", "List $listId deleted successfully.")
                    } else {
                        val errorBody = response.errorBody()?.string() ?: "Unknown error"
                        _errorMessage.value = "Failed to delete list: ${response.code()} ${response.message()}"
                        Log.e("ListDetailsVM", "DeleteList error: ${response.code()} - $errorBody")
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "Exception deleting list: ${e.localizedMessage}"
                    Log.e("ListDetailsVM", "DeleteList exception", e)
                } finally {
                    _isLoading.value = false
                }
            } else { _errorMessage.value = "Authentication error: Unable to get token" }
        }
    }

    // Placeholder - Ensure UserListService has a method for this if needed
    fun deletePlace(placeId: String) {
        viewModelScope.launch {
            Log.w("ListDetailsVM", "deletePlace function called but likely not implemented in UserListService.")
            _errorMessage.value = "Deleting individual places not yet supported."
            // Add actual service call here if/when implemented
        }
    }
}

// --- ViewModel Factory ---
class ListDetailsViewModelFactory(
    private val listId: String,
    private val initialName: String,
    private val listService: UserListService,
    // Parameter name MUST match ViewModel constructor
    private val getValidToken: suspend () -> String?
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ListDetailsViewModel::class.java)) {
            // Pass parameters with the correct names
            return ListDetailsViewModel(listId, initialName, listService, getValidToken) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}