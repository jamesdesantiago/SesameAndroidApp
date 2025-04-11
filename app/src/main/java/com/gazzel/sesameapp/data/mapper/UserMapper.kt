package com.gazzel.sesameapp.data.mapper

import com.gazzel.sesameapp.data.model.User as DataUser
import com.gazzel.sesameapp.domain.model.User as DomainUser
import com.gazzel.sesameapp.domain.model.Friend as DomainFriend

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

/**
 * Maps the Data layer User model to the Domain layer Friend model.
 * Requires the 'isFollowing' status to be passed in, as the User DTO itself doesn't contain it.
 */
fun DataUser.toDomainFriend(isFollowing: Boolean): DomainFriend {
    return DomainFriend(
        id = this.id.toString(), // Assuming domain Friend uses String ID and data User uses Int ID
        // Provide a sensible fallback if username is null
        username = this.username ?: this.email.substringBefore('@'),
        displayName = this.displayName,
        profilePicture = this.profilePicture,
        listCount = 0, // The paginated User DTO from the API doesn't include listCount. Defaulting to 0.
        // A separate API call or a different DTO would be needed if this count is crucial here.
        isFollowing = isFollowing // Set based on the context (e.g., true for /following results)
    )
}