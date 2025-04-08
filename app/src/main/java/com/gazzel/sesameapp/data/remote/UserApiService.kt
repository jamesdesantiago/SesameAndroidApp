// File: app/src/main/java/com/gazzel/sesameapp/data/remote/UserApiService.kt
package com.gazzel.sesameapp.data.remote

import com.gazzel.sesameapp.data.model.User
import com.gazzel.sesameapp.data.remote.dto.PrivacySettingsUpdateDto
import com.gazzel.sesameapp.data.remote.dto.UserProfileUpdateDto
import com.gazzel.sesameapp.data.remote.dto.UsernameCheckResponseDto
import com.gazzel.sesameapp.data.remote.dto.UsernameSetResponseDto
import com.gazzel.sesameapp.data.remote.dto.UsernameSetDto
import com.gazzel.sesameapp.domain.model.PrivacySettings
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface UserApiService {
    // --- User Profile & Account Operations (From both previous services, consolidated here) ---

    @GET("users/me") // <<< From UserApiService & UserProfileService (GET Current User Profile)
    suspend fun getCurrentUserProfile(
        @Header("Authorization") authorization: String
    ): Response<User> // Response is Data layer User model

    @PUT("users/me") // Or PATCH, if partial updates are supported
    suspend fun updateUserProfile(
        @Header("Authorization") authorization: String,
        @Body userProfileUpdate: UserProfileUpdateDto // <<< DTO for updates
    ): Response<User> // Assuming API returns updated User

    @DELETE("users/me") // From UserApiService (Delete Account)
    suspend fun deleteAccount(
        @Header("Authorization") authorization: String
    ): Response<Unit>

    @POST("users/set-username") // From UserApiService & UserProfileService (Set Username)
    suspend fun setUsername(
        @Header("Authorization") authorization: String,
        @Body request: UsernameSetDto // <<< Use DTO for request
    ): Response<UsernameSetResponseDto> // <<< Use DTO for response

    @GET("users/check-username") // From UserApiService & UserProfileService (Check Username)
    suspend fun checkUsername(
        @Header("Authorization") authorization: String
    ): Response<UsernameCheckResponseDto> // <<< Use DTO for response

    @GET("users/{userId}") // From UserProfileService (Get User Profile by ID)
    suspend fun getUserProfileById(
        @Path("userId") userId: String,
        @Header("Authorization") authorization: String
    ): Response<User>

    // --- Privacy Settings Operations (From UserApiService) ---

    @GET("users/me/settings")
    suspend fun getPrivacySettings(
        @Header("Authorization") authorization: String
    ): Response<PrivacySettings> // Assuming PrivacySettings domain model is response

    @PATCH("users/me/settings")
    suspend fun updatePrivacySettings(
        @Header("Authorization") authorization: String,
        @Body settingsUpdate: PrivacySettingsUpdateDto // <<< Use DTO for updates
    ): Response<PrivacySettings> // Or Response<Unit> if API returns no body

    // --- Friend/Follow Operations (From UserProfileService) ---

    @GET("users/search") // Search Users by Email
    suspend fun searchUsersByEmail(
        @Query("email") email: String,
        @Header("Authorization") token: String
    ): Response<List<User>>

    @POST("users/{userId}/follow") // Follow User
    suspend fun followUser(
        @Path("userId") userId: String,
        @Header("Authorization") token: String
    ): Response<Unit>

    @DELETE("users/{userId}/follow") // Unfollow User
    suspend fun unfollowUser(
        @Path("userId") userId: String,
        @Header("Authorization") token: String
    ): Response<Unit>

    @GET("users/following") // Get Following List
    suspend fun getFollowing(
        @Header("Authorization") token: String
    ): Response<List<User>>

    @GET("users/followers") // Get Followers List
    suspend fun getFollowers(
        @Header("Authorization") token: String
    ): Response<List<User>>

    @POST("users/{userId}/friend-request") // Send Friend Request (if applicable)
    suspend fun sendFriendRequest(
        @Path("userId") userId: String,
        @Header("Authorization") token: String
    ): Response<Unit>

    // Note: removed deprecated/duplicate `updateUsername` from UserApiService,
    //  as `setUsername` is the correct endpoint for username setting.
    //  Removed local DTOs from original UserApiService and UserProfileService.
    //  All DTOs should now be in data/remote/dto/UserDtos.kt, ListDtos.kt, PlaceDtos.kt
}


// --- DTOs (Remove these from UserApiService and UserProfileService - they are now in data/remote/dto/UserDtos.kt) ---
// REMOVE UsernameUpdateRequest, UsernameCheckResponse, UsernameSet, UsernameSetResponse
// REMOVE UsernameRequest, UsernameResponse, CheckUsernameResponse