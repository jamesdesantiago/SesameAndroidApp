// Create File: app/src/main/java/com/gazzel/sesameapp/data/remote/dto/ListDetailDto.kt
package com.gazzel.sesameapp.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO representing the detailed response for a single list (metadata + collaborators).
 * Matches the backend's `ListDetailResponse`. Excludes places.
 */
data class ListDetailDto(
    @SerializedName("id")
    val id: Int = 0, // <<< MATCH Backend type (Int)

    @SerializedName("name") // Assuming backend uses 'name'
    val name: String = "",

    @SerializedName("description")
    val description: String? = null,

    @SerializedName("isPrivate") // Matches backend 'isPrivate'
    val isPrivate: Boolean = false,

    @SerializedName("collaborators")
    val collaborators: List<String> = emptyList()

    // --- NO 'places' field here ---
)