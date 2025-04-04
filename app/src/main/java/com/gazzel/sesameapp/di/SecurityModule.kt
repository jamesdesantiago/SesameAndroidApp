package com.gazzel.sesameapp.di

import android.content.Context
import com.gazzel.sesameapp.data.manager.DeviceIdManager
import com.gazzel.sesameapp.data.manager.SecurityManager
import com.gazzel.sesameapp.data.network.SecurityInterceptor
import com.gazzel.sesameapp.domain.util.Logger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * A Hilt module that provides security-related dependencies for the application.
 * This module is responsible for initializing and providing security components
 * that are used throughout the app.
 *
 * Key components provided:
 * - SecurityManager: Handles encryption and secure storage
 * - DeviceIdManager: Manages secure device identification
 * - SecurityInterceptor: Adds security headers to network requests
 * - SecureOkHttpClient: Configured HTTP client with security features
 *
 * Security features:
 * - Singleton scoped components
 * - Secure HTTP client configuration
 * - Security header management
 * - Device identification
 *
 * Usage:
 * ```kotlin
 * @Inject
 * lateinit var securityManager: SecurityManager
 * ```
 *
 * @see SecurityManager
 * @see DeviceIdManager
 * @see SecurityInterceptor
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    /**
     * Provides a singleton instance of SecurityManager.
     * This manager handles encryption and secure storage operations.
     *
     * @param context The application context
     * @param logger The Logger instance
     * @return The configured SecurityManager
     */
    @Provides
    @Singleton
    fun provideSecurityManager(
        @ApplicationContext context: Context,
        logger: Logger
    ): SecurityManager {
        return SecurityManager(context, logger)
    }

    /**
     * Provides a singleton instance of DeviceIdManager.
     * This manager handles secure device identification.
     *
     * @param context The application context
     * @param securityManager The SecurityManager instance
     * @param logger The Logger instance
     * @return The configured DeviceIdManager
     */
    @Provides
    @Singleton
    fun provideDeviceIdManager(
        @ApplicationContext context: Context,
        securityManager: SecurityManager,
        logger: Logger
    ): DeviceIdManager {
        return DeviceIdManager(context, securityManager, logger)
    }
}