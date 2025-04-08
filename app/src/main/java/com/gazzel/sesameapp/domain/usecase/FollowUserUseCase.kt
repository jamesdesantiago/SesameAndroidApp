package com.gazzel.sesameapp.domain.usecase

import com.gazzel.sesameapp.domain.repository.FriendRepository
import com.gazzel.sesameapp.domain.util.Result
import javax.inject.Inject

class FollowUserUseCase @Inject constructor(
    private val friendRepository: FriendRepository
) {
    suspend operator fun invoke(userId: String): Result<Unit> {
        return friendRepository.followUser(userId)
    }
}