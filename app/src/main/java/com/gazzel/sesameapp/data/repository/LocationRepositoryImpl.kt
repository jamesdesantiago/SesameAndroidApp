package com.gazzel.sesameapp.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.gazzel.sesameapp.domain.repository.LocationRepository
import com.gazzel.sesameapp.domain.util.Resource
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.android.gms.location.LocationServices
import android.location.Location


class LocationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient
) : LocationRepository {

    override suspend fun getCurrentLocation(): Resource<LatLng> {
        return try {
            if (!hasLocationPermission()) {
                return Resource.Error("Location permission not granted")
            }

            val location = fusedLocationClient.lastLocation.await()
            if (location != null) {
                Resource.Success(LatLng(location.latitude, location.longitude))
            } else {
                Resource.Error("Unable to get current location")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get location")
        }
    }

    override suspend fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun requestLocationPermission(): Boolean {
        // This should be handled by the UI layer using ActivityResultContracts
        return false
    }
}
