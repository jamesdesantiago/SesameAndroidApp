package com.gazzel.sesameapp.domain.exception

/**
 * A custom exception to represent network-related issues.
 */
class NetworkException(
    override val message: String,
    override val cause: Throwable? = null
) : AppException()
