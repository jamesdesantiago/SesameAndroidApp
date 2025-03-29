package com.gazzel.sesameapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val id: String,
    val email: String,
    val username: String?,
    @SerializedName("display_name")
    val displayName: String?,
    @SerializedName("profile_picture")
    val profilePicture: String?
) 