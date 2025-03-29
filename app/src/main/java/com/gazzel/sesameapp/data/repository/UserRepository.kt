package com.gazzel.sesameapp.data.repository

import com.gazzel.sesameapp.data.model.User
import com.gazzel.sesameapp.data.remote.UserApiService
import com.gazzel.sesameapp.data.local.UserDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

interface UserRepository {
    suspend fun getCurrentUser(): Flow<User>
    suspend fun updateUsername(username: String): Result<Unit>
    suspend fun checkUsername(): Flow<Boolean>
}

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userApiService: UserApiService,
    private val userDao: UserDao
) : UserRepository {
    override suspend fun getCurrentUser(): Flow<User> = flow {
        try {
            val user = userApiService.getCurrentUser()
            userDao.insertUser(user)
            emit(user)
        } catch (e: Exception) {
            // Fallback to local data if network fails
            userDao.getCurrentUser()?.let { emit(it) }
        }
    }

    override suspend fun updateUsername(username: String): Result<Unit> = runCatching {
        userApiService.updateUsername(username)
    }

    override suspend fun checkUsername(): Flow<Boolean> = flow {
        try {
            val response = userApiService.checkUsername()
            emit(response.needsUsername)
        } catch (e: Exception) {
            emit(true) // Assume username is needed on error
        }
    }
} 