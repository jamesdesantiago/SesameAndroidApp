// presentation/viewmodels/HomeViewModel.kt
package com.gazzel.sesameapp.presentation.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gazzel.sesameapp.domain.model.PlaceItem
import com.gazzel.sesameapp.domain.repository.LocationRepository
import com.gazzel.sesameapp.domain.repository.PlaceRepository
// Import Result and extensions
import com.gazzel.sesameapp.domain.util.Result // <<< ADD: Import Result
import com.gazzel.sesameapp.domain.util.onError // <<< ADD: Import onError
import com.gazzel.sesameapp.domain.util.onSuccess // <<< ADD: Import onSuccess
import com.gazzel.sesameapp.domain.util.map // Optional: If mapping Result data needed
// Import LatLng
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

// UiState definition remains the same
sealed class HomeUiState {
    object Initial : HomeUiState()
    object Loading : HomeUiState()
    data class Success(
        val location: LatLng,
        val places: List<PlaceItem>, // Assuming PlaceItem is the chosen model
        val filteredPlaces: List<PlaceItem>
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

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Initial)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * Called when location permission is granted by the UI.
     * Attempts to retrieve current location and then fetch places.
     */
    fun onLocationPermissionGranted() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading // <<< SET Loading state BEFORE the call
            Log.d("HomeViewModel", "Location permission granted, fetching location...")

            // Call the repository method which now returns Result<LatLng>
            val locationResult: Result<LatLng> = locationRepository.getCurrentLocation()

            // Handle the Result using onSuccess/onError extensions
            locationResult.onSuccess { latLng ->
                // Location fetched successfully
                Log.d("HomeViewModel", "Location success: $latLng. Fetching places...")
                // Now fetch places using the successful location
                fetchPlacesAndFilter(latLng) // Pass LatLng directly
            }.onError { exception ->
                // Location fetch failed
                Log.e("HomeViewModel", "Failed to get location: ${exception.message}", exception)
                // Update UI state with the error message from the exception
                _uiState.value = HomeUiState.Error(exception.message ?: "Failed to get location")
            }
        }
    }

    // Renamed handleLocationSuccess to fetchPlacesAndFilter for clarity
    private suspend fun fetchPlacesAndFilter(location: LatLng) {
        // Keep Loading state or ensure it's set if coming from another flow
        if (_uiState.value !is HomeUiState.Loading) {
            _uiState.value = HomeUiState.Loading
        }

        // Call place repository which should also return Result<List<PlaceItem>>
        // Assuming placeRepository.getPlaces() is updated or already returns Result
        val placesResult: Result<List<PlaceItem>> = placeRepository.getPlaces()

        placesResult.onSuccess { places ->
            // Place fetch success
            Log.d("HomeViewModel", "Places fetched successfully: ${places.size} items.")
            val filteredPlaces = places.filter { place ->
                calculateDistance(
                    location.latitude, location.longitude,
                    place.latitude, place.longitude
                ) <= DEFAULT_RADIUS_KM
            }
            Log.d("HomeViewModel", "Filtered places: ${filteredPlaces.size} items within radius.")
            // Update UI state to Success with location, all places, and filtered places
            _uiState.value = HomeUiState.Success(
                location = location,
                places = places, // Keep all fetched places if needed elsewhere
                filteredPlaces = filteredPlaces // Pass filtered list for the map/UI
            )
        }.onError { exception ->
            // Place fetch failed
            Log.e("HomeViewModel", "Failed to get places: ${exception.message}", exception)
            // Update UI state with the error
            _uiState.value = HomeUiState.Error(exception.message ?: "Failed to load places")
        }
    }

    /**
     * Calculates the distance (in kilometers) between two lat-long points
     * using the Haversine formula. (Keep as is)
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    // Optional: Add a refresh function if needed
    fun refreshData() {
        Log.d("HomeViewModel", "Refresh triggered.")
        // Re-check permission and fetch location/places
        // This assumes the UI layer will ensure permission is still granted before calling refresh
        // or handle the permission request flow again if needed.
        onLocationPermissionGranted()
    }
}