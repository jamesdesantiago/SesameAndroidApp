package com.gazzel.sesameapp.domain.usecase

import com.gazzel.sesameapp.domain.repository.PlaceRepository
import com.gazzel.sesameapp.domain.util.Result
import javax.inject.Inject

class DeletePlaceItemUseCase @Inject constructor(
    private val placeRepository: PlaceRepository
) {
    suspend operator fun invoke(placeId: String): Result<Unit> {
        // Use the repo method that takes only placeId
        return placeRepository.deletePlace(placeId)
    }
}