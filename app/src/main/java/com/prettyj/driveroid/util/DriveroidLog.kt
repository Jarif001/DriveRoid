package com.prettyj.driveroid.util

import android.util.Log
import com.prettyj.driveroid.BuildConfig

/**
 * Tiny logging facade for the whole app.
 *
 * Every line is logged under the single [TAG] and prefixed with the component name, so the full
 * assistant timeline can be followed with `adb logcat -s DriveRoid`. Verbose lines are emitted in
 * debug builds only to keep release logs (and the battery) quiet.
 */
object DriveroidLog {

    private const val TAG = "DriveRoid"

    /** Detail that only matters while developing. */
    fun d(component: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, format(component, message))
    }

    /** Lifecycle changes: service start/stop, state transitions, permission decisions. */
    fun i(component: String, message: String) {
        Log.i(TAG, format(component, message))
    }

    /** Something unexpected that the app recovers from. */
    fun w(component: String, message: String, throwable: Throwable? = null) {
        Log.w(TAG, format(component, message), throwable)
    }

    /** Something that stops the assistant. */
    fun e(component: String, message: String, throwable: Throwable? = null) {
        Log.e(TAG, format(component, message), throwable)
    }

    private fun format(component: String, message: String) = "[$component] $message"
}
