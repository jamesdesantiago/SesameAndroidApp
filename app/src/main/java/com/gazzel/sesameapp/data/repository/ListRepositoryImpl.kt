// app/src/main/java/com/gazzel/sesameapp/data/repository/ListRepositoryImpl.kt
package com.gazzel.sesameapp.data.repository

import android.util.Log
import com.gazzel.sesameapp.data.manager.ICacheManager
import com.gazzel.sesameapp.data.mapper.toDomainModel
import com.gazzel.sesameapp.data.mapper.toServiceCreateDto
import com.gazzel.sesameapp.data.mapper.toServiceUpdateDto
import com.gazzel.sesameapp.data.remote.ListApiService
import com.gazzel.sesameapp.data.remote.dto.ListDto
import com.gazzel.sesameapp.domain.auth.TokenProvider
import com.gazzel.sesameapp.domain.exception.AppException
import com.gazzel.sesameapp.domain.model.SesameList
import com.gazzel.sesameapp.domain.repository.ListRepository
import com.gazzel.sesameapp.domain.util.Result
import com.gazzel.sesameapp.domain.util.flatMap
import com.gazzel.sesameapp.domain.util.map
import com.gazzel.sesameapp.domain.util.onError
import com.gazzel.sesameapp.domain.util.onSuccess
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.reflect.TypeToken
import retrofit2.Response
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListRepositoryImpl @Inject constructor(
    private val listApiService: ListApiService,
    private val tokenProvider: TokenProvider,
    private val cacheManager: ICacheManager,
    private val firebaseAuth: FirebaseAuth // Inject FirebaseAuth to get user ID for cache invalidation
) : ListRepository {

    // Cache constants
    companion object {
        private val USER_LISTS_CACHE_EXPIRY_MS = TimeUnit.MINUTES.toMillis(15) // 15 minutes
        private val LIST_DETAIL_CACHE_EXPIRY_MS = TimeUnit.HOURS.toMillis(1)    // 1 hour
        private const val USER_LISTS_CACHE_PREFIX = "user_lists_"
        private const val LIST_DETAIL_CACHE_PREFIX = "list_detail_"
        // Type definition for List<SesameList> cache
        private val SESAME_LIST_TYPE: Type = SesameList::class.java
        private val SESAME_LIST_LIST_TYPE: Type = object : TypeToken<List<SesameList>>() {}.type
    }

    // --- Generic API Call Handlers ---
    private suspend fun <Dto, Domain> handleApiCallToDomain(apiCall: suspend (String) -> Response<Dto>, mapper: (Dto) -> Domain): Result<Domain> {
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("User not authenticated"))
        val authHeader = "Bearer $token"
        return try {
            val response = apiCall(authHeader)
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

    private suspend fun <Dto, Domain> handleListApiCallToDomain(apiCall: suspend (String) -> Response<List<Dto>>, mapper: (Dto) -> Domain): Result<List<Domain>> {
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("User not authenticated"))
        val authHeader = "Bearer $token"
        return try {
            val response = apiCall(authHeader)
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

    private suspend fun handleUnitApiCall(apiCall: suspend (String) -> Response<Unit>): Result<Unit> {
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("User not authenticated"))
        val authHeader = "Bearer $token"
        return try {
            val response = apiCall(authHeader)
            if (response.isSuccessful || response.code() == 204) {
                Result.success(Unit)
            } else {
                Result.error(mapErrorToAppException(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Result.error(mapExceptionToAppException(e))
        }
    }
    // --- End API Call Handlers ---


    override suspend fun getUserLists(userId: String): Result<List<SesameList>> {
        val cacheKey = "$USER_LISTS_CACHE_PREFIX$userId"

        // 1. Try cache
        try {
            val cachedLists = cacheManager.getCachedData<List<SesameList>>(cacheKey, SESAME_LIST_LIST_TYPE)
            if (cachedLists != null) {
                Log.d("ListRepositoryImpl", "Cache Hit: Returning cached user lists for $userId")
                return Result.success(cachedLists)
            }
            Log.d("ListRepositoryImpl", "Cache Miss: for user lists $userId")
        } catch (e: Exception) {
            Log.e("ListRepositoryImpl", "Cache Get Error for user lists $userId", e)
        }

        // 2. Fetch from network
        val networkResult = handleListApiCallToDomain(
            apiCall = { authHeader -> listApiService.getUserLists(authHeader, userId = userId) }, // Pass userId if needed by API
            mapper = { listDto -> listDto.toDomainModel() }
        )

        // 3. Cache on success
        networkResult.onSuccess { fetchedLists ->
            try {
                cacheManager.cacheData(cacheKey, fetchedLists, SESAME_LIST_LIST_TYPE, USER_LISTS_CACHE_EXPIRY_MS)
                Log.d("ListRepositoryImpl", "Cached ${fetchedLists.size} user lists for $userId")
            } catch (e: Exception) {
                Log.e("ListRepositoryImpl", "Cache Put Error for user lists $userId", e)
            }
        }
        return networkResult
    }

    override suspend fun getPublicLists(): Result<List<SesameList>> {
        // Caching public lists might need a different strategy or shorter expiry
        // Skipping cache for simplicity for now
        return handleListApiCallToDomain(
            apiCall = { authHeader -> listApiService.getPublicLists(authHeader) }, // Pass auth header if needed
            mapper = { listDto -> listDto.toDomainModel() }
        )
    }

    override suspend fun getListById(id: String): Result<SesameList> {
        val cacheKey = "$LIST_DETAIL_CACHE_PREFIX$id"

        // 1. Try cache
        try {
            val cachedList = cacheManager.getCachedData<SesameList>(cacheKey, SESAME_LIST_TYPE)
            if (cachedList != null) {
                Log.d("ListRepositoryImpl", "Cache Hit: Returning cached list detail for $id")
                return Result.success(cachedList)
            }
            Log.d("ListRepositoryImpl", "Cache Miss: for list detail $id")
        } catch (e: Exception) {
            Log.e("ListRepositoryImpl", "Cache Get Error for list detail $id", e)
        }

        // 2. Fetch from network
        val networkResult = handleApiCallToDomain(
            apiCall = { authHeader -> listApiService.getListDetail(authHeader, id) },
            mapper = { listDto -> listDto.toDomainModel() }
        )

        // 3. Cache on success
        networkResult.onSuccess { fetchedList ->
            try {
                cacheManager.cacheData(cacheKey, fetchedList, SESAME_LIST_TYPE, LIST_DETAIL_CACHE_EXPIRY_MS)
                Log.d("ListRepositoryImpl", "Cached list detail for $id")
            } catch (e: Exception) {
                Log.e("ListRepositoryImpl", "Cache Put Error for list detail $id", e)
            }
        }
        return networkResult
    }

    override suspend fun createList(list: SesameList): Result<SesameList> {
        val listCreateDto = list.toServiceCreateDto()
        val networkResult = handleApiCallToDomain(
            apiCall = { authHeader -> listApiService.createList(authHeader, listCreateDto) },
            mapper = { listDto -> listDto.toDomainModel() }
        )

        networkResult.onSuccess { createdList ->
            val userId = firebaseAuth.currentUser?.uid
            try {
                // Cache new detail
                val detailCacheKey = "$LIST_DETAIL_CACHE_PREFIX${createdList.id}"
                cacheManager.cacheData(detailCacheKey, createdList, SESAME_LIST_TYPE, LIST_DETAIL_CACHE_EXPIRY_MS)

                // Invalidate user lists cache
                if (userId != null) {
                    val userListsCacheKey = "$USER_LISTS_CACHE_PREFIX$userId"
                    invalidateCache(userListsCacheKey) // Use helper
                    Log.d("ListRepositoryImpl", "Invalidated user lists cache after create.")
                } else {
                    Log.w("ListRepositoryImpl", "Could not get userId to invalidate user lists cache after create.")
                }
            } catch (e: Exception) {
                Log.e("ListRepositoryImpl", "Cache Error after creating list ${createdList.id}", e)
            }
        }
        return networkResult
    }

    override suspend fun updateList(list: SesameList): Result<SesameList> {
        val listUpdateDto = list.toServiceUpdateDto()
        val networkResult = handleApiCallToDomain(
            apiCall = { authHeader -> listApiService.updateList(authHeader, list.id, listUpdateDto) },
            mapper = { listDto -> listDto.toDomainModel() }
        )

        networkResult.onSuccess { updatedList ->
            val userId = firebaseAuth.currentUser?.uid
            try {
                // Update detail cache
                val cacheKey = "$LIST_DETAIL_CACHE_PREFIX${updatedList.id}"
                cacheManager.cacheData(cacheKey, updatedList, SESAME_LIST_TYPE, LIST_DETAIL_CACHE_EXPIRY_MS)

                // Invalidate user lists cache
                if (userId != null) {
                    val userListsCacheKey = "$USER_LISTS_CACHE_PREFIX$userId"
                    invalidateCache(userListsCacheKey) // Use helper
                    Log.d("ListRepositoryImpl", "Updated detail cache and invalidated user lists cache for list ${updatedList.id}")
                } else {
                    Log.w("ListRepositoryImpl", "Could not get userId to invalidate user lists cache after update.")
                    Log.d("ListRepositoryImpl", "Updated detail cache for list ${updatedList.id}")
                }
            } catch (e: Exception) {
                Log.e("ListRepositoryImpl", "Cache Error after updating list ${list.id}", e)
            }
        }
        return networkResult
    }

    override suspend fun deleteList(id: String): Result<Unit> {
        val networkResult = handleUnitApiCall { authHeader ->
            listApiService.deleteList(authHeader, id)
        }

        networkResult.onSuccess {
            val userId = firebaseAuth.currentUser?.uid
            try {
                // Invalidate detail cache
                val detailCacheKey = "$LIST_DETAIL_CACHE_PREFIX$id"
                invalidateCache(detailCacheKey) // Use helper

                // Invalidate user lists cache
                if (userId != null) {
                    val userListsCacheKey = "$USER_LISTS_CACHE_PREFIX$userId"
                    invalidateCache(userListsCacheKey) // Use helper
                    Log.d("ListRepositoryImpl", "Invalidated detail and user lists cache for list $id after deletion.")
                } else {
                    Log.w("ListRepositoryImpl", "Could not get userId to invalidate user lists cache after delete.")
                    Log.d("ListRepositoryImpl", "Invalidated detail cache for list $id after deletion.")
                }
            } catch (e: Exception) {
                Log.e("ListRepositoryImpl", "Cache Invalidation Error after deleting list $id", e)
            }
        }
        return networkResult
    }

    override suspend fun getRecentLists(limit: Int): Result<List<SesameList>> {
        return handleListApiCallToDomain(
            apiCall = { authHeader -> listApiService.getRecentLists(authHeader, limit) },
            mapper = { listDto -> listDto.toDomainModel() }
        )
    }

    override suspend fun searchLists(query: String): Result<List<SesameList>> {
        return handleListApiCallToDomain(
            apiCall = { authHeader -> listApiService.searchLists(authHeader, query) },
            mapper = { listDto -> listDto.toDomainModel() }
        )
    }

    // Method not supported
    override suspend fun addPlaceToList(listId: String, placeId: String): Result<Unit> {
        Log.e("ListRepositoryImpl", "addPlaceToList(listId, placeId) is not supported.")
        return Result.error(AppException.UnknownException("Operation addPlaceToList(listId, placeId) not supported."))
    }

    override suspend fun removePlaceFromList(listId: String, placeId: String): Result<Unit> {
        val networkResult = handleUnitApiCall { authHeader ->
            listApiService.removePlaceFromList(authHeader, listId, placeId)
        }
        networkResult.onSuccess {
            try {
                // Invalidate list detail cache (contains places)
                val detailCacheKey = "$LIST_DETAIL_CACHE_PREFIX$listId"
                invalidateCache(detailCacheKey) // Use helper

                // Invalidate list places cache (used by PlaceRepositoryImpl.getPlacesByListId)
                val listPlacesCacheKey = "list_places_$listId"
                invalidateCache(listPlacesCacheKey) // Use helper

                Log.d("ListRepositoryImpl", "Invalidated detail and list places cache for list $listId after removing place $placeId.")
            } catch (e: Exception) {
                Log.e("ListRepositoryImpl", "Cache Invalidation Error after removing place $placeId from list $listId", e)
            }
        }
        return networkResult
    }

    override suspend fun followList(listId: String): Result<Unit> {
        // TODO: Potentially invalidate list detail cache if follower count is shown/cached
        return handleUnitApiCall { authHeader ->
            listApiService.followList(authHeader, listId)
        }
    }

    override suspend fun unfollowList(listId: String): Result<Unit> {
        // TODO: Potentially invalidate list detail cache if follower count is shown/cached
        return handleUnitApiCall { authHeader ->
            listApiService.unfollowList(authHeader, listId)
        }
    }

    // --- Cache Invalidation Helper ---
    private suspend fun invalidateCache(key: String) {
        try {
            // Invalidate by setting data to null with immediate expiry (-1)
            // We need a type, but it doesn't matter what since data is null. Use Any?
            cacheManager.cacheData<Any?>(key, null, Any::class.java, -1L)
        } catch (e: Exception) {
            Log.e("ListRepositoryImpl", "Failed to invalidate cache key '$key'", e)
        }
    }

    // --- Error Mapping Helpers (Keep as they are) ---
    private fun mapErrorToAppException(code: Int, errorBody: String?): AppException {
        Log.e("ListRepositoryImpl", "API Error $code: ${errorBody ?: "Unknown error"}")
        return when (code) {
            400 -> AppException.ValidationException(errorBody ?: "Bad Request")
            401, 403 -> AppException.AuthException(errorBody ?: "Authorization failed") // Combine 401 and 403
            404 -> AppException.ResourceNotFoundException(errorBody ?: "Not Found")
            in 500..599 -> AppException.NetworkException("Server Error ($code): ${errorBody ?: ""}", code)
            else -> AppException.NetworkException("Network Error ($code): ${errorBody ?: ""}", code)
        }
    }

    private fun mapExceptionToAppException(e: Exception): AppException {
        Log.e("ListRepositoryImpl", "Network/Unknown error: ${e.message ?: "Unknown exception"}", e)
        return when(e) {
            is java.io.IOException -> AppException.NetworkException("Network IO error: ${e.message}", cause = e)
            is retrofit2.HttpException -> mapErrorToAppException(e.code(), e.response()?.errorBody()?.string() ?: e.message())
            is AppException -> e // Don't re-wrap existing AppExceptions
            else -> AppException.UnknownException(e.message ?: "An unknown error occurred", e)
        }
    }
}