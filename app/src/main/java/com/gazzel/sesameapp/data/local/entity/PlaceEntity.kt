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
    val updatedAt: Long = System.currentTimeMillis(),
    // --- Add/Ensure rating is String? ---
    val notes: String? = null,
    val rating: String? = null, // Make sure this is String?
    val visitStatus: String? = null
) {
    fun toDomain(): PlaceItem {
        return PlaceItem(
            id = id,
            name = name,
            address = address,
            latitude = latitude,
            longitude = longitude,
            notes = notes, // Map notes
            rating = rating, // Directly map the String? rating
            visitStatus = visitStatus, // Map visitStatus
            listId = listId,
            description = description // Map description
        )
    }

    companion object {
        fun fromDomain(place: PlaceItem): PlaceEntity {
            return PlaceEntity(
                id = place.id, // Use place.id directly if it's String
                name = place.name,
                description = place.description,
                address = place.address,
                latitude = place.latitude,
                longitude = place.longitude,
                listId = place.listId,
                notes = place.notes,
                rating = place.rating, // Directly map the String? rating
                visitStatus = place.visitStatus
                // createdAt/updatedAt will use defaults or could be passed if needed
            )
        }
    }
}