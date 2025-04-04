package com.gazzel.sesameapp.domain.model

// Represents the user's privacy settings fetched from the repository
data class PrivacySettings(
    val profileIsPublic: Boolean = true, // Default value (adjust if needed)
    val listsArePublic: Boolean = true,  // Default value (adjust if needed)
    val allowAnalytics: Boolean = true   // Default value (adjust if needed)
)