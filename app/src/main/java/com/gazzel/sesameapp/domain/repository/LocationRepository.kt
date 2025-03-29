package com.gazzel.sesameapp.domain.repository

import com.gazzel.sesameapp.domain.util.Resource
import com.google.android.gms.maps.model.LatLng

interface LocationRepository {
    suspend fun getCurrentLocation(): Resource<LatLng>
    suspend fun hasLocationPermission(): Boolean
    suspend fun requestLocationPermission(): Boolean
} 