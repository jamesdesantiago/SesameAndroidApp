// app/src/main/java/com/gazzel/sesameapp/data/mapper/UserMapper.kt
package com.gazzel.sesameapp.data.mapper

// Import all three User representations
import com.gazzel.sesameapp.data.local.entity.UserEntity
import com.gazzel.sesameapp.data.remote.dto.UserDto
import com.gazzel.sesameapp.domain.model.User as DomainUser // Alias domain model
import com.gazzel.sesameapp.domain.model.Friend as DomainFriend

// Maps Network DTO -> Domain Model
fun UserDto.toDomain(): DomainUser {
    return DomainUser(
        id = this.id,
        email = this.email,
        username = this.username,
        displayName = this.displayName,
        profilePicture = this.profilePicture
    )
}

// Maps Database Entity -> Domain Model
fun UserEntity.toDomain(): DomainUser {
    return DomainUser(
        id = this.id,
        email = this.email,
        username = this.username,
        displayName = this.displayName,
        profilePicture = this.profilePicture
    )
}

// Maps Domain Model -> Database Entity
fun DomainUser.toEntity(): UserEntity {
    return UserEntity(
        id = this.id,
        email = this.email,
        username = this.username,
        displayName = this.displayName,
        profilePicture = this.profilePicture
    )
}

/**
 * Maps the Network User DTO to the Domain layer Friend model.
 * Requires the 'isFollowing' status to be passed in.
 */
fun UserDto.toDomainFriend(isFollowing: Boolean): DomainFriend { // <<< CHANGED input type
    return DomainFriend(
        id = this.id, // Assuming domain Friend uses String ID, DTO also uses String
        username = this.username ?: this.email.substringBefore('@'), // Use username or derive from email
        displayName = this.displayName,
        profilePicture = this.profilePicture,
        // listCount = this.listCount ?: 0, // If UserDto has counts
        listCount = 0, // Default if UserDto doesn't include counts
        isFollowing = isFollowing
    )
}