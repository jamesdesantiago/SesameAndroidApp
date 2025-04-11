// app/src/main/java/com/gazzel/sesameapp/data/repository/PlaceRepositoryImpl.kt
package com.gazzel.sesameapp.data.repository

import android.util.Log
// Paging Imports
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.gazzel.sesameapp.data.paging.PlaceInListPagingSource // <<< ADD import for the new PagingSource
// Local DB Imports
import com.gazzel.sesameapp.data.local.dao.PlaceDao
import com.gazzel.sesameapp.data.local.entity.PlaceEntity
// Cache Manager Import
import com.gazzel.sesameapp.data.manager.ICacheManager
// Remote API Imports
import com.gazzel.sesameapp.data.remote.ListApiService
import com.gazzel.sesameapp.data.remote.dto.ListDto // Keep if used by helpers
import com.gazzel.sesameapp.data.remote.dto.PlaceCreateDto
import com.gazzel.sesameapp.data.remote.dto.PlaceDto
import com.gazzel.sesameapp.data.remote.dto.PlaceUpdateDto
// Domain Imports
import com.gazzel.sesameapp.domain.auth.TokenProvider
import com.gazzel.sesameapp.domain.exception.AppException
import com.gazzel.sesameapp.domain.model.Place
import com.gazzel.sesameapp.domain.model.PlaceItem
import com.gazzel.sesameapp.domain.repository.PlaceRepository
import com.gazzel.sesameapp.domain.util.Result
import com.gazzel.sesameapp.domain.util.flatMap
import com.gazzel.sesameapp.domain.util.map
import com.gazzel.sesameapp.domain.util.onError
import com.gazzel.sesameapp.domain.util.onSuccess
// Mappers (Assuming they exist in data.mapper)
import com.gazzel.sesameapp.data.mapper.toPlaceItem
import com.gazzel.sesameapp.data.mapper.toPlaceEntity
import com.gazzel.sesameapp.data.mapper.toDomainPlaceItem
// Other Imports
import com.google.android.gms.maps.model.LatLng
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf // For returning empty PagingData on error
import kotlinx.coroutines.flow.map as flowMap // Alias necessary due to Result.map extension
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException // Import for error handling if used in helpers
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton


// Keep local mapper definitions if they are specific to this repository's internal logic
// Otherwise, prefer centralizing them in the data/mapper package
private fun PlaceDto.toPlaceItemLocal(listId: String): PlaceItem {
    // TODO: Implement mapping logic if needed here, or ensure central mapper is used
    return PlaceItem(id = this.id.toString(), name = this.name, address = this.address, latitude=this.latitude, longitude=this.longitude, listId=listId, notes=this.notes, rating=this.rating, visitStatus=this.visitStatus)
}
private fun PlaceEntity.toDomainPlaceItemLocal(): PlaceItem {
    // TODO: Implement mapping logic if needed here, or ensure central mapper is used
    return PlaceItem(id = this.id, name = this.name, address = this.address, latitude=this.latitude, longitude=this.longitude, listId=this.listId, notes=this.notes, rating=this.rating, visitStatus=this.visitStatus, description = this.description)
}
private fun PlaceItem.toPlaceEntityLocal(): PlaceEntity {
    // TODO: Implement mapping logic if needed here, or ensure central mapper is used
    return PlaceEntity(id = this.id, name = this.name, address = this.address, latitude=this.latitude, longitude=this.longitude, listId=this.listId, notes=this.notes, rating=this.rating, visitStatus=this.visitStatus, description = this.description)
}


@Singleton
class PlaceRepositoryImpl @Inject constructor(
    private val listApiService: ListApiService,
    private val placeDao: PlaceDao,
    private val tokenProvider: TokenProvider,
    private val cacheManager: ICacheManager
) : PlaceRepository {

    companion object {
        // Page size for places pagination
        const val PLACES_NETWORK_PAGE_SIZE = 30
        // Cache constants
        private val PLACE_ITEM_CACHE_EXPIRY_MS = TimeUnit.HOURS.toMillis(1)
        private const val PLACE_ITEM_CACHE_PREFIX = "place_item_"
        // Cache key prefix for the non-paginated list of places (consider removing if not used)
        private const val LIST_PLACES_CACHE_PREFIX = "list_places_"
    }

    // --- API Call Handlers & Error Mapping (Keep your existing implementations) ---
    // Ensure these handle potential null bodies and map errors correctly
    private suspend fun <DtoIn, DomainOut> handleApiCallToDomain(
        apiCall: suspend () -> Response<DtoIn>,
        mapper: (DtoIn) -> DomainOut
    ): Result<DomainOut> {
        return try {
            val response = apiCall()
            if (response.isSuccessful) {
                response.body()?.let { Result.success(mapper(it)) }
                    ?: Result.error(AppException.UnknownException("API success response body was null"))
            } else {
                Result.error(mapErrorToAppException(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Result.error(mapExceptionToAppException(e))
        }
    }

    private suspend fun <DtoIn, DomainOut> handleListApiCallToDomain(
        apiCall: suspend () -> Response<List<DtoIn>>,
        mapper: (DtoIn) -> DomainOut
    ): Result<List<DomainOut>> {
        return try {
            val response = apiCall()
            if (response.isSuccessful) {
                val domainList = response.body()?.map(mapper) ?: emptyList()
                Result.success(domainList)
            } else {
                Result.error(mapErrorToAppException(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Result.error(mapExceptionToAppException(e))
        }
    }

    // Adjusted handleUnitApiCall to return the Response object on error for more flexibility
    private suspend fun handleApiResult(apiCall: suspend () -> Response<Unit>): Result<Unit> {
        return try {
            val response = apiCall()
            if (response.isSuccessful || response.code() == 204) { // 204 No Content is success for DELETE/PATCH
                Result.success(Unit)
            } else {
                Log.e("PlaceRepositoryImpl", "API call failed: ${response.code()} - ${response.message()}")
                Result.error(mapErrorToAppException(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Log.e("PlaceRepositoryImpl", "Exception during API call", e)
            Result.error(mapExceptionToAppException(e))
        }
    }


    // Keep your specific error mapping functions
    private fun mapErrorToAppException(code: Int, errorBody: String?): AppException {
        val defaultMsg = "API Error $code"
        val bodyMsg = errorBody ?: "No message"
        Log.e("PlaceRepositoryImpl", "$defaultMsg: $bodyMsg")
        return when (code) {
            401, 403 -> AppException.AuthException(errorBody ?: "Authorization failed")
            404 -> AppException.ResourceNotFoundException(errorBody ?: "Resource not found")
            409 -> AppException.ValidationException(errorBody ?: "Conflict") // e.g., Place already exists
            422 -> AppException.ValidationException(errorBody ?: "Invalid data")
            in 500..599 -> AppException.NetworkException("Server Error ($code)", code)
            else -> AppException.NetworkException("Network Error ($code): $bodyMsg", code)
        }
    }

    private fun mapExceptionToAppException(e: Exception): AppException {
        Log.e("PlaceRepositoryImpl", "Mapping exception: ${e.javaClass.simpleName}", e)
        return when (e) {
            is retrofit2.HttpException -> mapErrorToAppException(e.code(), e.response()?.errorBody()?.string())
            is IOException -> AppException.NetworkException("Network connection issue", cause = e)
            is AppException -> e // Don't re-wrap
            else -> AppException.UnknownException("An unexpected error occurred: ${e.message}", e)
        }
    }
    // --- End API Call Handlers & Error Mapping ---


    // --- Existing Method Implementations (Keep) ---
    // TODO: Review if getPlaces and getPlacesByListId are still needed or should use pagination.
    // The current implementations seem inefficient (N+1 fetches in getPlaces) and rely on a different ListDto structure.

    override suspend fun getPlaces(): Result<List<PlaceItem>> {
        Log.w("PlaceRepositoryImpl", "getPlaces() is potentially inefficient. Consider paginating all places view if needed.")
        // ... Keep your existing complex implementation for now, but mark for review ...
        return try {
            val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("Not authenticated"))
            val authHeader = "Bearer $token"

            // This call now returns PaginatedListResponseDto, need to handle it or use a different endpoint
            // For simplicity, fetching only the first page as a placeholder for the old logic. THIS IS NOT CORRECT for all places.
            val listsResult = try {
                listApiService.getUserLists(authHeader, null, null, 1, 100) // Fetch up to 100 lists? Still limited.
            } catch (e: Exception) { return Result.error(mapExceptionToAppException(e)) }

            if (!listsResult.isSuccessful || listsResult.body() == null) {
                return Result.error(mapErrorToAppException(listsResult.code(), listsResult.errorBody()?.string()))
            }

            val listIds = listsResult.body()!!.items.map { it.id.toString() } // Assuming ListDto ID is Int

            val allPlaceItems = mutableListOf<PlaceItem>()
            var firstError: AppException? = null

            // This N+1 fetching pattern is highly inefficient and should be replaced
            for (listIdStr in listIds) {
                // This method needs rework as getListDetail no longer returns places
                val placesInListResult = getPlacesByListId(listIdStr) // Recursive call? Potential issue. Let's assume it calls API
                placesInListResult.onSuccess { items ->
                    allPlaceItems.addAll(items)
                }.onError { error ->
                    Log.w("PlaceRepo", "getPlaces: Failed fetch details for list $listIdStr: ${error.message}")
                    if (firstError == null) firstError = error
                }
            }
            if (firstError != null && allPlaceItems.isEmpty()) Result.error(firstError!!)
            else Result.success(allPlaceItems)

        } catch (e: Exception) {
            Log.e("PlaceRepo", "getPlaces: General exception", e)
            Result.error(mapExceptionToAppException(e))
        }
    }


    override suspend fun getPlacesByListId(listId: String): Result<List<PlaceItem>> {
        Log.w("PlaceRepositoryImpl", "getPlacesByListId() called. Consider using getPlacesPaginated() instead for UI lists.")
        // This implementation fetches ListDetailDto (no places) then tries to access places - this needs fixing.
        // It should call the *paginated* place endpoint and fetch *all* pages, which defeats pagination.
        // Returning an error or an empty list might be safer until refactored.
        return Result.error(AppException.UnknownException("getPlacesByListId is deprecated due to pagination changes. Use getPlacesPaginated."))

        // --- OLD/Incorrect Logic ---
        /*
       val cacheKey = "$LIST_PLACES_CACHE_PREFIX$listId"
       val listType: Type = object : TypeToken<List<PlaceItem>>() {}.type
       try { val cached = cacheManager.getCachedData<List<PlaceItem>>(cacheKey, listType); if (cached != null) return Result.success(cached) } catch (e: Exception) { Log.e("PlaceRepo", "Cache Get Error", e) }
       val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("Not authenticated"))
       // This API call now returns ListDetailDto WITHOUT places
       val detailsResult = handleApiCallToDomain( apiCall = { listApiService.getListDetail("Bearer $token", listId) }, mapper = { it } )
       return detailsResult.map { listDetailDto ->
           // ERROR: listDetailDto no longer has .places
           // val placesDtoList = listDetailDto.places.orEmpty() // <<< THIS WILL FAIL
           val placeItems = emptyList<PlaceItem>() // Needs to fetch from /places endpoint now
           try { cacheManager.cacheData(cacheKey, placeItems, listType, PLACE_ITEM_CACHE_EXPIRY_MS) } catch (e: Exception) { Log.e("PlaceRepo", "Cache Put Error", e) }
           placeItems
       }
       */
    }


    override suspend fun addPlace(place: PlaceItem): Result<PlaceItem> {
        Log.d("PlaceRepositoryImpl", "Adding place: ${place.name} to list ${place.listId}")
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("Not authenticated"))
        val authHeader = "Bearer $token"
        val placeCreateDto = PlaceCreateDto( // Map Domain -> Create DTO
            placeId = place.id, // Assuming domain ID is the external placeId
            name = place.name,
            address = place.address,
            latitude = place.latitude,
            longitude = place.longitude,
            rating = place.rating,
            notes = place.notes,
            visitStatus = place.visitStatus
        )

        // Call API which now returns the created PlaceDto
        val apiResult = handleApiCallToDomain(
            apiCall = { listApiService.addPlace(authHeader, place.listId, placeCreateDto) },
            mapper = { placeDto -> placeDto.toPlaceItem(place.listId) } // Map Response DTO -> Domain Item
        )

        return apiResult.flatMap { addedPlaceItem -> // Use flatMap to chain DB operation
            // Invalidate places pager? Difficult without Paging library integration here.
            // TODO: Investigate how to best invalidate PagingData source from Repository action.
            // For now, rely on UI refresh.

            // Save to local DB
            withContext(Dispatchers.IO) {
                try {
                    // Use local mapper PlaceItem -> PlaceEntity
                    placeDao.insertPlace(addedPlaceItem.toPlaceEntityLocal())
                    Log.d("PlaceRepo", "addPlace: Successfully added & saved to DB ${addedPlaceItem.id}")
                    Result.success(addedPlaceItem) // Return the item returned by API (with DB ID)
                } catch (dbEx: Exception) {
                    Log.e("PlaceRepo", "addPlace: DB insert failed after API success for ${addedPlaceItem.id}", dbEx)
                    // Return API success but indicate DB failure? Or return DB error? Returning DB error.
                    Result.error(AppException.DatabaseException("Failed to save added place locally", dbEx))
                }
            }
        }
    }

    override suspend fun updatePlace(place: PlaceItem): Result<PlaceItem> {
        Log.d("PlaceRepositoryImpl", "Updating place: ${place.id} with notes: ${place.notes}")
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("Not authenticated"))
        val authHeader = "Bearer $token"
        // Map Domain -> Update DTO (Only includes notes based on current PlaceUpdate model)
        val placeUpdateDto = PlaceUpdateDto(notes = place.notes)

        // Call API which now returns the updated PlaceDto
        val apiResult = handleApiCallToDomain(
            apiCall = { listApiService.updatePlace(authHeader, place.listId, place.id, placeUpdateDto) },
            mapper = { placeDto -> placeDto.toPlaceItem(place.listId) } // Map Response DTO -> Domain Item
        )

        return apiResult.flatMap { updatedPlaceItem ->
            // Invalidate cache/pager if needed
            try {
                // Invalidate single item cache
                val cacheKey = "$PLACE_ITEM_CACHE_PREFIX${updatedPlaceItem.id}"
                cacheManager.cacheData(cacheKey, updatedPlaceItem, PlaceItem::class.java, -1L) // Invalidate
                Log.d("PlaceRepo", "Invalidated cache for place ${updatedPlaceItem.id}")
            } catch (e: Exception) { Log.e("PlaceRepo", "Cache invalidation error after updating place ${updatedPlaceItem.id}", e) }

            // Update local DB
            withContext(Dispatchers.IO) {
                try {
                    // Use local mapper PlaceItem -> PlaceEntity
                    placeDao.updatePlace(updatedPlaceItem.toPlaceEntityLocal())
                    Log.d("PlaceRepo", "updatePlace: Successfully updated & saved to DB ${updatedPlaceItem.id}")
                    Result.success(updatedPlaceItem) // Return the updated item from API
                } catch (dbEx: Exception) {
                    Log.e("PlaceRepo", "updatePlace: DB update failed after API success for ${updatedPlaceItem.id}", dbEx)
                    Result.error(AppException.DatabaseException("Failed to save updated place locally", dbEx))
                }
            }
        }
    }

    override suspend fun deletePlace(placeId: String): Result<Unit> {
        Log.d("PlaceRepositoryImpl", "Deleting place ID: $placeId")
        // 1. Get entity from DB to find listId (essential for API call)
        val entity = try {
            withContext(Dispatchers.IO) { placeDao.getPlaceById(placeId) }
        } catch (e: Exception) {
            Log.e("PlaceRepo", "Error fetching place $placeId from DB before delete", e)
            return Result.error(AppException.DatabaseException("Failed to get place $placeId before delete", e))
        }

        if (entity == null) {
            Log.w("PlaceRepo", "deletePlace($placeId): Not found locally. Assuming already deleted.")
            return Result.success(Unit) // Treat as success if already gone locally
        }
        val listId = entity.listId

        // 2. Call API (DELETE /lists/{listId}/places/{placeId})
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("Not authenticated"))
        val authHeader = "Bearer $token"
        // Use the handleApiResult helper
        val apiResult = handleApiResult {
            listApiService.removePlaceFromList(authHeader, listId, placeId)
        }

        // 3. Handle API Result and Update Local State
        return apiResult.flatMap { // If API call was successful (2xx)
            deletePlaceLocally(placeId, listId) // Helper to delete from DB and cache
        }.onError { exception ->
            // Special handling for 404 from API (Resource Not Found)
            if (exception is AppException.ResourceNotFoundException) {
                Log.w("PlaceRepo", "deletePlace($placeId): API returned 404. Deleting locally anyway.")
                deletePlaceLocally(placeId, listId) // Attempt local delete even if API says not found
            } else {
                // Propagate other API errors
                Log.e("PlaceRepo", "API delete failed for place $placeId", exception)
                Result.error(exception)
            }
        }
    }

    // Helper to consolidate local deletion logic
    private suspend fun deletePlaceLocally(placeId: String, listId: String): Result<Unit> {
        return try {
            // Invalidate cache
            cacheManager.cacheData<PlaceItem?>( "$PLACE_ITEM_CACHE_PREFIX$placeId", null, PlaceItem::class.java, -1L )
            // TODO: Invalidate places pager? Needs investigation.

            // Delete from DB
            withContext(Dispatchers.IO) { placeDao.deletePlace(placeId) }
            Log.d("PlaceRepo", "deletePlaceLocally($placeId): Deleted from cache and DB.")
            Result.success(Unit)
        } catch (localEx: Exception) {
            Log.e("PlaceRepo", "deletePlaceLocally($placeId): Local cache/DB delete failed.", localEx)
            Result.error(AppException.DatabaseException("Failed to delete place $placeId locally", localEx))
        }
    }


    override fun getPlaceById(placeId: String): Flow<PlaceItem?> {
        // Map from Entity -> Domain using local mapper or central mapper
        return placeDao.getPlaceByIdFlow(placeId)
            .map { entity -> entity?.toDomainPlaceItemLocal() } // Use specific mapper
            .flowOn(Dispatchers.IO)
    }

    override suspend fun getPlaceItemById(placeId: String): Result<PlaceItem> {
        // ... (Implementation remains the same as previous step - checks cache then DB) ...
        val cacheKey = "$PLACE_ITEM_CACHE_PREFIX$placeId"; val itemType: Type = PlaceItem::class.java
        try { val cached = cacheManager.getCachedData<PlaceItem>(cacheKey, itemType); if (cached != null) return Result.success(cached) } catch (e: Exception) { Log.e("PlaceRepo", "Cache Get Error", e) }
        return try {
            val entity = withContext(Dispatchers.IO) { placeDao.getPlaceById(placeId) }
            if (entity != null) { val domainItem = entity.toDomainPlaceItemLocal(); try { cacheManager.cacheData(cacheKey, domainItem, itemType, PLACE_ITEM_CACHE_EXPIRY_MS) } catch (e: Exception) { Log.e("PlaceRepo", "Cache Put Error", e) }; Result.success(domainItem) }
            else { Log.w("PlaceRepo", "Place $placeId not in DB/Cache."); Result.error(AppException.ResourceNotFoundException("Place $placeId not found")) }
        } catch (e: Exception) { Log.e("PlaceRepo", "Error getting Place $placeId from DB", e); Result.error(AppException.DatabaseException("Failed get Place $placeId", e)) }
    }

    // --- Methods using detailed Place model (Keep as is - likely not implemented) ---
    override suspend fun getPlaceDetails(placeId: String): Result<Place> { Log.e("PlaceRepositoryImpl", "getPlaceDetails NOT IMPLEMENTED"); return Result.error(AppException.UnknownException("Not implemented")) }
    override suspend fun searchPlaces(query: String, location: LatLng, radius: Int): Result<List<Place>> { Log.e("PlaceRepositoryImpl", "searchPlaces NOT IMPLEMENTED"); return Result.error(AppException.UnknownException("Not implemented")) }
    override suspend fun getNearbyPlaces(location: LatLng, radius: Int, type: String?): Result<List<Place>> { Log.e("PlaceRepositoryImpl", "getNearbyPlaces NOT IMPLEMENTED"); return Result.error(AppException.UnknownException("Not implemented")) }
    override suspend fun savePlace(place: Place): Result<String> { Log.e("PlaceRepositoryImpl", "savePlace NOT IMPLEMENTED"); return Result.error(AppException.UnknownException("Not implemented")) }


    // --- vvv NEW Paginated Method Implementation vvv ---
    override fun getPlacesPaginated(listId: String): Flow<PagingData<PlaceItem>> {
        Log.d("PlaceRepositoryImpl", "Creating Pager for getPlacesPaginated for list: $listId")
        if (listId.isBlank()) {
            Log.e("PlaceRepositoryImpl", "Cannot get paginated places for blank listId.")
            return flowOf(PagingData.empty())
        }
        return Pager(
            config = PagingConfig(
                pageSize = PLACES_NETWORK_PAGE_SIZE, // Use constant
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                Log.d("PlaceRepositoryImpl", "Instantiating PlaceInListPagingSource for list: $listId")
                PlaceInListPagingSource(listApiService, tokenProvider, listId)
            }
        ).flow
    }
    // --- ^^^ NEW Paginated Method Implementation ^^^ ---

} // End Class