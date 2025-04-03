package com.gazzel.sesameapp.data.repository

import com.google.android.gms.maps.model.LatLng
import com.gazzel.sesameapp.data.local.dao.PlaceDao
import com.gazzel.sesameapp.data.local.entity.PlaceEntity
import com.gazzel.sesameapp.data.service.ListService // Use YOUR ListService
import com.gazzel.sesameapp.data.model.PlaceDto      // Import the DTO
import com.gazzel.sesameapp.domain.exception.AppException
import com.gazzel.sesameapp.domain.model.Place       // Domain Place (used elsewhere)
import com.gazzel.sesameapp.domain.model.PlaceItem   // Domain PlaceItem
import com.gazzel.sesameapp.domain.repository.PlaceRepository
import com.gazzel.sesameapp.domain.util.Resource
import com.gazzel.sesameapp.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PlaceRepositoryImpl @Inject constructor(
    private val listService: ListService,
    private val placeDao: PlaceDao
) : PlaceRepository {

    // --- NEW Helper Mapping Function: PlaceDto -> PlaceItem ---
    // Directly maps the data layer DTO to the domain PlaceItem
    private fun PlaceDto.toPlaceItem(listId: String): PlaceItem {
        return PlaceItem(
            id = this.id,
            name = this.name,
            description = this.description ?: "", // Use DTO description
            address = this.address,
            latitude = this.latitude, // DTO has doubles directly
            longitude = this.longitude,
            listId = listId,
            notes = null, // Notes are not typically in the base PlaceDto
            rating = this.rating, // DTO rating is String?
            visitStatus = null // Visit status not typically in the base PlaceDto
        )
    }

    // --- REMOVE the old Place.toPlaceItem function ---
    // private fun Place.toPlaceItem(listId: String): PlaceItem { ... } // DELETE THIS


    // ---------------------
    // Methods Returning Resource<T> for PlaceItem
    // ---------------------

    override suspend fun getPlaces(): Resource<List<PlaceItem>> {
        return try {
            // Attempt to load from cache first (already returns PlaceItem via PlaceEntity.toDomain())
            val cachedEntities = placeDao.getAllPlaces().first()
            if (cachedEntities.isNotEmpty()) {
                val cachedPlaces = cachedEntities.map { it.toDomain() }
                // Optionally trigger a background refresh here if needed
                return Resource.Success(cachedPlaces)
            }

            // Fetch from network using a dummy token (replace with real auth)
            val token = "dummyToken" // TODO: Replace with actual token retrieval
            val response = listService.getLists(token) // Assuming getLists provides basic list info

            if (response.isSuccessful) {
                val lists = response.body().orEmpty()
                val allPlaceItems = mutableListOf<PlaceItem>()

                // For each list, fetch its details (which contain places)
                // --- START of Loop ---
                for (list in lists) {
                    try {
                        // Assuming getListDetail returns ListResponse which contains List<PlaceDto> or List<Place>
                        val detailsResponse = listService.getListDetail(list.id, token)
                        if (detailsResponse.isSuccessful) {

                            // v--------------------------------------------------v
                            // v PUT THE DEBUGGING SNIPPET HERE                   v
                            // v--------------------------------------------------v
                            detailsResponse.body()?.places?.let { placesList -> // What type is placesList? Check ListResponse definition.
                                // Try adding the explicit type here:
                                val placeItemsInList = placesList.map { placeDto: PlaceDto -> // <-- ADD : PlaceDto HERE for debugging
                                    // If the error moves to THIS line, then placesList does NOT contain PlaceDto.
                                    // Check the actual type of 'placeDto' or 'placesList'.
                                    // If it's PlaceDto, call placeDto.toPlaceItem(list.id)
                                    // If it's Place, call place.toPlaceItem(list.id) (after ensuring Place.toPlaceItem exists)

                                    // Assuming it's PlaceDto based on latest errors:
                                    placeDto.toPlaceItem(list.id)
                                }
                                allPlaceItems.addAll(placeItemsInList)
                            }
                            // ^--------------------------------------------------^
                            // ^ END OF DEBUGGING SNIPPET LOCATION                ^
                            // ^--------------------------------------------------^

                        } else {
                            // Log error for specific list but continue with others
                            // logger.warning("Failed to fetch details for list ${list.id}: ${detailsResponse.code()}")
                        }
                    } catch (e: Exception) {
                        // logger.error("Error processing list ${list.id}", e)
                    }
                }
                // --- END of Loop ---


                // Cache the fetched PlaceItems as PlaceEntities
                val entities = allPlaceItems.map { PlaceEntity.fromDomain(it) }
                if (entities.isNotEmpty()) { // Only insert if we actually got data
                    placeDao.insertPlaces(entities)
                }
                Resource.Success(allPlaceItems)
            } else {
                Resource.Error("Failed to load lists: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            // logger.error("Failed to getPlaces", e)
            Resource.Error(e.message ?: "An unexpected error occurred")
        }
    }

    override suspend fun getPlacesByListId(listId: String): Resource<List<PlaceItem>> {
        return try {
            val cachedEntities = placeDao.getPlacesByListId(listId).first()
            if (cachedEntities.isNotEmpty()) {
                // Return cached PlaceItems
                return Resource.Success(cachedEntities.map { it.toDomain() })
            }

            val token = "dummyToken" // TODO: Get real token
            // Assuming getListDetail returns ListResponse containing List<PlaceDto>
            val response = listService.getListDetail(listId, token)

            if (response.isSuccessful) {
                // Map PlaceDto -> PlaceItem
                val placesDtoList = response.body()?.places.orEmpty() // Assuming places is List<PlaceDto>?
                val placeItems = placesDtoList.map { placeDto -> // 'placeDto' is PlaceDto
                    // Use the new direct mapper (this replaces old line 133)
                    placeDto.toPlaceItem(listId)
                }

                // Cache PlaceItems as PlaceEntities
                val entities = placeItems.map { PlaceEntity.fromDomain(it) }
                if (entities.isNotEmpty()) {
                    placeDao.insertPlaces(entities)
                }
                Resource.Success(placeItems)
            } else {
                Resource.Error("Failed to load places for list $listId: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "An unexpected error occurred")
        }
    }

    // addPlace, updatePlace, deletePlace should be mostly okay as they work with PlaceItem
    // Ensure PlaceDao methods (getPlaceById, deletePlace) exist and are correct

    override suspend fun addPlace(place: PlaceItem): Resource<PlaceItem> {
        return try {
            val token = "dummyToken" // TODO: Replace with actual token retrieval
            // Assuming listService.addPlace expects PlaceItem or a compatible DTO
            // If it expects PlaceCreate, you might need to map PlaceItem -> PlaceCreate here
            val response = listService.addPlace(place.listId, place, token) // Verify service signature
            if (response.isSuccessful) {
                val addedPlace = response.body() // Ensure this returns PlaceItem or compatible
                if (addedPlace != null) {
                    placeDao.insertPlace(PlaceEntity.fromDomain(addedPlace))
                    Resource.Success(addedPlace)
                } else {
                    Resource.Error("Failed to add place: Empty response body")
                }
            } else {
                Resource.Error("Failed to add place: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "An unexpected error occurred")
        }
    }

    override suspend fun updatePlace(place: PlaceItem): Resource<PlaceItem> {
        return try {
            val token = "dummyToken" // TODO: Replace with actual token retrieval
            // Assuming listService.updatePlace expects PlaceItem or a compatible DTO
            // If it expects PlaceUpdate, you might need to map PlaceItem -> PlaceUpdate here
            val response = listService.updatePlace(place.listId, place.id, place, token) // Verify service signature
            if (response.isSuccessful) {
                val updatedPlace = response.body() // Ensure this returns PlaceItem or compatible
                if (updatedPlace != null) {
                    placeDao.updatePlace(PlaceEntity.fromDomain(updatedPlace))
                    Resource.Success(updatedPlace)
                } else {
                    Resource.Error("Failed to update place: Empty response body")
                }
            } else {
                Resource.Error("Failed to update place: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "An unexpected error occurred")
        }
    }

    override suspend fun deletePlace(placeId: String): Resource<Unit> {
        return try {
            val entity: PlaceEntity? = placeDao.getPlaceById(placeId) // Ensure DAO method exists

            if (entity == null) {
                return Resource.Error("Place with ID $placeId not found locally.")
            }
            val listId = entity.listId

            val token = "dummyToken" // TODO: Get real token
            val response = listService.deletePlace(listId, placeId, token)

            if (response.isSuccessful || response.code() == 204) {
                placeDao.deletePlace(placeId) // Ensure DAO method exists
                Resource.Success(Unit)
            } else {
                Resource.Error("Failed to delete place: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "An unexpected error occurred")
        }
    }


    // --- Methods dealing with domain.model.Place remain unimplemented ---

    override suspend fun updatePlace(place: Place): Result<Unit> {
        return Result.error(AppException.UnknownException("Detailed updatePlace(Place) not implemented"))
    }

    override suspend fun getPlaceDetails(placeId: String): Result<Place> {
        return Result.error(AppException.UnknownException("getPlaceDetails not implemented"))
    }

    override suspend fun savePlace(place: Place): Result<String> {
        return Result.error(AppException.UnknownException("savePlace not implemented"))
    }

    override suspend fun getNearbyPlaces(
        location: LatLng,
        radius: Int,
        type: String?
    ): Result<List<Place>> {
        return Result.error(AppException.UnknownException("getNearbyPlaces not implemented"))
    }

    // --- Flow methods ---

    override fun searchPlaces(query: String, location: LatLng, radius: Int): Flow<List<Place>> {
        return flow { throw NotImplementedError("Detailed searchPlaces not implemented") }
    }

    // This observes the CACHED PlaceItem
    override fun getPlaceById(placeId: String): Flow<PlaceItem?> { // Changed return type in Interface too hopefully
        return placeDao.getPlaceByIdFlow(placeId) // Ensure DAO method exists
            .map { entity -> entity?.toDomain() }
    }
}