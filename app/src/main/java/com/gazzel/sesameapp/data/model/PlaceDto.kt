package com.gazzel.sesameapp.data.model

// Import SerializedName for Gson
import com.google.gson.annotations.SerializedName
// Keep Timestamp for now, but verify JSON format (see notes below)
import com.google.firebase.Timestamp

data class PlaceDto(
    // Use @SerializedName if JSON key differs from Kotlin property name
    // If JSON key is exactly "id", you might not need the annotation, but it's safer to include it.
    @SerializedName("id")
    val id: String = "",
    @SerializedName("name")
    val name: String = "",
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("address")
    val address: String = "",
    @SerializedName("latitude")
    val latitude: Double = 0.0,
    @SerializedName("longitude")
    val longitude: Double = 0.0,
    @SerializedName("rating")
    val rating: String? = null, // Keep as String?
    @SerializedName("photoUrl")
    val photoUrl: String? = null,
    @SerializedName("websiteUrl")
    val websiteUrl: String? = null,
    @SerializedName("phoneNumber")
    val phoneNumber: String? = null,
    @SerializedName("openingHours")
    val openingHours: List<OpeningHoursDto>? = null,
    @SerializedName("types")
    val types: List<String> = emptyList(),
    @SerializedName("priceLevel")
    val priceLevel: Int? = null,

    // *** Timestamp Handling - IMPORTANT ***
    // Check your actual API JSON response. How are timestamps sent?
    // Option 1: ISO 8601 String (e.g., "2023-10-27T10:00:00Z") -> Use String type here
    // Option 2: Unix Milliseconds (e.g., 1698397200000) -> Use Long type here
    // Option 3: Firebase Timestamp object -> Requires custom Gson TypeAdapter (More complex)
    // Assuming String for now as an example, ADJUST BASED ON YOUR API:
    @SerializedName("createdAt")
    val createdAt: String? = null, // Example: Changed to String? - ADJUST THIS
    @SerializedName("updatedAt")
    val updatedAt: String? = null // Example: Changed to String? - ADJUST THIS

    // If your API REALLY sends Firestore Timestamps in JSON (unlikely for standard REST),
    // you'd keep `val createdAt: Timestamp`, but need to register a TypeAdapter with Gson.
    // val createdAt: Timestamp = Timestamp.now(),
    // val updatedAt: Timestamp = Timestamp.now()
)

data class OpeningHoursDto(
    // Add @SerializedName here too if needed
    @SerializedName("dayOfWeek")
    val dayOfWeek: Int = 0,
    @SerializedName("openTime")
    val openTime: String = "",
    @SerializedName("closeTime")
    val closeTime: String = "",
    @SerializedName("isOpen")
    val isOpen: Boolean = true
)