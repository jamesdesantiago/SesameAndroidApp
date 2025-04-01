package com.gazzel.sesameapp.data.mapper

import com.gazzel.sesameapp.data.model.ListDto
import com.gazzel.sesameapp.domain.model.SesameList
import java.util.Date

fun ListDto.toDomain(): SesameList {
    return SesameList(
        id = id,
        title = title,
        description = description.toString(),
        userId = userId,
        createdAt = createdAt.toDate().time,
        updatedAt = updatedAt.toDate().time,
        isPublic = isPublic,
        places = emptyList()
    )
}

fun SesameList.toDto(): ListDto {
    return ListDto(
        id = id,
        title = title,
        description = description,
        userId = userId,
        createdAt = com.google.firebase.Timestamp(Date(createdAt)),
        updatedAt = com.google.firebase.Timestamp(Date(updatedAt)),
        isPublic = isPublic,
        placeCount = 0,
        followerCount = 0
    )
} 