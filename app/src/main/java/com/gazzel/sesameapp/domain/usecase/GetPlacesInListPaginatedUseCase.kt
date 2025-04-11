// Create File: app/src/main/java/com/gazzel/sesameapp/domain/usecase/GetPlacesInListPaginatedUseCase.kt
package com.gazzel.sesameapp.domain.usecase

import androidx.paging.PagingData
import com.gazzel.sesameapp.domain.model.PlaceItem
import com.gazzel.sesameapp.domain.repository.PlaceRepository // Import PlaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf // For returning empty on error/invalid input
import javax.inject.Inject

/**
 * Use case responsible for retrieving a paginated flow of places belonging to a specific list.
 */
class GetPlacesInListPaginatedUseCase @Inject constructor(
    private val placeRepository: PlaceRepository // Inject the repository interface
) {
    /**
     * Invokes the use case.
     * @param listId The ID of the list for which to fetch places.
     * @return A Flow emitting PagingData<PlaceItem>. Returns an empty flow if listId is blank.
     */
    operator fun invoke(listId: String): Flow<PagingData<PlaceItem>> {
        // Basic input validation: Return empty PagingData for blank listId
        if (listId.isBlank()) {
            return flowOf(PagingData.empty())
        }
        // Delegate the call to the repository's paginated method
        return placeRepository.getPlacesPaginated(listId)
    }
}