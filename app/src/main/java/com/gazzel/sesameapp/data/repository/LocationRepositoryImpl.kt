// data/repository/LocationRepositoryImpl.kt
package com.gazzel.sesameapp.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
// Import domain interface and Result/Exceptions
import com.gazzel.sesameapp.domain.repository.LocationRepository
import com.gazzel.sesameapp.domain.util.Result // <<< CHANGE: Import Result
import com.gazzel.sesameapp.domain.exception.AppException // <<< ADD: Import Base Exception
import com.gazzel.sesameapp.domain.exception.LocationException // <<< ADD: Import Specific Exception
// Import LatLng and FusedLocationProviderClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import android.util.Log // For logging

class LocationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient
) : LocationRepository {

    override suspend fun getCurrentLocation(): Result<LatLng> { // <<< CHANGE Return Type
        return try {
            if (!hasLocationPermission()) {
                Log.w("LocationRepoImpl", "Location permission not granted.")
                // Return Result.Error with a specific LocationException
                return Result.error(LocationException("Location permission not granted")) // <<< CHANGE: Use Result.error
            }

            // Await might throw SecurityException if permission revoked after check
            @SuppressLint("MissingPermission") // Already checked above
            val location = fusedLocationClient.lastLocation.await()

            if (location != null) {
                Log.d("LocationRepoImpl", "Location retrieved: ${location.latitude}, ${location.longitude}")
                // Return Result.Success with the LatLng data
                Result.success(LatLng(location.latitude, location.longitude)) // <<< CHANGE: Use Result.success
            } else {
                Log.w("LocationRepoImpl", "FusedLocationProviderClient returned null location.")
                // Return Result.Error indicating location data wasn't available
                Result.error(LocationException("Unable to get current location (FusedLocationProvider returned null)")) // <<< CHANGE: Use Result.error
            }
        } catch (e: SecurityException) {
            // Catch specific permission-related exceptions during await()
            Log.e("LocationRepoImpl", "SecurityException getting location", e)
            Result.error(LocationException("Location permission failed or revoked", e)) // <<< CHANGE: Use Result.error
        } catch (e: Exception) {
            // Catch any other exceptions
            Log.e("LocationRepoImpl", "Generic exception getting location", e)
            Result.error(LocationException("Failed to get location: ${e.message}", e)) // <<< CHANGE: Use Result.error
        }
    }

    override suspend fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // As noted before, UI layer typically handles permission requests.
    // This implementation might remain simple or be removed.
    override suspend fun requestLocationPermission(): Boolean {
        // This repository implementation cannot directly trigger the UI permission request flow.
        Log.w("LocationRepoImpl", "requestLocationPermission called in Repository - UI should handle this.")
        // Returning current status instead.
        return hasLocationPermission()
    }
}