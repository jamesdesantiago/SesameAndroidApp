package com.gazzel.sesameapp.data.repository

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import retrofit2.Response
import com.gazzel.sesameapp.data.local.dao.PlaceDao
import com.gazzel.sesameapp.data.local.entity.PlaceEntity
import com.gazzel.sesameapp.data.mapper.toDomain
import com.gazzel.sesameapp.data.model.PlaceDto
import com.gazzel.sesameapp.data.service.AppListService
import com.gazzel.sesameapp.domain.exception.AppException // Keep AppException
import com.gazzel.sesameapp.domain.model.Place
import com.gazzel.sesameapp.domain.model.PlaceItem
import com.gazzel.sesameapp.domain.repository.PlaceRepository
import com.gazzel.sesameapp.domain.util.Result // Use Result consistently
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import com.gazzel.sesameapp.data.service.PlaceCreate as ServicePlaceCreate
import com.gazzel.sesameapp.data.service.PlaceUpdate as ServicePlaceUpdate
import com.gazzel.sesameapp.domain.model.ListResponse
import com.gazzel.sesameapp.domain.util.onError
import com.gazzel.sesameapp.domain.util.onSuccess

// --- Helper Functions (Top-Level) ---

private suspend fun getAuthTokenPlaceRepo(): String {
    println("WARNING: Using DUMMY_AUTH_TOKEN in PlaceRepositoryImpl helpers")
    return "DUMMY_AUTH_TOKEN"
}

// *** CHANGE HELPERS BACK TO RETURN Result<T> ***

// Helper using Result<T>
private suspend fun <T, R> handleApiCallForResult( // Renamed is fine
    apiCall: suspend () -> Response<T>,
    mapper: (T) -> R
): Result<R> { // << Return Result<R>
    return try {
        Log.d("ApiCallHelper", "Executing API call...")
        val response = apiCall()
        Log.d("ApiCallHelper", "Response Code: ${response.code()}")
        if (response.isSuccessful) {
            response.body()?.let { body ->
                Result.success(mapper(body)) // << Use Result.success
            } ?: Result.error(AppException.UnknownException("API success but response body was null")) // << Use Result.error
        } else {
            Result.error(mapErrorToAppException(response.code(), response.errorBody()?.string())) // << Use Result.error
        }
    } catch (e: Exception) {
        Result.error(mapExceptionToAppException(e)) // << Use Result.error
    }
}

// Helper for Unit responses using Result<Unit>
private suspend fun handleUnitApiCallForResult( // Renamed is fine
    apiCall: suspend () -> Response<Unit>
): Result<Unit> { // << Return Result<Unit>
    return try {
        Log.d("ApiCallHelper", "Executing Unit API call...")
        val response = apiCall()
        Log.d("ApiCallHelper", "Unit Response Code: ${response.code()}")
        if (response.isSuccessful) {
            Result.success(Unit) // << Use Result.success
        } else {
            Result.error(mapErrorToAppException(response.code(), response.errorBody()?.string())) // << Use Result.error
        }
    } catch (e: Exception) {
        Result.error(mapExceptionToAppException(e)) // << Use Result.error
    }
}

// Error mapping helpers (return AppException for Result.error)
private fun mapErrorToAppException(code: Int, errorBody: String?): AppException { // << Return AppException
    Log.e("RepositoryError", "API Error $code: ${errorBody ?: "Unknown error"}")
    return when (code) {
        400 -> AppException.ValidationException(errorBody ?: "Bad Request")
        401 -> AppException.AuthException(errorBody ?: "Unauthorized")
        403 -> AppException.AuthException(errorBody ?: "Forbidden")
        404 -> AppException.ResourceNotFoundException(errorBody ?: "Not Found")
        in 500..599 -> AppException.NetworkException("Server Error ($code): ${errorBody ?: ""}", code)
        else -> AppException.NetworkException("Network Error ($code): ${errorBody ?: ""}", code)
    }
}

private fun mapExceptionToAppException(e: Exception): AppException { // << Return AppException
    Log.e("RepositoryError", "Network error: ${e.message ?: "Unknown exception"}", e)
    return when (e) {
        is java.io.IOException -> AppException.NetworkException("Network IO error: ${e.message}", cause = e)
        else -> AppException.UnknownException(e.message ?: "An unknown error occurred", e)
    }
}

// Place DTO to Domain Item Mapper
private fun PlaceDto.toPlaceItem(listId: String): PlaceItem {
    return PlaceItem(
        id = this.id, name = this.name, description = this.description ?: "",
        address = this.address, latitude = this.latitude, longitude = this.longitude,
        listId = listId, notes = null, rating = this.rating, visitStatus = null
    )
}

// --- Repository Implementation ---

class PlaceRepositoryImpl @Inject constructor(
    private val appListService: AppListService,
    private val placeDao: PlaceDao
    // TODO: Inject AuthManager
) : PlaceRepository {

    // This local mapper duplicates the top-level one, remove it
    /*
    private fun PlaceDto.toPlaceItem(listId: String): PlaceItem {
        return PlaceItem(...)
    }
    */

    override suspend fun getPlaces(): Result<List<PlaceItem>> { // Interface expects Result
        return try {
            val cachedEntities = placeDao.getAllPlaces().first()
            if (cachedEntities.isNotEmpty()) {
                Log.d("PlaceRepo", "getPlaces: Returning cached data")
                return Result.success(cachedEntities.map { it.toDomain() }) // Use Result.success
            }
            Log.d("PlaceRepo", "getPlaces: Cache empty, fetching from network")

            val token = getAuthTokenPlaceRepo()
            // *** FIX: Assign Result, not Resource ***
            val listsResult: Result<List<ListResponse>> = handleApiCallForResult( // Use Result helper
                apiCall = { appListService.getUserLists("Bearer $token") },
                mapper = { it }
            )

            // Now process the Result correctly
            if (listsResult is Result.Success) {
                val lists = listsResult.data
                val allPlaceItems = mutableListOf<PlaceItem>()
                Log.d("PlaceRepo", "getPlaces: Fetched ${lists.size} lists")

                for (list in lists) {
                    try {
                        val listId = list.id
                        // *** FIX: Assign Result, not Resource ***
                        val detailsResult: Result<ListResponse> = handleApiCallForResult( // Use Result helper
                            apiCall = { appListService.getListDetail("Bearer $token", listId) },
                            mapper = { it }
                        )

                        if (detailsResult is Result.Success) {
                            detailsResult.data.places?.let { placesDtoList ->
                                val placeItemsInList = placesDtoList.map { placeDto ->
                                    placeDto.toPlaceItem(listId) // Use top-level mapper
                                }
                                allPlaceItems.addAll(placeItemsInList)
                            }
                        } else if (detailsResult is Result.Error) {
                            Log.w("PlaceRepo", "getPlaces: Failed fetch details for list ${listId}: ${detailsResult.exception.message}")
                        }
                    } catch (e: Exception) {
                        Log.e("PlaceRepo", "getPlaces: Error processing list ${list.id}", e)
                    }
                }

                Log.d("PlaceRepo", "getPlaces: Total places fetched: ${allPlaceItems.size}")
                val entities = allPlaceItems.map { PlaceEntity.fromDomain(it) }
                if (entities.isNotEmpty()) {
                    placeDao.insertPlaces(entities)
                    Log.d("PlaceRepo", "getPlaces: Cached ${entities.size} places")
                }
                Result.success(allPlaceItems) // Use Result.success
            } else { // listsResult is Result.Error
                Log.e("PlaceRepo", "getPlaces: Failed to load lists: ${(listsResult as Result.Error).exception.message}")
                Result.error(listsResult.exception) // Propagate Result.error
            }
        } catch (e: Exception) {
            Log.e("PlaceRepo", "getPlaces: General exception", e)
            Result.error(mapExceptionToAppException(e)) // Use Result.error
        }
    }

    override suspend fun getPlacesByListId(listId: String): Result<List<PlaceItem>> { // Interface expects Result
        return try {
            val cachedEntities = placeDao.getPlacesByListId(listId).first()
            if (cachedEntities.isNotEmpty()) {
                Log.d("PlaceRepo", "getPlacesByListId($listId): Returning cached data")
                return Result.success(cachedEntities.map { it.toDomain() }) // Use Result.success
            }
            Log.d("PlaceRepo", "getPlacesByListId($listId): Cache empty, fetching from network")

            val token = getAuthTokenPlaceRepo()
            // *** FIX: Assign Result, not Resource ***
            val detailsResult: Result<ListResponse> = handleApiCallForResult( // Use Result helper
                apiCall = { appListService.getListDetail("Bearer $token", listId) },
                mapper = { it }
            )

            if (detailsResult is Result.Success) {
                val placesDtoList = detailsResult.data.places.orEmpty()
                val placeItems = placesDtoList.map { placeDto ->
                    placeDto.toPlaceItem(listId) // Use top-level mapper
                }

                val entities = placeItems.map { PlaceEntity.fromDomain(it) }
                if (entities.isNotEmpty()) {
                    placeDao.insertPlaces(entities)
                    Log.d("PlaceRepo", "getPlacesByListId($listId): Cached ${entities.size} places")
                }
                Result.success(placeItems) // Use Result.success
            } else { // detailsResult is Result.Error
                Log.e("PlaceRepo", "getPlacesByListId($listId): Failed: ${(detailsResult as Result.Error).exception.message}")
                Result.error(detailsResult.exception) // Propagate Result.error
            }
        } catch (e: Exception) {
            Log.e("PlaceRepo", "getPlacesByListId($listId): General exception", e)
            Result.error(mapExceptionToAppException(e)) // Use Result.error
        }
    }

    override suspend fun addPlace(place: PlaceItem): Result<PlaceItem> { // Interface expects Result
        val token = getAuthTokenPlaceRepo()
        val placeCreateDto = ServicePlaceCreate(
            placeId = place.id,
            name = place.name,
            address = place.address,
            latitude = place.latitude,
            longitude = place.longitude,
            rating = place.rating?.toString()
        )
        // *** FIX: Assign Result, not Resource ***
        val result: Result<PlaceItem> = handleApiCallForResult( // Use Result helper
            apiCall = { appListService.addPlaceToList("Bearer $token", place.listId, placeCreateDto) },
            mapper = { it: PlaceItem -> it }
        )
        result.onSuccess { addedPlace ->
            placeDao.insertPlace(PlaceEntity.fromDomain(addedPlace))
            Log.d("PlaceRepo", "addPlace: Successfully added and cached ${addedPlace.id}")
        }
        return result
    }

    override suspend fun updatePlace(place: PlaceItem): Result<PlaceItem> { // Interface expects Result
        val token = getAuthTokenPlaceRepo()
        val placeUpdateDto = ServicePlaceUpdate(
            name = place.name,
            address = place.address,
            rating = place.rating?.toString(),
            notes = place.notes
        )
        // *** FIX: Assign Result, not Resource ***
        val result: Result<PlaceItem> = handleApiCallForResult( // Use Result helper
            apiCall = { appListService.updatePlace("Bearer $token", place.listId, place.id, placeUpdateDto) },
            mapper = { item: PlaceItem -> item }
        )
        result.onSuccess { updatedPlace ->
            placeDao.updatePlace(PlaceEntity.fromDomain(updatedPlace))
            Log.d("PlaceRepo", "updatePlace: Successfully updated and cached ${updatedPlace.id}")
        }
        return result
    }

    override suspend fun deletePlace(placeId: String): Result<Unit> { // Interface expects Result
        return try {
            val entity = placeDao.getPlaceById(placeId)
            if (entity == null) {
                Log.w("PlaceRepo", "deletePlace($placeId): Not found locally.")
                // Return Result.error consistent with interface
                return Result.error(AppException.ResourceNotFoundException("Place with ID $placeId not found locally."))
            }
            val listId = entity.listId
            val token = getAuthTokenPlaceRepo()

            // *** FIX: Assign Result, not Resource ***
            val result: Result<Unit> = handleUnitApiCallForResult { // Use Result helper
                appListService.removePlaceFromList("Bearer $token", listId, placeId)
            }

            result.onSuccess {
                placeDao.deletePlace(placeId)
                Log.d("PlaceRepo", "deletePlace($placeId): Successfully deleted from API and cache.")
            }
            result
        } catch (e: Exception) {
            Log.e("PlaceRepo", "deletePlace($placeId): General exception", e)
            Result.error(mapExceptionToAppException(e)) // Use Result.error
        }
    }


    // --- Unimplemented Methods (Matching Interface expected Result<T>) ---

    override suspend fun updatePlace(place: Place): Result<Unit> {
        return Result.error(AppException.UnknownException("Detailed updatePlace(Place) not implemented"))
    }

    override suspend fun getPlaceDetails(placeId: String): Result<Place> {
        return Result.error(AppException.UnknownException("getPlaceDetails not implemented"))
    }

    override suspend fun savePlace(place: Place): Result<String> {
        return Result.error(AppException.UnknownException("savePlace not implemented"))
    }

    override suspend fun getNearbyPlaces(
        location: LatLng,
        radius: Int,
        type: String?
    ): Result<List<Place>> {
        return Result.error(AppException.UnknownException("getNearbyPlaces not implemented"))
    }

    // --- Flow methods ---

    override fun searchPlaces(query: String, location: LatLng, radius: Int): Flow<List<Place>> {
        return flow { throw NotImplementedError("Detailed searchPlaces not implemented") }
    }

    override fun getPlaceById(placeId: String): Flow<PlaceItem?> {
        return placeDao.getPlaceByIdFlow(placeId)
            .map { entity -> entity?.toDomain() } // Assuming PlaceEntity has toDomain() -> PlaceItem?
    }

} // End of PlaceRepositoryImpl class