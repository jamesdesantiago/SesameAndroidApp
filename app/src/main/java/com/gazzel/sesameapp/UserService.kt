package com.gazzel.sesameapp

import retrofit2.Response
import retrofit2.http.*

interface UserService {
    // Search users by email
    @GET("users/search")
    suspend fun searchUsersByEmail(
        @Query("email") email: String,
        @Header("Authorization") token: String
    ): Response<List<User>>

    // Follow a user
    @POST("users/{userId}/follow")
    suspend fun followUser(
        @Path("userId") userId: Int,
        @Header("Authorization") token: String
    ): Response<Unit>

    // Unfollow a user
    @DELETE("users/{userId}/follow")
    suspend fun unfollowUser(
        @Path("userId") userId: Int,
        @Header("Authorization") token: String
    ): Response<Unit>

    // Get the list of users the current user is following
    @GET("users/following")
    suspend fun getFollowing(
        @Header("Authorization") token: String
    ): Response<List<User>>

    // Get the list of the current user's followers
    @GET("users/followers")
    suspend fun getFollowers(
        @Header("Authorization") token: String
    ): Response<List<User>>

    // Placeholder for sending a friend request (if applicable)
    @POST("users/{userId}/friend-request")
    suspend fun sendFriendRequest(
        @Path("userId") userId: Int,
        @Header("Authorization") token: String
    ): Response<Unit>

    // Placeholder for fetching a user's profile (for future profile functionality)
    @GET("users/{userId}")
    suspend fun getUserProfile(
        @Path("userId") userId: Int,
        @Header("Authorization") token: String
    ): Response<User>
}