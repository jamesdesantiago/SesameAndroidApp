// app/src/main/java/com/gazzel/sesameapp/data/repository/FriendRepositoryImpl.kt
package com.gazzel.sesameapp.data.repository

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
// Import API Service
import com.gazzel.sesameapp.data.remote.UserApiService
// Import PagingSources
import com.gazzel.sesameapp.data.paging.FollowingPagingSource
import com.gazzel.sesameapp.data.paging.FollowersPagingSource
import com.gazzel.sesameapp.data.paging.UserSearchPagingSource // Ensure import
// Import Domain model and Repository interface
import com.gazzel.sesameapp.domain.model.Friend
import com.gazzel.sesameapp.domain.repository.FriendRepository
// Import Result, Exceptions, TokenProvider
import com.gazzel.sesameapp.domain.auth.TokenProvider
import com.gazzel.sesameapp.domain.exception.AppException
import com.gazzel.sesameapp.domain.util.Result
// Import Data layer User model DTO (used by mappers implicitly or if returned directly)
import com.gazzel.sesameapp.data.remote.dto.UserDto
// Import Mapper function correctly
import com.gazzel.sesameapp.data.mapper.toDomainFriend
// Coroutine stuff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FriendRepositoryImpl @Inject constructor(
    private val userApiService: UserApiService,
    private val tokenProvider: TokenProvider
) : FriendRepository {

    companion object {
        // Define page size consistently
        const val NETWORK_PAGE_SIZE = 20
        // FOLLOWING_ID_FETCH_PAGE_SIZE removed as fetchCurrentUserFollowingIds is removed
    }

    // --- Deprecated Non-Paginated Methods (Consider removing or keeping as is if needed for specific non-UI logic) ---
    override fun getFriends(): Flow<List<Friend>> = flow {
        Log.w("FriendRepositoryImpl", "getFriends() called - This method is non-paginated and potentially inefficient. Consider using getFollowingPaginated().")
        val token = tokenProvider.getToken() ?: throw AppException.AuthException("User not authenticated")
        val authorizationHeader = "Bearer $token"
        // Attempt to fetch first large page as fallback
        val response = withContext(Dispatchers.IO) {
            userApiService.getFollowing(authorizationHeader, page = 1, pageSize = 1000) // High limit, not true pagination
        }
        if (response.isSuccessful && response.body() != null) {
            val followingDataUsers = response.body()!!.items // Assuming items is List<UserDto>
            val friends = followingDataUsers.map { dataUser -> dataUser.toDomainFriend() } // Mapper now gets isFollowing from DTO
            emit(friends)
        } else {
            throw mapApiErrorFriend(response.code(), response.errorBody()?.string())
        }
    }.catch { e ->
        throw mapExceptionToAppExceptionFriend(e, "Failed to load non-paginated friends")
    }.flowOn(Dispatchers.Default)

    override fun searchFriends(query: String): Flow<List<Friend>> = flow {
        Log.w("FriendRepositoryImpl", "searchFriends() called - This method is non-paginated and potentially inefficient. Consider using searchFriendsPaginated().")
        if (query.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val token = tokenProvider.getToken() ?: throw AppException.AuthException("User not authenticated")
        val authorizationHeader = "Bearer $token"
        // Removed fetchCurrentUserFollowingIds call

        // Attempt to fetch first large page as fallback
        val response = withContext(Dispatchers.IO) {
            userApiService.searchUsersByEmail(query, authorizationHeader, page = 1, pageSize = 1000) // High limit
        }
        if (response.isSuccessful && response.body() != null) {
            val searchResultDataUsers = response.body()!!.items // Assuming items is List<UserDto>
            val friends = searchResultDataUsers.map { dataUser ->
                // Mapper now gets isFollowing from DTO
                dataUser.toDomainFriend()
            }
            emit(friends)
        } else {
            throw mapApiErrorFriend(response.code(), response.errorBody()?.string())
        }
    }.catch { e ->
        throw mapExceptionToAppExceptionFriend(e, "Failed to search non-paginated friends")
    }.flowOn(Dispatchers.Default)

    // --- Action Methods ---
    override suspend fun followUser(userId: String): Result<Unit> {
        Log.d("FriendRepositoryImpl", "followUser called for ID: $userId")
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("User not authenticated"))
        val userIdInt = try { userId.toInt() } catch (e: NumberFormatException) { return Result.error(AppException.ValidationException("Invalid user ID format")) }

        return try {
            withContext(Dispatchers.IO) {
                val response = userApiService.followUser(userIdInt, "Bearer $token")
                if (response.isSuccessful || response.code() == 201 || response.code() == 200) { // Accept multiple success codes
                    Log.i("FriendRepositoryImpl", "Successfully followed user $userId (Status: ${response.code()})")
                    Result.success(Unit)
                } else {
                    Log.w("FriendRepositoryImpl", "Failed to follow user $userId (Status: ${response.code()})")
                    Result.error(mapApiErrorFriend(response.code(), response.errorBody()?.string()))
                }
            }
        } catch (e: Exception) {
            Log.e("FriendRepositoryImpl", "Exception during followUser for $userId", e)
            Result.error(mapExceptionToAppExceptionFriend(e, "Failed to follow user"))
        }
    }

    override suspend fun unfollowUser(userId: String): Result<Unit> {
        Log.d("FriendRepositoryImpl", "unfollowUser called for ID: $userId")
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("User not authenticated"))
        val userIdInt = try { userId.toInt() } catch (e: NumberFormatException) { return Result.error(AppException.ValidationException("Invalid user ID format")) }

        return try {
            withContext(Dispatchers.IO) {
                val response = userApiService.unfollowUser(userIdInt, "Bearer $token")
                if (response.isSuccessful || response.code() == 204 || response.code() == 200) { // Accept multiple success codes
                    Log.i("FriendRepositoryImpl", "Successfully unfollowed user $userId (Status: ${response.code()})")
                    Result.success(Unit)
                } else {
                    Log.w("FriendRepositoryImpl", "Failed to unfollow user $userId (Status: ${response.code()})")
                    Result.error(mapApiErrorFriend(response.code(), response.errorBody()?.string()))
                }
            }
        } catch (e: Exception) {
            Log.e("FriendRepositoryImpl", "Exception during unfollowUser for $userId", e)
            Result.error(mapExceptionToAppExceptionFriend(e, "Failed to unfollow user"))
        }
    }

    // --- Paging Method Implementations ---
    override fun getFollowingPaginated(): Flow<PagingData<Friend>> {
        Log.d("FriendRepositoryImpl", "Creating Pager for getFollowingPaginated")
        return Pager(
            config = PagingConfig(
                pageSize = NETWORK_PAGE_SIZE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                Log.d("FriendRepositoryImpl", "Instantiating FollowingPagingSource")
                FollowingPagingSource(userApiService, tokenProvider)
            }
        ).flow
    }

    override fun getFollowersPaginated(): Flow<PagingData<Friend>> {
        Log.d("FriendRepositoryImpl", "Creating Pager for getFollowersPaginated")
        return Pager(
            config = PagingConfig(
                pageSize = NETWORK_PAGE_SIZE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                Log.d("FriendRepositoryImpl", "Instantiating FollowersPagingSource")
                FollowersPagingSource(userApiService, tokenProvider)
            }
        ).flow
    }

    override fun searchFriendsPaginated(query: String): Flow<PagingData<Friend>> {
        Log.d("FriendRepositoryImpl", "Creating Pager flow for searchFriendsPaginated with query: '$query'")
        // Directly create the Pager flow without fetching IDs first
        return Pager(
            config = PagingConfig(
                pageSize = NETWORK_PAGE_SIZE, // Or a different size for search
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                Log.d("FriendRepositoryImpl", "Instantiating UserSearchPagingSource for query '$query'")
                // Instantiate WITHOUT followingIds
                UserSearchPagingSource(userApiService, tokenProvider, query.trim())
            }
        ).flow
    }

    // --- REMOVED fetchCurrentUserFollowingIds function ---

    // --- Error Mapping Helpers ---
    private fun mapApiErrorFriend(code: Int, errorBody: String?): AppException {
        val defaultMsg = "API Error $code"
        val bodyMsg = errorBody ?: "No specific error message provided."
        Log.e("FriendRepositoryImpl", "$defaultMsg: $bodyMsg")
        return when (code) {
            401 -> AppException.AuthException("Authentication failed. Please sign in again.")
            403 -> AppException.AuthException("Permission denied.")
            404 -> AppException.ResourceNotFoundException("User or resource not found.")
            409 -> AppException.ValidationException(errorBody ?: "Conflict detected.")
            422 -> AppException.ValidationException(errorBody ?: "Invalid data submitted.")
            in 500..599 -> AppException.NetworkException("Server error ($code). Please try again later.", code)
            else -> AppException.NetworkException("Network error ($code): $bodyMsg", code)
        }
    }

    private fun mapExceptionToAppExceptionFriend(e: Throwable, defaultMessage: String): AppException {
        Log.e("FriendRepositoryImpl", "$defaultMessage: ${e.message}", e)
        return when (e) {
            is retrofit2.HttpException -> mapApiErrorFriend(e.code(), e.response()?.errorBody()?.string())
            is IOException -> AppException.NetworkException("Network connection issue. Please check your connection.", cause = e)
            is AppException -> e // Don't re-wrap
            else -> AppException.UnknownException(e.message ?: defaultMessage, e)
        }
    }
}