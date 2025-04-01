package com.gazzel.sesameapp.domain.exception

/**
 * A custom exception to represent authentication-related issues.
 */
class AuthException(
    override val message: String,
    override val cause: Throwable? = null
) : AppException() // Assuming AppException is your base exception class
