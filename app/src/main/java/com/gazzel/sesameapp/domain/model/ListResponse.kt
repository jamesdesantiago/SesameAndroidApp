package com.gazzel.sesameapp.domain.model

import com.gazzel.sesameapp.data.remote.dto.PlaceDto

data class ListResponse(
    val id: String,
    val name: String,
    val description: String?,
    val isPrivate: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val collaborators: List<String>? = null,
    val places: List<PlaceDto>? = null // <-- MUST be List<PlaceDto>? (or your specific DTO type)
    // NOT List<Place>?, List<PlaceItem>?, List<Any>? etc.
)