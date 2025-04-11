// Create new file: app/src/main/java/com/gazzel/sesameapp/data/remote/dto/PaginatedListResponseDto.kt
package com.gazzel.sesameapp.data.remote.dto

// *** Import YOUR ListDto ***
import com.gazzel.sesameapp.data.remote.dto.ListDto // Import your actual ListDto path
import com.google.gson.annotations.SerializedName

data class PaginatedListResponseDto(
    @SerializedName("items")
    val items: List<ListDto>, // <<< Use YOUR ListDto here

    @SerializedName("page")
    val page: Int,

    @SerializedName("page_size")
    val pageSize: Int,

    @SerializedName("total_items")
    val totalItems: Int,

    @SerializedName("total_pages")
    val totalPages: Int
)