package com.gazzel.sesameapp.data.repository

import com.gazzel.sesameapp.data.local.UserDao
import com.gazzel.sesameapp.data.mapper.toDomain
import com.gazzel.sesameapp.data.model.User as DataUser
import com.gazzel.sesameapp.data.remote.UserApiService
import com.gazzel.sesameapp.data.remote.UsernameUpdateRequest
import com.gazzel.sesameapp.domain.model.User as DomainUser
import com.gazzel.sesameapp.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userApiService: UserApiService,
    private val userDao: UserDao
) : UserRepository {
    override suspend fun getCurrentUser(): Flow<DomainUser> {
        return flow {
            val user = userDao.getCurrentUser()
            user?.let { emit(it.toDomain()) }
        }
    }

    override suspend fun updateUsername(username: String): Result<Unit> {
        return try {
            userApiService.updateUsername(UsernameUpdateRequest(username))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun checkUsername(): Flow<Boolean> {
        return flow {
            val response = userApiService.checkUsername()
            emit(response.needsUsername)
        }
    }
}