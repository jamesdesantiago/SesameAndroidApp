// app/src/main/java/com/gazzel/sesameapp/data/repository/ListRepositoryImpl.kt
package com.gazzel.sesameapp.data.repository

// Import the specific mappers
import com.gazzel.sesameapp.data.mapper.toDomainModel
import com.gazzel.sesameapp.data.mapper.toServiceCreateDto
import com.gazzel.sesameapp.data.mapper.toServiceUpdateDto

import com.gazzel.sesameapp.data.service.AppListService
import com.gazzel.sesameapp.domain.exception.AppException
import com.gazzel.sesameapp.domain.model.SesameList
// Use the correct domain models/DTOs where the interface expects them
// Remove imports for service DTOs here if they are only used via mappers
// import com.gazzel.sesameapp.domain.model.ListCreate
// import com.gazzel.sesameapp.domain.model.ListUpdate
import com.gazzel.sesameapp.domain.model.ListResponse // Keep if used within repo logic
import com.gazzel.sesameapp.domain.repository.ListRepository
import com.gazzel.sesameapp.domain.util.Result
import com.gazzel.sesameapp.domain.util.map
import com.gazzel.sesameapp.domain.util.onSuccess
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

// TODO: Inject Auth Token provider
suspend fun getAuthToken(): String {
    println("WARNING: Using DUMMY_AUTH_TOKEN in ListRepositoryImpl")
    return "DUMMY_AUTH_TOKEN"
}

@Singleton
class ListRepositoryImpl @Inject constructor(
    private val appListService: AppListService
    // TODO: Inject Dao, AuthManager
) : ListRepository {

    // --- Overload 1: For calls expecting a non-Unit result ---
    private suspend fun <T, R> handleApiCall(
        apiCall: suspend () -> Response<T>,
        mapper: (T) -> R // Maps API response type T to Domain type R
    ): Result<R> {
        return try {
            val response = apiCall()
            if (response.isSuccessful) {
                response.body()?.let { body ->
                    Result.success(mapper(body)) // Apply mapper here
                } ?: Result.error(AppException.UnknownException("API success but response body was null when non-Unit expected"))
                // Removed the `Unit is R` check entirely
            } else {
                Result.error(mapError(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Result.error(mapException(e))
        }
    }

    // --- Overload 2: For calls expecting Unit result (e.g., DELETE, simple POST/PUT) ---
    private suspend fun handleUnitApiCall(
        apiCall: suspend () -> Response<Unit>
    ): Result<Unit> {
        return try {
            val response = apiCall()
            if (response.isSuccessful) {
                // Success even if body is null for Unit response (like 204 No Content)
                Result.success(Unit)
            } else {
                Result.error(mapError(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Result.error(mapException(e))
        }
    }


    // --- Implement ListRepository methods ---

    override suspend fun getUserLists(userId: String): Result<List<SesameList>> {
        val token = getAuthToken()
        // Use the regular handleApiCall, as R is List<SesameList> (not Unit)
        val apiResult: Result<List<ListResponse>> = handleApiCall(
            apiCall = { appListService.getUserLists("Bearer $token") },
            mapper = { listResponses -> listResponses }
        )
        return apiResult.map { responseList ->
            responseList.map { it.toDomainModel() }
        }
    }

    override suspend fun getPublicLists(): Result<List<SesameList>> {
        val token = getAuthToken()
        // Use the regular handleApiCall
        val apiResult: Result<List<ListResponse>> = handleApiCall(
            apiCall = { appListService.getPublicLists("Bearer $token") },
            mapper = { listResponses -> listResponses }
        )
        return apiResult.map { responseList ->
            responseList.map { it.toDomainModel() }
        }
    }

    override suspend fun getListById(id: String): Result<SesameList> {
        val token = getAuthToken()
        // Use the regular handleApiCall
        val apiResult: Result<ListResponse> = handleApiCall(
            apiCall = { appListService.getListDetail("Bearer $token", id) },
            mapper = { listResponse -> listResponse }
        )
        return apiResult.map { it.toDomainModel() }.onSuccess { /* caching */ }
    }

    override suspend fun createList(list: SesameList): Result<SesameList> {
        val token = getAuthToken()
        val listCreateDto = list.toServiceCreateDto()
        // Use the regular handleApiCall
        val apiResult: Result<ListResponse> = handleApiCall(
            apiCall = { appListService.createList("Bearer $token", listCreateDto) },
            mapper = { createdListResponse -> createdListResponse }
        )
        return apiResult.map { it.toDomainModel() }.onSuccess { /* caching */ }
    }

    override suspend fun updateList(list: SesameList): Result<SesameList> {
        val token = getAuthToken()
        val listUpdateDto = list.toServiceUpdateDto()
        // Use the regular handleApiCall
        val apiResult: Result<ListResponse> = handleApiCall(
            apiCall = { appListService.updateList("Bearer $token", list.id, listUpdateDto) },
            mapper = { updatedListResponse -> updatedListResponse }
        )
        return apiResult.map { it.toDomainModel() }.onSuccess { /* caching */ }
    }

    override suspend fun deleteList(id: String): Result<Unit> {
        val token = getAuthToken()
        // Use the SPECIFIC handleUnitApiCall because R is Unit
        return handleUnitApiCall {
            appListService.deleteList("Bearer $token", id)
        }.onSuccess { /* caching */ }
    }

    override suspend fun getRecentLists(limit: Int): Result<List<SesameList>> {
        val token = getAuthToken()
        // Use the regular handleApiCall
        val apiResult: Result<List<ListResponse>> = handleApiCall(
            apiCall = { appListService.getRecentLists("Bearer $token", limit) },
            mapper = { listResponses -> listResponses }
        )
        return apiResult.map { responseList ->
            responseList.map { it.toDomainModel() }
        }
    }

    override suspend fun searchLists(query: String): Result<List<SesameList>> {
        if (query.isBlank()) { return getRecentLists() }
        val token = getAuthToken()
        // Use the regular handleApiCall
        val apiResult: Result<List<ListResponse>> = handleApiCall(
            apiCall = { appListService.searchLists("Bearer $token", query) },
            mapper = { listResponses -> listResponses }
        )
        return apiResult.map { responseList ->
            responseList.map { it.toDomainModel() }
        }
    }

    override suspend fun addPlaceToList(listId: String, placeId: String): Result<Unit> {
        // Use the SPECIFIC handleUnitApiCall if this endpoint returns Unit
        return Result.error(AppException.UnknownException("addPlaceToList (linking) not implemented via API yet"))
        /*
        val token = getAuthToken()
        return handleUnitApiCall {
            appListService.linkPlaceToList("Bearer $token", listId, placeId)
        }
        */
    }

    override suspend fun removePlaceFromList(listId: String, placeId: String): Result<Unit> {
        val token = getAuthToken()
        // Use the SPECIFIC handleUnitApiCall
        return handleUnitApiCall {
            appListService.removePlaceFromList("Bearer $token", listId, placeId)
        }.onSuccess { /* caching */ }
    }

    override suspend fun followList(listId: String): Result<Unit> {
        val token = getAuthToken()
        // Use the SPECIFIC handleUnitApiCall
        return handleUnitApiCall {
            appListService.followList("Bearer $token", listId)
        }
    }

    override suspend fun unfollowList(listId: String): Result<Unit> {
        val token = getAuthToken()
        // Use the SPECIFIC handleUnitApiCall
        return handleUnitApiCall {
            appListService.unfollowList("Bearer $token", listId)
        }
    }

    // --- Error Mapping Helpers ---
    private fun mapError(code: Int, errorBody: String?): AppException {
        // ... (implementation remains the same) ...
        return when (code) {
            400 -> AppException.ValidationException(errorBody ?: "Bad Request")
            401 -> AppException.AuthException(errorBody ?: "Unauthorized")
            403 -> AppException.AuthException(errorBody ?: "Forbidden")
            404 -> AppException.ResourceNotFoundException(errorBody ?: "Not Found")
            in 500..599 -> AppException.NetworkException("Server Error ($code): ${errorBody ?: ""}", code)
            else -> AppException.NetworkException("Network Error ($code): ${errorBody ?: ""}", code)
        }
    }

    private fun mapException(e: Exception): AppException {
        // ... (implementation remains the same) ...
        return when(e) {
            is java.io.IOException -> AppException.NetworkException("Network IO error: ${e.message}", cause = e)
            else -> AppException.UnknownException(e.message ?: "An unknown error occurred", e)
        }
    }
}