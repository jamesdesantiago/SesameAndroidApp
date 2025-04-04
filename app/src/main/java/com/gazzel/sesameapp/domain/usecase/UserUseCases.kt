package com.gazzel.sesameapp.domain.usecase

import com.gazzel.sesameapp.domain.model.User
import com.gazzel.sesameapp.domain.repository.UserRepository
import com.gazzel.sesameapp.domain.util.Result
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class UserUseCases @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend fun getCurrentUser(): Flow<User> = userRepository.getCurrentUser()

    suspend fun updateUsername(username: String): Result<Unit> =
        userRepository.updateUsername(username)

    suspend fun checkUsername(): Flow<Boolean> = userRepository.checkUsername()
}