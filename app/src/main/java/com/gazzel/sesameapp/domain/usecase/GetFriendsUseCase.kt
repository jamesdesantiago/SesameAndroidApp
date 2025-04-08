package com.gazzel.sesameapp.domain.usecase

import com.gazzel.sesameapp.domain.model.Friend
import com.gazzel.sesameapp.domain.repository.FriendRepository
import kotlinx.coroutines.flow.Flow // Use Flow as the repo method returns Flow
import javax.inject.Inject

class GetFriendsUseCase @Inject constructor(
    private val friendRepository: FriendRepository
) {
    // Directly return the Flow from the repository
    operator fun invoke(): Flow<List<Friend>> {
        return friendRepository.getFriends()
    }
}