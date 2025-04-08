// presentation/viewmodels/ListDetailsViewModel.kt
package com.gazzel.sesameapp.presentation.viewmodels

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// Import Domain Model and Use Cases
import com.gazzel.sesameapp.domain.model.SesameList
import com.gazzel.sesameapp.domain.usecase.GetListDetailsUseCase
import com.gazzel.sesameapp.domain.usecase.UpdateListUseCase
import com.gazzel.sesameapp.domain.usecase.UpdatePlaceNotesUseCase
import com.gazzel.sesameapp.domain.usecase.DeleteListUseCase
import com.gazzel.sesameapp.domain.usecase.DeletePlaceItemUseCase
// Import Result and extensions
import com.gazzel.sesameapp.domain.util.Result
import com.gazzel.sesameapp.domain.util.onError
import com.gazzel.sesameapp.domain.util.onSuccess
// Other necessary imports
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
// *** REMOVE import kotlinx.coroutines.flow.update *** - Use .value assignment for state transitions
import kotlinx.coroutines.launch
import javax.inject.Inject

// Define the sealed class OUTSIDE the ViewModel class for better organization
// (Or in its own file: presentation/screens/listdetail/ListDetailsUiState.kt)
sealed class ListDetailsUiState {
    object Loading : ListDetailsUiState()
    data class Success(val list: SesameList) : ListDetailsUiState()
    data class Error(val message: String) : ListDetailsUiState()
    object DeleteSuccess : ListDetailsUiState()
}

@HiltViewModel
class ListDetailsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getListDetailsUseCase: GetListDetailsUseCase,
    private val updateListUseCase: UpdateListUseCase,
    private val updatePlaceNotesUseCase: UpdatePlaceNotesUseCase,
    private val deleteListUseCase: DeleteListUseCase,
    private val deletePlaceItemUseCase: DeletePlaceItemUseCase
) : ViewModel() {

    private val listId: String = savedStateHandle.get<String>(com.gazzel.sesameapp.presentation.navigation.Screen.ListDetail.ARG_LIST_ID) ?: ""

    // Single StateFlow for the UI State
    private val _uiState = MutableStateFlow<ListDetailsUiState>(ListDetailsUiState.Loading) // Start in Loading
    val uiState: StateFlow<ListDetailsUiState> = _uiState.asStateFlow()

    init {
        if (listId.isNotEmpty()) {
            fetchListDetail()
        } else {
            _uiState.value = ListDetailsUiState.Error("List ID not provided.")
            Log.e("ListDetailsVM", "List ID is missing in SavedStateHandle")
        }
    }

    // Make public for potential retry logic in UI
    fun fetchListDetail() {
        if (listId.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = ListDetailsUiState.Loading // Set Loading state
            Log.d("ListDetailsVM", "Fetching details via UseCase for list $listId")

            val result = getListDetailsUseCase(listId)

            result.onSuccess { sesameList ->
                _uiState.value = ListDetailsUiState.Success(sesameList) // Set Success state
                Log.d("ListDetailsVM", "Successfully fetched details for list $listId")
            }.onError { exception ->
                _uiState.value = ListDetailsUiState.Error(exception.message ?: "Failed to fetch details") // Set Error state
                Log.e("ListDetailsVM", "Fetch error via UseCase: ${exception.message}", exception)
            }
            // Loading is implicitly finished by setting Success or Error state
            // *** REMOVED _isLoading.value = false ***
        }
    }

    fun updateListPrivacy() {
        val currentSuccessState = _uiState.value as? ListDetailsUiState.Success
        if (currentSuccessState == null) {
            Log.w("ListDetailsVM", "Cannot update privacy, not in Success state.")
            return
        }
        val currentList = currentSuccessState.list

        viewModelScope.launch {
            val previousState = currentSuccessState // Store previous state for potential revert on error
            _uiState.value = ListDetailsUiState.Loading // Show loading
            val updatedListDomain = currentList.copy(isPublic = !currentList.isPublic)
            Log.d("ListDetailsVM", "Updating privacy via UseCase for list ${updatedListDomain.id}")

            val result = updateListUseCase(updatedListDomain)

            result.onSuccess { updatedListFromServer ->
                _uiState.value = ListDetailsUiState.Success(updatedListFromServer) // Update state
                Log.d("ListDetailsVM", "Successfully updated privacy for list $listId")
            }.onError { exception ->
                _uiState.value = ListDetailsUiState.Error(exception.message ?: "Failed to update privacy")
                // Optionally revert to previous state visually if using Snackbar for error:
                // _uiState.value = previousState
                Log.e("ListDetailsVM", "UpdatePrivacy error via UseCase: ${exception.message}", exception)
            }
            // *** REMOVED references to _isLoading, _errorMessage, _detail ***
        }
    }

    fun updateListName(newName: String) {
        val currentSuccessState = _uiState.value as? ListDetailsUiState.Success
        if (currentSuccessState == null) {
            Log.w("ListDetailsVM", "Cannot update name, not in Success state.")
            return
        }
        val currentList = currentSuccessState.list

        if (newName == currentList.title || newName.isBlank()) return // No change or invalid

        viewModelScope.launch {
            val previousState = currentSuccessState
            _uiState.value = ListDetailsUiState.Loading // Show loading
            val updatedListDomain = currentList.copy(title = newName)
            Log.d("ListDetailsVM", "Updating name via UseCase for list ${updatedListDomain.id}")

            val result = updateListUseCase(updatedListDomain)

            result.onSuccess { updatedListFromServer ->
                _uiState.value = ListDetailsUiState.Success(updatedListFromServer) // Update state
                Log.d("ListDetailsVM", "Successfully updated name for list $listId")
            }.onError { exception ->
                _uiState.value = ListDetailsUiState.Error(exception.message ?: "Failed to update name")
                // Optionally revert: _uiState.value = previousState
                Log.e("ListDetailsVM", "UpdateName error via UseCase: ${exception.message}", exception)
            }
            // *** REMOVED references to _isLoading, _errorMessage, _detail ***
        }
    }

    fun updatePlaceNotes(placeId: String, note: String?) {
        val currentSuccessState = _uiState.value as? ListDetailsUiState.Success
        if (currentSuccessState == null) {
            Log.w("ListDetailsVM", "Cannot update notes, not in Success state.")
            return
        }
        val currentList = currentSuccessState.list

        if (listId.isEmpty() || placeId.isEmpty()) {
            Log.e("ListDetailsVM", "Invalid List or Place ID for updating notes.")
            _uiState.value = ListDetailsUiState.Error("Cannot update notes: Invalid ID") // Set error state
            return
        }

        viewModelScope.launch {
            val previousState = currentSuccessState
            _uiState.value = ListDetailsUiState.Loading // Show loading
            Log.d("ListDetailsVM", "Updating notes via UseCase for place $placeId")
            val result = updatePlaceNotesUseCase(placeId, note)

            result.onSuccess { updatedPlaceItem ->
                Log.d("ListDetailsVM", "Successfully updated notes for place $placeId. Updating local state.")
                // Update the specific place item within the current list data
                val updatedList = currentList.copy(
                    places = currentList.places.map {
                        if (it.id == updatedPlaceItem.id) updatedPlaceItem else it
                    }
                )
                _uiState.value = ListDetailsUiState.Success(updatedList) // Set new success state

            }.onError { exception ->
                _uiState.value = ListDetailsUiState.Error(exception.message ?: "Failed to update notes")
                // Optionally revert: _uiState.value = previousState
                Log.e("ListDetailsVM", "UpdateNotes error via UseCase: ${exception.message}", exception)
            }
            // *** REMOVED references to _isLoading, _errorMessage ***
        }
    }

    fun deleteList() {
        if (listId.isEmpty()) {
            _uiState.value = ListDetailsUiState.Error("Invalid List ID for deletion.") // Set error state
            return
        }
        viewModelScope.launch {
            val previousState = _uiState.value // Store previous state in case of error
            _uiState.value = ListDetailsUiState.Loading // Show loading
            Log.d("ListDetailsVM", "Deleting list via UseCase: $listId")

            val result = deleteListUseCase(listId)

            result.onSuccess {
                _uiState.value = ListDetailsUiState.DeleteSuccess // Signal success for navigation
                Log.d("ListDetailsVM", "List $listId deleted successfully.")
            }.onError { exception ->
                _uiState.value = ListDetailsUiState.Error(exception.message ?: "Failed to delete list")
                // Optionally revert: if (previousState is ListDetailsUiState.Success) _uiState.value = previousState
                Log.e("ListDetailsVM", "DeleteList error via UseCase: ${exception.message}", exception)
            }
            // *** REMOVED references to _isLoading, _errorMessage, _deleteSuccess ***
        }
    }

    fun deletePlace(placeId: String) {
        val currentSuccessState = _uiState.value as? ListDetailsUiState.Success
        if (currentSuccessState == null) {
            Log.w("ListDetailsVM", "Cannot delete place, not in Success state.")
            return
        }
        val currentList = currentSuccessState.list

        if (listId.isEmpty() || placeId.isEmpty()) {
            _uiState.value = ListDetailsUiState.Error("Invalid List or Place ID for deletion.") // Set error state
            return
        }

        viewModelScope.launch {
            val previousState = currentSuccessState
            _uiState.value = ListDetailsUiState.Loading // Indicate activity
            Log.d("ListDetailsVM", "Deleting place via UseCase: $placeId")

            val result = deletePlaceItemUseCase(placeId)

            result.onSuccess {
                Log.d("ListDetailsVM", "Successfully deleted place $placeId. Updating local state.")
                // Remove the place item from the current list data
                val updatedList = currentList.copy(
                    places = currentList.places.filterNot { it.id == placeId }
                )
                _uiState.value = ListDetailsUiState.Success(updatedList) // Set new success state

            }.onError { exception ->
                _uiState.value = ListDetailsUiState.Error(exception.message ?: "Failed to delete place")
                // Optionally revert: _uiState.value = previousState
                Log.e("ListDetailsVM", "DeletePlace error via UseCase: ${exception.message}", exception)
            }
            // *** REMOVED references to _isLoading, _errorMessage, _detail ***
        }
    }

    // *** REMOVED clearErrorMessage() and resetDeleteSuccess() ***

} // End ViewModel class