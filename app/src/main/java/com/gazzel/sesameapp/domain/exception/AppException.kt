package com.gazzel.sesameapp.domain.exception

sealed class AppException : Exception() {
    data class NetworkException(
        override val message: String,
        val code: Int? = null,
        override val cause: Throwable? = null
    ) : AppException()

    data class LocationException(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppException()

    data class DatabaseException(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppException()

    data class AuthException(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppException()

    data class ValidationException(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppException()

    data class UnknownException(
        override val message: String = "An unknown error occurred",
        override val cause: Throwable? = null
    ) : AppException()
} 