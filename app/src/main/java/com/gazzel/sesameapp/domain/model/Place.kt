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