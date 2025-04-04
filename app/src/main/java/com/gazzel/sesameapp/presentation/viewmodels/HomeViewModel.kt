package com.gazzel.sesameapp.presentation.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gazzel.sesameapp.domain.model.PlaceItem
import com.gazzel.sesameapp.domain.repository.LocationRepository
import com.gazzel.sesameapp.domain.repository.PlaceRepository
import com.gazzel.sesameapp.domain.util.Resource
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

sealed class HomeUiState {
    object Initial : HomeUiState()
    object Loading : HomeUiState()
    data class Success(
        val location: LatLng,
        val places: List<PlaceItem>,
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
     * Called when location permission is granted.
     * Attempts to retrieve current location and then fetch places.
     */
    fun onLocationPermissionGranted() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val locationResult = locationRepository.getCurrentLocation()
                // Pass locationResult.data directly (which is LatLng?)
                if (locationResult is Resource.Success) {
                    handleLocationSuccess(locationResult.data) // Pass LatLng?
                } else if (locationResult is Resource.Error) {
                    // Pass message or default
                    _uiState.value = HomeUiState.Error(locationResult.message ?: "Unknown location error")
                } else if (locationResult is Resource.Loading) {
                    // State is already Loading, maybe do nothing or log
                    Log.d("HomeViewModel", "Location is loading...")
                }
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }

    private suspend fun handleLocationSuccess(location: LatLng?) {
        if (location == null) { // <<< ADD null check
            _uiState.value = HomeUiState.Error("Failed to get valid location data.")
            return
        }
        when (val placesResult = placeRepository.getPlaces()) {
            is Resource.Success -> {
                // Safely unwrap null to an empty list
                val places = placesResult.data ?: emptyList()

                val filteredPlaces = places.filter { place ->
                    calculateDistance(
                        location.latitude, location.longitude,
                        place.latitude, place.longitude
                    ) <= DEFAULT_RADIUS_KM
                }

                _uiState.value = HomeUiState.Success(
                    location = location,
                    places = places,
                    filteredPlaces = filteredPlaces
                )
            }
            is Resource.Error -> {
                // Provide a fallback error message
                _uiState.value = HomeUiState.Error(
                    placesResult.message ?: "Unable to load places."
                )
            }
            is Resource.Loading -> {
                // Places are still loading. The overall state might already be HomeUiState.Loading.
                // You might log this or potentially keep the UI in a loading state
                // if it wasn't already set. For now, just handling the case is enough.
                Log.d("HomeViewModel", "Location success, but places are still loading...")
                // Optionally ensure UI state reflects loading if it wasn't set before:
                // if (_uiState.value !is HomeUiState.Loading) {
                //     _uiState.value = HomeUiState.Loading
                // }
            }
        }
    }

    /**
     * Calculates the distance (in kilometers) between two lat-long points
     * using the Haversine formula.
     */
    private fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }
}
