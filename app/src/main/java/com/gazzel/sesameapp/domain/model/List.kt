package com.gazzel.sesameapp.domain.model

import android.util.Log
import com.google.gson.annotations.SerializedName
import java.util.Date

data class SesameList(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val isPublic: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val userId: String = "",
    val places: List<Place> = emptyList()
)

data class ListCreate(
    val name: String,
    val description: String? = null,
    val isPrivate: Boolean = false,
    val collaborators: List<String> = emptyList()
)

data class ListResponse(
    val id: String,
    val name: String,
    val description: String?,
    val isPrivate: Boolean,
    val collaborators: List<String>? = null,
    val places: List<Place>? = null // Use Place instead of PlaceItem for consistency
) {
    init {
        Log.d("ListResponse", "Deserialized: id=$id, name=$name, isPrivate=$isPrivate, collaborators=$collaborators")
    }
}
data class ListUpdate(
    val name: String? = null,
    val isPrivate: Boolean? = null
)

data class CollaboratorAdd(
    val email: String
) 