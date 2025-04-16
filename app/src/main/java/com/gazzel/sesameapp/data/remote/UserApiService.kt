// File: app/src/main/java/com/gazzel/sesameapp/data/remote/UserApiService.kt
package com.gazzel.sesameapp.data.remote

// Import the new UserDto
import com.gazzel.sesameapp.data.remote.dto.UserDto // <<< CHANGED import
// Import other necessary DTOs
import com.gazzel.sesameapp.data.remote.dto.PrivacySettingsUpdateDto
import com.gazzel.sesameapp.data.remote.dto.UserProfileUpdateDto
import com.gazzel.sesameapp.data.remote.dto.UsernameCheckResponseDto
import com.gazzel.sesameapp.data.remote.dto.UsernameSetResponseDto
import com.gazzel.sesameapp.data.remote.dto.UsernameSetDto
// Import the paginated DTO containing the new UserDto
import com.gazzel.sesameapp.data.remote.dto.PaginatedUserResponseDto // Ensure this uses UserDto internally
// Import Domain models (only if API directly returns them, like PrivacySettings)
import com.gazzel.sesameapp.domain.model.PrivacySettings
// Retrofit imports
import retrofit2.Response
import retrofit2.http.* // Use wildcard for brevity

interface UserApiService {

    @GET("users/me")
    suspend fun getCurrentUserProfile(
        @Header("Authorization") authorization: String
    ): Response<UserDto> // <<< CHANGED to UserDto

    @PUT("users/me") // Or PATCH
    suspend fun updateUserProfile(
        @Header("Authorization") authorization: String,
        @Body userProfileUpdate: UserProfileUpdateDto
    ): Response<UserDto> // <<< CHANGED to UserDto (assuming API returns updated user DTO)

    @DELETE("users/me")
    suspend fun deleteAccount(
        @Header("Authorization") authorization: String
    ): Response<Unit>

    @POST("users/set-username")
    suspend fun setUsername(
        @Header("Authorization") authorization: String,
        @Body request: UsernameSetDto
    ): Response<UsernameSetResponseDto>

    @GET("users/check-username")
    suspend fun checkUsername(
        @Header("Authorization") authorization: String
    ): Response<UsernameCheckResponseDto>

    @GET("users/{userId}")
    suspend fun getUserProfileById(
        @Path("userId") userId: Int,
        @Header("Authorization") authorization: String
    ): Response<UserDto> // <<< CHANGED to UserDto

    // --- Privacy Settings Operations ---

    @GET("users/me/settings")
    suspend fun getPrivacySettings(
        @Header("Authorization") authorization: String
    ): Response<PrivacySettings> // Stays as Domain model if API matches it exactly

    @PATCH("users/me/settings")
    suspend fun updatePrivacySettings(
        @Header("Authorization") authorization: String,
        @Body settingsUpdate: PrivacySettingsUpdateDto
    ): Response<PrivacySettings> // Stays as Domain model if API matches it exactly

    // --- Friend/Follow Operations ---

    // IMPORTANT: Update PaginatedUserResponseDto definition if needed to ensure it contains List<UserDto>
    // Example: data class PaginatedUserResponseDto<T>( ..., val items: List<T>, ... )
    // Then the Response would be Response<PaginatedUserResponseDto<UserDto>>
    // Assuming PaginatedUserResponseDto internally uses UserDto based on previous structure:

    @GET("users/search")
    suspend fun searchUsersByEmail(
        @Query("email") email: String,
        @Header("Authorization") token: String,
        @Query("page") page: Int,        // Assuming paginated search
        @Query("pageSize") pageSize: Int // Assuming paginated search
    ): Response<PaginatedUserResponseDto> // <<< KEEP - contains List<UserDto> internally

    @POST("users/{userId}/follow")
    suspend fun followUser(
        @Path("userId") userId: Int,
        @Header("Authorization") token: String
    ): Response<Unit>

    @DELETE("users/{userId}/follow")
    suspend fun unfollowUser(
        @Path("userId") userId: Int,
        @Header("Authorization") token: String
    ): Response<Unit>

    @GET("users/following")
    suspend fun getFollowing(
        @Header("Authorization") token: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int
    ): Response<PaginatedUserResponseDto> // <<< KEEP - contains List<UserDto> internally

    @GET("users/followers")
    suspend fun getFollowers(
        @Header("Authorization") token: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int
    ): Response<PaginatedUserResponseDto> // <<< KEEP - contains List<UserDto> internally

    @POST("users/{userId}/friend-request")
    suspend fun sendFriendRequest(
        @Path("userId") userId: Int,
        @Header("Authorization") token: String
    ): Response<Unit>

}