// File: app/src/main/java/com/gazzel/sesameapp/data/remote/dto/UserDtos.kt
package com.gazzel.sesameapp.data.remote.dto

import com.google.gson.annotations.SerializedName // Import if needed for JSON key mapping

// --- User Profile & Account DTOs ---

/**
 * DTO for sending the username during the initial setup.
 * Used as the request body for POST /users/set-username
 */
data class UsernameSetDto(
    val username: String
)

/**
 * DTO representing the response after successfully setting a username.
 * Used as the response type for POST /users/set-username
 */
data class UsernameSetResponseDto(
    // Adjust fields based on your actual API response structure
    val message: String // Example field
    // val user: UserResponseDto? // Maybe the API returns the updated user?
)

/**
 * DTO representing the response from checking if a username needs to be set.
 * Used as the response type for GET /users/check-username
 */
data class UsernameCheckResponseDto(
    // Use @SerializedName if the JSON key is different (e.g., "needs_username")
    // @SerializedName("needs_username")
    val needsUsername: Boolean
    // Add other fields if returned by the API, e.g.,
    // val existingUsername: String?
)

/**
 * DTO for sending user profile updates.
 * Used as the request body for PUT /users/me (or PATCH)
 * Fields are nullable to allow partial updates.
 */
data class UserProfileUpdateDto(
    // Use @SerializedName if JSON keys differ (e.g., "display_name")
    // @SerializedName("display_name")
    val displayName: String? = null,

    // @SerializedName("profile_picture")
    val profilePicture: String? = null
    // Add any other fields the user can update via the API
)

// --- Privacy Settings DTOs ---

/**
 * DTO for sending updates to privacy settings.
 * Used as the request body for PATCH /users/me/settings
 * Fields are nullable to allow partial updates.
 */
data class PrivacySettingsUpdateDto(
    // Use @SerializedName if JSON keys differ (e.g., "profile_is_public")
    // @SerializedName("profile_is_public")
    val profileIsPublic: Boolean? = null,

    // @SerializedName("lists_are_public")
    val listsArePublic: Boolean? = null,

    // @SerializedName("allow_analytics")
    val allowAnalytics: Boolean? = null
)

/**
 * (Optional) DTO representing the full user profile structure returned by the API.
 * Use this in Response<> types if the API structure differs significantly
 * from your data/model/User.kt Room entity.
 * If data/model/User.kt *exactly* matches the API response, you might reuse it,
 * but creating a separate DTO is often cleaner separation.
 */
/* // Uncomment and define if needed
data class UserResponseDto(
    @SerializedName("id")
    val id: String, // Assuming API uses String ID

    @SerializedName("email")
    val email: String,

    @SerializedName("username")
    val username: String?,

    @SerializedName("display_name")
    val displayName: String?,

    @SerializedName("profile_picture")
    val profilePicture: String?,

    // Add any other fields returned by GET /users/me, /users/search, etc.
    @SerializedName("list_count")
    val listCount: Int? = 0,

    @SerializedName("follower_count")
    val followerCount: Int? = 0,

    @SerializedName("following_count")
    val followingCount: Int? = 0,

    @SerializedName("created_at") // Example: Assuming ISO String format from API
    val createdAt: String?,

    @SerializedName("updated_at") // Example: Assuming ISO String format from API
    val updatedAt: String?
    // Add other fields as necessary based on API responses
)
*/

// --- Collaborator DTOs --- (Often grouped with List DTOs, but can be here too)

/**
 * DTO for adding a collaborator to a list (usually by email).
 * Used as request body for POST /lists/{listId}/collaborators
 */
data class CollaboratorAddDto(
    val email: String
)

// Add other User-related DTOs as needed for requests or specific responses.