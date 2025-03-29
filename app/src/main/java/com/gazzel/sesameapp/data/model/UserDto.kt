package com.gazzel.sesameapp.data.model

import com.google.firebase.Timestamp

data class UserDto(
    val id: String = "",
    val username: String = "",
    val displayName: String? = null,
    val profilePicture: String? = null,
    val email: String = "",
    val listCount: Int = 0,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
) 