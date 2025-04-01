package com.gazzel.sesameapp.data.repository

import com.gazzel.sesameapp.data.local.dao.UserDao
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
    override suspend fun getCurrentUser(): Flow<DomainUser> = flow {
        try {
            val dataUser = userApiService.getCurrentUser()
            userDao.insertUser(dataUser)
            emit(dataUser.toDomain())
        } catch (e: Exception) {
            val localUser = userDao.getCurrentUser()
            if (localUser != null) {
                emit(localUser.toDomain())
            } else {
                throw e // Propagate error if no local data
            }
        }
    }

    override suspend fun updateUsername(username: String): Result<Unit> = runCatching {
        userApiService.updateUsername(UsernameUpdateRequest(username))
        // Optionally update local data if the API call changes the user's profile
        val currentUser = userDao.getCurrentUser()
        if (currentUser != null) {
            userDao.insertUser(currentUser.copy(username = username))
        }
    }

    override suspend fun checkUsername(): Flow<Boolean> = flow {
        try {
            val response = userApiService.checkUsername()
            emit(response.needsUsername)
        } catch (e: Exception) {
            throw e // Propagate error instead of defaulting to true
        }
    }
}

// Mapper (could be moved to a separate file)
fun DataUser.toDomain(): DomainUser {
    return DomainUser(
        id = this.id,
        email = this.email,
        username = this.username,
        displayName = this.displayName,
        profilePicture = this.profilePicture
    )
}