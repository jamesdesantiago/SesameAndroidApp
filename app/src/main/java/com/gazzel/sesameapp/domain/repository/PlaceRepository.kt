// domain/repository/PlaceRepository.kt
package com.gazzel.sesameapp.domain.repository

import com.gazzel.sesameapp.domain.model.Place
import com.gazzel.sesameapp.domain.model.PlaceItem
import com.gazzel.sesameapp.domain.util.Result // Use Result consistently
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.Flow

interface PlaceRepository {
    // Change return types back to Result where applicable
    suspend fun getPlaces(): Result<List<PlaceItem>>
    suspend fun getPlacesByListId(listId: String): Result<List<PlaceItem>>
    suspend fun addPlace(place: PlaceItem): Result<PlaceItem>
    suspend fun updatePlace(place: PlaceItem): Result<PlaceItem>
    suspend fun deletePlace(placeId: String): Result<Unit>

    // Keep Result for these as originally specified by error messages
    suspend fun updatePlace(place: Place): Result<Unit>
    suspend fun getPlaceDetails(placeId: String): Result<Place>
    suspend fun savePlace(place: Place): Result<String>
    suspend fun getNearbyPlaces(location: LatLng, radius: Int, type: String? = null): Result<List<Place>>

    // Flows remain Flows
    fun searchPlaces(query: String, location: LatLng, radius: Int): Flow<List<Place>>
    fun getPlaceById(placeId: String): Flow<PlaceItem?>
}