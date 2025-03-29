package com.gazzel.sesameapp.data.manager

import android.content.Context
import android.provider.Settings
import com.gazzel.sesameapp.domain.exception.SecurityException
import com.gazzel.sesameapp.domain.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.security.MessageDigest

/**
 * A singleton manager responsible for generating and managing secure device identifiers.
 * This class ensures that each device has a unique, persistent identifier that can be
 * used for tracking and security purposes while maintaining user privacy.
 *
 * Key features:
 * - Secure device ID generation using multiple sources
 * - Persistent storage of device IDs
 * - Privacy-preserving hashing
 * - Fallback mechanisms for ID generation
 *
 * Security considerations:
 * - Uses multiple sources for ID generation to ensure uniqueness
 * - Implements cryptographic hashing (SHA-256)
 * - Securely stores IDs using SecurityManager
 * - Handles edge cases and failures gracefully
 *
 * Privacy considerations:
 * - No personally identifiable information is used
 * - IDs are hashed to prevent reverse engineering
 * - Multiple sources ensure uniqueness without tracking
 *
 * Implementation details:
 * - Uses Android's ANDROID_ID and SSID as base sources
 * - Adds timestamp to ensure uniqueness across resets
 * - Implements SHA-256 hashing for privacy
 * - Stores IDs securely using SecurityManager
 *
 * @property context The application context for accessing system settings
 * @property securityManager Security manager for secure storage operations
 * @property logger Logger instance for error tracking and monitoring
 */
@Singleton
class DeviceIdManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securityManager: SecurityManager,
    private val logger: Logger
) {
    /**
     * The key used for storing the device ID in secure storage.
     * This key should be unique to prevent conflicts with other secure storage entries.
     */
    private val deviceIdKey = "secure_device_id"

    /**
     * Retrieves the device ID, generating a new one if necessary.
     * This method ensures that each device has a unique, persistent identifier.
     *
     * Implementation details:
     * - First attempts to retrieve existing ID from secure storage
     * - If no ID exists or retrieval fails, generates a new one
     * - Handles exceptions gracefully with logging
     *
     * @return The device's unique identifier
     * @throws SecurityException if ID generation fails
     */
    fun getDeviceId(): String {
        return try {
            // First attempt to retrieve existing ID from secure storage
            // This ensures persistence across app restarts
            securityManager.secureRetrieve(deviceIdKey) ?: generateNewDeviceId()
        } catch (e: Exception) {
            // Log the error and attempt to generate a new ID
            // This provides a fallback mechanism in case of storage issues
            logger.error(SecurityException("Failed to get device ID", e))
            generateNewDeviceId()
        }
    }

    /**
     * Generates a new device ID using multiple sources to ensure uniqueness.
     * The generated ID is a SHA-256 hash of combined device-specific information.
     *
     * Implementation details:
     * - Uses ANDROID_ID as primary source (unique to each app installation)
     * - Adds SSID as secondary source (network identifier)
     * - Includes timestamp to ensure uniqueness across resets
     * - Implements SHA-256 hashing for privacy
     * - Stores the new ID securely
     *
     * Sources used for ID generation:
     * - Android ID (unique to each app installation)
     * - SSID (network identifier)
     * - Timestamp (ensures uniqueness across resets)
     *
     * @return A new, unique device identifier
     * @throws SecurityException if ID generation fails
     */
    private fun generateNewDeviceId(): String {
        return try {
            // Collect multiple sources of device-specific information
            // This ensures uniqueness even if one source is not available
            val sources = listOf(
                // ANDROID_ID is unique to each app installation
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID),
                // SSID provides network context
                Settings.Secure.getString(context.contentResolver, Settings.Secure.SSID),
                // Timestamp ensures uniqueness across resets
                System.currentTimeMillis().toString()
            )

            // Combine sources with a separator and create a hash
            // Using SHA-256 for cryptographic security and privacy
            val combined = sources.joinToString("|")
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(combined.toByteArray())
                // Convert bytes to hex string for storage
                .joinToString("") { "%02x".format(it) }

            // Store the new ID securely for future use
            securityManager.secureStore(deviceIdKey, hash)
            hash
        } catch (e: Exception) {
            // Log the error and propagate it
            // This ensures proper error handling at the application level
            logger.error(SecurityException("Failed to generate device ID", e))
            throw e
        }
    }

    /**
     * Resets the device ID, generating a new one.
     * This method should be used with caution as it will invalidate any existing
     * device-specific data or sessions.
     *
     * Implementation details:
     * - Generates a new device ID using existing sources
     * - Securely stores the new ID
     * - Handles exceptions with logging
     *
     * Use cases:
     * - User explicitly requests device reset
     * - Security breach detection
     * - App reinstallation
     *
     * @throws SecurityException if ID reset fails
     */
    fun resetDeviceId() {
        try {
            // Generate and store a new device ID
            // This invalidates any existing sessions or data
            securityManager.secureStore(deviceIdKey, generateNewDeviceId())
        } catch (e: Exception) {
            // Log the error and propagate it
            logger.error(SecurityException("Failed to reset device ID", e))
            throw e
        }
    }
} 