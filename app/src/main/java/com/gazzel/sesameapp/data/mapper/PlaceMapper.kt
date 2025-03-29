package com.gazzel.sesameapp.data.mapper

import com.gazzel.sesameapp.data.model.OpeningHoursDto
import com.gazzel.sesameapp.data.model.PlaceDto
import com.gazzel.sesameapp.domain.model.OpeningHours
import com.gazzel.sesameapp.domain.model.Place
import com.google.android.gms.maps.model.LatLng
import java.util.Date

fun PlaceDto.toDomain(): Place {
    return Place(
        id = id,
        name = name,
        description = description,
        address = address,
        location = LatLng(latitude, longitude),
        rating = rating,
        photoUrl = photoUrl,
        websiteUrl = websiteUrl,
        phoneNumber = phoneNumber,
        openingHours = openingHours?.map { it.toDomain() },
        types = types,
        priceLevel = priceLevel,
        createdAt = createdAt.toDate(),
        updatedAt = updatedAt.toDate()
    )
}

fun Place.toDto(): PlaceDto {
    return PlaceDto(
        id = id,
        name = name,
        description = description,
        address = address,
        latitude = location.latitude,
        longitude = location.longitude,
        rating = rating,
        photoUrl = photoUrl,
        websiteUrl = websiteUrl,
        phoneNumber = phoneNumber,
        openingHours = openingHours?.map { it.toDto() },
        types = types,
        priceLevel = priceLevel,
        createdAt = com.google.firebase.Timestamp(Date(createdAt.time)),
        updatedAt = com.google.firebase.Timestamp(Date(updatedAt.time))
    )
}

fun OpeningHoursDto.toDomain(): OpeningHours {
    return OpeningHours(
        dayOfWeek = dayOfWeek,
        openTime = openTime,
        closeTime = closeTime,
        isOpen = isOpen
    )
}

fun OpeningHours.toDto(): OpeningHoursDto {
    return OpeningHoursDto(
        dayOfWeek = dayOfWeek,
        openTime = openTime,
        closeTime = closeTime,
        isOpen = isOpen
    )
} 