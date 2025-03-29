package com.gazzel.sesameapp.domain.repository

import com.gazzel.sesameapp.domain.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun getCurrentUser(): Flow<User>
    suspend fun updateUsername(username: String): Result<Unit>
    suspend fun checkUsername(): Flow<Boolean>
}