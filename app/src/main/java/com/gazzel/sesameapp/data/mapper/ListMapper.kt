package com.gazzel.sesameapp.data.mapper

import com.gazzel.sesameapp.data.model.ListDto
import com.gazzel.sesameapp.domain.model.List
import java.util.Date

fun ListDto.toDomain(): List {
    return List(
        id = id,
        title = title,
        description = description,
        userId = userId,
        createdAt = createdAt.toDate(),
        updatedAt = updatedAt.toDate(),
        isPublic = isPublic,
        placeCount = placeCount,
        followerCount = followerCount
    )
}

fun List.toDto(): ListDto {
    return ListDto(
        id = id,
        title = title,
        description = description,
        userId = userId,
        createdAt = com.google.firebase.Timestamp(Date(createdAt.time)),
        updatedAt = com.google.firebase.Timestamp(Date(updatedAt.time)),
        isPublic = isPublic,
        placeCount = placeCount,
        followerCount = followerCount
    )
} 