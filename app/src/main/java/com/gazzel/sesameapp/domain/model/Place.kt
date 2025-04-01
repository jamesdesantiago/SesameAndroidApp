package com.gazzel.sesameapp.domain.model

import com.google.android.gms.maps.model.LatLng // Use Google Maps LatLng
import java.util.Date

data class Place(
    val id: String,
    val name: String,
    val description: String?,
    val address: String,
    val location: LatLng,
    val rating: Float?, // Changed to Float? to match domain model
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
    val rating: Double? = null, // Aligned with UserListService.kt
    val notes: String? = null,  // Optional extension
    val visitStatus: String? = null // Optional extension
)

data class PlaceUpdate(
    val name: String? = null,   // Aligned with UserListService.kt
    val address: String? = null,
    val rating: Double? = null,
    val notes: String? = null   // Optional extension
)