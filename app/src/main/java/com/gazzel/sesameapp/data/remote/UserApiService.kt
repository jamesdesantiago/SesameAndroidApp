package com.gazzel.sesameapp.data.remote

import com.gazzel.sesameapp.data.model.User
import retrofit2.Response
import retrofit2.http.*

interface UserApiService {
    @GET("users/me")
    suspend fun getCurrentUser(): User

    @PUT("users/username")
    suspend fun updateUsername(@Body request: UsernameUpdateRequest): Response<Unit>

    @GET("users/check-username")
    suspend fun checkUsername(): UsernameCheckResponse
}

data class UsernameUpdateRequest(
    val username: String
)

data class UsernameCheckResponse(
    val needsUsername: Boolean
) 