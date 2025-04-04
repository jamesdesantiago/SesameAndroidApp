package com.gazzel.sesameapp.domain.repository

import com.gazzel.sesameapp.domain.model.Notification

interface NotificationRepository {
    suspend fun getNotifications(): List<Notification>
    suspend fun markAsRead(notificationId: String)
    suspend fun markAllAsRead()
    suspend fun deleteNotification(notificationId: String)
}
