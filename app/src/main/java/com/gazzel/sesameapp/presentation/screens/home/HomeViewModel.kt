package com.gazzel.sesameapp.presentation.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gazzel.sesameapp.domain.model.PlaceItem
import com.gazzel.sesameapp.domain.repository.LocationRepository
import com.gazzel.sesameapp.domain.repository.PlaceRepository
import com.gazzel.sesameapp.domain.util.Result
import com.gazzel.sesameapp.domain.util.onError
import com.gazzel.sesameapp.domain.util.onSuccess
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// --- Sealed class for UI State (Keep as is) ---
sealed class HomeUiState {
    object Initial : HomeUiState() // Might not be needed if starting in Loading
    object Loading : HomeUiState()
    data class Success(
        val location: LatLng,
        val places: List<PlaceItem>, // All places fetched
        val filteredPlaces: List<PlaceItem> // Places within radius
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {

    companion object {
        private const val DEFAULT_RADIUS_KM = 1.0
        private const val EARTH_RADIUS_KM = 6371
    }

    // Start in Loading state as we immediately try to fetch location/places
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Note: Consider triggering data loading from the UI via a LaunchedEffect
    // instead of automatically in init, especially if location permission
    // isn't guaranteed at ViewModel creation. For now, keeping init call.
    // init {
    //    checkPermissionAndLoadData() // Or a similar starting function
    // }

    /**
     * Called when location permission is granted by the UI.
     * Attempts to retrieve current location and then fetch places.
     */
    fun onLocationPermissionGranted() {
        // Can be called from UI after permission check/grant
        loadInitialData()
    }

    /**
     * Triggers the initial data load sequence: get location, then places.
     */
    private fun loadInitialData() {
        viewModelScope.launch {
            // Ensure loading state is set
            if (_uiState.value !is HomeUiState.Loading) {
                _uiState.value = HomeUiState.Loading
            }
            Log.d("HomeViewModel", "Permission likely granted, fetching location...")

            // Call repo method returning Result<LatLng>
            val locationResult: Result<LatLng> = locationRepository.getCurrentLocation()

            // Handle Result using extensions
            locationResult.onSuccess { latLng ->
                // Location fetched successfully
                Log.d("HomeViewModel", "Location success: $latLng. Fetching places...")
                // Now fetch places using the successful location
                fetchPlacesAndFilter(latLng) // Pass non-null LatLng
            }.onError { exception ->
                // Location fetch failed
                Log.e("HomeViewModel", "Failed to get location: ${exception.message}", exception)
                _uiState.value = HomeUiState.Error(exception.message ?: "Failed to get location")
            }
        }
    }

    /**
     * Fetches places from the repository and filters them based on the provided location.
     * Assumes the UI state is already Loading or will be set appropriately before calling.
     */
    private suspend fun fetchPlacesAndFilter(location: LatLng) {
        Log.d("HomeViewModel", "Fetching places...")
        // Call repo method returning Result<List<PlaceItem>>
        val placesResult: Result<List<PlaceItem>> = placeRepository.getPlaces()

        // Handle Result
        placesResult.onSuccess { places -> // places is non-null List<PlaceItem>
            Log.d("HomeViewModel", "Places fetched successfully: ${places.size} items.")
            val filteredPlaces = places.filter { place ->
                calculateDistance(
                    location.latitude, location.longitude,
                    place.latitude, place.longitude
                ) <= DEFAULT_RADIUS_KM
            }
            Log.d("HomeViewModel", "Filtered ${filteredPlaces.size} places within radius.")
            _uiState.value = HomeUiState.Success(
                location = location,
                places = places, // Keep all fetched places
                filteredPlaces = filteredPlaces // Filtered list for map
            )
        }.onError { exception ->
            Log.e("HomeViewModel", "Failed to get places: ${exception.message}", exception)
            // Set error state, potentially retaining last known location if available
            // For simplicity now, just set the error message.
            _uiState.value = HomeUiState.Error(exception.message ?: "Failed to load places")
        }
    }

    /**
     * Calculates the distance (in kilometers) between two lat-long points. (Keep as is)
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1);
        val dLon = Math.toRadians(lon2 - lon1);
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2);
        val c = 2 * atan2(sqrt(a), sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    // Public function for UI to trigger a refresh
    fun refreshData() {
        Log.d("HomeViewModel", "Refresh triggered.")
        // Consider re-checking permission here if it could have been revoked,
        // or rely on the UI to only enable refresh if permission is granted.
        loadInitialData() // Re-run the load sequence
    }
}