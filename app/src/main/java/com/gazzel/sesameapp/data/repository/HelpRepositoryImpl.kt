package com.gazzel.sesameapp.data.repository

import com.gazzel.sesameapp.domain.model.FAQ
import com.gazzel.sesameapp.domain.repository.HelpRepository
import javax.inject.Inject

class HelpRepositoryImpl @Inject constructor() : HelpRepository {
    override suspend fun getFAQs(): List<FAQ> {
        // Return a dummy list or implement your data fetching logic here.
        return listOf(
            FAQ("What is Sesame?", "Sesame is a demo app."),
            FAQ("How do I contact support?", "Please email support@example.com")
        )
    }

    override suspend fun sendSupportEmail(subject: String, message: String) {
        // Implement sending email or delegate to your backend.
    }

    override suspend fun sendFeedback(feedback: String) {
        // Implement sending feedback or delegate to your backend.
    }
}
