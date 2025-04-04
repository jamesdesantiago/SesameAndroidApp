package com.gazzel.sesameapp.data.service

import com.gazzel.sesameapp.domain.model.User
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class UsernameRequest(val username: String)
data class UsernameResponse(val message: String)
data class CheckUsernameResponse(val needsUsername: Boolean)

interface UserProfileService {
    @GET("users/search")
    suspend fun searchUsersByEmail(
        @Query("email") email: String,
        @Header("Authorization") token: String
    ): Response<List<User>>

    @POST("users/{userId}/follow")
    suspend fun followUser(
        @Path("userId") userId: String,
        @Header("Authorization") token: String
    ): Response<Unit>

    @DELETE("users/{userId}/follow")
    suspend fun unfollowUser(
        @Path("userId") userId: String,
        @Header("Authorization") token: String
    ): Response<Unit>

    @GET("users/following")
    suspend fun getFollowing(
        @Header("Authorization") token: String
    ): Response<List<User>>

    @GET("users/followers")
    suspend fun getFollowers(
        @Header("Authorization") token: String
    ): Response<List<User>>

    @POST("users/{userId}/friend-request")
    suspend fun sendFriendRequest(
        @Path("userId") userId: String,
        @Header("Authorization") token: String
    ): Response<Unit>

    @GET("users/{userId}")
    suspend fun getUserProfile(
        @Path("userId") userId: String,
        @Header("Authorization") token: String
    ): Response<User>

    @POST("users/set-username")
    suspend fun setUsername(
        @Header("Authorization") authorization: String,
        @Body request: UsernameRequest
    ): Response<UsernameResponse>

    @GET("users/check-username")
    suspend fun checkUsername(
        @Header("Authorization") authorization: String
    ): Response<CheckUsernameResponse>
}

object ApiClient {
    private const val BASE_URL = "https://gazzel.io/"

    val userProfileService: UserProfileService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UserProfileService::class.java)
    }
} 