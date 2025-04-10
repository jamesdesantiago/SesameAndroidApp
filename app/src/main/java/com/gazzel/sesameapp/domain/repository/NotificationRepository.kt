package com.gazzel.sesameapp.domain.repository

import com.gazzel.sesameapp.domain.model.Notification

interface NotificationRepository {
    suspend fun getNotifications(): Result<List<Notification>> // Changed
    suspend fun markAsRead(notificationId: String): Result<Unit> // Changed
    suspend fun markAllAsRead(): Result<Unit> // Changed
    suspend fun deleteNotification(notificationId: String): Result<Unit> // Changed
}
