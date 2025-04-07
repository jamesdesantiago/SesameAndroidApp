// Create package: presentation/screens/search
// Create file: presentation/screens/search/SearchPlacesViewModel.kt
package com.gazzel.sesameapp.presentation.screens.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gazzel.sesameapp.BuildConfig // Use BuildConfig for API Key
import com.gazzel.sesameapp.data.manager.PlaceUpdateManager // For notifying list changes
// Import consolidated ListApiService (adjust if name differs)
import com.gazzel.sesameapp.data.service.ListApiService
import com.gazzel.sesameapp.data.service.GooglePlacesService
import com.gazzel.sesameapp.data.service.AutocompleteRequest
import com.gazzel.sesameapp.data.service.PlaceCreate // DTO for adding place
import com.gazzel.sesameapp.data.service.PlaceDetailsResponse
import com.gazzel.sesameapp.data.service.PlacePrediction
import com.gazzel.sesameapp.domain.auth.TokenProvider // Import TokenProvider
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
import android.util.Log

// Define the different states the UI can be in
sealed class SearchPlacesUiState {
    object Idle : SearchPlacesUiState() // Initial state or after completion/error reset
    data class Searching(val query: String) : SearchPlacesUiState()
    data class SuggestionsLoaded(val query: String, val suggestions: List<PlacePrediction>) : SearchPlacesUiState()
    data class LoadingDetails(val placeName: String?) : SearchPlacesUiState() // Loading place details
    data class DetailsLoaded(val placeDetails: PlaceDetailsResponse) : SearchPlacesUiState() // Ready for overlay step 1
    data class AddingPlace(val placeDetails: PlaceDetailsResponse) : SearchPlacesUiState() // When Add Place API call is in progress
    object PlaceAdded : SearchPlacesUiState() // Place successfully added
    data class Error(val message: String) : SearchPlacesUiState()
}

// Enum for overlay steps (copied from old screen)
enum class OverlayStep { Hidden, ShowPlaceDetails, AskVisitOrNot, AskRating }

@HiltViewModel
class SearchPlacesViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val googlePlacesService: GooglePlacesService,
    private val listService: ListApiService, // Use consolidated service
    private val tokenProvider: TokenProvider
) : ViewModel() {

    private val listId: String = savedStateHandle.get<String>("listId") ?: ""

    private val _uiState = MutableStateFlow<SearchPlacesUiState>(SearchPlacesUiState.Idle)
    val uiState: StateFlow<SearchPlacesUiState> = _uiState.asStateFlow()

    // Internal state for the multi-step process
    private val _overlayStep = MutableStateFlow(OverlayStep.Hidden)
    val overlayStep: StateFlow<OverlayStep> = _overlayStep.asStateFlow()

    private var _selectedPlaceDetails: PlaceDetailsResponse? = null // Store selected details internally
    private var _visitStatus: String? = null
    private var _userRating: String? = null

    private var autocompleteJob: Job? = null
    private val sessionToken = UUID.randomUUID().toString() // Generate unique session token once
    private val apiKey = BuildConfig.MAPS_API_KEY // Get API key from BuildConfig

    init {
        if (listId.isEmpty()) {
            _uiState.value = SearchPlacesUiState.Error("List ID not provided.")
            Log.e("SearchPlacesVM", "List ID is missing.")
        }
    }

    fun updateQuery(newQuery: String) {
        // Update the state immediately to reflect typing
        val currentState = _uiState.value
        // Preserve suggestions if query changes slightly while suggestions are shown
        val currentSuggestions = if (currentState is SearchPlacesUiState.SuggestionsLoaded) currentState.suggestions else emptyList()

        _uiState.value = if(newQuery.isBlank()) SearchPlacesUiState.Idle else SearchPlacesUiState.Searching(newQuery)

        autocompleteJob?.cancel() // Cancel previous job

        if (newQuery.length < 3) {
            // If query becomes too short, revert to Idle (clears suggestions)
            if (_uiState.value !is SearchPlacesUiState.Idle) {
                _uiState.value = SearchPlacesUiState.Idle
            }
            return
        }

        autocompleteJob = viewModelScope.launch {
            delay(400) // Debounce
            fetchAutocompleteSuggestions(newQuery)
        }
    }

    private suspend fun fetchAutocompleteSuggestions(query: String) {
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
        viewModelScope.launch {
            _uiState.value = SearchPlacesUiState.LoadingDetails(prediction.text.text)
            _overlayStep.value = OverlayStep.Hidden // Ensure overlay is hidden initially
            try {
                val fields = "id,displayName,formattedAddress,location,rating"
                val response = googlePlacesService.getPlaceDetails(prediction.placeId, apiKey, fields)
                if (response.isSuccessful && response.body() != null) {
                    _selectedPlaceDetails = response.body()
                    _uiState.value = SearchPlacesUiState.DetailsLoaded(_selectedPlaceDetails!!)
                    _overlayStep.value = OverlayStep.ShowPlaceDetails // Trigger overlay
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

    fun proceedToVisitStatusStep() {
        if (_uiState.value is SearchPlacesUiState.DetailsLoaded) {
            _overlayStep.value = OverlayStep.AskVisitOrNot
        }
    }

    fun setVisitStatusAndProceed(status: String?) {
        if (_overlayStep.value == OverlayStep.AskVisitOrNot) {
            _visitStatus = status
            _overlayStep.value = OverlayStep.AskRating
        }
    }

    fun setRatingAndAddPlace(rating: String?) {
        if (_overlayStep.value == OverlayStep.AskRating && _selectedPlaceDetails != null) {
            _userRating = rating
            addPlaceToList()
        } else {
            Log.w("SearchPlacesVM", "Cannot add place, invalid state. Overlay: ${_overlayStep.value}, Details: ${_selectedPlaceDetails != null}")
        }
    }

    private fun addPlaceToList() {
        val placeToAdd = _selectedPlaceDetails ?: return
        if (listId.isEmpty()) {
            _uiState.value = SearchPlacesUiState.Error("Cannot add place: List ID is missing.")
            resetOverlayState()
            return
        }

        viewModelScope.launch {
            _uiState.value = SearchPlacesUiState.AddingPlace(placeToAdd) // Show loading specifically for adding
            val token = tokenProvider.getToken()
            if (token == null) {
                _uiState.value = SearchPlacesUiState.Error("Authentication error.")
                resetOverlayState()
                return@launch
            }

            try {
                // Create the DTO to send to your backend
                val placeCreateDto = PlaceCreate(
                    placeId = placeToAdd.id,
                    name = placeToAdd.displayName.text,
                    address = placeToAdd.formattedAddress,
                    latitude = placeToAdd.location.latitude,
                    longitude = placeToAdd.location.longitude,
                    // Pass the rating and visitStatus collected from the user
                    rating = _userRating,
                    // visitStatus = _visitStatus // Add visitStatus to your PlaceCreate DTO if your API expects it
                )

                // Ensure service method and parameters match your consolidated ListApiService
                val response = listService.addPlace( // Adjust method name if needed
                    listId = listId,
                    place = placeCreateDto, // Pass the DTO
                    token = "Bearer $token" // Adjust parameter name if needed
                )

                if (response.isSuccessful || response.code() == 204) { // Allow 204 No Content
                    Log.d("SearchPlacesVM", "Place added successfully to list $listId")
                    PlaceUpdateManager.notifyPlaceAdded() // Notify other parts of the app
                    _uiState.value = SearchPlacesUiState.PlaceAdded // Signal success
                    resetOverlayState() // Reset internal state
                } else {
                    val errorMsg = "Failed to add place: ${response.code()} - ${response.errorBody()?.string()}"
                    _uiState.value = SearchPlacesUiState.Error(errorMsg)
                    Log.e("SearchPlacesVM", errorMsg)
                    resetOverlayState() // Reset internal state on error
                }
            } catch (e: Exception) {
                val errorMsg = "Exception adding place: ${e.message}"
                _uiState.value = SearchPlacesUiState.Error(errorMsg)
                Log.e("SearchPlacesVM", errorMsg, e)
                resetOverlayState() // Reset internal state on exception
            }
        }
    }

    fun resetOverlayState() {
        _overlayStep.value = OverlayStep.Hidden
        _selectedPlaceDetails = null
        _visitStatus = null
        _userRating = null
        // Consider resetting uiState to Idle or SuggestionsLoaded based on context
        // If we reset overlay, probably go back to Idle or show suggestions if query exists
        val currentUiState = _uiState.value
        if (currentUiState is SearchPlacesUiState.SuggestionsLoaded) {
            // Keep suggestions if they were present
        } else if (currentUiState !is SearchPlacesUiState.Idle && currentUiState !is SearchPlacesUiState.Searching){
            _uiState.value = SearchPlacesUiState.Idle
        }
    }

    fun clearError() {
        // Revert to a non-error state, e.g., Idle or based on query
        val currentState = _uiState.value
        if (currentState is SearchPlacesUiState.Error) {
            // If there was a query before the error, maybe go back to Searching/SuggestionsLoaded?
            // For simplicity, just go back to Idle for now.
            _uiState.value = SearchPlacesUiState.Idle
        }
    }
}