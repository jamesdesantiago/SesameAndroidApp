package com.gazzel.sesameapp.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class PlaceDto(
    @PropertyName("id")
    val id: String = "",
    @PropertyName("name")
    val name: String = "",
    @PropertyName("description")
    val description: String? = null,
    @PropertyName("address")
    val address: String = "",
    @PropertyName("latitude")
    val latitude: Double = 0.0,
    @PropertyName("longitude")
    val longitude: Double = 0.0,
    @PropertyName("rating")
    val rating: Float? = null,
    @PropertyName("photoUrl")
    val photoUrl: String? = null,
    @PropertyName("websiteUrl")
    val websiteUrl: String? = null,
    @PropertyName("phoneNumber")
    val phoneNumber: String? = null,
    @PropertyName("openingHours")
    val openingHours: List<OpeningHoursDto>? = null,
    @PropertyName("types")
    val types: List<String> = emptyList(),
    @PropertyName("priceLevel")
    val priceLevel: Int? = null,
    @PropertyName("createdAt")
    val createdAt: Timestamp = Timestamp.now(),
    @PropertyName("updatedAt")
    val updatedAt: Timestamp = Timestamp.now()
)

data class OpeningHoursDto(
    @PropertyName("dayOfWeek")
    val dayOfWeek: Int = 0,
    @PropertyName("openTime")
    val openTime: String = "",
    @PropertyName("closeTime")
    val closeTime: String = "",
    @PropertyName("isOpen")
    val isOpen: Boolean = true
) 