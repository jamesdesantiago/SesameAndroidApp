package com.gazzel.sesameapp.domain.model

import com.google.gson.annotations.SerializedName

data class PlaceItem(
    val id: String,
    val name: String,
    val description: String = "",
    val address: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    val listId: String,
    val notes: String? = null,
    val rating: String? = null,
    val visitStatus: String? = null
) 