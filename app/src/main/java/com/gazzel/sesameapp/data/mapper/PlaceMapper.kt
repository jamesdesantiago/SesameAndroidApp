// app/src/main/java/com/gazzel/sesameapp/data/mapper/PlaceMapper.kt
package com.gazzel.sesameapp.data.mapper

// Import Data layer models
import com.gazzel.sesameapp.data.local.entity.PlaceEntity
import com.gazzel.sesameapp.data.remote.dto.OpeningHoursDto
import com.gazzel.sesameapp.data.remote.dto.PlaceDto
// Import Domain layer models
import com.gazzel.sesameapp.domain.model.OpeningHours
import com.gazzel.sesameapp.domain.model.Place // Detailed domain model
import com.gazzel.sesameapp.domain.model.PlaceItem // List item domain model
// Other imports
import com.google.android.gms.maps.model.LatLng
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// --- Date Formatting Helpers (Keep as is) ---
private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}
private fun String?.parseIsoDate(): Date? {
    return this?.let { try { isoFormat.parse(it) } catch (e: Exception) { null } }
}
private fun Date?.formatIsoDate(): String? {
    return this?.let { isoFormat.format(it) }
}

// ==================================================
// Mappings for Detailed Domain Model: Place
// ==================================================

// Maps PlaceDto (Data Layer DTO, assumes detailed structure) -> Place (Domain Layer, detailed)
fun PlaceDto.toDomainPlace(): Place { // Renamed for clarity
    // This mapping assumes PlaceDto contains fields for the detailed Place model
    return Place(
        id = this.id,
        name = this.name,
        description = this.description,
        address = this.address,
        location = LatLng(this.latitude, this.longitude),
        rating = this.rating, // String? -> String?
        photoUrl = this.photoUrl,
        websiteUrl = this.websiteUrl,
        phoneNumber = this.phoneNumber,
        openingHours = this.openingHours?.map { it.toDomainOpeningHours() }, // Use specific mapper name
        types = this.types,
        priceLevel = this.priceLevel,
        // Parse timestamps based on DTO format (assuming String? here)
        createdAt = this.createdAt.parseIsoDate() ?: Date(0),
        updatedAt = this.updatedAt.parseIsoDate() ?: Date(0)
    )
}

// Maps Place (Domain Layer, detailed) -> PlaceDto (Data Layer DTO, assumes detailed structure)
fun Place.toPlaceDto(): PlaceDto { // Renamed for clarity
    return PlaceDto(
        id = this.id,
        name = this.name,
        description = this.description,
        address = this.address,
        latitude = this.location.latitude,
        longitude = this.location.longitude,
        rating = this.rating, // String? -> String?
        photoUrl = this.photoUrl,
        websiteUrl = this.websiteUrl,
        phoneNumber = this.phoneNumber,
        openingHours = this.openingHours?.map { it.toOpeningHoursDto() }, // Use specific mapper name
        types = this.types,
        priceLevel = this.priceLevel,
        // Format timestamps based on DTO format (assuming String? here)
        createdAt = this.createdAt.formatIsoDate(),
        updatedAt = this.updatedAt.formatIsoDate()
    )
}

// --- Opening Hours Mapping (Used by detailed Place) ---

// Maps OpeningHoursDto -> OpeningHours (Domain)
fun OpeningHoursDto.toDomainOpeningHours(): OpeningHours { // Renamed for clarity
    return OpeningHours(
        dayOfWeek = this.dayOfWeek,
        openTime = this.openTime,
        closeTime = this.closeTime,
        isOpen = this.isOpen
    )
}

// Maps OpeningHours (Domain) -> OpeningHoursDto
fun OpeningHours.toOpeningHoursDto(): OpeningHoursDto { // Renamed for clarity
    return OpeningHoursDto(
        dayOfWeek = this.dayOfWeek,
        openTime = this.openTime,
        closeTime = this.closeTime,
        isOpen = this.isOpen
    )
}


// ==================================================
// Mappings for List Item Domain Model: PlaceItem
// ==================================================

/**
 * Maps a PlaceDto (typically embedded in ListDto from API) to a PlaceItem (Domain).
 * Requires the listId to be passed in as the DTO itself doesn't contain it.
 */
fun PlaceDto.toDomainPlaceItem(listId: String): PlaceItem { // Added function
    return PlaceItem(
        id = this.id, // Use PlaceDto ID
        name = this.name,
        description = this.description ?: "", // Handle null description
        address = this.address,
        latitude = this.latitude,
        longitude = this.longitude,
        listId = listId, // Assign the passed-in listId
        notes = null, // DTO likely doesn't have notes, default to null
        rating = this.rating, // Map rating (String?) directly
        visitStatus = null // DTO likely doesn't have visitStatus, default to null
    )
}

/**
 * Maps a PlaceEntity (from Room DB) to a PlaceItem (Domain).
 */
fun PlaceEntity.toDomainPlaceItem(): PlaceItem { // Added function
    return PlaceItem(
        id = this.id,
        name = this.name,
        description = this.description,
        address = this.address,
        latitude = this.latitude,
        longitude = this.longitude,
        listId = this.listId,
        notes = this.notes, // Map notes from Entity
        rating = this.rating, // Map rating from Entity (ensure type matches String?)
        visitStatus = this.visitStatus // Map visitStatus from Entity
    )
}

/**
 * Maps a PlaceItem (Domain) to a PlaceEntity (for Room DB).
 */
fun PlaceItem.toPlaceEntity(): PlaceEntity { // Added function
    return PlaceEntity(
        id = this.id,
        name = this.name,
        description = this.description,
        address = this.address,
        latitude = this.latitude,
        longitude = this.longitude,
        listId = this.listId,
        notes = this.notes, // Map notes to Entity
        rating = this.rating, // Map rating (String?) to Entity
        visitStatus = this.visitStatus, // Map visitStatus to Entity
        // Let Room handle createdAt/updatedAt automatically on insert/update
        // or set them explicitly if needed:
        // createdAt = System.currentTimeMillis(), // Example for insert
        // updatedAt = System.currentTimeMillis() // Example for insert/update
    )
}