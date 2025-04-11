// app/src/main/java/com/gazzel/sesameapp/domain/repository/PlaceRepository.kt
package com.gazzel.sesameapp.domain.repository

// Import PagingData
import androidx.paging.PagingData
// Import Domain models
import com.gazzel.sesameapp.domain.model.Place
import com.gazzel.sesameapp.domain.model.PlaceItem
// Other imports
import com.gazzel.sesameapp.domain.util.Result
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.Flow

interface PlaceRepository {

    // --- Methods operating on PlaceItems (within lists) ---
    suspend fun getPlaces(): Result<List<PlaceItem>> // Get all items across lists (Consider deprecating or making paginated if needed)
    suspend fun getPlacesByListId(listId: String): Result<List<PlaceItem>> // Get all items for a list (Consider deprecating or making paginated)

    // --- vvv NEW Paginated Method vvv ---
    fun getPlacesPaginated(listId: String): Flow<PagingData<PlaceItem>> // <<< ADDED for paginated places in a list
    // --- ^^^ NEW Paginated Method ^^^ ---

    suspend fun addPlace(place: PlaceItem): Result<PlaceItem> // Add item to a list
    suspend fun updatePlace(place: PlaceItem): Result<PlaceItem> // Update item in a list (e.g., notes, rating)
    suspend fun deletePlace(placeId: String): Result<Unit> // Delete item by its ID
    fun getPlaceById(placeId: String): Flow<PlaceItem?> // Observe a single item (likely from DB)
    suspend fun getPlaceItemById(placeId: String): Result<PlaceItem> // Get single item suspend fun


    // --- Methods operating on detailed Place model (Keep as is for now) ---
    suspend fun getPlaceDetails(placeId: String): Result<Place>
    suspend fun searchPlaces(query: String, location: LatLng, radius: Int): Result<List<Place>>
    suspend fun getNearbyPlaces(location: LatLng, radius: Int, type: String? = null): Result<List<Place>>
    suspend fun savePlace(place: Place): Result<String> // Maybe rename to savePlaceDetails?

}