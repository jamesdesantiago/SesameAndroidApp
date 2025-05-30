// domain/usecase/GetUserListsUseCase.kt
package com.gazzel.sesameapp.domain.usecase

import com.gazzel.sesameapp.domain.model.SesameList
import com.gazzel.sesameapp.domain.repository.ListRepository
import com.gazzel.sesameapp.domain.repository.UserRepository
import com.gazzel.sesameapp.domain.util.Result
import com.gazzel.sesameapp.domain.exception.AppException
import kotlinx.coroutines.flow.firstOrNull // Use firstOrNull for safety
import javax.inject.Inject
import android.util.Log // Optional logging
import com.gazzel.sesameapp.domain.util.flatMap


class GetUserListsUseCase @Inject constructor(
    private val listRepository: ListRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(): Result<List<SesameList>> {
        Log.d("GetUserListsUseCase", "Executing...")
        // 1. Get current user ID safely
        // Call the suspend fun returning Result
        val userResult = userRepository.getCurrentUser()

        // Use flatMap to proceed only if user fetch was successful
        return userResult.flatMap { user ->
            // 2. Fetch lists using the user ID
            Log.d("GetUserListsUseCase", "Fetching lists for user: ${user.id}")
            listRepository.getUserLists(user.id) // This already returns Result
        } // flatMap automatically propagates the error from userResult if it failed
    }
}