// Create File: app/src/main/java/com/gazzel/sesameapp/data/remote/dto/PaginatedPlaceResponseDto.kt
package com.gazzel.sesameapp.data.remote.dto

// Import your data layer Place DTO. Adjust the import path if needed.
// Assuming it's 'PlaceDto' and located in 'data/remote/dto' or 'data/model'.
import com.gazzel.sesameapp.data.remote.dto.PlaceDto // Or com.gazzel.sesameapp.data.model.PlaceDto
import com.google.gson.annotations.SerializedName

data class PaginatedPlaceResponseDto(
    @SerializedName("items")
    val items: List<PlaceDto>, // List of Place data objects

    @SerializedName("page")
    val page: Int,

    @SerializedName("page_size")
    val pageSize: Int,

    @SerializedName("total_items")
    val totalItems: Int,

    @SerializedName("total_pages")
    val totalPages: Int
)