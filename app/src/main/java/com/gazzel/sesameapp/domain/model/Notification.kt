package com.gazzel.sesameapp.domain.model

data class Notification(
    val id: String,
    val title: String,
    val message: String,
    val isRead: Boolean,
    val timestamp: Long
)
