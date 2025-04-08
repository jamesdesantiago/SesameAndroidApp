// app/src/main/java/com/gazzel/sesameapp/data/mapper/ListMapper.kt

package com.gazzel.sesameapp.data.mapper

import com.gazzel.sesameapp.domain.model.SesameList
import com.gazzel.sesameapp.domain.model.ListResponse // Assume ListResponse is identical/similar enough for now
import com.gazzel.sesameapp.data.service.ListCreate as ServiceListCreate // Alias service DTO
import com.gazzel.sesameapp.data.service.ListUpdate as ServiceListUpdate // Alias service DTO
import java.util.Date

// Maps the API Response DTO to the Domain Model
fun ListResponse.toDomainModel(): SesameList {
    // !! IMPORTANT !! Adapt this mapping based on the ACTUAL fields
    // in ListResponse vs SesameList.
    // Assuming they have similar core fields for now.
    return SesameList(
        id = this.id,
        title = this.name, // Map API 'name' to domain 'title'
        description = this.description ?: "",
        isPublic = !this.isPrivate, // Map API 'isPrivate' to domain 'isPublic'
        createdAt = this.createdAt, // Assuming Long timestamps match
        updatedAt = this.updatedAt,
        userId = "", // ListResponse might not have userId directly, how is it determined? Fetch separately?
        places = emptyList() // Map PlaceDto to PlaceItem if needed
        // places = this.places?.map { it.toPlaceItem(this.id) } ?: emptyList() // Map PlaceDto to PlaceItem if needed
    )
}

// Maps the Domain Model to the Service DTO for Creating a list
fun SesameList.toServiceCreateDto(): ServiceListCreate {
    return ServiceListCreate(
        name = this.title, // Map domain 'title' to API 'name'
        isPrivate = !this.isPublic, // Map domain 'isPublic' to API 'isPrivate'
        collaborators = emptyList() // Add collaborators if needed
    )
}

// Maps the Domain Model to the Service DTO for Updating a list
fun SesameList.toServiceUpdateDto(): ServiceListUpdate {
    return ServiceListUpdate(
        name = this.title, // Map domain 'title' to API 'name'
        isPrivate = !this.isPublic // Map domain 'isPublic' to API 'isPrivate'
    )
}

// Add PlaceDto -> PlaceItem mapping if needed and not present elsewhere
// import com.gazzel.sesameapp.data.remote.dto.PlaceDto
// import com.gazzel.sesameapp.domain.model.PlaceItem
// fun PlaceDto.toPlaceItem(listId: String): PlaceItem { ... }