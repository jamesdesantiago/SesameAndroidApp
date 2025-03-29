package com.gazzel.sesameapp.data.mapper

import com.gazzel.sesameapp.data.model.User as DataUser
import com.gazzel.sesameapp.domain.model.User as DomainUser

fun DataUser.toDomain(): DomainUser {
    return DomainUser(
        id = id,
        email = email,
        username = username,
        displayName = displayName,
        profilePicture = profilePicture
    )
}

fun DomainUser.toEntity(): DataUser {
    return DataUser(
        id = id,
        email = email,
        username = username,
        displayName = displayName,
        profilePicture = profilePicture
    )
} 