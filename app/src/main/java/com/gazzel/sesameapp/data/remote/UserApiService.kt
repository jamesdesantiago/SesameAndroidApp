package com.gazzel.sesameapp.data.remote

import com.gazzel.sesameapp.data.model.User
import com.gazzel.sesameapp.domain.model.PrivacySettings
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT

interface UserApiService {
    @GET("users/me/settings") // Match your FastAPI route
    suspend fun getPrivacySettings(
        @Header("Authorization") authorization: String
    ): Response<PrivacySettings> // Or a specific DTO

    @PATCH("users/me/settings") // Match your FastAPI route
    suspend fun updatePrivacySettings(
        @Header("Authorization") authorization: String,
        @Body settingsUpdate: PrivacySettings // Or a specific update DTO
    ): Response<PrivacySettings> // Or Response<Unit>

    @DELETE("users/me") // Match your FastAPI route
    suspend fun deleteAccount(
        @Header("Authorization") authorization: String
    ): Response<Unit>

    @PUT("users/username") // This might be wrong endpoint based on FastAPI?
    suspend fun updateUsername(@Body request: UsernameUpdateRequest): Response<Unit>

    @GET("users/me") // <<<--- EXAMPLE ROUTE - CHANGE IF NEEDED
    suspend fun getCurrentUserProfile(
        @Header("Authorization") authorization: String
    ): Response<User>

    @POST("users/set-username") // Matches FastAPI
    suspend fun setUsername(
        @Header("Authorization") authorization: String,
        @Body request: UsernameSet // Matches FastAPI body model
    ): Response<UsernameSetResponse> // Or Response<Unit> if API returns no body

    @GET("users/check-username") // Matches FastAPI
    suspend fun checkUsername(
        @Header("Authorization") authorization: String
    ): Response<UsernameCheckResponse>


}

data class UsernameUpdateRequest(
    val username: String
)

data class UsernameCheckResponse(
    val needsUsername: Boolean
)

data class UsernameSet( // Request body for /users/set-username
    val username: String
)

data class UsernameSetResponse( // Response body for /users/set-username
    val message: String
)