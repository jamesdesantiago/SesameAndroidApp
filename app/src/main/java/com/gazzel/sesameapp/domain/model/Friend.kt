package com.gazzel.sesameapp.domain.model

data class Friend(
    val id: String,
    val username: String,
    val displayName: String?,
    val profilePicture: String?,
    val listCount: Int,
    val isFollowing: Boolean
)
