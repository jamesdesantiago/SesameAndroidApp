package com.gazzel.sesameapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.gazzel.sesameapp.domain.model.PlaceItem

@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val listId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): PlaceItem {
        return PlaceItem(
            id = id,
            name = name,
            address = address,
            latitude = latitude,
            longitude = longitude,
            notes = description,
            rating = null,
            visitStatus = null,
            listId = listId,
        )
    }

    companion object {
        fun fromDomain(place: PlaceItem): PlaceEntity {
            return PlaceEntity(
                id = place.id.toString(),
                name = place.name,
                description = place.notes ?: "",
                address = place.address,
                latitude = place.latitude,
                longitude = place.longitude,
                listId = place.listId
            )
        }
    }
} 