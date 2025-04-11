package com.gazzel.sesameapp.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ListDto(
    @SerializedName("id")
    val id: Int = 0, // <<< MATCH Backend type (Int)

    @SerializedName("name") // Assuming backend uses 'name'
    val name: String = "", // <<< Use 'name' to match backend

    @SerializedName("description")
    val description: String? = null,

    // Matches backend 'isPrivate', adjust SerializedName if JSON key is 'is_private'
    @SerializedName("isPrivate")
    val isPrivate: Boolean = false,

    // Matches backend 'place_count', adjust SerializedName if JSON key is 'place_count'
    @SerializedName("place_count")
    val placeCount: Int = 0
)