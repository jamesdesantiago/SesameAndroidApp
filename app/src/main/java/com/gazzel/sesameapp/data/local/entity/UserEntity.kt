// app/src/main/java/com/gazzel/sesameapp/data/local/entity/UserEntity.kt
package com.gazzel.sesameapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users") // Keep Room annotations
data class UserEntity(
    @PrimaryKey
    val id: String, // Assuming ID comes from Firebase Auth (String)
    val email: String,
    val username: String?,
    val displayName: String?,
    val profilePicture: String?
    // Add any other fields you might store LOCALLY but don't get from the user profile API initially
)