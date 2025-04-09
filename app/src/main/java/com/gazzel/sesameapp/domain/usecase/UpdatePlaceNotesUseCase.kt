package com.gazzel.sesameapp.domain.usecase

import com.gazzel.sesameapp.domain.model.PlaceItem
import com.gazzel.sesameapp.domain.repository.PlaceRepository
import com.gazzel.sesameapp.domain.util.Result
import com.gazzel.sesameapp.domain.exception.AppException
import com.gazzel.sesameapp.domain.util.flatMap // Import flatMap
// import kotlinx.coroutines.flow.firstOrNull // No longer needed
import javax.inject.Inject

class UpdatePlaceNotesUseCase @Inject constructor(
    private val placeRepository: PlaceRepository
) {
    suspend operator fun invoke(placeId: String, newNotes: String?): Result<PlaceItem> {
        return try {
            // 1. Get the current PlaceItem using the new suspend function
            val currentItemResult = placeRepository.getPlaceItemById(placeId) // <<< CHANGED

            // 2. Use flatMap to proceed only if getting the item was successful
            currentItemResult.flatMap { currentItem ->
                // Create the updated item
                val updatedItem = currentItem.copy(notes = newNotes)
                // Call the repository update method
                placeRepository.updatePlace(updatedItem) // This returns Result<PlaceItem>
            } // flatMap propagates the error if currentItemResult was Error

        } catch (e: Exception) { // Catch unexpected exceptions during the process
            Result.error(AppException.UnknownException("Failed to update notes", e))
        }
    }
}