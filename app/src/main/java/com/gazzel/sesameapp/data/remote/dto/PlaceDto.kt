// app/src/main/java/com/gazzel/sesameapp/data/remote/dto/PlaceDto.kt
package com.gazzel.sesameapp.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO representing a place item, matching backend PlaceItem model.
 */
data class PlaceDto(
    @SerializedName("id")
    val id: Int = 0, // <<< Changed to Int

    @SerializedName("name")
    val name: String = "",

    @SerializedName("address")
    val address: String = "",

    @SerializedName("latitude")
    val latitude: Double = 0.0,

    @SerializedName("longitude")
    val longitude: Double = 0.0,

    @SerializedName("rating")
    val rating: String? = null, // User rating

    // Make sure SerializedName matches JSON key (e.g., "visitStatus" or "visit_status")
    @SerializedName("visitStatus")
    val visitStatus: String? = null, // <<< Ensure this exists

    @SerializedName("notes")
    val notes: String? = null // <<< Ensure this exists
)