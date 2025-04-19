package com.gazzel.sesameapp.domain.usecase

import com.gazzel.sesameapp.domain.model.Friend
import com.gazzel.sesameapp.domain.repository.FriendRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchFriendsPaginatedUseCase @Inject constructor(
    private val friendRepository: FriendRepository
) {
    operator fun invoke(query: String): Flow<PagingData<Friend>> {
        // Basic validation: Don't trigger API for very short/blank queries
        // if (query.trim().length < 2) { // Example threshold
        //     return flowOf(PagingData.empty()) // Return empty flow immediately
        // }
        return friendRepository.searchFriendsPaginated(query.trim())
    }
}