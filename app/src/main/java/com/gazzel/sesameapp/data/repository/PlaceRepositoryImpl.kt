package com.gazzel.sesameapp.data.repository

import com.google.android.gms.maps.model.LatLng
import com.gazzel.sesameapp.data.local.dao.PlaceDao
import com.gazzel.sesameapp.data.local.entity.PlaceEntity
import com.gazzel.sesameapp.data.service.ListService
import com.gazzel.sesameapp.domain.exception.AppException
import com.gazzel.sesameapp.domain.model.Place
import com.gazzel.sesameapp.domain.model.PlaceItem
import com.gazzel.sesameapp.domain.repository.PlaceRepository
import com.gazzel.sesameapp.domain.util.Resource
import com.gazzel.sesameapp.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class PlaceRepositoryImpl @Inject constructor(
    private val listService: ListService,
    private val placeDao: PlaceDao
) : PlaceRepository {

    // ---------------------
    // Methods Returning Resource<T> for PlaceItem
    // ---------------------

    override suspend fun getPlaces(): Resource<List<PlaceItem>> {
        return try {
            // Attempt to load from cache
            val cachedEntities = placeDao.getAllPlaces().first()
            if (cachedEntities.isNotEmpty()) {
                val cachedPlaces = cachedEntities.map { it.toDomain() }
                return Resource.Success(cachedPlaces)
            }

            // Fetch from network using a dummy token
            val response = listService.getLists("dummyToken")
            if (response.isSuccessful) {
                val lists = response.body().orEmpty()
                val allPlaces = mutableListOf<PlaceItem>()

                // For each list, fetch details
                for (list in lists) {
                    val detailsResponse = listService.getListDetail(list.id, "dummyToken")
                    if (detailsResponse.isSuccessful) {
                        detailsResponse.body()?.places?.let { places ->
                            allPlaces.addAll(places)
                        }
                    }
                }

                // Cache data
                val entities = allPlaces.map { PlaceEntity.fromDomain(it) }
                placeDao.insertPlaces(entities)
                Resource.Success(allPlaces)
            } else {
                Resource.Error("Failed to load places: ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get places")
        }
    }

    override suspend fun getPlacesByListId(listId: String): Resource<List<PlaceItem>> {
        return try {
            val cachedEntities = placeDao.getPlacesByListId(listId).first()
            if (cachedEntities.isNotEmpty()) {
                val cachedPlaces = cachedEntities.map { it.toDomain() }
                return Resource.Success(cachedPlaces)
            }

            val response = listService.getListDetail(listId, "dummyToken")
            if (response.isSuccessful) {
                val places = response.body()?.places.orEmpty()
                val entities = places.map { PlaceEntity.fromDomain(it) }
                placeDao.insertPlaces(entities)
                Resource.Success(places)
            } else {
                Resource.Error("Failed to load places: ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get places")
        }
    }

    override suspend fun addPlace(place: PlaceItem): Resource<PlaceItem> {
        return try {
            val token = "dummyToken"
            val response = listService.addPlace(place.listId, place, token)
            if (response.isSuccessful) {
                val addedPlace = response.body()
                if (addedPlace != null) {
                    placeDao.insertPlace(PlaceEntity.fromDomain(addedPlace))
                    Resource.Success(addedPlace)
                } else {
                    Resource.Error("Failed to add place: No response body")
                }
            } else {
                Resource.Error("Failed to add place: ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to add place")
        }
    }

    override suspend fun updatePlace(place: PlaceItem): Resource<PlaceItem> {
        return try {
            val token = "dummyToken"
            val response = listService.updatePlace(place.listId, place.id, place, token)
            if (response.isSuccessful) {
                val updatedPlace = response.body()
                if (updatedPlace != null) {
                    placeDao.updatePlace(PlaceEntity.fromDomain(updatedPlace))
                    Resource.Success(updatedPlace)
                } else {
                    Resource.Error("Failed to update place: No response body")
                }
            } else {
                Resource.Error("Failed to update place: ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update place")
        }
    }

    override suspend fun deletePlace(placeId: String): Resource<Unit> {
        return try {
            val token = "dummyToken"
            val entity = placeDao.getPlaceById(placeId)
            if (entity == null) {
                return Resource.Error("Place not found in cache")
            }

            val response = listService.deletePlace(entity.listId, placeId, token)
            if (response.isSuccessful) {
                placeDao.deletePlace(placeId)
                Resource.Success(Unit)
            } else {
                Resource.Error("Failed to delete place: ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to delete place")
        }
    }

    // ---------------------
    // Methods Returning Result<T> for Place
    // ---------------------

    override suspend fun updatePlace(place: Place): Result<Unit> {
        // Not implemented
        return Result.error(AppException.UnknownException("updatePlace(Place) not implemented"))
    }

    override suspend fun getPlaceDetails(placeId: String): Result<Place> {
        // Not implemented
        return Result.error(AppException.UnknownException("getPlaceDetails not implemented"))
    }

    override suspend fun savePlace(place: Place): Result<String> {
        // Not implemented
        return Result.error(AppException.UnknownException("savePlace not implemented"))
    }

    override suspend fun getNearbyPlaces(
        location: LatLng,
        radius: Int,
        type: String?
    ): Result<List<Place>> {
        // Not implemented
        return Result.error(AppException.UnknownException("getNearbyPlaces not implemented"))
    }

    // ---------------------
    // Flow-based methods
    // ---------------------

    override fun searchPlaces(query: String, location: LatLng, radius: Int): Flow<List<Place>> {
        // Not implemented
        return flow { throw NotImplementedError("searchPlaces not implemented") }
    }

    override fun getPlaceById(placeId: String): Flow<Place?> {
        // Not implemented
        return flow { throw NotImplementedError("getPlaceById not implemented") }
    }
}
