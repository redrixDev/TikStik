// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.common

import android.util.Log
import com.redrixdev.tikstik.BuildConfig

/** Technical log: never personal data, URLs or file names from the TikTok cache. */
object Logger {
    // In release, only the exception's type: its message and stack trace may carry paths or data.
    fun warn(area: String, message: String, error: Throwable? = null) {
        if (BuildConfig.DEBUG || error == null) Log.w("TikStik/$area", message, error) else Log.w("TikStik/$area", "$message (${error.javaClass.simpleName})")
    }

    /** [detail] only in debug: anything that may carry user content. */
    fun warn(area: String, message: String, detail: String) {
        Log.w("TikStik/$area", if (BuildConfig.DEBUG) "$message: $detail" else message)
    }
}
