package com.gazzel.sesameapp.domain.repository

import com.gazzel.sesameapp.domain.model.Place
import com.gazzel.sesameapp.domain.model.PlaceItem
import com.gazzel.sesameapp.domain.util.Resource
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.Flow

interface PlaceRepository {
    fun searchPlaces(query: String, location: LatLng, radius: Int): Flow<List<Place>>
    fun getPlaceById(placeId: String): Flow<Place?>
    suspend fun getPlaceDetails(placeId: String): Result<Place>
    suspend fun savePlace(place: Place): Result<String>
    suspend fun updatePlace(place: Place): Result<Unit>
    suspend fun deletePlace(placeId: String): Result<Unit>
    suspend fun getNearbyPlaces(location: LatLng, radius: Int, type: String? = null): Result<List<Place>>
    suspend fun getPlaces(): Resource<List<PlaceItem>>
    suspend fun getPlacesByListId(listId: String): Resource<List<PlaceItem>>
    suspend fun addPlace(place: PlaceItem): Resource<PlaceItem>
    suspend fun updatePlace(place: PlaceItem): Resource<PlaceItem>
    suspend fun deletePlace(placeId: String): Resource<Unit>
} 