package com.gazzel.sesameapp.data.mapper

import com.gazzel.sesameapp.data.model.OpeningHoursDto
import com.gazzel.sesameapp.data.model.PlaceDto
import com.gazzel.sesameapp.domain.model.OpeningHours
import com.gazzel.sesameapp.domain.model.Place
import com.google.android.gms.maps.model.LatLng
// Keep this import ONLY if you need to CREATE Timestamps when converting FROM domain Date
// import com.google.firebase.Timestamp
import java.util.Date
import java.text.SimpleDateFormat // For parsing/formatting date strings
import java.util.Locale
import java.util.TimeZone

// --- Date Formatting ---
// Define standard formatters (adjust format string if your API uses a different one)
// ISO 8601 format often used in APIs
private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}
// You might need another format if your API is different

// Helper function to parse String? to Date? safely
private fun String?.parseIsoDate(): Date? {
    return this?.let {
        try {
            isoFormat.parse(it)
        } catch (e: Exception) {
            null // Return null if parsing fails
        }
    }
}

// Helper function to format Date? to String? safely
private fun Date?.formatIsoDate(): String? {
    return this?.let { isoFormat.format(it) }
}


// Maps PlaceDto (Data Layer - String? rating, String? timestamps)
// to Place (Domain - String? rating, Date timestamps)
fun PlaceDto.toDomain(): Place {
    return Place(
        id = this.id,
        name = this.name,
        description = this.description,
        address = this.address,
        location = LatLng(this.latitude, this.longitude),
        rating = this.rating, // Direct assignment String? -> String?
        photoUrl = this.photoUrl,
        websiteUrl = this.websiteUrl,
        phoneNumber = this.phoneNumber,
        openingHours = this.openingHours?.map { it.toDomain() },
        types = this.types,
        priceLevel = this.priceLevel,
        // Parse the String? from DTO into Date? (handle null/errors)
        // Provide a default Date if parsing fails or DTO field is null
        createdAt = this.createdAt.parseIsoDate() ?: Date(0), // Default to epoch if null/invalid
        updatedAt = this.updatedAt.parseIsoDate() ?: Date(0)  // Default to epoch if null/invalid
        // Or throw an exception if timestamps are mandatory? Depends on requirements.
    )
}

// Maps Place (Domain - String? rating, Date timestamps)
// to PlaceDto (Data Layer - String? rating, String? timestamps)
fun Place.toDto(): PlaceDto {
    return PlaceDto(
        id = this.id,
        name = this.name,
        description = this.description,
        address = this.address,
        latitude = this.location.latitude,
        longitude = this.location.longitude,
        rating = this.rating, // Direct assignment String? -> String?
        photoUrl = this.photoUrl,
        websiteUrl = this.websiteUrl,
        phoneNumber = this.phoneNumber,
        openingHours = this.openingHours?.map { it.toDto() },
        types = this.types,
        priceLevel = this.priceLevel,
        // Format the Date from domain model into String? for DTO
        createdAt = this.createdAt.formatIsoDate(),
        updatedAt = this.updatedAt.formatIsoDate()
        // DO NOT create new Firestore Timestamps here if PlaceDto expects String/Long
        // createdAt = Timestamp(this.createdAt), // REMOVE/REPLACE
        // updatedAt = Timestamp(this.updatedAt)   // REMOVE/REPLACE
    )
}

// --- Opening Hours Mapping (Should be okay) ---

fun OpeningHoursDto.toDomain(): OpeningHours {
    return OpeningHours(
        dayOfWeek = this.dayOfWeek,
        openTime = this.openTime,
        closeTime = this.closeTime,
        isOpen = this.isOpen
    )
}

fun OpeningHours.toDto(): OpeningHoursDto {
    return OpeningHoursDto(
        dayOfWeek = this.dayOfWeek,
        openTime = this.openTime,
        closeTime = this.closeTime,
        isOpen = this.isOpen
    )
}