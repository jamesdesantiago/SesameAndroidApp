// presentation/screens/search/SearchPlacesViewModel.kt
package com.gazzel.sesameapp.presentation.screens.search

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gazzel.sesameapp.BuildConfig
import com.gazzel.sesameapp.data.manager.PlaceUpdateManager
// Import PlaceRepository instead of ListApiService
import com.gazzel.sesameapp.domain.repository.PlaceRepository // <<< CHANGE
import com.gazzel.sesameapp.data.service.GooglePlacesService
import com.gazzel.sesameapp.data.service.AutocompleteRequest
// Import Google API response DTOs
import com.gazzel.sesameapp.data.service.PlaceDetailsResponse
import com.gazzel.sesameapp.data.service.PlacePrediction
// Import Domain Model
import com.gazzel.sesameapp.domain.model.PlaceItem // <<< ADD
// Import Result and extensions
import com.gazzel.sesameapp.domain.util.Result // <<< ADD
import com.gazzel.sesameapp.domain.util.onError // <<< ADD
import com.gazzel.sesameapp.domain.util.onSuccess // <<< ADD
// Import TokenProvider (Repository handles token now, but keep if needed for other reasons)
// import com.gazzel.sesameapp.domain.auth.TokenProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

// UiState definition remains the same
sealed class SearchPlacesUiState {
    object Idle : SearchPlacesUiState()
    data class Searching(val query: String) : SearchPlacesUiState()
    data class SuggestionsLoaded(val query: String, val suggestions: List<PlacePrediction>) : SearchPlacesUiState()
    data class LoadingDetails(val placeName: String?) : SearchPlacesUiState()
    data class DetailsLoaded(val placeDetails: PlaceDetailsResponse) : SearchPlacesUiState()
    // Keep PlaceDetailsResponse here for the overlay, even though we map to PlaceItem for repo call
    data class AddingPlace(val placeDetails: PlaceDetailsResponse) : SearchPlacesUiState()
    object PlaceAdded : SearchPlacesUiState()
    data class Error(val message: String) : SearchPlacesUiState()
}

// OverlayStep enum remains the same
enum class OverlayStep { Hidden, ShowPlaceDetails, AskVisitOrNot, AskRating }

@HiltViewModel
class SearchPlacesViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val googlePlacesService: GooglePlacesService,
    private val placeRepository: PlaceRepository, // <<< CHANGE: Inject PlaceRepository
    // private val tokenProvider: TokenProvider // <<< REMOVE (Repository handles token)
) : ViewModel() {

    // Key should match NavGraph argument name
    private val listId: String = savedStateHandle.get<String>(com.gazzel.sesameapp.presentation.navigation.Screen.SearchPlaces.ARG_LIST_ID) ?: ""

    private val _uiState = MutableStateFlow<SearchPlacesUiState>(SearchPlacesUiState.Idle)
    val uiState: StateFlow<SearchPlacesUiState> = _uiState.asStateFlow()

    private val _overlayStep = MutableStateFlow(OverlayStep.Hidden)
    val overlayStep: StateFlow<OverlayStep> = _overlayStep.asStateFlow()

    // Internal state to hold data during the multi-step process
    private var _selectedPlaceDetails: PlaceDetailsResponse? = null
    private var _visitStatus: String? = null
    private var _userRating: String? = null

    private var autocompleteJob: Job? = null
    private val sessionToken = UUID.randomUUID().toString()
    private val apiKey = BuildConfig.MAPS_API_KEY

    init {
        if (listId.isEmpty()) {
            _uiState.value = SearchPlacesUiState.Error("List ID not provided.")
            Log.e("SearchPlacesVM", "List ID is missing in SavedStateHandle")
        }
    }

    fun updateQuery(newQuery: String) {
        // --- Logic remains the same ---
        val currentState = _uiState.value
        val currentSuggestions = if (currentState is SearchPlacesUiState.SuggestionsLoaded) currentState.suggestions else emptyList()

        _uiState.value = if(newQuery.isBlank()) SearchPlacesUiState.Idle else SearchPlacesUiState.Searching(newQuery)
        autocompleteJob?.cancel()

        if (newQuery.length < 3) {
            if (_uiState.value !is SearchPlacesUiState.Idle) {
                _uiState.value = SearchPlacesUiState.Idle
            }
            return
        }

        autocompleteJob = viewModelScope.launch {
            delay(400)
            fetchAutocompleteSuggestions(newQuery)
        }
    }

    private suspend fun fetchAutocompleteSuggestions(query: String) {
        // --- Logic remains the same ---
        Log.d("SearchPlacesVM", "Fetching suggestions for: $query")
        try {
            val request = AutocompleteRequest(input = query, sessionToken = sessionToken)
            val response = googlePlacesService.getAutocompleteSuggestions(request, apiKey)

            if (response.isSuccessful) {
                val suggestions = response.body()?.suggestions?.map { it.placePrediction } ?: emptyList()
                _uiState.value = SearchPlacesUiState.SuggestionsLoaded(query, suggestions)
                Log.d("SearchPlacesVM", "Autocomplete success: ${suggestions.size} results")
            } else {
                val errorMsg = "Autocomplete failed: ${response.code()} ${response.message()}"
                _uiState.value = SearchPlacesUiState.Error(errorMsg)
                Log.e("SearchPlacesVM", "Autocomplete error: ${response.code()} - ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            val errorMsg = "Autocomplete error: ${e.localizedMessage ?: "Unknown error"}"
            _uiState.value = SearchPlacesUiState.Error(errorMsg)
            Log.e("SearchPlacesVM", "Autocomplete exception", e)
        }
    }

    fun selectPlace(prediction: PlacePrediction) {
        // --- Logic remains the same ---
        viewModelScope.launch {
            _uiState.value = SearchPlacesUiState.LoadingDetails(prediction.text.text)
            _overlayStep.value = OverlayStep.Hidden
            try {
                val fields = "id,displayName,formattedAddress,location,rating"
                val response = googlePlacesService.getPlaceDetails(prediction.placeId, apiKey, fields)
                if (response.isSuccessful && response.body() != null) {
                    _selectedPlaceDetails = response.body()
                    _uiState.value = SearchPlacesUiState.DetailsLoaded(_selectedPlaceDetails!!)
                    // Trigger overlay via VM state now
                    proceedToVisitStatusStep() // Or directly trigger overlay step 1
                    Log.d("SearchPlacesVM", "Details loaded, proceeding to overlay step.")
                } else {
                    val errorMsg = "Failed to get place details: ${response.code()} ${response.message()}"
                    _uiState.value = SearchPlacesUiState.Error(errorMsg)
                    Log.e("SearchPlacesVM", "GetDetails error: ${response.code()} - ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                val errorMsg = "Details error: ${e.localizedMessage ?: "Unknown error"}"
                _uiState.value = SearchPlacesUiState.Error(errorMsg)
                Log.e("SearchPlacesVM", "GetDetails exception", e)
            }
        }
    }

    // Functions controlling overlay steps remain mostly the same
    fun proceedToVisitStatusStep() {
        if (_uiState.value is SearchPlacesUiState.DetailsLoaded) {
            _overlayStep.value = OverlayStep.AskVisitOrNot
        } else {
            Log.w("SearchPlacesVM", "Cannot proceed to visit status, not in DetailsLoaded state.")
        }
    }

    fun setVisitStatusAndProceed(status: String?) {
        if (_overlayStep.value == OverlayStep.AskVisitOrNot) {
            _visitStatus = status
            _overlayStep.value = OverlayStep.AskRating
        } else {
            Log.w("SearchPlacesVM", "Cannot set visit status, not in AskVisitOrNot step.")
        }
    }

    fun setRatingAndAddPlace(rating: String?) {
        if (_overlayStep.value == OverlayStep.AskRating && _selectedPlaceDetails != null) {
            _userRating = rating
            // Trigger the refactored add place logic
            addPlaceToListWithRepository() // <<< CALL REFACTORED METHOD
        } else {
            Log.w("SearchPlacesVM", "Cannot add place, invalid state. Overlay: ${_overlayStep.value}, Details: ${_selectedPlaceDetails != null}")
        }
    }

    // --- REFACTORED: Add Place Logic ---
    private fun addPlaceToListWithRepository() {
        val placeDetails = _selectedPlaceDetails
        if (placeDetails == null) {
            _uiState.value = SearchPlacesUiState.Error("Cannot add place: Details are missing.")
            resetOverlayState()
            return
        }
        if (listId.isEmpty()) {
            _uiState.value = SearchPlacesUiState.Error("Cannot add place: List ID is missing.")
            resetOverlayState()
            return
        }

        viewModelScope.launch {
            _uiState.value = SearchPlacesUiState.AddingPlace(placeDetails) // Show loading specifically for adding

            try {
                // 1. Map Google Place Details + User Input -> PlaceItem (Domain Model)
                val placeItemToAdd = PlaceItem(
                    id = placeDetails.id, // Use Google Place ID as the ID for the item
                    name = placeDetails.displayName.text,
                    address = placeDetails.formattedAddress,
                    latitude = placeDetails.location.latitude,
                    longitude = placeDetails.location.longitude,
                    description = "", // Google details API didn't request description, add if needed
                    listId = listId, // Assign the target list ID
                    notes = null, // Notes aren't collected here
                    rating = _userRating, // User's rating (String?)
                    visitStatus = _visitStatus // User's visit status (String?)
                )
                Log.d("SearchPlacesVM", "Mapped to PlaceItem: $placeItemToAdd")

                // 2. Call Repository
                Log.d("SearchPlacesVM", "Calling placeRepository.addPlace...")
                val result: Result<PlaceItem> = placeRepository.addPlace(placeItemToAdd)

                // 3. Handle Repository Result
                result.onSuccess { addedPlaceItem ->
                    Log.d("SearchPlacesVM", "Place added successfully via repository: ${addedPlaceItem.id}")
                    PlaceUpdateManager.notifyPlaceAdded() // Notify other parts of the app
                    _uiState.value = SearchPlacesUiState.PlaceAdded // Signal success
                    resetOverlayState() // Reset internal state
                }.onError { exception ->
                    Log.e("SearchPlacesVM", "Failed to add place via repository", exception)
                    _uiState.value = SearchPlacesUiState.Error(exception.message ?: "Failed to add place")
                    resetOverlayState() // Reset internal state on error
                }

            } catch (e: Exception) {
                Log.e("SearchPlacesVM", "Exception during addPlaceToListWithRepository", e)
                _uiState.value = SearchPlacesUiState.Error("An unexpected error occurred while adding the place.")
                resetOverlayState() // Reset internal state on exception
            }
        }
    }

    // Reset and Error handling functions remain the same
    fun resetOverlayState() {
        _overlayStep.value = OverlayStep.Hidden
        _selectedPlaceDetails = null
        _visitStatus = null
        _userRating = null
        val currentUiState = _uiState.value
        // Avoid resetting if searching or showing suggestions
        if (currentUiState !is SearchPlacesUiState.Searching &&
            currentUiState !is SearchPlacesUiState.SuggestionsLoaded &&
            currentUiState !is SearchPlacesUiState.Idle) {
            _uiState.value = SearchPlacesUiState.Idle
        }
    }

    fun clearError() {
        val currentState = _uiState.value
        if (currentState is SearchPlacesUiState.Error) {
            _uiState.value = SearchPlacesUiState.Idle // Simple reset to Idle
        }
    }
}