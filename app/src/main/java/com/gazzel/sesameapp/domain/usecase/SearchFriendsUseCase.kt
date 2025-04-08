package com.gazzel.sesameapp.domain.usecase

import com.gazzel.sesameapp.domain.model.Friend
import com.gazzel.sesameapp.domain.repository.FriendRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchFriendsUseCase @Inject constructor(
    private val friendRepository: FriendRepository
) {
    // Directly return the Flow from the repository
    operator fun invoke(query: String): Flow<List<Friend>> {
        return friendRepository.searchFriends(query)
    }
}