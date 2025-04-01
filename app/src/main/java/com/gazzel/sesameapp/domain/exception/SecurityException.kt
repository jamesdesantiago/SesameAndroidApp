package com.gazzel.sesameapp.domain.exception

/**
 * A custom exception to represent security-related issues.
 */
class SecurityException(
    override val message: String,
    override val cause: Throwable? = null
) : AppException()
