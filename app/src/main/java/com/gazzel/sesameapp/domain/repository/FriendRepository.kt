    // domain/repository/FriendRepository.kt
    package com.gazzel.sesameapp.domain.repository

    import com.gazzel.sesameapp.domain.model.Friend
    import com.gazzel.sesameapp.domain.util.Result // <<< ADD Import
    import kotlinx.coroutines.flow.Flow
    import androidx.paging.PagingData

    interface FriendRepository {
        fun getFriends(): Flow<List<Friend>>
        fun searchFriends(query: String): Flow<List<Friend>>
        // --- ADD Methods ---
        suspend fun followUser(userId: String): Result<Unit>
        suspend fun unfollowUser(userId: String): Result<Unit>
        fun getFollowingPaginated(): Flow<PagingData<Friend>>
        fun getFollowersPaginated(): Flow<PagingData<Friend>>
    }