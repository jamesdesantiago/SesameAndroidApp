// domain/repository/LocationRepository.kt
package com.gazzel.sesameapp.domain.repository

// Import Result and LatLng
import com.gazzel.sesameapp.domain.util.Result // <<< CHANGE: Import Result
import com.google.android.gms.maps.model.LatLng

interface LocationRepository {
    // Change return type from Resource<LatLng> to Result<LatLng>
    suspend fun getCurrentLocation(): Result<LatLng> // <<< CHANGE Return Type

    suspend fun hasLocationPermission(): Boolean

    // This method is typically handled by UI request launchers,
    // consider removing it from the repository interface unless it
    // performs some specific background logic related to permissions.
    // For now, we'll leave it but comment on its potential removal.
    suspend fun requestLocationPermission(): Boolean // Keep signature for now
}