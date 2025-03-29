package com.gazzel.sesameapp.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class ListDto(
    @PropertyName("id")
    val id: String = "",
    @PropertyName("title")
    val title: String = "",
    @PropertyName("description")
    val description: String? = null,
    @PropertyName("userId")
    val userId: String = "",
    @PropertyName("createdAt")
    val createdAt: Timestamp = Timestamp.now(),
    @PropertyName("updatedAt")
    val updatedAt: Timestamp = Timestamp.now(),
    @PropertyName("isPublic")
    val isPublic: Boolean = false,
    @PropertyName("placeCount")
    val placeCount: Int = 0,
    @PropertyName("followerCount")
    val followerCount: Int = 0
) 