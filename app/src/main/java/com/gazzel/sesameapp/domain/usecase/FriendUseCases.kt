package com.gazzel.sesameapp.domain.usecase

import com.gazzel.sesameapp.domain.model.Friend
import com.gazzel.sesameapp.domain.repository.FriendRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FriendUseCases @Inject constructor(
    private val friendRepository: FriendRepository
) {
    fun getFriends(): Flow<List<Friend>> {
        return friendRepository.getFriends()
    }

    fun searchFriends(query: String): Flow<List<Friend>> {
        return friendRepository.searchFriends(query)
    }
}
