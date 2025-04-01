package com.gazzel.sesameapp.domain.repository

import com.gazzel.sesameapp.domain.model.FAQ

interface HelpRepository {
    suspend fun getFAQs(): List<FAQ>
    suspend fun sendSupportEmail(subject: String, message: String)
    suspend fun sendFeedback(feedback: String)
}
