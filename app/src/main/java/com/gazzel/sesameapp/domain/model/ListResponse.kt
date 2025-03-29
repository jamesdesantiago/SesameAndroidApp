package com.gazzel.sesameapp.domain.model

data class ListResponse(
    val id: String,
    val name: String,
    val description: String,
    val isPublic: Boolean,
    val places: List<PlaceItem>,
    val createdAt: Long,
    val updatedAt: Long
) 