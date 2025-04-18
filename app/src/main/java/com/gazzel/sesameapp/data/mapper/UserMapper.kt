// app/src/main/java/com/gazzel/sesameapp/data/mapper/UserMapper.kt
package com.gazzel.sesameapp.data.mapper

// Import all three User representations
import com.gazzel.sesameapp.data.local.entity.UserEntity
import com.gazzel.sesameapp.data.remote.dto.UserDto // Use the updated UserDto
import com.gazzel.sesameapp.domain.model.User as DomainUser // Alias domain model
import com.gazzel.sesameapp.domain.model.Friend as DomainFriend

// Maps Network DTO -> Domain Model (User)
fun UserDto.toDomain(): DomainUser {
    return DomainUser(
        // Ensure ID mapping is correct (Int from DTO -> String for Domain?)
        id = this.id.toString(), // Assuming DomainUser ID is String
        email = this.email,
        username = this.username,
        displayName = this.displayName,
        profilePicture = this.profilePicture
        // isFollowing field is NOT part of the core Domain User model
    )
}

// Maps Database Entity -> Domain Model (User)
fun UserEntity.toDomain(): DomainUser {
    return DomainUser(
        id = this.id, // Assuming UserEntity ID is String
        email = this.email,
        username = this.username,
        displayName = this.displayName,
        profilePicture = this.profilePicture
    )
}

// Maps Domain Model (User) -> Database Entity
fun DomainUser.toEntity(): UserEntity {
    return UserEntity(
        id = this.id, // Assuming UserEntity ID is String
        email = this.email,
        username = this.username,
        displayName = this.displayName,
        profilePicture = this.profilePicture
    )
}

/**
 * Maps the Network User DTO to the Domain layer Friend model.
 * Now uses the isFollowing field directly from the DTO.
 */
// --- REMOVED isFollowing parameter from the function signature ---
fun UserDto.toDomainFriend(): DomainFriend {
    return DomainFriend(
        id = this.id.toString(), // Assuming domain Friend ID is String
        username = this.username ?: this.email.substringBefore('@'), // Use username or derive from email
        displayName = this.displayName,
        profilePicture = this.profilePicture,
        // listCount = this.listCount ?: 0, // Map if available in UserDto
        listCount = 0, // Default if UserDto doesn't include counts
        // --- Get isFollowing directly from DTO ---
        isFollowing = this.isFollowing ?: false // Use DTO field, default to false if null/missing
    )
}