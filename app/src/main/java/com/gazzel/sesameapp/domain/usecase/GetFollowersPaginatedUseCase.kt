// Create New File: app/src/main/java/com/gazzel/sesameapp/domain/usecase/GetFollowersPaginatedUseCase.kt
package com.gazzel.sesameapp.domain.usecase

import androidx.paging.PagingData
import com.gazzel.sesameapp.domain.model.Friend // Still uses Friend model
import com.gazzel.sesameapp.domain.repository.FriendRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetFollowersPaginatedUseCase @Inject constructor(
    private val friendRepository: FriendRepository
) {
    operator fun invoke(): Flow<PagingData<Friend>> {
        // Call the specific paginated method in the repository
        return friendRepository.getFollowersPaginated()
    }
}