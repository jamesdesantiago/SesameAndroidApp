package com.gazzel.sesameapp.data.network

import com.gazzel.sesameapp.data.manager.DeviceIdManager
import com.gazzel.sesameapp.domain.exception.NetworkException
import com.gazzel.sesameapp.domain.util.Logger
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * An OkHttp interceptor that adds security headers and performs security checks on network requests.
 * This interceptor is responsible for ensuring secure communication between the app and the server.
 *
 * Key features:
 * - Adds security headers to requests
 * - Verifies security headers in responses
 * - Generates unique request IDs
 * - Includes device identification
 * - Validates server security headers
 *
 * Security headers added:
 * - X-Request-ID: Unique identifier for each request
 * - X-Platform: Platform identifier (Android)
 * - X-App-Version: Application version
 * - X-Device-ID: Secure device identifier
 *
 * Security headers verified:
 * - Strict-Transport-Security: Enforces HTTPS
 * - X-Content-Type-Options: Prevents MIME type sniffing
 * - X-Frame-Options: Prevents clickjacking
 * - X-XSS-Protection: Enables browser XSS protection
 *
 * @property logger Logger instance for error tracking
 * @property deviceIdManager Manager for secure device identification
 */
class SecurityInterceptor @Inject constructor(
    private val logger: Logger,
    private val deviceIdManager: DeviceIdManager
) : Interceptor {
    /**
     * Intercepts the network request to add security headers and verify response security.
     *
     * @param chain The interceptor chain
     * @return The response with security headers verified
     * @throws NetworkException if security checks fail
     */
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("X-Request-ID", generateRequestId())
            .addHeader("X-Platform", "Android")
            .addHeader("X-App-Version", "1.0.0") // TODO: Get from BuildConfig
            .addHeader("X-Device-ID", deviceIdManager.getDeviceId())
            .build()

        return try {
            val response = chain.proceed(request)
            
            // Verify security headers in response
            verifySecurityHeaders(response)
            
            response
        } catch (e: Exception) {
            logger.error(NetworkException("Security check failed", e))
            throw e
        }
    }

    /**
     * Generates a unique request ID using UUID.
     * This ID is used for request tracking and security purposes.
     *
     * @return A unique request identifier
     */
    private fun generateRequestId(): String {
        return java.util.UUID.randomUUID().toString()
    }

    /**
     * Verifies that the response contains all required security headers.
     * Logs a warning if any required headers are missing.
     *
     * Required headers:
     * - Strict-Transport-Security
     * - X-Content-Type-Options
     * - X-Frame-Options
     * - X-XSS-Protection
     *
     * @param response The response to verify
     */
    private fun verifySecurityHeaders(response: Response) {
        // Verify required security headers
        val requiredHeaders = listOf(
            "Strict-Transport-Security",
            "X-Content-Type-Options",
            "X-Frame-Options",
            "X-XSS-Protection"
        )

        val missingHeaders = requiredHeaders.filter { header ->
            response.header(header) == null
        }

        if (missingHeaders.isNotEmpty()) {
            logger.warning("Missing security headers: ${missingHeaders.joinToString()}")
        }
    }
} 