package com.gazzel.sesameapp.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gazzel.sesameapp.domain.model.PlaceItem
import com.gazzel.sesameapp.domain.repository.PlaceRepository
import com.gazzel.sesameapp.domain.repository.LocationRepository
import com.gazzel.sesameapp.domain.util.Resource
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Initial)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun onLocationPermissionGranted() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                when (val locationResult = locationRepository.getCurrentLocation()) {
                    is Resource.Success -> {
                        val location = locationResult.data
                        when (val placesResult = placeRepository.getPlaces()) {
                            is Resource.Success -> {
                                val places = placesResult.data
                                val filteredPlaces = places.filter { place ->
                                    calculateDistance(
                                        location.latitude, location.longitude,
                                        place.latitude, place.longitude
                                    ) <= 1.0 // 1km radius
                                }
                                _uiState.value = HomeUiState.Success(
                                    location = location,
                                    places = places,
                                    filteredPlaces = filteredPlaces
                                )
                            }
                            is Resource.Error -> {
                                _uiState.value = HomeUiState.Error(placesResult.message)
                            }
                        }
                    }
                    is Resource.Error -> {
                        _uiState.value = HomeUiState.Error(locationResult.message)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }

    private fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val r = 6371 // Earth's radius in kilometers
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
} 