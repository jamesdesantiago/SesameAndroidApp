package com.gazzel.sesameapp.domain.repository

import com.gazzel.sesameapp.domain.model.Friend
import kotlinx.coroutines.flow.Flow

interface FriendRepository {
    fun getFriends(): Flow<List<Friend>>
    fun searchFriends(query: String): Flow<List<Friend>>
}
