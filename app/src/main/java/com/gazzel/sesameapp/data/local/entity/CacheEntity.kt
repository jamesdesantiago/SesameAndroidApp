package com.gazzel.sesameapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cache")
data class CacheEntity(
    @PrimaryKey val key: String,
    val data: String, // Change this type or add a TypeConverter if necessary
    val expiryTime: Long
)
