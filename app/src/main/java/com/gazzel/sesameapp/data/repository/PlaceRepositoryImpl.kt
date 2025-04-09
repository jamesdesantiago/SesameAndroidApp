// data/repository/PlaceRepositoryImpl.kt
package com.gazzel.sesameapp.data.repository

import android.util.Log
import com.gazzel.sesameapp.data.local.dao.PlaceDao
import com.gazzel.sesameapp.data.local.entity.PlaceEntity
import com.gazzel.sesameapp.data.manager.ICacheManager // <<< Import
import com.gazzel.sesameapp.data.remote.ListApiService
import com.gazzel.sesameapp.data.remote.dto.ListDto
import com.gazzel.sesameapp.data.remote.dto.PlaceCreateDto
import com.gazzel.sesameapp.data.remote.dto.PlaceDto
import com.gazzel.sesameapp.data.remote.dto.PlaceUpdateDto
import com.gazzel.sesameapp.domain.auth.TokenProvider
import com.gazzel.sesameapp.domain.exception.AppException
import com.gazzel.sesameapp.domain.model.Place
import com.gazzel.sesameapp.domain.model.PlaceItem
import com.gazzel.sesameapp.domain.repository.PlaceRepository
import com.gazzel.sesameapp.domain.util.Result
import com.gazzel.sesameapp.domain.util.flatMap // Import if needed
import com.gazzel.sesameapp.domain.util.map
import com.gazzel.sesameapp.domain.util.onError
import com.gazzel.sesameapp.domain.util.onSuccess
import com.google.android.gms.maps.model.LatLng
import com.google.gson.reflect.TypeToken // <<< Import
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map as flowMap
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.lang.reflect.Type // <<< Import
import java.util.concurrent.TimeUnit // <<< Import
import javax.inject.Inject
import javax.inject.Singleton

// --- Mappers (keep as they are) ---
private fun PlaceDto.toPlaceItem(listId: String): PlaceItem { /* ... */ }
private fun PlaceEntity.toDomainPlaceItem(): PlaceItem { /* ... */ }
private fun PlaceItem.toPlaceEntity(): PlaceEntity { /* ... */ }
// --- End Mappers ---

@Singleton
class PlaceRepositoryImpl @Inject constructor(
    private val listApiService: ListApiService,
    private val placeDao: PlaceDao,
    private val tokenProvider: TokenProvider,
    private val cacheManager: ICacheManager // <<< Inject CacheManager
) : PlaceRepository {

    // Cache constants
    companion object {
        // Potentially adjust expiry based on how often place details change
        private val PLACE_ITEM_CACHE_EXPIRY_MS = TimeUnit.HOURS.toMillis(1) // 1 hour
        private const val PLACE_ITEM_CACHE_PREFIX = "place_item_"
    }

    // --- API Call Handlers (Assume these exist and are correct) ---
    private suspend fun <Dto, Domain> handleApiCallToDomain(apiCall: suspend () -> Response<Dto>, mapper: (Dto) -> Domain): Result<Domain> { /* ... */ }
    private suspend fun <Dto, Domain> handleListApiCallToDomain(apiCall: suspend () -> Response<List<Dto>>, mapper: (Dto) -> Domain): Result<List<Domain>> { /* ... */ }
    private suspend fun handleUnitApiCall(apiCall: suspend () -> Response<Unit>): Result<Unit> { /* ... */ }
    private fun mapErrorToAppException(code: Int, errorBody: String?): AppException { /* ... */ } // Renamed from mapError
    private fun mapExceptionToAppException(e: Exception): AppException { /* ... */ } // Renamed from mapException
    // --- End API Call Handlers ---

    // --- Existing Methods (Add cache invalidation) ---

    override suspend fun getPlaces(): Result<List<PlaceItem>> {
        // Caching the result of combining all list details is complex.
        // Current implementation fetches fresh data but caches individual list places.
        // Let's stick to caching individual items/lists for now.
        // This method primarily relies on getPlacesByListId indirectly.
        return try {
            // Fetch all user lists
            val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("Not authenticated"))
            // Use handleListApiCallToDomain helper which already includes token logic
            val listsResult = handleListApiCallToDomain(
                apiCall = { listApiService.getUserLists("Bearer $token") },
                mapper = { listDto: ListDto -> listDto.id } // Just need IDs
            )

            listsResult.flatMap { listIds ->
                val allPlaceItems = mutableListOf<PlaceItem>()
                var firstError: AppException? = null

                for (listId in listIds) {
                    // Use getPlacesByListId which includes caching
                    val placesInListResult = getPlacesByListId(listId)
                    placesInListResult.onSuccess { items ->
                        allPlaceItems.addAll(items)
                    }.onError { error ->
                        Log.w("PlaceRepo", "getPlaces: Failed fetch details for list $listId: ${error.message}")
                        if (firstError == null) firstError = error
                    }
                }

                // Process results
                if (firstError != null && allPlaceItems.isEmpty()) {
                    Result.error(firstError!!) // Return error only if nothing was fetched
                } else {
                    // Don't cache the combined result here, rely on individual list/item caches
                    Log.d("PlaceRepo", "getPlaces: Total places fetched across lists: ${allPlaceItems.size}")
                    Result.success(allPlaceItems)
                }
            }
        } catch (e: Exception) {
            Log.e("PlaceRepo", "getPlaces: General exception", e)
            Result.error(mapExceptionToAppException(e))
        }
    }

    override suspend fun getPlacesByListId(listId: String): Result<List<PlaceItem>> {
        // Caching entire list's places might be okay if lists aren't huge
        val cacheKey = "list_places_$listId"
        val listType: Type = object : TypeToken<List<PlaceItem>>() {}.type

        // 1. Try Cache
        try {
            val cachedItems = cacheManager.getCachedData<List<PlaceItem>>(cacheKey, listType)
            if (cachedItems != null) {
                Log.d("PlaceRepo", "Cache Hit: Returning cached places for list $listId")
                return Result.success(cachedItems)
            }
            Log.d("PlaceRepo", "Cache Miss: places for list $listId")
        } catch (e: Exception) {
            Log.e("PlaceRepo", "Cache Get Error for list places $listId", e)
        }

        // 2. Fetch List Details from API
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("Not authenticated"))
        // Use the existing helper
        val detailsResult: Result<ListDto> = handleApiCallToDomain(
            apiCall = { listApiService.getListDetail("Bearer $token", listId) },
            mapper = { listDto: ListDto -> listDto } // Map to self
        )

        return detailsResult.map { listDetailDto -> // Use map extension on Result
            val placesDtoList = listDetailDto.places.orEmpty()
            val placeItems = placesDtoList.map { it.toPlaceItem(listId) } // DTO -> PlaceItem

            // 3. Cache results
            try {
                cacheManager.cacheData(cacheKey, placeItems, listType, LIST_DETAIL_CACHE_EXPIRY_MS) // Use constant
                Log.d("PlaceRepo", "Cached ${placeItems.size} places for list $listId")
            } catch (e: Exception) {
                Log.e("PlaceRepo", "Cache Put Error for list places $listId", e)
            }
            placeItems // Return the list of PlaceItems from map
        }
    }

    override suspend fun addPlace(place: PlaceItem): Result<PlaceItem> {
        val placeCreateDto = PlaceCreateDto(
            placeId = place.id, name = place.name, address = place.address,
            latitude = place.latitude, longitude = place.longitude, rating = place.rating,
        )
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("Not authenticated"))

        val apiResult = handleUnitApiCall {
            listApiService.addPlace(authorization = "Bearer $token", listId = place.listId, place = placeCreateDto)
        }

        return apiResult.flatMap {
            // Invalidate list places cache and potentially single item cache if it exists
            try {
                cacheManager.cacheData<List<PlaceItem>?>( // Invalidate list cache
                    "list_places_${place.listId}", null, object : TypeToken<List<PlaceItem>>() {}.type, -1L
                )
                // Optionally pre-cache the newly added item detail
                // cacheManager.cacheData(PLACE_ITEM_CACHE_PREFIX + place.id, place, PlaceItem::class.java, PLACE_ITEM_CACHE_EXPIRY_MS)
            } catch (e: Exception) { Log.e("PlaceRepo", "Cache invalidation error after adding place ${place.id}", e) }

            // Save to local DB *after* successful API call
            withContext(Dispatchers.IO) {
                try {
                    placeDao.insertPlace(place.toPlaceEntity())
                    Log.d("PlaceRepo", "addPlace: Successfully added & saved to DB ${place.id}")
                    Result.success(place) // Return the original item
                } catch (dbEx: Exception) {
                    Log.e("PlaceRepo", "addPlace: DB insert failed after API success for ${place.id}", dbEx)
                    Result.error(AppException.DatabaseException("Failed to save added place locally", dbEx))
                }
            }
        }
    }

    override suspend fun updatePlace(place: PlaceItem): Result<PlaceItem> {
        val placeUpdateDto = PlaceUpdateDto(
            name = place.name, address = place.address, rating = place.rating, notes = place.notes
        )
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("Not authenticated"))

        val apiResult = handleUnitApiCall {
            listApiService.updatePlace(authorization = "Bearer $token", listId = place.listId, placeId = place.id, update = placeUpdateDto)
        }

        return apiResult.flatMap {
            // Invalidate relevant caches
            try {
                cacheManager.cacheData<List<PlaceItem>?>( // Invalidate list cache
                    "list_places_${place.listId}", null, object : TypeToken<List<PlaceItem>>() {}.type, -1L
                )
                // Update single item cache
                cacheManager.cacheData(
                    "$PLACE_ITEM_CACHE_PREFIX${place.id}", place, PlaceItem::class.java, PLACE_ITEM_CACHE_EXPIRY_MS
                )
                Log.d("PlaceRepo", "Invalidated/Updated caches for place ${place.id}")
            } catch (e: Exception) { Log.e("PlaceRepo", "Cache invalidation error after updating place ${place.id}", e) }

            // Update local DB
            withContext(Dispatchers.IO) {
                try {
                    placeDao.updatePlace(place.toPlaceEntity())
                    Log.d("PlaceRepo", "updatePlace: Successfully updated & saved to DB ${place.id}")
                    Result.success(place) // Return the updated item
                } catch (dbEx: Exception) {
                    Log.e("PlaceRepo", "updatePlace: DB update failed after API success for ${place.id}", dbEx)
                    Result.error(AppException.DatabaseException("Failed to save updated place locally", dbEx))
                }
            }
        }
    }

    override suspend fun deletePlace(placeId: String): Result<Unit> {
        // 1. Get entity to find listId (needed for API call and cache invalidation)
        val entity = try {
            withContext(Dispatchers.IO) { placeDao.getPlaceById(placeId) }
        } catch (e: Exception) {
            Log.e("PlaceRepo", "Error fetching place $placeId from DB before delete", e)
            return Result.error(AppException.DatabaseException("Failed to get place $placeId before delete", e))
        }

        if (entity == null) {
            Log.w("PlaceRepo", "deletePlace($placeId): Not found locally. Assuming success.")
            return Result.success(Unit) // Already gone
        }
        val listId = entity.listId // Get listId from the entity

        // 2. Call API
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("Not authenticated"))
        val apiResult = handleUnitApiCall {
            listApiService.removePlaceFromList(authorization = "Bearer $token", listId = listId, placeId = placeId)
        }

        // 3. Handle API Result and Update Local State
        return apiResult.flatMap {
            // Invalidate caches and delete from DB even if API failed with 404
            try {
                cacheManager.cacheData<List<PlaceItem>?>( // Invalidate list cache
                    "list_places_$listId", null, object : TypeToken<List<PlaceItem>>() {}.type, -1L
                )
                cacheManager.cacheData<PlaceItem?>( // Invalidate item cache
                    "$PLACE_ITEM_CACHE_PREFIX$placeId", null, PlaceItem::class.java, -1L
                )
                withContext(Dispatchers.IO) { placeDao.deletePlace(placeId) } // Delete from DB
                Log.d("PlaceRepo", "deletePlace($placeId): Successfully deleted from cache and DB after API success.")
                Result.success(Unit)
            } catch (localEx: Exception) {
                Log.e("PlaceRepo", "deletePlace($placeId): Local cache/DB delete failed after API success.", localEx)
                Result.error(AppException.DatabaseException("Failed to delete place $placeId locally", localEx))
            }
        }.onError { exception ->
            if (exception is AppException.ResourceNotFoundException) {
                // API said not found, still try to delete locally & invalidate cache
                Log.w("PlaceRepo", "deletePlace($placeId): API returned 404. Attempting local delete & cache invalidation.")
                try {
                    cacheManager.cacheData<List<PlaceItem>?>( "list_places_$listId", null, object : TypeToken<List<PlaceItem>>() {}.type, -1L )
                    cacheManager.cacheData<PlaceItem?>( "$PLACE_ITEM_CACHE_PREFIX$placeId", null, PlaceItem::class.java, -1L )
                    withContext(Dispatchers.IO) { placeDao.deletePlace(placeId) }
                    Result.success(Unit) // Treat as overall success
                } catch (localEx: Exception) {
                    Log.e("PlaceRepo", "deletePlace($placeId): Local cache/DB delete failed after API 404.", localEx)
                    Result.error(AppException.DatabaseException("Failed to delete place $placeId locally after 404", localEx))
                }
            } else {
                Result.error(exception) // Propagate other API errors
            }
        }
    }

    // --- Flow method (Stays the same) ---
    override fun getPlaceById(placeId: String): Flow<PlaceItem?> {
        return placeDao.getPlaceByIdFlow(placeId)
            .flowMap { entity -> entity?.toDomainPlaceItem() }
            .flowOn(Dispatchers.IO)
    }

    // --- ADDED: Implementation for getPlaceItemById ---
    override suspend fun getPlaceItemById(placeId: String): Result<PlaceItem> {
        val cacheKey = "$PLACE_ITEM_CACHE_PREFIX$placeId"
        val itemType: Type = PlaceItem::class.java

        // 1. Try Cache
        try {
            val cachedItem = cacheManager.getCachedData<PlaceItem>(cacheKey, itemType)
            if (cachedItem != null) {
                Log.d("PlaceRepo", "Cache Hit: Returning cached PlaceItem for $placeId")
                return Result.success(cachedItem)
            }
            Log.d("PlaceRepo", "Cache Miss: PlaceItem for $placeId")
        } catch (e: Exception) {
            Log.e("PlaceRepo", "Cache Get Error for PlaceItem $placeId", e)
        }

        // 2. Try Local DB (Primary source for single item fetch if cache misses)
        return try {
            val entity = withContext(Dispatchers.IO) { placeDao.getPlaceById(placeId) }
            if (entity != null) {
                Log.d("PlaceRepo", "DB Hit: Returning PlaceItem $placeId from local DB.")
                val domainItem = entity.toDomainPlaceItem()
                // Cache the result from DB
                try { cacheManager.cacheData(cacheKey, domainItem, itemType, PLACE_ITEM_CACHE_EXPIRY_MS) }
                catch (e: Exception) { Log.e("PlaceRepo", "Cache Put Error for PlaceItem $placeId from DB", e) }
                Result.success(domainItem)
            } else {
                // Item not found locally. Should we try network?
                // For updating notes, it usually implies the item exists.
                // If network fetch is desired:
                //  - Need the listId. How to get it? Requires rethinking flow or different endpoint.
                //  - Call getListDetail, find the place, cache it, return.
                // For now, return Not Found if not in DB/Cache.
                Log.w("PlaceRepo", "PlaceItem $placeId not found in DB or Cache.")
                Result.error(AppException.ResourceNotFoundException("PlaceItem with ID $placeId not found"))
            }
        } catch (e: Exception) {
            Log.e("PlaceRepo", "Error getting PlaceItem $placeId from DB", e)
            Result.error(AppException.DatabaseException("Failed to get PlaceItem $placeId", e))
        }
    }


    // --- Methods using detailed Place model (Keep as NOT IMPLEMENTED) ---
    override suspend fun getPlaceDetails(placeId: String): Result<Place> { /* ... */ }
    override suspend fun searchPlaces(query: String, location: LatLng, radius: Int): Result<List<Place>> { /* ... */ }
    override suspend fun getNearbyPlaces(location: LatLng, radius: Int, type: String?): Result<List<Place>> { /* ... */ }
    override suspend fun savePlace(place: Place): Result<String> { /* ... */ }

}