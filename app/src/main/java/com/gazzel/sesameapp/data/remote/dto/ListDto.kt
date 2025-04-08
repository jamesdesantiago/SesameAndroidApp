package com.gazzel.sesameapp.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ListDto(
    @SerializedName("id") // <-- CHANGE Annotation
    val id: String = "",
    @SerializedName("title") // <-- CHANGE Annotation
    val title: String = "",
    @SerializedName("description") // <-- CHANGE Annotation
    val description: String? = null,
    @SerializedName("userId") // <-- CHANGE Annotation (adjust JSON key if different)
    val userId: String = "",
    // Adjust type based on what your FastAPI returns for timestamps
    @SerializedName("createdAt") // <-- CHANGE Annotation
    val createdAt: Long = 0L, // Example: Assuming Long (Unix millis)
    @SerializedName("updatedAt") // <-- CHANGE Annotation
    val updatedAt: Long = 0L, // Example: Assuming Long (Unix millis)
    @SerializedName("isPublic") // <-- CHANGE Annotation (adjust JSON key if different)
    val isPublic: Boolean = false,
    @SerializedName("placeCount") // <-- CHANGE Annotation
    val placeCount: Int = 0,
    @SerializedName("followerCount") // <-- CHANGE Annotation
    val followerCount: Int = 0
)