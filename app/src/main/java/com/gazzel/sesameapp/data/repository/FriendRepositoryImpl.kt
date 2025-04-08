// data/repository/FriendRepositoryImpl.kt
package com.gazzel.sesameapp.data.repository

import android.util.Log
// Import the *consolidated* UserApiService
import com.gazzel.sesameapp.data.remote.UserApiService // <<< CHANGE
// Import Domain model and Repository interface
import com.gazzel.sesameapp.domain.model.Friend
import com.gazzel.sesameapp.domain.repository.FriendRepository
// Import Result, Exceptions, TokenProvider
import com.gazzel.sesameapp.domain.auth.TokenProvider // <<< ADD
import com.gazzel.sesameapp.domain.exception.AppException
import com.gazzel.sesameapp.domain.util.Result // Required if adding non-Flow methods later
// Import Data layer User model
import com.gazzel.sesameapp.data.model.User as DataUser
// Coroutine stuff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FriendRepositoryImpl @Inject constructor(
    // Inject consolidated UserApiService and TokenProvider
    private val userApiService: UserApiService, // <<< CHANGE Service type
    private val tokenProvider: TokenProvider // <<< INJECT TokenProvider
) : FriendRepository {

    // --- Flow for observing Friends (current implementation uses '/following' endpoint) ---
    override fun getFriends(): Flow<List<Friend>> = flow {
        Log.d("FriendRepositoryImpl", "Getting friends (using /following endpoint)")
        val token = tokenProvider.getToken() // Use TokenProvider
        if (token == null) {
            Log.w("FriendRepositoryImpl", "No token, cannot get friends.")
            // Throw exception in flow to signal error
            throw AppException.AuthException("User not authenticated")
        }
        val authorizationHeader = "Bearer $token"

        // Perform API call within IO context
        val response = withContext(Dispatchers.IO) {
            Log.d("FriendRepositoryImpl", "Calling API to get following list...")
            // Call the method from the CONSOLIDATED service
            userApiService.getFollowing(authorizationHeader) // Ensure this method exists in UserApiService
        }

        if (response.isSuccessful && response.body() != null) {
            val followingDataUsers = response.body()!!
            Log.d("FriendRepositoryImpl", "API getFollowing successful: ${followingDataUsers.size} users.")
            // Map the API User model to the Domain Friend model
            // We know these users are being followed because we called /following
            val friends = followingDataUsers.map { dataUser -> dataUser.toDomainFriend(isFollowing = true) }
            emit(friends)
        } else {
            // Throw exception in flow for API errors
            val errorMsg = "API getFollowing Error ${response.code()}: ${response.errorBody()?.string() ?: response.message()}"
            Log.e("FriendRepositoryImpl", "Failed to get following: $errorMsg")
            throw mapApiErrorFriend(response.code(), response.errorBody()?.string()) // Throw mapped exception
        }
    }.catch { e -> // Catch exceptions from API call or mapping
        Log.e("FriendRepositoryImpl", "Error in getFriends flow", e)
        // Re-throw as AppException or handle as needed
        throw mapExceptionToAppExceptionFriend(e, "Failed to load friends")
    }.flowOn(Dispatchers.Default) // Use Default dispatcher for flow logic


    // --- Flow for searching Friends ---
    override fun searchFriends(query: String): Flow<List<Friend>> = flow {
        Log.d("FriendRepositoryImpl", "Searching friends with query: $query")
        if (query.isBlank()) {
            emit(emptyList()) // Return empty immediately if query is blank
            return@flow
        }
        val token = tokenProvider.getToken()
        if (token == null) {
            Log.w("FriendRepositoryImpl", "No token, cannot search friends.")
            throw AppException.AuthException("User not authenticated")
        }
        val authorizationHeader = "Bearer $token"

        // Fetch current following list ONCE to determine 'isFollowing' status for search results
        // This adds an extra API call but is often necessary. Could be optimized with caching.
        val currentFollowing = try {
            withContext(Dispatchers.IO) { userApiService.getFollowing(authorizationHeader) }.body()?.map { it.id }?.toSet() ?: emptySet()
        } catch(e: Exception) {
            Log.w("FriendRepositoryImpl", "Could not fetch following list for search comparison", e)
            emptySet<String>() // Proceed without following status if fetch fails
        }


        // API Call for search
        val response = withContext(Dispatchers.IO) { // <<< IO Dispatcher
            Log.d("FriendRepositoryImpl", "Calling API to search users by email: $query")
            // Call the method from the CONSOLIDATED service
            userApiService.searchUsersByEmail(query, authorizationHeader) // Ensure this method exists
        }

        if (response.isSuccessful && response.body() != null) {
            val searchResultDataUsers = response.body()!!
            Log.d("FriendRepositoryImpl", "API searchUsers successful: ${searchResultDataUsers.size} results.")
            // Map results, checking against the fetched following list
            val friends = searchResultDataUsers.map { dataUser ->
                dataUser.toDomainFriend(isFollowing = currentFollowing.contains(dataUser.id)) // Check if user ID is in the 'following' set
            }
            emit(friends)
        } else {
            val errorMsg = "API searchUsers Error ${response.code()}: ${response.errorBody()?.string() ?: response.message()}"
            Log.e("FriendRepositoryImpl", "Failed to search users: $errorMsg")
            throw mapApiErrorFriend(response.code(), response.errorBody()?.string())
        }
    }.catch { e ->
        Log.e("FriendRepositoryImpl", "Error in searchFriends flow", e)
        throw mapExceptionToAppExceptionFriend(e, "Failed to search friends")
    }.flowOn(Dispatchers.Default)


    // --- TODO: Add suspend functions for follow/unfollow if needed by interface ---
    // Example:
    override suspend fun followUser(userId: String): Result<Unit> {
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("User not authenticated"))
        return try {
            withContext(Dispatchers.IO) {
                // Ensure followUser exists in the CONSOLIDATED UserApiService
                val response = userApiService.followUser(userId, "Bearer $token")
                if (response.isSuccessful || response.code() == 204) {
                    Result.success(Unit)
                } else {
                    Result.error(mapApiErrorFriend(response.code(), response.errorBody()?.string()))
                }
            }
        } catch (e: Exception) {
            Result.error(mapExceptionToAppExceptionFriend(e, "Failed to follow user"))
        }
    }

    override suspend fun unfollowUser(userId: String): Result<Unit> {
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("User not authenticated"))
        return try {
            withContext(Dispatchers.IO) {
                // Ensure unfollowUser exists in the CONSOLIDATED UserApiService
                val response = userApiService.unfollowUser(userId, "Bearer $token")
                if (response.isSuccessful || response.code() == 204) {
                    Result.success(Unit)
                } else {
                    Result.error(mapApiErrorFriend(response.code(), response.errorBody()?.string()))
                }
            }
        } catch (e: Exception) {
            Result.error(mapExceptionToAppExceptionFriend(e, "Failed to unfollow user"))
        }
    }


    // --- Error Mapping Helpers (Specific to FriendRepository or shared) ---
    private fun mapApiErrorFriend(code: Int, errorBody: String?): AppException {
        Log.e("FriendRepositoryImpl", "API Error $code: ${errorBody ?: "Unknown error"}")
        // Reuse or adapt mapping logic
        return when (code) {
            401, 403 -> AppException.AuthException(errorBody ?: "Authorization failed")
            404 -> AppException.ResourceNotFoundException(errorBody ?: "User not found")
            // Add other relevant codes
            else -> AppException.NetworkException("Network Error ($code): ${errorBody ?: ""}", code)
        }
    }

    private fun mapExceptionToAppExceptionFriend(e: Throwable, defaultMessage: String): AppException {
        Log.e("FriendRepositoryImpl", "$defaultMessage: ${e.message}", e)
        // Reuse or adapt mapping logic
        return when (e) {
            is retrofit2.HttpException -> mapApiErrorFriend(e.code(), e.response()?.errorBody()?.string() ?: e.message())
            is IOException -> AppException.NetworkException(e.message ?: "Network error", cause = e)
            is AppException -> e
            else -> AppException.UnknownException(e.message ?: defaultMessage, e)
        }
    }

}

// Helper Mapper (Keep as is or move to data/mapper package)
fun DataUser.toDomainFriend(isFollowing: Boolean): Friend {
    return Friend(
        id = this.id.toString(), // Assuming domain Friend uses String ID
        username = this.username ?: this.email.split("@")[0],
        displayName = this.displayName,
        profilePicture = this.profilePicture,
        listCount = 0, // API doesn't provide this for following/search? Default/fetch later.
        isFollowing = isFollowing
    )
}