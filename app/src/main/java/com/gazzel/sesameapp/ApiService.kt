package com.gazzel.sesameapp

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

data class UsernameRequest(val username: String)
data class UsernameResponse(val message: String)
data class CheckUsernameResponse(val needsUsername: Boolean)

interface UsernameService {
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

    val usernameService: UsernameService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UsernameService::class.java)
    }
}