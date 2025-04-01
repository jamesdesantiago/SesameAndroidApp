package com.gazzel.sesameapp.domain.usecase

import com.gazzel.sesameapp.domain.model.User // Explicit import
import com.gazzel.sesameapp.domain.repository.UserRepository // Explicit import
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