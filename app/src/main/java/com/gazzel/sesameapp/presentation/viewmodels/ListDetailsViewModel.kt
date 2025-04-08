// presentation/viewmodels/ListDetailsViewModel.kt
package com.gazzel.sesameapp.presentation.viewmodels

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// Import Domain Model and Use Cases
import com.gazzel.sesameapp.domain.model.SesameList // <<< Use Domain Model
import com.gazzel.sesameapp.domain.usecase.GetListDetailsUseCase // <<< Use Case
import com.gazzel.sesameapp.domain.usecase.UpdateListUseCase // <<< Use Case
import com.gazzel.sesameapp.domain.usecase.UpdatePlaceNotesUseCase // <<< Use Case
import com.gazzel.sesameapp.domain.usecase.DeleteListUseCase // <<< Use Case
import com.gazzel.sesameapp.domain.usecase.DeletePlaceItemUseCase // <<< Use Case
// Import Result and extensions
import com.gazzel.sesameapp.domain.util.Result
import com.gazzel.sesameapp.domain.util.onError
import com.gazzel.sesameapp.domain.util.onSuccess
// Other necessary imports
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update // Use update for state modification
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ListDetailsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    // Inject Use Cases
    private val getListDetailsUseCase: GetListDetailsUseCase,     // <<< Inject
    private val updateListUseCase: UpdateListUseCase,           // <<< Inject
    private val updatePlaceNotesUseCase: UpdatePlaceNotesUseCase, // <<< Inject
    private val deleteListUseCase: DeleteListUseCase,           // <<< Inject
    private val deletePlaceItemUseCase: DeletePlaceItemUseCase    // <<< Inject
    // Remove ListApiService and TokenProvider direct injection
) : ViewModel() {

    private val listId: String = savedStateHandle.get<String>(com.gazzel.sesameapp.presentation.navigation.Screen.ListDetail.ARG_LIST_ID) ?: ""

    // --- CHANGE State to hold Domain Model ---
    private val _detail = MutableStateFlow<SesameList?>(null) // <<< CHANGE Type to SesameList?
    val detail: StateFlow<SesameList?> = _detail.asStateFlow() // <<< Expose SesameList?

    // --- Loading/Error/Success states remain the same ---
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
        if (listId.isEmpty()) return
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            Log.d("ListDetailsVM", "Fetching details via UseCase for list $listId")

            // Call Use Case
            val result = getListDetailsUseCase(listId)

            // Handle Result<SesameList>
            result.onSuccess { sesameList ->
                _detail.value = sesameList // <<< Store Domain Model
                Log.d("ListDetailsVM", "Successfully fetched details for list $listId")
            }.onError { exception ->
                _errorMessage.value = exception.message ?: "Failed to fetch details"
                Log.e("ListDetailsVM", "Fetch error via UseCase: ${exception.message}", exception)
            }
            _isLoading.value = false // Set loading false regardless of outcome
        }
    }

    // --- Refactor update methods to use Use Cases ---

    fun updateListPrivacy() {
        val currentList = _detail.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val updatedList = currentList.copy(isPublic = !currentList.isPublic) // Create updated domain object
            Log.d("ListDetailsVM", "Updating privacy via UseCase for list ${updatedList.id}")

            val result = updateListUseCase(updatedList) // Call Use Case

            result.onSuccess { updatedListFromServer ->
                _detail.value = updatedListFromServer // Update state with result from use case
                Log.d("ListDetailsVM", "Successfully updated privacy for list $listId")
            }.onError { exception ->
                _errorMessage.value = exception.message ?: "Failed to update privacy"
                Log.e("ListDetailsVM", "UpdatePrivacy error via UseCase: ${exception.message}", exception)
            }
            _isLoading.value = false
        }
    }

    fun updateListName(newName: String) {
        val currentList = _detail.value ?: return
        if (newName == currentList.title || newName.isBlank()) return // No change or invalid

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val updatedList = currentList.copy(title = newName)
            Log.d("ListDetailsVM", "Updating name via UseCase for list ${updatedList.id}")

            val result = updateListUseCase(updatedList) // Call Use Case

            result.onSuccess { updatedListFromServer ->
                _detail.value = updatedListFromServer // Update state with result
                Log.d("ListDetailsVM", "Successfully updated name for list $listId")
            }.onError { exception ->
                _errorMessage.value = exception.message ?: "Failed to update name"
                Log.e("ListDetailsVM", "UpdateName error via UseCase: ${exception.message}", exception)
            }
            _isLoading.value = false
        }
    }


    fun updatePlaceNotes(placeId: String, note: String?) { // Note can be null to clear it
        if (listId.isEmpty() || placeId.isEmpty()) {
            _errorMessage.value = "Invalid List or Place ID for updating notes."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            Log.d("ListDetailsVM", "Updating notes via UseCase for place $placeId")

            // Call Use Case
            val result = updatePlaceNotesUseCase(placeId, note)

            result.onSuccess { updatedPlaceItem ->
                Log.d("ListDetailsVM", "Successfully updated notes for place $placeId. Updating local state.")
                // Update the specific place item within the _detail state
                _detail.update { currentList ->
                    currentList?.copy(
                        places = currentList.places.map {
                            if (it.id == updatedPlaceItem.id) updatedPlaceItem else it
                        }
                    )
                }
            }.onError { exception ->
                _errorMessage.value = exception.message ?: "Failed to update notes"
                Log.e("ListDetailsVM", "UpdateNotes error via UseCase: ${exception.message}", exception)
            }
            _isLoading.value = false
        }
    }

    fun deleteList() {
        if (listId.isEmpty()) {
            _errorMessage.value = "Invalid List ID for deletion."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _deleteSuccess.value = false
            Log.d("ListDetailsVM", "Deleting list via UseCase: $listId")

            // Call Use Case
            val result = deleteListUseCase(listId)

            result.onSuccess {
                _deleteSuccess.value = true // Signal success for navigation
                Log.d("ListDetailsVM", "List $listId deleted successfully.")
            }.onError { exception ->
                _errorMessage.value = exception.message ?: "Failed to delete list"
                Log.e("ListDetailsVM", "DeleteList error via UseCase: ${exception.message}", exception)
            }
            _isLoading.value = false
        }
    }

    fun deletePlace(placeId: String) {
        if (listId.isEmpty() || placeId.isEmpty()) {
            _errorMessage.value = "Invalid List or Place ID for deletion."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true // Indicate activity
            _errorMessage.value = null
            Log.d("ListDetailsVM", "Deleting place via UseCase: $placeId")

            // Call Use Case
            val result = deletePlaceItemUseCase(placeId)

            result.onSuccess {
                Log.d("ListDetailsVM", "Successfully deleted place $placeId. Updating local state.")
                // Remove the place item from the local _detail state
                _detail.update { currentList ->
                    currentList?.copy(
                        places = currentList.places.filterNot { it.id == placeId }
                    )
                }
            }.onError { exception ->
                _errorMessage.value = exception.message ?: "Failed to delete place"
                Log.e("ListDetailsVM", "DeletePlace error via UseCase: ${exception.message}", exception)
            }
            _isLoading.value = false
        }
    }

    // Helper functions remain the same
    fun clearErrorMessage() { _errorMessage.value = null }
    fun resetDeleteSuccess() { _deleteSuccess.value = false }
}