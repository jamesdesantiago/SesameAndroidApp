package com.gazzel.sesameapp.data.remote.dto

data class PlaceCreateDto( // Moved and renamed
    val placeId: String, // Google Place ID?
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val rating: String? = null, // User rating?
    // Add notes/visitStatus if needed by API on create
)

data class PlaceUpdateDto( // Moved and renamed
    val name: String? = null,
    val address: String? = null,
    val rating: String? = null,
    val notes: String? = null
    // Add visitStatus if needed by API on update
)