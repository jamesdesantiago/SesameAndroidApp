// app/src/main/java/com/gazzel/sesameapp/data/remote/dto/UserDto.kt
package com.gazzel.sesameapp.data.remote.dto

import com.google.gson.annotations.SerializedName

// Represents the user data EXACTLY as received from the API
// Adjust field names and nullability based on your ACTUAL API responses
data class UserDto(
    @SerializedName("id") // Match JSON key from API
    val id: String,

    @SerializedName("email") // Match JSON key from API
    val email: String,

    @SerializedName("username") // Match JSON key from API
    val username: String?,

    @SerializedName("display_name") // Match JSON key from API (e.g., 'display_name')
    val displayName: String?,

    @SerializedName("profile_picture") // Match JSON key from API (e.g., 'profile_picture')
    val profilePicture: String?,

    // Add other fields returned by the API if needed by the app,
    // like counts (if UserApiService endpoints return them)
    // @SerializedName("list_count")
    // val listCount: Int? = null,
    // @SerializedName("follower_count")
    // val followerCount: Int? = null,
    // @SerializedName("following_count")
    // val followingCount: Int? = null,
    // @SerializedName("created_at") // Adjust type based on API (String, Long?)
    // val createdAt: String? = null,
    // @SerializedName("updated_at")
    // val updatedAt: String? = null
)