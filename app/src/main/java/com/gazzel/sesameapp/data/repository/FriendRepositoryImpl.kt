package com.gazzel.sesameapp.data.repository // Or appropriate package

import android.util.Log
import com.gazzel.sesameapp.data.service.UserProfileService
import com.gazzel.sesameapp.domain.model.Friend
import com.gazzel.sesameapp.domain.repository.FriendRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import com.gazzel.sesameapp.data.model.User as DataUser

@Singleton // Make implementation a Singleton
class FriendRepositoryImpl @Inject constructor(
    private val userProfileService: UserProfileService, // Inject the service
    private val firebaseAuth: FirebaseAuth // Inject Auth to get token
) : FriendRepository {

    // Helper to get token (could be shared via another injected class)
    private suspend fun getAuthToken(): String? {
        return try {
            firebaseAuth.currentUser?.getIdToken(false)?.await()?.token
        } catch (e: Exception) { null }
    }

    // Example Implementation using existing /following endpoint
    override fun getFriends(): Flow<List<Friend>> = flow {
        Log.d("FriendRepositoryImpl", "Getting friends (using /following endpoint)")
        val token = getAuthToken()
        if (token == null) {
            Log.w("FriendRepositoryImpl", "No token, cannot get friends")
            emit(emptyList()) // Emit empty list or throw error if no token
            return@flow
        }
        try {
            val response = userProfileService.getFollowing("Bearer $token") // Call API
            if (response.isSuccessful && response.body() != null) {
                // Map the API User model to the Domain Friend model
                val friends = response.body()!!.map { dataUser -> dataUser.toDomainFriend(isFollowing = true) }
                emit(friends)
            } else {
                Log.e("FriendRepositoryImpl", "Failed to get following: ${response.code()}")
                emit(emptyList()) // Emit empty list on error
            }
        } catch (e: Exception) {
            Log.e("FriendRepositoryImpl", "Error getting following", e)
            emit(emptyList()) // Emit empty list on exception
        }
    }

    // Example Implementation using existing /users/search endpoint
    override fun searchFriends(query: String): Flow<List<Friend>> = flow {
        Log.d("FriendRepositoryImpl", "Searching friends with query: $query")
        if (query.isBlank()) {
            emit(emptyList()) // Return empty if query is blank
            return@flow
        }
        val token = getAuthToken()
        if (token == null) {
            Log.w("FriendRepositoryImpl", "No token, cannot search friends")
            emit(emptyList())
            return@flow
        }
        try {
            val response = userProfileService.searchUsersByEmail(query, "Bearer $token") // Call API
            if (response.isSuccessful && response.body() != null) {
                // TODO: Determine 'isFollowing' status for search results
                // This might require another API call or checking against a local cache/list
                val friends = response.body()!!.map { dataUser ->
                    dataUser.toDomainFriend(isFollowing = false) // Placeholder for isFollowing
                }
                emit(friends)
            } else {
                Log.e("FriendRepositoryImpl", "Failed to search users: ${response.code()}")
                emit(emptyList())
            }
        } catch (e: Exception) {
            Log.e("FriendRepositoryImpl", "Error searching users", e)
            emit(emptyList())
        }
    }
}

// Helper Mapper (Consider moving to a dedicated mapper file)
// Assumes DataUser is com.gazzel.sesameapp.data.model.User
// Assumes Domain Friend is com.gazzel.sesameapp.domain.model.Friend
fun DataUser.toDomainFriend(isFollowing: Boolean): Friend {
    return Friend(
        id = this.id.toString(), // Convert Int ID to String
        username = this.username ?: this.email.split("@")[0], // Use username or derive from email
        displayName = this.displayName, // Assuming DataUser has displayName
        profilePicture = this.profilePicture, // Assuming DataUser has profilePicture
        listCount = 0, // API doesn't provide this, default to 0 or fetch separately
        isFollowing = isFollowing // Pass calculated/known following status
    )
}

// Add necessary mappers in the opposite direction if needed