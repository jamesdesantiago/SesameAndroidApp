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

class GetUserListsUseCase @Inject constructor(
    private val listRepository: ListRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(): Result<List<SesameList>> {
        Log.d("GetUserListsUseCase", "Executing...")
        return try {
            // 1. Get current user ID safely
            val user = userRepository.getCurrentUser().firstOrNull()
                ?: return Result.error(AppException.AuthException("User not found or not authenticated."))

            // 2. Fetch lists using the user ID
            Log.d("GetUserListsUseCase", "Fetching lists for user: ${user.id}")
            listRepository.getUserLists(user.id) // This already returns Result<List<SesameList>>
        } catch (e: Exception) {
            Log.e("GetUserListsUseCase", "Error fetching user lists", e)
            // Catch potential errors from collecting the user flow or other unexpected issues
            Result.error(AppException.UnknownException("Failed to get user lists", e))
        }
    }
}