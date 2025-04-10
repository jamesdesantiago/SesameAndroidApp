package com.gazzel.sesameapp.domain.repository

import com.gazzel.sesameapp.domain.model.FAQ

interface HelpRepository {
    suspend fun getFAQs(): Result<List<FAQ>> // Changed
    suspend fun sendSupportEmail(subject: String, message: String): Result<Unit> // Changed
    suspend fun sendFeedback(feedback: String): Result<Unit> // Changed
}
