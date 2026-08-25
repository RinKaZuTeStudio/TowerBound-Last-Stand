package com.towerbound.laststand.core

import android.util.Log

/**
 * Central logging facade. Debug builds log verbosely; release builds only
 * log warnings/errors so logcat is not spammed during gameplay.
 */
object GameLog {
    private const val TAG_PREFIX = "TB:"
    @Volatile var verbose: Boolean = true

    fun i(tag: String, msg: String) {
        if (verbose) Log.i(TAG_PREFIX + tag, msg)
    }

    fun w(tag: String, msg: String) {
        Log.w(TAG_PREFIX + tag, msg)
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        Log.e(TAG_PREFIX + tag, msg, tr)
    }

    fun d(tag: String, msg: String) {
        if (verbose) Log.d(TAG_PREFIX + tag, msg)
    }
}
