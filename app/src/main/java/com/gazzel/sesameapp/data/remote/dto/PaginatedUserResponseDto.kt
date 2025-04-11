// Create file: data/remote/dto/PaginatedUserResponseDto.kt
package com.gazzel.sesameapp.data.remote.dto

import com.gazzel.sesameapp.data.remote.dto.UserDto // Use your data layer User model
import com.google.gson.annotations.SerializedName

data class PaginatedUserResponseDto(
    @SerializedName("items")
    val items: List<UserDto>, // <<< List of your User data model
    @SerializedName("page")
    val page: Int,
    @SerializedName("page_size")
    val pageSize: Int,
    @SerializedName("total_items")
    val totalItems: Int,
    @SerializedName("total_pages")
    val totalPages: Int
)