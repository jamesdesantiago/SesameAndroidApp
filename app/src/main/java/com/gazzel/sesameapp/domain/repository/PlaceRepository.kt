// domain/repository/PlaceRepository.kt
package com.gazzel.sesameapp.domain.repository

// Import both models
import com.gazzel.sesameapp.domain.model.Place // For detailed operations
import com.gazzel.sesameapp.domain.model.PlaceItem // For list item operations
import com.gazzel.sesameapp.domain.util.Result
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.Flow

interface PlaceRepository {
    // --- Methods operating on PlaceItems (within lists) ---
    suspend fun getPlaces(): Result<List<PlaceItem>> // Get all place items across user's lists
    suspend fun getPlacesByListId(listId: String): Result<List<PlaceItem>> // Get items for a specific list
    suspend fun addPlace(place: PlaceItem): Result<PlaceItem> // Add a PlaceItem entry to a list
    suspend fun updatePlace(place: PlaceItem): Result<PlaceItem> // Update a PlaceItem entry in a list
    suspend fun deletePlace(placeId: String): Result<Unit> // Delete a PlaceItem entry by its ID (assumes ID is unique across lists or context implies listId)
    fun getPlaceById(placeId: String): Flow<PlaceItem?> // Observe a PlaceItem entry

    // --- Methods operating on detailed Place model (Potentially different data source/logic) ---
    // These might fetch directly from Google Places SDK or a different API endpoint
    suspend fun getPlaceDetails(placeId: String): Result<Place> // Get full details
    suspend fun searchPlaces(query: String, location: LatLng, radius: Int): Result<List<Place>> // Search returns detailed Places
    suspend fun getNearbyPlaces(location: LatLng, radius: Int, type: String? = null): Result<List<Place>> // Nearby returns detailed Places

    suspend fun getPlaceItemById(placeId: String): Result<PlaceItem>

    // --- Methods whose model type needs clarification ---
    // Does saving a place mean saving full details or just a list item? Assume detailed Place for now.
    suspend fun savePlace(place: Place): Result<String>
    // Does updating a place mean updating the PlaceItem notes/rating or the core Place details?
    // The previous implementation used PlaceItem, let's keep that for now but rename to be specific.
    // Keep the PlaceItem update method above. Remove this potentially confusing one.
    // suspend fun updatePlace(place: Place): Result<Unit> // REMOVED - covered by updatePlace(PlaceItem)
}