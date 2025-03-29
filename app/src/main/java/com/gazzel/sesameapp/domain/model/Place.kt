package com.gazzel.sesameapp.domain.model

import com.google.android.gms.maps.model.LatLng
import com.google.gson.annotations.SerializedName
import java.util.Date

data class Place(
    val id: String,
    val name: String,
    val description: String?,
    val address: String,
    val location: LatLng,
    val rating: Float?,
    val photoUrl: String?,
    val websiteUrl: String?,
    val phoneNumber: String?,
    val openingHours: List<OpeningHours>?,
    val types: List<String>,
    val priceLevel: Int?,
    val createdAt: Date,
    val updatedAt: Date
)

data class OpeningHours(
    val dayOfWeek: Int,
    val openTime: String,
    val closeTime: String,
    val isOpen: Boolean
)

data class PlaceCreate(
    val placeId: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val rating: String? = null,
    val notes: String? = null,
    val visitStatus: String? = null
)

data class PlaceUpdate(
    val notes: String? = null
) 