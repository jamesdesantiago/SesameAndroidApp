package com.gazzel.sesameapp.data.remote.dto

data class ListCreateDto( // Moved and renamed
    val name: String,
    val isPrivate: Boolean,
    val collaborators: List<String> // Assuming emails or IDs
)

data class ListUpdateDto( // Moved and renamed
    val name: String? = null,
    val isPrivate: Boolean? = null
)

data class CollaboratorAddDto( // Moved and renamed
    val email: String
)