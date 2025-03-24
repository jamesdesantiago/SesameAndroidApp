package com.gazzel.sesameapp

import android.util.Log
import com.google.gson.annotations.SerializedName

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
    val isPrivate: Boolean,
    val collaborators: List<String>? = null,
    val places: List<PlaceItem>? = null
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

data class PlaceCreate(
    val placeId: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val rating: String? = null,
    val notes: String? = null, // Make notes optional
    val visitStatus: String? = null
)

data class PlaceItem(
    val id: Int,
    val name: String,
    val address: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    val notes: String?,
    val rating: String? = null,
    val visitStatus: String? = null
)

data class PlaceUpdate(
    val notes: String? = null
)

data class Friend(
    val initials: String,
    val username: String,
    val isFollowing: Boolean
)