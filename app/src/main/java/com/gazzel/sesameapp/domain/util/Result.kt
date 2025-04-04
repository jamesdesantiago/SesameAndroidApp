package com.gazzel.sesameapp.domain.util

import com.gazzel.sesameapp.domain.exception.AppException

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: AppException) : Result<Nothing>()

    companion object {
        fun <T> success(data: T): Result<T> = Success(data)
        fun error(exception: AppException): Result<Nothing> = Error(exception)
    }
}

inline fun <T> Result<T>.onSuccess(action: (data: T) -> Unit): Result<T> {
    if (this is Result.Success) action(data) // 'data' is the name from Result.Success
    return this
}

inline fun <T> Result<T>.onError(action: (exception: AppException) -> Unit): Result<T> {
    if (this is Result.Error) action(exception) // 'exception' is the name from Result.Error
    return this
}

inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> {
    return when (this) {
        is Result.Success -> Result.success(transform(data))
        is Result.Error -> Result.error(exception)
    }
}

inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> {
    return when (this) {
        is Result.Success -> transform(data)
        is Result.Error -> Result.error(exception)
    }
} 