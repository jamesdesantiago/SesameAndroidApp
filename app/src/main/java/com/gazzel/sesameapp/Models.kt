package com.gazzel.sesameapp

data class ListCreate(
    val name: String,
    val description: String? = null,
    val isPrivate: Boolean = false,
    val collaborators: List<String> = emptyList()
)

data class ListResponse(
    val id: Int,
    val name: String,
    val description: String?,
    val isPrivate: Boolean? = true,    // now nullable, defaulting to true if missing
    val collaborators: List<String> = emptyList()
)

data class ListUpdate(
    val name: String? = null,          // nullable so you can pass null to not update the name
    val isPrivate: Boolean? = null     // nullable so you can pass null when not updating privacy
)
