// app/src/main/java/com/gazzel/sesameapp/data/repository/ListRepositoryImpl.kt
package com.gazzel.sesameapp.data.repository

// Import the CORRECT consolidated service
import com.gazzel.sesameapp.data.remote.ListApiService // <<< CHANGE

// Import specific DTOs needed for requests

// Import specific response DTOs
import com.gazzel.sesameapp.data.remote.dto.ListDto // <<< CHANGE (Assuming this is the response DTO used by ListApiService)

// Import domain models
import com.gazzel.sesameapp.domain.model.SesameList

// Import mappers (Ensure these exist and are correct)
import com.gazzel.sesameapp.data.mapper.toDomainModel // Maps ListDto -> SesameList
import com.gazzel.sesameapp.data.mapper.toServiceCreateDto // Maps SesameList -> ListCreateDto
import com.gazzel.sesameapp.data.mapper.toServiceUpdateDto // Maps SesameList -> ListUpdateDto

// Other necessary imports
import com.gazzel.sesameapp.domain.auth.TokenProvider // <<< ADD for proper token handling
import com.gazzel.sesameapp.domain.exception.AppException
import com.gazzel.sesameapp.domain.repository.ListRepository
import com.gazzel.sesameapp.domain.util.Result
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log // For logging

@Singleton
class ListRepositoryImpl @Inject constructor(
    private val listApiService: ListApiService, // <<< CHANGE Service type
    private val tokenProvider: TokenProvider // <<< INJECT TokenProvider
    // TODO: Inject Dao if caching is needed
) : ListRepository {

    // --- Generic API Call Handlers (Adjusted for DTO mapping) ---

    // Handles API calls expecting a non-Unit DTO response, mapping it to a Domain model
    private suspend fun <Dto, Domain> handleApiCallToDomain(
        apiCall: suspend () -> Response<Dto>,
        mapper: (Dto) -> Domain // Maps API DTO response to Domain model
    ): Result<Domain> {
        val token = tokenProvider.getToken()
        if (token == null) {
            Log.w("ListRepositoryImpl", "Auth token is null, cannot make API call.")
            return Result.error(AppException.AuthException("User not authenticated"))
        }
        val authorizationHeader = "Bearer $token" // Prepare header once

        return try {
            // Pass the header or required token format to the actual API call lambda if needed
            // Assuming the apiCall lambda itself handles using the token via closure or direct passing
            Log.d("ListRepositoryImpl", "Executing API call...")
            val response = apiCall() // Execute the actual Retrofit call
            Log.d("ListRepositoryImpl", "API Response Code: ${response.code()}")

            if (response.isSuccessful) {
                response.body()?.let { body ->
                    Result.success(mapper(body)) // Apply mapper here
                } ?: Result.error(AppException.UnknownException("API success but response body was null"))
            } else {
                Result.error(mapError(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Log.e("ListRepositoryImpl", "API call exception", e)
            Result.error(mapException(e))
        }
    }

    // Handles API calls expecting a List of DTOs response, mapping it to a List of Domain models
    private suspend fun <Dto, Domain> handleListApiCallToDomain(
        apiCall: suspend () -> Response<List<Dto>>,
        mapper: (Dto) -> Domain // Maps a single DTO item to a Domain model item
    ): Result<List<Domain>> {
        val token = tokenProvider.getToken()
        if (token == null) {
            Log.w("ListRepositoryImpl", "Auth token is null, cannot make API call.")
            return Result.error(AppException.AuthException("User not authenticated"))
        }
        val authorizationHeader = "Bearer $token"

        return try {
            Log.d("ListRepositoryImpl", "Executing List API call...")
            val response = apiCall() // Execute the actual Retrofit call
            Log.d("ListRepositoryImpl", "API List Response Code: ${response.code()}")

            if (response.isSuccessful) {
                // Map the list, handling null body gracefully
                val domainList = response.body()?.map(mapper) ?: emptyList()
                Result.success(domainList)
            } else {
                Result.error(mapError(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Log.e("ListRepositoryImpl", "API List call exception", e)
            Result.error(mapException(e))
        }
    }


    // Handles API calls expecting Unit response (e.g., DELETE, simple POST/PUT)
    private suspend fun handleUnitApiCall(
        apiCall: suspend () -> Response<Unit>
    ): Result<Unit> {
        val token = tokenProvider.getToken()
        if (token == null) {
            Log.w("ListRepositoryImpl", "Auth token is null, cannot make API call.")
            return Result.error(AppException.AuthException("User not authenticated"))
        }
        val authorizationHeader = "Bearer $token"

        return try {
            Log.d("ListRepositoryImpl", "Executing Unit API call...")
            val response = apiCall() // Execute the actual Retrofit call
            Log.d("ListRepositoryImpl", "API Unit Response Code: ${response.code()}")
            if (response.isSuccessful || response.code() == 204) { // Allow 204 No Content
                Result.success(Unit)
            } else {
                Result.error(mapError(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Log.e("ListRepositoryImpl", "API Unit call exception", e)
            Result.error(mapException(e))
        }
    }

    // --- Implement ListRepository methods using new helpers and ListApiService ---

    override suspend fun getUserLists(userId: String): Result<List<SesameList>> {
        // Note: userId is not used if API relies solely on token
        return handleListApiCallToDomain(
            // Pass the actual service call lambda
            apiCall = { listApiService.getUserLists("Bearer ${tokenProvider.getToken()!!}") }, // Assuming non-null token here after check in helper
            mapper = { listDto: ListDto -> listDto.toDomainModel() } // Provide the mapping function
        )
        // .onSuccess { /* caching logic */ } // Keep caching if needed
    }

    override suspend fun getPublicLists(): Result<List<SesameList>> {
        return handleListApiCallToDomain(
            apiCall = { listApiService.getPublicLists("Bearer ${tokenProvider.getToken()!!}") }, // Adjust token usage if needed for public
            mapper = { listDto -> listDto. toDomainModel() }
        )
    }

    override suspend fun getListById(id: String): Result<SesameList> {
        return handleApiCallToDomain(
            apiCall = { listApiService.getListDetail("Bearer ${tokenProvider.getToken()!!}", id) },
            mapper = { listDto -> listDto.toDomainModel() }
        )
        // .onSuccess { /* caching logic */ }
    }

    override suspend fun createList(list: SesameList): Result<SesameList> {
        val listCreateDto = list.toServiceCreateDto() // Map Domain -> Request DTO
        return handleApiCallToDomain(
            apiCall = { listApiService.createList("Bearer ${tokenProvider.getToken()!!}", listCreateDto) },
            mapper = { listDto -> listDto.toDomainModel() } // Map Response DTO -> Domain
        )
        // .onSuccess { /* caching logic */ }
    }

    override suspend fun updateList(list: SesameList): Result<SesameList> {
        val listUpdateDto = list.toServiceUpdateDto() // Map Domain -> Request DTO
        return handleApiCallToDomain(
            apiCall = { listApiService.updateList("Bearer ${tokenProvider.getToken()!!}", list.id, listUpdateDto) },
            mapper = { listDto -> listDto.toDomainModel() } // Map Response DTO -> Domain
        )
        // .onSuccess { /* caching logic */ }
    }

    override suspend fun deleteList(id: String): Result<Unit> {
        return handleUnitApiCall {
            listApiService.deleteList("Bearer ${tokenProvider.getToken()!!}", id)
        }
        // .onSuccess { /* caching logic */ }
    }

    override suspend fun getRecentLists(limit: Int): Result<List<SesameList>> {
        return handleListApiCallToDomain(
            apiCall = { listApiService.getRecentLists("Bearer ${tokenProvider.getToken()!!}", limit) },
            mapper = { listDto -> listDto.toDomainModel() }
        )
    }

    override suspend fun searchLists(query: String): Result<List<SesameList>> {
        // Handle blank query locally or let API decide
        // if (query.isBlank()) { return getRecentLists() } // Keep local handling if desired
        return handleListApiCallToDomain(
            apiCall = { listApiService.searchLists("Bearer ${tokenProvider.getToken()!!}", query) },
            mapper = { listDto -> listDto.toDomainModel() }
        )
    }

    // --- REMOVE or Re-evaluate addPlaceToList ---
    // This method signature doesn't align well with ListApiService.addPlace
    // If it's about LINKING an existing place, a different API endpoint is needed.
    // If it's about CREATING a place via API, that's PlaceRepository's job.
    // Removing it for now. If needed, it requires rethinking.
    override suspend fun addPlaceToList(listId: String, placeId: String): Result<Unit> {
        Log.e("ListRepositoryImpl", "addPlaceToList(listId, placeId) is not supported via ListApiService. Check if linking is intended.")
        return Result.error(AppException.UnknownException("Operation addPlaceToList(listId, placeId) not supported by current API service setup."))
        // --- Old code attempting creation (incorrect place for this logic) ---
        /*
        // Needs PlaceCreateDto, but only has placeId. Where does the rest of the data come from?
        // This logic belongs in PlaceRepository or requires a different API endpoint.
        val placeCreateDto = PlaceCreateDto(placeId = placeId, name = "??", address = "??", latitude = 0.0, longitude = 0.0) // Incomplete data
        return handleUnitApiCall {
             listApiService.addPlace("Bearer ${tokenProvider.getToken()!!}", listId, placeCreateDto)
        }
        .onSuccess { /* caching logic if applicable */ }
        */
    }

    override suspend fun removePlaceFromList(listId: String, placeId: String): Result<Unit> {
        return handleUnitApiCall {
            listApiService.removePlaceFromList("Bearer ${tokenProvider.getToken()!!}", listId, placeId)
        }
        // .onSuccess { /* caching logic */ }
    }

    override suspend fun followList(listId: String): Result<Unit> {
        return handleUnitApiCall {
            listApiService.followList("Bearer ${tokenProvider.getToken()!!}", listId)
        }
    }

    override suspend fun unfollowList(listId: String): Result<Unit> {
        return handleUnitApiCall {
            listApiService.unfollowList("Bearer ${tokenProvider.getToken()!!}", listId)
        }
    }

    // --- Error Mapping Helpers (Keep as is) ---
    private fun mapError(code: Int, errorBody: String?): AppException {
        Log.e("ListRepositoryImpl", "API Error $code: ${errorBody ?: "Unknown error"}")
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
        Log.e("ListRepositoryImpl", "Network/Unknown error: ${e.message ?: "Unknown exception"}", e)
        return when(e) {
            is java.io.IOException -> AppException.NetworkException("Network IO error: ${e.message}", cause = e)
            // Add checks for specific API exceptions if needed (e.g., AuthenticationException)
            else -> AppException.UnknownException(e.message ?: "An unknown error occurred", e)
        }
    }
}