package com.gazzel.sesameapp.domain.model

import com.google.android.gms.maps.model.LatLng // Use Google Maps LatLng
import java.util.Date

data class Place(
    val id: String,
    val name: String,
    val description: String?, // Nullable description
    val address: String,
    val location: LatLng,     // Uses LatLng object
    val rating: String?,     // Rating is String?
    val photoUrl: String?,
    val websiteUrl: String?,
    val phoneNumber: String?,
    val openingHours: List<OpeningHours>?,
    val types: List<String>,
    val priceLevel: Int?,
    val createdAt: Date,     // Uses Date objects
    val updatedAt: Date
)

data class OpeningHours(
    val dayOfWeek: Int,
    val openTime: String,
    val closeTime: String,
    val isOpen: Boolean
)

// DTOs for API Interaction (Potentially move to data layer if not used elsewhere in domain)
data class PlaceCreate(
    val placeId: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val rating: String? = null, // Rating is String? here
    val notes: String? = null,
    val visitStatus: String? = null
)

data class PlaceUpdate(
    val name: String? = null,
    val address: String? = null,
    val rating: String? = null, // Rating is Double? here
    val notes: String? = null
)