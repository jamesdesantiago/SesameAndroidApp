package com.gazzel.sesameapp.data.remote // Or data.service if that's where it is

import com.gazzel.sesameapp.data.model.User
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

// --- Keep/Add Data classes if needed (e.g., for requests/responses not using User model) ---
// data class UsernameRequest(val username: String)
// data class UsernameResponse(val message: String)
// data class CheckUsernameResponse(val needsUsername: Boolean)

interface UserProfileService {

    // --- METHOD FOR SEARCHING USERS ---
    @GET("users/search") // Matches FastAPI route
    suspend fun searchUsersByEmail(
        @Query("email") email: String, // Matches FastAPI query param
        @Header("Authorization") token: String // Matches FastAPI header
    ): Response<List<User>> // Returns List of Data layer User

    // --- METHOD FOR GETTING FOLLOWING ---
    @GET("users/following") // Matches FastAPI route
    suspend fun getFollowing(
        @Header("Authorization") token: String // Matches FastAPI header
        // Optional: Add query params if your API supports pagination here
        // @Query("limit") limit: Int = 10,
        // @Query("offset") offset: Int = 0
    ): Response<List<User>> // Returns List of Data layer User


    // --- Keep/Add other methods from your previous UserApiService definition ---
    // Example:
    // @GET("users/followers") suspend fun getFollowers(...)
    // @POST("users/{userId}/follow") suspend fun followUser(...)
    // @DELETE("users/{userId}/follow") suspend fun unfollowUser(...)
    // @POST("users/{userId}/friend-request") suspend fun sendFriendRequest(...)
    // @GET("users/{userId}") suspend fun getUserProfile(...)
    // @POST("users/set-username") suspend fun setUsername(...)
    // @GET("users/check-username") suspend fun checkUsername(...)

    // --- Make sure methods needed by other repositories are also here ---

}