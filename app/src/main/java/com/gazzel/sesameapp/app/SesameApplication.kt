package com.gazzel.sesameapp.app

import android.app.Application
import com.gazzel.sesameapp.BuildConfig // <<< Import BuildConfig
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber // <<< Import Timber

@HiltAndroidApp
class SesameApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Plant the appropriate Timber tree
        if (BuildConfig.DEBUG) {
            // Plant a DebugTree that logs to Logcat during development
            Timber.plant(Timber.DebugTree())
            Timber.d("Timber DebugTree planted.") // Log confirmation
        } else {
            // Plant a different tree for release builds.
            // Option 1: Plant a tree that does nothing (effectively strips logs).
            // Timber.plant(ReleaseTree()) // See ReleaseTree definition below

            // Option 2 (Recommended if using Crashlytics): Plant a tree that sends
            // logs (WARN, ERROR, WTF) and non-fatal exceptions to Crashlytics.
            Timber.plant(CrashlyticsTree()) // See CrashlyticsTree definition below
            Timber.i("Timber CrashlyticsTree planted.") // Log confirmation (will only go to Crashlytics in release)
        }
    }
}

// --- Helper Tree Definitions (can be in this file or a separate utility file) ---

/**
 * A Timber Tree that does nothing. Used to disable logging in release builds
 * if Crashlytics integration is not desired.
 */
private class ReleaseTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Do nothing
    }
}

/**
 * A Timber Tree that logs warnings, errors, and exceptions to Firebase Crashlytics.
 * Ensures only important logs reach Crashlytics in release builds.
 */
private class CrashlyticsTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Only log WARN, ERROR, ASSERT to Crashlytics
        if (priority == Log.VERBOSE || priority == Log.DEBUG || priority == Log.INFO) {
            return // Ignore V, D, I logs in release Crashlytics
        }

        // Log non-fatal exceptions to Crashlytics if provided
        if (t != null) {
            // You might want to filter specific expected exceptions here
            // if (!isExpectedException(t)) {
            //    FirebaseCrashlytics.getInstance().recordException(t)
            // }
            // For now, log all non-fatal exceptions passed to Timber.e(t, ...)
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(t) // <<< Requires Crashlytics dependency
        }

        // Log the message to Crashlytics (useful for context around crashes)
        com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().log("[$priority] ${tag ?: "NoTag"}: $message")

        // Optional: Log WTF (Assert) as non-fatal exceptions as well
        // if (priority == Log.ASSERT) {
        //     FirebaseCrashlytics.getInstance().recordException(Throwable("ASSERT: [$tag] $message"))
        // }
    }
}