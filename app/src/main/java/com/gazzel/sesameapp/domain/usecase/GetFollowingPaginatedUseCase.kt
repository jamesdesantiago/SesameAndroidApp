// Create New File: app/src/main/java/com/gazzel/sesameapp/domain/usecase/GetFollowingPaginatedUseCase.kt
package com.gazzel.sesameapp.domain.usecase

import androidx.paging.PagingData
import com.gazzel.sesameapp.domain.model.Friend
import com.gazzel.sesameapp.domain.repository.FriendRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetFollowingPaginatedUseCase @Inject constructor(
    private val friendRepository: FriendRepository
) {
    operator fun invoke(): Flow<PagingData<Friend>> {
        // Call the specific paginated method in the repository
        return friendRepository.getFollowingPaginated()
    }
}