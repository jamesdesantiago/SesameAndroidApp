package com.gazzel.sesameapp.domain.model

data class User(
    val id: String,
    val email: String,
    val username: String?,
    val displayName: String?,
    val profilePicture: String?
)