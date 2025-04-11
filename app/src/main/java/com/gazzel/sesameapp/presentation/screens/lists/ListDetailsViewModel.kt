// app/src/main/java/com/gazzel/sesameapp/presentation/screens/lists/ListDetailsViewModel.kt (or viewmodels path)
package com.gazzel.sesameapp.presentation.screens.lists // Or viewmodels path

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.gazzel.sesameapp.domain.model.PlaceItem
import com.gazzel.sesameapp.domain.model.SesameList // Keep domain model
// Import Use Cases
import com.gazzel.sesameapp.domain.usecase.GetListDetailsUseCase // Fetches metadata
import com.gazzel.sesameapp.domain.usecase.GetPlacesInListPaginatedUseCase // <<< ADDED
import com.gazzel.sesameapp.domain.usecase.UpdateListUseCase
import com.gazzel.sesameapp.domain.usecase.UpdatePlaceNotesUseCase
import com.gazzel.sesameapp.domain.usecase.DeleteListUseCase
import com.gazzel.sesameapp.domain.usecase.DeletePlaceItemUseCase
// Import Result Utils
import com.gazzel.sesameapp.domain.util.Result
import com.gazzel.sesameapp.domain.util.onError
import com.gazzel.sesameapp.domain.util.onSuccess
// Navigation and Hilt
import com.gazzel.sesameapp.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
// Flow imports
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- Updated UI State ---
// Reflects separate loading/success/error for metadata
sealed class ListDetailsUiState {
    object InitialLoading : ListDetailsUiState() // Loading metadata
    data class Success(
        val listMetadata: SesameList, // Holds name, description, etc. (NO places)
        // Place loading/error states handled by Paging library's LoadState
    ) : ListDetailsUiState()
    data class MetadataError(val message: String) : ListDetailsUiState() // Error fetching metadata
    object DeleteSuccess : ListDetailsUiState() // State for successful list deletion
}


@OptIn(ExperimentalCoroutinesApi::class) // For stateIn/flatMapLatest if used later
@HiltViewModel
class ListDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getListDetailsUseCase: GetListDetailsUseCase, // Fetches metadata
    private val getPlacesInListPaginatedUseCase: GetPlacesInListPaginatedUseCase, // <<< Injected
    private val updateListUseCase: UpdateListUseCase,
    private val updatePlaceNotesUseCase: UpdatePlaceNotesUseCase,
    private val deleteListUseCase: DeleteListUseCase,
    private val deletePlaceItemUseCase: DeletePlaceItemUseCase
) : ViewModel() {

    val listId: String = savedStateHandle[Screen.ListDetail.ARG_LIST_ID] ?: ""

    // --- State for List Metadata ---
    private val _uiState = MutableStateFlow<ListDetailsUiState>(ListDetailsUiState.InitialLoading)
    val uiState: StateFlow<ListDetailsUiState> = _uiState.asStateFlow()

    // --- Flow for Paginated Places ---
    // Initialize only when listId is valid. Use stateIn for caching and sharing.
    val placesPagerFlow: StateFlow<PagingData<PlaceItem>> = flow {
        if (listId.isNotBlank()) {
            Log.d("ListDetailsVM", "Initializing places Pager Flow for list $listId")
            // Emit the flow from the use case and cache it
            emitAll(getPlacesInListPaginatedUseCase(listId).cachedIn(viewModelScope))
        } else {
            Log.w("ListDetailsVM", "Cannot initialize places Pager Flow: listId is blank.")
            emit(PagingData.empty()) // Emit empty if ID is invalid
        }
    }.stateIn( // Convert to StateFlow for easier observation and caching
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L), // Keep active 5s after UI stops observing
        initialValue = PagingData.empty() // Start with empty data
    )

    // --- SharedFlow for triggering UI actions (like pager refresh) ---
    private val _actionEvent = MutableSharedFlow<ListDetailAction>()
    val actionEvent: SharedFlow<ListDetailAction> = _actionEvent.asSharedFlow()

    // --- State for specific action errors (e.g., update/delete failures) ---
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()


    init {
        if (listId.isNotBlank()) {
            fetchListMetadata() // Fetch metadata on init
        } else {
            // Already handled by placesPagerFlow logic, but set metadata error too
            _uiState.value = ListDetailsUiState.MetadataError("List ID not provided.")
            Log.e("ListDetailsVM", "List ID is missing in SavedStateHandle")
        }
    }

    // Fetches only the list metadata (name, description, collaborators, etc.)
    private fun fetchListMetadata() {
        // Ensure we show loading only if not already in a success state
        if (_uiState.value !is ListDetailsUiState.Success) {
            _uiState.value = ListDetailsUiState.InitialLoading
        }
        viewModelScope.launch {
            Log.d("ListDetailsVM", "Fetching METADATA for list $listId")
            // This use case should now call the modified getListDetail API endpoint
            val result = getListDetailsUseCase(listId)

            result.onSuccess { listMetadata ->
                // Metadata fetched. Places will start loading via placesPagerFlow collection in UI.
                _uiState.value = ListDetailsUiState.Success(listMetadata)
                Log.d("ListDetailsVM", "Successfully fetched metadata for list $listId: ${listMetadata.title}")
            }.onError { exception ->
                _uiState.value = ListDetailsUiState.MetadataError(exception.message ?: "Failed to fetch list details")
                Log.e("ListDetailsVM", "Fetch metadata error: ${exception.message}", exception)
            }
        }
    }

    // Called by UI (e.g., pull-to-refresh)
    fun refresh() {
        Log.d("ListDetailsVM", "refresh called.")
        fetchListMetadata() // Re-fetch metadata
        viewModelScope.launch {
            _actionEvent.emit(ListDetailAction.RefreshPlaces) // Signal UI to refresh Pager
        }
    }


    fun updateListPrivacy() {
        val currentSuccessState = _uiState.value as? ListDetailsUiState.Success ?: return
        val currentMetadata = currentSuccessState.listMetadata

        viewModelScope.launch {
            // Optionally show a subtle loading state if needed
            val updatedMetadata = currentMetadata.copy(isPublic = !currentMetadata.isPublic)
            val result = updateListUseCase(updatedMetadata)

            result.onSuccess { returnedMetadata ->
                _uiState.value = ListDetailsUiState.Success(returnedMetadata) // Update metadata state
                _actionError.value = null // Clear previous errors
                Log.d("ListDetailsVM", "Successfully updated privacy")
            }.onError { exception ->
                _actionError.value = "Failed to update privacy: ${exception.message}"
                Log.e("ListDetailsVM", "UpdatePrivacy error: ${exception.message}", exception)
            }
        }
    }

    fun updateListName(newName: String) {
        val currentSuccessState = _uiState.value as? ListDetailsUiState.Success ?: return
        val currentMetadata = currentSuccessState.listMetadata
        if (newName.isBlank() || newName == currentMetadata.title) return

        viewModelScope.launch {
            val updatedMetadata = currentMetadata.copy(title = newName)
            val result = updateListUseCase(updatedMetadata)

            result.onSuccess { returnedMetadata ->
                _uiState.value = ListDetailsUiState.Success(returnedMetadata)
                _actionError.value = null
                Log.d("ListDetailsVM", "Successfully updated name")
            }.onError { exception ->
                _actionError.value = "Failed to update name: ${exception.message}"
                Log.e("ListDetailsVM", "UpdateName error: ${exception.message}", exception)
            }
        }
    }

    fun updatePlaceNotes(placeId: String, note: String?) {
        if (placeId.isBlank()) { _actionError.value = "Invalid Place ID"; return }

        viewModelScope.launch {
            // Loading state handled by Paging library during refresh
            val result = updatePlaceNotesUseCase(placeId, note)

            result.onSuccess { updatedPlaceItem ->
                Log.d("ListDetailsVM", "Update notes success for $placeId. Signaling refresh.")
                _actionError.value = null
                _actionEvent.emit(ListDetailAction.RefreshPlaces) // Signal UI to refresh Pager
            }.onError { exception ->
                _actionError.value = "Failed to update notes: ${exception.message}"
                Log.e("ListDetailsVM", "UpdateNotes error: ${exception.message}", exception)
            }
        }
    }

    fun deleteList() {
        if (listId.isBlank()) { _actionError.value = "Invalid List ID"; return }

        viewModelScope.launch {
            // Optionally set a specific deleting state in _uiState if needed
            val result = deleteListUseCase(listId)

            result.onSuccess {
                _uiState.value = ListDetailsUiState.DeleteSuccess // Signal navigation
                Log.d("ListDetailsVM", "List $listId deleted successfully.")
            }.onError { exception ->
                _actionError.value = "Failed to delete list: ${exception.message}"
                Log.e("ListDetailsVM", "DeleteList error: ${exception.message}", exception)
                // Maybe revert _uiState if it was changed to a deleting state
            }
        }
    }

    fun deletePlace(placeId: String) {
        if (placeId.isBlank()) { _actionError.value = "Invalid Place ID"; return }

        viewModelScope.launch {
            val result = deletePlaceItemUseCase(placeId)

            result.onSuccess {
                Log.d("ListDetailsVM", "Delete place $placeId success. Signaling refresh.")
                _actionError.value = null
                _actionEvent.emit(ListDetailAction.RefreshPlaces) // Signal UI to refresh Pager
            }.onError { exception ->
                _actionError.value = "Failed to delete place: ${exception.message}"
                Log.e("ListDetailsVM", "DeletePlace error: ${exception.message}", exception)
            }
        }
    }

    // Function for UI to clear the action error after displaying it
    fun clearActionError() {
        _actionError.value = null
    }

} // End ViewModel

// Sealed class for actions/events to trigger from VM -> UI
sealed class ListDetailAction {
    object RefreshPlaces : ListDetailAction() // Signal to call lazyPagingItems.refresh()
}