package com.gazzel.sesameapp.domain.usecase

import com.gazzel.sesameapp.domain.model.PlaceItem
import com.gazzel.sesameapp.domain.repository.PlaceRepository
import com.gazzel.sesameapp.domain.util.Result
import com.gazzel.sesameapp.domain.exception.AppException
import kotlinx.coroutines.flow.firstOrNull // To get the item
import javax.inject.Inject

class UpdatePlaceNotesUseCase @Inject constructor(
    private val placeRepository: PlaceRepository
) {
    suspend operator fun invoke(placeId: String, newNotes: String?): Result<PlaceItem> {
        return try {
            // 1. Get the current PlaceItem (suspend function might be better in repo than flow)
            // Let's assume we add a suspend fun getPlaceItemById(id): Result<PlaceItem> to PlaceRepository
            val currentItemResult = placeRepository.getPlaceItemById(placeId) // Needs adding to repo

            currentItemResult.flatMap { currentItem ->
                // 2. Create the updated item
                val updatedItem = currentItem.copy(notes = newNotes)
                // 3. Call the repository update method
                placeRepository.updatePlace(updatedItem) // This returns Result<PlaceItem>
            }
        } catch (e: Exception) {
            Result.error(AppException.UnknownException("Failed to update notes", e))
        }
    }
}
// Note: Requires adding suspend fun getPlaceItemById(id: String): Result<PlaceItem> to PlaceRepository interface and implementation (fetching from DAO).