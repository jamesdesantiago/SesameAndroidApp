package com.gazzel.sesameapp.data.repository

import com.gazzel.sesameapp.data.service.ListService
import com.gazzel.sesameapp.domain.model.PlaceItem
import com.gazzel.sesameapp.domain.repository.PlaceRepository
import com.gazzel.sesameapp.domain.util.Resource
import com.gazzel.sesameapp.data.local.dao.PlaceDao
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class PlaceRepositoryImpl @Inject constructor(
    private val listService: ListService,
    private val placeDao: PlaceDao
) : PlaceRepository {

    override suspend fun getPlaces(): Resource<List<PlaceItem>> {
        return try {
            // Try to get from cache first
            val cachedPlaces = placeDao.getAllPlaces().first()
            if (cachedPlaces.isNotEmpty()) {
                return Resource.Success(cachedPlaces)
            }

            // If cache is empty, fetch from network
            val response = listService.getLists()
            if (response.isSuccessful) {
                val lists = response.body() ?: emptyList()
                val allPlaces = mutableListOf<PlaceItem>()

                // Fetch places for each list
                for (list in lists) {
                    val listDetailsResponse = listService.getListDetail(list.id)
                    if (listDetailsResponse.isSuccessful) {
                        listDetailsResponse.body()?.places?.let { places ->
                            allPlaces.addAll(places)
                        }
                    }
                }

                // Cache the places
                placeDao.insertPlaces(allPlaces)
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
            // Try to get from cache first
            val cachedPlaces = placeDao.getPlacesByListId(listId).first()
            if (cachedPlaces.isNotEmpty()) {
                return Resource.Success(cachedPlaces)
            }

            // If cache is empty, fetch from network
            val response = listService.getListDetail(listId)
            if (response.isSuccessful) {
                val places = response.body()?.places ?: emptyList()
                // Cache the places
                placeDao.insertPlaces(places)
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
            val response = listService.addPlace(place)
            if (response.isSuccessful) {
                val addedPlace = response.body()
                if (addedPlace != null) {
                    placeDao.insertPlace(addedPlace)
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
            val response = listService.updatePlace(place)
            if (response.isSuccessful) {
                val updatedPlace = response.body()
                if (updatedPlace != null) {
                    placeDao.updatePlace(updatedPlace)
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
            val response = listService.deletePlace(placeId)
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
} 