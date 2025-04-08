// data/repository/PlaceRepositoryImpl.kt
package com.gazzel.sesameapp.data.repository

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import retrofit2.Response
import com.gazzel.sesameapp.data.local.dao.PlaceDao
import com.gazzel.sesameapp.data.local.entity.PlaceEntity
import com.gazzel.sesameapp.data.service.ListApiService
import com.gazzel.sesameapp.data.remote.dto.PlaceCreateDto
import com.gazzel.sesameapp.data.remote.dto.PlaceUpdateDto
import com.gazzel.sesameapp.data.model.ListDto
import com.gazzel.sesameapp.data.model.PlaceDto
// Import Domain models
import com.gazzel.sesameapp.domain.model.Place // Keep for detailed methods interface contract
import com.gazzel.sesameapp.domain.model.PlaceItem // Primary model for implementation
// Other necessary imports
import com.gazzel.sesameapp.domain.auth.TokenProvider
import com.gazzel.sesameapp.domain.exception.AppException
import com.gazzel.sesameapp.domain.repository.PlaceRepository
import com.gazzel.sesameapp.domain.util.Result
import com.gazzel.sesameapp.domain.util.onError
import com.gazzel.sesameapp.domain.util.onSuccess
import com.gazzel.sesameapp.domain.util.map // Add map import
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map as flowMap // Alias flow's map to avoid clash
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// Mapper: DTO -> PlaceItem (Internal consistency)
private fun PlaceDto.toPlaceItem(listId: String): PlaceItem {
    return PlaceItem(
        id = this.id, name = this.name, description = this.description ?: "",
        address = this.address, latitude = this.latitude, longitude = this.longitude,
        listId = listId, notes = null, // Map if DTO has notes/status
        rating = this.rating, visitStatus = null
    )
}

// Mapper: Entity -> PlaceItem (Internal consistency)
private fun PlaceEntity.toDomainPlaceItem(): PlaceItem { // Renamed for clarity
    return PlaceItem(
        id = this.id, name = this.name, description = this.description,
        address = this.address, latitude = this.latitude, longitude = this.longitude,
        listId = this.listId, notes = this.notes, rating = this.rating,
        visitStatus = this.visitStatus
    )
}

// Mapper: PlaceItem -> Entity (Internal consistency)
private fun PlaceItem.toPlaceEntity(): PlaceEntity { // Renamed for clarity
    return PlaceEntity(
        id = this.id, name = this.name, description = this.description,
        address = this.address, latitude = this.latitude, longitude = this.longitude,
        listId = this.listId, notes = this.notes, rating = this.rating,
        visitStatus = this.visitStatus,
        // Let Room handle createdAt/updatedAt if not explicitly set
    )
}


@Singleton
class PlaceRepositoryImpl @Inject constructor(
    private val listApiService: ListApiService,
    private val placeDao: PlaceDao,
    private val tokenProvider: TokenProvider
) : PlaceRepository {

    // --- API Call Handlers (Keep as previously defined) ---
    private suspend fun <Dto, Domain> handleApiCallToDomain(apiCall: suspend () -> Response<Dto>, mapper: (Dto) -> Domain): Result<Domain> { /* ... */ }
    private suspend fun <Dto, Domain> handleListApiCallToDomain(apiCall: suspend () -> Response<List<Dto>>, mapper: (Dto) -> Domain): Result<List<Domain>> { /* ... */ }
    private suspend fun handleUnitApiCall(apiCall: suspend () -> Response<Unit>): Result<Unit> { /* ... */ }
    private fun mapErrorToAppException(code: Int, errorBody: String?): AppException { /* ... */ }
    private fun mapExceptionToAppException(e: Exception): AppException { /* ... */ }
    // --- End API Call Handlers ---


    // --- Interface Implementation (Focusing on PlaceItem) ---

    override suspend fun getPlaces(): Result<List<PlaceItem>> {
        return try {
            // 1. Try cache
            val cachedEntities = withContext(Dispatchers.IO) { placeDao.getAllPlaces().first() }
            if (cachedEntities.isNotEmpty()) {
                Log.d("PlaceRepo", "getPlaces: Returning cached data")
                return Result.success(cachedEntities.map { it.toDomainPlaceItem() }) // Map Entity -> PlaceItem
            }
            Log.d("PlaceRepo", "getPlaces: Cache empty, fetching from network")

            // 2. Fetch all user lists
            val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("Not authenticated"))
            val listsResult = handleListApiCallToDomain(
                apiCall = { listApiService.getUserLists("Bearer $token") },
                mapper = { listDto: ListDto -> listDto } // Map to self first
            )

            listsResult.flatMap { lists -> // Use flatMap to chain results
                val allPlaceItems = mutableListOf<PlaceItem>()
                Log.d("PlaceRepo", "getPlaces: Fetched ${lists.size} lists")

                var encounteredError: AppException? = null

                // 3. Fetch details for each list
                for (list in lists) {
                    val detailsResult = handleApiCallToDomain(
                        apiCall = { listApiService.getListDetail("Bearer $token", list.id) },
                        mapper = { listDto: ListDto -> listDto }
                    )

                    detailsResult.onSuccess { listDetailDto ->
                        listDetailDto.places?.let { placesDtoList ->
                            allPlaceItems.addAll(placesDtoList.map { it.toPlaceItem(list.id) })
                        }
                    }.onError { exception ->
                        Log.w("PlaceRepo", "getPlaces: Failed fetch details for list ${list.id}: ${exception.message}")
                        // Store first error encountered, but continue fetching others
                        if (encounteredError == null) encounteredError = exception
                    }
                }

                // 4. Process results
                if (encounteredError != null && allPlaceItems.isEmpty()) {
                    // If we encountered errors AND got no places at all, return the error
                    Result.error(encounteredError!!)
                } else {
                    // If we got some places (even with partial errors), cache and return success
                    Log.d("PlaceRepo", "getPlaces: Total places fetched: ${allPlaceItems.size}")
                    if (allPlaceItems.isNotEmpty()) {
                        val entities = allPlaceItems.map { it.toPlaceEntity() }
                        withContext(Dispatchers.IO) { placeDao.insertPlaces(entities) } // Cache results
                        Log.d("PlaceRepo", "getPlaces: Cached ${entities.size} places")
                    }
                    Result.success(allPlaceItems) // Return successfully fetched items
                }
            } // Propagate error from listsResult.flatMap if initial list fetch failed
        } catch (e: Exception) {
            Log.e("PlaceRepo", "getPlaces: General exception", e)
            Result.error(mapExceptionToAppException(e))
        }
    }

    override suspend fun getPlacesByListId(listId: String): Result<List<PlaceItem>> {
        return try {
            // 1. Try Cache
            val cachedEntities = withContext(Dispatchers.IO) { placeDao.getPlacesByListId(listId).first() }
            if (cachedEntities.isNotEmpty()) {
                Log.d("PlaceRepo", "getPlacesByListId($listId): Returning cached data")
                return Result.success(cachedEntities.map { it.toDomainPlaceItem() }) // Entity -> PlaceItem
            }
            Log.d("PlaceRepo", "getPlacesByListId($listId): Cache empty, fetching from network")

            // 2. Fetch List Details from API
            val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("Not authenticated"))
            val detailsResult = handleApiCallToDomain(
                apiCall = { listApiService.getListDetail("Bearer $token", listId) },
                mapper = { listDto: ListDto -> listDto }
            )

            detailsResult.map { listDetailDto -> // Map success case
                val placesDtoList = listDetailDto.places.orEmpty()
                val placeItems = placesDtoList.map { it.toPlaceItem(listId) } // DTO -> PlaceItem

                // 3. Cache results
                if (placeItems.isNotEmpty()) {
                    val entities = placeItems.map { it.toPlaceEntity() }
                    withContext(Dispatchers.IO) { placeDao.insertPlaces(entities) }
                    Log.d("PlaceRepo", "getPlacesByListId($listId): Cached ${entities.size} places")
                }
                placeItems // Return the list of PlaceItems
            } // Propagate error if detailsResult was Error

        } catch (e: Exception) {
            Log.e("PlaceRepo", "getPlacesByListId($listId): General exception", e)
            Result.error(mapExceptionToAppException(e))
        }
    }

    override suspend fun addPlace(place: PlaceItem): Result<PlaceItem> {
        // Map Domain PlaceItem -> Request PlaceCreateDto
        val placeCreateDto = PlaceCreateDto(
            placeId = place.id,
            name = place.name, address = place.address,
            latitude = place.latitude, longitude = place.longitude,
            rating = place.rating,
            // notes = place.notes, // Add if supported
            // visitStatus = place.visitStatus // Add if supported
        )
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("Not authenticated"))

        // Call API
        val apiResult = handleUnitApiCall {
            listApiService.addPlace(
                authorization = "Bearer $token",
                listId = place.listId,
                place = placeCreateDto
            )
        }

        // If API succeeds, update local cache and return the original item
        return apiResult.flatMap { // Use flatMap as DB write can fail
            withContext(Dispatchers.IO) {
                try {
                    placeDao.insertPlace(place.toPlaceEntity()) // Use PlaceItem -> Entity mapper
                    Log.d("PlaceRepo", "addPlace: Successfully added and cached ${place.id}")
                    Result.success(place) // Return the original item
                } catch (dbEx: Exception) {
                    Log.e("PlaceRepo", "addPlace: DB insert failed after API success for ${place.id}", dbEx)
                    Result.error(AppException.DatabaseException("Failed to cache added place", dbEx))
                }
            }
        }
    }

    override suspend fun updatePlace(place: PlaceItem): Result<PlaceItem> {
        // Map Domain PlaceItem -> Request PlaceUpdateDto
        val placeUpdateDto = PlaceUpdateDto(
            name = place.name, address = place.address,
            rating = place.rating, notes = place.notes
            // visitStatus = place.visitStatus // Add if supported
        )
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("Not authenticated"))

        // Call API
        val apiResult = handleUnitApiCall {
            listApiService.updatePlace(
                authorization = "Bearer $token",
                listId = place.listId, placeId = place.id,
                update = placeUpdateDto
            )
        }

        // If API succeeds, update local cache and return the updated item
        return apiResult.flatMap { // Use flatMap as DB write can fail
            withContext(Dispatchers.IO) {
                try {
                    placeDao.updatePlace(place.toPlaceEntity()) // Use PlaceItem -> Entity mapper
                    Log.d("PlaceRepo", "updatePlace: Successfully updated and cached ${place.id}")
                    Result.success(place) // Return the updated item
                } catch (dbEx: Exception) {
                    Log.e("PlaceRepo", "updatePlace: DB update failed after API success for ${place.id}", dbEx)
                    Result.error(AppException.DatabaseException("Failed to cache updated place", dbEx))
                }
            }
        }
    }

    override suspend fun deletePlace(placeId: String): Result<Unit> {
        // 1. Find the place locally to get its listId
        val entity = withContext(Dispatchers.IO) { placeDao.getPlaceById(placeId) }
        if (entity == null) {
            Log.w("PlaceRepo", "deletePlace($placeId): Not found locally.")
            // Decide if this is an error or success (item already gone?) - Let's return success.
            // return Result.error(AppException.ResourceNotFoundException("Place with ID $placeId not found locally."))
            return Result.success(Unit)
        }
        val listId = entity.listId
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("Not authenticated"))

        // 2. Call API to delete
        val apiResult = handleUnitApiCall {
            listApiService.removePlaceFromList(
                authorization = "Bearer $token",
                listId = listId, placeId = placeId
            )
        }

        // 3. If API succeeds or place was already gone (404?), delete locally
        return apiResult.flatMap { // If API call itself was successful (e.g. 204)
            withContext(Dispatchers.IO) {
                try {
                    placeDao.deletePlace(placeId)
                    Log.d("PlaceRepo", "deletePlace($placeId): Successfully deleted from API and cache.")
                    Result.success(Unit)
                } catch (dbEx: Exception) {
                    Log.e("PlaceRepo", "deletePlace($placeId): DB delete failed after API success.", dbEx)
                    Result.error(AppException.DatabaseException("Failed to delete place from cache", dbEx))
                }
            }
        }.onError { exception -> // Handle API errors specifically
            if (exception is AppException.ResourceNotFoundException) {
                // If API returned 404, the item is already gone remotely. Still try to delete locally.
                Log.w("PlaceRepo", "deletePlace($placeId): API returned 404. Attempting local delete.")
                try {
                    withContext(Dispatchers.IO) { placeDao.deletePlace(placeId) }
                    Result.success(Unit) // Consider this overall success
                } catch (dbEx: Exception) {
                    Log.e("PlaceRepo", "deletePlace($placeId): DB delete failed after API 404.", dbEx)
                    Result.error(AppException.DatabaseException("Failed to delete place from cache after API 404", dbEx))
                }
            } else {
                // Propagate other API errors
                Result.error(exception)
            }
        }
    }


    // --- Flow methods (Interact primarily with DAO, map Entity -> PlaceItem) ---
    override fun getPlaceById(placeId: String): Flow<PlaceItem?> {
        return placeDao.getPlaceByIdFlow(placeId)
            .flowMap { entity -> entity?.toDomainPlaceItem() } // Use flow's map operator
            .flowOn(Dispatchers.IO) // Perform DB query on IO dispatcher
    }


    // --- Methods using detailed Place model (Currently Not Implemented) ---

    override suspend fun getPlaceDetails(placeId: String): Result<Place> {
        Log.w("PlaceRepositoryImpl", "getPlaceDetails(placeId) called - NOT IMPLEMENTED")
        // TODO: Implement fetching detailed Place data (e.g., from Google Places SDK or specific API endpoint)
        return Result.error(AppException.UnknownException("getPlaceDetails not implemented"))
    }

    override suspend fun searchPlaces(query: String, location: LatLng, radius: Int): Result<List<Place>> {
        Log.w("PlaceRepositoryImpl", "searchPlaces(...) called - NOT IMPLEMENTED")
        // TODO: Implement detailed Place search (e.g., via Google Places SDK Nearby Search)
        return Result.error(AppException.UnknownException("Detailed searchPlaces not implemented"))
    }

    override suspend fun getNearbyPlaces(location: LatLng, radius: Int, type: String?): Result<List<Place>> {
        Log.w("PlaceRepositoryImpl", "getNearbyPlaces(...) called - NOT IMPLEMENTED")
        // TODO: Implement detailed Nearby Place search (e.g., via Google Places SDK Nearby Search)
        return Result.error(AppException.UnknownException("getNearbyPlaces not implemented"))
    }

    override suspend fun savePlace(place: Place): Result<String> {
        Log.w("PlaceRepositoryImpl", "savePlace(Place) called - NOT IMPLEMENTED")
        // TODO: Implement logic to save FULL Place details (might involve different API/DB schema)
        return Result.error(AppException.UnknownException("savePlace for detailed Place not implemented"))
    }

    // --- Error Mapping Helpers (Keep as is) ---
    // ... (mapErrorToAppException and mapExceptionToAppException) ...

} // End of PlaceRepositoryImpl class