package com.gazzel.sesameapp.domain.util

import android.util.Log
import com.gazzel.sesameapp.domain.exception.AppException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Logger @Inject constructor() {
    private val tag = "SesameApp"

    fun debug(message: String, throwable: Throwable? = null) {
        // Always log in debug mode for now
        Log.d(tag, message, throwable)
    }

    fun info(message: String, throwable: Throwable? = null) {
        Log.i(tag, message, throwable)
    }

    fun warning(message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
    }

    fun error(message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
    }

    fun error(exception: AppException) {
        error(exception.message ?: "Unknown error", exception.cause)
    }
} 